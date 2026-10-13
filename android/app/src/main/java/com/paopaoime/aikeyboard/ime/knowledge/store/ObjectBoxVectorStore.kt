package com.paopaoime.aikeyboard.ime.knowledge.store

import com.paopaoime.aikeyboard.ime.knowledge.loader.Document
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlin.math.sqrt

class ObjectBoxVectorStore(private val boxStore: BoxStore) : VectorStore {
    private val box: Box<KnowledgeChunk> = boxStore.boxFor(KnowledgeChunk::class.java)

    override fun addDocuments(documents: List<Document>, vectors: List<FloatArray>) {
        val chunks = documents.zip(vectors).map { (doc, vec) ->
            KnowledgeChunk(
                docId = doc.metadata["id"].orEmpty(),
                text = doc.content,
                metadata = doc.metadata.toString(),
                vector = vec
            )
        }
        box.put(chunks)
    }

    override fun similaritySearch(queryVector: FloatArray, topK: Int): List<Document> {
        return box.query().build().use { q ->
            q.find()
                .asSequence()
                .map { chunk -> chunk to cosineDistance(queryVector, chunk.vector ?: floatArrayOf()) }
                .sortedBy { it.second }
                .take(topK)
                .map { (chunk, score) ->
                    Document(
                        chunk.text,
                        mapOf(
                            "score" to score.toString(),
                            "metadata" to chunk.metadata,
                            "docId" to chunk.docId
                        )
                    )
                }
                .toList()
        }
    }

    override fun getRandomDocuments(limit: Int): List<Document> {
        val count = box.count()
        if (count == 0L) return emptyList()

        // Get all IDs and shuffle them to pick random ones
        // Note: For very large datasets, fetching all IDs might be memory intensive,
        // but for typical mobile knowledge base sizes, it should be fine.
        val allIds = box.query().build().use { it.findIds() } // LongArray
        if (allIds.isEmpty()) return emptyList()

        val selectedIds = if (allIds.size <= limit) {
            allIds
        } else {
            allIds.toList().shuffled().take(limit).toLongArray()
        }

        val chunks = box.get(selectedIds)
        return chunks.map { chunk ->
            Document(
                chunk.text,
                mapOf(
                    "metadata" to chunk.metadata,
                    "docId" to chunk.docId
                )
            )
        }
    }

    override fun removeDocumentsByDocId(docId: String) {
        box.query().build().use { q ->
            val ids = q.find().filter { it.docId == docId }.map { it.id }
            if (ids.isNotEmpty()) {
                box.removeByIds(ids)
            }
        }
    }

    override fun clear() {
        box.removeAll()
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

