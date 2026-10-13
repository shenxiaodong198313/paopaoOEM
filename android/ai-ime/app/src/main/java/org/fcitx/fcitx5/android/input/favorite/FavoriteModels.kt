package org.fcitx.fcitx5.android.input.favorite
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

enum class FavoriteCategory(val labelRes: Int) {
    RECENT(org.fcitx.fcitx5.android.R.string.favorite_category_recent),
    FOLDERS(org.fcitx.fcitx5.android.R.string.favorite_category_folders),
    FILES(org.fcitx.fcitx5.android.R.string.favorite_category_files),
    NOTES(org.fcitx.fcitx5.android.R.string.favorite_category_notes),
    LINKS(org.fcitx.fcitx5.android.R.string.favorite_category_links),
    IMAGES(org.fcitx.fcitx5.android.R.string.favorite_category_images),
    VIDEOS(org.fcitx.fcitx5.android.R.string.favorite_category_videos),
}

enum class FavoriteAttachmentType {
    NONE,
    DOCUMENT,
    IMAGE,
    VIDEO,
}

@Serializable
enum class FavoriteRecordSource {
    LOCAL,
    REMOTE,
}

enum class FavoritePickerType {
    TEXT,
    IMAGE,
    FILE,
    VIDEO,
}

enum class FavoriteFileCategory(val label: String) {
    ALL("全部"),
    WORD("文字"),
    PRESENTATION("演示"),
    SHEET("表格"),
    PDF("PDF"),
}

enum class FavoriteNavigationMode {
    TYPE_ONLY,
    TYPE_PRIMARY_CATEGORY_SECONDARY,
    CATEGORY_PRIMARY_TYPE_SECONDARY,
    CATEGORY_ONLY,
}

data class FavoriteNavigationItem(
    val id: String,
    val label: String,
)

@Serializable
data class FavoriteRecord(
    val id: String,
    val directoryName: String = DEFAULT_DIRECTORY_NAME,
    val createdAt: Long,
    val updatedAt: Long,
    val parts: List<FavoritePart>,
    val source: FavoriteRecordSource = FavoriteRecordSource.LOCAL,
    val sourceLabel: String = "",
    val remoteMaterialId: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val tags: List<String> = emptyList(),
) {
    fun matchesCategory(category: FavoriteCategory): Boolean = when (category) {
        FavoriteCategory.RECENT -> true
        FavoriteCategory.FILES -> parts.any {
            it is FavoritePart.Attachment && it.attachmentType == FavoriteAttachmentType.DOCUMENT
        }
        FavoriteCategory.NOTES -> parts.any {
            it is FavoritePart.Text && it.text.isNotBlank()
        }
        FavoriteCategory.LINKS -> parts.any {
            it is FavoritePart.Text && URL_REGEX.containsMatchIn(it.text)
        }
        FavoriteCategory.IMAGES -> parts.any {
            it is FavoritePart.Attachment && it.attachmentType == FavoriteAttachmentType.IMAGE
        }
        FavoriteCategory.VIDEOS -> parts.any {
            it is FavoritePart.Attachment && it.attachmentType == FavoriteAttachmentType.VIDEO
        }
        FavoriteCategory.FOLDERS -> false
    }

    fun primaryAttachmentType(): FavoriteAttachmentType {
        return parts.filterIsInstance<FavoritePart.Attachment>()
            .map { it.attachmentType }
            .firstOrNull() ?: FavoriteAttachmentType.NONE
    }

    fun primaryTextPart(): FavoritePart.Text? {
        return parts.filterIsInstance<FavoritePart.Text>()
            .firstOrNull { it.text.trim().isNotEmpty() }
    }

    fun primaryAttachmentPart(): FavoritePart.Attachment? {
        return parts.filterIsInstance<FavoritePart.Attachment>().firstOrNull()
    }

    fun matchesMaterialCategory(targetCategoryId: String?): Boolean {
        if (targetCategoryId.isNullOrBlank()) return true
        return categoryId?.equals(targetCategoryId, ignoreCase = true) == true
    }

    fun previewText(): String {
        return primaryTextPart()?.text?.trim().orEmpty()
    }

    fun normalizedForSave(updatedAt: Long): FavoriteRecord {
        return copy(
            updatedAt = updatedAt,
            parts = parts.mapNotNull { part ->
                when (part) {
                    is FavoritePart.Text -> {
                        val normalized = part.text.trim()
                        if (normalized.isBlank()) null else part.copy(text = normalized)
                    }
                    is FavoritePart.Attachment -> part
                }
            }
        )
    }

    fun isRemoteManaged(): Boolean = source == FavoriteRecordSource.REMOTE
}

@Serializable
sealed class FavoritePart {
    abstract val id: String

    @Serializable
    @SerialName("text")
    data class Text(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
    ) : FavoritePart()

    @Serializable
    @SerialName("attachment")
    data class Attachment(
        override val id: String = UUID.randomUUID().toString(),
        val displayName: String,
        val storedPath: String,
        val mimeType: String,
        val attachmentType: FavoriteAttachmentType,
        val pickerType: FavoritePickerType,
        val fileSizeBytes: Long = 0L,
        val downloadUrl: String = "",
    ) : FavoritePart() {
        fun file(): File = File(storedPath)
    }
}

data class FavoriteComposerDraft(
    val draftId: String,
    val createdAt: Long,
    val parts: List<FavoritePart>,
    val activeTextPartId: String?,
) {
    fun ensureActiveTextPartId(): String? {
        return activeTextPartId ?: parts.filterIsInstance<FavoritePart.Text>().firstOrNull()?.id
    }

    fun firstBlankTextPartId(): String? {
        return parts.filterIsInstance<FavoritePart.Text>().firstOrNull { it.text.isBlank() }?.id
    }

    fun hasMeaningfulContent(): Boolean {
        return parts.any { part ->
            when (part) {
                is FavoritePart.Text -> part.text.isNotBlank()
                is FavoritePart.Attachment -> true
            }
        }
    }
}

internal fun createEmptyFavoriteDraft(): FavoriteComposerDraft {
    return FavoriteComposerDraft(
        draftId = UUID.randomUUID().toString(),
        createdAt = System.currentTimeMillis(),
        parts = emptyList(),
        activeTextPartId = null
    )
}

data class FavoriteTextEditorDraft(
    val targetPartId: String?,
    val originalText: String,
    val currentText: String,
    val composingText: String = "",
)

data class FavoriteTextSaveResult(
    val partId: String,
    val createdNew: Boolean,
)

data class FavoriteFolderItem(
    val id: String,
    val title: String,
    val materialCount: Int,
    val iconRes: Int,
    val backgroundRes: Int,
)

private const val DEFAULT_DIRECTORY_NAME = "知识库目录"

private val URL_REGEX = Regex("""((https?|ftp)://|www\.)[\w\-]+(\.[\w\-]+)+[/#?]?.*""", RegexOption.IGNORE_CASE)
