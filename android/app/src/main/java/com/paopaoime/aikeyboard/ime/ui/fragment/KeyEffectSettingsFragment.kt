package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge

class KeyEffectSettingsFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_key_effect_settings
) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.layout_key_effect_header, R.id.btn_key_effect_back)
        applyCardShadow(
            view,
            intArrayOf(
                R.id.card_key_effect_sound,
                R.id.card_key_effect_haptic,
                R.id.card_key_effect_bubble
            )
        )
        InputMethodSettingsBridge.normalizeSoundSettingsForUi()

        val bubbleSwitch = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_key_effect_bubble)
        val soundSeekBar = view.findViewById<SeekBar>(R.id.seek_key_effect_sound)
        val hapticSeekBar = view.findViewById<SeekBar>(R.id.seek_key_effect_haptic)

        bindLocalToggle(bubbleSwitch)

        bubbleSwitch.isChecked = InputMethodSettingsBridge.isPopupBubbleEnabled()
        soundSeekBar.progress = InputMethodSettingsBridge.getSoundVolume()
        hapticSeekBar.progress = InputMethodSettingsBridge.getHapticLevel()

        bubbleSwitch.setOnCheckedChangeListener { _, isChecked ->
            InputMethodSettingsBridge.setPopupBubbleEnabled(isChecked)
        }

        soundSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                InputMethodSettingsBridge.setSoundVolume(progress)
                val enabled = progress > 0
                InputMethodSettingsBridge.setSoundEnabled(enabled)
                if (enabled) {
                    InputMethodSettingsBridge.previewSoundEffect(view.context)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        hapticSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                InputMethodSettingsBridge.setHapticLevel(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        view.findViewById<View>(R.id.card_key_effect_bubble)?.setOnClickListener {
            bubbleSwitch.isChecked = !bubbleSwitch.isChecked
        }
    }
}
