/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package org.fcitx.fcitx5.android.input.status

import org.fcitx.fcitx5.android.core.RawConfig

/** The JNI addon response contains configuration under `cfg`, alongside its description. */
object PinyinDictionarySchemaProtocol {
    private const val MAX_RESPONSE_ATTEMPTS = 3

    /** Returns null only after this request's exact schema has been read back successfully. */
    suspend fun select(
        schemaId: String,
        requestId: String,
        exchange: suspend (RawConfig) -> RawConfig
    ): String? {
        require(requestId.isNotEmpty())
        repeat(MAX_RESPONSE_ATTEMPTS) {
            val response = exchange(RawConfig(arrayOf(
                RawConfig("Action", "select"), RawConfig("Schema", schemaId), RawConfig("RequestId", requestId)
            ))).findByName("cfg") ?: return "INVALID_SCHEMA_RESPONSE"
            // Another caller may prepare a schema between our separate JNI set/get calls.
            // Reissue only for an overwritten response; native same-schema selection is a no-op.
            if (response.findByName("RequestId")?.value != requestId) return@repeat
            val errorCode = response.findByName("ErrorCode")?.value.orEmpty()
            if (PinyinDictionaryRouting.confirmsSchemaResponse(
                    schemaId, response.findByName("Schema")?.value, errorCode,
                    requestId, response.findByName("RequestId")?.value
                )
            ) return null
            return errorCode.ifEmpty { "SCHEMA_NOT_CONFIRMED" }
        }
        return "STALE_SCHEMA_RESPONSE"
    }
}
