package org.fcitx.fcitx5.android.input.voice

import android.content.Context
import android.util.Log
import org.fcitx.fcitx5.android.R
import org.json.JSONObject
import java.net.URI

/** Host overrides res/raw/oem_voice_config.json; immutable for the lifetime of this process. */
data class VoiceTuning(
    val numThreads: Int = 2,
    val decodingMethod: String = "greedy_search",
    val maxActivePaths: Int = 4,
    val blankPenalty: Float = 0f,
    val endpointSilenceSeconds: Float = 2.4f,
    val endpointSpeechSilenceSeconds: Float = .7f,
    val endpointMaxUtteranceSeconds: Float = 15f,
)

data class OemVoiceConfig(
    val enabled: Boolean = true,
    val defaultMode: String = "performance",
    val punctuationThreads: Int = 1,
    val maxRecordingSeconds: Int = 180,
    val silenceTimeoutSeconds: Int = 30,
    val finishTimeoutMs: Int = 4000,
    val speechRmsThreshold: Float = .01f,
    val models: Map<String, VoiceTuning> = emptyMap(),
    val downloadUrlTemplates: List<String> = DEFAULT_URLS,
    val fileUrls: Map<String, List<String>> = emptyMap(),
) {
    fun tuning(mode: String) = models[mode] ?: VoiceTuning()
    fun urls(folder: String, name: String, repo: String, revision: String): List<String> =
        fileUrls["$folder/$name"] ?: downloadUrlTemplates.map {
            it.replace("{folder}", folder).replace("{name}", name)
                .replace("{repo}", repo).replace("{revision}", revision)
        }

    companion object {
        val DEFAULT_URLS = listOf(
            "https://hf-mirror.com/{repo}/resolve/{revision}/{name}",
            "https://huggingface.co/{repo}/resolve/{revision}/{name}",
        )
        @Volatile private var instance: OemVoiceConfig? = null
        fun get(context: Context): OemVoiceConfig = instance ?: synchronized(this) {
            instance ?: runCatching {
                context.resources.openRawResource(R.raw.oem_voice_config).bufferedReader().use { parse(it.readText()) }
            }.getOrElse {
                // Never log URLs/configuration or let a malformed optional feature break typing.
                Log.e("OemVoice", "Invalid oem_voice_config; voice disabled (${it.javaClass.simpleName})")
                OemVoiceConfig(enabled = false)
            }.also { instance = it }
        }

        fun parse(raw: String): OemVoiceConfig {
            val root = JSONObject(raw)
            require(root.int("schemaVersion", 1, 1..1) == 1)
            val modes = setOf("performance", "quality", "quality_xl")
            val selected = root.optString("defaultMode", "performance").also { require(it in modes) }
            val tuning = root.optJSONObject("models") ?: JSONObject()
            require(tuning.keys().asSequence().all { it in modes })
            val models = modes.associateWith { mode ->
                val value = tuning.optJSONObject(mode) ?: JSONObject()
                val method = value.optString("decodingMethod", "greedy_search")
                require(method in setOf("greedy_search", "modified_beam_search"))
                require(mode != "performance" || method == "greedy_search") // CTC model supports greedy here.
                VoiceTuning(
                    value.int("numThreads", 2, 1..8), method,
                    value.int("maxActivePaths", 4, 1..32),
                    value.float("blankPenalty", 0f, 0f..5f),
                    value.float("endpointSilenceSeconds", 2.4f, .1f..30f),
                    value.float("endpointSpeechSilenceSeconds", .7f, .1f..10f),
                    value.float("endpointMaxUtteranceSeconds", 15f, 1f..60f),
                )
            }
            fun urlList(json: JSONObject, key: String, fallback: List<String>): List<String> {
                if (!json.has(key)) return fallback
                val array = json.getJSONArray(key)
                require(array.length() in 1..8)
                return (0 until array.length()).map { array.getString(it).also { url ->
                    val expanded = url.replace(Regex("\\{(repo|revision|folder|name)\\}"), "placeholder")
                    val uri = URI(expanded)
                    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null)
                } }
            }
            val fileUrls = root.optJSONObject("fileUrls") ?: JSONObject()
            val overrides = fileUrls.keys().asSequence().associateWith { key ->
                require(key.matches(Regex("(performance|quality|quality_xl|punctuation)/[A-Za-z0-9._-]+")))
                urlList(fileUrls, key, emptyList())
            }
            return OemVoiceConfig(
                enabled = if (root.has("enabled")) root.getBoolean("enabled") else true,
                defaultMode = selected,
                punctuationThreads = root.int("punctuationThreads", 1, 1..4),
                maxRecordingSeconds = root.int("maxRecordingSeconds", 180, 10..600),
                silenceTimeoutSeconds = root.int("silenceTimeoutSeconds", 30, 5..120),
                finishTimeoutMs = root.int("finishTimeoutMs", 4000, 500..10000),
                speechRmsThreshold = root.float("speechRmsThreshold", .01f, .001f..0.2f),
                models = models,
                downloadUrlTemplates = urlList(root, "downloadUrlTemplates", DEFAULT_URLS),
                fileUrls = overrides,
            )
        }
        private fun JSONObject.int(key: String, fallback: Int, range: IntRange): Int {
            if (!has(key)) return fallback
            val number = getDouble(key)
            require(number.isFinite() && number == number.toInt().toDouble() && number.toInt() in range)
            return number.toInt()
        }
        private fun JSONObject.float(key: String, fallback: Float, range: ClosedFloatingPointRange<Float>): Float =
            (if (has(key)) getDouble(key).toFloat() else fallback).also { require(it.isFinite() && it in range) }
    }
}
