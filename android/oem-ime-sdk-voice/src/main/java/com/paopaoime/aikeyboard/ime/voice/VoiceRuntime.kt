package com.paopaoime.aikeyboard.ime.voice

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** JNI objects are created, used and released only on this worker. UI hiding retains models. */
object VoiceRuntime {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "PaopaoVoiceEngine") }
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: OnlineRecognizer? = null
    private var punctuation: OfflinePunctuation? = null
    private var stream: OnlineStream? = null
    @Volatile private var loaded: VoiceMode? = null
    fun resident(mode: VoiceMode): Boolean = loaded == mode
    private var streamToken = 0L
    private val token = AtomicLong(0)
    private val pending = AtomicInteger(0)
    private var previous = ""
    private var lastPartial = 0L
    interface Listener {
        fun ready(reused: Boolean, initMs: Long)
        fun text(value: String, final: Boolean)
        fun done()
        fun error(message: String)
    }
    fun begin(repo: VoiceRepository, listener: Listener): Long {
        val id = token.incrementAndGet(); val mode = repo.mode
        val tuning = repo.config.tuning(mode.id)
        worker.execute {
            guard(id, listener) {
                closeStream()
                val reused = loaded == mode && recognizer != null && punctuation != null
                val start = SystemClock.elapsedRealtime()
                if (!reused) {
                    releaseModels()
                    repo.verify(mode)
                    val folder = File(repo.root, mode.id)
                    val model = OnlineModelConfig(tokens = File(folder, "tokens.txt").path, numThreads = tuning.numThreads, provider = "cpu")
                    if (mode == VoiceMode.PERFORMANCE) model.zipformer2Ctc = OnlineZipformer2CtcModelConfig(File(folder, "model.int8.onnx").path)
                    else {
                        model.modelType = "zipformer2" // Avoid the expensive auto-detection session.
                        model.transducer = OnlineTransducerModelConfig(File(folder, "encoder.int8.onnx").path, File(folder, "decoder.onnx").path, File(folder, "joiner.int8.onnx").path)
                    }
                    recognizer = OnlineRecognizer(config = OnlineRecognizerConfig(modelConfig = model,
                        decodingMethod = tuning.decodingMethod, maxActivePaths = tuning.maxActivePaths, blankPenalty = tuning.blankPenalty,
                        endpointConfig = EndpointConfig(EndpointRule(false, tuning.endpointSilenceSeconds, 0f), EndpointRule(true, tuning.endpointSpeechSilenceSeconds, 0f), EndpointRule(false, 0f, tuning.endpointMaxUtteranceSeconds))))
                    punctuation = OfflinePunctuation(config = OfflinePunctuationConfig(OfflinePunctuationModelConfig(File(repo.root, "punctuation/model.int8.onnx").path, numThreads = repo.config.punctuationThreads)))
                    loaded = mode
                }
                val elapsed = if (reused) 0 else SystemClock.elapsedRealtime() - start
                Log.i("PaopaoVoice", "Model ready mode=${mode.id} reused=$reused initMs=$elapsed")
                if (token.get() != id) return@guard
                stream = recognizer!!.createStream(); streamToken = id; previous = ""; lastPartial = 0
                dispatch(id) { listener.ready(reused, elapsed) }
            }
        }
        return id
    }
    fun audio(id: Long, samples: FloatArray, listener: Listener): Boolean {
        if (token.get() != id) return false
        if (pending.incrementAndGet() > 24) { pending.decrementAndGet(); return false }
        worker.execute {
            try {
                guard(id, listener) {
                    if (streamToken != id) return@guard
                    val s = stream ?: return@guard; val r = recognizer ?: return@guard
                    s.acceptWaveform(samples, 16000)
                    while (r.isReady(s)) r.decode(s)
                    if (r.isEndpoint(s)) { result(id, listener, true); r.reset(s); previous = "" }
                    else if (SystemClock.elapsedRealtime() - lastPartial >= 160) { result(id, listener, false); lastPartial = SystemClock.elapsedRealtime() }
                }
            } finally { pending.decrementAndGet() }
        }
        return true
    }
    fun finish(id: Long, listener: Listener) = worker.execute {
        guard(id, listener) {
            if (streamToken != id) return@guard
            stream?.let { s -> s.acceptWaveform(FloatArray(11200), 16000); s.inputFinished(); while (recognizer!!.isReady(s)) recognizer!!.decode(s); result(id, listener, true) }
            closeStream(); dispatch(id) { listener.done() }
        }
    }
    fun discard(id: Long) {
        token.compareAndSet(id, id + 1)
        worker.execute { if (streamToken == id) closeStream() }
    }
    fun release(reason: String) {
        token.incrementAndGet()
        worker.execute { releaseModels(); Log.i("PaopaoVoice", "Models released reason=$reason") }
    }
    private fun result(id: Long, listener: Listener, final: Boolean) {
        var text = recognizer!!.getResult(stream!!).text.trim()
        if (final && text.isNotEmpty()) text = punctuation!!.addPunctuation(text)
        if (final || text != previous) { previous = text; dispatch(id) { listener.text(text, final) } }
    }
    private fun closeStream() { stream?.release(); stream = null; streamToken = 0 }
    private fun releaseModels() { closeStream(); recognizer?.release(); recognizer = null; punctuation?.release(); punctuation = null; loaded = null }
    private fun dispatch(id: Long, block: () -> Unit) { main.post { if (token.get() == id) block() } }
    private fun guard(id: Long, listener: Listener, block: () -> Unit) {
        if (token.get() != id) return
        try { block() } catch (error: Exception) { releaseModels(); Log.e("PaopaoVoice", "Runtime failed: ${error.javaClass.simpleName}"); dispatch(id) { listener.error("离线识别失败，请重新下载语音包后重试") } }
        catch (error: LinkageError) { dispatch(id) { listener.error("语音运行库加载失败") } }
    }
}
