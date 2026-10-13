package com.paopaoime.aikeyboard.ime.data

enum class OpenClawActionLevel {
    AUTO,
    SEMI_AUTO,
    MANUAL
}

data class OpenClawActionSuggestion(
    val actionCode: String,
    val title: String,
    val level: OpenClawActionLevel
)

object OpenClawPlaybookEngine {
    const val ACTION_RELAX_RHYTHM = "RELAX_RHYTHM"
    const val ACTION_LIMIT_FLOW = "LIMIT_FLOW"
    const val ACTION_DISABLE_MEDIA_BUCKET = "DISABLE_MEDIA_BUCKET"
    const val ACTION_MANUAL_TAKEOVER = "MANUAL_TAKEOVER"
    const val ACTION_APPLY_TEMPLATE_STABLE = "APPLY_TEMPLATE_STABLE"
    const val ACTION_APPLY_TEMPLATE_PEAK = "APPLY_TEMPLATE_PEAK"
    const val ACTION_APPLY_TEMPLATE_HIGH_OUTPUT = "APPLY_TEMPLATE_HIGH_OUTPUT"
    const val ACTION_APPLY_TEMPLATE_NIGHT_SAFE = "APPLY_TEMPLATE_NIGHT_SAFE"

    fun suggestByAlertCode(alertCode: String): List<OpenClawActionSuggestion> {
        return when (alertCode) {
            "PUBLISH_BLOCKED" -> listOf(
                OpenClawActionSuggestion(ACTION_RELAX_RHYTHM, "切换到轻松节奏", OpenClawActionLevel.AUTO),
                OpenClawActionSuggestion(ACTION_LIMIT_FLOW, "提高频控并收紧排队", OpenClawActionLevel.SEMI_AUTO),
                OpenClawActionSuggestion(ACTION_MANUAL_TAKEOVER, "人工接管当前发布任务", OpenClawActionLevel.MANUAL)
            )
            "RETRY_SURGE" -> listOf(
                OpenClawActionSuggestion(ACTION_LIMIT_FLOW, "快速限流降低重试洪峰", OpenClawActionLevel.AUTO),
                OpenClawActionSuggestion(ACTION_APPLY_TEMPLATE_STABLE, "切换稳态模板", OpenClawActionLevel.SEMI_AUTO)
            )
            "PERFORMANCE_JITTER" -> listOf(
                OpenClawActionSuggestion(ACTION_DISABLE_MEDIA_BUCKET, "降低媒体桶优先级", OpenClawActionLevel.AUTO),
                OpenClawActionSuggestion(ACTION_APPLY_TEMPLATE_STABLE, "切换夜间保守模板", OpenClawActionLevel.SEMI_AUTO)
            )
            else -> listOf(
                OpenClawActionSuggestion(ACTION_APPLY_TEMPLATE_STABLE, "应用稳态模板", OpenClawActionLevel.SEMI_AUTO),
                OpenClawActionSuggestion(ACTION_MANUAL_TAKEOVER, "人工复核并接管", OpenClawActionLevel.MANUAL)
            )
        }
    }
}
