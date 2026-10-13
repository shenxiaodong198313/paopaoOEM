package com.paopaoime.aikeyboard.ime.knowledge.embedding

import java.io.InputStream
import java.io.BufferedReader
import java.io.InputStreamReader

class SimpleBertTokenizer(vocabStream: InputStream) {
    private val vocab = HashMap<String, Int>()
    private val idsToTokens = HashMap<Int, String>()
    private val unkToken = "[UNK]"
    private val clsToken = "[CLS]"
    private val sepToken = "[SEP]"
    
    init {
        BufferedReader(InputStreamReader(vocabStream)).use { reader ->
            reader.readLines().forEachIndexed { index, line ->
                val token = line.trim()
                if (token.isNotEmpty()) {
                    vocab[token] = index
                    idsToTokens[index] = token
                }
            }
        }
    }

    fun tokenize(text: String, maxLength: Int = 512): LongArray {
        val tokens = mutableListOf<String>()
        tokens.add(clsToken)
        
        for (char in text) {
            val s = char.toString().lowercase()
            if (vocab.containsKey(s)) {
                tokens.add(s)
            } else {
                tokens.add(unkToken)
            }
            if (tokens.size >= maxLength - 1) break
        }
        tokens.add(sepToken)
        
        return tokens.map { vocab[it]?.toLong() ?: vocab[unkToken]?.toLong() ?: 0L }.toLongArray()
    }
}

