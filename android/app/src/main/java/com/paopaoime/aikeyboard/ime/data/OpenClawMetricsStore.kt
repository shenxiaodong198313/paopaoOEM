package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import org.json.JSONObject

data class OpenClawMetricsSnapshot(
    val totalRuns: Int = 0,
    val successRuns: Int = 0,
    val failedRuns: Int = 0,
    val retryEvents: Int = 0,
    val timeoutFailures: Int = 0,
    val queueRejectCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val durationSamples: Int = 0,
    val agentExclusiveMs: Long = 0L,
    val updatedAt: Long = 0L
) {
    val successRate: Double
        get() = if (totalRuns <= 0) 1.0 else successRuns.toDouble() / totalRuns.toDouble()
    val failedRate: Double
        get() = if (totalRuns <= 0) 0.0 else failedRuns.toDouble() / totalRuns.toDouble()
    val retryRate: Double
        get() = if (totalRuns <= 0) 0.0 else retryEvents.toDouble() / totalRuns.toDouble()
    val avgDurationMs: Long
        get() = if (durationSamples <= 0) 0L else totalDurationMs / durationSamples
}

object OpenClawMetricsStore {
    private const val PREFS_NAME = "openclaw_metrics_store"
    private const val KEY_SNAPSHOT = "snapshot"
    private const val KEY_AGENT_EXCLUSIVE_START = "agent_exclusive_start"

    fun recordEvent(context: Context, event: OpenClawEventRecord): OpenClawMetricsSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val old = parseSnapshot(prefs.getString(KEY_SNAPSHOT, null))
        var snapshot = old
        if (event.category == "ops.task.done") {
            val total = old.totalRuns + 1
            val success = old.successRuns + if (event.status.equals("success", ignoreCase = true)) 1 else 0
            val failed = old.failedRuns + if (event.status.equals("failed", ignoreCase = true) || event.failCode.isNotBlank()) 1 else 0
            snapshot = snapshot.copy(totalRuns = total, successRuns = success, failedRuns = failed)
        }
        if (event.category == "ops.agent.retry" || event.status.equals("retrying", ignoreCase = true)) {
            snapshot = snapshot.copy(retryEvents = snapshot.retryEvents + 1)
        }
        if (event.failCode == OpenClawFailCode.CALLBACK_TIMEOUT.code || event.content.contains("超时")) {
            snapshot = snapshot.copy(timeoutFailures = snapshot.timeoutFailures + 1)
        }
        if (event.category == "ops.schedule.reject") {
            snapshot = snapshot.copy(queueRejectCount = snapshot.queueRejectCount + 1)
        }
        if (event.durationMs > 0L) {
            snapshot = snapshot.copy(
                totalDurationMs = snapshot.totalDurationMs + event.durationMs,
                durationSamples = snapshot.durationSamples + 1
            )
        }
        snapshot = snapshot.copy(updatedAt = System.currentTimeMillis())
        persistSnapshot(prefs, snapshot)
        return snapshot
    }

    fun markAgentExclusiveStart(context: Context, startAt: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_AGENT_EXCLUSIVE_START, startAt)
            .apply()
    }

    fun markAgentExclusiveEnd(context: Context, endAt: Long = System.currentTimeMillis()): OpenClawMetricsSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val startAt = prefs.getLong(KEY_AGENT_EXCLUSIVE_START, 0L)
        val old = parseSnapshot(prefs.getString(KEY_SNAPSHOT, null))
        if (startAt <= 0L || endAt <= startAt) return old
        val snapshot = old.copy(
            agentExclusiveMs = old.agentExclusiveMs + (endAt - startAt),
            updatedAt = endAt
        )
        prefs.edit().putLong(KEY_AGENT_EXCLUSIVE_START, 0L).apply()
        persistSnapshot(prefs, snapshot)
        return snapshot
    }

    fun snapshot(context: Context): OpenClawMetricsSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return parseSnapshot(prefs.getString(KEY_SNAPSHOT, null))
    }

    private fun persistSnapshot(
        prefs: android.content.SharedPreferences,
        snapshot: OpenClawMetricsSnapshot
    ) {
        prefs.edit()
            .putString(
                KEY_SNAPSHOT,
                JSONObject()
                    .put("totalRuns", snapshot.totalRuns)
                    .put("successRuns", snapshot.successRuns)
                    .put("failedRuns", snapshot.failedRuns)
                    .put("retryEvents", snapshot.retryEvents)
                    .put("timeoutFailures", snapshot.timeoutFailures)
                    .put("queueRejectCount", snapshot.queueRejectCount)
                    .put("totalDurationMs", snapshot.totalDurationMs)
                    .put("durationSamples", snapshot.durationSamples)
                    .put("agentExclusiveMs", snapshot.agentExclusiveMs)
                    .put("updatedAt", snapshot.updatedAt)
                    .toString()
            )
            .apply()
    }

    private fun parseSnapshot(raw: String?): OpenClawMetricsSnapshot {
        if (raw.isNullOrBlank()) return OpenClawMetricsSnapshot()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return OpenClawMetricsSnapshot()
        return OpenClawMetricsSnapshot(
            totalRuns = obj.optInt("totalRuns", 0),
            successRuns = obj.optInt("successRuns", 0),
            failedRuns = obj.optInt("failedRuns", 0),
            retryEvents = obj.optInt("retryEvents", 0),
            timeoutFailures = obj.optInt("timeoutFailures", 0),
            queueRejectCount = obj.optInt("queueRejectCount", 0),
            totalDurationMs = obj.optLong("totalDurationMs", 0L),
            durationSamples = obj.optInt("durationSamples", 0),
            agentExclusiveMs = obj.optLong("agentExclusiveMs", 0L),
            updatedAt = obj.optLong("updatedAt", 0L)
        )
    }
}
