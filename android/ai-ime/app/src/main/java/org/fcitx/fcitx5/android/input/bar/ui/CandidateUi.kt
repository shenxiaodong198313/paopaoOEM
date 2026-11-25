/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar.ui

import android.content.Context
import org.fcitx.fcitx5.android.data.theme.SkinLabStandard
import android.view.View
import android.view.Gravity
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent

class CandidateUi(override val ctx: Context, theme: Theme, private val horizontalView: View) : Ui {
    private val style = (theme as? Theme.Custom)?.renderStyle
    private val outside = style?.preeditOutsideBar == true
    private val topSpacing = style?.preeditInTopSpacing == true
    private val topHeight = style?.topSpacingHeightDp((theme as? Theme.Custom)?.toolbarTopInsetDp ?: 0) ?: 0
    private val inlinePreedit = style?.inlinePreeditHeightDp?.takeIf { !outside && !topSpacing }
    private var bodyHeight = style?.barBodyHeightDp ?: 40
    private val rowHeight = if (SkinLabStandard.enabled(theme)) SkinLabStandard.bar(ctx) else if (outside) style!!.barBodyHeightDp else 40

    fun updateRowHeight(heightDp: Int) {
        if (inlinePreedit != null) {
            bodyHeight = heightDp
            val textHeight = kotlin.math.ceil(preeditTextView.textSize / ctx.resources.displayMetrics.density * 1.2f).toInt()
            val reserved = if (preeditTextView.visibility == View.VISIBLE) maxOf(inlinePreedit, textHeight) else 0
            val row = (heightDp - reserved).coerceAtLeast(24)
            for (child in listOf(expandButton, horizontalView)) {
                child.layoutParams?.let { it.height = ctx.dp(row); child.layoutParams = it }
            }
            return
        }
        if (!outside && !topSpacing) return
        for (child in listOf(expandButton, horizontalView)) {
            child.layoutParams?.let { it.height = ctx.dp(heightDp.coerceIn(36, 80)); child.layoutParams = it }
        }
    }

    fun updatePreedit(value: String) {
        val wasVisible = preeditTextView.visibility == View.VISIBLE
        preeditTextView.text = value
        preeditTextView.visibility = if (value.isNotEmpty()) View.VISIBLE else View.GONE
        if (inlinePreedit != null && wasVisible != value.isNotEmpty()) updateRowHeight(bodyHeight)
    }

    val expandButton = ToolButton(ctx, R.drawable.ic_baseline_expand_more_24, theme).apply {
        id = R.id.expand_candidate_btn
        visibility = View.INVISIBLE
    }

    val preeditTextView = textView {
        // An outside preedit sits on the keyboard background, while candidates
        // sit on the toolbar material; mixed light/dark skins need separate ink.
        setTextColor(style?.localConfiguration?.preeditTextColor?.takeIf { it != 0 } ?: if (outside || topSpacing) style?.preeditTextColor ?: theme.candidateTextColor else theme.candidateTextColor)
        textSize = (if (inlinePreedit != null || topSpacing) 12f else 14f) * (style?.localConfiguration?.preeditTextScale ?: 100) / 100f
        gravity = Gravity.CENTER_VERTICAL
        isSingleLine = true
        if (outside || inlinePreedit != null || topSpacing) includeFontPadding = false
        if (topSpacing) setPadding(0, ctx.dp(2), 0, ctx.dp(2))
        visibility = View.GONE
    }

    override val root = ctx.constraintLayout {
        add(preeditTextView, lParams(matchConstraints, if (topSpacing) dp(topHeight) else if (outside) dp(style!!.reservedPreeditDp) else wrapContent) {
            topOfParent()
            startOfParent()
            endOfParent()
            topMargin = if (outside || inlinePreedit != null || topSpacing) 0 else dp(2)
            marginStart = dp(style?.localConfiguration?.preeditLeft ?: 5)
        })
        add(expandButton, lParams(dp(40), dp(rowHeight)) {
            bottomOfParent()
            endOfParent()
            bottomMargin = if (outside || inlinePreedit != null || topSpacing) 0 else dp(2)
        })
        add(horizontalView, lParams(matchConstraints, dp(rowHeight)) {
            bottomOfParent()
            startOfParent()
            before(expandButton)
            bottomMargin = if (outside || inlinePreedit != null || topSpacing) 0 else dp(2)
            marginStart = if (style?.skinLabStandard == true) dp(SkinLabStandard.gapX(ctx) / 2 + 5.125f).toInt() else dp(-1)
        })
    }
}
