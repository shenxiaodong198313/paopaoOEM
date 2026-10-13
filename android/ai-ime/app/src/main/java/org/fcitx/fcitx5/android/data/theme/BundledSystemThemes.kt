package org.fcitx.fcitx5.android.data.theme

import android.content.SharedPreferences
import android.util.AtomicFile
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.utils.appContext
import java.io.File

/** Immutable, offline system pair. Assets are also available before the first unlock. */
object BundledSystemThemes {
    const val LIGHT = "daylight_porcelain"
    const val DARK = "night_graphite"
    fun contains(name: String) = name == LIGHT || name == DARK
    val light by lazy { load(LIGHT) }
    val dark by lazy { load(DARK) }
    val all get() = listOf(light, dark)

    private fun load(name: String): Theme.Custom {
        val context = appContext.createDeviceProtectedStorageContext()
        val prefix = "system_themes/$name"
        val theme = context.assets.open("$prefix/theme.json").bufferedReader().use {
            Json.decodeFromString(CustomThemeSerializer, it.readText())
        }
        val directory = File(context.filesDir, "system_themes/$name/r${theme.revision}").apply { mkdirs() }
        return theme.mapImageAssets { asset ->
            fun materialize(source: String): String {
                require(source == File(source).name && source !in listOf(".", ".."))
                val file = File(directory, source)
                if (!file.exists()) {
                    val target = AtomicFile(file)
                    val stream = target.startWrite()
                    try {
                        context.assets.open("$prefix/$source").use { it.copyTo(stream) }
                        target.finishWrite(stream)
                    } catch (failure: Throwable) { target.failWrite(stream); throw failure }
                }
                return file.absolutePath
            }
            asset.copy(croppedFilePath = materialize(asset.croppedFilePath), srcFilePath = materialize(asset.srcFilePath))
        }
    }

    /** Migrate the old factory pair once; retain deliberately selected custom themes. */
    fun migratePreferences(prefs: SharedPreferences) {
        val key = "oem_system_theme_pair_v1"
        if (prefs.getBoolean(key, false)) return
        val lightNames = setOf("MaterialLight", "PixelLight")
        val darkNames = setOf("MaterialDark", "PixelDark")
        val normal = prefs.getString("normal_mode_theme", null)
        // Older installations implicitly followed the system when the switch was unset.
        val existingInstallation = prefs.all.isNotEmpty()
        prefs.edit().apply {
            if (prefs.getString("light_mode_theme", null).let { it == null || it in lightNames }) putString("light_mode_theme", LIGHT)
            if (prefs.getString("dark_mode_theme", null).let { it == null || it in darkNames }) putString("dark_mode_theme", DARK)
            when (normal) {
                null, in lightNames -> putString("normal_mode_theme", LIGHT)
                in darkNames -> putString("normal_mode_theme", DARK)
            }
            if (!prefs.contains("follow_system_dark_mode")) putBoolean("follow_system_dark_mode", existingInstallation)
            putBoolean(key, true)
            commit()
        }
    }
}
