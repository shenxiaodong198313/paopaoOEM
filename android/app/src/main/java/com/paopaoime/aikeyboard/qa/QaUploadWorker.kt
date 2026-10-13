package com.paopaoime.aikeyboard.qa

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.qa.data.QaSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class QaUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "QaUploadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repo = QaSessionRepository.getInstance(applicationContext)
        val sessions = repo.getRecentSessions(50)
        
        if (sessions.isEmpty()) {
            return@withContext Result.success()
        }

        // 使用本地或配置好的鉴权逻辑
        val prefs = applicationContext.getSharedPreferences("auth_session", Context.MODE_PRIVATE)
        val token = prefs.getString("access_token", "") ?: ""

        val client = OkHttpClient()
        var cloudBaseUrl: String? = null
        var cloudBaseUrlResolved = false

        var allSuccess = true

        for (session in sessions) {
            try {
                val configuredPolicy = InputQaConfig.QaDataPolicy.parse(session.policyState)
                val policy = InputQaConfig.resolveEffectivePolicy(applicationContext, configuredPolicy)
                val hostSuccess = when (policy) {
                    InputQaConfig.QaDataPolicy.HOST_ONLY,
                    InputQaConfig.QaDataPolicy.DUAL_WRITE -> {
                        OemEnterpriseConfig.dispatchQaSessionToHost(applicationContext, session)
                    }
                    else -> true
                }
                val cloudSuccess = when (policy) {
                    InputQaConfig.QaDataPolicy.CLOUD_ONLY,
                    InputQaConfig.QaDataPolicy.DUAL_WRITE -> {
                        if (!cloudBaseUrlResolved) {
                            cloudBaseUrl = runCatching {
                                OemEnterpriseConfig.resolveApiBaseUrl(applicationContext)
                            }.getOrNull()
                            cloudBaseUrlResolved = true
                        }
                        val baseUrl = cloudBaseUrl
                        if (baseUrl.isNullOrBlank()) {
                            if (policy == InputQaConfig.QaDataPolicy.DUAL_WRITE) {
                                Log.w(
                                    TAG,
                                    "apiBaseUrl 未配置，DUAL_WRITE 跳过云端上传 sessionId=${session.sessionId}"
                                )
                                true
                            } else {
                                Log.w(
                                    TAG,
                                    "apiBaseUrl 未配置，CLOUD_ONLY 等待重试 sessionId=${session.sessionId}"
                                )
                                false
                            }
                        } else {
                            uploadSessionToCloud(
                                client = client,
                                baseUrl = baseUrl,
                                token = token,
                                session = session,
                            )
                        }
                    }
                    else -> true
                }
                val finalSuccess = when (policy) {
                    InputQaConfig.QaDataPolicy.LOCAL_ONLY -> true
                    InputQaConfig.QaDataPolicy.CLOUD_ONLY -> cloudSuccess
                    InputQaConfig.QaDataPolicy.HOST_ONLY -> hostSuccess
                    InputQaConfig.QaDataPolicy.DUAL_WRITE -> hostSuccess && cloudSuccess
                }
                Log.i(
                    TAG,
                    "sessionId=${session.sessionId} package=${session.packageName} policy=$policy hostSuccess=$hostSuccess cloudSuccess=$cloudSuccess finalSuccess=$finalSuccess"
                )
                if (finalSuccess) {
                    repo.deleteSession(session.sessionId)
                } else {
                    allSuccess = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                allSuccess = false
            }
        }

        if (allSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    private fun uploadSessionToCloud(
        client: OkHttpClient,
        baseUrl: String,
        token: String,
        session: com.paopaoime.aikeyboard.qa.data.QaSessionEntity,
    ): Boolean {
        val json = JSONObject().apply {
            put("sessionId", session.sessionId)
            put("packageName", session.packageName)
            put("appName", session.appName)
            put("qaContent", session.qaContent)
            put("inputSource", session.inputSource)
            put("timestamp", session.timestamp)
            put("policyState", session.policyState)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            // OEM 联调统一写入官网演示会话存储，便于网页侧实时刷新查看最近输入会话。
            .url("$baseUrl/api/v1/oem/input-qa")
            .post(requestBody)
        if (token.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            return response.isSuccessful
        }
    }
}
