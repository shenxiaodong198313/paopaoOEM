package org.fcitx.fcitx5.android.input.favorite

import android.animation.ValueAnimator
import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import splitties.dimensions.dp

internal class FavoriteSendModeToggleView(
    context: Context,
) : FrameLayout(context) {

    private companion object {
        private const val SHELL_WIDTH_DP = 52
        private const val SHELL_HEIGHT_DP = 26
        private const val TRACK_WIDTH_DP = 46
        private const val TRACK_HEIGHT_DP = 20
        private const val THUMB_SIZE_DP = 15
        private const val THUMB_AUTO_START_DP = 5
        private const val THUMB_MANUAL_START_DP = 32
        private const val LABEL_TEXT_SIZE_SP = 11f
        private const val LABEL_SIDE_PADDING_DP = 6
        private const val ANIMATION_DURATION_MS = 220L
    }

    private val colorEvaluator = ArgbEvaluator()
    private val manualStartColor = Color.parseColor("#F2F3FB")
    private val manualEndColor = Color.parseColor("#B8C0D4")
    private val autoStartColor = Color.parseColor("#36FFDA")
    private val autoEndColor = Color.parseColor("#E2FF63")

    private val outerShell = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = context.dp(SHELL_HEIGHT_DP / 2).toFloat()
        }
        clipToPadding = false
        clipChildren = false
    }

    private val trackView = View(context).apply {
        background = trackDrawable(manualStartColor, manualEndColor)
    }

    private val thumbView = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
    }

    private val stateLabel = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = LABEL_TEXT_SIZE_SP
        typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        paint.isFakeBoldText = true
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        text = "手动"
    }

    private var checked = false
    private var onCheckedChange: ((Boolean) -> Unit)? = null
    private var progress = 0f
    private var uncheckedLabel: String = "手动"
    private var checkedLabel: String = "自动"

    init {
        clipChildren = false
        clipToPadding = false
        isClickable = true
        isFocusable = true

        addView(
            outerShell,
            LayoutParams(
                context.dp(SHELL_WIDTH_DP),
                context.dp(SHELL_HEIGHT_DP),
                Gravity.CENTER
            )
        )

        outerShell.addView(
            trackView,
            FrameLayout.LayoutParams(
                context.dp(TRACK_WIDTH_DP),
                context.dp(TRACK_HEIGHT_DP),
                Gravity.CENTER
            )
        )

        outerShell.addView(
            stateLabel,
            FrameLayout.LayoutParams(
                context.dp(TRACK_WIDTH_DP),
                context.dp(TRACK_HEIGHT_DP),
                Gravity.CENTER
            )
        )

        outerShell.addView(
            thumbView,
            FrameLayout.LayoutParams(
                context.dp(THUMB_SIZE_DP),
                context.dp(THUMB_SIZE_DP),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        )

        setOnClickListener { toggle(true) }

        post { applyProgress(if (checked) 1f else 0f) }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        isClickable = enabled
        isFocusable = enabled
        alpha = if (enabled) 1f else 0.45f
    }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        onCheckedChange = listener
    }

    fun toggle(animate: Boolean = true) {
        if (!isEnabled) return
        setChecked(!checked, animate, notify = true)
    }

    fun renderChecked(enabled: Boolean, animate: Boolean = false) {
        setChecked(enabled, animate, notify = false)
    }

    fun isChecked(): Boolean = checked

    fun setLabels(
        unchecked: String,
        checked: String,
    ) {
        uncheckedLabel = unchecked
        checkedLabel = checked
        applyProgress(progress)
    }

    private fun setChecked(enabled: Boolean, animate: Boolean, notify: Boolean) {
        if (checked == enabled && !animate) return
        checked = enabled
        if (animate) {
            animateTo(if (enabled) 1f else 0f)
        } else {
            applyProgress(if (enabled) 1f else 0f)
        }
        if (notify) {
            onCheckedChange?.invoke(enabled)
        }
    }

    private fun animateTo(target: Float) {
        if (progress == target) return
        ValueAnimator.ofFloat(progress, target).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                applyProgress(animator.animatedValue as Float)
            }
            start()
        }
    }

    private fun applyProgress(value: Float) {
        progress = value
        val manualStart = context.dp(THUMB_MANUAL_START_DP).toFloat()
        val autoStart = context.dp(THUMB_AUTO_START_DP).toFloat()
        thumbView.translationX = manualStart + ((autoStart - manualStart) * value)
        val startColor = colorEvaluator.evaluate(value, manualStartColor, autoStartColor) as Int
        val endColor = colorEvaluator.evaluate(value, manualEndColor, autoEndColor) as Int
        trackView.background = trackDrawable(startColor, endColor)
        val autoEnabled = value >= 0.5f
        stateLabel.text = if (autoEnabled) checkedLabel else uncheckedLabel
        stateLabel.gravity = if (autoEnabled) {
            Gravity.END or Gravity.CENTER_VERTICAL
        } else {
            Gravity.START or Gravity.CENTER_VERTICAL
        }
        stateLabel.setPadding(
            context.dp(LABEL_SIDE_PADDING_DP),
            0,
            context.dp(LABEL_SIDE_PADDING_DP),
            0
        )
    }

    private fun trackDrawable(startColor: Int, endColor: Int): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(startColor, endColor)
        ).apply {
            cornerRadius = context.dp(TRACK_HEIGHT_DP / 2).toFloat()
        }
    }
}
