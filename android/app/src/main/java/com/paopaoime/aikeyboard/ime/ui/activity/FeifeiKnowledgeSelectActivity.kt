package com.paopaoime.aikeyboard.ime.ui.activity

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.KnowledgeBaseUiStore
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDirectoryUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeifeiKnowledgeSelectActivity : AppCompatActivity() {
    private val selectedDirectoryIds = mutableSetOf<String>()
    private var directories: List<KbDirectoryUi> = emptyList()
    private lateinit var adapter: DirectorySelectAdapter
    private var prefKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_feifei_kb_select)

        val personaId = intent.getStringExtra(EXTRA_PERSONA_ID) ?: DEFAULT_PERSONA_ID
        prefKey = "${PREF_KEY_SELECTED_DIRS}_$personaId"

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        selectedDirectoryIds.addAll(prefs.getStringSet(prefKey, emptySet()) ?: emptySet())

        val header = findViewById<android.view.View>(R.id.layout_kb_select_header)
        val recyclerView = findViewById<RecyclerView>(R.id.rv_kb_select)
        val tvEmpty = findViewById<TextView>(R.id.tv_kb_select_empty)
        tvEmpty.text = "无数据"

        val initialHeaderPaddingTop = header.paddingTop
        val initialRecyclerPaddingBottom = recyclerView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = initialHeaderPaddingTop + bars.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = initialRecyclerPaddingBottom + bars.bottom)
            insets
        }

        findViewById<ImageView>(R.id.btn_kb_select_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btn_kb_select_save).setOnClickListener {
            prefs.edit().putStringSet(prefKey, selectedDirectoryIds).apply()
            Toast.makeText(this, getString(R.string.save_success), Toast.LENGTH_SHORT).show()
            finish()
        }

        adapter = DirectorySelectAdapter(selectedDirectoryIds) { updateEmptyState(tvEmpty, recyclerView) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        renderDirectories()
        updateEmptyState(tvEmpty, recyclerView)
    }

    override fun onResume() {
        super.onResume()
        renderDirectories()
    }

    private fun renderDirectories() {
        directories = KnowledgeBaseUiStore.getDirectories()
        adapter.submitList(directories)
        val recyclerView = findViewById<RecyclerView>(R.id.rv_kb_select)
        val tvEmpty = findViewById<TextView>(R.id.tv_kb_select_empty)
        updateEmptyState(tvEmpty, recyclerView)
    }

    private fun updateEmptyState(tvEmpty: TextView, recyclerView: RecyclerView) {
        val hasItems = adapter.itemCount > 0
        tvEmpty.visibility = if (hasItems) android.view.View.GONE else android.view.View.VISIBLE
        recyclerView.visibility = if (hasItems) android.view.View.VISIBLE else android.view.View.GONE
    }

    private class DirectorySelectAdapter(
        private val selectedIds: MutableSet<String>,
        private val onSelectionChanged: () -> Unit
    ) : RecyclerView.Adapter<DirectorySelectAdapter.ViewHolder>() {
        private val items = mutableListOf<KbDirectoryUi>()
        private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

        fun submitList(newItems: List<KbDirectoryUi>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_kb_directory_select, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.name

            val dateText = dateFormat.format(Date(item.updatedAtMillis))
            val sizeText = formatSize(item.vectorStoreSize)
            val context = holder.itemView.context
            holder.tvSubtitle.text = "${context.getString(R.string.recent_update)}：$dateText  |  ${context.getString(R.string.vector_storage)}：$sizeText"

            holder.ivIcon.setImageResource(R.drawable.ic_kb_folder)

            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = selectedIds.contains(item.id)
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                updateSelection(item.id, isChecked)
                onSelectionChanged()
            }

            holder.itemView.setOnClickListener {
                val nextChecked = !selectedIds.contains(item.id)
                holder.checkBox.isChecked = nextChecked
            }
        }

        private fun updateSelection(id: String, isChecked: Boolean) {
            if (isChecked) {
                selectedIds.add(id)
            } else {
                selectedIds.remove(id)
            }
        }

        private fun formatSize(size: Long): String {
            if (size <= 0) return "0 MB"
            val mb = size.toDouble() / 1024 / 1024
            return String.format(Locale.getDefault(), "%.2f MB", mb)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.iv_kb_item_icon)
            val tvTitle: TextView = view.findViewById(R.id.tv_kb_item_title)
            val tvSubtitle: TextView = view.findViewById(R.id.tv_kb_item_subtitle)
            val checkBox: androidx.appcompat.widget.AppCompatCheckBox = view.findViewById(R.id.cb_kb_item_select)
        }
    }

    companion object {
        const val EXTRA_PERSONA_ID = "persona_id"
        private const val PREFS_NAME = "feifei_companion"
        private const val PREF_KEY_SELECTED_DIRS = "selected_kb_directory_ids"
        private const val DEFAULT_PERSONA_ID = "feifei"
    }
}
