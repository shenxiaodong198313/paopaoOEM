package com.paopaoime.aikeyboard.ime.ui.knowledgebase.parse

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.paopaoime.aikeyboard.ime.database.DataBaseKT
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipFile
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument

class KbOoxmlParseWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID).orEmpty()
        if (documentId.isBlank()) return Result.failure()

        val dao = DataBaseKT.instance.kbDocumentDao()
        val doc = dao.getById(documentId) ?: return Result.failure()
        val directoryId = doc.directoryId

        val fileName = doc.fileName.lowercase()
        val ext = fileName.substringAfterLast('.', "")
        val supported = ext == "docx" || ext == "xlsx" || ext == "pptx"
        if (!supported) return Result.success()

        val sourceFile = File(File(applicationContext.filesDir, "knowledge_base"), "${doc.directoryId}/${doc.fileName}")
        if (!sourceFile.exists()) {
            val now = System.currentTimeMillis()
            dao.updateParseState(documentId, STATUS_FAILED, doc.parseProgress, "文件不存在", now)
            DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)
            return Result.failure()
        }

        val outDir = File(applicationContext.filesDir, "knowledge_base_parsed").apply { mkdirs() }
        val outFile = File(outDir, "$documentId.txt")

        return runCatching {
            var lastProgress = -1
            fun updateProgress(progress: Int) {
                val p = progress.coerceIn(0, 100)
                if (p == lastProgress) return
                lastProgress = p
                dao.updateParseState(documentId, STATUS_RUNNING, p, null, System.currentTimeMillis())
            }

            updateProgress(0)
            if (!outFile.exists()) outFile.createNewFile()
            dao.setParsedTextPath(documentId, outFile.absolutePath)

            val text = when (ext) {
                "docx" -> extractDocxText(sourceFile) { updateProgress(it) }
                "xlsx" -> extractXlsxText(sourceFile) { updateProgress(it) }
                "pptx" -> extractPptxText(sourceFile) { updateProgress(it) }
                else -> ""
            }

            OutputStreamWriter(FileOutputStream(outFile, false), Charsets.UTF_8).use { writer ->
                writer.write(text)
                writer.flush()
            }

            if (isStopped) {
                val now = System.currentTimeMillis()
                dao.updateParseState(documentId, STATUS_FAILED, lastProgress.coerceAtLeast(0), "任务取消", now)
                DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)
                return Result.failure()
            }

            val now = System.currentTimeMillis()
            dao.updateParseState(documentId, STATUS_DONE, 100, null, now)
            dao.updateUpdatedAt(documentId, now)
            DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)
            Result.success()
        }.getOrElse { t ->
            val now = System.currentTimeMillis()
            dao.updateParseState(
                documentId,
                STATUS_FAILED,
                doc.parseProgress,
                (t.message ?: t.javaClass.simpleName),
                now
            )
            DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)
            Result.failure()
        }
    }

    private fun extractDocxText(file: File, onProgress: (Int) -> Unit): String {
        return runCatching {
            onProgress(5)
            FileInputStream(file).use { input ->
                XWPFDocument(input).use { document ->
                    onProgress(30)
                    XWPFWordExtractor(document).use { extractor ->
                        val raw = extractor.text.orEmpty()
                        onProgress(90)
                        raw.replace("\r\n", "\n")
                            .replace("\r", "\n")
                            .replace(Regex("\n{3,}"), "\n\n")
                            .trim()
                            .let { if (it.isBlank()) "" else it + "\n" }
                    }
                }
            }
        }.getOrElse {
            extractDocxTextLegacy(file, onProgress)
        }
    }

    private fun extractDocxTextLegacy(file: File, onProgress: (Int) -> Unit): String {
        onProgress(5)
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return ""
            val input = zip.getInputStream(entry)
            return input.use { stream ->
                val parser = android.util.Xml.newPullParser()
                parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true)
                parser.setInput(stream, Charsets.UTF_8.name())

                val out = StringBuilder()
                var event = parser.eventType
                var inText = false
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        org.xmlpull.v1.XmlPullParser.START_TAG -> {
                            val name = parser.name
                            if (name == "p") {
                                if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                            } else if (name == "t") {
                                inText = true
                            } else if (name == "tab") {
                                out.append('\t')
                            } else if (name == "br") {
                                out.append('\n')
                            }
                        }
                        org.xmlpull.v1.XmlPullParser.TEXT -> {
                            if (inText) out.append(parser.text)
                        }
                        org.xmlpull.v1.XmlPullParser.END_TAG -> {
                            if (parser.name == "t") inText = false
                        }
                    }
                    event = parser.next()
                }
                out.toString()
                    .replace(Regex("[ \\t]{2,}"), " ")
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()
                    .let { if (it.isBlank()) "" else it + "\n" }
                    .also { onProgress(95) }
            }
        }
    }

    private fun extractPptxText(file: File, onProgress: (Int) -> Unit): String {
        return runCatching {
            onProgress(5)
            FileInputStream(file).use { input ->
                XMLSlideShow(input).use { show ->
                    val slides = show.slides
                    if (slides.isEmpty()) return ""
                    val out = StringBuilder()
                    val total = slides.size.coerceAtLeast(1)
                    slides.forEachIndexed { index, slide ->
                        if (isStopped) throw IllegalStateException("任务取消")
                        val progress = ((index.toFloat() / total.toFloat()) * 90f + 5f).toInt().coerceIn(0, 100)
                        onProgress(progress)
                        if (out.isNotEmpty()) out.append("\n\n")
                        out.append("—— Slide ${index + 1} ——\n\n")
                        slide.shapes.forEach { shape ->
                            if (shape is XSLFTextShape) {
                                var paragraphHasText = false
                                shape.textParagraphs.forEach { paragraph ->
                                    val line = paragraph.text?.trim() ?: ""
                                    if (line.isNotEmpty()) {
                                        paragraphHasText = true
                                        out.append(line).append('\n')
                                    }
                                }
                                if (paragraphHasText) out.append('\n')
                            }
                        }
                    }
                    out.toString()
                        .replace(Regex("\n{3,}"), "\n\n")
                        .trim()
                        .let { if (it.isBlank()) "" else it + "\n" }
                        .also { onProgress(95) }
                }
            }
        }.getOrElse {
            extractPptxTextLegacy(file, onProgress)
        }
    }

    private fun extractPptxTextLegacy(file: File, onProgress: (Int) -> Unit): String {
        ZipFile(file).use { zip ->
            val slideEntries = zip.entries().toList()
                .map { it.name }
                .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
                .sortedWith(compareBy({ it.length }, { it }))

            if (slideEntries.isEmpty()) return ""

            val out = StringBuilder()
            val total = slideEntries.size.coerceAtLeast(1)
            slideEntries.forEachIndexed { index, name ->
                if (isStopped) throw IllegalStateException("任务取消")
                val progress = ((index.toFloat() / total.toFloat()) * 90f + 5f).toInt().coerceIn(0, 100)
                onProgress(progress)

                val entry = zip.getEntry(name) ?: return@forEachIndexed
                zip.getInputStream(entry).use { stream ->
                    val parser = android.util.Xml.newPullParser()
                    parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true)
                    parser.setInput(stream, Charsets.UTF_8.name())

                    if (out.isNotEmpty()) out.append("\n\n")
                    out.append("—— 第${index + 1}页 ——\n\n")

                    var event = parser.eventType
                    var inText = false
                    var paragraphHasText = false
                    while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            org.xmlpull.v1.XmlPullParser.START_TAG -> {
                                val tag = parser.name
                                if (tag == "t") {
                                    inText = true
                                } else if (tag == "p") {
                                    paragraphHasText = false
                                }
                            }
                            org.xmlpull.v1.XmlPullParser.TEXT -> {
                                if (inText) {
                                    val text = parser.text.orEmpty()
                                    if (text.isNotBlank()) paragraphHasText = true
                                    out.append(text)
                                }
                            }
                            org.xmlpull.v1.XmlPullParser.END_TAG -> {
                                val tag = parser.name
                                if (tag == "t") {
                                    inText = false
                                } else if (tag == "p") {
                                    if (paragraphHasText) out.append('\n')
                                }
                            }
                        }
                        event = parser.next()
                    }
                }
            }

            return out.toString()
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
                .let { if (it.isBlank()) "" else it + "\n" }
                .also { onProgress(95) }
        }
    }

    private fun extractXlsxText(file: File, onProgress: (Int) -> Unit): String {
        return runCatching {
            onProgress(5)
            FileInputStream(file).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val out = StringBuilder()
                    val formatter = DataFormatter()
                    val sheetCount = workbook.numberOfSheets
                    if (sheetCount <= 0) return ""
                    val total = sheetCount.coerceAtLeast(1)
                    for (sheetIndex in 0 until sheetCount) {
                        if (isStopped) throw IllegalStateException("任务取消")
                        val progress = ((sheetIndex.toFloat() / total.toFloat()) * 90f + 5f).toInt().coerceIn(0, 100)
                        onProgress(progress)
                        val sheet = workbook.getSheetAt(sheetIndex)
                        if (out.isNotEmpty()) out.append("\n\n")
                        out.append("—— Sheet ${sheetIndex + 1} ——\n\n")
                        val rowIterator = sheet.rowIterator()
                        while (rowIterator.hasNext()) {
                            val row = rowIterator.next()
                            val rowBuilder = StringBuilder()
                            var rowHasAnyValue = false
                            val lastCellNum = row.lastCellNum.toInt()
                            for (col in 0 until lastCellNum) {
                                val cell = row.getCell(col)
                                val value = if (cell == null) "" else formatter.formatCellValue(cell)
                                val normalized = value.replace("\n", " ").trim()
                                if (normalized.isNotEmpty()) {
                                    if (rowBuilder.isNotEmpty()) rowBuilder.append("    ")
                                    rowBuilder.append(normalized)
                                    rowHasAnyValue = true
                                }
                            }
                            if (rowHasAnyValue) {
                                out.append(rowBuilder.toString().trimEnd()).append('\n')
                            }
                        }
                    }
                    out.toString()
                        .replace(Regex("\n{3,}"), "\n\n")
                        .trim()
                        .let { if (it.isBlank()) "" else it + "\n" }
                        .also { onProgress(95) }
                }
            }
        }.getOrElse {
            extractXlsxTextLegacy(file, onProgress)
        }
    }

    private fun extractXlsxTextLegacy(file: File, onProgress: (Int) -> Unit): String {
        ZipFile(file).use { zip ->
            val sharedStrings = readXlsxSharedStrings(zip)
            val sheetEntries = zip.entries().toList()
                .map { it.name }
                .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sortedWith(compareBy({ it.length }, { it }))

            if (sheetEntries.isEmpty()) return ""

            val out = StringBuilder()
            val total = sheetEntries.size.coerceAtLeast(1)
            sheetEntries.forEachIndexed { sheetIndex, name ->
                if (isStopped) throw IllegalStateException("任务取消")
                val progress = ((sheetIndex.toFloat() / total.toFloat()) * 90f + 5f).toInt().coerceIn(0, 100)
                onProgress(progress)

                val entry = zip.getEntry(name) ?: return@forEachIndexed
                zip.getInputStream(entry).use { stream ->
                    val parser = android.util.Xml.newPullParser()
                    parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true)
                    parser.setInput(stream, Charsets.UTF_8.name())

                    if (out.isNotEmpty()) out.append("\n\n")
                    out.append("—— Sheet ${sheetIndex + 1} ——\n\n")

                    var event = parser.eventType
                    var cellType: String? = null
                    var inV = false
                    var rowBuilder = StringBuilder()
                    var rowHasAnyValue = false

                    fun flushRow() {
                        val row = rowBuilder.toString().trimEnd()
                        if (rowHasAnyValue && row.isNotBlank()) {
                            out.append(row).append('\n')
                        }
                        rowBuilder = StringBuilder()
                        rowHasAnyValue = false
                    }

                    while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            org.xmlpull.v1.XmlPullParser.START_TAG -> {
                                val tag = parser.name
                                if (tag == "row") {
                                    rowBuilder = StringBuilder()
                                    rowHasAnyValue = false
                                } else if (tag == "c") {
                                    cellType = parser.getAttributeValue(null, "t")
                                } else if (tag == "v") {
                                    inV = true
                                }
                            }
                            org.xmlpull.v1.XmlPullParser.TEXT -> {
                                if (inV) {
                                    val raw = parser.text.orEmpty()
                                    val value = if (cellType == "s") {
                                        val idx = raw.toIntOrNull()
                                        if (idx == null) "" else sharedStrings.getOrNull(idx).orEmpty()
                                    } else {
                                        raw
                                    }
                                    val normalized = value.replace("\n", " ").trim()
                                    if (normalized.isNotEmpty()) {
                                        if (rowBuilder.isNotEmpty()) rowBuilder.append("    ")
                                        rowBuilder.append(normalized)
                                        rowHasAnyValue = true
                                    }
                                }
                            }
                            org.xmlpull.v1.XmlPullParser.END_TAG -> {
                                val tag = parser.name
                                if (tag == "v") {
                                    inV = false
                                } else if (tag == "c") {
                                    cellType = null
                                } else if (tag == "row") {
                                    flushRow()
                                }
                            }
                        }
                        event = parser.next()
                    }

                    flushRow()
                }
            }

            return out.toString()
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
                .let { if (it.isBlank()) "" else it + "\n" }
                .also { onProgress(95) }
        }
    }

    private fun readXlsxSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        zip.getInputStream(entry).use { stream ->
            val parser = android.util.Xml.newPullParser()
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true)
            parser.setInput(stream, Charsets.UTF_8.name())

            val out = ArrayList<String>()
            val current = StringBuilder()
            var event = parser.eventType
            var inT = false
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (event) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        if (parser.name == "si") {
                            current.setLength(0)
                        } else if (parser.name == "t") {
                            inT = true
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        if (inT) current.append(parser.text.orEmpty())
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        val tag = parser.name
                        if (tag == "t") {
                            inT = false
                        } else if (tag == "si") {
                            out.add(current.toString())
                        }
                    }
                }
                event = parser.next()
            }
            return out
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

