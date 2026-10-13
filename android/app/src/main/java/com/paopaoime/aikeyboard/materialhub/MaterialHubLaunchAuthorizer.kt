package com.paopaoime.aikeyboard.materialhub

import android.os.SystemClock
import java.util.UUID

object MaterialHubLaunchAuthorizer {
    private const val TOKEN_TTL_MS = 5 * 60_000L
    private const val MAX_TRACKED_TOKENS = 16

    private val activeTokens = LinkedHashMap<String, Long>()

    @Synchronized
    fun issue(): String {
        pruneExpiredTokensLocked(SystemClock.elapsedRealtime())
        val token = UUID.randomUUID().toString()
        activeTokens[token] = SystemClock.elapsedRealtime()
        trimOverflowLocked()
        return token
    }

    @Synchronized
    fun consume(token: String?): Boolean {
        val normalized = token?.takeIf { it.isNotBlank() } ?: return false
        val now = SystemClock.elapsedRealtime()
        pruneExpiredTokensLocked(now)
        if (!activeTokens.containsKey(normalized)) {
            return false
        }
        activeTokens[normalized] = now
        return true
    }

    @Synchronized
    fun clearExpired() {
        pruneExpiredTokensLocked(SystemClock.elapsedRealtime())
    }

    private fun pruneExpiredTokensLocked(now: Long) {
        val iterator = activeTokens.entries.iterator()
        while (iterator.hasNext()) {
            val (_, issuedAt) = iterator.next()
            if (now - issuedAt > TOKEN_TTL_MS) {
                iterator.remove()
            }
        }
    }

    private fun trimOverflowLocked() {
        while (activeTokens.size > MAX_TRACKED_TOKENS) {
            val oldestKey = activeTokens.entries.firstOrNull()?.key ?: return
            activeTokens.remove(oldestKey)
        }
    }
}
