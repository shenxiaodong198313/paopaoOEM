package com.paopaoime.aikeyboard.ime.knowledge.store

import com.paopaoime.aikeyboard.ime.knowledge.loader.Document
import kotlin.math.sqrt

class InMemoryVectorStore : VectorStore {
    private data class Entry(val doc: Document, val vector: FloatArray)

    private val entries = mutableListOf<Entry>()

    override fun addDocuments(documents: List<Document>, vectors: List<FloatArray>) {
        documents.zip(vectors).forEach { (doc, vec) ->
            entries.add(Entry(doc, vec))
        }
    }

    override fun similaritySearch(queryVector: FloatArray, topK: Int): List<Document> {
        return entries
            .asSequence()
            .map { it.doc to cosineDistance(queryVector, it.vector) }
            .sortedBy { it.second }
            .take(topK)
            .map { (doc, distance) ->
                doc.copy(metadata = doc.metadata + ("score" to distance.toString()))
            }
            .toList()
    }

    override fun getRandomDocuments(limit: Int): List<Document> {
        return entries.shuffled().take(limit).map { it.doc }
    }

    override fun removeDocumentsByDocId(docId: String) {
        entries.removeAll { it.doc.metadata["id"] == docId || it.doc.metadata["docId"] == docId }
    }

    override fun clear() {
        entries.clear()
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        val size = minOf(a.size, b.size)
        if (size == 0) return 1f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in 0 until size) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA.toDouble()) * sqrt(normB.toDouble())
        if (denom == 0.0) return 1f
        val cosine = (dot / denom).toFloat().coerceIn(-1f, 1f)
        return 1f - cosine
    }
}
