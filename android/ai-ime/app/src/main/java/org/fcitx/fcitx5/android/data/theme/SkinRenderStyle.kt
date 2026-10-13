package org.fcitx.fcitx5.android.data.theme

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/** Optional, package-owned rendering parameters. Defaults preserve pre-2.5 themes. */
@Serializable
@Parcelize
data class SkinRenderStyle(
    val skinLabStandard: Boolean = false,
    // Optional system family; no font files or global keyboard preferences are changed.
    val keyFontFamily: String? = null,
    val keyPressScale: Float = 1f,
    val ambientSpaceEffect: SkinAmbientEffect? = null,
    val ambientSidebarEffect: SkinAmbientEffect? = null,
    val equalToolbarSlots: Boolean = false,
    val toolbarShortcutLimit: Int = 5,
    val clipKeyImagesToOutline: Boolean = true,
    val keyTextScale: Float = 1f,
    val functionKeyTextSizeDp: Float? = null,
    val keyHintTextScale: Float = 1f,
    val keyLabelCenterY: Float? = null,
    val keyLabelWithHintCenterY: Float? = null,
    val keyHintCenterY: Float? = null,
    val centerKeyGlyphs: Boolean = false,
    val toolbarHeightDp: Int = 60,
    val preeditOutsideBar: Boolean = false,
    val preeditHeightDp: Int = 18,
    val toolbarIconTint: Int? = null,
    val toolbarIconPaddingDp: Int? = null,
    val toolbarButtonBackground: Boolean = true,
    val unifiedSidebar: Boolean = false,
    val gridKeyVerticalCompensation: Boolean = true,
    val keyContentInsets: SkinContentInsets? = null,
    val keyIconSizeDp: Int? = null,
    val keyForegroundWidthDp: Int = 32,
    val keyForegroundHeightDp: Int = 12,
    val toolbarIconSizeDp: Int? = null,
    val keyPressEffect: SkinKeyPressEffect? = null,
    val layout: SkinLayoutStyle? = null,
    val keyForegroundSizes: Map<String, SkinForegroundSize> = emptyMap(),
    val keyTextBold: Boolean? = null,
    val spaceKeyTextColor: Int? = null,
    val sidebarTextColor: Int? = null,
    val keyTextScaleByLayout: Map<String, Float> = emptyMap(),
    val uppercaseLatinLabels: Boolean = false,
    val digitHintsOnly: Boolean = false,
    val alignToolbarToKeyFaces: Boolean = false,
    // Appearance only: the outer KeyView and sidebar touch grid retain their standard bounds.
    val keyVisualHorizontalInsetDp: Float? = null,
    val keyVisualVerticalInsetDp: Float? = null,
    val keyVisualHorizontalInsetDpByLayout: Map<String, Float> = emptyMap(),
    val keyVisualVerticalInsetDpByLayout: Map<String, Float> = emptyMap(),
    val toolbarHeightDpByLayout: Map<String, Int> = emptyMap(),
    // Limited to the existing QWERTY bottom row; the space key absorbs the remainder.
    val qwertyBottomRowWidths: Map<String, Float> = emptyMap()
) : Parcelable {
    init {
        require(keyFontFamily == null || keyFontFamily in setOf("sans-serif", "sans-serif-medium", "sans-serif-light", "sans-serif-rounded"))
        require(keyPressScale.isFinite() && keyPressScale in .94f..1f)
        require(toolbarShortcutLimit in 0..6)
        listOf(keyVisualHorizontalInsetDpByLayout, keyVisualVerticalInsetDpByLayout).forEach { map ->
            require(map.keys.all { it in setOf("qwerty", "t9", "number") })
            require(map.values.all { it.isFinite() && it in 0f..24f })
        }
        require(toolbarHeightDpByLayout.keys.all { it in setOf("qwerty", "t9", "number") })
        require(toolbarHeightDpByLayout.values.all { it in 36..80 })
        require(qwertyBottomRowWidths.keys.all { it in setOf("role:number_mode", "role:language", "role:enter", "qwerty/r4c2", "qwerty/r4c5") })
        require(qwertyBottomRowWidths.values.all { it.isFinite() && it in .08f.. .24f })
    }
    val toolbarArtworkSizeDp: Int? get() = toolbarIconSizeDp?.let { if (it >= 40) it * 7 / 10 else it }

    fun barBodyHeight(layout: String): Int = toolbarHeightDpByLayout[layout] ?: barBodyHeightDp
    val reservedPreeditDp: Int get() = if (preeditOutsideBar) preeditHeightDp.coerceIn(16, 32) else 0
    val barBodyHeightDp: Int get() = toolbarHeightDp.coerceIn(36, 80)
    val barTotalHeightDp: Int get() = reservedPreeditDp + barBodyHeightDp
}

@Serializable
@Parcelize
data class SkinForegroundSize(val widthDp: Int = 32, val heightDp: Int = 12) : Parcelable {
    init { require(widthDp in 8..96 && heightDp in 4..48) }
}

/** Optional slow, clipped particles above a space-key surface, below its label. */
@Serializable
@Parcelize
data class SkinAmbientEffect(
    val color: Int = -6226081,
    val particleCount: Int = 7,
    val cycleMs: Int = 12000,
    val opacity: Float = .22f,
    val radiusDp: Float = .65f,
    val insetHorizontalDp: Float = 12f,
    val insetVerticalFraction: Float = .22f
) : Parcelable {
    init {
        require(particleCount in 1..16 && cycleMs in 6000..30000)
        require(opacity.isFinite() && opacity in 0f.. .5f)
        require(radiusDp.isFinite() && radiusDp in .2f..2f)
        require(insetHorizontalDp.isFinite() && insetHorizontalDp in 0f..24f)
        require(insetVerticalFraction.isFinite() && insetVerticalFraction in .1f.. .4f)
    }
}

/** Package-owned overrides. Null fields keep the user's existing global preferences. */
@Serializable
@Parcelize
data class SkinLayoutStyle(
    val keyHorizontalMarginDp: Float? = null,
    val keyVerticalMarginDp: Float? = null,
    val keyHorizontalMarginLandscapeDp: Float? = null,
    val keyVerticalMarginLandscapeDp: Float? = null,
    val keyboardHeightDp: Int? = null,
    val keyboardHeightLandscapeDp: Int? = null,
    val sidePaddingDp: Int? = null,
    val sidePaddingLandscapeDp: Int? = null,
    val bottomPaddingDp: Int? = null,
    val bottomPaddingLandscapeDp: Int? = null,
    val keyRadiusDp: Float? = null,
    val keyBorder: Boolean? = null,
    val keyBorderStroke: Boolean? = null,
    val keyRippleEffect: Boolean? = null,
    val punctuationPosition: String? = null
) : Parcelable {
    init {
        listOf(keyHorizontalMarginDp, keyVerticalMarginDp, keyHorizontalMarginLandscapeDp,
            keyVerticalMarginLandscapeDp, keyRadiusDp).filterNotNull().forEach { require(it.isFinite() && it in 0f..24f) }
        listOf(keyboardHeightDp, keyboardHeightLandscapeDp).filterNotNull().forEach { require(it in 120..480) }
        listOf(sidePaddingDp, sidePaddingLandscapeDp, bottomPaddingDp, bottomPaddingLandscapeDp)
            .filterNotNull().forEach { require(it in 0..100) }
        require(punctuationPosition == null || punctuationPosition in setOf("TopCenter", "TopRight", "Bottom", "None"))
    }

    fun horizontalMargin(landscape: Boolean): Float? =
        if (landscape) keyHorizontalMarginLandscapeDp else keyHorizontalMarginDp
    fun verticalMargin(landscape: Boolean): Float? =
        if (landscape) keyVerticalMarginLandscapeDp else keyVerticalMarginDp
    fun keyboardHeight(landscape: Boolean): Int? =
        if (landscape) keyboardHeightLandscapeDp else keyboardHeightDp
    fun sidePadding(landscape: Boolean): Int? =
        if (landscape) sidePaddingLandscapeDp else sidePaddingDp
    fun bottomPadding(landscape: Boolean): Int? =
        if (landscape) bottomPaddingLandscapeDp else bottomPaddingDp
}

/** Optional under-key effect. Values and palette belong to the skin, never a named theme. */
@Serializable
@Parcelize
data class SkinKeyPressEffect(
    val colors: List<Int> = listOf(-1),
    val durationMs: Int = 700,
    val radiusDp: Float = 88f,
    val glowAlpha: Float = .65f,
    val particleCount: Int = 16,
    val particleSizeDp: Float = 1.5f,
    val travelDp: Float = 28f,
    val particleShape: String = "spark",
    val maxBursts: Int = 6,
    val releaseDurationMs: Int = 0,
    val rippleCount: Int = 0,
    val rippleStyle: String = "rings",
    val rippleWidthDp: Float = 1f,
    val rippleAlpha: Float = .6f,
    val motion: String = "expand",
    val speed: Float = 1f,
    val rotationTurns: Float = 1f,
    val turbulenceDp: Float = 8f,
    val gravityDp: Float = 40f,
    val trailSegments: Int = 0,
    val particleAlpha: Float = 1f,
    val digitSizeDp: Float = 9f,
    val followWindowMs: Int = 450,
    val fragmentCount: Int = 32,
    val fragmentMinRadians: Float = .045f,
    val fragmentMaxRadians: Float = .525f,
    val fragmentSpread: Float = .11f,
    val waveDistortion: Float = .027f
) : Parcelable {
    init {
        require(colors.size in 1..4)
        require(durationMs in 180..1600)
        require(radiusDp in 24f..180f && glowAlpha in 0f..1f)
        require(particleCount in 0..32 && particleSizeDp in .5f..4f)
        require(travelDp in 4f..80f && particleShape in setOf("dot", "spark", "mixed", "digit"))
        require(motion in setOf("expand", "weave", "follow", "orbit", "fountain", "nebula"))
        require(speed.isFinite() && speed in .25f..3f)
        require(rotationTurns.isFinite() && rotationTurns in -3f..3f)
        require(turbulenceDp.isFinite() && turbulenceDp in 0f..24f)
        require(gravityDp.isFinite() && gravityDp in -120f..120f)
        require(trailSegments in 0..4 && particleAlpha.isFinite() && particleAlpha in 0f..1f)
        require(digitSizeDp.isFinite() && digitSizeDp in 6f..20f)
        require(followWindowMs in 80..1200 && fragmentCount in 4..48)
        require(fragmentMinRadians.isFinite() && fragmentMinRadians in .01f..1f)
        require(fragmentMaxRadians.isFinite() && fragmentMaxRadians in fragmentMinRadians..1f)
        require(fragmentSpread.isFinite() && fragmentSpread in 0f..0.3f)
        require(waveDistortion.isFinite() && waveDistortion in 0f..0.12f)
        require(rippleCount in 0..4)
        require(rippleStyle in setOf("rings", "water"))
        require(rippleWidthDp.isFinite() && rippleWidthDp in .5f..3f)
        require(rippleAlpha.isFinite() && rippleAlpha in 0f..1f)
        require(maxBursts in 1..8)
        require(releaseDurationMs == 0 || releaseDurationMs in 180..1600)
    }
}

/** Insets inside the background bounds, excluding the key's existing outer margins. */
@Serializable
@Parcelize
data class SkinContentInsets(
    val leftDp: Float = 0f,
    val topDp: Float = 0f,
    val rightDp: Float = 0f,
    val bottomDp: Float = 0f
) : Parcelable

/** Source-pixel caps and source density make rounded edges independent of key aspect ratio. */
@Serializable
@Parcelize
data class SkinImageSlice(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val sourceDensityDpi: Int = 160,
    val tileCenter: Boolean = false
) : Parcelable

/** Optional tiled material below an authored transparent aperture in a sliced image. */
@Serializable
@Parcelize
data class SkinPatternFill(
    val image: Theme.Custom.CustomBackground,
    val sourceDensityDpi: Int = 160,
    val leftDp: Float = 0f,
    val topDp: Float = 0f,
    val rightDp: Float = 0f,
    val bottomDp: Float = 0f,
    val cornerRadiusDp: Float = 0f
) : Parcelable
