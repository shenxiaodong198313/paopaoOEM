package com.paopaoime.aikeyboard.ime.entity

import com.paopaoime.aikeyboard.ime.prefs.behavior.SkbMenuMode

class SkbFunItem( val funName: String, @JvmField val funImgRecource: Int, val skbMenuMode: SkbMenuMode){
    override fun equals(other: Any?): Boolean {
        return when(other) {
            !is SkbFunItem -> false
            else -> this === other || this.skbMenuMode == other.skbMenuMode
        }
    }
}

