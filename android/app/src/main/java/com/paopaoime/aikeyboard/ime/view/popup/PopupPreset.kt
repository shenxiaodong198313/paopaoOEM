
package com.paopaoime.aikeyboard.ime.view.popup

val PopupPreset: Map<String, Array<String>> = hashMapOf(

    //
    // Numbers (with superscripts/subscripts placeholders)
    //
    "0" to arrayOf("°", "+", "−", "0"),
    "1" to arrayOf("¹", "½", "⅓", "¼", "⅕", "⅙", "⅛", "⅔", "⅖", "1"),
    "2" to arrayOf("²", "⅔", "2"),
    "3" to arrayOf("³", "¾", "3"),
    "4" to arrayOf("⁴", "4"),
    "5" to arrayOf("⁵", "⅚", "5"),
    "6" to arrayOf("⁶", "6"),
    "7" to arrayOf("⁷", "⅞", "7"),
    "8" to arrayOf("⁸", "8"),
    "9" to arrayOf("⁹", "9"),

    //
    // Abc
    //
    "q" to arrayOf("q", "Q"),
    "w" to arrayOf("w", "W"),
    "e" to arrayOf("e", "E"),
    "r" to arrayOf("r", "R"),
    "t" to arrayOf("t", "T"),
    "y" to arrayOf("y", "Y"),
    "u" to arrayOf("u", "U"),
    "i" to arrayOf("i", "I"),
    "o" to arrayOf("o", "O"),
    "p" to arrayOf("p", "P"),
    "a" to arrayOf("a", "A"),
    "s" to arrayOf("s", "S"),
    "d" to arrayOf("d", "D"),
    "f" to arrayOf("f", "F"),
    "g" to arrayOf("g", "G"),
    "h" to arrayOf("h", "H"),
    "j" to arrayOf("j", "J"),
    "k" to arrayOf("k", "K"),
    "l" to arrayOf("l", "L"),
    "z" to arrayOf("z", "Z"),
    "x" to arrayOf("x", "X"),
    "c" to arrayOf("c", "C"),
    "v" to arrayOf("v", "V"),
    "b" to arrayOf("b", "B"),
    "n" to arrayOf("n", "N"),
    "m" to arrayOf("m", "M"),

    "Q" to arrayOf("q", "Q"),
    "W" to arrayOf("w", "W"),
    "E" to arrayOf("e", "E"),
    "R" to arrayOf("r", "R"),
    "T" to arrayOf("t", "T"),
    "Y" to arrayOf("y", "Y"),
    "U" to arrayOf("u", "U"),
    "I" to arrayOf("i", "I"),
    "O" to arrayOf("o", "O"),
    "P" to arrayOf("p", "P"),
    "A" to arrayOf("a", "A"),
    "S" to arrayOf("s", "S"),
    "D" to arrayOf("d", "D"),
    "F" to arrayOf("f", "F"),
    "G" to arrayOf("g", "G"),
    "H" to arrayOf("h", "H"),
    "J" to arrayOf("j", "J"),
    "K" to arrayOf("k", "K"),
    "L" to arrayOf("l", "L"),
    "Z" to arrayOf("z", "Z"),
    "X" to arrayOf("x", "X"),
    "C" to arrayOf("c", "C"),
    "V" to arrayOf("v", "V"),
    "B" to arrayOf("b", "B"),
    "N" to arrayOf("n", "N"),
    "M" to arrayOf("m", "M"),

    //
    // T9
    //
    "分词" to arrayOf("'", "\"", "“", "”"),
    "ABC" to arrayOf("B", "A", "C", "2", "b", "a", "c"),
    "DEF" to arrayOf("F", "E", "D", "3", "f", "e", "d"),
    "GHI" to arrayOf("H", "G", "I", "4", "h", "g", "i"),
    "JKL" to arrayOf("K", "J", "L", "5", "k", "j", "l"),
    "MNO" to arrayOf("O", "N", "M", "6", "o", "n", "m"),
    "PQRS" to arrayOf("Q", "P", "R", "S", "7", "q", "p", "r", "s"),
    "TUV" to arrayOf("U", "T", "V", "8", "u", "t", "v"),
    "WXYZ" to arrayOf("Z", "Y", "X", "W", "9", "z", "y", "x", "w"),

    //
    // LX17
    //
    "HP" to arrayOf("H", "P", "A", "h", "p", "a"),
    "Sh" to arrayOf("sh", "Sh"),
    "Zh" to arrayOf("zh", "Zh"),
    "oXv" to arrayOf("V", "X" ,"O", "3", "v", "x", "o"),
    "MS" to arrayOf("s", "m", "S", "M"),
    "WZ" to arrayOf("Z", "W", "E", "2", "z", "w", "e"),
    "JK" to arrayOf("I", "K", "J", "5", "i", "k", "j"),
    "NR" to arrayOf("r", "n", "R", "N"),
    "Ch" to arrayOf("Ch", "ch"),
    "Q~" to arrayOf("q", "Q"),
    "FC" to arrayOf("f", "C", "c", "F"),

    //
    // Other
    //
    "," to arrayOf(",", ".", "?", "!", ":", ";", "(", ")"),
    "." to arrayOf(".", ",", "?", "!", ":", ";", "(", ")"),
    "“" to arrayOf("“", "”", "‘", "’", "\"", "'", "«", "»"),
    "”" to arrayOf("”", "“", "’", "‘", "\"", "'", "»", "«"),
    "@" to arrayOf(",", "，", ".", "。", "?", "？", "!", "！", ":", "：", ";", "；"),
    "符号" to arrayOf(",", "，", ".", "。", "?", "？", "!", "！", ":", "：", ";", "；"),
    "表情" to arrayOf("🙂", "😀", "😄", "😅", "😆", "😉", "😊", "😋", "😎", "😍"),
    "123" to arrayOf( "0", "1", "2", "3", "4","5", "6", "7", "8", "9")
)

val PopupSmallPreset: Map<String, Array<String>> = hashMapOf(
    //
    // Half-width
    //
    "[" to arrayOf("["),
    "]" to arrayOf("]"),
    "<" to arrayOf("<"),
    ">" to arrayOf(">"),
    "(" to arrayOf("("),
    ")" to arrayOf(")"),
    "{" to arrayOf("{"),
    "}" to arrayOf("}"),
    "|" to arrayOf("|"),
    "-" to arrayOf("-"),
    "~" to arrayOf("~"),
    "+" to arrayOf("+"),
    "×" to arrayOf("×"),
    "÷" to arrayOf("÷"),
    "=" to arrayOf("="),
    "_" to arrayOf("_"),
    "#" to arrayOf("#"),
    "$" to arrayOf("$"),
    "%" to arrayOf("%"),
    "/" to arrayOf("/"),
    "`" to arrayOf("`"),
    "\\" to arrayOf("\\"),
    ":" to arrayOf(":"),
    ";" to arrayOf(";"),
    "'" to arrayOf("'"),
    "\"" to arrayOf("\""),
    "@" to arrayOf("@"),
    "," to arrayOf(","),
    "." to arrayOf("."),
    "&" to arrayOf("&"),
    "?" to arrayOf("?"),
    "!" to arrayOf("!"),
    "*" to arrayOf("*"),

    //
    // Full width
    //
    "," to arrayOf("，"),
    "." to arrayOf("。"),
    "?" to arrayOf("？"),
    "!" to arrayOf("！"),

    //
    // Numbers
    //
    "0" to arrayOf("0"),
    "1" to arrayOf("1"),
    "2" to arrayOf("2"),
    "3" to arrayOf("3"),
    "4" to arrayOf("4"),
    "5" to arrayOf("5"),
    "6" to arrayOf("6"),
    "7" to arrayOf("7"),
    "8" to arrayOf("8"),
    "9" to arrayOf("9")
)
