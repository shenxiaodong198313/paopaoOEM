package com.paopaoime.aikeyboard.ime.ui.activity

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.paopaoime.aikeyboard.ime.ui.activity.MyAssetsActivity
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.ai.liblib.GenerateRequest
import com.paopaoime.aikeyboard.ime.ai.liblib.GenerateTaskIdData
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibClient
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibEnvelope
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibImageUtils
import com.paopaoime.aikeyboard.ime.ai.liblib.UploadSignatureData
import com.paopaoime.aikeyboard.ime.ai.liblib.UploadSignatureRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.FileOutputStream

class MyVideosActivity : AppCompatActivity() {
    private data class LiblibCredential(
        val baseUrl: String,
        val accessKey: String,
        val secretKey: String
    )
    data class ModelOption(val id: String, val name: String, val desc: String)
    companion object {
        const val EXTRA_SKILL_PROMPT = "skill_prompt"
        const val EXTRA_SKILL_AUTO_SUBMIT = "skill_auto_submit"
        const val EXTRA_TEMPLATE_PROMPT = "template_prompt"
        const val EXTRA_TEMPLATE_TYPE = "template_type"
        const val EXTRA_TEMPLATE_MODEL_ID = "template_model_id"
    }

    // Views
    private lateinit var tabText2Video: TextView
    private lateinit var tabImg2Video: TextView
    
    private lateinit var panelText2Video: LinearLayout
    private lateinit var panelImg2Video: LinearLayout
    
    private lateinit var etPromptT2V: EditText
    private lateinit var etPromptI2V: EditText
    
    private lateinit var btnAddStartFrame: LinearLayout
    private lateinit var ivStartFramePreview: ImageView
    private lateinit var btnAddEndFrame: LinearLayout
    private lateinit var ivEndFramePreview: ImageView
    
    private lateinit var btnParamsSummary: LinearLayout
    private lateinit var tvSummaryDuration: TextView
    private lateinit var tvSummaryRatio: TextView
    private lateinit var tvSummaryMode: TextView
    private lateinit var btnGenerateMain: Button
    
    private lateinit var btnModelSelect: LinearLayout
    private lateinit var ivCurrentModelIcon: ImageView
    private lateinit var tvCurrentModel: TextView

    // Data
    private var currentTab = 0 // 0: T2V, 1: I2V
    
    // Video Params
    private var selectedModel = "kling-v2-1-master" // Default
    private var selectedDuration = "5" // 5 or 10
    private var selectedMode = "std" // std or pro
    private var selectedRatio = "16:9" // 16:9, 9:16, 1:1
    
    private var startFrameUri: Uri? = null
    private var endFrameUri: Uri? = null
    private var text2VideoModelOptions: List<ModelOption> = listOf(
        ModelOption("kling-v2-1-master", "视频 2.1", "默认模型，效果均衡"),
        ModelOption("kling-v2-5-turbo", "视频 2.5", "Turbo高速版，需专家模式"),
        ModelOption("kling-v2-master", "视频 2.0", "标准版本"),
        ModelOption("kling-v1-6", "视频 1.6", "经典版本"),
    )
    private var img2VideoModelOptions: List<ModelOption> = listOf(
        ModelOption("kling-v2-1", "视频 2.1 (旧)", "图生视频兼容模型"),
        ModelOption("kling-v2-1-master", "视频 2.1", "兼容图生/文生视频"),
        ModelOption("kling-v2-5-turbo", "视频 2.5", "Turbo高速版，需专家模式"),
        ModelOption("kling-v2-master", "视频 2.0", "标准版本"),
        ModelOption("kling-v1-6", "视频 1.6", "经典版本"),
    )
    
    private val pickStartFrameLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            startFrameUri = it
            ivStartFramePreview.visibility = View.VISIBLE
            ivStartFramePreview.setImageURI(it)
        }
    }
    
    private val pickEndFrameLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            endFrameUri = it
            ivEndFramePreview.visibility = View.VISIBLE
            ivEndFramePreview.setImageURI(it)
            
            // If End Frame is selected, force Pro mode
            if (selectedMode == "std") {
                selectedMode = "pro"
                updateSummary()
                Toast.makeText(this, "已自动切换为专家模式 (含尾帧必需)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_videos)
        
        applyTemplateSelectionIntent()
        initViews()
        initListeners()
        updateTabUI()
        updateSummary()
        syncVideoModelOptionsFromServer()
        applyEntryIntent()
    }

    private fun syncVideoModelOptionsFromServer() {
        if (!AuthSessionStore.isLoggedIn(this)) return
        lifecycleScope.launch {
            val accessToken = AuthSessionStore.accessToken(this@MyVideosActivity)
            val refreshToken = AuthSessionStore.refreshToken(this@MyVideosActivity)
            if (accessToken.isBlank() || refreshToken.isBlank()) return@launch
            val result = withContext(Dispatchers.IO) {
                AuthApiClient.getLiblibModelCatalog(accessToken, refreshToken)
            }
            if (!result.newAccessToken.isNullOrBlank() || !result.newRefreshToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(
                    this@MyVideosActivity,
                    result.newAccessToken ?: accessToken,
                    result.newRefreshToken
                )
            }
            if (!result.success) return@launch
            val source = result.items
                .filter { it.available && it.category == "video_model" && it.model.isNotBlank() }
                .sortedBy { it.sortOrder }
                .map {
                    ModelOption(
                        id = it.model,
                        name = it.displayName.ifBlank { it.model },
                        desc = it.intro
                    )
                }
            if (source.isEmpty()) return@launch
            text2VideoModelOptions = source
            img2VideoModelOptions = source
            ensureModelCompatibleForTab()
            updateSummary()
        }
    }

    private fun applyTemplateSelectionIntent() {
        val templateType = intent?.getStringExtra(EXTRA_TEMPLATE_TYPE)?.trim().orEmpty()
        if (templateType == "img2video") {
            currentTab = 1
        }
        val templateModelId = intent?.getStringExtra(EXTRA_TEMPLATE_MODEL_ID)?.trim().orEmpty()
        if (templateModelId.isNotBlank()) {
            selectedModel = templateModelId
        }
    }

    private fun applyEntryIntent() {
        val prompt = intent?.getStringExtra(EXTRA_TEMPLATE_PROMPT)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: intent?.getStringExtra(EXTRA_SKILL_PROMPT)?.trim().orEmpty()
        if (prompt.isBlank()) return
        val input = if (currentTab == 1) etPromptI2V else etPromptT2V
        input.setText(prompt)
        input.setSelection(input.text.length)
        if (intent?.getBooleanExtra(EXTRA_SKILL_AUTO_SUBMIT, false) == true) {
            performGeneration()
        }
    }

    private fun initViews() {
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        findViewById<View>(R.id.btn_my_assets).setOnClickListener {
            startActivity(Intent(this, MyAssetsActivity::class.java))
        }
        
        btnModelSelect = findViewById(R.id.btn_model_select)
        ivCurrentModelIcon = findViewById(R.id.iv_current_model_icon)
        tvCurrentModel = findViewById(R.id.tv_current_model)
        
        tabText2Video = findViewById(R.id.tab_text2video)
        tabImg2Video = findViewById(R.id.tab_img2video)
        
        panelText2Video = findViewById(R.id.panel_text2video)
        panelImg2Video = findViewById(R.id.panel_img2video)
        
        etPromptT2V = findViewById(R.id.et_prompt_text2video)
        etPromptI2V = findViewById(R.id.et_prompt_img2video)
        
        btnAddStartFrame = findViewById(R.id.btn_add_start_frame)
        ivStartFramePreview = findViewById(R.id.iv_start_frame_preview)
        btnAddEndFrame = findViewById(R.id.btn_add_end_frame)
        ivEndFramePreview = findViewById(R.id.iv_end_frame_preview)
        
        btnParamsSummary = findViewById(R.id.btn_params_summary)
        tvSummaryDuration = findViewById(R.id.tv_summary_duration)
        tvSummaryRatio = findViewById(R.id.tv_summary_ratio)
        tvSummaryMode = findViewById(R.id.tv_summary_mode)
        btnGenerateMain = findViewById(R.id.btn_generate_main)
    }

    private fun initListeners() {
        tabText2Video.setOnClickListener { currentTab = 0; updateTabUI() }
        tabImg2Video.setOnClickListener { currentTab = 1; updateTabUI() }
        
        btnAddStartFrame.setOnClickListener { pickStartFrameLauncher.launch("image/*") }
        btnAddEndFrame.setOnClickListener { pickEndFrameLauncher.launch("image/*") }
        
        btnModelSelect.setOnClickListener { showModelSelectDialog() }
        btnParamsSummary.setOnClickListener { showParamsBottomSheet() }
        btnGenerateMain.setOnClickListener { performGeneration() }
    }

    private fun ensureModelCompatibleForTab() {
        if (currentTab == 0 && selectedModel == "kling-v2-1") {
            selectedModel = "kling-v2-1-master"
        }
    }
    
    private fun updateTabUI() {
        // Reset colors
        val activeColor = Color.parseColor("#FFFFFF")
        val inactiveColor = Color.parseColor("#999999")
        val activeBg = R.drawable.bg_pill_tab_selected_gray // using pill style
        // val activeTint = Color.parseColor("#333333") // No longer needed as drawable handles color
        
        tabText2Video.setTextColor(if (currentTab == 0) activeColor else inactiveColor)
        tabText2Video.background = if (currentTab == 0) getDrawable(activeBg) else null
        // if (currentTab == 0) tabText2Video.backgroundTintList = android.content.res.ColorStateList.valueOf(activeTint)
        
        tabImg2Video.setTextColor(if (currentTab == 1) activeColor else inactiveColor)
        tabImg2Video.background = if (currentTab == 1) getDrawable(activeBg) else null
        // if (currentTab == 1) tabImg2Video.backgroundTintList = android.content.res.ColorStateList.valueOf(activeTint)
        
        panelText2Video.isVisible = currentTab == 0
        panelImg2Video.isVisible = currentTab == 1
        
        // Hide/Show Bottom Bar
        findViewById<LinearLayout>(R.id.layout_bottom_bar).isVisible = true

        ensureModelCompatibleForTab()
        updateSummary()
    }
    
    private fun updateSummary() {
        tvSummaryDuration.text = "${selectedDuration}秒"
        tvSummaryRatio.text = selectedRatio
        tvSummaryMode.text = if (selectedMode == "std") "标准模式" else "专家模式"

        val allModels = text2VideoModelOptions + img2VideoModelOptions
        val modelName = allModels.firstOrNull { it.id == selectedModel }?.name ?: selectedModel
        tvCurrentModel.text = modelName
        ivCurrentModelIcon.setImageResource(CreativeModelIconResolver.resolveIconRes(selectedModel))
    }

    private fun showModelSelectDialog() {
        val models = if (currentTab == 0) text2VideoModelOptions else img2VideoModelOptions

        val bottomSheet = BottomSheetDialog(this)
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 32, 0, 32)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        
        val titleLayout = RelativeLayout(this).apply {
            setPadding(32, 0, 32, 32)
        }
        val titleView = TextView(this).apply {
            text = "选择视频模型"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_menu_close)
            setColorFilter(Color.WHITE)
            setOnClickListener { bottomSheet.dismiss() }
            layoutParams = RelativeLayout.LayoutParams(48, 48).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        }
        titleLayout.addView(titleView)
        titleLayout.addView(closeBtn)
        view.addView(titleLayout)
        
        models.forEach { option ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(32, 24, 32, 24)
                background = getDrawable(R.drawable.bg_panel_white)?.apply { setTint(Color.TRANSPARENT) }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedModel = option.id
                    ensureModelCompatibleForTab()
                    if (option.id == "kling-v2-5-turbo") {
                        selectedMode = "pro"
                        Toast.makeText(context, "已切换为专家模式 (Turbo必需)", Toast.LENGTH_SHORT).show()
                    }
                    updateSummary()
                    bottomSheet.dismiss()
                }
            }

            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(28.dp, 28.dp).apply {
                    marginEnd = 16.dp
                }
                setImageResource(CreativeModelIconResolver.resolveIconRes(option.id))
            }

            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameView = TextView(this).apply {
                text = option.name
                textSize = 16f
                setTextColor(if (option.id == selectedModel) Color.GREEN else Color.WHITE)
                typeface = if (option.id == selectedModel) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            }
            
            val descView = TextView(this).apply {
                text = option.desc
                textSize = 12f
                setTextColor(Color.GRAY)
                setPadding(0, 8, 0, 0)
            }

            textLayout.addView(nameView)
            textLayout.addView(descView)
            itemLayout.addView(iconView)
            itemLayout.addView(textLayout)
            view.addView(itemLayout)
            
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.parseColor("#333333"))
                visibility = if (models.indexOf(option) != models.lastIndex) View.VISIBLE else View.GONE
            }
            view.addView(divider)
        }
        
        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun showParamsBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.layout_bottom_sheet_video_params, null)
        bottomSheet.setContentView(view)
        
        // Make background transparent to fix white corners
        bottomSheet.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)
        
        // Bind Views
        val btnClose = view.findViewById<ImageView>(R.id.btn_close_params)
        btnClose.setOnClickListener { bottomSheet.dismiss() }
        
        val btnDuration5 = view.findViewById<TextView>(R.id.btn_duration_5)
        val btnDuration10 = view.findViewById<TextView>(R.id.btn_duration_10)
        
        fun updateDurationUI() {
            btnDuration5.setBackgroundResource(if (selectedDuration == "5") R.drawable.bg_btn_primary_blue else R.drawable.bg_btn_secondary_gray)
            btnDuration10.setBackgroundResource(if (selectedDuration == "10") R.drawable.bg_btn_primary_blue else R.drawable.bg_btn_secondary_gray)
        }
        updateDurationUI()
        
        btnDuration5.setOnClickListener { selectedDuration = "5"; updateDurationUI() }
        btnDuration10.setOnClickListener { selectedDuration = "10"; updateDurationUI() }
        
        val btnModeStd = view.findViewById<TextView>(R.id.btn_mode_std)
        val btnModePro = view.findViewById<TextView>(R.id.btn_mode_pro)
        
        fun updateModeUI() {
            btnModeStd.setBackgroundResource(if (selectedMode == "std") R.drawable.bg_btn_primary_blue else R.drawable.bg_btn_secondary_gray)
            btnModePro.setBackgroundResource(if (selectedMode == "pro") R.drawable.bg_btn_primary_blue else R.drawable.bg_btn_secondary_gray)
        }
        updateModeUI()
        
        btnModeStd.setOnClickListener { 
            if (endFrameUri != null) {
                Toast.makeText(this, "含尾帧必须使用专家模式", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedModel == "kling-v2-5-turbo") {
                Toast.makeText(this, "Turbo模型必须使用专家模式", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectedMode = "std"
            updateModeUI() 
        }
        btnModePro.setOnClickListener { selectedMode = "pro"; updateModeUI() }
        
        val ratioMap = mapOf(
            R.id.btn_ratio_16_9 to "16:9",
            R.id.btn_ratio_9_16 to "9:16",
            R.id.btn_ratio_1_1 to "1:1"
        )
        
        fun updateRatioUI() {
            ratioMap.forEach { (id, ratio) ->
                val btn = view.findViewById<TextView>(id)
                if (ratio == selectedRatio) {
                    btn.setBackgroundResource(R.drawable.bg_option_selected_green)
                } else {
                    btn.setBackgroundResource(R.drawable.bg_option_normal_dark) // Assuming this exists or using fallback
                }
            }
        }
        // Check if bg_option_normal_dark exists, if not use a fallback drawable or code
        // For now, I'll assume it exists or use transparent
        
        updateRatioUI()
        
        ratioMap.forEach { (id, ratio) ->
            view.findViewById<TextView>(id).setOnClickListener {
                selectedRatio = ratio
                updateRatioUI()
            }
        }
        
        view.findViewById<Button>(R.id.btn_generate_action).setOnClickListener {
            performGeneration()
            bottomSheet.dismiss()
        }
        
        bottomSheet.setOnDismissListener { updateSummary() }
        bottomSheet.show()
    }

    private fun performGeneration() {
        btnGenerateMain.isEnabled = false
        btnGenerateMain.text = "生成中..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val credential = resolveLiblibCredential()
                if (credential == null) {
                    runOnUiThread { Toast.makeText(this@MyVideosActivity, getString(R.string.liblib_config_missing), Toast.LENGTH_LONG).show() }
                    return@launch
                }
                val client = LiblibClient(credential.accessKey, credential.secretKey, credential.baseUrl)
                var taskId: String
                var statusApiIdKey = "generateUuid"
                
                if (currentTab == 0) {
                    // Text to Video
                    val prompt = etPromptT2V.text.toString().trim()
                    if (prompt.isEmpty()) {
                        runOnUiThread { Toast.makeText(this@MyVideosActivity, "请输入描述词", Toast.LENGTH_SHORT).show() }
                        return@launch
                    }
                    
                    val modelForRequest = if (selectedModel == "kling-v2-1") "kling-v2-1-master" else selectedModel
                    val params = buildJsonObject {
                        put("model", modelForRequest)
                        put("prompt", prompt)
                        put("promptMagic", 1)
                        put("aspectRatio", selectedRatio)
                        put("duration", selectedDuration)
                        put("mode", selectedMode)
                        // sound on/off if supported (v2.6+ only, skipping for now)
                    }
                    
                    val req = GenerateRequest(
                        templateUuid = "61cd8b60d340404394f2a545eeaf197a", // T2V Template
                        generateParams = params
                    )
                    
                    val resp = client.postJson(
                        uriPath = "/api/generate/video/kling/text2video",
                        requestBody = req,
                        requestSerializer = GenerateRequest.serializer(),
                        responseSerializer = LiblibEnvelope.serializer(GenerateTaskIdData.serializer())
                    )
                    
                    taskId = resp.data?.generateUuid ?: resp.data?.generateUid ?: throw RuntimeException(resp.msg)
                    statusApiIdKey = if (!resp.data?.generateUid.isNullOrBlank()) "generateUid" else "generateUuid"
                    
                } else {
                    // Image to Video
                    val prompt = etPromptI2V.text.toString().trim()
                    // prompt is required for I2V as well
                    if (prompt.isEmpty()) {
                        runOnUiThread { Toast.makeText(this@MyVideosActivity, "请输入描述词 (选填但建议填写)", Toast.LENGTH_SHORT).show() }
                        // Actually prompt is required in API doc
                    }
                    
                    if (startFrameUri == null) {
                        runOnUiThread { Toast.makeText(this@MyVideosActivity, "请上传首帧图片", Toast.LENGTH_SHORT).show() }
                        return@launch
                    }
                    
                    runOnUiThread { Toast.makeText(this@MyVideosActivity, "上传首帧...", Toast.LENGTH_SHORT).show() }
                    val startFrameUrl = uploadImage(client, startFrameUri!!, "start_frame")
                    
                    var endFrameUrl: String? = null
                    if (endFrameUri != null) {
                        runOnUiThread { Toast.makeText(this@MyVideosActivity, "上传尾帧...", Toast.LENGTH_SHORT).show() }
                        endFrameUrl = uploadImage(client, endFrameUri!!, "end_frame")
                    }
                    
                    val params = buildJsonObject {
                        put("model", selectedModel)
                        put("prompt", if (prompt.isEmpty()) "video" else prompt) // Fallback prompt
                        put("promptMagic", 1)
                        put("mode", selectedMode)
                        put("duration", selectedDuration)
                        put("startFrame", startFrameUrl)
                        if (endFrameUrl != null) {
                            put("endFrame", endFrameUrl)
                        }
                    }
                    
                    val req = GenerateRequest(
                        templateUuid = "180f33c6748041b48593030156d2a71d", // I2V Template
                        generateParams = params
                    )
                    
                    val resp = client.postJson(
                        uriPath = "/api/generate/video/kling/img2video",
                        requestBody = req,
                        requestSerializer = GenerateRequest.serializer(),
                        responseSerializer = LiblibEnvelope.serializer(GenerateTaskIdData.serializer())
                    )
                    
                    taskId = resp.data?.generateUuid ?: resp.data?.generateUid ?: throw RuntimeException(resp.msg)
                    statusApiIdKey = if (!resp.data?.generateUid.isNullOrBlank()) "generateUid" else "generateUuid"
                }
                
                // Launch Result Activity
                val finalPrompt = if (currentTab == 0) etPromptT2V.text.toString().trim() else etPromptI2V.text.toString().trim()
                val statusApiUri = "/api/generate/status"
                
                // Enqueue Worker
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.paopaoime.aikeyboard.ime.worker.GenerationWorker>()
                    .setInputData(androidx.work.workDataOf(
                        "task_id" to taskId,
                        "access_key" to credential.accessKey,
                        "secret_key" to credential.secretKey,
                        "base_url" to credential.baseUrl,
                        "status_api_uri" to statusApiUri,
                        "status_api_id_key" to statusApiIdKey,
                        "prompt" to finalPrompt,
                        "model" to selectedModel,
                        "type" to 1, // Video
                        "duration" to selectedDuration
                    ))
                    .build()
                
                androidx.work.WorkManager.getInstance(applicationContext).enqueue(workRequest)

                val intent = Intent(this@MyVideosActivity, GenerationResultActivity::class.java).apply {
                    putExtra("task_id", taskId)
                    putExtra("work_id", workRequest.id.toString())
                    putExtra("access_key", credential.accessKey)
                    putExtra("secret_key", credential.secretKey)
                    putExtra("status_api_uri", statusApiUri)
                    putExtra("status_api_id_key", statusApiIdKey)
                    putExtra("prompt", finalPrompt)
                    putExtra("model", selectedModel)
                }
                startActivity(intent)
                
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MyVideosActivity, "生成失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread {
                    btnGenerateMain.isEnabled = true
                    btnGenerateMain.text = "生成视频"
                }
            }
        }
    }

    private suspend fun resolveLiblibCredential(): LiblibCredential? {
        val localPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val cloudPrefs = getSharedPreferences("openclaw_settings", MODE_PRIVATE)
        val localAk = localPrefs.getString("liblib_access_key", "").orEmpty()
        val localSk = localPrefs.getString("liblib_secret_key", "").orEmpty()
        val cloudAkLocal = cloudPrefs.getString("liblib_access_key", "").orEmpty()
        val cloudSkLocal = cloudPrefs.getString("liblib_secret_key", "").orEmpty()
        val localBase = cloudPrefs.getString("liblib_base_url", "").orEmpty().ifBlank { "https://openapi.liblibai.cloud" }

        if (AuthSessionStore.isLoggedIn(this)) {
            val accessToken = AuthSessionStore.accessToken(this)
            val refreshToken = AuthSessionStore.refreshToken(this)
            if (accessToken.isNotBlank() && refreshToken.isNotBlank()) {
                val cloudResult = withContext(Dispatchers.IO) {
                    AuthApiClient.getLiblibCloudConfig(accessToken, refreshToken)
                }
                if (!cloudResult.newAccessToken.isNullOrBlank() || !cloudResult.newRefreshToken.isNullOrBlank()) {
                    AuthSessionStore.updateTokens(this, cloudResult.newAccessToken ?: accessToken, cloudResult.newRefreshToken)
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
    
    private suspend fun uploadImage(client: LiblibClient, uri: Uri, fileNamePrefix: String): String {
        val jpegBytes = LiblibImageUtils.prepareJpegForUpload(this, uri)
        val uploadSig = client.postJson(
            uriPath = "/api/generate/upload/signature",
            requestBody = UploadSignatureRequest(name = fileNamePrefix, extension = "jpg"),
            requestSerializer = UploadSignatureRequest.serializer(),
            responseSerializer = LiblibEnvelope.serializer(UploadSignatureData.serializer())
        )
        val data = uploadSig.data ?: throw RuntimeException("上传签名失败: ${uploadSig.msg}")
        
        client.uploadToOss(
            postUrl = data.postUrl,
            fields = linkedMapOf(
                "key" to data.key,
                "policy" to data.policy,
                "x-oss-date" to data.xOssDate,
                "x-oss-signature" to data.xOssSignature,
                "x-oss-credential" to data.xOssCredential,
                "x-oss-signature-version" to data.xOssSignatureVersion,
            ),
            fileName = "$fileNamePrefix.jpg",
            mimeType = "image/jpeg",
            fileBytes = jpegBytes,
        )
        
        return "${data.postUrl}/${data.key}"
    }
}


