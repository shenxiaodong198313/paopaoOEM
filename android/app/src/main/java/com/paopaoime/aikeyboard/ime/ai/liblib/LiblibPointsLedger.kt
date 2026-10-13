package com.paopaoime.aikeyboard.ime.ai.liblib

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LiblibPointsRecord(
    val timestamp: Long,
    val consumeType: String,
    val taskDetail: String,
    val pointsCost: Int,
    val taskId: String,
    val accountBalance: Int
)

object LiblibPointsLedger {
    private const val PREFS_NAME = "liblib_points_ledger"
    private const val KEY_RECORDS = "records"
    private const val KEY_BALANCE = "latest_balance"
    private const val KEY_BALANCE_USER_ID = "latest_balance_user_id"
    private const val MAX_RECORDS = 200

    fun appendRecord(
        context: Context,
        consumeType: String,
        taskDetail: String,
        pointsCost: Int,
        taskId: String,
        accountBalance: Int
    ) {
        if (taskId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentUserId = context
            .getSharedPreferences("auth_session", Context.MODE_PRIVATE)
            .getString("user_id", "")
            .orEmpty()
        val source = prefs.getString(KEY_RECORDS, "[]").orEmpty()
        val array = runCatching { JSONArray(source) }.getOrDefault(JSONArray())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("taskId") == taskId) {
                item.put("consumeType", consumeType)
                item.put("taskDetail", taskDetail)
                item.put("pointsCost", pointsCost)
                item.put("accountBalance", accountBalance)
                item.put("timestamp", System.currentTimeMillis())
                item.put("userId", currentUserId)
                prefs.edit()
                    .putString(KEY_RECORDS, array.toString())
                    .putInt(KEY_BALANCE, accountBalance)
                    .putString(KEY_BALANCE_USER_ID, currentUserId)
                    .apply()
                return
            }
        }
        val obj = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("consumeType", consumeType)
            put("taskDetail", taskDetail)
            put("pointsCost", pointsCost)
            put("taskId", taskId)
            put("accountBalance", accountBalance)
            put("userId", currentUserId)
        }
        val newArray = JSONArray()
        newArray.put(obj)
        for (i in 0 until array.length()) {
            if (newArray.length() >= MAX_RECORDS) break
            newArray.put(array.optJSONObject(i))
        }
        prefs.edit()
            .putString(KEY_RECORDS, newArray.toString())
            .putInt(KEY_BALANCE, accountBalance)
            .putString(KEY_BALANCE_USER_ID, currentUserId)
            .apply()
    }

    fun latestBalance(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentUserId = context
            .getSharedPreferences("auth_session", Context.MODE_PRIVATE)
            .getString("user_id", "")
            .orEmpty()
        val balanceUserId = prefs.getString(KEY_BALANCE_USER_ID, "").orEmpty()
        if (currentUserId.isNotBlank() && balanceUserId.isNotBlank() && currentUserId != balanceUserId) {
            return 0
        }
        return prefs.getInt(KEY_BALANCE, 0)
    }

    fun updateBalance(context: Context, accountBalance: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_BALANCE, accountBalance).apply()
    }

    fun records(context: Context): List<LiblibPointsRecord> {
        return recordsForUser(context, null)
    }

    fun recordsForUser(context: Context, userId: String?): List<LiblibPointsRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val source = prefs.getString(KEY_RECORDS, "[]").orEmpty()
        val array = runCatching { JSONArray(source) }.getOrDefault(JSONArray())
        val result = ArrayList<LiblibPointsRecord>(array.length())
        val targetUserId = userId.orEmpty()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val recordUserId = item.optString("userId", "").orEmpty()
            if (targetUserId.isNotBlank() && recordUserId.isNotBlank() && recordUserId != targetUserId) {
                continue
            }
            result.add(
                LiblibPointsRecord(
                    timestamp = item.optLong("timestamp"),
                    consumeType = item.optString("consumeType"),
                    taskDetail = item.optString("taskDetail"),
                    pointsCost = item.optInt("pointsCost"),
                    taskId = item.optString("taskId"),
                    accountBalance = item.optInt("accountBalance")
                )
            )
        }
        return result.sortedByDescending { it.timestamp }
    }

    fun hasRecord(context: Context, taskId: String): Boolean {
        if (taskId.isBlank()) return false
        return records(context).any { it.taskId == taskId }
    }
}
