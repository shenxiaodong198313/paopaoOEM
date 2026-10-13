package org.fcitx.fcitx5.android.input.materialhub

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

internal object MaterialHubShizukuChecker {
    data class Status(
        val running: Boolean,
        val authorized: Boolean,
    ) {
        val installed: Boolean
            get() = running

        val ready: Boolean
            get() = running && authorized
    }

    fun getStatus(context: Context): Status {
        val running = isBinderReady()
        val authorized = running && hasPermission()
        return Status(
            running = running,
            authorized = authorized,
        )
    }

    private fun isBinderReady(): Boolean {
        if (Shizuku.isPreV11()) return false
        val binder = runCatching { Shizuku.getBinder() }.getOrNull()
        return binder != null && binder.pingBinder()
    }

    private fun hasPermission(): Boolean {
        if (Shizuku.isPreV11()) return false
        if (!isBinderReady()) return false
        return runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }
}
