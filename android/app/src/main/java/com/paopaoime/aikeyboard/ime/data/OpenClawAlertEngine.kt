package com.paopaoime.aikeyboard.ime.data

import android.content.Context

data class OpenClawAlert(
    val level: String,
    val code: String,
    val title: String,
    val message: String
)

object OpenClawAlertEngine {
    private const val PREFS_NAME = "openclaw_alert_engine"
    private const val KEY_LAST_P1_AT = "last_p1_at"
    private const val KEY_LAST_P2_AT = "last_p2_at"
    private const val KEY_LAST_P3_AT = "last_p3_at"
    private const val ALERT_COOLDOWN_MS = 5 * 60_000L

    fun evaluate(context: Context, snapshot: OpenClawMetricsSnapshot): List<OpenClawAlert> {
        val alerts = mutableListOf<OpenClawAlert>()
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val p1Due = now - prefs.getLong(KEY_LAST_P1_AT, 0L) >= ALERT_COOLDOWN_MS
        val p2Due = now - prefs.getLong(KEY_LAST_P2_AT, 0L) >= ALERT_COOLDOWN_MS
        val p3Due = now - prefs.getLong(KEY_LAST_P3_AT, 0L) >= ALERT_COOLDOWN_MS

        if (p1Due && (snapshot.timeoutFailures >= 3 || (snapshot.totalRuns >= 6 && snapshot.failedRate >= 0.5))) {
            alerts.add(
                OpenClawAlert(
                    level = "P1",
                    code = "PUBLISH_BLOCKED",
                    title = "发布链路高危告警",
                    message = "回调超时或失败率过高，建议切换轻松节奏并检查 Agent 链路"
                )
            )
            prefs.edit().putLong(KEY_LAST_P1_AT, now).apply()
        }
        if (p2Due && snapshot.totalRuns >= 8 && snapshot.retryRate >= 0.35) {
            alerts.add(
                OpenClawAlert(
                    level = "P2",
                    code = "RETRY_SURGE",
                    title = "重试率升高告警",
                    message = "重试率持续偏高，建议降低发布频率并检查平台风控与网络稳定性"
                )
            )
            prefs.edit().putLong(KEY_LAST_P2_AT, now).apply()
        }
        if (p3Due && (snapshot.avgDurationMs >= 70_000L || snapshot.queueRejectCount >= 5)) {
            alerts.add(
                OpenClawAlert(
                    level = "P3",
                    code = "PERFORMANCE_JITTER",
                    title = "性能波动提示",
                    message = "平均耗时或排队拒绝偏高，建议下调媒体桶优先级并提高冷却间隔"
                )
            )
            prefs.edit().putLong(KEY_LAST_P3_AT, now).apply()
        }
        return alerts
    }
}
