package com.example.enterprise

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.paopaoime.aikeyboard.sdk.core.OemCapability
import com.paopaoime.aikeyboard.sdk.core.OemLicenseProvider
import com.paopaoime.aikeyboard.sdk.core.OemLicenseRequest
import com.paopaoime.aikeyboard.sdk.core.OemLicenseSnapshot
import com.paopaoime.aikeyboard.sdk.core.OemLicenseStatus
import com.paopaoime.aikeyboard.sdk.core.OemManifestMetaKeys
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

object EnterpriseRemoteLicenseProvider : OemLicenseProvider {
    private const val TAG = "EnterpriseLicense"
    private const val ANALYTICS_PREFS_NAME = "paopao_oem_sdk_analytics"
    private const val LICENSE_PREFS_NAME = "oem_runtime_license"
    private const val KEY_INSTALL_ID = "install_id"

    override fun fetchLicense(context: Context, request: OemLicenseRequest): OemLicenseSnapshot {
        val baseUrl = resolveApiBaseUrl(context)
        if (baseUrl.isBlank()) {
            android.util.Log.w(TAG, "skip license resolve: missing apiBaseUrl")
            return OemLicenseSnapshot.invalid("missing apiBaseUrl")
        }
        val payload = JSONObject().apply {
            val installId = resolveInstallId(context)
            put("customerId", request.customerId)
            put("appKey", request.appKey)
            put("channel", request.channel)
            put("environment", request.environment.name.lowercase())
            put("hostApplicationId", request.hostIdentity.applicationId)
            put("hostCertSha256", request.hostIdentity.certSha256)
            put("hostVersionName", request.hostIdentity.versionName)
            put("hostVersionCode", request.hostIdentity.versionCode)
            put("installId", installId)
            put("deviceFingerprint", buildDeviceFingerprint(context, installId))
            put("deviceBrand", Build.BRAND.orEmpty())
            put("deviceModel", Build.MODEL.orEmpty())
            put("deviceOs", "android")
            put("deviceOsVersion", Build.VERSION.RELEASE.orEmpty())
            put("sdkVersion", request.sdkVersion)
            put("hostUserIdHash", request.hostUserIdHash ?: "")
            put("requestedCapabilities", JSONArray(request.requestedCapabilities.map(OemCapability::name)))
        }
        val requestUrl = "$baseUrl/api/v1/oem/license/resolve"
        android.util.Log.i(
            TAG,
            "resolve start app=${request.hostIdentity.applicationId}"
        )
        val response = postJson(requestUrl, payload)
        val data = response.optJSONObject("data") ?: response
        val statusRaw = data.optString("licenseStatus").trim().uppercase()
        val status = parseStatus(statusRaw)
        val grantedCapabilities = buildSet {
            val items = data.optJSONArray("grantedCapabilities") ?: JSONArray()
            repeat(items.length()) { index ->
                runCatching { OemCapability.valueOf(items.optString(index).trim().uppercase()) }.getOrNull()?.let(::add)
            }
        }
        val message = data.optString("message").ifBlank { status.name.lowercase() }
        val licenseId = data.optString("licenseId").ifBlank { null }
        val expiresAtMillis = data.optLong("expiresAtMillis").takeIf { it > 0L }
        android.util.Log.i(
            TAG,
            "resolve success status=$status"
        )
        return when (status) {
            OemLicenseStatus.ACTIVE -> OemLicenseSnapshot.active(licenseId, message, grantedCapabilities, expiresAtMillis)
            OemLicenseStatus.GRACE -> OemLicenseSnapshot.grace(licenseId, message, grantedCapabilities, expiresAtMillis)
            OemLicenseStatus.MISSING -> OemLicenseSnapshot.missing(message)
            OemLicenseStatus.INVALID -> OemLicenseSnapshot.invalid(message)
            else -> OemLicenseSnapshot(
                status = status,
                licenseId = licenseId,
                message = message,
                expiresAtMillis = expiresAtMillis,
                grantedCapabilities = grantedCapabilities,
            )
        }
    }

    private fun parseStatus(raw: String): OemLicenseStatus {
        return when (raw.trim().uppercase()) {
            "FULL" -> OemLicenseStatus.FULL
            "NOT_OPENED" -> OemLicenseStatus.NOT_OPENED
            else -> runCatching { OemLicenseStatus.valueOf(raw.trim().uppercase()) }.getOrDefault(OemLicenseStatus.INVALID)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveApiBaseUrl(context: Context): String {
        val appContext = context.applicationContext
        val appInfo = appContext.packageManager.getApplicationInfo(appContext.packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString(OemManifestMetaKeys.API_BASE_URL)?.trim()?.trimEnd('/').orEmpty()
    }

    private fun resolveInstallId(context: Context): String {
        val appContext = context.applicationContext
        val analyticsPrefs = appContext.getSharedPreferences(ANALYTICS_PREFS_NAME, Context.MODE_PRIVATE)
        val analyticsInstallId = analyticsPrefs.getString(KEY_INSTALL_ID, null)?.trim().orEmpty()
        if (analyticsInstallId.isNotEmpty()) {
            appContext.getSharedPreferences(LICENSE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_INSTALL_ID, analyticsInstallId)
                .apply()
            return analyticsInstallId
        }
        val licensePrefs = appContext.getSharedPreferences(LICENSE_PREFS_NAME, Context.MODE_PRIVATE)
        val existing = licensePrefs.getString(KEY_INSTALL_ID, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) {
            analyticsPrefs.edit().putString(KEY_INSTALL_ID, existing).apply()
            return existing
        }
        val next = UUID.randomUUID().toString()
        analyticsPrefs.edit().putString(KEY_INSTALL_ID, next).apply()
        licensePrefs.edit().putString(KEY_INSTALL_ID, next).apply()
        return next
    }

    private fun buildDeviceFingerprint(context: Context, installId: String): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            .orEmpty()
        val brand = Build.MANUFACTURER.orEmpty().trim().ifBlank { "unknown-brand" }
        val model = Build.MODEL.orEmpty().trim().ifBlank { "unknown-model" }
        val osVersion = Build.VERSION.RELEASE.orEmpty().trim().ifBlank { Build.VERSION.SDK_INT.toString() }
        val seed = listOf(androidId.ifBlank { installId }, brand, model, osVersion).joinToString("|")
        return sha256Hex(seed)
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun postJson(url: String, payload: JSONObject): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return connection.useAndDisconnect {
            OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            val code = responseCode
            val body = (if (code in 200..299) inputStream else errorStream)?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (body.isBlank()) {
                android.util.Log.e(TAG, "resolve failed: empty body code=$code url=$url")
                throw IllegalStateException("empty license response")
            }
            val json = JSONObject(body)
            if (code !in 200..299 || json.optBoolean("success") == false) {
                val message = json.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "license request failed" }
                android.util.Log.e(TAG, "resolve failed code=$code message=$message body=$body")
                throw IllegalStateException(message)
            }
            json
        }
    }

    private inline fun <T> HttpURLConnection.useAndDisconnect(block: HttpURLConnection.() -> T): T {
        return try {
            block()
        } finally {
            disconnect()
        }
    }
}
