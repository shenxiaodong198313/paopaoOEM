package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ai.liblib.GenerateRequest
import com.paopaoime.aikeyboard.ime.ai.liblib.GenerateTaskIdData
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibClient
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibEnvelope
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibImageUtils
import com.paopaoime.aikeyboard.ime.ai.liblib.UploadSignatureData
import com.paopaoime.aikeyboard.ime.ai.liblib.UploadSignatureRequest
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.ui.fragment.CreativeRemoteImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class CreativeTemplateGenerateActivity : AppCompatActivity() {
    private data class LiblibCredential(
        val baseUrl: String,
        val accessKey: String,
        val secretKey: String
    )

    private data class ImageModelOption(
        val id: String,
        val displayName: String,
        val uriPath: String,
        val templateUuid: String,
        val statusApiUri: String,
        val statusIdKey: String
    )

    private var imageModelOptions: List<ImageModelOption> = listOf(
        ImageModelOption(
            id = "star-3-alpha",
            displayName = "星流大模型",
            uriPath = "/api/generate/webui/text2img/ultra",
            templateUuid = "5d7e67009b344550bc1aa6ccbfa1d7f4",
            statusApiUri = "/api/generate/webui/status",
            statusIdKey = "generateUuid"
        ),
        ImageModelOption(
            id = "qwen-image",
            displayName = "Qwen-Image",
            uriPath = "/api/generate/webui/text2img",
            templateUuid = "bf085132c7134622895b783b520b39ff",
            statusApiUri = "/api/generate/status",
            statusIdKey = "generateUid"
        )
    )
    private var selectedModelId: String = "qwen-image"
    private var selectedResolution: String = "2k"
    private var selectedRatio: String = "9:16"
    private var selectedCount: Int = 1
    private var selectedSteps: Int = 30
    private var selectedSampler: Int = 0
    private var selectedCfgScale: Double = 1.0
    private var templatePrompt: String = ""
    private var templatePreviewUrl: String = ""
    private var sourceImageUri: Uri? = null

    private val pickSourceImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        sourceImageUri = uri
        findViewById<ImageView>(R.id.iv_user_image_preview).apply {
            visibility = View.VISIBLE
            setImageURI(uri)
        }
        findViewById<View>(R.id.layout_user_image_placeholder).visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_creative_template_generate)

        applyTemplateExtras()
        initNavigation()
        initContent()
        initParamChips()
        syncModelOptionsFromServer()
    }

    private fun applyTemplateExtras() {
        selectedModelId = intent.getStringExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_MODEL_ID)
            ?.trim()
            .orEmpty()
            .ifBlank { selectedModelId }
        templatePrompt = intent.getStringExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_PROMPT).orEmpty()
        templatePreviewUrl = intent.getStringExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_PREVIEW_URL).orEmpty()
        val steps = intent.getIntExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_STEPS, selectedSteps)
        if (steps > 0) {
            selectedSteps = steps
        }
        val sampler = intent.getIntExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_SAMPLER, selectedSampler)
        if (sampler >= 0) {
            selectedSampler = sampler
        }
        val cfgScale = intent.getDoubleExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_CFG_SCALE, selectedCfgScale)
        if (cfgScale > 0) {
            selectedCfgScale = cfgScale
        }
    }

    private fun initNavigation() {
        findViewById<View>(R.id.btn_back_template_generate).setOnClickListener { finish() }
    }

    private fun initContent() {
        CreativeRemoteImageLoader.bind(findViewById(R.id.iv_template_thumbnail), templatePreviewUrl)
        findViewById<View>(R.id.btn_pick_user_image).setOnClickListener {
            pickSourceImageLauncher.launch("image/*")
        }
        findViewById<View>(R.id.btn_ai_expand).setOnClickListener {
            Toast.makeText(this, "AI扩写功能暂未开放", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btn_generate_template).setOnClickListener {
            performGeneration()
        }
        updateModelUI()
        updateParamsUI()
    }

    private fun initParamChips() {
        val listener = View.OnClickListener { showParamsBottomSheet() }
        findViewById<View>(R.id.chip_template_ratio).setOnClickListener(listener)
        findViewById<View>(R.id.chip_template_count).setOnClickListener(listener)
        findViewById<View>(R.id.chip_template_resolution).setOnClickListener(listener)
    }

    private fun syncModelOptionsFromServer() {
        if (!AuthSessionStore.isLoggedIn(this)) return
        lifecycleScope.launch {
            val accessToken = AuthSessionStore.accessToken(this@CreativeTemplateGenerateActivity)
            val refreshToken = AuthSessionStore.refreshToken(this@CreativeTemplateGenerateActivity)
            if (accessToken.isBlank() || refreshToken.isBlank()) return@launch
            val result = withContext(Dispatchers.IO) {
                AuthApiClient.getLiblibModelCatalog(accessToken, refreshToken)
            }
            if (!result.newAccessToken.isNullOrBlank() || !result.newRefreshToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(
                    this@CreativeTemplateGenerateActivity,
                    result.newAccessToken ?: accessToken,
                    result.newRefreshToken
                )
            }
            if (!result.success) return@launch
            val serverOptions = result.items
                .filter { it.available && it.category == "image_text2img" && it.uriPath.isNotBlank() && it.templateUuid.isNotBlank() }
                .sortedBy { it.sortOrder }
                .map {
                    ImageModelOption(
                        id = it.model.ifBlank { it.id },
                        displayName = it.displayName.ifBlank { it.model },
                        uriPath = it.uriPath,
                        templateUuid = it.templateUuid,
                        statusApiUri = it.statusApiUri.ifBlank { "/api/generate/webui/status" },
                        statusIdKey = it.statusIdKey.ifBlank { "generateUuid" }
                    )
                }
            if (serverOptions.isEmpty()) return@launch
            imageModelOptions = serverOptions
            if (imageModelOptions.none { it.id == selectedModelId }) {
                selectedModelId = imageModelOptions.first().id
            }
            updateModelUI()
        }
    }

    private fun updateModelUI() {
        findViewById<TextView>(R.id.tv_template_model_name).text = currentModelDisplayName()
        findViewById<ImageView>(R.id.iv_template_model_icon).setImageResource(
            CreativeModelIconResolver.resolveIconRes(selectedModelId)
        )
    }

    private fun updateParamsUI() {
        findViewById<TextView>(R.id.chip_template_ratio).text = selectedRatio
        findViewById<TextView>(R.id.chip_template_count).text = "${selectedCount}张"
        findViewById<TextView>(R.id.chip_template_resolution).text = "${selectedResolution}高清"
    }

    private fun currentModelDisplayName(): String {
        return currentImageModel().displayName
    }

    private fun currentImageModel(): ImageModelOption {
        return imageModelOptions.firstOrNull { it.id == selectedModelId } ?: imageModelOptions.first()
    }

    private fun calculateSize(modelId: String, ratio: String, resolution: String): Pair<Int, Int> {
        val is2k = resolution == "2k"
        return if (modelId == "qwen-image") {
            var (width, height) = when (ratio) {
                "1:1" -> 1328 to 1328
                "3:4" -> 1140 to 1472
                "4:3" -> 1472 to 1140
                "9:16" -> 928 to 1664
                "16:9" -> 1664 to 928
                "3:2" -> 1472 to 980
                "2:3" -> 980 to 1472
                "21:9" -> 1664 to 712
                else -> 1328 to 1328
            }
            if (is2k) {
                width = (width * 1.2).toInt().coerceAtMost(2048)
                height = (height * 1.2).toInt().coerceAtMost(2048)
            }
            width to height
        } else {
            var (width, height) = when (ratio) {
                "1:1" -> 1024 to 1024
                "3:4" -> 768 to 1024
                "4:3" -> 1024 to 768
                "9:16" -> 720 to 1280
                "16:9" -> 1280 to 720
                "3:2" -> 1024 to 682
                "2:3" -> 682 to 1024
                "21:9" -> 1280 to 548
                else -> 1024 to 1024
            }
            if (is2k) {
                width = (width * 1.5).toInt().coerceAtMost(2048)
                height = (height * 1.5).toInt().coerceAtMost(2048)
            }
            width to height
        }
    }

    private fun showParamsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_params, null)
        dialog.setContentView(view)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        val btn1k = view.findViewById<TextView>(R.id.btn_resolution_1k)
        val btn2k = view.findViewById<TextView>(R.id.btn_resolution_2k)

        fun updateResolution(target: String) {
            selectedResolution = target
            val is2k = target == "2k"
            btn1k.setBackgroundResource(if (is2k) R.drawable.bg_btn_secondary_gray else R.drawable.bg_btn_primary_blue)
            btn2k.setBackgroundResource(if (is2k) R.drawable.bg_btn_primary_blue else R.drawable.bg_btn_secondary_gray)
        }

        btn1k.setOnClickListener { updateResolution("1k") }
        btn2k.setOnClickListener { updateResolution("2k") }
        updateResolution(selectedResolution)

        val ratioIds = listOf(
            R.id.btn_ratio_1_1,
            R.id.btn_ratio_9_16,
            R.id.btn_ratio_16_9,
            R.id.btn_ratio_4_3,
            R.id.btn_ratio_3_2,
            R.id.btn_ratio_2_3,
            R.id.btn_ratio_3_4,
            R.id.btn_ratio_21_9
        )

        fun updateRatio(target: String) {
            selectedRatio = target
            ratioIds.forEach { id ->
                val button = view.findViewById<TextView>(id)
                button.setBackgroundResource(
                    if (button.text.toString() == target) {
                        R.drawable.bg_option_selected_green
                    } else {
                        R.drawable.bg_option_normal_dark
                    }
                )
            }
        }

        ratioIds.forEach { id ->
            val button = view.findViewById<TextView>(id)
            button.setOnClickListener { updateRatio(button.text.toString()) }
        }
        updateRatio(selectedRatio)

        val seekBar = view.findViewById<SeekBar>(R.id.seekbar_count)
        val countView = view.findViewById<TextView>(R.id.tv_count_value)
        seekBar.progress = (selectedCount - 1).coerceAtLeast(0)
        countView.text = "${selectedCount}${getString(R.string.unit_sheet)}"
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedCount = progress + 1
                countView.text = "${selectedCount}${getString(R.string.unit_sheet)}"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        view.findViewById<View>(R.id.btn_close_params).setOnClickListener {
            updateParamsUI()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_generate_action).setOnClickListener {
            updateParamsUI()
            dialog.dismiss()
            performGeneration()
        }
        dialog.setOnDismissListener { updateParamsUI() }
        dialog.show()
    }

    private suspend fun uploadImage(client: LiblibClient, uri: Uri): String {
        val jpegBytes = LiblibImageUtils.prepareJpegForUpload(this, uri)
        val uploadSig = client.postJson(
            uriPath = "/api/generate/upload/signature",
            requestBody = UploadSignatureRequest(name = "source", extension = "jpg"),
            requestSerializer = UploadSignatureRequest.serializer(),
            responseSerializer = LiblibEnvelope.serializer(UploadSignatureData.serializer())
        )
        val data = uploadSig.data ?: throw RuntimeException(getString(R.string.upload_signature_failed))
        client.uploadToOss(
            postUrl = data.postUrl,
            fields = linkedMapOf(
                "key" to data.key,
                "policy" to data.policy,
                "x-oss-date" to data.xOssDate,
                "x-oss-signature" to data.xOssSignature,
                "x-oss-credential" to data.xOssCredential,
                "x-oss-signature-version" to data.xOssSignatureVersion
            ),
            fileName = "source.jpg",
            mimeType = "image/jpeg",
            fileBytes = jpegBytes
        )
        return "${data.postUrl}/${data.key}"
    }

    private fun performGeneration() {
        val userPrompt = findViewById<EditText>(R.id.et_template_prompt_input).text.toString().trim()
        val sourceUri = sourceImageUri
        val finalPrompt = buildFinalPrompt(userPrompt)
        if (finalPrompt.isBlank()) {
            Toast.makeText(this, "模板暂未配置提示词，请先补充描述后再生成", Toast.LENGTH_SHORT).show()
            return
        }

        val generateButton = findViewById<Button>(R.id.btn_generate_template)
        if (!generateButton.isEnabled) return
        generateButton.isEnabled = false
        generateButton.text = getString(R.string.liblib_requesting)

        lifecycleScope.launch {
            runCatching {
                val credential = resolveLiblibCredential()
                if (credential == null) {
                    Toast.makeText(
                        this@CreativeTemplateGenerateActivity,
                        getString(R.string.liblib_config_missing),
                        Toast.LENGTH_LONG
                    ).show()
                    return@runCatching
                }

                val client = LiblibClient(credential.accessKey, credential.secretKey, credential.baseUrl)
                val (taskId, statusApiUri, statusIdKey) = if (sourceUri != null) {
                    Toast.makeText(this@CreativeTemplateGenerateActivity, "正在上传参考图...", Toast.LENGTH_SHORT).show()
                    val sourceUrl = uploadImage(client, sourceUri)
                    val params = buildJsonObject {
                        put("prompt", finalPrompt)
                        put("imgCount", selectedCount)
                        put("sourceImage", sourceUrl)
                        if (selectedSteps > 0) {
                            put("steps", selectedSteps)
                        }
                        if (selectedSampler > 0) {
                            put("sampler", selectedSampler)
                        }
                        put("cfgScale", selectedCfgScale)
                    }
                    val request = GenerateRequest(
                        templateUuid = "07e00af4fc464c7ab55ff906f8acf1b7",
                        generateParams = params
                    )
                    val response = client.postJson(
                        uriPath = "/api/generate/webui/img2img/ultra",
                        requestBody = request,
                        requestSerializer = GenerateRequest.serializer(),
                        responseSerializer = LiblibEnvelope.serializer(GenerateTaskIdData.serializer())
                    )
                    Triple(
                        response.data?.generateUuid ?: response.data?.generateUid ?: throw RuntimeException(response.msg),
                        "/api/generate/webui/status",
                        "generateUuid"
                    )
                } else {
                    val model = currentImageModel()
                    val params = buildJsonObject {
                        put("prompt", finalPrompt)
                        put("imgCount", selectedCount)
                        if (selectedSteps > 0) {
                            put("steps", selectedSteps)
                        }
                        if (selectedSampler > 0) {
                            put("sampler", selectedSampler)
                        }
                        put("cfgScale", selectedCfgScale)

                        val (width, height) = calculateSize(model.id, selectedRatio, selectedResolution)
                        if (model.id == "qwen-image") {
                            put("checkPointId", "75e0be0c93b34dd8baeec9c968013e0c")
                            put("width", width)
                            put("height", height)
                        } else {
                            putJsonObject("imageSize") {
                                put("width", width)
                                put("height", height)
                            }
                        }
                    }
                    val request = GenerateRequest(
                        templateUuid = model.templateUuid,
                        generateParams = params
                    )
                    val response = client.postJson(
                        uriPath = model.uriPath,
                        requestBody = request,
                        requestSerializer = GenerateRequest.serializer(),
                        responseSerializer = LiblibEnvelope.serializer(GenerateTaskIdData.serializer())
                    )
                    Triple(
                        response.data?.generateUuid ?: response.data?.generateUid ?: throw RuntimeException(response.msg),
                        model.statusApiUri,
                        model.statusIdKey
                    )
                }

                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.paopaoime.aikeyboard.ime.worker.GenerationWorker>()
                    .setInputData(
                        androidx.work.workDataOf(
                            "task_id" to taskId,
                            "access_key" to credential.accessKey,
                            "secret_key" to credential.secretKey,
                            "base_url" to credential.baseUrl,
                            "status_api_uri" to statusApiUri,
                            "status_api_id_key" to statusIdKey,
                            "prompt" to finalPrompt,
                            "model" to currentModelDisplayName(),
                            "type" to 0
                        )
                    )
                    .build()
                androidx.work.WorkManager.getInstance(applicationContext).enqueue(workRequest)

                startActivity(
                    Intent(this@CreativeTemplateGenerateActivity, GenerationResultActivity::class.java).apply {
                        putExtra("task_id", taskId)
                        putExtra("work_id", workRequest.id.toString())
                        putExtra("access_key", credential.accessKey)
                        putExtra("secret_key", credential.secretKey)
                        putExtra("status_api_uri", statusApiUri)
                        putExtra("status_api_id_key", statusIdKey)
                        putExtra("prompt", finalPrompt)
                        putExtra("model", currentModelDisplayName())
                    }
                )
            }.onFailure { error ->
                Toast.makeText(
                    this@CreativeTemplateGenerateActivity,
                    "生成失败：${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                error.printStackTrace()
            }
            generateButton.isEnabled = true
            generateButton.text = "生成"
        }
    }

    private fun buildFinalPrompt(userPrompt: String): String {
        val trimmedTemplatePrompt = templatePrompt.trim()
        val trimmedUserPrompt = userPrompt.trim()
        return when {
            trimmedTemplatePrompt.isBlank() -> trimmedUserPrompt
            trimmedUserPrompt.isBlank() -> trimmedTemplatePrompt
            else -> "$trimmedTemplatePrompt\n$trimmedUserPrompt"
        }
    }

    private fun Int.dp(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private suspend fun resolveLiblibCredential(): LiblibCredential? {
        val localPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val cloudPrefs = getSharedPreferences("openclaw_settings", MODE_PRIVATE)
        val localAk = localPrefs.getString("liblib_access_key", "").orEmpty()
        val localSk = localPrefs.getString("liblib_secret_key", "").orEmpty()
        val cloudAkLocal = cloudPrefs.getString("liblib_access_key", "").orEmpty()
        val cloudSkLocal = cloudPrefs.getString("liblib_secret_key", "").orEmpty()
        val localBase = cloudPrefs.getString("liblib_base_url", "").orEmpty()
            .ifBlank { "https://openapi.liblibai.cloud" }

        if (AuthSessionStore.isLoggedIn(this)) {
            val accessToken = AuthSessionStore.accessToken(this)
            val refreshToken = AuthSessionStore.refreshToken(this)
            if (accessToken.isNotBlank() && refreshToken.isNotBlank()) {
                val cloudResult = withContext(Dispatchers.IO) {
                    AuthApiClient.getLiblibCloudConfig(accessToken, refreshToken)
                }
                if (!cloudResult.newAccessToken.isNullOrBlank() || !cloudResult.newRefreshToken.isNullOrBlank()) {
                    AuthSessionStore.updateTokens(
                        this@CreativeTemplateGenerateActivity,
                        cloudResult.newAccessToken ?: accessToken,
                        cloudResult.newRefreshToken
                    )
                }
                if (cloudResult.success) {
                    localPrefs.edit()
                        .putString("liblib_access_key", cloudResult.accessKey)
                        .putString("liblib_secret_key", cloudResult.secretKey)
                        .apply()
                    cloudPrefs.edit()
                        .putString("liblib_access_key", cloudResult.accessKey)
                        .putString("liblib_secret_key", cloudResult.secretKey)
                        .putString("liblib_base_url", cloudResult.baseUrl)
                        .apply()
                    return LiblibCredential(
                        baseUrl = cloudResult.baseUrl.ifBlank { "https://openapi.liblibai.cloud" },
                        accessKey = cloudResult.accessKey,
                        secretKey = cloudResult.secretKey
                    )
                }
            }
        }

        val fallbackAk = if (localAk.isNotBlank()) localAk else cloudAkLocal
        val fallbackSk = if (localSk.isNotBlank()) localSk else cloudSkLocal
        if (fallbackAk.isBlank() || fallbackSk.isBlank()) return null
        return LiblibCredential(
            baseUrl = localBase,
            accessKey = fallbackAk,
            secretKey = fallbackSk
        )
    }
}
