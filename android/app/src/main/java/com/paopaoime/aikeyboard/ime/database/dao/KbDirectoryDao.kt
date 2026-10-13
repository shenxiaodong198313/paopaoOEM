package com.paopaoime.aikeyboard.ime.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.paopaoime.aikeyboard.ime.database.BaseDao
import com.paopaoime.aikeyboard.ime.database.entry.KbDirectory

@Dao
interface KbDirectoryDao : BaseDao<KbDirectory> {
    @Query("select * from kb_directory ORDER BY updatedAtMillis DESC")
    fun getAll(): List<KbDirectory>

    @Query("select * from kb_directory where id = :id limit 1")
    fun getById(id: String): KbDirectory?

    @Query("update kb_directory set updatedAtMillis = :updatedAtMillis where id = :id")
    fun updateUpdatedAt(id: String, updatedAtMillis: Long)
}


