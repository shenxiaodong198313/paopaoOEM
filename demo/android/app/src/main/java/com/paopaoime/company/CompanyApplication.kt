package com.paopaoime.company

import android.util.Log
import com.paopaoime.aikeyboard.sdk.core.OemHostRuntime
import com.paopaoime.aikeyboard.sdk.core.OemImeSdk
import com.paopaoime.aikeyboard.sdk.core.OemSessionArchiveListener
import com.paopaoime.aikeyboard.sdk.core.OemSdkStatus
import org.fcitx.fcitx5.android.FcitxApplication
import java.util.concurrent.Executors

class CompanyApplication : FcitxApplication() {
    override fun onCreate() {
        super.onCreate()
        OemHostRuntime.initialize(this)
        val archiveListener = OemSessionArchiveListener { event ->
            Log.i(
                TAG,
                "收到宿主会话回调 eventId=${event.eventId} sessionId=${event.sessionId} target=${event.targetPackageName}"
            )
            true
        }
        initExecutor.execute {
            runCatching {
                OemImeSdk.initializeFromManifest(
                    application = this,
                    licenseProvider = EnterpriseRemoteLicenseProvider,
                    sessionArchiveListener = archiveListener,
                )
            }.onSuccess {
                lastStatus = it
                lastError = null
                Log.i(
                    TAG,
                    "初始化 OEM SDK 成功 license=${it.license.status} available=${it.availableCapabilities.joinToString(",")}"
                )
            }.onFailure {
                lastStatus = null
                lastError = it.message ?: it.javaClass.simpleName
                Log.e(TAG, "初始化 OEM SDK 失败", it)
            }
        }
    }

    companion object {
        private const val TAG = "CompanyApplication"
        private val initExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "company-oem-init").apply { isDaemon = true }
        }

        @Volatile
        var lastStatus: OemSdkStatus? = null

        @Volatile
        var lastError: String? = null
    }
}
