package com.paopaoime.aikeyboard.sdk.core

import android.content.Context

fun interface OemClipboardQuickReplyHandler {
    /**
     * Called on the main thread with the IME service context and the captured copy.
     * The return value is reserved and ignored; the host fully owns this click.
     */
    fun onQuickReply(context: Context, clipboardText: String, hostPackageName: String?): Boolean
}
