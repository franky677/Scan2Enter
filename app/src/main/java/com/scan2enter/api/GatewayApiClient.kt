package com.scan2enter.api

import android.util.Log
import com.scan2enter.reorder.ReorderItem
import org.json.JSONArray
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

    /**
     * Scarica dal Gateway la lista completa degli articoli da riordinare.
     */
    fun getReorderList(): Result<List<ReorderItem>> {
        return try {
            val url = "${baseUrl.trimEnd('/')}/api/reorder-list"

            Log.d(TAG, "GATEWAY GET REORDER LIST")
            Log.d(TAG, "URL = $url")
            Log.d(TAG, "PRIMA executeGet")

            val response = executeGet(url)

            Log.d(TAG, "DOPO executeGet")
            Log.d(TAG, "GATEWAY REORDER RESPONSE HTTP=${response.code}")
            Log.d(TAG, "BODY LENGTH=${response.body.length}")
            Log.d(TAG, "BODY START=${response.body.take(200)}")
            Log.d(TAG, "BODY END=${response.body.takeLast(200)}")

            if (response.code !in 200..299) {
                error(
                    "Gateway HTTP ${response.code}: ${response.body.take(500)}"
                )
            }

            Log.d(TAG, "PRIMA parseReorderList")

            val result = parseReorderList(response.body)

            Log.d(TAG, "DOPO parseReorderList")
            Log.d(
                TAG,
                "GATEWAY REORDER LIST elementi=${result.size}"
            )

            Result.success(result)
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "ECCEZIONE getReorderList " +
                        "TIPO=${error.javaClass.name} " +
                        "MESSAGGIO=${error.message}"
            )

            Log.e(
                TAG,
                "CAUSA=${error.cause?.javaClass?.name}: " +
                        "${error.cause?.message}"
            )

            Result.failure(error)
        }
    }

    private fun executeGet(urlString: String): HttpResponse {
        val connection =
            URL(urlString).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 120_000
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

    private fun parseReorderList(
        jsonText: String
    ): List<ReorderItem> {
        check(jsonText.isNotBlank()) {
            "Il Gateway ha restituito una lista di riordino vuota"
        }

        val trimmedJson = jsonText.trim()

        val array = when {
            trimmedJson.startsWith("[") -> {
                JSONArray(trimmedJson)
            }

            trimmedJson.startsWith("{") -> {
                val root = JSONObject(trimmedJson)

                root.optJSONArray("items")
                    ?: error(
                        "Risposta Gateway non valida: " +
                                "campo items mancante"
                    )
            }

            else -> {
                error(
                    "Risposta Gateway non valida: " +
                            "JSON non riconosciuto"
                )
            }
        }

        val result = ArrayList<ReorderItem>(array.length())

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue

            val articleId = when {
                item.has("idArticle") -> item.optLong("idArticle", 0L)
                else -> item.optLong("articleId", 0L)
            }

            if (articleId <= 0L) {
                Log.w(
                    TAG,
                    "RIGA RIORDINO IGNORATA: articleId mancante indice=$index"
                )
                continue
            }

            result.add(
                ReorderItem(
                    articleId = articleId,
                    barcode = item.optNullableString("barcode"),
                    articleCode = item.optNullableString("articleCode"),
                    description = item.optNullableString("description"),
                    supplierId = item.optLong("supplierId", 0L),
                    supplierName = item.optNullableString("supplierName"),
                    supplierArticleCode =
                        item.optNullableString("supplierArticleCode"),
                    stock = item.optNullableDouble("stock"),
                    availableStock = item.optNullableDouble("available"),
                    minimumStock = item.optNullableDouble("minimumStock"),
                    maximumStock = item.optNullableDouble("maximumStock"),
                    reorderLot = item.optNullableDouble("reorderLot"),
                    quantityToOrder =
                        item.optNullableDouble("suggestedQuantity") ?: 0.0
                )
            )
        }



        return result
    }

    private fun JSONObject.optNullableString(key: String): String {
        if (!has(key) || isNull(key)) return ""
        return optString(key, "").trim()
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null

        val value = optDouble(key, Double.NaN)
        if (value.isNaN() || value == -1.0) return null

        return value
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