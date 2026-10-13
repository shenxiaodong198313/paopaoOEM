package org.fcitx.fcitx5.android.input.favorite

import android.content.ContentUris
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class FavoriteEmbeddedPickerItem(
    val uri: Uri,
    val pickerType: FavoritePickerType,
    val displayName: String,
    val mimeType: String,
    val subtitle: String,
    val fileCategory: FavoriteFileCategory? = null,
    val sourceLabel: String = "",
    val updatedAtMillis: Long = 0L,
)

internal class FavoriteEmbeddedPickerStore(private val context: Context) {
    fun load(type: FavoritePickerType): List<FavoriteEmbeddedPickerItem> {
        return load(type = type, limit = null, offset = 0)
    }

    fun loadPage(
        type: FavoritePickerType,
        limit: Int,
        offset: Int,
    ): List<FavoriteEmbeddedPickerItem> {
        return load(
            type = type,
            limit = limit.coerceAtLeast(1),
            offset = offset.coerceAtLeast(0),
        )
    }

    private fun load(
        type: FavoritePickerType,
        limit: Int?,
        offset: Int,
    ): List<FavoriteEmbeddedPickerItem> {
        return runCatching {
            when (type) {
                FavoritePickerType.IMAGE -> loadImages(limit, offset)
                FavoritePickerType.VIDEO -> loadVideos(limit, offset)
                FavoritePickerType.FILE -> loadFiles(limit, offset)
                FavoritePickerType.TEXT -> emptyList()
            }
        }.onFailure { error ->
            Log.e(TAG, "load embedded picker failed type=$type limit=$limit offset=$offset", error)
        }.getOrDefault(emptyList())
    }

    private fun loadImages(limit: Int?, offset: Int): List<FavoriteEmbeddedPickerItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return queryItems(
            collection = collection,
            projection = baseProjection(),
            pickerType = FavoritePickerType.IMAGE,
            selection = "${MediaStore.MediaColumns.IS_PENDING}=0",
            limit = limit,
            offset = offset,
        )
    }

    private fun loadVideos(limit: Int?, offset: Int): List<FavoriteEmbeddedPickerItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        return queryItems(
            collection = collection,
            projection = baseProjection(),
            pickerType = FavoritePickerType.VIDEO,
            selection = "${MediaStore.MediaColumns.IS_PENDING}=0",
            limit = limit,
            offset = offset,
        )
    }

    private fun loadFiles(limit: Int?, offset: Int): List<FavoriteEmbeddedPickerItem> {
        val result = linkedMapOf<String, FavoriteEmbeddedPickerItem>()
        val selection = officeFileSelection()
        val selectionArgs = officeFileSelectionArgs()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryItems(
                collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection = fileProjection(),
                pickerType = FavoritePickerType.FILE,
                selection = selection,
                selectionArgs = selectionArgs,
                limit = limit,
                offset = offset,
            ).mapNotNull(::normalizeFileItem)
                .forEach { item -> result.putIfAbsent(item.uri.toString(), item) }
        }
        queryItems(
            collection = MediaStore.Files.getContentUri("external"),
            projection = fileProjection(),
            pickerType = FavoritePickerType.FILE,
            selection = selection,
            selectionArgs = selectionArgs,
            limit = limit,
            offset = offset,
        ).mapNotNull(::normalizeFileItem)
            .forEach { item -> result.putIfAbsent(item.uri.toString(), item) }
        return result.values
            .sortedByDescending { it.updatedAtMillis }
            .toList()
    }

    private fun queryItems(
        collection: Uri,
        projection: Array<String>,
        pickerType: FavoritePickerType,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        limit: Int? = null,
        offset: Int = 0,
    ): List<FavoriteEmbeddedPickerItem> {
        val contentResolver = context.contentResolver
        val result = ArrayList<FavoriteEmbeddedPickerItem>()
        val cursor = if (limit != null) {
            contentResolver.query(
                collection,
                projection,
                buildPagedQueryArgs(
                    selection = selection,
                    selectionArgs = selectionArgs,
                    limit = limit,
                    offset = offset,
                ),
                null,
            )
        } else {
            contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                buildSortOrder(),
            )
        }
        cursor?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val titleIndex = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE)
            val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dateModifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val displayName = cursor.getStringOrNull(nameIndex).orEmpty().ifBlank {
                    cursor.getStringOrNull(titleIndex).orEmpty()
                }.ifBlank {
                    "${pickerType.name.lowercase(Locale.ROOT)}_$id"
                }
                val mimeType = cursor.getStringOrNull(mimeIndex).orEmpty().ifBlank {
                    defaultMimeType(pickerType)
                }
                val sizeBytes = cursor.getLongOrNull(sizeIndex) ?: 0L
                val relativePath = cursor.getStringOrNull(relativePathIndex).orEmpty()
                val dateModifiedMillis = (cursor.getLongOrNull(dateModifiedIndex) ?: 0L) * 1000L
                result += FavoriteEmbeddedPickerItem(
                    uri = ContentUris.withAppendedId(collection, id),
                    pickerType = pickerType,
                    displayName = displayName,
                    mimeType = mimeType,
                    subtitle = buildSubtitle(
                        pickerType = pickerType,
                        updatedAtMillis = dateModifiedMillis,
                        sizeBytes = sizeBytes,
                        relativePath = relativePath
                    ),
                    sourceLabel = sourceLabel(relativePath, collection),
                    updatedAtMillis = dateModifiedMillis,
                    fileCategory = if (pickerType == FavoritePickerType.FILE) {
                        classifyFileCategory(displayName, mimeType)
                    } else {
                        null
                    }
                )
            }
        }
        return result
    }

    private fun buildSortOrder(): String {
        return "${MediaStore.MediaColumns.DATE_ADDED} DESC"
    }

    private fun buildPagedQueryArgs(
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int,
        offset: Int,
    ): Bundle {
        return Bundle().apply {
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_ADDED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit.coerceAtLeast(1))
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset.coerceAtLeast(0))
            selection?.let { putString(ContentResolver.QUERY_ARG_SQL_SELECTION, it) }
            selectionArgs?.let { putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, it) }
        }
    }

    private fun defaultMimeType(type: FavoritePickerType): String {
        return when (type) {
            FavoritePickerType.IMAGE -> "image/*"
            FavoritePickerType.VIDEO -> "video/*"
            FavoritePickerType.FILE -> "application/*"
            FavoritePickerType.TEXT -> "text/plain"
        }
    }

    private fun formatSize(sizeBytes: Long): String {
        if (sizeBytes <= 0L) return ""
        val kb = 1024f
        val mb = kb * 1024f
        val gb = mb * 1024f
        return when {
            sizeBytes >= gb -> String.format(Locale.US, "%.1f GB", sizeBytes / gb)
            sizeBytes >= mb -> String.format(Locale.US, "%.1f MB", sizeBytes / mb)
            sizeBytes >= kb -> String.format(Locale.US, "%.1f KB", sizeBytes / kb)
            else -> "$sizeBytes B"
        }
    }

    companion object {
        private const val TAG = "FavoritePickerStore"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        private val OFFICE_MIME_TYPES = listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-works",
            "application/rtf",
            "text/csv"
        )
        private val OFFICE_EXTENSIONS = listOf(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".csv", ".rtf"
        )
    }

    private fun officeFileSelection(): String {
        val mimePredicates = OFFICE_MIME_TYPES.joinToString(" OR ") {
            "${MediaStore.MediaColumns.MIME_TYPE}=?"
        }
        val namePredicates = OFFICE_EXTENSIONS.joinToString(" OR ") {
            "LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?"
        }
        return "(${MediaStore.MediaColumns.IS_PENDING}=0) AND (($mimePredicates) OR ($namePredicates))"
    }

    private fun officeFileSelectionArgs(): Array<String> {
        return buildList {
            addAll(OFFICE_MIME_TYPES)
            addAll(OFFICE_EXTENSIONS.map { "%$it" })
        }.toTypedArray()
    }

    private fun baseProjection(): Array<String> {
        return arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE
        )
    }

    private fun fileProjection(): Array<String> {
        return arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
        )
    }

    private fun normalizeFileItem(item: FavoriteEmbeddedPickerItem): FavoriteEmbeddedPickerItem? {
        val category = item.fileCategory ?: return null
        if (category == FavoriteFileCategory.ALL) return null
        return item.copy(fileCategory = category)
    }

    private fun classifyFileCategory(
        displayName: String,
        mimeType: String,
    ): FavoriteFileCategory {
        val lowerName = displayName.lowercase(Locale.ROOT)
        val lowerMime = mimeType.lowercase(Locale.ROOT)
        return when {
            lowerMime == "application/pdf" || lowerName.endsWith(".pdf") -> FavoriteFileCategory.PDF
            lowerMime == "application/msword" ||
                lowerMime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                lowerName.endsWith(".doc") ||
                lowerName.endsWith(".docx") -> FavoriteFileCategory.WORD
            lowerMime == "application/vnd.ms-powerpoint" ||
                lowerMime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" ||
                lowerName.endsWith(".ppt") ||
                lowerName.endsWith(".pptx") -> FavoriteFileCategory.PRESENTATION
            lowerMime == "application/vnd.ms-excel" ||
                lowerMime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
                lowerName.endsWith(".xls") ||
                lowerName.endsWith(".xlsx") ||
                lowerName.endsWith(".csv") -> FavoriteFileCategory.SHEET
            lowerMime == "application/rtf" ||
                lowerName.endsWith(".rtf") -> FavoriteFileCategory.WORD
            lowerMime in OFFICE_MIME_TYPES -> FavoriteFileCategory.WORD
            OFFICE_EXTENSIONS.any { lowerName.endsWith(it) } -> FavoriteFileCategory.WORD
            else -> FavoriteFileCategory.ALL
        }
    }

    private fun buildSubtitle(
        pickerType: FavoritePickerType,
        updatedAtMillis: Long,
        sizeBytes: Long,
        relativePath: String,
    ): String {
        val parts = buildList {
            if (updatedAtMillis > 0L) add(formatDate(updatedAtMillis))
            val source = sourceLabel(relativePath, null)
            if (pickerType == FavoritePickerType.FILE && source.isNotBlank()) add(source)
            if (pickerType != FavoritePickerType.FILE) {
                val sizeText = formatSize(sizeBytes)
                if (sizeText.isNotBlank()) add(sizeText)
            }
        }
        return parts.joinToString("  ")
    }

    private fun sourceLabel(relativePath: String, collection: Uri?): String {
        val normalized = relativePath.trim().trim('/').trim('\\')
        if (normalized.isNotBlank()) {
            val top = normalized.split('/', '\\').firstOrNull().orEmpty()
            if (top.isNotBlank()) return "来自 $top"
        }
        return when (collection) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI -> "来自 Download"
            else -> ""
        }
    }

    private fun formatDate(timeMillis: Long): String {
        if (timeMillis <= 0L) return ""
        return "于 ${DATE_FORMAT.format(Date(timeMillis))} 更新"
    }

    private fun android.database.Cursor.getStringOrNull(index: Int): String? {
        if (index < 0 || isNull(index)) return null
        return getString(index)
    }

    private fun android.database.Cursor.getLongOrNull(index: Int): Long? {
        if (index < 0 || isNull(index)) return null
        return getLong(index)
    }

    private fun android.database.Cursor.getIntOrNull(index: Int): Int? {
        if (index < 0 || isNull(index)) return null
        return getInt(index)
    }
}
