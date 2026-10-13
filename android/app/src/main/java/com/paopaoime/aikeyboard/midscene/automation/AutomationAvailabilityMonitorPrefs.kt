package com.paopaoime.aikeyboard.midscene.automation

import android.content.Context

object AutomationAvailabilityMonitorPrefs {

    private const val PREFS_NAME = "automation_availability_monitor"
    private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
    private const val KEY_LAST_READY = "last_ready"
    private const val KEY_DROP_NOTIFIED = "drop_notified"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isMonitoringEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MONITORING_ENABLED, false)

    fun setMonitoringEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONITORING_ENABLED, enabled).commit()
    }

    fun wasLastReady(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LAST_READY, false)

    fun setLastReady(context: Context, ready: Boolean) {
        prefs(context).edit().putBoolean(KEY_LAST_READY, ready).commit()
    }

    fun wasDropNotified(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DROP_NOTIFIED, false)

    fun setDropNotified(context: Context, notified: Boolean) {
        prefs(context).edit().putBoolean(KEY_DROP_NOTIFIED, notified).commit()
    }

    fun markMonitoringEnabled(context: Context, ready: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_MONITORING_ENABLED, true)
            .putBoolean(KEY_LAST_READY, ready)
            .putBoolean(KEY_DROP_NOTIFIED, false)
            .commit()
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_MONITORING_ENABLED, false)
            .putBoolean(KEY_LAST_READY, false)
            .putBoolean(KEY_DROP_NOTIFIED, false)
            .commit()
    }
}
