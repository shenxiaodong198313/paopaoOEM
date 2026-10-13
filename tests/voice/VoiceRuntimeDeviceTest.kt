package com.paopaoime.aikeyboard.ime.voice

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Requires all three model packs downloaded through the product UI. No weight injection. */
@RunWith(AndroidJUnit4::class)
class VoiceRuntimeDeviceTest {
    private data class Result(val text: String, val reused: Boolean, val initMs: Long, val decodeMs: Long)
    private fun recognize(repo: VoiceRepository, samples: FloatArray): Result {
        val ready=CountDownLatch(1);val done=CountDownLatch(1);var error="";var committed="";var reused=false;var initMs=-1L
        val listener=object:VoiceRuntime.Listener {
            override fun ready(warm:Boolean,ms:Long){reused=warm;initMs=ms;ready.countDown()}
            override fun text(value:String,final:Boolean){if(final)committed+=value}
            override fun done(){done.countDown()}
            override fun error(message:String){error=message;ready.countDown();done.countDown()}
        }
        val id=VoiceRuntime.begin(repo,listener)
        assertTrue("ready timeout",ready.await(40,TimeUnit.SECONDS));assertEquals("",error)
        val decodeStarted = SystemClock.elapsedRealtime()
        var offset=0
        while(offset<samples.size){val end=minOf(offset+1600,samples.size);val chunk=samples.copyOfRange(offset,end)
            val began=System.currentTimeMillis();while(!VoiceRuntime.audio(id,chunk,listener)){assertTrue("audio queue stalled",System.currentTimeMillis()-began<10000);Thread.sleep(30)}
            offset=end
        }
        VoiceRuntime.finish(id,listener);assertTrue("finish timeout",done.await(30,TimeUnit.SECONDS));assertEquals("",error)
        return Result(committed,reused,initMs,SystemClock.elapsedRealtime()-decodeStarted)
    }
    @Test fun threeChineseModesPunctuationWarmReuseAndSilence() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val repo=VoiceRepository.get(instrumentation.targetContext)
        val pcm=instrumentation.context.assets.open("voice_sample.pcm").use{it.readBytes()}
        val shorts=ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();val samples=FloatArray(shorts.remaining()){shorts.get()/32768f}
        val selected=repo.mode
        try {
            VoiceMode.entries.forEach { mode ->
                assertTrue("Download ${mode.id} through settings first",repo.ready(mode));repo.mode=mode
                val cold=recognize(repo,samples);val warm=recognize(repo,samples)
                assertTrue(cold.text.contains("泡泡输入法"));assertTrue(cold.text.contains("项目进度"));assertTrue(cold.text.contains("，"))
                assertEquals(cold.text,warm.text);assertTrue(warm.reused);assertEquals(0L,warm.initMs)
                assertEquals("",recognize(repo,FloatArray(16000)).text)
                Log.i("PaopaoVoiceTest","mode=${mode.id} coldInitMs=${cold.initMs} warmInitMs=${warm.initMs} coldDecodeMs=${cold.decodeMs} warmDecodeMs=${warm.decodeMs} audioMs=${samples.size/16} text=${cold.text}")
            }
        } finally { repo.mode=selected;VoiceRuntime.release("instrumentation-complete") }
    }
}
