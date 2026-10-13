package org.fcitx.fcitx5.android.input.bar.ui.idle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.SkinLabStandard
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui

/** Skin-matched, self-contained panel occupying the existing preedit + toolbar region. */
class ClipboardQuickReplyUi(override val ctx: Context, theme: Theme) : Ui {
    private val custom = theme as? Theme.Custom
    private val artwork = custom?.toolbarImage?.toDrawable(ctx)
    private val surface = artwork?.let { drawable ->
        val bitmap = Bitmap.createBitmap(ctx.dp(320), ctx.dp(58), Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        val center = ColorUtils.compositeColors(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2), opaque(theme.keyBackgroundColor))
        bitmap.recycle()
        center
    } ?: opaque(theme.keyBackgroundColor)
    private val fieldColor = ColorUtils.blendARGB(surface, opaque(theme.backgroundColor), .22f)
    private val ink = contrastTone(theme.keyTextColor, fieldColor)
    private val buttonColor = contrastTone(
        theme.accentKeyBackgroundColor.takeIf { Color.alpha(it) == 255 } ?: theme.genericActiveBackgroundColor,
        Color.WHITE, 4.7)

    val paste = TextView(ctx).apply {
        textSize = 14f
        setTextColor(ink)
        gravity = Gravity.CENTER_VERTICAL
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
        isClickable = true
        isFocusable = true
        setPadding(ctx.dp(9), 0, ctx.dp(8), 0)
    }
    val reply = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        contentDescription = "智能回复"
        isClickable = true
        isFocusable = true
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(buttonColor, ColorUtils.blendARGB(buttonColor, Color.BLACK, .1f))).apply {
            cornerRadius = ctx.dp(5f)
        }
        addView(TextView(ctx).apply {
            text = "智能回复"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_quick_reply_spark)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(ctx.dp(16), ctx.dp(16)).apply { leftMargin = ctx.dp(4) })
    }
    private val panel = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = artwork ?: GradientDrawable().apply { setColor(surface); cornerRadius = ctx.dp(7f) }
        // Bitmap/nine-slice padding must never shift the content or the button insets.
        setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6))
        addView(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(fieldColor)
                cornerRadius = ctx.dp(7f)
                setStroke(ctx.dp(.45f).toInt().coerceAtLeast(1), ColorUtils.blendARGB(surface, ink, .2f))
            }
            setPadding(0, 0, 0, 0)
            addView(paste, LinearLayout.LayoutParams(0, -1, 1f))
            // A single integer inset controls all three gaps, including density rounding.
            addView(reply, LinearLayout.LayoutParams(ctx.dp(92), -1).apply {
                val inset = ctx.dp(BUTTON_INSET_DP)
                topMargin = inset
                bottomMargin = inset
                rightMargin = inset
            })
        }, LinearLayout.LayoutParams(-1, -1))
    }
    override val root = FrameLayout(ctx).apply {
        val style = custom?.renderStyle
        val inset = if (style?.skinLabStandard == true) {
            SkinLabStandard.gapX(ctx) / 2
        } else style?.keyVisualHorizontalInsetDp ?: 0f
        addView(panel, FrameLayout.LayoutParams(-1, -1).apply {
            leftMargin = ctx.dp(inset).toInt()
            rightMargin = leftMargin
            topMargin = ctx.dp(1)
            bottomMargin = ctx.dp(1)
        })
    }

    fun setReplyEnabled(enabled: Boolean) {
        reply.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun opaque(color: Int) = ColorUtils.setAlphaComponent(color, 255)
    private fun contrastTone(color: Int, background: Int, target: Double = 4.5): Int {
        val source = opaque(color)
        if (ColorUtils.calculateContrast(source, background) >= target) return source
        val end = if (ColorUtils.calculateContrast(Color.WHITE, background) > ColorUtils.calculateContrast(Color.BLACK, background)) Color.WHITE else Color.BLACK
        for (step in 1..100) {
            val next = ColorUtils.blendARGB(source, end, step / 100f)
            if (ColorUtils.calculateContrast(next, background) >= target) return next
        }
        return end
    }

    companion object { const val BUTTON_INSET_DP = 3 }
}
