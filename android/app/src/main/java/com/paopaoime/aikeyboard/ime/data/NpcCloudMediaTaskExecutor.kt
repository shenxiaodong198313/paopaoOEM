package com.paopaoime.aikeyboard.ime.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.preference.PreferenceManager
import com.paopaoime.aikeyboard.ime.ai.liblib.GenerateRequest
import com.paopaoime.aikeyboard.ime.ai.liblib.GenerateTaskIdData
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibClient
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibEnvelope
import com.paopaoime.aikeyboard.ime.ai.liblib.StatusData
import com.paopaoime.aikeyboard.ime.ai.store.LocalAssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

data class NpcCloudMediaResult(
    val success: Boolean,
    val localPaths: List<String> = emptyList(),
    val prompt: String = "",
    val errorMessage: String = "",
    val durationMs: Long = 0L
)

data class CloudGenerationOverrides(
    val imageRatio: String? = null,
    val imageResolution: String? = null,
    val imageCount: Int? = null,
    val videoAspectRatio: String? = null,
    val videoDuration: Int? = null,
    val videoMode: String? = null,
    val videoModel: String? = null
)

object NpcCloudMediaTaskExecutor {
    private const val TAG = "NpcCloudMediaTaskExec"
    const val SKILL_QWEN_IMAGE = "qwen-image-generate-image"
    const val SKILL_QWEN_VIDEO = "qwen-image-generate-video"
    const val SKILL_XINGLIU_IMAGE = "xingliu-generate-image"
    const val SKILL_XINGLIU_VIDEO = "xingliu-generate-video"

    suspend fun generateBySkill(
        context: Context,
        skillId: String,
        prompt: String,
        stableMode: Boolean = false,
        overrides: CloudGenerationOverrides? = null,
        onProgress: suspend (Int) -> Unit
    ): NpcCloudMediaResult = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val ak = sanitizeKey(prefs.getString("liblib_access_key", "").orEmpty())
        val sk = sanitizeKey(prefs.getString("liblib_secret_key", "").orEmpty())
        if (ak.isBlank() || sk.isBlank()) {
            return@withContext NpcCloudMediaResult(success = false, errorMessage = "未配置Liblib API密钥")
        }
        val client = LiblibClient(ak, sk)
        val startedAt = System.currentTimeMillis()
        val request = buildRequest(
            skillId = skillId,
            prompt = normalizePrompt(prompt),
            stableMode = stableMode,
            overrides = overrides
        )
            ?: return@withContext NpcCloudMediaResult(success = false, errorMessage = "不支持的技能")
        val generateResp = runCatching {
            client.postJson(
                uriPath = request.submitUri,
                requestBody = GenerateRequest(
                    templateUuid = request.templateUuid,
                    generateParams = request.params
                ),
                requestSerializer = GenerateRequest.serializer(),
                responseSerializer = LiblibEnvelope.serializer(GenerateTaskIdData.serializer())
            )
        }.getOrElse { throwable ->
            val apiMessage = throwable.message.orEmpty().ifBlank { "网络请求失败" }
            Log.e(TAG, "submit exception skill=$skillId, msg=$apiMessage")
            return@withContext NpcCloudMediaResult(success = false, errorMessage = "提交异常：$apiMessage")
        }
        if (generateResp.code != 0) {
            val apiMessage = generateResp.msg.ifBlank { "接口返回错误" }
            Log.e(TAG, "submit failed code=${generateResp.code}, msg=$apiMessage, skill=$skillId")
            val detail = if (generateResp.code == 401) {
                "，请检查AccessKey/SecretKey是否正确且无空格换行"
            } else {
                ""
            }
            return@withContext NpcCloudMediaResult(
                success = false,
                errorMessage = "提交失败(code=${generateResp.code})：$apiMessage$detail"
            )
        }
        val taskUid = generateResp.data?.generateUid.orEmpty()
        val taskUuid = generateResp.data?.generateUuid.orEmpty()
        val taskId = taskUid.ifBlank { taskUuid }.ifBlank {
            val apiMessage = generateResp.msg.ifBlank { "未返回任务ID" }
            Log.e(TAG, "submit no task id, msg=$apiMessage, skill=$skillId")
            return@withContext NpcCloudMediaResult(
                success = false,
                errorMessage = "生成任务提交失败：$apiMessage"
            )
        }
        var lastProgress = 0
        repeat(150) {
            val statusResp = runCatching {
                client.postJson(
                    uriPath = request.statusUri,
                    requestBody = buildJsonObject {
                        put("generateUid", if (taskUid.isNotBlank()) taskUid else taskId)
                        put("generateUuid", if (taskUuid.isNotBlank()) taskUuid else taskId)
                    },
                    requestSerializer = JsonObject.serializer(),
                    responseSerializer = LiblibEnvelope.serializer(StatusData.serializer())
                )
            }.getOrElse { throwable ->
                val apiMessage = throwable.message.orEmpty().ifBlank { "网络请求失败" }
                Log.e(TAG, "status exception taskId=$taskId, msg=$apiMessage")
                return@withContext NpcCloudMediaResult(
                    success = false,
                    errorMessage = "状态查询异常：$apiMessage",
                    durationMs = System.currentTimeMillis() - startedAt
                )
            }
            if (statusResp.code != 0) {
                val apiMessage = statusResp.msg.ifBlank { "状态查询失败" }
                Log.e(TAG, "status failed code=${statusResp.code}, msg=$apiMessage, taskId=$taskId")
                return@withContext NpcCloudMediaResult(
                    success = false,
                    errorMessage = "状态查询失败(code=${statusResp.code})：$apiMessage",
                    durationMs = System.currentTimeMillis() - startedAt
                )
            }
            val data = statusResp.data
            if (data != null) {
                val progress = (data.percentCompleted * 100f).toInt().coerceIn(0, 100)
                if (progress > lastProgress) {
                    lastProgress = progress
                    onProgress(lastProgress)
                }
                if (progress <= 0 && data.generateStatus in 1..4 && lastProgress < 95) {
                    lastProgress = (lastProgress + 5).coerceAtMost(95)
                    onProgress(lastProgress)
                }
                if (data.generateStatus == 5 || (data.generateStatus == 3 && (data.images.isNotEmpty() || data.videos.isNotEmpty()))) {
                    onProgress(100)
                    val localPaths = saveResultToLocal(context, client, data, request, prompt)
                    if (localPaths.isEmpty()) {
                        return@withContext NpcCloudMediaResult(
                            success = false,
                            errorMessage = "生成成功但未获取到产物",
                            durationMs = System.currentTimeMillis() - startedAt
                        )
                    }
                    return@withContext NpcCloudMediaResult(
                        success = true,
                        localPaths = localPaths,
                        prompt = prompt,
                        durationMs = System.currentTimeMillis() - startedAt
                    )
                }
                if (data.generateStatus == 6 || data.generateStatus == 7) {
                    val failedMessage = data.generateMsg.orEmpty().ifBlank { "生成失败" }
                    Log.e(TAG, "generate failed status=${data.generateStatus}, msg=$failedMessage, taskId=$taskId")
                    return@withContext NpcCloudMediaResult(
                        success = false,
                        errorMessage = "$failedMessage(status=${data.generateStatus},task=$taskId)",
                        durationMs = System.currentTimeMillis() - startedAt
                    )
                }
            }
            delay(5000L)
        }
        Log.e(TAG, "generate timeout taskId=$taskId, skill=$skillId, stableMode=$stableMode")
        NpcCloudMediaResult(
            success = false,
            errorMessage = "生成超时",
            durationMs = System.currentTimeMillis() - startedAt
        )
    }

    private suspend fun saveResultToLocal(
        context: Context,
        client: LiblibClient,
        data: StatusData,
        request: CloudRequest,
        prompt: String
    ): List<String> {
        val manager = LocalAssetManager.getInstance(context.applicationContext)
        val localPaths = mutableListOf<String>()
        if (request.isVideo) {
            data.videos.forEach { video ->
                if (video.videoUrl.isBlank()) return@forEach
                val asset = manager.saveAsset(
                    client = client,
                    url = video.videoUrl,
                    type = 1,
                    prompt = prompt,
                    model = request.modelName,
                    duration = request.duration,
                    coverUrl = video.coverPath
                )
                localPaths.add(asset.localPath)
                saveVideoToGallery(context, asset.localPath)
            }
        } else {
            data.images.forEach { image ->
                if (image.imageUrl.isBlank()) return@forEach
                val asset = manager.saveAsset(
                    client = client,
                    url = image.imageUrl,
                    type = 0,
                    prompt = prompt,
                    model = request.modelName,
                    width = request.width,
                    height = request.height
                )
                localPaths.add(asset.localPath)
                saveImageToGallery(context, asset.localPath)
            }
        }
        return localPaths
    }

    private fun saveImageToGallery(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "OpenClaw_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/IME-X")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        }
    }

    private fun saveVideoToGallery(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "OpenClaw_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/IME-X")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        }
    }

    private data class CloudRequest(
        val submitUri: String,
        val statusUri: String,
        val templateUuid: String,
        val params: JsonObject,
        val isVideo: Boolean,
        val modelName: String,
        val width: Int = 0,
        val height: Int = 0,
        val duration: Int = 0
    )

    private fun buildRequest(
        skillId: String,
        prompt: String,
        stableMode: Boolean,
        overrides: CloudGenerationOverrides?
    ): CloudRequest? {
        return when (skillId) {
            SKILL_QWEN_IMAGE -> {
                val ratio = overrides?.imageRatio ?: "9:16"
                val resolution = overrides?.imageResolution ?: "1k"
                val imageCount = (overrides?.imageCount ?: 1).coerceAtLeast(1)
                val size = calculateImageSize(skillId = skillId, ratio = ratio, resolution = resolution)
                CloudRequest(
                    submitUri = "/api/generate/webui/text2img",
                    statusUri = "/api/generate/status",
                    templateUuid = "bf085132c7134622895b783b520b39ff",
                    params = buildJsonObject {
                        put("prompt", prompt)
                        put("imgCount", imageCount)
                        put("steps", if (stableMode) 24 else 30)
                        put("checkPointId", "75e0be0c93b34dd8baeec9c968013e0c")
                        put("width", size.first)
                        put("height", size.second)
                    },
                    isVideo = false,
                    modelName = "Qwen-Image",
                    width = size.first,
                    height = size.second
                )
            }
            SKILL_XINGLIU_IMAGE -> {
                val ratio = overrides?.imageRatio ?: "9:16"
                val resolution = overrides?.imageResolution ?: "1k"
                val imageCount = (overrides?.imageCount ?: 1).coerceAtLeast(1)
                val size = calculateImageSize(skillId = skillId, ratio = ratio, resolution = resolution)
                CloudRequest(
                    submitUri = "/api/generate/webui/text2img/ultra",
                    statusUri = "/api/generate/webui/status",
                    templateUuid = "5d7e67009b344550bc1aa6ccbfa1d7f4",
                    params = buildJsonObject {
                        put("prompt", prompt)
                        put("imgCount", imageCount)
                        put("steps", if (stableMode) 16 else 20)
                        putJsonObject("imageSize") {
                            put("width", size.first)
                            put("height", size.second)
                        }
                    },
                    isVideo = false,
                    modelName = "星流-Image",
                    width = size.first,
                    height = size.second
                )
            }
            SKILL_QWEN_VIDEO -> {
                val model = overrides?.videoModel ?: "kling-v2-1-master"
                val aspectRatio = overrides?.videoAspectRatio ?: "9:16"
                val duration = (overrides?.videoDuration ?: 5).coerceAtLeast(1)
                val mode = overrides?.videoMode ?: "std"
                CloudRequest(
                    submitUri = "/api/generate/video/kling/text2video",
                    statusUri = "/api/generate/status",
                    templateUuid = "61cd8b60d340404394f2a545eeaf197a",
                    params = buildJsonObject {
                        put("model", model)
                        put("prompt", prompt)
                        put("promptMagic", 1)
                        put("aspectRatio", aspectRatio)
                        put("duration", duration)
                        put("mode", mode)
                    },
                    isVideo = true,
                    modelName = "Qwen-Video",
                    duration = duration
                )
            }
            SKILL_XINGLIU_VIDEO -> {
                val model = overrides?.videoModel ?: "kling-v2-master"
                val aspectRatio = overrides?.videoAspectRatio ?: "9:16"
                val duration = (overrides?.videoDuration ?: 5).coerceAtLeast(1)
                val mode = overrides?.videoMode ?: "std"
                CloudRequest(
                    submitUri = "/api/generate/video/kling/text2video",
                    statusUri = "/api/generate/status",
                    templateUuid = "61cd8b60d340404394f2a545eeaf197a",
                    params = buildJsonObject {
                        put("model", model)
                        put("prompt", prompt)
                        put("promptMagic", 1)
                        put("aspectRatio", aspectRatio)
                        put("duration", duration)
                        put("mode", mode)
                    },
                    isVideo = true,
                    modelName = "星流-Video",
                    duration = duration
                )
            }
            else -> null
        }
    }

    private fun calculateImageSize(skillId: String, ratio: String, resolution: String): Pair<Int, Int> {
        val is2k = resolution == "2k"
        return if (skillId == SKILL_QWEN_IMAGE) {
            var base = when (ratio) {
                "1:1" -> 1328 to 1328
                "3:4" -> 1140 to 1472
                "4:3" -> 1472 to 1140
                "16:9" -> 1664 to 928
                else -> 928 to 1664
            }
            if (is2k) {
                base = (base.first * 1.2).toInt().coerceAtMost(2048) to (base.second * 1.2).toInt().coerceAtMost(2048)
            }
            base
        } else {
            var base = when (ratio) {
                "1:1" -> 1024 to 1024
                "3:4" -> 768 to 1024
                "4:3" -> 1024 to 768
                "16:9" -> 1280 to 720
                else -> 720 to 1280
            }
            if (is2k) {
                base = (base.first * 1.5).toInt().coerceAtMost(2048) to (base.second * 1.5).toInt().coerceAtMost(2048)
            }
            base
        }
    }

    private fun sanitizeKey(raw: String): String {
        return raw.filterNot { it == '\r' || it == '\n' || it == '\t' || it == ' ' }.trim()
    }

    private fun normalizePrompt(prompt: String): String {
        val collapsed = prompt.replace(Regex("\\s+"), " ").trim()
        return collapsed.take(320).ifBlank { "电商营销海报，现代简洁设计风格，突出产品卖点" }
    }
}
