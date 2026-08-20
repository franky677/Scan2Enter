package com.scan2enter.repository

import android.util.Log
import com.scan2enter.api.DeleteLocationResult
import com.scan2enter.api.DueRetailApiClient
import com.scan2enter.api.DueRetailStockSettings
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.api.LocationDto
import com.scan2enter.model.ProductInfo
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Le letture articolo e le scritture delle scorte passano dal Gateway.
 *
 * DueRetailApiClient resta nel costruttore per non modificare la creazione
 * condivisa del repository e mantenere compatibilità con il codice esistente.
 */
class ProductRepository(
    private val gatewayApi: GatewayApiClient,
    @Suppress("UNUSED_PARAMETER")
    private val dueRetailWriteApi: DueRetailApiClient
) {

    companion object {
        private const val TAG = "Scan2Enter"
        private const val GATEWAY_BASE_URL = "http://192.168.1.30:5055"
    }

    fun getProduct(barcode: String): Result<ProductInfo> = runCatching {
        val product = gatewayApi
            .getProductByBarcode(barcode)
            .getOrThrow()

        val locations = gatewayApi
            .getProductLocations(product.articleId)
            .getOrElse { error ->
                Log.e(
                    TAG,
                    "ERRORE LETTURA UBICAZIONI " +
                            "ARTICLE ID=${product.articleId}: " +
                            error.message,
                    error
                )
                emptyList()
            }

        ProductInfo(
            articleId = product.articleId,
            articleCode = product.articleCode,
            description = product.description,
            barcode = product.barcode,
            active = product.active,
            taxablePrice = product.taxablePrice,
            vatRate = product.vatRate,
            publicPrice = product.publicPrice,
            season = product.season,
            year = product.year,
            location = product.location,
            locations = locations,
            stock = product.stock,
            availableStock = product.availableStock,
            minimumStock = product.minimumStock,
            maximumStock = product.maximumStock,
            reorderLot = product.reorderLot,
            supplierId = product.supplierId,
            supplierName = product.supplierName,
            supplierArticleCode = product.supplierArticleCode,
            coverImagePath = product.coverImagePath
        )
    }

    fun getLocations(): Result<List<LocationDto>> = gatewayApi.getLocations()

    fun getProductLocations(articleId: Long): Result<List<LocationDto>> =
        gatewayApi.getProductLocations(articleId)

    fun addLocation(articleId: Long, locationId: Int): Result<Boolean> =
        gatewayApi.addLocation(articleId, locationId)

    fun removeLocation(articleId: Long, locationId: Int): Result<Boolean> =
        gatewayApi.removeLocation(articleId, locationId)

    fun createLocation(name: String): Result<LocationDto> =
        gatewayApi.createLocation(name)

    fun deleteLocation(locationId: Int): Result<DeleteLocationResult> =
        gatewayApi.deleteLocation(locationId)

    fun renameLocation(
        locationId: Int,
        name: String
    ): Result<LocationDto> =
        gatewayApi.renameLocation(
            locationId = locationId,
            name = name
        )

    fun duplicateNextLocation(
        locationId: Int
    ): Result<LocationDto> =
        gatewayApi.duplicateNextLocation(locationId)

    /**
     * Aggiorna i parametri di riordino tramite:
     * PUT /api/product/{articleId}/stock
     */
    fun updateStockSettings(
        articleId: Long,
        minimumStock: Double? = null,
        maximumStock: Double? = null,
        reorderLot: Double? = null
    ): Result<DueRetailStockSettings> = runCatching {
        require(articleId > 0L) { "articleId non valido: $articleId" }
        minimumStock?.let { require(it >= -1.0) { "Scorta minima non valida: $it" } }
        maximumStock?.let { require(it >= -1.0) { "Scorta massima non valida: $it" } }
        reorderLot?.let { require(it >= -1.0) { "Lotto riordino non valido: $it" } }

        val requestJson = JSONObject().apply {
            put("warehouseId", 0)
            put("variant1Id", -1)
            put("variant2Id", -1)
            put("variant3Id", -1)
            put("minimumStock", minimumStock ?: JSONObject.NULL)
            put("maximumStock", maximumStock ?: JSONObject.NULL)
            put("reorderLot", reorderLot ?: JSONObject.NULL)
        }

        val url = "$GATEWAY_BASE_URL/api/product/$articleId/stock"

        Log.d(TAG, "GATEWAY STOCK SETTINGS UPDATE START")
        Log.d(TAG, "URL=$url")
        Log.d(TAG, "BODY=$requestJson")

        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PUT"
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            val bytes = requestJson.toString().toByteArray(StandardCharsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }

            val code = connection.responseCode
            val body = readResponseBody(connection, code)

            Log.d(TAG, "GATEWAY STOCK SETTINGS HTTP=$code")
            Log.d(TAG, "BODY=${body.take(1000)}")

            if (code !in 200..299) {
                error("Gateway HTTP $code: ${body.take(500)}")
            }

            val json = JSONObject(body)
            check(json.optBoolean("updated", false)) {
                "Il Gateway non ha confermato l'aggiornamento"
            }

            DueRetailStockSettings(
                articleId = json.optLong("articleId", articleId),
                articleCode = "",
                minimumStock = json.optDouble("minimumStock", -1.0),
                maximumStock = json.optDouble("maximumStock", -1.0),
                reorderLot = json.optDouble("reorderLot", -1.0)
            ).also { updated ->
                Log.d(
                    TAG,
                    "GATEWAY STOCK SETTINGS UPDATE OK articleId=${updated.articleId} " +
                            "minimum=${updated.minimumStock} " +
                            "maximum=${updated.maximumStock} " +
                            "lot=${updated.reorderLot}"
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponseBody(
        connection: HttpURLConnection,
        responseCode: Int
    ): String {
        val stream: InputStream? =
            if (responseCode in 200..299) connection.inputStream else connection.errorStream

        if (stream == null) return ""

        return BufferedReader(
            InputStreamReader(stream, StandardCharsets.UTF_8)
        ).use { it.readText() }
    }
}