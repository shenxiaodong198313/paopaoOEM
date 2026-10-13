package com.paopaoime.aikeyboard.midscene.bridge.model

data class MidsceneBridgeRuntimeStatus(
    val executorKind: String = "plan-only",
    val deviceInfo: String = "",
    val accessibilityBridgeConnected: Boolean = false,
    val automationServiceConnected: Boolean = false,
    val automationAuthorized: Boolean = false,
    val screenshotAvailable: Boolean = false,
    val ready: Boolean = false,
) {
    val shizukuPaired: Boolean
        get() = automationServiceConnected

    val shizukuGranted: Boolean
        get() = automationAuthorized
}
