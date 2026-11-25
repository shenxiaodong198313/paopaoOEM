package org.fcitx.fcitx5.android.input.keyboard

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.SystemClock

/** Bounded, event-driven pose transition. Preloaded drawables; a single reusable frame callback. */
internal class SkinKeyStateDrawable(
    private val rest: Drawable,
    private val pressed: Drawable,
    private val released: Drawable?,
    private val releaseMs: Int
) : Drawable() {
    private var down = false
    private var releasingAt = 0L
    private val tick = Runnable { invalidateSelf() }
    override fun isStateful() = true
    override fun onBoundsChange(bounds: Rect) {
        rest.bounds = bounds; pressed.bounds = bounds; released?.bounds = bounds
    }
    override fun onStateChange(state: IntArray): Boolean {
        val next = android.R.attr.state_pressed in state
        if (next == down) return false
        unscheduleSelf(tick)
        releasingAt = if (down && !next && ValueAnimator.areAnimatorsEnabled() && released != null)
            SystemClock.uptimeMillis() else 0L
        down = next
        invalidateSelf()
        return true
    }
    override fun draw(canvas: Canvas) {
        unscheduleSelf(tick)
        when {
            down -> pressed.draw(canvas)
            releasingAt != 0L && released != null -> {
                val elapsed = SystemClock.uptimeMillis() - releasingAt
                if (elapsed >= releaseMs || !ValueAnimator.areAnimatorsEnabled()) {
                    releasingAt = 0L; rest.draw(canvas)
                } else {
                    rest.draw(canvas)
                    // Hold the reaction briefly, then blend back to the immutable rest art.
                    val progress = ((elapsed.toFloat() / releaseMs - .3f) / .7f).coerceIn(0f, 1f)
                    val save = canvas.saveLayerAlpha(null, ((1f - progress) * 255).toInt())
                    released.draw(canvas); canvas.restoreToCount(save)
                    scheduleSelf(tick, SystemClock.uptimeMillis() + 16L)
                }
            }
            else -> rest.draw(canvas)
        }
    }
    override fun jumpToCurrentState() {
        unscheduleSelf(tick); releasingAt = 0L; down = false; invalidateSelf()
    }
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        if (!visible) jumpToCurrentState()
        return super.setVisible(visible, restart)
    }
    override fun setAlpha(alpha: Int) { rest.alpha = alpha; pressed.alpha = alpha; released?.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) {
        rest.colorFilter = filter; pressed.colorFilter = filter; released?.colorFilter = filter
    }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A single isolated actor frame, uniformly fitted into a fixed part of its stationary cap. */
internal class SkinKeyActorFrameDrawable(
    private val image: Drawable, private val left: Float, private val top: Float,
    private val right: Float, private val bottom: Float
) : Drawable() {
    override fun onBoundsChange(bounds: Rect) {
        val region = android.graphics.RectF(bounds.left + bounds.width() * left,
            bounds.top + bounds.height() * top, bounds.left + bounds.width() * right,
            bounds.top + bounds.height() * bottom)
        val scale = minOf(region.width() / image.intrinsicWidth.coerceAtLeast(1),
            region.height() / image.intrinsicHeight.coerceAtLeast(1))
        val width = image.intrinsicWidth * scale
        val height = image.intrinsicHeight * scale
        image.setBounds((region.centerX() - width / 2).toInt(), (region.bottom - height).toInt(),
            (region.centerX() + width / 2).toInt(), region.bottom.toInt())
    }
    override fun draw(canvas: Canvas) = image.draw(canvas)
    override fun setAlpha(alpha: Int) { image.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { image.colorFilter = filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** One bounded action per press. Draw exactly one pose; never blend two actor silhouettes. */
internal class SkinKeySequenceDrawable(
    private val cap: Drawable, private val frames: List<Drawable>,
    private val durations: IntArray, private val enabled: Boolean
) : Drawable() {
    private var down = false
    private var startedAt = 0L
    private var currentFrameIndex = 0
    private val tick = Runnable { invalidateSelf() }
    override fun isStateful() = true
    override fun onBoundsChange(bounds: Rect) {
        cap.bounds = bounds
        frames.forEach { it.bounds = bounds }
    }
    override fun onStateChange(state: IntArray): Boolean {
        val next = android.R.attr.state_pressed in state
        if (next == down) return false
        down = next
        if (next) startedAt = if (enabled && ValueAnimator.areAnimatorsEnabled()) SystemClock.uptimeMillis() else 0L
        else if (!enabled || !ValueAnimator.areAnimatorsEnabled()) startedAt = 0L
        invalidateSelf()
        return true
    }
    override fun draw(canvas: Canvas) {
        unscheduleSelf(tick)
        val moving = enabled && ValueAnimator.areAnimatorsEnabled()
        currentFrameIndex = if (!moving) { startedAt = 0L; if (down) 1 else 0 }
            else if (startedAt != 0L) org.fcitx.fcitx5.android.data.theme.SkinSpriteTimeline.frameAt(SystemClock.uptimeMillis() - startedAt, durations)
            else 0
        if (currentFrameIndex == 0) startedAt = 0L
        cap.draw(canvas)
        frames[currentFrameIndex].draw(canvas)
        if (startedAt != 0L) scheduleSelf(tick, SystemClock.uptimeMillis() + 16L)
    }
    override fun jumpToCurrentState() {
        unscheduleSelf(tick); down = false; startedAt = 0L; currentFrameIndex = 0; invalidateSelf()
    }
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        if (!visible) jumpToCurrentState()
        return super.setVisible(visible, restart)
    }
    override fun setAlpha(alpha: Int) { cap.alpha = alpha; frames.forEach { it.alpha = alpha } }
    override fun setColorFilter(filter: ColorFilter?) {
        cap.colorFilter = filter; frames.forEach { it.colorFilter = filter }
    }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
