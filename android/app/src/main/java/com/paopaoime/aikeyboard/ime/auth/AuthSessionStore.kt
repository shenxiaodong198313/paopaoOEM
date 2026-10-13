package com.paopaoime.aikeyboard.ime.auth

import android.content.Context
import android.widget.Toast
import android.util.Base64
import org.json.JSONObject

object AuthSessionStore {
    private const val PREF_NAME = "auth_session"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_PHONE = "phone"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_AVATAR_URL = "avatar_url"
    private const val KEY_AVATAR_CACHE_PATH = "avatar_cache_path"
    private const val KEY_SYNC_AT_PREFIX = "sync_at_"

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveSession(
        context: Context,
        accessToken: String,
        refreshToken: String,
        userId: String,
        phone: String,
        nickname: String,
        avatarUrl: String = ""
    ) {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_PHONE, phone)
            .putString(KEY_NICKNAME, nickname)
            .putString(KEY_AVATAR_URL, avatarUrl)
            .apply()
    }

    fun updateTokens(context: Context, accessToken: String, refreshToken: String?) {
        val store = prefs(context)
        val currentUserId = store.getString(KEY_USER_ID, "").orEmpty()
        val tokenUserId = extractUserIdFromAccessToken(accessToken)
        if (currentUserId.isNotBlank() && tokenUserId.isNotBlank() && currentUserId != tokenUserId) {
            return
        }
        val editor = store.edit().putString(KEY_ACCESS_TOKEN, accessToken)
        if (!refreshToken.isNullOrBlank()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        if (currentUserId.isBlank() && tokenUserId.isNotBlank()) {
            editor.putString(KEY_USER_ID, tokenUserId)
        }
        editor.apply()
    }

    fun updateUserInfo(
        context: Context,
        userId: String?,
        phone: String?,
        nickname: String?,
        avatarUrl: String?
    ) {
        val editor = prefs(context).edit()
        if (!userId.isNullOrBlank()) editor.putString(KEY_USER_ID, userId)
        if (!phone.isNullOrBlank()) editor.putString(KEY_PHONE, phone)
        if (!nickname.isNullOrBlank()) editor.putString(KEY_NICKNAME, nickname)
        if (!avatarUrl.isNullOrBlank()) editor.putString(KEY_AVATAR_URL, avatarUrl)
        editor.apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        // OEM 版账号由宿主 App 外部接管，内置登录页已移除，
        // 因此这里不再用本地 token 拦截功能入口。
        return true
    }

    fun accessToken(context: Context): String {
        return prefs(context).getString(KEY_ACCESS_TOKEN, "") ?: ""
    }

    fun refreshToken(context: Context): String {
        return prefs(context).getString(KEY_REFRESH_TOKEN, "") ?: ""
    }

    fun userId(context: Context): String {
        return prefs(context).getString(KEY_USER_ID, "") ?: ""
    }

    fun userPhone(context: Context): String {
        return prefs(context).getString(KEY_PHONE, "") ?: ""
    }

    fun userNickname(context: Context): String {
        return prefs(context).getString(KEY_NICKNAME, "") ?: ""
    }

    fun userAvatarUrl(context: Context): String {
        return prefs(context).getString(KEY_AVATAR_URL, "") ?: ""
    }

    fun avatarCachePath(context: Context): String {
        return prefs(context).getString(KEY_AVATAR_CACHE_PATH, "") ?: ""
    }

    fun updateAvatarCachePath(context: Context, localPath: String) {
        prefs(context).edit().putString(KEY_AVATAR_CACHE_PATH, localPath).apply()
    }

    fun updateSyncAt(context: Context, scope: String) {
        if (scope.isBlank()) return
        prefs(context).edit().putLong(KEY_SYNC_AT_PREFIX + scope, System.currentTimeMillis()).apply()
    }

    fun getSyncAt(context: Context, scope: String): Long {
        if (scope.isBlank()) return 0L
        return prefs(context).getLong(KEY_SYNC_AT_PREFIX + scope, 0L)
    }

    fun isSyncExpired(context: Context, scope: String, ttlMillis: Long): Boolean {
        if (ttlMillis <= 0L) return false
        val syncAt = getSyncAt(context, scope)
        if (syncAt <= 0L) return true
        return System.currentTimeMillis() - syncAt > ttlMillis
    }

    fun hasCloudSession(context: Context): Boolean {
        return accessToken(context).isNotBlank()
    }

    fun showHostManagedAccountToast(context: Context, feature: String? = null) {
        val label = feature?.trim().orEmpty()
        val message = if (label.isBlank()) {
            "OEM 版账号由宿主 App 提供，已移除内置登录注册"
        } else {
            "OEM 版${label}账号由宿主 App 提供，已移除内置登录注册"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun handleOemAuthInvalid(context: Context, feature: String? = null) {
        clear(context)
        showHostManagedAccountToast(context, feature)
    }

    private fun extractUserIdFromAccessToken(accessToken: String): String {
        val token = accessToken.trim()
        if (token.isBlank()) return ""
        val chunks = token.split(".")
        if (chunks.size < 2) return ""
        val payloadSegment = chunks[1]
            .replace('-', '+')
            .replace('_', '/')
            .let {
                val padding = (4 - it.length % 4) % 4
                it + "=".repeat(padding)
            }
        val payloadJson = runCatching {
            String(Base64.decode(payloadSegment, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrElse { return "" }
        val payload = runCatching { JSONObject(payloadJson) }.getOrElse { return "" }
        return payload.optString("userId").orEmpty().trim()
    }
}
