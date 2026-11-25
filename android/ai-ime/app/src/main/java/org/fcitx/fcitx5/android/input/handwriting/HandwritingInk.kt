// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.handwriting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import org.fcitx.fcitx5.android.data.prefs.AppPrefs

interface InkStrokeSession {
    val buffer: HandwritingBuffer
    fun beginStroke(): Boolean
    fun finishStroke(points: List<InkPoint>)
    fun cancelStroke()
}

/** Shared stroke capture for the pad and the transparent keyboard overlay. */
class HandwritingInk(private val view: View, private val session: InkStrokeSession, private val defaultColor: Int) {
    private val points = mutableListOf<InkPoint>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val finished = mutableListOf<Pair<List<InkPoint>, Path>>()
    private val livePath = Path()
    private var lastWidth = 0f
    private var pointer = -1
    private var stylus = false
    fun begin(event: MotionEvent, prefix: List<InkPoint> = emptyList()): Boolean {
        pointer = event.getPointerId(0); stylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
        points.clear()
        if (!session.beginStroke()) { pointer = -1; return false }
        add(event)
        points.addAll(prefix)
        return true
    }
    private fun add(event: MotionEvent) {
        val index = event.findPointerIndex(pointer)
        if (index < 0) return
        for (i in 0 until event.historySize) points.add(InkPoint(event.getHistoricalX(index, i), event.getHistoricalY(index, i), event.getHistoricalEventTime(i)))
        points.add(InkPoint(event.getX(index), event.getY(index), event.eventTime))
        if (points.size > 2048) { val reduced = points.filterIndexed { index, _ -> index % 2 == 0 }; points.clear(); points.addAll(reduced) }
        view.invalidate()
    }
    fun event(event: MotionEvent) {
        if (pointer == -1) return
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL -> { cancel(); return }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                if (!stylus && event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS) {
                    points.clear(); pointer = event.getPointerId(index); stylus = true
                }
                return
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) != pointer) return
        }
        add(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            session.finishStroke(points.toList()); points.clear(); pointer = -1; view.invalidate()
        }
    }
    fun cancel() { points.clear(); pointer = -1; session.cancelStroke(); view.invalidate() }
    fun draw(canvas: Canvas) {
        if (points.isEmpty() && (session.buffer.erased || session.buffer.strokes.isEmpty())) { finished.clear(); return }
        val prefs = AppPrefs.getInstance().internal
        paint.color = prefs.handwritingStrokeColor.getValue().takeUnless { it == 0 } ?: defaultColor
        val width = view.resources.displayMetrics.density * prefs.handwritingStrokeWidth.getValue().coerceIn(1, 12)
        if (width != lastWidth) { finished.clear(); lastWidth = width }
        val strokes = session.buffer.strokes
        while (finished.size > strokes.size) finished.removeAt(finished.lastIndex)
        if (!session.buffer.erased) {
            strokes.forEachIndexed { index, stroke ->
                if (index == finished.size || finished[index].first !== stroke) {
                    val entry = stroke to Path().also { ChineseInkBrush.outline(it, stroke, width, true) }
                    if (index == finished.size) finished.add(entry) else finished[index] = entry
                }
                canvas.drawPath(finished[index].second, paint)
            }
        }
        if (points.isNotEmpty()) {
            ChineseInkBrush.outline(livePath, points, width, false)
            canvas.drawPath(livePath, paint)
        }
    }
}

class HandwritingPad(context: Context, private val session: HandwritingSession, color: Int, private val settings: () -> Unit) : View(context) {
    private val ink = HandwritingInk(this, session, color)
    init { contentDescription = "中文手写区域"; isClickable = true }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); ink.draw(canvas) }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!session.modelsReady) { if (event.actionMasked == MotionEvent.ACTION_UP) settings(); return true }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent.requestDisallowInterceptTouchEvent(true)
            if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER || event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) {
                session.backspace(); return true
            }
            ink.begin(event)
        } else ink.event(event)
        return true
    }
}

/** Taps remain keys until ink is claimed; a pending ink group owns subsequent strokes/dots. */
class KeyboardHandwritingFrame(context: Context) : FrameLayout(context) {
    var session: HandwritingSession? = null
    var allowed: () -> Boolean = { false }
    var cancelKeys: () -> Unit = {}
    private var ink: HandwritingInk? = null
    private var down: MotionEvent? = null
    private var capturing = false
    private var ignoreRemainingPointers = false
    private var typingPointers = false
    private var lastTypingUp: Long? = null
    private var afterTyping = false
    private val pendingMotion = mutableListOf<InkPoint>()
    private var lastMotion: InkPoint? = null
    private var movingSamples = 0
    private var maxDistance = 0f
    private var lastMovementTime = 0L
    // Keyboard taps often roll across the fingertip. Scroll slop alone (usually 8dp)
    // is too small to distinguish that motion from an intentional ink stroke.
    private val slop = maxOf(ViewConfiguration.get(context).scaledTouchSlop * 2f,
        18f * resources.displayMetrics.density)
    private val reservedKeys = intArrayOf(
        org.fcitx.fcitx5.android.R.id.button_backspace,
        org.fcitx.fcitx5.android.R.id.button_space,
        org.fcitx.fcitx5.android.R.id.button_mini_space,
        org.fcitx.fcitx5.android.R.id.button_return,
        org.fcitx.fcitx5.android.R.id.button_caps,
        org.fcitx.fcitx5.android.R.id.button_lang,
        org.fcitx.fcitx5.android.R.id.button_number_mode,
        org.fcitx.fcitx5.android.R.id.button_quickphrase
    )
    private fun startsOnReservedKey(event: MotionEvent): Boolean {
        val bounds = android.graphics.Rect()
        return reservedKeys.any { id ->
            val key = findViewById<View>(id)
            key?.isShown == true && run {
                key.getDrawingRect(bounds)
                offsetDescendantRectToMyCoords(key, bounds)
                bounds.contains(event.x.toInt(), event.y.toInt())
            }
        }
    }
    fun bind(session: HandwritingSession, color: Int) { this.session = session; ink = HandwritingInk(this, session, color); session.invalidate = { invalidate() } }
    private fun releaseDown() {
        down?.recycle(); down = null
        pendingMotion.clear(); lastMotion = null; movingSamples = 0; maxDistance = 0f
    }
    fun cancelInk() {
        releaseDown(); capturing = false; typingPointers = false; lastTypingUp = null; ink?.cancel()
    }
    private fun confirmStroke(event: MotionEvent, start: MotionEvent): Boolean {
        val index = event.findPointerIndex(start.getPointerId(0))
        if (index < 0) return false
        val samples = ArrayList<InkPoint>(event.historySize + 1)
        for (i in 0 until event.historySize) samples.add(InkPoint(
            event.getHistoricalX(index, i), event.getHistoricalY(index, i), event.getHistoricalEventTime(i)))
        samples.add(InkPoint(event.getX(index), event.getY(index), event.eventTime))
        for (point in samples) {
            maxDistance = maxOf(maxDistance, kotlin.math.hypot(point.x - start.x, point.y - start.y))
            val previous = lastMotion ?: InkPoint(start.x, start.y, start.eventTime)
            if (kotlin.math.hypot(point.x - previous.x, point.y - previous.y) >= resources.displayMetrics.density) {
                movingSamples++
                lastMovementTime = point.time
                lastMotion = point
            }
        }
        // A single early jump followed by a stationary hold is still a key. Use
        // actual movement timestamps (including batched history), not a timer
        // that could claim ink while the finger has stopped moving.
        val pen = start.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
        val distance = if (afterTyping && !pen) slop * 1.5f else slop
        val duration = if (afterTyping) 120L else 60L
        val confirmed = maxDistance > distance && (pen ||
            (movingSamples >= 2 && lastMovementTime - start.eventTime >= duration))
        if (confirmed && ink?.begin(start, pendingMotion) == true) {
            capturing = true
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel); cancel.recycle(); cancelKeys()
            pendingMotion.clear()
            parent.requestDisallowInterceptTouchEvent(true)
            return true
        }
        pendingMotion.addAll(samples)
        if (pendingMotion.size > 256) {
            val reduced = pendingMotion.filterIndexed { i, _ -> i % 2 == 0 }
            pendingMotion.clear(); pendingMotion.addAll(reduced)
        }
        return false
    }
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // dispatchTouchEvent runs before Android's normal interception check. Respect
        // the child's explicit ownership here too (notably delete hold/clear/undo).
        if (disallowIntercept && !capturing) releaseDown()
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }
    override fun onVisibilityAggregated(visible: Boolean) {
        super.onVisibilityAggregated(visible)
        if (visible && allowed()) session?.start() else { cancelInk(); session?.stop() }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            ignoreRemainingPointers = false
            typingPointers = false
            releaseDown(); capturing = false
            if (allowed() && session?.modelsReady == true && !startsOnReservedKey(event)) {
                if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER || event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) {
                    session?.backspace(); ignoreRemainingPointers = true; return true
                }
                down = MotionEvent.obtain(event)
                afterTyping = lastTypingUp?.let { event.eventTime - it in 0..220L } == true
                lastMovementTime = event.eventTime
                if (session?.hasPendingInk == true && session?.canResumeKeyboardWithTap != true) {
                    // Do not send DOWN to the keys: even a dot is now handwriting,
                    // until the current ink group is visually erased or confirmed.
                    capturing = ink?.begin(event) == true
                    if (capturing) parent.requestDisallowInterceptTouchEvent(true)
                    else { releaseDown(); ignoreRemainingPointers = true }
                    return true
                }
            }
        }
        if (ignoreRemainingPointers) return true
        if (!capturing && down != null && event.pointerCount > 1) {
            // Overlapping fingers before any stroke is claimed are ordinary typing.
            // Keep the entire stream, including POINTER_UP, for Android's split key
            // dispatch. Do not restart ink when only the last finger remains.
            releaseDown()
            typingPointers = true
        }
        if (typingPointers) {
            if (event.actionMasked == MotionEvent.ACTION_MOVE) return true
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) typingPointers = false
            val handled = super.dispatchTouchEvent(event)
            if (handled && event.actionMasked == MotionEvent.ACTION_UP) lastTypingUp = event.eventTime
            return handled
        }
        val start = down
        var typed = false
        if (start != null) {
            if (!capturing && (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_UP)) {
                confirmStroke(event, start)
            }
            if (capturing) {
                ink?.event(event)
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) { releaseDown(); capturing = false }
                return true
            }
            // Suppress key swipe, including diagonal and short moves. A short tap retains UP.
            if (event.actionMasked == MotionEvent.ACTION_MOVE) return true
            typed = event.actionMasked == MotionEvent.ACTION_UP
            if (typed || event.actionMasked == MotionEvent.ACTION_CANCEL) releaseDown()
        }
        val handled = super.dispatchTouchEvent(event)
        if (typed && handled) lastTypingUp = event.eventTime
        return handled
    }
    override fun dispatchDraw(canvas: Canvas) { super.dispatchDraw(canvas); ink?.draw(canvas) }
}
