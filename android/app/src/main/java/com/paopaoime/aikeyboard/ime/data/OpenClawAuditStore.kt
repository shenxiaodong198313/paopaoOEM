package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class OpenClawAuditRecord(
    val id: String,
    val timestamp: Long,
    val actor: String,
    val actionCode: String,
    val level: String,
    val detail: String,
    val result: String
)

object OpenClawAuditStore {
    private const val PREFS_NAME = "openclaw_audit_store"
    private const val KEY_AUDITS = "audits"
    private const val MAX_AUDITS = 320

    private val cache = mutableListOf<OpenClawAuditRecord>()
    @Volatile
    private var loaded = false

    fun append(context: Context, record: OpenClawAuditRecord) {
        ensureCache(context)
        synchronized(this) {
            cache.add(record)
            if (cache.size > MAX_AUDITS) {
                val overflow = cache.size - MAX_AUDITS
                repeat(overflow) { cache.removeAt(0) }
            }
            persist(context)
        }
    }

    fun list(context: Context, limit: Int = 30): List<OpenClawAuditRecord> {
        ensureCache(context)
        return synchronized(this) {
            cache.asReversed().take(limit.coerceAtLeast(1))
        }
    }

    private fun ensureCache(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            cache.clear()
            val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_AUDITS, null)
            val arr = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                cache.add(
                    OpenClawAuditRecord(
                        id = obj.optString("id"),
                        timestamp = obj.optLong("timestamp"),
                        actor = obj.optString("actor"),
                        actionCode = obj.optString("actionCode"),
                        level = obj.optString("level"),
                        detail = obj.optString("detail"),
                        result = obj.optString("result")
                    )
                )
            }
            loaded = true
        }
    }

    private fun persist(context: Context) {
        val arr = JSONArray()
        cache.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("timestamp", item.timestamp)
                    .put("actor", item.actor)
                    .put("actionCode", item.actionCode)
                    .put("level", item.level)
                    .put("detail", item.detail)
                    .put("result", item.result)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AUDITS, arr.toString())
            .apply()
    }
}
