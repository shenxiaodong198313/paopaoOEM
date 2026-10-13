package com.paopaoime.aikeyboard.ime.knowledge.splitter

abstract class TextSplitter {
    abstract fun splitText(text: String): List<String>
}

class RecursiveCharacterTextSplitter(
    private val chunkSize: Int = 500,
    private val chunkOverlap: Int = 100,
    private val separators: List<String> = listOf("\n\n", "\n", "。", "！", "？", " ", "")
) : TextSplitter() {

    override fun splitText(text: String): List<String> {
        return split(text, separators)
    }

    private fun split(text: String, separators: List<String>): List<String> {
        val finalChunks = mutableListOf<String>()
        var separator = separators.last()
        var newSeparators = emptyList<String>()
        
        for (i in separators.indices) {
            val s = separators[i]
            if (s == "") {
                separator = s
                break
            }
            if (text.contains(s)) {
                separator = s
                newSeparators = separators.subList(i + 1, separators.size)
                break
            }
        }

        val splits = if (separator == "") {
            // Character split
            text.map { it.toString() }
        } else {
            text.split(separator).filter { it.isNotEmpty() }
        }

        val goodSplits = mutableListOf<String>()
        for (s in splits) {
            if (s.length < chunkSize) {
                goodSplits.add(s)
            } else {
                if (newSeparators.isNotEmpty()) {
                    goodSplits.addAll(split(s, newSeparators))
                } else {
                    // Force split if too large and no more separators
                    if (s.length > chunkSize) {
                        goodSplits.addAll(s.chunked(chunkSize))
                    } else {
                        goodSplits.add(s)
                    }
                }
            }
        }
        
        return mergeSplits(goodSplits, separator)
    }

    private fun mergeSplits(splits: List<String>, separator: String): List<String> {
        val docs = mutableListOf<String>()
        var currentDoc = StringBuilder()
        var total = 0
        
        // Simple merge without complex overlap logic for now, just filling up chunks
        for (d in splits) {
            val len = d.length
            if (total + len + separator.length > chunkSize) {
                if (total > 0) {
                    docs.add(currentDoc.toString())
                    // TODO: Implement overlap by keeping last few splits
                    currentDoc = StringBuilder()
                    total = 0
                }
            }
            
            if (total > 0) {
                currentDoc.append(separator)
                total += separator.length
            }
            currentDoc.append(d)
            total += len
        }
        
        if (total > 0) {
            docs.add(currentDoc.toString())
        }
        return docs
    }
}

