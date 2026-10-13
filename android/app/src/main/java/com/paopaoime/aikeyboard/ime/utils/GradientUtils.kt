package com.paopaoime.aikeyboard.ime.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources

object GradientUtils {
    fun applyAiGradient(textView: TextView) {
        applyLinearGradient(
            textView,
            intArrayOf(
                Color.parseColor("#3edbff"),
                Color.parseColor("#ee90f6"),
                Color.parseColor("#fdc538")
            ),
            floatArrayOf(0.0093f, 0.5074f, 0.9952f)
        )
    }

    fun applyMidsceneAgentPillGradient(textView: TextView) {
        applyLinearGradient(
            textView,
            intArrayOf(
                Color.parseColor("#4BC4FF"),
                Color.parseColor("#1A9AFF"),
                Color.parseColor("#9676FF"),
                Color.parseColor("#BE64FE"),
                Color.parseColor("#E157CB"),
                Color.parseColor("#EF5794"),
                Color.parseColor("#FD683F"),
                Color.parseColor("#FE7C2B"),
                Color.parseColor("#FFA10B")
            ),
            floatArrayOf(0.0034f, 0.1376f, 0.2718f, 0.4457f, 0.5849f, 0.6892f, 0.8284f, 0.9427f, 0.9973f)
        )
    }

    fun applyMidsceneAgentPillGradient(imageView: ImageView, drawableRes: Int) {
        applyLinearGradientToIcon(
            imageView,
            drawableRes,
            intArrayOf(
                Color.parseColor("#4BC4FF"),
                Color.parseColor("#1A9AFF"),
                Color.parseColor("#9676FF"),
                Color.parseColor("#BE64FE"),
                Color.parseColor("#E157CB"),
                Color.parseColor("#EF5794"),
                Color.parseColor("#FD683F"),
                Color.parseColor("#FE7C2B"),
                Color.parseColor("#FFA10B")
            ),
            floatArrayOf(0.0034f, 0.1376f, 0.2718f, 0.4457f, 0.5849f, 0.6892f, 0.8284f, 0.9427f, 0.9973f)
        )
    }

    private fun applyLinearGradient(
        textView: TextView,
        colors: IntArray,
        positions: FloatArray
    ) {
        textView.post {
            val width = textView.width.toFloat()
            if (width > 0) {
                val paint = textView.paint
                val shader = LinearGradient(
                    0f, 0f, width, textView.textSize * 0.2f, // Slight angle approximation or just horizontal
                    colors,
                    positions,
                    Shader.TileMode.CLAMP
                )
                paint.shader = shader
                textView.invalidate()
            }
        }
    }

    private fun applyLinearGradientToIcon(
        imageView: ImageView,
        drawableRes: Int,
        colors: IntArray,
        positions: FloatArray
    ) {
        imageView.post {
            val width = imageView.width
            val height = imageView.height
            if (width <= 0 || height <= 0) return@post
            val drawable = AppCompatResources.getDrawable(imageView.context, drawableRes)?.mutate() ?: return@post
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.setTint(Color.WHITE)
            drawable.draw(canvas)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height * 0.2f,
                    colors,
                    positions,
                    Shader.TileMode.CLAMP
                )
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.xfermode = null
            imageView.setImageBitmap(bitmap)
        }
    }
}
