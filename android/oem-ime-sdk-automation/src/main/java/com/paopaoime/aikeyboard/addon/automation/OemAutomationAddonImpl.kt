package com.paopaoime.aikeyboard.addon.automation

import android.app.Application
import android.content.Context
import android.content.Intent
import com.paopaoime.aikeyboard.automation.bridge.AutomationBridgeFacade
import com.paopaoime.aikeyboard.automation.stellar.AutomationPrivilegedBackend
import com.paopaoime.aikeyboard.automation.stellar.StellarAutomationManager
import com.paopaoime.aikeyboard.midscene.agent.MidsceneInstalledAppCatalog
import com.paopaoime.aikeyboard.midscene.agent.MidsceneUiPreferences
import com.paopaoime.aikeyboard.midscene.automation.AutomationAvailabilityMonitor
import com.paopaoime.aikeyboard.midscene.automation.AutomationAvailabilityMonitorPrefs
import com.paopaoime.aikeyboard.midscene.automation.AutomationQuickToggleBridge
import com.paopaoime.aikeyboard.midscene.automation.AutomationSettingsLauncher
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommand
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeCommandResult
import com.paopaoime.aikeyboard.midscene.bridge.model.MidsceneBridgeScreenState
import com.paopaoime.aikeyboard.midscene.overlay.MidsceneExecutionOverlayService
import com.paopaoime.aikeyboard.sdk.core.OemAutomationAddon
import com.paopaoime.aikeyboard.sdk.core.OemAutomationOverlayState
import com.paopaoime.aikeyboard.sdk.core.OemAutomationRuntimeDiagnosis
import com.paopaoime.aikeyboard.sdk.core.OemAutomationRuntimeSnapshot
import com.paopaoime.aikeyboard.sdk.core.OemAutomationShellResult
import com.paopaoime.aikeyboard.sdk.core.OemAutomationStartBlocker
import com.paopaoime.aikeyboard.sdk.core.OemAutomationStartResult
import com.paopaoime.aikeyboard.sdk.core.OemAutomationStopResult

class OemAutomationAddonImpl : OemAutomationAddon {
    override fun initializeRuntime(application: Application, enableNeonOverlay: Boolean) {
        MidsceneUiPreferences.setOverlayNeonEnabled(application, enableNeonOverlay)
        StellarAutomationManager.init(application)
    }

    override suspend fun executeBridgeCommand(command: MidsceneBridgeCommand): MidsceneBridgeCommandResult {
        return AutomationBridgeFacade.executeCommand(command)
    }

    override suspend fun captureBridgeScreenState(): MidsceneBridgeScreenState? {
        return AutomationBridgeFacade.captureScreenState()
    }

    override fun isBridgeRuntimeReady(): Boolean {
        return AutomationBridgeFacade.currentRuntimeStatus().ready
    }

    override suspend fun setOverlayCaptureSuppressedAndAwaitNextFrame(
        context: Context,
        suppressed: Boolean,
        timeoutMs: Long
    ) {
        MidsceneExecutionOverlayService.setCaptureSuppressedAndAwaitNextFrame(context, suppressed, timeoutMs)
    }

    override fun bootstrapAvailabilityMonitor(context: Context) {
        AutomationAvailabilityMonitor.bootstrap(context)
    }

    override fun isAvailabilityMonitorEnabled(context: Context): Boolean {
        return AutomationAvailabilityMonitorPrefs.isMonitoringEnabled(context.applicationContext)
    }

    override fun wasAutomationLastReady(context: Context): Boolean {
        return AutomationAvailabilityMonitorPrefs.wasLastReady(context.applicationContext)
    }

    override fun markAutomationReadyConfirmed(context: Context) {
        AutomationAvailabilityMonitor.onAutomationReadyConfirmed(context)
    }

    override fun markAutomationManuallyStopped(context: Context) {
        AutomationAvailabilityMonitor.onAutomationManuallyStopped(context)
    }

    override suspend fun requestAutomationStart(context: Context): OemAutomationStartResult {
        return when (val result = AutomationQuickToggleBridge.requestStartAndAwait(context)) {
            AutomationQuickToggleBridge.StartResult.Started -> OemAutomationStartResult.Started
            AutomationQuickToggleBridge.StartResult.AlreadyRunning -> OemAutomationStartResult.AlreadyRunning
            AutomationQuickToggleBridge.StartResult.Unsupported -> OemAutomationStartResult.Unsupported
            is AutomationQuickToggleBridge.StartResult.NeedsUserAction ->
                OemAutomationStartResult.NeedsUserAction(result.reason, result.hint)
            is AutomationQuickToggleBridge.StartResult.Failed ->
                OemAutomationStartResult.Failed(result.reason, result.hint)
        }
    }

    override suspend fun requestAutomationStop(context: Context): OemAutomationStopResult {
        return when (val result = AutomationQuickToggleBridge.requestStopAndAwait(context)) {
            AutomationQuickToggleBridge.StopResult.Stopped -> OemAutomationStopResult.Stopped
            AutomationQuickToggleBridge.StopResult.AlreadyStopped -> OemAutomationStopResult.AlreadyStopped
            is AutomationQuickToggleBridge.StopResult.Failed ->
                OemAutomationStopResult.Failed(result.reason, result.hint)
        }
    }

    override suspend fun diagnoseAutomationRuntime(context: Context): OemAutomationRuntimeDiagnosis {
        val diagnosis = AutomationQuickToggleBridge.diagnoseRuntime(context)
        return OemAutomationRuntimeDiagnosis(
            ready = diagnosis.ready,
            running = diagnosis.running,
            authorized = diagnosis.authorized,
            startInProgress = diagnosis.startInProgress,
            hasNotificationPermission = diagnosis.hasNotificationPermission,
            hasReachableAdbPort = diagnosis.hasReachableAdbPort,
            hasSavedAdbPortCandidate = diagnosis.hasSavedAdbPortCandidate,
            blocker = mapStartBlocker(diagnosis.blocker),
            summary = diagnosis.summary,
        )
    }

    override fun launchAutomationSettings(context: Context) {
        AutomationSettingsLauncher.launch(context)
    }

    override fun isInstalledAppCatalogEnabled(context: Context): Boolean {
        return MidsceneInstalledAppCatalog.isEnabled(context)
    }

    override fun setInstalledAppCatalogEnabled(context: Context, enabled: Boolean) {
        MidsceneInstalledAppCatalog.setEnabled(context, enabled)
    }

    override fun getAutomationRuntimeSnapshot(context: Context): OemAutomationRuntimeSnapshot {
        val status = AutomationBridgeFacade.currentRuntimeStatus()
        return OemAutomationRuntimeSnapshot(
            backend = when (status.backend) {
                AutomationPrivilegedBackend.STELLAR -> "STELLAR"
                AutomationPrivilegedBackend.NONE -> "NONE"
            },
            running = status.serviceRunning,
            authorized = status.authorized,
            ready = status.ready,
            detailMessage = status.deviceInfo.ifBlank { status.executorKind },
        )
    }

    override fun captureAutomationScreenshotBase64(context: Context): String? {
        return StellarAutomationManager.captureScreenshotBase64(context)
    }

    override fun getLastAutomationScreenshotError(): String {
        return StellarAutomationManager.getLastScreenshotError().orEmpty()
    }

    override fun setAutomationOverlayNeonEnabled(context: Context, enabled: Boolean) {
        MidsceneUiPreferences.setOverlayNeonEnabled(context.applicationContext, enabled)
    }

    override fun showAutomationOverlay(context: Context, state: OemAutomationOverlayState, borderOnly: Boolean) {
        MidsceneExecutionOverlayService.show(context.applicationContext, state.toOverlayUiState(), borderOnly = borderOnly)
    }

    override suspend fun showAutomationOverlayAndAwaitFirstFrame(
        context: Context,
        state: OemAutomationOverlayState,
        borderOnly: Boolean
    ) {
        MidsceneExecutionOverlayService.showAndAwaitNextFrame(
            context.applicationContext,
            state.toOverlayUiState(),
            borderOnly = borderOnly
        )
    }

    override fun hideAutomationOverlay(context: Context) {
        MidsceneExecutionOverlayService.hide(context.applicationContext)
    }

    override fun executeAutomationShellCommand(command: String): OemAutomationShellResult {
        val (success, output) = StellarAutomationManager.executeShellCommand(command)
        return OemAutomationShellResult(success, output)
    }

    private fun OemAutomationOverlayState.toOverlayUiState(): MidsceneExecutionOverlayService.OverlayUiState {
        return MidsceneExecutionOverlayService.OverlayUiState(
            title = title,
            subtitle = subtitle,
            progressText = progressText,
            progressFraction = progressFraction,
            tokenText = tokenText,
            fullscreenMask = fullscreenMask,
        )
    }

    override fun createStellarManagerIntent(context: Context): Intent {
        return AutomationSettingsLauncher.createIntent(context)
    }

    override fun createAutomationStarterGuideIntent(context: Context, hasSecureSettings: Boolean): Intent {
        return AutomationSettingsLauncher.createIntent(context)
    }

    private fun mapStartBlocker(blocker: AutomationQuickToggleBridge.StartBlocker): OemAutomationStartBlocker {
        return when (blocker) {
            AutomationQuickToggleBridge.StartBlocker.NONE -> OemAutomationStartBlocker.NONE
            AutomationQuickToggleBridge.StartBlocker.STARTING -> OemAutomationStartBlocker.STARTING
            AutomationQuickToggleBridge.StartBlocker.NOTIFICATION_PERMISSION_REQUIRED ->
                OemAutomationStartBlocker.NOTIFICATION_PERMISSION_REQUIRED
            AutomationQuickToggleBridge.StartBlocker.WIRELESS_DEBUGGING_REQUIRED ->
                OemAutomationStartBlocker.WIRELESS_DEBUGGING_REQUIRED
            AutomationQuickToggleBridge.StartBlocker.ADB_PAIRING_REQUIRED ->
                OemAutomationStartBlocker.ADB_PAIRING_REQUIRED
            AutomationQuickToggleBridge.StartBlocker.AUTHORIZATION_REQUIRED ->
                OemAutomationStartBlocker.AUTHORIZATION_REQUIRED
        }
    }
}
