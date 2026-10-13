package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class OpenClawEventRecord(
    val timestamp: Long,
    val npcId: String,
    val npcName: String,
    val category: String,
    val content: String,
    val runId: String = "",
    val runState: String = "",
    val taskId: String = "",
    val subtaskId: String = "",
    val stepId: String = "",
    val taskType: String = "",
    val status: String = "",
    val retryCount: Int = 0,
    val failCode: String = "",
    val durationMs: Long = 0L,
    val resultType: String = "",
    val resultText: String = "",
    val resultPath: String = ""
)

object OpenClawEventStore {
    private const val PREFS_NAME = "openclaw_event_store"
    private const val KEY_REAL_EVENTS = "real_events"
    private const val KEY_DEBUG_FULL_RECORD = "debug_full_record"
    private const val MAX_EVENTS = 240
    private const val PERSIST_DEBOUNCE_MS = 800L
    private val KEY_EVENT_CATEGORIES = setOf(
        "ops.plan",
        "ops.publish.prepare",
        "ops.agent.accepted",
        "ops.agent.running",
        "ops.agent.retry",
        "ops.agent.stopped",
        "ops.publish.success",
        "ops.publish.failed",
        "ops.task.done",
        "ops.asset.produced",
        "ops.asset.reused",
        "ops.schedule.enqueue",
        "ops.schedule.dequeue",
        "ops.schedule.reject"
    )
    private val KEY_EVENT_PREFIXES = listOf("ops.publish.verify.", "ops.return.", "ops.alert.", "ops.soak.", "ops.channel.")
    private val NON_PERSIST_PREFIXES = listOf("idle.", "chatter.", "knowledge.")
    private val cache = mutableListOf<OpenClawEventRecord>()
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var persistJob: Job? = null
    @Volatile
    private var cacheLoaded = false

    fun appendRealEvent(
        context: Context,
        npcId: String,
        npcName: String,
        category: String,
        content: String,
        runId: String = "",
        runState: String = "",
        taskId: String = "",
        subtaskId: String = "",
        stepId: String = "",
        taskType: String = "",
        status: String = "",
        retryCount: Int = 0,
        failCode: String = "",
        durationMs: Long = 0L,
        resultType: String = "",
        resultText: String = "",
        resultPath: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (!shouldPersistEvent(context, category)) return
        ensureCache(context)
        synchronized(this) {
            cache.add(
                OpenClawEventRecord(
                    timestamp = timestamp,
                    npcId = npcId,
                    npcName = npcName,
                    category = category,
                    content = content,
                    runId = runId,
                    runState = runState,
                    taskId = taskId,
                    subtaskId = subtaskId,
                    stepId = stepId,
                    taskType = taskType,
                    status = status,
                    retryCount = retryCount,
                    failCode = failCode,
                    durationMs = durationMs,
                    resultType = resultType,
                    resultText = resultText,
                    resultPath = resultPath
                )
            )
            if (cache.size > MAX_EVENTS) {
                val overflow = cache.size - MAX_EVENTS
                repeat(overflow) { cache.removeAt(0) }
            }
        }
        schedulePersist(context.applicationContext)
    }

    fun setDebugFullRecordEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEBUG_FULL_RECORD, enabled)
            .apply()
    }

    fun isDebugFullRecordEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEBUG_FULL_RECORD, false)
    }

    fun listRealEvents(context: Context): List<OpenClawEventRecord> {
        ensureCache(context)
        return synchronized(this) {
            cache.sortedByDescending { it.timestamp }
        }
    }

    fun listEvents(
        context: Context,
        limit: Int,
        beforeTimestamp: Long = Long.MAX_VALUE,
        npcId: String? = null,
        categoryPrefixes: List<String> = emptyList()
    ): List<OpenClawEventRecord> {
        ensureCache(context)
        val safeLimit = limit.coerceAtLeast(1)
        return synchronized(this) {
            cache.asReversed()
                .asSequence()
                .filter { it.timestamp < beforeTimestamp }
                .filter { npcId.isNullOrBlank() || it.npcId == npcId }
                .filter { record ->
                    if (categoryPrefixes.isEmpty()) true
                    else categoryPrefixes.any { prefix -> record.category.startsWith(prefix) }
                }
                .take(safeLimit)
                .toList()
        }
    }

    private fun ensureCache(context: Context) {
        if (cacheLoaded) return
        synchronized(this) {
            if (cacheLoaded) return
            cache.clear()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = parseArray(prefs.getString(KEY_REAL_EVENTS, null))
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                cache.add(
                    OpenClawEventRecord(
                        timestamp = obj.optLong("timestamp", 0L),
                        npcId = obj.optString("npcId"),
                        npcName = obj.optString("npcName"),
                        category = obj.optString("category"),
                        content = obj.optString("content"),
                        runId = obj.optString("runId"),
                        runState = obj.optString("runState"),
                        taskId = obj.optString("taskId"),
                        subtaskId = obj.optString("subtaskId"),
                        stepId = obj.optString("stepId"),
                        taskType = obj.optString("taskType"),
                        status = obj.optString("status"),
                        retryCount = obj.optInt("retryCount", 0),
                        failCode = obj.optString("failCode"),
                        durationMs = obj.optLong("durationMs", 0L),
                        resultType = obj.optString("resultType"),
                        resultText = obj.optString("resultText"),
                        resultPath = obj.optString("resultPath")
                    )
                )
            }
            if (cache.size > MAX_EVENTS) {
                val overflow = cache.size - MAX_EVENTS
                repeat(overflow) { cache.removeAt(0) }
            }
            cacheLoaded = true
            schedulePersist(context.applicationContext, immediate = true)
        }
    }

    private fun schedulePersist(context: Context, immediate: Boolean = false) {
        if (immediate) {
            persistScope.launch {
                persistSnapshot(context.applicationContext)
            }
            return
        }
        synchronized(this) {
            if (persistJob?.isActive == true) return
            persistJob = persistScope.launch {
                delay(PERSIST_DEBOUNCE_MS)
                persistSnapshot(context.applicationContext)
                synchronized(this@OpenClawEventStore) {
                    persistJob = null
                }
            }
        }
    }

    private fun persistSnapshot(context: Context) {
        val snapshot = synchronized(this) { cache.toList() }
        val array = JSONArray()
        snapshot.forEach { record ->
            array.put(
                JSONObject()
                    .put("timestamp", record.timestamp)
                    .put("npcId", record.npcId)
                    .put("npcName", record.npcName)
                    .put("category", record.category)
                    .put("content", record.content)
                    .put("runId", record.runId)
                    .put("runState", record.runState)
                    .put("taskId", record.taskId)
                    .put("subtaskId", record.subtaskId)
                    .put("stepId", record.stepId)
                    .put("taskType", record.taskType)
                    .put("status", record.status)
                    .put("retryCount", record.retryCount)
                    .put("failCode", record.failCode)
                    .put("durationMs", record.durationMs)
                    .put("resultType", record.resultType)
                    .put("resultText", record.resultText)
                    .put("resultPath", record.resultPath)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REAL_EVENTS, array.toString())
            .apply()
    }

    private fun parseArray(raw: String?): JSONArray {
        if (raw.isNullOrBlank()) return JSONArray()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun shouldPersistEvent(context: Context, category: String): Boolean {
        if (NON_PERSIST_PREFIXES.any { category.startsWith(it) }) return false
        if (isDebugFullRecordEnabled(context)) return true
        if (KEY_EVENT_CATEGORIES.contains(category)) return true
        return KEY_EVENT_PREFIXES.any { prefix -> category.startsWith(prefix) }
    }
}
