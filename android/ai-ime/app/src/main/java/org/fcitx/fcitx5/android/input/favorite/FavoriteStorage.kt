package org.fcitx.fcitx5.android.input.favorite

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.utils.queryFileName
import java.io.File
import java.util.Locale
import java.util.UUID

class FavoriteStorage(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val rootDir = File(context.filesDir, ROOT_DIR_NAME)
    private val indexFile = File(rootDir, INDEX_FILE_NAME)
    private val assetRootDir = File(rootDir, ASSET_DIR_NAME)

    init {
        rootDir.mkdirs()
        assetRootDir.mkdirs()
    }

    fun listFavorites(): List<FavoriteRecord> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<FavoriteIndex>(indexFile.readText()).items
                .sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun saveFavorite(record: FavoriteRecord): FavoriteRecord {
        val normalized = record.normalizedForSave(updatedAt = System.currentTimeMillis())
        require(normalized.parts.isNotEmpty()) { "favorite parts cannot be empty" }
        val existing = listFavorites().toMutableList()
        val existingIndex = existing.indexOfFirst { it.id == normalized.id }
        if (existingIndex >= 0) {
            existing[existingIndex] = normalized
        } else {
            existing.add(0, normalized)
        }
        writeIndex(existing)
        return normalized
    }

    fun deleteFavorite(recordId: String): Boolean {
        val existing = listFavorites().toMutableList()
        val removed = existing.removeAll { it.id == recordId }
        if (!removed) return false
        writeIndex(existing)
        deleteDraftAssets(recordId)
        return true
    }

    fun createAttachmentFromUri(
        draftId: String,
        uri: Uri,
        pickerType: FavoritePickerType,
    ): FavoritePart.Attachment? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val displayName = context.contentResolver.queryFileName(uri)
            ?.takeIf { it.isNotBlank() }
            ?: "${pickerType.name.lowercase(Locale.ROOT)}_${System.currentTimeMillis()}"
        val extension = displayName.substringAfterLast('.', "")
        val targetName = buildString {
            append(UUID.randomUUID().toString())
            if (extension.isNotBlank()) {
                append('.')
                append(extension.lowercase(Locale.ROOT))
            }
        }
        val itemDir = File(assetRootDir, draftId).apply { mkdirs() }
        val targetFile = File(itemDir, targetName)
        return runCatching {
            inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            FavoritePart.Attachment(
                displayName = displayName,
                storedPath = targetFile.absolutePath,
                mimeType = resolveMimeType(uri, extension, pickerType),
                attachmentType = when (pickerType) {
                    FavoritePickerType.FILE -> FavoriteAttachmentType.DOCUMENT
                    FavoritePickerType.IMAGE -> FavoriteAttachmentType.IMAGE
                    FavoritePickerType.VIDEO -> FavoriteAttachmentType.VIDEO
                    FavoritePickerType.TEXT -> FavoriteAttachmentType.NONE
                },
                pickerType = pickerType,
                fileSizeBytes = targetFile.length()
            )
        }.getOrNull()
    }

    fun createPreviewAttachmentFromUri(
        uri: Uri,
        pickerType: FavoritePickerType,
    ): FavoritePart.Attachment? {
        cleanupPreviewAssets()
        return createAttachmentInternal(
            targetDir = File(assetRootDir, PREVIEW_DIR_NAME).apply { mkdirs() },
            uri = uri,
            pickerType = pickerType
        )
    }

    fun materializeAttachmentForRecord(
        recordId: String,
        attachment: FavoritePart.Attachment,
    ): FavoritePart.Attachment {
        val sourceFile = attachment.file()
        require(sourceFile.exists()) { "附件文件不存在：${attachment.displayName}" }
        val itemDir = File(assetRootDir, recordId).apply { mkdirs() }
        val existingParent = sourceFile.parentFile?.canonicalFile
        val targetParent = itemDir.canonicalFile
        if (existingParent == targetParent) {
            return attachment.copy(fileSizeBytes = sourceFile.length())
        }
        val extension = attachment.displayName.substringAfterLast('.', "")
            .ifBlank { sourceFile.extension }
        val targetName = buildString {
            append(UUID.randomUUID().toString())
            if (extension.isNotBlank()) {
                append('.')
                append(extension.lowercase(Locale.ROOT))
            }
        }
        val targetFile = File(itemDir, targetName)
        sourceFile.inputStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return attachment.copy(
            storedPath = targetFile.absolutePath,
            fileSizeBytes = targetFile.length(),
        )
    }

    fun deleteDraftAssets(draftId: String) {
        File(assetRootDir, draftId).deleteRecursively()
    }

    fun cleanupUnusedAssets(recordId: String, validStoredPaths: Set<String>) {
        val itemDir = File(assetRootDir, recordId)
        if (!itemDir.exists() || !itemDir.isDirectory) return
        itemDir.listFiles()?.forEach { file ->
            if (file.absolutePath !in validStoredPaths) {
                file.deleteRecursively()
            }
        }
    }

    private fun writeIndex(items: List<FavoriteRecord>) {
        indexFile.writeText(json.encodeToString(FavoriteIndex(items.sortedByDescending { it.updatedAt })))
    }

    private fun createAttachmentInternal(
        targetDir: File,
        uri: Uri,
        pickerType: FavoritePickerType,
    ): FavoritePart.Attachment? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val displayName = context.contentResolver.queryFileName(uri)
            ?.takeIf { it.isNotBlank() }
            ?: "${pickerType.name.lowercase(Locale.ROOT)}_${System.currentTimeMillis()}"
        val extension = displayName.substringAfterLast('.', "")
        val targetName = buildString {
            append(UUID.randomUUID().toString())
            if (extension.isNotBlank()) {
                append('.')
                append(extension.lowercase(Locale.ROOT))
            }
        }
        val targetFile = File(targetDir, targetName)
        return runCatching {
            inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            FavoritePart.Attachment(
                displayName = displayName,
                storedPath = targetFile.absolutePath,
                mimeType = resolveMimeType(uri, extension, pickerType),
                attachmentType = when (pickerType) {
                    FavoritePickerType.FILE -> FavoriteAttachmentType.DOCUMENT
                    FavoritePickerType.IMAGE -> FavoriteAttachmentType.IMAGE
                    FavoritePickerType.VIDEO -> FavoriteAttachmentType.VIDEO
                    FavoritePickerType.TEXT -> FavoriteAttachmentType.NONE
                },
                pickerType = pickerType,
                fileSizeBytes = targetFile.length()
            )
        }.getOrNull()
    }

    private fun cleanupPreviewAssets() {
        File(assetRootDir, PREVIEW_DIR_NAME).deleteRecursively()
    }

    private fun resolveMimeType(
        uri: Uri,
        extension: String,
        pickerType: FavoritePickerType,
    ): String {
        val fromResolver = context.contentResolver.getType(uri)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (fromResolver != null &&
            fromResolver != "application/*" &&
            fromResolver != "application/octet-stream"
        ) {
            return fromResolver
        }
        return when (extension.lowercase(Locale.ROOT)) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "rtf" -> "application/rtf"
            "html", "htm" -> "text/html"
            else -> when (pickerType) {
                FavoritePickerType.IMAGE -> "image/*"
                FavoritePickerType.VIDEO -> "video/*"
                FavoritePickerType.FILE -> "application/*"
                FavoritePickerType.TEXT -> "text/plain"
            }
        }
    }

    @Serializable
    private data class FavoriteIndex(
        val items: List<FavoriteRecord>,
    )

    companion object {
        private const val ROOT_DIR_NAME = "favorite_library"
        private const val INDEX_FILE_NAME = "favorites.json"
        private const val ASSET_DIR_NAME = "assets"
        private const val PREVIEW_DIR_NAME = "_preview"
    }
}

object FavoriteComposerSession {
    private val listeners = linkedSetOf<() -> Unit>()
    private var draft: FavoriteComposerDraft? = null
    private var textEditorDraft: FavoriteTextEditorDraft? = null
    private var previewReturnPickerType: FavoritePickerType? = null

    @Synchronized
    fun snapshot(): FavoriteComposerDraft? {
        val current = draft ?: return null
        return current.copy(parts = current.parts.toList())
    }

    @Synchronized
    fun hasDraft(): Boolean = draft != null

    @Synchronized
    fun hasMeaningfulDraft(): Boolean = draft?.hasMeaningfulContent() == true

    @Synchronized
    fun ensureDraft(): FavoriteComposerDraft {
        val current = draft
        if (current != null) return current
        val created = createEmptyFavoriteDraft()
        draft = created
        notifyChangedLocked()
        return created
    }

    @Synchronized
    fun textEditorSnapshot(): FavoriteTextEditorDraft? = textEditorDraft?.copy()

    @Synchronized
    fun isTextEditorActive(): Boolean = textEditorDraft != null

    @Synchronized
    fun setPreviewReturnPickerType(type: FavoritePickerType?) {
        previewReturnPickerType = type
    }

    @Synchronized
    fun peekPreviewReturnPickerType(): FavoritePickerType? = previewReturnPickerType

    @Synchronized
    fun clearPreviewReturnPickerType() {
        previewReturnPickerType = null
    }

    @Synchronized
    fun beginNewDraft(): FavoriteComposerDraft {
        val created = createEmptyFavoriteDraft()
        draft = created
        notifyChangedLocked()
        return created
    }

    @Synchronized
    fun loadFromRecord(record: FavoriteRecord) {
        draft = FavoriteComposerDraft(
            draftId = record.id,
            createdAt = record.createdAt,
            parts = record.parts.toList(),
            activeTextPartId = record.parts.filterIsInstance<FavoritePart.Text>().firstOrNull()?.id
        )
        textEditorDraft = null
        previewReturnPickerType = null
        notifyChangedLocked()
    }

    @Synchronized
    fun addEmptyTextPart() {
        val current = ensureDraft()
        val blankPartId = current.firstBlankTextPartId()
        if (blankPartId != null) {
            if (current.activeTextPartId != blankPartId) {
                draft = current.copy(activeTextPartId = blankPartId)
                notifyChangedLocked()
            }
            return
        }
        val newPart = FavoritePart.Text(text = "")
        draft = current.copy(
            parts = current.parts + newPart,
            activeTextPartId = newPart.id
        )
        notifyChangedLocked()
    }

    @Synchronized
    fun beginNewTextEditor() {
        ensureDraft()
        textEditorDraft = FavoriteTextEditorDraft(
            targetPartId = null,
            originalText = "",
            currentText = "",
            composingText = ""
        )
        notifyChangedLocked()
    }

    @Synchronized
    fun updateTextEditorText(text: String) {
        val editor = textEditorDraft ?: return
        if (editor.currentText == text) return
        textEditorDraft = editor.copy(currentText = text, composingText = "")
    }

    @Synchronized
    fun updateTextEditorComposing(text: String) {
        val editor = textEditorDraft ?: return
        if (editor.composingText == text) return
        textEditorDraft = editor.copy(composingText = text)
        notifyChangedLocked()
    }

    @Synchronized
    fun appendTextEditorText(text: String) {
        if (text.isEmpty()) return
        val editor = textEditorDraft ?: return
        textEditorDraft = editor.copy(
            currentText = editor.currentText + text,
            composingText = ""
        )
        notifyChangedLocked()
    }

    @Synchronized
    fun deleteTextEditorBackward(count: Int = 1) {
        val editor = textEditorDraft ?: return
        if (count <= 0) return
        val updatedComposing = dropLastCodePoints(editor.composingText, count)
        if (updatedComposing != editor.composingText) {
            textEditorDraft = editor.copy(composingText = updatedComposing)
            notifyChangedLocked()
            return
        }
        val updatedText = dropLastCodePoints(editor.currentText, count)
        if (updatedText == editor.currentText) return
        textEditorDraft = editor.copy(currentText = updatedText)
        notifyChangedLocked()
    }

    @Synchronized
    fun beginEditTextPart(partId: String) {
        val current = draft ?: return
        val part = current.parts.filterIsInstance<FavoritePart.Text>().firstOrNull { it.id == partId } ?: return
        textEditorDraft = FavoriteTextEditorDraft(
            targetPartId = part.id,
            originalText = part.text,
            currentText = part.text,
            composingText = ""
        )
        notifyChangedLocked()
    }

    @Synchronized
    fun discardTextEditor() {
        if (textEditorDraft == null) return
        textEditorDraft = null
        notifyChangedLocked()
    }

    @Synchronized
    fun setActiveTextPart(partId: String) {
        val current = draft ?: return
        if (current.parts.none { it is FavoritePart.Text && it.id == partId }) return
        draft = current.copy(activeTextPartId = partId)
        notifyChangedLocked()
    }

    @Synchronized
    fun removePart(partId: String) {
        val current = draft ?: return
        val remaining = current.parts.filterNot { it.id == partId }
        draft = when {
            remaining.isEmpty() -> createEmptyFavoriteDraft()
            else -> {
                val nextActive = when {
                    current.activeTextPartId == partId -> remaining.filterIsInstance<FavoritePart.Text>().firstOrNull()?.id
                    else -> current.activeTextPartId
                }
                current.copy(parts = remaining, activeTextPartId = nextActive)
            }
        }
        notifyChangedLocked()
    }

    @Synchronized
    fun movePart(partId: String, offset: Int) {
        val current = draft ?: return
        val index = current.parts.indexOfFirst { it.id == partId }
        if (index < 0) return
        val targetIndex = (index + offset).coerceIn(0, current.parts.lastIndex)
        if (targetIndex == index) return
        val mutable = current.parts.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(targetIndex, item)
        draft = current.copy(parts = mutable)
        notifyChangedLocked()
    }

    @Synchronized
    fun appendAttachment(attachment: FavoritePart.Attachment) {
        val current = ensureDraft()
        draft = current.copy(parts = current.parts + attachment)
        notifyChangedLocked()
    }

    @Synchronized
    fun saveTextEditor(): FavoriteTextSaveResult? {
        val editor = textEditorDraft ?: return null
        val normalized = (editor.currentText + editor.composingText).trim()
        if (normalized.isBlank()) return null
        val current = ensureDraft()
        val createdNew = editor.targetPartId == null
        val savedId = editor.targetPartId ?: FavoritePart.Text(text = normalized).id
        val updatedParts = if (createdNew) {
            current.parts + FavoritePart.Text(id = savedId, text = normalized)
        } else {
            current.parts.map { part ->
                if (part is FavoritePart.Text && part.id == editor.targetPartId) {
                    part.copy(text = normalized)
                } else {
                    part
                }
            }
        }
        draft = current.copy(parts = updatedParts, activeTextPartId = savedId)
        textEditorDraft = null
        notifyChangedLocked()
        return FavoriteTextSaveResult(partId = savedId, createdNew = createdNew)
    }

    @Synchronized
    fun buildRecordForSave(): FavoriteRecord? {
        val current = draft ?: return null
        val now = System.currentTimeMillis()
        val record = FavoriteRecord(
            id = current.draftId,
            createdAt = current.createdAt,
            updatedAt = now,
            parts = current.parts
        ).normalizedForSave(updatedAt = now)
        return record.takeIf { it.parts.isNotEmpty() }
    }

    @Synchronized
    fun clear() {
        draft = null
        textEditorDraft = null
        previewReturnPickerType = null
        notifyChangedLocked()
    }

    @Synchronized
    fun registerListener(listener: () -> Unit) {
        listeners += listener
    }

    @Synchronized
    fun unregisterListener(listener: () -> Unit) {
        listeners -= listener
    }

    @Synchronized
    private fun notifyChangedLocked() {
        listeners.toList().forEach { it.invoke() }
    }

    private fun dropLastCodePoints(text: String, count: Int): String {
        if (text.isEmpty()) return text
        val targetCount = count.coerceAtLeast(0)
        if (targetCount == 0) return text
        val removable = text.codePointCount(0, text.length).coerceAtMost(targetCount)
        if (removable == 0) return text
        val endIndex = text.offsetByCodePoints(text.length, -removable)
        return text.substring(0, endIndex)
    }
}
