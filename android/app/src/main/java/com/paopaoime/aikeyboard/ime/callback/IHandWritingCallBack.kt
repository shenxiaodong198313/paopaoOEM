package com.paopaoime.aikeyboard.ime.callback

import com.paopaoime.aikeyboard.ime.engine.CandidateListItem

fun interface IHandWritingCallBack {
    fun onSucess(item:Array<CandidateListItem>)
}
