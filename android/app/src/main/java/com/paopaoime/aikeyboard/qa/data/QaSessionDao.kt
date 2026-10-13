package com.paopaoime.aikeyboard.qa.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QaSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: QaSessionEntity)

    @Query("SELECT * FROM qa_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<QaSessionEntity>>

    @Query("SELECT * FROM qa_sessions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<QaSessionEntity>
    
    @Query("DELETE FROM qa_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
    
    @Query("DELETE FROM qa_sessions")
    suspend fun clearAll()
}
