package com.paopaoime.aikeyboard.ime.prefs.behavior

import com.paopaoime.aikeyboard.ime.view.preference.ManagedPreference

enum class SkbStyleMode {
    Google,
    Samsung;
    companion object : ManagedPreference.StringLikeCodec<SkbStyleMode> {
        override fun decode(raw: String) = try {
            SkbStyleMode.valueOf(raw)
        } catch (_: IllegalArgumentException) {
            Google
        }
    }
}
