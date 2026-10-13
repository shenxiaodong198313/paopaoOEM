package com.paopaoime.aikeyboard.ime.ui.drawable

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

class SoftShadowDrawable(
    density: Float,
    private val cornerRadiusDp: Float = 30f,
    private val shadowBlurDp: Float = 24f,
    private val shadowOffsetYDp: Float = 6f,
    private val contentInsetXDp: Float = 20f,
    private val contentInsetYDp: Float = 24f,
    fillColor: Int = Color.WHITE,
    shadowColor: Int = Color.parseColor("#1E000000")
) : Drawable() {

    private val cornerRadiusPx = cornerRadiusDp * density
    private val shadowBlurPx = shadowBlurDp * density
    private val shadowOffsetYPx = shadowOffsetYDp * density
    private val contentInsetXPx = contentInsetXDp * density
    private val contentInsetYPx = contentInsetYDp * density
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        setShadowLayer(shadowBlurPx, 0f, shadowOffsetYPx, shadowColor)
    }

    override fun draw(canvas: Canvas) {
        rect.set(
            bounds.left + contentInsetXPx,
            bounds.top + contentInsetYPx,
            bounds.right - contentInsetXPx,
            bounds.bottom - contentInsetYPx
        )
        if (rect.width() <= 0f || rect.height() <= 0f) return
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
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
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
