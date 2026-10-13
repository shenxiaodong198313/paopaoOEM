package com.paopaoime.aikeyboard.sdk.core

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.pm.PackageInfoCompat
import com.paopaoime.aikeyboard.sdk.ai.AiKeyboardHostSdk
import com.paopaoime.aikeyboard.sdk.core.drawer.OemAiReplyCapabilities
import com.paopaoime.aikeyboard.sdk.core.drawer.OemCustomDrawerSpec
import com.paopaoime.aikeyboard.sdk.core.drawer.OemCustomDrawerRegistry
import com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerContentFactory
import com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerHostActions
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialCapabilities
import com.paopaoime.aikeyboard.sdk.core.drawer.OemRegisterResult
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

enum class OemEnvironment {
    PROD,
    STAGING,
    DEV;

    companion object {
        fun parse(raw: String?): OemEnvironment {
            return when (raw?.trim()?.lowercase(Locale.ROOT)) {
                "prod", "production" -> PROD
                "staging", "stage", "pre" -> STAGING
                "dev", "debug", "test" -> DEV
                else -> PROD
            }
        }
    }
}

enum class OemAiMode {
    PROXY,
    DIRECT,
    DUAL;

    companion object {
        fun parse(raw: String?): OemAiMode {
            return when (raw?.trim()?.lowercase(Locale.ROOT)) {
                "direct", "openai", "openai_compat" -> DIRECT
                "dual", "both" -> DUAL
                else -> PROXY
            }
        }
    }
}

/**
 * 会话存档分发模式。
 *
 * - LOCAL_ONLY: 仅在 SDK 本地留存，不主动外发
 * - CLOUD_ONLY: 仅上传到宿主配置的企业服务端
 * - HOST_ONLY: 仅回调给宿主 App，由宿主自行处理
 * - DUAL_WRITE: 同时回调宿主 App 并上传企业服务端
 */
enum class OemSessionArchiveMode {
    LOCAL_ONLY,
    CLOUD_ONLY,
    HOST_ONLY,
    DUAL_WRITE;

    companion object {
        fun parse(raw: String?): OemSessionArchiveMode {
            return when (raw?.trim()?.uppercase(Locale.ROOT)) {
                "LOCAL", "LOCAL_ONLY" -> LOCAL_ONLY
                "HOST", "HOST_ONLY", "CALLBACK_ONLY" -> HOST_ONLY
                "DUAL", "DUAL_WRITE", "BOTH" -> DUAL_WRITE
                "CLOUD", "CLOUD_ONLY", "CLOUD_UPLOAD" -> CLOUD_ONLY
                else -> CLOUD_ONLY
            }
        }
    }
}

/**
 * 统一 SDK 下的细粒度功能开关。
 *
 * 这些开关用于控制具体产品功能是否对宿主暴露，
 * 与上层 license capability 做交集后才是最终可用能力。
 *
 * 默认策略：除「基础 + 输入法核心」外，其余功能默认全部关闭；
 * 企业集成后按开通范围通过 Manifest meta-data 或编程式配置显式开启。
 */
data class OemFeatureToggles(
    val aiReplyEnabled: Boolean = false,
    val materialLibraryEnabled: Boolean = false,
    val aiReplyCustomUiEnabled: Boolean = false,
    val materialLibraryCustomUiEnabled: Boolean = false,
    val customDrawersEnabled: Boolean = false,
    val qaSessionEnabled: Boolean = false,
    val automationEnabled: Boolean = false,
    val developerEnabled: Boolean = false,
)

private data class OemFeatureToggleOverrides(
    val aiReplyEnabled: Boolean? = null,
    val materialLibraryEnabled: Boolean? = null,
    val aiReplyCustomUiEnabled: Boolean? = null,
    val materialLibraryCustomUiEnabled: Boolean? = null,
    val customDrawersEnabled: Boolean? = null,
    val qaSessionEnabled: Boolean? = null,
    val automationEnabled: Boolean? = null,
    val developerEnabled: Boolean? = null,
)

private fun OemFeatureToggles.applyOverrides(overrides: OemFeatureToggleOverrides?): OemFeatureToggles {
    if (overrides == null) return this
    return copy(
        aiReplyEnabled = overrides.aiReplyEnabled ?: aiReplyEnabled,
        materialLibraryEnabled = overrides.materialLibraryEnabled ?: materialLibraryEnabled,
        aiReplyCustomUiEnabled = overrides.aiReplyCustomUiEnabled ?: aiReplyCustomUiEnabled,
        materialLibraryCustomUiEnabled = overrides.materialLibraryCustomUiEnabled ?: materialLibraryCustomUiEnabled,
        customDrawersEnabled = overrides.customDrawersEnabled ?: customDrawersEnabled,
        qaSessionEnabled = overrides.qaSessionEnabled ?: qaSessionEnabled,
        automationEnabled = overrides.automationEnabled ?: automationEnabled,
        developerEnabled = overrides.developerEnabled ?: developerEnabled,
    )
}

/**
 * 输入质检会话回调给宿主 App 的标准事件结构。
 *
 * 注意：
 * - `maskedContent` 默认应为本地脱敏后的内容
 * - `eventId` 需保持幂等，便于宿主去重
 */
data class OemSessionArchiveEvent(
    val eventId: String,
    val sessionId: String,
    val hostApplicationId: String,
    val targetPackageName: String,
    val targetAppName: String,
    val maskedContent: String,
    val inputSource: String,
    val timestamp: Long,
    val policyState: String,
)

/**
 * 宿主 App 的输入质检会话接收器。
 *
 * 返回 `true` 表示宿主已接收并自行处理，SDK 可以继续后续流程；
 * 返回 `false` 或抛出异常会视为本次回调失败。
 */
fun interface OemSessionArchiveListener {
    fun onSessionArchived(event: OemSessionArchiveEvent): Boolean
}

/**
 * SDK 结构化诊断事件。
 *
 * 用于把输入框恢复、素材发送等线上排障信息回调给宿主。事件不包含原始聊天内容，
 * `attributes` 仅承载包名、计数、阶段、结果等定位字段。
 */
data class OemDiagnosticEvent(
    val category: String,
    val name: String,
    val level: OemLogLevel,
    val message: String,
    val attributes: Map<String, String> = emptyMap(),
    val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * 宿主 App 的结构化诊断事件接收器。
 *
 * 返回 `true` 表示宿主已接收；返回 `false` 或抛出异常不会影响 SDK 主流程。
 */
fun interface OemDiagnosticListener {
    fun onDiagnosticEvent(event: OemDiagnosticEvent): Boolean
}

/**
 * AI 回复生成生命周期监听器。
 *
 * 宿主可用它自管生成期间的悬浮窗或 loading 反馈；回调异常不会影响 SDK 主流程。
 */
interface OemAiReplyLifecycleListener {
    fun onGenerationStarted(requestId: String, generateMode: String, fresh: Boolean)

    fun onGenerationFinished(requestId: String, generateMode: String, success: Boolean, reason: String)
}

/**
 * AI 回复分发模式：控制「AI 回复」功能的大模型生成结果来源。
 *
 * - CLOUD_ONLY: 域名配置模式（默认）。SDK 自行调用大模型：
 *   aiMode=proxy 时先向宿主配置的企业域名请求模型配置，
 *   aiMode=direct 时使用宿主配置的 OpenAI 兼容三件套直连；
 * - HOST_ONLY: 宿主回调模式。SDK 只负责截屏与 UI 展示，
 *   将截屏与组装好的 Prompt 交给宿主（[OemAiReplyHostDelegate]），
 *   由宿主自行调用大模型，并通过回调把生成的回复内容回传给 SDK 展示；
 *   SDK 全程不连接任何大模型，也不需要配置 apiBaseUrl 域名；
 * - DUAL_WRITE: 双通道容错模式。优先宿主回调，
 *   宿主未注册回调、拒绝处理、超时或失败时自动回落到域名配置模式。
 *
 * 配置解析优先级（高 → 低）：
 * 1. [OemImeSdk.initialize] 传入的 [OemSdkConfig.aiReplyDispatchMode]；
 * 2. AndroidManifest meta-data [OemManifestMetaKeys.AI_REPLY_DISPATCH_MODE]；
 * 3. 默认 CLOUD_ONLY。
 */
enum class OemAiReplyDispatchMode {
    CLOUD_ONLY,
    HOST_ONLY,
    DUAL_WRITE;

    companion object {
        fun parse(raw: String?): OemAiReplyDispatchMode {
            return when (raw?.trim()?.uppercase(Locale.ROOT)) {
                "CLOUD", "CLOUD_ONLY", "PROXY", "API" -> CLOUD_ONLY
                "HOST", "HOST_ONLY", "CALLBACK", "CALLBACK_ONLY" -> HOST_ONLY
                "DUAL", "DUAL_WRITE", "BOTH" -> DUAL_WRITE
                else -> CLOUD_ONLY
            }
        }
    }
}

/**
 * 素材库分发模式：控制「企业素材库」功能的企业集中管理素材来源。
 *
 * - LOCAL_ONLY: 仅本地模式。不拉取任何企业远程素材，
 *   素材库抽屉只显示用户本地自行添加的素材；
 * - CLOUD_ONLY: 域名配置模式（默认）。SDK 周期性向宿主配置的
 *   apiBaseUrl 域名请求企业素材列表（企业侧在自己的服务端完成增删改查）；
 * - HOST_ONLY: 宿主回调模式。SDK 通过 [OemMaterialHostDelegate] 向宿主
 *   拉取企业素材列表；宿主对素材的增删改通过
 *   [OemImeSdk.notifyRemoteMaterialUpserted] / [OemImeSdk.notifyRemoteMaterialRemoved]
 *   等推送接口实时通知 SDK；不需要配置 apiBaseUrl 域名；
 * - DUAL_WRITE: 双通道容错模式。优先宿主回调，
 *   宿主未注册回调、拒绝处理、超时或失败时自动回落到域名配置模式。
 *
 * 配置解析优先级（高 → 低）：
 * 1. [OemImeSdk.initialize] 传入的 [OemSdkConfig.materialDispatchMode]；
 * 2. AndroidManifest meta-data [OemManifestMetaKeys.MATERIAL_DISPATCH_MODE]；
 * 3. 默认 CLOUD_ONLY。
 */
enum class OemMaterialDispatchMode {
    LOCAL_ONLY,
    CLOUD_ONLY,
    HOST_ONLY,
    DUAL_WRITE;

    companion object {
        fun parse(raw: String?): OemMaterialDispatchMode {
            return when (raw?.trim()?.uppercase(Locale.ROOT)) {
                "LOCAL", "LOCAL_ONLY" -> LOCAL_ONLY
                "CLOUD", "CLOUD_ONLY", "API" -> CLOUD_ONLY
                "HOST", "HOST_ONLY", "CALLBACK", "CALLBACK_ONLY" -> HOST_ONLY
                "DUAL", "DUAL_WRITE", "BOTH" -> DUAL_WRITE
                else -> CLOUD_ONLY
            }
        }
    }
}

/**
 * 素材库导航模式：控制默认素材类型与企业分类的层级关系。
 *
 * 注意：
 * - UI 样式不随模式变化：第一行始终为高亮文字 + 下划线，第二行为圆角胶囊；
 * - 企业未配置任何分类时，不论这里如何设置，SDK 都自动回退为 [TYPE_ONLY]。
 */
enum class OemMaterialNavigationMode {
    TYPE_ONLY,
    TYPE_PRIMARY_CATEGORY_SECONDARY,
    CATEGORY_PRIMARY_TYPE_SECONDARY,
    CATEGORY_ONLY;

    companion object {
        fun parse(raw: String?): OemMaterialNavigationMode {
            return when (raw?.trim()?.uppercase(Locale.ROOT)) {
                "TYPE_PRIMARY_CATEGORY_SECONDARY",
                "TYPE_THEN_CATEGORY",
                "TYPE_PRIMARY" -> TYPE_PRIMARY_CATEGORY_SECONDARY
                "CATEGORY_PRIMARY_TYPE_SECONDARY",
                "CATEGORY_THEN_TYPE",
                "CATEGORY_PRIMARY" -> CATEGORY_PRIMARY_TYPE_SECONDARY
                "CATEGORY_ONLY",
                "ONLY_CATEGORY" -> CATEGORY_ONLY
                else -> TYPE_ONLY
            }
        }
    }
}

/**
 * AI 回复宿主回调请求中的单张截屏。
 *
 * @property imageBase64 截屏内容，JPEG 编码后的 Base64 字符串（不含 data: 前缀）
 * @property mimeType 图片 MIME 类型，固定为 image/jpeg
 * @property width 图片宽度（像素）
 * @property height 图片高度（像素）
 */
data class OemAiReplyScreenshot(
    val imageBase64: String,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * AI 回复宿主回调请求（SDK → 宿主）。
 *
 * 用户在键盘内触发「AI 回复」后，SDK 完成截屏与 Prompt 组装，
 * 将完整生成上下文通过本类交给宿主；宿主调用自己的大模型，
 * 再通过 [OemAiReplyResultCallback] 回传 [OemAiReplyResult]。
 *
 * @property requestId 本次生成请求唯一 ID，可用于幂等与日志串联
 * @property sessionId 本次 AI 回复面板会话 ID
 * @property generateMode 生成模式：FAST_LLM（快速回复）/ OCR（识屏回复）/
 *   IMAGE_REPLY（图片回复）/ PASTE（智能改写）。宿主可按模式差异化处理，
 *   也可以统一按截图 + Prompt 处理
 * @property toneKey 语气 key：friendly / concise / professional / formal
 * @property toneLabel 语气展示名
 * @property systemPrompt SDK 组装的系统提示词（宿主可直接透传给大模型，必填）
 * @property userPrompt SDK 组装的用户提示词（宿主可直接透传给大模型，必填）
 * @property ocrText 屏幕 OCR 文本（可能为空字符串；OCR 模式下为主要上下文）
 * @property screenshots 截屏列表（可能为空；FAST_LLM 模式可能包含多张滑动连拍帧）
 * @property replyCount 期望返回的回复条数（建议值，宿主可多可少）
 * @property temperature 采样参数 temperature（建议值）
 * @property topP 采样参数 topP（建议值）
 * @property maxTokens 最大 token 数（建议值）
 * @property timestamp 请求发起时间（毫秒时间戳）
 * @property hostApplicationId 宿主应用包名
 */
data class OemAiReplyRequest(
    val requestId: String,
    val sessionId: String,
    val generateMode: String,
    val toneKey: String,
    val toneLabel: String,
    val systemPrompt: String,
    val userPrompt: String,
    val ocrText: String,
    val screenshots: List<OemAiReplyScreenshot>,
    val replyCount: Int,
    val temperature: Double,
    val topP: Double,
    val maxTokens: Int,
    val timestamp: Long,
    val hostApplicationId: String,
)

/**
 * AI 回复宿主回调结果（宿主 → SDK）。
 *
 * @property requestId 必须与 [OemAiReplyRequest] 中的 requestId 保持一致
 * @property success 是否生成成功；为 false 时 SDK 使用 [errorMessage] 向用户提示失败
 * @property replies 大模型生成的回复内容列表（success=true 时必填）
 * @property errorMessage 失败原因（选填，success=false 时返回）
 * @property modelName 宿主侧模型名称（选填，用于面板展示与统计）
 * @property errorCode 失败状态码（选填），取值见 [OemAiReplyErrorCode]；
 *   宿主回传后 SDK 按状态码展示对应「非正常返回」状态与文案，未回传时由 SDK 依据 errorMessage 推断
 */
data class OemAiReplyResult(
    val requestId: String,
    val success: Boolean,
    val replies: List<String> = emptyList(),
    val errorMessage: String? = null,
    val modelName: String? = null,
    val errorCode: String? = null,
)

/**
 * AI 回复「非正常返回」状态码，用于错误状态分级与文案映射。
 * 宿主可通过 [OemAiReplyResult.errorCode] 回传，也可通过
 * [OemImeSdk.setAiReplyErrorTexts] 按状态码自定义上屏文案。
 */
object OemAiReplyErrorCode {
    /** 网络异常（断网 / 连接失败 / DNS / SSL） */
    const val NETWORK = "NETWORK"
    /** 生成超时（大模型请求超时 / 宿主回调超时） */
    const val TIMEOUT = "TIMEOUT"
    /** 大模型服务失败（429 限流 / 5xx / 调用异常） */
    const val MODEL_BUSY = "MODEL_BUSY"
    /** 命中大模型内容质检 / 安全审核 */
    const val CONTENT_BLOCKED = "CONTENT_BLOCKED"
    /** 返回内容异常（解析失败 / 数量不足 / 空结果） */
    const val RESULT_INVALID = "RESULT_INVALID"
    /** 配置缺失（模型配置不可用 / 宿主未注册回调 / SDK 未初始化） */
    const val CONFIG_MISSING = "CONFIG_MISSING"
    /** 聊天内容读取失败（截图 / 图片 / OCR 文本缺失） */
    const val CAPTURE_FAILED = "CAPTURE_FAILED"
    /** 未分类的其它失败 */
    const val UNKNOWN = "UNKNOWN"
}

/**
 * 状态码对应的自定义错误文案。title 非空时替换默认标题；
 * description 可选填，为空时使用默认描述。
 */
data class OemAiReplyErrorText(
    val title: String? = null,
    val description: String? = null,
)

/**
 * AI 回复结果回调。宿主完成大模型生成后必须调用一次本回调（无论成功或失败）；
 * 未调用时 SDK 将等待至超时，并判定本次宿主通道失败。
 */
fun interface OemAiReplyResultCallback {
    fun onResult(result: OemAiReplyResult)
}

/**
 * AI 回复宿主回调委托（宿主实现）。
 *
 * 典型实现：收到请求后把 screenshots 还原为图片（Base64 解码），
 * 连同 systemPrompt / userPrompt 一起调用企业自己的大模型网关，
 * 解析出回复文案列表后调用 callback.onResult 回传给 SDK。
 *
 * 返回值语义：
 * - true: 宿主已接受请求，稍后通过 callback 异步回传结果；
 * - false: 宿主拒绝处理（SDK 判定宿主通道不可用：
 *   HOST_ONLY 模式向用户提示失败，DUAL_WRITE 模式回落域名模式）。
 *
 * 抛出异常等价于返回 false。
 */
fun interface OemAiReplyHostDelegate {
    fun onGenerateReplies(request: OemAiReplyRequest, callback: OemAiReplyResultCallback): Boolean
}

/**
 * 自动化滑屏事件分发模式：控制"AI 回复-极速生成"等场景下自动连续滑屏的执行通道。
 *
 * - SDK_ONLY：仅 SDK 自身执行（无线 ADB / Stellar 特权进程 `input swipe`），不通知宿主；
 * - HOST_ONLY：仅宿主回调执行，SDK 将滑屏事件抽象为 [OemSwipeRequest] 通知 [OemSwipeHostDelegate]，
 *   宿主使用自有自动化能力（无障碍等）完成滑屏，并通过 [OemSwipeResultCallback] 回写结果；
 * - DUAL_WRITE：双写兜底（默认）。滑屏事件同时写入宿主回调与 SDK 自身执行通道：
 *   宿主已注册回调且接受本次滑屏时由宿主执行；宿主未注册、拒绝或超时未回写时，
 *   自动回落到 SDK 自身执行，保证极速生成连续滑屏不中断。
 *
 * 配置解析优先级（高→低）：
 * 1. [OemImeSdk.setSwipeDispatchMode] 运行时覆盖；
 * 2. [OemSdkConfig.swipeDispatchMode]（[OemImeSdk.initialize] 传入）；
 * 3. AndroidManifest meta-data [OemManifestMetaKeys.SWIPE_DISPATCH_MODE]；
 * 4. 默认 DUAL_WRITE。
 */
enum class OemSwipeDispatchMode {
    SDK_ONLY,
    HOST_ONLY,
    DUAL_WRITE;

    companion object {
        fun parse(raw: String?): OemSwipeDispatchMode {
            return when (raw?.trim()?.uppercase(Locale.ROOT)) {
                "SDK", "SDK_ONLY", "ADB" -> SDK_ONLY
                "HOST", "HOST_ONLY", "CALLBACK", "CALLBACK_ONLY" -> HOST_ONLY
                "DUAL", "DUAL_WRITE", "BOTH" -> DUAL_WRITE
                else -> DUAL_WRITE
            }
        }
    }
}

/**
 * 自动化滑屏事件抽象字段（SDK → 宿主）。
 *
 * 坐标语义与 `adb shell input swipe` 一致：从 (startX, startY) 拖动到 (endX, endY)，
 * 例如"内容向上翻页/读取更早聊天历史"为 startY 小于 endY 的向下拖动手势。
 * 宿主可依据绝对坐标执行，也可结合 [screenWidth]/[screenHeight] 换算为相对比例后
 * 使用自有自动化能力（无障碍手势等）执行。
 */
data class OemSwipeRequest(
    val requestId: String,
    val scene: String,
    val direction: String,
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val durationMs: Int,
    val timestamp: Long,
)

/** 宿主滑屏执行结果（宿主 → SDK）。 */
data class OemSwipeResult(
    val requestId: String,
    val success: Boolean,
    val errorMessage: String? = null,
)

/** 宿主滑屏结果回写回调：宿主完成滑屏后必须调用一次。 */
fun interface OemSwipeResultCallback {
    fun onResult(result: OemSwipeResult)
}

/**
 * 自动化滑屏宿主接管回调：SDK 通知宿主滑屏动作的定义与调用方式。
 * 返回 true 表示宿主接受并使用自有自动化能力执行本次滑屏，
 * 执行完成后必须通过 [OemSwipeResultCallback] 通知 SDK 滑动完成；
 * 返回 false 表示宿主不接管，SDK 按分发模式回落到自身执行通道。
 */
fun interface OemSwipeHostDelegate {
    fun onSwipeRequested(request: OemSwipeRequest, callback: OemSwipeResultCallback): Boolean
}

/**
 * 宿主宰图回调（宿主实现，自 1.0.0.20260824 起提供）。
 *
 * 面向具备系统级截屏能力的宿主（如 MDM 管控应用）：注册本回调后，
 * SDK 需要截图时直接调用 [captureScreen] 获取宿主截图，不再走
 * MediaProjection 授权，也不再受 FLAG_SECURE 限制。
 *
 * 语义约定：
 * - [captureScreen] 为同步方法，允许耗时较长（跨进程调用 / 权限申请等）；
 *   SDK 在后台线程等待宿主返回，期间不设超时、不会提前返回；
 * - 返回 Bitmap：SDK 正常使用；
 * - 返回 null：SDK 判定本次截图失败（抛出 CAPTURE_FAILED 类错误），
 *   不会再尝试 Automation / MediaProjection / 缓存截图等任何降级方式。
 */
fun interface OemScreenshotProvider {
    /** SDK 需要截图时调用，宿主同步返回截图 Bitmap；无法截图时返回 null。 */
    fun captureScreen(context: Context): Bitmap?
}

/**
 * 宿主回调企业素材的单个内容块（与服务端素材协议的 parts 一一对应）：
 * - [Text] 纯文本素材；
 * - [Attachment] 附件素材（图片 / 视频 / 文档，宿主提供下载地址）。
 */
sealed class OemRemoteMaterialPart {
    /**
     * 文本内容块。
     * @property partId 内容块 ID（素材内唯一，必填）
     * @property text 文本内容（必填）
     */
    data class Text(
        val partId: String,
        val text: String,
    ) : OemRemoteMaterialPart()

    /**
     * 附件内容块。
     * @property partId 内容块 ID（素材内唯一，必填）
     * @property displayName 附件展示名（必填）
     * @property mimeType MIME 类型，如 image/png、video/mp4、application/pdf
     * @property attachmentType 附件类别：IMAGE / VIDEO / DOCUMENT
     * @property downloadUrl 附件下载地址（必填；宿主可返回 https URL，
     *   也可返回自己 FileProvider 的 content:// URI）
     * @property fileSizeBytes 文件大小（字节，选填）
     */
    data class Attachment(
        val partId: String,
        val displayName: String,
        val mimeType: String,
        val attachmentType: String,
        val downloadUrl: String,
        val fileSizeBytes: Long = 0L,
    ) : OemRemoteMaterialPart()
}

/**
 * 宿主回调模式下的企业集中管理素材。
 *
 * @property materialId 素材唯一 ID（必填，用于增删改匹配）
 * @property sourceLabel 来源标签（默认「企业下发」）
 * @property createdAt 创建时间（毫秒时间戳）
 * @property updatedAt 最近更新时间（毫秒时间戳，抽屉按该字段倒序展示）
 * @property parts 素材内容块列表（建议至少一项）
 */
data class OemRemoteMaterialItem(
    val materialId: String,
    val sourceLabel: String = "企业下发",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val categoryId: String? = null,
    val categoryName: String? = null,
    val tags: List<String> = emptyList(),
    val parts: List<OemRemoteMaterialPart> = emptyList(),
)

/**
 * 企业素材库分类/标签条目（宿主 ↔ SDK）。
 *
 * 第一阶段仅面向第三方企业管理后台使用，SDK 侧用于：
 * - 首页双层导航中的“分类”展示；
 * - 搜索时的标签命中；
 * - 抽屉内分类/标签管理的宿主回调协议。
 */
data class OemMaterialTaxonomyItem(
    val taxonomyId: String,
    val name: String,
    val sort: Int = 0,
    val enabled: Boolean = true,
)

/**
 * 素材库分类/标签拉取请求（SDK → 宿主）。
 */
data class OemMaterialTaxonomyFetchRequest(
    val requestId: String,
    val hostApplicationId: String,
    val timestamp: Long,
)

/**
 * 素材库分类/标签拉取结果（宿主 → SDK）。
 */
data class OemMaterialTaxonomyFetchResult(
    val requestId: String,
    val success: Boolean,
    val categories: List<OemMaterialTaxonomyItem> = emptyList(),
    val tags: List<OemMaterialTaxonomyItem> = emptyList(),
    val errorMessage: String? = null,
)

fun interface OemMaterialTaxonomyFetchCallback {
    fun onResult(result: OemMaterialTaxonomyFetchResult)
}

enum class OemMaterialTaxonomyTarget {
    CATEGORY,
    TAG,
}

enum class OemMaterialTaxonomyMutationAction {
    CREATE,
    UPDATE,
    DELETE,
}

/**
 * 素材库分类/标签变更请求（SDK → 宿主）。
 *
 * - CREATE / UPDATE：传入 [item]；
 * - DELETE：传入 [taxonomyId]；
 * 第一阶段仅用于企业后台管理自身分类/标签，不涉及素材类型。
 */
data class OemMaterialTaxonomyMutationRequest(
    val requestId: String,
    val action: OemMaterialTaxonomyMutationAction,
    val target: OemMaterialTaxonomyTarget,
    val hostApplicationId: String,
    val timestamp: Long,
    val item: OemMaterialTaxonomyItem? = null,
    val taxonomyId: String? = null,
)

/**
 * 素材库分类/标签变更结果（宿主 → SDK）。
 */
data class OemMaterialTaxonomyMutationResult(
    val requestId: String,
    val success: Boolean,
    val item: OemMaterialTaxonomyItem? = null,
    val errorMessage: String? = null,
)

fun interface OemMaterialTaxonomyMutationCallback {
    fun onResult(result: OemMaterialTaxonomyMutationResult)
}

/**
 * 素材库宿主回调拉取请求（SDK → 宿主）。
 *
 * @property requestId 本次请求唯一 ID
 * @property limit 期望拉取的素材条数（建议上限，宿主可多可少）
 * @property hostApplicationId 宿主应用包名
 * @property timestamp 请求发起时间（毫秒时间戳）
 */
data class OemMaterialFetchRequest(
    val requestId: String,
    val limit: Int,
    val hostApplicationId: String,
    val timestamp: Long,
)

/**
 * 素材库宿主回调拉取结果（宿主 → SDK）。
 *
 * @property requestId 必须与 [OemMaterialFetchRequest] 中的 requestId 保持一致
 * @property success 是否拉取成功；为 false 时 SDK 使用 [errorMessage] 记录失败
 * @property materials 最新企业素材列表（全量语义，SDK 会用该列表整体替换本地缓存）
 * @property errorMessage 失败原因（选填，success=false 时返回）
 */
data class OemMaterialFetchResult(
    val requestId: String,
    val success: Boolean,
    val materials: List<OemRemoteMaterialItem> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * 素材库拉取结果回调。宿主完成素材列表查询后必须调用一次本回调。
 */
fun interface OemMaterialFetchCallback {
    fun onResult(result: OemMaterialFetchResult)
}

/**
 * 素材库宿主回调委托（宿主实现）。
 *
 * HOST_ONLY / DUAL_WRITE 模式下，SDK 需要刷新企业素材（抽屉打开、周期同步等）
 * 时会调用本回调请求最新素材列表。
 *
 * 返回值语义与 [OemAiReplyHostDelegate] 一致：
 * true 表示已接受并稍后通过 callback 回传；false 表示拒绝处理。
 */
fun interface OemMaterialHostDelegate {
    fun fetchRemoteMaterials(request: OemMaterialFetchRequest, callback: OemMaterialFetchCallback): Boolean
}

/**
 * 素材库分类/标签宿主回调委托（宿主实现）。
 *
 * 第一阶段约定：
 * - 分类支持在抽屉内做增删改查；
 * - 标签不作为导航，只作为搜索命中条件与管理对象；
 * - 素材类型始终由 SDK 内部定义，宿主只管理分类和标签。
 */
interface OemMaterialTaxonomyHostDelegate {
    fun fetchMaterialTaxonomy(
        request: OemMaterialTaxonomyFetchRequest,
        callback: OemMaterialTaxonomyFetchCallback,
    ): Boolean

    fun mutateMaterialTaxonomy(
        request: OemMaterialTaxonomyMutationRequest,
        callback: OemMaterialTaxonomyMutationCallback,
    ): Boolean
}

/**
 * 企业素材变更事件（宿主推送 → SDK），对应服务端增删改查语义：
 * - [Upsert] 新增或更新一条素材（增 / 改）；
 * - [Remove] 删除一条素材（删）；
 * - [Invalidate] 全量失效，SDK 将重新通过 [OemMaterialHostDelegate] 拉取列表（查）。
 */
sealed class OemRemoteMaterialCatalogEvent {
    data class Upsert(val item: OemRemoteMaterialItem) : OemRemoteMaterialCatalogEvent()
    data class Remove(val materialId: String) : OemRemoteMaterialCatalogEvent()
    data object Invalidate : OemRemoteMaterialCatalogEvent()
}

/**
 * 素材变更监听器，SDK 内部用于接收宿主推送的事件；
 * 企业集成方无需实现本接口，改用
 * [OemImeSdk.notifyRemoteMaterialUpserted] / [OemImeSdk.notifyRemoteMaterialRemoved] /
 * [OemImeSdk.invalidateRemoteMaterialCatalog] 推送即可。
 */
fun interface OemRemoteMaterialCatalogListener {
    fun onCatalogChanged(event: OemRemoteMaterialCatalogEvent)
}

/**
 * AI 回复 Prompt 组装上下文（SDK → 宿主 Prompt 组装器）。
 *
 * 宿主实现 [OemAiReplyPromptAssembler] 后，SDK 在每次 AI 回复生成前
 * 把当前截屏 / OCR / 语气等上下文交给宿主，由宿主决定最终发给大模型的
 * systemPrompt 与 userPrompt，实现「Prompt 与语气预留宿主自定义」。
 *
 * 全部字段均为只读上下文，必填。
 */
data class OemAiReplyPromptContext(
    /** 生成模式：FAST_LLM 极速回复 / OCR 读屏回复 / IMAGE_REPLY 图片回复 / PASTE 智能改写。 */
    val generateMode: String,
    /** 用户在面板选择的语气 key（与语气列表下标对应，如 tone_0）。 */
    val toneKey: String,
    /** 用户在面板选择的语气展示名（来自宿主自定义语气预设或 SDK 默认）。 */
    val toneLabel: String,
    /** 当前语气的 Prompt 提示语（来自语气预设 promptHint，宿主自定义语气时由宿主提供）。 */
    val tonePromptHint: String,
    /** 屏幕 OCR 文本（可能为空字符串）。 */
    val ocrText: String,
    /** 本次可用的截屏张数（可能为 0）。 */
    val screenshotCount: Int,
    /** 期望回复条数（建议值）。 */
    val replyCount: Int,
    /** 宿主应用包名。 */
    val hostApplicationId: String,
)

/**
 * 宿主组装出的 AI 回复 Prompt 对（宿主 → SDK）。
 *
 * 两个字段均必填且不能为空白；返回空白时 SDK 将回退内置模板。
 */
data class OemAiReplyPromptPair(
    /** 系统提示词：定义大模型角色与输出约束，由宿主按企业业务自定义。 */
    val systemPrompt: String,
    /** 用户提示词：包含本次截屏 / OCR 上下文与语气要求，由宿主按企业业务自定义。 */
    val userPrompt: String,
)

/**
 * AI 回复 Prompt 宿主组装器（选填契约）。
 *
 * 宿主在 [OemSdkConfig.aiReplyPromptAssembler] 或
 * [OemImeSdk.setAiReplyPromptAssembler] 中注册后，SDK 不再使用内置
 * Prompt 模板，而是完全委托宿主组装 systemPrompt / userPrompt；
 * 未注册时 SDK 使用内置模板（内置模板同样尊重宿主自定义语气与系统提示词）。
 */
fun interface OemAiReplyPromptAssembler {
    fun assemble(context: OemAiReplyPromptContext): OemAiReplyPromptPair
}

enum class OemCapability(val manifestValue: String) {
    AI("ai"),
    AUTOMATION("automation"),
    UI("ui"),
    MATERIAL_LIBRARY("material_library"),
    DEVELOPER("developer");

    companion object {
        fun parseCsv(raw: String?): Set<OemCapability> {
            if (raw.isNullOrBlank()) return emptySet()
            return raw.split(',')
                .mapNotNull { item ->
                    entries.firstOrNull { it.manifestValue == item.trim().lowercase(Locale.ROOT) }
                }
                .toSet()
        }
    }
}

enum class OemLicenseStatus(val allowsRuntime: Boolean) {
    ACTIVE(true),
    GRACE(true),
    EXPIRED(false),
    DISABLED(false),
    FULL(false),
    NOT_OPENED(false),
    MISSING(false),
    INVALID(false);
}

data class OemHostIdentity(
    val applicationId: String,
    val certSha256: String,
    val versionName: String,
    val versionCode: Long,
)

data class OemLicenseRequest(
    val customerId: String,
    val appKey: String,
    val channel: String,
    val environment: OemEnvironment,
    val hostIdentity: OemHostIdentity,
    val sdkVersion: String,
    val requestedCapabilities: Set<OemCapability>,
    val hostUserIdHash: String?,
)

data class OemLicenseSnapshot(
    val status: OemLicenseStatus,
    val licenseId: String? = null,
    val message: String = "",
    val expiresAtMillis: Long? = null,
    val grantedCapabilities: Set<OemCapability> = emptySet(),
) {
    fun resolvedCapabilities(requested: Set<OemCapability>): Set<OemCapability> {
        if (!status.allowsRuntime) return emptySet()
        return if (grantedCapabilities.isEmpty()) requested else requested.intersect(grantedCapabilities)
    }

    companion object {
        fun active(
            licenseId: String? = null,
            message: String = "active",
            grantedCapabilities: Set<OemCapability> = emptySet(),
            expiresAtMillis: Long? = null,
        ) = OemLicenseSnapshot(
            status = OemLicenseStatus.ACTIVE,
            licenseId = licenseId,
            message = message,
            expiresAtMillis = expiresAtMillis,
            grantedCapabilities = grantedCapabilities,
        )

        fun grace(
            licenseId: String? = null,
            message: String = "grace",
            grantedCapabilities: Set<OemCapability> = emptySet(),
            expiresAtMillis: Long? = null,
        ) = OemLicenseSnapshot(
            status = OemLicenseStatus.GRACE,
            licenseId = licenseId,
            message = message,
            expiresAtMillis = expiresAtMillis,
            grantedCapabilities = grantedCapabilities,
        )

        fun missing(message: String = "license missing") = OemLicenseSnapshot(
            status = OemLicenseStatus.MISSING,
            message = message,
        )

        fun full(message: String = "license full") = OemLicenseSnapshot(
            status = OemLicenseStatus.FULL,
            message = message,
        )

        fun notOpened(message: String = "license not opened") = OemLicenseSnapshot(
            status = OemLicenseStatus.NOT_OPENED,
            message = message,
        )

        fun invalid(message: String) = OemLicenseSnapshot(
            status = OemLicenseStatus.INVALID,
            message = message,
        )
    }
}

fun interface OemLicenseProvider {
    fun fetchLicense(context: Context, request: OemLicenseRequest): OemLicenseSnapshot
}

data class OemManifestConfig(
    val customerId: String?,
    val appKey: String?,
    val channel: String?,
    val environment: OemEnvironment,
    val enableAi: Boolean,
    val enableAutomation: Boolean,
    val enableAutomationSettingsEntry: Boolean,
    val enableUi: Boolean,
    val apiBaseUrl: String?,
    val analyticsBaseUrl: String?,
    val aiMode: OemAiMode,
    val aiOpenAiBaseUrl: String?,
    val aiOpenAiApiKey: String?,
    val aiOpenAiModel: String?,
    val sessionArchiveMode: OemSessionArchiveMode,
    val aiReplyDispatchMode: OemAiReplyDispatchMode,
    val materialDispatchMode: OemMaterialDispatchMode,
    val materialNavigationMode: OemMaterialNavigationMode,
    val swipeDispatchMode: OemSwipeDispatchMode,
    val featureToggles: OemFeatureToggles,
    val enableDebugLog: Boolean,
    val aiReplyMaxImageSelection: Int?,
    val sendTextMaxChars: Int?,
    val builtinNeonOverlayEnabled: Boolean?,
    val ocrEnabled: Boolean?,
) {
    companion object {
        const val DEFAULT_CHANNEL = "release"
    }

    fun toSdkConfig(
        licenseProvider: OemLicenseProvider,
        hostUserIdHash: String? = null,
        autoInitializeAiRuntime: Boolean = true,
        initializeLogCollector: Boolean = true,
        sessionArchiveListener: OemSessionArchiveListener? = null,
        aiReplyHostDelegate: OemAiReplyHostDelegate? = null,
        materialHostDelegate: OemMaterialHostDelegate? = null,
        materialTaxonomyHostDelegate: OemMaterialTaxonomyHostDelegate? = null,
        swipeHostDelegate: OemSwipeHostDelegate? = null,
        aiReplyTonePresets: List<OemDeveloperAiTonePreset>? = null,
        aiReplySystemPrompt: String? = null,
        aiReplyPromptAssembler: OemAiReplyPromptAssembler? = null,
    ): OemSdkConfig {
        return OemSdkConfig(
            customerId = customerId?.trim().orEmpty(),
            appKey = appKey?.trim().orEmpty(),
            channel = channel?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_CHANNEL,
            environment = environment,
            licenseProvider = licenseProvider,
            enableAi = enableAi,
            enableAutomation = enableAutomation,
            enableAutomationSettingsEntry = enableAutomationSettingsEntry,
            enableUi = enableUi,
            apiBaseUrl = apiBaseUrl,
            analyticsBaseUrl = analyticsBaseUrl,
            aiMode = aiMode,
            aiOpenAiBaseUrl = aiOpenAiBaseUrl,
            aiOpenAiApiKey = aiOpenAiApiKey,
            aiOpenAiModel = aiOpenAiModel,
            hostUserIdHash = hostUserIdHash,
            autoInitializeAiRuntime = autoInitializeAiRuntime,
            initializeLogCollector = initializeLogCollector,
            enableDebugLog = enableDebugLog,
            sessionArchiveMode = sessionArchiveMode,
            aiReplyDispatchMode = aiReplyDispatchMode,
            materialDispatchMode = materialDispatchMode,
            materialNavigationMode = materialNavigationMode,
            swipeDispatchMode = swipeDispatchMode,
            swipeHostDelegate = swipeHostDelegate,
            featureToggles = featureToggles,
            sessionArchiveListener = sessionArchiveListener,
            aiReplyHostDelegate = aiReplyHostDelegate,
            materialHostDelegate = materialHostDelegate,
            materialTaxonomyHostDelegate = materialTaxonomyHostDelegate,
            aiReplyTonePresets = aiReplyTonePresets,
            aiReplySystemPrompt = aiReplySystemPrompt,
            aiReplyPromptAssembler = aiReplyPromptAssembler,
            aiReplyMaxImageSelection = aiReplyMaxImageSelection,
            sendTextMaxChars = sendTextMaxChars,
            builtinNeonOverlayEnabled = builtinNeonOverlayEnabled,
            ocrEnabled = ocrEnabled,
        )
    }
}

object OemManifestMetaKeys {
    /** 企业客户 ID，正式接入时由我方交付 */
    const val CUSTOMER_ID = "com.paopaoime.oem.customerId"
    /** 企业 AppKey，正式接入时由我方交付 */
    const val APP_KEY = "com.paopaoime.oem.appKey"
    /** 渠道标识，用于区分发布渠道 */
    const val CHANNEL = "com.paopaoime.oem.channel"
    /** 环境标识，支持 prod / staging / dev */
    const val ENVIRONMENT = "com.paopaoime.oem.environment"
    /** 是否启用 AI 能力总开关 */
    const val ENABLE_AI = "com.paopaoime.oem.enableAi"
    /** 是否启用自动化能力总开关 */
    const val ENABLE_AUTOMATION = "com.paopaoime.oem.enableAutomation"
    /** 是否显示自动化设置入口，默认关闭 */
    const val ENABLE_AUTOMATION_SETTINGS_ENTRY = "com.paopaoime.oem.enableAutomationSettingsEntry"
    /** 是否启用 UI 能力总开关 */
    const val ENABLE_UI = "com.paopaoime.oem.enableUi"
    /** 企业服务端基础域名 */
    const val API_BASE_URL = "com.paopaoime.oem.apiBaseUrl"
    /** 席位激活上报通道域名（选填，仅私有化部署覆盖用）；缺省走 core 内置加密官方通道，与 apiBaseUrl 完全无关 */
    const val ANALYTICS_BASE_URL = "com.paopaoime.oem.analyticsBaseUrl"
    /** AI 运行模式，支持 proxy / direct / dual */
    const val AI_MODE = "com.paopaoime.oem.aiMode"
    /** 直连 OpenAI 兼容接口的基础地址 */
    const val AI_OPENAI_BASE_URL = "com.paopaoime.oem.aiOpenAiBaseUrl"
    /** 直连 OpenAI 兼容接口的密钥 */
    const val AI_OPENAI_API_KEY = "com.paopaoime.oem.aiOpenAiApiKey"
    /** 直连 OpenAI 兼容接口的模型名 */
    const val AI_OPENAI_MODEL = "com.paopaoime.oem.aiOpenAiModel"
    /** 输入质检会话分发模式 */
    const val SESSION_ARCHIVE_MODE = "com.paopaoime.oem.sessionArchiveMode"
    /**
     * AI 回复分发模式（选填）。
     * 可选值：CLOUD_ONLY（域名配置，默认）/ HOST_ONLY（宿主回调）/ DUAL_WRITE（双通道容错）。
     * 选择 HOST_ONLY 或 DUAL_WRITE 时，宿主必须注册 [OemAiReplyHostDelegate] 回调。
     */
    const val AI_REPLY_DISPATCH_MODE = "com.paopaoime.oem.aiReplyDispatchMode"
    /**
     * 素材库分发模式（选填）。
     * 可选值：LOCAL_ONLY（仅本地素材）/ CLOUD_ONLY（域名配置，默认）/
     * HOST_ONLY（宿主回调）/ DUAL_WRITE（双通道容错）。
     * 选择 HOST_ONLY 或 DUAL_WRITE 时，宿主必须注册 [OemMaterialHostDelegate] 回调。
     */
    const val MATERIAL_DISPATCH_MODE = "com.paopaoime.oem.materialDispatchMode"
    /**
     * 素材库导航模式（选填）。
     * 可选值：
     * - TYPE_ONLY（默认，仅显示素材类型）；
     * - TYPE_PRIMARY_CATEGORY_SECONDARY（类型一级、分类二级）；
     * - CATEGORY_PRIMARY_TYPE_SECONDARY（分类一级、类型二级）；
     * - CATEGORY_ONLY（仅显示分类，不显示类型）。
     *
     * 当宿主没有返回任何分类时，SDK 会自动回退到 TYPE_ONLY。
     */
    const val MATERIAL_NAVIGATION_MODE = "com.paopaoime.oem.materialNavigationMode"
    /**
     * 自动化滑屏分发模式（选填）。
     * 可选值：SDK_ONLY（SDK 自身 ADB 执行）/ HOST_ONLY（宿主回调执行）/
     * DUAL_WRITE（宿主优先+SDK 兜底，默认）。
     * 选择 HOST_ONLY 或 DUAL_WRITE 时，宿主可注册 [OemSwipeHostDelegate] 回调，
     * 使用自有自动化能力（无障碍等）执行极速生成的连续滑屏。
     */
    const val SWIPE_DISPATCH_MODE = "com.paopaoime.oem.swipeDispatchMode"
    /** 是否启用 AI 回复功能 */
    const val ENABLE_AI_REPLY = "com.paopaoime.oem.enableAiReply"
    /** 是否启用素材库功能 */
    const val ENABLE_MATERIAL_LIBRARY = "com.paopaoime.oem.enableMaterialLibrary"
    /** AI 回复自定义 UI 交互开关（DrawerKit L1），默认关闭 */
    const val ENABLE_AI_REPLY_CUSTOM_UI = "com.paopaoime.oem.enableAiReplyCustomUi"
    /** 素材库自定义 UI 交互开关（DrawerKit L1），默认关闭 */
    const val ENABLE_MATERIAL_LIBRARY_CUSTOM_UI = "com.paopaoime.oem.enableMaterialLibraryCustomUi"
    /** 用户自定义抽屉开关（DrawerKit L2），默认关闭 */
    const val ENABLE_CUSTOM_DRAWERS = "com.paopaoime.oem.enableCustomDrawers"
    /** 是否启用企业质检会话功能 */
    const val ENABLE_QA_SESSION = "com.paopaoime.oem.enableQaSession"
    /** AI 回复大模型请求超时（毫秒，选填，默认 30000） */
    const val AI_REPLY_GENERATION_TIMEOUT_MS = "com.paopaoime.oem.aiReplyGenerationTimeoutMs"
    /** AI 回复网络连接超时（毫秒，选填，默认 30000） */
    const val AI_REPLY_NETWORK_TIMEOUT_MS = "com.paopaoime.oem.aiReplyNetworkTimeoutMs"
    /** AI 回复错误文案宿主自定义开关（选填，默认 false） */
    const val AI_REPLY_ERROR_TEXT_CUSTOM_ENABLED = "com.paopaoime.oem.aiReplyErrorTextCustomEnabled"
    /** AI 回复传图回复最多可选图片数量（选填，默认 5，有效范围 1..20） */
    const val AI_REPLY_MAX_IMAGE_SELECTION = "com.paopaoime.oem.aiReplyMaxImageSelection"
    /** AI 回复与素材库单次文本发送最大字符数（选填，默认 1000） */
    const val SEND_TEXT_MAX_CHARS = "com.paopaoime.oem.sendTextMaxChars"
    /** 是否启用 SDK 内置 AI 回复霓虹边框/后台接管层（选填，默认 false） */
    const val BUILTIN_NEON_OVERLAY_ENABLED = "com.paopaoime.oem.builtinNeonOverlayEnabled"
    /** 是否启用 SDK 内置 OCR（选填，默认 true）；低配机或宿主自管 OCR 时可关闭 */
    const val OCR_ENABLED = "com.paopaoime.oem.ocrEnabled"
    /** 是否启用开发者模块（模块化交付新 key，未配置时默认 false） */
    const val ENABLE_DEVELOPER = "com.paopaoime.oem.enable_developer"
    /** 旧版开发者入口开关，仅为兼容宿主已有配置保留，优先级低于 ENABLE_DEVELOPER */
    const val ENABLE_DEVELOPER_ENTRY_LEGACY = "com.paopaoime.oem.enable_developer_entry"
    /** 集成调试日志总开关（日志桥接），默认关闭；开启后 SDK 通过 [OemLog] 向 logcat 与宿主接收器汇报关键链路日志 */
    const val ENABLE_DEBUG_LOG = "com.paopaoime.oem.enableDebugLog"
}

data class OemSdkConfig(
    val customerId: String,
    val appKey: String,
    val channel: String,
    val environment: OemEnvironment = OemEnvironment.PROD,
    val licenseProvider: OemLicenseProvider,
    val enableAi: Boolean = false,
    val enableAutomation: Boolean = false,
    val enableAutomationSettingsEntry: Boolean = false,
    val enableUi: Boolean = true,
    val apiBaseUrl: String? = null,
    val analyticsBaseUrl: String? = null,
    val aiMode: OemAiMode = OemAiMode.PROXY,
    val aiOpenAiBaseUrl: String? = null,
    val aiOpenAiApiKey: String? = null,
    val aiOpenAiModel: String? = null,
    val hostUserIdHash: String? = null,
    val autoInitializeAiRuntime: Boolean = true,
    val initializeLogCollector: Boolean = true,
    /** 集成调试日志总开关（默认关闭）。开启后 SDK 通过 [OemLog] 汇报关键链路日志。 */
    val enableDebugLog: Boolean = false,
    val sessionArchiveMode: OemSessionArchiveMode = OemSessionArchiveMode.CLOUD_ONLY,
    /**
     * AI 回复分发模式（选填，默认 [OemAiReplyDispatchMode.CLOUD_ONLY]）。
     * 设为 HOST_ONLY / DUAL_WRITE 时必须同时提供 [aiReplyHostDelegate]，
     * 或稍后通过 [OemImeSdk.setAiReplyHostDelegate] 注册。
     */
    val aiReplyDispatchMode: OemAiReplyDispatchMode = OemAiReplyDispatchMode.CLOUD_ONLY,
    /**
     * 素材库分发模式（选填，默认 [OemMaterialDispatchMode.CLOUD_ONLY]）。
     * 设为 HOST_ONLY / DUAL_WRITE 时必须同时提供 [materialHostDelegate]，
     * 或稍后通过 [OemImeSdk.setMaterialHostDelegate] 注册。
     */
    val materialDispatchMode: OemMaterialDispatchMode = OemMaterialDispatchMode.CLOUD_ONLY,
    /**
     * 素材库导航模式（选填，默认 [OemMaterialNavigationMode.TYPE_ONLY]）。
     * 仅控制“素材类型”和“企业分类”的层级关系，不影响 UI 样式。
     * 支持仅显示分类，不显示类型。
     */
    val materialNavigationMode: OemMaterialNavigationMode = OemMaterialNavigationMode.TYPE_ONLY,
    /**
     * 自动化滑屏分发模式（选填，默认 [OemSwipeDispatchMode.DUAL_WRITE]）。
     * 控制极速生成连续滑屏的执行通道，详见 [OemSwipeDispatchMode]。
     */
    val swipeDispatchMode: OemSwipeDispatchMode = OemSwipeDispatchMode.DUAL_WRITE,
    /** 自动化滑屏宿主接管回调，宿主使用自有自动化能力执行滑屏。 */
    val swipeHostDelegate: OemSwipeHostDelegate? = null,
    val featureToggles: OemFeatureToggles = OemFeatureToggles(),
    val sessionArchiveListener: OemSessionArchiveListener? = null,
    /**
     * AI 回复宿主回调委托（选填）。宿主实现后，SDK 触发 AI 回复时会把
     * 截屏与 Prompt 交给宿主，由宿主调用自己的大模型并回传生成结果。
     */
    val aiReplyHostDelegate: OemAiReplyHostDelegate? = null,
    /**
     * 素材库宿主回调委托（选填）。宿主实现后，SDK 拉取企业素材时改为
     * 向宿主请求素材列表；素材增删改另见 [OemImeSdk.notifyRemoteMaterialUpserted] 等推送接口。
     */
    val materialHostDelegate: OemMaterialHostDelegate? = null,
    /**
     * 素材库分类/标签宿主回调委托（选填）。
     * 宿主实现后，SDK 可向宿主拉取分类/标签列表，并把抽屉中的分类/标签增删改请求回调给宿主。
     */
    val materialTaxonomyHostDelegate: OemMaterialTaxonomyHostDelegate? = null,
    /**
     * 宿主自定义 AI 回复语气预设列表（选填）。
     * 为 null 或空时按「开发者模块配置 → SDK 内置语气」回退；
     * 自定义后 AI 回复面板的语气选项、以及 [OemAiReplyPromptContext.toneLabel] /
     * [OemAiReplyPromptContext.tonePromptHint] 均使用宿主预设。
     */
    val aiReplyTonePresets: List<OemDeveloperAiTonePreset>? = null,
    /**
     * 宿主自定义 AI 回复系统提示词（选填）。
     * 为 null 或空白时按「开发者模块配置 → SDK 内置系统提示词」回退。
     */
    val aiReplySystemPrompt: String? = null,
    /**
     * 宿主自定义 AI 回复 Prompt 组装器（选填）。
     * 注册后 SDK 完全委托宿主组装 systemPrompt / userPrompt；
     * 未注册时 SDK 使用内置模板（内置模板同样尊重 [aiReplyTonePresets] / [aiReplySystemPrompt]）。
     */
    val aiReplyPromptAssembler: OemAiReplyPromptAssembler? = null,
    /**
     * AI 回复大模型请求超时（毫秒，选填）。
     * 为 null 时按「manifest 配置 → 默认 30000」回退；同时作用于云端直连与宿主回调通道。
     */
    val aiReplyGenerationTimeoutMs: Long? = null,
    /**
     * AI 回复网络连接超时（毫秒，选填）。
     * 为 null 时按「manifest 配置 → 默认 30000」回退。
     */
    val aiReplyNetworkTimeoutMs: Long? = null,
    /**
     * AI 回复错误文案宿主自定义开关（默认关闭）。
     * 开启后 SDK 优先使用 [aiReplyErrorTexts] 中按状态码配置的标题/描述；
     * 关闭时始终使用 SDK 默认文案（标题「这次生成没有成功」，描述「请点击下方按钮重新生成」）。
     */
    val aiReplyErrorTextCustomEnabled: Boolean = false,
    /**
     * 宿主自定义错误文案，key 为 [OemAiReplyErrorCode] 状态码（选填）。
     * title 非空时替换默认标题；description 可选填，为空时使用默认描述。
     */
    val aiReplyErrorTexts: Map<String, OemAiReplyErrorText>? = null,
    /**
     * AI 回复「传图回复」最多可选图片数量（选填）。
     * 为 null 时按 Manifest 配置或 SDK 默认 5 回退；有效值会在使用侧收敛到 1..20。
     */
    val aiReplyMaxImageSelection: Int? = null,
    /**
     * AI 回复与素材库单次文本发送最大字符数（选填）。
     * 为 null 时按 Manifest 配置或 SDK 默认 1000 回退；传入正数后运行时立即生效。
     */
    val sendTextMaxChars: Int? = null,
    /**
     * 是否启用 SDK 内置 AI 回复霓虹边框（选填）。
     * 为 null 时按 Manifest 配置或默认 false 回退；宿主自管视觉反馈时保持关闭。
     */
    val builtinNeonOverlayEnabled: Boolean? = null,
    /** 结构化诊断事件接收器（选填），用于线上排障。 */
    val diagnosticListener: OemDiagnosticListener? = null,
    /** AI 回复生成生命周期监听器（选填），用于宿主自管生成中视觉反馈。 */
    val aiReplyLifecycleListener: OemAiReplyLifecycleListener? = null,
    /** 是否启用 SDK 内置 OCR。为 null 时按 Manifest 或默认 true 回退。 */
    val ocrEnabled: Boolean? = null,
) {
    init {
        require(customerId.isNotBlank()) { "customerId must not be blank" }
        require(appKey.isNotBlank()) { "appKey must not be blank" }
        require(channel.isNotBlank()) { "channel must not be blank" }
    }

    val requestedCapabilities: Set<OemCapability>
        get() = buildSet {
            if (enableAi) add(OemCapability.AI)
            if (enableAutomation) add(OemCapability.AUTOMATION)
            if (enableUi) add(OemCapability.UI)
        }
}

data class OemSdkStatus(
    val initialized: Boolean,
    val sdkVersion: String,
    val customerId: String,
    val channel: String,
    val environment: OemEnvironment,
    val hostIdentity: OemHostIdentity,
    val requestedCapabilities: Set<OemCapability>,
    val availableCapabilities: Set<OemCapability>,
    val license: OemLicenseSnapshot,
) {
    fun isCapabilityAvailable(capability: OemCapability): Boolean = capability in availableCapabilities
}

class OemSdkNotInitializedException : IllegalStateException("OemImeSdk has not been initialized")

enum class OemLicenseRefreshApplyMode {
    IMMEDIATE,
    DEFER_UNTIL_NEXT_USE,
}

object OemImeSdk {
    private const val TAG = "OemImeSdk"

    /**
     * 宿主滑屏结果回写等待的默认超时（毫秒）。
     *
     * 宿主的无障碍/系统级手势注入结果可能经跨进程通道回传，实测延迟可达 10s 级别，
     * 因此默认值预留充足余量；宿主可按实际链路通过 [setSwipeHostResultTimeoutMillis] 调整。
     */
    const val DEFAULT_SWIPE_HOST_RESULT_TIMEOUT_MS = 15_000L

    /** 宿主滑屏结果回写超时的最小允许值（毫秒），防止配置为 0/负数导致宿主永远无法回写。 */
    const val MIN_SWIPE_HOST_RESULT_TIMEOUT_MS = 1_000L

    private const val LICENSE_PREFS_NAME = "oem_runtime_license"
    private const val KEY_LICENSE_STATUS = "cached_license_status"
    private const val KEY_LICENSE_ID = "cached_license_id"
    private const val KEY_LICENSE_MESSAGE = "cached_license_message"
    private const val KEY_LICENSE_EXPIRES_AT = "cached_license_expires_at"
    private const val KEY_LICENSE_GRANTED = "cached_license_granted"
    private const val KEY_LICENSE_CACHED_AT = "cached_license_cached_at"
    private const val KEY_LICENSE_CUSTOMER_ID = "cached_license_customer_id"
    private const val KEY_LICENSE_CHANNEL = "cached_license_channel"
    private const val KEY_LICENSE_HOST_APP = "cached_license_host_app"
    private const val LICENSE_GRACE_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
    private const val UI_RUNTIME_PREFS_NAME = "com.paopaoime.oem.ui_runtime_config"
    private const val KEY_AI_REPLY_MAX_IMAGE_SELECTION = "ai_reply_max_image_selection"
    private val lock = Any()
    private val refreshExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "oem-license-refresh").apply { isDaemon = true }
    }

    @Volatile
    private var runtimeState: OemRuntimeState? = null

    @Volatile
    private var pendingStatus: OemSdkStatus? = null

    @Volatile
    private var refreshInFlight = false

    @Volatile
    private var lastRefreshDispatchAtElapsedMs = 0L

    @Volatile
    private var sessionArchiveListener: OemSessionArchiveListener? = null

    @Volatile
    private var diagnosticListener: OemDiagnosticListener? = null

    @Volatile
    private var aiReplyLifecycleListener: OemAiReplyLifecycleListener? = null

    @Volatile
    private var aiReplyHostDelegate: OemAiReplyHostDelegate? = null

    @Volatile
    private var materialHostDelegate: OemMaterialHostDelegate? = null

    @Volatile
    private var materialTaxonomyHostDelegate: OemMaterialTaxonomyHostDelegate? = null

    @Volatile
    private var swipeHostDelegate: OemSwipeHostDelegate? = null

    @Volatile
    private var swipeDispatchModeOverride: OemSwipeDispatchMode? = null

    @Volatile
    private var swipeHostResultTimeoutMs: Long = DEFAULT_SWIPE_HOST_RESULT_TIMEOUT_MS

    @Volatile
    private var screenshotProvider: OemScreenshotProvider? = null

    @Volatile
    private var aiReplyDispatchModeOverride: OemAiReplyDispatchMode? = null

    @Volatile
    private var materialDispatchModeOverride: OemMaterialDispatchMode? = null

    @Volatile
    private var materialNavigationModeOverride: OemMaterialNavigationMode? = null

    @Volatile
    private var featureToggleOverrides: OemFeatureToggleOverrides? = null

    @Volatile
    private var remoteMaterialCatalogListener: OemRemoteMaterialCatalogListener? = null

    @Volatile
    private var aiReplyTonePresets: List<OemDeveloperAiTonePreset>? = null

    @Volatile
    private var aiReplySystemPrompt: String? = null

    @Volatile
    private var aiReplyPromptAssembler: OemAiReplyPromptAssembler? = null

    @Volatile
    private var aiReplyGenerationTimeoutMsOverride: Long? = null

    @Volatile
    private var aiReplyNetworkTimeoutMsOverride: Long? = null

    @Volatile
    private var aiReplyErrorTextCustomEnabledOverride: Boolean? = null

    @Volatile
    private var aiReplyErrorTexts: Map<String, OemAiReplyErrorText>? = null

    @Volatile
    private var aiReplyMaxImageSelectionOverride: Int? = null

    @Volatile
    private var sendTextMaxCharsOverride: Int? = null

    @Volatile
    private var builtinNeonOverlayEnabledOverride: Boolean? = null

    fun initialize(application: Application, config: OemSdkConfig): OemSdkStatus {
        synchronized(lock) {
            runtimeState?.let { return it.status }

            OemLog.attachContext(application)
            val hostIdentity = OemPackageIdentityResolver.resolve(application)
            val request = OemLicenseRequest(
                customerId = config.customerId,
                appKey = config.appKey,
                channel = config.channel,
                environment = config.environment,
                hostIdentity = hostIdentity,
                sdkVersion = BuildConfig.OEM_SDK_VERSION,
                requestedCapabilities = config.requestedCapabilities,
                hostUserIdHash = config.hostUserIdHash?.trim()?.takeIf { it.isNotEmpty() },
            )
            val license = resolveLicense(application, config.licenseProvider, request)
            val availableCapabilities = license.resolvedCapabilities(config.requestedCapabilities)

            maybeInitializeAiRuntime(application, config, availableCapabilities)

            val status = OemSdkStatus(
                initialized = true,
                sdkVersion = BuildConfig.OEM_SDK_VERSION,
                customerId = config.customerId,
                channel = config.channel,
                environment = config.environment,
                hostIdentity = hostIdentity,
                requestedCapabilities = config.requestedCapabilities,
                availableCapabilities = availableCapabilities,
                license = license,
            )
            // Preserve pre-initialize runtime registrations/overrides.
            sessionArchiveListener = config.sessionArchiveListener ?: sessionArchiveListener
            diagnosticListener = config.diagnosticListener ?: diagnosticListener
            aiReplyLifecycleListener = config.aiReplyLifecycleListener ?: aiReplyLifecycleListener
            aiReplyHostDelegate = config.aiReplyHostDelegate ?: aiReplyHostDelegate
            materialHostDelegate = config.materialHostDelegate ?: materialHostDelegate
            materialTaxonomyHostDelegate = config.materialTaxonomyHostDelegate ?: materialTaxonomyHostDelegate
            swipeHostDelegate = config.swipeHostDelegate ?: swipeHostDelegate
            aiReplyTonePresets = config.aiReplyTonePresets
            aiReplySystemPrompt = config.aiReplySystemPrompt
            aiReplyPromptAssembler = config.aiReplyPromptAssembler
            aiReplyGenerationTimeoutMsOverride = config.aiReplyGenerationTimeoutMs?.takeIf { it > 0 }
            aiReplyNetworkTimeoutMsOverride = config.aiReplyNetworkTimeoutMs?.takeIf { it > 0 }
            aiReplyErrorTextCustomEnabledOverride = config.aiReplyErrorTextCustomEnabled
            aiReplyErrorTexts = config.aiReplyErrorTexts
            aiReplyMaxImageSelectionOverride = config.aiReplyMaxImageSelection?.takeIf { it > 0 }
                ?: aiReplyMaxImageSelectionOverride
            sendTextMaxCharsOverride = config.sendTextMaxChars?.takeIf { it > 0 }
                ?: sendTextMaxCharsOverride
            builtinNeonOverlayEnabledOverride = config.builtinNeonOverlayEnabled
                ?: builtinNeonOverlayEnabledOverride
            pendingStatus = null
            runtimeState = OemRuntimeState(application, config, status)
            persistAiReplyMaxImageSelection(application, aiReplyMaxImageSelectionOverride)
            if (config.enableDebugLog) OemLog.setEnabled(true)
            OemLog.i(
                TAG,
                "initialize sdkVersion=${BuildConfig.OEM_SDK_VERSION} customer=${config.customerId} " +
                    "channel=${config.channel} environment=${config.environment} " +
                    "requested=${config.requestedCapabilities.joinToString(",") { it.name }} " +
                    "available=${availableCapabilities.joinToString(",") { it.name }} " +
                    "license=${license.status} materialDispatch=${config.materialDispatchMode} " +
                    "materialNavigation=${config.materialNavigationMode} " +
                    "aiReplyDispatch=${config.aiReplyDispatchMode} debugLog=${OemLog.isEnabled()}",
            )
            OemAnalyticsReporter.onInitialized(application, config, status)
            return status
        }
    }

    fun initializeFromManifest(
        application: Application,
        licenseProvider: OemLicenseProvider,
        hostUserIdHash: String? = null,
        autoInitializeAiRuntime: Boolean = true,
        initializeLogCollector: Boolean = true,
        sessionArchiveListener: OemSessionArchiveListener? = null,
        aiReplyHostDelegate: OemAiReplyHostDelegate? = null,
        materialHostDelegate: OemMaterialHostDelegate? = null,
        aiReplyTonePresets: List<OemDeveloperAiTonePreset>? = null,
        aiReplySystemPrompt: String? = null,
        aiReplyPromptAssembler: OemAiReplyPromptAssembler? = null,
    ): OemSdkStatus {
        return initializeFromManifest(
            application = application,
            licenseProvider = licenseProvider,
            hostUserIdHash = hostUserIdHash,
            autoInitializeAiRuntime = autoInitializeAiRuntime,
            initializeLogCollector = initializeLogCollector,
            sessionArchiveListener = sessionArchiveListener,
            aiReplyHostDelegate = aiReplyHostDelegate,
            materialHostDelegate = materialHostDelegate,
            materialTaxonomyHostDelegate = null,
            aiReplyTonePresets = aiReplyTonePresets,
            aiReplySystemPrompt = aiReplySystemPrompt,
            aiReplyPromptAssembler = aiReplyPromptAssembler,
        )
    }

    fun initializeFromManifest(
        application: Application,
        licenseProvider: OemLicenseProvider,
        hostUserIdHash: String? = null,
        autoInitializeAiRuntime: Boolean = true,
        initializeLogCollector: Boolean = true,
        sessionArchiveListener: OemSessionArchiveListener? = null,
        aiReplyHostDelegate: OemAiReplyHostDelegate? = null,
        materialHostDelegate: OemMaterialHostDelegate? = null,
        materialTaxonomyHostDelegate: OemMaterialTaxonomyHostDelegate? = null,
        aiReplyTonePresets: List<OemDeveloperAiTonePreset>? = null,
        aiReplySystemPrompt: String? = null,
        aiReplyPromptAssembler: OemAiReplyPromptAssembler? = null,
    ): OemSdkStatus {
        OemLog.attachContext(application)
        OemLog.d(
            TAG,
            "initializeFromManifest autoInitializeAiRuntime=$autoInitializeAiRuntime " +
                "materialHostDelegate=${materialHostDelegate != null} " +
                "materialTaxonomyHostDelegate=${materialTaxonomyHostDelegate != null} " +
                "aiReplyHostDelegate=${aiReplyHostDelegate != null}",
        )
        val manifestConfig = manifestConfig(application)
        return initialize(
            application = application,
            config = manifestConfig.toSdkConfig(
                licenseProvider = licenseProvider,
                hostUserIdHash = hostUserIdHash,
                autoInitializeAiRuntime = autoInitializeAiRuntime,
                initializeLogCollector = initializeLogCollector,
                sessionArchiveListener = sessionArchiveListener,
                aiReplyHostDelegate = aiReplyHostDelegate,
                materialHostDelegate = materialHostDelegate,
                materialTaxonomyHostDelegate = materialTaxonomyHostDelegate,
                aiReplyTonePresets = aiReplyTonePresets,
                aiReplySystemPrompt = aiReplySystemPrompt,
                aiReplyPromptAssembler = aiReplyPromptAssembler,
            ),
        )
    }

    fun isInitialized(): Boolean = runtimeState != null

    fun getStatus(): OemSdkStatus = runtimeState?.status ?: throw OemSdkNotInitializedException()

    fun getConfig(): OemSdkConfig = runtimeState?.config ?: throw OemSdkNotInitializedException()

    fun isCapabilityAvailable(capability: OemCapability): Boolean {
        return runtimeState?.status?.isCapabilityAvailable(capability) == true
    }

    /**
     * AI 回复既要求 AI 能力可用，也要求宿主开放对应细粒度功能开关。
     */
    fun isAiReplyEnabled(): Boolean {
        getConfig()
        return getEffectiveFeatureToggles().aiReplyEnabled &&
            isCapabilityAvailable(OemCapability.AI) &&
            isCapabilityAvailable(OemCapability.UI)
    }

    /**
     * 素材库依赖 UI 能力承载。
     */
    fun isMaterialLibraryEnabled(): Boolean {
        getConfig()
        return getEffectiveFeatureToggles().materialLibraryEnabled &&
            isCapabilityAvailable(OemCapability.UI)
    }

    /**
     * 企业质检会话是独立功能，不强依赖 AI/UI capability。
     */
    fun isQaSessionEnabled(): Boolean {
        getConfig()
        return getEffectiveFeatureToggles().qaSessionEnabled
    }

    fun getSessionArchiveMode(): OemSessionArchiveMode = getConfig().sessionArchiveMode

    /**
     * 当前生效的功能开关：运行时覆盖 > 初始化配置（编程式配置 / Manifest）。
     */
    fun getEffectiveFeatureToggles(): OemFeatureToggles {
        val base = safeConfig()?.featureToggles ?: OemFeatureToggles()
        return base.applyOverrides(featureToggleOverrides)
    }

    /** 以整组开关覆盖当前运行时值；传 null 清除运行时覆盖并回退到初始化配置。 */
    fun setFeatureToggles(toggles: OemFeatureToggles?) {
        featureToggleOverrides =
            toggles?.let {
                OemFeatureToggleOverrides(
                    aiReplyEnabled = it.aiReplyEnabled,
                    materialLibraryEnabled = it.materialLibraryEnabled,
                    aiReplyCustomUiEnabled = it.aiReplyCustomUiEnabled,
                    materialLibraryCustomUiEnabled = it.materialLibraryCustomUiEnabled,
                    customDrawersEnabled = it.customDrawersEnabled,
                    qaSessionEnabled = it.qaSessionEnabled,
                    automationEnabled = it.automationEnabled,
                    developerEnabled = it.developerEnabled,
                )
            }
    }

    /**
     * 局部更新运行时功能开关。
     *
     * 只覆盖传入字段；未传入的字段保持现有运行时覆盖值不变。
     */
    fun updateFeatureToggles(
        aiReplyEnabled: Boolean? = null,
        materialLibraryEnabled: Boolean? = null,
        aiReplyCustomUiEnabled: Boolean? = null,
        materialLibraryCustomUiEnabled: Boolean? = null,
        customDrawersEnabled: Boolean? = null,
        qaSessionEnabled: Boolean? = null,
        automationEnabled: Boolean? = null,
        developerEnabled: Boolean? = null,
    ) {
        val current = featureToggleOverrides ?: OemFeatureToggleOverrides()
        featureToggleOverrides = current.copy(
            aiReplyEnabled = aiReplyEnabled ?: current.aiReplyEnabled,
            materialLibraryEnabled = materialLibraryEnabled ?: current.materialLibraryEnabled,
            aiReplyCustomUiEnabled = aiReplyCustomUiEnabled ?: current.aiReplyCustomUiEnabled,
            materialLibraryCustomUiEnabled = materialLibraryCustomUiEnabled ?: current.materialLibraryCustomUiEnabled,
            customDrawersEnabled = customDrawersEnabled ?: current.customDrawersEnabled,
            qaSessionEnabled = qaSessionEnabled ?: current.qaSessionEnabled,
            automationEnabled = automationEnabled ?: current.automationEnabled,
            developerEnabled = developerEnabled ?: current.developerEnabled,
        )
    }

    fun clearFeatureToggleOverrides() {
        featureToggleOverrides = null
    }

    fun setAiReplyEnabled(enabled: Boolean?) {
        updateFeatureToggles(aiReplyEnabled = enabled)
    }

    fun setMaterialLibraryEnabled(enabled: Boolean?) {
        updateFeatureToggles(materialLibraryEnabled = enabled)
    }

    fun setQaSessionEnabled(enabled: Boolean?) {
        updateFeatureToggles(qaSessionEnabled = enabled)
    }

    fun setSessionArchiveListener(listener: OemSessionArchiveListener?) {
        sessionArchiveListener = listener
    }

    fun setDiagnosticListener(listener: OemDiagnosticListener?) {
        diagnosticListener = listener
    }

    fun setAiReplyLifecycleListener(listener: OemAiReplyLifecycleListener?) {
        aiReplyLifecycleListener = listener
    }

    fun dispatchSessionArchiveEvent(event: OemSessionArchiveEvent): Boolean {
        return runCatching {
            sessionArchiveListener?.onSessionArchived(event) == true
        }.getOrDefault(false)
    }

    fun dispatchDiagnosticEvent(event: OemDiagnosticEvent): Boolean {
        val attributeText = event.attributes.entries.joinToString(separator = " ") { (key, value) ->
            "$key=$value"
        }
        OemLog.log(
            event.level,
            "OemDiagnostic",
            "category=${event.category} name=${event.name} message=${event.message} $attributeText".trim(),
        )
        return runCatching {
            diagnosticListener?.onDiagnosticEvent(event) == true
        }.getOrDefault(false)
    }

    fun hasDiagnosticListener(): Boolean = diagnosticListener != null

    fun hasAiReplyLifecycleListener(): Boolean = aiReplyLifecycleListener != null

    fun dispatchAiReplyGenerationStarted(requestId: String, generateMode: String, fresh: Boolean) {
        OemLog.d(TAG, "aiReply lifecycle started requestId=$requestId mode=$generateMode fresh=$fresh")
        runCatching {
            aiReplyLifecycleListener?.onGenerationStarted(requestId, generateMode, fresh)
        }.onFailure { error ->
            OemLog.w(TAG, "aiReply lifecycle start listener threw: ${error.message}", error)
        }
    }

    fun dispatchAiReplyGenerationFinished(
        requestId: String,
        generateMode: String,
        success: Boolean,
        reason: String,
    ) {
        OemLog.d(
            TAG,
            "aiReply lifecycle finished requestId=$requestId mode=$generateMode success=$success reason=$reason",
        )
        runCatching {
            aiReplyLifecycleListener?.onGenerationFinished(requestId, generateMode, success, reason)
        }.onFailure { error ->
            OemLog.w(TAG, "aiReply lifecycle finish listener threw: ${error.message}", error)
        }
    }


    /**
     * 获取当前生效的 AI 回复分发模式。
     *
     * 解析优先级：运行时覆盖值（[setAiReplyDispatchMode]）>
     * 初始化配置 [OemSdkConfig.aiReplyDispatchMode]（含 Manifest meta-data 解析结果）。
     */
    fun getAiReplyDispatchMode(): OemAiReplyDispatchMode {
        return aiReplyDispatchModeOverride ?: getConfig().aiReplyDispatchMode
    }

    /**
     * 运行时覆盖 AI 回复分发模式（选填能力）。
     *
     * 传入 null 清除覆盖值，回退到初始化配置。
     * 典型用途：宿主在自有设置页切换对接方式后即时生效，无需重启进程。
     */
    fun setAiReplyDispatchMode(mode: OemAiReplyDispatchMode?) {
        aiReplyDispatchModeOverride = mode
        OemLog.i(TAG, "setAiReplyDispatchMode override=$mode")
    }

    /**
     * 获取当前生效的素材库分发模式。
     *
     * 解析优先级：运行时覆盖值（[setMaterialDispatchMode]）>
     * 初始化配置 [OemSdkConfig.materialDispatchMode]（含 Manifest meta-data 解析结果）。
     */
    fun getMaterialDispatchMode(): OemMaterialDispatchMode {
        return materialDispatchModeOverride ?: getConfig().materialDispatchMode
    }

    /**
     * 运行时覆盖素材库分发模式（选填能力），语义同 [setAiReplyDispatchMode]。
     */
    fun setMaterialDispatchMode(mode: OemMaterialDispatchMode?) {
        materialDispatchModeOverride = mode
        OemLog.i(TAG, "setMaterialDispatchMode override=$mode")
    }

    /** 读取素材库分发模式运行时覆盖值；即使 SDK 尚未初始化也可使用。 */
    fun peekMaterialDispatchModeOverride(): OemMaterialDispatchMode? = materialDispatchModeOverride

    /**
     * 获取当前生效的素材库导航模式。
     *
     * 解析优先级：运行时覆盖值（[setMaterialNavigationMode]）>
     * 初始化配置 [OemSdkConfig.materialNavigationMode]（含 Manifest meta-data 解析结果）。
     */
    fun getMaterialNavigationMode(): OemMaterialNavigationMode {
        return materialNavigationModeOverride ?: getConfig().materialNavigationMode
    }

    /** 运行时覆盖素材库导航模式，传 null 恢复为初始化配置。 */
    fun setMaterialNavigationMode(mode: OemMaterialNavigationMode?) {
        materialNavigationModeOverride = mode
        OemLog.i(TAG, "setMaterialNavigationMode override=$mode")
    }

    /** 读取素材库导航模式运行时覆盖值；即使 SDK 尚未初始化也可使用。 */
    fun peekMaterialNavigationModeOverride(): OemMaterialNavigationMode? = materialNavigationModeOverride

    /**
     * 注册 / 替换 AI 回复宿主回调委托。
     *
     * 可在 [initialize] 之前或之后调用；传入 null 表示注销。
     * 当分发模式为 HOST_ONLY / DUAL_WRITE 时，宿主必须注册本委托，
     * 否则 AI 回复将无法生成（HOST_ONLY 直接提示失败，DUAL_WRITE 回落域名通道）。
     */
    fun setAiReplyHostDelegate(delegate: OemAiReplyHostDelegate?) {
        aiReplyHostDelegate = delegate
        OemLog.i(TAG, "setAiReplyHostDelegate registered=${delegate != null}")
    }

    /** 是否已注册 AI 回复宿主回调委托。 */
    fun isAiReplyHostDelegateRegistered(): Boolean = aiReplyHostDelegate != null

    /** 注册自动化滑屏宿主接管回调：SDK 通知宿主滑屏动作定义，宿主执行后回写结果。 */
    fun setSwipeHostDelegate(delegate: OemSwipeHostDelegate?) {
        swipeHostDelegate = delegate
        OemLog.i(TAG, "setSwipeHostDelegate registered=${delegate != null}")
    }

    fun isSwipeHostDelegateRegistered(): Boolean = swipeHostDelegate != null

    /** 当前生效的滑屏分发模式：运行时覆盖 > [OemSdkConfig.swipeDispatchMode]。 */
    fun getSwipeDispatchMode(): OemSwipeDispatchMode {
        return swipeDispatchModeOverride ?: getConfig().swipeDispatchMode
    }

    /** 运行时覆盖滑屏分发模式，传 null 恢复为初始化配置。 */
    fun setSwipeDispatchMode(mode: OemSwipeDispatchMode?) {
        swipeDispatchModeOverride = mode
        OemLog.i(TAG, "setSwipeDispatchMode override=$mode")
    }

    /** 向宿主分发一次滑屏事件；未注册回调或宿主抛异常时返回 false（宿主不接管）。 */
    fun dispatchSwipeToHost(request: OemSwipeRequest, callback: OemSwipeResultCallback): Boolean {
        val delegate = swipeHostDelegate ?: return false
        return runCatching { delegate.onSwipeRequested(request, callback) }.getOrDefault(false)
    }

    /**
     * 当前宿主滑屏结果回写等待超时（毫秒），默认 [DEFAULT_SWIPE_HOST_RESULT_TIMEOUT_MS]。
     *
     * 适用场景：宿主通过无障碍服务注入手势后，结果经跨进程通道（如 ContentObserver）
     * 回传 SDK 的链路延迟较大时，宿主应把超时上调到覆盖自身链路最大延迟，
     * 避免 SDK 因提前判定超时而丢弃本次滑屏结果。
     */
    fun getSwipeHostResultTimeoutMillis(): Long = swipeHostResultTimeoutMs

    /**
     * 配置宿主滑屏结果回写等待超时（毫秒）。
     *
     * 低于 [MIN_SWIPE_HOST_RESULT_TIMEOUT_MS] 的值会被收敛到最小值；
     * 建议在 [initialize] 之前或宿主注册 [setSwipeHostDelegate] 时一并设置。
     */
    fun setSwipeHostResultTimeoutMillis(timeoutMs: Long) {
        swipeHostResultTimeoutMs = timeoutMs.coerceAtLeast(MIN_SWIPE_HOST_RESULT_TIMEOUT_MS)
    }

    /**
     * 注册 / 替换宿主宰图回调（自 1.0.0.20260824 起提供）。
     *
     * 面向具备系统级截屏能力的宿主（如 MDM 管控应用）。注册后：
     * - SDK 跳过全部截图权限校验（能力预检与授权检查直接视为可用）；
     * - SDK 截图时只调用 [OemScreenshotProvider.captureScreen]，
     *   不再走 Automation / MediaProjection / 缓存截图的降级链路；
     * - 宿主返回 null 时本次截图直接判定失败（CAPTURE_FAILED），不做任何降级尝试。
     *
     * 传入 null 注销回调，SDK 回退到原有截图链路。
     */
    fun setScreenshotProvider(provider: OemScreenshotProvider?) {
        screenshotProvider = provider
    }

    /** 读取当前注册的宿主宰图回调；未注册返回 null。 */
    fun getScreenshotProvider(): OemScreenshotProvider? = screenshotProvider

    /** 是否已注册宿主宰图回调；注册后截图链路完全由宿主接管。 */
    fun isScreenshotProviderRegistered(): Boolean = screenshotProvider != null

    /**
     * 注册 / 替换素材库宿主回调委托。
     *
     * 当分发模式为 HOST_ONLY / DUAL_WRITE 时，宿主必须注册本委托，
     * 否则企业素材将无法拉取（保留上一次的本地缓存）。
     */
    fun setMaterialHostDelegate(delegate: OemMaterialHostDelegate?) {
        materialHostDelegate = delegate
        OemLog.i(TAG, "setMaterialHostDelegate registered=${delegate != null}")
    }

    /** 是否已注册素材库宿主回调委托。 */
    fun isMaterialHostDelegateRegistered(): Boolean = materialHostDelegate != null

    /**
     * 注册 / 替换素材库分类/标签宿主回调委托。
     *
     * 委托用于：
     * - 拉取分类/标签列表，驱动素材库双层导航与标签搜索；
     * - 承接抽屉中的分类/标签增删改操作。
     */
    fun setMaterialTaxonomyHostDelegate(delegate: OemMaterialTaxonomyHostDelegate?) {
        materialTaxonomyHostDelegate = delegate
        OemLog.i(TAG, "setMaterialTaxonomyHostDelegate registered=${delegate != null}")
    }

    /** 是否已注册素材库分类/标签宿主回调委托。 */
    fun isMaterialTaxonomyHostDelegateRegistered(): Boolean = materialTaxonomyHostDelegate != null

    // #region DrawerKit（R9 抽屉开放层）

    /** AI 回复「自定义UI交互」开关生效值（运行时覆盖 > Manifest > 默认 false）。 */
    fun isAiReplyCustomUiEnabled(): Boolean = getEffectiveFeatureToggles().aiReplyCustomUiEnabled

    /** 素材库「自定义UI交互」开关生效值（运行时覆盖 > Manifest > 默认 false）。 */
    fun isMaterialLibraryCustomUiEnabled(): Boolean =
        getEffectiveFeatureToggles().materialLibraryCustomUiEnabled

    /** 用户自定义抽屉开关生效值（Manifest / 编程式配置，默认 false）。 */
    fun isCustomDrawersEnabled(): Boolean = getEffectiveFeatureToggles().customDrawersEnabled

    /** 宿主为内置抽屉（ai_reply / material_hub）注册自定义内容工厂；传 null 恢复默认UI。 */
    fun setDrawerContentFactory(drawerId: String?, factory: OemDrawerContentFactory?) {
        val id = drawerId?.trim().orEmpty()
        if (id.isEmpty()) return
        synchronized(drawerContentFactories) {
            if (factory == null) {
                drawerContentFactories.remove(id)
            } else {
                drawerContentFactories[id] = factory
            }
        }
        OemLog.i(TAG, "setDrawerContentFactory drawerId=$id installed=${factory != null}")
    }

    /** 读取内置抽屉的自定义内容工厂。 */
    fun drawerContentFactory(drawerId: String): OemDrawerContentFactory? =
        synchronized(drawerContentFactories) { drawerContentFactories[drawerId] }

    /** The current clipboard entry handler. Null uses the built-in reply flow. */
    @Volatile
    private var currentClipboardQuickReplyHandler: OemClipboardQuickReplyHandler? = null

    val clipboardQuickReplyHandler: OemClipboardQuickReplyHandler?
        get() = currentClipboardQuickReplyHandler

    /** Register or remove the host-owned clipboard reply click handler. */
    fun setClipboardQuickReplyHandler(handler: OemClipboardQuickReplyHandler?) {
        currentClipboardQuickReplyHandler = handler
    }

    /** 注册自定义抽屉（L2），入口自动进入工具面板与定制工具栏体系。 */
    fun registerCustomDrawer(spec: OemCustomDrawerSpec): OemRegisterResult =
        OemCustomDrawerRegistry.register(spec).also {
            OemLog.i(TAG, "registerCustomDrawer id=${spec.id} title=${spec.title} result=$it")
        }

    /** 更新已注册自定义抽屉的标题/图标/内容工厂。 */
    fun updateCustomDrawer(spec: OemCustomDrawerSpec): OemRegisterResult =
        OemCustomDrawerRegistry.update(spec).also {
            OemLog.i(TAG, "updateCustomDrawer id=${spec.id} title=${spec.title} result=$it")
        }

    /** 注销自定义抽屉，入口与持久化配置同步清理。 */
    fun unregisterCustomDrawer(id: String): Boolean =
        OemCustomDrawerRegistry.unregister(id).also {
            OemLog.i(TAG, "unregisterCustomDrawer id=$id removed=$it")
        }

    /** 当前已注册的自定义抽屉列表（注册顺序）。 */
    fun customDrawers(): List<OemCustomDrawerSpec> =
        if (isCustomDrawersEnabled()) {
            OemCustomDrawerRegistry.list()
        } else {
            emptyList()
        }

    fun findCustomDrawer(id: String): OemCustomDrawerSpec? =
        if (isCustomDrawersEnabled()) {
            OemCustomDrawerRegistry.find(id)
        } else {
            null
        }

    /** 主动拉起抽屉（内置或自定义）。 */
    fun openDrawer(drawerId: String) {
        val actions = drawerHostActions
        if (actions == null) {
            OemLog.w(TAG, "openDrawer ignored: drawer host not bound, drawerId=$drawerId")
            return
        }
        OemLog.d(TAG, "openDrawer drawerId=$drawerId")
        actions.openDrawer(drawerId)
    }

    /** 主动关闭抽屉。 */
    fun closeDrawer(drawerId: String) {
        val actions = drawerHostActions
        if (actions == null) {
            OemLog.w(TAG, "closeDrawer ignored: drawer host not bound, drawerId=$drawerId")
            return
        }
        OemLog.d(TAG, "closeDrawer drawerId=$drawerId")
        actions.closeDrawer(drawerId)
    }

    /** 抽屉基座（MaterialHubActivity）绑定主动开合能力。 */
    fun bindDrawerHostActions(actions: OemDrawerHostActions?) {
        drawerHostActions = actions
    }

    /** AI 回复能力字段协议（UI 与能力解耦后交付宿主）。 */
    fun aiReplyCapabilities(): OemAiReplyCapabilities? = aiReplyCapabilitiesProvider

    /** 素材库能力字段协议。 */
    fun materialCapabilities(): OemMaterialCapabilities? = materialCapabilitiesProvider

    fun setAiReplyCapabilitiesProvider(provider: OemAiReplyCapabilities?) {
        aiReplyCapabilitiesProvider = provider
    }

    fun setMaterialCapabilitiesProvider(provider: OemMaterialCapabilities?) {
        materialCapabilitiesProvider = provider
    }

    private fun safeConfig(): OemSdkConfig? = runCatching { getConfig() }.getOrNull()

    private val drawerContentFactories = HashMap<String, OemDrawerContentFactory>()

    @Volatile
    private var drawerHostActions: OemDrawerHostActions? = null

    @Volatile
    private var aiReplyCapabilitiesProvider: OemAiReplyCapabilities? = null

    @Volatile
    private var materialCapabilitiesProvider: OemMaterialCapabilities? = null

    // #endregion DrawerKit

    /**
     * 获取宿主自定义 AI 回复语气预设（选填能力）。
     *
     * @return 宿主预设列表；未配置时返回 null（调用方按「开发者模块 → SDK 内置」回退）。
     */
    fun getAiReplyTonePresets(): List<OemDeveloperAiTonePreset>? = aiReplyTonePresets

    /** 运行时设置 / 清除（传 null）宿主自定义 AI 回复语气预设。 */
    fun setAiReplyTonePresets(presets: List<OemDeveloperAiTonePreset>?) {
        aiReplyTonePresets = presets
    }

    /**
     * 获取宿主自定义 AI 回复系统提示词（选填能力）。
     *
     * @return 宿主系统提示词；未配置时返回 null（调用方按「开发者模块 → SDK 内置」回退）。
     */
    fun getAiReplySystemPrompt(): String? = aiReplySystemPrompt

    /** 运行时设置 / 清除（传 null）宿主自定义 AI 回复系统提示词。 */
    fun setAiReplySystemPrompt(systemPrompt: String?) {
        aiReplySystemPrompt = systemPrompt
    }

    /**
     * 获取宿主自定义 AI 回复 Prompt 组装器（选填能力）。
     *
     * @return 宿主组装器；未注册时返回 null（SDK 使用内置 Prompt 模板）。
     */
    fun getAiReplyPromptAssembler(): OemAiReplyPromptAssembler? = aiReplyPromptAssembler

    /** 运行时注册 / 注销（传 null）宿主自定义 AI 回复 Prompt 组装器。 */
    fun setAiReplyPromptAssembler(assembler: OemAiReplyPromptAssembler?) {
        aiReplyPromptAssembler = assembler
    }

    /** 是否已注册宿主自定义 AI 回复 Prompt 组装器。 */
    fun isAiReplyPromptAssemblerRegistered(): Boolean = aiReplyPromptAssembler != null

    /** 宿主运行时覆盖的大模型请求超时（毫秒）；未设置时为 null。 */
    fun getAiReplyGenerationTimeoutMs(): Long? = aiReplyGenerationTimeoutMsOverride

    /** 宿主运行时覆盖大模型请求超时（毫秒）；传 null 或 <=0 表示清除覆盖。 */
    fun setAiReplyGenerationTimeoutMs(timeoutMs: Long?) {
        aiReplyGenerationTimeoutMsOverride = timeoutMs?.takeIf { it > 0 }
    }

    /** 宿主运行时覆盖的网络连接超时（毫秒）；未设置时为 null。 */
    fun getAiReplyNetworkTimeoutMs(): Long? = aiReplyNetworkTimeoutMsOverride

    /** 宿主运行时覆盖网络连接超时（毫秒）；传 null 或 <=0 表示清除覆盖。 */
    fun setAiReplyNetworkTimeoutMs(timeoutMs: Long?) {
        aiReplyNetworkTimeoutMsOverride = timeoutMs?.takeIf { it > 0 }
    }

    /** 宿主运行时覆盖的错误文案自定义开关；未设置时为 null。 */
    fun getAiReplyErrorTextCustomEnabled(): Boolean? = aiReplyErrorTextCustomEnabledOverride

    /** 宿主运行时开启/关闭错误文案自定义。 */
    fun setAiReplyErrorTextCustomEnabled(enabled: Boolean?) {
        aiReplyErrorTextCustomEnabledOverride = enabled
    }

    /** 宿主自定义错误文案表（key 为 [OemAiReplyErrorCode] 状态码）。 */
    fun getAiReplyErrorTexts(): Map<String, OemAiReplyErrorText>? = aiReplyErrorTexts

    /** 宿主运行时设置按状态码自定义的错误文案表。 */
    fun setAiReplyErrorTexts(texts: Map<String, OemAiReplyErrorText>?) {
        aiReplyErrorTexts = texts
    }

    /** 宿主运行时覆盖 AI 回复「传图回复」最多可选图片数量；传 null 或 <=0 表示清除覆盖。 */
    fun setAiReplyMaxImageSelection(maxCount: Int?) {
        aiReplyMaxImageSelectionOverride = maxCount?.takeIf { it > 0 }
        runtimeState?.application?.let { application ->
            persistAiReplyMaxImageSelection(application, aiReplyMaxImageSelectionOverride)
        }
    }

    /** 宿主运行时覆盖的 AI 回复「传图回复」最多可选图片数量；未设置时为 null。 */
    fun getAiReplyMaxImageSelection(): Int? = aiReplyMaxImageSelectionOverride

    /** 宿主运行时覆盖 AI 回复 / 素材库单次文本发送最大字符数；传 null 或 <=0 表示清除覆盖。 */
    fun setSendTextMaxChars(maxChars: Int?) {
        sendTextMaxCharsOverride = maxChars?.takeIf { it > 0 }
    }

    /** 宿主运行时覆盖的 AI 回复 / 素材库单次文本发送最大字符数；未设置时为 null。 */
    fun getSendTextMaxChars(): Int? = sendTextMaxCharsOverride

    /** 是否启用 SDK 内置 AI 回复霓虹边框；运行时覆盖 > 初始化配置 > 默认 false。 */
    fun isBuiltinNeonOverlayEnabled(): Boolean {
        return builtinNeonOverlayEnabledOverride ?: safeConfig()?.builtinNeonOverlayEnabled ?: false
    }

    /** 宿主运行时设置 SDK 内置霓虹总开关。 */
    fun setBuiltinNeonOverlayEnabled(enabled: Boolean) {
        builtinNeonOverlayEnabledOverride = enabled
        OemLog.i(TAG, "setBuiltinNeonOverlayEnabled enabled=$enabled")
    }

    /** 清除运行时覆盖，回退到初始化配置或默认 false。 */
    fun clearBuiltinNeonOverlayEnabledOverride() {
        builtinNeonOverlayEnabledOverride = null
        OemLog.i(TAG, "clearBuiltinNeonOverlayEnabledOverride")
    }

    private fun persistAiReplyMaxImageSelection(context: Context, maxCount: Int?) {
        context.applicationContext
            .getSharedPreferences(UI_RUNTIME_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (maxCount != null && maxCount > 0) {
                    putInt(KEY_AI_REPLY_MAX_IMAGE_SELECTION, maxCount)
                } else {
                    remove(KEY_AI_REPLY_MAX_IMAGE_SELECTION)
                }
            }
            .apply()
    }

    /**
     * 派发一次 AI 回复生成请求给宿主（SDK 内部使用）。
     *
     * @return true 表示宿主已接受请求（稍后通过 [callback] 回传结果）；
     * false 表示未注册委托、宿主拒绝处理或宿主抛出异常。
     */
    fun dispatchAiReplyGeneration(request: OemAiReplyRequest, callback: OemAiReplyResultCallback): Boolean {
        val delegate = aiReplyHostDelegate
        if (delegate == null) {
            OemLog.w(TAG, "dispatchAiReplyGeneration rejected: aiReplyHostDelegate not registered")
            return false
        }
        return runCatching { delegate.onGenerateReplies(request, callback) }
            .onFailure { error -> OemLog.e(TAG, "dispatchAiReplyGeneration host threw: ${error.message}", error) }
            .getOrDefault(false)
            .also { accepted -> OemLog.d(TAG, "dispatchAiReplyGeneration accepted=$accepted") }
    }

    /**
     * 派发一次企业素材拉取请求给宿主（SDK 内部使用）。
     *
     * @return true 表示宿主已接受请求（稍后通过 [callback] 回传结果）；
     * false 表示未注册委托、宿主拒绝处理或宿主抛出异常。
     */
    fun dispatchRemoteMaterialFetch(request: OemMaterialFetchRequest, callback: OemMaterialFetchCallback): Boolean {
        val delegate = materialHostDelegate
        if (delegate == null) {
            OemLog.w(TAG, "dispatchRemoteMaterialFetch rejected: materialHostDelegate not registered")
            return false
        }
        return runCatching { delegate.fetchRemoteMaterials(request, callback) }
            .onFailure { error -> OemLog.e(TAG, "dispatchRemoteMaterialFetch host threw: ${error.message}", error) }
            .getOrDefault(false)
            .also { accepted -> OemLog.d(TAG, "dispatchRemoteMaterialFetch accepted=$accepted limit=${request.limit}") }
    }

    /**
     * 派发一次企业素材分类/标签拉取请求给宿主（SDK 内部使用）。
     */
    fun dispatchMaterialTaxonomyFetch(
        request: OemMaterialTaxonomyFetchRequest,
        callback: OemMaterialTaxonomyFetchCallback,
    ): Boolean {
        val delegate = materialTaxonomyHostDelegate
        if (delegate == null) {
            OemLog.w(TAG, "dispatchMaterialTaxonomyFetch rejected: materialTaxonomyHostDelegate not registered")
            return false
        }
        return runCatching { delegate.fetchMaterialTaxonomy(request, callback) }
            .onFailure { error -> OemLog.e(TAG, "dispatchMaterialTaxonomyFetch host threw: ${error.message}", error) }
            .getOrDefault(false)
            .also { accepted -> OemLog.d(TAG, "dispatchMaterialTaxonomyFetch accepted=$accepted") }
    }

    /**
     * 派发一次企业素材分类/标签变更请求给宿主（SDK 内部使用）。
     */
    fun dispatchMaterialTaxonomyMutation(
        request: OemMaterialTaxonomyMutationRequest,
        callback: OemMaterialTaxonomyMutationCallback,
    ): Boolean {
        val delegate = materialTaxonomyHostDelegate
        if (delegate == null) {
            OemLog.w(TAG, "dispatchMaterialTaxonomyMutation rejected: materialTaxonomyHostDelegate not registered")
            return false
        }
        return runCatching { delegate.mutateMaterialTaxonomy(request, callback) }
            .onFailure { error -> OemLog.e(TAG, "dispatchMaterialTaxonomyMutation host threw: ${error.message}", error) }
            .getOrDefault(false)
            .also { accepted -> OemLog.d(TAG, "dispatchMaterialTaxonomyMutation accepted=$accepted") }
    }

    /**
     * 宿主推送「企业素材新增 / 更新」事件（增 / 改）。
     *
     * 宿主在自己的素材管理后台发生变更后调用本方法，
     * SDK 会立即更新本地企业素材缓存并刷新素材库抽屉展示。
     * 幂等语义：相同 materialId 重复推送视为更新。
     *
     * 注意：未初始化、或当前素材库分发模式为 LOCAL_ONLY / CLOUD_ONLY 时事件会被忽略。
     */
    fun notifyRemoteMaterialUpserted(item: OemRemoteMaterialItem) {
        dispatchRemoteMaterialCatalogEvent(OemRemoteMaterialCatalogEvent.Upsert(item))
    }

    /**
     * 宿主推送「企业素材删除」事件（删）。
     *
     * @param materialId 需要删除的素材 ID；不存在时静默忽略。
     */
    fun notifyRemoteMaterialRemoved(materialId: String) {
        dispatchRemoteMaterialCatalogEvent(OemRemoteMaterialCatalogEvent.Remove(materialId))
    }

    /**
     * 宿主推送「企业素材全量失效」事件（查）。
     *
     * SDK 收到后会重新通过 [OemMaterialHostDelegate.fetchRemoteMaterials] 拉取完整列表，
     * 适合宿主侧发生批量变更、难以逐条推送的场景。
     */
    fun invalidateRemoteMaterialCatalog() {
        dispatchRemoteMaterialCatalogEvent(OemRemoteMaterialCatalogEvent.Invalidate)
    }

    /**
     * SDK 内部使用：安装企业素材变更监听器，接收宿主推送的增删改事件。
     * 企业集成方无需调用。
     */
    fun installRemoteMaterialCatalogListener(listener: OemRemoteMaterialCatalogListener?) {
        remoteMaterialCatalogListener = listener
    }

    private fun dispatchRemoteMaterialCatalogEvent(event: OemRemoteMaterialCatalogEvent) {
        OemLog.d(
            TAG,
            "dispatchRemoteMaterialCatalogEvent event=${event::class.simpleName} " +
                "listenerInstalled=${remoteMaterialCatalogListener != null}",
        )
        runCatching {
            remoteMaterialCatalogListener?.onCatalogChanged(event)
        }.onFailure { error ->
            OemLog.w(TAG, "dispatch remote material catalog event failed: ${error.message}", error)
        }
    }

    fun manifestConfig(context: Context): OemManifestConfig {
        return OemManifestConfigParser.parse(context)
    }

    fun createSettingsIntent(context: Context): Intent {
        ensureCapabilityAvailable(OemCapability.UI)
        return AiKeyboardHostSdk.createSettingsIntent(context)
    }

    fun applyPendingLicenseRefreshIfAny(): Boolean {
        synchronized(lock) {
            val state = runtimeState ?: return false
            val pending = pendingStatus ?: return false
            pendingStatus = null
            maybeInitializeAiRuntime(
                application = state.application,
                config = state.config,
                availableCapabilities = pending.availableCapabilities,
                previousCapabilities = state.status.availableCapabilities,
            )
            runtimeState = state.copy(status = pending)
            OemLog.i(TAG, "apply pending license refresh status=${pending.license.status}")
            return true
        }
    }

    fun refreshLicenseAsync(
        context: Context,
        reason: String,
        applyMode: OemLicenseRefreshApplyMode = OemLicenseRefreshApplyMode.DEFER_UNTIL_NEXT_USE,
        minIntervalMs: Long = 15_000L,
    ): Boolean {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (runtimeState == null) return false
            val now = SystemClock.elapsedRealtime()
            if (refreshInFlight) return false
            if (minIntervalMs > 0L && now - lastRefreshDispatchAtElapsedMs < minIntervalMs) return false
            refreshInFlight = true
            lastRefreshDispatchAtElapsedMs = now
        }
        refreshExecutor.execute {
            runCatching {
                refreshLicenseInternal(appContext, reason, applyMode)
            }.onFailure { error ->
                OemLog.w(TAG, "refresh license async failed reason=$reason message=${error.message}", error)
            }
            refreshInFlight = false
        }
        return true
    }

    fun refreshLicenseNow(
        context: Context,
        reason: String,
        applyMode: OemLicenseRefreshApplyMode = OemLicenseRefreshApplyMode.IMMEDIATE,
    ): OemSdkStatus? {
        return runCatching {
            refreshLicenseInternal(context.applicationContext, reason, applyMode)
        }.onFailure { error ->
            OemLog.w(TAG, "refresh license now failed reason=$reason message=${error.message}", error)
        }.getOrNull()
    }

    fun resetForTesting() {
        synchronized(lock) {
            runtimeState = null
            pendingStatus = null
            refreshInFlight = false
            lastRefreshDispatchAtElapsedMs = 0L
            sessionArchiveListener = null
            aiReplyHostDelegate = null
            materialHostDelegate = null
            materialTaxonomyHostDelegate = null
            aiReplyTonePresets = null
            aiReplySystemPrompt = null
            aiReplyPromptAssembler = null
            aiReplyDispatchModeOverride = null
            materialDispatchModeOverride = null
            materialNavigationModeOverride = null
            remoteMaterialCatalogListener = null
            featureToggleOverrides = null
            sendTextMaxCharsOverride = null
        }
    }

    private fun ensureCapabilityAvailable(capability: OemCapability) {
        val status = getStatus()
        require(status.isCapabilityAvailable(capability)) {
            "Capability $capability is not available under current license or config"
        }
    }

    private fun refreshLicenseInternal(
        context: Context,
        reason: String,
        applyMode: OemLicenseRefreshApplyMode,
    ): OemSdkStatus? {
        val state = synchronized(lock) { runtimeState } ?: return null
        val request = OemLicenseRequest(
            customerId = state.config.customerId,
            appKey = state.config.appKey,
            channel = state.config.channel,
            environment = state.config.environment,
            hostIdentity = state.status.hostIdentity,
            sdkVersion = BuildConfig.OEM_SDK_VERSION,
            requestedCapabilities = state.config.requestedCapabilities,
            hostUserIdHash = state.config.hostUserIdHash?.trim()?.takeIf { it.isNotEmpty() },
        )
        val license = resolveLicense(context, state.config.licenseProvider, request)
        val refreshedStatus = state.status.copy(
            availableCapabilities = license.resolvedCapabilities(state.config.requestedCapabilities),
            license = license,
        )
        synchronized(lock) {
            val currentState = runtimeState ?: return refreshedStatus
            when (applyMode) {
                OemLicenseRefreshApplyMode.IMMEDIATE -> {
                    pendingStatus = null
                    maybeInitializeAiRuntime(
                        application = currentState.application,
                        config = currentState.config,
                        availableCapabilities = refreshedStatus.availableCapabilities,
                        previousCapabilities = currentState.status.availableCapabilities,
                    )
                    runtimeState = currentState.copy(status = refreshedStatus)
                }
                OemLicenseRefreshApplyMode.DEFER_UNTIL_NEXT_USE -> {
                    pendingStatus = refreshedStatus
                }
            }
        }
        OemLog.i(TAG, "refresh license success reason=$reason applyMode=$applyMode status=${refreshedStatus.license.status}")
        return refreshedStatus
    }

    private fun maybeInitializeAiRuntime(
        application: Application,
        config: OemSdkConfig,
        availableCapabilities: Set<OemCapability>,
        previousCapabilities: Set<OemCapability> = emptySet(),
    ) {
        if (!config.autoInitializeAiRuntime) return
        if (OemCapability.AI !in availableCapabilities) return
        if (OemCapability.AI in previousCapabilities) return
        runCatching {
            AiKeyboardHostSdk.initialize(application)
            if (config.initializeLogCollector) {
                AiKeyboardHostSdk.initializeLogCollector()
            }
        }.onFailure { error ->
            // 宿主 AI 运行时初始化失败不应阻断 OEM 授权状态建立和实例上报。
            OemLog.w(TAG, "AI runtime initialize failed, keep OEM SDK alive: ${error.message}", error)
        }
    }

    private fun resolveLicense(
        context: Context,
        provider: OemLicenseProvider,
        request: OemLicenseRequest,
    ): OemLicenseSnapshot {
        return runCatching { provider.fetchLicense(context, request) }
            .fold(
                onSuccess = { snapshot ->
                    if (snapshot.status.allowsRuntime) {
                        cacheSuccessfulLicense(context, request, snapshot)
                    } else {
                        clearCachedLicense(context)
                    }
                    snapshot
                },
                onFailure = { error ->
                    loadCachedGraceLicense(context, request)?.also {
                        OemLog.w(TAG, "use cached grace license after resolve failure: ${error.message}", error)
                    } ?: buildInitialGraceLicense(request).also {
                        OemLog.w(TAG, "use initial grace license after resolve failure: ${error.message}", error)
                    }
                },
            )
    }

    private fun cacheSuccessfulLicense(
        context: Context,
        request: OemLicenseRequest,
        snapshot: OemLicenseSnapshot,
    ) {
        context.applicationContext
            .getSharedPreferences(LICENSE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LICENSE_STATUS, snapshot.status.name)
            .putString(KEY_LICENSE_ID, snapshot.licenseId.orEmpty())
            .putString(KEY_LICENSE_MESSAGE, snapshot.message)
            .putLong(KEY_LICENSE_EXPIRES_AT, snapshot.expiresAtMillis ?: 0L)
            .putString(KEY_LICENSE_GRANTED, snapshot.grantedCapabilities.joinToString(",") { it.name })
            .putLong(KEY_LICENSE_CACHED_AT, System.currentTimeMillis())
            .putString(KEY_LICENSE_CUSTOMER_ID, request.customerId)
            .putString(KEY_LICENSE_CHANNEL, request.channel)
            .putString(KEY_LICENSE_HOST_APP, request.hostIdentity.applicationId)
            .apply()
    }

    private fun clearCachedLicense(context: Context) {
        context.applicationContext
            .getSharedPreferences(LICENSE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LICENSE_STATUS)
            .remove(KEY_LICENSE_ID)
            .remove(KEY_LICENSE_MESSAGE)
            .remove(KEY_LICENSE_EXPIRES_AT)
            .remove(KEY_LICENSE_GRANTED)
            .remove(KEY_LICENSE_CACHED_AT)
            .remove(KEY_LICENSE_CUSTOMER_ID)
            .remove(KEY_LICENSE_CHANNEL)
            .remove(KEY_LICENSE_HOST_APP)
            .apply()
    }

    private fun loadCachedGraceLicense(
        context: Context,
        request: OemLicenseRequest,
    ): OemLicenseSnapshot? {
        val prefs = context.applicationContext.getSharedPreferences(LICENSE_PREFS_NAME, Context.MODE_PRIVATE)
        val cachedAt = prefs.getLong(KEY_LICENSE_CACHED_AT, 0L)
        if (cachedAt <= 0L || System.currentTimeMillis() - cachedAt > LICENSE_GRACE_WINDOW_MS) {
            return null
        }
        if (prefs.getString(KEY_LICENSE_CUSTOMER_ID, "").orEmpty() != request.customerId) return null
        if (prefs.getString(KEY_LICENSE_CHANNEL, "").orEmpty() != request.channel) return null
        if (prefs.getString(KEY_LICENSE_HOST_APP, "").orEmpty() != request.hostIdentity.applicationId) return null
        val grantedCapabilities = prefs
            .getString(KEY_LICENSE_GRANTED, "")
            .orEmpty()
            .split(",")
            .mapNotNull { raw ->
                raw.trim().takeIf { it.isNotEmpty() }?.let {
                    runCatching { OemCapability.valueOf(it) }.getOrNull()
                }
            }
            .toSet()
            .ifEmpty { request.requestedCapabilities }
        return OemLicenseSnapshot.grace(
            licenseId = prefs.getString(KEY_LICENSE_ID, null),
            message = prefs.getString(KEY_LICENSE_MESSAGE, null)?.takeIf { it.isNotBlank() } ?: "grace",
            grantedCapabilities = grantedCapabilities,
            expiresAtMillis = prefs.getLong(KEY_LICENSE_EXPIRES_AT, 0L).takeIf { it > 0L },
        )
    }

    private fun buildInitialGraceLicense(request: OemLicenseRequest): OemLicenseSnapshot {
        return OemLicenseSnapshot.grace(
            licenseId = null,
            message = "grace",
            grantedCapabilities = request.requestedCapabilities,
            expiresAtMillis = System.currentTimeMillis() + LICENSE_GRACE_WINDOW_MS,
        )
    }
}

private data class OemRuntimeState(
    val application: Application,
    val config: OemSdkConfig,
    val status: OemSdkStatus,
)

private object OemManifestConfigParser {
    fun parse(context: Context): OemManifestConfig {
        val metaData = applicationMetaData(context)
        return OemManifestConfig(
            customerId = metaData?.getString(OemManifestMetaKeys.CUSTOMER_ID)?.trim(),
            appKey = metaData?.getString(OemManifestMetaKeys.APP_KEY)?.trim(),
            channel = metaData?.getString(OemManifestMetaKeys.CHANNEL)?.trim(),
            environment = OemEnvironment.parse(metaData?.getString(OemManifestMetaKeys.ENVIRONMENT)),
            enableAi = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_AI, false) ?: false,
            enableAutomation = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_AUTOMATION, false) ?: false,
            enableAutomationSettingsEntry = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_AUTOMATION_SETTINGS_ENTRY, false) ?: false,
            enableUi = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_UI, true) ?: true,
            apiBaseUrl = metaData?.getString(OemManifestMetaKeys.API_BASE_URL)?.trim(),
            analyticsBaseUrl = metaData?.getString(OemManifestMetaKeys.ANALYTICS_BASE_URL)?.trim(),
            aiMode = OemAiMode.parse(metaData?.getString(OemManifestMetaKeys.AI_MODE)),
            aiOpenAiBaseUrl = metaData?.getString(OemManifestMetaKeys.AI_OPENAI_BASE_URL)?.trim(),
            aiOpenAiApiKey = metaData?.getString(OemManifestMetaKeys.AI_OPENAI_API_KEY)?.trim(),
            aiOpenAiModel = metaData?.getString(OemManifestMetaKeys.AI_OPENAI_MODEL)?.trim(),
            sessionArchiveMode = OemSessionArchiveMode.parse(metaData?.getString(OemManifestMetaKeys.SESSION_ARCHIVE_MODE)),
            aiReplyDispatchMode = OemAiReplyDispatchMode.parse(metaData?.getString(OemManifestMetaKeys.AI_REPLY_DISPATCH_MODE)),
            materialDispatchMode = OemMaterialDispatchMode.parse(metaData?.getString(OemManifestMetaKeys.MATERIAL_DISPATCH_MODE)),
            materialNavigationMode = OemMaterialNavigationMode.parse(metaData?.getString(OemManifestMetaKeys.MATERIAL_NAVIGATION_MODE)),
            swipeDispatchMode = OemSwipeDispatchMode.parse(metaData?.getString(OemManifestMetaKeys.SWIPE_DISPATCH_MODE)),
            featureToggles = readFeatureToggles(metaData),
            enableDebugLog = metaData?.getBoolean(OemManifestMetaKeys.ENABLE_DEBUG_LOG, false) ?: false,
            aiReplyMaxImageSelection = readOptionalInt(metaData, OemManifestMetaKeys.AI_REPLY_MAX_IMAGE_SELECTION),
            sendTextMaxChars = readOptionalInt(metaData, OemManifestMetaKeys.SEND_TEXT_MAX_CHARS),
            builtinNeonOverlayEnabled = readOptionalBoolean(metaData, OemManifestMetaKeys.BUILTIN_NEON_OVERLAY_ENABLED),
            ocrEnabled = readOptionalBoolean(metaData, OemManifestMetaKeys.OCR_ENABLED),
        )
    }

    /**
     * 读取统一 SDK 的细粒度功能开关。
     *
     * 默认策略：除「基础 + 输入法核心」外全部默认关闭，企业开通后显式开启。
     * - automationEnabled 读取 ENABLE_AUTOMATION，未配置默认 false
     * - developerEnabled 优先读取 ENABLE_DEVELOPER；宿主仍在使用旧 key
     *   ENABLE_DEVELOPER_ENTRY_LEGACY 时按其取值回退，两者都未配置默认 false
     */
    private fun readFeatureToggles(metaData: Bundle?): OemFeatureToggles {
        if (metaData == null) return OemFeatureToggles()
        val developerEnabled = when {
            metaData.containsKey(OemManifestMetaKeys.ENABLE_DEVELOPER) ->
                metaData.getBoolean(OemManifestMetaKeys.ENABLE_DEVELOPER, false)
            metaData.containsKey(OemManifestMetaKeys.ENABLE_DEVELOPER_ENTRY_LEGACY) ->
                metaData.getBoolean(OemManifestMetaKeys.ENABLE_DEVELOPER_ENTRY_LEGACY, false)
            else -> false
        }
        return OemFeatureToggles(
            aiReplyEnabled = metaData.getBoolean(OemManifestMetaKeys.ENABLE_AI_REPLY, false),
            materialLibraryEnabled = metaData.getBoolean(OemManifestMetaKeys.ENABLE_MATERIAL_LIBRARY, false),
            aiReplyCustomUiEnabled = metaData.getBoolean(OemManifestMetaKeys.ENABLE_AI_REPLY_CUSTOM_UI, false),
            materialLibraryCustomUiEnabled = metaData.getBoolean(OemManifestMetaKeys.ENABLE_MATERIAL_LIBRARY_CUSTOM_UI, false),
            customDrawersEnabled = metaData.getBoolean(OemManifestMetaKeys.ENABLE_CUSTOM_DRAWERS, false),
            qaSessionEnabled = metaData.getBoolean(OemManifestMetaKeys.ENABLE_QA_SESSION, false),
            automationEnabled = metaData.getBoolean(OemManifestMetaKeys.ENABLE_AUTOMATION, false),
            developerEnabled = developerEnabled,
        )
    }

    @Suppress("DEPRECATION")
    private fun readOptionalInt(metaData: Bundle?, key: String): Int? {
        if (metaData == null || !metaData.containsKey(key)) return null
        return when (val value = metaData.get(key)) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun readOptionalBoolean(metaData: Bundle?, key: String): Boolean? {
        if (metaData == null || !metaData.containsKey(key)) return null
        return when (val value = metaData.get(key)) {
            is Boolean -> value
            is String -> value.trim().lowercase(Locale.ROOT).let { normalized ->
                when (normalized) {
                    "true", "1", "yes", "y", "on" -> true
                    "false", "0", "no", "n", "off" -> false
                    else -> null
                }
            }
            is Number -> value.toInt() != 0
            else -> null
        }
    }

    private fun applicationMetaData(context: Context): Bundle? {
        val pm = context.packageManager
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(
                context.packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        }
        return applicationInfo.metaData
    }
}

private object OemPackageIdentityResolver {
    fun resolve(context: Context): OemHostIdentity {
        val packageInfo = packageInfo(context)
        return OemHostIdentity(
            applicationId = context.packageName,
            certSha256 = resolveSigningDigest(packageInfo),
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        )
    }

    private fun packageInfo(context: Context): PackageInfo {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, flags)
        }
    }

    private fun resolveSigningDigest(packageInfo: PackageInfo): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.toList().orEmpty()
        }
        val firstSignature = signatures.firstOrNull() ?: return ""
        val digestBytes = MessageDigest.getInstance("SHA-256").digest(firstSignature.toByteArray())
        return digestBytes.joinToString(separator = "") { byte ->
            "%02x".format(Locale.ROOT, byte)
        }
    }
}
