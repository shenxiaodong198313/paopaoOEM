package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.CandidateWord

/** Presentation-only cleanup; preserve the native candidate's label and index. */
object T9CandidateText {
    private val trailingPinyin = Regex(" [a-zA-Z ']+$")

    fun clean(candidate: CandidateWord): CandidateWord = candidate.copy(
        text = candidate.text.replace(trailingPinyin, ""),
        comment = ""
    )

    /** The native index remains with its caller. Never strip letters from a mixed-script word to make a different word. */
    fun chineseDisplay(candidate: CandidateWord): CandidateWord? {
        val text = candidate.text.substringBefore(' ')
        if (text.isEmpty() || !text.codePoints().allMatch(Character::isIdeographic)) return null
        if (text != candidate.text && !trailingPinyin.matches(candidate.text.substring(text.length))) return null
        return if (text == candidate.text && candidate.comment.isEmpty()) candidate else candidate.copy(text = text, comment = "")
    }
}
