package com.paopaoime.aikeyboard.ime.ui.knowledgebase.model

data class KbDirectoryUi(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletable: Boolean,
    val vectorStoreSize: Long = 0,
)


