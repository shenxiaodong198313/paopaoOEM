package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.fragment.CreativeRemoteImageLoader

class CreativeTemplateDetailActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_TEMPLATE_ID = "creative_template_id"
        const val EXTRA_TEMPLATE_NAME = "creative_template_name"
        const val EXTRA_TEMPLATE_PREVIEW_URL = "creative_template_preview_url"
        const val EXTRA_TEMPLATE_TYPE = "creative_template_type"
        const val EXTRA_TEMPLATE_MODEL_ID = "creative_template_model_id"
        const val EXTRA_TEMPLATE_PROMPT = "creative_template_prompt"
        const val EXTRA_TEMPLATE_STEPS = "creative_template_steps"
        const val EXTRA_TEMPLATE_SAMPLER = "creative_template_sampler"
        const val EXTRA_TEMPLATE_CFG_SCALE = "creative_template_cfg_scale"

        private const val DEFAULT_AUTHOR_NAME = "泡泡官方"
        private const val DEFAULT_TEMPLATE_USAGE = "12.3万人做同款"
    }

    private val templateId: String by lazy { intent.getStringExtra(EXTRA_TEMPLATE_ID).orEmpty() }
    private val templateName: String by lazy { intent.getStringExtra(EXTRA_TEMPLATE_NAME).orEmpty() }
    private val templatePreviewUrl: String by lazy { intent.getStringExtra(EXTRA_TEMPLATE_PREVIEW_URL).orEmpty() }
    private val templateType: String by lazy { intent.getStringExtra(EXTRA_TEMPLATE_TYPE).orEmpty() }
    private val templateModelId: String by lazy { intent.getStringExtra(EXTRA_TEMPLATE_MODEL_ID).orEmpty() }
    private val templatePrompt: String by lazy { intent.getStringExtra(EXTRA_TEMPLATE_PROMPT).orEmpty() }
    private val templateSteps: Int by lazy { intent.getIntExtra(EXTRA_TEMPLATE_STEPS, 0) }
    private val templateSampler: Int by lazy { intent.getIntExtra(EXTRA_TEMPLATE_SAMPLER, 0) }
    private val templateCfgScale: Double by lazy { intent.getDoubleExtra(EXTRA_TEMPLATE_CFG_SCALE, 1.0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_creative_template_detail)

        findViewById<View>(R.id.btn_back_template_detail).setOnClickListener { finish() }
        val shareClickListener = View.OnClickListener {
            Toast.makeText(this, "分享功能敬请期待", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_share_template_detail).setOnClickListener(shareClickListener)
        findViewById<View>(R.id.btn_share_template_action).setOnClickListener(shareClickListener)
        findViewById<View>(R.id.btn_use_template).setOnClickListener {
            openTemplateWorkflow()
        }

        findViewById<TextView>(R.id.tv_template_usage).text = DEFAULT_TEMPLATE_USAGE
        findViewById<TextView>(R.id.tv_template_title).text = templateName.ifBlank { "创作模板" }
        findViewById<TextView>(R.id.tv_template_author).text = DEFAULT_AUTHOR_NAME
        CreativeRemoteImageLoader.bind(findViewById<ImageView>(R.id.iv_template_cover), templatePreviewUrl)
    }

    private fun openTemplateWorkflow() {
        val isVideo = templateType == "text2video" || templateType == "img2video"
        val intent = if (isVideo) {
            Intent(this, MyVideosActivity::class.java).apply {
                putExtra(MyVideosActivity.EXTRA_TEMPLATE_PROMPT, templatePrompt)
                putExtra(MyVideosActivity.EXTRA_TEMPLATE_TYPE, templateType)
                putExtra(MyVideosActivity.EXTRA_TEMPLATE_MODEL_ID, templateModelId)
            }
        } else {
            Intent(this, CreativeTemplateGenerateActivity::class.java).apply {
                putExtra(EXTRA_TEMPLATE_ID, templateId)
                putExtra(EXTRA_TEMPLATE_NAME, templateName)
                putExtra(EXTRA_TEMPLATE_PREVIEW_URL, templatePreviewUrl)
                putExtra(EXTRA_TEMPLATE_TYPE, templateType)
                putExtra(EXTRA_TEMPLATE_MODEL_ID, templateModelId)
                putExtra(EXTRA_TEMPLATE_PROMPT, templatePrompt)
                putExtra(EXTRA_TEMPLATE_STEPS, templateSteps)
                putExtra(EXTRA_TEMPLATE_SAMPLER, templateSampler)
                putExtra(EXTRA_TEMPLATE_CFG_SCALE, templateCfgScale)
            }
        }
        startActivity(intent)
        finish()
    }
}
