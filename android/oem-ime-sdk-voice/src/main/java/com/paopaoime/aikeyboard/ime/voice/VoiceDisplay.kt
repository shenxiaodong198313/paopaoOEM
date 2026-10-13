package com.paopaoime.aikeyboard.ime.voice

class VoiceDisplay {
    private var committed = ""
    private var partial = ""
    private fun tail(text: String) = text.codePoints().toArray().takeLast(320).toIntArray().let { String(it, 0, it.size) }
    fun apply(text: String, final: Boolean): String {
        if (final) { committed = tail(committed + text); partial = "" } else partial = tail(text)
        return tail(committed + partial)
    }
    fun reset() { committed = ""; partial = "" }
    companion object {
        fun lines(text: String, width: Float, measure: (String) -> Float): List<String> {
            if (width <= 0) return emptyList()
            val chars = text.replace(Regex("\\s+"), " ").trim().codePoints().toArray()
            var end = chars.size; val rows = mutableListOf<String>()
            repeat(2) {
                if (end > 0) {
                    var start = end - 1
                    while (start > 0 && measure(String(chars, start - 1, end - start + 1)) <= width) start--
                    rows += String(chars, start, end - start).trim(); end = start
                }
            }
            return rows
        }
    }
}
