package com.paopaoime.aikeyboard.ime.knowledge.loader

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfDocumentLoader(private val context: Context) : DocumentLoader {
    
    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            // Ignore if already initialized
        }
    }

    override suspend fun load(file: File): List<Document> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(file).use { document ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                listOf(Document(content = text, metadata = mapOf("source" to file.absolutePath, "type" to "pdf")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun support(extension: String): Boolean {
        return extension.equals("pdf", ignoreCase = true)
    }
}

