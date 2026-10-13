package com.paopaoime.aikeyboard.ime.knowledge

import android.content.Context
import com.paopaoime.aikeyboard.ime.knowledge.embedding.OnnxEmbeddingService
import com.paopaoime.aikeyboard.ime.knowledge.loader.Document
import com.paopaoime.aikeyboard.ime.knowledge.store.InMemoryVectorStore
import com.paopaoime.aikeyboard.ime.knowledge.loader.OfficeDocumentLoader
import com.paopaoime.aikeyboard.ime.knowledge.loader.PdfDocumentLoader
import com.paopaoime.aikeyboard.ime.knowledge.splitter.RecursiveCharacterTextSplitter
import java.io.File

/**
 * 知识库管理类
 * 负责协调文档加载、切分、向量化和存储。
 *
 * 使用前请确保：
 * 1. 将 ONNX Embedding 模型 (如 bge-small-zh-v1.5.onnx) 放入 context.filesDir 或指定路径。
 * 2. 将 vocab.txt 放入 context.filesDir 或指定路径。
 * 3. 确保 BoxStore 已正确初始化。
 */
class KnowledgeBaseManager(
    private val context: Context
) {
    
    private val embeddingService by lazy {
        OnnxEmbeddingService(context)
    }
    
    private val vectorStore by lazy {
        InMemoryVectorStore()
    }
    
    private val pdfLoader = PdfDocumentLoader(context)
    private val officeLoader = OfficeDocumentLoader()
    private val textSplitter = RecursiveCharacterTextSplitter()

    /**
     * 添加文档到知识库
     * @param file 文档文件
     * @return 添加的切片数量
     */
    suspend fun addDocument(file: File): Int {
        val extension = file.extension.lowercase()
        val docs = when {
            pdfLoader.support(extension) -> pdfLoader.load(file)
            officeLoader.support(extension) -> officeLoader.load(file)
            else -> emptyList()
        }
        
        if (docs.isEmpty()) return 0
        
        // Split
        val splitDocs = docs.flatMap { doc ->
            textSplitter.splitText(doc.content).map { chunk ->
                doc.copy(content = chunk)
            }
        }
        
        if (splitDocs.isEmpty()) return 0

        // Embed
        val texts = splitDocs.map { it.content }
        val vectors = embeddingService.embedDocuments(texts)
        
        // Store
        vectorStore.addDocuments(splitDocs, vectors)
        
        return splitDocs.size
    }
    
    /**
     * 添加已解析的文档文本到知识库
     * @param docId 文档ID
     * @param content 文档文本内容
     * @return Pair(添加的切片数量, 向量化数据大小字节数)
     */
    suspend fun addParsedDocument(docId: String, content: String): Pair<Int, Long> {
        if (content.isBlank()) return 0 to 0L
        
        val doc = Document(content, mapOf("id" to docId))
        
        // Split
        val splitDocs = textSplitter.splitText(doc.content).map { chunk ->
            doc.copy(content = chunk)
        }
        
        if (splitDocs.isEmpty()) return 0 to 0L

        // Embed
        val texts = splitDocs.map { it.content }
        val vectors = embeddingService.embedDocuments(texts)
        
        // Store
        vectorStore.addDocuments(splitDocs, vectors)
        
        // Calculate size
        val textSize = texts.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
        val vectorSize = vectors.sumOf { it.size * 4L }
        
        return splitDocs.size to (textSize + vectorSize)
    }

    fun removeDocumentVectors(docId: String) {
        vectorStore.removeDocumentsByDocId(docId)
    }

    /**
     * 搜索相关知识
     * @param query 查询语句
     * @param topK 返回结果数量
     * @return 相关文本列表
     */
    suspend fun search(query: String, topK: Int = 3): List<String> {
        val queryVector = embeddingService.embedQuery(query)
        val results = vectorStore.similaritySearch(queryVector, topK)
        return results.map { it.content }
    }

    data class SearchResult(
        val content: String,
        val score: Float,
        val rawScore: Float,
        val metadata: String?,
        val docId: String?
    )

    suspend fun searchWithScores(query: String, topK: Int = 3): List<SearchResult> {
        val queryVector = embeddingService.embedQuery(query)
        val results = vectorStore.similaritySearch(queryVector, topK)
        return results.mapNotNull { doc ->
            val rawScore = doc.metadata["score"]?.toFloatOrNull() ?: return@mapNotNull null
            val normalized = normalizeScore(rawScore)
            SearchResult(
                content = doc.content,
                score = normalized,
                rawScore = rawScore,
                metadata = doc.metadata["metadata"],
                docId = doc.metadata["docId"]
            )
        }
    }

    /**
     * 随机获取知识库内容
     * @param limit 获取数量
     * @return 文本列表
     */
    suspend fun getRandomContext(limit: Int = 3): List<String> {
        val docs = vectorStore.getRandomDocuments(limit)
        return docs.map { it.content }
    }

    private fun normalizeScore(rawScore: Float): Float {
        if (!rawScore.isFinite()) return 0f
        val normalized = if (rawScore > 1f) {
            1f / (1f + rawScore)
        } else {
            1f - rawScore
        }
        return normalized.coerceIn(0f, 1f)
    }
    
    /**
     * 清空知识库
     */
    fun clear() {
        vectorStore.clear()
    }
    
    fun close() {
        embeddingService.close()
    }
}

