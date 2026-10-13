package org.fcitx.fcitx5.android.data.theme

import android.util.AtomicFile
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.errorRuntime
import org.fcitx.fcitx5.android.utils.extract
import org.fcitx.fcitx5.android.utils.withTempDir
import timber.log.Timber
import java.io.File
import java.io.FileFilter
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ThemeFilesManager {

    private val dir = File(appContext.getExternalFilesDir(null), "theme").also { it.mkdirs() }

    private fun themeFile(theme: Theme.Custom) = File(dir, theme.name + ".json")

    fun newCustomBackgroundImages(): Triple<String, File, File> {
        val themeName = UUID.randomUUID().toString()
        val croppedImageFile = File(dir, "$themeName-cropped.png")
        val srcImageFile = File(dir, "$themeName-src")
        return Triple(themeName, croppedImageFile, srcImageFile)
    }

    fun saveThemeFiles(theme: Theme.Custom) {
        val target = AtomicFile(themeFile(theme))
        val bytes = Json.encodeToString(CustomThemeSerializer, theme).toByteArray()
        val stream = target.startWrite()
        try { stream.write(bytes); target.finishWrite(stream) }
        catch (error: Throwable) { target.failWrite(stream); throw error }
    }

    fun deleteThemeFiles(theme: Theme.Custom) {
        themeFile(theme).delete()
        theme.allImageAssets().forEach {
            File(it.croppedFilePath).delete()
            File(it.srcFilePath).delete()
        }
    }

    fun listThemes(): MutableList<Theme.Custom> {
        val files = dir.listFiles(FileFilter { it.extension == "json" }) ?: return mutableListOf()
        return files
            .sortedByDescending { it.lastModified() } // newest first
            .mapNotNull decode@{
                val (theme, migrated) = runCatching {
                    Json.decodeFromString(CustomThemeSerializer.WithMigrationStatus, it.readText())
                }.getOrElse { e ->
                    Timber.w("Failed to decode theme file ${it.absolutePath}: ${e.message}")
                    return@decode null
                }
                if (theme.allImageAssets().any {
                        !File(it.croppedFilePath).exists() || !File(it.srcFilePath).exists()
                    }
                ) {
                    Timber.w("Cannot find image assets for theme ${theme.name}")
                    return@decode null
                }
                // Update the saved file if migration happens
                if (migrated) {
                    saveThemeFiles(theme)
                }
                return@decode theme
            }.toMutableList()
    }

    /**
     * [dest] will be closed on finished
     */
    fun exportTheme(theme: Theme.Custom, dest: OutputStream) =
        runCatching {
            ZipOutputStream(dest.buffered()).use { zipStream ->
                // we don't export the internal path of images
                val tweakedTheme = theme.mapImageAssets {
                    it.copy(
                        croppedFilePath = it.croppedFilePath.substringAfterLast('/'),
                        srcFilePath = it.srcFilePath.substringAfterLast('/')
                    )
                }
                val written = mutableMapOf<String, File>()
                theme.allImageAssets().flatMap { listOf(it.croppedFilePath, it.srcFilePath) }
                    .distinct().forEach { imagePath ->
                    val file = File(imagePath)
                    val previous = written[file.name]
                    if (previous != null) {
                        require(previous.readBytes().contentEquals(file.readBytes())) { "Conflicting skin asset filename: ${file.name}" }
                        return@forEach
                    }
                    written[file.name] = file
                    zipStream.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zipStream) }
                    zipStream.closeEntry()
                }
                // write json
                zipStream.putNextEntry(ZipEntry("${tweakedTheme.name}.json"))
                zipStream.write(
                    Json.encodeToString(CustomThemeSerializer, tweakedTheme)
                        .encodeToByteArray()
                )
                // done
                zipStream.closeEntry()
            }
        }

    /**
     * @return (newCreated, theme, migrated)
     */
    fun importTheme(src: InputStream): Result<Triple<Boolean, Theme.Custom, Boolean>> =
        runCatching {
            ZipInputStream(src).use { zipStream ->
                withTempDir { tempDir ->
                    val extracted = zipStream.extract(tempDir)
                    val jsonFile = extracted.find { it.extension == "json" }
                        ?: errorRuntime(R.string.exception_theme_json)
                    val (decoded, migrated) = Json.decodeFromString(
                        CustomThemeSerializer.WithMigrationStatus,
                        jsonFile.readText()
                    )
                    if (BundledSystemThemes.contains(decoded.name) || ThemeManager.BuiltinThemes.find { it.name == decoded.name } != null)
                        errorRuntime(R.string.exception_theme_name_clash)
                    val newCreated = ThemeManager.getTheme(decoded.name) as? Theme.Custom == null
                    val newTheme = decoded.mapImageAssets { asset ->
                        val srcFile = File(dir, asset.srcFilePath)
                        extracted.find { it.name == srcFile.name }
                            ?.copyTo(srcFile, overwrite = true)
                            ?: errorRuntime(R.string.exception_theme_src_image)
                        val croppedFile = File(dir, asset.croppedFilePath)
                        extracted.find { it.name == croppedFile.name }
                            ?.copyTo(croppedFile, overwrite = true)
                            ?: errorRuntime(R.string.exception_theme_cropped_image)
                        asset.copy(
                            croppedFilePath = croppedFile.path,
                            srcFilePath = srcFile.path
                        )
                    }
                    saveThemeFiles(newTheme)
                    Triple(newCreated, newTheme, migrated)
                }
            }
        }

}
