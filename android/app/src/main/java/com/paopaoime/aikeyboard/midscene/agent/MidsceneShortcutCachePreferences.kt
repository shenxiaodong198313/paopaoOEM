package com.paopaoime.aikeyboard.midscene.agent

import android.content.Context
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore

data class MidsceneShortcutCacheSnapshot(
    val qwenExplicitCacheEnabled: Boolean,
    val midCacheEnabled: Boolean,
    val qwenCacheVersion: Int,
    val midCacheVersion: Int,
    val qwenLastClearedAtMs: Long,
    val midLastClearedAtMs: Long,
    val qwenEligibleRequestCount: Int,
    val qwenCacheHitCount: Int,
    val qwenExplicitCacheHitCount: Int,
    val qwenImplicitCacheHitCount: Int,
    val qwenTotalCachedTokens: Long,
    val qwenSavedBubbleTotal: Int,
    val midEligibleRequestCount: Int,
    val midCacheHitCount: Int,
    val lastObservedAtMs: Long,
    val lastTaskKey: String,
    val lastTaskTitle: String,
    val lastQwenCacheType: String,
    val lastQwenCachedTokens: Long,
    val lastQwenCacheHit: Boolean,
    val lastQwenSavedBubbleCount: Int,
    val lastMidCacheMode: String,
    val lastMidCachePhase: String,
    val lastMidCacheHit: Boolean
)

object MidsceneShortcutCachePreferences {
    private const val prefsName = "midscene_shortcut_cache_prefs"
    private const val keyCacheEnabledLegacy = "shortcut_cache_enabled"
    private const val keyQwenExplicitCacheEnabled = "shortcut_qwen_explicit_cache_enabled"
    private const val keyMidCacheEnabled = "shortcut_mid_cache_enabled"
    private const val keyQwenCacheVersion = "shortcut_qwen_cache_version"
    private const val keyMidCacheVersion = "shortcut_mid_cache_version"
    private const val keyQwenLastClearedAtMs = "shortcut_qwen_cache_last_cleared_at_ms"
    private const val keyMidLastClearedAtMs = "shortcut_mid_cache_last_cleared_at_ms"
    private const val keyQwenEligibleRequestCount = "shortcut_cache_qwen_eligible_request_count"
    private const val keyQwenCacheHitCount = "shortcut_cache_qwen_hit_count"
    private const val keyQwenExplicitCacheHitCount = "shortcut_cache_qwen_explicit_hit_count"
    private const val keyQwenImplicitCacheHitCount = "shortcut_cache_qwen_implicit_hit_count"
    private const val keyQwenTotalCachedTokens = "shortcut_cache_qwen_total_cached_tokens"
    private const val keyQwenSavedBubbleTotal = "shortcut_cache_qwen_saved_bubble_total"
    private const val keyMidEligibleRequestCount = "shortcut_cache_mid_eligible_request_count"
    private const val keyMidCacheHitCount = "shortcut_cache_mid_hit_count"
    private const val keyLastObservedAtMs = "shortcut_cache_last_observed_at_ms"
    private const val keyLastTaskKey = "shortcut_cache_last_task_key"
    private const val keyLastTaskTitle = "shortcut_cache_last_task_title"
    private const val keyLastQwenCacheType = "shortcut_cache_last_qwen_cache_type"
    private const val keyLastQwenCachedTokens = "shortcut_cache_last_qwen_cached_tokens"
    private const val keyLastQwenCacheHit = "shortcut_cache_last_qwen_cache_hit"
    private const val keyLastQwenSavedBubbleCount = "shortcut_cache_last_qwen_saved_bubble_count"
    private const val keyLastMidCacheMode = "shortcut_cache_last_mid_cache_mode"
    private const val keyLastMidCachePhase = "shortcut_cache_last_mid_cache_phase"
    private const val keyLastMidCacheHit = "shortcut_cache_last_mid_cache_hit"

    private fun scopedKey(context: Context, key: String): String {
        val userId = AuthSessionStore.userId(context).trim().ifBlank { "guest" }
        return "${userId}_$key"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun boolCompat(context: Context, key: String, legacyKey: String): Boolean {
        val prefs = prefs(context)
        val scoped = scopedKey(context, key)
        return if (prefs.contains(scoped)) {
            prefs.getBoolean(scoped, true)
        } else {
            prefs.getBoolean(scopedKey(context, legacyKey), true)
        }
    }

    fun snapshot(context: Context): MidsceneShortcutCacheSnapshot {
        val prefs = prefs(context)
        return MidsceneShortcutCacheSnapshot(
            qwenExplicitCacheEnabled = boolCompat(
                context,
                keyQwenExplicitCacheEnabled,
                keyCacheEnabledLegacy
            ),
            midCacheEnabled = boolCompat(
                context,
                keyMidCacheEnabled,
                keyCacheEnabledLegacy
            ),
            qwenCacheVersion = prefs.getInt(scopedKey(context, keyQwenCacheVersion), 0).coerceAtLeast(0),
            midCacheVersion = prefs.getInt(scopedKey(context, keyMidCacheVersion), 0).coerceAtLeast(0),
            qwenLastClearedAtMs = prefs.getLong(scopedKey(context, keyQwenLastClearedAtMs), 0L)
                .coerceAtLeast(0L),
            midLastClearedAtMs = prefs.getLong(scopedKey(context, keyMidLastClearedAtMs), 0L)
                .coerceAtLeast(0L),
            qwenEligibleRequestCount = prefs.getInt(scopedKey(context, keyQwenEligibleRequestCount), 0)
                .coerceAtLeast(0),
            qwenCacheHitCount = prefs.getInt(scopedKey(context, keyQwenCacheHitCount), 0)
                .coerceAtLeast(0),
            qwenExplicitCacheHitCount = prefs.getInt(
                scopedKey(context, keyQwenExplicitCacheHitCount),
                0
            ).coerceAtLeast(0),
            qwenImplicitCacheHitCount = prefs.getInt(
                scopedKey(context, keyQwenImplicitCacheHitCount),
                0
            ).coerceAtLeast(0),
            qwenTotalCachedTokens = prefs.getLong(scopedKey(context, keyQwenTotalCachedTokens), 0L)
                .coerceAtLeast(0L),
            qwenSavedBubbleTotal = prefs.getInt(scopedKey(context, keyQwenSavedBubbleTotal), 0)
                .coerceAtLeast(0),
            midEligibleRequestCount = prefs.getInt(scopedKey(context, keyMidEligibleRequestCount), 0)
                .coerceAtLeast(0),
            midCacheHitCount = prefs.getInt(scopedKey(context, keyMidCacheHitCount), 0)
                .coerceAtLeast(0),
            lastObservedAtMs = prefs.getLong(scopedKey(context, keyLastObservedAtMs), 0L)
                .coerceAtLeast(0L),
            lastTaskKey = prefs.getString(scopedKey(context, keyLastTaskKey), "").orEmpty(),
            lastTaskTitle = prefs.getString(scopedKey(context, keyLastTaskTitle), "").orEmpty(),
            lastQwenCacheType = prefs.getString(scopedKey(context, keyLastQwenCacheType), "").orEmpty(),
            lastQwenCachedTokens = prefs.getLong(scopedKey(context, keyLastQwenCachedTokens), 0L)
                .coerceAtLeast(0L),
            lastQwenCacheHit = prefs.getBoolean(scopedKey(context, keyLastQwenCacheHit), false),
            lastQwenSavedBubbleCount = prefs.getInt(
                scopedKey(context, keyLastQwenSavedBubbleCount),
                0
            ).coerceAtLeast(0),
            lastMidCacheMode = prefs.getString(scopedKey(context, keyLastMidCacheMode), "").orEmpty(),
            lastMidCachePhase = prefs.getString(scopedKey(context, keyLastMidCachePhase), "").orEmpty(),
            lastMidCacheHit = prefs.getBoolean(scopedKey(context, keyLastMidCacheHit), false)
        )
    }

    fun setQwenExplicitCacheEnabled(context: Context, enabled: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(scopedKey(context, keyQwenExplicitCacheEnabled), enabled)
            .apply()
    }

    fun setMidCacheEnabled(context: Context, enabled: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(scopedKey(context, keyMidCacheEnabled), enabled)
            .apply()
    }

    fun buildEffectiveTaskKey(context: Context, baseTaskKey: String): String {
        return baseTaskKey.trim().ifBlank { "shortcut_task" }
    }

    fun clearQwenRuntimeCache(context: Context): MidsceneShortcutCacheSnapshot {
        val now = System.currentTimeMillis()
        val current = snapshot(context)
        prefs(context)
            .edit()
            .putInt(scopedKey(context, keyQwenCacheVersion), current.qwenCacheVersion + 1)
            .putLong(scopedKey(context, keyQwenLastClearedAtMs), now)
            .apply()
        return snapshot(context)
    }

    fun clearMidRuntimeCache(context: Context): MidsceneShortcutCacheSnapshot {
        val now = System.currentTimeMillis()
        val current = snapshot(context)
        prefs(context)
            .edit()
            .putInt(scopedKey(context, keyMidCacheVersion), current.midCacheVersion + 1)
            .putLong(scopedKey(context, keyMidLastClearedAtMs), now)
            .apply()
        return snapshot(context)
    }

    fun recordObservation(
        context: Context,
        taskKey: String,
        taskTitle: String,
        qwenCacheEligible: Boolean,
        qwenCacheHit: Boolean,
        qwenCacheType: String,
        qwenCachedTokens: Long,
        qwenSavedBubbleCount: Int,
        midCacheEligible: Boolean,
        midCacheHit: Boolean,
        midCacheMode: String,
        midCachePhase: String
    ) {
        if (!qwenCacheEligible && !midCacheEligible) return
        val current = snapshot(context)
        val normalizedQwenCacheType = qwenCacheType.trim().lowercase()
        val normalizedTaskKey = taskKey.trim()
        val normalizedMidPhase = midCachePhase.trim()
        val editor = prefs(context)
            .edit()
            .putInt(
                scopedKey(context, keyQwenEligibleRequestCount),
                current.qwenEligibleRequestCount + if (qwenCacheEligible) 1 else 0
            )
            .putInt(
                scopedKey(context, keyQwenCacheHitCount),
                current.qwenCacheHitCount + if (qwenCacheHit) 1 else 0
            )
            .putInt(
                scopedKey(context, keyQwenExplicitCacheHitCount),
                current.qwenExplicitCacheHitCount + if (qwenCacheHit && normalizedQwenCacheType != "implicit") 1 else 0
            )
            .putInt(
                scopedKey(context, keyQwenImplicitCacheHitCount),
                current.qwenImplicitCacheHitCount + if (qwenCacheHit && normalizedQwenCacheType == "implicit") 1 else 0
            )
            .putLong(
                scopedKey(context, keyQwenTotalCachedTokens),
                current.qwenTotalCachedTokens + qwenCachedTokens.coerceAtLeast(0L)
            )
            .putInt(
                scopedKey(context, keyQwenSavedBubbleTotal),
                current.qwenSavedBubbleTotal + qwenSavedBubbleCount.coerceAtLeast(0)
            )
            .putInt(
                scopedKey(context, keyMidEligibleRequestCount),
                current.midEligibleRequestCount + if (midCacheEligible) 1 else 0
            )
            .putInt(
                scopedKey(context, keyMidCacheHitCount),
                current.midCacheHitCount + if (midCacheHit) 1 else 0
            )
            .putLong(scopedKey(context, keyLastObservedAtMs), System.currentTimeMillis())
            .putString(scopedKey(context, keyLastTaskKey), normalizedTaskKey)
            .putString(scopedKey(context, keyLastTaskTitle), taskTitle.trim())
            .putString(scopedKey(context, keyLastQwenCacheType), normalizedQwenCacheType)
            .putLong(scopedKey(context, keyLastQwenCachedTokens), qwenCachedTokens.coerceAtLeast(0L))
            .putBoolean(scopedKey(context, keyLastQwenCacheHit), qwenCacheHit)
            .putInt(
                scopedKey(context, keyLastQwenSavedBubbleCount),
                qwenSavedBubbleCount.coerceAtLeast(0)
            )
            .putString(scopedKey(context, keyLastMidCacheMode), midCacheMode.trim())
            .putString(scopedKey(context, keyLastMidCachePhase), normalizedMidPhase)
            .putBoolean(scopedKey(context, keyLastMidCacheHit), midCacheHit)
        
        editor.apply()
    }
}
