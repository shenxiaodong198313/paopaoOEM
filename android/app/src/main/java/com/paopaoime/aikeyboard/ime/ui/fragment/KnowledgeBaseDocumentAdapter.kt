package com.paopaoime.aikeyboard.ime.ui.fragment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDocumentUi
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class KnowledgeBaseDocumentAdapter(
    private val onDocumentClick: (KbDocumentUi) -> Unit,
    private val onRetryVectorize: (KbDocumentUi) -> Unit
) : RecyclerView.Adapter<KnowledgeBaseDocumentAdapter.ViewHolder>() {

    private val items = mutableListOf<KbDocumentUi>()
    private val previewCache = mutableMapOf<String, DocPreview>()
    private val thumbnailCache = object : LruCache<String, Bitmap>(thumbnailCacheSize()) {
        override fun sizeOf(key: String, value: Bitmap): Int = max(1, value.byteCount / 1024)
    }
    private val thumbnailLoading = mutableSetOf<String>()
    private val thumbnailExecutor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var htmlBadgeBitmap: Bitmap? = null
    private val urlDocCache = mutableMapOf<String, Boolean>()

    fun submitList(newItems: List<KbDocumentUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_kb_document, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.fileName
        holder.ivIcon.setImageResource(inferIconRes(item))
        holder.tvType.text = inferTypeLabel(item)
        holder.itemView.setOnClickListener { onDocumentClick(item) }

        val preview = previewCache[item.id] ?: buildPreview(holder.itemView.context, item).also {
            previewCache[item.id] = it
        }
        holder.tvSubtitle.text = preview.summary
        holder.tvTag1.text = preview.tags.getOrNull(0).orEmpty()
        holder.tvTag2.text = preview.tags.getOrNull(1).orEmpty()
        holder.tvTag1.visibility = if (preview.tags.isNotEmpty()) View.VISIBLE else View.GONE
        holder.tvTag2.visibility = if (preview.tags.size > 1) View.VISIBLE else View.GONE
        holder.layoutTags.visibility = if (preview.tags.isNotEmpty()) View.VISIBLE else View.GONE
        holder.tvSize.text = preview.sizeText
        holder.ivThumb.tag = item.id
        if (isHtmlDoc(item)) {
            holder.ivThumb.setImageBitmap(getHtmlBadgeBitmap(holder.itemView.context))
        } else {
            holder.ivThumb.setImageResource(R.drawable.bg_placeholder_gray)
            bindThumbnail(holder, item)
        }

        val errorText = when {
            item.parseStatus == 4 -> "解析失败"
            item.parseStatus == 3 && item.vectorStatus == 3 -> "向量化失败"
            else -> null
        }
        if (errorText == null) {
            holder.tvError.visibility = View.GONE
            holder.tvError.setOnClickListener(null)
        } else {
            holder.tvError.text = errorText
            holder.tvError.visibility = View.VISIBLE
            if (item.parseStatus == 3 && item.vectorStatus == 3) {
                holder.tvError.setOnClickListener { onRetryVectorize(item) }
            } else {
                holder.tvError.setOnClickListener(null)
            }
        }

        when {
            item.parseStatus == 1 || item.vectorStatus == 1 -> {
                holder.pbParse.isIndeterminate = true
                holder.pbParse.visibility = View.VISIBLE
            }
            item.parseStatus == 2 -> {
                holder.pbParse.isIndeterminate = false
                holder.pbParse.progress = item.parseProgress.coerceIn(0, 100)
                holder.pbParse.visibility = View.VISIBLE
            }
            else -> {
                holder.pbParse.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.iv_kb_doc_icon)
        val tvTitle: TextView = view.findViewById(R.id.tv_kb_doc_title)
        val tvSubtitle: TextView = view.findViewById(R.id.tv_kb_doc_subtitle)
        val tvError: TextView = view.findViewById(R.id.tv_kb_doc_error)
        val tvTag1: TextView = view.findViewById(R.id.tv_kb_doc_tag_1)
        val tvTag2: TextView = view.findViewById(R.id.tv_kb_doc_tag_2)
        val tvType: TextView = view.findViewById(R.id.tv_kb_doc_type)
        val tvSize: TextView = view.findViewById(R.id.tv_kb_doc_size)
        val pbParse: ProgressBar = view.findViewById(R.id.pb_kb_doc_parse)
        val ivThumb: ImageView = view.findViewById(R.id.iv_kb_doc_thumb)
        val layoutTags: View = view.findViewById(R.id.layout_kb_doc_tags)
    }

    private fun inferIconRes(item: KbDocumentUi): Int {
        val mime = item.mimeType.lowercase()
        if (mime.startsWith("image/")) return R.drawable.ic_doc_jpg
        if (mime == "application/pdf" || mime.contains("pdf")) return R.drawable.ic_doc_pdf
        if (mime.contains("wordprocessingml") || mime.contains("msword")) return R.drawable.ic_doc_word
        if (mime.contains("spreadsheetml") || mime.contains("ms-excel")) return R.drawable.ic_doc_excel
        if (mime.contains("presentationml") || mime.contains("ms-powerpoint")) return R.drawable.ic_doc_ppt
        if (mime.contains("html")) return R.drawable.ic_menu_language
        if (isUrlDoc(item)) return R.drawable.ic_menu_language

        return when (item.fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> R.drawable.ic_doc_jpg
            "pdf" -> R.drawable.ic_doc_pdf
            "doc", "docx" -> R.drawable.ic_doc_word
            "xls", "xlsx" -> R.drawable.ic_doc_excel
            "ppt", "pptx" -> R.drawable.ic_doc_ppt
            "html", "htm" -> R.drawable.ic_menu_language
            else -> R.drawable.ic_doc_pdf
        }
    }

    private data class DocPreview(
        val summary: String,
        val tags: List<String>,
        val sizeText: String
    )

    private fun buildPreview(context: android.content.Context, item: KbDocumentUi): DocPreview {
        val rawText = readParsedText(item.parsedTextPath, 2000)
        val summary = buildSummary(rawText, item.fileName)
        val tags = buildTags(rawText, item.fileName, item.mimeType)
        val sizeText = formatFileSize(getDocumentFile(context, item).length())
        return DocPreview(summary = summary, tags = tags, sizeText = sizeText)
    }

    private fun getDocumentFile(context: android.content.Context, item: KbDocumentUi): File {
        val root = File(context.filesDir, "knowledge_base")
        return File(File(root, item.directoryId), item.fileName)
    }

    private fun readParsedText(path: String?, maxChars: Int): String {
        val safePath = path?.trim().orEmpty()
        if (safePath.isBlank()) return ""
        val file = File(safePath)
        if (!file.exists()) return ""
        return runCatching {
            val builder = StringBuilder()
            file.bufferedReader().use { reader ->
                val buffer = CharArray(512)
                var total = 0
                while (true) {
                    val n = reader.read(buffer)
                    if (n <= 0) break
                    val remaining = maxChars - total
                    if (remaining <= 0) break
                    val toAppend = min(n, remaining)
                    builder.append(buffer, 0, toAppend)
                    total += toAppend
                    if (total >= maxChars) break
                }
            }
            builder.toString()
        }.getOrNull().orEmpty()
    }

    private fun buildSummary(rawText: String, fileName: String): String {
        val normalized = rawText.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return "暂无摘要"
        return smartTruncate(normalized, 140, fileName)
    }

    private fun smartTruncate(text: String, maxLen: Int, fileName: String): String {
        if (text.length <= maxLen) return text
        val lowerBound = (maxLen * 0.6).toInt()
        val cut = text.lastIndexOf(' ', maxLen)
        if (cut >= lowerBound) {
            return text.substring(0, cut).trim()
        }
        val altCut = text.lastIndexOfAny(charArrayOf('，', '。', '；', '、', ',', '.', ';', '!', '?'), maxLen)
        if (altCut >= lowerBound) {
            return text.substring(0, altCut + 1).trim()
        }
        val fallback = text.substring(0, maxLen).trim()
        if (fallback.isNotBlank()) return fallback
        return fileName.substringBeforeLast('.').ifBlank { fileName }
    }

    private fun buildTags(rawText: String, fileName: String, mimeType: String): List<String> {
        val tokens = mutableMapOf<String, Int>()
        val fileTokens = extractTokens(fileName.substringBeforeLast('.'), 3)
        for (token in fileTokens) {
            tokens[token] = (tokens[token] ?: 0) + 3
        }
        val contentTokens = extractTokens(rawText, 1)
        for (token in contentTokens) {
            tokens[token] = (tokens[token] ?: 0) + 1
        }
        val ranked = tokens.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenByDescending { it.key.length }
        )
        val tags = mutableListOf<String>()
        for (entry in ranked) {
            val tag = formatTag(entry.key)
            if (tag.isBlank()) continue
            if (tags.any { it.equals(tag, ignoreCase = true) }) continue
            tags.add(tag)
            if (tags.size >= 2) break
        }
        if (tags.size < 2) {
            val fallback = fallbackTags(fileName, mimeType)
            for (tag in fallback) {
                if (tags.any { it.equals(tag, ignoreCase = true) }) continue
                tags.add(tag)
                if (tags.size >= 2) break
            }
        }
        return tags
    }

    private fun bindThumbnail(holder: ViewHolder, item: KbDocumentUi) {
        val key = item.id
        val cached = thumbnailCache.get(key)
        if (cached != null) {
            holder.ivThumb.setImageBitmap(cached)
            return
        }
        if (thumbnailLoading.contains(key)) return
        if (shouldSkipThumbnail(item)) return
        if (isHtmlDoc(item)) return
        thumbnailLoading.add(key)
        val context = holder.itemView.context.applicationContext
        thumbnailExecutor.execute {
            val bitmap = buildThumbnail(context, item)
            mainHandler.post {
                thumbnailLoading.remove(key)
                if (bitmap != null) {
                    thumbnailCache.put(key, bitmap)
                }
                if (holder.ivThumb.tag == key && bitmap != null) {
                    holder.ivThumb.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun shouldSkipThumbnail(item: KbDocumentUi): Boolean {
        val ext = item.fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        if (ext == "xls" || ext == "xlsx") return true
        val mime = item.mimeType.lowercase(Locale.getDefault())
        return mime.contains("spreadsheetml") || mime.contains("ms-excel")
    }

    private fun buildThumbnail(context: android.content.Context, item: KbDocumentUi): Bitmap? {
        val file = getDocumentFile(context, item)
        if (!file.exists()) return null
        val sizePx = context.resources.getDimensionPixelSize(R.dimen.kb_doc_thumb_size)
        val ext = item.fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        val mime = item.mimeType.lowercase(Locale.getDefault())
        return when {
            mime.contains("html") || ext == "html" || ext == "htm" -> {
                buildHtmlBadgeThumbnail(sizePx)
            }
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> {
                decodeScaledBitmap(file, sizePx, sizePx)
            }
            mime.contains("pdf") || ext == "pdf" -> {
                renderPdfThumbnail(file, sizePx)
            }
            ext in setOf("xls", "xlsx") || mime.contains("spreadsheetml") || mime.contains("ms-excel") -> null
            else -> {
                val text = readParsedText(item.parsedTextPath, 600)
                renderTextThumbnail(text, item.fileName, sizePx)
            }
        }
    }

    private fun decodeScaledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    private fun renderPdfThumbnail(file: File, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val ratio = page.width.toFloat() / page.height.toFloat()
                    val targetWidth: Int
                    val targetHeight: Int
                    if (ratio >= 1f) {
                        targetWidth = sizePx
                        targetHeight = max(1, (sizePx / ratio).toInt())
                    } else {
                        targetHeight = sizePx
                        targetWidth = max(1, (sizePx * ratio).toInt())
                    }
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    private fun renderTextThumbnail(rawText: String, fileName: String, sizePx: Int): Bitmap? {
        val text = buildTextPreview(rawText, fileName)
        if (text.isBlank()) return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor("#666666")
        paint.textSize = sizePx * 0.18f
        val padding = max(6, (sizePx * 0.12f).toInt())
        val textWidth = sizePx - padding * 2
        val layout = buildStaticLayout(text, paint, textWidth)
        canvas.translate(padding.toFloat(), padding.toFloat())
        layout.draw(canvas)
        return bitmap
    }

    private fun buildStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        } else {
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
        }
    }

    private fun buildTextPreview(rawText: String, fileName: String): String {
        val normalized = rawText.replace(Regex("\\s+"), " ").trim()
        if (normalized.isNotBlank()) return smartTruncate(normalized, 80, fileName)
        return fileName.substringBeforeLast('.').ifBlank { fileName }
    }

    private fun getHtmlBadgeBitmap(context: android.content.Context): Bitmap {
        val cached = htmlBadgeBitmap
        if (cached != null) return cached
        val sizePx = context.resources.getDimensionPixelSize(R.dimen.kb_doc_thumb_size)
        val bitmap = buildHtmlBadgeThumbnail(sizePx)
        htmlBadgeBitmap = bitmap
        return bitmap
    }

    private fun buildHtmlBadgeThumbnail(sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF3E6")
        }
        val radius = sizePx * 0.2f
        val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF7A1A")
            textSize = sizePx * 0.28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fm = textPaint.fontMetrics
        val textX = sizePx * 0.5f
        val textY = sizePx * 0.5f - (fm.ascent + fm.descent) / 2f
        canvas.drawText("HTML", textX, textY, textPaint)
        return bitmap
    }

    private fun isHtmlDoc(item: KbDocumentUi): Boolean {
        val ext = item.fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        val mime = item.mimeType.lowercase(Locale.getDefault())
        return mime.contains("html") || ext == "html" || ext == "htm" || isUrlDoc(item)
    }

    private fun isUrlDoc(item: KbDocumentUi): Boolean {
        val cached = urlDocCache[item.id]
        if (cached != null) return cached
        val text = readParsedText(item.parsedTextPath, 4000)
        val hasLabel = text.contains("查看原文") || text.contains("原文链接")
        val hasUrl = android.util.Patterns.WEB_URL.matcher(text).find()
        val result = hasLabel || hasUrl
        urlDocCache[item.id] = result
        return result
    }

    private fun extractTokens(text: String, minWeight: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val english = Regex("[A-Za-z][A-Za-z0-9+._-]{1,}")
        for (match in english.findAll(text)) {
            val word = match.value.lowercase(Locale.getDefault())
            if (word.length < 3) continue
            if (EN_STOPWORDS.contains(word)) continue
            result.add(word)
        }
        val chinese = Regex("\\p{IsHan}{2,}")
        for (match in chinese.findAll(text)) {
            val seq = match.value
            if (seq.length <= 4) {
                if (!ZH_STOPWORDS.contains(seq)) result.add(seq)
            } else {
                for (i in 0 until seq.length - 1) {
                    val token = seq.substring(i, i + 2)
                    if (ZH_STOPWORDS.contains(token)) continue
                    result.add(token)
                }
            }
        }
        val weighted = mutableListOf<String>()
        for (token in result) {
            repeat(minWeight) { weighted.add(token) }
        }
        return weighted
    }

    private fun formatTag(token: String): String {
        val trimmed = token.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.all { it.isLetter() && it.isUpperCase() }) return trimmed
        if (trimmed.all { it.isLetterOrDigit() }) {
            if (trimmed.length <= 6) return trimmed.uppercase(Locale.getDefault())
            return trimmed.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
        return trimmed
    }

    private fun fallbackTags(fileName: String, mimeType: String): List<String> {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        val fromExt = when (ext) {
            "pdf" -> "PDF"
            "doc", "docx" -> "WORD"
            "xls", "xlsx" -> "EXCEL"
            "ppt", "pptx" -> "PPT"
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> "图片"
            else -> ""
        }
        val fromMime = when {
            mimeType.startsWith("image/") -> "图片"
            mimeType.contains("pdf") -> "PDF"
            mimeType.contains("wordprocessingml") || mimeType.contains("msword") -> "WORD"
            mimeType.contains("spreadsheetml") || mimeType.contains("ms-excel") -> "EXCEL"
            mimeType.contains("presentationml") || mimeType.contains("ms-powerpoint") -> "PPT"
            mimeType.contains("html") -> "网页"
            else -> "文档"
        }
        return listOf(fromExt, fromMime).filter { it.isNotBlank() }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0L) return "0 KB"
        val kb = size / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.getDefault(), "%.1f GB", gb)
            mb >= 1 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            kb >= 1 -> String.format(Locale.getDefault(), "%.1f KB", kb)
            else -> "${size} B"
        }
    }

    private fun inferTypeLabel(item: KbDocumentUi): String {
        val ext = item.fileName.substringAfterLast('.', "").uppercase(Locale.getDefault())
        if (ext.isNotBlank()) return ext
        val mime = item.mimeType.lowercase(Locale.getDefault())
        return when {
            isUrlDoc(item) -> "网页"
            mime.contains("pdf") -> "PDF"
            mime.contains("wordprocessingml") || mime.contains("msword") -> "DOCX"
            mime.contains("spreadsheetml") || mime.contains("ms-excel") -> "XLSX"
            mime.contains("presentationml") || mime.contains("ms-powerpoint") -> "PPTX"
            mime.startsWith("image/") -> "IMG"
            else -> "FILE"
        }
    }

    companion object {
        private fun thumbnailCacheSize(): Int {
            val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            return max(1024, maxMemory / 16)
        }

        private val EN_STOPWORDS = setOf(
            "the", "and", "for", "with", "this", "that", "from", "are", "was", "were",
            "you", "your", "but", "not", "can", "will", "just", "into", "over", "when",
            "how", "why", "what", "which", "they", "their", "our", "about", "also", "use",
            "using", "used", "than", "then", "have", "has", "had", "all", "any", "each",
            "such", "more", "most", "some", "other", "only", "been", "being"
        )
        private val ZH_STOPWORDS = setOf(
            "我们", "你们", "他们", "因为", "所以", "以及", "而且", "可以", "一个", "一些",
            "这种", "这个", "那个", "这些", "那些", "内容", "数据", "信息", "文档", "文件",
            "工作", "项目", "介绍", "方案", "报告", "需求", "说明", "相关", "主要", "可能"
        )
    }
}


