/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import android.graphics.Rect
import android.graphics.Canvas
import android.view.MotionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.allViews
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.SkinSoundResolver
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView.GestureType
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView.OnGestureListener
import org.fcitx.fcitx5.android.input.popup.PopupAction
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.input.status.KeyboardSelectionPreset
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.leftToRightOf
import splitties.views.dsl.constraintlayout.rightOfParent
import splitties.views.dsl.constraintlayout.rightToLeftOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

abstract class BaseKeyboard(
    context: Context,
    protected val theme: Theme,
    private val keyLayout: List<List<KeyDef>>,
    val skinLayoutId: String = "",
    private val forceKeyboardPopups: Boolean = false
) : ConstraintLayout(context) {

    private val letterTaps = LetterTapOrder()
    var keyActionListener: KeyActionListener? = null

    private val skinStyle = (theme as? Theme.Custom)?.renderStyle
    private val useFallbackParticles = skinStyle?.keyPressEffect == null && skinStyle?.suppressDefaultKeyEffect != true &&
        (skinStyle?.localConfiguration?.effectId ?: "skin") == "skin" &&
        skinStyle?.ambientSpaceEffect == null && skinStyle?.ambientSidebarEffect == null &&
        (theme as? Theme.Custom)?.keyAnimations.isNullOrEmpty()
    private val skinPressRenderer = if (skinLayoutId.isEmpty()) null else
        (skinStyle?.keyPressEffect ?: if (useFallbackParticles)
            org.fcitx.fcitx5.android.data.theme.SkinKeyPressEffect.lightweight(theme.keyTextColor) else null)
            ?.let { SkinKeyPressRenderer(resources.displayMetrics.density, it) }

    private var spriteLoad: Job? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val renderer = skinPressRenderer ?: return
        val images = (theme as? Theme.Custom)?.renderStyle?.keyPressEffect?.particleSprites.orEmpty()
        if (images.isEmpty()) return
        spriteLoad = CoroutineScope(Dispatchers.Main.immediate).launch {
            // Ordinary and protected assets use the same ownership-aware image cache.
            val ready = withContext(Dispatchers.IO) { images.mapNotNull { it.toDrawable(context) } }
            renderer.setParticleSprites(ready)
        }
    }

    private val effectAboveFaces = skinStyle?.localConfiguration?.effectAboveFaces ?:
        (useFallbackParticles || (skinStyle?.keyEffectAboveFaces == true && skinStyle.keyPressEffect?.particleSprites?.isNotEmpty() == true))
    private val scaleEffectToKeyPitch = skinStyle?.keyEffectAboveFaces == true || useFallbackParticles

    val activeSkinEffectCount: Int get() = skinPressRenderer?.activeCount ?: 0

    private val skinDecorationRenderer = (theme as? Theme.Custom)?.keyDecorations
        ?.takeIf { it.isNotEmpty() }
        ?.let { SkinKeyDecorationRenderer(resources.displayMetrics.density, it) }

    private val skinShadowRenderer = (theme as? Theme.Custom)?.renderStyle?.keyFaceShadow
        ?.let { SkinKeyShadowRenderer(resources.displayMetrics.density, it) }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        alignSharedFaces()
        skinDecorationRenderer?.layout(this)
        skinShadowRenderer?.layout(this)
        if (theme is Theme.Custom) toolbarKeyEdges = outerKeyEdges()
    }

    protected open fun alignSharedFaces() {}
    internal open val sidebarFace: android.view.View? get() = null

    internal var toolbarKeyEdges: Pair<Int, Int>? = null
        private set

    private fun outerKeyEdges(): Pair<Int, Int>? {
        var left = width
        var right = 0
        allViews.filterIsInstance<KeyView>().filter { it.paintsSkinCap && !it.sharedSidebarSlot }.forEach {
            val face = it.skinShadowBoundsIn(this)
            if (!face.isEmpty) {
                left = minOf(left, face.left.roundToInt())
                right = maxOf(right, face.right.roundToInt())
            }
        }
        sidebarFace?.let {
            left = minOf(left, it.left)
            right = maxOf(right, it.right)
        }
        return if (right > left) left to right else null
    }

    private var sidebarTouch: KeyView? = null
    protected var sidebarPressSurface: android.view.View? = null

    protected fun sidebarArtwork(custom: Theme.Custom?): android.graphics.drawable.Drawable? {
        val rest = custom?.takeIf { it.renderStyle?.unifiedSidebar == true }?.sidebarImage?.toDrawable(context) ?: return null
        val states = custom.keyStateImages["$skinLayoutId/sidebar"] ?: return rest
        val pressed = states.pressed.toDrawable(context) ?: return rest
        return SkinKeyStateDrawable(rest, pressed, states.released?.toDrawable(context), custom.renderStyle?.keyMotion?.releaseMs ?: 380)
    }

    internal fun onSkinKeyPress(key: KeyView) {
        if (key.sharedSidebarSlot && sidebarPressSurface != null) {
            sidebarTouch = key
            sidebarPressSurface?.isPressed = true
        }
        val renderer = skinPressRenderer ?: return
        if (!isShown) return
        val bounds = key.skinBoundsIn(this)
        val nominal = 100f * resources.displayMetrics.density
        val columns = if (skinLayoutId == "qwerty") 10 else 5
        val spanX = if (scaleEffectToKeyPitch) minOf(bounds.width(), width.toFloat() / columns) / nominal else 1f
        val spanY = if (scaleEffectToKeyPitch) minOf(bounds.height(), height / 4f) / nominal else 1f
        if (renderer.press(bounds, token = key, spanX = spanX, spanY = spanY)) postInvalidateOnAnimation()
    }

    internal fun onSkinKeyRelease(key: KeyView) {
        if (sidebarTouch === key) {
            sidebarTouch = null
            sidebarPressSurface?.isPressed = false
        }
        skinPressRenderer?.release(key)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_CANCEL) letterTaps.clear()
        // T9 candidate/sidebar updates can replace the touched child before UP.
        // End the gesture at its stable owner as well, so no orphan glow survives.
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            sidebarTouch = null
            sidebarPressSurface?.isPressed = false
            skinPressRenderer?.releaseAll()
            if ((skinPressRenderer?.activeCount ?: 0) > 0) postInvalidateOnAnimation()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        skinShadowRenderer?.draw(canvas, width, height)
        if (!effectAboveFaces) drawPressEffect(canvas)
        super.dispatchDraw(canvas)
        if (effectAboveFaces) drawPressEffect(canvas)
        skinDecorationRenderer?.draw(canvas, this)
    }

    private fun drawPressEffect(canvas: Canvas) {
        if (skinPressRenderer?.draw(canvas) == true) postInvalidateOnAnimation()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) {
            letterTaps.clear()
            skinPressRenderer?.clear()
        }
    }

    override fun onDetachedFromWindow() {
        letterTaps.clear()
        sidebarTouch = null
        sidebarPressSurface?.isPressed = false
        spriteLoad?.cancel()
        spriteLoad = null
        skinPressRenderer?.setParticleSprites(emptyList())
        skinShadowRenderer?.clear()
        skinPressRenderer?.clear()
        super.onDetachedFromWindow()
    }

    private val prefs = AppPrefs.getInstance()

    private val popupOnKeyPress: Boolean get() = (theme as? Theme.Custom)?.renderStyle?.localConfiguration?.bubbles
        ?: prefs.keyboard.popupOnKeyPress.getValue()
    private val expandKeypressArea by prefs.keyboard.expandKeypressArea
    private val swipeSymbolDirection by prefs.keyboard.swipeSymbolDirection

    private val spaceSwipeMoveCursor = prefs.keyboard.spaceSwipeMoveCursor
    private val spaceKeys = mutableListOf<KeyView>()
    private val spaceSwipeChangeListener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        spaceKeys.forEach {
            it.swipeEnabled = v
        }
    }

    private val vivoKeypressWorkaround by prefs.advanced.vivoKeypressWorkaround

    private val hapticOnRepeat by prefs.keyboard.hapticOnRepeat

    var popupActionListener: PopupActionListener? = null

    private val selectionSwipeThreshold = dp(10f)
    private val inputSwipeThreshold = dp(36f)

    // a rather large threshold effectively disables swipe of the direction
    private val disabledSwipeThreshold = dp(800f)

    private val keyRows: List<ConstraintLayout>

    init {
        isMotionEventSplittingEnabled = true
        keyRows = keyLayout.mapIndexed { rowIndex, row ->
            val keyViews = row.mapIndexed { columnIndex, key ->
                createKeyView(key, "r${rowIndex + 1}c${columnIndex + 1}")
            }
            val widths = row.mapIndexed { index, def ->
                if (skinLayoutId == "qwerty" && rowIndex == keyLayout.lastIndex)
                    keyViews[index].skinKeyIds.firstNotNullOfOrNull { id ->
                        (theme as? Theme.Custom)?.renderStyle?.qwertyBottomRowWidths?.get(id)
                    }
                        ?: def.appearance.percentWidth
                else def.appearance.percentWidth
            }.let { proposed ->
                // Reject an overfull authored row as a whole, retaining all native widths.
                if (proposed.filter { it > 0f }.sum() <= .9f || proposed == row.map { it.appearance.percentWidth }) proposed
                else row.map { it.appearance.percentWidth }
            }
            constraintLayout Row@{
                isMotionEventSplittingEnabled = true
                var totalWidth = 0f
                keyViews.forEachIndexed { index, view ->
                    add(view, lParams {
                        centerVertically()
                        if (index == 0) {
                            leftOfParent()
                            horizontalChainStyle = LayoutParams.CHAIN_PACKED
                        } else {
                            leftToRightOf(keyViews[index - 1])
                        }
                        if (index == keyViews.size - 1) {
                            rightOfParent()
                            // for RTL
                            horizontalChainStyle = LayoutParams.CHAIN_PACKED
                        } else {
                            rightToLeftOf(keyViews[index + 1])
                        }
                        val def = row[index]
                        matchConstraintPercentWidth = widths[index]
                    })
                    widths[index].let {
                        // 0f means fill remaining space, thus does not need expanding
                        totalWidth += if (it != 0f) it else 1f
                    }
                }
                if (expandKeypressArea && totalWidth < 1f) {
                    val free = (1f - totalWidth) / 2f
                    keyViews.first().apply {
                        updateLayoutParams<LayoutParams> {
                            matchConstraintPercentWidth += free
                        }
                        layoutMarginLeft = free / (row.first().appearance.percentWidth + free)
                    }
                    keyViews.last().apply {
                        updateLayoutParams<LayoutParams> {
                            matchConstraintPercentWidth += free
                        }
                        layoutMarginRight = free / (row.last().appearance.percentWidth + free)
                    }
                }
            }
        }
        keyRows.forEachIndexed { index, row ->
            add(row, lParams {
                if (index == 0) topOfParent()
                else below(keyRows[index - 1])
                if (index == keyRows.size - 1) bottomOfParent()
                else above(keyRows[index + 1])
                centerHorizontally()
            })
        }
        spaceSwipeMoveCursor.registerOnChangeListener(spaceSwipeChangeListener)
    }

    protected fun createKeyView(def: KeyDef, positionId: String? = null): KeyView {
        val address = SkinKeyAddress(skinLayoutId, positionId)
        return when (def.appearance) {
            is KeyDef.Appearance.AltText -> AltTextKeyView(context, theme, def.appearance, address)
            is KeyDef.Appearance.ImageText -> ImageTextKeyView(context, theme, def.appearance, address)
            is KeyDef.Appearance.StackedText -> StackedTextKeyView(context, theme, def.appearance, address)
            is KeyDef.Appearance.Text -> TextKeyView(context, theme, def.appearance, address)
            is KeyDef.Appearance.Image -> ImageKeyView(context, theme, def.appearance, address)
        }.apply {
            val action = def.behaviors.filterIsInstance<KeyDef.Behavior.Press>().firstOrNull()?.action
            if (def is AlphabetKey || def is AlphabetDigitKey) letterTapOrder = letterTaps
            val command = when (action) {
                is KeyAction.SymAction -> when (action.sym.sym) {
                    FcitxKeyMapping.FcitxKey_space -> "space"
                    FcitxKeyMapping.FcitxKey_Return -> "enter"
                    FcitxKeyMapping.FcitxKey_BackSpace -> "backspace"
                    else -> ""
                }
                is KeyAction.FcitxKeyAction -> if (skinLayoutId == "t9" && action.act == "'") "separator" else action.act
                is KeyAction.CommitAction -> action.text
                is KeyAction.CapsAction -> "shift"
                KeyAction.LangSwitchAction, KeyAction.T9ModeSwitchAction -> "language"
                KeyAction.T9ResetAction -> "retype"
                KeyAction.MainKeyboardSwitchAction -> if (skinLayoutId == "number") "number_return" else ""
                is KeyAction.LayoutSwitchAction -> when (action.act) {
                    NumberKeyboard.Name -> "number_mode"
                    PickerWindow.Key.Symbol.name -> "symbol"
                    PickerWindow.Key.Emoji.name -> "emoji"
                    else -> ""
                }
                is KeyAction.PickerSwitchAction -> when (action.key) {
                    PickerWindow.Key.Emoji -> "emoji"
                    PickerWindow.Key.Symbol -> "symbol"
                    else -> ""
                }
                else -> ""
            }
            skinSoundCandidates = SkinSoundResolver.candidates((theme as? Theme.Custom)?.skinSound,
                SkinSoundResolver.key(skinLayoutId, positionId, command, skinKeyId))
            soundEffect = when (def) {
                is SpaceKey -> InputFeedbacks.SoundEffect.SpaceBar
                is MiniSpaceKey -> InputFeedbacks.SoundEffect.SpaceBar
                is BackspaceKey -> InputFeedbacks.SoundEffect.Delete
                is ReturnKey -> InputFeedbacks.SoundEffect.Return
                else -> InputFeedbacks.SoundEffect.Standard
            }
            // Custom T9/number/mini space keys share the same action, not the preset class.
            if (command == "space") {
                spaceKeys.add(this)
                swipeEnabled = spaceSwipeMoveCursor.getValue()
                swipeRepeatEnabled = true
                swipeThresholdX = selectionSwipeThreshold
                swipeThresholdY = disabledSwipeThreshold
                onGestureListener = OnGestureListener { view, event ->
                    when (event.type) {
                        GestureType.Move -> when (val count = event.countX) {
                            0 -> false
                            else -> {
                                val sym =
                                    if (count > 0) FcitxKeyMapping.FcitxKey_Right else FcitxKeyMapping.FcitxKey_Left
                                val action = KeyAction.SymAction(KeySym(sym), KeyStates.Virtual)
                                repeat(count.absoluteValue) {
                                    onAction(action)
                                    if (hapticOnRepeat) InputFeedbacks.hapticFeedback(view)
                                }
                                true
                            }
                        }
                        else -> false
                    }
                }
            } else if (def is BackspaceKey) {
                swipeEnabled = true
                swipeRepeatEnabled = true
                swipeThresholdX = selectionSwipeThreshold
                swipeThresholdY = disabledSwipeThreshold
                onGestureListener = OnGestureListener { view, event ->
                    when (event.type) {
                        GestureType.Move -> {
                            val count = event.countX
                            if (count != 0) {
                                onAction(KeyAction.MoveSelectionAction(count))
                                if (hapticOnRepeat) InputFeedbacks.hapticFeedback(view)
                                true
                            } else false
                        }
                        GestureType.Up -> {
                            onAction(KeyAction.DeleteSelectionAction(event.totalX))
                            false
                        }
                        GestureType.Cancel -> {
                            onAction(KeyAction.CancelSelectionAction)
                            false
                        }
                        else -> false
                    }
                }
            }
            def.behaviors.forEach {
                when (it) {
                    is KeyDef.Behavior.Press -> {
                        val press = it.action
                        setOnClickListener {
                            if (letterTapOrder != null) onAction(press)
                            else letterTaps.afterLetters { onAction(press) }
                        }
                    }
                    is KeyDef.Behavior.LongPress -> {
                        setOnLongClickListener { _ ->
                            onAction(it.action)
                            true
                        }
                    }
                    is KeyDef.Behavior.Repeat -> {
                        repeatEnabled = true
                        onRepeatListener = { view ->
                            onAction(it.action)
                            if (hapticOnRepeat) InputFeedbacks.hapticFeedback(view)
                        }
                    }
                    is KeyDef.Behavior.Swipe -> {
                        swipeEnabled = true
                        swipeThresholdX = disabledSwipeThreshold
                        swipeThresholdY = inputSwipeThreshold
                        val oldOnGestureListener = onGestureListener ?: OnGestureListener.Empty
                        onGestureListener = OnGestureListener { view, event ->
                            when (event.type) {
                                GestureType.Up -> {
                                    if (!event.consumed && swipeSymbolDirection.checkY(event.totalY)) {
                                        onAction(it.action)
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            } || oldOnGestureListener.onGesture(view, event)
                        }
                    }
                    is KeyDef.Behavior.DoubleTap -> {
                        doubleTapEnabled = true
                        onDoubleTapListener = { _ ->
                            onAction(it.action)
                        }
                    }
                }
            }
            if (def is BackspaceKey) {
                deleteGesture = org.fcitx.fcitx5.android.input.deletion.DeleteKeyGesture(this, this@BaseKeyboard) { onAction(it) }
            }
            if ((theme as? Theme.Custom)?.renderStyle?.skinLabStandard == true) popupLongPressDelayMs = 1000
            def.popup?.forEach {
                when (it) {
                    // TODO: gesture processing middleware
                    is KeyDef.Popup.Menu -> {
                        setOnLongClickListener { view ->
                            if (!popupOnKeyPress && !forceKeyboardPopups) return@setOnLongClickListener false
                            view as KeyView
                            onPopupAction(PopupAction.ShowMenuAction(view.id, it, view.bounds))
                            // do not consume this LongClick gesture
                            false
                        }
                        val oldOnGestureListener = onGestureListener ?: OnGestureListener.Empty
                        swipeEnabled = true
                        onGestureListener = OnGestureListener { view, event ->
                            if (!popupOnKeyPress && !forceKeyboardPopups) {
                                return@OnGestureListener oldOnGestureListener.onGesture(view, event)
                            }
                            view as KeyView
                            when (event.type) {
                                GestureType.Move -> {
                                    onPopupChangeFocus(view.id, event.x, event.y)
                                }
                                GestureType.Up -> {
                                    onPopupTrigger(view.id)
                                }
                                GestureType.Cancel -> {
                                    onPopupAction(PopupAction.DismissAction(view.id))
                                    false
                                }
                                else -> false
                            } || oldOnGestureListener.onGesture(view, event)
                        }
                    }
                    is KeyDef.Popup.Punctuation -> {
                        setOnLongClickListener { view ->
                            if (!popupOnKeyPress && !forceKeyboardPopups) return@setOnLongClickListener false
                            view as KeyView
                            onPopupAction(PopupAction.ShowPunctuationAction(view.id, it, view.bounds))
                            false
                        }
                        val oldOnGestureListener = onGestureListener ?: OnGestureListener.Empty
                        swipeEnabled = true
                        onGestureListener = OnGestureListener { view, event ->
                            if (!popupOnKeyPress && !forceKeyboardPopups) {
                                return@OnGestureListener oldOnGestureListener.onGesture(view, event)
                            }
                            view as KeyView
                            when (event.type) {
                                GestureType.Move -> {
                                    onPopupChangeFocus(view.id, event.x, event.y)
                                }
                                GestureType.Up -> {
                                    onPopupTrigger(view.id)
                                }
                                GestureType.Cancel -> {
                                    onPopupAction(PopupAction.DismissAction(view.id))
                                    false
                                }
                                else -> false
                            } || oldOnGestureListener.onGesture(view, event)
                        }
                    }
                    is KeyDef.Popup.Keyboard -> {
                        setOnLongClickListener { view ->
                            if (!popupOnKeyPress && !forceKeyboardPopups) return@setOnLongClickListener false
                            view as KeyView
                            val popup = if (it is KeyDef.Popup.Keyboard.Preset &&
                                (theme as? Theme.Custom)?.renderStyle?.skinLabStandard == true &&
                                skinLayoutId == "t9" && it.label.length == 1 && it.label[0] in '2'..'9'
                            ) {
                                val letters = listOf("", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz")[it.label.toInt()]
                                KeyDef.Popup.Keyboard.Explicit((letters.map(Char::toString) + it.label + letters.uppercase().map(Char::toString)).toTypedArray())
                            } else it
                            onPopupAction(PopupAction.ShowKeyboardAction(view.id, popup, view.bounds))
                            // do not consume this LongClick gesture
                            false
                        }
                        val oldOnGestureListener = onGestureListener ?: OnGestureListener.Empty
                        swipeEnabled = true
                        onGestureListener = OnGestureListener { view, event ->
                            if (!popupOnKeyPress && !forceKeyboardPopups) {
                                return@OnGestureListener oldOnGestureListener.onGesture(view, event)
                            }
                            view as KeyView
                            when (event.type) {
                                GestureType.Move -> {
                                    onPopupChangeFocus(view.id, event.x, event.y)
                                }
                                GestureType.Up -> {
                                    onPopupTrigger(view.id)
                                }
                                GestureType.Cancel -> {
                                    onPopupAction(PopupAction.DismissAction(view.id))
                                    false
                                }
                                else -> false
                            } || oldOnGestureListener.onGesture(view, event)
                        }
                    }
                    is KeyDef.Popup.AltPreview -> {
                        val oldOnGestureListener = onGestureListener ?: OnGestureListener.Empty
                        onGestureListener = OnGestureListener { view, event ->
                            view as KeyView
                            if (popupOnKeyPress && ((theme as? Theme.Custom)?.renderStyle?.localConfiguration != null || !((theme as? Theme.Custom)?.renderStyle?.skinLabStandard == true && skinLayoutId in listOf("t9", "number")))) {
                                when (event.type) {
                                    GestureType.Down -> onPopupAction(
                                        PopupAction.PreviewAction(view.id, it.content, view.bounds)
                                    )
                                    GestureType.Move -> {
                                        val triggered = swipeSymbolDirection.checkY(event.totalY)
                                        val text = if (triggered) it.alternative else it.content
                                        onPopupAction(
                                            PopupAction.PreviewUpdateAction(view.id, text)
                                        )
                                    }
                                    GestureType.Up, GestureType.Cancel -> {
                                        onPopupAction(PopupAction.DismissAction(view.id))
                                    }
                                }
                            }
                            // never consume gesture in preview popup
                            oldOnGestureListener.onGesture(view, event)
                        }
                    }
                    is KeyDef.Popup.Preview -> {
                        val oldOnGestureListener = onGestureListener ?: OnGestureListener.Empty
                        onGestureListener = OnGestureListener { view, event ->
                            view as KeyView
                            if (popupOnKeyPress && ((theme as? Theme.Custom)?.renderStyle?.localConfiguration != null || !((theme as? Theme.Custom)?.renderStyle?.skinLabStandard == true && skinLayoutId in listOf("t9", "number")))) {
                                when (event.type) {
                                    GestureType.Down -> onPopupAction(
                                        PopupAction.PreviewAction(view.id, it.content, view.bounds)
                                    )
                                    GestureType.Up, GestureType.Cancel -> {
                                        onPopupAction(PopupAction.DismissAction(view.id))
                                    }
                                    else -> {}
                                }
                            }
                            // never consume gesture in preview popup
                            oldOnGestureListener.onGesture(view, event)
                        }
                    }
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        skinPressRenderer?.clear()
        letterTaps.clear()
        releaseAllTouchTargets()
    }

    private class TouchTarget(val view: KeyView, val hitRect: Rect)

    /**
     * HashMap of [PointerId (Int)][MotionEvent.getPointerId] to [TouchTarget]
     * for custom touch event dispatching
     */
    private val touchTargets = hashMapOf<Int, TouchTarget>()

    private fun releaseAllTouchTargets() {
        touchTargets.forEach {
            val keyView = it.value.view
            keyView.cancelGestures()
            onPopupAction(PopupAction.DismissAction(keyView.id))
        }
        touchTargets.clear()
    }

    private fun findTouchTarget(event: MotionEvent, pointerIndex: Int): TouchTarget? {
        val x0 = event.getX(pointerIndex).roundToInt()
        val y0 = event.getY(pointerIndex).roundToInt()
        val rowHitRect = Rect()
        val row = keyRows.find {
            it.getHitRect(rowHitRect)
            rowHitRect.contains(x0, y0)
        } ?: return null
        val x1 = x0 - rowHitRect.left
        val y1 = y0 - rowHitRect.top
        val keyHitRect = Rect()
        val key = row.children.filterIsInstance<KeyView>().find {
            it.getHitRect(keyHitRect)
            keyHitRect.contains(x1, y1)
        } ?: return null
        keyHitRect.offset(rowHitRect.left, rowHitRect.top)
        return TouchTarget(key, keyHitRect)
    }

    private fun dispatchMotionEventToTarget(
        event: MotionEvent,
        action: Int,
        pointerIndex: Int,
        target: TouchTarget
    ) {
        val childX = event.getX(pointerIndex) - target.hitRect.left
        val childY = event.getY(pointerIndex) - target.hitRect.top
        val e = MotionEvent.obtain(
            event.downTime, event.eventTime, action,
            childX, childY, event.getPressure(pointerIndex), event.getSize(pointerIndex),
            event.metaState, event.xPrecision, event.yPrecision,
            event.deviceId, event.edgeFlags
        )
        try {
            target.view.dispatchTouchEvent(e)
        } finally {
            e.recycle()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // intercept ACTION_DOWN and all following events will go to parent's onTouchEvent
        return if (vivoKeypressWorkaround && ev.actionMasked == MotionEvent.ACTION_DOWN) true
        else super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (vivoKeypressWorkaround) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    releaseAllTouchTargets()
                    val pid = event.getPointerId(0)
                    val target = findTouchTarget(event, 0) ?: return false
                    touchTargets[pid] = target
                    dispatchMotionEventToTarget(event, MotionEvent.ACTION_DOWN, 0, target)
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = event.actionIndex
                    val pid = event.getPointerId(i)
                    val target = findTouchTarget(event, i) ?: return true
                    if (target.view.letterTapOrder != null) {
                        val prior = touchTargets.entries.firstOrNull { it.value.view === target.view }
                        if (prior != null) {
                            val index = event.findPointerIndex(prior.key)
                            if (index >= 0) dispatchMotionEventToTarget(event, MotionEvent.ACTION_UP, index, prior.value)
                            touchTargets.remove(prior.key)
                        }
                    }
                    touchTargets[pid] = target
                    dispatchMotionEventToTarget(event, MotionEvent.ACTION_DOWN, i, target)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until event.pointerCount) {
                        val pid = event.getPointerId(i)
                        val target = touchTargets[pid] ?: continue
                        dispatchMotionEventToTarget(event, MotionEvent.ACTION_MOVE, i, target)
                    }
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val i = event.actionIndex
                    val pid = event.getPointerId(i)
                    val target = touchTargets[pid] ?: return true
                    dispatchMotionEventToTarget(event, MotionEvent.ACTION_UP, i, target)
                    touchTargets.remove(pid)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val pid = event.getPointerId(0)
                    val target = touchTargets[pid]
                    if (target == null) {
                        releaseAllTouchTargets()
                        return true
                    }
                    dispatchMotionEventToTarget(event, MotionEvent.ACTION_UP, 0, target)
                    touchTargets.remove(pid)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    releaseAllTouchTargets()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    @CallSuper
    protected open fun onAction(
        action: KeyAction,
        source: KeyActionListener.Source = KeyActionListener.Source.Keyboard
    ) {
        keyActionListener?.onKeyAction(action, source)
    }

    @CallSuper
    protected open fun onPopupAction(action: PopupAction) {
        popupActionListener?.onPopupAction(action)
    }

    private fun onPopupChangeFocus(viewId: Int, x: Float, y: Float): Boolean {
        val changeFocusAction = PopupAction.ChangeFocusAction(viewId, x, y)
        popupActionListener?.onPopupAction(changeFocusAction)
        return changeFocusAction.outResult
    }

    private fun onPopupTrigger(viewId: Int): Boolean {
        val triggerAction = PopupAction.TriggerAction(viewId)
        // ask popup keyboard whether there's a pending KeyAction
        onPopupAction(triggerAction)
        val action = triggerAction.outAction ?: return false
        onAction(action, KeyActionListener.Source.Popup)
        onPopupAction(PopupAction.DismissAction(viewId))
        return true
    }

    open fun onAttach() {
        // do nothing by default
    }

    open fun onReturnDrawableUpdate(@DrawableRes returnDrawable: Int) {
        // do nothing by default
    }

    open fun onPunctuationUpdate(mapping: Map<String, String>) {
        // do nothing by default
    }

    open fun onInputMethodUpdate(ime: InputMethodEntry) {
        findViewById<TextKeyView>(org.fcitx.fcitx5.android.R.id.button_space)?.mainText?.text =
            if (this is NumberKeyboard) {
                "空格"
            } else if (ime.languageCode.startsWith("ja")) {
                "日本語"
            } else if (ime.languageCode.startsWith("es")) {
                "Español"
            } else if (this !is T9Keyboard && (ime.uniqueName == KeyboardSelectionPreset.ENGLISH ||
                    ime.languageCode.startsWith("en", ignoreCase = true))) {
                "English"
            } else {
                "拼音"
            }
    }

    open fun onDetach() {
        releaseAllTouchTargets()
    }

}
