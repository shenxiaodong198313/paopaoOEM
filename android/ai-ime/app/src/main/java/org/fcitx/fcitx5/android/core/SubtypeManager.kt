/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2023 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core

import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder
import androidx.annotation.RequiresApi
import org.fcitx.fcitx5.android.utils.InputMethodUtil
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.inputMethodManager

object SubtypeManager {

    private const val MODE_KEYBOARD = "keyboard"
    private const val DEFAULT_SYSTEM_SUBTYPE_NAME = "中文26键"

    private const val IM_PINYIN = "fcitx-pinyin"
    private const val IM_KEYBOARD = "keyboard-us"
    private const val LOCALE_ZH_CN = "zh_CN"
    private const val LOCALE_EN_US = "en_US"
    private const val LANGUAGE_TAG_ZH_CN = "zh-CN"
    private const val LANGUAGE_TAG_EN_US = "en-US"

    private val knownSubtypes: HashMap<String, InputMethodSubtype> = hashMapOf()

    fun subtypeOf(inputMethod: String): InputMethodSubtype? {
        return knownSubtypes[inputMethod]
    }

    fun inputMethodOf(subtype: InputMethodSubtype): String {
        return subtype.extraValue.ifEmpty { IM_KEYBOARD }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun syncWith(inputMethods: Array<InputMethodEntry>) {
        knownSubtypes.clear()
        val systemVisibleInputs = resolveSystemVisibleInputs(inputMethods)
        val size = systemVisibleInputs.size
        val subtypes = arrayOfNulls<InputMethodSubtype>(size)
        val hashCodes = IntArray(size)
        systemVisibleInputs.forEachIndexed { i, im ->
            val builder = InputMethodSubtypeBuilder()
                .setSubtypeId(im.uniqueName.hashCode())
                .setSubtypeExtraValue(im.uniqueName)
                .setSubtypeMode(MODE_KEYBOARD)
                .setIsAsciiCapable(im.uniqueName == IM_KEYBOARD)
            val locale = resolveSubtypeLocale(im)
            val languageTag = resolveSubtypeLanguageTag(im)
            if (!locale.isNullOrBlank()) {
                @Suppress("DEPRECATION")
                builder.setSubtypeLocale(locale)
            }
            if (!languageTag.isNullOrBlank()) {
                builder.setLanguageTag(languageTag)
            }
            builder.setSubtypeNameOverride(resolveSubtypeNameOverride(im))
            val subtype = builder.build()
            val hashCode = subtype.hashCode()
            subtypes[i] = subtype
            hashCodes[i] = hashCode
            knownSubtypes[im.uniqueName] = subtype
        }
        val imm = appContext.inputMethodManager
        val imiId = InputMethodUtil.componentName
        // although this method has been marked as deprecated,
        // dynamic subtypes have to be "registered" before they can be "enabled"
        @Suppress("DEPRECATION")
        imm.setAdditionalInputMethodSubtypes(imiId, subtypes)
        imm.setExplicitlyEnabledInputMethodSubtypes(imiId, hashCodes)
    }

    private fun resolveSystemVisibleInputs(inputMethods: Array<InputMethodEntry>): Array<InputMethodEntry> {
        val defaultChinese = inputMethods.firstOrNull { it.uniqueName == IM_PINYIN }
        if (defaultChinese != null) {
            return arrayOf(defaultChinese)
        }
        val fallbackChinese = inputMethods.firstOrNull { it.languageCode.startsWith("zh", ignoreCase = true) }
        if (fallbackChinese != null) {
            return arrayOf(fallbackChinese)
        }
        return inputMethods.take(1).toTypedArray()
    }

    private fun resolveSubtypeNameOverride(inputMethod: InputMethodEntry): String {
        return when (inputMethod.uniqueName) {
            IM_PINYIN -> DEFAULT_SYSTEM_SUBTYPE_NAME
            IM_KEYBOARD -> "英文26键"
            else -> inputMethod.displayName
        }
    }

    private fun resolveSubtypeLocale(inputMethod: InputMethodEntry): String? {
        val normalized = inputMethod.languageCode
            .trim()
            .replace('-', '_')
        if (normalized.isNotEmpty()) {
            return normalized
        }
        return when (inputMethod.uniqueName) {
            IM_PINYIN -> LOCALE_ZH_CN
            IM_KEYBOARD -> LOCALE_EN_US
            else -> null
        }
    }

    private fun resolveSubtypeLanguageTag(inputMethod: InputMethodEntry): String? {
        val normalized = inputMethod.languageCode.trim()
        if (normalized.isNotEmpty()) {
            return normalized.replace('_', '-')
        }
        return when (inputMethod.uniqueName) {
            IM_PINYIN -> LANGUAGE_TAG_ZH_CN
            IM_KEYBOARD -> LANGUAGE_TAG_EN_US
            else -> null
        }
    }
}
