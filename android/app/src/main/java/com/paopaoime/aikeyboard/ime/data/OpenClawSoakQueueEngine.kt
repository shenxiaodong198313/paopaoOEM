package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class OpenClawSoakQueueState(
    val queueId: String,
    val currentIndex: Int,
    val total: Int,
    val runningName: String,
    val createdAt: Long
)

object OpenClawSoakQueueEngine {
    private const val PREFS_NAME = "openclaw_soak_queue"
    private const val KEY_ACTIVE_QUEUE = "active_queue"

    fun startQueue(context: Context, scenarios: List<OpenClawSoakScenario>): OpenClawSoakQueueState? {
        if (scenarios.isEmpty()) return null
        val queueId = UUID.randomUUID().toString()
        val payload = JSONObject()
            .put("queueId", queueId)
            .put("currentIndex", 0)
            .put("createdAt", System.currentTimeMillis())
            .put("scenarios", JSONArray().apply {
                scenarios.forEach { s ->
                    put(
                        JSONObject()
                            .put("id", s.id)
                            .put("name", s.name)
                            .put("durationMinutes", s.durationMinutes)
                            .put("rhythmMode", s.rhythmMode)
                            .put("pressureLevel", s.pressureLevel)
                            .put("createdAt", s.createdAt)
                    )
                }
            })
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_QUEUE, payload.toString())
            .apply()
        OpenClawSoakBaselineStore.startBaseline(context, scenarios.first())
        OpenClawEventStore.appendRealEvent(
            context = context,
            npcId = "system",
            npcName = "系统",
            category = "ops.soak.queue.start",
            content = "长稳队列启动：共${scenarios.size}个场景",
            status = "running",
            failCode = queueId
        )
        return OpenClawSoakQueueState(
            queueId = queueId,
            currentIndex = 0,
            total = scenarios.size,
            runningName = scenarios.first().name,
            createdAt = payload.optLong("createdAt")
        )
    }

    fun finishCurrentAndAdvance(context: Context, summary: String): OpenClawSoakQueueState? {
        val finished = OpenClawSoakBaselineStore.finishActiveBaseline(context, summary)
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_QUEUE, null).orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val scenarios = obj.optJSONArray("scenarios") ?: return null
        val currentIndex = obj.optInt("currentIndex", 0)
        val nextIndex = currentIndex + 1
        if (nextIndex >= scenarios.length()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE_QUEUE, "")
                .apply()
            OpenClawEventStore.appendRealEvent(
                context = context,
                npcId = "system",
                npcName = "系统",
                category = "ops.soak.queue.finish",
                content = "长稳队列完成：${scenarios.length()}个场景",
                status = "success",
                failCode = obj.optString("queueId")
            )
            return null
        }
        val next = scenarios.optJSONObject(nextIndex) ?: return null
        val scenario = OpenClawSoakScenario(
            id = next.optString("id"),
            name = next.optString("name"),
            durationMinutes = next.optInt("durationMinutes"),
            rhythmMode = next.optString("rhythmMode"),
            pressureLevel = next.optString("pressureLevel", "standard"),
            createdAt = next.optLong("createdAt")
        )
        obj.put("currentIndex", nextIndex)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_QUEUE, obj.toString())
            .apply()
        if (finished != null) {
            OpenClawEventStore.appendRealEvent(
                context = context,
                npcId = "system",
                npcName = "系统",
                category = "ops.soak.queue.advance",
                content = "长稳队列推进：${finished.scenarioName} -> ${scenario.name}",
                status = "running",
                failCode = obj.optString("queueId")
            )
        }
        OpenClawSoakBaselineStore.startBaseline(context, scenario)
        return OpenClawSoakQueueState(
            queueId = obj.optString("queueId"),
            currentIndex = nextIndex,
            total = scenarios.length(),
            runningName = scenario.name,
            createdAt = obj.optLong("createdAt")
        )
    }

    fun getActiveQueue(context: Context): OpenClawSoakQueueState? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_QUEUE, null).orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val arr = obj.optJSONArray("scenarios") ?: return null
        val index = obj.optInt("currentIndex", 0)
        val running = arr.optJSONObject(index)?.optString("name").orEmpty()
        return OpenClawSoakQueueState(
            queueId = obj.optString("queueId"),
            currentIndex = index,
            total = arr.length(),
            runningName = running,
            createdAt = obj.optLong("createdAt")
        )
    }
}

