package com.paopaoime.aikeyboard.sdk.ai

import android.app.Application
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AiKeyboardDownloadedModel(
    val modelId: String,
    val displayName: String,
    val localPath: String,
    val configPath: String,
    val isDiffusion: Boolean,
    val isVisual: Boolean,
    val tags: List<String>,
    val extraTags: List<String>
)

object AiKeyboardHostSdk {
    @Volatile
    private var currentModelId: String? = null

    fun initialize(application: Application) {
        AiKeyboardAiBackendRouter.bridge().initializeLegacy(application)
    }

    fun initializeLogCollector() {
        AiKeyboardAiBackendRouter.bridge().initializeLogCollector()
    }

    fun generateDiffusionOutputPath(context: Context, fileNamePrefix: String): String {
        return AiKeyboardAiBackendRouter.bridge().generateDiffusionOutputPath(context, fileNamePrefix)
    }

    fun getBackend(): AiKeyboardAiBackend {
        return AiKeyboardAiBackendRouter.currentBackend()
    }

    fun createSettingsIntent(context: Context): Intent {
        throw UnsupportedOperationException(
            "当前 AI Runtime SDK 未包含 OEM 输入法设置中心，请在接入 UI/IME 模块后再调用该入口。"
        )
    }

    fun getCurrentModelId(): String? = currentModelId

    fun setCurrentModelId(modelId: String?) {
        currentModelId = modelId
    }

    suspend fun loadDownloadedModels(context: Context): List<AiKeyboardDownloadedModel> = withContext(Dispatchers.IO) {
        emptyList()
    }
}
