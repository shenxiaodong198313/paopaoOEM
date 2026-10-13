package com.paopaoime.aikeyboard.midscene.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.paopaoime.aikeyboard.automation.stellar.StellarAutomationManager

class AutomationAvailabilityWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!AutomationAvailabilityMonitorPrefs.isMonitoringEnabled(context)) {
            return Result.success()
        }

        val status = StellarAutomationManager.getStatus(context)
        val currentReady = status.ready
        val previousReady = AutomationAvailabilityMonitorPrefs.wasLastReady(context)
        val alreadyNotified = AutomationAvailabilityMonitorPrefs.wasDropNotified(context)

        if (previousReady && !currentReady && !alreadyNotified) {
            AutomationAvailabilityNotifier.showUnavailable(context, status.detailMessage)
            AutomationAvailabilityMonitorPrefs.setDropNotified(context, true)
        } else if (currentReady) {
            AutomationAvailabilityNotifier.cancel(context)
            AutomationAvailabilityMonitorPrefs.setDropNotified(context, false)
        }

        AutomationAvailabilityMonitorPrefs.setLastReady(context, currentReady)
        AutomationAvailabilityMonitor.onHeartbeatFinished(context)
        return Result.success()
    }
}
