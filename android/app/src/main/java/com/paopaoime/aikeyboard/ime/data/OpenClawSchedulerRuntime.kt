package com.paopaoime.aikeyboard.ime.data

import kotlin.math.min

data class OpenClawScheduledTask(
    val plan: NpcOperationPlan,
    val basePriority: Int,
    val queuedAt: Long,
    val deferCount: Int = 0
)

data class OpenClawSchedulerDecision(
    val allowed: Boolean,
    val reasonCode: String,
    val reasonText: String
)

object OpenClawSchedulerReasonCode {
    const val ACCEPTED = "ACCEPTED"
    const val REJECT_PER_NPC_LIMIT = "REJECT_PER_NPC_LIMIT"
    const val DEFER_AGENT_EXCLUSIVE = "DEFER_AGENT_EXCLUSIVE"
    const val DEFER_EMPTY = "DEFER_EMPTY"
}

class OpenClawSchedulerRuntime {
    private val globalQueue = ArrayDeque<OpenClawScheduledTask>()
    private val perNpcQueue = mutableMapOf<String, ArrayDeque<OpenClawScheduledTask>>()
    private val waitScores = mutableMapOf<String, Int>()

    fun enqueue(task: OpenClawScheduledTask, maxPendingPerNpc: Int): OpenClawSchedulerDecision {
        val npcQueue = perNpcQueue.getOrPut(task.plan.npcId) { ArrayDeque() }
        if (npcQueue.size >= maxPendingPerNpc) {
            return OpenClawSchedulerDecision(
                allowed = false,
                reasonCode = OpenClawSchedulerReasonCode.REJECT_PER_NPC_LIMIT,
                reasonText = "单NPC待执行任务达到上限"
            )
        }
        npcQueue.addLast(task)
        globalQueue.addLast(task)
        waitScores.putIfAbsent(task.plan.npcId, 0)
        return OpenClawSchedulerDecision(
            allowed = true,
            reasonCode = OpenClawSchedulerReasonCode.ACCEPTED,
            reasonText = "已入队"
        )
    }

    fun pollNext(agentExclusiveActive: Boolean): OpenClawScheduledTask? {
        if (agentExclusiveActive) return null
        val candidates = mutableListOf<OpenClawScheduledTask>()
        perNpcQueue.forEach { (_, queue) ->
            queue.firstOrNull()?.let { candidates.add(it) }
        }
        if (candidates.isEmpty()) return null
        val next = candidates.maxByOrNull { task ->
            val waitScore = waitScores[task.plan.npcId] ?: 0
            task.basePriority + waitScore
        } ?: return null
        val npcId = next.plan.npcId
        perNpcQueue[npcId]?.removeFirstOrNull()
        val globalIndex = globalQueue.indexOfFirst { it.plan.taskId == next.plan.taskId }
        if (globalIndex >= 0) {
            repeat(globalIndex) {
                globalQueue.removeFirstOrNull()?.let { globalQueue.addLast(it) }
            }
            globalQueue.removeFirstOrNull()
        }
        waitScores.keys.forEach { key ->
            waitScores[key] = min(12, (waitScores[key] ?: 0) + if (key == npcId) -2 else 2)
        }
        return next
    }

    fun totalPending(): Int = globalQueue.size

    fun pendingForNpc(npcId: String): Int = perNpcQueue[npcId]?.size ?: 0

    fun removePendingForNpc(npcId: String): List<OpenClawScheduledTask> {
        val removed = perNpcQueue.remove(npcId)?.toList().orEmpty()
        if (removed.isEmpty()) return emptyList()
        val removedTaskIds = removed.map { it.plan.taskId }.toSet()
        val kept = globalQueue.filterNot { removedTaskIds.contains(it.plan.taskId) }
        globalQueue.clear()
        kept.forEach { globalQueue.addLast(it) }
        waitScores.remove(npcId)
        return removed
    }

    fun clear() {
        globalQueue.clear()
        perNpcQueue.clear()
        waitScores.clear()
    }

    fun drainAllPending(): List<OpenClawScheduledTask> {
        val pending = globalQueue.toList()
        clear()
        return pending
    }
}
