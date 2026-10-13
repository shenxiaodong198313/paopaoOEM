package com.paopaoime.aikeyboard.ime.application

import android.annotation.SuppressLint
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.paopaoime.aikeyboard.ime.data.emojicon.EmojiCompatHelper
import com.paopaoime.aikeyboard.ime.data.theme.ThemeManager
import com.paopaoime.aikeyboard.ime.data.theme.ThemeManager.prefs
import com.paopaoime.aikeyboard.ime.theme.ImeThemeDefaultsBridge
import com.paopaoime.aikeyboard.ime.database.DataBaseKT
import com.paopaoime.aikeyboard.ime.prefs.AppPrefs
import com.paopaoime.aikeyboard.ime.service.ClipboardHelper
import com.paopaoime.aikeyboard.ime.utils.AssetUtils.copyFileOrDir
import com.paopaoime.aikeyboard.ime.utils.thread.ThreadPoolUtils
import com.paopaoime.aikeyboard.ime.engine.Kernel

class Launcher {
    lateinit var context: Context
        private set

    fun initData(context: Context) {
        this.context = context
        currentInit()
        onInitDataChildThread()
    }

    private fun currentInit() {
        AppPrefs.init(PreferenceManager.getDefaultSharedPreferences(context))
        ImeThemeDefaultsBridge.ensureDefaults(context)
        ThemeManager.init(context.resources.configuration)
        DataBaseKT.instance.sideSymbolDao().getAllSideSymbolPinyin()
        ClipboardHelper.init()
        EmojiCompatHelper.init(context)
        PDFBoxResourceLoader.init(context)
        System.setProperty("javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
        System.setProperty("javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
        System.setProperty("javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
    }

    /**
     * 可以在子线程初始化的操作
     */
    private fun onInitDataChildThread() {
        ThreadPoolUtils.executeSingleton {
            // 复制词库文件
            val dataDictVersion = AppPrefs.getInstance().internal.dataDictVersion.getValue()
            if (dataDictVersion < CustomConstant.CURRENT_RIME_DICT_DATA_VERSIOM) {
                //rime词库
                copyFileOrDir(context, "rime", "", CustomConstant.RIME_DICT_PATH, true)
                copyFileOrDir(context, "hw", "", CustomConstant.HW_DICT_PATH, true)
                AppPrefs.getInstance().internal.dataDictVersion.setValue(CustomConstant.CURRENT_RIME_DICT_DATA_VERSIOM)
            }
            Kernel.resetIme()  // 解决词库复制慢，导致先调用初始化问题
            //初始化键盘主题
            val isFollowSystemDayNight = prefs.followSystemDayNightTheme.getValue()
            if (isFollowSystemDayNight) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        val instance = Launcher()
    }
}
