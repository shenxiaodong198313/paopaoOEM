package org.fcitx.fcitx5.android.data.theme

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/** Optional, package-owned rendering parameters. Defaults preserve pre-2.5 themes. */
@Serializable
@Parcelize
data class SkinRenderStyle(
    @kotlinx.serialization.Transient val suppressDefaultKeyEffect: Boolean = false,
    @kotlinx.serialization.Transient val localConfiguration: SkinConfiguration? = null,
    val skinLabStandard: Boolean = false,
    // Candidate-strip appearance only; generic active colors also serve menus/popups.
    // Missing values preserve every existing package's selected-candidate style.
    val candidateHighlightEnabled: Boolean = true,
    val numberReturnUsesFunctionKey: Boolean = false,
    // Opt-in artwork insets; the standard touch grid and keyboard height stay fixed.
    val customKeyFaceInsets: Boolean = false,
    // Authored toolbar geometry without disabling standard density, key layout or text fitting.
    // Opt-in capability; frozen clients retain their existing toolbar presentation.
    val customToolbarLayout: Boolean = false,
    // Optional authored surface bounds, independent of button slots. Requires
    // customToolbarLayout: "keyFaces" follows outer key-face edges; "fullWidth"
    // uses the available toolbar span. Explicit image insets still apply.
    // Null/unknown preserve legacy behavior, including old shared-template flags.
    val toolbarBackgroundAlignment: String? = null,
    // Per-skin default grid height in design units. Saved user height still wins.
    // Null retains the standard 240/160 grid; does not change key addresses/actions.
    val keyGridHeightDp: Int? = null,
    val keyGridHeightLandscapeDp: Int? = null,
    val qwertyMinHeightMatchesT9: Boolean = false,
    val defaultKeyWidthPercent: Int = 100,
    val defaultKeyHeightPercent: Int = 100,
    val defaultDigitHints: Boolean = true,
    val authoredPreeditHeightDp: Int? = null,
    val t9ColumnWidths: List<Float> = emptyList(),
    val t9BottomRowWidths: List<Float> = emptyList(),
    val t9AlignVisibleColumns: Boolean = false,
    val t9SidebarContentFraction: Float = 1f,
    val qwertyFiveKeyBottomRow: Boolean = false,
    val preserveToolbarArtwork: Boolean = false,
    val quickReplyTopInsetDp: Float = 0f,
    // Optional system family; no font files or global keyboard preferences are changed.
    val keyFontFamily: String? = null,
    val keyTextShadow: SkinTextShadow? = null,
    val keyLabelCenterXByKey: Map<String, Float> = emptyMap(),
    val keyLabelCenterYByKey: Map<String, Float> = emptyMap(),
    val keyHintCenterYByKey: Map<String, Float> = emptyMap(),
    // Resource-owned ink for alternating light/dark caps. Resolved once at view
    // construction, using the same precedence as key image overrides.
    val keyTextColorByKey: Map<String, Int> = emptyMap(),
    // Fit the visible bitmap, leaving the key's touch bounds unchanged.
    val keyImageAspectRatioByKey: Map<String, Float> = emptyMap(),
    val keyImageHorizontalOutsetDp: Float = 0f,
    val keyPressScale: Float = 1f,
    val keyMotion: SkinKeyMotion? = null,
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
    val preeditInTopSpacing: Boolean = false,
    val popupStyle: SkinPopupStyle? = null,
    // Optional preedit allocation inside the shared toolbar surface, with no extra frame.
    val inlinePreeditHeightDp: Int? = null,
    val preeditHeightDp: Int = 18,
    val preeditTextColor: Int? = null,
    val preeditShadowColor: Int? = null,
    val toolbarIconTint: Int? = null,
    val toolbarIconPaddingDp: Int? = null,
    val toolbarButtonBackground: Boolean = true,
    val unifiedSidebar: Boolean = false,
    val gridKeyVerticalCompensation: Boolean = true,
    val keyContentInsets: SkinContentInsets? = null,
    // Optional image-frame correction; the outer gesture slot stays unchanged.
    val keyFaceInsetsByKey: Map<String, SkinContentInsets> = emptyMap(),
    // A static keyboard-level layer below the keycaps, independent of their pixels.
    val keyFaceShadow: SkinKeyFaceShadow? = null,
    val toolbarIconOffsetYDp: Float = 0f,
    val toolbarIconOffsetYDpByRole: Map<String, Float> = emptyMap(),
    val keyIconSizeDp: Int? = null,
    val keyForegroundWidthDp: Int = 32,
    val keyForegroundHeightDp: Int = 12,
    val toolbarIconSizeDp: Int? = null,
    // Opt-in complete artwork bounds, including transparent padding. Unlike the
    // legacy glyph size these are not reduced by 0.7; touch slots stay unchanged.
    val toolbarArtworkSizes: Map<String, SkinToolbarArtworkSize> = emptyMap(),
    val keyPressEffect: SkinKeyPressEffect? = null,
    val layout: SkinLayoutStyle? = null,
    val keyForegroundSizes: Map<String, SkinForegroundSize> = emptyMap(),
    val keyTextBold: Boolean? = null,
    val spaceKeyTextColor: Int? = null,
    val languageKeyTextColor: Int? = null,
    val punctuationKeyTextColor: Int? = null,
    val sidebarTextColor: Int? = null,
    val keyTextScaleByLayout: Map<String, Float> = emptyMap(),
    val uppercaseLatinLabels: Boolean = false,
    val digitHintsOnly: Boolean = false,
    val hiddenKeyHints: Set<String> = emptySet(),
    val alignToolbarToKeyFaces: Boolean = false,
    // Appearance only: the outer KeyView and sidebar touch grid retain their standard bounds.
    val keyVisualHorizontalInsetDp: Float? = null,
    val keyVisualVerticalInsetDp: Float? = null,
    val keyVisualHorizontalInsetDpByLayout: Map<String, Float> = emptyMap(),
    val keyVisualVerticalInsetDpByLayout: Map<String, Float> = emptyMap(),
    val toolbarHeightDpByLayout: Map<String, Int> = emptyMap(),
    // Limited to the existing QWERTY bottom row; the space key absorbs the remainder.
    val qwertyBottomRowWidths: Map<String, Float> = emptyMap(),
    val keyEffectAboveFaces: Boolean = false
) : Parcelable {
    init {
        require(defaultKeyWidthPercent in 70..110 && defaultKeyHeightPercent in 70..110)
        require(!preeditInTopSpacing || (customToolbarLayout && !preeditOutsideBar))
        require(inlinePreeditHeightDp == null || inlinePreeditHeightDp in 12..24)
        require(authoredPreeditHeightDp == null || authoredPreeditHeightDp in 16..60)
        require(t9SidebarContentFraction.isFinite() && t9SidebarContentFraction in .6f..1f)
        listOf(t9ColumnWidths, t9BottomRowWidths).forEach { widths ->
            require(widths.isEmpty() || (widths.size == 5 && widths.all { it.isFinite() && it in .10f.. .45f } &&
                kotlin.math.abs(widths.sum() - 1f) < .001f))
        }
        listOfNotNull(keyGridHeightDp, keyGridHeightLandscapeDp).forEach {
            require(it in 120..480)
        }
        listOfNotNull(keyVisualHorizontalInsetDp, keyVisualVerticalInsetDp).forEach {
            require(it.isFinite() && it in 0f..24f)
        }
        require(keyFontFamily == null || keyFontFamily in setOf("sans-serif", "sans-serif-medium", "sans-serif-light", "sans-serif-rounded", "serif"))
        require(keyPressScale.isFinite() && keyPressScale in .94f..1f)
        require(keyLabelCenterXByKey.values.all { it.isFinite() && it in .1f.. .9f })
        val keySelector = Regex("^(?:(?:qwerty|t9|number)/)?(?:role:[a-z_]+|r[1-4]c(?:10|[1-9])|letter:[a-z]|digit:[0-9]|t9:[1-9])$")
        require(hiddenKeyHints.size <= 160 && hiddenKeyHints.all(keySelector::matches))
        listOf(keyLabelCenterYByKey, keyHintCenterYByKey).forEach { map ->
            require(map.size <= 160 && map.keys.all(keySelector::matches))
            require(map.values.all { it.isFinite() && it in .1f.. .9f })
        }
        require(keyFaceInsetsByKey.size <= 160 && keyFaceInsetsByKey.keys.all(keySelector::matches))
        keyFaceInsetsByKey.values.forEach { require(it.validFaceInsets()) }
        require(toolbarIconOffsetYDp.isFinite() && toolbarIconOffsetYDp in -16f..16f)
        require(toolbarIconOffsetYDpByRole.size <= 40 && toolbarIconOffsetYDpByRole.all { (key, value) ->
            key.matches(Regex("^[a-z][a-z_]{0,39}$")) && value.isFinite() && value in -16f..16f
        })
        require(keyTextColorByKey.size <= 160 && keyTextColorByKey.keys.all(keySelector::matches))
        require(keyImageAspectRatioByKey.size <= 160 && keyImageAspectRatioByKey.keys.all(keySelector::matches))
        require(keyImageAspectRatioByKey.values.all { it.isFinite() && it in .25f..4f })
        require(keyImageHorizontalOutsetDp.isFinite() && keyImageHorizontalOutsetDp in 0f..6f)
        require(toolbarShortcutLimit in 0..6)
        require(toolbarArtworkSizes.size <= 40 && toolbarArtworkSizes.keys.all {
            it.matches(Regex("^[a-z][a-z_]{0,39}$"))
        })
        require(quickReplyTopInsetDp.isFinite() && quickReplyTopInsetDp in 0f..8f)
        listOf(keyVisualHorizontalInsetDpByLayout, keyVisualVerticalInsetDpByLayout).forEach { map ->
            require(map.keys.all { it in setOf("qwerty", "t9", "number") })
            require(map.values.all { it.isFinite() && it in 0f..24f })
        }
        require(toolbarHeightDpByLayout.keys.all { it in setOf("qwerty", "t9", "number") })
        require(toolbarHeightDpByLayout.values.all { it in 36..80 })
        require(qwertyBottomRowWidths.keys.all { it in setOf("role:number_mode", "role:language", "role:enter", "qwerty/r4c1", "qwerty/r4c2", "qwerty/r4c5") })
        require(qwertyBottomRowWidths.values.all { it.isFinite() && it in .08f.. .24f })
    }
    fun barBodyHeight(layout: String): Int = toolbarHeightDpByLayout[layout] ?: barBodyHeightDp
    val configuredKeyWidthPercent: Int get() = localConfiguration?.keyWidth ?: defaultKeyWidthPercent
    val configuredKeyHeightPercent: Int get() = localConfiguration?.keyHeight ?: defaultKeyHeightPercent
    fun topSpacingHeightDp(authored: Int): Int {
        val configured = localConfiguration?.toolbarTop ?: authored.coerceAtLeast(0)
        return if (preeditInTopSpacing) maxOf(configured,
            kotlin.math.ceil(12f * (localConfiguration?.preeditTextScale ?: 100) / 100f * 1.2f).toInt() + 4)
        else configured
    }
    fun resolvedBarBodyHeight(layout: String, standardBodyHeight: Int): Int =
        if (skinLabStandard && !customToolbarLayout) standardBodyHeight else barBodyHeight(layout)
    fun resolvedBarTotalHeight(layout: String, standardBodyHeight: Int): Int =
        if (skinLabStandard && !customToolbarLayout) 20 + standardBodyHeight
        else reservedPreeditDp + barBodyHeight(layout)
    fun authoredKeyTextColor(ids: List<String>): Int? =
        localConfiguration?.keyTextColor?.takeIf { it != 0 } ?: ids.firstNotNullOfOrNull(keyTextColorByKey::get)
    fun authoredKeyAspectRatio(ids: List<String>): Float? = ids.firstNotNullOfOrNull(keyImageAspectRatioByKey::get)
    val reservedPreeditDp: Int get() = if (preeditOutsideBar) authoredPreeditHeightDp ?: preeditHeightDp.coerceIn(16, 32) else 0
    val barBodyHeightDp: Int get() = toolbarHeightDp.coerceIn(36, 80)
    val barTotalHeightDp: Int get() = reservedPreeditDp + barBodyHeightDp
    // Large illustrated icons include their own base; reduce artwork without shrinking hit targets.
    val toolbarArtworkSizeDp: Int? get() = toolbarIconSizeDp?.let { if (it >= 40) it * 7 / 10 else it }
}

@Serializable
@Parcelize
data class SkinToolbarArtworkSize(
    val widthDp: Int,
    val heightDp: Int,
    val landscapeWidthDp: Int? = null,
    val landscapeHeightDp: Int? = null
) : Parcelable {
    init {
        require(widthDp in 8..128 && heightDp in 8..128)
        require(landscapeWidthDp == null || landscapeWidthDp in 8..128)
        require(landscapeHeightDp == null || landscapeHeightDp in 8..128)
    }
    fun width(landscape: Boolean): Int = if (landscape) landscapeWidthDp ?: widthDp else widthDp
    fun height(landscape: Boolean): Int = if (landscape) landscapeHeightDp ?: heightDp else heightDp
}

@Serializable
@Parcelize
data class SkinTextShadow(
    val color: Int = 0,
    val radiusDp: Float = 0f,
    val dxDp: Float = 0f,
    val dyDp: Float = 0f
) : Parcelable {
    init {
        require(radiusDp.isFinite() && radiusDp in 0f..4f)
        require(dxDp.isFinite() && dxDp in -4f..4f)
        require(dyDp.isFinite() && dyDp in -4f..4f)
    }
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
    val waveDistortion: Float = .027f,
    val particleSprites: List<Theme.Custom.CustomBackground> = emptyList()
) : Parcelable {
    companion object {
        fun lightweight(ink: Int) = SkinKeyPressEffect(colors = listOf(ink), durationMs = 280,
            radiusDp = 24f, glowAlpha = 0f, particleCount = 4, particleSizeDp = 1.2f,
            travelDp = 12f, particleAlpha = .62f, maxBursts = 3)
    }

    init {
        require(colors.size in 1..4)
        require(particleSprites.size <= 4)
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

private fun SkinContentInsets.validFaceInsets(): Boolean =
    listOf(leftDp, topDp, rightDp, bottomDp).all { it.isFinite() && it in 0f..24f }

/** Design units; a rounded rectangle below all key art, not a text/drop-shadow filter. */
@Serializable
@Parcelize
data class SkinKeyFaceShadow(
    val widthPercent: Int = 100,
    val heightPercent: Int = 100,
    @kotlinx.serialization.Transient val widthScale: Float = widthPercent / 100f,
    @kotlinx.serialization.Transient val heightScale: Float = heightPercent / 100f,
    val color: Int = -16777216,
    val opacity: Float = .3f,
    val blurRadiusDp: Float = 3f,
    val spreadDp: Float = .5f,
    val cornerRadiusDp: Float = 8f,
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 2f,
    // Fade only the terminal band at the grid bottom; zero preserves the original mask.
    val bottomFadeDp: Float = 0f,
    val insets: SkinContentInsets = SkinContentInsets()
) : Parcelable {
    init {
        require(widthPercent in 70..120 && heightPercent in 70..120)
        require(widthScale.isFinite() && widthScale in .7f..1.2f)
        require(heightScale.isFinite() && heightScale in .7f..1.2f)
        require(opacity.isFinite() && opacity in 0f..1f)
        require(blurRadiusDp.isFinite() && blurRadiusDp in 0f..12f)
        require(spreadDp.isFinite() && spreadDp in 0f..8f)
        require(cornerRadiusDp.isFinite() && cornerRadiusDp in 0f..24f)
        require(offsetXDp.isFinite() && offsetXDp in -12f..12f)
        require(offsetYDp.isFinite() && offsetYDp in -12f..12f)
        require(bottomFadeDp.isFinite() && bottomFadeDp in 0f..24f)
        require(insets.validFaceInsets())
    }
}

/** Source-pixel caps and source density make rounded edges independent of key aspect ratio. */
@Serializable
@Parcelize
data class SkinImageSlice(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val sourceDensityDpi: Int = 160,
    val tileCenter: Boolean = false,
    /** Opt in to authored off-centre stretch bands; absent keeps the legacy half-image caps. */
    val asymmetricCaps: Boolean = false
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
