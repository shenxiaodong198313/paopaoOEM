package com.paopaoime.aikeyboard.ime.data

import java.util.UUID

data class OpenClawActionResult(
    val success: Boolean,
    val message: String
)

object OpenClawActionExecutor {
    fun execute(
        context: android.content.Context,
        actionCode: String,
        level: OpenClawActionLevel,
        actor: String,
        reason: String = ""
    ): OpenClawActionResult {
        val oldPolicy = OpenClawOperationPolicyStore.load(context)
        val newPolicy = when (actionCode) {
            OpenClawPlaybookEngine.ACTION_RELAX_RHYTHM -> oldPolicy.copy(rhythmMode = "relaxed")
            OpenClawPlaybookEngine.ACTION_LIMIT_FLOW -> oldPolicy.copy(
                minIntervalMinutes = (oldPolicy.minIntervalMinutes + 10).coerceAtMost(360),
                maxPendingPerNpc = (oldPolicy.maxPendingPerNpc - 1).coerceAtLeast(1)
            )
            OpenClawPlaybookEngine.ACTION_DISABLE_MEDIA_BUCKET -> oldPolicy.copy(
                mediaBucketPriority = (oldPolicy.mediaBucketPriority - 3).coerceAtLeast(1)
            )
            OpenClawPlaybookEngine.ACTION_APPLY_TEMPLATE_STABLE -> applyTemplate(oldPolicy, "stable")
            OpenClawPlaybookEngine.ACTION_APPLY_TEMPLATE_PEAK -> applyTemplate(oldPolicy, "peak")
            OpenClawPlaybookEngine.ACTION_APPLY_TEMPLATE_HIGH_OUTPUT -> applyTemplate(oldPolicy, "high_output")
            OpenClawPlaybookEngine.ACTION_APPLY_TEMPLATE_NIGHT_SAFE -> applyTemplate(oldPolicy, "night_safe")
            OpenClawPlaybookEngine.ACTION_MANUAL_TAKEOVER -> oldPolicy
            else -> oldPolicy
        }
        if (newPolicy != oldPolicy) {
            OpenClawOperationPolicyStore.save(context, newPolicy)
        }
        val message = when (actionCode) {
            OpenClawPlaybookEngine.ACTION_RELAX_RHYTHM -> "已切换轻松节奏"
            OpenClawPlaybookEngine.ACTION_LIMIT_FLOW -> "已提高频控并收紧排队"
            OpenClawPlaybookEngine.ACTION_DISABLE_MEDIA_BUCKET -> "已降低媒体桶优先级"
            OpenClawPlaybookEngine.ACTION_APPLY_TEMPLATE_STABLE -> "已应用稳态模板"
            OpenClawPlaybookEngine.ACTION_APPLY_TEMPLATE_PEAK -> "已应用高峰模板"
            OpenClawPlaybookEngine.ACTION_APPLY_TEMPLATE_HIGH_OUTPUT -> "已应用高产模板"
            OpenClawPlaybookEngine.ACTION_APPLY_TEMPLATE_NIGHT_SAFE -> "已应用夜间保守模板"
            OpenClawPlaybookEngine.ACTION_MANUAL_TAKEOVER -> "已记录人工接管请求"
            else -> "未知动作，未执行"
        }
        OpenClawAuditStore.append(
            context = context,
            record = OpenClawAuditRecord(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                actor = actor,
                actionCode = actionCode,
                level = level.name,
                detail = reason.ifBlank { message },
                result = if (message == "未知动作，未执行") "ignored" else "applied"
            )
        )
        return OpenClawActionResult(success = message != "未知动作，未执行", message = message)
    }

    fun applyTemplate(
        policy: OpenClawOperationPolicy,
        template: String
    ): OpenClawOperationPolicy {
        return when (template) {
            "stable" -> policy.copy(
                rhythmMode = "relaxed",
                minIntervalMinutes = policy.minIntervalMinutes.coerceAtLeast(35),
                textBucketPriority = 5,
                mediaBucketPriority = 4,
                publishBucketPriority = 7,
                maxPendingPerNpc = 1
            )
            "peak" -> policy.copy(
                rhythmMode = "standard",
                minIntervalMinutes = policy.minIntervalMinutes.coerceAtMost(20),
                textBucketPriority = 7,
                mediaBucketPriority = 8,
                publishBucketPriority = 9,
                maxPendingPerNpc = 3
            )
            "high_output" -> policy.copy(
                rhythmMode = "high_output",
                minIntervalMinutes = policy.minIntervalMinutes.coerceAtMost(12),
                textBucketPriority = 8,
                mediaBucketPriority = 9,
                publishBucketPriority = 9,
                maxPendingPerNpc = 4
            )
            "night_safe" -> policy.copy(
                rhythmMode = "relaxed",
                minIntervalMinutes = policy.minIntervalMinutes.coerceAtLeast(45),
                textBucketPriority = 4,
                mediaBucketPriority = 3,
                publishBucketPriority = 6,
                maxPendingPerNpc = 1
            )
            else -> policy
        }
    }
}
