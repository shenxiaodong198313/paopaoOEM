package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import android.graphics.Typeface
import android.os.Build

object SkinConfigurationTypeface {
    private val cache = mutableMapOf<String, Typeface>()
    @Synchronized fun get(context: Context, id: String, bold: Boolean, italic: Boolean): Typeface =
        cache.getOrPut("$id/$bold/$italic") {
            val style = (if (bold) Typeface.BOLD else 0) or (if (italic && id != "quicksand") Typeface.ITALIC else 0)
            if (id == "default") return@getOrPut Typeface.create(Typeface.DEFAULT, style)
            val file = when (id) { "nunito" -> "Nunito"; "quicksand" -> "Quicksand"; "lora" -> "Lora"; else -> return@getOrPut Typeface.DEFAULT }
            val suffix = if (italic && id != "quicksand") "-Italic" else ""
            val path = "fonts/skin-config/$file$suffix.ttf"
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) Typeface.Builder(context.assets, path).setWeight(if (bold) 700 else 400).setItalic(italic && id != "quicksand").build()
                else Typeface.createFromAsset(context.assets, path)
            }.getOrElse { Typeface.create(Typeface.DEFAULT, style) }
        }
}
