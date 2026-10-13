package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MemoryStorage(private val context: Context) {
    private val file = File(context.filesDir, "memories.json")

    fun listMemories(): List<MemoryEntry> {
        if (!file.exists()) return emptyList()
        val content = file.readText()
        if (content.isBlank()) return emptyList()
        val array = JSONArray(content)
        val result = mutableListOf<MemoryEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val title = obj.optString("title")
            val body = obj.optString("content")
            result.add(MemoryEntry(id, title, body))
        }
        return result.sortedBy { it.title.lowercase(Locale.getDefault()) }
    }

    fun createMemory(title: String, content: String): MemoryEntry {
        val id = "memory_${System.currentTimeMillis()}"
        val entry = MemoryEntry(id, title, content)
        val list = listMemories().toMutableList()
        list.add(entry)
        saveList(list)
        return entry
    }

    fun updateMemory(id: String, title: String, content: String) {
        val list = listMemories().map {
            if (it.id == id) MemoryEntry(id, title, content) else it
        }
        saveList(list)
    }

    fun deleteMemory(id: String) {
        val list = listMemories().filter { it.id != id }
        saveList(list)
    }

    private fun saveList(list: List<MemoryEntry>) {
        val array = JSONArray()
        list.forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("title", entry.title)
            obj.put("content", entry.content)
            array.put(obj)
        }
        file.writeText(array.toString())
    }
}

enum class MarkdownMemoryLayer(val folder: String) {
    WORKING("working"),
    SHORT_TERM("short-term"),
    LONG_TERM("long-term")
}

data class MarkdownMemoryDraft(
    val layer: MarkdownMemoryLayer,
    val persona: String,
    val source: String,
    val topic: String,
    val agentId: String = "",
    val sessionId: String = "",
    val resourceId: String = "",
    val itemId: String = "",
    val tags: List<String>,
    val relatedTaskIds: List<String>,
    val references: List<String> = emptyList(),
    val eventId: String = "",
    val ttlHours: Int,
    val confidence: Double,
    val contextText: String,
    val factText: String,
    val decisionText: String,
    val evidenceText: String,
    val nextText: String
)

data class MarkdownMemoryRecord(
    val id: String,
    val layer: String,
    val persona: String,
    val agentId: String,
    val sessionId: String,
    val resourceId: String,
    val itemId: String,
    val topic: String,
    val updatedAt: String,
    val tags: List<String>,
    val relatedTaskIds: List<String>,
    val references: List<String>,
    val eventId: String,
    val fact: String,
    val decision: String,
    val evidence: String,
    val filePath: String
)

data class MarkdownTopicIndexEntry(
    val persona: String,
    val topic: String,
    val updatedAt: String,
    val lastFile: String,
    val totalWrites: Int,
    val tags: List<String>,
    val layerCounts: Map<String, Int>
)

data class MarkdownMemoryRetrieveRequest(
    val query: String,
    val persona: String? = null,
    val agentId: String? = null,
    val sessionId: String? = null,
    val tags: List<String> = emptyList(),
    val topK: Int = 8,
    val sufficiencyThreshold: Double = 1.6
)

class MarkdownMemoryStore(private val context: Context) {
    private val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
    private val metricsStore = MemoryMetricsStore(context)
    private val root = File(context.filesDir, "memory")
    private val workingDir = File(root, MarkdownMemoryLayer.WORKING.folder)
    private val shortTermDir = File(root, MarkdownMemoryLayer.SHORT_TERM.folder)
    private val longTermDir = File(root, MarkdownMemoryLayer.LONG_TERM.folder)
    private val indexDir = File(root, "index")
    private val archiveDir = File(root, "archive")
    private val topicIndexFile = File(indexDir, "topic_index.json")

    fun writeWorking(draft: MarkdownMemoryDraft): String {
        return writeMemoryByPipeline(draft.copy(layer = MarkdownMemoryLayer.WORKING))
    }

    fun writeShortTerm(draft: MarkdownMemoryDraft): String {
        return writeMemoryByPipeline(draft.copy(layer = MarkdownMemoryLayer.SHORT_TERM))
    }

    fun summarizeTaskToShortTerm(
        persona: String,
        topic: String,
        taskId: String,
        summary: String,
        evidence: String,
        tags: List<String> = emptyList(),
        agentId: String = "",
        sessionId: String = "",
        resourceId: String = "",
        references: List<String> = emptyList(),
        eventId: String = ""
    ): String {
        val draft = MarkdownMemoryDraft(
            layer = MarkdownMemoryLayer.SHORT_TERM,
            persona = persona,
            source = "agent_task",
            topic = topic,
            agentId = agentId,
            sessionId = sessionId,
            resourceId = resourceId,
            tags = tags,
            relatedTaskIds = listOf(taskId),
            references = references,
            eventId = eventId,
            ttlHours = 24 * 14,
            confidence = 0.86,
            contextText = "任务结束后自动沉淀短期记忆",
            factText = summary,
            decisionText = "保留为短期运营经验，供后续任务复用",
            evidenceText = evidence,
            nextText = "下次同平台任务优先复用本结论"
        )
        val path = writeShortTerm(draft)
        promoteTopicToLongTermIfNeeded(persona, topic)
        return path
    }

    fun listMarkdownRecords(): List<MarkdownMemoryRecord> {
        ensureDirs()
        val result = mutableListOf<MarkdownMemoryRecord>()
        result += listLayerRecords(workingDir, MarkdownMemoryLayer.WORKING.folder)
        result += listLayerRecords(shortTermDir, MarkdownMemoryLayer.SHORT_TERM.folder)
        result += listLayerRecords(longTermDir, MarkdownMemoryLayer.LONG_TERM.folder)
        return result.sortedByDescending { it.updatedAt }
    }

    fun listTopicIndexEntries(): List<MarkdownTopicIndexEntry> {
        ensureDirs()
        if (!topicIndexFile.exists()) return emptyList()
        val rootObj = runCatching { JSONObject(topicIndexFile.readText()) }.getOrDefault(JSONObject())
        val result = mutableListOf<MarkdownTopicIndexEntry>()
        val keys = rootObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = rootObj.optJSONObject(key) ?: continue
            val layerObj = obj.optJSONObject("layer_counts") ?: JSONObject()
            val counts = mutableMapOf<String, Int>()
            val layerKeys = layerObj.keys()
            while (layerKeys.hasNext()) {
                val layer = layerKeys.next()
                counts[layer] = layerObj.optInt(layer, 0)
            }
            result.add(
                MarkdownTopicIndexEntry(
                    persona = obj.optString("persona"),
                    topic = obj.optString("topic"),
                    updatedAt = obj.optString("updated_at"),
                    lastFile = obj.optString("last_file"),
                    totalWrites = obj.optInt("total_writes", 0),
                    tags = parseJsonArray(obj.optJSONArray("tags")),
                    layerCounts = counts
                )
            )
        }
        return result.sortedByDescending { it.updatedAt }
    }

    fun readMemoryFile(path: String): String {
        if (path.isBlank()) return ""
        val content = runCatching { File(path).readText() }.getOrDefault("")
        if (content.isNotBlank()) {
            metricsStore.recordRetrieve(query = "read_file", hitCount = 1, topScore = 1.0)
        }
        return content
    }

    fun deleteMemoryFile(path: String): Boolean {
        if (path.isBlank()) return false
        val file = File(path)
        if (!file.exists()) return false
        val deleted = runCatching { file.delete() }.getOrDefault(false)
        if (deleted) {
            rebuildTopicIndex()
        }
        return deleted
    }

    fun retrieveRecords(request: MarkdownMemoryRetrieveRequest): List<MarkdownMemoryRecord> {
        val query = request.query.trim()
        if (query.isBlank()) return emptyList()
        val queryTokens = tokenize(query)
        val all = listMarkdownRecords().filter { record ->
            val personaMatch = request.persona.isNullOrBlank() || record.persona == request.persona
            val agentMatch = request.agentId.isNullOrBlank() || record.agentId == request.agentId
            val sessionMatch = request.sessionId.isNullOrBlank() || record.sessionId == request.sessionId
            val tagsMatch = request.tags.isEmpty() || record.tags.any { it in request.tags }
            personaMatch && agentMatch && sessionMatch && tagsMatch
        }
        if (all.isEmpty()) return emptyList()
        val scored = all.map { record ->
            record to scoreRecord(record, queryTokens)
        }.sortedByDescending { it.second }
        val topScore = scored.firstOrNull()?.second ?: 0.0
        val effectiveTopK = request.topK.coerceIn(1, 32)
        val result = if (topScore >= request.sufficiencyThreshold) {
            scored.take((effectiveTopK / 2).coerceAtLeast(1)).map { it.first }
        } else {
            scored.take(effectiveTopK).map { it.first }
        }
        metricsStore.recordRetrieve(query = query, hitCount = result.size, topScore = topScore)
        return result
    }

    private fun writeMemoryByPipeline(draft: MarkdownMemoryDraft): String {
        val normalizedDraft = normalizeDraft(draft)
        val dedup = dedupeExisting(normalizedDraft)
        if (dedup != null) {
            metricsStore.recordWrite(layer = normalizedDraft.layer.folder, deduped = true)
            return dedup
        }
        val path = writeMemory(normalizedDraft)
        metricsStore.recordWrite(layer = normalizedDraft.layer.folder, deduped = false)
        return path
    }

    private fun writeMemory(draft: MarkdownMemoryDraft): String {
        ensureDirs()
        val createdAt = formatter.format(Date())
        val id = "mem_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val fileName = "$id.md"
        val targetDir = when (draft.layer) {
            MarkdownMemoryLayer.WORKING -> workingDir
            MarkdownMemoryLayer.SHORT_TERM -> shortTermDir
            MarkdownMemoryLayer.LONG_TERM -> longTermDir
        }
        val file = File(targetDir, fileName)
        val markdown = buildString {
            appendLine("---")
            appendLine("id: $id")
            appendLine("type: ${draft.layer.folder}")
            appendLine("persona: ${draft.persona}")
            appendLine("agent_id: ${draft.agentId}")
            appendLine("session_id: ${draft.sessionId}")
            appendLine("source: ${draft.source}")
            appendLine("resource_id: ${draft.resourceId}")
            appendLine("item_id: ${draft.itemId}")
            appendLine("created_at: $createdAt")
            appendLine("updated_at: $createdAt")
            appendLine("ttl_hours: ${draft.ttlHours}")
            appendLine("confidence: ${"%.2f".format(Locale.US, draft.confidence.coerceIn(0.0, 1.0))}")
            appendLine("tags: [${draft.tags.joinToString(", ")}]")
            appendLine("topic: ${draft.topic}")
            appendLine("related_task_ids: [${draft.relatedTaskIds.joinToString(", ")}]")
            appendLine("references: [${draft.references.joinToString(", ")}]")
            appendLine("event_id: ${draft.eventId}")
            appendLine("---")
            appendLine()
            appendLine("## Context")
            appendLine(draft.contextText)
            appendLine()
            appendLine("## Fact")
            appendLine(draft.factText)
            appendLine()
            appendLine("## Decision")
            appendLine(draft.decisionText)
            appendLine()
            appendLine("## Evidence")
            appendLine(draft.evidenceText)
            appendLine()
            appendLine("## Next")
            appendLine(draft.nextText)
        }
        file.writeText(markdown)
        updateTopicIndex(
            layer = draft.layer,
            persona = draft.persona,
            topic = draft.topic,
            tags = draft.tags,
            filePath = file.absolutePath,
            updatedAt = createdAt
        )
        return file.absolutePath
    }

    private fun promoteTopicToLongTermIfNeeded(persona: String, topic: String): String {
        val candidates = shortTermDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.getDefault()) == "md" }
            .orEmpty()
            .mapNotNull { parseMemorySnapshot(it) }
            .filter { it.persona == persona && it.topic == topic }
            .sortedWith(compareByDescending<MemorySnapshot> { it.updatedAt }.thenByDescending { it.confidence })
        if (candidates.size < 3) return ""
        val primary = candidates.first()
        val history = candidates.drop(1).take(6)
        val longTermId = "lt_${sanitize(persona)}_${sanitize(topic)}"
        val file = File(longTermDir, "$longTermId.md")
        val now = formatter.format(Date())
        val markdown = buildString {
            appendLine("---")
            appendLine("id: $longTermId")
            appendLine("type: ${MarkdownMemoryLayer.LONG_TERM.folder}")
            appendLine("persona: $persona")
            appendLine("agent_id: ${primary.agentId}")
            appendLine("session_id: ${primary.sessionId}")
            appendLine("source: summary_merge")
            appendLine("resource_id: ${primary.resourceId}")
            appendLine("item_id: ${primary.itemId}")
            appendLine("created_at: $now")
            appendLine("updated_at: $now")
            appendLine("ttl_hours: 87600")
            appendLine("confidence: ${"%.2f".format(Locale.US, primary.confidence.coerceIn(0.0, 1.0))}")
            appendLine("tags: [${primary.tags.joinToString(", ")}]")
            appendLine("topic: $topic")
            appendLine("related_task_ids: [${candidates.flatMap { it.relatedTaskIds }.distinct().joinToString(", ")}]")
            appendLine("references: [${candidates.flatMap { it.references }.distinct().joinToString(", ")}]")
            appendLine("event_id: ${primary.eventId}")
            appendLine("---")
            appendLine()
            appendLine("## Context")
            appendLine("由短期记忆自动沉淀，按更新时间与置信度合并。")
            appendLine()
            appendLine("## Fact")
            appendLine(primary.fact.ifBlank { "暂无稳定结论" })
            appendLine()
            appendLine("## Decision")
            appendLine(primary.decision.ifBlank { "以当前主结论作为长期策略，保留历史冲突版本。" })
            appendLine()
            appendLine("## Evidence")
            appendLine(primary.evidence.ifBlank { "暂无证据" })
            if (history.isNotEmpty()) {
                appendLine()
                appendLine("历史版本：")
                history.forEach { item ->
                    appendLine("- ${item.updatedAt} | ${item.fact.take(120)}")
                }
            }
            appendLine()
            appendLine("## Next")
            appendLine("持续接收同主题短期记忆，定期刷新主结论。")
        }
        file.writeText(markdown)
        updateTopicIndex(
            layer = MarkdownMemoryLayer.LONG_TERM,
            persona = persona,
            topic = topic,
            tags = primary.tags,
            filePath = file.absolutePath,
            updatedAt = now
        )
        return file.absolutePath
    }

    private fun parseMemorySnapshot(file: File): MemorySnapshot? {
        val content = runCatching { file.readText() }.getOrNull() ?: return null
        val front = parseFrontMatter(content)
        val persona = front["persona"].orEmpty()
        val topic = front["topic"].orEmpty()
        if (persona.isBlank() || topic.isBlank()) return null
        val confidence = front["confidence"]?.toDoubleOrNull() ?: 0.5
        return MemorySnapshot(
            persona = persona,
            agentId = front["agent_id"].orEmpty(),
            sessionId = front["session_id"].orEmpty(),
            resourceId = front["resource_id"].orEmpty(),
            itemId = front["item_id"].orEmpty(),
            topic = topic,
            updatedAt = front["updated_at"].orEmpty(),
            confidence = confidence,
            tags = parseTags(front["tags"]),
            relatedTaskIds = parseTags(front["related_task_ids"]),
            references = parseTags(front["references"]),
            eventId = front["event_id"].orEmpty(),
            fact = readSection(content, "Fact"),
            decision = readSection(content, "Decision"),
            evidence = readSection(content, "Evidence")
        )
    }

    private fun listLayerRecords(dir: File, layer: String): List<MarkdownMemoryRecord> {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.getDefault()) == "md" }
            .orEmpty()
        return files.mapNotNull { file ->
            val content = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
            val front = parseFrontMatter(content)
            val persona = front["persona"].orEmpty()
            val topic = front["topic"].orEmpty()
            val id = front["id"].orEmpty().ifBlank { file.nameWithoutExtension }
            if (persona.isBlank() || topic.isBlank()) return@mapNotNull null
            MarkdownMemoryRecord(
                id = id,
                layer = layer,
                persona = persona,
                agentId = front["agent_id"].orEmpty(),
                sessionId = front["session_id"].orEmpty(),
                resourceId = front["resource_id"].orEmpty(),
                itemId = front["item_id"].orEmpty(),
                topic = topic,
                updatedAt = front["updated_at"].orEmpty(),
                tags = parseTags(front["tags"]),
                relatedTaskIds = parseTags(front["related_task_ids"]),
                references = parseTags(front["references"]),
                eventId = front["event_id"].orEmpty(),
                fact = readSection(content, "Fact"),
                decision = readSection(content, "Decision"),
                evidence = readSection(content, "Evidence"),
                filePath = file.absolutePath
            )
        }
    }

    private fun normalizeDraft(draft: MarkdownMemoryDraft): MarkdownMemoryDraft {
        val normalizedTags = draft.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val normalizedRefs = draft.references.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val normalizedTaskIds = draft.relatedTaskIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val itemId = draft.itemId.ifBlank { "item_${UUID.randomUUID().toString().take(8)}" }
        val resourceId = draft.resourceId.ifBlank {
            if (normalizedTaskIds.isNotEmpty()) "task:${normalizedTaskIds.first()}" else "res:${sanitize(draft.topic)}"
        }
        val eventId = draft.eventId.ifBlank {
            if (normalizedTaskIds.isNotEmpty()) normalizedTaskIds.first() else ""
        }
        return draft.copy(
            tags = normalizedTags,
            references = normalizedRefs,
            relatedTaskIds = normalizedTaskIds,
            itemId = itemId,
            resourceId = resourceId,
            eventId = eventId
        )
    }

    private fun dedupeExisting(draft: MarkdownMemoryDraft): String? {
        ensureDirs()
        val now = formatter.format(Date())
        val targetDir = when (draft.layer) {
            MarkdownMemoryLayer.WORKING -> workingDir
            MarkdownMemoryLayer.SHORT_TERM -> shortTermDir
            MarkdownMemoryLayer.LONG_TERM -> longTermDir
        }
        val candidateFiles = targetDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.getDefault()) == "md" }
            .orEmpty()
        val normalizedFact = normalizeTextForDedup(draft.factText)
        if (normalizedFact.isBlank()) return null
        val matched = candidateFiles.firstOrNull { file ->
            val snapshot = parseMemorySnapshot(file) ?: return@firstOrNull false
            if (snapshot.persona != draft.persona || snapshot.topic != draft.topic) return@firstOrNull false
            normalizeTextForDedup(snapshot.fact) == normalizedFact
        } ?: return null
        val original = runCatching { matched.readText() }.getOrDefault("")
        if (original.isBlank()) return matched.absolutePath
        val merged = mergeForDedup(original, draft, now)
        matched.writeText(merged)
        updateTopicIndex(
            layer = draft.layer,
            persona = draft.persona,
            topic = draft.topic,
            tags = draft.tags,
            filePath = matched.absolutePath,
            updatedAt = now
        )
        return matched.absolutePath
    }

    private fun mergeForDedup(origin: String, draft: MarkdownMemoryDraft, updatedAt: String): String {
        val lines = origin.lines().toMutableList()
        if (lines.firstOrNull()?.trim() != "---") return origin
        var frontEndIndex = -1
        for (i in 1 until lines.size) {
            if (lines[i].trim() == "---") {
                frontEndIndex = i
                break
            }
        }
        if (frontEndIndex <= 0) return origin
        val frontLines = lines.subList(1, frontEndIndex).toMutableList()
        val map = linkedMapOf<String, String>()
        frontLines.forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
            }
        }
        map["updated_at"] = updatedAt
        val mergedTags = (parseTags(map["tags"]) + draft.tags).distinct()
        val mergedTaskIds = (parseTags(map["related_task_ids"]) + draft.relatedTaskIds).distinct()
        val mergedRefs = (parseTags(map["references"]) + draft.references).distinct()
        map["tags"] = "[${mergedTags.joinToString(", ")}]"
        map["related_task_ids"] = "[${mergedTaskIds.joinToString(", ")}]"
        map["references"] = "[${mergedRefs.joinToString(", ")}]"
        if (map["event_id"].isNullOrBlank() && draft.eventId.isNotBlank()) {
            map["event_id"] = draft.eventId
        }
        val rebuiltFront = mutableListOf("---")
        map.forEach { (key, value) ->
            rebuiltFront.add("$key: $value")
        }
        rebuiltFront.add("---")
        val body = lines.drop(frontEndIndex + 1).joinToString("\n")
        return rebuiltFront.joinToString("\n") + "\n" + body
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N}\\u4e00-\\u9fa5]+"), " ")
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun scoreRecord(record: MarkdownMemoryRecord, queryTokens: List<String>): Double {
        var score = 0.0
        val topicText = record.topic.lowercase(Locale.getDefault())
        val factText = record.fact.lowercase(Locale.getDefault())
        val decisionText = record.decision.lowercase(Locale.getDefault())
        val evidenceText = record.evidence.lowercase(Locale.getDefault())
        val metaText = buildString {
            append(record.tags.joinToString(" "))
            append(" ")
            append(record.references.joinToString(" "))
            append(" ")
            append(record.relatedTaskIds.joinToString(" "))
        }.lowercase(Locale.getDefault())
        queryTokens.forEach { token ->
            if (topicText.contains(token)) score += 1.2
            if (metaText.contains(token)) score += 0.8
            if (factText.contains(token)) score += 0.6
            if (decisionText.contains(token)) score += 0.5
            if (evidenceText.contains(token)) score += 0.4
        }
        score += when (record.layer) {
            MarkdownMemoryLayer.SHORT_TERM.folder -> 0.2
            MarkdownMemoryLayer.LONG_TERM.folder -> 0.35
            else -> 0.1
        }
        return score
    }

    private fun normalizeTextForDedup(text: String): String {
        return text.lowercase(Locale.getDefault())
            .replace(Regex("\\s+"), "")
            .replace(Regex("[，。,.!！?？；;：:]"), "")
            .trim()
    }

    private fun parseFrontMatter(content: String): Map<String, String> {
        val lines = content.lines()
        if (lines.firstOrNull()?.trim() != "---") return emptyMap()
        val result = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.trim() == "---") break
            val index = line.indexOf(':')
            if (index <= 0) continue
            val key = line.substring(0, index).trim()
            val value = line.substring(index + 1).trim()
            result[key] = value
        }
        return result
    }

    private fun parseTags(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun readSection(content: String, title: String): String {
        val markers = listOf("Context", "Fact", "Decision", "Evidence", "Next")
            .map { "## $it" }
        val startMarker = "## $title"
        val start = content.indexOf(startMarker)
        if (start < 0) return ""
        val bodyStart = content.indexOf('\n', start).let { if (it < 0) content.length else it + 1 }
        val next = markers
            .map { marker -> content.indexOf(marker, bodyStart) }
            .filter { it >= 0 }
            .minOrNull() ?: content.length
        return content.substring(bodyStart, next).trim()
    }

    private fun updateTopicIndex(
        layer: MarkdownMemoryLayer,
        persona: String,
        topic: String,
        tags: List<String>,
        filePath: String,
        updatedAt: String
    ) {
        ensureDirs()
        val rootObj = if (topicIndexFile.exists()) {
            runCatching { JSONObject(topicIndexFile.readText()) }.getOrDefault(JSONObject())
        } else {
            JSONObject()
        }
        val key = "$persona::$topic"
        val current = rootObj.optJSONObject(key) ?: JSONObject()
        val layerCounts = current.optJSONObject("layer_counts") ?: JSONObject()
        val layerKey = layer.folder
        val previousLayerCount = layerCounts.optInt(layerKey, 0)
        layerCounts.put(layerKey, previousLayerCount + 1)
        val mergedTags = (parseJsonArray(current.optJSONArray("tags")) + tags).distinct()
        val totalWrites = current.optInt("total_writes", 0) + 1
        current.put("persona", persona)
        current.put("topic", topic)
        current.put("updated_at", updatedAt)
        current.put("last_file", filePath)
        current.put("total_writes", totalWrites)
        current.put("tags", JSONArray(mergedTags))
        current.put("layer_counts", layerCounts)
        rootObj.put(key, current)
        topicIndexFile.writeText(rootObj.toString())
    }

    private fun rebuildTopicIndex() {
        if (topicIndexFile.exists()) {
            runCatching { topicIndexFile.delete() }
        }
        val records = listMarkdownRecords()
        records.forEach { record ->
            updateTopicIndex(
                layer = when (record.layer) {
                    MarkdownMemoryLayer.WORKING.folder -> MarkdownMemoryLayer.WORKING
                    MarkdownMemoryLayer.SHORT_TERM.folder -> MarkdownMemoryLayer.SHORT_TERM
                    else -> MarkdownMemoryLayer.LONG_TERM
                },
                persona = record.persona,
                topic = record.topic,
                tags = record.tags,
                filePath = record.filePath,
                updatedAt = record.updatedAt.ifBlank { formatter.format(Date()) }
            )
        }
    }

    private fun parseJsonArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotBlank()) result.add(value)
        }
        return result
    }

    private fun sanitize(raw: String): String {
        return raw.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "_")
            .trim('_')
            .ifBlank { "topic" }
    }

    private fun ensureDirs() {
        if (!root.exists()) root.mkdirs()
        if (!workingDir.exists()) workingDir.mkdirs()
        if (!shortTermDir.exists()) shortTermDir.mkdirs()
        if (!longTermDir.exists()) longTermDir.mkdirs()
        if (!indexDir.exists()) indexDir.mkdirs()
        if (!archiveDir.exists()) archiveDir.mkdirs()
    }

    private data class MemorySnapshot(
        val persona: String,
        val agentId: String,
        val sessionId: String,
        val resourceId: String,
        val itemId: String,
        val topic: String,
        val updatedAt: String,
        val confidence: Double,
        val tags: List<String>,
        val relatedTaskIds: List<String>,
        val references: List<String>,
        val eventId: String,
        val fact: String,
        val decision: String,
        val evidence: String
    )
}
