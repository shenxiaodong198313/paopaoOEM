package com.paopaoime.aikeyboard.ime.prefs.behavior

import com.paopaoime.aikeyboard.ime.view.preference.ManagedPreference

enum class KeyboardSymbolSlideUpMod {
    SHORT,
    MEDIUM,
    LONG;
    companion object : ManagedPreference.StringLikeCodec<KeyboardSymbolSlideUpMod> {
        override fun decode(raw: String) = KeyboardSymbolSlideUpMod.valueOf(raw)
    }
}
