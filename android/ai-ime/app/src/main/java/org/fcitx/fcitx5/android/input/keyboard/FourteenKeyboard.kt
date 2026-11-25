// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme

/** Paired letters use an uppercase Rime code; long press chooses an exact letter. */
class FourteenKeyboard(context: Context, theme: Theme) :
    BaseKeyboard(context, theme, Layout, "fourteen", forceKeyboardPopups = true) {
    companion object {
        const val Name = "Fourteen"
        val Groups = listOf(listOf("qw", "er", "ty", "ui", "op"),
            listOf("as", "df", "gh", "jk", "l"), listOf("zx", "cv", "bn", "m"))
        fun code(group: String) = if (group.length == 1) group else group.first().uppercase()
        private fun key(group: String, width: Float) = KeyDef(
            KeyDef.Appearance.Text(group.uppercase(), 23f, percentWidth = width),
            setOf(KeyDef.Behavior.Press(KeyAction.FcitxKeyAction(code(group)))),
            arrayOf(KeyDef.Popup.Keyboard.Explicit(group.map(Char::toString).toTypedArray()))
        )
        val Layout = listOf(
            Groups[0].map { key(it, .2f) },
            Groups[1].map { key(it, .2f) },
            Groups[2].map { key(it, .2f) } + BackspaceKey(.2f),
            listOf(LayoutSwitchKey("?123", NumberKeyboard.Name, viewId = R.id.button_number_mode),
                CommaPeriodKey(.1f, KeyDef.Appearance.Variant.Alternative), LanguageKey(),
                SpaceKey(), EmojiKey(.1f, KeyDef.Appearance.Variant.Alternative), ReturnKey())
        )
    }
}
