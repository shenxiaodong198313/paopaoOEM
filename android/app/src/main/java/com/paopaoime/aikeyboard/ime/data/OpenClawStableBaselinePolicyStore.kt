package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import org.json.JSONObject

data class OpenClawStableBaselinePolicy(
    val minSuccessRate: Double = 0.90,
    val maxTimeoutFailures: Int = 2
)

object OpenClawStableBaselinePolicyStore {
    private const val PREFS_NAME = "openclaw_stable_baseline_policy"
    private const val KEY_POLICY = "policy"

    fun load(context: Context): OpenClawStableBaselinePolicy {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_POLICY, null)
        if (raw.isNullOrBlank()) return OpenClawStableBaselinePolicy()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return OpenClawStableBaselinePolicy()
        return OpenClawStableBaselinePolicy(
            minSuccessRate = obj.optDouble("minSuccessRate", 0.90),
            maxTimeoutFailures = obj.optInt("maxTimeoutFailures", 2)
        )
    }

    fun save(context: Context, policy: OpenClawStableBaselinePolicy) {
        val obj = JSONObject()
            .put("minSuccessRate", policy.minSuccessRate)
            .put("maxTimeoutFailures", policy.maxTimeoutFailures)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_POLICY, obj.toString())
            .apply()
    }
}
