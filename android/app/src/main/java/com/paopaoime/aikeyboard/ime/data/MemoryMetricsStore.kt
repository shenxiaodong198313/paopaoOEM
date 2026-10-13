package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoryMetricsStore(context: Context) {
    private val prefs = context.getSharedPreferences("memory_metrics", Context.MODE_PRIVATE)
    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun recordWrite(layer: String, deduped: Boolean) {
        val keyPrefix = "write_${formatter.format(Date())}_$layer"
        increase("${keyPrefix}_total")
        if (deduped) increase("${keyPrefix}_deduped")
        appendRecent(
            JSONObject().apply {
                put("type", "write")
                put("layer", layer)
                put("deduped", deduped)
                put("ts", System.currentTimeMillis())
            }
        )
    }

    fun recordRetrieve(query: String, hitCount: Int, topScore: Double) {
        val keyPrefix = "retrieve_${formatter.format(Date())}"
        increase("${keyPrefix}_total")
        if (hitCount > 0) increase("${keyPrefix}_hit")
        appendRecent(
            JSONObject().apply {
                put("type", "retrieve")
                put("query", query.take(80))
                put("hitCount", hitCount)
                put("topScore", topScore)
                put("ts", System.currentTimeMillis())
            }
        )
    }

    private fun increase(key: String) {
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    private fun appendRecent(event: JSONObject) {
        val existing = prefs.getString("recent_events", "[]").orEmpty()
        val array = runCatching { JSONArray(existing) }.getOrDefault(JSONArray())
        array.put(event)
        while (array.length() > 80) {
            array.remove(0)
        }
        prefs.edit().putString("recent_events", array.toString()).apply()
    }
}
