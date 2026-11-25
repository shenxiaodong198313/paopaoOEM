/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.theme

import android.content.res.Configuration
import android.os.Build
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.ThemeManager.activeTheme
import org.fcitx.fcitx5.android.utils.WeakHashSet
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.isDarkMode

object ThemeManager {

    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: Theme)
    }

    val BuiltinThemes = listOf(
        ThemePreset.MaterialLight,
        ThemePreset.MaterialDark,
        ThemePreset.PixelLight,
        ThemePreset.PixelDark,
        ThemePreset.NordLight,
        ThemePreset.NordDark,
        ThemePreset.DeepBlue,
        ThemePreset.Monokai,
        ThemePreset.AMOLEDBlack,
    )

    val DefaultTheme = BundledSystemThemes.light

    private var monetThemes = listOf(ThemeMonet.getLight(), ThemeMonet.getDark())

    private val customThemes: MutableList<Theme.Custom> by lazy { ThemeFilesManager.listThemes() }

    fun getTheme(name: String) =
        BundledSystemThemes.get(name) ?: customThemes.find { it.name == name } ?: monetThemes.find { it.name == name } ?: BuiltinThemes.find { it.name == name }

    fun getAllThemes() = BundledSystemThemes.all + customThemes + monetThemes + BuiltinThemes

    fun refreshThemes() {
        customThemes.clear()
        customThemes.addAll(ThemeFilesManager.listThemes())
        refreshActiveTheme()
    }

    /** Rights/preferences changed, but installed files did not. Do not rescan every skin. */
    fun refreshActiveTheme() {
        SkinTrialSession.checkOwner()
        activeTheme = evaluateActiveTheme()
    }

    /**
     * [backing property](https://kotlinlang.org/docs/properties.html#backing-properties)
     * of [activeTheme]; holds the [Theme] object currently in use
     */
    @Volatile private lateinit var _activeTheme: Theme
    private lateinit var localConfigurationBase: Theme

    fun refreshLocalConfiguration() {
        if (!::localConfigurationBase.isInitialized) return
        activeTheme = localConfigurationBase
    }

    var activeTheme: Theme
        get() = SkinTrialSession.renderedTheme() ?: _activeTheme
        private set(value) {
            val base = value
            localConfigurationBase = base
            val rendered = KeyboardEffectPreference.decorate(LocalSkinConfiguration.decorate(base))
            if (_activeTheme == rendered) return
            _activeTheme = rendered
            if (SkinTrialSession.renderedTheme() == null) fireChange()
        }

    private var isDarkMode = false

    private val onChangeListeners = WeakHashSet<OnThemeChangeListener>()

    fun addOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        val sound = (activeTheme as? Theme.Custom)?.renderStyle?.localConfiguration?.sound
        org.fcitx.fcitx5.android.data.KeySounds.configureLocalSound(sound?.takeUnless { it == "skin" })
        onChangeListeners.forEach { it.onThemeChange(activeTheme) }
    }

    fun notifyTrialThemeChanged() = fireChange()

    val prefs = AppPrefs.getInstance().registerProvider(::ThemePrefs)

    fun saveTheme(theme: Theme.Custom) {
        require(!SkinTrialSession.isTrial(theme)) { "试用皮肤不能保存为正式皮肤" }
        require(!BundledSystemThemes.contains(theme.name)) { "内置皮肤不可覆盖" }
        ThemeFilesManager.saveThemeFiles(theme)
        customThemes.indexOfFirst { it.name == theme.name }.also {
            if (it >= 0) customThemes[it] = theme else customThemes.add(0, theme)
        }
        if (activeTheme.name == theme.name) {
            activeTheme = theme
        }
    }

    fun deleteTheme(name: String) {
        if (BundledSystemThemes.contains(name)) return
        customThemes.find { it.name == name }?.also {
            ThemeFilesManager.deleteThemeFiles(it)
            customThemes.remove(it)
        }
        if (activeTheme.name == name) {
            activeTheme = evaluateActiveTheme()
        }
    }

    fun setNormalModeTheme(theme: Theme) {
        require(!SkinTrialSession.isTrial(theme)) { "试用皮肤不能设为正式皮肤" }
        // `normalModeTheme.setValue(theme)` would trigger `onThemePrefsChange` listener,
        // which calls `fireChange()`.
        // `activateTheme`'s setter would also trigger `fireChange()` when theme actually changes.
        // write to backing property directly to avoid unnecessary `fireChange()`
        localConfigurationBase = theme
        _activeTheme = KeyboardEffectPreference.decorate(LocalSkinConfiguration.decorate(localConfigurationBase))
        org.fcitx.fcitx5.android.data.KeySounds.configureLocalSound((_activeTheme as? Theme.Custom)?.renderStyle?.localConfiguration?.sound?.takeUnless { it == "skin" })
        prefs.normalModeTheme.setValue(theme)
    }

    private fun evaluateActiveTheme(): Theme {
        return if (prefs.followSystemDayNightTheme.getValue()) {
            if (isDarkMode) prefs.darkModeTheme else prefs.lightModeTheme
        } else {
            prefs.normalModeTheme
        }.getValue()
    }

    @Keep
    private val onThemePrefsChange = ManagedPreferenceProvider.OnChangeListener { key ->
        if (prefs.dayNightModePrefNames.contains(key)) {
            activeTheme = evaluateActiveTheme()
        } else {
            fireChange()
        }
    }

    fun init(configuration: Configuration) {
        isDarkMode = configuration.isDarkMode()
        // fire all `OnThemeChangedListener`s on theme preferences change
        prefs.registerOnChangeListener(onThemePrefsChange)
        localConfigurationBase = evaluateActiveTheme()
        _activeTheme = KeyboardEffectPreference.decorate(LocalSkinConfiguration.decorate(localConfigurationBase))
    }

    fun onSystemPlatteChange(newConfig: Configuration) {
        isDarkMode = newConfig.isDarkMode()
        monetThemes = listOf(ThemeMonet.getLight(), ThemeMonet.getDark())
        // `ManagedThemePreference` resolves the selected name through `getTheme()`
        // thus `evaluateActiveTheme()` should be called after updating `monetThemes`
        activeTheme = evaluateActiveTheme()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun syncToDeviceEncryptedStorage() {
        val ctx = appContext.createDeviceProtectedStorageContext()
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit {
            prefs.managedPreferences.forEach {
                it.value.putValueTo(this@edit)
            }
        }
    }

}