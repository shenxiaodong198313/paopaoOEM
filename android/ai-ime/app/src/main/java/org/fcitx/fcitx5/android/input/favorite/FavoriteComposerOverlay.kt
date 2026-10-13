package org.fcitx.fcitx5.android.input.favorite

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import org.fcitx.fcitx5.android.utils.toast
import splitties.dimensions.dp

internal class FavoriteComposerOverlay(
    context: Context,
    private val ui: FavoriteUi,
    private val onOpenList: () -> Unit,
    private val onCloseToKeyboard: () -> Unit,
    private val onVisibilityChanged: (Boolean) -> Unit = {},
    private val onKeyboardVisibilityChanged: (Boolean) -> Int,
    private val onTextEditorInputFinished: () -> Unit,
) {
    companion object {
        private const val TAG = "FavoriteComposerOverlay"
        private const val TEXT_EDITOR_CURSOR_BLINK_MS = 530L
        // #region debug-point A:document-preview-back-reporter
        private fun reportDocumentPreviewBackDebug(hypothesisId: String, location: String, msg: String, data: String) = Unit
        // #endregion
        // #region debug-point A:media-preview-back-reporter
        private fun reportMediaPreviewBackDebug(hypothesisId: String, location: String, msg: String, data: String) = Unit
        // #endregion
        // #region debug-point D:wechat-drawer-reporter
        private fun reportWechatDrawerPanelDebug(hypothesisId: String, location: String, msg: String, data: String) = Unit
        // #endregion
        // #region debug-point REPORTER:drawer-stuck-keyboard
        private fun reportDebug(hypothesisId: String, location: String, msg: String, data: String) = Unit
        // #endregion
    }

    private val storage = FavoriteStorage(context)
    private val embeddedPickerStore = FavoriteEmbeddedPickerStore(context)
    private var listenerRegistered = false
    private var activeEmbeddedPickerType: FavoritePickerType? = null
    private var embeddedPickerItems: List<FavoriteEmbeddedPickerItem> = emptyList()
    private val embeddedPickerItemsCache = mutableMapOf<FavoritePickerType, List<FavoriteEmbeddedPickerItem>>()
    private val embeddedPickerContentViewCache = mutableMapOf<FavoritePickerType, View>()
    private val embeddedPickerVisibleCount = mutableMapOf<FavoritePickerType, Int>()
    private val selectedEmbeddedPickerItems = mutableListOf<FavoriteEmbeddedPickerItem>()
    private var embeddedPickerLoading = false
    private var embeddedPickerImporting = false
    private var activeFileCategory = FavoriteFileCategory.ALL
    private var pendingEmbeddedPickerTypeAfterRestore: FavoritePickerType? = null
    private var pendingScrollToBottom = false
    private val embeddedPickerScrollY = mutableMapOf<FavoritePickerType, Int>()
    private var quickActionMenuVisible = false
    private var openedFromQuickEntry = false
    private var textEditorKeyboardRequested = false
    private var textEditorCursorVisible = true
    private var activeTextEditorViewHolder: FavoriteUi.TextEditorViewHolder? = null
    private var composerVisible = false
    private val cursorBlinkRunnable = object : Runnable {
        override fun run() {
            if (!ui.root.isVisible || !FavoriteComposerSession.isTextEditorActive()) return
            textEditorCursorVisible = !textEditorCursorVisible
            FavoriteComposerSession.textEditorSnapshot()?.let { editor ->
                activeTextEditorViewHolder?.let { holder ->
                    ui.bindTextEditorView(
                        holder = holder,
                        text = editor.currentText,
                        composingText = editor.composingText,
                        cursorVisible = textEditorCursorVisible,
                    )
                }
            }
            ui.root.postDelayed(this, TEXT_EDITOR_CURSOR_BLINK_MS)
        }
    }

    private val composerListener: () -> Unit = {
        // #region debug-point B:composer-listener
        reportDebug(
            hypothesisId = "B",
            location = "FavoriteComposerOverlay:composerListener",
            msg = "composer session listener fired",
            data = "textEditorActive=${FavoriteComposerSession.isTextEditorActive()},hasDraft=${FavoriteComposerSession.hasDraft()}"
        )
        // #endregion
        ui.root.post { render() }
        Unit
    }

    val view: View = ui.root.apply {
        isFocusable = true
        isFocusableInTouchMode = true
        setOnClickListener { }
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && isVisible) {
                handleBackPressed()
            } else {
                false
            }
        }
    }

    init {
        ui.showComposerMode()
        ui.floatingAddButton.visibility = View.GONE
        ui.collapseFloatingMenuImmediate()
        ui.composerBackAction.setOnClickListener {
            handleBackPressed()
        }
        ui.composerSaveButton.setOnClickListener {
            setQuickActionMenuVisible(false)
            if (FavoriteComposerSession.isTextEditorActive()) {
                saveTextEditorAndReturnToComposer()
            } else {
                saveDraft()
            }
        }
        ui.composerTextActionButton.setOnClickListener {
            completeEmbeddedMediaSelection()
        }
        ui.addActionButton.setOnClickListener { toggleQuickActionMenu() }
        ui.aiActionButton.setOnClickListener { context.toast("AI 填充稍后接入") }
        ui.drawerMask.setOnClickListener { setQuickActionMenuVisible(false) }
        ui.composerContentScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            handleEmbeddedPickerScrollLoad(scrollY)
        }
        ui.floatingActionItems.forEach { (type, view) ->
            view.setOnClickListener {
                setQuickActionMenuVisible(false)
                openPickerType(type)
            }
        }
        bindDefaultAddAction()
    }

    fun bindDefaultAddAction() {
        ui.configureComposerAddAction(
            iconRes = org.fcitx.fcitx5.android.R.drawable.ic_favorite_add_action_24,
            tintColor = android.graphics.Color.parseColor("#111111"),
        ) { toggleQuickActionMenu() }
    }

    private fun showInternal(fromQuickEntry: Boolean) {
        // #region debug-point A:document-show-internal
        reportDocumentPreviewBackDebug(
            hypothesisId = "A",
            location = "FavoriteComposerOverlay:showInternal",
            msg = "show composer internal for document preview session",
            data = "fromQuickEntry=$fromQuickEntry,activeEmbeddedPickerType=$activeEmbeddedPickerType,pendingEmbeddedPickerTypeAfterRestore=$pendingEmbeddedPickerTypeAfterRestore,hasDraft=${FavoriteComposerSession.hasDraft()},hasMeaningfulDraft=${FavoriteComposerSession.hasMeaningfulDraft()}"
        )
        // #endregion
        // #region debug-point A:show-internal
        reportMediaPreviewBackDebug(
            hypothesisId = "A",
            location = "FavoriteComposerOverlay:showInternal",
            msg = "show composer internal",
            data = "fromQuickEntry=$fromQuickEntry,activeEmbeddedPickerType=$activeEmbeddedPickerType,pendingEmbeddedPickerTypeAfterRestore=$pendingEmbeddedPickerTypeAfterRestore,hasDraft=${FavoriteComposerSession.hasDraft()},hasMeaningfulDraft=${FavoriteComposerSession.hasMeaningfulDraft()}"
        )
        // #endregion
        composerVisible = true
        openedFromQuickEntry = fromQuickEntry
        val existing = FavoriteComposerSession.snapshot()
        if (existing == null || !existing.hasMeaningfulContent()) {
            FavoriteComposerSession.beginNewDraft()
        } else {
            FavoriteComposerSession.ensureDraft()
        }
        if (FavoriteComposerSession.textEditorSnapshot() == null) {
            FavoriteComposerSession.discardTextEditor()
        }
        activeEmbeddedPickerType = null
        embeddedPickerItems = emptyList()
        embeddedPickerItemsCache.clear()
        embeddedPickerContentViewCache.clear()
        embeddedPickerVisibleCount.clear()
        selectedEmbeddedPickerItems.clear()
        embeddedPickerLoading = false
        embeddedPickerImporting = false
        activeFileCategory = FavoriteFileCategory.ALL
        if (!listenerRegistered) {
            FavoriteComposerSession.registerListener(composerListener)
            listenerRegistered = true
        }
        ui.showComposerMode(animated = true)
        ui.root.bringToFront()
        if (FavoriteComposerSession.textEditorSnapshot() == null) {
            ui.root.requestFocus()
        }
        bindDefaultAddAction()
        ui.setDrawerVisible(false)
        setQuickActionMenuVisible(false)
        onVisibilityChanged(true)
        val textEditorActive = FavoriteComposerSession.textEditorSnapshot() != null
        applyKeyboardVisibility(textEditorActive)
        textEditorKeyboardRequested = false
        render()
        pendingEmbeddedPickerTypeAfterRestore?.let { type ->
            pendingEmbeddedPickerTypeAfterRestore = null
            showEmbeddedPickerInternal(type)
        }
    }

    fun show() {
        showInternal(fromQuickEntry = false)
    }

    fun showForQuickEntry() {
        showInternal(fromQuickEntry = true)
    }

    fun showForEntry(type: FavoritePickerType) {
        // #region debug-point D:show-for-entry
        reportWechatDrawerPanelDebug(
            hypothesisId = "D",
            location = "FavoriteComposerOverlay:showForEntry",
            msg = "show favorite composer entry",
            data = "type=$type,openedFromQuickEntry=$openedFromQuickEntry,textEditorActive=${FavoriteComposerSession.isTextEditorActive()}"
        )
        // #endregion
        if (type == FavoritePickerType.TEXT) {
            FavoriteComposerSession.beginNewTextEditor()
            enterTextEditorMode()
            showInternal(fromQuickEntry = true)
            return
        }
        showInternal(fromQuickEntry = true)
        openPickerType(type)
    }

    fun hide() {
        composerVisible = false
        stopTextEditorCursorBlink()
        activeTextEditorViewHolder = null
        setQuickActionMenuVisible(false)
        ui.setDrawerVisible(false)
        applyKeyboardVisibility(false)
        textEditorKeyboardRequested = false
        if (listenerRegistered) {
            FavoriteComposerSession.unregisterListener(composerListener)
            listenerRegistered = false
        }
        onVisibilityChanged(false)
    }

    fun rerenderCurrentState() {
        render()
    }

    fun isShowing(): Boolean = composerVisible

    fun hasRestorableDestination(): Boolean {
        return FavoriteComposerSession.isTextEditorActive() ||
            FavoriteComposerSession.hasMeaningfulDraft() ||
            pendingEmbeddedPickerTypeAfterRestore != null
    }

    fun clearStaleEmptyState() {
        if (hasRestorableDestination()) return
        activeEmbeddedPickerType = null
        embeddedPickerItems = emptyList()
        embeddedPickerLoading = false
        pendingEmbeddedPickerTypeAfterRestore = null
        embeddedPickerItemsCache.clear()
        embeddedPickerContentViewCache.clear()
        embeddedPickerVisibleCount.clear()
        embeddedPickerScrollY.clear()
        quickActionMenuVisible = false
        openedFromQuickEntry = false
        FavoriteComposerSession.clear()
        hide()
        Log.d(TAG, "clearStaleEmptyState")
    }

    fun handleBackPressed(): Boolean {
        if (!composerVisible) return false
        // #region debug-point B:handle-back-pressed
        reportMediaPreviewBackDebug(
            hypothesisId = "B",
            location = "FavoriteComposerOverlay:handleBackPressed",
            msg = "handle composer back pressed",
            data = "activeEmbeddedPickerType=$activeEmbeddedPickerType,textEditorActive=${FavoriteComposerSession.isTextEditorActive()},openedFromQuickEntry=$openedFromQuickEntry,hasMeaningfulDraft=${FavoriteComposerSession.hasMeaningfulDraft()}"
        )
        // #endregion
        setQuickActionMenuVisible(false)
        if (activeEmbeddedPickerType != null) {
            hideEmbeddedPicker()
        } else if (FavoriteComposerSession.isTextEditorActive()) {
            discardTextEditorAndReturnToComposer()
        } else {
            discardDraftAndOpenList()
        }
        return true
    }

    fun handleSystemKeyboardHidden(): Boolean {
        if (!composerVisible || !FavoriteComposerSession.isTextEditorActive()) {
            return false
        }
        setQuickActionMenuVisible(false)
        discardTextEditorAndReturnToComposer()
        return true
    }

    private fun discardDraftAndReturnToKeyboard() {
        FavoriteComposerSession.snapshot()?.let { storage.deleteDraftAssets(it.draftId) }
        FavoriteComposerSession.clear()
        Log.d(TAG, "discardDraftAndReturnToKeyboard")
        onCloseToKeyboard()
    }

    private fun discardDraftAndOpenList() {
        FavoriteComposerSession.snapshot()?.let { storage.deleteDraftAssets(it.draftId) }
        FavoriteComposerSession.clear()
        Log.d(TAG, "discardDraftAndOpenList")
        onOpenList()
    }

    private fun saveDraft() {
        Log.d(TAG, "saveDraft clicked")
        val record = FavoriteComposerSession.buildRecordForSave()
        if (record == null) {
            view.context.toast("请先添加文本、图片、文件或视频")
            return
        }
        storage.saveFavorite(record)
        FavoriteComposerSession.clear()
        Log.d(TAG, "saveDraft success: id=${record.id}, parts=${record.parts.size}")
        view.context.toast("素材已保存")
        view.postDelayed({ onOpenList() }, 120L)
    }

    private fun openPickerType(type: FavoritePickerType) {
        Log.d(TAG, "openPickerType: $type")
        setQuickActionMenuVisible(false)
        when (type) {
            FavoritePickerType.TEXT -> {
                FavoriteComposerSession.beginNewTextEditor()
                enterTextEditorMode()
                render()
            }
            FavoritePickerType.IMAGE,
            FavoritePickerType.FILE,
            FavoritePickerType.VIDEO -> {
                applyKeyboardVisibility(false)
                showEmbeddedPicker(type)
            }
        }
    }

    private fun render() {
        if (!ui.root.isVisible) return
        // #region debug-point D:render-state
        reportMediaPreviewBackDebug(
            hypothesisId = "D",
            location = "FavoriteComposerOverlay:render",
            msg = "render composer state",
            data = "rootVisible=${ui.root.isVisible},activeEmbeddedPickerType=$activeEmbeddedPickerType,textEditorActive=${FavoriteComposerSession.isTextEditorActive()},draftParts=${FavoriteComposerSession.snapshot()?.parts?.size ?: -1},pendingEmbeddedPickerTypeAfterRestore=$pendingEmbeddedPickerTypeAfterRestore"
        )
        // #endregion
        activeEmbeddedPickerType?.let { pickerType ->
            stopTextEditorCursorBlink()
            renderEmbeddedPicker(pickerType)
            return
        }
        FavoriteComposerSession.textEditorSnapshot()?.let { editor ->
            renderTextEditor(editor)
            return
        }
        stopTextEditorCursorBlink()
        activeTextEditorViewHolder = null
        val draft = FavoriteComposerSession.snapshot() ?: FavoriteComposerSession.beginNewDraft()
        ui.configureComposerHeader(
            title = view.context.getString(org.fcitx.fcitx5.android.R.string.favorite_composer_title),
            showAddAction = true,
            showSaveAction = true
        )
        applyKeyboardVisibility(false)
        ui.composerContent.removeAllViews()
        if (draft.parts.isEmpty()) {
            ui.composerContent.addView(ui.buildComposerEmptyStateView(), ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            return
        }
        draft.parts.forEachIndexed { index, part ->
            val child = when (part) {
                is FavoritePart.Text -> ui.buildTextPartView(
                    part = part,
                    onSelect = {
                        FavoriteComposerSession.beginEditTextPart(part.id)
                        enterTextEditorMode()
                        render()
                    }
                )
                is FavoritePart.Attachment -> ui.buildAttachmentPartView(
                    part = part,
                    onPreview = { previewAttachment(part) }
                )
            }
            ui.composerContent.addView(child, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            if (index != draft.parts.lastIndex) {
                ui.composerContent.addView(View(view.context), ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    view.context.dp(14)
                ))
            }
        }
        if (pendingScrollToBottom) {
            ui.scrollComposerToBottom()
            pendingScrollToBottom = false
        }
    }

    private fun toggleQuickActionMenu() {
        setQuickActionMenuVisible(!quickActionMenuVisible)
    }

    private fun setQuickActionMenuVisible(visible: Boolean) {
        quickActionMenuVisible = visible
        if (visible) {
            ui.floatingActionMenu.visibility = View.VISIBLE
            ui.floatingActionMenu.alpha = 1f
            ui.floatingActionItems.values.forEach { item ->
                item.animate().cancel()
                item.alpha = 1f
                item.translationX = 0f
                item.scaleX = 1f
                item.scaleY = 1f
            }
        } else {
            ui.collapseFloatingMenuImmediate()
        }
    }

    private fun renderTextEditor(editor: FavoriteTextEditorDraft) {
        // #region debug-point D:render-text-editor-wechat
        reportWechatDrawerPanelDebug(
            hypothesisId = "D",
            location = "FavoriteComposerOverlay:renderTextEditor",
            msg = "render text editor for wechat drawer session",
            data = "textLength=${editor.currentText.length},composingLength=${editor.composingText.length},rootVisible=${ui.root.isVisible}"
        )
        // #endregion
        // #region debug-point C:render-text-editor
        reportDebug(
            hypothesisId = "C",
            location = "FavoriteComposerOverlay:renderTextEditor",
            msg = "render text editor",
            data = "textLength=${editor.currentText.length},rootVisible=${ui.root.isVisible}"
        )
        // #endregion
        ui.configureComposerHeader(
            title = view.context.getString(org.fcitx.fcitx5.android.R.string.favorite_picker_text),
            showAddAction = false,
            showSaveAction = true,
            textEditorMode = true
        )
        ui.configureComposerSaveAction(
            iconRes = org.fcitx.fcitx5.android.R.drawable.ic_favorite_preview_submit_success,
            tintColor = null,
        ) {
            if (FavoriteComposerSession.isTextEditorActive()) {
                saveTextEditorAndReturnToComposer()
            } else {
                saveDraft()
            }
        }
        val textEditorView = activeTextEditorViewHolder ?: ui.buildTextEditorView(
            text = editor.currentText,
            composingText = editor.composingText,
            cursorVisible = textEditorCursorVisible,
            onRequestKeyboard = { applyKeyboardVisibility(true) },
            onTextChanged = { updated ->
                FavoriteComposerSession.updateTextEditorText(updated)
            },
        ).also { holder ->
            activeTextEditorViewHolder = holder
            ui.composerContent.removeAllViews()
            ui.composerContent.addView(
                holder.root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        ui.layoutTextEditorViewport(textEditorView)
        ui.bindTextEditorView(
            holder = textEditorView,
            text = editor.currentText,
            composingText = editor.composingText,
            cursorVisible = textEditorCursorVisible,
        )
        textEditorView.contentView.post {
            if (!textEditorView.contentView.hasFocus()) {
                textEditorView.contentView.requestFocus()
            }
            textEditorView.contentView.setSelection(textEditorView.contentView.text?.length ?: 0)
        }
        ui.composerContentScroll.scrollTo(0, 0)
        ui.scrollTextEditorCursorIntoView(textEditorView)
        if (!textEditorKeyboardRequested) {
            textEditorKeyboardRequested = true
            ui.composerContent.post {
                applyKeyboardVisibility(true)
            }
        }
        startTextEditorCursorBlink()
    }

    private fun enterTextEditorMode() {
        textEditorKeyboardRequested = false
        textEditorCursorVisible = true
    }

    private fun discardTextEditorAndReturnToComposer() {
        stopTextEditorCursorBlink()
        activeTextEditorViewHolder = null
        onTextEditorInputFinished()
        FavoriteComposerSession.discardTextEditor()
        textEditorKeyboardRequested = false
        applyKeyboardVisibility(false)
        if (openedFromQuickEntry && !FavoriteComposerSession.ensureDraft().hasMeaningfulContent()) {
            discardDraftAndOpenList()
            return
        }
        render()
    }

    private fun saveTextEditorAndReturnToComposer() {
        val saveResult = FavoriteComposerSession.saveTextEditor()
        if (saveResult == null) {
            view.context.toast("请先输入文本内容")
            return
        }
        pendingScrollToBottom = saveResult.createdNew
        stopTextEditorCursorBlink()
        activeTextEditorViewHolder = null
        onTextEditorInputFinished()
        textEditorKeyboardRequested = false
        applyKeyboardVisibility(false)
        render()
    }

    private fun startTextEditorCursorBlink() {
        ui.root.removeCallbacks(cursorBlinkRunnable)
        if (!FavoriteComposerSession.isTextEditorActive()) return
        ui.root.postDelayed(cursorBlinkRunnable, TEXT_EDITOR_CURSOR_BLINK_MS)
    }

    private fun stopTextEditorCursorBlink() {
        ui.root.removeCallbacks(cursorBlinkRunnable)
        textEditorCursorVisible = true
    }

    private fun showEmbeddedPicker(type: FavoritePickerType) {
        if (type == FavoritePickerType.TEXT) return
        var waitingForExternalPermissionRestore = false
        val handled = FavoriteBridge.requestEmbeddedPickerPermission(type) { granted ->
            view.post {
                if (!waitingForExternalPermissionRestore || !granted) return@post
                pendingEmbeddedPickerTypeAfterRestore = type
            }
        }
        waitingForExternalPermissionRestore = handled
        if (handled) return
        pendingEmbeddedPickerTypeAfterRestore = null
        showEmbeddedPickerInternal(type)
    }

    private fun showEmbeddedPickerInternal(type: FavoritePickerType) {
        // #region debug-point A:document-show-embedded-picker
        reportDocumentPreviewBackDebug(
            hypothesisId = "A",
            location = "FavoriteComposerOverlay:showEmbeddedPickerInternal",
            msg = "show embedded picker internal for document preview session",
            data = "type=$type,pendingEmbeddedPickerTypeAfterRestore=$pendingEmbeddedPickerTypeAfterRestore,openedFromQuickEntry=$openedFromQuickEntry"
        )
        // #endregion
        // #region debug-point A:show-embedded-picker
        reportMediaPreviewBackDebug(
            hypothesisId = "A",
            location = "FavoriteComposerOverlay:showEmbeddedPickerInternal",
            msg = "show embedded picker internal",
            data = "type=$type,pendingEmbeddedPickerTypeAfterRestore=$pendingEmbeddedPickerTypeAfterRestore,openedFromQuickEntry=$openedFromQuickEntry"
        )
        // #endregion
        activeEmbeddedPickerType = type
        val cachedItems = embeddedPickerItemsCache[type]
        if (cachedItems != null) {
            embeddedPickerItems = cachedItems
            embeddedPickerLoading = false
            ensureEmbeddedPickerVisibleCount(type, cachedItems.size)
            render()
            return
        }
        embeddedPickerItems = emptyList()
        embeddedPickerLoading = true
        render()
        Thread {
            val items = embeddedPickerStore.load(type)
            view.post {
                if (activeEmbeddedPickerType != type) return@post
                embeddedPickerItemsCache[type] = items
                embeddedPickerItems = items
                embeddedPickerLoading = false
                ensureEmbeddedPickerVisibleCount(type, items.size)
                render()
            }
        }.start()
    }

    private fun hideEmbeddedPicker() {
        // #region debug-point A:hide-embedded-picker
        reportMediaPreviewBackDebug(
            hypothesisId = "A",
            location = "FavoriteComposerOverlay:hideEmbeddedPicker",
            msg = "hide embedded picker",
            data = "activeEmbeddedPickerType=$activeEmbeddedPickerType,selectedCount=${selectedEmbeddedPickerItems.size},openedFromQuickEntry=$openedFromQuickEntry,hasMeaningfulDraft=${FavoriteComposerSession.hasMeaningfulDraft()}"
        )
        // #endregion
        val previousPickerType = activeEmbeddedPickerType
        activeEmbeddedPickerType = null
        embeddedPickerItems = emptyList()
        embeddedPickerLoading = false
        embeddedPickerImporting = false
        selectedEmbeddedPickerItems.clear()
        pendingEmbeddedPickerTypeAfterRestore = null
        previousPickerType?.let(embeddedPickerScrollY::remove)
        FavoriteComposerSession.clearPreviewReturnPickerType()
        if (openedFromQuickEntry && !FavoriteComposerSession.ensureDraft().hasMeaningfulContent()) {
            discardDraftAndOpenList()
            return
        }
        render()
    }

    private fun renderEmbeddedPicker(type: FavoritePickerType) {
        applyKeyboardVisibility(false)
        val isSelectablePicker = type == FavoritePickerType.IMAGE || type == FavoritePickerType.VIDEO || type == FavoritePickerType.FILE
        val selectedCount = if (isSelectablePicker) selectedEmbeddedPickerItems.size else 0
        val visibleItems = visibleEmbeddedPickerItems(type)
        ui.configureComposerHeader(
            title = pickerTitle(type),
            showAddAction = false,
            showSaveAction = false,
            actionText = if (isSelectablePicker) {
                view.context.getString(org.fcitx.fcitx5.android.R.string.favorite_picker_complete_count, selectedCount)
            } else {
                null
            },
            actionTextEnabled = isSelectablePicker && selectedCount > 0 && !embeddedPickerImporting
        )
        val reusableContentView = if (!embeddedPickerLoading) embeddedPickerContentViewCache[type] else null
        val contentView = when {
            embeddedPickerLoading -> ui.buildPickerLoadingView("正在加载${pickerTitle(type)}")
            embeddedPickerItems.isEmpty() && type != FavoritePickerType.FILE ->
                ui.buildPickerEmptyStateView("当前没有可用的${pickerTitle(type)}")
            reusableContentView != null -> reusableContentView
            else -> ui.buildEmbeddedPickerView(
                type = type,
                items = visibleItems,
                selectedFileCategory = activeFileCategory,
                onSelectFileCategory = { category ->
                    activeFileCategory = category
                    embeddedPickerScrollY[FavoritePickerType.FILE] = 0
                    embeddedPickerContentViewCache.remove(FavoritePickerType.FILE)
                    render()
                },
                onPick = ::importEmbeddedPickerItem,
                onToggleMediaSelection = ::toggleEmbeddedMediaSelection,
                selectedMediaOrder = ::selectedMediaOrder,
                onPreviewFile = ::previewEmbeddedFileItem,
                onToggleFileSelection = ::toggleEmbeddedPickerSelection,
                selectedFileOrder = ::selectedMediaOrder
            ).also { embeddedPickerContentViewCache[type] = it }
        }
        val currentContentView = ui.composerContent.getChildAt(0)
        val alreadyAttached = currentContentView === contentView && ui.composerContent.childCount == 1
        if (!alreadyAttached) {
            ui.composerContent.removeAllViews()
            (contentView.parent as? ViewGroup)?.removeView(contentView)
            ui.composerContent.addView(contentView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        ui.restoreComposerScrollY(embeddedPickerScrollY[type] ?: 0)
    }

    private fun visibleEmbeddedPickerItems(type: FavoritePickerType): List<FavoriteEmbeddedPickerItem> {
        val visibleCount = embeddedPickerVisibleCount[type] ?: initialVisibleCount(type)
        if (type != FavoritePickerType.FILE || activeFileCategory == FavoriteFileCategory.ALL) {
            return embeddedPickerItems.take(visibleCount.coerceAtMost(embeddedPickerItems.size))
        }
        val targetCategory = activeFileCategory
        val result = ArrayList<FavoriteEmbeddedPickerItem>()
        var matchedCount = 0
        embeddedPickerItems.forEach { item ->
            result += item
            if (item.fileCategory == targetCategory) {
                matchedCount += 1
            }
            if (matchedCount >= visibleCount) {
                return result
            }
        }
        return result
    }

    private fun ensureEmbeddedPickerVisibleCount(type: FavoritePickerType, totalCount: Int) {
        val currentVisible = embeddedPickerVisibleCount[type] ?: 0
        if (currentVisible > 0) return
        embeddedPickerVisibleCount[type] = initialVisibleCount(type).coerceAtMost(totalCount)
    }

    private fun initialVisibleCount(type: FavoritePickerType): Int {
        return when (type) {
            FavoritePickerType.IMAGE,
            FavoritePickerType.VIDEO -> 80
            FavoritePickerType.FILE -> 80
            FavoritePickerType.TEXT -> 0
        }
    }

    private fun loadMoreBatchSize(type: FavoritePickerType): Int {
        return when (type) {
            FavoritePickerType.IMAGE,
            FavoritePickerType.VIDEO -> 80
            FavoritePickerType.FILE -> 80
            FavoritePickerType.TEXT -> 0
        }
    }

    private fun handleEmbeddedPickerScrollLoad(scrollY: Int) {
        val pickerType = activeEmbeddedPickerType ?: return
        if (embeddedPickerLoading || embeddedPickerImporting) return
        if (pickerType != FavoritePickerType.IMAGE && pickerType != FavoritePickerType.VIDEO && pickerType != FavoritePickerType.FILE) return
        val currentVisible = embeddedPickerVisibleCount[pickerType] ?: return
        if (currentVisible >= embeddedPickerItems.size) return
        val contentHeight = ui.composerContent.height
        val viewportHeight = ui.composerContentScroll.height
        if (contentHeight <= 0 || viewportHeight <= 0) return
        val preloadThreshold = ui.root.context.dp(240)
        val reachedLoadBoundary = scrollY + viewportHeight >= contentHeight - preloadThreshold
        if (!reachedLoadBoundary) return
        embeddedPickerScrollY[pickerType] = scrollY
        embeddedPickerVisibleCount[pickerType] = (currentVisible + loadMoreBatchSize(pickerType)).coerceAtMost(embeddedPickerItems.size)
        embeddedPickerContentViewCache.remove(pickerType)
        render()
    }

    private fun importEmbeddedPickerItem(item: FavoriteEmbeddedPickerItem) {
        val draft = FavoriteComposerSession.ensureDraft()
        view.context.toast("正在添加${item.displayName}")
        Thread {
            val attachment = storage.createAttachmentFromUri(draft.draftId, item.uri, item.pickerType)
            view.post {
                if (attachment == null) {
                    view.context.toast("素材导入失败，请重试")
                    return@post
                }
                FavoriteComposerSession.appendAttachment(attachment)
                pendingScrollToBottom = true
                hideEmbeddedPicker()
            }
        }.start()
    }

    private fun toggleEmbeddedMediaSelection(item: FavoriteEmbeddedPickerItem) {
        val pickerType = activeEmbeddedPickerType ?: return
        if (pickerType != FavoritePickerType.IMAGE && pickerType != FavoritePickerType.VIDEO) return
        toggleEmbeddedPickerSelection(item)
    }

    private fun toggleEmbeddedPickerSelection(item: FavoriteEmbeddedPickerItem) {
        val pickerType = activeEmbeddedPickerType ?: return
        if (pickerType != FavoritePickerType.IMAGE && pickerType != FavoritePickerType.VIDEO && pickerType != FavoritePickerType.FILE) return
        if (embeddedPickerImporting) return
        rememberEmbeddedPickerScrollPosition()
        embeddedPickerContentViewCache.remove(pickerType)
        val existingIndex = selectedEmbeddedPickerItems.indexOfFirst { it.uri == item.uri }
        if (existingIndex >= 0) {
            selectedEmbeddedPickerItems.removeAt(existingIndex)
        } else {
            selectedEmbeddedPickerItems += item
        }
        render()
    }

    private fun selectedMediaOrder(item: FavoriteEmbeddedPickerItem): Int {
        return selectedEmbeddedPickerItems.indexOfFirst { it.uri == item.uri }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
    }

    private fun completeEmbeddedMediaSelection() {
        val pickerType = activeEmbeddedPickerType ?: return
        if (pickerType != FavoritePickerType.IMAGE && pickerType != FavoritePickerType.VIDEO && pickerType != FavoritePickerType.FILE) return
        if (embeddedPickerImporting) return
        if (selectedEmbeddedPickerItems.isEmpty()) return
        val draft = FavoriteComposerSession.ensureDraft()
        val selectedItems = selectedEmbeddedPickerItems.toList()
        embeddedPickerImporting = true
        render()
        view.context.toast(
            view.context.getString(
                org.fcitx.fcitx5.android.R.string.favorite_picker_importing_count,
                selectedItems.size,
                pickerTitle(pickerType)
            )
        )
        Thread {
            val attachments = selectedItems.mapNotNull { item ->
                storage.createAttachmentFromUri(draft.draftId, item.uri, item.pickerType)
            }
            view.post {
                embeddedPickerImporting = false
                if (attachments.isEmpty()) {
                    view.context.toast("素材导入失败，请重试")
                    render()
                    return@post
                }
                attachments.forEach(FavoriteComposerSession::appendAttachment)
                pendingScrollToBottom = true
                hideEmbeddedPicker()
            }
        }.start()
    }

    private fun previewEmbeddedFileItem(item: FavoriteEmbeddedPickerItem) {
        if (activeEmbeddedPickerType != FavoritePickerType.FILE) return
        // #region debug-point B:document-preview-item
        reportDocumentPreviewBackDebug(
            hypothesisId = "B",
            location = "FavoriteComposerOverlay:previewEmbeddedFileItem",
            msg = "preview embedded file item from picker",
            data = "displayName=${item.displayName},activeEmbeddedPickerType=$activeEmbeddedPickerType,selectedCount=${selectedEmbeddedPickerItems.size},fileCategory=${item.fileCategory}"
        )
        // #endregion
        rememberEmbeddedPickerScrollPosition(FavoritePickerType.FILE)
        FavoriteComposerSession.setPreviewReturnPickerType(FavoritePickerType.FILE)
        view.context.toast("正在打开${item.displayName}")
        Thread {
            val attachment = storage.createPreviewAttachmentFromUri(item.uri, FavoritePickerType.FILE)
            view.post {
                if (attachment == null) {
                    FavoriteComposerSession.clearPreviewReturnPickerType()
                    view.context.toast("文档预览打开失败，请重试")
                    return@post
                }
                if (!FavoriteBridge.openAttachmentPreview(attachment)) {
                    FavoriteComposerSession.clearPreviewReturnPickerType()
                    view.context.toast("当前暂时无法打开预览")
                }
            }
        }.start()
    }

    private fun pickerTitle(type: FavoritePickerType): String {
        return when (type) {
            FavoritePickerType.IMAGE -> view.context.getString(org.fcitx.fcitx5.android.R.string.favorite_picker_image)
            FavoritePickerType.FILE -> view.context.getString(org.fcitx.fcitx5.android.R.string.favorite_attachment_document)
            FavoritePickerType.VIDEO -> view.context.getString(org.fcitx.fcitx5.android.R.string.favorite_picker_video)
            FavoritePickerType.TEXT -> view.context.getString(org.fcitx.fcitx5.android.R.string.favorite_picker_text)
        }
    }

    private fun applyKeyboardVisibility(visible: Boolean) {
        val keyboardHeight = onKeyboardVisibilityChanged(visible)
        val textEditorActive = FavoriteComposerSession.isTextEditorActive()
        // #region debug-point E:wechat-apply-keyboard-visibility
        reportWechatDrawerPanelDebug(
            hypothesisId = "E",
            location = "FavoriteComposerOverlay:applyKeyboardVisibility",
            msg = "apply keyboard visibility for wechat drawer session",
            data = "visible=$visible,keyboardHeight=$keyboardHeight,textEditorActive=$textEditorActive,rootVisible=${ui.root.isVisible}"
        )
        // #endregion
        // #region debug-point E:apply-keyboard-visibility
        reportDebug(
            hypothesisId = "E",
            location = "FavoriteComposerOverlay:applyKeyboardVisibility",
            msg = "apply keyboard visibility",
            data = "visible=$visible,keyboardHeight=$keyboardHeight,textEditorActive=$textEditorActive"
        )
        // #endregion
        if (visible && !textEditorActive) {
            ui.setComposerKeyboardVisible(true)
            ui.setComposerKeyboardHeight(keyboardHeight)
        } else {
            ui.setComposerKeyboardVisible(false)
            ui.setComposerKeyboardHeight(0)
        }
    }

    private fun previewAttachment(part: FavoritePart.Attachment) {
        // #region debug-point C:preview-attachment
        reportMediaPreviewBackDebug(
            hypothesisId = "C",
            location = "FavoriteComposerOverlay:previewAttachment",
            msg = "open attachment preview requested",
            data = "attachmentId=${part.id},type=${part.attachmentType},displayName=${part.displayName},activeEmbeddedPickerType=$activeEmbeddedPickerType,draftParts=${FavoriteComposerSession.snapshot()?.parts?.size ?: -1}"
        )
        // #endregion
        FavoriteComposerSession.clearPreviewReturnPickerType()
        if (!FavoriteBridge.openAttachmentPreview(part)) {
            view.context.toast("当前暂时无法打开预览")
        }
    }

    private fun rememberEmbeddedPickerScrollPosition(type: FavoritePickerType? = activeEmbeddedPickerType) {
        val pickerType = type ?: return
        embeddedPickerScrollY[pickerType] = ui.captureComposerScrollY()
    }
}
