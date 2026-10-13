package com.paopaoime.aikeyboard.materialhub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import com.paopaoime.aikeyboard.ime.service.PaddleOcrEngine
import com.paopaoime.aikeyboard.ime.service.PaddleOcrLine
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class AiCommunicateOcrSnapshot(
    val text: String,
    val capturedAtElapsedMs: Long,
    val attempts: Int,
    val signature: String,
)

object AiCommunicateOcrCacheStore {
    private const val TAG = "AiCommunicateOcr"
    private const val MAX_AGE_MS = 25_000L
    private const val CAPTURE_BUDGET_MS = 1_200L
    private const val RETRY_DELAY_MS = 120L
    private const val GOOD_ENOUGH_SCORE = 7
    private const val PREVIEW_FILE_NAME = "latest_preview.jpg"
    private const val LAUNCH_SNAPSHOT_MAX_AGE_MS = 2_500L
    private const val LAUNCH_WARMUP_WAIT_MS = 450L
    private const val OCR_TOP_CROP_RATIO = 0.08f
    private const val OCR_FALLBACK_BOTTOM_RATIO = 0.78f
    private const val OCR_MAX_WIDTH_PX = 960
    private const val EDGE_DECORATION_MAX_WIDTH_RATIO = 0.18f
    private const val LEFT_EDGE_DECORATION_RIGHT_RATIO = 0.18f
    private const val RIGHT_EDGE_DECORATION_LEFT_RATIO = 0.78f
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val captureMutex = Mutex()

    @Volatile
    private var latestSnapshot: AiCommunicateOcrSnapshot? = null

    @Volatile
    private var warmupJob: Job? = null

    @Volatile
    private var latestPreviewPath: String? = null

    @Volatile
    private var latestPreviewCapturedAtElapsedMs: Long = 0L

    fun clearSessionCache() {
        latestSnapshot = null
        latestPreviewPath = null
        latestPreviewCapturedAtElapsedMs = 0L
    }

    fun snapshot(maxAgeMs: Long = MAX_AGE_MS): AiCommunicateOcrSnapshot? {
        val current = latestSnapshot ?: return null
        return if (SystemClock.elapsedRealtime() - current.capturedAtElapsedMs <= maxAgeMs) current else null
    }

    fun previewImagePath(maxAgeMs: Long = MAX_AGE_MS): String? {
        val path = latestPreviewPath ?: return null
        val ageMs = SystemClock.elapsedRealtime() - latestPreviewCapturedAtElapsedMs
        return if (ageMs <= maxAgeMs && File(path).exists()) path else null
    }

    suspend fun prepareSnapshotForLaunch(context: Context): AiCommunicateOcrSnapshot? {
        snapshot(maxAgeMs = LAUNCH_SNAPSHOT_MAX_AGE_MS)?.let { cached ->
            if (previewImagePath(maxAgeMs = LAUNCH_SNAPSHOT_MAX_AGE_MS) != null) {
                Log.d(TAG, "prepareSnapshotForLaunch reuse fresh snapshot textLength=${cached.text.length}")
                return cached
            }
        }
        warmupJob?.takeIf { it.isActive }?.let { job ->
            withTimeoutOrNull(LAUNCH_WARMUP_WAIT_MS) {
                job.join()
            }
            snapshot(maxAgeMs = LAUNCH_SNAPSHOT_MAX_AGE_MS)?.let { warmed ->
                if (previewImagePath(maxAgeMs = LAUNCH_SNAPSHOT_MAX_AGE_MS) != null) {
                    Log.d(TAG, "prepareSnapshotForLaunch reuse warmup snapshot textLength=${warmed.text.length}")
                    return warmed
                }
            }
        }
        clearSessionCache()
        return captureFresh(context.applicationContext, force = true)
    }

    fun warmUpAsync(context: Context, force: Boolean = false) {
        if (!AiCommunicateScreenCapturePermissionStore.hasActiveGrant()) return
        if (!force && snapshot() != null) return
        if (warmupJob?.isActive == true) return
        warmupJob = scope.launch {
            runCatching { captureFresh(context.applicationContext, force = force) }
                .onFailure { error ->
                    Log.w(TAG, "warmUpAsync captureFresh failed message=${error.message}", error)
                }
        }
    }

    suspend fun captureFresh(context: Context, force: Boolean = false): AiCommunicateOcrSnapshot? {
        return captureMutex.withLock {
            if (!force) {
                snapshot()?.let { return it }
            }
            captureBestEffort(context) ?: latestSnapshot
        }
    }

    suspend fun captureFromBitmap(
        context: Context,
        bitmap: Bitmap,
        force: Boolean = false,
    ): AiCommunicateOcrSnapshot? {
        return captureMutex.withLock {
            if (!force) {
                snapshot()?.let { return it }
            }
            Log.d(
                TAG,
                "captureFromBitmap start force=$force bitmap=${bitmap.width}x${bitmap.height}"
            )
            persistPreviewBitmap(context, bitmap)
            val candidate = buildSnapshotCandidate(
                context = context,
                bitmap = bitmap,
                attempt = 1,
            )
            latestSnapshot = candidate?.snapshot ?: latestSnapshot
            Log.d(
                TAG,
                "captureFromBitmap result hasCandidate=${candidate != null} cachedTextLength=${latestSnapshot?.text?.length ?: 0}"
            )
            latestSnapshot
        }
    }

    suspend fun cachePreviewBitmap(
        context: Context,
        bitmap: Bitmap,
    ): String? {
        return captureMutex.withLock {
            persistPreviewBitmap(context, bitmap)
        }
    }

    private suspend fun captureBestEffort(context: Context): AiCommunicateOcrSnapshot? {
        val startedAt = SystemClock.elapsedRealtime()
        var attempt = 0
        var bestCandidate: SnapshotCandidate? = null
        while (SystemClock.elapsedRealtime() - startedAt < CAPTURE_BUDGET_MS) {
            attempt += 1
            val bitmap = AiCommunicateScreenCaptureManager.captureBitmap(context)
            if (bitmap == null && !AiCommunicateScreenCapturePermissionStore.hasActiveGrant()) {
                Log.w(TAG, "captureBestEffort stop attempt=$attempt because active projection is unavailable")
                break
            }
            val candidate = bitmap?.let {
                try {
                    val previewPath = persistPreviewBitmap(context, it)
                    buildSnapshotCandidate(context, it, attempt)
                        ?.also { snapshotCandidate ->
                            if (snapshotCandidate.isBetterThan(bestCandidate)) {
                                bestCandidate = snapshotCandidate
                                latestSnapshot = snapshotCandidate.snapshot
                                if (previewPath != null) {
                                    latestPreviewPath = previewPath
                                    latestPreviewCapturedAtElapsedMs = SystemClock.elapsedRealtime()
                                }
                                Log.d(
                                    TAG,
                                    "captureBestEffort improved attempt=$attempt quality=${snapshotCandidate.qualityScore} messages=${snapshotCandidate.messageCount} textLength=${snapshotCandidate.snapshot.text.length}"
                                )
                            }
                        } ?: run {
                        if (bestCandidate == null && previewPath != null) {
                            latestPreviewPath = previewPath
                            latestPreviewCapturedAtElapsedMs = SystemClock.elapsedRealtime()
                        }
                        null
                    }
                } finally {
                    it.recycle()
                }
            }
            if ((bestCandidate?.qualityScore ?: 0) >= GOOD_ENOUGH_SCORE) {
                break
            }
            delay(RETRY_DELAY_MS)
        }
        return bestCandidate?.snapshot
    }

    private fun persistPreviewBitmap(context: Context, bitmap: Bitmap): String? {
        return runCatching {
            val directory = File(context.cacheDir, "ai_communicate_preview").apply { mkdirs() }
            val file = File(directory, PREVIEW_FILE_NAME)
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
                output.flush()
            }
            latestPreviewPath = file.absolutePath
            latestPreviewCapturedAtElapsedMs = SystemClock.elapsedRealtime()
            file.absolutePath
        }.onFailure {
            Log.w(TAG, "persistPreviewBitmap failed message=${it.message}", it)
        }.getOrNull()
    }

    private suspend fun buildSnapshotCandidate(
        context: Context,
        bitmap: Bitmap,
        attempt: Int,
    ): SnapshotCandidate? {
        val ocrBitmap = prepareBitmapForOcr(bitmap)
        val ocrBitmapWidth = ocrBitmap.width
        val ocrBitmapHeight = ocrBitmap.height
        val ocrLines = try {
            PaddleOcrEngine.recognize(context, ocrBitmap).getOrElse {
                Log.w(TAG, "buildSnapshotCandidate OCR failed attempt=$attempt message=${it.message}", it)
                emptyList()
            }
        } finally {
            if (ocrBitmap !== bitmap) {
                ocrBitmap.recycle()
            }
        }
        Log.d(
            TAG,
            "buildSnapshotCandidate raw attempt=$attempt sourceBitmap=${bitmap.width}x${bitmap.height} ocrBitmap=${ocrBitmapWidth}x${ocrBitmapHeight} rawLineCount=${ocrLines.size}"
        )
        val normalized = normalizeConversation(
            lines = ocrLines,
            bitmapWidth = ocrBitmapWidth,
            bitmapHeight = ocrBitmapHeight,
        ) ?: normalizeLooseConversation(
            lines = ocrLines,
            bitmapWidth = ocrBitmapWidth,
            bitmapHeight = ocrBitmapHeight,
        )
        if (normalized == null || normalized.structuredText.isBlank()) {
            Log.d(
                TAG,
                "buildSnapshotCandidate empty attempt=$attempt rawLineCount=${ocrLines.size}"
            )
            return null
        }
        val snapshot = AiCommunicateOcrSnapshot(
            text = normalized.structuredText,
            capturedAtElapsedMs = SystemClock.elapsedRealtime(),
            attempts = attempt,
            signature = normalized.structuredText.normalizedSignature(),
        )
        Log.d(
            TAG,
            "buildSnapshotCandidate success attempt=$attempt quality=${normalized.qualityScore} messages=${normalized.messageCount} text=${normalized.structuredText.take(240)}"
        )
        return SnapshotCandidate(
            snapshot = snapshot,
            qualityScore = normalized.qualityScore,
            messageCount = normalized.messageCount,
        )
    }

    private fun normalizeConversation(
        lines: List<PaddleOcrLine>,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): StructuredConversation? {
        if (lines.isEmpty()) return null
        val minTop = lines.minOfOrNull { it.rect.top } ?: 0
        val screenWidth = bitmapWidth.coerceAtLeast(1)
        val screenHeight = bitmapHeight.coerceAtLeast(1)
        val topCutoff = minTop + (screenHeight * 0.05f).toInt()
        val bottomCutoff = (screenHeight * 0.96f).toInt()
        var lowConfidenceDropped = 0
        var outOfBoundsDropped = 0
        var blankDropped = 0
        var centerDropped = 0
        var noiseDropped = 0
        var edgeDecorationDropped = 0
        val chatLines = lines
            .asSequence()
            .filter {
                val keep = it.confidence >= 0.20f
                if (!keep) lowConfidenceDropped += 1
                keep
            }
            .filter {
                val keep = it.rect.top >= topCutoff && it.rect.bottom <= bottomCutoff
                if (!keep) outOfBoundsDropped += 1
                keep
            }
            .mapNotNull { line ->
                val sanitized = sanitizeLine(line.text)
                if (sanitized.isBlank()) {
                    blankDropped += 1
                    return@mapNotNull null
                }
                if (isLikelyKeyboardNoiseLine(line, sanitized, screenWidth, screenHeight)) {
                    noiseDropped += 1
                    return@mapNotNull null
                }
                if (isLikelyEdgeDecoration(line, sanitized, screenWidth)) {
                    edgeDecorationDropped += 1
                    return@mapNotNull null
                }
                val side = resolveSide(line, screenWidth)
                if (side == ConversationSide.CENTER) {
                    centerDropped += 1
                    return@mapNotNull null
                }
                if (isNoiseLine(sanitized, side)) {
                    noiseDropped += 1
                    return@mapNotNull null
                }
                ChatLine(
                    side = side,
                    text = normalizeSpecialMessage(sanitized),
                    rectTop = line.rect.top,
                    rectBottom = line.rect.bottom,
                    rectLeft = line.rect.left,
                    rectRight = line.rect.right,
                )
            }
            .sortedWith(compareBy<ChatLine> { it.rectTop }.thenBy { it.side.ordinal })
            .toList()
        Log.d(
            TAG,
            "normalizeConversation raw=${lines.size} kept=${chatLines.size} lowConfidenceDropped=$lowConfidenceDropped outOfBoundsDropped=$outOfBoundsDropped blankDropped=$blankDropped centerDropped=$centerDropped noiseDropped=$noiseDropped edgeDecorationDropped=$edgeDecorationDropped topCutoff=$topCutoff bottomCutoff=$bottomCutoff"
        )
        if (chatLines.isEmpty()) return null
        val grouped = buildMessageBlocks(chatLines, screenHeight)
        val messages = grouped.mapNotNull(::normalizeMessageBlock)
        Log.d(
            TAG,
            "normalizeConversation groupedMessages=${grouped.size} normalizedMessages=${messages.size} screenWidth=$screenWidth screenHeight=$screenHeight"
        )
        if (messages.isEmpty()) return null
        val structured = messages
            .takeLast(18)
            .joinToString("\n") { message ->
                val speaker = if (message.side == ConversationSide.LEFT) "客户" else "我"
                "$speaker：${message.text}"
            }
            .trim()
        if (structured.isBlank()) return null
        val totalChars = messages.sumOf { it.text.length }
        val hasBothSides = messages.any { it.side == ConversationSide.LEFT } && messages.any { it.side == ConversationSide.RIGHT }
        val hasDocument = messages.any { it.kind == MessageKind.DOCUMENT }
        val quality = messages.size * 2 + (totalChars / 18) + if (hasBothSides) 2 else 0 + if (hasDocument) 1 else 0
        return StructuredConversation(
            structuredText = structured,
            messageCount = messages.size,
            qualityScore = quality,
        )
    }

    private fun normalizeLooseConversation(
        lines: List<PaddleOcrLine>,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): StructuredConversation? {
        if (lines.isEmpty()) return null
        val screenWidth = bitmapWidth.coerceAtLeast(1)
        val screenHeight = bitmapHeight.coerceAtLeast(1)
        val topCutoff = (screenHeight * 0.03f).toInt()
        val bottomCutoff = (screenHeight * 0.97f).toInt()
        val chatLines = lines
            .asSequence()
            .filter { it.confidence >= 0.12f }
            .filter { it.rect.top >= topCutoff && it.rect.bottom <= bottomCutoff }
            .mapNotNull { line ->
                val sanitized = sanitizeLine(line.text)
                if (sanitized.isBlank()) return@mapNotNull null
                if (isLikelyKeyboardNoiseLine(line, sanitized, screenWidth, screenHeight)) return@mapNotNull null
                if (isLikelyEdgeDecoration(line, sanitized, screenWidth)) return@mapNotNull null
                if (isLooseNoiseLine(sanitized)) return@mapNotNull null
                ChatLine(
                    side = resolveLooseSide(line, screenWidth),
                    text = normalizeSpecialMessage(sanitized),
                    rectTop = line.rect.top,
                    rectBottom = line.rect.bottom,
                    rectLeft = line.rect.left,
                    rectRight = line.rect.right,
                )
            }
            .sortedWith(compareBy<ChatLine> { it.rectTop }.thenBy { it.side.ordinal })
            .toList()
        if (chatLines.isEmpty()) return null
        val grouped = buildMessageBlocks(chatLines, screenHeight)
        val messages = grouped.mapNotNull(::normalizeMessageBlock)
        if (messages.isEmpty()) return null
        val structured = messages
            .takeLast(18)
            .joinToString("\n") { message ->
                val speaker = if (message.side == ConversationSide.RIGHT) "我" else "客户"
                "$speaker：${message.text}"
            }
            .trim()
        if (structured.isBlank()) return null
        return StructuredConversation(
            structuredText = structured,
            messageCount = messages.size,
            qualityScore = messages.size + (messages.sumOf { it.text.length } / 24),
        )
    }

    private fun sanitizeLine(raw: String): String {
        return raw
            .replace(Regex("""\s+"""), " ")
            .replace("|", "")
            .trim()
    }

    private fun isNoiseLine(line: String, side: ConversationSide): Boolean {
        val normalized = line.lowercase(Locale.ROOT)
        if (normalized.isBlank()) return true
        if (normalized.matches(Regex("""\d{1,2}:\d{2}"""))) return true
        if (normalized.matches(Regex("""\d{1,2}月\d{1,2}日.*"""))) return true
        val exactNoise = setOf(
            "ai沟通",
            "智能生成",
            "轻松闲聊",
            "严谨正式",
            "知识库",
            "敬请期待",
            "发送",
            "返回",
            "更多",
            "拼音",
            "中a",
            "?123",
            "由泡泡 ai 生成，内容仅供参考",
            "v",
        )
        if (normalized in exactNoise) return true
        val partialNoise = listOf(
            "后续支持结合知识库内容回复",
            "正在理解屏幕内容",
            "内容仅供参考",
        )
        if (partialNoise.any { normalized.contains(it) }) return true
        if (side == ConversationSide.CENTER) return true
        return false
    }

    private fun isLooseNoiseLine(line: String): Boolean {
        val normalized = line.lowercase(Locale.ROOT)
        if (normalized.isBlank()) return true
        if (normalized.matches(Regex("""\d{1,2}:\d{2}"""))) return true
        if (normalized.matches(Regex("""\d{1,2}月\d{1,2}日.*"""))) return true
        return normalized in setOf(
            "ai沟通",
            "智能生成",
            "轻松闲聊",
            "严谨正式",
            "知识库",
            "发送",
            "返回",
            "更多",
            "企业输入法",
        )
    }

    private fun isLikelyKeyboardNoiseLine(
        line: PaddleOcrLine,
        text: String,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        val hasChinese = text.any { it in '\u4e00'..'\u9fff' }
        val widthRatio = (line.rect.width().coerceAtLeast(1)).toFloat() / screenWidth.coerceAtLeast(1)
        val isLowerArea = line.rect.top >= (screenHeight * 0.72f).toInt()
        if (!hasChinese && normalized.matches(Regex("""[a-z0-9]{1,4}"""))) return true
        if (!hasChinese && text.length <= 4 && widthRatio <= 0.22f && isLowerArea) return true
        if (normalized.matches(Regex("""\d+""")) && widthRatio <= 0.18f) return true
        return false
    }

    private fun isLikelyEdgeDecoration(
        line: PaddleOcrLine,
        text: String,
        screenWidth: Int,
    ): Boolean {
        val safeWidth = screenWidth.coerceAtLeast(1)
        val widthRatio = line.rect.width().toFloat() / safeWidth
        if (widthRatio > EDGE_DECORATION_MAX_WIDTH_RATIO) return false
        val normalized = text.normalizedSignature()
        if (normalized.isBlank()) return true
        val isShort = normalized.length <= 8
        val inLeftDecorationStrip = line.rect.right.toFloat() / safeWidth <= LEFT_EDGE_DECORATION_RIGHT_RATIO
        val inRightDecorationStrip = line.rect.left.toFloat() / safeWidth >= RIGHT_EDGE_DECORATION_LEFT_RATIO
        return (inLeftDecorationStrip || inRightDecorationStrip) && (isShort || normalized.contains("ai"))
    }

    private fun resolveSide(line: PaddleOcrLine, screenWidth: Int): ConversationSide {
        val safeWidth = screenWidth.coerceAtLeast(1)
        val centerX = line.rect.centerX()
        val isRight = line.rect.right >= (safeWidth * 0.78f).toInt() || centerX >= safeWidth * 0.62f
        val isLeft = line.rect.left <= (safeWidth * 0.22f).toInt() || centerX <= safeWidth * 0.42f
        return when {
            isRight && !isLeft -> ConversationSide.RIGHT
            isLeft && !isRight -> ConversationSide.LEFT
            centerX >= safeWidth * 0.56f -> ConversationSide.RIGHT
            centerX <= safeWidth * 0.44f -> ConversationSide.LEFT
            else -> ConversationSide.CENTER
        }
    }

    private fun resolveLooseSide(line: PaddleOcrLine, screenWidth: Int): ConversationSide {
        val centerX = line.rect.centerX()
        return if (centerX >= screenWidth * 0.5f) ConversationSide.RIGHT else ConversationSide.LEFT
    }

    private fun buildMessageBlocks(
        lines: List<ChatLine>,
        screenHeight: Int,
    ): List<MessageBlock> {
        val maxGap = (screenHeight * 0.035f).toInt().coerceAtLeast(28)
        val blocks = mutableListOf<MessageBlock>()
        for (line in lines) {
            val last = blocks.lastOrNull()
            if (
                last != null &&
                last.side == line.side &&
                line.rectTop - last.bottom <= maxGap
            ) {
                blocks[blocks.lastIndex] = last.append(line)
            } else {
                blocks += MessageBlock(
                    side = line.side,
                    lines = listOf(line.text),
                    bottom = line.rectBottom,
                    left = line.rectLeft,
                    right = line.rectRight,
                )
            }
        }
        return blocks
            .map { block ->
                val normalizedLines = block.lines
                    .asSequence()
                    .map(String::trim)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .toList()
                block.copy(lines = normalizedLines)
            }
            .filter { it.lines.isNotEmpty() }
            .filterNot { block -> block.lines.joinToString(" ").normalizedSignature().isBlank() }
            .filterNot { block ->
                val normalized = block.lines.joinToString(" ").normalizedSignature()
                normalized.length <= 4 && normalized.none { it in 'a'..'z' || it in '\u4e00'..'\u9fff' }
            }
    }

    private fun normalizeMessageBlock(block: MessageBlock): ConversationMessage? {
        val dedupedLines = block.lines
            .map(String::trim)
            .filter { it.isNotBlank() }
        if (dedupedLines.isEmpty()) return null
        val contentLines = dedupedLines.filterNot(::isMessageMetaLine)
        if (contentLines.isEmpty()) return null
        val mergedText = contentLines.joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (mergedText.isBlank()) return null
        if (isMediaPlaceholder(mergedText)) return null
        val hasDocumentMeta = dedupedLines.any(::isDocumentMetaLine)
        val documentTitle = extractDocumentTitle(contentLines, hasDocumentMeta)
        val normalizedText = (documentTitle ?: mergedText)
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalizedText.isBlank()) return null
        val signature = normalizedText.normalizedSignature()
        if (signature.isBlank()) return null
        if (signature.length <= 1 && normalizedText.none { it in '\u4e00'..'\u9fff' }) return null
        return ConversationMessage(
            side = block.side,
            text = normalizedText,
            kind = if (documentTitle != null) MessageKind.DOCUMENT else MessageKind.TEXT,
        )
    }

    private fun isMessageMetaLine(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return true
        if (isDocumentMetaLine(trimmed)) return true
        val normalized = trimmed.normalizedSignature()
        if (normalized.isBlank()) return true
        if (normalized in setOf("微信", "企业微信", "聊天信息", "发送中")) return true
        if (Regex("""\d+%""").matches(trimmed)) return true
        return false
    }

    private fun isDocumentMetaLine(text: String): Boolean {
        val trimmed = text.trim()
        val normalized = trimmed.lowercase(Locale.ROOT)
        if (Regex("""\b\d+(\.\d+)?\s?(kb|mb|gb)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)) return true
        if (Regex("""\d+\s*页""").containsMatchIn(trimmed)) return true
        if (normalized.contains("未下载") || normalized.contains("已下载") || normalized.contains("可预览")) return true
        return false
    }

    private fun isMediaPlaceholder(text: String): Boolean {
        val normalized = text.normalizedSignature()
        if (normalized.isBlank()) return true
        val exactPlaceholders = setOf(
            "图片",
            "照片",
            "相片",
            "视频",
            "小视频",
            "短视频",
            "表情",
            "表情包",
            "动画表情",
            "微信红包",
            "转账",
            "位置",
            "名片",
        )
        if (normalized in exactPlaceholders) return true
        return normalized.startsWith("图片") && normalized.length <= 4 ||
            normalized.startsWith("视频") && normalized.length <= 4
    }

    private fun extractDocumentTitle(
        lines: List<String>,
        hasDocumentMeta: Boolean,
    ): String? {
        val merged = lines
            .filterNot(::isDocumentMetaLine)
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (merged.isBlank()) return null
        val lower = merged.lowercase(Locale.ROOT)
        val hasFileExtension = Regex("""\.(pdf|docx?|xlsx?|pptx?|txt|zip|rar|7z)$""").containsMatchIn(lower)
        val hasDatePrefix = Regex("""20\d{2}[-./年]\d{1,2}[-./月]\d{1,2}""").containsMatchIn(merged)
        val documentKeywords = listOf(
            "文档",
            "附件",
            "合同",
            "报价",
            "报价单",
            "清单",
            "记录",
            "汇总",
            "方案",
            "需求",
            "排期",
            "简历",
            "报表",
            "表格",
            "资料",
            "目录",
            "名录",
            "台账",
            "收支",
            "对账",
        )
        val hasDocumentKeyword = documentKeywords.any { merged.contains(it) }
        val looksLikeDocument = hasDocumentMeta || hasFileExtension || (hasDatePrefix && hasDocumentKeyword)
        if (!looksLikeDocument) return null
        return "[文档] $merged"
    }

    private fun prepareBitmapForOcr(bitmap: Bitmap): Bitmap {
        val cropped = cropBitmapForChat(bitmap)
        if (cropped.width <= OCR_MAX_WIDTH_PX) {
            return cropped
        }
        val scaledHeight = (cropped.height * (OCR_MAX_WIDTH_PX / cropped.width.toFloat())).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(cropped, OCR_MAX_WIDTH_PX, scaledHeight, true)
        if (scaled !== cropped && cropped !== bitmap) {
            cropped.recycle()
        }
        return scaled
    }

    private fun cropBitmapForChat(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap
        val top = (height * OCR_TOP_CROP_RATIO).toInt().coerceIn(0, height - 2)
        val detectedKeyboardTop = detectKeyboardTop(bitmap)
        val fallbackBottom = (height * OCR_FALLBACK_BOTTOM_RATIO).toInt()
        val bottom = (detectedKeyboardTop ?: fallbackBottom)
            .coerceAtLeast((height * 0.48f).toInt())
            .coerceAtMost(height)
        if (top <= 0 && bottom >= height) return bitmap
        val croppedHeight = (bottom - top).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, 0, top, width, croppedHeight)
    }

    private fun detectKeyboardTop(bitmap: Bitmap): Int? {
        val startY = (bitmap.height * 0.58f).toInt()
        val endY = (bitmap.height * 0.90f).toInt()
        val rowStep = 18
        val colStep = 20
        var consecutiveDarkRows = 0
        for (y in startY until endY step rowStep) {
            var luminanceSum = 0L
            var sampleCount = 0
            for (x in 0 until bitmap.width step colStep) {
                val color = bitmap.getPixel(x, y)
                val luminance = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
                luminanceSum += luminance
                sampleCount += 1
            }
            if (sampleCount == 0) continue
            val averageLuminance = (luminanceSum / sampleCount).toInt()
            if (averageLuminance <= 95) {
                consecutiveDarkRows += 1
                if (consecutiveDarkRows >= 3) {
                    return (y - (bitmap.height * 0.05f).toInt()).coerceAtLeast((bitmap.height * 0.45f).toInt())
                }
            } else {
                consecutiveDarkRows = 0
            }
        }
        return null
    }

    private fun normalizeSpecialMessage(line: String): String {
        val normalized = line.trim()
        if (normalized.matches(Regex("""\d+\s*["秒]"""))) {
            return "[语音] ${normalized.replace("\"", "秒")}"
        }
        return normalized
    }

    private fun String.normalizedSignature(): String {
        return lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), "")
            .replace(Regex("""[^\u4e00-\u9fa5a-z0-9]"""), "")
    }

    private data class SnapshotCandidate(
        val snapshot: AiCommunicateOcrSnapshot,
        val qualityScore: Int,
        val messageCount: Int,
    ) {
        fun isBetterThan(other: SnapshotCandidate?): Boolean {
            if (other == null) return true
            return qualityScore > other.qualityScore ||
                (qualityScore == other.qualityScore && messageCount > other.messageCount) ||
                (qualityScore == other.qualityScore && messageCount == other.messageCount && snapshot.text.length > other.snapshot.text.length)
        }
    }

    private data class StructuredConversation(
        val structuredText: String,
        val messageCount: Int,
        val qualityScore: Int,
    )

    private enum class MessageKind {
        TEXT,
        DOCUMENT,
    }

    private data class ConversationMessage(
        val side: ConversationSide,
        val text: String,
        val kind: MessageKind,
    )

    private enum class ConversationSide {
        LEFT,
        RIGHT,
        CENTER,
    }

    private data class ChatLine(
        val side: ConversationSide,
        val text: String,
        val rectTop: Int,
        val rectBottom: Int,
        val rectLeft: Int,
        val rectRight: Int,
    )

    private data class MessageBlock(
        val side: ConversationSide,
        val lines: List<String>,
        val bottom: Int,
        val left: Int,
        val right: Int,
    ) {
        fun append(line: ChatLine): MessageBlock {
            val mergedLines = if (lines.lastOrNull() == line.text) {
                lines
            } else {
                lines + line.text
            }
            return copy(
                lines = mergedLines,
                bottom = line.rectBottom,
                left = minOf(left, line.rectLeft),
                right = maxOf(right, line.rectRight),
            )
        }
    }
}
