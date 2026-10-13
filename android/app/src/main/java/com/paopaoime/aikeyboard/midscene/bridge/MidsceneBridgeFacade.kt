package com.paopaoime.aikeyboard.midscene.bridge

import android.content.Context
import com.paopaoime.aikeyboard.automation.stellar.StellarAutomationPrivilegedManager
import com.paopaoime.aikeyboard.automation.stellar.StellarAutomationRuntime
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.application.Launcher
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommand
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommandResult
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeRuntimeStatus
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeScreenState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object MidsceneBridgeFacade {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    @Volatile
    private var lastCaptureFailureReason: String = ""

    fun currentRuntimeStatus(): MidsceneBridgeRuntimeStatus {
        val context = runCatching { Launcher.instance.context }.getOrNull()
            ?: return MidsceneBridgeRuntimeStatus(
                executorKind = "plan-only",
                deviceInfo = "android-bridge-unavailable"
            )
        return resolveRuntimeStatus(context)
    }

    fun latestCaptureFailureReason(): String {
        return lastCaptureFailureReason.ifBlank {
            "当前屏幕采集能力不可用，无法获取真实 screen state。"
        }
    }

    suspend fun captureScreenState(): MidsceneBridgeScreenState? {
        val context = runCatching { Launcher.instance.context }.getOrNull() ?: return null
        val runtimeStatus = resolveRuntimeStatus(context)
        val captured = when (runtimeStatus.executorKind) {
            "stellar" -> StellarAutomationRuntime(context).captureScreenState()
            else -> null
        }
        if (captured != null) {
            lastCaptureFailureReason = ""
            return captured
        }
        lastCaptureFailureReason = when (runtimeStatus.executorKind) {
            "stellar" -> {
                val detail = StellarAutomationPrivilegedManager.getLastScreenshotError().ifBlank { "未知 screencap 错误" }
                "输入法自动化引擎 screencap 失败：$detail"
            }
            else -> resolveUnavailableCaptureMessage(context, runtimeStatus)
        }
        return null
    }

    suspend fun captureScreenStateJson(): String {
        val screenState = captureScreenState()
        return json.encodeToString(screenState)
    }

    suspend fun executeCommand(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        val context = runCatching { Launcher.instance.context }.getOrNull()
            ?: return MidsceneBridgeCommandResult(
                success = false,
                errorCode = "BRIDGE_CONTEXT_UNAVAILABLE",
                message = "Launcher context unavailable",
                retryable = true
            )
        val runtimeStatus = resolveRuntimeStatus(context)
        return when (runtimeStatus.executorKind) {
            "stellar" -> StellarAutomationRuntime(context).executeCommand(command)
            else -> MidsceneBridgeCommandResult(
                success = false,
                errorCode = "BRIDGE_RUNTIME_UNAVAILABLE",
                message = resolveUnavailableRuntimeMessage(context, runtimeStatus),
                retryable = true
            )
        }
    }

    suspend fun executeCommandJson(commandJson: String): String {
        val command = json.decodeFromString<MidsceneBridgeCommand>(commandJson)
        return json.encodeToString(executeCommand(command))
    }

    private fun resolveRuntimeStatus(context: Context): MidsceneBridgeRuntimeStatus {
        return StellarAutomationRuntime(context).status()
    }

    private fun resolveUnavailableRuntimeMessage(
        context: Context,
        status: MidsceneBridgeRuntimeStatus
    ): String {
        val imeDisplayName = context.getString(R.string.oem_ime_display_name)
        return when {
            status.automationServiceConnected && !status.automationAuthorized ->
                context.getString(R.string.automation_executor_unauthorized_message, imeDisplayName)
            else ->
                "当前未检测到可用的输入法自动化引擎执行能力"
        }
    }

    private fun resolveUnavailableCaptureMessage(
        context: Context,
        status: MidsceneBridgeRuntimeStatus
    ): String {
        val automationStatus = runCatching { StellarAutomationPrivilegedManager.getStatus(context) }.getOrNull()
        val detail = automationStatus?.detailMessage?.ifBlank { null }
        val imeDisplayName = context.getString(R.string.oem_ime_display_name)
        return when {
            detail != null -> "当前仅保留自动化截图链路，$detail"
            status.automationServiceConnected && !status.automationAuthorized ->
                context.getString(R.string.automation_capture_unauthorized_message, imeDisplayName)
            else ->
                "当前仅保留自动化截图链路，未检测到可用的输入法自动化引擎本地执行器"
        }
    }
}
