/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.expanded.window

import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.CombinedLoadStates
import org.fcitx.fcitx5.android.input.candidates.expanded.CandidateHandwritingPage
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.core.CandidateAction
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.BooleanKey.ExpandedCandidatesEmpty
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.TransitionEvent.ExpandedCandidatesAttached
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.TransitionEvent.ExpandedCandidatesDetached
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder
import org.fcitx.fcitx5.android.input.candidates.expanded.CandidateTabActionsAdapter
import org.fcitx.fcitx5.android.input.candidates.expanded.CandidatesPagingSource
import org.fcitx.fcitx5.android.input.candidates.expanded.ExpandedCandidateLayout
import org.fcitx.fcitx5.android.input.candidates.expanded.PagingCandidateViewAdapter
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateComponent
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.inputView
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp
import splitties.views.recyclerview.verticalLayoutManager
import kotlin.math.max

abstract class BaseExpandedCandidateWindow<T : BaseExpandedCandidateWindow<T>> :
    InputWindow.SimpleInputWindow<T>(), InputBroadcastReceiver {

    protected val service by manager.inputMethodService()
    protected val theme by manager.theme()
    protected val fcitx by manager.fcitx()
    protected val inputView by manager.inputView()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val bar: KawaiiBarComponent by manager.must()
    private val horizontalCandidate: HorizontalCandidateComponent by manager.must()
    private val windowManager: InputWindowManager by manager.must()
    private val returnKeyDrawable: ReturnKeyDrawableComponent by manager.must()

    protected val disableAnimation by AppPrefs.getInstance().advanced.disableAnimation

    private lateinit var candidateLayout: ExpandedCandidateLayout

    protected val dividerDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val intrinsicSize = max(1, context.dp(1))
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = theme.dividerColor
        }
    }

    abstract fun onCreateCandidateLayout(): ExpandedCandidateLayout

    final override fun onCreateView(): View {
        candidateLayout = onCreateCandidateLayout().apply {
            scrollableTabs.apply {
                adapter = tabsAdapter
                layoutManager = verticalLayoutManager()
            }
            pinnedTabs.apply {
                adapter = pinnedTabsAdapter
                layoutManager = verticalLayoutManager()
            }
        }
        return candidateLayout
    }

    private val keyActionListener = KeyActionListener { it, source ->
        if (it is KeyAction.LayoutSwitchAction) {
            when (it.act) {
                ExpandedCandidateLayout.Keyboard.UpBtnLabel -> prevPage()
                ExpandedCandidateLayout.Keyboard.DownBtnLabel -> nextPage()
            }
        } else {
            commonKeyActionListener.listener.onKeyAction(it, source)
        }
    }

    abstract val adapter: PagingCandidateViewAdapter
    abstract val layoutManager: RecyclerView.LayoutManager

    val tabsAdapter by lazy {
        object : CandidateTabActionsAdapter(theme, false) {
            override fun onTriggerTabAction(id: Int) {
                fcitx.launchOnReady { it.triggerCandidateListTabAction(id) }
            }
        }
    }

    val pinnedTabsAdapter by lazy {
        object : CandidateTabActionsAdapter(theme, true) {
            override fun onTriggerTabAction(id: Int) {
                fcitx.launchOnReady { it.triggerCandidateListTabAction(id) }
            }
        }
    }

    private fun updateTabs(newTabs: Array<CandidateAction>) {
        val tabs = newTabs.takeWhile { !it.isSeparator }
        val pinnedTabs = newTabs.drop(tabs.size + 1).filter { !it.isSeparator }
        if (tabs.isEmpty() && pinnedTabs.isEmpty()) {
            candidateLayout.tabsContainer.visibility = View.GONE
        } else {
            candidateLayout.tabsContainer.visibility = View.VISIBLE
        }
        tabsAdapter.updateTabs(tabs)
        pinnedTabsAdapter.updateTabs(pinnedTabs)
    }

    private var totalCandidates = 0
    private var revision = 0L
    private var inkPage: CandidateHandwritingPage? = null
    private var selectingInk = false
    private val loadingChanged: (CombinedLoadStates) -> Unit = { state ->
        val empty = candidateLayout.page == ExpandedCandidateLayout.Page.Single &&
            state.refresh is LoadState.NotLoading && state.append.endOfPaginationReached && adapter.itemCount == 0
        candidateLayout.emptyMessage.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun changePage(page: ExpandedCandidateLayout.Page) {
        revision++
        if (page == ExpandedCandidateLayout.Page.Ink) {
            val ink = inkPage ?: CandidateHandwritingPage(service, candidateLayout.panel, ::selectInk).also {
                candidateLayout.inkContainer.addView(it)
                inkPage = it
            }
            ink.open()
        } else {
            inkPage?.close()
            candidateLayout.resetPosition()
            adapter.refresh()
        }
    }

    private fun selectInk(word: String) {
        if (selectingInk) return
        selectingInk = true
        val request = revision
        service.selectHandwrittenCandidate(word, bar.candidateReading, {
            revision == request && candidateLayout.isShown && windowManager.isAttached(this)
        }) { selectingInk = false }
    }

    private val candidatesPager by lazy {
        Pager(
            config = PagingConfig(
                pageSize = 48,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                CandidatesPagingSource(
                    fcitx,
                    total = totalCandidates,
                    isT9Mode = service.isT9KeyboardChineseMode(),
                    singleOnly = candidateLayout.page == ExpandedCandidateLayout.Page.Single,
                    revision = revision,
                    chineseOnly = service.usesChineseCandidateGrid()
                )
            }
        )
    }
    private var candidatesSubmitJob: Job? = null

    abstract fun prevPage()

    abstract fun nextPage()

    override fun onAttached() {
        totalCandidates = horizontalCandidate.adapter.total
        bar.expandButtonStateMachine.push(ExpandedCandidatesAttached)
        candidateLayout.embeddedKeyboard.also {
            it.onReturnDrawableUpdate(returnKeyDrawable.resourceId)
            it.keyActionListener = keyActionListener
        }
        candidateLayout.onCollapse = { windowManager.attachWindow(KeyboardWindow) }
        candidateLayout.onPageChanged = ::changePage
        adapter.addLoadStateListener(loadingChanged)
        updateTabs(fcitx.runImmediately { inputPanelCached.tabs })
        candidatesSubmitJob = service.lifecycleScope.launch {
            candidatesPager.flow.collectLatest {
                adapter.submitData(it)
            }
        }
    }

    fun bindCandidateUiViewHolder(holder: CandidateViewHolder, position: Int) {
        val boundRevision = adapter.peek(position)?.revision
        holder.itemView.setOnClickListener {
            val index = holder.idx
            if (index >= 0 && boundRevision == revision) {
                val accepted = holder.ui.captureSelectionFeedback()
                fcitx.launchOnReady { if (it.select(index)) accepted() }
            }
        }
        holder.itemView.setOnLongClickListener {
            if (boundRevision != revision) return@setOnLongClickListener false
            inputView.showCandidateActionMenu(
                holder.idx,
                holder.candidate.textWithComment(),
                holder.ui.root
            )
            true
        }
    }

    fun recycleCandidateViewHolder(holder: CandidateViewHolder) {
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
    }

    override fun onDetached() {
        bar.expandButtonStateMachine.push(
            ExpandedCandidatesDetached,
            ExpandedCandidatesEmpty to horizontalCandidate.allCandidatesFit
        )
        revision++
        inkPage?.close()
        adapter.removeLoadStateListener(loadingChanged)
        candidatesSubmitJob?.cancel()
        candidateLayout.embeddedKeyboard.keyActionListener = null
    }

    override fun onCandidateUpdate(data: FcitxEvent.CandidateListEvent.Data) {
        if (data.candidates.isEmpty()) {
            windowManager.attachWindow(KeyboardWindow)
        } else {
            totalCandidates = data.total
            revision++
            inkPage?.clear()
            if (candidateLayout.page != ExpandedCandidateLayout.Page.Ink) {
                candidateLayout.resetPosition()
                adapter.refresh()
            }
        }
    }

    override fun onPreeditEmptyStateUpdate(empty: Boolean) {
        if (empty) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    override fun onInputPanelUpdate(data: FcitxEvent.InputPanelEvent.Data) {
        updateTabs(data.tabs)
    }

}
