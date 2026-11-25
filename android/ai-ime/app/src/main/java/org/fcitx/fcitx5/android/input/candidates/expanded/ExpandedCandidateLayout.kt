/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.candidates.expanded

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageButton
import android.view.Gravity
import androidx.constraintlayout.widget.ConstraintLayout
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.keyboard.BackspaceKey
import org.fcitx.fcitx5.android.input.keyboard.BaseKeyboard
import org.fcitx.fcitx5.android.input.keyboard.ImageKeyView
import org.fcitx.fcitx5.android.input.keyboard.ImageLayoutSwitchKey
import org.fcitx.fcitx5.android.input.keyboard.KeyDef
import org.fcitx.fcitx5.android.input.keyboard.ReturnKey
import org.fcitx.fcitx5.android.utils.setVerticalScrollbarThumbColor
import org.fcitx.fcitx5.android.utils.singleSideBorderDrawable
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.leftToRightOf
import splitties.views.dsl.constraintlayout.rightOfParent
import splitties.views.dsl.constraintlayout.rightToLeftOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.imageResource

@SuppressLint("ViewConstructor")
class ExpandedCandidateLayout(context: Context, theme: Theme) : ConstraintLayout(context) {
    val panel = org.fcitx.fcitx5.android.input.status.ExpandedCandidatePalette(
        theme.backgroundColor, theme.candidateTextColor, theme.candidateCommentColor, theme.accentKeyBackgroundColor
    )

    class Keyboard(context: Context, theme: Theme) : BaseKeyboard(context, theme, Layout) {
        companion object {
            const val UpBtnLabel = "U"
            const val DownBtnLabel = "D"

            const val UpBtnId = 0xff55
            const val DownBtnId = 0xff56

            val Layout: List<List<KeyDef>> = listOf(
                listOf(
                    ImageLayoutSwitchKey(
                        R.drawable.ic_baseline_arrow_upward_24,
                        to = UpBtnLabel,
                        percentWidth = .25f,
                        variant = KeyDef.Appearance.Variant.Alternative,
                        viewId = UpBtnId
                    ),
                    ImageLayoutSwitchKey(
                        R.drawable.ic_baseline_arrow_downward_24,
                        to = DownBtnLabel,
                        percentWidth = .25f,
                        variant = KeyDef.Appearance.Variant.Alternative,
                        viewId = DownBtnId
                    ),
                    BackspaceKey(percentWidth = .25f, KeyDef.Appearance.Variant.Alternative),
                    ReturnKey(percentWidth = .25f)
                )
            )
        }

        val pageUpBtn: ImageKeyView by lazy { findViewById(UpBtnId) }
        val pageDnBtn: ImageKeyView by lazy { findViewById(DownBtnId) }
        val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
        val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

        override fun onReturnDrawableUpdate(returnDrawable: Int) {
            `return`.img.imageResource = returnDrawable
        }
    }

    private val keyBorder by ThemeManager.prefs.keyBorder
    private val keyVerticalMargin by ThemeManager.prefs.keyVerticalMargin
    private val keyVerticalMarginLandscape by ThemeManager.prefs.keyVerticalMarginLandscape

    val recyclerView = recyclerView {
        // disable item cross-fade animation
        itemAnimator = null
        isVerticalScrollBarEnabled = false
    }

    val tabsContainer = constraintLayout()

    val scrollableTabs = recyclerView {
        itemAnimator = null
        // always show scrollbar
        isScrollbarFadingEnabled = false
        scrollBarSize = dp(1)
        setVerticalScrollbarThumbColor(panel.ink)
    }

    val pinnedTabs = recyclerView {
        itemAnimator = null
        // prevent scrolling in pinned tabs at bottom
        overScrollMode = OVER_SCROLL_NEVER
        isNestedScrollingEnabled = false
        isVerticalScrollBarEnabled = false
    }

    var pageUpBtn: ImageKeyView
    var pageDnBtn: ImageKeyView

    val embeddedKeyboard = Keyboard(context, theme).also {
        pageUpBtn = it.pageUpBtn
        pageDnBtn = it.pageDnBtn
    }

    enum class Page(val title: String) { All("全部"), Single("单字"), Ink("手写找字") }

    var onPageChanged: (Page) -> Unit = {}
    var onCollapse: () -> Unit = {}
    var page = Page.All
        private set
    private fun pill(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(24).toFloat()
    }
    private val segmented = LinearLayout(context).apply {
        gravity = Gravity.CENTER
        setPadding(dp(3), dp(2), dp(3), dp(2))
        background = pill(panel.pressed)
    }
    private val header = FrameLayout(context).apply {
        id = View.generateViewId()
        addView(segmented, FrameLayout.LayoutParams(-2, dp(32), Gravity.CENTER))
        addView(ImageButton(context).apply {
            id = R.id.expand_candidate_btn
            contentDescription = context.getString(R.string.hide_candidates_list)
            setImageResource(R.drawable.ic_baseline_expand_less_24)
            setColorFilter(panel.ink); setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener { onCollapse() }
        }, FrameLayout.LayoutParams(dp(44), -1, Gravity.END))
    }
    val inkContainer = FrameLayout(context).apply { visibility = GONE }
    val emptyMessage = TextView(context).apply {
        gravity = Gravity.CENTER; setTextColor(panel.ink); text = "当前没有单字候选"; visibility = GONE
    }

    private fun selectPage(next: Page) {
        page = next
        for (i in 0 until segmented.childCount) {
            val tab = segmented.getChildAt(i)
            tab.isSelected = Page.entries[i] == page
            tab.background = if (tab.isSelected) pill(panel.surface) else null
        }
        val ink = page == Page.Ink
        inkContainer.visibility = if (ink) VISIBLE else GONE
        recyclerView.visibility = if (ink) GONE else VISIBLE
        embeddedKeyboard.visibility = if (ink) GONE else VISIBLE
        emptyMessage.visibility = GONE
        onPageChanged(next)
    }

    init {
        id = R.id.expanded_candidate_view
        // Text must be readable even where a decorative keyboard image is bright or dark.
        backgroundColor = panel.surface

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val inset = dp(if (landscape) keyVerticalMarginLandscape else keyVerticalMargin)
        tabsContainer.background =
            singleSideBorderDrawable(dp(1), theme.dividerColor, Gravity.RIGHT, inset)
        if (!keyBorder) {
            embeddedKeyboard.background =
                singleSideBorderDrawable(dp(1), theme.dividerColor, Gravity.TOP, inset)
        }

        Page.entries.forEach { target ->
            segmented.addView(TextView(context).apply {
                text = target.title; textSize = 15f; gravity = Gravity.CENTER
                includeFontPadding = false; setTextColor(panel.ink)
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { if (page != target) selectPage(target) }
            }, LinearLayout.LayoutParams(-2, -1))
        }
        selectPage(Page.All)
        add(header, lParams(width = 0, height = dp(42)) {
            topOfParent(); leftOfParent(); rightOfParent()
        })
        add(tabsContainer, lParams {
            matchConstraintPercentWidth = 0.15f
            below(header)
            leftOfParent()
            bottomOfParent()
        })
        tabsContainer.apply {
            add(scrollableTabs, lParams {
                topOfParent()
                centerHorizontally()
                above(pinnedTabs)
            })
            add(pinnedTabs, lParams(height = wrapContent) {
                bottomOfParent()
                centerHorizontally()
            })
        }
        add(recyclerView, lParams {
            below(header)
            leftToRightOf(tabsContainer)
            rightOfParent()
            above(embeddedKeyboard)
        })
        add(embeddedKeyboard, lParams(height = dp(36)) {
            leftOfParent()
            rightOfParent()
            bottomOfParent()
        })
        add(emptyMessage, lParams {
            below(header); leftToRightOf(tabsContainer); rightOfParent(); above(embeddedKeyboard)
        })
        add(inkContainer, lParams {
            below(header); leftOfParent(); rightOfParent(); bottomOfParent()
        })

    }

    fun resetPosition() {
        recyclerView.scrollToPosition(0)
    }
}
