// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.handwriting

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import kotlin.math.*

/** Kotlin port of the iOS/Harmony rasterizer and prefix CTC decoder; no network or UI. */
internal class PpOcrInk(model: ByteBuffer, dictionary: List<String>) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val tokens = listOf("") + dictionary
    private val allowed = chineseClasses(tokens)
    private val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(1); options.setInterOpNumThreads(1)
        options.setCPUArenaAllocator(false); options.setMemoryPatternOptimization(false)
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT)
        env.setTelemetry(false)
        env.createSession(model, options)
    }
    fun recognize(strokes: List<List<InkPoint>>): List<String> {
        val (pixels, width) = raster(strokes)
        val channels = FloatArray(pixels.size * 3)
        for (c in 0..2) pixels.copyInto(channels, c * pixels.size)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(channels), longArrayOf(1, 3, 48, width.toLong())).use { input ->
            session.run(mapOf(session.inputNames.first() to input)).use { result ->
                val output = result[0] as OnnxTensor
                val shape = output.info.shape
                check(shape.size == 3 && shape[0] == 1L && shape[2] == tokens.size.toLong())
                return decode(output.floatBuffer, shape[1].toInt(), tokens, allowed)
            }
        }
    }
    override fun close() = session.close()

    companion object {
        internal fun raster(strokes: List<List<InkPoint>>): Pair<FloatArray, Int> {
            val points = strokes.flatten()
            require(points.isNotEmpty() && points.size <= 65_536)
            require(points.all { it.x.isFinite() && it.y.isFinite() })
            val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
            val flatWidth = strokes.filter { it.isNotEmpty() }.maxOf { stroke -> stroke.maxOf { it.x } - stroke.minOf { it.x } }
            var scale = 38f / max(12f, if (maxY - minY < 12f) flatWidth else maxY - minY)
            val contentWidth = (ceil((maxX - minX) * scale).toInt() + 16).coerceIn(48, 1536)
            scale = min(scale, (contentWidth - 12f) / max(1f, maxX - minX))
            val width = max(320, contentWidth)
            val pixels = FloatArray(48 * width)
            for (row in 0 until 48) java.util.Arrays.fill(pixels, row * width, row * width + contentWidth, 1f)
            fun dot(x: Float, y: Float) {
                for (yy in max(0, y.toInt() - 2) until min(48, y.toInt() + 3))
                    for (xx in max(0, x.toInt() - 2) until min(width, x.toInt() + 3)) {
                        val ink = (1.7f - hypot(xx - x, yy - y)).coerceIn(0f, 1f)
                        pixels[yy * width + xx] = min(pixels[yy * width + xx], 1f - 2f * ink)
                    }
            }
            for (stroke in strokes) {
                var px = -1f; var py = -1f
                for (point in stroke) {
                    val x = (point.x - minX) * scale + 6
                    val y = (point.y - minY) * scale + (48f - (maxY - minY) * scale) / 2
                    if (px < 0) dot(x, y) else {
                        val n = max(1, ceil(hypot(x - px, y - py) * 2).toInt())
                        for (j in 1..n) dot(px + (x - px) * j / n, py + (y - py) * j / n)
                    }
                    px = x; py = y
                }
            }
            return pixels to width
        }
        private const val NEG = -1e100
        private fun add(a: Double, b: Double): Double {
            val high = max(a, b); val low = min(a, b)
            return if (low <= NEG) high else high + ln1p(exp(low - high))
        }
        private data class Score(var blank: Double = NEG, var text: Double = NEG) {
            val total get() = add(blank, text)
        }
        private fun isHan(cp: Int) = cp == 0x3007 || cp in 0x3400..0x4dbf || cp in 0x4e00..0x9fff ||
            cp in 0xf900..0xfaff || cp in 0x20000..0x2fa1f || cp in 0x30000..0x323af
        private const val MARKS = "，。！？；：、‘’“”（）【】《》〈〉〔〕［］｛｝…—·￥％＋－＝／＊＃＠＆～ ,.!?;:()[]{}+-=*/%#$&@_~^|<>\\\"'"
        private fun chineseText(text: String) = text.isNotEmpty() && text.codePoints().allMatch { cp ->
            isHan(cp) || cp in 48..57 || cp in 0xff10..0xff19 || (cp <= 0xffff && cp.toChar() in MARKS)
        }
        private fun hanProjection(text: String) = buildString {
            text.codePoints().forEach { cp -> if (isHan(cp)) appendCodePoint(cp) }
        }
        private fun aliasSpace(c: Char) = c in " \t\r\n\u00a0\u3000\u200b\ufeff"
        private fun normalizedCandidate(text: String): String {
            val compact = text.filterNot(::aliasSpace)
            return if (hanProjection(compact).isNotEmpty() && chineseText(compact)) compact else text.trim(::aliasSpace)
        }
        private fun compareCodePoints(a: String, b: String): Int {
            var i = 0; var j = 0
            while (i < a.length && j < b.length) {
                val x = a.codePointAt(i); val y = b.codePointAt(j)
                if (x != y) return x.compareTo(y)
                i += Character.charCount(x); j += Character.charCount(y)
            }
            return (a.length - i).compareTo(b.length - j)
        }
        internal fun cleanCandidates(ranked: List<Pair<String, Double>>): List<String> {
            val scores = sortedMapOf<String, Double>()
            for ((raw, score) in ranked) {
                val text = normalizedCandidate(raw)
                if (text.isNotEmpty()) scores[text] = scores[text]?.let { add(it, score) } ?: score
            }
            return scores.entries.sortedWith { a, b ->
                val score = b.value.compareTo(a.value)
                if (score != 0) score else compareCodePoints(a.key, b.key)
            }.filter { (text, score) ->
                val han = if (chineseText(text)) hanProjection(text) else ""
                han.isEmpty() || han == text || (scores[han] ?: NEG) <= score
            }.map { it.key }
        }
        private fun chineseClasses(tokens: List<String>) =
            tokens.indices.filter { it > 0 && chineseText(tokens[it]) }.toIntArray()

        internal fun decode(data: FloatBuffer, steps: Int, tokens: List<String>,
                            allowed: IntArray = chineseClasses(tokens)): List<String> {
            val classes = tokens.size
            require(steps in 1..4096 && data.remaining() == steps * classes)
            var beams = linkedMapOf(emptyList<Int>() to Score(blank = 0.0))
            for (t in 0 until steps) {
                val row = t * classes
                val top = mutableListOf<Int>()
                for (c in allowed) {
                    val p = data.get(row + c)
                    var index = 0
                    while (index < top.size && data.get(row + top[index]) >= p) index++
                    if (index < 6) { top.add(index, c); if (top.size > 6) top.removeAt(6) }
                }
                val next = linkedMapOf<List<Int>, Score>()
                for ((prefix, score) in beams) {
                    val same = next.getOrPut(prefix) { Score() }
                    same.blank = add(same.blank, score.total + ln(max(data.get(row), 1e-30f).toDouble()))
                    for (c in top) {
                        val p = ln(max(data.get(row + c), 1e-30f).toDouble())
                        val extended = next.getOrPut(prefix + c) { Score() }
                        if (prefix.lastOrNull() == c) {
                            same.text = add(same.text, score.text + p)
                            extended.text = add(extended.text, score.blank + p)
                        } else extended.text = add(extended.text, score.total + p)
                    }
                }
                beams = LinkedHashMap(next.entries.sortedByDescending { it.value.total }.take(24).associate { it.key to it.value })
            }
            return cleanCandidates(beams.entries.sortedByDescending { it.value.total }
                .map { (prefix, score) -> prefix.joinToString("") { tokens[it] } to score.total }).take(8)
        }
    }
}
