package com.paopaoime.aikeyboard.sdk.core

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object OemAnalyticsReporter {
    private const val TAG = "OemAnalytics"
    private const val PREFS_NAME = "paopao_oem_sdk_analytics"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_ACTIVATION_REPORTED = "activation_reported"
    private const val KEY_LAST_HEARTBEAT_DAY = "last_heartbeat_day"
    private const val SIGNATURE_VERSION = "v1"
    private const val REPORT_SOURCE = "sdk_core"
    private const val ANALYTICS_ENDPOINT_TOKEN = "OBUbABJVYEoaVEUeQlc/EQ4fCAIqSw5MXw=="
    private const val ANALYTICS_ENDPOINT_KEY = "PaopaoOem#2026"

    private val defaultAnalyticsBaseUrl: String by lazy { decodeAnalyticsEndpoint() }

    private fun decodeAnalyticsEndpoint(): String {
        val data = android.util.Base64.decode(ANALYTICS_ENDPOINT_TOKEN, android.util.Base64.DEFAULT)
        val key = ANALYTICS_ENDPOINT_KEY.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArray(data.size)
        for (i in data.indices) {
            out[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return String(out, StandardCharsets.UTF_8)
    }
    private const val CONNECT_TIMEOUT_MS = 6_000
    private const val READ_TIMEOUT_MS = 8_000

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "paopao-oem-analytics").apply { isDaemon = true }
    }

    fun onInitialized(application: Application, config: OemSdkConfig, status: OemSdkStatus) {
        val baseUrl = config.analyticsBaseUrl
            ?.trim()
            ?.trimEnd('/')
            ?.ifBlank { null }
            ?: defaultAnalyticsBaseUrl

        val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val installId = prefs.getString(KEY_INSTALL_ID, null)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_INSTALL_ID, it).apply()
        }
        val todayKey = currentUtcDayKey()

        if (!prefs.getBoolean(KEY_ACTIVATION_REPORTED, false)) {
            enqueueReport(
                application = application,
                baseUrl = baseUrl,
                config = config,
                status = status,
                installId = installId,
                eventType = "activate",
                dayKey = todayKey,
            ) {
                prefs.edit()
                    .putBoolean(KEY_ACTIVATION_REPORTED, true)
                    .putString(KEY_LAST_HEARTBEAT_DAY, todayKey)
                    .apply()
            }
            return
        }

        if (prefs.getString(KEY_LAST_HEARTBEAT_DAY, "") == todayKey) return

        enqueueReport(
            application = application,
            baseUrl = baseUrl,
            config = config,
            status = status,
            installId = installId,
            eventType = "heartbeat",
            dayKey = todayKey,
        ) {
            prefs.edit().putString(KEY_LAST_HEARTBEAT_DAY, todayKey).apply()
        }
    }

    private fun enqueueReport(
        application: Application,
        baseUrl: String,
        config: OemSdkConfig,
        status: OemSdkStatus,
        installId: String,
        eventType: String,
        dayKey: String,
        onSuccess: () -> Unit,
    ) {
        executor.execute {
            runCatching {
                val signatureTimestamp = System.currentTimeMillis()
                val payload = buildPayload(
                    application = application,
                    config = config,
                    status = status,
                    installId = installId,
                    eventType = eventType,
                    dayKey = dayKey,
                    signatureTimestamp = signatureTimestamp,
                )
                val signature = hmacSha256Hex(
                    secret = config.appKey,
                    content = buildCanonicalString(payload, signatureTimestamp),
                )
                postReport(
                    baseUrl = baseUrl,
                    payload = payload,
                    signature = signature,
                    signatureTimestamp = signatureTimestamp,
                )
            }.onSuccess { ok ->
                if (ok) {
                    Log.i(TAG, "report success eventType=$eventType installId=$installId")
                    onSuccess()
                } else {
                    Log.e(TAG, "report failed eventType=$eventType installId=$installId")
                }
            }.onFailure { error ->
                Log.e(TAG, "report exception eventType=$eventType installId=$installId message=${error.message}", error)
            }
        }
    }

    private fun buildPayload(
        application: Application,
        config: OemSdkConfig,
        status: OemSdkStatus,
        installId: String,
        eventType: String,
        dayKey: String,
        signatureTimestamp: Long,
    ): JSONObject {
        val device = buildDeviceSnapshot(application, installId)
        val eventIdSeed = if (eventType == "heartbeat") {
            "$eventType|${config.customerId}|${config.channel}|${status.hostIdentity.applicationId}|$installId|$dayKey"
        } else {
            "$eventType|${config.customerId}|${config.channel}|${status.hostIdentity.applicationId}|$installId"
        }
        val eventId = sha256Hex(eventIdSeed)
        val occurredAt = java.time.Instant.ofEpochMilli(signatureTimestamp).toString()

        return JSONObject()
            .put("eventType", eventType)
            .put("eventId", eventId)
            .put("customerId", config.customerId)
            .put("appKey", config.appKey)
            .put("channel", config.channel)
            .put("environment", status.environment.name.lowercase(Locale.ROOT))
            .put("occurredAt", occurredAt)
            .put("installId", installId)
            .put("sdkVersion", status.sdkVersion)
            .put("licenseStatus", status.license.status.name)
            .put("licenseId", status.license.licenseId ?: "")
            .put("requestedCapabilities", capabilitiesToJsonArray(status.requestedCapabilities))
            .put("availableCapabilities", capabilitiesToJsonArray(status.availableCapabilities))
            .put("hostIdentity", JSONObject()
                .put("applicationId", status.hostIdentity.applicationId)
                .put("certSha256", status.hostIdentity.certSha256)
                .put("versionName", status.hostIdentity.versionName)
                .put("versionCode", status.hostIdentity.versionCode)
            )
            .put("device", JSONObject()
                .put("deviceFingerprint", device.deviceFingerprint)
                .put("brand", device.brand)
                .put("model", device.model)
                .put("os", device.os)
                .put("osVersion", device.osVersion)
            )
            .put("reportSource", REPORT_SOURCE)
    }

    private fun capabilitiesToJsonArray(capabilities: Set<OemCapability>): JSONArray {
        val array = JSONArray()
        capabilities.map { it.name }.sorted().forEach(array::put)
        return array
    }

    private fun buildCanonicalString(payload: JSONObject, signatureTimestamp: Long): String {
        val hostIdentity = payload.optJSONObject("hostIdentity") ?: JSONObject()
        val device = payload.optJSONObject("device") ?: JSONObject()
        val requestedCapabilities = jsonArrayToSortedCsv(payload.optJSONArray("requestedCapabilities"))
        val availableCapabilities = jsonArrayToSortedCsv(payload.optJSONArray("availableCapabilities"))
        return listOf(
            SIGNATURE_VERSION,
            signatureTimestamp.toString(),
            payload.optString("eventId"),
            payload.optString("eventType"),
            payload.optString("customerId"),
            payload.optString("appKey"),
            payload.optString("channel"),
            payload.optString("environment"),
            payload.optString("occurredAt"),
            payload.optString("installId"),
            payload.optString("sdkVersion"),
            payload.optString("licenseStatus"),
            payload.optString("licenseId"),
            hostIdentity.optString("applicationId"),
            hostIdentity.optString("certSha256"),
            hostIdentity.optString("versionName"),
            hostIdentity.optLong("versionCode").toString(),
            device.optString("deviceFingerprint"),
            device.optString("brand"),
            device.optString("model"),
            device.optString("os"),
            device.optString("osVersion"),
            requestedCapabilities,
            availableCapabilities,
            payload.optString("reportSource"),
        ).joinToString("\n")
    }

    private fun postReport(
        baseUrl: String,
        payload: JSONObject,
        signature: String,
        signatureTimestamp: Long,
    ): Boolean {
        val connection = (URL("$baseUrl/api/v1/oem/enterprise-analytics/report").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Paopao-Signature-Version", SIGNATURE_VERSION)
            setRequestProperty("X-Paopao-Signature", signature)
            setRequestProperty("X-Paopao-Timestamp", signatureTimestamp.toString())
        }
        return runCatching {
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            val responseCode = connection.responseCode
            val body = readResponseBody(connection, responseCode in 200..299)
            if (responseCode !in 200..299) {
                Log.e(TAG, "report http failed code=$responseCode body=$body")
                return false
            }
            if (body.isBlank()) {
                return true
            }
            val success = runCatching { JSONObject(body).optBoolean("success", false) }.getOrDefault(false)
            if (!success) {
                Log.e(TAG, "report body failed body=$body")
            }
            success
        }.getOrDefault(false)
            .also {
                connection.disconnect()
            }
    }

    private fun readResponseBody(connection: HttpURLConnection, useInputStream: Boolean): String {
        val stream = if (useInputStream) connection.inputStream else connection.errorStream ?: return ""
        return stream.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
    }

    private fun jsonArrayToSortedCsv(array: JSONArray?): String {
        if (array == null) return ""
        val values = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) {
                values += value
            }
        }
        return values.distinct().sorted().joinToString(",")
    }

    private fun currentUtcDayKey(): String {
        val instant = java.time.Instant.now().atOffset(java.time.ZoneOffset.UTC)
        return String.format(Locale.US, "%04d%02d%02d", instant.year, instant.monthValue, instant.dayOfMonth)
    }

    private data class DeviceSnapshot(
        val deviceFingerprint: String,
        val brand: String,
        val model: String,
        val os: String,
        val osVersion: String,
    )

    private fun buildDeviceSnapshot(context: Context, installId: String): DeviceSnapshot {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            .orEmpty()
        val brand = Build.MANUFACTURER.orEmpty().trim().ifBlank { "unknown-brand" }
        val model = Build.MODEL.orEmpty().trim().ifBlank { "unknown-model" }
        val os = "Android"
        val osVersion = Build.VERSION.RELEASE.orEmpty().trim().ifBlank { Build.VERSION.SDK_INT.toString() }
        val fingerprintSeed = listOf(androidId.ifBlank { installId }, brand, model, osVersion).joinToString("|")
        return DeviceSnapshot(
            deviceFingerprint = sha256Hex(fingerprintSeed),
            brand = brand,
            model = model,
            os = os,
            osVersion = osVersion,
        )
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256Hex(secret: String, content: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(content.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
