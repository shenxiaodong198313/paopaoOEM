package com.paopaoime.aikeyboard.ime.ui.toolbar

import android.content.Context
import org.fcitx.fcitx5.android.data.theme.SkinLabStandard
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.graphics.drawable.RippleDrawable
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge.ToolbarItem
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.R as FcitxR
import splitties.dimensions.dp

object OemToolbarIconHelper {
    const val TOOLBAR_BUTTON_SIZE_DP = 32
    const val TOOLBAR_ICON_PADDING_DP = 5
    const val TOOLBAR_BUTTON_MARGIN_DP = 15
    const val TOOLBAR_EDGE_MARGIN_DP = 15

    const val PREVIEW_BUTTON_SIZE_DP = 32
    const val PREVIEW_ICON_PADDING_DP = 5

    const val AVAILABLE_BUTTON_SIZE_DP = 60
    const val AVAILABLE_ICON_PADDING_DP = 18
    private const val LEADING_ENTRY_EXTRA_PADDING_DP = 2
    private val customTheme get() = ThemeManager.activeTheme as? Theme.Custom

    private fun skinIconKey(@DrawableRes iconRes: Int?): String? = when (iconRes) {
        leadingEntryIconRes() -> "panel"
        FcitxR.drawable.ic_baseline_arrow_drop_down_24 -> "hide"
        else -> ToolbarItem.entries.firstOrNull { toolbarIconRes(it) == iconRes }?.prefValue
    }

    private fun applySkinIcon(icon: ImageView, @DrawableRes iconRes: Int?) {
        val theme = customTheme
        val tint = theme?.renderStyle?.toolbarIconTint
        val replacement = skinIconKey(iconRes)?.let { theme?.toolbarIcons?.get(it)?.toDrawable(icon.context) }
        if (replacement != null) icon.setImageDrawable(replacement)
        theme?.renderStyle?.toolbarArtworkSizeDp?.let { size ->
            icon.setPadding(0, 0, 0, 0)
            icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            icon.drawable?.let { drawable ->
                // A fixed-size centered layer also works in the 32dp and 60dp configuration grids.
                icon.setImageDrawable(LayerDrawable(arrayOf(drawable)).apply {
                    setLayerSize(0, icon.context.dp(size.coerceIn(8, 40)), icon.context.dp(size.coerceIn(8, 40)))
                    setLayerGravity(0, Gravity.CENTER)
                })
            }
        }
        icon.imageTintList = when {
            tint != null -> ColorStateList.valueOf(tint)
            replacement != null -> null
            else -> ColorStateList.valueOf(toolbarIconTintColor())
        }
    }

    @DrawableRes
    fun leadingEntryIconRes(): Int = R.drawable.ic_oem_toolbar_panel_entry

    @DrawableRes
    fun toolbarIconRes(item: ToolbarItem): Int = when (item) {
        ToolbarItem.Voice -> R.drawable.ic_oem_toolbar_voice
        ToolbarItem.KeyboardSelection -> R.drawable.ic_oem_toolbar_keyboard_selection
        ToolbarItem.AiReply -> R.drawable.ic_oem_toolbar_ai_reply
        ToolbarItem.MaterialHub -> R.drawable.ic_oem_toolbar_material_hub
        ToolbarItem.Emoji -> R.drawable.ic_oem_toolbar_emoji
        ToolbarItem.Clipboard -> R.drawable.ic_oem_toolbar_clipboard
        ToolbarItem.CustomizeToolbar -> R.drawable.ic_oem_toolbar_customize_toolbar
        ToolbarItem.Skin -> R.drawable.ic_oem_toolbar_skin
        ToolbarItem.TraditionalInput -> R.drawable.ic_oem_toolbar_traditional_input
        ToolbarItem.KeyboardAdjust -> R.drawable.ic_oem_toolbar_keyboard_adjust
    }

    fun applyToolbarButtonStyle(
        button: ToolButton,
        context: Context,
        @DrawableRes iconRes: Int? = null,
    ) {
        button.background = if (customTheme?.renderStyle?.skinLabStandard == true) {
            if (SkinLabStandard.circleToolbar(context)) GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (ThemeManager.activeTheme.isDark) ThemeManager.activeTheme.keyBackgroundColor else 0xFFF8F8F8.toInt()) } else null
        } else customTheme?.takeIf { it.renderStyle?.equalToolbarSlots == true }?.toolbarIcons?.get("button_background")?.toDrawable()
            ?: buildCircleButtonBackground(context, emphasized = false)
        val iconPadding = context.dp(customTheme?.renderStyle?.toolbarIconPaddingDp?.coerceIn(0, 16) ?: toolbarIconPaddingDp(iconRes))
        button.image.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        applySkinIcon(button.image, iconRes)
        button.applyIconSize()
    }

    fun applyPreviewStyle(
        frame: ViewGroup,
        icon: ImageView,
        context: Context,
        @DrawableRes iconRes: Int? = null,
    ) {
        frame.background = buildCircleButtonBackground(context, emphasized = false)
        val iconPadding = context.dp(customTheme?.renderStyle?.toolbarIconPaddingDp?.coerceIn(0, 16) ?: previewIconPaddingDp(iconRes))
        icon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        applySkinIcon(icon, iconRes)
    }

    fun applyAvailableOptionIconStyle(
        icon: ImageView,
        context: Context,
        @DrawableRes iconRes: Int? = null,
    ) {
        val iconPadding = context.dp(availableIconPaddingDp(iconRes))
        icon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        applySkinIcon(icon, iconRes)
    }

    private fun toolbarIconPaddingDp(@DrawableRes iconRes: Int?): Int {
        return if (iconRes == leadingEntryIconRes()) {
            TOOLBAR_ICON_PADDING_DP + LEADING_ENTRY_EXTRA_PADDING_DP
        } else {
            TOOLBAR_ICON_PADDING_DP
        }
    }

    private fun previewIconPaddingDp(@DrawableRes iconRes: Int?): Int {
        return if (iconRes == leadingEntryIconRes()) {
            PREVIEW_ICON_PADDING_DP + LEADING_ENTRY_EXTRA_PADDING_DP
        } else {
            PREVIEW_ICON_PADDING_DP
        }
    }

    private fun availableIconPaddingDp(@DrawableRes iconRes: Int?): Int {
        return if (iconRes == leadingEntryIconRes()) {
            AVAILABLE_ICON_PADDING_DP + LEADING_ENTRY_EXTRA_PADDING_DP
        } else {
            AVAILABLE_ICON_PADDING_DP
        }
    }

    fun toolbarIconTintColor(): Int {
        customTheme?.renderStyle?.toolbarIconTint?.let { return it }
        val theme = ThemeManager.activeTheme
        val surfaceColor = ColorUtils.setAlphaComponent(theme.barColor, 0xFF)
        return if (ColorUtils.calculateLuminance(surfaceColor) < 0.5) {
            Color.WHITE
        } else {
            theme.keyTextColor
        }
    }

    private fun buildCircleButtonBackground(context: Context, emphasized: Boolean): Drawable {
        val theme = ThemeManager.activeTheme
        val baseColor = ColorUtils.setAlphaComponent(theme.barColor, 0xFF)
        val isDarkSurface = ColorUtils.calculateLuminance(baseColor) < 0.5
        val overlayColor = when {
            isDarkSurface && emphasized -> ColorUtils.setAlphaComponent(Color.WHITE, 0x30)
            isDarkSurface -> ColorUtils.setAlphaComponent(Color.WHITE, 0x24)
            emphasized -> ColorUtils.setAlphaComponent(Color.WHITE, 0xF0)
            else -> ColorUtils.setAlphaComponent(Color.WHITE, 0xDE)
        }
        val fillColor = ColorUtils.compositeColors(overlayColor, baseColor)
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
        }
        val mask = InsetDrawable(ColorDrawable(Color.WHITE), 0)
        return RippleDrawable(
            ColorStateList.valueOf(theme.keyPressHighlightColor),
            if (customTheme?.renderStyle?.toolbarButtonBackground == false) null else fill,
            mask,
        ).apply {
            radius = context.dp(TOOLBAR_BUTTON_SIZE_DP / 2)
        }
    }
}
