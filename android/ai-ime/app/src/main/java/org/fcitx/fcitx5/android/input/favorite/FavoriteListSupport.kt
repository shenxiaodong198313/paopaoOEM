package org.fcitx.fcitx5.android.input.favorite

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

internal sealed class FavoriteListItem {
    data class RecordEntry(val record: FavoriteRecord) : FavoriteListItem()
    data class FolderEntry(val folder: FavoriteFolderItem) : FavoriteListItem()
}

internal class FavoriteAdapter(
    private val onPreviewClick: (FavoriteRecord) -> Unit,
    private val onFolderClick: (FavoriteFolderItem) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<FavoriteListItem>()

    init {
        setHasStableIds(true)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is FavoriteListItem.RecordEntry -> VIEW_TYPE_RECORD
            is FavoriteListItem.FolderEntry -> VIEW_TYPE_FOLDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutParams = StaggeredGridLayoutManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return when (viewType) {
            VIEW_TYPE_FOLDER -> FolderViewHolder(FavoriteUi.FavoriteFolderCardView(parent.context).apply {
                this.layoutParams = layoutParams
            })
            else -> RecordViewHolder(FavoriteUi.FavoriteCardView(parent.context).apply {
                this.layoutParams = layoutParams
            })
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long {
        return when (val item = items[position]) {
            is FavoriteListItem.RecordEntry -> item.record.id.hashCode().toLong()
            is FavoriteListItem.FolderEntry -> item.folder.id.hashCode().toLong()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is FavoriteListItem.RecordEntry -> {
                (holder as RecordViewHolder).cardView.bind(
                    item = item.record,
                    onPreviewClick = { onPreviewClick(item.record) },
                )
            }
            is FavoriteListItem.FolderEntry -> {
                (holder as FolderViewHolder).cardView.bind(item.folder)
                holder.cardView.setOnClickListener { onFolderClick(item.folder) }
            }
        }
    }

    fun submitItems(newItems: List<FavoriteListItem>) {
        val oldItems = items.toList()
        items.clear()
        items.addAll(newItems)
        val appendedOnly = oldItems.isNotEmpty() &&
            oldItems.size < newItems.size &&
            oldItems == newItems.take(oldItems.size)
        if (appendedOnly) {
            notifyItemRangeInserted(oldItems.size, newItems.size - oldItems.size)
        } else {
            notifyDataSetChanged()
        }
    }

    private class RecordViewHolder(val cardView: FavoriteUi.FavoriteCardView) : RecyclerView.ViewHolder(cardView)

    private class FolderViewHolder(val cardView: FavoriteUi.FavoriteFolderCardView) : RecyclerView.ViewHolder(cardView)

    companion object {
        private const val VIEW_TYPE_RECORD = 1
        private const val VIEW_TYPE_FOLDER = 2
    }
}

internal class FavoriteGridSpacingDecoration(
    private val spacingPx: Int,
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val params = view.layoutParams as? StaggeredGridLayoutManager.LayoutParams ?: return
        val halfSpacing = spacingPx / 2
        outRect.top = 0
        outRect.bottom = spacingPx
        outRect.left = if (params.spanIndex == 0) 0 else halfSpacing
        outRect.right = if (params.spanIndex == 0) halfSpacing else 0
    }
}
