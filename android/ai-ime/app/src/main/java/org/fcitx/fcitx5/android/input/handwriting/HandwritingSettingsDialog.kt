// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.handwriting

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.*
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.status.ToolPanelPalette

/** A compact fixed form: only the single color row scrolls, never the dialog body. */
class HandwritingSettingsDialog(private val service: FcitxInputMethodService, theme: Theme) :
    Dialog(service), FcitxInputMethodService.AttachedDialogDismissHandler {
    private val prefs = AppPrefs.getInstance().internal
    private val palette = ToolPanelPalette(theme.backgroundColor, theme.keyTextColor, theme.accentKeyBackgroundColor)
    private var preparing = false
    private var activationGeneration = 0
    private var selectedColor = prefs.handwritingStrokeColor.getValue()
    private val wide = context.resources.configuration.screenHeightDp < 480 &&
        context.resources.configuration.screenWidthDp > context.resources.configuration.screenHeightDp
    private fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    private fun card(color: Int = palette.card) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(14).toFloat()
    }
    private fun label(value: String, size: Float = 15f) = TextView(context).apply {
        text = value; textSize = size; setTextColor(palette.ink); includeFontPadding = false
    }
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun verticalParams(height: Int = -2, top: Int = 0) = LinearLayout.LayoutParams(-1, height).apply { topMargin = dp(top) }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = column().apply {
            setPadding(dp(18), dp(16), dp(18), dp(14)); background = card(palette.surface)
        }
        panel.addView(label("手写设置", 20f), verticalParams().apply { bottomMargin = dp(10) })
        val left = column()
        val right = column()
        val enabled = Switch(context).apply {
            contentDescription = "键盘手写"
            isChecked = prefs.keyboardHandwritingEnabled.getValue()
        }
        val busy = ProgressBar(context).apply {
            visibility = View.GONE; contentDescription = "正在准备手写"
        }
        val toggleSlot = FrameLayout(context).apply {
            addView(enabled, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            addView(busy, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
        }
        val enableRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("键盘手写", 16f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(toggleSlot, LinearLayout.LayoutParams(dp(56), dp(40)))
        }
        left.addView(enableRow, verticalParams(dp(40)))
        var updatingSwitch = false
        fun checked(value: Boolean) {
            updatingSwitch = true; enabled.isChecked = value; updatingSwitch = false
        }
        val actions = LinearLayout(context)
        enabled.setOnCheckedChangeListener { _, value ->
            if (!updatingSwitch && value) {
                checked(false)
                preparing = true
                actions.getChildAt(1).isEnabled = false
                val generation = ++activationGeneration
                enabled.visibility = View.INVISIBLE; busy.visibility = View.VISIBLE
                HandwritingModels.ensureReady { ready ->
                    if (isShowing && generation == activationGeneration) {
                        preparing = false
                        actions.getChildAt(1).isEnabled = true
                        checked(ready)
                        enabled.visibility = View.VISIBLE; busy.visibility = View.GONE
                        if (!ready) Toast.makeText(context, "内置中文手写初始化失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        left.addView(label("中文手写已内置。滑动写字，点按打字，开启后停用其他滑动操作", 12f).apply {
            maxLines = 2
        }, verticalParams())
        val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label("笔迹粗细"), LinearLayout.LayoutParams(0, -2, 1f))
        val preview = object : View(context) {
            var widthDp = prefs.handwritingStrokeWidth.getValue().coerceIn(1, 12)
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val path = android.graphics.Path()
            override fun onDraw(canvas: android.graphics.Canvas) {
                paint.color = selectedColor.takeUnless { it == 0 } ?: palette.ink
                val stroke = listOf(InkPoint(width * .12f, height * .25f, 0),
                    InkPoint(width * .40f, height * .44f, 55),
                    InkPoint(width * .67f, height * .63f, 90),
                    InkPoint(width * .88f, height * .65f, 106))
                ChineseInkBrush.outline(path, stroke, dp(widthDp).toFloat(), true)
                canvas.drawPath(path, paint)
            }
        }
        heading.addView(preview, LinearLayout.LayoutParams(dp(76), dp(24)))
        left.addView(heading, verticalParams(dp(24), 10))
        val thickness = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        thickness.addView(label("细", 13f))
        val seek = SeekBar(context).apply {
            max = 11; progress = preview.widthDp - 1; contentDescription = "笔迹粗细"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                    preview.widthDp = value + 1; preview.invalidate()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        thickness.addView(seek, LinearLayout.LayoutParams(0, dp(40), 1f))
        thickness.addView(label("粗", 13f)); left.addView(thickness)
        right.addView(label("轨迹颜色"), verticalParams(dp(24)))
        val colors = listOf(0, Color.WHITE, 0xff808080.toInt(), Color.BLACK, 0xffff1744.toInt(), 0xffff9800.toInt(),
            0xffe6ee00.toInt(), 0xff64dd17.toInt(), 0xff00e5ff.toInt(), 0xff304ffe.toInt(), 0xffab47bc.toInt())
        val colorNames = listOf("默认", "白色", "灰色", "黑色", "红色", "橙色", "黄色", "绿色", "青色", "蓝色", "紫色")
        val swatches = mutableListOf<Pair<Int, TextView>>()
        fun markColors() {
            swatches.forEach { (color, view) ->
                view.text = if (color == 0) (if (selectedColor == 0) "默认 ✓" else "默认") else if (selectedColor == color) "✓" else ""
                view.isSelected = selectedColor == color
                view.contentDescription = "${colorNames[colors.indexOf(color)]}轨迹${if (color == selectedColor) "，已选中" else ""}"
            }
            preview.invalidate()
        }
        val colorRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        colors.forEach { color ->
            val swatch = label("", 12f).apply {
                gravity = Gravity.CENTER
                val fill = color.takeUnless { it == 0 } ?: palette.card
                background = card(fill).apply { setStroke(dp(1), palette.ink and 0x00ffffff or 0x60000000) }
                setTextColor(ToolPanelPalette.readableInk(fill, palette.ink))
                setOnClickListener { selectedColor = color; markColors() }
            }
            swatches.add(color to swatch)
            colorRow.addView(swatch, LinearLayout.LayoutParams(dp(if (color == 0) 60 else 44), dp(44)).apply { marginEnd = dp(8) })
        }
        val colorScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false; isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(colorRow, ViewGroup.LayoutParams(-2, -1))
            contentDescription = "轨迹颜色，左右滑动选择"
        }
        right.addView(colorScroll, verticalParams(dp(48), 2))
        markColors()
        if (wide) {
            val columns = LinearLayout(context)
            columns.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(20) })
            columns.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
            panel.addView(columns)
        } else {
            panel.addView(left)
            panel.addView(right, verticalParams(top = 10))
        }
        listOf("关闭", "保存").forEachIndexed { index, title ->
            actions.addView(label(title, 16f).apply {
                gravity = Gravity.CENTER; background = card()
                setOnClickListener {
                    if (index == 1) {
                        if (preparing) return@setOnClickListener
                        prefs.keyboardHandwritingEnabled.setValue(enabled.isChecked)
                        prefs.handwritingStrokeWidth.setValue(seek.progress + 1)
                        prefs.handwritingStrokeColor.setValue(selectedColor)
                    }
                    dismiss()
                }
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                if (index == 0) marginEnd = dp(6) else marginStart = dp(6)
            })
        }
        panel.addView(actions, verticalParams(dp(44), 14))
        setContentView(panel)
        setOnShowListener {
            colorRow.post {
                swatches.firstOrNull { it.first == selectedColor }?.second?.let { selected ->
                    colorScroll.scrollTo((selected.left - dp(8)).coerceAtLeast(0), 0)
                }
            }
        }
    }

    override fun show() {
        // Avoid a first frame at the default position followed by a WindowManager move/resize.
        window?.apply {
            setLayout(dp((context.resources.configuration.screenWidthDp - 32).coerceAtMost(if (wide) 640 else 400)), ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            setWindowAnimations(0)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
            addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        super.show()
    }

    override fun onAttachedDialogDismissed() { activationGeneration++ }
}
