package com.paopaoime.aikeyboard.midscene.automation

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AutomationAvailabilityMonitor {

    private const val WORK_TAG = "automation_availability_monitor"
    private const val HEARTBEAT_SECONDS = 60L

    fun bootstrap(context: Context) {
        val appContext = context.applicationContext
        if (!AutomationAvailabilityMonitorPrefs.isMonitoringEnabled(appContext)) {
            return
        }
        scheduleNow(appContext)
    }

    fun onAutomationReadyConfirmed(context: Context) {
        val appContext = context.applicationContext
        AutomationAvailabilityMonitorPrefs.markMonitoringEnabled(appContext, ready = true)
        AutomationAvailabilityNotifier.cancel(appContext)
        scheduleNext(appContext)
    }

    fun onAutomationManuallyStopped(context: Context) {
        val appContext = context.applicationContext
        AutomationAvailabilityMonitorPrefs.clear(appContext)
        AutomationAvailabilityNotifier.cancel(appContext)
        WorkManager.getInstance(appContext).cancelAllWorkByTag(WORK_TAG)
    }

    internal fun onHeartbeatFinished(context: Context) {
        val appContext = context.applicationContext
        if (AutomationAvailabilityMonitorPrefs.isMonitoringEnabled(appContext)) {
            scheduleNext(appContext)
        }
    }

    private fun scheduleNow(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(WORK_TAG)
        val request = OneTimeWorkRequestBuilder<AutomationAvailabilityWorker>()
            .addTag(WORK_TAG)
            .build()
        workManager.enqueue(request)
    }

    private fun scheduleNext(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutomationAvailabilityWorker>()
            .addTag(WORK_TAG)
            .setInitialDelay(HEARTBEAT_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
