package com.paopaoime.aikeyboard.ime.data

data class OpenClawThresholdSuggestion(
    val recommendedP1FailureRate: Double,
    val recommendedP2RetryRate: Double,
    val recommendedP3AvgDurationMs: Long,
    val reason: String
)

object OpenClawThresholdTuner {
    fun suggest(snapshot: OpenClawMetricsSnapshot): OpenClawThresholdSuggestion {
        val p1 = if (snapshot.totalRuns >= 20) 0.45 else 0.55
        val p2 = if (snapshot.retryRate > 0.35) 0.30 else 0.35
        val p3 = when {
            snapshot.avgDurationMs <= 0L -> 70_000L
            snapshot.avgDurationMs > 80_000L -> 75_000L
            snapshot.avgDurationMs < 45_000L -> 60_000L
            else -> 70_000L
        }
        val reason = when {
            snapshot.failedRate > 0.45 -> "失败率较高，建议降低P1触发阈值并增强保护"
            snapshot.retryRate > 0.35 -> "重试率偏高，建议提前触发P2限流"
            snapshot.avgDurationMs > 70_000L -> "耗时偏高，建议收紧P3性能阈值"
            else -> "当前指标平稳，维持默认阈值窗口"
        }
        return OpenClawThresholdSuggestion(
            recommendedP1FailureRate = p1,
            recommendedP2RetryRate = p2,
            recommendedP3AvgDurationMs = p3,
            reason = reason
        )
    }
}
