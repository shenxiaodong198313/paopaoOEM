package com.paopaoime.aikeyboard.sdk.ai

import android.app.Application
import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class LegacyManagedSession(
    val modelId: String,
    val displayName: String,
    val sessionId: String?,
    val configPath: String,
    val supportOmni: Boolean
)

internal class LegacyLlmSession(
    val modelId: String,
    val displayName: String,
    val configPath: String
)

internal data class LegacyCloudModel(
    val modelId: String,
    val displayName: String,
    val isDecision: Boolean
)

internal object AiKeyboardAiCoreBridge {
    private val cloudModelMap = ConcurrentHashMap<String, LegacyCloudModel>()
    private var globalSession: LegacyLlmSession? = null
    private var globalModelId: String? = null

    private const val LOCAL_RUNTIME_REMOVED_MESSAGE =
        "OEM 版本已移除本地模型运行时，当前 SDK 不再支持本地模型会话。"

    fun initializeLegacy(application: Application) {
    }

    fun initializeLogCollector() {
        // OEM 已移除本地模型日志采集实现，保留空实现以兼容现有初始化流程。
    }

    fun generateDiffusionOutputPath(context: Context, fileNamePrefix: String): String {
        val outputDir = File(context.getExternalFilesDir("generated"), "diffusion")
            .takeIf { it.exists() || it.mkdirs() }
            ?: File(context.filesDir, "generated/diffusion").apply { mkdirs() }
        val safePrefix = fileNamePrefix.trim().ifBlank { "diffusion" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(outputDir, "${safePrefix}_${System.currentTimeMillis()}.png").absolutePath
    }

    fun createManagedSession(
        modelId: String,
        displayName: String,
        sessionId: String?,
        configPath: String,
        supportOmni: Boolean
    ): LegacyManagedSession {
        throw UnsupportedOperationException(
            LOCAL_RUNTIME_REMOVED_MESSAGE
        )
    }

    fun managedSessionSetKeepHistory(session: LegacyManagedSession, keepHistory: Boolean) {
        throw UnsupportedOperationException(LOCAL_RUNTIME_REMOVED_MESSAGE)
    }

    fun managedSessionLoad(session: LegacyManagedSession) {
        throw UnsupportedOperationException(LOCAL_RUNTIME_REMOVED_MESSAGE)
    }

    fun managedSessionGenerate(
        session: LegacyManagedSession,
        prompt: String,
        params: Map<String, Any>,
        onProgress: (String?) -> Boolean
    ): HashMap<String, Any> {
        throw UnsupportedOperationException(LOCAL_RUNTIME_REMOVED_MESSAGE)
    }

    fun managedSessionRelease(session: LegacyManagedSession) {
    }

    fun releaseGlobalSession() {
        globalSession = null
        globalModelId = null
    }

    fun getGlobalSession(): LegacyLlmSession? {
        return globalSession
    }

    fun globalSessionGenerate(
        session: LegacyLlmSession,
        prompt: String,
        onProgress: (String?) -> Boolean
    ): HashMap<String, Any> {
        throw UnsupportedOperationException(LOCAL_RUNTIME_REMOVED_MESSAGE)
    }

    fun getCurrentModelId(): String? {
        return globalModelId
    }

    suspend fun loadModel(context: Context, modelId: String, displayName: String, configPath: String) {
        throw UnsupportedOperationException(
            LOCAL_RUNTIME_REMOVED_MESSAGE
        )
    }

    fun loadCloudModels(context: Context): List<LegacyCloudModel> {
        return emptyList()
    }

    fun replaceCloudModels(models: List<LegacyCloudModel>) {
        cloudModelMap.clear()
        models.forEach { cloudModelMap[it.modelId] = it }
    }

    fun findCloudModel(modelId: String): LegacyCloudModel? {
        return cloudModelMap[modelId]
    }

    suspend fun requestCloudReplyStream(
        model: LegacyCloudModel,
        history: List<AiKeyboardCloudMessage>,
        onProgress: (String) -> Unit
    ) {
        throw UnsupportedOperationException("Cloud streaming is not available in current backend")
    }
}
