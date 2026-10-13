package com.paopaoime.aikeyboard.ime.database.entry

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_document",
    indices = [
        Index(value = ["directoryId"]),
    ]
)
data class KbDocument(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "directoryId")
    val directoryId: String,
    @ColumnInfo(name = "fileName")
    val fileName: String,
    @ColumnInfo(name = "mimeType")
    val mimeType: String,
    @ColumnInfo(name = "createdAtMillis")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updatedAtMillis")
    val updatedAtMillis: Long,
    @ColumnInfo(name = "parseStatus")
    val parseStatus: Int = 0,
    @ColumnInfo(name = "parseProgress")
    val parseProgress: Int = 0,
    @ColumnInfo(name = "parsedTextPath")
    val parsedTextPath: String? = null,
    @ColumnInfo(name = "parseError")
    val parseError: String? = null,
    @ColumnInfo(name = "parseUpdatedAtMillis")
    val parseUpdatedAtMillis: Long = 0,
    @ColumnInfo(name = "vectorStatus")
    val vectorStatus: Int = 0,
    @ColumnInfo(name = "vectorSize")
    val vectorSize: Long = 0,
    @ColumnInfo(name = "vectorError")
    val vectorError: String? = null,
    @ColumnInfo(name = "vectorUpdatedAtMillis")
    val vectorUpdatedAtMillis: Long = 0,
) {
    companion object {
        const val VECTOR_STATUS_NONE = 0
        const val VECTOR_STATUS_RUNNING = 1
        const val VECTOR_STATUS_DONE = 2
        const val VECTOR_STATUS_FAILED = 3
    }
}

