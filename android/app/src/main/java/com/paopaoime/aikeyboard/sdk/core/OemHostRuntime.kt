package com.paopaoime.aikeyboard.sdk.core

import android.app.Application
import com.paopaoime.aikeyboard.ime.application.Launcher
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient

object OemHostRuntime {
    fun initialize(application: Application, enableNeonOverlay: Boolean = false) {
        OemLog.attachContext(application)
        OemLog.d(
            "OemHostRuntime",
            "initialize developerInstalled=${OemAddons.hasDeveloper} " +
                "enableNeonOverlay=$enableNeonOverlay",
        )
        OemAddons.automation?.initializeRuntime(application, enableNeonOverlay)
        OemAddons.developer?.initializeRuntime(application)
        AuthApiClient.initialize(application)
        Launcher.instance.initData(application)
    }
}
