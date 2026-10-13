package com.paopaoime.aikeyboard.ime.ui.fragment

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperAiConfigDefaults
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperAiConfigStore
import com.paopaoime.aikeyboard.oem.debug.OemDeveloperAiModelConfig

class DeveloperAiModelConfigFragment : BaseInputMethodSettingsFragment(
    R.layout.fragment_developer_ai_model_config,
) {
    private var apiBaseUrlInput: EditText? = null
    private var aiOpenAiBaseUrlInput: EditText? = null
    private var aiOpenAiApiKeyInput: EditText? = null
    private var aiOpenAiModelInput: EditText? = null
    private var temperatureInput: EditText? = null
    private var topPInput: EditText? = null
    private var maxTokensInput: EditText? = null
    private var summaryView: TextView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindHeader(view, R.id.layout_developer_ai_model_header, R.id.btn_developer_ai_model_back)
        applyCardShadow(
            view,
            intArrayOf(
                R.id.card_developer_ai_model_intro,
                R.id.card_developer_ai_model_fields,
                R.id.card_developer_ai_model_actions,
            ),
        )
        apiBaseUrlInput = view.findViewById(R.id.et_developer_api_base_url)
        aiOpenAiBaseUrlInput = view.findViewById(R.id.et_developer_ai_openai_base_url)
        aiOpenAiApiKeyInput = view.findViewById(R.id.et_developer_ai_openai_api_key)
        aiOpenAiModelInput = view.findViewById(R.id.et_developer_ai_openai_model)
        temperatureInput = view.findViewById(R.id.et_developer_ai_temperature)
        topPInput = view.findViewById(R.id.et_developer_ai_top_p)
        maxTokensInput = view.findViewById(R.id.et_developer_ai_max_tokens)
        summaryView = view.findViewById(R.id.tv_developer_ai_model_summary)

        view.findViewById<View>(R.id.btn_developer_ai_model_save)?.setOnClickListener {
            saveConfig()
        }
        view.findViewById<View>(R.id.btn_developer_ai_model_reset)?.setOnClickListener {
            OemDeveloperAiConfigStore.clearModelConfig(requireContext())
            bindConfig(OemDeveloperAiModelConfig())
            Toast.makeText(requireContext(), R.string.developer_ai_model_reset_done, Toast.LENGTH_SHORT).show()
        }
        bindConfig(OemDeveloperAiConfigStore.load(requireContext()).modelConfig)
    }

    private fun bindConfig(config: OemDeveloperAiModelConfig) {
        apiBaseUrlInput?.setText(config.apiBaseUrl)
        aiOpenAiBaseUrlInput?.setText(config.aiOpenAiBaseUrl)
        aiOpenAiApiKeyInput?.setText(config.aiOpenAiApiKey)
        aiOpenAiModelInput?.setText(config.aiOpenAiModel)
        temperatureInput?.setText(config.temperature?.toString().orEmpty())
        topPInput?.setText(config.topP?.toString().orEmpty())
        maxTokensInput?.setText(config.maxTokens?.toString().orEmpty())
        summaryView?.text = getString(
            R.string.developer_ai_model_summary_template,
            OemDeveloperAiConfigDefaults.DEFAULT_TEMPERATURE.toString(),
            OemDeveloperAiConfigDefaults.DEFAULT_TOP_P.toString(),
            OemDeveloperAiConfigDefaults.DEFAULT_MAX_TOKENS,
        )
    }

    private fun saveConfig() {
        val temperature = parseDouble(temperatureInput, allowEmpty = true, min = 0.0, max = 2.0)
            ?: return
        val topP = parseDouble(topPInput, allowEmpty = true, min = 0.0, max = 1.0)
            ?: return
        val maxTokens = parseInt(maxTokensInput, allowEmpty = true, min = 1, max = 32000)
            ?: return
        val config = OemDeveloperAiModelConfig(
            apiBaseUrl = apiBaseUrlInput?.text?.toString().orEmpty().trim(),
            aiOpenAiBaseUrl = aiOpenAiBaseUrlInput?.text?.toString().orEmpty().trim(),
            aiOpenAiApiKey = aiOpenAiApiKeyInput?.text?.toString().orEmpty().trim(),
            aiOpenAiModel = aiOpenAiModelInput?.text?.toString().orEmpty().trim(),
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
        )
        OemDeveloperAiConfigStore.saveModelConfig(requireContext(), config)
        Toast.makeText(requireContext(), R.string.developer_ai_model_save_done, Toast.LENGTH_SHORT).show()
    }

    private fun parseDouble(
        input: EditText?,
        allowEmpty: Boolean,
        min: Double,
        max: Double,
    ): Double? {
        val raw = input?.text?.toString().orEmpty().trim()
        if (raw.isEmpty()) {
            return if (allowEmpty) {
                null
            } else {
                showInvalidInput()
                null
            }
        }
        val value = raw.toDoubleOrNull()
        if (value == null || value < min || value > max) {
            showInvalidInput()
            return null
        }
        return value
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
}
