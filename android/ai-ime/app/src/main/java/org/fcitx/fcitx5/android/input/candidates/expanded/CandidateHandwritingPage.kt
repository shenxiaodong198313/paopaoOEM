// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.candidates.expanded

import android.graphics.Canvas
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.handwriting.*
import org.fcitx.fcitx5.android.input.status.ExpandedCandidatePalette
import splitties.dimensions.dp

/** Ink here searches the active composition. Starting a stroke must never finalize the pinyin. */
class CandidateHandwritingPage(
    private val service: FcitxInputMethodService,
    private val palette: ExpandedCandidatePalette,
    private val select: (String) -> Unit
) : LinearLayout(service), InkStrokeSession {
    override val buffer = HandwritingBuffer()
    private val words = LinearLayout(context)
    private var worker: ChineseInkRecognizer? = null
    private var busy = false
    private var queued = false
    private var drawing = false
    private val hint = label("在这里写一个字", 16f)
    private val pad = object : View(context) {
        val ink = HandwritingInk(this, this@CandidateHandwritingPage, palette.ink)
        init { contentDescription = "手写找字书写区" }
        override fun onDraw(canvas: Canvas) = ink.draw(canvas)
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                parent.requestDisallowInterceptTouchEvent(true)
                ink.begin(event)
            } else ink.event(event)
            return true
        }
    }
    private fun label(value: String, size: Float) = TextView(context).apply {
        text = value; textSize = size; gravity = Gravity.CENTER; setTextColor(palette.ink)
        includeFontPadding = false
    }
    init {
        orientation = VERTICAL
        val strip = LinearLayout(context)
        strip.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false; addView(words)
        }, LayoutParams(0, -1, 1f))
        strip.addView(label("重写", 15f).apply { setOnClickListener { clear() } }, LayoutParams(dp(56), -1))
        addView(strip, LayoutParams(-1, dp(40)))
        addView(FrameLayout(context).apply {
            addView(pad, FrameLayout.LayoutParams(-1, -1))
            addView(hint, FrameLayout.LayoutParams(-1, -1))
        }, LayoutParams(-1, 0, 1f))
    }

    fun open() { updateHint() }

    fun close() {
        clear(); worker?.close(); worker = null; busy = false
    }

    private fun updateHint() {
        hint.visibility = if (buffer.strokes.isEmpty() && !drawing) VISIBLE else GONE
    }

    fun clear() {
        pad.ink.cancel(); removeCallbacks(recognizeLater)
        buffer.clear(); words.removeAllViews(); drawing = false; queued = false
        pad.invalidate(); updateHint()
    }
    override fun beginStroke(): Boolean {
        if (!isShown || !service.canUseHandwriting() || buffer.strokes.size >= 192 ||
            buffer.strokes.sumOf { it.size } >= 65_536) return false
        removeCallbacks(recognizeLater); buffer.begin(); drawing = true
        words.removeAllViews(); updateHint()
        return true
    }
    override fun finishStroke(points: List<InkPoint>) {
        drawing = false; buffer.add(points); pad.invalidate()
        postDelayed(recognizeLater, 260)
    }
    override fun cancelStroke() {
        drawing = false
        if (buffer.strokes.isNotEmpty()) postDelayed(recognizeLater, 260) else updateHint()
    }
    private val recognizeLater = Runnable { recognize() }
    private fun recognize() {
        if (!isShown || drawing || buffer.strokes.isEmpty()) return
        if (busy) { queued = true; return }
        busy = true; queued = false
        val epoch = service.extendedInputEpoch
        val token = buffer.revision
        val engine = worker ?: ChineseInkRecognizer().also { worker = it }
        engine.recognize(buffer.strokes.toList()) { result ->
            busy = false
            if (epoch == service.extendedInputEpoch && isShown && !drawing &&
                buffer.accept(token, result.orEmpty().filter(::isSingleCharacter))) {
                words.removeAllViews()
                if (buffer.candidates.isEmpty()) {
                    words.addView(label("继续书写，或点重写", 14f), LayoutParams(-2, -1))
                }
                buffer.candidates.forEach { word ->
                    words.addView(label(word, 20f).apply {
                        setOnClickListener { if (epoch == service.extendedInputEpoch && token == buffer.revision) select(word) }
                    }, LayoutParams(dp(48), -1))
                }
            }
            if (queued) recognize()
        }
    }
}
