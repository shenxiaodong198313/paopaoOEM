package com.paopaoime.aikeyboard.ime.prefs.behavior

import com.paopaoime.aikeyboard.ime.view.preference.ManagedPreference

enum class DoublePinyinSchemaMode {
    natural,
    flypy,
    abc,
    mspy,
    sogou,
    ziguang;

    companion object : ManagedPreference.StringLikeCodec<DoublePinyinSchemaMode> {
        override fun decode(raw: String) = DoublePinyinSchemaMode.valueOf(raw)
    }
}
