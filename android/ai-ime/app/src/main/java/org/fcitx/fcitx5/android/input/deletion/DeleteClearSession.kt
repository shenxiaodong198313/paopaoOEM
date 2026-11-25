// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.deletion

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/** One memory-only successful clear, never a history of ordinary backspaces. */
class DeleteClearSession {
    private var text: CharSequence? = null
    private var connection: InputConnection? = null
    private var epoch = -1L
    val hasRecord: Boolean get() = !text.isNullOrEmpty()
    fun invalidate() { text = null; connection = null; epoch = -1 }
    private data class Snapshot(val text: String, val start: Int, val end: Int)
    private fun snapshot(ic: InputConnection): Snapshot? {
        val extracted = ic.getExtractedText(ExtractedTextRequest().apply {
            hintMaxChars = 262145; hintMaxLines = 262145
        }, 0) ?: return null
        val value = extracted.text?.toString() ?: return null
        if (extracted.startOffset != 0 || extracted.partialStartOffset >= 0 ||
            extracted.partialEndOffset >= 0 || value.length > 262144 ||
            extracted.selectionStart !in 0..value.length || extracted.selectionEnd !in 0..value.length) return null
        return Snapshot(value, extracted.selectionStart, extracted.selectionEnd)
    }
    private fun noSurroundingText(ic: InputConnection): Boolean =
        ic.getTextBeforeCursor(1, 0)?.isEmpty() == true &&
            ic.getTextAfterCursor(1, 0)?.isEmpty() == true
    private fun empty(ic: InputConnection): Boolean {
        if (!noSurroundingText(ic) || !ic.getSelectedText(0).isNullOrEmpty()) return false
        // A custom editor may omit getSelectedText. An extracted selection must
        // not be mistaken for an empty document simply because both sides are empty.
        return snapshot(ic)?.text?.isEmpty() != false
    }
    /** Visibility only: short reads, without changing the host or undo record. */
    fun hasContent(ic: InputConnection?): Boolean = ic != null && runCatching {
        !ic.getTextBeforeCursor(1, 0).isNullOrEmpty() ||
            !ic.getTextAfterCursor(1, 0).isNullOrEmpty() ||
            !ic.getSelectedText(0).isNullOrEmpty() ||
            !ic.getExtractedText(ExtractedTextRequest().apply {
                hintMaxChars = 1; hintMaxLines = 1
            }, 0)?.text.isNullOrEmpty()
    }.getOrDefault(false)
    fun available(ic: InputConnection?, generation: Long): Boolean =
        ic != null && connection === ic && epoch == generation && !text.isNullOrEmpty() && runCatching { empty(ic) }.getOrDefault(false)
    fun clear(ic: InputConnection, generation: Long, start: Int, end: Int) {
        invalidate()
        var restoreStart = start
        var restoreEnd = end
        var replacing = false
        var changedSelection = false
        try {
            ic.finishComposingText()
            val original = snapshot(ic)
            val selected: String
            val committed: Boolean
            if (original != null) {
                if (original.text.isEmpty()) return
                restoreStart = original.start; restoreEnd = original.end
                val left = minOf(original.start, original.end)
                val right = maxOf(original.start, original.end)
                val before = ic.getTextBeforeCursor(original.text.length + 1, 0)?.toString()
                val after = ic.getTextAfterCursor(original.text.length + 1, 0)?.toString()
                // Clear the verified range around the current caret directly.
                // Custom editors may reject selectAll/setSelection or omit
                // getSelectedText while supporting normal IME deletion.
                if (before != original.text.substring(0, left) ||
                    after != original.text.substring(right) || snapshot(ic) != original) return
                selected = original.text
                ic.beginBatchEdit()
                committed = try {
                    replacing = true
                    val removedSelection = left == right || ic.commitText("", 1)
                    removedSelection && (left == 0 && right == selected.length ||
                        ic.deleteSurroundingText(left, selected.length - right))
                } finally { ic.endBatchEdit() }
            } else {
                // Preserve support for editors that do not implement extraction.
                changedSelection = true
                if (!ic.performContextMenuAction(android.R.id.selectAll)) return
                selected = ic.getSelectedText(0)?.toString() ?: return
                if (selected.isEmpty() || selected.length > 262144 || !noSurroundingText(ic)) return
                ic.beginBatchEdit()
                committed = try {
                    replacing = true
                    ic.commitText("", 1)
                } finally { ic.endBatchEdit() }
            }
            // Some hosts publish the result only when the batch has ended.
            if (committed && empty(ic)) {
                text = selected; connection = ic; epoch = generation
            }
        } catch (_: RuntimeException) { invalidate() } finally {
            if (changedSelection && !replacing && restoreStart >= 0 && restoreEnd >= 0) {
                runCatching { ic.setSelection(restoreStart, restoreEnd) }
            }
        }
    }
    fun undo(ic: InputConnection, generation: Long) {
        if (!available(ic, generation)) { invalidate(); return }
        val saved = text ?: return
        invalidate()
        ic.commitText(saved, 1)
    }
}
