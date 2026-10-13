package com.paopaoime.aikeyboard.compat.agent.data.entity

data class AgentAsset(
    val id: String,
    val type: Int, // 0: Image, 1: Video
    val path: String, // Local path
    val coverPath: String?, // For video
    val width: Int,
    val height: Int,
    val prompt: String,
    val createTime: Long
)

