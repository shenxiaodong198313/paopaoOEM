package com.paopaoime.aikeyboard.ime.theme

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeFilesManager
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.ui.common.withLoadingDialog

object ThemePackageBridge {

    fun register(
        fragment: Fragment,
        onExported: (String) -> Unit = {}
    ): Launcher {
        val launcher = fragment.registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri == null) return@registerForActivityResult
            val ctx = fragment.requireContext()
            val themeName = pendingThemeName ?: return@registerForActivityResult
            pendingThemeName = null
            val theme = ThemeManager.getTheme(themeName) as? Theme.Custom ?: run {
                ctx.toast(R.string.skin_theme_editor_export_missing_theme)
                return@registerForActivityResult
            }
            fragment.lifecycleScope.withLoadingDialog(ctx) {
                try {
                    withContext(Dispatchers.IO) {
                        val outputStream = ctx.contentResolver.openOutputStream(uri)!!
                        ThemeFilesManager.exportTheme(theme, outputStream).getOrThrow()
                    }
                    onExported(theme.name)
                } catch (e: Exception) {
                    ctx.toast(e)
                }
            }
        }
        return Launcher(launcher)
    }

    private var pendingThemeName: String? = null

    class Launcher internal constructor(
        private val launcher: ActivityResultLauncher<String>
    ) {
        fun launchExport(themeName: String) {
            pendingThemeName = themeName
            launcher.launch("$themeName.zip")
        }
    }
}
