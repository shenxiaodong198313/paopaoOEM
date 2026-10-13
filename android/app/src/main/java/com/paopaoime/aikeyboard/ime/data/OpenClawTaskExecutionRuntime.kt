package com.paopaoime.aikeyboard.ime.data

enum class OpenClawTaskKind {
    GENERATE,
    PUBLISH,
    VERIFY,
    RETURN
}

data class OpenClawTaskStep(
    val id: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long,
    val retryCount: Int,
    val failCode: String
)

data class OpenClawWorkerResult(
    val success: Boolean,
    val retryable: Boolean,
    val failCode: String = "",
    val message: String = "",
    val durationMs: Long = 0L,
    val output: String = "",
    val path: String = ""
)

fun interface OpenClawTaskWorker {
    suspend fun execute(attempt: Int): OpenClawWorkerResult
}

data class OpenClawRetryDecision(
    val shouldRetry: Boolean,
    val nextDelayMs: Long
)

class OpenClawRetryCompensator(
    private val maxRetry: Int = 1,
    private val baseDelayMs: Long = 1200L
) {
    fun decide(attempt: Int, result: OpenClawWorkerResult): OpenClawRetryDecision {
        if (result.success) return OpenClawRetryDecision(shouldRetry = false, nextDelayMs = 0L)
        if (!result.retryable) return OpenClawRetryDecision(shouldRetry = false, nextDelayMs = 0L)
        if (attempt >= maxRetry) return OpenClawRetryDecision(shouldRetry = false, nextDelayMs = 0L)
        val delay = (baseDelayMs * (attempt + 1)).coerceAtMost(15_000L)
        return OpenClawRetryDecision(shouldRetry = true, nextDelayMs = delay)
    }
}
