package org.fcitx.fcitx5.android.data.theme

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
@Parcelize
data class SkinSound(
    override val version: Int,
    override val defaultId: String,
    override val roleOverrides: Map<String, String> = emptyMap(),
    override val keyOverrides: Map<String, String> = emptyMap(),
    override val fallbackId: String
) : Parcelable, SkinSoundDefinition {
    init { SkinSoundResolver.validate(this) }
}

/** Kotlin's numeric decoder permits quoted integers; the package contract does not. */
fun decodeSkinSound(element: JsonElement): SkinSound {
    require(element is JsonObject)
    val version = element["version"] as? JsonPrimitive
    require(version != null && !version.isString && version.content == "1")
    for (field in listOf("defaultId", "fallbackId")) require((element[field] as? JsonPrimitive)?.isString == true)
    for (field in listOf("roleOverrides", "keyOverrides")) element[field]?.let { mapping ->
        require(mapping is JsonObject && mapping.values.all { it is JsonPrimitive && it.isString })
    }
    return Json.decodeFromJsonElement<SkinSound>(element)
}
