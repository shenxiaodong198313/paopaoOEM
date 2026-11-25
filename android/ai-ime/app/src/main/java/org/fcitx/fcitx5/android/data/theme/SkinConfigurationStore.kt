package org.fcitx.fcitx5.android.data.theme

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Read once, validate each stored snapshot, and isolate updates by stable installed skin name. */
class SkinConfigurationStore(private val read: () -> Map<String, *>, private val write: (String, String) -> Unit) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val values by lazy {
        read().mapNotNull { (name, raw) -> (raw as? String)?.let {
            runCatching {
                val fields = json.parseToJsonElement(it).jsonObject
                val config = json.decodeFromString<SkinConfiguration>(it)
                val legacy = (fields["backgroundDefaultsVersion"]?.jsonPrimitive?.intOrNull ?: 0) < 1
                name to if (legacy && config.gradientRange == 100) config.copy(gradientRange = 0) else config
            }.getOrNull()
        } }.toMap().toMutableMap()
    }
    private val previews = mutableMapOf<String, SkinConfiguration>()
    @Synchronized fun get(name: String): SkinConfiguration? = previews[name] ?: values[name]
    @Synchronized fun preview(name: String, config: SkinConfiguration): Boolean {
        require(name.isNotBlank() && name.length <= 240 && !name.startsWith("skin_trial_"))
        if (get(name) == config) return false
        previews[name] = config
        return true
    }
    @Synchronized fun save(name: String, config: SkinConfiguration): Boolean {
        require(name.isNotBlank() && name.length <= 240 && !name.startsWith("skin_trial_"))
        if (values[name] == config) { previews.remove(name); return false }
        val encoded = json.encodeToString(config)
        write(name, encoded)
        values[name] = config
        previews.remove(name)
        return true
    }
}
