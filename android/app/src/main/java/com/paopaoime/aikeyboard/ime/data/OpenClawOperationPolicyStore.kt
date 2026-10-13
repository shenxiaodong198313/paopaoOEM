package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class OpenClawOperationPolicy(
    val enabled: Boolean = true,
    val allowedChannelCodes: Set<String> = OperationChannel.entries.map { it.code }.toSet(),
    val startHour: Int = 8,
    val endHour: Int = 23,
    val minIntervalMinutes: Int = 30,
    val dailyQuota: Int = 8,
    val rhythmMode: String = "standard",
    val maxPendingPerNpc: Int = 2,
    val textBucketPriority: Int = 5,
    val mediaBucketPriority: Int = 6,
    val publishBucketPriority: Int = 8,
    val agentExclusiveMode: Boolean = true,
    val assetReuseEnabled: Boolean = true,
    val disableAgentTaskExecution: Boolean = true
)

data class OpenClawPolicyCheck(
    val allowed: Boolean,
    val failCode: String = "",
    val reason: String = ""
)

object OpenClawOperationPolicyStore {
    private const val PREFS_NAME = "openclaw_operation_policy"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ALLOWED_CHANNELS = "allowed_channels"
    private const val KEY_START_HOUR = "start_hour"
    private const val KEY_END_HOUR = "end_hour"
    private const val KEY_MIN_INTERVAL = "min_interval_minutes"
    private const val KEY_DAILY_QUOTA = "daily_quota"
    private const val KEY_RHYTHM_MODE = "rhythm_mode"
    private const val KEY_MAX_PENDING_PER_NPC = "max_pending_per_npc"
    private const val KEY_TEXT_BUCKET_PRIORITY = "text_bucket_priority"
    private const val KEY_MEDIA_BUCKET_PRIORITY = "media_bucket_priority"
    private const val KEY_PUBLISH_BUCKET_PRIORITY = "publish_bucket_priority"
    private const val KEY_AGENT_EXCLUSIVE_MODE = "agent_exclusive_mode"
    private const val KEY_ASSET_REUSE_ENABLED = "asset_reuse_enabled"
    private const val KEY_DISABLE_AGENT_TASK_EXECUTION = "disable_agent_task_execution"
    private const val KEY_EXEC_HISTORY = "execution_history"
    private const val MAX_HISTORY = 300

    fun load(context: Context): OpenClawOperationPolicy {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allChannels = OperationChannel.entries.map { it.code }.toSet()
        val defaultChannels = JSONArray(allChannels.toList()).toString()
        val channelRaw = prefs.getString(KEY_ALLOWED_CHANNELS, defaultChannels)
        val channels = parseStringSet(channelRaw).ifEmpty { allChannels }
        val disableAgentTaskExecution = if (prefs.contains(KEY_DISABLE_AGENT_TASK_EXECUTION)) {
            prefs.getBoolean(KEY_DISABLE_AGENT_TASK_EXECUTION, true)
        } else {
            true
        }
        return OpenClawOperationPolicy(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            allowedChannelCodes = channels,
            startHour = prefs.getInt(KEY_START_HOUR, 8).coerceIn(0, 23),
            endHour = prefs.getInt(KEY_END_HOUR, 23).coerceIn(0, 23),
            minIntervalMinutes = prefs.getInt(KEY_MIN_INTERVAL, 30).coerceIn(0, 360),
            dailyQuota = prefs.getInt(KEY_DAILY_QUOTA, 8).coerceIn(1, 100),
            rhythmMode = prefs.getString(KEY_RHYTHM_MODE, "standard").orEmpty().ifBlank { "standard" },
            maxPendingPerNpc = prefs.getInt(KEY_MAX_PENDING_PER_NPC, 2).coerceIn(1, 8),
            textBucketPriority = prefs.getInt(KEY_TEXT_BUCKET_PRIORITY, 5).coerceIn(1, 10),
            mediaBucketPriority = prefs.getInt(KEY_MEDIA_BUCKET_PRIORITY, 6).coerceIn(1, 10),
            publishBucketPriority = prefs.getInt(KEY_PUBLISH_BUCKET_PRIORITY, 8).coerceIn(1, 10),
            agentExclusiveMode = prefs.getBoolean(KEY_AGENT_EXCLUSIVE_MODE, true),
            assetReuseEnabled = prefs.getBoolean(KEY_ASSET_REUSE_ENABLED, true),
            disableAgentTaskExecution = disableAgentTaskExecution
        )
    }

    fun save(context: Context, policy: OpenClawOperationPolicy) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val channels = policy.allowedChannelCodes.ifEmpty { OperationChannel.entries.map { it.code }.toSet() }
        prefs.edit()
            .putBoolean(KEY_ENABLED, policy.enabled)
            .putString(KEY_ALLOWED_CHANNELS, JSONArray(channels.toList()).toString())
            .putInt(KEY_START_HOUR, policy.startHour.coerceIn(0, 23))
            .putInt(KEY_END_HOUR, policy.endHour.coerceIn(0, 23))
            .putInt(KEY_MIN_INTERVAL, policy.minIntervalMinutes.coerceIn(0, 360))
            .putInt(KEY_DAILY_QUOTA, policy.dailyQuota.coerceIn(1, 100))
            .putString(KEY_RHYTHM_MODE, policy.rhythmMode)
            .putInt(KEY_MAX_PENDING_PER_NPC, policy.maxPendingPerNpc.coerceIn(1, 8))
            .putInt(KEY_TEXT_BUCKET_PRIORITY, policy.textBucketPriority.coerceIn(1, 10))
            .putInt(KEY_MEDIA_BUCKET_PRIORITY, policy.mediaBucketPriority.coerceIn(1, 10))
            .putInt(KEY_PUBLISH_BUCKET_PRIORITY, policy.publishBucketPriority.coerceIn(1, 10))
            .putBoolean(KEY_AGENT_EXCLUSIVE_MODE, policy.agentExclusiveMode)
            .putBoolean(KEY_ASSET_REUSE_ENABLED, policy.assetReuseEnabled)
            .putBoolean(KEY_DISABLE_AGENT_TASK_EXECUTION, policy.disableAgentTaskExecution)
            .apply()
    }

    fun evaluate(
        context: Context,
        channel: OperationChannel,
        nowMs: Long = System.currentTimeMillis()
    ): OpenClawPolicyCheck {
        val policy = load(context)
        if (!policy.enabled) return OpenClawPolicyCheck(allowed = true)
        if (!policy.allowedChannelCodes.contains(channel.code)) {
            return OpenClawPolicyCheck(
                allowed = false,
                failCode = OpenClawFailCode.POLICY_BLOCKED.code,
                reason = "策略限制：${channel.displayName} 未开启"
            )
        }
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        if (!isWithinHourWindow(hour, policy.startHour, policy.endHour)) {
            return OpenClawPolicyCheck(
                allowed = false,
                failCode = OpenClawFailCode.POLICY_BLOCKED.code,
                reason = "策略限制：当前不在执行时间窗 ${policy.startHour}:00-${policy.endHour}:59"
            )
        }
        val records = loadHistory(context)
        val currentDay = dayKey(nowMs)
        val dayCount = records.count { dayKey(it.timestamp) == currentDay }
        if (dayCount >= policy.dailyQuota) {
            return OpenClawPolicyCheck(
                allowed = false,
                failCode = OpenClawFailCode.POLICY_BLOCKED.code,
                reason = "策略限制：已达到今日配额 ${policy.dailyQuota} 条"
            )
        }
        val lastChannelTs = records
            .asReversed()
            .firstOrNull { it.channelCode == channel.code }
            ?.timestamp
            ?: 0L
        if (policy.minIntervalMinutes > 0 && lastChannelTs > 0L) {
            val deltaMin = ((nowMs - lastChannelTs) / 60000L).toInt()
            if (deltaMin < policy.minIntervalMinutes) {
                return OpenClawPolicyCheck(
                    allowed = false,
                    failCode = OpenClawFailCode.POLICY_BLOCKED.code,
                    reason = "策略限制：${channel.displayName} 频控未到，需间隔 ${policy.minIntervalMinutes} 分钟"
                )
            }
        }
        return OpenClawPolicyCheck(allowed = true)
    }

    fun recordExecution(context: Context, channel: OperationChannel, timestamp: Long = System.currentTimeMillis()) {
        val history = loadHistory(context).toMutableList()
        history.add(OpenClawExecutionRecord(channel.code, timestamp))
        val keep = history.takeLast(MAX_HISTORY)
        val array = JSONArray()
        keep.forEach { item ->
            array.put(
                JSONObject()
                    .put("channelCode", item.channelCode)
                    .put("timestamp", item.timestamp)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EXEC_HISTORY, array.toString())
            .apply()
    }

    private fun isWithinHourWindow(hour: Int, start: Int, end: Int): Boolean {
        return if (start <= end) {
            hour in start..end
        } else {
            hour >= start || hour <= end
        }
    }

    private fun parseStringSet(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val set = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotBlank()) set.add(value)
        }
        return set
    }

    private fun loadHistory(context: Context): List<OpenClawExecutionRecord> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EXEC_HISTORY, null)
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val list = mutableListOf<OpenClawExecutionRecord>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(
                OpenClawExecutionRecord(
                    channelCode = obj.optString("channelCode"),
                    timestamp = obj.optLong("timestamp")
                )
            )
        }
        return list
    }

    private fun dayKey(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val year = cal.get(Calendar.YEAR)
        val day = cal.get(Calendar.DAY_OF_YEAR)
        return "$year-$day"
    }

    private data class OpenClawExecutionRecord(
        val channelCode: String,
        val timestamp: Long
    )
}
