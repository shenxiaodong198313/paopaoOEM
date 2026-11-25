package org.fcitx.fcitx5.android.input.keyboard

/** Only keyboard-origin single symbols reach this policy; literal delivery bypasses it. */
object SymbolPairAssist {
    data class Plan(val text: String, val cursor: Int = -1, val skip: Int = 0)
    private val pairs = mapOf("(" to ")", "[" to "]", "{" to "}", "（" to "）", "【" to "】",
        "《" to "》", "〈" to "〉", "「" to "」", "『" to "』", "“" to "”", "‘" to "’", "\"" to "\"", "'" to "'")
    fun plan(text: String, cursor: Int, enabled: Boolean, allowed: Boolean, selected: String?,
        collapsed: Boolean, before: String?, after: String?): Plan {
        if (!enabled || !allowed || text.length != 1 || cursor != -1) return Plan(text, cursor)
        if (collapsed && text in pairs.values && after?.startsWith(text) == true) return Plan("", skip = 1)
        val close = pairs[text] ?: return Plan(text, cursor)
        // An apostrophe in a contraction is a letter separator, not an opening quote.
        if (text == "'" && before?.lastOrNull()?.isLetterOrDigit() == true) return Plan(text, cursor)
        if (!collapsed && selected == null) return Plan(text, cursor)
        val inside = selected.orEmpty()
        return Plan(text + inside + close, text.length + inside.length)
    }
    fun isSymbol(text: String) = text in pairs || text in pairs.values
}
