package com.paopaoime.aikeyboard.ime.utils

import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import com.paopaoime.aikeyboard.ime.application.Launcher
import com.paopaoime.aikeyboard.ime.prefs.InputFeedbacks.SoundEffect
import com.paopaoime.aikeyboard.ime.prefs.InputFeedbacks.hapticFeedback
import com.paopaoime.aikeyboard.ime.prefs.InputFeedbacks.soundEffect

/**
 * 设备相关工具类
 */
object DevicesUtils {
    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    @JvmStatic
    fun dip2px(dpValue: Float): Int {
        val scale = Launcher.instance.context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }
    @JvmStatic
    fun dip2px(dpValue: Int): Int {
        val scale = Launcher.instance.context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    @JvmStatic
    fun px2sp(px: Int): Float {
        val scale = Launcher.instance.context.resources.displayMetrics.density
        return px / (scale + 1)
    }

    /**
     * 根据手机的分辨率从 px(像素) 的单位 转成为 dp
     */
	@JvmStatic
	fun px2dip(pxValue: Int): Float {
        val scale = Launcher.instance.context.resources.displayMetrics.density
        return pxValue / scale + 0.5f
    }

    @JvmStatic
    fun sp2px(sp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), Launcher.instance.context.resources.displayMetrics).toInt()
    }

    /**
     * 闇囧姩
     */
	@JvmStatic
	fun tryVibrate(view: View?) {
        hapticFeedback(view!!)
    }


    /**
     * 播放按键按下的声音
     */
	@JvmStatic
	fun tryPlayKeyDown(code: Int = 0) {
        var soundEffect = SoundEffect.Standard
        soundEffect = when (code) {
            KeyEvent.KEYCODE_DEL -> SoundEffect.Delete
            KeyEvent.KEYCODE_SPACE -> SoundEffect.SpaceBar
            KeyEvent.KEYCODE_ENTER -> SoundEffect.Return
            else -> SoundEffect.Standard
        }
        soundEffect(soundEffect)
    }
}

