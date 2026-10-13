package org.fcitx.fcitx5.android.input.materialhub

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.util.Log
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.input.favorite.FavoriteAdapter
import org.fcitx.fcitx5.android.input.favorite.FavoriteAttachmentType
import org.fcitx.fcitx5.android.input.favorite.FavoriteBridge
import org.fcitx.fcitx5.android.input.favorite.FavoriteCategory
import org.fcitx.fcitx5.android.input.favorite.FavoriteComposerOverlay
import org.fcitx.fcitx5.android.input.favorite.FavoriteComposerSession
import org.fcitx.fcitx5.android.input.favorite.FavoriteEmbeddedPickerItem
import org.fcitx.fcitx5.android.input.favorite.FavoriteEmbeddedPickerStore
import org.fcitx.fcitx5.android.input.favorite.FavoriteFileCategory
import org.fcitx.fcitx5.android.input.favorite.FavoriteFolderItem
import org.fcitx.fcitx5.android.input.favorite.FavoriteGridSpacingDecoration
import org.fcitx.fcitx5.android.input.favorite.FavoriteDiagnosticEvent
import org.fcitx.fcitx5.android.input.favorite.FavoriteHostShareDispatchResult
import org.fcitx.fcitx5.android.input.favorite.FavoriteHostInputRestoreBaseline
import org.fcitx.fcitx5.android.input.favorite.FavoriteListItem
import org.fcitx.fcitx5.android.input.favorite.FavoriteNavigationItem
import org.fcitx.fcitx5.android.input.favorite.FavoriteNavigationMode
import org.fcitx.fcitx5.android.input.favorite.FavoritePart
import org.fcitx.fcitx5.android.input.favorite.FavoritePickerType
import org.fcitx.fcitx5.android.input.favorite.FavoriteRecord
import org.fcitx.fcitx5.android.input.favorite.FavoriteRecordSource
import org.fcitx.fcitx5.android.input.favorite.FavoriteStorage
import org.fcitx.fcitx5.android.input.favorite.FavoriteUi
import org.fcitx.fcitx5.android.utils.toast
import splitties.dimensions.dp
import java.util.UUID

private object MaterialHubFavoriteWorker {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

data class MaterialHubNavigationConfig(
    val mode: FavoriteNavigationMode = FavoriteNavigationMode.TYPE_ONLY,
    val categories: List<FavoriteNavigationItem> = emptyList(),
)

data class MaterialHubTaxonomyItem(
    val id: String,
    val name: String,
    val sort: Int = 0,
    val enabled: Boolean = true,
)

data class MaterialHubTaxonomySnapshot(
    val categories: List<MaterialHubTaxonomyItem> = emptyList(),
    val tags: List<MaterialHubTaxonomyItem> = emptyList(),
)

enum class MaterialHubTaxonomyTarget {
    CATEGORY,
    TAG,
}

private enum class TaxonomyMutationAction {
    CREATE,
    UPDATE,
    DELETE,
}

data class MaterialHubTaxonomyMutationResult(
    val success: Boolean,
    val item: MaterialHubTaxonomyItem? = null,
    val errorMessage: String? = null,
)

class MaterialHubFavoriteController(
    private val context: Context,
    private val onKeyboardVisibilityChanged: (Boolean) -> Int,
    private val onRequestTextComposerActivity: (() -> Boolean)? = null,
    private val onRequestClose: (afterClosed: (() -> Unit)?) -> Unit,
    private val onPreviewAttachment: (FavoritePart.Attachment, onBackToMaterialGroup: () -> Unit) -> Unit,
    private val onEnsurePickerPermission: ((FavoritePickerType, onResult: (Boolean) -> Unit) -> Unit)? = null,
    private val isLoggedIn: () -> Boolean,
    private val onLoginRequested: () -> Unit,
    private val userAvatarProvider: () -> Bitmap?,
    private val defaultAvatarProvider: () -> Bitmap?,
    private val loadRemoteFavorites: (suspend () -> List<FavoriteRecord>)? = null,
    private val syncRemoteFavorites: (suspend () -> Unit)? = null,
    private val loadNavigationConfig: (suspend () -> MaterialHubNavigationConfig)? = null,
    private val loadTaxonomySnapshot: (suspend () -> MaterialHubTaxonomySnapshot)? = null,
    private val createTaxonomyItem: (suspend (MaterialHubTaxonomyTarget, String) -> MaterialHubTaxonomyMutationResult)? = null,
    private val updateTaxonomyItem: (suspend (MaterialHubTaxonomyTarget, MaterialHubTaxonomyItem) -> MaterialHubTaxonomyMutationResult)? = null,
    private val deleteTaxonomyItem: (suspend (MaterialHubTaxonomyTarget, String) -> MaterialHubTaxonomyMutationResult)? = null,
) {
    // #region debug-point A:idlefish-keyboard-focus-reporter
    private fun reportIdlefishKeyboardFocusDebug(
        hypothesisId: String,
        location: String,
        msg: String,
        data: String,
    ) {
        Log.d(TAG, "$location $hypothesisId $msg $data")
        FavoriteBridge.reportDiagnosticEvent(
            FavoriteDiagnosticEvent(
                category = "material_restore",
                name = "debug",
                level = "DEBUG",
                message = msg,
                attributes = mapOf(
                    "hypothesisId" to hypothesisId,
                    "location" to location,
                    "payload" to data,
                ),
            ),
        )
    }
    // #endregion

    private enum class TransientPreviewPageState {
        MAIN,
        PICKER,
        TEXT_EDITOR,
    }

    private val storage = FavoriteStorage(context)
    private val embeddedPickerStore = FavoriteEmbeddedPickerStore(context)
    private val ui = FavoriteUi(context, enableFloatingAddMenu = true)
    private val adapter = FavoriteAdapter(
        onPreviewClick = ::handlePreviewClick,
        onFolderClick = ::openFolder,
    )
    private val composerOverlay = FavoriteComposerOverlay(
        context = context,
        ui = ui,
        onOpenList = { reopenListFromComposer() },
        onCloseToKeyboard = { requestCloseToHost() },
        onVisibilityChanged = { visible ->
            syncPrimaryActions(composerVisible = visible)
        },
        onKeyboardVisibilityChanged = { visible -> onKeyboardVisibilityChanged(visible) },
        onTextEditorInputFinished = {},
    )

    private var selectedType = FavoriteCategory.RECENT
    private var visibleCount = PAGE_SIZE
    private var autoSendEnabled = false
    private var searchModeActive = false
    private var searchKeyword = ""
    private var selectedCategoryId: String? = null
    private var preSearchType = FavoriteCategory.RECENT
    private var preSearchCategoryId: String? = null
    private val searchDebounceHandler = Handler(Looper.getMainLooper())
    private var searchDebounceRunnable: Runnable? = null
    private var localRecords: List<FavoriteRecord> = emptyList()
    private var remoteRecords: List<FavoriteRecord> = emptyList()
    private var records: List<FavoriteRecord> = emptyList()
    private var navigationConfig = MaterialHubNavigationConfig()
    private var taxonomySnapshot = MaterialHubTaxonomySnapshot()
    private var taxonomyPageRequestToken = 0
    private var localLoadGeneration = 0
    private var localLoadInProgress = false
    private var remoteSyncInProgress = false
    private var browsingFolder: FavoriteFolderItem? = null
    private var floatingMenuExpanded = false
    private var scrollListener: RecyclerView.OnScrollListener? = null
    private var sendingInProgress = false
    private var transientComposerBackHandler: (() -> Unit)? = null
    private var transientPreviewView: FavoriteMaterialGroupPreviewView? = null
    private var transientPreviewPersistedRecord: FavoriteRecord? = null
    private var transientPreviewPageState: TransientPreviewPageState? = null
    private var transientFloatingAddSuppressed = false
    private val transientPickerItemsCache = mutableMapOf<FavoritePickerType, List<FavoriteEmbeddedPickerItem>>()
    private val transientPickerVisibleCount = mutableMapOf<FavoritePickerType, Int>()
    private val transientPickerScrollY = mutableMapOf<FavoritePickerType, Int>()
    private var launchHostPackageName: String? = null
    private val loadingStateView by lazy { buildLoadingStateView() }

    private val folderItems by lazy {
        listOf(
            FavoriteFolderItem(
                id = "folder_personal",
                title = context.getString(org.fcitx.fcitx5.android.R.string.favorite_folder_title_placeholder),
                materialCount = 30,
                iconRes = org.fcitx.fcitx5.android.R.drawable.ic_favorite_folder_personal,
                backgroundRes = org.fcitx.fcitx5.android.R.drawable.bg_favorite_folder_card_personal,
            ),
            FavoriteFolderItem(
                id = "folder_academic",
                title = context.getString(org.fcitx.fcitx5.android.R.string.favorite_folder_title_placeholder),
                materialCount = 102,
                iconRes = org.fcitx.fcitx5.android.R.drawable.ic_favorite_folder_academic,
                backgroundRes = org.fcitx.fcitx5.android.R.drawable.bg_favorite_folder_card_academic,
            ),
            FavoriteFolderItem(
                id = "folder_work",
                title = context.getString(org.fcitx.fcitx5.android.R.string.favorite_folder_title_placeholder),
                materialCount = 300,
                iconRes = org.fcitx.fcitx5.android.R.drawable.ic_favorite_folder_work,
                backgroundRes = org.fcitx.fcitx5.android.R.drawable.bg_favorite_folder_card_work,
            ),
            FavoriteFolderItem(
                id = "folder_other",
                title = context.getString(org.fcitx.fcitx5.android.R.string.favorite_folder_title_placeholder),
                materialCount = 201,
                iconRes = org.fcitx.fcitx5.android.R.drawable.ic_favorite_folder_other,
                backgroundRes = org.fcitx.fcitx5.android.R.drawable.bg_favorite_folder_card_other,
            ),
        )
    }

    val rootView: View = ui.root

    init {
        ensureInitialized()
    }

    fun show(
        startInComposer: Boolean = false,
        quickTextEntry: Boolean = false,
        restorePickerType: String? = null,
        hostPackageName: String? = null,
    ) {
        launchHostPackageName = hostPackageName
        autoSendEnabled = false
        if (!isLoggedIn()) {
            showLoginRequiredState()
            return
        }
        loadFavorites()
        restoreRemoteFavoritesFromCache()
        syncRemoteFavoritesInBackground()
        loadNavigationConfigInBackground()
        if (startInComposer) {
            showComposer(
                quickTextEntry = quickTextEntry,
                restorePickerType = restorePickerType,
            )
            return
        }
        showList()
    }

    fun refreshSessionState() {
        // onResume can immediately follow a requested composer page. Do not replace that
        // page with the material list while its editor keyboard is being opened.
        if (transientComposerBackHandler != null || composerOverlay.isShowing()) {
            return
        }
        resetListChrome()
        ui.setListTitleVisible(true)
        if (!isLoggedIn()) {
            showLoginRequiredState()
            return
        }
        autoSendEnabled = false
        ui.setEmptyStateContent(null)
        loadFavorites()
        restoreRemoteFavoritesFromCache()
        syncRemoteFavoritesInBackground()
        loadNavigationConfigInBackground()
        showList(animated = false)
    }

    fun showRestrictedState(title: String, message: String) {
        collapseFloatingActionMenu()
        browsingFolder = null
        ui.renderFolderBrowsingState(null)
        ui.setListTitle(title.ifBlank { "素材库" })
        ui.setListTitleVisible(false)
        ui.setListSearchVisible(false)
        ui.setListCategoryRowVisible(false)
        ui.setListManageActionVisible(false)
        ui.listAddActionView.visibility = View.VISIBLE
        ui.floatingAddButton.visibility = View.GONE
        ui.floatingActionMenu.visibility = View.GONE
        ui.floatingMenuScrim.visibility = View.GONE
        ui.setEmptyStateContent(
            buildRestrictedStateView(
                message = message.ifBlank { "素材库当前被限制使用，请联系企业管理员" },
            ),
        )
        ui.showEmptyState(true)
        ui.showListMode(animated = false)
        ui.root.requestFocus()
    }

    fun handleBackPressed(): Boolean {
        // #region debug-point flash-preview-blank-back
        Log.d(
            "FlashPreviewDebug",
            "handleBackPressed drawerMask=${ui.drawerMask.isVisible} floating=$floatingMenuExpanded transient=${transientComposerBackHandler != null} browsingFolder=${browsingFolder?.id}"
        )
        // #endregion
        if (ui.drawerMask.isVisible) {
            ui.setDrawerVisible(false)
            return true
        }
        if (floatingMenuExpanded) {
            collapseFloatingActionMenu()
            return true
        }
        transientComposerBackHandler?.let { backHandler ->
            backHandler.invoke()
            return true
        }
        if (composerOverlay.handleBackPressed()) {
            return true
        }
        if (searchModeActive) {
            exitSearchMode()
            return true
        }
        if (browsingFolder != null) {
            browsingFolder = null
            bindItems(scrollToTop = true)
            return true
        }
        onRequestClose(null)
        return true
    }

    fun handleSystemKeyboardHidden(): Boolean {
        return composerOverlay.handleSystemKeyboardHidden()
    }

    fun destroy() {
        cancelPendingSearchFilter()
        clearTransientPreviewState()
        ui.hideComposerTransientContent()
        transientComposerBackHandler = null
        composerOverlay.hide()
    }

    fun showTransientComposerPage(
        title: String,
        contentView: View,
        onBack: () -> Unit,
        showAddAction: Boolean = false,
        showSaveAction: Boolean = false,
        actionText: String? = null,
        actionTextEnabled: Boolean = false,
        onSaveAction: (() -> Unit)? = null,
        onTextAction: (() -> Unit)? = null,
        showDownloadAction: Boolean = false,
        onDownloadAction: (() -> Unit)? = null,
        suppressFloatingAdd: Boolean = false,
    ) {
        // #region debug-point flash-preview-blank-transient
        Log.d(
            "FlashPreviewDebug",
            "showTransientComposerPage title=$title content=${contentView.javaClass.simpleName} rootChildren=${(contentView as? ViewGroup)?.childCount ?: -1}"
        )
        // #endregion
        transientComposerBackHandler = onBack
        transientFloatingAddSuppressed = suppressFloatingAdd
        collapseFloatingActionMenu()
        ui.setDrawerVisible(false)
        onKeyboardVisibilityChanged(false)
        ui.setComposerKeyboardVisible(false)
        ui.configureComposerHeader(
            title = title,
            showAddAction = showAddAction,
            showSaveAction = showSaveAction,
            actionText = actionText,
            actionTextEnabled = actionTextEnabled,
            showDownloadAction = showDownloadAction,
        )
        ui.composerSaveButton.setOnClickListener { onSaveAction?.invoke() }
        ui.composerTextActionButton.setOnClickListener { onTextAction?.invoke() }
        ui.downloadActionButton.setOnClickListener { onDownloadAction?.invoke() }
        ui.showComposerMode(animated = false)
        ui.showComposerTransientContent(contentView)
        ui.root.bringToFront()
        syncPrimaryActions(composerVisible = true)
    }

    fun hideTransientComposerPage() {
        if (transientComposerBackHandler == null) return
        clearTransientPreviewState()
        transientComposerBackHandler = null
        ui.hideComposerTransientContent()
        composerOverlay.rerenderCurrentState()
    }

    private fun hideTransientPreviewToList() {
        if (transientComposerBackHandler == null) return
        clearTransientPreviewState()
        transientComposerBackHandler = null
        ui.hideComposerTransientContent()
        showList(animated = false)
    }

    private fun hideTransientPageToList() {
        transientComposerBackHandler = null
        ui.hideComposerTransientContent()
        showList(animated = false)
    }

    private fun clearTransientPreviewState() {
        transientPreviewPersistedRecord?.let { record ->
            storage.cleanupUnusedAssets(
                record.id,
                record.parts.filterIsInstance<FavoritePart.Attachment>().map { it.storedPath }.toSet()
            )
        }
        transientPreviewPersistedRecord = null
        transientPreviewView = null
        transientPreviewPageState = null
        transientFloatingAddSuppressed = false
        transientPickerItemsCache.clear()
        transientPickerVisibleCount.clear()
        transientPickerScrollY.clear()
        composerOverlay.bindDefaultAddAction()
        ui.addActionButton.contentDescription =
            context.getString(org.fcitx.fcitx5.android.R.string.favorite_add)
    }

    private fun transientPickerInitialVisibleCount(type: FavoritePickerType): Int {
        return when (type) {
            FavoritePickerType.IMAGE,
            FavoritePickerType.VIDEO,
            FavoritePickerType.FILE -> 80
            FavoritePickerType.TEXT -> 0
        }
    }

    private fun transientPickerLoadMoreBatchSize(type: FavoritePickerType): Int {
        return when (type) {
            FavoritePickerType.IMAGE,
            FavoritePickerType.VIDEO,
            FavoritePickerType.FILE -> 80
            FavoritePickerType.TEXT -> 0
        }
    }

    private fun ensureTransientPickerVisibleCount(type: FavoritePickerType, totalCount: Int) {
        val currentVisible = transientPickerVisibleCount[type] ?: 0
        if (currentVisible > 0) return
        transientPickerVisibleCount[type] = transientPickerInitialVisibleCount(type).coerceAtMost(totalCount)
    }

    private fun resetTransientPickerVisibleCount(type: FavoritePickerType, totalCount: Int) {
        transientPickerVisibleCount[type] = transientPickerInitialVisibleCount(type).coerceAtMost(totalCount)
    }

    private fun visibleTransientPickerItems(
        type: FavoritePickerType,
        allItems: List<FavoriteEmbeddedPickerItem>,
        selectedFileCategory: FavoriteFileCategory,
    ): List<FavoriteEmbeddedPickerItem> {
        val visibleCount = transientPickerVisibleCount[type]
            ?: transientPickerInitialVisibleCount(type).coerceAtMost(allItems.size)
        if (type != FavoritePickerType.FILE || selectedFileCategory == FavoriteFileCategory.ALL) {
            return allItems.take(visibleCount.coerceAtMost(allItems.size))
        }
        val result = ArrayList<FavoriteEmbeddedPickerItem>()
        var matchedCount = 0
        allItems.forEach { item ->
            result += item
            if (item.fileCategory == selectedFileCategory) {
                matchedCount += 1
            }
            if (matchedCount >= visibleCount) {
                return result
            }
        }
        return result
    }

    private fun transientPickerTotalCount(
        type: FavoritePickerType,
        allItems: List<FavoriteEmbeddedPickerItem>,
        selectedFileCategory: FavoriteFileCategory,
    ): Int {
        return if (type == FavoritePickerType.FILE && selectedFileCategory != FavoriteFileCategory.ALL) {
            allItems.count { it.fileCategory == selectedFileCategory }
        } else {
            allItems.size
        }
    }

    private fun previewTransientPickerFileItem(
        item: FavoriteEmbeddedPickerItem,
        pickerType: FavoritePickerType,
        currentScrollY: Int,
        onBackToPicker: () -> Unit,
    ) {
        if (pickerType != FavoritePickerType.FILE) return
        transientPickerScrollY[pickerType] = currentScrollY
        context.toast("正在打开${item.displayName}")
        Thread {
            val attachment = storage.createPreviewAttachmentFromUri(item.uri, FavoritePickerType.FILE)
            ui.root.post {
                if (attachment == null) {
                    context.toast("文档预览打开失败，请重试")
                    return@post
                }
                onPreviewAttachment(attachment, onBackToPicker)
            }
        }.start()
    }

    private fun buildTransientImportOverlay(): View {
        val indicator = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            isClickable = false
            isFocusable = false
        }
        val message = TextView(context).apply {
            text = "正在加载文件..."
            textSize = 15f
            setTextColor(Color.parseColor("#2A2251"))
            gravity = Gravity.CENTER
            setPadding(0, context.dp(12), 0, 0)
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(16).toFloat()
                setColor(Color.parseColor("#F7F8FC"))
            }
            setPadding(context.dp(24), context.dp(20), context.dp(24), context.dp(18))
            addView(
                indicator,
                LinearLayout.LayoutParams(
                    context.dp(220),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                message,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        return FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            addView(
                panel,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
    }

    private fun resetAutoSendMode() {
        autoSendEnabled = false
    }

    private fun resolveEffectiveAutoSendMode(showFailureToast: Boolean): Boolean {
        if (!autoSendEnabled) return false
        val ready = MaterialHubShizukuChecker.getStatus(context).ready
        if (ready) return true
        resetAutoSendMode()
        if (showFailureToast) {
            context.toast("自动化异常，请先到我的-设置-自动化设置检查 Stellar / ADB 授权，或切换为手动发送模式")
        }
        return false
    }

    private fun ensureInitialized() {
        ui.recyclerView.layoutManager = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL).apply {
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
        }
        if (ui.recyclerView.itemDecorationCount == 0) {
            ui.recyclerView.addItemDecoration(FavoriteGridSpacingDecoration(context.dp(12)))
        }
        ui.recyclerView.adapter = adapter
        ui.searchActionView.setOnClickListener { enterSearchMode() }
        ui.searchInputView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (!searchModeActive) return
                scheduleSearchFilter(s?.toString().orEmpty())
            }
        })
        ui.searchInputView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                flushSearchFilter()
                true
            } else {
                false
            }
        }
        ui.searchInputView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && searchModeActive) {
                onKeyboardVisibilityChanged(true)
            }
        }
        ui.listAddActionView.setImageResource(org.fcitx.fcitx5.android.R.drawable.ic_favorite_panel_close)
        ui.listAddActionView.imageTintList = null
        ui.listAddActionView.rotation = 0f
        ui.listAddActionView.contentDescription = context.getString(org.fcitx.fcitx5.android.R.string.favorite_close)
        ui.listAddActionView.setOnClickListener { onRequestClose(null) }
        ui.listManageActionView.setOnClickListener {
            collapseFloatingActionMenu()
            openTaxonomyManagementPage()
        }
        ui.composerBackAction.setOnClickListener {
            if (transientComposerBackHandler != null) {
                transientComposerBackHandler?.invoke()
            } else {
                handleBackPressed()
            }
        }
        ui.drawerMask.setOnClickListener { ui.setDrawerVisible(false) }
        ui.drawerButtons.forEach { (type, button) ->
            button.setOnClickListener {
                ui.setDrawerVisible(false)
                openComposerForType(type)
            }
        }
        ui.collapseFloatingMenuImmediate()
        ui.floatingAddButton.setOnClickListener { toggleFloatingActionMenu() }
        ui.floatingMenuScrim.setOnClickListener { setFloatingActionMenuExpanded(false, animated = true) }
        ui.floatingActionItems.forEach { (type, view) ->
            view.setOnClickListener { openComposerForType(type) }
        }
        ui.onPrimaryNavigationSelected = { itemId ->
            collapseFloatingActionMenu()
            if (handlePrimaryNavigationSelected(itemId)) {
                browsingFolder = null
                visibleCount = PAGE_SIZE
                bindItems(scrollToTop = true)
            }
        }
        ui.onSecondaryNavigationSelected = { itemId ->
            collapseFloatingActionMenu()
            if (handleSecondaryNavigationSelected(itemId)) {
                browsingFolder = null
                visibleCount = PAGE_SIZE
                bindItems(scrollToTop = true)
            }
        }
        ui.listBackAction.setOnClickListener {
            collapseFloatingActionMenu()
            if (searchModeActive) {
                exitSearchMode()
                return@setOnClickListener
            }
            browsingFolder = null
            bindItems(scrollToTop = true)
        }
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (floatingMenuExpanded && kotlin.math.abs(dy) > context.dp(4)) {
                    collapseFloatingActionMenu()
                }
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? StaggeredGridLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPositions(null).maxOrNull() ?: return
                if (lastVisible >= adapter.itemCount - 3) {
                    loadMore()
                }
            }
        }.also(ui.recyclerView::addOnScrollListener)
    }

    private fun loadFavorites(scrollToTop: Boolean = true) {
        val generation = ++localLoadGeneration
        localLoadInProgress = true
        if (records.isEmpty()) {
            showLoadingState()
        }
        MaterialHubFavoriteWorker.scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                storage.listFavorites()
            }
            if (generation != localLoadGeneration) return@launch
            localLoadInProgress = false
            localRecords = loaded
            records = mergeFavoriteRecords(localRecords, remoteRecords)
            visibleCount = PAGE_SIZE
            bindItems(scrollToTop = scrollToTop)
        }
    }

    private fun loadNavigationConfigInBackground() {
        val loader = loadNavigationConfig ?: return
        MaterialHubFavoriteWorker.scope.launch {
            val loaded = runCatching { loader.invoke() }
                .onFailure { error ->
                    Log.e(TAG, "loadNavigationConfigInBackground failed", error)
                }
                .getOrDefault(MaterialHubNavigationConfig())
            navigationConfig = loaded
            ensureValidCategorySelection()
            Log.d(
                TAG,
                "navigationConfig updated: mode=${loaded.mode}, configuredCategories=${loaded.categories.size}, availableCategories=${availableCategoryItems().size}, selectedCategoryId=$selectedCategoryId, records=${records.size}",
            )
            bindItems(scrollToTop = false)
        }
    }

    private fun supportsTaxonomyManagement(): Boolean {
        return loadTaxonomySnapshot != null &&
            createTaxonomyItem != null &&
            updateTaxonomyItem != null &&
            deleteTaxonomyItem != null
    }

    private fun openTaxonomyManagementPage() {
        if (!supportsTaxonomyManagement()) {
            context.toast("当前宿主未接入分类/标签管理")
            return
        }
        if (!isLoggedIn()) {
            onLoginRequested()
            return
        }
        val requestToken = ++taxonomyPageRequestToken
        showTransientComposerPage(
            title = context.getString(org.fcitx.fcitx5.android.R.string.favorite_manage_taxonomy_title),
            contentView = buildTaxonomyLoadingView(),
            onBack = {
                taxonomyPageRequestToken++
                hideTransientPageToList()
            },
            showAddAction = false,
            suppressFloatingAdd = true,
        )
        MaterialHubFavoriteWorker.scope.launch {
            val snapshot = runCatching { loadTaxonomySnapshot?.invoke() ?: MaterialHubTaxonomySnapshot() }
                .onFailure { error ->
                    Log.e(TAG, "openTaxonomyManagementPage failed", error)
                }
                .getOrDefault(MaterialHubTaxonomySnapshot())
            if (requestToken != taxonomyPageRequestToken) return@launch
            taxonomySnapshot = snapshot
            navigationConfig = navigationConfig.copy(
                categories = snapshot.categories.map { FavoriteNavigationItem(it.id, it.name) },
            )
            ensureValidCategorySelection()
            bindItems(scrollToTop = false)
            showTransientComposerPage(
                title = context.getString(org.fcitx.fcitx5.android.R.string.favorite_manage_taxonomy_title),
                contentView = buildTaxonomyManagementView(snapshot),
                onBack = {
                    taxonomyPageRequestToken++
                    hideTransientPageToList()
                },
                showAddAction = false,
                suppressFloatingAdd = true,
            )
        }
    }

    private fun buildTaxonomyLoadingView(): View {
        return FrameLayout(context).apply {
            minimumHeight = context.dp(220)
            addView(
                ProgressBar(context),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }
    }

    private fun buildTaxonomyManagementView(snapshot: MaterialHubTaxonomySnapshot): View {
        return ScrollView(context).apply {
            isFillViewport = true
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(24))
                    addView(
                        TextView(context).apply {
                            text = context.getString(org.fcitx.fcitx5.android.R.string.favorite_manage_taxonomy_tip)
                            setTextColor(Color.parseColor("#667085"))
                            textSize = 14f
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            bottomMargin = context.dp(16)
                        },
                    )
                    addView(buildTaxonomySection(
                        title = context.getString(org.fcitx.fcitx5.android.R.string.favorite_manage_categories),
                        emptyText = context.getString(org.fcitx.fcitx5.android.R.string.favorite_manage_categories_empty),
                        items = snapshot.categories,
                        target = MaterialHubTaxonomyTarget.CATEGORY,
                    ))
                    addView(buildTaxonomySection(
                        title = context.getString(org.fcitx.fcitx5.android.R.string.favorite_manage_tags),
                        emptyText = context.getString(org.fcitx.fcitx5.android.R.string.favorite_manage_tags_empty),
                        items = snapshot.tags,
                        target = MaterialHubTaxonomyTarget.TAG,
                    ), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = context.dp(16)
                    })
                },
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun buildTaxonomySection(
        title: String,
        emptyText: String,
        items: List<MaterialHubTaxonomyItem>,
        target: MaterialHubTaxonomyTarget,
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(20).toFloat()
                setColor(Color.parseColor("#FFFFFF"))
            }
            setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(context).apply {
                            text = title
                            setTextColor(Color.parseColor("#111827"))
                            textSize = 16f
                            typeface = Typeface.defaultFromStyle(Typeface.BOLD)
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(
                        buildTaxonomyActionChip(
                            text = context.getString(org.fcitx.fcitx5.android.R.string.favorite_manage_add),
                            textColor = "#2B95EB",
                            backgroundColor = "#E9F4FF",
                        ).apply {
                            setOnClickListener { showTaxonomyInputDialog(target = target) }
                        },
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            if (items.isEmpty()) {
                addView(
                    TextView(context).apply {
                        text = emptyText
                        setTextColor(Color.parseColor("#98A2B3"))
                        textSize = 14f
                        gravity = Gravity.CENTER_HORIZONTAL
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = context.dp(18)
                    },
                )
            } else {
                items.forEachIndexed { index, item ->
                    addView(
                        buildTaxonomyRow(item = item, target = target),
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = if (index == 0) context.dp(14) else context.dp(10)
                        },
                    )
                }
            }
        }
    }

    private fun buildTaxonomyRow(
        item: MaterialHubTaxonomyItem,
        target: MaterialHubTaxonomyTarget,
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(14).toFloat()
                setColor(Color.parseColor("#F8FAFC"))
            }
            setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
            addView(
                TextView(context).apply {
                    text = item.name
                    setTextColor(Color.parseColor("#111827"))
                    textSize = 15f
                    maxLines = 1
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                buildTaxonomyActionChip(
                    text = context.getString(org.fcitx.fcitx5.android.R.string.edit),
                    textColor = "#2B95EB",
                    backgroundColor = "#E9F4FF",
                ).apply {
                    setOnClickListener { showTaxonomyInputDialog(target = target, editingItem = item) }
                },
            )
            addView(
                buildTaxonomyActionChip(
                    text = context.getString(org.fcitx.fcitx5.android.R.string.delete),
                    textColor = "#D92D20",
                    backgroundColor = "#FEE4E2",
                ).apply {
                    setOnClickListener { confirmDeleteTaxonomyItem(target = target, item = item) }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = context.dp(8)
                },
            )
        }
    }

    private fun buildTaxonomyActionChip(
        text: String,
        textColor: String,
        backgroundColor: String,
    ): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor(textColor))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(context.dp(10), context.dp(6), context.dp(10), context.dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(14).toFloat()
                setColor(Color.parseColor(backgroundColor))
            }
            isClickable = true
            isFocusable = true
        }
    }

    private fun showTaxonomyInputDialog(
        target: MaterialHubTaxonomyTarget,
        editingItem: MaterialHubTaxonomyItem? = null,
    ) {
        val editText = EditText(context).apply {
            setText(editingItem?.name.orEmpty())
            setSelection(text.length)
            hint = context.getString(
                if (target == MaterialHubTaxonomyTarget.CATEGORY) {
                    org.fcitx.fcitx5.android.R.string.favorite_manage_category_hint
                } else {
                    org.fcitx.fcitx5.android.R.string.favorite_manage_tag_hint
                },
            )
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(
                context.getString(
                    when {
                        editingItem != null && target == MaterialHubTaxonomyTarget.CATEGORY ->
                            org.fcitx.fcitx5.android.R.string.favorite_manage_edit_category
                        editingItem != null ->
                            org.fcitx.fcitx5.android.R.string.favorite_manage_edit_tag
                        target == MaterialHubTaxonomyTarget.CATEGORY ->
                            org.fcitx.fcitx5.android.R.string.favorite_manage_add_category
                        else ->
                            org.fcitx.fcitx5.android.R.string.favorite_manage_add_tag
                    },
                ),
            )
            .setView(editText)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(org.fcitx.fcitx5.android.R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = editText.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    context.toast(
                        context.getString(
                            if (target == MaterialHubTaxonomyTarget.CATEGORY) {
                                org.fcitx.fcitx5.android.R.string.favorite_manage_category_required
                            } else {
                                org.fcitx.fcitx5.android.R.string.favorite_manage_tag_required
                            },
                        ),
                    )
                    return@setOnClickListener
                }
                dialog.dismiss()
                mutateTaxonomyItem(
                    target = target,
                    action = if (editingItem == null) TaxonomyMutationAction.CREATE else TaxonomyMutationAction.UPDATE,
                    name = name,
                    editingItem = editingItem,
                )
            }
        }
        dialog.show()
    }

    private fun confirmDeleteTaxonomyItem(
        target: MaterialHubTaxonomyTarget,
        item: MaterialHubTaxonomyItem,
    ) {
        AlertDialog.Builder(context)
            .setTitle(
                context.getString(
                    if (target == MaterialHubTaxonomyTarget.CATEGORY) {
                        org.fcitx.fcitx5.android.R.string.favorite_manage_delete_category
                    } else {
                        org.fcitx.fcitx5.android.R.string.favorite_manage_delete_tag
                    },
                ),
            )
            .setMessage(
                context.getString(
                    org.fcitx.fcitx5.android.R.string.favorite_manage_delete_message,
                    item.name,
                ),
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(org.fcitx.fcitx5.android.R.string.delete) { _, _ ->
                mutateTaxonomyItem(
                    target = target,
                    action = TaxonomyMutationAction.DELETE,
                    editingItem = item,
                )
            }
            .show()
    }

    private fun mutateTaxonomyItem(
        target: MaterialHubTaxonomyTarget,
        action: TaxonomyMutationAction,
        name: String = "",
        editingItem: MaterialHubTaxonomyItem? = null,
    ) {
        MaterialHubFavoriteWorker.scope.launch {
            val result = when (action) {
                TaxonomyMutationAction.CREATE -> {
                    createTaxonomyItem?.invoke(target, name)
                }
                TaxonomyMutationAction.UPDATE -> {
                    val existing = editingItem ?: return@launch
                    updateTaxonomyItem?.invoke(target, existing.copy(name = name))
                }
                TaxonomyMutationAction.DELETE -> {
                    val existing = editingItem ?: return@launch
                    deleteTaxonomyItem?.invoke(target, existing.id)
                }
            } ?: MaterialHubTaxonomyMutationResult(success = false, errorMessage = "宿主未处理当前操作")
            if (!result.success) {
                context.toast(result.errorMessage ?: "操作失败，请重试")
                return@launch
            }
            context.toast(
                context.getString(
                    when (action) {
                        TaxonomyMutationAction.CREATE ->
                            if (target == MaterialHubTaxonomyTarget.CATEGORY) {
                                org.fcitx.fcitx5.android.R.string.favorite_manage_category_added
                            } else {
                                org.fcitx.fcitx5.android.R.string.favorite_manage_tag_added
                            }
                        TaxonomyMutationAction.UPDATE ->
                            if (target == MaterialHubTaxonomyTarget.CATEGORY) {
                                org.fcitx.fcitx5.android.R.string.favorite_manage_category_updated
                            } else {
                                org.fcitx.fcitx5.android.R.string.favorite_manage_tag_updated
                            }
                        TaxonomyMutationAction.DELETE ->
                            if (target == MaterialHubTaxonomyTarget.CATEGORY) {
                                org.fcitx.fcitx5.android.R.string.favorite_manage_category_deleted
                            } else {
                                org.fcitx.fcitx5.android.R.string.favorite_manage_tag_deleted
                            }
                    },
                ),
            )
            loadNavigationConfigInBackground()
            openTaxonomyManagementPage()
        }
    }

    private fun showLoadingState() {
        if (!isLoggedIn()) {
            showLoginRequiredState()
            return
        }
        ui.setEmptyStateContent(loadingStateView)
        ui.showEmptyState(true)
        adapter.submitItems(emptyList())
    }

    private fun buildLoadingStateView(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            addView(
                ProgressBar(context).apply {
                    isIndeterminate = true
                }
            )
            addView(
                TextView(context).apply {
                    text = "正在加载素材..."
                    setTextColor(Color.parseColor("#8A8F99"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                }.also { label ->
                    val params = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    params.topMargin = context.dp(12)
                    label.layoutParams = params
                }
            )
        }
    }

    private fun restoreRemoteFavoritesFromCache() {
        if (loadRemoteFavorites == null) {
            remoteRecords = emptyList()
            records = mergeFavoriteRecords(localRecords, remoteRecords)
            bindItems(scrollToTop = false)
            return
        }
        MaterialHubFavoriteWorker.scope.launch {
            reloadRemoteFavoritesFromCache(scrollToTop = false)
        }
    }

    private fun syncRemoteFavoritesInBackground() {
        val syncer = syncRemoteFavorites
        if (syncer == null) {
            return
        }
        if (remoteSyncInProgress) {
            return
        }
        remoteSyncInProgress = true
        MaterialHubFavoriteWorker.scope.launch {
            val syncResult = runCatching { syncer.invoke() }
            remoteSyncInProgress = false
            syncResult
                .onSuccess {
                    reloadRemoteFavoritesFromCache(scrollToTop = false)
                }
                .onFailure {
                    Log.e(TAG, "syncRemoteFavoritesInBackground failed", it)
                }
        }
    }

    private suspend fun reloadRemoteFavoritesFromCache(scrollToTop: Boolean) {
        val loader = loadRemoteFavorites ?: return
        val loaded = runCatching { loader.invoke() }
            .onFailure { error ->
                Log.e(TAG, "reloadRemoteFavoritesFromCache failed", error)
            }
            .getOrDefault(emptyList())
        remoteRecords = loaded
        records = mergeFavoriteRecords(localRecords, remoteRecords)
        visibleCount = PAGE_SIZE.coerceAtMost(currentRecordItems().size.coerceAtLeast(PAGE_SIZE))
        bindItems(scrollToTop = scrollToTop)
    }

    private fun reopenListFromComposer() {
        composerOverlay.hide()
        collapseFloatingActionMenu()
        loadFavorites()
        showList(animated = true)
    }

    private fun requestCloseToHost() {
        onKeyboardVisibilityChanged(false)
        onRequestClose(null)
    }

    private fun openComposerForType(type: FavoritePickerType) {
        if (!isLoggedIn()) {
            showLoginRequiredState()
            return
        }
        collapseFloatingActionMenu()
        Log.d(
            "FlashPreviewDebug",
            "openComposerForType type=$type fromPreview=${transientPreviewView != null} state=$transientPreviewPageState"
        )
        when (type) {
            FavoritePickerType.TEXT -> {
                if (onRequestTextComposerActivity?.invoke() != true) {
                    showQuickAddTextEditor()
                }
            }
            FavoritePickerType.IMAGE,
            FavoritePickerType.FILE,
            FavoritePickerType.VIDEO -> ensurePickerPermission(type) {
                showQuickAddAttachmentPicker(type)
            }
        }
    }

    private fun ensurePickerPermission(
        type: FavoritePickerType,
        onGranted: () -> Unit,
    ) {
        if (type == FavoritePickerType.TEXT) {
            onGranted()
            return
        }
        val handler = onEnsurePickerPermission
        if (handler == null) {
            onGranted()
            return
        }
        handler(type) { granted ->
            if (granted) {
                onGranted()
            }
        }
    }

    private fun showList(animated: Boolean = false) {
        composerOverlay.clearStaleEmptyState()
        resetListChrome()
        ui.showListMode(animated = animated)
        syncPrimaryActions(composerVisible = false)
        ui.root.requestFocus()
    }

    private fun resetListChrome() {
        ui.setListTitle(context.getString(org.fcitx.fcitx5.android.R.string.favorite_panel_title))
        ui.setListSearchVisible(true)
        ui.setListCategoryRowVisible(true)
        ui.setListManageActionVisible(false)
        ui.listAddActionView.visibility = View.VISIBLE
    }

    private fun showComposer(
        quickTextEntry: Boolean,
        restorePickerType: String? = null,
    ) {
        val pickerTypeToRestore = restorePickerType
            ?.let { runCatching { FavoritePickerType.valueOf(it) }.getOrNull() }
        if (pickerTypeToRestore != null) {
            openComposerForType(pickerTypeToRestore)
            return
        }
        if (quickTextEntry) {
            openComposerForType(FavoritePickerType.TEXT)
            return
        }
        openComposerForType(FavoritePickerType.TEXT)
    }

    private fun syncPrimaryActions(composerVisible: Boolean) {
        val previewFloatingAddVisible = transientPreviewView != null &&
            transientComposerBackHandler != null &&
            transientPreviewPageState == TransientPreviewPageState.MAIN &&
            transientPreviewPersistedRecord?.isRemoteManaged() != true &&
            !transientFloatingAddSuppressed
        if (composerVisible && !previewFloatingAddVisible) {
            ui.floatingAddButton.visibility = View.GONE
            ui.floatingActionMenu.visibility = View.GONE
            ui.floatingMenuScrim.visibility = View.GONE
            ui.setDrawerVisible(false)
            return
        }
        ui.floatingAddButton.visibility = View.VISIBLE
        ui.floatingAddButton.alpha = 1f
        ui.floatingAddButton.scaleX = 1f
        ui.floatingAddButton.scaleY = 1f
        ui.listAddActionView.visibility = View.VISIBLE
    }

    private fun toggleFloatingActionMenu() {
        setFloatingActionMenuExpanded(!floatingMenuExpanded, animated = true)
    }

    private fun collapseFloatingActionMenu() {
        setFloatingActionMenuExpanded(false, animated = false)
    }

    private fun setFloatingActionMenuExpanded(expanded: Boolean, animated: Boolean) {
        if (floatingMenuExpanded == expanded && (expanded || !animated)) return
        floatingMenuExpanded = expanded
        animateFloatingAddButton(expanded)
        if (expanded) {
            val items = ui.floatingActionItems.values.toList().asReversed()
            ui.floatingMenuScrim.animate().cancel()
            ui.floatingMenuScrim.visibility = View.VISIBLE
            ui.floatingMenuScrim.alpha = if (animated) 0f else 1f
            if (animated) {
                ui.floatingMenuScrim.animate()
                    .alpha(1f)
                    .setDuration(FLOATING_ITEM_ENTER_DURATION_MS)
                    .start()
            }
            ui.floatingActionMenu.visibility = View.VISIBLE
            ui.floatingActionMenu.alpha = 1f
            items.forEachIndexed { index, item ->
                item.animate().cancel()
                item.alpha = if (animated) 0f else 1f
                item.translationX = if (animated) context.dp(40).toFloat() else 0f
                item.scaleX = if (animated) 0.92f else 1f
                item.scaleY = if (animated) 0.92f else 1f
                if (animated) {
                    item.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setStartDelay(index * FLOATING_ITEM_STAGGER_MS)
                        .setDuration(FLOATING_ITEM_ENTER_DURATION_MS)
                        .start()
                }
            }
            return
        }
        val items = ui.floatingActionItems.values.toList()
        if (!animated) {
            items.forEach { it.animate().cancel() }
            ui.floatingMenuScrim.animate().cancel()
            ui.collapseFloatingMenuImmediate()
            return
        }
        ui.floatingMenuScrim.animate().cancel()
        ui.floatingMenuScrim.animate()
            .alpha(0f)
            .setDuration(FLOATING_ITEM_EXIT_DURATION_MS)
            .withEndAction {
                if (!floatingMenuExpanded) {
                    ui.floatingMenuScrim.visibility = View.GONE
                }
            }
            .start()
        items.forEachIndexed { index, item ->
            item.animate().cancel()
            item.animate()
                .alpha(0f)
                .translationX(context.dp(32).toFloat())
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setStartDelay(index * FLOATING_ITEM_STAGGER_MS / 2)
                .setDuration(FLOATING_ITEM_EXIT_DURATION_MS)
                .withEndAction {
                    if (!floatingMenuExpanded && index == items.lastIndex) {
                        ui.collapseFloatingMenuImmediate()
                    }
                }
                .start()
        }
    }

    private fun animateFloatingAddButton(expanded: Boolean) {
        ui.setFloatingAddButtonExpanded(expanded, animate = true)
    }

    private fun loadMore() {
        if (selectedType == FavoriteCategory.FOLDERS && browsingFolder == null) return
        val total = currentRecordItems().size
        if (visibleCount >= total) return
        visibleCount = (visibleCount + PAGE_SIZE).coerceAtMost(total)
        bindItems()
    }

    private fun bindItems(scrollToTop: Boolean = false) {
        if (!isLoggedIn()) {
            showLoginRequiredState()
            return
        }
        if (localLoadInProgress && records.isEmpty()) {
            showLoadingState()
            return
        }
        ensureValidCategorySelection()
        ui.setEmptyStateContent(
            if (searchModeActive) {
                if (searchKeyword.trim().isEmpty()) ui.searchIdleStateView else ui.searchEmptyStateView
            } else {
                null
            },
        )
        ui.renderPrimaryNavigation(primaryNavigationItems(), currentPrimarySelectionId())
        ui.renderSecondaryNavigation(secondaryNavigationItems(), currentSecondarySelectionId())
        ui.renderFolderBrowsingState(browsingFolder)
        val items = buildListItems()
        adapter.submitItems(items)
        ui.showEmptyState(items.isEmpty())
        if (scrollToTop && items.isNotEmpty()) {
            ui.recyclerView.scrollToPosition(0)
        }
    }

    private fun buildListItems(): List<FavoriteListItem> {
        return buildList {
            if (selectedType == FavoriteCategory.FOLDERS && browsingFolder == null) {
                addAll(folderItems.map(FavoriteListItem::FolderEntry))
            } else {
                addAll(currentRecordItems().take(visibleCount).map(FavoriteListItem::RecordEntry))
            }
        }
    }

    private fun filteredItems(): List<FavoriteRecord> {
        val base = records.sortedByDescending { it.updatedAt }.filter { record ->
            activeTypeFilter()?.let(record::matchesCategory) != false &&
                record.matchesMaterialCategory(activeCategoryFilterId())
        }
        if (!searchModeActive) return base
        val keyword = searchKeyword.trim()
        if (keyword.isEmpty()) return emptyList()
        return base.filter { record -> record.matchesSearchKeyword(keyword) }
    }

    private fun handlePrimaryNavigationSelected(itemId: String): Boolean {
        return when (resolvedNavigationMode()) {
            FavoriteNavigationMode.CATEGORY_PRIMARY_TYPE_SECONDARY,
            FavoriteNavigationMode.CATEGORY_ONLY -> {
                if (selectedCategoryId == itemId) {
                    false
                } else {
                    selectedCategoryId = itemId
                    true
                }
            }
            else -> {
                val nextType = parseTypeNavigationId(itemId) ?: return false
                if (selectedType == nextType) {
                    false
                } else {
                    selectedType = nextType
                    true
                }
            }
        }
    }

    private fun handleSecondaryNavigationSelected(itemId: String): Boolean {
        return when (resolvedNavigationMode()) {
            FavoriteNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY -> {
                if (selectedCategoryId == itemId) {
                    false
                } else {
                    selectedCategoryId = itemId
                    true
                }
            }
            FavoriteNavigationMode.CATEGORY_PRIMARY_TYPE_SECONDARY -> {
                val nextType = parseTypeNavigationId(itemId) ?: return false
                if (selectedType == nextType) {
                    false
                } else {
                    selectedType = nextType
                    true
                }
            }
            FavoriteNavigationMode.CATEGORY_ONLY,
            FavoriteNavigationMode.TYPE_ONLY -> false
        }
    }

    private fun resolvedNavigationMode(): FavoriteNavigationMode {
        return if (availableCategoryItems().isEmpty()) {
            FavoriteNavigationMode.TYPE_ONLY
        } else {
            navigationConfig.mode
        }
    }

    private fun activeCategoryFilterId(): String? {
        return when (resolvedNavigationMode()) {
            FavoriteNavigationMode.TYPE_ONLY -> null
            FavoriteNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY,
            FavoriteNavigationMode.CATEGORY_PRIMARY_TYPE_SECONDARY,
            FavoriteNavigationMode.CATEGORY_ONLY -> selectedCategoryId
        }
    }

    private fun activeTypeFilter(): FavoriteCategory? {
        return when (resolvedNavigationMode()) {
            FavoriteNavigationMode.CATEGORY_ONLY -> null
            FavoriteNavigationMode.TYPE_ONLY,
            FavoriteNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY,
            FavoriteNavigationMode.CATEGORY_PRIMARY_TYPE_SECONDARY -> selectedType
        }
    }

    private fun currentCategoryAssignment(): Pair<String, String>? {
        val mode = resolvedNavigationMode()
        if (mode == FavoriteNavigationMode.TYPE_ONLY) return null
        val categoryId = selectedCategoryId?.trim().orEmpty()
        if (categoryId.isBlank()) return null
        val category = availableCategoryItems().firstOrNull { it.id == categoryId } ?: return null
        return category.id to category.label
    }

    private fun primaryNavigationItems(): List<FavoriteNavigationItem> {
        return when (resolvedNavigationMode()) {
            FavoriteNavigationMode.CATEGORY_PRIMARY_TYPE_SECONDARY,
            FavoriteNavigationMode.CATEGORY_ONLY -> availableCategoryItems()
            FavoriteNavigationMode.TYPE_ONLY,
            FavoriteNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY -> ui.defaultTypeNavigationItems()
        }
    }

    private fun secondaryNavigationItems(): List<FavoriteNavigationItem> {
        return when (resolvedNavigationMode()) {
            FavoriteNavigationMode.TYPE_ONLY,
            FavoriteNavigationMode.CATEGORY_ONLY -> emptyList()
            FavoriteNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY -> availableCategoryItems()
            FavoriteNavigationMode.CATEGORY_PRIMARY_TYPE_SECONDARY -> ui.defaultTypeNavigationItems()
        }
    }

    private fun currentPrimarySelectionId(): String? {
        return when (resolvedNavigationMode()) {
            FavoriteNavigationMode.CATEGORY_PRIMARY_TYPE_SECONDARY,
            FavoriteNavigationMode.CATEGORY_ONLY -> selectedCategoryId
            FavoriteNavigationMode.TYPE_ONLY,
            FavoriteNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY -> selectedType.name
        }
    }

    private fun currentSecondarySelectionId(): String? {
        return when (resolvedNavigationMode()) {
            FavoriteNavigationMode.TYPE_ONLY,
            FavoriteNavigationMode.CATEGORY_ONLY -> null
            FavoriteNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY -> selectedCategoryId
            FavoriteNavigationMode.CATEGORY_PRIMARY_TYPE_SECONDARY -> selectedType.name
        }
    }

    private fun availableCategoryItems(): List<FavoriteNavigationItem> {
        val configured = LinkedHashMap<String, FavoriteNavigationItem>()
        navigationConfig.categories.forEach { item ->
            if (item.id.isNotBlank() && item.label.isNotBlank()) {
                configured.putIfAbsent(item.id, item)
            }
        }
        records.forEach { record ->
            val categoryId = record.categoryId?.trim().orEmpty()
            val categoryName = record.categoryName?.trim().orEmpty()
            if (categoryId.isNotBlank() && categoryName.isNotBlank()) {
                configured.putIfAbsent(categoryId, FavoriteNavigationItem(categoryId, categoryName))
            }
        }
        return configured.values.toList()
    }

    private fun ensureValidCategorySelection() {
        if (resolvedNavigationMode() == FavoriteNavigationMode.CATEGORY_ONLY) {
            selectedType = FavoriteCategory.RECENT
            browsingFolder = null
        }
        val categories = availableCategoryItems()
        selectedCategoryId = when {
            categories.isEmpty() -> null
            categories.any { it.id == selectedCategoryId } -> selectedCategoryId
            else -> categories.first().id
        }
    }

    private fun parseTypeNavigationId(itemId: String): FavoriteCategory? {
        return FavoriteCategory.values().firstOrNull { it.name == itemId }
    }

    private fun FavoriteRecord.matchesSearchKeyword(keyword: String): Boolean {
        val normalizedTags = tags.any { tag -> tag.contains(keyword, ignoreCase = true) }
        if (normalizedTags) return true
        return parts.any { part ->
            when (part) {
                is FavoritePart.Text -> part.text.contains(keyword, ignoreCase = true)
                is FavoritePart.Attachment -> part.displayName.contains(keyword, ignoreCase = true)
            }
        }
    }

    private fun enterSearchMode() {
        if (searchModeActive) {
            ui.focusSearchInput()
            return
        }
        if (!isLoggedIn()) {
            showLoginRequiredState()
            return
        }
        collapseFloatingActionMenu()
        preSearchType = selectedType
        preSearchCategoryId = selectedCategoryId
        searchModeActive = true
        searchKeyword = ""
        browsingFolder = null
        visibleCount = PAGE_SIZE
        ui.setSearchMode(true)
        ui.setListTitle(context.getString(org.fcitx.fcitx5.android.R.string.favorite_search_title))
        ui.clearSearchInputText()
        bindItems(scrollToTop = true)
        ui.searchInputView.post {
            ui.focusSearchInput()
            onKeyboardVisibilityChanged(true)
        }
    }

    private fun exitSearchMode() {
        if (!searchModeActive) return
        searchModeActive = false
        searchKeyword = ""
        cancelPendingSearchFilter()
        ui.setSearchMode(false)
        ui.setListTitle(context.getString(org.fcitx.fcitx5.android.R.string.favorite_panel_title))
        ui.clearSearchInputText()
        selectedType = preSearchType
        selectedCategoryId = preSearchCategoryId
        browsingFolder = null
        visibleCount = PAGE_SIZE
        bindItems(scrollToTop = true)
        onKeyboardVisibilityChanged(false)
        ui.root.requestFocus()
    }

    private fun cancelPendingSearchFilter() {
        searchDebounceRunnable?.let(searchDebounceHandler::removeCallbacks)
        searchDebounceRunnable = null
    }

    private fun scheduleSearchFilter(keyword: String) {
        cancelPendingSearchFilter()
        val runnable = Runnable {
            searchDebounceRunnable = null
            if (!searchModeActive) return@Runnable
            searchKeyword = keyword
            visibleCount = PAGE_SIZE
            bindItems(scrollToTop = true)
        }
        searchDebounceRunnable = runnable
        searchDebounceHandler.postDelayed(runnable, SEARCH_DEBOUNCE_MS)
    }

    private fun flushSearchFilter() {
        if (!searchModeActive) return
        cancelPendingSearchFilter()
        searchKeyword = ui.searchInputView.text?.toString().orEmpty()
        visibleCount = PAGE_SIZE
        bindItems(scrollToTop = true)
        ui.searchInputView.clearFocus()
        onKeyboardVisibilityChanged(false)
    }

    private fun currentRecordItems(): List<FavoriteRecord> {
        val activeFolder = browsingFolder
        return if (activeFolder != null) {
            folderRecords(activeFolder)
        } else {
            filteredItems()
        }
    }

    private fun folderRecords(folder: FavoriteFolderItem): List<FavoriteRecord> {
        val sorted = records.sortedByDescending { it.updatedAt }
        if (sorted.isEmpty()) return emptyList()
        val folderIndex = folderItems.indexOfFirst { it.id == folder.id }.coerceAtLeast(0)
        val partitioned = sorted.filterIndexed { index, _ -> index % folderItems.size == folderIndex }
        return if (partitioned.isNotEmpty()) partitioned else sorted
    }

    private fun openFolder(folder: FavoriteFolderItem) {
        browsingFolder = folder
        visibleCount = PAGE_SIZE
        bindItems(scrollToTop = true)
    }

    private fun handlePreviewClick(item: FavoriteRecord) {
        // #region debug-point flash-preview-blank-click
        Log.d(
            "FlashPreviewDebug",
            "handlePreviewClick record=${item.id} parts=${item.parts.size} attachments=${item.parts.count { it is FavoritePart.Attachment }} texts=${item.parts.count { it is FavoritePart.Text }}"
        )
        // #endregion
        if (searchModeActive) {
            sendFavoriteRecord(item)
            return
        }
        if (shouldSendRecordDirectly(item)) {
            Log.d(
                "FlashPreviewDebug",
                "handlePreviewClick directSend record=${item.id} hostPackage=${launchHostPackageName.orEmpty()}"
            )
            sendFavoriteRecord(item)
            return
        }
        collapseFloatingActionMenu()
        openMaterialGroupPreview(item)
    }

    private fun shouldSendRecordDirectly(item: FavoriteRecord): Boolean {
        val hostPackageName = launchHostPackageName?.trim().orEmpty()
        if (!hostPackageName.equals("com.taobao.idlefish", ignoreCase = true)) {
            return false
        }
        if (item.parts.isEmpty()) {
            return false
        }
        return item.parts.all { it is FavoritePart.Text }
    }

    private fun openMaterialGroupPreview(item: FavoriteRecord) {
        val previewView = FavoriteMaterialGroupPreviewView(
            context = context,
            record = item,
            userAvatarProvider = userAvatarProvider,
            defaultAvatarProvider = defaultAvatarProvider,
            onAttachmentClick = { attachment ->
                onPreviewAttachment(attachment) {
                    showMaterialGroupPreviewPage()
                }
            },
            onSendClick = { updatedRecord -> sendFavoriteRecord(updatedRecord) },
            onDeleteClick = { deleteFavoriteRecordWithLoading() },
            onEditPartClick = { part -> openPreviewPartEditor(part) },
        )
        transientPreviewView = previewView
        transientPreviewPersistedRecord = item
        showMaterialGroupPreviewPage(playEnterAnimation = true)
    }

    private fun showMaterialGroupPreviewPage(playEnterAnimation: Boolean = false) {
        val previewView = transientPreviewView ?: return
        transientPreviewPageState = TransientPreviewPageState.MAIN
        showTransientComposerPage(
            title = currentMaterialPreviewTitle(previewView.isEditMode()),
            contentView = previewView,
            onBack = ::hideTransientPreviewToList,
            showAddAction = true,
        )
        refreshMaterialGroupPreviewAction()
        if (playEnterAnimation && !previewView.isEditMode()) {
            ui.playPreviewHeaderEntranceAnimation()
            previewView.playPreviewEnterAnimation()
        }
    }

    private fun refreshMaterialGroupPreviewAction() {
        val previewView = transientPreviewView ?: return
        val remoteManaged = transientPreviewPersistedRecord?.isRemoteManaged() == true
        if (remoteManaged && previewView.isEditMode()) {
            previewView.setEditMode(false)
        }
        val editing = previewView.isEditMode()
        ui.configureComposerHeader(
            title = currentMaterialPreviewTitle(editing),
            showAddAction = !remoteManaged,
            showSaveAction = editing && !remoteManaged,
        )
        if (!remoteManaged) {
            ui.configureComposerAddAction(
                iconRes = if (editing) {
                    org.fcitx.fcitx5.android.R.drawable.ic_favorite_preview_group_delete
                } else {
                    org.fcitx.fcitx5.android.R.drawable.ic_favorite_preview_edit
                },
                tintColor = null,
                iconScale = PREVIEW_HEADER_ACTION_ICON_SCALE,
            ) {
                if (editing) {
                    deleteFavoriteRecordWithLoading()
                } else {
                    previewView.setEditMode(true)
                    refreshMaterialGroupPreviewAction()
                    ui.playPreviewHeaderEntranceAnimation()
                }
            }
        }
        if (editing && !remoteManaged) {
            ui.configureComposerSaveAction(
                iconRes = org.fcitx.fcitx5.android.R.drawable.ic_favorite_preview_submit_success,
                tintColor = null,
                iconScale = PREVIEW_HEADER_ACTION_ICON_SCALE,
            ) {
                submitPreviewEdits()
            }
        }
        ui.addActionButton.contentDescription = when {
            remoteManaged -> "企业素材"
            editing -> "删除素材"
            else -> "编辑"
        }
        ui.composerSaveButton.contentDescription = if (remoteManaged) "企业素材只读" else "保存"
        syncPrimaryActions(composerVisible = true)
    }

    private fun currentMaterialPreviewTitle(editing: Boolean): String {
        return context.getString(
            if (editing) {
                org.fcitx.fcitx5.android.R.string.favorite_material_group_edit_title
            } else {
                org.fcitx.fcitx5.android.R.string.favorite_material_group_preview_title
            }
        )
    }

    private fun submitPreviewEdits() {
        val previewView = transientPreviewView ?: return
        if (transientPreviewPersistedRecord?.isRemoteManaged() == true) {
            context.toast("企业下发素材当前为只读，请在服务端修改")
            previewView.setEditMode(false)
            refreshMaterialGroupPreviewAction()
            return
        }
        val editedRecord = previewView.currentRecordSnapshot()
        if (editedRecord.parts.isEmpty()) {
            context.toast("素材组已空，请点击右上角删除素材")
            return
        }
        val saved = storage.saveFavorite(editedRecord)
        storage.cleanupUnusedAssets(
            saved.id,
            saved.parts.filterIsInstance<FavoritePart.Attachment>().map { it.storedPath }.toSet()
        )
        transientPreviewPersistedRecord = saved
        previewView.updateWorkingRecord(saved)
        previewView.setEditMode(false)
        refreshMaterialGroupPreviewAction()
        loadFavorites()
        context.toast("素材已保存")
    }

    private fun deleteFavoriteRecordWithLoading() {
        val previewView = transientPreviewView ?: return
        val record = transientPreviewPersistedRecord ?: previewView.currentRecordSnapshot()
        if (record.isRemoteManaged()) {
            context.toast("企业下发素材当前为只读，请在服务端删除")
            return
        }
        previewView.setGroupDeleting(true)
        ui.addActionButton.isEnabled = false
        ui.composerSaveButton.isEnabled = false
        MaterialHubFavoriteWorker.scope.launch {
            delay(2000L)
            val deleted = storage.deleteFavorite(record.id)
            previewView.setGroupDeleting(false)
            ui.addActionButton.isEnabled = true
            ui.composerSaveButton.isEnabled = true
            if (!deleted) {
                context.toast("删除素材失败，请重试")
                refreshMaterialGroupPreviewAction()
                return@launch
            }
            context.toast("素材已删除")
            clearTransientPreviewState()
            transientComposerBackHandler = null
            ui.hideComposerTransientContent()
            loadFavorites()
            showList(animated = false)
        }
    }

    private fun openPreviewPartEditor(part: FavoritePart) {
        when (part) {
            is FavoritePart.Text -> showPreviewTextEditor(part)
            is FavoritePart.Attachment -> showPreviewAttachmentPicker(part)
        }
    }

    private fun showPreviewTextEditor(part: FavoritePart.Text) {
        transientPreviewPageState = TransientPreviewPageState.TEXT_EDITOR
        var editingText = part.text
        val holder = ui.buildTextEditorView(
            text = part.text,
            composingText = "",
            cursorVisible = true,
            onRequestKeyboard = {
                onKeyboardVisibilityChanged(true)
                ui.setComposerKeyboardVisible(true)
            },
            onTextChanged = { updated ->
                editingText = updated
            },
        )
        val submitAction = submitAction@{
            val normalized = editingText.trim()
            if (normalized.isBlank()) {
                context.toast("请先输入文本内容")
                return@submitAction
            }
            transientPreviewView?.updateTextPart(part.id, normalized)
            onKeyboardVisibilityChanged(false)
            ui.setComposerKeyboardVisible(false)
            showMaterialGroupPreviewPage()
        }
        showTransientComposerPage(
            title = context.getString(org.fcitx.fcitx5.android.R.string.favorite_picker_text),
            contentView = holder.root,
            onBack = {
                onKeyboardVisibilityChanged(false)
                ui.setComposerKeyboardVisible(false)
                showMaterialGroupPreviewPage()
            },
            showSaveAction = true,
            onSaveAction = submitAction,
        )
        ui.configureComposerSaveAction(
            iconRes = org.fcitx.fcitx5.android.R.drawable.ic_favorite_preview_submit_success,
            tintColor = null,
            iconScale = PREVIEW_HEADER_ACTION_ICON_SCALE,
            onClick = submitAction,
        )
        ui.layoutTextEditorViewport(holder)
        ui.bindTextEditorView(holder, part.text, "", true)
        holder.contentView.post {
            holder.contentView.requestFocus()
            holder.contentView.setSelection(holder.contentView.text?.length ?: 0)
            onKeyboardVisibilityChanged(true)
            ui.setComposerKeyboardVisible(true)
            ui.scrollTextEditorCursorIntoView(holder)
        }
    }

    private fun showPreviewAttachmentPicker(part: FavoritePart.Attachment) {
        val pickerType = when (part.attachmentType) {
            FavoriteAttachmentType.DOCUMENT -> FavoritePickerType.FILE
            FavoriteAttachmentType.VIDEO -> FavoritePickerType.VIDEO
            else -> FavoritePickerType.IMAGE
        }
        ensurePickerPermission(pickerType) {
            showPreviewAttachmentPickerWithPermission(part, pickerType)
        }
    }

    private fun showPreviewAttachmentPickerWithPermission(
        part: FavoritePart.Attachment,
        pickerType: FavoritePickerType,
    ) {
        transientPreviewPageState = TransientPreviewPageState.PICKER
        val contentHost = FrameLayout(context)
        var selectedItem: FavoriteEmbeddedPickerItem? = null
        var selectedFileCategory = FavoriteFileCategory.ALL
        var loading = transientPickerItemsCache[pickerType] == null
        var items: List<FavoriteEmbeddedPickerItem> = emptyList()
        var pickerScrollView: ScrollView? = null
        var pageShown = false
        var importing = false
        val importOverlay = buildTransientImportOverlay()
        var currentPickerContentView: View? = null

        fun selectedOrder(item: FavoriteEmbeddedPickerItem): Int {
            return if (selectedItem?.uri == item.uri) 1 else 0
        }

        var toggleSelection: ((FavoriteEmbeddedPickerItem) -> Unit)? = null
        var reopenPickerPage: (() -> Unit)? = null

        fun syncActionState() {
            if (!pageShown) return
            ui.updateComposerTextActionState(
                actionText = "替换",
                enabled = selectedItem != null && !importing,
            )
            importOverlay.visibility = if (importing) View.VISIBLE else View.GONE
        }

        fun renderPicker() {
            val totalCount = transientPickerTotalCount(pickerType, items, selectedFileCategory)
            ensureTransientPickerVisibleCount(pickerType, totalCount)
            val visibleItems = visibleTransientPickerItems(pickerType, items, selectedFileCategory)
            val content = when {
                loading -> ui.buildPickerLoadingView("正在加载${pickerTitle(pickerType)}")
                items.isEmpty() && pickerType != FavoritePickerType.FILE ->
                    ui.buildPickerEmptyStateView("当前没有可用的${pickerTitle(pickerType)}")
                else -> ui.buildEmbeddedPickerView(
                    type = pickerType,
                    items = visibleItems,
                    selectedFileCategory = selectedFileCategory,
                    onSelectFileCategory = { category ->
                        selectedFileCategory = category
                        transientPickerScrollY[pickerType] = 0
                        resetTransientPickerVisibleCount(
                            pickerType,
                            transientPickerTotalCount(pickerType, items, selectedFileCategory)
                        )
                        renderPicker()
                    },
                    onPick = {},
                    onToggleMediaSelection = { item ->
                        selectedItem = if (selectedItem?.uri == item.uri) null else item
                        if (pickerType == FavoritePickerType.IMAGE || pickerType == FavoritePickerType.VIDEO) {
                            currentPickerContentView?.let { contentView ->
                                ui.updateEmbeddedMediaPickerSelection(contentView, ::selectedOrder)
                                syncActionState()
                            } ?: renderPicker()
                        } else {
                            renderPicker()
                        }
                    },
                    selectedMediaOrder = ::selectedOrder,
                    onPreviewFile = { item ->
                        previewTransientPickerFileItem(
                            item = item,
                            pickerType = pickerType,
                            currentScrollY = pickerScrollView?.scrollY ?: transientPickerScrollY[pickerType] ?: 0,
                            onBackToPicker = { reopenPickerPage?.invoke() },
                        )
                    },
                    onToggleFileSelection = { item ->
                        selectedItem = if (selectedItem?.uri == item.uri) null else item
                        renderPicker()
                    },
                    selectedFileOrder = ::selectedOrder,
                )
            }
            val wrappedContent = if (!loading) {
                ScrollView(context).apply {
                    isFillViewport = true
                    overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
                    isVerticalScrollBarEnabled = false
                    addView(
                        content,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                    setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        transientPickerScrollY[pickerType] = scrollY
                        if (loading) return@setOnScrollChangeListener
                        val currentVisible = transientPickerVisibleCount[pickerType] ?: return@setOnScrollChangeListener
                        if (currentVisible >= totalCount) return@setOnScrollChangeListener
                        val contentHeight = getChildAt(0)?.height ?: 0
                        val viewportHeight = height
                        if (contentHeight <= 0 || viewportHeight <= 0) return@setOnScrollChangeListener
                        val preloadThreshold = context.dp(240)
                        val reachedBoundary = scrollY + viewportHeight >= contentHeight - preloadThreshold
                        if (!reachedBoundary) return@setOnScrollChangeListener
                        transientPickerVisibleCount[pickerType] = (
                            currentVisible + transientPickerLoadMoreBatchSize(pickerType)
                        ).coerceAtMost(totalCount)
                        renderPicker()
                    }
                }.also { pickerScrollView = it }
            } else {
                content
            }
            currentPickerContentView = content
            contentHost.removeAllViews()
            contentHost.addView(
                wrappedContent,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            contentHost.addView(
                importOverlay,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            if (!pageShown) {
                showTransientComposerPage(
                    title = pickerTitle(pickerType),
                    contentView = contentHost,
                    onBack = ::showMaterialGroupPreviewPage,
                    actionText = "替换",
                    actionTextEnabled = selectedItem != null && !importing,
                    onTextAction = {
                        val target = selectedItem ?: return@showTransientComposerPage
                        val recordId = transientPreviewPersistedRecord?.id
                            ?: transientPreviewView?.currentRecordSnapshot()?.id
                            ?: return@showTransientComposerPage
                        importing = true
                        syncActionState()
                        Thread {
                            val replacement = storage.createAttachmentFromUri(recordId, target.uri, pickerType)
                            contentHost.post {
                                importing = false
                                if (replacement == null) {
                                    syncActionState()
                                    context.toast("替换素材失败，请重试")
                                    return@post
                                }
                                transientPreviewView?.replacePart(part.id, replacement.copy(id = part.id))
                                showMaterialGroupPreviewPage()
                            }
                        }.start()
                    },
                )
                pageShown = true
            } else {
                syncActionState()
            }
            pickerScrollView?.post {
                pickerScrollView.scrollTo(0, transientPickerScrollY[pickerType] ?: 0)
            }
        }

        reopenPickerPage = {
            pageShown = false
            renderPicker()
        }

        renderPicker()
        transientPickerItemsCache[pickerType]?.let { cached ->
            items = cached
            loading = false
            ensureTransientPickerVisibleCount(
                pickerType,
                transientPickerTotalCount(pickerType, cached, selectedFileCategory)
            )
            renderPicker()
            return
        }
        Thread {
            val loaded = embeddedPickerStore.load(pickerType)
            contentHost.post {
                loading = false
                items = loaded
                transientPickerItemsCache[pickerType] = loaded
                ensureTransientPickerVisibleCount(
                    pickerType,
                    transientPickerTotalCount(pickerType, loaded, selectedFileCategory)
                )
                renderPicker()
            }
        }.start()
    }

    private fun showQuickAddTextEditor() {
        transientPreviewPageState = TransientPreviewPageState.TEXT_EDITOR
        var editingText = ""
        val holder = ui.buildTextEditorView(
            text = "",
            composingText = "",
            cursorVisible = true,
            onRequestKeyboard = {
                onKeyboardVisibilityChanged(true)
                ui.setComposerKeyboardVisible(true)
            },
            onTextChanged = { updated ->
                editingText = updated
            },
        )
        val backAction = {
            onKeyboardVisibilityChanged(false)
            ui.setComposerKeyboardVisible(false)
            if (transientPreviewView != null) {
                showMaterialGroupPreviewPage()
            } else {
                showList(animated = false)
            }
        }
        val submitAction = submitAction@{
            val normalized = editingText.trim()
            if (normalized.isBlank()) {
                context.toast("请先输入文本内容")
                return@submitAction
            }
            Log.d(
                "FlashPreviewDebug",
                "quickAddText submit length=${normalized.length} existingPreview=${transientPreviewView != null}"
            )
            onKeyboardVisibilityChanged(false)
            ui.setComposerKeyboardVisible(false)
            persistQuickAddedParts(listOf(FavoritePart.Text(text = normalized)))
        }
        showTransientComposerPage(
            title = context.getString(org.fcitx.fcitx5.android.R.string.favorite_picker_text),
            contentView = holder.root,
            onBack = backAction,
            showSaveAction = true,
            onSaveAction = submitAction,
        )
        ui.configureComposerSaveAction(
            iconRes = org.fcitx.fcitx5.android.R.drawable.ic_favorite_preview_submit_success,
            tintColor = null,
            iconScale = PREVIEW_HEADER_ACTION_ICON_SCALE,
            onClick = submitAction,
        )
        ui.layoutTextEditorViewport(holder)
        ui.bindTextEditorView(holder, "", "", true)
        holder.contentView.post {
            holder.contentView.requestFocus()
            holder.contentView.setSelection(holder.contentView.text?.length ?: 0)
            onKeyboardVisibilityChanged(true)
            ui.setComposerKeyboardVisible(true)
            ui.scrollTextEditorCursorIntoView(holder)
        }
    }

    private fun showQuickAddAttachmentPicker(pickerType: FavoritePickerType) {
        transientPreviewPageState = TransientPreviewPageState.PICKER
        val contentHost = FrameLayout(context)
        val selectedItems = mutableListOf<FavoriteEmbeddedPickerItem>()
        var selectedFileCategory = FavoriteFileCategory.ALL
        var loading = transientPickerItemsCache[pickerType] == null
        var items: List<FavoriteEmbeddedPickerItem> = emptyList()
        var pickerScrollView: ScrollView? = null
        var pageShown = false
        var importing = false
        val importOverlay = buildTransientImportOverlay()
        var currentPickerContentView: View? = null

        fun selectedOrder(item: FavoriteEmbeddedPickerItem): Int {
            return selectedItems.indexOfFirst { it.uri == item.uri }
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: 0
        }

        var toggleSelection: ((FavoriteEmbeddedPickerItem) -> Unit)? = null
        var reopenPickerPage: (() -> Unit)? = null

        fun syncActionState() {
            if (!pageShown) return
            ui.updateComposerTextActionState(
                actionText = "添加",
                enabled = selectedItems.isNotEmpty() && !importing,
            )
            importOverlay.visibility = if (importing) View.VISIBLE else View.GONE
        }

        fun renderPicker() {
            val totalCount = transientPickerTotalCount(pickerType, items, selectedFileCategory)
            ensureTransientPickerVisibleCount(pickerType, totalCount)
            val visibleItems = visibleTransientPickerItems(pickerType, items, selectedFileCategory)
            val content = when {
                loading -> ui.buildPickerLoadingView("正在加载${pickerTitle(pickerType)}")
                items.isEmpty() && pickerType != FavoritePickerType.FILE ->
                    ui.buildPickerEmptyStateView("当前没有可用的${pickerTitle(pickerType)}")
                else -> ui.buildEmbeddedPickerView(
                    type = pickerType,
                    items = visibleItems,
                    selectedFileCategory = selectedFileCategory,
                    onSelectFileCategory = { category ->
                        selectedFileCategory = category
                        transientPickerScrollY[pickerType] = 0
                        resetTransientPickerVisibleCount(
                            pickerType,
                            transientPickerTotalCount(pickerType, items, selectedFileCategory)
                        )
                        renderPicker()
                    },
                    onPick = {},
                    onToggleMediaSelection = { item -> toggleSelection?.invoke(item) },
                    selectedMediaOrder = ::selectedOrder,
                    onPreviewFile = { item ->
                        Log.d(
                            "FlashPreviewDebug",
                            "quickAddPreviewFileSelect displayName=${item.displayName} category=${item.fileCategory}"
                        )
                        previewTransientPickerFileItem(
                            item = item,
                            pickerType = pickerType,
                            currentScrollY = pickerScrollView?.scrollY ?: transientPickerScrollY[pickerType] ?: 0,
                            onBackToPicker = { reopenPickerPage?.invoke() },
                        )
                    },
                    onToggleFileSelection = { item -> toggleSelection?.invoke(item) },
                    selectedFileOrder = ::selectedOrder,
                )
            }
            val wrappedContent = if (!loading) {
                ScrollView(context).apply {
                    isFillViewport = true
                    overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
                    isVerticalScrollBarEnabled = false
                    addView(
                        content,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                    setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        transientPickerScrollY[pickerType] = scrollY
                        if (loading) return@setOnScrollChangeListener
                        val currentVisible = transientPickerVisibleCount[pickerType] ?: return@setOnScrollChangeListener
                        if (currentVisible >= totalCount) return@setOnScrollChangeListener
                        val contentHeight = getChildAt(0)?.height ?: 0
                        val viewportHeight = height
                        if (contentHeight <= 0 || viewportHeight <= 0) return@setOnScrollChangeListener
                        val preloadThreshold = context.dp(240)
                        val reachedBoundary = scrollY + viewportHeight >= contentHeight - preloadThreshold
                        if (!reachedBoundary) return@setOnScrollChangeListener
                        transientPickerVisibleCount[pickerType] = (
                            currentVisible + transientPickerLoadMoreBatchSize(pickerType)
                        ).coerceAtMost(totalCount)
                        renderPicker()
                    }
                }.also { pickerScrollView = it }
            } else {
                content
            }
            currentPickerContentView = content
            contentHost.removeAllViews()
            contentHost.addView(
                wrappedContent,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            contentHost.addView(
                importOverlay,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            if (!pageShown) {
                showTransientComposerPage(
                    title = pickerTitle(pickerType),
                    contentView = contentHost,
                    onBack = {
                        if (transientPreviewView != null) {
                            showMaterialGroupPreviewPage()
                        } else {
                            showList(animated = false)
                        }
                    },
                    actionText = "添加",
                    actionTextEnabled = selectedItems.isNotEmpty() && !importing,
                    onTextAction = {
                        val targets = selectedItems.toList()
                        if (targets.isEmpty()) return@showTransientComposerPage
                        val existingRecord = transientPreviewPersistedRecord ?: transientPreviewView?.currentRecordSnapshot()
                        val recordId = existingRecord?.id ?: UUID.randomUUID().toString()
                        val createdAt = existingRecord?.createdAt ?: System.currentTimeMillis()
                        Log.d(
                            "FlashPreviewDebug",
                            "quickAddAttachment start type=$pickerType selected=${targets.size} recordId=$recordId existingPreview=${transientPreviewView != null}"
                        )
                        importing = true
                        syncActionState()
                        Thread {
                            val attachments = targets.mapNotNull { item ->
                                storage.createAttachmentFromUri(recordId, item.uri, pickerType)
                            }
                            contentHost.post {
                                importing = false
                                Log.d(
                                    "FlashPreviewDebug",
                                    "quickAddAttachment created type=$pickerType requested=${targets.size} actual=${attachments.size} recordId=$recordId"
                                )
                                if (attachments.isEmpty()) {
                                    syncActionState()
                                    context.toast("素材导入失败，请重试")
                                    return@post
                                }
                                persistQuickAddedParts(
                                    newParts = attachments,
                                    recordId = recordId,
                                    createdAt = createdAt,
                                )
                            }
                        }.start()
                    },
                )
                pageShown = true
            } else {
                syncActionState()
            }
            pickerScrollView?.post {
                pickerScrollView.scrollTo(0, transientPickerScrollY[pickerType] ?: 0)
            }
        }

        toggleSelection = { item: FavoriteEmbeddedPickerItem ->
            transientPickerScrollY[pickerType] = pickerScrollView?.scrollY ?: transientPickerScrollY[pickerType] ?: 0
            val existingIndex = selectedItems.indexOfFirst { it.uri == item.uri }
            if (existingIndex >= 0) {
                selectedItems.removeAt(existingIndex)
            } else {
                selectedItems += item
            }
            if (pickerType == FavoritePickerType.IMAGE || pickerType == FavoritePickerType.VIDEO) {
                currentPickerContentView?.let { contentView ->
                    ui.updateEmbeddedMediaPickerSelection(contentView, ::selectedOrder)
                    syncActionState()
                } ?: renderPicker()
            } else {
                renderPicker()
            }
        }

        reopenPickerPage = {
            pageShown = false
            renderPicker()
        }

        renderPicker()
        transientPickerItemsCache[pickerType]?.let { cached ->
            items = cached
            loading = false
            ensureTransientPickerVisibleCount(
                pickerType,
                transientPickerTotalCount(pickerType, cached, selectedFileCategory)
            )
            renderPicker()
            return
        }
        Thread {
            val loaded = embeddedPickerStore.load(pickerType)
            contentHost.post {
                loading = false
                items = loaded
                transientPickerItemsCache[pickerType] = loaded
                Log.d(
                    "FlashPreviewDebug",
                    "quickAddAttachment pickerLoaded type=$pickerType count=${loaded.size}"
                )
                ensureTransientPickerVisibleCount(
                    pickerType,
                    transientPickerTotalCount(pickerType, loaded, selectedFileCategory)
                )
                renderPicker()
            }
        }.start()
    }

    private fun persistQuickAddedParts(
        newParts: List<FavoritePart>,
        recordId: String = transientPreviewPersistedRecord?.id
            ?: transientPreviewView?.currentRecordSnapshot()?.id
            ?: UUID.randomUUID().toString(),
        createdAt: Long = transientPreviewPersistedRecord?.createdAt
            ?: transientPreviewView?.currentRecordSnapshot()?.createdAt
            ?: System.currentTimeMillis(),
    ) {
        val existingRecord = transientPreviewPersistedRecord ?: transientPreviewView?.currentRecordSnapshot()
        val targetRecord = if (existingRecord != null) {
            existingRecord.copy(parts = existingRecord.parts + newParts)
        } else {
            val categoryAssignment = currentCategoryAssignment()
            FavoriteRecord(
                id = recordId,
                createdAt = createdAt,
                updatedAt = createdAt,
                parts = newParts,
                categoryId = categoryAssignment?.first,
                categoryName = categoryAssignment?.second,
            )
        }
        val saved = storage.saveFavorite(targetRecord)
        Log.d(
            "FlashPreviewDebug",
            "persistQuickAddedParts record=${saved.id} parts=${saved.parts.size} attachments=${saved.parts.count { it is FavoritePart.Attachment }} texts=${saved.parts.count { it is FavoritePart.Text }}"
        )
        transientPreviewPersistedRecord = saved
        val previewView = transientPreviewView
        if (previewView == null) {
            openMaterialGroupPreview(saved)
        } else {
            val wasEditing = previewView.isEditMode()
            previewView.updateWorkingRecord(saved)
            previewView.setEditMode(wasEditing)
            showMaterialGroupPreviewPage()
        }
        loadFavorites()
    }

    private fun pickerTitle(type: FavoritePickerType): String {
        return when (type) {
            FavoritePickerType.TEXT -> context.getString(org.fcitx.fcitx5.android.R.string.favorite_picker_text)
            FavoritePickerType.IMAGE -> context.getString(org.fcitx.fcitx5.android.R.string.favorite_picker_image)
            FavoritePickerType.FILE -> context.getString(org.fcitx.fcitx5.android.R.string.favorite_picker_file)
            FavoritePickerType.VIDEO -> context.getString(org.fcitx.fcitx5.android.R.string.favorite_picker_video)
        }
    }

    private fun sendFavoriteRecord(item: FavoriteRecord) {
        if (sendingInProgress) {
            context.toast("正在发送素材，请稍候")
            return
        }
        if (item.parts.isEmpty()) {
            context.toast("当前素材为空")
            return
        }
        sendingInProgress = true
        // #region debug-point B:idlefish-send-record-entry
        reportIdlefishKeyboardFocusDebug(
            hypothesisId = "H1",
            location = "MaterialHubFavoriteController.sendFavoriteRecord",
            msg = "material hub close requested before sending favorite record",
            data =
                "hostPackage=${launchHostPackageName.orEmpty()} " +
                    "partCount=${item.parts.size} " +
                    "textCount=${item.parts.count { it is FavoritePart.Text }} " +
                    "attachmentCount=${item.parts.count { it is FavoritePart.Attachment }}",
        )
        // #endregion
        val restoreBaseline = captureRestoreBaselineBeforeClose()
        onRequestClose {
            MaterialHubFavoriteWorker.scope.launch {
                try {
                    delay(AFTER_CLOSE_RESTORE_STABILIZE_MS)
                    val restored = restoreHostInputBeforeSend(restoreBaseline)
                    if (!restored) {
                        reportMaterialRestoreDiagnostic(
                            name = "restore_failed_toast",
                            level = "WARN",
                            message = "show restore failed toast before sending material",
                            attributes = restoreBaseline.toAttributeMap() + mapOf(
                                "partCount" to item.parts.size.toString(),
                                "textCount" to item.parts.count { it is FavoritePart.Text }.toString(),
                                "attachmentCount" to item.parts.count { it is FavoritePart.Attachment }.toString(),
                            ),
                        )
                        context.toast("未恢复到聊天输入框，请重新点击输入框后再试")
                        return@launch
                    }
                    val effectiveAutoSendEnabled = resolveEffectiveAutoSendMode(showFailureToast = true)
                    sendFavoriteParts(
                        parts = item.parts,
                        automationEnabled = effectiveAutoSendEnabled,
                    )
                } finally {
                    sendingInProgress = false
                }
            }
        }
    }

    private fun captureRestoreBaselineBeforeClose(): FavoriteHostInputRestoreBaseline {
        val hostPackageName = launchHostPackageName?.trim().orEmpty()
        val targetPackageName = FavoriteBridge.currentInputTargetPackage()
        val startCount = FavoriteBridge.currentInputViewStartCount()
        val requestedShowKeyboard = FavoriteBridge.requestShowKeyboard()
        val baseline = FavoriteHostInputRestoreBaseline(
            hostPackageName = hostPackageName,
            targetPackageName = targetPackageName,
            startCount = startCount,
            requestedShowKeyboard = requestedShowKeyboard,
            createdAtMs = System.currentTimeMillis(),
        )
        reportMaterialRestoreDiagnostic(
            name = "restore_baseline_before_close",
            level = "INFO",
            message = "captured host input restore baseline before material drawer close",
            attributes = baseline.toAttributeMap(),
        )
        return baseline
    }

    private fun deleteFavoriteRecord(item: FavoriteRecord) {
        val deleted = storage.deleteFavorite(item.id)
        if (!deleted) {
            context.toast("删除素材失败，请重试")
            return
        }
        context.toast("素材已删除")
        clearTransientPreviewState()
        transientComposerBackHandler = null
        ui.hideComposerTransientContent()
        loadFavorites()
        showList(animated = false)
    }

    private fun showLoginRequiredState() {
        collapseFloatingActionMenu()
        browsingFolder = null
        ui.setListManageActionVisible(false)
        ui.setEmptyStateContent(buildLoginRequiredView())
        ui.renderFolderBrowsingState(browsingFolder)
        ui.showEmptyState(true)
        ui.showListMode(animated = false)
        syncPrimaryActions(composerVisible = true)
    }

    private fun buildRestrictedStateView(
        message: String,
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            addView(
                TextView(context).apply {
                    text = message
                    setTextColor(Color.parseColor("#111827"))
                    textSize = 18f
                    typeface = Typeface.defaultFromStyle(Typeface.BOLD)
                    gravity = android.view.Gravity.CENTER
                    setLineSpacing(0f, 1.25f)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun buildLoginRequiredView(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            addView(
                TextView(context).apply {
                    text = context.getString(org.fcitx.fcitx5.android.R.string.favorite_login_required_tip)
                    setTextColor(Color.parseColor("#4B5563"))
                    textSize = 15f
                    gravity = android.view.Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                TextView(context).apply {
                    text = context.getString(org.fcitx.fcitx5.android.R.string.favorite_login_action)
                    gravity = android.view.Gravity.CENTER
                    textSize = 15f
                    typeface = Typeface.defaultFromStyle(Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = context.dp(22).toFloat()
                        setColor(Color.parseColor("#051EFF"))
                    }
                    isClickable = true
                    isFocusable = true
                    setPadding(context.dp(28), context.dp(12), context.dp(28), context.dp(12))
                    setOnClickListener { onLoginRequested() }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = context.dp(18)
                }
            )
        }
    }

    private suspend fun sendFavoriteParts(
        parts: List<FavoritePart>,
        automationEnabled: Boolean,
    ): Boolean {
        when (FavoriteBridge.tryHostShare(launchHostPackageName.orEmpty(), parts)) {
            FavoriteHostShareDispatchResult.DISPATCHED -> return true
            FavoriteHostShareDispatchResult.FAILED -> {
                context.toast("未能打开当前宿主的分享页面，请重试")
                return false
            }
            FavoriteHostShareDispatchResult.NOT_HANDLED -> Unit
        }
        parts.forEachIndexed { index, part ->
            when (part) {
                is FavoritePart.Text -> {
                    val committed = FavoriteBridge.commitText(part.text)
                    if (!committed) {
                        context.toast(
                            if (index == 0) "文本发送失败，请重试" else "第 ${index + 1} 条素材发送中断，已停止续发"
                        )
                        return false
                    }
                    delay(if (index == 0) 110L else 160L)
                    val sentOk = FavoriteBridge.performTextSend(part.text)
                    if (!sentOk) {
                        context.toast(
                            if (index == 0) "文本发送失败，请重试" else "第 ${index + 1} 条素材发送中断，已停止续发"
                        )
                        return false
                    }
                }
                is FavoritePart.Attachment -> {
                    val sent = sendSingleAttachment(
                        attachment = part,
                        partIndex = index,
                        automationEnabled = automationEnabled,
                    )
                    if (!sent) return false
                }
            }
            if (index != parts.lastIndex) {
                delay(220L)
            }
        }
        return true
    }

    private suspend fun restoreHostInputBeforeSend(
        baseline: FavoriteHostInputRestoreBaseline,
    ): Boolean {
        val hostPackageName = baseline.hostPackageName.ifBlank { launchHostPackageName?.trim().orEmpty() }
        val restorePolicy = FavoriteBridge.hostInputRestorePolicy(hostPackageName)
        // #region debug-point C:idlefish-restore-entry
        reportIdlefishKeyboardFocusDebug(
            hypothesisId = "H1",
            location = "MaterialHubFavoriteController.restoreHostInputBeforeSend",
            msg = "start restoring host input before sending",
            data =
                "hostPackage=$hostPackageName " +
                    "targetPackage=${FavoriteBridge.currentInputTargetPackage().orEmpty()} " +
                    "startCount=${FavoriteBridge.currentInputViewStartCount()} " +
                    "baselineStartCount=${baseline.startCount} " +
                    "baselineTarget=${baseline.targetPackageName.orEmpty()} " +
                    "preCloseRequestShowKeyboard=${baseline.requestedShowKeyboard}",
        )
        // #endregion
        if (hostPackageName.isBlank()) {
            reportMaterialRestoreDiagnostic(
                name = "restore_failed_blank_host",
                level = "WARN",
                message = "host package is blank before material send restore",
                attributes = baseline.toAttributeMap(),
            )
            return false
        }
        val closeWindowPackage = FavoriteBridge.currentInputTargetPackage().orEmpty()
        val closeWindowStartCount = FavoriteBridge.currentInputViewStartCount()
        val closeWindowRebound = closeWindowPackage.equals(hostPackageName, ignoreCase = true) &&
            closeWindowStartCount > baseline.startCount
        if (closeWindowRebound) {
            reportMaterialRestoreDiagnostic(
                name = "restore_rebound_during_close_window",
                level = "INFO",
                message = "host input rebound happened after drawer close request and before restore polling",
                attributes = baseline.toAttributeMap() + mapOf(
                    "currentPackage" to closeWindowPackage,
                    "currentStartCount" to closeWindowStartCount.toString(),
                ),
            )
            return true
        }
        if (restorePolicy.reuseActiveInputSession) {
            val currentPackage = FavoriteBridge.currentInputTargetPackage().orEmpty()
            val currentStartCount = FavoriteBridge.currentInputViewStartCount()
            if (currentPackage.equals(hostPackageName, ignoreCase = true) && currentStartCount > 0) {
                reportIdlefishKeyboardFocusDebug(
                    hypothesisId = "H1",
                    location = "MaterialHubFavoriteController.restoreHostInputBeforeSend",
                    msg = "reuse current idlefish input session before sending",
                    data =
                        "hostPackage=$hostPackageName " +
                            "currentPackage=$currentPackage " +
                            "currentStartCount=$currentStartCount",
                )
                return true
            }
        }
        val maxRestoreRounds = restorePolicy.restoreAttempts.coerceIn(1, 3)
        var lastBeforeStartCount = baseline.startCount
        repeat(maxRestoreRounds) { roundIndex ->
            val beforeStartCount = if (roundIndex == 0) baseline.startCount else FavoriteBridge.currentInputViewStartCount()
            lastBeforeStartCount = beforeStartCount
            val activated = if (restorePolicy.requiresHostInputActivation) {
                FavoriteBridge.activateHostInput(hostPackageName)
            } else {
                false
            }
            // #region debug-point C:idlefish-activate-host-input
            reportIdlefishKeyboardFocusDebug(
                hypothesisId = "H2",
                location = "MaterialHubFavoriteController.restoreHostInputBeforeSend",
                msg =
                    if (restorePolicy.requiresHostInputActivation) {
                        "host-specific input activation requested before keyboard restore"
                    } else {
                        "host input activation not required before keyboard restore"
                    },
                data =
                    "hostPackage=$hostPackageName " +
                        "round=${roundIndex + 1} " +
                        "activated=$activated " +
                        "beforeStartCount=$beforeStartCount",
            )
            // #endregion
            val requested = FavoriteBridge.requestShowKeyboard()
            // #region debug-point C:idlefish-request-show-keyboard
            reportIdlefishKeyboardFocusDebug(
                hypothesisId = "H3",
                location = "MaterialHubFavoriteController.restoreHostInputBeforeSend",
                msg = "requested keyboard restore after material hub closed",
                data =
                    "requested=$requested " +
                        "round=${roundIndex + 1} " +
                        "beforeStartCount=$beforeStartCount " +
                        "hostPackage=$hostPackageName",
            )
            // #endregion
            if (!requested) {
                reportMaterialRestoreDiagnostic(
                    name = "restore_request_show_keyboard_failed",
                    level = "WARN",
                    message = "requestShowKeyboard returned false while restoring material send target",
                    attributes = baseline.toAttributeMap() + mapOf(
                        "round" to (roundIndex + 1).toString(),
                        "beforeStartCount" to beforeStartCount.toString(),
                    ),
                )
                return false
            }
            repeat(8) {
                delay(140L)
                val currentPackage = FavoriteBridge.currentInputTargetPackage().orEmpty()
                val currentStartCount = FavoriteBridge.currentInputViewStartCount()
                val rebound = currentPackage.equals(hostPackageName, ignoreCase = true) &&
                    currentStartCount > beforeStartCount
                // #region debug-point D:idlefish-restore-poll
                reportIdlefishKeyboardFocusDebug(
                    hypothesisId = "H1",
                    location = "MaterialHubFavoriteController.restoreHostInputBeforeSend",
                    msg = "poll host input rebound state",
                    data =
                        "round=${roundIndex + 1} " +
                            "attempt=${it + 1} " +
                            "currentPackage=$currentPackage " +
                            "currentStartCount=$currentStartCount " +
                            "beforeStartCount=$beforeStartCount " +
                            "rebound=$rebound",
                )
                // #endregion
                if (rebound) {
                    delay(100L)
                    return true
                }
            }
        }
        // #region debug-point D:idlefish-restore-failed
        reportIdlefishKeyboardFocusDebug(
            hypothesisId = "H1",
            location = "MaterialHubFavoriteController.restoreHostInputBeforeSend",
            msg = "failed to rebound host input after material hub close",
            data =
                "hostPackage=$hostPackageName " +
                    "finalPackage=${FavoriteBridge.currentInputTargetPackage().orEmpty()} " +
                    "finalStartCount=${FavoriteBridge.currentInputViewStartCount()} " +
                    "beforeStartCount=$lastBeforeStartCount",
        )
        // #endregion
        reportMaterialRestoreDiagnostic(
            name = "restore_failed",
            level = "WARN",
            message = "failed to rebound host input after material hub close",
            attributes = baseline.toAttributeMap() + mapOf(
                "hostPackage" to hostPackageName,
                "finalPackage" to FavoriteBridge.currentInputTargetPackage().orEmpty(),
                "finalStartCount" to FavoriteBridge.currentInputViewStartCount().toString(),
                "beforeStartCount" to lastBeforeStartCount.toString(),
                "restoreAttempts" to maxRestoreRounds.toString(),
            ),
        )
        return false
    }

    private fun reportMaterialRestoreDiagnostic(
        name: String,
        level: String,
        message: String,
        attributes: Map<String, String>,
    ) {
        FavoriteBridge.reportDiagnosticEvent(
            FavoriteDiagnosticEvent(
                category = "material_restore",
                name = name,
                level = level,
                message = message,
                attributes = attributes,
            ),
        )
    }

    private fun FavoriteHostInputRestoreBaseline.toAttributeMap(): Map<String, String> = mapOf(
        "hostPackage" to hostPackageName,
        "baselineTargetPackage" to targetPackageName.orEmpty(),
        "baselineStartCount" to startCount.toString(),
        "preCloseRequestShowKeyboard" to requestedShowKeyboard.toString(),
        "baselineCreatedAtMs" to createdAtMs.toString(),
    )

    private suspend fun sendSingleAttachment(
        attachment: FavoritePart.Attachment,
        partIndex: Int,
        automationEnabled: Boolean,
    ): Boolean {
            val previousInputViewStartCount = FavoriteBridge.currentInputViewStartCount()
            val directSent = FavoriteBridge.tryDirectAttachmentSend(attachment)
            if (!directSent) {
                context.toast("第 ${partIndex + 1} 条素材发送失败，请重试")
                return false
            }
            val sent = FavoriteBridge.awaitDirectAttachmentSent(
                previousInputViewStartCount = previousInputViewStartCount,
                timeoutMs = 15_000L,
                automationEnabled = automationEnabled,
            )
            if (!sent) {
                context.toast(
                    if (automationEnabled) {
                        "未检测到第 ${partIndex + 1} 条素材自动发送完成，已停止续发"
                    } else {
                        "未检测到第 ${partIndex + 1} 条素材发送完成，已停止续发"
                    }
                )
                return false
            }
        return true
    }

    companion object {
        private const val TAG = "MaterialHubFavorite"
        private const val PREVIEW_HEADER_ACTION_ICON_SCALE = 1f
        private const val PAGE_SIZE = 10
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val FLOATING_ITEM_STAGGER_MS = 36L
        private const val FLOATING_ITEM_ENTER_DURATION_MS = 240L
        private const val FLOATING_ITEM_EXIT_DURATION_MS = 150L
        private const val FLOATING_BUTTON_SWAP_HALF_DURATION_MS = 90L
        private const val AFTER_CLOSE_RESTORE_STABILIZE_MS = 120L
    }

    private fun mergeFavoriteRecords(
        local: List<FavoriteRecord>,
        remote: List<FavoriteRecord>,
    ): List<FavoriteRecord> {
        return buildList {
            addAll(remote)
            addAll(local)
        }.sortedByDescending { it.updatedAt }
    }
}
