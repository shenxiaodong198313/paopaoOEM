package org.fcitx.fcitx5.android.data.theme

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/** App-owned per-skin preferences. Never serialized into, or uploaded with, a skin package. */
@Serializable
@Parcelize
data class SkinConfiguration(
    val keyWidth: Int = 100,
    val keyHeight: Int = 100,
    val shadowEnabled: Boolean = false,
    val shadowWidth: Int = 100,
    val shadowHeight: Int = 100,
    val shadow: SkinKeyFaceShadow = SkinKeyFaceShadow(bottomFadeDp = 8f),
    val toolbarVisible: Boolean = true,
    val toolbarLinked: Boolean = true,
    val toolbarLeft: Int = 0,
    val toolbarRight: Int = 0,
    val toolbarTop: Int? = null,
    val background: String = "gradient",
    val gradientRange: Int = 0,
    val backgroundDefaultsVersion: Int = 1,
    val bubbles: Boolean = true,
    val digitHints: Boolean = true,
    val volume: Int = 50,
    val sound: String = "skin",
    val haptic: Int = 6,
    val preeditTextScale: Int = 100,
    val preeditTextColor: Int = 0,
    val preeditLeft: Int = 8,
    val candidateTextScale: Int = 100,
    val keyTextScale: Int = 100,
    val candidateTextColor: Int = 0,
    val keyTextColor: Int = 0,
    val font: String = "default",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val textShadowEnabled: Boolean = false,
    val textShadow: SkinTextShadow = SkinTextShadow(color = 0x5971548D, radiusDp = 1.5f, dyDp = 1f),
    val effectId: String = "skin",
    val effect: SkinKeyPressEffect = SkinKeyPressEffect(),
    val gradientColors: Boolean = false,
    val effectIntensity: Int = 60,
    val keyMotion: SkinKeyMotion? = null,
    val labelVerticalOffset: Int = 0,
    val hintVerticalOffset: Int = 0,
    val characterAnimationEnabled: Boolean = true,
    val characterAnimationSpeed: Int = 100,
    val popupStyle: SkinPopupStyle? = null,
    val effectAboveFaces: Boolean = false,
    val toolbarAutoAlign: Boolean? = null
) : Parcelable {
    init {
        require(keyWidth in 70..110 && keyHeight in 70..110)
        require(shadowWidth in 70..120 && shadowHeight in 70..120)
        require(toolbarLeft in 0..36 && toolbarRight in 0..36)
        require(toolbarTop == null || toolbarTop in 0..24)
        require(background in setOf("transparent", "gradient") && gradientRange in 0..100)
        require(volume in 0..100 && haptic in 0..6)
        require(sound == "skin" || sound in org.fcitx.fcitx5.android.data.KeySounds.items.map { it.first })
        require(preeditTextScale in 70..130 && preeditLeft in 0..36)
        require(preeditTextColor == 0 || (preeditTextColor ushr 24) == 255)
        require(candidateTextScale in 70..130 && keyTextScale in 70..130)
        require(candidateTextColor == 0 || (candidateTextColor ushr 24) == 255)
        require(keyTextColor == 0 || (keyTextColor ushr 24) == 255)
        require(font in setOf("default", "nunito", "quicksand", "lora"))
        require(!italic || font != "quicksand")
        require(effectId == "skin" || effectId == "none" || SkinEffectCatalog.items.any { it.id == effectId })
        require(effectIntensity in 0..100)
        require(labelVerticalOffset in -20..20 && hintVerticalOffset in -20..20)
        require(characterAnimationSpeed in 50..150)
    }

    fun followsKeyEdges(legacyDefault: Int): Boolean = toolbarAutoAlign ?:
        (toolbarLeft == toolbarRight && (toolbarLeft == 0 || toolbarLeft == legacyDefault))

    companion object {
        fun hidesDigitHint(config: SkinConfiguration?, label: String, hint: String, isLetterKey: Boolean = true, defaultEnabled: Boolean = true): Boolean =
            (config?.digitHints ?: defaultEnabled) == false && isLetterKey && label.isNotEmpty() &&
                label.codePoints().allMatch { Character.isLetter(it) } && hint.isNotEmpty() && hint.all { it in '0'..'9' }

        fun defaults(theme: Theme.Custom): SkinConfiguration {
            val style = theme.renderStyle
            return SkinConfiguration(
                keyWidth = style?.defaultKeyWidthPercent ?: 100,
                keyHeight = style?.defaultKeyHeightPercent ?: 100,
                digitHints = style?.defaultDigitHints ?: true,
                toolbarAutoAlign = true,
                toolbarTop = theme.toolbarTopInsetDp.takeIf { style?.customToolbarLayout == true },
                toolbarLeft = if (style?.customToolbarLayout == true) theme.toolbarHorizontalInsetDp.coerceIn(0, 36) else 0,
                toolbarRight = if (style?.customToolbarLayout == true) theme.toolbarHorizontalInsetDp.coerceIn(0, 36) else 0,
                shadowEnabled = style?.keyFaceShadow != null,
                shadowWidth = style?.keyFaceShadow?.widthPercent ?: 100,
                shadowHeight = style?.keyFaceShadow?.heightPercent ?: 100,
                shadow = style?.keyFaceShadow ?: SkinKeyFaceShadow(bottomFadeDp = 8f),
                bold = style?.keyTextBold ?: false,
                textShadowEnabled = style?.keyTextShadow != null,
                textShadow = style?.keyTextShadow ?: SkinTextShadow(color = 0x5971548D, radiusDp = 1.5f, dyDp = 1f),
                effect = style?.keyPressEffect ?: SkinKeyPressEffect.lightweight(theme.keyTextColor),
                effectAboveFaces = (style?.keyPressEffect == null && style?.ambientSpaceEffect == null &&
                    style?.ambientSidebarEffect == null && theme.keyAnimations.isEmpty()) ||
                    (style?.keyEffectAboveFaces == true && style.keyPressEffect?.particleSprites?.isNotEmpty() == true),
                keyMotion = style?.keyMotion,
                sound = "skin"
            )
        }
    }
}

/** All motion, shape, ring/water, release and ambient capabilities of the mobile renderer. */
object SkinEffectCatalog {
    data class Preset(val id: String, val title: String, val effect: SkinKeyPressEffect,
        val particles: Boolean = true, val ripples: Boolean = false)
    val items = listOf(
        Preset("expand", "星芒扩散", SkinKeyPressEffect()),
        Preset("weave", "交织流光", SkinKeyPressEffect(motion = "weave", turbulenceDp = 14f)),
        Preset("follow", "指尖跟随", SkinKeyPressEffect(motion = "follow", trailSegments = 2)),
        Preset("orbit", "环绕星轨", SkinKeyPressEffect(motion = "orbit", rotationTurns = 1.5f)),
        Preset("fountain", "粒子喷泉", SkinKeyPressEffect(motion = "fountain", gravityDp = 70f)),
        Preset("nebula", "柔雾星云", SkinKeyPressEffect(motion = "nebula", particleShape = "mixed", glowAlpha = .8f)),
        Preset("dots", "圆点微粒", SkinKeyPressEffect(particleShape = "dot")),
        Preset("mixed", "混合星点", SkinKeyPressEffect(particleShape = "mixed")),
        Preset("digits", "数字粒子", SkinKeyPressEffect(particleShape = "digit", motion = "fountain")),
        Preset("trails", "星光拖尾", SkinKeyPressEffect(trailSegments = 4, motion = "weave")),
        Preset("rings", "圆环涟漪", SkinKeyPressEffect(particleCount = 0, rippleCount = 3, glowAlpha = .15f), false, true),
        Preset("water", "水波涟漪", SkinKeyPressEffect(particleCount = 0, rippleCount = 3, rippleStyle = "water", glowAlpha = .2f), false, true),
        Preset("fragments", "断续水波", SkinKeyPressEffect(particleCount = 0, rippleCount = 3, rippleStyle = "water", fragmentCount = 20, fragmentSpread = .2f, waveDistortion = .065f), false, true),
        Preset("release", "松手余辉", SkinKeyPressEffect(releaseDurationMs = 900, glowAlpha = .8f)),
        Preset("ambient_space", "空格内腔微粒", SkinKeyPressEffect(particleCount = 0), false),
        Preset("ambient_sidebar", "侧栏环境微粒", SkinKeyPressEffect(particleCount = 0), false)
    )
    fun preset(id: String) = items.firstOrNull { it.id == id }
}
