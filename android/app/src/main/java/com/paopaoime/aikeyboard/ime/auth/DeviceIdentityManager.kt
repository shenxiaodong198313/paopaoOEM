package com.paopaoime.aikeyboard.ime.auth

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

object DeviceIdentityManager {
    private const val UNKNOWN_DEVICE = "unknown-device"

    fun resolveDeviceId(context: Context): String {
        if (!hasDeviceInfoPermission(context)) return UNKNOWN_DEVICE
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            .orEmpty()
        if (androidId.isBlank()) return UNKNOWN_DEVICE
        val model = Build.MODEL.orEmpty().trim().replace("\\s+".toRegex(), "-").lowercase()
        return "android-${androidId.takeLast(16)}-${model.take(24)}"
    }

    fun resolveDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return "$manufacturer $model".trim().ifBlank { Build.DEVICE.orEmpty().ifBlank { UNKNOWN_DEVICE } }
    }

    fun resolveDeviceBrand(): String {
        return Build.MANUFACTURER.orEmpty().trim().ifBlank { "unknown-brand" }
    }

    fun resolveDeviceModel(): String {
        return Build.MODEL.orEmpty().trim().ifBlank { "unknown-model" }
    }

    fun resolveDeviceOs(): String {
        return "Android"
    }

    fun resolveDeviceOsVersion(): String {
        val release = Build.VERSION.RELEASE.orEmpty().trim()
        if (release.isNotBlank()) return release
        return Build.VERSION.SDK_INT.toString()
    }

    fun hasDeviceInfoPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }
}
