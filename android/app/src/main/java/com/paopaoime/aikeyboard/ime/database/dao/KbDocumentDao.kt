package com.paopaoime.aikeyboard.ime.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.paopaoime.aikeyboard.ime.database.BaseDao
import com.paopaoime.aikeyboard.ime.database.entry.KbDocument

@Dao
interface KbDocumentDao : BaseDao<KbDocument> {
    @Query("select * from kb_document where directoryId = :directoryId ORDER BY updatedAtMillis DESC")
    fun listByDirectory(directoryId: String): List<KbDocument>

    @Query("select * from kb_document")
    fun getAll(): List<KbDocument>

    @Query("select * from kb_document where id = :id limit 1")
    fun getById(id: String): KbDocument?

    @Query(
        "update kb_document set parseStatus = :status, parseProgress = :progress, parseError = :error, parseUpdatedAtMillis = :updatedAtMillis where id = :id"
    )
    fun updateParseState(id: String, status: Int, progress: Int, error: String?, updatedAtMillis: Long)

    @Query(
        "update kb_document set parsedTextPath = :path where id = :id"
    )
    fun setParsedTextPath(id: String, path: String?)

    @Query("update kb_document set updatedAtMillis = :updatedAtMillis where id = :id")
    fun updateUpdatedAt(id: String, updatedAtMillis: Long)

    @Query("update kb_document set vectorStatus = :status where id = :id")
    fun updateVectorStatus(id: String, status: Int)

    @Query(
        "update kb_document set vectorStatus = :status, vectorSize = :size, vectorError = :error, vectorUpdatedAtMillis = :updatedAtMillis, updatedAtMillis = :updatedAtMillis where id = :id"
    )
    fun updateVectorState(id: String, status: Int, size: Long, error: String?, updatedAtMillis: Long)

    @Query("update kb_document set vectorSize = :size where id = :id")
    fun updateVectorSize(id: String, size: Long)

    @Query("select sum(vectorSize) from kb_document where directoryId = :directoryId")
    fun sumVectorSizeByDirectory(directoryId: String): Long?

    @Query("delete from kb_document where id = :id")
    fun deleteById(id: String)
}

