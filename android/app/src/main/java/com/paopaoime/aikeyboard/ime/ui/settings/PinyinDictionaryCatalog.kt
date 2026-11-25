package com.paopaoime.aikeyboard.ime.ui.settings

import org.fcitx.fcitx5.android.input.status.PinyinDictionaryRouting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Available bundled dictionaries; keyboard layout and input-method choices are separate. */
object PinyinDictionaryCatalog {
    data class Option(val id: String, val title: String, val description: String, val schemas: List<String> = emptyList())

    data class Selection(val profile: String, val doublePinyinSchema: String)

    data class PreparedSchema(val schema: String, val errorCode: String, val error: String)

    val options: List<Option> = listOf(
        Option(PinyinDictionaryRouting.FROST_PROFILE, "白霜词库", "随应用提供，首次使用无需下载",
            PinyinDictionaryRouting.schemasForProfile(PinyinDictionaryRouting.FROST_PROFILE)),
        Option(PinyinDictionaryRouting.ICE_PROFILE, "雾凇词库", "按需下载，准备完成后可离线使用",
            PinyinDictionaryRouting.schemasForProfile(PinyinDictionaryRouting.ICE_PROFILE)),
        Option(PinyinDictionaryRouting.DEFAULT_PROFILE, "Rime词库", "原有中文词库；下载并启用后可使用五笔", listOf("t9")),
    )

    suspend fun prepare(profile: String, request: suspend (String) -> PreparedSchema) {
        val option = requireNotNull(options.find { it.id == profile }) { "未找到这个词库，请重新选择" }
        for (schema in option.schemas) {
            currentCoroutineContext().ensureActive()
            val result = request(schema)
            check(result.errorCode.isEmpty() && result.error.isEmpty() && result.schema == schema) {
                "词库准备未完成，请重试"
            }
        }
    }

    /** A late preparation result cannot replace a newer selection or a settings reset. */
    class SelectionCoordinator {
        private val lock = Any()
        private var generation = 0L

        fun invalidate() = synchronized(lock) { generation += 1 }

        /** Caller serializes writes; includes cancellation during disk commit or return to UI. */
        suspend fun selectWithRollback(
            profile: String,
            prepare: suspend () -> Unit,
            persist: () -> Unit,
            rollback: () -> Unit,
        ) {
            var persistenceAttempted = false
            try {
                withContext(Dispatchers.IO) {
                    select(profile, prepare) {
                        persistenceAttempted = true
                        persist()
                    }
                }
            } catch (failure: Exception) {
                if (persistenceAttempted) withContext(NonCancellable + Dispatchers.IO) {
                    runCatching(rollback).exceptionOrNull()?.let(failure::addSuppressed)
                }
                throw failure
            }
        }

        suspend fun select(profile: String, prepare: suspend () -> Unit, persist: () -> Unit) {
            require(options.any { it.id == profile }) { "未找到这个词库，请重新选择" }
            val request = synchronized(lock) { ++generation }
            if (options.first { it.id == profile }.schemas.isNotEmpty()) prepare()
            val context = currentCoroutineContext()
            synchronized(lock) {
                context.ensureActive()
                if (request != generation) throw CancellationException("Dictionary selection changed")
                persist()
            }
        }
    }

    /** The writer atomically persists only these two preferences, leaving keyboard settings intact. */
    fun save(
        profile: String,
        doublePinyinMode: String?,
        read: () -> Selection,
        write: (Selection) -> Boolean,
    ) {
        require(options.any { it.id == profile }) { "未找到这个词库，请重新选择" }
        val previous = read()
        val next = Selection(
            profile,
            if (profile == PinyinDictionaryRouting.DEFAULT_PROFILE) previous.doublePinyinSchema
            else PinyinDictionaryRouting.frostDoublePinyinSchemaForMode(requireNotNull(doublePinyinMode)),
        )
        if (next == previous) return
        try {
            check(write(next)) { "Preference persistence failed" }
        } catch (failure: Exception) {
            // SharedPreferences can update its memory before reporting a disk-write failure.
            runCatching { check(write(previous)) { "Preference rollback failed" } }
                .exceptionOrNull()?.let(failure::addSuppressed)
            throw IllegalStateException("词库设置未能保存，请重试", failure)
        }
    }
}
