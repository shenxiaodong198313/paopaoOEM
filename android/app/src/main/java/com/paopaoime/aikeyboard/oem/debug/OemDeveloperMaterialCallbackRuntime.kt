package com.paopaoime.aikeyboard.oem.debug

import android.content.Context
import android.util.Log
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk
import com.paopaoime.aikeyboard.sdk.core.OemMaterialFetchCallback
import com.paopaoime.aikeyboard.sdk.core.OemMaterialFetchRequest
import com.paopaoime.aikeyboard.sdk.core.OemMaterialFetchResult
import com.paopaoime.aikeyboard.sdk.core.OemMaterialHostDelegate
import com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyFetchCallback
import com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyFetchRequest
import com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyFetchResult
import com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyHostDelegate
import com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyItem
import com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyMutationAction
import com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyMutationCallback
import com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyMutationRequest
import com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyMutationResult
import com.paopaoime.aikeyboard.sdk.core.OemMaterialTaxonomyTarget

object OemDeveloperMaterialCallbackRuntime {
    fun ensureInstalled(context: Context) {
        val appContext = context.applicationContext
        val hasDelegates =
            OemImeSdk.isMaterialHostDelegateRegistered() &&
                OemImeSdk.isMaterialTaxonomyHostDelegateRegistered()
        val hasModes =
            OemImeSdk.peekMaterialDispatchModeOverride() != null &&
                OemImeSdk.peekMaterialNavigationModeOverride() != null
        if (hasDelegates && hasModes) return
        if (!OemDeveloperMaterialCallbackStore.hasPersistedSnapshot(appContext)) return
        Log.d(
            "OemDeveloperAddon",
            "ensureInstalled rehydrating developer material callbacks from persisted snapshot",
        )
        install(appContext)
    }

    fun install(context: Context) {
        val appContext = context.applicationContext
        val snapshot = OemDeveloperMaterialCallbackStore.load(appContext)
        Log.d(
            "OemDeveloperAddon",
            "install dispatch=${snapshot.dispatchMode} navigation=${snapshot.navigationMode} categories=${snapshot.categories.size} tags=${snapshot.tags.size}",
        )
        OemImeSdk.setMaterialHostDelegate(StoreBackedMaterialHostDelegate(appContext))
        OemImeSdk.setMaterialTaxonomyHostDelegate(StoreBackedTaxonomyHostDelegate(appContext))
        applySavedModes(appContext)
        Log.d(
            "OemDeveloperAddon",
            "install delegates material=${OemImeSdk.isMaterialHostDelegateRegistered()} taxonomy=${OemImeSdk.isMaterialTaxonomyHostDelegateRegistered()}",
        )
    }

    fun applySavedModes(context: Context) {
        val snapshot = OemDeveloperMaterialCallbackStore.load(context.applicationContext)
        OemImeSdk.setMaterialDispatchMode(snapshot.dispatchMode)
        OemImeSdk.setMaterialNavigationMode(snapshot.navigationMode)
        Log.d(
            "OemDeveloperAddon",
            "applySavedModes dispatch=${snapshot.dispatchMode} navigation=${snapshot.navigationMode}",
        )
    }

    fun notifyMaterialCatalogChanged() {
        OemImeSdk.invalidateRemoteMaterialCatalog()
    }

    private class StoreBackedMaterialHostDelegate(
        private val appContext: Context,
    ) : OemMaterialHostDelegate {
        override fun fetchRemoteMaterials(
            request: OemMaterialFetchRequest,
            callback: OemMaterialFetchCallback,
        ): Boolean {
            val items = OemDeveloperMaterialCallbackStore.buildRemoteMaterials(appContext)
            callback.onResult(
                OemMaterialFetchResult(
                    requestId = request.requestId,
                    success = true,
                    materials = items,
                ),
            )
            return true
        }
    }

    private class StoreBackedTaxonomyHostDelegate(
        private val appContext: Context,
    ) : OemMaterialTaxonomyHostDelegate {
        override fun fetchMaterialTaxonomy(
            request: OemMaterialTaxonomyFetchRequest,
            callback: OemMaterialTaxonomyFetchCallback,
        ): Boolean {
            val (categories, tags) = OemDeveloperMaterialCallbackStore.buildTaxonomyItems(appContext)
            callback.onResult(
                OemMaterialTaxonomyFetchResult(
                    requestId = request.requestId,
                    success = true,
                    categories = categories,
                    tags = tags,
                ),
            )
            return true
        }

        override fun mutateMaterialTaxonomy(
            request: OemMaterialTaxonomyMutationRequest,
            callback: OemMaterialTaxonomyMutationCallback,
        ): Boolean {
            val result = when (request.target) {
                OemMaterialTaxonomyTarget.CATEGORY -> mutateCategory(request)
                OemMaterialTaxonomyTarget.TAG -> mutateTag(request)
            }
            callback.onResult(result)
            OemImeSdk.invalidateRemoteMaterialCatalog()
            return true
        }

        private fun mutateCategory(request: OemMaterialTaxonomyMutationRequest): OemMaterialTaxonomyMutationResult {
            return when (request.action) {
                OemMaterialTaxonomyMutationAction.CREATE -> {
                    val name = request.item?.name?.trim().orEmpty()
                    val snapshot = OemDeveloperMaterialCallbackStore.upsertCategory(appContext, id = null, name = name)
                    val created = snapshot.categories.lastOrNull { it.name == name }
                    OemMaterialTaxonomyMutationResult(
                        requestId = request.requestId,
                        success = created != null,
                        item = created?.toTaxonomyItem(),
                        errorMessage = if (created == null) "分类创建失败" else null,
                    )
                }
                OemMaterialTaxonomyMutationAction.UPDATE -> {
                    val item = request.item
                    val snapshot = if (item == null) {
                        null
                    } else {
                        OemDeveloperMaterialCallbackStore.upsertCategory(appContext, id = item.taxonomyId, name = item.name)
                    }
                    val updated = snapshot?.categories?.firstOrNull { it.id == item?.taxonomyId }
                    OemMaterialTaxonomyMutationResult(
                        requestId = request.requestId,
                        success = updated != null,
                        item = updated?.toTaxonomyItem(),
                        errorMessage = if (updated == null) "分类更新失败" else null,
                    )
                }
                OemMaterialTaxonomyMutationAction.DELETE -> {
                    val id = request.taxonomyId?.trim().orEmpty()
                    OemDeveloperMaterialCallbackStore.deleteCategory(appContext, id)
                    OemMaterialTaxonomyMutationResult(
                        requestId = request.requestId,
                        success = id.isNotBlank(),
                        errorMessage = if (id.isBlank()) "分类删除失败" else null,
                    )
                }
            }
        }

        private fun mutateTag(request: OemMaterialTaxonomyMutationRequest): OemMaterialTaxonomyMutationResult {
            return when (request.action) {
                OemMaterialTaxonomyMutationAction.CREATE -> {
                    val name = request.item?.name?.trim().orEmpty()
                    val snapshot = OemDeveloperMaterialCallbackStore.upsertTag(appContext, id = null, name = name)
                    val created = snapshot.tags.lastOrNull { it.name == name }
                    OemMaterialTaxonomyMutationResult(
                        requestId = request.requestId,
                        success = created != null,
                        item = created?.toTaxonomyItem(),
                        errorMessage = if (created == null) "标签创建失败" else null,
                    )
                }
                OemMaterialTaxonomyMutationAction.UPDATE -> {
                    val item = request.item
                    val snapshot = if (item == null) {
                        null
                    } else {
                        OemDeveloperMaterialCallbackStore.upsertTag(appContext, id = item.taxonomyId, name = item.name)
                    }
                    val updated = snapshot?.tags?.firstOrNull { it.id == item?.taxonomyId }
                    OemMaterialTaxonomyMutationResult(
                        requestId = request.requestId,
                        success = updated != null,
                        item = updated?.toTaxonomyItem(),
                        errorMessage = if (updated == null) "标签更新失败" else null,
                    )
                }
                OemMaterialTaxonomyMutationAction.DELETE -> {
                    val id = request.taxonomyId?.trim().orEmpty()
                    OemDeveloperMaterialCallbackStore.deleteTag(appContext, id)
                    OemMaterialTaxonomyMutationResult(
                        requestId = request.requestId,
                        success = id.isNotBlank(),
                        errorMessage = if (id.isBlank()) "标签删除失败" else null,
                    )
                }
            }
        }
    }

    private fun OemDeveloperMaterialTaxonomyRecord.toTaxonomyItem(): OemMaterialTaxonomyItem {
        return OemMaterialTaxonomyItem(
            taxonomyId = id,
            name = name,
            sort = sort,
            enabled = enabled,
        )
    }
}
