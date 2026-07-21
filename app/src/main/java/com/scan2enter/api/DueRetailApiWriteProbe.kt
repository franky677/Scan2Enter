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
 * Probe diagnostico per verificare quali metodi HTTP sono accettati
 * dalla risorsa /api/Articolo/{id}.
 *
 * Le richieste PUT e PATCH vengono inviate con Content-Length 0:
 * nessun JSON e nessun valore articolo viene trasmesso.
 */
class DueRetailApiWriteProbe(
    private val username: String,
    private val password: String,
    private val baseUrl: String = "http://192.168.1.30:9000",
    private val clientId: String = UUID.randomUUID().toString(),
    private val application: String = "DueMobileRetail",
    private val appVersion: String = "24.8.9"
) {

    companion object {
        private const val TAG = "Scan2EnterProbe"
        private const val BODY_PREVIEW_LIMIT = 1_500
    }

    @Volatile
    private var accessToken: String? = null

    fun probeStockEndpoints(articleId: Long): Result<List<ProbeResult>> =
        runCatching {
            require(articleId > 0L) {
                "articleId deve essere maggiore di zero"
            }

            require(username.isNotBlank()) {
                "Username Due Retail non configurato"
            }

            require(password.isNotBlank()) {
                "Password Due Retail non configurata"
            }

            val articleUrl = "$baseUrl/api/Articolo/$articleId"

            Log.d(TAG, "========================================")
            Log.d(TAG, "INIZIO API METHOD PROBE")
            Log.d(TAG, "articleId=$articleId")
            Log.d(TAG, "url=$articleUrl")
            Log.d(TAG, "clientId=$clientId")
            Log.d(TAG, "PUT/PATCH CON BODY VUOTO - NESSUN JSON INVIATO")
            Log.d(TAG, "========================================")

            val results = listOf(
                executeAuthenticatedRequest(
                    method = "HEAD",
                    urlString = articleUrl,
                    emptyBody = false
                ),
                executeAuthenticatedRequest(
                    method = "PUT",
                    urlString = articleUrl,
                    emptyBody = true
                ),
                executeAuthenticatedRequest(
                    method = "PATCH",
                    urlString = articleUrl,
                    emptyBody = true
                )
            )

            logSummary(results)
            results
        }

    private fun executeAuthenticatedRequest(
        method: String,
        urlString: String,
        emptyBody: Boolean
    ): ProbeResult {
        var token = getOrCreateAccessToken()

        var result = executeRequest(
            method = method,
            urlString = urlString,
            bearerToken = token,
            emptyBody = emptyBody
        )

        if (result.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            Log.w(TAG, "401 ricevuto - rinnovo token")

            accessToken = null
            token = getOrCreateAccessToken()

            result = executeRequest(
                method = method,
                urlString = urlString,
                bearerToken = token,
                emptyBody = emptyBody
            )
        }

        logResult(result)
        return result
    }

    private fun executeRequest(
        method: String,
        urlString: String,
        bearerToken: String,
        emptyBody: Boolean
    ): ProbeResult {
        val connection = URL(urlString).openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false

            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(
                "Authorization",
                "Bearer $bearerToken"
            )

            if (emptyBody) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(0)
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )

                connection.outputStream.use {
                    // Body intenzionalmente vuoto.
                }
            }

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage.orEmpty()
            val body = readResponseBody(
                connection = connection,
                responseCode = responseCode,
                method = method
            )

            ProbeResult(
                method = method,
                url = urlString,
                responseCode = responseCode,
                responseMessage = responseMessage,
                allowHeader = connection.getHeaderField("Allow").orEmpty(),
                contentType = connection.contentType.orEmpty(),
                locationHeader = connection.getHeaderField("Location").orEmpty(),
                body = body.take(BODY_PREVIEW_LIMIT)
            )
        } catch (error: Exception) {
            ProbeResult(
                method = method,
                url = urlString,
                responseCode = -1,
                responseMessage = error.javaClass.simpleName,
                allowHeader = "",
                contentType = "",
                locationHeader = "",
                body = error.message.orEmpty()
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
                responseCode = responseCode,
                method = "POST"
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
        responseCode: Int,
        method: String
    ): String {
        if (method == "HEAD") {
            return ""
        }

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

    private fun logResult(result: ProbeResult) {
        Log.d(TAG, "----------------------------------------")
        Log.d(TAG, "${result.method} ${result.url}")
        Log.d(
            TAG,
            "HTTP=${result.responseCode} ${result.responseMessage}"
        )
        Log.d(
            TAG,
            "Allow=${result.allowHeader.ifBlank { "<assente>" }}"
        )
        Log.d(
            TAG,
            "Content-Type=${result.contentType.ifBlank { "<assente>" }}"
        )
        Log.d(
            TAG,
            "Location=${result.locationHeader.ifBlank { "<assente>" }}"
        )

        Log.d(
            TAG,
            if (result.body.isBlank()) {
                "BODY=<vuoto>"
            } else {
                "BODY=${result.body}"
            }
        )
    }

    private fun logSummary(results: List<ProbeResult>) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "RIEPILOGO API METHOD PROBE")

        results.forEach { result ->
            Log.d(
                TAG,
                "${result.method.padEnd(7)} " +
                        "HTTP=${result.responseCode.toString().padEnd(3)} " +
                        "Allow=${result.allowHeader.ifBlank { "-" }} " +
                        result.url
            )
        }

        Log.d(TAG, "FINE API METHOD PROBE")
        Log.d(TAG, "========================================")
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(
            value,
            StandardCharsets.UTF_8.name()
        )
}

data class ProbeResult(
    val method: String,
    val url: String,
    val responseCode: Int,
    val responseMessage: String,
    val allowHeader: String,
    val contentType: String,
    val locationHeader: String,
    val body: String
)