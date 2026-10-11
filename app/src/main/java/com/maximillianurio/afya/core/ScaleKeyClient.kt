package com.maximillianurio.afya.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * The vendor provisions one 4-byte obfuscation key per scale MAC on its cloud, and the lookup is a
 * plain GET guarded only by the app-id header. `data` in the reply is the key as a hex string.
 * This is the only network call in the app and it fires only for encrypted frames.
 */
object ScaleKeyClient {
    /** The one host the app ever contacts, named so the network log can say where a call went. */
    const val HOST = "intl.chips-cloud.com"
    private const val BASE_URL = "https://$HOST/v3"
    private const val PATH = "/device/scale/sn?mac="
    private const val APP_ID = "ebcad75de0d42a844d98a755644e30"

    sealed class Result {
        data class Key(val keyHex: String, val body: String) : Result()
        data class Failure(val message: String, val body: String = "") : Result()
    }

    suspend fun fetchKey(mac: String): Result = withContext(Dispatchers.IO) {
        val normalised = mac.replace(":", "").lowercase(Locale.ENGLISH)
        if (normalised.length != 12 || normalised.any { !it.isDigit() && it !in 'a'..'f' }) {
            return@withContext Result.Failure("MAC does not look like 12 hex chars: $mac")
        }
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(BASE_URL + PATH + normalised).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Charset", "UTF-8")
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty("cs-app-id", APP_ID)
                setRequestProperty("appSource", "intel")
            }
            val code = connection.responseCode
            val body = readBody(connection)
            if (code !in 200..299) {
                return@withContext Result.Failure("HTTP $code", body)
            }
            val statusCode = jsonStringField(body, "code") ?: numberField(body, "code")
            val key = jsonStringField(body, "data")
            val message = jsonStringField(body, "msg")
            when {
                key.isNullOrBlank() -> Result.Failure(
                    "No key in reply${statusCode?.let { " (code $it)" } ?: ""}${message?.let { ": $it" } ?: ""}",
                    body,
                )

                else -> Result.Key(key.trim(), body)
            }
        } catch (e: Exception) {
            Result.Failure("${e.javaClass.simpleName}: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection): String {
        val stream = connection.inputStream ?: connection.errorStream ?: return ""
        val encoded = connection.contentEncoding?.lowercase(Locale.ENGLISH) ?: ""
        val source = if (encoded.contains("gzip")) GZIPInputStream(stream) else stream
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        source.use { input ->
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                buffer.write(chunk, 0, read)
            }
        }
        return buffer.toString("UTF-8")
    }

    private fun jsonStringField(body: String, field: String): String? =
        Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)

    private fun numberField(body: String, field: String): String? =
        Regex("\"$field\"\\s*:\\s*([0-9-]+)").find(body)?.groupValues?.get(1)
}
