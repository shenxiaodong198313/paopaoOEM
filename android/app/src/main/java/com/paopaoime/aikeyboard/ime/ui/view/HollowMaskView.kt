package com.paopaoime.aikeyboard.ime.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class HollowMaskView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    var targetView: View? = null
        set(value) {
            field = value
            invalidate()
        }

    // Default scale 1.0 (exact match)
    var hollowScale: Float = 1.0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val target = targetView ?: return

        if (target.visibility != View.VISIBLE) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            return
        }

        // Use screen coordinates to ensure precise alignment regardless of view hierarchy
        val targetLoc = IntArray(2)
        target.getLocationInWindow(targetLoc)
        val maskLoc = IntArray(2)
        this.getLocationInWindow(maskLoc)

        // Calculate target's position relative to this mask view
        val relX = targetLoc[0] - maskLoc[0]
        val relY = targetLoc[1] - maskLoc[1]

        val cx = relX + target.width / 2f
        val cy = relY + target.height / 2f
        
        // Shrink the hole by 5dp on each side (total 10dp reduction in width/height)
        val shrinkPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            5f, 
            resources.displayMetrics
        )
        
        val w = (target.width * hollowScale) - (shrinkPx * 2)
        val h = (target.height * hollowScale) - (shrinkPx * 2)

        val left = (cx - w / 2).toInt()
        val top = (cy - h / 2).toInt()
        val right = (cx + w / 2).toInt()
        val bottom = (cy + h / 2).toInt()

        val holeRect = Rect(left, top, right, bottom)

        // Draw 4 rectangles around the hole
        // Top
        canvas.drawRect(0f, 0f, width.toFloat(), holeRect.top.toFloat(), paint)
        // Bottom
        canvas.drawRect(0f, holeRect.bottom.toFloat(), width.toFloat(), height.toFloat(), paint)
        // Left
        canvas.drawRect(0f, holeRect.top.toFloat(), holeRect.left.toFloat(), holeRect.bottom.toFloat(), paint)
        // Right
        canvas.drawRect(holeRect.right.toFloat(), holeRect.top.toFloat(), width.toFloat(), holeRect.bottom.toFloat(), paint)
    }
}
