package org.fcitx.fcitx5.android.input.status

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Shared Android/iOS/Harmony contract: design/keyboard-tool-panel.json. */
class ToolPanelPalette(background: Int, foreground: Int, accent: Int) {
    val surface = background or 0xff000000.toInt()
    val ink = readableInk(surface, foreground)
    val card = blend(surface, if (luminance(surface) < .25) -1 else 0xff000000.toInt(), if (luminance(surface) < .25) 180 else 80, ink)
    val selectedCard = blend(card, accent or 0xff000000.toInt(), 220, ink)

    companion object {
        fun luminance(color: Int): Double {
            val channels = listOf(16, 8, 0).map { shift ->
                val s = ((color ushr shift) and 255) / 255.0
                if (s <= .04045) s / 12.92 else ((s + .055) / 1.055).pow(2.4)
            }
            return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722
        }
        fun contrast(a: Int, b: Int): Double {
            val x = luminance(a); val y = luminance(b)
            return (max(x, y) + .05) / (min(x, y) + .05)
        }
        fun readableInk(surface: Int, preferred: Int): Int {
            val color = preferred or 0xff000000.toInt()
            if (contrast(surface, color) >= 4.5) return color
            return if (contrast(surface, 0xff000000.toInt()) >= contrast(surface, -1)) 0xff000000.toInt() else -1
        }
        private fun mix(a: Int, b: Int, weight: Int): Int {
            var result = 0xff000000.toInt()
            for (shift in listOf(0, 8, 16)) {
                val channel = (((a ushr shift) and 255) * (1000 - weight) + ((b ushr shift) and 255) * weight + 500) / 1000
                result = result or (channel shl shift)
            }
            return result
        }
        private fun blend(surface: Int, target: Int, weight: Int, ink: Int): Int {
            for (amount in weight downTo 1 step 20) {
                val color = mix(surface, target, amount)
                if (contrast(color, ink) >= 4.5) return color
            }
            return surface
        }
    }
}
