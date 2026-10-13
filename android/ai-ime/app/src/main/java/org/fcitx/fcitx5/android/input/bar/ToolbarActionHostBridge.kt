package org.fcitx.fcitx5.android.input.bar

import org.fcitx.fcitx5.android.input.materialhub.MaterialHubLaunchRequest

object ToolbarActionHostBridge {
    const val ACTION_OPEN_CUSTOMIZE_TOOLBAR = "com.paopaoime.aikeyboard.action.OPEN_CUSTOMIZE_TOOLBAR"
    const val EXTRA_OPEN_DESTINATION = "org.fcitx.fcitx5.android.input.bar.OPEN_DESTINATION"
    const val DESTINATION_CUSTOMIZE_TOOLBAR = "customize_toolbar"

    @Volatile
    private var launchVoiceInputHandler: (() -> Boolean)? = null

    @Volatile
    private var launchKeyboardSelectionHandler: (() -> Boolean)? = null

    @Volatile
    private var launchEmojiHandler: (() -> Boolean)? = null

    @Volatile
    private var launchMaterialHubHandler: ((MaterialHubLaunchRequest) -> Boolean)? = null

    @Volatile
    private var launchClipboardHandler: (() -> Boolean)? = null

    @Volatile
    private var launchCustomizeToolbarHandler: (() -> Boolean)? = null

    @Volatile
    private var launchSkinHandler: (() -> Boolean)? = null

    @Volatile
    private var launchTraditionalInputHandler: (() -> Boolean)? = null

    @Volatile
    private var launchKeyboardAdjustHandler: (() -> Boolean)? = null

    @Volatile
    private var launchCustomDrawerHandler: ((String) -> Boolean)? = null

    fun bindLaunchVoiceInput(handler: () -> Boolean) {
        launchVoiceInputHandler = handler
    }

    fun bindLaunchKeyboardSelection(handler: () -> Boolean) {
        launchKeyboardSelectionHandler = handler
    }

    fun bindLaunchEmoji(handler: () -> Boolean) {
        launchEmojiHandler = handler
    }

    fun bindLaunchMaterialHub(handler: (MaterialHubLaunchRequest) -> Boolean) {
        launchMaterialHubHandler = handler
    }

    fun bindLaunchClipboard(handler: () -> Boolean) {
        launchClipboardHandler = handler
    }

    fun bindLaunchCustomizeToolbar(handler: () -> Boolean) {
        launchCustomizeToolbarHandler = handler
    }

    fun bindLaunchSkin(handler: () -> Boolean) {
        launchSkinHandler = handler
    }

    fun bindLaunchTraditionalInput(handler: () -> Boolean) {
        launchTraditionalInputHandler = handler
    }

    fun bindLaunchKeyboardAdjust(handler: () -> Boolean) {
        launchKeyboardAdjustHandler = handler
    }

    fun bindLaunchCustomDrawer(handler: (String) -> Boolean) {
        launchCustomDrawerHandler = handler
    }

    fun unbindLaunchCustomDrawer() {
        launchCustomDrawerHandler = null
    }

    fun unbindAll() {
        launchVoiceInputHandler = null
        launchKeyboardSelectionHandler = null
        launchEmojiHandler = null
        launchMaterialHubHandler = null
        launchClipboardHandler = null
        launchCustomizeToolbarHandler = null
        launchSkinHandler = null
        launchTraditionalInputHandler = null
        launchKeyboardAdjustHandler = null
    }

    fun launchVoiceInput(): Boolean = launchVoiceInputHandler?.invoke() == true
    fun launchKeyboardSelection(): Boolean = launchKeyboardSelectionHandler?.invoke() == true
    fun launchEmoji(): Boolean = launchEmojiHandler?.invoke() == true
    fun launchAiReply(): Boolean = launchMaterialHub(MaterialHubLaunchRequest.aiReply())
    fun launchMaterialHub(): Boolean = launchMaterialHub(MaterialHubLaunchRequest.materialHub())
    fun launchMaterialHub(request: MaterialHubLaunchRequest): Boolean = launchMaterialHubHandler?.invoke(request) == true
    fun launchClipboard(): Boolean = launchClipboardHandler?.invoke() == true
    fun launchCustomizeToolbar(): Boolean = launchCustomizeToolbarHandler?.invoke() == true
    fun launchSkin(): Boolean = launchSkinHandler?.invoke() == true
    fun launchTraditionalInput(): Boolean = launchTraditionalInputHandler?.invoke() == true
    fun launchKeyboardAdjust(): Boolean = launchKeyboardAdjustHandler?.invoke() == true
    fun launchCustomDrawer(drawerId: String): Boolean = launchCustomDrawerHandler?.invoke(drawerId) == true
}
