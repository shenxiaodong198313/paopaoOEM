package com.paopaoime.aikeyboard.ime.keyboard

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import com.paopaoime.aikeyboard.ime.data.theme.Theme

class KeyboardPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var theme: Theme? = null

    fun setTheme(theme: Theme) {
        this.theme = theme
        invalidate()
    }

    fun setTheme(theme: Theme, backgroundDrawable: Drawable?) {
        this.theme = theme
        if (backgroundDrawable != null) {
            background = backgroundDrawable
        }
        invalidate()
    }
}

