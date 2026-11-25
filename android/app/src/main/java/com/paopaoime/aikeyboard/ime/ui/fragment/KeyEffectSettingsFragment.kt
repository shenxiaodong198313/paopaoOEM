package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.content.ContextCompat
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge
import org.fcitx.fcitx5.android.data.theme.KeyboardEffectPreference
import org.fcitx.fcitx5.android.data.theme.SkinEffectCatalog

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
        val soundList = view.findViewById<LinearLayout>(R.id.key_sound_list)
        val sounds = org.fcitx.fcitx5.android.data.KeySounds
        sounds.prepareAsync(view.context)
        val chips = mutableListOf<Pair<String, AppCompatRadioButton>>()
        val density = resources.displayMetrics.density
        val gap = (KeySoundChipLayout.GAP_DP * density).toInt()
        val tint = ContextCompat.getColor(view.context, R.color.text_cyan)
        var selectedSound = sounds.selected(view.context)
        var currentColumns = 0
        fun refreshSelection() {
            chips.forEach { (id, chip) ->
                val selected = id == selectedSound
                chip.isChecked = selected
                chip.isSelected = selected
                chip.setTextColor(if (selected) tint else Color.rgb(23, 56, 74))
                val shape = GradientDrawable().apply {
                    cornerRadius = 100 * density
                    setColor(Color.WHITE)
                    setStroke((if (selected) 1.5f * density else density).toInt().coerceAtLeast(1),
                        if (selected) tint else Color.rgb(224, 227, 230))
                }
                chip.background = RippleDrawable(ColorStateList.valueOf(0x1800897B), shape, null)
            }
        }
        fun renderSounds(columns: Int) {
            if (columns == currentColumns) return
            currentColumns = columns
            chips.clear()
            soundList.removeAllViews()
            sounds.items.chunked(columns).forEachIndexed { rowIndex, items ->
                val row = LinearLayout(view.context).apply { orientation = LinearLayout.HORIZONTAL }
                repeat(columns) { column ->
                    val item = items.getOrNull(column)
                    val child = if (item == null) View(view.context) else {
                        val (id, name) = item
                        AppCompatRadioButton(view.context).apply {
                            text = name
                            textSize = 14f
                            isSoundEffectsEnabled = false
                            buttonDrawable = null
                            gravity = Gravity.CENTER
                            minWidth = 0
                            minimumWidth = 0
                            minHeight = (48 * density).toInt()
                            maxLines = 2
                            setPadding((8 * density).toInt(), (10 * density).toInt(),
                                (8 * density).toInt(), (10 * density).toInt())
                            contentDescription = "$name，选择并试听"
                            setOnClickListener {
                                sounds.select(view.context, id)
                                selectedSound = id
                                refreshSelection()
                                sounds.play(view.context, id, maxOf(35, InputMethodSettingsBridge.getSoundVolume()) / 100f)
                            }
                            chips.add(id to this)
                        }
                    }
                    row.addView(child, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (column > 0) marginStart = gap
                    })
                }
                soundList.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (rowIndex > 0) topMargin = gap })
            }
            refreshSelection()
        }
        soundList.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            renderSounds(KeySoundChipLayout.columns(right - left, density, resources.configuration.fontScale))
        }
        renderSounds(KeySoundChipLayout.columns(
            ((resources.configuration.screenWidthDp - 72) * density).toInt(), density, resources.configuration.fontScale))
        val motionButton = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.button_key_effect_motion)
        motionButton.setOnClickListener { chooseMotion(motionButton) }
        renderMotion(motionButton)
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
                } else {
                    sounds.cancelPending()
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

    private fun motionChoices() = listOf(
        KeyboardEffectPreference.SKIN_DEFAULT to getString(R.string.oem_key_effect_skin_default),
        KeyboardEffectPreference.NONE to getString(R.string.oem_key_effect_none),
    ) + SkinEffectCatalog.items.map { it.id to it.title }

    private fun renderMotion(button: android.widget.Button) {
        val label = motionChoices().first { it.first == KeyboardEffectPreference.selected() }.second
        button.text = getString(R.string.oem_key_effect_motion_selected, label)
    }

    private fun chooseMotion(button: android.widget.Button) {
        val choices = motionChoices()
        showSingleChoiceDialog(
            title = getString(R.string.oem_key_effect_motion),
            options = choices.map { it.second }.toTypedArray(),
            checkedIndex = choices.indexOfFirst { it.first == KeyboardEffectPreference.selected() },
        ) { index ->
            KeyboardEffectPreference.select(choices[index].first)
            renderMotion(button)
        }
    }

    override fun onDestroyView() {
        org.fcitx.fcitx5.android.data.KeySounds.cancelPending()
        super.onDestroyView()
    }
}
