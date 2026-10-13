package com.paopaoime.aikeyboard.ime.auth

import android.content.Context
import android.util.Log
import com.paopaoime.aikeyboard.ime.BuildConfig
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

object AuthApiClient {
    private const val TAG = "AuthApiClient"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getResolvedBaseUrl(): String {
        return OemEnterpriseConfig.resolveApiBaseUrl(appContext)
    }

    private val baseUrl: String
        get() = getResolvedBaseUrl()

    data class SmsSendResult(
        val success: Boolean,
        val message: String,
        val debugCode: String?
    )

    data class LoginResult(
        val success: Boolean,
        val message: String,
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val phone: String,
        val nickname: String,
        val avatarUrl: String
    )

    data class RefreshResult(
        val success: Boolean,
        val message: String,
        val accessToken: String,
        val refreshToken: String
    )

    data class AppUpgradeInfo(
        val releaseId: String,
        val displayVersion: String,
        val packageVersionCode: Long,
        val packageVersionName: String,
        val title: String,
        val content: String,
        val downloadUrl: String,
        val isForceUpdate: Boolean
    )

    data class AppUpgradeCheckResult(
        val success: Boolean,
        val hasUpdate: Boolean,
        val message: String,
        val upgrade: AppUpgradeInfo?,
        val authInvalid: Boolean = false
    )

    data class AppUpgradeDownloadResult(
        val success: Boolean,
        val message: String,
        val file: File? = null
    )

    data class UserProfile(
        val userId: String,
        val phoneNumber: String,
        val nickname: String,
        val avatarUrl: String
    )

    data class UserProfileResult(
        val success: Boolean,
        val message: String,
        val profile: UserProfile?,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class InviteSummary(
        val inviteCode: String,
        val inviteUrl: String,
        val inviterRewardAmount: Int,
        val inviteeRewardAmount: Int,
        val totalInvitedUsers: Int,
        val totalInviterReward: Int,
        val hasBoundInviteCode: Boolean,
        val boundInviteCode: String,
        val boundAt: String
    )

    data class InviteSummaryResult(
        val success: Boolean,
        val message: String,
        val summary: InviteSummary?,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class UserDetailProfile(
        val bio: String,
        val tags: List<String>,
        val location: String,
        val age: String,
        val zodiac: String,
        val occupation: String
    )

    data class UserDetailProfileResult(
        val success: Boolean,
        val message: String,
        val profile: UserDetailProfile?,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class PointLedgerItem(
        val ledgerId: String,
        val bizType: String,
        val direction: String,
        val amount: Int,
        val balanceAfter: Int,
        val bizId: String,
        val idempotencyKey: String?,
        val remark: String,
        val createdAt: String
    )

    data class PointSummaryResult(
        val success: Boolean,
        val message: String,
        val balance: Int,
        val frozenBalance: Int,
        val updatedAt: String,
        val latestLedgers: List<PointLedgerItem>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class PointLedgerPageResult(
        val success: Boolean,
        val message: String,
        val page: Int,
        val pageSize: Int,
        val total: Int,
        val items: List<PointLedgerItem>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class TokenUsageEvent(
        val clientEventId: String,
        val provider: String,
        val model: String,
        val bizType: String,
        val bizId: String,
        val accountOwnerType: String,
        val accountOwnerId: String,
        val accountOwnerName: String,
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val pointsCost: Int,
        val requestId: String,
        val occurredAt: String
    )

    data class TokenUsageLedgerItem(
        val id: String,
        val provider: String,
        val model: String,
        val bizType: String,
        val bizId: String,
        val accountOwnerType: String,
        val accountOwnerId: String,
        val accountOwnerName: String,
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val pointsCost: Int,
        val requestId: String,
        val clientEventId: String,
        val occurredAt: String,
        val createdAt: String
    )

    data class TokenUsageBatchResult(
        val success: Boolean,
        val message: String,
        val acceptedCount: Int,
        val duplicatedCount: Int,
        val rejectedCount: Int,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class AiCommunicateRecordPayload(
        val sessionId: String,
        val mode: String,
        val tone: String,
        val promptText: String,
        val ocrContextPreview: String,
        val screenshotDataUrl: String?,
        val screenshotDataUrls: List<String> = emptyList(),
        val screenshotMimeType: String = "image/jpeg",
        val replies: List<String>,
        val modelName: String,
        val provider: String,
    )

    data class AiReplyTaskReportPayload(
        val sessionId: String,
        val mode: String,
        val tone: String,
        val entry: String,
        val startedAt: Long,
        val finishedAt: Long,
        val finalStatus: String,
        val reasonCode: String,
        val reasonMessage: String,
        val summary: JSONObject,
        val device: JSONObject,
        val runtime: JSONObject,
        val clientTimeline: JSONArray,
        val abnormalLog: String,
        val extraDetail: JSONObject,
    )

    data class AiReplyTaskReportResult(
        val success: Boolean,
        val message: String,
        val taskId: String,
        val sessionId: String,
        val finalStatus: String,
        val abnormalLogUploaded: Boolean,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false,
    )

    data class TokenUsageLedgerPageResult(
        val success: Boolean,
        val message: String,
        val page: Int,
        val pageSize: Int,
        val total: Int,
        val items: List<TokenUsageLedgerItem>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class AssetPersona(
        val id: String,
        val name: String,
        val promptText: String,
        val tone: String,
        val constraints: JSONObject,
        val isEnabled: Boolean
    )

    data class PersonaListResult(
        val success: Boolean,
        val message: String,
        val items: List<AssetPersona>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class AssetSkill(
        val id: String,
        val name: String,
        val description: String,
        val params: JSONObject,
        val isEnabled: Boolean,
        val version: Int
    )

    data class SkillListResult(
        val success: Boolean,
        val message: String,
        val items: List<AssetSkill>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class AssetKnowledgeBase(
        val id: String,
        val name: String,
        val visibility: String,
        val status: String,
        val updatedAt: String
    )

    data class KnowledgeBaseListResult(
        val success: Boolean,
        val message: String,
        val items: List<AssetKnowledgeBase>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class AssetKnowledgeDoc(
        val id: String,
        val knowledgeBaseId: String,
        val title: String,
        val objectKey: String,
        val fileSize: Long,
        val mimeType: String,
        val splitStatus: String,
        val vectorStatus: String,
        val updatedAt: String
    )

    data class KnowledgeDocListResult(
        val success: Boolean,
        val message: String,
        val items: List<AssetKnowledgeDoc>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class AssetMemory(
        val id: String,
        val memoryType: String,
        val content: String,
        val weight: Int,
        val expireAt: String?,
        val ref: JSONObject
    )

    data class MemoryListResult(
        val success: Boolean,
        val message: String,
        val items: List<AssetMemory>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class AssetCreation(
        val assetId: String,
        val clientAssetId: String,
        val assetType: String,
        val title: String,
        val promptText: String,
        val modelName: String,
        val width: Int,
        val height: Int,
        val durationMs: Int,
        val fileSize: Long,
        val mimeType: String,
        val originalObjectKey: String,
        val coverObjectKey: String,
        val thumbObjectKey: String,
        val status: String,
        val sourceTaskId: String,
        val sourceProvider: String,
        val createdAt: String,
        val updatedAt: String
    )

    data class CreationListResult(
        val success: Boolean,
        val message: String,
        val items: List<AssetCreation>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class ManagedThemeItem(
        val themeId: String,
        val themeName: String,
        val packageThemeName: String,
        val previewUrl: String,
        val packageUrl: String,
        val sizeKb: Int,
        val activeUsers: Int,
        val updatedAt: String
    )

    data class ManagedThemeListResult(
        val success: Boolean,
        val message: String,
        val items: List<ManagedThemeItem>
    )

    data class CreativeSyncTask(
        val taskId: String,
        val clientAssetId: String,
        val assetId: String,
        val assetType: String,
        val sourceTaskId: String,
        val originalObjectKey: String,
        val phase: String,
        val status: String,
        val retryCount: Int,
        val lastErrorCode: String,
        val lastErrorMessage: String
    )

    data class CreativeSyncTaskListResult(
        val success: Boolean,
        val message: String,
        val items: List<CreativeSyncTask>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class CreationDetailResult(
        val success: Boolean,
        val message: String,
        val item: AssetCreation?,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class CreationDownloadSignResult(
        val success: Boolean,
        val message: String,
        val downloadUrl: String,
        val objectKey: String,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class LiblibCloudConfigResult(
        val success: Boolean,
        val message: String,
        val baseUrl: String,
        val accessKey: String,
        val secretKey: String,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class LiblibModelCatalogItem(
        val id: String,
        val category: String,
        val model: String,
        val displayName: String,
        val intro: String,
        val iconUrl: String,
        val uriPath: String,
        val templateUuid: String,
        val statusApiUri: String,
        val statusIdKey: String,
        val sortOrder: Int,
        val available: Boolean
    )

    data class LiblibModelCatalogResult(
        val success: Boolean,
        val message: String,
        val items: List<LiblibModelCatalogItem>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class CreativeTemplateItem(
        val templateId: String,
        val channelId: String,
        val templateName: String,
        val previewUrl: String,
        val templateType: String,
        val modelName: String,
        val promptText: String,
        val steps: Int,
        val sampler: Int,
        val cfgScale: Double
    )

    data class CreativeTemplateChannel(
        val channelId: String,
        val channelName: String,
        val layoutStyle: String,
        val sortOrder: Int,
        val templates: List<CreativeTemplateItem>
    )

    data class CreativeTemplateCatalogResult(
        val success: Boolean,
        val message: String,
        val items: List<CreativeTemplateChannel>
    )

    data class MidsceneModelCatalogItem(
        val id: String,
        val role: String,
        val provider: String,
        val model: String,
        val displayName: String,
        val intro: String,
        val iconUrl: String,
        val supportsMidscene: Boolean,
        val midsceneScopes: List<String>,
        val midsceneFamily: String,
        val supportsVision: Boolean,
        val supportsPlanning: Boolean,
        val supportsInsight: Boolean
    )

    data class MidsceneModelCatalogResult(
        val success: Boolean,
        val message: String,
        val items: List<MidsceneModelCatalogItem>,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class CloudModelRuntimeConfig(
        val modelName: String,
        val apiKey: String,
        val baseUrl: String
    )

    data class CloudModelRuntimeConfigResult(
        val success: Boolean,
        val message: String,
        val config: CloudModelRuntimeConfig?,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class OssUploadSignResult(
        val success: Boolean,
        val message: String,
        val uploadUrl: String,
        val objectKey: String,
        val contentType: String,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class OssDownloadSignResult(
        val success: Boolean,
        val message: String,
        val downloadUrl: String,
        val objectKey: String,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    data class AssetMutationResult(
        val success: Boolean,
        val message: String,
        val newAccessToken: String?,
        val newRefreshToken: String?,
        val authInvalid: Boolean = false
    )

    private data class Envelope(
        val success: Boolean,
        val data: JSONObject,
        val message: String
    )

    private data class AuthRequestResult(
        val statusCode: Int,
        val envelope: Envelope,
        val newAccessToken: String?,
        val newRefreshToken: String?
    )

    private fun parseMessage(json: JSONObject, fallback: String): String {
        val messageInError = json.optJSONObject("error")?.optString("message").orEmpty()
        if (messageInError.isNotBlank()) return messageInError
        val message = json.optString("message").orEmpty()
        if (message.isNotBlank()) return message
        return fallback
    }

    private fun parseEnvelope(json: JSONObject, fallbackMessage: String): Envelope {
        val hasStandard = json.has("success")
        if (hasStandard) {
            val data = json.optJSONObject("data") ?: JSONObject()
            return Envelope(
                success = json.optBoolean("success", false),
                data = data,
                message = parseMessage(json, fallbackMessage)
            )
        }
        val oldSuccess = json.optBoolean("ok", false)
        return Envelope(
            success = oldSuccess,
            data = json,
            message = parseMessage(json, fallbackMessage)
        )
    }

    private fun safeJsonString(json: JSONObject, key: String): String {
        val raw = json.opt(key)
        if (raw == null || raw == JSONObject.NULL) return ""
        val value = raw.toString().trim()
        if (value.equals("null", ignoreCase = true) || value.equals("undefined", ignoreCase = true)) return ""
        return value
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.optString(i).trim()
            if (value.isNotBlank()) {
                result.add(value)
            }
        }
        return result
    }

    private fun parseCreativeTemplateCatalog(array: JSONArray?): List<CreativeTemplateChannel> {
        if (array == null) return emptyList()
        val result = ArrayList<CreativeTemplateChannel>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val templateArray = item.optJSONArray("templates")
            val templates = ArrayList<CreativeTemplateItem>(templateArray?.length() ?: 0)
            if (templateArray != null) {
                for (index in 0 until templateArray.length()) {
                    val template = templateArray.optJSONObject(index) ?: continue
                    templates.add(
                        CreativeTemplateItem(
                            templateId = safeJsonString(template, "templateId"),
                            channelId = safeJsonString(template, "channelId"),
                            templateName = safeJsonString(template, "templateName"),
                            previewUrl = safeJsonString(template, "previewUrl").let { raw ->
                                if (raw.isBlank()) "" else resolveAbsoluteUrl(raw)
                            },
                            templateType = safeJsonString(template, "templateType"),
                            modelName = safeJsonString(template, "modelName"),
                            promptText = safeJsonString(template, "promptText"),
                            steps = template.optInt("steps", 0),
                            sampler = template.optInt("sampler", 0),
                            cfgScale = template.optDouble("cfgScale", 1.0)
                        )
                    )
                }
            }
            result.add(
                CreativeTemplateChannel(
                    channelId = safeJsonString(item, "channelId"),
                    channelName = safeJsonString(item, "channelName"),
                    layoutStyle = safeJsonString(item, "layoutStyle"),
                    sortOrder = item.optInt("sortOrder", 0),
                    templates = templates
                )
            )
        }
        return result
    }

    private fun parseUserDetailProfile(data: JSONObject): UserDetailProfile {
        val preferences = data.optJSONObject("preferences") ?: JSONObject()
        return UserDetailProfile(
            bio = safeJsonString(data, "bio"),
            tags = parseStringList(data.optJSONArray("tags")),
            location = safeJsonString(preferences, "location"),
            age = safeJsonString(preferences, "age"),
            zodiac = safeJsonString(preferences, "zodiac"),
            occupation = safeJsonString(preferences, "occupation")
        )
    }

    private fun postJson(path: String, body: JSONObject, bearerToken: String? = null): Pair<Int, JSONObject> {
        return requestJson(path = path, method = "POST", body = body, bearerToken = bearerToken)
    }

    private fun getJson(path: String, bearerToken: String): Pair<Int, JSONObject> {
        return requestJson(path = path, method = "GET", body = null, bearerToken = bearerToken)
    }

    private fun putJson(path: String, body: JSONObject, bearerToken: String): Pair<Int, JSONObject> {
        return requestJson(path = path, method = "PUT", body = body, bearerToken = bearerToken)
    }

    private fun deleteJson(path: String, bearerToken: String): Pair<Int, JSONObject> {
        return requestJson(path = path, method = "DELETE", body = null, bearerToken = bearerToken)
    }

    private fun requestJson(
        path: String,
        method: String,
        body: JSONObject?,
        bearerToken: String?
    ): Pair<Int, JSONObject> {
        val attempts = if (method == "GET" || method == "DELETE") 2 else 1
        var lastSocketTimeout: SocketTimeoutException? = null
        var lastIoException: IOException? = null
        repeat(attempts) {
            try {
                val requestBuilder = Request.Builder().url("$baseUrl$path")
                requestBuilder.addHeader("x-device-id", resolveDeviceId())
                requestBuilder.addHeader("x-device-name", resolveDeviceName())
                requestBuilder.addHeader("x-device-brand", resolveDeviceBrand())
                requestBuilder.addHeader("x-device-model", resolveDeviceModel())
                requestBuilder.addHeader("x-device-os", resolveDeviceOs())
                requestBuilder.addHeader("x-device-os-version", resolveDeviceOsVersion())
                if (!bearerToken.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $bearerToken")
                }
                when (method) {
                    "GET" -> requestBuilder.get()
                    "DELETE" -> requestBuilder.delete()
                    "PUT" -> requestBuilder.put((body ?: JSONObject()).toString().toRequestBody(jsonMediaType))
                    else -> requestBuilder.post((body ?: JSONObject()).toString().toRequestBody(jsonMediaType))
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    val json = if (bodyString.isNotBlank()) {
                        parseResponseJson(path, response.code, bodyString)
                    } else {
                        JSONObject()
                    }
                    return response.code to json
                }
            } catch (e: SocketTimeoutException) {
                lastSocketTimeout = e
            } catch (e: IOException) {
                lastIoException = e
            }
        }
        if (lastSocketTimeout != null) throw lastSocketTimeout
        if (lastIoException != null) throw lastIoException
        throw IOException("请求失败")
    }

    private fun parseResponseJson(path: String, statusCode: Int, bodyString: String): JSONObject {
        return try {
            when (val parsed = JSONTokener(bodyString).nextValue()) {
                is JSONObject -> parsed
                is JSONArray -> JSONObject()
                    .put("success", statusCode in 200..299)
                    .put("message", "服务端返回了数组响应")
                    .put("data", JSONObject().put("items", parsed))
                else -> JSONObject()
                    .put("success", false)
                    .put("message", bodyString.take(200))
            }
        } catch (t: Throwable) {
            Log.e(
                TAG,
                "parseResponseJson failed path=$path status=$statusCode preview=${bodyString.take(200)}",
                t
            )
            JSONObject()
                .put("success", false)
                .put("message", "服务端返回了非 JSON 响应")
                .put("data", JSONObject().put("rawBody", bodyString.take(500)))
        }
    }

    private fun resolveAbsoluteUrl(pathOrUrl: String): String {
        val raw = pathOrUrl.trim()
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        return if (raw.startsWith("/")) "$baseUrl$raw" else "$baseUrl/$raw"
    }

    private fun buildUpgradeDownloadUrl(upgrade: AppUpgradeInfo): String {
        if (upgrade.downloadUrl.isNotBlank()) {
            return resolveAbsoluteUrl(upgrade.downloadUrl)
        }
        val encodedReleaseId = URLEncoder.encode(upgrade.releaseId, Charsets.UTF_8.name())
        return "$baseUrl/api/v1/app/android/upgrade-download?releaseId=$encodedReleaseId"
    }

    private fun sanitizeUpgradeApkFileName(upgrade: AppUpgradeInfo): String {
        val raw = "paopao_upgrade_${upgrade.packageVersionCode}_${upgrade.packageVersionName}.apk"
        val sanitized = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (sanitized.endsWith(".apk")) sanitized else "${sanitized}.apk"
    }

    private fun sanitizeApkFileName(rawName: String, fallbackName: String): String {
        val candidate = rawName.trim().ifBlank { fallbackName }
        val sanitized = candidate.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (sanitized.endsWith(".apk")) sanitized else "${sanitized}.apk"
    }

    private fun downloadApkFile(
        context: Context,
        downloadUrl: String,
        targetFileName: String,
        bearerToken: String? = null,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): AppUpgradeDownloadResult {
        return try {
            val targetDir = File(context.filesDir, "app-upgrades").apply {
                if (!exists()) mkdirs()
            }
            targetDir.listFiles()?.forEach { existing ->
                if (existing.isFile && (existing.name.endsWith(".apk") || existing.name.endsWith(".part"))) {
                    existing.delete()
                }
            }
            val targetFile = File(targetDir, sanitizeApkFileName(targetFileName, "downloaded_app.apk"))
            val tempFile = File(targetDir, "${targetFile.name}.part")
            if (tempFile.exists()) tempFile.delete()
            if (targetFile.exists()) targetFile.delete()
            val requestBuilder = Request.Builder().url(downloadUrl).get()
            requestBuilder.addHeader("x-device-id", resolveDeviceId())
            requestBuilder.addHeader("x-device-name", resolveDeviceName())
            requestBuilder.addHeader("x-device-brand", resolveDeviceBrand())
            requestBuilder.addHeader("x-device-model", resolveDeviceModel())
            requestBuilder.addHeader("x-device-os", resolveDeviceOs())
            requestBuilder.addHeader("x-device-os-version", resolveDeviceOsVersion())
            if (!bearerToken.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $bearerToken")
            }
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return AppUpgradeDownloadResult(false, "下载升级包失败(${response.code})", null)
                }
                val body = response.body ?: return AppUpgradeDownloadResult(false, "下载升级包失败，响应体为空", null)
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloadedBytes = 0L
                        while (true) {
                            val readCount = input.read(buffer)
                            if (readCount <= 0) break
                            output.write(buffer, 0, readCount)
                            downloadedBytes += readCount
                            onProgress?.invoke(downloadedBytes, totalBytes)
                        }
                        output.flush()
                    }
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                AppUpgradeDownloadResult(true, "下载完成", targetFile)
            }
        } catch (_: SocketTimeoutException) {
            AppUpgradeDownloadResult(false, "下载升级包超时，请稍后重试", null)
        } catch (error: Exception) {
            AppUpgradeDownloadResult(false, error.message?.takeIf { it.isNotBlank() } ?: "下载升级包失败", null)
        }
    }

    fun downloadAppUpgradeApk(
        context: Context,
        upgrade: AppUpgradeInfo,
        bearerToken: String? = null,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): AppUpgradeDownloadResult {
        return downloadApkFile(
            context = context,
            downloadUrl = buildUpgradeDownloadUrl(upgrade),
            targetFileName = sanitizeUpgradeApkFileName(upgrade),
            bearerToken = bearerToken,
            onProgress = onProgress
        )
    }

    fun downloadManagedThemePackage(
        context: Context,
        packageUrl: String,
        themeId: String
    ): AppUpgradeDownloadResult {
        return try {
            val targetDir = File(context.cacheDir, "managed-themes").apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(targetDir, "managed_theme_${themeId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.zip")
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val downloadResult = downloadFileByUrl(resolveAbsoluteUrl(packageUrl), targetFile)
            if (!downloadResult.success) {
                return AppUpgradeDownloadResult(false, downloadResult.message, null)
            }
            AppUpgradeDownloadResult(true, "下载完成", targetFile)
        } catch (error: Exception) {
            AppUpgradeDownloadResult(false, error.message?.takeIf { it.isNotBlank() } ?: "下载主题包失败", null)
        }
    }

    fun listManagedThemes(): ManagedThemeListResult {
        return try {
            val accessToken = appContext?.let { AuthSessionStore.accessToken(it) }.orEmpty()
            val (statusCode, json) = requestJson(
                path = "/api/v1/app/themes",
                method = "GET",
                body = null,
                bearerToken = accessToken.ifBlank { null }
            )
            val envelope = parseEnvelope(json, "获取主题列表失败")
            if (statusCode in 200..299 && envelope.success) {
                return ManagedThemeListResult(
                    success = true,
                    message = "获取成功",
                    items = parseManagedThemes(envelope.data.optJSONArray("items"))
                )
            }
            ManagedThemeListResult(
                success = false,
                message = envelope.message.ifBlank { "获取主题列表失败" },
                items = emptyList()
            )
        } catch (_: SocketTimeoutException) {
            ManagedThemeListResult(false, "连接服务超时，请检查服务端是否启动", emptyList())
        } catch (_: Exception) {
            ManagedThemeListResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList())
        }
    }

    fun reportManagedThemeUsage(themeId: String): AssetMutationResult {
        val body = JSONObject().put("deviceId", resolveDeviceId())
        return try {
            val context = appContext
            val accessToken = context?.let { AuthSessionStore.accessToken(it) }.orEmpty()
            val refreshToken = context?.let { AuthSessionStore.refreshToken(it) }.orEmpty()
            if (accessToken.isNotBlank()) {
                val response = doAuthorizedRequest(
                    method = "POST",
                    path = "/api/v1/app/themes/$themeId/report-usage",
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    body = body
                )
                return mapMutationResult(response, "上报主题使用失败")
            }
            val (statusCode, json) = requestJson(
                path = "/api/v1/app/themes/$themeId/report-usage",
                method = "POST",
                body = body,
                bearerToken = null
            )
            val envelope = parseEnvelope(json, "上报主题使用失败")
            AssetMutationResult(
                success = statusCode in 200..299 && envelope.success,
                message = envelope.message.ifBlank { "上报主题使用失败" },
                newAccessToken = null,
                newRefreshToken = null,
                authInvalid = statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    private fun resolveDeviceId(): String {
        val context = appContext ?: return "unknown-device"
        return DeviceIdentityManager.resolveDeviceId(context)
    }

    private fun resolveDeviceName(): String {
        return DeviceIdentityManager.resolveDeviceName()
    }

    private fun resolveDeviceBrand(): String {
        return DeviceIdentityManager.resolveDeviceBrand()
    }

    private fun resolveDeviceModel(): String {
        return DeviceIdentityManager.resolveDeviceModel()
    }

    private fun resolveDeviceOs(): String {
        return DeviceIdentityManager.resolveDeviceOs()
    }

    private fun resolveDeviceOsVersion(): String {
        return DeviceIdentityManager.resolveDeviceOsVersion()
    }

    private fun parseLedgerItems(array: JSONArray?): List<PointLedgerItem> {
        if (array == null) return emptyList()
        val result = ArrayList<PointLedgerItem>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                PointLedgerItem(
                    ledgerId = item.optString("ledgerId"),
                    bizType = item.optString("bizType"),
                    direction = item.optString("direction"),
                    amount = item.optInt("amount", 0),
                    balanceAfter = item.optInt("balanceAfter", 0),
                    bizId = item.optString("bizId"),
                    idempotencyKey = item.optString("idempotencyKey").ifBlank { null },
                    remark = item.optString("remark"),
                    createdAt = item.optString("createdAt")
                )
            )
        }
        return result
    }

    private fun parseTokenUsageItems(array: JSONArray?): List<TokenUsageLedgerItem> {
        if (array == null) return emptyList()
        val result = ArrayList<TokenUsageLedgerItem>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                TokenUsageLedgerItem(
                    id = item.optString("id"),
                    provider = item.optString("provider"),
                    model = item.optString("model"),
                    bizType = item.optString("bizType"),
                    bizId = item.optString("bizId"),
                    accountOwnerType = item.optString("accountOwnerType"),
                    accountOwnerId = item.optString("accountOwnerId"),
                    accountOwnerName = item.optString("accountOwnerName"),
                    promptTokens = item.optInt("promptTokens", 0),
                    completionTokens = item.optInt("completionTokens", 0),
                    totalTokens = item.optInt("totalTokens", 0),
                    pointsCost = item.optInt("pointsCost", 0),
                    requestId = item.optString("requestId"),
                    clientEventId = item.optString("clientEventId"),
                    occurredAt = item.optString("occurredAt"),
                    createdAt = item.optString("createdAt")
                )
            )
        }
        return result
    }

    private fun parsePersonas(array: JSONArray?): List<AssetPersona> {
        if (array == null) return emptyList()
        val result = ArrayList<AssetPersona>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                AssetPersona(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    promptText = item.optString("prompt_text"),
                    tone = item.optString("tone"),
                    constraints = item.optJSONObject("constraints_json") ?: JSONObject(),
                    isEnabled = item.optInt("is_enabled", 0) == 1 || item.optBoolean("is_enabled", false)
                )
            )
        }
        return result
    }

    private fun parseSkills(array: JSONArray?): List<AssetSkill> {
        if (array == null) return emptyList()
        val result = ArrayList<AssetSkill>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                AssetSkill(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    description = item.optString("description"),
                    params = item.optJSONObject("params_json") ?: JSONObject(),
                    isEnabled = item.optInt("is_enabled", 0) == 1 || item.optBoolean("is_enabled", false),
                    version = item.optInt("version", 1)
                )
            )
        }
        return result
    }

    private fun parseKnowledgeBases(array: JSONArray?): List<AssetKnowledgeBase> {
        if (array == null) return emptyList()
        val result = ArrayList<AssetKnowledgeBase>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                AssetKnowledgeBase(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    visibility = item.optString("visibility"),
                    status = item.optString("status"),
                    updatedAt = item.optString("updated_at")
                )
            )
        }
        return result
    }

    private fun parseKnowledgeDocs(array: JSONArray?): List<AssetKnowledgeDoc> {
        if (array == null) return emptyList()
        val result = ArrayList<AssetKnowledgeDoc>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                AssetKnowledgeDoc(
                    id = item.optString("id"),
                    knowledgeBaseId = item.optString("knowledge_base_id"),
                    title = item.optString("title"),
                    objectKey = item.optString("object_key"),
                    fileSize = item.optLong("file_size", 0L),
                    mimeType = item.optString("mime_type"),
                    splitStatus = item.optString("split_status"),
                    vectorStatus = item.optString("vector_status"),
                    updatedAt = item.optString("updated_at")
                )
            )
        }
        return result
    }

    private fun parseMemories(array: JSONArray?): List<AssetMemory> {
        if (array == null) return emptyList()
        val result = ArrayList<AssetMemory>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                AssetMemory(
                    id = item.optString("id"),
                    memoryType = item.optString("memory_type"),
                    content = item.optString("content"),
                    weight = item.optInt("weight", 1),
                    expireAt = item.optString("expire_at").ifBlank { null },
                    ref = item.optJSONObject("ref") ?: JSONObject()
                )
            )
        }
        return result
    }

    private fun parseCreation(item: JSONObject?): AssetCreation? {
        if (item == null) return null
        return AssetCreation(
            assetId = item.optString("assetId"),
            clientAssetId = item.optString("clientAssetId"),
            assetType = item.optString("assetType"),
            title = item.optString("title"),
            promptText = item.optString("promptText"),
            modelName = item.optString("modelName"),
            width = item.optInt("width", 0),
            height = item.optInt("height", 0),
            durationMs = item.optInt("durationMs", 0),
            fileSize = item.optLong("fileSize", 0L),
            mimeType = item.optString("mimeType"),
            originalObjectKey = item.optString("originalObjectKey"),
            coverObjectKey = item.optString("coverObjectKey"),
            thumbObjectKey = item.optString("thumbObjectKey"),
            status = item.optString("status"),
            sourceTaskId = item.optString("sourceTaskId"),
            sourceProvider = item.optString("sourceProvider"),
            createdAt = item.optString("createdAt"),
            updatedAt = item.optString("updatedAt")
        )
    }

    private fun parseCreations(array: JSONArray?): List<AssetCreation> {
        if (array == null) return emptyList()
        val result = ArrayList<AssetCreation>(array.length())
        for (i in 0 until array.length()) {
            val parsed = parseCreation(array.optJSONObject(i)) ?: continue
            result.add(parsed)
        }
        return result
    }

    private fun parseManagedThemes(array: JSONArray?): List<ManagedThemeItem> {
        if (array == null) return emptyList()
        val result = ArrayList<ManagedThemeItem>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                ManagedThemeItem(
                    themeId = item.optString("themeId"),
                    themeName = item.optString("themeName"),
                    packageThemeName = item.optString("packageThemeName"),
                    previewUrl = safeJsonString(item, "previewUrl").let { if (it.isBlank()) "" else resolveAbsoluteUrl(it) },
                    packageUrl = safeJsonString(item, "packageUrl").let { if (it.isBlank()) "" else resolveAbsoluteUrl(it) },
                    sizeKb = item.optInt("sizeKb", 0),
                    activeUsers = item.optInt("activeUsers", 0),
                    updatedAt = item.optString("updatedAt")
                )
            )
        }
        return result
    }

    private fun parseLiblibModelCatalog(array: JSONArray?): List<LiblibModelCatalogItem> {
        if (array == null) return emptyList()
        val result = ArrayList<LiblibModelCatalogItem>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                LiblibModelCatalogItem(
                    id = item.optString("id"),
                    category = item.optString("category"),
                    model = item.optString("model"),
                    displayName = item.optString("displayName"),
                    intro = item.optString("intro"),
                    iconUrl = item.optString("iconUrl"),
                    uriPath = item.optString("uriPath"),
                    templateUuid = item.optString("templateUuid"),
                    statusApiUri = item.optString("statusApiUri"),
                    statusIdKey = item.optString("statusIdKey"),
                    sortOrder = item.optInt("sortOrder", 0),
                    available = item.optBoolean("available", false)
                )
            )
        }
        return result
    }

    private fun parseMidsceneModelCatalog(catalog: JSONObject): List<MidsceneModelCatalogItem> {
        val result = linkedMapOf<String, MidsceneModelCatalogItem>()
        val roleKeys = listOf("vision", "decision")
        roleKeys.forEach { roleKey ->
            val array = catalog.optJSONArray(roleKey) ?: return@forEach
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (!item.optBoolean("enabled", true)) continue
                if (!item.optBoolean("supportsMidscene", true)) continue
                val model = item.optString("model").trim()
                val itemId = item.optString("id").trim()
                val key = if (model.isNotBlank()) model.lowercase() else itemId
                if (key.isBlank()) continue
                val scopes = buildList {
                    val scopeArray = item.optJSONArray("midsceneScopes")
                    if (scopeArray != null) {
                        for (j in 0 until scopeArray.length()) {
                            val scope = scopeArray.optString(j).trim()
                            if (scope.isNotBlank()) add(scope)
                        }
                    }
                }
                val parsed = MidsceneModelCatalogItem(
                    id = itemId,
                    role = item.optString("role").trim().ifBlank { roleKey },
                    provider = item.optString("provider").trim(),
                    model = model,
                    displayName = item.optString("displayName").trim().ifBlank { model },
                    intro = item.optString("intro").trim(),
                    iconUrl = safeJsonString(item, "iconUrl").let { if (it.isBlank()) "" else resolveAbsoluteUrl(it) },
                    supportsMidscene = true,
                    midsceneScopes = scopes,
                    midsceneFamily = item.optString("midsceneFamily").trim(),
                    supportsVision = item.optBoolean("supportsVision", roleKey == "vision"),
                    supportsPlanning = item.optBoolean("supportsPlanning", roleKey == "decision"),
                    supportsInsight = item.optBoolean("supportsInsight", roleKey == "vision")
                )
                val existing = result[key]
                result[key] = if (existing == null) {
                    parsed
                } else {
                    parsed.copy(
                        role = existing.role,
                        intro = if (existing.intro.isNotBlank()) existing.intro else parsed.intro,
                        supportsVision = existing.supportsVision || parsed.supportsVision,
                        supportsPlanning = existing.supportsPlanning || parsed.supportsPlanning,
                        supportsInsight = existing.supportsInsight || parsed.supportsInsight,
                        midsceneScopes = (existing.midsceneScopes + parsed.midsceneScopes).distinct()
                    )
                }
            }
        }
        return result.values.toList()
    }

    private fun doAuthorizedRequest(
        method: String,
        path: String,
        accessToken: String,
        refreshToken: String,
        body: JSONObject? = null
    ): AuthRequestResult {
        val first = when (method) {
            "GET" -> getJson(path, accessToken)
            "PUT" -> putJson(path, body ?: JSONObject(), accessToken)
            "DELETE" -> deleteJson(path, accessToken)
            else -> postJson(path, body ?: JSONObject(), accessToken)
        }
        if (first.first != 401) {
            return AuthRequestResult(first.first, parseEnvelope(first.second, "请求失败"), null, null)
        }
        if (refreshToken.isBlank()) {
            return AuthRequestResult(first.first, parseEnvelope(first.second, "登录态已失效"), null, null)
        }
        val refreshResult = refreshAccessToken(refreshToken)
        if (!refreshResult.success || refreshResult.accessToken.isBlank()) {
            return AuthRequestResult(
                first.first,
                Envelope(false, JSONObject(), refreshResult.message.ifBlank { "登录态已失效" }),
                null,
                null
            )
        }
        val second = when (method) {
            "GET" -> getJson(path, refreshResult.accessToken)
            "PUT" -> putJson(path, body ?: JSONObject(), refreshResult.accessToken)
            "DELETE" -> deleteJson(path, refreshResult.accessToken)
            else -> postJson(path, body ?: JSONObject(), refreshResult.accessToken)
        }
        return AuthRequestResult(
            second.first,
            parseEnvelope(second.second, "请求失败"),
            refreshResult.accessToken,
            refreshResult.refreshToken
        )
    }

    private fun mapMutationResult(response: AuthRequestResult, fallbackMessage: String): AssetMutationResult {
        return AssetMutationResult(
            success = response.statusCode in 200..299 && response.envelope.success,
            message = if (response.envelope.message.isNotBlank()) response.envelope.message else fallbackMessage,
            newAccessToken = response.newAccessToken,
            newRefreshToken = response.newRefreshToken,
            authInvalid = response.statusCode == 401
        )
    }

    private fun doAuthorizedGet(
        path: String,
        accessToken: String,
        refreshToken: String
    ): AuthRequestResult {
        return doAuthorizedRequest(
            method = "GET",
            path = path,
            accessToken = accessToken,
            refreshToken = refreshToken,
            body = null
        )
    }

    fun listPersonas(accessToken: String, refreshToken: String): PersonaListResult {
        return try {
            val response = doAuthorizedGet("/api/v1/assets/personas?page=1&pageSize=100", accessToken, refreshToken)
            if (response.statusCode in 200..299 && response.envelope.success) {
                return PersonaListResult(
                    success = true,
                    message = "获取成功",
                    items = parsePersonas(response.envelope.data.optJSONArray("items")),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            PersonaListResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取人设失败" },
                items = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            PersonaListResult(false, "连接服务超时，请检查服务端是否启动", emptyList(), null, null, false)
        } catch (_: Exception) {
            PersonaListResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList(), null, null, false)
        }
    }

    fun createPersona(
        accessToken: String,
        refreshToken: String,
        name: String,
        promptText: String,
        tone: String,
        isEnabled: Boolean,
        constraints: JSONObject = JSONObject()
    ): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/assets/personas",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
                    .put("name", name)
                    .put("promptText", promptText)
                    .put("tone", tone)
                    .put("isEnabled", isEnabled)
                    .put("constraints", constraints)
            )
            mapMutationResult(response, "创建人设失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun updatePersona(
        accessToken: String,
        refreshToken: String,
        personaId: String,
        name: String,
        promptText: String,
        tone: String,
        isEnabled: Boolean,
        constraints: JSONObject = JSONObject()
    ): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "PUT",
                path = "/api/v1/assets/personas/$personaId",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
                    .put("name", name)
                    .put("promptText", promptText)
                    .put("tone", tone)
                    .put("isEnabled", isEnabled)
                    .put("constraints", constraints)
            )
            mapMutationResult(response, "更新人设失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun deletePersona(accessToken: String, refreshToken: String, personaId: String): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "DELETE",
                path = "/api/v1/assets/personas/$personaId",
                accessToken = accessToken,
                refreshToken = refreshToken
            )
            mapMutationResult(response, "删除人设失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun listSkills(accessToken: String, refreshToken: String): SkillListResult {
        return try {
            val response = doAuthorizedGet("/api/v1/assets/skills?page=1&pageSize=200", accessToken, refreshToken)
            if (response.statusCode in 200..299 && response.envelope.success) {
                return SkillListResult(
                    success = true,
                    message = "获取成功",
                    items = parseSkills(response.envelope.data.optJSONArray("items")),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            SkillListResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取技能失败" },
                items = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            SkillListResult(false, "连接服务超时，请检查服务端是否启动", emptyList(), null, null, false)
        } catch (_: Exception) {
            SkillListResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList(), null, null, false)
        }
    }

    fun createSkill(
        accessToken: String,
        refreshToken: String,
        name: String,
        description: String,
        content: String,
        isEnabled: Boolean
    ): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/assets/skills",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("isEnabled", isEnabled)
                    .put("params", JSONObject().put("content", content))
            )
            mapMutationResult(response, "创建技能失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun updateSkill(
        accessToken: String,
        refreshToken: String,
        skillId: String,
        name: String,
        description: String,
        content: String,
        isEnabled: Boolean
    ): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "PUT",
                path = "/api/v1/assets/skills/$skillId",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("isEnabled", isEnabled)
                    .put("params", JSONObject().put("content", content))
            )
            mapMutationResult(response, "更新技能失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun deleteSkill(accessToken: String, refreshToken: String, skillId: String): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "DELETE",
                path = "/api/v1/assets/skills/$skillId",
                accessToken = accessToken,
                refreshToken = refreshToken
            )
            mapMutationResult(response, "删除技能失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun listKnowledgeBases(accessToken: String, refreshToken: String): KnowledgeBaseListResult {
        return try {
            val response = doAuthorizedGet("/api/v1/assets/knowledge-bases?page=1&pageSize=200", accessToken, refreshToken)
            if (response.statusCode in 200..299 && response.envelope.success) {
                return KnowledgeBaseListResult(
                    success = true,
                    message = "获取成功",
                    items = parseKnowledgeBases(response.envelope.data.optJSONArray("items")),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            KnowledgeBaseListResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取知识库失败" },
                items = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            KnowledgeBaseListResult(false, "连接服务超时，请检查服务端是否启动", emptyList(), null, null, false)
        } catch (_: Exception) {
            KnowledgeBaseListResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList(), null, null, false)
        }
    }

    fun createKnowledgeBase(
        accessToken: String,
        refreshToken: String,
        name: String,
        visibility: String = "private",
        status: String = "active"
    ): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/assets/knowledge-bases",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
                    .put("name", name)
                    .put("visibility", visibility)
                    .put("status", status)
            )
            mapMutationResult(response, "创建知识库失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun deleteKnowledgeBase(accessToken: String, refreshToken: String, knowledgeBaseId: String): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "DELETE",
                path = "/api/v1/assets/knowledge-bases/$knowledgeBaseId",
                accessToken = accessToken,
                refreshToken = refreshToken
            )
            mapMutationResult(response, "删除知识库失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun listKnowledgeDocs(
        accessToken: String,
        refreshToken: String,
        knowledgeBaseId: String
    ): KnowledgeDocListResult {
        return try {
            val path = "/api/v1/assets/knowledge-docs?page=1&pageSize=200&knowledgeBaseId=$knowledgeBaseId"
            val response = doAuthorizedGet(path, accessToken, refreshToken)
            if (response.statusCode in 200..299 && response.envelope.success) {
                return KnowledgeDocListResult(
                    success = true,
                    message = "获取成功",
                    items = parseKnowledgeDocs(response.envelope.data.optJSONArray("items")),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            KnowledgeDocListResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取知识文档失败" },
                items = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            KnowledgeDocListResult(false, "连接服务超时，请检查服务端是否启动", emptyList(), null, null, false)
        } catch (_: Exception) {
            KnowledgeDocListResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList(), null, null, false)
        }
    }

    fun createKnowledgeDoc(
        accessToken: String,
        refreshToken: String,
        knowledgeBaseId: String,
        title: String,
        documentId: String? = null,
        objectKey: String,
        fileSize: Long,
        mimeType: String,
        splitStatus: String = "pending",
        vectorStatus: String = "pending"
    ): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/assets/knowledge-docs",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
                    .put("knowledgeBaseId", knowledgeBaseId)
                    .put("title", title)
                    .put("id", documentId ?: "")
                    .put("objectKey", objectKey)
                    .put("fileSize", fileSize)
                    .put("mimeType", mimeType)
                    .put("splitStatus", splitStatus)
                    .put("vectorStatus", vectorStatus)
            )
            mapMutationResult(response, "创建知识文档失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun createOssUploadSign(
        accessToken: String,
        refreshToken: String,
        dir: String,
        fileName: String,
        contentType: String
    ): OssUploadSignResult {
        return try {
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/oss/upload-sign",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
                    .put("dir", dir)
                    .put("fileName", fileName)
                    .put("contentType", contentType)
            )
            val headers = response.envelope.data.optJSONObject("headers")
            val signedContentType = headers?.optString("Content-Type").orEmpty().ifBlank { contentType }
            if (response.statusCode in 200..299 && response.envelope.success) {
                return OssUploadSignResult(
                    success = true,
                    message = "获取上传签名成功",
                    uploadUrl = response.envelope.data.optString("uploadUrl"),
                    objectKey = response.envelope.data.optString("objectKey"),
                    contentType = signedContentType.ifBlank { "application/octet-stream" },
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            OssUploadSignResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取上传签名失败" },
                uploadUrl = "",
                objectKey = "",
                contentType = signedContentType.ifBlank { "application/octet-stream" },
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            OssUploadSignResult(false, "连接服务超时，请检查服务端是否启动", "", "", contentType, null, null, false)
        } catch (_: Exception) {
            OssUploadSignResult(false, "网络异常，请检查手机与服务端网络连通性", "", "", contentType, null, null, false)
        }
    }

    fun uploadFileBySignedUrl(
        uploadUrl: String,
        file: File,
        contentType: String
    ): AssetMutationResult {
        return try {
            val mediaType = runCatching { contentType.toMediaType() }
                .getOrDefault("application/octet-stream".toMediaType())
            val request = Request.Builder()
                .url(uploadUrl)
                .put(file.asRequestBody(mediaType))
                .addHeader("Content-Type", mediaType.toString())
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code in 200..299) {
                    AssetMutationResult(true, "上传成功", null, null, false)
                } else {
                    AssetMutationResult(false, "上传失败(${response.code})", null, null, false)
                }
            }
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "上传超时，请检查网络", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "上传异常，请稍后重试", null, null, false)
        }
    }

    fun createOssDownloadSign(
        accessToken: String,
        refreshToken: String,
        objectKey: String
    ): OssDownloadSignResult {
        return try {
            val encoded = java.net.URLEncoder.encode(objectKey, "UTF-8")
            val response = doAuthorizedGet(
                path = "/api/v1/oss/download-url?objectKey=$encoded",
                accessToken = accessToken,
                refreshToken = refreshToken
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                return OssDownloadSignResult(
                    success = true,
                    message = "获取下载签名成功",
                    downloadUrl = response.envelope.data.optString("downloadUrl"),
                    objectKey = response.envelope.data.optString("objectKey"),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            OssDownloadSignResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取下载签名失败" },
                downloadUrl = "",
                objectKey = objectKey,
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            OssDownloadSignResult(false, "连接服务超时，请检查服务端是否启动", "", objectKey, null, null, false)
        } catch (_: Exception) {
            OssDownloadSignResult(false, "网络异常，请检查手机与服务端网络连通性", "", objectKey, null, null, false)
        }
    }

    fun downloadFileByUrl(
        downloadUrl: String,
        target: File
    ): AssetMutationResult {
        return try {
            target.parentFile?.mkdirs()
            val request = Request.Builder()
                .url(downloadUrl)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code !in 200..299) {
                    return AssetMutationResult(false, "下载失败(${response.code})", null, null, false)
                }
                val body = response.body ?: return AssetMutationResult(false, "下载失败：响应体为空", null, null, false)
                target.outputStream().use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
                AssetMutationResult(true, "下载成功", null, null, false)
            }
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "下载超时，请检查网络", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "下载异常，请稍后重试", null, null, false)
        }
    }

    fun updateKnowledgeDocStatus(
        accessToken: String,
        refreshToken: String,
        docId: String,
        splitStatus: String,
        vectorStatus: String
    ): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "PUT",
                path = "/api/v1/assets/knowledge-docs/$docId/status",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
                    .put("splitStatus", splitStatus)
                    .put("vectorStatus", vectorStatus)
            )
            mapMutationResult(response, "更新知识文档状态失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun deleteKnowledgeDoc(accessToken: String, refreshToken: String, docId: String): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "DELETE",
                path = "/api/v1/assets/knowledge-docs/$docId",
                accessToken = accessToken,
                refreshToken = refreshToken
            )
            mapMutationResult(response, "删除知识文档失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun listMemories(
        accessToken: String,
        refreshToken: String,
        memoryType: String = ""
    ): MemoryListResult {
        return try {
            val path = if (memoryType.isBlank()) {
                "/api/v1/assets/memories?page=1&pageSize=200"
            } else {
                "/api/v1/assets/memories?page=1&pageSize=200&memoryType=$memoryType"
            }
            val response = doAuthorizedGet(path, accessToken, refreshToken)
            if (response.statusCode in 200..299 && response.envelope.success) {
                return MemoryListResult(
                    success = true,
                    message = "获取成功",
                    items = parseMemories(response.envelope.data.optJSONArray("items")),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            MemoryListResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取记忆失败" },
                items = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            MemoryListResult(false, "连接服务超时，请检查服务端是否启动", emptyList(), null, null, false)
        } catch (_: Exception) {
            MemoryListResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList(), null, null, false)
        }
    }

    fun createMemory(
        accessToken: String,
        refreshToken: String,
        memoryType: String,
        content: String,
        weight: Int = 1,
        expireAt: String? = null,
        ref: JSONObject = JSONObject()
    ): AssetMutationResult {
        return try {
            val body = JSONObject()
                .put("memoryType", memoryType)
                .put("content", content)
                .put("weight", weight)
                .put("ref", ref)
            if (!expireAt.isNullOrBlank()) {
                body.put("expireAt", expireAt)
            }
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/assets/memories",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = body
            )
            mapMutationResult(response, "创建记忆失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun deleteMemory(accessToken: String, refreshToken: String, memoryId: String): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "DELETE",
                path = "/api/v1/assets/memories/$memoryId",
                accessToken = accessToken,
                refreshToken = refreshToken
            )
            mapMutationResult(response, "删除记忆失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun createCreativeUploadSign(
        accessToken: String,
        refreshToken: String,
        assetType: String,
        purpose: String,
        fileName: String,
        contentType: String
    ): OssUploadSignResult {
        return try {
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/assets/creations/upload-sign",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
                    .put("assetType", assetType)
                    .put("purpose", purpose)
                    .put("fileName", fileName)
                    .put("contentType", contentType)
            )
            val headers = response.envelope.data.optJSONObject("headers")
            val signedContentType = headers?.optString("Content-Type").orEmpty().ifBlank { contentType }
            if (response.statusCode in 200..299 && response.envelope.success) {
                return OssUploadSignResult(
                    success = true,
                    message = "获取上传签名成功",
                    uploadUrl = response.envelope.data.optString("uploadUrl"),
                    objectKey = response.envelope.data.optString("objectKey"),
                    contentType = signedContentType.ifBlank { "application/octet-stream" },
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            OssUploadSignResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取上传签名失败" },
                uploadUrl = "",
                objectKey = "",
                contentType = signedContentType.ifBlank { "application/octet-stream" },
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            OssUploadSignResult(false, "连接服务超时，请检查服务端是否启动", "", "", contentType, null, null, false)
        } catch (_: Exception) {
            OssUploadSignResult(false, "网络异常，请检查手机与服务端网络连通性", "", "", contentType, null, null, false)
        }
    }

    fun createCreativeAsset(
        accessToken: String,
        refreshToken: String,
        clientAssetId: String,
        assetType: String,
        title: String,
        promptText: String,
        modelName: String,
        width: Int,
        height: Int,
        durationMs: Int,
        fileSize: Long,
        mimeType: String,
        originalObjectKey: String,
        coverObjectKey: String = "",
        thumbObjectKey: String = "",
        sourceTaskId: String = "",
        sourceProvider: String = "liblib",
        status: String = "ready"
    ): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/assets/creations",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
                    .put("clientAssetId", clientAssetId)
                    .put("assetType", assetType)
                    .put("title", title)
                    .put("promptText", promptText)
                    .put("modelName", modelName)
                    .put("width", width)
                    .put("height", height)
                    .put("durationMs", durationMs)
                    .put("fileSize", fileSize)
                    .put("mimeType", mimeType)
                    .put("originalObjectKey", originalObjectKey)
                    .put("coverObjectKey", coverObjectKey)
                    .put("thumbObjectKey", thumbObjectKey)
                    .put("sourceTaskId", sourceTaskId)
                    .put("sourceProvider", sourceProvider)
                    .put("status", status)
            )
            mapMutationResult(response, "创建创作记录失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun listCreativeAssets(
        accessToken: String,
        refreshToken: String,
        assetType: String = "",
        page: Int = 1,
        pageSize: Int = 100
    ): CreationListResult {
        return try {
            val queryType = assetType.trim().lowercase()
            val path = if (queryType.isBlank()) {
                "/api/v1/assets/creations?page=$page&pageSize=$pageSize"
            } else {
                "/api/v1/assets/creations?page=$page&pageSize=$pageSize&assetType=$queryType"
            }
            val response = doAuthorizedGet(path, accessToken, refreshToken)
            if (response.statusCode in 200..299 && response.envelope.success) {
                return CreationListResult(
                    success = true,
                    message = "获取成功",
                    items = parseCreations(response.envelope.data.optJSONArray("items")),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            CreationListResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取创作列表失败" },
                items = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            CreationListResult(false, "连接服务超时，请检查服务端是否启动", emptyList(), null, null, false)
        } catch (_: Exception) {
            CreationListResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList(), null, null, false)
        }
    }

    fun listCreativeSyncTasks(
        accessToken: String,
        refreshToken: String,
        status: String = "",
        page: Int = 1,
        pageSize: Int = 100
    ): CreativeSyncTaskListResult {
        return try {
            val queryStatus = status.trim().lowercase()
            val path = if (queryStatus.isBlank()) {
                "/api/v1/assets/creations/sync-tasks?page=$page&pageSize=$pageSize"
            } else {
                "/api/v1/assets/creations/sync-tasks?page=$page&pageSize=$pageSize&status=$queryStatus"
            }
            val response = doAuthorizedGet(path, accessToken, refreshToken)
            if (response.statusCode in 200..299 && response.envelope.success) {
                val array = response.envelope.data.optJSONArray("items") ?: JSONArray()
                val items = ArrayList<CreativeSyncTask>(array.length())
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    items.add(
                        CreativeSyncTask(
                            taskId = item.optString("taskId"),
                            clientAssetId = item.optString("clientAssetId"),
                            assetId = item.optString("assetId"),
                            assetType = item.optString("assetType"),
                            sourceTaskId = item.optString("sourceTaskId"),
                            originalObjectKey = item.optString("originalObjectKey"),
                            phase = item.optString("phase"),
                            status = item.optString("status"),
                            retryCount = item.optInt("retryCount", 0),
                            lastErrorCode = item.optString("lastErrorCode"),
                            lastErrorMessage = item.optString("lastErrorMessage")
                        )
                    )
                }
                return CreativeSyncTaskListResult(
                    success = true,
                    message = "获取同步任务成功",
                    items = items,
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            CreativeSyncTaskListResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取同步任务失败" },
                items = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            CreativeSyncTaskListResult(false, "连接服务超时，请检查服务端是否启动", emptyList(), null, null, false)
        } catch (_: Exception) {
            CreativeSyncTaskListResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList(), null, null, false)
        }
    }

    fun retryCreativeSyncTask(
        accessToken: String,
        refreshToken: String,
        clientAssetId: String
    ): AssetMutationResult {
        return try {
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/assets/creations/$clientAssetId/retry-sync",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
            )
            mapMutationResult(response, "重试同步失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun reportCreativeSync(
        accessToken: String,
        refreshToken: String,
        clientAssetId: String,
        assetType: String,
        phase: String,
        status: String = "",
        title: String = "",
        promptText: String = "",
        modelName: String = "",
        width: Int = 0,
        height: Int = 0,
        durationMs: Int = 0,
        fileSize: Long = 0L,
        mimeType: String = "",
        originalObjectKey: String = "",
        coverObjectKey: String = "",
        thumbObjectKey: String = "",
        sourceTaskId: String = "",
        sourceProvider: String = "liblib",
        errorCode: String = "",
        errorMessage: String = "",
        errorDetail: String = "",
        nextRetryAt: String = ""
    ): AssetMutationResult {
        return try {
            val body = JSONObject()
                .put("clientAssetId", clientAssetId)
                .put("assetType", assetType)
                .put("phase", phase)
                .put("status", status)
                .put("title", title)
                .put("promptText", promptText)
                .put("modelName", modelName)
                .put("width", width)
                .put("height", height)
                .put("durationMs", durationMs)
                .put("fileSize", fileSize)
                .put("mimeType", mimeType)
                .put("originalObjectKey", originalObjectKey)
                .put("coverObjectKey", coverObjectKey)
                .put("thumbObjectKey", thumbObjectKey)
                .put("sourceTaskId", sourceTaskId)
                .put("sourceProvider", sourceProvider)
                .put("errorCode", errorCode)
                .put("errorMessage", errorMessage)
                .put("errorDetail", errorDetail)
                .put("nextRetryAt", nextRetryAt)
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/assets/creations/sync-report",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = body
            )
            mapMutationResult(response, "上报创作同步状态失败")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "连接服务超时，请检查服务端是否启动", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun getCreativeAssetDetail(
        accessToken: String,
        refreshToken: String,
        assetId: String
    ): CreationDetailResult {
        return try {
            val response = doAuthorizedGet("/api/v1/assets/creations/$assetId", accessToken, refreshToken)
            if (response.statusCode in 200..299 && response.envelope.success) {
                return CreationDetailResult(
                    success = true,
                    message = "获取成功",
                    item = parseCreation(response.envelope.data),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            CreationDetailResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取创作详情失败" },
                item = null,
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            CreationDetailResult(false, "连接服务超时，请检查服务端是否启动", null, null, null, false)
        } catch (_: Exception) {
            CreationDetailResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, null, false)
        }
    }

    fun createCreativeDownloadSign(
        accessToken: String,
        refreshToken: String,
        assetId: String,
        purpose: String = "original"
    ): CreationDownloadSignResult {
        return try {
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/assets/creations/$assetId/download-sign",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject().put("purpose", purpose)
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                return CreationDownloadSignResult(
                    success = true,
                    message = "获取下载签名成功",
                    downloadUrl = response.envelope.data.optString("downloadUrl"),
                    objectKey = response.envelope.data.optString("objectKey"),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            CreationDownloadSignResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取下载签名失败" },
                downloadUrl = "",
                objectKey = "",
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            CreationDownloadSignResult(false, "连接服务超时，请检查服务端是否启动", "", "", null, null, false)
        } catch (_: Exception) {
            CreationDownloadSignResult(false, "网络异常，请检查手机与服务端网络连通性", "", "", null, null, false)
        }
    }

    fun getLiblibCloudConfig(accessToken: String, refreshToken: String): LiblibCloudConfigResult {
        return try {
            val response = doAuthorizedGet("/api/v1/assets/model-settings", accessToken, refreshToken)
            if (response.statusCode in 200..299 && response.envelope.success) {
                val draft = response.envelope.data.optJSONObject("draft") ?: JSONObject()
                val baseUrl = draft.optString("liblibBaseUrl")
                val accessKey = draft.optString("liblibAccessKey")
                val secretKey = draft.optString("liblibSecretKey")
                val ok = baseUrl.isNotBlank() && accessKey.isNotBlank() && secretKey.isNotBlank()
                return LiblibCloudConfigResult(
                    success = ok,
                    message = if (ok) "获取云端哩布哩布配置成功" else "云端哩布哩布配置不完整",
                    baseUrl = baseUrl,
                    accessKey = accessKey,
                    secretKey = secretKey,
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            LiblibCloudConfigResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取云端哩布哩布配置失败" },
                baseUrl = "",
                accessKey = "",
                secretKey = "",
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            LiblibCloudConfigResult(false, "连接服务超时，请检查服务端是否启动", "", "", "", null, null, false)
        } catch (_: Exception) {
            LiblibCloudConfigResult(false, "网络异常，请检查手机与服务端网络连通性", "", "", "", null, null, false)
        }
    }

    fun getLiblibModelCatalog(accessToken: String, refreshToken: String): LiblibModelCatalogResult {
        return try {
            val response = doAuthorizedGet("/api/v1/assets/model-catalog", accessToken, refreshToken)
            if (response.statusCode in 200..299 && response.envelope.success) {
                val catalog = response.envelope.data.optJSONObject("catalog") ?: JSONObject()
                val items = parseLiblibModelCatalog(catalog.optJSONArray("liblibModels"))
                return LiblibModelCatalogResult(
                    success = true,
                    message = "获取模型目录成功",
                    items = items,
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            LiblibModelCatalogResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取模型目录失败" },
                items = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            LiblibModelCatalogResult(false, "连接服务超时，请检查服务端是否启动", emptyList(), null, null, false)
        } catch (_: Exception) {
            LiblibModelCatalogResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList(), null, null, false)
        }
    }

    fun getCreativeTemplateCatalog(): CreativeTemplateCatalogResult {
        return try {
            val (status, json) = getJson("/api/v1/creative/channels", "")
            val envelope = parseEnvelope(json, "获取创作模板失败")
            if (status in 200..299 && envelope.success) {
                return CreativeTemplateCatalogResult(
                    success = true,
                    message = "获取创作模板成功",
                    items = parseCreativeTemplateCatalog(envelope.data.optJSONArray("items"))
                )
            }
            CreativeTemplateCatalogResult(
                success = false,
                message = envelope.message.ifBlank { "获取创作模板失败" },
                items = emptyList()
            )
        } catch (_: SocketTimeoutException) {
            CreativeTemplateCatalogResult(false, "连接服务超时，请检查服务端是否启动", emptyList())
        } catch (_: Exception) {
            CreativeTemplateCatalogResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList())
        }
    }

    fun getMidsceneModelCatalog(accessToken: String, refreshToken: String): MidsceneModelCatalogResult {
        return try {
            val response = doAuthorizedGet("/api/v1/assets/model-catalog", accessToken, refreshToken)
            if (response.statusCode in 200..299 && response.envelope.success) {
                val catalog = response.envelope.data.optJSONObject("catalog") ?: JSONObject()
                return MidsceneModelCatalogResult(
                    success = true,
                    message = "获取 Midscene 模型目录成功",
                    items = parseMidsceneModelCatalog(catalog),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            MidsceneModelCatalogResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取 Midscene 模型目录失败" },
                items = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            MidsceneModelCatalogResult(false, "连接服务超时，请检查服务端是否启动", emptyList(), null, null, false)
        } catch (_: Exception) {
            MidsceneModelCatalogResult(false, "网络异常，请检查手机与服务端网络连通性", emptyList(), null, null, false)
        }
    }

    fun getPreferredCloudModelConfig(
        accessToken: String,
        refreshToken: String,
        preferredModelName: String,
    ): CloudModelRuntimeConfigResult {
        return try {
            val response = doAuthorizedGet("/api/v1/assets/model-settings", accessToken, refreshToken)
            if (response.statusCode !in 200..299 || !response.envelope.success) {
                Log.w(
                    TAG,
                    "getPreferredCloudModelConfig request failed status=${response.statusCode} message=${response.envelope.message}"
                )
                return CloudModelRuntimeConfigResult(
                    success = false,
                    message = response.envelope.message.ifBlank { "获取模型设置失败" },
                    config = null,
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = response.statusCode == 401
                )
            }
            val settingsRoot = response.envelope.data
            val draft = settingsRoot.optJSONObject("draft")
            val decisionModels = draft?.optJSONArray("decisionModels")
            val selectedDecision = draft?.optJSONObject("decision")
            val preferredAliases = buildModelAliases(preferredModelName)
            val selectedModelRaw = selectedDecision?.optString("model").orEmpty()
                .ifBlank { selectedDecision?.optString("modelName").orEmpty() }
            val selectedAliases = buildModelAliases(selectedModelRaw)
            val preferredConfigFromSelected = buildRuntimeConfig(selectedDecision)
                ?.takeIf { it.matchesAnyAlias(preferredAliases) && !it.isProxyDecisionConfig() }
            val preferredConfigFromArray = findModelConfig(decisionModels, preferredAliases, requireDirect = true)
            val selectedConfigFromArray = findModelConfig(decisionModels, selectedAliases, requireDirect = true)
            val selectedDirectConfig = buildRuntimeConfig(selectedDecision)?.takeIf { !it.isProxyDecisionConfig() }
            val preferredConfigFromDraftTree = findModelConfigInTree(draft, preferredAliases, requireDirect = true)
            val preferredConfigFromSettingsTree = findModelConfigInTree(settingsRoot, preferredAliases, requireDirect = true)
            val preferredProxyFromDraftTree = findModelConfigInTree(draft, preferredAliases, requireDirect = false)
            val preferredProxyFromSettingsTree = findModelConfigInTree(settingsRoot, preferredAliases, requireDirect = false)
            val proxyFallback = selectedDirectConfig
                ?: findModelConfig(decisionModels, selectedAliases, requireDirect = false)
                ?: findModelConfig(decisionModels, preferredAliases, requireDirect = false)
                ?: preferredProxyFromDraftTree
                ?: preferredProxyFromSettingsTree
            val config = preferredConfigFromSelected
                ?: preferredConfigFromArray
                ?: preferredConfigFromDraftTree
                ?: preferredConfigFromSettingsTree
                ?: selectedConfigFromArray
                ?: selectedDirectConfig
                ?: proxyFallback
            Log.d(
                TAG,
                "getPreferredCloudModelConfig preferred=$preferredModelName selected=${selectedDecision?.optString("modelName").orEmpty()} decisionCount=${decisionModels?.length() ?: 0} settingsKeys=${settingsRoot.keys().asSequence().toList()} resolvedModel=${config?.modelName.orEmpty()} resolvedBaseUrl=${config?.baseUrl.orEmpty()}"
            )
            if (config != null) {
                return CloudModelRuntimeConfigResult(
                    success = true,
                    message = "获取成功",
                    config = config,
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            CloudModelRuntimeConfigResult(
                success = false,
                message = "未找到可用的模型配置",
                config = null,
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = false
            )
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "getPreferredCloudModelConfig timeout preferred=$preferredModelName", e)
            CloudModelRuntimeConfigResult(false, "AI 服务连接超时，请稍后重试", null, null, null, false)
        } catch (e: Exception) {
            Log.e(TAG, "getPreferredCloudModelConfig exception preferred=$preferredModelName", e)
            CloudModelRuntimeConfigResult(false, "AI 服务连接失败，请稍后重试", null, null, null, false)
        }
    }

    private fun findModelConfigInTree(
        node: Any?,
        aliases: Set<String>,
        requireDirect: Boolean,
    ): CloudModelRuntimeConfig? {
        if (node == null || aliases.isEmpty()) return null
        when (node) {
            is JSONObject -> {
                val directConfig = buildRuntimeConfig(node)
                if (directConfig != null &&
                    (!requireDirect || !directConfig.isProxyDecisionConfig()) &&
                    (directConfig.matchesAnyAlias(aliases) || node.matchesAnyAlias(aliases))
                ) {
                    return directConfig
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val childKey = keys.next()
                    val matched = findModelConfigInTree(node.opt(childKey), aliases, requireDirect)
                    if (matched != null) return matched
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    val matched = findModelConfigInTree(node.opt(index), aliases, requireDirect)
                    if (matched != null) return matched
                }
            }
        }
        return null
    }

    private fun findModelConfig(
        array: JSONArray?,
        aliases: Set<String>,
        requireDirect: Boolean,
    ): CloudModelRuntimeConfig? {
        if (array == null || aliases.isEmpty()) return null
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val config = buildRuntimeConfig(item) ?: continue
            if (requireDirect && config.isProxyDecisionConfig()) continue
            if (config.matchesAnyAlias(aliases) || item.matchesAnyAlias(aliases)) {
                return config
            }
        }
        return null
    }

    private fun buildRuntimeConfig(node: JSONObject?): CloudModelRuntimeConfig? {
        if (node == null) return null
        return CloudModelRuntimeConfig(
            modelName = node.optString("model").ifBlank { node.optString("modelName") }.trim(),
            apiKey = node.optString("apiKey").trim(),
            baseUrl = node.optString("baseUrl").trim().trimEnd('/'),
        ).takeIf { it.modelName.isNotBlank() && it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() }
    }

    private fun buildModelAliases(raw: String?): Set<String> {
        val seed = raw.orEmpty().trim()
        if (seed.isBlank()) return emptySet()
        val normalized = normalizeModelToken(seed)
        val aliases = linkedSetOf(normalized)
        when (normalized) {
            "qwen36flash" -> {
                aliases += normalizeModelToken("qwen3_6_flash")
                aliases += normalizeModelToken("qwen3.6-flash-2026-04-16")
                aliases += normalizeModelToken("qwen3_6_flash_20260416")
            }
            "qwen36plus" -> {
                aliases += normalizeModelToken("qwen3_6_plus")
                aliases += normalizeModelToken("qwen3.6-plus-2026-04-02")
            }
        }
        return aliases
    }

    private fun JSONObject.matchesAnyAlias(aliases: Set<String>): Boolean {
        if (aliases.isEmpty()) return false
        return sequenceOf(
            optString("model"),
            optString("modelName"),
            optString("displayName"),
            optString("id"),
        ).map(::normalizeModelToken).any { it.isNotBlank() && it in aliases }
    }

    private fun CloudModelRuntimeConfig.matchesAnyAlias(aliases: Set<String>): Boolean {
        if (aliases.isEmpty()) return false
        return normalizeModelToken(modelName) in aliases
    }

    private fun CloudModelRuntimeConfig.isProxyDecisionConfig(): Boolean {
        return normalizeModelToken(modelName) == "decision" ||
            baseUrl.contains("/model-proxy/decision", ignoreCase = true)
    }

    private fun normalizeModelToken(value: String?): String {
        return value.orEmpty()
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9]"""), "")
    }

    fun sendSmsCode(phoneNumber: String): SmsSendResult {
        return try {
            val (status, json) = postJson(
                path = "/api/v1/auth/sms/send",
                body = JSONObject().put("phoneNumber", phoneNumber)
            )
            val envelope = parseEnvelope(json, "发送失败")
            if (status in 200..299 && envelope.success) {
                return SmsSendResult(
                    success = true,
                    message = "验证码已发送",
                    debugCode = envelope.data.optString("debugCode").ifBlank { null }
                )
            }
            SmsSendResult(false, envelope.message.ifBlank { "发送失败" }, null)
        } catch (_: SocketTimeoutException) {
            SmsSendResult(false, "连接服务超时，请检查服务端是否启动", null)
        } catch (_: Exception) {
            SmsSendResult(false, "网络异常，请检查手机与服务端网络连通性", null)
        }
    }

    fun loginBySmsCode(phoneNumber: String, code: String): LoginResult {
        return try {
            val deviceId = resolveDeviceId()
            val deviceName = resolveDeviceName()
            val deviceBrand = resolveDeviceBrand()
            val deviceModel = resolveDeviceModel()
            val deviceOs = resolveDeviceOs()
            val deviceOsVersion = resolveDeviceOsVersion()
            val (status, json) = postJson(
                path = "/api/v1/auth/sms/login",
                body = JSONObject()
                    .put("phoneNumber", phoneNumber)
                    .put("code", code)
                    .put("deviceId", deviceId)
                    .put("deviceName", deviceName)
                    .put("deviceBrand", deviceBrand)
                    .put("deviceModel", deviceModel)
                    .put("deviceOs", deviceOs)
                    .put("deviceOsVersion", deviceOsVersion)
            )
            val envelope = parseEnvelope(json, "登录失败")
            if (status in 200..299 && envelope.success) {
                val user = envelope.data.optJSONObject("user") ?: JSONObject()
                return LoginResult(
                    success = true,
                    message = "登录成功",
                    accessToken = envelope.data.optString("accessToken"),
                    refreshToken = envelope.data.optString("refreshToken"),
                    userId = safeJsonString(user, "userId"),
                    phone = safeJsonString(user, "phoneNumber"),
                    nickname = safeJsonString(user, "nickname"),
                    avatarUrl = safeJsonString(user, "avatarUrl")
                )
            }
            LoginResult(false, envelope.message.ifBlank { "登录失败" }, "", "", "", "", "", "")
        } catch (_: SocketTimeoutException) {
            LoginResult(false, "连接服务超时，请检查服务端是否启动", "", "", "", "", "", "")
        } catch (_: Exception) {
            LoginResult(false, "网络异常，请检查手机与服务端网络连通性", "", "", "", "", "", "")
        }
    }

    fun loginByOneKeyToken(token: String): LoginResult {
        return try {
            val deviceId = resolveDeviceId()
            val deviceName = resolveDeviceName()
            val deviceBrand = resolveDeviceBrand()
            val deviceModel = resolveDeviceModel()
            val deviceOs = resolveDeviceOs()
            val deviceOsVersion = resolveDeviceOsVersion()
            val (status, json) = postJson(
                path = "/api/v1/auth/onekey/login",
                body = JSONObject()
                    .put("token", token)
                    .put("deviceId", deviceId)
                    .put("deviceName", deviceName)
                    .put("deviceBrand", deviceBrand)
                    .put("deviceModel", deviceModel)
                    .put("deviceOs", deviceOs)
                    .put("deviceOsVersion", deviceOsVersion)
            )
            val envelope = parseEnvelope(json, "一键登录失败")
            if (status in 200..299 && envelope.success) {
                val user = envelope.data.optJSONObject("user") ?: JSONObject()
                return LoginResult(
                    success = true,
                    message = "登录成功",
                    accessToken = envelope.data.optString("accessToken"),
                    refreshToken = envelope.data.optString("refreshToken"),
                    userId = safeJsonString(user, "userId"),
                    phone = safeJsonString(user, "phoneNumber"),
                    nickname = safeJsonString(user, "nickname"),
                    avatarUrl = safeJsonString(user, "avatarUrl")
                )
            }
            LoginResult(false, envelope.message.ifBlank { "一键登录失败" }, "", "", "", "", "", "")
        } catch (_: SocketTimeoutException) {
            LoginResult(false, "连接服务超时，请检查服务端是否启动", "", "", "", "", "", "")
        } catch (_: Exception) {
            LoginResult(false, "网络异常，请检查手机与服务端网络连通性", "", "", "", "", "", "")
        }
    }

    fun loginByWechatCode(code: String): LoginResult {
        return try {
            val deviceId = resolveDeviceId()
            val deviceName = resolveDeviceName()
            val deviceBrand = resolveDeviceBrand()
            val deviceModel = resolveDeviceModel()
            val deviceOs = resolveDeviceOs()
            val deviceOsVersion = resolveDeviceOsVersion()
            val (status, json) = postJson(
                path = "/api/v1/auth/wechat/login",
                body = JSONObject()
                    .put("code", code)
                    .put("deviceId", deviceId)
                    .put("deviceName", deviceName)
                    .put("deviceBrand", deviceBrand)
                    .put("deviceModel", deviceModel)
                    .put("deviceOs", deviceOs)
                    .put("deviceOsVersion", deviceOsVersion)
            )
            val envelope = parseEnvelope(json, "微信登录失败")
            if (status in 200..299 && envelope.success) {
                val user = envelope.data.optJSONObject("user") ?: JSONObject()
                return LoginResult(
                    success = true,
                    message = "登录成功",
                    accessToken = envelope.data.optString("accessToken"),
                    refreshToken = envelope.data.optString("refreshToken"),
                    userId = safeJsonString(user, "userId"),
                    phone = safeJsonString(user, "phoneNumber"),
                    nickname = safeJsonString(user, "nickname"),
                    avatarUrl = safeJsonString(user, "avatarUrl")
                )
            }
            LoginResult(false, envelope.message.ifBlank { "微信登录失败" }, "", "", "", "", "", "")
        } catch (_: SocketTimeoutException) {
            LoginResult(false, "连接服务超时，请检查服务端是否启动", "", "", "", "", "", "")
        } catch (_: Exception) {
            LoginResult(false, "网络异常，请检查手机与服务端网络连通性", "", "", "", "", "", "")
        }
    }

    fun refreshAccessToken(refreshToken: String): RefreshResult {
        return try {
            val deviceId = resolveDeviceId()
            val deviceName = resolveDeviceName()
            val deviceBrand = resolveDeviceBrand()
            val deviceModel = resolveDeviceModel()
            val deviceOs = resolveDeviceOs()
            val deviceOsVersion = resolveDeviceOsVersion()
            val (status, json) = postJson(
                path = "/api/v1/auth/refresh",
                body = JSONObject()
                    .put("refreshToken", refreshToken)
                    .put("deviceId", deviceId)
                    .put("deviceName", deviceName)
                    .put("deviceBrand", deviceBrand)
                    .put("deviceModel", deviceModel)
                    .put("deviceOs", deviceOs)
                    .put("deviceOsVersion", deviceOsVersion)
            )
            val envelope = parseEnvelope(json, "刷新登录态失败")
            if (status in 200..299 && envelope.success) {
                return RefreshResult(
                    success = true,
                    message = "刷新成功",
                    accessToken = envelope.data.optString("accessToken"),
                    refreshToken = envelope.data.optString("refreshToken")
                )
            }
            RefreshResult(false, envelope.message.ifBlank { "刷新登录态失败" }, "", "")
        } catch (_: SocketTimeoutException) {
            RefreshResult(false, "连接服务超时，请检查服务端是否启动", "", "")
        } catch (_: Exception) {
            RefreshResult(false, "网络异常，请检查手机与服务端网络连通性", "", "")
        }
    }

    fun getProfile(accessToken: String, refreshToken: String): UserProfileResult {
        return try {
            val response = doAuthorizedGet(
                path = "/api/v1/auth/profile",
                accessToken = accessToken,
                refreshToken = refreshToken
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                val user = response.envelope.data.optJSONObject("user") ?: JSONObject()
                return UserProfileResult(
                    success = true,
                    message = "获取成功",
                    profile = UserProfile(
                        userId = safeJsonString(user, "userId"),
                        phoneNumber = safeJsonString(user, "phoneNumber"),
                        nickname = safeJsonString(user, "nickname"),
                        avatarUrl = safeJsonString(user, "avatarUrl")
                    ),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            UserProfileResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取用户信息失败" },
                profile = null,
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            UserProfileResult(false, "连接服务超时，请检查服务端是否启动", null, null, null, false)
        } catch (_: Exception) {
            UserProfileResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, null, false)
        }
    }

    fun updateProfile(accessToken: String, refreshToken: String, nickname: String): UserProfileResult {
        return try {
            val response = doAuthorizedRequest(
                method = "PUT",
                path = "/api/v1/auth/profile",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject().put("nickname", nickname)
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                val user = response.envelope.data.optJSONObject("user") ?: JSONObject()
                return UserProfileResult(
                    success = true,
                    message = "更新成功",
                    profile = UserProfile(
                        userId = safeJsonString(user, "userId"),
                        phoneNumber = safeJsonString(user, "phoneNumber"),
                        nickname = safeJsonString(user, "nickname"),
                        avatarUrl = safeJsonString(user, "avatarUrl")
                    ),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            UserProfileResult(
                success = false,
                message = response.envelope.message.ifBlank { "更新用户信息失败" },
                profile = null,
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            UserProfileResult(false, "连接服务超时，请检查服务端是否启动", null, null, null, false)
        } catch (_: Exception) {
            UserProfileResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, null, false)
        }
    }

    fun getUserDetailProfile(accessToken: String, refreshToken: String): UserDetailProfileResult {
        return try {
            val response = doAuthorizedGet(
                path = "/api/v1/user/profile",
                accessToken = accessToken,
                refreshToken = refreshToken
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                return UserDetailProfileResult(
                    success = true,
                    message = "获取成功",
                    profile = parseUserDetailProfile(response.envelope.data),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            UserDetailProfileResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取扩展资料失败" },
                profile = null,
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            UserDetailProfileResult(false, "连接服务超时，请检查服务端是否启动", null, null, null, false)
        } catch (_: Exception) {
            UserDetailProfileResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, null, false)
        }
    }

    fun updateUserDetailProfile(
        accessToken: String,
        refreshToken: String,
        bio: String,
        tags: List<String>,
        location: String,
        age: String,
        zodiac: String,
        occupation: String
    ): UserDetailProfileResult {
        return try {
            val response = doAuthorizedRequest(
                method = "PUT",
                path = "/api/v1/user/profile",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject()
                    .put("bio", bio)
                    .put("tags", JSONArray(tags))
                    .put(
                        "preferences",
                        JSONObject()
                            .put("location", location)
                            .put("age", age)
                            .put("zodiac", zodiac)
                            .put("occupation", occupation)
                    )
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                return UserDetailProfileResult(
                    success = true,
                    message = "保存成功",
                    profile = parseUserDetailProfile(response.envelope.data),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            UserDetailProfileResult(
                success = false,
                message = response.envelope.message.ifBlank { "保存资料失败" },
                profile = null,
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            UserDetailProfileResult(false, "连接服务超时，请检查服务端是否启动", null, null, null, false)
        } catch (_: Exception) {
            UserDetailProfileResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, null, false)
        }
    }

    fun getPointSummary(accessToken: String, refreshToken: String): PointSummaryResult {
        return try {
            val response = doAuthorizedGet(
                path = "/api/v1/points/summary",
                accessToken = accessToken,
                refreshToken = refreshToken
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                return PointSummaryResult(
                    success = true,
                    message = "获取成功",
                    balance = response.envelope.data.optInt("balance", 0),
                    frozenBalance = response.envelope.data.optInt("frozenBalance", 0),
                    updatedAt = response.envelope.data.optString("updatedAt"),
                    latestLedgers = parseLedgerItems(response.envelope.data.optJSONArray("latestLedgers")),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            PointSummaryResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取积分失败" },
                balance = 0,
                frozenBalance = 0,
                updatedAt = "",
                latestLedgers = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            PointSummaryResult(false, "连接服务超时，请检查服务端是否启动", 0, 0, "", emptyList(), null, null, false)
        } catch (_: Exception) {
            PointSummaryResult(false, "网络异常，请检查手机与服务端网络连通性", 0, 0, "", emptyList(), null, null, false)
        }
    }

    fun getInviteSummary(accessToken: String, refreshToken: String): InviteSummaryResult {
        return try {
            val response = doAuthorizedGet(
                path = "/api/v1/invite/me",
                accessToken = accessToken,
                refreshToken = refreshToken
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                val data = response.envelope.data
                return InviteSummaryResult(
                    success = true,
                    message = "获取成功",
                    summary = InviteSummary(
                        inviteCode = data.optString("inviteCode"),
                        inviteUrl = data.optString("inviteUrl"),
                        inviterRewardAmount = data.optInt("inviterRewardAmount", 88),
                        inviteeRewardAmount = data.optInt("inviteeRewardAmount", 188),
                        totalInvitedUsers = data.optInt("totalInvitedUsers", 0),
                        totalInviterReward = data.optInt("totalInviterReward", 0),
                        hasBoundInviteCode = data.optBoolean("hasBoundInviteCode", false),
                        boundInviteCode = data.optString("boundInviteCode"),
                        boundAt = data.optString("boundAt")
                    ),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            InviteSummaryResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取邀请信息失败" },
                summary = null,
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            InviteSummaryResult(false, "连接服务超时，请检查服务端是否启动", null, null, null, false)
        } catch (_: Exception) {
            InviteSummaryResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, null, false)
        }
    }

    fun getPointLedger(
        accessToken: String,
        refreshToken: String,
        page: Int = 1,
        pageSize: Int = 50
    ): PointLedgerPageResult {
        return try {
            val response = doAuthorizedGet(
                path = "/api/v1/points/ledger?page=$page&pageSize=$pageSize",
                accessToken = accessToken,
                refreshToken = refreshToken
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                return PointLedgerPageResult(
                    success = true,
                    message = "获取成功",
                    page = response.envelope.data.optInt("page", page),
                    pageSize = response.envelope.data.optInt("pageSize", pageSize),
                    total = response.envelope.data.optInt("total", 0),
                    items = parseLedgerItems(response.envelope.data.optJSONArray("items")),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            PointLedgerPageResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取积分流水失败" },
                page = page,
                pageSize = pageSize,
                total = 0,
                items = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            PointLedgerPageResult(false, "连接服务超时，请检查服务端是否启动", page, pageSize, 0, emptyList(), null, null, false)
        } catch (_: Exception) {
            PointLedgerPageResult(false, "网络异常，请检查手机与服务端网络连通性", page, pageSize, 0, emptyList(), null, null, false)
        }
    }

    fun uploadTokenUsageEvents(
        accessToken: String,
        refreshToken: String,
        events: List<TokenUsageEvent>
    ): TokenUsageBatchResult {
        return try {
            val array = JSONArray()
            events.forEach { event ->
                val item = JSONObject()
                    .put("clientEventId", event.clientEventId)
                    .put("provider", event.provider)
                    .put("model", event.model)
                    .put("bizType", event.bizType)
                    .put("bizId", event.bizId)
                    .put("accountOwnerType", event.accountOwnerType)
                    .put("accountOwnerId", event.accountOwnerId)
                    .put("accountOwnerName", event.accountOwnerName)
                    .put("promptTokens", event.promptTokens)
                    .put("completionTokens", event.completionTokens)
                    .put("totalTokens", event.totalTokens)
                    .put("pointsCost", event.pointsCost)
                    .put("requestId", event.requestId)
                    .put("occurredAt", event.occurredAt)
                array.put(item)
            }
            val response = doAuthorizedRequest(
                method = "POST",
                path = "/api/v1/token-usage/events:batch",
                accessToken = accessToken,
                refreshToken = refreshToken,
                body = JSONObject().put("events", array)
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                return TokenUsageBatchResult(
                    success = true,
                    message = "上报成功",
                    acceptedCount = response.envelope.data.optInt("acceptedCount", 0),
                    duplicatedCount = response.envelope.data.optInt("duplicatedCount", 0),
                    rejectedCount = response.envelope.data.optInt("rejectedCount", 0),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            TokenUsageBatchResult(
                success = false,
                message = response.envelope.message.ifBlank { "Token 消耗上报失败" },
                acceptedCount = 0,
                duplicatedCount = 0,
                rejectedCount = 0,
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            TokenUsageBatchResult(false, "连接服务超时，请检查服务端是否启动", 0, 0, 0, null, null, false)
        } catch (_: Exception) {
            TokenUsageBatchResult(false, "网络异常，请检查手机与服务端网络连通性", 0, 0, 0, null, null, false)
        }
    }

    fun upsertAiCommunicateRecord(
        accessToken: String,
        refreshToken: String,
        payload: AiCommunicateRecordPayload,
    ): AssetMutationResult {
        return try {
            val replies = JSONArray()
            payload.replies.forEach(replies::put)
            val screenshotDataUrls = JSONArray()
            payload.screenshotDataUrls.forEach(screenshotDataUrls::put)
            val response =
                doAuthorizedRequest(
                    method = "POST",
                    path = "/api/v1/ai-communicate/records",
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    body =
                        JSONObject()
                            .put("sessionId", payload.sessionId)
                            .put("mode", payload.mode)
                            .put("tone", payload.tone)
                            .put("promptText", payload.promptText)
                            .put("ocrContextPreview", payload.ocrContextPreview)
                            .put("screenshotDataUrl", payload.screenshotDataUrl)
                            .put("screenshotDataUrls", screenshotDataUrls)
                            .put("screenshotMimeType", payload.screenshotMimeType)
                            .put("replies", replies)
                            .put("modelName", payload.modelName)
                            .put("provider", payload.provider),
                )
            mapMutationResult(response, "AI沟通记录保存成功")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "保存 AI沟通记录超时，请稍后重试", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun reportAiReplyTask(
        accessToken: String,
        refreshToken: String,
        payload: AiReplyTaskReportPayload,
    ): AiReplyTaskReportResult {
        return try {
            val response =
                doAuthorizedRequest(
                    method = "POST",
                    path = "/api/v1/ai-communicate/tasks/report",
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    body =
                        JSONObject()
                            .put("sessionId", payload.sessionId)
                            .put("mode", payload.mode)
                            .put("tone", payload.tone)
                            .put("entry", payload.entry)
                            .put("startedAt", java.time.Instant.ofEpochMilli(payload.startedAt).toString())
                            .put("finishedAt", java.time.Instant.ofEpochMilli(payload.finishedAt).toString())
                            .put("finalStatus", payload.finalStatus)
                            .put("reasonCode", payload.reasonCode)
                            .put("reasonMessage", payload.reasonMessage)
                            .put("summary", payload.summary)
                            .put("device", payload.device)
                            .put("runtime", payload.runtime)
                            .put("clientTimeline", payload.clientTimeline)
                            .put("abnormalLog", payload.abnormalLog)
                            .put("extraDetail", payload.extraDetail),
                )
            if (response.statusCode in 200..299 && response.envelope.success) {
                return AiReplyTaskReportResult(
                    success = true,
                    message = "AI回复任务日志上传成功",
                    taskId = response.envelope.data.optString("taskId"),
                    sessionId = response.envelope.data.optString("sessionId"),
                    finalStatus = response.envelope.data.optString("finalStatus"),
                    abnormalLogUploaded = response.envelope.data.optBoolean("abnormalLogUploaded", false),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false,
                )
            }
            AiReplyTaskReportResult(
                success = false,
                message = response.envelope.message.ifBlank { "AI回复任务日志上传失败" },
                taskId = "",
                sessionId = payload.sessionId,
                finalStatus = payload.finalStatus,
                abnormalLogUploaded = false,
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401,
            )
        } catch (_: SocketTimeoutException) {
            AiReplyTaskReportResult(false, "上传 AI回复任务日志超时，请稍后重试", "", payload.sessionId, payload.finalStatus, false, null, null, false)
        } catch (_: Exception) {
            AiReplyTaskReportResult(false, "网络异常，请检查手机与服务端网络连通性", "", payload.sessionId, payload.finalStatus, false, null, null, false)
        }
    }

    fun markAiCommunicateReplySelection(
        accessToken: String,
        refreshToken: String,
        sessionId: String,
        replyIndex: Int,
        replyText: String,
    ): AssetMutationResult {
        return try {
            val response =
                doAuthorizedRequest(
                    method = "POST",
                    path = "/api/v1/ai-communicate/records/$sessionId/selection",
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    body =
                        JSONObject()
                            .put("replyIndex", replyIndex)
                            .put("replyText", replyText),
                )
            mapMutationResult(response, "AI沟通选中记录成功")
        } catch (_: SocketTimeoutException) {
            AssetMutationResult(false, "保存 AI沟通点击记录超时，请稍后重试", null, null, false)
        } catch (_: Exception) {
            AssetMutationResult(false, "网络异常，请检查手机与服务端网络连通性", null, null, false)
        }
    }

    fun getTokenUsageLedger(
        accessToken: String,
        refreshToken: String,
        provider: String,
        accountOwnerId: String = "",
        page: Int = 1,
        pageSize: Int = 100
    ): TokenUsageLedgerPageResult {
        return try {
            val path = "/api/v1/token-usage/ledger?page=$page&pageSize=$pageSize&provider=$provider&accountOwnerId=$accountOwnerId"
            val response = doAuthorizedGet(
                path = path,
                accessToken = accessToken,
                refreshToken = refreshToken
            )
            if (response.statusCode in 200..299 && response.envelope.success) {
                return TokenUsageLedgerPageResult(
                    success = true,
                    message = "获取成功",
                    page = response.envelope.data.optInt("page", page),
                    pageSize = response.envelope.data.optInt("pageSize", pageSize),
                    total = response.envelope.data.optInt("total", 0),
                    items = parseTokenUsageItems(response.envelope.data.optJSONArray("items")),
                    newAccessToken = response.newAccessToken,
                    newRefreshToken = response.newRefreshToken,
                    authInvalid = false
                )
            }
            TokenUsageLedgerPageResult(
                success = false,
                message = response.envelope.message.ifBlank { "获取 Token 明细失败" },
                page = page,
                pageSize = pageSize,
                total = 0,
                items = emptyList(),
                newAccessToken = response.newAccessToken,
                newRefreshToken = response.newRefreshToken,
                authInvalid = response.statusCode == 401
            )
        } catch (_: SocketTimeoutException) {
            TokenUsageLedgerPageResult(false, "连接服务超时，请检查服务端是否启动", page, pageSize, 0, emptyList(), null, null, false)
        } catch (_: Exception) {
            TokenUsageLedgerPageResult(false, "网络异常，请检查手机与服务端网络连通性", page, pageSize, 0, emptyList(), null, null, false)
        }
    }

    private fun parseAppUpgradeInfo(data: JSONObject): AppUpgradeInfo {
        return AppUpgradeInfo(
            releaseId = safeJsonString(data, "releaseId"),
            displayVersion = safeJsonString(data, "displayVersion"),
            packageVersionCode = data.optLong("packageVersionCode", 0L),
            packageVersionName = safeJsonString(data, "packageVersionName"),
            title = safeJsonString(data, "title"),
            content = safeJsonString(data, "content"),
            downloadUrl = safeJsonString(data, "downloadUrl"),
            isForceUpdate = data.optBoolean("isForceUpdate", false)
        )
    }

    fun checkAppUpgrade(
        packageVersionCode: Long,
        bearerToken: String? = null
    ): AppUpgradeCheckResult {
        if (!OemEnterpriseConfig.shouldAllowAppUpgrade()) {
            return AppUpgradeCheckResult(
                success = false,
                hasUpdate = false,
                message = "当前 OEM 版本未开放在线升级",
                upgrade = null,
                authInvalid = false,
            )
        }
        return try {
            val encodedVersionCode = packageVersionCode.toString()
            val response = requestJson(
                path = "/api/v1/app/android/upgrade-check?packageVersionCode=$encodedVersionCode",
                method = "GET",
                body = null,
                bearerToken = bearerToken
            )
            val envelope = parseEnvelope(response.second, "检查更新失败")
            if (response.first in 200..299 && envelope.success) {
                val hasUpdate = envelope.data.optBoolean("hasUpdate", false)
                return AppUpgradeCheckResult(
                    success = true,
                    hasUpdate = hasUpdate,
                    message = envelope.message.ifBlank { if (hasUpdate) "发现新版本" else "当前已是最新版本" },
                    upgrade = if (hasUpdate) parseAppUpgradeInfo(envelope.data) else null,
                    authInvalid = false
                )
            }
            AppUpgradeCheckResult(
                success = false,
                hasUpdate = false,
                message = envelope.message.ifBlank { "检查更新失败" },
                upgrade = null,
                authInvalid = response.first == 401
            )
        } catch (_: SocketTimeoutException) {
            AppUpgradeCheckResult(false, false, "连接服务超时，请稍后重试", null, false)
        } catch (_: Exception) {
            AppUpgradeCheckResult(false, false, "网络异常，请检查手机与服务端网络连通性", null, false)
        }
    }
}
