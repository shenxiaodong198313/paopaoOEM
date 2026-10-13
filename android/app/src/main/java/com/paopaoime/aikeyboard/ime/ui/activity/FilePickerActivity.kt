package com.paopaoime.aikeyboard.ime.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

object FilePickBridge {
    @Volatile
    var onPicked: ((Uri) -> Unit)? = null

    @Volatile
    var onDismissed: (() -> Unit)? = null
}

class FilePickerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PICKER_KIND = "favorite_picker_kind"
    }

    private var completed = false

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            completed = true
            try {
                val takeFlags =
                    (result.data?.flags ?: 0) and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Throwable) {
            }
            FilePickBridge.onPicked?.invoke(uri)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pickerKind = intent.getStringExtra(EXTRA_PICKER_KIND).orEmpty()
        val (pickerMimeType, mimeTypes) = when (pickerKind) {
            "IMAGE" -> "image/*" to arrayOf("image/*")
            "VIDEO" -> "video/*" to arrayOf("video/*")
            "FILE" -> "*/*" to arrayOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/plain"
            )
            else -> "*/*" to arrayOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "image/*",
            )
        }
        val intent = if (pickerKind == "IMAGE" || pickerKind == "VIDEO") {
            Intent(
                Intent.ACTION_PICK,
                if (pickerKind == "VIDEO") {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            ).apply {
                type = pickerMimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = pickerMimeType
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
        }
        launcher.launch(intent)
    }

    override fun onStop() {
        if (isFinishing && !completed) {
            FilePickBridge.onDismissed?.invoke()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            FilePickBridge.onPicked = null
            FilePickBridge.onDismissed = null
        }
        super.onDestroy()
    }
}

