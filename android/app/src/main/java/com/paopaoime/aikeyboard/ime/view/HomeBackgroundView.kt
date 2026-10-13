package com.paopaoime.aikeyboard.ime.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class HomeBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        // 装饰图形的颜色：半透明的浅色
        shapePaint.color = Color.parseColor("#33FFFFFF") // 20% white
        shapePaint.style = Paint.Style.STROKE
        shapePaint.strokeWidth = 100f // 较粗的线条
        shapePaint.strokeCap = Paint.Cap.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 背景颜色：浅灰色 #F0F0F3
        val colorTop = Color.parseColor("#F0F0F3")
        val colorBottom = Color.parseColor("#F0F0F3")

        val shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            colorTop, colorBottom, Shader.TileMode.CLAMP)
        bgPaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 1. Draw Background Gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Draw Abstract Shape (Top Right) - Removed as requested
        // 为了更像图片，我们可以画几个半透明的圆或者粗线条

        // 保存画布状态
        /*
        canvas.save()

        // 绘制一个简单的抽象装饰
        // 一个大的半透明圆环在右上角
        shapePaint.strokeWidth = width * 0.15f // 线宽随屏幕宽度变化
        shapePaint.color = Color.argb(40, 255, 255, 255) // 非常淡的白色

        // 在右上角画一段弧
        canvas.drawArc(
            width * 0.4f, -height * 0.1f,
            width * 1.4f, height * 0.4f,
            140f, 200f, false, shapePaint
        )

        // 再画一段交错的
         canvas.drawArc(
            width * 0.6f, -height * 0.2f,
            width * 1.2f, height * 0.3f,
            100f, 180f, false, shapePaint
        )

        canvas.restore()
        */
    }
}

