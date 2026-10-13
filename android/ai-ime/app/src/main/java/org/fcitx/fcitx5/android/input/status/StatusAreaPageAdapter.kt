package org.fcitx.fcitx5.android.input.status

import android.annotation.SuppressLint
import org.fcitx.fcitx5.android.data.theme.SkinLabStandard
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp

abstract class StatusAreaPageAdapter : RecyclerView.Adapter<StatusAreaPageAdapter.Holder>() {
    inner class Holder(val rootLayout: LinearLayout) : RecyclerView.ViewHolder(rootLayout) {
        private val cells = List(PAGE_SIZE) {
            FrameLayout(rootLayout.context).apply {
                val standard = SkinLabStandard.enabled(theme)
                val columns = if (standard && SkinLabStandard.landscape(rootLayout.context)) 8 else COLUMN_COUNT
                val row = it / columns
                val topMargin = if (row == 0 || standard) 0 else rootLayout.context.dp(4)
                val sideMargin = if (standard) 0 else rootLayout.context.dp(3)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = sideMargin
                    marginEnd = sideMargin
                    if (topMargin > 0) {
                        this.topMargin = topMargin
                    }
                }
                rootLayout.findViewWithTag<LinearLayout>("row_$row")?.addView(this)
            }
        }

        private val entryUis = List(PAGE_SIZE) { StatusAreaEntryUi(rootLayout.context, theme, fitAvailableHeight = true) }

        init {
            entryUis.forEachIndexed { index, ui ->
                cells[index].addView(ui.root)
            }
        }

        fun bind(entries: List<StatusAreaEntry>) {
            entryUis.forEachIndexed { index, ui ->
                val entry = entries.getOrNull(index)
                val cell = cells[index]
                if (entry == null) {
                    cell.visibility = View.INVISIBLE
                    ui.root.setOnClickListener(null)
                } else {
                    cell.visibility = View.VISIBLE
                    ui.setEntry(entry)
                    ui.root.setOnClickListener { onItemClick(it, entry) }
                }
            }
        }
    }

    var pages: List<List<StatusAreaEntry>> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val page = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                if (View.MeasureSpec.getMode(heightMeasureSpec) != View.MeasureSpec.UNSPECIFIED) {
                    if (SkinLabStandard.enabled(theme)) {
                        val rows = if (SkinLabStandard.landscape(context)) 1 else 2
                        val block = context.dp(72 * rows + 22 * (rows - 1))
                        val top = ((View.MeasureSpec.getSize(heightMeasureSpec) - context.dp(36) - block) / 2).coerceAtLeast(0)
                        val side = context.dp(if (SkinLabStandard.landscape(context)) 6 else 3)
                        val columns = PAGE_SIZE / rows
                        val gapX = ((View.MeasureSpec.getSize(widthMeasureSpec) + side * 2 - context.dp(72) * columns).toFloat() / (columns + 1)).toInt()
                        setPadding(gapX - side, top, gapX - side, 0)
                        repeat(childCount) { row ->
                            val line = getChildAt(row) as LinearLayout
                            repeat(line.childCount) { column ->
                                line.getChildAt(column).layoutParams = LinearLayout.LayoutParams(context.dp(72), context.dp(72)).apply {
                                    marginEnd = if (column < columns - 1) gapX else 0
                                }
                            }
                            getChildAt(row).layoutParams = (getChildAt(row).layoutParams as LinearLayout.LayoutParams).apply {
                                height = context.dp(72); topMargin = if (row == 0) 0 else context.dp(22)
                            }
                        }
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                        return
                    }
                    val gap = context.dp(4)
                    val available = (View.MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom - gap).coerceAtLeast(0)
                    // Keep the original spacing on tall keyboards, but fit both rows inside compact skins.
                    val cellHeight = (available / ROW_COUNT).coerceAtMost(context.dp(94))
                    repeat(childCount) { row ->
                        getChildAt(row).layoutParams.height = cellHeight + if (row == 0) 0 else gap
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(8))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            repeat(if (SkinLabStandard.enabled(theme) && SkinLabStandard.landscape(context)) 1 else ROW_COUNT) { rowIndex ->
                addView(
                    LinearLayout(context).apply {
                        tag = "row_$rowIndex"
                        orientation = LinearLayout.HORIZONTAL
                        // CENTER_VERTICAL adds half the top margin again, clipping the second row.
                        gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                )
            }
        }
        return Holder(page)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount() = pages.size

    abstract val theme: Theme

    abstract fun onItemClick(view: View, entry: StatusAreaEntry)

    companion object {
        const val PAGE_SIZE = 8
        private const val COLUMN_COUNT = 4
        private const val ROW_COUNT = 2
    }
}
