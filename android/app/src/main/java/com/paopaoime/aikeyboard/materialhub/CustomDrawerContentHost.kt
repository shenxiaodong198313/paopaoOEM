package com.paopaoime.aikeyboard.materialhub

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerContentFactory
import com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerController
import com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerTheme

private const val LOADING_REVEAL_DELAY_MS = 200L

class CustomDrawerContentHost(
    private val activity: MaterialHubActivity,
    private val drawerId: String,
    private val defaultTitle: String,
    private val factory: OemDrawerContentFactory,
    private val onContentFailed: () -> Unit = {},
) : OemDrawerController {

    val root: FrameLayout
    private var titleView: TextView? = null
    private var backButton: ImageView? = null
    private var contentSlot: FrameLayout? = null
    var hostContentFailed: Boolean = false
        private set
    private var contentReady = false

    init {
        val ctx = activity
        root = FrameLayout(ctx).apply {
            val cornerRadius = dp(28f).toFloat()
            val background = GradientDrawable().apply {
                setColor(Color.parseColor("#F7F8FC"))
                cornerRadii = floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f)
            }
            setBackgroundDrawable(background)
        }
        root.post {
            if (!root.isAttachedToWindow) return@post
            val fullDrawer = runCatching { factory.onCreateFullDrawerView(ctx, this) }
            if (fullDrawer.isFailure) {
                reportHostContentError(fullDrawer.exceptionOrNull() ?: IllegalStateException("host full drawer error"))
                return@post
            }
            val fullDrawerView = fullDrawer.getOrNull()
            if (fullDrawerView != null) {
                root.removeAllViews()
                root.addView(
                    fullDrawerView,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                )
                onContentReady()
                return@post
            }
            installDefaultDrawerContent(ctx)
        }
    }

    private fun installDefaultDrawerContent(ctx: Context) {
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        val headerBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20f), dp(16f), dp(10f), dp(12f))
        }
        val backButtonView = ImageView(ctx).apply {
            setImageResource(org.fcitx.fcitx5.android.R.drawable.ic_favorite_panel_back)
            contentDescription = "返回"
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            setOnClickListener {
                if (!factory.onBackPressed()) {
                    activity.onDrawerBaseBack()
                }
            }
        }
        backButton = backButtonView
        val titleTextView = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#2A2251"))
            gravity = Gravity.CENTER
            text = defaultTitle
        }
        titleView = titleTextView
        val closeButton = ImageView(ctx).apply {
            setImageResource(org.fcitx.fcitx5.android.R.drawable.ic_favorite_panel_close)
            contentDescription = "关闭"
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            setOnClickListener { activity.closeMaterialHub() }
        }
        val buttonSize = dp(40f)
        headerBar.addView(
            backButtonView,
            LinearLayout.LayoutParams(buttonSize, buttonSize)
        )
        headerBar.addView(
            titleTextView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        )
        headerBar.addView(
            closeButton,
            LinearLayout.LayoutParams(buttonSize, buttonSize)
        )
        val contentFrame = FrameLayout(ctx)
        contentSlot = contentFrame
        column.addView(headerBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(70f)))
        column.addView(
            contentFrame,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        root.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val loadingView = buildLoadingView(ctx).apply { visibility = View.INVISIBLE }
        contentFrame.addView(
            loadingView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        contentFrame.post {
            if (!contentFrame.isAttachedToWindow) return@post
            val created = runCatching { factory.onCreateContentView(ctx, this) }
            if (created.isSuccess) {
                contentFrame.removeAllViews()
                contentFrame.addView(
                    created.getOrThrow(),
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                )
                onContentReady()
            } else {
                contentFrame.removeAllViews()
                reportHostContentError(created.exceptionOrNull() ?: IllegalStateException("host content error"))
            }
        }
        contentFrame.postDelayed({
            if (loadingView.parent != null && loadingView.visibility != View.VISIBLE) {
                loadingView.visibility = View.VISIBLE
            }
        }, LOADING_REVEAL_DELAY_MS)
    }

    private fun reportHostContentError(error: Throwable) {
        hostContentFailed = true
        root.removeAllViews()
        factory.onHostContentError(error)
        onContentFailed()
    }

    private fun onContentReady() {
        contentReady = true
        clipboardReplyState()?.let(::notifyClipboardReplyState)
    }

    fun notifyClipboardReplyState(state: com.paopaoime.aikeyboard.sdk.core.drawer.OemClipboardReplyState) {
        if (!contentReady || hostContentFailed) return
        runCatching { factory.onClipboardReplyStateChanged(state) }.onFailure(::reportHostContentError)
    }

    override fun clipboardReplyState(): com.paopaoime.aikeyboard.sdk.core.drawer.OemClipboardReplyState? =
        activity.clipboardReplyState()

    override fun regenerateClipboardReply(): Boolean = activity.regenerateClipboardReply()

    override fun insertClipboardReply(text: String): Boolean = activity.insertClipboardReply(text)

    fun handleBackPressed(): Boolean = factory.onBackPressed()

    fun notifyStateChanged(state: com.paopaoime.aikeyboard.sdk.core.drawer.OemDrawerState) {
        runCatching { factory.onDrawerStateChanged(state) }
    }

    override fun closeDrawer() {
        activity.closeMaterialHub()
    }

    override fun openDrawer(drawerId: String) {
        activity.requestOpenDrawer(drawerId)
    }

    override fun setTitle(title: CharSequence?) {
        titleView?.text = title?.takeIf { it.isNotBlank() } ?: defaultTitle
    }

    override fun setBackVisible(visible: Boolean) {
        backButton?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    override fun currentTheme(): OemDrawerTheme = OemDrawerTheme.LIGHT

    private fun buildLoadingView(context: Context): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ProgressBar(context))
            addView(
                TextView(context).apply {
                    text = "正在加载宿主自定义视图..."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(Color.parseColor("#6B6580"))
                    gravity = Gravity.CENTER
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.topMargin = dp(12f)
                    layoutParams = params
                }
            )
        }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            activity.resources.displayMetrics
        ).toInt()
}
