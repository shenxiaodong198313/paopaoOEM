/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.annotation.Keep
import androidx.core.view.allViews
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.status.KeyboardSelectionPreset
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.views.imageResource

@SuppressLint("ViewConstructor")
class TextKeyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, Layout, "qwerty") {

    enum class CapsState { None, Once, Lock }

    companion object {
        const val Name = "Text"

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                AlphabetKey("Q", "1"),
                AlphabetKey("W", "2"),
                AlphabetKey("E", "3"),
                AlphabetKey("R", "4"),
                AlphabetKey("T", "5"),
                AlphabetKey("Y", "6"),
                AlphabetKey("U", "7"),
                AlphabetKey("I", "8"),
                AlphabetKey("O", "9"),
                AlphabetKey("P", "0")
            ),
            listOf(
                AlphabetKey("A", "@"),
                AlphabetKey("S", "*"),
                AlphabetKey("D", "+"),
                AlphabetKey("F", "-"),
                AlphabetKey("G", "="),
                AlphabetKey("H", "/"),
                AlphabetKey("J", "#"),
                AlphabetKey("K", "("),
                AlphabetKey("L", ")")
            ),
            listOf(
                CapsKey(),
                AlphabetKey("Z", "'"),
                AlphabetKey("X", ":"),
                AlphabetKey("C", "\""),
                AlphabetKey("V", "?"),
                AlphabetKey("B", "!"),
                AlphabetKey("N", "~"),
                AlphabetKey("M", "\\"),
                BackspaceKey()
            ),
            listOf(
                LayoutSwitchKey("?123", NumberKeyboard.Name, viewId = R.id.button_number_mode),
                CommaPeriodKey(0.1f, KeyDef.Appearance.Variant.Alternative),
                LanguageKey(),
                SpaceKey(),
                EmojiKey(0.1f, KeyDef.Appearance.Variant.Alternative),
                ReturnKey()
            )
        )
    }

    val caps: ImageKeyView by lazy { findViewById(R.id.button_caps) }
    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val quickphrase: ImageKeyView by lazy { findViewById(R.id.button_quickphrase) }
    val lang: ImageKeyView by lazy { findViewById(R.id.button_lang) }
    val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    private val showLangSwitchKey = AppPrefs.getInstance().keyboard.showLangSwitchKey

    @Keep
    private val showLangSwitchKeyListener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        updateLangSwitchKey(v)
    }

    private val keepLettersUppercase by AppPrefs.getInstance().keyboard.keepLettersUppercase

    init {
        updateLangSwitchKey(showLangSwitchKey.getValue())
        showLangSwitchKey.registerOnChangeListener(showLangSwitchKeyListener)
    }

    private val textKeys: List<TextKeyView> by lazy {
        allViews.filterIsInstance(TextKeyView::class.java).toList()
    }

    private val stackedTextKeys: List<StackedTextKeyView> by lazy {
        allViews.filterIsInstance(StackedTextKeyView::class.java).toList()
    }

    private var capsState: CapsState = CapsState.None

    private fun transformAlphabet(c: String): String {
        return when (capsState) {
            CapsState.None -> c.lowercase()
            else -> c.uppercase()
        }
    }

    private var punctuationMapping: Map<String, String> = mapOf()
    private fun transformPunctuation(p: String) = punctuationMapping.getOrDefault(p, p)
    private var lastDisplayIme: InputMethodEntry? = null

    private fun fallbackDisplayIme(): InputMethodEntry {
        val prefs = AppPrefs.getInstance()
        if (prefs.keyboard.useT9KeyboardLayout.getValue()) {
            return InputMethodEntry(
                KeyboardSelectionPreset.ENGLISH,
                "English",
                "input-keyboard",
                "",
                "En",
                "en",
                "androidkeyboard",
                true
            )
        }
        return when (prefs.internal.lastFullKeyboardChineseIme.getValue()) {
            KeyboardSelectionPreset.SHUANGPIN -> InputMethodEntry(
                KeyboardSelectionPreset.SHUANGPIN,
                context.getString(R.string.status_area_keyboard_selection_shuangpin),
                "input-keyboard",
                "",
                "双",
                "zh_CN",
                "keyboard",
                true
            )
            KeyboardSelectionPreset.WUBI -> InputMethodEntry(
                KeyboardSelectionPreset.WUBI,
                context.getString(R.string.status_area_keyboard_selection_wubi),
                "input-keyboard",
                "",
                "五",
                "zh_CN",
                "keyboard",
                true
            )
            KeyboardSelectionPreset.WUBI_PINYIN -> InputMethodEntry(
                KeyboardSelectionPreset.WUBI_PINYIN,
                context.getString(R.string.status_area_keyboard_selection_wubi_pinyin),
                "input-keyboard",
                "",
                "五",
                "zh_CN",
                "keyboard",
                true
            )
            else -> InputMethodEntry(
                KeyboardSelectionPreset.PINYIN,
                "拼音",
                "fcitx-pinyin",
                "拼音",
                "拼",
                "zh_CN",
                "pinyin",
                true
            )
        }
    }

    private fun englishDisplayIme(): InputMethodEntry =
        InputMethodEntry(
            KeyboardSelectionPreset.ENGLISH,
            "English",
            "input-keyboard",
            "",
            "En",
            "en",
            "androidkeyboard",
            true
        )

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        var transformed = action
        when (action) {
            is KeyAction.FcitxKeyAction -> when (source) {
                KeyActionListener.Source.Keyboard -> {
                    when (capsState) {
                        CapsState.None -> {
                            transformed = action.copy(act = action.act.lowercase())
                        }
                        CapsState.Once -> {
                            transformed = action.copy(
                                act = action.act.uppercase(),
                                states = KeyStates(KeyState.Virtual, KeyState.Shift)
                            )
                            switchCapsState()
                        }
                        CapsState.Lock -> {
                            transformed = action.copy(
                                act = action.act.uppercase(),
                                states = KeyStates(KeyState.Virtual, KeyState.CapsLock)
                            )
                        }
                    }
                }
                KeyActionListener.Source.Popup -> {
                    if (capsState == CapsState.Once) {
                        switchCapsState()
                    }
                }
            }
            is KeyAction.CapsAction -> switchCapsState(action.lock)
            else -> {}
        }
        super.onAction(transformed, source)
    }

    override fun onAttach() {
        capsState = CapsState.None
        updateCapsButtonIcon()
        updateAlphabetKeys()
    }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.updateReturnDrawable(returnDrawable)
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        punctuationMapping = mapping
        updatePunctuationKeys()
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        val rawDisplayIme = when {
            ime.uniqueName.isNotEmpty() -> ime.also { lastDisplayIme = it }
            else -> lastDisplayIme ?: fallbackDisplayIme()
        }
        val displayIme = when {
            // If text keyboard is active while T9 layout mode is enabled, we are in
            // the T9 English text view and the space label must stay English.
            AppPrefs.getInstance().keyboard.useT9KeyboardLayout.getValue() -> englishDisplayIme()
            rawDisplayIme.uniqueName == KeyboardSelectionPreset.ENGLISH ||
                rawDisplayIme.languageCode.startsWith("en", ignoreCase = true) -> englishDisplayIme()
            else -> rawDisplayIme
        }
        space.mainText.text = buildString {
            append(displayIme.displayName)
            displayIme.subMode.run { label.ifEmpty { name.ifEmpty { null } } }?.let { append(" ($it)") }
        }
        lang.img.imageResource =
            if (displayIme.uniqueName == KeyboardSelectionPreset.ENGLISH ||
                displayIme.languageCode.startsWith("en", ignoreCase = true)
            ) {
                R.drawable.ic_t9_lang_en
            } else {
                R.drawable.ic_t9_lang_zh
            }
        lang.setSkinState(if (displayIme.uniqueName == KeyboardSelectionPreset.ENGLISH ||
            displayIme.languageCode.startsWith("en", ignoreCase = true)) "en" else "zh")
        if (capsState != CapsState.None) {
            switchCapsState()
        }
    }

    private fun transformPopupPreview(c: String): String {
        if (c.length != 1) return c
        if (c[0] in 'a'..'z' || c[0] in 'A'..'Z') return latinKeyDisplayLabel(c)
        if (c[0].isLetter()) return transformAlphabet(c)
        return transformPunctuation(c)
    }

    override fun onPopupAction(action: PopupAction) {
        val newAction = when (action) {
            is PopupAction.PreviewAction -> action.copy(content = transformPopupPreview(action.content))
            is PopupAction.PreviewUpdateAction -> action.copy(content = transformPopupPreview(action.content))
            is PopupAction.ShowKeyboardAction -> {
                val label = action.keyboard.label
                if (label.length == 1 && label[0].isLetter())
                    action.copy(keyboard = KeyDef.Popup.Keyboard(transformAlphabet(label)))
                else action
            }
            else -> action
        }
        super.onPopupAction(newAction)
    }

    private fun switchCapsState(lock: Boolean = false) {
        capsState =
            if (lock) {
                when (capsState) {
                    CapsState.Lock -> CapsState.None
                    else -> CapsState.Lock
                }
            } else {
                when (capsState) {
                    CapsState.None -> CapsState.Once
                    else -> CapsState.None
                }
            }
        updateCapsButtonIcon()
        updateAlphabetKeys()
    }

    private fun updateCapsButtonIcon() {
        caps.setSkinState(when (capsState) {
            CapsState.None -> "off"
            CapsState.Once -> "once"
            CapsState.Lock -> "lock"
        })
        caps.img.apply {
            imageResource = when (capsState) {
                CapsState.None -> R.drawable.ic_capslock_none
                CapsState.Once -> R.drawable.ic_capslock_once
                CapsState.Lock -> R.drawable.ic_capslock_lock
            }
        }
    }

    private fun updateLangSwitchKey(visible: Boolean) {
        lang.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateAlphabetKeys() {
        textKeys.forEach {
            if (it.def !is KeyDef.Appearance.AltText) return
            it.mainText.text = it.def.displayText.let { str ->
                if (str.length != 1 || !str[0].isLetter()) return@forEach
                if (str[0] in 'a'..'z' || str[0] in 'A'..'Z') latinKeyDisplayLabel(str)
                else if (keepLettersUppercase || (theme as? Theme.Custom)?.renderStyle?.uppercaseLatinLabels == true)
                    str.uppercase(java.util.Locale.ROOT) else transformAlphabet(str)
            }
        }
    }

    private fun updatePunctuationKeys() {
        textKeys.forEach {
            if (it is AltTextKeyView) {
                it.def as KeyDef.Appearance.AltText
                it.altText.text = transformPunctuation(it.def.altText)
            } else {
                it.def as KeyDef.Appearance.Text
                it.mainText.text = it.def.displayText.let { str ->
                    if (str[0].run { isLetter() || isWhitespace() }) return@forEach
                    transformPunctuation(str)
                }
            }
        }
        stackedTextKeys.forEach {
            it.topText.text = transformPunctuation(it.stackedDef.topText)
            it.bottomText.text = transformPunctuation(it.stackedDef.bottomText)
        }
    }

}
