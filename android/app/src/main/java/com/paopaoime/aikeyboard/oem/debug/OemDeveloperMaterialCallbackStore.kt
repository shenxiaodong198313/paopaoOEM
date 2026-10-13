package com.paopaoime.aikeyboard.oem.debug

import android.content.Context
import com.paopaoime.aikeyboard.sdk.core.OemMaterialDispatchMode
import com.paopaoime.aikeyboard.sdk.core.OemMaterialNavigationMode
import com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyItem
import com.paopaoime.aikeyboard.sdk.core.OemRemoteMaterialItem
import com.paopaoime.aikeyboard.sdk.core.OemRemoteMaterialPart
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class OemDeveloperMaterialTaxonomyRecord(
    val id: String,
    val name: String,
    val sort: Int = 0,
    val enabled: Boolean = true,
)

data class OemDeveloperMaterialCallbackSnapshot(
    val dispatchMode: OemMaterialDispatchMode = OemMaterialDispatchMode.HOST_ONLY,
    val navigationMode: OemMaterialNavigationMode = OemMaterialNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY,
    val categories: List<OemDeveloperMaterialTaxonomyRecord> = emptyList(),
    val tags: List<OemDeveloperMaterialTaxonomyRecord> = emptyList(),
)

object OemDeveloperMaterialCallbackStore {
    private const val STORE_FILE_NAME = "oem_developer_material_callback.json"

    private const val JSON_DISPATCH_MODE = "dispatchMode"
    private const val JSON_NAVIGATION_MODE = "navigationMode"
    private const val JSON_CATEGORIES = "categories"
    private const val JSON_TAGS = "tags"
    private const val JSON_ID = "id"
    private const val JSON_NAME = "name"
    private const val JSON_SORT = "sort"
    private const val JSON_ENABLED = "enabled"

    private val fileLock = Any()

    fun hasPersistedSnapshot(context: Context): Boolean {
        synchronized(fileLock) {
            return storeFile(context).exists()
        }
    }

    fun load(context: Context): OemDeveloperMaterialCallbackSnapshot {
        synchronized(fileLock) {
            val file = storeFile(context)
            if (!file.exists()) {
                val seeded = defaultSnapshot()
                writeSnapshot(context, seeded)
                return seeded
            }
            return runCatching {
                parseSnapshot(file.readText())
            }.getOrElse {
                val seeded = defaultSnapshot()
                writeSnapshot(context, seeded)
                seeded
            }
        }
    }

    fun saveModes(
        context: Context,
        dispatchMode: OemMaterialDispatchMode,
        navigationMode: OemMaterialNavigationMode,
    ): OemDeveloperMaterialCallbackSnapshot {
        synchronized(fileLock) {
            val current = load(context)
            val next = current.copy(
                dispatchMode = dispatchMode,
                navigationMode = navigationMode,
            )
            writeSnapshot(context, next)
            return next
        }
    }

    fun resetToDemo(context: Context): OemDeveloperMaterialCallbackSnapshot {
        synchronized(fileLock) {
            val snapshot = defaultSnapshot()
            writeSnapshot(context, snapshot)
            return snapshot
        }
    }

    fun clear(context: Context): OemDeveloperMaterialCallbackSnapshot {
        synchronized(fileLock) {
            val snapshot = OemDeveloperMaterialCallbackSnapshot()
            writeSnapshot(context, snapshot)
            return snapshot
        }
    }

    fun upsertCategory(
        context: Context,
        id: String?,
        name: String,
    ): OemDeveloperMaterialCallbackSnapshot {
        synchronized(fileLock) {
            val current = load(context)
            val nextCategories = upsertRecord(current.categories, id = id, name = name)
            val next = current.copy(categories = nextCategories)
            writeSnapshot(context, next)
            return next
        }
    }

    fun deleteCategory(context: Context, id: String): OemDeveloperMaterialCallbackSnapshot {
        synchronized(fileLock) {
            val current = load(context)
            val next = current.copy(categories = current.categories.filterNot { it.id == id })
            writeSnapshot(context, next)
            return next
        }
    }

    fun upsertTag(
        context: Context,
        id: String?,
        name: String,
    ): OemDeveloperMaterialCallbackSnapshot {
        synchronized(fileLock) {
            val current = load(context)
            val nextTags = upsertRecord(current.tags, id = id, name = name)
            val next = current.copy(tags = nextTags)
            writeSnapshot(context, next)
            return next
        }
    }

    fun deleteTag(context: Context, id: String): OemDeveloperMaterialCallbackSnapshot {
        synchronized(fileLock) {
            val current = load(context)
            val next = current.copy(tags = current.tags.filterNot { it.id == id })
            writeSnapshot(context, next)
            return next
        }
    }

    fun buildTaxonomyItems(context: Context): Pair<List<OemMaterialTaxonomyItem>, List<OemMaterialTaxonomyItem>> {
        val snapshot = load(context)
        return snapshot.categories.map { it.toTaxonomyItem() } to snapshot.tags.map { it.toTaxonomyItem() }
    }

    fun buildRemoteMaterials(context: Context): List<OemRemoteMaterialItem> {
        val snapshot = load(context)
        val now = System.currentTimeMillis()
        val tags = snapshot.tags.filter { it.enabled }
        val categories = snapshot.categories.filter { it.enabled }
        val results = mutableListOf<OemRemoteMaterialItem>()

        results += OemRemoteMaterialItem(
            materialId = "developer_welcome_note",
            sourceLabel = "开发者宿主回调",
            createdAt = now - 10_000,
            updatedAt = now - 10_000,
            tags = tags.take(2).map { it.name },
            parts = listOf(
                OemRemoteMaterialPart.Text(
                    partId = "text_welcome",
                    text = "这是开发者模块生成的宿主回调测试文案。你可以修改分类、标签，再打开素材库验证双层导航和标签搜索是否生效。",
                ),
            ),
        )

        if (categories.isEmpty()) {
            results += OemRemoteMaterialItem(
                materialId = "developer_uncategorized_note",
                sourceLabel = "开发者宿主回调",
                createdAt = now - 9_000,
                updatedAt = now - 9_000,
                tags = tags.drop(1).take(2).map { it.name },
                parts = listOf(
                    OemRemoteMaterialPart.Text(
                        partId = "text_uncategorized",
                        text = "当前没有自定义分类，素材库会自动回退为单行类型导航。你可以先在开发者页新增分类，再回到素材库查看第二行分类胶囊。",
                    ),
                ),
            )
        } else {
            categories.forEachIndexed { index, category ->
                val assignedTags = when {
                    tags.isEmpty() -> emptyList()
                    tags.size == 1 -> listOf(tags.first().name)
                    else -> listOf(tags[index % tags.size].name, tags[(index + 1) % tags.size].name).distinct()
                }
                results += OemRemoteMaterialItem(
                    materialId = "developer_category_${category.id}",
                    sourceLabel = "开发者宿主回调",
                    createdAt = now - (8_000 - index * 500L),
                    updatedAt = now - (8_000 - index * 500L),
                    categoryId = category.id,
                    categoryName = category.name,
                    tags = assignedTags,
                    parts = listOf(
                        OemRemoteMaterialPart.Text(
                            partId = "text_category_${category.id}",
                            text = buildString {
                                append("分类【")
                                append(category.name)
                                append("】的宿主回调测试素材。")
                                if (assignedTags.isNotEmpty()) {
                                    append(" 当前标签：")
                                    append(assignedTags.joinToString("、"))
                                    append("。")
                                }
                                append(" 你可以在素材库搜索分类名、标签名，确认是否能命中。")
                            },
                        ),
                    ),
                )
            }
        }
        return results.sortedByDescending { it.updatedAt }
    }

    private fun upsertRecord(
        current: List<OemDeveloperMaterialTaxonomyRecord>,
        id: String?,
        name: String,
    ): List<OemDeveloperMaterialTaxonomyRecord> {
        val normalizedName = name.trim()
        val existingId = id?.trim().orEmpty()
        val nextId = existingId.ifBlank {
            "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        }
        val nextSort = current.maxOfOrNull { it.sort }?.plus(10) ?: 10
        val nextRecord = OemDeveloperMaterialTaxonomyRecord(
            id = nextId,
            name = normalizedName,
            sort = current.firstOrNull { it.id == nextId }?.sort ?: nextSort,
            enabled = true,
        )
        return buildList {
            var replaced = false
            current.forEach { record ->
                if (record.id == nextId) {
                    add(nextRecord)
                    replaced = true
                } else {
                    add(record)
                }
            }
            if (!replaced) {
                add(nextRecord)
            }
        }.sortedWith(compareBy<OemDeveloperMaterialTaxonomyRecord> { it.sort }.thenBy { it.name })
    }

    private fun OemDeveloperMaterialTaxonomyRecord.toTaxonomyItem(): OemMaterialTaxonomyItem {
        return OemMaterialTaxonomyItem(
            taxonomyId = id,
            name = name,
            sort = sort,
            enabled = enabled,
        )
    }

    private fun storeFile(context: Context): File {
        return File(context.applicationContext.filesDir, STORE_FILE_NAME)
    }

    private fun writeSnapshot(context: Context, snapshot: OemDeveloperMaterialCallbackSnapshot) {
        val file = storeFile(context)
        file.parentFile?.mkdirs()
        file.writeText(
            JSONObject()
                .put(JSON_DISPATCH_MODE, snapshot.dispatchMode.name)
                .put(JSON_NAVIGATION_MODE, snapshot.navigationMode.name)
                .put(JSON_CATEGORIES, JSONArray(writeRecords(snapshot.categories)))
                .put(JSON_TAGS, JSONArray(writeRecords(snapshot.tags)))
                .toString(),
        )
    }

    private fun parseSnapshot(raw: String): OemDeveloperMaterialCallbackSnapshot {
        val json = JSONObject(raw)
        return OemDeveloperMaterialCallbackSnapshot(
            dispatchMode = OemMaterialDispatchMode.parse(json.optString(JSON_DISPATCH_MODE)),
            navigationMode = OemMaterialNavigationMode.parse(json.optString(JSON_NAVIGATION_MODE)),
            categories = readRecords(json.optJSONArray(JSON_CATEGORIES)),
            tags = readRecords(json.optJSONArray(JSON_TAGS)),
        )
    }

    private fun readRecords(array: JSONArray?): List<OemDeveloperMaterialTaxonomyRecord> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString(JSON_ID).trim()
                val name = item.optString(JSON_NAME).trim()
                if (id.isBlank() || name.isBlank()) continue
                add(
                    OemDeveloperMaterialTaxonomyRecord(
                        id = id,
                        name = name,
                        sort = item.optInt(JSON_SORT, (index + 1) * 10),
                        enabled = item.optBoolean(JSON_ENABLED, true),
                    ),
                )
            }
        }.sortedWith(compareBy<OemDeveloperMaterialTaxonomyRecord> { it.sort }.thenBy { it.name })
    }

    private fun writeRecords(records: List<OemDeveloperMaterialTaxonomyRecord>): List<JSONObject> {
        return records.map { record ->
            JSONObject()
                .put(JSON_ID, record.id)
                .put(JSON_NAME, record.name)
                .put(JSON_SORT, record.sort)
                .put(JSON_ENABLED, record.enabled)
        }
    }

    private fun defaultSnapshot(): OemDeveloperMaterialCallbackSnapshot {
        return OemDeveloperMaterialCallbackSnapshot(
            dispatchMode = OemMaterialDispatchMode.HOST_ONLY,
            navigationMode = OemMaterialNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY,
            categories = listOf(
                OemDeveloperMaterialTaxonomyRecord(id = "dev_category_brand", name = "品牌话术", sort = 10),
                OemDeveloperMaterialTaxonomyRecord(id = "dev_category_product", name = "商品卖点", sort = 20),
                OemDeveloperMaterialTaxonomyRecord(id = "dev_category_service", name = "售后答疑", sort = 30),
            ),
            tags = listOf(
                OemDeveloperMaterialTaxonomyRecord(id = "dev_tag_hot", name = "爆款", sort = 10),
                OemDeveloperMaterialTaxonomyRecord(id = "dev_tag_discount", name = "优惠", sort = 20),
                OemDeveloperMaterialTaxonomyRecord(id = "dev_tag_new", name = "上新", sort = 30),
            ),
        )
    }
}
