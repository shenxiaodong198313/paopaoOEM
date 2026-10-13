package com.paopaoime.aikeyboard.ime.engine

import android.view.KeyEvent

object Kernel {
    private var currentSchema: String = "pinyin"
    private var candidateList: List<CandidateListItem> = emptyList()

    val candidates: List<CandidateListItem>
        get() = candidateList

    val nextPageCandidates: List<CandidateListItem>
        get() = emptyList()

    val prefixs: Array<String>
        get() = emptyArray()

    var commitText: String = ""
        private set

    var wordsShowPinyin: String = ""
        private set

    var isFinish: Boolean = true
        private set

    fun reset() {
        candidateList = emptyList()
        commitText = ""
        wordsShowPinyin = ""
        isFinish = true
    }

    fun resetIme() {
        reset()
    }

    fun getCurrentRimeSchema(): String {
        return currentSchema
    }

    fun initImeSchema(schema: String) {
        currentSchema = schema
        reset()
    }

    fun nativeUpdateImeOption() {
    }

    fun inputKeyCode(event: KeyEvent) {
        isFinish = false
        val ch = event.unicodeChar
        if (ch != 0) {
            wordsShowPinyin += ch.toChar()
            candidateList = listOf(CandidateListItem(wordsShowPinyin))
        }
    }

    fun selectPrefix(position: Int) {
    }

    fun deleteAction() {
        if (wordsShowPinyin.isNotEmpty()) {
            wordsShowPinyin = wordsShowPinyin.dropLast(1)
            candidateList = if (wordsShowPinyin.isEmpty()) emptyList() else listOf(CandidateListItem(wordsShowPinyin))
        }
    }

    fun getWordSelectedWord(candId: Int) {
        commitText = candidateList.getOrNull(candId)?.text ?: commitText
        reset()
    }

    fun getAssociateWord(words: String) {
    }
}
