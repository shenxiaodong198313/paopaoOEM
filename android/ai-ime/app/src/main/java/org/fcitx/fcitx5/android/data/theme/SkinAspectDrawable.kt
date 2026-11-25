package org.fcitx.fcitx5.android.data.theme

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.min
import kotlin.math.roundToInt

/** Centre a cap at its authored aspect ratio without changing hit geometry. */
class SkinAspectDrawable(private val image: Drawable, private val aspect: Float) : Drawable() {
    override fun onBoundsChange(bounds: Rect) {
        val width = min(bounds.width().toFloat(), bounds.height() * aspect)
        val height = width / aspect
        val left = bounds.left + (bounds.width() - width) / 2f
        val top = bounds.top + (bounds.height() - height) / 2f
        image.setBounds(left.roundToInt(), top.roundToInt(), (left + width).roundToInt(), (top + height).roundToInt())
    }
    override fun draw(canvas: Canvas) = image.draw(canvas)
    override fun setAlpha(alpha: Int) { image.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { image.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
