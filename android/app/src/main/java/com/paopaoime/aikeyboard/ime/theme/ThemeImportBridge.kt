package com.paopaoime.aikeyboard.ime.theme

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.paopaoime.aikeyboard.ime.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.ui.common.withLoadingDialog
import org.fcitx.fcitx5.android.utils.importErrorDialog
import org.fcitx.fcitx5.android.utils.queryFileName

object ThemeImportBridge {

    fun register(
        fragment: Fragment,
        onChanged: (ImeThemeBridge.ThemeSelectionState) -> Unit
    ): Launcher {
        val launcher = fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            val ctx = fragment.requireContext()
            val cr = ctx.contentResolver
            fragment.lifecycleScope.withLoadingDialog(ctx) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val name = cr.queryFileName(uri) ?: return@withContext
                    val ext = name.substringAfterLast('.')
                    if (ext != "zip") {
                        ctx.importErrorDialog(R.string.theme_import_zip_required, ext)
                        return@withContext
                    }
                    try {
                        val inputStream = cr.openInputStream(uri)!!
                        val (_, theme, _) = org.fcitx.fcitx5.android.data.theme.ThemeFilesManager
                            .importTheme(inputStream)
                            .getOrThrow()
                        ThemeManager.refreshThemes()
                        withContext(Dispatchers.Main) {
                            if (!ImeThemeBridge.isFollowSystemDayNightThemeEnabled()) {
                                ThemeManager.setNormalModeTheme(theme)
                                onChanged(ImeThemeBridge.currentSelectionState())
                            } else {
                                onChanged(ImeThemeBridge.currentSelectionState(theme))
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            ctx.importErrorDialog(e)
                        }
                    }
                }
            }
        }
        return Launcher(launcher)
    }

    class Launcher internal constructor(
        private val launcher: ActivityResultLauncher<String>
    ) {
        fun launchZip() {
            launcher.launch("application/zip")
        }
    }
}
