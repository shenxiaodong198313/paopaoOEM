package com.paopaoime.aikeyboard.ime.theme

import android.os.Build
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeFilesManager
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import java.io.File

object ImeThemeBridge {

    enum class BuiltinThemeChoice {
        Light,
        Dark
    }

    data class ThemeSelectionState(
        val activeThemeName: String,
        val isAddSelected: Boolean,
        val isLightSelected: Boolean,
        val isDarkSelected: Boolean
    )

    data class PaletteThemeSpec(
        val key: String,
        val useDarkKeys: Boolean,
        val brightness: Int = DEFAULT_PALETTE_BRIGHTNESS
    ) {
        val themeName: String
            get() = generatedPaletteThemeName(key)
    }

    private var followSystemDayNightTheme by ThemeManager.prefs.followSystemDayNightTheme

    fun refreshThemes() {
        ThemeManager.refreshThemes()
    }

    fun isFollowSystemDayNightThemeEnabled(): Boolean = followSystemDayNightTheme

    fun setFollowSystemDayNightThemeEnabled(enabled: Boolean) {
        followSystemDayNightTheme = enabled
    }

    fun currentSelectionState(activeTheme: Theme = ThemeManager.activeTheme): ThemeSelectionState {
        val activeThemeName = activeTheme.name
        val lightThemeName = ThemeManager.prefs.lightModeTheme.getValue().name
        val darkThemeName = ThemeManager.prefs.darkModeTheme.getValue().name
        return ThemeSelectionState(
            activeThemeName = activeThemeName,
            isAddSelected = activeThemeName != lightThemeName &&
                activeThemeName != darkThemeName &&
                !ThemeMarketplaceBridge.isManagedThemeName(activeThemeName),
            isLightSelected = activeThemeName == lightThemeName,
            isDarkSelected = activeThemeName == darkThemeName
        )
    }

    fun createOnThemeChangeListener(
        onChanged: (ThemeSelectionState) -> Unit
    ): ThemeManager.OnThemeChangeListener = ThemeManager.OnThemeChangeListener { theme ->
        onChanged(currentSelectionState(theme))
    }

    fun addOnChangedListener(listener: ThemeManager.OnThemeChangeListener) {
        ThemeManager.addOnChangedListener(listener)
    }

    fun removeOnChangedListener(listener: ThemeManager.OnThemeChangeListener) {
        ThemeManager.removeOnChangedListener(listener)
    }

    fun applyBuiltinTheme(choice: BuiltinThemeChoice) {
        applyTheme(
            when (choice) {
                BuiltinThemeChoice.Light -> ThemeManager.prefs.lightModeTheme.getValue()
                BuiltinThemeChoice.Dark -> ThemeManager.prefs.darkModeTheme.getValue()
            }
        )
    }

    fun applyThemeByName(themeName: String) {
        val theme = ThemeManager.getTheme(themeName) ?: return
        applyTheme(theme)
    }

    fun getCustomThemes(): List<Theme.Custom> {
        return ThemeManager.getAllThemes()
            .filterIsInstance<Theme.Custom>()
            .filterNot { ThemeMarketplaceBridge.isManagedThemeName(it.name) || org.fcitx.fcitx5.android.data.theme.BundledSystemThemes.contains(it.name) }
    }

    fun deleteThemeByName(themeName: String) {
        val theme = ThemeManager.getTheme(themeName) as? Theme.Custom ?: return
        ThemeManager.deleteTheme(theme.name)
    }

    suspend fun savePaletteTheme(spec: PaletteThemeSpec, bitmap: Bitmap): String {
        purgeGeneratedPaletteThemes(keepThemeName = spec.themeName)
        val existingTheme = ThemeManager.getTheme(spec.themeName) as? Theme.Custom
        val theme = withContext(Dispatchers.IO) {
            buildPaletteTheme(spec, bitmap, existingTheme)
        }
        ThemeManager.saveTheme(theme)
        return theme.name
    }

    fun generatedPaletteThemeName(key: String): String = "$GENERATED_THEME_PREFIX$key"

    private suspend fun buildPaletteTheme(
        spec: PaletteThemeSpec,
        bitmap: Bitmap,
        existingTheme: Theme.Custom?
    ): Theme.Custom = withContext(Dispatchers.IO) {
        val (croppedFile, srcFile) = existingTheme?.backgroundImage?.let {
            File(it.croppedFilePath) to File(it.srcFilePath)
        } ?: run {
            val (_, croppedImageFile, srcImageBaseFile) = ThemeFilesManager.newCustomBackgroundImages()
            croppedImageFile to File("${srcImageBaseFile.absolutePath}.png")
        }
        croppedFile.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        srcFile.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val template = if (spec.useDarkKeys) {
            ThemePreset.TransparentLight
        } else {
            ThemePreset.TransparentDark
        }
        template.deriveCustomBackground(
            name = spec.themeName,
            croppedBackgroundImage = croppedFile.absolutePath,
            originBackgroundImage = srcFile.absolutePath,
            brightness = spec.brightness
        )
    }

    private fun purgeGeneratedPaletteThemes(keepThemeName: String) {
        ThemeManager.getAllThemes()
            .filterIsInstance<Theme.Custom>()
            .filter { it.name.startsWith(GENERATED_THEME_PREFIX) && it.name != keepThemeName }
            .forEach { ThemeManager.deleteTheme(it.name) }
    }

    private fun applyTheme(theme: Theme) {
        if (followSystemDayNightTheme) {
            followSystemDayNightTheme = false
        }
        ThemeManager.setNormalModeTheme(theme)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            AppPrefs.getInstance().syncToDeviceEncryptedStorage()
            ThemeManager.syncToDeviceEncryptedStorage()
        }
    }

    private const val GENERATED_THEME_PREFIX = "skin_palette_theme_"
    private const val DEFAULT_PALETTE_BRIGHTNESS = 70
}
