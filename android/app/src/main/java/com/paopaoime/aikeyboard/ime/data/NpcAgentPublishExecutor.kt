package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AgentPublishPhase {
    ACCEPTED,
    RUNNING,
    FINISHED,
    FAILED
}

data class AgentPublishRequest(
    val taskId: String,
    val npcId: String,
    val channel: OperationChannel,
    val contentType: OperationContentType,
    val content: String,
    val mediaPaths: List<String> = emptyList(),
    val mediaSelectCount: Int = 0,
    val selectionMode: String = "",
    val generatedAtMs: Long = 0L
)

data class AgentPublishResult(
    val success: Boolean,
    val durationMs: Long,
    val errorMessage: String = "",
    val failCode: String = "",
    val callbackMessage: String = ""
)

object NpcAgentPublishExecutor {
    private const val AGENT_CALLBACK_TIMEOUT_MS = 600_000L
    private const val VERIFY_CALLBACK_TIMEOUT_MS = 180_000L
    private const val AUTOG_LM_REMOVED_MESSAGE = "旧 AutoGLM 自动化已废弃，当前链路不再执行"

    suspend fun publish(
        context: Context,
        request: AgentPublishRequest,
        onPhase: (AgentPublishPhase, String) -> Unit
    ): AgentPublishResult = withContext(Dispatchers.IO) {
        val fingerprint = buildTaskFingerprint(request)
        onPhase(AgentPublishPhase.ACCEPTED, "accepted")
        onPhase(AgentPublishPhase.RUNNING, "running")
        val result = runAgentTask(
            context = context,
            taskId = request.taskId,
            npcId = request.npcId,
            command = buildAgentCommand(
                request = request,
                fingerprint = fingerprint
            ),
            timeoutMs = AGENT_CALLBACK_TIMEOUT_MS
        )
        if (result.success) {
            val normalized = result.callbackMessage.lowercase()
            val callbackFingerprint = parseStringField(result.callbackMessage, "FP")
            val isPublishOk = normalized.contains("publish_ok")
            val mode = parseStringField(result.callbackMessage, "MODE")
            if (!isPublishOk) {
                onPhase(AgentPublishPhase.FAILED, "failed")
                return@withContext AgentPublishResult(
                    success = false,
                    durationMs = result.durationMs,
                    errorMessage = "发布回调缺少 PUBLISH_OK 标记",
                    failCode = OpenClawFailCode.UNKNOWN.code,
                    callbackMessage = result.callbackMessage
                )
            }
            if (callbackFingerprint.isBlank() || callbackFingerprint != fingerprint) {
                onPhase(AgentPublishPhase.FAILED, "failed")
                return@withContext AgentPublishResult(
                    success = false,
                    durationMs = result.durationMs,
                    errorMessage = "发布回调指纹不匹配",
                    failCode = OpenClawFailCode.UNKNOWN.code,
                    callbackMessage = result.callbackMessage
                )
            }
            if (mode.isNotBlank() && !mode.equals("PUBLISH_ONLY", ignoreCase = true)) {
                onPhase(AgentPublishPhase.FAILED, "failed")
                return@withContext AgentPublishResult(
                    success = false,
                    durationMs = result.durationMs,
                    errorMessage = "发布回调模式非法",
                    failCode = OpenClawFailCode.UNKNOWN.code,
                    callbackMessage = result.callbackMessage
                )
            }
            onPhase(AgentPublishPhase.FINISHED, "finished")
            result
        } else {
            onPhase(AgentPublishPhase.FAILED, "failed")
            result
        }
    }

    suspend fun verifyPublishedInMine(
        context: Context,
        request: AgentPublishRequest,
        verifyTaskId: String,
        expectedKeywords: List<String>,
        publishStartAtMs: Long,
        maxAgeMinutes: Int = 8,
        onPhase: (AgentPublishPhase, String) -> Unit
    ): AgentPublishResult = withContext(Dispatchers.IO) {
        onPhase(AgentPublishPhase.ACCEPTED, "accepted")
        onPhase(AgentPublishPhase.RUNNING, "running")
        val sanitizedKeywords = expectedKeywords
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val requiredHits = sanitizedKeywords.size.coerceAtLeast(1).coerceAtMost(2)
        val fingerprint = buildTaskFingerprint(request)
        val result = runAgentTask(
            context = context,
            taskId = verifyTaskId,
            npcId = request.npcId,
            command = buildVerifyCommand(
                request = request,
                expectedKeywords = sanitizedKeywords,
                publishStartAtMs = publishStartAtMs,
                maxAgeMinutes = maxAgeMinutes,
                requiredHits = requiredHits,
                fingerprint = fingerprint
            ),
            timeoutMs = VERIFY_CALLBACK_TIMEOUT_MS
        )
        if (result.success) {
            val normalized = result.callbackMessage.lowercase()
            val callbackFingerprint = parseStringField(result.callbackMessage, "FP")
            if (callbackFingerprint.isNotBlank() && callbackFingerprint != fingerprint) {
                onPhase(AgentPublishPhase.FAILED, "failed")
                return@withContext AgentPublishResult(
                    success = false,
                    durationMs = result.durationMs,
                    errorMessage = "回查指纹不匹配",
                    failCode = OpenClawFailCode.UNKNOWN.code,
                    callbackMessage = result.callbackMessage
                )
            }
            if (normalized.contains("verify_stale")) {
                onPhase(AgentPublishPhase.FAILED, "failed")
                return@withContext AgentPublishResult(
                    success = false,
                    durationMs = result.durationMs,
                    errorMessage = "命中到历史内容，需继续发布最新内容",
                    failCode = OpenClawFailCode.UNKNOWN.code,
                    callbackMessage = result.callbackMessage
                )
            }
            val ageMin = parseIntField(normalized, "age_min")
            val hits = parseIntField(normalized, "hits")
            val keywordHit = sanitizedKeywords
                .map { it.lowercase() }
                .any { normalized.contains(it) }
            val ageOk = ageMin == null || ageMin in 0..maxAgeMinutes
            val hitOk = (hits != null && hits >= requiredHits) || keywordHit
            if (normalized.contains("verify_ok") && ageOk && hitOk) {
                onPhase(AgentPublishPhase.FINISHED, "finished")
                result
            } else {
                onPhase(AgentPublishPhase.FAILED, "failed")
                AgentPublishResult(
                    success = false,
                    durationMs = result.durationMs,
                    errorMessage = "回查结果缺少有效证据",
                    failCode = OpenClawFailCode.UNKNOWN.code,
                    callbackMessage = result.callbackMessage
                )
            }
        } else {
            onPhase(AgentPublishPhase.FAILED, "failed")
            result
        }
    }

    private suspend fun runAgentTask(
        context: Context,
        taskId: String,
        npcId: String,
        command: String,
        timeoutMs: Long
    ): AgentPublishResult {
        return AgentPublishResult(
            success = false,
            durationMs = 0L,
            errorMessage = AUTOG_LM_REMOVED_MESSAGE,
            failCode = OpenClawFailCode.AGENT_LAUNCH_FAILED.code,
            callbackMessage = "AUTOG_LM_REMOVED"
        )
    }

    private fun buildAgentCommand(request: AgentPublishRequest, fingerprint: String): String {
        val contentSnapshot = request.content
            .replace("\n", " ")
            .trim()
            .take(420)
        val mediaPaths = request.mediaPaths
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return buildString {
            append("请执行社交媒体发布任务。")
            append("目标平台仅限：${request.channel.displayName}。")
            append("内容类型：${request.contentType.displayName}。")
            if (request.contentType == OperationContentType.IMAGE || request.contentType == OperationContentType.VIDEO) {
                val mediaType = if (request.contentType == OperationContentType.VIDEO) "video" else "image"
                val selectCount = if (request.contentType == OperationContentType.VIDEO) 1 else request.mediaSelectCount.coerceIn(1, 9)
                append("媒体选择模式：${request.selectionMode.ifBlank { "latest_generated" }}。")
                append("媒体类型：$mediaType。")
                append("目标选择数量：$selectCount。")
                if (request.generatedAtMs > 0L) {
                    append("素材生成时间戳：${request.generatedAtMs}。")
                }
                if (mediaPaths.isNotEmpty()) {
                    append("本次生成素材本地路径：${mediaPaths.take(9).joinToString("；")}。")
                }
                if (contentSnapshot.isNotBlank()) {
                    append("发布文案：$contentSnapshot。")
                }
                append("请进入相册后按“最近项目/时间最新”优先选择刚生成的素材。")
                append("若为图片，连续选择最新的${selectCount}张；若为视频，只选择1条最新视频。")
                append("禁止图片与视频混选，禁止选择旧素材。")
                append("若无法确认是本次新素材，立即失败并回传原因，不要猜测。")
            } else {
                append("发布内容：$contentSnapshot。")
            }
            append("任务指纹：$fingerprint。")
            append("本任务是单平台单次发布任务，禁止切换到其他社交平台。")
            append("本任务只负责发布动作本身，不要执行“我的频道/主页”回查。")
            append("当你确认发布动作已完成后，执行 finish(message=\"PUBLISH_OK|FP=$fingerprint|MODE=PUBLISH_ONLY|EXCERPT=<摘要>\")。")
            append("若发布动作无法完成，执行 fail(\"PUBLISH_FAILED|FP=$fingerprint|REASON=<原因>\")。")
        }
    }

    private fun buildVerifyCommand(
        request: AgentPublishRequest,
        expectedKeywords: List<String>,
        publishStartAtMs: Long,
        maxAgeMinutes: Int,
        requiredHits: Int,
        fingerprint: String
    ): String {
        val keywordText = expectedKeywords
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("、")
            .ifBlank { request.channel.displayName }
        return buildString {
            append("请执行发布回查任务。")
            append("目标平台仅限：${request.channel.displayName}。")
            append("任务指纹：$fingerprint。")
            append("进入该平台的我的频道或个人主页，刷新后只检查最新1条记录。")
            append("任务发布时间下限：$publishStartAtMs。")
            append("重点匹配关键词：$keywordText。")
            append("若最新记录是旧内容（例如早于${maxAgeMinutes}分钟前，或明显不是本次新发），执行 fail(\"VERIFY_STALE|FP=$fingerprint|TIME_TEXT=<时间>|EXCERPT=<摘要>\")。")
            append("若关键词命中不足${requiredHits}个，执行 fail(\"VERIFY_NO_MATCH|FP=$fingerprint|HITS=<命中数>|EXCERPT=<摘要>\")。")
            append("仅当最新记录满足“发布时间在${maxAgeMinutes}分钟内且关键词命中>=${requiredHits}”时，")
            append("执行 finish(message=\"VERIFY_OK|FP=$fingerprint|AGE_MIN=<分钟>|HITS=<命中数>|TIME_TEXT=<时间>|EXCERPT=<摘要>\")。")
        }
    }

    private fun parseIntField(text: String, key: String): Int? {
        val marker = "$key="
        val start = text.indexOf(marker)
        if (start < 0) return null
        val tail = text.substring(start + marker.length)
        val number = tail.takeWhile { it.isDigit() }
        return number.toIntOrNull()
    }

    private fun parseStringField(text: String, key: String): String {
        val marker = "$key="
        val start = text.indexOf(marker)
        if (start < 0) return ""
        val tail = text.substring(start + marker.length)
        return tail.takeWhile { it != '|' && it != '\n' && it != '\r' }.trim()
    }

    private fun buildTaskFingerprint(request: AgentPublishRequest): String {
        val normalized = request.content
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(280)
        val raw = "${request.channel.code}|${request.contentType.name}|$normalized"
        val hash = raw.hashCode().toUInt().toString(16)
        return hash.takeLast(8).uppercase()
    }
}
