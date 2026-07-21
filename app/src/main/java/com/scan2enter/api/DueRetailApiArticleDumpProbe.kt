package com.scan2enter.api

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Probe diagnostico di sola lettura.
 *
 * Esegue esclusivamente:
 * GET /api/Articolo/{id}
 *
 * Non invia PUT, PATCH, POST o DELETE sull'articolo.
 */
class DueRetailApiArticleDumpProbe(
    private val username: String,
    private val password: String,
    private val baseUrl: String = "http://192.168.1.30:9000",
    private val clientId: String = UUID.randomUUID().toString(),
    private val application: String = "DueMobileRetail",
    private val appVersion: String = "24.8.9"
) {

    companion object {
        private const val TAG = "Scan2EnterArticleDump"
        private const val LOG_CHUNK_SIZE = 3_000
    }

    @Volatile
    private var accessToken: String? = null

    fun dumpArticle(articleId: Long): Result<String> =
        runCatching {
            require(articleId > 0L) {
                "articleId deve essere maggiore di zero"
            }

            val url = "$baseUrl/api/Articolo/$articleId"

            Log.d(TAG, "========================================")
            Log.d(TAG, "INIZIO ARTICLE JSON DUMP")
            Log.d(TAG, "SOLO GET - NESSUNA SCRITTURA")
            Log.d(TAG, "articleId=$articleId")
            Log.d(TAG, "url=$url")
            Log.d(TAG, "clientId=$clientId")
            Log.d(TAG, "========================================")

            var token = getOrCreateAccessToken()
            var response = executeGet(url, token)

            if (response.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                Log.w(TAG, "401 ricevuto - rinnovo token")
                accessToken = null
                token = getOrCreateAccessToken()
                response = executeGet(url, token)
            }

            Log.d(
                TAG,
                "HTTP=${response.responseCode} ${response.responseMessage}"
            )
            Log.d(
                TAG,
                "Content-Type=${response.contentType.ifBlank { "<assente>" }}"
            )

            if (response.responseCode !in 200..299) {
                Log.e(
                    TAG,
                    "GET ARTICOLO FALLITO BODY=${response.body}"
                )

                error(
                    "GET articolo HTTP ${response.responseCode}: " +
                            response.body
                )
            }

            logBodyInChunks(response.body)

            Log.d(TAG, "========================================")
            Log.d(TAG, "FINE ARTICLE JSON DUMP")
            Log.d(TAG, "========================================")

            response.body
        }

    private fun executeGet(
        urlString: String,
        bearerToken: String
    ): HttpResponse {
        val connection = URL(urlString).openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false

            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(
                "Authorization",
                "Bearer $bearerToken"
            )

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage.orEmpty()
            val body = readResponseBody(
                connection = connection,
                responseCode = responseCode
            )

            HttpResponse(
                responseCode = responseCode,
                responseMessage = responseMessage,
                contentType = connection.contentType.orEmpty(),
                body = body
            )
        } finally {
            connection.disconnect()
        }
    }

    @Synchronized
    private fun getOrCreateAccessToken(): String {
        accessToken
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val token = requestAccessToken()
        accessToken = token
        return token
    }

    private fun requestAccessToken(): String {
        val formBody = listOf(
            "username" to username,
            "password" to password,
            "clientId" to clientId,
            "appName" to application,
            "appVersion" to appVersion,
            "grant_type" to "password"
        ).joinToString("&") { (name, value) ->
            "${urlEncode(name)}=${urlEncode(value)}"
        }

        val connection =
            URL("$baseUrl/Token").openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
            )
            connection.setRequestProperty("Accept", "application/json")

            connection.outputStream.use { output ->
                output.write(
                    formBody.toByteArray(StandardCharsets.UTF_8)
                )
            }

            val responseCode = connection.responseCode
            val responseBody = readResponseBody(
                connection = connection,
                responseCode = responseCode
            )

            if (responseCode !in 200..299) {
                error(
                    "Autenticazione HTTP $responseCode: $responseBody"
                )
            }

            val root = JSONObject(responseBody)

            return sequenceOf(
                root.optString("access_token"),
                root.optString("AccessToken"),
                root.optString("token"),
                root.optString("Token"),
                root.optJSONObject("data")
                    ?.optString("access_token")
                    .orEmpty(),
                root.optJSONObject("data")
                    ?.optString("AccessToken")
                    .orEmpty(),
                root.optJSONObject("data")
                    ?.optString("token")
                    .orEmpty(),
                root.optJSONObject("data")
                    ?.optString("Token")
                    .orEmpty()
            ).firstOrNull { it.isNotBlank() }
                ?: error(
                    "Token non trovato nella risposta di autenticazione"
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

    private fun logBodyInChunks(body: String) {
        if (body.isBlank()) {
            Log.d(TAG, "JSON=<vuoto>")
            return
        }

        val totalChunks =
            (body.length + LOG_CHUNK_SIZE - 1) / LOG_CHUNK_SIZE

        body.chunked(LOG_CHUNK_SIZE)
            .forEachIndexed { index, chunk ->
                Log.d(
                    TAG,
                    "JSON_CHUNK ${index + 1}/$totalChunks = $chunk"
                )
            }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(
            value,
            StandardCharsets.UTF_8.name()
        )

    private data class HttpResponse(
        val responseCode: Int,
        val responseMessage: String,
        val contentType: String,
        val body: String
    )
}