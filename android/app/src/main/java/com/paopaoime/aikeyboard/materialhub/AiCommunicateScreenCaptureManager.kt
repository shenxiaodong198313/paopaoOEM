package com.paopaoime.aikeyboard.materialhub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.paopaoime.aikeyboard.sdk.core.OemAddons
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyErrorCode
import com.paopaoime.aikeyboard.sdk.core.OemAutomationStartResult
import com.paopaoime.aikeyboard.sdk.core.OemAutomationStopResult
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object AiCommunicateScreenCapturePermissionStore {
    private const val PREF_NAME = "ai_communicate_projection"
    private const val KEY_GRANTED_HINT = "granted_hint"
    private const val KEY_RESULT_CODE = "result_code"
    private const val KEY_RESULT_DATA_URI = "result_data_uri"
    private const val TAG = "AiScreenCapture"

    @Volatile
    private var resultCode: Int? = null

    @Volatile
    private var resultData: Intent? = null

    @Volatile
    private var activeProjection: MediaProjection? = null

    @Volatile
    private var projectionCallback: MediaProjection.Callback? = null

    fun updateGrant(context: Context, resultCode: Int, data: Intent?) {
        val appContext = context.applicationContext
        this.resultCode = resultCode
        this.resultData = data?.let(::Intent)
        rebuildProjection(appContext)
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GRANTED_HINT, resultCode == Activity.RESULT_OK)
            .putInt(KEY_RESULT_CODE, resultCode)
            .putString(KEY_RESULT_DATA_URI, data?.toUri(Intent.URI_INTENT_SCHEME))
            .apply()
    }

    fun hasActiveGrant(): Boolean = resultCode == Activity.RESULT_OK && activeProjection != null

    fun hasGrantHint(context: Context): Boolean {
        if (OemImeSdk.isScreenshotProviderRegistered()) return true
        return context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GRANTED_HINT, false)
    }

    fun hasUsableGrant(context: Context): Boolean {
        if (OemImeSdk.isScreenshotProviderRegistered()) return true
        if (hasActiveGrant()) return true
        if (!hasGrantHint(context)) return false
        restorePersistedGrant(context)
        return hasActiveGrant()
    }

    fun restorePersistedGrant(context: Context): Boolean {
        if (hasActiveGrant()) return true
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val storedCode = prefs.getInt(KEY_RESULT_CODE, Activity.RESULT_CANCELED)
        val storedUri = prefs.getString(KEY_RESULT_DATA_URI, null).orEmpty()
        if (storedCode != Activity.RESULT_OK || storedUri.isBlank()) {
            return false
        }
        val restoredIntent = runCatching {
            Intent.parseUri(storedUri, Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: run {
            clearPersistedGrant(context)
            return false
        }
        resultCode = storedCode
        resultData = restoredIntent
        rebuildProjection(context.applicationContext)
        if (!hasActiveGrant()) {
            clearPersistedGrant(context)
            return false
        }
        return true
    }

    fun clear(context: Context) {
        AiCommunicateScreenCaptureManager.releaseSession()
        releaseProjection()
        resultCode = null
        resultData = null
        clearPersistedGrant(context)
        stopForegroundService(context)
    }

    internal fun activeProjection(): MediaProjection? = activeProjection

    fun invalidateActiveGrant(context: Context) {
        AiCommunicateScreenCaptureManager.releaseSession()
        releaseProjection()
        resultCode = null
        resultData = null
        clearPersistedGrant(context)
        stopForegroundService(context)
    }

    private fun rebuildProjection(context: Context) {
        releaseProjection()
        val safeCode = resultCode
        val safeData = resultData
        if (safeCode != Activity.RESULT_OK || safeData == null) {
            Log.w(TAG, "rebuildProjection skipped: invalid result payload")
            return
        }
        val projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                ?: run {
                    Log.w(TAG, "rebuildProjection failed: projection manager unavailable")
                    return
                }
        val projection = runCatching {
            projectionManager.getMediaProjection(safeCode, safeData)
        }.getOrNull()
        if (projection == null) {
            Log.w(TAG, "rebuildProjection failed: getMediaProjection returned null")
            return
        }
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "active projection stopped by system")
                AiCommunicateScreenCaptureManager.releaseSession()
                activeProjection = null
                projectionCallback = null
                stopForegroundService(context)
            }
        }
        projection.registerCallback(callback, Handler(Looper.getMainLooper()))
        activeProjection = projection
        projectionCallback = callback
        Log.d(TAG, "rebuildProjection success")
    }

    private fun releaseProjection() {
        val projection = activeProjection
        val callback = projectionCallback
        activeProjection = null
        projectionCallback = null
        if (projection != null && callback != null) {
            runCatching { projection.unregisterCallback(callback) }
        }
        if (projection != null) {
            runCatching { projection.stop() }
        }
    }

    fun stopForegroundService(context: Context) {
        AiScreenCaptureForegroundService.stop(context)
    }

    private fun clearPersistedGrant(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GRANTED_HINT, false)
            .remove(KEY_RESULT_CODE)
            .remove(KEY_RESULT_DATA_URI)
            .apply()
    }
}

object AiCommunicateScreenCaptureManager {
    private const val TAG = "AiScreenCapture"
    private const val SCREENSHOT_DATA_PREFIX = "data:image/png;base64,"
    private const val AUTOMATION_READY_RECOVERY_TIMEOUT_MS = 1_800L
    private const val AUTOMATION_READY_RECOVERY_POLL_MS = 150L
    private const val AUTOMATION_SERVICE_RESTART_SETTLE_MS = 500L

    private data class CaptureSession(
        val projection: MediaProjection,
        val imageReader: ImageReader,
        val virtualDisplay: VirtualDisplay,
        val width: Int,
        val height: Int,
        val densityDpi: Int,
    )

    @Volatile
    private var captureSession: CaptureSession? = null

    @Volatile
    private var lastCaptureFailureReason: String = ""

    fun hasAutomationCapture(context: Context): Boolean {
        val automation = OemAddons.automation ?: return false
        return automation.getAutomationRuntimeSnapshot(context.applicationContext).ready
    }

    fun shouldPreferAutomationCapture(context: Context): Boolean {
        val automation = OemAddons.automation ?: return false
        val appContext = context.applicationContext
        return automation.getAutomationRuntimeSnapshot(appContext).ready ||
            automation.isAvailabilityMonitorEnabled(appContext) ||
            automation.wasAutomationLastReady(appContext)
    }

    fun hasAnyCaptureCapability(context: Context): Boolean {
        if (OemImeSdk.isScreenshotProviderRegistered()) return true
        return shouldPreferAutomationCapture(context) ||
            AiCommunicateScreenCapturePermissionStore.hasUsableGrant(context.applicationContext)
    }

    fun getLastFailureReason(): String = lastCaptureFailureReason

    fun setLastFailureReason(reason: String) {
        lastCaptureFailureReason = reason.trim()
    }

    suspend fun captureBitmap(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        lastCaptureFailureReason = ""
        val screenshotProvider = OemImeSdk.getScreenshotProvider()
        if (screenshotProvider != null) {
            Log.d(TAG, "captureBitmap via host screenshot provider, waiting host result without timeout")
            val hostBitmap = runCatching { screenshotProvider.captureScreen(appContext) }
                .onFailure { error ->
                    Log.w(TAG, "captureBitmap host screenshot provider threw: ${error.message}", error)
                }
                .getOrNull()
            if (hostBitmap != null) {
                lastCaptureFailureReason = ""
                return@withContext hostBitmap
            }
            lastCaptureFailureReason = "宿主宰图回调未返回有效截图"
            Log.w(TAG, "captureBitmap host screenshot provider returned null, fail fast without fallback")
            throw AiCommunicateReplyService.UserVisibleFailure(
                "宿主宰图回调未返回有效截图，请检查宿主截屏实现后重试",
                OemAiReplyErrorCode.CAPTURE_FAILED,
            )
        }
        val automation = OemAddons.automation
        if (automation != null) {
            val automationReady = awaitAutomationReadyIfNeeded(appContext)
            if (automationReady) {
                captureBitmapViaAutomationWithRecovery(appContext)?.let { bitmap ->
                    return@withContext bitmap
                }
                if (lastCaptureFailureReason.isBlank()) {
                    lastCaptureFailureReason =
                        automation.getLastAutomationScreenshotError().ifBlank { "自动化截图失败" }
                }
                Log.w(
                    TAG,
                    "captureBitmap automation path failed reason=$lastCaptureFailureReason, fallback to projection if available"
                )
            } else if (shouldPreferAutomationCapture(appContext)) {
                lastCaptureFailureReason = automation.getAutomationRuntimeSnapshot(appContext).detailMessage
                Log.w(
                    TAG,
                    "captureBitmap automation expected but not ready after recovery window reason=$lastCaptureFailureReason"
                )
            }
        }
        val mediaProjection = AiCommunicateScreenCapturePermissionStore.activeProjection()
        if (mediaProjection == null) {
            if (lastCaptureFailureReason.isBlank()) {
                lastCaptureFailureReason = if (automation == null) {
                    "当前未集成自动化模块，请先授权手动截图会话"
                } else {
                    "当前没有可用的手动截图会话"
                }
            }
            Log.w(TAG, "captureBitmap aborted: no active projection instance")
            return@withContext null
        }
        try {
            val session = ensureSession(appContext, mediaProjection) ?: return@withContext null
            captureWithSession(session)?.also {
                lastCaptureFailureReason = ""
                return@withContext it
            }
            lastCaptureFailureReason = "手动截图未获取到画面"
            null
        } catch (error: SecurityException) {
            Log.w(TAG, "captureBitmap security exception: ${error.message}", error)
            releaseSession()
            AiCommunicateScreenCapturePermissionStore.invalidateActiveGrant(appContext)
            lastCaptureFailureReason = error.message ?: "截图权限已失效"
            null
        }
    }

    private suspend fun awaitAutomationReadyIfNeeded(context: Context): Boolean {
        val appContext = context.applicationContext
        if (hasAutomationCapture(appContext)) {
            return true
        }
        if (!shouldPreferAutomationCapture(appContext)) {
            return false
        }
        if (waitForAutomationReady(appContext)) {
            return true
        }
        Log.w(TAG, "awaitAutomationReadyIfNeeded timeout waiting automation ready, attempt recovery")
        if (recoverAutomationService(appContext, "service not ready before capture")) {
            if (waitForAutomationReady(appContext)) {
                return true
            }
            if (lastCaptureFailureReason.isBlank()) {
                lastCaptureFailureReason = "自动化服务已自动重启，但仍未恢复就绪"
            }
        }
        return false
    }

    private suspend fun waitForAutomationReady(context: Context): Boolean {
        val maxAttempts =
            (AUTOMATION_READY_RECOVERY_TIMEOUT_MS / AUTOMATION_READY_RECOVERY_POLL_MS)
                .toInt()
                .coerceAtLeast(1)
        repeat(maxAttempts) {
            delay(AUTOMATION_READY_RECOVERY_POLL_MS)
            if (hasAutomationCapture(context)) {
                return true
            }
        }
        return false
    }

    private suspend fun captureBitmapViaAutomationWithRecovery(context: Context): Bitmap? {
        captureBitmapViaAutomation(context)?.let { bitmap ->
            return bitmap
        }
        val automationLastError = OemAddons.automation?.getLastAutomationScreenshotError() ?: ""
        val initialFailure =
            automationLastError
                .ifBlank { lastCaptureFailureReason }
                .ifBlank { "自动化截图失败" }
        Log.w(TAG, "captureBitmapViaAutomationWithRecovery initial failure=$initialFailure")
        if (!recoverAutomationService(context, "capture failure: $initialFailure")) {
            if (lastCaptureFailureReason.isBlank()) {
                lastCaptureFailureReason = initialFailure
            }
            return null
        }
        captureBitmapViaAutomation(context)?.let { bitmap ->
            Log.i(TAG, "captureBitmapViaAutomationWithRecovery recovered after service restart")
            return bitmap
        }
        val automationRetryError = OemAddons.automation?.getLastAutomationScreenshotError() ?: ""
        val retryFailure =
            automationRetryError
                .ifBlank { lastCaptureFailureReason }
                .ifBlank { "自动化截图在服务重启后仍未恢复" }
        lastCaptureFailureReason =
            buildString {
                append("自动化服务已自动重启并重试，但截图仍失败")
                if (retryFailure.isNotBlank()) {
                    append("：")
                    append(retryFailure)
                }
            }
        Log.w(TAG, "captureBitmapViaAutomationWithRecovery retry failed reason=$retryFailure")
        return null
    }

    private suspend fun recoverAutomationService(context: Context, reason: String): Boolean {
        val automation = OemAddons.automation
        if (automation == null) {
            lastCaptureFailureReason = "当前未集成自动化模块，无法自动恢复自动化服务"
            Log.w(TAG, "recoverAutomationService aborted: automation addon not installed")
            return false
        }
        val appContext = context.applicationContext
        Log.w(TAG, "recoverAutomationService start reason=$reason")
        when (val stopResult = automation.requestAutomationStop(appContext)) {
            is OemAutomationStopResult.Failed -> {
                lastCaptureFailureReason =
                    buildString {
                        append("自动化服务重启前停止失败")
                        if (stopResult.reason.isNotBlank()) {
                            append("：")
                            append(stopResult.reason)
                        }
                    }
                Log.w(TAG, "recoverAutomationService stop failed reason=${stopResult.reason}")
                return false
            }
            else -> Unit
        }
        delay(AUTOMATION_SERVICE_RESTART_SETTLE_MS)
        return when (val startResult = automation.requestAutomationStart(appContext)) {
            is OemAutomationStartResult.Started,
            is OemAutomationStartResult.AlreadyRunning -> {
                lastCaptureFailureReason = ""
                Log.i(TAG, "recoverAutomationService started successfully")
                true
            }
            is OemAutomationStartResult.NeedsUserAction -> {
                lastCaptureFailureReason =
                    buildString {
                        append("自动化服务自动恢复失败")
                        if (startResult.reason.isNotBlank()) {
                            append("：")
                            append(startResult.reason)
                        }
                    }
                Log.w(TAG, "recoverAutomationService needs user action reason=${startResult.reason}")
                false
            }
            is OemAutomationStartResult.Failed -> {
                lastCaptureFailureReason =
                    buildString {
                        append("自动化服务自动恢复失败")
                        if (startResult.reason.isNotBlank()) {
                            append("：")
                            append(startResult.reason)
                        }
                    }
                Log.w(TAG, "recoverAutomationService start failed reason=${startResult.reason}")
                false
            }
            is OemAutomationStartResult.Unsupported -> {
                lastCaptureFailureReason = "当前系统不支持自动恢复自动化服务"
                Log.w(TAG, lastCaptureFailureReason)
                false
            }
        }
    }

    private suspend fun captureBitmapViaAutomation(context: Context): Bitmap? {
        val automation = OemAddons.automation ?: return null
        val screenshotDataUrl = automation.captureAutomationScreenshotBase64(context) ?: return null
        val base64Payload = screenshotDataUrl.substringAfter("base64,", "")
            .ifBlank {
                screenshotDataUrl.removePrefix(SCREENSHOT_DATA_PREFIX)
            }
            .trim()
            .ifBlank {
                lastCaptureFailureReason = "自动化截图返回了空图片数据"
                Log.w(TAG, "captureBitmapViaAutomation payload blank")
                return null
            }
        val pngBytes = runCatching {
            Base64.decode(base64Payload, Base64.DEFAULT)
        }.getOrElse {
            lastCaptureFailureReason = "自动化截图解码失败: ${it.message}"
            Log.w(TAG, "captureBitmapViaAutomation decode failed message=${it.message}", it)
            return null
        }
        val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
        if (bitmap == null) {
            val decodeError = automation.getLastAutomationScreenshotError()
            lastCaptureFailureReason = decodeError.ifBlank { "自动化截图位图解码失败" }
            Log.w(
                TAG,
                "captureBitmapViaAutomation bitmap decode returned null error=$decodeError"
            )
            return null
        }
        lastCaptureFailureReason = ""
        return bitmap
    }

    @Synchronized
    fun releaseSession() {
        val session = captureSession ?: return
        captureSession = null
        runCatching { session.virtualDisplay.release() }
        runCatching { session.imageReader.close() }
    }

    @Synchronized
    private fun ensureSession(
        context: Context,
        mediaProjection: MediaProjection,
    ): CaptureSession? {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val densityDpi = metrics.densityDpi.coerceAtLeast(1)
        captureSession?.let { existing ->
            if (
                existing.projection === mediaProjection &&
                existing.width == width &&
                existing.height == height &&
                existing.densityDpi == densityDpi
            ) {
                return existing
            }
        }
        releaseSession()
        Log.d(TAG, "ensureSession create width=$width height=$height densityDpi=$densityDpi")
        val imageReader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        } else {
            @Suppress("DEPRECATION")
            ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        }
        return try {
            val virtualDisplay = mediaProjection.createVirtualDisplay(
                "AiCommunicateCapture",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null,
            ) ?: return null
            CaptureSession(
                projection = mediaProjection,
                imageReader = imageReader,
                virtualDisplay = virtualDisplay,
                width = width,
                height = height,
                densityDpi = densityDpi,
            ).also {
                captureSession = it
            }
        } catch (t: Throwable) {
            runCatching { imageReader.close() }
            throw t
        }
    }

    private suspend fun captureWithSession(session: CaptureSession): Bitmap? {
        Log.d(
            TAG,
            "captureWithSession acquire width=${session.width} height=${session.height} densityDpi=${session.densityDpi}"
        )
        repeat(18) {
            delay(70)
            session.imageReader.acquireLatestImage()?.use { image ->
                val bitmap = image.toBitmap()
                Log.d(
                    TAG,
                    "captureWithSession acquired frame attempt=${it + 1} bitmap=${bitmap?.width ?: 0}x${bitmap?.height ?: 0}"
                )
                return bitmap
            }
        }
        Log.w(TAG, "captureWithSession timeout: no image acquired")
        return null
    }

    private fun Image.toBitmap(): Bitmap? {
        val plane = planes.firstOrNull() ?: return null
        val buffer = plane.buffer ?: return null
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height).also {
            if (it !== bitmap) {
                bitmap.recycle()
            }
        }
    }
}
