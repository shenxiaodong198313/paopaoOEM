package com.paopaoime.aikeyboard.ime.ui.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.data.MemoryEntry
import com.paopaoime.aikeyboard.ime.data.MarkdownMemoryRecord
import com.paopaoime.aikeyboard.ime.data.MarkdownMemoryRetrieveRequest
import com.paopaoime.aikeyboard.ime.data.MarkdownMemoryStore
import com.paopaoime.aikeyboard.ime.data.MarkdownTopicIndexEntry
import com.paopaoime.aikeyboard.ime.data.MemoryStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

class MemoryLibraryActivity : AppCompatActivity() {
    private enum class SourceFilter { ALL, LEGACY, WORKING, SHORT_TERM, LONG_TERM }
    private enum class PersonaFilter { ALL, NPC1, NPC2, SYSTEM }

    private lateinit var storage: MemoryStorage
    private lateinit var markdownStore: MarkdownMemoryStore
    private lateinit var adapter: MemoryListAdapter
    private lateinit var tvStats: TextView
    private lateinit var btnAdd: TextView

    private var allItems: List<MemoryLibraryUiItem> = emptyList()
    private var topicIndex: List<MarkdownTopicIndexEntry> = emptyList()
    private var sourceFilter: SourceFilter = SourceFilter.ALL
    private var personaFilter: PersonaFilter = PersonaFilter.ALL
    private var searchText: String = ""
    private var cloudBacked: Boolean = false
    private var syncStatusLabel: String = "离线缓存（非权威）"

    companion object {
        private const val MEMORY_SYNC_TTL_MILLIS = 15 * 60 * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory_library)
        storage = MemoryStorage(this)
        markdownStore = MarkdownMemoryStore(this)

        val header = findViewById<View>(R.id.layout_memory_header)
        val headerTop = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(header) { view, windowInsets ->
            val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, headerTop + statusBars.top, view.paddingRight, view.paddingBottom)
            windowInsets
        }
        header.requestApplyInsets()

        val recyclerView = findViewById<RecyclerView>(R.id.rv_memory_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MemoryListAdapter(
            onEdit = { handleEditOrPreview(it) },
            onDelete = { deleteMemory(it) }
        )
        recyclerView.adapter = adapter
        tvStats = findViewById(R.id.tv_memory_stats)
        btnAdd = findViewById(R.id.btn_memory_add)

        btnAdd.setOnClickListener {
            showEditDialog(null)
        }
        btnAdd.setOnLongClickListener {
            setSyncStatus("手动同步中…")
            syncFromServer()
            true
        }
        findViewById<View>(R.id.btn_memory_back).setOnClickListener { finish() }
        bindFilterViews()

        refreshData()
        if (AuthSessionStore.isSyncExpired(this, syncScope(), MEMORY_SYNC_TTL_MILLIS)) {
            setSyncStatus("缓存可能已过期，建议手动同步")
        }
        syncFromServer()
    }

    private fun bindFilterViews() {
        findViewById<EditText>(R.id.et_memory_search).addTextChangedListener {
            searchText = it?.toString()?.trim().orEmpty()
            applyFilters()
        }
        bindSourceFilter(R.id.tv_memory_filter_all, SourceFilter.ALL)
        bindSourceFilter(R.id.tv_memory_filter_legacy, SourceFilter.LEGACY)
        bindSourceFilter(R.id.tv_memory_filter_working, SourceFilter.WORKING)
        bindSourceFilter(R.id.tv_memory_filter_short, SourceFilter.SHORT_TERM)
        bindSourceFilter(R.id.tv_memory_filter_long, SourceFilter.LONG_TERM)
        bindPersonaFilter(R.id.tv_memory_persona_all, PersonaFilter.ALL)
        bindPersonaFilter(R.id.tv_memory_persona_npc1, PersonaFilter.NPC1)
        bindPersonaFilter(R.id.tv_memory_persona_npc2, PersonaFilter.NPC2)
        bindPersonaFilter(R.id.tv_memory_persona_system, PersonaFilter.SYSTEM)
        refreshFilterUi()
    }

    private fun bindSourceFilter(viewId: Int, filter: SourceFilter) {
        findViewById<TextView>(viewId).setOnClickListener {
            sourceFilter = filter
            refreshFilterUi()
            applyFilters()
        }
    }

    private fun bindPersonaFilter(viewId: Int, filter: PersonaFilter) {
        findViewById<TextView>(viewId).setOnClickListener {
            personaFilter = filter
            refreshFilterUi()
            applyFilters()
        }
    }

    private fun refreshFilterUi() {
        updateChip(findViewById(R.id.tv_memory_filter_all), sourceFilter == SourceFilter.ALL)
        updateChip(findViewById(R.id.tv_memory_filter_legacy), sourceFilter == SourceFilter.LEGACY)
        updateChip(findViewById(R.id.tv_memory_filter_working), sourceFilter == SourceFilter.WORKING)
        updateChip(findViewById(R.id.tv_memory_filter_short), sourceFilter == SourceFilter.SHORT_TERM)
        updateChip(findViewById(R.id.tv_memory_filter_long), sourceFilter == SourceFilter.LONG_TERM)
        updateChip(findViewById(R.id.tv_memory_persona_all), personaFilter == PersonaFilter.ALL)
        updateChip(findViewById(R.id.tv_memory_persona_npc1), personaFilter == PersonaFilter.NPC1)
        updateChip(findViewById(R.id.tv_memory_persona_npc2), personaFilter == PersonaFilter.NPC2)
        updateChip(findViewById(R.id.tv_memory_persona_system), personaFilter == PersonaFilter.SYSTEM)
    }

    private fun updateChip(view: TextView, selected: Boolean) {
        view.isSelected = selected
        if (selected) {
            view.setTextColor(0xFFFFFFFF.toInt())
            view.setBackgroundResource(R.drawable.bg_filter_chip_selected)
        } else {
            view.setTextColor(0xFFC4C4C6.toInt())
            view.setBackgroundResource(R.drawable.bg_filter_chip_normal)
        }
    }

    private fun refreshData() {
        cloudBacked = false
        setSyncStatus("离线缓存（非权威）")
        val legacy = storage.listMemories().map {
            MemoryLibraryUiItem(
                id = it.id,
                title = it.title.ifBlank { "未命名记忆" },
                content = it.content,
                meta = "来源：历史记忆",
                sourceType = "legacy",
                layer = "legacy",
                persona = "",
                tags = emptyList(),
                filePath = "",
                editable = true,
                deletable = true
            )
        }
        val markdown = markdownStore.listMarkdownRecords().map { it.toUiItem() }
        topicIndex = markdownStore.listTopicIndexEntries()
        allItems = (markdown + legacy)
        applyFilters()
    }

    private fun syncFromServer() {
        if (!AuthSessionStore.isLoggedIn(this)) return
        val accessToken = AuthSessionStore.accessToken(this)
        val refreshToken = AuthSessionStore.refreshToken(this)
        if (accessToken.isBlank()) return
        lifecycleScope.launch {
            setSyncStatus("同步中…")
            val result = withContext(Dispatchers.IO) {
                AuthApiClient.listMemories(accessToken, refreshToken)
            }
            if (!result.newAccessToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(this@MemoryLibraryActivity, result.newAccessToken, result.newRefreshToken)
            }
            if (result.authInvalid) {
                handleAuthInvalid()
                return@launch
            }
            if (!result.success) {
                setSyncStatus("同步失败：${result.message}")
                return@launch
            }
            cloudBacked = true
            val cloudItems = result.items.map { memory ->
                val layer = when (memory.memoryType) {
                    "long" -> "long-term"
                    "summary" -> "short-term"
                    else -> "short-term"
                }
                MemoryLibraryUiItem(
                    id = memory.id,
                    title = memory.ref.optString("title").ifBlank { "云端记忆" },
                    content = memory.content,
                    meta = "来源：云端记忆 · 类型：${memory.memoryType}",
                    sourceType = "cloud",
                    layer = layer,
                    persona = memory.ref.optString("persona").ifBlank { "system" },
                    tags = emptyList(),
                    filePath = "",
                    editable = false,
                    deletable = true
                )
            }
            allItems = cloudItems
            topicIndex = emptyList()
            AuthSessionStore.updateSyncAt(this@MemoryLibraryActivity, syncScope())
            setSyncStatus("已同步")
            applyFilters()
        }
    }

    private fun applyFilters() {
        val keyword = searchText.lowercase(Locale.getDefault())
        val personaKey = when (personaFilter) {
            PersonaFilter.ALL -> null
            PersonaFilter.NPC1 -> "npc1"
            PersonaFilter.NPC2 -> "npc2"
            PersonaFilter.SYSTEM -> "system"
        }
        val retrievedIds = if (keyword.isBlank()) {
            emptySet()
        } else {
            markdownStore.retrieveRecords(
                MarkdownMemoryRetrieveRequest(
                    query = keyword,
                    persona = personaKey,
                    topK = 30
                )
            ).map { it.id }.toSet()
        }
        val filtered = allItems.filter { item ->
            val sourceMatch = when (sourceFilter) {
                SourceFilter.ALL -> true
                SourceFilter.LEGACY -> item.sourceType == "legacy"
                SourceFilter.WORKING -> item.layer == "working"
                SourceFilter.SHORT_TERM -> item.layer == "short-term"
                SourceFilter.LONG_TERM -> item.layer == "long-term"
            }
            val personaMatch = when (personaFilter) {
                PersonaFilter.ALL -> true
                PersonaFilter.NPC1 -> item.persona == "npc1"
                PersonaFilter.NPC2 -> item.persona == "npc2"
                PersonaFilter.SYSTEM -> item.persona == "system"
            } || item.sourceType == "legacy" && personaFilter == PersonaFilter.ALL
            val searchMatch = keyword.isBlank() || buildString {
                append(item.title)
                append(" ")
                append(item.content)
                append(" ")
                append(item.meta)
                append(" ")
                append(item.tags.joinToString(" "))
            }.lowercase(Locale.getDefault()).contains(keyword)
            val semanticMatch = keyword.isBlank() || item.sourceType == "legacy" || retrievedIds.contains(item.id)
            sourceMatch && personaMatch && (searchMatch || semanticMatch)
        }
        adapter.submitList(filtered)
        val markdownCount = allItems.count { it.sourceType == "markdown" }
        val topicCount = topicIndex.size
        val longTopicCount = topicIndex.count { (it.layerCounts["long-term"] ?: 0) > 0 }
        tvStats.text = "$syncStatusLabel · 当前 ${filtered.size} 条 · Markdown ${markdownCount} 条 · Topic ${topicCount} 个 · 长期主题 ${longTopicCount} 个"
    }

    private fun handleEditOrPreview(item: MemoryLibraryUiItem) {
        if (item.sourceType == "legacy") {
            showEditDialog(MemoryEntry(item.id, item.title, item.content))
        } else {
            val raw = markdownStore.readMemoryFile(item.filePath)
            AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(raw.ifBlank { item.content })
                .setPositiveButton(getString(R.string.confirm), null)
                .show()
        }
    }

    private fun deleteMemory(item: MemoryLibraryUiItem) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.memory_delete_title))
            .setMessage(getString(R.string.memory_delete_confirm))
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                if (item.sourceType == "legacy") {
                    storage.deleteMemory(item.id)
                } else if (item.sourceType == "cloud" && cloudBacked && AuthSessionStore.isLoggedIn(this)) {
                    val accessToken = AuthSessionStore.accessToken(this)
                    val refreshToken = AuthSessionStore.refreshToken(this)
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            AuthApiClient.deleteMemory(accessToken, refreshToken, item.id)
                        }
                        if (!result.newAccessToken.isNullOrBlank()) {
                            AuthSessionStore.updateTokens(this@MemoryLibraryActivity, result.newAccessToken, result.newRefreshToken)
                        }
                        if (result.authInvalid) {
                            handleAuthInvalid()
                            return@launch
                        }
                        if (!result.success) {
                            setSyncStatus("同步失败：${result.message}")
                            Toast.makeText(this@MemoryLibraryActivity, result.message, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        setSyncStatus("已同步")
                        syncFromServer()
                    }
                    return@setPositiveButton
                } else {
                    markdownStore.deleteMemoryFile(item.filePath)
                }
                refreshData()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showEditDialog(entry: MemoryEntry?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val titleInput = EditText(this).apply {
            hint = getString(R.string.memory_title_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(entry?.title ?: "")
        }
        val contentInput = EditText(this).apply {
            hint = getString(R.string.memory_content_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            setText(entry?.content ?: "")
        }
        container.addView(titleInput)
        container.addView(contentInput)
        AlertDialog.Builder(this)
            .setTitle(if (entry == null) getString(R.string.memory_add_title) else getString(R.string.memory_edit_title))
            .setView(container)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val title = titleInput.text.toString().trim()
                val content = contentInput.text.toString().trim()
                if (content.isBlank()) {
                    Toast.makeText(this, "记忆内容不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (entry == null) {
                    if (cloudBacked && AuthSessionStore.isLoggedIn(this)) {
                        val accessToken = AuthSessionStore.accessToken(this)
                        val refreshToken = AuthSessionStore.refreshToken(this)
                        lifecycleScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                AuthApiClient.createMemory(
                                    accessToken = accessToken,
                                    refreshToken = refreshToken,
                                    memoryType = "short",
                                    content = content,
                                    weight = 1,
                                    ref = JSONObject().put("title", title)
                                )
                            }
                            if (!result.newAccessToken.isNullOrBlank()) {
                                AuthSessionStore.updateTokens(this@MemoryLibraryActivity, result.newAccessToken, result.newRefreshToken)
                            }
                            if (result.authInvalid) {
                                handleAuthInvalid()
                                return@launch
                            }
                            if (!result.success) {
                                setSyncStatus("同步失败：${result.message}")
                                Toast.makeText(this@MemoryLibraryActivity, result.message, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            setSyncStatus("已同步")
                            syncFromServer()
                        }
                        return@setPositiveButton
                    }
                    storage.createMemory(title, content)
                } else {
                    storage.updateMemory(entry.id, title, content)
                }
                refreshData()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun MarkdownMemoryRecord.toUiItem(): MemoryLibraryUiItem {
        val preview = fact.ifBlank { decision }.ifBlank { evidence }.ifBlank { "无内容摘要" }
        val sourceName = when (layer) {
            "working" -> "working"
            "short-term" -> "short-term"
            "long-term" -> "long-term"
            else -> layer
        }
        val scopeText = buildString {
            if (agentId.isNotBlank()) append(" · Agent:$agentId")
            if (sessionId.isNotBlank()) append(" · Session:$sessionId")
            if (eventId.isNotBlank()) append(" · Event:$eventId")
        }
        val mergedTags = (tags + references + relatedTaskIds.map { "task:$it" }).distinct()
        return MemoryLibraryUiItem(
            id = id,
            title = "$topic · $persona",
            content = preview,
            meta = "来源：$sourceName · 更新时间：${updatedAt.ifBlank { "未知" }}$scopeText",
            sourceType = "markdown",
            layer = layer,
            persona = persona,
            tags = mergedTags,
            filePath = filePath,
            editable = false,
            deletable = true
        )
    }

    private fun setSyncStatus(status: String) {
        syncStatusLabel = status
        applyFilters()
    }

    private fun handleAuthInvalid() {
        AuthSessionStore.handleOemAuthInvalid(this, "记忆库")
        cloudBacked = false
        setSyncStatus("离线缓存（非权威）")
    }

    private fun syncScope(): String = "memory_list"
}

private data class MemoryLibraryUiItem(
    val id: String,
    val title: String,
    val content: String,
    val meta: String,
    val sourceType: String,
    val layer: String,
    val persona: String,
    val tags: List<String>,
    val filePath: String,
    val editable: Boolean,
    val deletable: Boolean
)

private class MemoryListAdapter(
    private val onEdit: (MemoryLibraryUiItem) -> Unit,
    private val onDelete: (MemoryLibraryUiItem) -> Unit
) : RecyclerView.Adapter<MemoryViewHolder>() {
    private val items = mutableListOf<MemoryLibraryUiItem>()

    fun submitList(list: List<MemoryLibraryUiItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memory_entry, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val entry = items[position]
        holder.bind(entry, onEdit, onDelete)
    }

    override fun getItemCount(): Int = items.size
}

private class MemoryViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
    private val title = itemView.findViewById<TextView>(R.id.tv_memory_title)
    private val meta = itemView.findViewById<TextView>(R.id.tv_memory_meta)
    private val content = itemView.findViewById<TextView>(R.id.tv_memory_content)
    private val edit = itemView.findViewById<TextView>(R.id.btn_memory_edit)
    private val delete = itemView.findViewById<TextView>(R.id.btn_memory_delete)

    fun bind(
        entry: MemoryLibraryUiItem,
        onEdit: (MemoryLibraryUiItem) -> Unit,
        onDelete: (MemoryLibraryUiItem) -> Unit
    ) {
        title.text = entry.title
        meta.text = entry.meta
        val tagText = if (entry.tags.isEmpty()) "" else " #${entry.tags.joinToString(" #")}"
        content.text = entry.content + tagText
        edit.text = if (entry.editable) "编辑" else "查看"
        edit.setOnClickListener { onEdit(entry) }
        if (entry.deletable) {
            delete.visibility = View.VISIBLE
            delete.setOnClickListener { onDelete(entry) }
        } else {
            delete.visibility = View.GONE
            delete.setOnClickListener(null)
        }
    }
}
