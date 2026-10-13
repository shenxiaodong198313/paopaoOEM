/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.horizontal

import android.content.res.Configuration
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.BooleanKey.ExpandedCandidatesEmpty
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.TransitionEvent.ExpandedCandidatesUpdated
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder
import org.fcitx.fcitx5.android.input.candidates.expanded.decoration.FlexboxVerticalDecoration
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateMode.AlwaysFillWidth
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateMode.AutoFillWidth
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateMode.NeverFillWidth
import org.fcitx.fcitx5.android.input.dependency.UniqueViewComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.inputView
import org.fcitx.fcitx5.android.input.dependency.theme
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp
import kotlin.math.max

class HorizontalCandidateComponent :
    UniqueViewComponent<HorizontalCandidateComponent, RecyclerView>(), InputBroadcastReceiver {

    private val service by manager.inputMethodService()
    private val context by manager.context()
    private val fcitx by manager.fcitx()
    private val theme by manager.theme()
    private val inputView by manager.inputView()
    private val bar: KawaiiBarComponent by manager.must()

    private val fillStyle by AppPrefs.getInstance().keyboard.horizontalCandidateStyle
    private val maxSpanCountPref by lazy {
        AppPrefs.getInstance().keyboard.run {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                expandedCandidateGridSpanCount
            else
                expandedCandidateGridSpanCountLandscape
        }
    }

    private var layoutMinWidth = 0
    private var layoutItemWidth = ViewGroup.LayoutParams.WRAP_CONTENT
    private var isLoadingMore = false

    private val _expandedCandidateOffset = MutableSharedFlow<Int>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val expandedCandidateOffset = _expandedCandidateOffset.asSharedFlow()

    private fun refreshExpanded(childCount: Int) {
        _expandedCandidateOffset.tryEmit(childCount)
        bar.expandButtonStateMachine.push(
            ExpandedCandidatesUpdated,
            ExpandedCandidatesEmpty to (adapter.total == childCount)
        )
    }

    val adapter: HorizontalCandidateViewAdapter by lazy {
        object : HorizontalCandidateViewAdapter(theme) {
            override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                holder.itemView.updateLayoutParams<RecyclerView.LayoutParams> {
                    width = layoutItemWidth
                    if (width == ViewGroup.LayoutParams.WRAP_CONTENT) {
                        holder.itemView.minimumWidth = max(layoutMinWidth, context.dp(40))
                    } else {
                        holder.itemView.minimumWidth = 0
                    }
                    // Standard CandidateUi already places the first highlight 4 design units
                    // inside the visible bar. The legacy item margin must not be added again.
                    if (position == 0 && !org.fcitx.fcitx5.android.data.theme.SkinLabStandard.enabled(theme)) {
                        marginStart = context.dp(3)
                    } else {
                        marginStart = 0
                    }
                }
                holder.itemView.setOnClickListener {
                    fcitx.launchOnReady { it.select(holder.idx) }
                }
                holder.itemView.setOnLongClickListener {
                    inputView.showCandidateActionMenu(
                        holder.idx,
                        holder.candidate.textWithComment(),
                        holder.ui.root
                    )
                    true
                }
            }

            override fun onViewRecycled(holder: CandidateViewHolder) {
                holder.itemView.setOnClickListener(null)
                holder.itemView.setOnLongClickListener(null)
                super.onViewRecycled(holder)
            }
        }
    }

    val layoutManager: LinearLayoutManager by lazy {
        object : LinearLayoutManager(context, RecyclerView.HORIZONTAL, false) {
            override fun onLayoutCompleted(state: RecyclerView.State) {
                super.onLayoutCompleted(state)
                refreshExpanded(childCount)
            }
        }
    }

    private val dividerDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val intrinsicSize = max(1, context.dp(1))
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = theme.dividerColor
        }
    }

    override val view by lazy {
        object : RecyclerView(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                updateLayoutParamsForSize(w)
                if (w != oldw) {
                    post { this@HorizontalCandidateComponent.adapter.notifyDataSetChanged() }
                }
            }
        }.apply {
            id = R.id.candidate_view
            itemAnimator = null
            adapter = this@HorizontalCandidateComponent.adapter
            layoutManager = this@HorizontalCandidateComponent.layoutManager
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dx > 0 && !isLoadingMore) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        val visibleItemCount = lm.childCount
                        val totalItemCount = lm.itemCount
                        val pastVisibleItems = lm.findFirstVisibleItemPosition()

                        if ((visibleItemCount + pastVisibleItems) >= totalItemCount - 5) {
                            if (this@HorizontalCandidateComponent.adapter.total == -1 || totalItemCount < this@HorizontalCandidateComponent.adapter.total) {
                                loadMoreCandidates(totalItemCount)
                            }
                        }
                    }
                }
            })
        }
    }

    private fun loadMoreCandidates(startIndex: Int) {
        isLoadingMore = true
        service.lifecycleScope.launch {
            val pageSize = 20
            val newCandidates = fcitx.runOnReady {
                getCandidates(startIndex, pageSize)
            }
            if (newCandidates.isNotEmpty()) {
                val cleanCandidates = if (service.isT9KeyboardChineseMode()) {
                    newCandidates.map { candidate ->
                        candidate.copy(
                            text = candidate.text.replace(Regex(" [a-zA-Z ']+$"), ""),
                            comment = ""
                        )
                    }.toTypedArray()
                } else {
                    newCandidates
                }
                this@HorizontalCandidateComponent.adapter.appendCandidates(cleanCandidates)
            }
            isLoadingMore = false
        }
    }

    private fun updateLayoutParamsForSize(w: Int) {
        val maxSpanCount = maxSpanCountPref.getValue()
        val candidatesCount = adapter.candidates.size
        if (w <= 0) return
        when (fillStyle) {
            NeverFillWidth -> {
                layoutMinWidth = 0
                layoutItemWidth = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            AutoFillWidth -> {
                layoutMinWidth = w / maxSpanCount - dividerDrawable.intrinsicWidth
                if (candidatesCount in 1 until maxSpanCount) {
                    layoutItemWidth = (w - (candidatesCount - 1) * dividerDrawable.intrinsicWidth) / candidatesCount
                } else {
                    layoutItemWidth = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }
            AlwaysFillWidth -> {
                layoutMinWidth = w / maxSpanCount - dividerDrawable.intrinsicWidth
                layoutItemWidth = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
    }

    override fun onCandidateUpdate(data: FcitxEvent.CandidateListEvent.Data) {
        val rawCandidates = data.candidates
        val candidates = if (service.isT9KeyboardChineseMode()) {
            rawCandidates.map { candidate ->
                timber.log.Timber.d("RAW CANDIDATE: '%s'", candidate.textWithComment())
                candidate.copy(
                    text = candidate.text.replace(Regex(" [a-zA-Z ']+$"), ""),
                    comment = ""
                )
            }.toTypedArray()
        } else {
            rawCandidates
        }
        val total = data.total

        // Temporarily set adapter's candidates to calculate dimensions accurately
        val oldCandidates = adapter.candidates
        val oldTotal = adapter.total
        adapter.updateCandidates(candidates, total)
        updateLayoutParamsForSize(view.width)
        
        isLoadingMore = false
        view.scrollToPosition(0)

        if (candidates.isEmpty()) {
            refreshExpanded(0)
        }
    }
}
