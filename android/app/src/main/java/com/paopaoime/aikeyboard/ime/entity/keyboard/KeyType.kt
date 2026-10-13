package com.paopaoime.aikeyboard.ime.entity.keyboard

import com.paopaoime.aikeyboard.ime.view.preference.ManagedPreference

enum class KeyType {
    AccentKey,
    Function,
    Normal;

    companion object : ManagedPreference.StringLikeCodec<KeyType> {
        override fun decode(raw: String): KeyType =
            KeyType.valueOf(raw)
    }
}
