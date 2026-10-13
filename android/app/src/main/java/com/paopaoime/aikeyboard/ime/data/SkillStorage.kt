package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import java.io.File
import java.util.Locale

class SkillStorage(private val context: Context) {
    private val rootDir = File(context.filesDir, "skills")

    fun listSkills(): List<SkillEntry> {
        ensureSeed()
        if (!rootDir.exists()) return emptyList()
        return rootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val file = File(dir, "SKILL.md")
                if (!file.exists()) return@mapNotNull null
                parseSkill(file, dir.name)
            }
            ?.sortedBy { it.name.lowercase(Locale.getDefault()) }
            ?: emptyList()
    }

    fun createSkill(name: String, description: String, content: String): SkillEntry {
        val id = slugify(name).ifBlank { "skill_${System.currentTimeMillis()}" }
        val dir = File(rootDir, id)
        dir.mkdirs()
        val file = File(dir, "SKILL.md")
        val body = buildSkillContent(name, description, content)
        file.writeText(body)
        return SkillEntry(id, name, description, body)
    }

    fun updateSkill(id: String, name: String, description: String, content: String) {
        val dir = File(rootDir, id)
        dir.mkdirs()
        val file = File(dir, "SKILL.md")
        val body = buildSkillContent(name, description, content)
        file.writeText(body)
    }

    fun deleteSkill(id: String) {
        val dir = File(rootDir, id)
        if (!dir.exists()) return
        dir.deleteRecursively()
    }

    private fun parseSkill(file: File, id: String): SkillEntry? {
        val lines = file.readLines()
        if (lines.isEmpty()) return null
        var name = id
        var description = ""
        var contentStart = 0
        if (lines.first().trim() == "---") {
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.trim() == "---") {
                    contentStart = i + 1
                    break
                }
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().trim('"')
                    if (key == "name") name = value
                    if (key == "description") description = value
                }
            }
        }
        val content = lines.drop(contentStart).joinToString("\n").trim()
        return SkillEntry(id, name, description, content)
    }

    private fun buildSkillContent(name: String, description: String, content: String): String {
        val header = listOf(
            "---",
            "name: \"$name\"",
            "description: \"$description\"",
            "---",
            ""
        ).joinToString("\n")
        return header + content.trim()
    }

    private fun slugify(input: String): String {
        return input.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    private fun ensureSeed() {
        rootDir.mkdirs()
        val builtinSkills = listOf(
            "auto-wake-home",
            "wechat-moments-publish",
            "wechat-moments-media-publish",
            "xiaohongshu-publish",
            "weibo-publish",
            "douyin-publish",
            "publish-verify",
            "qwen-image-generate-image",
            "qwen-image-generate-video",
            "xingliu-generate-image",
            "xingliu-generate-video"
        )
        listOf("media-generate").forEach { removedSkillId ->
            val removedDir = File(rootDir, removedSkillId)
            if (removedDir.exists()) {
                removedDir.deleteRecursively()
            }
        }
        builtinSkills.forEach { skillId ->
            val targetDir = File(rootDir, skillId)
            val targetFile = File(targetDir, "SKILL.md")
            runCatching {
                val assetPath = "skills/$skillId/SKILL.md"
                val content = context.assets.open(assetPath).bufferedReader().use { it.readText() }
                if (targetFile.exists() && targetFile.readText() == content) return@runCatching
                targetDir.mkdirs()
                targetFile.writeText(content)
            }
        }
    }
}
