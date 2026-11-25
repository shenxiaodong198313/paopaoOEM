// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.handwriting

import android.graphics.Path
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min

/** Builds only the visible ink silhouette. Recognition always receives the original timed samples. */
internal object ChineseInkBrush {
    fun outline(path: Path, points: List<InkPoint>, width: Float, lifted: Boolean) {
        path.rewind()
        val first = points.firstOrNull() ?: return
        var length = 0f
        for (i in 1 until points.size) {
            length += hypot(points[i].x - points[i-1].x, points[i].y - points[i-1].y)
        }
        if (length < width * .35f) {
            val x = first.x
            val y = first.y
            path.moveTo(x - width * .42f, y - width * .62f)
            path.quadTo(x + width * .78f, y - width * .15f, x + width * .48f, y + width * .47f)
            path.quadTo(x - width * .22f, y + width * .90f, x - width * .42f, y - width * .62f)
            path.close()
            return
        }
        // Draw each variable-width strip into one path. Rounded joins also cover sharp turns.
        var travelled = 0f
        var speed = 0f
        var previousRadius = width * .28f
        for (i in 1 until points.size) {
            val a = points[i-1]
            val b = points[i]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val distance = hypot(dx, dy)
            if (distance < .001f) continue
            val nx = -dy / distance
            val ny = dx / distance
            val elapsed = (b.time - a.time).coerceAtLeast(1L)
            val measured = (distance / (elapsed * width)).coerceIn(0f, 1f)
            speed += (measured - speed) * .35f
            val steps = ceil(distance / (width * .8f)).toInt().coerceIn(1, 48)
            var x = a.x
            var y = a.y
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                val nextX = a.x + dx * t
                val nextY = a.y + dy * t
                val position = travelled + distance * t
                val tail = ((length - position) / min(length * .45f, width * 4f)).coerceIn(0f, 1f)
                val downward = (dy / distance * 2f).coerceIn(0f, 1f)
                val rightFall = downward * (dx / distance * 2f).coerceIn(0f, 1f)
                val start = (position / (width * 2f)).coerceAtMost(1f)
                val belly = rightFall * (position / length) * .35f
                val taper = if (lifted) 1f - (1f - tail) * (.24f + .68f * downward) else 1f
                val radius = width * (.38f + .20f * start + belly - .18f * speed) * taper
                path.moveTo(x + nx * previousRadius, y + ny * previousRadius)
                path.lineTo(nextX + nx * radius, nextY + ny * radius)
                path.lineTo(nextX - nx * radius, nextY - ny * radius)
                path.lineTo(x - nx * previousRadius, y - ny * previousRadius)
                path.close()
                path.addCircle(x, y, previousRadius, Path.Direction.CCW)
                previousRadius = radius
                x = nextX; y = nextY
            }
            travelled += distance
            if (i == points.lastIndex || travelled >= length) path.addCircle(x, y, previousRadius, Path.Direction.CCW)
        }
    }
}
