package com.paopaoime.company

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.input.voice.OemVoiceBridge
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceAbsenceTest {
    @Test fun noModuleOrDisabledConfigurationKeepsKeyboardAndHidesVoice() {
        val test=InstrumentationRegistry.getInstrumentation();val context=test.targetContext
        val installed=InstrumentationRegistry.getArguments().getString("voiceInstalled")=="true"
        assertEquals(installed,OemVoiceBridge.installed);assertFalse(OemVoiceBridge.enabled(context))
        assertNull(OemVoiceBridge.settings(context))
        if(!installed) assertTrue(runCatching{Class.forName("com.k2fsa.sherpa.onnx.OnlineRecognizer")}.isFailure)
        val host=test.startActivitySync(Intent(context,InputCheckActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as InputCheckActivity
        fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList()
        var board:org.fcitx.fcitx5.android.input.keyboard.BaseKeyboard?=null
        val end=SystemClock.elapsedRealtime()+15000
        while(board==null && SystemClock.elapsedRealtime()<end){test.runOnMainSync{board=WindowInspector.getGlobalWindowViews().flatMap{all(it)}.filterIsInstance<org.fcitx.fcitx5.android.input.keyboard.BaseKeyboard>().firstOrNull{it.isShown}};Thread.sleep(100)}
        assertNotNull("Base keyboard did not appear",board)
        test.runOnMainSync{
            var c:android.content.Context=board!!.context
            while(c !is com.paopaoime.aikeyboard.ime.service.AiInputMethodService)c=(c as android.content.ContextWrapper).baseContext
            assertFalse(c.launchLocalVoiceInput())
            assertFalse(InputMethodSettingsBridge.getToolbarDisplaySettings(context).orderedItems.any{it is InputMethodSettingsBridge.ToolbarDisplayItem.Builtin && it.item==InputMethodSettingsBridge.ToolbarItem.Voice})
            assertFalse(WindowInspector.getGlobalWindowViews().flatMap{all(it)}.any{it.isShown && it.contentDescription?.toString()=="语音输入"})
            host.editor.setText("基础输入保留")
        }
    }
}
