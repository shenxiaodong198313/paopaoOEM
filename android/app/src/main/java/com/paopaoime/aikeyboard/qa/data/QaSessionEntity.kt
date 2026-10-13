package com.paopaoime.aikeyboard.qa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qa_sessions")
data class QaSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val packageName: String,
    val appName: String = "",
    val qaContent: String,
    val inputSource: String = "type",
    val timestamp: Long,
    val policyState: String
)
