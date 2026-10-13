package com.paopaoime.aikeyboard.automation.stellar

import android.content.Context
import com.paopaoime.aikeyboard.midscene.shizuku.MidsceneShizukuManager

object StellarAutomationPrivilegedManager {
    data class Status(
        val running: Boolean,
        val authorized: Boolean,
        val ready: Boolean,
        val detailMessage: String,
    )

    fun init(context: Context) {
        MidsceneShizukuManager.init(context)
    }

    fun getStatus(context: Context): Status {
        val legacy = MidsceneShizukuManager.getStatus(context.applicationContext)
        return Status(
            running = legacy.running,
            authorized = legacy.authorized,
            ready = legacy.ready,
            detailMessage = legacy.detailMessage,
        )
    }

    fun isBinderReady(): Boolean = MidsceneShizukuManager.isBinderReady()

    fun hasPermission(): Boolean = MidsceneShizukuManager.hasPermission()

    fun executeShellCommand(command: String): Pair<Boolean, String> {
        return MidsceneShizukuManager.executeShellCommand(command)
    }

    fun dispatchShellCommand(command: String): Pair<Boolean, String> {
        return MidsceneShizukuManager.dispatchShellCommand(command)
    }

    fun captureScreenshotBase64(context: Context): String? {
        return MidsceneShizukuManager.captureScreenshotBase64(context.applicationContext)
    }

    fun getLastScreenshotError(): String = MidsceneShizukuManager.getLastScreenshotError()
}
