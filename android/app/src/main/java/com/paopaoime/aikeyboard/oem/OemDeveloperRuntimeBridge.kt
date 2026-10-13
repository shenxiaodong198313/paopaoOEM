package com.paopaoime.aikeyboard.oem

import android.content.Context

object OemDeveloperRuntimeBridge {
    private const val MATERIAL_RUNTIME_CLASS =
        "com.paopaoime.aikeyboard.oem.debug.OemDeveloperMaterialCallbackRuntime"

    fun ensureMaterialCallbacksInstalled(context: Context?) {
        val appContext = context?.applicationContext ?: return
        runCatching {
            val runtimeClass = Class.forName(MATERIAL_RUNTIME_CLASS)
            val method = runtimeClass.getMethod("ensureInstalled", Context::class.java)
            method.invoke(null, appContext)
        }
    }
}
