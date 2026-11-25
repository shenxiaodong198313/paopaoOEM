/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs

/** A second finger may lift before the first. Only confirmed taps leave in down order. */
internal class LetterTapOrder {
    class Tap(val click: () -> Unit) {
        var resolved = false
        var accepted = false
    }

    private val pending = ArrayDeque<Tap>()
    fun begin(click: () -> Unit) = Tap(click).also { pending.addLast(it) }

    fun finish(tap: Tap, accepted: Boolean) {
        tap.accepted = accepted
        tap.resolved = true
        while (pending.firstOrNull()?.resolved == true) {
            val next = pending.removeFirst()
            if (next.accepted) next.click()
        }
    }

    fun afterLetters(click: () -> Unit) {
        if (pending.isEmpty()) click() else finish(begin(click), true)
    }

    fun clear() = pending.clear()
}

open class CustomGestureView(ctx: Context) : FrameLayout(ctx) {
    internal var letterTapOrder: LetterTapOrder? = null
    private var pendingLetter: LetterTapOrder.Tap? = null
    private var letterPointer = -1
    private var downX = 0f

    private fun finishLetter(accepted: Boolean) {
        val tap = pendingLetter ?: return
        pendingLetter = null
        letterTapOrder?.finish(tap, accepted)
    }

    var deleteGesture: org.fcitx.fcitx5.android.input.deletion.DeleteKeyGesture? = null


    enum class SwipeAxis { X, Y }

    enum class GestureType { Down, Move, Up, Cancel }

    data class Event(
        val type: GestureType,
        val consumed: Boolean,
        val x: Float,
        val y: Float,
        val countX: Int,
        val countY: Int,
        val totalX: Int,
        val totalY: Int
    )

    fun interface OnGestureListener {
        fun onGesture(view: View, event: Event): Boolean

        companion object {
            val Empty = OnGestureListener { _, _ -> false }
        }
    }

    private val lifecycleScope by lazy {
        findViewTreeLifecycleOwner()?.lifecycleScope!!
    }

    @Volatile
    private var touchMovedOutside = false

    @Volatile
    private var longPressTriggered = false
    var longPressEnabled = false
    var popupLongPressDelayMs: Int? = null
    private var longPressJob: Job? = null

    @Volatile
    var longPressFeedbackEnabled = true

    @Volatile
    private var repeatStarted = false
    var repeatEnabled = false
    private var repeatJob: Job? = null

    var swipeEnabled = false
    var swipeRepeatEnabled = false
    var swipeThresholdX = 24f
    var swipeThresholdY = 24f

    private var swipeRepeatTriggered = false
    private var swipeLastX = -1f
    private var swipeLastY = -1f
    private var swipeXUnconsumed = 0f
    private var swipeYUnconsumed = 0f
    private var swipeTotalX = 0
    private var swipeTotalY = 0
    private var gestureConsumed = false

    var doubleTapEnabled = false
    private var lastClickTime = 0L
    private var maybeDoubleTap = false

    var onDoubleTapListener: ((View) -> Unit)? = null
    var onRepeatListener: ((View) -> Unit)? = null
    var onGestureListener: OnGestureListener? = null

    var soundEffect: InputFeedbacks.SoundEffect = InputFeedbacks.SoundEffect.Standard
    var skinSoundCandidates: List<String> = emptyList()
    var soundOnTouch: Boolean = true

    private val touchSlop: Float = ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()

    init {
        // disable system sound effect and haptic feedback
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            isPressed = false
        }
    }

    private fun pointInView(x: Float, y: Float): Boolean {
        val horizontal = x >= -touchSlop && x < width + touchSlop ||
                letterTapOrder != null && kotlin.math.abs(x - downX) <= width
        return horizontal && y >= -touchSlop && y < height + touchSlop
    }

    private fun resetState() {
        touchMovedOutside = false
        if (longPressEnabled) {
            longPressTriggered = false
            longPressJob?.cancel()
            longPressJob = null
        }
        if (repeatEnabled) {
            repeatStarted = false
            repeatJob?.cancel()
            repeatJob = null
        }
        if (swipeEnabled) {
            if (swipeRepeatEnabled) {
                swipeRepeatTriggered = false
            }
            swipeXUnconsumed = 0f
            swipeYUnconsumed = 0f
            swipeTotalX = 0
            swipeTotalY = 0
            gestureConsumed = false
        }
        // double tap state should be preserved on touch up
    }

    fun cancelGestures() {
        letterPointer = -1
        finishLetter(false)
        deleteGesture?.cancel()
        isPressed = false
        resetState()
        // reset double tap state on cancel
        if (doubleTapEnabled) {
            maybeDoubleTap = false
            lastClickTime = 0
        }
        // Cancellation releases listeners' state without triggering an Up action or click.
        dispatchGestureEvent(GestureType.Cancel, 0f, 0f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        deleteGesture?.let { return it.touch(event) }
        if (letterTapOrder == null) return handleTouch(event.actionMasked, event.x, event.y)
        val index = event.actionIndex
        val id = event.getPointerId(index)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> letterPointer = id
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Android groups both contacts when two fingers land on the very same key.
                val prior = event.findPointerIndex(letterPointer)
                if (prior >= 0) handleTouch(MotionEvent.ACTION_UP, event.getX(prior), event.getY(prior))
                letterPointer = id
                return handleTouch(MotionEvent.ACTION_DOWN, event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                if (id != letterPointer) return true
                letterPointer = -1
                return handleTouch(MotionEvent.ACTION_UP, event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> {
                val active = event.findPointerIndex(letterPointer)
                return active < 0 || handleTouch(MotionEvent.ACTION_MOVE, event.getX(active), event.getY(active))
            }
        }
        return handleTouch(event.actionMasked, event.x, event.y)
    }

    private fun handleTouch(action: Int, x: Float, y: Float): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled) return false
                downX = x
                finishLetter(false)
                pendingLetter = letterTapOrder?.begin { performClick() }
                InputFeedbacks.beginTouch()
                drawableHotspotChanged(x, y)
                isPressed = true
                InputFeedbacks.hapticFeedback(this)
                if (soundOnTouch) InputFeedbacks.soundEffect(soundEffect, skinSoundCandidates)
                dispatchGestureEvent(GestureType.Down, x, y)
                if (longPressEnabled) {
                    longPressJob?.cancel()
                    longPressJob = lifecycleScope.launch {
                        delay((popupLongPressDelayMs ?: longPressDelay).toLong())
                        if (longPressFeedbackEnabled) {
                            InputFeedbacks.hapticFeedback(this@CustomGestureView, true)
                        }
                        longPressTriggered = performLongClick()
                        if (longPressTriggered) finishLetter(false)
                    }
                }
                if (repeatEnabled) {
                    repeatJob?.cancel()
                    repeatJob = lifecycleScope.launch {
                        delay(longPressDelay.toLong())
                        repeatStarted = true
                        var lastTriggerTime: Long
                        while (isActive && isEnabled) {
                            lastTriggerTime = SystemClock.uptimeMillis()
                            onRepeatListener?.invoke(this@CustomGestureView)
                            val t = lastTriggerTime + RepeatInterval - SystemClock.uptimeMillis()
                            if (t > 0) delay(t)
                        }
                    }
                }
                if (swipeEnabled) {
                    swipeLastX = x
                    swipeLastY = y
                }
            }
            MotionEvent.ACTION_UP -> {
                isPressed = false
                InputFeedbacks.hapticFeedback(this, longPress = true, keyUp = true)
                dispatchGestureEvent(GestureType.Up, x, y)
                val shouldPerformClick = !(touchMovedOutside ||
                        longPressTriggered ||
                        repeatStarted ||
                        swipeRepeatTriggered ||
                        gestureConsumed)
                resetState()
                if (letterTapOrder != null) {
                    finishLetter(shouldPerformClick)
                } else if (shouldPerformClick) {
                    if (doubleTapEnabled) {
                        val now = System.currentTimeMillis()
                        if (maybeDoubleTap && now - lastClickTime <= longPressDelay) {
                            maybeDoubleTap = false
                            onDoubleTapListener?.invoke(this)
                        } else {
                            maybeDoubleTap = true
                            performClick()
                        }
                        lastClickTime = now
                    } else {
                        performClick()
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isEnabled) return false
                drawableHotspotChanged(x, y)
                if (!touchMovedOutside && !pointInView(x, y)) {
                    touchMovedOutside = true
                    finishLetter(false)
                    if (longPressEnabled) {
                        longPressJob?.cancel()
                        longPressJob = null
                    }
                    if (repeatEnabled) {
                        repeatJob?.cancel()
                        repeatJob = null
                    }
                    if (repeatStarted || !swipeEnabled) {
                        isPressed = false
                    }
                }
                if (!swipeEnabled || longPressTriggered || repeatStarted) return true
                val countX = consumeSwipe(x, SwipeAxis.X)
                val countY = consumeSwipe(y, SwipeAxis.Y)
                dispatchGestureEvent(GestureType.Move, x, y, countX, countY)
                swipeLastX = x
                swipeLastY = y
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelGestures()
                return true
            }
        }
        return true
    }

    private fun dispatchGestureEvent(
        type: GestureType,
        x: Float,
        y: Float,
        countX: Int = 0,
        countY: Int = 0
    ) {
        val event = Event(type, gestureConsumed, x, y, countX, countY, swipeTotalX, swipeTotalY)
        val consumed = onGestureListener?.onGesture(this, event) ?: return
        if (consumed && !gestureConsumed) {
            gestureConsumed = true
        }
    }

    private fun consumeSwipe(current: Float, axis: SwipeAxis): Int {
        val unconsumed: Float
        val threshold: Float
        when (axis) {
            SwipeAxis.X -> {
                unconsumed = current - swipeLastX + swipeXUnconsumed
                threshold = swipeThresholdX
            }
            SwipeAxis.Y -> {
                unconsumed = current - swipeLastY + swipeYUnconsumed
                threshold = swipeThresholdY
            }
        }
        val remains: Float = unconsumed % threshold
        val count: Int = (unconsumed / threshold).toInt()
        if (count != 0) {
            if (swipeRepeatEnabled && !swipeRepeatTriggered) {
                swipeRepeatTriggered = true
            }
            if (longPressEnabled && !longPressTriggered) {
                longPressJob?.cancel()
                longPressJob = null
            }
            if (repeatEnabled && !repeatStarted) {
                repeatJob?.cancel()
                repeatJob = null
            }
        }
        when (axis) {
            SwipeAxis.X -> {
                swipeXUnconsumed = remains
                swipeTotalX += count
            }
            SwipeAxis.Y -> {
                swipeYUnconsumed = remains
                swipeTotalY += count
            }
        }
        return count
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) {
            deleteGesture?.cancel()
            if (pendingLetter != null) cancelGestures()
        }
    }

    override fun onDetachedFromWindow() {
        deleteGesture?.cancel()
        if (pendingLetter != null) cancelGestures()
        super.onDetachedFromWindow()
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        longPressEnabled = l != null
        super.setOnLongClickListener(l)
    }

    companion object {
        val longPressDelay by AppPrefs.getInstance().keyboard.longPressDelay
        const val RepeatInterval = 50L
    }
}
