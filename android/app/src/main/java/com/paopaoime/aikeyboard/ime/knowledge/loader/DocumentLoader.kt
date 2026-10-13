package com.paopaoime.aikeyboard.ime.knowledge.loader

import java.io.File

data class Document(
    val content: String,
    val metadata: Map<String, String> = emptyMap()
)

interface DocumentLoader {
    /**
     * 加载并解析文件内容
     */
    suspend fun load(file: File): List<Document>

    /**
     * 是否支持该文件后缀
     */
    fun support(extension: String): Boolean
}

