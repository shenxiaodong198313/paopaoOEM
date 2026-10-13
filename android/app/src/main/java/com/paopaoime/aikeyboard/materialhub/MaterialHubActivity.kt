package com.paopaoime.aikeyboard.materialhub

import android.animation.ObjectAnimator
import android.app.Activity
import android.Manifest
import android.graphics.BlurMaskFilter
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.OpenableColumns
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.UserAvatarHelper
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.activity.ImagePermissionActivity
import com.paopaoime.aikeyboard.ime.ui.activity.ImagePermissionBridge
import com.paopaoime.aikeyboard.ime.ui.fragment.KnowledgeBaseDocumentViewerFragment
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.KnowledgeBaseUiStore
import com.paopaoime.aikeyboard.materialhub.logging.AiReplyTaskLogger
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.sdk.core.OemAddons
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyErrorCode
import com.paopaoime.aikeyboard.sdk.core.OemAutomationOverlayState
import com.paopaoime.aikeyboard.sdk.core.OemDeveloperAiConfigDefaults
import com.paopaoime.aikeyboard.sdk.core.OemSwipeDispatchMode
import com.paopaoime.aikeyboard.sdk.core.OemSwipeRequest
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk
import com.paopaoime.aikeyboard.sdk.core.OemLicenseRefreshApplyMode
import com.paopaoime.aikeyboard.sdk.core.OemLog
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import org.json.JSONObject
import org.fcitx.fcitx5.android.input.materialhub.AiCommunicatePanelController
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubTaxonomyItem
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubTaxonomyMutationResult
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubTaxonomySnapshot
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubTaxonomyTarget
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubLaunchRequest
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubFavoriteController
import org.fcitx.fcitx5.android.input.favorite.FavoriteAttachmentType
import org.fcitx.fcitx5.android.input.favorite.FavoriteBridge
import org.fcitx.fcitx5.android.input.favorite.FavoriteHostShareDispatchResult
import org.fcitx.fcitx5.android.input.favorite.FavoriteHostInputRestorer
import org.fcitx.fcitx5.android.input.favorite.FavoriteHostSendWorker
import org.fcitx.fcitx5.android.input.favorite.FavoritePart
import org.fcitx.fcitx5.android.input.favorite.FavoritePickerType
import org.fcitx.fcitx5.android.input.favorite.FavoriteRecord

class MaterialHubActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MaterialHubActivity"
        private const val DRAWER_OPEN_DURATION_MS = 420L
        private const val DRAWER_CLOSE_DURATION_MS = 320L
        private const val CLOSE_CALLBACK_DELAY_MS = 180L
        private const val DRAWER_HEIGHT_RATIO = 0.8f
        private const val AI_REPLY_AFTER_CLOSE_SEND_DELAY_MS = 380L
        private const val FAST_LLM_PREPARE_COUNTDOWN_START = 3
        private const val FAST_LLM_PREPARE_TICK_MS = 1_000L
        private const val FAST_LLM_PREPARE_DRAWER_RETRACT_DURATION_MS = 260L
        private const val EXTRA_CAPTURE_RETURN_TOKEN = "material_hub_capture_return_token"
        private const val EXTRA_LAUNCH_TOKEN = "material_hub_launch_token"
        private const val EXTRA_ENTRY_MODE = "material_hub_entry_mode"
        private const val EXTRA_START_IN_COMPOSER = "material_hub_start_in_composer"
        private const val EXTRA_QUICK_TEXT_ENTRY = "material_hub_quick_text_entry"
        private const val EXTRA_RESTORE_PICKER_TYPE = "material_hub_restore_picker_type"
        private const val EXTRA_CLIPBOARD_REPLY_TEXT = "material_hub_clipboard_reply_text"
        private const val EXTRA_HOST_PACKAGE_NAME = "material_hub_host_package_name"
        private const val EXTRA_PREVIEW_FILE_PATH = "material_hub_preview_file_path"
        private const val EXTRA_PREVIEW_DISPLAY_NAME = "material_hub_preview_display_name"
        private const val EXTRA_PREVIEW_MIME_TYPE = "material_hub_preview_mime_type"
        private const val EXTRA_PREVIEW_ATTACHMENT_TYPE = "material_hub_preview_attachment_type"
        private const val ENTRY_MODE_MATERIAL_HUB = "material_hub"
        private const val ENTRY_MODE_AI_COMMUNICATE = "ai_communicate"
        private const val EXTRA_CUSTOM_DRAWER_ID = "material_hub_custom_drawer_id"
        private const val AI_FAST_CAPTURE_TARGET_COUNT = 5
        private const val AI_FAST_CAPTURE_MAX_SWIPE_ATTEMPTS = 7
        private const val AI_FAST_CAPTURE_EARLY_STOP_ENOUGH_COUNT = 3
        private const val AI_FAST_CAPTURE_DUPLICATE_DISTANCE_THRESHOLD = 1
        private const val AI_FAST_CAPTURE_VIEWPORT_LEFT_RATIO = 0.04f
        private const val AI_FAST_CAPTURE_VIEWPORT_RIGHT_RATIO = 0.96f
        private const val AI_FAST_CAPTURE_VIEWPORT_TOP_RATIO = 0.08f
        private const val AI_FAST_CAPTURE_VIEWPORT_BOTTOM_RATIO = 0.88f
        private const val AI_FAST_CAPTURE_VERIFY_PROFILE_WIDTH = 24
        private const val AI_FAST_CAPTURE_VERIFY_PROFILE_HEIGHT = 72
        private const val AI_FAST_CAPTURE_VERIFY_MAX_SHIFT_ROWS = 18
        private const val AI_FAST_CAPTURE_VERIFY_MIN_SHIFT_ROWS = 6
        private const val AI_FAST_CAPTURE_VERIFY_MIN_FINGERPRINT_DISTANCE = 6
        private const val AI_FAST_CAPTURE_VERIFY_SCORE_IMPROVEMENT_PERCENT = 92
        private const val AI_FAST_CAPTURE_NO_HISTORY_MIN_SUCCESSFUL_SWIPES = 2
        private const val AI_FAST_CAPTURE_NO_HISTORY_MAX_STABLE_SIGNATURE_LENGTH = 24
        // #region debug-point C:document-preview-back-reporter
        private fun reportDocumentPreviewBackDebug(hypothesisId: String, location: String, msg: String, data: String) {
            // OEM 版本不保留本地调试回传。
        }
        // #endregion
        // #region debug-point D:media-preview-back-reporter
        private fun reportMediaPreviewBackDebug(hypothesisId: String, location: String, msg: String, data: String) {
            // OEM 版本不保留本地调试回传。
        }
        // #endregion

        fun createIntent(
            context: Context,
            request: MaterialHubLaunchRequest,
            launchToken: String,
        ): Intent {
            return Intent(context, MaterialHubActivity::class.java).apply {
                putExtra(EXTRA_LAUNCH_TOKEN, launchToken)
                putExtra(EXTRA_ENTRY_MODE, request.toEntryModeExtra())
                putExtra(EXTRA_START_IN_COMPOSER, request.startInComposer)
                putExtra(EXTRA_QUICK_TEXT_ENTRY, request.quickTextEntry)
                putExtra(EXTRA_RESTORE_PICKER_TYPE, request.restorePickerType)
                putExtra(EXTRA_HOST_PACKAGE_NAME, request.hostPackageName)
                putExtra(EXTRA_CLIPBOARD_REPLY_TEXT, request.clipboardReplyText)
                putExtra(EXTRA_PREVIEW_FILE_PATH, request.previewFilePath)
                putExtra(EXTRA_PREVIEW_DISPLAY_NAME, request.previewDisplayName)
                putExtra(EXTRA_PREVIEW_MIME_TYPE, request.previewMimeType)
                putExtra(EXTRA_PREVIEW_ATTACHMENT_TYPE, request.previewAttachmentType)
            }
        }

        fun createAiCommunicateIntent(context: Context, launchToken: String): Intent {
            return createIntent(
                context = context,
                request = MaterialHubLaunchRequest.aiReply(),
                launchToken = launchToken,
            )
        }

        private fun MaterialHubLaunchRequest.toEntryModeExtra(): String {
            return when (target) {
                MaterialHubLaunchRequest.Target.MATERIAL_HUB -> ENTRY_MODE_MATERIAL_HUB
                MaterialHubLaunchRequest.Target.AI_REPLY -> ENTRY_MODE_AI_COMMUNICATE
            }
        }

        fun createCustomDrawerIntent(context: Context, drawerId: String, launchToken: String): Intent {
            return Intent(context, MaterialHubActivity::class.java).apply {
                putExtra(EXTRA_LAUNCH_TOKEN, launchToken)
                putExtra(EXTRA_CUSTOM_DRAWER_ID, drawerId)
            }
        }

        @Volatile
        var currentInstance: MaterialHubActivity? = null
            private set

        @JvmStatic
        fun currentActivityForFacade(): MaterialHubActivity? = currentInstance
    }

    private lateinit var root: FrameLayout
    private lateinit var statusBarSpacer: View
    private lateinit var drawerHost: FrameLayout
    private lateinit var aiCommunicateNeonOverlay: AiCommunicateNeonBorderView
    private lateinit var aiCommunicateController: AiCommunicatePanelController
    private lateinit var favoriteController: MaterialHubFavoriteController
    private val remoteMaterialService by lazy { OemRemoteMaterialService(this) }

    /**
     * AI 回复生成整体超时。
     *
     * 优先使用宿主通过 SDK 运行时接口设置的值，其次读取 manifest meta-data，
     * 最后回落到默认 30 秒。
     */
    private fun resolveAiGenerationTimeoutMs(): Long {
        return runCatching {
            OemEnterpriseConfig.resolveAiReplyGenerationTimeoutMs(applicationContext)
        }.getOrDefault(OemEnterpriseConfig.DEFAULT_AI_REPLY_TIMEOUT_MS)
    }

    private fun resolveAiReplyErrorCode(throwable: Throwable): String {
        return when (throwable) {
            is TimeoutCancellationException -> OemAiReplyErrorCode.TIMEOUT
            is AiCommunicateReplyService.UserVisibleFailure ->
                throwable.errorCode ?: OemAiReplyErrorCode.UNKNOWN
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            -> OemAiReplyErrorCode.NETWORK
            else -> OemAiReplyErrorCode.UNKNOWN
        }
    }

    /**
     * 统一的 AI 回复失败展示入口。
     *
     * 优先使用宿主按状态码下发的自定义文案（标题必填、描述可选），
     * 缺省时回落到默认文案「这次生成没有成功 / 请点击下方按钮重新生成」。
     */
    private fun showAiReplyError(
        mode: AiCommunicatePanelController.GenerateMode,
        tone: AiCommunicatePanelController.Tone,
        errorCode: String?,
        rawMessage: String?,
    ) {
        val customText = runCatching {
            OemEnterpriseConfig.resolveAiReplyErrorText(applicationContext, errorCode)
        }.getOrNull()
        if (clipboardReplyText != null) updateClipboardReplyState(
            com.paopaoime.aikeyboard.sdk.core.drawer.OemClipboardReplyState(
                sourceText = clipboardReplyText.orEmpty(),
                errorCode = errorCode,
                errorTitle = customText?.title?.takeIf { it.isNotBlank() } ?: AiCommunicatePanelController.DEFAULT_ERROR_TITLE,
                errorDescription = customText?.description?.takeIf { it.isNotBlank() } ?: AiCommunicatePanelController.DEFAULT_ERROR_DESCRIPTION,
            )
        )
        aiCommunicateController.showError(
            mode = mode,
            tone = tone,
            message = rawMessage.orEmpty(),
            errorTitle = customText?.title?.takeIf { it.isNotBlank() }
                ?: AiCommunicatePanelController.DEFAULT_ERROR_TITLE,
            errorDescription = customText?.description?.takeIf { it.isNotBlank() }
                ?: AiCommunicatePanelController.DEFAULT_ERROR_DESCRIPTION,
        )
    }

    /** The clipboard shortcut inserts a draft; it never presses Send or performs an editor action. */
    private fun commitClipboardReply(index: Int, reply: String) {
        if (clipboardReplyCommitPending) return
        val text = normalizeTextForSendLimit(reply) ?: return
        val host = aiCommunicateHostPackageName.orEmpty()
        if (text.isBlank() || host.isBlank()) return
        clipboardReplyCommitPending = true
        val sessionId = aiCommunicateSessionId
        lifecycleScope.launch {
            sessionId?.let { id ->
                FavoriteHostSendWorker.scope.launch {
                    try { recordAiCommunicateReplySelection(id, index, reply) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { OemLog.w(TAG, "Clipboard reply selection record failed", failure) }
                }
            }
            val policy = FavoriteBridge.hostInputRestorePolicy(host)
            if (policy.dispatchTextBeforeOverlayClose && FavoriteBridge.currentInputTargetPackage() == host) {
                val inserted = FavoriteBridge.commitText(text)
                closeMaterialHub()
                if (!inserted) Toast.makeText(applicationContext, "当前暂未写入输入框，请重试", Toast.LENGTH_SHORT).show()
            } else {
                closeMaterialHub {
                    FavoriteHostSendWorker.scope.launch {
                        delay(AI_REPLY_AFTER_CLOSE_SEND_DELAY_MS)
                        val ready = FavoriteHostInputRestorer.restore(host)
                        if (!ready || FavoriteBridge.currentInputTargetPackage() != host || !FavoriteBridge.commitText(text)) {
                            Toast.makeText(applicationContext, "未恢复到原输入框，请重新打开快捷回复", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun clipboardReplyState(): com.paopaoime.aikeyboard.sdk.core.drawer.OemClipboardReplyState? = quickReplyState

    private fun updateClipboardReplyState(state: com.paopaoime.aikeyboard.sdk.core.drawer.OemClipboardReplyState) {
        quickReplyState = state
        customDrawerHost?.notifyClipboardReplyState(state)
    }

    fun regenerateClipboardReply(): Boolean {
        val text = clipboardReplyText ?: return false
        if (!isAiCommunicateMode() || closeRunning || applyCurrentEntryRestrictionIfNeeded()) return false
        aiCommunicateController.showClipboardReplyLoading(aiCommunicateController.selectedTone)
        requestAiCommunicateRepliesForPaste(text, aiCommunicateController.selectedTone)
        return true
    }

    fun insertClipboardReply(text: String): Boolean {
        if (clipboardReplyText == null || !isAiCommunicateMode() || clipboardReplyCommitPending || closeRunning) return false
        val normalized = normalizeTextForSendLimit(text) ?: return false
        if (normalized.isBlank() || aiCommunicateHostPackageName.isNullOrBlank()) return false
        commitClipboardReply(aiCommunicateLatestReplies.indexOf(text), normalized)
        return true
    }

    private fun normalizeTextForSendLimit(text: String): String? {
        val normalized = text.trim()
        if (normalized.isBlank()) return ""
        val maxChars = OemEnterpriseConfig.resolveSendTextMaxChars(applicationContext)
        val codePointCount = normalized.codePointCount(0, normalized.length)
        if (codePointCount <= maxChars) return normalized
        OemLog.w(TAG, "send text exceeds maxChars=$maxChars actualChars=$codePointCount")
        Toast.makeText(applicationContext, "超出可发送字数上限（${maxChars}字）", Toast.LENGTH_SHORT).show()
        return null
    }

    private fun normalizeFavoriteRecordForSend(record: FavoriteRecord): FavoriteRecord? {
        val normalizedParts = mutableListOf<FavoritePart>()
        for (part in record.parts) {
            when (part) {
                is FavoritePart.Text -> {
                    val normalizedText = normalizeTextForSendLimit(part.text) ?: return null
                    if (normalizedText.isNotBlank()) {
                        normalizedParts += part.copy(text = normalizedText)
                    }
                }
                is FavoritePart.Attachment -> normalizedParts += part
            }
        }
        return record.copy(parts = normalizedParts)
    }

    /**
     * 失败态下点击语气 / 重新生成时，丢弃上一轮上下文，
     * 从截屏开始完整重走一遍生成流程。
     */
    private fun restartAiCommunicateFromScratch(
        requestedTone: AiCommunicatePanelController.Tone?,
    ) {
        requestedTone?.let { aiCommunicateController.selectTone(it) }
        if (aiCommunicateSessionMode == AiCommunicatePanelController.GenerateMode.PASTE) {
            aiCommunicateController.beginErrorRetryFeedback()
        }
        handleAiCommunicateEntrySelected(aiCommunicateSessionMode)
    }

    private var currentEntryMode: String = ENTRY_MODE_MATERIAL_HUB
    private var clipboardReplyText: String? = null
    private var clipboardReplyCommitPending = false
    private var quickReplyState: com.paopaoime.aikeyboard.sdk.core.drawer.OemClipboardReplyState? = null

    private var currentCustomDrawerId: String? = null
    private var customDrawerHost: CustomDrawerContentHost? = null
    private var facadeComposerRequested = false
    private var launchHostPackageName: String? = null
    private var facadeProjectionListener: com.paopaoime.aikeyboard.sdk.core.drawer.OemPermissionListener? = null
    private var facadeAlbumListener: com.paopaoime.aikeyboard.sdk.core.drawer.OemAlbumPickerListener? = null
    private var facadeMediaPickerListener: ((Uri?) -> Unit)? = null
    private var facadeDocumentPickerListener: ((Uri?) -> Unit)? = null
    private var facadeSendInProgress = false

    private val facadeProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            facadeProjectionListener?.onResult(result.resultCode == RESULT_OK)
            facadeProjectionListener = null
        }

    private val facadeAlbumLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            val listener = facadeAlbumListener
            facadeAlbumListener = null
            if (uris.isNullOrEmpty()) {
                listener?.onCancelled()
            } else {
                listener?.onImagesSelected(uris)
            }
        }

    private val facadeMediaPickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            facadeMediaPickerListener?.invoke(uri)
            facadeMediaPickerListener = null
        }

    private val facadeDocumentPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            facadeDocumentPickerListener?.invoke(uri)
            facadeDocumentPickerListener = null
        }
    private var aiCommunicateJob: Job? = null
    private var aiCommunicatePrepareJob: Job? = null
    private var entryLicenseRefreshJob: Job? = null
    private var aiCommunicateSessionMode = AiCommunicatePanelController.GenerateMode.FAST_LLM
    private var aiCommunicateSessionId: String? = null
    private var aiCommunicateLatestReplies: List<String> = emptyList()
    private var aiCommunicateLatestGenerationContext: AiCommunicateReplyService.GenerationContext? = null
    private var aiCommunicateHostPackageName: String? = null
    private var projectionPermissionRequestInFlight = false
    private var projectionPermissionSuppressedForSession = false
    private var drawerOpened = false
    private var skipInitialDrawerAnimation = false
    private var closeRunning = false
    private var lastImeVisible: Boolean? = null
    private var inlinePreviewVisible = false
    private var inlinePreviewDocumentId: String? = null
    private var inlinePreviewVideoView: VideoView? = null
    private var pendingInlineDocumentPreview: Triple<File, String, String>? = null
    private var inlinePreviewOnBack: (() -> Unit)? = null
    private var inlinePreviewDownloadTarget: InlinePreviewDownloadTarget? = null
    private var inlinePreviewDownloading = false

    private data class InlinePreviewDownloadTarget(
        val localFile: File?,
        val downloadUrl: String,
        val displayName: String,
        val mimeType: String,
        val attachmentType: FavoriteAttachmentType,
    )
    private var currentUserAvatarBitmap: Bitmap? = null
    private var currentDefaultAvatarBitmap: Bitmap? = null
    private var materialHubWindowFocusable = true
    private var aiCommunicateNeonAnimator: ObjectAnimator? = null
    private var aiCommunicateNeonActive = false
    private var aiCommunicateBackgroundNeonOverlayActive = false
    private var aiCommunicateDrawerRetractedForCapture = false
    private var aiCaptureReturnToken: String? = null
    private var aiCaptureReturnSignal: CompletableDeferred<Unit>? = null
    private val drawerInterpolator by lazy { DecelerateInterpolator() }

    private data class FastLlmCaptureBatch(
        val primaryPreviewPath: String?,
        val orderedPreviewPaths: List<String>,
        val historyState: AiCommunicateReplyService.HistoryState,
        val actualCaptureCount: Int,
        val ocrSourceText: String,
    )

    private data class FastCaptureSwipeProfile(
        val upwardStartOffsetRatio: Float,
        val downwardEndOffsetRatio: Float,
        val durationMs: Int,
        val settleDelayMs: Long,
    )

    private data class FastCaptureScrollableViewport(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = (right - left).coerceAtLeast(1)
        val height: Int get() = (bottom - top).coerceAtLeast(1)
        val centerX: Int get() = left + width / 2
        val centerY: Int get() = top + height / 2
    }

    private data class FastCaptureFrameAnalysis(
        val fingerprint: Long,
        val verticalProfile: IntArray,
    )

    private data class FastCaptureMovementCheck(
        val moved: Boolean,
        val estimatedShiftRows: Int,
        val fingerprintDistance: Int,
        val zeroOffsetScore: Long,
        val bestScore: Long,
    )

    private data class FastCaptureOcrSummary(
        val hasChatContent: Boolean,
        val latestNonBlankText: String,
        val newestSignature: String,
        val oldestSignature: String,
        val signaturesStable: Boolean,
    )
    private val requestScreenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            projectionPermissionRequestInFlight = false
            val granted = result.resultCode == Activity.RESULT_OK && result.data != null
            if (!granted) {
                projectionPermissionSuppressedForSession = true
                Toast.makeText(this, "未获取截屏权限，请授权后再继续", Toast.LENGTH_SHORT).show()
                if (::aiCommunicateController.isInitialized) {
                    aiCommunicateController.showPermission(aiCommunicateSessionMode)
                }
                return@registerForActivityResult
            }
            // 不要直接 updateGrant，而是启动前台服务，让服务去更新并获取 Projection
            AiScreenCaptureForegroundService.start(this, result.resultCode, result.data)
            projectionPermissionSuppressedForSession = false
            if (::aiCommunicateController.isInitialized &&
                aiCommunicateSessionMode != AiCommunicatePanelController.GenerateMode.FAST_LLM
            ) {
                aiCommunicateController.showGenerating(
                    mode = aiCommunicateSessionMode,
                    tone = aiCommunicateController.selectedTone,
                    title = "正在建立截屏会话",
                    detail = "系统授权已完成，正在连接本次截屏会话，马上进入处理流程。",
                    progress = 18,
                    steps = buildAiCommunicateGeneratingSteps(aiCommunicateSessionMode, activeStep = 0),
                )
            }
            lifecycleScope.launch {
                var waited = 0
                while (!AiCommunicateScreenCapturePermissionStore.hasActiveGrant() && waited < 10) {
                    kotlinx.coroutines.delay(100)
                    waited++
                }

                if (AiCommunicateScreenCapturePermissionStore.hasActiveGrant()) {
                    startAiCommunicatePreparation(
                        tone = aiCommunicateController.selectedTone,
                        force = true,
                        mode = aiCommunicateSessionMode,
                        allowActivityCaptureFallback = true,
                    )
                } else {
                    projectionPermissionSuppressedForSession = true
                    OemLog.w(TAG, "projection permission granted but service failed to get active projection")
                    Toast.makeText(this@MaterialHubActivity, "当前设备暂未成功建立截屏会话，请重试授权", Toast.LENGTH_SHORT).show()
                    aiCommunicateController.showPermission(aiCommunicateSessionMode)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this
        overridePendingTransition(0, 0)
        if (!authorizeLaunchIntent(intent)) {
            finish()
            return
        }
        skipInitialDrawerAnimation = intent?.getBooleanExtra(EXTRA_QUICK_TEXT_ENTRY, false) == true
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setMaterialHubWindowFocusable(true)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        root = buildRootView()
        setContentView(root)
        currentEntryMode = intent?.getStringExtra(EXTRA_ENTRY_MODE) ?: ENTRY_MODE_MATERIAL_HUB
        currentCustomDrawerId = intent?.getStringExtra(EXTRA_CUSTOM_DRAWER_ID)
        customDrawerFallbackToBuiltin = false
        if (!ensureCurrentEntryModeEnabled()) {
            return
        }
        currentDefaultAvatarBitmap = defaultAvatarBitmap()
        refreshUserSessionState()
        ensureRootControllerForCurrentMode()
        syncAiCommunicateLaunchGateWithVisibility(visible = true)
        applyStartRequest(intent)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            statusBarSpacer.updateLayoutParams<ViewGroup.LayoutParams> {
                height = statusInsets.top
            }
            handleImeVisibilityChanged(insets.isVisible(WindowInsetsCompat.Type.ime()))
            insets
        }
        onBackPressedDispatcher.addCallback(this) {
            if (inlinePreviewVisible) {
                hideInlineAttachmentPreview()
                return@addCallback
            }
            val customHost = customDrawerHost
            val consumed = if (customHost != null) {
                customHost.handleBackPressed()
            } else if (::favoriteController.isInitialized && !isAiCommunicateMode()) {
                favoriteController.handleBackPressed()
            } else {
                aiCommunicateController.handleBackPressed()
            }
            if (!consumed) {
                closeMaterialHub()
            }
        }
        root.doOnLayout {
            configureDrawerHeight()
            openDrawerOnce()
        }
    }

    private var customDrawerFallbackToBuiltin = false

    private fun ensureRootControllerForCurrentMode() {
        val customHost = resolveCustomDrawerHost()
        customDrawerHost = customHost
        if (customHost == null) {
            if (isAiCommunicateMode()) {
                ensureAiCommunicateController()
            } else {
                ensureFavoriteController()
            }
        }
        drawerHost.removeAllViews()
        drawerHost.addView(
            currentRootControllerView(),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        )
    }

    private fun onCustomHostContentFailed() {
        customDrawerFallbackToBuiltin = true
        customDrawerHost = null
        ensureRootControllerForCurrentMode()
    }

    private fun resolveCustomDrawerHost(): CustomDrawerContentHost? {
        if (customDrawerFallbackToBuiltin) return null
        val customId = currentCustomDrawerId
        if (customId != null) {
            if (!runCatching { OemImeSdk.isCustomDrawersEnabled() }.getOrDefault(false)) {
                warnCustomDrawerFallback(customId, "ENABLE_CUSTOM_DRAWERS")
                return null
            }
            val spec = runCatching { OemImeSdk.findCustomDrawer(customId) }.getOrNull() ?: return null
            return CustomDrawerContentHost(this, customId, spec.title, spec.contentFactory) { onCustomHostContentFailed() }
        }
        if (facadeComposerRequested) {
            facadeComposerRequested = false
            return null
        }
        if (isAiCommunicateMode()) {
            val factory = OemImeSdk.drawerContentFactory(com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerKit.DRAWER_AI_REPLY)
                ?: return null
            if (!runCatching { OemImeSdk.isAiReplyCustomUiEnabled() }.getOrDefault(false)) {
                warnCustomDrawerFallback(
                    com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerKit.DRAWER_AI_REPLY,
                    "ENABLE_AI_REPLY_CUSTOM_UI",
                )
                return null
            }
            return CustomDrawerContentHost(
                this,
                com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerKit.DRAWER_AI_REPLY,
                "AI回复",
                factory,
            ) { onCustomHostContentFailed() }
        }
        val factory = OemImeSdk.drawerContentFactory(com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerKit.DRAWER_MATERIAL_HUB)
            ?: return null
        if (!runCatching { OemImeSdk.isMaterialLibraryCustomUiEnabled() }.getOrDefault(false)) {
            warnCustomDrawerFallback(
                com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerKit.DRAWER_MATERIAL_HUB,
                "ENABLE_MATERIAL_LIBRARY_CUSTOM_UI",
            )
            return null
        }
        return CustomDrawerContentHost(
            this,
            com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerKit.DRAWER_MATERIAL_HUB,
            "快捷发送",
            factory,
        ) { onCustomHostContentFailed() }
    }

    private fun warnCustomDrawerFallback(drawerId: String, missingToggle: String) {
        OemLog.w(
            TAG,
            "custom drawer fallback to builtin: drawerId=$drawerId missingToggle=$missingToggle",
        )
    }

    private fun ensureAiCommunicateController() {
        if (::aiCommunicateController.isInitialized) return
        aiCommunicateController = AiCommunicatePanelController(
            context = this,
            availableTones = resolveAiCommunicateToneOptions(),
            onRequestClose = { afterClosed -> closeMaterialHub(afterClosed) },
            onBackToStart = {
                if (clipboardReplyText != null) closeMaterialHub()
                else resetAiCommunicateFlowToStart()
            },
            onEntrySelected = { mode ->
                handleAiCommunicateEntrySelected(mode)
            },
            onPlaceholderSelected = { placeholder ->
                when (placeholder) {
                    AiCommunicatePanelController.PlaceholderAction.PASTE_SESSION ->
                        Toast.makeText(this, "复制粘贴会话功能即将上线", Toast.LENGTH_SHORT).show()
                    AiCommunicatePanelController.PlaceholderAction.UPLOAD_IMAGE ->
                        Toast.makeText(this, "传图回复功能即将上线", Toast.LENGTH_SHORT).show()
                }
            },
            onRequestPermission = { requestAiCommunicateProjectionPermission() },
            onRequestImagePermission = { onResult ->
                ensureAiCommunicateImageReplyPermission(onResult)
            },
            onImageReplySelected = { uris ->
                requestAiCommunicateRepliesForImages(
                    imageUris = uris,
                    tone = aiCommunicateController.selectedTone,
                )
            },
            onToneSelected = { tone ->
                if (aiCommunicateController.isShowingError) {
                    restartAiCommunicateFromScratch(requestedTone = tone)
                } else {
                    refreshAiCommunicateRepliesFromExistingContext(
                        requestedTone = tone,
                        fallbackTone = aiCommunicateController.selectedTone,
                    )
                }
            },
            onRegenerate = {
                if (aiCommunicateController.isShowingError) {
                    restartAiCommunicateFromScratch(requestedTone = null)
                } else {
                    refreshAiCommunicateRepliesFromExistingContext(
                        requestedTone = aiCommunicateController.selectedTone,
                        fallbackTone = aiCommunicateController.selectedTone,
                    )
                }
            },
            onReplySelected = { index, reply ->
                handleAiCommunicateReplySelected(index, reply)
            },
            footerTextProvider = {
                val capabilities = OemImeSdk.aiReplyCapabilities()
                capabilities?.footerText() ?: AiCommunicatePanelController.DEFAULT_FOOTER_NOTE
            },
            onFooterClick = {
                OemImeSdk.aiReplyCapabilities()?.onFooterClick() == true
            },
        )
    }

    private fun ensureFavoriteController() {
        if (::favoriteController.isInitialized) return
        favoriteController = MaterialHubFavoriteController(
            context = this,
            onKeyboardVisibilityChanged = { visible ->
                if (visible) {
                    setMaterialHubWindowFocusable(true)
                    showRealKeyboard()
                } else {
                    hideRealKeyboard()
                    setMaterialHubWindowFocusable(true)
                }
                0
            },
            onRequestClose = { afterClosed ->
                closeMaterialHub(afterClosed)
            },
            onPreviewAttachment = { attachment, onBackToMaterialGroup ->
                showInlineAttachmentPreview(attachment, onBackToMaterialGroup)
            },
            onEnsurePickerPermission = { type, onResult ->
                ensureFavoritePickerReadPermission(type, onResult)
            },
            isLoggedIn = { true },
            onLoginRequested = { openLoginPage() },
            userAvatarProvider = { currentUserAvatarBitmap },
            defaultAvatarProvider = { currentDefaultAvatarBitmap },
            loadRemoteFavorites = {
                remoteMaterialService.listLatestRemoteFavorites()
            },
            syncRemoteFavorites = {
                remoteMaterialService.syncRemoteFavorites()
            },
            loadNavigationConfig = {
                OemMaterialTaxonomyService(this).loadNavigationConfig()
            },
            loadTaxonomySnapshot = {
                OemMaterialTaxonomyService(this).loadSnapshot().let { snapshot ->
                    MaterialHubTaxonomySnapshot(
                        categories = snapshot.categories.map { item ->
                            MaterialHubTaxonomyItem(
                                id = item.taxonomyId,
                                name = item.name,
                                sort = item.sort,
                                enabled = item.enabled,
                            )
                        },
                        tags = snapshot.tags.map { item ->
                            MaterialHubTaxonomyItem(
                                id = item.taxonomyId,
                                name = item.name,
                                sort = item.sort,
                                enabled = item.enabled,
                            )
                        },
                    )
                }
            },
            createTaxonomyItem = { target, name ->
                val service = OemMaterialTaxonomyService(this)
                val result = when (target) {
                    MaterialHubTaxonomyTarget.CATEGORY -> service.createCategory(name)
                    MaterialHubTaxonomyTarget.TAG -> service.createTag(name)
                }
                MaterialHubTaxonomyMutationResult(
                    success = result.success,
                    item = result.item?.let { item ->
                        MaterialHubTaxonomyItem(
                            id = item.taxonomyId,
                            name = item.name,
                            sort = item.sort,
                            enabled = item.enabled,
                        )
                    },
                    errorMessage = result.errorMessage,
                )
            },
            updateTaxonomyItem = { target, item ->
                val service = OemMaterialTaxonomyService(this)
                val result = when (target) {
                    MaterialHubTaxonomyTarget.CATEGORY -> service.updateCategory(
                        com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyItem(
                            taxonomyId = item.id,
                            name = item.name,
                            sort = item.sort,
                            enabled = item.enabled,
                        ),
                    )
                    MaterialHubTaxonomyTarget.TAG -> service.updateTag(
                        com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyItem(
                            taxonomyId = item.id,
                            name = item.name,
                            sort = item.sort,
                            enabled = item.enabled,
                        ),
                    )
                }
                MaterialHubTaxonomyMutationResult(
                    success = result.success,
                    item = result.item?.let { updated ->
                        MaterialHubTaxonomyItem(
                            id = updated.taxonomyId,
                            name = updated.name,
                            sort = updated.sort,
                            enabled = updated.enabled,
                        )
                    },
                    errorMessage = result.errorMessage,
                )
            },
            deleteTaxonomyItem = { target, taxonomyId ->
                val service = OemMaterialTaxonomyService(this)
                val result = when (target) {
                    MaterialHubTaxonomyTarget.CATEGORY -> service.deleteCategory(taxonomyId)
                    MaterialHubTaxonomyTarget.TAG -> service.deleteTag(taxonomyId)
                }
                MaterialHubTaxonomyMutationResult(
                    success = result.success,
                    item = result.item?.let { updated ->
                        MaterialHubTaxonomyItem(
                            id = updated.taxonomyId,
                            name = updated.name,
                            sort = updated.sort,
                            enabled = updated.enabled,
                        )
                    },
                    errorMessage = result.errorMessage,
                )
            },
        )
    }

    override fun onResume() {
        super.onResume()
        syncAiCommunicateLaunchGateWithVisibility(visible = true)
        if (handleEntryLicenseRefreshOnResume()) {
            return
        }
        continueEntryAfterLicenseDecision()
    }

    private fun handleEntryLicenseRefreshOnResume(): Boolean {
        OemImeSdk.applyPendingLicenseRefreshIfAny()
        val currentStatus = runCatching {
            if (OemImeSdk.isInitialized()) OemImeSdk.getStatus().license.status else null
        }.getOrNull()
        if (currentStatus == null || currentStatus.allowsRuntime) {
            queueDeferredEntryLicenseRefresh()
            return false
        }
        if (entryLicenseRefreshJob?.isActive == true) {
            return true
        }
        entryLicenseRefreshJob = lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                OemImeSdk.refreshLicenseNow(
                    context = applicationContext,
                    reason = currentEntryRefreshReason(prefix = "restricted_recover"),
                    applyMode = OemLicenseRefreshApplyMode.IMMEDIATE,
                )
            }
            if (!isFinishing && !isDestroyed) {
                continueEntryAfterLicenseDecision()
            }
        }
        return true
    }

    private fun continueEntryAfterLicenseDecision() {
        if (applyCurrentEntryRestrictionIfNeeded()) {
            return
        }
        if (OemEnterpriseConfig.isMaterialLibraryEnabled(this)) {
            OemRemoteMaterialAutoSync.warmUp(this, reason = "material_hub_activity_resume")
        }
        refreshUserSessionState()
        if (::favoriteController.isInitialized) {
            favoriteController.refreshSessionState()
        } else if (::aiCommunicateController.isInitialized) {
            aiCommunicateController.refreshSessionState()
        }
    }

    private fun queueDeferredEntryLicenseRefresh() {
        OemImeSdk.refreshLicenseAsync(
            context = applicationContext,
            reason = currentEntryRefreshReason(prefix = "entry_open"),
            applyMode = OemLicenseRefreshApplyMode.DEFER_UNTIL_NEXT_USE,
            minIntervalMs = 2_000L,
        )
    }

    private fun currentEntryRefreshReason(prefix: String): String {
        return if (isAiCommunicateMode()) {
            "${prefix}_ai_reply"
        } else {
            "${prefix}_material_hub"
        }
    }

    override fun onStop() {
        entryLicenseRefreshJob?.cancel()
        syncAiCommunicateLaunchGateWithVisibility(visible = false)
        super.onStop()
    }

    fun onDrawerBaseBack() {
        closeMaterialHub()
    }

    fun requestOpenDrawer(drawerId: String) {
        if (drawerId == currentCustomDrawerId) return
        if (drawerId == com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerKit.DRAWER_AI_REPLY && isAiCommunicateMode() && currentCustomDrawerId == null) return
        if (drawerId == com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerKit.DRAWER_MATERIAL_HUB && !isAiCommunicateMode() && currentCustomDrawerId == null) return
        val token = MaterialHubLaunchAuthorizer.issue()
        val newIntent = when (drawerId) {
            com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerKit.DRAWER_AI_REPLY ->
                createAiCommunicateIntent(this, token)
            com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerKit.DRAWER_MATERIAL_HUB ->
                createIntent(
                    this,
                    MaterialHubLaunchRequest.materialHub(),
                    token,
                )
            else -> createCustomDrawerIntent(this, drawerId, token)
        }
        setIntent(newIntent)
        clipboardReplyText = null
        quickReplyState = null
        currentEntryMode = newIntent.getStringExtra(EXTRA_ENTRY_MODE) ?: ENTRY_MODE_MATERIAL_HUB
        currentCustomDrawerId = newIntent.getStringExtra(EXTRA_CUSTOM_DRAWER_ID)
        customDrawerFallbackToBuiltin = false
        customDrawerHost?.notifyStateChanged(com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerState.CLOSED)
        customDrawerHost = null
        ensureRootControllerForCurrentMode()
    }

    fun requestScreenPermissionForFacade(listener: com.paopaoime.aikeyboard.sdk.core.drawer.OemPermissionListener) {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? android.media.projection.MediaProjectionManager
        if (projectionManager == null) {
            listener.onResult(false)
            return
        }
        facadeProjectionListener = listener
        runCatching { facadeProjectionLauncher.launch(projectionManager.createScreenCaptureIntent()) }
            .onFailure {
                facadeProjectionListener = null
                listener.onResult(false)
            }
    }

    fun launchFacadeAlbumPicker(listener: com.paopaoime.aikeyboard.sdk.core.drawer.OemAlbumPickerListener) {
        facadeAlbumListener = listener
        runCatching { facadeAlbumLauncher.launch("image/*") }
            .onFailure {
                facadeAlbumListener = null
                listener.onCancelled()
            }
    }

    fun launchFacadePicker(
        type: com.paopaoime.aikeyboard.sdk.core.drawer.OemPickerType,
        listener: (Uri?) -> Unit,
    ) {
        when (type) {
            com.paopaoime.aikeyboard.sdk.core.drawer.OemPickerType.TEXT -> listener(null)
            com.paopaoime.aikeyboard.sdk.core.drawer.OemPickerType.IMAGE -> {
                facadeMediaPickerListener = listener
                runCatching { facadeMediaPickerLauncher.launch("image/*") }
                    .onFailure {
                        facadeMediaPickerListener = null
                        listener(null)
                    }
            }
            com.paopaoime.aikeyboard.sdk.core.drawer.OemPickerType.VIDEO -> {
                facadeMediaPickerListener = listener
                runCatching { facadeMediaPickerLauncher.launch("video/*") }
                    .onFailure {
                        facadeMediaPickerListener = null
                        listener(null)
                    }
            }
            com.paopaoime.aikeyboard.sdk.core.drawer.OemPickerType.FILE -> {
                facadeDocumentPickerListener = listener
                runCatching { facadeDocumentPickerLauncher.launch(arrayOf("*/*")) }
                    .onFailure {
                        facadeDocumentPickerListener = null
                        listener(null)
                    }
            }
        }
    }

    fun openComposerForFacade(type: com.paopaoime.aikeyboard.sdk.core.drawer.OemPickerType) {
        OemLog.w(
            TAG,
            "openComposerForFacade is deprecated for custom drawer flow, type=$type. " +
                "Custom hosts should keep all composer UI inside their own content view."
        )
        val message = when (type) {
            com.paopaoime.aikeyboard.sdk.core.drawer.OemPickerType.TEXT ->
                "文本编辑请由宿主在自定义抽屉内完成"
            com.paopaoime.aikeyboard.sdk.core.drawer.OemPickerType.IMAGE ->
                "图片添加请改用宿主自定义素材流"
            com.paopaoime.aikeyboard.sdk.core.drawer.OemPickerType.FILE ->
                "文档添加请改用宿主自定义素材流"
            com.paopaoime.aikeyboard.sdk.core.drawer.OemPickerType.VIDEO ->
                "视频添加请改用宿主自定义素材流"
        }
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    fun sendFavoriteRecordForFacade(
        record: FavoriteRecord,
        onComplete: ((Boolean) -> Unit)? = null,
    ): Boolean {
        val sendRecord = normalizeFavoriteRecordForSend(record) ?: run {
            onComplete?.invoke(false)
            return false
        }
        if (facadeSendInProgress) {
            Toast.makeText(applicationContext, "正在发送素材，请稍候", Toast.LENGTH_SHORT).show()
            onComplete?.invoke(false)
            return false
        }
        if (sendRecord.parts.isEmpty()) {
            Toast.makeText(applicationContext, "当前素材为空", Toast.LENGTH_SHORT).show()
            onComplete?.invoke(false)
            return false
        }
        facadeSendInProgress = true
        closeMaterialHub {
            FavoriteHostSendWorker.scope.launch {
                val success = try {
                    delay(380L)
                    val hostPackageName = launchHostPackageName?.trim().orEmpty()
                    if (hostPackageName.isBlank()) {
                        Toast.makeText(applicationContext, "未识别当前聊天宿主，请重新打开键盘后再试", Toast.LENGTH_SHORT).show()
                        false
                    } else {
                        val inputReady = FavoriteHostInputRestorer.restore(hostPackageName)
                        if (!inputReady) {
                            Toast.makeText(applicationContext, "未恢复到聊天输入框，请重新点击输入框后再试", Toast.LENGTH_SHORT).show()
                            false
                        } else {
                            sendFavoritePartsForFacade(sendRecord.parts, hostPackageName)
                        }
                    }
                } finally {
                    facadeSendInProgress = false
                }
                onComplete?.invoke(success)
            }
        }
        return true
    }

    private suspend fun sendFavoritePartsForFacade(
        parts: List<FavoritePart>,
        hostPackageName: String,
    ): Boolean {
        when (FavoriteBridge.tryHostShare(hostPackageName, parts)) {
            FavoriteHostShareDispatchResult.DISPATCHED -> return true
            FavoriteHostShareDispatchResult.FAILED -> {
                Toast.makeText(applicationContext, "未能打开当前宿主的分享页面，请重试", Toast.LENGTH_SHORT).show()
                return false
            }
            FavoriteHostShareDispatchResult.NOT_HANDLED -> Unit
        }
        parts.forEachIndexed { index, part ->
            when (part) {
                is FavoritePart.Text -> {
                    val committed = FavoriteBridge.commitText(part.text)
                    if (!committed) {
                        Toast.makeText(
                            applicationContext,
                            if (index == 0) "文本发送失败，请重试" else "第 ${index + 1} 条素材发送中断，已停止续发",
                            Toast.LENGTH_SHORT,
                        ).show()
                        return false
                    }
                    delay(if (index == 0) 110L else 160L)
                    val sentOk = FavoriteBridge.performTextSend(part.text)
                    if (!sentOk) {
                        Toast.makeText(
                            applicationContext,
                            if (index == 0) "文本发送失败，请重试" else "第 ${index + 1} 条素材发送中断，已停止续发",
                            Toast.LENGTH_SHORT,
                        ).show()
                        return false
                    }
                }
                is FavoritePart.Attachment -> {
                    val previousInputViewStartCount = FavoriteBridge.currentInputViewStartCount()
                    val directSent = FavoriteBridge.tryDirectAttachmentSend(part)
                    if (!directSent) {
                        Toast.makeText(applicationContext, "第 ${index + 1} 条素材发送失败，请重试", Toast.LENGTH_SHORT).show()
                        return false
                    }
                    val sent = FavoriteBridge.awaitDirectAttachmentSent(
                        previousInputViewStartCount = previousInputViewStartCount,
                        timeoutMs = 15_000L,
                        automationEnabled = false,
                    )
                    if (!sent) {
                        Toast.makeText(applicationContext, "未检测到第 ${index + 1} 条素材发送完成，已停止续发", Toast.LENGTH_SHORT).show()
                        return false
                    }
                }
            }
            if (index != parts.lastIndex) {
                delay(220L)
            }
        }
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!authorizeLaunchIntent(intent)) {
            OemLog.w(TAG, "Ignoring unauthorized material hub relaunch")
            return
        }
        val captureReturnToken = intent.getStringExtra(EXTRA_CAPTURE_RETURN_TOKEN)
        if (captureReturnToken != null && captureReturnToken == aiCaptureReturnToken) {
            // Returning from capture must not replay the entry request or reset the live AI session.
            overridePendingTransition(0, 0)
            aiCaptureReturnSignal?.complete(Unit)
            aiCaptureReturnToken = null
            return
        }
        setIntent(intent)
        currentEntryMode = intent.getStringExtra(EXTRA_ENTRY_MODE) ?: ENTRY_MODE_MATERIAL_HUB
        currentCustomDrawerId = intent.getStringExtra(EXTRA_CUSTOM_DRAWER_ID)
        customDrawerFallbackToBuiltin = false
        if (!ensureCurrentEntryModeEnabled()) {
            return
        }
        closeRunning = false
        ensureRootControllerForCurrentMode()
        syncAiCommunicateLaunchGateWithVisibility(visible = true)
        // #region debug-point C:document-on-new-intent
        reportDocumentPreviewBackDebug(
            hypothesisId = "C",
            location = "MaterialHubActivity:onNewIntent",
            msg = "material hub received new intent for document preview session",
            data = "startInComposer=${intent.getBooleanExtra(EXTRA_START_IN_COMPOSER, false)},quickTextEntry=${intent.getBooleanExtra(EXTRA_QUICK_TEXT_ENTRY, false)},drawerOpened=$drawerOpened,closeRunning=$closeRunning"
        )
        // #endregion
        // #region debug-point D:on-new-intent
        reportMediaPreviewBackDebug(
            hypothesisId = "D",
            location = "MaterialHubActivity:onNewIntent",
            msg = "material hub received new intent",
            data = "startInComposer=${intent.getBooleanExtra(EXTRA_START_IN_COMPOSER, false)},quickTextEntry=${intent.getBooleanExtra(EXTRA_QUICK_TEXT_ENTRY, false)},drawerOpened=$drawerOpened,closeRunning=$closeRunning"
        )
        // #endregion
        applyStartRequest(intent)
    }

    override fun onDestroy() {
        if (currentInstance === this) {
            currentInstance = null
        }
        clearInlinePreview()
        aiCommunicatePrepareJob?.cancel()
        aiCommunicateJob?.cancel()
        hideAiCommunicateNeonOverlay()
        hideAiCommunicateBackgroundNeonOverlay()
        syncAiCommunicateLaunchGateWithVisibility(visible = false)
        if (::favoriteController.isInitialized) {
            favoriteController.destroy()
        }
        if (::aiCommunicateController.isInitialized) {
            aiCommunicateController.destroy()
        }
        super.onDestroy()
    }

    private fun applyStartRequest(intent: Intent?) {
        clipboardReplyText = intent?.getStringExtra(EXTRA_CLIPBOARD_REPLY_TEXT)?.takeIf { it.isNotBlank() }
        clipboardReplyCommitPending = false
        quickReplyState = null
        intent?.getStringExtra(EXTRA_HOST_PACKAGE_NAME)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { aiCommunicateHostPackageName = it }
        if (applyCurrentEntryRestrictionIfNeeded()) {
            cancelRestrictedEntryRuntime()
            return
        }
        if (isAiCommunicateMode()) {
            ensureAiCommunicateController()
            AiCommunicateOcrCacheStore.clearSessionCache()
            resetAiCommunicateDrawerPresentation()
            aiCommunicateController.showStart()
            projectionPermissionRequestInFlight = false
            projectionPermissionSuppressedForSession = false
            aiCommunicatePrepareJob?.cancel()
            aiCommunicateJob?.cancel()
            aiCommunicateSessionId = null
            aiCommunicateLatestReplies = emptyList()
            aiCommunicateLatestGenerationContext = null
            hideAiCommunicateBackgroundNeonOverlay()
            hideAiCommunicateNeonOverlay()
            clipboardReplyText?.let { text ->
                aiCommunicateSessionMode = AiCommunicatePanelController.GenerateMode.PASTE
                aiCommunicateController.showClipboardReplyLoading(aiCommunicateController.selectedTone)
                requestAiCommunicateRepliesForPaste(text, aiCommunicateController.selectedTone)
            }
            return
        }
        if (customDrawerHost == null) {
            ensureFavoriteController()
        }
        OemRemoteMaterialSyncScheduler.ensureScheduled(this)
        val startInComposer = intent?.getBooleanExtra(EXTRA_START_IN_COMPOSER, false) == true
        val quickTextEntry = intent?.getBooleanExtra(EXTRA_QUICK_TEXT_ENTRY, false) == true
        // A quick text request is itself a composer request. Treat it as the source of
        // truth as well so a relaunch can never focus the editor keyboard over the list.
        val shouldStartInComposer = startInComposer || quickTextEntry
        val restorePickerType = intent?.getStringExtra(EXTRA_RESTORE_PICKER_TYPE)
        val hostPackageName = intent?.getStringExtra(EXTRA_HOST_PACKAGE_NAME)
        launchHostPackageName = hostPackageName
        val previewFilePath = intent?.getStringExtra(EXTRA_PREVIEW_FILE_PATH).orEmpty()
        val previewDisplayName = intent?.getStringExtra(EXTRA_PREVIEW_DISPLAY_NAME).orEmpty()
        val previewMimeType = intent?.getStringExtra(EXTRA_PREVIEW_MIME_TYPE).orEmpty()
        val previewAttachmentType = intent?.getStringExtra(EXTRA_PREVIEW_ATTACHMENT_TYPE).orEmpty()
        // #region debug-point C:document-apply-start-request
        reportDocumentPreviewBackDebug(
            hypothesisId = "C",
            location = "MaterialHubActivity:applyStartRequest",
            msg = "apply material hub start request for document preview session",
            data = "startInComposer=$startInComposer,quickTextEntry=$quickTextEntry,restorePickerType=$restorePickerType,previewAttachmentType=$previewAttachmentType,drawerOpened=$drawerOpened,closeRunning=$closeRunning,lastImeVisible=$lastImeVisible"
        )
        // #endregion
        // #region debug-point D:apply-start-request
        reportMediaPreviewBackDebug(
            hypothesisId = "D",
            location = "MaterialHubActivity:applyStartRequest",
            msg = "apply material hub start request",
            data = "startInComposer=$startInComposer,quickTextEntry=$quickTextEntry,restorePickerType=$restorePickerType,previewAttachmentType=$previewAttachmentType,drawerOpened=$drawerOpened,closeRunning=$closeRunning,lastImeVisible=$lastImeVisible"
        )
        // #endregion
        if (previewFilePath.isNotBlank() && drawerOpened) {
            showInlineAttachmentPreview(
                filePath = previewFilePath,
                displayName = previewDisplayName,
                mimeType = previewMimeType,
                attachmentType = previewAttachmentType,
            )
            return
        }
        if (customDrawerHost == null) {
            favoriteController.show(
                startInComposer = shouldStartInComposer,
                quickTextEntry = quickTextEntry,
                restorePickerType = restorePickerType,
                hostPackageName = hostPackageName,
            )
        }
        if (previewFilePath.isNotBlank()) {
            root.post {
                showInlineAttachmentPreview(
                    filePath = previewFilePath,
                    displayName = previewDisplayName,
                    mimeType = previewMimeType,
                    attachmentType = previewAttachmentType,
                )
            }
        }
    }

    private fun syncAiCommunicateLaunchGateWithVisibility(visible: Boolean) {
        if (isAiCommunicateMode()) {
            if (visible) {
                AiCommunicateLaunchGate.markPanelVisible()
            } else {
                AiCommunicateLaunchGate.markPanelHidden()
            }
        } else if (visible) {
            AiCommunicateLaunchGate.release()
        }
    }

    private fun buildRootView(): FrameLayout {
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        val hostLayer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        statusBarSpacer = View(this)
        hostLayer.addView(
            statusBarSpacer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
            )
        )
        hostLayer.addView(
            View(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        )
        container.addView(
            hostLayer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        )
        drawerHost = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        aiCommunicateNeonOverlay = AiCommunicateNeonBorderView(this).apply {
            visibility = View.GONE
            isClickable = false
            isFocusable = false
            setBorderThickness(dp(6).toFloat())
            setCornerRadiusPx(dp(24).toFloat())
        }
        container.addView(
            aiCommunicateNeonOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        )
        container.addView(
            drawerHost,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(460),
                Gravity.BOTTOM,
            )
        )
        aiCommunicateNeonOverlay.bringToFront()
        return container
    }

    private fun openLoginPage() {
        AuthSessionStore.showHostManagedAccountToast(this, "素材库")
    }

    private fun refreshUserSessionState() {
        currentDefaultAvatarBitmap = currentDefaultAvatarBitmap ?: defaultAvatarBitmap()
        currentUserAvatarBitmap = null
        if (!AuthSessionStore.isLoggedIn(this)) {
            return
        }
        val avatarUrl = AuthSessionStore.userAvatarUrl(this)
        val localPath = AuthSessionStore.avatarCachePath(this)
        lifecycleScope.launch {
            currentUserAvatarBitmap = withContext(Dispatchers.IO) {
                UserAvatarHelper.loadAvatarBitmap(this@MaterialHubActivity, avatarUrl, localPath)
            }
        }
    }

    private fun defaultAvatarBitmap(): Bitmap? {
        return BitmapFactory.decodeResource(resources, R.drawable.img_edit_profile_avatar_default_21)
    }

    private fun showInlineAttachmentPreview(
        attachment: FavoritePart.Attachment,
        onBack: (() -> Unit)? = null,
    ) {
        val localFile = attachment.storedPath
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.isFile }
        inlinePreviewVisible = true
        inlinePreviewOnBack = onBack
        clearInlinePreview()
        val previewRoot = when (attachment.attachmentType) {
            org.fcitx.fcitx5.android.input.favorite.FavoriteAttachmentType.DOCUMENT -> {
                if (localFile != null) {
                    buildInlineDocumentPreviewContainer(
                        file = localFile,
                        displayName = attachment.displayName.ifBlank { localFile.name },
                        mimeType = attachment.mimeType,
                    )
                } else {
                    buildInlineRemoteDocumentPreview(
                        displayName = attachment.displayName,
                        mimeType = attachment.mimeType,
                    )
                }
            }
            org.fcitx.fcitx5.android.input.favorite.FavoriteAttachmentType.VIDEO -> when {
                localFile != null -> buildInlineVideoPreview(localFile)
                attachment.downloadUrl.isNotBlank() -> buildInlineRemoteVideoPreview(attachment.downloadUrl)
                else -> return
            }
            else -> when {
                localFile != null -> buildInlineImagePreview(localFile)
                attachment.downloadUrl.isNotBlank() -> buildInlineRemoteImagePreview(attachment.downloadUrl)
                else -> return
            }
        }
        inlinePreviewDownloadTarget = InlinePreviewDownloadTarget(
            localFile = localFile,
            downloadUrl = attachment.downloadUrl,
            displayName = attachment.displayName,
            mimeType = attachment.mimeType,
            attachmentType = attachment.attachmentType,
        )
        favoriteController.showTransientComposerPage(
            title = "",
            contentView = previewRoot,
            onBack = ::hideInlineAttachmentPreview,
            showDownloadAction = true,
            onDownloadAction = ::downloadInlinePreviewAttachment,
            suppressFloatingAdd = true,
        )
        if (attachment.attachmentType ==
            org.fcitx.fcitx5.android.input.favorite.FavoriteAttachmentType.DOCUMENT &&
            localFile != null
        ) {
            attachInlineDocumentPreviewIfNeeded(previewRoot.id)
        }
    }

    private fun showInlineAttachmentPreview(
        filePath: String,
        displayName: String,
        mimeType: String,
        attachmentType: String,
        onBack: (() -> Unit)? = null,
    ) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return
        inlinePreviewVisible = true
        inlinePreviewOnBack = onBack
        clearInlinePreview()
        val previewRoot = when (attachmentType) {
            "DOCUMENT" -> buildInlineDocumentPreviewContainer(file, displayName.ifBlank { file.name }, mimeType)
            "VIDEO" -> buildInlineVideoPreview(file)
            else -> buildInlineImagePreview(file)
        }
        inlinePreviewDownloadTarget = InlinePreviewDownloadTarget(
            localFile = file,
            downloadUrl = "",
            displayName = displayName,
            mimeType = mimeType,
            attachmentType = when (attachmentType) {
                "DOCUMENT" -> FavoriteAttachmentType.DOCUMENT
                "VIDEO" -> FavoriteAttachmentType.VIDEO
                else -> FavoriteAttachmentType.IMAGE
            },
        )
        favoriteController.showTransientComposerPage(
            title = "",
            contentView = previewRoot,
            onBack = ::hideInlineAttachmentPreview,
            showDownloadAction = true,
            onDownloadAction = ::downloadInlinePreviewAttachment,
            suppressFloatingAdd = true,
        )
        if (attachmentType == "DOCUMENT") {
            attachInlineDocumentPreviewIfNeeded(previewRoot.id)
        }
    }

    private fun hideInlineAttachmentPreview() {
        if (!inlinePreviewVisible) return
        val onBack = inlinePreviewOnBack
        inlinePreviewVisible = false
        inlinePreviewOnBack = null
        clearInlinePreview()
        if (onBack != null) {
            onBack()
        } else {
            favoriteController.hideTransientComposerPage()
        }
    }

    private fun clearInlinePreview() {
        inlinePreviewVideoView?.stopPlayback()
        inlinePreviewVideoView = null
        inlinePreviewDownloadTarget = null
        inlinePreviewDownloading = false
        inlinePreviewDocumentId?.let(KnowledgeBaseUiStore::clearTransientDocument)
        inlinePreviewDocumentId = null
        pendingInlineDocumentPreview = null
        supportFragmentManager.findFragmentByTag("materialhub-inline-preview-document")?.let { fragment ->
            supportFragmentManager.beginTransaction()
                .remove(fragment)
                .commitNowAllowingStateLoss()
        }
    }

    private fun downloadInlinePreviewAttachment() {
        val target = inlinePreviewDownloadTarget
        if (target == null) {
            Toast.makeText(this, "暂无可下载的素材", Toast.LENGTH_SHORT).show()
            return
        }
        if (inlinePreviewDownloading) return
        inlinePreviewDownloading = true
        Toast.makeText(this, "开始下载…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { saveInlinePreviewToPublicStorage(target) }
            }
            inlinePreviewDownloading = false
            result.fold(
                onSuccess = { savedPath ->
                    Toast.makeText(this@MaterialHubActivity, "已下载到 $savedPath", Toast.LENGTH_LONG).show()
                },
                onFailure = { error ->
                    OemLog.e(TAG, "material preview download failed", error)
                    Toast.makeText(
                        this@MaterialHubActivity,
                        "下载失败：${error.message.orEmpty()}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            )
        }
    }

    private fun saveInlinePreviewToPublicStorage(target: InlinePreviewDownloadTarget): String {
        val rawName = target.displayName.ifBlank { target.localFile?.name.orEmpty() }
            .ifBlank { "paopao_material_${System.currentTimeMillis()}" }
        val fileName = ensureInlinePreviewFileExtension(rawName, target.attachmentType)
        val (collection, directory, fallbackMime) = when (target.attachmentType) {
            FavoriteAttachmentType.VIDEO -> Triple(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                Environment.DIRECTORY_MOVIES,
                "video/mp4",
            )
            FavoriteAttachmentType.DOCUMENT -> Triple(
                MediaStore.Files.getContentUri("external"),
                Environment.DIRECTORY_DOCUMENTS,
                "application/octet-stream",
            )
            else -> Triple(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                Environment.DIRECTORY_PICTURES,
                "image/jpeg",
            )
        }
        val mimeType = target.mimeType.ifBlank { fallbackMime }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, directory)
            }
            val uri = contentResolver.insert(collection, values)
                ?: throw IOException("创建下载条目失败")
            contentResolver.openOutputStream(uri, "wt").use { stream ->
                val out = stream ?: throw IOException("打开下载流失败")
                copyInlinePreviewSource(target, out)
            }
            return "$directory/$fileName"
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("未授予存储写入权限")
        }
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(directory)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("创建下载目录失败")
        }
        val dest = File(dir, fileName)
        FileOutputStream(dest).use { copyInlinePreviewSource(target, it) }
        MediaScannerConnection.scanFile(this, arrayOf(dest.absolutePath), null, null)
        return "$directory/$fileName"
    }

    private fun copyInlinePreviewSource(target: InlinePreviewDownloadTarget, out: OutputStream) {
        val local = target.localFile
        if (local != null && local.exists()) {
            FileInputStream(local).use { input -> input.copyTo(out) }
            return
        }
        if (target.downloadUrl.isBlank()) throw IOException("无可用下载源")
        URL(target.downloadUrl).openStream().use { input -> input.copyTo(out) }
    }

    private fun ensureInlinePreviewFileExtension(
        name: String,
        attachmentType: FavoriteAttachmentType,
    ): String {
        val trimmed = name.trim()
        val extension = trimmed.substringAfterLast('.', "")
        if (trimmed.contains('.') && extension.length in 1..8) return trimmed
        val fallback = when (attachmentType) {
            FavoriteAttachmentType.VIDEO -> "mp4"
            FavoriteAttachmentType.DOCUMENT -> "bin"
            else -> "jpg"
        }
        return "$trimmed.$fallback"
    }

    private fun buildInlineImagePreview(file: File): View {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(ImageView(context).apply {
                setBackgroundColor(Color.BLACK)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageURI(Uri.fromFile(file))
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    private fun buildInlineRemoteImagePreview(imageUrl: String): View {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            val imageView = ImageView(context).apply {
                setBackgroundColor(Color.BLACK)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(android.R.drawable.ic_menu_gallery)
                imageTintList = ContextCompat.getColorStateList(context, android.R.color.white)
            }
            addView(
                imageView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            )
            lifecycleScope.launch {
                val remoteBitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        java.net.URL(imageUrl).openStream().use(BitmapFactory::decodeStream)
                    }.getOrNull()
                }
                if (remoteBitmap != null) {
                    imageView.setImageBitmap(remoteBitmap)
                    imageView.imageTintList = null
                }
            }
        }
    }

    private fun buildInlineVideoPreview(file: File): View {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            val videoView = VideoView(context).apply {
                val anchorTarget = this
                setVideoURI(Uri.fromFile(file))
                setMediaController(MediaController(this@MaterialHubActivity).apply {
                    setAnchorView(anchorTarget)
                })
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
            }
            inlinePreviewVideoView = videoView
            addView(videoView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    private fun buildInlineRemoteVideoPreview(videoUrl: String): View {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            val videoView = VideoView(context).apply {
                val anchorTarget = this
                setVideoURI(Uri.parse(videoUrl))
                setMediaController(MediaController(this@MaterialHubActivity).apply {
                    setAnchorView(anchorTarget)
                })
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
            }
            inlinePreviewVideoView = videoView
            addView(
                videoView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            )
        }
    }

    private fun buildInlineDocumentPreviewContainer(file: File, displayName: String, mimeType: String): View {
        pendingInlineDocumentPreview = Triple(file, displayName, mimeType)
        return FrameLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.WHITE)
        }
    }

    private fun buildInlineRemoteDocumentPreview(displayName: String, mimeType: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(24))
            addView(
                ImageView(context).apply {
                    setImageResource(android.R.drawable.ic_menu_agenda)
                    imageTintList = ContextCompat.getColorStateList(context, android.R.color.black)
                },
                LinearLayout.LayoutParams(dp(48), dp(48))
            )
            addView(
                android.widget.TextView(context).apply {
                    text = displayName.ifBlank { "远端文档" }
                    setTextColor(Color.parseColor("#111827"))
                    textSize = 16f
                    gravity = Gravity.CENTER
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(16)
                }
            )
            addView(
                android.widget.TextView(context).apply {
                    text = mimeType.ifBlank { "企业素材远端文档" }
                    setTextColor(Color.parseColor("#6B7280"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(8)
                }
            )
        }
    }

    private fun attachInlineDocumentPreviewIfNeeded(containerId: Int) {
        val request = pendingInlineDocumentPreview ?: return
        if (containerId == View.NO_ID) return
        val (file, displayName, mimeType) = request
        val documentId = KnowledgeBaseUiStore.registerTransientDocument(file, displayName, mimeType)
        inlinePreviewDocumentId = documentId
        pendingInlineDocumentPreview = null
        root.post {
            if (!inlinePreviewVisible) return@post
            supportFragmentManager.beginTransaction()
                .replace(
                    containerId,
                    KnowledgeBaseDocumentViewerFragment.newInstance(
                        documentId = documentId,
                        hideHeader = true,
                    ),
                    "materialhub-inline-preview-document"
                )
                .commitNowAllowingStateLoss()
        }
    }

    private fun configureDrawerHeight() {
        val targetHeight = (root.height * DRAWER_HEIGHT_RATIO).roundToInt()
        drawerHost.updateLayoutParams<FrameLayout.LayoutParams> {
            height = targetHeight
            gravity = Gravity.BOTTOM
        }
    }

    private fun showAiCommunicateNeonOverlay() {
        if (!isBuiltinAiCommunicateNeonEnabled()) return
        if (!::aiCommunicateNeonOverlay.isInitialized || aiCommunicateNeonActive) return
        aiCommunicateNeonActive = true
        OemLog.d(TAG, "showAiCommunicateNeonOverlay active=true")
        aiCommunicateNeonOverlay.visibility = View.VISIBLE
        aiCommunicateNeonOverlay.alpha = 1f
        aiCommunicateNeonAnimator?.cancel()
        aiCommunicateNeonAnimator =
            ObjectAnimator.ofFloat(aiCommunicateNeonOverlay, "gradientRotation", 0f, 360f).apply {
                duration = 1_800L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
    }

    private fun pauseAiCommunicateNeonAnimation() {
        if (!isBuiltinAiCommunicateNeonEnabled()) return
        OemLog.d(TAG, "pauseAiCommunicateNeonAnimation")
        aiCommunicateNeonAnimator?.pause()
    }

    private fun resumeAiCommunicateNeonAnimation() {
        if (!isBuiltinAiCommunicateNeonEnabled()) return
        OemLog.d(TAG, "resumeAiCommunicateNeonAnimation")
        aiCommunicateNeonAnimator?.resume()
    }

    private fun hideAiCommunicateNeonOverlay() {
        aiCommunicateNeonActive = false
        OemLog.d(TAG, "hideAiCommunicateNeonOverlay active=false")
        aiCommunicateNeonAnimator?.cancel()
        aiCommunicateNeonAnimator = null
        if (::aiCommunicateNeonOverlay.isInitialized) {
            aiCommunicateNeonOverlay.visibility = View.GONE
        }
    }

    private suspend fun <T> withAiCommunicateNeonSuppressed(block: suspend () -> T): T {
        if (!isBuiltinAiCommunicateNeonEnabled()) return block()
        return withContext(Dispatchers.Main.immediate) {
            val localNeon = aiCommunicateNeonActive && ::aiCommunicateNeonOverlay.isInitialized &&
                aiCommunicateNeonOverlay.visibility == View.VISIBLE
            val previousVisibility = if (localNeon) aiCommunicateNeonOverlay.visibility else View.GONE
            val backgroundOverlay = OemAddons.automation.takeIf { aiCommunicateBackgroundNeonOverlayActive }
            try {
                if (localNeon) {
                    aiCommunicateNeonOverlay.visibility = View.INVISIBLE
                }
                backgroundOverlay?.setOverlayCaptureSuppressedAndAwaitNextFrame(applicationContext, true)
                if (localNeon) {
                    // Wait until the hidden border has reached the compositor before capture.
                    awaitAiCommunicateNeonVisibilityFrame()
                }
                block()
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (localNeon && aiCommunicateNeonActive) {
                        aiCommunicateNeonOverlay.visibility = previousVisibility
                    }
                    backgroundOverlay?.setOverlayCaptureSuppressedAndAwaitNextFrame(applicationContext, false)
                }
            }
        }
    }

    private suspend fun awaitAiCommunicateNeonVisibilityFrame() {
        val decor = window.decorView
        suspendCancellableCoroutine<Unit> { continuation ->
            val complete = Runnable { if (continuation.isActive) continuation.resume(Unit) }
            val nextFrame = Runnable { decor.postOnAnimation(complete) }
            continuation.invokeOnCancellation {
                decor.removeCallbacks(nextFrame)
                decor.removeCallbacks(complete)
            }
            decor.postOnAnimation(nextFrame)
        }
    }

    private fun shouldUseAiCommunicateBackgroundNeonOverlay(): Boolean {
        return isBuiltinAiCommunicateNeonEnabled() && Settings.canDrawOverlays(this)
    }

    private fun showAiCommunicateBackgroundNeonOverlay() {
        if (!shouldUseAiCommunicateBackgroundNeonOverlay() || aiCommunicateBackgroundNeonOverlayActive) return
        val automation = OemAddons.automation ?: return
        aiCommunicateBackgroundNeonOverlayActive = true
        automation.showAutomationOverlay(
            applicationContext,
            OemAutomationOverlayState(
                title = "AI回复正在工作",
                subtitle = "正在读取聊天界面，请勿触碰屏幕",
                progressText = "正在获取聊天截图",
                progressFraction = 0.16f,
                tokenText = "",
                fullscreenMask = false,
            ),
            borderOnly = true,
        )
    }

    private suspend fun showAiCommunicateBackgroundNeonOverlayAndAwaitFirstFrame() {
        if (!shouldUseAiCommunicateBackgroundNeonOverlay() || aiCommunicateBackgroundNeonOverlayActive) return
        val automation = OemAddons.automation ?: return
        aiCommunicateBackgroundNeonOverlayActive = true
        automation.showAutomationOverlayAndAwaitFirstFrame(
            applicationContext,
            OemAutomationOverlayState(
                title = "AI回复正在工作",
                subtitle = "正在读取聊天界面，请勿触碰屏幕",
                progressText = "正在获取聊天截图",
                progressFraction = 0.16f,
                tokenText = "",
                fullscreenMask = false,
            ),
            borderOnly = true,
        )
    }

    private suspend fun activateFastLlmPreparationNeon(taskLogger: AiReplyTaskLogger? = null) {
        if (!isBuiltinAiCommunicateNeonEnabled()) {
            taskLogger?.info(
                phase = "overlay",
                event = "builtin_neon_disabled",
                title = "SDK内置霓虹已关闭",
            )
            OemLog.i(TAG, "builtin ai reply neon overlay disabled, skip sdk overlay chain")
            return
        }
        OemAddons.automation?.setAutomationOverlayNeonEnabled(applicationContext, true)
        showAiCommunicateNeonOverlay()
        taskLogger?.info(
            phase = "overlay",
            event = "host_neon_on",
            title = "宿主霓虹已点亮",
        )
        if (shouldUseAiCommunicateBackgroundNeonOverlay()) {
            taskLogger?.info(
                phase = "overlay",
                event = "background_overlay_requested",
                title = "请求后台霓虹边框",
            )
            showAiCommunicateBackgroundNeonOverlayAndAwaitFirstFrame()
            if (aiCommunicateBackgroundNeonOverlayActive) {
                taskLogger?.info(
                    phase = "overlay",
                    event = "background_overlay_ack",
                    title = "后台霓虹边框已接管",
                )
                hideAiCommunicateNeonOverlay()
            }
        }
    }

    private fun hideAiCommunicateBackgroundNeonOverlay() {
        aiCommunicateBackgroundNeonOverlayActive = false
        if (!isBuiltinAiCommunicateNeonEnabled() || !Settings.canDrawOverlays(this)) return
        OemAddons.automation?.hideAutomationOverlay(applicationContext)
    }

    private fun isBuiltinAiCommunicateNeonEnabled(): Boolean {
        return OemEnterpriseConfig.resolveBuiltinNeonOverlayEnabled(this)
    }

    private fun notifyAiReplyLifecycleStarted(
        requestId: String,
        mode: AiCommunicatePanelController.GenerateMode,
        fresh: Boolean,
    ) {
        OemImeSdk.dispatchAiReplyGenerationStarted(
            requestId = requestId,
            generateMode = mode.name,
            fresh = fresh,
        )
    }

    private fun notifyAiReplyLifecycleFinished(
        requestId: String,
        mode: AiCommunicatePanelController.GenerateMode,
        success: Boolean,
        reason: String,
    ) {
        OemImeSdk.dispatchAiReplyGenerationFinished(
            requestId = requestId,
            generateMode = mode.name,
            success = success,
            reason = reason,
        )
    }

    private suspend fun animateDrawerRetractionForPreparation() {
        withContext(Dispatchers.Main) {
            if (::aiCommunicateController.isInitialized) {
                aiCommunicateController.pauseAnimations()
            }
            drawerHost.animate().cancel()
            drawerHost.translationY = drawerHost.height.toFloat()
            aiCommunicateDrawerRetractedForCapture = true
        }
    }

    private suspend fun ensureAiCommunicateDrawerVisibleAfterCapture() {
        if (!aiCommunicateDrawerRetractedForCapture) return
        withContext(Dispatchers.Main) {
            drawerHost.animate().cancel()
            drawerHost.alpha = 1f
            drawerHost.translationY = 0f
            aiCommunicateDrawerRetractedForCapture = false
        }
        withContext(Dispatchers.Main) {
            if (::aiCommunicateController.isInitialized) {
                aiCommunicateController.resumeAnimations()
            }
        }
    }

    private fun resetAiCommunicateDrawerPresentation() {
        if (!::drawerHost.isInitialized) return
        drawerHost.animate().cancel()
        drawerHost.alpha = 1f
        drawerHost.translationY = 0f
        aiCommunicateDrawerRetractedForCapture = false
    }

    private fun handleImeVisibilityChanged(visible: Boolean) {
        val previousVisible = lastImeVisible
        lastImeVisible = visible
        if (previousVisible == true && !visible && !closeRunning) {
            root.post {
                if (!closeRunning && ::favoriteController.isInitialized) {
                    favoriteController.handleSystemKeyboardHidden()
                }
            }
        }
    }

    private fun currentRootControllerView(): View {
        customDrawerHost?.let { return it.root }
        return if (isAiCommunicateMode()) {
            aiCommunicateController.rootView
        } else {
            favoriteController.rootView
        }
    }

    private fun isAiCommunicateMode(): Boolean {
        return currentEntryMode == ENTRY_MODE_AI_COMMUNICATE
    }

    private fun ensureCurrentEntryModeEnabled(): Boolean {
        if (currentCustomDrawerId != null) {
            return true
        }
        val enabled = if (isAiCommunicateMode()) {
            OemEnterpriseConfig.isAiReplyEntryEnabled(this)
        } else {
            OemEnterpriseConfig.isMaterialLibraryEntryEnabled(this)
        }
        if (enabled) {
            return true
        }
        val message = if (isAiCommunicateMode()) {
            "当前宿主已关闭 AI 回复功能"
        } else {
            "当前宿主已关闭素材库功能"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
        return false
    }

    private fun applyCurrentEntryRestrictionIfNeeded(): Boolean {
        val restriction = if (isAiCommunicateMode()) {
            OemEnterpriseConfig.resolveAiReplyRestriction(this)
        } else {
            OemEnterpriseConfig.resolveMaterialLibraryRestriction(this)
        }
        return if (restriction != null) {
            setMaterialHubWindowTouchable(true)
            setMaterialHubWindowFocusable(true)
            hideRealKeyboard()
            root.post {
                root.isFocusableInTouchMode = true
                root.requestFocus()
            }
            if (isAiCommunicateMode()) {
                ensureAiCommunicateController()
                cancelRestrictedEntryRuntime()
                aiCommunicateController.showRestrictedState(restriction.title, restriction.message)
            } else {
                ensureFavoriteController()
                favoriteController.showRestrictedState(restriction.feature, restriction.message)
            }
            true
        } else {
            false
        }
    }

    private fun cancelRestrictedEntryRuntime() {
        if (!isAiCommunicateMode()) return
        aiCommunicatePrepareJob?.cancel()
        aiCommunicateJob?.cancel()
        hideAiCommunicateNeonOverlay()
        hideAiCommunicateBackgroundNeonOverlay()
        resetAiCommunicateDrawerPresentation()
        projectionPermissionRequestInFlight = false
        projectionPermissionSuppressedForSession = false
        aiCommunicateSessionId = null
        aiCommunicateLatestReplies = emptyList()
        aiCommunicateLatestGenerationContext = null
        AiCommunicateOcrCacheStore.clearSessionCache()
    }

    private fun resetAiCommunicateFlowToStart() {
        aiCommunicatePrepareJob?.cancel()
        aiCommunicateJob?.cancel()
        hideAiCommunicateNeonOverlay()
        hideAiCommunicateBackgroundNeonOverlay()
        resetAiCommunicateDrawerPresentation()
        aiCommunicateSessionId = null
        aiCommunicateLatestReplies = emptyList()
        aiCommunicateLatestGenerationContext = null
        projectionPermissionRequestInFlight = false
        projectionPermissionSuppressedForSession = false
        AiCommunicateOcrCacheStore.clearSessionCache()
        aiCommunicateController.showStart()
    }

    private fun getClipboardText(): String {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clipData = clipboardManager.primaryClip
        return if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0).text?.toString() ?: ""
        } else {
            ""
        }
    }

    private fun hasAiCommunicateImagePermission(): Boolean {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureAiCommunicateImageReplyPermission(onResult: (Boolean) -> Unit) {
        if (hasAiCommunicateImagePermission()) {
            onResult(true)
            return
        }
        val oldResult = ImagePermissionBridge.onResult
        ImagePermissionBridge.onResult = { granted ->
            ImagePermissionBridge.onResult = oldResult
            if (!granted) {
                Toast.makeText(this, "请先授予相册权限后再使用传图回复", Toast.LENGTH_SHORT).show()
            }
            onResult(granted)
        }
        val launched =
            runCatching {
                startActivity(
                    Intent(this, ImagePermissionActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        putExtra(
                            ImagePermissionActivity.EXTRA_PERMISSION_KIND,
                            ImagePermissionActivity.PERMISSION_KIND_IMAGE,
                        )
                    },
                )
                true
            }.getOrElse {
                ImagePermissionBridge.onResult = oldResult
                false
            }
        if (!launched) {
            Toast.makeText(this, "暂时无法拉起相册权限，请稍后重试", Toast.LENGTH_SHORT).show()
            onResult(false)
        }
    }

    private fun ensureFavoritePickerReadPermission(
        type: FavoritePickerType,
        onResult: (Boolean) -> Unit,
    ) {
        if (type == FavoritePickerType.TEXT) {
            onResult(true)
            return
        }
        if (hasFavoritePickerReadPermission(type)) {
            onResult(true)
            return
        }
        val oldResult = ImagePermissionBridge.onResult
        ImagePermissionBridge.onResult = { granted ->
            ImagePermissionBridge.onResult = oldResult
            if (!granted) {
                val label = favoritePickerTitle(type)
                Toast.makeText(this, "请先授予${label}权限后再继续", Toast.LENGTH_SHORT).show()
            }
            onResult(granted)
        }
        val launched =
            runCatching {
                startActivity(
                    Intent(this, ImagePermissionActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        putExtra(
                            ImagePermissionActivity.EXTRA_PERMISSION_KIND,
                            when (type) {
                                FavoritePickerType.VIDEO -> ImagePermissionActivity.PERMISSION_KIND_VIDEO
                                FavoritePickerType.FILE -> ImagePermissionActivity.PERMISSION_KIND_FILE
                                FavoritePickerType.IMAGE -> ImagePermissionActivity.PERMISSION_KIND_IMAGE
                                FavoritePickerType.TEXT -> ImagePermissionActivity.PERMISSION_KIND_IMAGE
                            },
                        )
                    },
                )
                true
            }.getOrElse {
                ImagePermissionBridge.onResult = oldResult
                false
            }
        if (!launched) {
            Toast.makeText(this, "暂时无法拉起权限请求，请稍后重试", Toast.LENGTH_SHORT).show()
            onResult(false)
        }
    }

    private fun hasFavoritePickerReadPermission(type: FavoritePickerType): Boolean {
        return when (type) {
            FavoritePickerType.FILE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
            FavoritePickerType.VIDEO -> {
                val permission =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_VIDEO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }
            FavoritePickerType.IMAGE -> {
                val permission =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }
            FavoritePickerType.TEXT -> true
        }
    }

    private fun favoritePickerTitle(type: FavoritePickerType): String {
        return when (type) {
            FavoritePickerType.IMAGE -> "图片"
            FavoritePickerType.VIDEO -> "视频"
            FavoritePickerType.FILE -> "文档"
            FavoritePickerType.TEXT -> "文本"
        }
    }

    private fun handleAiCommunicateEntrySelected(mode: AiCommunicatePanelController.GenerateMode) {
        aiCommunicateSessionMode = mode
        if (mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY) {
            aiCommunicateController.startImageReplyPicker()
            return
        }
        if (mode == AiCommunicatePanelController.GenerateMode.PASTE) {
            val clipboardText = clipboardReplyText ?: getClipboardText()
            if (clipboardText.isBlank()) {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
                return
            }
            if (clipboardReplyText != null) aiCommunicateController.showClipboardReplyLoading(aiCommunicateController.selectedTone)
            else aiCommunicateController.setPasteGenerating(true)
            requestAiCommunicateRepliesForPaste(clipboardText, aiCommunicateController.selectedTone)
            return
        }

        val automationReady = AiCommunicateScreenCaptureManager.hasAutomationCapture(this)
        val hasProjectionGrant = AiCommunicateScreenCapturePermissionStore.hasUsableGrant(this)
        if (automationReady || hasProjectionGrant) {
            startAiCommunicatePreparation(
                tone = aiCommunicateController.selectedTone,
                force = true,
                mode = mode,
            )
        } else {
            aiCommunicateController.showPermission(mode)
        }
    }

    private fun requestAiCommunicateRepliesForPaste(
        text: String,
        tone: AiCommunicatePanelController.Tone,
    ) {
        if (clipboardReplyText != null) {
            updateClipboardReplyState(com.paopaoime.aikeyboard.sdk.core.drawer.OemClipboardReplyState(
                sourceText = text, loading = true,
            ))
        }
        val mode = AiCommunicatePanelController.GenerateMode.PASTE
        val generationTimeoutMs = resolveAiGenerationTimeoutMs()
        aiCommunicatePrepareJob?.cancel()
        aiCommunicateJob?.cancel()
        val sessionId = UUID.randomUUID().toString()
        aiCommunicateSessionId = sessionId
        aiCommunicateLatestReplies = emptyList()
        aiCommunicateLatestGenerationContext = null
        
        aiCommunicateJob = lifecycleScope.launch {
            val aiTaskLogger = AiReplyTaskLogger.startOcrTask(
                context = applicationContext,
                sessionId = sessionId,
                tone = tone.label,
            )
            var lifecycleFinished = false
            fun finishLifecycle(success: Boolean, reason: String) {
                if (lifecycleFinished) return
                lifecycleFinished = true
                notifyAiReplyLifecycleFinished(sessionId, mode, success, reason)
            }

            notifyAiReplyLifecycleStarted(sessionId, mode, fresh = true)
            try {
                aiTaskLogger.info(
                    phase = "capture",
                    event = "paste_capture_started",
                    title = "获取剪贴板内容",
                    detail = "length=${text.length}",
                )
                
                val generationContextOverride = AiCommunicateReplyService.GenerationContext(
                    screenshotPreviewPath = null,
                    screenshotPreviewPaths = emptyList(),
                    ocrSourceText = text,
                    historyState = AiCommunicateReplyService.HistoryState.NORMAL_HISTORY,
                    actualCaptureCount = 1,
                )
                
                aiTaskLogger.setSummaryNumber("captureCount", 1)
                aiTaskLogger.setSummaryString("historyState", "NORMAL_HISTORY")
                aiTaskLogger.setSummaryNumber("ocrTextLength", text.length)
                
                val generationResult = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(generationTimeoutMs) {
                        runCatching {
                            AiCommunicateReplyService.generateReplies(
                                context = this@MaterialHubActivity,
                                tone = tone.toServiceTone(),
                                mode = AiCommunicatePanelController.GenerateMode.OCR,
                                sessionId = sessionId,
                                allowSnapshotCapture = false,
                                existingContext = generationContextOverride,
                                taskLogger = aiTaskLogger,
                            )
                        }.getOrElse { throw it }
                    } ?: throw AiCommunicateReplyService.UserVisibleFailure(
                        "本次生成超时了，请重试",
                        OemAiReplyErrorCode.TIMEOUT,
                    )
                }
                
                aiCommunicateLatestReplies = generationResult.replies
                aiCommunicateLatestGenerationContext = generationContextOverride
                persistAiCommunicateGeneration(tone, generationResult)
                
                if (generationResult.replies.isNotEmpty()) {
                    if (clipboardReplyText != null) updateClipboardReplyState(
                        com.paopaoime.aikeyboard.sdk.core.drawer.OemClipboardReplyState(
                            sourceText = text, suggestions = generationResult.replies, modelName = generationResult.modelName,
                        )
                    )
                    aiCommunicateController.setPasteGenerating(false)
                    aiCommunicateController.showResult(
                        tone = tone,
                        mode = mode,
                        replies = generationResult.replies,
                    )
                    aiTaskLogger.finishSuccess()
                    finishLifecycle(success = true, reason = "success")
                } else {
                    aiCommunicateController.setPasteGenerating(false)
                    showAiReplyError(
                        mode = mode,
                        tone = tone,
                        errorCode = OemAiReplyErrorCode.RESULT_INVALID,
                        rawMessage = "模型未返回有效回复候选",
                    )
                    aiTaskLogger.finishFailure(
                        status = "generation_failed",
                        reasonCode = "empty_replies",
                        reasonMessage = "模型未返回有效回复候选",
                    )
                    finishLifecycle(success = false, reason = "failed")
                }
            } catch (t: CancellationException) {
                finishLifecycle(success = false, reason = "cancelled")
                throw t
            } catch (t: Throwable) {
                OemLog.e(TAG, "requestAiCommunicateRepliesForPaste failure", t)
                aiCommunicateController.setPasteGenerating(false)
                showAiReplyError(
                    mode = mode,
                    tone = tone,
                    errorCode = resolveAiReplyErrorCode(t),
                    rawMessage = t.message,
                )
                aiTaskLogger.finishFailure(
                    status = "generation_failed",
                    reasonCode = "llm_request_failed",
                    reasonMessage = t.message ?: "生成失败",
                    throwable = t,
                )
                val reason = if (resolveAiReplyErrorCode(t) == OemAiReplyErrorCode.TIMEOUT) "timeout" else "failed"
                finishLifecycle(success = false, reason = reason)
            } finally {
                if (!lifecycleFinished) {
                    finishLifecycle(success = false, reason = "cancelled")
                }
            }
        }
    }

    private fun requestAiCommunicateRepliesForImages(
        imageUris: List<Uri>,
        tone: AiCommunicatePanelController.Tone,
    ) {
        if (imageUris.isEmpty()) {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
            return
        }
        val mode = AiCommunicatePanelController.GenerateMode.IMAGE_REPLY
        val generationTimeoutMs = resolveAiGenerationTimeoutMs()
        aiCommunicatePrepareJob?.cancel()
        aiCommunicateJob?.cancel()
        val sessionId = UUID.randomUUID().toString()
        aiCommunicateSessionId = sessionId
        aiCommunicateLatestReplies = emptyList()
        aiCommunicateLatestGenerationContext = null

        aiCommunicateJob = lifecycleScope.launch {
            val aiTaskLogger = AiReplyTaskLogger.startImageReplyTask(
                context = applicationContext,
                sessionId = sessionId,
                tone = tone.label,
            )
            val stepPacer = AiCommunicateStepPacer()
            var lifecycleFinished = false
            fun finishLifecycle(success: Boolean, reason: String) {
                if (lifecycleFinished) return
                lifecycleFinished = true
                notifyAiReplyLifecycleFinished(sessionId, mode, success, reason)
            }

            notifyAiReplyLifecycleStarted(sessionId, mode, fresh = true)
              try {
                  activateFastLlmPreparationNeon(aiTaskLogger)
                  aiCommunicateController.showPreparing(
                      mode = mode,
                    tone = tone,
                    progress = 10,
                )
                stepPacer.markStepShown()
                aiTaskLogger.info(
                    phase = "capture",
                    event = "image_picker_selection_started",
                    title = "开始读取所选图片",
                    detail = "count=${imageUris.size}",
                )
                val localImagePaths = withContext(Dispatchers.IO) {
                    persistAiCommunicateImageReplyInputs(
                        sessionId = sessionId,
                        imageUris = imageUris,
                    )
                }
                if (localImagePaths.isEmpty()) {
                    throw AiCommunicateReplyService.UserVisibleFailure(
                        "未读取到可用图片，请重新选择后重试",
                        OemAiReplyErrorCode.CAPTURE_FAILED,
                    )
                }
                val historyState = resolveImageReplyHistoryState(localImagePaths.size)
                val generationContextOverride =
                    AiCommunicateReplyService.GenerationContext(
                        screenshotPreviewPath = localImagePaths.firstOrNull(),
                        screenshotPreviewPaths = localImagePaths,
                        ocrSourceText = "",
                        historyState = historyState,
                        actualCaptureCount = localImagePaths.size,
                    )
                aiTaskLogger.setSummaryNumber("captureCount", localImagePaths.size)
                aiTaskLogger.setSummaryString("historyState", historyState.wireValue)
                aiTaskLogger.info(
                    phase = "capture",
                    event = "image_picker_selection_finished",
                    title = "图片读取完成",
                    detail = "count=${localImagePaths.size}",
                    data = JSONObject().put("captureCount", localImagePaths.size),
                )
                stepPacer.awaitNext(minGapMs = 1_000L)
                aiCommunicateController.showGenerating(
                    mode = mode,
                    tone = tone,
                    title = "正在生成回复建议",
                    detail = "正在根据图片中的会话内容整理一组可直接发送的回复建议。",
                    progress = 60,
                    steps = emptyList(),
                )
                stepPacer.markStepShown()
                aiTaskLogger.info(
                    phase = "generation",
                    event = "image_reply_generation_started",
                    title = "开始生成图片回复建议",
                    detail = "count=${localImagePaths.size}",
                )
                val generationResult = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(generationTimeoutMs) {
                        runCatching {
                            AiCommunicateReplyService.generateReplies(
                                context = this@MaterialHubActivity,
                                tone = tone.toServiceTone(),
                                mode = mode,
                                sessionId = sessionId,
                                allowSnapshotCapture = false,
                                existingContext = generationContextOverride,
                                taskLogger = aiTaskLogger,
                            )
                        }.getOrElse { throw it }
                    } ?: throw AiCommunicateReplyService.UserVisibleFailure(
                        "本次生成超时了，请重试",
                        OemAiReplyErrorCode.TIMEOUT,
                    )
                }
                stepPacer.awaitNext(minGapMs = 1_000L)
                aiCommunicateLatestReplies = generationResult.replies
                aiCommunicateLatestGenerationContext =
                    AiCommunicateReplyService.GenerationContext(
                        screenshotPreviewPath = generationResult.screenshotPreviewPath,
                        screenshotPreviewPaths = generationResult.screenshotPreviewPaths,
                        ocrSourceText = generationResult.ocrSourceText,
                        historyState = generationResult.historyState,
                        actualCaptureCount = generationResult.actualCaptureCount,
                    )
                persistAiCommunicateGeneration(tone, generationResult)
                if (generationResult.replies.isNotEmpty()) {
                    aiCommunicateController.showResult(
                        tone = tone,
                        mode = mode,
                        replies = generationResult.replies,
                    )
                    aiTaskLogger.finishSuccess()
                    finishLifecycle(success = true, reason = "success")
                } else {
                    showAiReplyError(
                        mode = mode,
                        tone = tone,
                        errorCode = OemAiReplyErrorCode.RESULT_INVALID,
                        rawMessage = "模型未返回有效回复候选",
                    )
                    aiTaskLogger.finishFailure(
                        status = "generation_failed",
                        reasonCode = "empty_replies",
                        reasonMessage = "模型未返回有效回复候选",
                    )
                    finishLifecycle(success = false, reason = "failed")
                }
            } catch (t: CancellationException) {
                finishLifecycle(success = false, reason = "cancelled")
                throw t
            } catch (t: Throwable) {
                OemLog.e(TAG, "requestAiCommunicateRepliesForImages failure", t)
                showAiReplyError(
                    mode = mode,
                    tone = tone,
                    errorCode = resolveAiReplyErrorCode(t),
                    rawMessage = t.message,
                )
                aiTaskLogger.finishFailure(
                    status = "generation_failed",
                    reasonCode = "image_reply_failed",
                    reasonMessage = t.message ?: "传图回复生成失败",
                    throwable = t,
                )
                val reason = if (resolveAiReplyErrorCode(t) == OemAiReplyErrorCode.TIMEOUT) "timeout" else "failed"
                finishLifecycle(success = false, reason = reason)
            } finally {
                if (!lifecycleFinished) {
                    finishLifecycle(success = false, reason = "cancelled")
                }
                hideAiCommunicateNeonOverlay()
                hideAiCommunicateBackgroundNeonOverlay()
                if (isActive) {
                    ensureAiCommunicateDrawerVisibleAfterCapture()
                }
            }
        }
    }

    private fun persistAiCommunicateImageReplyInputs(
        sessionId: String,
        imageUris: List<Uri>,
    ): List<String> {
        val targetDir = File(filesDir, "ai_communicate/image_reply/$sessionId").apply { mkdirs() }
        return imageUris.mapIndexedNotNull { index, uri ->
            val extension = resolveAiCommunicateImageExtension(uri)
            val targetFile = File(targetDir, "${index + 1}_${UUID.randomUUID()}.$extension")
            val copied = contentResolver.openInputStream(uri)?.use { input ->
                copyAiCommunicateImageInput(input, targetFile)
            } ?: false
            targetFile.absolutePath.takeIf { copied && targetFile.exists() && targetFile.length() > 0L }
        }
    }

    private fun copyAiCommunicateImageInput(input: InputStream, targetFile: File): Boolean {
        return runCatching {
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
            true
        }.getOrDefault(false)
    }

    private fun resolveAiCommunicateImageExtension(uri: Uri): String {
        val displayName = queryAiCommunicateDisplayName(uri)
        val fromName = displayName.substringAfterLast('.', "").trim().lowercase()
        if (fromName.isNotBlank()) {
            return fromName
        }
        val mimeType = contentResolver.getType(uri).orEmpty().lowercase()
        return when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            else -> "jpg"
        }
    }

    private fun queryAiCommunicateDisplayName(uri: Uri): String {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use ""
                    cursor.getString(0).orEmpty()
                }
        }.getOrNull().orEmpty()
    }

    private fun resolveImageReplyHistoryState(imageCount: Int): AiCommunicateReplyService.HistoryState {
        return when {
            imageCount >= 3 -> AiCommunicateReplyService.HistoryState.NORMAL_HISTORY
            imageCount >= 1 -> AiCommunicateReplyService.HistoryState.LIMITED_HISTORY
            else -> AiCommunicateReplyService.HistoryState.NO_HISTORY
        }
    }

    private fun startAiCommunicatePreparation(
        tone: AiCommunicatePanelController.Tone,
        force: Boolean,
        mode: AiCommunicatePanelController.GenerateMode,
        allowActivityCaptureFallback: Boolean = true,
    ) {
        if (!isAiCommunicateMode()) return
        aiCommunicatePrepareJob?.cancel()
        aiCommunicateJob?.cancel()
        aiCommunicatePrepareJob =
            lifecycleScope.launch {
                val sessionId = UUID.randomUUID().toString()
                aiCommunicateSessionId = sessionId
                val taskLogger = if (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM) {
                    AiReplyTaskLogger.startFastLlmTask(
                        context = applicationContext,
                        sessionId = sessionId,
                        tone = tone.label,
                    )
                } else {
                    AiReplyTaskLogger.startOcrTask(
                        context = applicationContext,
                        sessionId = sessionId,
                        tone = tone.label,
                    )
                }
                var requestDispatched = false
                try {
                    taskLogger.info(
                        phase = "prepare",
                        event = "prepare_started",
                        title = if (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM) "极速生成准备开始" else "识屏回复准备开始",
                        detail = "countdown=$FAST_LLM_PREPARE_COUNTDOWN_START",
                    )
                    taskLogger.setSummaryNumber("countdownSeconds", FAST_LLM_PREPARE_COUNTDOWN_START)
                    taskLogger.setSummaryBoolean(
                        "usedAutomationCapture",
                        AiCommunicateScreenCaptureManager.shouldPreferAutomationCapture(this@MaterialHubActivity),
                    )
                    taskLogger.setSummaryBoolean(
                        "projectionGrantAvailable",
                        AiCommunicateScreenCapturePermissionStore.hasUsableGrant(this@MaterialHubActivity),
                    )

                    // Step 1 ACTIVE
                    aiCommunicateController.showPreparing(
                        mode = mode,
                        tone = tone,
                        progress = 10,
                    )
                    delay(1000L) // Wait 1s for "checking environment"

                    if (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM || mode == AiCommunicatePanelController.GenerateMode.OCR) {
                        activateFastLlmPreparationNeon(taskLogger)
                    }

                    // Step 1 DONE, Step 2 ACTIVE
                    aiCommunicateController.showPreparing(
                        mode = mode,
                        tone = tone,
                        progress = 40,
                    )
                    delay(500L) // Wait 0.5s before pausing animation

                    taskLogger.info(
                        phase = "drawer",
                        event = "neon_animation_paused",
                        title = "提前暂停霓虹动画",
                    )
                    pauseAiCommunicateNeonAnimation()
                    delay(1000L) // Wait 1.0s with animation paused to eliminate frame drop during retraction

                    taskLogger.info(
                        phase = "drawer",
                        event = "drawer_retract_started",
                        title = "抽屉开始收回",
                    )
                    animateDrawerRetractionForPreparation()
                    taskLogger.info(
                        phase = "drawer",
                        event = "drawer_retract_finished",
                        title = "抽屉收回完成",
                    )
                    requestDispatched = true
                    requestAiCommunicateReplies(
                        tone = tone,
                        force = force,
                        mode = mode,
                        allowActivityCaptureFallback = allowActivityCaptureFallback,
                        sessionIdOverride = sessionId,
                        taskLogger = taskLogger,
                    )
                } finally {
                    if (!requestDispatched) {
                        taskLogger.finishFailure(
                            status = "cancelled",
                            reasonCode = "task_cancelled_before_request",
                            reasonMessage = "生成在准备阶段被中断",
                        )
                    }
                    aiCommunicatePrepareJob = null
                }
            }
    }

    private fun requestAiCommunicateReplies(
        tone: AiCommunicatePanelController.Tone,
        force: Boolean,
        mode: AiCommunicatePanelController.GenerateMode = aiCommunicateSessionMode,
        allowActivityCaptureFallback: Boolean = true,
        sessionIdOverride: String? = null,
        taskLogger: AiReplyTaskLogger? = null,
    ) {
        val captureTimeoutMs = 6_000L
        val fastBatchCaptureTimeoutMs = 30_000L
        val snapshotPostProcessTimeoutMs = 6_000L
        val generationTimeoutMs = resolveAiGenerationTimeoutMs()
        if (!isAiCommunicateMode()) return
        aiCommunicatePrepareJob?.cancel()
        val automationReady = AiCommunicateScreenCaptureManager.hasAutomationCapture(this)
        val projectionGrantAvailable = AiCommunicateScreenCapturePermissionStore.hasUsableGrant(this)
        if (!automationReady && !projectionGrantAvailable) {
            aiCommunicateController.showPermission(mode)
            lifecycleScope.launch {
                taskLogger?.finishFailure(
                    status = "capture_failed",
                    reasonCode = "capture_capability_missing",
                    reasonMessage = "当前没有可用截图能力",
                )
            }
            return
        }
        aiCommunicateSessionMode = mode
        aiCommunicateJob?.cancel()
        val sessionId = sessionIdOverride ?: UUID.randomUUID().toString()
        aiCommunicateSessionId = sessionId
        aiCommunicateLatestReplies = emptyList()
        aiCommunicateLatestGenerationContext = null
        aiCommunicateJob = lifecycleScope.launch {
            val requestStartedAt = SystemClock.elapsedRealtime()
            val stepPacer = AiCommunicateStepPacer()
            val shouldShowNeonOverlay = mode == AiCommunicatePanelController.GenerateMode.FAST_LLM ||
                                        mode == AiCommunicatePanelController.GenerateMode.OCR ||
                                        mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY
            val aiTaskLogger =
                taskLogger ?: when (mode) {
                    AiCommunicatePanelController.GenerateMode.FAST_LLM ->
                        AiReplyTaskLogger.startFastLlmTask(
                            context = applicationContext,
                            sessionId = sessionId,
                            tone = tone.label,
                        )
                    AiCommunicatePanelController.GenerateMode.IMAGE_REPLY ->
                        AiReplyTaskLogger.startImageReplyTask(
                            context = applicationContext,
                            sessionId = sessionId,
                            tone = tone.label,
                        )
                    AiCommunicatePanelController.GenerateMode.OCR, AiCommunicatePanelController.GenerateMode.PASTE ->
                        AiReplyTaskLogger.startOcrTask(
                            context = applicationContext,
                            sessionId = sessionId,
                            tone = tone.label,
                        )
                }
            var lifecycleFinished = false
            fun finishLifecycle(success: Boolean, reason: String) {
                if (lifecycleFinished) return
                lifecycleFinished = true
                notifyAiReplyLifecycleFinished(sessionId, mode, success, reason)
            }

            notifyAiReplyLifecycleStarted(sessionId, mode, fresh = true)
            try {
                if (shouldShowNeonOverlay) {
                    activateFastLlmPreparationNeon(aiTaskLogger)
                }
                var latestPreviewPath: String? = null
                var latestOcrPreview = ""
                var captureSucceeded = false
                var generationContextOverride: AiCommunicateReplyService.GenerationContext? = null
                if (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM) {
                    aiTaskLogger?.info(
                        phase = "capture",
                        event = "background_capture_started",
                        title = "开始后台多屏截图",
                    )
                    OemLog.d(TAG, "requestAiCommunicateReplies FAST_LLM using multi-screenshot path with OCR disabled")
                    val captureBatch =
                        if (OemImeSdk.isScreenshotProviderRegistered()) {
                            captureFastLlmBatchBehindDrawer(
                                targetCount = AI_FAST_CAPTURE_TARGET_COUNT,
                                maxSwipeAttempts = AI_FAST_CAPTURE_MAX_SWIPE_ATTEMPTS,
                                taskLogger = aiTaskLogger,
                            )
                        } else {
                            withTimeoutOrNull(fastBatchCaptureTimeoutMs) {
                                captureFastLlmBatchBehindDrawer(
                                    targetCount = AI_FAST_CAPTURE_TARGET_COUNT,
                                    maxSwipeAttempts = AI_FAST_CAPTURE_MAX_SWIPE_ATTEMPTS,
                                    taskLogger = aiTaskLogger,
                                )
                            }
                        }
                    if (captureBatch == null) {
                        AiCommunicateScreenCaptureManager.setLastFailureReason(
                            "多屏截图处理超时或被中断，请稍后重试"
                        )
                        aiTaskLogger?.warn(
                            phase = "capture",
                            event = "fast_capture_timeout",
                            title = "多屏截图超时",
                            detail = "timeoutMs=$fastBatchCaptureTimeoutMs",
                        )
                    }
                    latestPreviewPath = captureBatch?.primaryPreviewPath
                    OemLog.d(
                        TAG,
                        "requestAiCommunicateReplies fast capture finished previewCount=${captureBatch?.actualCaptureCount ?: 0} previewPath=${!latestPreviewPath.isNullOrBlank()} historyState=${captureBatch?.historyState}"
                    )
                    captureSucceeded = captureBatch != null && captureBatch.orderedPreviewPaths.isNotEmpty()
                    generationContextOverride =
                        captureBatch?.let {
                            aiTaskLogger?.setSummaryNumber("captureCount", it.actualCaptureCount)
                            aiTaskLogger?.setSummaryString("historyState", it.historyState.wireValue)
                            AiCommunicateReplyService.GenerationContext(
                                screenshotPreviewPath = it.primaryPreviewPath,
                                screenshotPreviewPaths = it.orderedPreviewPaths,
                                ocrSourceText = it.ocrSourceText,
                                historyState = it.historyState,
                                actualCaptureCount = it.actualCaptureCount,
                            )
                        }
                    latestOcrPreview = if (!captureSucceeded || captureBatch == null) {
                        ""
                    } else {
                        when (captureBatch.historyState) {
                            AiCommunicateReplyService.HistoryState.NO_HISTORY ->
                                "已读取聊天界面，当前判断为新客户或无历史聊天场景。"
                            AiCommunicateReplyService.HistoryState.LIMITED_HISTORY ->
                                "已获取少量聊天历史截图，正在整理上下文。"
                            AiCommunicateReplyService.HistoryState.NORMAL_HISTORY ->
                                "已获取连续聊天截图，正在理解对话对象、语气和上下文。"
                        }
                    }
                } else {
            // OCR
            aiTaskLogger?.info(
                phase = "capture",
                event = "ocr_capture_started",
                title = "开始识屏截图",
                detail = "timeoutMs=$snapshotPostProcessTimeoutMs",
            )
            val snapshot =
                if (OemImeSdk.isScreenshotProviderRegistered()) {
                    captureSnapshotBehindDrawer(force = force || allowActivityCaptureFallback)
                } else {
                    withTimeoutOrNull(snapshotPostProcessTimeoutMs) {
                        captureSnapshotBehindDrawer(force = force || allowActivityCaptureFallback)
                    }
                }
            ensureAiCommunicateDrawerVisibleAfterCapture()
            latestPreviewPath = AiCommunicateOcrCacheStore.previewImagePath()
            latestOcrPreview = snapshot?.text.orEmpty()
                    val historyState = resolveHistoryStateFromOcrText(latestOcrPreview, latestPreviewPath)
                    val actualCaptureCount =
                        if (latestPreviewPath.isNullOrBlank() && latestOcrPreview.isBlank()) 0 else 1
                    generationContextOverride =
                        AiCommunicateReplyService.GenerationContext(
                            screenshotPreviewPath = latestPreviewPath,
                            screenshotPreviewPaths = listOfNotNull(latestPreviewPath),
                            ocrSourceText = latestOcrPreview,
                            historyState = historyState,
                            actualCaptureCount = actualCaptureCount,
                        )
                    aiTaskLogger?.setSummaryNumber("captureCount", actualCaptureCount)
                    aiTaskLogger?.setSummaryString("historyState", historyState.wireValue)
                    aiTaskLogger?.setSummaryNumber("ocrTextLength", latestOcrPreview.length)
                    aiTaskLogger?.info(
                        phase = "capture",
                        event = "ocr_snapshot_finished",
                        title = "识屏快照完成",
                        detail =
                            "snapshot=${snapshot != null} preview=${!latestPreviewPath.isNullOrBlank()} textLength=${latestOcrPreview.length}",
                        data =
                            JSONObject()
                                .put("snapshotAvailable", snapshot != null)
                                .put("previewAvailable", !latestPreviewPath.isNullOrBlank())
                                .put("textLength", latestOcrPreview.length)
                                .put("historyState", historyState.wireValue)
                                .put("captureCount", actualCaptureCount),
                    )
                    OemLog.d(
                        TAG,
                        "requestAiCommunicateReplies ocr capture finished snapshot=${snapshot != null} previewPath=${!latestPreviewPath.isNullOrBlank()} textLength=${latestOcrPreview.length}"
                    )
                    captureSucceeded = snapshot != null || !latestPreviewPath.isNullOrBlank()
                }
                if (!captureSucceeded) {
                    val automationPreferred =
                        AiCommunicateScreenCaptureManager.shouldPreferAutomationCapture(this@MaterialHubActivity)
                    val hasProjectionGrant = AiCommunicateScreenCapturePermissionStore.hasUsableGrant(this@MaterialHubActivity)
                    val captureFailureDetail = AiCommunicateScreenCaptureManager.getLastFailureReason().trim()
                    if (!hasProjectionGrant) {
                        OemLog.w(
                            TAG,
                            "requestAiCommunicateReplies capture failed, fallback to permission page detail=$captureFailureDetail automationPreferred=$automationPreferred"
                        )
                        aiTaskLogger?.finishFailure(
                            status = "capture_failed",
                            reasonCode = "capture_capability_missing",
                            reasonMessage = "当前没有可用截图能力，已切到授权页",
                        )
                        aiCommunicateController.showPermission(mode)
                        finishLifecycle(success = false, reason = "failed")
                        return@launch
                    }
                    val message = buildString {
                        append("截图失败")
                        if (captureFailureDetail.isNotBlank()) {
                            append("：")
                            append(captureFailureDetail)
                        }
                        append("。请重新授权截图权限后重试")
                    }
                    showAiReplyError(
                        mode = mode,
                        tone = tone,
                        errorCode = OemAiReplyErrorCode.CAPTURE_FAILED,
                        rawMessage = message,
                    )
                    aiTaskLogger?.finishFailure(
                        status = "capture_failed",
                        reasonCode = "screenshot_empty",
                        reasonMessage = message,
                    )
                    finishLifecycle(success = false, reason = "failed")
                    return@launch
                }
                aiCommunicateController.showGenerating(
                      mode = mode,
                      tone = tone,
                      title = if (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM) "正在理解聊天上下文" else "正在提取聊天文本",
                      detail = if (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM) {
                          "已获取聊天画面，正在整理对话对象、语气和聊天阶段。"
                      } else if (latestOcrPreview.isBlank()) {
                          "已完成截屏，正在继续整理聊天文本，若文本较少会按通用场景生成。"
                      } else {
                          "已完成截屏，正在基于识屏文本整理本轮对话上下文。"
                      },
                      progress = 40,
                      steps = emptyList(),
                  )
                  stepPacer.markStepShown()
                  aiTaskLogger?.info(
                      phase = "generation",
                      event = "generation_started",
                      title = if (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM) "开始理解聊天上下文" else "开始提取聊天文本",
                      detail = "mode=${mode.name} preview=${!latestPreviewPath.isNullOrBlank()} ocrTextLength=${latestOcrPreview.length}",
                      data =
                          JSONObject()
                              .put("mode", mode.name)
                              .put("previewAvailable", !latestPreviewPath.isNullOrBlank())
                              .put("ocrTextLength", latestOcrPreview.length),
                  )
                  stepPacer.awaitNext(minGapMs = 2_000L) // Wait at least 2s for Step 2 ACTIVE

                  aiCommunicateController.showGenerating(
                      mode = mode,
                      tone = tone,
                      title = "正在整理上下文",
                      detail = "已完成对话理解，准备生成回复建议",
                      progress = 70, // Step 2 DONE, Step 3 TODO
                      steps = emptyList(),
                  )
                  delay(1000L) // Wait 1s

                  aiCommunicateController.showGenerating(
                      mode = mode,
                      tone = tone,
                      title = "正在生成回复建议",
                      detail = if (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM) {
                          "正在把截图理解结果整理成一组可直接使用的回复建议。"
                      } else {
                          "正在把识屏文本与对话语境整理成一组可直接使用的回复建议。"
                      },
                      progress = 90, // Step 3 ACTIVE
                      steps = emptyList(),
                  )
                  stepPacer.markStepShown()
                val generationResult = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(generationTimeoutMs) {
                        runCatching {
                            AiCommunicateReplyService.generateReplies(
                                context = this@MaterialHubActivity,
                                tone = tone.toServiceTone(),
                                mode = mode,
                                sessionId = sessionId,
                                allowSnapshotCapture = false,
                                existingContext = generationContextOverride,
                                taskLogger = aiTaskLogger,
                            )
                        }.getOrElse { throw it }
                    } ?: throw AiCommunicateReplyService.UserVisibleFailure(
                        "本次生成超时了，请重试",
                        OemAiReplyErrorCode.TIMEOUT,
                    )
                }
                stepPacer.awaitNext(minGapMs = 1_000L)
                OemLog.d(
                    TAG,
                    "requestAiCommunicateReplies tone=${tone.label} mode=${mode.label} replies=${generationResult.replies.size} totalCostMs=${SystemClock.elapsedRealtime() - requestStartedAt}"
                )
                aiCommunicateLatestReplies = generationResult.replies
                aiCommunicateLatestGenerationContext =
                    AiCommunicateReplyService.GenerationContext(
                        screenshotPreviewPath = generationResult.screenshotPreviewPath,
                        screenshotPreviewPaths = generationResult.screenshotPreviewPaths,
                        ocrSourceText = generationResult.ocrSourceText,
                        historyState = generationResult.historyState,
                        actualCaptureCount = generationResult.actualCaptureCount,
                    )
                persistAiCommunicateGeneration(tone, generationResult)

                if (generationResult.replies.isNotEmpty()) {
                    aiCommunicateController.showResult(
                        tone = tone,
                        mode = generationResult.mode,
                        replies = generationResult.replies,
                    )
                    aiTaskLogger?.info(
                        phase = "result",
                        event = "result_rendered",
                        title = "回复结果已展示",
                        detail = "replyCount=${generationResult.replies.size}",
                    )
                    aiTaskLogger?.setSummaryNumber("replyCount", generationResult.replies.size)
                    aiTaskLogger?.finishSuccess()
                    finishLifecycle(success = true, reason = "success")
                } else {
                    showAiReplyError(
                        mode = mode,
                        tone = tone,
                        errorCode = OemAiReplyErrorCode.RESULT_INVALID,
                        rawMessage = "模型未返回有效回复候选",
                    )
                    aiTaskLogger?.finishFailure(
                        status = "generation_failed",
                        reasonCode = "empty_replies",
                        reasonMessage = "模型未返回有效回复候选",
                    )
                    finishLifecycle(success = false, reason = "failed")
                }
            } catch (_: TimeoutCancellationException) {
                showAiReplyError(
                    mode = mode,
                    tone = tone,
                    errorCode = OemAiReplyErrorCode.TIMEOUT,
                    rawMessage = "本次识别超时了，请重试",
                )
                aiTaskLogger?.finishFailure(
                    status = "timeout",
                    reasonCode = "llm_request_timeout",
                    reasonMessage = "本次识别超时了，请重试",
                )
                finishLifecycle(success = false, reason = "timeout")
            } catch (t: AiCommunicateReplyService.UserVisibleFailure) {
                OemLog.w(TAG, "requestAiCommunicateReplies user visible failure message=${t.message}", t)
                showAiReplyError(
                    mode = mode,
                    tone = tone,
                    errorCode = t.errorCode ?: OemAiReplyErrorCode.UNKNOWN,
                    rawMessage = t.message,
                )
                aiTaskLogger?.finishFailure(
                    status = "generation_failed",
                    reasonCode = "llm_request_failed",
                    reasonMessage = t.message ?: "AI生成失败",
                    throwable = t,
                )
                val reason = if (t.errorCode == OemAiReplyErrorCode.TIMEOUT) "timeout" else "failed"
                finishLifecycle(success = false, reason = reason)
            } catch (t: CancellationException) {
                finishLifecycle(success = false, reason = "cancelled")
                throw t
            } catch (t: Throwable) {
                OemLog.e(TAG, "requestAiCommunicateReplies unexpected failure", t)
                showAiReplyError(
                    mode = mode,
                    tone = tone,
                    errorCode = resolveAiReplyErrorCode(t),
                    rawMessage = t.message,
                )
                aiTaskLogger?.finishFailure(
                    status = "generation_failed",
                    reasonCode = "llm_request_failed",
                    reasonMessage = t.message ?: "本次识别失败了，请重试",
                    throwable = t,
                )
                finishLifecycle(success = false, reason = "failed")
            } finally {
                if (!lifecycleFinished) {
                    finishLifecycle(success = false, reason = "cancelled")
                }
                if (shouldShowNeonOverlay) {
                    hideAiCommunicateNeonOverlay()
                    hideAiCommunicateBackgroundNeonOverlay()
                }
                if (isActive) {
                    ensureAiCommunicateDrawerVisibleAfterCapture()
                }
            }
        }
    }

    private fun refreshAiCommunicateRepliesFromExistingContext(
        requestedTone: AiCommunicatePanelController.Tone,
        fallbackTone: AiCommunicatePanelController.Tone,
    ) {
        val mode = aiCommunicateSessionMode
        val previousReplies = aiCommunicateLatestReplies
        val existingContext = aiCommunicateLatestGenerationContext
        if (!isAiCommunicateMode()) return
        if (
            existingContext == null ||
            (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM &&
                existingContext.screenshotPreviewPath.isNullOrBlank()) ||
            (mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY &&
                existingContext.screenshotPreviewPaths.isEmpty() &&
                existingContext.screenshotPreviewPath.isNullOrBlank()) ||
            (mode == AiCommunicatePanelController.GenerateMode.OCR &&
                existingContext.screenshotPreviewPath.isNullOrBlank() &&
                existingContext.ocrSourceText.isBlank())
        ) {
            Toast.makeText(this, "上一轮聊天上下文已失效，请返回重新读取聊天内容", Toast.LENGTH_SHORT).show()
            if (previousReplies.isNotEmpty()) {
                aiCommunicateController.showResult(
                    tone = fallbackTone,
                    mode = mode,
                    replies = previousReplies,
                )
            }
            return
        }
        val generationTimeoutMs = resolveAiGenerationTimeoutMs()
        aiCommunicateJob?.cancel()
        val sessionId = UUID.randomUUID().toString()
        aiCommunicateSessionId = sessionId
        aiCommunicateController.showResultRefreshing(
            tone = requestedTone,
            mode = mode,
            replies = previousReplies,
        )
        aiCommunicateJob = lifecycleScope.launch {
            var lifecycleFinished = false
            fun finishLifecycle(success: Boolean, reason: String) {
                if (lifecycleFinished) return
                lifecycleFinished = true
                notifyAiReplyLifecycleFinished(sessionId, mode, success, reason)
            }

            notifyAiReplyLifecycleStarted(sessionId, mode, fresh = false)
            try {
                val generationResult =
                    withContext(Dispatchers.IO) {
                        withTimeoutOrNull(generationTimeoutMs) {
                            runCatching {
                                AiCommunicateReplyService.generateReplies(
                                    context = this@MaterialHubActivity,
                                    tone = requestedTone.toServiceTone(),
                                    mode = mode,
                                    sessionId = sessionId,
                                    allowSnapshotCapture = false,
                                    existingContext = existingContext,
                                )
                            }.getOrElse { throw it }
                        } ?: throw AiCommunicateReplyService.UserVisibleFailure(
                            "本次生成超时了，请重试",
                            OemAiReplyErrorCode.TIMEOUT,
                        )
                    }
                OemLog.d(
                    TAG,
                    "refreshAiCommunicateRepliesFromExistingContext tone=${requestedTone.label} mode=${mode.label} replies=${generationResult.replies.size}"
                )
                aiCommunicateLatestReplies = generationResult.replies
                aiCommunicateLatestGenerationContext =
                    AiCommunicateReplyService.GenerationContext(
                        screenshotPreviewPath = generationResult.screenshotPreviewPath,
                        screenshotPreviewPaths = generationResult.screenshotPreviewPaths,
                        ocrSourceText = generationResult.ocrSourceText,
                        historyState = generationResult.historyState,
                        actualCaptureCount = generationResult.actualCaptureCount,
                    )
                persistAiCommunicateGeneration(requestedTone, generationResult)
                aiCommunicateController.showResult(
                    tone = requestedTone,
                    mode = generationResult.mode,
                    replies = generationResult.replies,
                )
                finishLifecycle(success = true, reason = "success")
            } catch (t: AiCommunicateReplyService.UserVisibleFailure) {
                OemLog.w(TAG, "refreshAiCommunicateRepliesFromExistingContext user visible failure message=${t.message}", t)
                Toast.makeText(this@MaterialHubActivity, "本次刷新没有成功，请重试", Toast.LENGTH_SHORT).show()
                aiCommunicateController.showResult(
                    tone = fallbackTone,
                    mode = mode,
                    replies = previousReplies,
                )
                val reason = if (t.errorCode == OemAiReplyErrorCode.TIMEOUT) "timeout" else "failed"
                finishLifecycle(success = false, reason = reason)
            } catch (t: CancellationException) {
                finishLifecycle(success = false, reason = "cancelled")
                throw t
            } catch (t: Throwable) {
                OemLog.e(TAG, "refreshAiCommunicateRepliesFromExistingContext unexpected failure", t)
                Toast.makeText(this@MaterialHubActivity, "本次刷新失败了，请重试", Toast.LENGTH_SHORT).show()
                aiCommunicateController.showResult(
                    tone = fallbackTone,
                    mode = mode,
                    replies = previousReplies,
                )
                finishLifecycle(success = false, reason = "failed")
            } finally {
                if (!lifecycleFinished) {
                    finishLifecycle(success = false, reason = "cancelled")
                }
            }
        }
    }

    private fun buildAiCommunicateGeneratingSteps(
        mode: AiCommunicatePanelController.GenerateMode,
        activeStep: Int,
    ): List<AiCommunicatePanelController.GeneratingStep> {
        val titles =
            if (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM) {
                listOf(
                    "读取聊天界面",
                    "理解截图上下文",
                    "生成回复建议",
                )
            } else {
                listOf(
                    "读取聊天界面",
                    "提取聊天文本",
                    "生成回复建议",
                )
            }
        val details =
            if (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM) {
                listOf(
                    "截取当前聊天画面，准备交给模型理解。",
                    "分析聊天对象、语气和上下文关系。",
                    "整理出一组更适合当前场景的回复建议。",
                )
            } else {
                listOf(
                    "截取当前聊天画面，准备识别聊天文字。",
                    "提取对话文本，整理本轮会话上下文。",
                    "结合识屏文本生成一组回复建议。",
                )
            }
        return titles.mapIndexed { index, title ->
            AiCommunicatePanelController.GeneratingStep(
                title = title,
                detail = details[index],
                status = when {
                    activeStep > index -> AiCommunicatePanelController.StepStatus.DONE
                    activeStep == index -> AiCommunicatePanelController.StepStatus.ACTIVE
                    else -> AiCommunicatePanelController.StepStatus.TODO
                },
            )
        }
    }

    private class AiCommunicateStepPacer {
        private var lastStepAt = 0L

        fun markStepShown(now: Long = SystemClock.elapsedRealtime()) {
            lastStepAt = now
        }

        suspend fun awaitNext(minGapMs: Long = 500L) {
            if (lastStepAt == 0L) {
                lastStepAt = SystemClock.elapsedRealtime()
                return
            }
            val elapsed = SystemClock.elapsedRealtime() - lastStepAt
            if (elapsed < minGapMs) {
                delay(minGapMs - elapsed)
            }
            lastStepAt = SystemClock.elapsedRealtime()
        }
    }

    private fun handleAiCommunicateReplySelected(
        index: Int,
        reply: String,
    ) {
        if (clipboardReplyText != null) {
            commitClipboardReply(index, reply)
            return
        }
        val sessionId = aiCommunicateSessionId
        lifecycleScope.launch {
            val sendReply = normalizeTextForSendLimit(reply) ?: return@launch
            if (sendReply.isBlank()) {
                Toast.makeText(applicationContext, "回复内容为空，暂不能发送", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (sessionId != null) {
                recordAiCommunicateReplySelection(
                    sessionId = sessionId,
                    replyIndex = index,
                    replyText = reply,
                )
            }
            val hostPackageName = aiCommunicateHostPackageName.orEmpty()
            OemLog.d(
                TAG,
                "AI reply selected package=$hostPackageName " +
                    "replyLength=${reply.length} sendLength=${sendReply.length}",
            )
            val restorePolicy = FavoriteBridge.hostInputRestorePolicy(hostPackageName)
            if (restorePolicy.dispatchTextBeforeOverlayClose) {
                // Some hosts replace the real editor connection with a [-1,-1] placeholder when
                // this activity finishes. Send through the still-valid original connection first;
                // this matches the attached material-library text path and needs no accessibility.
                val commitSuccess = FavoriteBridge.commitText(sendReply)
                OemLog.d(TAG, "AI reply pre-close commit package=$hostPackageName success=$commitSuccess")
                if (!commitSuccess) {
                    closeMaterialHub()
                    Toast.makeText(applicationContext, "当前暂未写入输入框，请重试", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                delay(120L)
                val sendSuccess = FavoriteBridge.performTextSend(sendReply)
                OemLog.d(TAG, "AI reply pre-close send package=$hostPackageName success=$sendSuccess")
                closeMaterialHub()
                if (!sendSuccess) {
                    Toast.makeText(applicationContext, "当前暂未自动发送成功，请手动发送", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            closeMaterialHub {
                FavoriteHostSendWorker.scope.launch sendReply@{
                    delay(AI_REPLY_AFTER_CLOSE_SEND_DELAY_MS)
                    // Use the same host-input restoration path as a single material-library text.
                    val inputReady = FavoriteHostInputRestorer.restore(hostPackageName)
                    OemLog.d(TAG, "AI reply host input restore package=$hostPackageName ready=$inputReady")
                    if (!inputReady) {
                        Toast.makeText(
                            applicationContext,
                            "未恢复到聊天输入框，请重新打开 AI 回复后再试",
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@sendReply
                    }
                    val commitSuccess = FavoriteBridge.commitText(sendReply)
                    if (!commitSuccess) {
                        Toast.makeText(applicationContext, "当前暂未写入输入框，请重试", Toast.LENGTH_SHORT).show()
                        return@sendReply
                    }
                    delay(120L)
                    if (!FavoriteBridge.performTextSend(sendReply)) {
                        Toast.makeText(applicationContext, "当前暂未自动发送成功，请手动发送", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private suspend fun persistAiCommunicateGeneration(
        tone: AiCommunicatePanelController.Tone,
        generationResult: AiCommunicateReplyService.GenerationResult,
    ) {
        val accessToken = AuthSessionStore.accessToken(this).trim()
        val refreshToken = AuthSessionStore.refreshToken(this).trim()
        if (accessToken.isBlank()) return
        val screenshotDataUrls =
            generationResult.screenshotPreviewPaths
                .mapNotNull(::encodePreviewImageAsDataUrl)
                .ifEmpty {
                    listOfNotNull(encodePreviewImageAsDataUrl(generationResult.screenshotPreviewPath))
                }
        val screenshotDataUrl = screenshotDataUrls.firstOrNull()
        OemLog.d(
            TAG,
            "persistAiCommunicateGeneration preparing upload sessionId=${generationResult.sessionId} screenshotCount=${screenshotDataUrls.size} historyState=${generationResult.historyState} actualCaptureCount=${generationResult.actualCaptureCount}"
        )
        val recordResult =
            withContext(Dispatchers.IO) {
                AuthApiClient.upsertAiCommunicateRecord(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    payload =
                        AuthApiClient.AiCommunicateRecordPayload(
                            sessionId = generationResult.sessionId,
                            mode = mapAiCommunicateBackendMode(generationResult.mode),
                            tone = tone.label,
                            promptText = generationResult.promptText,
                            ocrContextPreview = generationResult.ocrContextPreview,
                            screenshotDataUrl = screenshotDataUrl,
                            screenshotDataUrls = screenshotDataUrls,
                            replies = generationResult.replies,
                            modelName = generationResult.modelName,
                            provider = generationResult.provider,
                        ),
                )
            }
        recordResult.newAccessToken?.takeIf { it.isNotBlank() }?.let { newAccess ->
            AuthSessionStore.updateTokens(this, newAccess, recordResult.newRefreshToken)
        }
        if (!recordResult.success) {
            OemLog.w(
                TAG,
                "persistAiCommunicateGeneration record upload failed message=${recordResult.message} sessionId=${generationResult.sessionId}"
            )
        } else {
            OemLog.d(
                TAG,
                "persistAiCommunicateGeneration record uploaded sessionId=${generationResult.sessionId}"
            )
        }
        generationResult.tokenUsageEvent?.let { usageEvent ->
            val usageResult =
                withContext(Dispatchers.IO) {
                    AuthApiClient.uploadTokenUsageEvents(
                        accessToken = AuthSessionStore.accessToken(this@MaterialHubActivity),
                        refreshToken = AuthSessionStore.refreshToken(this@MaterialHubActivity),
                        events = listOf(usageEvent),
                    )
                }
            usageResult.newAccessToken?.takeIf { it.isNotBlank() }?.let { newAccess ->
                AuthSessionStore.updateTokens(this, newAccess, usageResult.newRefreshToken)
            }
            if (!usageResult.success) {
                OemLog.w(
                    TAG,
                    "persistAiCommunicateGeneration token usage upload failed message=${usageResult.message} sessionId=${generationResult.sessionId}"
                )
            } else {
                OemLog.d(
                    TAG,
                    "persistAiCommunicateGeneration token usage uploaded accepted=${usageResult.acceptedCount} duplicated=${usageResult.duplicatedCount} rejected=${usageResult.rejectedCount} sessionId=${generationResult.sessionId}"
                )
            }
        } ?: OemLog.w(
            TAG,
            "persistAiCommunicateGeneration skipped token usage upload because tokenUsageEvent is null sessionId=${generationResult.sessionId}"
        )
    }

    private fun mapAiCommunicateBackendMode(mode: AiCommunicatePanelController.GenerateMode): String {
        return when (mode) {
            AiCommunicatePanelController.GenerateMode.FAST_LLM,
            AiCommunicatePanelController.GenerateMode.IMAGE_REPLY,
            -> "llm"
            AiCommunicatePanelController.GenerateMode.OCR, AiCommunicatePanelController.GenerateMode.PASTE -> "ocr"
        }
    }

    private suspend fun recordAiCommunicateReplySelection(
        sessionId: String,
        replyIndex: Int,
        replyText: String,
    ) {
        val accessToken = AuthSessionStore.accessToken(this).trim()
        val refreshToken = AuthSessionStore.refreshToken(this).trim()
        if (accessToken.isBlank()) return
        withContext(Dispatchers.IO) {
            AuthApiClient.markAiCommunicateReplySelection(
                accessToken = accessToken,
                refreshToken = refreshToken,
                sessionId = sessionId,
                replyIndex = replyIndex,
                replyText = replyText,
            )
        }.newAccessToken?.takeIf { it.isNotBlank() }?.let { newAccess ->
            AuthSessionStore.updateTokens(this, newAccess, null)
        }
    }

    private suspend fun capturePreviewBehindDrawer(): String? {
        val bitmap = captureBitmapForAiCommunicateBehindDrawer("capturePreviewBehindDrawer") ?: return null
        return runCatching {
            AiCommunicateOcrCacheStore.cachePreviewBitmap(applicationContext, bitmap)
        }.onFailure {
            OemLog.w(TAG, "capturePreviewBehindDrawer cachePreviewBitmap failed message=${it.message}", it)
        }.getOrNull()
    }

    private suspend fun captureFastLlmBatchBehindDrawer(
        targetCount: Int,
        maxSwipeAttempts: Int,
        taskLogger: AiReplyTaskLogger? = null,
    ): FastLlmCaptureBatch? {
        if (targetCount <= 0) return null
        val savedPaths =
            withAiCommunicateDrawerHidden {
                withAiCommunicateCaptureTaskHidden {
                    val capturedPaths = mutableListOf<String>()
                    var currentAnalysis: FastCaptureFrameAnalysis? = null
                    val firstBitmap =
                        captureBitmapForAiCommunicateWithoutDrawerToggle(
                            "captureFastLlmBatchBehindDrawer#0"
                        ) ?: return@withAiCommunicateCaptureTaskHidden capturedPaths
                    try {
                        currentAnalysis = analyzeFastCaptureFrame(firstBitmap, "accepted#0")
                        val firstPath = saveBitmapAsFastLlmPreview(firstBitmap, 0) ?: return@withAiCommunicateCaptureTaskHidden capturedPaths
                        capturedPaths.add(firstPath)
                        taskLogger?.info(
                            phase = "capture",
                            event = "screenshot_captured",
                            title = "首张截图完成",
                            detail = "captureCount=${capturedPaths.size}",
                            data = JSONObject().put("captureCount", capturedPaths.size),
                        )
                    } finally {
                        firstBitmap.recycle()
                    }
                    var acceptedCount = capturedPaths.size
                    var consecutiveVerificationFailures = 0
                    var swipeAttempts = 0
                    while (acceptedCount < targetCount) {
                        val baselineAnalysis = currentAnalysis ?: break
                        if (swipeAttempts >= maxSwipeAttempts) {
                            break
                        }
                        val swipeProfile = buildFastCaptureSwipeProfile(consecutiveVerificationFailures)
                        taskLogger?.info(
                            phase = "capture",
                            event = "swipe_started",
                            title = "开始滑动历史消息",
                            detail = "attempt=${swipeAttempts + 1}",
                            data = JSONObject().put("attempt", swipeAttempts + 1),
                        )
                        val swipeSuccess = performAutomationSwipeUpWithoutDrawerToggle(swipeProfile)
                        swipeAttempts += 1
                        if (!swipeSuccess) {
                            taskLogger?.warn(
                                phase = "capture",
                                event = "swipe_finished",
                                title = "滑动失败",
                                detail = "attempt=$swipeAttempts",
                                data = JSONObject().put("attempt", swipeAttempts).put("moved", false),
                            )
                            break
                        }
                        OemLog.d(
                            TAG,
                            "captureFastLlmBatchBehindDrawer swipe dispatched acceptedCount=$acceptedCount swipeAttempts=$swipeAttempts verificationFailures=$consecutiveVerificationFailures profile=${swipeProfile.upwardStartOffsetRatio}->${swipeProfile.downwardEndOffsetRatio}"
                        )
                        delay(swipeProfile.settleDelayMs)
                        val verificationBitmap =
                            captureBitmapForAiCommunicateWithoutDrawerToggle(
                                "captureFastLlmBatchBehindDrawer#verify$acceptedCount"
                            ) ?: break
                        try {
                            val verificationAnalysis =
                                analyzeFastCaptureFrame(
                                    verificationBitmap,
                                    "verify#$acceptedCount"
                                )
                            val movement =
                                evaluateFastCaptureMovement(
                                    previous = baselineAnalysis,
                                    current = verificationAnalysis,
                                )
                            OemLog.d(
                                TAG,
                                "captureFastLlmBatchBehindDrawer swipe verification moved=${movement.moved} shiftRows=${movement.estimatedShiftRows} fingerprintDistance=${movement.fingerprintDistance} zeroScore=${movement.zeroOffsetScore} bestScore=${movement.bestScore}"
                            )
                            if (!movement.moved) {
                                consecutiveVerificationFailures += 1
                                taskLogger?.warn(
                                    phase = "capture",
                                    event = "swipe_finished",
                                    title = "滑动后内容无变化",
                                    detail = "attempt=$swipeAttempts verificationFailures=$consecutiveVerificationFailures",
                                    data =
                                        JSONObject()
                                            .put("attempt", swipeAttempts)
                                            .put("moved", false)
                                            .put("verificationFailures", consecutiveVerificationFailures),
                                )
                                OemLog.d(
                                    TAG,
                                    "captureFastLlmBatchBehindDrawer verification failed acceptedCount=$acceptedCount verificationFailures=$consecutiveVerificationFailures"
                                )
                                if (acceptedCount >= AI_FAST_CAPTURE_EARLY_STOP_ENOUGH_COUNT) {
                                    OemLog.d(
                                        TAG,
                                        "captureFastLlmBatchBehindDrawer reached top after enough captures acceptedCount=$acceptedCount"
                                    )
                                    break
                                }
                                if (consecutiveVerificationFailures >= 2) {
                                    break
                                }
                                continue
                            }
                            val verifiedPath = saveBitmapAsFastLlmPreview(verificationBitmap, acceptedCount) ?: break
                            consecutiveVerificationFailures = 0
                            capturedPaths.add(verifiedPath)
                            taskLogger?.info(
                                phase = "capture",
                                event = "swipe_finished",
                                title = "滑动成功并接纳新截图",
                                detail = "attempt=$swipeAttempts captureCount=${capturedPaths.size}",
                                data =
                                    JSONObject()
                                        .put("attempt", swipeAttempts)
                                        .put("moved", true)
                                        .put("captureCount", capturedPaths.size),
                            )
                            taskLogger?.info(
                                phase = "capture",
                                event = "screenshot_captured",
                                title = "新增截图完成",
                                detail = "captureCount=${capturedPaths.size}",
                                data = JSONObject().put("captureCount", capturedPaths.size),
                            )
                            currentAnalysis = verificationAnalysis
                            acceptedCount = capturedPaths.size
                        } finally {
                            verificationBitmap.recycle()
                        }
                    }
                    capturedPaths
                }
        }
        if (savedPaths.isEmpty()) return null
        val actualCaptureCount = savedPaths.size
        val successfulSwipeCount = (actualCaptureCount - 1).coerceAtLeast(0)
        val ocrSummary =
            inspectAcceptedFastCaptureContent(
                previewPaths = savedPaths,
                enableNoHistoryCheck = successfulSwipeCount >= AI_FAST_CAPTURE_NO_HISTORY_MIN_SUCCESSFUL_SWIPES,
            )
        OemLog.d(
            TAG,
            "captureFastLlmBatchBehindDrawer history inspection actualCaptureCount=$actualCaptureCount successfulSwipeCount=$successfulSwipeCount hasChatContent=${ocrSummary.hasChatContent} signaturesStable=${ocrSummary.signaturesStable}"
        )
        taskLogger?.setSummaryNumber("acceptedCaptureCount", actualCaptureCount)
        taskLogger?.setSummaryNumber("swipeAttempts", successfulSwipeCount)
        taskLogger?.info(
            phase = "capture",
            event = "capture_batch_finished",
            title = "多屏截图完成",
            detail = "captureCount=$actualCaptureCount successfulSwipeCount=$successfulSwipeCount",
            data =
                JSONObject()
                    .put("captureCount", actualCaptureCount)
                    .put("successfulSwipeCount", successfulSwipeCount),
        )
        return FastLlmCaptureBatch(
            primaryPreviewPath = savedPaths.firstOrNull(),
            orderedPreviewPaths = savedPaths.asReversed(),
            historyState =
                resolveFastLlmHistoryState(
                    actualCaptureCount = actualCaptureCount,
                    ocrSummary = ocrSummary,
                    successfulSwipeCount = successfulSwipeCount,
                ),
            actualCaptureCount = actualCaptureCount,
            ocrSourceText = ocrSummary.latestNonBlankText,
        )
    }

    private suspend fun performAutomationSwipeUpBehindDrawer(): Boolean {
        return withAiCommunicateDrawerHidden {
            performAutomationSwipeUpWithoutDrawerToggle(buildFastCaptureSwipeProfile(0))
        }
    }

    private fun buildFastCaptureSwipeProfile(duplicateCount: Int): FastCaptureSwipeProfile {
        return if (duplicateCount > 0) {
            FastCaptureSwipeProfile(
                upwardStartOffsetRatio = 0.30f,
                downwardEndOffsetRatio = 0.30f,
                durationMs = 920,
                settleDelayMs = 920L,
            )
        } else {
            FastCaptureSwipeProfile(
                upwardStartOffsetRatio = 0.22f,
                downwardEndOffsetRatio = 0.22f,
                durationMs = 680,
                settleDelayMs = 720L,
            )
        }
    }

    private suspend fun performAutomationSwipeUpWithoutDrawerToggle(
        profile: FastCaptureSwipeProfile,
    ): Boolean {
        val dm = resources.displayMetrics
        val viewport =
            buildFastCaptureScrollableViewport(
                width = dm.widthPixels,
                height = dm.heightPixels,
            )
        val x = viewport.centerX
        val centerY = viewport.centerY
        val startY =
            (centerY - viewport.height * profile.upwardStartOffsetRatio)
                .roundToInt()
                .coerceIn(viewport.top, viewport.bottom)
        val endY =
            (centerY + viewport.height * profile.downwardEndOffsetRatio)
                .roundToInt()
                .coerceIn(viewport.top, viewport.bottom)
        OemLog.d(
            TAG,
            "performAutomationSwipeUpWithoutDrawerToggle viewport=[${viewport.left},${viewport.top},${viewport.right},${viewport.bottom}] centerY=$centerY x=$x startY=$startY endY=$endY durationMs=${profile.durationMs} direction=downward_drag"
        )
        val swipeMode = runCatching { OemEnterpriseConfig.resolveSwipeDispatchMode(applicationContext) }
            .getOrDefault(OemSwipeDispatchMode.DUAL_WRITE)
        if (swipeMode != OemSwipeDispatchMode.SDK_ONLY) {
            val request = OemSwipeRequest(
                requestId = UUID.randomUUID().toString(),
                scene = "AI_FAST_GENERATE",
                direction = "DOWNWARD_DRAG",
                startX = x,
                startY = startY,
                endX = x,
                endY = endY,
                screenWidth = dm.widthPixels,
                screenHeight = dm.heightPixels,
                durationMs = profile.durationMs,
                timestamp = System.currentTimeMillis(),
            )
            dispatchSwipeToHostAndAwait(request)?.let { handled ->
                OemLog.d(TAG, "performAutomationSwipeUpWithoutDrawerToggle handled by host, success=$handled")
                return handled
            }
            OemLog.d(TAG, "performAutomationSwipeUpWithoutDrawerToggle host channel unavailable, mode=$swipeMode")
            if (swipeMode == OemSwipeDispatchMode.HOST_ONLY) return false
        }
        return withContext(Dispatchers.IO) {
            OemAddons.automation
                ?.executeAutomationShellCommand("input swipe $x $startY $x $endY ${profile.durationMs}")
                ?.success
                ?: false
        }
    }

    private suspend fun dispatchSwipeToHostAndAwait(request: OemSwipeRequest): Boolean? {
        if (!OemImeSdk.isSwipeHostDelegateRegistered()) return null
        val swipeHostResultTimeoutMs = OemImeSdk.getSwipeHostResultTimeoutMillis()
        OemLog.d(TAG, "dispatchSwipeToHostAndAwait waiting host result timeoutMs=$swipeHostResultTimeoutMs")
        return withTimeoutOrNull(swipeHostResultTimeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val accepted = OemImeSdk.dispatchSwipeToHost(request) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result.success)
                    }
                }
                if (!accepted && continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    private suspend fun <T> withAiCommunicateDrawerHidden(block: suspend () -> T): T {
        return withContext(Dispatchers.Main) {
            val shouldHideByAlpha = !aiCommunicateDrawerRetractedForCapture
            val previousAlpha = drawerHost.alpha
            val previousFocusable = materialHubWindowFocusable
            val previousTouchable = isMaterialHubWindowTouchable()
            val previousClickable = drawerHost.isClickable
            hideRealKeyboard()
            if (previousFocusable) {
                setMaterialHubWindowFocusable(false)
            }
            if (previousTouchable) {
                setMaterialHubWindowTouchable(false)
            }
            if (::aiCommunicateController.isInitialized) {
                aiCommunicateController.pauseAnimations()
            }
            if (shouldHideByAlpha) {
                drawerHost.alpha = 0f
            }
            drawerHost.isClickable = false
            OemLog.d(
                TAG,
                "withAiCommunicateDrawerHidden passthrough enabled focusable=$previousFocusable touchable=$previousTouchable alphaHidden=$shouldHideByAlpha retracted=$aiCommunicateDrawerRetractedForCapture"
            )
            try {
                delay(180L)
                block()
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (shouldHideByAlpha) {
                        drawerHost.alpha = previousAlpha
                    }
                    drawerHost.isClickable = previousClickable
                    ensureAiCommunicateDrawerVisibleAfterCapture()
                    if (::aiCommunicateController.isInitialized) {
                        aiCommunicateController.resumeAnimations()
                    }
                    setMaterialHubWindowTouchable(previousTouchable)
                    setMaterialHubWindowFocusable(previousFocusable)
                    OemLog.d(
                        TAG,
                        "withAiCommunicateDrawerHidden passthrough restored focusable=$previousFocusable touchable=$previousTouchable"
                    )
                }
            }
        }
    }

    private suspend fun <T> withAiCommunicateCaptureTaskHidden(block: suspend () -> T): T {
        return withContext(Dispatchers.Main.immediate) {
            var movedToBack = false
            val localNeonWasVisible = aiCommunicateNeonActive && ::aiCommunicateNeonOverlay.isInitialized &&
                aiCommunicateNeonOverlay.visibility == View.VISIBLE
            try {
                // Hide before the task transition so the border cannot slide sideways with the
                // Activity. The independent overlay, when available, stays fixed on the display.
                if (localNeonWasVisible) {
                    aiCommunicateNeonOverlay.visibility = View.INVISIBLE
                    awaitAiCommunicateNeonVisibilityFrame()
                }
                showAiCommunicateBackgroundNeonOverlayAndAwaitFirstFrame()
                // A separate task has an ActivityRecordInputSink on modern Android. Window flags
                // alone cannot pass injected gestures to the chat task below it.
                movedToBack = moveTaskToBack(true)
                if (!movedToBack) {
                    throw AiCommunicateReplyService.UserVisibleFailure(
                        "无法临时隐藏截图面板，请重试",
                        OemAiReplyErrorCode.CAPTURE_FAILED,
                    )
                }
                delay(450L)
                block()
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    hideAiCommunicateBackgroundNeonOverlay()
                    if (movedToBack && !isFinishing && !isDestroyed) {
                        returnToAiCommunicateAfterCapture()
                    }
                    if (localNeonWasVisible && aiCommunicateNeonActive && !isFinishing && !isDestroyed) {
                        aiCommunicateNeonOverlay.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private suspend fun returnToAiCommunicateAfterCapture() {
        val token = UUID.randomUUID().toString()
        val returned = CompletableDeferred<Unit>()
        aiCaptureReturnToken = token
        aiCaptureReturnSignal = returned
        try {
            val returnIntent = Intent(intent).apply {
                setClass(this@MaterialHubActivity, MaterialHubActivity::class.java)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtra(EXTRA_LAUNCH_TOKEN, MaterialHubLaunchAuthorizer.issue())
                putExtra(EXTRA_CAPTURE_RETURN_TOKEN, token)
            }
            // Do not use AppTask.moveToFront(): ColorOS routes it through startActivityFromRecents
            // and can swap root tasks, exposing Launcher and destroying the chat surface.
            startActivity(returnIntent)
            overridePendingTransition(0, 0)
            val acknowledged = withTimeoutOrNull(1_000L) { returned.await(); true } == true
            OemLog.d(TAG, "returnToAiCommunicateAfterCapture reordered=true acknowledged=$acknowledged taskId=$taskId")
        } finally {
            // Keep the marker if delivery is delayed beyond the wait: a late onNewIntent must
            // still acknowledge the return instead of replaying the entry and resetting AI state.
            if (returned.isCompleted) aiCaptureReturnToken = null
            aiCaptureReturnSignal = null
        }
    }

    private suspend fun saveBitmapAsFastLlmPreview(bitmap: Bitmap, index: Int): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val directory = File(filesDir, "ai-communicate-multi").apply { mkdirs() }
                val file = File(directory, "fast_capture_${SystemClock.elapsedRealtime()}_$index.jpg")
                file.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                }
                file.absolutePath
            }.getOrNull()
        }
    }

    private fun computeBitmapDifferenceHash(bitmap: Bitmap): Long {
        val cropped = cropBitmapForFastCaptureFingerprint(bitmap)
        val scaled = Bitmap.createScaledBitmap(cropped, 9, 8, true)
        if (cropped !== bitmap) {
            cropped.recycle()
        }
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = scaled.getPixel(x, y)
                val right = scaled.getPixel(x + 1, y)
                val leftLuma = (Color.red(left) * 30 + Color.green(left) * 59 + Color.blue(left) * 11) / 100
                val rightLuma = (Color.red(right) * 30 + Color.green(right) * 59 + Color.blue(right) * 11) / 100
                if (leftLuma > rightLuma) {
                    hash = hash or (1L shl (y * 8 + x))
                }
            }
        }
        scaled.recycle()
        return hash
    }

    private suspend fun analyzeFastCaptureFrame(
        bitmap: Bitmap,
        traceLabel: String,
    ): FastCaptureFrameAnalysis {
        return withContext(Dispatchers.Default) {
            OemLog.d(
                TAG,
                "analyzeFastCaptureFrame trace=$traceLabel fingerprintReady=true profileReady=true"
            )
            FastCaptureFrameAnalysis(
                fingerprint = computeBitmapDifferenceHash(bitmap),
                verticalProfile = buildFastCaptureVerticalProfile(bitmap),
            )
        }
    }

    private fun buildFastCaptureVerticalProfile(bitmap: Bitmap): IntArray {
        val cropped = cropBitmapForFastCaptureFingerprint(bitmap)
        val scaled =
            Bitmap.createScaledBitmap(
                cropped,
                AI_FAST_CAPTURE_VERIFY_PROFILE_WIDTH,
                AI_FAST_CAPTURE_VERIFY_PROFILE_HEIGHT,
                true,
            )
        if (cropped !== bitmap) {
            cropped.recycle()
        }
        val profile = IntArray(AI_FAST_CAPTURE_VERIFY_PROFILE_HEIGHT)
        for (y in 0 until AI_FAST_CAPTURE_VERIFY_PROFILE_HEIGHT) {
            var sum = 0
            for (x in 0 until AI_FAST_CAPTURE_VERIFY_PROFILE_WIDTH) {
                val pixel = scaled.getPixel(x, y)
                sum += (Color.red(pixel) * 30 + Color.green(pixel) * 59 + Color.blue(pixel) * 11) / 100
            }
            profile[y] = sum / AI_FAST_CAPTURE_VERIFY_PROFILE_WIDTH
        }
        scaled.recycle()
        return profile
    }

    private fun evaluateFastCaptureMovement(
        previous: FastCaptureFrameAnalysis,
        current: FastCaptureFrameAnalysis,
    ): FastCaptureMovementCheck {
        val zeroOffsetScore =
            computeFastCaptureProfileScore(
                previous = previous.verticalProfile,
                current = current.verticalProfile,
                offset = 0,
            )
        var bestOffset = 0
        var bestScore = zeroOffsetScore
        for (offset in -AI_FAST_CAPTURE_VERIFY_MAX_SHIFT_ROWS..AI_FAST_CAPTURE_VERIFY_MAX_SHIFT_ROWS) {
            if (offset == 0) continue
            val score =
                computeFastCaptureProfileScore(
                    previous = previous.verticalProfile,
                    current = current.verticalProfile,
                    offset = offset,
                )
            if (score < bestScore) {
                bestScore = score
                bestOffset = offset
            }
        }
        val fingerprintDistance = hammingDistance(previous.fingerprint, current.fingerprint)
        val estimatedShiftRows = abs(bestOffset)
        val scoreImprovedEnough =
            zeroOffsetScore > 0L &&
                bestScore * 100L <= zeroOffsetScore * AI_FAST_CAPTURE_VERIFY_SCORE_IMPROVEMENT_PERCENT
        val moved =
            estimatedShiftRows >= AI_FAST_CAPTURE_VERIFY_MIN_SHIFT_ROWS &&
                (
                    scoreImprovedEnough ||
                        fingerprintDistance >= AI_FAST_CAPTURE_VERIFY_MIN_FINGERPRINT_DISTANCE
                )
        return FastCaptureMovementCheck(
            moved = moved,
            estimatedShiftRows = estimatedShiftRows,
            fingerprintDistance = fingerprintDistance,
            zeroOffsetScore = zeroOffsetScore,
            bestScore = bestScore,
        )
    }

    private fun computeFastCaptureProfileScore(
        previous: IntArray,
        current: IntArray,
        offset: Int,
    ): Long {
        var score = 0L
        var samples = 0
        for (index in previous.indices) {
            val shiftedIndex = index + offset
            if (shiftedIndex !in current.indices) continue
            score += abs(previous[index] - current[shiftedIndex]).toLong()
            samples += 1
        }
        return if (samples == 0) Long.MAX_VALUE else score / samples
    }

    private fun normalizeFastCaptureOcrSignature(text: String): String {
        return text
            .lowercase()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[`~!@#$%^&*()_+=\\-\\[\\]{};:'\",.<>/?\\\\|]"), "")
    }

    private fun cropBitmapForFastCaptureFingerprint(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap
        val viewport = buildFastCaptureScrollableViewport(width = width, height = height)
        return Bitmap.createBitmap(bitmap, viewport.left, viewport.top, viewport.width, viewport.height)
    }

    private fun buildFastCaptureScrollableViewport(
        width: Int,
        height: Int,
    ): FastCaptureScrollableViewport {
        val left = (width * AI_FAST_CAPTURE_VIEWPORT_LEFT_RATIO).roundToInt().coerceIn(0, width - 1)
        val top = (height * AI_FAST_CAPTURE_VIEWPORT_TOP_RATIO).roundToInt().coerceIn(0, height - 1)
        val right = (width * AI_FAST_CAPTURE_VIEWPORT_RIGHT_RATIO).roundToInt().coerceIn(left + 1, width)
        val bottom = (height * AI_FAST_CAPTURE_VIEWPORT_BOTTOM_RATIO).roundToInt().coerceIn(top + 1, height)
        return FastCaptureScrollableViewport(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        )
    }

    private fun hammingDistance(a: Long, b: Long): Int {
        return java.lang.Long.bitCount(a xor b)
    }

    private fun resolveHistoryStateFromOcrText(
        ocrText: String,
        previewPath: String?,
    ): AiCommunicateReplyService.HistoryState {
        val lines =
            ocrText.lineSequence()
                .map(String::trim)
                .filter { it.isNotBlank() }
                .toList()
        if (lines.isEmpty()) {
            return if (previewPath.isNullOrBlank()) {
                AiCommunicateReplyService.HistoryState.NO_HISTORY
            } else {
                AiCommunicateReplyService.HistoryState.NO_HISTORY
            }
        }
        return if (lines.size < 6) {
            AiCommunicateReplyService.HistoryState.LIMITED_HISTORY
        } else {
            AiCommunicateReplyService.HistoryState.NORMAL_HISTORY
        }
    }

    private suspend fun inspectAcceptedFastCaptureContent(
        previewPaths: List<String>,
        enableNoHistoryCheck: Boolean,
    ): FastCaptureOcrSummary {
        if (!enableNoHistoryCheck || previewPaths.isEmpty()) {
            return FastCaptureOcrSummary(
                hasChatContent = true,
                latestNonBlankText = "",
                newestSignature = "",
                oldestSignature = "",
                signaturesStable = false,
            )
        }
        return withContext(Dispatchers.Default) {
            var latestNonBlankText = ""
            var newestSignature = ""
            var oldestSignature = ""
            val candidateIndices =
                listOf(0, previewPaths.lastIndex)
                    .distinct()
                    .filter { it in previewPaths.indices }
            for (index in candidateIndices) {
                val path = previewPaths[index]
                val bitmap =
                    withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(path)
                    } ?: continue
                try {
                    val snapshot =
                        AiCommunicateOcrCacheStore.captureFromBitmap(
                            context = applicationContext,
                            bitmap = bitmap,
                            force = false,
                        )
                    val text = snapshot?.text?.trim().orEmpty()
                    val signature = normalizeFastCaptureOcrSignature(text)
                    if (index == 0) {
                        newestSignature = signature
                    }
                    if (index == previewPaths.lastIndex) {
                        oldestSignature = signature
                    }
                    OemLog.d(
                        TAG,
                        "inspectAcceptedFastCaptureContent index=$index textLength=${text.length} hasChatContent=${signature.isNotBlank()}"
                    )
                    if (signature.isNotBlank() && latestNonBlankText.isBlank()) {
                        latestNonBlankText = text
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            val hasChatContent = newestSignature.isNotBlank() || oldestSignature.isNotBlank()
            val signaturesStable =
                newestSignature.isNotBlank() &&
                    oldestSignature.isNotBlank() &&
                    newestSignature == oldestSignature
            FastCaptureOcrSummary(
                hasChatContent = hasChatContent,
                latestNonBlankText = latestNonBlankText,
                newestSignature = newestSignature,
                oldestSignature = oldestSignature,
                signaturesStable = signaturesStable,
            )
        }
    }

    private fun resolveFastLlmHistoryState(
        actualCaptureCount: Int,
        ocrSummary: FastCaptureOcrSummary,
        successfulSwipeCount: Int,
    ): AiCommunicateReplyService.HistoryState {
        if (actualCaptureCount <= 0) {
            return AiCommunicateReplyService.HistoryState.NO_HISTORY
        }
        if (
            successfulSwipeCount >= AI_FAST_CAPTURE_NO_HISTORY_MIN_SUCCESSFUL_SWIPES &&
            !ocrSummary.hasChatContent
        ) {
            return AiCommunicateReplyService.HistoryState.NO_HISTORY
        }
        if (
            successfulSwipeCount >= AI_FAST_CAPTURE_NO_HISTORY_MIN_SUCCESSFUL_SWIPES &&
            ocrSummary.signaturesStable &&
            ocrSummary.newestSignature.length <= AI_FAST_CAPTURE_NO_HISTORY_MAX_STABLE_SIGNATURE_LENGTH
        ) {
            return AiCommunicateReplyService.HistoryState.NO_HISTORY
        }
        return when {
            actualCaptureCount >= 3 -> AiCommunicateReplyService.HistoryState.NORMAL_HISTORY
            else -> AiCommunicateReplyService.HistoryState.LIMITED_HISTORY
        }
    }

    private fun encodePreviewImageAsDataUrl(path: String?): String? {
        val file = path?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        if (!file.exists()) return null
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val scaled =
            if (original.width <= 1280) {
                original
            } else {
                val ratio = 1280f / original.width.toFloat()
                Bitmap.createScaledBitmap(
                    original,
                    1280,
                    (original.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            }
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 72, output)
        if (scaled !== original) {
            scaled.recycle()
        }
        original.recycle()
        return "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun captureSnapshotBehindDrawer(force: Boolean): AiCommunicateOcrSnapshot? {
        val bitmap = captureBitmapForAiCommunicateBehindDrawer("captureSnapshotBehindDrawer") ?: return null
        return try {
            AiCommunicateOcrCacheStore.captureFromBitmap(
                context = applicationContext,
                bitmap = bitmap,
                force = force,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun captureBitmapForAiCommunicateBehindDrawer(traceLabel: String): Bitmap? {
        return withAiCommunicateDrawerHidden {
            captureBitmapForAiCommunicateWithoutDrawerToggle(traceLabel)
        }
    }

    private suspend fun captureBitmapForAiCommunicateWithoutDrawerToggle(traceLabel: String): Bitmap? {
        OemLog.d(TAG, "captureBitmapForAiCommunicateBehindDrawer trace=$traceLabel")
        return withAiCommunicateNeonSuppressed {
            AiCommunicateScreenCaptureManager.captureBitmap(applicationContext)
        }
    }

    private fun requestAiCommunicateProjectionPermission() {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager ?: run {
                projectionPermissionSuppressedForSession = true
                showAiReplyError(
                    mode = aiCommunicateSessionMode,
                    tone = aiCommunicateController.selectedTone,
                    errorCode = OemAiReplyErrorCode.CAPTURE_FAILED,
                    rawMessage = "当前系统暂不支持截屏授权，请进入权限设置页排查。",
                )
                return
            }
        if (projectionPermissionRequestInFlight) {
            OemLog.d(TAG, "requestAiCommunicateProjectionPermission ignored: already requesting")
            return
        }
        projectionPermissionSuppressedForSession = false
        projectionPermissionRequestInFlight = true
        runCatching {
            requestScreenCaptureLauncher.launch(buildScreenCaptureIntent(projectionManager))
        }.onFailure {
            projectionPermissionRequestInFlight = false
            projectionPermissionSuppressedForSession = true
            showAiReplyError(
                mode = aiCommunicateSessionMode,
                tone = aiCommunicateController.selectedTone,
                errorCode = OemAiReplyErrorCode.CAPTURE_FAILED,
                rawMessage = "当前无法拉起截屏授权，请稍后重试。",
            )
        }
    }

    private fun buildScreenCaptureIntent(projectionManager: MediaProjectionManager): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        } else {
            projectionManager.createScreenCaptureIntent()
        }
    }

    private fun AiCommunicatePanelController.Tone.toServiceTone(): AiCommunicateReplyService.Tone {
        return AiCommunicateReplyService.Tone(
            key = key,
            label = label,
            promptHint = promptHint,
        )
    }

    private fun resolveAiCommunicateToneOptions(): List<AiCommunicatePanelController.Tone> {
        val presets = OemEnterpriseConfig.resolveAiReplyTonePresets(applicationContext)
        return presets
            .mapIndexed { index, preset ->
                AiCommunicatePanelController.Tone(
                    key = "tone_$index",
                    label = preset.label,
                    promptHint = preset.promptHint,
                )
            }
    }

    private fun openDrawerOnce() {
        if (drawerOpened) return
        drawerOpened = true
        if (skipInitialDrawerAnimation) {
            drawerHost.translationY = 0f
            return
        }
        val initialTranslation =
            drawerHost.height.takeIf { it > 0 }?.toFloat()
                ?: drawerHost.layoutParams.height.takeIf { it > 0 }?.toFloat()
                ?: (root.height * DRAWER_HEIGHT_RATIO)
        drawerHost.translationY = initialTranslation
        animateDrawerToOpen()
    }

    fun closeMaterialHub(afterClosed: (() -> Unit)? = null) {
        if (closeRunning) return
        closeRunning = true
        if (materialHubWindowFocusable) {
            hideRealKeyboard()
        }
        drawerHost.animate().cancel()
        drawerHost.animate()
            .translationY(drawerHost.height.toFloat())
            .setDuration(DRAWER_CLOSE_DURATION_MS)
            .setInterpolator(drawerInterpolator)
            .withEndAction {
                finishAndRemoveTask()
                overridePendingTransition(0, 0)
                afterClosed?.let {
                    Handler(Looper.getMainLooper()).postDelayed(it, CLOSE_CALLBACK_DELAY_MS)
                }
            }
            .start()
    }

    private fun animateDrawerToOpen() {
        drawerHost.animate().cancel()
        drawerHost.animate()
            .translationY(0f)
            .setDuration(DRAWER_OPEN_DURATION_MS)
            .setInterpolator(drawerInterpolator)
            .start()
    }

    private fun showRealKeyboard() {
        WindowInsetsControllerCompat(window, root).show(WindowInsetsCompat.Type.ime())
    }

    private fun hideRealKeyboard() {
        WindowInsetsControllerCompat(window, root).hide(WindowInsetsCompat.Type.ime())
    }

    fun setFacadeKeyboardVisible(visible: Boolean) {
        if (visible) {
            setMaterialHubWindowFocusable(true)
            showRealKeyboard()
        } else {
            hideRealKeyboard()
            setMaterialHubWindowFocusable(true)
        }
    }

    private fun authorizeLaunchIntent(intent: Intent?): Boolean {
        val launchToken = intent?.getStringExtra(EXTRA_LAUNCH_TOKEN)
        val authorized = MaterialHubLaunchAuthorizer.consume(launchToken)
        if (!authorized) {
            OemLog.w(TAG, "Blocked material hub launch without active ime token")
        }
        return authorized
    }

    private fun setMaterialHubWindowFocusable(focusable: Boolean) {
        materialHubWindowFocusable = focusable
        if (focusable) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
        }
    }

    private fun isMaterialHubWindowTouchable(): Boolean {
        return window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0
    }

    private fun setMaterialHubWindowTouchable(touchable: Boolean) {
        if (touchable) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}

private class AiCommunicateNeonBorderView(
    context: Context,
) : View(context) {
    private val borderRect = RectF()
    private val shaderMatrix = Matrix()
    private var shader: SweepGradient? = null
    private var strokeWidthPx = 0f
    private var cornerRadiusPx = 0f

    private val glowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val corePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val colors =
        intArrayOf(
            Color.parseColor("#FFFF3B30"),
            Color.parseColor("#FFFF9500"),
            Color.parseColor("#FFFFD60A"),
            Color.parseColor("#FF30D158"),
            Color.parseColor("#FF32D7FF"),
            Color.parseColor("#FF0A84FF"),
            Color.parseColor("#FF5E5CE6"),
            Color.parseColor("#FFBF5AF2"),
            Color.parseColor("#FFFF2D55"),
            Color.parseColor("#FFFF3B30"),
        )
    private val colorPositions = floatArrayOf(0f, 0.12f, 0.24f, 0.36f, 0.5f, 0.64f, 0.76f, 0.88f, 0.96f, 1f)
    private var gradientRotation: Float = 0f

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        alpha = 1f
    }

    fun setGradientRotation(value: Float) {
        gradientRotation = value
        updateShaderMatrix()
        invalidate()
    }

    fun getGradientRotation(): Float = gradientRotation

    fun setCornerRadiusPx(value: Float) {
        if (cornerRadiusPx == value) return
        cornerRadiusPx = value
        invalidate()
    }

    fun setBorderThickness(value: Float) {
        if (strokeWidthPx == value) return
        strokeWidthPx = value
        glowPaint.strokeWidth = value * 1.35f
        corePaint.strokeWidth = value
        glowPaint.maskFilter = BlurMaskFilter(value * 2.2f, BlurMaskFilter.Blur.NORMAL)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        shader = SweepGradient(w / 2f, h / 2f, colors, colorPositions)
        glowPaint.shader = shader
        corePaint.shader = shader
        updateShaderMatrix()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokeWidthPx * 0.5f
        borderRect.set(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(borderRect, cornerRadiusPx, cornerRadiusPx, glowPaint)
        canvas.drawRoundRect(borderRect, cornerRadiusPx, cornerRadiusPx, corePaint)
    }

    private fun updateShaderMatrix() {
        val localShader = shader ?: return
        shaderMatrix.reset()
        shaderMatrix.setRotate(gradientRotation, width / 2f, height / 2f)
        localShader.setLocalMatrix(shaderMatrix)
        glowPaint.shader = localShader
        corePaint.shader = localShader
    }
}
