package com.paopaoime.aikeyboard.addon.material

import android.content.Context
import android.content.Intent
import com.paopaoime.aikeyboard.materialhub.MaterialHubActivity
import com.paopaoime.aikeyboard.materialhub.MaterialHubLaunchAuthorizer
import com.paopaoime.aikeyboard.materialhub.OemDrawerCapabilities
import com.paopaoime.aikeyboard.materialhub.OemRemoteMaterialAutoSync
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk
import com.paopaoime.aikeyboard.sdk.core.OemMaterialHubAddon
import com.paopaoime.aikeyboard.sdk.core.OemMaterialHubLaunchOptions
import org.fcitx.fcitx5.android.input.materialhub.MaterialHubLaunchRequest

class MaterialAddonImpl : OemMaterialHubAddon {

    override fun createAuthorizedLaunchIntent(context: Context, options: OemMaterialHubLaunchOptions): Intent? {
        val target = when (options.target) {
            OemMaterialHubLaunchOptions.TARGET_MATERIAL_HUB -> MaterialHubLaunchRequest.Target.MATERIAL_HUB
            OemMaterialHubLaunchOptions.TARGET_AI_REPLY -> MaterialHubLaunchRequest.Target.AI_REPLY
            else -> return null
        }
        val request = MaterialHubLaunchRequest(
            target = target,
            startInComposer = options.startInComposer,
            quickTextEntry = options.quickTextEntry,
            restorePickerType = options.restorePickerType,
            hostPackageName = options.hostPackageName,
            previewFilePath = options.previewFilePath,
            previewDisplayName = options.previewDisplayName,
            previewMimeType = options.previewMimeType,
            previewAttachmentType = options.previewAttachmentType,
            clipboardReplyText = options.clipboardReplyText,
        )
        val launchToken = MaterialHubLaunchAuthorizer.issue()
        return MaterialHubActivity.createIntent(context, request, launchToken)
    }

    override fun createCustomDrawerLaunchIntent(context: Context, drawerId: String): Intent? {
        if (OemImeSdk.findCustomDrawer(drawerId) == null) return null
        val launchToken = MaterialHubLaunchAuthorizer.issue()
        return MaterialHubActivity.createCustomDrawerIntent(context, drawerId, launchToken)
    }

    override fun closeCurrentDrawer() {
        MaterialHubActivity.currentInstance?.closeMaterialHub()
    }

    override fun installDrawerKitRuntime(context: Context) {
        val appContext = context.applicationContext ?: context
        OemImeSdk.setAiReplyCapabilitiesProvider(OemDrawerCapabilities.aiReply(appContext))
        OemImeSdk.setMaterialCapabilitiesProvider(OemDrawerCapabilities.material(appContext))
    }

    override fun startRemoteMaterialAutoSync(context: Context, reason: String) {
        OemRemoteMaterialAutoSync.warmUp(context, reason = reason)
    }

    override fun isRemoteMaterialAutoSyncRunning(): Boolean = OemRemoteMaterialAutoSync.isSyncInProgress()

    override fun stopRemoteMaterialAutoSync() {
        OemRemoteMaterialAutoSync.stop()
    }
}
