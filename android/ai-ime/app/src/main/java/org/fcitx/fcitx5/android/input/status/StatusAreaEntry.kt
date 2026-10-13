/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.Action

sealed class StatusAreaEntry(
    val label: String,
    @DrawableRes
    val icon: Int,
    val active: Boolean
) {
    class Custom(
        val drawerId: String,
        label: String,
        val iconDrawable: Drawable?,
    ) : StatusAreaEntry(label, 0, false)

    class Android(label: String, icon: Int, val type: Type) :
        StatusAreaEntry(label, icon, false) {
        enum class Type {
            VoiceInput,
            KeyboardSelection,
            AiReply,
            MaterialHub,
            Emoji,
            Clipboard,
            CustomizeToolbar,
            Skin,
            KeyEffects,
            InputSettings,
            TraditionalInput,
            KeyboardAdjust,
            ReloadConfig,
            InputMethod
        }
    }

    class KeyboardSelection(
        label: String,
        icon: Int,
        val target: Target,
        active: Boolean = false
    ) : StatusAreaEntry(label, icon, active) {
        enum class Target {
            Pinyin9,
            Pinyin26,
            English26,
            Shuangpin,
            Wubi,
            WubiPinyin
        }
    }

    class Fcitx(val action: Action, label: String, icon: Int, active: Boolean) :
        StatusAreaEntry(label, icon, active)

    companion object {
        // Engine status controls remain available internally, but are not product tool tiles.
        internal fun isHiddenPanelAction(action: Action): Boolean {
            if (action.isSeparator || action.name == "fcitx-rime-im" || action.name == "fullwidth") {
                return true
            }
            if (action.icon.startsWith("fcitx-fullwidth-")) return true
            return action.name.startsWith("fcitx-rime-") &&
                listOf("-ascii_punct", "-full_shape", "-search_single_char").any(action.name::endsWith)
        }

        private fun drawableFromIconName(icon: String) = when (icon) {
            // androidkeyboard
            "tools-check-spelling" -> R.drawable.ic_baseline_spellcheck_24
            // fcitx5-chinese-addons
            "fcitx-chttrans-active" -> R.drawable.ic_fcitx_status_chttrans_trad
            "fcitx-chttrans-inactive" -> R.drawable.ic_fcitx_status_chttrans_simp
            "fcitx-punc-active" -> R.drawable.ic_fcitx_status_punc_active
            "fcitx-punc-inactive" -> R.drawable.ic_fcitx_status_punc_inactive
            "fcitx-fullwidth-active" -> R.drawable.ic_fcitx_status_fullwidth_active
            "fcitx-fullwidth-inactive" -> R.drawable.ic_fcitx_status_fullwidth_inactive
            "fcitx-remind-active" -> R.drawable.ic_fcitx_status_prediction_active
            "fcitx-remind-inactive" -> R.drawable.ic_fcitx_status_prediction_inactive
            // fcitx5-unikey
            "document-edit" -> R.drawable.ic_baseline_edit_24
            "character-set" -> R.drawable.ic_baseline_text_format_24
            "edit-find" -> R.drawable.ic_baseline_search_24
            // fallback
            "" -> 0
            else -> {
                if (icon.endsWith("-inactive")) {
                    R.drawable.ic_baseline_code_off_24
                } else {
                    R.drawable.ic_baseline_code_24
                }
            }
        }

        fun fromAction(it: Action): Fcitx {
            val active = it.icon.endsWith("-active") || it.isChecked
            // Rime's emoji switch has no icon name; avoid rendering a color emoji as its icon.
            val icon = if (it.name.startsWith("fcitx-rime-") && it.name.endsWith("-emoji")) {
                R.drawable.ic_oem_toolbar_emoji
            } else {
                drawableFromIconName(it.icon)
            }
            return Fcitx(it, it.shortText, icon, active)
        }
    }
}
