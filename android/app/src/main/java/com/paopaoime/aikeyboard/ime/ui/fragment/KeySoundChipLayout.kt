package com.paopaoime.aikeyboard.ime.ui.fragment

internal object KeySoundChipLayout {
    const val GAP_DP = 8

    // Keep names readable at large font sizes and in narrow settings windows.
    fun columns(availableWidthPx: Int, density: Float, fontScale: Float): Int {
        val widthDp = availableWidthPx / density.coerceAtLeast(0.1f)
        val minimumWidthDp = 88f * fontScale.coerceAtLeast(1f)
        return ((widthDp + GAP_DP) / (minimumWidthDp + GAP_DP)).toInt().coerceIn(1, 3)
    }
}
