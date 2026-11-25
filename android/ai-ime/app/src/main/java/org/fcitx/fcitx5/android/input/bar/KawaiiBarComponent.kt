/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Size
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.ViewAnimator
import android.widget.inline.InlineContentView
import androidx.core.view.doOnPreDraw
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlag
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxEvent.CandidateListEvent
import org.fcitx.fcitx5.android.core.FcitxEvent.InputPanelEvent
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.State.ClickToAttachWindow
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.State.ClickToDetachWindow
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.State.Hidden
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.BooleanKey.CandidateEmpty
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.BooleanKey.PreeditEmpty
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.TransitionEvent.CandidatesUpdated
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.TransitionEvent.ExtendedWindowAttached
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.TransitionEvent.PreeditUpdated
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.TransitionEvent.WindowDetached
import org.fcitx.fcitx5.android.input.bar.ui.CandidateUi
import org.fcitx.fcitx5.android.input.bar.ui.IdleUi
import org.fcitx.fcitx5.android.input.bar.ui.TitleUi
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.candidates.expanded.ExpandedCandidateStyle
import org.fcitx.fcitx5.android.input.candidates.expanded.window.FlexboxExpandedCandidateWindow
import org.fcitx.fcitx5.android.input.candidates.expanded.window.GridExpandedCandidateWindow
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateComponent
import org.fcitx.fcitx5.android.input.clipboard.ClipboardWindow
import org.fcitx.fcitx5.android.input.dependency.UniqueViewComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.inputView
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.editing.TextEditingWindow
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubLaunchRequest
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubHostBridge
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.status.StatusAreaHostBridge
import org.fcitx.fcitx5.android.input.status.StatusAreaWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.AppUtil
import org.fcitx.fcitx5.android.utils.InputMethodUtil
import org.fcitx.fcitx5.android.utils.toast
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.manager.must
import splitties.bitflags.hasFlag
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

class KawaiiBarComponent : UniqueViewComponent<KawaiiBarComponent, FrameLayout>(),
    InputBroadcastReceiver {

    private val context by manager.context()
    private val theme by manager.theme()
    private val service by manager.inputMethodService()
    private val windowManager: InputWindowManager by manager.must()
    private val inputView by manager.inputView()
    private val horizontalCandidate: HorizontalCandidateComponent by manager.must()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val popup: PopupComponent by manager.must()

    private val prefs = AppPrefs.getInstance()
    private val skinStyle get() = (theme as? org.fcitx.fcitx5.android.data.theme.Theme.Custom)?.renderStyle
    private var skinLayout = if (prefs.keyboard.useT9KeyboardLayout.getValue()) "t9" else "qwerty"
    private var clipboardVisible = false
    private val barBodyHeightDp get() = skinStyle?.resolvedBarBodyHeight(skinLayout, org.fcitx.fcitx5.android.data.theme.SkinLabStandard.bar(context)) ?.plus(OEM_TOOLBAR_EXTRA_HEIGHT_DP) ?: (HEIGHT + OEM_TOOLBAR_EXTRA_HEIGHT_DP)
    private val panelTopGapDp get() = if (skinStyle?.customToolbarLayout == true)
        skinStyle?.topSpacingHeightDp((theme as? org.fcitx.fcitx5.android.data.theme.Theme.Custom)?.toolbarTopInsetDp ?: 0) ?: 0 else 0
    private val baseHeightDp get() = (skinStyle?.resolvedBarTotalHeight(skinLayout, org.fcitx.fcitx5.android.data.theme.SkinLabStandard.bar(context)) ?: HEIGHT) + panelTopGapDp + OEM_TOOLBAR_EXTRA_HEIGHT_DP

    val isClipboardPanelVisible get() = clipboardVisible
    val heightDp get() = if (clipboardVisible) baseHeightDp.coerceAtLeast(56 + OEM_TOOLBAR_EXTRA_HEIGHT_DP) else baseHeightDp

    private val expandedCandidateStyle by prefs.keyboard.expandedCandidateStyle
    private val expandToolbarByDefault by prefs.keyboard.expandToolbarByDefault
    private val toolbarNumRowOnPassword by prefs.keyboard.toolbarNumRowOnPassword
    private val showVoiceInputButton by prefs.keyboard.showVoiceInputButton
    private val preferredVoiceInput by prefs.keyboard.preferredVoiceInput

    private var isInlineSuggestionPresent: Boolean = false
    private var isCapabilityFlagsPassword: Boolean = false
    private var isKeyboardLayoutNumber: Boolean = false
    private var isToolbarManuallyToggled: Boolean = false

    private enum class NumberRowState { Auto, ForceShow, ForceHide }

    private var numberRowState = NumberRowState.Auto

    private fun evalIdleUiState(fromUser: Boolean = false) {
        val newState = when {
            numberRowState == NumberRowState.ForceShow -> IdleUi.State.NumberRow
            isInlineSuggestionPresent -> IdleUi.State.InlineSuggestion
            isCapabilityFlagsPassword && !isKeyboardLayoutNumber && numberRowState != NumberRowState.ForceHide -> IdleUi.State.NumberRow
            prefs.internal.quickReplyEnabled.getValue() && service.hostClipboard.current() != null -> IdleUi.State.Clipboard
            /**
             * state matrix:
             *                               expandToolbarByDefault
             *                          |   \   |    true |   false
             * isToolbarManuallyToggled |  true |   Empty | Toolbar
             *                          | false | Toolbar |   Empty
             */
            expandToolbarByDefault == isToolbarManuallyToggled -> IdleUi.State.Empty
            else -> IdleUi.State.Toolbar
        }
        if (newState != idleUi.currentState) idleUi.updateState(newState, fromUser)
        updateClipboardHeight()
    }

    private val hideKeyboardCallback = View.OnClickListener {
        service.requestHideSelf(0)
    }

    private val swipeDownExpandCallback = CustomGestureView.OnGestureListener { _, e ->
        if (e.type == CustomGestureView.GestureType.Up && e.totalY > 0) {
            service.requestHideSelf(0)
            true
        } else false
    }

    // Combined gesture: determine primary direction by comparing totalX and totalY.
    // - If horizontal is dominant and left, show number row (when allowed).
    // - If vertical is dominant and down, hide keyboard.
    private val swipeHideKeyboardCallback = CustomGestureView.OnGestureListener { v, e ->
        require(v is ToolButton)
        if (e.type == CustomGestureView.GestureType.Cancel) {
            v.iconRotation = 0f
            return@OnGestureListener false
        }
        val numberRowAvailable = isCapabilityFlagsPassword && !isKeyboardLayoutNumber
        if (numberRowAvailable) {
            val dir = if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) 1 else -1
            // `e.x` and `e.y` are relative to the view's top-left corner
            val centerX = e.x - v.width / 2f
            val centerY = e.y - v.height / 2f

            val distance = hypot(centerX, centerY)
            // the button is ↓, so apply -90 degrees offset
            var angle = atan2(-centerX, centerY) * (180f / PI.toFloat())

            when (e.type) {
                CustomGestureView.GestureType.Move -> {
                    angle = if (angle in -45f..45f) {
                        angle.coerceIn(-10f, 10f)
                    } else abs(angle).coerceIn(90f - 10f, 90f + 10f) * dir
                    v.iconRotation = angle
                }
                CustomGestureView.GestureType.Up -> {
                    val handled = when (angle) {
                        in -45f..45f if distance > v.swipeThresholdX -> {
                            service.requestHideSelf(0)
                            true
                        }
                        !in -45f..45f if distance > v.swipeThresholdY -> {
                            v.iconRotation = 90f * dir
                            numberRowState = NumberRowState.ForceShow
                            evalIdleUiState(fromUser = true)
                            true
                        }
                        else -> false
                    }
                    v.iconRotation = 0f
                    return@OnGestureListener handled
                }
                else -> {}
            }
        }

        if (e.type == CustomGestureView.GestureType.Up && abs(e.totalY) > abs(e.totalX) && e.totalY > 0) {
            service.requestHideSelf(0)
            true
        } else false
    }

    private var voiceInputSubtype: Pair<String, InputMethodSubtype>? = null

    private val switchToVoiceInputCallback = View.OnClickListener {
        val (id, subtype) = voiceInputSubtype ?: return@OnClickListener
        InputMethodUtil.switchInputMethod(service, id, subtype)
    }

    private val idleUi: IdleUi by lazy {
        IdleUi(context, theme, popup, commonKeyActionListener).apply {
            menuButton.setOnClickListener {
                when (idleUi.currentState) {
                    IdleUi.State.Empty -> {
                        isToolbarManuallyToggled = !expandToolbarByDefault
                        evalIdleUiState(fromUser = true)
                    }
                    IdleUi.State.Toolbar -> {
                        isToolbarManuallyToggled = expandToolbarByDefault
                        evalIdleUiState(fromUser = true)
                    }
                    else -> {
                        isToolbarManuallyToggled = !expandToolbarByDefault
                        service.hostClipboard.dismiss()
                        evalIdleUiState(fromUser = true)
                    }
                }
            }
            hideKeyboardButton.apply {
                setOnClickListener(hideKeyboardCallback)
                swipeEnabled = true
                swipeThresholdY = dp(HEIGHT.toFloat())
                swipeThresholdX = swipeThresholdY
                onGestureListener = swipeHideKeyboardCallback
            }
            buttonsUi.apply {
                undoButton.setOnClickListener {
                    service.sendCombinationKeyEvents(KeyEvent.KEYCODE_Z, ctrl = true)
                }
                redoButton.setOnClickListener {
                    service.sendCombinationKeyEvents(KeyEvent.KEYCODE_Z, ctrl = true, shift = true)
                }
                cursorMoveButton.setOnClickListener {
                    windowManager.attachWindow(TextEditingWindow())
                }
                clipboardButton.setOnClickListener {
                    windowManager.attachWindow(ClipboardWindow())
                }
                materialHubButton.setOnClickListener {
                    ToolbarActionHostBridge.launchMaterialHub()
                }
                moreButton.setOnClickListener {
                    windowManager.attachWindow(StatusAreaWindow())
                }
            }
            numberRow.apply {
                onCollapseListener = {
                    numberRowState = NumberRowState.ForceHide
                    evalIdleUiState(fromUser = true)
                }
            }
        }
    }

    private val candidateUi by lazy {
        CandidateUi(context, theme, horizontalCandidate.view).apply { updateRowHeight(barBodyHeightDp) }.apply {
            expandButton.apply {
                swipeEnabled = true
                swipeThresholdY = dp(HEIGHT.toFloat())
                onGestureListener = swipeDownExpandCallback
            }
        }
    }

    val candidateReading: String get() = candidateUi.preeditTextView.text.toString()

    // Retain the skin's composition label above the expanded list.
    val expandedHeaderHeightPx: Int
        get() {
            val label = candidateUi.preeditTextView
            if (label.visibility != View.VISIBLE) return 0
            val params = label.layoutParams as ViewGroup.MarginLayoutParams
            val labelHeight = params.height.takeIf { it > 0 } ?: label.lineHeight
            return (candidateUi.root.layoutParams as FrameLayout.LayoutParams).topMargin +
                params.topMargin + labelHeight + label.paddingTop + label.paddingBottom
        }

    private val titleUi by lazy {
        TitleUi(context, theme)
    }

    private val handwritingUi by lazy { org.fcitx.fcitx5.android.input.handwriting.HandwritingCandidateStrip(service, theme).apply { ui.updateRowHeight(barBodyHeightDp) } }
    private var handwritingOwner: Any? = null
    fun showHandwritingCandidates(owner: Any, words: List<String>, select: (String) -> Unit) {
        if (words.isEmpty()) { clearHandwritingCandidates(owner); return }
        service.hostClipboard.dismiss()
        handwritingOwner = owner
        handwritingUi.show(words, select)
        switchUiByState(barStateMachine.currentState)
    }
    fun clearHandwritingCandidates(owner: Any) {
        if (handwritingOwner !== owner) return
        handwritingOwner = null; handwritingUi.clear()
        switchUiByState(barStateMachine.currentState)
    }

    private val barStateMachine = KawaiiBarStateMachine.new {
        switchUiByState(it)
    }

    val expandButtonStateMachine = ExpandButtonStateMachine.new {
        horizontalCandidate.view.visibility = if (it == ClickToDetachWindow) View.INVISIBLE else View.VISIBLE
        when (it) {
            ClickToAttachWindow -> {
                setExpandButtonToAttach()
                setExpandButtonEnabled(true)
            }
            ClickToDetachWindow -> {
                setExpandButtonEnabled(false)
            }
            Hidden -> {
                setExpandButtonEnabled(false)
            }
        }
    }

    // set expand candidate button to create expand candidate
    private fun setExpandButtonToAttach() {
        candidateUi.expandButton.setOnClickListener {
            windowManager.attachWindow(
                if (service.usesChineseCandidateGrid() || expandedCandidateStyle == ExpandedCandidateStyle.Grid)
                    GridExpandedCandidateWindow() else FlexboxExpandedCandidateWindow()
            )
        }
        candidateUi.expandButton.setIcon(R.drawable.ic_baseline_expand_more_24)
        candidateUi.expandButton.contentDescription = context.getString(R.string.expand_candidates_list)
    }

    private fun setExpandButtonEnabled(enabled: Boolean) {
        candidateUi.expandButton.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    }

    private fun switchUiByState(state: KawaiiBarStateMachine.State) {
        val index = if (handwritingOwner != null) 3 else state.ordinal
        if (view.displayedChild == index) return
        val new = view.getChildAt(index)
        if (new != titleUi.root) {
            titleUi.setReturnButtonOnClickListener { }
            titleUi.setTitle("")
            titleUi.removeExtension()
        }
        view.displayedChild = index
        updateClipboardHeight()
        if (skinStyle?.skinLabStandard == true) inputView.updateToolbarSurface(handwritingOwner != null || state == KawaiiBarStateMachine.State.Candidate)
    }

    override val view by lazy {
        ViewAnimator(context).apply {
            background = (theme as? org.fcitx.fcitx5.android.data.theme.Theme.Custom)
                ?.barDrawable(ThemeManager.prefs.keyBorder.getValue(), context)
                ?: android.graphics.drawable.ColorDrawable(
                    if (ThemeManager.prefs.keyBorder.getValue()) Color.TRANSPARENT
                    else theme.barColor
                )
            // Drawable insets must not become child padding: preedit owns its own strip.
            setPadding(0, 0, 0, 0)
            add(idleUi.root, lParams(matchParent, matchParent) {
                topMargin = context.dp(panelTopGapDp + (skinStyle?.reservedPreeditDp ?: 0))
            })
            add(candidateUi.root, lParams(matchParent, matchParent) { topMargin = if (skinStyle?.preeditInTopSpacing == true) 0 else context.dp(panelTopGapDp) })
            add(titleUi.root, lParams(matchParent, matchParent) {
                topMargin = context.dp(panelTopGapDp + (skinStyle?.reservedPreeditDp ?: 0))
            })
            add(handwritingUi.ui.root, lParams(matchParent, matchParent) { topMargin = if (skinStyle?.preeditInTopSpacing == true) 0 else context.dp(panelTopGapDp) })
        }
    }

    override fun onScopeSetupFinished(scope: DynamicScope) {
        bindToolbarActionBridge()
        idleUi.clipboardUi.paste.setOnClickListener {
            service.hostClipboard.current()?.let { service.commitText(it.text) }
            service.hostClipboard.dismiss()
        }
        idleUi.clipboardUi.reply.setOnClickListener {
            if (!prefs.internal.quickReplyEnabled.getValue()) return@setOnClickListener
            val copy = service.hostClipboard.current() ?: return@setOnClickListener
            // Snapshot before dispatch: a host may unregister itself inside its callback.
            val hostHandled = service.isClipboardQuickReplyHostHandled()
            if (MaterialHubHostBridge.launch(MaterialHubLaunchRequest(
                    target = MaterialHubLaunchRequest.Target.AI_REPLY,
                    hostPackageName = copy.host,
                    clipboardReplyText = copy.text,
                )) && !hostHandled) service.hostClipboard.dismiss()
        }
        var collection: kotlinx.coroutines.Job? = null
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                collection?.cancel()
                collection = service.lifecycleScope.launch {
                    service.hostClipboard.suggestion.collect {
                        idleUi.clipboardUi.setReplyEnabled(service.isClipboardQuickReplyEnabled())
                        idleUi.clipboardUi.paste.text = it?.text.orEmpty()
                        evalIdleUiState()
                    }
                }
            }
            override fun onViewDetachedFromWindow(v: View) { collection?.cancel(); collection = null }
        })
    }

    private fun updateClipboardHeight() {
        val visible = idleUi.currentState == IdleUi.State.Clipboard && view.displayedChild == 0
        if (visible) view.doOnPreDraw {
            if (clipboardVisible && service.isInputViewShown) service.hostClipboard.markShown()
        }
        if (clipboardVisible == visible) return
        clipboardVisible = visible
        (idleUi.root.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.topMargin = if (visible) 0 else context.dp(panelTopGapDp + (skinStyle?.reservedPreeditDp ?: 0))
            idleUi.root.layoutParams = it
        }
        inputView.updateToolbarSurface()
        view.layoutParams?.let { it.height = context.dp(heightDp); view.layoutParams = it }
        view.requestLayout()
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        handwritingOwner?.let(::clearHandwritingCandidates)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            idleUi.privateMode(info.imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        }
        isCapabilityFlagsPassword = toolbarNumRowOnPassword && capFlags.has(CapabilityFlag.Password)
        isInlineSuggestionPresent = false
        numberRowState = NumberRowState.Auto
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            idleUi.inlineSuggestionsBar.clear()
        }
        voiceInputSubtype = InputMethodUtil.findVoiceSubtype(preferredVoiceInput)
        val shouldShowVoiceInput =
            false // Local voice is routed through the OEM toolbar and obeys oem_voice_config.
        idleUi.setHideKeyboardIsVoiceInput(
            shouldShowVoiceInput,
            if (shouldShowVoiceInput) switchToVoiceInputCallback else hideKeyboardCallback
        )
        evalIdleUiState()
    }

    override fun onPreeditEmptyStateUpdate(empty: Boolean) {
        if (!empty) service.hostClipboard.dismiss()
        barStateMachine.push(PreeditUpdated, PreeditEmpty to empty)
    }

    override fun onInputPanelUpdate(data: InputPanelEvent.Data) {
        if (!service.isT9KeyboardChineseMode()) {
            val preeditStr = data.preedit.toString()
            candidateUi.updatePreedit(preeditStr)
        }
    }

    override fun onCandidateUpdate(data: CandidateListEvent.Data) {
        if (data.candidates.isNotEmpty()) service.hostClipboard.dismiss()
        barStateMachine.push(CandidatesUpdated, CandidateEmpty to data.candidates.isEmpty())
        
        if (service.isT9KeyboardChineseMode() && data.candidates.isNotEmpty()) {
            val firstRaw = data.candidates[0].textWithComment()
            val match = Regex(" ([a-zA-Z ']+)$").find(firstRaw)
            val pinyin = match?.groupValues?.get(1)?.replace(" ", "'") ?: ""
            candidateUi.updatePreedit(pinyin)
        }
    }

    override fun onWindowAttached(window: InputWindow) {
        when (window) {
            is InputWindow.ExtendedInputWindow<*> -> {
                titleUi.setTitle(window.title)
                window.onCreateBarExtension()?.let { titleUi.addExtension(it, window.showTitle) }
                titleUi.setReturnButtonOnClickListener {
                    windowManager.attachWindow(KeyboardWindow)
                }
                barStateMachine.push(ExtendedWindowAttached)
            }
            else -> {}
        }
    }

    override fun onWindowDetached(window: InputWindow) {
        barStateMachine.push(WindowDetached)
    }

    private val suggestionSize by lazy {
        Size(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(barBodyHeightDp))
    }

    private val directExecutor by lazy {
        Executor { it.run() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            isInlineSuggestionPresent = false
            evalIdleUiState()
            idleUi.inlineSuggestionsBar.clear()
            return true
        }
        var pinned: InlineSuggestion? = null
        val scrollable = mutableListOf<InlineSuggestion>()
        var extraPinnedCount = 0
        suggestions.forEach {
            if (it.info.isPinned) {
                if (pinned == null) {
                    pinned = it
                } else {
                    scrollable.add(extraPinnedCount++, it)
                }
            } else {
                scrollable.add(it)
            }
        }
        service.lifecycleScope.launch {
            idleUi.inlineSuggestionsBar.setPinnedView(
                pinned?.let { inflateInlineContentView(it) }
            )
        }
        service.lifecycleScope.launch {
            val views = scrollable.map { s ->
                service.lifecycleScope.async {
                    inflateInlineContentView(s)
                }
            }.awaitAll()
            idleUi.inlineSuggestionsBar.setScrollableViews(views)
        }
        isInlineSuggestionPresent = true
        evalIdleUiState()
        return true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun inflateInlineContentView(suggestion: InlineSuggestion): InlineContentView? {
        return suspendCancellableCoroutine { c ->
            // callback view might be null
            suggestion.inflate(context, suggestionSize, directExecutor) { v ->
                c.resume(v)
            }
        }
    }

    companion object {
        private const val OEM_TOOLBAR_EXTRA_HEIGHT_DP = 12
        const val HEIGHT = 60
    }

    fun onKeyboardLayoutSwitched(isNumber: Boolean, layout: String = "qwerty") {
        val oldHeight = heightDp
        skinLayout = layout
        if (oldHeight != heightDp) {
            view.layoutParams?.let { it.height = context.dp(heightDp); view.layoutParams = it }
            candidateUi.updateRowHeight(barBodyHeightDp)
            handwritingUi.ui.updateRowHeight(barBodyHeightDp)
            view.requestLayout()
        }
        isKeyboardLayoutNumber = isNumber
        evalIdleUiState()
    }

    private fun bindToolbarActionBridge() {
        ToolbarActionHostBridge.bindLaunchVoiceInput { service.launchLocalVoiceInput() }
        ToolbarActionHostBridge.bindLaunchKeyboardSelection {
            windowManager.attachWindow(StatusAreaWindow(startInKeyboardSelectionPage = true))
            true
        }
        ToolbarActionHostBridge.bindLaunchEmoji {
            windowManager.attachWindow(PickerWindow.Key.Emoji)
            true
        }
        ToolbarActionHostBridge.bindLaunchMaterialHub { request ->
            if (!MaterialHubHostBridge.launch(request)) {
                val unavailableLabel =
                    if (request.target == MaterialHubLaunchRequest.Target.AI_REPLY) {
                        context.getString(R.string.status_area_ai_reply)
                    } else {
                        context.getString(R.string.status_area_material_hub)
                    }
                context.toast(
                    context.getString(
                        R.string.status_area_feature_unavailable,
                        unavailableLabel
                    )
                )
                false
            } else {
                true
            }
        }
        ToolbarActionHostBridge.bindLaunchClipboard {
            windowManager.attachWindow(ClipboardWindow())
            true
        }
        ToolbarActionHostBridge.bindLaunchCustomizeToolbar {
            service.requestHideSelf(0)
            val launchIntent = Intent(ToolbarActionHostBridge.ACTION_OPEN_CUSTOMIZE_TOOLBAR).apply {
                `package` = context.packageName
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            if (launchIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(launchIntent)
                true
            } else {
                AppUtil.launchMain(context)
                true
            }
        }
        ToolbarActionHostBridge.bindLaunchSkin {
            service.requestHideSelf(0)
            if (!StatusAreaHostBridge.launchSkinChannel()) {
                context.toast(
                    context.getString(
                        R.string.status_area_feature_unavailable,
                        context.getString(R.string.status_area_skin)
                    )
                )
                false
            } else {
                true
            }
        }
        ToolbarActionHostBridge.bindLaunchTraditionalInput {
            windowManager.attachWindow(StatusAreaWindow())
            true
        }
        ToolbarActionHostBridge.bindLaunchKeyboardAdjust {
            windowManager.attachWindow(KeyboardWindow)
            inputView.keyboardView.post {
                inputView.beginKeyboardAdjustment()
            }
            true
        }
    }
}
