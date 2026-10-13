package com.paopaoime.aikeyboard.addon.aireply

import android.content.Context
import android.content.Intent
import com.paopaoime.aikeyboard.materialhub.AiCommunicateLaunchGate
import com.paopaoime.aikeyboard.materialhub.AiCommunicateOcrCacheStore
import com.paopaoime.aikeyboard.materialhub.AiCommunicateScreenCapturePermissionStore
import com.paopaoime.aikeyboard.materialhub.AiScreenCaptureForegroundService
import com.paopaoime.aikeyboard.sdk.core.OemAiReplyAddon

class AiReplyAddonImpl : OemAiReplyAddon {

    override fun tryAcquireLaunch(): Long? = AiCommunicateLaunchGate.tryAcquire()

    override fun releaseLaunch(token: Long): Boolean = AiCommunicateLaunchGate.releaseIfPending(token)

    override fun releaseLaunch() {
        AiCommunicateLaunchGate.release()
    }

    override fun markLaunchPanelVisible() {
        AiCommunicateLaunchGate.markPanelVisible()
    }

    override fun markLaunchPanelHidden() {
        AiCommunicateLaunchGate.markPanelHidden()
    }

    override fun clearSessionCache() {
        AiCommunicateOcrCacheStore.clearSessionCache()
    }

    override fun isScreenCapturePermissionGranted(context: Context): Boolean =
        AiCommunicateScreenCapturePermissionStore.hasUsableGrant(context)

    override fun clearScreenCapturePermission(context: Context) {
        AiCommunicateScreenCapturePermissionStore.clear(context)
    }

    override fun startScreenCaptureForegroundService(context: Context, resultCode: Int, data: Intent?) {
        AiScreenCaptureForegroundService.start(context, resultCode, data)
    }
}
