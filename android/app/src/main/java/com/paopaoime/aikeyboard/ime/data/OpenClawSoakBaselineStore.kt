package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class OpenClawSoakBaseline(
    val id: String,
    val scenarioId: String,
    val scenarioName: String,
    val durationMinutes: Int,
    val startedAt: Long,
    val endedAt: Long,
    val summary: String,
    val metrics: OpenClawMetricsSnapshot
)

data class OpenClawBaselineDiff(
    val latest: OpenClawSoakBaseline,
    val stable: OpenClawSoakBaseline,
    val successRateDelta: Double,
    val retryRateDelta: Double,
    val timeoutDelta: Int,
    val avgDurationDeltaMs: Long,
    val stableRule: OpenClawStableBaselinePolicy
)

object OpenClawSoakBaselineStore {
    private const val PREFS_NAME = "openclaw_soak_baselines"
    private const val KEY_BASELINES = "baselines"
    private const val KEY_ACTIVE = "active"
    private const val MAX_BASELINES = 90

    fun startBaseline(
        context: Context,
        scenario: OpenClawSoakScenario
    ): String {
        val id = UUID.randomUUID().toString()
        val obj = JSONObject()
            .put("id", id)
            .put("scenarioId", scenario.id)
            .put("scenarioName", scenario.name)
            .put("durationMinutes", scenario.durationMinutes)
            .put("startedAt", System.currentTimeMillis())
            .put("metricsStart", serializeMetrics(OpenClawMetricsStore.snapshot(context)))
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE, obj.toString())
            .apply()
        OpenClawEventStore.appendRealEvent(
            context = context,
            npcId = "system",
            npcName = "系统",
            category = "ops.soak.start",
            content = "长稳开始：${scenario.name}（${scenario.durationMinutes}分钟）",
            status = "running",
            failCode = scenario.id
        )
        return id
    }

    fun finishActiveBaseline(context: Context, summary: String): OpenClawSoakBaseline? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ACTIVE, null).orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val startedAt = obj.optLong("startedAt", 0L)
        if (startedAt <= 0L) return null
        val scenarioId = obj.optString("scenarioId")
        val scenarioName = obj.optString("scenarioName")
        val durationMinutes = obj.optInt("durationMinutes")
        val id = obj.optString("id")
        val endedAt = System.currentTimeMillis()
        val metrics = OpenClawMetricsStore.snapshot(context)
        val item = OpenClawSoakBaseline(
            id = id,
            scenarioId = scenarioId,
            scenarioName = scenarioName,
            durationMinutes = durationMinutes,
            startedAt = startedAt,
            endedAt = endedAt,
            summary = summary,
            metrics = metrics
        )
        val list = listBaselines(context).toMutableList()
        list.add(0, item)
        if (list.size > MAX_BASELINES) list.removeLast()
        persistBaselines(context, list)
        prefs.edit().putString(KEY_ACTIVE, "").apply()
        OpenClawEventStore.appendRealEvent(
            context = context,
            npcId = "system",
            npcName = "系统",
            category = "ops.soak.finish",
            content = "长稳结束：${scenarioName}（${durationMinutes}分钟），${summary}",
            status = "success",
            failCode = scenarioId
        )
        return item
    }

    fun getActive(context: Context): JSONObject? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, null).orEmpty()
        if (raw.isBlank()) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun listBaselines(context: Context, limit: Int = 20): List<OpenClawSoakBaseline> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BASELINES, null)
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
        val list = mutableListOf<OpenClawSoakBaseline>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val metricsObj = obj.optJSONObject("metrics")
            list.add(
                OpenClawSoakBaseline(
                    id = obj.optString("id"),
                    scenarioId = obj.optString("scenarioId"),
                    scenarioName = obj.optString("scenarioName"),
                    durationMinutes = obj.optInt("durationMinutes"),
                    startedAt = obj.optLong("startedAt"),
                    endedAt = obj.optLong("endedAt"),
                    summary = obj.optString("summary"),
                    metrics = parseMetrics(metricsObj)
                )
            )
        }
        return list.take(limit.coerceAtLeast(1))
    }

    fun latestBaseline(context: Context): OpenClawSoakBaseline? {
        return listBaselines(context, 1).firstOrNull()
    }

    fun latestDiffAgainstStable(context: Context): OpenClawBaselineDiff? {
        val stableRule = OpenClawStableBaselinePolicyStore.load(context)
        val list = listBaselines(context, 30)
        if (list.size < 2) return null
        val latest = list.first()
        val stable = list.drop(1).firstOrNull {
            it.metrics.successRate >= stableRule.minSuccessRate &&
                it.metrics.timeoutFailures <= stableRule.maxTimeoutFailures
        } ?: list.getOrNull(1) ?: return null
        return OpenClawBaselineDiff(
            latest = latest,
            stable = stable,
            successRateDelta = latest.metrics.successRate - stable.metrics.successRate,
            retryRateDelta = latest.metrics.retryRate - stable.metrics.retryRate,
            timeoutDelta = latest.metrics.timeoutFailures - stable.metrics.timeoutFailures,
            avgDurationDeltaMs = latest.metrics.avgDurationMs - stable.metrics.avgDurationMs,
            stableRule = stableRule
        )
    }

    private fun persistBaselines(context: Context, list: List<OpenClawSoakBaseline>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("scenarioId", item.scenarioId)
                    .put("scenarioName", item.scenarioName)
                    .put("durationMinutes", item.durationMinutes)
                    .put("startedAt", item.startedAt)
                    .put("endedAt", item.endedAt)
                    .put("summary", item.summary)
                    .put("metrics", serializeMetrics(item.metrics))
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASELINES, arr.toString())
            .apply()
    }

    private fun serializeMetrics(metrics: OpenClawMetricsSnapshot): JSONObject {
        return JSONObject()
            .put("totalRuns", metrics.totalRuns)
            .put("successRuns", metrics.successRuns)
            .put("failedRuns", metrics.failedRuns)
            .put("retryEvents", metrics.retryEvents)
            .put("timeoutFailures", metrics.timeoutFailures)
            .put("queueRejectCount", metrics.queueRejectCount)
            .put("totalDurationMs", metrics.totalDurationMs)
            .put("durationSamples", metrics.durationSamples)
            .put("agentExclusiveMs", metrics.agentExclusiveMs)
            .put("updatedAt", metrics.updatedAt)
    }

    private fun parseMetrics(obj: JSONObject?): OpenClawMetricsSnapshot {
        if (obj == null) return OpenClawMetricsSnapshot()
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
