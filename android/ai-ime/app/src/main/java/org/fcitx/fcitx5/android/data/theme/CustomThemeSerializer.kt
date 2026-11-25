/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.theme

import arrow.core.compose
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.fcitx.fcitx5.android.utils.NostalgicSerializer

object CustomThemeSerializer : JsonTransformingSerializer<Theme.Custom>(Theme.Custom.serializer()) {

    const val MAX_SKIN_ENGINE = 9

    val supportedPackageVersions: Set<String> = setOf("2.4", "2.5", "2.6", "2.7", "2.8", "2.9", "2.10", "2.11", "2.12", "2.13", "2.14", "2.15", "2.16", CURRENT_VERSION)

    val WithMigrationStatus = NostalgicSerializer(this) {
        it.jsonObject[VERSION]?.jsonPrimitive?.content !in supportedPackageVersions
    }

    override fun transformSerialize(element: JsonElement): JsonElement =
        element.jsonObject.addVersion()

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val required = element.jsonObject["minimumSkinEngine"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        require(required in 1..MAX_SKIN_ENGINE) { "此皮肤需要更新泡泡输入法" }
        val version = element.jsonObject[VERSION]?.let {
            val version = it.jsonPrimitive.content
            if (version !in knownVersions)
                error("$version is not in known versions: $knownVersions")
            version
        } ?: FALLBACK_VERSION
        val migrated = applyStrategy(version, element.jsonObject).removeVersion()
        // An invalid optional sound contract must not disable an otherwise usable visual skin.
        val sound = migrated["skinSound"]
        return if (sound != null && sound != JsonNull && runCatching {
            decodeSkinSound(sound)
        }.isFailure) JsonObject(migrated - "skinSound") else migrated
    }

    private fun JsonObject.addVersion(): JsonObject {
        fun needsNewRenderer(value: JsonElement): Boolean = value is JsonObject && value.any { (key, child) ->
            (key in setOf("renderStyle", "sidebarImage", "keyImageOverrides", "toolbarIcons", "slice", "patternFill") &&
                child != JsonNull && child != JsonObject(emptyMap())) || needsNewRenderer(child)
        }
        fun needsContentRenderer(value: JsonElement): Boolean = value is JsonObject && value.any { (key, child) ->
            (key in setOf("keyForegrounds", "keyContentInsets", "keyIconSizeDp", "keyForegroundWidthDp", "keyForegroundHeightDp", "toolbarIconSizeDp") &&
                child != JsonNull && child != JsonObject(emptyMap())) || needsContentRenderer(child)
        }
        val needsRevisionRenderer = this["revision"] != null ||
            (this["renderStyle"] as? JsonObject)?.get("functionKeyTextSizeDp")?.let { it != JsonNull } == true
        val needsEffectRenderer = (this["renderStyle"] as? JsonObject)?.get("keyPressEffect")?.let { it != JsonNull } == true ||
            (this["keyColorOverrides"] as? JsonObject)?.isNotEmpty() == true ||
            listOf("keyImageOverrides", "keyForegrounds").any { field ->
                (this[field] as? JsonObject)?.keys?.any { '/' in it && ':' !in it.substringBefore('/') } == true
            }
        val style = this["renderStyle"] as? JsonObject
        val needsLayoutRenderer = listOf("layout", "keyTextBold").any { style?.get(it)?.let { value -> value != JsonNull } == true } ||
            (style?.get("keyForegroundSizes") as? JsonObject)?.isNotEmpty() == true
        val needsDecorationRenderer = (this["keyDecorations"] as? JsonObject)?.isNotEmpty() == true
        val needsStateRenderer = listOf("qwertyBottomRowWidths", "toolbarHeightDpByLayout",
            "keyVisualHorizontalInsetDpByLayout", "keyVisualVerticalInsetDpByLayout")
            .any { (style?.get(it) as? JsonObject)?.isNotEmpty() == true } ||
            (this["keyForegrounds"] as? JsonObject)?.keys?.any { '@' in it } == true
        val extendedWidths = (style?.get("qwertyBottomRowWidths") as? JsonObject)?.keys
            ?.any { it !in setOf("role:language", "role:enter") } == true
        val ambient = listOf("ambientSpaceEffect", "ambientSidebarEffect").any { style?.get(it)?.let { value -> value != JsonNull } == true } || style?.get("equalToolbarSlots")?.jsonPrimitive?.content == "true" || (style?.get("keyPressEffect") as? JsonObject)?.get("releaseDurationMs")?.jsonPrimitive?.content?.toIntOrNull()?.let { it > 0 } == true
        val effect = style?.get("keyPressEffect") as? JsonObject
        val needsRippleRenderer = style?.get("keyFontFamily")?.let { it != JsonNull } == true ||
            style?.get("keyPressScale")?.jsonPrimitive?.content?.toFloatOrNull()?.let { it != 1f } == true ||
            effect?.get("rippleCount")?.jsonPrimitive?.content?.toIntOrNull()?.let { it > 0 } == true ||
            effect?.get("rippleStyle")?.jsonPrimitive?.content == "water" ||
            effect?.get("particleShape")?.jsonPrimitive?.content == "mixed"
        val motionDefaults = mapOf("motion" to "expand", "speed" to "1.0", "rotationTurns" to "1.0",
            "turbulenceDp" to "8.0", "gravityDp" to "40.0", "trailSegments" to "0", "particleAlpha" to "1.0",
            "digitSizeDp" to "9.0", "followWindowMs" to "450", "fragmentCount" to "32",
            "fragmentMinRadians" to "0.045", "fragmentMaxRadians" to "0.525", "fragmentSpread" to "0.11", "waveDistortion" to "0.027")
        val needsMotionRenderer = effect?.get("particleShape")?.jsonPrimitive?.content == "digit" ||
            motionDefaults.any { (key, default) -> effect?.get(key)?.jsonPrimitive?.content?.let {
                if (key == "motion") it != default else it.toDoubleOrNull() != default.toDoubleOrNull()
            } == true }
        val needsTextShadowRenderer = style?.get("keyTextShadow")?.let { it != JsonNull } == true ||
            (style?.get("keyLabelCenterXByKey") as? JsonObject)?.isNotEmpty() == true ||
            style?.get("keyImageHorizontalOutsetDp")?.jsonPrimitive?.content?.toFloatOrNull()?.let { it > 0f } == true
        val declaredEngine = this["minimumSkinEngine"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val fitsDecoration = (this["keyDecorations"] as? JsonObject)?.values?.any {
            (it as? JsonObject)?.get("fitToKey")?.jsonPrimitive?.content == "true"
        } == true
        val hasReplyInset = (style?.get("quickReplyTopInsetDp")?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f) > 0f
        val newLayout = fitsDecoration || hasReplyInset
        val hasSkinSound = this["skinSound"]?.let { it != JsonNull } == true
        fun hasSourceClip(value: kotlinx.serialization.json.JsonElement): Boolean = when (value) {
            is JsonObject -> value["sourceCornerRadiusPx"]?.let { it != JsonNull } == true || value.values.any(::hasSourceClip)
            is kotlinx.serialization.json.JsonArray -> value.any(::hasSourceClip)
            else -> false
        }
        val hasAuthoredResponse = hasSourceClip(this) || (style?.get("customToolbarLayout")?.jsonPrimitive?.content == "true" && (this["toolbarTopInsetDp"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0) > 0) || listOf("keyMotion", "authoredPreeditHeightDp", "inlinePreeditHeightDp").any { style?.get(it)?.let { value -> value != JsonNull } == true } ||
            listOf("keyLabelCenterYByKey", "keyHintCenterYByKey").any { (style?.get(it) as? JsonObject)?.isNotEmpty() == true } ||
            listOf("t9ColumnWidths", "t9BottomRowWidths", "hiddenKeyHints").any { (style?.get(it) as? kotlinx.serialization.json.JsonArray)?.isNotEmpty() == true } ||
            style?.get("qwertyFiveKeyBottomRow")?.jsonPrimitive?.content == "true" ||
            (style?.get("t9SidebarContentFraction")?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f) < 1f ||
            (this["keyStateImages"] as? JsonObject)?.isNotEmpty() == true || this["preeditImage"]?.let { it != JsonNull } == true
        val hasSequenceRenderer = (this["keyAnimations"] as? JsonObject)?.isNotEmpty() == true ||
            listOf("qwertyMinHeightMatchesT9", "preeditInTopSpacing").any { style?.get(it)?.jsonPrimitive?.content == "true" } ||
            listOf("defaultKeyWidthPercent", "defaultKeyHeightPercent").any { style?.get(it)?.jsonPrimitive?.content?.toIntOrNull()?.let { n -> n != 100 } == true } ||
            style?.get("defaultDigitHints")?.jsonPrimitive?.content == "false"
        val hasPopupPalette = style?.get("popupStyle")?.let { it != JsonNull } == true
        val aboveFaces = style?.get("keyEffectAboveFaces")?.jsonPrimitive?.content == "true"
        val hasParticleSprites = (effect?.get("particleSprites") as? kotlinx.serialization.json.JsonArray)?.isNotEmpty() == true
        val minimumEngine = maxOf(declaredEngine, if (hasParticleSprites || (this["keyStateImages"] as? JsonObject)?.keys?.any { it == "t9/sidebar" || it == "number/sidebar" } == true) 9 else if (aboveFaces) 8 else if (hasPopupPalette) 7 else if (hasSequenceRenderer) 6 else if (hasAuthoredResponse) 5 else if (hasSkinSound) 4 else if (newLayout) 3 else 2)
        val needsSkinLabRenderer = hasParticleSprites || aboveFaces || hasPopupPalette || hasSequenceRenderer || hasAuthoredResponse || hasSkinSound || newLayout || declaredEngine > 1 || listOf("skinLabStandard", "numberReturnUsesFunctionKey", "customKeyFaceInsets", "preserveToolbarArtwork", "preeditTextColor", "preeditShadowColor").any { style?.get(it)?.let { value -> value != JsonNull } == true }
        val version = if (needsSkinLabRenderer) CURRENT_VERSION else if (needsTextShadowRenderer) "2.16" else if (needsMotionRenderer) "2.15" else if (needsRippleRenderer) "2.14" else if (ambient) "2.13" else if (extendedWidths) "2.12" else if (needsStateRenderer) "2.11" else if (needsDecorationRenderer) "2.10" else if (needsLayoutRenderer) "2.9" else if (needsEffectRenderer) "2.8" else if (needsRevisionRenderer) "2.7" else if (needsContentRenderer(this)) "2.6" else if (needsNewRenderer(this)) "2.5" else "2.4"
        return JsonObject((this - "minimumSkinEngine") + (VERSION to JsonPrimitive(version)) + if (version == CURRENT_VERSION) mapOf("minimumSkinEngine" to JsonPrimitive(minimumEngine)) else emptyMap())
    }

    private fun JsonObject.removeVersion() =
        JsonObject(this - VERSION)

    private val EmptyTransform: (JsonObject) -> JsonObject = { it }

    private fun applyStrategy(oldVersion: String, obj: JsonObject) =
        strategies
            .takeWhile { it.version != oldVersion }
            .foldRight(EmptyTransform) { it, acc -> it.transformation compose acc }
            .invoke(obj)

    data class MigrationStrategy(
        val version: String,
        val transformation: (JsonObject) -> JsonObject
    )

    private val strategies: List<MigrationStrategy> =
        // Add migrations here
        listOf(
            MigrationStrategy("2.14", EmptyTransform),
            MigrationStrategy("2.15", EmptyTransform),
            MigrationStrategy("2.13", EmptyTransform),
            MigrationStrategy("2.12", EmptyTransform),
            MigrationStrategy("2.11", EmptyTransform),
            MigrationStrategy("2.10", EmptyTransform),
            MigrationStrategy("2.9", EmptyTransform),
            MigrationStrategy("2.8", EmptyTransform),
            MigrationStrategy("2.7", EmptyTransform),
            MigrationStrategy("2.6", EmptyTransform),
            MigrationStrategy("2.5", EmptyTransform),
            MigrationStrategy("2.4", EmptyTransform),
            MigrationStrategy("2.3", EmptyTransform),
            MigrationStrategy("2.2", EmptyTransform),
            MigrationStrategy("2.1") {
                JsonObject(it.toMutableMap().apply {
                    put("candidateTextColor", getValue("keyTextColor"))
                    put("candidateLabelColor", getValue("keyTextColor"))
                    put("candidateCommentColor", getValue("altKeyTextColor"))
                })
            },
            MigrationStrategy("2.0") {
                JsonObject(it.toMutableMap().apply {
                    if (get("backgroundImage") != null) {
                        val popupBkgColor = if (getValue("isDark").jsonPrimitive.boolean) {
                            ThemePreset.PixelDark.popupBackgroundColor
                        } else {
                            ThemePreset.PixelLight.popupBackgroundColor
                        }
                        put("popupBackgroundColor", JsonPrimitive(popupBkgColor))
                        put("popupTextColor", getValue("keyTextColor"))
                        put("genericActiveBackgroundColor", getValue("accentKeyBackgroundColor"))
                        put("genericActiveForegroundColor", getValue("accentKeyTextColor"))
                    } else {
                        put("popupBackgroundColor", getValue("barColor"))
                        put("popupTextColor", getValue("keyTextColor"))
                        put("genericActiveBackgroundColor", getValue("accentKeyBackgroundColor"))
                        put("genericActiveForegroundColor", getValue("accentKeyTextColor"))
                    }
                })
            },
            MigrationStrategy("1.0", EmptyTransform)
        )

    private const val VERSION = "version"

    private const val CURRENT_VERSION = "2.15"
    private const val FALLBACK_VERSION = "1.0"

    private val knownVersions = strategies.map { it.version }

}
