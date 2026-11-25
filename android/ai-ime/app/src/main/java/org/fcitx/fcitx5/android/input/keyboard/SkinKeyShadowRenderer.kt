package org.fcitx.fcitx5.android.input.keyboard

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.view.allViews
import org.fcitx.fcitx5.android.data.theme.SkinKeyFaceShadow
import kotlin.math.min
import kotlin.math.roundToInt

/** One cached software mask per keyboard size. Touches never blur or allocate a bitmap. */
internal class SkinKeyShadowRenderer(private val density: Float, private val effect: SkinKeyFaceShadow) {
    private var bitmap: Bitmap? = null
    private var previous = emptyList<RectF>()
    private var previousSize = 0L
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun layout(host: BaseKeyboard) {
        if (host.width <= 0 || host.height <= 0 || effect.opacity <= 0f) { clear(); return }
        val faces = mutableListOf<RectF>()
        host.sidebarFace?.let { faces.add(RectF(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())) }
        host.allViews.filterIsInstance<KeyView>().forEach { key ->
            if (key.sharedSidebarSlot || key.visibility != android.view.View.VISIBLE) return@forEach
            val rect = key.skinShadowBoundsIn(host)
            if (rect.isEmpty) return@forEach
            if (key.paintsSkinCap) faces.add(rect)
        }
        val size = (host.width.toLong() shl 32) or host.height.toLong()
        if (size == previousSize && faces == previous && bitmap != null) return
        clear()
        previous = faces.map(::RectF)
        previousSize = size
        // A soft mask does not need full resolution on unusually large displays.
        val scale = min(1f, kotlin.math.sqrt(4_000_000.0 / (host.width.toDouble() * host.height)).toFloat())
        val mask = Bitmap.createBitmap((host.width * scale).roundToInt().coerceAtLeast(1),
            (host.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        canvas.scale(scale, scale)
        val ink = Color.argb((Color.alpha(effect.color) * effect.opacity).roundToInt(),
            Color.red(effect.color), Color.green(effect.color), Color.blue(effect.color))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            if (effect.blurRadiusDp > 0f) maskFilter =
                BlurMaskFilter(effect.blurRadiusDp * density, BlurMaskFilter.Blur.NORMAL)
        }
        for (face in faces) {
            val r = RectF(face.left + effect.insets.leftDp * density,
                face.top + effect.insets.topDp * density,
                face.right - effect.insets.rightDp * density,
                face.bottom - effect.insets.bottomDp * density)
            r.inset(r.width() * (1f - effect.widthScale) / 2f, r.height() * (1f - effect.heightScale) / 2f)
            r.inset(-effect.spreadDp * density, -effect.spreadDp * density)
            if (r.isEmpty) continue
            r.offset(effect.offsetXDp * density, effect.offsetYDp * density)
            canvas.drawRoundRect(r, effect.cornerRadiusDp * density, effect.cornerRadiusDp * density, paint)
        }
        // The grid owns a finite bitmap. Its last row must reach transparent before that
        // boundary, rather than cutting off a still-visible blur. Earlier rows are untouched.
        val fade = min(host.height.toFloat(), effect.bottomFadeDp * density)
        if (fade > 0f) {
            val end = host.height.toFloat()
            val start = end - fade
            val tailPaint = Paint().apply {
                shader = LinearGradient(0f, start, 0f, end, Color.WHITE, Color.TRANSPARENT,
                    Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawRect(0f, start, host.width.toFloat(), end, tailPaint)
        }
        bitmap = mask
    }

    fun draw(canvas: Canvas, width: Int, height: Int) {
        bitmap?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), bitmapPaint) }
    }

    fun clear() {
        bitmap = null; previous = emptyList(); previousSize = 0L
    }
}
