/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

import org.fcitx.fcitx5.android.data.theme.SkinLabStandard
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.SubtypeManager
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.bar.ToolbarActionHostBridge
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.clipboard.ClipboardWindow
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.editorinfo.EditorInfoWindow
import org.fcitx.fcitx5.android.input.dependency.inputView
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubHostBridge
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubLaunchRequest
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.Clipboard
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.CustomizeToolbar
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.Emoji
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.AiReply
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.InputMethod
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.KeyboardAdjust
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.KeyboardSelection
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.MaterialHub
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.ReloadConfig
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.KeyEffects
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.InputSettings
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.Skin
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.TraditionalInput
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.VoiceInput
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.KeyboardSelection.Target
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.AppUtil
import org.fcitx.fcitx5.android.utils.DeviceUtil
import org.fcitx.fcitx5.android.utils.alpha
import org.fcitx.fcitx5.android.utils.toast
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp
import splitties.resources.styledColor
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams

class StatusAreaWindow(
    private val startInKeyboardSelectionPage: Boolean = false
) : InputWindow.ExtendedInputWindow<StatusAreaWindow>(),
    InputBroadcastReceiver {

    private companion object {
        val visibleFullKeyboardImeSet = setOf(
            KeyboardSelectionPreset.PINYIN,
            KeyboardSelectionPreset.SHUANGPIN,
            KeyboardSelectionPreset.WUBI,
            KeyboardSelectionPreset.WUBI_PINYIN
        )
    }

    private enum class PageMode {
        Root,
        KeyboardSelection
    }

    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val fcitx: FcitxConnection by manager.fcitx()
    private val theme by manager.theme()
    private val inputView by manager.inputView()
    private val windowManager: InputWindowManager by manager.must()

    private val editorInfoInspector by AppPrefs.getInstance().internal.editorInfoInspector
    private val keyBorder by ThemeManager.prefs.keyBorder

    private var popupMenu: PopupMenu? = null
    private var lastActions: Array<Action> = emptyArray()
    private var pageCount = 0
    private var pageMode = PageMode.Root

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            updatePageIndicators(position)
        }
    }

    private fun activateAction(action: Action) {
        fcitx.launchOnReady {
            it.activateAction(action.id)
        }
    }

    private val pageAdapter: StatusAreaPageAdapter by lazy {
        object : StatusAreaPageAdapter() {
            override fun onItemClick(view: View, entry: StatusAreaEntry) {
                handleEntryClick(view, entry)
            }

            override val theme = this@StatusAreaWindow.theme
        }
    }

    private val pageIndicator by lazy {
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
    }

    private val pager by lazy {
        ViewPager2(context).apply {
            adapter = pageAdapter
            offscreenPageLimit = 1
            clipToPadding = false
            setPadding(0, 0, 0, if (SkinLabStandard.enabled(theme)) 0 else context.dp(12))
            registerOnPageChangeCallback(pageChangeCallback)
            (getChildAt(0) as? RecyclerView)?.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        }
    }

    val view by lazy {
        FrameLayout(context).apply {
            setBackgroundColor(if (SkinLabStandard.enabled(theme)) theme.backgroundColor else ToolPanelPalette(theme.backgroundColor, theme.keyTextColor, theme.accentKeyBackgroundColor).surface)
            addView(
                pager,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                pageIndicator,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply {
                    bottomMargin = context.dp(if (SkinLabStandard.enabled(theme)) 13.5f else 2f).toInt()
                }
            )
        }
    }

    private fun handleEntryClick(view: View, entry: StatusAreaEntry) {
        when (entry) {
            is StatusAreaEntry.KeyboardSelection -> handleKeyboardSelectionClick(entry)
            is StatusAreaEntry.Fcitx -> showActionMenuOrActivate(view, entry.action)
            is StatusAreaEntry.Custom -> {
                if (!CustomDrawerHostBridge.launch(entry.drawerId)) {
                    context.toast(
                        context.getString(R.string.status_area_feature_unavailable, entry.label)
                    )
                }
            }
            is StatusAreaEntry.Android -> when (entry.type) {
                VoiceInput -> service.launchLocalVoiceInput()

                CustomizeToolbar -> {
                    if (!ToolbarActionHostBridge.launchCustomizeToolbar()) {
                        context.toast(
                            context.getString(R.string.status_area_feature_coming_soon, entry.label)
                        )
                    }
                }

                KeyboardAdjust -> {
                    windowManager.attachWindow(KeyboardWindow)
                    inputView.keyboardView.post {
                        inputView.beginKeyboardAdjustment()
                    }
                }
                KeyboardSelection -> showKeyboardSelectionPage()
                AiReply -> {
                    service.requestHideSelf(0)
                    if (!ToolbarActionHostBridge.launchAiReply()) {
                        context.toast(
                            context.getString(R.string.status_area_feature_unavailable, entry.label)
                        )
                    }
                }
                MaterialHub -> {
                    service.requestHideSelf(0)
                    if (!ToolbarActionHostBridge.launchMaterialHub()) {
                        context.toast(
                            context.getString(R.string.status_area_feature_unavailable, entry.label)
                        )
                    }
                }
                Emoji -> windowManager.attachWindow(PickerWindow.Key.Emoji)
                Clipboard -> windowManager.attachWindow(ClipboardWindow())
                Skin -> {
                    service.requestHideSelf(0)
                    if (!StatusAreaHostBridge.launchSkinChannel()) {
                        context.toast(
                            context.getString(R.string.status_area_feature_unavailable, entry.label)
                        )
                    }
                }
                KeyEffects, InputSettings -> {
                    val opened = if (entry.type == KeyEffects) StatusAreaHostBridge.launchKeyEffects()
                        else StatusAreaHostBridge.launchInputMethodSettings()
                    if (!opened) context.toast(context.getString(R.string.status_area_feature_unavailable, entry.label))
                }
                TraditionalInput -> context.toast(
                    context.getString(R.string.status_area_feature_unavailable, entry.label)
                )
                ReloadConfig -> reloadConfig()
                InputMethod -> fcitx.runImmediately { inputMethodEntryCached }.let {
                    AppUtil.launchMainToInputMethodConfig(
                        context, it.uniqueName, it.displayName
                    )
                }
            }
        }
    }

    private fun handleKeyboardSelectionClick(entry: StatusAreaEntry.KeyboardSelection) {
        when (entry.target) {
            Target.Pinyin9 -> switchKeyboardSelection(KeyboardSelectionPreset.T9, useT9Layout = true)
            Target.Pinyin26 -> switchKeyboardSelection(KeyboardSelectionPreset.PINYIN, useT9Layout = false)
            Target.English26 -> switchKeyboardSelection(KeyboardSelectionPreset.ENGLISH, useT9Layout = false)
            Target.Shuangpin -> switchKeyboardSelection(KeyboardSelectionPreset.SHUANGPIN, useT9Layout = false)
            Target.Wubi -> switchKeyboardSelection(KeyboardSelectionPreset.WUBI, useT9Layout = false)
            Target.WubiPinyin -> switchKeyboardSelection(KeyboardSelectionPreset.WUBI_PINYIN, useT9Layout = false)
        }
    }

    private fun switchKeyboardSelection(uniqueName: String, useT9Layout: Boolean) {
        fcitx.launchOnReady { f ->
            val available = f.availableIme().toList()
            // #region debug-point F:keyboard-selection-click
            service.reportKeyboardHiddenZhongzhouDebug(
                "F",
                "StatusAreaWindow.switchKeyboardSelection",
                "keyboard selection clicked",
                mapOf(
                    "targetIme" to uniqueName,
                    "targetUseT9Layout" to useT9Layout,
                    "currentImeCached" to f.inputMethodEntryCached.uniqueName,
                    "currentDisplay" to f.inputMethodEntryCached.displayName,
                    "currentSubModeName" to f.inputMethodEntryCached.subMode.name,
                    "currentSubModeLabel" to f.inputMethodEntryCached.subMode.label,
                    "rememberedChineseIme" to AppPrefs.getInstance().internal.lastFullKeyboardChineseIme.getValue(),
                    "prefUseT9KeyboardLayout" to AppPrefs.getInstance().keyboard.useT9KeyboardLayout.getValue()
                )
            )
            // #endregion
            if (available.none { it.uniqueName == uniqueName }) {
                context.toast(
                    context.getString(
                        R.string.status_area_feature_unavailable,
                        keyboardSelectionLabelFor(uniqueName)
                    )
                )
                return@launchOnReady
            }
            val enabledBefore = f.enabledIme().toList()
            val reordered = KeyboardSelectionPreset.reorderEnabledIme(available, enabledBefore)
            if (reordered.map { it.uniqueName } != enabledBefore.map { it.uniqueName }) {
                f.setEnabledIme(reordered.map { it.uniqueName }.toTypedArray())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    SubtypeManager.syncWith(f.enabledIme())
                }
            }
            service.lifecycleScope.launch {
                if (useT9Layout) {
                    service.selectT9Keyboard()
                } else {
                    service.selectFullKeyboardIme(uniqueName)
                }
                windowManager.attachWindow(KeyboardWindow)
            }
        }
    }

    private fun keyboardSelectionLabelFor(uniqueName: String): String = when (uniqueName) {
        KeyboardSelectionPreset.T9 -> context.getString(R.string.status_area_keyboard_selection_pinyin_9)
        KeyboardSelectionPreset.PINYIN -> context.getString(R.string.status_area_keyboard_selection_pinyin_26)
        KeyboardSelectionPreset.ENGLISH -> context.getString(R.string.status_area_keyboard_selection_english_26)
        KeyboardSelectionPreset.SHUANGPIN -> context.getString(R.string.status_area_keyboard_selection_shuangpin)
        KeyboardSelectionPreset.WUBI -> context.getString(R.string.status_area_keyboard_selection_wubi)
        KeyboardSelectionPreset.WUBI_PINYIN ->
            context.getString(R.string.status_area_keyboard_selection_wubi_pinyin)

        else -> uniqueName
    }

    private fun showActionMenuOrActivate(view: View, action: Action) {
        val actions = action.menu
        if (actions.isNullOrEmpty()) {
            activateAction(action)
            return
        }
        val popup = PopupMenu(context, view)
        val menu = popup.menu
        val hasDivider =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !DeviceUtil.isHMOS && !DeviceUtil.isHonorMagicOS) {
                menu.setGroupDividerEnabled(true)
                true
            } else {
                false
            }
        var groupId = 0
        actions.forEach {
            if (it.isSeparator) {
                if (hasDivider) {
                    groupId++
                } else {
                    val dividerString = buildSpannedString {
                        color(context.styledColor(android.R.attr.colorForeground).alpha(0.4f)) {
                            append("──────────")
                        }
                    }
                    menu.add(groupId, 0, 0, dividerString).apply {
                        isEnabled = false
                    }
                }
            } else {
                menu.add(groupId, 0, 0, it.shortText).apply {
                    setOnMenuItemClickListener { _ ->
                        activateAction(it)
                        true
                    }
                }
            }
        }
        popupMenu?.dismiss()
        popupMenu = popup
        popup.show()
    }

    private fun reloadConfig() {
        fcitx.launchOnReady { f ->
            f.reloadConfig()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                SubtypeManager.syncWith(f.enabledIme())
            }
            service.lifecycleScope.launch {
                Toast.makeText(service, R.string.done, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildEntries(actions: Array<Action>): List<StatusAreaEntry> {
        val traditionalEntry = buildTraditionalEntry(actions)
        val paopaoEntries = buildList<StatusAreaEntry> {
            if (org.fcitx.fcitx5.android.input.voice.OemVoiceBridge.enabled(context)) add(
                StatusAreaEntry.Android(
                    context.getString(R.string.status_area_voice_input),
                    R.drawable.ic_oem_toolbar_voice,
                    VoiceInput
                )
            )
            add(
                StatusAreaEntry.Android(
                    context.getString(R.string.status_area_keyboard_selection),
                    R.drawable.ic_oem_toolbar_keyboard_selection,
                    KeyboardSelection
                )
            )
            if (MaterialHubHostBridge.isAvailable(MaterialHubLaunchRequest.Target.AI_REPLY)) {
                add(
                    StatusAreaEntry.Android(
                        context.getString(R.string.status_area_ai_reply),
                        R.drawable.ic_oem_toolbar_ai_reply,
                        AiReply
                    )
                )
            }
            if (MaterialHubHostBridge.isAvailable(MaterialHubLaunchRequest.Target.MATERIAL_HUB)) {
                add(
                    StatusAreaEntry.Android(
                        context.getString(R.string.status_area_material_hub),
                        R.drawable.ic_oem_toolbar_material_hub,
                        MaterialHub
                    )
                )
            }
            add(
                StatusAreaEntry.Android(
                    context.getString(R.string.status_area_emoji),
                    R.drawable.ic_oem_toolbar_emoji,
                    Emoji
                )
            )
            add(
                StatusAreaEntry.Android(
                    context.getString(R.string.clipboard),
                    R.drawable.ic_oem_toolbar_clipboard,
                    Clipboard
                )
            )
            add(
                StatusAreaEntry.Android(
                    context.getString(R.string.status_area_customize_toolbar),
                    R.drawable.ic_oem_toolbar_customize_toolbar,
                    CustomizeToolbar
                )
            )
            add(traditionalEntry)
            add(
                StatusAreaEntry.Android(
                    context.getString(R.string.status_area_keyboard_adjust),
                    R.drawable.ic_oem_toolbar_keyboard_adjust,
                    KeyboardAdjust
                )
            )
            CustomDrawerHostBridge.entries().forEach { item ->
                add(StatusAreaEntry.Custom(item.id, item.title, item.icon))
            }
        }
        val fcitxEntries = actions
            .filterNot(StatusAreaEntry::isHiddenPanelAction)
            .filterNot { it.icon == "fcitx-chttrans-active" || it.icon == "fcitx-chttrans-inactive" }
            .map(StatusAreaEntry::fromAction)
        return paopaoEntries + fcitxEntries
    }

    private fun buildKeyboardSelectionEntries(
        currentImeOverride: InputMethodEntry? = null,
        useT9LayoutOverride: Boolean? = null
    ): List<StatusAreaEntry> {
        val prefs = AppPrefs.getInstance()
        val currentIme = currentImeOverride ?: fcitx.runImmediately { inputMethodEntryCached }
        val useT9Layout = useT9LayoutOverride ?: prefs.keyboard.useT9KeyboardLayout.getValue()
        val rememberedChineseIme = prefs.internal.lastFullKeyboardChineseIme.getValue()
        val activeTarget = service.currentKeyboardSelectionTarget(
            currentImeOverride = currentIme,
            useT9LayoutOverride = useT9Layout
        )
        // #region debug-point G:build-keyboard-selection-entries
        service.reportKeyboardHiddenZhongzhouDebug(
            "G",
            "StatusAreaWindow.buildKeyboardSelectionEntries",
            "keyboard selection page state resolved",
            mapOf(
                "currentIme" to currentIme.uniqueName,
                "currentDisplay" to currentIme.displayName,
                "currentSubModeName" to currentIme.subMode.name,
                "currentSubModeLabel" to currentIme.subMode.label,
                "useT9Layout" to useT9Layout,
                "rememberedChineseIme" to rememberedChineseIme,
                "activeTarget" to activeTarget.name
            )
        )
        // #endregion
        return listOf(
            StatusAreaEntry.KeyboardSelection(
                context.getString(R.string.status_area_keyboard_selection_pinyin_9),
                R.drawable.ic_oem_layout_pinyin9,
                Target.Pinyin9,
                active = activeTarget == Target.Pinyin9
            ),
            StatusAreaEntry.KeyboardSelection(
                context.getString(R.string.status_area_keyboard_selection_pinyin_26),
                R.drawable.ic_oem_layout_pinyin26,
                Target.Pinyin26,
                active = activeTarget == Target.Pinyin26
            ),
            StatusAreaEntry.KeyboardSelection(
                context.getString(R.string.status_area_keyboard_selection_english_26),
                R.drawable.ic_oem_layout_english26,
                Target.English26,
                active = activeTarget == Target.English26
            ),
            StatusAreaEntry.KeyboardSelection(
                context.getString(R.string.status_area_keyboard_selection_shuangpin),
                R.drawable.ic_oem_layout_shuangpin,
                Target.Shuangpin,
                active = activeTarget == Target.Shuangpin
            ),
            StatusAreaEntry.KeyboardSelection(
                context.getString(R.string.status_area_keyboard_selection_wubi),
                R.drawable.ic_oem_layout_wubi,
                Target.Wubi,
                active = activeTarget == Target.Wubi
            ),
            StatusAreaEntry.KeyboardSelection(
                context.getString(R.string.status_area_keyboard_selection_wubi_pinyin),
                R.drawable.ic_oem_layout_wubi_pinyin,
                Target.WubiPinyin,
                active = activeTarget == Target.WubiPinyin
            )
        )
    }

    private fun buildTraditionalEntry(actions: Array<Action>): StatusAreaEntry {
        val action = actions.firstOrNull {
            it.icon == "fcitx-chttrans-active" || it.icon == "fcitx-chttrans-inactive"
        }
        if (action == null) {
            return StatusAreaEntry.Android(
                context.getString(R.string.status_area_traditional_input),
                R.drawable.ic_oem_toolbar_traditional_input,
                TraditionalInput
            )
        }
        val mapped = StatusAreaEntry.fromAction(action)
        return StatusAreaEntry.Fcitx(
            action,
            context.getString(R.string.status_area_traditional_input),
            mapped.icon,
            mapped.active
        )
    }

    private fun updatePageIndicators(selectedPage: Int) {
        val visiblePage = selectedPage.coerceIn(0, pageCount - 1)
        pageIndicator.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        if (pageCount <= 1) {
            pageIndicator.removeAllViews()
            return
        }
        if (pageIndicator.childCount != pageCount) {
            pageIndicator.removeAllViews()
            repeat(pageCount) {
                pageIndicator.addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(context.dp(if (SkinLabStandard.enabled(theme)) 5 else 6), context.dp(if (SkinLabStandard.enabled(theme)) 5 else 6)).apply {
                            marginStart = context.dp(if (SkinLabStandard.enabled(theme)) 4.5f else 4f).toInt()
                            marginEnd = context.dp(if (SkinLabStandard.enabled(theme)) 4.5f else 4f).toInt()
                        }
                    }
                )
            }
        }
        repeat(pageIndicator.childCount) { index ->
            val color =
                if (index == visiblePage) theme.keyTextColor else theme.keyTextColor.alpha(0.22f)
            pageIndicator.getChildAt(index).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
    }

    private fun updateEntryPages(entries: List<StatusAreaEntry>) {
        val pages = entries.chunked(StatusAreaPageAdapter.PAGE_SIZE)
        pageCount = pages.size
        pageAdapter.pages = pages
        if (pager.currentItem >= pageCount) {
            pager.setCurrentItem((pageCount - 1).coerceAtLeast(0), false)
        }
        updatePageIndicators(pager.currentItem)
    }

    private fun updatePages(actions: Array<Action>) {
        lastActions = actions
        when (pageMode) {
            PageMode.Root -> updateEntryPages(buildEntries(actions))
            PageMode.KeyboardSelection -> updateEntryPages(buildKeyboardSelectionEntries())
        }
    }

    private fun showRootPage() {
        pageMode = PageMode.Root
        updatePages(lastActions)
        updateBarExtensionState()
    }

    private fun showKeyboardSelectionPage() {
        pageMode = PageMode.KeyboardSelection
        updateEntryPages(buildKeyboardSelectionEntries())
        pager.setCurrentItem(0, false)
        updateBarExtensionState()
    }

    private fun updateBarExtensionState() {
        if (editorInfoInspector) {
            editorInfoButton.visibility = if (pageMode == PageMode.KeyboardSelection) View.GONE else View.VISIBLE
        }
    }

    override fun onStatusAreaUpdate(actions: Array<Action>) {
        updatePages(actions)
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        if (pageMode == PageMode.KeyboardSelection) {
            updateEntryPages(buildKeyboardSelectionEntries(currentImeOverride = ime))
        }
    }

    override fun onCreateView() = view

    private val editorInfoButton by lazy {
        ToolButton(context, R.drawable.ic_baseline_info_24, theme).apply {
            contentDescription = context.getString(R.string.editor_info_inspector)
            setOnClickListener { windowManager.attachWindow(EditorInfoWindow()) }
        }
    }

    private val settingsButton by lazy {
        ToolButton(context, R.drawable.ic_baseline_settings_24, theme).apply {
            contentDescription = context.getString(R.string.open_input_method_settings)
            setOnClickListener {
                if (!StatusAreaHostBridge.launchInputMethodSettings()) {
                    context.toast(context.getString(R.string.status_area_feature_unavailable, contentDescription))
                }
            }
        }
    }

    private val barExtension by lazy {
        context.horizontalLayout {
            if (editorInfoInspector) {
                add(editorInfoButton, lParams(dp(40), dp(40)))
            }
            add(settingsButton, lParams(dp(40), dp(40)))
        }
    }

    override fun onCreateBarExtension() = barExtension

    override fun onAttached() {
        updateBarExtensionState()
        fcitx.launchOnReady {
            val data = it.statusArea()
            service.lifecycleScope.launch {
                lastActions = data
                if (startInKeyboardSelectionPage) {
                    showKeyboardSelectionPage()
                } else {
                    onStatusAreaUpdate(data)
                }
            }
        }
    }

    init {
        CustomDrawerHostBridge.bindRegistryChanged {
            if (pageMode == PageMode.Root) {
                updatePages(lastActions)
            }
        }
    }

    override fun onDetached() {
        CustomDrawerHostBridge.unbindRegistryChanged()
        popupMenu?.dismiss()
        popupMenu = null
    }
}
