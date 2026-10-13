package com.paopaoime.aikeyboard.ime.bridge

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ImeTextBridge {
    private const val TAG = "ImeTextBridge"
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var agentSessionActive: Boolean = false

    @Volatile
    private var agentSessionHandler: ((Boolean) -> Unit)? = null

    @Volatile
    private var updateSendButtonHandler: ((String) -> Unit)? = null

    @Volatile
    private var performEnterHandler: (() -> Boolean)? = null

    @Volatile
    private var hideSelfHandler: (() -> Unit)? = null

    @Volatile
    private var showKeyboardHandler: (() -> Boolean)? = null

    @Volatile
    private var beginKeyboardAdjustmentHandler: (() -> Boolean)? = null

    @Volatile
    private var keyboardAdjustmentStateHandler: ((Boolean) -> Unit)? = null

    @Volatile
    private var dismissKeyboardAdjustmentHandler: (() -> Boolean)? = null

    @Volatile
    private var typeTextHandler: ((String) -> Boolean)? = null

    @Volatile
    private var pendingText: String? = null

    @Volatile
    private var agentPanelHandler: ((String) -> Boolean)? = null

    @Volatile
    private var pendingAgentText: String? = null

    @Volatile
    private var agentPanelCommitHandler: (() -> Boolean)? = null

    @Volatile
    private var clipboardPanelHandler: (() -> Boolean)? = null

    @Volatile
    private var pasteLatestClipboardHandler: (() -> Boolean)? = null

    @Volatile
    private var clearClipboardHandler: (() -> Boolean)? = null

    @Volatile
    private var inputSnapshotHandler: (() -> InputSnapshot?)? = null

    data class InputSnapshot(
        val beforeCursor: String,
        val selectedText: String,
        val afterCursor: String
    ) {
        fun mergedText(): String = beforeCursor + selectedText + afterCursor
    }


    fun bind(handler: (String) -> Boolean) {
        typeTextHandler = handler
        flush()
    }

    fun unbind() {
        typeTextHandler = null
        pendingText = null
    }

    fun isEnabled(): Boolean = typeTextHandler != null

    fun typeText(text: String): Boolean {
        val currentHandler = typeTextHandler
        if (currentHandler == null) {
            pendingText = text
            return false
        }
        val ok = invokeAndWaitOnMain(currentHandler, text)
        if (ok) {
            pendingText = null
        } else {
            pendingText = text
        }
        return ok
    }

    fun flush() {
        val text = pendingText ?: return
        val currentHandler = typeTextHandler ?: return
        val ok = invokeAndWaitOnMain(currentHandler, text)
        if (ok) pendingText = null
    }

    fun bindAgentPanel(handler: (String) -> Boolean) {
        agentPanelHandler = handler
        flushAgentPanel()
    }

    fun unbindAgentPanel() {
        agentPanelHandler = null
        pendingAgentText = null
    }

    fun isAgentPanelEnabled(): Boolean = agentPanelHandler != null

    fun showAgentPanel(text: String): Boolean {
        pendingAgentText = text
        val currentHandler = agentPanelHandler ?: return false
        val ok = invokeAndWaitOnMain(currentHandler, text)
        if (ok) pendingAgentText = null
        return ok
    }

    fun flushAgentPanel() {
        val text = pendingAgentText ?: return
        val currentHandler = agentPanelHandler ?: return
        val ok = invokeAndWaitOnMain(currentHandler, text)
        if (ok) pendingAgentText = null
    }

    fun bindAgentPanelCommit(handler: () -> Boolean) {
        agentPanelCommitHandler = handler
    }

    fun unbindAgentPanelCommit() {
        agentPanelCommitHandler = null
    }

    fun commitAgentPanelText(): Boolean {
        val currentHandler = agentPanelCommitHandler ?: return false
        return invokeAndWaitOnMain(currentHandler)
    }

    fun bindClipboardPanel(handler: () -> Boolean) {
        clipboardPanelHandler = handler
    }

    fun unbindClipboardPanel() {
        clipboardPanelHandler = null
    }

    fun isClipboardPanelEnabled(): Boolean = clipboardPanelHandler != null

    fun showClipboardPanel(): Boolean {
        val currentHandler = clipboardPanelHandler ?: return false
        return invokeAndWaitOnMain(currentHandler)
    }

    fun bindPasteLatestClipboard(handler: () -> Boolean) {
        pasteLatestClipboardHandler = handler
    }

    fun unbindPasteLatestClipboard() {
        pasteLatestClipboardHandler = null
    }

    fun pasteLatestClipboardContent(): Boolean {
        val currentHandler = pasteLatestClipboardHandler ?: return false
        return invokeAndWaitOnMain(currentHandler)
    }

    fun bindClearClipboard(handler: () -> Boolean) {
        clearClipboardHandler = handler
    }

    fun unbindClearClipboard() {
        clearClipboardHandler = null
    }

    fun clearClipboard(): Boolean {
        val currentHandler = clearClipboardHandler ?: return false
        return invokeAndWaitOnMain(currentHandler)
    }

    fun bindInputSnapshot(handler: () -> InputSnapshot?) {
        inputSnapshotHandler = handler
    }

    fun unbindInputSnapshot() {
        inputSnapshotHandler = null
    }

    fun readInputSnapshot(): InputSnapshot? {
        val currentHandler = inputSnapshotHandler ?: return null
        return invokeAndWaitForValueOnMain(currentHandler)
    }

    fun bindAgentSession(handler: (Boolean) -> Unit) {
        agentSessionHandler = handler
    }

    fun unbindAgentSession() {
        agentSessionHandler = null
    }

    fun setAgentSessionActive(active: Boolean) {
        agentSessionActive = active
        val currentHandler = agentSessionHandler ?: return
        mainHandler.post {
            try {
                currentHandler(active)
            } catch (t: Throwable) {
                Log.w(TAG, "agentSessionHandler invocation failed: ${t.message}", t)
            }
        }
    }

    fun isAgentSessionActive(): Boolean = agentSessionActive

    fun bindUpdateSendButton(handler: (String) -> Unit) {
        updateSendButtonHandler = handler
    }

    fun unbindUpdateSendButton() {
        updateSendButtonHandler = null
    }

    fun updateSendButtonText(text: String) {
        val currentHandler = updateSendButtonHandler ?: return
        mainHandler.post {
            try {
                currentHandler(text)
            } catch (t: Throwable) {
                Log.w(TAG, "updateSendButtonHandler invocation failed: ${t.message}", t)
            }
        }
    }

    fun bindPerformEnter(handler: () -> Boolean) {
        performEnterHandler = handler
    }

    fun unbindPerformEnter() {
        performEnterHandler = null
    }

    fun performEnter(): Boolean {
        val currentHandler = performEnterHandler ?: return false
        return invokeAndWaitOnMain(currentHandler)
    }

    fun bindHideSelf(handler: () -> Unit) {
        hideSelfHandler = handler
    }

    fun unbindHideSelf() {
        hideSelfHandler = null
    }

    fun requestHideSelf() {
        val currentHandler = hideSelfHandler ?: return
        mainHandler.post {
            try {
                currentHandler()
            } catch (t: Throwable) {
                Log.w(TAG, "hideSelfHandler invocation failed: ${t.message}", t)
            }
        }
    }

    fun bindShowKeyboard(handler: () -> Boolean) {
        showKeyboardHandler = handler
    }

    fun unbindShowKeyboard() {
        showKeyboardHandler = null
    }

    fun requestShowKeyboard(): Boolean {
        val currentHandler = showKeyboardHandler ?: return false
        return invokeAndWaitOnMain(currentHandler)
    }

    fun bindBeginKeyboardAdjustment(handler: () -> Boolean) {
        beginKeyboardAdjustmentHandler = handler
    }

    fun unbindBeginKeyboardAdjustment() {
        beginKeyboardAdjustmentHandler = null
    }

    fun requestBeginKeyboardAdjustment(): Boolean {
        val currentHandler = beginKeyboardAdjustmentHandler ?: return false
        return invokeAndWaitOnMain(currentHandler)
    }

    fun bindKeyboardAdjustmentState(handler: (Boolean) -> Unit) {
        keyboardAdjustmentStateHandler = handler
    }

    fun unbindKeyboardAdjustmentState() {
        keyboardAdjustmentStateHandler = null
    }

    fun notifyKeyboardAdjustmentState(active: Boolean) {
        val currentHandler = keyboardAdjustmentStateHandler ?: return
        mainHandler.post {
            try {
                currentHandler(active)
            } catch (t: Throwable) {
                Log.w(TAG, "keyboardAdjustmentStateHandler invocation failed: ${t.message}", t)
            }
        }
    }

    fun bindDismissKeyboardAdjustment(handler: () -> Boolean) {
        dismissKeyboardAdjustmentHandler = handler
    }

    fun unbindDismissKeyboardAdjustment() {
        dismissKeyboardAdjustmentHandler = null
    }

    fun requestDismissKeyboardAdjustment(): Boolean {
        val currentHandler = dismissKeyboardAdjustmentHandler ?: return false
        return invokeAndWaitOnMain(currentHandler)
    }

    private fun invokeAndWaitOnMain(handler: (String) -> Boolean, text: String): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return try {
                handler(text)
            } catch (t: Throwable) {
                Log.w(TAG, "handler invocation failed: ${t.message}", t)
                false
            }
        }

        val latch = CountDownLatch(1)
        var result = false
        mainHandler.post {
            result = try {
                handler(text)
            } catch (t: Throwable) {
                Log.w(TAG, "handler invocation failed: ${t.message}", t)
                false
            } finally {
                latch.countDown()
            }
        }
        val completed = try {
            latch.await(1200, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            Log.w(TAG, "handler await interrupted: ${t.message}", t)
            false
        }
        if (!completed) Log.w(TAG, "handler await timeout")
        return completed && result
    }

    private fun invokeAndWaitOnMain(handler: () -> Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return try {
                handler()
            } catch (t: Throwable) {
                Log.w(TAG, "handler invocation failed: ${t.message}", t)
                false
            }
        }

        val latch = CountDownLatch(1)
        var result = false
        mainHandler.post {
            result = try {
                handler()
            } catch (t: Throwable) {
                Log.w(TAG, "handler invocation failed: ${t.message}", t)
                false
            } finally {
                latch.countDown()
            }
        }
        val completed = try {
            latch.await(1200, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            Log.w(TAG, "handler await interrupted: ${t.message}", t)
            false
        }
        if (!completed) Log.w(TAG, "handler await timeout")
        return completed && result
    }

    private fun <T> invokeAndWaitForValueOnMain(handler: () -> T?): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return try {
                handler()
            } catch (t: Throwable) {
                Log.w(TAG, "value handler invocation failed: ${t.message}", t)
                null
            }
        }

        val latch = CountDownLatch(1)
        var result: T? = null
        mainHandler.post {
            result = try {
                handler()
            } catch (t: Throwable) {
                Log.w(TAG, "value handler invocation failed: ${t.message}", t)
                null
            } finally {
                latch.countDown()
            }
        }
        val completed = try {
            latch.await(1200, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            Log.w(TAG, "value handler await interrupted: ${t.message}", t)
            false
        }
        if (!completed) Log.w(TAG, "value handler await timeout")
        return if (completed) result else null
    }
}
