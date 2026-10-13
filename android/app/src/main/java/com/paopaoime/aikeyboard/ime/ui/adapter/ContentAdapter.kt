package com.paopaoime.aikeyboard.ime.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.paopaoime.aikeyboard.ime.R

data class ContentItem(
    val title: String,
    val time: String,
    val body: String,
    val tag1: String,
    val tag2: String
)

class ContentAdapter(private val items: List<ContentItem>) : RecyclerView.Adapter<ContentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val tvTime: TextView = view.findViewById(R.id.tv_time)
        val tvBody: TextView = view.findViewById(R.id.tv_body)
        val tvTag1: TextView = view.findViewById(R.id.tag_1)
        val tvTag2: TextView = view.findViewById(R.id.tag_2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_content_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvTime.text = item.time
        holder.tvBody.text = item.body
        holder.tvTag1.text = item.tag1
        holder.tvTag2.text = item.tag2
    }

    override fun getItemCount() = items.size
}
