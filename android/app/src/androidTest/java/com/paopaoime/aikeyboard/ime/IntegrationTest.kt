package com.paopaoime.aikeyboard.ime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager

@RunWith(AndroidJUnit4::class)
class IntegrationTest {
    @Test
    fun testAiInputMethodServiceExists() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val serviceName = "com.paopaoime.aikeyboard.ime.service.AiInputMethodService"
        val intent = Intent().setClassName(appContext, serviceName)
        val packageManager = appContext.packageManager
        val resolveInfo = packageManager.resolveService(intent, PackageManager.MATCH_ALL)
        Assert.assertNotNull("AiInputMethodService should be resolvable", resolveInfo)
    }

    @Test
    fun testSettingsActivityExists() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val activityName = "org.fcitx.fcitx5.android.ui.main.MainActivity"
        val intent = Intent().setClassName(appContext, activityName)
        val packageManager = appContext.packageManager
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_ALL)
        Assert.assertNotNull("Fcitx5 Settings Activity should be resolvable", resolveInfo)
    }
}
