package com.scan2enter.api

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class DueRetailApiClient(
    private val username: String,
    private val password: String,
    private val baseUrl: String = "http://192.168.1.30:9000",
    private val clientId: String = UUID.randomUUID().toString(),
    private val application: String = "DueMobileRetail",
    private val appVersion: String = "24.8.9"
) {

    companion object {
        private const val TAG = "Scan2Enter"
    }

    init {
        Log.d(TAG, "========================================")
        Log.d(TAG, "DueRetailApiClient CREATED")
        Log.d(TAG, "clientId=$clientId")
        Log.d(TAG, "baseUrl=$baseUrl")
        Log.d(TAG, "========================================")
    }

    @Volatile
    private var accessToken: String? = null

    fun getProductByBarcode(barcode: String): Result<DueRetailProductDetail> =
        runCatching {
            require(barcode.isNotBlank()) {
                "Il barcode non può essere vuoto"
            }

            require(username.isNotBlank()) {
                "Username Due Retail non configurato"
            }

            val normalizedBarcode = barcode.trim()

            val encodedBarcode = URLEncoder.encode(
                normalizedBarcode,
                StandardCharsets.UTF_8.name()
            )

            val searchJson = authenticatedGetJson(
                "$baseUrl/api/Articolo?barcode=$encodedBarcode"
            )

            val summary = parseSearchResponse(searchJson)
                ?: error(
                    "Nessun articolo trovato per il barcode $normalizedBarcode"
                )

            val detailJson = authenticatedGetJson(
                "$baseUrl/api/Articolo/${summary.id}"
            )

            parseDetailResponse(
                jsonText = detailJson,
                requestedBarcode = normalizedBarcode
            )
        }

    /**
     * Aggiorna esclusivamente i parametri di riordino dell'articolo.
     *
     * Procedura:
     * 1. GET del DTO completo;
     * 2. modifica dei soli campi richiesti;
     * 3. PUT dello stesso DTO;
     * 4. GET di verifica.
     *
     * Un valore null lascia invariato il campo corrispondente.
     */
    fun updateStockSettings(
        articleId: Long,
        minimumStock: Double? = null,
        maximumStock: Double? = null,
        reorderLot: Double? = null
    ): Result<DueRetailStockSettings> =
        runCatching {
            require(articleId > 0L) {
                "articleId non valido: $articleId"
            }

            require(
                minimumStock != null ||
                        maximumStock != null ||
                        reorderLot != null
            ) {
                "Nessun valore da aggiornare"
            }

            minimumStock?.let {
                require(it >= 0.0) {
                    "Scorta minima non valida: $it"
                }
            }

            maximumStock?.let {
                require(it >= -1.0) {
                    "Scorta massima non valida: $it"
                }
            }

            reorderLot?.let {
                require(it >= 0.0) {
                    "Lotto riordino non valido: $it"
                }
            }

            val url = "$baseUrl/api/Articolo/$articleId"

            Log.d(TAG, "========================================")
            Log.d(TAG, "STOCK SETTINGS UPDATE START")
            Log.d(TAG, "articleId=$articleId")
            Log.d(TAG, "minimumStock=$minimumStock")
            Log.d(TAG, "maximumStock=$maximumStock")
            Log.d(TAG, "reorderLot=$reorderLot")
            Log.d(TAG, "========================================")

            val beforeJson = authenticatedGetJson(url)
            val beforeRoot = parseSuccessfulRoot(beforeJson)
            val articleDto = beforeRoot.getJSONObject("data")

            val articleCode = articleDto.optString("Codice")

            val previous = DueRetailStockSettings(
                articleId = articleDto.getLong("Id"),
                articleCode = articleCode,
                minimumStock =
                    articleDto.optDouble("ScortaMinima", -1.0),
                maximumStock =
                    articleDto.optDouble("ScortaMassima", -1.0),
                reorderLot =
                    articleDto.optDouble("LottoRiordino", -1.0)
            )

            minimumStock?.let {
                articleDto.put("ScortaMinima", it)
            }

            maximumStock?.let {
                articleDto.put("ScortaMassima", it)
            }

            reorderLot?.let {
                articleDto.put("LottoRiordino", it)
            }

            authenticatedPutJson(
                urlString = url,
                jsonBody = articleDto.toString()
            )

            val afterJson = authenticatedGetJson(url)
            val afterRoot = parseSuccessfulRoot(afterJson)
            val verifiedDto = afterRoot.getJSONObject("data")

            val updated = DueRetailStockSettings(
                articleId = verifiedDto.getLong("Id"),
                articleCode = verifiedDto.optString("Codice"),
                minimumStock =
                    verifiedDto.optDouble("ScortaMinima", -1.0),
                maximumStock =
                    verifiedDto.optDouble("ScortaMassima", -1.0),
                reorderLot =
                    verifiedDto.optDouble("LottoRiordino", -1.0)
            )

            minimumStock?.let {
                check(updated.minimumStock == it) {
                    "Verifica ScortaMinima fallita: " +
                            "atteso=$it, ricevuto=${updated.minimumStock}"
                }
            }

            maximumStock?.let {
                check(updated.maximumStock == it) {
                    "Verifica ScortaMassima fallita: " +
                            "atteso=$it, ricevuto=${updated.maximumStock}"
                }
            }

            reorderLot?.let {
                check(updated.reorderLot == it) {
                    "Verifica LottoRiordino fallita: " +
                            "atteso=$it, ricevuto=${updated.reorderLot}"
                }
            }

            Log.d(TAG, "STOCK SETTINGS UPDATE OK")
            Log.d(
                TAG,
                "article=${updated.articleCode} " +
                        "minimum ${previous.minimumStock} -> " +
                        "${updated.minimumStock}, " +
                        "maximum ${previous.maximumStock} -> " +
                        "${updated.maximumStock}, " +
                        "lot ${previous.reorderLot} -> " +
                        "${updated.reorderLot}"
            )
            Log.d(TAG, "========================================")

            updated
        }

    private fun authenticatedGetJson(urlString: String): String {
        Log.d(TAG, "GET REQUEST")
        Log.d(TAG, "URL = $urlString")

        var token = getOrCreateAccessToken()

        var response = executeGet(
            urlString = urlString,
            bearerToken = token
        )

        Log.d(TAG, "GET RESPONSE code=${response.code}")

        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            Log.w(TAG, "401 ricevuto - richiedo un nuovo token")

            accessToken = null
            token = getOrCreateAccessToken()

            response = executeGet(
                urlString = urlString,
                bearerToken = token
            )

            Log.d(TAG, "GET RETRY RESPONSE code=${response.code}")
        }

        requireHttpSuccess(
            operation = "GET",
            response = response
        )

        return response.body
    }

    private fun authenticatedPutJson(
        urlString: String,
        jsonBody: String
    ): String {
        Log.d(TAG, "PUT REQUEST")
        Log.d(TAG, "URL = $urlString")

        var token = getOrCreateAccessToken()

        var response = executePut(
            urlString = urlString,
            bearerToken = token,
            jsonBody = jsonBody
        )

        Log.d(TAG, "PUT RESPONSE code=${response.code}")

        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            Log.w(TAG, "PUT 401 ricevuto - richiedo un nuovo token")

            accessToken = null
            token = getOrCreateAccessToken()

            response = executePut(
                urlString = urlString,
                bearerToken = token,
                jsonBody = jsonBody
            )

            Log.d(TAG, "PUT RETRY RESPONSE code=${response.code}")
        }

        requireHttpSuccess(
            operation = "PUT",
            response = response
        )

        if (response.body.isNotBlank()) {
            parseSuccessfulRoot(response.body)
        }

        return response.body
    }

    private fun parseSuccessfulRoot(jsonText: String): JSONObject {
        val root = JSONObject(jsonText)

        check(root.optInt("result_code", -1) == 0) {
            root.optString(
                "error_message",
                "Errore API sconosciuto"
            )
        }

        return root
    }

    private fun requireHttpSuccess(
        operation: String,
        response: HttpResponse
    ) {
        if (response.code !in 200..299) {
            Log.e(TAG, "$operation ERROR")
            Log.e(TAG, "HTTP=${response.code}")
            Log.e(TAG, response.body)

            error(
                "$operation HTTP ${response.code}: ${response.body}"
            )
        }
    }

    @Synchronized
    private fun getOrCreateAccessToken(): String {
        accessToken?.takeIf { it.isNotBlank() }?.let {
            Log.d(TAG, "TOKEN riutilizzato")
            return it
        }

        Log.d(TAG, "TOKEN non presente - richiesta autenticazione")

        val token = requestAccessToken()

        Log.d(TAG, "TOKEN ottenuto con successo")

        accessToken = token
        return token
    }

    private fun requestAccessToken(): String {
        Log.d(TAG, "TOKEN REQUEST")
        Log.d(TAG, "clientId=$clientId")

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
                connection,
                responseCode
            )

            Log.d(TAG, "TOKEN RESPONSE")
            Log.d(TAG, "HTTP=$responseCode")
            Log.d(TAG, responseBody)

            if (responseCode !in 200..299) {
                Log.e(TAG, "AUTENTICAZIONE FALLITA")
                error(
                    "Autenticazione HTTP $responseCode: " +
                            responseBody
                )
            }

            val root = JSONObject(responseBody)

            val token = sequenceOf(
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

            val resolvedToken = token ?: error(
                "Token non trovato nella risposta di autenticazione: " +
                        responseBody
            )

            Log.d(TAG, "ACCESS TOKEN ricevuto")

            return resolvedToken
        } finally {
            connection.disconnect()
        }
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
        val connection =
            URL(urlString).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 20_000
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )
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
                        jsonBody.toByteArray(
                            StandardCharsets.UTF_8
                        )
                    )
                }
            }

            val responseCode = connection.responseCode
            val responseBody = readResponseBody(
                connection,
                responseCode
            )

            return HttpResponse(
                code = responseCode,
                body = responseBody
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
            if (responseCode in 200..299) {
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

    private fun urlEncode(value: String): String =
        URLEncoder.encode(
            value,
            StandardCharsets.UTF_8.name()
        )

    private fun parseSearchResponse(
        jsonText: String
    ): DueRetailProductSummary? {
        val root = parseSuccessfulRoot(jsonText)

        val data = root.optJSONArray("data") ?: JSONArray()

        if (data.length() == 0) {
            return null
        }

        val item = data.getJSONObject(0)

        return DueRetailProductSummary(
            id = item.getLong("Id"),
            articleCode = item.optString("Codice"),
            description = item.optString("Descrizione"),
            vatRate = item.optDouble("AliquotaIva", 0.0),
            year = item.optString("StagioneAnno"),
            season = item.optString("StagionePeriodicita"),
            minimumStock =
                item.optDouble("ScortaMinima", -1.0),
            maximumStock =
                item.optDouble("ScortaMassima", -1.0),
            reorderLot =
                item.optDouble("LottoRiordino", -1.0)
        )
    }

    private fun parseDetailResponse(
        jsonText: String,
        requestedBarcode: String
    ): DueRetailProductDetail {
        val root = parseSuccessfulRoot(jsonText)
        val item = root.getJSONObject("data")

        val barcode = findBarcode(
            item.optJSONArray("DTOBarcodes"),
            requestedBarcode
        )

        val publicPriceEntry = findPublicPrice(
            item.optJSONArray("DTOPrezziVenditaRetail")
        )

        val stockEntry = firstObject(
            item.optJSONArray(
                "DTOGiacenze_SommatePerMagazzinoProdotto"
            )
        )

        return DueRetailProductDetail(
            id = item.getLong("Id"),
            articleCode = item.optString("Codice"),
            description = item.optString("Descrizione"),
            barcode = barcode,
            vatRate =
                item.optDouble("AliquotaIva", 0.0),
            year =
                item.optString("StagioneAnno"),
            season =
                item.optString("StagionePeriodicita"),
            publicPrice =
                publicPriceEntry
                    ?.optDoubleOrNull("Prezzo"),
            taxablePrice =
                publicPriceEntry
                    ?.optDoubleOrNull("Imponibile"),
            stock =
                stockEntry
                    ?.optDoubleOrNull("QtaGiacenza"),
            availableStock =
                stockEntry
                    ?.optDoubleOrNull("QtaDisponibile"),
            minimumStock =
                item.optDouble("ScortaMinima", -1.0),
            maximumStock =
                item.optDouble("ScortaMassima", -1.0),
            reorderLot =
                item.optDouble("LottoRiordino", -1.0),
            rawJson = jsonText
        )
    }

    private fun findBarcode(
        barcodes: JSONArray?,
        requestedBarcode: String
    ): String {
        if (barcodes == null) {
            return requestedBarcode
        }

        for (index in 0 until barcodes.length()) {
            val item =
                barcodes.optJSONObject(index) ?: continue

            val barcode = item.optString("Barcode")

            if (barcode == requestedBarcode) {
                return barcode
            }
        }

        return barcodes.optJSONObject(0)
            ?.optString("Barcode")
            ?.takeIf { it.isNotBlank() }
            ?: requestedBarcode
    }

    private fun findPublicPrice(
        prices: JSONArray?
    ): JSONObject? {
        if (prices == null) {
            return null
        }

        for (index in 0 until prices.length()) {
            val item =
                prices.optJSONObject(index) ?: continue

            val list = item.optJSONObject("Listino")
            val description =
                list?.optString("Descrizione").orEmpty()

            if (
                description.equals(
                    "3-AL PUBBLICO",
                    ignoreCase = true
                )
            ) {
                return item
            }
        }

        return null
    }

    private fun firstObject(
        array: JSONArray?
    ): JSONObject? =
        if (array != null && array.length() > 0) {
            array.optJSONObject(0)
        } else {
            null
        }

    private fun JSONObject.optDoubleOrNull(
        name: String
    ): Double? =
        if (has(name) && !isNull(name)) {
            optDouble(name)
        } else {
            null
        }

    private data class HttpResponse(
        val code: Int,
        val body: String
    )
}

data class DueRetailStockSettings(
    val articleId: Long,
    val articleCode: String,
    val minimumStock: Double,
    val maximumStock: Double,
    val reorderLot: Double
)