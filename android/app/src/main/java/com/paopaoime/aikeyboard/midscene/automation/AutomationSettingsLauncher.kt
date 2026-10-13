package com.paopaoime.aikeyboard.midscene.automation

import android.content.Context
import android.content.Intent
import roro.stellar.manager.MainActivity

object AutomationSettingsLauncher {
    fun createIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java)
    }

    fun launch(context: Context) {
        context.startActivity(createIntent(context))
    }
}
