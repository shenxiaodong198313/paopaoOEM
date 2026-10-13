package com.paopaoime.aikeyboard.ime.ui.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ai.store.LocalAsset
import java.io.File

class AssetsAdapter(
    private var assets: List<LocalAsset> = emptyList(),
    private val onItemClick: (LocalAsset, Int) -> Unit
) : RecyclerView.Adapter<AssetsAdapter.ViewHolder>() {

    fun updateData(newAssets: List<LocalAsset>) {
        assets = newAssets
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumb: ImageView = itemView.findViewById(R.id.iv_asset_thumb)
        val ivPlay: ImageView = itemView.findViewById(R.id.iv_play_icon)
        val tvType: TextView = itemView.findViewById(R.id.tv_asset_type)
        val tvPrompt: TextView = itemView.findViewById(R.id.tv_prompt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_asset, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val asset = assets[position]
        
        // Load thumbnail
        if (asset.type == 1) { // Video
            holder.ivPlay.visibility = View.VISIBLE
            holder.tvType.text = "VIDEO"
            if (asset.coverPath.isNotEmpty()) {
                 holder.ivThumb.setImageURI(Uri.fromFile(File(asset.coverPath)))
            } else {
                 holder.ivThumb.setImageResource(android.R.drawable.ic_menu_slideshow)
            }
        } else {
            holder.ivPlay.visibility = View.GONE
            holder.tvType.text = "IMG"
            holder.ivThumb.setImageURI(Uri.fromFile(File(asset.localPath)))
        }

        holder.tvPrompt.text = asset.prompt
        
        holder.itemView.setOnClickListener {
            onItemClick(asset, position)
        }
    }

    override fun getItemCount(): Int = assets.size
}


