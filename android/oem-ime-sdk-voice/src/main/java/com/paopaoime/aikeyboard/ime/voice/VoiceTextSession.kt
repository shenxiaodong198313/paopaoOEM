package com.paopaoime.aikeyboard.ime.voice

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/** Own only this utterance's composing region. Never delete surrounding host text. */
class VoiceTextSession(private val connection: InputConnection, initialCursor: Int = -1, private val current: () -> Boolean) {
    private var prefix = connection.getTextBeforeCursor(48, 0)?.toString()
    private var suffix = connection.getTextAfterCursor(32, 0)?.toString()
    private var preview = ""
    private var pending = ""
    private var composing = prefix != null && suffix != null
    private var anchor = connection.getExtractedText(ExtractedTextRequest(), 0)?.let { it.startOffset + it.selectionStart } ?: initialCursor
    private val expected = ArrayDeque<Int>()
    private var ended = false
    private var invalidated = false
    init { connection.finishComposingText() }
    fun apply(text: String, final: Boolean): Boolean {
        if (ended || !locationMatches()) return false
        pending = if (final) "" else text
        if (final && text.isEmpty()) { connection.finishComposingText(); prefix = (prefix.orEmpty() + preview).takeLast(48); anchor += preview.length; preview = ""; return true }
        if (anchor >= 0 && (final || composing)) { expected.addLast(anchor + text.length); while (expected.size > 32) expected.removeFirst() }
        if (final) {
            if (!connection.commitText(text, 1)) return false
            connection.finishComposingText()
            prefix = prefix?.let { (it + text).takeLast(48) }; anchor = if (anchor >= 0) anchor + text.length else -1; preview = ""
        } else if (composing) {
            if (connection.setComposingText(text, 1)) preview = text else { composing = false; expected.clear() }
        }
        return true
    }
    private fun locationMatches(): Boolean {
        if (invalidated || !current()) return false
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
        if (anchor >= 0 && extracted != null && (extracted.selectionStart != extracted.selectionEnd || extracted.startOffset + extracted.selectionStart != anchor + preview.length)) return false
        if (prefix != null && suffix != null) {
            val before = connection.getTextBeforeCursor(48, 0)?.toString()
            val after = connection.getTextAfterCursor(32, 0)?.toString()
            if (before != (prefix + preview).takeLast(48) || after != suffix) return false
        }
        return true
    }
    fun selection(start: Int, end: Int, candidatesStart: Int, candidatesEnd: Int): Boolean {
        if (ended) return true
        if (start != end) { invalidated = true; return false }
        if (expected.contains(end)) {
            while (expected.isNotEmpty()) { if (expected.removeFirst() == end) break }
            return true
        }
        val valid = anchor >= 0 && end == anchor + preview.length && (preview.isEmpty() || candidatesStart == anchor && candidatesEnd == end)
        if (!valid) invalidated = true
        return valid
    }
    fun finish() {
        if (ended) return
        ended = true
        if (locationMatches()) {
            if (!composing && pending.isNotEmpty()) connection.commitText(pending, 1)
            connection.finishComposingText()
        }
        pending = ""
    }
}
