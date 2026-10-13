package com.paopaoime.aikeyboard.addon.developer

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.fragment.DeveloperSettingsFragment

class DeveloperSettingsHostActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply {
            id = R.id.fragment_container
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        setContentView(container)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, DeveloperSettingsFragment())
                .commit()
        }
    }
}
