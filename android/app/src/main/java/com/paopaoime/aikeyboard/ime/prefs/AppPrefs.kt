
package com.paopaoime.aikeyboard.ime.prefs

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.application.Launcher
import com.paopaoime.aikeyboard.ime.application.CustomConstant
import com.paopaoime.aikeyboard.ime.manager.InputModeSwitcherManager
import com.paopaoime.aikeyboard.ime.prefs.behavior.ClipboardLayoutMode
import com.paopaoime.aikeyboard.ime.prefs.behavior.DoublePinyinSchemaMode
import com.paopaoime.aikeyboard.ime.prefs.behavior.FullDisplayCenterMode
import com.paopaoime.aikeyboard.ime.prefs.behavior.FullDisplayKeyMode
import com.paopaoime.aikeyboard.ime.prefs.behavior.HalfWidthSymbolsMode
import com.paopaoime.aikeyboard.ime.prefs.behavior.KeyboardOneHandedMod
import com.paopaoime.aikeyboard.ime.utils.DevicesUtils
import com.paopaoime.aikeyboard.ime.view.preference.ManagedPreference


class AppPrefs(private val sharedPreferences: SharedPreferences) {

    inner class Internal : ManagedPreferenceInternal(sharedPreferences) {
        val pinyinModeRime = string("input_method_pinyin_mode_rime", CustomConstant.SCHEMA_ZH_QWERTY) //拼音输入模式记录，记录引操
        val inputDefaultMode = int("input_default_method_mode", InputModeSwitcherManager.MODE_QWERTY_CHINESE)   //默认输入法类型
        val inputMethodPinyinMode = int("input_method_pinyin_mode", InputModeSwitcherManager.MODE_QWERTY_CHINESE)  // 保存中文输入法类型
        val dataDictVersion = int("rime_dict_data_version", 0)  //缓存rime词库版本号 用于校验是否覆盖词库文件
        val keyboardHeightRatio = float("keyboard_height_ratio", 0.3f)     //键盘高度比例
        val keyboardHeightRatioLandscape = float("keyboard_height_ratio_landscape", 0.5f)     //键盘高度比例:横屏
        val candidatesHeightRatio = float("candidates_height_ratio", 0.035f)     //候选词栏高度比例
        val candidatesHeightRatioLandscape = float("candidates_height_ratio_landscape", 0.06f)     //候选词栏高度比例:横屏
        val keyboardModeFloat = bool("keyboard_mode_float", false)     // 悬浮模式
        val keyboardModeFloatLandscape = bool("keyboard_mode_float_landscape", false)// 悬浮模式:横屏
        val keyboardBottomPaddingFloat = int("keyboard_padding_bottom", DevicesUtils.dip2px(100))     //竖屏悬浮模式底边距
        val keyboardRightPaddingFloat = int("keyboard_padding_right", DevicesUtils.dip2px(20))     //竖屏悬浮模式右边距
        val keyboardBottomPaddingLandscapeFloat = int("keyboard_padding_bottom_landscape", DevicesUtils.dip2px(50))     //横屏悬浮模式底边距
        val keyboardRightPaddingLandscapeFloat = int("keyboard_padding_right_landscape", DevicesUtils.dip2px(20))     //横屏悬浮模式右边距
        val keyboardBottomPadding = int("keyboard_padding_bottom_normal", DevicesUtils.dip2px(0))     //竖屏非悬浮底边距
        val keyboardRightPadding = int("keyboard_padding_right_normal", DevicesUtils.dip2px(0))     //竖屏非悬浮右边距
        val clipboardUpdateTime = long("clipboard_update_time", 0)     //剪切板更新时间
        val clipboardUpdateContent = string("clipboard_update_content","")     //剪切板更新内容
        val fullDisplayKeyboardEnable = bool("full_display_keyboard_enable", true)     //全面屏键盘优化
        val fullDisplayKeyModeLeft = string("full_display_key_mode_left", FullDisplayKeyMode.SwitchIme.name)     //全面屏键盘优化:左键盘
        val fullDisplayKeyModeRight = string("full_display_key_mode_right", FullDisplayKeyMode.Clipboard.name)     //全面屏键盘优化:右键盘
        val fullDisplayCenterMode = string("full_display_center_mode", FullDisplayCenterMode.MoveCursor.name)     //全面屏键盘优化:中间区域

        val soundOnKeyPress = int("key_press_vibration_amplitude",0)     //按键音量
        val vibrationAmplitude = int("key_press_sound_volume", 0)     //触感强度

        val privacyPolicySure = bool("privacy_policy_sure", true)
    }

    inner class Input : ManagedPreferenceCategory(R.string.setting_ime_input, sharedPreferences) {

        val titleChinese = category(R.string.chinese_input_setting)

        val chineseFanTi = switch(
            R.string.setting_jian_fan, "chinese_jian_fan_enable", false
        )

        val doublePYSchemaMode =
            list(
                R.string.double_pinyin_schema_mode,
                "double_pinyin_schema_mode",
                DoublePinyinSchemaMode.flypy,
                DoublePinyinSchemaMode,
                listOf(
                    DoublePinyinSchemaMode.flypy,
                    DoublePinyinSchemaMode.natural,
                    DoublePinyinSchemaMode.abc,
                    DoublePinyinSchemaMode.mspy,
                    DoublePinyinSchemaMode.sogou,
                    DoublePinyinSchemaMode.ziguang,
                ),
                listOf(
                    R.string.double_pinyin_flypy_plus,
                    R.string.double_pinyin_natural,
                    R.string.double_pinyin_abc,
                    R.string.double_pinyin_mspy,
                    R.string.double_pinyin_sougou,
                    R.string.double_pinyin_ziguang,
                )
            )

        val chinesePrediction = switch(
            R.string.chinese_association, "chinese_association_enable", true
        )

        val chinesePredictionDate = switch(
            R.string.chinese_association_date, "chinese_association_date_enable", true
        )

        val titleEnglish = category(R.string.EnglishInput)

        //输出英文单词:英文补全
        val abcSearchEnglishCell = switch(
            R.string.search_english_cell, "search_english_cell_enable", true
        )

        val abcSpaceAuto = switch(
            R.string.space_auto, "abc_space_auto_enable", false
        ){
            abcSearchEnglishCell.getValue()
        }

        val titleEmoji = category(R.string.emoji_setting)
        val emojiInput = switch(
            R.string.emoji_input, "emoji_input_enable", true
        )

        val titleSymbol = category(R.string.symbol_setting)
        val symbolPairInput = switch(
            R.string.symbol_pair_input, "symbol_pair_input_enable", true
        )
    }

    inner class KeyboardSetting : ManagedPreferenceCategory(R.string.setting_ime_keyboard, sharedPreferences) {

        val candidateTextSize = int(
            R.string.candidate_size_input_setting,
            "candidate_size",
            55,
            25,
            100,
            "%",
            defaultLabel = R.string.system_default
        )

        val keyboardBalloonShow = switch(R.string.keypopup_input_settings, "keyboard_balloon_show_enable", false)

        val longPressTimeout = int(
            R.string.long_press_timeout,
            "long_press_timeout",
            400,
            100,
            700,
            "毫秒",
            50,
            defaultLabel = R.string.number_400_ms
        )


        val abcNumberLine = switch(R.string.engish_full_keyboard, "keyboard_abc_number_line_enable", false)

        val lx17WithLeftPrefix = switch(R.string.lx17_with_left_prefix, "lx17_with_left_prefix_enable", true)

        val keyboardDoubleInputKey = switch(R.string.keyboard_double_input_key, "keyboard_double_input_pinyin_enable", true)

        val keyboardMnemonic = switch(R.string.keyboard_mnemonic_show, "keyboard_mnemonic_show_enable", false)

        val spaceSwipeMoveCursor = switch(R.string.space_swipe_move_cursor, "space_swipe_move_cursor", true)

        val spaceSwipeMoveCursorSpeed = int(
            R.string.swipe_move_cursor_speed,
            "swipe_move_cursor_speed",
            10,
            1,
            50,
            "px"
        )

        // 锁定英语键盘:锁定后，切换到英语键盘，下次弹出键盘使用英语模式
        val keyboardLockEnglish = switch(R.string.keyboard_menu_lock_english, "keyboard_menu_lock_english_enable", false)

        val oneHandedModSwitch = switch(R.string.keyboard_one_handed_mod, "keyboard_one_handed_mod_enable", false)

        val oneHandedMod = list(
            R.string.keyboard_one_handed_mod,
            "keyboard_one_handed_mod",
            KeyboardOneHandedMod.LEFT,
            KeyboardOneHandedMod,
            listOf(
                KeyboardOneHandedMod.LEFT,
                KeyboardOneHandedMod.RIGHT
            ),
            listOf(
                R.string.keyboard_one_handed_mod_left,
                R.string.keyboard_one_handed_mod_right
            )
        ) {
            oneHandedModSwitch.getValue()
        }

        val halfWidthSymbolsMode = list(
            R.string.half_width_symbols_tips,
            "half_width_symbols_tips",
            HalfWidthSymbolsMode.All,
            HalfWidthSymbolsMode,
            listOf(
                HalfWidthSymbolsMode.All,
                HalfWidthSymbolsMode.OnlyUsed,
                HalfWidthSymbolsMode.None
            ),
            listOf(
                R.string.half_width_symbols_tips_all,
                R.string.half_width_symbols_tips_only_used,
                R.string.half_width_symbols_tips_none
            )
        )
    }

    inner class Other : ManagedPreferenceCategory(R.string.setting_ime_other, sharedPreferences) {
        val titleLab = category(R.string.lab_features)

        val labImageCompressEnable = switch(
            R.string.lab_image_compress_enable,
            "lab_image_compress_enable",
            false,
            R.string.lab_image_compress_enable_desc
        )

        val labImageCompressMaxEdge = int(
            R.string.lab_image_compress_max_edge,
            "lab_image_compress_max_edge",
            1280,
            512,
            4096,
            "px",
            64
        ) { labImageCompressEnable.getValue() }

        val labImageCompressJpegQuality = int(
            R.string.lab_image_compress_jpeg_quality,
            "lab_image_compress_jpeg_quality",
            75,
            30,
            100,
            "%",
            1
        ) { labImageCompressEnable.getValue() }

        val imeHideIcon = switch(R.string.ime_hide_icon, "ime_hide_icon_enable", false, R.string.ime_hide_icon_tips)
    }

    inner class Handwriting : ManagedPreferenceCategory(R.string.setting_ime_input, sharedPreferences) {


        val handWritingWidth = int(
            R.string.paint_thickness,
            "hand_writing_width",
            35,
            0,
            100,
            "%",
            defaultLabel = R.string.system_default
        )

        val handWritingSpeed = int(
            R.string.discern_sensitive,
            "hand_writing_speed",
            500,
            300,
            1300,
            "毫秒",
            100,
            defaultLabel = R.string.number_500_ms
        )
    }

    inner class Clipboard : ManagedPreferenceCategory(R.string.clipboard, sharedPreferences) {
        val clipboardListening = switch(R.string.clipboard_listening, "clipboard_enable", true)
        val clipboardHistoryLimit = int(
            R.string.clipboard_limit,
            "clipboard_limit",
            50,
            10,
            110,
            "条",
            10,
            defaultLabel = R.string.num_50
        ) { clipboardListening.getValue() }
        val clipboardLayoutCompact = list(
            R.string.clipboard_layout_compact_mode,
            "clipboard_layout_mode",
            ClipboardLayoutMode.ListView,
            ClipboardLayoutMode,
            listOf(
                ClipboardLayoutMode.ListView,
                ClipboardLayoutMode.GridView,
                ClipboardLayoutMode.FlexboxView
            ),
            listOf(
                R.string.clipboard_layout_mode_list,
                R.string.clipboard_layout_mode_grid,
                R.string.clipboard_layout_mode_flexbox
            )
        ) {
            clipboardListening.getValue()
        }
    }

    private val providers = mutableListOf<ManagedPreferenceProvider>()

    fun <T : ManagedPreferenceProvider> registerProvider(
        providerF: (SharedPreferences) -> T
    ): T {
        val provider = providerF(sharedPreferences)
        providers.add(provider)
        return provider
    }

    private fun <T : ManagedPreferenceProvider> T.register() = this.apply {
        registerProvider { this }
    }


    val internal = Internal().register()
    val handwriting = Handwriting().register()
    val input = Input().register()
    val clipboard = Clipboard().register()
    val keyboardSetting = KeyboardSetting().register()
    val other = Other().register()

    private val onSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            providers.forEach {
                it.managedPreferences[key]?.fireChange()
            }
        }

    @RequiresApi(Build.VERSION_CODES.N)
    fun syncToDeviceEncryptedStorage() {
        val ctx = Launcher.instance.context.createDeviceProtectedStorageContext()
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit {
            internal.managedPreferences.forEach {
                it.value.putValueTo(this@edit)
            }

            input.managedPreferences.forEach {
                it.value.putValueTo(this@edit)
            }
            handwriting.managedPreferences.forEach {
                it.value.putValueTo(this@edit)
            }
            clipboard.managedPreferences.forEach {
                it.value.putValueTo(this@edit)
            }
            keyboardSetting.managedPreferences.forEach {
                it.value.putValueTo(this@edit)
            }
            other.managedPreferences.forEach {
                it.value.putValueTo(this@edit)
            }
        }
    }
    companion object {
        object IntCodec : ManagedPreference.StringLikeCodec<Int> {
            override fun decode(raw: String): Int? = raw.toIntOrNull()
            override fun encode(x: Int): String = x.toString()
        }
        object StringCodec : ManagedPreference.StringLikeCodec<String> {
            override fun decode(raw: String): String = raw
            override fun encode(x: String): String = x
        }
        private var instance: AppPrefs? = null

        /**
         * MUST call before use
         */
        fun init(sharedPreferences: SharedPreferences) {
            if (instance != null)
                return
            instance = AppPrefs(sharedPreferences)
            sharedPreferences.registerOnSharedPreferenceChangeListener(getInstance().onSharedPreferenceChangeListener)
        }

        fun getInstance() = instance!!
    }
}
