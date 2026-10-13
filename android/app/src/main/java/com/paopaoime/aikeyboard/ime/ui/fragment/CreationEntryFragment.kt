package com.paopaoime.aikeyboard.ime.ui.fragment

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.ui.activity.CreativeTemplateDetailActivity
import com.paopaoime.aikeyboard.ime.ui.activity.MyAssetsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreationEntryFragment : Fragment() {
    private lateinit var header: View
    private lateinit var tabContainer: LinearLayout
    private lateinit var templateContainer: LinearLayout
    private lateinit var loadingView: ProgressBar
    private lateinit var emptyView: TextView
    private var catalogItems: List<AuthApiClient.CreativeTemplateChannel> = emptyList()
    private var selectedChannelId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_creation_entry, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header = view.findViewById(R.id.creation_header)
        tabContainer = view.findViewById(R.id.layout_creation_tabs)
        templateContainer = view.findViewById(R.id.layout_creation_templates)
        loadingView = view.findViewById(R.id.progress_creation_loading)
        emptyView = view.findViewById(R.id.tv_creation_empty)

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            header.setPadding(header.paddingLeft, statusTop + 16.dp(), header.paddingRight, header.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)

        view.findViewById<View>(R.id.btn_assets_expanded)?.setOnClickListener {
            startActivity(Intent(requireContext(), MyAssetsActivity::class.java))
        }
        loadCreativeCatalog()
    }

    private fun loadCreativeCatalog() {
        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                AuthApiClient.getCreativeTemplateCatalog()
            }
            if (!isAdded) return@launch
            loadingView.visibility = View.GONE
            if (!result.success) {
                showEmpty(result.message.ifBlank { "创作模板加载失败" })
                return@launch
            }
            catalogItems = result.items
            if (catalogItems.isEmpty()) {
                showEmpty("后台暂未配置创作模板")
                return@launch
            }
            if (catalogItems.none { it.channelId == selectedChannelId }) {
                selectedChannelId = catalogItems.first().channelId
            }
            renderTabs()
            renderTemplates()
        }
    }

    private fun showEmpty(message: String) {
        tabContainer.removeAllViews()
        templateContainer.removeAllViews()
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
    }

    private fun showChannelEmpty(message: String) {
        templateContainer.removeAllViews()
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
    }

    private fun renderTabs() {
        tabContainer.removeAllViews()
        catalogItems.forEach { item ->
            val tab = TextView(requireContext()).apply {
                text = item.channelName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(20.dp(), 10.dp(), 20.dp(), 10.dp())
                background = buildTabBackground(item.channelId == selectedChannelId)
                setTextColor(if (item.channelId == selectedChannelId) Color.WHITE else Color.parseColor("#111111"))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = 12.dp()
                }
                setOnClickListener {
                    if (selectedChannelId == item.channelId) return@setOnClickListener
                    selectedChannelId = item.channelId
                    renderTabs()
                    renderTemplates()
                }
            }
            tabContainer.addView(tab)
        }
    }

    private fun renderTemplates() {
        templateContainer.removeAllViews()
        emptyView.visibility = View.GONE
        val channel = catalogItems.firstOrNull { it.channelId == selectedChannelId }
        if (channel == null || channel.templates.isEmpty()) {
            showChannelEmpty("该频道暂未配置模板")
            return
        }
        if (channel.layoutStyle == "double_grid") {
            renderDoubleGrid(channel.templates)
        } else {
            renderSingleColumn(channel.templates)
        }
    }

    private fun renderSingleColumn(templates: List<AuthApiClient.CreativeTemplateItem>) {
        templates.forEachIndexed { index, item ->
            val card = buildTemplateCard(item, false)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    topMargin = 16.dp()
                }
            }
            templateContainer.addView(card, params)
        }
    }

    private fun renderDoubleGrid(templates: List<AuthApiClient.CreativeTemplateItem>) {
        var currentRow: LinearLayout? = null
        templates.forEachIndexed { index, item ->
            if (index % 2 == 0) {
                currentRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) {
                            topMargin = 16.dp()
                        }
                    }
                }
                templateContainer.addView(currentRow)
            }
            val card = buildTemplateCard(item, true)
            currentRow?.addView(
                card,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index % 2 == 0) {
                        rightMargin = 8.dp()
                    } else {
                        leftMargin = 8.dp()
                    }
                }
            )
        }
    }

    private fun buildTemplateCard(
        item: AuthApiClient.CreativeTemplateItem,
        compact: Boolean
    ): View {
        val imageHeight = if (compact) 188.dp() else 236.dp()
        val card = CardView(requireContext()).apply {
            radius = 20.dp().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.WHITE)
            preventCornerOverlap = true
            useCompatPadding = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { openTemplate(item) }
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        val image = ImageView(requireContext()).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#F2F3F5"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                imageHeight
            )
        }
        CreativeRemoteImageLoader.bind(image, item.previewUrl)
        content.addView(image)
        card.addView(content)
        return card
    }

    private fun openTemplate(item: AuthApiClient.CreativeTemplateItem) {
        val intent = Intent(requireContext(), CreativeTemplateDetailActivity::class.java).apply {
            putExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_ID, item.templateId)
            putExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_NAME, item.templateName)
            putExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_PREVIEW_URL, item.previewUrl)
            putExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_TYPE, item.templateType)
            putExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_MODEL_ID, item.modelName)
            putExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_PROMPT, item.promptText)
            putExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_STEPS, item.steps)
            putExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_SAMPLER, item.sampler)
            putExtra(CreativeTemplateDetailActivity.EXTRA_TEMPLATE_CFG_SCALE, item.cfgScale)
        }
        startActivity(intent)
    }

    private fun buildTabBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 12.dp().toFloat()
            setStroke(1.dp(), if (selected) Color.parseColor("#111111") else Color.parseColor("#D9D9D9"))
            setColor(if (selected) Color.parseColor("#111111") else Color.WHITE)
        }
    }

    private fun Int.dp(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

}
