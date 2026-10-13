package com.paopaoime.aikeyboard.ime.data

import android.content.Context

class OpenClawAgentBridgeAdapter(private val appContext: Context) {
    suspend fun publish(
        request: AgentPublishRequest,
        onPhase: (AgentPublishPhase, String) -> Unit
    ): AgentPublishResult {
        return NpcAgentPublishExecutor.publish(
            context = appContext,
            request = request,
            onPhase = onPhase
        )
    }

    suspend fun verify(
        request: AgentPublishRequest,
        verifyTaskId: String,
        expectedKeywords: List<String>,
        publishStartAtMs: Long,
        maxAgeMinutes: Int,
        onPhase: (AgentPublishPhase, String) -> Unit
    ): AgentPublishResult {
        return NpcAgentPublishExecutor.verifyPublishedInMine(
            context = appContext,
            request = request,
            verifyTaskId = verifyTaskId,
            expectedKeywords = expectedKeywords,
            publishStartAtMs = publishStartAtMs,
            maxAgeMinutes = maxAgeMinutes,
            onPhase = onPhase
        )
    }
}
