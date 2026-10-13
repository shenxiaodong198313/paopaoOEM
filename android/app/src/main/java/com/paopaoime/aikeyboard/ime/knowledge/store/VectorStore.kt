package com.paopaoime.aikeyboard.ime.knowledge.store

import com.paopaoime.aikeyboard.ime.knowledge.loader.Document

interface VectorStore {
    fun addDocuments(documents: List<Document>, vectors: List<FloatArray>)
    fun similaritySearch(queryVector: FloatArray, topK: Int): List<Document>
    fun getRandomDocuments(limit: Int): List<Document>
    fun removeDocumentsByDocId(docId: String)
    fun clear()
}

