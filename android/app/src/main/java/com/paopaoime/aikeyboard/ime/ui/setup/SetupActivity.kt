
package com.paopaoime.aikeyboard.ime.ui.setup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.databinding.ActivitySetupBinding
import com.paopaoime.aikeyboard.ime.ui.activity.OemMainActivity
import com.paopaoime.aikeyboard.ime.utils.InputMethodUtil
import com.paopaoime.aikeyboard.ime.utils.notificationManager

class SetupActivity : FragmentActivity() {

    private val viewModel: SetupViewModel by viewModels()
    private var launchedSettings = false

    private lateinit var binding: ActivitySetupBinding
    private val imeDisplayName: String by lazy { getString(R.string.oem_ime_display_name) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySetupBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val sysBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            windowInsets
        }
        setContentView(binding.root)
        binding.setupTitle.text = imeDisplayName
        binding.btnEnable.text = getString(R.string.setup_step1_enable, imeDisplayName)
        binding.btnEnable.setOnClickListener {
            InputMethodUtil.startSettingsActivity(this)
        }
        binding.btnSelect.text = getString(R.string.setup_step2_select, imeDisplayName)
        binding.btnSelect.setOnClickListener {
            InputMethodUtil.showPicker()
        }

        createNotificationChannel()
        syncButtons()
    }

    override fun onStart() {
        super.onStart()
        if (!launchedSettings && InputMethodUtil.isSelected()) {
            launchedSettings = true
            startActivity(Intent(this, OemMainActivity::class.java))
            finish()
            return
        }
        syncButtons()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!launchedSettings && InputMethodUtil.isSelected()) {
            launchedSettings = true
            startActivity(Intent(this, OemMainActivity::class.java))
            finish()
            return
        }
        syncButtons()
    }

    private fun syncButtons() {
        val enabled = InputMethodUtil.isEnabled()
        binding.btnEnable.isEnabled = !enabled
        binding.btnSelect.isEnabled = enabled
        binding.btnEnable.setBackgroundResource(if (enabled) R.drawable.bg_btn_secondary_gray else R.drawable.bg_btn_primary_blue)
        binding.btnEnable.setTextColor(if (enabled) 0xFF212121.toInt() else 0xFFFFFFFF.toInt())
        binding.btnSelect.setBackgroundResource(if (enabled) R.drawable.bg_btn_primary_blue else R.drawable.bg_btn_secondary_gray)
        binding.btnSelect.setTextColor(if (enabled) 0xFFFFFFFF.toInt() else 0xFF212121.toInt())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getText(R.string.setup_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = CHANNEL_ID }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onPause() {
        if (!InputMethodUtil.isSelected())
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sdk_launcher_transparent)
                .setContentTitle(imeDisplayName)
                .setContentText(getText(R.string.setup_keyboard))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, javaClass),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setAutoCancel(true)
                .build()
                .let { notificationManager.notify(NOTIFY_ID, it) }
        super.onPause()
    }

    override fun onResume() {
        notificationManager.cancel(NOTIFY_ID)
        super.onResume()
        syncButtons()
    }

    companion object {
        private const val CHANNEL_ID = "setup"
        private const val NOTIFY_ID = 233

        fun createIntent(context: android.content.Context): Intent {
            return Intent(context, SetupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }

        fun shouldShowUp() = !(InputMethodUtil.isEnabled() && InputMethodUtil.isSelected())
    }
}
