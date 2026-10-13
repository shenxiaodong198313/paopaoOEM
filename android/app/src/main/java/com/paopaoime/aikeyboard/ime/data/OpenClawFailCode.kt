package com.paopaoime.aikeyboard.ime.data

enum class OpenClawFailCode(val code: String) {
    NONE(""),
    POLICY_BLOCKED("POLICY_BLOCKED"),
    MODEL_CONTEXT_OVERFLOW("MODEL_CONTEXT_OVERFLOW"),
    ACTION_FORMAT_INVALID("ACTION_FORMAT_INVALID"),
    UI_ELEMENT_NOT_FOUND("UI_ELEMENT_NOT_FOUND"),
    CALLBACK_TIMEOUT("CALLBACK_TIMEOUT"),
    USER_STOPPED("USER_STOPPED"),
    PERMISSION_DENIED("PERMISSION_DENIED"),
    AGENT_LAUNCH_FAILED("AGENT_LAUNCH_FAILED"),
    CONTENT_GENERATION_FAILED("CONTENT_GENERATION_FAILED"),
    MODEL_REQUEST_FAILED("MODEL_REQUEST_FAILED"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromMessage(message: String): OpenClawFailCode {
            val lower = message.lowercase()
            return when {
                lower.contains("requested token count exceeds") ||
                    lower.contains("maximum context length") ||
                    lower.contains("context length") -> MODEL_CONTEXT_OVERFLOW
                lower.contains("unknown action format") ||
                    lower.contains("action_format_invalid") -> ACTION_FORMAT_INVALID
                lower.contains("ui element not found") ||
                    lower.contains("元素未找到") -> UI_ELEMENT_NOT_FOUND
                lower.contains("permission denied") ||
                    lower.contains("权限") -> PERMISSION_DENIED
                lower.contains("超时") || lower.contains("timeout") -> CALLBACK_TIMEOUT
                lower.contains("stopped") ||
                    lower.contains("stop by user") ||
                    lower.contains("用户停止") ||
                    lower.contains("手动停止") ||
                    lower.contains("已停止") -> USER_STOPPED
                lower.contains("无法启动 agent") -> AGENT_LAUNCH_FAILED
                lower.startsWith("error:") -> MODEL_REQUEST_FAILED
                else -> UNKNOWN
            }
        }
    }
}
