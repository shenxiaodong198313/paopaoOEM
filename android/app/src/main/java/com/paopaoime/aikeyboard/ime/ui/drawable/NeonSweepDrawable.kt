package com.paopaoime.aikeyboard.ime.ui.drawable

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable

class NeonSweepDrawable : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: Shader? = null

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (bounds.isEmpty) {
            shader = null
            return
        }
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        shader = SweepGradient(
            centerX,
            centerY,
            intArrayOf(
                Color.parseColor("#7FDBFF"),
                Color.parseColor("#7EA7FF"),
                Color.parseColor("#B58CFF"),
                Color.parseColor("#F39AD9"),
                Color.parseColor("#FFD46B"),
                Color.parseColor("#8DEDD7"),
                Color.parseColor("#7FDBFF")
            ),
            floatArrayOf(0f, 0.18f, 0.36f, 0.54f, 0.72f, 0.88f, 1f)
        )
        paint.shader = shader
    }

    override fun draw(canvas: Canvas) {
        if (shader == null) return
        canvas.drawRect(bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
