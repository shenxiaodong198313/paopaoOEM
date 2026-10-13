package org.fcitx.fcitx5.android.input.materialhub

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SweepGradient
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setPadding
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.input.favorite.FavoriteEmbeddedPickerItem
import org.fcitx.fcitx5.android.input.favorite.FavoriteEmbeddedPickerStore
import org.fcitx.fcitx5.android.input.favorite.FavoritePickerType
import org.fcitx.fcitx5.android.input.favorite.FavoriteUi

class AiCommunicatePanelController(
    context: Context,
    availableTones: List<Tone>,
    private val onRequestClose: (afterClosed: (() -> Unit)?) -> Unit,
    private val onBackToStart: () -> Unit,
    private val onEntrySelected: (GenerateMode) -> Unit,
    private val onPlaceholderSelected: (PlaceholderAction) -> Unit,
    private val onRequestPermission: () -> Unit,
    private val onRequestImagePermission: ((Boolean) -> Unit) -> Unit,
    private val onImageReplySelected: (List<Uri>) -> Unit,
    private val onToneSelected: (Tone) -> Unit,
    private val onRegenerate: () -> Unit,
    private val onReplySelected: (Int, String) -> Unit,
    private val footerTextProvider: () -> String? = { DEFAULT_FOOTER_NOTE },
    private val onFooterClick: () -> Boolean = { false },
) {
    private fun resolveOptionalDrawable(name: String): Int? {
        val resId = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        return resId.takeIf { it != 0 }
    }

    data class Tone(
        val key: String,
        val label: String,
        val promptHint: String,
    ) {
        companion object {
            fun defaultPresets(): List<Tone> {
                return listOf(
                    Tone(
                        key = "polite",
                        label = "礼貌回复",
                        promptHint = "语气礼貌、克制、清晰，适合商务或不太熟的关系，不要显得生硬。",
                    ),
                    Tone(
                        key = "natural",
                        label = "自然沟通",
                        promptHint = "语气自然、场景通用、像真人即时回复，不油腻，不端着。",
                    ),
                    Tone(
                        key = "deal",
                        label = "推动成交",
                        promptHint = "在自然礼貌前提下更偏向推进沟通、确认意向、促成下一步，但不要压迫感太强。",
                    ),
                )
            }
        }
    }

    enum class GenerateMode(val label: String, val intro: String) {
        FAST_LLM("极速生成", "自动读取当前聊天内容，直接生成更自然的回复建议，适合当前对话立即开始。"),
        OCR("识屏回复", "先识别屏幕聊天内容，再结合上下文生成回复建议。"),
        IMAGE_REPLY("传图回复", "选择聊天截图或会话图片后，直接识别图片中的会话内容并生成回复建议。"),
        PASTE("复制粘贴", "根据粘贴的内容生成回复。"),
    }

    enum class PlaceholderAction {
        PASTE_SESSION,
        UPLOAD_IMAGE,
    }

    enum class StepStatus {
        TODO,
        ACTIVE,
        DONE,
    }

    data class GeneratingStep(
        val title: String,
        val detail: String,
        val status: StepStatus,
    )

    private data class TimelineStepUiModel(
        val number: Int,
        val title: String,
        val detail: String,
        val status: StepStatus,
    )

    private data class TimelinePageUiModel(
        val steps: List<TimelineStepUiModel>,
    )

    private enum class Page {
        START,
        RESTRICTED,
        IMAGE_PICKER,
        PREPARING,
        PERMISSION,
        GENERATING,
        RESULT,
        ERROR,
    }

    private data class PanelState(
        val page: Page = Page.START,
        val selectedTone: Tone,
        val activeMode: GenerateMode? = null,
        val introText: String = "",
        val preparingTitle: String = "",
        val preparingDetail: String = "",
        val preparingProgress: Int = 0,
        val permissionHint: String = "",
        val generatingTitle: String = "",
        val generatingDetail: String = "",
        val generatingProgress: Int = 0,
        val generatingSteps: List<GeneratingStep> = emptyList(),
        val replies: List<String> = emptyList(),
        val resultRefreshing: Boolean = false,
        val resultRefreshingMessage: String = "",
        val errorMessage: String? = null,
        val errorTitle: String = "",
        val errorDescription: String = "",
        val errorRetryLoading: Boolean = false,
        val restrictedTitle: String = "",
        val isPasteGenerating: Boolean = false,
        val imagePickerItems: List<FavoriteEmbeddedPickerItem> = emptyList(),
        val selectedImageUris: List<Uri> = emptyList(),
        val imagePickerLoading: Boolean = false,
        val imagePickerPermissionDenied: Boolean = false,
    )

    private val animatedNeonViews = mutableListOf<AnimatedNeonBorderView>()

    private var errorRetryFeedbackRunnable: Runnable? = null
    private val embeddedPickerStore = FavoriteEmbeddedPickerStore(context)
    private val favoriteUi = FavoriteUi(context)
    private val toneOptions = availableTones.ifEmpty { Tone.defaultPresets() }
    private val maxImageReplySelection = resolveMaxImageReplySelection(context)
    private var state = PanelState(selectedTone = toneOptions.first())

    private fun registerNeonView(view: AnimatedNeonBorderView) {
        animatedNeonViews.add(view)
    }

    private fun unregisterNeonView(view: AnimatedNeonBorderView) {
        animatedNeonViews.remove(view)
    }

    fun pauseAnimations() {
        animatedNeonViews.forEach { it.pauseAnimation() }
    }

    fun resumeAnimations() {
        animatedNeonViews.forEach { it.resumeAnimation() }
    }
    private val ctx = context
    private val panelBackgroundTopInset = dp(18)
    private var imagePickerItemsCache: List<FavoriteEmbeddedPickerItem> = emptyList()
    private var imagePickerVisibleCount = 0
    private var imagePickerScrollY = 0
    private var imagePickerLoadVersion = 0
    private var imagePickerAllLoaded = false
    private var imagePickerLoadingMore = false
    private var imagePickerRecyclerBinding: FavoriteUi.EmbeddedMediaPickerRecyclerBinding? = null

    private val titleView = TextView(ctx).apply {
        id = View.generateViewId()
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#2A2251"))
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
    }
    private val introView = TextView(ctx).apply {
        setTextColor(Color.parseColor("#6D7480"))
        textSize = 13f
        setLineSpacing(0f, 1.25f)
    }
    private val backButton =
        buildHeaderButton(
            contentDescription = "返回",
            iconRes = R.drawable.ic_favorite_panel_back,
        ).apply {
            id = View.generateViewId()
        }
    private val closeButton =
        buildHeaderButton(
            contentDescription = "关闭",
            iconRes = R.drawable.ic_favorite_panel_close,
        )
    private val headerActionButton = TextView(ctx).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        background = roundedFill("#DADBDD", 999)
        text = "确定"
        minWidth = dp(72)
        minHeight = dp(36)
        setPadding(dp(18), 0, dp(18), 0)
        visibility = View.GONE
    }
    private val contentScrollView = ScrollView(ctx).apply {
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        isFillViewport = true
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )
    }
    private val timelineContainer = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )
    }
    private val contentContainer = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), 0, dp(18), 0)
    }
    private val footerContainer = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), 0, dp(18), 0)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(12)
        }
    }

    private val panelBackgroundView = ImageView(ctx).apply {
        setImageResource(R.drawable.bg_favorite_panel_rectangle)
        scaleType = ImageView.ScaleType.FIT_XY
    }
    private val headerBar = FrameLayout(ctx).apply {
        setPadding(dp(20), dp(16), dp(10), dp(12))
        addView(
            backButton,
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START or Gravity.CENTER_VERTICAL),
        )
        addView(
            titleView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        addView(
            closeButton,
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END or Gravity.CENTER_VERTICAL),
        )
        addView(
            headerActionButton,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL,
            ).apply {
                rightMargin = dp(8)
            },
        )
    }
    private val panelContentView = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = topRoundedFill(28)
        clipToOutline = true
        setPadding(0, 0, 0, dp(24))
        addView(
            headerBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(70),
            ),
        )
        addView(introView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(12)
            marginStart = dp(18)
            marginEnd = dp(18)
        })
        contentScrollView.addView(
            contentContainer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(contentScrollView)
        addView(timelineContainer)
        addView(footerContainer)
    }
    val rootView: View = FrameLayout(ctx).apply {
        addView(
            panelBackgroundView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            panelContentView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                topMargin = panelBackgroundTopInset
            },
        )
    }

    val selectedTone: Tone
        get() = state.selectedTone

    val activeMode: GenerateMode?
        get() = state.activeMode

    val isShowingError: Boolean
        get() = state.page == Page.ERROR

    companion object {
        const val DEFAULT_ERROR_TITLE = "这次生成没有成功"
        const val DEFAULT_ERROR_DESCRIPTION = "请点击下方按钮重新生成"
        const val DEFAULT_FOOTER_NOTE = "回复内容由当前宿主配置的 AI 模型生成"
        const val ERROR_RETRY_FEEDBACK_MS = 1500L
        private const val DEFAULT_MAX_IMAGE_REPLY_SELECTION = 5
        private const val MIN_MAX_IMAGE_REPLY_SELECTION = 1
        private const val MAX_MAX_IMAGE_REPLY_SELECTION = 20
        private const val META_AI_REPLY_MAX_IMAGE_SELECTION = "com.paopaoime.oem.aiReplyMaxImageSelection"
        private const val UI_RUNTIME_PREFS_NAME = "com.paopaoime.oem.ui_runtime_config"
        private const val KEY_AI_REPLY_MAX_IMAGE_SELECTION = "ai_reply_max_image_selection"
    }

    init {
        backButton.setOnClickListener {
            if (state.page != Page.START) {
                onBackToStart()
            }
        }
        closeButton.setOnClickListener { onRequestClose(null) }
        headerActionButton.setOnClickListener {
            if (state.page == Page.IMAGE_PICKER && state.selectedImageUris.isNotEmpty() && !state.imagePickerLoading) {
                onImageReplySelected(state.selectedImageUris)
            }
        }
        showStart()
    }

    fun showStart() {
        state = PanelState(
            selectedTone = toneOptions.first(),
            page = Page.START,
            introText = "",
        )
        render()
    }

    fun showRestrictedState(title: String, message: String) {
        state = PanelState(
            selectedTone = state.selectedTone,
            page = Page.RESTRICTED,
            introText = "",
            errorMessage = message,
            restrictedTitle = title,
        )
        render()
    }

    fun startImageReplyPicker() {
        state = state.copy(
            page = Page.IMAGE_PICKER,
            activeMode = GenerateMode.IMAGE_REPLY,
            introText = "",
            imagePickerItems = emptyList(),
            selectedImageUris = emptyList(),
            imagePickerLoading = true,
            imagePickerPermissionDenied = false,
            errorMessage = null,
        )
        render()
        onRequestImagePermission { granted ->
            if (granted) {
                loadImagePickerItems()
            } else {
                state = state.copy(
                    page = Page.IMAGE_PICKER,
                    activeMode = GenerateMode.IMAGE_REPLY,
                    imagePickerLoading = false,
                    imagePickerPermissionDenied = true,
                    imagePickerItems = emptyList(),
                    selectedImageUris = emptyList(),
                )
                render()
            }
        }
    }

    private fun loadImagePickerItems() {
        val cachedItems = imagePickerItemsCache
        imagePickerScrollY = 0
        imagePickerVisibleCount = 0
        imagePickerAllLoaded = false
        imagePickerLoadingMore = false
        imagePickerRecyclerBinding = null
        if (cachedItems.isNotEmpty()) {
            ensureImagePickerVisibleCount(cachedItems.size)
            imagePickerAllLoaded = cachedItems.size < imagePickerLoadMoreBatchSize()
        }
        state = state.copy(
            page = Page.IMAGE_PICKER,
            activeMode = GenerateMode.IMAGE_REPLY,
            imagePickerLoading = cachedItems.isEmpty(),
            imagePickerPermissionDenied = false,
            imagePickerItems = cachedItems,
            selectedImageUris = emptyList(),
        )
        render()
        val loadVersion = ++imagePickerLoadVersion
        Thread {
            val loaded = embeddedPickerStore.loadPage(
                type = FavoritePickerType.IMAGE,
                limit = imagePickerLoadMoreBatchSize(),
                offset = 0,
            )
            rootView.post {
                if (loadVersion != imagePickerLoadVersion) return@post
                imagePickerItemsCache = loaded
                imagePickerAllLoaded = loaded.size < imagePickerLoadMoreBatchSize()
                resetImagePickerVisibleCount(loaded.size)
                if (state.page != Page.IMAGE_PICKER || state.activeMode != GenerateMode.IMAGE_REPLY) {
                    return@post
                }
                val availableUris = loaded.mapTo(linkedSetOf()) { it.uri }
                state = state.copy(
                    page = Page.IMAGE_PICKER,
                    activeMode = GenerateMode.IMAGE_REPLY,
                    imagePickerLoading = false,
                    imagePickerPermissionDenied = false,
                    imagePickerItems = loaded,
                    selectedImageUris = state.selectedImageUris.filter { it in availableUris },
                )
                render()
            }
        }.start()
    }

    private fun imagePickerInitialVisibleCount(): Int = 32

    private fun imagePickerLoadMoreBatchSize(): Int = 32

    private fun ensureImagePickerVisibleCount(totalCount: Int) {
        if (imagePickerVisibleCount > 0) {
            imagePickerVisibleCount = imagePickerVisibleCount.coerceAtMost(totalCount)
            return
        }
        imagePickerVisibleCount = imagePickerInitialVisibleCount().coerceAtMost(totalCount)
    }

    private fun resetImagePickerVisibleCount(totalCount: Int) {
        imagePickerVisibleCount = imagePickerInitialVisibleCount().coerceAtMost(totalCount)
    }

    private fun visibleImagePickerItems(): List<FavoriteEmbeddedPickerItem> {
        val visibleCount = imagePickerVisibleCount
            .takeIf { it > 0 }
            ?: imagePickerInitialVisibleCount().coerceAtMost(state.imagePickerItems.size)
        return state.imagePickerItems.take(visibleCount.coerceAtMost(state.imagePickerItems.size))
    }

    fun showPermission(mode: GenerateMode) {
        state = state.copy(
            page = Page.PERMISSION,
            activeMode = mode,
            introText = "读取当前聊天内容前，需要一次截屏授权来识别对话上下文。",
            permissionHint = "为了更准确理解这段对话的对象、语气和上下文，系统会在你确认后抓取当前聊天画面，用于本次回复建议生成。",
            errorMessage = null,
        )
        render()
    }

    fun showPreparing(
        mode: GenerateMode,
        tone: Tone = state.selectedTone,
        progress: Int,
    ) {
        state = state.copy(
            page = Page.PREPARING,
            activeMode = mode,
            selectedTone = tone,
            introText = "即将开始极速生成回复，Agent将自动查看当前聊天界面屏幕，请勿手动打断。",
            preparingTitle = "即将开始极速生成回复",
            preparingDetail = "Agent 将自动读取当前聊天界面，并在倒计时结束后收回抽屉开始滑动截屏。",
            preparingProgress = progress.coerceIn(0, 100),
            errorMessage = null,
        )
        render()
    }

    fun showGenerating(
        mode: GenerateMode,
        tone: Tone = state.selectedTone,
        title: String,
        detail: String,
        progress: Int,
        steps: List<GeneratingStep>,
    ) {
        state = state.copy(
            page = Page.GENERATING,
            activeMode = mode,
            selectedTone = tone,
            introText = "正在根据当前聊天内容整理一版可直接发送的回复建议，请稍候片刻。",
            generatingTitle = title,
            generatingDetail = detail,
            generatingProgress = progress.coerceIn(0, 100),
            generatingSteps = steps,
            errorMessage = null,
        )
        render()
    }

    fun showResult(
        mode: GenerateMode,
        replies: List<String>,
        tone: Tone,
    ) {
        state = state.copy(
            page = Page.RESULT,
            activeMode = mode,
            selectedTone = tone,
            introText = "已根据当前聊天整理出更清晰的沟通要点，你可以直接浏览结果并切换表达语气。",
            replies = replies.take(10),
            resultRefreshing = false,
            resultRefreshingMessage = "",
            errorMessage = null,
        )
        render()
    }

    fun showClipboardReplyLoading(tone: Tone) {
        state = state.copy(
            page = Page.RESULT,
            activeMode = GenerateMode.PASTE,
            selectedTone = tone,
            introText = "根据刚刚复制的内容生成回复，点选后填入当前输入框。",
            replies = emptyList(),
            resultRefreshing = true,
            resultRefreshingMessage = "正在生成高情商回复…",
            errorMessage = null,
        )
        render()
    }

    fun showResultRefreshing(
        mode: GenerateMode,
        replies: List<String>,
        tone: Tone,
        message: String = "正在基于当前聊天内容刷新回复建议，不会重新截图。",
    ) {
        state = state.copy(
            page = Page.RESULT,
            activeMode = mode,
            selectedTone = tone,
            introText = "已基于上一轮聊天上下文继续刷新回复建议，当前抽屉和截图保持不变。",
            replies = replies.take(10),
            resultRefreshing = true,
            resultRefreshingMessage = message,
            errorMessage = null,
        )
        render()
    }

    fun showError(
        mode: GenerateMode,
        tone: Tone,
        message: String,
        errorTitle: String = DEFAULT_ERROR_TITLE,
        errorDescription: String = DEFAULT_ERROR_DESCRIPTION,
    ) {
        state = state.copy(
            page = Page.ERROR,
            activeMode = mode,
            selectedTone = tone,
            introText = "这次生成没有成功，你可以返回默认页重新开始，或者直接重新生成。",
            resultRefreshing = false,
            resultRefreshingMessage = "",
            errorMessage = message,
            errorTitle = errorTitle,
            errorDescription = errorDescription,
            errorRetryLoading = state.page == Page.ERROR && state.errorRetryLoading,
        )
        render()
    }

    fun selectTone(tone: Tone) {
        if (state.selectedTone == tone) return
        state = state.copy(selectedTone = tone)
        render()
    }

    /**
     * 粘贴模式失败态点击语气 / 重新生成时的即时反馈：
     * 错误卡文案区域先切换为 loading 动效，1.5 秒后再恢复文案，
     * 避免链路过短、返回过快时用户感知不到点击生效。
     */
    fun beginErrorRetryFeedback() {
        if (state.page != Page.ERROR) return
        errorRetryFeedbackRunnable?.let { panelContentView.removeCallbacks(it) }
        val runnable = Runnable {
            errorRetryFeedbackRunnable = null
            if (state.page == Page.ERROR && state.errorRetryLoading) {
                state = state.copy(errorRetryLoading = false)
                render()
            }
        }
        errorRetryFeedbackRunnable = runnable
        state = state.copy(errorRetryLoading = true)
        render()
        panelContentView.postDelayed(runnable, ERROR_RETRY_FEEDBACK_MS)
    }

    fun refreshSessionState() = Unit

    private var pasteNeonViewRef: ColorfulNeonBorderView? = null

    fun setPasteGenerating(generating: Boolean) {
        if (state.isPasteGenerating == generating) return
        state = state.copy(isPasteGenerating = generating)
        pasteNeonViewRef?.visibility = if (generating) View.VISIBLE else View.GONE
    }

    fun handleBackPressed(): Boolean {
        return if (state.page == Page.START) {
            false
        } else {
            onBackToStart()
            true
        }
    }

    fun handleSystemKeyboardHidden(): Boolean = false

    fun destroy() = Unit

    private fun render() {
        titleView.text = when (state.page) {
            Page.IMAGE_PICKER -> "图片"
            Page.RESTRICTED -> ""
            else -> "AI回复"
        }
        titleView.visibility = if (state.page == Page.RESTRICTED) View.GONE else View.VISIBLE
        introView.text = state.introText
        introView.visibility =
            if (state.page == Page.PREPARING || state.page == Page.GENERATING || state.page == Page.RESULT || state.page == Page.IMAGE_PICKER || state.introText.isEmpty()) View.GONE else View.VISIBLE
        introView.maxLines = if (state.page == Page.START || state.page == Page.RESTRICTED) 1 else Int.MAX_VALUE
        introView.ellipsize = if (state.page == Page.START || state.page == Page.RESTRICTED) TextUtils.TruncateAt.END else null
        val showBack = state.page != Page.START && state.page != Page.RESTRICTED
        backButton.visibility = if (showBack) View.VISIBLE else View.GONE
        val showPickerAction = state.page == Page.IMAGE_PICKER
        closeButton.visibility = if (showPickerAction) View.GONE else View.VISIBLE
        headerActionButton.visibility = if (showPickerAction) View.VISIBLE else View.GONE
        headerActionButton.isEnabled = state.selectedImageUris.isNotEmpty() && !state.imagePickerLoading
        headerActionButton.text = "确定"
        headerActionButton.background = roundedFill(
            if (headerActionButton.isEnabled) "#07C160" else "#DADBDD",
            999,
        )
        contentContainer.setPadding(
            if (state.page == Page.IMAGE_PICKER) 0 else dp(18),
            0,
            if (state.page == Page.IMAGE_PICKER) 0 else dp(18),
            0,
        )
        updatePageLayoutMode()
        contentContainer.gravity = Gravity.NO_GRAVITY
        contentContainer.removeAllViews()
        timelineContainer.removeAllViews()
        footerContainer.removeAllViews()
        if (state.page != Page.IMAGE_PICKER || state.imagePickerLoading || state.imagePickerPermissionDenied) {
            imagePickerRecyclerBinding = null
        }
        when (state.page) {
            Page.START -> renderStartPage()
            Page.RESTRICTED -> renderRestrictedPage()
            Page.IMAGE_PICKER -> renderImagePickerPage()
            Page.PREPARING -> renderPreparingPage()
            Page.PERMISSION -> renderPermissionPage()
            Page.GENERATING -> renderGeneratingPage()
            Page.RESULT -> renderResultPage()
            Page.ERROR -> renderErrorPage()
        }
    }

    private fun updatePageLayoutMode() {
        val isStartPage = state.page == Page.START || state.page == Page.RESTRICTED
        val isImagePickerPage = state.page == Page.IMAGE_PICKER
        val isFillHeightPage = isImagePickerPage || state.page == Page.PREPARING || state.page == Page.GENERATING
        val isStretchContentPage =
            state.page == Page.RESULT || state.page == Page.ERROR || state.page == Page.PERMISSION
        
        contentScrollView.visibility = if (isFillHeightPage) View.GONE else View.VISIBLE
        timelineContainer.visibility = if (isFillHeightPage) View.VISIBLE else View.GONE
        
        contentScrollView.overScrollMode =
            if (isStartPage) View.OVER_SCROLL_NEVER else View.OVER_SCROLL_IF_CONTENT_SCROLLS
        contentScrollView.isVerticalScrollBarEnabled = !isStartPage
        contentScrollView.isFillViewport = isStartPage || isStretchContentPage
        contentContainer.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (isStartPage || isStretchContentPage) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            },
        )
        (footerContainer.layoutParams as? LinearLayout.LayoutParams)?.apply {
            topMargin = if (isStartPage) dp(14) else dp(12)
        }
    }

    private fun renderStartPage() {
        val pasteCard = buildPasteSessionEntryCard()
        val primaryCard = buildPrimaryEntryCard()
        val entryGrid = buildEntryGrid()
        contentContainer.addView(
            pasteCard,
            layoutParams(top = 12),
        )
        contentContainer.addView(buildStartPageSpacer())
        contentContainer.addView(
            primaryCard,
            layoutParams(),
        )
        contentContainer.addView(buildStartPageSpacer())
        contentContainer.addView(
            entryGrid,
            layoutParams(),
        )
        buildFooterNote()?.let { footerContainer.addView(it) }
        contentScrollView.post {
            syncStartPageCardHeights(
                pasteCard = pasteCard,
                primaryCard = primaryCard,
                entryGrid = entryGrid,
            )
        }
    }

    private fun renderRestrictedPage() {
        contentContainer.gravity = Gravity.CENTER
        contentContainer.addView(
            buildRestrictedStateView(
                message = state.errorMessage?.ifBlank { "AI回复当前被限制使用，请联系企业管理员" }
                    ?: "AI回复当前被限制使用，请联系企业管理员",
            ),
            layoutParams(),
        )
    }

    private fun buildRestrictedStateView(
        message: String,
    ): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                TextView(ctx).apply {
                    text = message
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#111827"))
                    textSize = 18f
                    setLineSpacing(0f, 1.25f)
                    setTypeface(typeface, Typeface.BOLD)
                },
                layoutParams(),
            )
        }
    }

    private fun renderPermissionPage() {
        contentContainer.addView(
            buildInfoCard(
                eyebrow = "权限说明",
                title = "为什么需要截屏权限",
                body = state.permissionHint,
                tag = "仅用于本次读取聊天内容",
                tagAtBottom = true,
            ),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        footerContainer.addView(
            buildPrimaryButton("立即授权") {
                onRequestPermission()
            },
        )
    }

    private fun renderImagePickerPage() {
        when {
            state.imagePickerLoading -> {
                timelineContainer.addView(
                    favoriteUi.buildPickerLoadingView("正在加载图片"),
                    layoutParams(top = 0),
                )
            }
            state.imagePickerPermissionDenied -> {
                timelineContainer.addView(
                    buildInfoCard(
                        eyebrow = "权限说明",
                        title = "需要相册访问权限",
                        body = "传图回复需要先读取系统相册里的聊天截图或会话图片，才能按你选中的顺序生成回复建议。",
                        tag = "仅用于本次图片识别",
                    ),
                    layoutParams(top = 12),
                )
                footerContainer.addView(
                    buildPrimaryButton("授予相册权限") {
                        startImageReplyPicker()
                    },
                )
            }
            state.imagePickerItems.isEmpty() -> {
                timelineContainer.addView(
                    favoriteUi.buildPickerEmptyStateView("当前没有可用的图片"),
                    layoutParams(top = 0),
                )
            }
            else -> {
                ensureImagePickerVisibleCount(state.imagePickerItems.size)
                val binding = imagePickerRecyclerBinding ?: favoriteUi.buildEmbeddedMediaPickerRecyclerView(
                    type = FavoritePickerType.IMAGE,
                    items = visibleImagePickerItems(),
                    onToggleSelection = { item -> toggleImageReplySelection(item) },
                    selectedOrder = ::selectedImageOrder,
                    onLoadMore = ::loadMoreImagePickerItems,
                ).also { imagePickerRecyclerBinding = it }
                binding.adapter.submitItems(visibleImagePickerItems())
                timelineContainer.addView(
                    binding.root,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    ),
                )
            }
        }
    }

    private fun loadMoreImagePickerItems() {
        if (state.page != Page.IMAGE_PICKER || state.imagePickerLoading || imagePickerLoadingMore || imagePickerAllLoaded) return
        val loadVersion = imagePickerLoadVersion
        val offset = state.imagePickerItems.size
        imagePickerLoadingMore = true
        Thread {
            val loaded = embeddedPickerStore.loadPage(
                type = FavoritePickerType.IMAGE,
                limit = imagePickerLoadMoreBatchSize(),
                offset = offset,
            )
            rootView.post {
                imagePickerLoadingMore = false
                if (loadVersion != imagePickerLoadVersion || state.page != Page.IMAGE_PICKER) return@post
                if (loaded.isEmpty()) {
                    imagePickerAllLoaded = true
                    return@post
                }
                val knownUris = state.imagePickerItems.mapTo(linkedSetOf()) { it.uri }
                val merged = state.imagePickerItems + loaded.filter { knownUris.add(it.uri) }
                imagePickerAllLoaded = loaded.size < imagePickerLoadMoreBatchSize()
                imagePickerItemsCache = merged
                state = state.copy(imagePickerItems = merged)
                imagePickerVisibleCount = merged.size
                imagePickerRecyclerBinding?.adapter?.submitItems(visibleImagePickerItems())
            }
        }.start()
    }

    private fun renderPreparingPage() {
        timelineContainer.addView(
            buildTimelineProgressCard(),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    private fun renderGeneratingPage() {
        timelineContainer.addView(
            buildTimelineProgressCard(),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    private fun renderResultPage() {
        contentContainer.addView(buildToneRow(), layoutParams(bottom = dp(10)))
        contentContainer.addView(
            buildReplyListCard(),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        footerContainer.addView(buildPrimaryButton("重新生成") {
            onRegenerate()
        })
    }

    private fun renderErrorPage() {
        contentContainer.addView(buildToneRow(), layoutParams(bottom = dp(10)))
        contentContainer.addView(
            buildErrorCard(),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        footerContainer.addView(buildPrimaryButton("重新生成") {
            onRegenerate()
        })
    }

    private fun buildPrimaryEntryCard(): View {
        return WhiteShadowCardContainer(ctx).apply {
            setOnClickListener {
                if (!state.isPasteGenerating) {
                    onEntrySelected(GenerateMode.FAST_LLM)
                }
            }
            addView(
                FrameLayout(ctx).apply {
                    background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(Color.parseColor("#E8FFD7"), Color.parseColor("#D6FDF9"))
                    ).apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(8).toFloat()
                    }
                    
                    addView(
                        LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(12), dp(12), dp(12), dp(12))
                            addView(
                                LinearLayout(ctx).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    gravity = Gravity.CENTER_VERTICAL
                                    addView(
                                        buildEntryTitle("极速生成"),
                                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                                    )
                                },
                            )
                            addView(
                                TextView(ctx).apply {
                                    text = "自动多屏识别，结果更精准"
                                    setTextColor(Color.parseColor("#85817E"))
                                    textSize = 13f
                                    setLineSpacing(0f, 1.45f)
                                    maxLines = 1
                                    ellipsize = TextUtils.TruncateAt.END
                                },
                                layoutParams(top = 8),
                            )
                        },
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )

                    addView(
                        ImageView(ctx).apply {
                            resolveOptionalDrawable("ic_ai_reply_fast_llm_left_bottom")?.let(::setImageResource)
                            scaleType = ImageView.ScaleType.FIT_START
                            adjustViewBounds = true
                        },
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM or Gravity.START
                        ).apply {
                            width = (ctx.resources.displayMetrics.widthPixels - dp(36)) / 2
                            leftMargin = dp(12)
                            bottomMargin = dp(12)
                        }
                    )

                    addView(
                        ImageView(ctx).apply {
                            resolveOptionalDrawable("ic_ai_reply_fast_llm_illustration")?.let(::setImageResource)
                            scaleType = ImageView.ScaleType.FIT_END
                            adjustViewBounds = true
                        },
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            dp(98),
                            Gravity.BOTTOM or Gravity.END
                        ).apply {
                            rightMargin = dp(10)
                            bottomMargin = 0
                        }
                    )
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                },
            )
        }
    }

    private fun buildEntryGrid(): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            addView(
                buildSmallEntryCard(
                    title = "识屏回复",
                    body = "理解屏幕生成回复",
                    gradientColors = intArrayOf(Color.parseColor("#FFDA76"), Color.parseColor("#FEEB9B"))
                ) { onEntrySelected(GenerateMode.OCR) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
            )
            addView(
                buildSmallEntryCard(
                    title = "传图回复",
                    body = "上传多图生成回复",
                    gradientColors = intArrayOf(Color.parseColor("#EAD7FF"), Color.parseColor("#DEF0FF"))
                ) { onEntrySelected(GenerateMode.IMAGE_REPLY) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
            )
        }
    }

    private fun buildPasteSessionEntryCard(): View {
        return WhiteShadowCardContainer(ctx).apply {
            setOnClickListener { 
                if (!state.isPasteGenerating) {
                    onEntrySelected(GenerateMode.PASTE) 
                }
            }
            
            val contentWrapper = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.parseColor("#E2FAF4"), Color.parseColor("#FBFFFE"))
                ).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                }
                clipToOutline = true
                setPadding(dp(16), dp(12), dp(16), dp(12))

                addView(
                    LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        
                        addView(
                            ImageView(ctx).apply {
                                setImageResource(R.drawable.ic_paste_quote)
                            },
                            LinearLayout.LayoutParams(dp(20), dp(16))
                        )
                        
                        addView(
                            TextView(ctx).apply {
                                text = "AI帮你理解内容，生成回复消息"
                                setTextColor(Color.parseColor("#000000"))
                                textSize = 13f
                                setTypeface(typeface, Typeface.BOLD)
                                maxLines = 1
                                gravity = Gravity.CENTER
                                ellipsize = TextUtils.TruncateAt.END
                            },
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                marginStart = dp(6)
                                marginEnd = dp(6)
                            }
                        )
                        
                        addView(
                            ImageView(ctx).apply {
                                setImageResource(R.drawable.ic_paste_pen)
                            },
                            LinearLayout.LayoutParams(dp(20), dp(20))
                        )
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
                
                addView(
                            LinearLayout(ctx).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.RECTANGLE
                                    cornerRadius = dp(15).toFloat()
                                    setColor(Color.WHITE)
                                }
                                setPadding(dp(10), dp(5), dp(10), dp(5))
                                
                                addView(
                                    ImageView(ctx).apply {
                                        setImageResource(R.drawable.ic_paste_add)
                                    },
                                    LinearLayout.LayoutParams(dp(11), dp(11))
                                )
                                
                                addView(
                                    TextView(ctx).apply {
                                        text = "点击复制，生成回复"
                                        setTextColor(Color.parseColor("#999999"))
                                        textSize = 12f
                                    },
                                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                        marginStart = dp(4)
                                    }
                                )
                            },
                            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                topMargin = dp(10)
                            }
                        )
            }

            addView(
                contentWrapper,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                },
            )

            val neonView = ColorfulNeonBorderView(ctx).apply {
                visibility = if (state.isPasteGenerating) View.VISIBLE else View.GONE
                setCornerRadiusPx(dp(10).toFloat())
                setBorderThickness(dp(4).toFloat())
            }
            pasteNeonViewRef = neonView
            addView(
                neonView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            )
        }
    }

    private fun buildSmallEntryCard(
        title: String,
        body: String,
        gradientColors: IntArray,
        onClick: () -> Unit,
    ): View {
        return WhiteShadowCardContainer(ctx).apply {
            setOnClickListener {
                if (!state.isPasteGenerating) {
                    onClick()
                }
            }
            
            val contentWrapper = FrameLayout(ctx).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.BR_TL,
                    gradientColors
                ).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                }
                clipToOutline = true

                val bgResId = if (title == "识屏回复") R.drawable.bg_ai_reply_ocr_decoration else R.drawable.bg_ai_reply_image_decoration
                addView(
                    ImageView(ctx).apply {
                        setImageResource(bgResId)
                        scaleType = ImageView.ScaleType.FIT_XY
                        translationX = dpf(38f)
                        translationY = dpf(37f)
                    },
                    FrameLayout.LayoutParams(
                        dp(126), dp(123), Gravity.BOTTOM or Gravity.END
                    )
                )

                val iconDrawableName = if (title == "识屏回复") {
                    "ic_ai_reply_ocr_illustration"
                } else {
                    "ic_ai_reply_image_illustration"
                }
                val iconWidth = if (title == "识屏回复") 51 else 51
                val iconHeight = if (title == "识屏回复") 51 else 51
                addView(
                    ImageView(ctx).apply {
                        resolveOptionalDrawable(iconDrawableName)?.let(::setImageResource)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    },
                    FrameLayout.LayoutParams(
                        dp(iconWidth), dp(iconHeight), Gravity.BOTTOM or Gravity.END
                    ).apply {
                        rightMargin = dp(4)
                        bottomMargin = dp(4)
                    }
                )

                addView(
                    LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        addView(buildEntryTitle(title))
                        addView(
                            TextView(ctx).apply {
                                text = body
                                setTextColor(Color.parseColor("#85817E"))
                                textSize = 13f
                                setLineSpacing(0f, 1.4f)
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.END
                            },
                            layoutParams(top = 8),
                        )
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                )
            }

            addView(
                contentWrapper,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                },
            )
        }
    }

    private fun buildEntryTitle(text: String): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#1C1818"))
            textSize = 14.4f // 18f * 0.8
            setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun buildRecommendationBadge(): View {
        return ImageView(ctx).apply {
            setImageResource(R.drawable.ic_recommend_badge_full)
            scaleType = ImageView.ScaleType.FIT_CENTER
            
            layoutParams = LinearLayout.LayoutParams(
                dp(70),
                dp(22)
            ).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
        }
    }

    private fun buildFeatureTagCloud(): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(buildFeatureTag("读上下文"))
                    addView(
                        buildFeatureTag("一步直达"),
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            marginStart = dp(8)
                        },
                    )
                },
            )
            addView(
                buildFeatureTag("更像人工"),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(8)
                },
            )
        }
    }

    private fun buildFeatureTag(text: String): View {
        return TextView(ctx).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#303843"))
            textSize = 12f
            background = roundedFill("#FFFFFF", 20, alpha = 0.92f)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
    }

    private fun buildFeatureIllustration(): View {
        return ReplyFeatureIllustrationView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(84))
        }
    }

    private fun buildSmallEntryIllustration(title: String): View {
        return FrameLayout(ctx).apply {
            addView(
                if (title == "识屏回复") {
                    ScreenReplyIllustrationView(ctx)
                } else {
                    ImageReplyIllustrationView(ctx)
                },
                FrameLayout.LayoutParams(dp(76), dp(64), Gravity.BOTTOM or Gravity.END).apply {
                    bottomMargin = dp(2)
                },
            )
        }
    }

    private fun buildInfoCard(
        eyebrow: String,
        title: String,
        body: String,
        tag: String,
        tagAtBottom: Boolean = false,
    ): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#FFFFFF"))
                setStroke(dp(1), Color.parseColor("#E2E7F8"))
            }
            setPadding(dp(14))
            addView(buildEyebrow(eyebrow))
            addView(
                TextView(ctx).apply {
                    text = title
                    setTextColor(Color.parseColor("#15171B"))
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                },
                layoutParams(top = 10),
            )
            addView(
                TextView(ctx).apply {
                    text = body
                    setTextColor(Color.parseColor("#6D7480"))
                    textSize = 13f
                    setLineSpacing(0f, 1.4f)
                },
                layoutParams(top = 8),
            )
            if (tagAtBottom) {
                addView(
                    View(ctx),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                        topMargin = dp(12)
                    },
                )
                addView(
                    buildSingleTag(tag).apply {
                        gravity = Gravity.CENTER
                    },
                    layoutParams(top = 12),
                )
            } else {
                addView(
                    buildSingleTag(tag),
                    layoutParams(top = 12),
                )
            }
        }
    }

    private fun toggleImageReplySelection(item: FavoriteEmbeddedPickerItem) {
        val selected = state.selectedImageUris.toMutableList()
        val existingIndex = selected.indexOfFirst { it == item.uri }
        if (existingIndex >= 0) {
            selected.removeAt(existingIndex)
        } else {
            if (selected.size >= maxImageReplySelection) {
                Toast.makeText(ctx, "最多选择${maxImageReplySelection}张图片", Toast.LENGTH_SHORT).show()
                return
            }
            selected += item.uri
        }
        state = state.copy(selectedImageUris = selected)
        favoriteUi.updateEmbeddedMediaPickerSelection(
            imagePickerRecyclerBinding?.root ?: contentContainer,
            ::selectedImageOrder,
        )
        renderHeaderActionState()
    }

    private fun selectedImageOrder(item: FavoriteEmbeddedPickerItem): Int {
        return state.selectedImageUris.indexOfFirst { it == item.uri }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
    }

    private fun renderHeaderActionState() {
        val enabled = state.selectedImageUris.isNotEmpty() && !state.imagePickerLoading
        headerActionButton.isEnabled = enabled
        headerActionButton.background = roundedFill(if (enabled) "#07C160" else "#DADBDD", 999)
    }

    @Suppress("DEPRECATION")
    private fun resolveMaxImageReplySelection(context: Context): Int {
        val appContext = context.applicationContext
        val runtimeConfigured = appContext
            .getSharedPreferences(UI_RUNTIME_PREFS_NAME, Context.MODE_PRIVATE)
            .takeIf { it.contains(KEY_AI_REPLY_MAX_IMAGE_SELECTION) }
            ?.getInt(KEY_AI_REPLY_MAX_IMAGE_SELECTION, DEFAULT_MAX_IMAGE_REPLY_SELECTION)
        val metaData = runCatching {
            val pm = appContext.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(
                    appContext.packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                ).metaData
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(appContext.packageName, PackageManager.GET_META_DATA).metaData
            }
        }.getOrNull()
        val manifestConfigured = metaData?.takeIf { it.containsKey(META_AI_REPLY_MAX_IMAGE_SELECTION) }
            ?.get(META_AI_REPLY_MAX_IMAGE_SELECTION)
            ?.let { value ->
                when (value) {
                    is Number -> value.toInt()
                    is String -> value.trim().toIntOrNull()
                    else -> null
                }
            }
        val configured = runtimeConfigured
            ?: manifestConfigured
            ?: DEFAULT_MAX_IMAGE_REPLY_SELECTION
        return configured.coerceIn(MIN_MAX_IMAGE_REPLY_SELECTION, MAX_MAX_IMAGE_REPLY_SELECTION)
    }

    private fun buildTimelineProgressCard(): View {
        val pageModel = buildTimelinePageUiModel()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            addView(
                TextView(ctx).apply {
                    text = "请勿触碰操作屏幕"
                    setTextColor(Color.parseColor("#1B1A1F"))
                    textSize = 22f
                    setTypeface(typeface, Typeface.BOLD)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(20)
                    marginStart = dp(18)
                    bottomMargin = dp(30)
                }
            )

            addView(
                TimelineProgressContainer(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(18), 0, dp(18), dp(24))

                    pageModel.steps.forEachIndexed { index, step ->
                        addView(
                            buildTimelineStepRow(step),
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                0,
                                1f
                            ).apply {
                                if (index > 0) topMargin = dp(16)
                            }
                        )
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }
    }

    private fun buildTimelinePageUiModel(): TimelinePageUiModel {
        return when (state.page) {
            Page.PREPARING -> TimelinePageUiModel(steps = buildPreparingTimelineSteps(state.activeMode))
            Page.GENERATING -> TimelinePageUiModel(steps = buildGeneratingTimelineSteps(state.activeMode))
            else -> TimelinePageUiModel(steps = emptyList())
        }
    }

    private fun buildPreparingTimelineSteps(mode: GenerateMode?): List<TimelineStepUiModel> {
        val statuses = resolveTimelineStatuses(state.preparingProgress, mode)
        if (mode == GenerateMode.IMAGE_REPLY) {
            return listOf(
                TimelineStepUiModel(
                    number = 1,
                    title = "识别图片内会话",
                    detail = "读取你选中的聊天图片，识别其中的会话内容与上下文",
                    status = statuses[0],
                ),
                TimelineStepUiModel(
                    number = 2,
                    title = "生成回复建议",
                    detail = "根据图片中的会话内容生成一组可直接发送的回复建议",
                    status = statuses[1],
                ),
            )
        }
        return listOf(
            TimelineStepUiModel(
                number = 1,
                title = "检查智能体运行环境",
                detail = "检查智能体运行环境是否正常",
                status = statuses[0],
            ),
            TimelineStepUiModel(
                number = 2,
                title = "识别当前屏幕信息",
                detail = "智能体会自动识别当前最近沟通信息",
                status = statuses[1],
            ),
            TimelineStepUiModel(
                number = 3,
                title = "生成回复建议",
                detail = "智能体根据最近沟通信息生成回复建议",
                status = statuses[2],
            ),
        )
    }

    private fun buildGeneratingTimelineSteps(mode: GenerateMode?): List<TimelineStepUiModel> {
        val statuses = resolveTimelineStatuses(state.generatingProgress, mode)
        if (mode == GenerateMode.IMAGE_REPLY) {
            return listOf(
                TimelineStepUiModel(
                    number = 1,
                    title = "识别图片内会话",
                    detail = "读取你选中的聊天图片，识别其中的会话内容与上下文",
                    status = statuses[0],
                ),
                TimelineStepUiModel(
                    number = 2,
                    title = "生成回复建议",
                    detail = "根据图片中的会话内容生成一组可直接发送的回复建议",
                    status = statuses[1],
                ),
            )
        }
        return listOf(
            TimelineStepUiModel(
                number = 1,
                title = "检查智能体运行环境",
                detail = "检查智能体运行环境是否正常",
                status = statuses[0],
            ),
            TimelineStepUiModel(
                number = 2,
                title = "识别当前屏幕信息",
                detail = "智能体会自动识别当前最近沟通信息",
                status = statuses[1],
            ),
            TimelineStepUiModel(
                number = 3,
                title = "生成回复建议",
                detail = "智能体根据最近沟通信息生成回复建议",
                status = statuses[2],
            ),
        )
    }

    private fun resolveTimelineStatuses(progress: Int, mode: GenerateMode?): List<StepStatus> {
        if (mode == GenerateMode.IMAGE_REPLY) {
            return when {
                progress >= 100 -> listOf(StepStatus.DONE, StepStatus.DONE)
                progress >= 50 -> listOf(StepStatus.DONE, StepStatus.ACTIVE)
                progress > 0 -> listOf(StepStatus.ACTIVE, StepStatus.TODO)
                else -> listOf(StepStatus.TODO, StepStatus.TODO)
            }
        }
        return when {
            progress >= 100 -> listOf(StepStatus.DONE, StepStatus.DONE, StepStatus.DONE)
            progress >= 80 -> listOf(StepStatus.DONE, StepStatus.DONE, StepStatus.ACTIVE)
            progress >= 60 -> listOf(StepStatus.DONE, StepStatus.DONE, StepStatus.TODO)
            progress >= 40 -> listOf(StepStatus.DONE, StepStatus.ACTIVE, StepStatus.TODO)
            progress >= 20 -> listOf(StepStatus.DONE, StepStatus.TODO, StepStatus.TODO)
            progress > 0 -> listOf(StepStatus.ACTIVE, StepStatus.TODO, StepStatus.TODO)
            else -> listOf(StepStatus.TODO, StepStatus.TODO, StepStatus.TODO)
        }
    }

    private fun buildTimelineStepRow(
        step: TimelineStepUiModel,
    ): View {
        val circleSize = dp(32)
        val circleRadius = circleSize / 2
        val cardMarginStart = dp(32) // Increased to make room for timeline on the left

        val cardContent = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = buildTimelineStepCardBackground(step.status)
            clipToOutline = true

            setPadding(dp(32), dp(20), dp(20), dp(20))

            addView(
                TextView(ctx).apply {
                    text = step.title
                    setTextColor(when (step.status) {
                        StepStatus.DONE -> Color.WHITE
                        StepStatus.ACTIVE, StepStatus.TODO -> Color.parseColor("#1B1A1F")
                    })
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    setLineSpacing(0f, 1.15f)
                }
            )
            addView(
                TextView(ctx).apply {
                    text = step.detail
                    setTextColor(when (step.status) {
                        StepStatus.DONE -> Color.WHITE
                        StepStatus.ACTIVE, StepStatus.TODO -> Color.parseColor("#737B98")
                    })
                    textSize = 11f
                    setLineSpacing(0f, 1.3f)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                }
            )
        }

        val cardContainer = FrameLayout(ctx).apply {
            addView(cardContent, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        val circleIndicator = TimelineStepIndicatorView(ctx, step.number).apply {
            update(step.status)
        }

        return FrameLayout(ctx).apply {
            setTag(R.id.ai_communicate_step_status, step.status)
            addView(cardContainer, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = cardMarginStart
                gravity = Gravity.CENTER_VERTICAL
            })

            addView(circleIndicator, FrameLayout.LayoutParams(circleSize, circleSize).apply {
                marginStart = cardMarginStart - circleRadius
                gravity = Gravity.CENTER_VERTICAL
            })
        }
    }

    private fun buildTimelineStepCardBackground(status: StepStatus): GradientDrawable {
        return when (status) {
            StepStatus.DONE -> GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#A834C759"), Color.parseColor("#FF31BD54"))
            ).apply {
                cornerRadius = dp(20).toFloat()
            }
            StepStatus.ACTIVE,
            StepStatus.TODO,
            -> GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.WHITE)
            }
        }
    }

    private fun buildGeneratingCard(): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#FFFFFF"))
                setStroke(dp(1), Color.parseColor("#E4E8EE"))
            }
            setPadding(dp(14))
            addView(buildEyebrow("当前状态"))
            addView(
                TextView(ctx).apply {
                    text = state.generatingTitle
                    setTextColor(Color.parseColor("#15171B"))
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                },
                layoutParams(top = 10),
            )
            addView(
                TextView(ctx).apply {
                    text = state.generatingDetail
                    setTextColor(Color.parseColor("#6C7380"))
                    textSize = 13f
                    setLineSpacing(0f, 1.4f)
                },
                layoutParams(top = 8),
            )
            addView(
                buildProgressTrack(state.generatingProgress),
                layoutParams(top = 14),
            )
        }
    }

    private fun buildPreparingCard(): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#FFFFFF"))
                setStroke(dp(1), Color.parseColor("#DDE5FB"))
            }
            setPadding(dp(18))
            addView(buildEyebrow("准备开始"))
            addView(
                TextView(ctx).apply {
                    text = state.preparingTitle
                    setTextColor(Color.parseColor("#15171B"))
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                },
                layoutParams(top = 10),
            )
            addView(
                TextView(ctx).apply {
                    text = state.preparingDetail
                    setTextColor(Color.parseColor("#6C7380"))
                    textSize = 13f
                    setLineSpacing(0f, 1.4f)
                },
                layoutParams(top = 8),
            )
            addView(
                TextView(ctx).apply {
                    text = state.preparingProgress.toString()
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#4E46E5"))
                    textSize = 34f
                    setTypeface(typeface, Typeface.BOLD)
                    background = roundedFill("#F4F2FF", 18).apply {
                        setStroke(dp(1), Color.parseColor("#DDD7FF"))
                    }
                    setPadding(dp(22), dp(14), dp(22), dp(14))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(18)
                },
            )
            addView(
                TextView(ctx).apply {
                    text = "倒计时结束后将自动收回抽屉，并开始滑动截屏"
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#7A8190"))
                    textSize = 12f
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(12)
                },
            )
        }
    }

    private fun buildStepsCard(): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill("#FFFFFF", 14).apply {
                setStroke(dp(1), Color.parseColor("#E4E8EE"))
            }
            setPadding(dp(13))
            addView(
                TextView(ctx).apply {
                    text = "处理步骤"
                    setTextColor(Color.parseColor("#15171B"))
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                },
            )
            state.generatingSteps.forEachIndexed { index, step ->
                addView(buildStepRow(step), layoutParams(top = if (index == 0) 10 else 8))
            }
        }
    }

    private fun buildStepRow(step: GeneratingStep): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedFill("#F8F9FC", 12).apply {
                setStroke(dp(1), Color.parseColor("#EDF1F5"))
            }
            setPadding(dp(12))
            gravity = Gravity.CENTER_VERTICAL
            addView(buildStepDot(step.status))
            addView(
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        TextView(ctx).apply {
                            text = step.title
                            setTextColor(Color.parseColor("#15171B"))
                            textSize = 14f
                            setTypeface(typeface, Typeface.BOLD)
                        },
                    )
                    addView(
                        TextView(ctx).apply {
                            text = step.detail
                            setTextColor(Color.parseColor("#6C7380"))
                            textSize = 13f
                            setLineSpacing(0f, 1.3f)
                        },
                        layoutParams(top = 4),
                    )
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10)
                },
            )
            addView(
                TextView(ctx).apply {
                    text = when (step.status) {
                        StepStatus.TODO -> "待处理"
                        StepStatus.ACTIVE -> "进行中"
                        StepStatus.DONE -> "已完成"
                    }
                    setTextColor(
                        when (step.status) {
                            StepStatus.TODO -> Color.parseColor("#8C93A0")
                            StepStatus.ACTIVE -> Color.parseColor("#5A54E6")
                            StepStatus.DONE -> Color.parseColor("#2F9E44")
                        },
                    )
                    textSize = 11f
                    setTypeface(typeface, Typeface.BOLD)
                    background = roundedFill(
                        when (step.status) {
                            StepStatus.TODO -> "#F2F4F7"
                            StepStatus.ACTIVE -> "#EEEAFE"
                            StepStatus.DONE -> "#EAF7EE"
                        },
                        999,
                    )
                    setPadding(dp(9), dp(5), dp(9), dp(5))
                },
            )
        }
    }

    private fun buildStepDot(status: StepStatus): View {
        return TextView(ctx).apply {
            gravity = Gravity.CENTER
            text = when (status) {
                StepStatus.DONE -> "✓"
                StepStatus.ACTIVE -> "•"
                StepStatus.TODO -> ""
            }
            setTextColor(
                when (status) {
                    StepStatus.DONE -> Color.parseColor("#2F9E44")
                    StepStatus.ACTIVE -> Color.parseColor("#5A54E6")
                    StepStatus.TODO -> Color.TRANSPARENT
                },
            )
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    when (status) {
                        StepStatus.DONE -> Color.parseColor("#EAF7EE")
                        StepStatus.ACTIVE -> Color.parseColor("#EEEAFE")
                        StepStatus.TODO -> Color.parseColor("#F1F3F5")
                    },
                )
                setStroke(
                    dp(1),
                    when (status) {
                        StepStatus.DONE -> Color.parseColor("#B9E2C3")
                        StepStatus.ACTIVE -> Color.parseColor("#D8D2FF")
                        StepStatus.TODO -> Color.parseColor("#E2E6EA")
                    },
                )
            }
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        }
    }

    private fun buildReplyListCard(): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            if (state.resultRefreshing) {
                addView(
                    buildRefreshingHintCard(),
                    layoutParams(bottom = if (state.replies.isEmpty()) 0 else 10),
                )
            }
            if (state.replies.isEmpty()) {
                repeat(if (state.resultRefreshing) 3 else 1) { index ->
                    addView(
                        if (state.resultRefreshing) buildReplyLoadingCard() else buildEmptyReplyCard(),
                        layoutParams(bottom = if (index == 2) 0 else 10),
                    )
                }
                return@apply
            }
            state.replies.forEachIndexed { index, reply ->
                addView(
                    buildReplyCard(index, reply).apply {
                        alpha = if (state.resultRefreshing) 0.58f else 1f
                        isEnabled = !state.resultRefreshing
                    },
                    layoutParams(bottom = if (index == state.replies.lastIndex) 0 else dp(10)),
                )
            }
        }
    }

    private fun buildRefreshingHintCard(): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedFill("#F4F2FF", 14).apply {
                setStroke(dp(1), Color.parseColor("#DDD7FF"))
            }
            setPadding(dp(12))
            addView(
                View(ctx).apply {
                    background = roundedFill("#6B64F0", 999)
                },
                LinearLayout.LayoutParams(dp(8), dp(8)),
            )
            addView(
                TextView(ctx).apply {
                    text = state.resultRefreshingMessage
                    setTextColor(Color.parseColor("#5A54E6"))
                    textSize = 12f
                    setLineSpacing(0f, 1.2f)
                    setTypeface(typeface, Typeface.BOLD)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10)
                },
            )
        }
    }

    private fun buildReplyLoadingCard(): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#FAFAFD"))
                setStroke(dp(1), Color.parseColor("#ECEFF5"))
            }
            setPadding(dp(14))
            addView(
                View(ctx).apply {
                    background = roundedFill("#EEEAFE", 999)
                },
                LinearLayout.LayoutParams(dp(68), dp(10)),
            )
            addView(
                View(ctx).apply {
                    background = roundedFill("#EEF1F6", 999)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply {
                    topMargin = dp(14)
                },
            )
            addView(
                View(ctx).apply {
                    background = roundedFill("#EEF1F6", 999)
                },
                LinearLayout.LayoutParams((dp(196)).coerceAtLeast(dp(120)), dp(10)).apply {
                    topMargin = dp(10)
                },
            )
        }
    }

    private fun buildEmptyReplyCard(): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedFill("#FBFBFD", 16).apply {
                setStroke(dp(1), Color.parseColor("#E4E8EE"))
            }
            setPadding(dp(18))
            addView(
                TextView(ctx).apply {
                    text = "当前还没有可展示的回复建议"
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#6D7480"))
                    textSize = 13f
                },
            )
        }
    }

    private fun buildReplyCard(index: Int, reply: String): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(31).toFloat()
                setColor(Color.parseColor("#66FFFFFF")) // var(--White-40, #ffffff66)
                setStroke(dp(1), Color.parseColor("#99FFFFFF")) // var(--White-60, #ffffff99)
            }
            setPadding(dp(19), dp(19), dp(19), dp(19))
            setOnClickListener { onReplySelected(index, reply) }
            
            addView(
                TextView(ctx).apply {
                    text = reply
                    setTextColor(Color.parseColor("#262626")) // var(--Dark, #262626)
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setLineSpacing(0f, 1.45f)
                    maxLines = 6
                    ellipsize = TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            
            addView(
                FrameLayout(ctx).apply {
                    background = roundedFill("#FFFFFF", 20)
                    addView(
                        ReplyArrowIconView(ctx),
                        FrameLayout.LayoutParams(dp(12), dp(12), Gravity.CENTER)
                    )
                },
                LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    marginStart = dp(16)
                }
            )
        }
    }

    private fun buildErrorCard(): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedFill("#FFFFFF", 14).apply {
                setStroke(dp(1), Color.parseColor("#E9D7DB"))
            }
            setPadding(dp(18))
            if (state.errorRetryLoading) {
                addView(
                    ImageView(ctx).apply {
                        setImageResource(R.drawable.ic_ai_reply_loading)
                        startLoadingRotation()
                    },
                    LinearLayout.LayoutParams(dp(44), dp(44)),
                )
                return@apply
            }
            addView(
                TextView(ctx).apply {
                    text = state.errorTitle.ifBlank { DEFAULT_ERROR_TITLE }
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#15171B"))
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                },
            )
            addView(
                TextView(ctx).apply {
                    text = state.errorDescription.ifBlank { DEFAULT_ERROR_DESCRIPTION }
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#8A8A8A"))
                    textSize = 14f
                    setLineSpacing(0f, 1.35f)
                },
                layoutParams(top = 12),
            )
        }
    }

    private fun ImageView.startLoadingRotation() {
        ObjectAnimator.ofFloat(this, View.ROTATION, 0f, 360f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun buildToneRow(): View {
        val toneRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        toneOptions.forEachIndexed { index, tone ->
            toneRow.addView(
                TextView(ctx).apply {
                    val selected = state.selectedTone == tone
                    text = tone.label
                    gravity = Gravity.CENTER
                    val enabled = !state.resultRefreshing
                    setTextColor(
                        if (selected) Color.parseColor("#090314") else Color.parseColor("#666666")
                    )
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    background = if (selected) {
                        GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(Color.parseColor("#E0FE54"), Color.parseColor("#7BDE78"))
                        ).apply {
                            cornerRadius = dp(24).toFloat()
                        }
                    } else {
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(Color.parseColor("#F5F5F5"))
                            cornerRadius = dp(24).toFloat()
                        }
                    }
                    setPadding(dp(14), dp(6), dp(14), dp(6))
                    alpha = if (enabled) 1f else 0.5f
                    setOnClickListener {
                        if (enabled && state.selectedTone != tone) {
                            onToneSelected(tone)
                        }
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) marginStart = dp(8)
                },
            )
        }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                toneRow,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun buildFooterNote(): View? {
        val footerText = footerTextProvider()?.trim().orEmpty()
        if (footerText.isBlank()) return null
        return TextView(ctx).apply {
            text = footerText
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#85817E"))
            textSize = 12f
            setOnClickListener {
                onFooterClick()
            }
        }
    }

    private inner class AnimatedNeonBorderView(
        context: Context,
        private val cornerRadiusPx: Float,
        private val strokeWidthPx: Float,
    ) : View(context) {
        private val borderRect = RectF()

        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = strokeWidthPx
            color = Color.parseColor("#1392FF")
        }

        init {
            setBackgroundColor(Color.TRANSPARENT)
        }

        fun pauseAnimation() {
            // No-op for static border
        }

        fun resumeAnimation() {
            // No-op for static border
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val inset = strokeWidthPx * 0.5f
            borderRect.set(inset, inset, width - inset, height - inset)
            canvas.drawRoundRect(borderRect, cornerRadiusPx, cornerRadiusPx, corePaint)
        }
    }

    private inner class ColorfulNeonBorderView(
        context: Context,
    ) : View(context) {
        private val borderRect = RectF()
        private val shaderMatrix = Matrix()
        private var shader: SweepGradient? = null
        private var strokeWidthPx = 0f
        private var cornerRadiusPx = dpf(12f)
        private var outerClipRadiusPx = dpf(12f)
        private val clipPath = Path()

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
            Color.parseColor("#FFFF3B30"),
        )
        private val colorPositions = floatArrayOf(0f, 0.12f, 0.24f, 0.36f, 0.5f, 0.64f, 0.76f, 0.88f, 0.96f, 1f)
        private var gradientRotation: Float = 0f

        private var animator: android.animation.ObjectAnimator? = null

        init {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            setBorderThickness(strokeWidthPx)
        }

        fun setGradientRotation(value: Float) {
            gradientRotation = value
            updateShaderMatrix()
            invalidate()
        }

        fun getGradientRotation(): Float = gradientRotation

        fun setCornerRadiusPx(value: Float) {
            if (cornerRadiusPx == value) return
            cornerRadiusPx = value
            invalidate()
        }

        fun setBorderThickness(value: Float) {
            if (strokeWidthPx == value) return
            strokeWidthPx = value
            glowPaint.strokeWidth = value * 1.35f
            corePaint.strokeWidth = value
            glowPaint.maskFilter = android.graphics.BlurMaskFilter(value * 2.2f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            invalidate()
        }

        fun startAnimation() {
            if (animator?.isRunning == true) return
            animator = android.animation.ObjectAnimator.ofFloat(this, "gradientRotation", 0f, 360f).apply {
                duration = 1800L
                repeatCount = android.animation.ObjectAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                start()
            }
        }

        fun stopAnimation() {
            animator?.cancel()
            animator = null
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (visibility == View.VISIBLE) startAnimation()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopAnimation()
        }

        override fun setVisibility(visibility: Int) {
            super.setVisibility(visibility)
            if (visibility == View.VISIBLE) {
                startAnimation()
            } else {
                stopAnimation()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return
            shader = SweepGradient(w / 2f, h / 2f, colors, colorPositions)
            glowPaint.shader = shader
            corePaint.shader = shader
            setBorderThickness(strokeWidthPx)
            updateShaderMatrix()
            clipPath.reset()
            clipPath.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), outerClipRadiusPx, outerClipRadiusPx, Path.Direction.CW)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.save()
            canvas.clipPath(clipPath)
            super.onDraw(canvas)
            val inset = strokeWidthPx * 0.5f
            borderRect.set(inset, inset, width - inset, height - inset)
            canvas.drawRoundRect(borderRect, cornerRadiusPx, cornerRadiusPx, glowPaint)
            canvas.drawRoundRect(borderRect, cornerRadiusPx, cornerRadiusPx, corePaint)
            canvas.restore()
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

    private inner class TimelineProgressContainer(context: Context) : LinearLayout(context) {
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpf(2f)
            color = Color.parseColor("#E2E6EA")
        }

        private val activeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#34C759") // Green for DONE
        }

        private val inactiveDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#737B98") // Dark blue/purple for ACTIVE/TODO
        }

        init {
            setWillNotDraw(false)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val childCount = childCount
            if (childCount < 1) return

            val circleX = paddingLeft + dpf(4f) // Small dot X position

            // Draw line connecting the first and last child's center
            val firstChild = getChildAt(0)
            val lastChild = getChildAt(childCount - 1)
            
            val startY = firstChild.top + firstChild.height / 2f
            val stopY = lastChild.top + lastChild.height / 2f
            
            canvas.drawLine(circleX, startY, circleX, stopY, linePaint)

            // Draw dots at each child's center
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val dotY = child.top + child.height / 2f
                
                // Get the status from the view tag, assuming we store it there
                val status = child.getTag(R.id.ai_communicate_step_status) as? StepStatus ?: StepStatus.TODO
                
                val paint = if (status == StepStatus.DONE) activeDotPaint else inactiveDotPaint
                canvas.drawCircle(circleX, dotY, dpf(4f), paint)
            }
        }
    }

    private inner class WhiteShadowCardContainer(
        context: Context,
    ) : FrameLayout(context) {
        private val cardRect = RectF()
        private val shadowPath = Path()
        private val cornerRadius = dpf(12f)
        private val shadowSpread = dpf(10f)
        private val shadowSteps = 32
        private val shadowEdgeAlpha = 0.10f
        private val shadowColor = Color.rgb(209, 209, 209)
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        init {
            clipChildren = false
            clipToPadding = false
            setWillNotDraw(false)
            val inset = shadowSpread.toInt()
            setPadding(inset, inset, inset, inset)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            cardRect.set(
                paddingLeft.toFloat(),
                paddingTop.toFloat(),
                (width - paddingRight).toFloat(),
                (height - paddingBottom).toFloat(),
            )
            for (index in 0 until shadowSteps) {
                val innerProgress = index.toFloat() / shadowSteps.toFloat()
                val outerProgress = (index + 1).toFloat() / shadowSteps.toFloat()
                val midProgress = (innerProgress + outerProgress) * 0.5f
                val innerExpansion = shadowSpread * innerProgress
                val outerExpansion = shadowSpread * outerProgress
                val alpha = (255f * shadowEdgeAlpha * (1f - midProgress)).toInt().coerceIn(0, 255)
                shadowPaint.color = Color.argb(alpha, Color.red(shadowColor), Color.green(shadowColor), Color.blue(shadowColor))
                shadowPath.reset()
                shadowPath.fillType = Path.FillType.EVEN_ODD
                shadowPath.addRoundRect(
                    cardRect.left - outerExpansion,
                    cardRect.top - outerExpansion,
                    cardRect.right + outerExpansion,
                    cardRect.bottom + outerExpansion,
                    cornerRadius + outerExpansion,
                    cornerRadius + outerExpansion,
                    Path.Direction.CW,
                )
                shadowPath.addRoundRect(
                    cardRect.left - innerExpansion,
                    cardRect.top - innerExpansion,
                    cardRect.right + innerExpansion,
                    cardRect.bottom + innerExpansion,
                    cornerRadius + innerExpansion,
                    cornerRadius + innerExpansion,
                    Path.Direction.CCW,
                )
                canvas.drawPath(shadowPath, shadowPaint)
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, fillPaint)
        }
    }

    private inner class TimelineStepIndicatorView(
        context: Context,
        private val stepNumber: Int,
    ) : View(context) {
        private var status: StepStatus = StepStatus.TODO
        private val ringRect = RectF()
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpf(1.6f)
        }
        private val innerTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpf(2.4f)
            color = Color.parseColor("#D6E8FB")
            strokeCap = Paint.Cap.ROUND
        }
        private val activeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpf(2.6f)
            color = Color.parseColor("#34C759")
            strokeCap = Paint.Cap.ROUND
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = dpf(10.5f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpf(2.2f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
        }

        fun update(newStatus: StepStatus) {
            status = newStatus
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = minOf(width, height).toFloat()
            val center = size * 0.5f
            val circleRadius = size * 0.44f
            val ringInset = size * 0.14f
            val baseline = center - (textPaint.ascent() + textPaint.descent()) * 0.5f
            fillPaint.color =
                when (status) {
                    StepStatus.DONE -> Color.parseColor("#34C759")
                    StepStatus.ACTIVE -> Color.parseColor("#F8FBFF")
                    StepStatus.TODO -> Color.parseColor("#FFFFFF")
                }
            strokePaint.color =
                when (status) {
                    StepStatus.DONE -> Color.parseColor("#F7F6F9")
                    StepStatus.ACTIVE -> Color.parseColor("#D7EAFE")
                    StepStatus.TODO -> Color.parseColor("#CFCFD6")
                }
            canvas.drawCircle(center, center, circleRadius, fillPaint)
            canvas.drawCircle(center, center, circleRadius, strokePaint)
            when (status) {
                StepStatus.DONE -> {
                    val check = Path().apply {
                        moveTo(size * 0.31f, size * 0.51f)
                        lineTo(size * 0.45f, size * 0.65f)
                        lineTo(size * 0.69f, size * 0.39f)
                    }
                    canvas.drawPath(check, checkPaint)
                }
                StepStatus.ACTIVE -> {
                    ringRect.set(
                        ringInset,
                        ringInset,
                        size - ringInset,
                        size - ringInset,
                    )
                    canvas.drawArc(ringRect, 0f, 360f, false, innerTrackPaint)
                    val startAngle = ((SystemClock.uptimeMillis() % 1200L) / 1200f) * 360f - 90f
                    canvas.drawArc(ringRect, startAngle, 292f, false, activeRingPaint)
                    textPaint.color = Color.parseColor("#171717")
                    canvas.drawText(stepNumber.toString(), center, baseline, textPaint)
                    postInvalidateOnAnimation()
                }
                StepStatus.TODO -> {
                    textPaint.color = Color.parseColor("#1B1B1B")
                    canvas.drawText(stepNumber.toString(), center, baseline, textPaint)
                }
            }
        }
    }

    private fun buildStartPageSpacer(): View {
        return View(ctx)
    }

    private inner class ReplyArrowIconView(
        context: Context,
    ) : View(context) {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#262626")
            strokeWidth = dpf(1.5f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val arrowPath = Path().apply {
                moveTo(w * 0.35f, h * 0.2f)
                lineTo(w * 0.65f, h * 0.5f)
                lineTo(w * 0.35f, h * 0.8f)
            }
            canvas.drawPath(arrowPath, strokePaint)
        }
    }

    private inner class PasteTriggerIconView(
        context: Context,
    ) : View(context) {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#1392FF")
            strokeWidth = dpf(1.6f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val rect = RectF(w * 0.26f, h * 0.18f, w * 0.82f, h * 0.86f)
            canvas.drawRoundRect(rect, dpf(2.5f), dpf(2.5f), strokePaint)
            canvas.drawLine(w * 0.38f, h * 0.09f, w * 0.66f, h * 0.09f, strokePaint)
            canvas.drawLine(w * 0.39f, h * 0.47f, w * 0.66f, h * 0.47f, strokePaint)
            canvas.drawLine(w * 0.39f, h * 0.67f, w * 0.61f, h * 0.67f, strokePaint)
        }
    }

    private inner class FastLlmChevronRightIconView(
        context: Context,
    ) : View(context) {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#CCFFFFFF")
            strokeWidth = dpf(2f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val arrowPath = Path().apply {
                moveTo(w * 0.40f, h * 0.25f)
                lineTo(w * 0.65f, h * 0.50f)
                lineTo(w * 0.40f, h * 0.75f)
            }
            canvas.drawPath(arrowPath, strokePaint)
        }
    }

    private inner class ReplyFeatureIllustrationView(
        context: Context,
    ) : View(context) {
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#14281A18")
        }
        private val backBubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#E4E5E8")
        }
        private val frontBubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#C9F0D1")
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#686F76")
            strokeWidth = dpf(2.2f)
            strokeCap = Paint.Cap.ROUND
        }
        private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpf(3.8f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val penPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1E232A")
        }
        private val penTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#303843")
        }
        private val penMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#8FFFFFFF")
            strokeWidth = dpf(1.5f)
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            wavePaint.shader = LinearGradient(
                0f,
                h,
                w,
                h * 0.55f,
                Color.parseColor("#1392FF"),
                Color.parseColor("#1392FF"),
                Shader.TileMode.CLAMP,
            )
            canvas.drawOval(
                RectF(w * 0.48f, h * 0.82f, w * 0.94f, h * 0.96f),
                shadowPaint,
            )
            canvas.drawPath(buildBackBubblePath(w, h), backBubblePaint)
            canvas.drawPath(buildFrontBubblePath(w, h), frontBubblePaint)
            canvas.drawLine(w * 0.22f, h * 0.23f, w * 0.52f, h * 0.23f, linePaint)
            canvas.drawLine(w * 0.22f, h * 0.32f, w * 0.46f, h * 0.32f, linePaint)
            canvas.drawLine(w * 0.42f, h * 0.49f, w * 0.73f, h * 0.49f, linePaint)
            canvas.drawLine(w * 0.42f, h * 0.58f, w * 0.67f, h * 0.58f, linePaint)
            val wave = Path().apply {
                moveTo(w * 0.06f, h * 0.76f)
                cubicTo(w * 0.14f, h * 0.68f, w * 0.22f, h * 0.68f, w * 0.30f, h * 0.76f)
                cubicTo(w * 0.38f, h * 0.84f, w * 0.45f, h * 0.84f, w * 0.53f, h * 0.76f)
                cubicTo(w * 0.61f, h * 0.68f, w * 0.69f, h * 0.68f, w * 0.77f, h * 0.76f)
            }
            canvas.drawPath(wave, wavePaint)
            canvas.save()
            canvas.rotate(11f, w * 0.77f, h * 0.71f)
            canvas.drawPath(buildPenBodyPath(w, h), penPaint)
            canvas.drawPath(buildPenTipPath(w, h), penTipPaint)
            canvas.drawLine(w * 0.73f, h * 0.33f, w * 0.90f, h * 0.48f, penMarkPaint)
            canvas.restore()
        }

        private fun buildBackBubblePath(w: Float, h: Float): Path {
            val rect = RectF(w * 0.13f, h * 0.08f, w * 0.60f, h * 0.47f)
            return Path().apply {
                addRoundRect(rect, dpf(10f), dpf(10f), Path.Direction.CW)
                moveTo(w * 0.27f, h * 0.47f)
                lineTo(w * 0.21f, h * 0.56f)
                lineTo(w * 0.33f, h * 0.50f)
                close()
            }
        }

        private fun buildFrontBubblePath(w: Float, h: Float): Path {
            val rect = RectF(w * 0.33f, h * 0.25f, w * 0.76f, h * 0.63f)
            return Path().apply {
                addRoundRect(rect, dpf(9f), dpf(9f), Path.Direction.CW)
                moveTo(w * 0.52f, h * 0.63f)
                lineTo(w * 0.44f, h * 0.74f)
                lineTo(w * 0.61f, h * 0.64f)
                close()
            }
        }

        private fun buildPenBodyPath(w: Float, h: Float): Path {
            return Path().apply {
                moveTo(w * 0.67f, h * 0.77f)
                lineTo(w * 0.78f, h * 0.36f)
                quadTo(w * 0.79f, h * 0.31f, w * 0.83f, h * 0.34f)
                lineTo(w * 0.92f, h * 0.42f)
                quadTo(w * 0.96f, h * 0.46f, w * 0.92f, h * 0.50f)
                lineTo(w * 0.70f, h * 0.82f)
                close()
            }
        }

        private fun buildPenTipPath(w: Float, h: Float): Path {
            return Path().apply {
                moveTo(w * 0.67f, h * 0.77f)
                lineTo(w * 0.72f, h * 0.73f)
                lineTo(w * 0.74f, h * 0.81f)
                close()
            }
        }
    }

    private inner class ScreenReplyIllustrationView(
        context: Context,
    ) : View(context) {
        private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#B8FFFFFF")
        }
        private val panelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#D1FFFFFF")
            strokeWidth = dpf(1.4f)
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#686F76")
            strokeWidth = dpf(2f)
            strokeCap = Paint.Cap.ROUND
        }
        private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#1392FF")
            strokeWidth = dpf(2.4f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#291392FF")
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val panelRect = RectF(w * 0.10f, h * 0.15f, w * 0.62f, h * 0.62f)
            canvas.drawRoundRect(panelRect, dpf(10f), dpf(10f), panelPaint)
            canvas.drawRoundRect(panelRect, dpf(10f), dpf(10f), panelStrokePaint)
            canvas.drawLine(w * 0.20f, h * 0.30f, w * 0.52f, h * 0.30f, linePaint)
            canvas.drawLine(w * 0.20f, h * 0.39f, w * 0.47f, h * 0.39f, linePaint)
            canvas.drawLine(w * 0.20f, h * 0.48f, w * 0.43f, h * 0.48f, linePaint)
            val frame = Path().apply {
                moveTo(w * 0.74f, h * 0.31f)
                lineTo(w * 0.86f, h * 0.31f)
                lineTo(w * 0.86f, h * 0.43f)
                moveTo(w * 0.86f, h * 0.58f)
                lineTo(w * 0.86f, h * 0.70f)
                lineTo(w * 0.74f, h * 0.70f)
                moveTo(w * 0.37f, h * 0.70f)
                lineTo(w * 0.25f, h * 0.70f)
                lineTo(w * 0.25f, h * 0.58f)
                moveTo(w * 0.25f, h * 0.43f)
                lineTo(w * 0.25f, h * 0.31f)
                lineTo(w * 0.37f, h * 0.31f)
            }
            canvas.drawPath(frame, scanPaint)
            canvas.drawCircle(w * 0.79f, h * 0.48f, w * 0.08f, dotFillPaint)
            canvas.drawCircle(w * 0.79f, h * 0.48f, w * 0.08f, scanPaint)
        }
    }

    private inner class ImageReplyIllustrationView(
        context: Context,
    ) : View(context) {
        private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#85FFFFFF")
        }
        private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#D1FFFFFF")
            strokeWidth = dpf(1.4f)
        }
        private val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#445162")
            strokeWidth = dpf(2.4f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FF9D00")
        }
        private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#1FC158")
            strokeWidth = dpf(2.4f)
            strokeCap = Paint.Cap.ROUND
        }
        private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1FC158")
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val card = RectF(w * 0.10f, h * 0.18f, w * 0.78f, h * 0.68f)
            canvas.drawRoundRect(card, dpf(12f), dpf(12f), cardPaint)
            canvas.drawRoundRect(card, dpf(12f), dpf(12f), cardStrokePaint)
            val mountain = Path().apply {
                moveTo(w * 0.20f, h * 0.53f)
                lineTo(w * 0.34f, h * 0.36f)
                lineTo(w * 0.44f, h * 0.47f)
                lineTo(w * 0.55f, h * 0.31f)
                lineTo(w * 0.69f, h * 0.53f)
            }
            canvas.drawPath(mountain, mountainPaint)
            canvas.drawCircle(w * 0.56f, h * 0.26f, w * 0.06f, sunPaint)
            val eye = Path().apply {
                moveTo(w * 0.22f, h * 0.83f)
                quadTo(w * 0.40f, h * 0.66f, w * 0.58f, h * 0.83f)
                quadTo(w * 0.76f, h * 0.66f, w * 0.94f, h * 0.83f)
            }
            canvas.drawPath(eye, eyePaint)
            canvas.drawCircle(w * 0.56f, h * 0.78f, w * 0.045f, pupilPaint)
        }
    }

    private fun buildPrimaryButton(text: String, onClick: () -> Unit): View {
        return TextView(ctx).apply {
            val enabled = !(state.page == Page.RESULT && state.resultRefreshing)
            this.text = if (state.page == Page.RESULT && state.resultRefreshing) "正在刷新..." else text
            gravity = Gravity.CENTER
            setTextColor(if (enabled) Color.parseColor("#39415F") else Color.parseColor("#8A91A0"))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (enabled) {
                    intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#F0F3FF"))
                } else {
                    intArrayOf(Color.parseColor("#F7F8FB"), Color.parseColor("#EEF1F6"))
                },
            ).apply {
                cornerRadius = dp(999).toFloat()
                setStroke(dp(1), if (enabled) Color.parseColor("#DFE5F8") else Color.parseColor("#E3E7EE"))
            }
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setOnClickListener {
                if (enabled) {
                    onClick()
                }
            }
        }
    }

    private fun buildHeaderButton(contentDescription: String, iconRes: Int): View {
        return ImageView(ctx).apply {
            this.contentDescription = contentDescription
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(iconRes)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
    }

    private fun buildEyebrow(text: String): View {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#5A54E6"))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            background = roundedFill("#EEEAFE", 999)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
    }

    private fun buildSingleTag(text: String): View {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#5A54E6"))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            background = roundedFill("#F3F6FF", 999).apply {
                setStroke(dp(1), Color.parseColor("#DFE6FF"))
            }
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
    }

    private fun buildTagRow(tags: List<String>, selected: Boolean): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tags.forEachIndexed { index, tag ->
                addView(
                    TextView(ctx).apply {
                        text = tag
                        gravity = Gravity.CENTER
                        setTextColor(if (selected) Color.parseColor("#6C7D9D") else Color.parseColor("#6F7681"))
                        textSize = 11f
                        background = roundedFill(if (selected) "#E6ECF9" else "#F5F6F8", 999)
                        setPadding(dp(8), dp(7), dp(8), dp(7))
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (index > 0) marginStart = dp(8)
                    },
                )
            }
        }
    }

    private fun buildProgressTrack(progress: Int): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(ctx).apply {
                    text = "处理进度 $progress%"
                    setTextColor(Color.parseColor("#5A54E6"))
                    textSize = 12f
                    setTypeface(typeface, Typeface.BOLD)
                },
            )
            addView(
                LinearLayout(ctx).apply {
                    background = roundedFill("#EBEEF5", 999)
                    addView(
                        View(ctx).apply {
                            background = GradientDrawable(
                                GradientDrawable.Orientation.LEFT_RIGHT,
                                intArrayOf(Color.parseColor("#6B64F0"), Color.parseColor("#6F89FF")),
                            ).apply {
                                cornerRadius = dp(999).toFloat()
                            }
                        },
                        LinearLayout.LayoutParams(0, dp(6), progress.toFloat().coerceAtLeast(1f)),
                    )
                    addView(View(ctx), LinearLayout.LayoutParams(0, dp(6), (100 - progress).toFloat().coerceAtLeast(0f)))
                },
                layoutParams(top = 8),
            )
        }
    }

    private fun layoutParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = top
            bottomMargin = bottom
        }
    }

    private fun roundedFill(color: String, radiusDp: Int, alpha: Float = 1f): GradientDrawable {
        val baseColor = Color.parseColor(color)
        val applied = Color.argb(
            (Color.alpha(baseColor) * alpha).toInt(),
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor),
        )
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(applied)
        }
    }

    private fun surfaceCardBackground(radiusDp: Int): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#FFFDFC"),
                Color.parseColor("#FBFAF9"),
            ),
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun favoriteListCardBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(Color.WHITE)
        }
    }

    private fun syncStartPageCardHeights(
        pasteCard: View,
        primaryCard: View,
        entryGrid: View,
    ) {
        if (state.page != Page.START) return
        val spacingPx = 0
        val introGapPx = dp(12)
        val availableHeight = contentScrollView.height - contentContainer.paddingTop - contentContainer.paddingBottom
        if (availableHeight <= 0) return
        val pasteHeight = dp(106)
        val availableFlexibleHeight =
            (availableHeight - introGapPx - pasteHeight - spacingPx * 2).coerceAtLeast(0)
        val minGridHeight = dp(132)
        val maxGridHeight = dp(168)
        var gridHeight = (availableFlexibleHeight * 0.40f).toInt().coerceIn(minGridHeight, maxGridHeight)
        var primaryHeight = availableFlexibleHeight - gridHeight
        val minPrimaryHeight = dp(136)
        if (primaryHeight < minPrimaryHeight) {
            val deficit = minPrimaryHeight - primaryHeight
            gridHeight = (gridHeight - deficit).coerceAtLeast(dp(126))
            primaryHeight = availableFlexibleHeight - gridHeight
        }
        if (primaryHeight <= 0 || gridHeight <= 0) return
        for (index in 0 until contentContainer.childCount) {
            val child = contentContainer.getChildAt(index)
            if (child !== pasteCard && child !== primaryCard && child !== entryGrid) {
                child.layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                    )
            }
        }
        (pasteCard.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            if (params.height != pasteHeight) {
                params.height = pasteHeight
                pasteCard.layoutParams = params
            }
        }
        (primaryCard.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            if (params.height != primaryHeight) {
                params.height = primaryHeight
                primaryCard.layoutParams = params
            }
        }
        (entryGrid.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            if (params.height != gridHeight) {
                params.height = gridHeight
                entryGrid.layoutParams = params
            }
        }
        contentScrollView.scrollTo(0, 0)
    }

    private fun topRoundedFill(radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            cornerRadii = floatArrayOf(
                dp(radiusDp).toFloat(), dp(radiusDp).toFloat(),
                dp(radiusDp).toFloat(), dp(radiusDp).toFloat(),
                0f, 0f,
                0f, 0f,
            )
        }
    }

    private fun dp(value: Int): Int = (value * ctx.resources.displayMetrics.density).toInt()

    private fun dpf(value: Float): Float = value * ctx.resources.displayMetrics.density
}
