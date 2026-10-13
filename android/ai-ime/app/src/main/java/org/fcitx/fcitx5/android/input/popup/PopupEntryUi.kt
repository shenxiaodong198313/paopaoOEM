/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.popup

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.ViewOutlineProvider
import android.widget.TextView
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.AutoScaleTextView
import org.fcitx.fcitx5.android.input.keyboard.latinKeyDisplayLabel
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.gravityCenter

class PopupEntryUi(override val ctx: Context, theme: Theme, keyHeight: Int, radius: Float) : Ui {
    val standard = org.fcitx.fcitx5.android.data.theme.SkinLabStandard.enabled(theme)
    private var displayedText = ""
    val standardPreview = if (standard) StandardPopupPreview(ctx, theme) else null
    fun configureStandard(left: Float, top: Float) { standardPreview?.configure(displayedText, left, top) }


    var lastShowTime = -1L

    val textView = view(::AutoScaleTextView) {
        textSize = 23f
        gravity = gravityCenter
        setPadding(dp(12), 0, dp(12), 0)
        scaleMode = AutoScaleTextView.Mode.Proportional
        setTextColor(theme.popupTextColor)
    }

    override val root = constraintLayout {
        background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(theme.popupBackgroundColor)
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = if (standard) 0f else dp(2f)
        if (standard) background = null
        if (standardPreview != null) add(standardPreview, lParams(matchParent, matchParent) {})
        textView.visibility = if (standard) android.view.View.GONE else android.view.View.VISIBLE
        add(textView, lParams(matchParent, keyHeight) {
            topOfParent()
            centerHorizontally()
        })
    }

    fun setText(text: String) {
        // All single-key preview paths share this display rule, independent of skin
        // revision and Shift. Committed input and long-press choices stay untouched.
        displayedText = latinKeyDisplayLabel(text)
        textView.text = displayedText
    }

    fun preferredWidth(): Int = kotlin.math.ceil(textView.paint.measureText(textView.text.toString()))
        .toInt() + textView.paddingLeft + textView.paddingRight
}
