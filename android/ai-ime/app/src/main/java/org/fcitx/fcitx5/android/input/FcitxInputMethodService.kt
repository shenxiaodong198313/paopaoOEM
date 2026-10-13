/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.LruCache
import android.util.Size
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.view.WindowCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.core.ScancodeMapping
import org.fcitx.fcitx5.android.core.SubtypeManager
import org.fcitx.fcitx5.android.core.TextFormatFlag
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.cursor.CursorRange
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.keyboard.T9Keyboard
import org.fcitx.fcitx5.android.input.keyboard.TextKeyboard
import org.fcitx.fcitx5.android.input.favorite.FavoriteComposerSession
import org.fcitx.fcitx5.android.input.status.KeyboardSelectionPreset
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry
import org.fcitx.fcitx5.android.input.cursor.CursorTracker
import org.fcitx.fcitx5.android.input.t9.T9CandidateBudget
import org.fcitx.fcitx5.android.input.t9.T9CompositionTracker
import org.fcitx.fcitx5.android.input.t9.T9PresentationState
import org.fcitx.fcitx5.android.input.t9.T9PinyinUtils
import org.fcitx.fcitx5.android.input.t9.T9ResolvedSegment
import org.fcitx.fcitx5.android.input.t9.T9SidebarState
import org.fcitx.fcitx5.android.utils.InputMethodUtil
import org.fcitx.fcitx5.android.utils.alpha
import org.fcitx.fcitx5.android.utils.forceShowSelf
import org.fcitx.fcitx5.android.utils.inputMethodManager
import org.fcitx.fcitx5.android.utils.isTypeNull
import org.fcitx.fcitx5.android.utils.monitorCursorAnchor
import org.fcitx.fcitx5.android.utils.styledFloat
import org.fcitx.fcitx5.android.utils.withBatchEdit
import splitties.bitflags.hasFlag
import splitties.dimensions.dp
import splitties.resources.styledColor
import timber.log.Timber
import java.net.URL
import kotlin.math.max

open class FcitxInputMethodService : LifecycleInputMethodService() {
    interface AttachedDialogDismissHandler {
        fun onAttachedDialogDismissed()
    }

    private lateinit var fcitx: FcitxConnection

    private var jobs = Channel<Job>(capacity = Channel.UNLIMITED)

    private val cachedKeyEvents = LruCache<Int, KeyEvent>(78)
    private var cachedKeyEventIndex = 0

    /**
     * Saves MetaState produced by hardware keyboard with "sticky" modifier keys, to clear them in order.
     * See also [InputConnection#clearMetaKeyStates(int)](https://developer.android.com/reference/android/view/inputmethod/InputConnection#clearMetaKeyStates(int))
     */
    private var lastMetaState: Int = 0

    private lateinit var pkgNameCache: PackageNameCache

    private lateinit var decorView: View
    private lateinit var contentView: FrameLayout
    private var inputView: InputView? = null
    private var candidatesView: CandidatesView? = null

    // #region debug-point A:ime-insets-reporter
    private fun reportWechatDrawerPanelDebug(
        hypothesisId: String,
        location: String,
        msg: String,
        data: String,
    ) = Unit
    // #endregion

    // #region debug-point A:keyboard-hidden-zhongzhou-reporter
    internal fun reportKeyboardHiddenZhongzhouDebug(
        hypothesisId: String,
        location: String,
        msg: String,
        data: Map<String, Any?> = emptyMap(),
    ) = Unit
    // #endregion

    private val navbarMgr = NavigationBarManager()
    private val inputDeviceMgr = InputDeviceManager { isVirtualKeyboard ->
        postFcitxJob {
            setCandidatePagingMode(if (isVirtualKeyboard) 0 else 1)
        }
        currentInputConnection?.monitorCursorAnchor(!isVirtualKeyboard)
        if (isVirtualKeyboard) {
            hideStatusIcon()
        } else {
            showStatusIcon(StatusIconMapping.fromEntry(fcitx.runImmediately { inputMethodEntryCached }))
        }
        window.window?.let {
            navbarMgr.evaluate(it, isVirtualKeyboard)
        }
    }

    private var capabilityFlags = CapabilityFlags.DefaultFlags

    private val selection = CursorTracker()

    val currentInputSelection: CursorRange
        get() = selection.latest

    private val composing = CursorRange()
    private var composingText = FormattedText.Empty

    private fun resetComposingState() {
        composing.clear()
        composingText = FormattedText.Empty
    }

    private var cursorUpdateIndex: Int = 0

    private var highlightColor: Int = 0x66008577 // material_deep_teal_500 with alpha 0.4

    private val prefs = AppPrefs.getInstance()
    private val inlineSuggestions by prefs.keyboard.inlineSuggestions
    private val ignoreSystemCursor by prefs.advanced.ignoreSystemCursor
    private val useT9KeyboardLayout by prefs.keyboard.useT9KeyboardLayout
    private val useLocalT9CandidatePipeline = false
    private val lastFullKeyboardChineseImePref = prefs.internal.lastFullKeyboardChineseIme

    private enum class T9InputMode {
        CHINESE,
        ENGLISH
    }

    private var currentT9Mode = T9InputMode.CHINESE
    private val t9ChineseIme = KeyboardSelectionPreset.T9
    private val t9EnglishIme = KeyboardSelectionPreset.ENGLISH
    private val t9ChineseSchemaId = "t9"

    private val t9CompositionTracker = T9CompositionTracker()
    private var t9ResolvedSegments: List<T9ResolvedSegment> = emptyList()
    private var t9SidebarPageIndex = 0
    private var t9SidebarPagingKey = ""
    private var t9SidebarSeparatorPending = false
    private val fullKeyboardChineseImeCandidates = listOf(
        KeyboardSelectionPreset.PINYIN,
        KeyboardSelectionPreset.SHUANGPIN,
        KeyboardSelectionPreset.WUBI,
        KeyboardSelectionPreset.WUBI_PINYIN
    )

    var onT9ModeChanged: ((String) -> Unit)? = null
    var onT9SidebarStateChanged: ((T9SidebarState) -> Unit)? = null
    var onKeyboardLayoutSwitchRequested: ((String) -> Unit)? = null
    var onImeStateRefreshRequested: ((InputMethodEntry) -> Unit)? = null
    private var lastStableImeEntry: InputMethodEntry? = null
    private var lastKeyboardSelectionTarget = StatusAreaEntry.KeyboardSelection.Target.Pinyin26

    private val recreateInputViewPrefs: Array<ManagedPreference<*>> = arrayOf(
        prefs.keyboard.expandKeypressArea,
        prefs.advanced.disableAnimation,
        prefs.advanced.ignoreSystemWindowInsets,
    )

    private fun replaceInputView(theme: Theme): InputView {
        val newInputView = InputView(this, fcitx, theme)
        // Update the current InputView reference before framework detaches the old view.
        // Otherwise the old view still sees itself as current and clears callbacks that the
        // new view has just registered during theme recreation.
        inputView = newInputView
        setInputView(newInputView)
        inputDeviceMgr.setInputView(newInputView)
        return newInputView
    }

    private fun replaceCandidateView(theme: Theme): CandidatesView {
        val newCandidatesView = CandidatesView(this, fcitx, theme)
        // replace CandidatesView manually
        contentView.removeView(candidatesView)
        // put CandidatesView directly under content view
        contentView.addView(newCandidatesView)
        inputDeviceMgr.setCandidatesView(newCandidatesView)
        candidatesView = newCandidatesView
        newCandidatesView.refreshT9State()
        return newCandidatesView
    }

    private fun replaceInputViews(theme: Theme) {
        navbarMgr.evaluate(window.window!!, inputDeviceMgr.isVirtualKeyboard)
        replaceInputView(theme)
        replaceCandidateView(theme)
    }

    @Keep
    private val recreateInputViewListener = ManagedPreference.OnChangeListener<Any> { _, _ ->
        replaceInputView(ThemeManager.activeTheme)
    }

    @Keep
    private val recreateCandidatesViewListener = ManagedPreferenceProvider.OnChangeListener {
        replaceCandidateView(ThemeManager.activeTheme)
    }

    @Keep
    private val onThemeChangeListener = ThemeManager.OnThemeChangeListener {
        replaceInputViews(it)
        restoreKeyboardWindowForCurrentSelection(
            currentImeOverride = fcitx.runImmediately { inputMethodEntryCached },
            ensureImeConsistency = true
        )
        onInputViewsRecreated(it)
    }

    protected open fun onInputViewsRecreated(theme: Theme) = Unit

    internal fun isCurrentInputView(view: InputView): Boolean = inputView === view

    /**
     * Post a fcitx operation to [jobs] to be executed
     *
     * Unlike `fcitx.runOnReady` or `fcitx.launchOnReady` where
     * subsequent operations can start if the prior operation is not finished (suspended),
     * [postFcitxJob] ensures that operations are executed sequentially.
     */
    fun postFcitxJob(block: suspend FcitxAPI.() -> Unit): Job {
        val job = fcitx.lifecycleScope.launch(start = CoroutineStart.LAZY) {
            fcitx.runOnReady(block)
        }
        jobs.trySend(job)
        return job
    }

    open fun getCurrentT9ModeLabel(): String = when (currentT9Mode) {
        T9InputMode.CHINESE -> "中"
        T9InputMode.ENGLISH -> "En"
    }

    open fun launchLocalVoiceInput(): Boolean = false

    protected fun showLocalVoiceSurface(view: View?) { inputView?.showVoiceSurface(view) }

    open fun switchToPreferredVoiceInput(): Boolean {
        val preferredVoiceInput = AppPrefs.getInstance().keyboard.preferredVoiceInput.getValue()
        val subtype = InputMethodUtil.findVoiceSubtype(preferredVoiceInput) ?: return false
        InputMethodUtil.switchInputMethod(this, subtype.first, subtype.second)
        return true
    }

    open fun isFavoritePanelEnabled(): Boolean = true

    open fun isAttachedFavoritePanelShowing(): Boolean = false

    open fun isVirtualKeyboard(): Boolean = inputDeviceMgr.isVirtualKeyboard

    data class RuntimeInputBlockState(
        val label: String,
        val title: String,
        val message: String,
    )

    open fun getRuntimeInputBlockState(): RuntimeInputBlockState? = null

    open fun isT9ChineseModeActive(): Boolean =
        useT9KeyboardLayout &&
            useLocalT9CandidatePipeline &&
            currentT9Mode == T9InputMode.CHINESE

    open fun isT9KeyboardChineseMode(): Boolean =
        useT9KeyboardLayout && currentT9Mode == T9InputMode.CHINESE

    open fun isT9EnglishMode(): Boolean =
        useT9KeyboardLayout && currentT9Mode == T9InputMode.ENGLISH

    private fun isT9SidebarChineseMode(): Boolean =
        useT9KeyboardLayout && currentT9Mode == T9InputMode.CHINESE

    private fun InputMethodEntry.isT9EnglishEntry(): Boolean =
        uniqueName == t9EnglishIme || languageCode.startsWith("en", ignoreCase = true)

    private fun InputMethodEntry.isFullKeyboardEnglishEntry(): Boolean =
        uniqueName == KeyboardSelectionPreset.ENGLISH || languageCode.startsWith("en", ignoreCase = true)

    private fun stableImeForInteraction(entry: InputMethodEntry): InputMethodEntry {
        val hiddenRimeUnderFullKeyboard = !useT9KeyboardLayout && entry.uniqueName == t9ChineseIme
        if (entry.uniqueName.isNotEmpty() && !hiddenRimeUnderFullKeyboard) {
            lastStableImeEntry = entry
            return entry
        }
        return lastStableImeEntry ?: InputMethodEntry(
            KeyboardSelectionPreset.PINYIN,
            "拼音",
            "fcitx-pinyin",
            "拼音",
            "拼",
            "zh_CN",
            "pinyin",
            true
        )
    }

    private fun rememberFullKeyboardChineseIme(uniqueName: String) {
        if (uniqueName in fullKeyboardChineseImeCandidates) {
            lastFullKeyboardChineseImePref.setValue(uniqueName)
        }
    }

    private fun sanitizeRememberedFullKeyboardChineseIme(): String {
        val remembered = lastFullKeyboardChineseImePref.getValue()
        if (remembered in fullKeyboardChineseImeCandidates) {
            return remembered
        }
        lastFullKeyboardChineseImePref.setValue(KeyboardSelectionPreset.PINYIN)
        return KeyboardSelectionPreset.PINYIN
    }

    private suspend fun FcitxAPI.resolveFullKeyboardChineseIme(): String {
        val availableImeNames = availableIme().map { it.uniqueName }.toSet()
        val remembered = sanitizeRememberedFullKeyboardChineseIme()
        if (remembered in availableImeNames) {
            return remembered
        }
        return fullKeyboardChineseImeCandidates.firstOrNull { it in availableImeNames }
            ?: KeyboardSelectionPreset.PINYIN
    }

    private fun InputMethodEntry.isRimeT9Entry(): Boolean {
        if (uniqueName != t9ChineseIme) return false
        return subMode.name.equals(t9ChineseSchemaId, ignoreCase = true) ||
            subMode.label.contains("九键") ||
            subMode.label.equals(t9ChineseSchemaId, ignoreCase = true)
    }

    private fun Action.matchesT9SchemaAction(): Boolean {
        if (isSeparator) return false
        val fields = listOf(name, shortText, longText)
        return fields.any {
            it.equals(t9ChineseSchemaId, ignoreCase = true) ||
                it.contains("中文九键") ||
                it.contains("九键")
        }
    }

    private fun flattenActions(actions: Array<Action>): Sequence<Action> =
        actions.asSequence().flatMap { action ->
            sequenceOf(action) + (action.menu?.let(::flattenActions) ?: emptySequence())
        }

    private suspend fun FcitxAPI.ensureT9ChineseImeAndSchema() {
        val before = currentIme()
        if (before.isRimeT9Entry()) return
        if (before.uniqueName != t9ChineseIme) {
            activateIme(t9ChineseIme)
        }
        val target = flattenActions(statusArea()).firstOrNull { it.matchesT9SchemaAction() }
        if (target != null) {
            activateAction(target.id)
        }
    }

    private fun ensureT9ChineseSession() {
        if (!useT9KeyboardLayout) return
        currentT9Mode = T9InputMode.CHINESE
        onT9ModeChanged?.invoke(getCurrentT9ModeLabel())
        postFcitxJob {
            ensureT9ChineseImeAndSchema()
        }
    }

    open fun getT9PresentationState(): T9PresentationState? {
        if (!isT9ChineseModeActive()) return null
        val unresolvedDigits = t9CompositionTracker.getFullComposition()
        if (unresolvedDigits.isEmpty()) return null
        val topReading = buildT9TopReading(unresolvedDigits)
        return T9PresentationState(
            topReading = topReading,
            pinyinOptions = buildT9PinyinOptions(t9CompositionTracker.getCurrentSegment())
        )
    }

    open fun getT9PagedCandidates(): FcitxEvent.PagedCandidateEvent.Data {
        val state = getT9PresentationState() ?: return FcitxEvent.PagedCandidateEvent.Data.Empty
        return FcitxEvent.PagedCandidateEvent.Data(
            candidates = state.pinyinOptions.mapIndexed { index, pinyin ->
                CandidateWord(
                    label = "${index + 1}.",
                    text = pinyin,
                    comment = T9PinyinUtils.pinyinToT9Keys(pinyin)
                )
            }.toTypedArray(),
            cursorIndex = -1,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )
    }

    open fun handleT9FcitxKeyAction(action: String): Boolean {
        if (!isT9SidebarChineseMode() || action.length != 1) return false
        return when (val ch = action[0]) {
            in '2'..'9' -> {
                t9CompositionTracker.appendDigit(ch)
                val shouldInjectSeparator = t9SidebarSeparatorPending && t9ResolvedSegments.isNotEmpty()
                t9SidebarSeparatorPending = false
                refreshT9CandidatesView()
                if (shouldInjectSeparator) {
                    postFcitxJob {
                        sendKey('\'')
                        sendKey(ch)
                    }
                    true
                } else {
                    false
                }
            }
            '\'' -> {
                t9CompositionTracker.appendApostrophe()
                t9SidebarSeparatorPending = false
                refreshT9CandidatesView()
                false
            }
            else -> {
                clearT9Composition(resetEngine = t9ResolvedSegments.isNotEmpty())
                false
            }
        }
    }

    open fun handleT9SymAction(sym: KeySym): Boolean {
        if (!isT9SidebarChineseMode()) return false
        if (sym.sym != FcitxKeyMapping.FcitxKey_BackSpace) {
            return false
        }
        if (t9CompositionTracker.backspace()) {
            if (t9CompositionTracker.isEmpty() && t9ResolvedSegments.isNotEmpty()) {
                t9SidebarSeparatorPending = true
            }
            refreshT9CandidatesView()
            return false
        }
        val lastResolved = t9ResolvedSegments.lastOrNull() ?: return false
        t9ResolvedSegments = t9ResolvedSegments.dropLast(1)
        val restoredDigits = buildString {
            append(t9CompositionTracker.getFullComposition())
            append(lastResolved.sourceDigits)
        }
        t9CompositionTracker.replace(restoredDigits)
        t9SidebarSeparatorPending = false
        rebuildT9SidebarEngine()
        refreshT9CandidatesView()
        return true
    }

    open fun onT9CandidatePressed(index: Int): Boolean {
        val state = getT9PresentationState() ?: return false
        val pinyin = state.pinyinOptions.getOrNull(index) ?: return false
        val currentDigits = t9CompositionTracker.getCurrentSegment()
        val consumedLength = T9PinyinUtils.matchedPrefixLength(currentDigits, pinyin)
        if (consumedLength <= 0) return false
        val consumedDigits = currentDigits.take(consumedLength)
        val remainingDigits = currentDigits.drop(consumedLength)
        t9ResolvedSegments = t9ResolvedSegments + T9ResolvedSegment(
            pinyin = pinyin,
            sourceDigits = consumedDigits,
            engineBacked = true
        )
        if (remainingDigits.isEmpty()) {
            t9CompositionTracker.removeCurrentSegment()
            t9SidebarSeparatorPending = true
        } else {
            t9CompositionTracker.replaceCurrentSegment(remainingDigits)
            t9SidebarSeparatorPending = false
        }
        rebuildT9SidebarEngine()
        refreshT9CandidatesView()
        return true
    }

    open fun getT9SidebarState(): T9SidebarState {
        if (!isT9SidebarChineseMode()) return T9SidebarState.Empty
        val digits = t9CompositionTracker.getCurrentSegment()
        val options = buildT9PinyinOptions(digits)
        if (digits.isEmpty() || options.isEmpty()) {
            t9SidebarPageIndex = 0
            t9SidebarPagingKey = ""
            return T9SidebarState.Empty
        }
        val pagingKey = "${t9ResolvedSegments.joinToString("|") { it.pinyin }}#$digits"
        if (pagingKey != t9SidebarPagingKey) {
            t9SidebarPagingKey = pagingKey
            t9SidebarPageIndex = 0
        }
        val totalPages = (options.size + 3) / 4
        val pageIndex = t9SidebarPageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        t9SidebarPageIndex = pageIndex
        val start = pageIndex * 4
        val end = minOf(start + 4, options.size)
        return T9SidebarState(
            items = options.subList(start, end),
            pageIndex = pageIndex,
            totalPages = totalPages
        )
    }

    open fun advanceT9SidebarPage() {
        val state = getT9SidebarState()
        if (!state.showingCandidates || state.totalPages <= 1) return
        t9SidebarPageIndex = (state.pageIndex + 1) % state.totalPages
        notifyT9SidebarStateChanged()
    }

    open fun onT9SidebarCandidatePressed(index: Int): Boolean {
        val state = getT9SidebarState()
        val pinyin = state.items.getOrNull(index) ?: return false
        val currentDigits = t9CompositionTracker.getCurrentSegment()
        val consumedLength = T9PinyinUtils.matchedPrefixLength(currentDigits, pinyin)
        if (consumedLength <= 0) return false
        val consumedDigits = currentDigits.take(consumedLength)
        val remainingDigits = currentDigits.drop(consumedLength)
        t9ResolvedSegments = t9ResolvedSegments + T9ResolvedSegment(
            pinyin = pinyin,
            sourceDigits = consumedDigits,
            engineBacked = true
        )
        if (remainingDigits.isEmpty()) {
            t9CompositionTracker.removeCurrentSegment()
            t9SidebarSeparatorPending = true
        } else {
            t9CompositionTracker.replaceCurrentSegment(remainingDigits)
            t9SidebarSeparatorPending = false
        }
        rebuildT9SidebarEngine()
        refreshT9CandidatesView()
        return true
    }

    private fun syncT9ModeWithIme(entry: InputMethodEntry) {
        if (!useT9KeyboardLayout) return
        val nextMode = if (entry.isT9EnglishEntry()) {
            T9InputMode.ENGLISH
        } else {
            T9InputMode.CHINESE
        }
        if (currentT9Mode != nextMode) {
            currentT9Mode = nextMode
            onT9ModeChanged?.invoke(getCurrentT9ModeLabel())
        }
    }

    private fun dispatchImeStateRefresh(entry: InputMethodEntry) {
        if (entry.uniqueName.isNotEmpty()) {
            lastStableImeEntry = entry
        }
        lifecycleScope.launch {
            onImeStateRefreshRequested?.invoke(entry)
        }
    }

    private fun keyboardLayoutNameForSelectionTarget(
        target: StatusAreaEntry.KeyboardSelection.Target
    ): String = if (target == StatusAreaEntry.KeyboardSelection.Target.Pinyin9) {
        T9Keyboard.Name
    } else {
        TextKeyboard.Name
    }

    private fun keyboardSelectionTargetForIme(
        entry: InputMethodEntry,
        useT9Layout: Boolean = prefs.keyboard.useT9KeyboardLayout.getValue()
    ): StatusAreaEntry.KeyboardSelection.Target {
        if (useT9Layout) {
            return if (entry.isT9EnglishEntry()) {
                StatusAreaEntry.KeyboardSelection.Target.English26
            } else {
                StatusAreaEntry.KeyboardSelection.Target.Pinyin9
            }
        }
        return when {
            entry.uniqueName == KeyboardSelectionPreset.ENGLISH ||
                entry.languageCode.startsWith("en", ignoreCase = true) ->
                StatusAreaEntry.KeyboardSelection.Target.English26
            entry.uniqueName == KeyboardSelectionPreset.SHUANGPIN ->
                StatusAreaEntry.KeyboardSelection.Target.Shuangpin
            entry.uniqueName == KeyboardSelectionPreset.WUBI ->
                StatusAreaEntry.KeyboardSelection.Target.Wubi
            entry.uniqueName == KeyboardSelectionPreset.WUBI_PINYIN ->
                StatusAreaEntry.KeyboardSelection.Target.WubiPinyin
            entry.uniqueName == KeyboardSelectionPreset.PINYIN ->
                StatusAreaEntry.KeyboardSelection.Target.Pinyin26
            entry.uniqueName == KeyboardSelectionPreset.T9 ->
                lastKeyboardSelectionTarget
            else -> lastKeyboardSelectionTarget
        }
    }

    private fun updateKeyboardSelectionTarget(target: StatusAreaEntry.KeyboardSelection.Target) {
        lastKeyboardSelectionTarget = target
    }

    fun currentKeyboardSelectionTarget(
        currentImeOverride: InputMethodEntry? = null,
        useT9LayoutOverride: Boolean? = null
    ): StatusAreaEntry.KeyboardSelection.Target {
        val useT9Layout = useT9LayoutOverride ?: prefs.keyboard.useT9KeyboardLayout.getValue()
        val currentIme = currentImeOverride ?: fcitx.runImmediately { inputMethodEntryCached }
        val resolvedTarget = keyboardSelectionTargetForIme(currentIme, useT9Layout)
        lastKeyboardSelectionTarget = resolvedTarget
        return resolvedTarget
    }

    fun restoreKeyboardWindowForCurrentSelection(
        currentImeOverride: InputMethodEntry? = null,
        ensureImeConsistency: Boolean = false
    ) {
        val currentIme = currentImeOverride ?: fcitx.runImmediately { inputMethodEntryCached }
        val target = currentKeyboardSelectionTarget(currentImeOverride = currentIme)
        val layoutName = keyboardLayoutNameForSelectionTarget(target)
        if (target == StatusAreaEntry.KeyboardSelection.Target.Pinyin9) {
            currentT9Mode = T9InputMode.CHINESE
            onT9ModeChanged?.invoke(getCurrentT9ModeLabel())
        }
        onKeyboardLayoutSwitchRequested?.invoke(layoutName)
        if (ensureImeConsistency && target == StatusAreaEntry.KeyboardSelection.Target.Pinyin9) {
            postFcitxJob {
                ensureT9ChineseImeAndSchema()
                onKeyboardLayoutSwitchRequested?.invoke(T9Keyboard.Name)
                dispatchImeStateRefresh(stableImeForInteraction(currentIme()))
            }
        } else {
            dispatchImeStateRefresh(stableImeForInteraction(currentIme))
        }
        refreshT9CandidatesView()
    }

    open fun switchToNextT9Mode() {
        if (!useT9KeyboardLayout) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            lifecycleScope.launch { switchToNextT9Mode() }
            return
        }
        clearT9Composition(resetEngine = t9ResolvedSegments.isNotEmpty())
        currentT9Mode = when (currentT9Mode) {
            T9InputMode.CHINESE -> T9InputMode.ENGLISH
            T9InputMode.ENGLISH -> T9InputMode.CHINESE
        }
        updateKeyboardSelectionTarget(
            if (currentT9Mode == T9InputMode.ENGLISH) {
                StatusAreaEntry.KeyboardSelection.Target.English26
            } else {
                StatusAreaEntry.KeyboardSelection.Target.Pinyin9
            }
        )
        onT9ModeChanged?.invoke(getCurrentT9ModeLabel())
        postFcitxJob {
            if (currentT9Mode == T9InputMode.CHINESE) {
                ensureT9ChineseImeAndSchema()
            } else {
                activateIme(t9EnglishIme)
            }
            onKeyboardLayoutSwitchRequested?.invoke(
                if (currentT9Mode == T9InputMode.ENGLISH) {
                    TextKeyboard.Name
                } else {
                    T9Keyboard.Name
                }
            )
            dispatchImeStateRefresh(stableImeForInteraction(currentIme()))
        }
        refreshT9CandidatesView()
    }

    open fun selectT9Keyboard() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            lifecycleScope.launch { selectT9Keyboard() }
            return
        }
        val beforeIme = fcitx.runImmediately { inputMethodEntryCached }
        // #region debug-point B:select-t9-keyboard
        reportKeyboardHiddenZhongzhouDebug(
            "B",
            "FcitxInputMethodService.selectT9Keyboard",
            "selectT9Keyboard requested",
            mapOf(
                "beforeIme" to beforeIme.uniqueName,
                "beforeDisplay" to beforeIme.displayName,
                "beforeSubModeName" to beforeIme.subMode.name,
                "beforeSubModeLabel" to beforeIme.subMode.label,
                "rememberedChineseIme" to lastFullKeyboardChineseImePref.getValue(),
                "useT9KeyboardLayout" to prefs.keyboard.useT9KeyboardLayout.getValue()
            )
        )
        // #endregion
        prefs.keyboard.useT9KeyboardLayout.setValue(true)
        updateKeyboardSelectionTarget(StatusAreaEntry.KeyboardSelection.Target.Pinyin9)
        clearT9Composition(resetEngine = t9ResolvedSegments.isNotEmpty())
        currentT9Mode = T9InputMode.CHINESE
        onT9ModeChanged?.invoke(getCurrentT9ModeLabel())
        onKeyboardLayoutSwitchRequested?.invoke(T9Keyboard.Name)
        postFcitxJob {
            ensureT9ChineseImeAndSchema()
            onKeyboardLayoutSwitchRequested?.invoke(T9Keyboard.Name)
            dispatchImeStateRefresh(stableImeForInteraction(currentIme()))
        }
        refreshT9CandidatesView()
    }

    open fun selectFullKeyboardIme(uniqueName: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            lifecycleScope.launch { selectFullKeyboardIme(uniqueName) }
            return
        }
        val beforeIme = fcitx.runImmediately { inputMethodEntryCached }
        // #region debug-point C:select-full-keyboard-ime
        reportKeyboardHiddenZhongzhouDebug(
            "C",
            "FcitxInputMethodService.selectFullKeyboardIme",
            "selectFullKeyboardIme requested",
            mapOf(
                "targetIme" to uniqueName,
                "beforeIme" to beforeIme.uniqueName,
                "beforeDisplay" to beforeIme.displayName,
                "beforeSubModeName" to beforeIme.subMode.name,
                "beforeSubModeLabel" to beforeIme.subMode.label,
                "rememberedChineseImeBefore" to lastFullKeyboardChineseImePref.getValue()
            )
        )
        // #endregion
        if (uniqueName != KeyboardSelectionPreset.ENGLISH) {
            rememberFullKeyboardChineseIme(uniqueName)
        }
        prefs.keyboard.useT9KeyboardLayout.setValue(false)
        updateKeyboardSelectionTarget(
            when (uniqueName) {
                KeyboardSelectionPreset.ENGLISH -> StatusAreaEntry.KeyboardSelection.Target.English26
                KeyboardSelectionPreset.SHUANGPIN -> StatusAreaEntry.KeyboardSelection.Target.Shuangpin
                KeyboardSelectionPreset.WUBI -> StatusAreaEntry.KeyboardSelection.Target.Wubi
                KeyboardSelectionPreset.WUBI_PINYIN -> StatusAreaEntry.KeyboardSelection.Target.WubiPinyin
                else -> StatusAreaEntry.KeyboardSelection.Target.Pinyin26
            }
        )
        clearT9Composition(resetEngine = t9ResolvedSegments.isNotEmpty())
        currentT9Mode = T9InputMode.CHINESE
        onT9ModeChanged?.invoke(getCurrentT9ModeLabel())
        onKeyboardLayoutSwitchRequested?.invoke(TextKeyboard.Name)
        postFcitxJob {
            activateIme(uniqueName)
            onKeyboardLayoutSwitchRequested?.invoke(TextKeyboard.Name)
            dispatchImeStateRefresh(stableImeForInteraction(currentIme()))
        }
        refreshT9CandidatesView()
    }

    open fun toggleFullKeyboardChineseEnglish() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            lifecycleScope.launch { toggleFullKeyboardChineseEnglish() }
            return
        }
        prefs.keyboard.useT9KeyboardLayout.setValue(false)
        clearT9Composition(resetEngine = t9ResolvedSegments.isNotEmpty())
        currentT9Mode = T9InputMode.CHINESE
        onT9ModeChanged?.invoke(getCurrentT9ModeLabel())
        onKeyboardLayoutSwitchRequested?.invoke(TextKeyboard.Name)
        val currentIme = stableImeForInteraction(fcitx.runImmediately { inputMethodEntryCached })
        if (!currentIme.isFullKeyboardEnglishEntry()) {
            rememberFullKeyboardChineseIme(currentIme.uniqueName)
        }
        postFcitxJob {
            val targetIme = if (currentIme.isFullKeyboardEnglishEntry()) {
                resolveFullKeyboardChineseIme()
            } else {
                KeyboardSelectionPreset.ENGLISH
            }
            updateKeyboardSelectionTarget(
                when (targetIme) {
                    KeyboardSelectionPreset.ENGLISH -> StatusAreaEntry.KeyboardSelection.Target.English26
                    KeyboardSelectionPreset.SHUANGPIN -> StatusAreaEntry.KeyboardSelection.Target.Shuangpin
                    KeyboardSelectionPreset.WUBI -> StatusAreaEntry.KeyboardSelection.Target.Wubi
                    KeyboardSelectionPreset.WUBI_PINYIN -> StatusAreaEntry.KeyboardSelection.Target.WubiPinyin
                    else -> StatusAreaEntry.KeyboardSelection.Target.Pinyin26
                }
            )
            activateIme(targetIme)
            onKeyboardLayoutSwitchRequested?.invoke(TextKeyboard.Name)
            dispatchImeStateRefresh(stableImeForInteraction(currentIme()))
        }
        refreshT9CandidatesView()
    }

    open fun ensureFavoriteComposerChineseInputMode() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            lifecycleScope.launch { ensureFavoriteComposerChineseInputMode() }
            return
        }
        val currentIme = stableImeForInteraction(fcitx.runImmediately { inputMethodEntryCached })
        if (useT9KeyboardLayout) {
            when {
                currentIme.isT9EnglishEntry() || currentT9Mode == T9InputMode.ENGLISH -> {
                    switchToNextT9Mode()
                }
                currentIme.uniqueName != t9ChineseIme -> {
                    selectT9Keyboard()
                }
            }
            return
        }
        if (!currentIme.isFullKeyboardEnglishEntry()) return
        lifecycleScope.launch {
            val targetIme = fcitx.runOnReady { resolveFullKeyboardChineseIme() }
            selectFullKeyboardIme(targetIme)
        }
    }

    private fun buildT9TopReading(unresolvedDigits: String): FormattedText {
        val strings = mutableListOf<String>()
        val flags = mutableListOf<Int>()
        val resolvedReading = t9ResolvedSegments.joinToString(" ") { it.pinyin }
        if (resolvedReading.isNotEmpty()) {
            strings += resolvedReading
            flags += TextFormatFlag.NoFlag.flag
            strings += "  "
            flags += TextFormatFlag.NoFlag.flag
        }
        strings += unresolvedDigits
        flags += TextFormatFlag.HighLight.flag
        return FormattedText(strings.toTypedArray(), flags.toIntArray(), -1)
    }

    private fun buildT9PinyinOptions(digits: String): List<String> {
        if (digits.isEmpty()) return emptyList()
        val budget = T9CandidateBudget.normalizedBudget(12)
        var usedBudget = 0
        val options = mutableListOf<String>()
        T9PinyinUtils.t9KeyToPinyin(digits).forEach { option ->
            val cost = T9CandidateBudget.candidateCost(option)
            if (options.isNotEmpty() && usedBudget + cost > budget) return@forEach
            options += option
            usedBudget += cost
        }
        return options
    }

    private fun rebuildT9SidebarEngine() {
        val resolvedSegments = t9ResolvedSegments
        val rawComposition = t9CompositionTracker.getFullComposition()
        postFcitxJob {
            reset()
            resolvedSegments.forEachIndexed { index, segment ->
                if (index > 0) {
                    sendKey('\'')
                }
                segment.pinyin.forEach { sendKey(it) }
            }
            if (rawComposition.isNotEmpty()) {
                if (resolvedSegments.isNotEmpty() && !rawComposition.startsWith('\'')) {
                    sendKey('\'')
                }
                rawComposition.forEach { sendKey(it) }
            }
        }
    }

    private fun clearT9Composition(resetEngine: Boolean = false) {
        val shouldResetEngine = resetEngine && (
            t9ResolvedSegments.isNotEmpty() ||
                t9SidebarSeparatorPending
            )
        t9ResolvedSegments = emptyList()
        t9CompositionTracker.clear()
        t9SidebarPageIndex = 0
        t9SidebarPagingKey = ""
        t9SidebarSeparatorPending = false
        if (shouldResetEngine) {
            postFcitxJob { reset() }
        }
        refreshT9CandidatesView()
    }

    private fun refreshT9CandidatesView() {
        candidatesView?.refreshT9State()
        notifyT9SidebarStateChanged()
    }

    private fun notifyT9SidebarStateChanged() {
        onT9SidebarStateChanged?.invoke(getT9SidebarState())
    }

    open fun resetT9InputState() {
        clearT9Composition(resetEngine = t9ResolvedSegments.isNotEmpty())
        postFcitxJob { reset() }
    }

    val hostClipboard by lazy {
        org.fcitx.fcitx5.android.input.clipboard.HostClipboardController(this, ::clipboardForegroundPackage)
    }

    protected open fun clipboardForegroundPackage(): String? = null

    open fun isClipboardQuickReplyEnabled(): Boolean = true

    /** Host-owned clicks must not consume or dismiss the clipboard suggestion. */
    open fun isClipboardQuickReplyHostHandled(): Boolean = false

    override fun onCreate() {
        fcitx = FcitxDaemon.connect(javaClass.name)
        lifecycleScope.launch {
            jobs.consumeEach { it.join() }
        }
        lifecycleScope.launch {
            fcitx.runImmediately { eventFlow }.collect {
                handleFcitxEvent(it)
            }
        }
        pkgNameCache = PackageNameCache(this)
        recreateInputViewPrefs.forEach {
            it.registerOnChangeListener(recreateInputViewListener)
        }
        prefs.candidates.registerOnChangeListener(recreateCandidatesViewListener)
        ThemeManager.addOnChangedListener(onThemeChangeListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            postFcitxJob {
                SubtypeManager.syncWith(enabledIme())
            }
        }
        super.onCreate()
        hostClipboard.start()
        decorView = window.window!!.decorView
        contentView = decorView.findViewById(android.R.id.content)
        syncMainWindowBackground()
        lastKnownConfig = resources.configuration
    }

    private fun handleFcitxEvent(event: FcitxEvent<*>) {
        if (handleFavoriteTextEditorEvent(event)) {
            return
        }
        when (event) {
            is FcitxEvent.CommitStringEvent -> {
                clearT9Composition()
                commitText(event.data.text, event.data.cursor)
            }
            is FcitxEvent.KeyEvent -> event.data.let event@{
                if (it.states.virtual) {
                    // KeyEvent from virtual keyboard
                    when (it.sym.sym) {
                        FcitxKeyMapping.FcitxKey_BackSpace -> handleBackspaceKey()
                        FcitxKeyMapping.FcitxKey_Return -> handleReturnKey()
                        FcitxKeyMapping.FcitxKey_Left -> handleArrowKey(KeyEvent.KEYCODE_DPAD_LEFT)
                        FcitxKeyMapping.FcitxKey_Right -> handleArrowKey(KeyEvent.KEYCODE_DPAD_RIGHT)
                        else -> if (it.unicode > 0) {
                            commitText(Character.toString(it.unicode))
                        } else {
                            Timber.w("Unhandled Virtual KeyEvent: $it")
                        }
                    }
                } else {
                    // KeyEvent from physical keyboard (or input method engine forwardKey)
                    // use cached event if available
                    cachedKeyEvents.remove(it.timestamp)?.let { keyEvent ->
                        /**
                         * intercept the KeyEvent which would cause the default [android.text.method.QwertyKeyListener]
                         * to show a Gingerbread-style CharacterPickerDialog
                         */
                        if (keyEvent.unicodeChar == KeyCharacterMap.PICKER_DIALOG_INPUT.code) {
                            currentInputConnection?.sendKeyEvent(
                                KeyEvent(
                                    keyEvent.downTime, keyEvent.eventTime,
                                    keyEvent.action, keyEvent.keyCode,
                                    keyEvent.repeatCount, keyEvent.metaState, -1,
                                    keyEvent.scanCode, keyEvent.flags, keyEvent.source
                                )
                            )
                            return@event
                        }
                        currentInputConnection?.sendKeyEvent(keyEvent)
                        if (KeyEvent.isModifierKey(keyEvent.keyCode)) {
                            when (keyEvent.action) {
                                KeyEvent.ACTION_DOWN -> {
                                    // save current metaState when modifier key down
                                    lastMetaState = keyEvent.metaState
                                }
                                KeyEvent.ACTION_UP -> {
                                    // only clear metaState that would be missing when this modifier key up
                                    currentInputConnection?.clearMetaKeyStates(lastMetaState xor keyEvent.metaState)
                                    lastMetaState = keyEvent.metaState
                                }
                            }
                        }
                        return@event
                    }
                    // simulate key event
                    val keyCode = it.sym.keyCode
                    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                        // recognized keyCode
                        val eventTime = SystemClock.uptimeMillis()
                        if (it.up) {
                            sendUpKeyEvent(eventTime, keyCode, it.states.metaState)
                        } else {
                            sendDownKeyEvent(eventTime, keyCode, it.states.metaState)
                        }
                    } else {
                        // no matching keyCode, commit character once on key down
                        if (!it.up && it.unicode > 0) {
                            commitText(Character.toString(it.unicode))
                        } else {
                            Timber.w("Unhandled Fcitx KeyEvent: $it")
                        }
                    }
                }
            }
            is FcitxEvent.ClientPreeditEvent -> {
                updateComposingText(event.data)
                refreshT9CandidatesView()
            }
            is FcitxEvent.DeleteSurroundingEvent -> {
                val (before, after) = event.data
                handleDeleteSurrounding(before, after)
            }
            is FcitxEvent.IMChangeEvent -> {
                if (!prefs.keyboard.useT9KeyboardLayout.getValue() &&
                    event.data.uniqueName == t9ChineseIme
                ) {
                    val normalizedIme = sanitizeRememberedFullKeyboardChineseIme()
                    // #region debug-point H:normalize-hidden-rime-ime
                    reportKeyboardHiddenZhongzhouDebug(
                        "H",
                        "FcitxInputMethodService.handleFcitxEvent",
                        "normalized hidden rime ime back to visible full keyboard ime",
                        mapOf(
                            "illegalIme" to event.data.uniqueName,
                            "illegalDisplay" to event.data.displayName,
                            "normalizedIme" to normalizedIme,
                            "rememberedChineseIme" to lastFullKeyboardChineseImePref.getValue(),
                            "useT9KeyboardLayout" to prefs.keyboard.useT9KeyboardLayout.getValue()
                        )
                    )
                    // #endregion
                    postFcitxJob {
                        activateIme(normalizedIme)
                    }
                    return
                }
                val uiIme = stableImeForInteraction(event.data)
                updateKeyboardSelectionTarget(
                    keyboardSelectionTargetForIme(
                        uiIme,
                        prefs.keyboard.useT9KeyboardLayout.getValue()
                    )
                )
                syncT9ModeWithIme(uiIme)
                if (prefs.keyboard.useT9KeyboardLayout.getValue()) {
                    onKeyboardLayoutSwitchRequested?.invoke(
                        if (uiIme.isT9EnglishEntry()) {
                            TextKeyboard.Name
                        } else {
                            T9Keyboard.Name
                        }
                    )
                }
                if (!event.data.languageCode.startsWith("zh")) {
                    clearT9Composition(resetEngine = t9ResolvedSegments.isNotEmpty())
                }
                dispatchImeStateRefresh(uiIme)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val im = event.data.uniqueName
                    val subtype = SubtypeManager.subtypeOf(im) ?: return
                    skipNextSubtypeChange = im
                    // [^1]: notify system that input method subtype has changed
                    switchInputMethod(InputMethodUtil.componentName, subtype)
                }
                if (inputDeviceMgr.evaluateOnInputMethodActivate()) {
                    showStatusIcon(StatusIconMapping.fromEntry(event.data))
                }
            }
            is FcitxEvent.SwitchInputMethodEvent -> {
                val (reason) = event.data
                if (reason != FcitxEvent.SwitchInputMethodEvent.Reason.CapabilityChanged &&
                    reason != FcitxEvent.SwitchInputMethodEvent.Reason.Other
                ) {
                    if (inputDeviceMgr.evaluateOnInputMethodSwitch()) {
                        // show inputView for [CandidatesView] when input method switched by user
                        forceShowSelf()
                    }
                }
            }
            is FcitxEvent.InputPanelEvent -> {
                refreshT9CandidatesView()
            }
            is FcitxEvent.PagedCandidateEvent -> {
                refreshT9CandidatesView()
            }
            else -> {}
        }
    }

    private fun handleFavoriteTextEditorEvent(event: FcitxEvent<*>): Boolean {
        if (!FavoriteComposerSession.isTextEditorActive()) return false
        when (event) {
            is FcitxEvent.CommitStringEvent -> {
                clearT9Composition()
                FavoriteComposerSession.appendTextEditorText(event.data.text)
                return true
            }
            is FcitxEvent.ClientPreeditEvent -> {
                FavoriteComposerSession.updateTextEditorComposing(event.data.toString())
                refreshT9CandidatesView()
                return true
            }
            is FcitxEvent.DeleteSurroundingEvent -> {
                val (before, _) = event.data
                if (before > 0) {
                    FavoriteComposerSession.deleteTextEditorBackward(before)
                }
                return true
            }
            is FcitxEvent.KeyEvent -> event.data.let {
                if (it.up) return true
                if (it.states.virtual) {
                    when (it.sym.sym) {
                        FcitxKeyMapping.FcitxKey_BackSpace -> {
                            FavoriteComposerSession.deleteTextEditorBackward()
                            return true
                        }
                        FcitxKeyMapping.FcitxKey_Return -> {
                            FavoriteComposerSession.appendTextEditorText("\n")
                            clearT9Composition(resetEngine = t9ResolvedSegments.isNotEmpty())
                            return true
                        }
                        FcitxKeyMapping.FcitxKey_Left,
                        FcitxKeyMapping.FcitxKey_Right -> return true
                        else -> if (it.unicode > 0) {
                            FavoriteComposerSession.appendTextEditorText(Character.toString(it.unicode))
                            return true
                        }
                    }
                } else if (it.unicode > 0 && it.sym.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                    FavoriteComposerSession.appendTextEditorText(Character.toString(it.unicode))
                    return true
                }
            }
            else -> Unit
        }
        return false
    }

    private fun handleDeleteSurrounding(before: Int, after: Int) {
        val ic = currentInputConnection ?: return
        if (before > 0) {
            selection.predictOffset(-before)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ic.deleteSurroundingTextInCodePoints(before, after)
        } else {
            ic.deleteSurroundingText(before, after)
        }
    }

    private fun handleBackspaceKey() {
        val lastSelection = selection.latest
        if (lastSelection.isNotEmpty()) {
            selection.predict(lastSelection.start)
        } else if (lastSelection.start > 0) {
            selection.predictOffset(-1)
        }
        // In practice nobody (apart form ourselves) would set `privateImeOptions` to our
        // `DeleteSurroundingFlag`, leading to a behavior of simulating backspace key pressing
        // in almost every EditText.
        if (currentInputEditorInfo.privateImeOptions != DeleteSurroundingFlag ||
            currentInputEditorInfo.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL
        ) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        if (lastSelection.isEmpty()) {
            if (lastSelection.start <= 0) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                currentInputConnection.deleteSurroundingTextInCodePoints(1, 0)
            } else {
                currentInputConnection.deleteSurroundingText(1, 0)
            }
        } else {
            currentInputConnection.commitText("", 0)
        }
    }

    private fun handleReturnKey() {
        currentInputEditorInfo.run {
            if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL ||
                imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ENTER_ACTION)
            ) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                return
            }
            if (actionLabel?.isNotEmpty() == true && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                currentInputConnection.performEditorAction(actionId)
                return
            }
            when (val action = imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_ACTION_NONE -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                else -> currentInputConnection.performEditorAction(action)
            }
        }
    }

    private fun handleArrowKey(keyCode: Int) {
        val type = currentInputEditorInfo.inputType and InputType.TYPE_MASK_CLASS
        val variation = currentInputEditorInfo.inputType and InputType.TYPE_MASK_VARIATION
        if (type == InputType.TYPE_NULL ||
            // confirm URL suggestion in browser location bar, see also https://bugzilla.mozilla.org/show_bug.cgi?id=1999915
            type == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_URI
        ) {
            sendDownUpKeyEvents(keyCode)
            return
        }
        val (start, end) = currentInputSelection
        val offset = if (start == end) 1 else 0
        val target = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> start - offset
            KeyEvent.KEYCODE_DPAD_RIGHT -> end + offset
            else -> return
        }
        currentInputConnection.setSelection(target, target)
    }

    open fun commitText(text: String, cursor: Int = -1) {
        if (text.isNotEmpty()) hostClipboard.dismiss()
        val ic = currentInputConnection ?: return
        // when composing text equals commit content, finish composing text as-is
        if (composing.isNotEmpty() && composingText.toString() == text) {
            val c = if (cursor == -1) text.length else cursor
            val target = composing.start + c
            resetComposingState()
            ic.withBatchEdit {
                if (selection.current.start != target) {
                    selection.predict(target)
                    ic.setSelection(target, target)
                }
                ic.finishComposingText()
            }
            return
        }
        // committed text should replace composing (if any), replace selected range (if any),
        // or simply prepend before cursor
        val start = if (composing.isEmpty()) selection.latest.start else composing.start
        resetComposingState()
        if (cursor == -1) {
            selection.predict(start + text.length)
            ic.commitText(text, 1)
        } else {
            val target = start + cursor
            selection.predict(target)
            ic.withBatchEdit {
                commitText(text, 1)
                setSelection(target, target)
            }
        }
    }

    private fun sendDownKeyEvent(eventTime: Long, keyEventCode: Int, metaState: Int = 0) {
        currentInputConnection?.sendKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_DOWN,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                ScancodeMapping.keyCodeToScancode(keyEventCode),
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }

    private fun sendUpKeyEvent(eventTime: Long, keyEventCode: Int, metaState: Int = 0) {
        currentInputConnection?.sendKeyEvent(
            KeyEvent(
                eventTime,
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                ScancodeMapping.keyCodeToScancode(keyEventCode),
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }

    fun deleteSelection() {
        val lastSelection = selection.latest
        if (lastSelection.isEmpty()) return
        selection.predict(lastSelection.start)
        currentInputConnection?.commitText("", 1)
    }

    fun sendCombinationKeyEvents(
        keyEventCode: Int,
        alt: Boolean = false,
        ctrl: Boolean = false,
        shift: Boolean = false
    ) {
        var metaState = 0
        if (alt) metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (ctrl) metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (shift) metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        val eventTime = SystemClock.uptimeMillis()
        if (alt) sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT)
        if (ctrl) sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT)
        if (shift) sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        sendDownKeyEvent(eventTime, keyEventCode, metaState)
        sendUpKeyEvent(eventTime, keyEventCode, metaState)
        if (shift) sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        if (ctrl) sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT)
        if (alt) sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT)
    }

    fun applySelectionOffset(offsetStart: Int, offsetEnd: Int = 0) {
        val lastSelection = selection.latest
        currentInputConnection?.also {
            val start = max(lastSelection.start + offsetStart, 0)
            val end = max(lastSelection.end + offsetEnd, 0)
            if (start > end) return
            selection.predict(start, end)
            it.setSelection(start, end)
        }
    }

    fun cancelSelection() {
        val lastSelection = selection.latest
        if (lastSelection.isEmpty()) return
        val end = lastSelection.end
        selection.predict(end)
        currentInputConnection?.setSelection(end, end)
    }

    private lateinit var lastKnownConfig: Configuration

    override fun onConfigurationChanged(newConfig: Configuration) {
        postFcitxJob { reset() }
        /**
         * skip keyboard|keyboardHidden changes, because we have [inputDeviceMgr]
         * skip uiMode (system light/dark mode) changes, because we have [onThemeChangeListener]
         * to replace InputView(s) when needed
         * [android.inputmethodservice.InputMethodService.onConfigurationChanged] would call
         * resetStateForNewConfiguration() which calls initViews() causes InputView(s) to be replaced again
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r36/core/java/android/inputmethodservice/InputMethodService.java#1984
         */
        val f = ActivityInfo.CONFIG_KEYBOARD or
                ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
                ActivityInfo.CONFIG_UI_MODE
        val diff = lastKnownConfig.diff(newConfig)
        Timber.d("onConfigurationChanged diff=$diff")
        /**
         * perform `super.onConfigurationChanged` only when `newConfig` diff fall outside "skipped" flags
         * we have to calculate the mask ourselves because nobody knows how `handledConfigChanges` works
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r36/core/java/android/inputmethodservice/InputMethodService.java#1876
         */
        if (diff and f != diff) {
            super.onConfigurationChanged(newConfig)
        }
        lastKnownConfig = newConfig
    }

    override fun onWindowShown() {
        super.onWindowShown()
        try {
            highlightColor = styledColor(android.R.attr.colorAccent).alpha(0.4f)
        } catch (_: Exception) {
            Timber.w("Device does not support android.R.attr.colorAccent which it should have.")
        }
        InputFeedbacks.syncSystemPrefs()
    }

    override fun onCreateInputView(): View? {
        replaceInputViews(ThemeManager.activeTheme)
        // We will call `setInputView` by ourselves. This is fine.
        return null
    }

    override fun setInputView(view: View) {
        super.setInputView(view)
        syncMainWindowLayout()
    }

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        syncMainWindowLayout()
    }

    private fun shouldUseFullscreenInputWindow(): Boolean {
        return !inputDeviceMgr.isVirtualKeyboard
    }

    private fun syncMainWindowLayout() {
        val windowHeight = if (shouldUseFullscreenInputWindow()) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }
        window.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, windowHeight)
        contentView.findViewById<FrameLayout>(android.R.id.inputArea)
            ?.updateLayoutParams<ViewGroup.LayoutParams> {
                height = windowHeight
            }
        inputView?.updateLayoutParams<ViewGroup.LayoutParams> {
            height = windowHeight
        }
        syncMainWindowBackground()
    }

    private fun syncMainWindowBackground() {
        window.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        decorView.setBackgroundColor(Color.TRANSPARENT)
        contentView.setBackgroundColor(Color.TRANSPARENT)
        contentView.findViewById<FrameLayout>(android.R.id.inputArea)
            ?.setBackgroundColor(Color.TRANSPARENT)
    }

    private var inputViewLocation = intArrayOf(0, 0)

    override fun onComputeInsets(outInsets: Insets) {
        if (inputDeviceMgr.isVirtualKeyboard) {
            inputView?.keyboardView?.getLocationInWindow(inputViewLocation)
            outInsets.apply {
                contentTopInsets = inputViewLocation[1]
                visibleTopInsets = inputViewLocation[1]
                touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
            }
        } else {
            val n = decorView.findViewById<View>(android.R.id.navigationBarBackground)?.height ?: 0
            val h = decorView.height - n
            outInsets.apply {
                contentTopInsets = h
                visibleTopInsets = h
                touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
            }
        }
    }

    // always show InputView since we delegate CandidatesView's visibility to it
    @SuppressLint("MissingSuperCall")
    override fun onEvaluateInputViewShown() = true

    fun superEvaluateInputViewShown() = super.onEvaluateInputViewShown()

    override fun onEvaluateFullscreenMode() = false

    private fun forwardKeyEvent(event: KeyEvent): Boolean {
        // reason to use a self increment index rather than timestamp:
        // KeyUp and KeyDown events actually can happen on the same time
        val timestamp = cachedKeyEventIndex++
        cachedKeyEvents.put(timestamp, event)
        val sym = KeySym.fromKeyEvent(event)
        if (sym != null) {
            val states = KeyStates.fromKeyEvent(event)
            val up = event.action == KeyEvent.ACTION_UP
            postFcitxJob {
                sendKey(sym, states, event.scanCode, up, timestamp)
            }
            return true
        }
        Timber.d("Skipped KeyEvent: $event")
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // request to show floating CandidatesView when pressing physical keyboard
        if (inputDeviceMgr.evaluateOnKeyDown(event, this)) {
            postFcitxJob {
                focus(true)
            }
            forceShowSelf()
        }
        return forwardKeyEvent(event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return forwardKeyEvent(event) || super.onKeyUp(keyCode, event)
    }

    // Added in API level 14, deprecated in 29
    // it's needed because editors still use it even on API 36
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onViewClicked(focusChanged: Boolean) {
        super.onViewClicked(focusChanged)
        inputDeviceMgr.evaluateOnViewClicked(this)
    }

    @RequiresApi(34)
    override fun onUpdateEditorToolType(toolType: Int) {
        super.onUpdateEditorToolType(toolType)
        inputDeviceMgr.evaluateOnUpdateEditorToolType(toolType, this)
    }

    private var firstBindInput = true

    override fun onBindInput() {
        val uid = currentInputBinding.uid
        val pkgName = pkgNameCache.forUid(uid)
        Timber.d("onBindInput: uid=$uid pkg=$pkgName")
        postFcitxJob {
            // ensure InputContext has been created before focusing it
            activate(uid, pkgName)
        }
        if (firstBindInput) {
            firstBindInput = false
            // only use input method from subtype for the first `onBindInput`, because
            // 1. fcitx has `ShareInputState` option, thus reading input method from subtype
            //    everytime would ruin `ShareInputState=Program`
            // 2. im from subtype should be read once, when user changes input method from other
            //    app to a subtype of ours via system input method picker (on 34+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val subtype = inputMethodManager.currentInputMethodSubtype ?: return
                val im = SubtypeManager.inputMethodOf(subtype)
                // #region debug-point D:on-bind-input-subtype
                reportKeyboardHiddenZhongzhouDebug(
                    "D",
                    "FcitxInputMethodService.onBindInput",
                    "first bind restored ime from system subtype",
                    mapOf(
                        "subtypeExtraValue" to subtype.extraValue,
                        "subtypeLanguageTag" to subtype.languageTag,
                        "subtypeLocale" to subtype.locale,
                        "resolvedIme" to im,
                        "currentImeCached" to fcitx.runImmediately { inputMethodEntryCached }.uniqueName,
                        "rememberedChineseIme" to lastFullKeyboardChineseImePref.getValue(),
                        "useT9KeyboardLayout" to prefs.keyboard.useT9KeyboardLayout.getValue()
                    )
                )
                // #endregion
                postFcitxJob {
                    activateIme(im)
                }
            }
        }
    }

    /**
     * When input method changes internally (eg. via language switch key or keyboard shortcut),
     * we want to notify system that subtype has changed (see [^1]), then ignore the incoming
     * [onCurrentInputMethodSubtypeChanged] callback.
     * Input method should only be changed when user changes subtype in system input method picker
     * manually.
     */
    private var skipNextSubtypeChange: String? = null

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val im = SubtypeManager.inputMethodOf(newSubtype)
            Timber.d("onCurrentInputMethodSubtypeChanged: im=$im")
            // #region debug-point E:on-current-subtype-changed
            reportKeyboardHiddenZhongzhouDebug(
                "E",
                "FcitxInputMethodService.onCurrentInputMethodSubtypeChanged",
                "system subtype changed and requested ime activation",
                mapOf(
                    "subtypeExtraValue" to newSubtype.extraValue,
                    "subtypeLanguageTag" to newSubtype.languageTag,
                    "subtypeLocale" to newSubtype.locale,
                    "resolvedIme" to im,
                    "skipNextSubtypeChange" to skipNextSubtypeChange,
                    "currentImeCached" to fcitx.runImmediately { inputMethodEntryCached }.uniqueName,
                    "rememberedChineseIme" to lastFullKeyboardChineseImePref.getValue(),
                    "useT9KeyboardLayout" to prefs.keyboard.useT9KeyboardLayout.getValue()
                )
            )
            // #endregion
            // don't change input method if this "subtype change" was our notify to system
            // see [^1]
            if (skipNextSubtypeChange == im) {
                skipNextSubtypeChange = null
                return
            }
            postFcitxJob {
                activateIme(im)
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        hostClipboard.startInput(attribute)
        // update selection as soon as possible
        // sometimes when restarting input, onUpdateSelection happens before onStartInput, and
        // initialSel{Start,End} is outdated. but it's the client app's responsibility to send
        // right cursor position, try to workaround this would simply introduce more bugs.
        selection.resetTo(attribute.initialSelStart, attribute.initialSelEnd)
        resetComposingState()
        clearT9Composition()
        val flags = CapabilityFlags.fromEditorInfo(attribute)
        capabilityFlags = flags
        // EditorInfo may change between onStartInput and onStartInputView
        inputDeviceMgr.notifyOnStartInput(attribute)
        Timber.d("onStartInput: initialSel=${selection.current}, restarting=$restarting")
        val isNullType = attribute.isTypeNull()
        // wait until InputContext created/activated
        postFcitxJob {
            if (restarting) {
                // when input restarts in the same editor, focus out to clear previous state
                focus(false)
                // try focus out before changing CapabilityFlags,
                // to avoid confusing state of different text fields
            }
            // EditorInfo can be different in onStartInput and onStartInputView,
            // especially in browsers
            setCapFlags(flags)
            // for hardware keyboard, focus to allow switching input methods before onStartInputView
            if (!isNullType) {
                focus(true)
            }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        Timber.d("onStartInputView: restarting=$restarting")
        syncMainWindowLayout()
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        if (inputClass != InputType.TYPE_CLASS_NUMBER &&
            inputClass != InputType.TYPE_CLASS_PHONE
        ) {
            if (currentKeyboardSelectionTarget() == StatusAreaEntry.KeyboardSelection.Target.Pinyin9) {
                ensureT9ChineseSession()
            } else {
                restoreKeyboardWindowForCurrentSelection()
            }
        }
        postFcitxJob {
            focus(true)
        }
        inputView?.syncRuntimeBlockState(getRuntimeInputBlockState())
        if (inputDeviceMgr.evaluateOnStartInputView(info, this)) {
            // because onStartInputView will always be called after onStartInput,
            // editorInfo and capFlags should be up-to-date
            inputView?.startInput(info, capabilityFlags, restarting)
        } else {
            if (currentInputConnection?.monitorCursorAnchor() != true) {
                if (!decorLocationUpdated) {
                    updateDecorLocation()
                }
                // anchor CandidatesView to bottom-left corner in case InputConnection does not
                // support monitoring CursorAnchorInfo
                candidatesView?.updateCursorAnchor(contentSize)
            }
            showStatusIcon(StatusIconMapping.fromEntry(fcitx.runImmediately { inputMethodEntryCached }))
        }
    }

    fun clearFavoriteTextEditorInputState() {
        FavoriteComposerSession.updateTextEditorComposing("")
        clearT9Composition(resetEngine = t9ResolvedSegments.isNotEmpty())
        postFcitxJob { reset() }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        // onUpdateSelection can left behind when user types quickly enough, eg. long press backspace
        cursorUpdateIndex += 1
        Timber.d("onUpdateSelection: old=[$oldSelStart,$oldSelEnd] new=[$newSelStart,$newSelEnd]")
        handleCursorUpdate(newSelStart, newSelEnd, cursorUpdateIndex)
        inputView?.updateSelection(newSelStart, newSelEnd)
    }

    private val contentSize = floatArrayOf(0f, 0f)
    private val decorLocation = floatArrayOf(0f, 0f)
    private val decorLocationInt = intArrayOf(0, 0)
    private var decorLocationUpdated = false

    private fun updateDecorLocation() {
        contentSize[0] = contentView.width.toFloat()
        contentSize[1] = contentView.height.toFloat()
        decorView.getLocationOnScreen(decorLocationInt)
        decorLocation[0] = decorLocationInt[0].toFloat()
        decorLocation[1] = decorLocationInt[1].toFloat()
        // contentSize and decorLocation can be completely wrong,
        // when measuring right after the very first onStartInputView() of an IMS' lifecycle
        if (contentSize[0] > 0 && contentSize[1] > 0) {
            decorLocationUpdated = true
        }
    }

    private val anchorPosition = floatArrayOf(0f, 0f, 0f, 0f)

    override fun onUpdateCursorAnchorInfo(info: CursorAnchorInfo) {
        val bounds = info.getCharacterBounds(0)
        if (bounds != null) {
            // anchor to start of composing span instead of insertion mark if available
            val horizontal =
                if (candidatesView?.layoutDirection == View.LAYOUT_DIRECTION_RTL) bounds.right else bounds.left
            anchorPosition[0] = horizontal
            anchorPosition[1] = bounds.bottom
            anchorPosition[2] = horizontal
            anchorPosition[3] = bounds.top
        } else {
            anchorPosition[0] = info.insertionMarkerHorizontal
            anchorPosition[1] = info.insertionMarkerBottom
            anchorPosition[2] = info.insertionMarkerHorizontal
            anchorPosition[3] = info.insertionMarkerTop
        }
        // avoid calling `decorView.getLocationOnScreen` repeatedly
        if (!decorLocationUpdated) {
            updateDecorLocation()
        }
        if (anchorPosition.any(Float::isNaN)) {
            // anchor candidates view to bottom-left corner in case CursorAnchorInfo is invalid
            candidatesView?.updateCursorAnchor(contentSize)
            return
        }
        // params of `Matrix.mapPoints` must be [x0, y0, x1, y1]
        info.matrix.mapPoints(anchorPosition)
        val (xOffset, yOffset) = decorLocation
        anchorPosition[0] -= xOffset
        anchorPosition[1] -= yOffset
        anchorPosition[2] -= xOffset
        anchorPosition[3] -= yOffset
        candidatesView?.updateCursorAnchor(anchorPosition, contentSize)
    }

    private fun handleCursorUpdate(newSelStart: Int, newSelEnd: Int, updateIndex: Int) {
        if (selection.consume(newSelStart, newSelEnd)) {
            return // do nothing if prediction matches
        } else {
            // cursor update can't match any prediction: it's treated as a user input
            selection.resetTo(newSelStart, newSelEnd)
        }
        // skip selection range update, we only care about selection cursor (zero width) here
        if (newSelStart != newSelEnd) return
        // do reset if composing is empty && input panel is not empty
        if (composing.isEmpty()) {
            postFcitxJob {
                if (!isEmpty()) {
                    Timber.d("handleCursorUpdate: reset")
                    reset()
                }
            }
            return
        }
        // check if cursor inside composing text
        if (composing.contains(newSelStart)) {
            if (ignoreSystemCursor) return
            // fcitx cursor position is relative to client preedit (composing text)
            val position = newSelStart - composing.start
            // move fcitx cursor when cursor position changed
            if (position != composingText.cursor) {
                // cursor in InvokeActionEvent counts by "UTF-8 characters"
                val codePointPosition = composingText.codePointCountUntil(position)
                postFcitxJob {
                    if (updateIndex != cursorUpdateIndex) return@postFcitxJob
                    Timber.d("handleCursorUpdate: move fcitx cursor to $codePointPosition")
                    moveCursor(codePointPosition)
                }
            }
        } else {
            Timber.d("handleCursorUpdate: focus out/in")
            resetComposingState()
            // cursor outside composing range, finish composing as-is
            currentInputConnection?.finishComposingText()
            // `fcitx.reset()` here would commit preedit after new cursor position
            // since we have `ClientUnfocusCommit`, focus out and in would do the trick
            postFcitxJob {
                focusOutIn()
            }
        }
    }

    // because setComposingText(text, cursor) can only put cursor at end of composing,
    // sometimes onUpdateSelection would receive event with wrong cursor position.
    // those events need to be filtered.
    // because of https://android.googlesource.com/platform/frameworks/base.git/+/refs/tags/android-11.0.0_r45/core/java/android/view/inputmethod/BaseInputConnection.java#851
    // it's not possible to set cursor inside composing text
    private fun updateComposingText(text: FormattedText) {
        val ic = currentInputConnection ?: return
        val lastSelection = selection.latest
        ic.beginBatchEdit()
        if (composingText.spanEquals(text)) {
            // composing text content is up-to-date
            // update cursor only when it's not empty AND cursor position is valid
            if (text.length > 0 && text.cursor >= 0) {
                val p = text.cursor + composing.start
                if (p != lastSelection.start) {
                    Timber.d("updateComposingText: set Android selection ($p, $p)")
                    ic.setSelection(p, p)
                    selection.predict(p)
                }
            }
        } else {
            // composing text content changed
            Timber.d("updateComposingText: '$text' lastSelection=$lastSelection")
            if (text.isEmpty()) {
                if (composing.isEmpty()) {
                    // do not reset saved selection range when incoming composing
                    // and saved composing range are both empty:
                    // composing.start is invalid when it's empty.
                    selection.predict(lastSelection.start)
                } else {
                    // clear composing text, put cursor at start of original composing
                    selection.predict(composing.start)
                    composing.clear()
                }
                ic.setComposingText("", 1)
            } else {
                val start = if (composing.isEmpty()) lastSelection.start else composing.start
                composing.update(start, start + text.length)
                // skip cursor reposition when:
                // - preedit cursor is at the end
                // - cursor position is invalid
                if (text.cursor == text.length || text.cursor < 0) {
                    selection.predict(composing.end)
                    ic.setComposingText(text.toSpannedString(highlightColor), 1)
                } else {
                    val p = text.cursor + composing.start
                    selection.predict(p)
                    ic.setComposingText(text.toSpannedString(highlightColor), 1)
                    ic.setSelection(p, p)
                }
            }
            Timber.d("updateComposingText: composing=$composing")
        }
        composingText = text
        ic.endBatchEdit()
    }

    /**
     * Finish composing text and leave cursor position as-is.
     * Also updates internal composing state of [FcitxInputMethodService].
     */
    fun finishComposing() {
        val ic = currentInputConnection ?: return
        if (composing.isEmpty()) return
        composing.clear()
        composingText = FormattedText.Empty
        ic.finishComposingText()
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        // ignore inline suggestion when disabled by user || using physical keyboard with floating candidates view
        if (!inlineSuggestions || !inputDeviceMgr.isVirtualKeyboard) return null
        val theme = ThemeManager.activeTheme
        val chipDrawable =
            if (theme.isDark) R.drawable.bkg_inline_suggestion_dark else R.drawable.bkg_inline_suggestion_light
        val chipBg = Icon.createWithResource(this, chipDrawable).setTint(theme.keyTextColor)
        val style = InlineSuggestionUi.newStyleBuilder()
            .setSingleIconChipStyle(
                ViewStyle.Builder()
                    .setBackgroundColor(Color.TRANSPARENT)
                    .setPadding(0, 0, 0, 0)
                    .build()
            )
            .setChipStyle(
                ViewStyle.Builder()
                    .setBackground(chipBg)
                    .setPadding(dp(10), 0, dp(10), 0)
                    .build()
            )
            .setTitleStyle(
                TextViewStyle.Builder()
                    .setLayoutMargin(dp(4), 0, dp(4), 0)
                    .setTextColor(theme.keyTextColor)
                    .setTextSize(14f)
                    .build()
            )
            .setSubtitleStyle(
                TextViewStyle.Builder()
                    .setTextColor(theme.altKeyTextColor)
                    .setTextSize(12f)
                    .build()
            )
            .setStartIconStyle(
                ImageViewStyle.Builder()
                    .setTintList(ColorStateList.valueOf(theme.altKeyTextColor))
                    .build()
            )
            .setEndIconStyle(
                ImageViewStyle.Builder()
                    .setTintList(ColorStateList.valueOf(theme.altKeyTextColor))
                    .build()
            )
            .build()
        val styleBundle = UiVersions.newStylesBuilder()
            .addStyle(style)
            .build()
        val spec = InlinePresentationSpec
            .Builder(Size(0, 0), Size(Int.MAX_VALUE, Int.MAX_VALUE))
            .setStyle(styleBundle)
            .build()
        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (!inlineSuggestions || !inputDeviceMgr.isVirtualKeyboard) return false
        return inputView?.handleInlineSuggestions(response) == true
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        hostClipboard.hideKeyboard()
        Timber.d("onFinishInputView: finishingInput=$finishingInput")
        inputView?.dismissKeyboardAdjustment()
        decorLocationUpdated = false
        inputDeviceMgr.onFinishInputView()
        currentInputConnection?.apply {
            finishComposingText()
            monitorCursorAnchor(false)
        }
        resetComposingState()
        clearT9Composition()
        postFcitxJob {
            focusOutIn()
        }
        hideStatusIcon()
        showingDialog?.dismiss()
    }

    override fun onFinishInput() {
        hostClipboard.finishInput()
        Timber.d("onFinishInput")
        clearT9Composition()
        postFcitxJob {
            focus(false)
        }
        capabilityFlags = CapabilityFlags.DefaultFlags
    }

    override fun onUnbindInput() {
        hostClipboard.unbindInput()
        cachedKeyEvents.evictAll()
        cachedKeyEventIndex = 0
        cursorUpdateIndex = 0
        // currentInputBinding can be null on some devices under some special Multi-screen mode
        val uid = currentInputBinding?.uid ?: return
        Timber.d("onUnbindInput: uid=$uid")
        postFcitxJob {
            deactivate(uid)
        }
    }

    override fun onDestroy() {
        hostClipboard.destroy()
        recreateInputViewPrefs.forEach {
            it.unregisterOnChangeListener(recreateInputViewListener)
        }
        prefs.candidates.unregisterOnChangeListener(recreateCandidatesViewListener)
        ThemeManager.removeOnChangedListener(onThemeChangeListener)
        super.onDestroy()
        // Fcitx might be used in super.onDestroy()
        FcitxDaemon.disconnect(javaClass.name)
    }

    private var showingDialog: Dialog? = null

    fun showDialog(dialog: Dialog) {
        showAttachedDialog(dialog, dimBehind = true)
    }

    fun showAttachedDialog(dialog: Dialog, dimBehind: Boolean) {
        showingDialog?.dismiss()
        dialog.window?.also {
            it.attributes.apply {
                token = decorView.windowToken
                type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            WindowCompat.setDecorFitsSystemWindows(it, false)
            it.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            it.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            it.statusBarColor = Color.TRANSPARENT
            it.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.isNavigationBarContrastEnforced = false
                it.isStatusBarContrastEnforced = false
            }
            it.setBackgroundDrawableResource(android.R.color.transparent)
            it.decorView.setBackgroundColor(Color.TRANSPARENT)
            if (dimBehind) {
                it.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                it.setDimAmount(styledFloat(android.R.attr.backgroundDimAmount))
            } else {
                it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                it.setDimAmount(0f)
            }
        }
        dialog.setOnDismissListener {
            showingDialog = null
            (dialog as? AttachedDialogDismissHandler)?.onAttachedDialogDismissed()
        }
        dialog.show()
        showingDialog = dialog
    }

    fun currentKeyboardWindowHeightPx(): Int {
        val barHeightPx = dp(KawaiiBarComponent.HEIGHT)
        return inputView?.keyboardView?.height?.takeIf { it > 0 }?.let { it + barHeightPx }
            ?: InputWindowSizeOverride.getKeyboardHeightPxOverride()
            ?: (resources.displayMetrics.heightPixels * 38 / 100)
    }

    fun beginKeyboardAdjustment(): Boolean {
        val view = inputView ?: return false
        view.keyboardView.post {
            view.beginKeyboardAdjustment()
        }
        return true
    }

    fun dismissKeyboardAdjustment(): Boolean {
        val view = inputView ?: return false
        view.keyboardView.post {
            view.dismissKeyboardAdjustment()
        }
        return true
    }

    @Suppress("ConstPropertyName")
    companion object {
        const val DeleteSurroundingFlag = "org.fcitx.fcitx5.android.DELETE_SURROUNDING"
    }
}
