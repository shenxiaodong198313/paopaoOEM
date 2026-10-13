package org.fcitx.fcitx5.android.input

object InputWindowSizeOverride {
    private val listeners = linkedSetOf<() -> Unit>()

    private var keyboardHeightPercentOverride: Int? = null
    private var keyboardHeightPxOverride: Int? = null

    fun getKeyboardHeightPercentOverride(): Int? = keyboardHeightPercentOverride

    fun getKeyboardHeightPxOverride(): Int? = keyboardHeightPxOverride

    fun setKeyboardHeightPercentOverride(percent: Int?) {
        if (keyboardHeightPercentOverride == percent) return
        keyboardHeightPercentOverride = percent
        listeners.toList().forEach { it.invoke() }
    }

    fun setKeyboardHeightPxOverride(heightPx: Int?) {
        val normalized = heightPx?.takeIf { it >= 0 }
        if (keyboardHeightPxOverride == normalized) return
        keyboardHeightPxOverride = normalized
        listeners.toList().forEach { it.invoke() }
    }


    fun registerListener(listener: () -> Unit) {
        listeners += listener
    }

    fun unregisterListener(listener: () -> Unit) {
        listeners -= listener
    }
}
