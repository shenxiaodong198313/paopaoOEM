package com.paopaoime.aikeyboard.ime.prefs.behavior

import com.paopaoime.aikeyboard.ime.view.preference.ManagedPreference

enum class PopupMenuMode {
    Text,
    Clear,
    SwitchIME,
    EnglishCell,
    Revertl,
    Move,
    Enter,
    EMOJI,
    None;

    companion object : ManagedPreference.StringLikeCodec<PopupMenuMode> {
        override fun decode(raw: String) = PopupMenuMode.valueOf(raw)
    }
}
