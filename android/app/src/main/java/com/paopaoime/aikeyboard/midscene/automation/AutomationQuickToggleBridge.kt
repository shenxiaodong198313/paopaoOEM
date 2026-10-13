package com.paopaoime.aikeyboard.midscene.automation

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.paopaoime.aikeyboard.automation.stellar.StellarAutomationManager
import com.paopaoime.aikeyboard.ime.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import roro.stellar.Stellar
import roro.stellar.manager.StellarApplication
import roro.stellar.manager.adb.AdbMdns
import roro.stellar.manager.adb.AdbPortResolver
import roro.stellar.manager.startup.notification.BootStartNotifications
import roro.stellar.manager.startup.worker.AdbStartWorker
import roro.stellar.manager.startup.worker.AdbStarter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

object AutomationQuickToggleBridge {
    private const val FORCE_STOP_SETTLE_MS = 800L
    private const val SILENT_DIRECT_START_BINDER_TIMEOUT_MS = 20_000L

    enum class StartBlocker {
        NONE,
        STARTING,
        NOTIFICATION_PERMISSION_REQUIRED,
        WIRELESS_DEBUGGING_REQUIRED,
        ADB_PAIRING_REQUIRED,
        AUTHORIZATION_REQUIRED,
    }

    data class RuntimeDiagnosis(
        val ready: Boolean,
        val running: Boolean,
        val authorized: Boolean,
        val startInProgress: Boolean,
        val hasNotificationPermission: Boolean,
        val hasReachableAdbPort: Boolean,
        val hasSavedAdbPortCandidate: Boolean,
        val blocker: StartBlocker,
        val summary: String,
    )

    sealed class StartResult {
        object Started : StartResult()
        object AlreadyRunning : StartResult()
        object Unsupported : StartResult()
        data class NeedsUserAction(
            val reason: String,
            val hint: String,
        ) : StartResult()
        data class Failed(
            val reason: String,
            val hint: String? = null,
        ) : StartResult()
    }

    sealed class StopResult {
        object Stopped : StopResult()
        object AlreadyStopped : StopResult()
        data class Failed(
            val reason: String,
            val hint: String? = null,
        ) : StopResult()
    }

    private enum class WirelessDebuggingAvailability {
        AVAILABLE,
        NEEDS_MANUAL_ENABLE,
    }

    fun isServiceRunning(context: Context): Boolean {
        return StellarAutomationManager.getStatus(context.applicationContext).running
    }

    fun isAutomationReady(context: Context): Boolean {
        return StellarAutomationManager.getStatus(context.applicationContext).ready
    }

    suspend fun diagnoseRuntime(context: Context): RuntimeDiagnosis {
        val appContext = context.applicationContext
        val status = StellarAutomationManager.getStatus(appContext)
        val startInProgress = isStartInProgress(appContext)
        val hasNotificationPermission = hasNotificationPermission(appContext)
        val hasReachableAdbPort =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                AdbPortResolver.resolveReachablePort() in 1..65535
        val hasSavedAdbPortCandidate =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                AdbPortResolver.hasSavedPortCandidate()

        val blocker = when {
            status.ready -> StartBlocker.NONE
            startInProgress || hasReachableAdbPort -> StartBlocker.STARTING
            !hasNotificationPermission -> StartBlocker.NOTIFICATION_PERMISSION_REQUIRED
            status.running && !status.authorized -> StartBlocker.AUTHORIZATION_REQUIRED
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> StartBlocker.WIRELESS_DEBUGGING_REQUIRED
            hasSavedAdbPortCandidate -> StartBlocker.ADB_PAIRING_REQUIRED
            else -> StartBlocker.WIRELESS_DEBUGGING_REQUIRED
        }

        val summary = when (blocker) {
            StartBlocker.NONE -> status.detailMessage
            StartBlocker.STARTING -> {
                if (status.running) {
                    status.detailMessage
                } else {
                    appContext.getString(R.string.automation_toggle_starting)
                }
            }
            StartBlocker.NOTIFICATION_PERMISSION_REQUIRED ->
                appContext.getString(R.string.automation_toggle_notification_permission_missing_summary)
            StartBlocker.WIRELESS_DEBUGGING_REQUIRED ->
                appContext.getString(R.string.automation_toggle_no_port_detected)
            StartBlocker.ADB_PAIRING_REQUIRED ->
                appContext.getString(R.string.automation_toggle_pairing_required_summary)
            StartBlocker.AUTHORIZATION_REQUIRED -> status.detailMessage
        }

        return RuntimeDiagnosis(
            ready = status.ready,
            running = status.running,
            authorized = status.authorized,
            startInProgress = startInProgress,
            hasNotificationPermission = hasNotificationPermission,
            hasReachableAdbPort = hasReachableAdbPort,
            hasSavedAdbPortCandidate = hasSavedAdbPortCandidate,
            blocker = blocker,
            summary = summary,
        )
    }

    suspend fun requestStartAndAwait(context: Context): StartResult {
        val appContext = context.applicationContext
        StellarApplication.initialize(appContext)
        BootStartNotifications.createChannel(appContext)
        if (isAutomationReady(appContext)) {
            return StartResult.AlreadyRunning
        }
        if (isServiceRunning(appContext) && StellarAutomationManager.getStatus(appContext).authorized) {
            return StartResult.Started
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return StartResult.Unsupported
        }

        val availability = detectWirelessDebuggingAvailability(appContext)
        if (availability == WirelessDebuggingAvailability.NEEDS_MANUAL_ENABLE) {
            AdbStartWorker.cancel(appContext)
            return StartResult.NeedsUserAction(
                reason = appContext.getString(R.string.automation_toggle_no_port_detected),
                hint = appContext.getString(R.string.automation_toggle_enable_wireless_hint),
            )
        }

        AdbStartWorker.enqueue(appContext)
        return if (waitForReadyState(appContext, expectedReady = true, timeoutMs = 20_000L)) {
            StartResult.Started
        } else {
            val latestStatus = StellarAutomationManager.getStatus(appContext)
            if (latestStatus.running && latestStatus.authorized) {
                return StartResult.Started
            }
            AdbStartWorker.cancel(appContext)
            if (attemptSilentDirectStart(appContext)) {
                return StartResult.Started
            }
            val finalAvailability = detectWirelessDebuggingAvailability(appContext)
            if (finalAvailability == WirelessDebuggingAvailability.NEEDS_MANUAL_ENABLE) {
                StartResult.NeedsUserAction(
                    reason = appContext.getString(R.string.automation_toggle_no_port_detected),
                    hint = appContext.getString(R.string.automation_toggle_enable_wireless_hint),
                )
            } else {
                StartResult.Failed(
                    reason = appContext.getString(R.string.automation_toggle_start_failed_reason),
                    hint = appContext.getString(R.string.automation_toggle_start_failed_hint),
                )
            }
        }
    }

    private suspend fun attemptSilentDirectStart(context: Context): Boolean {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }
        val port = AdbPortResolver.resolveReachablePort().takeIf { it in 1..65535 }
            ?: detectAdbPortViaMdns(appContext)
        if (port !in 1..65535) {
            return false
        }
        if (!AdbStarter.startAdb("127.0.0.1", port, maxRetries = 3)) {
            return false
        }
        if (AdbStarter.waitForBinder(timeoutMs = SILENT_DIRECT_START_BINDER_TIMEOUT_MS)) {
            return true
        }
        return StellarAutomationManager.getStatus(appContext).ready
    }

    suspend fun requestStopAndAwait(context: Context): StopResult {
        val appContext = context.applicationContext
        val running = isServiceRunning(appContext)
        val starting = isStartInProgress(appContext)
        if (!running && !starting) {
            return StopResult.AlreadyStopped
        }

        AdbStartWorker.cancel(appContext)
        if (Stellar.pingBinder()) {
            runCatching { Stellar.exit() }
        }

        if (waitForReadyState(appContext, expectedReady = false, timeoutMs = 4_000L)) {
            StopResult.Stopped
        }
        forceStopStellarServer(appContext)
        delay(FORCE_STOP_SETTLE_MS)

        return if (waitForReadyState(appContext, expectedReady = false, timeoutMs = 6_000L)) {
            StopResult.Stopped
        } else {
            StopResult.Failed(
                reason = "${appContext.getString(R.string.automation_toggle_stop_failed_reason)}：旧的 stellar_server 进程未退出",
                hint = appContext.getString(R.string.automation_toggle_stop_failed_hint),
            )
        }
    }

    fun isStartInProgress(context: Context): Boolean {
        return AdbStartWorker.isStartInProgress(context.applicationContext)
    }

    private suspend fun detectWirelessDebuggingAvailability(context: Context): WirelessDebuggingAvailability {
        val appContext = context.applicationContext
        val knownReachablePort = AdbPortResolver.resolveReachablePort()
        if (knownReachablePort in 1..65535) {
            return WirelessDebuggingAvailability.AVAILABLE
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return WirelessDebuggingAvailability.NEEDS_MANUAL_ENABLE
        }
        val discoveredPort = detectAdbPortViaMdns(appContext)
        return if (discoveredPort in 1..65535) {
            WirelessDebuggingAvailability.AVAILABLE
        } else {
            WirelessDebuggingAvailability.NEEDS_MANUAL_ENABLE
        }
    }

    private suspend fun detectAdbPortViaMdns(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return -1
        }
        return suspendCancellableCoroutine { continuation ->
            val settled = AtomicBoolean(false)
            var adbMdns: AdbMdns? = null

            fun finish(port: Int) {
                if (!settled.compareAndSet(false, true)) {
                    return
                }
                adbMdns?.destroy()
                continuation.resume(port)
            }

            adbMdns = AdbMdns(
                context = context.applicationContext,
                serviceType = AdbMdns.TLS_CONNECT,
                observer = Observer<Pair<String, Int>> { service ->
                    val discoveredPort = service.second
                    if (discoveredPort in 1..65535) {
                        finish(discoveredPort)
                    }
                },
                onMaxRefresh = {
                    finish(-1)
                },
                maxRefreshCount = 3,
            )

            continuation.invokeOnCancellation {
                adbMdns.destroy()
            }

            adbMdns.start()
        }
    }

    private suspend fun waitForReadyState(
        context: Context,
        expectedReady: Boolean,
        timeoutMs: Long,
    ): Boolean {
        val appContext = context.applicationContext
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                val status = StellarAutomationManager.getStatus(appContext)
                val currentReady = status.ready
                val currentRunning = status.running
                val startInProgress = isStartInProgress(appContext)

                val reached = if (expectedReady) {
                    currentReady
                } else {
                    !currentRunning && !startInProgress && !Stellar.pingBinder()
                }
                if (reached) {
                    break
                }
                delay(300L)
            }
            true
        } ?: false
    }

    private fun forceStopStellarServer(context: Context) {
        if (!Stellar.pingBinder()) {
            return
        }
        val processName = "${context.packageName}:stellar_server"
        StellarAutomationManager.dispatchShellCommand(
            "sh -c 'pid=\$(pidof -s \"$processName\"); if [ -z \"\$pid\" ]; then pid=\$(pidof -s stellar_server); fi; if [ -n \"\$pid\" ]; then kill -9 \$pid; fi'",
        )
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
        }
    }
}
