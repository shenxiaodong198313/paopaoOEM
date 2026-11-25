/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2023 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core

import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.utils.InputMethodUtil
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.inputMethodManager

object SubtypeManager {

    private const val MODE_KEYBOARD = "keyboard"
    private const val DEFAULT_SYSTEM_SUBTYPE_NAME = "泡泡输入法"
    // Android compares explicit subtype IDs without comparing display/override flags.
    // A new ID ensures an upgrade replaces previously persisted engine-name entries.
    private const val SYSTEM_SUBTYPE_REVISION = "paopao-app-name-v2:"

    private const val IM_PINYIN = "fcitx-pinyin"
    private const val IM_KEYBOARD = "keyboard-us"
    private const val LOCALE_ZH_CN = "zh_CN"
    private const val LOCALE_EN_US = "en_US"
    private const val LANGUAGE_TAG_ZH_CN = "zh-CN"
    private const val LANGUAGE_TAG_EN_US = "en-US"

    // Readers on the IME UI thread never wait for PackageManager/IMM Binder calls.
    @Volatile
    private var knownSubtypes: Map<String, InputMethodSubtype> = emptyMap()
    private val syncMutex = Mutex()
    private var lastSyncedInputMethods: List<InputMethodEntry>? = null

    fun subtypeOf(inputMethod: String): InputMethodSubtype? {
        return knownSubtypes[inputMethod]
    }

    fun inputMethodOf(subtype: InputMethodSubtype): String {
        return subtype.extraValue.ifEmpty { IM_KEYBOARD }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    suspend fun syncWith(inputMethods: Array<InputMethodEntry>) {
        // Keep every enabled entry in the key: configuration changes must invalidate even
        // when resolveSystemVisibleInputs currently exposes the same single Chinese subtype.
        val snapshot = inputMethods.toList()
        syncMutex.withLock {
            if (lastSyncedInputMethods == snapshot) return
            withContext(Dispatchers.IO) {
                syncLocked(snapshot)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun syncLocked(inputMethods: List<InputMethodEntry>) {
        val updatedSubtypes = mutableMapOf<String, InputMethodSubtype>()
        val systemVisibleInputs = resolveSystemVisibleInputs(inputMethods.toTypedArray())
        val size = systemVisibleInputs.size
        val subtypes = arrayOfNulls<InputMethodSubtype>(size)
        val hashCodes = IntArray(size)
        systemVisibleInputs.forEachIndexed { i, im ->
            val builder = InputMethodSubtypeBuilder()
                .setSubtypeId((SYSTEM_SUBTYPE_REVISION + im.uniqueName).hashCode())
                .setSubtypeExtraValue(im.uniqueName)
                .setSubtypeMode(MODE_KEYBOARD)
                // This is the app's internal keyboard choice, not a separately selectable
                // system language. The system picker then renders only the IME label.
                .setOverridesImplicitlyEnabledSubtype(true)
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
            builder.setSubtypeNameOverride(DEFAULT_SYSTEM_SUBTYPE_NAME)
            val subtype = builder.build()
            val hashCode = subtype.hashCode()
            subtypes[i] = subtype
            hashCodes[i] = hashCode
            updatedSubtypes[im.uniqueName] = subtype
        }
        val imm = appContext.inputMethodManager
        val imiId = InputMethodUtil.componentName
        // Either Binder call can fail after the system partially changed. Invalidate the
        // previous success too, so returning to its configuration still repairs the system.
        lastSyncedInputMethods = null
        // although this method has been marked as deprecated,
        // dynamic subtypes have to be "registered" before they can be "enabled"
        @Suppress("DEPRECATION")
        imm.setAdditionalInputMethodSubtypes(imiId, subtypes)
        imm.setExplicitlyEnabledInputMethodSubtypes(imiId, hashCodes)
        knownSubtypes = updatedSubtypes.toMap()
        lastSyncedInputMethods = inputMethods
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
