// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.status

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Optional CC-BY-4.0 Rime language model. No download/hash/file access on the key path. */
object T9LanguageModel {
    const val bytes = 418080812L
    const val sha256 = "769854c83f6c914a05a1e1abb67c31c00f1ae622a41878e05957bcdee3dbda9a"
    const val url = "https://api.github.com/repos/amzxyz/rime-wanxiang-data/releases/assets/719554076"
    private val mutex = Mutex()
    private val root get() = with(FcitxApplication.getInstance().directBootAwareContext) {
        File(getExternalFilesDir(null) ?: filesDir, "data/rime").apply { mkdirs() }
    }
    private val model get() = File(root, "wanxiang-paopao-769854c8.gram")
    private val marker get() = File(root, "wanxiang-paopao-769854c8.ready")
    @Volatile var available = false
        private set
    val enabled get() = available && AppPrefs.getInstance().internal.t9LanguageModelEnabled.getValue()
    class Request {
        @Volatile var cancelled = false
        @Volatile var connection: HttpURLConnection? = null
        fun cancel() { cancelled = true; connection?.disconnect() }
        fun check() { check(!cancelled) { "已取消下载，进度已保留" } }
    }
    private fun hash(file: File, request: Request? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                request?.check()
                val count = input.read(buffer); if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    /** Called on the existing startup worker; the marker is written only after full verification. */
    fun restore() {
        available = runCatching {
            model.isFile && model.length() == bytes && marker.readText() == "$sha256:${model.lastModified()}"
        }.getOrDefault(false)
    }
    suspend fun verifyInstalled() = withContext(Dispatchers.IO) {
        available = runCatching { model.isFile && model.length() == bytes && hash(model) == sha256 }.getOrDefault(false)
        available
    }
    suspend fun download(request: Request, progress: (Int) -> Unit) = mutex.withLock {
        withContext(Dispatchers.IO) {
            request.check()
            if (verifyInstalled()) return@withContext
            val part = File(root, "wanxiang-paopao-769854c8.part")
            if (part.length() > bytes) part.delete()
            var offset = part.length()
            if (offset < bytes) {
                val connection = URL(url).openConnection() as HttpURLConnection
                request.connection = connection
                try {
                    request.check(); connection.connectTimeout = 20_000; connection.readTimeout = 30_000
                    connection.setRequestProperty("Accept", "application/octet-stream")
                    if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
                    val response = connection.responseCode
                    check(connection.url.protocol == "https") { "模型下载连接无效" }
                    if (response == 206) {
                        check(connection.getHeaderField("Content-Range") == "bytes $offset-${bytes - 1}/$bytes") { "模型已更新，请等待应用更新后下载" }
                    } else {
                        check(response == 200) { "模型下载暂不可用，请稍后重试" }
                        offset = 0
                    }
                    val length = connection.contentLengthLong
                    check(length < 0 || length == bytes - offset) { "模型已更新，请等待应用更新后下载" }
                    RandomAccessFile(part, "rw").use { output ->
                        output.setLength(offset); output.seek(offset)
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(128 * 1024); var lastProgress = -1
                            while (true) {
                                currentCoroutineContext().ensureActive(); request.check()
                                val count = input.read(buffer); if (count < 0) break
                                check(offset + count <= bytes) { "模型大小不匹配" }
                                output.write(buffer, 0, count); offset += count
                                val percent = (offset * 100 / bytes).toInt()
                                if (percent != lastProgress) { lastProgress = percent; progress(percent) }
                            }
                        }
                        output.fd.sync()
                    }
                    check(offset == bytes) { "下载中断，可继续下载" }
                } finally { request.connection = null; connection.disconnect() }
            }
            request.check(); currentCoroutineContext().ensureActive()
            if (hash(part, request) != sha256) { part.delete(); error("模型校验失败，请重试；若上游已更新，请等待应用更新") }
            check(part.renameTo(model)) { "模型保存失败，请检查剩余空间后重试" }
            marker.writeText("$sha256:${model.lastModified()}")
            available = true
        }
    }
    fun schema(base: String): String = when (base) {
        "t9" -> "paopao_default_t9_enhanced"
        "paopao_frost_t9", "paopao_ice_t9" -> base + "_enhanced"
        else -> base
    }
}
