package com.paopaoime.aikeyboard.midscene.agent

import android.content.Context

object MidsceneUiPreferences {
    private const val prefsName = "midscene_ui_prefs"
    private const val keyOverlayNeonEnabled = "overlay_neon_enabled"
    private const val keyReportCollectionEnabled = "report_collection_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun isOverlayNeonEnabled(context: Context): Boolean =
        prefs(context).getBoolean(keyOverlayNeonEnabled, false)

    fun setOverlayNeonEnabled(context: Context, enabled: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(keyOverlayNeonEnabled, enabled)
            .apply()
    }

    fun isReportCollectionEnabled(context: Context): Boolean =
        prefs(context).getBoolean(keyReportCollectionEnabled, false)

    fun setReportCollectionEnabled(context: Context, enabled: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(keyReportCollectionEnabled, enabled)
            .apply()
    }
}
