package com.paopaoime.aikeyboard.ime.ui.knowledgebase.parse

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.paopaoime.aikeyboard.ime.database.DataBaseKT
import com.paopaoime.aikeyboard.ime.database.entry.KbDocument
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.KnowledgeBaseUiStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File

class KbVectorizeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID).orEmpty()
        if (documentId.isBlank()) return Result.failure()
        val maxRetries = inputData.getInt(KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES).coerceAtLeast(1)
        val timeoutMillis = inputData.getLong(KEY_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS).coerceAtLeast(1)
        val attempt = runAttemptCount + 1

        val dao = DataBaseKT.instance.kbDocumentDao()
        val doc = dao.getById(documentId) ?: return Result.failure()
        val directoryId = doc.directoryId

        val parsedPath = doc.parsedTextPath?.trim().orEmpty()
        if (parsedPath.isBlank() || !File(parsedPath).exists()) {
            val now = System.currentTimeMillis()
            dao.updateVectorState(documentId, KbDocument.VECTOR_STATUS_FAILED, 0L, "解析结果不存在", now)
            DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)
            return Result.failure()
        }

        dao.updateVectorState(documentId, KbDocument.VECTOR_STATUS_RUNNING, 0L, null, System.currentTimeMillis())

        return runCatching {
            withTimeout(timeoutMillis) {
                if (isStopped) throw CancellationException("任务取消")
                val content = File(parsedPath).readText()
                val kbManager = KnowledgeBaseUiStore.getManager(applicationContext)
                kbManager.removeDocumentVectors(documentId)
                val result = kbManager.addParsedDocument(documentId, content)
                dao.updateVectorState(
                    documentId,
                    KbDocument.VECTOR_STATUS_DONE,
                    result.second,
                    null,
                    System.currentTimeMillis()
                )
                DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, System.currentTimeMillis())
                Result.success()
            }
        }.getOrElse { t ->
            val kbManager = KnowledgeBaseUiStore.getManager(applicationContext)
            runCatching { kbManager.removeDocumentVectors(documentId) }
            val error = when (t) {
                is TimeoutCancellationException -> "向量化超时"
                is CancellationException -> "任务取消"
                else -> t.message?.trim().orEmpty().ifBlank { t.javaClass.simpleName }
            }
            val now = System.currentTimeMillis()
            val shouldRetry = attempt < maxRetries
            if (shouldRetry) {
                Log.w(TAG, "向量化失败，准备重试（$attempt/$maxRetries）：$error")
                dao.updateVectorState(documentId, KbDocument.VECTOR_STATUS_RUNNING, 0L, error, now)
                Result.retry()
            } else {
                Log.e(TAG, "向量化失败，已达最大重试次数（$attempt/$maxRetries）：$error", t)
                dao.updateVectorState(documentId, KbDocument.VECTOR_STATUS_FAILED, 0L, error, now)
                DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)
                Result.failure()
            }
        }
    }

    companion object {
        private const val KEY_DOCUMENT_ID = "document_id"
        private const val KEY_MAX_RETRIES = "max_retries"
        private const val KEY_TIMEOUT_MILLIS = "timeout_millis"
        private const val DEFAULT_MAX_RETRIES = 3
        private const val DEFAULT_TIMEOUT_MILLIS = 5 * 60 * 1000L
        private const val TAG = "KbVectorizeWorker"

        fun inputData(
            documentId: String,
            maxRetries: Int = DEFAULT_MAX_RETRIES,
            timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
        ): Data = Data.Builder()
            .putString(KEY_DOCUMENT_ID, documentId)
            .putInt(KEY_MAX_RETRIES, maxRetries)
            .putLong(KEY_TIMEOUT_MILLIS, timeoutMillis)
            .build()
    }
}

