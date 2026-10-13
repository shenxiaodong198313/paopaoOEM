package com.paopaoime.aikeyboard.ime.prefs.behavior

import com.paopaoime.aikeyboard.ime.view.preference.ManagedPreference

enum class SymbolMode {
    Symbol,
    Emojicon,
    Emoticon;
    companion object : ManagedPreference.StringLikeCodec<SymbolMode> {
        override fun decode(raw: String) = SymbolMode.valueOf(raw)
    }
}
