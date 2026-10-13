package com.paopaoime.aikeyboard.materialhub

import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.sdk.core.OemLog
import com.paopaoime.aikeyboard.sdk.core.drawer.OemAiReplyCapabilities
import com.paopaoime.aikeyboard.sdk.core.drawer.OemAiReplyListener
import com.paopaoime.aikeyboard.sdk.core.drawer.OemAiReplyProgress
import com.paopaoime.aikeyboard.sdk.core.drawer.OemAiReplySuggestions
import com.paopaoime.aikeyboard.sdk.core.drawer.OemAiReplyStepStatus
import com.paopaoime.aikeyboard.sdk.core.drawer.OemAlbumPickerListener
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialCapabilities
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialCategory
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialDeleteListener
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialDetail
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialDetailListener
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialFolder
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialItem
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialListener
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialMutationListener
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialPage
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialPart
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialPartType
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialQuery
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialRefreshListener
import com.paopaoime.aikeyboard.sdk.core.drawer.OemMaterialStateFlags
import com.paopaoime.aikeyboard.sdk.core.drawer.OemFolderListener
import com.paopaoime.aikeyboard.sdk.core.drawer.OemPermissionListener
import com.paopaoime.aikeyboard.sdk.core.drawer.OemPickerType
import com.paopaoime.aikeyboard.sdk.core.drawer.OemTonePreset
import org.fcitx.fcitx5.android.input.favorite.FavoriteAttachmentType
import org.fcitx.fcitx5.android.input.favorite.FavoriteBridge
import org.fcitx.fcitx5.android.input.favorite.FavoriteCategory
import org.fcitx.fcitx5.android.input.favorite.FavoritePart
import org.fcitx.fcitx5.android.input.favorite.FavoritePickerType
import org.fcitx.fcitx5.android.input.favorite.FavoriteRecord
import org.fcitx.fcitx5.android.input.favorite.FavoriteStorage
import org.fcitx.fcitx5.android.input.materialhub.AiCommunicatePanelController
import java.io.File
import java.util.UUID

object OemDrawerCapabilities {

    fun aiReply(context: Context): OemAiReplyCapabilities =
        AiReplyCapabilitiesImpl(context.applicationContext)

    fun material(context: Context): OemMaterialCapabilities =
        MaterialCapabilitiesImpl(context.applicationContext)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}

private const val TAG = "OemDrawerCapabilities"

private fun normalizeTextForSendLimit(context: Context, text: String): String? {
    val normalized = text.trim()
    if (normalized.isBlank()) return ""
    val maxChars = OemEnterpriseConfig.resolveSendTextMaxChars(context)
    val codePointCount = normalized.codePointCount(0, normalized.length)
    if (codePointCount <= maxChars) return normalized
    OemLog.w(TAG, "send text exceeds maxChars=$maxChars actualChars=$codePointCount")
    Toast.makeText(context, "超出可发送字数上限（${maxChars}字）", Toast.LENGTH_SHORT).show()
    return null
}

private fun sendTextViaFacade(context: Context, text: String): Boolean {
    val normalized = normalizeTextForSendLimit(context, text) ?: return false
    if (normalized.isBlank()) return false
    val now = System.currentTimeMillis()
    val record = FavoriteRecord(
        id = UUID.randomUUID().toString(),
        createdAt = now,
        updatedAt = now,
        parts = listOf(FavoritePart.Text(text = normalized)),
    )
    MaterialHubActivity.currentInstance?.let { activity ->
        return activity.sendFavoriteRecordForFacade(record)
    }
    val committed = FavoriteBridge.commitText(normalized)
    if (!committed) return false
    return FavoriteBridge.performTextSend(normalized)
}

private class AiReplyCapabilitiesImpl(
    private val context: Context,
) : OemAiReplyCapabilities {

    private var currentToneIndex = 0
    private var lastMode: AiCommunicatePanelController.GenerateMode =
        AiCommunicatePanelController.GenerateMode.FAST_LLM
    private var lastImagePaths: List<String> = emptyList()

    override fun startPasteSession(listener: OemAiReplyListener) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val copied = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.text?.toString().orEmpty()
        if (copied.isBlank()) {
            listener.onError("剪贴板暂无可理解的会话内容，请先复制聊天文本")
            return
        }
        runGenerate(
            listener = listener,
            mode = AiCommunicatePanelController.GenerateMode.PASTE,
            existing = AiCommunicateReplyService.GenerationContext(
                screenshotPreviewPath = null,
                ocrSourceText = copied,
            ),
            allowCapture = false,
        )
    }

    override fun startFastGenerate(listener: OemAiReplyListener) {
        runGenerate(
            listener = listener,
            mode = AiCommunicatePanelController.GenerateMode.FAST_LLM,
            existing = null,
            allowCapture = true,
        )
    }

    override fun startScreenReply(listener: OemAiReplyListener) {
        runGenerate(
            listener = listener,
            mode = AiCommunicatePanelController.GenerateMode.OCR,
            existing = null,
            allowCapture = true,
        )
    }

    override fun requestScreenPermission(listener: OemPermissionListener) {
        val activity = MaterialHubActivity.currentInstance
        if (activity == null) {
            listener.onResult(false)
            return
        }
        activity.requestScreenPermissionForFacade(listener)
    }

    override fun openAlbumPicker(listener: OemAlbumPickerListener) {
        val activity = MaterialHubActivity.currentInstance
        if (activity == null) {
            listener.onCancelled()
            return
        }
        activity.launchFacadeAlbumPicker(listener)
    }

    override fun startImageReply(uris: List<Uri>, listener: OemAiReplyListener) {
        val paths = uris.mapNotNull { copyUriToCache(it) }
        if (paths.isEmpty()) {
            listener.onError("未能读取所选图片，请重试")
            return
        }
        lastImagePaths = paths
        runGenerate(
            listener = listener,
            mode = AiCommunicatePanelController.GenerateMode.IMAGE_REPLY,
            existing = AiCommunicateReplyService.GenerationContext(
                screenshotPreviewPath = paths.firstOrNull(),
                screenshotPreviewPaths = paths,
                actualCaptureCount = paths.size,
            ),
            allowCapture = false,
        )
    }

    override fun setTone(toneId: String) {
        val presets = OemEnterpriseConfig.resolveAiReplyTonePresets(context)
        val index = presets.indexOfFirst { it.label == toneId || it.promptHint == toneId }
        if (index >= 0) currentToneIndex = index
    }

    override fun tonePresets(): List<OemTonePreset> =
        OemEnterpriseConfig.resolveAiReplyTonePresets(context).map { OemTonePreset(it.label, it.label) }

    override fun regenerate(listener: OemAiReplyListener) {
        if (lastMode == AiCommunicatePanelController.GenerateMode.IMAGE_REPLY && lastImagePaths.isNotEmpty()) {
            runGenerate(
                listener = listener,
                mode = lastMode,
                existing = AiCommunicateReplyService.GenerationContext(
                    screenshotPreviewPath = lastImagePaths.firstOrNull(),
                    screenshotPreviewPaths = lastImagePaths,
                    actualCaptureCount = lastImagePaths.size,
                ),
                allowCapture = false,
            )
        } else {
            runGenerate(listener = listener, mode = lastMode, existing = null, allowCapture = true)
        }
    }

    override fun footerText(): String = "回复内容由当前宿主配置的 AI 模型生成"

    override fun isRestricted(): Boolean =
        OemEnterpriseConfig.resolveAiReplyRestriction(context) != null ||
            !OemEnterpriseConfig.isAiReplyEntryEnabled(context)

    override fun sendReplyToInput(text: String): Boolean {
        MaterialHubActivity.currentInstance?.takeIf { it.clipboardReplyState() != null }?.let {
            return it.insertClipboardReply(text)
        }
        val sent = sendTextViaFacade(context, text)
        OemLog.d(TAG, "sendReplyToInput accepted=$sent length=${text.length}")
        return sent
    }

    private fun resolveTone(): AiCommunicateReplyService.Tone {
        val presets = OemEnterpriseConfig.resolveAiReplyTonePresets(context)
        val preset = presets.getOrNull(currentToneIndex) ?: presets.firstOrNull()
        return AiCommunicateReplyService.Tone(
            key = preset?.label ?: "natural",
            label = preset?.label ?: "自然沟通",
            promptHint = preset?.promptHint ?: "",
        )
    }

    private fun runGenerate(
        listener: OemAiReplyListener,
        mode: AiCommunicatePanelController.GenerateMode,
        existing: AiCommunicateReplyService.GenerationContext?,
        allowCapture: Boolean,
    ) {
        lastMode = mode
        listener.onProgress(OemAiReplyProgress(0, "检查智能体运行环境", OemAiReplyStepStatus.ACTIVE))
        OemDrawerCapabilities.scope.launch {
            val result = runCatching {
                AiCommunicateReplyService.generateReplies(
                    context = context,
                    tone = resolveTone(),
                    mode = mode,
                    sessionId = UUID.randomUUID().toString(),
                    allowSnapshotCapture = allowCapture,
                    existingContext = existing,
                )
            }
            result.fold(
                onSuccess = { generation ->
                    listener.onProgress(OemAiReplyProgress(2, "生成回复建议", OemAiReplyStepStatus.DONE))
                    listener.onSuccess(
                        OemAiReplySuggestions(
                            suggestions = generation.replies,
                            mode = mode.name,
                            modelName = generation.modelName,
                        )
                    )
                },
                onFailure = { error ->
                    listener.onError(error.message ?: "生成失败，请稍后重试")
                },
            )
        }
    }

    private fun copyUriToCache(uri: Uri): String? = runCatching {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val target = File(context.cacheDir, "drawer_album_${UUID.randomUUID()}.png")
        inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.absolutePath
    }.getOrNull()
}

private class MaterialCapabilitiesImpl(
    private val context: Context,
) : OemMaterialCapabilities {

    private val pageSize = 20
    private val storage by lazy { FavoriteStorage(context) }
    private val remoteService by lazy { OemRemoteMaterialService(context) }

    override fun query(query: OemMaterialQuery, listener: OemMaterialListener) {
        OemDrawerCapabilities.scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val records = storage.listFavorites()
                val category = when (query.category) {
                    OemMaterialCategory.RECENT -> FavoriteCategory.RECENT
                    OemMaterialCategory.FILES -> FavoriteCategory.FILES
                    OemMaterialCategory.IMAGES -> FavoriteCategory.IMAGES
                    OemMaterialCategory.VIDEOS -> FavoriteCategory.VIDEOS
                }
                val keyword = query.keyword
                val folderId = query.folderId
                val filtered = records.filter { record ->
                    record.matchesCategory(category) &&
                        (keyword.isNullOrBlank() || recordTitle(record).contains(keyword, true)) &&
                        (folderId.isNullOrBlank() || record.directoryName == folderId)
                }
                val pageItems = filtered.drop(query.page * pageSize).take(pageSize)
                OemMaterialPage(
                    items = pageItems.map { record ->
                        toMaterialItem(record)
                    },
                    hasMore = filtered.size > (query.page + 1) * pageSize,
                )
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { listener.onPage(it) },
                    onFailure = { listener.onError(it.message ?: "素材加载失败") },
                )
            }
        }
    }

    override fun observeFolders(listener: OemFolderListener) {
        OemDrawerCapabilities.scope.launch(Dispatchers.IO) {
            val result = runCatching {
                storage.listFavorites()
                    .groupBy { it.directoryName.takeIf(String::isNotBlank) ?: "default" }
                    .map { (folderId, items) ->
                        OemMaterialFolder(
                            id = folderId,
                            title = if (folderId == "default") "默认分组" else folderId,
                            count = items.size,
                        )
                    }
                    .sortedByDescending { it.count }
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { listener.onFolders(it) },
                    onFailure = { listener.onError(it.message ?: "素材分组加载失败") },
                )
            }
        }
    }

    override fun loadDetail(itemId: String, listener: OemMaterialDetailListener) {
        OemDrawerCapabilities.scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val record = requireRecord(itemId)
                toMaterialDetail(record)
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { listener.onDetail(it) },
                    onFailure = { listener.onError(it.message ?: "素材详情加载失败") },
                )
            }
        }
    }

    override fun saveText(text: String, listener: OemMaterialMutationListener) {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            listener.onError("文本内容不能为空")
            return
        }
        OemDrawerCapabilities.scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val now = System.currentTimeMillis()
                val saved = storage.saveFavorite(
                    FavoriteRecord(
                        id = UUID.randomUUID().toString(),
                        createdAt = now,
                        updatedAt = now,
                        parts = listOf(FavoritePart.Text(text = normalized)),
                    )
                )
                toMaterialItem(saved)
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { listener.onSuccess(it) },
                    onFailure = { listener.onError(it.message ?: "文本素材保存失败") },
                )
            }
        }
    }

    override fun pickAndSave(type: OemPickerType, listener: OemMaterialMutationListener) {
        if (type == OemPickerType.TEXT) {
            listener.onError("文本素材请通过 saveText 保存")
            return
        }
        val activity = MaterialHubActivity.currentInstance
        if (activity == null) {
            listener.onError("当前素材抽屉未处于激活状态")
            return
        }
        activity.launchFacadePicker(type) { uri ->
            if (uri == null) {
                listener.onError("已取消选择素材")
                return@launchFacadePicker
            }
            OemDrawerCapabilities.scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val recordId = UUID.randomUUID().toString()
                    val attachment = storage.createAttachmentFromUri(recordId, uri, type.toFavoritePickerType())
                        ?: error("无法读取所选素材")
                    val now = System.currentTimeMillis()
                    val saved = storage.saveFavorite(
                        FavoriteRecord(
                            id = recordId,
                            createdAt = now,
                            updatedAt = now,
                            parts = listOf(attachment),
                        )
                    )
                    toMaterialItem(saved)
                }
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = { listener.onSuccess(it) },
                        onFailure = { listener.onError(it.message ?: "素材保存失败") },
                    )
                }
            }
        }
    }

    override fun updateDetail(itemId: String, parts: List<OemMaterialPart>, listener: OemMaterialMutationListener) {
        if (parts.isEmpty()) {
            listener.onError("素材内容不能为空")
            return
        }
        OemDrawerCapabilities.scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val existing = requireRecord(itemId)
                val materializedParts = parts.map { it.toFavoritePart(existing.id) }
                val updated = storage.saveFavorite(
                    existing.copy(
                        parts = materializedParts,
                    )
                )
                storage.cleanupUnusedAssets(
                    updated.id,
                    updated.parts.filterIsInstance<FavoritePart.Attachment>().map { it.storedPath }.toSet()
                )
                toMaterialItem(updated)
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { listener.onSuccess(it) },
                    onFailure = { listener.onError(it.message ?: "素材更新失败") },
                )
            }
        }
    }

    override fun delete(itemId: String, listener: OemMaterialDeleteListener) {
        OemDrawerCapabilities.scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val deleted = storage.deleteFavorite(itemId)
                check(deleted) { "素材删除失败" }
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { listener.onSuccess() },
                    onFailure = { listener.onError(it.message ?: "素材删除失败") },
                )
            }
        }
    }

    override fun sendToInput(item: OemMaterialItem): Boolean {
        val record = runCatching { storage.listFavorites() }
            .getOrDefault(emptyList())
            .firstOrNull { it.id == item.id }
            ?: runCatching { remoteService.readCachedRemoteFavorites(limit = 20) }
                .getOrDefault(emptyList())
                .firstOrNull { it.id == item.id }
            ?: return false
        MaterialHubActivity.currentInstance?.let { activity ->
            return activity.sendFavoriteRecordForFacade(record)
        }
        val text = normalizeTextForSendLimit(
            context,
            record.parts.filterIsInstance<FavoritePart.Text>().joinToString("\n") { it.text.trim() },
        ) ?: return false
        if (text.isBlank()) return false
        val committed = FavoriteBridge.commitText(text)
        if (!committed) return false
        return FavoriteBridge.performTextSend(text)
    }

    override fun queryRemoteMaterials(query: OemMaterialQuery, listener: OemMaterialListener) {
        OemDrawerCapabilities.scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val records = remoteService.readCachedRemoteFavorites(limit = 20)
                val category = when (query.category) {
                    OemMaterialCategory.RECENT -> FavoriteCategory.RECENT
                    OemMaterialCategory.FILES -> FavoriteCategory.FILES
                    OemMaterialCategory.IMAGES -> FavoriteCategory.IMAGES
                    OemMaterialCategory.VIDEOS -> FavoriteCategory.VIDEOS
                }
                val keyword = query.keyword
                val filtered = records.filter { record ->
                    record.matchesCategory(category) &&
                        (keyword.isNullOrBlank() || recordTitle(record).contains(keyword, true))
                }
                val pageItems = filtered.drop(query.page * pageSize).take(pageSize)
                OemMaterialPage(
                    items = pageItems.map { record -> toMaterialItem(record) },
                    hasMore = filtered.size > (query.page + 1) * pageSize,
                )
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { page ->
                        OemLog.d(TAG, "queryRemoteMaterials page=${query.page} items=${page.items.size}")
                        listener.onPage(page)
                    },
                    onFailure = { error ->
                        OemLog.w(TAG, "queryRemoteMaterials failed: ${error.message}")
                        listener.onError(error.message ?: "企业素材加载失败")
                    },
                )
            }
        }
    }

    override fun refreshRemoteMaterials(listener: OemMaterialRefreshListener) {
        OemDrawerCapabilities.scope.launch(Dispatchers.IO) {
            val result = runCatching {
                remoteService.refreshRemoteCatalog()
                remoteService.readCachedRemoteFavorites(limit = 20).size
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { count ->
                        OemLog.d(TAG, "refreshRemoteMaterials success count=$count")
                        listener.onSuccess(count)
                    },
                    onFailure = { error ->
                        OemLog.w(TAG, "refreshRemoteMaterials failed: ${error.message}")
                        listener.onError(error.message ?: "企业素材刷新失败")
                    },
                )
            }
        }
    }

    override fun sendTextToInput(text: String): Boolean {
        val sent = sendTextViaFacade(context, text)
        OemLog.d(TAG, "sendTextToInput accepted=$sent length=${text.length}")
        return sent
    }

    override fun previewView(item: OemMaterialItem): View? {
        val record = runCatching { requireRecord(item.id) }.getOrNull() ?: return buildPreviewCard(item)
        return buildPreviewCard(record)
    }

    override fun openComposer(type: OemPickerType) {
        MaterialHubActivity.currentInstance?.openComposerForFacade(type)
    }

    override fun stateFlags(): OemMaterialStateFlags {
        val records = runCatching { storage.listFavorites() }.getOrDefault(emptyList())
        return OemMaterialStateFlags(
            empty = records.isEmpty(),
            loginRequired = false,
            restricted = !OemEnterpriseConfig.isMaterialLibraryEntryEnabled(context),
        )
    }

    private fun recordTitle(record: org.fcitx.fcitx5.android.input.favorite.FavoriteRecord): String {
        val text = record.parts.filterIsInstance<FavoritePart.Text>().firstOrNull()?.text
        if (!text.isNullOrBlank()) return text.take(40)
        val attachment = record.parts.filterIsInstance<FavoritePart.Attachment>().firstOrNull()
        return attachment?.displayName ?: "素材"
    }

    private fun recordFilePath(record: org.fcitx.fcitx5.android.input.favorite.FavoriteRecord): String? =
        record.parts.filterIsInstance<FavoritePart.Attachment>().firstOrNull()?.storedPath

    private fun requireRecord(itemId: String): FavoriteRecord {
        return storage.listFavorites().firstOrNull { it.id == itemId }
            ?: error("未找到对应素材")
    }

    private fun toMaterialItem(record: FavoriteRecord): OemMaterialItem {
        val attachment = record.primaryAttachmentPart()
        return OemMaterialItem(
            id = record.id,
            title = recordTitle(record),
            kind = recordKind(record),
            filePath = attachment?.storedPath,
            sizeText = attachment?.fileSizeBytes?.takeIf { it > 0 }?.let(::formatFileSize),
            timeText = record.updatedAt.toString(),
            source = record.source.name.lowercase(),
        )
    }

    private fun toMaterialDetail(record: FavoriteRecord): OemMaterialDetail {
        return OemMaterialDetail(
            id = record.id,
            title = recordTitle(record),
            parts = record.parts.map { part ->
                when (part) {
                    is FavoritePart.Text -> OemMaterialPart(
                        id = part.id,
                        type = OemMaterialPartType.TEXT,
                        text = part.text,
                        title = part.text.take(40),
                    )
                    is FavoritePart.Attachment -> OemMaterialPart(
                        id = part.id,
                        type = when (part.attachmentType) {
                            FavoriteAttachmentType.IMAGE -> OemMaterialPartType.IMAGE
                            FavoriteAttachmentType.VIDEO -> OemMaterialPartType.VIDEO
                            FavoriteAttachmentType.DOCUMENT,
                            FavoriteAttachmentType.NONE -> OemMaterialPartType.FILE
                        },
                        title = part.displayName,
                        filePath = part.storedPath,
                        mimeType = part.mimeType,
                        sizeText = part.fileSizeBytes.takeIf { it > 0 }?.let(::formatFileSize),
                    )
                }
            },
            source = record.source.name.lowercase(),
            updatedAtText = record.updatedAt.toString(),
        )
    }

    private fun OemMaterialPart.toFavoritePart(recordId: String): FavoritePart {
        return when (type) {
            OemMaterialPartType.TEXT -> FavoritePart.Text(
                id = id,
                text = text?.trim().orEmpty(),
            )
            OemMaterialPartType.IMAGE,
            OemMaterialPartType.FILE,
            OemMaterialPartType.VIDEO -> materializeAttachmentForRecord(
                recordId = recordId,
                attachment = FavoritePart.Attachment(
                    id = id,
                    displayName = title?.ifBlank { "素材" } ?: "素材",
                    storedPath = filePath?.ifBlank { error("附件路径不能为空") } ?: error("附件路径不能为空"),
                    mimeType = mimeType?.ifBlank { "application/octet-stream" } ?: "application/octet-stream",
                    attachmentType = when (type) {
                        OemMaterialPartType.IMAGE -> FavoriteAttachmentType.IMAGE
                        OemMaterialPartType.VIDEO -> FavoriteAttachmentType.VIDEO
                        OemMaterialPartType.FILE -> FavoriteAttachmentType.DOCUMENT
                        OemMaterialPartType.TEXT -> FavoriteAttachmentType.NONE
                    },
                    pickerType = when (type) {
                        OemMaterialPartType.IMAGE -> FavoritePickerType.IMAGE
                        OemMaterialPartType.VIDEO -> FavoritePickerType.VIDEO
                        OemMaterialPartType.FILE -> FavoritePickerType.FILE
                        OemMaterialPartType.TEXT -> FavoritePickerType.TEXT
                    },
                    fileSizeBytes = parseSizeText(sizeText),
                )
            )
        }
    }

    private fun materializeAttachmentForRecord(
        recordId: String,
        attachment: FavoritePart.Attachment,
    ): FavoritePart.Attachment {
        val sourceFile = attachment.file()
        require(sourceFile.exists()) { "附件文件不存在：${attachment.displayName}" }
        val itemDir = File(context.filesDir, "favorite_library/assets/$recordId").apply { mkdirs() }
        val existingParent = sourceFile.parentFile?.canonicalFile
        val targetParent = itemDir.canonicalFile
        if (existingParent == targetParent) {
            return attachment.copy(fileSizeBytes = sourceFile.length())
        }
        val extension = attachment.displayName.substringAfterLast('.', "")
            .ifBlank { sourceFile.extension }
        val targetName = buildString {
            append(UUID.randomUUID().toString())
            if (extension.isNotBlank()) {
                append('.')
                append(extension.lowercase())
            }
        }
        val targetFile = File(itemDir, targetName)
        sourceFile.inputStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return attachment.copy(
            storedPath = targetFile.absolutePath,
            fileSizeBytes = targetFile.length(),
        )
    }

    private fun recordKind(record: FavoriteRecord): String {
        return when (record.primaryAttachmentType()) {
            FavoriteAttachmentType.IMAGE -> OemMaterialCategory.IMAGES.wireValue
            FavoriteAttachmentType.DOCUMENT -> OemMaterialCategory.FILES.wireValue
            FavoriteAttachmentType.VIDEO -> OemMaterialCategory.VIDEOS.wireValue
            FavoriteAttachmentType.NONE -> OemPickerType.TEXT.wireValue
        }
    }

    private fun buildPreviewCard(item: OemMaterialItem): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#FFFFFF"))
            }
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(
                TextView(context).apply {
                    text = item.title
                    setTextColor(Color.parseColor("#2A2251"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setLineSpacing(0f, 1.15f)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
            )
            addView(
                TextView(context).apply {
                    text = buildString {
                        append("类型：")
                        append(item.kind)
                        item.source.takeIf { it.isNotBlank() }?.let {
                            append("  来源：")
                            append(it)
                        }
                        item.timeText?.takeIf { it.isNotBlank() }?.let {
                            append("\n更新时间：")
                            append(it)
                        }
                        item.sizeText?.takeIf { it.isNotBlank() }?.let {
                            append("\n大小：")
                            append(it)
                        }
                    }
                    setTextColor(Color.parseColor("#6B6580"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    val params = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    params.topMargin = dp(10)
                    layoutParams = params
                }
            )
            buildFilePreview(item.filePath, item.kind)?.let { preview ->
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(180)
                )
                params.topMargin = dp(12)
                preview.layoutParams = params
                addView(preview)
            }
        }
    }

    private fun buildPreviewCard(record: FavoriteRecord): View {
        val detail = toMaterialDetail(record)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#FFFFFF"))
            }
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(
                TextView(context).apply {
                    text = detail.title
                    setTextColor(Color.parseColor("#2A2251"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setLineSpacing(0f, 1.15f)
                }
            )
            detail.parts.forEachIndexed { index, part ->
                addView(buildPartPreviewView(part).apply {
                    val params = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    params.topMargin = if (index == 0) dp(12) else dp(10)
                    layoutParams = params
                })
            }
        }
    }

    private fun buildPartPreviewView(part: OemMaterialPart): View {
        return when (part.type) {
            OemMaterialPartType.TEXT -> TextView(context).apply {
                text = part.text
                setTextColor(Color.parseColor("#2A2251"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#F7F8FF"))
                }
            }
            OemMaterialPartType.IMAGE,
            OemMaterialPartType.FILE,
            OemMaterialPartType.VIDEO -> buildAttachmentPartPreview(part)
        }
    }

    private fun buildAttachmentPartPreview(part: OemMaterialPart): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#F7F8FF"))
            }
            buildFilePreview(part.filePath, part.type.name.lowercase())?.let { preview ->
                addView(
                    preview,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(180),
                    )
                )
            }
            addView(
                TextView(context).apply {
                    text = part.title
                    setTextColor(Color.parseColor("#2A2251"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    val params = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    params.topMargin = dp(8)
                    layoutParams = params
                }
            )
            addView(
                TextView(context).apply {
                    text = listOfNotNull(part.mimeType, part.sizeText).joinToString(" · ")
                    setTextColor(Color.parseColor("#6B6580"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                }
            )
        }
    }

    private fun buildFilePreview(filePath: String?, kind: String): View? {
        val file = filePath?.takeIf { it.isNotBlank() }?.let(::File)
        if (file?.exists() != true) return null
        return when (kind) {
            OemMaterialCategory.IMAGES.wireValue,
            OemMaterialPartType.IMAGE.name.lowercase() -> {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
                ImageView(context).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    adjustViewBounds = true
                }
            }
            OemMaterialCategory.VIDEOS.wireValue,
            OemMaterialPartType.VIDEO.name.lowercase() -> {
                val bitmap = runCatching {
                    MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(file.absolutePath)
                        retriever.frameAtTime
                    }
                }.getOrNull()
                if (bitmap == null) {
                    buildFallbackIcon(android.R.drawable.ic_media_play)
                } else {
                    ImageView(context).apply {
                        setImageBitmap(bitmap)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        adjustViewBounds = true
                    }
                }
            }
            else -> buildFallbackIcon(android.R.drawable.ic_menu_save)
        }
    }

    private fun buildFallbackIcon(iconRes: Int): View {
        return ImageView(context).apply {
            setImageResource(iconRes)
            val padding = dp(24)
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.parseColor("#EEF0FF"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return ""
        val kb = 1024L
        val mb = kb * 1024L
        return when {
            sizeBytes >= mb -> String.format("%.1f MB", sizeBytes.toDouble() / mb.toDouble())
            sizeBytes >= kb -> String.format("%.1f KB", sizeBytes.toDouble() / kb.toDouble())
            else -> "$sizeBytes B"
        }
    }

    private fun parseSizeText(sizeText: String?): Long {
        return sizeText
            ?.substringBefore(' ')
            ?.toDoubleOrNull()
            ?.let { value ->
                when {
                    sizeText.contains("MB", ignoreCase = true) -> (value * 1024 * 1024).toLong()
                    sizeText.contains("KB", ignoreCase = true) -> (value * 1024).toLong()
                    else -> value.toLong()
                }
            } ?: 0L
    }

    private fun OemPickerType.toFavoritePickerType(): FavoritePickerType {
        return when (this) {
            OemPickerType.TEXT -> FavoritePickerType.TEXT
            OemPickerType.IMAGE -> FavoritePickerType.IMAGE
            OemPickerType.FILE -> FavoritePickerType.FILE
            OemPickerType.VIDEO -> FavoritePickerType.VIDEO
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
}
