package com.paopaoime.aikeyboard.ime.ui.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRadioButton
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge.DarkModeOption
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge.LayoutSidebarMode

class LayoutDisplaySettingsFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_layout_display_settings
) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.layout_layout_display_header, R.id.btn_layout_display_back)
        applyCardShadow(
            view,
            intArrayOf(
                R.id.card_layout_display_number_input,
                R.id.card_layout_display_height,
                R.id.card_layout_display_sidebar,
                R.id.card_layout_display_dark_mode
            )
        )

        val selectedNumberInputIndex = if (InputMethodSettingsBridge.getLayoutNumberRowEnabled()) 0 else 1
        bindSingleChoiceGroup(
            rootView = view,
            optionIds = intArrayOf(
                R.id.option_layout_display_number_full,
                R.id.option_layout_display_number_nine
            ),
            radioIds = intArrayOf(
                R.id.radio_layout_display_number_full,
                R.id.radio_layout_display_number_nine
            ),
            selectedIndex = selectedNumberInputIndex
        ) { selectedIndex ->
            InputMethodSettingsBridge.setLayoutNumberRowEnabled(selectedIndex == 0)
        }

        val selectedSidebarIndex = when (InputMethodSettingsBridge.getLayoutSidebarMode(requireContext())) {
            LayoutSidebarMode.Pinyin -> 0
            LayoutSidebarMode.Number -> 1
        }
        bindSingleChoiceGroup(
            rootView = view,
            optionIds = intArrayOf(
                R.id.option_layout_display_sidebar_pinyin,
                R.id.option_layout_display_sidebar_number
            ),
            radioIds = intArrayOf(
                R.id.radio_layout_display_sidebar_pinyin,
                R.id.radio_layout_display_sidebar_number
            ),
            selectedIndex = selectedSidebarIndex
        ) { selectedIndex ->
            InputMethodSettingsBridge.setLayoutSidebarMode(
                requireContext(),
                if (selectedIndex == 0) LayoutSidebarMode.Pinyin else LayoutSidebarMode.Number
            )
        }

        val selectedDarkModeIndex = when (InputMethodSettingsBridge.getDarkModeOption()) {
            DarkModeOption.FollowSystem -> 0
            DarkModeOption.Light -> 1
            DarkModeOption.Dark -> 2
        }
        bindSingleChoiceGroup(
            rootView = view,
            optionIds = intArrayOf(
                R.id.option_layout_display_dark_system,
                R.id.option_layout_display_dark_light,
                R.id.option_layout_display_dark_dark
            ),
            radioIds = intArrayOf(
                R.id.radio_layout_display_dark_system,
                R.id.radio_layout_display_dark_light,
                R.id.radio_layout_display_dark_dark
            ),
            selectedIndex = selectedDarkModeIndex
        ) { selectedIndex ->
            val option = when (selectedIndex) {
                1 -> DarkModeOption.Light
                2 -> DarkModeOption.Dark
                else -> DarkModeOption.FollowSystem
            }
            InputMethodSettingsBridge.setDarkModeOption(option)
        }

        val heightButton = view.findViewById<TextView>(R.id.btn_layout_display_height_setting)
        val heightPreview = view.findViewById<View>(R.id.preview_layout_display_height)
        renderHeightButton(heightButton)
        renderHeightPreview(heightPreview)
        heightButton?.setOnClickListener { openKeyboardHeightAdjustPage() }
        view.findViewById<View>(R.id.card_layout_display_height)?.setOnClickListener {
            openKeyboardHeightAdjustPage()
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { root ->
            renderHeightButton(root.findViewById(R.id.btn_layout_display_height_setting))
            renderHeightPreview(root.findViewById(R.id.preview_layout_display_height))
        }
    }

    private fun bindSingleChoiceGroup(
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
                radioViews[currentIndex].setTextColor(
                    Color.parseColor(if (selected) "#22B783" else "#818785")
                )
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

    private fun openKeyboardHeightAdjustPage() {
        openSettingsPage(KeyboardHeightAdjustFragment())
    }

    private fun renderHeightButton(heightButton: TextView?) {
        heightButton?.text = getString(
            R.string.layout_display_height_button_format,
            InputMethodSettingsBridge.getKeyboardHeightPercent()
        )
    }

    private fun renderHeightPreview(heightPreview: View?) {
        val preview = heightPreview ?: return
        val percent = InputMethodSettingsBridge.getKeyboardHeightPercent().coerceIn(10, 90)
        val ratio = (percent - 10) / 80f
        val minHeightDp = 84
        val maxHeightDp = 124
        val targetDp = minHeightDp + ((maxHeightDp - minHeightDp) * ratio)
        preview.layoutParams = preview.layoutParams.apply {
            height = (targetDp * preview.resources.displayMetrics.density).toInt()
        }
    }
}
