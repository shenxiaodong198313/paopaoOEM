package com.paopaoime.aikeyboard.ime.theme

import android.content.Context
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.theme.draft.ThemeEditorPayload
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.data.theme.CustomThemeSerializer
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeFilesManager
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.extract
import org.fcitx.fcitx5.android.utils.withTempDir
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

object ThemeMarketplaceBridge {

    private const val MANAGED_THEME_PREFIX = "market_theme_"
    private const val MANAGED_THEME_EDITOR_FILE_NAME = "managed_theme_editor.payload"
    private const val REPORT_TTL_MILLIS = 4 * 60 * 60 * 1000L
    private val packageJson = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ManagedThemeEditorPayloadFile(
        val editor: ThemeEditorPayload
    )

    data class ManagedThemeCard(
        val themeId: String,
        val themeName: String,
        val packageThemeName: String,
        val previewUrl: String,
        val packageUrl: String,
        val sizeKb: Int,
        val activeUsers: Int,
        val updatedAt: String
    ) {
        val internalThemeName: String
            get() = ThemeMarketplaceBridge.internalThemeName(themeId)
    }

    suspend fun listManagedThemes(): List<ManagedThemeCard> = withContext(Dispatchers.IO) {
        val result = AuthApiClient.listManagedThemes()
        if (!result.success) {
            throw IllegalStateException(result.message.ifBlank { "获取后台主题失败" })
        }
        result.items.map {
            ManagedThemeCard(
                themeId = it.themeId,
                themeName = it.themeName,
                packageThemeName = it.packageThemeName,
                previewUrl = it.previewUrl,
                packageUrl = it.packageUrl,
                sizeKb = it.sizeKb,
                activeUsers = it.activeUsers,
                updatedAt = it.updatedAt
            )
        }
    }

    suspend fun applyManagedTheme(
        context: Context,
        item: ManagedThemeCard
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = ThemeManager.getTheme(item.internalThemeName) as? Theme.Custom
            val installedTheme = existing ?: run {
                val download = AuthApiClient.downloadManagedThemePackage(context, item.packageUrl, item.themeId)
                val packageFile = download.file
                    ?: throw IllegalStateException(download.message.ifBlank { "下载主题包失败" })
                if (!download.success) {
                    throw IllegalStateException(download.message.ifBlank { "下载主题包失败" })
                }
                installManagedTheme(item.themeId, packageFile.inputStream())
            }
            ThemeManager.refreshThemes()
            withContext(Dispatchers.Main) {
                ImeThemeBridge.applyThemeByName(installedTheme.name)
                loadManagedThemeEditor(item.themeId)?.let(ThemeDraftBridge::applyEditorPayload)
            }
            reportActiveThemeUsageIfNeeded(context, force = true)
            installedTheme.name
        }
    }

    suspend fun reportActiveThemeUsageIfNeeded(
        context: Context,
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (!OemEnterpriseConfig.shouldReportManagedThemeUsage()) {
            return@withContext
        }
        val themeId = themeIdFromManagedName(ThemeManager.activeTheme.name) ?: return@withContext
        val scope = "managed_theme_report_$themeId"
        if (!force && !AuthSessionStore.isSyncExpired(context, scope, REPORT_TTL_MILLIS)) {
            return@withContext
        }
        val result = AuthApiClient.reportManagedThemeUsage(themeId)
        if (result.success) {
            AuthSessionStore.updateSyncAt(context, scope)
        }
    }

    fun isManagedThemeName(themeName: String): Boolean {
        return themeIdFromManagedName(themeName) != null
    }

    fun themeIdFromManagedName(themeName: String): String? {
        val name = themeName.trim()
        if (!name.startsWith(MANAGED_THEME_PREFIX)) return null
        return name.removePrefix(MANAGED_THEME_PREFIX).ifBlank { null }
    }

    fun internalThemeName(themeId: String): String {
        return "$MANAGED_THEME_PREFIX${themeId.trim()}"
    }

    private fun installManagedTheme(themeId: String, src: InputStream): Theme.Custom {
        ZipInputStream(src).use { zipStream ->
            return withTempDir { tempDir ->
                val extracted = zipStream.extract(tempDir)
                val jsonFile = extracted.find { it.extension == "json" }
                    ?: throw IllegalStateException("主题包缺少 json 文件")
                val (decoded, _) = Json.decodeFromString(
                    CustomThemeSerializer.WithMigrationStatus,
                    jsonFile.readText()
                )
                val editorPayload = extracted.find {
                    it.name.equals(MANAGED_THEME_EDITOR_FILE_NAME, ignoreCase = true)
                }?.let(::decodeManagedThemeEditorPayload)
                val targetName = internalThemeName(themeId)
                val existing = ThemeManager.getTheme(targetName) as? Theme.Custom
                if (existing != null) {
                    ThemeManager.deleteTheme(existing.name)
                }
                val themeDir = File(appContext.getExternalFilesDir(null), "theme").apply { mkdirs() }
                val renamedTheme = decoded.copy(name = targetName).mapImageAssets { asset ->
                    val srcName = asset.srcFilePath.substringAfterLast('/')
                    val croppedName = asset.croppedFilePath.substringAfterLast('/')
                    val targetSrc = File(themeDir, "${themeId}_$srcName")
                    val targetCropped = File(themeDir, "${themeId}_$croppedName")
                    extracted.find { it.name == srcName }
                        ?.copyTo(targetSrc, overwrite = true)
                        ?: throw IllegalStateException("主题包缺少素材文件：$srcName")
                    extracted.find { it.name == croppedName }
                        ?.copyTo(targetCropped, overwrite = true)
                        ?: throw IllegalStateException("主题包缺少素材文件：$croppedName")
                    asset.copy(
                        srcFilePath = targetSrc.absolutePath,
                        croppedFilePath = targetCropped.absolutePath
                    )
                }
                ThemeFilesManager.saveThemeFiles(renamedTheme)
                saveManagedThemeEditor(themeId, editorPayload)
                renamedTheme
            }
        }
    }

    private fun decodeManagedThemeEditorPayload(file: File): ThemeEditorPayload {
        val text = file.readText()
        return runCatching {
            packageJson.decodeFromString<ManagedThemeEditorPayloadFile>(text).editor
        }.recoverCatching {
            packageJson.decodeFromString<ThemeEditorPayload>(text)
        }.getOrElse {
            throw IllegalStateException("托管主题编辑参数无效：${file.name}", it)
        }
    }

    private fun managedThemeEditorFile(themeId: String): File {
        val themeDir = File(appContext.getExternalFilesDir(null), "theme").apply { mkdirs() }
        return File(themeDir, "${themeId}_managed_editor.payload")
    }

    private fun saveManagedThemeEditor(themeId: String, editor: ThemeEditorPayload?) {
        val target = managedThemeEditorFile(themeId)
        if (editor == null) {
            target.delete()
            return
        }
        target.writeText(packageJson.encodeToString(ManagedThemeEditorPayloadFile(editor)))
    }

    private fun loadManagedThemeEditor(themeId: String): ThemeEditorPayload? {
        val target = managedThemeEditorFile(themeId)
        if (!target.exists()) return null
        return runCatching {
            packageJson.decodeFromString<ManagedThemeEditorPayloadFile>(target.readText()).editor
        }.recoverCatching {
            packageJson.decodeFromString<ThemeEditorPayload>(target.readText())
        }.getOrNull()
    }
}
