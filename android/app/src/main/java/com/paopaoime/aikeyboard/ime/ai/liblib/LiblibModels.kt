package com.paopaoime.aikeyboard.ime.ai.liblib

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class LiblibEnvelope<T>(
    val code: Int = -1,
    val msg: String = "",
    val data: T? = null,
)

@Serializable
data class GenerateTaskIdData(
    val generateUuid: String? = null,
    @SerialName("generateUid")
    val generateUid: String? = null,
)

@Serializable
data class UploadSignatureRequest(
    val name: String,
    val extension: String,
)

@Serializable
data class UploadSignatureData(
    val key: String,
    val policy: String,
    val postUrl: String,
    val xOssDate: String,
    val xOssExpires: Int,
    val xOssSignature: String,
    val xOssCredential: String,
    val xOssSignatureVersion: String,
)

@Serializable
data class GenerateRequest(
    val templateUuid: String,
    val generateParams: JsonObject,
)

@Serializable
data class GenerateResponseData(
    val generateUuid: String,
)

@Serializable
data class StatusRequest(
    val generateUuid: String? = null,
    @SerialName("generateUid")
    val generateUid: String? = null,
)

@Serializable
data class StatusImage(
    val imageUrl: String = "",
    val seed: Long = 0,
    val auditStatus: Int = 0,
)

@Serializable
data class StatusVideo(
    val videoUrl: String = "",
    val coverPath: String = "",
    val auditStatus: Int = 0,
)

@Serializable
data class StatusData(
    val generateUuid: String = "",
    val generateStatus: Int = 0,
    val percentCompleted: Float = 0f,
    val generateMsg: String? = null,
    val pointsCost: Int = 0,
    val accountBalance: Int = 0,
    val images: List<StatusImage> = emptyList(),
    val videos: List<StatusVideo> = emptyList(),
)

@Serializable
data class ControlNetParams(
    val controlType: String,
    val controlImage: String,
)

@Serializable
data class ImageSize(
    val width: Int,
    val height: Int,
)

