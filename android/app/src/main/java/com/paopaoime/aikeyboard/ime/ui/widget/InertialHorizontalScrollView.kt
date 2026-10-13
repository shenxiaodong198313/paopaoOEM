package com.paopaoime.aikeyboard.ime.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.roundToInt

class InertialHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val inertialScroller = OverScroller(context).apply {
        setFriction(ViewConfiguration.getScrollFriction() * 0.72f)
    }
    private var initialX = 0f
    private var initialY = 0f
    private var isHorizontalGesture = false

    init {
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        runCatching {
            val field = HorizontalScrollView::class.java.getDeclaredField("mScroller")
            field.isAccessible = true
            field.set(this, inertialScroller)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ev.x
                initialY = ev.y
                isHorizontalGesture = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - initialX
                val dy = ev.y - initialY
                val absDx = abs(dx)
                val absDy = abs(dy)
                if (!isHorizontalGesture) {
                    when {
                        absDy > touchSlop && absDy > absDx -> {
                            parent?.requestDisallowInterceptTouchEvent(false)
                            return false
                        }
                        absDx > touchSlop && absDx > absDy -> {
                            isHorizontalGesture = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isHorizontalGesture = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ev.x
                initialY = ev.y
                isHorizontalGesture = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - initialX
                val dy = ev.y - initialY
                val absDx = abs(dx)
                val absDy = abs(dy)
                if (!isHorizontalGesture && absDx > touchSlop && absDx > absDy) {
                    isHorizontalGesture = true
                }
                parent?.requestDisallowInterceptTouchEvent(isHorizontalGesture)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isHorizontalGesture = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun fling(velocityX: Int) {
        val absVelocity = abs(velocityX)
        val factor = when {
            absVelocity >= 9000 -> 2.5f
            absVelocity >= 7000 -> 2.2f
            absVelocity >= 5000 -> 1.9f
            absVelocity >= 3200 -> 1.65f
            absVelocity >= 1800 -> 1.45f
            else -> 1.25f
        }
        val adjustedVelocity = (velocityX * factor).roundToInt()
        super.fling(adjustedVelocity)
    }
}
