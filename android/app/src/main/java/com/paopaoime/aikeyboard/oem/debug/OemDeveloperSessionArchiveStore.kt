package com.paopaoime.aikeyboard.oem.debug

import android.content.Context
import android.util.Log
import com.paopaoime.aikeyboard.sdk.core.OemSessionArchiveEvent
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class OemDeveloperSessionArchiveRecord(
    val eventId: String,
    val sessionId: String,
    val hostApplicationId: String,
    val targetPackageName: String,
    val targetAppName: String,
    val maskedContent: String,
    val inputSource: String,
    val policyState: String,
    val timestamp: Long,
)

data class OemDeveloperSessionArchiveSnapshot(
    val totalCount: Int,
    val records: List<OemDeveloperSessionArchiveRecord>,
)

object OemDeveloperSessionArchiveStore {
    private const val TAG = "OemDeveloperArchive"
    private const val STORE_FILE_NAME = "oem_developer_session_archive.json"
    private const val MAX_RECORDS = 10
    private const val MAX_KNOWN_EVENT_IDS = 128

    private const val JSON_TOTAL_COUNT = "totalCount"
    private const val JSON_KNOWN_EVENT_IDS = "knownEventIds"
    private const val JSON_RECORDS = "records"
    private const val JSON_EVENT_ID = "eventId"
    private const val JSON_SESSION_ID = "sessionId"
    private const val JSON_HOST_APPLICATION_ID = "hostApplicationId"
    private const val JSON_TARGET_PACKAGE_NAME = "targetPackageName"
    private const val JSON_TARGET_APP_NAME = "targetAppName"
    private const val JSON_MASKED_CONTENT = "maskedContent"
    private const val JSON_INPUT_SOURCE = "inputSource"
    private const val JSON_POLICY_STATE = "policyState"
    private const val JSON_TIMESTAMP = "timestamp"

    private val fileLock = Any()

    fun save(context: Context, event: OemSessionArchiveEvent): OemDeveloperSessionArchiveSnapshot {
        synchronized(fileLock) {
            val current = loadState(context)
            val knownEventIds = current.knownEventIds.toMutableList()
            val isNewEvent = event.eventId.isNotBlank() && !knownEventIds.contains(event.eventId)
            if (isNewEvent) {
                knownEventIds.add(0, event.eventId)
            }
            val newRecord = OemDeveloperSessionArchiveRecord(
                eventId = event.eventId,
                sessionId = event.sessionId,
                hostApplicationId = event.hostApplicationId,
                targetPackageName = event.targetPackageName,
                targetAppName = event.targetAppName,
                maskedContent = event.maskedContent,
                inputSource = event.inputSource,
                policyState = event.policyState,
                timestamp = event.timestamp,
            )
            val updatedRecords = buildList {
                add(newRecord)
                current.snapshot.records
                    .asSequence()
                    .filterNot { it.eventId == newRecord.eventId }
                    .take(MAX_RECORDS - 1)
                    .forEach { add(it) }
            }
            val nextState = StoreState(
                snapshot = OemDeveloperSessionArchiveSnapshot(
                    totalCount = if (isNewEvent) current.snapshot.totalCount + 1 else current.snapshot.totalCount,
                    records = updatedRecords,
                ),
                knownEventIds = knownEventIds.take(MAX_KNOWN_EVENT_IDS),
            )
            writeState(context, nextState)
            Log.i(
                TAG,
                "recorded total=${nextState.snapshot.totalCount} records=${nextState.snapshot.records.size} sessionId=${newRecord.sessionId} policy=${newRecord.policyState}"
            )
            return nextState.snapshot
        }
    }

    fun load(context: Context): OemDeveloperSessionArchiveSnapshot? {
        synchronized(fileLock) {
            val snapshot = loadState(context).snapshot
            return if (snapshot.totalCount <= 0 || snapshot.records.isEmpty()) null else snapshot
        }
    }

    fun clear(context: Context) {
        synchronized(fileLock) {
            val file = storeFile(context)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private data class StoreState(
        val snapshot: OemDeveloperSessionArchiveSnapshot,
        val knownEventIds: List<String>,
    )

    private fun storeFile(context: Context): File =
        File(context.applicationContext.filesDir, STORE_FILE_NAME)

    private fun loadState(context: Context): StoreState {
        val file = storeFile(context)
        if (!file.exists()) {
            return StoreState(
                snapshot = OemDeveloperSessionArchiveSnapshot(0, emptyList()),
                knownEventIds = emptyList(),
            )
        }
        return runCatching {
            parseState(file.readText())
        }.getOrElse {
            StoreState(
                snapshot = OemDeveloperSessionArchiveSnapshot(0, emptyList()),
                knownEventIds = emptyList(),
            )
        }
    }

    private fun writeState(context: Context, state: StoreState) {
        val file = storeFile(context)
        file.parentFile?.mkdirs()
        file.writeText(
            JSONObject()
                .put(JSON_TOTAL_COUNT, state.snapshot.totalCount)
                .put(JSON_KNOWN_EVENT_IDS, JSONArray(state.knownEventIds))
                .put(JSON_RECORDS, JSONArray(writeRecords(state.snapshot.records)))
                .toString()
        )
    }

    private fun parseState(raw: String): StoreState {
        val json = JSONObject(raw)
        return StoreState(
            snapshot = OemDeveloperSessionArchiveSnapshot(
                totalCount = json.optInt(JSON_TOTAL_COUNT, 0),
                records = readRecords(json.optJSONArray(JSON_RECORDS)?.toString()),
            ),
            knownEventIds = readKnownEventIds(json.optJSONArray(JSON_KNOWN_EVENT_IDS)),
        )
    }

    private fun readKnownEventIds(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
    }

    private fun readRecords(raw: String?): List<OemDeveloperSessionArchiveRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val jsonArray = JSONArray(raw)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(index) ?: continue
                    val eventId = item.optString(JSON_EVENT_ID)
                    if (eventId.isBlank()) continue
                    add(
                        OemDeveloperSessionArchiveRecord(
                            eventId = eventId,
                            sessionId = item.optString(JSON_SESSION_ID),
                            hostApplicationId = item.optString(JSON_HOST_APPLICATION_ID),
                            targetPackageName = item.optString(JSON_TARGET_PACKAGE_NAME),
                            targetAppName = item.optString(JSON_TARGET_APP_NAME),
                            maskedContent = item.optString(JSON_MASKED_CONTENT),
                            inputSource = item.optString(JSON_INPUT_SOURCE),
                            policyState = item.optString(JSON_POLICY_STATE),
                            timestamp = item.optLong(JSON_TIMESTAMP),
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun writeRecords(records: List<OemDeveloperSessionArchiveRecord>): String {
        val jsonArray = JSONArray()
        records.take(MAX_RECORDS).forEach { record ->
            jsonArray.put(
                JSONObject()
                    .put(JSON_EVENT_ID, record.eventId)
                    .put(JSON_SESSION_ID, record.sessionId)
                    .put(JSON_HOST_APPLICATION_ID, record.hostApplicationId)
                    .put(JSON_TARGET_PACKAGE_NAME, record.targetPackageName)
                    .put(JSON_TARGET_APP_NAME, record.targetAppName)
                    .put(JSON_MASKED_CONTENT, record.maskedContent)
                    .put(JSON_INPUT_SOURCE, record.inputSource)
                    .put(JSON_POLICY_STATE, record.policyState)
                    .put(JSON_TIMESTAMP, record.timestamp)
            )
        }
        return jsonArray.toString()
    }
}
