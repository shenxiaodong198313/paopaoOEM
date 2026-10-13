package com.paopaoime.aikeyboard.ime.ui.knowledgebase.model

data class KbDocumentUi(
    val id: String,
    val directoryId: String,
    val fileName: String,
    val mimeType: String,
    val updatedAtMillis: Long,
    val parseStatus: Int,
    val parseProgress: Int,
    val parsedTextPath: String?,
    val parseError: String?,
    val vectorStatus: Int = 0,
    val vectorError: String? = null,
    val vectorUpdatedAtMillis: Long = 0,
)


