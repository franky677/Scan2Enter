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


            val encodedBarcode = URLEncoder.encode(
                barcode.trim(),
                StandardCharsets.UTF_8.name()
            )

            val searchJson = authenticatedGetJson(
                "$baseUrl/api/Articolo?barcode=$encodedBarcode"
            )

            val summary = parseSearchResponse(searchJson)
                ?: error("Nessun articolo trovato per il barcode $barcode")

            val detailJson = authenticatedGetJson(
                "$baseUrl/api/Articolo/${summary.id}"
            )

            parseDetailResponse(
                jsonText = detailJson,
                requestedBarcode = barcode.trim()
            )
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

        if (response.code !in 200..299) {
            Log.e(TAG, "GET ERROR")
            Log.e(TAG, "HTTP=${response.code}")
            Log.e(TAG, response.body)

            error("Errore HTTP ${response.code}: ${response.body}")
        }

        return response.body
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

        val connection = (
                URL("$baseUrl/Token").openConnection() as HttpURLConnection
                )

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
                output.write(formBody.toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseBody = readResponseBody(connection, responseCode)

            Log.d(TAG, "TOKEN RESPONSE")
            Log.d(TAG, "HTTP=$responseCode")
            Log.d(TAG, responseBody)

            if (responseCode !in 200..299) {
                Log.e(TAG, "AUTENTICAZIONE FALLITA")
                error("Autenticazione HTTP $responseCode: $responseBody")
            }

            val root = JSONObject(responseBody)

            val token = sequenceOf(
                root.optString("access_token"),
                root.optString("AccessToken"),
                root.optString("token"),
                root.optString("Token"),
                root.optJSONObject("data")?.optString("access_token").orEmpty(),
                root.optJSONObject("data")?.optString("AccessToken").orEmpty(),
                root.optJSONObject("data")?.optString("token").orEmpty(),
                root.optJSONObject("data")?.optString("Token").orEmpty()
            ).firstOrNull { it.isNotBlank() }

            val resolvedToken = token ?: error(
                "Token non trovato nella risposta di autenticazione: $responseBody"
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
    ): HttpResponse {
        val connection = (
                URL(urlString).openConnection() as HttpURLConnection
                )

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(
                "Authorization",
                "Bearer $bearerToken"
            )

            val responseCode = connection.responseCode
            val responseBody = readResponseBody(connection, responseCode)

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
        val stream: InputStream? = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        if (stream == null) {
            return ""
        }

        return BufferedReader(
            InputStreamReader(stream, StandardCharsets.UTF_8)
        ).use { reader ->
            reader.readText()
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(
            value,
            StandardCharsets.UTF_8.name()
        )

    private fun parseSearchResponse(jsonText: String): DueRetailProductSummary? {
        val root = JSONObject(jsonText)

        check(root.optInt("result_code", -1) == 0) {
            root.optString("error_message", "Errore API sconosciuto")
        }

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
            minimumStock = item.optDouble("ScortaMinima", -1.0),
            maximumStock = item.optDouble("ScortaMassima", -1.0),
            reorderLot = item.optDouble("LottoRiordino", -1.0)
        )
    }

    private fun parseDetailResponse(
        jsonText: String,
        requestedBarcode: String
    ): DueRetailProductDetail {
        val root = JSONObject(jsonText)

        check(root.optInt("result_code", -1) == 0) {
            root.optString("error_message", "Errore API sconosciuto")
        }

        val item = root.getJSONObject("data")

        val barcode = findBarcode(
            item.optJSONArray("DTOBarcodes"),
            requestedBarcode
        )

        val publicPriceEntry = findPublicPrice(
            item.optJSONArray("DTOPrezziVenditaRetail")
        )

        val stockEntry = firstObject(
            item.optJSONArray("DTOGiacenze_SommatePerMagazzinoProdotto")
        )

        return DueRetailProductDetail(
            id = item.getLong("Id"),
            articleCode = item.optString("Codice"),
            description = item.optString("Descrizione"),
            barcode = barcode,
            vatRate = item.optDouble("AliquotaIva", 0.0),
            year = item.optString("StagioneAnno"),
            season = item.optString("StagionePeriodicita"),
            publicPrice = publicPriceEntry?.optDoubleOrNull("Prezzo"),
            taxablePrice = publicPriceEntry?.optDoubleOrNull("Imponibile"),
            stock = stockEntry?.optDoubleOrNull("QtaGiacenza"),
            availableStock = stockEntry?.optDoubleOrNull("QtaDisponibile"),
            minimumStock = item.optDouble("ScortaMinima", -1.0),
            maximumStock = item.optDouble("ScortaMassima", -1.0),
            reorderLot = item.optDouble("LottoRiordino", -1.0),
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
            val item = barcodes.optJSONObject(index) ?: continue
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

    private fun findPublicPrice(prices: JSONArray?): JSONObject? {
        if (prices == null) {
            return null
        }

        for (index in 0 until prices.length()) {
            val item = prices.optJSONObject(index) ?: continue
            val list = item.optJSONObject("Listino")
            val description = list?.optString("Descrizione").orEmpty()

            if (description.equals("3-AL PUBBLICO", ignoreCase = true)) {
                return item
            }
        }

        return null
    }

    private fun firstObject(array: JSONArray?): JSONObject? =
        if (array != null && array.length() > 0) {
            array.optJSONObject(0)
        } else {
            null
        }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
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