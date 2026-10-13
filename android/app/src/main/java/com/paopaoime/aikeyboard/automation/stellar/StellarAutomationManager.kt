package com.paopaoime.aikeyboard.automation.stellar

import android.content.Context

enum class AutomationPrivilegedBackend {
    STELLAR,
    NONE,
}

data class AutomationPrivilegedStatus(
    val backend: AutomationPrivilegedBackend = AutomationPrivilegedBackend.NONE,
    val running: Boolean = false,
    val authorized: Boolean = false,
    val ready: Boolean = false,
    val detailMessage: String = "",
)

object StellarAutomationManager {
    fun init(context: Context) {
        StellarAutomationPrivilegedManager.init(context)
    }

    fun getStatus(context: Context): AutomationPrivilegedStatus {
        val legacy = StellarAutomationPrivilegedManager.getStatus(context.applicationContext)
        val backend = resolveBackend(legacy.running)
        return AutomationPrivilegedStatus(
            backend = backend,
            running = legacy.running,
            authorized = legacy.authorized,
            ready = legacy.ready,
            detailMessage = legacy.detailMessage,
        )
    }

    fun isBinderReady(): Boolean {
        return StellarAutomationPrivilegedManager.isBinderReady()
    }

    fun hasPermission(): Boolean {
        return StellarAutomationPrivilegedManager.hasPermission()
    }

    fun executeShellCommand(command: String): Pair<Boolean, String> {
        return StellarAutomationPrivilegedManager.executeShellCommand(command)
    }

    fun dispatchShellCommand(command: String): Pair<Boolean, String> {
        return StellarAutomationPrivilegedManager.dispatchShellCommand(command)
    }

    fun captureScreenshotBase64(context: Context): String? {
        return StellarAutomationPrivilegedManager.captureScreenshotBase64(context.applicationContext)
    }

    fun getLastScreenshotError(): String {
        return StellarAutomationPrivilegedManager.getLastScreenshotError()
    }

    private fun resolveBackend(running: Boolean): AutomationPrivilegedBackend {
        return if (running) AutomationPrivilegedBackend.STELLAR else AutomationPrivilegedBackend.NONE
    }
}
