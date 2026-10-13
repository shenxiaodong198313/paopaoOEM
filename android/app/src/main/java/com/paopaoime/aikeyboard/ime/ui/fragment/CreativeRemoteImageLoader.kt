package com.paopaoime.aikeyboard.ime.ui.fragment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object CreativeRemoteImageLoader {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun bind(imageView: ImageView, source: String) {
        val normalized = source.trim()
        imageView.tag = normalized
        when {
            normalized.isBlank() -> imageView.setImageDrawable(null)
            normalized.startsWith("file://") -> imageView.setImageURI(Uri.parse(normalized))
            normalized.startsWith("/") -> imageView.setImageURI(Uri.fromFile(File(normalized)))
            else -> bindRemote(imageView, normalized)
        }
    }

    private fun bindRemote(imageView: ImageView, url: String) {
        val cached = cache[url]
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }
        Thread {
            val bitmap = runCatching {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull() ?: return@Thread
            cache[url] = bitmap
            imageView.post {
                if (imageView.tag == url) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }.start()
    }
}
