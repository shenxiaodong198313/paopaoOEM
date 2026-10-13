/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import org.fcitx.fcitx5.android.input.FcitxInputMethodService

object InputMethodUtil {

    private const val HOST_IME_SERVICE = "com.paopaoime.aikeyboard.ime.service.AiInputMethodService"
    private val runtimePackageName: String get() = appContext.packageName

    private fun resolveDeclaredImeComponent(): ComponentName? {
        val pm = appContext.packageManager
        val intent = Intent("android.view.InputMethod").setPackage(runtimePackageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ResolveInfoFlags.of(0L)
        } else null
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
        return appContext.inputMethodManager.enabledInputMethodList.any {
            it.packageName == runtimePackageName && it.serviceName == serviceName
        }
    }

    fun isSelected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            appContext.inputMethodManager.currentInputMethodInfo?.let {
                it.packageName == runtimePackageName && it.serviceName == serviceName
            } ?: false
        } else {
            getSecureSettings<String>(Settings.Secure.DEFAULT_INPUT_METHOD) == componentName
        }
    }

    fun startSettingsActivity(context: Context) =
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

    fun showPicker() = appContext.inputMethodManager.showInputMethodPicker()

    fun listVoiceInputMethods(): List<Pair<InputMethodInfo, InputMethodSubtype>> {
        return appContext.inputMethodManager.enabledInputMethodList
            .mapNotNull { info ->
                info.firstVoiceSubtype()?.let { info to it }
            }
    }

    /**
     * Find input method with `"voice"` subtype, preferring one with [id]
     */
    fun findVoiceSubtype(id: String): Pair<String, InputMethodSubtype>? {
        val inputMethods = appContext.inputMethodManager.enabledInputMethodList
        if (inputMethods.isEmpty()) return null
        var firstId: String? = null
        var firstSubtype: InputMethodSubtype? = null
        inputMethods.forEach {
            val voiceSubtype = it.firstVoiceSubtype() ?: return@forEach
            if (it.id == id) {
                return id to voiceSubtype
            }
            if (firstId == null) {
                firstId = it.id
                firstSubtype = voiceSubtype
            }
        }
        if (firstId != null && firstSubtype != null) {
            return firstId to firstSubtype
        }
        return null
    }

    fun switchInputMethod(
        service: FcitxInputMethodService,
        id: String,
        subtype: InputMethodSubtype
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.switchInputMethod(id, subtype)
        } else {
            @Suppress("DEPRECATION")
            appContext.inputMethodManager
                .setInputMethodAndSubtype(service.window.window!!.attributes.token, id, subtype)
        }
    }
}
