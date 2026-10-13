package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.paopaoime.aikeyboard.ime.R

object CreativeModelIconResolver {
    fun resolveIconRes(modelId: String): Int {
        val normalized = modelId.trim().lowercase()
        return if (normalized == "qwen-image" || normalized.contains("qwen-image")) {
            R.drawable.ic_model_qwen
        } else {
            R.drawable.ic_model_creative_default
        }
    }

    fun resolveStartDrawable(context: Context, modelId: String, sizeDp: Float = 20f): Drawable? {
        val drawable = ContextCompat.getDrawable(context, resolveIconRes(modelId)) ?: return null
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            sizeDp,
            context.resources.displayMetrics
        ).toInt()
        drawable.setBounds(0, 0, sizePx, sizePx)
        return drawable
    }
}
