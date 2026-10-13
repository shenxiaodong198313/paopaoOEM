package com.paopaoime.aikeyboard.materialhub

import android.content.Context
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.oem.OemDeveloperRuntimeBridge
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk
import com.paopaoime.aikeyboard.sdk.core.OemLog
import com.paopaoime.aikeyboard.sdk.core.OemMaterialDispatchMode
import com.paopaoime.aikeyboard.sdk.core.OemMaterialFetchCallback
import com.paopaoime.aikeyboard.sdk.core.OemMaterialFetchRequest
import com.paopaoime.aikeyboard.sdk.core.OemMaterialFetchResult
import com.paopaoime.aikeyboard.sdk.core.OemRemoteMaterialCatalogEvent
import com.paopaoime.aikeyboard.sdk.core.OemRemoteMaterialCatalogListener
import com.paopaoime.aikeyboard.sdk.core.OemRemoteMaterialItem
import com.paopaoime.aikeyboard.sdk.core.OemRemoteMaterialPart
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.fcitx.fcitx5.android.input.favorite.FavoriteAttachmentType
import org.fcitx.fcitx5.android.input.favorite.FavoritePart
import org.fcitx.fcitx5.android.input.favorite.FavoritePickerType
import org.fcitx.fcitx5.android.input.favorite.FavoriteRecord
import org.fcitx.fcitx5.android.input.favorite.FavoriteRecordSource
import org.json.JSONArray
import org.json.JSONObject

class OemRemoteMaterialService(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val syncStore = OemRemoteMaterialSyncStore(appContext)

    init {
        OemRemoteMaterialHostBridge.install(appContext)
    }

    suspend fun refreshRemoteCatalog(limit: Int = 10) = withContext(Dispatchers.IO) {
        val normalizedLimit = limit.coerceIn(1, 20)
        val dispatchMode = OemEnterpriseConfig.resolveMaterialDispatchMode(appContext)
        when (dispatchMode) {
            OemMaterialDispatchMode.LOCAL_ONLY -> {
                syncStore.writeCatalog(
                    OemRemoteMaterialCatalog(
                        lastCheckedAt = System.currentTimeMillis(),
                        materials = emptyList(),
                    ),
                )
                OemLog.d(TAG, "refreshRemoteCatalog skipped: material dispatch mode is LOCAL_ONLY")
                return@withContext
            }
            OemMaterialDispatchMode.HOST_ONLY -> {
                refreshRemoteCatalogFromHost(normalizedLimit)
                return@withContext
            }
            OemMaterialDispatchMode.DUAL_WRITE -> {
                val hostSuccess = runCatching {
                    refreshRemoteCatalogFromHost(normalizedLimit)
                }.onFailure { error ->
                    OemLog.w(TAG, "host material channel failed, fallback to cloud: ${error.message}", error)
                }.isSuccess
                if (hostSuccess) return@withContext
            }
            OemMaterialDispatchMode.CLOUD_ONLY -> Unit
        }
        val baseUrl = OemEnterpriseConfig.resolveApiBaseUrl(appContext).trimEnd('/')
        try {
            val fetchedMaterials = fetchRemoteCatalog(baseUrl, normalizedLimit)
            val nextCatalog = OemRemoteMaterialCatalog(
                lastCheckedAt = System.currentTimeMillis(),
                materials = fetchedMaterials.sortedByDescending { it.updatedAt },
            )
            syncStore.writeCatalog(nextCatalog)
        } catch (error: Throwable) {
            OemLog.e(TAG, "refreshRemoteCatalog failed, baseUrl=$baseUrl", error)
            throw error
        }
    }

    /**
     * 宿主回调通道：通过 [OemImeSdk.dispatchRemoteMaterialFetch] 向宿主请求最新企业素材列表，
     * 成功后整体替换本地缓存。任何失败都会抛出异常，由调用方决定是否回落域名通道。
     */
    private suspend fun refreshRemoteCatalogFromHost(limit: Int) {
        val hostMaterials = fetchCatalogFromHost(limit)
        val nextCatalog = OemRemoteMaterialCatalog(
            lastCheckedAt = System.currentTimeMillis(),
            materials = hostMaterials.sortedByDescending { it.updatedAt },
        )
        syncStore.writeCatalog(nextCatalog)
        OemLog.d(TAG, "refreshRemoteCatalog via host callback success, count=${hostMaterials.size}")
    }

    private suspend fun fetchCatalogFromHost(limit: Int): List<CachedRemoteMaterial> {
        OemDeveloperRuntimeBridge.ensureMaterialCallbacksInstalled(appContext)
        if (!OemImeSdk.isMaterialHostDelegateRegistered()) {
            throw IllegalStateException("宿主未注册素材库回调（OemMaterialHostDelegate）")
        }
        val request = OemMaterialFetchRequest(
            requestId = UUID.randomUUID().toString(),
            limit = limit,
            hostApplicationId = appContext.packageName,
            timestamp = System.currentTimeMillis(),
        )
        val result = withTimeoutOrNull(HOST_FETCH_TIMEOUT_MS) {
            suspendCancellableCoroutine<OemMaterialFetchResult?> { continuation ->
                val delivered = AtomicBoolean(false)
                val callback = OemMaterialFetchCallback { hostResult ->
                    if (delivered.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(hostResult)
                    }
                }
                val accepted = runCatching {
                    OemImeSdk.dispatchRemoteMaterialFetch(request, callback)
                }.getOrDefault(false)
                if (!accepted && delivered.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
        if (result == null) {
            throw IllegalStateException("素材库宿主回调超时或拒绝处理")
        }
        if (!result.success) {
            throw IllegalStateException(
                result.errorMessage?.takeIf { it.isNotBlank() } ?: "素材库宿主回调返回失败",
            )
        }
        return result.materials.mapNotNull { item -> mapHostMaterial(item) }
    }

    suspend fun listLatestRemoteFavorites(limit: Int = 10): List<FavoriteRecord> = withContext(Dispatchers.IO) {
        readCachedRemoteFavorites(limit)
    }

    fun readCachedRemoteFavorites(limit: Int = 10): List<FavoriteRecord> {
        val catalog = syncStore.readCatalog()
        return catalog.materials
            .sortedByDescending { it.updatedAt }
            .asSequence()
            .mapNotNull(::toFavoriteRecord)
            .take(limit.coerceIn(1, 20))
            .toList()
    }

    suspend fun syncRemoteFavorites(limit: Int = 10) = withContext(Dispatchers.IO) {
        refreshRemoteCatalog(limit)
    }

    private fun fetchRemoteCatalog(baseUrl: String, limit: Int): List<CachedRemoteMaterial> {
        val (listPath, summaries) = fetchListEnvelope(baseUrl, limit)
        val detailItems = ArrayList<CachedRemoteMaterial>()
        for (index in 0 until summaries.length()) {
            val item = summaries.optJSONObject(index) ?: continue
            val materialId = item.optString("materialId").trim()
            if (materialId.isBlank()) {
                continue
            }
            val material = runCatching {
                fetchMaterialDetail(baseUrl, listPath, materialId)
            }.onFailure { error ->
                OemLog.e(TAG, "fetchMaterialDetail failed, materialId=$materialId, path=$listPath", error)
            }.getOrNull()
            if (material != null) {
                detailItems += material
            }
        }
        return detailItems
    }

    private fun fetchListEnvelope(baseUrl: String, limit: Int): Pair<String, JSONArray> {
        val candidates = listOf(
            "/api/v1/material-library/materials",
            "/api/v1/oem/material-library-demo/materials",
        )
        candidates.forEach { path ->
            val url = "$baseUrl$path?limit=$limit"
            val response = executeJsonRequest(url)
            if (response == null || response.optBoolean("success").not()) {
                OemLog.w(TAG, "fetchListEnvelope skipped, path=$path, responseSuccess=${response?.optBoolean("success")}")
                return@forEach
            }
            val items = response.optJSONObject("data")?.optJSONArray("items")
            if (items != null) {
                return path to items
            }
        }
        return "/api/v1/oem/material-library-demo/materials" to JSONArray()
    }

    private fun fetchMaterialDetail(
        baseUrl: String,
        listPath: String,
        materialId: String,
    ): CachedRemoteMaterial? {
        val detailUrl = "$baseUrl${listPath.trimEnd('/')}/$materialId"
        val payload = executeJsonRequest(detailUrl) ?: return null
        if (!payload.optBoolean("success")) {
            return null
        }
        val data = payload.optJSONObject("data") ?: return null
        val partsArray = data.optJSONArray("parts") ?: JSONArray()
        val parts = ArrayList<CachedRemoteMaterialPart>()
        for (index in 0 until partsArray.length()) {
            val part = partsArray.optJSONObject(index) ?: continue
            val normalizedPart = mapRemotePart(baseUrl, materialId, part) ?: continue
            parts += normalizedPart
        }
        if (parts.isEmpty()) {
            return null
        }
        val sourceLabel = data.optString("sourceLabel").trim().ifBlank { "企业下发" }
        val createdAt = parseDateTimeMillis(data.optString("createdAt"))
        val updatedAt = parseDateTimeMillis(data.optString("updatedAt"))
        val material = CachedRemoteMaterial(
            materialId = materialId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            sourceLabel = sourceLabel,
            categoryId = data.optString("categoryId").trim().ifBlank { null },
            categoryName = data.optString("categoryName").trim().ifBlank { null },
            tags = parseStringList(data.optJSONArray("tags")),
            signature = "",
            parts = parts,
        )
        return material.copy(signature = buildMaterialSignature(material))
    }

    private fun mapRemotePart(
        baseUrl: String,
        materialId: String,
        part: JSONObject,
    ): CachedRemoteMaterialPart? {
        val partId = part.optString("partId").trim().ifBlank { "part_${materialId}_${System.currentTimeMillis()}" }
        val type = part.optString("type").trim().lowercase(Locale.ROOT)
        if (type == "text") {
            val text = part.optString("text").trim()
            if (text.isBlank()) {
                return null
            }
            return CachedRemoteMaterialPart.Text(
                partId = partId,
                text = text,
            )
        }
        val displayName = part.optString("displayName").trim()
        val attachmentType = mapAttachmentType(part.optString("attachmentType"))
        val pickerType = mapPickerType(attachmentType)
        val mimeType = part.optString("mimeType").trim().ifBlank { defaultMimeType(attachmentType) }
        val downloadUrl = resolveDownloadUrl(baseUrl, part.optString("downloadUrl").trim())
        if (displayName.isBlank() || downloadUrl.isBlank()) {
            return null
        }
        return CachedRemoteMaterialPart.Attachment(
            partId = partId,
            displayName = displayName,
            mimeType = mimeType,
            attachmentType = attachmentType,
            pickerType = pickerType,
            downloadUrl = downloadUrl,
            fileSizeBytes = part.optLong("fileSizeBytes"),
        )
    }

    private fun toFavoriteRecord(material: CachedRemoteMaterial): FavoriteRecord? {
        val parts = material.parts.mapNotNull { part ->
            when (part) {
                is CachedRemoteMaterialPart.Text -> FavoritePart.Text(
                    id = part.partId,
                    text = part.text,
                )
                is CachedRemoteMaterialPart.Attachment -> FavoritePart.Attachment(
                    id = part.partId,
                    displayName = part.displayName,
                    storedPath = "",
                    mimeType = part.mimeType,
                    attachmentType = part.attachmentType,
                    pickerType = part.pickerType,
                    fileSizeBytes = part.fileSizeBytes,
                    downloadUrl = part.downloadUrl,
                )
            }
        }
        if (parts.isEmpty()) {
            return null
        }
        return FavoriteRecord(
            id = "remote_${material.materialId}",
            directoryName = "企业素材",
            createdAt = material.createdAt,
            updatedAt = material.updatedAt,
            parts = parts,
            source = FavoriteRecordSource.REMOTE,
            sourceLabel = material.sourceLabel,
            remoteMaterialId = material.materialId,
            categoryId = material.categoryId,
            categoryName = material.categoryName,
            tags = material.tags,
        )
    }

    private fun executeJsonRequest(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                OemLog.w(TAG, "executeJsonRequest failed, code=${response.code}, url=$url")
                return null
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                OemLog.w(TAG, "executeJsonRequest empty body, url=$url")
                return null
            }
            return runCatching { JSONObject(body) }
                .onFailure { error ->
                    OemLog.e(TAG, "executeJsonRequest invalid json, url=$url, body=${body.take(200)}", error)
                }
                .getOrNull()
        }
    }

    private fun resolveDownloadUrl(baseUrl: String, rawUrl: String): String {
        if (rawUrl.isBlank()) {
            return ""
        }
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl
        }
        val base = baseUrl.toHttpUrlOrNull() ?: return rawUrl
        return base.resolve(rawUrl)?.toString().orEmpty()
    }

    private fun parseDateTimeMillis(value: String): Long {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            return System.currentTimeMillis()
        }
        return runCatching {
            DATE_TIME_FORMAT.parse(normalized)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }

    private companion object {
        const val TAG = "OemRemoteMaterial"
        val DATE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }
}

/** 宿主回调拉取企业素材的单次超时时间。 */
private const val HOST_FETCH_TIMEOUT_MS = 30_000L

private fun mapAttachmentType(raw: String): FavoriteAttachmentType {
    return when (raw.trim().lowercase(Locale.ROOT)) {
        "image" -> FavoriteAttachmentType.IMAGE
        "video" -> FavoriteAttachmentType.VIDEO
        else -> FavoriteAttachmentType.DOCUMENT
    }
}

private fun mapPickerType(type: FavoriteAttachmentType): FavoritePickerType {
    return when (type) {
        FavoriteAttachmentType.IMAGE -> FavoritePickerType.IMAGE
        FavoriteAttachmentType.VIDEO -> FavoritePickerType.VIDEO
        FavoriteAttachmentType.DOCUMENT -> FavoritePickerType.FILE
        FavoriteAttachmentType.NONE -> FavoritePickerType.TEXT
    }
}

private fun defaultMimeType(type: FavoriteAttachmentType): String {
    return when (type) {
        FavoriteAttachmentType.IMAGE -> "image/*"
        FavoriteAttachmentType.VIDEO -> "video/*"
        FavoriteAttachmentType.DOCUMENT -> "application/*"
        FavoriteAttachmentType.NONE -> "text/plain"
    }
}

private fun buildMaterialSignature(material: CachedRemoteMaterial): String {
    return buildString {
        append(material.materialId)
        append('|')
        append(material.updatedAt)
        append('|')
        material.parts.forEach { part ->
            when (part) {
                is CachedRemoteMaterialPart.Text -> {
                    append("text:")
                    append(part.partId)
                    append(':')
                    append(part.text)
                }
                is CachedRemoteMaterialPart.Attachment -> {
                    append("attachment:")
                    append(part.partId)
                    append(':')
                    append(part.displayName)
                    append(':')
                    append(part.mimeType)
                    append(':')
                    append(part.attachmentType.name)
                    append(':')
                    append(part.downloadUrl)
                }
            }
            append('|')
        }
    }
}

/**
 * 将宿主回调返回的企业素材映射为本地缓存模型；
 * 无效素材（ID 为空或无有效内容块）返回 null。
 */
private fun mapHostMaterial(item: OemRemoteMaterialItem): CachedRemoteMaterial? {
    val materialId = item.materialId.trim()
    if (materialId.isBlank()) {
        return null
    }
    val parts = item.parts.mapNotNull { part ->
        when (part) {
            is OemRemoteMaterialPart.Text -> {
                val text = part.text.trim()
                if (text.isBlank()) {
                    null
                } else {
                    CachedRemoteMaterialPart.Text(
                        partId = part.partId.trim().ifBlank { "host_text_${UUID.randomUUID()}" },
                        text = text,
                    )
                }
            }
            is OemRemoteMaterialPart.Attachment -> {
                val displayName = part.displayName.trim()
                val downloadUrl = part.downloadUrl.trim()
                if (displayName.isBlank() || downloadUrl.isBlank()) {
                    null
                } else {
                    val attachmentType = mapAttachmentType(part.attachmentType)
                    CachedRemoteMaterialPart.Attachment(
                        partId = part.partId.trim().ifBlank { "host_attachment_${UUID.randomUUID()}" },
                        displayName = displayName,
                        mimeType = part.mimeType.trim().ifBlank { defaultMimeType(attachmentType) },
                        attachmentType = attachmentType,
                        pickerType = mapPickerType(attachmentType),
                        downloadUrl = downloadUrl,
                        fileSizeBytes = part.fileSizeBytes,
                    )
                }
            }
        }
    }
    if (parts.isEmpty()) {
        return null
    }
    val now = System.currentTimeMillis()
    val material = CachedRemoteMaterial(
        materialId = materialId,
        createdAt = if (item.createdAt > 0L) item.createdAt else now,
        updatedAt = if (item.updatedAt > 0L) item.updatedAt else now,
        sourceLabel = item.sourceLabel.trim().ifBlank { "企业下发" },
        categoryId = item.categoryId?.trim()?.ifBlank { null },
        categoryName = item.categoryName?.trim()?.ifBlank { null },
        tags = item.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        signature = "",
        parts = parts,
    )
    return material.copy(signature = buildMaterialSignature(material))
}

/**
 * 企业素材宿主推送桥接：把宿主通过 [OemImeSdk.notifyRemoteMaterialUpserted] /
 * [OemImeSdk.notifyRemoteMaterialRemoved] / [OemImeSdk.invalidateRemoteMaterialCatalog]
 * 推送的增删改事件落到本地素材缓存。
 *
 * [install] 幂等，可重复调用；由 [OemRemoteMaterialService] 构造与输入法服务启动时触发。
 */
internal object OemRemoteMaterialHostBridge {

    private const val TAG = "OemRemoteMaterialBridge"

    @Volatile
    private var installed = false

    private val installLock = Any()

    fun install(context: Context) {
        if (installed) return
        synchronized(installLock) {
            if (installed) return
            installed = true
            val appContext = context.applicationContext
            OemImeSdk.installRemoteMaterialCatalogListener(OemRemoteMaterialCatalogListener { event ->
                runCatching {
                    applyEvent(appContext, event)
                }.onFailure { error ->
                    OemLog.e(TAG, "apply remote material catalog event failed", error)
                }
            })
        }
    }

    private fun applyEvent(context: Context, event: OemRemoteMaterialCatalogEvent) {
        when (event) {
            is OemRemoteMaterialCatalogEvent.Upsert -> applyUpsert(context, event.item)
            is OemRemoteMaterialCatalogEvent.Remove -> applyRemove(context, event.materialId)
            OemRemoteMaterialCatalogEvent.Invalidate -> {
                OemRemoteMaterialAutoSync.warmUp(context, "host_push_invalidate", force = true)
            }
        }
    }

    private fun applyUpsert(context: Context, item: OemRemoteMaterialItem) {
        val mapped = mapHostMaterial(item) ?: return
        val store = OemRemoteMaterialSyncStore(context)
        val catalog = store.readCatalog()
        val merged = catalog.materials.filterNot { it.materialId == mapped.materialId } + mapped
        store.writeCatalog(
            OemRemoteMaterialCatalog(
                lastCheckedAt = System.currentTimeMillis(),
                materials = merged.sortedByDescending { it.updatedAt },
            ),
        )
        OemLog.d(TAG, "applyUpsert materialId=${mapped.materialId} total=${merged.size}")
    }

    private fun applyRemove(context: Context, materialId: String) {
        val store = OemRemoteMaterialSyncStore(context)
        val catalog = store.readCatalog()
        if (catalog.materials.none { it.materialId == materialId }) return
        store.writeCatalog(
            OemRemoteMaterialCatalog(
                lastCheckedAt = catalog.lastCheckedAt,
                materials = catalog.materials.filterNot { it.materialId == materialId },
            ),
        )
        OemLog.d(TAG, "applyRemove materialId=$materialId")
    }
}

private class OemRemoteMaterialSyncStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readCatalog(): OemRemoteMaterialCatalog {
        val raw = prefs.getString(KEY_CATALOG_JSON, "").orEmpty()
        if (raw.isBlank()) {
            return OemRemoteMaterialCatalog()
        }
        return runCatching {
            parseCatalog(JSONObject(raw))
        }.getOrElse {
            OemLog.e("OemRemoteMaterial", "readCatalog failed", it)
            OemRemoteMaterialCatalog()
        }
    }

    fun writeCatalog(catalog: OemRemoteMaterialCatalog) {
        prefs.edit()
            .putString(KEY_CATALOG_JSON, catalog.toJson().toString())
            .commit()
    }

    private fun parseCatalog(json: JSONObject): OemRemoteMaterialCatalog {
        val materialsArray = json.optJSONArray("materials") ?: JSONArray()
        val materials = ArrayList<CachedRemoteMaterial>(materialsArray.length())
        for (index in 0 until materialsArray.length()) {
            val item = materialsArray.optJSONObject(index) ?: continue
            parseMaterial(item)?.let(materials::add)
        }
        return OemRemoteMaterialCatalog(
            lastCheckedAt = json.optLong("lastCheckedAt"),
            materials = materials,
        )
    }

    private fun parseMaterial(json: JSONObject): CachedRemoteMaterial? {
        val materialId = json.optString("materialId").trim()
        if (materialId.isBlank()) {
            return null
        }
        val partsArray = json.optJSONArray("parts") ?: JSONArray()
        val parts = ArrayList<CachedRemoteMaterialPart>(partsArray.length())
        for (index in 0 until partsArray.length()) {
            val item = partsArray.optJSONObject(index) ?: continue
            parsePart(item)?.let(parts::add)
        }
        if (parts.isEmpty()) {
            return null
        }
        return CachedRemoteMaterial(
            materialId = materialId,
            createdAt = json.optLong("createdAt"),
            updatedAt = json.optLong("updatedAt"),
            sourceLabel = json.optString("sourceLabel").trim().ifBlank { "企业下发" },
            categoryId = json.optString("categoryId").trim().ifBlank { null },
            categoryName = json.optString("categoryName").trim().ifBlank { null },
            tags = parseStringList(json.optJSONArray("tags")),
            signature = json.optString("signature").trim(),
            parts = parts,
        )
    }

    private fun parsePart(json: JSONObject): CachedRemoteMaterialPart? {
        val type = json.optString("type").trim().lowercase(Locale.ROOT)
        val partId = json.optString("partId").trim()
        if (partId.isBlank()) {
            return null
        }
        return if (type == "text") {
            val text = json.optString("text").trim()
            if (text.isBlank()) null else CachedRemoteMaterialPart.Text(
                partId = partId,
                text = text,
            )
        } else {
            val displayName = json.optString("displayName").trim()
            val downloadUrl = json.optString("downloadUrl").trim()
            if (displayName.isBlank() || downloadUrl.isBlank()) {
                null
            } else {
                CachedRemoteMaterialPart.Attachment(
                    partId = partId,
                    displayName = displayName,
                    mimeType = json.optString("mimeType").trim().ifBlank { "application/*" },
                    attachmentType = when (json.optString("attachmentType").trim()) {
                        FavoriteAttachmentType.IMAGE.name -> FavoriteAttachmentType.IMAGE
                        FavoriteAttachmentType.VIDEO.name -> FavoriteAttachmentType.VIDEO
                        else -> FavoriteAttachmentType.DOCUMENT
                    },
                    pickerType = when (json.optString("pickerType").trim()) {
                        FavoritePickerType.IMAGE.name -> FavoritePickerType.IMAGE
                        FavoritePickerType.VIDEO.name -> FavoritePickerType.VIDEO
                        else -> FavoritePickerType.FILE
                    },
                    downloadUrl = downloadUrl,
                    fileSizeBytes = json.optLong("fileSizeBytes"),
                )
            }
        }
    }

    private companion object {
        private const val PREFS_NAME = "oem_remote_material_sync_store"
        private const val KEY_CATALOG_JSON = "catalog_json"
    }
}

private data class OemRemoteMaterialCatalog(
    val lastCheckedAt: Long = 0L,
    val materials: List<CachedRemoteMaterial> = emptyList(),
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("lastCheckedAt", lastCheckedAt)
            put("materials", JSONArray().apply {
                materials.forEach { material ->
                    put(material.toJson())
                }
            })
        }
    }
}

private data class CachedRemoteMaterial(
    val materialId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceLabel: String,
    val categoryId: String?,
    val categoryName: String?,
    val tags: List<String>,
    val signature: String,
    val parts: List<CachedRemoteMaterialPart>,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("materialId", materialId)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("sourceLabel", sourceLabel)
            put("categoryId", categoryId)
            put("categoryName", categoryName)
            put("tags", JSONArray(tags))
            put("signature", signature)
            put("parts", JSONArray().apply {
                parts.forEach { part ->
                    put(part.toJson())
                }
            })
        }
    }
}

private sealed class CachedRemoteMaterialPart {
    abstract val partId: String

    abstract fun toJson(): JSONObject

    data class Text(
        override val partId: String,
        val text: String,
    ) : CachedRemoteMaterialPart() {
        override fun toJson(): JSONObject {
            return JSONObject().apply {
                put("type", "text")
                put("partId", partId)
                put("text", text)
            }
        }
    }

    data class Attachment(
        override val partId: String,
        val displayName: String,
        val mimeType: String,
        val attachmentType: FavoriteAttachmentType,
        val pickerType: FavoritePickerType,
        val downloadUrl: String,
        val fileSizeBytes: Long,
    ) : CachedRemoteMaterialPart() {
        override fun toJson(): JSONObject {
            return JSONObject().apply {
                put("type", "attachment")
                put("partId", partId)
                put("displayName", displayName)
                put("mimeType", mimeType)
                put("attachmentType", attachmentType.name)
                put("pickerType", pickerType.name)
                put("downloadUrl", downloadUrl)
                put("fileSizeBytes", fileSizeBytes)
            }
        }
    }
}

private fun parseStringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    val result = ArrayList<String>(array.length())
    for (index in 0 until array.length()) {
        val value = array.optString(index).trim()
        if (value.isNotBlank() && result.none { it.equals(value, ignoreCase = true) }) {
            result += value
        }
    }
    return result
}
