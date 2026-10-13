package com.paopaoime.aikeyboard.ime.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.paopaoime.aikeyboard.ime.ai.store.LocalAssetManager
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.ui.activity.MyAssetsActivity
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibClient
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibEnvelope
import com.paopaoime.aikeyboard.ime.ai.liblib.StatusData
import com.paopaoime.aikeyboard.ime.ai.liblib.StatusRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GenerationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "generation_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "GenerationWorker"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString("task_id") ?: return Result.failure()
        val accessKey = inputData.getString("access_key") ?: return Result.failure()
        val secretKey = inputData.getString("secret_key") ?: return Result.failure()
        val baseUrl = inputData.getString("base_url") ?: "https://openapi.liblibai.cloud"
        val prompt = inputData.getString("prompt") ?: ""
        val model = inputData.getString("model") ?: ""
        val type = inputData.getInt("type", 0) // 0: Image, 1: Video
        val width = inputData.getInt("width", 0)
        val height = inputData.getInt("height", 0)
        val duration = inputData.getInt("duration", 0)
        val statusApiUri = inputData.getString("status_api_uri") ?: "/api/generate/webui/status"
        val statusApiIdKey = inputData.getString("status_api_id_key") ?: "generateUuid"

        val client = LiblibClient(accessKey, secretKey, baseUrl)
        
        createNotificationChannel()

        var finished = false
        var success = false
        var successPointsCost = 0
        val localPaths = ArrayList<String>()
        var failReason = ""

        Log.i(TAG, "workStart taskId=$taskId type=$type statusApiUri=$statusApiUri statusApiIdKey=$statusApiIdKey baseUrl=$baseUrl")

        // Poll for up to 10 minutes (200 * 3s = 600s)
        repeat(200) {
            if (finished) return@repeat

            try {
                val resp = withContext(Dispatchers.IO) {
                    val statusRequest = StatusRequest(
                        generateUuid = taskId,
                        generateUid = taskId
                    )
                    client.postJson(
                        statusApiUri,
                        statusRequest,
                        StatusRequest.serializer(),
                        LiblibEnvelope.serializer(StatusData.serializer())
                    )
                }
                
                if (resp.code == 0 && resp.data != null) {
                    val statusData = resp.data
                    Log.i(
                        TAG,
                        "pollStatus taskId=$taskId status=${statusData.generateStatus} msg=${statusData.generateMsg.orEmpty()} images=${statusData.images.size} videos=${statusData.videos.size}"
                    )
                    if (statusData.generateStatus == 5) { // Succeeded
                        success = true
                        finished = true
                        successPointsCost = statusData.pointsCost
                        
                        val assetManager = LocalAssetManager(applicationContext)

                        // Save assets
                        if (type == 1) { // Video
                            statusData.videos.forEach { video ->
                                val asset = assetManager.saveAsset(
                                    client,
                                    video.videoUrl,
                                    1,
                                    prompt,
                                    model,
                                    width,
                                    height,
                                    duration,
                                    video.coverPath
                                )
                                localPaths.add(asset.localPath)
                            }
                        } else { // Image
                            statusData.images.forEach { image ->
                                val asset = assetManager.saveAsset(
                                    client,
                                    image.imageUrl,
                                    0,
                                    prompt,
                                    model,
                                    width,
                                    height,
                                    0
                                )
                                localPaths.add(asset.localPath)
                            }
                        }
                    } else if (statusData.generateStatus == 6 || statusData.generateStatus == 7) { // Failed or Timed out
                        failReason = statusData.generateMsg ?: "generateStatus=${statusData.generateStatus}"
                        Log.e(TAG, "generationFailed taskId=$taskId reason=$failReason")
                        finished = true
                        success = false
                    }
                }
            } catch (e: Exception) {
                failReason = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "pollStatusError taskId=$taskId uri=$statusApiUri reason=$failReason", e)
            }

            if (!finished) {
                delay(3000)
            }
        }
        if (!finished) {
            failReason = "poll_timeout_600s"
            Log.e(TAG, "pollStatusTimeout taskId=$taskId")
        }

        if (success) {
            val accessToken = AuthSessionStore.accessToken(applicationContext)
            val refreshToken = AuthSessionStore.refreshToken(applicationContext)
            if (accessToken.isNotBlank() && refreshToken.isNotBlank()) {
                val tokenState = CreativeSyncTokenState(accessToken, refreshToken)
                runCatching {
                    uploadLiblibTokenUsage(
                        taskId = taskId,
                        pointsCost = successPointsCost,
                        type = type,
                        model = model,
                        tokenState = tokenState
                    )
                }.onFailure { e ->
                    Log.e(TAG, "uploadLiblibTokenUsageFailed taskId=$taskId", e)
                }
                val assets = LocalAssetManager.getInstance(applicationContext).getAssets()
                    .sortedByDescending { it.createTime }
                    .take(localPaths.size)
                assets.forEach { asset ->
                    runCatching {
                        CreativeAssetCloudSync.syncLocalAssetToCloud(
                            context = applicationContext,
                            asset = asset,
                            taskId = taskId,
                            tokenState = tokenState
                        )
                    }
                        .onFailure { e -> Log.e(TAG, "syncAssetToCloudException assetId=${asset.id} taskId=$taskId", e) }
                }
                if (tokenState.accessToken != accessToken || tokenState.refreshToken != refreshToken) {
                    AuthSessionStore.updateTokens(applicationContext, tokenState.accessToken, tokenState.refreshToken)
                }
            }
            showResultNotification(applicationContext.getString(R.string.generation_complete), applicationContext.getString(R.string.click_to_view_result))
            val output = workDataOf(
                "success" to true,
                "local_paths" to localPaths.toTypedArray()
            )
            return Result.success(output)
        } else {
            showResultNotification(applicationContext.getString(R.string.generation_failed), applicationContext.getString(R.string.please_retry))
            return Result.failure(workDataOf("reason" to failReason.ifBlank { "unknown_failure" }))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.generation_status_notification)
            val descriptionText = applicationContext.getString(R.string.notification_generation_progress_result)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showResultNotification(title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(applicationContext, MyAssetsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            try {
                notify(NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    private fun uploadLiblibTokenUsage(
        taskId: String,
        pointsCost: Int,
        type: Int,
        model: String,
        tokenState: CreativeSyncTokenState
    ) {
        val owner = resolveLiblibAccountOwner()
        val event = AuthApiClient.TokenUsageEvent(
            clientEventId = buildLiblibClientEventId(taskId, owner.ownerId),
            provider = "liblib",
            model = model,
            bizType = if (type == 1) "video" else "image",
            bizId = taskId,
            accountOwnerType = owner.ownerType,
            accountOwnerId = owner.ownerId,
            accountOwnerName = owner.ownerName,
            promptTokens = 0,
            completionTokens = 0,
            totalTokens = pointsCost.coerceAtLeast(0),
            pointsCost = pointsCost.coerceAtLeast(0),
            requestId = "",
            occurredAt = formatIsoTimestamp(System.currentTimeMillis())
        )
        val response = AuthApiClient.uploadTokenUsageEvents(
            accessToken = tokenState.accessToken,
            refreshToken = tokenState.refreshToken,
            events = listOf(event)
        )
        if (!response.success) {
            Log.e(TAG, "uploadLiblibTokenUsageRejected taskId=$taskId msg=${response.message}")
        } else {
            Log.i(TAG, "uploadLiblibTokenUsageSuccess taskId=$taskId accepted=${response.acceptedCount} duplicated=${response.duplicatedCount}")
        }
        CreativeAssetCloudSync.applyTokenUpdate(tokenState, response.newAccessToken, response.newRefreshToken)
    }

    private data class AccountOwner(
        val ownerType: String,
        val ownerId: String,
        val ownerName: String
    )

    private fun resolveLiblibAccountOwner(): AccountOwner {
        val prefs = applicationContext.getSharedPreferences("openclaw_settings", Context.MODE_PRIVATE)
        val accessKey = prefs.getString("liblib_access_key", "").orEmpty()
        val baseUrl = prefs.getString("liblib_base_url", "").orEmpty().ifBlank { "https://openapi.liblibai.cloud" }
        val ownerId = sha256("liblib|$baseUrl|$accessKey")
        val host = extractHost(baseUrl).ifBlank { "liblib" }
        val suffix = if (accessKey.length >= 4) accessKey.takeLast(4) else accessKey
        val ownerName = if (suffix.isBlank()) host else "$host·$suffix"
        return AccountOwner("provider_key", ownerId, ownerName)
    }

    private fun extractHost(url: String): String {
        val normalized = url.trim()
        if (normalized.isBlank()) return ""
        val schemeIndex = normalized.indexOf("://")
        val start = if (schemeIndex >= 0) schemeIndex + 3 else 0
        val slashIndex = normalized.indexOf("/", start)
        return if (slashIndex >= 0) normalized.substring(start, slashIndex) else normalized.substring(start)
    }

    private fun buildLiblibClientEventId(taskId: String, ownerId: String): String {
        return sha256("liblib|$ownerId|$taskId")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun formatIsoTimestamp(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(timestamp))
    }
}


