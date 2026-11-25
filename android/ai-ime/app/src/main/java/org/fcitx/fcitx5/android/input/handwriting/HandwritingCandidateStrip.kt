// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.handwriting

import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.bar.ui.CandidateUi
import org.fcitx.fcitx5.android.input.candidates.CandidateItemUi
import org.fcitx.fcitx5.android.input.status.ToolPanelPalette

/** Uses the same candidate typography/material and feedback as the native candidate strip. */
class HandwritingCandidateStrip(private val service: FcitxInputMethodService, private val theme: Theme) {
    private val row = LinearLayout(service)
    private val scroll = HorizontalScrollView(service).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    val ui = CandidateUi(service, theme, scroll)
    private var version = 0L
    private var expanded: Dialog? = null
    private fun dp(value: Int) = (service.resources.displayMetrics.density * value).toInt()
    fun clear() { version++; expanded?.dismiss(); expanded = null; row.removeAllViews() }
    fun show(words: List<String>, select: (String) -> Unit) {
        clear(); val token = version
        words.forEach { word ->
            val item = CandidateItemUi(service, theme)
            item.updateCandidate(CandidateWord("", word, ""))
            item.root.setOnClickListener {
                if (version == token) { val feedback = item.captureSelectionFeedback(); select(word); feedback() }
            }
            row.addView(item.root, LinearLayout.LayoutParams(-2, -1))
        }
        scroll.scrollTo(0, 0)
        ui.expandButton.visibility = View.VISIBLE
        ui.expandButton.setOnClickListener {
            val palette = ToolPanelPalette(theme.backgroundColor, theme.keyTextColor, theme.accentKeyBackgroundColor)
            val panel = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply { setColor(palette.surface); cornerRadius = dp(16).toFloat() }
            }
            val grid = GridLayout(service).apply { columnCount = 3 }
            val dialog = Dialog(service)
            words.forEach { word ->
                val item = CandidateItemUi(service, theme, expanded = true)
                item.updateCandidate(CandidateWord("", word, ""))
                item.root.setOnClickListener { if (version == token) { select(word); dialog.dismiss() } }
                grid.addView(item.root, GridLayout.LayoutParams().apply { width = 0; height = dp(50); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) })
            }
            panel.addView(ScrollView(service).apply { addView(grid) }, LinearLayout.LayoutParams(-1, 0, 1f))
            panel.addView(TextView(service).apply {
                text = "收起候选"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(palette.ink)
                setOnClickListener { dialog.dismiss() }
            }, LinearLayout.LayoutParams(-1, dp(44)))
            dialog.setContentView(panel)
            service.showDialog(dialog)
            dialog.window?.setGravity(Gravity.BOTTOM)
            dialog.window?.setLayout((service.resources.displayMetrics.widthPixels * .96f).toInt(), service.currentKeyboardWindowHeightPx())
            expanded = dialog
        }
    }
}
