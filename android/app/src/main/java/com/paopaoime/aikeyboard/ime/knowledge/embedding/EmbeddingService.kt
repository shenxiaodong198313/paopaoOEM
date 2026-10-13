package com.paopaoime.aikeyboard.ime.knowledge.embedding

interface EmbeddingService {
    suspend fun embedQuery(text: String): FloatArray
    suspend fun embedDocuments(texts: List<String>): List<FloatArray>
    fun dim(): Int
}

