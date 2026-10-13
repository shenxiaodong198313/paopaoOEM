package org.fcitx.fcitx5.android.input.clipboard

/** Ephemeral copy events, never reconstructed from clipboard history or persisted preferences. */
class HostClipboardState {
    data class Suggestion(val host: String, val text: String, val timestamp: Long)

    var suggestion: Suggestion? = null
        private set
    private var shown = false
    private var host: String? = null
    private var startedAt = Long.MAX_VALUE
    private var lastTimestamp = Long.MIN_VALUE

    fun startHost(packageName: String?, allowed: Boolean, now: Long) {
        val next = packageName?.takeIf { allowed && it.isNotBlank() }
        if (next == host) return
        dismiss()
        host = next
        startedAt = now
    }

    fun finishHost() {
        host = null
        dismiss()
        startedAt = Long.MAX_VALUE
    }

    fun copied(text: String?, timestamp: Long, sensitive: Boolean, foregroundHost: String?) {
        if (timestamp <= lastTimestamp) return
        lastTimestamp = timestamp
        suggestion = null
        val target = host ?: return
        if (timestamp < startedAt || sensitive || text.isNullOrBlank()) return
        if (foregroundHost != null && foregroundHost != target) return
        // Keep the intent payload comfortably below Binder's transaction limit.
        if (text.length > 20_000) return
        shown = false
        suggestion = Suggestion(target, text, timestamp)
    }

    fun matchesHost(packageName: String) = host == packageName
    fun markShown() { if (suggestion != null) shown = true }
    fun hideKeyboard() { if (shown) dismiss() }
    fun dismiss() { suggestion = null; shown = false }
}
