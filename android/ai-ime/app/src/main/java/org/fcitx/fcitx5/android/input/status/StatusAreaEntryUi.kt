/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

import android.content.Context
import org.fcitx.fcitx5.android.data.theme.SkinLabStandard
import android.graphics.Typeface
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.icu.text.BreakIterator
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.AutoScaleTextView
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerOn
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.imageDrawable

class StatusAreaEntryUi(
    override val ctx: Context,
    private val theme: Theme,
    private val fitAvailableHeight: Boolean = false
) : Ui {

    private val standard = SkinLabStandard.enabled(theme)
    private val palette = ToolPanelPalette(theme.backgroundColor, theme.keyTextColor, theme.accentKeyBackgroundColor)

    private val bkgDrawable = ShapeDrawable(
        RoundRectShape(
            FloatArray(8) { ctx.dp(16).toFloat() },
            null,
            null
        )
    )

    val bkg = frameLayout {
        background = bkgDrawable
    }

    val icon = imageView {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    val textIcon = view(::AutoScaleTextView) {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
        // keep original typeface, apply textStyle only
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 600 = Semi Bold, 700 = Bold which is too heavy
            typeface = Typeface.create(typeface, 600, false)
        } else {
            setTypeface(typeface, Typeface.BOLD)
        }
    }

    val label = textView {
        textSize = if (standard) 11.5f else 13f
        gravity = gravityCenter
        maxLines = 2
        setTextColor(theme.keyTextColor)
    }

    override val root = object : CustomGestureView(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (!standard && fitAvailableHeight && View.MeasureSpec.getMode(heightMeasureSpec) != View.MeasureSpec.UNSPECIFIED) {
                val width = View.MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(dp(74))
                val height = View.MeasureSpec.getSize(heightMeasureSpec)
                if (label.layoutParams.width != width) {
                    label.layoutParams = label.layoutParams.apply { this.width = width }
                }
                label.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                // Reserve the label's actual font height; shrink icon padding, not text or the icon.
                val backgroundHeight = (height - dp(12) - label.measuredHeight).coerceIn(dp(24), dp(60))
                if (bkg.layoutParams.height != backgroundHeight) {
                    // ConstraintLayout must invalidate its cached constraints when this size changes.
                    bkg.layoutParams = bkg.layoutParams.apply { this.height = backgroundHeight }
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        val content = constraintLayout {
            add(bkg, lParams(dp(if (standard) 72 else 60), dp(if (standard) 72 else 60)) {
                topOfParent(dp(if (standard) 0 else 6)); centerHorizontally()
                if (!standard) above(label)
            })
            add(icon, lParams(dp(if (standard) 25 else 24), dp(if (standard) 25 else 24)) {
                if (standard) { topOfParent(dp(13.42f).toInt()); centerHorizontally() } else centerOn(bkg)
            })
            add(textIcon, lParams(dp(24), dp(24)) {
                if (standard) { topOfParent(dp(13.92f).toInt()); centerHorizontally() } else centerOn(bkg)
            })
            add(label, lParams(dp(if (standard) 72 else 74), if (standard) dp(20) else wrapContent) {
                if (standard) topOfParent(dp(44.72f).toInt()) else below(bkg, dp(6))
                centerHorizontally()
            })
        }

        init {
            add(content, lParams(matchParent, matchParent))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (fitAvailableHeight) ViewGroup.LayoutParams.MATCH_PARENT else dp(94)
            )
        }
    }

    fun setEntry(entry: StatusAreaEntry) {
        val contentColor =
            palette.ink
        if (entry is StatusAreaEntry.Custom) {
            val customDrawable = entry.iconDrawable
            if (customDrawable != null) {
                icon.visibility = View.VISIBLE
                textIcon.visibility = View.GONE
                icon.imageDrawable = customDrawable
            } else {
                icon.visibility = View.GONE
                textIcon.visibility = View.VISIBLE
                textIcon.text = getFirstCharacter(entry.label)
                textIcon.setTextColor(contentColor)
            }
        } else if (entry.icon != 0) {
            icon.visibility = View.VISIBLE
            textIcon.visibility = View.GONE
            icon.imageDrawable = ctx.drawable(entry.icon)!!.apply {
                setTint(contentColor)
            }
        } else {
            icon.visibility = View.GONE
            textIcon.visibility = View.VISIBLE
            textIcon.text = getFirstCharacter(entry.label)
            textIcon.setTextColor(contentColor)
        }
        bkgDrawable.paint.color =
            if (standard) (if (theme.isDark) theme.keyBackgroundColor else 0xFFF8F8F8.toInt()) else if (entry.active) palette.selectedCard else palette.card
        label.text = entry.label
        // Tool labels sit on the panel, not on a function key. Match the icon.
        label.setTextColor(contentColor)
    }

    private fun getFirstCharacter(s: String): String {
        if (s.isEmpty()) return ""
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(s)
            s.substring(iterator.first(), iterator.next())
        } else {
            s.substring(0, s.offsetByCodePoints(0, 1))
        }
    }
}
