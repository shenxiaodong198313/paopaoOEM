package com.paopaoime.aikeyboard.oem

import android.content.Context
import android.content.pm.PackageManager
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.qa.data.QaSessionEntity
import com.paopaoime.aikeyboard.sdk.core.OemAddons
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyDispatchMode
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyErrorText
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyPromptAssembler
import com.paopaoime.aikeyboard.sdk.core.OemDeveloperAiConfigDefaults
import com.paopaoime.aikeyboard.sdk.core.OemDeveloperAiTonePreset
import com.paopaoime.aikeyboard.sdk.core.OemDeveloperAiModelConfig
import com.paopaoime.aikeyboard.sdk.core.OemDeveloperResolvedAiReplyConfig
import com.paopaoime.aikeyboard.sdk.core.OemFeatureToggles
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk
import com.paopaoime.aikeyboard.sdk.core.OemLicenseStatus
import com.paopaoime.aikeyboard.sdk.core.OemLog
import com.paopaoime.aikeyboard.sdk.core.OemManifestMetaKeys
import com.paopaoime.aikeyboard.sdk.core.OemMaterialDispatchMode
import com.paopaoime.aikeyboard.sdk.core.OemMaterialNavigationMode
import com.paopaoime.aikeyboard.sdk.core.OemSessionArchiveEvent
import com.paopaoime.aikeyboard.sdk.core.OemSessionArchiveMode
import com.paopaoime.aikeyboard.sdk.core.OemSwipeDispatchMode

enum class OemAiMode {
    PROXY,
    DIRECT,
    DUAL;

    companion object {
        fun parse(raw: String?): OemAiMode {
            return when (raw?.trim()?.lowercase()) {
                "direct", "openai", "openai_compat" -> DIRECT
                "dual", "both" -> DUAL
                else -> PROXY
            }
        }
    }
}

private data class OemManifestRuntimeConfig(
    val apiBaseUrl: String?,
    val aiMode: OemAiMode,
    val aiOpenAiBaseUrl: String?,
    val aiOpenAiApiKey: String?,
    val aiOpenAiModel: String?,
    val enableAi: Boolean,
    val enableAutomation: Boolean,
    val enableAutomationSettingsEntry: Boolean,
    val enableUi: Boolean,
    val enableDeveloperEntry: Boolean,
    val sessionArchiveMode: OemSessionArchiveMode,
    val aiReplyDispatchMode: OemAiReplyDispatchMode,
    val materialDispatchMode: OemMaterialDispatchMode,
    val materialNavigationMode: OemMaterialNavigationMode,
    val swipeDispatchMode: OemSwipeDispatchMode,
    val aiReplyGenerationTimeoutMs: Long?,
    val aiReplyNetworkTimeoutMs: Long?,
    val aiReplyErrorTextCustomEnabled: Boolean?,
    val aiReplyMaxImageSelection: Int?,
    val sendTextMaxChars: Int?,
    val builtinNeonOverlayEnabled: Boolean?,
    val ocrEnabled: Boolean?,
    val featureToggles: OemFeatureToggles,
)

data class OemDirectAiConfig(
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
)

data class OemKeyboardRuntimeBlock(
    val status: OemLicenseStatus,
    val badge: String,
    val title: String,
    val message: String,
)

private enum class OemRestrictedFeature(val labelResId: Int) {
    AI_REPLY(R.string.oem_feature_name_ai_reply),
    MATERIAL_LIBRARY(R.string.oem_feature_name_material_library),
    QA_DISTRIBUTION(R.string.oem_feature_name_qa_distribution),
}

data class OemFeatureRestriction(
    val feature: String,
    val status: OemLicenseStatus,
    val title: String,
    val message: String,
)

object OemEnterpriseConfig {
    private const val TAG = "OemEnterpriseConfig"
    private const val HOST_ARCHIVE_TAG = "OemHostArchive"
    private const val DEVELOPER_PREFS = "oem_developer_debug"
    private const val KEY_SESSION_ARCHIVE_MODE_OVERRIDE = "session_archive_mode_override"
    private const val META_ENABLE_DEVELOPER_ENTRY = "com.paopaoime.oem.enable_developer_entry"
    /**
     * 统一解析企业 API 基础域名。
     *
     * 优先级：
     * 1. 开发调试覆盖
     * 2. SDK 初始化后的运行时配置
     * 3. AndroidManifest 中的对外配置
     *
     * 不再回退到工程内置默认域名，避免企业宿主漏配后静默打到我方域名。
     */
    fun resolveApiBaseUrl(context: Context?): String {
        val developerOverride = context?.applicationContext
            ?.let { OemAddons.developer?.loadAiModelConfig(it)?.apiBaseUrl?.trim() }
            ?.takeIf { it.isNotEmpty() }
        val runtimeBaseUrl = sdkConfigOrNull()?.apiBaseUrl
        val manifestBaseUrl = context?.let(::manifestConfigOrNull)?.apiBaseUrl
        val configured = developerOverride ?: runtimeBaseUrl ?: manifestBaseUrl
        val resolved = configured
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf { it.isNotEmpty() }
        if (resolved != null) {
            val source = when {
                developerOverride != null -> "developerOverride"
                runtimeBaseUrl != null -> "sdkRuntime"
                else -> "manifest"
            }
            OemLog.d(TAG, "resolveApiBaseUrl source=$source url=$resolved")
        }
        return resolved
            ?: throw IllegalStateException(
                "OEM apiBaseUrl 未配置。请在宿主 SDK 初始化参数或 AndroidManifest 的 " +
                    "com.paopaoime.oem.apiBaseUrl 中显式填写企业自有 API 域名。"
            )
    }

    fun resolveAiMode(context: Context?): OemAiMode {
        val runtimeMode = sdkConfigOrNull()?.aiMode?.name?.let(OemAiMode::valueOf)
        return runtimeMode ?: context?.let(::manifestConfigOrNull)?.aiMode ?: OemAiMode.PROXY
    }

    fun resolveDirectAiConfig(context: Context?): OemDirectAiConfig? {
        val runtime = sdkConfigOrNull()
        val manifest = context?.let(::manifestConfigOrNull)
        val developerConfig = resolveDeveloperAiModelConfig(context)
        val baseUrl = (
            developerConfig.aiOpenAiBaseUrl.takeIf { it.isNotBlank() }
                ?: runtime?.aiOpenAiBaseUrl
                ?: manifest?.aiOpenAiBaseUrl
            )?.trim()?.trimEnd('/').orEmpty()
        val apiKey = (
            developerConfig.aiOpenAiApiKey.takeIf { it.isNotBlank() }
                ?: runtime?.aiOpenAiApiKey
                ?: manifest?.aiOpenAiApiKey
            )?.trim().orEmpty()
        val modelName = (
            developerConfig.aiOpenAiModel.takeIf { it.isNotBlank() }
                ?: runtime?.aiOpenAiModel
                ?: manifest?.aiOpenAiModel
            )?.trim().orEmpty()
        if (baseUrl.isBlank() || apiKey.isBlank() || modelName.isBlank()) {
            return null
        }
        return OemDirectAiConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = modelName,
        )
    }

    /**
     * AI 回复对外是独立功能开关，但运行时仍要求 AI + UI 基础能力都打开。
     */
    fun isAiReplyEnabled(context: Context?): Boolean {
        val sdkEnabled = runCatching {
            if (OemImeSdk.isInitialized()) OemImeSdk.isAiReplyEnabled() else null
        }.getOrNull()
        if (sdkEnabled != null) return sdkEnabled
        val manifest = context?.let(::manifestConfigOrNull)
        val enableAi = manifest?.enableAi ?: true
        val enableUi = manifest?.enableUi ?: true
        val toggleEnabled = manifest?.featureToggles?.aiReplyEnabled ?: true
        return enableAi && enableUi && toggleEnabled
    }

    /**
     * 是否保留 AI 回复入口。
     *
     * 这里只判断宿主配置是否开放，不把企业 license 状态算进来。
     * 这样授权异常时仍可保留入口，点开后进入授权占位页。
     */
    fun isAiReplyEntryEnabled(context: Context?): Boolean {
        val runtime = sdkConfigOrNull()
        if (runtime != null) {
            return runtime.enableAi &&
                runtime.enableUi &&
                sdkFeatureTogglesOrNull()?.aiReplyEnabled == true
        }
        val manifest = context?.let(::manifestConfigOrNull)
        val enableAi = manifest?.enableAi ?: true
        val enableUi = manifest?.enableUi ?: true
        val toggleEnabled = manifest?.featureToggles?.aiReplyEnabled ?: true
        return enableAi && enableUi && toggleEnabled
    }

    /**
     * 素材库依赖 UI 承载，因此会与 UI 总开关取交集。
     */
    fun isMaterialLibraryEnabled(context: Context?): Boolean {
        val sdkEnabled = runCatching {
            if (OemImeSdk.isInitialized()) OemImeSdk.isMaterialLibraryEnabled() else null
        }.getOrNull()
        if (sdkEnabled != null) return sdkEnabled
        val manifest = context?.let(::manifestConfigOrNull)
        val enableUi = manifest?.enableUi ?: true
        val toggleEnabled = manifest?.featureToggles?.materialLibraryEnabled ?: true
        return enableUi && toggleEnabled
    }

    /**
     * 是否保留素材库入口。
     *
     * 与 AI 回复相同，这里只看宿主是否开放，不直接受企业授权状态影响。
     */
    fun isMaterialLibraryEntryEnabled(context: Context?): Boolean {
        val runtime = sdkConfigOrNull()
        if (runtime != null) {
            return runtime.enableUi &&
                sdkFeatureTogglesOrNull()?.materialLibraryEnabled == true
        }
        val manifest = context?.let(::manifestConfigOrNull)
        val enableUi = manifest?.enableUi ?: true
        val toggleEnabled = manifest?.featureToggles?.materialLibraryEnabled ?: true
        return enableUi && toggleEnabled
    }

    /**
     * 企业质检会话为独立功能，不强依赖 AI/UI capability。
     */
    fun isQaSessionEnabled(context: Context?): Boolean {
        val sdkEnabled = runCatching {
            if (OemImeSdk.isInitialized()) OemImeSdk.isQaSessionEnabled() else null
        }.getOrNull()
        if (sdkEnabled != null) return sdkEnabled
        return context?.let(::manifestConfigOrNull)?.featureToggles?.qaSessionEnabled ?: true
    }

    fun resolveAiReplyRestriction(context: Context?): OemFeatureRestriction? {
        return resolveFeatureRestriction(context, OemRestrictedFeature.AI_REPLY)
    }

    fun resolveMaterialLibraryRestriction(context: Context?): OemFeatureRestriction? {
        return resolveFeatureRestriction(context, OemRestrictedFeature.MATERIAL_LIBRARY)
    }

    fun resolveQaDistributionRestriction(context: Context?): OemFeatureRestriction? {
        return resolveFeatureRestriction(context, OemRestrictedFeature.QA_DISTRIBUTION)
    }

    fun isDeveloperEntryEnabled(context: Context?): Boolean {
        return context?.let(::manifestConfigOrNull)?.enableDeveloperEntry ?: true
    }

    fun isAutomationSettingsEntryEnabled(context: Context?): Boolean {
        val runtime = sdkConfigOrNull()
        if (runtime != null) {
            return runtime.enableUi &&
                runtime.enableAutomation &&
                runtime.enableAutomationSettingsEntry
        }
        val manifest = context?.let(::manifestConfigOrNull)
        return manifest?.let {
            it.enableUi && it.enableAutomation && it.enableAutomationSettingsEntry
        } ?: false
    }

    fun resolveKeyboardRuntimeBlock(context: Context?): OemKeyboardRuntimeBlock? {
        // 新产品策略下，基础输入始终保留，授权异常只降级 AI/素材库/质检分发能力。
        return null
    }

    fun resolveSessionArchiveMode(context: Context?): OemSessionArchiveMode {
        val overrideMode = context?.applicationContext?.getSharedPreferences(DEVELOPER_PREFS, Context.MODE_PRIVATE)
            ?.getString(KEY_SESSION_ARCHIVE_MODE_OVERRIDE, null)
            ?.let(OemSessionArchiveMode::parse)
        val sdkMode = runCatching {
            if (OemImeSdk.isInitialized()) OemImeSdk.getSessionArchiveMode() else null
        }.getOrNull()
        val manifestMode = context?.let(::manifestConfigOrNull)?.sessionArchiveMode
        val mode = overrideMode ?: sdkMode ?: manifestMode ?: OemSessionArchiveMode.CLOUD_ONLY
        val source = when {
            overrideMode != null -> "developerOverride"
            sdkMode != null -> "sdkRuntime"
            manifestMode != null -> "manifest"
            else -> "default"
        }
        OemLog.d(TAG, "resolveSessionArchiveMode mode=$mode source=$source")
        return mode
    }

    /**
     * 解析 AI 回复分发模式。
     *
     * 优先级（高 → 低）：
     * 1. SDK 运行时配置（含运行时覆盖值）；
     * 2. AndroidManifest meta-data com.paopaoime.oem.aiReplyDispatchMode；
     * 3. 默认 CLOUD_ONLY（域名配置模式）。
     */
    fun resolveAiReplyDispatchMode(context: Context?): OemAiReplyDispatchMode {
        val sdkMode = runCatching {
            if (OemImeSdk.isInitialized()) OemImeSdk.getAiReplyDispatchMode() else null
        }.getOrNull()
        val manifestMode = context?.let(::manifestConfigOrNull)?.aiReplyDispatchMode
        val mode = sdkMode ?: manifestMode ?: OemAiReplyDispatchMode.CLOUD_ONLY
        val source = when {
            sdkMode != null -> "sdkRuntime"
            manifestMode != null -> "manifest"
            else -> "default"
        }
        OemLog.d(TAG, "resolveAiReplyDispatchMode mode=$mode source=$source")
        return mode
    }

    /**
     * 解析素材库分发模式。
     *
     * 优先级（高 → 低）：
     * 1. SDK 运行时配置（含运行时覆盖值）；
     * 2. AndroidManifest meta-data com.paopaoime.oem.materialDispatchMode；
     * 3. 默认 CLOUD_ONLY（域名配置模式）。
     */
    fun resolveMaterialDispatchMode(context: Context?): OemMaterialDispatchMode {
        OemDeveloperRuntimeBridge.ensureMaterialCallbacksInstalled(context)
        val sdkMode = runCatching {
            OemImeSdk.peekMaterialDispatchModeOverride()
                ?: if (OemImeSdk.isInitialized()) OemImeSdk.getMaterialDispatchMode() else null
        }.getOrNull()
        val manifestMode = context?.let(::manifestConfigOrNull)?.materialDispatchMode
        val mode = sdkMode ?: manifestMode ?: OemMaterialDispatchMode.CLOUD_ONLY
        val source = when {
            sdkMode != null -> "sdkRuntime"
            manifestMode != null -> "manifest"
            else -> "default"
        }
        OemLog.d(TAG, "resolveMaterialDispatchMode mode=$mode source=$source")
        return mode
    }

    /**
     * 解析素材库导航模式。
     *
     * 优先级（高 → 低）：
     * 1. SDK 运行时配置（含运行时覆盖值）；
     * 2. AndroidManifest meta-data com.paopaoime.oem.materialNavigationMode；
     * 3. 默认 TYPE_ONLY。
     *
     * 注意：若宿主最终没有返回任何分类，素材库页面会在 UI 层自动回退成单行类型导航；
     * CATEGORY_ONLY 模式下仅显示分类，不显示类型。
     */
    fun resolveMaterialNavigationMode(context: Context?): OemMaterialNavigationMode {
        OemDeveloperRuntimeBridge.ensureMaterialCallbacksInstalled(context)
        val sdkMode = runCatching {
            OemImeSdk.peekMaterialNavigationModeOverride()
                ?: if (OemImeSdk.isInitialized()) OemImeSdk.getMaterialNavigationMode() else null
        }.getOrNull()
        val manifestMode = context?.let(::manifestConfigOrNull)?.materialNavigationMode
        val mode = sdkMode ?: manifestMode ?: OemMaterialNavigationMode.TYPE_ONLY
        val source = when {
            sdkMode != null -> "sdkRuntime"
            manifestMode != null -> "manifest"
            else -> "default"
        }
        OemLog.d(TAG, "resolveMaterialNavigationMode mode=$mode source=$source")
        return mode
    }

    /**
     * 解析自动化滑屏分发模式。
     *
     * 优先级（高 → 低）：
     * 1. SDK 运行时配置（含运行时覆盖值）；
     * 2. AndroidManifest meta-data com.paopaoime.oem.swipeDispatchMode；
     * 3. 默认 DUAL_WRITE（宿主优先+SDK 兜底）。
     */
    fun resolveSwipeDispatchMode(context: Context?): OemSwipeDispatchMode {
        val sdkMode = runCatching {
            if (OemImeSdk.isInitialized()) OemImeSdk.getSwipeDispatchMode() else null
        }.getOrNull()
        val manifestMode = context?.let(::manifestConfigOrNull)?.swipeDispatchMode
        val mode = sdkMode ?: manifestMode ?: OemSwipeDispatchMode.DUAL_WRITE
        val source = when {
            sdkMode != null -> "sdkRuntime"
            manifestMode != null -> "manifest"
            else -> "default"
        }
        OemLog.d(TAG, "resolveSwipeDispatchMode mode=$mode source=$source")
        return mode
    }

    /** AI 回复超时默认值：断网检测与大模型请求超时均为 30 秒 */
    const val DEFAULT_AI_REPLY_TIMEOUT_MS = 30_000L

    /**
     * 解析 AI 回复大模型请求超时（毫秒），同时作用于云端直连与宿主回调通道。
     *
     * 优先级（高 → 低）：
     * 1. SDK 运行时覆盖（[OemImeSdk.setAiReplyGenerationTimeoutMs] / OemSdkConfig）；
     * 2. AndroidManifest meta-data com.paopaoime.oem.aiReplyGenerationTimeoutMs；
     * 3. 默认 30000。
     */
    fun resolveAiReplyGenerationTimeoutMs(context: Context?): Long {
        val runtime = runCatching { OemImeSdk.getAiReplyGenerationTimeoutMs() }.getOrNull()
        if (runtime != null && runtime > 0) return runtime
        val manifest = context?.let(::manifestConfigOrNull)?.aiReplyGenerationTimeoutMs
        if (manifest != null && manifest > 0) return manifest
        return DEFAULT_AI_REPLY_TIMEOUT_MS
    }

    /**
     * 解析 AI 回复网络连接超时（毫秒）。
     *
     * 优先级（高 → 低）：
     * 1. SDK 运行时覆盖（[OemImeSdk.setAiReplyNetworkTimeoutMs] / OemSdkConfig）；
     * 2. AndroidManifest meta-data com.paopaoime.oem.aiReplyNetworkTimeoutMs；
     * 3. 默认 30000。
     */
    fun resolveAiReplyNetworkTimeoutMs(context: Context?): Long {
        val runtime = runCatching { OemImeSdk.getAiReplyNetworkTimeoutMs() }.getOrNull()
        if (runtime != null && runtime > 0) return runtime
        val manifest = context?.let(::manifestConfigOrNull)?.aiReplyNetworkTimeoutMs
        if (manifest != null && manifest > 0) return manifest
        return DEFAULT_AI_REPLY_TIMEOUT_MS
    }

    /**
     * 解析 AI 回复错误文案宿主自定义开关。
     *
     * 优先级（高 → 低）：
     * 1. SDK 运行时覆盖（[OemImeSdk.setAiReplyErrorTextCustomEnabled] / OemSdkConfig）；
     * 2. AndroidManifest meta-data com.paopaoime.oem.aiReplyErrorTextCustomEnabled；
     * 3. 默认 false（使用 SDK 默认文案）。
     */
    fun resolveAiReplyErrorTextCustomEnabled(context: Context?): Boolean {
        val runtime = runCatching { OemImeSdk.getAiReplyErrorTextCustomEnabled() }.getOrNull()
        if (runtime != null) return runtime
        return context?.let(::manifestConfigOrNull)?.aiReplyErrorTextCustomEnabled ?: false
    }

    /** AI 回复「传图回复」最多可选图片数量，默认 5，允许宿主通过运行时或 Manifest 配置。 */
    fun resolveAiReplyMaxImageSelection(context: Context?): Int {
        val runtime = runCatching { OemImeSdk.getAiReplyMaxImageSelection() }.getOrNull()
        val manifest = context?.let(::manifestConfigOrNull)?.aiReplyMaxImageSelection
        return (runtime ?: manifest ?: DEFAULT_AI_REPLY_MAX_IMAGE_SELECTION)
            .coerceIn(MIN_AI_REPLY_MAX_IMAGE_SELECTION, MAX_AI_REPLY_MAX_IMAGE_SELECTION)
    }

    /** AI 回复与素材库单次文本发送最大字符数，默认 1000，允许宿主运行时或 Manifest 配置。 */
    fun resolveSendTextMaxChars(context: Context?): Int {
        val runtime = runCatching { OemImeSdk.getSendTextMaxChars() }.getOrNull()
        val manifest = context?.let(::manifestConfigOrNull)?.sendTextMaxChars
        return (runtime ?: manifest ?: DEFAULT_SEND_TEXT_MAX_CHARS)
            .coerceIn(MIN_SEND_TEXT_MAX_CHARS, MAX_SEND_TEXT_MAX_CHARS)
    }

    /** SDK 内置 AI 回复霓虹总开关：运行时覆盖 > Manifest > 默认关闭。 */
    fun resolveBuiltinNeonOverlayEnabled(context: Context?): Boolean {
        val runtime = runCatching {
            if (OemImeSdk.isInitialized()) OemImeSdk.isBuiltinNeonOverlayEnabled() else null
        }.getOrNull()
        return runtime ?: context?.let(::manifestConfigOrNull)?.builtinNeonOverlayEnabled ?: false
    }

    /** SDK 内置 OCR 总开关：SDK 初始化参数 > Manifest > 默认开启。 */
    fun resolveOcrEnabled(context: Context?): Boolean {
        return runCatching {
            if (OemImeSdk.isInitialized()) OemImeSdk.getConfig().ocrEnabled else null
        }.getOrNull()
            ?: context?.let(::manifestConfigOrNull)?.ocrEnabled
            ?: true
    }

    /**
     * 解析指定状态码的宿主自定义错误文案；自定义开关关闭时返回 null。
     */
    fun resolveAiReplyErrorText(context: Context?, statusCode: String?): OemAiReplyErrorText? {
        if (statusCode.isNullOrBlank()) return null
        if (!resolveAiReplyErrorTextCustomEnabled(context)) return null
        return runCatching { OemImeSdk.getAiReplyErrorTexts() }.getOrNull()?.get(statusCode)
    }

    fun resolveDeveloperAiModelConfig(context: Context?): OemDeveloperAiModelConfig {
        val appContext = context?.applicationContext ?: return OemDeveloperAiModelConfig()
        return OemAddons.developer?.loadAiModelConfig(appContext)
            ?: OemDeveloperAiModelConfig()
    }

    fun resolveDeveloperTemperature(context: Context?): Double {
        return resolveDeveloperAiModelConfig(context).temperature
            ?: OemDeveloperAiConfigDefaults.DEFAULT_TEMPERATURE
    }

    fun resolveDeveloperTopP(context: Context?): Double {
        return resolveDeveloperAiModelConfig(context).topP
            ?: OemDeveloperAiConfigDefaults.DEFAULT_TOP_P
    }

    fun resolveDeveloperMaxTokens(context: Context?): Int {
        return resolveDeveloperAiModelConfig(context).maxTokens
            ?: OemDeveloperAiConfigDefaults.DEFAULT_MAX_TOKENS
    }

    fun resolveDeveloperAiReplyConfig(context: Context?): OemDeveloperResolvedAiReplyConfig {
        val appContext = context?.applicationContext
        val base = appContext?.let { OemAddons.developer?.resolveAiReplyConfig(it) }
            ?: OemDeveloperResolvedAiReplyConfig(
                systemPrompt = OemDeveloperAiConfigDefaults.defaultSystemPrompt(),
                tonePresets = OemDeveloperAiConfigDefaults.DEFAULT_TONE_PRESETS,
                replyCount = OemDeveloperAiConfigDefaults.DEFAULT_REPLY_COUNT,
                replyLengthPolicy = OemDeveloperAiConfigDefaults.DEFAULT_REPLY_LENGTH_POLICY,
                businessRole = OemDeveloperAiConfigDefaults.DEFAULT_BUSINESS_ROLE,
                minChars = OemDeveloperAiConfigDefaults.DEFAULT_MIN_CHARS,
                maxChars = OemDeveloperAiConfigDefaults.DEFAULT_MAX_CHARS,
                allowEmoji = OemDeveloperAiConfigDefaults.DEFAULT_ALLOW_EMOJI,
                avoidNickname = OemDeveloperAiConfigDefaults.DEFAULT_AVOID_NICKNAME,
                language = OemDeveloperAiConfigDefaults.DEFAULT_LANGUAGE,
            )
        val hostSystemPrompt = runCatching {
            if (OemImeSdk.isInitialized()) OemImeSdk.getAiReplySystemPrompt() else null
        }.getOrNull()
        val hostTonePresets = runCatching {
            if (OemImeSdk.isInitialized()) OemImeSdk.getAiReplyTonePresets() else null
        }.getOrNull()
        return base.copy(
            systemPrompt = hostSystemPrompt?.takeIf { it.isNotBlank() } ?: base.systemPrompt,
            tonePresets = hostTonePresets?.takeIf { it.isNotEmpty() } ?: base.tonePresets,
        )
    }

    /**
     * 解析 AI 回复语气预设列表。
     *
     * 优先级：宿主自定义（OemSdkConfig.aiReplyTonePresets / setAiReplyTonePresets）→
     * 开发者模块配置 → SDK 内置默认语气。
     */
    fun resolveAiReplyTonePresets(context: Context?): List<OemDeveloperAiTonePreset> {
        return resolveDeveloperAiReplyConfig(context).tonePresets
    }

    /**
     * 解析宿主自定义 AI 回复 Prompt 组装器（未注册返回 null，SDK 走内置模板）。
     */
    fun resolveAiReplyPromptAssembler(context: Context?): OemAiReplyPromptAssembler? {
        return runCatching {
            if (OemImeSdk.isInitialized()) OemImeSdk.getAiReplyPromptAssembler() else null
        }.getOrNull()
    }

    fun setDeveloperSessionArchiveModeOverride(context: Context, mode: OemSessionArchiveMode) {
        context.applicationContext
            .getSharedPreferences(DEVELOPER_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSION_ARCHIVE_MODE_OVERRIDE, mode.name)
            .apply()
    }

    /**
     * 将输入质检会话回调给宿主。
     *
     * 宿主如果在 SDK 初始化时注册了 `OemSessionArchiveListener`，
     * 就会收到这里派发的标准事件对象。
     */
    fun dispatchQaSessionToHost(context: Context, session: QaSessionEntity): Boolean {
        if (!isQaSessionEnabled(context)) return false
        if (resolveQaDistributionRestriction(context) != null) return false
        val event = OemSessionArchiveEvent(
            eventId = session.sessionId,
            sessionId = session.sessionId,
            hostApplicationId = context.applicationContext.packageName,
            targetPackageName = session.packageName,
            targetAppName = session.appName,
            maskedContent = session.qaContent,
            inputSource = session.inputSource,
            timestamp = session.timestamp,
            policyState = session.policyState,
        )
        OemAddons.developer?.saveSessionArchiveEvent(context, event)
        return runCatching {
            val dispatched = OemImeSdk.isInitialized() && OemImeSdk.dispatchSessionArchiveEvent(event)
            OemLog.i(
                HOST_ARCHIVE_TAG,
                "dispatchQaSessionToHost dispatched=$dispatched sessionId=${session.sessionId} package=${session.packageName} app=${session.appName} policy=${session.policyState}"
            )
            dispatched
        }.getOrDefault(false)
    }

    fun shouldExposeLegacyBusinessFeatures(): Boolean = false

    fun shouldReportManagedThemeUsage(): Boolean = false

    fun shouldAllowMemberCommerce(): Boolean = false

    fun shouldAllowAppUpgrade(): Boolean = false

    fun shouldCaptureOwnPackageInput(): Boolean = true

    private fun sdkConfigOrNull() = runCatching {
        if (OemImeSdk.isInitialized()) OemImeSdk.getConfig() else null
    }.getOrNull()

    private fun sdkFeatureTogglesOrNull() = runCatching {
        if (OemImeSdk.isInitialized()) OemImeSdk.getEffectiveFeatureToggles() else null
    }.getOrNull()

    private fun licenseSnapshotOrNull() = runCatching {
        if (OemImeSdk.isInitialized()) OemImeSdk.getStatus().license else null
    }.getOrNull()

    private fun resolveFeatureRestriction(
        context: Context?,
        feature: OemRestrictedFeature,
    ): OemFeatureRestriction? {
        val appContext = context?.applicationContext ?: return null
        val license = licenseSnapshotOrNull() ?: return null
        if (license.status == OemLicenseStatus.ACTIVE || license.status == OemLicenseStatus.GRACE) {
            return null
        }
        val featureName = appContext.getString(feature.labelResId)
        if (license.status == OemLicenseStatus.DISABLED) {
            val disabledMessage = when (feature) {
                OemRestrictedFeature.AI_REPLY -> "AI回复当前被限制使用，请联系企业管理员"
                OemRestrictedFeature.MATERIAL_LIBRARY -> "素材库当前被限制使用，请联系企业管理员"
                OemRestrictedFeature.QA_DISTRIBUTION ->
                    appContext.getString(R.string.oem_feature_block_message_disabled, featureName)
            }
            return OemFeatureRestriction(
                feature = featureName,
                status = license.status,
                title = "",
                message = disabledMessage,
            )
        }
        val titleResId = when (license.status) {
            OemLicenseStatus.EXPIRED -> R.string.oem_feature_block_title_expired
            OemLicenseStatus.FULL -> R.string.oem_feature_block_title_full
            OemLicenseStatus.NOT_OPENED,
            OemLicenseStatus.MISSING -> R.string.oem_feature_block_title_not_opened
            OemLicenseStatus.INVALID -> R.string.oem_feature_block_title_invalid
            OemLicenseStatus.ACTIVE,
            OemLicenseStatus.GRACE,
            OemLicenseStatus.DISABLED -> return null
        }
        val messageResId = when (license.status) {
            OemLicenseStatus.EXPIRED -> R.string.oem_feature_block_message_expired
            OemLicenseStatus.FULL -> R.string.oem_feature_block_message_full
            OemLicenseStatus.NOT_OPENED,
            OemLicenseStatus.MISSING -> R.string.oem_feature_block_message_not_opened
            OemLicenseStatus.INVALID -> R.string.oem_feature_block_message_invalid
            OemLicenseStatus.ACTIVE,
            OemLicenseStatus.GRACE,
            OemLicenseStatus.DISABLED -> return null
        }
        return OemFeatureRestriction(
            feature = featureName,
            status = license.status,
            title = appContext.getString(titleResId, featureName),
            message = appContext.getString(messageResId, featureName),
        )
    }

    @Suppress("DEPRECATION")
    private fun manifestConfigOrNull(context: Context): OemManifestRuntimeConfig? = runCatching {
        val appContext = context.applicationContext
        val appInfo = appContext.packageManager.getApplicationInfo(
            appContext.packageName,
            PackageManager.GET_META_DATA,
        )
        val metaData = appInfo.metaData
        OemManifestRuntimeConfig(
            apiBaseUrl = metaData?.getString(OemManifestMetaKeys.API_BASE_URL)?.trim(),
            aiMode = OemAiMode.parse(metaData?.getString(OemManifestMetaKeys.AI_MODE)),
            aiOpenAiBaseUrl = metaData?.getString(OemManifestMetaKeys.AI_OPENAI_BASE_URL)?.trim(),
            aiOpenAiApiKey = metaData?.getString(OemManifestMetaKeys.AI_OPENAI_API_KEY)?.trim(),
            aiOpenAiModel = metaData?.getString(OemManifestMetaKeys.AI_OPENAI_MODEL)?.trim(),
            enableAi = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_AI, false) ?: false,
            enableAutomation = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_AUTOMATION, false) ?: false,
            enableAutomationSettingsEntry = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_AUTOMATION_SETTINGS_ENTRY, false) ?: false,
            enableUi = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_UI, true) ?: true,
            enableDeveloperEntry = metaData?.getBoolean(META_ENABLE_DEVELOPER_ENTRY, false) ?: false,
            sessionArchiveMode = OemSessionArchiveMode.parse(metaData?.getString(OemManifestMetaKeys.SESSION_ARCHIVE_MODE)),
            aiReplyDispatchMode = OemAiReplyDispatchMode.parse(metaData?.getString(OemManifestMetaKeys.AI_REPLY_DISPATCH_MODE)),
            materialDispatchMode = OemMaterialDispatchMode.parse(metaData?.getString(OemManifestMetaKeys.MATERIAL_DISPATCH_MODE)),
            materialNavigationMode = OemMaterialNavigationMode.parse(metaData?.getString(OemManifestMetaKeys.MATERIAL_NAVIGATION_MODE)),
            swipeDispatchMode = OemSwipeDispatchMode.parse(metaData?.getString(OemManifestMetaKeys.SWIPE_DISPATCH_MODE)),
            aiReplyGenerationTimeoutMs = metaData?.getString(OemManifestMetaKeys.AI_REPLY_GENERATION_TIMEOUT_MS)
                ?.trim()?.toLongOrNull(),
            aiReplyNetworkTimeoutMs = metaData?.getString(OemManifestMetaKeys.AI_REPLY_NETWORK_TIMEOUT_MS)
                ?.trim()?.toLongOrNull(),
            aiReplyErrorTextCustomEnabled = metaData?.containsKey(OemManifestMetaKeys.AI_REPLY_ERROR_TEXT_CUSTOM_ENABLED)
                ?.let { metaData.getBoolean(OemManifestMetaKeys.AI_REPLY_ERROR_TEXT_CUSTOM_ENABLED, false) },
            aiReplyMaxImageSelection = readOptionalInt(metaData, OemManifestMetaKeys.AI_REPLY_MAX_IMAGE_SELECTION),
            sendTextMaxChars = readOptionalInt(metaData, OemManifestMetaKeys.SEND_TEXT_MAX_CHARS),
            builtinNeonOverlayEnabled = readOptionalBoolean(metaData, OemManifestMetaKeys.BUILTIN_NEON_OVERLAY_ENABLED),
            ocrEnabled = readOptionalBoolean(metaData, OemManifestMetaKeys.OCR_ENABLED),
            featureToggles = OemFeatureToggles(
                aiReplyEnabled = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_AI_REPLY, false) ?: false,
                materialLibraryEnabled = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_MATERIAL_LIBRARY, false) ?: false,
                aiReplyCustomUiEnabled = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_AI_REPLY_CUSTOM_UI, false) ?: false,
                materialLibraryCustomUiEnabled = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_MATERIAL_LIBRARY_CUSTOM_UI, false) ?: false,
                customDrawersEnabled = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_CUSTOM_DRAWERS, false) ?: false,
                qaSessionEnabled = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_QA_SESSION, false) ?: false,
                automationEnabled = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_AUTOMATION, false) ?: false,
            ),
        )
    }.getOrNull()

    private const val DEFAULT_AI_REPLY_MAX_IMAGE_SELECTION = 5
    private const val MIN_AI_REPLY_MAX_IMAGE_SELECTION = 1
    private const val MAX_AI_REPLY_MAX_IMAGE_SELECTION = 20
    private const val DEFAULT_SEND_TEXT_MAX_CHARS = 1000
    private const val MIN_SEND_TEXT_MAX_CHARS = 1
    private const val MAX_SEND_TEXT_MAX_CHARS = 10_000

    @Suppress("DEPRECATION")
    private fun readOptionalInt(metaData: android.os.Bundle?, key: String): Int? {
        if (metaData == null || !metaData.containsKey(key)) return null
        return when (val value = metaData.get(key)) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun readOptionalBoolean(metaData: android.os.Bundle?, key: String): Boolean? {
        if (metaData == null || !metaData.containsKey(key)) return null
        return when (val value = metaData.get(key)) {
            is Boolean -> value
            is String -> when (value.trim().lowercase()) {
                "true", "1", "yes", "y", "on" -> true
                "false", "0", "no", "n", "off" -> false
                else -> null
            }
            is Number -> value.toInt() != 0
            else -> null
        }
    }
}
