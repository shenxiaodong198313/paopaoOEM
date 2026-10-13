package com.paopaoime.aikeyboard.ime.keyboard.container

import com.paopaoime.aikeyboard.ime.prefs.behavior.SymbolMode

class SymbolContainer : InputBaseContainer() {

    private var menuMode: SymbolMode = SymbolMode.Symbol

    fun getMenuMode(): SymbolMode = menuMode

    fun showSymbolView(mode: SymbolMode) {
        menuMode = mode
    }
}

