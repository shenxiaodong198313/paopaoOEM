package com.paopaoime.aikeyboard.ime.keyboard.container

import com.paopaoime.aikeyboard.ime.prefs.behavior.SkbMenuMode

class ClipBoardContainer : InputBaseContainer() {

    private var menuMode: SkbMenuMode = SkbMenuMode.ClipBoard

    fun getMenuMode(): SkbMenuMode = menuMode

    fun showClipBoardView(mode: SkbMenuMode) {
        menuMode = mode
    }
}

