package com.paopaoime.aikeyboard.ime.ai.liblib

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class LiblibClient(
    rawAccessKey: String,
    rawSecretKey: String,
    private val baseUrl: String = "https://openapi.liblibai.cloud",
) {
    private val accessKey: String = sanitizeCredential(rawAccessKey)
    private val secretKey: String = sanitizeCredential(rawSecretKey)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    private fun makeNonce(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    private fun sanitizeCredential(raw: String): String {
        return raw.filterNot { it == '\r' || it == '\n' || it == '\t' || it == ' ' }.trim()
    }

    private fun makeSignature(uriPath: String, timestampMs: String, nonce: String): String {
        val content = "$uriPath&$timestampMs&$nonce"
        val mac = Mac.getInstance("HmacSHA1")
        val secret = SecretKeySpec(secretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA1")
        mac.init(secret)
        val digest = mac.doFinal(content.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun signedUrl(uriPath: String): String {
        val ts = System.currentTimeMillis().toString()
        val nonce = makeNonce()
        val signature = makeSignature(uriPath, ts, nonce)
        val query = "AccessKey=$accessKey&Signature=$signature&Timestamp=$ts&SignatureNonce=$nonce"
        return "$baseUrl$uriPath?$query"
    }

    suspend fun <TReq, TResp> postJson(
        uriPath: String,
        requestBody: TReq,
        requestSerializer: KSerializer<TReq>,
        responseSerializer: KSerializer<TResp>,
    ): TResp = withContext(Dispatchers.IO) {
        val url = URL(signedUrl(uriPath))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val bodyStr = json.encodeToString(requestSerializer, requestBody)
        BufferedOutputStream(conn.outputStream).use { os ->
            os.write(bodyStr.toByteArray(StandardCharsets.UTF_8))
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw RuntimeException("HTTP $code: $text")
        }
        json.decodeFromString(responseSerializer, text)
    }

    suspend fun uploadToOss(
        postUrl: String,
        fields: Map<String, String>,
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        val boundary = "----IME_X_${UUID.randomUUID()}"
        val url = URL(postUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        fun writeLine(out: BufferedOutputStream, s: String) {
            out.write(s.toByteArray(StandardCharsets.UTF_8))
        }

        BufferedOutputStream(conn.outputStream).use { out ->
            for ((k, v) in fields) {
                writeLine(out, "--$boundary\r\n")
                writeLine(out, "Content-Disposition: form-data; name=\"$k\"\r\n\r\n")
                writeLine(out, v)
                writeLine(out, "\r\n")
            }

            writeLine(out, "--$boundary\r\n")
            writeLine(out, "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
            writeLine(out, "Content-Type: $mimeType\r\n\r\n")
            out.write(fileBytes)
            writeLine(out, "\r\n")
            writeLine(out, "--$boundary--\r\n")
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            throw RuntimeException("涓婁紶澶辫触 HTTP $code: $err")
        }
    }

    suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 60_000
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            throw RuntimeException("涓嬭浇澶辫触 HTTP $code")
        }
        BufferedInputStream(conn.inputStream).use { input ->
            val buffer = ByteArray(16 * 1024)
            val bos = ByteArrayOutputStream()
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                bos.write(buffer, 0, read)
            }
            bos.toByteArray()
        }
    }
}

