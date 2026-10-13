package com.paopaoime.aikeyboard.ime.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.paopaoime.aikeyboard.ime.ai.store.LocalAsset
import com.paopaoime.aikeyboard.ime.ai.store.LocalAssetManager
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import java.io.File

data class CreativeSyncTokenState(
    var accessToken: String,
    var refreshToken: String
)

object CreativeAssetCloudSync {
    private const val TAG = "CreativeAssetCloudSync"
    private const val LEGACY_BACKFILL_LIMIT = 12

    fun scheduleLegacyBackfill(context: Context) {
        val appContext = context.applicationContext
        val userId = AuthSessionStore.userId(appContext).trim()
        val accessToken = AuthSessionStore.accessToken(appContext)
        val refreshToken = AuthSessionStore.refreshToken(appContext)
        if (userId.isBlank() || accessToken.isBlank() || refreshToken.isBlank()) return
        val request = OneTimeWorkRequestBuilder<CreativeAssetBackfillWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "creative_asset_backfill_$userId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun applyTokenUpdate(
        tokenState: CreativeSyncTokenState,
        newAccessToken: String?,
        newRefreshToken: String?
    ) {
        if (!newAccessToken.isNullOrBlank()) {
            tokenState.accessToken = newAccessToken
        }
        if (!newRefreshToken.isNullOrBlank()) {
            tokenState.refreshToken = newRefreshToken
        }
    }

    fun syncLegacyLocalAssets(context: Context, maxCount: Int = LEGACY_BACKFILL_LIMIT): Int {
        val appContext = context.applicationContext
        val accessToken = AuthSessionStore.accessToken(appContext)
        val refreshToken = AuthSessionStore.refreshToken(appContext)
        if (accessToken.isBlank() || refreshToken.isBlank()) return 0
        if (!isNetworkAvailable(appContext)) return 0

        val tokenState = CreativeSyncTokenState(accessToken, refreshToken)
        val cloudClientAssetIds = LinkedHashSet<String>()
        val inFlightClientAssetIds = LinkedHashSet<String>()

        val cloudResult = AuthApiClient.listCreativeAssets(
            accessToken = tokenState.accessToken,
            refreshToken = tokenState.refreshToken,
            page = 1,
            pageSize = 200
        )
        applyTokenUpdate(tokenState, cloudResult.newAccessToken, cloudResult.newRefreshToken)
        if (cloudResult.success) {
            cloudResult.items.forEach { item ->
                val clientAssetId = item.clientAssetId.trim()
                if (clientAssetId.isNotBlank()) {
                    cloudClientAssetIds.add(clientAssetId)
                }
            }
        }

        val syncResult = AuthApiClient.listCreativeSyncTasks(
            accessToken = tokenState.accessToken,
            refreshToken = tokenState.refreshToken,
            page = 1,
            pageSize = 200
        )
        applyTokenUpdate(tokenState, syncResult.newAccessToken, syncResult.newRefreshToken)
        if (syncResult.success) {
            syncResult.items.forEach { task ->
                val clientAssetId = task.clientAssetId.trim()
                if (clientAssetId.isBlank()) return@forEach
                when (task.status) {
                    "success" -> cloudClientAssetIds.add(clientAssetId)
                    "pending", "retrying" -> inFlightClientAssetIds.add(clientAssetId)
                }
            }
        }

        val localAssets = LocalAssetManager.getInstance(appContext).getAssets()
            .sortedByDescending { it.createTime }
        var syncedCount = 0
        localAssets
            .asSequence()
            .filter { asset ->
                asset.id.isNotBlank() &&
                    File(asset.localPath).exists() &&
                    !cloudClientAssetIds.contains(asset.id) &&
                    !inFlightClientAssetIds.contains(asset.id)
            }
            .take(maxCount.coerceAtLeast(1))
            .forEach { asset ->
                val synced = runCatching {
                    syncLocalAssetToCloud(
                        context = appContext,
                        asset = asset,
                        taskId = "legacy_backfill"
                    )
                }.getOrElse { error ->
                    Log.e(TAG, "syncLegacyAssetFailed assetId=${asset.id}", error)
                    false
                }
                if (synced) {
                    syncedCount += 1
                }
            }

        persistTokenStateIfChanged(appContext, accessToken, refreshToken, tokenState)
        return syncedCount
    }

    fun syncLocalAssetToCloud(
        context: Context,
        asset: LocalAsset,
        taskId: String,
        tokenState: CreativeSyncTokenState = CreativeSyncTokenState(
            AuthSessionStore.accessToken(context.applicationContext),
            AuthSessionStore.refreshToken(context.applicationContext)
        )
    ): Boolean {
        val appContext = context.applicationContext
        val originalFile = File(asset.localPath)
        if (!originalFile.exists()) return false
        if (tokenState.accessToken.isBlank() || tokenState.refreshToken.isBlank()) return false

        val assetType = if (asset.type == 1) "video" else "image"
        val originalMimeType = if (asset.type == 1) "video/mp4" else "image/png"
        val reportPhase: (String, String, String, String, String, String) -> Unit =
            { phase, status, errorCode, errorMessage, originalObjectKey, coverObjectKey ->
                val report = AuthApiClient.reportCreativeSync(
                    accessToken = tokenState.accessToken,
                    refreshToken = tokenState.refreshToken,
                    clientAssetId = asset.id,
                    assetType = assetType,
                    phase = phase,
                    status = status,
                    title = asset.prompt.take(50),
                    promptText = asset.prompt,
                    modelName = asset.model,
                    width = asset.width,
                    height = asset.height,
                    durationMs = asset.duration,
                    fileSize = originalFile.length(),
                    mimeType = originalMimeType,
                    originalObjectKey = originalObjectKey,
                    coverObjectKey = coverObjectKey,
                    sourceTaskId = taskId,
                    sourceProvider = "liblib",
                    errorCode = errorCode,
                    errorMessage = errorMessage
                )
                applyTokenUpdate(tokenState, report.newAccessToken, report.newRefreshToken)
            }

        reportPhase("submitted", "pending", "", "", "", "")
        val originalSign = AuthApiClient.createCreativeUploadSign(
            accessToken = tokenState.accessToken,
            refreshToken = tokenState.refreshToken,
            assetType = assetType,
            purpose = "original",
            fileName = originalFile.name,
            contentType = originalMimeType
        )
        applyTokenUpdate(tokenState, originalSign.newAccessToken, originalSign.newRefreshToken)
        if (!originalSign.success || originalSign.uploadUrl.isBlank() || originalSign.objectKey.isBlank()) {
            Log.e(TAG, "createCreativeUploadSignFailed assetId=${asset.id} msg=${originalSign.message}")
            reportPhase("oss_upload_failed", "failed", "OSS_SIGN_FAILED", originalSign.message, "", "")
            persistTokenStateIfChanged(appContext, "", "", tokenState)
            return false
        }

        val originalUpload = AuthApiClient.uploadFileBySignedUrl(
            uploadUrl = originalSign.uploadUrl,
            file = originalFile,
            contentType = originalSign.contentType.ifBlank { originalMimeType }
        )
        if (!originalUpload.success) {
            Log.e(TAG, "uploadOriginalFailed assetId=${asset.id} msg=${originalUpload.message}")
            reportPhase("oss_upload_failed", "failed", "OSS_UPLOAD_FAILED", originalUpload.message, "", "")
            persistTokenStateIfChanged(appContext, "", "", tokenState)
            return false
        }

        var coverObjectKey = ""
        if (asset.coverPath.isNotBlank()) {
            val coverFile = File(asset.coverPath)
            if (coverFile.exists()) {
                val coverMimeType = guessImageMimeType(coverFile)
                val coverSign = AuthApiClient.createCreativeUploadSign(
                    accessToken = tokenState.accessToken,
                    refreshToken = tokenState.refreshToken,
                    assetType = assetType,
                    purpose = "cover",
                    fileName = coverFile.name,
                    contentType = coverMimeType
                )
                applyTokenUpdate(tokenState, coverSign.newAccessToken, coverSign.newRefreshToken)
                if (coverSign.success && coverSign.uploadUrl.isNotBlank() && coverSign.objectKey.isNotBlank()) {
                    val coverUpload = AuthApiClient.uploadFileBySignedUrl(
                        uploadUrl = coverSign.uploadUrl,
                        file = coverFile,
                        contentType = coverSign.contentType.ifBlank { coverMimeType }
                    )
                    if (coverUpload.success) {
                        coverObjectKey = coverSign.objectKey
                    }
                }
            }
        }

        reportPhase("oss_uploaded", "pending", "", "", originalSign.objectKey, coverObjectKey)
        val createResult = AuthApiClient.createCreativeAsset(
            accessToken = tokenState.accessToken,
            refreshToken = tokenState.refreshToken,
            clientAssetId = asset.id,
            assetType = assetType,
            title = asset.prompt.take(50),
            promptText = asset.prompt,
            modelName = asset.model,
            width = asset.width,
            height = asset.height,
            durationMs = asset.duration,
            fileSize = originalFile.length(),
            mimeType = originalMimeType,
            originalObjectKey = originalSign.objectKey,
            coverObjectKey = coverObjectKey,
            sourceTaskId = taskId,
            sourceProvider = "liblib",
            status = "ready"
        )
        applyTokenUpdate(tokenState, createResult.newAccessToken, createResult.newRefreshToken)
        if (!createResult.success) {
            Log.e(TAG, "createCreativeAssetFailed assetId=${asset.id} msg=${createResult.message}")
            reportPhase(
                "register_failed",
                "failed",
                "REGISTER_FAILED",
                createResult.message,
                originalSign.objectKey,
                coverObjectKey
            )
            persistTokenStateIfChanged(appContext, "", "", tokenState)
            return false
        }

        reportPhase("ready", "success", "", "", originalSign.objectKey, coverObjectKey)
        persistTokenStateIfChanged(appContext, "", "", tokenState)
        Log.i(TAG, "syncLocalAssetToCloudSuccess assetId=${asset.id} taskId=$taskId")
        return true
    }

    private fun persistTokenStateIfChanged(
        context: Context,
        originalAccessToken: String,
        originalRefreshToken: String,
        tokenState: CreativeSyncTokenState
    ) {
        val baselineAccessToken = originalAccessToken.ifBlank { AuthSessionStore.accessToken(context) }
        val baselineRefreshToken = originalRefreshToken.ifBlank { AuthSessionStore.refreshToken(context) }
        if (tokenState.accessToken != baselineAccessToken || tokenState.refreshToken != baselineRefreshToken) {
            AuthSessionStore.updateTokens(context, tokenState.accessToken, tokenState.refreshToken)
        }
    }

    private fun guessImageMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
