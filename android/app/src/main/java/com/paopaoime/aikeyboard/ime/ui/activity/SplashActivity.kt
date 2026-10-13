package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.setup.SetupActivity
import org.fcitx.fcitx5.android.input.bar.ToolbarActionHostBridge

class SplashActivity : AppCompatActivity() {
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContentView(R.layout.activity_splash)
        navigateHome()
    }

    private fun navigateHome() {
        if (hasNavigated || isFinishing) return
        hasNavigated = true
        val intent = if (SetupActivity.shouldShowUp()) {
            SetupActivity.createIntent(this@SplashActivity)
        } else {
            OemMainActivity.createIntent(
                this@SplashActivity,
                intent.getStringExtra(ToolbarActionHostBridge.EXTRA_OPEN_DESTINATION)
            ).apply {
                action = Intent.ACTION_MAIN
            }
        }
        startActivity(intent)
        finish()
    }
}
