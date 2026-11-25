package org.fcitx.fcitx5.android.data.theme

import android.content.Context
import android.graphics.Color
import org.fcitx.fcitx5.android.utils.appContext

/** Single-process local store. The IME hot path only reads immutable in-memory snapshots. */
object LocalSkinConfiguration {
    private val preferences by lazy { appContext.getSharedPreferences("skin_configuration_v1", Context.MODE_PRIVATE) }
    private val store = SkinConfigurationStore({ preferences.all }) { name, value -> preferences.edit().putString(name, value).apply() }

    fun get(name: String): SkinConfiguration? = store.get(name)
    fun load(name: String, theme: Theme.Custom): SkinConfiguration = get(name) ?: SkinConfiguration.defaults(theme)

    fun save(name: String, config: SkinConfiguration) {
        val previous = store.get(name)
        store.save(name, config)
        if (previous == config) return
        refresh()
    }

    /** Slider frames update memory; only the settled/final value is persisted. */
    fun preview(name: String, config: SkinConfiguration) {
        if (store.preview(name, config)) refresh()
    }

    private fun refresh() {
        org.fcitx.fcitx5.android.data.KeySounds.cancelPending()
        ThemeManager.refreshLocalConfiguration()
        SkinTrialSession.refreshLocalConfiguration()
    }

    fun reset(name: String, theme: Theme.Custom): SkinConfiguration = SkinConfiguration.defaults(theme).also { save(name, it) }

    fun decorate(theme: Theme, settingsName: String = theme.name): Theme {
        val custom = theme as? Theme.Custom ?: return theme
        val config = get(settingsName) ?: return theme
        val original = custom.renderStyle ?: SkinRenderStyle()
        val selected = when (config.effectId) {
            "none", "ambient_space", "ambient_sidebar" -> null
            "skin" -> original.keyPressEffect
            else -> config.effect
        }
        val palette = if (!config.gradientColors || config.effect.colors.size < 2) listOf(config.effect.colors.first()) else {
            val a = config.effect.colors.first(); val b = config.effect.colors.last()
            (0..3).map { i ->
                val t = i / 3f
                fun blend(x: Int, y: Int) = (x + (y - x) * t).toInt()
                Color.rgb(blend(Color.red(a), Color.red(b)), blend(Color.green(a), Color.green(b)), blend(Color.blue(a), Color.blue(b)))
            }
        }
        val effect = if (config.effectId == "skin") selected else selected?.copy(
            colors = palette, glowAlpha = selected.glowAlpha * config.effectIntensity / 100f,
            particleAlpha = selected.particleAlpha * config.effectIntensity / 100f,
            rippleAlpha = selected.rippleAlpha * config.effectIntensity / 100f
        )
        val ambient = SkinAmbientEffect(color = palette.first(), opacity = config.effectIntensity / 200f,
            particleCount = config.effect.particleCount.coerceIn(1, 16), radiusDp = config.effect.particleSizeDp.coerceIn(.2f, 2f),
            cycleMs = (12000 / config.effect.speed).toInt().coerceIn(6000, 30000))
        return custom.copy(renderStyle = original.copy(
            localConfiguration = config,
            popupStyle = config.popupStyle ?: original.popupStyle,
            keyMotion = config.keyMotion ?: original.keyMotion,
            keyLabelCenterY = if (config.labelVerticalOffset == 0) original.keyLabelCenterY else
                ((original.keyLabelCenterY ?: .5f) + config.labelVerticalOffset / 100f).coerceIn(.15f, .85f),
            keyLabelWithHintCenterY = if (config.labelVerticalOffset == 0) original.keyLabelWithHintCenterY else
                ((original.keyLabelWithHintCenterY ?: .62f) + config.labelVerticalOffset / 100f).coerceIn(.15f, .85f),
            keyHintCenterY = if (config.hintVerticalOffset == 0) original.keyHintCenterY else
                ((original.keyHintCenterY ?: .23f) + config.hintVerticalOffset / 100f).coerceIn(.08f, .5f),
            keyLabelCenterYByKey = original.keyLabelCenterYByKey.mapValues { (_, value) ->
                (value + config.labelVerticalOffset / 100f).coerceIn(.15f, .85f)
            },
            keyHintCenterYByKey = original.keyHintCenterYByKey.mapValues { (_, value) ->
                (value + config.hintVerticalOffset / 100f).coerceIn(.1f, .9f)
            },
            keyTextBold = config.bold,
            keyTextShadow = if (config.textShadowEnabled) config.textShadow else null,
            keyFaceShadow = if (config.shadowEnabled) config.shadow.copy(widthScale = config.shadowWidth / 100f, heightScale = config.shadowHeight / 100f) else null,
            keyPressEffect = effect,
            ambientSpaceEffect = when (config.effectId) { "ambient_space" -> ambient; "skin" -> original.ambientSpaceEffect; else -> null },
            ambientSidebarEffect = when (config.effectId) { "ambient_sidebar" -> ambient; "skin" -> original.ambientSidebarEffect; else -> null }
        ))
    }
}
