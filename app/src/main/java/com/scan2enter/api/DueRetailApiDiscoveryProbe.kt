package com.scan2enter.api

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Probe diagnostico di sola lettura per cercare documentazione
 * e metadati esposti dal server Due Retail.
 *
 * Esegue esclusivamente richieste GET.
 * Non modifica alcun dato.
 */
class DueRetailApiDiscoveryProbe(
    private val baseUrl: String = "http://192.168.1.30:9000"
) {

    companion object {
        private const val TAG = "Scan2EnterDiscovery"
        private const val BODY_PREVIEW_LIMIT = 800
    }

    fun discover(): Result<List<DiscoveryResult>> =
        runCatching {
            val normalizedBaseUrl = baseUrl.trimEnd('/')

            val paths = listOf(
                "/",
                "/api",
                "/swagger",
                "/swagger/",
                "/swagger/index.html",
                "/swagger/v1/swagger.json",
                "/swagger/v2/swagger.json",
                "/swagger/docs/v1",
                "/swagger/docs/v2",
                "/openapi.json",
                "/api/help",
                "/api/help/index",
                "/api/help/routes",
                "/api/\$metadata",
                "/.well-known/openapi.json"
            )

            Log.d(TAG, "========================================")
            Log.d(TAG, "INIZIO API DISCOVERY PROBE")
            Log.d(TAG, "baseUrl=$normalizedBaseUrl")
            Log.d(TAG, "SOLO RICHIESTE GET - NESSUNA SCRITTURA")
            Log.d(TAG, "========================================")

            val results = paths.map { path ->
                executeGet("$normalizedBaseUrl$path")
                    .also(::logResult)
            }

            logSummary(results)

            results
        }

    private fun executeGet(urlString: String): DiscoveryResult {
        val connection = URL(urlString).openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false

            connection.setRequestProperty(
                "Accept",
                "application/json, text/html, text/plain, */*"
            )

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage.orEmpty()

            val body = readResponseBody(
                connection = connection,
                responseCode = responseCode
            )

            DiscoveryResult(
                url = urlString,
                responseCode = responseCode,
                responseMessage = responseMessage,
                contentType = connection.contentType.orEmpty(),
                serverHeader = connection.getHeaderField("Server").orEmpty(),
                locationHeader = connection.getHeaderField("Location").orEmpty(),
                allowHeader = connection.getHeaderField("Allow").orEmpty(),
                bodyPreview = body
                    .replace("\r", " ")
                    .replace("\n", " ")
                    .take(BODY_PREVIEW_LIMIT)
            )
        } catch (error: Exception) {
            DiscoveryResult(
                url = urlString,
                responseCode = -1,
                responseMessage = error.javaClass.simpleName,
                contentType = "",
                serverHeader = "",
                locationHeader = "",
                allowHeader = "",
                bodyPreview = error.message.orEmpty()
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponseBody(
        connection: HttpURLConnection,
        responseCode: Int
    ): String {
        val stream: InputStream? =
            if (responseCode in 200..399) {
                connection.inputStream
            } else {
                connection.errorStream
            }

        if (stream == null) {
            return ""
        }

        return BufferedReader(
            InputStreamReader(
                stream,
                StandardCharsets.UTF_8
            )
        ).use { reader ->
            reader.readText()
        }
    }

    private fun logResult(result: DiscoveryResult) {
        Log.d(TAG, "----------------------------------------")
        Log.d(TAG, "GET ${result.url}")
        Log.d(
            TAG,
            "HTTP=${result.responseCode} ${result.responseMessage}"
        )
        Log.d(
            TAG,
            "Content-Type=${result.contentType.ifBlank { "<assente>" }}"
        )
        Log.d(
            TAG,
            "Server=${result.serverHeader.ifBlank { "<assente>" }}"
        )
        Log.d(
            TAG,
            "Location=${result.locationHeader.ifBlank { "<assente>" }}"
        )
        Log.d(
            TAG,
            "Allow=${result.allowHeader.ifBlank { "<assente>" }}"
        )

        Log.d(
            TAG,
            if (result.bodyPreview.isBlank()) {
                "BODY=<vuoto>"
            } else {
                "BODY=${result.bodyPreview}"
            }
        )
    }

    private fun logSummary(results: List<DiscoveryResult>) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "RIEPILOGO API DISCOVERY PROBE")

        results.forEach { result ->
            Log.d(
                TAG,
                "HTTP=${result.responseCode.toString().padEnd(3)} " +
                        "TYPE=${result.contentType.ifBlank { "-" }} " +
                        result.url
            )
        }

        Log.d(TAG, "FINE API DISCOVERY PROBE")
        Log.d(TAG, "========================================")
    }
}

data class DiscoveryResult(
    val url: String,
    val responseCode: Int,
    val responseMessage: String,
    val contentType: String,
    val serverHeader: String,
    val locationHeader: String,
    val allowHeader: String,
    val bodyPreview: String
)