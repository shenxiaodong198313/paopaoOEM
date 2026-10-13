package com.paopaoime.aikeyboard.automation.bridge

import com.paopaoime.aikeyboard.automation.stellar.AutomationPrivilegedBackend
import com.paopaoime.aikeyboard.automation.stellar.StellarAutomationManager
import com.paopaoime.aikeyboard.ime.application.Launcher
import com.paopaoime.aikeyboard.midscene.bridge.MidsceneBridgeFacade
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommand
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommandResult
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeScreenState

data class AutomationBridgeRuntimeStatus(
    val backend: AutomationPrivilegedBackend = AutomationPrivilegedBackend.NONE,
    val executorKind: String = "plan-only",
    val deviceInfo: String = "",
    val serviceRunning: Boolean = false,
    val authorized: Boolean = false,
    val screenshotAvailable: Boolean = false,
    val ready: Boolean = false,
)

object AutomationBridgeFacade {
    fun currentRuntimeStatus(): AutomationBridgeRuntimeStatus {
        val context = runCatching { Launcher.instance.context }.getOrNull()
            ?: return AutomationBridgeRuntimeStatus()
        val privilegedStatus = StellarAutomationManager.getStatus(context)
        val legacyStatus = MidsceneBridgeFacade.currentRuntimeStatus()
        return AutomationBridgeRuntimeStatus(
            backend = privilegedStatus.backend,
            executorKind = when (privilegedStatus.backend) {
                AutomationPrivilegedBackend.STELLAR -> "stellar"
                AutomationPrivilegedBackend.NONE -> legacyStatus.executorKind
            },
            deviceInfo = when (privilegedStatus.backend) {
                AutomationPrivilegedBackend.STELLAR -> "android-stellar-bridge+screencap"
                AutomationPrivilegedBackend.NONE -> legacyStatus.deviceInfo
            },
            serviceRunning = privilegedStatus.running,
            authorized = privilegedStatus.authorized,
            screenshotAvailable = legacyStatus.screenshotAvailable || privilegedStatus.ready,
            ready = legacyStatus.ready || privilegedStatus.ready,
        )
    }

    fun latestCaptureFailureReason(): String {
        return MidsceneBridgeFacade.latestCaptureFailureReason()
    }

    suspend fun captureScreenState(): MidsceneBridgeScreenState? {
        return MidsceneBridgeFacade.captureScreenState()
    }

    suspend fun captureScreenStateJson(): String {
        return MidsceneBridgeFacade.captureScreenStateJson()
    }

    suspend fun executeCommand(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        return MidsceneBridgeFacade.executeCommand(command)
    }

    suspend fun executeCommandJson(commandJson: String): String {
        return MidsceneBridgeFacade.executeCommandJson(commandJson)
    }
}
