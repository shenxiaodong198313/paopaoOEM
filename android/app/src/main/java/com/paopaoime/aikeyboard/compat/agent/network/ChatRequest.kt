package com.paopaoime.aikeyboard.compat.agent.network

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    val temperature: Double? = null,
    @SerializedName("top_p") val topP: Double? = null,
    @SerializedName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerializedName("reasoning_effort") val reasoningEffort: String? = null,
    val thinking: ThinkingConfig? = null,
    @SerializedName("enable_thinking") val enableThinking: Boolean? = null,
    val stream: Boolean = false
)

data class ThinkingConfig(
    val type: String
)

data class Message(
    val role: String,
    val content: Any // Can be String or List<ContentItem>
)

data class ContentItem(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String
)

data class ChatResponse(
    val choices: List<Choice>,
    val model: String? = null,
    val usage: ChatUsage? = null
)

data class Choice(
    val message: MessageResponse
)

data class MessageResponse(
    val content: String
)

data class ChatUsage(
    @SerializedName(value = "prompt_tokens", alternate = ["input_tokens"]) val promptTokens: Int? = null,
    @SerializedName(value = "completion_tokens", alternate = ["output_tokens"]) val completionTokens: Int? = null,
    @SerializedName(value = "total_tokens", alternate = ["all_tokens"]) val totalTokens: Int? = null
)

