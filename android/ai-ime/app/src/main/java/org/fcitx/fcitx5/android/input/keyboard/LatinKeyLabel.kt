package org.fcitx.fcitx5.android.input.keyboard

/** Keycap/preview appearance only; never use this to transform committed input. */
internal fun latinKeyDisplayLabel(text: String): String =
    if (text.length == 1 && text[0] in 'a'..'z') text[0].uppercaseChar().toString() else text
