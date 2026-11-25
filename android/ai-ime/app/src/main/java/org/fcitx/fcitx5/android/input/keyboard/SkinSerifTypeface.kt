package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import android.graphics.Typeface

/** App-owned fonts are cached independently of downloadable skin packages. */
internal object SkinSerifTypeface {
    private val fonts = mutableMapOf<Boolean, Typeface>()

    @Synchronized
    fun get(context: Context, bold: Boolean): Typeface = fonts.getOrPut(bold) {
        val weight = if (bold) "Bold" else "Regular"
        Typeface.createFromAsset(context.applicationContext.assets,
            "fonts/skin-serif/SourceSerif4-$weight.ttf")
    }
}
