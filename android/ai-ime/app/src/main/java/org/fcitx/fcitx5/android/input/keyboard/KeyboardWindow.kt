/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.transition.Slide
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.t9.T9SidebarState
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import kotlin.math.roundToInt

class KeyboardWindow : InputWindow.SimpleInputWindow<KeyboardWindow>(), EssentialWindow,
    InputBroadcastReceiver {

    private val service by manager.inputMethodService()
    private val fcitx by manager.fcitx()
    private val theme by manager.theme()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val windowManager: InputWindowManager by manager.must()
    private val popup: PopupComponent by manager.must()
    private val bar: KawaiiBarComponent by manager.must()
    private val returnKeyDrawable: ReturnKeyDrawableComponent by manager.must()

    companion object : EssentialWindow.Key

    override val key: EssentialWindow.Key
        get() = KeyboardWindow

    override fun enterAnimation(lastWindow: InputWindow) = Slide().apply {
        slideEdge = Gravity.BOTTOM
    }.takeIf {
        // disable animation switching between picker
        lastWindow !is PickerWindow
    }

    override fun exitAnimation(nextWindow: InputWindow) =
        super.exitAnimation(nextWindow).takeIf {
            // disable animation switching between picker
            nextWindow !is PickerWindow
        }

    private lateinit var keyboardView: FrameLayout

    private val keyboards: HashMap<String, BaseKeyboard> by lazy {
        hashMapOf(
            TextKeyboard.Name to TextKeyboard(context, theme),
            NumberKeyboard.Name to NumberKeyboard(context, theme),
            T9Keyboard.Name to T9Keyboard(context, theme)
        )
    }
    private var currentKeyboardName = ""
    private var lastMainKeyboardName = T9Keyboard.Name
    private var lastSymbolType: String by AppPrefs.getInstance().internal.lastSymbolLayout
    private val useT9KeyboardLayout by AppPrefs.getInstance().keyboard.useT9KeyboardLayout

    var onLayoutChanged: ((String) -> Unit)? = null

    private val currentKeyboard: BaseKeyboard? get() = keyboards[currentKeyboardName]

    private val keyActionListener = KeyActionListener { it, source ->
        when (it) {
            is KeyAction.LayoutSwitchAction -> switchLayout(it.act)
            is KeyAction.MainKeyboardSwitchAction -> switchLayout(resolveMainKeyboardTarget(), remember = false)
            is KeyAction.T9ModeSwitchAction -> {
                service.switchToNextT9Mode()
            }
            is KeyAction.LangSwitchAction ->
                if (currentKeyboardName == TextKeyboard.Name && service.isT9EnglishMode()) {
                    service.switchToNextT9Mode()
                } else if (currentKeyboardName == TextKeyboard.Name) {
                    service.toggleFullKeyboardChineseEnglish()
                } else {
                    commonKeyActionListener.listener.onKeyAction(it, source)
                }
            else -> commonKeyActionListener.listener.onKeyAction(it, source)
        }
    }

    private val popupActionListener: PopupActionListener by lazy {
        popup.listener
    }

    // This will be called EXACTLY ONCE
    override fun onCreateView(): View {
        keyboardView = context.frameLayout(R.id.keyboard_view)
        attachLayout(defaultMainKeyboard)
        return keyboardView
    }

    private fun detachCurrentLayout() {
        currentKeyboard?.also {
            it.onDetach()
            keyboardView.removeView(it)
            it.keyActionListener = null
            it.popupActionListener = null
            (it as? T9Keyboard)?.let { t9Keyboard ->
                t9Keyboard.onSidebarCandidatePressed = null
                t9Keyboard.onSidebarSwipeUp = null
            }
        }
    }

    private fun attachLayout(target: String) {
        currentKeyboardName = target
        if (target == TextKeyboard.Name || target == T9Keyboard.Name) {
            lastMainKeyboardName = target
        }
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            keyboardView.apply { add(it, lParams(matchParent, matchParent)) }
            it.onAttach()
            it.onReturnDrawableUpdate(returnKeyDrawable.resourceId)
            it.onInputMethodUpdate(fcitx.runImmediately { inputMethodEntryCached })
            (it as? T9Keyboard)?.let(::bindT9Keyboard)
        }
        onLayoutChanged?.invoke(target)
    }

    private fun bindT9Keyboard(keyboard: T9Keyboard) {
        keyboard.onSidebarCandidatePressed = { index ->
            service.onT9SidebarCandidatePressed(index)
        }
        keyboard.onSidebarSwipeUp = {
            service.advanceT9SidebarPage()
        }
        keyboard.updateT9ModeLabel(service.getCurrentT9ModeLabel())
        keyboard.updateSidebarState(service.getT9SidebarState())
    }

    private val defaultMainKeyboard: String
        get() = if (useT9KeyboardLayout) T9Keyboard.Name else TextKeyboard.Name

    private fun resolveMainKeyboardTarget(): String {
        if (!useT9KeyboardLayout) return TextKeyboard.Name
        return when (lastMainKeyboardName) {
            TextKeyboard.Name, T9Keyboard.Name -> lastMainKeyboardName
            else -> defaultMainKeyboard
        }
    }

    fun switchToMainKeyboard() {
        switchLayout(resolveMainKeyboardTarget(), remember = false)
    }

    fun switchLayout(to: String, remember: Boolean = true) {
        val requestedTarget = when {
            to.isNotEmpty() -> to
            keyboards.containsKey(lastSymbolType) -> lastSymbolType
            else -> defaultMainKeyboard
        }
        val target = requestedTarget
        ContextCompat.getMainExecutor(service).execute {
            if (keyboards.containsKey(target)) {
                if (remember && target != TextKeyboard.Name && target != T9Keyboard.Name) {
                    lastSymbolType = target
                }
                if (target == currentKeyboardName) return@execute
                detachCurrentLayout()
                attachLayout(target)
                if (windowManager.isAttached(this)) {
                    notifyBarLayoutChanged()
                }
            } else {
                if (remember) {
                    lastSymbolType = PickerWindow.Key.Symbol.name
                }
                windowManager.attachWindow(PickerWindow.Key.Symbol)
            }
        }
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        val targetLayout = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> NumberKeyboard.Name
            InputType.TYPE_CLASS_PHONE -> NumberKeyboard.Name
            else -> defaultMainKeyboard
        }
        switchLayout(targetLayout, remember = false)
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        currentKeyboard?.onInputMethodUpdate(ime)
        (currentKeyboard as? T9Keyboard)?.let(::bindT9Keyboard)
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        currentKeyboard?.onPunctuationUpdate(mapping)
    }

    override fun onReturnKeyDrawableUpdate(resourceId: Int) {
        currentKeyboard?.onReturnDrawableUpdate(resourceId)
    }

    override fun onAttached() {
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            it.onAttach()
            (it as? T9Keyboard)?.let(::bindT9Keyboard)
        }
        onLayoutChanged?.invoke(currentKeyboardName)
        notifyBarLayoutChanged()
    }

    fun isCurrentLayoutT9(): Boolean = currentKeyboardName == T9Keyboard.Name

    override fun onT9ModeUpdate(modeLabel: String) {
        (currentKeyboard as? T9Keyboard)?.let {
            it.updateT9ModeLabel(modeLabel)
            it.updateSidebarState(service.getT9SidebarState())
        }
    }

    fun updateT9SidebarState(state: T9SidebarState) {
        (currentKeyboard as? T9Keyboard)?.updateSidebarState(state)
    }

    override fun onDetached() {
        currentKeyboard?.let {
            it.onDetach()
            it.keyActionListener = null
            it.popupActionListener = null
        }
        popup.dismissAll()
    }

    fun preferredHeightPx(): Int {
        val measured = keyboardView.height.takeIf { it > 0 } ?: keyboardView.measuredHeight.takeIf { it > 0 }
        if (measured != null) return measured
        return (context.resources.displayMetrics.heightPixels * 0.38f).roundToInt()
    }

    fun mountEmbedded(container: FrameLayout) {
        val parent = keyboardView.parent as? FrameLayout
        if (parent === container) {
            onAttached()
            return
        }
        parent?.removeView(keyboardView)
        container.removeAllViews()
        container.addView(
            keyboardView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        onAttached()
    }

    fun unmountEmbedded() {
        if (!::keyboardView.isInitialized) return
        onDetached()
        (keyboardView.parent as? FrameLayout)?.removeView(keyboardView)
    }

    // Call this when
    // 1) the keyboard window was newly attached
    // 2) currently keyboard window is attached and switchLayout was used
    private fun notifyBarLayoutChanged() {
        bar.onKeyboardLayoutSwitched(currentKeyboardName == NumberKeyboard.Name,
            when (currentKeyboardName) { T9Keyboard.Name -> "t9"; NumberKeyboard.Name -> "number"; else -> "qwerty" })
    }
}
