package com.paopaoime.aikeyboard.ime.theme

import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.ui.main.settings.theme.CustomThemeActivity

object ThemeEditorBridge {

    enum class Action {
        Created,
        Updated,
        Deleted
    }

    data class Result(
        val action: Action,
        val themeName: String?,
        val selectionState: ImeThemeBridge.ThemeSelectionState
    )

    fun register(
        fragment: Fragment,
        onChanged: (Result) -> Unit
    ): Launcher {
        val launcher = fragment.registerForActivityResult(CustomThemeActivity.Contract()) { result ->
            if (result == null) return@registerForActivityResult
            when (result) {
                is CustomThemeActivity.BackgroundResult.Created -> {
                    ThemeManager.saveTheme(result.theme)
                    val selectionState = if (!ImeThemeBridge.isFollowSystemDayNightThemeEnabled()) {
                        ThemeManager.setNormalModeTheme(result.theme)
                        ImeThemeBridge.currentSelectionState()
                    } else {
                        ImeThemeBridge.currentSelectionState(result.theme)
                    }
                    onChanged(Result(Action.Created, result.theme.name, selectionState))
                }

                is CustomThemeActivity.BackgroundResult.Deleted -> {
                    ThemeManager.deleteTheme(result.name)
                    onChanged(
                        Result(
                            Action.Deleted,
                            result.name,
                            ImeThemeBridge.currentSelectionState()
                        )
                    )
                }

                is CustomThemeActivity.BackgroundResult.Updated -> {
                    ThemeManager.saveTheme(result.theme)
                    onChanged(
                        Result(
                            Action.Updated,
                            result.theme.name,
                            ImeThemeBridge.currentSelectionState(result.theme)
                        )
                    )
                }
            }
        }
        return Launcher(launcher)
    }

    class Launcher internal constructor(
        private val launcher: ActivityResultLauncher<Theme.Custom?>
    ) {
        fun launchNew() {
            launcher.launch(null)
        }

        fun launchExisting(themeName: String) {
            launcher.launch(ThemeManager.getTheme(themeName) as? Theme.Custom)
        }
    }
}
