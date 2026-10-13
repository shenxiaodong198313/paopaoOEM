package org.fcitx.fcitx5.android.input.status

object StatusAreaHostBridge {
    @Volatile
    private var launchKeyEffectsHandler: (() -> Boolean)? = null

    fun bindLaunchKeyEffects(handler: () -> Boolean) { launchKeyEffectsHandler = handler }
    fun unbindLaunchKeyEffects() { launchKeyEffectsHandler = null }
    fun launchKeyEffects(): Boolean = launchKeyEffectsHandler?.invoke() == true

    @Volatile
    private var launchSkinChannelHandler: (() -> Boolean)? = null

    @Volatile
    private var launchInputMethodSettingsHandler: (() -> Boolean)? = null

    fun bindLaunchSkinChannel(handler: () -> Boolean) {
        launchSkinChannelHandler = handler
    }

    fun bindLaunchInputMethodSettings(handler: () -> Boolean) {
        launchInputMethodSettingsHandler = handler
    }

    fun unbindLaunchSkinChannel() {
        launchSkinChannelHandler = null
    }

    fun unbindLaunchInputMethodSettings() {
        launchInputMethodSettingsHandler = null
    }

    fun launchSkinChannel(): Boolean {
        val handler = launchSkinChannelHandler ?: return false
        return handler()
    }

    fun launchInputMethodSettings(): Boolean {
        val handler = launchInputMethodSettingsHandler ?: return false
        return handler()
    }
}
