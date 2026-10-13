package com.paopaoime.aikeyboard.ime.ui.knowledgebase

import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.database.entry.KbDocument
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.ime.ui.activity.MarketingPromptStore
import java.text.DecimalFormat

class KnowledgeBaseStatsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OemEnterpriseConfig.shouldExposeLegacyBusinessFeatures()) {
            Toast.makeText(this, R.string.oem_feature_disabled, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContentView(R.layout.activity_kb_stats)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        val directoryValueView = findViewById<TextView>(R.id.tv_base_directory_value)
        val documentValueView = findViewById<TextView>(R.id.tv_base_document_value)
        val vectorSizeView = findViewById<TextView>(R.id.tv_base_vector_value)
        val docTypeCountView = findViewById<TextView>(R.id.tv_base_doc_type_value)
        val docTypesView = findViewById<TextView>(R.id.tv_doc_types)
        val parseStatusView = findViewById<TextView>(R.id.tv_parse_status)
        val vectorStatusView = findViewById<TextView>(R.id.tv_vector_status)
        val docTypesChart = findViewById<LinearLayout>(R.id.chart_doc_types)
        val parseStatusChart = findViewById<LinearLayout>(R.id.chart_parse_status)
        val vectorStatusChart = findViewById<LinearLayout>(R.id.chart_vector_status)
        val chatTriggerView = findViewById<TextView>(R.id.tv_chat_trigger_value)
        val chatReadyView = findViewById<TextView>(R.id.tv_chat_ready_value)
        val chatLowView = findViewById<TextView>(R.id.tv_chat_low_value)
        val chatEmptyView = findViewById<TextView>(R.id.tv_chat_empty_value)
        val skillTriggerView = findViewById<TextView>(R.id.tv_skill_trigger_value)
        val skillReadyView = findViewById<TextView>(R.id.tv_skill_ready_value)
        val skillLowView = findViewById<TextView>(R.id.tv_skill_low_value)
        val skillEmptyView = findViewById<TextView>(R.id.tv_skill_empty_value)
        val wechatTriggerView = findViewById<TextView>(R.id.tv_module_wechat_trigger)
        val wechatReadyView = findViewById<TextView>(R.id.tv_module_wechat_ready)
        val wechatLowView = findViewById<TextView>(R.id.tv_module_wechat_low)
        val wechatEmptyView = findViewById<TextView>(R.id.tv_module_wechat_empty)
        val xhsTriggerView = findViewById<TextView>(R.id.tv_module_xhs_trigger)
        val xhsReadyView = findViewById<TextView>(R.id.tv_module_xhs_ready)
        val xhsLowView = findViewById<TextView>(R.id.tv_module_xhs_low)
        val xhsEmptyView = findViewById<TextView>(R.id.tv_module_xhs_empty)

        val stats = KnowledgeBaseUiStore.getStats()
        directoryValueView.text = stats.directoryCount.toString()
        documentValueView.text = stats.documentCount.toString()
        vectorSizeView.text = formatBytes(stats.vectorSizeTotal)
        docTypeCountView.text = stats.mimeTypeCounts.size.toString()
        docTypesView.text = buildDocTypes(stats)
        parseStatusView.text = buildParseStatus(stats)
        vectorStatusView.text = buildVectorStatus(stats)
        renderDocTypesChart(docTypesChart, stats)
        renderParseStatusChart(parseStatusChart, stats)
        renderVectorStatusChart(vectorStatusChart, stats)

        val moduleLabels = linkedMapOf(
            MarketingPromptStore.TYPE_AGENT to "微信朋友圈运营",
            MarketingPromptStore.TYPE_AGENT_XHS to "小红书运营"
        )
        val hitStats = KnowledgeBaseStatsStore.getHitStats(moduleLabels.keys.toList())
        chatTriggerView.text = hitStats.chatTriggerCount.toString()
        chatReadyView.text = hitStats.chatReadyCount.toString()
        chatLowView.text = hitStats.chatLowCount.toString()
        chatEmptyView.text = hitStats.chatEmptyCount.toString()
        skillTriggerView.text = hitStats.skillTriggerCount.toString()
        skillReadyView.text = hitStats.skillReadyCount.toString()
        skillLowView.text = hitStats.skillLowCount.toString()
        skillEmptyView.text = hitStats.skillEmptyCount.toString()

        val wechat = hitStats.skillModuleStats[MarketingPromptStore.TYPE_AGENT]
        wechatTriggerView.text = (wechat?.triggerCount ?: 0L).toString()
        wechatReadyView.text = (wechat?.readyCount ?: 0L).toString()
        wechatLowView.text = (wechat?.lowCount ?: 0L).toString()
        wechatEmptyView.text = (wechat?.emptyCount ?: 0L).toString()

        val xhs = hitStats.skillModuleStats[MarketingPromptStore.TYPE_AGENT_XHS]
        xhsTriggerView.text = (xhs?.triggerCount ?: 0L).toString()
        xhsReadyView.text = (xhs?.readyCount ?: 0L).toString()
        xhsLowView.text = (xhs?.lowCount ?: 0L).toString()
        xhsEmptyView.text = (xhs?.emptyCount ?: 0L).toString()
    }

    private fun buildDocTypes(stats: KnowledgeBaseStats): String {
        if (stats.mimeTypeCounts.isEmpty()) return "暂无数据"
        return stats.mimeTypeCounts.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { "${it.key}：${it.value}" }
    }

    private fun buildParseStatus(stats: KnowledgeBaseStats): String {
        if (stats.parseStatusCounts.isEmpty()) return "暂无数据"
        val labels = mapOf(
            0 to "未开始",
            1 to "排队中",
            2 to "解析中",
            3 to "完成",
            4 to "失败"
        )
        return stats.parseStatusCounts.entries
            .sortedBy { it.key }
            .joinToString("\n") { "${labels[it.key] ?: it.key}：${it.value}" }
    }

    private fun buildVectorStatus(stats: KnowledgeBaseStats): String {
        if (stats.vectorStatusCounts.isEmpty()) return "暂无数据"
        val labels = mapOf(
            KbDocument.VECTOR_STATUS_NONE to "未开始",
            KbDocument.VECTOR_STATUS_RUNNING to "进行中",
            KbDocument.VECTOR_STATUS_DONE to "完成",
            KbDocument.VECTOR_STATUS_FAILED to "失败"
        )
        return stats.vectorStatusCounts.entries
            .sortedBy { it.key }
            .joinToString("\n") { "${labels[it.key] ?: it.key}：${it.value}" }
    }

    private fun renderDocTypesChart(container: LinearLayout, stats: KnowledgeBaseStats) {
        val palette = listOf(
            Color.parseColor("#4C8BF5"),
            Color.parseColor("#34A853"),
            Color.parseColor("#FBBC05"),
            Color.parseColor("#EA4335"),
            Color.parseColor("#7E57C2"),
            Color.parseColor("#26C6DA"),
            Color.parseColor("#FF7043"),
            Color.parseColor("#8D6E63")
        )
        val items = stats.mimeTypeCounts.entries
            .sortedByDescending { it.value }
            .mapIndexed { index, entry ->
                ChartItem(entry.key, entry.value.toLong(), palette[index % palette.size])
            }
        container.removeAllViews()
        if (items.isEmpty()) {
            showEmpty(container)
            return
        }
        renderBarChart(container, items)
    }

    private fun renderParseStatusChart(container: LinearLayout, stats: KnowledgeBaseStats) {
        val labels = mapOf(
            0 to "未开始",
            1 to "排队中",
            2 to "解析中",
            3 to "完成",
            4 to "失败"
        )
        val colors = mapOf(
            0 to Color.parseColor("#9E9E9E"),
            1 to Color.parseColor("#42A5F5"),
            2 to Color.parseColor("#26C6DA"),
            3 to Color.parseColor("#34A853"),
            4 to Color.parseColor("#EA4335")
        )
        val items = stats.parseStatusCounts.entries
            .sortedBy { it.key }
            .map {
                ChartItem(labels[it.key] ?: it.key.toString(), it.value.toLong(), colors[it.key] ?: Color.GRAY)
            }
        container.removeAllViews()
        if (items.isEmpty()) {
            showEmpty(container)
            return
        }
        renderBarChart(container, items)
    }

    private fun renderVectorStatusChart(container: LinearLayout, stats: KnowledgeBaseStats) {
        val labels = mapOf(
            KbDocument.VECTOR_STATUS_NONE to "未开始",
            KbDocument.VECTOR_STATUS_RUNNING to "进行中",
            KbDocument.VECTOR_STATUS_DONE to "完成",
            KbDocument.VECTOR_STATUS_FAILED to "失败"
        )
        val colors = mapOf(
            KbDocument.VECTOR_STATUS_NONE to Color.parseColor("#9E9E9E"),
            KbDocument.VECTOR_STATUS_RUNNING to Color.parseColor("#42A5F5"),
            KbDocument.VECTOR_STATUS_DONE to Color.parseColor("#34A853"),
            KbDocument.VECTOR_STATUS_FAILED to Color.parseColor("#EA4335")
        )
        val items = stats.vectorStatusCounts.entries
            .sortedBy { it.key }
            .map {
                ChartItem(labels[it.key] ?: it.key.toString(), it.value.toLong(), colors[it.key] ?: Color.GRAY)
            }
        container.removeAllViews()
        if (items.isEmpty()) {
            showEmpty(container)
            return
        }
        renderBarChart(container, items)
    }

    private fun renderBarChart(container: LinearLayout, items: List<ChartItem>) {
        if (items.isEmpty()) return
        val total = items.sumOf { it.value }
        if (total <= 0L) return
        items.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = dp(8)
                }
            }
            val label = TextView(this).apply {
                val percent = item.value.toDouble() / total.toDouble() * 100
                text = "${item.label}  ${item.value}  (${formatPercent(percent)})"
                textSize = 12f
                setTextColor(Color.parseColor("#555555"))
            }
            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 1f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(7)
                ).apply {
                    topMargin = dp(6)
                }
                background = null
            }
            val ratio = (item.value.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
            val fill = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, ratio)
                setBackgroundColor(item.color)
            }
            val empty = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - ratio)
                setBackgroundColor(Color.parseColor("#EEEEEE"))
            }
            bar.addView(fill)
            bar.addView(empty)
            row.addView(label)
            row.addView(bar)
            container.addView(row)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digit = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceAtMost(units.lastIndex)
        val value = bytes / Math.pow(1024.0, digit.toDouble())
        val format = DecimalFormat(if (digit == 0) "0" else "0.##")
        return "${format.format(value)} ${units[digit]}"
    }

    private fun formatPercent(value: Double): String {
        val format = DecimalFormat("0.#")
        return "${format.format(value)}%"
    }

    private fun showEmpty(container: LinearLayout) {
        val tv = TextView(this).apply {
            text = "暂无数据"
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
        }
        container.addView(tv)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class ChartItem(
        val label: String,
        val value: Long,
        val color: Int
    )
}
