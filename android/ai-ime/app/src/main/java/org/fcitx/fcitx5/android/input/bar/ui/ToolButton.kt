/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar.ui

import android.content.Context
import android.content.res.ColorStateList
import android.widget.ImageView
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.utils.borderlessRippleDrawable
import org.fcitx.fcitx5.android.utils.circlePressHighlightDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.imageResource
import splitties.views.padding

class ToolButton(context: Context) : CustomGestureView(context) {
    private var skinTheme: Theme.Custom? = null
    private var defaultIconTint: Int? = null

    companion object {
        val disableAnimation by AppPrefs.getInstance().advanced.disableAnimation
    }

    val image = imageView {
        isClickable = false
        isFocusable = false
        padding = dp(10)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    constructor(context: Context, @DrawableRes icon: Int, theme: Theme) : this(context) {
        skinTheme = theme as? Theme.Custom
        defaultIconTint = theme.altKeyTextColor
        image.imageTintList = ColorStateList.valueOf(theme.altKeyTextColor)
        setIcon(icon)
        skinTheme?.renderStyle?.toolbarIconPaddingDp?.let { image.padding = dp(it.coerceIn(0, 16)) }
        setPressHighlightColor(theme.keyPressHighlightColor)
        add(image, lParams(wrapContent, wrapContent, gravityCenter))
        applyIconSize()

    }

    fun setIcon(@DrawableRes icon: Int) {
        val key = when (icon) {
            R.drawable.ic_baseline_expand_more_24 -> "expand_candidates"
            R.drawable.ic_baseline_expand_less_24 -> "collapse_candidates"
            R.drawable.ic_baseline_arrow_drop_down_24 -> "hide"
            R.drawable.ic_baseline_arrow_back_24 -> "back"
            R.drawable.ic_baseline_settings_24 -> "settings"
            R.drawable.ic_baseline_keyboard_voice_24 -> "voice"
            R.drawable.ic_view_private -> "private"
            R.drawable.ic_baseline_undo_24 -> "undo"
            R.drawable.ic_baseline_redo_24 -> "redo"
            R.drawable.ic_cursor_move -> "cursor_move"
            R.drawable.ic_clipboard -> "clipboard"
            R.drawable.ic_baseline_extension_24 -> "material_hub"
            R.drawable.ic_baseline_more_horiz_24 -> "more"
            else -> null
        }
        val replacement = key?.let { skinTheme?.toolbarIcons?.get(it)?.toDrawable(context) }
        if (replacement != null) image.setImageDrawable(replacement)
        else image.imageResource = if (skinTheme?.renderStyle?.skinLabStandard == true &&
            icon == R.drawable.ic_baseline_settings_24) R.drawable.ic_oem_toolbar_settings else icon
        applyIconSize()
        image.imageTintList = when {
            skinTheme?.renderStyle?.toolbarIconTint != null -> ColorStateList.valueOf(skinTheme!!.renderStyle!!.toolbarIconTint!!)
            replacement != null -> null
            else -> defaultIconTint?.let(ColorStateList::valueOf)
        }
    }

    /** Fix the artwork bounds; the surrounding button keeps its original hit target. */
    fun applyIconSize() {
        val size = skinTheme?.renderStyle?.toolbarArtworkSizeDp ?: return
        image.padding = 0
        image.layoutParams = lParams(dp(size.coerceIn(8, 40)), dp(size.coerceIn(8, 40)), gravityCenter)
        image.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    fun setPressHighlightColor(@ColorInt color: Int) {
        background = if (disableAnimation) {
            circlePressHighlightDrawable(color)
        } else {
            borderlessRippleDrawable(color, dp(20))
        }
    }
}
