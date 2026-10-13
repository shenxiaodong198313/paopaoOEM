package com.paopaoime.aikeyboard.ime.prefs.behavior

import com.paopaoime.aikeyboard.ime.view.preference.ManagedPreference

enum class KeyboardOneHandedMod {
    LEFT,
    RIGHT;
    companion object : ManagedPreference.StringLikeCodec<KeyboardOneHandedMod> {
        override fun decode(raw: String) = KeyboardOneHandedMod.valueOf(raw)
    }
}
