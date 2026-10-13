package com.paopaoime.aikeyboard.ime.knowledge.embedding

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class OnnxEmbeddingService(
    private val context: Context,
) : EmbeddingService {

    private val dimension = 384

    override suspend fun embedQuery(text: String): FloatArray {
        return withContext(Dispatchers.Default) { buildHashEmbedding(text) }
    }

    override suspend fun embedDocuments(texts: List<String>): List<FloatArray> {
        return withContext(Dispatchers.Default) { texts.map { buildHashEmbedding(it) } }
    }

    private fun buildHashEmbedding(text: String): FloatArray {
        val vector = FloatArray(dimension)
        text.windowed(size = 2, step = 1, partialWindows = true)
            .filter { it.isNotBlank() }
            .forEach { token ->
                val hash = token.hashCode()
                val index = (hash and Int.MAX_VALUE) % dimension
                vector[index] += if ((hash and 1) == 0) 1f else -1f
            }
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }

    override fun dim(): Int {
        return dimension
    }
    
    fun close() {
        // No native resources are held in the OEM lightweight build.
    }
}

