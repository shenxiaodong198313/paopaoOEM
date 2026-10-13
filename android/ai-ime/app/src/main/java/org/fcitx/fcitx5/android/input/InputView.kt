/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcaster
import org.fcitx.fcitx5.android.input.broadcast.PreeditEmptyStateComponent
import org.fcitx.fcitx5.android.input.broadcast.PunctuationComponent
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateComponent
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.keyboard.T9Keyboard
import org.fcitx.fcitx5.android.input.picker.emojiPicker
import org.fcitx.fcitx5.android.input.picker.emoticonPicker
import org.fcitx.fcitx5.android.input.picker.symbolPicker
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.preedit.PreeditComponent
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.alpha
import org.fcitx.fcitx5.android.utils.unset
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.manager.wrapToUniqueComponent
import org.mechdancer.dependency.plusAssign
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class InputView(
    service: FcitxInputMethodService,
    fcitx: FcitxConnection,
    theme: Theme
) : BaseInputView(service, fcitx, theme) {
    companion object {
        private const val MinKeyboardWidthDp = 180

        // #region debug-point B:composer-ui-reporter
        private fun reportWechatDrawerPanelDebug(
            hypothesisId: String,
            location: String,
            msg: String,
            data: String,
        ) = Unit
        // #endregion
    }

    private val keyBorder: Boolean
        get() = (theme as? Theme.Custom)?.renderStyle?.layout?.keyBorder ?: ThemeManager.prefs.keyBorder.getValue()

    private val customBackground = imageView {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private val placeholderOnClickListener = OnClickListener { }

    // use clickable view as padding, so MotionEvent can be split to padding view and keyboard view
    private val leftPaddingSpace = view(::View) {
        setOnClickListener(placeholderOnClickListener)
    }
    private val rightPaddingSpace = view(::View) {
        setOnClickListener(placeholderOnClickListener)
    }
    private val bottomPaddingSpace = view(::View) {
        // height as keyboardBottomPadding
        // bottomMargin as WindowInsets (Navigation Bar) offset
        setOnClickListener(placeholderOnClickListener)
    }

    private val scope = DynamicScope()
    private val themedContext = context.withTheme(R.style.Theme_InputViewTheme)
    private val broadcaster = InputBroadcaster()
    private val popup = PopupComponent()
    private val punctuation = PunctuationComponent()
    private val returnKeyDrawable = ReturnKeyDrawableComponent()
    private val preeditEmptyState = PreeditEmptyStateComponent()
    private val preedit = PreeditComponent()
    private val commonKeyActionListener = CommonKeyActionListener()
    private val windowManager = InputWindowManager()
    private val kawaiiBar = KawaiiBarComponent()
    private val horizontalCandidate = HorizontalCandidateComponent()
    private val keyboardWindow = KeyboardWindow()
    private val symbolPicker = symbolPicker()
    private val emojiPicker = emojiPicker()
    private val emoticonPicker = emoticonPicker()

    private fun setupScope() {
        scope += this@InputView.wrapToUniqueComponent()
        scope += service.wrapToUniqueComponent()
        scope += fcitx.wrapToUniqueComponent()
        scope += theme.wrapToUniqueComponent()
        scope += themedContext.wrapToUniqueComponent()
        scope += broadcaster
        scope += popup
        scope += punctuation
        scope += returnKeyDrawable
        scope += preeditEmptyState
        scope += preedit
        scope += commonKeyActionListener
        scope += windowManager
        scope += kawaiiBar
        scope += horizontalCandidate
        broadcaster.onScopeSetupFinished(scope)
    }

    private val keyboardPrefs = AppPrefs.getInstance().keyboard

    private val focusChangeResetKeyboard by keyboardPrefs.focusChangeResetKeyboard

    private val keyboardHeightPercent = keyboardPrefs.keyboardHeightPercent
    private val keyboardHeightPercentLandscape = keyboardPrefs.keyboardHeightPercentLandscape
    private val t9KeyboardHeightPercent = keyboardPrefs.t9KeyboardHeightPercent
    private val t9KeyboardHeightPercentLandscape = keyboardPrefs.t9KeyboardHeightPercentLandscape
    private val useT9KeyboardLayout by keyboardPrefs.useT9KeyboardLayout
    private var isT9KeyboardActive = false
    private val keyboardSidePadding = keyboardPrefs.keyboardSidePadding
    private val keyboardSidePaddingLandscape = keyboardPrefs.keyboardSidePaddingLandscape
    private val keyboardHorizontalOffset = keyboardPrefs.keyboardHorizontalOffset
    private val keyboardHorizontalOffsetLandscape = keyboardPrefs.keyboardHorizontalOffsetLandscape
    private val keyboardBottomPadding = keyboardPrefs.keyboardBottomPadding
    private val keyboardBottomPaddingLandscape = keyboardPrefs.keyboardBottomPaddingLandscape

    private val keyboardSizePrefs = listOf(
        keyboardHeightPercent,
        keyboardHeightPercentLandscape,
        t9KeyboardHeightPercent,
        t9KeyboardHeightPercentLandscape,
        keyboardSidePadding,
        keyboardSidePaddingLandscape,
        keyboardHorizontalOffset,
        keyboardHorizontalOffsetLandscape,
        keyboardBottomPadding,
        keyboardBottomPaddingLandscape,
    )

    private data class KeyboardAdjustDraft(
        val heightPercent: Float,
        val leftPaddingDp: Int,
        val rightPaddingDp: Int,
        val bottomPaddingDp: Int,
    )

    private data class KeyboardLayoutMetrics(
        val heightPx: Int,
        val leftPaddingPx: Int,
        val rightPaddingPx: Int,
        val bottomPaddingPx: Int,
    )

    private var keyboardAdjustDraft: KeyboardAdjustDraft? = null
    private var keyboardAdjustInitialDraft: KeyboardAdjustDraft? = null
    private var keyboardAdjustReset = false

    private val keyboardAdjustOverlay = KeyboardAdjustOverlay(themedContext).apply {
        visibility = GONE
    }

    private val runtimeBlockedBadge = TextView(themedContext).apply {
        gravity = Gravity.CENTER
        setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
        setTextColor(theme.keyTextColor)
        textSize = 12f
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(14).toFloat()
            setColor(theme.barColor.alpha(0.92f))
        }
    }

    private val runtimeBlockedTitle = TextView(themedContext).apply {
        gravity = Gravity.CENTER
        setTextColor(theme.keyTextColor)
        textSize = 20f
    }

    private val runtimeBlockedMessage = TextView(themedContext).apply {
        gravity = Gravity.CENTER
        setTextColor(theme.keyTextColor.alpha(0.82f))
        textSize = 14f
        setLineSpacing(0f, 1.15f)
    }

    private val runtimeBlockedOverlay = FrameLayout(themedContext).apply {
        visibility = GONE
        isClickable = true
        isFocusable = true
        setBackgroundColor(theme.backgroundColor)
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(context.dp(24), context.dp(24), context.dp(24), context.dp(28))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = context.dp(24).toFloat()
                    setColor(theme.barColor.alpha(if (theme.isDark) 0.92f else 0.96f))
                }
                addView(
                    runtimeBlockedBadge,
                    LinearLayout.LayoutParams(wrapContent, wrapContent).apply {
                        bottomMargin = context.dp(16)
                    }
                )
                addView(
                    runtimeBlockedTitle,
                    LinearLayout.LayoutParams(matchParent, wrapContent).apply {
                        bottomMargin = context.dp(10)
                    }
                )
                addView(
                    runtimeBlockedMessage,
                    LinearLayout.LayoutParams(matchParent, wrapContent)
                )
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                marginStart = context.dp(24)
                marginEnd = context.dp(24)
            }
        )
    }

    private val keyboardHeightPx: Int
        get() {
            InputWindowSizeOverride.getKeyboardHeightPxOverride()?.let { return it }
            val percent = InputWindowSizeOverride.getKeyboardHeightPercentOverride() ?: when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE ->
                    if (isT9KeyboardActive) t9KeyboardHeightPercentLandscape else keyboardHeightPercentLandscape
                else ->
                    if (isT9KeyboardActive) t9KeyboardHeightPercent else keyboardHeightPercent
            }.getValue()
            return resources.displayMetrics.heightPixels * percent / 100
        }

    private val keyboardSidePaddingPx: Int
        get() {
            val value = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardSidePaddingLandscape
                else -> keyboardSidePadding
            }.getValue()
            return dp(value)
        }

    private val keyboardBottomPaddingPx: Int
        get() {
            val value = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardBottomPaddingLandscape
                else -> keyboardBottomPadding
            }.getValue()
            return dp(value)
        }

    private val keyboardHorizontalOffsetDp: Int
        get() = when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> keyboardHorizontalOffsetLandscape
            else -> keyboardHorizontalOffset
        }.getValue()

    @Keep
    private val onKeyboardSizeChangeListener = ManagedPreferenceProvider.OnChangeListener { key ->
        if (keyboardSizePrefs.any { it.key == key }) {
            updateKeyboardSize()
        }
    }

    private val onKeyboardHeightOverrideChanged = {
        updateKeyboardSize()
    }

    lateinit var keyboardView: View

    private var voiceSurface: View? = null
    private val voiceHiddenChildren = mutableMapOf<View, Int>()
    fun showVoiceSurface(surface: View?) {
        voiceSurface?.let { removeView(it) }; voiceSurface = null
        voiceHiddenChildren.forEach { (child, visibility) -> child.visibility = visibility }; voiceHiddenChildren.clear()
        background = theme.backgroundDrawable(keyBorder)
        if (surface != null) {
            for (index in 0 until childCount) { val child = getChildAt(index); voiceHiddenChildren[child] = child.visibility; child.visibility = INVISIBLE }
            setBackgroundColor(android.graphics.Color.rgb(8, 14, 29))
            addView(surface, LayoutParams(0, 0).apply { topToTop = 0; bottomToBottom = 0; startToStart = 0; endToEnd = 0 })
            voiceSurface = surface
        }
    }


    init {
        // MUST call before any operation
        setupScope()

        service.onT9ModeChanged = { broadcaster.onT9ModeUpdate(it) }
        service.onT9SidebarStateChanged = { keyboardWindow.updateT9SidebarState(it) }
        service.onKeyboardLayoutSwitchRequested = { keyboardWindow.switchLayout(it, remember = false) }
        service.onImeStateRefreshRequested = { broadcaster.onImeUpdate(it) }

        // restore punctuation mapping in case of InputView recreation
        fcitx.launchOnReady {
            punctuation.updatePunctuationMapping(it.statusAreaActionsCached)
        }

        // make sure KeyboardWindow's view has been created before it receives any broadcast
        windowManager.addEssentialWindow(keyboardWindow, createView = true)
        windowManager.addEssentialWindow(symbolPicker)
        windowManager.addEssentialWindow(emojiPicker)
        windowManager.addEssentialWindow(emoticonPicker)
        isT9KeyboardActive = useT9KeyboardLayout
        keyboardWindow.onLayoutChanged = { layoutName ->
            val shouldBeT9 = useT9KeyboardLayout && layoutName == T9Keyboard.Name
            if (isT9KeyboardActive != shouldBeT9) {
                isT9KeyboardActive = shouldBeT9
                updateKeyboardSize()
            }
        }
        windowManager.onActiveWindowChanged = { oldWindow, newWindow ->
            if (oldWindow === keyboardWindow && isT9KeyboardActive) {
                isT9KeyboardActive = false
                updateKeyboardSize()
            } else if (newWindow === keyboardWindow && useT9KeyboardLayout && keyboardWindow.isCurrentLayoutT9()) {
                if (!isT9KeyboardActive) {
                    isT9KeyboardActive = true
                    updateKeyboardSize()
                }
            }
            applyWindowChrome(newWindow)
        }
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        broadcaster.onImeUpdate(fcitx.runImmediately { inputMethodEntryCached })
        keyboardWindow.updateT9SidebarState(service.getT9SidebarState())

        customBackground.imageDrawable = theme.backgroundDrawable(keyBorder)

        keyboardView = constraintLayout {
            // allow MotionEvent to be delivered to keyboard while pressing on padding views.
            // although it should be default for apps targeting Honeycomb (3.0, API 11) and higher,
            // but it's not the case on some devices ... just set it here
            isMotionEventSplittingEnabled = true
            add(customBackground, lParams(matchParent, matchParent) {
                topOfParent()
                bottomOfParent()
                startOfParent()
                endOfParent()
            })
            add(kawaiiBar.view, lParams(matchParent, dp(kawaiiBar.heightDp)) {
                topOfParent()
                centerHorizontally()
            })
            add(leftPaddingSpace, lParams {
                below(kawaiiBar.view)
                startOfParent()
                bottomOfParent()
            })
            add(rightPaddingSpace, lParams {
                below(kawaiiBar.view)
                endOfParent()
                bottomOfParent()
            })
            add(windowManager.view, lParams {
                below(kawaiiBar.view)
                above(bottomPaddingSpace)
                /**
                 * set start and end constrain in [updateKeyboardSize]
                 */
            })
            add(bottomPaddingSpace, lParams {
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
                bottomOfParent()
            })
            add(keyboardAdjustOverlay, lParams(matchParent, matchParent) {
                topOfParent()
                bottomOfParent()
                startOfParent()
                endOfParent()
            })
            add(runtimeBlockedOverlay, lParams(matchParent, matchParent) {
                topOfParent()
                bottomOfParent()
                startOfParent()
                endOfParent()
            })
        }
        background = theme.backgroundDrawable(keyBorder)

        updateKeyboardSize()
        add(preedit.ui.root, lParams(matchParent, wrapContent) {
            above(keyboardView)
            centerHorizontally()
        })
        add(keyboardView, lParams(matchParent, wrapContent) {
            centerHorizontally()
            bottomOfParent()
        })
        add(popup.root, lParams(matchParent, matchParent) {
            centerVertically()
            centerHorizontally()
        })
        bringChildToFront(preedit.ui.root)
        bringChildToFront(keyboardView)
        bringChildToFront(popup.root)
        applyWindowChrome(keyboardWindow)
        syncRuntimeBlockState()

        keyboardPrefs.registerOnChangeListener(onKeyboardSizeChangeListener)
        InputWindowSizeOverride.registerListener(onKeyboardHeightOverrideChanged)
    }

    private fun updateKeyboardSize() {
        val metrics = currentKeyboardLayoutMetrics()
        windowManager.view.updateLayoutParams {
            height = metrics.heightPx
        }
        bottomPaddingSpace.updateLayoutParams {
            height = metrics.bottomPaddingPx
        }
        if (metrics.leftPaddingPx == 0 && metrics.rightPaddingPx == 0) {
            // hide side padding space views when unnecessary
            leftPaddingSpace.visibility = GONE
            rightPaddingSpace.visibility = GONE
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToEnd = unset
                endToStart = unset
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = VISIBLE
            rightPaddingSpace.visibility = VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = metrics.leftPaddingPx
            }
            rightPaddingSpace.updateLayoutParams {
                width = metrics.rightPaddingPx
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = unset
                endToEnd = unset
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        preedit.ui.root.setPadding(metrics.leftPaddingPx, 0, metrics.rightPaddingPx, 0)
        kawaiiBar.view.setPadding(metrics.leftPaddingPx, 0, metrics.rightPaddingPx, 0)
        updateToolbarSurface()
        keyboardAdjustOverlay.syncState()
        syncRuntimeBlockState()
    }

    private fun applyWindowChrome(activeWindow: org.fcitx.fcitx5.android.input.wm.InputWindow) {
        if (!this::keyboardView.isInitialized) return
        val backgroundColor = Color.TRANSPARENT
        background = theme.backgroundDrawable(keyBorder)
        kawaiiBar.view.visibility = VISIBLE
        keyboardView.setBackgroundColor(backgroundColor)
        windowManager.view.setBackgroundColor(backgroundColor)
        bottomPaddingSpace.setBackgroundColor(backgroundColor)
        leftPaddingSpace.setBackgroundColor(backgroundColor)
        rightPaddingSpace.setBackgroundColor(backgroundColor)
        updateToolbarSurface()
        customBackground.visibility = VISIBLE
    }

    private var standardCandidateBar = false
    fun updateToolbarSurface(candidatesVisible: Boolean? = null) {
        if (candidatesVisible != null) standardCandidateBar = candidatesVisible
        val customTheme = theme as? Theme.Custom
        if (kawaiiBar.isClipboardPanelVisible) { kawaiiBar.view.background = null; return }
        if (customTheme?.renderStyle?.skinLabStandard == true && org.fcitx.fcitx5.android.data.theme.SkinLabStandard.circleToolbar(context) && !standardCandidateBar) { kawaiiBar.view.background = null; return }
        if (customTheme?.toolbarImage != null || customTheme?.renderStyle?.preeditOutsideBar == true) {
            val bar = kawaiiBar.view
            val left = bar.paddingLeft
            val top = bar.paddingTop
            val right = bar.paddingRight
            val bottom = bar.paddingBottom
            val surface = customTheme.barDrawable(keyBorder, context)
            bar.background = if (customTheme.renderStyle?.alignToolbarToKeyFaces == true && customTheme.renderStyle.skinLabStandard != true) {
                val metrics = currentKeyboardLayoutMetrics()
                val keyInset = dp(customTheme.renderStyle.keyVisualHorizontalInsetDp ?: 0f).roundToInt()
                android.graphics.drawable.InsetDrawable(surface,
                    metrics.leftPaddingPx + keyInset, 0, metrics.rightPaddingPx + keyInset, 0)
            } else if (customTheme.renderStyle?.skinLabStandard == true) {
                val metrics = currentKeyboardLayoutMetrics()
                val defaultSide = dp(if (org.fcitx.fcitx5.android.data.theme.SkinLabStandard.landscape(context)) 6 else 3)
                android.graphics.drawable.InsetDrawable(surface,
                    metrics.leftPaddingPx - defaultSide, 0, metrics.rightPaddingPx - defaultSide, 0)
            } else surface
            // InsetDrawable padding belongs to its artwork, not toolbar child layout.
            bar.setPadding(left, top, right, bottom)
        } else {
            kawaiiBar.view.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            bottomMargin = getNavBarBottomInset(insets)
        }
        return insets
    }

    /**
     * called when [InputView] is about to show, or restart
     */
    fun startInput(info: EditorInfo, capFlags: CapabilityFlags, restarting: Boolean = false) {
        val runtimeBlockState = service.getRuntimeInputBlockState()
        syncRuntimeBlockState(runtimeBlockState)
        if (runtimeBlockState != null) {
            return
        }
        broadcaster.onStartInput(info, capFlags)
        returnKeyDrawable.updateDrawableOnEditorInfo(info)
        updateKeyboardSize()
        if (focusChangeResetKeyboard || !restarting) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    fun syncRuntimeBlockState(
        runtimeBlockState: FcitxInputMethodService.RuntimeInputBlockState? = service.getRuntimeInputBlockState()
    ) {
        val blocked = runtimeBlockState != null
        runtimeBlockedOverlay.visibility = if (blocked) VISIBLE else GONE
        preedit.ui.root.visibility = if (blocked) GONE else VISIBLE
        kawaiiBar.view.visibility = if (blocked) GONE else VISIBLE
        windowManager.view.visibility = if (blocked) INVISIBLE else VISIBLE
        customBackground.visibility = if (blocked) GONE else VISIBLE
        if (!blocked) {
            applyWindowChrome(keyboardWindow)
            return
        }
        val state = runtimeBlockState
        runtimeBlockedBadge.text = state.label
        runtimeBlockedBadge.visibility = if (state.label.isBlank()) GONE else VISIBLE
        runtimeBlockedTitle.text = state.title
        runtimeBlockedMessage.text = state.message
        keyboardView.setBackgroundColor(Color.TRANSPARENT)
        bottomPaddingSpace.setBackgroundColor(Color.TRANSPARENT)
        leftPaddingSpace.setBackgroundColor(Color.TRANSPARENT)
        rightPaddingSpace.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onStartHandleFcitxEvent() {
        val inputPanelData = fcitx.runImmediately { inputPanelCached }
        val inputMethodEntry = fcitx.runImmediately { inputMethodEntryCached }
        val statusAreaActions = fcitx.runImmediately { statusAreaActionsCached }
        arrayOf(
            FcitxEvent.InputPanelEvent(inputPanelData),
            FcitxEvent.IMChangeEvent(inputMethodEntry),
            FcitxEvent.StatusAreaEvent(
                FcitxEvent.StatusAreaEvent.Data(statusAreaActions, inputMethodEntry)
            )
        ).forEach { handleFcitxEvent(it) }
    }

    override fun handleFcitxEvent(it: FcitxEvent<*>) {
        when (it) {
            is FcitxEvent.CandidateListEvent -> {
                broadcaster.onCandidateUpdate(it.data)
            }
            is FcitxEvent.ClientPreeditEvent -> {
                preeditEmptyState.updatePreeditEmptyState(clientPreedit = it.data)
                broadcaster.onClientPreeditUpdate(it.data)
            }
            is FcitxEvent.InputPanelEvent -> {
                preeditEmptyState.updatePreeditEmptyState(preedit = it.data.preedit)
                broadcaster.onInputPanelUpdate(it.data)
            }
            is FcitxEvent.IMChangeEvent -> {
                broadcaster.onImeUpdate(it.data)
            }
            is FcitxEvent.StatusAreaEvent -> {
                punctuation.updatePunctuationMapping(it.data.actions)
                broadcaster.onStatusAreaUpdate(it.data.actions)
            }
            else -> {}
        }
    }

    fun updateSelection(start: Int, end: Int) {
        broadcaster.onSelectionUpdate(start, end)
    }

    fun beginKeyboardAdjustment() {
        if (keyboardAdjustDraft != null) return
        keyboardAdjustReset = false
        keyboardAdjustDraft = currentKeyboardLayoutMetrics().toAdjustDraft()
        keyboardAdjustInitialDraft = keyboardAdjustDraft?.copy()
        keyboardAdjustOverlay.visibility = VISIBLE
        KeyboardAdjustmentHostBridge.notifyState(true)
        updateKeyboardSize()
    }

    fun dismissKeyboardAdjustment() {
        cancelKeyboardAdjustment()
    }

    private fun confirmKeyboardAdjustment() {
        val draft = keyboardAdjustDraft ?: return
        persistKeyboardAdjustDraft(draft)
        keyboardAdjustDraft = null
        keyboardAdjustInitialDraft = null
        keyboardAdjustOverlay.visibility = GONE
        KeyboardAdjustmentHostBridge.notifyState(false)
        updateKeyboardSize()
    }

    private fun cancelKeyboardAdjustment() {
        if (keyboardAdjustDraft == null) return
        keyboardAdjustDraft = null
        keyboardAdjustInitialDraft = null
        keyboardAdjustReset = false
        keyboardAdjustOverlay.visibility = GONE
        KeyboardAdjustmentHostBridge.notifyState(false)
        updateKeyboardSize()
    }

    private fun resetKeyboardAdjustment() {
        keyboardAdjustReset = true
        keyboardAdjustDraft = currentKeyboardLayoutMetrics(useDefaults = true).toAdjustDraft()
        updateKeyboardSize()
    }

    private fun KeyboardLayoutMetrics.toAdjustDraft() = KeyboardAdjustDraft(
        heightPercent = heightPx * 100f / resources.displayMetrics.heightPixels,
        leftPaddingDp = pxToDp(leftPaddingPx.toFloat()),
        rightPaddingDp = pxToDp(rightPaddingPx.toFloat()),
        bottomPaddingDp = pxToDp(bottomPaddingPx.toFloat()),
    )

    private fun updateKeyboardAdjustDraft(transform: (KeyboardAdjustDraft) -> KeyboardAdjustDraft) {
        val current = keyboardAdjustDraft ?: return
        keyboardAdjustDraft = normalizeKeyboardAdjustDraft(transform(current))
        updateKeyboardSize()
    }

    private fun currentKeyboardLayoutMetrics(useDefaults: Boolean = false): KeyboardLayoutMetrics {
        val standard = org.fcitx.fcitx5.android.data.theme.SkinLabStandard
        val style = (theme as? Theme.Custom)?.renderStyle
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val saved = resolvePersistedKeyboardAdjustDraft()
        val draft = keyboardAdjustDraft.takeUnless { useDefaults }
        fun hasValue(pref: org.fcitx.fcitx5.android.data.prefs.ManagedPreference.PInt) =
            !useDefaults && pref.sharedPreferences.contains(pref.key)
        val defaultHeight = if (standard.enabled(theme)) dp(standard.grid(context))
            else style?.layout?.keyboardHeight(landscape)?.let { dp(it) }
                ?: resources.displayMetrics.heightPixels * defaultKeyboardHeightPercent() / 100
        val defaultSide = if (standard.enabled(theme)) (if (landscape) 6 else 3)
            else style?.layout?.sidePadding(landscape) ?: currentKeyboardSidePaddingPreference().defaultValue
        val defaultBottom = if (standard.enabled(theme)) 6
            else style?.layout?.bottomPadding(landscape) ?: currentKeyboardBottomPaddingPreference().defaultValue
        val height = draft?.heightPercent ?: saved.heightPercent.takeIf { hasValue(currentKeyboardHeightPreference()) }
        val customWidth = hasValue(currentKeyboardSidePaddingPreference()) || hasValue(currentKeyboardHorizontalOffsetPreference())
        val geometry = normalizeKeyboardAdjustDraft(KeyboardAdjustDraft(
            heightPercent = height ?: defaultHeight * 100f / resources.displayMetrics.heightPixels,
            leftPaddingDp = draft?.leftPaddingDp ?: if (customWidth) saved.leftPaddingDp else defaultSide,
            rightPaddingDp = draft?.rightPaddingDp ?: if (customWidth) saved.rightPaddingDp else defaultSide,
            bottomPaddingDp = draft?.bottomPaddingDp ?: if (hasValue(currentKeyboardBottomPaddingPreference())) saved.bottomPaddingDp else defaultBottom,
        ))
        val overrideHeight = InputWindowSizeOverride.getKeyboardHeightPxOverride()
            ?: InputWindowSizeOverride.getKeyboardHeightPercentOverride()?.let { resources.displayMetrics.heightPixels * it / 100 }
        return KeyboardLayoutMetrics(
            heightPx = overrideHeight ?: (resources.displayMetrics.heightPixels * geometry.heightPercent / 100f).roundToInt(),
            leftPaddingPx = dp(geometry.leftPaddingDp),
            rightPaddingPx = dp(geometry.rightPaddingDp),
            bottomPaddingPx = dp(geometry.bottomPaddingDp),
        )
    }

    private fun resolvePersistedKeyboardAdjustDraft(): KeyboardAdjustDraft {
        val sidePadding = when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> keyboardSidePaddingLandscape
            else -> keyboardSidePadding
        }.getValue()
        val offset = keyboardHorizontalOffsetDp.coerceIn(-sidePadding, sidePadding)
        return normalizeKeyboardAdjustDraft(
            KeyboardAdjustDraft(
                heightPercent = when (resources.configuration.orientation) {
                    Configuration.ORIENTATION_LANDSCAPE ->
                        if (isT9KeyboardActive) t9KeyboardHeightPercentLandscape else keyboardHeightPercentLandscape
                    else ->
                        if (isT9KeyboardActive) t9KeyboardHeightPercent else keyboardHeightPercent
                }.getValue().toFloat(),
                leftPaddingDp = sidePadding + offset,
                rightPaddingDp = sidePadding - offset,
                bottomPaddingDp = when (resources.configuration.orientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> keyboardBottomPaddingLandscape
                    else -> keyboardBottomPadding
                }.getValue(),
            )
        )
    }

    private fun normalizeKeyboardAdjustDraft(draft: KeyboardAdjustDraft): KeyboardAdjustDraft {
        val maxCombinedPaddingDp = (screenWidthDp() - MinKeyboardWidthDp).coerceAtLeast(0).coerceAtMost(600)
        var leftPaddingDp = draft.leftPaddingDp.coerceAtLeast(0)
        var rightPaddingDp = draft.rightPaddingDp.coerceAtLeast(0)
        val combined = leftPaddingDp + rightPaddingDp
        if (combined > maxCombinedPaddingDp) {
            val scale = maxCombinedPaddingDp.toFloat() / combined.toFloat()
            leftPaddingDp = (leftPaddingDp * scale).roundToInt()
            rightPaddingDp = (rightPaddingDp * scale).roundToInt()
        }
        return draft.copy(
            heightPercent = draft.heightPercent.coerceIn(keyboardHeightPercentRange().first.toFloat(), keyboardHeightPercentRange().last.toFloat()),
            leftPaddingDp = leftPaddingDp,
            rightPaddingDp = rightPaddingDp,
            bottomPaddingDp = draft.bottomPaddingDp.coerceIn(0, 100),
        )
    }

    private fun persistKeyboardAdjustDraft(draft: KeyboardAdjustDraft) {
        val normalized = normalizeKeyboardAdjustDraft(draft)
        val initial = keyboardAdjustInitialDraft
        if (keyboardAdjustReset) {
            listOf(currentKeyboardHeightPreference(), currentKeyboardSidePaddingPreference(),
                currentKeyboardHorizontalOffsetPreference(), currentKeyboardBottomPaddingPreference()).forEach {
                it.sharedPreferences.edit().remove(it.key).apply()
            }
        }
        val baseline = if (keyboardAdjustReset) currentKeyboardLayoutMetrics(useDefaults = true).toAdjustDraft() else initial
        if (normalized.heightPercent != baseline?.heightPercent) {
            currentKeyboardHeightPreference().setValue(normalized.heightPercent.roundToInt())
        }
        if (normalized.bottomPaddingDp != baseline?.bottomPaddingDp) {
            currentKeyboardBottomPaddingPreference().setValue(normalized.bottomPaddingDp)
        }
        val sidePadding = ((normalized.leftPaddingDp + normalized.rightPaddingDp) / 2f).roundToInt()
            .coerceIn(0, 300)
        val offset = ((normalized.leftPaddingDp - normalized.rightPaddingDp) / 2f).roundToInt()
            .coerceIn(-300, 300)
            .coerceIn(-sidePadding, sidePadding)
        if (normalized.leftPaddingDp != baseline?.leftPaddingDp || normalized.rightPaddingDp != baseline?.rightPaddingDp) {
            currentKeyboardSidePaddingPreference().setValue(sidePadding)
            currentKeyboardHorizontalOffsetPreference().setValue(offset)
        }
    }

    private fun currentKeyboardHeightPreference() = when (resources.configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE ->
            if (isT9KeyboardActive) t9KeyboardHeightPercentLandscape else keyboardHeightPercentLandscape
        else ->
            if (isT9KeyboardActive) t9KeyboardHeightPercent else keyboardHeightPercent
    }

    private fun currentKeyboardSidePaddingPreference() = when (resources.configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> keyboardSidePaddingLandscape
        else -> keyboardSidePadding
    }

    private fun currentKeyboardHorizontalOffsetPreference() = when (resources.configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> keyboardHorizontalOffsetLandscape
        else -> keyboardHorizontalOffset
    }

    private fun currentKeyboardBottomPaddingPreference() = when (resources.configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> keyboardBottomPaddingLandscape
        else -> keyboardBottomPadding
    }

    private fun keyboardHeightPercentRange(): IntRange =
        if (isT9KeyboardActive) 5..50 else 10..90

    private fun defaultKeyboardHeightPercent(): Int =
        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> 49
            else -> 30
        }

    // Skin Lab uses design-unit density; configuration.screenWidthDp uses device density.
    private fun screenWidthDp(): Int = pxToDp((width.takeIf { it > 0 }
        ?: resources.displayMetrics.widthPixels).toFloat())

    private fun pxToDp(px: Float): Int =
        (px / resources.displayMetrics.density).roundToInt()

    private inner class KeyboardAdjustOverlay(context: android.content.Context) : FrameLayout(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val handleInset = context.dp(10)
        private val actionSpacing = context.dp(16)
        private val scrim = View(context).apply {
            setBackgroundColor(theme.keyTextColor.alpha(0.42f))
            setOnClickListener { cancelKeyboardAdjustment() }
        }
        private val topHandle = createHandle(horizontal = true, description = "top-handle")
        private val bottomHandle = createHandle(horizontal = true, description = "bottom-handle")
        private val leftHandle = createHandle(horizontal = false, description = "left-handle")
        private val rightHandle = createHandle(horizontal = false, description = "right-handle")
        private val resetButton = createActionButton(
            R.drawable.ic_baseline_undo_24,
            context.getString(R.string.reset),
        ) { resetKeyboardAdjustment() }
        private val moveButton = createActionButton(
            R.drawable.ic_baseline_drag_handle_24,
            context.getString(R.string.keyboard_adjust_move),
        ) { }
        private val moveButtonIcon: View
            get() = moveButton.getChildAt(0)
        private val moveButtonLabel: View
            get() = moveButton.getChildAt(1)
        private val doneButton = createActionButton(
            R.drawable.ic_baseline_check_24,
            context.getString(R.string.done),
        ) { confirmKeyboardAdjustment() }
        private val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(24).toFloat()
                setColor(theme.barColor.alpha(0.88f))
            }
            setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
            addView(resetButton)
            addView(moveButton)
            addView(doneButton)
        }

        init {
            isClickable = true
            isFocusable = true
            addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(topHandle)
            addView(bottomHandle)
            addView(leftHandle)
            addView(rightHandle)
            addView(actionRow)
            installDragGesture(topHandle) { start, _, dy ->
                val startHeightPx = resources.displayMetrics.heightPixels * start.heightPercent / 100f
                val nextHeightPercent =
                    ((startHeightPx - dy) * 100f / resources.displayMetrics.heightPixels)
                start.copy(heightPercent = nextHeightPercent)
            }
            installDragGesture(bottomHandle) { start, _, dy ->
                start.copy(bottomPaddingDp = start.bottomPaddingDp - pxToDp(dy))
            }
            installDragGesture(leftHandle) { start, dx, _ ->
                start.copy(leftPaddingDp = start.leftPaddingDp + pxToDp(dx))
            }
            installDragGesture(rightHandle) { start, dx, _ ->
                start.copy(rightPaddingDp = start.rightPaddingDp - pxToDp(dx))
            }
            installDragGesture(moveButton) { start, dx, dy ->
                val horizontalDelta = pxToDp(dx).coerceIn(-start.leftPaddingDp, start.rightPaddingDp)
                start.copy(
                    leftPaddingDp = start.leftPaddingDp + horizontalDelta,
                    rightPaddingDp = start.rightPaddingDp - horizontalDelta,
                    bottomPaddingDp = start.bottomPaddingDp - pxToDp(dy),
                )
            }
            installDragGesture(moveButtonIcon) { start, dx, dy ->
                val horizontalDelta = pxToDp(dx).coerceIn(-start.leftPaddingDp, start.rightPaddingDp)
                start.copy(
                    leftPaddingDp = start.leftPaddingDp + horizontalDelta,
                    rightPaddingDp = start.rightPaddingDp - horizontalDelta,
                    bottomPaddingDp = start.bottomPaddingDp - pxToDp(dy),
                )
            }
            installDragGesture(moveButtonLabel) { start, dx, dy ->
                val horizontalDelta = pxToDp(dx).coerceIn(-start.leftPaddingDp, start.rightPaddingDp)
                start.copy(
                    leftPaddingDp = start.leftPaddingDp + horizontalDelta,
                    rightPaddingDp = start.rightPaddingDp - horizontalDelta,
                    bottomPaddingDp = start.bottomPaddingDp - pxToDp(dy),
                )
            }
        }

        fun syncState() {
            if (visibility != VISIBLE) return
            post { positionControls() }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = true

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            if (visibility == VISIBLE) {
                positionControls()
            }
        }

        private fun createHandle(horizontal: Boolean, description: String): View {
            val lineLength = context.dp(22)
            val lineThickness = context.dp(2)
            val lineSpacing = context.dp(3)
            return LinearLayout(context).apply {
                orientation = if (horizontal) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                repeat(3) { index ->
                    addView(
                        View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = lineThickness.toFloat()
                                setColor(theme.keyTextColor.alpha(0.9f))
                            }
                        },
                        LinearLayout.LayoutParams(
                            if (horizontal) lineLength else lineThickness,
                            if (horizontal) lineThickness else lineLength,
                        ).apply {
                            if (horizontal && index > 0) {
                                topMargin = lineSpacing
                            } else if (!horizontal && index > 0) {
                                marginStart = lineSpacing
                            }
                        }
                    )
                }
                layoutParams = LayoutParams(
                    if (horizontal) context.dp(56) else context.dp(42),
                    if (horizontal) context.dp(42) else context.dp(56),
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = context.dp(20).toFloat()
                    setColor(theme.barColor.alpha(0.82f))
                }
                contentDescription = description
            }
        }

        private fun createActionButton(
            icon: Int,
            label: String,
            onClick: () -> Unit,
        ): LinearLayout {
            val iconButton = ToolButton(context, icon, theme).apply {
                layoutParams = LinearLayout.LayoutParams(context.dp(56), context.dp(56))
                contentDescription = label
                setOnClickListener { onClick() }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(theme.keyTextColor.alpha(0.16f))
                }
            }
            val labelView = TextView(context).apply {
                gravity = Gravity.CENTER
                text = label
                setTextColor(theme.keyTextColor)
                textSize = 12f
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                contentDescription = label
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = actionSpacing / 2
                    marginEnd = actionSpacing / 2
                }
                addView(iconButton)
                addView(
                    labelView,
                    LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT,
                    )
                )
            }
        }

        private fun installDragGesture(
            target: View,
            onDrag: (KeyboardAdjustDraft, Float, Float) -> KeyboardAdjustDraft,
        ) {
            var downRawX = 0f
            var downRawY = 0f
            var dragging = false
            var startDraft: KeyboardAdjustDraft? = null
            val longPressRunnable = Runnable {
                if (keyboardAdjustDraft == null) return@Runnable
                dragging = true
                target.isPressed = true
                target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            target.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        dragging = false
                        startDraft = keyboardAdjustDraft?.copy()
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        view.postDelayed(
                            longPressRunnable,
                            ViewConfiguration.getLongPressTimeout().toLong()
                        )
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!dragging && kotlin.math.hypot(dx, dy) >= ViewConfiguration.get(context).scaledTouchSlop) {
                            view.removeCallbacks(longPressRunnable)
                            dragging = true
                            view.isPressed = true
                        }
                        if (dragging) {
                            val initial = startDraft ?: return@setOnTouchListener true
                            keyboardAdjustDraft = normalizeKeyboardAdjustDraft(onDrag(initial, dx, dy))
                            updateKeyboardSize()
                        }
                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        view.removeCallbacks(longPressRunnable)
                        view.isPressed = false
                        dragging = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        true
                    }

                    else -> false
                }
            }
        }

    fun currentKeyboardWindowHeightPx(): Int {
        val metrics = currentKeyboardLayoutMetrics()
        return dp(kawaiiBar.heightDp) + metrics.heightPx + metrics.bottomPaddingPx
    }

        private fun positionControls() {
            if (windowManager.view.width == 0 || windowManager.view.height == 0) return
            val keyboardLeft = windowManager.view.left.toFloat()
            val keyboardTop = windowManager.view.top.toFloat()
            val keyboardRight = windowManager.view.right.toFloat()
            val keyboardBottom = windowManager.view.bottom.toFloat()
            val centerX = (keyboardLeft + keyboardRight) / 2f
            val centerY = (keyboardTop + keyboardBottom) / 2f
            placeCentered(
                topHandle,
                centerX,
                (keyboardTop + handleInset).coerceAtLeast(topHandle.measuredHeight / 2f),
            )
            placeCentered(
                bottomHandle,
                centerX,
                (keyboardBottom - handleInset).coerceAtMost(height - bottomHandle.measuredHeight / 2f),
            )
            placeCentered(
                leftHandle,
                (keyboardLeft + handleInset).coerceAtLeast(leftHandle.measuredWidth / 2f),
                centerY,
            )
            placeCentered(
                rightHandle,
                (keyboardRight - handleInset).coerceAtMost(width - rightHandle.measuredWidth / 2f),
                centerY,
            )
            placeCentered(actionRow, centerX, centerY)
        }

        private fun placeCentered(target: View, centerX: Float, centerY: Float) {
            target.x = (centerX - target.measuredWidth / 2f)
                .coerceIn(0f, (width - target.measuredWidth).coerceAtLeast(0).toFloat())
            target.y = (centerY - target.measuredHeight / 2f)
                .coerceIn(0f, (height - target.measuredHeight).coerceAtLeast(0).toFloat())
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        if (runtimeBlockedOverlay.visibility == VISIBLE) return false
        return kawaiiBar.handleInlineSuggestions(response)
    }

    override fun onDetachedFromWindow() {
        val isCurrentInputView = service.isCurrentInputView(this)
        if (isCurrentInputView) {
            service.onT9ModeChanged = null
            service.onT9SidebarStateChanged = null
            service.onKeyboardLayoutSwitchRequested = null
            service.onImeStateRefreshRequested = null
        }
        keyboardWindow.onLayoutChanged = null
        windowManager.onActiveWindowChanged = null
        keyboardAdjustDraft = null
        keyboardPrefs.unregisterOnChangeListener(onKeyboardSizeChangeListener)
        InputWindowSizeOverride.unregisterListener(onKeyboardHeightOverrideChanged)
        // clear DynamicScope, implies that InputView should not be attached again after detached.
        scope.clear()
        super.onDetachedFromWindow()
    }
}
