/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar.ui

import android.content.Context
import org.fcitx.fcitx5.android.data.theme.SkinLabStandard
import android.view.View
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
    private val rowHeight = if (SkinLabStandard.enabled(theme)) SkinLabStandard.bar(ctx) else if (outside) style!!.barBodyHeightDp else 40

    fun updateRowHeight(heightDp: Int) {
        if (!outside) return
        for (child in listOf(expandButton, horizontalView)) {
            child.layoutParams?.let { it.height = ctx.dp(heightDp.coerceIn(36, 80)); child.layoutParams = it }
        }
    }

    val expandButton = ToolButton(ctx, R.drawable.ic_baseline_expand_more_24, theme).apply {
        id = R.id.expand_candidate_btn
        visibility = View.INVISIBLE
    }

    val preeditTextView = textView {
        setTextColor(theme.candidateTextColor)
        textSize = 14f
        isSingleLine = true
        if (outside) includeFontPadding = false
        visibility = View.GONE
    }

    override val root = ctx.constraintLayout {
        add(preeditTextView, lParams(matchConstraints, if (outside) dp(style!!.reservedPreeditDp) else wrapContent) {
            topOfParent()
            startOfParent()
            endOfParent()
            topMargin = if (outside) 0 else dp(2)
            marginStart = dp(5)
        })
        add(expandButton, lParams(dp(40), dp(rowHeight)) {
            bottomOfParent()
            endOfParent()
            bottomMargin = if (outside) 0 else dp(2)
        })
        add(horizontalView, lParams(matchConstraints, dp(rowHeight)) {
            bottomOfParent()
            startOfParent()
            before(expandButton)
            bottomMargin = if (outside) 0 else dp(2)
            marginStart = if (style?.skinLabStandard == true) dp(SkinLabStandard.gapX(ctx) / 2 + 5.125f).toInt() else dp(-1)
        })
    }
}
