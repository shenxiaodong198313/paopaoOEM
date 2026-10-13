/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.horizontal

import android.annotation.SuppressLint
import org.fcitx.fcitx5.android.data.theme.SkinLabStandard
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.CandidateItemUi
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

open class HorizontalCandidateViewAdapter(val theme: Theme) :
    RecyclerView.Adapter<CandidateViewHolder>() {

    init {
        setHasStableIds(true)
    }

    var candidates: Array<CandidateWord> = arrayOf()
        private set

    var total = -1
        private set

    @SuppressLint("NotifyDataSetChanged")
    fun updateCandidates(data: Array<CandidateWord>, total: Int) {
        this.candidates = data
        this.total = total
        notifyDataSetChanged()
    }

    fun appendCandidates(newCandidates: Array<CandidateWord>) {
        val oldSize = candidates.size
        candidates += newCandidates
        notifyItemRangeInserted(oldSize, newCandidates.size)
    }

    override fun getItemCount() = candidates.size

    override fun getItemId(position: Int) = candidates.getOrNull(position).hashCode().toLong()

    @CallSuper
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
        val ui = CandidateItemUi(parent.context, theme)
        ui.root.apply {
            minimumWidth = dp(40)
            setPaddingDp(if (SkinLabStandard.enabled(theme)) 0 else 2, 0, if (SkinLabStandard.enabled(theme)) 4 else 2, 0)
            layoutParams = RecyclerView.LayoutParams(wrapContent, matchParent)
        }
        return CandidateViewHolder(ui)
    }

    @CallSuper
    override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
        holder.update(position, candidates[position])
        // AutoScaleTextView draws a String using currentTextColor; spans are discarded.
        // Set both branches on every bind, including unchanged words and recycled holders.
        holder.ui.text.setTextColor(
            if (SkinLabStandard.enabled(theme) && position == 0) android.graphics.Color.WHITE
            else theme.candidateTextColor
        )
        if (SkinLabStandard.enabled(theme)) {
            val context = holder.ui.root.context
            holder.ui.text.layoutParams = (holder.ui.text.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                height = context.dp(SkinLabStandard.bar(context) - 11.5f).toInt()
                gravity = android.view.Gravity.CENTER
            }
            holder.ui.text.translationY = -context.dp(.875f).toFloat()

        }
        if (position == 0) {
            val bgColor = if (SkinLabStandard.enabled(theme)) 0xFF2F78FF.toInt() else (theme.keyTextColor and 0x00FFFFFF) or 0x1A000000
            holder.ui.text.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = holder.ui.root.context.dp(if (SkinLabStandard.enabled(theme)) 5 else 2).toFloat()
            }
        } else {
            holder.ui.text.background = null
        }
    }

    @CallSuper
    override fun onViewRecycled(holder: CandidateViewHolder) {
        holder.clear()
    }

}
