package com.paopaoime.aikeyboard.ime.prefs.behavior

import com.paopaoime.aikeyboard.ime.view.preference.ManagedPreference

enum class FullDisplayKeyMode {
    SwitchIme,
    SwitchLanguage,
    Clipboard,
    Phrases,
    None;

    companion object : ManagedPreference.StringLikeCodec<FullDisplayKeyMode> {
        override fun decode(raw: String): FullDisplayKeyMode =
            FullDisplayKeyMode.valueOf(raw)
    }
}
