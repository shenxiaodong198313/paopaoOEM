package com.paopaoime.aikeyboard.ime.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import kotlin.math.log10

/** One microphone per process; stopping never waits for model inference or a network request. */
class VoiceSession(context: Context, private val observer: Observer) : VoiceRuntime.Listener {
    interface Observer {
        fun state(value: String, message: String)
        fun text(value: String, final: Boolean)
        fun level(value: Float)
    }
    private val app = context.applicationContext
    private val config = org.fcitx.fcitx5.android.input.voice.OemVoiceConfig.get(app)
    private val main = Handler(Looper.getMainLooper())
    private val recording = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null
    private var id = 0L
    private var stage = "idle"
    private var startedAt = 0L
    @Volatile private var lastSpeech = 0L
    @Volatile private var graceful = false
    private val deadline = Runnable { Log.i("PaopaoVoice", "Stop reason=duration-limit"); finish() }
    private val timeout = Runnable { cancel("已保留当前文字") }
    private val silence = object : Runnable {
        override fun run() {
            if (stage != "listening") return
            if (SystemClock.elapsedRealtime() - lastSpeech >= config.silenceTimeoutSeconds * 1000L) {
                Log.i("PaopaoVoice", "Stop reason=silence-timeout"); finish()
            } else main.postDelayed(this, 1000)
        }
    }
    val active get() = stage != "idle"
    fun start() {
        if (!org.fcitx.fcitx5.android.input.voice.OemVoiceConfig.get(app).enabled) { cancel("语音输入未启用"); return }
        if (active) return
        current?.cancel(); current = this
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { cancel("请先允许麦克风权限"); return }
        val repo = VoiceRepository.get(app)
        if (!repo.ready()) { cancel("请先下载语音包"); return }
        startedAt = SystemClock.elapsedRealtime(); graceful = false
        state("preparing", if (VoiceRuntime.resident(repo.mode)) "正在开启麦克风…" else "正在加载语音模型…")
        id = VoiceRuntime.begin(repo, this)
    }
    override fun ready(reused: Boolean, initMs: Long) {
        if (stage != "preparing") return
        try {
            val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(min > 0)
            val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 6400))
            recorder = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED)
            if (Build.VERSION.SDK_INT >= 29) {
                val callback = object : AudioManager.AudioRecordingCallback() {
                    override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                        if (recorder === audio && stage == "listening" && configs.any { it.clientAudioSessionId == audio.audioSessionId && it.isClientSilenced }) {
                            Log.i("PaopaoVoice", "Stop reason=system-silenced")
                            cancel("麦克风已被系统暂停，已保留当前文字")
                        }
                    }
                }
                audio.registerAudioRecordingCallback(ContextCompat.getMainExecutor(app), callback)
                recordingCallback = callback
            }
            audio.startRecording(); check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            recording.set(true); lastSpeech = SystemClock.elapsedRealtime()
            state("listening", "正在倾听…")
            main.postDelayed(deadline, config.maxRecordingSeconds * 1000L); main.postDelayed(silence, 1000)
            val sessionId = id
            Log.i("PaopaoVoice", "Capture ready reused=$reused initMs=$initMs clickToCaptureMs=${SystemClock.elapsedRealtime() - startedAt}")
            Thread({
                try {
                    val pcm = ShortArray(1600)
                    while (recording.get()) {
                        val count = audio.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                        if (!recording.get()) break
                        if (count <= 0) { main.post { if (id == sessionId && active) cancel("麦克风已中断") }; break }
                        val samples = FloatArray(count); var sum = 0.0
                        for (i in 0 until count) { samples[i] = pcm[i] / 32768f; sum += samples[i] * samples[i] }
                        val rms = sqrt(sum / count).toFloat()
                        if (rms > config.speechRmsThreshold) lastSpeech = SystemClock.elapsedRealtime()
                        // Match Harmony's -56..-18 dB meter so quiet speech still lifts the ribbons.
                        val level = ((20f * log10(maxOf(rms, .00001f)) + 56f) / 38f).coerceIn(0f, 1f)
                        main.post { if (id == sessionId && stage == "listening") observer.level(level) }
                        if (!VoiceRuntime.audio(sessionId, samples, this)) { main.post { if (id == sessionId && active) cancel("设备繁忙，已保留当前文字") }; break }
                    }
                } catch (error: Exception) { main.post { if (id == sessionId && stage == "listening") cancel("麦克风已中断") } }
                finally {
                    synchronized(this) {
                        if (recorder === audio) { unregisterRecordingCallback(audio); recorder = null }
                        runCatching { audio.stop() }; audio.release()
                    }
                    Log.i("PaopaoVoice", "Capture released")
                    if (graceful && id == sessionId) VoiceRuntime.finish(sessionId, this)
                }
            }, "PaopaoVoiceCapture").start()
        } catch (error: Exception) {
            recorder?.let { unregisterRecordingCallback(it); runCatching { it.release() } }; recorder = null
            cancel("无法开启麦克风，请检查系统权限")
        }
    }
    fun finish() {
        if (!active || stage == "finishing") return
        if (stage == "preparing") { cancel(); return }
        graceful = true
        state("finishing", "正在完成…")
        stopMicrophone()
        main.postDelayed(timeout, config.finishTimeoutMs.toLong())
    }
    @Synchronized private fun stopMicrophone() {
        recording.set(false)
        main.removeCallbacks(deadline); main.removeCallbacks(silence)
        runCatching { recorder?.stop() }
    }
    private fun unregisterRecordingCallback(audio: AudioRecord) {
        if (Build.VERSION.SDK_INT >= 29) recordingCallback?.let { runCatching { audio.unregisterAudioRecordingCallback(it) } }
        recordingCallback = null
    }
    fun cancel(message: String = "") {
        graceful = false; stopMicrophone(); main.removeCallbacks(timeout)
        VoiceRuntime.discard(id)
        if (current === this) current = null
        state("idle", message)
    }
    override fun text(value: String, final: Boolean) { if (active) observer.text(value, final) }
    override fun done() { main.removeCallbacks(timeout); if (current === this) current = null; state("idle", "") }
    override fun error(message: String) { cancel(message) }
    private fun state(value: String, message: String) { stage = value; observer.state(value, message) }
    companion object { private var current: VoiceSession? = null }
}
