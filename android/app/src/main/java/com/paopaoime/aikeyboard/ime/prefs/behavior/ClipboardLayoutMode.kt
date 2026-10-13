package com.paopaoime.aikeyboard.ime.prefs.behavior

import com.paopaoime.aikeyboard.ime.view.preference.ManagedPreference

enum class ClipboardLayoutMode {
    ListView,
    GridView,
    FlexboxView;

    companion object : ManagedPreference.StringLikeCodec<ClipboardLayoutMode> {
        override fun decode(raw: String): ClipboardLayoutMode =
            ClipboardLayoutMode.valueOf(raw)
    }
}
