package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibPointsLedger
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.billing.BubbleDisplayHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TokenConsumeHistoryActivity : AppCompatActivity() {

    private data class ConsumeRow(
        val timestamp: Long,
        val bubbleText: String,
        val queryId: String
    )

    private lateinit var rvRecords: RecyclerView
    private lateinit var tvBalance: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var tvPointsSourceHint: TextView
    private val data = ArrayList<ConsumeRow>()
    private val adapter = ConsumeRecordAdapter(data)
    private val dayFormat = SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthSessionStore.isLoggedIn(this)) {
            AuthSessionStore.showHostManagedAccountToast(this, "消耗明细")
            finish()
            return
        }
        setContentView(R.layout.activity_token_consume_history)
        val rootView = findViewById<View>(R.id.history_root)
        val topBar = findViewById<View>(R.id.top_bar)
        val topBarPaddingTop = topBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            topBar.setPadding(
                topBar.paddingLeft,
                topBarPaddingTop + statusBar.top,
                topBar.paddingRight,
                topBar.paddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        rvRecords = findViewById(R.id.rv_consume_records)
        tvBalance = findViewById(R.id.tv_balance_value)
        tvEmpty = findViewById(R.id.tv_empty)
        tvPointsSourceHint = findViewById(R.id.tv_points_source_hint)
        rvRecords.layoutManager = LinearLayoutManager(this)
        rvRecords.adapter = adapter
        renderLocalBalance()
        renderRows(emptyList())
    }

    override fun onResume() {
        super.onResume()
        if (!AuthSessionStore.isLoggedIn(this)) {
            AuthSessionStore.showHostManagedAccountToast(this, "消耗明细")
            finish()
            return
        }
        loadBubbleLedger()
    }

    private fun renderLocalBalance() {
        val balance = LiblibPointsLedger.latestBalance(this)
        tvBalance.text = BubbleDisplayHelper.balanceToBubbles(balance).toString()
        tvPointsSourceHint.text = "服务端账本（权威），正在同步"
    }

    private fun loadBubbleLedger() {
        val accessToken = AuthSessionStore.accessToken(this).trim()
        val refreshToken = AuthSessionStore.refreshToken(this).trim()
        if (accessToken.isBlank()) {
            tvPointsSourceHint.text = "登录态已失效，请重新登录"
            return
        }
        lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) {
                AuthApiClient.getPointSummary(accessToken, refreshToken)
            }
            val nextAccessToken = summary.newAccessToken ?: accessToken
            val nextRefreshToken = summary.newRefreshToken ?: refreshToken
            val ledger = withContext(Dispatchers.IO) {
                AuthApiClient.getTokenUsageLedger(
                    accessToken = nextAccessToken,
                    refreshToken = nextRefreshToken,
                    provider = "",
                    accountOwnerId = "",
                    page = 1,
                    pageSize = 200
                )
            }
            if (summary.authInvalid || ledger.authInvalid) {
                AuthSessionStore.handleOemAuthInvalid(this@TokenConsumeHistoryActivity, "消耗明细")
                finish()
                return@launch
            }
            val finalAccessToken = ledger.newAccessToken ?: nextAccessToken
            val finalRefreshToken = ledger.newRefreshToken ?: nextRefreshToken
            if (finalAccessToken != accessToken || finalRefreshToken != refreshToken) {
                AuthSessionStore.updateTokens(this@TokenConsumeHistoryActivity, finalAccessToken, finalRefreshToken)
            }
            if (summary.success) {
                LiblibPointsLedger.updateBalance(this@TokenConsumeHistoryActivity, summary.balance)
                tvBalance.text = BubbleDisplayHelper.balanceToBubbles(summary.balance).toString()
            }
            if (!ledger.success) {
                tvPointsSourceHint.text = ledger.message.ifBlank { "服务端账本同步失败" }
                renderRows(emptyList())
                return@launch
            }
            renderRows(ledger.items.map { it.toRow() })
            tvPointsSourceHint.text = "服务端账本（权威），已同步"
        }
    }

    private fun renderRows(rows: List<ConsumeRow>) {
        data.clear()
        data.addAll(rows)
        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
    }

    private inner class ConsumeRecordAdapter(
        private val items: List<ConsumeRow>
    ) : RecyclerView.Adapter<ConsumeRecordAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTime: TextView = itemView.findViewById(R.id.tv_col_time)
            val tvBubble: TextView = itemView.findViewById(R.id.tv_col_bubble)
            val tvQueryId: TextView = itemView.findViewById(R.id.tv_col_query_id)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_token_consume_record, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvTime.text = dayFormat.format(Date(item.timestamp))
            holder.tvBubble.text = item.bubbleText
            holder.tvQueryId.text = item.queryId
        }

        override fun getItemCount(): Int = items.size
    }

    private fun AuthApiClient.TokenUsageLedgerItem.toRow(): ConsumeRow {
        val bubbleAmount = BubbleDisplayHelper.estimateBubbleCost(
            provider = provider,
            model = model,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            pointsCost = pointsCost
        )?.amount ?: pointsCost.coerceAtLeast(0)
        val queryIdValue = id.ifBlank {
            requestId.ifBlank {
                clientEventId.ifBlank { "-" }
            }
        }
        return ConsumeRow(
            timestamp = parseServerTime(occurredAt.ifBlank { createdAt }),
            bubbleText = if (bubbleAmount > 0) bubbleAmount.toString() else "-",
            queryId = queryIdValue
        )
    }

    private fun parseServerTime(value: String): Long {
        if (value.isBlank()) return System.currentTimeMillis()
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        patterns.forEach { pattern ->
            val formatter = SimpleDateFormat(pattern, Locale.US)
            if (pattern.contains("'Z'")) {
                formatter.timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = runCatching { formatter.parse(value) }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return System.currentTimeMillis()
    }
}
