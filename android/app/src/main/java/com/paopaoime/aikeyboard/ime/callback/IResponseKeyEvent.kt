package com.paopaoime.aikeyboard.ime.callback

import com.paopaoime.aikeyboard.ime.entity.keyboard.SoftKey
import com.paopaoime.aikeyboard.ime.prefs.behavior.PopupMenuMode
import com.paopaoime.aikeyboard.ime.engine.CandidateListItem

interface IResponseKeyEvent {
    fun responseKeyEvent(sKey: SoftKey)
    fun responseLongKeyEvent(result:Pair<PopupMenuMode, String>)
    fun responseHandwritingResultEvent(words: Array<CandidateListItem>)
}

