/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
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
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
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

    val bordered: Boolean
    val borderStroke: Boolean
    val rippled: Boolean
    val radius: Float
    val hMargin: Int
    val vMargin: Int
    protected val skinStyle = (theme as? Theme.Custom)?.renderStyle
    private val visualHInset get() = if (skinStyle?.skinLabStandard == true && def.margin) dp(org.fcitx.fcitx5.android.data.theme.SkinLabStandard.gapX(context) / 2).roundToInt() else if (def.margin) (skinStyle?.keyVisualHorizontalInsetDpByLayout?.get(skinAddress.layout) ?: skinStyle?.keyVisualHorizontalInsetDp)
        ?.let { dp(it.coerceIn(0f, 24f)).roundToInt() } ?: hMargin else 0
    private val visualVInset get() = if (skinStyle?.skinLabStandard == true && def.margin) dp(org.fcitx.fcitx5.android.data.theme.SkinLabStandard.gapY(context) / 2).roundToInt() else if (def.margin) (skinStyle?.keyVisualVerticalInsetDpByLayout?.get(skinAddress.layout) ?: skinStyle?.keyVisualVerticalInsetDp)
        ?.let { dp(it.coerceIn(0f, 24f)).roundToInt() } ?: vMargin else 0
    protected val hidePrintedHint get() = skinStyle?.digitHintsOnly == true &&
        def is KeyDef.Appearance.AltText && !def.altText.all { it.isDigit() }

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
    private val skinForegroundSize = skinKeyIds.firstNotNullOfOrNull { skinStyle?.keyForegroundSizes?.get(it) }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Release before the action can replace this keyboard (language/number switching).
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            var host = parent
            while (host != null && host !is BaseKeyboard) host = host.parent
            (host as? BaseKeyboard)?.onSkinKeyRelease(this)
        }
        val handled = super.onTouchEvent(event)
        if (skinStyle?.skinLabStandard != true && skinStyle?.keyPressScale?.let { it < 1f } == true) {
            val down = handled && isEnabled && event.actionMasked == MotionEvent.ACTION_DOWN
            if (down || event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                val scale = if (down) skinStyle.keyPressScale else 1f
                appearanceView.scaleX = scale
                appearanceView.scaleY = scale
            }
        }
        if (handled && isEnabled && event.actionMasked == MotionEvent.ACTION_DOWN) {
            var host = parent
            while (host != null && host !is BaseKeyboard) host = host.parent
            (host as? BaseKeyboard)?.onSkinKeyPress(this)
        }
        return handled
    }

    internal fun skinBoundsIn(host: BaseKeyboard): RectF {
        val rect = Rect(0, 0, appearanceView.width, appearanceView.height)
        host.offsetDescendantRectToMyCoords(appearanceView, rect)
        return RectF(rect).apply { inset(hMargin.toFloat(), vMargin.toFloat()) }
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
    private val skinForeground = (theme as? Theme.Custom)?.let { custom ->
        skinKeyIds.firstNotNullOfOrNull { custom.keyForegrounds[it]?.toDrawable(context) }
    }?.let { drawable ->
        imageView {
            setImageDrawable(drawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }
    }

    fun setSkinState(state: String) {
        val foreground = skinForeground ?: return
        val custom = theme as? Theme.Custom ?: return
        val ids = skinKeyIds.map { "$it@$state" } + skinKeyIds
        val drawable = ids.firstNotNullOfOrNull { custom.keyForegrounds[it]?.toDrawable(context) } ?: return
        foreground.setImageDrawable(drawable)
    }

    protected fun keyTypeface(original: Typeface?, textStyle: Int): Typeface =
        skinStyle?.keyFontFamily?.let { Typeface.create(it, Typeface.NORMAL) }
            ?: Typeface.create(original, textStyle)

    override fun onDetachedFromWindow() {
        appearanceView.scaleX = 1f
        appearanceView.scaleY = 1f
        super.onDetachedFromWindow()
    }

    protected fun contentBounds(): RectF {
        val inset = skinStyle?.keyContentInsets
        // Shared sidebar children do not each own a complete cap; do not subtract its base four times.
        val shared = skinStyle?.unifiedSidebar == true && def.border == Border.Off && !def.margin
        val density = resources.displayMetrics.density
        fun px(value: Float?) = if (shared) 0f else (value ?: 0f).coerceIn(0f, 24f) * density
        val l = (visualHInset + px(inset?.leftDp)).coerceAtMost(appearanceView.width / 2f)
        val t = (visualVInset + px(inset?.topDp)).coerceAtMost(appearanceView.height / 2f)
        val r = (appearanceView.width - visualHInset - px(inset?.rightDp)).coerceAtLeast(l)
        val b = (appearanceView.height - visualVInset - px(inset?.bottomDp)).coerceAtLeast(t)
        return RectF(l, t, r, b)
    }

    protected fun placeContent(view: View, centerY: Float = .5f) {
        val face = contentBounds()
        view.translationX = face.centerX() - (view.left + view.width / 2f)
        view.translationY = face.top + face.height() * centerY - (view.top + view.height / 2f)
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
        themedBackgroundDrawable(resolveImageRole())?.let { mask ->
            mask.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            return mask
        }
        return if (bordered) insetRadiusDrawable(hMargin, vMargin, radius, color)
        else InsetDrawable(ColorDrawable(color), hMargin, vMargin, hMargin, vMargin)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        appearanceView.alpha = if (enabled) 1f else styledFloat(android.R.attr.disabledAlpha)
    }

    fun updateBounds() {
        val (x, y) = cachedLocation.also { appearanceView.getLocationInWindow(it) }
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
        skinForeground?.let { foreground ->
            // Alpha preserves layout and dynamic label updates without displaying text over the artwork.
            for (index in 0 until appearanceView.childCount) {
                val child = appearanceView.getChildAt(index)
                if (child !== foreground && child !is SkinAmbientView) child.alpha = 0f
            }
            foreground.bringToFront()
            val face = contentBounds()
            val scale = min(1f, min(face.width() / foreground.width.coerceAtLeast(1), face.height() / foreground.height.coerceAtLeast(1)))
            foreground.scaleX = scale
            foreground.scaleY = scale
            placeContent(foreground)
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

    private fun themedBackgroundDrawable(role: KeyImageRole): Drawable? {
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
        return insetDrawable(drawable, visualHInset, visualVInset)
    }
}

@SuppressLint("ViewConstructor")
open class TextKeyView(ctx: Context, theme: Theme, def: KeyDef.Appearance.Text, skinAddress: SkinKeyAddress = SkinKeyAddress()) :
    KeyView(ctx, theme, def, skinAddress) {
    val mainText = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        background = null
        text = def.displayText
        val labelSize = skinStyle?.functionKeyTextSizeDp?.takeIf { def.isFunctionLabel }?.coerceIn(8f, 24f)
            ?: (def.textSize * (skinStyle?.keyTextScaleByLayout?.get(skinAddress.layout) ?: skinStyle?.keyTextScale ?: 1f).coerceIn(0.4f, 1.5f))
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, labelSize)
        centerGlyphs = skinStyle?.centerKeyGlyphs == true
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        // keep original typeface, apply textStyle only
        typeface = keyTypeface(typeface, skinStyle?.keyTextBold?.let { if (it) Typeface.BOLD else Typeface.NORMAL } ?: def.textStyle)
        setTextColor(
            when {
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
        appearanceView.apply {
            add(mainText, lParams(wrapContent, wrapContent) {
                centerInParent()
            })
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val center = if (hidePrintedHint) .5f else if (def is KeyDef.Appearance.AltText) skinStyle?.keyLabelWithHintCenterY
            ?: skinStyle?.keyLabelCenterY else skinStyle?.keyLabelCenterY ?: if (skinStyle?.keyContentInsets != null) .5f else null
        // ImageText has its own icon/text pair; preserve that semantic arrangement.
        if (center != null && def !is KeyDef.Appearance.ImageText) {
            placeContent(mainText, center.coerceIn(0.15f, 0.85f))
        }
    }
}

@SuppressLint("ViewConstructor")
class AltTextKeyView(ctx: Context, theme: Theme, def: KeyDef.Appearance.AltText, skinAddress: SkinKeyAddress = SkinKeyAddress()) :
    TextKeyView(ctx, theme, def, skinAddress) {
    val altText = view(::AutoScaleTextView) {
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
                Variant.Normal -> theme.candidateCommentColor
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
        skinStyle?.keyHintCenterY?.let { center ->
            placeContent(altText, center.coerceIn(0.1f, 0.9f))
        }
    }
}

@SuppressLint("ViewConstructor")
class ImageKeyView(ctx: Context, theme: Theme, def: KeyDef.Appearance.Image, skinAddress: SkinKeyAddress = SkinKeyAddress()) :
    KeyView(ctx, theme, def, skinAddress) {
    val img = imageView { configure(theme, def.src, def.variant); scaleType = ImageView.ScaleType.FIT_CENTER }

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
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, stackedDef.topTextSize * (skinStyle?.keyTextScaleByLayout?.get(skinAddress.layout) ?: skinStyle?.keyTextScale ?: 1f).coerceIn(0.4f, 1.5f))
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
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, stackedDef.bottomTextSize * (skinStyle?.keyTextScaleByLayout?.get(skinAddress.layout) ?: skinStyle?.keyTextScale ?: 1f).coerceIn(0.4f, 1.5f))
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
    }
}
