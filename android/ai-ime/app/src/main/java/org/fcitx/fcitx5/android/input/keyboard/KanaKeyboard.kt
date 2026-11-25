// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.theme.Theme

/** Kana keys feed readings into the same Japanese dictionary as the romaji layout. */
class KanaKeyboard(context: Context, theme: Theme) : BaseKeyboard(context, theme, Layout, "kana", forceKeyboardPopups = true) {
    companion object {
        const val Name = "Kana"
        val Rows = listOf(
            listOf("あいうえおぁぃぅぇぉ", "かきくけこがぎぐげご", "さしすせそざじずぜぞ"),
            listOf("たちつてとだぢづでどっ", "なにぬねの", "はひふへほばびぶべぼぱぴぷぺぽ"),
            listOf("まみむめも", "やゆよゃゅょ", "らりるれろ"))
        val Readings = buildMap<Char, String> {
            listOf("あいうえお" to "a i u e o", "かきくけこ" to "ka ki ku ke ko",
                "さしすせそ" to "sa shi su se so", "たちつてと" to "ta chi tsu te to",
                "なにぬねの" to "na ni nu ne no", "はひふへほ" to "ha hi fu he ho",
                "まみむめも" to "ma mi mu me mo", "やゆよ" to "ya yu yo",
                "らりるれろ" to "ra ri ru re ro", "わをんー" to "wa wo nn -",
                "がぎぐげご" to "ga gi gu ge go", "ざじずぜぞ" to "za ji zu ze zo",
                "だぢづでど" to "da di du de do", "ばびぶべぼ" to "ba bi bu be bo",
                "ぱぴぷぺぽ" to "pa pi pu pe po", "ぁぃぅぇぉゃゅょっ" to "xa xi xu xe xo xya xyu xyo xtsu")
                .forEach { (kana, codes) -> kana.toList().zip(codes.split(' ')).forEach { (k, code) -> put(k, code) } }
        }
        private fun key(group: String, width: Float = .25f) = KeyDef(
            KeyDef.Appearance.Text(group.first().toString(), 23f, percentWidth = width),
            setOf(KeyDef.Behavior.Press(KeyAction.FcitxKeyAction(group.first().toString()))),
            arrayOf(KeyDef.Popup.Keyboard.Explicit(group.map(Char::toString).toTypedArray())))
        val Layout = listOf(
            Rows[0].map { key(it) } + BackspaceKey(.25f),
            Rows[1].map { key(it) } + key("わをんー"),
            Rows[2].map { key(it) } + SymbolKey("。", .25f),
            listOf(LayoutSwitchKey("?123", NumberKeyboard.Name, viewId = R.id.button_number_mode),
                LanguageKey(), SpaceKey(), EmojiKey(.1f), ReturnKey()))
    }
    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        val reading = (action as? KeyAction.FcitxKeyAction)?.act?.singleOrNull()?.let(Readings::get)
        if (reading == null) super.onAction(action, source)
        else reading.forEach { super.onAction(KeyAction.FcitxKeyAction(it.toString()), source) }
    }
    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        super.onInputMethodUpdate(ime)
        findViewById<TextKeyView>(R.id.button_space)?.mainText?.text = "日本語"
    }
}
