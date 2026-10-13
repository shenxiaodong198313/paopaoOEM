package org.fcitx.fcitx5.android.input.status

import android.graphics.drawable.Drawable

data class CustomDrawerEntry(
    val id: String,
    val title: String,
    val icon: Drawable?,
)

object CustomDrawerHostBridge {
    private var listHandler: (() -> List<CustomDrawerEntry>)? = null
    private var launchHandler: ((String) -> Boolean)? = null
    private var changeHandler: (() -> Unit)? = null

    fun bindList(handler: () -> List<CustomDrawerEntry>) {
        listHandler = handler
    }

    fun unbindList() {
        listHandler = null
    }

    fun bindLaunch(handler: (String) -> Boolean) {
        launchHandler = handler
    }

    fun unbindLaunch() {
        launchHandler = null
    }

    fun bindRegistryChanged(handler: () -> Unit) {
        changeHandler = handler
    }

    fun unbindRegistryChanged() {
        changeHandler = null
    }

    fun entries(): List<CustomDrawerEntry> = listHandler?.invoke().orEmpty()

    fun launch(drawerId: String): Boolean = launchHandler?.invoke(drawerId) == true

    fun notifyRegistryChanged() {
        changeHandler?.invoke()
    }
}
