package com.paopaoime.company

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = ImeHostGateway.createSetupIntent(this)
        if (intent == null) {
            Toast.makeText(this, R.string.input_method_entry_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        runCatching {
            startActivity(intent)
        }.onFailure {
            Toast.makeText(this, it.message ?: getString(R.string.open_settings_failed), Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
