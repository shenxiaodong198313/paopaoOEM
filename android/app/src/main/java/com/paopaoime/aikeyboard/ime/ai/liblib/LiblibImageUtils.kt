package com.paopaoime.aikeyboard.ime.ai.liblib

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

object LiblibImageUtils {
    private const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024

    suspend fun prepareJpegForUpload(
        context: Context,
        uri: Uri,
        maxBytes: Int = MAX_UPLOAD_BYTES,
        maxDim: Int = 2048,
    ): ByteArray = withContext(Dispatchers.IO) {
        val bmp = decodeScaledBitmap(context.contentResolver, uri, maxDim)
        val dimCandidates = listOf(maxDim, 1536, 1280, 1024, 896, 768)
        var current: Bitmap = bmp
        for (dim in dimCandidates) {
            if (max(current.width, current.height) > dim) {
                val scaled = scaleDown(current, dim)
                if (scaled !== current) current.recycle()
                current = scaled
            }

            var quality = 92
            while (quality >= 40) {
                val out = ByteArrayOutputStream()
                current.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val bytes = out.toByteArray()
                if (bytes.size <= maxBytes) {
                    if (!current.isRecycled) current.recycle()
                    return@withContext bytes
                }
                quality -= 8
            }
        }

        val out = ByteArrayOutputStream()
        current.compress(Bitmap.CompressFormat.JPEG, 40, out)
        val bytes = out.toByteArray()
        if (!current.isRecycled) current.recycle()
        if (bytes.size > maxBytes) {
            throw RuntimeException("图片压缩后仍超过10MB")
        }
        bytes
    }

    private fun decodeScaledBitmap(resolver: ContentResolver, uri: Uri, maxDim: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw RuntimeException("图片解析失败")
        }
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        return bitmap ?: throw RuntimeException("图片解码失败")
    }

    private fun calculateInSampleSize(w: Int, h: Int, maxDim: Int): Int {
        var inSample = 1
        var halfW = w / 2
        var halfH = h / 2
        while (halfW / inSample >= maxDim || halfH / inSample >= maxDim) {
            inSample *= 2
        }
        return max(inSample, 1)
    }

    private fun scaleDown(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        val maxSide = max(w, h)
        if (maxSide <= maxDim) return src
        val scale = maxDim.toFloat() / maxSide.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }
}

