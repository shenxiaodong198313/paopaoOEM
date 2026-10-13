

package com.paopaoime.aikeyboard.ime.utils

import android.provider.Settings
import com.paopaoime.aikeyboard.ime.application.Launcher

fun getSecureSettings(name: String): String? {
    return Settings.Secure.getString(Launcher.instance.context.contentResolver, name)
}

