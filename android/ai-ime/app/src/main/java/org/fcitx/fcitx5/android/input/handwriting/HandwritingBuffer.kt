// SPDX-License-Identifier: LGPL-2.1-or-later
package org.fcitx.fcitx5.android.input.handwriting

/** Ink visibility and candidate lifetime are deliberately independent. */
class HandwritingBuffer {
    val strokes = mutableListOf<List<InkPoint>>()
    var revision = 0L; private set
    var erased = false; private set
    var candidates = emptyList<String>(); private set
    fun begin(): String? {
        // Starting the next character accepts the still-visible first candidate.
        val commit = candidates.firstOrNull().takeIf { erased }
        if (commit != null) clear()
        erased = false
        revision++
        candidates = emptyList()
        return commit
    }
    fun add(points: List<InkPoint>) { if (points.isNotEmpty()) strokes.add(points.toList()); revision++ }
    fun accept(token: Long, words: List<String>): Boolean {
        if (token != revision) return false
        candidates = words.filter(String::isNotBlank).distinct().take(24)
        return true
    }
    fun eraseTrace() { erased = true }
    fun undo() { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex); revision++; candidates = emptyList(); erased = false }
    fun clear() { strokes.clear(); candidates = emptyList(); erased = false; revision++ }
}

/** Scores from separate ML Kit text models are not calibrated against each other. */
object HandwritingCandidates {
    fun merge(groups: List<List<String>>): List<String> {
        val cleaned = groups.map { it.filter(String::isNotBlank).distinct().take(8) }
        val scores = linkedMapOf<String, Double>()
        cleaned.forEach { words -> words.forEachIndexed { rank, word ->
            if (word.isNotBlank()) scores[word] = (scores[word] ?: 0.0) + 1.0 / (rank + 2)
        } }
        // Keep every model's best proposal reachable, even when another script dominates.
        val ranked = scores.keys.sortedByDescending { scores[it] }
        return (ranked.take(1) + cleaned.mapNotNull { it.firstOrNull() } + ranked).distinct().take(24)
    }
}
