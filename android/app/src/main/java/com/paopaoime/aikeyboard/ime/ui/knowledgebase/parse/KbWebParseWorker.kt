package com.paopaoime.aikeyboard.ime.ui.knowledgebase.parse

import android.content.Context
import androidx.core.text.HtmlCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.paopaoime.aikeyboard.ime.database.DataBaseKT
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import net.dankito.readability4j.Readability4J
import okhttp3.OkHttpClient
import okhttp3.Request

class KbWebParseWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID).orEmpty()
        val url = inputData.getString(KEY_URL).orEmpty()
        if (documentId.isBlank() || url.isBlank()) return Result.failure()

        val dao = DataBaseKT.instance.kbDocumentDao()
        val doc = dao.getById(documentId) ?: return Result.failure()
        val directoryId = doc.directoryId

        val outDir = File(applicationContext.filesDir, "knowledge_base_parsed").apply { mkdirs() }
        val outFile = File(outDir, "$documentId.txt")

        return runCatching {
            updateState(dao, documentId, STATUS_RUNNING, 0, null, System.currentTimeMillis())
            if (!outFile.exists()) outFile.createNewFile()
            dao.setParsedTextPath(documentId, outFile.absolutePath)

            val html = fetchHtml(url)
            updateState(dao, documentId, STATUS_RUNNING, 40, null, System.currentTimeMillis())

            val article = Readability4J(url, html).parse()
            var title = article.title?.trim().orEmpty()
            var text = article.textContent?.trim().orEmpty()
            updateState(dao, documentId, STATUS_RUNNING, 70, null, System.currentTimeMillis())

            if (text.isBlank()) {
                text = extractPlainText(html)
            }
            if (title.isBlank()) {
                title = extractTitle(html)
            }
            if (text.isBlank() && title.isBlank()) {
                val now = System.currentTimeMillis()
                updateState(dao, documentId, STATUS_FAILED, 100, "未提取到正文", now)
                DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)
                return Result.failure()
            }

            val output = buildMarkdownOutput(title, url, text)
            OutputStreamWriter(FileOutputStream(outFile, false), Charsets.UTF_8).use { writer ->
                writer.write(output)
                writer.flush()
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

    private fun fetchHtml(url: String): String {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("请求失败：${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun buildMarkdownOutput(title: String, url: String, text: String): String {
        val normalizedText = text.trim()
        val sb = StringBuilder()
        if (title.isNotBlank()) {
            sb.append("# ").append(title.trim())
            if (normalizedText.isNotBlank()) sb.append("\n\n")
        }
        if (normalizedText.isNotBlank()) {
            sb.append(normalizedText)
        }
        if (sb.isNotEmpty()) sb.append("\n\n")
        sb.append("[查看原文](").append(url).append(")\n")
        return sb.toString()
    }

    private fun extractTitle(html: String): String {
        val match = Regex("(?is)<title[^>]*>(.*?)</title>").find(html) ?: return ""
        return HtmlCompat.fromHtml(match.groupValues[1], HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .trim()
    }

    private fun extractPlainText(html: String): String {
        val cleaned = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
        val text = HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        return text.replace(Regex("\\s+"), " ").trim()
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

    companion object {
        private const val KEY_DOCUMENT_ID = "document_id"
        private const val KEY_URL = "url"

        const val STATUS_NONE = 0
        const val STATUS_QUEUED = 1
        const val STATUS_RUNNING = 2
        const val STATUS_DONE = 3
        const val STATUS_FAILED = 4

        fun inputData(documentId: String, url: String): Data = Data.Builder()
            .putString(KEY_DOCUMENT_ID, documentId)
            .putString(KEY_URL, url)
            .build()
    }
}
