package org.fcitx.fcitx5.android.input.keyboard

import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.view.allViews
import org.fcitx.fcitx5.android.data.theme.SkinKeyDecoration
import kotlin.math.min
import kotlin.math.roundToInt

/** A keyboard-level draw pass. No added View, listener, focus target, or animation timer. */
internal class SkinKeyDecorationRenderer(
    private val density: Float,
    private val decorations: Map<String, SkinKeyDecoration>
) {
    private data class Entry(val key: KeyView, val style: SkinKeyDecoration, val drawable: Drawable)
    private var entries = emptyList<Entry>()
    private val loaded = mutableMapOf<String, Drawable?>()

    fun layout(host: BaseKeyboard) {
        entries = host.allViews.filterIsInstance<KeyView>().mapNotNull { key ->
            val id = key.skinKeyIds.firstOrNull(decorations::containsKey) ?: return@mapNotNull null
            val style = decorations.getValue(id)
            // Keep failed optional artwork absent; never replace the key's dynamic contents.
            val drawable = loaded.getOrPut(id) { style.image.toDrawable(host.context)?.mutate() } ?: return@mapNotNull null
            Entry(key, style, drawable)
        }.toList()
    }

    fun draw(canvas: Canvas, host: BaseKeyboard) {
        for ((key, style, drawable) in entries) {
            if (!key.isShown) continue
            val face = key.skinBoundsIn(host)
            if (face.isEmpty) continue
            val width = style.widthDp * density
            val height = style.heightDp * density
            val ratio = min(width / drawable.intrinsicWidth.coerceAtLeast(1),
                height / drawable.intrinsicHeight.coerceAtLeast(1))
            val w = drawable.intrinsicWidth.coerceAtLeast(1) * ratio
            val h = drawable.intrinsicHeight.coerceAtLeast(1) * ratio
            val cx = face.left + face.width() * style.anchorX + style.offsetXDp * density
            val cy = face.top + face.height() * style.anchorY + style.offsetYDp * density
            val clip = RectF(face).apply { inset(-style.maxOverflowDp * density, -style.maxOverflowDp * density) }
            val save = canvas.save()
            canvas.clipRect(clip)
            drawable.setBounds((cx-w/2).roundToInt(), (cy-h/2).roundToInt(),
                (cx+w/2).roundToInt(), (cy+h/2).roundToInt())
            drawable.alpha = if (key.isEnabled) 255 else 100
            drawable.draw(canvas)
            canvas.restoreToCount(save)
        }
    }
}
