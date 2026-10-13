package com.paopaoime.aikeyboard.ime.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.paopaoime.aikeyboard.ime.database.BaseDao
import com.paopaoime.aikeyboard.ime.database.entry.Clipboard

@Dao
interface ClipboardDao : BaseDao<Clipboard> {

    @Query("select * from clipboard ORDER BY isKeep DESC, time DESC")
    fun getAll(): List<Clipboard>

    @Query("delete from clipboard where content = :content")
    fun deleteByContent(content: String)

    @Query("delete from clipboard")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM clipboard")
    fun getCount(): Int

    @Query("DELETE FROM clipboard WHERE content IN ( SELECT content FROM clipboard ORDER BY time ASC LIMIT :overflow)")
    fun deleteOldest(overflow: Int)
}

