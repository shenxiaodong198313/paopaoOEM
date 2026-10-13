package org.fcitx.fcitx5.android.data.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min
import kotlin.math.roundToInt
import org.fcitx.fcitx5.android.utils.DarkenColorFilter

/** Nine-region bitmap renderer. Only the center may tile; corners never stretch independently. */
class SlicedSkinDrawable(
    private val bitmap: Bitmap,
    private val slice: SkinImageSlice,
    targetDensityDpi: Int,
    private val pattern: SkinPatternFill? = null,
    patternBitmap: Bitmap? = null
) : Drawable() {
    // Adjacent bitmap rectangles must not each anti-alias their shared edge.
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val densityScale = targetDensityDpi.toFloat() / slice.sourceDensityDpi.coerceIn(72, 1280)
    private val dpScale = targetDensityDpi / 160f
    private val patternScale = targetDensityDpi.toFloat() / (pattern?.sourceDensityDpi ?: 160).coerceIn(72, 1280)
    private val patternShader = patternBitmap?.let { BitmapShader(it, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    private val patternMatrix = Matrix()
    private val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = patternShader
        colorFilter = DarkenColorFilter(100 - (pattern?.image?.brightness ?: 100))
    }
    private val source = Rect()
    private val destination = RectF()

    override fun draw(canvas: Canvas) {
        if (pattern != null && patternShader != null) {
            destination.set(bounds.left + pattern.leftDp.coerceAtLeast(0f) * dpScale,
                bounds.top + pattern.topDp.coerceAtLeast(0f) * dpScale,
                bounds.right - pattern.rightDp.coerceAtLeast(0f) * dpScale,
                bounds.bottom - pattern.bottomDp.coerceAtLeast(0f) * dpScale)
            if (destination.width() > 0 && destination.height() > 0) {
                patternMatrix.setScale(patternScale, patternScale)
                patternMatrix.postTranslate(destination.left, destination.top)
                patternShader.setLocalMatrix(patternMatrix)
                val radius = min(pattern.cornerRadiusDp.coerceAtLeast(0f) * dpScale, min(destination.width(), destination.height()) / 2f)
                canvas.drawRoundRect(destination, radius, radius, patternPaint)
            }
        }
        val l = slice.left.coerceIn(0, bitmap.width / 2)
        val r = slice.right.coerceIn(0, bitmap.width / 2)
        val t = slice.top.coerceIn(0, bitmap.height / 2)
        val b = slice.bottom.coerceIn(0, bitmap.height / 2)
        // Reduce all caps uniformly for very small targets, preserving corner proportions.
        val capScale = min(densityScale, min(
            bounds.width().toFloat() / (l + r).coerceAtLeast(1),
            bounds.height().toFloat() / (t + b).coerceAtLeast(1)
        ))
        val sx = intArrayOf(0, l, bitmap.width - r, bitmap.width)
        val sy = intArrayOf(0, t, bitmap.height - b, bitmap.height)
        val dx = floatArrayOf(bounds.left.toFloat(), bounds.left + l * capScale, bounds.right - r * capScale, bounds.right.toFloat())
        val dy = floatArrayOf(bounds.top.toFloat(), bounds.top + t * capScale, bounds.bottom - b * capScale, bounds.bottom.toFloat())
        // Shared integer boundaries avoid hairlines from fractional GPU coverage.
        for (i in 1..2) { dx[i] = dx[i].roundToInt().toFloat(); dy[i] = dy[i].roundToInt().toFloat() }
        for (row in 0..2) for (column in 0..2) {
            if (sx[column + 1] <= sx[column] || sy[row + 1] <= sy[row]) continue
            source.set(sx[column], sy[row], sx[column + 1], sy[row + 1])
            destination.set(dx[column], dy[row], dx[column + 1], dy[row + 1])
            if (destination.width() <= 0 || destination.height() <= 0) continue
            if (slice.tileCenter && row == 1 && column == 1) {
                val save = canvas.save()
                canvas.clipRect(destination)
                val tileWidth = (source.width() * densityScale).coerceAtLeast(1f)
                val tileHeight = (source.height() * densityScale).coerceAtLeast(1f)
                var y = dy[row]
                while (y < dy[row + 1]) {
                    var x = dx[column]
                    while (x < dx[column + 1]) {
                        canvas.drawBitmap(bitmap, source, RectF(x, y, x + tileWidth, y + tileHeight), paint)
                        x += tileWidth
                    }
                    y += tileHeight
                }
                canvas.restoreToCount(save)
            } else canvas.drawBitmap(bitmap, source, destination, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; patternPaint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        patternPaint.colorFilter = colorFilter ?: DarkenColorFilter(100 - (pattern?.image?.brightness ?: 100))
        invalidateSelf()
    }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
