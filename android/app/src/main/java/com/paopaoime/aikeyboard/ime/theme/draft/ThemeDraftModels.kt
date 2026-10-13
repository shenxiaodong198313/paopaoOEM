package com.paopaoime.aikeyboard.ime.theme.draft

import android.graphics.Rect
import kotlinx.serialization.Serializable
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.utils.RectSerializer

@Serializable
data class ThemeBackgroundPayload(
    val croppedFilePath: String,
    val srcFilePath: String,
    val brightness: Int = 70,
    val cropRect: @Serializable(RectSerializer::class) Rect? = null,
    val cropRotation: Int = 0
)

@Serializable
data class ThemeCorePayload(
    val themeName: String,
    val isDark: Boolean,
    val backgroundColor: Int,
    val barColor: Int,
    val keyboardColor: Int,
    val keyBackgroundColor: Int,
    val keyTextColor: Int,
    val candidateTextColor: Int,
    val candidateLabelColor: Int,
    val candidateCommentColor: Int,
    val altKeyBackgroundColor: Int,
    val altKeyTextColor: Int,
    val accentKeyBackgroundColor: Int,
    val accentKeyTextColor: Int,
    val keyPressHighlightColor: Int,
    val keyShadowColor: Int,
    val popupBackgroundColor: Int,
    val popupTextColor: Int,
    val spaceBarColor: Int,
    val dividerColor: Int,
    val clipboardEntryColor: Int,
    val genericActiveBackgroundColor: Int,
    val genericActiveForegroundColor: Int,
    val backgroundImage: ThemeBackgroundPayload? = null,
    val toolbarImage: ThemeBackgroundPayload? = null,
    val keyImage: ThemeBackgroundPayload? = null,
    val altKeyImage: ThemeBackgroundPayload? = null,
    val accentKeyImage: ThemeBackgroundPayload? = null,
    val shiftKeyImage: ThemeBackgroundPayload? = null,
    val backspaceKeyImage: ThemeBackgroundPayload? = null,
    val numberModeKeyImage: ThemeBackgroundPayload? = null,
    val languageKeyImage: ThemeBackgroundPayload? = null,
    val spaceBarImage: ThemeBackgroundPayload? = null,
    val enterKeyImage: ThemeBackgroundPayload? = null,
    val toolbarTopInsetDp: Int = 0,
    val toolbarHorizontalInsetDp: Int = 0
)

@Serializable
data class ThemeEditorPayload(
    val keyBorder: Boolean,
    val keyBorderStroke: Boolean,
    val keyRippleEffect: Boolean,
    val keyHorizontalMargin: Int,
    val keyHorizontalMarginLandscape: Int,
    val keyVerticalMargin: Int,
    val keyVerticalMarginLandscape: Int,
    val keyRadius: Int,
    val textEditingButtonRadius: Int,
    val clipboardEntryRadius: Int,
    val punctuationPosition: String,
    val navbarBackground: String
)

@Serializable
data class ThemeDraft(
    val id: String,
    val name: String,
    val sourceThemeName: String? = null,
    val createdAt: Long,
    val lastModifiedAt: Long,
    val core: ThemeCorePayload,
    val editor: ThemeEditorPayload
)

fun Theme.toThemeCorePayload(themeNameOverride: String = name): ThemeCorePayload {
    val background = (this as? Theme.Custom)?.backgroundImage?.let {
        ThemeBackgroundPayload(
            croppedFilePath = it.croppedFilePath,
            srcFilePath = it.srcFilePath,
            brightness = it.brightness,
            cropRect = it.cropRect,
            cropRotation = it.cropRotation
        )
    }
    val toolbar = (this as? Theme.Custom)?.toolbarImage?.let {
        ThemeBackgroundPayload(
            croppedFilePath = it.croppedFilePath,
            srcFilePath = it.srcFilePath,
            brightness = it.brightness,
            cropRect = it.cropRect,
            cropRotation = it.cropRotation
        )
    }
    val key = (this as? Theme.Custom)?.keyImage?.let {
        ThemeBackgroundPayload(
            croppedFilePath = it.croppedFilePath,
            srcFilePath = it.srcFilePath,
            brightness = it.brightness,
            cropRect = it.cropRect,
            cropRotation = it.cropRotation
        )
    }
    val altKey = (this as? Theme.Custom)?.altKeyImage?.let {
        ThemeBackgroundPayload(
            croppedFilePath = it.croppedFilePath,
            srcFilePath = it.srcFilePath,
            brightness = it.brightness,
            cropRect = it.cropRect,
            cropRotation = it.cropRotation
        )
    }
    val accentKey = (this as? Theme.Custom)?.accentKeyImage?.let {
        ThemeBackgroundPayload(
            croppedFilePath = it.croppedFilePath,
            srcFilePath = it.srcFilePath,
            brightness = it.brightness,
            cropRect = it.cropRect,
            cropRotation = it.cropRotation
        )
    }
    val shiftKey = (this as? Theme.Custom)?.shiftKeyImage?.let {
        ThemeBackgroundPayload(
            croppedFilePath = it.croppedFilePath,
            srcFilePath = it.srcFilePath,
            brightness = it.brightness,
            cropRect = it.cropRect,
            cropRotation = it.cropRotation
        )
    }
    val backspaceKey = (this as? Theme.Custom)?.backspaceKeyImage?.let {
        ThemeBackgroundPayload(
            croppedFilePath = it.croppedFilePath,
            srcFilePath = it.srcFilePath,
            brightness = it.brightness,
            cropRect = it.cropRect,
            cropRotation = it.cropRotation
        )
    }
    val numberModeKey = (this as? Theme.Custom)?.numberModeKeyImage?.let {
        ThemeBackgroundPayload(
            croppedFilePath = it.croppedFilePath,
            srcFilePath = it.srcFilePath,
            brightness = it.brightness,
            cropRect = it.cropRect,
            cropRotation = it.cropRotation
        )
    }
    val languageKey = (this as? Theme.Custom)?.languageKeyImage?.let {
        ThemeBackgroundPayload(
            croppedFilePath = it.croppedFilePath,
            srcFilePath = it.srcFilePath,
            brightness = it.brightness,
            cropRect = it.cropRect,
            cropRotation = it.cropRotation
        )
    }
    val spaceBar = (this as? Theme.Custom)?.spaceBarImage?.let {
        ThemeBackgroundPayload(
            croppedFilePath = it.croppedFilePath,
            srcFilePath = it.srcFilePath,
            brightness = it.brightness,
            cropRect = it.cropRect,
            cropRotation = it.cropRotation
        )
    }
    val enterKey = (this as? Theme.Custom)?.enterKeyImage?.let {
        ThemeBackgroundPayload(
            croppedFilePath = it.croppedFilePath,
            srcFilePath = it.srcFilePath,
            brightness = it.brightness,
            cropRect = it.cropRect,
            cropRotation = it.cropRotation
        )
    }
    return ThemeCorePayload(
        themeName = themeNameOverride,
        isDark = isDark,
        backgroundColor = backgroundColor,
        barColor = barColor,
        keyboardColor = keyboardColor,
        keyBackgroundColor = keyBackgroundColor,
        keyTextColor = keyTextColor,
        candidateTextColor = candidateTextColor,
        candidateLabelColor = candidateLabelColor,
        candidateCommentColor = candidateCommentColor,
        altKeyBackgroundColor = altKeyBackgroundColor,
        altKeyTextColor = altKeyTextColor,
        accentKeyBackgroundColor = accentKeyBackgroundColor,
        accentKeyTextColor = accentKeyTextColor,
        keyPressHighlightColor = keyPressHighlightColor,
        keyShadowColor = keyShadowColor,
        popupBackgroundColor = popupBackgroundColor,
        popupTextColor = popupTextColor,
        spaceBarColor = spaceBarColor,
        dividerColor = dividerColor,
        clipboardEntryColor = clipboardEntryColor,
        genericActiveBackgroundColor = genericActiveBackgroundColor,
        genericActiveForegroundColor = genericActiveForegroundColor,
        backgroundImage = background,
        toolbarImage = toolbar,
        keyImage = key,
        altKeyImage = altKey,
        accentKeyImage = accentKey,
        shiftKeyImage = shiftKey,
        backspaceKeyImage = backspaceKey,
        numberModeKeyImage = numberModeKey,
        languageKeyImage = languageKey,
        spaceBarImage = spaceBar,
        enterKeyImage = enterKey,
        toolbarTopInsetDp = (this as? Theme.Custom)?.toolbarTopInsetDp ?: 0,
        toolbarHorizontalInsetDp = (this as? Theme.Custom)?.toolbarHorizontalInsetDp ?: 0
    )
}

fun ThemeCorePayload.toThemeCustom(themeNameOverride: String = themeName): Theme.Custom {
    return Theme.Custom(
        name = themeNameOverride,
        isDark = isDark,
        backgroundImage = backgroundImage?.toCustomBackground(),
        toolbarImage = toolbarImage?.toCustomBackground(),
        keyImage = keyImage?.toCustomBackground(),
        altKeyImage = altKeyImage?.toCustomBackground(),
        accentKeyImage = accentKeyImage?.toCustomBackground(),
        shiftKeyImage = shiftKeyImage?.toCustomBackground(),
        backspaceKeyImage = backspaceKeyImage?.toCustomBackground(),
        numberModeKeyImage = numberModeKeyImage?.toCustomBackground(),
        languageKeyImage = languageKeyImage?.toCustomBackground(),
        spaceBarImage = spaceBarImage?.toCustomBackground(),
        enterKeyImage = enterKeyImage?.toCustomBackground(),
        backgroundColor = backgroundColor,
        barColor = barColor,
        keyboardColor = keyboardColor,
        keyBackgroundColor = keyBackgroundColor,
        keyTextColor = keyTextColor,
        candidateTextColor = candidateTextColor,
        candidateLabelColor = candidateLabelColor,
        candidateCommentColor = candidateCommentColor,
        altKeyBackgroundColor = altKeyBackgroundColor,
        altKeyTextColor = altKeyTextColor,
        accentKeyBackgroundColor = accentKeyBackgroundColor,
        accentKeyTextColor = accentKeyTextColor,
        keyPressHighlightColor = keyPressHighlightColor,
        keyShadowColor = keyShadowColor,
        popupBackgroundColor = popupBackgroundColor,
        popupTextColor = popupTextColor,
        spaceBarColor = spaceBarColor,
        dividerColor = dividerColor,
        clipboardEntryColor = clipboardEntryColor,
        genericActiveBackgroundColor = genericActiveBackgroundColor,
        genericActiveForegroundColor = genericActiveForegroundColor,
        toolbarTopInsetDp = toolbarTopInsetDp,
        toolbarHorizontalInsetDp = toolbarHorizontalInsetDp
    )
}

fun ThemeBackgroundPayload.toCustomBackground(): Theme.Custom.CustomBackground {
    return Theme.Custom.CustomBackground(
        croppedFilePath = croppedFilePath,
        srcFilePath = srcFilePath,
        brightness = brightness,
        cropRect = cropRect,
        cropRotation = cropRotation
    )
}
