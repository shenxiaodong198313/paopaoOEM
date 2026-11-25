// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.handwriting

import android.view.View
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.keyboard.KeyAction

/** One focused editor, one in-flight recognition and at most one latest pending snapshot. */
class HandwritingSession(private val service: FcitxInputMethodService, private val host: View, private val bar: KawaiiBarComponent) : InkStrokeSession {
    override val buffer = HandwritingBuffer()
    var invalidate: () -> Unit = {}
    var showMessage: (String) -> Unit = {}
    @Volatile private var active = false
    @Volatile private var epoch = -1L
    @Volatile private var connection: android.view.inputmethod.InputConnection? = null
    @Volatile private var editor: android.view.inputmethod.EditorInfo? = null
    @Volatile private var generation = 0L
    private var recognizer: ChineseInkRecognizer? = null
    private var busy = false
    private var queued = false
    private var nativeReady = false
    private var preparing = false
    @Volatile private var preparationVersion = 0L
    private var drawing = false
    private val pendingKeys = mutableListOf<Pair<KeyAction, () -> Unit>>()
    val modelsReady get() = service.canUseHandwriting()
    private fun sessionMatches() = active && epoch == service.extendedInputEpoch && connection != null &&
        connection === service.currentInputConnection && editor === service.currentInputEditorInfo
    private fun current() = sessionMatches() && host.isShown
    // Erasure retains candidates and immediately lets the next tap return to keys.
    // A continuing drag still starts a handwriting stroke.
    val hasPendingInk get() = current() && (drawing || buffer.strokes.isNotEmpty() || buffer.candidates.isNotEmpty())
    val canResumeKeyboardWithTap get() = hasPendingInk && buffer.erased && !drawing
    fun start() {
        if (active && epoch == service.extendedInputEpoch && connection === service.currentInputConnection) return
        stop(); active = true; epoch = service.extendedInputEpoch
        connection = service.currentInputConnection; editor = service.currentInputEditorInfo
    }
    fun stop() {
        active = false; generation++; preparationVersion++; busy = false; queued = false; nativeReady = false; preparing = false; drawing = false
        host.removeCallbacks(recognizeLater); host.removeCallbacks(eraseLater)
        pendingKeys.clear(); recognizer?.close(); recognizer = null; buffer.clear(); bar.clearHandwritingCandidates(this)
        invalidate(); connection = null; editor = null
    }
    private fun prepare() {
        if (nativeReady || preparing) return
        preparing = true
        val requestGeneration = generation
        val requestPreparation = preparationVersion
        service.prepareHandwriting({ sessionMatches() && generation == requestGeneration && preparationVersion == requestPreparation }) {
            preparing = false; nativeReady = true
            if (!drawing && buffer.strokes.isNotEmpty()) schedule()
        }
    }
    override fun beginStroke(): Boolean {
        if (!current()) start()
        if (!current() || !modelsReady) return false
        if (pendingKeys.isNotEmpty()) { showMessage("正在完成上一段手写，请稍候"); return false }
        if (!buffer.erased && (buffer.strokes.size >= 192 || buffer.strokes.sumOf { it.size } >= 65_536)) {
            showMessage("笔画较多，请先选择候选再继续书写"); return false
        }
        host.removeCallbacks(eraseLater); host.removeCallbacks(recognizeLater)
        buffer.begin()?.let { service.commitText(it) }
        drawing = true; bar.clearHandwritingCandidates(this); prepare(); invalidate()
        return true
    }
    override fun finishStroke(points: List<InkPoint>) {
        drawing = false
        if (!current() || !modelsReady) return
        buffer.add(points); invalidate(); schedule()
        host.removeCallbacks(eraseLater); host.postDelayed(eraseLater, 1_000)
    }
    override fun cancelStroke() {
        drawing = false
        if (buffer.strokes.isNotEmpty()) {
            schedule()
            host.removeCallbacks(eraseLater); host.postDelayed(eraseLater, 1_000)
        }
    }
    private val eraseLater = Runnable {
        if (current() && !drawing) {
            buffer.eraseTrace(); invalidate()
        }
    }
    private val recognizeLater = Runnable { recognize() }
    private fun schedule() { host.removeCallbacks(recognizeLater); host.postDelayed(recognizeLater, 300) }
    private fun recognize() {
        if (!current() || drawing || !modelsReady || buffer.strokes.isEmpty()) return
        if (!nativeReady) { prepare(); return }
        if (busy) { queued = true; return }
        busy = true; queued = false
        val requestGeneration = generation
        val token = buffer.revision
        val engine = recognizer ?: ChineseInkRecognizer().also { recognizer = it }
        engine.recognize(buffer.strokes.map { it.toList() }) completed@ { words ->
            if (generation != requestGeneration) return@completed
            busy = false
            if (current() && !drawing && buffer.revision == token) {
                if (words.isNullOrEmpty()) {
                    pendingKeys.clear()
                    showMessage("暂未识别成功，笔迹已保留；请继续书写后重试按键")
                } else if (buffer.accept(token, words)) {
                    bar.showHandwritingCandidates(this, words) { selected ->
                        if (current() && buffer.revision == token && selected in buffer.candidates) commit(selected)
                    }
                    val waiting = pendingKeys.toList(); pendingKeys.clear()
                    waiting.forEach { (action, dispatch) -> if (current() && !beforeKey(action)) dispatch() }
                }
            }
            if (queued && current()) { queued = false; schedule() }
        }
    }
    private fun commit(text: String) {
        if (!current()) return
        service.commitText(text); clear()
    }
    fun commitFirst(): Boolean {
        val text = buffer.candidates.firstOrNull() ?: return false
        if (!current()) return false
        commit(text); return true
    }
    fun clear() {
        pendingKeys.clear(); buffer.clear(); host.removeCallbacks(recognizeLater); host.removeCallbacks(eraseLater)
        queued = false; bar.clearHandwritingCandidates(this); invalidate()
    }
    fun backspace(): Boolean {
        if (buffer.strokes.isEmpty() && buffer.candidates.isEmpty()) return false
        host.removeCallbacks(eraseLater)
        if (buffer.erased) clear() else {
            buffer.undo(); bar.clearHandwritingCandidates(this); invalidate(); schedule()
            host.postDelayed(eraseLater, 1_000)
        }
        return true
    }
    /** A key immediately after the last stroke waits for its result, preserving input order. */
    fun dispatchKey(action: KeyAction, allowIdleExit: Boolean = false, dispatch: () -> Unit) {
        if (action is KeyAction.DeleteGestureAvailabilityAction) { dispatch(); return }
        if (action is KeyAction.DeleteClearAction || action is KeyAction.DeleteUndoAction) { clear(); dispatch(); return }

        // Only a real key action from the keyboard overlay confirms idle exit.
        // Retain the existing first-candidate handoff; if recognition is still
        // pending/failed, clear it so that this explicit key cannot be swallowed.
        // Previously queued actions keep their ordering instead of being discarded.
        if (allowIdleExit && canResumeKeyboardWithTap && pendingKeys.isEmpty() && action !is KeyAction.CancelSelectionAction) {
            if (!beforeKey(action)) dispatch()
            return
        }

        val deleting = (action as? KeyAction.SymAction)?.sym?.sym == FcitxKeyMapping.FcitxKey_BackSpace
        if (action !is KeyAction.CancelSelectionAction && current() &&
            (pendingKeys.isNotEmpty() || (!deleting && buffer.strokes.isNotEmpty() && buffer.candidates.isEmpty()))) {
            pendingKeys.add(action to dispatch)
            host.removeCallbacks(recognizeLater); recognize()
        } else if (!beforeKey(action)) dispatch()
    }
    /** Keyboard taps never leave handwriting candidates bound to an old native preedit. */
    fun beforeKey(action: KeyAction): Boolean {
        if (action is KeyAction.CancelSelectionAction) return false
        if (buffer.strokes.isEmpty() && buffer.candidates.isEmpty() && !preparing && !nativeReady) return false
        val sym = (action as? KeyAction.SymAction)?.sym?.sym
        if (sym == FcitxKeyMapping.FcitxKey_BackSpace && backspace()) return true
        if (sym == FcitxKeyMapping.FcitxKey_space && commitFirst()) return true
        commitFirst(); clear(); nativeReady = false; preparing = false; preparationVersion++
        return false
    }
}
