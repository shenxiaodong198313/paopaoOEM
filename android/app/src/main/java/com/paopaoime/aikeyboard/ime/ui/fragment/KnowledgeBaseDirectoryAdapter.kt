package com.paopaoime.aikeyboard.ime.ui.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDirectoryUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KnowledgeBaseDirectoryAdapter(
    private val onDirectoryClick: (KbDirectoryUi) -> Unit
) : RecyclerView.Adapter<KnowledgeBaseDirectoryAdapter.ViewHolder>() {

    private val items = mutableListOf<KbDirectoryUi>()
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    fun submitList(newItems: List<KbDirectoryUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_kb_directory, parent, false)
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
        holder.itemView.setOnClickListener { onDirectoryClick(item) }
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 MB"
        val mb = size.toDouble() / 1024 / 1024
        return String.format(Locale.getDefault(), "%.2f MB", mb)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.iv_kb_item_icon)
        val tvTitle: TextView = view.findViewById(R.id.tv_kb_item_title)
        val tvSubtitle: TextView = view.findViewById(R.id.tv_kb_item_subtitle)
    }
}

