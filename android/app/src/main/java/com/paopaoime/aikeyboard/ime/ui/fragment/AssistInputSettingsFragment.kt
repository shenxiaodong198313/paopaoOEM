package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.prefs.behavior.HalfWidthSymbolsMode
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge

class AssistInputSettingsFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_assist_input_settings
) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.layout_assist_input_header, R.id.btn_assist_input_back)
        applyCardShadow(
            view,
            intArrayOf(
                R.id.card_assist_input_behavior,
                R.id.card_assist_input_association,
                R.id.card_assist_input_symbols
            )
        )
        val returnSendSwitch = view.findViewById<SwitchCompat>(R.id.switch_assist_input_return_send)
        val autoSpaceSwitch = view.findViewById<SwitchCompat>(R.id.switch_assist_input_auto_space)
        val pairSymbolSwitch = view.findViewById<SwitchCompat>(R.id.switch_assist_input_pair_symbol)
        val chineseAssociationSwitch = view.findViewById<SwitchCompat>(R.id.switch_assist_input_chinese_association)
        val dateAssociationSwitch = view.findViewById<SwitchCompat>(R.id.switch_assist_input_date_association)
        val emojiAssociationSwitch = view.findViewById<SwitchCompat>(R.id.switch_assist_input_emoji_association)
        val halfWidthValue = view.findViewById<TextView>(R.id.text_assist_input_half_width_symbols_value)

        bindLocalToggle(returnSendSwitch)
        bindLocalToggle(autoSpaceSwitch)
        bindLocalToggle(pairSymbolSwitch)
        bindLocalToggle(chineseAssociationSwitch)
        bindLocalToggle(dateAssociationSwitch)
        bindLocalToggle(emojiAssociationSwitch)

        returnSendSwitch.isChecked = InputMethodSettingsBridge.isAssistReturnSendEnabled(requireContext())
        autoSpaceSwitch.isChecked = InputMethodSettingsBridge.isAssistAutoSpaceEnabled()
        pairSymbolSwitch.isChecked = InputMethodSettingsBridge.isAssistPairSymbolEnabled()
        chineseAssociationSwitch.isChecked = InputMethodSettingsBridge.isAssistChineseAssociationEnabled()
        dateAssociationSwitch.isChecked = InputMethodSettingsBridge.isAssistDateAssociationEnabled()
        emojiAssociationSwitch.isChecked = InputMethodSettingsBridge.isAssistEmojiAssociationEnabled()
        renderHalfWidthValue(halfWidthValue)

        returnSendSwitch.setOnCheckedChangeListener { _, isChecked ->
            InputMethodSettingsBridge.setAssistReturnSendEnabled(requireContext(), isChecked)
        }
        autoSpaceSwitch.setOnCheckedChangeListener { _, isChecked ->
            InputMethodSettingsBridge.setAssistAutoSpaceEnabled(isChecked)
        }
        pairSymbolSwitch.setOnCheckedChangeListener { _, isChecked ->
            InputMethodSettingsBridge.setAssistPairSymbolEnabled(isChecked)
        }
        chineseAssociationSwitch.setOnCheckedChangeListener { _, isChecked ->
            InputMethodSettingsBridge.setAssistChineseAssociationEnabled(isChecked)
        }
        dateAssociationSwitch.setOnCheckedChangeListener { _, isChecked ->
            InputMethodSettingsBridge.setAssistDateAssociationEnabled(isChecked)
        }
        emojiAssociationSwitch.setOnCheckedChangeListener { _, isChecked ->
            InputMethodSettingsBridge.setAssistEmojiAssociationEnabled(isChecked)
        }
        view.findViewById<View>(R.id.item_assist_input_half_width_symbols)?.setOnClickListener {
            openHalfWidthDialog(halfWidthValue)
        }
    }

    private fun openHalfWidthDialog(halfWidthValue: TextView) {
        val choices = arrayOf(
            HalfWidthSymbolsMode.All,
            HalfWidthSymbolsMode.OnlyUsed,
            HalfWidthSymbolsMode.None,
        )
        val titles = arrayOf(
            getString(R.string.assist_input_half_width_symbols_always),
            getString(R.string.assist_input_half_width_symbols_value),
            getString(R.string.assist_input_half_width_symbols_never),
        )
        val current = InputMethodSettingsBridge.getHalfWidthSymbolsMode()
        val checkedIndex = choices.indexOf(current).let { if (it >= 0) it else 1 }
        showSingleChoiceDialog(
            title = getString(R.string.assist_input_half_width_symbols_dialog_title),
            options = titles,
            checkedIndex = checkedIndex,
        ) { index ->
            InputMethodSettingsBridge.setHalfWidthSymbolsMode(choices[index])
            renderHalfWidthValue(halfWidthValue)
        }
    }

    private fun renderHalfWidthValue(halfWidthValue: TextView) {
        halfWidthValue.text = when (InputMethodSettingsBridge.getHalfWidthSymbolsMode()) {
            HalfWidthSymbolsMode.All -> getString(R.string.assist_input_half_width_symbols_always)
            HalfWidthSymbolsMode.OnlyUsed -> getString(R.string.assist_input_half_width_symbols_value)
            HalfWidthSymbolsMode.None -> getString(R.string.assist_input_half_width_symbols_never)
        }
    }
}
