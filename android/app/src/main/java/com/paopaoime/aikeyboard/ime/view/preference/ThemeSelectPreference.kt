
package com.paopaoime.aikeyboard.ime.view.preference

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.paopaoime.aikeyboard.ime.data.theme.Theme
import com.paopaoime.aikeyboard.ime.data.theme.ThemeManager

class ThemeSelectPreference(context: Context, private val defaultTheme: Theme) :
    Preference(context) {

    init {
        setDefaultValue(defaultTheme.name)
    }

    private val currentThemeName
        get() = getPersistedString(defaultTheme.name)

    override fun onClick() {
        val allThemes = ThemeManager.getAllThemes()
        val themeNames = allThemes.map { it.name }.toTypedArray()
        var selectedIndex = allThemes.indexOfFirst { it.name == currentThemeName }
            .takeIf { it >= 0 } ?: 0
        AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(themeNames, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selectedName = themeNames.getOrNull(selectedIndex) ?: return@setPositiveButton
                if (callChangeListener(selectedName)) {
                    persistString(selectedName)
                    notifyChanged()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

}
