/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.status

/** Logical keyboard choices stay stable when their phonetic dictionary changes. */
object PinyinDictionaryRouting {
    const val DEFAULT_PROFILE = "default"
    const val FROST_PROFILE = "frost"
    const val ICE_PROFILE = "ice"
    const val FROST_PINYIN_SCHEMA = "paopao_frost_pinyin"
    const val FROST_T9_SCHEMA = "paopao_frost_t9"
    const val ICE_PINYIN_SCHEMA = "paopao_ice_pinyin"
    const val ICE_T9_SCHEMA = "paopao_ice_t9"
    const val DEFAULT_FROST_DOUBLE_PINYIN_SCHEMA = "paopao_frost_double_pinyin"

    data class DoublePinyinSchema(val id: String, val name: String)

    val frostDoublePinyinSchemas = listOf(
        DoublePinyinSchema(DEFAULT_FROST_DOUBLE_PINYIN_SCHEMA, "自然码"),
        DoublePinyinSchema("paopao_frost_double_pinyin_flypy", "小鹤双拼"),
        DoublePinyinSchema("paopao_frost_double_pinyin_abc", "智能 ABC"),
        DoublePinyinSchema("paopao_frost_double_pinyin_mspy", "微软双拼"),
        DoublePinyinSchema("paopao_frost_double_pinyin_sogou", "搜狗双拼"),
        DoublePinyinSchema("paopao_frost_double_pinyin_ziguang", "紫光双拼")
    )
    val iceDoublePinyinSchemas = frostDoublePinyinSchemas.map {
        it.copy(id = it.id.replace("paopao_frost_", "paopao_ice_"))
    }
    private val frostDoublePinyinIds = frostDoublePinyinSchemas.map { it.id }.toSet()
    private val iceDoublePinyinIds = iceDoublePinyinSchemas.map { it.id }.toSet()
    private val fullKeyboardChineseIds = setOf("pinyin", "shuangpin", "wbx", "wbpy")

    data class Route(val logicalIme: String, val nativeIme: String, val schemaId: String? = null)

    /** Japanese needs an optional dictionary; no download is offered in this release. */
    fun bundledExtendedKeyboard(value: String): String = when (value) {
        "japanese_romaji", "japanese_kana" -> ""
        else -> value
    }

    fun normalizeProfile(value: String?): String = when (value) {
        DEFAULT_PROFILE, FROST_PROFILE, ICE_PROFILE -> value
        else -> FROST_PROFILE
    }

    fun usesRimeForPinyin(profile: String?): Boolean = normalizeProfile(profile) != DEFAULT_PROFILE

    fun schemasForProfile(profile: String?): List<String> = when (normalizeProfile(profile)) {
        FROST_PROFILE -> listOf(FROST_PINYIN_SCHEMA, FROST_T9_SCHEMA) + frostDoublePinyinSchemas.map { it.id }
        ICE_PROFILE -> listOf(ICE_PINYIN_SCHEMA, ICE_T9_SCHEMA) + iceDoublePinyinSchemas.map { it.id }
        else -> emptyList()
    }

    fun normalizeFrostDoublePinyinSchema(value: String?): String =
        value?.takeIf { it in frostDoublePinyinIds } ?: DEFAULT_FROST_DOUBLE_PINYIN_SCHEMA

    fun frostDoublePinyinSchemaForMode(mode: String?): String = when (mode) {
        "flypy", "abc", "mspy", "sogou", "ziguang" -> "paopao_frost_double_pinyin_$mode"
        else -> DEFAULT_FROST_DOUBLE_PINYIN_SCHEMA
    }

    fun frostDoublePinyinMode(schemaId: String?): String =
        normalizeFrostDoublePinyinSchema(schemaId).removePrefix("paopao_frost_double_pinyin")
            .removePrefix("_").ifEmpty { "natural" }

    /** The existing full Frost ID remains the persisted spelling-mode choice for both dictionaries. */
    fun doublePinyinSchema(profile: String?, storedFrostSchema: String?): String {
        val frostSchema = normalizeFrostDoublePinyinSchema(storedFrostSchema)
        return if (normalizeProfile(profile) == ICE_PROFILE) {
            frostSchema.replace("paopao_frost_", "paopao_ice_")
        } else frostSchema
    }

    fun normalizeFullKeyboardIme(value: String?): String =
        value?.takeIf { it in fullKeyboardChineseIds } ?: "pinyin"

    fun isFrostSchema(schemaId: String): Boolean =
        schemaId == FROST_PINYIN_SCHEMA || schemaId == FROST_T9_SCHEMA || schemaId in frostDoublePinyinIds

    fun confirmsSchema(requested: String, actual: String?, errorCode: String?): Boolean =
        errorCode.isNullOrEmpty() && actual == requested

    fun confirmsSchemaResponse(
        requestedSchema: String, actualSchema: String?, errorCode: String?,
        requestId: String, responseId: String?
    ): Boolean = requestId.isNotEmpty() && requestId == responseId &&
        confirmsSchema(requestedSchema, actualSchema, errorCode)

    /** Five-stroke and English keep their native engines under every dictionary profile. */
    fun route(logicalIme: String, profile: String?, storedDoublePinyinSchema: String?): Route {
        if (logicalIme == "spanish") return Route(logicalIme, "keyboard-es")
        if (logicalIme == "japanese_romaji" || logicalIme == "japanese_kana")
            return Route(logicalIme, "rime", "paopao_japanese")
        if (logicalIme == "pinyin14") return Route(logicalIme, "rime",
            if (normalizeProfile(profile) == ICE_PROFILE) "paopao_ice_pinyin14" else "paopao_frost_pinyin14")
        if (!usesRimeForPinyin(profile)) {
            return Route(logicalIme, logicalIme, if (logicalIme == "rime") "t9" else null)
        }
        val isIce = normalizeProfile(profile) == ICE_PROFILE
        return when (logicalIme) {
            "pinyin" -> Route(logicalIme, "rime", if (isIce) ICE_PINYIN_SCHEMA else FROST_PINYIN_SCHEMA)
            "rime" -> Route(logicalIme, "rime", if (isIce) ICE_T9_SCHEMA else FROST_T9_SCHEMA)
            "shuangpin" -> Route(logicalIme, "rime", doublePinyinSchema(profile, storedDoublePinyinSchema))
            else -> Route(logicalIme, logicalIme)
        }
    }

    /** Resolve native Rime events without changing the saved layout or Chinese keyboard. */
    fun logicalIme(nativeIme: String, schemaId: String, useT9Layout: Boolean, rememberedIme: String?): String {
        if (nativeIme != "rime") return nativeIme
        if (useT9Layout) return "rime"
        return when {
            schemaId == FROST_PINYIN_SCHEMA || schemaId == ICE_PINYIN_SCHEMA -> "pinyin"
            schemaId in frostDoublePinyinIds || schemaId in iceDoublePinyinIds -> "shuangpin"
            else -> normalizeFullKeyboardIme(rememberedIme)
        }
    }
}
