package com.paopaoime.aikeyboard.ime.knowledge.loader

import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OfficeDocumentLoader : DocumentLoader {
    override suspend fun load(file: File): List<Document> = withContext(Dispatchers.IO) {
        val extension = file.extension.lowercase()
        try {
            val text = when (extension) {
                "docx" -> loadDocx(file)
                else -> "" // 目前仅支持 docx
            }
            if (text.isNotBlank()) {
                listOf(Document(content = text, metadata = mapOf("source" to file.absolutePath, "type" to extension)))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun loadDocx(file: File): String {
        FileInputStream(file).use { fis ->
            val document = XWPFDocument(fis)
            val sb = StringBuilder()
            document.paragraphs.forEach { para ->
                sb.append(para.text).append("\n")
            }
            return sb.toString()
        }
    }

    override fun support(extension: String): Boolean {
        return extension == "docx"
    }
}

