package com.paopaoime.aikeyboard.ime.data

enum class OperationTaskType {
    KNOWLEDGE_LEARN,
    CONTENT_GENERATE,
    CONTENT_PUBLISH,
    OPERATION_REPORT
}

enum class OperationTaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}

enum class OperationChannel(val code: String, val displayName: String) {
    WECHAT("wechat", "微信朋友圈"),
    XIAOHONGSHU("xiaohongshu", "小红书"),
    DOUYIN("douyin", "抖音"),
    WEIBO("weibo", "微博")
}

enum class OperationContentType(val displayName: String) {
    COPYWRITING("文案"),
    ARTICLE("文章"),
    IMAGE("图片"),
    VIDEO("视频")
}

data class NpcOperationPlan(
    val taskId: String,
    val npcId: String,
    val channel: OperationChannel,
    val contentType: OperationContentType,
    val knowledgeTitle: String?,
    val knowledgeSnippet: String?,
    val memorySummary: String?,
    val memoryReferences: List<String>
)
