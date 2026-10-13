package com.paopaoime.aikeyboard.qa.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class QaSessionRepository private constructor(context: Context) {
    private val dao = QaDatabase.getInstance(context).qaSessionDao()

    fun getAllSessions(): Flow<List<QaSessionEntity>> {
        return dao.getAllSessions()
    }

    suspend fun getRecentSessions(limit: Int = 50): List<QaSessionEntity> {
        return dao.getRecentSessions(limit)
    }

    suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    companion object {
        @Volatile
        private var INSTANCE: QaSessionRepository? = null

        fun getInstance(context: Context): QaSessionRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QaSessionRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
