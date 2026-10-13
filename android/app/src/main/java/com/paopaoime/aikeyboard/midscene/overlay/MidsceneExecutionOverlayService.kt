package com.paopaoime.aikeyboard.midscene.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.midscene.agent.MidsceneUiPreferences
import com.paopaoime.aikeyboard.ime.ui.activity.OemMainActivity
import com.paopaoime.aikeyboard.midscene.agent.MidsceneTaskControlCenter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MidsceneExecutionOverlayService : Service() {
    private val overlayScale = 0.6f

    data class OverlayStepState(
        val text: String = "",
        val status: String = "completed"
    )

    data class OverlayUiState(
        val title: String = "泡泡AI智能体正在工作",
        val subtitle: String = "请勿触碰屏幕，否则任务将失败",
        val progressText: String = "正在初始化自动化任务",
        val progressFraction: Float = 0.08f,
        val tokenText: String = "当前已消耗Token：0",
        val fullscreenMask: Boolean = false,
        val steps: List<OverlayStepState> = emptyList(),
        val presentationStyle: String = PRESENTATION_STYLE_DYNAMIC_ISLAND
    )

    companion object {
        const val PRESENTATION_STYLE_DYNAMIC_ISLAND = "dynamic_island"
        const val PRESENTATION_STYLE_LEGACY_PANEL = "legacy_panel"

        private const val ACTION_SHOW = "com.paopaoime.aikeyboard.midscene.overlay.SHOW"
        private const val ACTION_HIDE = "com.paopaoime.aikeyboard.midscene.overlay.HIDE"
        private const val ACTION_STOP = "com.paopaoime.aikeyboard.midscene.overlay.STOP"
        private const val ACTION_SET_CAPTURE_SUPPRESSED =
            "com.paopaoime.aikeyboard.midscene.overlay.SET_CAPTURE_SUPPRESSED"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val EXTRA_PROGRESS_TEXT = "progress_text"
        private const val EXTRA_PROGRESS_FRACTION = "progress_fraction"
        private const val EXTRA_TOKEN_TEXT = "token_text"
        private const val EXTRA_FULLSCREEN_MASK = "fullscreen_mask"
        private const val EXTRA_STEP_ITEMS = "step_items"
        private const val EXTRA_PRESENTATION_STYLE = "presentation_style"
        private const val EXTRA_SUPPRESSED = "suppressed"
        private const val EXTRA_BORDER_ONLY = "border_only"
        private const val EXTRA_SHOW_REQUEST_ID = "show_request_id"
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val CHANNEL_ID = "midscene_execution_overlay"
        private const val NOTIFICATION_ID = 0x4D53

        private val captureSuppressionRequestId = AtomicInteger(1)
        private val captureSuppressionAcks = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()
        private val showRequestId = AtomicInteger(1)
        private val showAcks = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()

        fun show(context: Context, message: String) {
            val state = OverlayUiState(progressText = message)
            show(context, state)
        }

        fun show(
            context: Context,
            state: OverlayUiState,
            captureSuppressed: Boolean = false,
            borderOnly: Boolean = false
        ) {
            val intent = Intent(context, MidsceneExecutionOverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_MESSAGE, state.progressText)
                putExtra(EXTRA_TITLE, state.title)
                putExtra(EXTRA_SUBTITLE, state.subtitle)
                putExtra(EXTRA_PROGRESS_TEXT, state.progressText)
                putExtra(EXTRA_PROGRESS_FRACTION, state.progressFraction.coerceIn(0f, 1f))
                putExtra(EXTRA_TOKEN_TEXT, state.tokenText)
                putExtra(EXTRA_FULLSCREEN_MASK, state.fullscreenMask)
                putExtra(EXTRA_PRESENTATION_STYLE, state.presentationStyle)
                putStringArrayListExtra(
                    EXTRA_STEP_ITEMS,
                    ArrayList(state.steps.map { "${it.status}\u0001${it.text}" })
                )
                putExtra(EXTRA_SUPPRESSED, captureSuppressed)
                putExtra(EXTRA_BORDER_ONLY, borderOnly)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        suspend fun showAndAwaitNextFrame(
            context: Context,
            state: OverlayUiState,
            captureSuppressed: Boolean = false,
            borderOnly: Boolean = false,
            timeoutMs: Long = 320L
        ) {
            val requestId = showRequestId.getAndIncrement()
            val ack = CompletableDeferred<Unit>()
            showAcks[requestId] = ack
            val intent = Intent(context, MidsceneExecutionOverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_MESSAGE, state.progressText)
                putExtra(EXTRA_TITLE, state.title)
                putExtra(EXTRA_SUBTITLE, state.subtitle)
                putExtra(EXTRA_PROGRESS_TEXT, state.progressText)
                putExtra(EXTRA_PROGRESS_FRACTION, state.progressFraction.coerceIn(0f, 1f))
                putExtra(EXTRA_TOKEN_TEXT, state.tokenText)
                putExtra(EXTRA_FULLSCREEN_MASK, state.fullscreenMask)
                putExtra(EXTRA_PRESENTATION_STYLE, state.presentationStyle)
                putStringArrayListExtra(
                    EXTRA_STEP_ITEMS,
                    ArrayList(state.steps.map { "${it.status}\u0001${it.text}" })
                )
                putExtra(EXTRA_SUPPRESSED, captureSuppressed)
                putExtra(EXTRA_BORDER_ONLY, borderOnly)
                putExtra(EXTRA_SHOW_REQUEST_ID, requestId)
            }
            val started = runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
            if (!started) {
                showAcks.remove(requestId)
                return
            }
            withTimeoutOrNull(timeoutMs) { ack.await() }
            showAcks.remove(requestId)
        }

        fun hide(context: Context) {
            val intent = Intent(context, MidsceneExecutionOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            runCatching { context.startService(intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MidsceneExecutionOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }

        fun setCaptureSuppressed(context: Context, suppressed: Boolean) {
            val intent = Intent(context, MidsceneExecutionOverlayService::class.java).apply {
                action = ACTION_SET_CAPTURE_SUPPRESSED
                putExtra(EXTRA_SUPPRESSED, suppressed)
            }
            runCatching { context.startService(intent) }
        }

        suspend fun setCaptureSuppressedAndAwaitNextFrame(
            context: Context,
            suppressed: Boolean,
            timeoutMs: Long = 120L
        ) {
            val requestId = captureSuppressionRequestId.getAndIncrement()
            val ack = CompletableDeferred<Unit>()
            captureSuppressionAcks[requestId] = ack
            val intent = Intent(context, MidsceneExecutionOverlayService::class.java).apply {
                action = ACTION_SET_CAPTURE_SUPPRESSED
                putExtra(EXTRA_SUPPRESSED, suppressed)
                putExtra(EXTRA_REQUEST_ID, requestId)
            }
            val started = runCatching { context.startService(intent) }.isSuccess
            if (!started) {
                captureSuppressionAcks.remove(requestId)
                return
            }
            withTimeoutOrNull(timeoutMs) { ack.await() }
            captureSuppressionAcks.remove(requestId)
        }

        private fun completeCaptureSuppressionAck(requestId: Int) {
            if (requestId <= 0) return
            captureSuppressionAcks.remove(requestId)?.complete(Unit)
        }

        private fun completeShowAck(requestId: Int) {
            if (requestId <= 0) return
            showAcks.remove(requestId)?.complete(Unit)
        }
    }

    private val baseOverlayFlags =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private val waveformBaseHeightsDp = listOf(20, 14, 17, 24, 19, 14, 18, 24, 27, 21, 18, 13, 7, 6, 5, 5, 5, 5)

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var rootContainer: FrameLayout? = null
    private var islandContainer: View? = null
    private var neonBorderView: NeonBorderView? = null
    private var robotIconView: ImageView? = null
    private var timerTextView: TextView? = null
    private val waveformBars = mutableListOf<View>()
    private var neonBorderAnimator: ObjectAnimator? = null
    private var robotSwingAnimator: AnimatorSet? = null
    private val waveformAnimators = mutableListOf<ObjectAnimator>()
    private var currentUiState = OverlayUiState()
    private var captureSuppressed = false
    private var borderOnly = false
    private var overlayActive = false
    private var sessionStartRealtimeMs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timerTicker = object : Runnable {
        override fun run() {
            updateTimerText()
            mainHandler.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_STOP -> {
                MidsceneTaskControlCenter.requestStopFromOverlay("overlay_service_action_stop")
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_SET_CAPTURE_SUPPRESSED -> {
                captureSuppressed = intent.getBooleanExtra(EXTRA_SUPPRESSED, false)
                applyCaptureSuppressedState()
                val requestId = intent.getIntExtra(EXTRA_REQUEST_ID, 0)
                val overlay = overlayView
                if (requestId > 0) {
                    if (overlay != null) {
                        overlay.postOnAnimation {
                            overlay.postOnAnimation {
                                completeCaptureSuppressionAck(requestId)
                            }
                        }
                    } else {
                        completeCaptureSuppressionAck(requestId)
                    }
                }
                return START_STICKY
            }

            ACTION_SHOW, null -> {
                val nextState = parseUiState(intent)
                captureSuppressed = intent?.getBooleanExtra(EXTRA_SUPPRESSED, captureSuppressed) == true
                borderOnly = intent?.getBooleanExtra(EXTRA_BORDER_ONLY, borderOnly) == true
                val shouldRebuild = overlayView != null &&
                    nextState.presentationStyle != currentUiState.presentationStyle
                if (shouldRebuild) {
                    removeOverlay()
                }
                currentUiState = nextState
                val notification = buildNotification(currentUiState)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                showOrUpdateOverlay(currentUiState)
                val requestId = intent?.getIntExtra(EXTRA_SHOW_REQUEST_ID, 0) ?: 0
                val overlay = overlayView
                if (requestId > 0) {
                    if (overlay != null) {
                        overlay.postOnAnimation {
                            overlay.postOnAnimation {
                                completeShowAck(requestId)
                            }
                        }
                    } else {
                        completeShowAck(requestId)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun parseUiState(intent: Intent?): OverlayUiState {
        val legacyMessage = intent?.getStringExtra(EXTRA_MESSAGE)?.trim().orEmpty()
        val title = intent?.getStringExtra(EXTRA_TITLE)?.trim()
            ?.ifBlank { null }
            ?: getString(R.string.midscene_overlay_title)
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE)?.trim()
            ?.ifBlank { null }
            ?: getString(R.string.midscene_overlay_subtitle)
        val progressText = intent?.getStringExtra(EXTRA_PROGRESS_TEXT)?.trim()
            ?.ifBlank { null }
            ?: legacyMessage.ifBlank { getString(R.string.midscene_overlay_progress_default) }
        val tokenText = intent?.getStringExtra(EXTRA_TOKEN_TEXT)?.trim()
            ?.ifBlank { null }
            ?: getString(R.string.midscene_overlay_token_placeholder)
        val progressFraction = intent?.getFloatExtra(EXTRA_PROGRESS_FRACTION, 0.08f) ?: 0.08f
        val fullscreenMask = intent?.getBooleanExtra(EXTRA_FULLSCREEN_MASK, false) == true
        val steps = intent?.getStringArrayListExtra(EXTRA_STEP_ITEMS)
            ?.mapNotNull { encoded ->
                val parts = encoded.split('\u0001', limit = 2)
                val status = parts.getOrNull(0)?.trim().orEmpty()
                val text = parts.getOrNull(1)?.trim().orEmpty()
                if (text.isBlank()) null else {
                    OverlayStepState(text = text, status = status.ifBlank { "completed" })
                }
            }
            .orEmpty()
        return OverlayUiState(
            title = title,
            subtitle = subtitle,
            progressText = progressText,
            progressFraction = progressFraction.coerceIn(0f, 1f),
            tokenText = tokenText,
            fullscreenMask = fullscreenMask,
            steps = steps,
            presentationStyle = intent?.getStringExtra(EXTRA_PRESENTATION_STYLE)
                ?.trim()
                ?.ifBlank { null }
                ?: PRESENTATION_STYLE_DYNAMIC_ISLAND
        )
    }

    private fun showOrUpdateOverlay(state: OverlayUiState) {
        if (!Settings.canDrawOverlays(this)) return
        ensureOverlayView(state.presentationStyle)
        if (sessionStartRealtimeMs <= 0L) {
            sessionStartRealtimeMs = SystemClock.elapsedRealtime()
        }
        applyUiState(state)
        applyCaptureSuppressedState()
        startRuntimeEffects()
    }

    private fun ensureOverlayView(presentationStyle: String) {
        if (overlayView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = when (presentationStyle) {
            PRESENTATION_STYLE_LEGACY_PANEL -> buildDynamicIslandOverlayView()
            else -> buildDynamicIslandOverlayView()
        }
        view.setOnApplyWindowInsetsListener { v, insets ->
            v.post {
                updateOverlayPosition()
                updateNeonBorderGeometry()
            }
            insets
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseOverlayFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        wm.addView(view, params)
        windowManager = wm
        overlayView = view
        overlayLayoutParams = params
        overlayActive = true
        view.post {
            updateOverlayPosition()
            updateNeonBorderGeometry()
        }
        applyUiState(currentUiState)
        applyCaptureSuppressedState()
    }

    private fun buildDynamicIslandOverlayView(): View {
        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        rootContainer = root
        val neonBorder = NeonBorderView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        neonBorderView = neonBorder
        root.addView(neonBorder)

        val island = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                dp(84),
                dp(57),
                Gravity.TOP or Gravity.START
            )
            clipChildren = false
            clipToPadding = false
            isClickable = false
            isFocusable = false
            elevation = 0f
            translationZ = 0f
        }
        islandContainer = island

        val stopButton = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                dp(84),
                dp(38),
                Gravity.START or Gravity.BOTTOM
            )
            setPadding(dp(7), dp(6), dp(10), dp(6))
            isClickable = true
            isFocusable = true
            contentDescription = "终止任务"
            background = GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                intArrayOf(
                    Color.parseColor("#1ED07D"),
                    Color.parseColor("#9BF36B")
                )
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(40).toFloat()
            }
            setOnClickListener {
                MidsceneTaskControlCenter.requestStopFromOverlay("overlay_button")
                stopSelf()
            }
        }
        val pauseIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                dp(26),
                dp(26),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
            setImageResource(R.drawable.midscene_overlay_pause_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val stopLabel = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL or Gravity.END
            ).apply {
                marginEnd = dp(11)
            }
            includeFontPadding = false
            text = "终止"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(dp(4).toFloat(), 0f, dp(2).toFloat(), Color.parseColor("#9911CC75"))
        }
        stopButton.addView(pauseIcon)
        stopButton.addView(stopLabel)

        val robotIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                dp(33),
                dp(40),
                Gravity.TOP or Gravity.END
            ).apply {
                marginEnd = dp(5)
            }
            setImageResource(R.drawable.midscene_overlay_robot)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        robotIcon.pivotX = dp(16.5f).toFloat()
        robotIcon.pivotY = dp(28f).toFloat()
        robotIconView = robotIcon

        timerTextView = null
        waveformBars.clear()
        island.addView(stopButton)
        island.addView(robotIcon)
        root.addView(island)
        return root
    }

    private fun applyUiState(state: OverlayUiState) {
        currentUiState = state
        updateTimerText()
        updateNeonBorderGeometry()
    }

    private fun applyCaptureSuppressedState() {
        val view = overlayView ?: return
        val params = overlayLayoutParams
        val overlayPassthrough = captureSuppressed || borderOnly
        val neonEnabled = borderOnly || MidsceneUiPreferences.isOverlayNeonEnabled(this)
        val desiredFlags = baseOverlayFlags or
            if (overlayPassthrough) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
        if (params != null && params.flags != desiredFlags) {
            params.flags = desiredFlags
            runCatching { windowManager?.updateViewLayout(view, params) }
        }
        rootContainer?.alpha = 1f
        // 截图期隐藏边框和停止悬浮窗，避免污染聊天截图。
        neonBorderView?.alpha = if (neonEnabled && !captureSuppressed) 1f else 0f
        islandContainer?.alpha = if (overlayPassthrough) 0f else 1f
        islandContainer?.visibility = if (overlayPassthrough) View.INVISIBLE else View.VISIBLE
        view.isEnabled = !overlayPassthrough
        view.importantForAccessibility = if (overlayPassthrough) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
    }

    private fun startRuntimeEffects() {
        val neonEnabled = borderOnly || MidsceneUiPreferences.isOverlayNeonEnabled(this)
        val borderView = neonBorderView
        if (borderView != null) {
            // 更新状态或重启动画也必须保留截图期间的隐藏状态。
            borderView.alpha = if (neonEnabled && !captureSuppressed) 1f else 0f
            if (neonEnabled) {
                if (neonBorderAnimator == null) {
                    neonBorderAnimator = ObjectAnimator.ofFloat(borderView, "gradientRotation", 0f, 360f).apply {
                        duration = 3600L
                        repeatCount = ObjectAnimator.INFINITE
                        repeatMode = ObjectAnimator.RESTART
                        interpolator = null
                    }
                }
                val animator = neonBorderAnimator
                if (animator != null) {
                    if (!animator.isStarted) {
                        animator.start()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && animator.isPaused) {
                        animator.resume()
                    }
                }
            } else {
                neonBorderAnimator?.cancel()
                neonBorderAnimator = null
            }
        }
        if (waveformAnimators.isEmpty()) {
            waveformBars.forEachIndexed { index, bar ->
                val animator = ObjectAnimator.ofFloat(
                    bar,
                    View.SCALE_Y,
                    0.62f,
                    1.16f,
                    0.7f
                ).apply {
                    duration = (780L + (index % 5) * 110L)
                    startDelay = (index * 70L) % 360L
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.RESTART
                    interpolator = AccelerateDecelerateInterpolator()
                }
                animator.start()
                waveformAnimators += animator
            }
        } else {
            waveformAnimators.forEach { animator ->
                if (!animator.isStarted) {
                    animator.start()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && animator.isPaused) {
                    animator.resume()
                }
            }
        }
        val robotView = robotIconView
        if (robotView != null) {
            if (robotSwingAnimator == null) {
                val elasticInterpolator = PathInterpolator(0.22f, 1.2f, 0.3f, 1f)
                val settleInterpolator = PathInterpolator(0.32f, 0f, 0.18f, 1f)
                val rotateAnimator = ObjectAnimator.ofFloat(
                    robotView,
                    View.ROTATION,
                    0f,
                    -6f,
                    5f,
                    -4f,
                    3f,
                    0f
                ).apply {
                    duration = 1450L
                    interpolator = elasticInterpolator
                }
                val shiftAnimator = ObjectAnimator.ofFloat(
                    robotView,
                    View.TRANSLATION_X,
                    0f,
                    -dp(1.8f).toFloat(),
                    dp(1.6f).toFloat(),
                    -dp(1.2f).toFloat(),
                    dp(0.8f).toFloat(),
                    0f
                ).apply {
                    duration = 1450L
                    interpolator = elasticInterpolator
                }
                val holdRotationAnimator = ObjectAnimator.ofFloat(robotView, View.ROTATION, 0f, 0f).apply {
                    duration = 900L
                    interpolator = settleInterpolator
                }
                val holdShiftAnimator = ObjectAnimator.ofFloat(robotView, View.TRANSLATION_X, 0f, 0f).apply {
                    duration = 900L
                    interpolator = settleInterpolator
                }
                val swingSet = AnimatorSet().apply {
                    playTogether(rotateAnimator, shiftAnimator)
                }
                val holdSet = AnimatorSet().apply {
                    playTogether(holdRotationAnimator, holdShiftAnimator)
                }
                robotSwingAnimator = AnimatorSet().apply {
                    playSequentially(swingSet, holdSet)
                    startDelay = 260L
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (overlayActive && robotIconView != null) {
                                animation.start()
                            }
                        }
                    })
                }
            }
            val animator = robotSwingAnimator
            if (animator != null && !animator.isStarted) {
                animator.start()
            }
        }
        mainHandler.removeCallbacks(timerTicker)
        mainHandler.post(timerTicker)
    }

    private fun updateTimerText() {
        val start = sessionStartRealtimeMs.takeIf { it > 0L } ?: SystemClock.elapsedRealtime()
        val elapsedMs = max(0L, SystemClock.elapsedRealtime() - start)
        timerTextView?.text = formatElapsedDuration(elapsedMs)
    }

    private fun updateOverlayPosition() {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val island = islandContainer ?: return
        val width = island.width.takeIf { it > 0 } ?: return
        val height = island.height.takeIf { it > 0 } ?: return

        val bottomInset = resolveBottomInset()
        val reservedBottomSpace = max(bottomInset + sdp(72), (screenHeight * 0.12f).roundToInt())
        val lowerHalfMidpointTop = (screenHeight * 0.75f).roundToInt() - (height / 2)
        val maxAllowedTop = screenHeight - reservedBottomSpace - height
        val minAllowedTop = (screenHeight * 0.5f).roundToInt()
        val targetY = lowerHalfMidpointTop
            .coerceAtLeast(minAllowedTop)
            .coerceAtMost(maxAllowedTop.coerceAtLeast(0))
        val params = island.layoutParams as? FrameLayout.LayoutParams ?: return
        val horizontalPadding = max(dp(16), (screenWidth * 0.04f).roundToInt())
        val targetStartMargin = (screenWidth - width - horizontalPadding).coerceAtLeast(dp(12))
        if (params.topMargin == targetY &&
            params.marginStart == targetStartMargin &&
            params.marginEnd == 0
        ) {
            return
        }
        params.topMargin = targetY
        params.marginStart = targetStartMargin
        params.marginEnd = 0
        island.layoutParams = params
    }

    private fun removeOverlay() {
        overlayActive = false
        mainHandler.removeCallbacks(timerTicker)
        neonBorderAnimator?.cancel()
        neonBorderAnimator = null
        robotSwingAnimator?.cancel()
        robotSwingAnimator = null
        waveformAnimators.forEach { it.cancel() }
        waveformAnimators.clear()
        waveformBars.clear()
        val wm = windowManager
        val view = overlayView
        if (wm != null && view != null) {
            runCatching { wm.removeView(view) }
        }
        overlayView = null
        overlayLayoutParams = null
        rootContainer = null
        islandContainer = null
        neonBorderView = null
        robotIconView = null
        timerTextView = null
        windowManager = null
        sessionStartRealtimeMs = 0L
    }

    private fun buildNotification(state: OverlayUiState): Notification {
        ensureNotificationChannel()
        val launchIntent = Intent(this, OemMainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.midscene_overlay_notification_title))
            .setContentText(state.progressText.ifBlank { "Midscene 任务执行中" })
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Midscene 执行提示",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "展示 Midscene 执行中的系统悬浮提示"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun openMidscenePanel() {
        val launchIntent = Intent(this, OemMainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(launchIntent) }
    }

    private fun formatElapsedDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun resolveBottomInset(): Int {
        val rootInsets = overlayView?.rootWindowInsets
        if (rootInsets != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return rootInsets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom
            }
            @Suppress("DEPRECATION")
            return rootInsets.stableInsetBottom
        }
        return 0
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private fun dp(value: Float): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private fun updateNeonBorderGeometry() {
        val borderView = neonBorderView ?: return
        val roundedRadius = resolveScreenCornerRadius()
        val thickness = max(sdp(8), dp(4))
        borderView.setBorderThickness(thickness.toFloat())
        borderView.setCornerRadiusPx(max(roundedRadius, thickness * 2).toFloat())
    }

    private fun resolveScreenCornerRadius(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val insets = overlayView?.rootWindowInsets
            val radii = listOfNotNull(
                insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius,
                insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT)?.radius,
                insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius,
                insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_LEFT)?.radius
            )
            if (radii.isNotEmpty()) {
                return radii.maxOrNull() ?: 0
            }
        }
        return 0
    }

    private fun sdp(value: Int): Int {
        return max(1, (dp(value) * overlayScale).roundToInt())
    }
}

private class NeonBorderView(context: Context) : View(context) {
    private val borderRect = RectF()
    private val shaderMatrix = Matrix()
    private var shader: SweepGradient? = null
    private var strokeWidthPx = 8f
    private var cornerRadiusPx = 0f

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val colors = intArrayOf(
        Color.parseColor("#FFFF3B30"),
        Color.parseColor("#FFFF9500"),
        Color.parseColor("#FFFFD60A"),
        Color.parseColor("#FF30D158"),
        Color.parseColor("#FF32D7FF"),
        Color.parseColor("#FF0A84FF"),
        Color.parseColor("#FF5E5CE6"),
        Color.parseColor("#FFBF5AF2"),
        Color.parseColor("#FFFF2D55"),
        Color.parseColor("#FFFF3B30")
    )
    private val colorPositions = floatArrayOf(0f, 0.12f, 0.24f, 0.36f, 0.5f, 0.64f, 0.76f, 0.88f, 0.96f, 1f)

    var gradientRotation: Float = 0f
        set(value) {
            field = value
            updateShaderMatrix()
            invalidate()
        }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        alpha = 1f
    }

    fun setBorderThickness(value: Float) {
        if (strokeWidthPx == value) return
        strokeWidthPx = value
        glowPaint.strokeWidth = value * 1.35f
        corePaint.strokeWidth = value
        glowPaint.maskFilter = BlurMaskFilter(value * 2.2f, BlurMaskFilter.Blur.NORMAL)
        invalidate()
    }

    fun setCornerRadiusPx(value: Float) {
        if (cornerRadiusPx == value) return
        cornerRadiusPx = value
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        shader = SweepGradient(w / 2f, h / 2f, colors, colorPositions)
        glowPaint.shader = shader
        corePaint.shader = shader
        updateShaderMatrix()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokeWidthPx * 0.5f
        borderRect.set(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(borderRect, cornerRadiusPx, cornerRadiusPx, glowPaint)
        canvas.drawRoundRect(borderRect, cornerRadiusPx, cornerRadiusPx, corePaint)
    }

    private fun updateShaderMatrix() {
        val localShader = shader ?: return
        shaderMatrix.reset()
        shaderMatrix.setRotate(gradientRotation, width / 2f, height / 2f)
        localShader.setLocalMatrix(shaderMatrix)
        glowPaint.shader = localShader
        corePaint.shader = localShader
    }
}
