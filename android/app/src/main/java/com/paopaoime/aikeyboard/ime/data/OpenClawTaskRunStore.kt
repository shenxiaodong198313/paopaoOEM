package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class OpenClawRunState {
    PENDING,
    DISPATCHED,
    EXECUTING,
    RETRYING,
    RETURNING,
    COMPLETED,
    FAILED_RETRYABLE,
    FAILED_FATAL,
    MANUAL_REQUIRED,
    STOPPED,
    SKIPPED
}

data class OpenClawTaskRun(
    val runId: String,
    val taskId: String,
    val npcId: String,
    val subtaskId: String,
    val taskType: String,
    val state: OpenClawRunState,
    val retryCount: Int,
    val failCode: String,
    val lastMessage: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class OpenClawRunDomainEvent(
    val eventId: String,
    val runId: String,
    val taskId: String,
    val npcId: String,
    val trigger: String,
    val fromState: OpenClawRunState?,
    val toState: OpenClawRunState,
    val failCode: String,
    val message: String,
    val retryCount: Int,
    val timestamp: Long
)

object OpenClawTaskStateMachine {
    private val allowed = mapOf(
        OpenClawRunState.PENDING to setOf(
            OpenClawRunState.DISPATCHED,
            OpenClawRunState.SKIPPED,
            OpenClawRunState.STOPPED
        ),
        OpenClawRunState.DISPATCHED to setOf(
            OpenClawRunState.EXECUTING,
            OpenClawRunState.COMPLETED,
            OpenClawRunState.FAILED_RETRYABLE,
            OpenClawRunState.FAILED_FATAL,
            OpenClawRunState.STOPPED
        ),
        OpenClawRunState.EXECUTING to setOf(
            OpenClawRunState.EXECUTING,
            OpenClawRunState.RETRYING,
            OpenClawRunState.RETURNING,
            OpenClawRunState.COMPLETED,
            OpenClawRunState.SKIPPED,
            OpenClawRunState.FAILED_RETRYABLE,
            OpenClawRunState.FAILED_FATAL,
            OpenClawRunState.MANUAL_REQUIRED,
            OpenClawRunState.STOPPED
        ),
        OpenClawRunState.RETRYING to setOf(
            OpenClawRunState.EXECUTING,
            OpenClawRunState.FAILED_FATAL,
            OpenClawRunState.MANUAL_REQUIRED,
            OpenClawRunState.STOPPED
        ),
        OpenClawRunState.RETURNING to setOf(
            OpenClawRunState.RETURNING,
            OpenClawRunState.COMPLETED,
            OpenClawRunState.MANUAL_REQUIRED,
            OpenClawRunState.FAILED_FATAL,
            OpenClawRunState.STOPPED
        ),
        OpenClawRunState.FAILED_RETRYABLE to setOf(
            OpenClawRunState.RETRYING,
            OpenClawRunState.FAILED_FATAL,
            OpenClawRunState.MANUAL_REQUIRED,
            OpenClawRunState.STOPPED
        ),
        OpenClawRunState.FAILED_FATAL to emptySet(),
        OpenClawRunState.MANUAL_REQUIRED to setOf(
            OpenClawRunState.EXECUTING,
            OpenClawRunState.COMPLETED,
            OpenClawRunState.STOPPED
        ),
        OpenClawRunState.STOPPED to emptySet(),
        OpenClawRunState.SKIPPED to emptySet(),
        OpenClawRunState.COMPLETED to emptySet()
    )

    fun canTransit(from: OpenClawRunState, to: OpenClawRunState): Boolean {
        return allowed[from]?.contains(to) == true
    }
}

object OpenClawTaskRunStore {
    private const val PREFS_NAME = "openclaw_task_run_store"
    private const val KEY_RUNS = "runs"
    private const val KEY_EVENTS = "events"
    private const val KEY_TASK_RUN_MAP = "task_run_map"
    private const val MAX_RUNS = 320
    private const val MAX_EVENTS = 2000

    private val runs = linkedMapOf<String, OpenClawTaskRun>()
    private val taskToRunId = mutableMapOf<String, String>()
    private val events = mutableListOf<OpenClawRunDomainEvent>()
    @Volatile
    private var loaded = false

    fun createRun(
        context: Context,
        taskId: String,
        npcId: String,
        subtaskId: String,
        taskType: String,
        timestamp: Long = System.currentTimeMillis()
    ): OpenClawTaskRun {
        ensureCache(context)
        synchronized(this) {
            val existingRunId = taskToRunId[taskId]
            if (!existingRunId.isNullOrBlank()) {
                val existing = runs[existingRunId]
                if (existing != null) return existing
            }
            val runId = "run-${UUID.randomUUID().toString().replace("-", "").takeLast(12)}"
            val run = OpenClawTaskRun(
                runId = runId,
                taskId = taskId,
                npcId = npcId,
                subtaskId = subtaskId,
                taskType = taskType,
                state = OpenClawRunState.PENDING,
                retryCount = 0,
                failCode = "",
                lastMessage = "run_created",
                createdAt = timestamp,
                updatedAt = timestamp
            )
            taskToRunId[taskId] = runId
            runs[runId] = run
            appendEventInternal(
                OpenClawRunDomainEvent(
                    eventId = "evt-${UUID.randomUUID().toString().replace("-", "").takeLast(12)}",
                    runId = runId,
                    taskId = taskId,
                    npcId = npcId,
                    trigger = "run_created",
                    fromState = null,
                    toState = OpenClawRunState.PENDING,
                    failCode = "",
                    message = "run created",
                    retryCount = 0,
                    timestamp = timestamp
                )
            )
            trimOverflow()
            persist(context)
            return run
        }
    }

    fun transit(
        context: Context,
        runId: String,
        trigger: String,
        toState: OpenClawRunState,
        failCode: String = "",
        message: String = "",
        retryIncrement: Int = 0,
        timestamp: Long = System.currentTimeMillis()
    ): OpenClawTaskRun? {
        ensureCache(context)
        synchronized(this) {
            val current = runs[runId] ?: return null
            if (!OpenClawTaskStateMachine.canTransit(current.state, toState)) {
                appendEventInternal(
                    OpenClawRunDomainEvent(
                        eventId = "evt-${UUID.randomUUID().toString().replace("-", "").takeLast(12)}",
                        runId = current.runId,
                        taskId = current.taskId,
                        npcId = current.npcId,
                        trigger = "invalid_$trigger",
                        fromState = current.state,
                        toState = current.state,
                        failCode = failCode,
                        message = "invalid_transition_to_${toState.name}",
                        retryCount = current.retryCount,
                        timestamp = timestamp
                    )
                )
                trimOverflow()
                persist(context)
                return current
            }
            val nextRetryCount = (current.retryCount + retryIncrement).coerceAtLeast(0)
            val next = current.copy(
                state = toState,
                retryCount = nextRetryCount,
                failCode = failCode.ifBlank { current.failCode },
                lastMessage = message.ifBlank { trigger },
                updatedAt = timestamp
            )
            runs[runId] = next
            appendEventInternal(
                OpenClawRunDomainEvent(
                    eventId = "evt-${UUID.randomUUID().toString().replace("-", "").takeLast(12)}",
                    runId = next.runId,
                    taskId = next.taskId,
                    npcId = next.npcId,
                    trigger = trigger,
                    fromState = current.state,
                    toState = toState,
                    failCode = failCode,
                    message = message,
                    retryCount = nextRetryCount,
                    timestamp = timestamp
                )
            )
            trimOverflow()
            persist(context)
            return next
        }
    }

    fun findRunIdByTaskId(context: Context, taskId: String): String {
        if (taskId.isBlank()) return ""
        ensureCache(context)
        return synchronized(this) { taskToRunId[taskId].orEmpty() }
    }

    fun getRun(context: Context, runId: String): OpenClawTaskRun? {
        if (runId.isBlank()) return null
        ensureCache(context)
        return synchronized(this) { runs[runId] }
    }

    fun replay(context: Context, runId: String): List<OpenClawRunDomainEvent> {
        if (runId.isBlank()) return emptyList()
        ensureCache(context)
        return synchronized(this) {
            events.filter { it.runId == runId }.sortedBy { it.timestamp }
        }
    }

    private fun appendEventInternal(event: OpenClawRunDomainEvent) {
        events.add(event)
    }

    private fun trimOverflow() {
        while (events.size > MAX_EVENTS) {
            events.removeAt(0)
        }
        while (runs.size > MAX_RUNS) {
            val first = runs.entries.firstOrNull() ?: break
            runs.remove(first.key)
            taskToRunId.remove(first.value.taskId)
            events.removeAll { it.runId == first.key }
        }
    }

    private fun ensureCache(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runs.clear()
            taskToRunId.clear()
            events.clear()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val runArray = parseArray(prefs.getString(KEY_RUNS, null))
            for (i in 0 until runArray.length()) {
                val obj = runArray.optJSONObject(i) ?: continue
                val run = OpenClawTaskRun(
                    runId = obj.optString("runId"),
                    taskId = obj.optString("taskId"),
                    npcId = obj.optString("npcId"),
                    subtaskId = obj.optString("subtaskId"),
                    taskType = obj.optString("taskType"),
                    state = parseState(obj.optString("state"), OpenClawRunState.PENDING),
                    retryCount = obj.optInt("retryCount", 0),
                    failCode = obj.optString("failCode"),
                    lastMessage = obj.optString("lastMessage"),
                    createdAt = obj.optLong("createdAt", 0L),
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
                if (run.runId.isBlank() || run.taskId.isBlank()) continue
                runs[run.runId] = run
            }
            val mapObj = parseObject(prefs.getString(KEY_TASK_RUN_MAP, null))
            val keys = mapObj.keys()
            while (keys.hasNext()) {
                val taskId = keys.next().orEmpty()
                val runId = mapObj.optString(taskId)
                if (taskId.isNotBlank() && runId.isNotBlank()) {
                    taskToRunId[taskId] = runId
                }
            }
            val eventArray = parseArray(prefs.getString(KEY_EVENTS, null))
            for (i in 0 until eventArray.length()) {
                val obj = eventArray.optJSONObject(i) ?: continue
                val toState = parseState(obj.optString("toState"), OpenClawRunState.PENDING)
                val fromRaw = obj.optString("fromState")
                val fromState = fromRaw.takeIf { it.isNotBlank() }?.let { parseState(it, null) }
                events.add(
                    OpenClawRunDomainEvent(
                        eventId = obj.optString("eventId"),
                        runId = obj.optString("runId"),
                        taskId = obj.optString("taskId"),
                        npcId = obj.optString("npcId"),
                        trigger = obj.optString("trigger"),
                        fromState = fromState,
                        toState = toState,
                        failCode = obj.optString("failCode"),
                        message = obj.optString("message"),
                        retryCount = obj.optInt("retryCount", 0),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
            loaded = true
            trimOverflow()
            persist(context)
        }
    }

    private fun persist(context: Context) {
        val runArray = JSONArray()
        runs.values.forEach { run ->
            runArray.put(
                JSONObject()
                    .put("runId", run.runId)
                    .put("taskId", run.taskId)
                    .put("npcId", run.npcId)
                    .put("subtaskId", run.subtaskId)
                    .put("taskType", run.taskType)
                    .put("state", run.state.name)
                    .put("retryCount", run.retryCount)
                    .put("failCode", run.failCode)
                    .put("lastMessage", run.lastMessage)
                    .put("createdAt", run.createdAt)
                    .put("updatedAt", run.updatedAt)
            )
        }
        val eventArray = JSONArray()
        events.forEach { event ->
            eventArray.put(
                JSONObject()
                    .put("eventId", event.eventId)
                    .put("runId", event.runId)
                    .put("taskId", event.taskId)
                    .put("npcId", event.npcId)
                    .put("trigger", event.trigger)
                    .put("fromState", event.fromState?.name.orEmpty())
                    .put("toState", event.toState.name)
                    .put("failCode", event.failCode)
                    .put("message", event.message)
                    .put("retryCount", event.retryCount)
                    .put("timestamp", event.timestamp)
            )
        }
        val taskMapObj = JSONObject()
        taskToRunId.forEach { (taskId, runId) ->
            taskMapObj.put(taskId, runId)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RUNS, runArray.toString())
            .putString(KEY_EVENTS, eventArray.toString())
            .putString(KEY_TASK_RUN_MAP, taskMapObj.toString())
            .apply()
    }

    private fun parseArray(raw: String?): JSONArray {
        if (raw.isNullOrBlank()) return JSONArray()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun parseObject(raw: String?): JSONObject {
        if (raw.isNullOrBlank()) return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun parseState(raw: String?, fallback: OpenClawRunState?): OpenClawRunState {
        if (raw.isNullOrBlank()) return fallback ?: OpenClawRunState.PENDING
        return runCatching { OpenClawRunState.valueOf(raw) }.getOrDefault(fallback ?: OpenClawRunState.PENDING)
    }
}
