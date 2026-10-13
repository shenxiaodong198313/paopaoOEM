/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.dimensions.dp

private class NumberSidebarSymbolKey(
    symbol: String
) : KeyDef(
    Appearance.Text(
        displayText = symbol,
        textSize = 20f,
        textStyle = Typeface.BOLD,
        percentWidth = 0.1f,
        variant = Appearance.Variant.Alternative,
        border = Appearance.Border.Off,
        margin = false
    ),
    setOf(
        Behavior.Press(KeyAction.FcitxKeyAction(symbol))
    ),
    arrayOf(
        Popup.Preview(symbol),
        Popup.Keyboard(symbol)
    )
)

private class NumberMainKeyboardReturnKey : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_number_keyboard_return_back,
        percentWidth = 0.1f,
        variant = Appearance.Variant.Accent
    ),
    setOf(
        Behavior.Press(KeyAction.MainKeyboardSwitchAction)
    )
)

private class NumberLineBreakKey : KeyDef(
    Appearance.Text(
        displayText = "换行",
        textSize = 18f,
        textStyle = Typeface.BOLD,
        percentWidth = 0.1f,
        variant = Appearance.Variant.Alternative,
        isFunctionLabel = true
    ),
    setOf(
        Behavior.Press(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_Return)))
    )
)

private class NumberSpaceKey : KeyDef(
    Appearance.Text(
        displayText = "空格",
        viewId = R.id.button_space,
        textSize = 18f,
        textStyle = Typeface.BOLD,
        percentWidth = 0.1f,
        variant = Appearance.Variant.Alternative,
        isFunctionLabel = true
    ),
    setOf(
        Behavior.Press(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_space)))
    )
)

private class NumberEmojiKey : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_baseline_tag_faces_24,
        percentWidth = 0.1f,
        variant = Appearance.Variant.Alternative
    ),
    setOf(
        Behavior.Press(KeyAction.PickerSwitchAction(PickerWindow.Key.Emoji))
    )
)

private class NumberSymbolSwitchKey : KeyDef(
    Appearance.Text(
        displayText = "符号",
        textSize = 18f,
        textStyle = Typeface.BOLD,
        percentWidth = 0.1f,
        isFunctionLabel = true
    ),
    setOf(
        Behavior.Press(KeyAction.PickerSwitchAction(PickerWindow.Key.Symbol))
    )
)

@SuppressLint("ViewConstructor")
class NumberKeyboard(
    context: Context,
    theme: Theme,
) : BaseKeyboard(context, theme, emptyList(), "number") {

    private val backspace: ImageKeyView
    private var verticalCompensation: Int = 0

    init {
        val sampleKey = createKeyView(NumPadKey("5", 0xffb5, 30f), "r2c3")
        val horizontalMargin = sampleKey.hMargin
        val verticalMargin = sampleKey.vMargin
        val keyCornerRadius = sampleKey.radius
        val custom = theme as? Theme.Custom
        verticalCompensation = if (custom?.renderStyle?.gridKeyVerticalCompensation == false) 0
            else (verticalMargin - horizontalMargin).coerceAtLeast(0)

        val columns = List(5) { anchorView() }
        val rows = List(4) { anchorView() }

        columns.forEachIndexed { index, anchor ->
            addView(anchor, columnAnchorLayoutParams(columns, index))
        }
        rows.forEachIndexed { index, anchor ->
            addView(anchor, rowAnchorLayoutParams(rows, index))
        }

        val sidebarStrip = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = custom?.takeIf { it.renderStyle?.unifiedSidebar == true }?.sidebarImage?.toDrawable(context)
                ?: buildSidebarBackground(keyCornerRadius)
            setPadding(0, 0, 0, 0)
            showDividers = if (custom?.renderStyle?.unifiedSidebar == true) LinearLayout.SHOW_DIVIDER_NONE else LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerDrawable = insetRadiusDrawable(0, 0, 0f, theme.dividerColor)
        }
        listOf("@", "%", "-", "+").forEachIndexed { index, symbol ->
            sidebarStrip.addView(
                createKeyView(NumberSidebarSymbolKey(symbol), "sidebar:${index + 1}"),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }
        // Draw the shared cap against the same row anchors/insets as regular key faces.
        // Keep the existing symbol-slot container bounds, preserving its touch coordinates.
        val visualH = if (custom?.renderStyle?.skinLabStandard == true) org.fcitx.fcitx5.android.data.theme.SkinLabStandard.gapX(context) / 2 else custom?.renderStyle?.keyVisualHorizontalInsetDp
        val visualV = if (custom?.renderStyle?.skinLabStandard == true) org.fcitx.fcitx5.android.data.theme.SkinLabStandard.gapY(context) / 2 else custom?.renderStyle?.keyVisualVerticalInsetDp
        if (custom?.renderStyle?.unifiedSidebar == true && visualH != null && visualV != null) {
            val sidebarBackground = sidebarStrip.background
            sidebarStrip.background = null
            val surface = View(context).apply {
                background = sidebarBackground
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addCell(surface, columns[0], rows[0], rows[2],
                kotlin.math.round(dp(visualH.coerceIn(0f, 24f))).toInt(),
                kotlin.math.round(dp(visualV.coerceIn(0f, 24f))).toInt())
        }
        addCell(
            sidebarStrip,
            columns[0],
            rows[0],
            rows[2],
            horizontalMargin,
            if (custom?.renderStyle?.gridKeyVerticalCompensation == false) verticalMargin else horizontalMargin
        )
        addCell(createKeyView(NumberMainKeyboardReturnKey(), "r4c1"), columns[0], rows[3])

        addCell(createKeyView(NumPadKey("1", 0xffb1, 30f), "r1c2"), columns[1], rows[0])
        addCell(createKeyView(NumPadKey("2", 0xffb2, 30f), "r1c3"), columns[2], rows[0])
        addCell(createKeyView(NumPadKey("3", 0xffb3, 30f), "r1c4"), columns[3], rows[0])

        addCell(createKeyView(NumPadKey("4", 0xffb4, 30f), "r2c2"), columns[1], rows[1])
        addCell(createKeyView(NumPadKey("5", 0xffb5, 30f), "r2c3"), columns[2], rows[1])
        addCell(createKeyView(NumPadKey("6", 0xffb6, 30f), "r2c4"), columns[3], rows[1])

        addCell(createKeyView(NumPadKey("7", 0xffb7, 30f), "r3c2"), columns[1], rows[2])
        addCell(createKeyView(NumPadKey("8", 0xffb8, 30f), "r3c3"), columns[2], rows[2])
        addCell(createKeyView(NumPadKey("9", 0xffb9, 30f), "r3c4"), columns[3], rows[2])

        addCell(createKeyView(NumberSymbolSwitchKey(), "r4c2"), columns[1], rows[3])
        addCell(createKeyView(NumPadKey("0", 0xffb0, 30f), "r4c3"), columns[2], rows[3])
        addCell(createKeyView(NumPadKey(".", 0xffae, 26f), "r4c4"), columns[3], rows[3])

        backspace = createKeyView(
            BackspaceKey(variant = KeyDef.Appearance.Variant.Alternative), "r1c5"
        ) as ImageKeyView
        addCell(backspace, columns[4], rows[0])
        addCell(createKeyView(NumberSpaceKey(), "r2c5"), columns[4], rows[1])
        addCell(createKeyView(NumberEmojiKey(), "r3c5"), columns[4], rows[2])
        addCell(createKeyView(NumberLineBreakKey(), "r4c5"), columns[4], rows[3])
    }

    companion object {
        const val Name = "Number"
    }

    private fun anchorView(): View = View(context).apply {
        id = View.generateViewId()
        visibility = View.INVISIBLE
    }

    private fun columnAnchorLayoutParams(
        columns: List<View>,
        index: Int
    ): ConstraintLayout.LayoutParams =
        ConstraintLayout.LayoutParams(0, 0).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            horizontalWeight = 1f
            if (index == 0) {
                leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
                horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_SPREAD
            } else {
                leftToRight = columns[index - 1].id
            }
            if (index == columns.lastIndex) {
                rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
            } else {
                rightToLeft = columns[index + 1].id
            }
        }

    private fun rowAnchorLayoutParams(
        rows: List<View>,
        index: Int
    ): ConstraintLayout.LayoutParams =
        ConstraintLayout.LayoutParams(0, 0).apply {
            leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
            verticalWeight = 1f
            if (index == 0) {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_SPREAD
            } else {
                topToBottom = rows[index - 1].id
            }
            if (index == rows.lastIndex) {
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            } else {
                bottomToTop = rows[index + 1].id
            }
        }

    private fun addCell(
        view: View,
        column: View,
        row: View,
        horizontalMargin: Int? = null,
        verticalMargin: Int? = null
    ) {
        addCell(view, column, row, row, horizontalMargin, verticalMargin)
    }

    private fun addCell(
        view: View,
        column: View,
        topRow: View,
        bottomRow: View,
        horizontalMargin: Int? = null,
        verticalMargin: Int? = null
    ) {
        addView(view, ConstraintLayout.LayoutParams(0, 0).apply {
            leftToLeft = column.id
            rightToRight = column.id
            topToTop = topRow.id
            bottomToBottom = bottomRow.id
            horizontalMargin?.let {
                leftMargin = it
                rightMargin = it
            }
            if (verticalMargin != null) {
                topMargin = verticalMargin
                bottomMargin = verticalMargin
            } else if (view is KeyView && verticalCompensation > 0) {
                topMargin = -verticalCompensation
                bottomMargin = -verticalCompensation
            }
        })
    }

    private fun buildSidebarBackground(radius: Float): Drawable {
        val prefs = ThemeManager.prefs
        val borderWidth = dp(1)
        val bordered = prefs.keyBorder.getValue()
        val borderStroke = prefs.keyBorderStroke.getValue()
        return if (bordered) {
            if (borderStroke) {
                borderedKeyBackgroundDrawable(
                    theme.altKeyBackgroundColor,
                    theme.keyShadowColor,
                    radius,
                    borderWidth,
                    0,
                    0
                )
            } else {
                shadowedKeyBackgroundDrawable(
                    theme.altKeyBackgroundColor,
                    theme.keyShadowColor,
                    radius,
                    borderWidth,
                    0,
                    0
                )
            }
        } else {
            radiusDrawable(radius, theme.altKeyBackgroundColor)
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onPopupAction(action: PopupAction) {
        // leave empty on purpose to disable popup in NumberKeyboard
    }

}
