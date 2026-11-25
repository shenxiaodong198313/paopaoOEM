package org.fcitx.fcitx5.android.input.keyboard

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import org.fcitx.fcitx5.android.data.theme.skinInkBounds
import kotlin.math.min

/** Scales the entire authored cap with its press mask; never changes the gesture slot. */
internal class SkinFaceScaleDrawable(private val child: Drawable, private val x: Float, private val y: Float,
    private val insetX: Int, private val insetY: Int, private val gap: Float) : Drawable() {
    override fun onBoundsChange(bounds: Rect) { child.bounds = bounds }
    override fun isStateful() = child.isStateful
    override fun onStateChange(state: IntArray): Boolean = child.setState(state)
    private val sx get() = if (x <= 1f) x else min(x, (bounds.width() - gap) / (bounds.width() - 2f * insetX).coerceAtLeast(1f)).coerceAtLeast(1f)
    private val sy get() = if (y <= 1f) y else min(y, (bounds.height() - gap) / (bounds.height() - 2f * insetY).coerceAtLeast(1f)).coerceAtLeast(1f)
    fun faceBounds(art: Drawable, width: Int, height: Int): android.graphics.RectF {
        setBounds(0, 0, width, height)
        val r = art.skinInkBounds(android.graphics.RectF(art.bounds))
        val cx = width / 2f
        val cy = height / 2f
        return android.graphics.RectF(cx + (r.left - cx) * sx, cy + (r.top - cy) * sy,
            cx + (r.right - cx) * sx, cy + (r.bottom - cy) * sy)
    }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.scale(sx, sy, bounds.exactCenterX(), bounds.exactCenterY())
        child.draw(canvas)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { child.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { child.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
