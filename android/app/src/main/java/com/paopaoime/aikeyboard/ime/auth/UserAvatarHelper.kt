package com.paopaoime.aikeyboard.ime.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.paopaoime.aikeyboard.ime.R
import java.io.File
import java.net.URL

object UserAvatarHelper {
    fun saveCustomAvatar(context: Context, userId: String, uri: Uri): String? {
        val safeUserId = userId.ifBlank { "guest" }
        val targetDir = File(context.filesDir, "avatar_custom")
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, "avatar_$safeUserId.img")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            targetFile.absolutePath
        }.getOrNull()
    }

    fun loadAvatarBitmap(context: Context, avatarUrl: String, localPath: String): Bitmap? {
        val localBitmap = loadBitmapFromPath(localPath)
        if (localBitmap != null) return localBitmap.takeUnless { isDefaultAvatarBitmap(context, it) }
        if (avatarUrl.isBlank()) return null
        val cacheDir = File(context.filesDir, "avatar_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "wechat_${avatarUrl.hashCode().toUInt().toString(16)}.img")
        loadBitmapFromPath(cacheFile.absolutePath)?.let {
            return it.takeUnless { bitmap -> isDefaultAvatarBitmap(context, bitmap) }
        }
        return runCatching {
            URL(avatarUrl).openStream().use { input ->
                val bytes = input.readBytes()
                cacheFile.writeBytes(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()?.takeUnless { bitmap -> isDefaultAvatarBitmap(context, bitmap) }
    }

    private fun loadBitmapFromPath(path: String): Bitmap? {
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun isDefaultAvatarBitmap(context: Context, bitmap: Bitmap): Boolean {
        val defaultBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.img_edit_profile_avatar_default_21)
            ?: return false
        return bitmap.sameAs(defaultBitmap)
    }
}
