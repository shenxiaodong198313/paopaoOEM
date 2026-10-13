package com.paopaoime.aikeyboard.ime.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.paopaoime.aikeyboard.ime.application.Launcher

object InputMethodUtil {
    private const val HOST_IME_SERVICE = "com.paopaoime.aikeyboard.ime.service.AiInputMethodService"

    private val runtimePackageName: String
        get() = Launcher.instance.context.packageName

    private fun resolveDeclaredImeComponent(): ComponentName? {
        val context = Launcher.instance.context
        val pm = context.packageManager
        val intent = Intent("android.view.InputMethod").setPackage(runtimePackageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ResolveInfoFlags.of(0L)
        } else {
            null
        }
        val services = if (flags != null) {
            @Suppress("NewApi")
            pm.queryIntentServices(intent, flags)
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, 0)
        }
        val target = services.firstOrNull { it.serviceInfo?.name == HOST_IME_SERVICE }
            ?: services.firstOrNull()
        val si = target?.serviceInfo ?: return null
        return ComponentName(si.packageName, si.name)
    }

    @JvmField
    val serviceName: String = resolveDeclaredImeComponent()?.className ?: HOST_IME_SERVICE

    @JvmField
    val componentName: String = resolveDeclaredImeComponent()?.flattenToShortString()
        ?: ComponentName(runtimePackageName, HOST_IME_SERVICE).flattenToShortString()

    fun isEnabled(): Boolean {
        return Launcher.instance.context.inputMethodManager.enabledInputMethodList.any {
            it.packageName == runtimePackageName && it.serviceName == serviceName
        }
    }

    fun isSelected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Launcher.instance.context.inputMethodManager.currentInputMethodInfo?.let {
                it.packageName == runtimePackageName && it.serviceName == serviceName
            } ?: false
        } else {
            getSecureSettings(Settings.Secure.DEFAULT_INPUT_METHOD) == componentName
        }
    }

    fun startSettingsActivity(context: Context) =
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

    fun showPicker() = Launcher.instance.context.inputMethodManager.showInputMethodPicker()
}
