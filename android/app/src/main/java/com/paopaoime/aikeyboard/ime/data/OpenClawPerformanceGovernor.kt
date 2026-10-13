package com.paopaoime.aikeyboard.ime.data

import android.app.ActivityManager
import android.content.Context

data class OpenClawGovernorDecision(
    val cooldownScale: Double,
    val rhythmMode: String,
    val textPriority: Int,
    val mediaPriority: Int,
    val publishPriority: Int,
    val reason: String
)

object OpenClawPerformanceGovernor {
    fun evaluate(
        context: Context,
        basePolicy: OpenClawOperationPolicy,
        metrics: OpenClawMetricsSnapshot,
        agentExclusiveActive: Boolean
    ): OpenClawGovernorDecision {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClass = am?.memoryClass ?: 256
        var cooldownScale = 1.0
        var rhythm = basePolicy.rhythmMode
        var textPriority = basePolicy.textBucketPriority
        var mediaPriority = basePolicy.mediaBucketPriority
        var publishPriority = basePolicy.publishBucketPriority
        var reason = "baseline"

        if (agentExclusiveActive) {
            cooldownScale = 1.35
            rhythm = "relaxed"
            reason = "agent_exclusive"
        } else if (metrics.failedRate >= 0.45 || metrics.timeoutFailures >= 3) {
            cooldownScale = 1.5
            rhythm = "relaxed"
            mediaPriority = (mediaPriority - 2).coerceIn(1, 10)
            publishPriority = (publishPriority - 1).coerceIn(1, 10)
            reason = "failure_guard"
        } else if (metrics.retryRate >= 0.35 || metrics.avgDurationMs > 65_000L) {
            cooldownScale = 1.25
            mediaPriority = (mediaPriority - 1).coerceIn(1, 10)
            reason = "retry_guard"
        } else if (memoryClass >= 512 && basePolicy.rhythmMode == "high_output") {
            cooldownScale = 0.85
            textPriority = (textPriority + 1).coerceIn(1, 10)
            mediaPriority = (mediaPriority + 1).coerceIn(1, 10)
            reason = "high_memory_boost"
        }

        return OpenClawGovernorDecision(
            cooldownScale = cooldownScale,
            rhythmMode = rhythm,
            textPriority = textPriority,
            mediaPriority = mediaPriority,
            publishPriority = publishPriority,
            reason = reason
        )
    }
}
