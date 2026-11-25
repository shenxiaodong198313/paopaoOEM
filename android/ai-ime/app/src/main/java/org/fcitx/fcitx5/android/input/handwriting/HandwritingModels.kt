// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.handwriting

import android.os.Handler
import android.os.Looper
import org.fcitx.fcitx5.android.utils.appContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** A single CPU session for all three ink surfaces. Only the worker touches the model. */
internal object HandwritingModels {
    private const val MODEL_BYTES = 21_159_378
    private const val LEGACY = "ppocrv5-da72dc72ca4dc220df0dfde68c1dedc31c58d3e76a25871122e5056227d50092.onnx"
    private val worker = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "PaopaoChineseInk")
    }.apply { removeOnCancelPolicy = true }
    private val main = Handler(Looper.getMainLooper())
    private var engine: PpOcrInk? = null
    private var release: ScheduledFuture<*>? = null

    private fun load(): PpOcrInk {
        val bytes = ByteBuffer.allocateDirect(MODEL_BYTES)
        appContext.assets.open("handwriting/ppocrv6-small.onnx").use { input ->
            val channel = Channels.newChannel(input)
            while (bytes.hasRemaining()) check(channel.read(bytes) >= 0)
            check(input.read() == -1)
        }
        bytes.flip()
        val tokens = appContext.assets.open("handwriting/tokens.txt").bufferedReader().use { it.readLines() }
        check(tokens.size == 18_709 && tokens.last() == " ")
        val model = PpOcrInk(bytes, tokens)
        val old = File(appContext.filesDir, "handwriting")
        File(old, LEGACY).delete()
        File(old, "$LEGACY.part").delete()
        return model
    }

    fun <T> submit(active: () -> Boolean, action: (PpOcrInk) -> T, complete: (T?) -> Unit) {
        worker.execute {
            if (!active()) return@execute
            release?.cancel(false)
            val result = runCatching {
                val model = engine ?: load().also { engine = it }
                action(model)
            }.getOrNull()
            release = worker.schedule({
                engine?.close()
                engine = null
            }, 30, TimeUnit.SECONDS)
            main.post { if (active()) complete(result) }
        }
    }

    fun ensureReady(complete: (Boolean) -> Unit) = submit({ true }, { true }) { complete(it == true) }
}

class ChineseInkRecognizer {
    @Volatile private var closed = false
    fun recognize(strokes: List<List<InkPoint>>, complete: (List<String>?) -> Unit) =
        HandwritingModels.submit({ !closed }, { it.recognize(strokes) }, complete)
    fun close() { closed = true }
}
