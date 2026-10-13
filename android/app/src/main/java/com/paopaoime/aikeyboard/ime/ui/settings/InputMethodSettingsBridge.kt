package com.paopaoime.aikeyboard.ime.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import androidx.preference.PreferenceManager
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.application.CustomConstant
import com.paopaoime.aikeyboard.ime.prefs.AppPrefs as HostAppPrefs
import com.paopaoime.aikeyboard.ime.prefs.behavior.ClipboardLayoutMode
import com.paopaoime.aikeyboard.ime.prefs.behavior.DoublePinyinSchemaMode
import com.paopaoime.aikeyboard.ime.prefs.behavior.HalfWidthSymbolsMode
import com.paopaoime.aikeyboard.ime.prefs.behavior.KeyboardOneHandedMod
import com.paopaoime.aikeyboard.ime.manager.InputModeSwitcherManager
import com.paopaoime.aikeyboard.ime.theme.ImeThemeBridge
import com.paopaoime.aikeyboard.ime.utils.audioManager
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.sdk.core.OemAddons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.data.InputFeedbacks.InputFeedbackMode
import org.fcitx.fcitx5.android.data.clipboard.ClipboardManager
import org.fcitx.fcitx5.android.data.prefs.AppPrefs as FcitxAppPrefs
import org.fcitx.fcitx5.android.data.theme.ThemeManager as FcitxThemeManager
import org.fcitx.fcitx5.android.input.clipboard.ClipboardLayoutMode as FcitxClipboardLayoutMode
import org.fcitx.fcitx5.android.input.keyboard.SpaceLongPressBehavior
import org.fcitx.fcitx5.android.input.status.KeyboardSelectionPreset

object InputMethodSettingsBridge {

    private const val DEFAULT_KEYBOARD_HEIGHT_PERCENT = 30
    private const val DEFAULT_SOUND_VOLUME = 50
    private const val DEFAULT_HAPTIC_LEVEL = 5
    private const val DEFAULT_CLIPBOARD_HISTORY_LIMIT = 10
    private const val DEFAULT_CLIPBOARD_TIMEOUT_SECONDS = 30
    const val MAX_TOOLBAR_ITEMS = 5
    private const val KEY_ASSIST_RETURN_SEND = "bridge_assist_return_send"

    private val DEFAULT_TOOLBAR_VISIBLE_ITEMS = setOf(
        ToolbarItem.Voice,
        ToolbarItem.AiReply,
        ToolbarItem.MaterialHub,
        ToolbarItem.Clipboard,
    )

    private val DEFAULT_TOOLBAR_ORDER = listOf(
        ToolbarItem.Voice,
        ToolbarItem.AiReply,
        ToolbarItem.MaterialHub,
        ToolbarItem.Clipboard,
    )

    private val UPGRADE_DEFAULT_TOOLBAR_VISIBLE_ITEMS = setOf(
        ToolbarItem.KeyboardSelection,
        ToolbarItem.AiReply,
        ToolbarItem.MaterialHub,
        ToolbarItem.Emoji,
        ToolbarItem.Clipboard,
    )

    private val UPGRADE_DEFAULT_TOOLBAR_ORDER = listOf(
        ToolbarItem.KeyboardSelection,
        ToolbarItem.AiReply,
        ToolbarItem.MaterialHub,
        ToolbarItem.Emoji,
        ToolbarItem.Clipboard,
    )

    private val LEGACY_DEFAULT_TOOLBAR_VISIBLE_ITEMS = setOf(
        ToolbarItem.KeyboardSelection,
        ToolbarItem.Emoji,
        ToolbarItem.Clipboard,
    )

    private val LEGACY_DEFAULT_TOOLBAR_ORDER = listOf(
        ToolbarItem.KeyboardSelection,
        ToolbarItem.Emoji,
        ToolbarItem.Clipboard,
    )

    enum class DarkModeOption {
        FollowSystem,
        Light,
        Dark,
    }

    enum class LayoutSidebarMode {
        Pinyin,
        Number,
    }

    enum class KeyboardMode {
        FullKeyboard,
        NineGrid,
    }

    enum class MainKeyboardType {
        NineGridPinyin,
        FullPinyin,
        English26,
        DoublePinyin,
        Wubi,
        WubiPinyin,
    }

    enum class OneHandMode {
        Off,
        Left,
        Right,
    }

    data class CustomToolbarEntry(
        val id: String,
        val title: String,
        val icon: android.graphics.drawable.Drawable?,
    )

    sealed class ToolbarDisplayItem {
        data class Builtin(val item: ToolbarItem) : ToolbarDisplayItem()
        data class Custom(val id: String, val title: String, val icon: android.graphics.drawable.Drawable?) :
            ToolbarDisplayItem()

        fun prefValue(): String = when (this) {
            is Builtin -> item.prefValue
            is Custom -> CUSTOM_TOOLBAR_PREF_PREFIX + id
        }

        fun label(context: Context): String = when (this) {
            is Builtin -> context.getString(item.labelRes)
            is Custom -> title
        }
    }

    data class ToolbarDisplaySettings(
        val visibleItems: List<ToolbarDisplayItem>,
        val orderedItems: List<ToolbarDisplayItem>,
    )

    const val CUSTOM_TOOLBAR_PREF_PREFIX = "custom_"

    @Volatile
    private var customToolbarEntriesProvider: (() -> List<CustomToolbarEntry>)? = null

    fun bindCustomToolbarEntriesProvider(provider: () -> List<CustomToolbarEntry>) {
        customToolbarEntriesProvider = provider
    }

    fun customToolbarEntries(): List<CustomToolbarEntry> =
        customToolbarEntriesProvider?.invoke().orEmpty()

    enum class ToolbarItem(val prefValue: String, val labelRes: Int) {
        Voice("voice", R.string.customize_toolbar_item_voice),
        KeyboardSelection("keyboard_selection", R.string.customize_toolbar_item_keyboard_selection),
        AiReply("ai_reply", R.string.customize_toolbar_item_ai_reply),
        MaterialHub("material_hub", R.string.customize_toolbar_item_material_hub),
        Emoji("emoji", R.string.customize_toolbar_item_emoji),
        Clipboard("clipboard", R.string.customize_toolbar_item_clipboard),
        CustomizeToolbar("customize_toolbar", R.string.customize_toolbar_item_customize_toolbar),
        Skin("skin", R.string.customize_toolbar_item_skin),
        TraditionalInput("traditional_input", R.string.customize_toolbar_item_traditional_input),
        KeyboardAdjust("keyboard_adjust", R.string.customize_toolbar_item_keyboard_adjust);

        companion object {
            fun fromPrefValue(value: String): ToolbarItem? = entries.firstOrNull { it.prefValue == value }
        }
    }

    data class ToolbarSettings(
        val expandByDefault: Boolean,
        val hideComplexEntries: Boolean,
        val visibleItems: Set<ToolbarItem>,
        val orderedItems: List<ToolbarItem>,
    )

    private val allToolbarItems = ToolbarItem.entries.toList()
    private val oemDisabledToolbarItems = setOf(ToolbarItem.Skin)

    private const val KEY_LAYOUT_SIDEBAR_MODE = "bridge_layout_sidebar_mode"
    private const val KEY_TOOLBAR_HIDE_COMPLEX = "bridge_toolbar_hide_complex"
    private const val KEY_TOOLBAR_VISIBLE_ITEMS = "bridge_toolbar_visible_items"
    private const val KEY_TOOLBAR_ORDER = "bridge_toolbar_order"
    private const val KEY_TOOLBAR_AI_MATERIAL_MIGRATED = "bridge_toolbar_ai_material_migrated"
    private const val KEY_HANDWRITING_ENABLED = "bridge_handwriting_enabled"

    private val hapticPresets = listOf(
        0 to 0,
        0 to 0,
        10 to 80,
        16 to 120,
        22 to 160,
        28 to 210,
        36 to 255,
    )

    private fun sharedPrefs(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    }

    private fun hostPrefs(): HostAppPrefs = HostAppPrefs.getInstance()

    private fun fcitxPrefs(): FcitxAppPrefs = FcitxAppPrefs.getInstance()

    fun getLayoutNumberRowEnabled(): Boolean = hostPrefs().keyboardSetting.abcNumberLine.getValue()

    fun setLayoutNumberRowEnabled(enabled: Boolean) {
        hostPrefs().keyboardSetting.abcNumberLine.setValue(enabled)
    }

    fun getLayoutSidebarMode(context: Context): LayoutSidebarMode {
        val raw = sharedPrefs(context).getString(KEY_LAYOUT_SIDEBAR_MODE, LayoutSidebarMode.Pinyin.name)
        return runCatching { LayoutSidebarMode.valueOf(raw ?: LayoutSidebarMode.Pinyin.name) }
            .getOrDefault(LayoutSidebarMode.Pinyin)
    }

    fun setLayoutSidebarMode(context: Context, mode: LayoutSidebarMode) {
        sharedPrefs(context).edit().putString(KEY_LAYOUT_SIDEBAR_MODE, mode.name).apply()
    }

    fun getKeyboardHeightPercent(): Int {
        val prefs = fcitxPrefs().keyboard
        return if (prefs.useT9KeyboardLayout.getValue()) {
            prefs.t9KeyboardHeightPercent.getValue()
        } else {
            prefs.keyboardHeightPercent.getValue()
        }
    }

    fun setKeyboardHeightPercent(percent: Int) {
        val clampedFull = percent.coerceIn(10, 90)
        val clampedT9 = percent.coerceIn(5, 50)
        fcitxPrefs().keyboard.keyboardHeightPercent.setValue(clampedFull)
        fcitxPrefs().keyboard.keyboardHeightPercentLandscape.setValue(clampedFull.coerceIn(10, 90))
        fcitxPrefs().keyboard.t9KeyboardHeightPercent.setValue(clampedT9)
        fcitxPrefs().keyboard.t9KeyboardHeightPercentLandscape.setValue(clampedT9.coerceIn(5, 50))
    }

    fun getDarkModeOption(): DarkModeOption {
        if (ImeThemeBridge.isFollowSystemDayNightThemeEnabled()) {
            return DarkModeOption.FollowSystem
        }
        return if (FcitxThemeManager.activeTheme.isDark) {
            DarkModeOption.Dark
        } else {
            DarkModeOption.Light
        }
    }

    fun setDarkModeOption(option: DarkModeOption) {
        when (option) {
            DarkModeOption.FollowSystem -> ImeThemeBridge.setFollowSystemDayNightThemeEnabled(true)
            DarkModeOption.Light -> {
                ImeThemeBridge.setFollowSystemDayNightThemeEnabled(false)
                ImeThemeBridge.applyBuiltinTheme(ImeThemeBridge.BuiltinThemeChoice.Light)
            }
            DarkModeOption.Dark -> {
                ImeThemeBridge.setFollowSystemDayNightThemeEnabled(false)
                ImeThemeBridge.applyBuiltinTheme(ImeThemeBridge.BuiltinThemeChoice.Dark)
            }
        }
    }

    fun isSoundEnabled(): Boolean {
        return fcitxPrefs().keyboard.soundOnKeyPress.getValue() != InputFeedbackMode.Disabled
    }

    fun normalizeSoundSettingsForUi() {
        val prefs = fcitxPrefs().keyboard
        val mode = prefs.soundOnKeyPress.getValue()
        val volume = prefs.soundOnKeyPressVolume.getValue()
        // Opening settings must not re-enable sound that the user explicitly disabled.
        if (volume <= 0 && mode != InputFeedbackMode.Disabled) {
            prefs.soundOnKeyPress.setValue(InputFeedbackMode.Disabled)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        val pref = fcitxPrefs().keyboard.soundOnKeyPress
        pref.setValue(
            if (enabled) InputFeedbackMode.Enabled else InputFeedbackMode.Disabled
        )
    }

    fun getSoundVolume(): Int = if (isSoundEnabled()) fcitxPrefs().keyboard.soundOnKeyPressVolume.getValue() else 0

    fun setSoundVolume(volume: Int) {
        fcitxPrefs().keyboard.soundOnKeyPressVolume.setValue(volume.coerceIn(0, 100))
    }

    fun previewSoundEffect(context: Context) {
        val mode = fcitxPrefs().keyboard.soundOnKeyPress.getValue()
        if (mode == InputFeedbackMode.Disabled) return
        val volume = getSoundVolume()
        if (volume <= 0) return
        org.fcitx.fcitx5.android.data.KeySounds.play(context, volume = volume / 100f)
    }

    fun isHapticEnabled(): Boolean {
        return fcitxPrefs().keyboard.hapticOnKeyPress.getValue() != InputFeedbackMode.Disabled
    }

    fun setHapticEnabled(enabled: Boolean) {
        val pref = fcitxPrefs().keyboard.hapticOnKeyPress
        val current = pref.getValue()
        pref.setValue(
            when {
                !enabled -> InputFeedbackMode.Disabled
                current == InputFeedbackMode.Disabled -> InputFeedbackMode.FollowingSystem
                else -> current
            }
        )
    }

    fun getHapticLevel(): Int {
        val prefs = fcitxPrefs().keyboard
        val mode = prefs.hapticOnKeyPress.getValue()
        if (mode == InputFeedbackMode.Disabled) return 0
        if (mode == InputFeedbackMode.FollowingSystem) return 1
        val currentDuration = prefs.buttonPressVibrationMilliseconds.getValue()
        val currentAmplitude = prefs.buttonPressVibrationAmplitude.getValue()
        val bestIndex = hapticPresets
            .drop(2)
            .mapIndexed { index, preset ->
                val durationDiff = kotlin.math.abs(currentDuration - preset.first)
                val amplitudeDiff = kotlin.math.abs(currentAmplitude - preset.second)
                index + 2 to (durationDiff * 2 + amplitudeDiff)
            }
            .minByOrNull { it.second }
            ?.first
        return bestIndex ?: 4
    }

    fun setHapticLevel(level: Int) {
        val prefs = fcitxPrefs().keyboard
        when (level.coerceIn(0, hapticPresets.lastIndex)) {
            0 -> prefs.hapticOnKeyPress.setValue(InputFeedbackMode.Disabled)
            1 -> {
                prefs.hapticOnKeyPress.setValue(InputFeedbackMode.FollowingSystem)
                prefs.buttonPressVibrationMilliseconds.setValue(0)
                prefs.buttonLongPressVibrationMilliseconds.setValue(0)
                prefs.buttonPressVibrationAmplitude.setValue(0)
                prefs.buttonLongPressVibrationAmplitude.setValue(0)
            }
            else -> {
                val (duration, amplitude) = hapticPresets[level]
                prefs.hapticOnKeyPress.setValue(InputFeedbackMode.Enabled)
                prefs.buttonPressVibrationMilliseconds.setValue(duration)
                prefs.buttonLongPressVibrationMilliseconds.setValue(duration)
                prefs.buttonPressVibrationAmplitude.setValue(amplitude)
                prefs.buttonLongPressVibrationAmplitude.setValue(amplitude)
            }
        }
    }

    fun isPopupBubbleEnabled(): Boolean = fcitxPrefs().keyboard.popupOnKeyPress.getValue()

    fun setPopupBubbleEnabled(enabled: Boolean) {
        fcitxPrefs().keyboard.popupOnKeyPress.setValue(enabled)
        hostPrefs().keyboardSetting.keyboardBalloonShow.setValue(enabled)
    }

    fun getClipboardHistoryEnabled(): Boolean = fcitxPrefs().clipboard.clipboardListening.getValue()

    fun setClipboardHistoryEnabled(enabled: Boolean) {
        fcitxPrefs().clipboard.clipboardListening.setValue(enabled)
        hostPrefs().clipboard.clipboardListening.setValue(enabled)
    }

    fun getClipboardHistoryLimit(): Int = fcitxPrefs().clipboard.clipboardHistoryLimit.getValue()

    fun setClipboardHistoryLimit(limit: Int) {
        val clamped = limit.coerceIn(10, 110)
        fcitxPrefs().clipboard.clipboardHistoryLimit.setValue(clamped)
        hostPrefs().clipboard.clipboardHistoryLimit.setValue(clamped)
    }

    // Keep legacy bridge signatures for host compatibility. Toolbar suggestions are removed.
    @Deprecated("Clipboard text is only shown in the explicit clipboard panel")
    fun getClipboardSuggestionEnabled(): Boolean = false

    @Deprecated("Clipboard toolbar suggestions are no longer supported")
    @Suppress("UNUSED_PARAMETER")
    fun setClipboardSuggestionEnabled(enabled: Boolean) = Unit

    @Deprecated("Clipboard toolbar suggestions are no longer supported")
    fun getClipboardTimeoutSeconds(): Int = 0

    @Deprecated("Clipboard toolbar suggestions are no longer supported")
    @Suppress("UNUSED_PARAMETER")
    fun setClipboardTimeoutSeconds(seconds: Int) = Unit

    fun getClipboardLayoutMode(): ClipboardLayoutMode = hostPrefs().clipboard.clipboardLayoutCompact.getValue()

    fun setClipboardLayoutMode(mode: ClipboardLayoutMode) {
        hostPrefs().clipboard.clipboardLayoutCompact.setValue(mode)
        fcitxPrefs().clipboard.clipboardLayoutMode.setValue(
            FcitxClipboardLayoutMode.valueOf(mode.name)
        )
    }

    suspend fun clearClipboardHistory(): Int = withContext(Dispatchers.IO) {
        ClipboardManager.deleteAll(skipPinned = false).size
    }

    fun getKeyboardMode(): KeyboardMode {
        return if (fcitxPrefs().keyboard.useT9KeyboardLayout.getValue()) {
            KeyboardMode.NineGrid
        } else {
            KeyboardMode.FullKeyboard
        }
    }

    fun setKeyboardMode(mode: KeyboardMode) {
        fcitxPrefs().keyboard.useT9KeyboardLayout.setValue(mode == KeyboardMode.NineGrid)
    }

    fun isHandwritingEnabled(context: Context): Boolean {
        return sharedPrefs(context).getBoolean(KEY_HANDWRITING_ENABLED, true)
    }

    fun setHandwritingEnabled(context: Context, enabled: Boolean) {
        sharedPrefs(context).edit().putBoolean(KEY_HANDWRITING_ENABLED, enabled).apply()
        if (!enabled) {
            val host = hostPrefs().internal
            if (host.pinyinModeRime.getValue() == CustomConstant.SCHEMA_ZH_HANDWRITING) {
                host.pinyinModeRime.setValue(CustomConstant.SCHEMA_ZH_QWERTY)
            }
            if (host.inputMethodPinyinMode.getValue() == InputModeSwitcherManager.MODE_HANDWRITING_CHINESE) {
                host.inputMethodPinyinMode.setValue(InputModeSwitcherManager.MODE_QWERTY_CHINESE)
            }
            if (host.inputDefaultMode.getValue() == InputModeSwitcherManager.MODE_HANDWRITING_CHINESE) {
                host.inputDefaultMode.setValue(InputModeSwitcherManager.MODE_QWERTY_CHINESE)
            }
        }
    }

    fun getPrimaryKeyboardType(): MainKeyboardType {
        if (hostPrefs().keyboardSetting.keyboardLockEnglish.getValue()) {
            return MainKeyboardType.English26
        }
        if (fcitxPrefs().keyboard.useT9KeyboardLayout.getValue()) {
            return MainKeyboardType.NineGridPinyin
        }
        val rememberedIme = fcitxPrefs().internal.lastFullKeyboardChineseIme.getValue()
        return when {
            rememberedIme == KeyboardSelectionPreset.SHUANGPIN -> MainKeyboardType.DoublePinyin
            rememberedIme == KeyboardSelectionPreset.WUBI -> MainKeyboardType.Wubi
            rememberedIme == KeyboardSelectionPreset.WUBI_PINYIN -> MainKeyboardType.WubiPinyin
            else -> MainKeyboardType.FullPinyin
        }
    }

    fun setPrimaryKeyboardType(type: MainKeyboardType) {
        val host = hostPrefs()
        val fcitx = fcitxPrefs()
        when (type) {
            MainKeyboardType.NineGridPinyin -> {
                host.keyboardSetting.keyboardLockEnglish.setValue(false)
                host.internal.pinyinModeRime.setValue(CustomConstant.SCHEMA_ZH_T9)
                host.internal.inputMethodPinyinMode.setValue(InputModeSwitcherManager.MODE_T9_CHINESE)
                host.internal.inputDefaultMode.setValue(InputModeSwitcherManager.MODE_T9_CHINESE)
                fcitx.keyboard.useT9KeyboardLayout.setValue(true)
            }
            MainKeyboardType.FullPinyin -> {
                host.keyboardSetting.keyboardLockEnglish.setValue(false)
                fcitx.internal.lastFullKeyboardChineseIme.setValue(KeyboardSelectionPreset.PINYIN)
                host.internal.pinyinModeRime.setValue(CustomConstant.SCHEMA_ZH_QWERTY)
                host.internal.inputMethodPinyinMode.setValue(InputModeSwitcherManager.MODE_QWERTY_CHINESE)
                host.internal.inputDefaultMode.setValue(InputModeSwitcherManager.MODE_QWERTY_CHINESE)
                fcitx.keyboard.useT9KeyboardLayout.setValue(false)
            }
            MainKeyboardType.English26 -> {
                host.keyboardSetting.keyboardLockEnglish.setValue(true)
                host.internal.inputDefaultMode.setValue(InputModeSwitcherManager.MODE_ENGLISH_LOWER)
                fcitx.keyboard.useT9KeyboardLayout.setValue(false)
            }
            MainKeyboardType.DoublePinyin -> {
                host.keyboardSetting.keyboardLockEnglish.setValue(false)
                fcitx.internal.lastFullKeyboardChineseIme.setValue(KeyboardSelectionPreset.SHUANGPIN)
                host.internal.pinyinModeRime.setValue(
                    CustomConstant.SCHEMA_ZH_DOUBLE_FLYPY + getDoublePinyinSchema()
                )
                host.internal.inputMethodPinyinMode.setValue(InputModeSwitcherManager.MODE_QWERTY_CHINESE)
                host.internal.inputDefaultMode.setValue(InputModeSwitcherManager.MODE_QWERTY_CHINESE)
                fcitx.keyboard.useT9KeyboardLayout.setValue(false)
            }
            MainKeyboardType.Wubi -> {
                host.keyboardSetting.keyboardLockEnglish.setValue(false)
                fcitx.internal.lastFullKeyboardChineseIme.setValue(KeyboardSelectionPreset.WUBI)
                host.internal.inputMethodPinyinMode.setValue(InputModeSwitcherManager.MODE_QWERTY_CHINESE)
                host.internal.inputDefaultMode.setValue(InputModeSwitcherManager.MODE_QWERTY_CHINESE)
                fcitx.keyboard.useT9KeyboardLayout.setValue(false)
            }
            MainKeyboardType.WubiPinyin -> {
                host.keyboardSetting.keyboardLockEnglish.setValue(false)
                fcitx.internal.lastFullKeyboardChineseIme.setValue(KeyboardSelectionPreset.WUBI_PINYIN)
                host.internal.inputMethodPinyinMode.setValue(InputModeSwitcherManager.MODE_QWERTY_CHINESE)
                host.internal.inputDefaultMode.setValue(InputModeSwitcherManager.MODE_QWERTY_CHINESE)
                fcitx.keyboard.useT9KeyboardLayout.setValue(false)
            }
        }
    }

    fun getDoublePinyinSchema(): DoublePinyinSchemaMode = hostPrefs().input.doublePYSchemaMode.getValue()

    fun setDoublePinyinSchema(mode: DoublePinyinSchemaMode) {
        hostPrefs().input.doublePYSchemaMode.setValue(mode)
        val host = hostPrefs().internal
        val currentSchema = host.pinyinModeRime.getValue()
        if (currentSchema.startsWith(CustomConstant.SCHEMA_ZH_DOUBLE_FLYPY) &&
            currentSchema != CustomConstant.SCHEMA_ZH_DOUBLE_LX17
        ) {
            host.pinyinModeRime.setValue(CustomConstant.SCHEMA_ZH_DOUBLE_FLYPY + mode)
        }
    }

    fun isDoublePinyinHintEnabled(): Boolean = hostPrefs().keyboardSetting.keyboardMnemonic.getValue()

    fun setDoublePinyinHintEnabled(enabled: Boolean) {
        hostPrefs().keyboardSetting.keyboardMnemonic.setValue(enabled)
    }

    fun isSpaceSwipeMoveCursorEnabled(): Boolean = fcitxPrefs().keyboard.spaceSwipeMoveCursor.getValue()

    fun setSpaceSwipeMoveCursorEnabled(enabled: Boolean) {
        fcitxPrefs().keyboard.spaceSwipeMoveCursor.setValue(enabled)
        hostPrefs().keyboardSetting.spaceSwipeMoveCursor.setValue(enabled)
    }

    fun getOneHandMode(): OneHandMode {
        val padding = fcitxPrefs().keyboard.keyboardSidePadding.getValue()
        val offset = fcitxPrefs().keyboard.keyboardHorizontalOffset.getValue()
        if (padding == 0 && offset == 0) return OneHandMode.Off
        return if (offset < 0) OneHandMode.Left else OneHandMode.Right
    }

    fun setOneHandMode(mode: OneHandMode) {
        val host = hostPrefs().keyboardSetting
        val fcitx = fcitxPrefs().keyboard
        when (mode) {
            OneHandMode.Off -> {
                host.oneHandedModSwitch.setValue(false)
                fcitx.keyboardSidePadding.setValue(0)
                fcitx.keyboardSidePaddingLandscape.setValue(0)
                fcitx.keyboardHorizontalOffset.setValue(0)
                fcitx.keyboardHorizontalOffsetLandscape.setValue(0)
            }
            OneHandMode.Left -> {
                host.oneHandedModSwitch.setValue(true)
                host.oneHandedMod.setValue(KeyboardOneHandedMod.LEFT)
                fcitx.keyboardSidePadding.setValue(12)
                fcitx.keyboardSidePaddingLandscape.setValue(12)
                fcitx.keyboardHorizontalOffset.setValue(-12)
                fcitx.keyboardHorizontalOffsetLandscape.setValue(-12)
            }
            OneHandMode.Right -> {
                host.oneHandedModSwitch.setValue(true)
                host.oneHandedMod.setValue(KeyboardOneHandedMod.RIGHT)
                fcitx.keyboardSidePadding.setValue(12)
                fcitx.keyboardSidePaddingLandscape.setValue(12)
                fcitx.keyboardHorizontalOffset.setValue(12)
                fcitx.keyboardHorizontalOffsetLandscape.setValue(12)
            }
        }
    }

    fun isAssistReturnSendEnabled(context: Context): Boolean {
        return sharedPrefs(context).getBoolean(KEY_ASSIST_RETURN_SEND, false)
    }

    fun setAssistReturnSendEnabled(context: Context, enabled: Boolean) {
        sharedPrefs(context).edit().putBoolean(KEY_ASSIST_RETURN_SEND, enabled).apply()
    }

    fun isAssistAutoSpaceEnabled(): Boolean = hostPrefs().input.abcSpaceAuto.getValue()

    fun setAssistAutoSpaceEnabled(enabled: Boolean) {
        hostPrefs().input.abcSpaceAuto.setValue(enabled)
    }

    fun isAssistPairSymbolEnabled(): Boolean = hostPrefs().input.symbolPairInput.getValue()

    fun setAssistPairSymbolEnabled(enabled: Boolean) {
        hostPrefs().input.symbolPairInput.setValue(enabled)
    }

    fun isAssistChineseAssociationEnabled(): Boolean = hostPrefs().input.chinesePrediction.getValue()

    fun setAssistChineseAssociationEnabled(enabled: Boolean) {
        hostPrefs().input.chinesePrediction.setValue(enabled)
    }

    fun isAssistDateAssociationEnabled(): Boolean = hostPrefs().input.chinesePredictionDate.getValue()

    fun setAssistDateAssociationEnabled(enabled: Boolean) {
        hostPrefs().input.chinesePredictionDate.setValue(enabled)
    }

    fun isAssistEmojiAssociationEnabled(): Boolean = hostPrefs().input.emojiInput.getValue()

    fun setAssistEmojiAssociationEnabled(enabled: Boolean) {
        hostPrefs().input.emojiInput.setValue(enabled)
    }

    fun getHalfWidthSymbolsMode(): HalfWidthSymbolsMode = hostPrefs().keyboardSetting.halfWidthSymbolsMode.getValue()

    fun setHalfWidthSymbolsMode(mode: HalfWidthSymbolsMode) {
        hostPrefs().keyboardSetting.halfWidthSymbolsMode.setValue(mode)
    }

    fun getToolbarSettings(context: Context): ToolbarSettings {
        val prefs = sharedPrefs(context)
        migrateToolbarDefaultsIfNeeded(prefs)
        if (!prefs.getBoolean("toolbar_voice_migrated", false)) {
            val old = setOf("ai_reply", "material_hub", "clipboard")
            val saved = prefs.getStringSet(KEY_TOOLBAR_VISIBLE_ITEMS, null)
            val order = prefs.getString(KEY_TOOLBAR_ORDER, null)
            prefs.edit().apply {
                if (saved == old && order == "ai_reply,material_hub,clipboard") {
                    putStringSet(KEY_TOOLBAR_VISIBLE_ITEMS, old + "voice")
                    putString(KEY_TOOLBAR_ORDER, "voice,$order")
                }
                putBoolean("toolbar_voice_migrated", true)
            }.apply()
        }
        val visibleItems = prefs.getStringSet(
            KEY_TOOLBAR_VISIBLE_ITEMS,
            DEFAULT_TOOLBAR_VISIBLE_ITEMS.map { it.prefValue }.toSet()
        ).orEmpty()
            .mapNotNull(ToolbarItem::fromPrefValue)
            .filter { it in allToolbarItems }
            .take(MAX_TOOLBAR_ITEMS)
            .toSet()

        val orderedItems = prefs.getString(
            KEY_TOOLBAR_ORDER,
            DEFAULT_TOOLBAR_ORDER.joinToString(",") { it.prefValue }
        ).orEmpty()
            .split(',')
            .mapNotNull { ToolbarItem.fromPrefValue(it) }
            .filter { it in allToolbarItems }
            .ifEmpty {
                DEFAULT_TOOLBAR_ORDER
            }

        return ToolbarSettings(
            expandByDefault = fcitxPrefs().keyboard.expandToolbarByDefault.getValue(),
            hideComplexEntries = prefs.getBoolean(KEY_TOOLBAR_HIDE_COMPLEX, true),
            visibleItems = sanitizeToolbarItems(context, visibleItems).toSet(),
            orderedItems = sanitizeToolbarItems(context, orderedItems),
        )
    }

    private fun migrateToolbarDefaultsIfNeeded(prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_TOOLBAR_AI_MATERIAL_MIGRATED, false)) {
            return
        }
        val visiblePref = prefs.getStringSet(KEY_TOOLBAR_VISIBLE_ITEMS, null)
        val orderPref = prefs.getString(KEY_TOOLBAR_ORDER, null)
        if (visiblePref == null || orderPref == null) {
            prefs.edit()
                .putBoolean(KEY_TOOLBAR_AI_MATERIAL_MIGRATED, true)
                .apply()
            return
        }
        val visibleItems = visiblePref.mapNotNull(ToolbarItem::fromPrefValue).toSet()
        val orderedItems = orderPref.split(',').mapNotNull(ToolbarItem::fromPrefValue)
        val shouldUpgradeVisible = visibleItems == LEGACY_DEFAULT_TOOLBAR_VISIBLE_ITEMS
        val shouldUpgradeOrder = orderedItems == LEGACY_DEFAULT_TOOLBAR_ORDER
        val editor = prefs.edit()
        if (shouldUpgradeVisible) {
            editor.putStringSet(
                KEY_TOOLBAR_VISIBLE_ITEMS,
                UPGRADE_DEFAULT_TOOLBAR_VISIBLE_ITEMS.map { it.prefValue }.toSet()
            )
        }
        if (shouldUpgradeOrder) {
            editor.putString(
                KEY_TOOLBAR_ORDER,
                UPGRADE_DEFAULT_TOOLBAR_ORDER.joinToString(",") { it.prefValue }
            )
        }
        editor.putBoolean(KEY_TOOLBAR_AI_MATERIAL_MIGRATED, true).apply()
    }

    fun setToolbarExpandByDefault(enabled: Boolean) {
        fcitxPrefs().keyboard.expandToolbarByDefault.setValue(enabled)
    }

    fun setToolbarHideComplex(context: Context, enabled: Boolean) {
        sharedPrefs(context).edit().putBoolean(KEY_TOOLBAR_HIDE_COMPLEX, enabled).apply()
    }

    fun setToolbarVisibleItems(context: Context, items: Set<ToolbarItem>) {
        val sanitized = sanitizeToolbarItems(context, items).take(MAX_TOOLBAR_ITEMS).toSet()
        sharedPrefs(context).edit()
            .putStringSet(KEY_TOOLBAR_VISIBLE_ITEMS, sanitized.map { it.prefValue }.toSet())
            .apply()
    }

    fun setToolbarOrder(context: Context, items: List<ToolbarItem>) {
        val currentVisible = getToolbarSettings(context).visibleItems
        val sanitized = items
            .filter { it in allToolbarItems && it in currentVisible }
            .filterNot { it in unavailableToolbarItems(context) }
            .distinct()
            .take(MAX_TOOLBAR_ITEMS)
        sharedPrefs(context).edit()
            .putString(KEY_TOOLBAR_ORDER, sanitized.joinToString(",") { it.prefValue })
            .apply()
    }

    fun buildToolbarOrderSummary(context: Context): String {
        return resolveToolbarItemsForDisplay(context).joinToString(" / ") {
            context.getString(it.labelRes)
        }
    }

    fun resolveToolbarItemsForDisplay(context: Context): List<ToolbarItem> {
        val settings = getToolbarSettings(context)
        val orderedSelected = settings.orderedItems.filter { it in settings.visibleItems }
        val remaining = allToolbarItems.filter { it in settings.visibleItems && it !in orderedSelected }
        return sanitizeToolbarItems(context, orderedSelected + remaining).take(MAX_TOOLBAR_ITEMS)
    }

    fun availableToolbarItemsForOem(context: Context): List<ToolbarItem> {
        return sanitizeToolbarItems(context, allToolbarItems)
    }

    fun availableDisplayItems(context: Context): List<ToolbarDisplayItem> {
        return sanitizeToolbarItems(context, allToolbarItems).map { ToolbarDisplayItem.Builtin(it) } +
            customToolbarEntries().map { ToolbarDisplayItem.Custom(it.id, it.title, it.icon) }
    }

    fun getToolbarDisplaySettings(context: Context): ToolbarDisplaySettings {
        val prefs = sharedPrefs(context)
        migrateToolbarDefaultsIfNeeded(prefs)
        if (!prefs.getBoolean("toolbar_voice_migrated", false)) {
            val old = setOf("ai_reply", "material_hub", "clipboard")
            val saved = prefs.getStringSet(KEY_TOOLBAR_VISIBLE_ITEMS, null)
            val order = prefs.getString(KEY_TOOLBAR_ORDER, null)
            prefs.edit().apply {
                if (saved == old && order == "ai_reply,material_hub,clipboard") {
                    putStringSet(KEY_TOOLBAR_VISIBLE_ITEMS, old + "voice")
                    putString(KEY_TOOLBAR_ORDER, "voice,$order")
                }
                putBoolean("toolbar_voice_migrated", true)
            }.apply()
        }
        val available = availableDisplayItems(context)
        val availableValues = available.map { it.prefValue() }.toSet()
        val valueToItem = available.associateBy { it.prefValue() }

        val storedVisible = prefs.getStringSet(
            KEY_TOOLBAR_VISIBLE_ITEMS,
            DEFAULT_TOOLBAR_VISIBLE_ITEMS.map { it.prefValue }.toSet()
        ).orEmpty()
        val knownVisible = storedVisible.filter { it in availableValues }.toMutableSet()
        val storedOrder = prefs.getString(
            KEY_TOOLBAR_ORDER,
            DEFAULT_TOOLBAR_ORDER.joinToString(",") { it.prefValue }
        ).orEmpty().split(',').filter { it.isNotBlank() }
        val knownStoredOrder = storedOrder.filter { it in availableValues }
        val everSeen = (knownVisible + knownStoredOrder).toSet()

        var changed = storedVisible.size != knownVisible.size || storedOrder.size != knownStoredOrder.size
        for (entry in customToolbarEntries()) {
            val value = CUSTOM_TOOLBAR_PREF_PREFIX + entry.id
            if (value !in everSeen && knownVisible.size < MAX_TOOLBAR_ITEMS) {
                knownVisible.add(value)
                changed = true
            }
        }
        if (changed) {
            prefs.edit()
                .putStringSet(KEY_TOOLBAR_VISIBLE_ITEMS, knownVisible)
                .putString(KEY_TOOLBAR_ORDER, knownStoredOrder.filter { it in knownVisible }.joinToString(","))
                .apply()
        }

        val sanitizedVisible = sanitizeToolbarItems(
            context,
            knownVisible.mapNotNull { valueToItem[it] }.filterIsInstance<ToolbarDisplayItem.Builtin>().map { it.item }
        ).map { ToolbarDisplayItem.Builtin(it) as ToolbarDisplayItem } +
            knownVisible.mapNotNull { valueToItem[it] }.filterIsInstance<ToolbarDisplayItem.Custom>()

        val orderedValues = knownStoredOrder.filter { it in knownVisible.map { v -> v } .toSet() }
        val orderedSelected = orderedValues.mapNotNull { valueToItem[it] }
            .filter { it in sanitizedVisible }
        val remaining = sanitizedVisible.filter { it !in orderedSelected }
        val ordered = (orderedSelected + remaining).take(MAX_TOOLBAR_ITEMS)

        return ToolbarDisplaySettings(
            visibleItems = sanitizedVisible.take(MAX_TOOLBAR_ITEMS),
            orderedItems = ordered,
        )
    }

    fun setToolbarDisplayVisibleItems(context: Context, items: Set<ToolbarDisplayItem>) {
        val availableValues = availableDisplayItems(context).map { it.prefValue() }.toSet()
        val sanitized = items.map { it.prefValue() }.filter { it in availableValues }
            .distinct().take(MAX_TOOLBAR_ITEMS).toSet()
        sharedPrefs(context).edit()
            .putStringSet(KEY_TOOLBAR_VISIBLE_ITEMS, sanitized)
            .apply()
    }

    fun setToolbarDisplayOrder(context: Context, items: List<ToolbarDisplayItem>) {
        val settings = getToolbarDisplaySettings(context)
        val visibleValues = settings.visibleItems.map { it.prefValue() }.toSet()
        val availableValues = availableDisplayItems(context).map { it.prefValue() }.toSet()
        val sanitized = items.map { it.prefValue() }
            .filter { it in availableValues && it in visibleValues }
            .distinct()
            .take(MAX_TOOLBAR_ITEMS)
        sharedPrefs(context).edit()
            .putString(KEY_TOOLBAR_ORDER, sanitized.joinToString(","))
            .apply()
    }

    fun buildToolbarDisplayOrderSummary(context: Context): String {
        return getToolbarDisplaySettings(context).orderedItems.joinToString(" / ") { it.label(context) }
    }

    private fun sanitizeToolbarItems(context: Context, items: Iterable<ToolbarItem>): List<ToolbarItem> {
        return items
            .filter { it in allToolbarItems }
            .filterNot { it in unavailableToolbarItems(context) }
            .distinct()
    }

    private fun unavailableToolbarItems(context: Context): Set<ToolbarItem> {
        return buildSet {
            addAll(oemDisabledToolbarItems)
            if (!org.fcitx.fcitx5.android.input.voice.OemVoiceBridge.enabled(context)) add(ToolbarItem.Voice)
            if (!OemAddons.hasAiReply || !OemEnterpriseConfig.isAiReplyEntryEnabled(context)) {
                add(ToolbarItem.AiReply)
            }
            if (!OemAddons.hasMaterialHub || !OemEnterpriseConfig.isMaterialLibraryEntryEnabled(context)) {
                add(ToolbarItem.MaterialHub)
            }
        }
    }

    fun resetAllSettingsToDefault(context: Context) {
        val host = hostPrefs()
        val fcitx = fcitxPrefs()
        val prefs = sharedPrefs(context)

        setLayoutNumberRowEnabled(false)
        setLayoutSidebarMode(context, LayoutSidebarMode.Pinyin)
        setKeyboardHeightPercent(DEFAULT_KEYBOARD_HEIGHT_PERCENT)
        setDarkModeOption(DarkModeOption.FollowSystem)

        fcitx.keyboard.soundOnKeyPress.setValue(InputFeedbackMode.Enabled)
        org.fcitx.fcitx5.android.data.KeySounds.select(context, "standard")
        setSoundVolume(DEFAULT_SOUND_VOLUME)
        setHapticLevel(DEFAULT_HAPTIC_LEVEL)
        setPopupBubbleEnabled(true)

        setToolbarExpandByDefault(false)
        setToolbarHideComplex(context, true)
        setToolbarVisibleItems(context, DEFAULT_TOOLBAR_VISIBLE_ITEMS)
        setToolbarOrder(context, DEFAULT_TOOLBAR_ORDER)

        setAssistReturnSendEnabled(context, false)
        setAssistAutoSpaceEnabled(false)
        setAssistPairSymbolEnabled(true)
        setAssistChineseAssociationEnabled(true)
        setAssistDateAssociationEnabled(true)
        setAssistEmojiAssociationEnabled(true)
        setHalfWidthSymbolsMode(HalfWidthSymbolsMode.All)

        setClipboardHistoryEnabled(true)
        setClipboardHistoryLimit(DEFAULT_CLIPBOARD_HISTORY_LIMIT)
        setClipboardLayoutMode(ClipboardLayoutMode.ListView)

        setKeyboardMode(KeyboardMode.FullKeyboard)
        setHandwritingEnabled(context, true)
        setDoublePinyinSchema(DoublePinyinSchemaMode.flypy)
        setDoublePinyinHintEnabled(false)
        setSpaceSwipeMoveCursorEnabled(true)
        setOneHandMode(OneHandMode.Off)

        host.internal.pinyinModeRime.setValue(CustomConstant.SCHEMA_ZH_QWERTY)
        host.internal.inputDefaultMode.setValue(InputModeSwitcherManager.MODE_QWERTY_CHINESE)
        host.internal.inputMethodPinyinMode.setValue(InputModeSwitcherManager.MODE_QWERTY_CHINESE)
        host.keyboardSetting.oneHandedModSwitch.setValue(false)
        host.keyboardSetting.oneHandedMod.setValue(KeyboardOneHandedMod.LEFT)
        host.keyboardSetting.keyboardBalloonShow.setValue(true)

        fcitx.keyboard.popupOnKeyPress.setValue(true)
        fcitx.keyboard.useT9KeyboardLayout.setValue(false)
        fcitx.keyboard.spaceKeyLongPressBehavior.setValue(SpaceLongPressBehavior.None)
        fcitx.keyboard.expandToolbarByDefault.setValue(false)
        fcitx.keyboard.keyboardSidePadding.setValue(0)
        fcitx.keyboard.keyboardSidePaddingLandscape.setValue(0)
        fcitx.keyboard.keyboardHorizontalOffset.setValue(0)
        fcitx.keyboard.keyboardHorizontalOffsetLandscape.setValue(0)
        fcitx.keyboard.keyboardHeightPercent.setValue(DEFAULT_KEYBOARD_HEIGHT_PERCENT)
        fcitx.keyboard.keyboardHeightPercentLandscape.setValue(49)
        fcitx.keyboard.t9KeyboardHeightPercent.setValue(DEFAULT_KEYBOARD_HEIGHT_PERCENT)
        fcitx.keyboard.t9KeyboardHeightPercentLandscape.setValue(49)

        prefs.edit()
            .putString(KEY_LAYOUT_SIDEBAR_MODE, LayoutSidebarMode.Pinyin.name)
            .putBoolean(KEY_TOOLBAR_HIDE_COMPLEX, true)
            .putStringSet(KEY_TOOLBAR_VISIBLE_ITEMS, DEFAULT_TOOLBAR_VISIBLE_ITEMS.map { it.prefValue }.toSet())
            .putString(KEY_TOOLBAR_ORDER, DEFAULT_TOOLBAR_ORDER.joinToString(",") { it.prefValue })
            .putBoolean(KEY_HANDWRITING_ENABLED, true)
            .putBoolean(KEY_ASSIST_RETURN_SEND, false)
            .apply()
    }
}
