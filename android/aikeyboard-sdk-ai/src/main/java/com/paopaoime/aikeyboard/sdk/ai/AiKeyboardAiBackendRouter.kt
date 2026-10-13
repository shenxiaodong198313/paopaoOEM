package com.paopaoime.aikeyboard.sdk.ai

enum class AiKeyboardAiBackend {
    LEGACY_AICORE
}

internal object AiKeyboardAiBackendRouter {
    fun currentBackend(): AiKeyboardAiBackend = AiKeyboardAiBackend.LEGACY_AICORE
    fun bridge(): AiKeyboardAiCoreBridge = AiKeyboardAiCoreBridge
}
