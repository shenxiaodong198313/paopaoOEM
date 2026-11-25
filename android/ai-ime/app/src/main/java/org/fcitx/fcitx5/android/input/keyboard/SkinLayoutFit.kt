package org.fcitx.fcitx5.android.input.keyboard

import kotlin.math.abs
import kotlin.math.min

/** Pixel geometry only: never changes saved height preferences or touch ownership. */
object SkinLayoutFit {
    fun compactScale(actual: Float, reference: Float): Float =
        if (actual <= 0f) 1f else min(1f, actual / reference.coerceAtLeast(1f))

    fun gridHeight(requested: Int, host: Int, chrome: Int): Int =
        min(requested, (host - chrome).coerceAtLeast(1)).coerceAtLeast(1)

    fun textScale(faceWidth: Float, faceHeight: Float, centerX: Float, centerY: Float,
                  textWidth: Float, lineHeight: Float): Float = min(1f,
        min(faceWidth.coerceAtLeast(0f) * 2f * min(centerX, 1f - centerX) / textWidth.coerceAtLeast(1f),
            faceHeight.coerceAtLeast(0f) * 2f * min(centerY, 1f - centerY) / lineHeight.coerceAtLeast(1f)))
        .coerceAtLeast(0f)

    fun pairScale(faceHeight: Float, firstY: Float, secondY: Float,
                  firstLineHeight: Float, secondLineHeight: Float): Float =
        min(1f, 2f * faceHeight.coerceAtLeast(0f) * abs(firstY - secondY) /
            (firstLineHeight + secondLineHeight).coerceAtLeast(1f))
}
