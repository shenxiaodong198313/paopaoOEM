package com.paopaoime.aikeyboard.ime.voice

import android.Manifest
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.paopaoime.aikeyboard.ime.ui.fragment.VoiceToTextSettingsFragment

class VoiceSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!org.fcitx.fcitx5.android.input.voice.OemVoiceConfig.get(this).enabled) { finish(); return }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction().replace(android.R.id.content, VoiceToTextSettingsFragment()).commit()
    }
}

class VoicePermissionActivity : AppCompatActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("麦克风权限未开启").setMessage("请在系统权限设置中允许此输入法使用麦克风。")
                .setPositiveButton("去设置") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))); finish() }
                .setNegativeButton("取消") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
        } else {
            Toast.makeText(this, if (granted) "已允许麦克风，点击键盘麦克风即可开始" else "未开启麦克风权限", Toast.LENGTH_SHORT).show(); finish()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); if (!org.fcitx.fcitx5.android.input.voice.OemVoiceConfig.get(this).enabled) { finish(); return }; if (savedInstanceState == null) permission.launch(Manifest.permission.RECORD_AUDIO) }
}
