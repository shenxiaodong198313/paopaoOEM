package com.paopaoime.aikeyboard.ime.keyboard

import com.paopaoime.aikeyboard.ime.keyboard.container.CandidatesContainer
import com.paopaoime.aikeyboard.ime.keyboard.container.ClipBoardContainer
import com.paopaoime.aikeyboard.ime.keyboard.container.InputBaseContainer
import com.paopaoime.aikeyboard.ime.keyboard.container.SymbolContainer

class KeyboardManager private constructor() {

    enum class KeyboardType {
        Default,
        ClipBoard,
        Candidates,
        Symbol,
    }

    var currentContainer: Any = InputBaseContainer()
        private set

    fun clearKeyboard() {}

    fun switchKeyboard() {
        currentContainer = InputBaseContainer()
    }

    fun switchKeyboard(skbLayout: Int) {
        currentContainer = InputBaseContainer()
    }

    fun switchKeyboard(type: KeyboardType) {
        currentContainer = when (type) {
            KeyboardType.ClipBoard -> ClipBoardContainer()
            KeyboardType.Candidates -> CandidatesContainer()
            KeyboardType.Symbol -> SymbolContainer()
            KeyboardType.Default -> InputBaseContainer()
        }
    }

    companion object {
        @JvmStatic
        val instance: KeyboardManager by lazy { KeyboardManager() }
    }
}
