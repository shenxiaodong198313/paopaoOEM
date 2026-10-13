package org.fcitx.fcitx5.android.input.popup

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.core.graphics.PathParser
import org.json.JSONObject
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.SkinLabStandard
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import kotlin.math.floor
import kotlin.math.roundToInt

/** Draw the exact generated Skin Lab path; all coordinates use the scoped design density. */
class StandardPopupPreview(context: Context, private val theme: Theme) : View(context) {
    private val density = resources.displayMetrics.density
    private val entries = JSONObject(context.assets.open("system_themes/daylight_porcelain/popup-geometry.json").bufferedReader().use { it.readText() })
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var label = ""
    private var origin = PointF()
    init { setLayerType(LAYER_TYPE_SOFTWARE, null); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun configure(text: String, left: Float, top: Float) { label = text.substringAfterLast('\n').uppercase(); origin = PointF(left / density, top / density); invalidate() }
    override fun onDraw(canvas: Canvas) {
        val data = entries.optJSONObject((if (SkinLabStandard.landscape(context)) "l" else "p") + label) ?: return
        val path = PathParser.createPathFromPathData(data.getString("path")) ?: return
        val box = data.getJSONArray("label"); val body = data.getJSONArray("body")
        canvas.save(); canvas.scale(density, density); canvas.translate(origin.x, origin.y)
        paint.color = Color.WHITE; paint.style = Paint.Style.FILL
        val layer = canvas.saveLayer(-200f, -200f, 1000f, 500f, null)
        paint.setShadowLayer(6f, 0f, 3f, 0x4d2d3039); canvas.drawPath(path, paint); paint.clearShadowLayer()
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); canvas.drawPath(path, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        paint.shader = LinearGradient(0f, body.getDouble(1).toFloat(), 0f, (body.getDouble(1) + body.getDouble(3) * .7).toFloat(), Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(-200f, -200f, 1000f, 500f, paint)
        paint.shader = null; paint.xfermode = null; canvas.restoreToCount(layer)
        paint.color = if (theme.isDark) theme.popupBackgroundColor else Color.WHITE
        canvas.drawPath(path, paint)
        paint.color = if (theme.isDark) theme.popupTextColor else Color.BLACK; paint.textSize = 28f; paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val bounds = Rect(); paint.getTextBounds(label, 0, label.length, bounds)
        val x = (box.getDouble(0) + box.getDouble(2) / 2).toFloat()
        val y = (box.getDouble(1) + box.getDouble(3) / 2).toFloat()
        canvas.drawText(label, x - bounds.exactCenterX(), y - bounds.exactCenterY(), paint)
        canvas.restore()
    }
}

/** A single horizontal, detached menu. Selection and commits stay in the existing popup owner. */
class StandardPopupMenu(
    ctx: Context, theme: Theme, outer: Rect, trigger: Rect,
    dismiss: PopupContainerUi.() -> Unit,
    private val keys: Array<String>, private val labels: Array<String>, initial: Int
) : PopupContainerUi(ctx, theme, outer, trigger, dismiss) {
    private val density = ctx.resources.displayMetrics.density
    private val designWidth = minOf(outer.width() / density - 4, keys.size * 34f + 8)
    private val cell = (designWidth - 8) / keys.size
    private val shadowPad = 10f
    private var selected = initial.coerceIn(0, keys.lastIndex)
    override val offsetX = ((trigger.centerX() - designWidth * density / 2).coerceIn(outer.left + 2 * density, outer.right - (designWidth + 2) * density) - trigger.left - shadowPad * density).roundToInt()
    override val offsetY = ((SkinLabStandard.gapY(ctx) / 2 + .875f - 6 - 50 - shadowPad) * density).roundToInt()
    override val root = object : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
        override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(((designWidth + shadowPad * 2) * density).roundToInt(), ((50 + shadowPad * 2) * density).roundToInt()) }
        override fun onDraw(canvas: Canvas) {
            canvas.save(); canvas.scale(density, density); canvas.translate(shadowPad, shadowPad)
            paint.color = if (theme.isDark) theme.popupBackgroundColor else Color.WHITE; paint.setShadowLayer(6f, 0f, 3f, 0x4d2d3039)
            canvas.drawRoundRect(0f, 0f, designWidth, 50f, 10f, 10f, paint); paint.clearShadowLayer()
            labels.forEachIndexed { i, text ->
                val x = 4 + cell * i
                if (i == selected) { paint.color = 0xff007aff.toInt(); canvas.drawRoundRect(x, 4f, x + cell, 46f, 7f, 7f, paint) }
                paint.color = if (i == selected) Color.WHITE else if (theme.isDark) theme.popupTextColor else Color.BLACK; paint.textSize = minOf(25f, cell * .82f)
                val bounds = Rect(); paint.getTextBounds(text, 0, text.length, bounds)
                canvas.drawText(text, x + cell / 2 - bounds.exactCenterX(), 25 - bounds.exactCenterY(), paint)
            }; canvas.restore()
        }
    }
    override fun onChangeFocus(x: Float, y: Float): Boolean {
        selected = floor((x / density - shadowPad - 4) / cell).toInt().coerceIn(0, keys.lastIndex); root.invalidate(); return false
    }
    override fun onTrigger(): KeyAction = KeyAction.FcitxKeyAction(keys[selected])
}
