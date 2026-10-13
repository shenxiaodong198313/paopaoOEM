package com.paopaoime.aikeyboard.midscene.shizuku

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.Html
import android.os.Build
import android.util.Log
import com.paopaoime.aikeyboard.ime.bridge.ImeTextBridge
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeErrorCode
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeWindowState
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommand
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommandVerification
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommandResult
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeRuntimeStatus
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeScreenState
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeVerificationSnapshot
import com.paopaoime.aikeyboard.midscene.overlay.MidsceneExecutionOverlayService
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class MidsceneShizukuRuntime(
    context: Context
) {
    companion object {
        private const val TAG = "MidsceneShizukuRuntime"
        private const val YADB_ASSET_PATH = "midscene/yadb"
        private const val YADB_DEVICE_PATH = "/data/local/tmp/yadb"
        private const val INPUT_GUARD_VALID_MS = 8_000L
        private const val INPUT_VERIFY_RETRY_COUNT = 4
        private const val INPUT_VERIFY_RETRY_INTERVAL_MS = 180L
    }

    private val appContext = context.applicationContext
    @Volatile
    private var lastKnownForegroundSnapshot: ForegroundSnapshot? = null
    @Volatile
    private var yadbPreparedOnDevice = false
    @Volatile
    private var lastInputGuardState: LastInputGuardState? = null

    fun status(): MidsceneBridgeRuntimeStatus {
        val automationStatus = MidsceneShizukuManager.getStatus(appContext)
        val ready = automationStatus.ready
        return MidsceneBridgeRuntimeStatus(
            executorKind = if (ready) "stellar" else "plan-only",
            deviceInfo = if (ready) "android-stellar-bridge+screencap" else "android-stellar-pending",
            accessibilityBridgeConnected = false,
            automationServiceConnected = automationStatus.running,
            automationAuthorized = automationStatus.authorized,
            screenshotAvailable = ready,
            ready = ready
        )
    }

    suspend fun captureScreenState(): MidsceneBridgeScreenState? {
        MidsceneExecutionOverlayService.setCaptureSuppressedAndAwaitNextFrame(appContext, true)
        val screenshotBase64 = try {
            MidsceneShizukuManager.captureScreenshotBase64(appContext)
        } finally {
            MidsceneExecutionOverlayService.setCaptureSuppressed(appContext, false)
        } ?: return null
        val metrics = appContext.resources.displayMetrics
        val foregroundSnapshot = readStableForegroundSnapshot(allowFallback = false)
        val focusedInputSnapshot = readFocusedInputSnapshot()
        val packageName = foregroundSnapshot?.packageName
        val className = foregroundSnapshot?.className
        return MidsceneBridgeScreenState(
            width = max(1, metrics.widthPixels),
            height = max(1, metrics.heightPixels),
            devicePixelRatio = metrics.density,
            screenshotBase64 = screenshotBase64,
            currentApp = packageName,
            currentPage = className,
            windowState = MidsceneBridgeWindowState(
                packageName = packageName,
                className = className,
                source = foregroundSnapshot?.source,
                sampleCount = foregroundSnapshot?.sampleCount ?: 0,
                focusedEditable = focusedInputSnapshot?.focusedEditable == true,
                focusedText = focusedInputSnapshot?.text,
                focusedHint = focusedInputSnapshot?.hint,
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun executeCommand(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        logCommandStart(command)
        return when (command.type.trim().lowercase()) {
            "tap" -> executeTap(command)
            "double_tap" -> executeDoubleTap(command)
            "long_press" -> executeLongPress(command)
            "swipe", "scroll" -> executeSwipe(command)
            "input_text", "input" -> executeInputText(command)
            "launch_app" -> executeLaunchApp(command)
            "terminate_app" -> executeTerminateApp(command)
            "run_adb_shell" -> executeRunAdbShell(command)
            "back" -> executeNavigationCommand(
                shellCommand = "input keyevent 4",
                strategy = "foreground_changed",
                successMessage = "back completed"
            )
            "home" -> executeNavigationCommand(
                shellCommand = "input keyevent 3",
                strategy = "home_foreground",
                successMessage = "home completed",
                expectHomeScreen = true
            )
            "recent" -> executeNavigationCommand(
                shellCommand = "input keyevent 187",
                strategy = "foreground_changed",
                successMessage = "recent completed"
            )
            "wait" -> {
                executeWaitWithForegroundGuard(command)
            }

            "key", "keyevent" -> executeKey(command)
            "capture" -> {
                if (captureScreenState() != null) {
                    MidsceneBridgeCommandResult(success = true, message = "capture completed")
                } else {
                    MidsceneBridgeCommandResult(
                        success = false,
                        errorCode = MidsceneBridgeErrorCode.SCREENSHOT_FAILED,
                        message = MidsceneShizukuManager.getLastScreenshotError().ifBlank { "截图失败" },
                        retryable = true
                    )
                }
            }
            else -> MidsceneBridgeCommandResult(
                success = false,
                errorCode = MidsceneBridgeErrorCode.UNSUPPORTED_ACTION,
                message = "输入法自动化引擎尚未支持动作: ${command.type}",
                retryable = false
            )
        }
    }

    private suspend fun executeTap(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        val x = command.x
        val y = command.y
        if (x == null || y == null) {
            return MidsceneBridgeCommandResult(
                success = false,
                errorCode = MidsceneBridgeErrorCode.INVALID_PARAMS,
                message = "输入法自动化引擎当前仅支持坐标 tap",
                retryable = false
            )
        }
        return dispatchPointerCommandWithForegroundGuard(
            shellCommand = "input tap $x $y",
            gestureType = "tap",
            x = x,
            y = y
        )
    }

    private fun executeDoubleTap(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        val x = command.x
        val y = command.y
        if (x == null || y == null) {
            return MidsceneBridgeCommandResult(
                success = false,
                errorCode = MidsceneBridgeErrorCode.INVALID_PARAMS,
                message = "double_tap 缺少坐标",
                retryable = false
            )
        }
        return dispatchAsyncCommand("input tap $x $y && input tap $x $y")
    }

    private suspend fun executeLongPress(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        val x = command.x
        val y = command.y
        if (x == null || y == null) {
            return MidsceneBridgeCommandResult(
                success = false,
                errorCode = MidsceneBridgeErrorCode.INVALID_PARAMS,
                message = "long_press 缺少坐标",
                retryable = false
            )
        }
        val durationMs = max(300L, command.durationMs ?: 2000L)
        return dispatchPointerCommandWithForegroundGuard(
            shellCommand = "input swipe $x $y $x $y $durationMs",
            gestureType = "long_press",
            x = x,
            y = y
        )
    }

    private fun executeSwipe(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        val fromX = command.fromX
        val fromY = command.fromY
        val toX = command.toX
        val toY = command.toY
        if (fromX == null || fromY == null || toX == null || toY == null) {
            return MidsceneBridgeCommandResult(
                success = false,
                errorCode = MidsceneBridgeErrorCode.INVALID_PARAMS,
                message = "swipe 缺少起止坐标",
                retryable = false
            )
        }
        val durationMs = max(50L, command.durationMs ?: 350L)
        return dispatchAsyncCommand("input swipe $fromX $fromY $toX $toY $durationMs")
    }

    private suspend fun executeInputText(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        val mode = normalizeInputMode(command.mode)
        val text = command.text ?: ""

        val focusResult = focusInputTarget(command)
        if (!focusResult.success) {
            return focusResult
        }

        val currentForeground = readForegroundComponentOnce() ?: lastKnownForegroundSnapshot

        maybeSkipRepeatedReplace(mode, text, currentForeground)?.let { return it }

        if (mode != "typeOnly") {
            val clearResult = clearFocusedInput()
            if (!clearResult.success) {
                return clearResult
            }
            clearInputGuardState()
            delay(180L)
        }

        if (mode == "clear" || text.isEmpty()) {
            clearInputGuardState()
            return MidsceneBridgeCommandResult(success = true, message = "input completed")
        }

        val imeResult = inputTextViaImeBridge(text)
        if (imeResult?.success == true) {
            recordInputGuardState(text, currentForeground, imeResult, "ime")
            return imeResult
        }

        val shouldUseYadb = shouldUseYadbForText(text)

        if (shouldUseYadb) {
            return inputTextViaYadb(text).also { result ->
                recordInputGuardState(text, currentForeground, result, "yadb")
            }
        }

        val shellResult = inputTextViaShell(text)
        if (shellResult.success) {
            recordInputGuardState(text, currentForeground, shellResult, "shell")
            return shellResult
        }

        if (imeResult?.success == true) {
            recordInputGuardState(text, currentForeground, imeResult, "ime")
            return imeResult
        }

        if (imeResult != null) {
            return imeResult
        }

        return shellResult
    }

    private suspend fun executeLaunchApp(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        val resolvedPackage = resolvePackageName(
            packageName = command.packageName,
            appName = command.appName
        ) ?: return MidsceneBridgeCommandResult(
            success = false,
            errorCode = MidsceneBridgeErrorCode.APP_NOT_FOUND,
            message = "未找到可启动应用: ${command.appName ?: command.packageName.orEmpty()}",
            retryable = false
        )
        val launchComponent = resolveLaunchComponent(resolvedPackage)
        val attempts = buildList {
            if (launchComponent != null) {
                add("am start -W -n $launchComponent -a android.intent.action.MAIN -c android.intent.category.LAUNCHER")
            }
            add("monkey -p $resolvedPackage -c android.intent.category.LAUNCHER 1")
        }
        val outputs = mutableListOf<String>()
        for (launchCommand in attempts) {
            val (success, output) = MidsceneShizukuManager.executeShellCommand(launchCommand)
            outputs += "${launchCommand}: ${output.ifBlank { if (success) "ok" else "failed" }}"
            if (!success) continue
            val focusedPackage = waitForForegroundPackage(targetPackage = resolvedPackage)
            if (focusedPackage == resolvedPackage) {
                return MidsceneBridgeCommandResult(
                    success = true,
                    message = buildString {
                        append("launch app completed")
                        append(": ")
                        append(focusedPackage)
                        if (launchComponent != null) {
                            append(" via ")
                            append(launchComponent)
                        }
                    }
                )
            }
        }
        val focusedPackage = readForegroundPackage().orEmpty()
        return MidsceneBridgeCommandResult(
            success = false,
            errorCode = MidsceneBridgeErrorCode.APP_LAUNCH_NOT_IN_FOREGROUND,
            message = buildString {
                append("启动后前台应用未切换到目标包")
                if (focusedPackage.isNotBlank()) {
                    append(": current=")
                    append(focusedPackage)
                }
                if (outputs.isNotEmpty()) {
                    append(" | ")
                    append(outputs.joinToString(" || "))
                }
            },
            retryable = true
        )
    }

    private fun executeTerminateApp(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        val resolvedPackage = resolvePackageName(
            packageName = command.packageName ?: command.uri,
            appName = command.appName ?: command.uri
        ) ?: return MidsceneBridgeCommandResult(
            success = false,
            errorCode = MidsceneBridgeErrorCode.APP_NOT_FOUND,
            message = "未找到可终止应用: ${command.uri ?: command.appName ?: command.packageName.orEmpty()}",
            retryable = false
        )
        return dispatchAsyncCommand("am force-stop $resolvedPackage")
    }

    private fun executeRunAdbShell(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        val shellCommand = command.command?.trim().orEmpty()
        if (shellCommand.isBlank()) {
            return MidsceneBridgeCommandResult(
                success = false,
                errorCode = MidsceneBridgeErrorCode.INVALID_PARAMS,
                message = "run_adb_shell 缺少 command",
                retryable = false
            )
        }
        return dispatchCommand(shellCommand)
    }

    private suspend fun executeKey(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        val keyCode = command.keyCode
        if (keyCode != null) {
            return when (keyCode) {
                3 -> executeNavigationCommand(
                    shellCommand = "input keyevent 3",
                    strategy = "home_foreground",
                    successMessage = "key home completed",
                    expectHomeScreen = true
                )
                4 -> executeNavigationCommand(
                    shellCommand = "input keyevent 4",
                    strategy = "foreground_changed",
                    successMessage = "key back completed"
                )
                187 -> executeNavigationCommand(
                    shellCommand = "input keyevent 187",
                    strategy = "foreground_changed",
                    successMessage = "key recent completed"
                )
                else -> dispatchAsyncCommand("input keyevent $keyCode")
            }
        }
        val keyName = command.keyName?.trim().orEmpty()
        if (keyName.isBlank()) {
            return MidsceneBridgeCommandResult(
                success = false,
                errorCode = MidsceneBridgeErrorCode.INVALID_KEY,
                message = "缺少 keyName",
                retryable = false
            )
        }
        val normalized = normalizeKeyName(keyName)
        return when (normalized) {
            "3" -> executeNavigationCommand(
                shellCommand = "input keyevent 3",
                strategy = "home_foreground",
                successMessage = "key home completed",
                expectHomeScreen = true
            )
            "4" -> executeNavigationCommand(
                shellCommand = "input keyevent 4",
                strategy = "foreground_changed",
                successMessage = "key back completed"
            )
            "187" -> executeNavigationCommand(
                shellCommand = "input keyevent 187",
                strategy = "foreground_changed",
                successMessage = "key recent completed"
            )
            else -> dispatchAsyncCommand("input keyevent $normalized")
        }
    }

    private suspend fun executeNavigationCommand(
        shellCommand: String,
        strategy: String,
        successMessage: String,
        expectHomeScreen: Boolean = false
    ): MidsceneBridgeCommandResult {
        val beforeSnapshot = readStableForegroundSnapshot()
        val dispatchResult = dispatchAsyncCommand(shellCommand)
        if (!dispatchResult.success) {
            return dispatchResult
        }
        delay(if (expectHomeScreen) 500L else 350L)
        val afterSnapshot = readStableForegroundSnapshot()
        val matched = when {
            beforeSnapshot == null || afterSnapshot == null -> null
            expectHomeScreen -> isHomePackage(afterSnapshot.packageName) || hasForegroundChanged(beforeSnapshot, afterSnapshot)
            else -> hasForegroundChanged(beforeSnapshot, afterSnapshot)
        }
        val verification = MidsceneBridgeCommandVerification(
            strategy = strategy,
            expectedText = if (expectHomeScreen) {
                "foreground should switch to launcher or leave the current app"
            } else {
                "foreground should change after navigation"
            },
            matched = matched,
            before = beforeSnapshot?.toVerificationSnapshot(),
            after = afterSnapshot?.toVerificationSnapshot()
        )
        val verificationMessage = buildNavigationVerificationMessage(
            baseMessage = successMessage,
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = afterSnapshot,
            matched = matched,
            expectHomeScreen = expectHomeScreen
        )
        return MidsceneBridgeCommandResult(
            success = true,
            message = verificationMessage,
            verified = matched,
            verification = verification
        )
    }

    private suspend fun executeWaitWithForegroundGuard(
        command: MidsceneBridgeCommand
    ): MidsceneBridgeCommandResult {
        val beforeSnapshot = readStableForegroundSnapshot()
        val durationMs = max(0L, command.durationMs ?: 500L)
        delay(durationMs)
        if (!isWechatPackage(beforeSnapshot?.packageName)) {
            return MidsceneBridgeCommandResult(success = true, message = "wait completed")
        }
        val afterSnapshot = readStableForegroundSnapshot()
        if (!hasUnexpectedExternalJumpFromWechat(beforeSnapshot, afterSnapshot)) {
            return MidsceneBridgeCommandResult(success = true, message = "wait completed")
        }
        val jumpedPackage = afterSnapshot?.packageName.orEmpty()
        Log.w(
            TAG,
            "executeWaitWithForegroundGuard detected unexpected external jump during wait: " +
                "before=${beforeSnapshot?.packageName}/${beforeSnapshot?.className} " +
                "after=${afterSnapshot?.packageName}/${afterSnapshot?.className} durationMs=$durationMs"
        )
        dispatchAsyncCommand("input keyevent 4")
        delay(300L)
        val recoveredSnapshot = readStableForegroundSnapshot()
        val recoveredToWechat = isWechatPackage(recoveredSnapshot?.packageName)
        return MidsceneBridgeCommandResult(
            success = false,
            errorCode = MidsceneBridgeErrorCode.SHIZUKU_COMMAND_FAILED,
            message = if (recoveredToWechat) {
                "检测到等待期间从微信异常跳到外部应用 ${jumpedPackage}，已自动返回微信，请重试"
            } else {
                "检测到等待期间从微信异常跳到外部应用 ${jumpedPackage}，已尝试自动返回，请重试"
            },
            retryable = true,
            verified = false,
            verification = MidsceneBridgeCommandVerification(
                strategy = "wechat_wait_external_jump_guard",
                expectedText = "foreground should remain in WeChat during wait",
                matched = recoveredToWechat,
                before = beforeSnapshot?.toVerificationSnapshot(),
                after = afterSnapshot?.toVerificationSnapshot()
            )
        )
    }

    private suspend fun dispatchPointerCommandWithForegroundGuard(
        shellCommand: String,
        gestureType: String,
        x: Int,
        y: Int
    ): MidsceneBridgeCommandResult {
        val beforeSnapshot = readStableForegroundSnapshot()
        val dispatchResult = dispatchAsyncCommand(shellCommand)
        if (!dispatchResult.success) {
            return dispatchResult
        }
        if (!shouldGuardWechatUpperRightGesture(beforeSnapshot, x, y)) {
            return dispatchResult
        }
        delay(320L)
        val afterSnapshot = readStableForegroundSnapshot()
        if (!hasUnexpectedExternalJumpFromWechat(beforeSnapshot, afterSnapshot)) {
            return dispatchResult
        }
        val jumpedPackage = afterSnapshot?.packageName.orEmpty()
        Log.w(
            TAG,
            "dispatchPointerCommandWithForegroundGuard detected unexpected external jump: " +
                "gesture=$gestureType x=$x y=$y before=${beforeSnapshot?.packageName}/${beforeSnapshot?.className} " +
                "after=${afterSnapshot?.packageName}/${afterSnapshot?.className}"
        )
        dispatchAsyncCommand("input keyevent 4")
        delay(280L)
        val recoveredSnapshot = readStableForegroundSnapshot()
        val recoveredToWechat = isWechatPackage(recoveredSnapshot?.packageName)
        return MidsceneBridgeCommandResult(
            success = false,
            errorCode = MidsceneBridgeErrorCode.SHIZUKU_COMMAND_FAILED,
            message = if (recoveredToWechat) {
                "检测到微信右上区域手势触发外部应用 ${jumpedPackage}，已自动返回微信，请重试并改用更保守点位"
            } else {
                "检测到微信右上区域手势触发外部应用 ${jumpedPackage}，已尝试自动返回，请重试"
            },
            retryable = true,
            verified = false,
            verification = MidsceneBridgeCommandVerification(
                strategy = "wechat_upper_right_external_jump_guard",
                expectedText = "foreground should remain in WeChat after gesture",
                matched = recoveredToWechat,
                before = beforeSnapshot?.toVerificationSnapshot(),
                after = afterSnapshot?.toVerificationSnapshot()
            )
        )
    }

    private fun dispatchCommand(shellCommand: String): MidsceneBridgeCommandResult {
        val (success, output) = MidsceneShizukuManager.executeShellCommand(shellCommand)
        return if (success) {
            MidsceneBridgeCommandResult(success = true, message = output.ifBlank { "命令已派发" })
        } else {
            MidsceneBridgeCommandResult(
                success = false,
                errorCode = MidsceneBridgeErrorCode.SHIZUKU_COMMAND_FAILED,
                message = output.ifBlank { "输入法自动化引擎命令执行失败" },
                retryable = true
            )
        }
    }

    private fun dispatchAsyncCommand(shellCommand: String): MidsceneBridgeCommandResult {
        val beforeSnapshot = readForegroundComponentOnce() ?: lastKnownForegroundSnapshot
        Log.i(
            TAG,
            "dispatchAsyncCommand start shell=\"$shellCommand\" " +
                "before=${formatForegroundSnapshot(beforeSnapshot)}"
        )
        val (success, output) = MidsceneShizukuManager.dispatchShellCommand(shellCommand)
        val afterSnapshot = readForegroundComponentOnce() ?: lastKnownForegroundSnapshot
        Log.i(
            TAG,
            "dispatchAsyncCommand end shell=\"$shellCommand\" success=$success " +
                "after=${formatForegroundSnapshot(afterSnapshot)} output=${output.ifBlank { "<empty>" }}"
        )
        return if (success) {
            MidsceneBridgeCommandResult(success = true, message = output.ifBlank { "命令已派发" })
        } else {
            MidsceneBridgeCommandResult(
                success = false,
                errorCode = MidsceneBridgeErrorCode.SHIZUKU_COMMAND_FAILED,
                message = output.ifBlank { "输入法自动化引擎命令派发失败" },
                retryable = true
            )
        }
    }

    private fun shouldGuardWechatUpperRightGesture(
        beforeSnapshot: ForegroundSnapshot?,
        x: Int,
        y: Int
    ): Boolean {
        if (!isWechatPackage(beforeSnapshot?.packageName)) {
            return false
        }
        val className = beforeSnapshot?.className.orEmpty()
        val looksLikeWechatTimelineOrLauncher =
            className.contains("LauncherUI", ignoreCase = true) ||
                className.contains("SnsTimeLineUI", ignoreCase = true) ||
                className.contains("SnsBrowseUI", ignoreCase = true)
        if (!looksLikeWechatTimelineOrLauncher) {
            return false
        }
        val metrics = appContext.resources.displayMetrics
        val screenWidth = max(1, metrics.widthPixels)
        val screenHeight = max(1, metrics.heightPixels)
        val inUpperRightRiskZone =
            x >= (screenWidth * 0.80f).toInt() &&
                y <= (screenHeight * 0.16f).toInt()
        return inUpperRightRiskZone
    }

    private fun hasUnexpectedExternalJumpFromWechat(
        beforeSnapshot: ForegroundSnapshot?,
        afterSnapshot: ForegroundSnapshot?
    ): Boolean {
        if (!isWechatPackage(beforeSnapshot?.packageName)) {
            return false
        }
        val afterPackage = afterSnapshot?.packageName.orEmpty()
        if (afterPackage.isBlank()) {
            return false
        }
        return !isWechatPackage(afterPackage) && !isHomePackage(afterPackage)
    }

    private fun isWechatPackage(packageName: String?): Boolean {
        return packageName?.trim().equals("com.tencent.mm", ignoreCase = true)
    }

    private fun logCommandStart(command: MidsceneBridgeCommand) {
        val foregroundSnapshot = readForegroundComponentOnce() ?: lastKnownForegroundSnapshot
        Log.i(
            TAG,
            "executeCommand type=${command.type} params=${summarizeCommand(command)} " +
                "foreground=${formatForegroundSnapshot(foregroundSnapshot)}"
        )
    }

    private fun summarizeCommand(command: MidsceneBridgeCommand): String {
        return buildString {
            when (command.type.trim().lowercase()) {
                "tap", "double_tap", "long_press" -> {
                    append("x=")
                    append(command.x)
                    append(",y=")
                    append(command.y)
                    if (command.durationMs != null) {
                        append(",durationMs=")
                        append(command.durationMs)
                    }
                }
                "swipe", "scroll" -> {
                    append("from=(")
                    append(command.fromX)
                    append(",")
                    append(command.fromY)
                    append("),to=(")
                    append(command.toX)
                    append(",")
                    append(command.toY)
                    append("),durationMs=")
                    append(command.durationMs)
                    if (!command.direction.isNullOrBlank()) {
                        append(",direction=")
                        append(command.direction)
                    }
                }
                "input", "input_text" -> {
                    append("mode=")
                    append(command.mode)
                    append(",textLength=")
                    append(command.text?.length ?: 0)
                    append(",x=")
                    append(command.x)
                    append(",y=")
                    append(command.y)
                }
                "launch_app" -> {
                    append("appName=")
                    append(command.appName)
                    append(",packageName=")
                    append(command.packageName)
                }
                "terminate_app" -> {
                    append("uri=")
                    append(command.uri)
                }
                "run_adb_shell" -> {
                    append("command=")
                    append(command.command)
                }
                "wait" -> {
                    append("durationMs=")
                    append(command.durationMs)
                }
                "key", "keyevent" -> {
                    append("keyCode=")
                    append(command.keyCode)
                    append(",keyName=")
                    append(command.keyName)
                }
                else -> {
                    append("raw")
                }
            }
        }
    }

    private fun formatForegroundSnapshot(snapshot: ForegroundSnapshot?): String {
        if (snapshot == null) {
            return "<unknown>"
        }
        return buildString {
            append(snapshot.packageName)
            append("/")
            append(snapshot.className ?: "<none>")
            append("@")
            append(snapshot.source)
            append("#")
            append(snapshot.sampleCount)
        }
    }

    private fun resolvePackageName(packageName: String?, appName: String?): String? {
        val safePackageName = packageName?.trim().orEmpty().ifBlank { null }
        if (safePackageName != null) return safePackageName
        val safeAppName = appName?.trim().orEmpty().ifBlank { return null }
        val normalizedTarget = normalizeAppLabel(safeAppName)
        val packageManager = appContext.packageManager
        val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                queryIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(queryIntent, 0)
        }
        return candidates
            .asSequence()
            .mapNotNull { info ->
                val candidatePackage = info.activityInfo?.packageName?.trim().orEmpty()
                val candidateLabel = info.loadLabel(packageManager).toString().trim()
                if (candidatePackage.isBlank() || candidateLabel.isBlank()) {
                    null
                } else {
                    candidatePackage to normalizeAppLabel(candidateLabel)
                }
            }
            .sortedBy { (_, normalizedLabel) ->
                when {
                    normalizedLabel == normalizedTarget -> 0
                    normalizedLabel.contains(normalizedTarget) -> 1
                    else -> 2
                }
            }
            .firstOrNull { (_, normalizedLabel) ->
                normalizedLabel == normalizedTarget || normalizedLabel.contains(normalizedTarget)
            }
            ?.first
    }

    private fun resolveLaunchComponent(packageName: String): String? {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName) ?: return null
        val component = launchIntent.component ?: return null
        val className = component.className.trim()
        if (className.isBlank()) return null
        return "${component.packageName}/$className"
    }

    private suspend fun waitForForegroundPackage(targetPackage: String): String? {
        repeat(6) { index ->
            val currentPackage = readForegroundPackage()
            if (currentPackage == targetPackage) return currentPackage
            delay(if (index < 2) 250L else 400L)
        }
        return readForegroundPackage()
    }

    private suspend fun readStableForegroundSnapshot(allowFallback: Boolean = true): ForegroundSnapshot? {
        val fallback = if (allowFallback) lastKnownForegroundSnapshot else null
        val samples = mutableListOf<ForegroundSnapshot>()
        repeat(5) { attempt ->
            val snapshot = readForegroundComponentOnce()
            if (snapshot != null) {
                samples += snapshot
                if (samples.size >= 2) {
                    val stableSnapshot = chooseStableForegroundSnapshot(samples)
                    if (stableSnapshot != null && stableSnapshot.sampleCount >= 2) {
                        lastKnownForegroundSnapshot = stableSnapshot
                        return stableSnapshot
                    }
                }
            }
            if (attempt < 4) {
                delay(120L + attempt * 80L)
            }
        }
        val stableSnapshot = chooseStableForegroundSnapshot(samples)
        if (stableSnapshot != null) {
            lastKnownForegroundSnapshot = stableSnapshot
            return stableSnapshot
        }
        return fallback
    }

    private fun readForegroundPackage(): String? {
        return readForegroundComponentOnce()?.packageName ?: lastKnownForegroundSnapshot?.packageName
    }

    private fun readForegroundClassName(): String? {
        return readForegroundComponentOnce()?.className ?: lastKnownForegroundSnapshot?.className
    }

    private fun readForegroundComponentOnce(): ForegroundSnapshot? {
        val componentRegex = Regex("""([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/(\.?[a-zA-Z0-9_.$]+)""")
        val packageRegex = Regex("""\b([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)\b""")
        val commands = listOf(
            "dumpsys activity activities" to listOf("topResumedActivity", "mResumedActivity", "ResumedActivity"),
            "dumpsys window" to listOf("mCurrentFocus", "mFocusedApp"),
            "dumpsys activity top" to listOf("ACTIVITY", "TASK")
        )
        for ((command, markers) in commands) {
            val (_, output) = MidsceneShizukuManager.executeShellCommand(command)
            val candidateLines = buildList {
                output.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .filter { line ->
                        markers.any { marker -> line.contains(marker) } &&
                            !line.contains("null", ignoreCase = true)
                    }
                    .forEach(::add)
                if (isEmpty()) {
                    output.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .filter { line ->
                            !line.contains("null", ignoreCase = true) &&
                                (
                                    line.startsWith("ACTIVITY ") ||
                                        line.contains("ActivityRecord{") ||
                                        line.contains("Window{")
                                    )
                        }
                        .forEach(::add)
                }
            }
            for (candidateLine in candidateLines) {
                val match = componentRegex.find(candidateLine)
                if (match != null) {
                    val packageName = match.groupValues.getOrNull(1)?.trim().orEmpty()
                    val className = normalizeComponentClassName(
                        packageName = packageName,
                        rawClassName = match.groupValues.getOrNull(2)
                    )
                    if (packageName.isNotBlank()) {
                        return ForegroundSnapshot(
                            packageName = packageName,
                            className = className,
                            source = command,
                            sampleCount = 1
                        )
                    }
                }
                val packageMatch = packageRegex.findAll(candidateLine)
                    .map { it.groupValues.getOrNull(1)?.trim().orEmpty() }
                    .firstOrNull { candidatePackage ->
                        candidatePackage.isNotBlank() &&
                            '.' in candidatePackage &&
                            !candidatePackage.startsWith("u0") &&
                            !candidatePackage.startsWith("Task") &&
                            !candidatePackage.startsWith("ActivityRecord")
                    }
                if (!packageMatch.isNullOrBlank()) {
                    return ForegroundSnapshot(
                        packageName = packageMatch,
                        className = null,
                        source = "$command(package-only)",
                        sampleCount = 1
                    )
                }
            }
        }
        return null
    }

    private fun chooseStableForegroundSnapshot(samples: List<ForegroundSnapshot>): ForegroundSnapshot? {
        if (samples.isEmpty()) return null
        return samples
            .groupBy { it.packageName to (it.className ?: "") }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<Pair<String, String>, List<ForegroundSnapshot>>> { it.value.size }
                    .thenByDescending { entry -> if (entry.key.second.isNotBlank()) 1 else 0 }
            )
            .firstOrNull()
            ?.let { (_, matchedSamples) ->
                val latest = matchedSamples.last()
                latest.copy(
                    sampleCount = matchedSamples.size,
                    source = matchedSamples
                        .groupingBy { it.source }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key
                        ?: latest.source
                )
            }
    }

    private fun normalizeComponentClassName(packageName: String, rawClassName: String?): String? {
        val className = rawClassName?.trim().orEmpty()
        if (className.isBlank()) return null
        return if (className.startsWith(".")) {
            "$packageName$className"
        } else {
            className
        }
    }

    private fun normalizeAppLabel(value: String): String {
        return value
            .trim()
            .lowercase()
            .replace(" ", "")
    }

    private fun normalizeKeyName(value: String): String {
        val trimmed = value.trim()
        if (trimmed.all { it.isDigit() }) return trimmed
        val upper = trimmed.uppercase()
        return when (upper) {
            "BACK" -> "4"
            "HOME" -> "3"
            "RECENT", "RECENTS" -> "187"
            "ENTER" -> "66"
            else -> if (upper.startsWith("KEYCODE_")) upper else "KEYCODE_$upper"
        }
    }

    private suspend fun focusInputTarget(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        val x = command.x
        val y = command.y
        if (x == null || y == null) {
            return MidsceneBridgeCommandResult(success = true, message = "input target unchanged")
        }
        repeat(2) { attempt ->
            val foregroundSnapshot = readForegroundComponentOnce() ?: lastKnownForegroundSnapshot
            val (tapX, tapY) = resolveWechatChatInputTapPoint(x, y, foregroundSnapshot)
            val tapResult = dispatchAsyncCommand("input tap $tapX $tapY")
            if (!tapResult.success) {
                return tapResult
            }
            delay(180L)
            if (ImeTextBridge.isEnabled()) {
                runCatching { ImeTextBridge.requestShowKeyboard() }
                delay(220L)
            }
            if (waitForFocusedEditableInput()) {
                return MidsceneBridgeCommandResult(
                    success = true,
                    message = "input target focused"
                )
            }
            Log.d(TAG, "focusInputTarget retry attempt=${attempt + 1} x=$tapX y=$tapY")
        }
        return MidsceneBridgeCommandResult(
            success = false,
            errorCode = MidsceneBridgeErrorCode.SHIZUKU_COMMAND_FAILED,
            message = "输入框未进入可编辑态",
            retryable = true
        )
    }

    private fun resolveWechatChatInputTapPoint(
        x: Int,
        y: Int,
        foregroundSnapshot: ForegroundSnapshot?
    ): Pair<Int, Int> {
        val packageName = foregroundSnapshot?.packageName.orEmpty()
        val className = foregroundSnapshot?.className.orEmpty()
        val isWechatChatPage =
            packageName.contains("com.tencent.mm", ignoreCase = true) &&
                className.contains("ChattingUI", ignoreCase = true)
        if (!isWechatChatPage) {
            return x to y
        }
        val metrics = appContext.resources.displayMetrics
        val screenWidth = max(1, metrics.widthPixels)
        val screenHeight = max(1, metrics.heightPixels)
        val isBottomToolbarArea = y >= (screenHeight * 0.72f).toInt()
        val isRightActionArea = x >= (screenWidth * 0.72f).toInt()
        if (!isBottomToolbarArea || !isRightActionArea) {
            return x to y
        }
        val fallbackX = (screenWidth * 0.36f).toInt().coerceIn(
            (screenWidth * 0.18f).toInt(),
            (screenWidth * 0.62f).toInt()
        )
        Log.d(
            TAG,
            "resolveWechatChatInputTapPoint adjust x=$x->$fallbackX y=$y page=$className"
        )
        return fallbackX to y
    }

    private suspend fun waitForFocusedEditableInput(): Boolean {
        repeat(INPUT_VERIFY_RETRY_COUNT) {
            val snapshot = readFocusedInputSnapshot()
            if (snapshot?.focusedEditable == true) {
                return true
            }
            delay(INPUT_VERIFY_RETRY_INTERVAL_MS)
        }
        return false
    }

    private suspend fun clearFocusedInput(): MidsceneBridgeCommandResult {
        ensureYadbReadyOnDevice()?.let { return it }
        val commands = buildYadbClearCommands()
        var lastOutput = ""
        commands.forEachIndexed { index, shellCommand ->
            val (success, output) = MidsceneShizukuManager.executeShellCommand(shellCommand)
            lastOutput = output
            Log.d(
                TAG,
                "clearFocusedInput attempt=${index + 1}/${commands.size} success=$success output=$output command=$shellCommand"
            )
            if (success) {
                return MidsceneBridgeCommandResult(success = true, message = "input cleared via yadb")
            }
        }
        return dispatchCommand(
            "input keyevent 123; i=0; while [ \$i -lt 40 ]; do input keyevent 67; i=\$((i+1)); done"
        ).let { result ->
            if (result.success) {
                result.copy(message = "input cleared via shell fallback")
            } else {
                result.copy(message = lastOutput.ifBlank { result.message })
            }
        }
    }

    private fun maybeSkipRepeatedReplace(
        mode: String,
        text: String,
        currentForeground: ForegroundSnapshot?
    ): MidsceneBridgeCommandResult? {
        if (mode != "replace" || text.isEmpty()) {
            return null
        }
        val lastState = lastInputGuardState ?: return null
        if (lastState.text != text) {
            return null
        }
        val samePackage = lastState.packageName == currentForeground?.packageName
        val sameClass = lastState.className == currentForeground?.className
        if (!samePackage || !sameClass) {
            return null
        }
        if (System.currentTimeMillis() - lastState.timestampMs > INPUT_GUARD_VALID_MS) {
            return null
        }
        Log.d(
            TAG,
            "skip repeated replace for same text on same page package=${currentForeground?.packageName} class=${currentForeground?.className} source=${lastState.source}"
        )
        return MidsceneBridgeCommandResult(
            success = true,
            message = "input skipped to avoid repeated replace clearing the same text",
            verified = lastState.verified,
            verification = lastState.verification
        )
    }

    private fun recordInputGuardState(
        text: String,
        foregroundSnapshot: ForegroundSnapshot?,
        result: MidsceneBridgeCommandResult,
        source: String
    ) {
        if (!result.success || text.isEmpty()) {
            return
        }
        lastInputGuardState = LastInputGuardState(
            text = text,
            packageName = foregroundSnapshot?.packageName,
            className = foregroundSnapshot?.className,
            verified = result.verified,
            verification = result.verification,
            source = source,
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun clearInputGuardState() {
        lastInputGuardState = null
    }

    private suspend fun verifyFocusedInputContains(expectedText: String): InputVerificationResult {
        repeat(INPUT_VERIFY_RETRY_COUNT) { index ->
            val foregroundSnapshot = readForegroundComponentOnce()
            val focusedInputSnapshot = readFocusedInputSnapshot()
            if (focusedInputSnapshot == null) {
                if (index < INPUT_VERIFY_RETRY_COUNT - 1) {
                    delay(INPUT_VERIFY_RETRY_INTERVAL_MS)
                    return@repeat
                }
                return InputVerificationResult(
                    matched = null,
                    payload = MidsceneBridgeCommandVerification(
                        strategy = "focused_editable_contains_text",
                        expectedText = expectedText,
                        matched = null,
                        after = null
                    ),
                    messageSuffix = "verification=unavailable"
                )
            }
            val actualText = focusedInputSnapshot.text.orEmpty()
            val matched = focusedInputSnapshot.focusedEditable && actualText.contains(expectedText)
            val shouldReturn = matched ||
                index == INPUT_VERIFY_RETRY_COUNT - 1 ||
                actualText.isNotBlank() ||
                !focusedInputSnapshot.focusedEditable
            if (shouldReturn) {
                return InputVerificationResult(
                    matched = matched,
                    payload = MidsceneBridgeCommandVerification(
                        strategy = "focused_editable_contains_text",
                        expectedText = expectedText,
                        matched = matched,
                        after = focusedInputSnapshot.toVerificationSnapshot(
                            packageName = foregroundSnapshot?.packageName,
                            className = foregroundSnapshot?.className
                        )
                    ),
                    messageSuffix = buildString {
                        append("verification=")
                        append(if (matched) "matched" else "mismatch")
                        append(", focusedEditable=")
                        append(focusedInputSnapshot.focusedEditable)
                        append(", focusedTextLength=")
                        append(actualText.length)
                        append(", verifyAttempt=")
                        append(index + 1)
                    }
                )
            }
            delay(INPUT_VERIFY_RETRY_INTERVAL_MS)
        }
        return InputVerificationResult(
            matched = null,
            payload = MidsceneBridgeCommandVerification(
                strategy = "focused_editable_contains_text",
                expectedText = expectedText,
                matched = null,
                after = null
            ),
            messageSuffix = "verification=unavailable"
        )
    }

    private fun readFocusedInputSnapshot(): FocusedInputSnapshot? {
        readFocusedInputSnapshotFromImeBridge()?.let { return it }
        val commands = listOf(
            "uiautomator dump /dev/tty",
            "uiautomator dump /data/local/tmp/midscene_uiautomator.xml >/dev/null 2>&1 && cat /data/local/tmp/midscene_uiautomator.xml"
        )
        for (command in commands) {
            val (success, output) = MidsceneShizukuManager.executeShellCommand(command)
            if (!success || output.isBlank()) continue
            val snapshot = parseFocusedInputSnapshot(output, command)
            if (snapshot != null) {
                return snapshot
            }
        }
        return null
    }

    private fun readFocusedInputSnapshotFromImeBridge(): FocusedInputSnapshot? {
        val snapshot = runCatching { ImeTextBridge.readInputSnapshot() }.getOrNull() ?: return null
        val mergedText = snapshot.mergedText().ifBlank { null }
        return FocusedInputSnapshot(
            focusedEditable = true,
            text = mergedText,
            hint = null,
            source = "ime_bridge"
        )
    }

    private fun parseFocusedInputSnapshot(output: String, source: String): FocusedInputSnapshot? {
        val xmlStart = output.indexOf("<?xml")
        if (xmlStart < 0) return null
        val xml = output.substring(xmlStart)
        val nodeRegex = Regex("""<node\s+([^>]*?)\/>""")
        val attrRegex = Regex("""([a-zA-Z0-9:_-]+)="([^"]*)"""")
        nodeRegex.findAll(xml).forEach { match ->
            val attrs = buildMap<String, String> {
                attrRegex.findAll(match.groupValues[1]).forEach { attrMatch ->
                    put(attrMatch.groupValues[1], decodeXmlAttr(attrMatch.groupValues[2]))
                }
            }
            val isFocused = attrs["focused"].equals("true", ignoreCase = true)
            if (!isFocused) return@forEach
            val className = attrs["class"].orEmpty()
            val text = attrs["text"]?.trim().orEmpty().ifBlank { null }
            val hint = attrs["hint"]?.trim().orEmpty().ifBlank { null }
            val editable = isEditableClass(className) || attrs["focusable"].equals("true", ignoreCase = true)
            return FocusedInputSnapshot(
                focusedEditable = editable,
                text = text,
                hint = hint,
                source = source
            )
        }
        return null
    }

    private fun decodeXmlAttr(value: String): String {
        return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun isEditableClass(className: String): Boolean {
        if (className.isBlank()) return false
        return className.contains("EditText", ignoreCase = true) ||
            className.contains("TextField", ignoreCase = true) ||
            className.contains("TextInput", ignoreCase = true)
    }

    private suspend fun inputTextViaImeBridge(text: String): MidsceneBridgeCommandResult? {
        if (!ImeTextBridge.isEnabled()) {
            Log.d(TAG, "inputTextViaImeBridge skipped: IME bridge not enabled")
            return null
        }

        val committed = runCatching {
            ImeTextBridge.typeText(text)
        }.getOrDefault(false)
        Log.d(TAG, "inputTextViaImeBridge commitText result=$committed")
        if (committed) {
            delay(120L)
            return MidsceneBridgeCommandResult(
                success = true,
                message = "input completed via ime bridge commitText"
            )
        }

        val pasteResult = inputTextViaImePaste(text)
        if (pasteResult != null) {
            return pasteResult
        }

        return MidsceneBridgeCommandResult(
            success = false,
            errorCode = MidsceneBridgeErrorCode.SHIZUKU_COMMAND_FAILED,
            message = "IME bridge 输入失败",
            retryable = true
        )
    }

    private suspend fun inputTextViaYadb(text: String): MidsceneBridgeCommandResult {
        ensureYadbReadyOnDevice()?.let { return it }
        val commands = buildYadbKeyboardCommands(text)
        var lastOutput = ""
        commands.forEachIndexed { index, shellCommand ->
            val (success, output) = MidsceneShizukuManager.executeShellCommand(shellCommand)
            lastOutput = output
            Log.d(
                TAG,
                "inputTextViaYadb attempt=${index + 1}/${commands.size} success=$success output=$output command=$shellCommand"
            )
            if (success) {
                delay(220L)
                val verification = verifyFocusedInputContains(text)
                return MidsceneBridgeCommandResult(
                    success = true,
                    message = buildString {
                        append("input completed via yadb")
                        verification.messageSuffix?.let {
                            append(" | ")
                            append(it)
                        }
                    },
                    verified = verification.matched,
                    verification = verification.payload
                )
            }
        }
        return MidsceneBridgeCommandResult(
            success = false,
            errorCode = MidsceneBridgeErrorCode.SHIZUKU_COMMAND_FAILED,
            message = lastOutput.ifBlank { "yadb 输入失败" },
            retryable = true
        )
    }

    private suspend fun inputTextViaImePaste(text: String): MidsceneBridgeCommandResult? {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        val previousClip = runCatching { clipboard.primaryClip }.getOrNull()
        val previousDescription = runCatching { clipboard.primaryClipDescription }.getOrNull()
        return try {
            clipboard.setPrimaryClip(ClipData.newPlainText("midscene-input", text))
            delay(80L)
            val pasted = runCatching {
                ImeTextBridge.pasteLatestClipboardContent()
            }.getOrDefault(false)
            Log.d(TAG, "inputTextViaImePaste result=$pasted")
            if (!pasted) {
                MidsceneBridgeCommandResult(
                    success = false,
                    errorCode = MidsceneBridgeErrorCode.SHIZUKU_COMMAND_FAILED,
                    message = "IME bridge 粘贴失败",
                    retryable = true
                )
            } else {
                delay(500L)
                MidsceneBridgeCommandResult(
                    success = true,
                    message = "input completed via ime bridge paste"
                )
            }
        } finally {
            runCatching {
                if (previousClip != null && previousDescription != null) {
                    clipboard.setPrimaryClip(previousClip)
                } else {
                    clipboard.clearPrimaryClip()
                }
            }
        }
    }

    private fun inputTextViaShell(text: String): MidsceneBridgeCommandResult {
        val shellCommands = buildInputTextShellCommands(text)
        var lastResult = MidsceneBridgeCommandResult(success = true, message = "input completed")
        for (shellCommand in shellCommands) {
            lastResult = dispatchAsyncCommand(shellCommand)
            if (!lastResult.success) {
                return lastResult
            }
        }
        return lastResult
    }

    private fun buildInputTextShellCommands(text: String): List<String> {
        return text.split("\n").flatMapIndexed { index, segment ->
            buildList {
                if (segment.isNotEmpty()) {
                    add("input text '${escapeInputText(segment)}'")
                }
                if (index < text.lines().lastIndex) {
                    add("input keyevent 66")
                }
            }
        }
    }

    private fun escapeInputText(text: String): String {
        return text
            .replace("'", "'\\''")
            .replace(" ", "%s")
    }

    private fun shouldUseYadbForText(text: String): Boolean {
        val hasNonAscii = text.any { it.code >= 128 }
        val hasFormatSpecifiers = Regex("%[a-zA-Z]").containsMatchIn(text)
        val hasShellSpecialChars = text.any { it == '\\' || it == '`' || it == '$' }
        val hasBothQuotes = text.contains('"') && text.contains('\'')
        return hasNonAscii || hasFormatSpecifiers || hasShellSpecialChars || hasBothQuotes
    }

    private fun ensureYadbReadyOnDevice(): MidsceneBridgeCommandResult? {
        if (yadbPreparedOnDevice) {
            return null
        }
        val existing = MidsceneShizukuManager.executeShellCommand("[ -x '$YADB_DEVICE_PATH' ]")
        if (existing.first) {
            yadbPreparedOnDevice = true
            return null
        }

        val localYadb = runCatching { ensureLocalYadbAsset() }.getOrElse { throwable ->
            return MidsceneBridgeCommandResult(
                success = false,
                errorCode = MidsceneBridgeErrorCode.SHIZUKU_COMMAND_FAILED,
                message = throwable.message ?: "准备 yadb 资源失败",
                retryable = true
            )
        }

        val copyCommand =
            "cp '${escapeShellSingleQuoted(localYadb.absolutePath)}' '$YADB_DEVICE_PATH' && " +
                "chmod 755 '$YADB_DEVICE_PATH'"
        val (success, output) = MidsceneShizukuManager.executeShellCommand(copyCommand)
        if (!success) {
            return MidsceneBridgeCommandResult(
                success = false,
                errorCode = MidsceneBridgeErrorCode.SHIZUKU_COMMAND_FAILED,
                message = output.ifBlank { "部署 yadb 到设备失败" },
                retryable = true
            )
        }

        yadbPreparedOnDevice = true
        return null
    }

    private fun ensureLocalYadbAsset(): File {
        val rootDir = appContext.getExternalFilesDir("midscene")
            ?: File(appContext.cacheDir, "midscene")
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        val targetFile = File(rootDir, "yadb")
        val assetBytes = appContext.assets.open(YADB_ASSET_PATH).use { it.readBytes() }
        if (!targetFile.exists() || targetFile.length() != assetBytes.size.toLong()) {
            FileOutputStream(targetFile).use { output ->
                output.write(assetBytes)
                output.flush()
            }
        }
        targetFile.setReadable(true, false)
        targetFile.setExecutable(true, false)
        return targetFile
    }

    private fun escapeYadbText(text: String): String {
        return text
            .replace("'", "'\\''")
            .replace("\n", "\\n")
    }

    private fun buildYadbKeyboardCommands(text: String): List<String> {
        val escapedText = escapeYadbText(text)
        return listOf(
            "CLASSPATH=$YADB_DEVICE_PATH app_process /data/local/tmp " +
                "com.ysbing.yadb.Main -keyboard '$escapedText'",
            "app_process -Djava.class.path=$YADB_DEVICE_PATH /data/local/tmp " +
                "com.ysbing.yadb.Main -keyboard '$escapedText'"
        )
    }

    private fun buildYadbClearCommands(): List<String> {
        return listOf(
            "CLASSPATH=$YADB_DEVICE_PATH app_process /data/local/tmp " +
                "com.ysbing.yadb.Main -keyboardClear",
            "app_process -Djava.class.path=$YADB_DEVICE_PATH /data/local/tmp " +
                "com.ysbing.yadb.Main -keyboardClear"
        )
    }

    private fun escapeShellSingleQuoted(text: String): String {
        return text.replace("'", "'\\''")
    }

    private fun normalizeInputMode(mode: String?): String {
        return when (mode?.trim()?.lowercase()) {
            "clear" -> "clear"
            "typeonly", "type_only" -> "typeOnly"
            else -> "replace"
        }
    }

    private data class ForegroundSnapshot(
        val packageName: String,
        val className: String?,
        val source: String,
        val sampleCount: Int
    )

    private data class FocusedInputSnapshot(
        val focusedEditable: Boolean,
        val text: String?,
        val hint: String?,
        val source: String
    )

    private data class LastInputGuardState(
        val text: String,
        val packageName: String?,
        val className: String?,
        val verified: Boolean?,
        val verification: MidsceneBridgeCommandVerification?,
        val source: String,
        val timestampMs: Long
    )

    private data class InputVerificationResult(
        val matched: Boolean?,
        val payload: MidsceneBridgeCommandVerification?,
        val messageSuffix: String? = null
    )

    private fun ForegroundSnapshot.toVerificationSnapshot(): MidsceneBridgeVerificationSnapshot {
        return MidsceneBridgeVerificationSnapshot(
            packageName = packageName,
            className = className,
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun FocusedInputSnapshot.toVerificationSnapshot(
        packageName: String?,
        className: String?
    ): MidsceneBridgeVerificationSnapshot {
        return MidsceneBridgeVerificationSnapshot(
            packageName = packageName,
            className = className,
            focusedEditable = focusedEditable,
            editableText = text,
            editableHint = hint,
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun hasForegroundChanged(
        beforeSnapshot: ForegroundSnapshot,
        afterSnapshot: ForegroundSnapshot
    ): Boolean {
        return beforeSnapshot.packageName != afterSnapshot.packageName ||
            beforeSnapshot.className != afterSnapshot.className
    }

    private fun isHomePackage(packageName: String?): Boolean {
        val safePackageName = packageName?.trim().orEmpty()
        if (safePackageName.isBlank()) return false
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = appContext.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val defaultHomePackage = resolved?.activityInfo?.packageName?.trim().orEmpty()
        if (defaultHomePackage.isNotBlank() && safePackageName == defaultHomePackage) {
            return true
        }
        return safePackageName.contains("launcher", ignoreCase = true) ||
            safePackageName.contains("home", ignoreCase = true)
    }

    private fun buildNavigationVerificationMessage(
        baseMessage: String,
        beforeSnapshot: ForegroundSnapshot?,
        afterSnapshot: ForegroundSnapshot?,
        matched: Boolean?,
        expectHomeScreen: Boolean
    ): String {
        val beforeLabel = beforeSnapshot?.let { "${it.packageName}/${it.className ?: "unknown"}" } ?: "unknown"
        val afterLabel = afterSnapshot?.let { "${it.packageName}/${it.className ?: "unknown"}" } ?: "unknown"
        return when (matched) {
            true -> "$baseMessage | before=$beforeLabel | after=$afterLabel"
            false -> if (expectHomeScreen) {
                "导航后仍未回到桌面 | before=$beforeLabel | after=$afterLabel"
            } else {
                "导航后前台未变化 | before=$beforeLabel | after=$afterLabel"
            }
            null -> "$baseMessage | before=$beforeLabel | after=$afterLabel | verification=unknown"
        }
    }
}
