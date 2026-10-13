package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge.ToolbarItem
import com.paopaoime.aikeyboard.ime.ui.toolbar.OemToolbarIconHelper
import java.util.Collections

class CustomizeToolbarSettingsFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_customize_toolbar_settings
) {
    private val selectedItems = mutableListOf<InputMethodSettingsBridge.ToolbarDisplayItem>()
    private lateinit var selectedAdapter: SelectedToolbarAdapter
    private lateinit var availableAdapter: AvailableToolbarAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.layout_customize_toolbar_header, R.id.btn_customize_toolbar_back)

        selectedItems.clear()
        selectedItems.addAll(InputMethodSettingsBridge.getToolbarDisplaySettings(requireContext()).orderedItems)

        hideFixedPreviewIcons(view)
        bindSelectedRecycler(view.findViewById(R.id.recycler_customize_toolbar_selected))
        bindAvailableRecycler(view.findViewById(R.id.recycler_customize_toolbar_available))
        renderEditorState()
    }

    private fun hideFixedPreviewIcons(root: View) {
        root.findViewById<View>(R.id.layout_customize_toolbar_fixed_more).visibility = View.GONE
        root.findViewById<View>(R.id.layout_customize_toolbar_fixed_hide).visibility = View.GONE
    }

    private fun bindSelectedRecycler(recyclerView: RecyclerView) {
        selectedAdapter = SelectedToolbarAdapter(
            onStartDrag = { holder -> dragHelper?.startDrag(holder) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        recyclerView.adapter = selectedAdapter
        if (recyclerView.itemDecorationCount == 0) {
            recyclerView.addItemDecoration(HorizontalSpacingDecoration(dp(OemToolbarIconHelper.TOOLBAR_BUTTON_MARGIN_DP)))
        }
        dragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                Collections.swap(selectedItems, from, to)
                selectedAdapter.notifyItemMoved(from, to)
                persistToolbarState()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled(): Boolean = false
        }).also { it.attachToRecyclerView(recyclerView) }
    }

    private fun bindAvailableRecycler(recyclerView: RecyclerView) {
        availableAdapter = AvailableToolbarAdapter(::toggleToolbarItem)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)
        recyclerView.adapter = availableAdapter
        recyclerView.setPadding(dp(8), dp(4), dp(8), dp(8))
        recyclerView.clipToPadding = false
        if (recyclerView.itemDecorationCount == 0) {
            recyclerView.addItemDecoration(GridSpacingDecoration(spanCount = 4, horizontalSpacePx = dp(3), verticalSpacePx = dp(4)))
        }
    }

    private var dragHelper: ItemTouchHelper? = null

    private fun toggleToolbarItem(item: InputMethodSettingsBridge.ToolbarDisplayItem) {
        val existed = selectedItems.remove(item)
        if (!existed) {
            if (selectedItems.size >= InputMethodSettingsBridge.MAX_TOOLBAR_ITEMS) {
                Toast.makeText(requireContext(), R.string.customize_toolbar_max_reached, Toast.LENGTH_SHORT).show()
                return
            }
            selectedItems.add(item)
        }
        persistToolbarState()
        renderEditorState()
    }

    private fun persistToolbarState() {
        InputMethodSettingsBridge.setToolbarDisplayVisibleItems(requireContext(), selectedItems.toSet())
        InputMethodSettingsBridge.setToolbarDisplayOrder(requireContext(), selectedItems.toList())
    }

    private fun renderEditorState() {
        selectedAdapter.submit(selectedItems)
        view?.findViewById<RecyclerView>(R.id.recycler_customize_toolbar_selected)?.requestLayout()
        availableAdapter.submit(
            InputMethodSettingsBridge.availableDisplayItems(requireContext()),
            selectedItems.toSet()
        )
    }

    private fun tintSelectedPreview(imageView: ImageView) {
        imageView.imageTintList = android.content.res.ColorStateList.valueOf(OemToolbarIconHelper.toolbarIconTintColor())
    }

    private fun tintAvailable(imageView: ImageView, selected: Boolean) {
        imageView.imageTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (selected) "#22B59A" else "#2F3337")
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class SelectedToolbarAdapter(
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    ) : RecyclerView.Adapter<SelectedToolbarAdapter.SelectedViewHolder>() {
        private val items = mutableListOf<InputMethodSettingsBridge.ToolbarDisplayItem>()

        fun submit(newItems: List<InputMethodSettingsBridge.ToolbarDisplayItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_customize_toolbar_preview_icon, parent, false)
            return SelectedViewHolder(view)
        }

        override fun onBindViewHolder(holder: SelectedViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class SelectedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconFrame = itemView.findViewById<FrameLayout>(R.id.layout_customize_toolbar_preview_item)
            private val icon = itemView.findViewById<ImageView>(R.id.image_customize_toolbar_preview_icon)

            init {
                itemView.setOnLongClickListener {
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                        onStartDrag(this)
                    }
                    true
                }
            }

            fun bind(item: InputMethodSettingsBridge.ToolbarDisplayItem) {
                when (item) {
                    is InputMethodSettingsBridge.ToolbarDisplayItem.Builtin -> {
                        val iconRes = OemToolbarIconHelper.toolbarIconRes(item.item)
                        icon.setImageResource(iconRes)
                        tintSelectedPreview(icon)
                        OemToolbarIconHelper.applyPreviewStyle(iconFrame, icon, itemView.context, iconRes)
                        itemView.contentDescription = getString(item.item.labelRes)
                    }
                    is InputMethodSettingsBridge.ToolbarDisplayItem.Custom -> {
                        icon.imageTintList = null
                        icon.setImageDrawable(
                            item.icon ?: itemView.context.getDrawable(R.drawable.ic_oem_toolbar_custom_placeholder)
                        )
                        OemToolbarIconHelper.applyPreviewStyle(iconFrame, icon, itemView.context, 0)
                        itemView.contentDescription = item.title
                    }
                }
            }
        }
    }

    private inner class AvailableToolbarAdapter(
        private val onToggle: (InputMethodSettingsBridge.ToolbarDisplayItem) -> Unit,
    ) : RecyclerView.Adapter<AvailableToolbarAdapter.AvailableViewHolder>() {
        private val items = mutableListOf<InputMethodSettingsBridge.ToolbarDisplayItem>()
        private var selected = emptySet<InputMethodSettingsBridge.ToolbarDisplayItem>()

        fun submit(
            allItems: List<InputMethodSettingsBridge.ToolbarDisplayItem>,
            selectedItems: Set<InputMethodSettingsBridge.ToolbarDisplayItem>,
        ) {
            items.clear()
            items.addAll(allItems)
            selected = selectedItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvailableViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_customize_toolbar_available_option, parent, false)
            return AvailableViewHolder(view)
        }

        override fun onBindViewHolder(holder: AvailableViewHolder, position: Int) {
            holder.bind(items[position], items[position] in selected)
        }

        override fun getItemCount(): Int = items.size

        inner class AvailableViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconFrame = itemView.findViewById<FrameLayout>(R.id.layout_customize_toolbar_option_icon)
            private val icon = itemView.findViewById<ImageView>(R.id.image_customize_toolbar_option_icon)
            private val label = itemView.findViewById<TextView>(R.id.text_customize_toolbar_option_label)

            fun bind(item: InputMethodSettingsBridge.ToolbarDisplayItem, isSelected: Boolean) {
                when (item) {
                    is InputMethodSettingsBridge.ToolbarDisplayItem.Builtin -> {
                        val iconRes = OemToolbarIconHelper.toolbarIconRes(item.item)
                        icon.setImageResource(iconRes)
                        tintAvailable(icon, isSelected)
                        label.text = getString(item.item.labelRes)
                        OemToolbarIconHelper.applyAvailableOptionIconStyle(icon, itemView.context, iconRes)
                    }
                    is InputMethodSettingsBridge.ToolbarDisplayItem.Custom -> {
                        icon.imageTintList = null
                        icon.setImageDrawable(
                            item.icon ?: itemView.context.getDrawable(R.drawable.ic_oem_toolbar_custom_placeholder)
                        )
                        label.text = item.title
                    }
                }
                label.setTextColor(android.graphics.Color.parseColor(if (isSelected) "#22B59A" else "#5D6368"))
                iconFrame.setBackgroundResource(
                    if (isSelected) R.drawable.bg_customize_toolbar_editor_icon_selected
                    else R.drawable.bg_customize_toolbar_editor_icon
                )
                itemView.setOnClickListener { onToggle(item) }
            }
        }
    }

    private class HorizontalSpacingDecoration(
        private val spacePx: Int,
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            if (position > 0) {
                outRect.left = spacePx
            }
        }
    }

    private class GridSpacingDecoration(
        private val spanCount: Int,
        private val horizontalSpacePx: Int,
        private val verticalSpacePx: Int,
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            val column = position % spanCount
            outRect.left = horizontalSpacePx
            outRect.right = horizontalSpacePx
            if (position >= spanCount) {
                outRect.top = verticalSpacePx
            }
        }
    }
}
