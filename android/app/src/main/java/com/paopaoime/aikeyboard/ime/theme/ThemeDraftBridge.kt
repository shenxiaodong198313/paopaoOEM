package com.paopaoime.aikeyboard.ime.theme

import com.paopaoime.aikeyboard.ime.application.Launcher
import com.paopaoime.aikeyboard.ime.theme.draft.ThemeCorePayload
import com.paopaoime.aikeyboard.ime.theme.draft.ThemeDraft
import com.paopaoime.aikeyboard.ime.theme.draft.ThemeDraftStorage
import com.paopaoime.aikeyboard.ime.theme.draft.ThemeEditorPayload
import com.paopaoime.aikeyboard.ime.theme.draft.toThemeCustom
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs

object ThemeDraftBridge {

    private val storage by lazy {
        ThemeDraftStorage(Launcher.instance.context)
    }

    suspend fun createDraftFromTheme(
        themeName: String = ThemeManager.activeTheme.name,
        draftName: String? = null
    ): ThemeDraft? {
        val theme = ThemeManager.getTheme(themeName) ?: return null
        return storage.createDraftFromTheme(
            theme = theme,
            editor = captureCurrentEditorPayload(),
            draftName = draftName ?: theme.name,
            sourceThemeName = theme.name
        )
    }

    suspend fun createDraft(
        draftName: String,
        core: ThemeCorePayload,
        editor: ThemeEditorPayload,
        sourceThemeName: String? = null
    ): ThemeDraft {
        return storage.createDraft(
            draftName = draftName,
            core = core,
            editor = editor,
            sourceThemeName = sourceThemeName
        )
    }

    suspend fun saveDraft(draft: ThemeDraft): ThemeDraft {
        return storage.saveDraft(draft)
    }

    suspend fun getDraft(draftId: String): ThemeDraft? {
        return storage.getDraft(draftId)
    }

    suspend fun getAllDrafts(): List<ThemeDraft> {
        return storage.listDrafts()
    }

    suspend fun deleteDraft(draftId: String) {
        storage.deleteDraft(draftId)
    }

    fun captureCurrentEditorPayload(): ThemeEditorPayload {
        val prefs = ThemeManager.prefs
        return ThemeEditorPayload(
            keyBorder = prefs.keyBorder.getValue(),
            keyBorderStroke = prefs.keyBorderStroke.getValue(),
            keyRippleEffect = prefs.keyRippleEffect.getValue(),
            keyHorizontalMargin = prefs.keyHorizontalMargin.getValue(),
            keyHorizontalMarginLandscape = prefs.keyHorizontalMarginLandscape.getValue(),
            keyVerticalMargin = prefs.keyVerticalMargin.getValue(),
            keyVerticalMarginLandscape = prefs.keyVerticalMarginLandscape.getValue(),
            keyRadius = prefs.keyRadius.getValue(),
            textEditingButtonRadius = prefs.textEditingButtonRadius.getValue(),
            clipboardEntryRadius = prefs.clipboardEntryRadius.getValue(),
            punctuationPosition = prefs.punctuationPosition.getValue().name,
            navbarBackground = prefs.navbarBackground.getValue().name
        )
    }

    fun applyEditorPayload(editor: ThemeEditorPayload) {
        val prefs = ThemeManager.prefs
        prefs.keyBorder.setValue(editor.keyBorder)
        prefs.keyBorderStroke.setValue(editor.keyBorderStroke)
        prefs.keyRippleEffect.setValue(editor.keyRippleEffect)
        prefs.keyHorizontalMargin.setValue(editor.keyHorizontalMargin)
        prefs.keyHorizontalMarginLandscape.setValue(editor.keyHorizontalMarginLandscape)
        prefs.keyVerticalMargin.setValue(editor.keyVerticalMargin)
        prefs.keyVerticalMarginLandscape.setValue(editor.keyVerticalMarginLandscape)
        prefs.keyRadius.setValue(editor.keyRadius)
        prefs.textEditingButtonRadius.setValue(editor.textEditingButtonRadius)
        prefs.clipboardEntryRadius.setValue(editor.clipboardEntryRadius)
        prefs.punctuationPosition.setValue(
            decodePunctuationPosition(editor.punctuationPosition)
        )
        prefs.navbarBackground.setValue(
            decodeNavbarBackground(editor.navbarBackground)
        )
    }

    fun buildThemeFromDraft(
        draft: ThemeDraft,
        themeNameOverride: String = draft.core.themeName
    ): Theme.Custom {
        return draft.core.toThemeCustom(themeNameOverride)
    }

    fun saveDraftAsTheme(
        draft: ThemeDraft,
        themeNameOverride: String = draft.core.themeName
    ): String {
        val theme = buildThemeFromDraft(draft, themeNameOverride)
        ThemeManager.saveTheme(theme)
        return theme.name
    }

    private fun decodePunctuationPosition(raw: String): ThemePrefs.PunctuationPosition {
        return runCatching {
            ThemePrefs.PunctuationPosition.valueOf(raw)
        }.getOrDefault(ThemePrefs.PunctuationPosition.Bottom)
    }

    private fun decodeNavbarBackground(raw: String): ThemePrefs.NavbarBackground {
        return runCatching {
            ThemePrefs.NavbarBackground.valueOf(raw)
        }.getOrDefault(ThemePrefs.NavbarBackground.ColorOnly)
    }
}
