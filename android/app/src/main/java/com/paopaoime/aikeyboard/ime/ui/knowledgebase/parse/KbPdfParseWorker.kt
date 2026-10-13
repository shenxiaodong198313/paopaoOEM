package com.paopaoime.aikeyboard.ime.ui.knowledgebase.parse

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.paopaoime.aikeyboard.ime.database.DataBaseKT
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

class KbPdfParseWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID).orEmpty()
        if (documentId.isBlank()) return Result.failure()

        val dao = DataBaseKT.instance.kbDocumentDao()
        val doc = dao.getById(documentId) ?: return Result.failure()
        val directoryId = doc.directoryId

        val mime = doc.mimeType.lowercase()
        if (!mime.contains("pdf") && !doc.fileName.lowercase().endsWith(".pdf")) return Result.success()

        val sourceFile = File(File(applicationContext.filesDir, "knowledge_base"), "${doc.directoryId}/${doc.fileName}")
        if (!sourceFile.exists()) {
            val now = System.currentTimeMillis()
            updateState(dao, documentId, STATUS_FAILED, doc.parseProgress, "文件不存在", now)
            DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)
            return Result.failure()
        }

        val outDir = File(applicationContext.filesDir, "knowledge_base_parsed").apply { mkdirs() }
        val outFile = File(outDir, "$documentId.txt")

        return runCatching {
            PDFBoxResourceLoader.init(applicationContext)
            updateState(dao, documentId, STATUS_RUNNING, 0, null, System.currentTimeMillis())
            if (!outFile.exists()) outFile.createNewFile()
            dao.setParsedTextPath(documentId, outFile.absolutePath)

            PDDocument.load(sourceFile).use { pdf ->
                val totalPages = pdf.numberOfPages.coerceAtLeast(1)
                val stripper = PDFTextStripper()

                OutputStreamWriter(FileOutputStream(outFile, false), Charsets.UTF_8).use { writer ->
                    for (pageIndex in 1..totalPages) {
                        stripper.startPage = pageIndex
                        stripper.endPage = pageIndex
                        val text = stripper.getText(pdf).orEmpty()
                        writer.write(formatPageText(pageIndex, text))
                        writer.flush()

                        val progress = ((pageIndex.toFloat() / totalPages.toFloat()) * 100f).toInt().coerceIn(0, 100)
                        updateState(dao, documentId, STATUS_RUNNING, progress, null, System.currentTimeMillis())
                        if (isStopped) break
                    }
                }
            }

            if (isStopped) {
                val now = System.currentTimeMillis()
                updateState(dao, documentId, STATUS_FAILED, doc.parseProgress, "任务取消", now)
                DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)
                return Result.failure()
            }

            val now = System.currentTimeMillis()
            updateState(dao, documentId, STATUS_DONE, 100, null, now)
            dao.updateUpdatedAt(documentId, now)
            DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)
            Result.success()
        }.getOrElse { t ->
            val now = System.currentTimeMillis()
            updateState(dao, documentId, STATUS_FAILED, doc.parseProgress, (t.message ?: t.javaClass.simpleName), now)
            DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)
            Result.failure()
        }
    }

    private fun updateState(
        dao: com.paopaoime.aikeyboard.ime.database.dao.KbDocumentDao,
        documentId: String,
        status: Int,
        progress: Int,
        error: String?,
        now: Long
    ) {
        dao.updateParseState(documentId, status, progress, error, now)
    }

    private fun formatPageText(pageIndex: Int, rawText: String): String {
        val text = rawText.replace("\r\n", "\n").replace("\r", "\n")
        val normalized = text
            .lineSequence()
            .map { it.trimEnd() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        val header = "—— 第${pageIndex}页 ——"
        return if (normalized.isBlank()) {
            "\n\n$header\n\n"
        } else {
            "\n\n$header\n\n$normalized\n"
        }
    }

    companion object {
        private const val KEY_DOCUMENT_ID = "document_id"

        const val STATUS_NONE = 0
        const val STATUS_QUEUED = 1
        const val STATUS_RUNNING = 2
        const val STATUS_DONE = 3
        const val STATUS_FAILED = 4

        fun inputData(documentId: String): Data = Data.Builder()
            .putString(KEY_DOCUMENT_ID, documentId)
            .build()
    }
}


