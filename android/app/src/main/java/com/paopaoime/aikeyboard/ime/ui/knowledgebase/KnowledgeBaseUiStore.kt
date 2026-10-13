package com.paopaoime.aikeyboard.ime.ui.knowledgebase

import com.paopaoime.aikeyboard.ime.database.DataBaseKT
import com.paopaoime.aikeyboard.ime.database.entry.KbDirectory
import com.paopaoime.aikeyboard.ime.database.entry.KbDocument
import com.paopaoime.aikeyboard.ime.application.Launcher
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDirectoryUi
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDocumentUi
import com.paopaoime.aikeyboard.ime.utils.copyUriToKnowledgeBaseFile
import com.paopaoime.aikeyboard.ime.utils.deleteKnowledgeBaseFile
import com.paopaoime.aikeyboard.ime.utils.queryFileName
import android.net.Uri
import android.content.Context
import com.paopaoime.aikeyboard.ime.R
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.BackoffPolicy
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.parse.KbPdfParseWorker
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.parse.KbOoxmlParseWorker
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.parse.KbVectorizeWorker
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.parse.KbWebParseWorker
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.UUID

object KnowledgeBaseUiStore {
    private var kbManager: com.paopaoime.aikeyboard.ime.knowledge.KnowledgeBaseManager? = null
    private val transientDocuments = linkedMapOf<String, KbDocumentUi>()
    private val transientDocumentPaths = linkedMapOf<String, String>()

    fun getManager(context: android.content.Context): com.paopaoime.aikeyboard.ime.knowledge.KnowledgeBaseManager {
        if (kbManager == null) {
            kbManager = com.paopaoime.aikeyboard.ime.knowledge.KnowledgeBaseManager(context.applicationContext)
        }
        return kbManager!!
    }

    private const val DEFAULT_DIR_ID = "personal_default"
    private const val TRANSIENT_DIR_ID = "__favorite_preview__"
    private const val DEFAULT_VECTOR_MAX_RETRIES = 3
    private const val DEFAULT_VECTOR_TIMEOUT_MILLIS = 5 * 60 * 1000L

    private fun kbRootDir(): File = File(Launcher.instance.context.filesDir, "knowledge_base")

    private fun ensureDirectoryOnDisk(directoryId: String) {
        val root = kbRootDir().apply { mkdirs() }
        File(root, directoryId).mkdirs()
    }

    private fun ensureDefaultDirectory() {
        val dao = DataBaseKT.instance.kbDirectoryDao()
        if (dao.getById(DEFAULT_DIR_ID) != null) return
        val now = System.currentTimeMillis()
        ensureDirectoryOnDisk(DEFAULT_DIR_ID)
        dao.insert(
            KbDirectory(
                id = DEFAULT_DIR_ID,
                name = "个人知识库",
                createdAtMillis = now,
                updatedAtMillis = now,
                deletable = 0,
            )
        )
    }

    fun getDirectories(): List<KbDirectoryUi> {
        ensureDefaultDirectory()
        val dao = DataBaseKT.instance.kbDirectoryDao()
        val docDao = DataBaseKT.instance.kbDocumentDao()
        
        return dao.getAll().map {
            val size = docDao.sumVectorSizeByDirectory(it.id) ?: 0L
            KbDirectoryUi(
                id = it.id,
                name = it.name,
                createdAtMillis = it.createdAtMillis,
                updatedAtMillis = it.updatedAtMillis,
                deletable = it.deletable != 0,
                vectorStoreSize = size
            )
        }
    }

    fun getDefaultDirectoryId(): String {
        ensureDefaultDirectory()
        return DEFAULT_DIR_ID
    }

    fun createDirectory(name: String): KbDirectoryUi {
        ensureDefaultDirectory()
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        ensureDirectoryOnDisk(id)
        DataBaseKT.instance.kbDirectoryDao().insert(
            KbDirectory(
                id = id,
                name = name,
                createdAtMillis = now,
                updatedAtMillis = now,
                deletable = 1,
            )
        )
        return KbDirectoryUi(
            id = id,
            name = name,
            createdAtMillis = now,
            updatedAtMillis = now,
            deletable = true,
            vectorStoreSize = 0L
        )
    }

    fun getDirectory(directoryId: String): KbDirectoryUi? {
        ensureDefaultDirectory()
        val dir = DataBaseKT.instance.kbDirectoryDao().getById(directoryId) ?: return null
        val size = DataBaseKT.instance.kbDocumentDao().sumVectorSizeByDirectory(directoryId) ?: 0L
        return KbDirectoryUi(
            id = dir.id,
            name = dir.name,
            createdAtMillis = dir.createdAtMillis,
            updatedAtMillis = dir.updatedAtMillis,
            deletable = dir.deletable != 0,
            vectorStoreSize = size
        )
    }

    fun listDocuments(directoryId: String): List<KbDocumentUi> =
        DataBaseKT.instance.kbDocumentDao().listByDirectory(directoryId).map { doc ->
            val vectorState = normalizeVectorState(doc)
            KbDocumentUi(
                id = doc.id,
                directoryId = doc.directoryId,
                fileName = doc.fileName,
                mimeType = doc.mimeType,
                updatedAtMillis = vectorState.updatedAtMillis,
                parseStatus = doc.parseStatus,
                parseProgress = doc.parseProgress,
                parsedTextPath = doc.parsedTextPath,
                parseError = doc.parseError,
                vectorStatus = vectorState.vectorStatus,
                vectorError = vectorState.vectorError,
                vectorUpdatedAtMillis = vectorState.vectorUpdatedAtMillis,
            )
        }

    fun addDocumentFromUri(directoryId: String, uri: Uri): KbDocumentUi {
        ensureDefaultDirectory()
        ensureDirectoryOnDisk(directoryId)
        val context = Launcher.instance.context

        val rawName = context.contentResolver.queryFileName(uri)?.trim().orEmpty()
        if (rawName.isBlank()) throw IllegalStateException(context.getString(R.string.kb_error_filename))
        val fileName = rawName.replace(Regex("[\\\\/\\u0000]"), "_")

        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (!isSupportedKnowledgeBaseMimeOrName(mime, fileName)) {
            throw IllegalStateException(context.getString(R.string.kb_error_unsupported_type))
        }

        val dest = File(File(context.filesDir, "knowledge_base"), "$directoryId/$fileName")
        if (dest.exists()) throw IllegalStateException(context.getString(R.string.kb_error_duplicate, fileName))

        val copiedName = context.copyUriToKnowledgeBaseFile(directoryId, uri)
            ?: throw IllegalStateException(context.getString(R.string.kb_error_save))

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val normalizedMime = normalizePreferredMimeType(mime, copiedName)
        val parseStatus = if (isPdf(normalizedMime, copiedName) || isOoxml(normalizedMime, copiedName)) 1 else 0

        DataBaseKT.instance.kbDocumentDao().insert(
            KbDocument(
                id = id,
                directoryId = directoryId,
                fileName = copiedName,
                mimeType = normalizedMime,
                createdAtMillis = now,
                updatedAtMillis = now,
                parseStatus = parseStatus,
            )
        )
        DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)

        val ui = KbDocumentUi(
            id = id,
            directoryId = directoryId,
            fileName = copiedName,
            mimeType = normalizedMime,
            updatedAtMillis = now,
            parseStatus = parseStatus,
            parseProgress = 0,
            parsedTextPath = null,
            parseError = null,
            vectorStatus = 0,
        )
        if (parseStatus == 1) {
            when {
                isPdf(normalizedMime, copiedName) -> enqueuePdfParse(id)
                isOoxml(normalizedMime, copiedName) -> enqueueOoxmlParse(id)
            }
        }
        return ui
    }

    fun addWebDocumentFromUrl(url: String, title: String? = null, directoryId: String = DEFAULT_DIR_ID): KbDocumentUi {
        ensureDefaultDirectory()
        ensureDirectoryOnDisk(directoryId)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val fileName = buildWebFileName(title, url)
        val mimeType = "text/html"

        DataBaseKT.instance.kbDocumentDao().insert(
            KbDocument(
                id = id,
                directoryId = directoryId,
                fileName = fileName,
                mimeType = mimeType,
                createdAtMillis = now,
                updatedAtMillis = now,
                parseStatus = 1,
            )
        )
        DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(directoryId, now)

        enqueueWebParse(id, url)

        return KbDocumentUi(
            id = id,
            directoryId = directoryId,
            fileName = fileName,
            mimeType = mimeType,
            updatedAtMillis = now,
            parseStatus = 1,
            parseProgress = 0,
            parsedTextPath = null,
            parseError = null,
            vectorStatus = 0,
        )
    }

    fun deleteDocument(documentId: String): Boolean {
        ensureDefaultDirectory()
        val doc = DataBaseKT.instance.kbDocumentDao().getById(documentId) ?: return false
        val context = Launcher.instance.context
        WorkManager.getInstance(context).cancelUniqueWork(pdfParseWorkName(documentId))
        WorkManager.getInstance(context).cancelUniqueWork(ooxmlParseWorkName(documentId))
        WorkManager.getInstance(context).cancelUniqueWork(vectorizeWorkName(documentId))
        doc.parsedTextPath?.let { runCatching { File(it).delete() } }
        val ok = context.deleteKnowledgeBaseFile(doc.directoryId, doc.fileName)
        if (!ok) return false
        getManager(context).removeDocumentVectors(documentId)
        DataBaseKT.instance.kbDocumentDao().deleteById(documentId)
        val now = System.currentTimeMillis()
        DataBaseKT.instance.kbDirectoryDao().updateUpdatedAt(doc.directoryId, now)
        return true
    }

    fun getDocument(documentId: String): KbDocumentUi? {
        transientDocuments[documentId]?.let { return it }
        ensureDefaultDirectory()
        val doc = DataBaseKT.instance.kbDocumentDao().getById(documentId) ?: return null
        val vectorState = normalizeVectorState(doc)
        return KbDocumentUi(
            id = doc.id,
            directoryId = doc.directoryId,
            fileName = doc.fileName,
            mimeType = doc.mimeType,
            updatedAtMillis = vectorState.updatedAtMillis,
            parseStatus = doc.parseStatus,
            parseProgress = doc.parseProgress,
            parsedTextPath = doc.parsedTextPath,
            parseError = doc.parseError,
            vectorStatus = vectorState.vectorStatus,
            vectorError = vectorState.vectorError,
            vectorUpdatedAtMillis = vectorState.vectorUpdatedAtMillis,
        )
    }

    fun registerTransientDocument(file: File, displayName: String, mimeType: String): String {
        val normalizedName = displayName.ifBlank { file.name }
        val normalizedMime = normalizePreferredMimeType(mimeType, normalizedName)
        val id = "favorite_preview_${UUID.randomUUID()}"
        val now = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        transientDocuments[id] = KbDocumentUi(
            id = id,
            directoryId = TRANSIENT_DIR_ID,
            fileName = normalizedName,
            mimeType = normalizedMime,
            updatedAtMillis = now,
            parseStatus = 0,
            parseProgress = 0,
            parsedTextPath = null,
            parseError = null,
            vectorStatus = 0,
        )
        transientDocumentPaths[id] = file.absolutePath
        return id
    }

    fun resolveTransientDocumentFile(documentId: String): File? {
        val path = transientDocumentPaths[documentId] ?: return null
        return File(path)
    }

    fun clearTransientDocument(documentId: String) {
        transientDocuments.remove(documentId)
        transientDocumentPaths.remove(documentId)
    }

    private fun pdfParseWorkName(documentId: String): String = "kb_pdf_parse_$documentId"
    private fun ooxmlParseWorkName(documentId: String): String = "kb_ooxml_parse_$documentId"
    private fun webParseWorkName(documentId: String): String = "kb_web_parse_$documentId"
    private fun vectorizeWorkName(documentId: String): String = "kb_vectorize_$documentId"

    fun enqueuePdfParse(documentId: String) {
        val context = Launcher.instance.context
        val parseRequest = OneTimeWorkRequestBuilder<KbPdfParseWorker>()
            .setInputData(KbPdfParseWorker.inputData(documentId))
            .build()
        val vectorizeRequest = buildVectorizeRequest(documentId, DEFAULT_VECTOR_MAX_RETRIES, DEFAULT_VECTOR_TIMEOUT_MILLIS)
            
        WorkManager.getInstance(context)
            .beginUniqueWork(pdfParseWorkName(documentId), ExistingWorkPolicy.KEEP, parseRequest)
            .then(vectorizeRequest)
            .enqueue()
    }

    fun enqueueOoxmlParse(documentId: String) {
        val context = Launcher.instance.context
        val parseRequest = OneTimeWorkRequestBuilder<KbOoxmlParseWorker>()
            .setInputData(KbOoxmlParseWorker.inputData(documentId))
            .build()
        val vectorizeRequest = buildVectorizeRequest(documentId, DEFAULT_VECTOR_MAX_RETRIES, DEFAULT_VECTOR_TIMEOUT_MILLIS)

        WorkManager.getInstance(context)
            .beginUniqueWork(ooxmlParseWorkName(documentId), ExistingWorkPolicy.KEEP, parseRequest)
            .then(vectorizeRequest)
            .enqueue()
    }

    fun enqueueWebParse(documentId: String, url: String) {
        val context = Launcher.instance.context
        val parseRequest = OneTimeWorkRequestBuilder<KbWebParseWorker>()
            .setInputData(KbWebParseWorker.inputData(documentId, url))
            .build()
        val vectorizeRequest = buildVectorizeRequest(documentId, DEFAULT_VECTOR_MAX_RETRIES, DEFAULT_VECTOR_TIMEOUT_MILLIS)

        WorkManager.getInstance(context)
            .beginUniqueWork(webParseWorkName(documentId), ExistingWorkPolicy.KEEP, parseRequest)
            .then(vectorizeRequest)
            .enqueue()
    }

    fun enqueueVectorize(
        documentId: String,
        maxRetries: Int = DEFAULT_VECTOR_MAX_RETRIES,
        timeoutMillis: Long = DEFAULT_VECTOR_TIMEOUT_MILLIS
    ) {
        val context = Launcher.instance.context
        val now = System.currentTimeMillis()
        DataBaseKT.instance.kbDocumentDao()
            .updateVectorState(documentId, KbDocument.VECTOR_STATUS_RUNNING, 0L, null, now)
        val request = buildVectorizeRequest(documentId, maxRetries, timeoutMillis)
        WorkManager.getInstance(context)
            .enqueueUniqueWork(vectorizeWorkName(documentId), ExistingWorkPolicy.REPLACE, request)
    }

    fun getStats(): KnowledgeBaseStats {
        ensureDefaultDirectory()
        val directories = DataBaseKT.instance.kbDirectoryDao().getAll()
        val documents = DataBaseKT.instance.kbDocumentDao().getAll()
        val vectorSizeTotal = documents.sumOf { it.vectorSize }
        val mimeTypeCounts = documents.groupingBy {
            normalizeDocumentType(it.mimeType, it.fileName)
        }.eachCount()
        val parseStatusCounts = documents.groupingBy { it.parseStatus }.eachCount()
        val vectorStatusCounts = documents.groupingBy { it.vectorStatus }.eachCount()
        return KnowledgeBaseStats(
            directoryCount = directories.size,
            documentCount = documents.size,
            vectorSizeTotal = vectorSizeTotal,
            mimeTypeCounts = mimeTypeCounts,
            parseStatusCounts = parseStatusCounts,
            vectorStatusCounts = vectorStatusCounts
        )
    }

    private fun buildVectorizeRequest(
        documentId: String,
        maxRetries: Int,
        timeoutMillis: Long
    ) = OneTimeWorkRequestBuilder<KbVectorizeWorker>()
        .setInputData(KbVectorizeWorker.inputData(documentId, maxRetries, timeoutMillis))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .build()

    private fun isPdf(mimeType: String, fileName: String): Boolean {
        val mime = mimeType.lowercase()
        if (mime == "application/pdf" || mime.contains("pdf")) return true
        return fileName.lowercase().endsWith(".pdf")
    }

    private fun isOoxml(mimeType: String, fileName: String): Boolean {
        val mime = mimeType.lowercase()
        if (mime.contains("wordprocessingml") || mime.contains("spreadsheetml") || mime.contains("presentationml")) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "docx" || ext == "xlsx" || ext == "pptx"
    }

    private fun isSupportedKnowledgeBaseMimeOrName(mimeType: String, fileName: String): Boolean {
        val mime = mimeType.lowercase()
        if (mime.startsWith("image/")) return true
        if (mime == "application/pdf" || mime.contains("pdf")) return true
        if (mime.contains("wordprocessingml") || mime.contains("msword")) return true
        if (mime.contains("spreadsheetml") || mime.contains("ms-excel")) return true
        if (mime.contains("presentationml") || mime.contains("ms-powerpoint")) return true

        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")
    }

    private fun inferMimeTypeByFileName(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "rtf" -> "application/rtf"
            "html", "htm" -> "text/html"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            else -> "application/octet-stream"
        }
    }

    private fun normalizePreferredMimeType(mimeType: String, fileName: String): String {
        val normalized = mimeType.trim().lowercase()
        if (normalized.isBlank() ||
            normalized == "application/*" ||
            normalized == "application/octet-stream"
        ) {
            return inferMimeTypeByFileName(fileName)
        }
        return normalized
    }

    private fun buildWebFileName(title: String?, url: String): String {
        val base = title?.trim()?.takeIf { it.isNotBlank() }
            ?: runCatching { Uri.parse(url).host }.getOrNull()?.trim().orEmpty().ifBlank { url }
        val cleaned = base
            .replace(Regex("[\\\\/\\u0000]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
        val shortName = if (cleaned.length > 60) cleaned.take(60) else cleaned
        val ext = shortName.substringAfterLast('.', "")
        val withExt = if (ext.lowercase() in setOf("html", "htm", "txt")) shortName else "$shortName.html"
        return if (withExt.isNotBlank()) withExt else "网页_${System.currentTimeMillis()}.html"
    }

    private fun normalizeDocumentType(mimeType: String, fileName: String): String {
        val mime = mimeType.lowercase()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> "图片"
            mime.contains("pdf") || ext == "pdf" -> "PDF"
            mime.contains("wordprocessingml") || mime.contains("msword") || ext in setOf("doc", "docx") -> "Word"
            mime.contains("spreadsheetml") || mime.contains("ms-excel") || ext in setOf("xls", "xlsx") -> "Excel"
            mime.contains("presentationml") || mime.contains("ms-powerpoint") || ext in setOf("ppt", "pptx") -> "PPT"
            mime.contains("html") || ext in setOf("html", "htm") -> "Web"
            ext == "txt" || mime == "text/plain" -> "TXT"
            else -> "其他"
        }
    }

    private data class VectorState(
        val vectorStatus: Int,
        val vectorError: String?,
        val vectorUpdatedAtMillis: Long,
        val updatedAtMillis: Long
    )

    private fun normalizeVectorState(doc: KbDocument): VectorState {
        val status = doc.vectorStatus
        if (status != KbDocument.VECTOR_STATUS_RUNNING) {
            return VectorState(status, doc.vectorError, doc.vectorUpdatedAtMillis, doc.updatedAtMillis)
        }
        val now = System.currentTimeMillis()
        val updatedAt = doc.vectorUpdatedAtMillis
        if (updatedAt <= 0L || now - updatedAt > DEFAULT_VECTOR_TIMEOUT_MILLIS) {
            val error = "向量化超时"
            DataBaseKT.instance.kbDocumentDao()
                .updateVectorState(doc.id, KbDocument.VECTOR_STATUS_FAILED, 0L, error, now)
            return VectorState(KbDocument.VECTOR_STATUS_FAILED, error, now, now)
        }
        return VectorState(status, doc.vectorError, updatedAt, doc.updatedAtMillis)
    }
}

data class KnowledgeBaseStats(
    val directoryCount: Int,
    val documentCount: Int,
    val vectorSizeTotal: Long,
    val mimeTypeCounts: Map<String, Int>,
    val parseStatusCounts: Map<Int, Int>,
    val vectorStatusCounts: Map<Int, Int>
)

data class KnowledgeBaseHitStats(
    val chatTriggerCount: Long,
    val chatReadyCount: Long,
    val chatLowCount: Long,
    val chatEmptyCount: Long,
    val skillTriggerCount: Long,
    val skillReadyCount: Long,
    val skillLowCount: Long,
    val skillEmptyCount: Long,
    val skillModuleStats: Map<String, KnowledgeBaseHitModuleStats>
)

data class KnowledgeBaseHitModuleStats(
    val triggerCount: Long,
    val readyCount: Long,
    val lowCount: Long,
    val emptyCount: Long
)

object KnowledgeBaseStatsStore {
    private const val PREFS_NAME = "kb_stats"
    private const val KEY_CHAT_TRIGGER = "chat_trigger"
    private const val KEY_CHAT_READY = "chat_ready"
    private const val KEY_CHAT_LOW = "chat_low"
    private const val KEY_CHAT_EMPTY = "chat_empty"
    private const val KEY_SKILL_TRIGGER = "skill_trigger"
    private const val KEY_SKILL_READY = "skill_ready"
    private const val KEY_SKILL_LOW = "skill_low"
    private const val KEY_SKILL_EMPTY = "skill_empty"

    fun recordChat(status: String) {
        val prefs = prefs()
        increment(prefs, KEY_CHAT_TRIGGER)
        when (status) {
            "ready" -> increment(prefs, KEY_CHAT_READY)
            "low" -> increment(prefs, KEY_CHAT_LOW)
            "empty" -> increment(prefs, KEY_CHAT_EMPTY)
        }
    }

    fun recordSkill(module: String, status: String) {
        val prefs = prefs()
        increment(prefs, KEY_SKILL_TRIGGER)
        when (status) {
            "ready" -> increment(prefs, KEY_SKILL_READY)
            "low" -> increment(prefs, KEY_SKILL_LOW)
            "empty" -> increment(prefs, KEY_SKILL_EMPTY)
        }
        increment(prefs, moduleKey(module, "trigger"))
        when (status) {
            "ready" -> increment(prefs, moduleKey(module, "ready"))
            "low" -> increment(prefs, moduleKey(module, "low"))
            "empty" -> increment(prefs, moduleKey(module, "empty"))
        }
    }

    fun getHitStats(modules: List<String>): KnowledgeBaseHitStats {
        val prefs = prefs()
        val moduleStats = modules.associateWith { module ->
            KnowledgeBaseHitModuleStats(
                triggerCount = prefs.getLong(moduleKey(module, "trigger"), 0L),
                readyCount = prefs.getLong(moduleKey(module, "ready"), 0L),
                lowCount = prefs.getLong(moduleKey(module, "low"), 0L),
                emptyCount = prefs.getLong(moduleKey(module, "empty"), 0L)
            )
        }
        return KnowledgeBaseHitStats(
            chatTriggerCount = prefs.getLong(KEY_CHAT_TRIGGER, 0L),
            chatReadyCount = prefs.getLong(KEY_CHAT_READY, 0L),
            chatLowCount = prefs.getLong(KEY_CHAT_LOW, 0L),
            chatEmptyCount = prefs.getLong(KEY_CHAT_EMPTY, 0L),
            skillTriggerCount = prefs.getLong(KEY_SKILL_TRIGGER, 0L),
            skillReadyCount = prefs.getLong(KEY_SKILL_READY, 0L),
            skillLowCount = prefs.getLong(KEY_SKILL_LOW, 0L),
            skillEmptyCount = prefs.getLong(KEY_SKILL_EMPTY, 0L),
            skillModuleStats = moduleStats
        )
    }

    private fun prefs() = Launcher.instance.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun increment(prefs: android.content.SharedPreferences, key: String) {
        val next = prefs.getLong(key, 0L) + 1
        prefs.edit().putLong(key, next).apply()
    }

    private fun moduleKey(module: String, key: String) = "skill_${module}_$key"
}



