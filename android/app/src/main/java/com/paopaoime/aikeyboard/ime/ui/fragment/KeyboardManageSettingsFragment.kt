package com.paopaoime.aikeyboard.ime.ui.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.prefs.behavior.DoublePinyinSchemaMode
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge.MainKeyboardType
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge.OneHandMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class KeyboardManageSettingsFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_keyboard_manage_settings
) {
    private var loadedDoubleSchema: DoublePinyinSchemaMode? = null
    private var keyboardSelectionJob: Job? = null

    private data class KeyboardOptionView(
        val containerId: Int,
        val labelId: Int,
        val indicatorId: Int,
        val type: MainKeyboardType,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.layout_keyboard_manage_header, R.id.btn_keyboard_manage_back)
        applyCardShadow(
            view,
            intArrayOf(
                R.id.card_keyboard_manage_mode,
                R.id.card_keyboard_manage_schema,
                R.id.card_keyboard_manage_behavior
            )
        )

        val doubleHintSwitch = view.findViewById<SwitchCompat>(R.id.switch_keyboard_manage_double_hint)
        val cursorSwitch = view.findViewById<SwitchCompat>(R.id.switch_keyboard_manage_cursor)
        val doubleSchemaValue = view.findViewById<TextView>(R.id.text_keyboard_manage_double_schema_value)
        val oneHandValue = view.findViewById<TextView>(R.id.text_keyboard_manage_one_hand_value)
        val keyboardOptions = listOf(
            KeyboardOptionView(
                R.id.item_keyboard_manage_main_nine,
                R.id.text_keyboard_manage_main_nine,
                R.id.image_keyboard_manage_main_nine_indicator,
                MainKeyboardType.NineGridPinyin
            ),
            KeyboardOptionView(
                R.id.item_keyboard_manage_main_full,
                R.id.text_keyboard_manage_main_full,
                R.id.image_keyboard_manage_main_full_indicator,
                MainKeyboardType.FullPinyin
            ),
            KeyboardOptionView(
                R.id.item_keyboard_manage_main_english,
                R.id.text_keyboard_manage_main_english,
                R.id.image_keyboard_manage_main_english_indicator,
                MainKeyboardType.English26
            ),
            KeyboardOptionView(
                R.id.item_keyboard_manage_main_double,
                R.id.text_keyboard_manage_main_double,
                R.id.image_keyboard_manage_main_double_indicator,
                MainKeyboardType.DoublePinyin
            ),
            KeyboardOptionView(
                R.id.item_keyboard_manage_main_wubi,
                R.id.text_keyboard_manage_main_wubi,
                R.id.image_keyboard_manage_main_wubi_indicator,
                MainKeyboardType.Wubi
            ),
            KeyboardOptionView(
                R.id.item_keyboard_manage_main_wubi_pinyin,
                R.id.text_keyboard_manage_main_wubi_pinyin,
                R.id.image_keyboard_manage_main_wubi_pinyin_indicator,
                MainKeyboardType.WubiPinyin
            ),
        )

        bindLocalToggle(doubleHintSwitch)
        bindLocalToggle(cursorSwitch)

        doubleHintSwitch.isChecked = InputMethodSettingsBridge.isDoublePinyinHintEnabled()
        cursorSwitch.isChecked = InputMethodSettingsBridge.isSpaceSwipeMoveCursorEnabled()
        renderKeyboardOptions(view, keyboardOptions)
        renderValues(null, oneHandValue)
        loadDoubleSchema(view, doubleSchemaValue)

        keyboardOptions.forEach { option ->
            view.findViewById<View>(option.containerId).setOnClickListener {
                keyboardSelectionJob?.cancel()
                keyboardSelectionJob = viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        InputMethodSettingsBridge.setPrimaryKeyboardType(option.type)
                        renderKeyboardOptions(view, keyboardOptions)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        Toast.makeText(context, failure.message ?: "键盘设置保存失败，请重试", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        doubleHintSwitch.setOnCheckedChangeListener { _, isChecked ->
            InputMethodSettingsBridge.setDoublePinyinHintEnabled(isChecked)
        }
        cursorSwitch.setOnCheckedChangeListener { _, isChecked ->
            InputMethodSettingsBridge.setSpaceSwipeMoveCursorEnabled(isChecked)
        }

        view.findViewById<View>(R.id.item_keyboard_manage_double_schema)?.setOnClickListener {
            openDoubleSchemaDialog(doubleSchemaValue)
        }
        view.findViewById<View>(R.id.item_keyboard_manage_one_hand)?.setOnClickListener {
            openOneHandDialog(oneHandValue)
        }
    }

    private fun openDoubleSchemaDialog(doubleSchemaValue: TextView) {
        val values = arrayOf(
            DoublePinyinSchemaMode.flypy,
            DoublePinyinSchemaMode.natural,
            DoublePinyinSchemaMode.abc,
            DoublePinyinSchemaMode.mspy,
            DoublePinyinSchemaMode.sogou,
            DoublePinyinSchemaMode.ziguang,
        )
        val titles = arrayOf("小鹤双拼", "自然码", "智能ABC", "微软双拼", "搜狗双拼", "紫光双拼")
        val checkedIndex = values.indexOf(loadedDoubleSchema)
        showSingleChoiceDialog(
            title = getString(R.string.keyboard_manage_double_schema_dialog_title),
            options = titles,
            checkedIndex = checkedIndex,
        ) { index ->
            val row = requireView().findViewById<View>(R.id.item_keyboard_manage_double_schema)
            viewLifecycleOwner.lifecycleScope.launch {
                row.isEnabled = false
                doubleSchemaValue.text = "正在保存…"
                try {
                    InputMethodSettingsBridge.setDoublePinyinSchema(values[index])
                    loadedDoubleSchema = values[index]
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Toast.makeText(context, failure.message ?: "双拼方案保存失败，请重试", Toast.LENGTH_LONG).show()
                } finally {
                    renderValues(doubleSchemaValue, null)
                    row.isEnabled = true
                }
            }
        }
    }

    private fun loadDoubleSchema(root: View, value: TextView) {
        loadedDoubleSchema = null
        val row = root.findViewById<View>(R.id.item_keyboard_manage_double_schema)
        row.isEnabled = false
        value.text = "正在读取…"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                loadedDoubleSchema = InputMethodSettingsBridge.getDoublePinyinSchema()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Toast.makeText(context, failure.message ?: "双拼方案读取失败，请重试", Toast.LENGTH_LONG).show()
            } finally {
                renderValues(value, null)
                row.isEnabled = true
            }
        }
    }

    private fun openOneHandDialog(oneHandValue: TextView) {
        val checkedIndex = when (InputMethodSettingsBridge.getOneHandMode()) {
            OneHandMode.Off -> 0
            OneHandMode.Left -> 1
            OneHandMode.Right -> 2
        }
        showSingleChoiceDialog(
            title = getString(R.string.keyboard_manage_one_hand_dialog_title),
            options = arrayOf(
                getString(R.string.keyboard_manage_one_hand_off),
                getString(R.string.keyboard_manage_one_hand_left),
                getString(R.string.keyboard_manage_one_hand_right),
            ),
            checkedIndex = checkedIndex,
        ) { index ->
            val mode = when (index) {
                1 -> OneHandMode.Left
                2 -> OneHandMode.Right
                else -> OneHandMode.Off
            }
            InputMethodSettingsBridge.setOneHandMode(mode)
            renderValues(null, oneHandValue)
        }
    }

    private fun renderValues(
        doubleSchemaValue: TextView?,
        oneHandValue: TextView?,
    ) {
        doubleSchemaValue?.text = when (loadedDoubleSchema) {
            DoublePinyinSchemaMode.flypy -> "小鹤双拼"
            DoublePinyinSchemaMode.natural -> "自然码"
            DoublePinyinSchemaMode.abc -> "智能ABC"
            DoublePinyinSchemaMode.mspy -> "微软双拼"
            DoublePinyinSchemaMode.sogou -> "搜狗双拼"
            DoublePinyinSchemaMode.ziguang -> "紫光双拼"
            null -> "未读取到当前方案"
        }
        oneHandValue?.text = when (InputMethodSettingsBridge.getOneHandMode()) {
            OneHandMode.Off -> getString(R.string.keyboard_manage_one_hand_off)
            OneHandMode.Left -> getString(R.string.keyboard_manage_one_hand_left)
            OneHandMode.Right -> getString(R.string.keyboard_manage_one_hand_right)
        }
    }

    private fun renderKeyboardOptions(root: View, options: List<KeyboardOptionView>) {
        val current = InputMethodSettingsBridge.getPrimaryKeyboardType()
        val selectedTextColor = Color.parseColor("#24B79B")
        val normalTextColor = Color.parseColor("#2E3131")
        val selectedTint = ColorStateList.valueOf(Color.WHITE)
        val transparentTint = ColorStateList.valueOf(Color.TRANSPARENT)
        options.forEach { option ->
            root.findViewById<View>(option.containerId).visibility = if (option.type in setOf(MainKeyboardType.Wubi, MainKeyboardType.WubiPinyin) && !org.fcitx.fcitx5.android.input.status.PinyinDictionaryPackages.wubiEnabled()) View.GONE else View.VISIBLE
            val selected = current == option.type
            root.findViewById<View>(option.containerId).setBackgroundResource(
                if (selected) R.drawable.bg_keyboard_manage_option_selected
                else R.drawable.bg_keyboard_manage_option
            )
            root.findViewById<TextView>(option.labelId).setTextColor(
                if (selected) selectedTextColor else normalTextColor
            )
            val indicator = root.findViewById<ImageView>(option.indicatorId)
            indicator.setBackgroundResource(
                if (selected) R.drawable.bg_keyboard_manage_option_indicator_selected
                else R.drawable.bg_keyboard_manage_option_indicator
            )
            indicator.setImageResource(if (selected) R.drawable.ic_menu_done else 0)
            indicator.imageTintList = if (selected) selectedTint else transparentTint
        }
        root.findViewById<TextView>(R.id.text_keyboard_manage_main_hint).text = when (current) {
            MainKeyboardType.NineGridPinyin -> getString(R.string.keyboard_manage_main_selected_nine)
            MainKeyboardType.FullPinyin -> getString(R.string.keyboard_manage_main_selected_full)
            MainKeyboardType.English26 -> getString(R.string.keyboard_manage_main_selected_english)
            MainKeyboardType.DoublePinyin -> getString(R.string.keyboard_manage_main_selected_double)
            MainKeyboardType.Wubi -> getString(R.string.keyboard_manage_main_selected_wubi)
            MainKeyboardType.WubiPinyin -> getString(R.string.keyboard_manage_main_selected_wubi_pinyin)
        }
        root.findViewById<TextView>(R.id.text_keyboard_manage_double_schema_value).alpha =
            if (current == MainKeyboardType.DoublePinyin) 1f else 0.56f
        root.findViewById<View>(R.id.item_keyboard_manage_double_schema).alpha =
            if (current == MainKeyboardType.DoublePinyin) 1f else 0.78f
    }
}
