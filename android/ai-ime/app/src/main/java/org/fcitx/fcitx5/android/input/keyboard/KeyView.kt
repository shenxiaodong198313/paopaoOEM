/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.os.Build
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.View
import android.view.MotionEvent
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.SkinInkFitDrawable
import org.fcitx.fcitx5.android.data.theme.SkinAspectDrawable
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs.PunctuationPosition
import org.fcitx.fcitx5.android.data.theme.Theme.KeyImageRole
import org.fcitx.fcitx5.android.input.AutoScaleTextView
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.utils.styledFloat
import org.fcitx.fcitx5.android.utils.unset
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.parentId
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.existingOrNewId
import splitties.views.imageResource
import splitties.views.padding
import kotlin.math.min
import kotlin.math.roundToInt

abstract class KeyView(ctx: Context, val theme: Theme, val def: KeyDef.Appearance, val skinAddress: SkinKeyAddress = SkinKeyAddress()) :
    CustomGestureView(ctx) {

    protected fun applySkinTextShadow(text: TextView) {
        skinStyle?.keyTextShadow?.let { shadow ->
            val density = resources.displayMetrics.density
            text.setShadowLayer(shadow.radiusDp * density, shadow.dxDp * density,
                shadow.dyDp * density, shadow.color)
        }
    }

    val bordered: Boolean
    val borderStroke: Boolean
    val rippled: Boolean
    val radius: Float
    val hMargin: Int
    val vMargin: Int
    protected val skinStyle = (theme as? Theme.Custom)?.renderStyle
    private val visualHInset get() = if (skinStyle?.skinLabStandard == true && skinStyle?.customKeyFaceInsets != true && def.margin) dp(org.fcitx.fcitx5.android.data.theme.SkinLabStandard.gapX(context) / 2).roundToInt() else if (def.margin) (skinStyle?.keyVisualHorizontalInsetDpByLayout?.get(skinAddress.layout) ?: skinStyle?.keyVisualHorizontalInsetDp)
        ?.let { dp(it.coerceIn(0f, 24f)).roundToInt() } ?: hMargin else 0
    private val compactRowScale get() = if (skinStyle?.skinLabStandard == true)
        SkinLayoutFit.compactScale(appearanceView.height.toFloat(),
            dp(org.fcitx.fcitx5.android.data.theme.SkinLabStandard.grid(context, skinStyle) / 4).toFloat()) else 1f
    private val visualVInset get(): Int {
        val authored = if (skinStyle?.skinLabStandard == true && skinStyle?.customKeyFaceInsets != true && def.margin)
            dp(org.fcitx.fcitx5.android.data.theme.SkinLabStandard.gapY(context) / 2).roundToInt()
        else if (def.margin) (skinStyle?.keyVisualVerticalInsetDpByLayout?.get(skinAddress.layout) ?: skinStyle?.keyVisualVerticalInsetDp)
            ?.let { dp(it.coerceIn(0f, 24f)).roundToInt() } ?: vMargin else 0
        return (authored * compactRowScale).roundToInt()
    }
    protected val hidePrintedHint get() = def is KeyDef.Appearance.AltText && (
        skinKeyIds.any { it in (skinStyle?.hiddenKeyHints ?: emptySet()) } ||
        skinStyle?.digitHintsOnly == true && !def.altText.all { it.isDigit() } ||
            org.fcitx.fcitx5.android.data.theme.SkinConfiguration.hidesDigitHint(
                skinStyle?.localConfiguration, def.displayText, def.altText, !def.isFunctionLabel || "t9/r1c2" in skinKeyIds, skinStyle?.defaultDigitHints ?: true))

    /** Stable skin identifiers, independent of current uppercase state and changing candidates. */
    val skinKeyId: String = when (def.viewId) {
        R.id.button_caps -> "role:shift"
        R.id.button_backspace -> "role:backspace"
        R.id.button_number_mode -> "role:number_mode"
        R.id.button_lang -> "role:language"
        R.id.button_space -> "role:space"
        R.id.button_return -> "role:enter"
        else -> when {
            def is KeyDef.Appearance.AltText && def.displayText.length > 1 && def.altText.length == 1 && def.altText in "1".."9" -> "t9:${def.altText}"
            def is KeyDef.Appearance.Text && def.displayText.length == 1 && def.displayText[0].isLetter() -> "letter:${def.displayText.lowercase(java.util.Locale.ROOT)}"
            def is KeyDef.Appearance.Text && def.displayText.length == 1 && def.displayText[0].isDigit() -> "digit:${def.displayText}"
            def is KeyDef.Appearance.Text -> "text:${def.displayText}"
            else -> "role:${def.variant.name.lowercase(java.util.Locale.ROOT)}"
        }
    }

    val skinKeyIds: List<String> = skinAddress.candidates(skinKeyId)
    protected val authoredMainCenterY get() = skinKeyIds.firstNotNullOfOrNull { skinStyle?.keyLabelCenterYByKey?.get(it) }
    protected val authoredHintCenterY get() = skinKeyIds.firstNotNullOfOrNull { skinStyle?.keyHintCenterYByKey?.get(it) } ?: skinStyle?.keyHintCenterY
    private val skinForegroundSize = skinKeyIds.firstNotNullOfOrNull { skinStyle?.keyForegroundSizes?.get(it) }

    private val hasCharacterAnimation get() = skinKeyIds.any { (theme as? Theme.Custom)?.keyAnimations?.containsKey(it) == true }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Release before the action can replace this keyboard (language/number switching).
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            var host = parent
            while (host != null && host !is BaseKeyboard) host = host.parent
            (host as? BaseKeyboard)?.onSkinKeyRelease(this)
        }
        val handled = super.onTouchEvent(event)
        if (hasCharacterAnimation && event.actionMasked == MotionEvent.ACTION_CANCEL) appearanceView.background?.jumpToCurrentState()
        val authoredMotion = skinStyle?.keyMotion
        if (!hasCharacterAnimation && (authoredMotion?.enabled ?: ((skinStyle?.skinLabStandard != true || skinStyle?.customKeyFaceInsets == true) && skinStyle?.keyPressScale?.let { it < 1f } == true))) {
            val down = handled && isEnabled && event.actionMasked == MotionEvent.ACTION_DOWN
            if (down || event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                animateSkinPress(down, event.actionMasked == MotionEvent.ACTION_CANCEL)
            }
        }
        if (handled && isEnabled && event.actionMasked == MotionEvent.ACTION_DOWN) {
            var host = parent
            while (host != null && host !is BaseKeyboard) host = host.parent
            (host as? BaseKeyboard)?.onSkinKeyPress(this)
        }
        return handled
    }

    private fun animateSkinPress(down: Boolean, cancel: Boolean = false) {
        appearanceView.animate().cancel()
        if (cancel || (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) {
            appearanceView.scaleX = 1f
            appearanceView.scaleY = 1f
            appearanceView.translationY = 0f
            if (cancel) appearanceView.background?.jumpToCurrentState()
            return
        }
        val motion = skinStyle?.keyMotion?.takeIf { it.enabled }
        val scaleX = if (down) motion?.scaleX ?: skinStyle?.keyPressScale ?: 1f else 1f
        val scaleY = if (down) motion?.scaleY ?: skinStyle?.keyPressScale ?: 1f else 1f
        // Only artwork moves. The outer gesture view and popup geometry remain fixed.
        appearanceView.animate().scaleX(scaleX).scaleY(scaleY)
            .translationY(if (down) dp(motion?.sinkDp ?: 0f) else 0f)
            .setDuration(if (down) motion?.pressMs?.toLong() ?: 70L else motion?.releaseMs?.toLong() ?: 180L)
            .setInterpolator(if (down) DecelerateInterpolator() else OvershootInterpolator(motion?.bounce ?: .55f))
            .start()
    }

    internal fun skinBoundsIn(host: BaseKeyboard): RectF {
        val rect = Rect(appearanceView.left, appearanceView.top, appearanceView.right, appearanceView.bottom)
        host.offsetDescendantRectToMyCoords(this, rect)
        return RectF(rect).apply { inset(hMargin.toFloat(), vMargin.toFloat()) }
    }

    internal val sharedSidebarSlot: Boolean get() =
        skinStyle?.unifiedSidebar == true && def.border == Border.Off && !def.margin

    internal val paintsSkinCap: Boolean get() = appearanceView.background != null

    private var restFace: SkinFaceScaleDrawable? = null
    private var restArt: Drawable? = null

    internal fun skinShadowBoundsIn(host: BaseKeyboard): RectF {
        val rect = Rect(appearanceView.left, appearanceView.top, appearanceView.right, appearanceView.bottom)
        host.offsetDescendantRectToMyCoords(this, rect)
        val art = restArt
        if (art != null) restFace?.faceBounds(art, appearanceView.width, appearanceView.height)?.let {
            it.offset(rect.left.toFloat(), rect.top.toFloat())
            return it
        }
        return RectF(rect).apply { inset(visualHInset.toFloat(), visualVInset.toFloat()) }
    }

    init {
        val prefs = ThemeManager.prefs
        val layout = skinStyle?.layout
        bordered = layout?.keyBorder ?: prefs.keyBorder.getValue()
        borderStroke = layout?.keyBorderStroke ?: prefs.keyBorderStroke.getValue()
        rippled = layout?.keyRippleEffect ?: prefs.keyRippleEffect.getValue()
        radius = dp(layout?.keyRadiusDp ?: prefs.keyRadius.getValue().toFloat())
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hMarginPref =
            if (landscape) prefs.keyHorizontalMarginLandscape else prefs.keyHorizontalMargin
        val vMarginPref =
            if (landscape) prefs.keyVerticalMarginLandscape else prefs.keyVerticalMargin
        hMargin = if (def.margin) layout?.horizontalMargin(landscape)?.let { dp(it).roundToInt() } ?: dp(hMarginPref.getValue()) else 0
        vMargin = if (def.margin) layout?.verticalMargin(landscape)?.let { dp(it).roundToInt() } ?: dp(vMarginPref.getValue()) else 0
    }

    private val cachedLocation = intArrayOf(0, 0)
    private val cachedBounds = Rect()
    private var boundsValid = false
    val bounds: Rect
        get() = cachedBounds.also {
            if (!boundsValid) updateBounds()
        }

    /**
     * KeyView content left margin, in percentage of parent width
     */
    @FloatRange(0.0, 1.0)
    var layoutMarginLeft = 0f

    /**
     * KeyView content right margin, in percentage of parent width
     */
    @FloatRange(0.0, 1.0)
    var layoutMarginRight = 0f

    /**
     * [KeyView] contains 2 parts: `TouchEventView` and `AppearanceView`.
     *
     * `TouchEventView` is the outer [CustomGestureView] that handles touch events.
     *
     * `AppearanceView` in the inner [ConstraintLayout], it can be smaller than its parent,
     * and holds the [bounds] for popup.
     */
    protected val appearanceView = constraintLayout {
        // sync any state from parent
        isDuplicateParentStateEnabled = true
    }

    // Content-only replacement: the original KeyView remains the touch/action/accessibility owner.
    // Latin keycaps display capitals independently of the case sent to the editor.
    // A @label stamp replaces only that letter. Numeric hints keep their own view.
    private val stampedLetter = skinKeyId.startsWith("letter:") &&
        (theme as? Theme.Custom)?.keyForegrounds?.containsKey("$skinKeyId@label") == true &&
        skinStyle?.localConfiguration?.let { it.font != "default" || it.italic || !it.bold ||
            it.keyTextColor != 0 || !it.textShadowEnabled } != true
    private var skinState = when {
        stampedLetter -> "label"
        def.viewId == R.id.button_return -> "return"
        else -> "off"
    }
    private val replacedChildAlpha = java.util.WeakHashMap<View, Float>()
    private val skinForeground = (theme as? Theme.Custom)?.let { custom ->
        if (skinStyle?.skinLabStandard == true && skinAddress.layout == "number" && skinKeyId == "role:enter") null
        else if (skinKeyIds.any { id -> custom.keyForegrounds.keys.any { it == id || it.startsWith("$id@") } }) imageView {
            val ids = skinKeyIds.map { "$it@$skinState" } + skinKeyIds
            setImageDrawable(ids.firstNotNullOfOrNull { custom.keyForegrounds[it]?.toDrawable(context) })
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        } else null
    }

    fun setSkinState(state: String) {
        skinState = state
        val foreground = skinForeground ?: return
        val custom = theme as? Theme.Custom ?: return
        val ids = skinKeyIds.map { "$it@$state" } + skinKeyIds
        val drawable = ids.firstNotNullOfOrNull { custom.keyForegrounds[it]?.toDrawable(context) }
        foreground.setImageDrawable(drawable)
        updateSkinForegroundVisibility()
    }

    private fun updateSkinForegroundVisibility() {
        val foreground = skinForeground ?: return
        val replacing = foreground.drawable != null
        foreground.visibility = if (replacing) View.VISIBLE else View.INVISIBLE
        for (index in 0 until appearanceView.childCount) {
            val child = appearanceView.getChildAt(index)
            if (child === foreground || child is SkinAmbientView) continue
            if (stampedLetter && child === (this as? AltTextKeyView)?.altText) continue
            if (replacing) {
                if (!replacedChildAlpha.containsKey(child)) replacedChildAlpha[child] = child.alpha
                child.alpha = 0f
            } else {
                replacedChildAlpha.remove(child)?.let { child.alpha = it }
            }
        }
    }

    protected fun keyTypeface(original: Typeface?, textStyle: Int): Typeface {
        skinStyle?.localConfiguration?.let { config ->
            if (config.font != "default" && (skinKeyId.startsWith("letter:") || skinKeyId.startsWith("t9:"))) {
                return SkinConfigurationTypeface.get(context, config.font, config.bold, config.italic)
            }
            if (config.font == "default" && (skinKeyId.startsWith("letter:") || skinKeyId.startsWith("t9:"))) {
                val style = (if (config.bold) Typeface.BOLD else Typeface.NORMAL) or (if (config.italic) Typeface.ITALIC else 0)
                return skinStyle.keyFontFamily?.let { Typeface.create(it, style) } ?: Typeface.create(original, style)
            }
        }
        return skinStyle?.keyFontFamily?.let {
            if (it == "serif") SkinSerifTypeface.get(context, skinStyle?.keyTextBold == true)
            else Typeface.create(it, Typeface.NORMAL)
        }
            ?: Typeface.create(original, textStyle)
    }

    internal fun cancelSkinFeedback() {
        // A retained IME window may hide without detaching or losing window focus.
        // Clear the owner state too, otherwise duplicate-parent state can restore the pose.
        isPressed = false
        appearanceView.isPressed = false
        animateSkinPress(false, cancel = true)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) cancelSkinFeedback()
    }

    override fun onDetachedFromWindow() {
        cancelSkinFeedback()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) cancelSkinFeedback()
    }

    protected fun contentBounds(): RectF {
        val inset = skinStyle?.keyContentInsets
        // Shared sidebar children do not each own a complete cap; do not subtract its base four times.
        val shared = skinStyle?.unifiedSidebar == true && def.border == Border.Off && !def.margin
        val density = resources.displayMetrics.density
        fun px(value: Float?) = if (shared) 0f else (value ?: 0f).coerceIn(0f, 24f) * density
        val verticalScale = if (skinStyle?.skinLabStandard == true)
            min(compactRowScale, (appearanceView.height - 2 * visualVInset).coerceAtLeast(0) /
                (2f * (px(inset?.topDp) + px(inset?.bottomDp))).coerceAtLeast(1f)) else 1f
        val l = (visualHInset + px(inset?.leftDp)).coerceAtMost(appearanceView.width / 2f)
        val t = (visualVInset + px(inset?.topDp) * verticalScale).coerceAtMost(appearanceView.height / 2f)
        val r = (appearanceView.width - visualHInset - px(inset?.rightDp)).coerceAtLeast(l)
        val b = (appearanceView.height - visualVInset - px(inset?.bottomDp) * verticalScale).coerceAtLeast(t)
        return RectF(l, t, r, b)
    }

    protected fun fitTextContent(first: TextView, firstY: Float, second: TextView? = null, secondY: Float = .5f) {
        if (skinStyle?.skinLabStandard != true) return
        val face = contentBounds()
        val centerX = skinKeyIds.firstNotNullOfOrNull { skinStyle?.keyLabelCenterXByKey?.get(it) } ?: .5f
        fun lineHeight(text: TextView) = text.paint.fontMetrics.let { it.bottom - it.top }
        fun fit(text: TextView, center: Float) = SkinLayoutFit.textScale(face.width(), face.height(),
            centerX, center, text.paint.measureText(text.text.toString()), lineHeight(text))
        var scale = fit(first, firstY)
        if (second != null && second.visibility == VISIBLE) {
            scale = min(scale, min(fit(second, secondY),
                SkinLayoutFit.pairScale(face.height(), firstY, secondY, lineHeight(first), lineHeight(second))))
            second.scaleX = scale; second.scaleY = scale
            placeContent(second, secondY)
        }
        first.scaleX = scale; first.scaleY = scale
        placeContent(first, firstY)
    }

    protected fun placeContent(view: View, centerY: Float = .5f) {
        val face = contentBounds()
        val centerX = skinKeyIds.firstNotNullOfOrNull { skinStyle?.keyLabelCenterXByKey?.get(it) } ?: .5f
        view.translationX = face.left + face.width() * centerX - (view.left + view.width / 2f)
        view.translationY = face.top + face.height() * centerY - (view.top + view.height / 2f)
    }

    protected fun placeNumericHint(view: TextView, main: TextView, desiredCenter: Float) {
        if (skinStyle?.skinLabStandard != true) return
        val face = contentBounds()
        val capHeight = (appearanceView.height - 2 * visualVInset).coerceAtLeast(0)
        val sizeInset = capHeight * (1f - ((skinStyle?.configuredKeyHeightPercent ?: 100) / 100f).coerceAtMost(1f)) / 2f
        val ink = Rect(); view.paint.getTextBounds(view.text.toString(), 0, view.text.length, ink)
        val mainInk = Rect(); main.paint.getTextBounds(main.text.toString(), 0, main.text.length, mainInk)
        val stamp = skinForeground?.takeIf { stampedLetter && it.drawable != null }
        val mainInkTop = stamp?.let { it.y + it.height * (1f - it.scaleY) / 2f }
            ?: (main.y + main.pivotY + (main.baseline + mainInk.top - main.pivotY) * main.scaleY)
        val top = face.top + sizeInset + dp(2f)
        val bottom = minOf(face.bottom - sizeInset - dp(2f), mainInkTop - dp(2f)).coerceAtLeast(top + 1f)
        // Moving a small digit must not resize the main key label. Fit only its own ink.
        val scale = minOf(1f, face.width() / ink.width().coerceAtLeast(1),
            (bottom - top) / ink.height().coerceAtLeast(1)).coerceAtLeast(.01f)
        view.scaleX = scale; view.scaleY = scale
        val half = ink.height() * scale / 2f
        val target = (face.top + face.height() * desiredCenter).coerceIn(top + half, maxOf(top + half, bottom - half))
        placeContent(view, desiredCenter)
        view.translationY = target - view.top - view.pivotY - (view.baseline + ink.exactCenterY() - view.pivotY) * scale
    }

    protected fun fitImageContent(image: ImageView, centerY: Float = .5f, text: TextView? = null, textY: Float = .76f) {
        if (skinStyle?.skinLabStandard != true) return
        val face = contentBounds()
        val centerX = skinKeyIds.firstNotNullOfOrNull { skinStyle?.keyLabelCenterXByKey?.get(it) } ?: .5f
        var scale = SkinLayoutFit.textScale(face.width(), face.height(), centerX, centerY,
            image.width.toFloat(), image.height.toFloat())
        if (text != null) {
            val line = text.paint.fontMetrics.let { it.bottom - it.top }
            scale = min(scale, min(SkinLayoutFit.textScale(face.width(), face.height(), centerX, textY,
                text.paint.measureText(text.text.toString()), line),
                SkinLayoutFit.pairScale(face.height(), centerY, textY, image.height.toFloat(), line)))
            text.scaleX = scale; text.scaleY = scale
            placeContent(text, textY)
        }
        image.scaleX = scale; image.scaleY = scale
        placeContent(image, centerY)
    }

    private val roundedOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val right = (view.width - hMargin).coerceAtLeast(hMargin + 1)
            val bottom = (view.height - vMargin).coerceAtLeast(vMargin + 1)
            outline.setRoundRect(hMargin, vMargin, right, bottom, radius)
        }
    }

    init {
        // trigger setEnabled(true)
        isEnabled = true
        isClickable = true
        isHapticFeedbackEnabled = false
        if (def.viewId > 0) {
            id = def.viewId
        }
        val roleDrawable = themedBackgroundDrawable(resolveImageRole())
        // key border
        if ((bordered && def.border != Border.Off) || def.border == Border.On) {
            val borderOrShadowWidth = dp(1)
            appearanceView.background = roleDrawable ?: run {
                val bkgColor = when (def.variant) {
                    Variant.Normal, Variant.AltForeground -> theme.keyBackgroundColor
                    Variant.Alternative -> theme.altKeyBackgroundColor
                    Variant.Accent -> theme.accentKeyBackgroundColor
                }
                if (borderStroke) borderedKeyBackgroundDrawable(
                    bkgColor, theme.keyShadowColor,
                    radius, borderOrShadowWidth, hMargin, vMargin
                ) else shadowedKeyBackgroundDrawable(
                    bkgColor, theme.keyShadowColor,
                    radius, borderOrShadowWidth, hMargin, vMargin
                )
            }
            if (roleDrawable != null) {
                // InsetDrawable propagates padding to the container; clear it so themed
                // key images do not shrink the text/icon content area a second time.
                appearanceView.padding = 0
            }
            applyRoundedClip(roleDrawable != null)
            // foreground: press highlight or ripple
            setupPressHighlight()
        } else {
            if (roleDrawable != null && def.border != Border.Special) {
                appearanceView.background = roleDrawable
                appearanceView.padding = 0
                applyRoundedClip(true)
            } else {
                applyRoundedClip(false)
            }
            // normal press highlight for keys without special background
            // special background is handled in `onSizeChanged()`
            if (def.border != Border.Special) {
                setupPressHighlight()
            }
        }
        skinStyle?.ambientSpaceEffect?.takeIf { skinKeyId == "role:space" }?.let { effect ->
            appearanceView.addView(SkinAmbientView(context, effect), 0,
                android.view.ViewGroup.LayoutParams(-1, -1))
        }
        skinForeground?.let { foreground ->
            appearanceView.add(foreground, appearanceView.lParams(
                dp((skinForegroundSize?.widthDp ?: skinStyle?.keyForegroundWidthDp ?: 32).coerceIn(8, 96)),
                dp((skinForegroundSize?.heightDp ?: skinStyle?.keyForegroundHeightDp ?: 12).coerceIn(4, 48))
            ) { centerInParent() })
        }
        add(appearanceView, lParams(matchParent, matchParent))
    }

    private fun setupPressHighlight(mask: Drawable? = null) {
        appearanceView.foreground = if (rippled) {
            RippleDrawable(
                ColorStateList.valueOf(theme.keyPressHighlightColor), null,
                // ripple should be masked with an opaque color
                mask ?: highlightMaskDrawable(Color.WHITE)
            )
        } else if (bordered && borderStroke && themedBackgroundDrawable(resolveImageRole()) == null) {
            StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    borderedKeyBackgroundDrawable(
                        Color.TRANSPARENT, theme.keyShadowColor,
                        radius, dp(2), hMargin, vMargin
                    )
                )
            }
        } else {
            StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    // use mask drawable as highlight directly
                    mask ?: highlightMaskDrawable(theme.keyPressHighlightColor)
                )
            }
        }
    }

    private fun highlightMaskDrawable(@ColorInt color: Int): Drawable {
        // Use a separate copy of the actual key artwork: its alpha, nine-slice
        // corners and visual insets must also define the pressed surface.
        themedBackgroundDrawable(resolveImageRole(), includeStates = false)?.let { mask ->
            mask.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            return mask
        }
        return if (bordered) insetRadiusDrawable(hMargin, vMargin, radius, color)
        else InsetDrawable(ColorDrawable(color), hMargin, vMargin, hMargin, vMargin)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) animateSkinPress(false, cancel = true)
        appearanceView.alpha = if (enabled) 1f else styledFloat(android.R.attr.disabledAlpha)
    }

    fun updateBounds() {
        val location = cachedLocation.also { getLocationInWindow(it) }
        val x = location[0] + appearanceView.left
        val y = location[1] + appearanceView.top
        cachedBounds.set(x, y, x + appearanceView.width, y + appearanceView.height)
        boundsValid = true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        boundsValid = false
        if (layoutMarginLeft != 0f || layoutMarginRight != 0f) {
            val w = right - left
            val h = bottom - top
            val layoutWidth = (w * (1f - layoutMarginLeft - layoutMarginRight)).roundToInt()
            appearanceView.updateLayoutParams<LayoutParams> {
                leftMargin = (w * layoutMarginLeft).roundToInt()
                rightMargin = (w * layoutMarginRight).roundToInt()
            }
            // sets `measuredWidth` and `measuredHeight` of `AppearanceView`
            // https://developer.android.com/guide/topics/ui/how-android-draws#measure
            appearanceView.measure(
                MeasureSpec.makeMeasureSpec(layoutWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
            )
        }
        super.onLayout(changed, left, top, right, bottom)
        if (changed && skinStyle?.skinLabStandard == true) {
            // The first drawable was created before measurement. Rebind its authored
            // insets to the actual row height without changing the outer hit rectangle.
            themedBackgroundDrawable(resolveImageRole())?.let {
                appearanceView.background = it
                appearanceView.padding = 0
                setupPressHighlight()
            }
        }
        updateSkinForegroundVisibility()
        skinForeground?.let { foreground ->
            foreground.bringToFront()
            val face = contentBounds()
            val requested = if (stampedLetter) (skinStyle?.localConfiguration?.keyTextScale ?: 100) / 100f else 1f
            val hasHint = stampedLetter && !hidePrintedHint && (this as? AltTextKeyView)?.altText?.visibility == VISIBLE
            val center = if (!stampedLetter) .5f else authoredMainCenterY ?: (if (hasHint) skinStyle?.keyLabelWithHintCenterY else null)
                ?: skinStyle?.keyLabelCenterY ?: .5f
            val height = face.height() * 2f * min(center, 1f - center)
            val scale = min(requested, min(face.width() / foreground.width.coerceAtLeast(1), height / foreground.height.coerceAtLeast(1)))
            foreground.scaleX = scale
            foreground.scaleY = scale
            placeContent(foreground, center)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (bordered) return
        when (def.viewId) {
            R.id.button_space -> {
                themedBackgroundDrawable(KeyImageRole.Space)?.let { drawable ->
                    appearanceView.background = drawable
                    appearanceView.padding = 0
                    applyRoundedClip(true)
                    setupPressHighlight()
                    return
                }
                val bkgRadius = dp(3f)
                val minHeight = dp(26)
                val hInset = dp(10)
                val vInset = if (h < minHeight) 0 else min((h - minHeight) / 2, dp(16))
                appearanceView.background = insetRadiusDrawable(
                    hInset, vInset, bkgRadius, theme.spaceBarColor
                )
                // InsetDrawable sets padding to container view; remove padding to prevent text from bing clipped
                appearanceView.padding = 0
                // apply press highlight for background area
                setupPressHighlight(
                    insetRadiusDrawable(
                        hInset, vInset, bkgRadius,
                        if (rippled) Color.WHITE else theme.keyPressHighlightColor
                    )
                )
            }
            R.id.button_return -> {
                themedBackgroundDrawable(KeyImageRole.Enter)?.let { drawable ->
                    appearanceView.background = drawable
                    appearanceView.padding = 0
                    applyRoundedClip(true)
                    setupPressHighlight()
                    return
                }
                val drawableSize = min(min(w, h), dp(35))
                val hInset = (w - drawableSize) / 2
                val vInset = (h - drawableSize) / 2
                appearanceView.background = insetOvalDrawable(
                    hInset, vInset, theme.accentKeyBackgroundColor
                )
                appearanceView.padding = 0
                setupPressHighlight(
                    insetOvalDrawable(
                        hInset, vInset, if (rippled) Color.WHITE else theme.keyPressHighlightColor
                    )
                )
            }
        }
    }

    private fun resolveImageRole(): KeyImageRole {
        return when (def.viewId) {
            R.id.button_caps -> KeyImageRole.Shift
            R.id.button_backspace -> KeyImageRole.Backspace
            R.id.button_number_mode -> KeyImageRole.NumberMode
            R.id.button_lang -> KeyImageRole.Language
            R.id.button_space -> KeyImageRole.Space
            R.id.button_return -> KeyImageRole.Enter
            else -> when (def.variant) {
                Variant.Normal -> KeyImageRole.Normal
                Variant.AltForeground, Variant.Alternative -> KeyImageRole.Alternative
                Variant.Accent -> KeyImageRole.Enter
            }
        }
    }

    private fun applyRoundedClip(enabled: Boolean) {
        appearanceView.outlineProvider = roundedOutlineProvider
        appearanceView.clipToOutline = enabled && radius > 0f && skinStyle?.clipKeyImagesToOutline != false
    }

    private fun themedBackgroundDrawable(role: KeyImageRole, includeStates: Boolean = true): Drawable? {
        val customTheme = theme as? Theme.Custom ?: return null
        // Border.Off/margin=false denotes a clickable slot in an already painted sidebar.
        if (skinStyle?.unifiedSidebar == true && def.border == Border.Off && !def.margin) return null
        val drawable = customTheme.keyDrawable(role, skinKeyIds, context) { color ->
            GradientDrawable().apply {
                cornerRadius = radius
                setColor(color)
                if (borderStroke) setStroke(dp(1), theme.keyShadowColor)
            }
        } ?: return null
        val outset = dp(skinStyle?.keyImageHorizontalOutsetDp ?: 0f).roundToInt()
        val aspect = skinStyle?.authoredKeyAspectRatio(skinKeyIds)
        // Expand only the authored image and its identical press mask. Text and hit bounds stay fixed.
        val faceInsets = skinKeyIds.firstNotNullOfOrNull { skinStyle?.keyFaceInsetsByKey?.get(it) }
        fun configured(drawable: Drawable): Drawable = skinStyle?.let {
            SkinFaceScaleDrawable(drawable, it.configuredKeyWidthPercent / 100f, it.configuredKeyHeightPercent / 100f, visualHInset, visualVInset, dp(1f))
        } ?: drawable
        fun fit(image: Drawable): Drawable {
            val fitted = when {
                skinAddress.layout == "t9" && skinStyle?.t9AlignVisibleColumns == true ->
                    SkinInkFitDrawable(image, horizontalOnly = true)
                aspect != null -> SkinAspectDrawable(image, aspect)
                else -> image
            }
            if (faceInsets != null) return configured(InsetDrawable(fitted,
                visualHInset - outset + dp(faceInsets.leftDp).roundToInt(),
                visualVInset + (dp(faceInsets.topDp) * compactRowScale).roundToInt(),
                visualHInset - outset + dp(faceInsets.rightDp).roundToInt(),
                visualVInset + (dp(faceInsets.bottomDp) * compactRowScale).roundToInt()))
            return configured(if (outset > 0) InsetDrawable(fitted, visualHInset - outset, visualVInset,
                visualHInset - outset, visualVInset) else insetDrawable(fitted, visualHInset, visualVInset))
        }
        val rest = fit(drawable)
        if (includeStates) {
            restFace = rest as? SkinFaceScaleDrawable
            restArt = drawable
            val animation = skinKeyIds.firstNotNullOfOrNull { customTheme.keyAnimations[it] }
            if (animation != null) {
                val frames = animation.frames.mapNotNull { it.toDrawable(context)?.let { frame ->
                    // Full-face storyboards share the cap bounds at every keyboard height.
                    // Isolated character sprites retain their authored aspect and placement.
                    if (animation.fillsKeyFace) fit(frame)
                    else fit(SkinKeyActorFrameDrawable(frame, animation.left, animation.top, animation.right, animation.bottom))
                } }
                if (frames.size == animation.frames.size) return SkinKeySequenceDrawable(rest, frames,
                    animation.frameDurationsMs.toIntArray(),
                    skinStyle?.keyMotion?.enabled != false)
            }
        }
        if (includeStates && (skinStyle?.keyMotion?.enabled != false || customTheme.minimumSkinEngine >= 9)) {
            val states = skinKeyIds.firstNotNullOfOrNull { customTheme.keyStateImages[it] }
            val pressed = states?.pressed?.toDrawable(context)
            if (pressed != null) return SkinKeyStateDrawable(rest, fit(pressed),
                states.released?.toDrawable(context)?.let(::fit), skinStyle?.keyMotion?.releaseMs ?: 380)
        }
        return rest
    }
}

@SuppressLint("ViewConstructor")
open class TextKeyView(ctx: Context, theme: Theme, def: KeyDef.Appearance.Text, skinAddress: SkinKeyAddress = SkinKeyAddress()) :
    KeyView(ctx, theme, def, skinAddress) {
    val mainText = view(::AutoScaleTextView) {
        applySkinTextShadow(this)
        isClickable = false
        isFocusable = false
        background = null
        text = def.displayText
        val labelSize = skinStyle?.functionKeyTextSizeDp?.takeIf { def.isFunctionLabel }?.coerceIn(8f, 24f)
            ?: (def.textSize * (skinStyle?.keyTextScaleByLayout?.get(skinAddress.layout) ?: skinStyle?.keyTextScale ?: 1f).coerceIn(0.4f, 1.5f))
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, labelSize * (skinStyle?.localConfiguration?.keyTextScale ?: 100) / 100f)
        centerGlyphs = skinStyle?.centerKeyGlyphs == true
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        // keep original typeface, apply textStyle only
        typeface = keyTypeface(typeface, skinStyle?.keyTextBold?.let { if (it) Typeface.BOLD else Typeface.NORMAL } ?: def.textStyle)
        setTextColor(
            when {
                skinStyle?.skinLabStandard == true && skinAddress.layout == "number" && skinAddress.position == "r4c5" -> if (theme.isDark) Color.WHITE else 0xff222735.toInt()
                skinAddress.layout == "qwerty" && skinAddress.position == "r4c2" && skinStyle?.punctuationKeyTextColor != null -> skinStyle.punctuationKeyTextColor
                def.viewId == R.id.button_lang && skinStyle?.languageKeyTextColor != null -> skinStyle.languageKeyTextColor
                def.viewId == R.id.button_space -> skinStyle?.spaceKeyTextColor ?: theme.altKeyTextColor
                def.border == Border.Off && !def.margin && skinStyle?.sidebarTextColor != null -> skinStyle.sidebarTextColor!!
                else -> when (def.variant) {
                    Variant.Normal -> theme.keyTextColor
                    Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
                    Variant.Accent -> theme.accentKeyTextColor
                }
            }
        )
    }

    init {
        skinStyle?.authoredKeyTextColor(skinKeyIds)?.let(mainText::setTextColor)
        skinStyle?.localConfiguration?.keyTextColor?.takeIf { it != 0 }?.let(mainText::setTextColor)
        appearanceView.apply {
            add(mainText, lParams(wrapContent, wrapContent) {
                centerInParent()
            })
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val center = authoredMainCenterY ?: if (hidePrintedHint) skinStyle?.keyLabelCenterY ?: .5f else if (def is KeyDef.Appearance.AltText) skinStyle?.keyLabelWithHintCenterY
            ?: skinStyle?.keyLabelCenterY else skinStyle?.keyLabelCenterY ?: if (skinStyle?.keyContentInsets != null) .5f else null
        // ImageText has its own icon/text pair; preserve that semantic arrangement.
        if (center != null && def !is KeyDef.Appearance.ImageText) {
            placeContent(mainText, center.coerceIn(0.15f, 0.85f))
        }
        if (def !is KeyDef.Appearance.ImageText) fitTextContent(mainText, (center ?: .5f).coerceIn(.15f, .85f))
    }
}

@SuppressLint("ViewConstructor")
class AltTextKeyView(ctx: Context, theme: Theme, def: KeyDef.Appearance.AltText, skinAddress: SkinKeyAddress = SkinKeyAddress()) :
    TextKeyView(ctx, theme, def, skinAddress) {
    val altText = view(::AutoScaleTextView) {
        applySkinTextShadow(this)
        isClickable = false
        isFocusable = false
        // TODO hardcoded alt text size
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.666667f * (skinStyle?.keyHintTextScale ?: 1f).coerceIn(0.4f, 1.5f))
        centerGlyphs = skinStyle?.centerKeyGlyphs == true
        typeface = keyTypeface(typeface, if (skinStyle?.keyTextBold == false) Typeface.NORMAL else Typeface.BOLD)
        text = def.altText
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        setTextColor(
            when (def.variant) {
                Variant.Normal -> if (skinStyle?.skinLabStandard == true) {
                    (mainText.currentTextColor and 0x00ffffff) or (0xb3 shl 24)
                } else theme.candidateCommentColor
                Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
                Variant.Accent -> theme.accentKeyTextColor
            }
        )
    }

    init {
        appearanceView.apply {
            add(altText, lParams(wrapContent, wrapContent))
        }
        applyLayout(resources.configuration.orientation)
    }

    private fun applyTopRightAltTextPosition() {
        mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            // reset
            topMargin = 0
            bottomToTop = unset
            // set
            topToTop = parentId
            bottomToBottom = parentId
        }
        altText.visibility = View.VISIBLE
        altText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            // reset
            bottomToBottom = unset; bottomMargin = 0
            // set
            topToTop = parentId; topMargin = vMargin
            leftToLeft = unset
            rightToRight = parentId; rightMargin = hMargin + dp(4)
        }
    }

    private fun applyTopCenterAltTextPosition() {
        mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            // reset
            topMargin = 0
            bottomToTop = unset
            // set
            topToTop = parentId
            bottomToBottom = parentId
        }
        altText.visibility = View.VISIBLE
        altText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            // reset
            bottomToBottom = unset; bottomMargin = 0
            rightMargin = 0
            // set
            topToTop = parentId; topMargin = vMargin
            leftToLeft = parentId
            rightToRight = parentId
        }
    }

    private fun applyBottomAltTextPosition() {
        mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            // reset
            bottomToBottom = unset
            // set
            topToTop = parentId; topMargin = vMargin
            bottomToTop = altText.existingOrNewId
        }
        altText.visibility = View.VISIBLE
        altText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            // reset
            topToTop = unset; topMargin = 0
            rightMargin = 0
            // set
            leftToLeft = parentId
            rightToRight = parentId
            bottomToBottom = parentId; bottomMargin = vMargin + dp(2)
        }
    }

    private fun applyNoAltTextPosition() {
        mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            // reset
            topMargin = 0
            bottomToTop = unset
            // set
            topToTop = parentId
            bottomToBottom = parentId
        }
        altText.visibility = View.GONE
    }

    private fun resolvedPunctuationPosition(): PunctuationPosition =
        skinStyle?.layout?.punctuationPosition?.let(PunctuationPosition::valueOf)
            ?: ThemeManager.prefs.punctuationPosition.getValue()

    private fun applyLayout(orientation: Int) {
        if (hidePrintedHint) {
            applyNoAltTextPosition()
            return
        }
        if (skinStyle?.skinLabStandard == true && authoredHintCenterY != null) {
            // Authored centers position each measured label independently. The old
            // bottom-hint chain can squeeze the main label to 0x0 before scaling.
            mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = 0; bottomMargin = 0; bottomToTop = unset
                topToTop = parentId; bottomToBottom = parentId
                leftToLeft = parentId; rightToRight = parentId
            }
            altText.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = 0; bottomMargin = 0; rightMargin = 0
                topToTop = parentId; bottomToBottom = parentId
                leftToLeft = parentId; rightToRight = parentId
            }
            altText.visibility = if (resolvedPunctuationPosition() == PunctuationPosition.None) View.GONE else View.VISIBLE
            return
        }
        when (resolvedPunctuationPosition()) {
            PunctuationPosition.Bottom -> when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> applyTopRightAltTextPosition()
                else -> applyBottomAltTextPosition()
            }
            PunctuationPosition.TopCenter -> applyTopCenterAltTextPosition()
            PunctuationPosition.TopRight -> applyTopRightAltTextPosition()
            PunctuationPosition.None -> applyNoAltTextPosition()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (resolvedPunctuationPosition() in setOf(
                PunctuationPosition.TopRight,
                PunctuationPosition.TopCenter
            )
        ) {
            return
        }
        applyLayout(newConfig.orientation)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val hintCenter = authoredHintCenterY
        hintCenter?.let { center ->
            placeContent(altText, center.coerceIn(0.1f, 0.9f))
        }
        if (!hidePrintedHint && altText.visibility == VISIBLE && hintCenter != null) {
            fitTextContent(mainText, (authoredMainCenterY ?: skinStyle?.keyLabelWithHintCenterY ?: skinStyle?.keyLabelCenterY ?: .5f).coerceIn(.15f, .85f))
            placeNumericHint(altText, mainText, hintCenter.coerceIn(.1f, .9f))
        }
    }
}

@SuppressLint("ViewConstructor")
class ImageKeyView(ctx: Context, theme: Theme, def: KeyDef.Appearance.Image, skinAddress: SkinKeyAddress = SkinKeyAddress()) :
    KeyView(ctx, theme, def, skinAddress) {
    val img = imageView {
        configure(theme, def.src, def.variant)
        if (def.viewId == R.id.button_lang) skinStyle?.languageKeyTextColor?.let {
            imageTintList = ColorStateList.valueOf(it)
        }
        if (skinStyle?.skinLabStandard == true && skinAddress.layout == "number" && skinAddress.position == "r4c1") {
            imageTintList = ColorStateList.valueOf(if (skinStyle?.numberReturnUsesFunctionKey == true) theme.altKeyTextColor else Color.WHITE)
        }
        skinStyle?.authoredKeyTextColor(skinKeyIds)?.let { imageTintList = ColorStateList.valueOf(it) }
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    fun updateReturnDrawable(@DrawableRes resource: Int) {
        // The shared skin keeps one return glyph; editor actions remain unchanged.
        img.imageResource = if (skinStyle?.skinLabStandard == true)
            R.drawable.ic_baseline_keyboard_return_24 else resource
        setSkinState(when (resource) {
            R.drawable.ic_baseline_search_24 -> "search"
            R.drawable.ic_baseline_send_24 -> "send"
            R.drawable.ic_baseline_done_24 -> "done"
            R.drawable.ic_baseline_keyboard_tab_24 -> "next"
            R.drawable.ic_baseline_keyboard_tab_reverse_24 -> "previous"
            R.drawable.ic_baseline_arrow_forward_24 -> "go"
            else -> "return"
        })
    }

    init {
        appearanceView.apply {
            add(img, lParams(skinStyle?.keyIconSizeDp?.let { dp(it.coerceIn(8, 40)) } ?: wrapContent, skinStyle?.keyIconSizeDp?.let { dp(it.coerceIn(8, 40)) } ?: wrapContent) {
                centerInParent()
            })
        }
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (skinStyle?.keyContentInsets != null) placeContent(img)
        fitImageContent(img)
    }
}

private fun ImageView.configure(theme: Theme, @DrawableRes src: Int, variant: Variant) = apply {
    isClickable = false
    isFocusable = false
    imageTintList = ColorStateList.valueOf(
        when (variant) {
            Variant.Normal -> theme.keyTextColor
            Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
            Variant.Accent -> theme.accentKeyTextColor
        }
    )
    imageResource = src
}

@SuppressLint("ViewConstructor")
class ImageTextKeyView(ctx: Context, theme: Theme, def: KeyDef.Appearance.ImageText, skinAddress: SkinKeyAddress = SkinKeyAddress()) :
    TextKeyView(ctx, theme, def, skinAddress) {
    val img = imageView {
        configure(theme, def.src, def.variant)
        if (def.viewId == R.id.button_lang) skinStyle?.languageKeyTextColor?.let {
            imageTintList = ColorStateList.valueOf(it)
        }
    }

    init {
        appearanceView.apply {
            add(img, lParams(dp(skinStyle?.keyIconSizeDp?.coerceIn(8, 40) ?: 13), dp(skinStyle?.keyIconSizeDp?.coerceIn(8, 40) ?: 13)))
        }
        mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            centerHorizontally()
            bottomToBottom = parentId
            bottomMargin = vMargin + dp(4)
            topToTop = unset
        }
        img.updateLayoutParams<ConstraintLayout.LayoutParams> {
            centerHorizontally()
            topToTop = parentId
        }
        updateMargins(resources.configuration.orientation)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (skinStyle?.keyContentInsets != null) {
            placeContent(img, .30f)
            placeContent(mainText, .76f)
        }
        fitImageContent(img, .30f, mainText, .76f)
    }

    private fun updateMargins(orientation: Int) {
        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    bottomMargin = vMargin + dp(2)
                }
                img.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topMargin = vMargin + dp(4)
                }
            }
            else -> {
                mainText.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    bottomMargin = vMargin + dp(4)
                }
                img.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topMargin = vMargin + dp(8)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        updateMargins(newConfig.orientation)
    }
}

@SuppressLint("ViewConstructor")
class StackedTextKeyView(
    ctx: Context,
    theme: Theme,
    val stackedDef: KeyDef.Appearance.StackedText,
    skinAddress: SkinKeyAddress = SkinKeyAddress()
) : KeyView(ctx, theme, stackedDef, skinAddress) {
    val topText = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        includeFontPadding = false
        text = if (skinStyle?.skinLabStandard == true) "◦" else stackedDef.topText
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, stackedDef.topTextSize * (skinStyle?.keyTextScaleByLayout?.get(skinAddress.layout) ?: skinStyle?.keyTextScale ?: 1f).coerceIn(0.4f, 1.5f) * (skinStyle?.localConfiguration?.keyTextScale ?: 100) / 100f)
        centerGlyphs = skinStyle?.centerKeyGlyphs == true
        typeface = keyTypeface(typeface, skinStyle?.keyTextBold?.let { if (it) Typeface.BOLD else Typeface.NORMAL } ?: stackedDef.textStyle)
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        setTextColor(
            when (stackedDef.variant) {
                Variant.Normal -> theme.keyTextColor
                Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
                Variant.Accent -> theme.accentKeyTextColor
            }
        )
    }

    val bottomText = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        includeFontPadding = false
        text = if (skinStyle?.skinLabStandard == true) "," else stackedDef.bottomText
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, stackedDef.bottomTextSize * (skinStyle?.keyTextScaleByLayout?.get(skinAddress.layout) ?: skinStyle?.keyTextScale ?: 1f).coerceIn(0.4f, 1.5f) * (skinStyle?.localConfiguration?.keyTextScale ?: 100) / 100f)
        centerGlyphs = skinStyle?.centerKeyGlyphs == true
        typeface = keyTypeface(typeface, skinStyle?.keyTextBold?.let { if (it) Typeface.BOLD else Typeface.NORMAL } ?: stackedDef.textStyle)
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        setTextColor(
            when (stackedDef.variant) {
                Variant.Normal -> theme.keyTextColor
                Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
                Variant.Accent -> theme.accentKeyTextColor
            }
        )
    }

    init {
        appearanceView.apply {
            add(topText, lParams(wrapContent, wrapContent))
            add(bottomText, lParams(wrapContent, wrapContent))
        }
        skinStyle?.localConfiguration?.keyTextColor?.takeIf { it != 0 }?.let { ink -> topText.setTextColor(ink); bottomText.setTextColor(ink) }
        applySkinTextShadow(topText)
        applySkinTextShadow(bottomText)
        topText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topToTop = parentId
            bottomToTop = bottomText.existingOrNewId
            leftToLeft = parentId
            rightToRight = parentId
            topMargin = vMargin + dp(4)
            bottomMargin = dp(1)
        }
        bottomText.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topToBottom = topText.existingOrNewId
            bottomToBottom = parentId
            leftToLeft = parentId
            rightToRight = parentId
            topMargin = dp(1)
            bottomMargin = vMargin + dp(4)
        }
        // Comma-like glyphs visually sink even with symmetric margins, so lift the bottom slot slightly more.
        bottomText.translationY = if (skinStyle?.skinLabStandard == true) 0f else -dp(6).toFloat()
        if (skinStyle?.skinLabStandard == true) { topText.alpha = .6f; topText.centerGlyphs = true; bottomText.centerGlyphs = true }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (skinStyle?.keyContentInsets != null) {
            placeContent(topText, if (skinStyle?.skinLabStandard == true) .28f else .30f)
            placeContent(bottomText, if (skinStyle?.skinLabStandard == true) .66f else .72f)
        }
        fitTextContent(topText, .28f, bottomText, .66f)
    }
}
