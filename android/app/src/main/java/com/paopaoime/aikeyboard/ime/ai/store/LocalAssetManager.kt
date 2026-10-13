package com.paopaoime.aikeyboard.ime.ai.store

import android.content.Context
import com.paopaoime.aikeyboard.ime.ai.liblib.LiblibClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream

@Serializable
data class LocalAsset(
    val id: String,
    val type: Int, // 0: Image, 1: Video
    val localPath: String, // Absolute path
    val prompt: String,
    val createTime: Long,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Int = 0,
    val model: String = "",
    val coverPath: String = ""
)

class LocalAssetManager(private val context: Context) {
    private val assetsRootDir = File(context.getExternalFilesDir(null), "generated_assets")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        if (!assetsRootDir.exists()) assetsRootDir.mkdirs()
    }

    private fun currentUserScope(): String {
        val uid = AuthSessionStore.userId(context).trim()
        if (uid.isBlank()) return "guest"
        val normalized = uid.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return normalized.ifBlank { "guest" }
    }

    private fun currentAssetsDir(): File {
        val dir = File(assetsRootDir, currentUserScope())
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun currentIndexFile(): File = File(currentAssetsDir(), "index.json")

    private fun allUserDirs(): List<File> {
        if (!assetsRootDir.exists()) return emptyList()
        return assetsRootDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
    }

    suspend fun ensureImageThumbnails(maxBytes: Int = 50 * 1024) = withContext(Dispatchers.IO) {
        val assets = getAssets()
        if (assets.isEmpty()) return@withContext

        var changed = false
        val updated = assets.map { asset ->
            if (asset.type != 0) return@map asset

            val coverPath = asset.coverPath
            val coverOk = coverPath.isNotBlank() && File(coverPath).exists()
            if (coverOk) return@map asset

            val thumbFile = tryCreateImageThumbnail(
                imageFile = File(asset.localPath),
                targetDir = currentAssetsDir(),
                maxBytes = maxBytes
            )
            if (thumbFile != null) {
                changed = true
                asset.copy(coverPath = thumbFile.absolutePath)
            } else {
                asset
            }
        }

        if (changed) saveIndex(updated)
    }

    suspend fun ensureVideoCovers(maxBytes: Int = 80 * 1024) = withContext(Dispatchers.IO) {
        val assets = getAssets()
        if (assets.isEmpty()) return@withContext

        var changed = false
        val updated = assets.map { asset ->
            if (asset.type != 1) return@map asset

            val coverPath = asset.coverPath
            val coverOk = coverPath.isNotBlank() && File(coverPath).exists()
            if (coverOk) return@map asset

            val coverFile = tryCreateVideoCoverThumbnail(
                videoFile = File(asset.localPath),
                maxBytes = maxBytes
            )
            if (coverFile != null) {
                changed = true
                asset.copy(coverPath = coverFile.absolutePath)
            } else {
                asset
            }
        }

        if (changed) saveIndex(updated)
    }

    suspend fun pruneCache(
        maxAssets: Int = 180,
        maxAgeDays: Int = 30,
        maxTotalBytes: Long = 120L * 1024L * 1024L
    ) = withContext(Dispatchers.IO) {
        val assets = getAssets().sortedByDescending { it.createTime }
        if (assets.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val maxAgeMs = maxAgeDays.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        val kept = ArrayList<LocalAsset>(assets.size)
        assets.forEachIndexed { index, asset ->
            val ageMs = (now - asset.createTime).coerceAtLeast(0L)
            val withinAge = ageMs <= maxAgeMs
            val withinCount = index < maxAssets.coerceAtLeast(20)
            if (withinAge || withinCount) {
                kept.add(asset)
            }
        }
        var totalBytes = 0L
        val finalKept = ArrayList<LocalAsset>(kept.size)
        kept.forEach { asset ->
            val localSize = File(asset.localPath).takeIf { it.exists() }?.length() ?: 0L
            val coverSize = if (asset.coverPath.isBlank()) 0L else File(asset.coverPath).takeIf { it.exists() }?.length() ?: 0L
            val add = localSize + coverSize
            if (totalBytes + add <= maxTotalBytes.coerceAtLeast(30L * 1024L * 1024L)) {
                totalBytes += add
                finalKept.add(asset)
            }
        }
        val keepSet = finalKept.map { it.id }.toHashSet()
        val removed = assets.filterNot { keepSet.contains(it.id) }
        removed.forEach { asset ->
            safeDeleteFile(asset.localPath)
            safeDeleteFile(asset.coverPath)
        }
        val referenced = HashSet<String>()
        finalKept.forEach { asset ->
            referenced.add(File(asset.localPath).absolutePath)
            if (asset.coverPath.isNotBlank()) referenced.add(File(asset.coverPath).absolutePath)
        }
        val assetsDir = currentAssetsDir()
        val indexFile = currentIndexFile()
        assetsDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            if (file.absolutePath == indexFile.absolutePath) return@forEach
            if (!referenced.contains(file.absolutePath)) {
                runCatching { file.delete() }
            }
        }
        if (finalKept.size != assets.size) {
            saveIndex(finalKept.sortedByDescending { it.createTime })
        }
    }

    suspend fun saveAsset(
        client: LiblibClient,
        url: String,
        type: Int,
        prompt: String,
        model: String,
        width: Int = 0,
        height: Int = 0,
        duration: Int = 0,
        coverUrl: String = ""
    ): LocalAsset = withContext(Dispatchers.IO) {
        val assetsDir = currentAssetsDir()
        val fileName = "ASSET_${System.currentTimeMillis()}_${url.hashCode().takeIf { it != 0 } ?: "0"}.${if (type == 0) "png" else "mp4"}"
        val file = File(assetsDir, fileName)
        
        // Download main file if not exists
        if (!file.exists()) {
            try {
                val bytes = client.downloadBytes(url)
                file.writeBytes(bytes)
            } catch (e: Exception) {
                e.printStackTrace()
                // Don't throw, just log? No, we need the file.
                throw e
            }
        }

        // Download cover if exists
        var savedCoverPath = ""
        if (coverUrl.isNotEmpty()) {
            val coverName = "COVER_${System.currentTimeMillis()}_${coverUrl.hashCode()}.png"
            val coverFile = File(assetsDir, coverName)
            if (!coverFile.exists()) {
                 try {
                    val bytes = client.downloadBytes(coverUrl)
                    coverFile.writeBytes(bytes)
                    savedCoverPath = coverFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                savedCoverPath = coverFile.absolutePath
            }
        }

        if (type == 0) {
            val thumbFile = tryCreateImageThumbnail(
                imageFile = file,
                targetDir = assetsDir,
                maxBytes = 50 * 1024
            )
            if (thumbFile != null) {
                savedCoverPath = thumbFile.absolutePath
            }
        }

        // Determine dimensions if not provided
        var finalWidth = width
        var finalHeight = height
        var finalDuration = duration

        if (finalWidth == 0 || finalHeight == 0) {
            try {
                if (type == 0) { // Image
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    finalWidth = options.outWidth
                    finalHeight = options.outHeight
                } else if (type == 1) { // Video
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
                    finalWidth = w
                    finalHeight = h
                    finalDuration = d
                    retriever.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val asset = LocalAsset(
            id = System.currentTimeMillis().toString(),
            type = type,
            localPath = file.absolutePath,
            prompt = prompt,
            createTime = System.currentTimeMillis(),
            width = finalWidth,
            height = finalHeight,
            duration = finalDuration,
            model = model,
            coverPath = savedCoverPath
        )

        val assets = getAssets().toMutableList()
        val newKey = buildDeduplicateKey(asset)
        val next = assets.filterNot { buildDeduplicateKey(it) == newKey }.toMutableList()
        next.add(0, asset)
        saveIndex(next)
        
        return@withContext asset
    }

    fun getAssets(): List<LocalAsset> {
        val indexFile = currentIndexFile()
        if (!indexFile.exists()) return emptyList()
        return try {
            val decoded: List<LocalAsset> = json.decodeFromString(indexFile.readText())
            val unique = LinkedHashMap<String, LocalAsset>()
            decoded.sortedByDescending { it.createTime }.forEach { item ->
                val key = buildDeduplicateKey(item)
                if (!unique.containsKey(key)) unique[key] = item
            }
            val result = unique.values.toList()
            if (result.size != decoded.size) {
                saveIndex(result)
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun buildDeduplicateKey(asset: LocalAsset): String {
        val hash = extractAssetSourceHash(asset.localPath)
        return if (hash.isNotBlank()) {
            "${asset.type}|hash:$hash"
        } else {
            "${asset.type}|path:${asset.localPath}"
        }
    }

    private fun safeDeleteFile(path: String) {
        if (path.isBlank()) return
        val file = File(path)
        if (!file.exists() || !file.isFile) return
        if (!file.absolutePath.startsWith(assetsRootDir.absolutePath)) return
        runCatching { file.delete() }
    }

    private fun extractAssetSourceHash(localPath: String): String {
        val name = File(localPath).name
        val match = assetNameHashRegex.find(name) ?: return ""
        return match.groupValues.getOrNull(1)?.trim().orEmpty()
    }

    private fun saveIndex(assets: List<LocalAsset>) {
        val indexFile = currentIndexFile()
        try {
            indexFile.writeText(json.encodeToString(assets))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun tryCreateImageThumbnail(imageFile: File, targetDir: File, maxBytes: Int): File? {
        if (!imageFile.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        val targetMaxDim = 720
        val sampleSize = calculateInSampleSize(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            reqMaxDim = targetMaxDim
        )

        val bitmap = BitmapFactory.decodeFile(
            imageFile.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null

        var currentBitmap = bitmap
        try {
            val thumbName = "THUMB_${System.currentTimeMillis()}_${imageFile.absolutePath.hashCode()}.jpg"
            val thumbFile = File(targetDir, thumbName)

            var quality = 85
            var loop = 0
            while (loop < 30) {
                val baos = ByteArrayOutputStream()
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                val bytes = baos.toByteArray()

                if (bytes.size <= maxBytes) {
                    thumbFile.writeBytes(bytes)
                    return thumbFile
                }

                if (quality > 35) {
                    quality = (quality - 10).coerceAtLeast(35)
                    loop++
                    continue
                }

                val nextWidth = (currentBitmap.width * 0.85f).toInt().coerceAtLeast(120)
                val nextHeight = (currentBitmap.height * 0.85f).toInt().coerceAtLeast(120)
                if (nextWidth == currentBitmap.width || nextHeight == currentBitmap.height) {
                    quality = 25
                    loop++
                    continue
                }
                val scaled = Bitmap.createScaledBitmap(currentBitmap, nextWidth, nextHeight, true)
                if (scaled != currentBitmap) {
                    if (currentBitmap != bitmap) currentBitmap.recycle()
                    currentBitmap = scaled
                }
                quality = 85
                loop++
            }

            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            if (currentBitmap != bitmap) currentBitmap.recycle()
            bitmap.recycle()
        }
    }

    private fun tryCreateVideoCoverThumbnail(videoFile: File, maxBytes: Int): File? {
        if (!videoFile.exists()) return null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null

            var currentBitmap = frame
            val assetsDir = currentAssetsDir()
            val coverName = "VCOVER_${System.currentTimeMillis()}_${videoFile.absolutePath.hashCode()}.jpg"
            val coverFile = File(assetsDir, coverName)

            var quality = 85
            var loop = 0
            while (loop < 30) {
                val baos = ByteArrayOutputStream()
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                val bytes = baos.toByteArray()
                if (bytes.size <= maxBytes) {
                    coverFile.writeBytes(bytes)
                    return coverFile
                }

                if (quality > 35) {
                    quality = (quality - 10).coerceAtLeast(35)
                    loop++
                    continue
                }

                val nextWidth = (currentBitmap.width * 0.85f).toInt().coerceAtLeast(160)
                val nextHeight = (currentBitmap.height * 0.85f).toInt().coerceAtLeast(160)
                if (nextWidth == currentBitmap.width || nextHeight == currentBitmap.height) {
                    quality = 25
                    loop++
                    continue
                }
                val scaled = Bitmap.createScaledBitmap(currentBitmap, nextWidth, nextHeight, true)
                if (scaled != currentBitmap) {
                    if (currentBitmap != frame) currentBitmap.recycle()
                    currentBitmap = scaled
                }
                quality = 85
                loop++
            }

            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            retriever.release()
        }
    }

    private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqMaxDim: Int): Int {
        var inSampleSize = 1
        var halfHeight = srcHeight / 2
        var halfWidth = srcWidth / 2
        while (halfHeight / inSampleSize >= reqMaxDim && halfWidth / inSampleSize >= reqMaxDim) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    companion object {
        private val assetNameHashRegex = Regex("^ASSET_\\d+_(-?\\d+)\\..+$")
        @Volatile
        private var instance: LocalAssetManager? = null

        fun getInstance(context: Context): LocalAssetManager {
            return instance ?: synchronized(this) {
                instance ?: LocalAssetManager(context.applicationContext).also { instance = it }
            }
        }
    }
}


