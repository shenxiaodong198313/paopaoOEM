package com.paopaoime.aikeyboard.ime.data

import android.content.Context

object OpenClawRegressionReporter {
    fun generateSummary(context: Context): String {
        val metrics = OpenClawMetricsStore.snapshot(context)
        val recentAlerts = OpenClawEventStore.listRealEvents(context)
            .asSequence()
            .filter { it.category.startsWith("ops.alert.") }
            .take(20)
            .toList()
        val p1 = recentAlerts.count { it.status == "P1" || it.category.endsWith(".p1") }
        val p2 = recentAlerts.count { it.status == "P2" || it.category.endsWith(".p2") }
        val p3 = recentAlerts.count { it.status == "P3" || it.category.endsWith(".p3") }
        return buildString {
            append("回归摘要：")
            append("总任务${metrics.totalRuns}，成功率${"%.1f".format(metrics.successRate * 100)}%，")
            append("平均耗时${metrics.avgDurationMs}ms，重试率${"%.1f".format(metrics.retryRate * 100)}%，")
            append("超时${metrics.timeoutFailures}次，告警P1/P2/P3=$p1/$p2/$p3。")
        }
    }
}
