package org.fcitx.fcitx5.android.data.theme

import android.content.Context
import org.fcitx.fcitx5.android.utils.appContext

/** An OEM settings override for the press effect. Other theme properties keep their own values. */
object KeyboardEffectPreference {
    const val SKIN_DEFAULT = "skin"
    const val NONE = "none"

    private val storage by lazy { appContext.getSharedPreferences("paopao_key_effects", Context.MODE_PRIVATE) }

    fun selected(): String = storage.getString("motion", SKIN_DEFAULT)
        ?.takeIf(::isSupported) ?: SKIN_DEFAULT

    fun select(id: String) {
        require(isSupported(id))
        if (id == selected()) return
        storage.edit().putString("motion", id).apply()
        ThemeManager.refreshLocalConfiguration()
    }

    private fun isSupported(id: String) = id == SKIN_DEFAULT || id == NONE || SkinEffectCatalog.preset(id) != null

    internal fun decorate(theme: Theme): Theme {
        val id = selected()
        if (id == SKIN_DEFAULT) return theme
        val custom = when (theme) {
            is Theme.Custom -> theme
            is Theme.Builtin -> theme.deriveCustomNoBackground(theme.name)
            is Theme.Monet -> theme.toCustom()
        }
        val style = custom.renderStyle ?: SkinRenderStyle()
        val preset = SkinEffectCatalog.preset(id)
        val ambient = preset?.takeIf { id == "ambient_space" || id == "ambient_sidebar" }?.let {
            SkinAmbientEffect(color = it.effect.colors.first(), opacity = .3f, particleCount = 8, radiusDp = 1f)
        }
        return custom.copy(renderStyle = style.copy(
            keyPressEffect = if (ambient == null) preset?.effect else null,
            ambientSpaceEffect = ambient.takeIf { id == "ambient_space" },
            ambientSidebarEffect = ambient.takeIf { id == "ambient_sidebar" },
            keyEffectAboveFaces = preset?.particles == true,
            suppressDefaultKeyEffect = true,
        ))
    }
}
