package com.paopaoime.aikeyboard.ime.theme.draft

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.data.theme.Theme
import java.io.File
import java.util.UUID

class ThemeDraftStorage(private val context: Context) {

    private val draftsRootDir = File(context.filesDir, DRAFTS_DIR_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    init {
        ensureRootDir()
    }

    suspend fun listDrafts(): List<ThemeDraft> = withContext(Dispatchers.IO) {
        loadIndex()
    }

    suspend fun getDraft(id: String): ThemeDraft? = withContext(Dispatchers.IO) {
        loadIndex().find { it.id == id }
    }

    suspend fun saveDraft(draft: ThemeDraft): ThemeDraft = withContext(Dispatchers.IO) {
        ensureRootDir()
        val now = System.currentTimeMillis()
        val normalized = persistDraftAssets(
            draft.copy(
                createdAt = draft.createdAt.takeIf { it > 0 } ?: now,
                lastModifiedAt = now
            )
        )
        val drafts = loadIndex().toMutableList()
        val existingIndex = drafts.indexOfFirst { it.id == normalized.id }
        if (existingIndex >= 0) {
            drafts[existingIndex] = normalized
        } else {
            drafts.add(0, normalized)
        }
        saveIndex(drafts)
        normalized
    }

    suspend fun createDraftFromTheme(
        theme: Theme,
        editor: ThemeEditorPayload,
        draftName: String = theme.name,
        sourceThemeName: String = theme.name
    ): ThemeDraft = withContext(Dispatchers.IO) {
        saveDraft(
            ThemeDraft(
                id = UUID.randomUUID().toString(),
                name = draftName,
                sourceThemeName = sourceThemeName,
                createdAt = System.currentTimeMillis(),
                lastModifiedAt = System.currentTimeMillis(),
                core = theme.toThemeCorePayload(),
                editor = editor
            )
        )
    }

    suspend fun createDraft(
        draftName: String,
        core: ThemeCorePayload,
        editor: ThemeEditorPayload,
        sourceThemeName: String? = null
    ): ThemeDraft = withContext(Dispatchers.IO) {
        saveDraft(
            ThemeDraft(
                id = UUID.randomUUID().toString(),
                name = draftName,
                sourceThemeName = sourceThemeName,
                createdAt = System.currentTimeMillis(),
                lastModifiedAt = System.currentTimeMillis(),
                core = core,
                editor = editor
            )
        )
    }

    suspend fun deleteDraft(id: String) = withContext(Dispatchers.IO) {
        val next = loadIndex().filterNot { it.id == id }
        saveIndex(next)
        draftDir(id).deleteRecursively()
    }

    private fun ensureRootDir() {
        if (!draftsRootDir.exists()) draftsRootDir.mkdirs()
    }

    private fun loadIndex(): List<ThemeDraft> {
        val file = indexFile()
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ThemeDraft>>(file.readText())
                .sortedByDescending { it.lastModifiedAt }
        }.getOrDefault(emptyList())
    }

    private fun saveIndex(drafts: List<ThemeDraft>) {
        val file = indexFile()
        file.writeText(
            json.encodeToString(
                drafts.sortedByDescending { it.lastModifiedAt }
            )
        )
    }

    private fun persistDraftAssets(draft: ThemeDraft): ThemeDraft {
        val dir = draftDir(draft.id).apply { mkdirs() }
        val background = draft.core.backgroundImage?.persist(dir, "background")
        val toolbar = draft.core.toolbarImage?.persist(dir, "toolbar")
        val key = draft.core.keyImage?.persist(dir, "key")
        val altKey = draft.core.altKeyImage?.persist(dir, "alt_key")
        val accentKey = draft.core.accentKeyImage?.persist(dir, "accent_key")
        val shiftKey = draft.core.shiftKeyImage?.persist(dir, "shift")
        val backspaceKey = draft.core.backspaceKeyImage?.persist(dir, "backspace")
        val numberModeKey = draft.core.numberModeKeyImage?.persist(dir, "number_mode")
        val languageKey = draft.core.languageKeyImage?.persist(dir, "language")
        val spaceBar = draft.core.spaceBarImage?.persist(dir, "space")
        val enterKey = draft.core.enterKeyImage?.persist(dir, "enter")
        val normalized = draft.copy(
            core = draft.core.copy(
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
                enterKeyImage = enterKey
            )
        )
        cleanupDraftAssets(
            id = draft.id,
            keepPaths = normalized.core.allAssets().flatMapTo(mutableSetOf()) {
                listOf(it.croppedFilePath, it.srcFilePath)
            }
        )
        return normalized
    }

    private fun ThemeBackgroundPayload.persist(dir: File, prefix: String): ThemeBackgroundPayload {
        val croppedTarget = copyAssetIntoDraftDir(
            sourcePath = croppedFilePath,
            targetFile = File(dir, "${prefix}_cropped${extensionSuffix(croppedFilePath)}")
        )
        val srcTarget = copyAssetIntoDraftDir(
            sourcePath = srcFilePath,
            targetFile = File(dir, "${prefix}_src${extensionSuffix(srcFilePath)}")
        )
        return copy(
            croppedFilePath = croppedTarget,
            srcFilePath = srcTarget
        )
    }

    private fun copyAssetIntoDraftDir(sourcePath: String, targetFile: File): String {
        if (sourcePath.isBlank()) return sourcePath
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists() || !sourceFile.isFile) return sourcePath
        if (sourceFile.absolutePath == targetFile.absolutePath) return targetFile.absolutePath
        targetFile.parentFile?.mkdirs()
        sourceFile.inputStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return targetFile.absolutePath
    }

    private fun cleanupDraftAssets(id: String, keepPaths: Set<String>) {
        val dir = draftDir(id)
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.contains("_")) {
                if (file.absolutePath !in keepPaths) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    private fun ThemeCorePayload.allAssets(): List<ThemeBackgroundPayload> {
        return listOfNotNull(
            backgroundImage,
            toolbarImage,
            keyImage,
            altKeyImage,
            accentKeyImage,
            shiftKeyImage,
            backspaceKeyImage,
            numberModeKeyImage,
            languageKeyImage,
            spaceBarImage,
            enterKeyImage
        )
    }

    private fun draftDir(id: String): File = File(draftsRootDir, id)

    private fun indexFile(): File = File(draftsRootDir, INDEX_FILE_NAME)

    private fun extensionSuffix(path: String): String {
        val extension = File(path).extension
        return if (extension.isBlank()) ".png" else ".$extension"
    }

    companion object {
        private const val DRAFTS_DIR_NAME = "theme_drafts"
        private const val INDEX_FILE_NAME = "index.json"
    }
}
