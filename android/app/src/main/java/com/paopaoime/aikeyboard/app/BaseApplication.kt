package com.paopaoime.aikeyboard.app

import com.paopaoime.aikeyboard.sdk.core.OemHostRuntime
import dagger.hilt.android.HiltAndroidApp
import org.fcitx.fcitx5.android.FcitxApplication

@HiltAndroidApp
class BaseApplication : FcitxApplication() {
    override fun onCreate() {
        super.onCreate()
        // OEM 已移除 Midscene 设置入口，这里固定开启霓虹边框偏好，避免后台 overlay 重建后丢灯。
        OemHostRuntime.initialize(this, enableNeonOverlay = true)
    }
}
