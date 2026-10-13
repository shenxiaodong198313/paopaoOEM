package com.paopaoime.aikeyboard.midscene.agent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.paopaoime.aikeyboard.automation.bridge.AutomationBridgeFacade
import com.paopaoime.aikeyboard.ime.application.Launcher
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeScreenState
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class MidsceneAuthExpiredException(message: String = "登录态已失效，请重新登录") : IOException(message)

class MidsceneMaskedPlannerException(
    message: String = "大模型当前服务暂时不可用。",
    val debugDetail: String = ""
) : IOException(message)

private data class MidsceneMaskedClientError(
    val code: String,
    val userMessage: String
)

@Serializable
data class MidsceneAgentEvent(
    val badge: String = "",
    val title: String = "",
    val detail: String = ""
)

@Serializable
data class MidsceneAgentTimelineTask(
    val taskType: String = "",
    val taskSubType: String = "",
    val title: String = "",
    val detail: String = "",
    val thought: String = "",
    val reasoning: String = "",
    val instruction: String = "",
    val outputText: String = "",
    val totalTokens: Int? = null,
    val usageText: String = "",
    val promptTokens: Int = 0,
    val standardInputTokens: Int = 0,
    val completionTokens: Int = 0,
    val cacheCreationInputTokens: Int = 0,
    val cachedTokens: Int = 0,
    val cacheType: String = "",
    val plannerProtocol: String = "",
    val structuredFlowPhase: String = "",
    val realBubbleCost: Int = 0,
    val billedBubbleCost: Int = 0,
    val standardInputBubbleCost: Double = 0.0,
    val cacheCreationBubbleCost: Double = 0.0,
    val cachedBubbleCost: Double = 0.0,
    val completionBubbleCost: Double = 0.0,
    val bubbleEstimated: Boolean = false,
    val usageSummaryText: String = "",
    val status: String = "",
    val durationMs: Long? = null,
    val showTitleIcon: Boolean = false,
    val titleIcon: String = "",
    val statusIcon: String = ""
)

@Serializable
data class MidsceneAgentAction(
    val type: String = "",
    val label: String = "",
    val params: JsonObject = JsonObject(emptyMap()),
    val shell: String = ""
)

@Serializable
data class MidsceneAgentRuntimeStatus(
    val screenshotIncluded: Boolean = false,
    val deviceInfo: String = ""
)

@Serializable
data class MidsceneAgentClientMetrics(
    val screenshotBytes: Long = 0,
    val screenshotBase64Bytes: Long = 0,
    val requestBodyBytes: Long = 0,
    val uploadElapsedMs: Long? = null,
    val requestElapsedMs: Long? = null
)

@Serializable
data class MidsceneAgentSummary(
    val plannedStepCount: Int = 0,
    val actionCount: Int = 0,
    val shouldContinuePlanning: Boolean = true,
    val subGoalCount: Int = 0,
    val completedSubGoalIndexes: List<Int> = emptyList(),
    val incompleteSubGoalCount: Int = 0,
    val subGoalSummary: String = "",
    val currentSubGoal: String = "",
    val finalizeMessage: String = "",
    val finalizeSuccess: Boolean? = null,
    val nextAction: String = "",
    val bubbleAmount: Int = 0,
    val bubbleEstimated: Boolean = false,
    val qwenCacheTransportMode: String = "",
    val qwenCacheStrategyLabel: String = "",
    val cacheEligible: Boolean = false,
    val cacheHit: Boolean = false,
    val cacheType: String = "",
    val cacheCreationInputTokens: Int = 0,
    val cachedTokens: Int = 0,
    val cacheStatusMessage: String = "",
    val plannerResultCacheEnabled: Boolean = false,
    val plannerResultCacheEligible: Boolean = false,
    val plannerResultCacheMode: String = "",
    val plannerResultCacheHit: Boolean = false,
    val plannerResultCacheFallback: Boolean = false,
    val plannerResultCacheWrote: Boolean = false,
    val plannerResultCacheStatusMessage: String = "",
    val plannerResultCachePhase: String = "",
    val promptTokens: Int = 0,
    val standardInputTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val standardInputBubbleCost: Double = 0.0,
    val cacheCreationBubbleCost: Double = 0.0,
    val cachedBubbleCost: Double = 0.0,
    val completionBubbleCost: Double = 0.0,
    val cacheHitRatePercent: Double = 0.0
)

@Serializable
data class MidsceneAgentResolvedModelItem(
    val key: String = "",
    val modelName: String = "",
    val displayName: String = ""
)

@Serializable
data class MidsceneAgentResolvedModels(
    val defaultModel: MidsceneAgentResolvedModelItem = MidsceneAgentResolvedModelItem(),
    val planningModel: MidsceneAgentResolvedModelItem = MidsceneAgentResolvedModelItem(),
    val insightModel: MidsceneAgentResolvedModelItem = MidsceneAgentResolvedModelItem()
)

@Serializable
data class MidsceneAgentPlanResponse(
    val sessionId: String = "",
    val source: String = "",
    val mode: String = "",
    val runtime: MidsceneAgentRuntimeStatus = MidsceneAgentRuntimeStatus(),
    val clientMetrics: MidsceneAgentClientMetrics = MidsceneAgentClientMetrics(),
    val resolvedModels: MidsceneAgentResolvedModels = MidsceneAgentResolvedModels(),
    val rawTimeline: List<MidsceneAgentTimelineTask> = emptyList(),
    val events: List<MidsceneAgentEvent> = emptyList(),
    val actions: List<MidsceneAgentAction> = emptyList(),
    val summary: MidsceneAgentSummary = MidsceneAgentSummary()
)

@Serializable
data class MidsceneAgentShortcutVariables(
    val text: String = ""
)

@Serializable
data class MidsceneAgentShortcutTask(
    val taskKey: String,
    val title: String = "",
    val cacheEnabled: Boolean = false,
    val qwenCacheEnabled: Boolean = false,
    val midCacheEnabled: Boolean = false,
    val qwenCacheVersion: Int = 0,
    val midCacheVersion: Int = 0,
    val variables: MidsceneAgentShortcutVariables? = null
)

@Serializable
enum class MidsceneReportStrategy {
    @SerialName("off")
    OFF,

    @SerialName("async")
    ASYNC
}

@Serializable
data class MidsceneTaskReportTimelineItem(
    val type: String = "",
    val title: String = "",
    val detail: String = "",
    val status: String = "",
    val durationMs: Long? = null
)

@Serializable
private data class MidsceneAgentErrorPayload(
    val code: String = "",
    val message: String = ""
)

@Serializable
private data class MidsceneAgentEnvelope(
    val success: Boolean,
    val data: MidsceneAgentPlanResponse? = null,
    val error: MidsceneAgentErrorPayload? = null
)

@Serializable
private data class MidsceneTaskReportRequest(
    val sessionId: String,
    val prompt: String,
    val finalStatus: String,
    val clientTimeline: List<MidsceneTaskReportTimelineItem> = emptyList(),
    val reasonCode: String = "",
    val reasonMessage: String = "",
    val abnormalLog: String? = null,
    val extraDetail: JsonObject? = null
)

@Serializable
private data class MidsceneTaskReportEnvelope(
    val success: Boolean,
    val error: MidsceneAgentErrorPayload? = null
)

@Serializable
private data class MidsceneAgentPlanRequest(
    val requestId: String,
    val prompt: String,
    val source: String,
    val runtime: MidsceneAgentPlanRuntime,
    val modelSelection: MidsceneAgentModelSelectionPayload,
    val reportStrategy: MidsceneReportStrategy = MidsceneReportStrategy.OFF,
    val shortcutTask: MidsceneAgentShortcutTask? = null
)

@Serializable
private data class MidsceneAgentModelSelectionPayload(
    val defaultModelId: String,
    val defaultModelKey: String,
    val defaultModelName: String,
    val insightModelId: String? = null,
    val insightModelKey: String? = null,
    val insightModelName: String? = null,
    val planningModelId: String? = null,
    val planningModelKey: String? = null,
    val planningModelName: String? = null,
    val planningEnabled: Boolean = false,
    val insightEnabled: Boolean = false,
    val independentPlanningEnabled: Boolean = false,
    val comboModeEnabled: Boolean = false
)

@Serializable
private data class MidsceneAgentPlanRuntime(
    @SerialName("screenWidth")
    val screenWidth: Int,
    @SerialName("screenHeight")
    val screenHeight: Int,
    val devicePixelRatio: Float? = null,
    val screenshotShrinkFactor: Int = 1,
    val screenshotBase64: String,
    val currentApp: String? = null,
    val currentPage: String? = null,
    val windowState: MidsceneBridgeWindowState? = null,
    val executionFeedbackMessage: String? = null,
    val installedApps: List<MidsceneInstalledAppInfo> = emptyList(),
    val appListPermissionEnabled: Boolean = false,
    val deviceInfo: String,
    val accessibilityBridgeConnected: Boolean,
    val executorKind: String,
    val automationServiceConnected: Boolean,
    val automationAuthorized: Boolean,
    @SerialName("shizukuPaired")
    val legacyAutomationServiceConnected: Boolean = automationServiceConnected,
    @SerialName("shizukuGranted")
    val legacyAutomationAuthorized: Boolean = automationAuthorized
)

private data class MidsceneScreenshotPayloadMetrics(
    val screenshotBytes: Long = 0,
    val screenshotBase64Bytes: Long = 0,
    val requestBodyBytes: Long = 0
)

private data class MidscenePlanRequestMetrics(
    val requestBodyBytes: Long = 0,
    val uploadElapsedMs: Long? = null,
    val requestElapsedMs: Long? = null
)

private data class MidscenePlanExecutionResult(
    val response: MidsceneAgentPlanResponse,
    val requestMetrics: MidscenePlanRequestMetrics = MidscenePlanRequestMetrics()
)

private data class MidscenePreparedScreenshot(
    val screenshotBase64: String,
    val effectiveShrinkFactor: Int
)

private class MidscenePlanRequestEventListener : EventListener() {
    private val requestBodyStartNs = AtomicLong(0L)
    private val requestBodyEndNs = AtomicLong(0L)
    private val requestBodyBytes = AtomicLong(0L)

    override fun requestBodyStart(call: Call) {
        requestBodyStartNs.compareAndSet(0L, System.nanoTime())
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        requestBodyBytes.set(byteCount)
        requestBodyEndNs.compareAndSet(0L, System.nanoTime())
    }

    fun toMetrics(requestElapsedMs: Long): MidscenePlanRequestMetrics {
        val uploadElapsedMs = nanosToMillis(requestBodyEndNs.get() - requestBodyStartNs.get())
        return MidscenePlanRequestMetrics(
            requestBodyBytes = requestBodyBytes.get().coerceAtLeast(0L),
            uploadElapsedMs = uploadElapsedMs,
            requestElapsedMs = requestElapsedMs.coerceAtLeast(0L)
        )
    }

    private fun nanosToMillis(durationNs: Long): Long? {
        if (durationNs <= 0L) return null
        return TimeUnit.NANOSECONDS.toMillis(durationNs).coerceAtLeast(1L)
    }
}

object MidsceneAgentApiClient {
    private const val genericPlannerUnavailableMessage = "大模型当前服务暂时不可用。"
    private const val genericPlannerSupportMessage = "服务暂时不可用，请稍后重试。"
    private const val contentReviewBlockedMessage = "图片或视频内容未通过平台审核，任务已自动终止。"
    private const val connectTimeoutSeconds = 10L
    private const val plannerTimeoutSeconds = 240L
    private const val plannerCallTimeoutSeconds = 260L
    private const val plannerAttempts = 2
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(plannerTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(plannerTimeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(plannerCallTimeoutSeconds, TimeUnit.SECONDS)
        .build()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun runPlan(
        prompt: String,
        screenState: MidsceneBridgeScreenState,
        requestId: String,
        executionFeedbackMessage: String? = null,
        modelSelection: MidsceneExecutionModelSelection,
        reportStrategy: MidsceneReportStrategy = MidsceneReportStrategy.OFF,
        shortcutTask: MidsceneAgentShortcutTask? = null
    ): MidsceneAgentPlanResponse = withContext(Dispatchers.IO) {
        val baseUrl = OemEnterpriseConfig.resolveApiBaseUrl(Launcher.instance.context)
        require(baseUrl.isNotBlank()) { "AUTH_BASE_URL is blank" }
        val context = Launcher.instance.context
        val accessToken = AuthSessionStore.accessToken(context).trim()
        val refreshToken = AuthSessionStore.refreshToken(context).trim()
        if (accessToken.isBlank()) {
            throw MidsceneAuthExpiredException()
        }
        val installedApps = MidsceneInstalledAppCatalog.collect(context)
        val runtimeStatus = AutomationBridgeFacade.currentRuntimeStatus()
        val preparedScreenshot = prepareRuntimeScreenshot(
            screenshotBase64 = screenState.screenshotBase64,
            screenshotShrinkFactor = modelSelection.screenshotShrinkFactor
        )

        val payload = MidsceneAgentPlanRequest(
            requestId = requestId,
            prompt = prompt,
            source = "android-midscene-bridge",
            runtime = MidsceneAgentPlanRuntime(
                screenWidth = screenState.width,
                screenHeight = screenState.height,
                devicePixelRatio = screenState.devicePixelRatio,
                screenshotShrinkFactor = preparedScreenshot.effectiveShrinkFactor,
                screenshotBase64 = preparedScreenshot.screenshotBase64,
                currentApp = screenState.currentApp,
                currentPage = screenState.currentPage,
                windowState = screenState.windowState,
                executionFeedbackMessage = executionFeedbackMessage
                    ?.trim()
                    ?.ifBlank { null },
                installedApps = installedApps,
                appListPermissionEnabled = MidsceneInstalledAppCatalog.isEnabled(context),
                deviceInfo = runtimeStatus.deviceInfo,
                accessibilityBridgeConnected = runtimeStatus.ready,
                executorKind = runtimeStatus.executorKind,
                automationServiceConnected = runtimeStatus.backend != com.paopaoime.aikeyboard.automation.stellar.AutomationPrivilegedBackend.NONE,
                automationAuthorized = runtimeStatus.authorized
            ),
            modelSelection = MidsceneAgentModelSelectionPayload(
                defaultModelId = modelSelection.defaultModel.modelId,
                defaultModelKey = modelSelection.defaultModel.modelId,
                defaultModelName = modelSelection.defaultModel.displayName,
                insightModelId = modelSelection.insightModel?.modelId,
                insightModelKey = modelSelection.insightModel?.modelId,
                insightModelName = modelSelection.insightModel?.displayName,
                planningModelId = modelSelection.planningModel?.modelId,
                planningModelKey = modelSelection.planningModel?.modelId,
                planningModelName = modelSelection.planningModel?.displayName,
                planningEnabled = modelSelection.planningEnabled,
                insightEnabled = modelSelection.insightEnabled,
                independentPlanningEnabled = modelSelection.planningEnabled,
                comboModeEnabled = modelSelection.comboModeEnabled
            ),
            reportStrategy = reportStrategy,
            shortcutTask = shortcutTask
        )

        val requestBody = json.encodeToString(payload)
        val payloadMetrics = buildScreenshotPayloadMetrics(
            screenshotBase64 = preparedScreenshot.screenshotBase64,
            requestBody = requestBody
        )

        try {
            withTimeout(TimeUnit.SECONDS.toMillis(plannerCallTimeoutSeconds + 5L)) {
                val planResult = executePlanRequest(
                    baseUrl = baseUrl,
                    requestBody = requestBody,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    context = context
                )
                planResult.response.copy(
                    clientMetrics = MidsceneAgentClientMetrics(
                        screenshotBytes = payloadMetrics.screenshotBytes,
                        screenshotBase64Bytes = payloadMetrics.screenshotBase64Bytes,
                        requestBodyBytes =
                            planResult.requestMetrics.requestBodyBytes.takeIf { it > 0L }
                                ?: payloadMetrics.requestBodyBytes,
                        uploadElapsedMs = planResult.requestMetrics.uploadElapsedMs,
                        requestElapsedMs = planResult.requestMetrics.requestElapsedMs
                    )
                )
            }
        } catch (throwable: Throwable) {
            throw normalizePlannerError(throwable, baseUrl)
        }
    }

    suspend fun reportTaskResult(
        sessionId: String,
        prompt: String,
        finalStatus: String,
        clientTimeline: List<MidsceneTaskReportTimelineItem>,
        reasonCode: String = "",
        reasonMessage: String = "",
        abnormalLog: String? = null,
        extraDetail: JsonObject? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val safeSessionId = sessionId.trim()
        val safeStatus = finalStatus.trim()
        if (safeSessionId.isBlank() || safeStatus.isBlank()) {
            return@withContext false
        }
        val baseUrl = OemEnterpriseConfig.resolveApiBaseUrl(Launcher.instance.context)
        if (baseUrl.isBlank()) {
            return@withContext false
        }
        val context = Launcher.instance.context
        val accessToken = AuthSessionStore.accessToken(context).trim()
        val refreshToken = AuthSessionStore.refreshToken(context).trim()
        if (accessToken.isBlank()) {
            return@withContext false
        }
        val requestBody = json.encodeToString(
            MidsceneTaskReportRequest(
                sessionId = safeSessionId,
                prompt = prompt.trim(),
                finalStatus = safeStatus,
                clientTimeline = clientTimeline,
                reasonCode = reasonCode.trim(),
                reasonMessage = reasonMessage.trim(),
                abnormalLog = abnormalLog?.trim()?.ifBlank { null },
                extraDetail = extraDetail
            )
        )
        return@withContext runCatching {
            executeTaskReportRequest(
                baseUrl = baseUrl,
                requestBody = requestBody,
                accessToken = accessToken,
                refreshToken = refreshToken,
                context = context
            )
        }.getOrDefault(false)
    }

    private fun buildPlanRequest(
        baseUrl: String,
        requestBody: String,
        accessToken: String
    ): Request {
        return Request.Builder()
            .url("$baseUrl/api/v1/midscene/run")
            .post(requestBody.toRequestBody(jsonMediaType))
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
    }

    private fun buildTaskReportRequest(
        baseUrl: String,
        requestBody: String,
        accessToken: String
    ): Request {
        return Request.Builder()
            .url("$baseUrl/api/v1/midscene/tasks/report")
            .post(requestBody.toRequestBody(jsonMediaType))
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
    }

    private fun executePlanRequest(
        baseUrl: String,
        requestBody: String,
        accessToken: String,
        refreshToken: String,
        context: android.content.Context
    ): MidscenePlanExecutionResult {
        var lastError: Throwable? = null
        var currentAccessToken = accessToken
        for (attempt in 0 until plannerAttempts) {
            try {
                val request = buildPlanRequest(baseUrl, requestBody, currentAccessToken)
                var shouldRetryWithRefreshedToken = false
                val eventListener = MidscenePlanRequestEventListener()
                val requestStartedAtNs = System.nanoTime()
                client
                    .newBuilder()
                    .eventListener(eventListener)
                    .build()
                    .newCall(request)
                    .execute()
                    .use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (response.code == 401) {
                        if (refreshToken.isBlank()) {
                            throw MidsceneAuthExpiredException()
                        }
                        val refreshResult = AuthApiClient.refreshAccessToken(refreshToken)
                        if (!refreshResult.success || refreshResult.accessToken.isBlank()) {
                            throw MidsceneAuthExpiredException()
                        }
                        AuthSessionStore.updateTokens(
                            context,
                            refreshResult.accessToken,
                            refreshResult.refreshToken
                        )
                        currentAccessToken = refreshResult.accessToken
                        shouldRetryWithRefreshedToken = true
                        return@use
                    }
                    if (!response.isSuccessful) {
                        val serverMessage = extractServerMessage(bodyText)
                        val debugDetail =
                            "Midscene 规划请求失败(${response.code}): " +
                                serverMessage.ifBlank { "empty response" }
                        if (isContentReviewBlockedMessage(bodyText, serverMessage)) {
                            throw MidsceneMaskedPlannerException(
                                message = contentReviewBlockedMessage,
                                debugDetail = debugDetail
                            )
                        }
                        if (shouldMaskPlannerFailure(response.code, bodyText, serverMessage)) {
                            throw MidsceneMaskedPlannerException(
                                message = genericPlannerUnavailableMessage,
                                debugDetail = debugDetail
                            )
                        }
                        error(serverMessage.ifBlank { debugDetail })
                    }

                    val envelope = runCatching {
                        json.decodeFromString<MidsceneAgentEnvelope>(bodyText)
                    }.getOrElse { throwable ->
                        error("Midscene 返回解析失败: ${throwable.message ?: "unknown error"}")
                    }

                    if (!envelope.success || envelope.data == null) {
                        val message = envelope.error?.message?.ifBlank { null } ?: "Midscene 未返回可用计划"
                        val errorCode = envelope.error?.code?.trim().orEmpty()
                        if (isContentReviewBlockedMessage(bodyText, message, errorCode)) {
                            throw MidsceneMaskedPlannerException(
                                message = contentReviewBlockedMessage,
                                debugDetail = "Midscene 规划失败(code=$errorCode): $message"
                            )
                        }
                        if (shouldMaskPlannerFailure(200, bodyText, message, errorCode)) {
                            throw MidsceneMaskedPlannerException(
                                message = genericPlannerUnavailableMessage,
                                debugDetail = "Midscene 规划失败(code=$errorCode): $message"
                            )
                        }
                        error(message)
                    }
                        val requestElapsedMs =
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStartedAtNs)
                        return MidscenePlanExecutionResult(
                            response = envelope.data,
                            requestMetrics = eventListener.toMetrics(requestElapsedMs)
                        )
                }
                if (shouldRetryWithRefreshedToken) {
                    continue
                }
            } catch (throwable: Throwable) {
                lastError = throwable
                if (attempt == plannerAttempts - 1 || !shouldRetryPlannerRequest(throwable)) {
                    break
                }
            }
        }
        throw lastError ?: IOException("Midscene 请求失败")
    }

    private fun executeTaskReportRequest(
        baseUrl: String,
        requestBody: String,
        accessToken: String,
        refreshToken: String,
        context: android.content.Context
    ): Boolean {
        var currentAccessToken = accessToken
        repeat(2) {
            val request = buildTaskReportRequest(baseUrl, requestBody, currentAccessToken)
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (response.code == 401) {
                    if (refreshToken.isBlank()) {
                        return false
                    }
                    val refreshResult = AuthApiClient.refreshAccessToken(refreshToken)
                    if (!refreshResult.success || refreshResult.accessToken.isBlank()) {
                        return false
                    }
                    AuthSessionStore.updateTokens(
                        context,
                        refreshResult.accessToken,
                        refreshResult.refreshToken
                    )
                    currentAccessToken = refreshResult.accessToken
                    return@repeat
                }
                if (!response.isSuccessful) {
                    error("Midscene 任务上报失败(${response.code}): ${extractServerMessage(bodyText)}")
                }
                val envelope = runCatching {
                    json.decodeFromString<MidsceneTaskReportEnvelope>(bodyText)
                }.getOrNull()
                return envelope?.success == true
            }
        }
        return false
    }

    private fun extractServerMessage(bodyText: String): String {
        if (bodyText.isBlank()) return "empty response"
        return runCatching {
            val envelope = json.decodeFromString<MidsceneAgentEnvelope>(bodyText)
            envelope.error?.message?.ifBlank { null }
                ?: bodyText
        }.getOrDefault(bodyText)
    }

    private fun isContentReviewBlockedMessage(vararg texts: String): Boolean {
        return texts
            .map { it.lowercase() }
            .any { text ->
                text.contains("internalerror.algo.datainspectionfailed") ||
                    text.contains("input image data may contain inappropriate content") ||
                    text.contains("image data may contain inappropriate content") ||
                    text.contains("contain inappropriate content") ||
                    text.contains("未通过平台审核") ||
                    text.contains("不适宜内容")
            }
    }

    private fun shouldRetryPlannerRequest(throwable: Throwable): Boolean {
        if (
            isContentReviewBlockedMessage(
                throwable.message.orEmpty(),
                throwable.cause?.message.orEmpty()
            )
        ) {
            return false
        }
        if (
            throwable is UnknownHostException ||
            throwable is ConnectException ||
            throwable is SocketTimeoutException
        ) {
            return true
        }
        val retryableMessages = sequenceOf(throwable, throwable.cause)
            .filterNotNull()
            .mapNotNull { it.message?.lowercase() }
            .toList()
        return retryableMessages.any { message ->
            message.contains("connection abort") ||
                message.contains("socket hang up") ||
                message.contains("econnreset") ||
                message.contains("connection reset") ||
                message.contains("broken pipe") ||
                message.contains("unexpected end of stream") ||
                message.contains("stream was reset")
        }
    }

    private fun shouldMaskPlannerFailure(
        statusCode: Int,
        bodyText: String,
        serverMessage: String,
        errorCode: String = ""
    ): Boolean {
        val normalizedBody = bodyText.lowercase()
        val normalizedMessage = serverMessage.lowercase()
        val normalizedCode = errorCode.lowercase()
        if (isExplicitBillingPlannerFailure(statusCode, bodyText, serverMessage, errorCode)) {
            return false
        }
        if (isExplicitConfigPlannerFailure(bodyText, serverMessage, errorCode)) {
            return false
        }
        if (statusCode >= 500) return true
        if (normalizedBody.contains("<html")) return true
        if (normalizedBody.contains("bad gateway")) return true
        if (normalizedMessage.contains("bad gateway")) return true
        if (normalizedMessage.contains("gateway")) return true
        if (normalizedMessage.contains("upstream")) return true
        if (normalizedMessage.contains("当前服务端模型都在维护")) return true
        if (normalizedCode == "midscene_all_models_unavailable") return true
        if (resolveMaskedClientError(statusCode, serverMessage, errorCode) != null) return true
        return false
    }

    private fun isExplicitConfigPlannerFailure(
        bodyText: String,
        serverMessage: String,
        errorCode: String = ""
    ): Boolean {
        val normalizedBody = bodyText.lowercase()
        val normalizedMessage = serverMessage.lowercase()
        val normalizedCode = errorCode.lowercase()
        if (normalizedCode == "midscene_role_model_unavailable") return true
        val patterns = listOf(
            "没有可用模型，请联系管理员配置",
            "没有可用模型",
            "请联系管理员配置"
        )
        return patterns.any { pattern ->
            normalizedBody.contains(pattern) || normalizedMessage.contains(pattern)
        }
    }

    private fun resolveMaskedClientError(
        statusCode: Int,
        serverMessage: String,
        errorCode: String = ""
    ): MidsceneMaskedClientError? {
        val normalizedMessage = serverMessage.lowercase()
        val normalizedCode = errorCode.lowercase()
        return when {
            normalizedMessage.contains("midscene_agent_url") && normalizedMessage.contains("未配置") ->
                MidsceneMaskedClientError(
                    code = "MSP-42201",
                    userMessage = "$genericPlannerSupportMessage 错误码：MSP-42201"
                )
            normalizedMessage.contains("midscene_executor_url") && normalizedMessage.contains("未配置") ->
                MidsceneMaskedClientError(
                    code = "MSP-42202",
                    userMessage = "$genericPlannerSupportMessage 错误码：MSP-42202"
                )
            normalizedMessage.contains("auth_base_url") && normalizedMessage.contains("blank") ->
                MidsceneMaskedClientError(
                    code = "MSP-40001",
                    userMessage = "$genericPlannerSupportMessage 错误码：MSP-40001"
                )
            normalizedCode == "midscene_planner_failed" && normalizedMessage.contains("未配置") ->
                MidsceneMaskedClientError(
                    code = "MSP-42209",
                    userMessage = "$genericPlannerSupportMessage 错误码：MSP-42209"
                )
            statusCode == 422 && normalizedMessage.contains("未配置") ->
                MidsceneMaskedClientError(
                    code = "MSP-42200",
                    userMessage = "$genericPlannerSupportMessage 错误码：MSP-42200"
                )
            else -> null
        }
    }

    private fun isExplicitBillingPlannerFailure(
        statusCode: Int,
        bodyText: String,
        serverMessage: String,
        errorCode: String = ""
    ): Boolean {
        val normalizedBody = bodyText.lowercase()
        val normalizedMessage = serverMessage.lowercase()
        val normalizedCode = errorCode.lowercase()
        if (statusCode == 402) return true
        if (normalizedCode == "midscene_model_account_unavailable") return true
        val patterns = listOf(
            "access denied, please make sure your account is in good standing",
            "insufficient balance",
            "account balance",
            "payment required",
            "billing details",
            "arrear",
            "欠费",
            "余额不足"
        )
        return patterns.any { pattern ->
            normalizedBody.contains(pattern) || normalizedMessage.contains(pattern)
        }
    }

    private fun normalizePlannerError(throwable: Throwable, baseUrl: String): Throwable {
        return when (throwable) {
            is MidsceneMaskedPlannerException -> throwable

            is UnknownHostException -> IOException(
                "无法解析 ${requestHostLabel(baseUrl)}，请检查当前网络或 DNS",
                throwable
            )

            is ConnectException -> IOException(
                "已连接到 ${requestHostLabel(baseUrl)}，但 Midscene 服务暂时不可用",
                throwable
            )

            is SocketTimeoutException,
            is TimeoutCancellationException -> IOException(
                "Midscene 规划超时，请稍后重试",
                throwable
            )

            is InterruptedIOException -> {
                val timeoutLikeMessage = throwable.message?.contains("timeout", ignoreCase = true) == true
                if (timeoutLikeMessage) {
                    IOException("Midscene 规划超时，请稍后重试", throwable)
                } else {
                    IOException(
                        throwable.message?.ifBlank { null } ?: "Midscene 请求失败",
                        throwable
                    )
                }
            }

            is MidsceneAuthExpiredException -> throwable

            is IllegalArgumentException -> {
                val masked = resolveMaskedClientError(
                    statusCode = 400,
                    serverMessage = throwable.message.orEmpty()
                )
                if (masked != null) {
                    MidsceneMaskedPlannerException(
                        message = masked.userMessage,
                        debugDetail = throwable.message.orEmpty()
                    )
                } else {
                    IOException(
                        throwable.message?.ifBlank { null } ?: "Midscene 请求失败",
                        throwable
                    )
                }
            }

            is IOException ->
                if (
                    isContentReviewBlockedMessage(
                        throwable.message.orEmpty(),
                        throwable.cause?.message.orEmpty()
                    )
                ) {
                    MidsceneMaskedPlannerException(
                        message = contentReviewBlockedMessage,
                        debugDetail = throwable.message.orEmpty()
                    )
                } else {
                    IOException(
                        throwable.message?.ifBlank { null } ?: "Midscene 请求失败",
                        throwable
                    )
                }

            else -> IOException(
                throwable.message?.ifBlank { null } ?: "Midscene 请求失败",
                throwable
            )
        }
    }

    private fun requestHostLabel(baseUrl: String): String {
        return runCatching { Request.Builder().url(baseUrl).build().url.host }
            .getOrNull()
            ?.ifBlank { null }
            ?: baseUrl
    }

    private fun buildScreenshotPayloadMetrics(
        screenshotBase64: String,
        requestBody: String
    ): MidsceneScreenshotPayloadMetrics {
        val normalizedBase64 = screenshotBase64.substringAfter("base64,", screenshotBase64).trim()
        val screenshotBase64Bytes = normalizedBase64.toByteArray(Charsets.UTF_8).size.toLong()
        val padding = normalizedBase64.takeLastWhile { it == '=' }.length
        val screenshotBytes =
            if (normalizedBase64.isBlank()) {
                0L
            } else {
                ((normalizedBase64.length.toLong() * 3L) / 4L - padding).coerceAtLeast(0L)
            }
        return MidsceneScreenshotPayloadMetrics(
            screenshotBytes = screenshotBytes,
            screenshotBase64Bytes = screenshotBase64Bytes,
            requestBodyBytes = requestBody.toByteArray(Charsets.UTF_8).size.toLong()
        )
    }

    private fun prepareRuntimeScreenshot(
        screenshotBase64: String,
        screenshotShrinkFactor: Int
    ): MidscenePreparedScreenshot {
        val normalizedFactor = screenshotShrinkFactor.coerceAtLeast(1)
        if (normalizedFactor == 1) {
            return MidscenePreparedScreenshot(
                screenshotBase64 = screenshotBase64,
                effectiveShrinkFactor = 1
            )
        }

        return runCatching {
            val prefix = screenshotBase64.substringBefore("base64,", "")
            val body = screenshotBase64.substringAfter("base64,", screenshotBase64).trim()
            val screenshotBytes = Base64.decode(body, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(screenshotBytes, 0, screenshotBytes.size)
                ?: throw IOException("decode screenshot failed")
            val targetWidth = (bitmap.width / normalizedFactor.toFloat()).toInt().coerceAtLeast(1)
            val targetHeight = (bitmap.height / normalizedFactor.toFloat()).toInt().coerceAtLeast(1)
            if (targetWidth == bitmap.width && targetHeight == bitmap.height) {
                bitmap.recycle()
                return@runCatching MidscenePreparedScreenshot(
                    screenshotBase64 = screenshotBase64,
                    effectiveShrinkFactor = 1
                )
            }

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            bitmap.recycle()
            val output = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            scaledBitmap.recycle()
            val scaledBytes = output.toByteArray()
            output.close()
            val scaledBase64Body = Base64.encodeToString(scaledBytes, Base64.NO_WRAP)
            val scaledBase64 = buildString {
                append(prefix.ifBlank { "data:image/png;" })
                append("base64,")
                append(scaledBase64Body)
            }
            MidscenePreparedScreenshot(
                screenshotBase64 = scaledBase64,
                effectiveShrinkFactor = 1
            )
        }.getOrElse {
            MidscenePreparedScreenshot(
                screenshotBase64 = screenshotBase64,
                effectiveShrinkFactor = 1
            )
        }
    }
}
