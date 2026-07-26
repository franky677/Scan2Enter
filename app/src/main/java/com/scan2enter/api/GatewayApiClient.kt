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

/**
 * Client di sola lettura per Scan2EnterGateway.
 *
 * Non usa credenziali, token o clientId Due Retail.
 */
class GatewayApiClient(
    private val baseUrl: String = "http://192.168.1.30:5055"
) {

    companion object {
        private const val TAG = "Scan2Enter"
    }

    fun getProductByBarcode(
        barcode: String
    ): Result<GatewayProductDto> = runCatching {
        require(barcode.isNotBlank()) {
            "Il barcode non può essere vuoto"
        }

        val normalizedBarcode = barcode.trim()
        val encodedBarcode = URLEncoder.encode(
            normalizedBarcode,
            StandardCharsets.UTF_8.name()
        )

        val url = "${baseUrl.trimEnd('/')}/api/product/$encodedBarcode"

        Log.d(TAG, "GATEWAY GET PRODUCT")
        Log.d(TAG, "URL = $url")

        val response = executeGet(url)

        Log.d(TAG, "GATEWAY RESPONSE HTTP=${response.code}")
        Log.d(TAG, response.body)

        when (response.code) {
            HttpURLConnection.HTTP_NOT_FOUND -> {
                error(
                    "Nessun articolo trovato per il barcode " +
                            normalizedBarcode
                )
            }

            !in 200..299 -> {
                error(
                    "Gateway HTTP ${response.code}: ${response.body}"
                )
            }
        }

        parseProduct(response.body)
    }

    private fun executeGet(urlString: String): HttpResponse {
        val connection =
            URL(urlString).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            val responseBody = readResponseBody(
                connection = connection,
                responseCode = responseCode
            )

            return HttpResponse(
                code = responseCode,
                body = responseBody
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseProduct(jsonText: String): GatewayProductDto {
        check(jsonText.isNotBlank()) {
            "Il Gateway ha restituito una risposta vuota"
        }

        val item = JSONObject(jsonText)

        val articleId = item.optLong("articleId", 0L)

        check(articleId > 0L) {
            "Risposta Gateway non valida: articleId mancante"
        }

        return GatewayProductDto(
            articleId = articleId,
            articleCode = item.optString("articleCode"),
            description = item.optString("description"),
            barcode = item.optString("barcode"),
            taxablePrice = item.optString("taxablePrice"),
            vatRate = item.optString("vatRate"),
            publicPrice = item.optString("publicPrice"),
            season = item.optString("season"),
            year = item.optString("year"),
            location = item.optString("location"),
            stock = item.optString("stock"),
            availableStock = item.optString("availableStock"),
            minimumStock = item.optString("minimumStock"),
            maximumStock = item.optString("maximumStock"),
            reorderLot = item.optString("reorderLot"),
            supplierId = item.optLong("supplierId", 0L),
            supplierName = item.optString("supplierName"),
            supplierArticleCode =
                item.optString("supplierArticleCode"),
            coverImagePath = item.optString("coverImagePath")
        )
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

    private data class HttpResponse(
        val code: Int,
        val body: String
    )
}
