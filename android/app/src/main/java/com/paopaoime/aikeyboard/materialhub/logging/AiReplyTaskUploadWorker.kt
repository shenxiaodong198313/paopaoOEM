package com.paopaoime.aikeyboard.materialhub.logging

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AiReplyTaskUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "AiReplyTaskUpload"
        private const val INPUT_SESSION_ID = "session_id"

        fun enqueueNow(
            context: Context,
            sessionId: String,
        ) {
            val request =
                OneTimeWorkRequestBuilder<AiReplyTaskUploadWorker>()
                    .setInputData(Data.Builder().putString(INPUT_SESSION_ID, sessionId).build())
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("ai_reply_task_upload_$sessionId", ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            if (!OemEnterpriseConfig.isAiReplyEnabled(applicationContext)) {
                return@withContext Result.success()
            }
            val accessToken = AuthSessionStore.accessToken(applicationContext).trim()
            val refreshToken = AuthSessionStore.refreshToken(applicationContext).trim()
            if (accessToken.isBlank() || refreshToken.isBlank()) {
                val dispatchMode =
                    runCatching {
                        OemEnterpriseConfig.resolveAiReplyDispatchMode(applicationContext)
                    }.getOrNull()
                if (dispatchMode == com.paopaoime.aikeyboard.sdk.core.OemAiReplyDispatchMode.HOST_ONLY) {
                    return@withContext Result.success()
                }
                return@withContext Result.retry()
            }
            val store = AiReplyTaskLogStore.getInstance(applicationContext)
            val sessionId = inputData.getString(INPUT_SESSION_ID).orEmpty()
            val sessions =
                if (sessionId.isNotBlank()) {
                    listOfNotNull(store.getSession(sessionId))
                } else {
                    store.listPending(limit = 10)
                }
            if (sessions.isEmpty()) {
                return@withContext Result.success()
            }
            var allUploaded = true
            sessions.forEach { session ->
                try {
                    store.markUploading(session.sessionId)
                    store.appendEvent(
                        session.sessionId,
                        JSONObject()
                            .put("eventId", "${session.sessionId}_${System.nanoTime()}")
                            .put("sessionId", session.sessionId)
                            .put("timestamp", System.currentTimeMillis())
                            .put("level", "INFO")
                            .put("phase", "upload")
                            .put("event", "upload_started")
                            .put("title", "开始上传任务日志")
                            .put("detail", "uploadStatus=${session.uploadStatus}")
                            .put("data", JSONObject()),
                    )
                    val timeline = store.loadTimeline(session)
                    val result =
                        AuthApiClient.reportAiReplyTask(
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            payload =
                                AuthApiClient.AiReplyTaskReportPayload(
                                    sessionId = session.sessionId,
                                    mode = session.mode,
                                    tone = session.tone,
                                    entry = session.entry,
                                    startedAt = session.startedAt,
                                    finishedAt = session.finishedAt,
                                    finalStatus = session.status,
                                    reasonCode = session.reasonCode,
                                    reasonMessage = session.reasonMessage,
                                    summary = JSONObject(session.summaryJson),
                                    device = JSONObject(session.deviceInfoJson),
                                    runtime = JSONObject(session.runtimeInfoJson),
                                    clientTimeline = timeline,
                                    abnormalLog = session.abnormalLog,
                                    extraDetail = JSONObject(session.extraDetailJson),
                                ),
                        )
                    result.newAccessToken?.takeIf { it.isNotBlank() }?.let { newAccess ->
                        AuthSessionStore.updateTokens(applicationContext, newAccess, result.newRefreshToken)
                    }
                    if (!result.success) {
                        allUploaded = false
                        store.markUploadFailed(session.sessionId, result.message)
                        store.appendEvent(
                            session.sessionId,
                            JSONObject()
                                .put("eventId", "${session.sessionId}_${System.nanoTime()}")
                                .put("sessionId", session.sessionId)
                                .put("timestamp", System.currentTimeMillis())
                                .put("level", "ERROR")
                                .put("phase", "upload")
                                .put("event", "upload_failed")
                                .put("title", "任务日志上传失败")
                                .put("detail", result.message)
                                .put("data", JSONObject().put("authInvalid", result.authInvalid)),
                        )
                    } else {
                        store.markUploaded(session.sessionId, result.taskId)
                        store.appendEvent(
                            session.sessionId,
                            JSONObject()
                                .put("eventId", "${session.sessionId}_${System.nanoTime()}")
                                .put("sessionId", session.sessionId)
                                .put("timestamp", System.currentTimeMillis())
                                .put("level", "INFO")
                                .put("phase", "upload")
                                .put("event", "upload_finished")
                                .put("title", "任务日志上传成功")
                                .put("detail", "taskId=${result.taskId} status=${result.finalStatus}")
                                .put(
                                    "data",
                                    JSONObject()
                                        .put("taskId", result.taskId)
                                        .put("abnormalLogUploaded", result.abnormalLogUploaded),
                                ),
                        )
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "upload failed sessionId=${session.sessionId}", t)
                    allUploaded = false
                    store.markUploadFailed(session.sessionId, t.message ?: t.javaClass.simpleName)
                }
            }
            if (allUploaded) Result.success() else Result.retry()
        }
}
