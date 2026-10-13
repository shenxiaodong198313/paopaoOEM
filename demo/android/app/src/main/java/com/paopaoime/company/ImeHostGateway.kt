package com.paopaoime.company

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class ImeHostConfig(
    val packageName: String,
    val setupActivityName: String,
) {
    val componentName: ComponentName
        get() = ComponentName(packageName, setupActivityName)
}

object ImeHostGateway {
    private const val META_TARGET_IME_SETUP_ACTIVITY = "com.yingxiaoime.enterprise.targetImeSetupActivity"

    fun loadConfig(context: Context): ImeHostConfig {
        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        }
        val metaData = appInfo.metaData
        return ImeHostConfig(
            packageName = context.packageName,
            setupActivityName = metaData?.getString(META_TARGET_IME_SETUP_ACTIVITY).orEmpty(),
        )
    }

    fun createSetupIntent(context: Context, config: ImeHostConfig = loadConfig(context)): Intent? {
        if (config.packageName.isBlank() || config.setupActivityName.isBlank()) return null
        return Intent().apply {
            component = config.componentName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }
}
