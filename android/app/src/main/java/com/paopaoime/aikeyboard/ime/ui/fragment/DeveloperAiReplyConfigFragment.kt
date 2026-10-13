package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperAiConfigDefaults
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperAiConfigStore
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperAiReplyConfig
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperAiTonePreset

class DeveloperAiReplyConfigFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_developer_ai_reply_config,
) {
    private var systemPromptInput: EditText? = null
    private var replyCountInput: EditText? = null
    private var replyLengthPolicyInput: EditText? = null
    private var businessRoleInput: EditText? = null
    private var minCharsInput: EditText? = null
    private var maxCharsInput: EditText? = null
    private var languageInput: EditText? = null
    private var allowEmojiSwitch: SwitchCompat? = null
    private var avoidNicknameSwitch: SwitchCompat? = null
    private var summaryView: TextView? = null
    private var toneEmptyView: TextView? = null
    private var toneListContainer: LinearLayout? = null
    private val tonePresets = mutableListOf<OemDeveloperAiTonePreset>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.layout_developer_ai_reply_header, R.id.btn_developer_ai_reply_back)
        applyCardShadow(
            view,
            intArrayOf(
                R.id.card_developer_ai_reply_intro,
                R.id.card_developer_ai_reply_fields,
                R.id.card_developer_ai_reply_tones,
                R.id.card_developer_ai_reply_actions,
            ),
        )
        systemPromptInput = view.findViewById(R.id.et_developer_ai_system_prompt)
        replyCountInput = view.findViewById(R.id.et_developer_ai_reply_count)
        replyLengthPolicyInput = view.findViewById(R.id.et_developer_ai_reply_length_policy)
        businessRoleInput = view.findViewById(R.id.et_developer_ai_business_role)
        minCharsInput = view.findViewById(R.id.et_developer_ai_min_chars)
        maxCharsInput = view.findViewById(R.id.et_developer_ai_max_chars)
        languageInput = view.findViewById(R.id.et_developer_ai_language)
        allowEmojiSwitch = view.findViewById(R.id.switch_developer_ai_allow_emoji)
        avoidNicknameSwitch = view.findViewById(R.id.switch_developer_ai_avoid_nickname)
        summaryView = view.findViewById(R.id.tv_developer_ai_reply_summary)
        toneEmptyView = view.findViewById(R.id.tv_developer_ai_tones_empty)
        toneListContainer = view.findViewById(R.id.layout_developer_ai_tone_list)

        view.findViewById<View>(R.id.btn_developer_ai_tone_add)?.setOnClickListener {
            showToneEditor(existingIndex = null)
        }
        view.findViewById<View>(R.id.btn_developer_ai_reply_save)?.setOnClickListener {
            saveConfig()
        }
        view.findViewById<View>(R.id.btn_developer_ai_reply_reset)?.setOnClickListener {
            OemDeveloperAiConfigStore.clearReplyConfig(requireContext())
            bindConfig(OemDeveloperAiReplyConfig())
            Toast.makeText(requireContext(), R.string.developer_ai_reply_reset_done, Toast.LENGTH_SHORT).show()
        }
        bindConfig(OemDeveloperAiConfigStore.load(requireContext()).replyConfig)
    }

    private fun bindConfig(config: OemDeveloperAiReplyConfig) {
        systemPromptInput?.setText(config.systemPrompt)
        replyCountInput?.setText(config.replyCount?.toString().orEmpty())
        replyLengthPolicyInput?.setText(config.replyLengthPolicy)
        businessRoleInput?.setText(config.businessRole)
        minCharsInput?.setText(config.minChars?.toString().orEmpty())
        maxCharsInput?.setText(config.maxChars?.toString().orEmpty())
        languageInput?.setText(config.language)
        allowEmojiSwitch?.isChecked = config.allowEmoji ?: OemDeveloperAiConfigDefaults.DEFAULT_ALLOW_EMOJI
        avoidNicknameSwitch?.isChecked = config.avoidNickname ?: OemDeveloperAiConfigDefaults.DEFAULT_AVOID_NICKNAME
        tonePresets.clear()
        tonePresets.addAll(config.tonePresets)
        summaryView?.text = getString(
            R.string.developer_ai_reply_summary_template,
            OemDeveloperAiConfigDefaults.DEFAULT_REPLY_COUNT,
            OemDeveloperAiConfigDefaults.DEFAULT_MIN_CHARS,
            OemDeveloperAiConfigDefaults.DEFAULT_MAX_CHARS,
            OemDeveloperAiConfigDefaults.DEFAULT_LANGUAGE,
        )
        renderToneList()
    }

    private fun renderToneList() {
        val container = toneListContainer ?: return
        container.removeAllViews()
        toneEmptyView?.visibility = if (tonePresets.isEmpty()) View.VISIBLE else View.GONE
        tonePresets.forEachIndexed { index, preset ->
            container.addView(createToneItemView(index, preset))
        }
    }

    private fun createToneItemView(index: Int, preset: OemDeveloperAiTonePreset): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_white_12dp)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { params ->
                if (index > 0) {
                    params.topMargin = dp(10)
                }
            }

            addView(TextView(requireContext()).apply {
                text = getString(R.string.developer_ai_tone_title_template, index + 1, preset.label)
                setTextColor(requireContext().getColor(android.R.color.black))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(requireContext()).apply {
                text = preset.promptHint
                setTextColor(requireContext().getColor(android.R.color.darker_gray))
                textSize = 12f
                setLineSpacing(0f, 1.2f)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            })
            addView(
                LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        buildActionChip(
                            text = getString(R.string.developer_ai_tone_edit),
                            onClick = {
                                showToneEditor(existingIndex = index)
                            },
                        ),
                    )
                    addView(
                        buildActionChip(
                            text = getString(R.string.developer_ai_tone_delete),
                            onClick = {
                                tonePresets.removeAt(index)
                                renderToneList()
                            },
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                marginStart = dp(8)
                            },
                        ),
                    )
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(10)
                },
            )
        }
    }

    private fun buildActionChip(
        text: String,
        onClick: () -> Unit,
        layoutParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    ): TextView {
        return TextView(requireContext()).apply {
            this.layoutParams = layoutParams
            setBackgroundResource(R.drawable.bg_developer_mode_chip_unselected)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextColor(requireContext().getColor(android.R.color.black))
            textSize = 12f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            this.text = text
            setOnClickListener { onClick() }
        }
    }

    private fun showToneEditor(existingIndex: Int?) {
        val existing = existingIndex?.let(tonePresets::get)
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val labelInput = EditText(requireContext()).apply {
            hint = getString(R.string.developer_ai_tone_label_hint)
            setText(existing?.label.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val promptInput = EditText(requireContext()).apply {
            hint = getString(R.string.developer_ai_tone_prompt_hint)
            setText(existing?.promptHint.orEmpty())
            minLines = 4
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        content.addView(labelInput)
        content.addView(promptInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(12)
        })
        AlertDialog.Builder(requireContext())
            .setTitle(
                if (existingIndex == null) {
                    R.string.developer_ai_reply_tone_add
                } else {
                    R.string.developer_ai_tone_edit_title
                },
            )
            .setView(content)
            .setPositiveButton(R.string.developer_ai_tone_save) { _, _ ->
                val label = labelInput.text?.toString().orEmpty().trim()
                val promptHint = promptInput.text?.toString().orEmpty().trim()
                if (label.isBlank() || promptHint.isBlank()) {
                    Toast.makeText(requireContext(), R.string.developer_ai_input_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val next = OemDeveloperAiTonePreset(label = label, promptHint = promptHint)
                if (existingIndex == null) {
                    tonePresets.add(next)
                } else {
                    tonePresets[existingIndex] = next
                }
                renderToneList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveConfig() {
        val replyCount = parseInt(
            replyCountInput,
            allowEmpty = true,
            min = OemDeveloperAiConfigDefaults.MIN_REPLY_COUNT,
            max = OemDeveloperAiConfigDefaults.MAX_REPLY_COUNT,
        )
        val minChars = parseInt(minCharsInput, allowEmpty = true, min = 1, max = 200)
        val maxChars = parseInt(maxCharsInput, allowEmpty = true, min = minChars ?: 1, max = 400)
        val config = OemDeveloperAiReplyConfig(
            systemPrompt = systemPromptInput?.text?.toString().orEmpty().trim(),
            tonePresets = tonePresets.toList(),
            replyCount = replyCount,
            replyLengthPolicy = replyLengthPolicyInput?.text?.toString().orEmpty().trim(),
            businessRole = businessRoleInput?.text?.toString().orEmpty().trim(),
            minChars = minChars,
            maxChars = maxChars,
            allowEmoji = allowEmojiSwitch?.isChecked ?: OemDeveloperAiConfigDefaults.DEFAULT_ALLOW_EMOJI,
            avoidNickname = avoidNicknameSwitch?.isChecked ?: OemDeveloperAiConfigDefaults.DEFAULT_AVOID_NICKNAME,
            language = languageInput?.text?.toString().orEmpty().trim(),
        )
        OemDeveloperAiConfigStore.saveReplyConfig(requireContext(), config)
        Toast.makeText(requireContext(), R.string.developer_ai_reply_save_done, Toast.LENGTH_SHORT).show()
    }

    private fun parseInt(
        input: EditText?,
        allowEmpty: Boolean,
        min: Int,
        max: Int,
    ): Int? {
        val raw = input?.text?.toString().orEmpty().trim()
        if (raw.isEmpty()) {
            return if (allowEmpty) {
                null
            } else {
                showInvalidInput()
                null
            }
        }
        val value = raw.toIntOrNull()
        if (value == null || value < min || value > max) {
            showInvalidInput()
            return null
        }
        return value
    }

    private fun showInvalidInput() {
        Toast.makeText(requireContext(), R.string.developer_ai_input_invalid, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
