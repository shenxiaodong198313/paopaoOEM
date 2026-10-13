package com.paopaoime.aikeyboard.ime.service

import org.fcitx.fcitx5.android.input.favorite.FavoriteHostInputRestorePolicy

internal enum class FavoriteHostInputActivationStrategy {
    NONE,
    WECOM_CHAT_EDITOR,
    IDLEFISH_CHAT_EDITOR,
}

internal enum class FavoriteAttachmentSendStrategy {
    UNSUPPORTED,
    INPUT_CONTENT,
    INPUT_CONTENT_WITH_HOST_SHARE_FALLBACK,
    HOST_SHARE,
}

internal enum class FavoriteDirectAttachmentCompletionStrategy {
    NONE,
    WECHAT_CONFIRMATION,
    COMMIT_ACCEPTED,
}

internal enum class FavoriteMaterialHubPresentationStrategy {
    ACTIVITY_OVERLAY,
    ATTACHED_IME_DIALOG,
}

/** Shared fallback for hosts whose real editor connection is lost after an overlay closes. */
private fun preservedHostInputPolicy() = FavoriteHostInputRestorePolicy(
    reuseActiveInputSession = true,
    dispatchTextBeforeOverlayClose = true,
)

internal enum class FavoriteHostApp(
    val packageName: String?,
    val inputActivationStrategy: FavoriteHostInputActivationStrategy,
    val inputRestorePolicy: FavoriteHostInputRestorePolicy,
    val attachmentSendStrategy: FavoriteAttachmentSendStrategy,
    val directAttachmentCompletionStrategy: FavoriteDirectAttachmentCompletionStrategy,
    val materialHubPresentationStrategy: FavoriteMaterialHubPresentationStrategy,
    val shareActivityClassName: String? = null,
    val shareMimeTypePrefixes: Set<String> = emptySet(),
) {
    WECHAT(
        packageName = "com.tencent.mm",
        inputActivationStrategy = FavoriteHostInputActivationStrategy.NONE,
        inputRestorePolicy = FavoriteHostInputRestorePolicy(
            restoreAttempts = 3,
        ),
        attachmentSendStrategy = FavoriteAttachmentSendStrategy.INPUT_CONTENT,
        directAttachmentCompletionStrategy = FavoriteDirectAttachmentCompletionStrategy.WECHAT_CONFIRMATION,
        materialHubPresentationStrategy = FavoriteMaterialHubPresentationStrategy.ACTIVITY_OVERLAY,
    ),
    QQ(
        packageName = "com.tencent.mobileqq",
        inputActivationStrategy = FavoriteHostInputActivationStrategy.NONE,
        inputRestorePolicy = FavoriteHostInputRestorePolicy(),
        attachmentSendStrategy = FavoriteAttachmentSendStrategy.HOST_SHARE,
        directAttachmentCompletionStrategy = FavoriteDirectAttachmentCompletionStrategy.NONE,
        materialHubPresentationStrategy = FavoriteMaterialHubPresentationStrategy.ACTIVITY_OVERLAY,
        shareActivityClassName = "com.tencent.mobileqq.activity.JumpActivity",
    ),
    WECOM(
        packageName = "com.tencent.wework",
        inputActivationStrategy = FavoriteHostInputActivationStrategy.WECOM_CHAT_EDITOR,
        inputRestorePolicy = FavoriteHostInputRestorePolicy(
            reuseActiveInputSession = true,
            requiresHostInputActivation = true,
            restoreAttempts = 2,
            dispatchTextBeforeOverlayClose = true,
        ),
        attachmentSendStrategy = FavoriteAttachmentSendStrategy.HOST_SHARE,
        directAttachmentCompletionStrategy = FavoriteDirectAttachmentCompletionStrategy.NONE,
        materialHubPresentationStrategy = FavoriteMaterialHubPresentationStrategy.ATTACHED_IME_DIALOG,
        shareActivityClassName = "com.tencent.wework.launch.AppSchemeLaunchActivity",
    ),
    WHATSAPP(
        packageName = "com.whatsapp",
        inputActivationStrategy = FavoriteHostInputActivationStrategy.NONE,
        inputRestorePolicy = FavoriteHostInputRestorePolicy(),
        attachmentSendStrategy = FavoriteAttachmentSendStrategy.INPUT_CONTENT_WITH_HOST_SHARE_FALLBACK,
        directAttachmentCompletionStrategy = FavoriteDirectAttachmentCompletionStrategy.COMMIT_ACCEPTED,
        materialHubPresentationStrategy = FavoriteMaterialHubPresentationStrategy.ACTIVITY_OVERLAY,
        shareActivityClassName = "com.whatsapp.contact.ui.picker.ExternalShareAlias",
    ),
    IDLEFISH(
        packageName = "com.taobao.idlefish",
        inputActivationStrategy = FavoriteHostInputActivationStrategy.IDLEFISH_CHAT_EDITOR,
        inputRestorePolicy = FavoriteHostInputRestorePolicy(
            reuseActiveInputSession = true,
            restoreAttempts = 2,
        ),
        attachmentSendStrategy = FavoriteAttachmentSendStrategy.UNSUPPORTED,
        directAttachmentCompletionStrategy = FavoriteDirectAttachmentCompletionStrategy.NONE,
        materialHubPresentationStrategy = FavoriteMaterialHubPresentationStrategy.ATTACHED_IME_DIALOG,
    ),
    DOUYIN(
        packageName = "com.ss.android.ugc.aweme",
        inputActivationStrategy = FavoriteHostInputActivationStrategy.NONE,
        inputRestorePolicy = preservedHostInputPolicy(),
        attachmentSendStrategy = FavoriteAttachmentSendStrategy.HOST_SHARE,
        directAttachmentCompletionStrategy = FavoriteDirectAttachmentCompletionStrategy.NONE,
        materialHubPresentationStrategy = FavoriteMaterialHubPresentationStrategy.ATTACHED_IME_DIALOG,
        shareActivityClassName = "com.ss.android.ugc.aweme.share.ui.SystemShareNewActivity",
        shareMimeTypePrefixes = setOf("image/", "video/"),
    ),
    XIAOHONGSHU(
        packageName = "com.xingin.xhs",
        inputActivationStrategy = FavoriteHostInputActivationStrategy.NONE,
        inputRestorePolicy = preservedHostInputPolicy(),
        attachmentSendStrategy = FavoriteAttachmentSendStrategy.UNSUPPORTED,
        directAttachmentCompletionStrategy = FavoriteDirectAttachmentCompletionStrategy.NONE,
        materialHubPresentationStrategy = FavoriteMaterialHubPresentationStrategy.ATTACHED_IME_DIALOG,
    ),
    KUAISHOU(
        packageName = "com.smile.gifmaker",
        inputActivationStrategy = FavoriteHostInputActivationStrategy.NONE,
        inputRestorePolicy = preservedHostInputPolicy(),
        attachmentSendStrategy = FavoriteAttachmentSendStrategy.HOST_SHARE,
        directAttachmentCompletionStrategy = FavoriteDirectAttachmentCompletionStrategy.NONE,
        materialHubPresentationStrategy = FavoriteMaterialHubPresentationStrategy.ATTACHED_IME_DIALOG,
        shareActivityClassName =
            "com.yxcorp.gifshow.reminder.thirdpartyshare.ThirdPartyShareFriendsActivity",
        shareMimeTypePrefixes = setOf("image/", "video/"),
    ),
    GENERIC(
        packageName = null,
        inputActivationStrategy = FavoriteHostInputActivationStrategy.NONE,
        inputRestorePolicy = preservedHostInputPolicy(),
        attachmentSendStrategy = FavoriteAttachmentSendStrategy.UNSUPPORTED,
        directAttachmentCompletionStrategy = FavoriteDirectAttachmentCompletionStrategy.NONE,
        materialHubPresentationStrategy = FavoriteMaterialHubPresentationStrategy.ATTACHED_IME_DIALOG,
    );

    companion object {
        fun fromPackageName(packageName: String?): FavoriteHostApp {
            val normalized = packageName?.trim().orEmpty()
            if (normalized.isEmpty()) return GENERIC
            return entries.firstOrNull { host ->
                host.packageName.equals(normalized, ignoreCase = true)
            } ?: GENERIC
        }
    }
}
