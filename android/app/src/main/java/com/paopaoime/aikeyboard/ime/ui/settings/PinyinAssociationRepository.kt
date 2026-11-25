package com.paopaoime.aikeyboard.ime.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.status.PinyinAssociation
import org.fcitx.fcitx5.android.input.status.PinyinDictionaryPackages
import java.util.UUID

/** Global intent defaults on and is consumed when a supported engine route activates. */
object PinyinAssociationRepository {
    fun read(): Boolean = AppPrefs.getInstance().internal.chineseAssociationEnabled.getValue()

    private suspend fun <T> withEngine(action: suspend FcitxAPI.() -> T): T = withContext(Dispatchers.IO) {
        val client = "pinyin-association-${UUID.randomUUID()}"
        val connection = FcitxDaemon.connect(client)
        try {
            withTimeout(30_000L) { connection.runOnReady { action() } }
        } finally {
            FcitxDaemon.disconnect(client)
        }
    }

    suspend fun write(value: Boolean): Boolean = PinyinAssociation.change {
        withContext(Dispatchers.IO + NonCancellable) {
            val preference = AppPrefs.getInstance().internal.chineseAssociationEnabled
            fun persist() {
                check(preference.sharedPreferences.edit().putBoolean(preference.key, value).commit()) {
                    "中文联想设置保存失败，请重试"
                }
            }
            withEngine {
                val nativeReady = PinyinDictionaryPackages.ready("default")
                val previousRime = preference.getValue()
                // Optional native pinyin remains unloaded until its dictionary is installed.
                val previous = if (nativeReady) PinyinAssociation.enabled(getAddonConfig("pinyin")) else null
                try {
                    PinyinAssociation.applyRime(this, value)
                    if (nativeReady) PinyinAssociation.apply(this, value)
                    persist()
                } catch (failure: Exception) {
                    runCatching { withContext(NonCancellable) {
                        PinyinAssociation.applyRime(this@withEngine, previousRime)
                        if (previous != null) PinyinAssociation.apply(this@withEngine, previous)
                    } }
                        .exceptionOrNull()?.let(failure::addSuppressed)
                    throw failure
                }
            }
            value
        }
    }
}
