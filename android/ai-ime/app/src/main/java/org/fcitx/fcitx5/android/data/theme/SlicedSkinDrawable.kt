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
    private val horizontalCaps = skinSliceCaps(bitmap.width, slice.left, slice.right, slice.asymmetricCaps)
    private val verticalCaps = skinSliceCaps(bitmap.height, slice.top, slice.bottom, slice.asymmetricCaps)

    internal fun inkBounds(target: RectF): RectF {
        val ink = skinBitmapInk(bitmap)
        if (ink.width() == bitmap.width && ink.height() == bitmap.height) return RectF(target)
        val scale = minOf(densityScale, target.width() / horizontalCaps.sum().coerceAtLeast(1),
            target.height() / verticalCaps.sum().coerceAtLeast(1))
        fun map(value: Int, length: Int, total: Float, caps: IntArray): Float {
            val first = caps[0]
            val last = caps[1]
            return when {
                value <= first -> value * scale
                value >= length - last -> total - (length - value) * scale
                else -> first * scale + (value - first) * (total - (first + last) * scale) /
                    (length - first - last).coerceAtLeast(1)
            }
        }
        return RectF(target.left + map(ink.left, bitmap.width, target.width(), horizontalCaps),
            target.top + map(ink.top, bitmap.height, target.height(), verticalCaps),
            target.left + map(ink.right, bitmap.width, target.width(), horizontalCaps),
            target.top + map(ink.bottom, bitmap.height, target.height(), verticalCaps))
    }

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
        val l = horizontalCaps[0]
        val r = horizontalCaps[1]
        val t = verticalCaps[0]
        val b = verticalCaps[1]
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

// Measure each decoded image once. Transparent extraction margins belong to the asset,
// so geometry comparisons use painted pixels. No scan takes place on a key press.
private val skinInkCache = java.util.WeakHashMap<Bitmap, Rect>()
private fun skinBitmapInk(bitmap: Bitmap): Rect = synchronized(skinInkCache) {
    skinInkCache.getOrPut(bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val rect = Rect(0, 0, w, h)
        if (bitmap.hasAlpha() && bitmap.config != Bitmap.Config.HARDWARE) {
            val line = IntArray(maxOf(w, h))
            fun occupied(x: Int, y: Int, cols: Int, rows: Int): Boolean {
                bitmap.getPixels(line, 0, cols, x, y, cols, rows)
                return (0 until cols * rows).any { android.graphics.Color.alpha(line[it]) >= 128 }
            }
            while (rect.left < w && !occupied(rect.left, 0, 1, h)) rect.left++
            if (rect.left == w) rect.set(0, 0, w, h) else {
                while (!occupied(rect.right - 1, 0, 1, h)) rect.right--
                while (!occupied(0, rect.top, w, 1)) rect.top++
                while (!occupied(0, rect.bottom - 1, w, 1)) rect.bottom--
            }
        }
        rect
    }
}

internal fun Drawable.skinInkBounds(target: RectF): RectF = when (this) {
    is SourceClippedSkinDrawable -> image.skinInkBounds(target)
    is SlicedSkinDrawable -> inkBounds(target)
    is android.graphics.drawable.BitmapDrawable -> {
        val ink = skinBitmapInk(bitmap)
        val dx = target.width() / bitmap.width
        val dy = target.height() / bitmap.height
        RectF(target.left + ink.left * dx, target.top + ink.top * dy,
            target.left + ink.right * dx, target.top + ink.bottom * dy)
    }
    else -> RectF(target)
}

// A magnified antialiased edge can cross half opacity between source pixel centers.
private val horizontalContourCache = java.util.WeakHashMap<Bitmap, Pair<Float, Float>>()
private fun Bitmap.horizontalContour(): Pair<Float, Float> = synchronized(horizontalContourCache) {
    horizontalContourCache.getOrPut(this) {
        val box = skinBitmapInk(this)
        if (!hasAlpha() || config == Bitmap.Config.HARDWARE) return@getOrPut 0f to width.toFloat()
        fun crossing(column: Int, direction: Int): Float {
            val neighbor = column + direction
            if (neighbor !in 0 until width) return if (direction < 0) 0f else width.toFloat()
            val pixels = IntArray(height * 2)
            getPixels(pixels, 0, 2, minOf(column, neighbor), 0, 2, height)
            var farthest = 0f
            for (row in 0 until height) {
                val inside = pixels[row * 2 + if (direction < 0) 1 else 0] ushr 24
                val outside = pixels[row * 2 + if (direction < 0) 0 else 1] ushr 24
                if (inside >= 128 && outside < 128)
                    farthest = maxOf(farthest, (inside - 127.5f) / (inside - outside))
            }
            return column + 0.5f + direction * farthest
        }
        crossing(box.left, -1) to crossing(box.right - 1, 1)
    }
}

private fun Drawable.horizontalInk(target: RectF): RectF = when (this) {
    is SourceClippedSkinDrawable -> image.horizontalInk(target)
    is android.graphics.drawable.BitmapDrawable -> {
        val (left, right) = bitmap.horizontalContour()
        val scale = target.width() / bitmap.width
        RectF(target.left + left * scale, target.top, target.left + right * scale, target.bottom)
    }
    else -> skinInkBounds(target)
}

/** Align this image's visible artwork to the requested slot, including nine-slice cap scaling. */
internal class SkinInkFitDrawable(private val artwork: Drawable, private val horizontalOnly: Boolean = false) : android.graphics.drawable.DrawableWrapper(artwork) {
    override fun onBoundsChange(bounds: Rect) {
        val target = RectF(bounds)
        val fitted = RectF(target)
        repeat(3) {
            val ink = if (horizontalOnly) artwork.horizontalInk(fitted) else artwork.skinInkBounds(fitted)
            if (ink.isEmpty) return
            fitted.set(fitted.left + target.left - ink.left, if (horizontalOnly) target.top else fitted.top + target.top - ink.top,
                fitted.right + target.right - ink.right, if (horizontalOnly) target.bottom else fitted.bottom + target.bottom - ink.bottom)
        }
        super.onBoundsChange(Rect(fitted.left.roundToInt(), fitted.top.roundToInt(),
            fitted.right.roundToInt(), fitted.bottom.roundToInt()))
    }
}
