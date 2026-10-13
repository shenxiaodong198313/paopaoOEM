package com.paopaoime.company

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paopaoime.aikeyboard.ime.voice.*
import org.fcitx.fcitx5.android.input.voice.OemVoiceConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VoiceIntegrationTest {
    private val test get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = test.targetContext
    private fun main(block: () -> Unit) { if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()) block() else test.runOnMainSync(block) }
    private fun waitUntil(timeout: Long = 10000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) { if (condition()) return; Thread.sleep(100) }
        assertTrue("Condition timed out", condition())
    }
    private fun all(v: View): List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun views(): List<View> { var result = emptyList<View>(); main { result = WindowInspector.getGlobalWindowViews().flatMap { all(it) } }; return result }
    private fun click(name: String) { val id=context.resources.getIdentifier(name,"id",context.packageName);main { views().first { it.id==id && it.isShown }.performClick() } }
    private fun screenshot(name: String) { Thread.sleep(500); val dir=File(context.getExternalFilesDir(null),"voice-verification").apply{mkdirs()};File(dir,"$name.png").outputStream().use { test.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
    private fun shownId(name:String) = views().any { it.id==context.resources.getIdentifier(name,"id",context.packageName) && it.isShown }
    private fun board() = views().filterIsInstance<org.fcitx.fcitx5.android.input.keyboard.BaseKeyboard>().firstOrNull { it.isShown }
    private fun service(): com.paopaoime.aikeyboard.ime.service.AiInputMethodService {
        var c:android.content.Context=board()!!.context
        while(c !is com.paopaoime.aikeyboard.ime.service.AiInputMethodService) c=(c as android.content.ContextWrapper).baseContext
        return c
    }
    private fun permissionButton(suffix:String):android.view.accessibility.AccessibilityNodeInfo? {
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo?):android.view.accessibility.AccessibilityNodeInfo? {
            if(n==null)return null
            if(n.viewIdResourceName?.endsWith(":id/$suffix")==true)return n
            for(i in 0 until n.childCount){val found=visit(n.getChild(i));if(found!=null)return found}
            return null
        }
        return visit(test.uiAutomation.rootInActiveWindow)
    }
    @Test fun permanentlyDeniedMicrophoneGuidesSystemSettings() {
        val info=test.uiAutomation.serviceInfo;info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;test.uiAutomation.serviceInfo=info
        org.junit.Assume.assumeTrue("Set microphone to denied in Android app settings first",context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
        test.startActivitySync(Intent(context,VoiceSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync()
        click("voice_input_permission")
        waitUntil{permissionButton("permission_deny_and_dont_ask_again_button")!=null || views().any{it is TextView && it.isShown && it.text.toString()=="去设置"}}
        permissionButton("permission_deny_and_dont_ask_again_button")?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        waitUntil{views().any{it is TextView && it.isShown && it.text.toString()=="去设置"}}
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO));screenshot("microphone-denied-settings-guide")
        main { views().filterIsInstance<TextView>().first{it.isShown && it.text.toString()=="取消"}.performClick() }
        assertTrue(shownId("voice_input_download"));assertFalse(VoiceImeController.isPassword(android.text.InputType.TYPE_CLASS_TEXT))
    }
    @Test fun openVoiceFromInputMethodSettings() {
        test.startActivitySync(Intent().setClassName(context.packageName,"com.paopaoime.aikeyboard.ime.ui.activity.OemMainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitUntil{shownId("card_voice_to_text")};click("card_voice_to_text")
        waitUntil{shownId("voice_input_modes") && shownId("voice_input_download")};screenshot("embedded-voice-settings")
        click("btn_voice_to_text_back");waitUntil{shownId("card_voice_to_text")}
    }
    @Test fun microphonePanelStopCursorAndHide() {
        val host=test.startActivitySync(Intent(context,InputCheckActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as InputCheckActivity
        main { host.editor.requestFocus(); (host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(host.editor,0) }
        waitUntil(20000){board()!=null}
        val ime=service()
        val mic=views().first { it.contentDescription?.toString()=="语音输入" && it.isShown }
        main { mic.performClick() };waitUntil(10000){shownId("voice_input_panel")}
        android.util.Log.i("OemVoiceTest","MICROPHONE_READY")
        Thread.sleep(4500)
        val sample=File(context.cacheDir,"voice_sample.wav")
        test.context.assets.open("voice_sample.wav").use{input->sample.outputStream().use{input.copyTo(it)}}
        val audio=host.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val original=audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val player=android.media.MediaPlayer()
        try {
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,(audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)*0.7).toInt(),0)
            player.setDataSource(sample.path);player.prepare();player.start();Thread.sleep(11000);screenshot("microphone-listening")
        } finally {player.release();audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,original,0);sample.delete()}
        click("voice_input_stop");waitUntil(15000){!shownId("voice_input_panel") && board()!=null}
        var text="";main{text=host.editor.text.toString()}
        assertTrue("Microphone transcript empty",text.isNotBlank());android.util.Log.i("OemVoiceTest","microphoneText=$text");screenshot("microphone-stopped")
        main { assertTrue(ime.launchLocalVoiceInput()) };waitUntil{shownId("voice_input_panel")}
        main { host.editor.setSelection(0) };waitUntil{!shownId("voice_input_panel")};val previous=host.editor.text.toString();Thread.sleep(1500);assertEquals(previous,host.editor.text.toString())
        main { assertTrue(ime.launchLocalVoiceInput()) };waitUntil{shownId("voice_input_panel")}
        main { (host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(host.editor.windowToken,0) }
        waitUntil{!shownId("voice_input_panel")};Thread.sleep(1000);android.util.Log.i("OemVoiceTest","panel/manual stop/cursor/hide passed")
    }
    @Test fun passwordGuardRejectsAllAndroidPasswordTypes() {
        OemVoiceConfig.get(context)
        for(type in listOf(129,145,225,18)) {
            val editor=android.view.inputmethod.EditorInfo().apply { inputType=type }
            val fake=object:android.inputmethodservice.InputMethodService() {
                override fun getCurrentInputEditorInfo()=editor
                override fun isInputViewShown()=true
            }
            val controller=VoiceImeController(fake){fail("Password displayed a voice panel")}
            assertFalse("Password type $type enabled voice",controller.start())
            assertFalse(controller.active)
        }
    }
    @Test fun configContracts() {
        assertTrue(OemVoiceConfig.get(context).enabled)
        val c=OemVoiceConfig.parse("""{"enabled":false,"defaultMode":"quality_xl","models":{"quality":{"numThreads":3,"decodingMethod":"modified_beam_search","maxActivePaths":8,"blankPenalty":0.2}},"downloadUrlTemplates":["https://cdn.example.com/{folder}/{name}"],"fileUrls":{"quality/tokens.txt":["https://cdn.example.com/tokens.txt"]}}""")
        assertFalse(c.enabled);assertEquals("quality_xl",c.defaultMode);assertEquals(3,c.tuning("quality").numThreads)
        assertEquals("modified_beam_search",c.tuning("quality").decodingMethod);assertEquals(2,c.tuning("performance").numThreads)
        assertEquals(listOf("https://cdn.example.com/tokens.txt"),c.urls("quality","tokens.txt","repo","rev"))
        assertEquals(listOf("https://cdn.example.com/quality/decoder.onnx"),c.urls("quality","decoder.onnx","repo","rev"))
        for(raw in listOf("{" , """{"models":{"quality":{"numThreads":0}}}""", """{"models":{"performance":{"decodingMethod":"modified_beam_search"}}}""", """{"downloadUrlTemplates":["http://cdn.example.com/x"]}""", """{"maxRecordingSeconds":999999}""")) {
            assertTrue("Invalid config accepted",runCatching{OemVoiceConfig.parse(raw)}.isFailure)
        }
    }
    @Test fun downloadThreeModelsThroughSettings() {
        val repo=VoiceRepository.get(context)
        test.startActivitySync(Intent(context,VoiceSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        test.waitForIdleSync()
        val ids=listOf("voice_mode_performance","voice_mode_balanced","voice_mode_quality")
        var paused=false
        VoiceMode.entries.forEachIndexed { index,mode ->
            click(ids[index]);assertEquals(mode,repo.mode)
            if(!repo.ready(mode)) {
                click("voice_input_download")
                var attempts=1
                val end=SystemClock.elapsedRealtime()+1800000
                while(!repo.ready(mode) && SystemClock.elapsedRealtime()<end) {
                    val status=repo.download.value
                    if(!paused && status.running && status.downloaded>1024*1024 && status.downloaded<status.total) {
                        click("voice_input_download");waitUntil{!repo.download.value.running}
                        val bytes=repo.downloaded(mode);assertTrue(bytes>0);screenshot("download-paused")
                        click("voice_input_download");waitUntil(30000){repo.downloaded(mode)>bytes || repo.ready(mode)};paused=true
                    }
                    if(!repo.download.value.running && !repo.ready(mode)) {
                        assertTrue("Download failed: ${repo.download.value.message}",attempts++<4)
                        click("voice_input_download")
                    }
                    Thread.sleep(500)
                }
            }
            assertTrue("Model unavailable: ${mode.id}",repo.ready(mode));repo.verify(mode)
            screenshot("download-${mode.id}")
            android.util.Log.i("OemVoiceTest","download verified ${mode.id}")
        }
        repo.mode=VoiceMode.PERFORMANCE
    }
}
