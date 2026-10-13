package com.paopaoime.aikeyboard.compat.agent.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver

class RealtimeBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var mDownscaleFactor = 0.3f
    private var mBlurRadius = 20f
    private var mOverlayColor = 0x661A1A1A.toInt()
    private var mBlurType = 0

    fun setBlurType(type: Int) {
        if (mBlurType != type) {
            mBlurType = type
            refreshBlur()
        }
    }

    fun setBlurRadius(radius: Float) {
        if (mBlurRadius != radius) {
            mBlurRadius = radius
            refreshBlur()
        }
    }

    fun setCornerRadius(radius: Float) {
        this.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        this.clipToOutline = true
    }

    fun setOverlayColor(color: Int) {
        if (mOverlayColor != color) {
            mOverlayColor = color
            mOverlayPaint.color = color
            invalidate()
        }
    }

    fun setDownscaleFactor(factor: Float) {
        if (mDownscaleFactor != factor) {
            mDownscaleFactor = factor
            refreshBlur()
        }
    }

    fun forceRefresh() {
        if (mDecorView == null) {
            mDecorView = getActivity()?.window?.decorView
        }
        lastRefreshAt = 0L
        refreshBlur()
    }
    
    private var mBlurredBitmap: Bitmap? = null
    private var mBlurringCanvas: Canvas? = null
    private var mBitmapToBlur: Bitmap? = null
    private var mBitmapToBlurPadded: Bitmap? = null
    private var mBlurContentWidth: Int = 0
    private var mBlurContentHeight: Int = 0
    
    private var mDecorView: View? = null
    private val mDecorViewLocation = IntArray(2)
    private val mViewLocation = IntArray(2)
    private val mRectSrc = Rect()
    private val mRectDst = Rect()
    private val mPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val mOverlayPaint = Paint().apply { color = mOverlayColor }

    private var isRendering = false
    private var lastRefreshAt: Long = 0L
    private val minRefreshIntervalMs = 80L

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (!isShown) return@OnPreDrawListener true
        val now = SystemClock.uptimeMillis()
        if (now - lastRefreshAt >= minRefreshIntervalMs) {
            lastRefreshAt = now
            refreshBlur()
        }
        true
    }

    init {
        val activity = getActivity()
        mDecorView = activity?.window?.decorView
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mDecorView = getActivity()?.window?.decorView
        mDecorView?.viewTreeObserver?.addOnPreDrawListener(preDrawListener)
    }

    override fun onDetachedFromWindow() {
        mDecorView?.viewTreeObserver?.removeOnPreDrawListener(preDrawListener)
        releaseBitmap()
        super.onDetachedFromWindow()
    }

    private fun releaseBitmap() {
        mBlurredBitmap?.recycle()
        mBlurredBitmap = null
        mBitmapToBlur?.recycle()
        mBitmapToBlur = null
        mBitmapToBlurPadded?.recycle()
        mBitmapToBlurPadded = null
    }
    
    private fun isBlurEnabled(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    private fun refreshBlur() {
        if (!isBlurEnabled()) {
            releaseBitmap()
            return
        }
        if (isRendering || mDecorView == null || width == 0 || height == 0) return
        isRendering = true

        try {
            // 1. Prepare Bitmaps
            val scaledWidth = (width * mDownscaleFactor).toInt().coerceAtLeast(1)
            val scaledHeight = (height * mDownscaleFactor).toInt().coerceAtLeast(1)
            mBlurContentWidth = scaledWidth
            mBlurContentHeight = scaledHeight

            if (mBitmapToBlur == null || mBitmapToBlur?.width != scaledWidth || mBitmapToBlur?.height != scaledHeight) {
                releaseBitmap()
                mBitmapToBlur = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                if (mBitmapToBlur == null) return
                mBlurringCanvas = Canvas(mBitmapToBlur!!)
            }

            // 2. Capture Background
            // Hide ourselves so we don't draw our own blur (infinite loop)
            val wasVisible = visibility == View.VISIBLE
            if (wasVisible) visibility = View.INVISIBLE

            mDecorView?.getLocationOnScreen(mDecorViewLocation)
            this.getLocationOnScreen(mViewLocation)

            val x = mViewLocation[0] - mDecorViewLocation[0]
            val y = mViewLocation[1] - mDecorViewLocation[1]

            mBlurringCanvas?.save()
            mBlurringCanvas?.scale(mDownscaleFactor, mDownscaleFactor)
            mBlurringCanvas?.translate(-x.toFloat(), -y.toFloat())
            
            // Draw DecorView onto our small bitmap
            mDecorView?.draw(mBlurringCanvas!!)
            
            mBlurringCanvas?.restore()

            // Restore visibility
            if (wasVisible) visibility = View.VISIBLE

            // 3. Blur
            // The old external blur helper was provided transitively by agent-core.
            // Keep a local fallback so app can build and render a softened background.
            val inputForBlur = ensureStrideSafeBitmap(mBitmapToBlur!!)
            mBlurredBitmap = createBlurBitmap(inputForBlur)

            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isRendering = false
        }
    }

    private fun ensureStrideSafeBitmap(src: Bitmap): Bitmap {
        val alignedWidth = alignUpTo(src.width, 4)
        if (alignedWidth == src.width) return src

        if (mBitmapToBlurPadded == null || mBitmapToBlurPadded?.width != alignedWidth || mBitmapToBlurPadded?.height != src.height) {
            mBitmapToBlurPadded?.recycle()
            mBitmapToBlurPadded = Bitmap.createBitmap(alignedWidth, src.height, Bitmap.Config.ARGB_8888)
        }

        val dst = mBitmapToBlurPadded ?: return src
        val c = Canvas(dst)
        c.drawBitmap(src, 0f, 0f, null)

        if (src.width > 1 && alignedWidth > src.width) {
            val srcRect = Rect(src.width - 1, 0, src.width, src.height)
            val dstRect = Rect(src.width, 0, alignedWidth, src.height)
            c.drawBitmap(src, srcRect, dstRect, null)
        }

        return dst
    }

    private fun createBlurBitmap(src: Bitmap): Bitmap {
        val config = src.config ?: Bitmap.Config.ARGB_8888
        return src.copy(config, false)
    }

    private fun alignUpTo(value: Int, multiple: Int): Int {
        if (multiple <= 0) return value
        val m = value % multiple
        return if (m == 0) value else value + (multiple - m)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isBlurEnabled()) {
            return
        }
        
        // Draw Blurred Background
        if (mBlurredBitmap != null && !mBlurredBitmap!!.isRecycled) {
            val srcW = mBlurContentWidth.coerceAtMost(mBlurredBitmap!!.width).coerceAtLeast(1)
            val srcH = mBlurContentHeight.coerceAtMost(mBlurredBitmap!!.height).coerceAtLeast(1)
            mRectSrc.set(0, 0, srcW, srcH)
            mRectDst.set(0, 0, width, height)
            canvas.drawBitmap(mBlurredBitmap!!, mRectSrc, mRectDst, mPaint)
        }
        
        // Draw Overlay
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mOverlayPaint)
    }

    private fun getActivity(): Activity? {
        var context = context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }
}

data class BlurStyle(
    val cornerRadiusDp: Float? = null,
    val blurRadius: Float? = null,
    val blurType: Int? = null,
    val overlayColor: Int? = null,
    val downscaleFactor: Float? = null
)

object BlurStyles {
    val BottomNav = BlurStyle(
        cornerRadiusDp = 28f,
        blurRadius = 20f,
        blurType = 0,
        overlayColor = 0x661A1A1A.toInt(),
        downscaleFactor = 0.3f
    )
    val BottomNavFab = BlurStyle(
        cornerRadiusDp = 28f,
        blurRadius = 20f,
        blurType = 0,
        overlayColor = 0x661A1A1A.toInt(),
        downscaleFactor = 0.3f
    )
    val BottomNavHighlight = BlurStyle(
        cornerRadiusDp = 28f,
        blurRadius = 20f,
        blurType = 0,
        overlayColor = 0x66C9F122,
        downscaleFactor = 0.3f
    )
}

fun RealtimeBlurView.applyStyle(style: BlurStyle) {
    style.cornerRadiusDp?.let {
        val radiusPx = it * resources.displayMetrics.density
        setCornerRadius(radiusPx)
    }
    style.blurRadius?.let { setBlurRadius(it) }
    style.blurType?.let { setBlurType(it) }
    style.overlayColor?.let { setOverlayColor(it) }
    style.downscaleFactor?.let { setDownscaleFactor(it) }
}

