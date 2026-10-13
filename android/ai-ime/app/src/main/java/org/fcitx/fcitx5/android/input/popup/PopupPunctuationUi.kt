/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.popup

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.ViewOutlineProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.AutoScaleTextView
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import kotlin.math.floor

class PopupPunctuationUi(
    override val ctx: Context,
    theme: Theme,
    outerBounds: Rect,
    triggerBounds: Rect,
    onDismissSelf: PopupContainerUi.() -> Unit = {},
    private val items: Array<String>,
    private val labels: Array<String>,
    private val defaultFocusIndex: Int
) : PopupContainerUi(ctx, theme, outerBounds, triggerBounds, onDismissSelf) {

    private val keyWidth = ctx.dp(38)
    private val keyHeight = ctx.dp(48)
    private val stemHeight = ctx.dp(18)
    private val radius = ctx.dp(ThemeManager.prefs.keyRadius.getValue()).toFloat()
    private val stripWidth = keyWidth * items.size
    private val stemWidth = (triggerBounds.width() - ctx.dp(4)).coerceIn(ctx.dp(20), keyWidth)
    private val stemLeft = (defaultFocusIndex * keyWidth) + (keyWidth - stemWidth) / 2

    override val offsetX = ((triggerBounds.width() - keyWidth) / 2) - (keyWidth * defaultFocusIndex)
    override val offsetY = -(keyHeight + stemHeight)

    private val stripBackground = GradientDrawable().apply {
        cornerRadius = radius
        setColor(theme.popupBackgroundColor)
    }

    private val stemBackground = GradientDrawable().apply {
        cornerRadius = radius
        setColor(theme.popupBackgroundColor)
    }

    private val focusBackground = GradientDrawable().apply {
        cornerRadius = radius
        setColor(theme.genericActiveBackgroundColor)
    }

    private inner class PopupLabelUi(override val ctx: Context, val label: String) : Ui {
        val textView = view(::AutoScaleTextView) {
            text = this@PopupLabelUi.label
            textSize = 22f
            setTextColor(theme.popupTextColor)
        }

        override val root = frameLayout {
            add(textView, lParams(wrapContent, wrapContent) {
                gravity = gravityCenter
            })
        }
    }

    private var focusedIndex = defaultFocusIndex.coerceIn(0, items.lastIndex)
    private val keyUis = labels.map { PopupLabelUi(ctx, it) }

    init {
        markFocus(focusedIndex)
    }

    override val root = frameLayout {
        add(horizontalLayout {
            background = stripBackground
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = radius > 0f
            elevation = dp(2f)
            keyUis.forEach { keyUi ->
                add(keyUi.root, lParams(keyWidth, keyHeight))
            }
        }, lParams(stripWidth, keyHeight))
        add(frameLayout {
            background = stemBackground
            elevation = dp(2f)
        }, lParams(stemWidth, stemHeight) {
            leftMargin = stemLeft
            topMargin = keyHeight - dp(2)
        })
    }

    private fun markFocus(index: Int) {
        keyUis.getOrNull(index)?.apply {
            root.background = focusBackground
            textView.setTextColor(theme.genericActiveForegroundColor)
        }
    }

    private fun markInactive(index: Int) {
        keyUis.getOrNull(index)?.apply {
            root.background = null
            textView.setTextColor(theme.popupTextColor)
        }
    }

    override fun onChangeFocus(x: Float, y: Float): Boolean {
        val dismissTop = -ctx.dp(20)
        val dismissBottom = keyHeight + stemHeight + triggerBounds.height()
        if (x < -keyWidth * 2 || x > stripWidth + keyWidth * 2 || y < dismissTop || y > dismissBottom) {
            onDismissSelf(this)
            return true
        }
        val newColumn = limitIndex(floor(x / keyWidth).toInt(), items.size)
        if (newColumn != focusedIndex) {
            markInactive(focusedIndex)
            markFocus(newColumn)
            focusedIndex = newColumn
        }
        return false
    }

    override fun onTrigger(): KeyAction? {
        val key = items.getOrNull(focusedIndex) ?: return null
        return KeyAction.FcitxKeyAction(key)
    }
}
