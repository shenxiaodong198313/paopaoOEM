package com.paopaoime.aikeyboard.ime.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

fun ContentResolver.queryFileName(uri: Uri): String? = query(uri, null, null, null, null)?.use {
    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    it.moveToFirst()
    it.getString(index)
}

private const val PHRASE_ATTACHMENTS_DIR = "phrase_attachments"
private const val KNOWLEDGE_BASE_DIR = "knowledge_base"

fun Context.copyUriToPhraseAttachment(uri: Uri): Uri? {
    if (isPhraseAttachmentUri(uri)) return uri
    val resolver = contentResolver
    val displayName = runCatching { resolver.queryFileName(uri) }.getOrNull()
    val ext = displayName
        ?.substringAfterLast('.', "")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.replace(Regex("[^A-Za-z0-9]"), "")
        ?.take(10)
    val baseName = UUID.randomUUID().toString().replace("-", "")
    val fileName = if (!ext.isNullOrBlank()) "$baseName.$ext" else baseName
    val dir = File(filesDir, PHRASE_ATTACHMENTS_DIR).apply { mkdirs() }
    val dest = File(dir, fileName)
    try {
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null
    } catch (_: Throwable) {
        runCatching { dest.delete() }
        return null
    }
    return try {
        FileProvider.getUriForFile(this, "$packageName.cropper.fileprovider", dest)
    } catch (_: Throwable) {
        runCatching { dest.delete() }
        null
    }
}

fun Context.isPhraseAttachmentUri(uri: Uri): Boolean {
    if (uri.scheme != "content") return false
    if (uri.authority != "$packageName.cropper.fileprovider") return false
    val segments = uri.pathSegments
    return segments.size >= 3 && segments[0] == "images" && segments[1] == PHRASE_ATTACHMENTS_DIR
}

fun Context.deletePhraseAttachmentUri(uri: Uri): Boolean {
    if (!isPhraseAttachmentUri(uri)) return false
    val segments = uri.pathSegments
    val fileName = segments.lastOrNull().orEmpty()
    if (fileName.isBlank()) return false
    val dir = File(filesDir, PHRASE_ATTACHMENTS_DIR)
    return runCatching { File(dir, fileName).delete() }.getOrDefault(false)
}

fun Context.copyUriToKnowledgeBaseFile(directoryId: String, uri: Uri): String? {
    val resolver = contentResolver
    val displayName = runCatching { resolver.queryFileName(uri) }.getOrNull()?.trim().orEmpty()
    if (displayName.isBlank()) return null
    val safeName = displayName.replace(Regex("[\\\\/\\u0000]"), "_")
    val root = File(filesDir, KNOWLEDGE_BASE_DIR).apply { mkdirs() }
    val dir = File(root, directoryId).apply { mkdirs() }
    val dest = File(dir, safeName)
    if (dest.exists()) return null
    try {
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null
    } catch (_: Throwable) {
        runCatching { dest.delete() }
        return null
    }
    return safeName
}

fun Context.deleteKnowledgeBaseFile(directoryId: String, fileName: String): Boolean {
    val root = File(filesDir, KNOWLEDGE_BASE_DIR)
    val dir = File(root, directoryId)
    val safeName = fileName.replace(Regex("[\\\\/\\u0000]"), "_")
    val f = File(dir, safeName)
    if (!f.exists()) return true
    return runCatching { f.delete() }.getOrDefault(false)
}

