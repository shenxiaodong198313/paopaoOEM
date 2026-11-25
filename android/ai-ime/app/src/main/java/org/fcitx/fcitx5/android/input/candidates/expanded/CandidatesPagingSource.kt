/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.candidates.expanded

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.input.t9.T9CandidateText

data class ExpandedWord(val index: Int, val word: CandidateWord, val revision: Long)

internal fun isSingleCharacter(text: String): Boolean =
    text.isNotEmpty() && Character.isLetter(text.codePointAt(0)) &&
        Character.charCount(text.codePointAt(0)) == text.length

class CandidatesPagingSource(
    private val fcitx: FcitxConnection,
    val total: Int,
    val isT9Mode: Boolean = false,
    private val singleOnly: Boolean = false,
    private val revision: Long = 0,
    private val chineseOnly: Boolean = false
) : PagingSource<Int, ExpandedWord>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExpandedWord> {
        var start = params.key ?: 0
        val size = params.loadSize
        // Native indices include phrases even when the single-character page hides them.
        // An empty filtered batch must not hide matches on the following native page.
        while (true) {
            currentCoroutineContext().ensureActive()
            val raw = fcitx.runOnReady { getCandidates(start, size) }
            val words = raw.mapIndexedNotNull { i, item ->
                val word = when {
                    chineseOnly -> T9CandidateText.chineseDisplay(item) ?: return@mapIndexedNotNull null
                    isT9Mode -> T9CandidateText.clean(item)
                    else -> item
                }
                if (singleOnly && !isSingleCharacter(word.text)) null else ExpandedWord(start + i, word, revision)
            }
            start += raw.size
            val hasMore = raw.size == size && (total < 0 || start < total)
            if (words.isNotEmpty() || !hasMore) {
                return LoadResult.Page(words, null, start.takeIf { hasMore })
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ExpandedWord>): Int? = null
}
