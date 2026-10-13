package com.paopaoime.aikeyboard.midscene.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class MidsceneInstalledAppInfo(
    val appName: String,
    val packageName: String
)

object MidsceneInstalledAppCatalog {
    private const val PREFS_NAME = "midscene_agent_runtime"
    private const val KEY_READ_INSTALLED_APPS_ENABLED = "read_installed_apps_enabled"

    fun isEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_READ_INSTALLED_APPS_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_READ_INSTALLED_APPS_ENABLED, enabled)
            .apply()
    }

    fun collect(context: Context): List<MidsceneInstalledAppInfo> {
        if (!isEnabled(context)) return emptyList()
        val packageManager = context.packageManager
        val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                queryIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(queryIntent, 0)
        }

        return activities.asSequence()
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName?.trim().orEmpty()
                val appName = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (packageName.isBlank() || appName.isBlank()) {
                    null
                } else {
                    MidsceneInstalledAppInfo(appName = appName, packageName = packageName)
                }
            }
            .distinctBy { it.packageName.lowercase(Locale.ROOT) }
            .sortedWith(
                compareBy<MidsceneInstalledAppInfo> { it.appName.lowercase(Locale.ROOT) }
                    .thenBy { it.packageName.lowercase(Locale.ROOT) }
            )
            .toList()
    }
}
