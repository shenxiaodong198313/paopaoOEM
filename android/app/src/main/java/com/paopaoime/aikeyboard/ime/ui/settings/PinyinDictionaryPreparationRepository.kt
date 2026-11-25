package com.paopaoime.aikeyboard.ime.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.fcitx.fcitx5.android.core.RawConfig
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.input.status.PinyinDictionaryModeResolver
import java.util.UUID

/** Uses the engine's deployment API without selecting a schema or touching user dictionaries. */
internal object PinyinDictionaryPreparationRepository {
    private val requests = Mutex()

    suspend fun readNativeDoublePinyinMode(): String = resolveDoublePinyinMode(
        PinyinDictionaryCatalog.Selection("default", "")
    )

    /** Verifies native before Host/Rime persistence; failure or cancellation restores the prior mode. */
    suspend fun changeDoublePinyinMode(
        mode: String,
        persist: () -> Unit,
        rollbackPreferences: () -> Unit,
    ) = requests.withLock {
        val target = PinyinDictionaryModeResolver.nativeProfileForMode(mode)
        var original: String? = null
        var nativeWriteAttempted = false
        var preferenceWriteAttempted = false
        try {
            // Catch cancellation on the return to the caller dispatcher as well as native work.
            withContext(Dispatchers.IO) {
                val client = "pinyin-dictionary-prepare-${UUID.randomUUID()}"
                val connection = FcitxDaemon.connect(client)
                try {
                    withTimeout(30_000L) {
                        connection.runOnReady {
                            original = PinyinDictionaryModeResolver.nativeProfileFromConfig(getAddonConfig("pinyin"))
                            if (original != target) {
                                nativeWriteAttempted = true
                                setAddonConfig("pinyin", RawConfig(arrayOf(RawConfig("ShuangpinProfile", target))))
                            }
                            check(PinyinDictionaryModeResolver.nativeProfileFromConfig(getAddonConfig("pinyin")) == target) {
                                "双拼方案未能在输入引擎中生效，请重试"
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        preferenceWriteAttempted = true
                        persist()
                    }
                } finally {
                    // The last disconnect waits for nativeExit/save; it must stay off the UI thread.
                    FcitxDaemon.disconnect(client)
                }
            }
        } catch (failure: Exception) {
            withContext(NonCancellable + Dispatchers.IO) {
                if (preferenceWriteAttempted) {
                    runCatching(rollbackPreferences).exceptionOrNull()?.let(failure::addSuppressed)
                }
                if (nativeWriteAttempted) {
                    runCatching {
                        // The normal connection has closed, including cancellation during its close.
                        val client = "pinyin-dictionary-prepare-${UUID.randomUUID()}"
                        val connection = FcitxDaemon.connect(client)
                        try {
                            withTimeout(30_000L) {
                                connection.runOnReady {
                                    setAddonConfig("pinyin", RawConfig(arrayOf(RawConfig("ShuangpinProfile", requireNotNull(original)))))
                                    check(PinyinDictionaryModeResolver.nativeProfileFromConfig(getAddonConfig("pinyin")) == original) {
                                        "双拼方案恢复失败"
                                    }
                                }
                            }
                        } finally {
                            FcitxDaemon.disconnect(client)
                        }
                    }.exceptionOrNull()?.let(failure::addSuppressed)
                }
            }
            if (failure is TimeoutCancellationException) {
                throw IllegalStateException("双拼方案保存超时，请重试", failure)
            }
            throw failure
        }
    }

    /** Default uses the native engine's actual mode, never the host's implicit preference default. */
    suspend fun resolveDoublePinyinMode(previous: PinyinDictionaryCatalog.Selection): String {
        if (!PinyinDictionaryModeResolver.requiresNativeConfig(previous.profile)) {
            return PinyinDictionaryModeResolver.resolveForSelection(previous.profile, previous.doublePinyinSchema)
        }
        return try {
            withContext(Dispatchers.IO) {
                withTimeout(30_000L) {
                    requests.withLock {
                        val client = "pinyin-dictionary-prepare-${UUID.randomUUID()}"
                        val connection = FcitxDaemon.connect(client)
                        try {
                            connection.runOnReady {
                                PinyinDictionaryModeResolver.resolveForSelection(
                                    previous.profile, previous.doublePinyinSchema, getAddonConfig("pinyin")
                                )
                            }
                        } finally {
                            FcitxDaemon.disconnect(client)
                        }
                    }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw IllegalStateException("当前双拼方案读取超时，请重试", timeout)
        }
    }

    suspend fun prepare(profile: String) = withContext(Dispatchers.IO) {
        try {
            withTimeout(300_000L) {
                requests.withLock {
                    val client = "pinyin-dictionary-prepare-${UUID.randomUUID()}"
                    val connection = FcitxDaemon.connect(client)
                    try {
                        if (profile == "default") {
                            // Fcitx remembers an addon load failure from before the optional
                            // libime files existed. Refresh that cache once, off the native
                            // dispatcher, and only when no composition would be lost.
                            val needsRestart = connection.runOnReady {
                                getAddonConfig("pinyin").findByName("cfg")
                                    ?.findByName("ShuangpinProfile")?.value.isNullOrEmpty()
                            }
                            if (needsRestart) {
                                check(connection.runOnReady { isEmpty() }) { "请先完成当前输入，再加载词库" }
                                FcitxDaemon.restartFcitx()
                            }
                            connection.runOnReady {
                                PinyinDictionaryModeResolver.nativeProfileFromConfig(getAddonConfig("pinyin"))
                            }
                        }
                        connection.runOnReady {
                            if (profile == "default") check(org.fcitx.fcitx5.android.core.OfflinePinyinProbe.validate(
                                java.io.File(org.fcitx.fcitx5.android.core.data.DataManager.dataDir, "usr/share/libime").absolutePath
                            )) { "Rime词库原生加载失败，请重试" }
                            PinyinDictionaryCatalog.prepare(profile) request@{ schema ->
                                repeat(3) {
                                    val requestId = UUID.randomUUID().toString()
                                    setAddonSubConfig("rime", "paopao-schema", RawConfig(arrayOf(
                                        RawConfig("Action", "prepare"),
                                        RawConfig("Schema", schema),
                                        RawConfig("RequestId", requestId),
                                    )))
                                    val cfg = getAddonSubConfig("rime", "paopao-schema")
                                        .findByName("cfg") ?: error("词库准备接口暂不可用，请重试")
                                    // Keyboard selection shares this addon response; never accept its result.
                                    if (cfg.findByName("RequestId")?.value != requestId) return@repeat
                                    return@request PinyinDictionaryCatalog.PreparedSchema(
                                        schema = cfg.findByName("PreparedSchema")?.value.orEmpty(),
                                        errorCode = cfg.findByName("ErrorCode")?.value.orEmpty(),
                                        error = cfg.findByName("Error")?.value.orEmpty(),
                                    )
                                }
                                error("词库准备结果已变化，请重试")
                            }
                        }
                    } finally {
                        FcitxDaemon.disconnect(client)
                    }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw IllegalStateException("词库准备超时，请重试", timeout)
        }
    }
}
