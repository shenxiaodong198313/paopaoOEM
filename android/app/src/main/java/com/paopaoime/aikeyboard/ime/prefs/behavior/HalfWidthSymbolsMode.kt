package com.paopaoime.aikeyboard.ime.prefs.behavior

import com.paopaoime.aikeyboard.ime.view.preference.ManagedPreference

enum class HalfWidthSymbolsMode {
    All,
    OnlyUsed,
    None;

    companion object : ManagedPreference.StringLikeCodec<HalfWidthSymbolsMode> {
        override fun decode(raw: String): HalfWidthSymbolsMode =
            HalfWidthSymbolsMode.valueOf(raw)
    }
}
