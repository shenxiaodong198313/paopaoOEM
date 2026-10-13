/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.Keep
import androidx.constraintlayout.widget.ConstraintLayout
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.t9.T9SidebarState
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import splitties.dimensions.dp
import splitties.views.imageResource

private class T9DigitKey(
    digit: Char,
    letters: String,
    percentWidth: Float = 0.1f
) : KeyDef(
    Appearance.AltText(
        displayText = letters,
        altText = digit.toString(),
        textSize = 20f,
        textStyle = android.graphics.Typeface.BOLD,
        percentWidth = percentWidth
    ),
    setOf(
        Behavior.Press(KeyAction.FcitxKeyAction(digit.toString())),
        Behavior.LongPress(KeyAction.FcitxKeyAction(digit.toString()))
    ),
    arrayOf(
        Popup.AltPreview(letters, digit.toString()),
        Popup.Keyboard(digit.toString())
    )
)

private class T9PunctuationKey(
    displayText: String,
    altText: String,
    percentWidth: Float = 0.1f,
    variant: KeyDef.Appearance.Variant = KeyDef.Appearance.Variant.Alternative
) : KeyDef(
    Appearance.AltText(
        displayText = displayText,
        altText = altText,
        textSize = 18f,
        textStyle = android.graphics.Typeface.BOLD,
        percentWidth = percentWidth,
        variant = variant
    ),
    setOf(
        Behavior.Press(KeyAction.FcitxKeyAction(displayText)),
        Behavior.LongPress(KeyAction.FcitxKeyAction(altText))
    ),
    arrayOf(
        Popup.AltPreview(displayText, altText),
        Popup.Keyboard(displayText)
    )
)

private class T9SegmentationKey(
    percentWidth: Float = 0.1f
) : KeyDef(
    Appearance.AltText(
        displayText = "分词",
        altText = "1",
        textSize = 18f,
        textStyle = android.graphics.Typeface.BOLD,
        percentWidth = percentWidth,
        variant = Appearance.Variant.Alternative,
        isFunctionLabel = true
    ),
    setOf(
        Behavior.Press(KeyAction.FcitxKeyAction("'")),
        Behavior.LongPress(KeyAction.FcitxKeyAction("1"))
    ),
    arrayOf(
        Popup.AltPreview("分词", "1"),
        Popup.Keyboard("1")
    )
)

private class T9SpaceKey(
    percentWidth: Float = 0.1f
) : KeyDef(
    Appearance.AltText(
        displayText = "拼音",
        altText = "0",
        viewId = R.id.button_space,
        textSize = 18f,
        textStyle = android.graphics.Typeface.BOLD,
        percentWidth = percentWidth,
        isFunctionLabel = true
    ),
    setOf(
        Behavior.Press(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_space))),
        Behavior.LongPress(KeyAction.FcitxKeyAction("0"))
    ),
    arrayOf(
        Popup.AltPreview("拼音", "0"),
        Popup.Keyboard("0")
    )
)

private class T9EmojiKey(
    percentWidth: Float = 0.1f
) : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_baseline_tag_faces_24,
        percentWidth = percentWidth,
        variant = Appearance.Variant.Alternative
    ),
    setOf(
        Behavior.Press(KeyAction.PickerSwitchAction(PickerWindow.Key.Emoji))
    )
)

private class T9StripPunctuationKey(
    symbol: String
) : KeyDef(
    Appearance.Text(
        displayText = symbol,
        textSize = 20f,
        textStyle = android.graphics.Typeface.BOLD,
        percentWidth = 0.1f,
        variant = Appearance.Variant.Alternative,
        border = Appearance.Border.Off,
        margin = false
    ),
    setOf(
        Behavior.Press(KeyAction.FcitxKeyAction(symbol))
    ),
    arrayOf(
        Popup.Preview(symbol)
    )
)

private class T9SidebarSlotKey(
    text: String = ""
) : KeyDef(
    Appearance.Text(
        displayText = text,
        textSize = 20f,
        textStyle = android.graphics.Typeface.BOLD,
        percentWidth = 0.1f,
        variant = Appearance.Variant.Alternative,
        border = Appearance.Border.Off,
        margin = false
    ),
    emptySet(),
    emptyArray()
)

@SuppressLint("ViewConstructor")
class T9Keyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, emptyList(), "t9") {

    private val mode: ImageKeyView
    private val `return`: ImageKeyView
    private val sidebarSlots = mutableListOf<TextKeyView>()
    private var verticalCompensation: Int = 0
    private var sidebarState: T9SidebarState = T9SidebarState.Empty

    var onSidebarCandidatePressed: ((Int) -> Unit)? = null
    var onSidebarSwipeUp: (() -> Unit)? = null

    init {
        val sampleKey = createKeyView(T9DigitKey('5', "JKL"), "r2c3")
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

        val punctuationStrip = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = custom?.takeIf { it.renderStyle?.unifiedSidebar == true }?.sidebarImage?.toDrawable(context) ?: GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = keyCornerRadius
                setColor(theme.altKeyBackgroundColor)
            }
            setPadding(0, 0, 0, 0)
        }
        listOf("，", "。", "？", "！").forEachIndexed { index, symbol ->
            val slot = createKeyView(T9SidebarSlotKey(symbol), "sidebar:${index + 1}") as TextKeyView
            slot.setOnClickListener {
                handleSidebarClick(index)
            }
            slot.swipeEnabled = true
            slot.swipeThresholdX = dp(800).toFloat()
            slot.swipeThresholdY = dp(36).toFloat()
            slot.onGestureListener = CustomGestureView.OnGestureListener { _, event ->
                if (event.type == CustomGestureView.GestureType.Up && event.totalY < -dp(36)) {
                    onSidebarSwipeUp?.invoke()
                    true
                } else {
                    false
                }
            }
            sidebarSlots += slot
            punctuationStrip.addView(
                slot,
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
            val sidebarBackground = punctuationStrip.background
            punctuationStrip.background = null
            val surface = (custom.renderStyle?.ambientSidebarEffect?.let { SkinAmbientView(context, it) }
                ?: View(context)).apply {
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
            punctuationStrip,
            columns[0],
            rows[0],
            rows[2],
            horizontalMargin,
            if (custom?.renderStyle?.gridKeyVerticalCompensation == false) verticalMargin else horizontalMargin
        )

        addCell(
            createKeyView(
                LayoutSwitchKey(
                    "符",
                    PickerWindow.Key.Symbol.name,
                    variant = KeyDef.Appearance.Variant.Alternative,
                    viewId = R.id.button_punctuation
                ), "r4c1"
            ),
            columns[0],
            rows[3]
        )

        addCell(createKeyView(T9SegmentationKey(), "r1c2"), columns[1], rows[0])
        addCell(createKeyView(T9DigitKey('2', "ABC"), "r1c3"), columns[2], rows[0])
        addCell(createKeyView(T9DigitKey('3', "DEF"), "r1c4"), columns[3], rows[0])

        addCell(createKeyView(T9DigitKey('4', "GHI"), "r2c2"), columns[1], rows[1])
        addCell(sampleKey, columns[2], rows[1])
        addCell(createKeyView(T9DigitKey('6', "MNO"), "r2c4"), columns[3], rows[1])

        addCell(createKeyView(T9DigitKey('7', "PQRS"), "r3c2"), columns[1], rows[2])
        addCell(createKeyView(T9DigitKey('8', "TUV"), "r3c3"), columns[2], rows[2])
        addCell(createKeyView(T9DigitKey('9', "WXYZ"), "r3c4"), columns[3], rows[2])

        mode = createKeyView(
            ImageLanguageKey(
                R.drawable.ic_t9_lang_zh,
                percentWidth = 0.1f,
                viewId = R.id.button_lang
            ), "r4c2"
        ) as ImageKeyView
        addCell(mode, columns[1], rows[3])
        addCell(createKeyView(T9SpaceKey(), "r4c3"), columns[2], rows[3])
        addCell(
            createKeyView(
                LayoutSwitchKey(
                    "123",
                    NumberKeyboard.Name,
                    variant = KeyDef.Appearance.Variant.Alternative,
                    viewId = R.id.button_number_mode
                ), "r4c4"
            ),
            columns[3],
            rows[3]
        )

        addCell(
            createKeyView(BackspaceKey(variant = KeyDef.Appearance.Variant.Alternative), "r1c5"),
            columns[4],
            rows[0]
        )
        addCell(createKeyView(T9ResetKey(percentWidth = 0.1f), "r2c5"), columns[4], rows[1])
        addCell(createKeyView(T9EmojiKey(), "r3c5"), columns[4], rows[2])
        `return` = createKeyView(ReturnKey(percentWidth = 0.1f), "r4c5") as ImageKeyView
        addCell(`return`, columns[4], rows[3])
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

    companion object {
        const val Name = "T9"
    }

    @Keep
    fun updateT9ModeLabel(modeLabel: String) {
        mode.setSkinState(if (modeLabel == "En") "en" else "zh")
        mode.img.imageResource = when (modeLabel) {
            "En" -> R.drawable.ic_t9_lang_en
            else -> R.drawable.ic_t9_lang_zh
        }
    }

    @Keep
    fun updateSidebarState(state: T9SidebarState) {
        sidebarState = state
        val punctuationFallback = listOf("，", "。", "？", "！")
        sidebarSlots.forEachIndexed { index, view ->
            val text = if (state.showingCandidates) {
                state.items.getOrNull(index).orEmpty()
            } else {
                punctuationFallback[index]
            }
            view.mainText.text = text
            view.isEnabled = text.isNotEmpty()
            view.alpha = if (text.isNotEmpty()) 1f else 0.35f
        }
    }

    private fun handleSidebarClick(index: Int) {
        if (sidebarState.showingCandidates) {
            onSidebarCandidatePressed?.invoke(index)
            return
        }
        val symbol = listOf("，", "。", "？", "！").getOrNull(index) ?: return
        keyActionListener?.onKeyAction(
            KeyAction.FcitxKeyAction(symbol),
            KeyActionListener.Source.Keyboard
        )
    }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.updateReturnDrawable(returnDrawable)
    }
}
