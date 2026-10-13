package com.paopaoime.aikeyboard.qa

import android.content.Context
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import com.paopaoime.aikeyboard.sdk.core.OemSessionArchiveMode

object InputQaConfig {
    /**
     * 输入质检会话的最终投递策略。
     *
     * 这里与对外的 `sessionArchiveMode` 一一对应，供宿主运行时直接使用。
     */
    enum class QaDataPolicy {
        LOCAL_ONLY,
        CLOUD_ONLY,
        HOST_ONLY,
        DUAL_WRITE;

        companion object {
            fun parse(raw: String?): QaDataPolicy {
                return when (raw?.trim()?.uppercase()) {
                    "LOCAL_ONLY" -> LOCAL_ONLY
                    "HOST_ONLY" -> HOST_ONLY
                    "DUAL_WRITE" -> DUAL_WRITE
                    "CLOUD_ONLY", "CLOUD_UPLOAD" -> CLOUD_ONLY
                    else -> CLOUD_ONLY
                }
            }
        }
    }

    fun resolvePolicy(context: Context): QaDataPolicy {
        val configuredPolicy = when (OemEnterpriseConfig.resolveSessionArchiveMode(context)) {
            OemSessionArchiveMode.LOCAL_ONLY -> QaDataPolicy.LOCAL_ONLY
            OemSessionArchiveMode.CLOUD_ONLY -> QaDataPolicy.CLOUD_ONLY
            OemSessionArchiveMode.HOST_ONLY -> QaDataPolicy.HOST_ONLY
            OemSessionArchiveMode.DUAL_WRITE -> QaDataPolicy.DUAL_WRITE
        }
        return resolveEffectivePolicy(context, configuredPolicy)
    }

    fun resolveEffectivePolicy(context: Context, configuredPolicy: QaDataPolicy): QaDataPolicy {
        return if (OemEnterpriseConfig.resolveQaDistributionRestriction(context) != null) {
            QaDataPolicy.LOCAL_ONLY
        } else {
            configuredPolicy
        }
    }

    /**
     * 对外默认放开应用白名单，避免宿主在初次接入时被名单限制误判。
     * 后续若客户有需要，可再叠加企业级白名单配置。
     */
    val allowedPackages = emptySet<String>()

    /**
     * 默认不启用黑名单，避免首次联调时直接被过滤。
     */
    val blockedPackages = emptySet<String>()

    fun isEnabled(context: Context): Boolean {
        return OemEnterpriseConfig.isQaSessionEnabled(context)
    }
}
