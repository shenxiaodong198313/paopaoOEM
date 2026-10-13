package com.paopaoime.aikeyboard.ime.ui.activity

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
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
import com.paopaoime.aikeyboard.ime.ui.activity.MyAssetsActivity
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
import kotlinx.serialization.json.putJsonObject

class MyImagesActivity : AppCompatActivity() {
    private data class LiblibCredential(
        val baseUrl: String,
        val accessKey: String,
        val secretKey: String
    )
    companion object {
        const val EXTRA_SKILL_PROMPT = "skill_prompt"
        const val EXTRA_SKILL_AUTO_SUBMIT = "skill_auto_submit"
        const val EXTRA_TEMPLATE_PROMPT = "template_prompt"
        const val EXTRA_TEMPLATE_TYPE = "template_type"
        const val EXTRA_TEMPLATE_MODEL_ID = "template_model_id"
        const val EXTRA_TEMPLATE_STEPS = "template_steps"
        const val EXTRA_TEMPLATE_SAMPLER = "template_sampler"
        const val EXTRA_TEMPLATE_CFG_SCALE = "template_cfg_scale"
    }

    private data class ImageModelOption(
        val id: String,
        val displayName: String,
        val uriPath: String,
        val templateUuid: String,
        val statusApiUri: String,
        val statusIdKey: String
    )

    // State
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
    private var selectedModelId: String = "star-3-alpha"
    private var selectedResolution: String = "2k" // "1k" or "2k"
    private var selectedRatio: String = "9:16"
    private var selectedCount: Int = 4
    private var selectedSteps: Int = 30
    private var selectedSampler: Int = 0
    private var selectedCfgScale: Double = 1.0
    private var currentTab: Int = 0 // 0: Text2Img, 1: Img2Img, 2: Config

    private var sourceImageUri: Uri? = null

    // Multi-Ref State
    private var currentRefTab: Int = 0 // 0: Single, 1: Multi
    private val subjectUris = mutableListOf<Uri>()
    private var sceneUri: Uri? = null
    private var styleUri: Uri? = null

    private val pickSourceImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            sourceImageUri = uri
            findViewById<ImageView>(R.id.iv_reference_preview).apply {
                setImageURI(uri)
                visibility = View.VISIBLE
            }
        }
    }

    private val pickSubjectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (subjectUris.size < 4) {
                subjectUris.add(uri)
                updateSubjectUI()
            } else {
                Toast.makeText(this, getString(R.string.max_subject_images), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickSceneImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            sceneUri = uri
            updateSceneUI()
        }
    }

    private val pickStyleImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            styleUri = uri
            updateStyleUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_images)

        applyTemplateSelectionIntent()
        initNavigation()
        initTabs()
        initRefTabs()
        initInputs()
        initMultiRefInputs()
        initBottomBar()
        syncModelOptionsFromServer()
        applyEntryIntent()
    }

    private fun currentImageModel(): ImageModelOption {
        return imageModelOptions.firstOrNull { it.id == selectedModelId } ?: imageModelOptions.first()
    }

    private fun syncModelOptionsFromServer() {
        if (!AuthSessionStore.isLoggedIn(this)) return
        lifecycleScope.launch {
            val accessToken = AuthSessionStore.accessToken(this@MyImagesActivity)
            val refreshToken = AuthSessionStore.refreshToken(this@MyImagesActivity)
            if (accessToken.isBlank() || refreshToken.isBlank()) return@launch
            val result = withContext(Dispatchers.IO) {
                AuthApiClient.getLiblibModelCatalog(accessToken, refreshToken)
            }
            if (!result.newAccessToken.isNullOrBlank() || !result.newRefreshToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(
                    this@MyImagesActivity,
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

    private fun applyTemplateSelectionIntent() {
        val templateType = intent?.getStringExtra(EXTRA_TEMPLATE_TYPE)?.trim().orEmpty()
        if (templateType == "img2img") {
            currentTab = 1
        }
        val templateModelId = intent?.getStringExtra(EXTRA_TEMPLATE_MODEL_ID)?.trim().orEmpty()
        if (templateModelId.isNotBlank()) {
            selectedModelId = templateModelId
        }
        val intentSteps = intent?.getIntExtra(EXTRA_TEMPLATE_STEPS, selectedSteps) ?: selectedSteps
        if (intentSteps > 0) {
            selectedSteps = intentSteps
        }
        val intentSampler = intent?.getIntExtra(EXTRA_TEMPLATE_SAMPLER, selectedSampler) ?: selectedSampler
        if (intentSampler > 0) {
            selectedSampler = intentSampler
        }
        val intentCfgScale = intent?.getDoubleExtra(EXTRA_TEMPLATE_CFG_SCALE, selectedCfgScale) ?: selectedCfgScale
        if (intentCfgScale > 0) {
            selectedCfgScale = intentCfgScale
        }
    }

    private fun applyEntryIntent() {
        val prompt = intent?.getStringExtra(EXTRA_TEMPLATE_PROMPT)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: intent?.getStringExtra(EXTRA_SKILL_PROMPT)?.trim().orEmpty()
        if (prompt.isBlank()) return
        val input = if (currentTab == 1) {
            findViewById<EditText>(R.id.et_prompt_img2img)
        } else {
            findViewById<EditText>(R.id.et_prompt_text2img)
        }
        input.setText(prompt)
        input.setSelection(input.text.length)
        if (intent?.getBooleanExtra(EXTRA_SKILL_AUTO_SUBMIT, false) == true) {
            performGeneration()
        }
    }

    private fun initNavigation() {
        findViewById<View>(R.id.iv_back).setOnClickListener { finish() }

        findViewById<View>(R.id.btn_my_assets).setOnClickListener {
            startActivity(Intent(this, MyAssetsActivity::class.java))
        }
        
        // Model Selection
        findViewById<View>(R.id.btn_model_select).setOnClickListener {
            showModelSelectionDialog()
        }
        updateModelUI()
    }

    private fun initTabs() {
        val tabText2Img = findViewById<TextView>(R.id.tab_text2img)
        val tabImg2Img = findViewById<TextView>(R.id.tab_img2img)
        
        val panelText2Img = findViewById<View>(R.id.panel_text2img)
        val panelImg2Img = findViewById<View>(R.id.panel_img2img)

        fun updateTabs() {
            // Reset all
            listOf(tabText2Img, tabImg2Img).forEach {
                it.setTextColor(0xFF999999.toInt())
                it.background = null // or transparent
            }
            panelText2Img.visibility = View.GONE
            panelImg2Img.visibility = View.GONE

            // Activate current
            val activeTab = when (currentTab) {
                0 -> tabText2Img
                else -> tabImg2Img
            }

            activeTab.setTextColor(0xFFFFFFFF.toInt())
            activeTab.setBackgroundResource(R.drawable.bg_pill_tab_selected_gray)

            when (currentTab) {
                0 -> panelText2Img.visibility = View.VISIBLE
                1 -> panelImg2Img.visibility = View.VISIBLE
            }
        }

        tabText2Img.setOnClickListener { currentTab = 0; updateTabs() }
        tabImg2Img.setOnClickListener { currentTab = 1; updateTabs() }

        updateTabs()
    }

    private fun initInputs() {
        findViewById<View>(R.id.btn_add_reference_image).setOnClickListener {
            pickSourceImageLauncher.launch("image/*")
        }
    }

    private fun initRefTabs() {
        val tabSingle = findViewById<TextView>(R.id.tab_single_ref)
        val tabMulti = findViewById<TextView>(R.id.tab_multi_ref)
        val indicator = findViewById<View>(R.id.indicator_ref_tabs)
        val layoutSingle = findViewById<View>(R.id.layout_single_ref)
        val layoutMulti = findViewById<View>(R.id.layout_multi_ref)
        val hint = findViewById<TextView>(R.id.tv_ref_hint)
        
        fun update() {
            if (currentRefTab == 0) {
                tabSingle.setTextColor(0xFFFFFFFF.toInt())
                tabMulti.setTextColor(0xFF999999.toInt())
                layoutSingle.visibility = View.VISIBLE
                layoutMulti.visibility = View.GONE
                hint.visibility = View.GONE
                indicator.animate().translationX(0f).setDuration(200).start()
            } else {
                tabSingle.setTextColor(0xFF999999.toInt())
                tabMulti.setTextColor(0xFFFFFFFF.toInt())
                layoutSingle.visibility = View.GONE
                layoutMulti.visibility = View.VISIBLE
                hint.visibility = View.VISIBLE
                val offset = tabMulti.x - tabSingle.x
                if (offset > 0) {
                     indicator.animate().translationX(offset).setDuration(200).start()
                }
            }
        }
        
        tabSingle.setOnClickListener { currentRefTab = 0; update() }
        tabMulti.setOnClickListener { currentRefTab = 1; update() }
        
        // Initial state
        tabSingle.post { update() }
    }

    private fun initMultiRefInputs() {
        // Subject
        findViewById<View>(R.id.btn_add_subject).setOnClickListener {
             pickSubjectImageLauncher.launch("image/*")
        }
        
        // Scene
        findViewById<View>(R.id.btn_add_scene).setOnClickListener {
            pickSceneImageLauncher.launch("image/*")
        }
        findViewById<View>(R.id.btn_replace_scene).setOnClickListener {
             pickSceneImageLauncher.launch("image/*")
        }
        findViewById<View>(R.id.btn_delete_scene).setOnClickListener {
            sceneUri = null
            updateSceneUI()
        }
        
        // Style
        findViewById<View>(R.id.btn_add_style).setOnClickListener {
            pickStyleImageLauncher.launch("image/*")
        }
        findViewById<View>(R.id.btn_replace_style).setOnClickListener {
            pickStyleImageLauncher.launch("image/*")
        }
        findViewById<View>(R.id.btn_delete_style).setOnClickListener {
            styleUri = null
            updateStyleUI()
        }
    }

    private fun updateSubjectUI() {
        val container = findViewById<android.widget.LinearLayout>(R.id.container_subject_images)
        val addButton = findViewById<View>(R.id.btn_add_subject)
        val countTv = findViewById<TextView>(R.id.tv_subject_count)
        
        countTv.text = "${subjectUris.size}/4"
        
        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until container.childCount) {
            val v = container.getChildAt(i)
            if (v.id != R.id.btn_add_subject) {
                viewsToRemove.add(v)
            }
        }
        viewsToRemove.forEach { container.removeView(it) }
        
        subjectUris.forEachIndexed { index, uri ->
            val size = (80 * resources.displayMetrics.density).toInt()
            val view = ImageView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                    rightMargin = (12 * resources.displayMetrics.density).toInt()
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(uri)
                setOnClickListener {
                     AlertDialog.Builder(this@MyImagesActivity)
                        .setTitle(getString(R.string.delete_image_confirm))
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            subjectUris.removeAt(index)
                            updateSubjectUI()
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            }
            container.addView(view, index)
        }
        
        addButton.visibility = if (subjectUris.size < 4) View.VISIBLE else View.GONE
    }

    private fun updateSceneUI() {
        val btnAdd = findViewById<View>(R.id.btn_add_scene)
        val container = findViewById<View>(R.id.container_scene_preview)
        val iv = findViewById<ImageView>(R.id.iv_scene_preview)
        
        if (sceneUri != null) {
            btnAdd.visibility = View.GONE
            container.visibility = View.VISIBLE
            iv.setImageURI(sceneUri)
        } else {
            btnAdd.visibility = View.VISIBLE
            container.visibility = View.GONE
        }
    }

    private fun updateStyleUI() {
        val btnAdd = findViewById<View>(R.id.btn_add_style)
        val container = findViewById<View>(R.id.container_style_preview)
        val iv = findViewById<ImageView>(R.id.iv_style_preview)
        
        if (styleUri != null) {
            btnAdd.visibility = View.GONE
            container.visibility = View.VISIBLE
            iv.setImageURI(styleUri)
        } else {
            btnAdd.visibility = View.VISIBLE
            container.visibility = View.GONE
        }
    }

    private fun calculateSize(modelId: String, ratio: String, resolution: String): Pair<Int, Int> {
        val is2k = resolution == "2k"
        if (modelId == "qwen-image") {
            // Qwen Recommended Sizes
            // 1:1 -> 1328x1328
            // 3:4 -> 1140x1472
            // 4:3 -> 1472x1140
            // 9:16 -> 928x1664
            // 16:9 -> 1664x928
            var (w, h) = when (ratio) {
                "1:1" -> 1328 to 1328
                "3:4" -> 1140 to 1472
                "4:3" -> 1472 to 1140
                "9:16" -> 928 to 1664
                "16:9" -> 1664 to 928
                // Approximations for others
                "3:2" -> 1472 to 980
                "2:3" -> 980 to 1472
                "21:9" -> 1664 to 712
                else -> 1328 to 1328
            }
            if (is2k) {
                // Max is 2048, scale up slightly but keep within bounds
                w = (w * 1.2).toInt().coerceAtMost(2048)
                h = (h * 1.2).toInt().coerceAtMost(2048)
            }
            return w to h
        } else {
            // Star-3
            // 1k base: 1024x1024
            var (w, h) = when (ratio) {
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
                // Scale up 1.5x for 2k
                w = (w * 1.5).toInt().coerceAtMost(2048)
                h = (h * 1.5).toInt().coerceAtMost(2048)
            }
            return w to h
        }
    }

    private fun initBottomBar() {
        // Params Summary Click -> Show Bottom Sheet
        findViewById<View>(R.id.btn_params_summary).setOnClickListener {
            showParamsBottomSheet()
        }
        updateParamsSummaryUI()

        // Generate Button
        findViewById<Button>(R.id.btn_generate_main).setOnClickListener {
            performGeneration()
        }
    }

    private fun updateModelUI() {
        findViewById<TextView>(R.id.tv_current_model).text = currentImageModel().displayName
        findViewById<ImageView>(R.id.iv_current_model_icon).setImageResource(
            CreativeModelIconResolver.resolveIconRes(currentImageModel().id)
        )
    }

    private fun updateParamsSummaryUI() {
        findViewById<TextView>(R.id.tv_summary_resolution).text = "${selectedResolution}高清"
        findViewById<TextView>(R.id.tv_summary_ratio).text = selectedRatio
        findViewById<TextView>(R.id.tv_summary_count).text = "${selectedCount}张"
    }

    private fun showModelSelectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_model_selection, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val ids = listOf(R.id.model_option_1, R.id.model_option_2, R.id.model_option_3, R.id.model_option_4)
        ids.forEachIndexed { index, viewId ->
            val textView = dialogView.findViewById<TextView>(viewId)
            val option = imageModelOptions.getOrNull(index)
            if (option == null) {
                textView.visibility = View.GONE
            } else {
                textView.visibility = View.VISIBLE
                textView.text = option.displayName
                textView.setTextColor(if (option.id == selectedModelId) Color.WHITE else Color.parseColor("#CCCCCC"))
                textView.compoundDrawablePadding =
                    (16 * resources.displayMetrics.density).toInt()
                textView.setCompoundDrawablesRelative(
                    CreativeModelIconResolver.resolveStartDrawable(this, option.id),
                    null,
                    null,
                    null
                )
                textView.setOnClickListener {
                    selectedModelId = option.id
                    updateModelUI()
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showParamsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_params, null)
        dialog.setContentView(view)

        // Make background transparent to fix white corners
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)

        // Bind Resolution
        val btn1k = view.findViewById<TextView>(R.id.btn_resolution_1k)
        val btn2k = view.findViewById<TextView>(R.id.btn_resolution_2k)
        
        fun updateRes(res: String) {
            val is2k = res == "2k"
            btn2k.setBackgroundResource(if (is2k) R.drawable.bg_option_selected_green else R.drawable.bg_option_normal_dark)
            btn1k.setBackgroundResource(if (!is2k) R.drawable.bg_option_selected_green else R.drawable.bg_option_normal_dark)
        }
        updateRes(selectedResolution)

        btn1k.setOnClickListener { selectedResolution = "1k"; updateRes("1k") }
        btn2k.setOnClickListener { selectedResolution = "2k"; updateRes("2k") }

        // Bind Ratio
        val ratioIds = listOf(
            R.id.btn_ratio_1_1, R.id.btn_ratio_9_16, R.id.btn_ratio_16_9,
            R.id.btn_ratio_4_3, R.id.btn_ratio_3_2, R.id.btn_ratio_2_3,
            R.id.btn_ratio_3_4, R.id.btn_ratio_21_9
        )
        
        fun updateRatio(ratio: String) {
            ratioIds.forEach { id ->
                val btn = view.findViewById<TextView>(id)
                val btnText = btn.text.toString()
                if (btnText == ratio) {
                    btn.setBackgroundResource(R.drawable.bg_option_selected_green)
                } else {
                    btn.setBackgroundResource(R.drawable.bg_option_normal_dark)
                }
            }
            selectedRatio = ratio
        }
        // Need to set click listeners
        ratioIds.forEach { id ->
            val btn = view.findViewById<TextView>(id)
            btn.setOnClickListener { updateRatio(btn.text.toString()) }
        }
        updateRatio(selectedRatio)

        // Bind Count
        val seekbar = view.findViewById<SeekBar>(R.id.seekbar_count)
        val tvCount = view.findViewById<TextView>(R.id.tv_count_value)
        seekbar.progress = selectedCount - 1
        tvCount.text = "${selectedCount}${getString(R.string.unit_sheet)}"

        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val count = progress + 1
                tvCount.text = "${count}${getString(R.string.unit_sheet)}"
                selectedCount = count
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Close
        view.findViewById<View>(R.id.btn_close_params).setOnClickListener {
            updateParamsSummaryUI()
            dialog.dismiss()
        }

        // Generate
        view.findViewById<View>(R.id.btn_generate_action).setOnClickListener {
            updateParamsSummaryUI()
            dialog.dismiss()
            performGeneration()
        }

        dialog.setOnDismissListener {
            updateParamsSummaryUI()
        }

        dialog.show()
    }

    private suspend fun uploadImage(client: LiblibClient, uri: Uri, type: String): String {
        val jpegBytes = LiblibImageUtils.prepareJpegForUpload(this, uri)
        val uploadSig = client.postJson(
            uriPath = "/api/generate/upload/signature",
            requestBody = UploadSignatureRequest(name = type, extension = "jpg"),
            requestSerializer = UploadSignatureRequest.serializer(),
            responseSerializer = LiblibEnvelope.serializer(UploadSignatureData.serializer()),
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
                "x-oss-signature-version" to data.xOssSignatureVersion,
            ),
            fileName = "$type.jpg",
            mimeType = "image/jpeg",
            fileBytes = jpegBytes,
        )
        return "${data.postUrl}/${data.key}"
    }

    private fun performGeneration() {
        val btnGenerate = findViewById<Button>(R.id.btn_generate_main)
        if (!btnGenerate.isEnabled) return

        btnGenerate.isEnabled = false
        btnGenerate.text = getString(R.string.liblib_requesting)

        lifecycleScope.launch {
            runCatching {
                val credential = resolveLiblibCredential()
                if (credential == null) {
                    Toast.makeText(this@MyImagesActivity, getString(R.string.liblib_config_missing), Toast.LENGTH_LONG).show()
                    return@runCatching
                }
                val client = LiblibClient(credential.accessKey, credential.secretKey, credential.baseUrl)
                
                // Common Params
                val prompt = if (currentTab == 0) {
                    findViewById<EditText>(R.id.et_prompt_text2img).text.toString()
                } else {
                    findViewById<EditText>(R.id.et_prompt_img2img).text.toString()
                }
                
                if (prompt.isBlank()) {
                    Toast.makeText(this@MyImagesActivity, "璇峰～鍐?Prompt", Toast.LENGTH_SHORT).show()
                    return@runCatching
                }

                val taskId = if (currentTab == 0) {
                    val modelOption = currentImageModel()
                    // Text 2 Img
                    val params = buildJsonObject {
                        put("prompt", prompt)
                        put("imgCount", selectedCount)
                        put("steps", selectedSteps)
                        if (selectedSampler > 0) {
                            put("sampler", selectedSampler)
                        }
                        put("cfgScale", selectedCfgScale)
                        
                        // Calculate dimensions
                        val (w, h) = calculateSize(modelOption.id, selectedRatio, selectedResolution)
                        
                        if (modelOption.id == "qwen-image") {
                            // Qwen-Image specific
                            put("checkPointId", "75e0be0c93b34dd8baeec9c968013e0c")
                            put("width", w)
                            put("height", h)
                        } else {
                            // Star-3 supports imageSize
                            putJsonObject("imageSize") {
                                put("width", w)
                                put("height", h)
                            }
                        }
                    }
                    
                    val req = GenerateRequest(
                        templateUuid = modelOption.templateUuid,
                        generateParams = params
                    )
                    
                    val resp = client.postJson(
                        uriPath = modelOption.uriPath,
                        requestBody = req,
                        requestSerializer = GenerateRequest.serializer(),
                        responseSerializer = LiblibEnvelope.serializer(GenerateTaskIdData.serializer()),
                    )
                    resp.data?.generateUuid ?: resp.data?.generateUid ?: throw RuntimeException(resp.msg)
                } else {
                    // Img 2 Img (Tab 1)
                    if (currentRefTab == 0) {
                        // Single Image Reference
                        val srcUri = sourceImageUri
                        if (srcUri == null) {
                            Toast.makeText(this@MyImagesActivity, "璇烽€夋嫨鍙傝€冨浘", Toast.LENGTH_SHORT).show()
                            return@runCatching
                        }
                        
                        Toast.makeText(this@MyImagesActivity, "涓婁紶鍙傝€冨浘...", Toast.LENGTH_SHORT).show()
                        val sourceUrl = uploadImage(client, srcUri, "source")

                        val params = buildJsonObject {
                            put("prompt", prompt)
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
                        
                        val req = GenerateRequest(
                            templateUuid = "07e00af4fc464c7ab55ff906f8acf1b7", // Img2Img template
                            generateParams = params
                        )
                        
                        val resp = client.postJson(
                            uriPath = "/api/generate/webui/img2img/ultra",
                            requestBody = req,
                            requestSerializer = GenerateRequest.serializer(),
                            responseSerializer = LiblibEnvelope.serializer(GenerateTaskIdData.serializer()),
                        )
                        resp.data?.generateUuid ?: resp.data?.generateUid ?: throw RuntimeException(resp.msg)
                    } else {
                        // Multi Image Reference (Keling Model)
                        // Note: As per documentation, only Keling supports Multi-Image Reference
                        if (subjectUris.isEmpty() && sceneUri == null && styleUri == null) {
                            Toast.makeText(this@MyImagesActivity, getString(R.string.liblib_uploading_ref_least_one), Toast.LENGTH_SHORT).show()
                            return@runCatching
                        }

                        Toast.makeText(this@MyImagesActivity, getString(R.string.liblib_uploading_ref), Toast.LENGTH_SHORT).show()
                        
                        val referenceImages = mutableListOf<String>()
                        
                        // Upload Subjects
                        subjectUris.forEachIndexed { index, uri ->
                            referenceImages.add(uploadImage(client, uri, "subject_$index"))
                        }
                        
                        // Upload Scene
                        sceneUri?.let {
                            referenceImages.add(uploadImage(client, it, "scene"))
                        }
                        
                        // Upload Style
                        styleUri?.let {
                            referenceImages.add(uploadImage(client, it, "style"))
                        }

                        // Map Aspect Ratio to Keling supported values (1:1, 16:9, 9:16)
                        val kelingRatio = when (selectedRatio) {
                            "16:9" -> "16:9"
                            "9:16" -> "9:16"
                            else -> "1:1" // Default fallback
                        }

                        val params = buildJsonObject {
                            put("prompt", prompt)
                            put("model", "kling-v1-6") // Default
                            put("mode", "std") // Default
                            put("aspectRatio", kelingRatio)
                            put("duration", "5")
                            put("promptMagic", 1)
                            
                            put("referenceImages", kotlinx.serialization.json.JsonArray(
                                referenceImages.map { kotlinx.serialization.json.JsonPrimitive(it) }
                            ))
                        }

                        val req = GenerateRequest(
                            templateUuid = "ca01e798b4424587b0dfdb98b089da05", // Keling Multi-Ref
                            generateParams = params
                        )

                        val resp = client.postJson(
                            uriPath = "/api/generate/video/kling/multiImg2video",
                            requestBody = req,
                            requestSerializer = GenerateRequest.serializer(),
                            responseSerializer = LiblibEnvelope.serializer(GenerateTaskIdData.serializer()),
                        )
                        resp.data?.generateUuid ?: resp.data?.generateUid ?: throw RuntimeException(resp.msg)
                    }
                }
                
                // Start Result Activity
                val selectedImageModel = currentImageModel()
                val statusUri = when {
                    // Keling uses generic status API
                    currentTab == 1 && currentRefTab == 1 -> "/api/generate/status"
                    currentTab == 0 -> selectedImageModel.statusApiUri
                    else -> "/api/generate/webui/status"
                }
                val statusIdKey = when {
                    currentTab == 1 && currentRefTab == 1 -> "generateUuid"
                    currentTab == 0 -> selectedImageModel.statusIdKey
                    else -> "generateUuid"
                }

                val type = if (currentTab == 1 && currentRefTab == 1) 1 else 0

                // Enqueue Worker
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.paopaoime.aikeyboard.ime.worker.GenerationWorker>()
                    .setInputData(androidx.work.workDataOf(
                        "task_id" to taskId,
                        "access_key" to credential.accessKey,
                        "secret_key" to credential.secretKey,
                        "base_url" to credential.baseUrl,
                        "status_api_uri" to statusUri,
                        "status_api_id_key" to statusIdKey,
                        "prompt" to prompt,
                        "model" to selectedImageModel.displayName,
                        "type" to type
                    ))
                    .build()
                
                androidx.work.WorkManager.getInstance(applicationContext).enqueue(workRequest)

                startActivity(Intent(this@MyImagesActivity, GenerationResultActivity::class.java).apply {
                    putExtra("task_id", taskId)
                    putExtra("work_id", workRequest.id.toString())
                    putExtra("access_key", credential.accessKey)
                    putExtra("secret_key", credential.secretKey)
                    putExtra("status_api_uri", statusUri)
                    putExtra("status_api_id_key", statusIdKey)
                    putExtra("prompt", prompt)
                    putExtra("model", selectedImageModel.displayName)
                })

            }.onFailure { e ->
                Toast.makeText(this@MyImagesActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
            
            // Restore button state
            btnGenerate.isEnabled = true
            btnGenerate.text = "${selectedCount} 生成"
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
}


