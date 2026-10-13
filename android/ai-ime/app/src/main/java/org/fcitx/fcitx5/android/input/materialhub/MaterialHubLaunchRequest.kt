package org.fcitx.fcitx5.android.input.materialhub

data class MaterialHubLaunchRequest(
    val target: Target,
    val startInComposer: Boolean = false,
    val quickTextEntry: Boolean = false,
    val restorePickerType: String? = null,
    val hostPackageName: String? = null,
    val previewFilePath: String? = null,
    val previewDisplayName: String? = null,
    val previewMimeType: String? = null,
    val previewAttachmentType: String? = null,
    val clipboardReplyText: String? = null,
) {
    enum class Target {
        MATERIAL_HUB,
        AI_REPLY,
    }

    fun withHostPackageName(packageName: String?): MaterialHubLaunchRequest {
        val normalized = packageName?.takeIf { it.isNotBlank() } ?: return this
        if (hostPackageName == normalized) return this
        return copy(hostPackageName = normalized)
    }

    companion object {
        fun materialHub(
            startInComposer: Boolean = false,
            quickTextEntry: Boolean = false,
            restorePickerType: String? = null,
            hostPackageName: String? = null,
            previewFilePath: String? = null,
            previewDisplayName: String? = null,
            previewMimeType: String? = null,
            previewAttachmentType: String? = null,
        ): MaterialHubLaunchRequest {
            return MaterialHubLaunchRequest(
                target = Target.MATERIAL_HUB,
                startInComposer = startInComposer,
                quickTextEntry = quickTextEntry,
                restorePickerType = restorePickerType,
                hostPackageName = hostPackageName,
                previewFilePath = previewFilePath,
                previewDisplayName = previewDisplayName,
                previewMimeType = previewMimeType,
                previewAttachmentType = previewAttachmentType,
            )
        }

        fun aiReply(): MaterialHubLaunchRequest {
            return MaterialHubLaunchRequest(target = Target.AI_REPLY)
        }
    }
}
