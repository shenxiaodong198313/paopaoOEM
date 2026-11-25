// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.handwriting

import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Toast
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlag
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.*
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.voice.OemVoiceBridge
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must

/** Uses the normal toolbar/candidate strip and the current skin's key rendering. */
class HandwritingWindow : InputWindow.SimpleInputWindow<HandwritingWindow>(), InputBroadcastReceiver {
    private val service by manager.inputMethodService()
    private val theme by manager.theme()
    private val windows: InputWindowManager by manager.must()
    private val bar: KawaiiBarComponent by manager.must()
    private val common: CommonKeyActionListener by manager.must()
    private val returnDrawable: ReturnKeyDrawableComponent by manager.must()
    private lateinit var root: LinearLayout
    private lateinit var session: HandwritingSession
    private lateinit var pad: HandwritingPad
    private lateinit var enter: ImageKeyView
    private var chinesePunctuation = true
    private val punctuation = mutableListOf<TextKeyView>()
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun settings() { service.showDialog(HandwritingSettingsDialog(service, theme)) }
    private fun key(label: String, id: Int = -1, action: () -> Unit) = TextKeyView(context, theme,
        KeyDef.Appearance.Text(label, 20f, variant = KeyDef.Appearance.Variant.Alternative,
            viewId = id, isFunctionLabel = true), SkinKeyAddress("handwriting")).apply {
        contentDescription = label; setOnClickListener { action() }
    }
    private fun imageKey(label: String, icon: Int, id: Int = -1, action: () -> Unit) = ImageKeyView(context, theme,
        KeyDef.Appearance.Image(icon, variant = KeyDef.Appearance.Variant.Alternative, viewId = id),
        SkinKeyAddress("handwriting")).apply { contentDescription = label; setOnClickListener { action() } }
    private fun send(action: KeyAction) {
        session.dispatchKey(action) { common.listener.onKeyAction(action, KeyActionListener.Source.Keyboard) }
    }
    private fun picker(key: PickerWindow.Key) { session.commitFirst(); windows.attachWindow(key) }
    override fun onCreateView(): View {
        root = object : LinearLayout(context) {
            override fun onVisibilityAggregated(visible: Boolean) {
                super.onVisibilityAggregated(visible)
                if (::session.isInitialized) { if (visible) session.start() else session.stop() }
            }
        }.apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), dp(2), dp(3), dp(3)) }
        session = HandwritingSession(service, root, bar)
        session.showMessage = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        val upper = row()
        pad = HandwritingPad(context, session, theme.keyTextColor, ::settings).apply {
            background = GradientDrawable().apply { setColor(theme.keyBackgroundColor); cornerRadius = dp(10).toFloat() }
        }
        session.invalidate = { pad.invalidate() }
        upper.addView(pad, LinearLayout.LayoutParams(0, -1, 5f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        val side = column()
        val backspace = { send(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_BackSpace))) }
        side.addView(imageKey("删除", R.drawable.ic_baseline_backspace_24, R.id.button_backspace, backspace).apply {
            repeatEnabled = true; onRepeatListener = { backspace() }
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        val punctuationGroup = column().apply {
            background = GradientDrawable().apply { setColor(theme.altKeyBackgroundColor); cornerRadius = dp(10).toFloat() }
        }
        listOf("，", "。", "？", "！").forEachIndexed { index, label ->
            val view = TextKeyView(context, theme, KeyDef.Appearance.Text(label, 23f,
                variant = KeyDef.Appearance.Variant.Alternative, border = KeyDef.Appearance.Border.Off,
                margin = false), SkinKeyAddress("handwriting")).apply {
                contentDescription = label
                setOnClickListener { send(KeyAction.CommitAction(punctuationLabels()[index])) }
            }
            punctuation.add(view); punctuationGroup.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        side.addView(punctuationGroup, LinearLayout.LayoutParams(-1, 0, 4f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        upper.addView(side, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(upper, LinearLayout.LayoutParams(-1, 0, 4f))
        val bottom = row()
        fun add(view: View, weight: Float = 1f) { bottom.addView(view, LinearLayout.LayoutParams(0, -1, weight)) }
        add(key("符") { picker(PickerWindow.Key.Symbol) })
        add(key("标点", R.id.button_lang) {
            chinesePunctuation = !chinesePunctuation
            punctuation.forEachIndexed { index, view ->
                view.mainText.text = punctuationLabels()[index]; view.contentDescription = punctuationLabels()[index]
            }
        }.apply { contentDescription = "切换中英文标点；手写仅识别中文" })
        add(imageKey("表情", R.drawable.ic_oem_toolbar_emoji) { picker(PickerWindow.Key.Emoji) })
        val voiceEnabled = OemVoiceBridge.enabled(context)
        val spaceLabel = if (voiceEnabled) "空格，可长按语音输入" else "空格"
        val spaceIcon = if (voiceEnabled) R.drawable.ic_handwriting_space_voice else R.drawable.ic_baseline_space_bar_24
        val space = imageKey(spaceLabel, spaceIcon, R.id.button_space) {
            send(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_space)))
        }
        if (voiceEnabled) {
            space.longPressEnabled = true
            space.setOnLongClickListener {
                session.commitFirst()
                service.launchLocalVoiceInput()
                true
            }
        }
        add(space, 2.5f)
        add(key("123") {
            session.commitFirst()
            (windows.getEssentialWindow(KeyboardWindow) as KeyboardWindow).apply {
                handwritingNumberMode = true; switchLayout(NumberKeyboard.Name)
            }
            windows.attachWindow(KeyboardWindow)
        })
        enter = imageKey("换行", returnDrawable.resourceId, R.id.button_return) {
            send(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_Return)))
        }
        add(enter, 1.3f)
        root.addView(bottom, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }
    private fun punctuationLabels() = if (chinesePunctuation) listOf("，", "。", "？", "！") else listOf(",", ".", "?", "!")
    override fun onAttached() {
        if (!service.canUseHandwriting()) { root.post { if (windows.isAttached(this)) windows.attachWindow(KeyboardWindow) }; return }
        (windows.getEssentialWindow(KeyboardWindow) as KeyboardWindow).handwritingNumberMode = false
        session.start(); enter.updateReturnDrawable(returnDrawable.resourceId) }
    override fun onDetached() { session.stop() }
    override fun onReturnKeyDrawableUpdate(resourceId: Int) { if (::enter.isInitialized) enter.updateReturnDrawable(resourceId) }
    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        if (!::session.isInitialized) return
        session.stop()
        if (capFlags.has(CapabilityFlag.Password) || capFlags.has(CapabilityFlag.Alpha) ||
            info.inputType and InputType.TYPE_MASK_CLASS in setOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE)) {
            root.post { if (windows.isAttached(this)) windows.attachWindow(KeyboardWindow) }
        } else session.start()
    }
}
