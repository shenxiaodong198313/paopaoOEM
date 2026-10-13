/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

import org.fcitx.fcitx5.android.core.InputMethodEntry

object KeyboardSelectionPreset {
    const val T9 = "rime"
    const val ENGLISH = "keyboard-us"
    const val PINYIN = "pinyin"
    const val SHUANGPIN = "shuangpin"
    const val WUBI = "wbx"
    const val WUBI_PINYIN = "wbpy"

    val preferredEnabledImeOrder = listOf(
        PINYIN,
        T9,
        ENGLISH,
        SHUANGPIN,
        WUBI,
        WUBI_PINYIN
    )

    fun reorderEnabledIme(
        available: List<InputMethodEntry>,
        enabled: List<InputMethodEntry>
    ): List<InputMethodEntry> {
        val availableByUniqueName = available.associateBy { it.uniqueName }
        return buildList {
            preferredEnabledImeOrder.forEach { uniqueName ->
                availableByUniqueName[uniqueName]?.let(::add)
            }
            enabled.forEach { entry ->
                if (none { it.uniqueName == entry.uniqueName }) {
                    add(entry)
                }
            }
        }
    }
}
