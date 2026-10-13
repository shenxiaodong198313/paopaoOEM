package com.paopaoime.aikeyboard.midscene.bridge.model

import kotlinx.serialization.Serializable

@Serializable
data class MidsceneBridgeScreenState(
    val width: Int,
    val height: Int,
    val devicePixelRatio: Float? = null,
    val screenshotBase64: String,
    val currentApp: String? = null,
    val currentPage: String? = null,
    val windowState: MidsceneBridgeWindowState? = null
)

@Serializable
data class MidsceneBridgeWindowState(
    val packageName: String? = null,
    val className: String? = null,
    val source: String? = null,
    val sampleCount: Int = 0,
    val focusedEditable: Boolean = false,
    val focusedText: String? = null,
    val focusedHint: String? = null,
    val timestampMs: Long
)

@Serializable
data class MidsceneBridgeCommand(
    val type: String,
    val x: Int? = null,
    val y: Int? = null,
    val fromX: Int? = null,
    val fromY: Int? = null,
    val toX: Int? = null,
    val toY: Int? = null,
    val durationMs: Long? = null,
    val direction: String? = null,
    val scrollType: String? = null,
    val distance: Float? = null,
    val text: String? = null,
    val mode: String? = null,
    val command: String? = null,
    val uri: String? = null,
    val keyName: String? = null,
    val keyCode: Int? = null,
    val appName: String? = null,
    val packageName: String? = null
)

@Serializable
data class MidsceneBridgeCommandResult(
    val success: Boolean,
    val errorCode: String? = null,
    val message: String = "",
    val retryable: Boolean = false,
    val verified: Boolean? = null,
    val verification: MidsceneBridgeCommandVerification? = null
)

@Serializable
data class MidsceneBridgeCommandVerification(
    val strategy: String,
    val expectedText: String? = null,
    val matched: Boolean? = null,
    val before: MidsceneBridgeVerificationSnapshot? = null,
    val after: MidsceneBridgeVerificationSnapshot? = null
)

@Serializable
data class MidsceneBridgeVerificationSnapshot(
    val packageName: String? = null,
    val className: String? = null,
    val focusedEditable: Boolean = false,
    val editableText: String? = null,
    val editableHint: String? = null,
    val timestampMs: Long
)
