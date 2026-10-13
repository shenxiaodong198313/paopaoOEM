package com.paopaoime.aikeyboard.ime.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommand
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommandResult
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeScreenState
import com.paopaoime.aikeyboard.sdk.core.OemAddons
import kotlinx.coroutines.delay
import java.io.File

object FavoriteWechatShareHelper {
    private const val TAG = "FavoriteWechatShare"
    private const val WECHAT_PACKAGE = "com.tencent.mm"
    private const val AUTO_CONFIRM_POLL_INTERVAL_MS = 180L
    private const val AUTO_CONFIRM_TAP_INTERVAL_MS = 260L
    private const val AUTO_CONFIRM_MAX_CACHED_TAP_ATTEMPTS = 2
    private const val AUTO_CONFIRM_POST_TAP_SETTLE_MS = 1200L
    private const val SEND_BUTTON_PREFS = "favorite_auto_send_cache"
    private const val KEY_CONFIRM_SEND_X = "confirm_send_x"
    private const val KEY_CONFIRM_SEND_Y = "confirm_send_y"
    private const val KEY_CONFIRM_SEND_SCREEN_WIDTH = "confirm_send_screen_width"
    private const val KEY_CONFIRM_SEND_SCREEN_HEIGHT = "confirm_send_screen_height"
    @Volatile
    private var pendingAttachmentName: String? = null

    private data class ConfirmTargets(
        val fixedZoneTarget: FallbackTarget?,
        val ocrTarget: FallbackTarget?,
        val textOnlyTarget: FallbackTarget?,
    ) {
        val bestTarget: FallbackTarget?
            get() = fixedZoneTarget ?: ocrTarget ?: textOnlyTarget

        val hasAnyTarget: Boolean
            get() = fixedZoneTarget != null || ocrTarget != null || textOnlyTarget != null
    }

    private data class ConfirmActionButtons(
        val sendLine: OcrLine?,
        val cancelLine: OcrLine?,
    ) {
        val ready: Boolean
            get() {
                val send = sendLine ?: return false
                val cancel = cancelLine ?: return false
                val sameRow = kotlin.math.abs(send.rect.centerY() - cancel.rect.centerY()) <= send.rect.height().coerceAtLeast(cancel.rect.height()) * 2
                val leftRightValid = cancel.rect.centerX() < send.rect.centerX()
                return sameRow && leftRightValid
            }
    }

    private data class ChatPageSignals(
        val titleLine: OcrLine?,
        val attachmentNameLine: OcrLine?,
    ) {
        val ready: Boolean
            get() = titleLine != null && attachmentNameLine != null
    }

    // #region debug-point auto-send-doc-tap-reporter
    private fun reportAutoSendDocTapDebug(hypothesisId: String, location: String, msg: String, data: String) = Unit
    // #endregion
    // #region debug-point C:host-text-tap-reporter
    private fun reportHostTextTapDebug(hypothesisId: String, location: String, msg: String, data: String) = Unit
    // #endregion

    suspend fun tryTapChatTextSendButton(context: Context): Boolean {
        val screenState = OemAddons.automation?.captureBridgeScreenState() ?: return false
        val currentApp = screenState.currentApp?.trim().orEmpty()
        if (currentApp.isBlank() || currentApp.equals(context.packageName, ignoreCase = true)) {
            return false
        }
        // #region debug-point C:text-tap-entry
        reportHostTextTapDebug(
            hypothesisId = "C",
            location = "FavoriteWechatShareHelper:tryTapChatTextSendButton:start",
            msg = "text fallback tap entered",
            data = "app=$currentApp,page=${screenState.currentPage},size=${screenState.width}x${screenState.height}"
        )
        // #endregion
        val snapshot = recognizeScreen(context, screenState.screenshotBase64) ?: return false
        val target = findLocalChatInputSendButton(snapshot) ?: return false
        val tapped = tapPoint(context, target.x, target.y)
        // #region debug-point C:text-tap-result
        reportHostTextTapDebug(
            hypothesisId = "C",
            location = "FavoriteWechatShareHelper:tryTapChatTextSendButton:tap",
            msg = "text fallback tap executed",
            data = "app=$currentApp,page=${screenState.currentPage},target=${formatTarget(target)},tapped=$tapped"
        )
        // #endregion
        Log.d(
            TAG,
            "text send fallback app=$currentApp page=${screenState.currentPage} tap=(${target.x},${target.y}) text=${target.text} score=${target.score} tapped=$tapped"
        )
        return tapped
    }

    suspend fun tryTapChatTextSendButtonAndVerifyDisappeared(context: Context): Boolean {
        val beforeState = OemAddons.automation?.captureBridgeScreenState() ?: return false
        val currentApp = beforeState.currentApp?.trim().orEmpty()
        if (currentApp.isBlank() || currentApp.equals(context.packageName, ignoreCase = true)) {
            return false
        }
        val beforeSnapshot = recognizeScreen(context, beforeState.screenshotBase64) ?: return false
        val target = findLocalChatInputSendButton(beforeSnapshot) ?: return false
        val tapped = tapPoint(context, target.x, target.y)
        // #region debug-point C:text-tap-verify-entry
        reportHostTextTapDebug(
            hypothesisId = "C",
            location = "FavoriteWechatShareHelper:tryTapChatTextSendButtonAndVerifyDisappeared:start",
            msg = "text tap verify entered",
            data = "app=$currentApp,target=${formatTarget(target)},tapped=$tapped"
        )
        // #endregion
        if (!tapped) {
            return false
        }
        delay(320)
        val afterState = OemAddons.automation?.captureBridgeScreenState() ?: return false
        val afterSnapshot = recognizeScreen(context, afterState.screenshotBase64) ?: return false
        val remaining = findLocalChatInputSendButton(afterSnapshot)
        val verified = remaining == null
        // #region debug-point D:text-tap-verify-result
        reportHostTextTapDebug(
            hypothesisId = "D",
            location = "FavoriteWechatShareHelper:tryTapChatTextSendButtonAndVerifyDisappeared:result",
            msg = "text tap verify completed",
            data = "app=$currentApp,target=${formatTarget(target)},remaining=${formatTarget(remaining)},verified=$verified"
        )
        // #endregion
        return verified
    }

    fun markPendingAttachmentName(displayName: String) {
        pendingAttachmentName = displayName.takeIf { it.isNotBlank() }
    }

    fun clearPendingAttachmentName() {
        pendingAttachmentName = null
    }

    suspend fun autoConfirmDirectAttachmentSend(
        context: Context,
        timeoutMs: Long,
        previousInputViewStartCount: Int,
    ): Boolean {
        // #region debug-point auto-send-doc-tap-entry
        reportAutoSendDocTapDebug(
            hypothesisId = "H0",
            location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:start",
            msg = "auto confirm entered",
            data = "timeoutMs=$timeoutMs runtimeReady=${OemAddons.automation?.isBridgeRuntimeReady() == true} previousInputViewStartCount=$previousInputViewStartCount"
        )
        // #endregion
        if (OemAddons.automation?.isBridgeRuntimeReady() != true) return false
        val deadlineAt = System.currentTimeMillis() + timeoutMs
        val inputService = context as? AiInputMethodService
        var sendTapped = false
        var lastTapAt = 0L
        var cachedTapAttempts = 0
        var usedCachedPoint = false
        var postTapConfirmGoneCount = 0
        var cachedTargetDisabled = false
        while (System.currentTimeMillis() < deadlineAt) {
            delay(AUTO_CONFIRM_POLL_INTERVAL_MS)
            val screenState = OemAddons.automation?.captureBridgeScreenState() ?: run {
                reportAutoSendDocTapDebug(
                    hypothesisId = "H0A",
                    location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:capture",
                    msg = "capture screen state returned null",
                    data = "sendTapped=$sendTapped"
                )
                continue
            }
            if (!screenState.currentApp.equals(WECHAT_PACKAGE, ignoreCase = true)) {
                reportAutoSendDocTapDebug(
                    hypothesisId = "H0B",
                    location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:appMismatch",
                    msg = "screen state app is not wechat",
                    data = "currentApp=${screenState.currentApp} page=${screenState.currentPage}"
                )
                continue
            }
            val snapshot = recognizeScreen(context, screenState.screenshotBase64) ?: run {
                reportAutoSendDocTapDebug(
                    hypothesisId = "H0C",
                    location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:ocr",
                    msg = "recognize screen returned null",
                    data = "page=${screenState.currentPage} size=${screenState.width}x${screenState.height}"
                )
                continue
            }
            val confirmTargets = resolveConfirmTargets(snapshot)
            val confirmButtons = resolveConfirmActionButtons(snapshot)
            val sendConfirmSignalsReady = isSendConfirmSheet(snapshot, confirmButtons)
            val chatSignals = resolveWechatChatSignals(snapshot, pendingAttachmentName)
            val chatPageVisible = isWechatChatPageClass(screenState.currentPage)
            val confirmReady = isConfirmSheetReady(
                currentPage = screenState.currentPage,
                snapshot = snapshot,
                targets = confirmTargets,
                sendConfirmSignalsReady = sendConfirmSignalsReady,
            )
            if (sendTapped) {
                val currentStartCount = inputService?.currentInputViewStartCount()
                val currentStartCountForLog = currentStartCount ?: -1
                val inputResumed = currentStartCount?.let { it > previousInputViewStartCount } == true
                reportAutoSendDocTapDebug(
                    hypothesisId = "H3R",
                    location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:postTapState",
                    msg = "evaluating post tap completion state",
                    data = "page=${screenState.currentPage} sendConfirmSignalsReady=$sendConfirmSignalsReady confirmReady=$confirmReady currentStartCount=$currentStartCountForLog previousInputViewStartCount=$previousInputViewStartCount"
                )
                if (!sendConfirmSignalsReady && inputResumed) {
                    reportAutoSendDocTapDebug(
                        hypothesisId = "H3D",
                        location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:inputResume",
                        msg = "input resumed after confirm tap",
                        data = "page=${screenState.currentPage} startCount=$currentStartCount previous=$previousInputViewStartCount"
                    )
                    clearPendingAttachmentName()
                    return true
                }
                if (!sendConfirmSignalsReady && (chatSignals.ready || chatPageVisible)) {
                    reportAutoSendDocTapDebug(
                        hypothesisId = "H3C",
                        location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:chatResume",
                        msg = "returned to wechat chat after confirm tap",
                        data = "page=${screenState.currentPage} sendTapped=$sendTapped title=${chatSignals.titleLine?.text ?: "null"} attachment=${chatSignals.attachmentNameLine?.text ?: "null"}"
                    )
                    clearPendingAttachmentName()
                    return true
                }
                if (!confirmReady) {
                    postTapConfirmGoneCount += 1
                } else {
                    postTapConfirmGoneCount = 0
                }
                if (postTapConfirmGoneCount >= 2) {
                    clearCachedConfirmSendTarget(context)
                    clearPendingAttachmentName()
                    return true
                }
                if (!sendConfirmSignalsReady) {
                    clearCachedConfirmSendTarget(context)
                    cachedTargetDisabled = true
                    reportAutoSendDocTapDebug(
                        hypothesisId = "H3E",
                        location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:postTapSignalsGone",
                        msg = "confirm signals disappeared after tap, keep waiting without retrying stale target",
                        data = "page=${screenState.currentPage} goneCount=$postTapConfirmGoneCount currentStartCount=$currentStartCountForLog"
                    )
                    continue
                }
                if (System.currentTimeMillis() - lastTapAt <= AUTO_CONFIRM_POST_TAP_SETTLE_MS) {
                    reportAutoSendDocTapDebug(
                        hypothesisId = "H3A",
                        location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:postTapWait",
                        msg = "waiting confirm sheet settle after first tap",
                        data = "confirmReady=$confirmReady goneCount=$postTapConfirmGoneCount"
                    )
                    continue
                }
                reportAutoSendDocTapDebug(
                    hypothesisId = "H3B",
                    location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:postTapRetryArm",
                    msg = "confirm sheet still visible after settle window, allow retry",
                    data = "confirmReady=$confirmReady lastTapAt=$lastTapAt"
                )
                sendTapped = false
            }
            val textTarget = if (sendConfirmSignalsReady) {
                confirmTargets.bestTarget ?: buildDefaultConfirmSendTarget(snapshot).takeIf { sendConfirmSignalsReady }
            } else {
                null
            }
            // #region debug-point auto-send-doc-tap-candidates
            if (confirmReady || confirmTargets.hasAnyTarget) {
                reportAutoSendDocTapDebug(
                    hypothesisId = "H1",
                    location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:candidates",
                    msg = "confirm candidates evaluated",
                    data = buildString {
                        append("page=").append(screenState.currentPage)
                        append(" app=").append(screenState.currentApp)
                        append(" size=").append(screenState.width).append("x").append(screenState.height)
                        append(" snapshot=").append(snapshot.width).append("x").append(snapshot.height)
                        append(" confirmReady=").append(confirmReady)
                        append(" fixed=").append(formatTarget(confirmTargets.fixedZoneTarget))
                        append(" ocr=").append(formatTarget(confirmTargets.ocrTarget))
                        append(" textOnly=").append(formatTarget(confirmTargets.textOnlyTarget))
                        append(" text=").append(formatTarget(textTarget))
                        append(" sheet=").append(sendConfirmSignalsReady)
                        append(" sendLine=").append(confirmButtons.sendLine?.text ?: "null")
                        append(" cancelLine=").append(confirmButtons.cancelLine?.text ?: "null")
                        append(" chatTitle=").append(chatSignals.titleLine?.text ?: "null")
                        append(" chatAttachment=").append(chatSignals.attachmentNameLine?.text ?: "null")
                        append(" half=").append(isConfirmHalfScreenPage(screenState.currentPage))
                        append(" sendTapped=").append(sendTapped)
                    }
                )
            }
            // #endregion
            val cachedTarget = if (!cachedTargetDisabled) {
                loadCachedConfirmSendTarget(context, screenState)
            } else {
                null
            }
            if (sendConfirmSignalsReady && textTarget == null && cachedTarget != null) {
                val now = System.currentTimeMillis()
                if (now - lastTapAt >= AUTO_CONFIRM_TAP_INTERVAL_MS) {
                    // #region debug-point auto-send-doc-tap-cached
                    reportAutoSendDocTapDebug(
                        hypothesisId = "H2",
                        location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:cachedTap",
                        msg = "using cached confirm tap target",
                        data = "target=${formatCachedTarget(cachedTarget)} attempt=${cachedTapAttempts + 1}/$AUTO_CONFIRM_MAX_CACHED_TAP_ATTEMPTS"
                    )
                    // #endregion
                    val tapped = tapPoint(context, cachedTarget.x, cachedTarget.y)
                    Log.d(
                        TAG,
                        "auto confirm attachment cachedTap=(${cachedTarget.x},${cachedTarget.y}) tapped=$tapped " +
                            "attempt=${cachedTapAttempts + 1}/$AUTO_CONFIRM_MAX_CACHED_TAP_ATTEMPTS"
                    )
                    lastTapAt = now
                    usedCachedPoint = true
                    if (tapped) {
                        sendTapped = true
                        postTapConfirmGoneCount = 0
                    }
                    cachedTapAttempts += 1
                }
                if (cachedTapAttempts < AUTO_CONFIRM_MAX_CACHED_TAP_ATTEMPTS) {
                    continue
                }
                clearCachedConfirmSendTarget(context)
                cachedTargetDisabled = true
            }
            if (!sendConfirmSignalsReady) {
                continue
            }
            val target = textTarget ?: continue
            // #region debug-point auto-send-doc-tap-tap
            reportAutoSendDocTapDebug(
                hypothesisId = "H3",
                location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:tap",
                msg = "using calibrated confirm tap target",
                data = "target=${formatTarget(target)}"
            )
            // #endregion
            cachedTargetDisabled = false
            saveCachedConfirmSendTarget(
                context = context,
                screenWidth = screenState.width,
                screenHeight = screenState.height,
                x = target.x,
                y = target.y
            )
            val tapped = tapPoint(context, target.x, target.y)
            Log.d(
                TAG,
                "auto confirm attachment calibratedTap=(${target.x},${target.y}) text=${target.text} score=${target.score} tapped=$tapped"
            )
            lastTapAt = System.currentTimeMillis()
            cachedTapAttempts = 0
            if (tapped) {
                sendTapped = true
                postTapConfirmGoneCount = 0
            }
        }
        // #region debug-point auto-send-doc-tap-timeout
        reportAutoSendDocTapDebug(
            hypothesisId = "H4",
            location = "FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend:timeout",
            msg = "auto confirm timed out",
            data = "usedCachedPoint=$usedCachedPoint sendTapped=$sendTapped postTapConfirmGoneCount=$postTapConfirmGoneCount"
        )
        // #endregion
        if (usedCachedPoint) {
            clearCachedConfirmSendTarget(context)
        }
        clearPendingAttachmentName()
        return false
    }

    fun resolveLocalSampleFile(context: Context, fileName: String): File? {
        val candidates = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, "favorite_share_samples/$fileName") },
            File(context.filesDir, "favorite_share_samples/$fileName"),
        )
        return candidates.firstOrNull { it.exists() }
    }

    private suspend fun recognizeScreen(
        context: Context,
        screenshotBase64: String,
    ): OcrSnapshot? {
        val bitmap = decodeBitmap(screenshotBase64) ?: return null
        val lines = PaddleOcrEngine.recognize(context.applicationContext, bitmap)
            .onFailure {
                Log.w(TAG, "paddle ocr failed message=${it.message}", it)
            }
            .getOrNull()
            ?.map { OcrLine(text = it.text, rect = it.rect) }
            .orEmpty()
        if (lines.isEmpty()) return null
        return OcrSnapshot(
            width = bitmap.width.coerceAtLeast(1),
            height = bitmap.height.coerceAtLeast(1),
            bitmap = bitmap,
            lines = lines
        )
    }

    private fun isSendConfirmSheet(snapshot: OcrSnapshot, buttons: ConfirmActionButtons): Boolean {
        val texts = snapshot.lines.map { normalizeForCompare(it.text) }
        val hasConfirmTitle = texts.any { it.contains("发送给") || it.contains("发送到当前聊天") } ||
            (texts.any { it.contains("发送到") } && texts.any { it.contains("当前聊天") })
        return hasConfirmTitle && buttons.ready
    }

    private fun isConfirmHalfScreenPage(currentPage: String?): Boolean {
        return currentPage.orEmpty().contains("HalfScreenTransparentActivity", ignoreCase = true)
    }

    private fun isConfirmSheetReady(
        currentPage: String?,
        snapshot: OcrSnapshot,
        targets: ConfirmTargets,
        sendConfirmSignalsReady: Boolean,
    ): Boolean {
        return sendConfirmSignalsReady
    }

    private fun resolveConfirmTargets(snapshot: OcrSnapshot): ConfirmTargets {
        return ConfirmTargets(
            fixedZoneTarget = findFixedConfirmSendButton(snapshot),
            ocrTarget = findLocalConfirmSendButton(snapshot),
            textOnlyTarget = findTextOnlyConfirmSendButton(snapshot),
        )
    }

    private fun resolveConfirmActionButtons(snapshot: OcrSnapshot): ConfirmActionButtons {
        val sendLine = snapshot.lines
            .filter { line ->
                val normalized = normalizeForCompare(line.text)
                val cy = line.rect.centerY().toFloat()
                val cx = line.rect.centerX().toFloat()
                cy in (snapshot.height * 0.62f)..(snapshot.height * 0.97f) &&
                    cx > snapshot.width * 0.45f &&
                    (normalized == "发送" || normalized.endsWith("发送"))
            }
            .maxByOrNull { it.rect.centerX() }
        val cancelLine = snapshot.lines
            .filter { line ->
                val normalized = normalizeForCompare(line.text)
                val cy = line.rect.centerY().toFloat()
                val cx = line.rect.centerX().toFloat()
                cy in (snapshot.height * 0.62f)..(snapshot.height * 0.97f) &&
                    cx < snapshot.width * 0.55f &&
                    normalized.contains("取消")
            }
            .minByOrNull { it.rect.centerX() }
        return ConfirmActionButtons(sendLine = sendLine, cancelLine = cancelLine)
    }

    private fun resolveWechatChatSignals(
        snapshot: OcrSnapshot,
        expectedAttachmentName: String?,
    ): ChatPageSignals {
        val titleLine = snapshot.lines
            .filter { line ->
                val normalized = normalizeForCompare(line.text)
                val cy = line.rect.centerY().toFloat()
                val cx = line.rect.centerX().toFloat()
                cy in (snapshot.height * 0.03f)..(snapshot.height * 0.14f) &&
                    cx in (snapshot.width * 0.20f)..(snapshot.width * 0.80f) &&
                    normalized.length in 2..20 &&
                    !normalized.contains("发送到当前聊天") &&
                    !normalized.contains("发送给") &&
                    !normalized.contains("预览") &&
                    !normalized.matches(Regex("\\d{1,2}:\\d{2}"))
            }
            .maxByOrNull { it.rect.width() }
        val expectedTokens = buildAttachmentNameTokens(expectedAttachmentName)
        val attachmentNameLine = snapshot.lines.firstOrNull { line ->
            val normalized = normalizeForCompare(line.text)
            val cy = line.rect.centerY().toFloat()
            cy in (snapshot.height * 0.15f)..(snapshot.height * 0.78f) &&
                expectedTokens.any { token ->
                    token.isNotBlank() && normalized.contains(token, ignoreCase = true)
                }
        }
        return ChatPageSignals(titleLine = titleLine, attachmentNameLine = attachmentNameLine)
    }

    private fun buildAttachmentNameTokens(displayName: String?): List<String> {
        val name = displayName?.substringBeforeLast('.', displayName)?.trim().orEmpty()
        if (name.isBlank()) return emptyList()
        val normalized = normalizeForCompare(name)
        val tokens = linkedSetOf<String>()
        if (normalized.length >= 4) {
            tokens += normalized.take(8)
            tokens += normalized.takeLast(minOf(8, normalized.length))
        } else {
            tokens += normalized
        }
        name.split(Regex("[^\\p{L}\\p{N}]+"))
            .map { normalizeForCompare(it) }
            .filter { it.length >= 4 }
            .forEach { tokens += it }
        return tokens.filter { it.isNotBlank() }
    }

    private fun buildDefaultConfirmSendTarget(snapshot: OcrSnapshot): FallbackTarget {
        return FallbackTarget(
            x = (snapshot.width * 0.695f).toInt().coerceIn(0, snapshot.width - 1),
            y = (snapshot.height * 0.86f).toInt().coerceIn(0, snapshot.height - 1),
            text = "defaultHotzone|signals",
            score = 1,
        )
    }

    private fun isWechatChatPageClass(currentPage: String?): Boolean {
        return currentPage.orEmpty().contains("ChattingUI", ignoreCase = true)
    }

    private fun loadCachedConfirmSendTarget(
        context: Context,
        screenState: MidsceneBridgeScreenState,
    ): CachedTapTarget? {
        val prefs = context.getSharedPreferences(SEND_BUTTON_PREFS, Context.MODE_PRIVATE)
        val cachedWidth = prefs.getInt(KEY_CONFIRM_SEND_SCREEN_WIDTH, 0)
        val cachedHeight = prefs.getInt(KEY_CONFIRM_SEND_SCREEN_HEIGHT, 0)
        if (cachedWidth != screenState.width || cachedHeight != screenState.height) {
            return null
        }
        val x = prefs.getInt(KEY_CONFIRM_SEND_X, -1)
        val y = prefs.getInt(KEY_CONFIRM_SEND_Y, -1)
        if (x !in 0 until screenState.width || y !in 0 until screenState.height) {
            return null
        }
        return CachedTapTarget(x = x, y = y)
    }

    private fun saveCachedConfirmSendTarget(
        context: Context,
        screenWidth: Int,
        screenHeight: Int,
        x: Int,
        y: Int,
    ) {
        context.getSharedPreferences(SEND_BUTTON_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CONFIRM_SEND_X, x)
            .putInt(KEY_CONFIRM_SEND_Y, y)
            .putInt(KEY_CONFIRM_SEND_SCREEN_WIDTH, screenWidth)
            .putInt(KEY_CONFIRM_SEND_SCREEN_HEIGHT, screenHeight)
            .apply()
    }

    private fun clearCachedConfirmSendTarget(context: Context) {
        context.getSharedPreferences(SEND_BUTTON_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CONFIRM_SEND_X)
            .remove(KEY_CONFIRM_SEND_Y)
            .remove(KEY_CONFIRM_SEND_SCREEN_WIDTH)
            .remove(KEY_CONFIRM_SEND_SCREEN_HEIGHT)
            .apply()
    }

    private fun findLocalConfirmSendButton(snapshot: OcrSnapshot): FallbackTarget? {
        val candidate = snapshot.lines
            .filter { line ->
                val normalized = normalizeForCompare(line.text)
                val cy = line.rect.centerY().toFloat()
                val cx = line.rect.centerX().toFloat()
                cy in (snapshot.height * 0.62f)..(snapshot.height * 0.96f) &&
                    cx > snapshot.width * 0.45f &&
                    (normalized == "发送" || normalized.endsWith("发送"))
            }
            .mapNotNull { line ->
                val buttonRect = Rect(
                    (line.rect.left - snapshot.width * 0.08f).toInt().coerceAtLeast(0),
                    (line.rect.top - snapshot.height * 0.022f).toInt().coerceAtLeast(0),
                    (line.rect.right + snapshot.width * 0.08f).toInt().coerceAtMost(snapshot.width),
                    (line.rect.bottom + snapshot.height * 0.024f).toInt().coerceAtMost(snapshot.height)
                )
                val greenRatio = estimateGreenButtonRatio(snapshot.bitmap, buttonRect)
                if (greenRatio < 0.16f) {
                    return@mapNotNull null
                }
                var score = 0
                score += (greenRatio * 1000).toInt()
                if (line.rect.centerX() > snapshot.width * 0.62f) score += 160
                if (line.rect.centerY() > snapshot.height * 0.72f) score += 120
                FallbackTarget(
                    x = buttonRect.centerX(),
                    y = buttonRect.centerY(),
                    text = "${line.text}|green=$greenRatio",
                    score = score,
                )
            }
            .maxByOrNull { it.score }
        return candidate?.takeIf { it.score >= 260 }
    }

    private fun findFixedConfirmSendButton(snapshot: OcrSnapshot): FallbackTarget? {
        val width = snapshot.width
        val height = snapshot.height
        val candidates = listOf(
            Rect(
                (width * 0.52f).toInt(),
                (height * 0.80f).toInt(),
                (width * 0.84f).toInt(),
                (height * 0.92f).toInt()
            ),
            Rect(
                (width * 0.56f).toInt(),
                (height * 0.82f).toInt(),
                (width * 0.83f).toInt(),
                (height * 0.90f).toInt()
            ),
            Rect(
                (width * 0.58f).toInt(),
                (height * 0.81f).toInt(),
                (width * 0.86f).toInt(),
                (height * 0.91f).toInt()
            )
        )
        val candidate = candidates.mapNotNull { rect ->
            val greenRatio = estimateGreenButtonRatio(snapshot.bitmap, rect)
            if (greenRatio < 0.18f) {
                return@mapNotNull null
            }
            var score = (greenRatio * 1000).toInt()
            if (rect.centerX() > width * 0.62f) score += 200
            if (rect.centerY() > height * 0.82f) score += 160
            FallbackTarget(
                x = rect.centerX(),
                y = rect.centerY(),
                text = "fixedZone|green=$greenRatio",
                score = score
            )
        }.maxByOrNull { it.score }
        return candidate?.takeIf { it.score >= 360 }
    }

    private fun findTextOnlyConfirmSendButton(snapshot: OcrSnapshot): FallbackTarget? {
        val candidate = snapshot.lines
            .filter { line ->
                val normalized = normalizeForCompare(line.text)
                val cy = line.rect.centerY().toFloat()
                val cx = line.rect.centerX().toFloat()
                cy in (snapshot.height * 0.62f)..(snapshot.height * 0.97f) &&
                    cx > snapshot.width * 0.45f &&
                    (normalized == "发送" || normalized.endsWith("发送"))
            }
            .map { line ->
                val fallbackRect = Rect(
                    (line.rect.left - snapshot.width * 0.12f).toInt().coerceAtLeast(0),
                    (line.rect.top - snapshot.height * 0.035f).toInt().coerceAtLeast(0),
                    (line.rect.right + snapshot.width * 0.12f).toInt().coerceAtMost(snapshot.width),
                    (line.rect.bottom + snapshot.height * 0.04f).toInt().coerceAtMost(snapshot.height)
                )
                var score = 0
                if (line.rect.centerX() > snapshot.width * 0.60f) score += 220
                if (line.rect.centerY() > snapshot.height * 0.78f) score += 180
                if (line.rect.centerX() > snapshot.width * 0.70f) score += 80
                FallbackTarget(
                    x = fallbackRect.centerX(),
                    y = fallbackRect.centerY(),
                    text = "${line.text}|textOnly",
                    score = score,
                )
            }
            .maxByOrNull { it.score }
        return candidate?.takeIf { it.score >= 300 }
    }

    private fun findLocalChatInputSendButton(snapshot: OcrSnapshot): FallbackTarget? {
        val buttonCandidate = snapshot.lines
            .filter { line ->
                isBottomRightSendLabel(snapshot, line)
            }
            .mapNotNull { line ->
                val buttonRect = Rect(
                    (line.rect.left - snapshot.width * 0.04f).toInt().coerceAtLeast(0),
                    (line.rect.top - snapshot.height * 0.02f).toInt().coerceAtLeast(0),
                    (line.rect.right + snapshot.width * 0.04f).toInt().coerceAtMost(snapshot.width),
                    (line.rect.bottom + snapshot.height * 0.02f).toInt().coerceAtMost(snapshot.height)
                )
                val accentRatio = estimateAccentButtonRatio(snapshot.bitmap, buttonRect)
                if (accentRatio < 0.12f) {
                    return@mapNotNull null
                }
                var score = (accentRatio * 1000).toInt()
                if (line.rect.centerX() > snapshot.width * 0.84f) score += 180
                if (line.rect.centerY() > snapshot.height * 0.84f) score += 140
                FallbackTarget(
                    x = buttonRect.centerX(),
                    y = buttonRect.centerY(),
                    text = "${line.text}|accent=$accentRatio",
                    score = score,
                )
            }
            .maxByOrNull { it.score }
            ?.takeIf { it.score >= 240 }
        if (buttonCandidate != null) {
            // #region debug-point C:button-candidate
            reportHostTextTapDebug(
                hypothesisId = "C",
                location = "FavoriteWechatShareHelper:findLocalChatInputSendButton:buttonCandidate",
                msg = "accent button candidate matched",
                data = "snapshot=${snapshot.width}x${snapshot.height},target=${formatTarget(buttonCandidate)}"
            )
            // #endregion
            return buttonCandidate
        }
        val textOnlyCandidate = snapshot.lines
            .filter { line -> isBottomRightSendLabel(snapshot, line) }
            .map { line ->
                val fallbackRect = Rect(
                    (line.rect.left - snapshot.width * 0.10f).toInt().coerceAtLeast(0),
                    (line.rect.top - snapshot.height * 0.035f).toInt().coerceAtLeast(0),
                    (line.rect.right + snapshot.width * 0.12f).toInt().coerceAtMost(snapshot.width),
                    (line.rect.bottom + snapshot.height * 0.04f).toInt().coerceAtMost(snapshot.height)
                )
                var score = 0
                if (line.rect.centerX() > snapshot.width * 0.84f) score += 240
                if (line.rect.centerY() > snapshot.height * 0.84f) score += 180
                if (line.rect.width() < snapshot.width * 0.22f) score += 80
                FallbackTarget(
                    x = fallbackRect.centerX(),
                    y = fallbackRect.centerY(),
                    text = "${line.text}|textOnly",
                    score = score,
                )
            }
            .maxByOrNull { it.score }
        val finalCandidate = textOnlyCandidate?.takeIf { it.score >= 260 }
        // #region debug-point C:text-only-candidate
        reportHostTextTapDebug(
            hypothesisId = "C",
            location = "FavoriteWechatShareHelper:findLocalChatInputSendButton:textOnlyCandidate",
            msg = if (finalCandidate != null) "text-only candidate matched" else "no chat send button candidate matched",
            data = "snapshot=${snapshot.width}x${snapshot.height},candidate=${formatTarget(finalCandidate)}"
        )
        // #endregion
        return finalCandidate
    }

    private fun isBottomRightSendLabel(snapshot: OcrSnapshot, line: OcrLine): Boolean {
        val normalized = normalizeForCompare(line.text).lowercase()
        val cy = line.rect.centerY().toFloat()
        val cx = line.rect.centerX().toFloat()
        val matchesSendText = normalized == "发送" ||
            normalized.endsWith("发送") ||
            normalized == "send" ||
            normalized.endsWith("send")
        return cy in (snapshot.height * 0.76f)..(snapshot.height * 0.985f) &&
            cx > snapshot.width * 0.70f &&
            line.rect.width() < snapshot.width * 0.26f &&
            matchesSendText
    }

    private fun estimateAccentButtonRatio(bitmap: Bitmap, rect: Rect): Float {
        val clipped = Rect(
            rect.left.coerceAtLeast(0),
            rect.top.coerceAtLeast(0),
            rect.right.coerceAtMost(bitmap.width),
            rect.bottom.coerceAtMost(bitmap.height)
        )
        if (clipped.width() <= 0 || clipped.height() <= 0) return 0f
        val stepX = (clipped.width() / 18).coerceAtLeast(1)
        val stepY = (clipped.height() / 12).coerceAtLeast(1)
        var sampled = 0
        var green = 0
        var white = 0
        var y = clipped.top
        while (y < clipped.bottom) {
            var x = clipped.left
            while (x < clipped.right) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                sampled += 1
                val greenAccent = g >= 110 && g >= r + 18 && g >= b + 18
                val blueAccent = b >= 120 && b >= r + 18 && b >= g + 10
                if (greenAccent || blueAccent) {
                    green += 1
                } else if (r >= 220 && g >= 220 && b >= 220) {
                    white += 1
                }
                x += stepX
            }
            y += stepY
        }
        if (sampled == 0) return 0f
        return (green + (white / 3f)) / sampled.toFloat()
    }

    private fun estimateGreenButtonRatio(bitmap: Bitmap, rect: Rect): Float {
        return estimateAccentButtonRatio(bitmap, rect)
    }

    private suspend fun tapPoint(context: Context, x: Int, y: Int): Boolean {
        val result = executeBridgeCommandWithOverlayPassthrough(
            context = context,
            command = MidsceneBridgeCommand(
                type = "tap",
                x = x,
                y = y
            )
        )
        return result.success
    }

    private suspend fun executeBridgeCommandWithOverlayPassthrough(
        context: Context,
        command: MidsceneBridgeCommand,
    ): MidsceneBridgeCommandResult {
        val automation = OemAddons.automation
            ?: return MidsceneBridgeCommandResult(success = false, message = "automation addon unavailable")
        val appContext = context.applicationContext
        val canOverlay = Settings.canDrawOverlays(appContext)
        if (canOverlay) {
            automation.setOverlayCaptureSuppressedAndAwaitNextFrame(appContext, true)
        }
        return try {
            automation.executeBridgeCommand(command)
        } finally {
            if (canOverlay) {
                automation.setOverlayCaptureSuppressedAndAwaitNextFrame(appContext, false)
            }
        }
    }

    private fun decodeBitmap(screenshotBase64: String) = runCatching {
        val body = screenshotBase64.substringAfter("base64,", screenshotBase64).trim()
        val bytes = Base64.decode(body, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun formatTarget(target: FallbackTarget?): String {
        return target?.let { "${it.x},${it.y}|${it.text}|${it.score}" } ?: "null"
    }

    private fun formatCachedTarget(target: CachedTapTarget?): String {
        return target?.let { "${it.x},${it.y}" } ?: "null"
    }

    private fun normalizeForCompare(text: String): String {
        return text.replace("\\s+".toRegex(), "")
            .replace("[：:·•,，。!！?？~～()（）\\[\\]{}<>|/\\\\\"'`]+".toRegex(), "")
            .trim()
    }

    private data class OcrLine(
        val text: String,
        val rect: Rect,
    )

    private data class OcrSnapshot(
        val width: Int,
        val height: Int,
        val bitmap: Bitmap,
        val lines: List<OcrLine>,
    )

    private data class FallbackTarget(
        val x: Int,
        val y: Int,
        val text: String,
        val score: Int,
    )

    private data class CachedTapTarget(
        val x: Int,
        val y: Int,
    )
}
