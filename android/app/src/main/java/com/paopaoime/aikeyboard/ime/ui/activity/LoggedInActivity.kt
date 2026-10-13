package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoggedInActivity : AppCompatActivity() {
    private lateinit var tvUserName: TextView
    private lateinit var tvUserPhone: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logged_in)

        findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            finish()
        }
        tvUserName = findViewById(R.id.tv_user_name)
        tvUserPhone = findViewById(R.id.tv_user_phone)
        renderUserInfo()

        findViewById<AppCompatButton>(R.id.btn_logout).setOnClickListener {
            AuthSessionStore.clear(this)
            Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        syncProfileFromServer()
    }

    private fun renderUserInfo() {
        val nickname = AuthSessionStore.userNickname(this)
        val phone = AuthSessionStore.userPhone(this)
        tvUserName.text = if (nickname.isNotBlank()) nickname else "已登录用户"
        tvUserPhone.text = if (phone.isNotBlank()) "手机号 $phone" else "未绑定手机号（微信登录）"
    }

    private fun syncProfileFromServer() {
        if (!AuthSessionStore.isLoggedIn(this)) return
        val accessToken = AuthSessionStore.accessToken(this)
        val refreshToken = AuthSessionStore.refreshToken(this)
        if (accessToken.isBlank()) return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                AuthApiClient.getProfile(accessToken, refreshToken)
            }
            val nextAccessToken = result.newAccessToken
            val nextRefreshToken = result.newRefreshToken
            if (!nextAccessToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(this@LoggedInActivity, nextAccessToken, nextRefreshToken)
            }
            if (result.authInvalid) {
                AuthSessionStore.clear(this@LoggedInActivity)
                Toast.makeText(this@LoggedInActivity, "登录态已失效，请重新登录", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            if (!result.success || result.profile == null) return@launch
            AuthSessionStore.updateUserInfo(
                context = this@LoggedInActivity,
                userId = result.profile.userId,
                phone = result.profile.phoneNumber,
                nickname = result.profile.nickname,
                avatarUrl = result.profile.avatarUrl
            )
            renderUserInfo()
        }
    }
}
