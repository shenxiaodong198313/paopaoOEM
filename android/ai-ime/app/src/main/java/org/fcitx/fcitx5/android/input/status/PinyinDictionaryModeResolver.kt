/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.status

import org.fcitx.fcitx5.android.core.RawConfig

/** Preserves the active spelling keys when changing phonetic dictionary providers. */
object PinyinDictionaryModeResolver {
    class ModeResolutionException(val code: String, message: String) : IllegalStateException(message)

    private val nativeModes = mapOf(
        "Ziranma" to "natural",
        "Xiaohe" to "flypy",
        "ABC" to "abc",
        "MS" to "mspy",
        "Sogou" to "sogou",
        "Ziguang" to "ziguang",
    )

    fun requiresNativeConfig(currentProfile: String?): Boolean =
        !PinyinDictionaryRouting.usesRimeForPinyin(currentProfile)

    /** JNI returns cfg and desc siblings; translated labels are never configuration values. */
    fun nativeProfileFromConfig(response: RawConfig): String {
        val nativeProfile = response.findByName("cfg")?.findByName("ShuangpinProfile")?.value
        if (nativeProfile.isNullOrEmpty()) {
            throw ModeResolutionException("NATIVE_PROFILE_MISSING", "无法读取当前双拼方案，请稍后重试")
        }
        return nativeProfile
    }

    fun modeFromNativeConfig(response: RawConfig): String {
        return nativeModes[nativeProfileFromConfig(response)] ?: throw ModeResolutionException(
            "UNSUPPORTED_NATIVE_PROFILE",
            "当前双拼方案暂不支持切换词库，请先选择自然码、小鹤、智能 ABC、微软、搜狗或紫光双拼"
        )
    }

    fun nativeProfileForMode(mode: String): String = nativeModes.entries
        .firstOrNull { it.value == mode }?.key ?: throw ModeResolutionException(
            "UNSUPPORTED_MODE", "当前双拼方案尚不能同步到默认词库，请选择其他双拼方案"
        )

    /**
     * The default engine's actual profile wins over historical Host preference defaults.
     * Optional providers retain their saved mode, including Sogou, without consulting libime.
     * The caller supplies nativeConfig only when [requiresNativeConfig] returns true.
     */
    fun resolveForSelection(
        currentProfile: String?,
        storedFrostSchema: String?,
        nativeConfig: RawConfig? = null,
    ): String {
        if (requiresNativeConfig(currentProfile)) {
            return modeFromNativeConfig(nativeConfig ?: throw ModeResolutionException(
                "NATIVE_PROFILE_MISSING", "无法读取当前双拼方案，请稍后重试"
            ))
        }
        val savedSchema = PinyinDictionaryRouting.frostDoublePinyinSchemas
            .firstOrNull { it.id == storedFrostSchema }
            ?: throw ModeResolutionException("SAVED_MODE_INVALID", "当前双拼方案设置无效，请重新选择双拼方案")
        return PinyinDictionaryRouting.frostDoublePinyinMode(savedSchema.id)
    }
}
