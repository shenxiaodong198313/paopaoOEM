package com.paopaoime.aikeyboard.ime.prefs.behavior

import com.paopaoime.aikeyboard.ime.view.preference.ManagedPreference

enum class FullDisplayCenterMode {
    MoveCursor,
//    MovePinyin,
    None;

    companion object : ManagedPreference.StringLikeCodec<FullDisplayCenterMode> {
        override fun decode(raw: String): FullDisplayCenterMode =
            FullDisplayCenterMode.valueOf(raw)
    }
}
