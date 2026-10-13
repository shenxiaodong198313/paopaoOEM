package com.paopaoime.aikeyboard.midscene.agent

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MidsceneTaskLogger {
    private const val LOG_DIR = "midscene/logs"
    private const val LATEST_LOG_FILE = "latest.log"
    private const val PUBLIC_LOG_FILE = "midscene-latest.log"
    private const val PUBLIC_LOG_FILE_PREFIX = "midscene-latest"
    private val PUBLIC_LOG_RELATIVE_DIR = "${Environment.DIRECTORY_DOWNLOADS}/paopao/midscene"
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    @Synchronized
    fun startSession(
        context: Context,
        prompt: String,
        modelName: String,
        independentPlanningEnabled: Boolean,
        debugModeEnabled: Boolean
    ): String? {
        val dir = ensureLogDir(context)
        val latestFile = File(dir, LATEST_LOG_FILE)
        val header = buildString {
            appendLine("# Midscene Latest Task Log")
            appendLine("startedAt=${fileFormat.format(Date())}")
            appendLine("model=$modelName")
            appendLine("independentPlanning=$independentPlanningEnabled")
            appendLine("debugMode=$debugModeEnabled")
            appendLine("prompt=$prompt")
            appendLine()
        }
        latestFile.writeText(header)
        syncPublicLatestLog(context, latestFile)
        append(context, "日志会话已开始")
        return latestFile.absolutePath
    }

    @Synchronized
    fun append(context: Context, message: String) {
        val latestFile = File(ensureLogDir(context), LATEST_LOG_FILE)
        latestFile.appendText("[${timeFormat.format(Date())}] $message\n")
        syncPublicLatestLog(context, latestFile)
    }

    @Synchronized
    fun finishSession(context: Context, status: String) {
        append(context, "日志会话结束: $status")
    }

    fun latestLogFile(context: Context): File {
        return File(ensureLogDir(context), LATEST_LOG_FILE)
    }

    private fun ensureLogDir(context: Context): File {
        val root = context.filesDir
        return File(root, LOG_DIR).apply { mkdirs() }
    }

    private fun syncPublicLatestLog(context: Context, latestFile: File) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                syncPublicLatestLogWithMediaStore(context, latestFile)
            } else {
                syncPublicLatestLogLegacy(latestFile)
            }
        }
    }

    private fun syncPublicLatestLogLegacy(latestFile: File) {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(publicDir, "paopao/midscene").apply { mkdirs() }
        File(targetDir, PUBLIC_LOG_FILE).writeText(latestFile.readText())
    }

    private fun syncPublicLatestLogWithMediaStore(context: Context, latestFile: File) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val existingEntries = resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf("$PUBLIC_LOG_RELATIVE_DIR/"),
            null
        )?.use { cursor ->
            buildList {
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    add(
                        ContentUris.withAppendedId(collection, cursor.getLong(idIndex)) to
                            cursor.getString(nameIndex).orEmpty()
                    )
                }
            }
        }.orEmpty()
        val matchedEntries = existingEntries.filter { (_, displayName) ->
            displayName.startsWith(PUBLIC_LOG_FILE_PREFIX, ignoreCase = true)
        }
        val existingUri = matchedEntries.firstOrNull()?.first
        matchedEntries.drop(1).forEach { (uri, _) ->
            runCatching { resolver.delete(uri, null, null) }
        }
        val targetUri = existingUri ?: resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, PUBLIC_LOG_FILE)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "$PUBLIC_LOG_RELATIVE_DIR/")
            }
        )
        if (targetUri != null) {
            resolver.openOutputStream(targetUri, "wt")?.bufferedWriter()?.use { writer ->
                writer.write(latestFile.readText())
            }
        }
    }
}
