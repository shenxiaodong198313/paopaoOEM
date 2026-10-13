package com.paopaoime.aikeyboard.materialhub.logging

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.paopaoime.aikeyboard.ime.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AiReplyTaskLogger private constructor(
    private val appContext: Context,
    val sessionId: String,
    private val mode: String,
    private val tone: String,
    private val entry: String,
    private val taskTitle: String,
    private val startedAt: Long,
) {
    private val store = AiReplyTaskLogStore.getInstance(appContext)
    private val summary = JSONObject()
    private val runtime = JSONObject()
    @Volatile
    private var finished = false

    companion object {
        private const val LEVEL_INFO = "INFO"
        private const val LEVEL_WARN = "WARN"
        private const val LEVEL_ERROR = "ERROR"

        suspend fun startAiCommunicateTask(
            context: Context,
            sessionId: String,
            mode: String,
            tone: String,
            entry: String,
            taskTitle: String,
        ): AiReplyTaskLogger {
            val logger =
                AiReplyTaskLogger(
                    appContext = context.applicationContext,
                    sessionId = sessionId,
                    mode = mode,
                    tone = tone,
                    entry = entry,
                    taskTitle = taskTitle,
                    startedAt = System.currentTimeMillis(),
                )
            logger.start()
            return logger
        }

        suspend fun startFastLlmTask(
            context: Context,
            sessionId: String,
            tone: String,
        ): AiReplyTaskLogger =
            startAiCommunicateTask(
                context = context,
                sessionId = sessionId,
                mode = "llm",
                tone = tone,
                entry = "material_hub_fast_llm",
                taskTitle = "极速生成",
            )

        suspend fun startOcrTask(
            context: Context,
            sessionId: String,
            tone: String,
        ): AiReplyTaskLogger =
            startAiCommunicateTask(
                context = context,
                sessionId = sessionId,
                mode = "ocr",
                tone = tone,
                entry = "material_hub_ocr",
                taskTitle = "识屏回复",
            )

        suspend fun startImageReplyTask(
            context: Context,
            sessionId: String,
            tone: String,
        ): AiReplyTaskLogger =
            startAiCommunicateTask(
                context = context,
                sessionId = sessionId,
                mode = "image_reply",
                tone = tone,
                entry = "material_hub_image_reply",
                taskTitle = "传图回复",
            )
    }

    private suspend fun start() {
        store.createSession(
            sessionId = sessionId,
            mode = mode,
            tone = tone,
            entry = entry,
            startedAt = startedAt,
            deviceInfoJson = buildDeviceInfo().toString(),
        )
        appendEvent(
            level = LEVEL_INFO,
            phase = "start",
            event = "task_started",
            title = "${taskTitle}开始",
            detail = "mode=$mode tone=$tone entry=$entry",
        )
    }

    suspend fun info(
        phase: String,
        event: String,
        title: String,
        detail: String = "",
        data: JSONObject? = null,
    ) {
        appendEvent(LEVEL_INFO, phase, event, title, detail, data)
    }

    suspend fun warn(
        phase: String,
        event: String,
        title: String,
        detail: String = "",
        data: JSONObject? = null,
    ) {
        appendEvent(LEVEL_WARN, phase, event, title, detail, data)
    }

    suspend fun error(
        phase: String,
        event: String,
        title: String,
        detail: String = "",
        data: JSONObject? = null,
    ) {
        appendEvent(LEVEL_ERROR, phase, event, title, detail, data)
    }

    suspend fun setSummaryString(key: String, value: String?) {
        summary.put(key, value ?: "")
    }

    suspend fun setSummaryNumber(key: String, value: Number?) {
        summary.put(key, value ?: 0)
    }

    suspend fun setSummaryBoolean(key: String, value: Boolean) {
        summary.put(key, value)
    }

    suspend fun setRuntimeString(key: String, value: String?) {
        runtime.put(key, value ?: "")
        store.updateRuntimeInfo(sessionId, runtime.toString())
    }

    suspend fun setRuntimeNumber(key: String, value: Number?) {
        runtime.put(key, value ?: 0)
        store.updateRuntimeInfo(sessionId, runtime.toString())
    }

    suspend fun finishSuccess() {
        if (finished) return
        finished = true
        val finishedAt = System.currentTimeMillis()
        summary.put("totalCostMs", (finishedAt - startedAt).coerceAtLeast(0L))
        appendEvent(
            level = LEVEL_INFO,
            phase = "result",
            event = "task_succeeded",
            title = "${taskTitle}成功",
            detail = "sessionId=$sessionId",
            data = JSONObject().put("totalCostMs", summary.optLong("totalCostMs", 0L)),
        )
        store.finishSession(
            sessionId = sessionId,
            status = "success",
            reasonCode = "",
            reasonMessage = "",
            summaryJson = summary.toString(),
            runtimeInfoJson = runtime.toString(),
            abnormalLog = "",
            extraDetailJson = "{}",
            finishedAt = finishedAt,
        )
        AiReplyTaskUploadWorker.enqueueNow(appContext, sessionId)
    }

    suspend fun finishFailure(
        status: String,
        reasonCode: String,
        reasonMessage: String,
        throwable: Throwable? = null,
        extraDetail: JSONObject? = null,
    ) {
        if (finished) return
        finished = true
        val finishedAt = System.currentTimeMillis()
        summary.put("totalCostMs", (finishedAt - startedAt).coerceAtLeast(0L))
        val abnormalLog =
            buildString {
                append("reasonCode=")
                append(reasonCode)
                append('\n')
                append("reasonMessage=")
                append(reasonMessage)
                throwable?.let {
                    append('\n')
                    append(stackTraceToString(it))
                }
            }
        appendEvent(
            level = LEVEL_ERROR,
            phase = "result",
            event = "task_failed",
            title = "${taskTitle}失败",
            detail = "$reasonCode $reasonMessage".trim(),
            data =
                JSONObject()
                    .put("status", status)
                    .put("reasonCode", reasonCode)
                    .put("reasonMessage", reasonMessage),
        )
        store.finishSession(
            sessionId = sessionId,
            status = status,
            reasonCode = reasonCode,
            reasonMessage = reasonMessage,
            summaryJson = summary.toString(),
            runtimeInfoJson = runtime.toString(),
            abnormalLog = abnormalLog.take(200000),
            extraDetailJson = (extraDetail ?: JSONObject()).toString(),
            finishedAt = finishedAt,
        )
        AiReplyTaskUploadWorker.enqueueNow(appContext, sessionId)
    }

    private suspend fun appendEvent(
        level: String,
        phase: String,
        event: String,
        title: String,
        detail: String,
        data: JSONObject? = null,
    ) {
        if (finished && event != "upload_started" && event != "upload_finished" && event != "upload_failed") {
            return
        }
        val item =
            JSONObject()
                .put("eventId", "${sessionId}_${System.nanoTime()}")
                .put("sessionId", sessionId)
                .put("timestamp", System.currentTimeMillis())
                .put("level", level)
                .put("phase", phase)
                .put("event", event)
                .put("title", title)
                .put("detail", detail)
                .put("data", data ?: JSONObject())
        store.appendEvent(sessionId, item)
    }

    private suspend fun buildDeviceInfo(): JSONObject =
        withContext(Dispatchers.Default) {
            JSONObject()
                .put("appVersionCode", BuildConfig.VERSION_CODE)
                .put("appVersionName", BuildConfig.versionName)
                .put("packageName", appContext.packageName)
                .put("deviceBrand", Build.BRAND ?: "")
                .put("deviceModel", Build.MODEL ?: "")
                .put("manufacturer", Build.MANUFACTURER ?: "")
                .put("androidVersion", Build.VERSION.RELEASE ?: "")
                .put("sdkInt", Build.VERSION.SDK_INT)
                .put("romDisplay", Build.DISPLAY ?: "")
                .put("abi", Build.SUPPORTED_ABIS?.firstOrNull().orEmpty())
                .put("networkType", resolveNetworkType(appContext))
        }

    private fun resolveNetworkType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return ""
        val network = cm.activeNetwork ?: return "offline"
        val caps = cm.getNetworkCapabilities(network) ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    private fun stackTraceToString(t: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
}
