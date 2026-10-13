package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class OpenClawTaskAsset(
    val assetId: String,
    val contentType: String,
    val producerTaskId: String,
    val producerRunId: String,
    val producerNpcId: String,
    val summary: String,
    val payload: String,
    val path: String,
    val createdAt: Long,
    val consumedPublishTaskIds: List<String>
)

object OpenClawTaskAssetStore {
    private const val PREFS_NAME = "openclaw_asset_store"
    private const val KEY_ASSETS = "assets"
    private const val MAX_ASSETS = 240

    private val cache = mutableListOf<OpenClawTaskAsset>()
    @Volatile
    private var loaded = false

    fun addGeneratedAsset(
        context: Context,
        contentType: OperationContentType,
        producerTaskId: String,
        producerRunId: String,
        producerNpcId: String,
        summary: String,
        payload: String,
        path: String
    ): OpenClawTaskAsset {
        ensureCache(context)
        synchronized(this) {
            val asset = OpenClawTaskAsset(
                assetId = "asset-${UUID.randomUUID().toString().replace("-", "").takeLast(12)}",
                contentType = contentType.name,
                producerTaskId = producerTaskId,
                producerRunId = producerRunId,
                producerNpcId = producerNpcId,
                summary = summary,
                payload = payload,
                path = path,
                createdAt = System.currentTimeMillis(),
                consumedPublishTaskIds = emptyList()
            )
            cache.add(asset)
            trimOverflow()
            persist(context)
            return asset
        }
    }

    fun findReusableAsset(
        context: Context,
        contentType: OperationContentType,
        limitMinutes: Int = 240
    ): OpenClawTaskAsset? {
        ensureCache(context)
        val now = System.currentTimeMillis()
        val maxAge = limitMinutes.coerceAtLeast(1) * 60_000L
        return synchronized(this) {
            cache.asReversed().firstOrNull {
                it.contentType == contentType.name && (now - it.createdAt) <= maxAge
            }
        }
    }

    fun markConsumedByPublishTask(context: Context, assetId: String, publishTaskId: String) {
        if (assetId.isBlank() || publishTaskId.isBlank()) return
        ensureCache(context)
        synchronized(this) {
            val index = cache.indexOfFirst { it.assetId == assetId }
            if (index < 0) return
            val target = cache[index]
            if (target.consumedPublishTaskIds.contains(publishTaskId)) return
            cache[index] = target.copy(
                consumedPublishTaskIds = target.consumedPublishTaskIds + publishTaskId
            )
            persist(context)
        }
    }

    private fun ensureCache(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            cache.clear()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = parseArray(prefs.getString(KEY_ASSETS, null))
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val consumeArray = obj.optJSONArray("consumedPublishTaskIds") ?: JSONArray()
                val consumed = buildList {
                    for (j in 0 until consumeArray.length()) {
                        val id = consumeArray.optString(j)
                        if (id.isNotBlank()) add(id)
                    }
                }
                cache.add(
                    OpenClawTaskAsset(
                        assetId = obj.optString("assetId"),
                        contentType = obj.optString("contentType"),
                        producerTaskId = obj.optString("producerTaskId"),
                        producerRunId = obj.optString("producerRunId"),
                        producerNpcId = obj.optString("producerNpcId"),
                        summary = obj.optString("summary"),
                        payload = obj.optString("payload"),
                        path = obj.optString("path"),
                        createdAt = obj.optLong("createdAt", 0L),
                        consumedPublishTaskIds = consumed
                    )
                )
            }
            loaded = true
            trimOverflow()
            persist(context)
        }
    }

    private fun trimOverflow() {
        while (cache.size > MAX_ASSETS) {
            cache.removeAt(0)
        }
    }

    private fun persist(context: Context) {
        val array = JSONArray()
        cache.forEach { asset ->
            val consumed = JSONArray()
            asset.consumedPublishTaskIds.forEach { consumed.put(it) }
            array.put(
                JSONObject()
                    .put("assetId", asset.assetId)
                    .put("contentType", asset.contentType)
                    .put("producerTaskId", asset.producerTaskId)
                    .put("producerRunId", asset.producerRunId)
                    .put("producerNpcId", asset.producerNpcId)
                    .put("summary", asset.summary)
                    .put("payload", asset.payload)
                    .put("path", asset.path)
                    .put("createdAt", asset.createdAt)
                    .put("consumedPublishTaskIds", consumed)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ASSETS, array.toString())
            .apply()
    }

    private fun parseArray(raw: String?): JSONArray {
        if (raw.isNullOrBlank()) return JSONArray()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }
}
