package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.data.theme.SkinKeyPressEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs

/** Bounded, allocation-free motion shared by every skin. Coordinates are relative to the key. */
object SkinParticleMotion {
    class Position(var x: Float = 0f, var y: Float = 0f)

    fun sample(s: SkinKeyPressEffect, density: Float, time: Float, index: Int,
               x: Float, y: Float, dx: Float, dy: Float, fromX: Float, fromY: Float, out: Position) {
        val t = time.coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t)
        val angle = t * 2f * PI.toFloat() * s.rotationTurns
        val phase = index * 2.399963f
        val wave = sin(t * 2f * PI.toFloat() + phase) * s.turbulenceDp * density * sin(t * PI.toFloat())
        when (s.motion) {
            "weave" -> {
                out.x = x + dx * t + wave
                out.y = y + dy * t + sin(t * 4f * PI.toFloat() + phase) * s.turbulenceDp * density * sin(t * PI.toFloat())
            }
            "follow" -> {
                out.x = fromX * (1f - eased) + x * (1f - t) + wave
                out.y = fromY * (1f - eased) + y * (1f - t) - wave * .65f
            }
            "orbit", "nebula" -> {
                val spread = if (s.motion == "nebula") .12f + .88f * eased else .5f + .2f * sin(t * PI.toFloat())
                val a = if (s.motion == "nebula" && index % 2 == 0) -angle else angle
                val px = x + dx * spread
                val py = y + dy * spread
                out.x = px * cos(a) - py * sin(a) + wave
                out.y = (px * sin(a) + py * cos(a)) * .72f
            }
            "fountain" -> {
                out.x = x + dx * t * .65f + wave
                out.y = y - abs(dy) * t + s.gravityDp * density * t * t
            }
            else -> {
                val travel = if (s.rippleStyle == "water") eased else t
                out.x = x + dx * travel
                out.y = y + dy * travel
            }
        }
    }
}
