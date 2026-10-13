package com.paopaoime.aikeyboard.sdk.core.drawer

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View

object OemDrawerKit {
    const val DRAWER_AI_REPLY = "ai_reply"
    const val DRAWER_MATERIAL_HUB = "material_hub"
    const val MAX_CUSTOM_DRAWERS = 4
}

enum class OemDrawerState {
    OPENED,
    CLOSED,
    BACK,
}

enum class OemDrawerTheme {
    LIGHT,
    DARK,
}

interface OemDrawerController {
    fun closeDrawer()
    fun openDrawer(drawerId: String)
    fun setTitle(title: CharSequence?)
    fun setBackVisible(visible: Boolean)
    fun currentTheme(): OemDrawerTheme
    /** Non-null only for this drawer's one-shot clipboard shortcut. */
    fun clipboardReplyState(): OemClipboardReplyState? = null
    /** Regenerate from the captured text using the existing customer AI reply configuration. */
    fun regenerateClipboardReply(): Boolean = false
    /** Close the drawer and insert a draft into the original host; never send it. */
    fun insertClipboardReply(text: String): Boolean = false
}

data class OemClipboardReplyState(
    val sourceText: String,
    val loading: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val modelName: String = "",
    val errorCode: String? = null,
    val errorTitle: String? = null,
    val errorDescription: String? = null,
)

interface OemDrawerContentFactory {
    fun onCreateContentView(context: Context, controller: OemDrawerController): View
    fun onCreateFullDrawerView(context: Context, controller: OemDrawerController): View? = null
    fun onDrawerStateChanged(state: OemDrawerState) {}
    fun onBackPressed(): Boolean = false
    fun onHostContentError(error: Throwable) {}
    /** Called on the main thread after view creation and whenever shortcut state changes. */
    fun onClipboardReplyStateChanged(state: OemClipboardReplyState) {}
}

fun interface OemCustomDrawerEntryHandler {
    /**
     * Called on the main thread with the IME service context. The host owns the entry;
     * the return value is reserved and ignored. Exceptions are silently contained.
     */
    fun onEntryClick(context: Context, drawerId: String): Boolean
}

data class OemCustomDrawerSpec @JvmOverloads constructor(
    val id: String,
    val title: String,
    val iconRes: Int = 0,
    val icon: Drawable? = null,
    val contentFactory: OemDrawerContentFactory,
    /** Null preserves the SDK drawer flow; otherwise only the host callback runs. */
    val entryHandler: OemCustomDrawerEntryHandler? = null,
)

enum class OemRegisterResult {
    OK,
    DUPLICATE_ID,
    QUOTA_EXCEEDED,
    INVALID_ID,
}

object OemCustomDrawerRegistry {
    private val drawers = LinkedHashMap<String, OemCustomDrawerSpec>()
    @Volatile
    var onRegistryChanged: (() -> Unit)? = null

    @Synchronized
    fun register(spec: OemCustomDrawerSpec): OemRegisterResult {
        if (spec.id.isBlank() ||
            spec.id == OemDrawerKit.DRAWER_AI_REPLY ||
            spec.id == OemDrawerKit.DRAWER_MATERIAL_HUB
        ) {
            return OemRegisterResult.INVALID_ID
        }
        if (drawers.containsKey(spec.id)) return OemRegisterResult.DUPLICATE_ID
        if (drawers.size >= OemDrawerKit.MAX_CUSTOM_DRAWERS) {
            return OemRegisterResult.QUOTA_EXCEEDED
        }
        drawers[spec.id] = spec
        notifyChanged()
        return OemRegisterResult.OK
    }

    @Synchronized
    fun update(spec: OemCustomDrawerSpec): OemRegisterResult {
        if (spec.id.isBlank() ||
            spec.id == OemDrawerKit.DRAWER_AI_REPLY ||
            spec.id == OemDrawerKit.DRAWER_MATERIAL_HUB
        ) {
            return OemRegisterResult.INVALID_ID
        }
        if (!drawers.containsKey(spec.id)) return OemRegisterResult.INVALID_ID
        drawers[spec.id] = spec
        notifyChanged()
        return OemRegisterResult.OK
    }

    @Synchronized
    fun unregister(id: String): Boolean {
        val removed = drawers.remove(id) != null
        if (removed) notifyChanged()
        return removed
    }

    @Synchronized
    fun list(): List<OemCustomDrawerSpec> = drawers.values.toList()

    @Synchronized
    fun find(id: String): OemCustomDrawerSpec? = drawers[id]

    private fun notifyChanged() {
        onRegistryChanged?.invoke()
    }
}

interface OemDrawerHostActions {
    fun openDrawer(drawerId: String)
    fun closeDrawer(drawerId: String)
}

data class OemTonePreset(
    val id: String,
    val label: String,
)

enum class OemAiReplyStepStatus {
    TODO,
    ACTIVE,
    DONE,
}

data class OemAiReplyProgress(
    val stepIndex: Int,
    val stepLabel: String,
    val status: OemAiReplyStepStatus,
)

data class OemAiReplySuggestions(
    val suggestions: List<String>,
    val mode: String,
    val modelName: String = "",
)

interface OemAiReplyListener {
    fun onProgress(progress: OemAiReplyProgress) {}
    fun onSuccess(result: OemAiReplySuggestions)
    fun onError(message: String)
}

interface OemPermissionListener {
    fun onResult(granted: Boolean)
}

interface OemAlbumPickerListener {
    fun onImagesSelected(uris: List<Uri>)
    fun onCancelled() {}
}

interface OemAiReplyCapabilities {
    fun startPasteSession(listener: OemAiReplyListener)
    fun startFastGenerate(listener: OemAiReplyListener)
    fun startScreenReply(listener: OemAiReplyListener)
    fun requestScreenPermission(listener: OemPermissionListener)
    fun openAlbumPicker(listener: OemAlbumPickerListener)
    fun startImageReply(uris: List<Uri>, listener: OemAiReplyListener)
    fun setTone(toneId: String)
    fun tonePresets(): List<OemTonePreset>
    fun regenerate(listener: OemAiReplyListener)
    fun footerText(): String? = null
    fun onFooterClick(): Boolean = false
    fun isRestricted(): Boolean

    /**
     * 将一条 AI 回复文本直接发送到当前聊天输入框（关闭抽屉并走素材发送通道）。
     * 供自定义 UI / 自定义抽屉在拿到 [OemAiReplySuggestions] 后直接发送。
     *
     * @return true 表示已进入发送流程；false 表示当前无可用发送通道。
     */
    fun sendReplyToInput(text: String): Boolean = false
}

enum class OemMaterialCategory(val wireValue: String) {
    RECENT("recent"),
    FILES("files"),
    IMAGES("images"),
    VIDEOS("videos"),
}

enum class OemMaterialFileSub(val wireValue: String) {
    ALL("all"),
    WORD("word"),
    PRESENTATION("presentation"),
    SHEET("sheet"),
    PDF("pdf"),
}

enum class OemPickerType(val wireValue: String) {
    TEXT("text"),
    IMAGE("image"),
    FILE("file"),
    VIDEO("video"),
}

data class OemMaterialQuery(
    val category: OemMaterialCategory = OemMaterialCategory.RECENT,
    val fileSub: OemMaterialFileSub? = null,
    val keyword: String? = null,
    val folderId: String? = null,
    val page: Int = 0,
)

data class OemMaterialItem(
    val id: String,
    val title: String,
    val kind: String,
    val uri: Uri? = null,
    val filePath: String? = null,
    val sizeText: String? = null,
    val timeText: String? = null,
    val source: String = "local",
)

data class OemMaterialPage(
    val items: List<OemMaterialItem>,
    val hasMore: Boolean,
)

data class OemMaterialFolder(
    val id: String,
    val title: String,
    val count: Int,
)

data class OemMaterialStateFlags(
    val empty: Boolean,
    val loginRequired: Boolean,
    val restricted: Boolean,
)

enum class OemMaterialPartType {
    TEXT,
    IMAGE,
    FILE,
    VIDEO,
}

data class OemMaterialPart(
    val id: String,
    val type: OemMaterialPartType,
    val text: String? = null,
    val title: String? = null,
    val filePath: String? = null,
    val mimeType: String? = null,
    val sizeText: String? = null,
)

data class OemMaterialDetail(
    val id: String,
    val title: String,
    val parts: List<OemMaterialPart>,
    val source: String = "local",
    val updatedAtText: String? = null,
)

interface OemMaterialListener {
    fun onPage(page: OemMaterialPage)
    fun onError(message: String) {}
}

interface OemFolderListener {
    fun onFolders(folders: List<OemMaterialFolder>)
    fun onError(message: String) {}
}

interface OemMaterialDetailListener {
    fun onDetail(detail: OemMaterialDetail)
    fun onError(message: String) {}
}

interface OemMaterialMutationListener {
    fun onSuccess(item: OemMaterialItem)
    fun onError(message: String) {}
}

interface OemMaterialDeleteListener {
    fun onSuccess()
    fun onError(message: String) {}
}

interface OemMaterialRefreshListener {
    fun onSuccess(count: Int)
    fun onError(message: String) {}
}

interface OemMaterialCapabilities {
    fun query(query: OemMaterialQuery, listener: OemMaterialListener)
    fun queryMaterials(query: OemMaterialQuery, listener: OemMaterialListener) = query(query, listener)
    fun observeFolders(listener: OemFolderListener) {}
    fun loadDetail(itemId: String, listener: OemMaterialDetailListener) {}
    fun saveText(text: String, listener: OemMaterialMutationListener) {}
    fun pickAndSave(type: OemPickerType, listener: OemMaterialMutationListener) {}
    fun updateDetail(itemId: String, parts: List<OemMaterialPart>, listener: OemMaterialMutationListener) {}
    fun delete(itemId: String, listener: OemMaterialDeleteListener) {}
    fun sendToInput(item: OemMaterialItem): Boolean
    fun previewView(item: OemMaterialItem): View? = null
    fun openComposer(type: OemPickerType)
    fun stateFlags(): OemMaterialStateFlags

    /**
     * 查询企业远程素材目录（宿主下发 / 企业域名通道的本地缓存），
     * 与 [query] 的本地收藏相互独立；自定义抽屉可借此实现企业素材流。
     */
    fun queryRemoteMaterials(query: OemMaterialQuery, listener: OemMaterialListener) {}

    /**
     * 主动刷新企业远程素材目录（触发宿主回调 / 域名通道同步），完成后回调目录条数。
     */
    fun refreshRemoteMaterials(listener: OemMaterialRefreshListener) {}

    /**
     * 将一段文本直接发送到当前聊天输入框（关闭抽屉并走素材发送通道）。
     * 自定义抽屉 / 自定义 UI 可借此实现「自定义功能 → 直接发送」闭环。
     *
     * @return true 表示已进入发送流程；false 表示当前无可用发送通道。
     */
    fun sendTextToInput(text: String): Boolean = false
}
