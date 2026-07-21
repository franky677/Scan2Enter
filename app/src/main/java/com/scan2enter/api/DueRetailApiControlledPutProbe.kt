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
 * Probe TEMPORANEO e protetto per verificare l'aggiornamento articolo via PUT.
 *
 * Sicurezze:
 * - consente la scrittura esclusivamente sull'articolo di test PIPPO;
 * - articleId obbligatorio: 163545;
 * - codice restituito dal GET obbligatorio: PIPPO;
 * - modifica soltanto ScortaMinima;
 * - esegue un GET di verifica dopo il PUT.
 *
 * Rimuovere o disabilitare dopo il test.
 */
class DueRetailApiControlledPutProbe(
    private val username: String,
    private val password: String,
    private val baseUrl: String = "http://192.168.1.30:9000",
    private val clientId: String = UUID.randomUUID().toString(),
    private val application: String = "DueMobileRetail",
    private val appVersion: String = "24.8.9"
) {

    companion object {
        private const val TAG = "Scan2EnterPutProbe"

        private const val TEST_ARTICLE_ID = 163545L
        private const val TEST_ARTICLE_CODE = "PIPPO"
        private const val TEST_MINIMUM_STOCK = 1.0

        private const val LOG_CHUNK_SIZE = 3_000
    }

    @Volatile
    private var accessToken: String? = null

    fun testMinimumStockUpdate(articleId: Long): Result<Unit> =
        runCatching {
            require(articleId == TEST_ARTICLE_ID) {
                "SCRITTURA BLOCCATA: articleId=$articleId, " +
                        "consentito solo $TEST_ARTICLE_ID"
            }

            val url = "$baseUrl/api/Articolo/$articleId"

            Log.d(TAG, "========================================")
            Log.d(TAG, "INIZIO CONTROLLED PUT PROBE")
            Log.d(TAG, "ARTICOLO CONSENTITO: $TEST_ARTICLE_CODE")
            Log.d(TAG, "articleId=$articleId")
            Log.d(TAG, "url=$url")
            Log.d(TAG, "clientId=$clientId")
            Log.d(TAG, "TARGET ScortaMinima=$TEST_MINIMUM_STOCK")
            Log.d(TAG, "========================================")

            var token = getOrCreateAccessToken()

            val beforeResponse = executeGetWithRefresh(
                urlString = url,
                initialToken = token
            )
            token = beforeResponse.token

            requireSuccess(
                operation = "GET PRIMA DEL PUT",
                response = beforeResponse.response
            )

            val beforeRoot = JSONObject(beforeResponse.response.body)

            checkApiResult(
                operation = "GET PRIMA DEL PUT",
                root = beforeRoot
            )

            val articleDto = beforeRoot.optJSONObject("data")
                ?: error("GET senza oggetto data")

            val articleCode = articleDto.optString("Codice").trim()

            require(articleCode.equals(TEST_ARTICLE_CODE, ignoreCase = false)) {
                "SCRITTURA BLOCCATA: codice ricevuto='$articleCode', " +
                        "atteso='$TEST_ARTICLE_CODE'"
            }

            val oldMinimumStock =
                articleDto.optDouble("ScortaMinima", Double.NaN)

            require(!oldMinimumStock.isNaN()) {
                "Campo ScortaMinima assente o non numerico"
            }

            Log.d(TAG, "VERIFICA IDENTITA ARTICOLO OK")
            Log.d(TAG, "Codice=$articleCode")
            Log.d(TAG, "ScortaMinima PRIMA=$oldMinimumStock")
            Log.d(TAG, "ScortaMassima INVARIATA=${articleDto.opt("ScortaMassima")}")
            Log.d(TAG, "LottoRiordino INVARIATO=${articleDto.opt("LottoRiordino")}")

            /*
             * JSONObject modifica esclusivamente questa proprietà.
             * Tutte le altre proprietà del DTO restano quelle ricevute dal GET.
             */
            articleDto.put("ScortaMinima", TEST_MINIMUM_STOCK)

            Log.d(TAG, "INVIO PUT CON DTO COMPLETO")
            Log.d(TAG, "UNICO CAMPO MODIFICATO: ScortaMinima")
            logBodyInChunks(
                label = "PUT_BODY",
                body = articleDto.toString()
            )

            var putResponse = executePut(
                urlString = url,
                bearerToken = token,
                jsonBody = articleDto.toString()
            )

            if (putResponse.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                Log.w(TAG, "PUT: 401 ricevuto - rinnovo token")
                accessToken = null
                token = getOrCreateAccessToken()

                putResponse = executePut(
                    urlString = url,
                    bearerToken = token,
                    jsonBody = articleDto.toString()
                )
            }

            Log.d(
                TAG,
                "PUT HTTP=${putResponse.responseCode} " +
                        putResponse.responseMessage
            )
            Log.d(
                TAG,
                "PUT Content-Type=" +
                        putResponse.contentType.ifBlank { "<assente>" }
            )
            Log.d(
                TAG,
                "PUT BODY=" +
                        putResponse.body.ifBlank { "<vuoto>" }
            )

            requireSuccess(
                operation = "PUT",
                response = putResponse
            )

            if (putResponse.body.isNotBlank()) {
                val putRoot = runCatching {
                    JSONObject(putResponse.body)
                }.getOrNull()

                if (putRoot != null) {
                    checkApiResult(
                        operation = "PUT",
                        root = putRoot
                    )
                }
            }

            val afterResponse = executeGetWithRefresh(
                urlString = url,
                initialToken = token
            )

            requireSuccess(
                operation = "GET DOPO IL PUT",
                response = afterResponse.response
            )

            val afterRoot = JSONObject(afterResponse.response.body)

            checkApiResult(
                operation = "GET DOPO IL PUT",
                root = afterRoot
            )

            val afterDto = afterRoot.optJSONObject("data")
                ?: error("GET di verifica senza oggetto data")

            val verifiedCode = afterDto.optString("Codice").trim()
            val newMinimumStock =
                afterDto.optDouble("ScortaMinima", Double.NaN)

            require(verifiedCode == TEST_ARTICLE_CODE) {
                "Verifica fallita: codice dopo PUT='$verifiedCode'"
            }

            Log.d(TAG, "----------------------------------------")
            Log.d(TAG, "VERIFICA FINALE")
            Log.d(TAG, "Codice=$verifiedCode")
            Log.d(TAG, "ScortaMinima PRIMA=$oldMinimumStock")
            Log.d(TAG, "ScortaMinima DOPO=$newMinimumStock")
            Log.d(TAG, "ScortaMassima DOPO=${afterDto.opt("ScortaMassima")}")
            Log.d(TAG, "LottoRiordino DOPO=${afterDto.opt("LottoRiordino")}")

            check(newMinimumStock == TEST_MINIMUM_STOCK) {
                "PUT accettato ma verifica fallita: " +
                        "ScortaMinima=$newMinimumStock, " +
                        "atteso=$TEST_MINIMUM_STOCK"
            }

            Log.d(TAG, "ESITO=SUCCESSO")
            Log.d(
                TAG,
                "ScortaMinima aggiornata correttamente " +
                        "$oldMinimumStock -> $newMinimumStock"
            )
            Log.d(TAG, "========================================")
            Log.d(TAG, "FINE CONTROLLED PUT PROBE")
            Log.d(TAG, "========================================")

            Unit

        }.onFailure { error ->
            Log.e(TAG, "ESITO=FALLIMENTO", error)
            Log.d(TAG, "========================================")
            Log.d(TAG, "FINE CONTROLLED PUT PROBE CON ERRORE")
            Log.d(TAG, "========================================")
        }

    private fun executeGetWithRefresh(
        urlString: String,
        initialToken: String
    ): AuthenticatedResponse {
        var token = initialToken
        var response = executeGet(urlString, token)

        if (response.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            Log.w(TAG, "GET: 401 ricevuto - rinnovo token")
            accessToken = null
            token = getOrCreateAccessToken()
            response = executeGet(urlString, token)
        }

        return AuthenticatedResponse(
            token = token,
            response = response
        )
    }

    private fun executeGet(
        urlString: String,
        bearerToken: String
    ): HttpResponse =
        executeJsonRequest(
            urlString = urlString,
            method = "GET",
            bearerToken = bearerToken,
            jsonBody = null
        )

    private fun executePut(
        urlString: String,
        bearerToken: String,
        jsonBody: String
    ): HttpResponse =
        executeJsonRequest(
            urlString = urlString,
            method = "PUT",
            bearerToken = bearerToken,
            jsonBody = jsonBody
        )

    private fun executeJsonRequest(
        urlString: String,
        method: String,
        bearerToken: String,
        jsonBody: String?
    ): HttpResponse {
        val connection = URL(urlString).openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false

            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(
                "Authorization",
                "Bearer $bearerToken"
            )

            if (jsonBody != null) {
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )

                connection.outputStream.use { output ->
                    output.write(
                        jsonBody.toByteArray(StandardCharsets.UTF_8)
                    )
                }
            }

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

    private fun requireSuccess(
        operation: String,
        response: HttpResponse
    ) {
        check(response.responseCode in 200..299) {
            "$operation HTTP ${response.responseCode} " +
                    "${response.responseMessage}: ${response.body}"
        }
    }

    private fun checkApiResult(
        operation: String,
        root: JSONObject
    ) {
        if (!root.has("result_code")) {
            return
        }

        val resultCode = root.optInt("result_code", Int.MIN_VALUE)

        check(resultCode == 0) {
            "$operation result_code=$resultCode, " +
                    "error_message=${root.optString("error_message")}, " +
                    "body=$root"
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

            check(responseCode in 200..299) {
                "Autenticazione HTTP $responseCode: $responseBody"
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

    private fun logBodyInChunks(
        label: String,
        body: String
    ) {
        if (body.isBlank()) {
            Log.d(TAG, "$label=<vuoto>")
            return
        }

        val totalChunks =
            (body.length + LOG_CHUNK_SIZE - 1) / LOG_CHUNK_SIZE

        body.chunked(LOG_CHUNK_SIZE)
            .forEachIndexed { index, chunk ->
                Log.d(
                    TAG,
                    "${label}_CHUNK ${index + 1}/$totalChunks = $chunk"
                )
            }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(
            value,
            StandardCharsets.UTF_8.name()
        )

    private data class AuthenticatedResponse(
        val token: String,
        val response: HttpResponse
    )

    private data class HttpResponse(
        val responseCode: Int,
        val responseMessage: String,
        val contentType: String,
        val body: String
    )
}