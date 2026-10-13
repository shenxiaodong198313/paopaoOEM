package org.fcitx.fcitx5.android.input.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import org.fcitx.fcitx5.android.data.theme.SkinAmbientEffect
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Decorative only. Frames stop when the IME is hidden/detached; never owns input. */
internal class SkinAmbientView(context: Context, private val style: SkinAmbientEffect) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(731)
    private val seeds = List(style.particleCount) { FloatArray(4) { random.nextFloat() } }
    private val frame = Runnable { if (isShown && windowVisibility == VISIBLE) invalidate() }
    private var active = false
    init {
        isClickable = false; isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        active = isVisible
        removeCallbacks(frame)
        if (isVisible) invalidate()
    }
    override fun onDetachedFromWindow() {
        active = false; removeCallbacks(frame)
        super.onDetachedFromWindow()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active || !isShown || !ValueAnimator.areAnimatorsEnabled()) return
        val density = resources.displayMetrics.density
        val left = (style.insetHorizontalDp * density).coerceAtMost(width * .38f)
        val top = height * style.insetVerticalFraction
        val w = width - 2 * left
        val h = height - 2 * top
        if (w <= 0 || h <= 0) return
        val time = SystemClock.uptimeMillis().toDouble() / style.cycleMs
        val save = canvas.save()
        canvas.clipRect(left, top, left+w, top+h)
        for (s in seeds) {
            val phase = ((time * (.8 + .4*s[3]) + s[0]) % 1.0).toFloat()
            val x = left + w * (.08f + s[1]*.84f + .025f*sin(time*PI*2+s[0]*6).toFloat())
            val y = top + h*(1-phase)
            val fade = sin(phase * PI).toFloat().coerceAtLeast(0f)
            paint.color = style.color
            paint.alpha = (255*style.opacity*fade).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = .3f*density
            canvas.drawCircle(x,y,style.radiusDp*density*(.65f+s[2]*.65f),paint)
        }
        canvas.restoreToCount(save)
        removeCallbacks(frame)
        postDelayed(frame, 50L)
    }
}
