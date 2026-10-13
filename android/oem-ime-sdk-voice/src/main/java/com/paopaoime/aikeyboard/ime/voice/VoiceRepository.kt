package com.paopaoime.aikeyboard.ime.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class VoiceDownload(val running: Boolean = false, val downloaded: Long = 0, val total: Long = 0, val message: String = "")

class VoiceRepository private constructor(context: Context) {
    val config = org.fcitx.fcitx5.android.input.voice.OemVoiceConfig.get(context)
    val root = File(context.filesDir, "voice_input_v2").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("voice_input_v2", Context.MODE_PRIVATE)
    private val io = Executors.newSingleThreadExecutor()
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val paused = AtomicBoolean(false)
    @Volatile private var call: okhttp3.Call? = null
    private val mutable = MutableStateFlow(VoiceDownload())
    val download = mutable.asStateFlow()
    var mode: VoiceMode
        get() = VoiceMode.from(prefs.getString("mode", config.defaultMode))
        set(value) {
            if (!mutable.value.running && value != mode) {
                prefs.edit().putString("mode", value.id).apply()
                mutable.value = VoiceDownload()
            }
        }

    fun file(item: ModelFile) = File(root, "${item.folder}/${item.name}")
    private fun marker(item: ModelFile) = File(root, "${item.folder}/${item.name}.verified")
    fun ready(mode: VoiceMode = this.mode) = VoiceModels.files(mode).all { item ->
        file(item).length() == item.bytes && runCatching { marker(item).readText() == item.sha256 }.getOrDefault(false)
    }
    fun verify(mode: VoiceMode) {
        VoiceModels.files(mode).forEach { item ->
            if (file(item).length() != item.bytes || digest(file(item)) != item.sha256) {
                marker(item).delete()
                throw IOException("语音包校验失败，请重新下载")
            }
        }
    }
    fun downloaded(mode: VoiceMode = this.mode) = VoiceModels.files(mode).sumOf {
        if (file(it).length() == it.bytes && marker(it).exists()) it.bytes
        else File(file(it).path + ".part").length().coerceAtMost(it.bytes)
    }
    @Synchronized fun startDownload() {
        if (!config.enabled || mutable.value.running) return
        val selected = mode
        paused.set(false)
        mutable.value = VoiceDownload(true, downloaded(selected), VoiceModels.bytes(selected), "正在下载")
        io.execute {
            try {
                for (item in VoiceModels.files(selected)) {
                    if (paused.get()) throw IOException("已暂停")
                    if (file(item).length() == item.bytes && marker(item).run { isFile && readText() == item.sha256 }) continue
                    var completed = false
                    for (url in config.urls(item.folder, item.name, item.repo, item.revision)) {
                        try { downloadFile(item, selected, url); completed = true; break }
                        catch (error: Exception) { if (paused.get()) throw error }
                    }
                    if (!completed) throw IOException("下载未完成，请检查网络后继续")
                }
                mutable.value = VoiceDownload(false, VoiceModels.bytes(selected), VoiceModels.bytes(selected), "语音包已下载")
            } catch (error: Exception) {
                mutable.value = VoiceDownload(false, downloaded(selected), VoiceModels.bytes(selected), if (paused.get()) "已暂停" else error.message ?: "下载未完成，请重试")
            } finally { call = null }
        }
    }
    fun pause() { paused.set(true); call?.cancel() }
    private fun downloadFile(item: ModelFile, mode: VoiceMode, url: String) {
        val destination = file(item); destination.parentFile!!.mkdirs()
        val part = File(destination.path + ".part")
        if (part.length() > item.bytes) part.delete()
        var offset = part.length()
        if (offset < item.bytes) {
            val request = Request.Builder().url(url).apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
            val current = http.newCall(request); call = current
            current.execute().use { response ->
                if (response.code != 200 && response.code != 206) throw IOException("下载源暂时不可用")
                if (response.code == 206 && !response.header("Content-Range").orEmpty().startsWith("bytes $offset-")) throw IOException("下载续传校验失败")
                if (response.code == 200) offset = 0
                val body = response.body ?: throw IOException("下载内容为空")
                if (body.contentType()?.subtype?.contains("html") == true) throw IOException("下载源返回异常内容")
                java.io.FileOutputStream(part, offset > 0).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024); var last = 0L
                        while (true) {
                            if (paused.get()) throw IOException("已暂停")
                            val count = input.read(buffer); if (count < 0) break
                            offset += count
                            if (offset > item.bytes) { part.delete(); throw IOException("下载大小校验失败") }
                            output.write(buffer, 0, count)
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - last > 200) { mutable.value = VoiceDownload(true, downloaded(mode), VoiceModels.bytes(mode), "正在下载"); last = now }
                        }
                        output.fd.sync()
                    }
                }
            }
        }
        if (paused.get()) throw IOException("已暂停")
        mutable.value = mutable.value.copy(message = "正在校验语音包")
        if (part.length() != item.bytes) throw IOException("下载未完成")
        if (digest(part) != item.sha256) { part.delete(); throw IOException("语音包校验失败，请重试") }
        check(part.renameTo(destination)) { "语音包保存失败" }
        marker(item).writeText(item.sha256)
    }
    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(128 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) } }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    companion object {
        @Volatile private var instance: VoiceRepository? = null
        fun get(context: Context): VoiceRepository = instance ?: synchronized(this) { instance ?: VoiceRepository(context.applicationContext).also { instance = it } }
    }
}
