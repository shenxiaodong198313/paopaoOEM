package com.paopaoime.aikeyboard.midscene.agent

import java.util.LinkedHashSet
import java.util.Locale

data class MidscenePromptBootstrapTarget(
    val appName: String,
    val packageName: String,
    val matchedSegment: String,
    val matchedAlias: String
)

object MidscenePromptBootstrapResolver {
    private val launchHints = listOf(
        "打开",
        "启动",
        "进入",
        "前往",
        "去",
        "切到",
        "切换到",
        "跳转到",
        "回到",
        "返回",
        "来到",
        "唤起"
    )
    private val numberedStepRegex = Regex("""^\s*(?:\d+[.、\)]|[-*])\s*(.+)$""")
    private val sentenceSplitRegex = Regex("""[\r\n。！？!?,，；;]+""")
    private val knownPackageAliases = mapOf(
        "com.tencent.mm" to listOf("wechat", "weixin"),
        "com.xingin.xhs" to listOf("rednote", "red note", "xiaohongshu", "xhs"),
        "com.ss.android.ugc.aweme" to listOf("douyin"),
        "com.eg.android.AlipayGphone" to listOf("alipay"),
        "com.tencent.mobileqq" to listOf("qq"),
        "tv.danmaku.bili" to listOf("bilibili", "b站", "bili"),
        "com.taobao.taobao" to listOf("taobao"),
        "com.jingdong.app.mall" to listOf("jd"),
        "com.sankuai.meituan" to listOf("meituan")
    )

    fun resolve(
        prompt: String,
        installedApps: List<MidsceneInstalledAppInfo>,
        selfPackageName: String? = null
    ): MidscenePromptBootstrapTarget? {
        val candidates = buildCandidates(installedApps, selfPackageName)
        if (candidates.isEmpty()) return null
        val segments = extractCandidateSegments(prompt)
        if (segments.isEmpty()) return null
        val bestMatch = segments
            .flatMapIndexed { segmentIndex, segment ->
                findMatches(segment, candidates).map { match ->
                    match.copy(segmentIndex = segmentIndex)
                }
            }
            .sortedWith(
                compareBy<AppMatch> { it.segmentIndex }
                    .thenByDescending { it.hasLaunchHint }
                    .thenBy { it.matchIndex }
                    .thenByDescending { it.alias.length }
            )
            .firstOrNull()
            ?: return null
        return MidscenePromptBootstrapTarget(
            appName = bestMatch.app.appName,
            packageName = bestMatch.app.packageName,
            matchedSegment = bestMatch.segment,
            matchedAlias = bestMatch.alias
        )
    }

    private fun buildCandidates(
        installedApps: List<MidsceneInstalledAppInfo>,
        selfPackageName: String?
    ): List<AppCandidate> {
        return installedApps.asSequence()
            .mapNotNull { info ->
                val appName = info.appName.trim()
                val packageName = info.packageName.trim()
                if (appName.isBlank() || packageName.isBlank()) {
                    null
                } else if (packageName == selfPackageName?.trim()) {
                    null
                } else {
                    val aliases = LinkedHashSet<String>().apply {
                        if (appName.length >= 2) {
                            add(appName)
                        }
                        knownPackageAliases[packageName]?.forEach { alias ->
                            if (alias.trim().length >= 2) {
                                add(alias.trim())
                            }
                        }
                    }
                    if (aliases.isEmpty()) {
                        null
                    } else {
                        AppCandidate(
                            appName = appName,
                            packageName = packageName,
                            aliases = aliases.toList()
                        )
                    }
                }
            }
            .distinctBy { it.packageName.lowercase(Locale.ROOT) }
            .toList()
    }

    private fun extractCandidateSegments(prompt: String): List<String> {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isBlank()) return emptyList()
        val ordered = LinkedHashSet<String>()
        val lines = trimmedPrompt.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        lines.forEach { line ->
            val numbered = numberedStepRegex.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (numbered.isNotBlank()) {
                ordered.add(numbered)
            }
        }
        lines.firstOrNull()?.let { ordered.add(it) }
        sentenceSplitRegex.split(trimmedPrompt)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.let { ordered.add(it) }
        lines.take(3)
            .joinToString(" ")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { ordered.add(it) }
        ordered.add(trimmedPrompt)
        return ordered.toList()
    }

    private fun findMatches(
        segment: String,
        candidates: List<AppCandidate>
    ): List<AppMatch> {
        val normalizedSegment = normalizeText(segment)
        if (normalizedSegment.isBlank()) return emptyList()
        return candidates.mapNotNull { app ->
            val aliasMatch = app.aliases.mapNotNull { alias ->
                val normalizedAlias = normalizeText(alias)
                val index = normalizedSegment.indexOf(normalizedAlias)
                if (normalizedAlias.isBlank() || index < 0) {
                    null
                } else {
                    alias to index
                }
            }.sortedWith(
                compareBy<Pair<String, Int>> { it.second }
                    .thenByDescending { it.first.length }
            ).firstOrNull() ?: return@mapNotNull null
            val matchIndex = aliasMatch.second
            AppMatch(
                app = app,
                segment = segment,
                segmentIndex = Int.MAX_VALUE,
                alias = aliasMatch.first,
                matchIndex = matchIndex,
                hasLaunchHint = hasLaunchHint(normalizedSegment, matchIndex)
            )
        }
    }

    private fun hasLaunchHint(normalizedSegment: String, matchIndex: Int): Boolean {
        return launchHints.any { hint ->
            val normalizedHint = normalizeText(hint)
            val hintIndex = normalizedSegment.indexOf(normalizedHint)
            hintIndex >= 0 && hintIndex <= matchIndex + normalizedHint.length
        }
    }

    private fun normalizeText(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), "")
    }

    private data class AppCandidate(
        val appName: String,
        val packageName: String,
        val aliases: List<String>
    )

    private data class AppMatch(
        val app: AppCandidate,
        val segment: String,
        val segmentIndex: Int,
        val alias: String,
        val matchIndex: Int,
        val hasLaunchHint: Boolean
    )
}
