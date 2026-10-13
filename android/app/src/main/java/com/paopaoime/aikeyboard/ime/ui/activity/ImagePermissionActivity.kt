package com.paopaoime.aikeyboard.ime.ui.activity

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object ImagePermissionBridge {
    @Volatile
    var onResult: ((Boolean) -> Unit)? = null
}

class ImagePermissionActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PERMISSION_KIND = "permission_kind"
        const val PERMISSION_KIND_IMAGE = "image"
        const val PERMISSION_KIND_VIDEO = "video"
        const val PERMISSION_KIND_FILE = "file"
    }

    private val manageAllFilesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            deliverFilePermissionResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra(EXTRA_PERMISSION_KIND).orEmpty()
        if (kind == PERMISSION_KIND_FILE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                deliverResult(true)
            } else {
                manageAllFilesLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
            return
        }
        val permission = when {
            Build.VERSION.SDK_INT < 33 -> Manifest.permission.READ_EXTERNAL_STORAGE
            kind == PERMISSION_KIND_VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
            else -> Manifest.permission.READ_MEDIA_IMAGES
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            deliverResult(true)
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(permission), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        deliverResult(granted)
    }

    private fun deliverFilePermissionResult() {
        deliverResult(Environment.isExternalStorageManager())
    }

    private fun deliverResult(granted: Boolean) {
        ImagePermissionBridge.onResult?.invoke(granted)
        ImagePermissionBridge.onResult = null
        finish()
    }
}

