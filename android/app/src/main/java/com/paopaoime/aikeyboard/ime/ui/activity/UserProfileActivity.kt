package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.auth.UserAvatarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserProfileActivity : AppCompatActivity() {
    private data class ProfileUiState(
        var nickname: String = "",
        var phone: String = "",
        var avatarUrl: String = "",
        var avatarLocalPath: String = "",
        var bio: String = ""
    )

    private lateinit var ivAvatar: ImageView
    private lateinit var layoutCompleteBanner: View
    private lateinit var tvProfileName: TextView
    private lateinit var tvAboutMe: TextView
    private var avatarJob: Job? = null
    private val uiState = ProfileUiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthSessionStore.isLoggedIn(this)) {
            AuthSessionStore.showHostManagedAccountToast(this, "用户资料")
            finish()
            return
        }
        setContentView(R.layout.activity_user_profile)
        ivAvatar = findViewById(R.id.iv_avatar)
        layoutCompleteBanner = findViewById(R.id.layout_complete_banner)
        tvProfileName = findViewById(R.id.tv_profile_name)
        tvAboutMe = findViewById(R.id.tv_about_me)

        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_complete_action).setOnClickListener { openEditPage() }
        findViewById<AppCompatButton>(R.id.btn_logout).setOnClickListener { logout() }

        applySessionState()
    }

    override fun onResume() {
        super.onResume()
        if (!AuthSessionStore.isLoggedIn(this)) {
            finish()
            return
        }
        syncProfile()
    }

    override fun onDestroy() {
        avatarJob?.cancel()
        avatarJob = null
        super.onDestroy()
    }

    private fun applySessionState() {
        uiState.nickname = AuthSessionStore.userNickname(this)
        uiState.phone = AuthSessionStore.userPhone(this)
        uiState.avatarUrl = AuthSessionStore.userAvatarUrl(this)
        uiState.avatarLocalPath = AuthSessionStore.avatarCachePath(this)
        renderProfile()
    }

    private fun syncProfile() {
        val accessToken = AuthSessionStore.accessToken(this)
        val refreshToken = AuthSessionStore.refreshToken(this)
        if (accessToken.isBlank()) return
        lifecycleScope.launch {
            var latestAccess = accessToken
            var latestRefresh = refreshToken
            val basic = withContext(Dispatchers.IO) {
                AuthApiClient.getProfile(latestAccess, latestRefresh)
            }
            if (!basic.newAccessToken.isNullOrBlank()) {
                latestAccess = basic.newAccessToken
                latestRefresh = basic.newRefreshToken ?: latestRefresh
                AuthSessionStore.updateTokens(this@UserProfileActivity, latestAccess, basic.newRefreshToken)
            }
            if (basic.authInvalid) {
                handleAuthInvalid()
                return@launch
            }
            if (basic.success && basic.profile != null) {
                AuthSessionStore.updateUserInfo(
                    this@UserProfileActivity,
                    userId = basic.profile.userId,
                    phone = basic.profile.phoneNumber,
                    nickname = basic.profile.nickname,
                    avatarUrl = basic.profile.avatarUrl
                )
                uiState.nickname = basic.profile.nickname
                uiState.phone = basic.profile.phoneNumber
                uiState.avatarUrl = basic.profile.avatarUrl
                uiState.avatarLocalPath = AuthSessionStore.avatarCachePath(this@UserProfileActivity)
            }

            val detail = withContext(Dispatchers.IO) {
                AuthApiClient.getUserDetailProfile(latestAccess, latestRefresh)
            }
            if (!detail.newAccessToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(this@UserProfileActivity, detail.newAccessToken, detail.newRefreshToken)
            }
            if (detail.authInvalid) {
                handleAuthInvalid()
                return@launch
            }
            if (detail.success && detail.profile != null) {
                uiState.bio = detail.profile.bio
            }
            renderProfile()
        }
    }

    private fun renderProfile() {
        tvProfileName.text = displayName()
        val bio = uiState.bio.trim()
        tvAboutMe.text = if (bio.isBlank()) "没啥可介绍的" else bio
        tvAboutMe.setTextColor(if (bio.isBlank()) 0x42000000 else 0xE5000000.toInt())
        layoutCompleteBanner.visibility = if (isIncompleteProfile()) View.VISIBLE else View.GONE
        renderAvatarImages()
    }

    private fun renderAvatarImages() {
        avatarJob?.cancel()
        ivAvatar.scaleType = ImageView.ScaleType.FIT_CENTER
        ivAvatar.setImageResource(R.drawable.img_edit_profile_avatar_default)
        val avatarUrl = uiState.avatarUrl
        val localPath = uiState.avatarLocalPath
        avatarJob = lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                UserAvatarHelper.loadAvatarBitmap(this@UserProfileActivity, avatarUrl, localPath)
            }
            if (bitmap != null) {
                applyBitmap(bitmap)
            }
        }
    }

    private fun applyBitmap(bitmap: Bitmap) {
        ivAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
        ivAvatar.setImageBitmap(bitmap)
    }

    private fun displayName(): String {
        return uiState.nickname.ifBlank {
            uiState.phone.ifBlank { "企业输入法用户" }
        }
    }

    private fun isIncompleteProfile(): Boolean {
        return uiState.bio.isBlank()
    }

    private fun openEditPage() {
        startActivity(Intent(this, EditUserProfileActivity::class.java))
    }

    private fun logout() {
        AuthSessionStore.clear(this)
        Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun handleAuthInvalid() {
        AuthSessionStore.clear(this)
        Toast.makeText(this, "登录态已失效，请重新登录", Toast.LENGTH_SHORT).show()
        finish()
    }
}
