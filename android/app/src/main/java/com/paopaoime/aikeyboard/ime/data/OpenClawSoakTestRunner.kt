package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class OpenClawSoakScenario(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val rhythmMode: String,
    val pressureLevel: String,
    val createdAt: Long
)

object OpenClawSoakTestRunner {
    private const val PREFS_NAME = "openclaw_soak_runner"
    private const val KEY_SCENARIOS = "scenarios"
    private const val MAX_SCENARIOS = 120

    fun scheduleScenario(
        context: Context,
        name: String,
        durationMinutes: Int,
        rhythmMode: String,
        pressureLevel: String = "standard"
    ): OpenClawSoakScenario {
        val item = OpenClawSoakScenario(
            id = UUID.randomUUID().toString(),
            name = name,
            durationMinutes = durationMinutes.coerceAtLeast(30),
            rhythmMode = rhythmMode,
            pressureLevel = pressureLevel,
            createdAt = System.currentTimeMillis()
        )
        val list = listScenarios(context).toMutableList()
        list.add(0, item)
        if (list.size > MAX_SCENARIOS) {
            list.removeLast()
        }
        persist(context, list)
        return item
    }

    fun listScenarios(context: Context): List<OpenClawSoakScenario> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SCENARIOS, null)
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
        val list = mutableListOf<OpenClawSoakScenario>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                OpenClawSoakScenario(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    durationMinutes = obj.optInt("durationMinutes"),
                    rhythmMode = obj.optString("rhythmMode"),
                    pressureLevel = obj.optString("pressureLevel", "standard"),
                    createdAt = obj.optLong("createdAt")
                )
            )
        }
        return list.sortedByDescending { it.createdAt }
    }

    fun scheduleLongCyclePack(context: Context): List<OpenClawSoakScenario> {
        val scenarios = listOf(
            Triple("长周期-标准稳态", 360, "standard"),
            Triple("长周期-高压吞吐", 480, "high_output"),
            Triple("长周期-夜间保守", 420, "relaxed")
        )
        return scenarios.mapIndexed { index, item ->
            val pressure = when (index) {
                0 -> "standard"
                1 -> "high"
                else -> "conservative"
            }
            scheduleScenario(
                context = context,
                name = item.first,
                durationMinutes = item.second,
                rhythmMode = item.third,
                pressureLevel = pressure
            )
        }
    }

    private fun persist(context: Context, data: List<OpenClawSoakScenario>) {
        val arr = JSONArray()
        data.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("durationMinutes", item.durationMinutes)
                    .put("rhythmMode", item.rhythmMode)
                    .put("pressureLevel", item.pressureLevel)
                    .put("createdAt", item.createdAt)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCENARIOS, arr.toString())
            .apply()
    }
}
