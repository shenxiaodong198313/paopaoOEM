package com.paopaoime.aikeyboard.midscene.agent

import java.util.concurrent.CopyOnWriteArraySet

object MidsceneTaskControlCenter {
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()
    @Volatile
    private var pendingStopSource: String? = null

    fun registerStopListener(listener: (String) -> Unit) {
        listeners.add(listener)
        val pending = synchronized(this) {
            pendingStopSource.also { pendingStopSource = null }
        }
        if (pending != null) {
            listener.invoke(pending)
        }
    }

    fun unregisterStopListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    fun requestStopFromOverlay(source: String = "overlay_unknown") {
        val snapshot = listeners.toList()
        if (snapshot.isEmpty()) {
            synchronized(this) {
                pendingStopSource = source
            }
            return
        }
        synchronized(this) {
            pendingStopSource = null
        }
        snapshot.forEach { it.invoke(source) }
    }

    fun clearPendingStopRequest() {
        synchronized(this) {
            pendingStopSource = null
        }
    }
}
