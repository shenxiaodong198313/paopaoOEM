package com.paopaoime.aikeyboard.sdk.ai

import android.content.Context

data class AiKeyboardCloudMessage(
    val role: String,
    val content: String
)

data class AiKeyboardCloudModel(
    val modelId: String,
    val displayName: String,
    val isDecision: Boolean
)

class AiKeyboardChatSession internal constructor(
    private val rawSession: LegacyLlmSession
) {
    fun generate(prompt: String, onProgress: (String?) -> Boolean): HashMap<String, Any> {
        return AiKeyboardAiBackendRouter.bridge().globalSessionGenerate(rawSession, prompt, onProgress)
    }
}

class AiKeyboardManagedSession internal constructor(
    private val rawSession: LegacyManagedSession
) {
    fun setKeepHistory(keepHistory: Boolean) {
        AiKeyboardAiBackendRouter.bridge().managedSessionSetKeepHistory(rawSession, keepHistory)
    }

    fun load() {
        AiKeyboardAiBackendRouter.bridge().managedSessionLoad(rawSession)
    }

    fun generate(
        prompt: String,
        params: Map<String, Any>,
        onProgress: (String?) -> Boolean
    ): HashMap<String, Any> {
        return AiKeyboardAiBackendRouter.bridge().managedSessionGenerate(rawSession, prompt, params, onProgress)
    }

    fun release() {
        AiKeyboardAiBackendRouter.bridge().managedSessionRelease(rawSession)
    }
}

object AiKeyboardAiSdk {
    fun createSession(
        modelId: String,
        displayName: String,
        sessionId: String?,
        configPath: String,
        supportOmni: Boolean = true
    ): AiKeyboardManagedSession {
        val session = AiKeyboardAiBackendRouter.bridge()
            .createManagedSession(modelId, displayName, sessionId, configPath, supportOmni)
        return AiKeyboardManagedSession(session)
    }

    fun release() {
        AiKeyboardAiBackendRouter.bridge().releaseGlobalSession()
    }

    fun getSession(): AiKeyboardChatSession? {
        return AiKeyboardAiBackendRouter.bridge().getGlobalSession()?.let { AiKeyboardChatSession(it) }
    }

    fun getCurrentModelId(): String? {
        return AiKeyboardAiBackendRouter.bridge().getCurrentModelId()
    }

    suspend fun loadModel(context: Context, modelId: String, displayName: String, configPath: String) {
        AiKeyboardAiBackendRouter.bridge().loadModel(context, modelId, displayName, configPath)
    }

    fun loadCloudModels(context: Context): List<AiKeyboardCloudModel> {
        val cloudModels = AiKeyboardAiBackendRouter.bridge().loadCloudModels(context)
        AiKeyboardAiBackendRouter.bridge().replaceCloudModels(cloudModels)
        return cloudModels.map {
            AiKeyboardCloudModel(
                modelId = it.modelId,
                displayName = it.displayName,
                isDecision = it.isDecision
            )
        }
    }

    suspend fun requestCloudReplyStream(
        model: AiKeyboardCloudModel,
        history: List<AiKeyboardCloudMessage>,
        onProgress: (String) -> Unit
    ) {
        val bridgeModel = AiKeyboardAiBackendRouter.bridge().findCloudModel(model.modelId)
            ?: throw IllegalStateException("Cloud model not found: ${model.modelId}")
        AiKeyboardAiBackendRouter.bridge().requestCloudReplyStream(bridgeModel, history, onProgress)
    }

    fun getBackend(): AiKeyboardAiBackend {
        return AiKeyboardAiBackendRouter.currentBackend()
    }
}
