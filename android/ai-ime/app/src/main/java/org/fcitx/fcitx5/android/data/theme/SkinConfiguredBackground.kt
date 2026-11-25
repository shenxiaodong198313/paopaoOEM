package org.fcitx.fcitx5.android.data.theme

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Alpha-only center-to-both-edges fade of the existing artwork, not a replacement color. */
internal class SkinConfiguredBackground(private val source: Drawable, private val range: Int) : Drawable() {
    private val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    override fun onBoundsChange(bounds: android.graphics.Rect) {
        source.bounds = bounds
        val edge = (.48f * range / 100f).coerceIn(0f, .48f)
        mask.shader = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT),
            floatArrayOf(0f, edge, 1f - edge, 1f), Shader.TileMode.CLAMP)
    }
    override fun draw(canvas: Canvas) {
        if (range <= 0) { source.draw(canvas); return }
        val layer = canvas.saveLayer(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), null)
        source.draw(canvas)
        canvas.drawRect(bounds, mask)
        canvas.restoreToCount(layer)
    }
    override fun setAlpha(alpha: Int) { source.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { source.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
