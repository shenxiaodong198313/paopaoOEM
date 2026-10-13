package com.paopaoime.aikeyboard.midscene.shizuku

import android.content.Context
import android.util.Base64
import android.util.Log
import com.paopaoime.aikeyboard.ime.R
import roro.stellar.Stellar
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object MidsceneShizukuManager {
    private const val COMMAND_TIMEOUT_SECONDS = 3L
    private const val SCREENSHOT_TIMEOUT_SECONDS = 5L
    private const val PROCESS_POLL_INTERVAL_MS = 80L
    private const val TAG = "MidsceneShizuku"

    private val initialized = AtomicBoolean(false)
    private val binderAvailable = AtomicBoolean(false)
    @Volatile
    private var lastScreenshotError: String = ""

    private enum class Backend {
        STELLAR,
        NONE
    }

    data class Status(
        val running: Boolean,
        val authorized: Boolean,
        val detailMessage: String,
    ) {
        val installed: Boolean
            get() = running

        val enabled: Boolean
            get() = true

        val ready: Boolean
            get() = running && authorized

        val stateLabel: String
            get() = if (ready) "自动化正常" else "自动化异常"

        val summary: String
            get() = stateLabel
    }

    fun init(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            binderAvailable.set(isStellarBinderReady())
        }
        context.applicationContext
    }

    fun getStatus(context: Context): Status {
        val appContext = context.applicationContext
        val backend = resolveBackend(appContext)
        val running = backend != Backend.NONE
        val authorized = when (backend) {
            Backend.STELLAR -> hasStellarPermission(appContext)
            Backend.NONE -> false
        }
        val imeDisplayName = appContext.getString(R.string.oem_ime_display_name)
        return Status(
            running = running,
            authorized = authorized,
            detailMessage = when {
                running && authorized ->
                    appContext.getString(R.string.automation_authorized_message, imeDisplayName)
                !running ->
                    "未检测到可用的输入法自动化引擎服务，请先在输入法自动化引擎中启动无线 ADB / Root 服务。"
                else ->
                    appContext.getString(R.string.automation_unauthorized_message, imeDisplayName)
            },
        )
    }

    fun isEnabled(context: Context): Boolean {
        return true
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        // 保留兼容空实现：自动化能力现在只取决于系统特权执行状态，
        // 不再额外要求应用内本地开关开启。
        context
    }

    fun isBinderReady(): Boolean {
        val ready = resolveBackend(null) != Backend.NONE
        binderAvailable.set(ready)
        return ready
    }

    fun hasPermission(): Boolean {
        return when (resolveBackend(null)) {
            Backend.STELLAR -> hasStellarPermission(null)
            Backend.NONE -> false
        }
    }

    private fun hasStellarPermission(context: Context?): Boolean {
        context
        if (!isStellarBinderReady()) return false
        return runCatching {
            Stellar.checkSelfPermission("stellar")
        }.getOrElse {
            Log.w(TAG, "checkSelfPermission failed", it)
            false
        }
    }

    private fun isStellarBinderReady(): Boolean {
        return runCatching { Stellar.pingBinder() }.getOrDefault(false)
    }

    private fun resolveBackend(context: Context?): Backend {
        context
        return if (isStellarBinderReady()) Backend.STELLAR else Backend.NONE
    }

    fun executeShellCommand(command: String): Pair<Boolean, String> {
        if (!isBinderReady()) {
            binderAvailable.set(false)
            return false to "自动化特权服务未就绪"
        }
        if (!hasPermission()) {
            return false to "自动化特权服务未授权"
        }
        return try {
            val process = createPrivilegedProcess(command)
            val stdoutBuffer = StringBuilder()
            val stderrBuffer = StringBuilder()
            val stdoutThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (stdoutBuffer.isNotEmpty()) stdoutBuffer.append('\n')
                            stdoutBuffer.append(line)
                        }
                    }
                }
            }
            val stderrThread = Thread {
                runCatching {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (stderrBuffer.isNotEmpty()) stderrBuffer.append('\n')
                            stderrBuffer.append(line)
                        }
                    }
                }
            }
            stdoutThread.start()
            stderrThread.start()
            val exitCode = waitForExitCode(process, COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (exitCode == null) {
                process.destroy()
                return false to "Shell 命令超时"
            }
            stdoutThread.join(200L)
            stderrThread.join(200L)
            val stdout = stdoutBuffer.toString()
            val stderr = stderrBuffer.toString()
            val output = buildString {
                if (stdout.isNotBlank()) append(stdout.trim())
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(stderr.trim())
                }
            }.ifBlank { "exitCode=$exitCode" }
            (exitCode == 0) to output
        } catch (throwable: Throwable) {
            binderAvailable.set(false)
            false to (throwable.message ?: throwable.javaClass.simpleName)
        }
    }

    fun dispatchShellCommand(command: String): Pair<Boolean, String> {
        if (!isBinderReady()) {
            binderAvailable.set(false)
            return false to "自动化特权服务未就绪"
        }
        if (!hasPermission()) {
            return false to "自动化特权服务未授权"
        }
        return try {
            createPrivilegedProcess("$command >/dev/null 2>&1 &")
            true to "命令已派发"
        } catch (throwable: Throwable) {
            binderAvailable.set(false)
            false to (throwable.message ?: throwable.javaClass.simpleName)
        }
    }

    fun captureScreenshotBase64(context: Context? = null): String? {
        val appContext = context?.applicationContext
        val primaryBackend = resolveBackend(appContext)
        if (primaryBackend == Backend.NONE) {
            binderAvailable.set(false)
            lastScreenshotError = "自动化特权服务未就绪"
            return null
        }
        if (!hasPermission(primaryBackend, appContext)) {
            lastScreenshotError = "自动化特权服务未授权"
            return null
        }
        captureScreenshotBase64ByBackend(primaryBackend, appContext)?.let { return it }
        if (lastScreenshotError.isBlank()) {
            lastScreenshotError = "自动化截图失败: backend=${primaryBackend.name}, 未返回详细错误"
            Log.w(TAG, lastScreenshotError)
        }
        return null
    }

    private fun captureScreenshotBase64ByBackend(
        backend: Backend,
        context: Context?,
    ): String? {
        captureScreenshotBase64ViaStdout(backend)?.let { return it }
        if (context != null) {
            captureScreenshotBase64ViaAppReadableFile(context, backend)?.let { return it }
            captureScreenshotBase64ViaFileFallback(context, backend)?.let { return it }
        }
        if (lastScreenshotError.isBlank()) {
            lastScreenshotError = "自动化截图失败: backend=${backend.name}, stdout 与文件回退均未返回详细错误"
            Log.w(TAG, lastScreenshotError)
        }
        return null
    }

    private fun captureScreenshotBase64ViaStdout(backend: Backend): String? {
        return try {
            val process = createPrivilegedProcess("screencap -p", backend)
            val stdoutBuffer = ByteArrayOutputStream()
            val stderrBuffer = ByteArrayOutputStream()
            val stdoutThread = Thread {
                process.inputStream.use { input ->
                    input.copyTo(stdoutBuffer)
                }
            }
            val stderrThread = Thread {
                process.errorStream.use { error ->
                    error.copyTo(stderrBuffer)
                }
            }
            stdoutThread.start()
            stderrThread.start()
            val exitCode = waitForExitCode(process, SCREENSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (exitCode == null) {
                process.destroy()
                lastScreenshotError = "screencap 超时"
                Log.w(TAG, lastScreenshotError)
                return null
            }
            stdoutThread.join(1000L)
            stderrThread.join(1000L)
            val pngBytes = stdoutBuffer.toByteArray()
            val stderr = stderrBuffer.toString(Charsets.UTF_8.name()).trim()
            if (exitCode != 0 || pngBytes.isEmpty()) {
                lastScreenshotError = buildString {
                    append("screencap stdout 失败: backend=")
                    append(backend.name)
                    append(", exitCode=$exitCode")
                    if (stderr.isNotBlank()) {
                        append(", stderr=")
                        append(stderr)
                    } else if (pngBytes.isEmpty()) {
                        append(", stdout 为空")
                    }
                }
                Log.w(TAG, lastScreenshotError)
                return null
            }
            val base64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
            lastScreenshotError = ""
            "data:image/png;base64,$base64"
        } catch (throwable: Throwable) {
            binderAvailable.set(false)
            lastScreenshotError = throwable.message ?: throwable.javaClass.simpleName
            Log.w(TAG, "captureScreenshotBase64ViaStdout failed backend=${backend.name}", throwable)
            null
        }
    }

    private fun captureScreenshotBase64ViaAppReadableFile(context: Context, backend: Backend): String? {
        val appContext = context.applicationContext
        val directory = appContext.getExternalFilesDir("automation")?.apply { mkdirs() }
        if (directory == null) {
            lastScreenshotError = "screencap 可读缓存目录不可用: backend=${backend.name}"
            Log.w(TAG, lastScreenshotError)
            return null
        }
        val targetFile = directory.resolve("midscene_screencap_stage.png")
        val filePath = escapeShellArg(targetFile.absolutePath)
        val command =
            "rm -f '$filePath'; " +
                "screencap -p '$filePath' >/dev/null 2>&1; " +
                "status=$?; " +
                "exit ${'$'}status"
        return try {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val process = createPrivilegedProcess(command, backend)
            val stderrBuffer = ByteArrayOutputStream()
            val stderrThread = Thread {
                process.errorStream.use { error ->
                    error.copyTo(stderrBuffer)
                }
            }
            stderrThread.start()
            val exitCode = waitForExitCode(process, SCREENSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (exitCode == null) {
                process.destroy()
                lastScreenshotError = "screencap 可读文件回退超时: backend=${backend.name}"
                Log.w(TAG, lastScreenshotError)
                return null
            }
            stderrThread.join(1000L)
            val stderr = stderrBuffer.toString(Charsets.UTF_8.name()).trim()
            if (exitCode != 0) {
                lastScreenshotError = buildString {
                    append("screencap 可读文件回退失败: backend=")
                    append(backend.name)
                    append(", exitCode=$exitCode")
                    if (stderr.isNotBlank()) {
                        append(", stderr=")
                        append(stderr)
                    }
                }
                Log.w(TAG, lastScreenshotError)
                return null
            }
            if (!targetFile.exists()) {
                lastScreenshotError = "screencap 可读文件回退失败: backend=${backend.name}, 目标文件不存在"
                Log.w(TAG, lastScreenshotError)
                return null
            }
            val pngBytes = targetFile.readBytes()
            if (pngBytes.isEmpty()) {
                lastScreenshotError = "screencap 可读文件回退结果为空: backend=${backend.name}"
                Log.w(TAG, lastScreenshotError)
                return null
            }
            val base64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
            lastScreenshotError = ""
            "data:image/png;base64,$base64"
        } catch (throwable: Throwable) {
            binderAvailable.set(false)
            lastScreenshotError = throwable.message ?: throwable.javaClass.simpleName
            Log.w(TAG, "captureScreenshotBase64ViaAppReadableFile failed backend=${backend.name}", throwable)
            null
        } finally {
            runCatching {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            }
        }
    }

    private fun captureScreenshotBase64ViaFileFallback(context: Context, backend: Backend): String? {
        val appContext = context.applicationContext
        val tempFilePath =
            if (backend == Backend.STELLAR) {
                "/sdcard/Download/midscene_screencap_fallback.png"
            } else {
                appContext.cacheDir.resolve("midscene_screencap_fallback.png").absolutePath
            }
        val filePath = escapeShellArg(tempFilePath)
        val command =
            "rm -f '$filePath'; " +
                "screencap -p '$filePath' >/dev/null 2>&1; " +
                "status=$?; " +
                "if [ ${'$'}status -eq 0 ]; then " +
                "if command -v base64 >/dev/null 2>&1; then base64 '$filePath'; " +
                "elif command -v toybox >/dev/null 2>&1; then toybox base64 '$filePath'; " +
                "else cat '$filePath'; fi; " +
                "fi; " +
                "rm -f '$filePath'; " +
                "exit ${'$'}status"
        return try {
            val process = createPrivilegedProcess(command, backend)
            val stdoutBuffer = ByteArrayOutputStream()
            val stderrBuffer = ByteArrayOutputStream()
            val stdoutThread = Thread {
                process.inputStream.use { input ->
                    input.copyTo(stdoutBuffer)
                }
            }
            val stderrThread = Thread {
                process.errorStream.use { error ->
                    error.copyTo(stderrBuffer)
                }
            }
            stdoutThread.start()
            stderrThread.start()
            val exitCode = waitForExitCode(process, SCREENSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (exitCode == null) {
                process.destroy()
                lastScreenshotError = "screencap 文件回退超时: backend=${backend.name}"
                Log.w(TAG, lastScreenshotError)
                return null
            }
            stdoutThread.join(1000L)
            stderrThread.join(1000L)
            val stdoutText = stdoutBuffer.toString(Charsets.UTF_8.name()).replace("\\s+".toRegex(), "")
            val stderr = stderrBuffer.toString(Charsets.UTF_8.name()).trim()
            if (exitCode != 0 || stdoutText.isBlank()) {
                lastScreenshotError = buildString {
                    append("screencap 文件回退失败: backend=")
                    append(backend.name)
                    append(", exitCode=$exitCode")
                    if (stderr.isNotBlank()) {
                        append(", stderr=")
                        append(stderr)
                    } else if (stdoutText.isBlank()) {
                        append(", stdout 为空")
                    }
                }
                Log.w(TAG, lastScreenshotError)
                return null
            }
            val pngBytes = runCatching { Base64.decode(stdoutText, Base64.DEFAULT) }.getOrElse {
                lastScreenshotError = "screencap 文件回退 base64 解码失败: backend=${backend.name}, ${it.message}"
                Log.w(TAG, lastScreenshotError, it)
                return null
            }
            if (pngBytes.isEmpty()) {
                lastScreenshotError = "screencap 文件回退结果为空: backend=${backend.name}"
                Log.w(TAG, lastScreenshotError)
                return null
            }
            val base64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
            lastScreenshotError = ""
            Log.d(TAG, "captureScreenshotBase64ViaFileFallback succeeded backend=${backend.name} bytes=${pngBytes.size}")
            "data:image/png;base64,$base64"
        } catch (throwable: Throwable) {
            binderAvailable.set(false)
            lastScreenshotError = throwable.message ?: throwable.javaClass.simpleName
            Log.w(TAG, "captureScreenshotBase64ViaFileFallback failed backend=${backend.name}", throwable)
            null
        }
    }

    private fun escapeShellArg(value: String): String {
        return value.replace("'", "'\\''")
    }

    fun getLastScreenshotError(): String {
        return lastScreenshotError
    }

    fun performHome(): Pair<Boolean, String> {
        return dispatchShellCommand("input keyevent 3")
    }

    fun performBack(): Pair<Boolean, String> {
        return dispatchShellCommand("input keyevent 4")
    }

    fun performTapCenter(context: Context): Pair<Boolean, String> {
        val dm = context.resources.displayMetrics
        val x = dm.widthPixels / 2
        val y = dm.heightPixels / 2
        return dispatchShellCommand("input tap $x $y")
    }

    fun launchWechat(): Pair<Boolean, String> {
        return executeShellCommand("monkey -p com.tencent.mm -c android.intent.category.LAUNCHER 1")
    }

    private fun createStellarProcess(command: String): Process {
        return Stellar.newProcess(
            arrayOf("sh", "-c", command),
            null,
            null
        )
    }

    private fun createPrivilegedProcess(command: String, backend: Backend = resolveBackend(null)): Process {
        return when (backend) {
            Backend.STELLAR -> createStellarProcess(command)
            Backend.NONE -> error("自动化特权服务未就绪")
        }
    }

    private fun isBackendReady(backend: Backend): Boolean {
        return when (backend) {
            Backend.STELLAR -> isStellarBinderReady()
            Backend.NONE -> false
        }
    }

    private fun hasPermission(backend: Backend, context: Context?): Boolean {
        return when (backend) {
            Backend.STELLAR -> hasStellarPermission(context)
            Backend.NONE -> false
        }
    }

    private fun waitForExitCode(
        process: Process,
        timeout: Long,
        unit: TimeUnit
    ): Int? {
        val deadlineAt = System.currentTimeMillis() + unit.toMillis(timeout)
        while (System.currentTimeMillis() <= deadlineAt) {
            try {
                return process.exitValue()
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(PROCESS_POLL_INTERVAL_MS)
            } catch (throwable: IllegalArgumentException) {
                val message = throwable.message.orEmpty()
                if (!message.contains("process hasn't exited", ignoreCase = true)) {
                    throw throwable
                }
                Thread.sleep(PROCESS_POLL_INTERVAL_MS)
            }
        }
        return null
    }
}
