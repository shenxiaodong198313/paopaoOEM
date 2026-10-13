package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.prefs.behavior.ClipboardLayoutMode
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge
import kotlinx.coroutines.launch

class ClipboardSettingsFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_clipboard_settings
) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.layout_clipboard_header, R.id.btn_clipboard_back)
        applyCardShadow(
            view,
            intArrayOf(
                R.id.card_clipboard_history,
                R.id.card_clipboard_layout,
                R.id.card_clipboard_clear
            )
        )
        val historySwitch = view.findViewById<SwitchCompat>(R.id.switch_clipboard_history)
        val limitValue = view.findViewById<TextView>(R.id.text_clipboard_limit_value)

        bindLocalToggle(historySwitch)
        historySwitch.isChecked = InputMethodSettingsBridge.getClipboardHistoryEnabled()
        historySwitch.setOnCheckedChangeListener { _, isChecked ->
            InputMethodSettingsBridge.setClipboardHistoryEnabled(isChecked)
        }
        renderLimit(limitValue)

        bindLayoutOptionGroup(
            view,
            intArrayOf(
                R.id.option_clipboard_layout_list,
                R.id.option_clipboard_layout_grid,
                R.id.option_clipboard_layout_compact
            ),
            intArrayOf(
                R.id.radio_clipboard_layout_list,
                R.id.radio_clipboard_layout_grid,
                R.id.radio_clipboard_layout_compact
            ),
            selectedIndex = when (InputMethodSettingsBridge.getClipboardLayoutMode()) {
                ClipboardLayoutMode.ListView -> 0
                ClipboardLayoutMode.GridView -> 1
                ClipboardLayoutMode.FlexboxView -> 2
            }
        ) { selectedIndex ->
            val mode = when (selectedIndex) {
                1 -> ClipboardLayoutMode.GridView
                2 -> ClipboardLayoutMode.FlexboxView
                else -> ClipboardLayoutMode.ListView
            }
            InputMethodSettingsBridge.setClipboardLayoutMode(mode)
        }
        view.findViewById<View>(R.id.item_clipboard_limit)?.setOnClickListener {
            openLimitDialog(limitValue)
        }
        view.findViewById<View>(R.id.btn_clipboard_clear_action)?.setOnClickListener {
            showConfirmDialog(
                title = getString(R.string.clipboard_clear_title),
                message = getString(R.string.clipboard_clear_confirm_message),
                positiveText = getString(R.string.clipboard_clear_action),
            ) {
                lifecycleScope.launch {
                    val clearedCount = InputMethodSettingsBridge.clearClipboardHistory()
                    showMessageDialog(
                        title = getString(R.string.clipboard_clear_title),
                        message = getString(R.string.clipboard_clear_result_format, clearedCount)
                    )
                }
            }
        }
    }

    private fun bindLayoutOptionGroup(
        rootView: View,
        optionIds: IntArray,
        radioIds: IntArray,
        selectedIndex: Int,
        onSelected: (Int) -> Unit,
    ) {
        val optionViews = optionIds.map { rootView.findViewById<View>(it) }
        val radioViews = radioIds.map { rootView.findViewById<AppCompatRadioButton>(it) }

        fun renderSelection(index: Int) {
            optionViews.forEachIndexed { currentIndex, optionView ->
                val selected = currentIndex == index
                optionView.isSelected = selected
                radioViews[currentIndex].isChecked = selected
            }
        }

        optionViews.forEachIndexed { index, optionView ->
            optionView.setOnClickListener {
                renderSelection(index)
                onSelected(index)
            }
            radioViews[index].setOnClickListener {
                renderSelection(index)
                onSelected(index)
            }
        }
        renderSelection(selectedIndex)
    }

    private fun openLimitDialog(limitView: TextView) {
        val choices = intArrayOf(10, 30, 50, 80, 110)
        val current = InputMethodSettingsBridge.getClipboardHistoryLimit()
        val selectedIndex = choices.indexOfFirst { it == current }.let { if (it >= 0) it else 2 }
        showSingleChoiceDialog(
            title = getString(R.string.clipboard_limit_dialog_title),
            options = choices.map { getString(R.string.clipboard_limit_choice_format, it) }.toTypedArray(),
            checkedIndex = selectedIndex,
        ) { index ->
            InputMethodSettingsBridge.setClipboardHistoryLimit(choices[index])
            renderLimit(limitView)
        }
    }

    private fun renderLimit(limitView: TextView) {
        limitView.text = getString(
            R.string.clipboard_limit_choice_format,
            InputMethodSettingsBridge.getClipboardHistoryLimit()
        )
    }

}
