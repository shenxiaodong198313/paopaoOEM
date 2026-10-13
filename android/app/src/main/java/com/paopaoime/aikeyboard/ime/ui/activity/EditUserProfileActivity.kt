package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

class EditUserProfileActivity : AppCompatActivity() {
    private lateinit var ivAvatar: ImageView
    private lateinit var etNickname: android.widget.EditText
    private lateinit var etBio: android.widget.EditText
    private lateinit var btnSave: AppCompatButton

    private var currentAvatarUrl: String = ""
    private var currentAvatarLocalPath: String = ""
    private var draftAvatarLocalPath: String = ""
    private var avatarJob: Job? = null
    private var isSaving = false

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val userId = AuthSessionStore.userId(this)
        val savedPath = UserAvatarHelper.saveCustomAvatar(this, userId, uri)
        if (savedPath.isNullOrBlank()) {
            Toast.makeText(this, "头像保存失败，请重试", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        draftAvatarLocalPath = savedPath
        renderAvatar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthSessionStore.isLoggedIn(this)) {
            AuthSessionStore.showHostManagedAccountToast(this, "编辑资料")
            finish()
            return
        }
        setContentView(R.layout.activity_edit_user_profile)
        ivAvatar = findViewById(R.id.iv_avatar)
        etNickname = findViewById(R.id.et_nickname)
        etBio = findViewById(R.id.et_bio)
        btnSave = findViewById(R.id.btn_save)

        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.avatar_click_area).setOnClickListener { pickAvatarLauncher.launch("image/*") }
        findViewById<ImageView>(R.id.iv_avatar_badge).setOnClickListener { pickAvatarLauncher.launch("image/*") }
        ivAvatar.setOnClickListener { pickAvatarLauncher.launch("image/*") }
        btnSave.setOnClickListener { saveProfile() }

        applySessionState()
        syncProfile()
    }

    override fun onDestroy() {
        avatarJob?.cancel()
        avatarJob = null
        super.onDestroy()
    }

    private fun applySessionState() {
        currentAvatarUrl = AuthSessionStore.userAvatarUrl(this)
        currentAvatarLocalPath = AuthSessionStore.avatarCachePath(this)
        draftAvatarLocalPath = currentAvatarLocalPath
        etNickname.setText(AuthSessionStore.userNickname(this))
        renderAvatar()
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
                AuthSessionStore.updateTokens(this@EditUserProfileActivity, latestAccess, basic.newRefreshToken)
            }
            if (basic.authInvalid) {
                handleAuthInvalid()
                return@launch
            }
            if (basic.success && basic.profile != null) {
                AuthSessionStore.updateUserInfo(
                    this@EditUserProfileActivity,
                    userId = basic.profile.userId,
                    phone = basic.profile.phoneNumber,
                    nickname = basic.profile.nickname,
                    avatarUrl = basic.profile.avatarUrl
                )
                currentAvatarUrl = basic.profile.avatarUrl
                if (etNickname.text.isNullOrBlank()) {
                    etNickname.setText(basic.profile.nickname)
                }
            }

            val detail = withContext(Dispatchers.IO) {
                AuthApiClient.getUserDetailProfile(latestAccess, latestRefresh)
            }
            if (!detail.newAccessToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(this@EditUserProfileActivity, detail.newAccessToken, detail.newRefreshToken)
            }
            if (detail.authInvalid) {
                handleAuthInvalid()
                return@launch
            }
            if (detail.success && detail.profile != null) {
                if (etBio.text.isNullOrBlank()) {
                    etBio.setText(detail.profile.bio)
                }
            }
        }
    }

    private fun renderAvatar() {
        avatarJob?.cancel()
        ivAvatar.scaleType = ImageView.ScaleType.FIT_CENTER
        ivAvatar.setImageResource(R.drawable.img_edit_profile_avatar_default)
        avatarJob = lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                UserAvatarHelper.loadAvatarBitmap(
                    this@EditUserProfileActivity,
                    currentAvatarUrl,
                    draftAvatarLocalPath.ifBlank { currentAvatarLocalPath }
                )
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

    private fun saveProfile() {
        if (isSaving) return
        val nickname = etNickname.text?.toString()?.trim().orEmpty()
        if (nickname.isBlank()) {
            Toast.makeText(this, "请输入昵称", Toast.LENGTH_SHORT).show()
            return
        }
        val bio = etBio.text?.toString()?.trim().orEmpty()
        setSaving(true)
        lifecycleScope.launch {
            var latestAccess = AuthSessionStore.accessToken(this@EditUserProfileActivity)
            var latestRefresh = AuthSessionStore.refreshToken(this@EditUserProfileActivity)
            val basic = withContext(Dispatchers.IO) {
                AuthApiClient.updateProfile(latestAccess, latestRefresh, nickname)
            }
            if (!basic.newAccessToken.isNullOrBlank()) {
                latestAccess = basic.newAccessToken
                latestRefresh = basic.newRefreshToken ?: latestRefresh
                AuthSessionStore.updateTokens(this@EditUserProfileActivity, latestAccess, basic.newRefreshToken)
            }
            if (basic.authInvalid) {
                setSaving(false)
                handleAuthInvalid()
                return@launch
            }
            if (!basic.success || basic.profile == null) {
                setSaving(false)
                Toast.makeText(this@EditUserProfileActivity, basic.message, Toast.LENGTH_SHORT).show()
                return@launch
            }
            AuthSessionStore.updateUserInfo(
                this@EditUserProfileActivity,
                userId = basic.profile.userId,
                phone = basic.profile.phoneNumber,
                nickname = basic.profile.nickname,
                avatarUrl = basic.profile.avatarUrl
            )
            currentAvatarUrl = basic.profile.avatarUrl

            val detail = withContext(Dispatchers.IO) {
                AuthApiClient.updateUserDetailProfile(
                    accessToken = latestAccess,
                    refreshToken = latestRefresh,
                    bio = bio,
                    tags = emptyList(),
                    location = "",
                    age = "",
                    zodiac = "",
                    occupation = ""
                )
            }
            if (!detail.newAccessToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(this@EditUserProfileActivity, detail.newAccessToken, detail.newRefreshToken)
            }
            setSaving(false)
            if (detail.authInvalid) {
                handleAuthInvalid()
                return@launch
            }
            if (!detail.success) {
                Toast.makeText(this@EditUserProfileActivity, detail.message, Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (draftAvatarLocalPath.isNotBlank()) {
                AuthSessionStore.updateAvatarCachePath(this@EditUserProfileActivity, draftAvatarLocalPath)
            }
            Toast.makeText(this@EditUserProfileActivity, "资料已保存", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun setSaving(saving: Boolean) {
        isSaving = saving
        btnSave.isEnabled = !saving
        btnSave.alpha = if (saving) 0.7f else 1f
    }

    private fun handleAuthInvalid() {
        AuthSessionStore.clear(this)
        Toast.makeText(this, "登录态已失效，请重新登录", Toast.LENGTH_SHORT).show()
        finish()
    }
}
