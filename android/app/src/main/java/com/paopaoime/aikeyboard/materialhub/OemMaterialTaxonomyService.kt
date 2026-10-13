package com.paopaoime.aikeyboard.materialhub

import android.content.Context
import android.util.Log
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.oem.OemDeveloperRuntimeBridge
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk
import com.paopaoime.aikeyboard.sdk.core.OemMaterialNavigationMode
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.fcitx.fcitx5.android.input.favorite.FavoriteNavigationItem
import org.fcitx.fcitx5.android.input.favorite.FavoriteNavigationMode
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubNavigationConfig

class OemMaterialTaxonomyService(
    context: Context,
) {
    private val appContext = context.applicationContext

    data class Snapshot(
        val mode: FavoriteNavigationMode,
        val categories: List<OemMaterialTaxonomyItem>,
        val tags: List<OemMaterialTaxonomyItem>,
    ) {
        fun toNavigationConfig(): MaterialHubNavigationConfig {
            return MaterialHubNavigationConfig(
                mode = mode,
                categories = categories.map { item -> FavoriteNavigationItem(item.taxonomyId, item.name) },
            )
        }
    }

    suspend fun loadNavigationConfig(): MaterialHubNavigationConfig = withContext(Dispatchers.IO) {
        val snapshot = loadSnapshot()
        Log.d(
            TAG,
            "loadNavigationConfig success: mode=${snapshot.mode}, categories=${snapshot.categories.size}, tags=${snapshot.tags.size}",
        )
        snapshot.toNavigationConfig()
    }

    suspend fun loadSnapshot(): Snapshot = withContext(Dispatchers.IO) {
        val taxonomy = fetchTaxonomyFromHost()
        Snapshot(
            mode = mapNavigationMode(OemEnterpriseConfig.resolveMaterialNavigationMode(appContext)),
            categories = taxonomy.categories,
            tags = taxonomy.tags,
        )
    }

    suspend fun createCategory(name: String): OemMaterialTaxonomyMutationResult {
        return mutate(
            action = OemMaterialTaxonomyMutationAction.CREATE,
            target = OemMaterialTaxonomyTarget.CATEGORY,
            item = OemMaterialTaxonomyItem(
                taxonomyId = "",
                name = name,
            ),
        )
    }

    suspend fun updateCategory(item: OemMaterialTaxonomyItem): OemMaterialTaxonomyMutationResult {
        return mutate(
            action = OemMaterialTaxonomyMutationAction.UPDATE,
            target = OemMaterialTaxonomyTarget.CATEGORY,
            item = item,
        )
    }

    suspend fun deleteCategory(taxonomyId: String): OemMaterialTaxonomyMutationResult {
        return mutate(
            action = OemMaterialTaxonomyMutationAction.DELETE,
            target = OemMaterialTaxonomyTarget.CATEGORY,
            taxonomyId = taxonomyId,
        )
    }

    suspend fun createTag(name: String): OemMaterialTaxonomyMutationResult {
        return mutate(
            action = OemMaterialTaxonomyMutationAction.CREATE,
            target = OemMaterialTaxonomyTarget.TAG,
            item = OemMaterialTaxonomyItem(
                taxonomyId = "",
                name = name,
            ),
        )
    }

    suspend fun updateTag(item: OemMaterialTaxonomyItem): OemMaterialTaxonomyMutationResult {
        return mutate(
            action = OemMaterialTaxonomyMutationAction.UPDATE,
            target = OemMaterialTaxonomyTarget.TAG,
            item = item,
        )
    }

    suspend fun deleteTag(taxonomyId: String): OemMaterialTaxonomyMutationResult {
        return mutate(
            action = OemMaterialTaxonomyMutationAction.DELETE,
            target = OemMaterialTaxonomyTarget.TAG,
            taxonomyId = taxonomyId,
        )
    }

    private suspend fun fetchTaxonomyFromHost(): OemMaterialTaxonomyFetchResult {
        if (!isDelegateReady()) {
            Log.w(TAG, "fetchTaxonomyFromHost skipped: delegate not ready")
            return OemMaterialTaxonomyFetchResult(
                requestId = "",
                success = false,
                errorMessage = "素材库分类/标签宿主回调未注册",
            )
        }
        val request = OemMaterialTaxonomyFetchRequest(
            requestId = UUID.randomUUID().toString(),
            hostApplicationId = appContext.packageName,
            timestamp = System.currentTimeMillis(),
        )
        val result = withTimeoutOrNull(HOST_FETCH_TIMEOUT_MS) {
            suspendCancellableCoroutine<OemMaterialTaxonomyFetchResult?> { continuation ->
                val delivered = AtomicBoolean(false)
                val callback = OemMaterialTaxonomyFetchCallback { hostResult ->
                    if (delivered.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(hostResult)
                    }
                }
                val accepted = runCatching {
                    OemImeSdk.dispatchMaterialTaxonomyFetch(request, callback)
                }.getOrDefault(false)
                if (!accepted && delivered.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(null)
                }
                continuation.invokeOnCancellation {
                    delivered.set(true)
                }
            }
        }
        if (result == null) {
            Log.w(TAG, "fetchCategoriesFromHost timeout or rejected")
            return OemMaterialTaxonomyFetchResult(
                requestId = request.requestId,
                success = false,
                errorMessage = "素材库分类/标签拉取超时或宿主未响应",
            )
        }
        if (!result.success) {
            Log.w(TAG, "fetchCategoriesFromHost failed: ${result.errorMessage}")
            return result
        }
        Log.d(
            TAG,
            "fetchTaxonomyFromHost success: categories=${result.categories.size}, tags=${result.tags.size}",
        )
        return result.copy(
            categories = normalizeItems(result.categories),
            tags = normalizeItems(result.tags),
        )
    }

    private suspend fun mutate(
        action: OemMaterialTaxonomyMutationAction,
        target: OemMaterialTaxonomyTarget,
        item: OemMaterialTaxonomyItem? = null,
        taxonomyId: String? = null,
    ): OemMaterialTaxonomyMutationResult = withContext(Dispatchers.IO) {
        if (!isDelegateReady()) {
            return@withContext OemMaterialTaxonomyMutationResult(
                requestId = "",
                success = false,
                errorMessage = "素材库分类/标签宿主回调未注册",
            )
        }
        val request = OemMaterialTaxonomyMutationRequest(
            requestId = UUID.randomUUID().toString(),
            action = action,
            target = target,
            hostApplicationId = appContext.packageName,
            timestamp = System.currentTimeMillis(),
            item = item,
            taxonomyId = taxonomyId,
        )
        val result = withTimeoutOrNull(HOST_MUTATION_TIMEOUT_MS) {
            suspendCancellableCoroutine<OemMaterialTaxonomyMutationResult?> { continuation ->
                val delivered = AtomicBoolean(false)
                val callback = OemMaterialTaxonomyMutationCallback { hostResult ->
                    if (delivered.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(hostResult)
                    }
                }
                val accepted = runCatching {
                    OemImeSdk.dispatchMaterialTaxonomyMutation(request, callback)
                }.getOrDefault(false)
                if (!accepted && delivered.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(null)
                }
                continuation.invokeOnCancellation {
                    delivered.set(true)
                }
            }
        }
        when {
            result == null -> {
                OemMaterialTaxonomyMutationResult(
                    requestId = request.requestId,
                    success = false,
                    errorMessage = "素材库分类/标签变更超时或宿主未响应",
                )
            }
            !result.success -> result
            else -> result.copy(item = result.item?.normalize())
        }
    }

    private fun normalizeItems(items: List<OemMaterialTaxonomyItem>): List<OemMaterialTaxonomyItem> {
        return items.asSequence()
            .filter { it.enabled }
            .map { it.normalize() }
            .filter { item -> item.taxonomyId.isNotBlank() && item.name.isNotBlank() }
            .distinctBy { item -> item.taxonomyId }
            .sortedWith(compareBy<OemMaterialTaxonomyItem> { it.sort }.thenBy { it.name })
            .toList()
    }

    private fun mapNavigationMode(mode: OemMaterialNavigationMode): FavoriteNavigationMode {
        return when (mode) {
            OemMaterialNavigationMode.TYPE_ONLY -> FavoriteNavigationMode.TYPE_ONLY
            OemMaterialNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY ->
                FavoriteNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY
            OemMaterialNavigationMode.CATEGORY_PRIMARY_TYPE_SECONDARY ->
                FavoriteNavigationMode.CATEGORY_PRIMARY_TYPE_SECONDARY
            OemMaterialNavigationMode.CATEGORY_ONLY ->
                FavoriteNavigationMode.CATEGORY_ONLY
        }
    }

    private fun OemMaterialTaxonomyItem.normalize(): OemMaterialTaxonomyItem {
        return copy(
            taxonomyId = taxonomyId.trim(),
            name = name.trim(),
        )
    }

    private fun isDelegateReady(): Boolean {
        OemDeveloperRuntimeBridge.ensureMaterialCallbacksInstalled(appContext)
        return OemImeSdk.isMaterialTaxonomyHostDelegateRegistered()
    }

    private companion object {
        private const val TAG = "OemMaterialTaxonomy"
        private const val HOST_FETCH_TIMEOUT_MS = 3_000L
        private const val HOST_MUTATION_TIMEOUT_MS = 5_000L
    }
}
