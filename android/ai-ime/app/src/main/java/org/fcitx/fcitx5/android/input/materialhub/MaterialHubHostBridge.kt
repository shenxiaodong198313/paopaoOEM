package org.fcitx.fcitx5.android.input.materialhub

object MaterialHubHostBridge {
    @Volatile
    private var launchHandler: ((MaterialHubLaunchRequest) -> Boolean)? = null

    @Volatile
    private var availabilityProvider: ((MaterialHubLaunchRequest.Target) -> Boolean)? = null

    fun bindLaunch(handler: (MaterialHubLaunchRequest) -> Boolean) {
        launchHandler = handler
    }

    fun unbindLaunch() {
        launchHandler = null
    }

    fun bindAvailability(provider: (MaterialHubLaunchRequest.Target) -> Boolean) {
        availabilityProvider = provider
    }

    fun unbindAvailability() {
        availabilityProvider = null
    }

    fun isAvailable(target: MaterialHubLaunchRequest.Target): Boolean {
        return availabilityProvider?.invoke(target) == true
    }

    fun launch(request: MaterialHubLaunchRequest): Boolean {
        val handler = launchHandler ?: return false
        return handler(request)
    }

    fun launchMaterialHub(): Boolean {
        return launch(MaterialHubLaunchRequest.materialHub())
    }

    fun launchAiReply(): Boolean {
        return launch(MaterialHubLaunchRequest.aiReply())
    }
}
