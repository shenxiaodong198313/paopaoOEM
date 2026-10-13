package com.paopaoime.aikeyboard.materialhub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.os.SystemClock
import android.util.Log
import com.paopaoime.aikeyboard.compat.agent.network.ContentItem
import com.paopaoime.aikeyboard.compat.agent.network.ImageUrl
import com.paopaoime.aikeyboard.compat.agent.network.Message
import com.paopaoime.aikeyboard.compat.agent.network.ModelClient
import com.paopaoime.aikeyboard.ime.billing.BubbleDisplayHelper
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.materialhub.logging.AiReplyTaskLogger
import com.paopaoime.aikeyboard.oem.OemAiMode
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyDispatchMode
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyErrorCode
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyPromptContext
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyRequest
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyResult
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyResultCallback
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyScreenshot
import com.paopaoime.aikeyboard.sdk.core.OemDeveloperAiConfigDefaults
import com.paopaoime.aikeyboard.sdk.core.OemDeveloperResolvedAiReplyConfig
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.fcitx.fcitx5.android.input.materialhub.AiCommunicatePanelController

object AiCommunicateReplyService {
    private const val TAG = "AiCommunicateReply"
    private const val PREFERRED_MODEL_NAME = "qwen3.6-flash"

    enum class HistoryState(val wireValue: String) {
        NO_HISTORY("no_history"),
        LIMITED_HISTORY("limited_history"),
        NORMAL_HISTORY("normal_history"),
    }

    data class GenerationResult(
        val sessionId: String,
        val replies: List<String>,
        val ocrContextPreview: String,
        val ocrSourceText: String,
        val screenshotPreviewPath: String?,
        val screenshotPreviewPaths: List<String>,
        val historyState: HistoryState,
        val actualCaptureCount: Int,
        val promptText: String,
        val mode: AiCommunicatePanelController.GenerateMode,
        val modelName: String = "",
        val provider: String = "",
        val tokenUsageEvent: AuthApiClient.TokenUsageEvent? = null,
    )

    data class GenerationContext(
        val screenshotPreviewPath: String?,
        val screenshotPreviewPaths: List<String> = emptyList(),
        val ocrSourceText: String = "",
        val historyState: HistoryState = HistoryState.NO_HISTORY,
        val actualCaptureCount: Int = 0,
    )

    class UserVisibleFailure(
        override val message: String,
        val errorCode: String? = null,
    ) : IllegalStateException(message)

    data class Tone(
        val key: String,
        val label: String,
        val promptHint: String,
    )

    suspend fun generateReplies(
        context: Context,
        tone: Tone,
        mode: AiCommunicatePanelController.GenerateMode,
        sessionId: String,
        refreshSeed: String = UUID.randomUUID().toString(),
        allowSnapshotCapture: Boolean = true,
        existingContext: GenerationContext? = null,
        taskLogger: AiReplyTaskLogger? = null,
    ): GenerationResult {
        OemEnterpriseConfig.resolveAiReplyRestriction(context.applicationContext)?.let { restriction ->
            throw UserVisibleFailure(restriction.message, OemAiReplyErrorCode.CONFIG_MISSING)
        }
        if (!OemEnterpriseConfig.isAiReplyEntryEnabled(context.applicationContext)) {
            throw UserVisibleFailure(
                "当前宿主已关闭 AI 回复功能，请联系管理员开启后再试",
                OemAiReplyErrorCode.CONFIG_MISSING,
            )
        }
        val startedAt = SystemClock.elapsedRealtime()
        val ocrStartedAt = SystemClock.elapsedRealtime()
        val reusedPreviewPath = existingContext?.screenshotPreviewPath?.takeIf { path ->
            path.isNotBlank() && File(path).exists()
        }
        val reusedPreviewPaths =
            existingContext?.screenshotPreviewPaths
                ?.filter { path -> path.isNotBlank() && File(path).exists() }
                .orEmpty()
        val reusedOcrSourceText = existingContext?.ocrSourceText.orEmpty()
        val ocrSnapshot =
            if (reusedOcrSourceText.isNotBlank()) {
                null
            } else {
                AiCommunicateOcrCacheStore.snapshot()
                    ?: if (allowSnapshotCapture) {
                        AiCommunicateOcrCacheStore.captureFresh(context.applicationContext, force = false)
                    } else {
                        null
                    }
            }
        val screenshotPreviewPath = reusedPreviewPath ?: AiCommunicateOcrCacheStore.previewImagePath()
        val screenshotPreviewPaths =
            when {
                reusedPreviewPaths.isNotEmpty() -> reusedPreviewPaths
                !screenshotPreviewPath.isNullOrBlank() -> listOf(screenshotPreviewPath)
                else -> emptyList()
            }
        val ocrSourceText = reusedOcrSourceText.ifBlank { ocrSnapshot?.text.orEmpty() }
        val actualCaptureCount =
            existingContext?.actualCaptureCount?.takeIf { it >= 0 }
                ?: if (
                    mode == AiCommunicatePanelController.GenerateMode.FAST_LLM ||
                    mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY
                ) {
                    screenshotPreviewPaths.size
                } else if (screenshotPreviewPath.isNullOrBlank() && ocrSourceText.isBlank()) {
                    0
                } else {
                    1
                }
        val historyState =
            existingContext?.historyState
                ?: resolveHistoryState(
                    mode = mode,
                    ocrSourceText = ocrSourceText,
                    actualCaptureCount = actualCaptureCount,
                )
        val ocrContextPreview =
            if (
                mode == AiCommunicatePanelController.GenerateMode.FAST_LLM ||
                mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY
            ) {
                buildLlmModePreview(
                    historyState = historyState,
                    actualCaptureCount = actualCaptureCount,
                    source = if (mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY) "图片" else "截图",
                )
            } else {
                buildDisplayOcrContext(ocrSourceText)
            }
        val replyScene =
            if (
                mode == AiCommunicatePanelController.GenerateMode.FAST_LLM ||
                mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY
            ) {
                if (screenshotPreviewPath.isNullOrBlank()) ReplyScene.GREETING else ReplyScene.NORMAL
            } else {
                analyzeConversationMeta(ocrSourceText).scene
            }
        Log.d(
            TAG,
            "generateReplies mode=${mode.name} snapshotHit=${ocrSnapshot != null} reusedContext=${existingContext != null} textLength=${ocrSourceText.length} attempts=${ocrSnapshot?.attempts ?: 0} preview=${!screenshotPreviewPath.isNullOrBlank()} costMs=${SystemClock.elapsedRealtime() - ocrStartedAt}"
        )
        val dispatchMode = OemEnterpriseConfig.resolveAiReplyDispatchMode(context.applicationContext)
        val replyConfig = OemEnterpriseConfig.resolveDeveloperAiReplyConfig(context.applicationContext)
        val temperature = OemEnterpriseConfig.resolveDeveloperTemperature(context.applicationContext)
        val topP = OemEnterpriseConfig.resolveDeveloperTopP(context.applicationContext)
        val maxTokens = OemEnterpriseConfig.resolveDeveloperMaxTokens(context.applicationContext)
        val hostAssembler = OemEnterpriseConfig.resolveAiReplyPromptAssembler(context.applicationContext)
        val hostPrompts =
            if (hostAssembler != null) {
                runCatching {
                    hostAssembler.assemble(
                        OemAiReplyPromptContext(
                            generateMode = mode.name,
                            toneKey = tone.key,
                            toneLabel = tone.label,
                            tonePromptHint = tone.promptHint,
                            ocrText = ocrSourceText,
                            screenshotCount = screenshotPreviewPaths.size,
                            replyCount = replyConfig.replyCount,
                            hostApplicationId = context.applicationContext.packageName,
                        )
                    )
                }.getOrNull()
                    ?.takeIf { it.systemPrompt.isNotBlank() && it.userPrompt.isNotBlank() }
            } else {
                null
            }
        if (hostAssembler != null && hostPrompts == null) {
            Log.w(TAG, "generateReplies host prompt assembler returned blank prompts, fallback to builtin template")
        }
        val systemPrompt = hostPrompts?.systemPrompt ?: replyConfig.systemPrompt
        val prompt =
            hostPrompts?.userPrompt
                ?: buildPrompt(
                    tone = tone,
                    replyConfig = replyConfig,
                    mode = mode,
                    refreshSeed = refreshSeed,
                    ocrContext = ocrSourceText,
                    historyState = historyState,
                    actualCaptureCount = actualCaptureCount,
                )
        if (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM && screenshotPreviewPath.isNullOrBlank()) {
            throw UserVisibleFailure(
                "当前没有获取到聊天截图，已自动尝试恢复截图服务，请稍后重试",
                OemAiReplyErrorCode.CAPTURE_FAILED,
            )
        }
        if (
            mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY &&
            screenshotPreviewPaths.isEmpty() &&
            screenshotPreviewPath.isNullOrBlank()
        ) {
            throw UserVisibleFailure("当前没有获取到可用聊天图片，请重新选择后重试", OemAiReplyErrorCode.CAPTURE_FAILED)
        }
        if (mode == AiCommunicatePanelController.GenerateMode.OCR &&
            screenshotPreviewPath.isNullOrBlank() &&
            ocrContextPreview.isBlank()
        ) {
            throw UserVisibleFailure(
                "当前没有识别到聊天内容，请检查截图能力后重试",
                OemAiReplyErrorCode.CAPTURE_FAILED,
            )
        }
        if (dispatchMode != OemAiReplyDispatchMode.CLOUD_ONLY) {
            val hostOutcome =
                generateRepliesViaHost(
                    context = context,
                    mode = mode,
                    tone = tone,
                    sessionId = sessionId,
                    systemPrompt = systemPrompt,
                    userPrompt = prompt,
                    ocrSourceText = ocrSourceText,
                    screenshotPreviewPaths = screenshotPreviewPaths,
                    replyCount = replyConfig.replyCount,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                    taskLogger = taskLogger,
                    generationTimeoutMs = OemEnterpriseConfig.resolveAiReplyGenerationTimeoutMs(context.applicationContext),
                )
            when (hostOutcome) {
                is HostGenerationOutcome.Success -> {
                    taskLogger?.info(
                        phase = "generation",
                        event = "host_generation_finished",
                        title = "宿主回调回复生成完成",
                        detail = "replyCount=${hostOutcome.replies.size} model=${hostOutcome.modelName.ifBlank { "host" }}",
                        data =
                            JSONObject()
                                .put("replyCount", hostOutcome.replies.size)
                                .put("modelName", hostOutcome.modelName.ifBlank { "host" }),
                    )
                    return GenerationResult(
                        sessionId = sessionId,
                        replies = hostOutcome.replies,
                        ocrContextPreview = ocrContextPreview,
                        ocrSourceText = ocrSourceText,
                        screenshotPreviewPath = screenshotPreviewPath,
                        screenshotPreviewPaths = screenshotPreviewPaths,
                        historyState = historyState,
                        actualCaptureCount = actualCaptureCount,
                        promptText = prompt,
                        mode = mode,
                        modelName = hostOutcome.modelName.ifBlank { "host" },
                        provider = "host_callback",
                        tokenUsageEvent = null,
                    )
                }
                is HostGenerationOutcome.Failure -> {
                    taskLogger?.info(
                        phase = "generation",
                        event = "host_generation_failed",
                        title = "宿主回调生成失败",
                        detail = hostOutcome.message,
                        data =
                            JSONObject()
                                .put("message", hostOutcome.message)
                                .put("errorCode", hostOutcome.errorCode ?: ""),
                    )
                    if (dispatchMode == OemAiReplyDispatchMode.HOST_ONLY) {
                        throw UserVisibleFailure(
                            message = hostOutcome.message,
                            errorCode = hostOutcome.errorCode,
                        )
                    }
                    Log.w(
                        TAG,
                        "generateReplies host channel unavailable, fallback to cloud: ${hostOutcome.message}",
                    )
                }
            }
        }
        val runtimeResolution = buildRuntime(context)
        val runtime = runtimeResolution.config
        if (runtime == null) {
            Log.w(
                TAG,
                "generateReplies failed: runtime unavailable textLength=${ocrContextPreview.length} reason=${runtimeResolution.failureMessage}"
            )
            throw UserVisibleFailure(
                runtimeResolution.failureMessage.ifBlank {
                    "当前未获取到可用 AI 模型配置，请稍后重试"
                },
                OemAiReplyErrorCode.CONFIG_MISSING,
            )
        }
        taskLogger?.setRuntimeString("provider", inferProvider(runtime.baseUrl))
        taskLogger?.setRuntimeString("modelName", runtime.modelName)
        taskLogger?.info(
            phase = "generation",
            event = "runtime_resolved",
            title = "模型运行时已解析",
            detail = "provider=${inferProvider(runtime.baseUrl)} model=${runtime.modelName}",
            data =
                JSONObject()
                    .put("provider", inferProvider(runtime.baseUrl))
                    .put("modelName", runtime.modelName),
        )
        Log.d(TAG, "runtime model=${runtime.modelName} baseUrl=${runtime.baseUrl}")
        val client = ModelClient(
            baseUrl = runtime.baseUrl,
            apiKey = runtime.apiKey,
            modelName = runtime.modelName,
            isGemini = false,
            forceOpenAiCompat = true,
            networkTimeoutMs = OemEnterpriseConfig.resolveAiReplyNetworkTimeoutMs(context.applicationContext),
        )
        var usageEvent: AuthApiClient.TokenUsageEvent? = null
        var usagePromptTokens = 0
        var usageCompletionTokens = 0
        var usageTotalTokens = 0
        var usageTimestamp = 0L
        var usageModelName = runtime.modelName
        val userMessage =
            if (
                mode == AiCommunicatePanelController.GenerateMode.FAST_LLM ||
                mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY
            ) {
                buildVisionMessage(prompt = prompt, screenshotPreviewPaths = screenshotPreviewPaths)
            } else {
                Message(role = "user", content = prompt)
            }
        val raw = client.sendRequest(
            history = listOf(
                Message(
                    role = "system",
                    content = systemPrompt,
                ),
                userMessage,
            ),
            screenshot = null,
            isReasoning = false,
            thinkingEnabled = false,
            maxTokensOverride = maxTokens,
            temperatureOverride = temperature,
            topPOverride = topP,
            onTokenUsage = { usage ->
                usagePromptTokens += usage.promptTokens
                usageCompletionTokens += usage.completionTokens
                usageTotalTokens += usage.totalTokens
                usageTimestamp = maxOf(usageTimestamp, usage.timestamp)
                if (usage.model.isNotBlank()) {
                    usageModelName = usage.model
                }
                val usageProvider = inferProvider(runtime.baseUrl)
                usageEvent =
                    AuthApiClient.TokenUsageEvent(
                        clientEventId = sessionId,
                        provider = usageProvider,
                        model = usageModelName,
                        bizType = "ai_communicate",
                        bizId = sessionId,
                        accountOwnerType = "",
                        accountOwnerId = "",
                        accountOwnerName = "",
                        promptTokens = usagePromptTokens,
                        completionTokens = usageCompletionTokens,
                        totalTokens = usageTotalTokens,
                        pointsCost = estimatePremiumBubbleCost(
                            provider = usageProvider,
                            model = usageModelName,
                            promptTokens = usagePromptTokens,
                            completionTokens = usageCompletionTokens,
                            totalTokens = usageTotalTokens,
                        ),
                        requestId = sessionId,
                        occurredAt = java.time.Instant.ofEpochMilli(usageTimestamp).toString(),
                    )
            },
        ).trim()
        if (raw.startsWith("Error:", ignoreCase = true) || raw.startsWith("Network Error:", ignoreCase = true)) {
            Log.w(TAG, "generateReplies failed due to model error: $raw")
            throw UserVisibleFailure(
                "当前 AI 模型调用失败，请稍后重试",
                classifyModelError(raw),
            )
        }
        Log.d(TAG, "generateReplies success totalCostMs=${SystemClock.elapsedRealtime() - startedAt} rawLength=${raw.length}")
        val replies = ensureTargetReplyCount(
            initialReplies = parseReplies(raw),
            client = client,
            systemPrompt = systemPrompt,
            originalPrompt = prompt,
            replyConfig = replyConfig,
            mode = mode,
            screenshotPreviewPaths = screenshotPreviewPaths,
            refreshSeed = refreshSeed,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            onTokenUsage = { usage ->
                usagePromptTokens += usage.promptTokens
                usageCompletionTokens += usage.completionTokens
                usageTotalTokens += usage.totalTokens
                usageTimestamp = maxOf(usageTimestamp, usage.timestamp)
                if (usage.model.isNotBlank()) {
                    usageModelName = usage.model
                }
                val usageProvider = inferProvider(runtime.baseUrl)
                usageEvent =
                    AuthApiClient.TokenUsageEvent(
                        clientEventId = sessionId,
                        provider = usageProvider,
                        model = usageModelName,
                        bizType = "ai_communicate",
                        bizId = sessionId,
                        accountOwnerType = "",
                        accountOwnerId = "",
                        accountOwnerName = "",
                        promptTokens = usagePromptTokens,
                        completionTokens = usageCompletionTokens,
                        totalTokens = usageTotalTokens,
                        pointsCost = estimatePremiumBubbleCost(
                            provider = usageProvider,
                            model = usageModelName,
                            promptTokens = usagePromptTokens,
                            completionTokens = usageCompletionTokens,
                            totalTokens = usageTotalTokens,
                        ),
                        requestId = sessionId,
                        occurredAt = java.time.Instant.ofEpochMilli(usageTimestamp).toString(),
                    )
            },
        )
        usageEvent?.let { event ->
            taskLogger?.setRuntimeNumber("promptTokens", event.promptTokens)
            taskLogger?.setRuntimeNumber("completionTokens", event.completionTokens)
            taskLogger?.setRuntimeNumber("totalTokens", event.totalTokens)
            taskLogger?.info(
                phase = "generation",
                event = "token_usage_received",
                title = "模型消耗统计已记录",
                detail = "prompt=${event.promptTokens} completion=${event.completionTokens} total=${event.totalTokens}",
                data =
                    JSONObject()
                        .put("promptTokens", event.promptTokens)
                        .put("completionTokens", event.completionTokens)
                        .put("totalTokens", event.totalTokens),
            )
        }
        taskLogger?.info(
            phase = "generation",
            event = "generation_finished",
            title = "回复生成完成",
            detail = "replyCount=${replies.size}",
            data = JSONObject().put("replyCount", replies.size),
        )
        return GenerationResult(
            sessionId = sessionId,
            replies = replies,
            ocrContextPreview = ocrContextPreview,
            ocrSourceText = ocrSourceText,
            screenshotPreviewPath = screenshotPreviewPath,
            screenshotPreviewPaths = screenshotPreviewPaths,
            historyState = historyState,
            actualCaptureCount = actualCaptureCount,
            promptText = prompt,
            mode = mode,
            modelName = runtime.modelName,
            provider = inferProvider(runtime.baseUrl),
            tokenUsageEvent = usageEvent,
        )
    }

    private const val HOST_SCREENSHOT_MAX_WIDTH = 1280

    private sealed class HostGenerationOutcome {
        data class Success(
            val replies: List<String>,
            val modelName: String,
        ) : HostGenerationOutcome()

        data class Failure(
            val message: String,
            val errorCode: String? = null,
        ) : HostGenerationOutcome()
    }

    /**
     * Host callback channel: hands off the screenshot and the already-assembled Prompt to the host,
     * the host calls its own large model and returns the generated reply content via callback.
     *
     * Returns [HostGenerationOutcome.Success] on host channel success;
     * for cases where the delegate is not registered, the host rejects it, times out, fails,
     * or the returned content is empty, uniformly returns [HostGenerationOutcome.Failure],
     * and the caller decides whether to fall back to the domain configuration channel based on the dispatch mode.
     */
    private suspend fun generateRepliesViaHost(
        context: Context,
        mode: AiCommunicatePanelController.GenerateMode,
        tone: Tone,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        ocrSourceText: String,
        screenshotPreviewPaths: List<String>,
        replyCount: Int,
        temperature: Double,
        topP: Double,
        maxTokens: Int,
        taskLogger: AiReplyTaskLogger?,
        generationTimeoutMs: Long,
    ): HostGenerationOutcome {
        if (!OemImeSdk.isInitialized()) {
            return HostGenerationOutcome.Failure(
                message = "OEM SDK is not initialized. AI reply host callback is unavailable",
                errorCode = OemAiReplyErrorCode.CONFIG_MISSING,
            )
        }
        if (!OemImeSdk.isAiReplyHostDelegateRegistered()) {
            return HostGenerationOutcome.Failure(
                message = "The host has not registered the AI reply callback (OemAiReplyHostDelegate). Please contact the host app to complete the integration",
                errorCode = OemAiReplyErrorCode.CONFIG_MISSING,
            )
        }
        val screenshots = screenshotPreviewPaths.mapNotNull(::encodeScreenshotForHost)
        val request =
            OemAiReplyRequest(
                requestId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                generateMode = mode.name,
                toneKey = tone.key,
                toneLabel = tone.label,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                ocrText = ocrSourceText,
                screenshots = screenshots,
                replyCount = replyCount,
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens,
                timestamp = System.currentTimeMillis(),
                hostApplicationId = context.applicationContext.packageName,
            )
        taskLogger?.info(
            phase = "generation",
            event = "host_dispatch_started",
            title = "AI reply request dispatched to host",
            detail = "requestId=${request.requestId} screenshots=${screenshots.size} ocrTextLength=${ocrSourceText.length}",
            data =
                JSONObject()
                    .put("requestId", request.requestId)
                    .put("screenshots", screenshots.size)
                    .put("ocrTextLength", ocrSourceText.length),
        )
        val result =
            withTimeoutOrNull(generationTimeoutMs) {
                suspendCancellableCoroutine<OemAiReplyResult?> { continuation ->
                    val delivered = AtomicBoolean(false)
                    val callback =
                        OemAiReplyResultCallback { hostResult ->
                            if (delivered.compareAndSet(false, true) && continuation.isActive) {
                                continuation.resume(hostResult)
                            }
                        }
                    val accepted =
                        runCatching {
                            OemImeSdk.dispatchAiReplyGeneration(request, callback)
                        }.getOrDefault(false)
                    if (!accepted && delivered.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        if (result == null) {
            return HostGenerationOutcome.Failure(
                message = "The host callback timed out or refused to handle the request. Please try again later",
                errorCode = OemAiReplyErrorCode.TIMEOUT,
            )
        }
        if (!result.success) {
            val hostMessage = result.errorMessage?.takeIf { it.isNotBlank() }
                ?: "Host large model generation failed. Please try again later"
            return HostGenerationOutcome.Failure(
                message = hostMessage,
                errorCode = result.errorCode ?: classifyModelError(hostMessage),
            )
        }
        val replies = result.replies.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (replies.isEmpty()) {
            return HostGenerationOutcome.Failure(
                message = "The host callback returned empty reply content. Please try again later",
                errorCode = OemAiReplyErrorCode.RESULT_INVALID,
            )
        }
        return HostGenerationOutcome.Success(replies = replies, modelName = result.modelName.orEmpty())
    }

    private fun encodeScreenshotForHost(path: String?): OemAiReplyScreenshot? {
        if (path.isNullOrBlank()) return null
        val source = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return null
        val previewBitmap =
            if (source.width > HOST_SCREENSHOT_MAX_WIDTH) {
                val ratio = HOST_SCREENSHOT_MAX_WIDTH.toFloat() / source.width.toFloat()
                val scaled =
                    Bitmap.createScaledBitmap(
                        source,
                        HOST_SCREENSHOT_MAX_WIDTH,
                        (source.height * ratio).roundToInt().coerceAtLeast(1),
                        true,
                    )
                if (scaled !== source) source.recycle()
                scaled
            } else {
                source
            }
        val stream = ByteArrayOutputStream()
        val encoded = runCatching { previewBitmap.compress(Bitmap.CompressFormat.JPEG, 72, stream) }.getOrDefault(false)
        val result =
            if (encoded) {
                OemAiReplyScreenshot(
                    imageBase64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP),
                    mimeType = "image/jpeg",
                    width = previewBitmap.width,
                    height = previewBitmap.height,
                )
            } else {
                null
            }
        previewBitmap.recycle()
        return result
    }

    private data class RuntimeConfig(
        val baseUrl: String,
        val apiKey: String,
        val modelName: String,
    )

    private data class RuntimeResolution(
        val config: RuntimeConfig?,
        val failureMessage: String = "",
    )

    private enum class ReplyScene {
        GREETING,
        FOLLOW_UP,
        NORMAL,
    }

    private data class ConversationMeta(
        val scene: ReplyScene,
        val hasCustomerMessage: Boolean,
        val hasDocumentHint: Boolean,
    )

    private fun buildRuntime(context: Context): RuntimeResolution {
        val aiMode = OemEnterpriseConfig.resolveAiMode(context)
        val directConfig = OemEnterpriseConfig.resolveDirectAiConfig(context)
        val accessToken = AuthSessionStore.accessToken(context).trim()
        val refreshToken = AuthSessionStore.refreshToken(context).trim()
        if (aiMode == OemAiMode.DIRECT) {
            return directRuntimeOrFailure(directConfig)
        }
        if (accessToken.isBlank() && aiMode != OemAiMode.DUAL) {
            Log.i(
                TAG,
                "buildRuntime request cloud model config without local cloud session"
            )
        }
        if (accessToken.isBlank() && aiMode == OemAiMode.DUAL && directConfig != null) {
            return directRuntimeOrFailure(directConfig)
        }
        val result = AuthApiClient.getPreferredCloudModelConfig(
            accessToken = accessToken,
            refreshToken = refreshToken,
            preferredModelName = PREFERRED_MODEL_NAME,
        )
        result.newAccessToken?.takeIf { it.isNotBlank() }?.let { newAccess ->
            AuthSessionStore.updateTokens(context, newAccess, result.newRefreshToken)
        }
        val config = result.config
        if (config == null) {
            if (aiMode == OemAiMode.DUAL && directConfig != null) {
                Log.i(TAG, "buildRuntime fallback to direct AI config after proxy resolution failed")
                return directRuntimeOrFailure(directConfig)
            }
            return RuntimeResolution(
                config = null,
                failureMessage = normalizeRuntimeFailureMessage(
                    rawMessage = result.message,
                    authInvalid = result.authInvalid,
                ),
            )
        }
        Log.d(
            TAG,
            "buildRuntime resolved preferredModel=$PREFERRED_MODEL_NAME model=${config.modelName} baseUrl=${config.baseUrl} message=${result.message}"
        )
        return RuntimeResolution(
            config = RuntimeConfig(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                modelName = config.modelName.ifBlank { PREFERRED_MODEL_NAME },
            )
        )
    }

    private fun directRuntimeOrFailure(config: com.paopaoime.aikeyboard.oem.OemDirectAiConfig?): RuntimeResolution {
        if (config == null) {
            return RuntimeResolution(
                config = null,
                failureMessage = "当前未配置可用的直连 AI 模型，请检查宿主下发的 OpenAI 兼容参数",
            )
        }
        return RuntimeResolution(
            config = RuntimeConfig(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                modelName = config.modelName,
            ),
        )
    }

    private fun classifyModelError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("network error") ||
                lower.contains("unknownhostexception") ||
                lower.contains("sockettimeoutexception") ||
                lower.contains("connectexception") ||
                lower.contains("sslhandshakeexception") ||
                lower.contains("failed to connect") ||
                lower.contains("unable to resolve host") -> OemAiReplyErrorCode.NETWORK
            lower.contains("data_inspection") ||
                lower.contains("inappropriate") ||
                lower.contains("content_filter") ||
                lower.contains("content policy") ||
                lower.contains("moderation") ||
                lower.contains("sensitive") ||
                raw.contains("敏感") ||
                raw.contains("违规") ||
                raw.contains("审核") ||
                raw.contains("质检") -> OemAiReplyErrorCode.CONTENT_BLOCKED
            else -> OemAiReplyErrorCode.MODEL_BUSY
        }
    }

    private fun normalizeRuntimeFailureMessage(rawMessage: String, authInvalid: Boolean): String {
        if (authInvalid) {
            return "当前未获取到可用 AI 模型配置，请检查宿主下发的模型设置"
        }
        val message = rawMessage.trim()
        return when {
            message.isBlank() -> "当前未获取到可用 AI 模型配置，请稍后重试"
            message.contains("网络异常") -> "AI 服务连接失败，请稍后重试"
            message.contains("连接服务超时") -> "AI 服务连接超时，请稍后重试"
            message.contains("未找到可用的模型配置") -> "当前未找到可用 AI 模型配置，请检查模型服务设置"
            message.contains("获取模型设置失败") -> "AI 模型配置获取失败，请检查宿主或服务端配置"
            message.contains("登录态已失效") -> "当前未获取到可用 AI 模型配置，请检查宿主下发的模型设置"
            else -> message
        }
    }

    private fun buildPrompt(
        tone: Tone,
        replyConfig: OemDeveloperResolvedAiReplyConfig,
        mode: AiCommunicatePanelController.GenerateMode,
        refreshSeed: String,
        ocrContext: String,
        historyState: HistoryState,
        actualCaptureCount: Int,
    ): String {
        if (
            mode == AiCommunicatePanelController.GenerateMode.FAST_LLM ||
            mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY
        ) {
            return buildVisionPrompt(
                tone = tone,
                replyConfig = replyConfig,
                refreshSeed = refreshSeed,
                historyState = historyState,
                actualCaptureCount = actualCaptureCount,
                sourceDescription =
                    if (mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY) {
                        "用户主动选择的一组聊天图片"
                    } else {
                        "一组微信客户沟通界面的连续截图"
                    },
                imageReplyMode = mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY,
            )
        }
        val trimmedContext = buildDisplayOcrContext(ocrContext)
        val conversationMeta = analyzeConversationMeta(trimmedContext)
        val protocolSection = buildSharedHistoryProtocol(historyState, actualCaptureCount)
        val contextSection = if (trimmedContext.isBlank()) {
            """
当前没有识别到明确聊天内容。
请直接生成适合刚打开会话时可发送的问候语、破冰语、开场白类短回复。
优先给出自然、礼貌、低打扰的话术，不要生成“认同观点”“推进任务”这类依赖上下文的句子。
""".trimIndent()
        } else if (!conversationMeta.hasCustomerMessage) {
            """
以下文案是一组微信客户和我的对话消息：
$trimmedContext

当前可见内容里只有 `我：` 的历史发言，还没有看到客户的新消息。
这说明当前更像“我发出上一条后，准备补一句自然跟进或礼貌催回”，而不是确认收到、不是先看文档、也不是承接客户刚发来的资料。

理解规则：
1. 优先生成轻量跟进、确认方便沟通、礼貌催回、补充说明类短句。
2. 禁止输出“收到”“我先看一下”“我先核对一下”“我先看文档”这类只有客户先发资料时才成立的话。
3. 不要把我自己刚发出的上一条原样重复一遍。
4. 不要显得咄咄逼人，语气保持自然、礼貌、低打扰。
""".trimIndent()
        } else {
            """
以下文案是一组微信客户和我的对话消息：
$trimmedContext

理解规则：
1. `客户：` 表示客户或对方发来的消息，`我：` 表示我已经发出的消息。
2. `[文档] 标题` 表示会话里出现了文档消息，标题对回复意图有强参考价值。
3. 图片、视频、头像、顶部昵称、时间、按钮、输入法文案都已经尽量过滤，不要把它们当正文。
4. 默认优先围绕最近一轮客户消息来回复；如果最后一条是 `我：`，请结合最近几轮会话生成自然的跟进话术。
5. 不要直接喊对方昵称，除非上下文里明确需要这样称呼。
""".trimIndent()
        }
        val sceneSpecificRule =
            when (conversationMeta.scene) {
                ReplyScene.GREETING ->
                    "9. 当前没有明确聊天内容时，只生成问候语、破冰语、开场白，不要生成收到类或看文档类句子。"
                ReplyScene.FOLLOW_UP ->
                    "9. 当前只有我方历史发言时，只生成自然跟进、确认方便沟通、礼貌催回类句子，禁止输出“收到/我先看/我先核对/我先看文档”。"
                ReplyScene.NORMAL ->
                    if (conversationMeta.hasDocumentHint) {
                        "9. 只有在客户消息里明确出现文档、附件、资料时，才可以生成“收到，我先看一下/我先核对一下”这类句子。"
                    } else {
                        "9. 没有明确文档或资料时，不要生成“我先看文档”“我先核对资料”这类句子。"
                    }
            }
        return """
请根据以上会话，直接生成 ${replyConfig.replyCount} 条${resolveLanguageDisplay(replyConfig.language)}即时通讯短回复候选，输出为 JSON 数组字符串。

要求：
1. 场景是微信、企业微信、闲鱼这类 IM 沟通。
2. 默认语境是“我现在准备回复客户”，不是写总结，不是分析聊天。
3. 遇到文档消息时，可以自然体现“收到、我先看一下、稍后回复、我先核对一下”这类动作感。
4. 不要长篇大论，每条尽量控制在 ${replyConfig.minChars} 到 ${replyConfig.maxChars} 个字符之间。
5. ${if (replyConfig.avoidNickname) "不要称呼对方昵称，不要出现“神神神小仙”这类直接念名字的写法。" else "是否称呼对方昵称请根据上下文自行判断，避免生硬。"}
6. 不要输出序号、分类标题、解释、括号说明，只输出 JSON 数组。
7. ${replyConfig.replyCount} 条内容要尽量有差异，不要只是同义改写。
8. 如果能看出是多轮对话，请综合最近几轮语境生成，不要只围绕最后一句做机械改写。
${sceneSpecificRule}
10. 除非客户明确在表达观点、方案、判断，否则不要输出“这个点我认同”“你这么说也有道理”这类抽象认同句。
11. 每条都要像真人即时发送的话，不要写成 AI 腔或客服公告。
12. ${tone.promptHint}
13. ${if (replyConfig.allowEmoji) "可以包含少量自然表情，但不要每条都带。" else "不要使用表情符号，保持纯文本即时沟通风格。"}
14. business_role = ${replyConfig.businessRole}
15. language = ${replyConfig.language}
16. reply_length_policy = ${replyConfig.replyLengthPolicy}
17. 本次随机因子：$refreshSeed

$protocolSection

$contextSection

示例格式：
["收到啦，我晚点回你","哈哈你这句挺有意思","可以呀，我觉得没问题"]
""".trimIndent()
    }

    private fun buildDisplayOcrContext(ocrContext: String): String {
        return ocrContext
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(24)
            .joinToString("\n")
    }

    private fun buildVisionPrompt(
        tone: Tone,
        replyConfig: OemDeveloperResolvedAiReplyConfig,
        refreshSeed: String,
        historyState: HistoryState,
        actualCaptureCount: Int,
        sourceDescription: String,
        imageReplyMode: Boolean = false,
    ): String {
        return """
你现在看到的是${sourceDescription}，请直接从图片中理解聊天上下文并生成 ${replyConfig.replyCount} 条${resolveLanguageDisplay(replyConfig.language)}即时通讯回复候选，输出为 JSON 数组字符串。

${buildSharedHistoryProtocol(historyState, actualCaptureCount)}

界面理解规则：
1. 这是微信聊天页，左侧白色气泡通常表示客户发言，右侧绿色气泡通常表示我发言。
2. 多张截图是同一会话的连续历史，顺序按从旧到新排列；相邻截图可能存在少量重复内容，请你自行识别，不要把重复误判成新消息。
3. ${if (imageReplyMode) "这是用户主动选择的 N 张聊天图片，你必须严格按照用户选择顺序依次理解，后一张永远比前一张更新；最后一张代表当前最新会话位置，最终回复必须优先服务于最后一张及其最近几轮上下文。" else "默认把最后一张截图视为当前最新位置，生成回复时优先围绕最后一张以及它之前最近几轮上下文。"}
4. 顶部昵称、时间、返回按钮、右上角菜单、输入框、键盘、头像徽标、状态栏，都不是正式聊天内容。
5. 如果 `history_state = no_history`，说明当前没有有效历史聊天内容，请生成首次触达、新客户开场、破冰式回复，不要假设双方已有上下文。
6. 如果 `history_state = limited_history`，说明当前只有少量历史内容，请避免过度脑补未出现的聊天背景。
7. 如果出现文档卡片、表格文件、附件标题，请把它理解为客户发来的正式资料；回复可以偏向“收到，我先看一下”“我先核对后回复你”。
8. 如果出现图片、视频、表情等媒体消息，但没有明确文字内容，不要凭空脑补具体细节，只围绕当前可见文本和会话阶段回复。
9. 如果最后明显是客户刚发来的消息，优先围绕客户最后一轮内容回复；如果最后明显是我刚发出的消息，可以补一句跟进、确认方便沟通或礼貌催回的话术。
10. 如果截图里只看到我自己上一条消息、没看到客户新消息，禁止输出“收到”“我先看一下”“我先核对一下”“我先看文档”这类承接客户资料的句子。
11. 如果前面几张图片和最后一张图片存在内容冲突，以顺序更靠后的图片为准；不要把旧图片里的状态当成最新状态。

输出要求：
1. 场景是微信、企业微信、闲鱼这类 IM 沟通。
2. 默认语境是“我现在准备回复客户”，不是总结，不是分析，不要解释你的判断过程。
3. 每条尽量控制在 ${replyConfig.minChars} 到 ${replyConfig.maxChars} 个字符之间。
4. 不要输出序号、分类标题、解释、括号说明，只输出 JSON 数组。
5. ${replyConfig.replyCount} 条内容要尽量有差异，覆盖确认、推进、收到后查看、礼貌追问、开场问候等不同方向。
6. 除非截图明确是观点讨论，否则不要生成“这个点我认同”“你这么说也有道理”这类抽象认同句。
7. 没有明确看到文档、资料、附件时，不要生成“我先看文档”“我先核对资料”这类句子。
8. ${if (replyConfig.avoidNickname) "不要直接称呼客户昵称，除非截图正文里明确需要这样称呼。" else "是否称呼客户昵称请根据截图正文与场景自行判断。"}
9. 每条都要像真人即时发送的话，不要写成 AI 腔或客服公告。
10. ${tone.promptHint}
11. 你输出的是“当前此刻准备发送给客户的最新回复”，不是给每张图分别配一句。
12. ${if (replyConfig.allowEmoji) "可以包含少量自然表情，但不要每条都带。" else "不要使用表情符号，保持纯文本即时沟通风格。"}
13. business_role = ${replyConfig.businessRole}
14. language = ${replyConfig.language}
15. reply_length_policy = ${replyConfig.replyLengthPolicy}
16. 本次随机因子：$refreshSeed

示例格式：
["收到，我先看一下","好的，我稍后回复你","你好呀，方便沟通一下吗"]
""".trimIndent()
    }

    private fun buildVisionMessage(prompt: String, screenshotPreviewPaths: List<String>): Message {
        val items = mutableListOf(ContentItem(type = "text", text = prompt))
        screenshotPreviewPaths.forEach { path ->
            encodeScreenshotAsDataUrl(path)?.let { dataUrl ->
            items += ContentItem(type = "image_url", imageUrl = ImageUrl(url = dataUrl))
            }
        }
        return Message(role = "user", content = items)
    }

    private fun encodeScreenshotAsDataUrl(path: String?): String? {
        val file = path?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        if (!file.exists()) return null
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val bitmap = scaleBitmapForVision(original)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, output)
        if (bitmap !== original) {
            bitmap.recycle()
        }
        original.recycle()
        val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    private fun scaleBitmapForVision(bitmap: Bitmap): Bitmap {
        val maxWidth = 1280
        if (bitmap.width <= maxWidth) return bitmap
        val scale = maxWidth / bitmap.width.toFloat()
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, maxWidth, targetHeight, true)
    }

    private fun buildLlmModePreview(
        historyState: HistoryState,
        actualCaptureCount: Int,
        source: String,
    ): String {
        return when (historyState) {
            HistoryState.NO_HISTORY -> "当前没有有效历史聊天内容，本次按新客户或新会话场景生成开场回复。"
            HistoryState.LIMITED_HISTORY -> "已获取 $actualCaptureCount 张聊天$source，但历史内容有限，正在按少量上下文场景生成回复。"
            HistoryState.NORMAL_HISTORY -> "已按顺序获取 $actualCaptureCount 张聊天$source，正在理解连续上下文并生成回复。"
        }
    }

    private fun inferProvider(baseUrl: String): String {
        val normalized = baseUrl.lowercase()
        return when {
            normalized.contains("dashscope") -> "dashscope"
            normalized.contains("volces") || normalized.contains("ark") -> "volc"
            normalized.contains("bigmodel") || normalized.contains("zhipu") -> "zhipu"
            normalized.contains("liblib") -> "liblib"
            else -> "other"
        }
    }

    private fun estimatePremiumBubbleCost(
        provider: String,
        model: String,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
    ): Int {
        return BubbleDisplayHelper.estimateBubbleCost(
            provider = provider,
            model = model,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            pointsCost = 0,
        )?.amount ?: 0
    }

    private fun parseReplies(raw: String): List<String> {
        val normalized = raw
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val parsedJson = runCatching {
            val arrayText = normalized.substringAfter('[', missingDelimiterValue = "").let { body ->
                if (body.isBlank()) normalized else "[${body.substringBeforeLast(']', "")}]"
            }
            JSONArray(arrayText)
        }.getOrNull()
        val candidates = mutableListOf<String>()
        if (parsedJson != null) {
            for (index in 0 until parsedJson.length()) {
                val item = parsedJson.optString(index).sanitizeReply()
                if (item.isNotBlank()) {
                    candidates += item
                }
            }
        }
        if (candidates.isEmpty()) {
            normalized.lineSequence()
                .map { line ->
                    line.replace(Regex("""^\s*[\-\*\d\.\)、]+\s*"""), "").sanitizeReply()
                }
                .filter { it.isNotBlank() }
                .forEach(candidates::add)
        }
        val deduped = candidates
            .map(String::trim)
            .filter { it.length in 2..32 }
            .distinct()
            .take(OemDeveloperAiConfigDefaults.MAX_REPLY_COUNT)
            .toMutableList()
        if (deduped.isEmpty()) {
            throw UserVisibleFailure("AI 返回结果解析失败，请重试", OemAiReplyErrorCode.RESULT_INVALID)
        }
        return deduped.take(OemDeveloperAiConfigDefaults.MAX_REPLY_COUNT)
    }

    private suspend fun ensureTargetReplyCount(
        initialReplies: List<String>,
        client: ModelClient,
        systemPrompt: String,
        originalPrompt: String,
        replyConfig: OemDeveloperResolvedAiReplyConfig,
        mode: AiCommunicatePanelController.GenerateMode,
        screenshotPreviewPaths: List<String>,
        refreshSeed: String,
        temperature: Double,
        topP: Double,
        maxTokens: Int,
        onTokenUsage: (ModelClient.TokenUsage) -> Unit,
    ): List<String> {
        val merged = initialReplies.toMutableList()
        if (merged.size >= replyConfig.replyCount) {
            return merged.take(replyConfig.replyCount)
        }
        for (retryIndex in 0 until 2) {
            val missingCount = replyConfig.replyCount - merged.size
            val repairPrompt =
                """
基于同一段聊天上下文，继续补齐回复候选。

当前已经生成的候选：
${merged.joinToString("\n") { "- $it" }}

请不要重复上面的内容，直接补足到总共 ${replyConfig.replyCount} 条。
本次至少新增 ${missingCount} 条。
必须只输出 JSON 数组字符串，不要输出解释，不要输出 Markdown，不要输出对象结构。
每条仍然要像真人即时通讯短回复，长度控制在 ${replyConfig.minChars} 到 ${replyConfig.maxChars} 个字符之间。
如果前面的内容方向太集中，请补齐不同表达方向。
reply_length_policy = ${replyConfig.replyLengthPolicy}
补齐随机因子：$refreshSeed-retry-${retryIndex + 1}

原始任务说明如下：
$originalPrompt
""".trimIndent()
            val repairMessage =
                if (
                    mode == AiCommunicatePanelController.GenerateMode.FAST_LLM ||
                    mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY
                ) {
                    buildVisionMessage(
                        prompt = repairPrompt,
                        screenshotPreviewPaths = screenshotPreviewPaths,
                    )
                } else {
                    Message(role = "user", content = repairPrompt)
                }
            val repairRaw = client.sendRequest(
                history = listOf(
                    Message(role = "system", content = systemPrompt),
                    repairMessage,
                ),
                screenshot = null,
                isReasoning = false,
                thinkingEnabled = false,
                maxTokensOverride = maxTokens,
                temperatureOverride = temperature,
                topPOverride = topP,
                onTokenUsage = onTokenUsage,
            ).trim()
            if (repairRaw.startsWith("Error:", ignoreCase = true) || repairRaw.startsWith("Network Error:", ignoreCase = true)) {
                Log.w(TAG, "ensureTargetReplyCount repair failed due to model error: $repairRaw")
                break
            }
            parseReplies(repairRaw).forEach { candidate ->
                if (candidate !in merged) {
                    merged += candidate
                }
            }
            if (merged.size >= replyConfig.replyCount) {
                return merged.take(replyConfig.replyCount)
            }
        }
        if (merged.size < replyConfig.replyCount) {
            throw UserVisibleFailure("AI 返回回复数量不足，请重试", OemAiReplyErrorCode.RESULT_INVALID)
        }
        return merged.take(replyConfig.replyCount)
    }

    private fun String.sanitizeReply(): String {
        return trim()
            .replace("\"", "")
            .replace("“", "")
            .replace("”", "")
            .replace(Regex("""^\[|\]$"""), "")
            .replace(Regex("""\s+"""), " ")
    }

    private fun analyzeConversationMeta(ocrContext: String): ConversationMeta {
        val lines =
            buildDisplayOcrContext(ocrContext)
                .lineSequence()
                .map(String::trim)
                .filter { it.isNotBlank() }
                .toList()
        val hasCustomer = lines.any { it.startsWith("客户：") }
        val hasMine = lines.any { it.startsWith("我：") }
        val hasDocumentHint = lines.any { it.contains("[文档]") || it.contains("文档") || it.contains("附件") || it.contains("资料") }
        val scene =
            when {
                !hasCustomer && !hasMine -> ReplyScene.GREETING
                !hasCustomer && hasMine -> ReplyScene.FOLLOW_UP
                else -> ReplyScene.NORMAL
            }
        return ConversationMeta(
            scene = scene,
            hasCustomerMessage = hasCustomer,
            hasDocumentHint = hasDocumentHint,
        )
    }

    private fun resolveHistoryState(
        mode: AiCommunicatePanelController.GenerateMode,
        ocrSourceText: String,
        actualCaptureCount: Int,
    ): HistoryState {
        val trimmedContext = buildDisplayOcrContext(ocrSourceText)
        if (trimmedContext.isBlank()) {
            return if (
                (mode == AiCommunicatePanelController.GenerateMode.FAST_LLM ||
                    mode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY) &&
                actualCaptureCount > 0
            ) {
                HistoryState.NO_HISTORY
            } else {
                HistoryState.NO_HISTORY
            }
        }
        val lineCount = trimmedContext.lineSequence().count()
        return when {
            actualCaptureCount >= 3 || lineCount >= 6 -> HistoryState.NORMAL_HISTORY
            else -> HistoryState.LIMITED_HISTORY
        }
    }

    private fun buildSharedHistoryProtocol(
        historyState: HistoryState,
        actualCaptureCount: Int,
    ): String {
        return """
上下文协议：
1. history_state = ${historyState.wireValue}
2. actual_capture_count = $actualCaptureCount
3. 当 history_state = no_history 时，表示当前没有有效历史聊天记录，请按首次触达、新客户开场或破冰场景生成。
4. 当 history_state = limited_history 时，表示当前只拿到少量聊天历史，请避免脑补未出现的背景。
5. 当 history_state = normal_history 时，表示当前已有足够历史上下文，可按正常会话理解。
""".trimIndent()
    }

    private fun resolveLanguageDisplay(language: String): String {
        return when (language.trim().lowercase()) {
            "zh", "zh-cn", "zh_hans", "chinese" -> "中文"
            "en", "en-us", "english" -> "英文"
            else -> language.ifBlank { "中文" }
        }
    }

}
