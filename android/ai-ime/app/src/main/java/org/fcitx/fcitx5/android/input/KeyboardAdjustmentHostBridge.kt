package org.fcitx.fcitx5.android.input

import android.os.Handler
import android.os.Looper

object KeyboardAdjustmentHostBridge {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var stateHandler: ((Boolean) -> Unit)? = null

    fun bindState(handler: (Boolean) -> Unit) {
        stateHandler = handler
    }

    fun unbindState() {
        stateHandler = null
    }

    fun notifyState(active: Boolean) {
        val handler = stateHandler ?: return
        mainHandler.post {
            handler(active)
        }
    }
}
