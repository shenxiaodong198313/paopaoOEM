package com.paopaoime.aikeyboard.oem.debug

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class OemDeveloperAiTonePreset(
    val label: String,
    val promptHint: String,
)

data class OemDeveloperAiModelConfig(
    val apiBaseUrl: String = "",
    val aiOpenAiBaseUrl: String = "",
    val aiOpenAiApiKey: String = "",
    val aiOpenAiModel: String = "",
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
)

data class OemDeveloperAiReplyConfig(
    val systemPrompt: String = "",
    val tonePresets: List<OemDeveloperAiTonePreset> = emptyList(),
    val replyCount: Int? = null,
    val replyLengthPolicy: String = "",
    val businessRole: String = "",
    val minChars: Int? = null,
    val maxChars: Int? = null,
    val allowEmoji: Boolean? = null,
    val avoidNickname: Boolean? = null,
    val language: String = "",
)

data class OemDeveloperAiConfigSnapshot(
    val modelConfig: OemDeveloperAiModelConfig = OemDeveloperAiModelConfig(),
    val replyConfig: OemDeveloperAiReplyConfig = OemDeveloperAiReplyConfig(),
)

data class OemDeveloperResolvedAiReplyConfig(
    val systemPrompt: String,
    val tonePresets: List<OemDeveloperAiTonePreset>,
    val replyCount: Int,
    val replyLengthPolicy: String,
    val businessRole: String,
    val minChars: Int,
    val maxChars: Int,
    val allowEmoji: Boolean,
    val avoidNickname: Boolean,
    val language: String,
)

object OemDeveloperAiConfigDefaults {
    const val DEFAULT_TEMPERATURE = 0.0
    const val DEFAULT_TOP_P = 0.85
    const val DEFAULT_MAX_TOKENS = 1100
    const val DEFAULT_REPLY_COUNT = 10
    const val MIN_REPLY_COUNT = 1
    const val MAX_REPLY_COUNT = 20
    const val DEFAULT_MIN_CHARS = 6
    const val DEFAULT_MAX_CHARS = 22
    const val DEFAULT_REPLY_LENGTH_POLICY = "short_reply"
    const val DEFAULT_BUSINESS_ROLE = "IM 沟通回复助手"
    const val DEFAULT_LANGUAGE = "zh-CN"
    const val DEFAULT_ALLOW_EMOJI = true
    const val DEFAULT_AVOID_NICKNAME = true

    val DEFAULT_TONE_PRESETS: List<OemDeveloperAiTonePreset> =
        listOf(
            OemDeveloperAiTonePreset(
                label = "礼貌回复",
                promptHint = "语气礼貌、克制、清晰，适合商务或不太熟的关系，不要显得生硬。",
            ),
            OemDeveloperAiTonePreset(
                label = "自然沟通",
                promptHint = "语气自然、场景通用、像真人即时回复，不油腻，不端着。",
            ),
            OemDeveloperAiTonePreset(
                label = "推动成交",
                promptHint = "在自然礼貌前提下更偏向推进沟通、确认意向、促成下一步，但不要压迫感太强。",
            ),
        )
}

object OemDeveloperAiConfigStore {
    private const val STORE_FILE_NAME = "oem_developer_ai_config.json"

    private const val JSON_MODEL_CONFIG = "modelConfig"
    private const val JSON_REPLY_CONFIG = "replyConfig"

    private const val JSON_API_BASE_URL = "apiBaseUrl"
    private const val JSON_AI_OPENAI_BASE_URL = "aiOpenAiBaseUrl"
    private const val JSON_AI_OPENAI_API_KEY = "aiOpenAiApiKey"
    private const val JSON_AI_OPENAI_MODEL = "aiOpenAiModel"
    private const val JSON_TEMPERATURE = "temperature"
    private const val JSON_TOP_P = "topP"
    private const val JSON_MAX_TOKENS = "maxTokens"

    private const val JSON_SYSTEM_PROMPT = "systemPrompt"
    private const val JSON_TONE_PRESETS = "tonePresets"
    private const val JSON_TONE_LABEL = "label"
    private const val JSON_TONE_PROMPT_HINT = "promptHint"
    private const val JSON_REPLY_COUNT = "replyCount"
    private const val JSON_REPLY_LENGTH_POLICY = "replyLengthPolicy"
    private const val JSON_BUSINESS_ROLE = "businessRole"
    private const val JSON_MIN_CHARS = "minChars"
    private const val JSON_MAX_CHARS = "maxChars"
    private const val JSON_ALLOW_EMOJI = "allowEmoji"
    private const val JSON_AVOID_NICKNAME = "avoidNickname"
    private const val JSON_LANGUAGE = "language"

    private val fileLock = Any()

    fun load(context: Context): OemDeveloperAiConfigSnapshot {
        synchronized(fileLock) {
            val file = storeFile(context)
            if (!file.exists()) {
                return OemDeveloperAiConfigSnapshot()
            }
            return runCatching {
                parseSnapshot(file.readText())
            }.getOrElse {
                OemDeveloperAiConfigSnapshot()
            }
        }
    }

    fun saveModelConfig(context: Context, config: OemDeveloperAiModelConfig) {
        synchronized(fileLock) {
            val current = load(context)
            writeSnapshot(context, current.copy(modelConfig = normalizeModelConfig(config)))
        }
    }

    fun saveReplyConfig(context: Context, config: OemDeveloperAiReplyConfig) {
        synchronized(fileLock) {
            val current = load(context)
            writeSnapshot(context, current.copy(replyConfig = normalizeReplyConfig(config)))
        }
    }

    fun clearModelConfig(context: Context) {
        synchronized(fileLock) {
            val current = load(context)
            writeSnapshot(context, current.copy(modelConfig = OemDeveloperAiModelConfig()))
        }
    }

    fun clearReplyConfig(context: Context) {
        synchronized(fileLock) {
            val current = load(context)
            writeSnapshot(context, current.copy(replyConfig = OemDeveloperAiReplyConfig()))
        }
    }

    fun resolveReplyConfig(context: Context): OemDeveloperResolvedAiReplyConfig {
        val raw = load(context).replyConfig
        val businessRole = raw.businessRole.trim().ifBlank { OemDeveloperAiConfigDefaults.DEFAULT_BUSINESS_ROLE }
        val language = raw.language.trim().ifBlank { OemDeveloperAiConfigDefaults.DEFAULT_LANGUAGE }
        val minChars = (raw.minChars ?: OemDeveloperAiConfigDefaults.DEFAULT_MIN_CHARS)
            .coerceAtLeast(1)
        val maxChars = (raw.maxChars ?: OemDeveloperAiConfigDefaults.DEFAULT_MAX_CHARS)
            .coerceAtLeast(minChars)
        val replyCount = (raw.replyCount ?: OemDeveloperAiConfigDefaults.DEFAULT_REPLY_COUNT)
            .coerceIn(
                OemDeveloperAiConfigDefaults.MIN_REPLY_COUNT,
                OemDeveloperAiConfigDefaults.MAX_REPLY_COUNT,
            )
        val tonePresets = raw.tonePresets.filter { it.label.isNotBlank() && it.promptHint.isNotBlank() }
            .ifEmpty { OemDeveloperAiConfigDefaults.DEFAULT_TONE_PRESETS }
        return OemDeveloperResolvedAiReplyConfig(
            systemPrompt = raw.systemPrompt.trim().ifBlank {
                defaultSystemPrompt(
                    businessRole = businessRole,
                    language = language,
                )
            },
            tonePresets = tonePresets,
            replyCount = replyCount,
            replyLengthPolicy = raw.replyLengthPolicy.trim().ifBlank {
                OemDeveloperAiConfigDefaults.DEFAULT_REPLY_LENGTH_POLICY
            },
            businessRole = businessRole,
            minChars = minChars,
            maxChars = maxChars,
            allowEmoji = raw.allowEmoji ?: OemDeveloperAiConfigDefaults.DEFAULT_ALLOW_EMOJI,
            avoidNickname = raw.avoidNickname ?: OemDeveloperAiConfigDefaults.DEFAULT_AVOID_NICKNAME,
            language = language,
        )
    }

    fun defaultSystemPrompt(
        businessRole: String = OemDeveloperAiConfigDefaults.DEFAULT_BUSINESS_ROLE,
        language: String = OemDeveloperAiConfigDefaults.DEFAULT_LANGUAGE,
    ): String {
        val displayLanguage = when (language.trim().lowercase()) {
            "zh", "zh-cn", "zh_hans", "chinese" -> "中文"
            "en", "en-us", "english" -> "英文"
            else -> language.ifBlank { "中文" }
        }
        return "你是$businessRole。你只输出可直接发送的${displayLanguage}回复候选，不解释，不加分析，不输出标题。"
    }

    private fun storeFile(context: Context): File =
        File(context.applicationContext.filesDir, STORE_FILE_NAME)

    private fun writeSnapshot(context: Context, snapshot: OemDeveloperAiConfigSnapshot) {
        val file = storeFile(context)
        file.parentFile?.mkdirs()
        file.writeText(writeSnapshotJson(snapshot).toString())
    }

    private fun parseSnapshot(raw: String): OemDeveloperAiConfigSnapshot {
        val json = JSONObject(raw)
        return OemDeveloperAiConfigSnapshot(
            modelConfig = readModelConfig(json.optJSONObject(JSON_MODEL_CONFIG)),
            replyConfig = readReplyConfig(json.optJSONObject(JSON_REPLY_CONFIG)),
        )
    }

    private fun writeSnapshotJson(snapshot: OemDeveloperAiConfigSnapshot): JSONObject {
        return JSONObject()
            .put(JSON_MODEL_CONFIG, writeModelConfig(snapshot.modelConfig))
            .put(JSON_REPLY_CONFIG, writeReplyConfig(snapshot.replyConfig))
    }

    private fun readModelConfig(json: JSONObject?): OemDeveloperAiModelConfig {
        if (json == null) return OemDeveloperAiModelConfig()
        return OemDeveloperAiModelConfig(
            apiBaseUrl = json.optString(JSON_API_BASE_URL).trim(),
            aiOpenAiBaseUrl = json.optString(JSON_AI_OPENAI_BASE_URL).trim(),
            aiOpenAiApiKey = json.optString(JSON_AI_OPENAI_API_KEY).trim(),
            aiOpenAiModel = json.optString(JSON_AI_OPENAI_MODEL).trim(),
            temperature = json.optDoubleOrNull(JSON_TEMPERATURE),
            topP = json.optDoubleOrNull(JSON_TOP_P),
            maxTokens = json.optIntOrNull(JSON_MAX_TOKENS),
        )
    }

    private fun writeModelConfig(config: OemDeveloperAiModelConfig): JSONObject {
        return JSONObject().apply {
            put(JSON_API_BASE_URL, config.apiBaseUrl.trim())
            put(JSON_AI_OPENAI_BASE_URL, config.aiOpenAiBaseUrl.trim())
            put(JSON_AI_OPENAI_API_KEY, config.aiOpenAiApiKey.trim())
            put(JSON_AI_OPENAI_MODEL, config.aiOpenAiModel.trim())
            putNullable(JSON_TEMPERATURE, config.temperature)
            putNullable(JSON_TOP_P, config.topP)
            putNullable(JSON_MAX_TOKENS, config.maxTokens)
        }
    }

    private fun readReplyConfig(json: JSONObject?): OemDeveloperAiReplyConfig {
        if (json == null) return OemDeveloperAiReplyConfig()
        return OemDeveloperAiReplyConfig(
            systemPrompt = json.optString(JSON_SYSTEM_PROMPT).trim(),
            tonePresets = readTonePresets(json.optJSONArray(JSON_TONE_PRESETS)),
            replyCount = json.optIntOrNull(JSON_REPLY_COUNT),
            replyLengthPolicy = json.optString(JSON_REPLY_LENGTH_POLICY).trim(),
            businessRole = json.optString(JSON_BUSINESS_ROLE).trim(),
            minChars = json.optIntOrNull(JSON_MIN_CHARS),
            maxChars = json.optIntOrNull(JSON_MAX_CHARS),
            allowEmoji = json.optBooleanOrNull(JSON_ALLOW_EMOJI),
            avoidNickname = json.optBooleanOrNull(JSON_AVOID_NICKNAME),
            language = json.optString(JSON_LANGUAGE).trim(),
        )
    }

    private fun writeReplyConfig(config: OemDeveloperAiReplyConfig): JSONObject {
        return JSONObject().apply {
            put(JSON_SYSTEM_PROMPT, config.systemPrompt.trim())
            put(JSON_TONE_PRESETS, JSONArray(writeTonePresets(config.tonePresets)))
            putNullable(JSON_REPLY_COUNT, config.replyCount)
            put(JSON_REPLY_LENGTH_POLICY, config.replyLengthPolicy.trim())
            put(JSON_BUSINESS_ROLE, config.businessRole.trim())
            putNullable(JSON_MIN_CHARS, config.minChars)
            putNullable(JSON_MAX_CHARS, config.maxChars)
            putNullable(JSON_ALLOW_EMOJI, config.allowEmoji)
            putNullable(JSON_AVOID_NICKNAME, config.avoidNickname)
            put(JSON_LANGUAGE, config.language.trim())
        }
    }

    private fun readTonePresets(array: JSONArray?): List<OemDeveloperAiTonePreset> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val label = item.optString(JSON_TONE_LABEL).trim()
                val promptHint = item.optString(JSON_TONE_PROMPT_HINT).trim()
                if (label.isNotBlank() && promptHint.isNotBlank()) {
                    add(
                        OemDeveloperAiTonePreset(
                            label = label,
                            promptHint = promptHint,
                        ),
                    )
                }
            }
        }
    }

    private fun writeTonePresets(list: List<OemDeveloperAiTonePreset>): List<JSONObject> {
        return list.mapNotNull { preset ->
            val label = preset.label.trim()
            val promptHint = preset.promptHint.trim()
            if (label.isBlank() || promptHint.isBlank()) {
                null
            } else {
                JSONObject()
                    .put(JSON_TONE_LABEL, label)
                    .put(JSON_TONE_PROMPT_HINT, promptHint)
            }
        }
    }

    private fun normalizeModelConfig(config: OemDeveloperAiModelConfig): OemDeveloperAiModelConfig {
        return config.copy(
            apiBaseUrl = config.apiBaseUrl.trim(),
            aiOpenAiBaseUrl = config.aiOpenAiBaseUrl.trim(),
            aiOpenAiApiKey = config.aiOpenAiApiKey.trim(),
            aiOpenAiModel = config.aiOpenAiModel.trim(),
            temperature = config.temperature,
            topP = config.topP,
            maxTokens = config.maxTokens?.coerceAtLeast(1),
        )
    }

    private fun normalizeReplyConfig(config: OemDeveloperAiReplyConfig): OemDeveloperAiReplyConfig {
        val minChars = config.minChars?.coerceAtLeast(1)
        val maxChars = config.maxChars?.coerceAtLeast(minChars ?: 1)
        return config.copy(
            systemPrompt = config.systemPrompt.trim(),
            tonePresets = config.tonePresets
                .map {
                    OemDeveloperAiTonePreset(
                        label = it.label.trim(),
                        promptHint = it.promptHint.trim(),
                    )
                }
                .filter { it.label.isNotBlank() && it.promptHint.isNotBlank() },
            replyCount = config.replyCount?.coerceIn(
                OemDeveloperAiConfigDefaults.MIN_REPLY_COUNT,
                OemDeveloperAiConfigDefaults.MAX_REPLY_COUNT,
            ),
            replyLengthPolicy = config.replyLengthPolicy.trim(),
            businessRole = config.businessRole.trim(),
            minChars = minChars,
            maxChars = maxChars,
            language = config.language.trim(),
        )
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) {
            put(key, JSONObject.NULL)
        } else {
            put(key, value)
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key)
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return optBoolean(key)
    }
}
