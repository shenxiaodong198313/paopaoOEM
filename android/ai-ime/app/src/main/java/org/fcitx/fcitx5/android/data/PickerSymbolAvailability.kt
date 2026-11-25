package org.fcitx.fcitx5.android.data

/** Keeps the removed regional-indicator pair out of legacy picker history. */
internal fun isAvailablePickerSymbol(text: String): Boolean {
    for (index in text.indices) {
        if (text.codePointAt(index) == 0x1F1F9 &&
            index + 2 < text.length && text.codePointAt(index + 2) == 0x1F1FC
        ) {
            return false
        }
    }
    return true
}
