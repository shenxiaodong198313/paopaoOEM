package com.paopaoime.aikeyboard.ime.service

import android.Manifest
import android.app.Dialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.os.SystemClock
import android.graphics.Rect
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.paopaoime.aikeyboard.materialhub.OemMaterialTaxonomyService
import com.shenji.keyboard.agent.AutoGLMManager
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.auth.UserAvatarHelper
import com.paopaoime.aikeyboard.ime.bridge.ImeTextBridge
import com.paopaoime.aikeyboard.ime.database.DataBaseKT
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommand
import com.paopaoime.aikeyboard.sdk.core.OemAddons
import com.paopaoime.aikeyboard.sdk.core.OemDiagnosticEvent
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk
import com.paopaoime.aikeyboard.sdk.core.drawer.OemCustomDrawerRegistry
import com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerHostActions
import com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerKit
import com.paopaoime.aikeyboard.sdk.core.OemLicenseRefreshApplyMode
import com.paopaoime.aikeyboard.sdk.core.OemLog
import com.paopaoime.aikeyboard.sdk.core.OemLogLevel
import com.paopaoime.aikeyboard.sdk.core.OemMaterialHubLaunchOptions
import com.paopaoime.aikeyboard.ime.theme.ThemeMarketplaceBridge
import com.paopaoime.aikeyboard.ime.ui.activity.FilePickBridge
import com.paopaoime.aikeyboard.ime.ui.activity.FilePickerActivity
import com.paopaoime.aikeyboard.ime.ui.activity.ImagePermissionActivity
import com.paopaoime.aikeyboard.ime.ui.activity.ImagePermissionBridge
import com.paopaoime.aikeyboard.ime.ui.activity.OemMainActivity
import com.paopaoime.aikeyboard.qa.InputQaCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import java.util.HashSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import org.fcitx.fcitx5.android.core.SubtypeManager
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.KeyboardAdjustmentHostBridge
import org.fcitx.fcitx5.android.input.favorite.FavoriteBridge
import org.fcitx.fcitx5.android.input.favorite.FavoriteComposerSession
import org.fcitx.fcitx5.android.input.favorite.FavoriteHostShareDispatchResult
import org.fcitx.fcitx5.android.input.favorite.FavoritePart
import org.fcitx.fcitx5.android.input.favorite.FavoritePickerType
import org.fcitx.fcitx5.android.input.favorite.FavoriteStorage
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubFavoriteController
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubTaxonomyItem
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubTaxonomyMutationResult
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubTaxonomySnapshot
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubTaxonomyTarget
import org.fcitx.fcitx5.android.input.bar.ToolbarActionHostBridge
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubHostBridge
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubLaunchRequest
import org.fcitx.fcitx5.android.input.status.CustomDrawerEntry
import org.fcitx.fcitx5.android.input.status.CustomDrawerHostBridge
import org.fcitx.fcitx5.android.input.status.StatusAreaHostBridge
import org.fcitx.fcitx5.android.input.t9.T9SidebarState
import splitties.dimensions.dp

class AiInputMethodService : FcitxInputMethodService() {
    private val localVoice by lazy { org.fcitx.fcitx5.android.input.voice.OemVoiceBridge.controller(this) { showLocalVoiceSurface(it) } }
    override fun launchLocalVoiceInput(): Boolean = (localVoice?.start() ?: false)
    override fun switchToPreferredVoiceInput(): Boolean = (localVoice?.start() ?: false)
    override fun onWindowHidden() { localVoice?.cancel(); super.onWindowHidden() }
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        localVoice?.selection(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }
    override fun onTrimMemory(level: Int) {
        if (level in setOf(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE, android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)) {
            localVoice?.cancel(); org.fcitx.fcitx5.android.input.voice.OemVoiceBridge.release("memory-$level")
        }
        super.onTrimMemory(level)
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        localVoice?.editorChanged(attribute.initialSelStart, attribute.initialSelEnd)
        super.onStartInput(attribute, restarting)
    }
    override fun onFinishInput() { localVoice?.cancel(); super.onFinishInput() }

    private var lastIdlefishTextCommitMode: String = "none"
    private var attachedMaterialHubDialog: Dialog? = null
    private var attachedMaterialHubController: MaterialHubFavoriteController? = null
    private var attachedMaterialHubUserAvatar: Bitmap? = null
    private var attachedMaterialHubDefaultAvatar: Bitmap? = null

    companion object {
        private const val TAG = "AiInputMethodService"
        private const val BOOTSTRAP_PREFS = "paopao_ime_bootstrap"
        private const val KEY_PREFERRED_CHINESE_INITIALIZED = "preferred_chinese_initialized"
        private const val DEFAULT_CHINESE_IME = "fcitx-pinyin"
        private const val DEFAULT_ENGLISH_IME = "keyboard-us"
        private const val MATERIAL_HUB_LAUNCH_DELAY_MS = 0L
        private const val AI_COMMUNICATE_LAUNCH_DELAY_MS = 0L
        private const val AI_COMMUNICATE_LAUNCH_WATCHDOG_MS = 2_000L
        private const val ENTRY_LICENSE_REFRESH_MIN_INTERVAL_MS = 2_000L
        private const val ATTACHED_MATERIAL_HUB_HEIGHT_RATIO = 0.8f
        private const val TEXT_SEND_VERIFICATION_SAMPLE_CHARS = 64
        private const val TEXT_SEND_VERIFICATION_FULL_MATCH_CHARS = 128
        private const val TEXT_SEND_VERIFICATION_WINDOW_PADDING = 64
        private const val TEXT_SEND_VERIFICATION_MAX_WINDOW = 2_048

        private fun String.toOemLogLevel(): OemLogLevel {
            return when (uppercase()) {
                "VERBOSE" -> OemLogLevel.VERBOSE
                "INFO" -> OemLogLevel.INFO
                "WARN" -> OemLogLevel.WARN
                "ERROR" -> OemLogLevel.ERROR
                else -> OemLogLevel.DEBUG
            }
        }

        // #region debug-point B:document-preview-back-reporter
        private fun reportDocumentPreviewBackDebug(hypothesisId: String, location: String, msg: String, data: String) = Unit
        // #endregion
        // #region debug-point C:media-preview-back-reporter
        private fun reportMediaPreviewBackDebug(hypothesisId: String, location: String, msg: String, data: String) = Unit
        // #endregion
        // #region debug-point A:host-text-send-reporter
        private fun reportHostTextSendDebug(hypothesisId: String, location: String, msg: String, data: String) = Unit
        // #endregion
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inputViewStartCounter = AtomicInteger(0)
    private var pendingDirectAttachmentHostApp: FavoriteHostApp = FavoriteHostApp.GENERIC
    private var numberSidebarPageIndex = 0
    override fun isFavoritePanelEnabled(): Boolean = true

    override fun isAttachedFavoritePanelShowing(): Boolean = false

    override fun onCreate() {
        super.onCreate()
        ensureExpandedToolbarByDefault()
        preferChineseInputMethodOnFirstSetup()
        OemLog.d(TAG, "onCreate: AiInputMethodService started")
        ImeTextBridge.bind { text ->
            commitTextWithVerification(text)
        }
        ImeTextBridge.bindClipboardPanel {
            requestShowSelf(0)
            true
        }
        ImeTextBridge.bindPasteLatestClipboard {
            pasteLatestClipboardWithVerification()
        }
        ImeTextBridge.bindClearClipboard {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return@bindClearClipboard false
            return@bindClearClipboard try {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                true
            } catch (_: Throwable) {
                false
            }
        }
        ImeTextBridge.bindAgentSession { active ->
            if (active) {
                requestShowSelf(0)
            }
        }
        ImeTextBridge.bindUpdateSendButton { _ -> }
        ImeTextBridge.bindPerformEnter {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            true
        }
        ImeTextBridge.bindHideSelf {
            requestHideSelf(0)
        }
        ImeTextBridge.bindShowKeyboard {
            requestShowSelf(0)
            true
        }
        ImeTextBridge.bindBeginKeyboardAdjustment {
            requestShowSelf(0)
            beginKeyboardAdjustment()
        }
        ImeTextBridge.bindDismissKeyboardAdjustment {
            dismissKeyboardAdjustment()
        }
        KeyboardAdjustmentHostBridge.bindState { active ->
            ImeTextBridge.notifyKeyboardAdjustmentState(active)
        }
        ImeTextBridge.bindInputSnapshot {
            currentInputConnection?.let { snapshotInputState(it).toBridgeSnapshot() }
        }
        if (isFavoritePanelEnabled()) {
            FavoriteBridge.bindCommitText { text ->
                commitTextWithVerification(text)
            }
            FavoriteBridge.bindDirectAttachmentSend { fileName ->
                tryDirectSendFavoriteAttachment(fileName)
            }
            FavoriteBridge.bindHostShare { hostPackageName, parts ->
                shareFavoritePartsWithHostIntent(hostPackageName, parts)
            }
            FavoriteBridge.bindPerformTextSend { expectedText ->
                performFavoriteTextSend(expectedText)
            }
            FavoriteBridge.bindAwaitDirectAttachmentSent { previousInputViewStartCount, timeoutMs, automationEnabled ->
                waitForDirectAttachmentResume(previousInputViewStartCount, timeoutMs, automationEnabled)
            }
            FavoriteBridge.bindCurrentInputViewStartCount {
                currentInputViewStartCount()
            }
            FavoriteBridge.bindRequestEmbeddedPickerPermission { pickerType, onResult ->
                requestEmbeddedFavoritePickerPermission(pickerType, onResult)
            }
            FavoriteBridge.bindOpenAttachmentPreview { attachment ->
                openFavoriteAttachmentPreview(attachment)
            }
            FavoriteBridge.bindRequestShowKeyboard {
                requestShowSelf(0)
                true
            }
            FavoriteBridge.bindCurrentInputTargetPackage {
                currentInputEditorInfo?.packageName?.takeIf { currentInputConnection != null }
            }
            FavoriteBridge.bindActivateHostInput { packageName ->
                activateFavoriteHostInput(packageName)
            }
            FavoriteBridge.bindHostInputRestorePolicy { packageName ->
                FavoriteHostApp.fromPackageName(packageName).inputRestorePolicy
            }
            FavoriteBridge.bindDiagnosticEvent { event ->
                OemImeSdk.dispatchDiagnosticEvent(
                    OemDiagnosticEvent(
                        category = event.category,
                        name = event.name,
                        level = event.level.toOemLogLevel(),
                        message = event.message,
                        attributes = event.attributes,
                        timestampMs = event.timestampMs,
                    ),
                )
            }
        }
        StatusAreaHostBridge.bindLaunchSkinChannel {
            AuthSessionStore.showHostManagedAccountToast(this, "皮肤")
            false
        }
        StatusAreaHostBridge.bindLaunchInputMethodSettings {
            runCatching {
                requestHideSelf(0)
                Handler(Looper.getMainLooper()).post {
                    startActivity(
                        Intent(this, OemMainActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        }
                    )
                }
            }.isSuccess
        }
        MaterialHubHostBridge.bindLaunch { request ->
            handleMaterialHubLaunchRequest(request)
        }
        MaterialHubHostBridge.bindAvailability { target ->
            when (target) {
                MaterialHubLaunchRequest.Target.MATERIAL_HUB ->
                    OemAddons.hasMaterialHub && OemEnterpriseConfig.isMaterialLibraryEntryEnabled(this)
                MaterialHubLaunchRequest.Target.AI_REPLY ->
                    OemAddons.hasAiReply && OemEnterpriseConfig.isAiReplyEntryEnabled(this)
            }
        }
        bindDrawerKitHostBridges()
    }

    private fun bindDrawerKitHostBridges() {
        CustomDrawerHostBridge.bindList {
            OemImeSdk.customDrawers().map { spec ->
                CustomDrawerEntry(
                    id = spec.id,
                    title = spec.title,
                    icon = spec.icon ?: resolveCustomDrawerIcon(spec.iconRes),
                )
            }
        }
        CustomDrawerHostBridge.bindLaunch { drawerId -> launchCustomDrawer(drawerId) }
        OemCustomDrawerRegistry.onRegistryChanged = { CustomDrawerHostBridge.notifyRegistryChanged() }
        ToolbarActionHostBridge.bindLaunchCustomDrawer { drawerId -> launchCustomDrawer(drawerId) }
        InputMethodSettingsBridge.bindCustomToolbarEntriesProvider {
            OemImeSdk.customDrawers().map { spec ->
                InputMethodSettingsBridge.CustomToolbarEntry(
                    id = spec.id,
                    title = spec.title,
                    icon = spec.icon ?: resolveCustomDrawerIcon(spec.iconRes),
                )
            }
        }
        OemImeSdk.bindDrawerHostActions(object : OemDrawerHostActions {
            override fun openDrawer(drawerId: String) {
                launchDrawerById(drawerId)
            }

            override fun closeDrawer(drawerId: String) {
                OemAddons.materialHub?.closeCurrentDrawer()
            }
        })
    }

    private fun resolveCustomDrawerIcon(iconRes: Int): android.graphics.drawable.Drawable? {
        if (iconRes == 0) return null
        return runCatching { ContextCompat.getDrawable(this, iconRes) }.getOrNull()
    }

    private fun launchDrawerById(drawerId: String): Boolean {
        return when (drawerId) {
            OemDrawerKit.DRAWER_AI_REPLY -> launchAiCommunicatePanel()
            OemDrawerKit.DRAWER_MATERIAL_HUB -> launchMaterialHubActivity()
            else -> launchCustomDrawer(drawerId)
        }
    }

    private fun launchCustomDrawer(drawerId: String): Boolean {
        val spec = OemImeSdk.findCustomDrawer(drawerId)
        if (spec == null) {
            Toast.makeText(this, "自定义抽屉不可用", Toast.LENGTH_SHORT).show()
            return false
        }
        spec.entryHandler?.let { handler ->
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runCatching { handler.onEntryClick(this, drawerId) }
            } else {
                Handler(Looper.getMainLooper()).post {
                    runCatching { handler.onEntryClick(this, drawerId) }
                }
            }
            return true
        }
        val hostPackageName = currentInputEditorInfo?.packageName?.takeIf { it.isNotBlank() }
        if (currentInputConnection == null || hostPackageName == null) {
            OemLog.w(TAG, "Blocked custom drawer launch without active editor context")
            Toast.makeText(this, "请先在可输入的应用中打开键盘", Toast.LENGTH_SHORT).show()
            return false
        }
        val intent = OemAddons.materialHub?.createCustomDrawerLaunchIntent(this, drawerId)
        if (intent == null) {
            OemLog.w(TAG, "Blocked custom drawer launch without material hub addon")
            Toast.makeText(this, "自定义抽屉不可用", Toast.LENGTH_SHORT).show()
            return false
        }
        return runCatching {
            requestHideSelf(0)
            startActivity(
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        }.isSuccess
    }

    override fun onEvaluateInputViewShown(): Boolean {
        val info = currentInputEditorInfo ?: return false
        if ((info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_NULL) {
            return false
        }
        return superEvaluateInputViewShown()
    }

    override fun getRuntimeInputBlockState(): RuntimeInputBlockState? {
        return null
    }

    override fun onDestroy() {
        localVoice?.cancel()
        ImeTextBridge.unbindShowKeyboard()
        ImeTextBridge.unbindBeginKeyboardAdjustment()
        ImeTextBridge.unbindDismissKeyboardAdjustment()
        ImeTextBridge.unbindInputSnapshot()
        KeyboardAdjustmentHostBridge.unbindState()
        FavoriteBridge.unbindCommitText()
        FavoriteBridge.unbindDirectAttachmentSend()
        FavoriteBridge.unbindHostShare()
        FavoriteBridge.unbindPerformTextSend()
        FavoriteBridge.unbindAwaitDirectAttachmentSent()
        FavoriteBridge.unbindCurrentInputViewStartCount()
        FavoriteBridge.unbindRequestEmbeddedPickerPermission()
        FavoriteBridge.unbindOpenAttachmentPreview()
        FavoriteBridge.unbindRequestShowKeyboard()
        FavoriteBridge.unbindCurrentInputTargetPackage()
        FavoriteBridge.unbindActivateHostInput()
        FavoriteBridge.unbindHostInputRestorePolicy()
        FavoriteBridge.unbindDiagnosticEvent()
        StatusAreaHostBridge.unbindLaunchSkinChannel()
        StatusAreaHostBridge.unbindLaunchInputMethodSettings()
        MaterialHubHostBridge.unbindLaunch()
        MaterialHubHostBridge.unbindAvailability()
        CustomDrawerHostBridge.unbindList()
        CustomDrawerHostBridge.unbindLaunch()
        CustomDrawerHostBridge.unbindRegistryChanged()
        ToolbarActionHostBridge.unbindLaunchCustomDrawer()
        OemCustomDrawerRegistry.onRegistryChanged = null
        OemImeSdk.bindDrawerHostActions(null)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleMaterialHubLaunchRequest(
        request: MaterialHubLaunchRequest,
        skipEntryLicenseRefresh: Boolean = false,
    ): Boolean {
        return when (request.target) {
            MaterialHubLaunchRequest.Target.MATERIAL_HUB ->
                if (OemEnterpriseConfig.isMaterialLibraryEntryEnabled(this)) {
                    if (!skipEntryLicenseRefresh && !prepareEntryLicenseState(request)) {
                        return true
                    }
                    launchMaterialHubActivity(request = request)
                } else {
                    Toast.makeText(this, "当前宿主已关闭素材库功能", Toast.LENGTH_SHORT).show()
                    false
                }
            MaterialHubLaunchRequest.Target.AI_REPLY -> {
                val clipboardText = request.clipboardReplyText
                val handler = OemImeSdk.clipboardQuickReplyHandler
                if (clipboardText != null && handler != null) {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        runCatching { handler.onQuickReply(this, clipboardText, request.hostPackageName) }
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            runCatching { handler.onQuickReply(this, clipboardText, request.hostPackageName) }
                        }
                    }
                    return true
                }
                if (OemEnterpriseConfig.isAiReplyEntryEnabled(this)) {
                    if (!skipEntryLicenseRefresh && !prepareEntryLicenseState(request)) {
                        return true
                    }
                    launchAiCommunicatePanel(request = request)
                } else {
                    Toast.makeText(this, "当前宿主已关闭 AI 回复功能", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }
    }

    private fun prepareEntryLicenseState(request: MaterialHubLaunchRequest): Boolean {
        if (!OemImeSdk.isInitialized()) return true
        OemImeSdk.applyPendingLicenseRefreshIfAny()
        val currentStatus = runCatching { OemImeSdk.getStatus().license.status }.getOrNull() ?: return true
        if (currentStatus.allowsRuntime) {
            OemImeSdk.refreshLicenseAsync(
                context = applicationContext,
                reason = currentEntryRefreshReason(request.target, prefix = "entry_open"),
                applyMode = OemLicenseRefreshApplyMode.DEFER_UNTIL_NEXT_USE,
                minIntervalMs = ENTRY_LICENSE_REFRESH_MIN_INTERVAL_MS,
            )
            return true
        }
        serviceScope.launch {
            OemImeSdk.refreshLicenseNow(
                context = applicationContext,
                reason = currentEntryRefreshReason(request.target, prefix = "restricted_recover"),
                applyMode = OemLicenseRefreshApplyMode.IMMEDIATE,
            )
            withContext(Dispatchers.Main) {
                handleMaterialHubLaunchRequest(request, skipEntryLicenseRefresh = true)
            }
        }
        return false
    }

    private fun currentEntryRefreshReason(
        target: MaterialHubLaunchRequest.Target,
        prefix: String,
    ): String {
        return when (target) {
            MaterialHubLaunchRequest.Target.AI_REPLY -> "${prefix}_ai_reply_service"
            MaterialHubLaunchRequest.Target.MATERIAL_HUB -> "${prefix}_material_hub_service"
        }
    }

    override fun clipboardForegroundPackage(): String? = runCatching {
        AutoGLMManager.getInstance()?.service?.rootInActiveWindow?.packageName?.toString()
    }.getOrNull()

    override fun isClipboardQuickReplyEnabled(): Boolean =
        isClipboardQuickReplyHostHandled() ||
            (OemAddons.hasAiReply && OemAddons.materialHub != null && OemEnterpriseConfig.isAiReplyEntryEnabled(this))

    override fun isClipboardQuickReplyHostHandled(): Boolean = OemImeSdk.clipboardQuickReplyHandler != null

    private fun createAuthorizedMaterialHubIntent(request: MaterialHubLaunchRequest): Intent? {
        val hostPackageName = currentInputEditorInfo?.packageName?.takeIf { it.isNotBlank() }
        if (currentInputConnection == null || hostPackageName == null) {
            OemLog.w(TAG, "Blocked material hub launch without active editor context")
            return null
        }
        if (request.clipboardReplyText != null &&
            (request.hostPackageName != hostPackageName || !hostClipboard.isActiveHost(hostPackageName))) return null
        val options = OemMaterialHubLaunchOptions(
            target = when (request.target) {
                MaterialHubLaunchRequest.Target.MATERIAL_HUB ->
                    OemMaterialHubLaunchOptions.TARGET_MATERIAL_HUB
                MaterialHubLaunchRequest.Target.AI_REPLY ->
                    OemMaterialHubLaunchOptions.TARGET_AI_REPLY
            },
            startInComposer = request.startInComposer,
            quickTextEntry = request.quickTextEntry,
            restorePickerType = request.restorePickerType,
            hostPackageName = hostPackageName,
            previewFilePath = request.previewFilePath,
            previewDisplayName = request.previewDisplayName,
            previewMimeType = request.previewMimeType,
            previewAttachmentType = request.previewAttachmentType,
            clipboardReplyText = request.clipboardReplyText,
        )
        return OemAddons.materialHub?.createAuthorizedLaunchIntent(this, options)
    }

    private fun launchMaterialHubActivity(
        startInComposer: Boolean = false,
        quickTextEntry: Boolean = false,
        delayMs: Long = MATERIAL_HUB_LAUNCH_DELAY_MS,
        reuseExistingTask: Boolean = true,
        restorePickerType: String? = null,
    ): Boolean {
        return launchMaterialHubActivity(
            request = MaterialHubLaunchRequest.materialHub(
                startInComposer = startInComposer,
                quickTextEntry = quickTextEntry,
                restorePickerType = restorePickerType,
            ),
            delayMs = delayMs,
            reuseExistingTask = reuseExistingTask,
        )
    }

    private fun launchMaterialHubActivity(
        request: MaterialHubLaunchRequest,
        delayMs: Long = MATERIAL_HUB_LAUNCH_DELAY_MS,
        reuseExistingTask: Boolean = true,
    ): Boolean {
        if (!OemEnterpriseConfig.isMaterialLibraryEntryEnabled(this)) {
            Toast.makeText(this, "当前宿主已关闭素材库功能", Toast.LENGTH_SHORT).show()
            return false
        }
        val customUiInstalled = hasMaterialCustomUiOverride()
        if (
            !customUiInstalled &&
            currentFavoriteHostApp().materialHubPresentationStrategy ==
            FavoriteMaterialHubPresentationStrategy.ATTACHED_IME_DIALOG &&
            request.target == MaterialHubLaunchRequest.Target.MATERIAL_HUB &&
            request.previewFilePath.isNullOrBlank()
        ) {
            return launchAttachedMaterialHubDialog(
                startInComposer = request.startInComposer,
                quickTextEntry = request.quickTextEntry,
                restorePickerType = request.restorePickerType,
            )
        }
        if (customUiInstalled) {
            OemLog.d(TAG, "material hub launch routed to overlay activity: custom UI factory installed")
        }
        return launchMaterialHubActivityOverlay(
            request = request,
            delayMs = delayMs,
            reuseExistingTask = reuseExistingTask,
        )
    }

    private fun hasMaterialCustomUiOverride(): Boolean {
        return runCatching {
            OemImeSdk.isMaterialLibraryCustomUiEnabled() &&
                OemImeSdk.drawerContentFactory(
                    com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerKit.DRAWER_MATERIAL_HUB,
                ) != null
        }.getOrDefault(false)
    }

    private fun launchMaterialHubActivityOverlay(
        request: MaterialHubLaunchRequest,
        delayMs: Long = MATERIAL_HUB_LAUNCH_DELAY_MS,
        reuseExistingTask: Boolean = true,
        beforeLaunch: (() -> Unit)? = null,
        hideImeBeforeLaunch: Boolean = true,
        afterLaunch: (() -> Unit)? = null,
    ): Boolean {
        val authorizedIntent = createAuthorizedMaterialHubIntent(request) ?: return false
        return runCatching {
            beforeLaunch?.invoke()
            if (hideImeBeforeLaunch) {
                requestHideSelf(0)
            }
            val launchFlags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    if (reuseExistingTask) {
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    } else {
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    }
            val launchAction = {
                startActivity(
                    authorizedIntent.apply {
                        addFlags(launchFlags)
                    }
                )
                afterLaunch?.invoke()
                Unit
            }
            if (delayMs > 0L) {
                Handler(Looper.getMainLooper()).postDelayed(launchAction, delayMs)
            } else {
                Handler(Looper.getMainLooper()).post(launchAction)
            }
        }.isSuccess
    }

    private fun launchAttachedMaterialHubDialog(
        startInComposer: Boolean = false,
        quickTextEntry: Boolean = false,
        restorePickerType: String? = null,
    ): Boolean {
        return runCatching {
            attachedMaterialHubDefaultAvatar =
                attachedMaterialHubDefaultAvatar ?: BitmapFactory.decodeResource(resources, R.drawable.img_edit_profile_avatar_default_21)
            refreshAttachedMaterialHubAvatar()
            attachedMaterialHubDialog?.dismiss()
            attachedMaterialHubController?.destroy()
            var textComposerActivityLaunchPending = false
            val controller = MaterialHubFavoriteController(
                context = this,
                onKeyboardVisibilityChanged = { visible ->
                    if (visible) {
                        requestShowSelf(0)
                    }
                    0
                },
                onRequestTextComposerActivity = requestTextComposer@{
                    if (textComposerActivityLaunchPending) {
                        return@requestTextComposer true
                    }
                    textComposerActivityLaunchPending = true
                    val dialogToDismiss = attachedMaterialHubDialog
                    val launched = launchMaterialHubActivityOverlay(
                        request = MaterialHubLaunchRequest.materialHub(
                            startInComposer = true,
                            quickTextEntry = true,
                        ),
                        // Keep the attached drawer visible during the focusable editor handoff,
                        // so this looks like an in-place page switch instead of close/reopen.
                        delayMs = 0L,
                        reuseExistingTask = false,
                        hideImeBeforeLaunch = false,
                        afterLaunch = {
                            Handler(Looper.getMainLooper()).postDelayed(
                                Runnable { dialogToDismiss?.dismiss() },
                                120L,
                            )
                            Unit
                        },
                    )
                    if (!launched) {
                        textComposerActivityLaunchPending = false
                    }
                    launched
                },
                onRequestClose = { afterClosed ->
                    attachedMaterialHubDialog?.dismiss()
                    afterClosed?.invoke()
                },
                onPreviewAttachment = { _, _ ->
                    Toast.makeText(this, "当前宿主暂不支持附件预览", Toast.LENGTH_SHORT).show()
                },
                onEnsurePickerPermission = { type, onResult ->
                    requestEmbeddedFavoritePickerPermission(type, onResult)
                },
                isLoggedIn = { true },
                onLoginRequested = {
                    AuthSessionStore.showHostManagedAccountToast(this, "素材库")
                },
                userAvatarProvider = { attachedMaterialHubUserAvatar },
                defaultAvatarProvider = { attachedMaterialHubDefaultAvatar },
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
            val dialog = buildAttachedMaterialHubDialog(controller).also { built ->
                built.setOnDismissListener {
                    attachedMaterialHubController?.destroy()
                    attachedMaterialHubController = null
                    attachedMaterialHubDialog = null
                }
            }
            attachedMaterialHubController = controller
            attachedMaterialHubDialog = dialog
            showAttachedDialog(dialog, dimBehind = false)
            val restriction = OemEnterpriseConfig.resolveMaterialLibraryRestriction(this)
            if (restriction != null) {
                controller.showRestrictedState(restriction.feature, restriction.message)
            } else {
                controller.show(
                    startInComposer = startInComposer,
                    quickTextEntry = quickTextEntry,
                    restorePickerType = restorePickerType,
                    hostPackageName = currentInputEditorInfo?.packageName,
                )
            }
        }.isSuccess
    }

    private fun buildAttachedMaterialHubDialog(controller: MaterialHubFavoriteController): Dialog {
        val attachedPanelHeight = calculateAttachedMaterialHubHeight()
        val dialog = Dialog(this)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)
        dialog.setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundColor(Color.TRANSPARENT)
                        addView(
                            View(context),
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                0,
                                1f,
                            )
                        )
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                )
                addView(
                    FrameLayout(context).apply {
                        clipChildren = false
                        clipToPadding = false
                        addView(
                            controller.rootView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        )
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        attachedPanelHeight,
                        Gravity.BOTTOM,
                    )
                )
            }
        )
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        return dialog
    }

    private fun calculateAttachedMaterialHubHeight(): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        if (screenHeight > 0) {
            return (screenHeight * ATTACHED_MATERIAL_HUB_HEIGHT_RATIO).roundToInt()
        }
        return dp(460)
    }

    private fun refreshAttachedMaterialHubAvatar() {
        attachedMaterialHubUserAvatar = null
        if (!AuthSessionStore.isLoggedIn(this)) return
        val avatarUrl = AuthSessionStore.userAvatarUrl(this)
        val localPath = AuthSessionStore.avatarCachePath(this)
        serviceScope.launch {
            attachedMaterialHubUserAvatar = withContext(Dispatchers.IO) {
                UserAvatarHelper.loadAvatarBitmap(this@AiInputMethodService, avatarUrl, localPath)
            }
        }
    }
    fun launchAiCommunicatePanel(
        delayMs: Long = AI_COMMUNICATE_LAUNCH_DELAY_MS,
        request: MaterialHubLaunchRequest = MaterialHubLaunchRequest.aiReply(),
    ): Boolean {
        if (!OemEnterpriseConfig.isAiReplyEntryEnabled(this)) {
            Toast.makeText(this, "当前宿主已关闭 AI 回复功能", Toast.LENGTH_SHORT).show()
            return false
        }
        val launchToken = OemAddons.aiReply?.tryAcquireLaunch()
        if (launchToken == null) {
            OemLog.d(TAG, "launchAiCommunicatePanel ignored: launch gate busy")
            return false
        }
        val authorizedIntent = createAuthorizedMaterialHubIntent(request) ?: run {
            OemAddons.aiReply?.releaseLaunch(launchToken)
            return false
        }
        return runCatching {
            requestHideSelf(0)
            OemAddons.aiReply?.clearSessionCache()
            val launchFlags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            val launchAction = Runnable {
                if (request.clipboardReplyText != null && !hostClipboard.isActiveHost(request.hostPackageName.orEmpty())) {
                    OemAddons.aiReply?.releaseLaunch(launchToken)
                    return@Runnable
                }
                runCatching {
                    OemLog.d(TAG, "launchAiCommunicatePanel startActivity dispatch")
                    startActivity(
                        authorizedIntent.apply {
                            addFlags(launchFlags)
                        }
                    )
                }.onFailure { throwable ->
                    val released = OemAddons.aiReply?.releaseLaunch(launchToken) ?: false
                    OemLog.w(
                        TAG,
                        "launchAiCommunicatePanel startActivity failed released=$released message=${throwable.message}",
                        throwable,
                    )
                }
            }
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    val released = OemAddons.aiReply?.releaseLaunch(launchToken) ?: false
                    if (released) {
                        OemLog.w(TAG, "launchAiCommunicatePanel watchdog released stale gate token=$launchToken")
                    }
                },
                delayMs + AI_COMMUNICATE_LAUNCH_WATCHDOG_MS,
            )
            if (delayMs > 0L) {
                Handler(Looper.getMainLooper()).postDelayed(launchAction, delayMs)
            } else {
                Handler(Looper.getMainLooper()).post(launchAction)
            }
        }.onFailure {
            OemAddons.aiReply?.releaseLaunch(launchToken)
        }.isSuccess
    }
    private fun restoreMaterialHubComposer(quickTextEntry: Boolean = false): Boolean {
        // #region debug-point B:document-restore-material-hub-composer
        reportDocumentPreviewBackDebug(
            hypothesisId = "B",
            location = "AiInputMethodService:restoreMaterialHubComposer",
            msg = "restore material hub composer requested for document preview session",
            data = "quickTextEntry=$quickTextEntry,currentInputViewStartCount=${currentInputViewStartCount()},hasDraft=${FavoriteComposerSession.hasDraft()},hasMeaningfulDraft=${FavoriteComposerSession.hasMeaningfulDraft()},textEditorActive=${FavoriteComposerSession.isTextEditorActive()}"
        )
        // #endregion
        // #region debug-point C:restore-material-hub-composer
        reportMediaPreviewBackDebug(
            hypothesisId = "C",
            location = "AiInputMethodService:restoreMaterialHubComposer",
            msg = "restore material hub composer requested",
            data = "quickTextEntry=$quickTextEntry,currentInputViewStartCount=${currentInputViewStartCount()},hasDraft=${FavoriteComposerSession.hasDraft()},hasMeaningfulDraft=${FavoriteComposerSession.hasMeaningfulDraft()},textEditorActive=${FavoriteComposerSession.isTextEditorActive()}"
        )
        // #endregion
        return launchMaterialHubActivity(
            startInComposer = true,
            quickTextEntry = quickTextEntry,
            delayMs = 0L,
            reuseExistingTask = true,
        )
    }

    private fun restoreMaterialHubPicker(type: FavoritePickerType): Boolean {
        // #region debug-point B:document-restore-material-hub-picker
        reportDocumentPreviewBackDebug(
            hypothesisId = "B",
            location = "AiInputMethodService:restoreMaterialHubPicker",
            msg = "restore material hub picker requested for document preview session",
            data = "type=$type,currentInputViewStartCount=${currentInputViewStartCount()},hasDraft=${FavoriteComposerSession.hasDraft()},hasMeaningfulDraft=${FavoriteComposerSession.hasMeaningfulDraft()},textEditorActive=${FavoriteComposerSession.isTextEditorActive()}"
        )
        // #endregion
        return launchMaterialHubActivity(
            startInComposer = true,
            quickTextEntry = false,
            delayMs = 0L,
            reuseExistingTask = true,
            restorePickerType = type.name,
        )
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        localVoice?.cancel()
        InputQaCaptureManager.getInstance(this).onStartInputView(info)
        val runtimeBlockState = getRuntimeInputBlockState()
        if (runtimeBlockState == null && OemEnterpriseConfig.isMaterialLibraryEnabled(this)) {
            OemAddons.materialHub?.startRemoteMaterialAutoSync(this, reason = "ime_on_start_input_view")
        }
        ensureExpandedToolbarByDefault()
        super.onStartInputView(info, restarting)
        inputViewStartCounter.incrementAndGet()
        if (runtimeBlockState == null) {
            applyToolbarCustomization()
            serviceScope.launch {
                ThemeMarketplaceBridge.reportActiveThemeUsageIfNeeded(this@AiInputMethodService)
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        localVoice?.cancel()
        InputQaCaptureManager.getInstance(this).onFinishInputView()
        super.onFinishInputView(finishingInput)
    }

    override fun onInputViewsRecreated(theme: Theme) {
        super.onInputViewsRecreated(theme)
        if (getRuntimeInputBlockState() == null) {
            applyToolbarCustomization()
        }
    }

    override fun commitText(text: String, cursor: Int) {
        InputQaCaptureManager.getInstance(this).onCommitText(text)
        super.commitText(text, cursor)
    }

    override fun getT9SidebarState(): T9SidebarState {
        if (!shouldUseNumberSidebarMode()) {
            numberSidebarPageIndex = 0
            return super.getT9SidebarState()
        }
        val symbols = loadNumberSidebarSymbols()
        if (symbols.isEmpty()) {
            return super.getT9SidebarState()
        }
        val totalPages = ((symbols.size - 1) / 4) + 1
        val pageIndex = numberSidebarPageIndex.coerceIn(0, totalPages - 1)
        return T9SidebarState(
            items = symbols.drop(pageIndex * 4).take(4),
            pageIndex = pageIndex,
            totalPages = totalPages,
        )
    }

    override fun advanceT9SidebarPage() {
        if (!shouldUseNumberSidebarMode()) {
            super.advanceT9SidebarPage()
            return
        }
        val symbols = loadNumberSidebarSymbols()
        val totalPages = ((symbols.size - 1) / 4) + 1
        if (totalPages <= 1) {
            return
        }
        numberSidebarPageIndex = (numberSidebarPageIndex + 1) % totalPages
        onT9SidebarStateChanged?.invoke(getT9SidebarState())
    }

    override fun onT9SidebarCandidatePressed(index: Int): Boolean {
        if (!shouldUseNumberSidebarMode()) {
            return super.onT9SidebarCandidatePressed(index)
        }
        val symbol = getCurrentNumberSidebarItems().getOrNull(index) ?: return false
        Handler(Looper.getMainLooper()).post {
            commitText(symbol, 1)
        }
        return true
    }

    private fun shouldUseNumberSidebarMode(): Boolean {
        if (
            InputMethodSettingsBridge.getLayoutSidebarMode(this) !=
            InputMethodSettingsBridge.LayoutSidebarMode.Number
        ) {
            return false
        }
        if (!AppPrefs.getInstance().keyboard.useT9KeyboardLayout.getValue()) {
            return false
        }
        return getCurrentT9ModeLabel() == "中"
    }

    private fun getCurrentNumberSidebarItems(): List<String> {
        val symbols = loadNumberSidebarSymbols()
        if (symbols.isEmpty()) {
            return emptyList()
        }
        val totalPages = ((symbols.size - 1) / 4) + 1
        val pageIndex = numberSidebarPageIndex.coerceIn(0, totalPages - 1)
        return symbols.drop(pageIndex * 4).take(4)
    }

    private fun loadNumberSidebarSymbols(): List<String> {
        return DataBaseKT.instance.sideSymbolDao().getAllSideSymbolNumber().mapNotNull { entry ->
            val value = entry.symbolValue.takeIf { it.isNotBlank() } ?: entry.symbolKey
            value.takeIf { it.isNotBlank() }
        }
    }

    fun currentInputViewStartCount(): Int = inputViewStartCounter.get()

    fun isTargetAppInputReady(packageName: String): Boolean {
        val currentPackage = currentInputEditorInfo?.packageName
        return currentPackage.equals(packageName, ignoreCase = true) && currentInputConnection != null
    }

    private fun shareFavoritePartsWithHostIntent(
        hostPackageName: String,
        parts: List<FavoritePart>,
    ): FavoriteHostShareDispatchResult {
        val hostApp = FavoriteHostApp.fromPackageName(hostPackageName)
        val attachments = parts.filterIsInstance<FavoritePart.Attachment>()
        if (attachments.isEmpty()) {
            return FavoriteHostShareDispatchResult.NOT_HANDLED
        }
        val shouldUseHostShare = when (hostApp.attachmentSendStrategy) {
            FavoriteAttachmentSendStrategy.HOST_SHARE -> true
            FavoriteAttachmentSendStrategy.INPUT_CONTENT_WITH_HOST_SHARE_FALLBACK ->
                !canSendFavoriteAttachmentsViaInputContent(hostApp, attachments)
            FavoriteAttachmentSendStrategy.INPUT_CONTENT,
            FavoriteAttachmentSendStrategy.UNSUPPORTED -> false
        }
        if (!shouldUseHostShare) {
            return FavoriteHostShareDispatchResult.NOT_HANDLED
        }
        val targetPackageName = hostApp.packageName
            ?: return FavoriteHostShareDispatchResult.FAILED
        val targetActivityClassName = hostApp.shareActivityClassName
            ?: return FavoriteHostShareDispatchResult.FAILED
        val sharedItems = attachments.map { attachment ->
            val file = resolveFavoriteAttachmentFile(attachment)
                ?: return FavoriteHostShareDispatchResult.FAILED
            val mimeType = resolveAttachmentMimeType(attachment, file)
                ?: "application/octet-stream"
            val uri = runCatching {
                FileProvider.getUriForFile(this, "$packageName.cropper.fileprovider", file)
            }.getOrNull() ?: return FavoriteHostShareDispatchResult.FAILED
            Triple(uri, mimeType, attachment.displayName)
        }
        if (
            hostApp.shareMimeTypePrefixes.isNotEmpty() &&
            sharedItems.any { (_, mimeType, _) ->
                hostApp.shareMimeTypePrefixes.none { prefix ->
                    mimeType.startsWith(prefix, ignoreCase = true)
                }
            }
        ) {
            OemLog.w(
                TAG,
                "Host share rejected unsupported media: host=${hostApp.name}, " +
                    "mimeTypes=${sharedItems.joinToString { it.second }}",
            )
            return FavoriteHostShareDispatchResult.FAILED
        }
        val sharedUris = ArrayList(sharedItems.map { it.first })
        val sharedMimeType = mergeSharedMimeTypes(sharedItems.map { it.second })
        val sharedText = parts
            .filterIsInstance<FavoritePart.Text>()
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        val intent = Intent(
            if (sharedUris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
        ).apply {
            type = sharedMimeType
            component = ComponentName(targetPackageName, targetActivityClassName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            if (sharedUris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, sharedUris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, sharedUris)
            }
            if (sharedText.isNotEmpty()) {
                putExtra(Intent.EXTRA_TEXT, sharedText)
            }
            clipData = ClipData(
                ClipDescription(sharedItems.first().third, arrayOf(sharedMimeType)),
                ClipData.Item(sharedUris.first()),
            ).also { clip ->
                sharedUris.drop(1).forEach { uri -> clip.addItem(ClipData.Item(uri)) }
            }
        }
        return runCatching {
            sharedUris.forEach { uri ->
                grantUriPermission(targetPackageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            OemLog.d(
                TAG,
                "Host share dispatched: host=${hostApp.name}, attachments=${sharedUris.size}, mime=$sharedMimeType",
            )
            FavoriteHostShareDispatchResult.DISPATCHED
        }.getOrElse { throwable ->
            OemLog.w(TAG, "Host share launch failed: host=${hostApp.name}", throwable)
            FavoriteHostShareDispatchResult.FAILED
        }
    }

    private fun canSendFavoriteAttachmentsViaInputContent(
        hostApp: FavoriteHostApp,
        attachments: List<FavoritePart.Attachment>,
    ): Boolean {
        if (attachments.size != 1) return false
        val editorInfo = currentInputEditorInfo ?: return false
        if (!editorInfo.packageName.equals(hostApp.packageName, ignoreCase = true)) return false
        if (currentInputConnection == null) return false
        val attachment = attachments.single()
        val file = resolveFavoriteAttachmentFile(attachment) ?: return false
        val mimeType = resolveAttachmentMimeType(attachment, file) ?: return false
        return isMimeTypeSupportedByEditor(
            supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo),
            mimeType = mimeType,
        )
    }

    private fun mergeSharedMimeTypes(mimeTypes: List<String>): String {
        val normalized = mimeTypes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        if (normalized.size == 1) return normalized.first()
        val topLevelTypes = normalized.map { it.substringBefore('/', "") }
            .filter { it.isNotEmpty() }
            .distinct()
        return if (topLevelTypes.size == 1) "${topLevelTypes.first()}/*" else "*/*"
    }

    private fun tryDirectSendFavoriteAttachment(attachment: FavoritePart.Attachment): Boolean {
        val editorInfo = currentInputEditorInfo ?: return false
        val hostApp = FavoriteHostApp.fromPackageName(editorInfo.packageName)
        if (
            hostApp.attachmentSendStrategy != FavoriteAttachmentSendStrategy.INPUT_CONTENT &&
            hostApp.attachmentSendStrategy != FavoriteAttachmentSendStrategy.INPUT_CONTENT_WITH_HOST_SHARE_FALLBACK
        ) {
            return false
        }
        val hostPackageName = hostApp.packageName ?: return false
        val file = resolveFavoriteAttachmentFile(attachment) ?: return false
        val mimeType = resolveAttachmentMimeType(attachment, file) ?: return false
        val inputConnection = currentInputConnection ?: return false
        val supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        val supportsAttachmentContent = isMimeTypeSupportedByEditor(supportedMimeTypes, mimeType)
        if (!supportsAttachmentContent) return false
        val uri = runCatching {
            FileProvider.getUriForFile(this, "$packageName.cropper.fileprovider", file)
        }.getOrNull() ?: return false
        val description = ClipDescription(file.name, arrayOf(mimeType))
        val contentInfo = InputContentInfoCompat(uri, description, null)
        if (hostApp.directAttachmentCompletionStrategy == FavoriteDirectAttachmentCompletionStrategy.WECHAT_CONFIRMATION) {
            FavoriteWechatShareHelper.markPendingAttachmentName(attachment.displayName)
        }
        return runCatching {
            grantUriPermission(hostPackageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            InputConnectionCompat.commitContent(
                inputConnection,
                editorInfo,
                contentInfo,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null
            )
        }.getOrDefault(false).also { committed ->
            if (committed) {
                pendingDirectAttachmentHostApp = hostApp
            } else if (
                hostApp.directAttachmentCompletionStrategy ==
                FavoriteDirectAttachmentCompletionStrategy.WECHAT_CONFIRMATION
            ) {
                FavoriteWechatShareHelper.clearPendingAttachmentName()
            }
        }
    }

    private suspend fun waitForDirectAttachmentResume(
        previousInputViewStartCount: Int,
        timeoutMs: Long,
        automationEnabled: Boolean,
    ): Boolean {
        val hostApp = pendingDirectAttachmentHostApp
        return try {
            when (hostApp.directAttachmentCompletionStrategy) {
                FavoriteDirectAttachmentCompletionStrategy.COMMIT_ACCEPTED -> {
                    delay(650L)
                    true
                }
                FavoriteDirectAttachmentCompletionStrategy.WECHAT_CONFIRMATION -> {
                    if (automationEnabled) {
                        return FavoriteWechatShareHelper.autoConfirmDirectAttachmentSend(
                            context = this,
                            timeoutMs = timeoutMs,
                            previousInputViewStartCount = previousInputViewStartCount,
                        )
                    }
                    val startedAt = SystemClock.elapsedRealtime()
                    var observedNonAttachmentStage = false
                    while (SystemClock.elapsedRealtime() - startedAt < timeoutMs) {
                        delay(180L)
                        val ready = isTargetAppAttachmentReady()
                        val currentStartCount = currentInputViewStartCount()
                        if (!ready) {
                            observedNonAttachmentStage = true
                            continue
                        }
                        if (observedNonAttachmentStage &&
                            (currentStartCount > previousInputViewStartCount || ready)
                        ) {
                            return true
                        }
                    }
                    false
                }
                FavoriteDirectAttachmentCompletionStrategy.NONE -> false
            }
        } finally {
            pendingDirectAttachmentHostApp = FavoriteHostApp.GENERIC
        }
    }

    private fun isTargetAppAttachmentReady(): Boolean {
        val editorInfo = currentInputEditorInfo ?: return false
        if (
            FavoriteHostApp.fromPackageName(editorInfo.packageName).directAttachmentCompletionStrategy !=
            FavoriteDirectAttachmentCompletionStrategy.WECHAT_CONFIRMATION
        ) {
            return false
        }
        if (currentInputConnection == null) return false
        val supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        return supportedMimeTypes.any { mime ->
            val lowerMime = mime.lowercase()
            lowerMime == "*/*" ||
                lowerMime == "image/*" ||
                lowerMime == "video/*" ||
                lowerMime == "application/*" ||
                lowerMime.startsWith("image/") ||
                lowerMime.startsWith("video/") ||
                lowerMime.startsWith("application/")
        }
    }

    private fun ensureExpandedToolbarByDefault() {
        AppPrefs.getInstance().keyboard.expandToolbarByDefault.setValue(true)
    }

    private fun preferChineseInputMethodOnFirstSetup() {
        val prefs = getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PREFERRED_CHINESE_INITIALIZED, false)) {
            return
        }
        postFcitxJob {
            val enabled = enabledIme()
            val preferred = enabled.firstOrNull { it.uniqueName == DEFAULT_CHINESE_IME }
                ?: return@postFcitxJob
            val current = runCatching { currentIme() }.getOrNull()
            val shouldSwitchToChinese = current == null ||
                current.uniqueName == DEFAULT_ENGLISH_IME ||
                current.languageCode.startsWith("en", ignoreCase = true) ||
                current.displayName.equals("English", ignoreCase = true)
            if (shouldSwitchToChinese) {
                activateIme(preferred.uniqueName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    SubtypeManager.subtypeOf(preferred.uniqueName)?.let { subtype ->
                        switchInputMethod(
                            com.paopaoime.aikeyboard.ime.utils.InputMethodUtil.componentName,
                            subtype
                        )
                    }
                }
            }
            prefs.edit().putBoolean(KEY_PREFERRED_CHINESE_INITIALIZED, true).apply()
        }
    }

    private fun applyToolbarCustomization() {
        val decorView = window.window?.decorView ?: return
        applyToolbarCustomization(decorView)
        decorView.post { applyToolbarCustomization(decorView) }
        decorView.postDelayed({ applyToolbarCustomization(decorView) }, 120L)
    }

    private fun applyToolbarCustomization(root: View) {
        runCatching {
            ImeToolbarCustomizer.apply(root, this)
        }.onFailure { throwable ->
            OemLog.w(TAG, "applyToolbarCustomization failed: ${throwable.message}", throwable)
        }
    }

    private fun resolveFavoriteSampleFile(fileName: String): File? {
        return FavoriteWechatShareHelper.resolveLocalSampleFile(this, fileName)
    }

    private fun resolveFavoriteAttachmentFile(attachment: FavoritePart.Attachment): File? {
        val localFile = attachment.file().takeIf { it.exists() && it.isFile }
        if (localFile != null) return localFile
        return resolveFavoriteSampleFile(attachment.displayName)
    }

    private fun requestFavoriteAttachmentPicker(type: FavoritePickerType): Boolean {
        if (type != FavoritePickerType.TEXT && hasFavoriteReadPermission(type)) {
            OemLog.d(TAG, "requestFavoriteAttachmentPicker skip permission page: type=$type")
            return launchFavoritePicker(type)
        }
        return when (type) {
            FavoritePickerType.IMAGE,
            FavoritePickerType.VIDEO,
            FavoritePickerType.FILE -> requestFavoriteReadPermission(type) {
                if (!it) {
                    showFavoriteToast("未获得访问权限")
                    restoreMaterialHubComposer()
                } else {
                    launchFavoritePicker(type)
                }
            }
            FavoritePickerType.TEXT -> false
        }
    }

    private fun requestEmbeddedFavoritePickerPermission(
        type: FavoritePickerType,
        onResult: (Boolean) -> Unit,
    ): Boolean {
        if (type == FavoritePickerType.TEXT) return false
        if (hasFavoriteReadPermission(type)) {
            onResult(true)
            return false
        }
        return requestFavoriteReadPermission(type) { granted ->
            if (!granted) {
                showFavoriteToast("未获得访问权限")
            }
            onResult(granted)
            restoreMaterialHubComposer()
        }
    }

    private fun hasFavoriteReadPermission(type: FavoritePickerType): Boolean {
        return when (type) {
            FavoritePickerType.FILE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
            FavoritePickerType.VIDEO -> {
                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_VIDEO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }
            FavoritePickerType.IMAGE -> {
                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }
            FavoritePickerType.TEXT -> true
        }
    }

    private fun requestFavoriteReadPermission(
        type: FavoritePickerType,
        onResult: (Boolean) -> Unit,
    ): Boolean {
        val oldResult = ImagePermissionBridge.onResult
        ImagePermissionBridge.onResult = { granted ->
            ImagePermissionBridge.onResult = oldResult
            onResult(granted)
        }
        return runCatching {
            startActivity(
                Intent(this, ImagePermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    putExtra(
                        ImagePermissionActivity.EXTRA_PERMISSION_KIND,
                        when (type) {
                            FavoritePickerType.VIDEO -> ImagePermissionActivity.PERMISSION_KIND_VIDEO
                            FavoritePickerType.FILE -> ImagePermissionActivity.PERMISSION_KIND_FILE
                            else -> ImagePermissionActivity.PERMISSION_KIND_IMAGE
                        }
                    )
                }
            )
            true
        }.getOrElse {
            ImagePermissionBridge.onResult = oldResult
            false
        }
    }

    private fun launchFavoritePicker(type: FavoritePickerType): Boolean {
        val oldPicked = FilePickBridge.onPicked
        val oldDismissed = FilePickBridge.onDismissed
        fun restore() {
            FilePickBridge.onPicked = oldPicked
            FilePickBridge.onDismissed = oldDismissed
        }
        FilePickBridge.onPicked = { uri ->
            restore()
            handleFavoritePickerResult(type, uri)
        }
        FilePickBridge.onDismissed = {
            restore()
            Handler(Looper.getMainLooper()).post {
                restoreMaterialHubComposer()
            }
        }
        return runCatching {
            startActivity(
                Intent(this, FilePickerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    putExtra(FilePickerActivity.EXTRA_PICKER_KIND, type.name)
                }
            )
            true
        }.getOrElse {
            restore()
            false
        }
    }

    private fun handleFavoritePickerResult(type: FavoritePickerType, uri: Uri) {
        serviceScope.launch {
            val draft = FavoriteComposerSession.ensureDraft()
            val attachment = FavoriteStorage(this@AiInputMethodService)
                .createAttachmentFromUri(draft.draftId, uri, type)
            withContext(Dispatchers.Main) {
                if (attachment == null) {
                    showFavoriteToast("素材导入失败，请重试")
                } else {
                    FavoriteComposerSession.appendAttachment(attachment)
                }
                restoreMaterialHubComposer()
            }
        }
    }

    private fun openFavoriteAttachmentPreview(attachment: FavoritePart.Attachment): Boolean {
        val file = resolveFavoriteAttachmentFile(attachment) ?: return false
        // #region debug-point B:document-open-preview-activity
        reportDocumentPreviewBackDebug(
            hypothesisId = "B",
            location = "AiInputMethodService:openFavoriteAttachmentPreview",
            msg = "start material hub inline preview for document preview session",
            data = "attachmentId=${attachment.id},type=${attachment.attachmentType},displayName=${attachment.displayName},filePath=${file.absolutePath}"
        )
        // #endregion
        // #region debug-point C:open-preview-activity
        reportMediaPreviewBackDebug(
            hypothesisId = "C",
            location = "AiInputMethodService:openFavoriteAttachmentPreview",
            msg = "start material hub inline preview",
            data = "attachmentId=${attachment.id},type=${attachment.attachmentType},displayName=${attachment.displayName},filePath=${file.absolutePath}"
        )
        // #endregion
        val authorizedIntent =
            createAuthorizedMaterialHubIntent(
                MaterialHubLaunchRequest.materialHub(
                    previewFilePath = file.absolutePath,
                    previewDisplayName = attachment.displayName,
                    previewMimeType = attachment.mimeType,
                    previewAttachmentType = attachment.attachmentType.name,
                )
            ) ?: return false
        return runCatching {
            startActivity(
                authorizedIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            )
            FavoriteComposerSession.clearPreviewReturnPickerType()
            true
        }.getOrElse {
            FavoriteComposerSession.clearPreviewReturnPickerType()
            false
        }
    }

    private fun showFavoriteToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveAttachmentMimeType(
        attachment: FavoritePart.Attachment,
        file: File,
    ): String? {
        val attachmentMime = attachment.mimeType.trim().lowercase()
        if (attachmentMime.isNotBlank() &&
            attachmentMime != "application/*" &&
            attachmentMime != "application/octet-stream"
        ) {
            return attachmentMime
        }
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "rtf" -> "application/rtf"
            "html", "htm" -> "text/html"
            else -> null
        }
    }

    private fun isMimeTypeSupportedByEditor(
        supportedMimeTypes: Array<String>,
        mimeType: String,
    ): Boolean {
        if (supportedMimeTypes.isEmpty()) return false
        val lowerMimeType = mimeType.lowercase()
        val mimeGroup = lowerMimeType.substringBefore('/', "")
        return supportedMimeTypes.any { supported ->
            val lowerSupported = supported.lowercase()
            lowerSupported == "*/*" ||
                lowerSupported == lowerMimeType ||
                (mimeGroup.isNotEmpty() && lowerSupported == "$mimeGroup/*")
        }
    }

    private fun commitTextWithVerification(text: String): Boolean {
        val ic = currentInputConnection ?: return false
        lastIdlefishTextCommitMode = "none"
        if (currentFavoriteHostApp() == FavoriteHostApp.IDLEFISH) {
            val strategyCommitted = tryCommitTextViaHostStrategy(text)
            if (strategyCommitted) {
                scheduleIdlefishInputProbe(stage = "hostStrategyPrimary:$lastIdlefishTextCommitMode", expectedText = text)
                return true
            }
        }
        val before = snapshotInputState(ic)
        OemLog.d(TAG, "commitTextWithVerification before=${before.describeForLog()} textLength=${text.length}")
        // #region debug-point A:commit-text-entry
        reportHostTextSendDebug(
            hypothesisId = "A",
            location = "AiInputMethodService:commitTextWithVerification:start",
            msg = "commit text entered",
            data = "package=${currentInputEditorInfo?.packageName.orEmpty()},inputType=${currentInputEditorInfo?.inputType ?: -1},imeOptions=${currentInputEditorInfo?.imeOptions ?: -1},textLength=${text.length},before=${before.describeForLog()}"
        )
        // #endregion

        if (runInputMutation(ic, text, "commitText") { connection ->
                connection.commitText(text, 1)
            }) {
            scheduleIdlefishInputProbe(stage = "commitText", expectedText = text)
            return true
        }

        if (runInputMutation(ic, text, "setComposingText") { connection ->
                connection.setComposingText(text, 1)
                connection.finishComposingText()
            }) {
            scheduleIdlefishInputProbe(stage = "setComposingText", expectedText = text)
            return true
        }

        if (runInputMutation(ic, text, "charByChar") { connection ->
            var inserted = false
            text.forEach { ch ->
                inserted = connection.commitText(ch.toString(), 1) || inserted
            }
            inserted
        }) {
            scheduleIdlefishInputProbe(stage = "charByChar", expectedText = text)
            return true
        }
        val strategyCommitted = tryCommitTextViaHostStrategy(text)
        if (strategyCommitted) {
            scheduleIdlefishInputProbe(stage = "hostStrategy:$lastIdlefishTextCommitMode", expectedText = text)
        }
        return strategyCommitted
    }

    private fun pasteLatestClipboardWithVerification(): Boolean {
        val ic = currentInputConnection ?: return false
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clipText = runCatching {
            clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        }.getOrDefault("")
        if (clipText.isEmpty()) return false

        InputQaCaptureManager.getInstance(this).onPasteText(clipText)

        return runInputMutation(ic, clipText, "paste") { connection ->
            connection.performContextMenuAction(android.R.id.paste)
        }
    }

    private fun performFavoriteTextSend(expectedText: String): Boolean {
        if (expectedText.isBlank()) return false
        // Xiaohongshu's chat editor is multiline and uses a separate send button. A synthetic
        // Enter only appends a line break, so leave the committed text intact for manual sending.
        if (currentFavoriteHostApp() == FavoriteHostApp.XIAOHONGSHU) return false
        if (currentFavoriteHostApp() == FavoriteHostApp.IDLEFISH) {
            return performIdlefishFavoriteTextSend(expectedText)
        }
        val ic = currentInputConnection ?: return false
        // #region debug-point B:text-send-entry
        reportHostTextSendDebug(
            hypothesisId = "B",
            location = "AiInputMethodService:performFavoriteTextSend:start",
            msg = "favorite text send entered",
            data = "package=${currentInputEditorInfo?.packageName.orEmpty()},actionId=${currentInputEditorInfo?.actionId ?: -1},actionLabel=${currentInputEditorInfo?.actionLabel ?: ""},imeOptions=${currentInputEditorInfo?.imeOptions ?: -1},expectedLength=${expectedText.length},snapshot=${snapshotInputState(ic).describeForLog()}"
        )
        // #endregion
        val beforeSend = snapshotInputState(ic)
        if (!beforeSend.containsText(expectedText)) {
            OemLog.d(
                TAG,
                "performFavoriteTextSend aborted expected text missing before send " +
                    "before=${beforeSend.describeForLog()} expectedLength=${expectedText.length}"
            )
            return false
        }
        if (tryPerformEditorActionAndVerify(ic, EditorInfo.IME_ACTION_SEND, expectedText, "imeActionSend")) {
            return true
        }
        val editorInfo = currentInputEditorInfo
        if (
            editorInfo?.actionLabel?.isNotEmpty() == true &&
            editorInfo.actionId != EditorInfo.IME_ACTION_UNSPECIFIED &&
            tryPerformEditorActionAndVerify(ic, editorInfo.actionId, expectedText, "actionLabel:${editorInfo.actionLabel}")
        ) {
            return true
        }
        val imeAction = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (
            imeAction != null &&
            imeAction != EditorInfo.IME_ACTION_UNSPECIFIED &&
            imeAction != EditorInfo.IME_ACTION_NONE &&
            imeAction != EditorInfo.IME_ACTION_SEND &&
            tryPerformEditorActionAndVerify(ic, imeAction, expectedText, "imeOptions:$imeAction")
        ) {
            return true
        }
        if (trySendEnterAndVerify(ic, expectedText)) {
            return true
        }
        val tapped = runCatching {
            runBlocking {
                FavoriteWechatShareHelper.tryTapChatTextSendButton(this@AiInputMethodService)
            }
        }.getOrDefault(false)
        // #region debug-point C:fallback-tap-result
        reportHostTextSendDebug(
            hypothesisId = "C",
            location = "AiInputMethodService:performFavoriteTextSend:fallbackTap",
            msg = "fallback tap completed",
            data = "package=${currentInputEditorInfo?.packageName.orEmpty()},tapped=$tapped,expectedLength=${expectedText.length},snapshot=${snapshotInputState(ic).describeForLog()}"
        )
        // #endregion
        if (!tapped) {
            OemLog.d(TAG, "performFavoriteTextSend fallback tap skipped expectedLength=${expectedText.length}")
            return false
        }
        SystemClock.sleep(220)
        val consumed = !snapshotInputState(ic).containsText(expectedText)
        // #region debug-point D:fallback-consumed
        reportHostTextSendDebug(
            hypothesisId = "D",
            location = "AiInputMethodService:performFavoriteTextSend:fallbackConsumed",
            msg = "fallback tap consumption checked",
            data = "package=${currentInputEditorInfo?.packageName.orEmpty()},consumed=$consumed,expectedLength=${expectedText.length},snapshot=${snapshotInputState(ic).describeForLog()}"
        )
        // #endregion
        OemLog.d(TAG, "performFavoriteTextSend fallback tap consumed=$consumed expectedLength=${expectedText.length}")
        return consumed
    }

    private fun performIdlefishFavoriteTextSend(expectedText: String): Boolean {
        if (!hasAutomationLocalExecutor()) {
            val bestEffort = performIdlefishBestEffortSendWithoutAutomation(expectedText)
            reportHostTextSendDebug(
                hypothesisId = "D",
                location = "AiInputMethodService:performIdlefishFavoriteTextSend:noAutomationExecutor",
                msg = "idlefish non-automation send fallback completed",
                data = "package=${currentInputEditorInfo?.packageName.orEmpty()},bestEffort=$bestEffort,commitMode=$lastIdlefishTextCommitMode,expectedLength=${expectedText.length}"
            )
            scheduleIdlefishInputProbe(
                stage = "afterSend:noAutomationExecutor:bestEffort=$bestEffort:commitMode=$lastIdlefishTextCommitMode",
                expectedText = expectedText
            )
            return bestEffort || lastIdlefishTextCommitMode == "clipboard_paste_only"
        }
        // #region debug-point D:idlefish-send-entry
        reportHostTextSendDebug(
            hypothesisId = "D",
            location = "AiInputMethodService:performIdlefishFavoriteTextSend:start",
            msg = "idlefish dedicated send entered",
            data = "package=${currentInputEditorInfo?.packageName.orEmpty()},expectedLength=${expectedText.length}"
        )
        // #endregion
        val tapped = runCatching {
            runBlocking {
                FavoriteWechatShareHelper.tryTapChatTextSendButtonAndVerifyDisappeared(this@AiInputMethodService)
            }
        }.getOrDefault(false)
        // #region debug-point D:idlefish-send-result
        reportHostTextSendDebug(
            hypothesisId = "D",
            location = "AiInputMethodService:performIdlefishFavoriteTextSend:result",
            msg = "idlefish dedicated send completed",
            data = "package=${currentInputEditorInfo?.packageName.orEmpty()},tapped=$tapped,expectedLength=${expectedText.length}"
        )
        scheduleIdlefishInputProbe(
            stage = "afterSend:dedicated:tapped=$tapped",
            expectedText = expectedText
        )
        // #endregion
        return tapped
    }

    private fun tryPerformEditorActionAndVerify(
        ic: InputConnection,
        actionId: Int,
        expectedText: String,
        label: String,
    ): Boolean {
        val before = snapshotInputState(ic)
        if (!before.containsText(expectedText)) {
            OemLog.d(
                TAG,
                "tryPerformEditorActionAndVerify skipped label=$label missingExpectedBefore=${before.describeForLog()} expectedLength=${expectedText.length}"
            )
            return false
        }
        val actionOk = runCatching { ic.performEditorAction(actionId) }.getOrDefault(false)
        SystemClock.sleep(160)
        val after = snapshotInputState(ic)
        val consumed = actionOk && !after.containsText(expectedText) && after != before
        // #region debug-point B:editor-action-result
        reportHostTextSendDebug(
            hypothesisId = "B",
            location = "AiInputMethodService:tryPerformEditorActionAndVerify",
            msg = "editor action evaluated",
            data = "package=${currentInputEditorInfo?.packageName.orEmpty()},label=$label,actionId=$actionId,actionOk=$actionOk,consumed=$consumed,before=${before.describeForLog()},after=${after.describeForLog()},expectedLength=${expectedText.length}"
        )
        // #endregion
        OemLog.d(
            TAG,
            "tryPerformEditorActionAndVerify label=$label actionId=$actionId actionOk=$actionOk consumed=$consumed " +
                "before=${before.describeForLog()} after=${after.describeForLog()} expectedLength=${expectedText.length}"
        )
        return consumed
    }

    private fun trySendEnterAndVerify(
        ic: InputConnection,
        expectedText: String,
    ): Boolean {
        val before = snapshotInputState(ic)
        if (!before.containsText(expectedText)) {
            OemLog.d(
                TAG,
                "trySendEnterAndVerify skipped missingExpectedBefore=${before.describeForLog()} expectedLength=${expectedText.length}"
            )
            return false
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        SystemClock.sleep(160)
        val after = snapshotInputState(ic)
        val consumed = !after.containsText(expectedText) && after != before
        // #region debug-point B:enter-result
        reportHostTextSendDebug(
            hypothesisId = "B",
            location = "AiInputMethodService:trySendEnterAndVerify",
            msg = "enter send evaluated",
            data = "package=${currentInputEditorInfo?.packageName.orEmpty()},consumed=$consumed,before=${before.describeForLog()},after=${after.describeForLog()},expectedLength=${expectedText.length}"
        )
        // #endregion
        OemLog.d(
            TAG,
            "trySendEnterAndVerify consumed=$consumed before=${before.describeForLog()} " +
                "after=${after.describeForLog()} expectedLength=${expectedText.length}"
        )
        return consumed
    }

    private fun runInputMutation(
        ic: InputConnection,
        expectedText: String,
        label: String,
        mutation: (InputConnection) -> Boolean,
    ): Boolean {
        val before = snapshotInputState(ic)
        val actionOk = runCatching { mutation(ic) }.getOrDefault(false)
        SystemClock.sleep(120)
        val after = snapshotInputState(ic)
        val verified = actionOk && after.containsText(expectedText) && after != before
        // #region debug-point A:input-mutation-result
        reportHostTextSendDebug(
            hypothesisId = "A",
            location = "AiInputMethodService:runInputMutation",
            msg = "input mutation evaluated",
            data = "package=${currentInputEditorInfo?.packageName.orEmpty()},label=$label,actionOk=$actionOk,verified=$verified,before=${before.describeForLog()},after=${after.describeForLog()},expectedLength=${expectedText.length}"
        )
        // #endregion
        OemLog.d(
            TAG,
            "runInputMutation label=$label actionOk=$actionOk verified=$verified before=${before.describeForLog()} after=${after.describeForLog()} expectedLength=${expectedText.length}"
        )
        return verified
    }

    private fun currentFavoriteHostApp(): FavoriteHostApp {
        return FavoriteHostApp.fromPackageName(currentInputEditorInfo?.packageName)
    }

    private suspend fun activateFavoriteHostInput(packageName: String): Boolean {
        val hostApp = FavoriteHostApp.fromPackageName(packageName)
        return when (hostApp.inputActivationStrategy) {
            FavoriteHostInputActivationStrategy.WECOM_CHAT_EDITOR -> activateWeComChatInput()
            FavoriteHostInputActivationStrategy.IDLEFISH_CHAT_EDITOR -> activateIdlefishChatInput()
            FavoriteHostInputActivationStrategy.NONE -> false
        }
    }

    private suspend fun activateIdlefishChatInput(): Boolean {
        return activateHostChatInput(
            hostApp = FavoriteHostApp.IDLEFISH,
            targetLabel = "idlefish",
            targetFinder = ::findIdlefishInputActivationNode,
        )
    }

    private suspend fun activateWeComChatInput(): Boolean {
        return activateHostChatInput(
            hostApp = FavoriteHostApp.WECOM,
            targetLabel = "wecom",
            targetFinder = ::findWeComInputActivationNode,
        )
    }

    private suspend fun activateHostChatInput(
        hostApp: FavoriteHostApp,
        targetLabel: String,
        targetFinder: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?,
    ): Boolean {
        val manager = AutoGLMManager.getInstance()
        if (manager == null) {
            reportHostTextSendDebug(
                hypothesisId = "E",
                location = "AiInputMethodService:activateHostChatInput",
                msg = "$targetLabel activation skipped because accessibility manager unavailable",
                data = "hostPackage=${hostApp.packageName.orEmpty()},editorPackage=${currentInputEditorInfo?.packageName.orEmpty()}"
            )
            return false
        }
        val root = manager.service?.rootInActiveWindow
        val rootPackage = root?.packageName?.toString()?.trim().orEmpty()
        if (!rootPackage.equals(hostApp.packageName, ignoreCase = true)) {
            reportHostTextSendDebug(
                hypothesisId = "E",
                location = "AiInputMethodService:activateHostChatInput",
                msg = "$targetLabel activation skipped because active window package mismatched",
                data = "expectedPackage=${hostApp.packageName.orEmpty()},rootPackage=$rootPackage"
            )
            return false
        }
        val target = root?.let(targetFinder)
        val rect = Rect()
        if (target == null || !runCatching { target.getBoundsInScreen(rect); !rect.isEmpty }.getOrDefault(false)) {
            reportHostTextSendDebug(
                hypothesisId = "E",
                location = "AiInputMethodService:activateHostChatInput",
                msg = "$targetLabel activation target not found",
                data = "rootPackage=$rootPackage"
            )
            return false
        }
        val tapped = manager.performTap(rect.centerX().toFloat(), rect.centerY().toFloat())
        delay(220L)
        reportHostTextSendDebug(
            hypothesisId = "E",
            location = "AiInputMethodService:activateHostChatInput",
            msg = "$targetLabel activation tap dispatched",
            data = "rootPackage=$rootPackage rect=${rect.flattenToString()} tapped=$tapped"
        )
        return tapped
    }

    private fun findIdlefishInputActivationNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screenHeight = resources.displayMetrics.heightPixels
        val minTop = (screenHeight * 0.72f).toInt()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        val visited = HashSet<Int>(256)
        val rect = Rect()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!visited.add(node.hashCode())) continue
            val childCount = runCatching { node.childCount }.getOrDefault(0)
            for (index in childCount - 1 downTo 0) {
                runCatching { node.getChild(index) }.getOrNull()?.let(stack::addLast)
            }
            val desc = node.contentDescription?.toString().orEmpty()
            if (!desc.contains("想跟TA说点什么")) continue
            val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
            if (!visible) continue
            val inBounds = runCatching {
                node.getBoundsInScreen(rect)
                !rect.isEmpty && rect.top >= minTop
            }.getOrDefault(false)
            if (!inBounds) continue
            return node
        }
        return null
    }

    private fun findWeComInputActivationNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val minCenterY = (screenHeight * 0.45f).toInt()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        val visited = HashSet<Int>(256)
        val rect = Rect()
        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!visited.add(node.hashCode())) continue
            val childCount = runCatching { node.childCount }.getOrDefault(0)
            for (index in childCount - 1 downTo 0) {
                runCatching { node.getChild(index) }.getOrNull()?.let(stack::addLast)
            }
            val className = node.className?.toString().orEmpty()
            val isEditText = className.endsWith("EditText", ignoreCase = true)
            val isEditable = runCatching { node.isEditable }.getOrDefault(false)
            if (!isEditText && !isEditable) continue
            if (!runCatching { node.isVisibleToUser && node.isEnabled && !node.isPassword }.getOrDefault(false)) {
                continue
            }
            val inBounds = runCatching {
                node.getBoundsInScreen(rect)
                !rect.isEmpty && rect.centerY() >= minCenterY && rect.width() >= screenWidth / 4
            }.getOrDefault(false)
            if (!inBounds) continue
            val resourceId = node.viewIdResourceName.orEmpty()
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.hintText?.toString().orEmpty()
            } else {
                ""
            }
            val semanticText = listOf(node.text?.toString().orEmpty(), hint, node.contentDescription?.toString().orEmpty())
                .joinToString(separator = " ")
            val hasChatSemantics = listOf("消息", "输入", "回复", "发送", "按住").any(semanticText::contains)
            val score =
                (if (isEditText) 500 else 0) +
                    (if (resourceId.startsWith("${FavoriteHostApp.WECOM.packageName}:id/")) 220 else 0) +
                    (if (hasChatSemantics) 180 else 0) +
                    (if (runCatching { node.isFocusable }.getOrDefault(false)) 80 else 0) +
                    (if (runCatching { node.isClickable }.getOrDefault(false)) 60 else 0) +
                    ((rect.centerY().toFloat() / screenHeight) * 100).toInt() +
                    ((rect.width().toFloat() / screenWidth) * 80).toInt()
            if (score > bestScore) {
                bestScore = score
                bestNode = node
            }
        }
        return bestNode
    }

    private fun tryCommitTextViaHostStrategy(text: String): Boolean {
        return when (currentFavoriteHostApp()) {
            FavoriteHostApp.IDLEFISH -> commitTextViaIdlefishStrategy(text)
            FavoriteHostApp.WECHAT,
            FavoriteHostApp.QQ,
            FavoriteHostApp.WECOM,
            FavoriteHostApp.WHATSAPP,
            FavoriteHostApp.DOUYIN,
            FavoriteHostApp.XIAOHONGSHU,
            FavoriteHostApp.KUAISHOU,
            FavoriteHostApp.GENERIC -> false
        }
    }

    private fun commitTextViaIdlefishStrategy(text: String): Boolean {
        if (hasAutomationLocalExecutor()) {
            val midsceneCommitted = commitTextViaMidsceneInput("idlefish", text)
            if (midsceneCommitted) {
                lastIdlefishTextCommitMode = "midscene_input"
                return true
            }
        }
        val clipboardCommitted = pasteTextViaClipboardBestEffort(text)
        if (clipboardCommitted) {
            lastIdlefishTextCommitMode = "clipboard_paste_only"
            reportHostTextSendDebug(
                hypothesisId = "A",
                location = "AiInputMethodService:commitTextViaIdlefishStrategy",
                msg = "idlefish clipboard fallback committed",
                data = "package=${currentInputEditorInfo?.packageName.orEmpty()},textLength=${text.length}"
            )
        }
        return clipboardCommitted
    }

    private fun commitTextViaMidsceneInput(strategy: String, text: String): Boolean {
        val result = runCatching {
            runBlocking {
                OemAddons.automation?.executeBridgeCommand(
                    MidsceneBridgeCommand(
                        type = "input_text",
                        text = text,
                        mode = "replace"
                    )
                )
            }
        }.getOrNull()
        val success = result?.success == true && result.verified != false
        // #region debug-point A:host-accessibility-set-text
        reportHostTextSendDebug(
            hypothesisId = "A",
            location = "AiInputMethodService:commitTextViaMidsceneInput",
            msg = "host midscene input evaluated",
            data = "package=${currentInputEditorInfo?.packageName.orEmpty()},strategy=$strategy,success=$success,verified=${result?.verified},message=${result?.message.orEmpty()},textLength=${text.length}"
        )
        // #endregion
        return success
    }

    private fun pasteTextViaClipboardBestEffort(text: String): Boolean {
        val ic = currentInputConnection ?: return false
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val previousClip = runCatching { clipboard.primaryClip }.getOrNull()
        return try {
            clipboard.setPrimaryClip(ClipData.newPlainText("favorite-idlefish-input", text))
            SystemClock.sleep(80)
            val pasted = runCatching {
                InputQaCaptureManager.getInstance(this).onPasteText(text)
                ic.performContextMenuAction(android.R.id.paste)
            }.getOrDefault(false)
            SystemClock.sleep(140)
            pasted
        } finally {
            runCatching {
                if (previousClip != null) {
                    clipboard.setPrimaryClip(previousClip)
                }
            }
        }
    }

    private fun hasAutomationLocalExecutor(): Boolean {
        return OemAddons.automation?.isBridgeRuntimeReady() == true
    }

    private fun performIdlefishBestEffortSendWithoutAutomation(expectedText: String): Boolean {
        val ic = currentInputConnection ?: return false
        if (tryPerformEditorActionAndVerify(ic, EditorInfo.IME_ACTION_SEND, expectedText, "idlefishImeActionSend")) {
            reportHostTextSendDebug(
                hypothesisId = "D",
                location = "AiInputMethodService:performIdlefishBestEffortSendWithoutAutomation",
                msg = "idlefish ime action send consumed text",
                data = "package=${currentInputEditorInfo?.packageName.orEmpty()},commitMode=$lastIdlefishTextCommitMode,expectedLength=${expectedText.length},snapshot=${snapshotInputState(ic).describeForLog()}"
            )
            scheduleIdlefishInputProbe(stage = "sendAction:imeActionSend", expectedText = expectedText)
            return true
        }
        val editorInfo = currentInputEditorInfo
        if (
            editorInfo?.actionLabel?.isNotEmpty() == true &&
            editorInfo.actionId != EditorInfo.IME_ACTION_UNSPECIFIED &&
            tryPerformEditorActionAndVerify(ic, editorInfo.actionId, expectedText, "idlefishActionLabel:${editorInfo.actionLabel}")
        ) {
            reportHostTextSendDebug(
                hypothesisId = "D",
                location = "AiInputMethodService:performIdlefishBestEffortSendWithoutAutomation",
                msg = "idlefish editor action label consumed text",
                data = "package=${currentInputEditorInfo?.packageName.orEmpty()},actionId=${editorInfo.actionId},actionLabel=${editorInfo.actionLabel ?: ""},commitMode=$lastIdlefishTextCommitMode,expectedLength=${expectedText.length},snapshot=${snapshotInputState(ic).describeForLog()}"
            )
            scheduleIdlefishInputProbe(stage = "sendAction:actionLabel:${editorInfo.actionId}", expectedText = expectedText)
            return true
        }
        val imeAction = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (
            imeAction != null &&
            imeAction != EditorInfo.IME_ACTION_UNSPECIFIED &&
            imeAction != EditorInfo.IME_ACTION_NONE &&
            tryPerformEditorActionAndVerify(ic, imeAction, expectedText, "idlefishImeOptions:$imeAction")
        ) {
            reportHostTextSendDebug(
                hypothesisId = "D",
                location = "AiInputMethodService:performIdlefishBestEffortSendWithoutAutomation",
                msg = "idlefish ime options action consumed text",
                data = "package=${currentInputEditorInfo?.packageName.orEmpty()},imeAction=$imeAction,commitMode=$lastIdlefishTextCommitMode,expectedLength=${expectedText.length},snapshot=${snapshotInputState(ic).describeForLog()}"
            )
            scheduleIdlefishInputProbe(stage = "sendAction:imeOptions:$imeAction", expectedText = expectedText)
            return true
        }
        val enterConsumed = trySendEnterAndVerify(ic, expectedText)
        reportHostTextSendDebug(
            hypothesisId = "D",
            location = "AiInputMethodService:performIdlefishBestEffortSendWithoutAutomation",
            msg = if (enterConsumed) "idlefish enter key consumed text without automation executor" else "idlefish enter key failed to consume text without automation executor",
            data = "package=${currentInputEditorInfo?.packageName.orEmpty()},commitMode=$lastIdlefishTextCommitMode,expectedLength=${expectedText.length},snapshot=${snapshotInputState(ic).describeForLog()}"
        )
        scheduleIdlefishInputProbe(stage = "sendAction:enterKey", expectedText = expectedText)
        return enterConsumed
    }

    private fun scheduleIdlefishInputProbe(stage: String, expectedText: String) {
        if (currentFavoriteHostApp() != FavoriteHostApp.IDLEFISH) return
        val delays = longArrayOf(0L, 120L, 300L)
        delays.forEach { delayMs ->
            serviceScope.launch {
                if (delayMs > 0L) {
                    delay(delayMs)
                }
                val ic = currentInputConnection
                val snapshot = ic?.let(::snapshotInputState)
                reportHostTextSendDebug(
                    hypothesisId = "F",
                    location = "AiInputMethodService:scheduleIdlefishInputProbe",
                    msg = "idlefish delayed input probe captured",
                    data =
                        "stage=$stage " +
                            "delayMs=$delayMs " +
                            "package=${currentInputEditorInfo?.packageName.orEmpty()} " +
                            "inputType=${currentInputEditorInfo?.inputType ?: -1} " +
                            "imeOptions=${currentInputEditorInfo?.imeOptions ?: -1} " +
                            "startCount=${currentInputViewStartCount()} " +
                            "hasConnection=${ic != null} " +
                            "containsExpected=${snapshot?.containsText(expectedText) ?: false} " +
                            "snapshot=${snapshot?.describeForLog() ?: "null"}"
                )
            }
        }
    }

    private fun snapshotInputState(ic: InputConnection): InputStateSnapshot {
        val windowLength = resolveTextSendVerificationWindow()
        return InputStateSnapshot(
            beforeCursor = runCatching { ic.getTextBeforeCursor(windowLength, 0)?.toString().orEmpty() }.getOrDefault(""),
            selectedText = runCatching { ic.getSelectedText(0)?.toString().orEmpty() }.getOrDefault(""),
            afterCursor = runCatching { ic.getTextAfterCursor(windowLength, 0)?.toString().orEmpty() }.getOrDefault(""),
            windowLength = windowLength,
        )
    }

    private fun resolveTextSendVerificationWindow(): Int {
        val maxChars = OemEnterpriseConfig.resolveSendTextMaxChars(applicationContext)
        return (maxChars + TEXT_SEND_VERIFICATION_WINDOW_PADDING)
            .coerceAtMost(TEXT_SEND_VERIFICATION_MAX_WINDOW)
            .coerceAtLeast(TEXT_SEND_VERIFICATION_FULL_MATCH_CHARS)
    }

    private data class InputStateSnapshot(
        val beforeCursor: String,
        val selectedText: String,
        val afterCursor: String,
        val windowLength: Int,
    ) {
        fun containsText(text: String): Boolean {
            if (text.isEmpty()) return true
            val snapshotText = "$beforeCursor\n$selectedText\n$afterCursor"
            val codePointCount = text.codePointCount(0, text.length)
            if (codePointCount <= TEXT_SEND_VERIFICATION_FULL_MATCH_CHARS) {
                return snapshotText.contains(text)
            }
            val prefixEnd = text.offsetByCodePoints(0, TEXT_SEND_VERIFICATION_SAMPLE_CHARS)
            val suffixStart = text.offsetByCodePoints(0, codePointCount - TEXT_SEND_VERIFICATION_SAMPLE_CHARS)
            val prefix = text.substring(0, prefixEnd)
            val suffix = text.substring(suffixStart)
            return snapshotText.contains(prefix) && snapshotText.contains(suffix)
        }

        fun describeForLog(): String {
            return "window=$windowLength,before=${beforeCursor.takeLast(32)}, selected=${selectedText.take(32)}, after=${afterCursor.take(32)}"
        }

        fun toBridgeSnapshot(): ImeTextBridge.InputSnapshot {
            return ImeTextBridge.InputSnapshot(
                beforeCursor = beforeCursor,
                selectedText = selectedText,
                afterCursor = afterCursor
            )
        }
    }
}
