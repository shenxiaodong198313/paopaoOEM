package org.fcitx.fcitx5.android.input.favorite

data class FavoriteHostInputRestorePolicy(
    val reuseActiveInputSession: Boolean = false,
    val requiresHostInputActivation: Boolean = false,
    val restoreAttempts: Int = 1,
    /**
     * Activity overlays can invalidate some hosts' editor connection as they close. For those
     * hosts, dispatch text while the original connection is still valid, then close the overlay.
     */
    val dispatchTextBeforeOverlayClose: Boolean = false,
)

data class FavoriteHostInputRestoreBaseline(
    val hostPackageName: String,
    val targetPackageName: String?,
    val startCount: Int,
    val requestedShowKeyboard: Boolean,
    val createdAtMs: Long,
)

data class FavoriteDiagnosticEvent(
    val category: String,
    val name: String,
    val level: String,
    val message: String,
    val attributes: Map<String, String> = emptyMap(),
    val timestampMs: Long = System.currentTimeMillis(),
)

enum class FavoriteHostShareDispatchResult {
    NOT_HANDLED,
    DISPATCHED,
    FAILED,
}

object FavoriteBridge {
    @Volatile
    private var commitTextHandler: ((String) -> Boolean)? = null
    @Volatile
    private var directAttachmentSendHandler: ((FavoritePart.Attachment) -> Boolean)? = null
    @Volatile
    private var hostShareHandler: ((String, List<FavoritePart>) -> FavoriteHostShareDispatchResult)? = null
    @Volatile
    private var performTextSendHandler: ((String) -> Boolean)? = null
    @Volatile
    private var awaitDirectAttachmentSentHandler: (suspend (Int, Long, Boolean) -> Boolean)? = null
    @Volatile
    private var currentInputViewStartCountHandler: (() -> Int)? = null
    @Volatile
    private var requestEmbeddedPickerPermissionHandler: ((FavoritePickerType, (Boolean) -> Unit) -> Boolean)? = null
    @Volatile
    private var openAttachmentPreviewHandler: ((FavoritePart.Attachment) -> Boolean)? = null
    @Volatile
    private var requestShowKeyboardHandler: (() -> Boolean)? = null
    @Volatile
    private var currentInputTargetPackageHandler: (() -> String?)? = null
    @Volatile
    private var activateHostInputHandler: (suspend (String) -> Boolean)? = null
    @Volatile
    private var hostInputRestorePolicyHandler: ((String) -> FavoriteHostInputRestorePolicy)? = null
    @Volatile
    private var diagnosticEventHandler: ((FavoriteDiagnosticEvent) -> Boolean)? = null

    fun bindCommitText(handler: (String) -> Boolean) {
        commitTextHandler = handler
    }

    fun bindDirectAttachmentSend(handler: (FavoritePart.Attachment) -> Boolean) {
        directAttachmentSendHandler = handler
    }

    fun bindHostShare(handler: (String, List<FavoritePart>) -> FavoriteHostShareDispatchResult) {
        hostShareHandler = handler
    }

    fun bindPerformTextSend(handler: (String) -> Boolean) {
        performTextSendHandler = handler
    }

    fun bindAwaitDirectAttachmentSent(handler: suspend (Int, Long, Boolean) -> Boolean) {
        awaitDirectAttachmentSentHandler = handler
    }

    fun bindCurrentInputViewStartCount(handler: () -> Int) {
        currentInputViewStartCountHandler = handler
    }

    fun bindRequestEmbeddedPickerPermission(
        handler: (FavoritePickerType, (Boolean) -> Unit) -> Boolean
    ) {
        requestEmbeddedPickerPermissionHandler = handler
    }

    fun bindOpenAttachmentPreview(handler: (FavoritePart.Attachment) -> Boolean) {
        openAttachmentPreviewHandler = handler
    }

    fun bindRequestShowKeyboard(handler: () -> Boolean) {
        requestShowKeyboardHandler = handler
    }

    fun bindCurrentInputTargetPackage(handler: () -> String?) {
        currentInputTargetPackageHandler = handler
    }

    fun bindActivateHostInput(handler: suspend (String) -> Boolean) {
        activateHostInputHandler = handler
    }

    fun bindHostInputRestorePolicy(handler: (String) -> FavoriteHostInputRestorePolicy) {
        hostInputRestorePolicyHandler = handler
    }

    fun bindDiagnosticEvent(handler: (FavoriteDiagnosticEvent) -> Boolean) {
        diagnosticEventHandler = handler
    }

    fun unbindCommitText() {
        commitTextHandler = null
    }

    fun unbindDirectAttachmentSend() {
        directAttachmentSendHandler = null
    }

    fun unbindHostShare() {
        hostShareHandler = null
    }

    fun unbindPerformTextSend() {
        performTextSendHandler = null
    }

    fun unbindAwaitDirectAttachmentSent() {
        awaitDirectAttachmentSentHandler = null
    }

    fun unbindCurrentInputViewStartCount() {
        currentInputViewStartCountHandler = null
    }

    fun unbindRequestEmbeddedPickerPermission() {
        requestEmbeddedPickerPermissionHandler = null
    }

    fun unbindOpenAttachmentPreview() {
        openAttachmentPreviewHandler = null
    }

    fun unbindRequestShowKeyboard() {
        requestShowKeyboardHandler = null
    }

    fun unbindCurrentInputTargetPackage() {
        currentInputTargetPackageHandler = null
    }

    fun unbindActivateHostInput() {
        activateHostInputHandler = null
    }

    fun unbindHostInputRestorePolicy() {
        hostInputRestorePolicyHandler = null
    }

    fun unbindDiagnosticEvent() {
        diagnosticEventHandler = null
    }

    fun commitText(text: String): Boolean {
        val handler = commitTextHandler ?: return false
        return handler(text)
    }

    fun performTextSend(expectedText: String): Boolean {
        val handler = performTextSendHandler ?: return false
        return handler(expectedText)
    }

    fun tryDirectAttachmentSend(attachment: FavoritePart.Attachment): Boolean {
        val handler = directAttachmentSendHandler ?: return false
        return handler(attachment)
    }

    fun tryHostShare(
        hostPackageName: String,
        parts: List<FavoritePart>,
    ): FavoriteHostShareDispatchResult {
        val handler = hostShareHandler ?: return FavoriteHostShareDispatchResult.NOT_HANDLED
        return handler(hostPackageName, parts)
    }

    suspend fun awaitDirectAttachmentSent(
        previousInputViewStartCount: Int,
        timeoutMs: Long,
        automationEnabled: Boolean,
    ): Boolean {
        val handler = awaitDirectAttachmentSentHandler ?: return false
        return handler(previousInputViewStartCount, timeoutMs, automationEnabled)
    }

    fun currentInputViewStartCount(): Int {
        val handler = currentInputViewStartCountHandler ?: return 0
        return handler()
    }

    fun requestEmbeddedPickerPermission(
        type: FavoritePickerType,
        onResult: (Boolean) -> Unit,
    ): Boolean {
        val handler = requestEmbeddedPickerPermissionHandler ?: return false
        return handler(type, onResult)
    }

    fun openAttachmentPreview(attachment: FavoritePart.Attachment): Boolean {
        val handler = openAttachmentPreviewHandler ?: return false
        return handler(attachment)
    }

    fun requestShowKeyboard(): Boolean {
        val handler = requestShowKeyboardHandler ?: return false
        return handler()
    }

    fun currentInputTargetPackage(): String? {
        val handler = currentInputTargetPackageHandler ?: return null
        return handler()
    }

    suspend fun activateHostInput(packageName: String): Boolean {
        val handler = activateHostInputHandler ?: return false
        return handler(packageName)
    }

    fun hostInputRestorePolicy(packageName: String): FavoriteHostInputRestorePolicy {
        return hostInputRestorePolicyHandler?.invoke(packageName)
            ?: FavoriteHostInputRestorePolicy()
    }

    fun reportDiagnosticEvent(event: FavoriteDiagnosticEvent): Boolean {
        val handler = diagnosticEventHandler ?: return false
        return runCatching { handler(event) }.getOrDefault(false)
    }
}
