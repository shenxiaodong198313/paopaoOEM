package org.fcitx.fcitx5.android.input.status

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.core.data.DataManager
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.utils.appContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/** Immutable, app-pinned data packages. No network/hash/deployment work on the key path. */
object PinyinDictionaryPackages {
    private const val RIME_BUILD_PREFIX = "usr/share/rime-data/build/"
    private val installations = Mutex()
    data class Entry(val path: String, val size: Long, val sha256: String)
    data class Pack(val version: String, val size: Long, val sha256: String, val url: String, val files: List<Entry>)
    class Request {
        @Volatile var cancelled = false
        @Volatile var connection: HttpURLConnection? = null
        fun cancel() { cancelled = true; connection?.disconnect() }
        fun check() { check(!cancelled) { "已取消" } }
    }
    private val manifest by lazy {
        JSONObject(appContext.assets.open("paopao-dictionaries.json").bufferedReader().use { it.readText() }).also {
            check(it.getString("platform") == "android")
            check(it.getString("version").matches(Regex("[a-f0-9]{64}")))
        }
    }
    val version: String get() = manifest.getString("version")
    private val root: File get() = File(DataManager.dataDir, "dictionaries").apply { mkdirs() }
    private val rimeUser: File get() = with(FcitxApplication.getInstance().directBootAwareContext) {
        File(getExternalFilesDir(null) ?: filesDir, "data/rime")
    }
    private fun entries(json: org.json.JSONArray): List<Entry> = (0 until json.length()).map {
        val value = json.getJSONObject(it)
        Entry(value.getString("path"), value.getLong("size"), value.getString("sha256")).also { entry ->
            require(entry.path.matches(Regex("[a-zA-Z0-9_./-]+")) && !entry.path.startsWith('/') &&
                entry.path.split('/').none { it.isEmpty() || it == "." || it == ".." })
            require(entry.size in 1..(256L * 1024 * 1024) && entry.sha256.matches(Regex("[a-f0-9]{64}")))
        }
    }.also { require(it.map(Entry::path).distinct().size == it.size) }
    fun pack(id: String): Pack {
        require(id == "ice" || id == "default")
        val value = manifest.getJSONObject("downloads").getJSONObject(id)
        return Pack(value.getString("version"), value.getLong("size"), value.getString("sha256"),
            value.getString("url"), entries(value.getJSONArray("files"))).also {
            require(it.version.matches(Regex("[a-f0-9]{64}")) && it.sha256.matches(Regex("[a-f0-9]{64}")))
            require(it.size in 1..(256L * 1024 * 1024))
            require(it.url == "https://paopaoime.com/assets/dictionaries/android/v1/$id-${it.version}.zip")
        }
    }
    private fun directory(id: String): File = File(root, "packages/$id/${pack(id).version}")
    fun ready(id: String): Boolean = id == "frost" || runCatching {
        File(directory(id), "ready").readText() == pack(id).version
    }.getOrDefault(false)
    fun selected(): String {
        val saved = PinyinDictionaryRouting.normalizeProfile(AppPrefs.getInstance().internal.pinyinDictionaryProfile.getValue())
        return saved.takeIf(::ready) ?: "frost"
    }
    fun nativeImeAllowed(id: String): Boolean = id !in setOf("pinyin", "shuangpin", "wbx", "wbpy", "db", "zrm") || wubiEnabled()
    fun wubiEnabled(): Boolean = selected() == "default" && ready("default")
    private fun hash(file: File, request: Request? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(256 * 1024)
            while (true) { request?.check(); val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun verify(directory: File, files: List<Entry>, request: Request? = null) {
        for (entry in files) {
            request?.check(); val file = File(directory, entry.path)
            check(file.canonicalFile == file.absoluteFile && file.isFile && file.length() == entry.size && hash(file, request) == entry.sha256) {
                "词库文件校验失败，请重新下载"
            }
        }
    }
    suspend fun verifySelection(id: String) = withContext(Dispatchers.IO) {
        check(ready(id)) { "请先下载词库" }
        if (id == "frost") verify(DataManager.dataDir, entries(manifest.getJSONObject("profiles").getJSONArray("frost")))
        else verify(directory(id), pack(id).files)
        currentCoroutineContext().ensureActive()
    }
    private fun atomicCopy(source: File, target: File) {
        target.parentFile!!.mkdirs()
        val temp = File(target.parentFile, target.name + ".dictionary-tmp")
        source.inputStream().use { input -> temp.outputStream().use { out -> input.copyTo(out, 256 * 1024); out.fd.sync() } }
        check(temp.renameTo(target)) { "词库加载写入失败" }
    }
    private fun atomicText(file: File, text: String) {
        file.parentFile!!.mkdirs(); val temp = File(file.parentFile, file.name + ".tmp")
        temp.outputStream().use { out -> out.write(text.toByteArray()); out.fd.sync() }
        check(temp.renameTo(file)) { "词库状态保存失败" }
    }
    /** New immutable files only; callers run this before native preparation. */
    fun stage(id: String, source: File = directory(id)) {
        val pack = pack(id)
        for (entry in pack.files) {
            val dest = File(DataManager.dataDir, entry.path)
            if (!dest.exists() || dest.length() != entry.size || hash(dest) != entry.sha256) atomicCopy(File(source, entry.path), dest)
            if (entry.path.startsWith(RIME_BUILD_PREFIX)) {
                val runtime = File(rimeUser, "build/" + entry.path.substringAfterLast('/'))
                if (!runtime.exists() || runtime.length() != entry.size || hash(runtime) != entry.sha256) atomicCopy(File(source, entry.path), runtime)
            }
        }
        RimePredictionSchema.prepare(File(rimeUser, "build"))
    }
    /** Restores packaged assets on the engine startup worker while keeping user databases in place. */
    fun prepareForStartup() {
        T9LanguageModel.restore()
        val userBuild = File(rimeUser, "build")
        val marker = File(rimeUser, "paopao-runtime-version")
        if (!marker.isFile || marker.readText() != version) {
            val build = File(DataManager.dataDir, "usr/share/rime-data/build")
            build.listFiles().orEmpty().filter { it.isFile }.forEach { atomicCopy(it, File(userBuild, it.name)) }
            atomicText(marker, version)
        }
        for (id in listOf("ice", "default")) if (ready(id)) {
            val currentPack = pack(id)
            val restored = File(root, "staged-$version-$id")
            // Rebuilding the asset hierarchy can remove runtime files while leaving the markers in place.
            val filesPresent = currentPack.files.all { entry ->
                val stagedFile = File(DataManager.dataDir, entry.path)
                val mirrorPresent = !entry.path.startsWith(RIME_BUILD_PREFIX) ||
                    File(userBuild, entry.path.substringAfterLast('/')).isFile
                stagedFile.isFile && stagedFile.length() == entry.size && mirrorPresent
            }
            if (!filesPresent || runCatching { restored.readText() }.getOrNull() != currentPack.version) {
                val installed = directory(id)
                verify(installed, currentPack.files)
                stage(id, installed)
                atomicText(restored, currentPack.version)
            }
        }
        RimePredictionSchema.prepare(userBuild)
        val internal = AppPrefs.getInstance().internal
        val preference = internal.pinyinDictionaryProfile
        val migrated = File(root, "offline-policy-v1")
        if (!migrated.exists()) {
            // Preserve the old native setting before the unavailable legacy package falls back.
            val old = preference.sharedPreferences.getString(preference.key, "default")
            if (old == "default") {
                val config = File(rimeUser.parentFile!!.parentFile, "config/conf/pinyin.conf")
                val native = if (config.isFile) config.readLines().firstOrNull { it.startsWith("ShuangpinProfile=") }?.substringAfter('=') else null
                val modes = mapOf("Ziranma" to "natural", "Xiaohe" to "flypy", "ABC" to "abc", "MS" to "mspy", "Sogou" to "sogou", "Ziguang" to "ziguang")
                val mode = if (native == null) "natural" else modes[native]
                // Unsupported custom spellings require an explicit new mode choice.
                internal.frostDoublePinyinSchema.setValue(if (mode != null)
                    PinyinDictionaryRouting.frostDoublePinyinSchemaForMode(mode) else "unsupported-legacy-mode")
            }
            atomicText(migrated, "1")
        }
        val normalized = selected()
        if (preference.getValue() != normalized) preference.setValue(normalized)
        if (!wubiEnabled() && internal.lastFullKeyboardChineseIme.getValue() in setOf("wbx", "wbpy"))
            internal.lastFullKeyboardChineseIme.setValue("pinyin")
    }
    suspend fun install(id: String, request: Request, progress: (Int, Boolean) -> Unit,
                        probe: suspend () -> Unit) = installations.withLock {
        installLocked(id, request, progress, probe)
    }
    // Anonymous installation identity is used only for optional download throttling.
    private fun downloadTicket(id: String, pack: Pack, request: Request): JSONObject {
        val identityFile = File(root, "download-installation")
        val installation = identityFile.takeIf { it.isFile }?.readText()?.trim()
            ?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
            ?: UUID.randomUUID().toString().also { atomicText(identityFile, it) }
        val body = JSONObject().put("installationId", installation).put("platform", "android")
            .put("profile", id).put("version", pack.version).put("sha256", pack.sha256)
        fun post(action: String): JSONObject {
            request.check()
            val connection = URL("https://paopaoime.com/api/v1/dictionaries/$action").openConnection() as HttpURLConnection
            request.connection = connection
            try {
                request.check(); connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000; connection.readTimeout = 15_000
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                check(status == 200) { if (status == 429) "词库下载请求较多，请稍后重试" else "词库下载授权失败，请重试" }
                val bytes = connection.inputStream.use { it.readBytesBounded(16 * 1024) }
                request.check()
                val response = JSONObject(String(bytes, Charsets.UTF_8))
                check(response.getBoolean("success")) { "词库下载授权失败，请重试" }
                return response.getJSONObject("data")
            } finally { request.connection = null; connection.disconnect() }
        }
        val challenge = post("challenge").getString("challenge")
        check(challenge.matches(Regex("[a-f0-9]{64}")))
        body.put("challenge", challenge)
        return post("ticket").also {
            val url = URL(it.getString("downloadUrl"))
            check(url.protocol == "https" && url.host == "paopao-prod-files-paopao.oss-cn-beijing.aliyuncs.com" &&
                url.port == -1 && url.userInfo == null && url.ref == null && url.path == "/dictionaries/android/v1/$id-${pack.version}.zip") { "词库下载地址无效" }
            check(it.getString("downloadToken").matches(Regex("[a-f0-9]{64}")) &&
                it.getLong("size") == pack.size && it.getString("sha256") == pack.sha256) { "词库下载授权无效" }
        }
    }
    private fun java.io.InputStream.readBytesBounded(max: Int): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        while (true) { val count = read(buffer); if (count < 0) break
            check(result.size() + count <= max) { "词库下载授权响应过大" }; result.write(buffer, 0, count) }
        return result.toByteArray()
    }
    private suspend fun installLocked(id: String, request: Request, progress: (Int, Boolean) -> Unit,
                                      probe: suspend () -> Unit) = withContext(Dispatchers.IO) {
        val pack = pack(id); val directory = directory(id)
        if (ready(id)) { verify(directory, pack.files, request); return@withContext }
        directory.parentFile!!.mkdirs()
        val part = File(directory.parentFile, directory.name + ".part")
        var offset = part.takeIf { it.isFile }?.length() ?: 0L
        if (offset > pack.size) { part.delete(); offset = 0L }
        if (offset < pack.size) {
            val ticket = downloadTicket(id, pack, request)
            request.check()
            val connection = URL(ticket.getString("downloadUrl")).openConnection() as HttpURLConnection
            connection.setRequestProperty("x-paopao-download-token", ticket.getString("downloadToken"))
            request.connection = connection; connection.instanceFollowRedirects = false
            connection.connectTimeout = 45_000; connection.readTimeout = 45_000
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            try {
                request.check(); val code = connection.responseCode
                if (code == 206) {
                    check(connection.getHeaderField("Content-Range") == "bytes $offset-${pack.size - 1}/${pack.size}") { "续传范围无效" }
                } else { check(code == 200) { "词库下载失败，请重试" }; offset = 0 }
                var received = offset; var last = 0L
                RandomAccessFile(part, "rw").use { output ->
                    output.setLength(offset); output.seek(offset)
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive(); request.check()
                            val count = input.read(buffer); if (count < 0) break
                            check(received + count <= pack.size) { "词库下载大小超出清单" }
                            output.write(buffer, 0, count); received += count
                            if (System.nanoTime() - last >= 100_000_000) { last = System.nanoTime(); progress((received * 100 / pack.size).toInt(), false) }
                        }
                    }
                    output.fd.sync()
                }
                check(received == pack.size) { "词库下载不完整，请重试" }
            } finally { request.connection = null; connection.disconnect() }
        }
        request.check(); progress(100, true)
        if (hash(part, request) != pack.sha256) { part.delete(); error("词库校验失败，请重新下载") }
        val staging = File(directory.parentFile, directory.name + ".install-" + UUID.randomUUID())
        staging.mkdirs()
        try {
            val allowed = pack.files.associateBy { it.path }; val seen = mutableSetOf<String>()
            ZipInputStream(part.inputStream().buffered()).use { zip ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive(); request.check()
                    val entry = zip.nextEntry ?: break
                    val expected = allowed[entry.name] ?: error("词库包包含未声明文件")
                    check(!entry.isDirectory && seen.add(entry.name)) { "词库包包含重复文件" }
                    val file = File(staging, entry.name); file.parentFile!!.mkdirs()
                    check(file.canonicalPath.startsWith(staging.canonicalPath + File.separator))
                    var size = 0L
                    file.outputStream().use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive(); request.check()
                            val count = zip.read(buffer); if (count < 0) break
                            size += count; check(size <= expected.size) { "词库解压大小无效" }; output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                    check(size == expected.size); zip.closeEntry()
                }
            }
            check(seen == allowed.keys); verify(staging, pack.files, request)
            request.check(); stage(id, staging); probe(); currentCoroutineContext().ensureActive(); request.check()
            if (directory.exists()) check(directory.deleteRecursively())
            check(staging.renameTo(directory)); atomicText(File(directory, "ready"), pack.version)
            atomicText(File(root, "staged-$version-$id"), pack.version); part.delete()
        } finally { staging.deleteRecursively() }
    }
}
