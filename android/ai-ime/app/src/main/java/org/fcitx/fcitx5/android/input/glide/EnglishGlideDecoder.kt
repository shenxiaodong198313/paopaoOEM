// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.glide

import java.io.InputStream
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.abs

data class GlidePoint(val x: Float, val y: Float)
data class GlideRequest(val points: List<GlidePoint>, val centers: Map<Char, GlidePoint>, val keyWidth: Float,
    val capitalized: Boolean, val capsLocked: Boolean)

/** Geometric word matching over the existing full Fcitx English lexicon. No network. */
class EnglishGlideDecoder(stream: InputStream) {
    data class Word(val text: String, val frequency: Int)
    private val buckets: Map<String, List<Word>>
    init {
        val bytes = stream.use { it.readBytes() }
        require(bytes.size >= 12 && String(bytes, 0, 8, Charsets.US_ASCII) == "FSCD0000")
        fun number(at: Int, size: Int) = (0 until size).fold(0) { n, i -> n or ((bytes[at + i].toInt() and 255) shl (8 * i)) }
        val count = number(8, 4); require(count in 1..1_000_000)
        var cursor = 12
        val words = ArrayList<Word>()
        repeat(count) {
            require(cursor + 2 < bytes.size)
            val frequency = number(cursor, 2); cursor += 2
            val start = cursor
            while (cursor < bytes.size && bytes[cursor] != 0.toByte()) cursor++
            require(cursor < bytes.size)
            val word = String(bytes, start, cursor - start, Charsets.UTF_8); cursor++
            if (word.length in 2..30 && word.all { it in 'a'..'z' }) words.add(Word(word, frequency))
        }
        buckets = words.groupBy { "${it.text.first()}${it.text.last()}" }
    }

    fun decode(request: GlideRequest): List<String> {
        if (request.points.size < 2 || request.centers.size != 26 || request.keyWidth <= 0) return emptyList()
        val start = request.centers.minBy { distance(it.value, request.points.first()) }.key
        val end = request.centers.minBy { distance(it.value, request.points.last()) }.key
        val measured = sample(request.points)
        val measuredLength = length(request.points).coerceAtLeast(1f)
        val scored = buckets["$start$end"].orEmpty().mapNotNull { word ->
            val path = word.text.fold(mutableListOf<GlidePoint>()) { result, char ->
                val p = request.centers.getValue(char)
                if (result.lastOrNull() != p) result.add(p)
                result
            }
            if (path.size < 2) return@mapNotNull null
            val expected = sample(path)
            val shape = measured.zip(expected).sumOf { (a, b) ->
                val d = distance(a, b) / request.keyWidth; (d * d).toDouble()
            } / measured.size
            val lengthPenalty = abs(ln((length(path).coerceAtLeast(1f) / measuredLength).toDouble()))
            val score = shape + .35 * lengthPenalty - .035 * ln(1.0 + word.frequency)
            if (shape > 2.0) null else word.text to score
        }.sortedBy { it.second }
        return scored.take(8).map { (word, _) ->
            if (request.capsLocked) word.uppercase() else if (request.capitalized) word.replaceFirstChar(Char::uppercase) else word
        }
    }
    companion object {
        fun distance(a: GlidePoint, b: GlidePoint) = hypot(a.x - b.x, a.y - b.y)
        fun length(points: List<GlidePoint>) = points.zipWithNext().sumOf { (a, b) -> distance(a, b).toDouble() }.toFloat()
        fun sample(points: List<GlidePoint>, count: Int = 32): List<GlidePoint> {
            if (points.size == 1) return List(count) { points[0] }
            val total = length(points)
            if (total == 0f) return List(count) { points[0] }
            val result = ArrayList<GlidePoint>(count)
            var edge = 1; var passed = 0f
            repeat(count) { index ->
                val target = total * index / (count - 1)
                while (edge < points.lastIndex && passed + distance(points[edge - 1], points[edge]) < target) {
                    passed += distance(points[edge - 1], points[edge]); edge++
                }
                val a = points[edge - 1]; val b = points[edge]
                val t = ((target - passed) / distance(a, b).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
                result.add(GlidePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            }
            return result
        }
    }
}
