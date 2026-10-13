package org.fcitx.fcitx5.android.input.keyboard

/** One-based physical positions; legacy semantic IDs stay available for old skin packages. */
data class SkinKeyAddress(val layout: String = "", val position: String? = null) {
    fun candidates(legacyId: String): List<String> = buildList {
        if (layout.isNotEmpty()) {
            if (position != null) add("$layout/$position")
            add("$layout/$legacyId")
        }
        add(legacyId)
    }
}
