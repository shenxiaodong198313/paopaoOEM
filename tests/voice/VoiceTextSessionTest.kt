package com.paopaoime.aikeyboard.ime.voice

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class VoiceTextSessionTest {
    private class Editor(var text: String = "原文后文", var cursor: Int = 2, val supportsPreview: Boolean = true, val readable: Boolean = true) {
        var composingStart = -1
        val connection = Proxy.newProxyInstance(InputConnection::class.java.classLoader,arrayOf(InputConnection::class.java)) { _, method, args ->
            when(method.name) {
                "getTextBeforeCursor" -> if(readable)text.substring(0,cursor).takeLast(args!![0] as Int) else null
                "getTextAfterCursor" -> if(readable)text.substring(cursor).take(args!![0] as Int) else null
                "getExtractedText" -> if(readable)ExtractedText().also { it.text=text;it.selectionStart=cursor;it.selectionEnd=cursor } else null
                "finishComposingText" -> { composingStart=-1;true }
                "setComposingText", "commitText" -> {
                    if(method.name=="setComposingText" && !supportsPreview) false
                    else {
                        val start=if(composingStart>=0)composingStart else cursor;val value=args!![0].toString()
                        text=text.substring(0,start)+value+text.substring(cursor);cursor=start+value.length
                        composingStart=if(method.name=="setComposingText")start else -1;true
                    }
                }
                else -> if(method.returnType==Boolean::class.javaPrimitiveType)false else null
            }
        } as InputConnection
    }
    @Test fun revisionsAndPunctuationOnlyReplaceOwnedPhrase() {
        val editor=Editor();val session=VoiceTextSession(editor.connection){true}
        assertTrue(session.apply("今天下五",false));assertEquals("原文今天下五后文",editor.text)
        assertTrue(session.selection(editor.cursor,editor.cursor,2,editor.cursor))
        assertTrue(session.apply("今天下午",false));assertEquals("原文今天下午后文",editor.text)
        assertTrue(session.apply("今天下午。",true));assertEquals("原文今天下午。后文",editor.text)
        assertTrue(session.apply("三点开会",false));session.finish();session.finish()
        assertEquals("原文今天下午。三点开会后文",editor.text)
    }
    @Test fun fallbackBuffersUntilFinalAndRetainsPendingOnStop() {
        val editor=Editor(supportsPreview=false);val session=VoiceTextSession(editor.connection){true}
        assertTrue(session.apply("明天",false));assertEquals("原文后文",editor.text)
        assertTrue(session.apply("明天。",true));assertEquals("原文明天。后文",editor.text)
        assertTrue(session.apply("见面",false));session.finish();session.finish()
        assertEquals("原文明天。见面后文",editor.text)
    }
    @Test fun cursorOrConnectionChangeRejectsLateResults() {
        val editor=Editor();var current=true;val session=VoiceTextSession(editor.connection){current}
        assertTrue(session.apply("你好",false));editor.cursor=0
        assertFalse(session.apply("你好。",true));session.finish();assertEquals("原文你好后文",editor.text)
        val next=VoiceTextSession(editor.connection){current};current=false
        assertFalse(next.apply("迟到结果",true));next.finish();assertEquals("原文你好后文",editor.text)
    }
    @Test fun unrelatedSelectionStopsAndEmptyFinalDoesNotDuplicate() {
        val editor=Editor();val session=VoiceTextSession(editor.connection){true}
        assertTrue(session.apply("你好。",true));assertTrue(session.apply("",true));assertEquals("原文你好。后文",editor.text)
        assertFalse(session.selection(0,1,-1,-1))
        assertFalse(session.selection(0,0,-1,-1))
    }
    @Test fun opaqueEditorUsesSelectionEventsAndNeverFlushesAtMovedCursor() {
        val editor=Editor(readable=false);val session=VoiceTextSession(editor.connection,editor.cursor){true}
        assertTrue(session.apply("你好",false));assertEquals("原文后文",editor.text)
        assertTrue(session.apply("你好。",true));assertTrue(session.selection(editor.cursor,editor.cursor,-1,-1))
        assertTrue(session.apply("不能写到别处",false));editor.cursor=0
        assertFalse(session.selection(0,0,-1,-1));session.finish()
        assertEquals("原文你好。后文",editor.text)
        assertFalse(session.apply("迟到结果",true))
    }
}
