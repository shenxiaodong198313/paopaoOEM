// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.deletion

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Keeps the original touch owner while the finger moves into either action target. */
class DeleteKeyGesture(
    private val view: CustomGestureView,
    private val boundary: View,
    private val dispatch: (KeyAction) -> Unit
) {
    private var active = false
    private var held = false
    private var vertical = false
    private var horizontal = false
    private var paused = false
    private var armed = ""
    private var canClear = false
    private var canUndo = false
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var totalX = 0
    private val upper = Rect()
    private val lower = Rect()
    private var upPopup: PopupWindow? = null
    private var downPopup: PopupWindow? = null
    private val repeat = object : Runnable {
        override fun run() {
            if (!view.isEnabled || !view.isAttachedToWindow) { cancel(); return }
            if (!active || paused || armed.isNotEmpty() || horizontal) return
            held = true
            if (canClear) refreshAvailability()
            show()
            view.onRepeatListener?.invoke(view) ?: view.performClick()
            view.postDelayed(this, CustomGestureView.RepeatInterval)
        }
    }
    private fun geometry() {
        // PopupWindow is attached to the IME window, whose screen origin is
        // usually below the host editor. Keep both drawing and hit geometry in
        // that window; screen/root-view coordinates would add the IME offset twice.
        val loc = IntArray(2); view.getLocationInWindow(loc)
        val origin = IntArray(2); boundary.getLocationInWindow(origin)
        val root = Rect(origin[0], origin[1], origin[0] + boundary.width, origin[1] + boundary.height)
        val width = min(root.width() - 8, max(dp(138), view.width * 3))
        val height = max(dp(32), view.height - dp(8))
        val x = max(root.left + 4, min(root.right - width - 4, loc[0] + view.width - width))
        upper.set(x - loc[0], -view.height, x - loc[0] + width, -view.height + height)
        val y = max(loc[1] + view.height, root.bottom - view.height)
        lower.set(x - loc[0], y - loc[1], x - loc[0] + width, y - loc[1] + height)
    }
    private fun dp(value: Int) = (value * view.resources.displayMetrics.density).toInt()
    private fun popup(label: String, rect: Rect, color: Int): PopupWindow {
        val text = TextView(view.context).apply {
            this.text = label; textSize = 19f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(9).toFloat(); setStroke(dp(3), 0x99888888.toInt()) }
            contentDescription = label
            val icon = if (label == "上滑清空") org.fcitx.fcitx5.android.R.drawable.delete_clear_outline else org.fcitx.fcitx5.android.R.drawable.delete_undo_outline
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            compoundDrawablePadding = dp(6); setPadding(dp(10), 0, dp(8), 0)
        }
        val position = IntArray(2); view.getLocationInWindow(position)
        return PopupWindow(text, rect.width(), rect.height(), false).apply {
            isTouchable = false; isFocusable = false; isOutsideTouchable = false
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            elevation = dp(2).toFloat(); animationStyle = 0
            showAtLocation(view, Gravity.TOP or Gravity.LEFT, position[0] + rect.left, position[1] + rect.top)
        }
    }
    private fun refreshAvailability() {
        dispatch(KeyAction.DeleteGestureAvailabilityAction { clear, undo ->
            if (active) { canClear = clear; canUndo = undo }
        })
    }
    private fun show() {
        if (!view.isAttachedToWindow) return
        if (canClear) {
            if (upPopup == null) upPopup = popup("上滑清空", upper, 0xFFFF464C.toInt())
        } else { upPopup?.dismiss(); upPopup = null }
        if (canUndo) {
            if (downPopup == null) downPopup = popup("下滑撤回", lower, 0xFF168BFA.toInt())
        } else { downPopup?.dismiss(); downPopup = null }
    }
    fun touch(event: MotionEvent): Boolean {
        if (!view.isEnabled || event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_CANCEL) { cancel(); return true }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancel(); if (!view.isEnabled) return false
                active = true; startX = event.x; startY = event.y; lastX = event.x; totalX = 0
                canClear = false; canUndo = false
                refreshAvailability()
                geometry(); view.isPressed = true
                InputFeedbacks.beginTouch(); InputFeedbacks.hapticFeedback(view)
                if (view.soundOnTouch) InputFeedbacks.soundEffect(view.soundEffect, view.skinSoundCandidates)
                view.parent?.requestDisallowInterceptTouchEvent(true)
                view.postDelayed(repeat, CustomGestureView.longPressDelay.toLong())
            }
            MotionEvent.ACTION_MOVE -> if (active) {
                val up = canClear && upper.contains(event.x.toInt(), event.y.toInt())
                val down = canUndo && lower.contains(event.x.toInt(), event.y.toInt())
                if (up || down) { if (horizontal) { dispatch(KeyAction.CancelSelectionAction); horizontal = false }; vertical = true; paused = true; armed = if (up) "clear" else "undo"; view.removeCallbacks(repeat); show() }
                else {
                    armed = ""
                    val inKey = event.x >= 0 && event.x < view.width && event.y >= 0 && event.y < view.height
                    if (!inKey) { paused = true; view.removeCallbacks(repeat) }
                    if (!vertical && abs(event.y - startY) < view.height * .4f && abs(event.x - startX) >= view.swipeThresholdX) {
                        horizontal = true; view.removeCallbacks(repeat)
                        val count = ((event.x - lastX) / view.swipeThresholdX).toInt()
                        if (count != 0) { totalX += count; lastX += count * view.swipeThresholdX; dispatch(KeyAction.MoveSelectionAction(count)) }
                    } else if (inKey && !horizontal && paused) {
                        paused = false
                        view.removeCallbacks(repeat); view.postDelayed(repeat, if (held) CustomGestureView.RepeatInterval else CustomGestureView.longPressDelay.toLong())
                    }
                }
                upPopup?.contentView?.alpha = if (armed == "undo") .65f else 1f
                downPopup?.contentView?.alpha = if (armed == "clear") .65f else 1f
            }
            MotionEvent.ACTION_UP -> if (active) {
                // UP may carry a newer position than the last delivered MOVE.
                // A target armed earlier must never authorize an outside release.
                refreshAvailability()
                val action = when {
                    armed == "clear" && canClear && upper.contains(event.x.toInt(), event.y.toInt()) -> "clear"
                    armed == "undo" && canUndo && lower.contains(event.x.toInt(), event.y.toInt()) -> "undo"
                    else -> ""
                }
                val tap = !held && !vertical && !horizontal && event.x >= 0 && event.x < view.width && event.y >= 0 && event.y < view.height
                val selection = horizontal; val count = totalX
                finish()
                when { action == "clear" -> dispatch(KeyAction.DeleteClearAction)
                    action == "undo" -> dispatch(KeyAction.DeleteUndoAction)
                    selection -> dispatch(KeyAction.DeleteSelectionAction(count))
                    tap -> view.performClick() }
            }
        }
        return true
    }
    private fun finish() { view.removeCallbacks(repeat); active = false; paused = false; held = false; vertical = false; horizontal = false; armed = ""; view.isPressed = false; upPopup?.dismiss(); downPopup?.dismiss(); upPopup = null; downPopup = null }
    fun cancel() { val selection = horizontal; finish(); if (selection) dispatch(KeyAction.CancelSelectionAction) }
}
