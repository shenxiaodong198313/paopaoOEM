package com.paopaoime.aikeyboard.materialhub.logging

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "ai_reply_task_sessions")
data class AiReplyTaskSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val serverTaskId: String = "",
    val mode: String,
    val tone: String,
    val entry: String,
    val status: String,
    val reasonCode: String,
    val reasonMessage: String,
    val startedAt: Long,
    val finishedAt: Long,
    val uploadStatus: String,
    val uploadRetryCount: Int,
    val lastUploadError: String,
    val summaryJson: String,
    val deviceInfoJson: String,
    val runtimeInfoJson: String,
    val abnormalLog: String,
    val extraDetailJson: String,
    val logFilePath: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface AiReplyTaskSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiReplyTaskSessionEntity)

    @Query("SELECT * FROM ai_reply_task_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun findBySessionId(sessionId: String): AiReplyTaskSessionEntity?

    @Query(
        "SELECT * FROM ai_reply_task_sessions " +
            "WHERE finishedAt > 0 AND uploadStatus != 'uploaded' " +
            "ORDER BY updatedAt ASC LIMIT :limit"
    )
    suspend fun listPending(limit: Int): List<AiReplyTaskSessionEntity>
}

@Database(entities = [AiReplyTaskSessionEntity::class], version = 1, exportSchema = false)
abstract class AiReplyTaskLogDatabase : RoomDatabase() {
    abstract fun sessionDao(): AiReplyTaskSessionDao

    companion object {
        @Volatile
        private var instance: AiReplyTaskLogDatabase? = null

        fun getInstance(context: Context): AiReplyTaskLogDatabase {
            return instance ?: synchronized(this) {
                instance
                    ?: Room.databaseBuilder(
                        context.applicationContext,
                        AiReplyTaskLogDatabase::class.java,
                        "ai_reply_task_log_db",
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                        .also { instance = it }
            }
        }
    }
}

class AiReplyTaskLogStore private constructor(
    private val appContext: Context,
) {
    private val dao = AiReplyTaskLogDatabase.getInstance(appContext).sessionDao()

    companion object {
        @Volatile
        private var instance: AiReplyTaskLogStore? = null

        fun getInstance(context: Context): AiReplyTaskLogStore {
            return instance ?: synchronized(this) {
                instance ?: AiReplyTaskLogStore(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun createSession(
        sessionId: String,
        mode: String,
        tone: String,
        entry: String,
        startedAt: Long,
        deviceInfoJson: String,
    ): AiReplyTaskSessionEntity =
        withContext(Dispatchers.IO) {
            val existing = dao.findBySessionId(sessionId)
            if (existing != null) return@withContext existing
            val logFile = buildLogFile(sessionId, startedAt)
            ensureLogFile(logFile)
            val now = System.currentTimeMillis()
            val entity =
                AiReplyTaskSessionEntity(
                    sessionId = sessionId,
                    mode = mode,
                    tone = tone,
                    entry = entry,
                    status = "running",
                    reasonCode = "",
                    reasonMessage = "",
                    startedAt = startedAt,
                    finishedAt = 0L,
                    uploadStatus = "pending",
                    uploadRetryCount = 0,
                    lastUploadError = "",
                    summaryJson = "{}",
                    deviceInfoJson = deviceInfoJson,
                    runtimeInfoJson = "{}",
                    abnormalLog = "",
                    extraDetailJson = "{}",
                    logFilePath = logFile.absolutePath,
                    createdAt = now,
                    updatedAt = now,
                )
            dao.upsert(entity)
            entity
        }

    suspend fun appendEvent(sessionId: String, event: JSONObject) {
        withContext(Dispatchers.IO) {
            val session = dao.findBySessionId(sessionId) ?: return@withContext
            val logFile = File(session.logFilePath)
            ensureLogFile(logFile)
            logFile.appendText(event.toString() + "\n", Charsets.UTF_8)
        }
    }

    suspend fun updateRuntimeInfo(sessionId: String, runtimeInfoJson: String) {
        updateSession(sessionId) { it.copy(runtimeInfoJson = runtimeInfoJson, updatedAt = System.currentTimeMillis()) }
    }

    suspend fun finishSession(
        sessionId: String,
        status: String,
        reasonCode: String,
        reasonMessage: String,
        summaryJson: String,
        runtimeInfoJson: String,
        abnormalLog: String,
        extraDetailJson: String,
        finishedAt: Long,
    ) {
        updateSession(sessionId) {
            it.copy(
                status = status,
                reasonCode = reasonCode,
                reasonMessage = reasonMessage,
                summaryJson = summaryJson,
                runtimeInfoJson = runtimeInfoJson,
                abnormalLog = abnormalLog,
                extraDetailJson = extraDetailJson,
                finishedAt = finishedAt,
                uploadStatus = "pending",
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun getSession(sessionId: String): AiReplyTaskSessionEntity? =
        withContext(Dispatchers.IO) { dao.findBySessionId(sessionId) }

    suspend fun listPending(limit: Int = 10): List<AiReplyTaskSessionEntity> =
        withContext(Dispatchers.IO) { dao.listPending(limit) }

    suspend fun markUploading(sessionId: String) {
        updateSession(sessionId) { it.copy(uploadStatus = "uploading", updatedAt = System.currentTimeMillis()) }
    }

    suspend fun markUploaded(sessionId: String, serverTaskId: String) {
        updateSession(sessionId) {
            it.copy(
                uploadStatus = "uploaded",
                serverTaskId = serverTaskId,
                lastUploadError = "",
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun markUploadFailed(sessionId: String, error: String) {
        updateSession(sessionId) {
            it.copy(
                uploadStatus = "failed",
                uploadRetryCount = it.uploadRetryCount + 1,
                lastUploadError = error.take(500),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun loadTimeline(session: AiReplyTaskSessionEntity): JSONArray =
        withContext(Dispatchers.IO) {
            val array = JSONArray()
            val logFile = File(session.logFilePath)
            if (!logFile.exists()) return@withContext array
            logFile.forEachLine(Charsets.UTF_8) { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    runCatching { array.put(JSONObject(trimmed)) }
                }
            }
            array
        }

    private suspend fun updateSession(
        sessionId: String,
        transform: (AiReplyTaskSessionEntity) -> AiReplyTaskSessionEntity,
    ) {
        withContext(Dispatchers.IO) {
            val existing = dao.findBySessionId(sessionId) ?: return@withContext
            dao.upsert(transform(existing))
        }
    }

    private fun buildLogFile(sessionId: String, startedAt: Long): File {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(startedAt))
        return File(File(appContext.filesDir, "ai_reply_task_logs/$day"), "$sessionId.jsonl")
    }

    private fun ensureLogFile(file: File) {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.createNewFile()
        }
    }
}
