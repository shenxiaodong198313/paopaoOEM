package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibClient
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibEnvelope
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibPointsLedger
import com.paopaoime.aikeyboard.ime.ai.liblib.StatusData
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import com.paopaoime.aikeyboard.ime.ui.activity.MyAssetsActivity
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GenerationResultActivity : AppCompatActivity() {

    private var taskId: String? = null
    private var accessKey: String? = null
    private var secretKey: String? = null
    private var statusApiUri: String? = null
    private var statusApiIdKey: String? = null
    private var prompt: String = ""
    private var model: String = ""
    private val generatedImages = ArrayList<String>()
    private val generatedVideos = ArrayList<String>()

    private var isPolling = false
    private var loadingAnimJob: kotlinx.coroutines.Job? = null
    private var displayPercent = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generation_result)

        taskId = intent.getStringExtra("task_id")
        accessKey = intent.getStringExtra("access_key")
        secretKey = intent.getStringExtra("secret_key")
        
        
        statusApiUri = intent.getStringExtra("status_api_uri")
        statusApiIdKey = intent.getStringExtra("status_api_id_key")
        prompt = intent.getStringExtra("prompt") ?: ""
        model = intent.getStringExtra("model") ?: ""

        findViewById<ImageView>(R.id.iv_close_result).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.btn_reedit).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.btn_continue).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.btn_assets).setOnClickListener {
            startActivity(Intent(this, MyAssetsActivity::class.java))
        }

        findViewById<ImageView>(R.id.btn_expand).setOnClickListener {
            if (generatedImages.isNotEmpty()) {
                val intent = Intent(this, ImageBrowserActivity::class.java)
                intent.putStringArrayListExtra("image_urls", generatedImages)
                if (generatedVideos.isNotEmpty()) {
                    intent.putStringArrayListExtra("video_urls", generatedVideos)
                }
                intent.putExtra("access_key", accessKey)
                intent.putExtra("secret_key", secretKey)
                startActivity(intent)
            }
        }

        val workId = intent.getStringExtra("work_id")
        
        startLoadingAnim()
        
        if (workId != null) {
            observeWork(workId)
        } else if (taskId != null && accessKey != null && secretKey != null) {
            startPolling()
        } else {
            findViewById<TextView>(R.id.tv_loading_status).text = getString(R.string.generation_param_error)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loadingAnimJob?.cancel()
    }

    private fun playVideoInApp(videoUrl: String) {
        val uri = when {
            videoUrl.startsWith("file://") -> android.net.Uri.parse(videoUrl)
            videoUrl.startsWith("/") -> android.net.Uri.fromFile(File(videoUrl))
            else -> android.net.Uri.parse(videoUrl)
        }

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(this)

        val videoView = VideoView(this).apply {
            setVideoURI(uri)
        }

        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        val closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_menu_close)
            setColorFilter(android.graphics.Color.WHITE)
            background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
                paint.color = android.graphics.Color.parseColor("#80000000")
            }
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            setOnClickListener { dialog.dismiss() }
        }

        root.addView(
            videoView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            closeBtn,
            FrameLayout.LayoutParams(
                (44 * resources.displayMetrics.density).toInt(),
                (44 * resources.displayMetrics.density).toInt()
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                val margin = (16 * resources.displayMetrics.density).toInt()
                topMargin = margin
                marginEnd = margin
            }
        )

        dialog.setContentView(root)
        dialog.setOnDismissListener {
            videoView.stopPlayback()
        }
        dialog.show()

        videoView.setOnPreparedListener {
            mediaController.show(0)
            videoView.start()
        }
        videoView.setOnErrorListener { _, _, _ ->
            android.widget.Toast.makeText(this, "无法播放视频", android.widget.Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            true
        }
    }

    private fun startLoadingAnim() {
        loadingAnimJob?.cancel()
        loadingAnimJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                updateLoadingProgress((displayPercent + 1).coerceAtMost(95), null)
                delay(700)
            }
        }
    }

    private fun observeWork(workId: String) {
        val loadingLayout = findViewById<LinearLayout>(R.id.layout_loading)
        val statusText = findViewById<TextView>(R.id.tv_loading_status)
        val resultImage = findViewById<ImageView>(R.id.iv_result_image)
        val btnPlay = findViewById<ImageView>(R.id.btn_play_video)

        androidx.work.WorkManager.getInstance(this).getWorkInfoByIdLiveData(java.util.UUID.fromString(workId))
            .observe(this) { workInfo ->
                if (workInfo == null) return@observe
                
                // Try to extract taskId from progress or output
                val progressId = workInfo.progress.getString("uuid") ?: workInfo.progress.getString("task_id")
                val outputId = workInfo.outputData.getString("uuid") ?: workInfo.outputData.getString("task_id")
                val foundId = progressId ?: outputId
                
                if (!foundId.isNullOrEmpty() && taskId == null) {
                    taskId = foundId
                }
                
                when (workInfo.state) {
                    androidx.work.WorkInfo.State.SUCCEEDED -> {
                        val success = workInfo.outputData.getBoolean("success", false)
                        if (success) {
                            val snapshotTaskId = taskId.orEmpty()
                            if (snapshotTaskId.isNotBlank() && !LiblibPointsLedger.hasRecord(this@GenerationResultActivity, snapshotTaskId)) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    backfillLedgerFromStatus(snapshotTaskId)
                                }
                            }
                            val localPaths = workInfo.outputData.getStringArray("local_paths")
                            if (!localPaths.isNullOrEmpty()) {
                                val rendered = renderFromLocalPaths(localPaths)
                                if (rendered) {
                                    updateLoadingProgress(100, "生成完成")
                                    loadingLayout.visibility = View.GONE
                                    loadingAnimJob?.cancel()
                                } else {
                                    statusText.text = getString(R.string.generation_failed)
                                }
                            } else {
                                statusText.text = getString(R.string.generation_failed)
                            }
                        } else {
                            statusText.text = getString(R.string.generation_failed)
                        }
                    }
                    androidx.work.WorkInfo.State.FAILED -> {
                        statusText.text = getString(R.string.generation_failed)
                    }
                    androidx.work.WorkInfo.State.RUNNING -> {
                        val progressPercent = workInfo.progress.getInt("progress", -1)
                        if (progressPercent >= 0) {
                            updateLoadingProgress(progressPercent.coerceIn(displayPercent, 99), null)
                        }
                        if (!isPolling) {
                            val debugInfo = StringBuilder()
                            debugInfo.append("WorkState: RUNNING\n")
                            if (workInfo.progress.keyValueMap.isNotEmpty()) {
                                debugInfo.append("Prog: ${workInfo.progress.keyValueMap}\n")
                            }
                            if (taskId != null) {
                                debugInfo.append("TaskId: $taskId\n(Polling not started?)")
                            } else {
                                debugInfo.append("No TaskId yet...")
                            }
                            statusText.text = getString(R.string.generating_wait)
                        }
                    }
                    else -> {
                        if (!isPolling) {
                            statusText.text = getString(R.string.generating_wait)
                        }
                    }
                }
            }
    }

    private suspend fun backfillLedgerFromStatus(snapshotTaskId: String) {
        val ak = accessKey.orEmpty()
        val sk = secretKey.orEmpty()
        if (ak.isBlank() || sk.isBlank()) return
        runCatching {
            val client = LiblibClient(ak, sk)
            val resp = client.postJson(
                uriPath = statusApiUri ?: "/api/generate/webui/status",
                requestBody = buildJsonObject {
                    put(statusApiIdKey ?: "generateUuid", snapshotTaskId)
                    put("generateUuid", snapshotTaskId)
                    put("generateUid", snapshotTaskId)
                },
                requestSerializer = JsonObject.serializer(),
                responseSerializer = LiblibEnvelope.serializer(StatusData.serializer())
            )
            val data = resp.data ?: return
            if (data.generateStatus != 5) return
            recordPointsIfNeeded(data)
        }
    }

    private fun recordPointsIfNeeded(data: StatusData) {
        val resolvedTaskId = data.generateUuid.ifBlank { taskId.orEmpty() }
        if (resolvedTaskId.isBlank()) return
        val hasLocalRecord = LiblibPointsLedger.hasRecord(this, resolvedTaskId)
        val consumeType = if (data.videos.isNotEmpty()) "视频" else "生图"
        if (!hasLocalRecord) {
            LiblibPointsLedger.appendRecord(
                context = this,
                consumeType = consumeType,
                taskDetail = prompt,
                pointsCost = data.pointsCost,
                taskId = resolvedTaskId,
                accountBalance = data.accountBalance
            )
        }
        uploadLiblibUsageToCloud(
            resolvedTaskId = resolvedTaskId,
            consumeType = consumeType,
            modelName = model,
            pointsCost = data.pointsCost
        )
    }

    private fun uploadLiblibUsageToCloud(
        resolvedTaskId: String,
        consumeType: String,
        modelName: String,
        pointsCost: Int
    ) {
        val accessToken = AuthSessionStore.accessToken(this)
        val refreshToken = AuthSessionStore.refreshToken(this)
        if (accessToken.isBlank() || refreshToken.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val owner = resolveLiblibAccountOwner()
            val event = AuthApiClient.TokenUsageEvent(
                clientEventId = sha256("liblib|${owner.ownerId}|$resolvedTaskId"),
                provider = "liblib",
                model = modelName,
                bizType = if (consumeType == "视频") "video" else "image",
                bizId = resolvedTaskId,
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
            val result = AuthApiClient.uploadTokenUsageEvents(
                accessToken = accessToken,
                refreshToken = refreshToken,
                events = listOf(event)
            )
            if (!result.success) {
                android.util.Log.e("GenerationResultActivity", "uploadLiblibUsageToCloudRejected taskId=$resolvedTaskId msg=${result.message}")
            } else {
                android.util.Log.i(
                    "GenerationResultActivity",
                    "uploadLiblibUsageToCloudSuccess taskId=$resolvedTaskId accepted=${result.acceptedCount} duplicated=${result.duplicatedCount}"
                )
            }
            val nextAccessToken = result.newAccessToken
            val nextRefreshToken = result.newRefreshToken
            if (!nextAccessToken.isNullOrBlank() || !nextRefreshToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(
                    this@GenerationResultActivity,
                    nextAccessToken ?: accessToken,
                    nextRefreshToken ?: refreshToken
                )
            }
        }
    }

    private data class AccountOwner(
        val ownerType: String,
        val ownerId: String,
        val ownerName: String
    )

    private fun resolveLiblibAccountOwner(): AccountOwner {
        val prefs = getSharedPreferences("openclaw_settings", MODE_PRIVATE)
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

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun formatIsoTimestamp(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(timestamp))
    }

    private fun startPolling() {
        if (isPolling) return
        isPolling = true
        
        val client = LiblibClient(accessKey!!, secretKey!!)
        val loadingLayout = findViewById<LinearLayout>(R.id.layout_loading)
        val resultImage = findViewById<ImageView>(R.id.iv_result_image)
        val statusText = findViewById<TextView>(R.id.tv_loading_status)
        
        val startTime = System.currentTimeMillis()

        CoroutineScope(Dispatchers.Main).launch {
            var finished = false
            var lastPercent = 0f
            
            repeat(150) {
                if (finished) return@launch
                
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                // DEBUG: Show status
                // statusText.text = "TaskId: $taskId\nElapsed: ${elapsedSeconds}s\nWait..."
                
                try {
                     val resp = withContext(Dispatchers.IO) {
                        client.postJson(
                            uriPath = statusApiUri ?: "/api/generate/webui/status",
                            requestBody = buildJsonObject {
                                put(statusApiIdKey ?: "generateUuid", taskId)
                                put("generateUuid", taskId)
                                put("generateUid", taskId)
                            },
                            requestSerializer = JsonObject.serializer(),
                            responseSerializer = LiblibEnvelope.serializer(StatusData.serializer()),
                        )
                    }
                    val data = resp.data
                    // DEBUG: Show data
                    if (data != null) {
                        // Ensure percent never goes back
                        if (data.percentCompleted > lastPercent) {
                            lastPercent = data.percentCompleted
                        }
                        val apiPercent = (lastPercent * 100).toInt().coerceIn(0, 100)
                        val elapsedPercent = ((elapsedSeconds.toInt() * 100) / 180).coerceIn(0, 95)
                        updateLoadingProgress(maxOf(apiPercent, elapsedPercent), "正在生成中…（可后台等待）")
                        
                        when (data.generateStatus) {
                            5 -> { // Success
                                recordPointsIfNeeded(data)
                                val images = data.images.mapNotNull { it.imageUrl }.toMutableList()
                                
                                // Prepare video list aligned with images
                                val videoList = ArrayList<String>()
                                repeat(images.size) { videoList.add("") }
                                
                                data.videos.forEach { video ->
                                    if (video.coverPath.isNotEmpty()) {
                                        images.add(video.coverPath)
                                        videoList.add(video.videoUrl)
                                    }
                                }
                                
                                generatedImages.clear()
                                generatedImages.addAll(images)
                                
                                generatedVideos.clear()
                                generatedVideos.addAll(videoList)

                                val url = images.firstOrNull()
                                if (url != null) {
                                    val bytes = withContext(Dispatchers.IO) { 
                                        runCatching { client.downloadBytes(url) }.getOrNull() 
                                    }
                                    if (bytes != null) {
                                        val bmp = withContext(Dispatchers.IO) { 
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) 
                                        }
                                        if (bmp != null) {
                                            updateLoadingProgress(100, "生成完成")
                                            resultImage.setImageBitmap(bmp)
                                            findViewById<View>(R.id.layout_result_content).visibility = View.VISIBLE
                                            loadingLayout.visibility = View.GONE
                                            loadingAnimJob?.cancel()
                                            
                                            // Update Count and Expand visibility
                                            if (images.size > 1) {
                                                findViewById<TextView>(R.id.tv_image_count).apply {
                                                    text = "${images.size}张"
                                                    visibility = View.VISIBLE
                                                }
                                                findViewById<View>(R.id.btn_expand).visibility = View.VISIBLE
                                            } else {
                                                findViewById<View>(R.id.tv_image_count).visibility = View.GONE
                                                findViewById<View>(R.id.btn_expand).visibility = View.VISIBLE // Always allow expand to view details/play
                                            }
                                            
                                            // Show Play button if first item is video
                                            val firstVideoUrl = generatedVideos.getOrNull(0)
                                            val btnPlay = findViewById<View>(R.id.btn_play_video)
                                            if (!firstVideoUrl.isNullOrEmpty()) {
                                                btnPlay.visibility = View.VISIBLE
                                                btnPlay.setOnClickListener {
                                                    playVideoInApp(firstVideoUrl)
                                                }
                                                // Make image also play video
                                                resultImage.setOnClickListener {
                                                    btnPlay.performClick()
                                                }
                                            } else {
                                                btnPlay.visibility = View.GONE
                                                // Make image expand if no video
                                                resultImage.setOnClickListener {
                                                    findViewById<View>(R.id.btn_expand).performClick()
                                                }
                                            }
                                            
                                            finished = true
                                        } else {
                                            statusText.text = getString(R.string.generation_failed) + ": 预览解码失败"
                                            finished = true
                                        }
                                    } else {
                                        statusText.text = getString(R.string.generation_failed) + ": 图片下载失败"
                                        finished = true
                                    }
                                } else {
                                    statusText.text = "生成完成，等待本地结果"
                                    finished = true
                                }
                            }
                            6, 7 -> { // Fail
                                statusText.text = getString(R.string.generation_failed) + ": ${data.generateMsg ?: "未知错误"}"
                                finished = true
                            }
                            else -> {
                                // Still running
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val msg = e.message ?: e.toString()
                    statusText.text = "Error:\n${e::class.simpleName}\n$msg"
                }

                if (!finished) delay(2000)
            }
            
            if (!finished) {
                statusText.text = getString(R.string.generation_timeout)
            }
        }
    }

    private fun updateLoadingProgress(percent: Int, message: String?) {
        val bounded = percent.coerceIn(0, 100)
        if (bounded < displayPercent) return
        displayPercent = bounded
        findViewById<ProgressBar>(R.id.progress_loading).progress = bounded
        findViewById<TextView>(R.id.tv_loading_percent).text = "$bounded%"
        if (!message.isNullOrBlank()) {
            findViewById<TextView>(R.id.tv_loading_status).text = message
        }
    }

    private fun renderFromLocalPaths(localPaths: Array<String>): Boolean {
        val firstPath = localPaths.firstOrNull() ?: return false
        val resultImage = findViewById<ImageView>(R.id.iv_result_image)
        val btnPlay = findViewById<ImageView>(R.id.btn_play_video)
        val imageCount = findViewById<TextView>(R.id.tv_image_count)
        val expand = findViewById<View>(R.id.btn_expand)
        val resultLayout = findViewById<View>(R.id.layout_result_content)

        val isVideo = firstPath.endsWith(".mp4", ignoreCase = true)
        if (isVideo) {
            generatedVideos.clear()
            generatedVideos.addAll(localPaths.map { "file://$it" })
            generatedImages.clear()
            generatedImages.addAll(localPaths.map { "file://$it" })
            btnPlay.visibility = View.VISIBLE
            btnPlay.setOnClickListener { playVideoInApp(generatedVideos[0]) }
            imageCount.visibility = View.GONE
            expand.visibility = View.VISIBLE
            resultLayout.visibility = View.VISIBLE
            return true
        }

        generatedImages.clear()
        generatedImages.addAll(localPaths.map { "file://$it" })
        generatedVideos.clear()
        generatedVideos.addAll(List(localPaths.size) { "" })
        val bitmap = BitmapFactory.decodeFile(firstPath) ?: return false
        resultImage.setImageBitmap(bitmap)
        btnPlay.visibility = View.GONE
        if (localPaths.size > 1) {
            imageCount.text = "${localPaths.size}张"
            imageCount.visibility = View.VISIBLE
        } else {
            imageCount.visibility = View.GONE
        }
        expand.visibility = View.VISIBLE
        resultLayout.visibility = View.VISIBLE
        return true
    }
}

