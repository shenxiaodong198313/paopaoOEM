package org.fcitx.fcitx5.android.data.theme

/** Optional, bounded references to installed sounds; never paths or downloadable audio. */
interface SkinSoundDefinition {
    val version: Int
    val defaultId: String
    val roleOverrides: Map<String, String>
    val keyOverrides: Map<String, String>
    val fallbackId: String
}

data class SkinSoundKey(val address: String, val semantic: String, val role: String)

object SkinSoundResolver {
    val ids = setOf("bd_default", "standard", "mouse", "click", "crisp", "pluck", "glass", "tick",
        "bd_allegro", "bd_bopu", "bd_dadu", "bd_dilu", "bd_duda", "bd_dulu", "bd_dudu",
        "bd_kada", "bd_koukou", "bd_mechanical", "bd_blackberry", "bd_miku", "bd_marimba",
        "bd_cute", "bd_puppy", "bd_kitten", "bd_pikachu", "bd_cotton_candy", "bd_pop",
        "bd_cartoon_pop", "bd_cute_simple", "bd_cute_toy")
    val roles = setOf("key", "letter", "number", "punctuation", "sidebarSlot", "space", "backspace",
        "enter", "shift", "language", "number_mode", "number_return", "symbol", "emoji",
        "alternative", "function", "retype", "separator", "toolbar", "candidate").map { "role:$it" }.toSet()
    private val physical = Regex("(qwerty|t9|number)/(r[1-4]c(10|[1-9])|sidebar:[1-4])")
    private val semantic = Regex("(letter:[a-z]|digit:[0-9]|t9:[1-9])")
    fun validKey(key: String): Boolean = physical.matches(key) || semantic.matches(key) || key in roles ||
        (key.startsWith("text:") && key.substring(5).codePointCount(0, key.length - 5) in 1..32 &&
            key.substring(5).codePoints().noneMatch { Character.getType(it) in setOf(
                Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.SURROGATE.toInt()) })
    fun validate(value: SkinSoundDefinition) {
        require(value.version == 1 && value.defaultId in ids && value.fallbackId == "bd_default")
        require(value.roleOverrides.size <= 20 && value.roleOverrides.keys.all { it in roles })
        require(value.keyOverrides.size <= 128 && value.keyOverrides.keys.all(::validKey))
        val used = (value.roleOverrides.values + value.keyOverrides.values + value.defaultId + value.fallbackId).toSet()
        require(used.size <= 8 && used.all { it in ids })
    }
    /** Computed when key views are created; touch only checks ready sample IDs. */
    fun candidates(value: SkinSoundDefinition?, key: SkinSoundKey): List<String> {
        if (value == null) return listOf("bd_default")
        return listOfNotNull(value.keyOverrides[key.address], value.keyOverrides[key.semantic],
            value.keyOverrides[key.role], value.roleOverrides[key.role], value.defaultId, value.fallbackId).distinct()
    }
    fun key(layout: String, address: String?, command: String, hint: String = ""): SkinSoundKey {
        val role = when {
            address?.startsWith("sidebar:") == true -> "sidebarSlot"
            command in setOf("space", "backspace", "enter", "shift", "language", "number_mode", "number_return", "symbol", "emoji", "retype", "separator") -> command
            layout == "t9" && command in "2".."9" && command.length == 1 -> "letter"
            Regex("[a-zA-Z]").matches(command) -> "letter"
            Regex("[0-9]").matches(command) -> "number"
            command.isNotEmpty() -> "punctuation"
            else -> "function"
        }
        val semantic = when (role) {
            "letter" -> if (layout == "t9" && command in "2".."9") "t9:$command" else "letter:${command.lowercase(java.util.Locale.ROOT)}"
            "number" -> "digit:$command"
            "punctuation" -> "text:$command"
            "sidebarSlot" -> if (hint.startsWith("text:")) hint else "role:sidebarSlot"
            else -> "role:$role"
        }
        return SkinSoundKey(if (address == null) "" else "$layout/$address", semantic, "role:$role")
    }
}
