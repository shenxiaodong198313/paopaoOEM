package com.paopaoime.aikeyboard.materialhub

import android.os.SystemClock

object AiCommunicateLaunchGate {
    private const val STALE_LAUNCH_TIMEOUT_MS = 8_000L

    @Volatile
    private var launchRequestedAtElapsedMs: Long = 0L

    @Volatile
    private var launchRequestToken: Long = 0L

    @Volatile
    private var panelVisible = false

    @Synchronized
    fun tryAcquire(): Long? {
        val now = SystemClock.elapsedRealtime()
        val launchInFlight =
            launchRequestedAtElapsedMs > 0L &&
                now - launchRequestedAtElapsedMs <= STALE_LAUNCH_TIMEOUT_MS
        if (panelVisible || launchInFlight) {
            return null
        }
        launchRequestedAtElapsedMs = now
        launchRequestToken = now
        return launchRequestToken
    }

    @Synchronized
    fun markPanelVisible() {
        launchRequestedAtElapsedMs = 0L
        launchRequestToken = 0L
        panelVisible = true
    }

    @Synchronized
    fun markPanelHidden() {
        launchRequestedAtElapsedMs = 0L
        launchRequestToken = 0L
        panelVisible = false
    }

    @Synchronized
    fun release() {
        launchRequestedAtElapsedMs = 0L
        launchRequestToken = 0L
        panelVisible = false
    }

    @Synchronized
    fun releaseIfPending(requestToken: Long): Boolean {
        if (panelVisible) return false
        if (launchRequestedAtElapsedMs <= 0L) return false
        if (launchRequestToken != requestToken) return false
        launchRequestedAtElapsedMs = 0L
        launchRequestToken = 0L
        return true
    }
}
