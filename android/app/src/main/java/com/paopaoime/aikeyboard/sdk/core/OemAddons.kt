package com.paopaoime.aikeyboard.sdk.core

import android.app.Application
import android.content.Context
import android.content.Intent
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommand
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommandResult
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeScreenState

data class OemMaterialHubLaunchOptions(
    val target: String,
    val startInComposer: Boolean = false,
    val quickTextEntry: Boolean = false,
    val restorePickerType: String? = null,
    val hostPackageName: String? = null,
    val previewFilePath: String? = null,
    val previewDisplayName: String? = null,
    val previewMimeType: String? = null,
    val previewAttachmentType: String? = null,
    val clipboardReplyText: String? = null,
) {
    companion object {
        const val TARGET_MATERIAL_HUB = "material_hub"
        const val TARGET_AI_REPLY = "ai_reply"
    }
}

enum class OemAutomationStartBlocker {
    NONE,
    STARTING,
    NOTIFICATION_PERMISSION_REQUIRED,
    WIRELESS_DEBUGGING_REQUIRED,
    ADB_PAIRING_REQUIRED,
    AUTHORIZATION_REQUIRED,
}

data class OemAutomationRuntimeDiagnosis(
    val ready: Boolean,
    val running: Boolean,
    val authorized: Boolean,
    val startInProgress: Boolean,
    val hasNotificationPermission: Boolean,
    val hasReachableAdbPort: Boolean,
    val hasSavedAdbPortCandidate: Boolean,
    val blocker: OemAutomationStartBlocker,
    val summary: String,
)

sealed class OemAutomationStartResult {
    object Started : OemAutomationStartResult()
    object AlreadyRunning : OemAutomationStartResult()
    object Unsupported : OemAutomationStartResult()
    data class NeedsUserAction(
        val reason: String,
        val hint: String,
    ) : OemAutomationStartResult()
    data class Failed(
        val reason: String,
        val hint: String? = null,
    ) : OemAutomationStartResult()
}

sealed class OemAutomationStopResult {
    object Stopped : OemAutomationStopResult()
    object AlreadyStopped : OemAutomationStopResult()
    data class Failed(
        val reason: String,
        val hint: String? = null,
    ) : OemAutomationStopResult()
}

data class OemAutomationRuntimeSnapshot(
    val backend: String = BACKEND_NONE,
    val running: Boolean = false,
    val authorized: Boolean = false,
    val ready: Boolean = false,
    val detailMessage: String = "",
) {
    companion object {
        const val BACKEND_STELLAR = "STELLAR"
        const val BACKEND_NONE = "NONE"
    }
}

data class OemAutomationOverlayState(
    val title: String = "",
    val subtitle: String = "",
    val progressText: String = "",
    val progressFraction: Float = 0f,
    val tokenText: String = "",
    val fullscreenMask: Boolean = true,
)

data class OemAutomationShellResult(
    val success: Boolean,
    val output: String,
)

data class OemDeveloperAiTonePreset(
    val label: String,
    val promptHint: String,
)

object OemDeveloperAiConfigDefaults {
    const val DEFAULT_TEMPERATURE = 0.0
    const val DEFAULT_TOP_P = 0.85
    const val DEFAULT_MAX_TOKENS = 1100
    const val DEFAULT_REPLY_COUNT = 10
    const val MIN_REPLY_COUNT = 1
    const val MAX_REPLY_COUNT = 20
    const val DEFAULT_MIN_CHARS = 6
    const val DEFAULT_MAX_CHARS = 22
    const val DEFAULT_REPLY_LENGTH_POLICY = "short_reply"
    const val DEFAULT_BUSINESS_ROLE = "IM 沟通回复助手"
    const val DEFAULT_LANGUAGE = "zh-CN"
    const val DEFAULT_ALLOW_EMOJI = true
    const val DEFAULT_AVOID_NICKNAME = true

    val DEFAULT_TONE_PRESETS: List<OemDeveloperAiTonePreset> =
        listOf(
            OemDeveloperAiTonePreset(
                label = "礼貌回复",
                promptHint = "语气礼貌、克制、清晰，适合商务或不太熟的关系，不要显得生硬。",
            ),
            OemDeveloperAiTonePreset(
                label = "自然沟通",
                promptHint = "语气自然、场景通用、像真人即时回复，不油腻，不端着。",
            ),
            OemDeveloperAiTonePreset(
                label = "推动成交",
                promptHint = "在自然礼貌前提下更偏向推进沟通、确认意向、促成下一步，但不要压迫感太强。",
            ),
        )

    fun defaultSystemPrompt(
        businessRole: String = DEFAULT_BUSINESS_ROLE,
        language: String = DEFAULT_LANGUAGE,
    ): String {
        val displayLanguage = when (language.trim().lowercase()) {
            "zh", "zh-cn", "zh_hans", "chinese" -> "中文"
            "en", "en-us", "english" -> "英文"
            else -> language.ifBlank { "中文" }
        }
        return "你是$businessRole。你只输出可直接发送的${displayLanguage}回复候选，不解释，不加分析，不输出标题。"
    }
}

data class OemDeveloperAiModelConfig(
    val apiBaseUrl: String = "",
    val aiOpenAiBaseUrl: String = "",
    val aiOpenAiApiKey: String = "",
    val aiOpenAiModel: String = "",
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
)

data class OemDeveloperResolvedAiReplyConfig(
    val systemPrompt: String,
    val tonePresets: List<OemDeveloperAiTonePreset>,
    val replyCount: Int,
    val replyLengthPolicy: String,
    val businessRole: String,
    val minChars: Int,
    val maxChars: Int,
    val allowEmoji: Boolean,
    val avoidNickname: Boolean,
    val language: String,
)

interface OemMaterialHubAddon {
    fun createAuthorizedLaunchIntent(context: Context, options: OemMaterialHubLaunchOptions): Intent?

    fun createAuthorizedLaunchIntent(context: Context, target: String, targetPackageName: String?): Intent? {
        return createAuthorizedLaunchIntent(
            context,
            OemMaterialHubLaunchOptions(
                target = target,
                hostPackageName = targetPackageName,
            ),
        )
    }

    fun createCustomDrawerLaunchIntent(context: Context, drawerId: String): Intent? = null

    fun closeCurrentDrawer() {}

    fun installDrawerKitRuntime(context: Context) {}

    fun startRemoteMaterialAutoSync(context: Context, reason: String)

    fun isRemoteMaterialAutoSyncRunning(): Boolean

    fun stopRemoteMaterialAutoSync()
}

interface OemAiReplyAddon {
    fun tryAcquireLaunch(): Long?

    fun releaseLaunch(token: Long): Boolean

    fun releaseLaunch()

    fun markLaunchPanelVisible()

    fun markLaunchPanelHidden()

    fun clearSessionCache()

    fun isScreenCapturePermissionGranted(context: Context): Boolean

    fun clearScreenCapturePermission(context: Context)

    fun startScreenCaptureForegroundService(context: Context, resultCode: Int, data: Intent?)
}

interface OemAutomationAddon {
    fun initializeRuntime(application: Application, enableNeonOverlay: Boolean)

    suspend fun executeBridgeCommand(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult

    suspend fun captureBridgeScreenState(): MidsceneBridgeScreenState?

    fun isBridgeRuntimeReady(): Boolean

    suspend fun setOverlayCaptureSuppressedAndAwaitNextFrame(
        context: Context,
        suppressed: Boolean,
        timeoutMs: Long = 120L,
    )

    fun bootstrapAvailabilityMonitor(context: Context)

    fun isAvailabilityMonitorEnabled(context: Context): Boolean

    fun wasAutomationLastReady(context: Context): Boolean

    fun markAutomationReadyConfirmed(context: Context)

    fun markAutomationManuallyStopped(context: Context)

    suspend fun requestAutomationStart(context: Context): OemAutomationStartResult

    suspend fun requestAutomationStop(context: Context): OemAutomationStopResult

    suspend fun diagnoseAutomationRuntime(context: Context): OemAutomationRuntimeDiagnosis

    fun launchAutomationSettings(context: Context)

    fun isInstalledAppCatalogEnabled(context: Context): Boolean

    fun setInstalledAppCatalogEnabled(context: Context, enabled: Boolean)

    fun getAutomationRuntimeSnapshot(context: Context): OemAutomationRuntimeSnapshot

    fun captureAutomationScreenshotBase64(context: Context): String?

    fun getLastAutomationScreenshotError(): String

    fun setAutomationOverlayNeonEnabled(context: Context, enabled: Boolean)

    fun showAutomationOverlay(context: Context, state: OemAutomationOverlayState, borderOnly: Boolean)

    suspend fun showAutomationOverlayAndAwaitFirstFrame(
        context: Context,
        state: OemAutomationOverlayState,
        borderOnly: Boolean,
    )

    fun hideAutomationOverlay(context: Context)

    fun executeAutomationShellCommand(command: String): OemAutomationShellResult

    fun createStellarManagerIntent(context: Context): Intent?

    fun createAutomationStarterGuideIntent(context: Context, hasSecureSettings: Boolean): Intent?
}

interface OemDeveloperAddon {
    fun initializeRuntime(application: Application) {}

    fun loadAiModelConfig(context: Context): OemDeveloperAiModelConfig

    fun resolveAiReplyConfig(context: Context): OemDeveloperResolvedAiReplyConfig

    fun saveSessionArchiveEvent(context: Context, event: OemSessionArchiveEvent)

    fun createDeveloperSettingsIntent(context: Context): Intent?
}

object OemAddons {
    private const val TAG = "OemAddons"

    @Volatile
    var materialHub: OemMaterialHubAddon? = null
        private set

    @Volatile
    var aiReply: OemAiReplyAddon? = null
        private set

    @Volatile
    var automation: OemAutomationAddon? = null
        private set

    @Volatile
    var developer: OemDeveloperAddon? = null
        private set

    val hasMaterialHub: Boolean
        get() = materialHub != null

    val hasAiReply: Boolean
        get() = aiReply != null

    val hasAutomation: Boolean
        get() = automation != null

    val hasDeveloper: Boolean
        get() = developer != null

    fun install(addon: OemMaterialHubAddon) {
        materialHub = addon
        OemLog.i(TAG, "install addon=materialHub impl=${addon.javaClass.name}")
    }

    fun install(addon: OemAiReplyAddon) {
        aiReply = addon
        OemLog.i(TAG, "install addon=aiReply impl=${addon.javaClass.name}")
    }

    fun install(addon: OemAutomationAddon) {
        automation = addon
        OemLog.i(TAG, "install addon=automation impl=${addon.javaClass.name}")
    }

    fun install(addon: OemDeveloperAddon) {
        developer = addon
        OemLog.i(TAG, "install addon=developer impl=${addon.javaClass.name}")
    }

    fun uninstallMaterialHub() {
        materialHub = null
        OemLog.i(TAG, "uninstall addon=materialHub")
    }

    fun uninstallAiReply() {
        aiReply = null
        OemLog.i(TAG, "uninstall addon=aiReply")
    }

    fun uninstallAutomation() {
        automation = null
        OemLog.i(TAG, "uninstall addon=automation")
    }

    fun uninstallDeveloper() {
        developer = null
        OemLog.i(TAG, "uninstall addon=developer")
    }
}
