package com.paopaoime.aikeyboard.ime.service

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.equationl.paddleocr4android.CpuPowerMode
import com.equationl.paddleocr4android.OCR
import com.equationl.paddleocr4android.OcrConfig
import com.equationl.paddleocr4android.bean.OcrResult
import com.paopaoime.aikeyboard.oem.OemEnterpriseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

object PaddleOcrEngine {
    private const val TAG = "PaddleOcrEngine"
    private const val MODEL_PATH = "paddle_ocr/PP-OCRv5_mobile"
    private const val LABEL_PATH = "paddle_ocr/labels/ppocr_keys_ocrv5.txt"
    private const val DET_MODEL = "det.nb"
    private const val REC_MODEL = "rec.nb"
    private const val CLS_MODEL = "cls.nb"
    private const val IDLE_RELEASE_MS = 12_000L
    private const val OCR_MAX_INPUT_LONG_SIDE_PX = 1280
    private const val OCR_DET_LONG_SIZE_MEMORY_FIRST_PX = 640
    private const val OCR_DET_LONG_SIZE_PERFORMANCE_PX = 960
    private const val OCR_MIN_AVAILABLE_MEMORY_BYTES = 160L * 1024L * 1024L
    private const val LOW_END_TOTAL_MEMORY_BYTES = 4L * 1024L * 1024L * 1024L
    private const val LOW_END_MEMORY_CLASS_MB = 192

    private val engineMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ocr: OCR? = null
    private var initSucceeded = false
    @Volatile
    private var lastUseElapsedMs: Long = 0L
    @Volatile
    private var idleReleaseJob: Job? = null

    suspend fun recognize(
        context: Context,
        bitmap: Bitmap,
    ): Result<List<PaddleOcrLine>> = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            runCatching {
                if (!OemEnterpriseConfig.resolveOcrEnabled(context.applicationContext)) {
                    releaseLocked("ocr_disabled_by_config")
                    throw IllegalStateException("SDK 内置 OCR 已通过配置关闭")
                }
                ensureEnoughMemoryForOcr(context.applicationContext)
                lastUseElapsedMs = SystemClock.elapsedRealtime()
                val engine = ensureEngine(context.applicationContext)
                val inputBitmap = downscaleForOcr(bitmap)
                try {
                    val result = engine.runSync(inputBitmap).getOrThrow()
                    scheduleIdleRelease()
                    mapLines(result)
                } finally {
                    if (inputBitmap !== bitmap) {
                        inputBitmap.recycle()
                    }
                }
            }.onFailure {
                scheduleIdleRelease()
            }
        }
    }

    fun release(reason: String) {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch {
            engineMutex.withLock {
                releaseLocked(reason)
            }
        }
    }

    private fun ensureEngine(context: Context): OCR {
        val current = ocr ?: OCR(context).also { ocr = it }
        if (initSucceeded) return current
        val profile = resolveRuntimeProfile(context)
        val initResult = current.initModelSync(
            OcrConfig().apply {
                modelPath = MODEL_PATH
                labelPath = LABEL_PATH
                detModelFilename = DET_MODEL
                recModelFilename = REC_MODEL
                clsModelFilename = CLS_MODEL
                isRunDet = true
                isRunCls = true
                isRunRec = true
                cpuThreadNum = profile.cpuThreadNum
                cpuPowerMode = profile.cpuPowerMode
                detLongSize = profile.detLongSize
                isUseOpencl = false
                isDrwwTextPositionBox = false
            }
        )
        if (initResult.isFailure || initResult.getOrNull() != true) {
            ocr = null
            initSucceeded = false
            throw initResult.exceptionOrNull() ?: IllegalStateException("PaddleOCR 初始化失败")
        }
        initSucceeded = true
        Log.d(
            TAG,
            "PaddleOCR initialized with modelPath=$MODEL_PATH profile=${profile.name} threads=${profile.cpuThreadNum} power=${profile.cpuPowerMode} detLongSize=${profile.detLongSize}"
        )
        return current
    }

    private fun resolveRuntimeProfile(context: Context): OcrRuntimeProfile {
        val lowEnd = isLowEndOrMemoryTight(context)
        return if (lowEnd) {
            OcrRuntimeProfile(
                name = "memory_first",
                cpuThreadNum = 1,
                cpuPowerMode = CpuPowerMode.LITE_POWER_LOW,
                detLongSize = OCR_DET_LONG_SIZE_MEMORY_FIRST_PX,
            )
        } else {
            OcrRuntimeProfile(
                name = "performance",
                cpuThreadNum = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                cpuPowerMode = CpuPowerMode.LITE_POWER_FULL,
                detLongSize = OCR_DET_LONG_SIZE_PERFORMANCE_PX,
            )
        }
    }

    private fun ensureEnoughMemoryForOcr(context: Context) {
        val memoryInfo = readMemoryInfo(context)
        val availableBytes = memoryInfo?.availMem ?: Long.MAX_VALUE
        val thresholdBytes = memoryInfo?.threshold ?: OCR_MIN_AVAILABLE_MEMORY_BYTES
        val guardedThreshold = maxOf(OCR_MIN_AVAILABLE_MEMORY_BYTES, thresholdBytes * 2)
        if (memoryInfo?.lowMemory == true || availableBytes < guardedThreshold) {
            releaseLocked("system_low_memory_${availableBytes}bytes")
            throw IllegalStateException("当前设备可用内存不足，已跳过本次 OCR 识别")
        }
    }

    private fun isSystemMemoryTight(context: Context): Boolean {
        val memoryInfo = readMemoryInfo(context) ?: return false
        val guardedThreshold = maxOf(OCR_MIN_AVAILABLE_MEMORY_BYTES, memoryInfo.threshold * 2)
        return memoryInfo.lowMemory || memoryInfo.availMem < guardedThreshold
    }

    private fun isLowEndOrMemoryTight(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return isSystemMemoryTight(context)
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val processors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return activityManager.isLowRamDevice ||
            activityManager.memoryClass <= LOW_END_MEMORY_CLASS_MB ||
            memoryInfo.totalMem in 1 until LOW_END_TOTAL_MEMORY_BYTES ||
            processors <= 4 ||
            isSystemMemoryTight(context)
    }

    private fun readMemoryInfo(context: Context): ActivityManager.MemoryInfo? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return null
        return ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
    }

    private fun downscaleForOcr(bitmap: Bitmap): Bitmap {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= OCR_MAX_INPUT_LONG_SIDE_PX) {
            return bitmap
        }
        val scale = OCR_MAX_INPUT_LONG_SIDE_PX / longSide.toFloat()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        Log.d(
            TAG,
            "downscale OCR bitmap from ${bitmap.width}x${bitmap.height} to ${targetWidth}x${targetHeight}"
        )
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private data class OcrRuntimeProfile(
        val name: String,
        val cpuThreadNum: Int,
        val cpuPowerMode: CpuPowerMode,
        val detLongSize: Int,
    )

    private fun scheduleIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            engineMutex.withLock {
                val idleFor = SystemClock.elapsedRealtime() - lastUseElapsedMs
                if (idleFor >= IDLE_RELEASE_MS) {
                    releaseLocked("idle_timeout_${idleFor}ms")
                }
            }
        }
    }

    private fun releaseLocked(reason: String) {
        val current = ocr ?: return
        runCatching {
            current.releaseModel()
        }.onFailure {
            Log.w(TAG, "PaddleOCR release failed reason=$reason message=${it.message}", it)
        }
        ocr = null
        initSucceeded = false
        Log.d(TAG, "PaddleOCR released reason=$reason")
    }

    private fun mapLines(result: OcrResult): List<PaddleOcrLine> {
        return result.outputRawResult
            .asSequence()
            .mapNotNull { item ->
                val text = item.label?.trim().orEmpty()
                if (text.isBlank()) return@mapNotNull null
                val rect = toRect(item.points) ?: return@mapNotNull null
                PaddleOcrLine(
                    text = text,
                    rect = rect,
                    confidence = item.confidence
                )
            }
            .sortedWith(compareBy<PaddleOcrLine> { it.rect.top }.thenBy { it.rect.left })
            .toList()
    }

    private fun toRect(points: List<Point>?): Rect? {
        if (points.isNullOrEmpty()) return null
        val left = points.minOf { it.x }
        val top = points.minOf { it.y }
        val right = points.maxOf { it.x }.coerceAtLeast(left + 1)
        val bottom = points.maxOf { it.y }.coerceAtLeast(top + 1)
        return Rect(left, top, right, bottom)
            .takeIf { it.width() > 0 && it.height() > 0 }
    }
}

data class PaddleOcrLine(
    val text: String,
    val rect: Rect,
    val confidence: Float,
)
