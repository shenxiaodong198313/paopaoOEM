package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.paopaoime.aikeyboard.compat.agent.ui.HostNavigationControl
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.fragment.CustomizeToolbarSettingsFragment
import com.paopaoime.aikeyboard.ime.ui.setup.SetupActivity
import org.fcitx.fcitx5.android.input.bar.ToolbarActionHostBridge

class CustomizeToolbarActivity : AppCompatActivity(), HostNavigationControl {
    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(ToolbarActionHostBridge.ACTION_OPEN_CUSTOMIZE_TOOLBAR).apply {
                `package` = context.packageName
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
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
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CustomizeToolbarSettingsFragment())
                .commit()
        }
    }

    override fun setBottomNavigationVisibility(visible: Boolean) {
        // 独立页面没有底部导航，这里保留空实现给通用设置页调用。
    }

    override fun updateNavMask(alpha: Float) {
        // 独立页面没有底部导航遮罩，这里保留空实现给通用设置页调用。
    }
}
