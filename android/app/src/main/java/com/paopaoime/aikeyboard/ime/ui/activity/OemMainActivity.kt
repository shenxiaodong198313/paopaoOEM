package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.paopaoime.aikeyboard.compat.agent.ui.HostNavigationControl
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.fragment.CustomizeToolbarSettingsFragment
import com.paopaoime.aikeyboard.ime.ui.fragment.InputMethodSettingsFragment
import com.paopaoime.aikeyboard.ime.ui.setup.SetupActivity
import org.fcitx.fcitx5.android.input.bar.ToolbarActionHostBridge

class OemMainActivity : AppCompatActivity(), HostNavigationControl {
    companion object {
        fun createIntent(context: Context, openDestination: String? = null): Intent {
            return Intent(context, OemMainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                openDestination?.let {
                    putExtra(ToolbarActionHostBridge.EXTRA_OPEN_DESTINATION, it)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SetupActivity.shouldShowUp()) {
            startActivity(SetupActivity.createIntent(this))
            finish()
            return
        }
        setContentView(R.layout.activity_oem_main)
        if (savedInstanceState == null) {
            openInitialPage(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!SetupActivity.shouldShowUp()) {
            openInitialPage(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (SetupActivity.shouldShowUp()) {
            startActivity(SetupActivity.createIntent(this))
            finish()
        }
    }

    override fun setBottomNavigationVisibility(visible: Boolean) {
        // OEM 首页没有底部导航，这里保留空实现供设置页复用。
    }

    override fun updateNavMask(alpha: Float) {
        // OEM 首页没有底部导航遮罩，这里保留空实现供旧设置页复用。
    }

    private fun openInitialPage(intent: Intent?) {
        val fragment = when (intent?.getStringExtra(ToolbarActionHostBridge.EXTRA_OPEN_DESTINATION)) {
            ToolbarActionHostBridge.DESTINATION_CUSTOMIZE_TOOLBAR -> CustomizeToolbarSettingsFragment()
            else -> InputMethodSettingsFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
