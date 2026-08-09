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
 * Client per Scan2EnterGateway.
 *
 * Non usa credenziali, token o clientId Due Retail.
 */
class GatewayApiClient(
    private val baseUrl: String = "http://192.168.1.30:5055"
) {

    companion object {
        private const val TAG = "Scan2Enter"
    }

    /**
     * Costruisce l'indirizzo HTTP dell'immagine prodotto esposta dal Gateway.
     *
     * GET /api/product/{barcode}/image
     */
    fun getProductImageUrl(barcode: String): String {
        require(barcode.isNotBlank()) {
            "Il barcode non può essere vuoto"
        }

        val encodedBarcode = URLEncoder.encode(
            barcode.trim(),
            StandardCharsets.UTF_8.name()
        )

        return "${baseUrl.trimEnd('/')}/api/product/$encodedBarcode/image"
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
     * Recupera il prezzo corretto per un cliente e un articolo.
     *
     * GET /api/session/client-price?clientId=...&barcode=...
     */
    fun getClientPrice(
        clientId: Int,
        barcode: String
    ): Result<ClientPriceDto> = runCatching {
        require(clientId > 0) {
            "Cliente non valido"
        }

        require(barcode.isNotBlank()) {
            "Barcode non valido"
        }

        val encodedBarcode = URLEncoder.encode(
            barcode.trim(),
            StandardCharsets.UTF_8.name()
        )

        val url =
            "${baseUrl.trimEnd('/')}/api/session/client-price" +
                    "?clientId=$clientId" +
                    "&barcode=$encodedBarcode"

        Log.d(TAG, "GATEWAY GET CLIENT PRICE")
        Log.d(TAG, "URL = $url")

        val response = executeGet(url)

        Log.d(TAG, "GATEWAY CLIENT PRICE HTTP=${response.code}")
        Log.d(TAG, "BODY=${response.body.take(500)}")

        when (response.code) {
            HttpURLConnection.HTTP_NOT_FOUND -> {
                error("Prezzo cliente non trovato")
            }

            !in 200..299 -> {
                error(
                    "Gateway HTTP ${response.code}: ${response.body.take(500)}"
                )
            }
        }

        parseClientPrice(response.body)
    }

    /**
     * Cerca clienti disponibili per la Sessione.
     *
     * GET /api/session/customers?q=...
     */
    fun getCustomers(
        query: String
    ): Result<List<CustomerDto>> = runCatching {
        val encodedQuery = URLEncoder.encode(
            query.trim(),
            StandardCharsets.UTF_8.name()
        )

        val url =
            "${baseUrl.trimEnd('/')}/api/session/customers?q=$encodedQuery"

        Log.d(TAG, "GATEWAY GET CUSTOMERS")
        Log.d(TAG, "URL = $url")

        val response = executeGet(url)

        Log.d(TAG, "GATEWAY CUSTOMERS HTTP=${response.code}")
        Log.d(TAG, "BODY=${response.body.take(500)}")

        if (response.code !in 200..299) {
            error(
                "Gateway HTTP ${response.code}: ${response.body.take(500)}"
            )
        }

        parseCustomers(response.body)
    }

    /**
     * Scarica tutte le ubicazioni disponibili.
     *
     * GET /api/locations
     */
    fun getLocations(): Result<List<LocationDto>> = runCatching {
        val url = "${baseUrl.trimEnd('/')}/api/locations"

        Log.d(TAG, "GATEWAY GET ALL LOCATIONS")
        Log.d(TAG, "URL = $url")

        val response = executeGet(url)

        Log.d(TAG, "GATEWAY LOCATIONS RESPONSE HTTP=${response.code}")
        Log.d(TAG, "BODY=${response.body.take(500)}")

        if (response.code !in 200..299) {
            error(
                "Gateway HTTP ${response.code}: ${response.body.take(500)}"
            )
        }

        parseLocations(response.body)
    }

    /**
     * Scarica le ubicazioni assegnate a un articolo.
     *
     * GET /api/product/{articleId}/locations
     */
    fun getProductLocations(
        articleId: Long
    ): Result<List<LocationDto>> = runCatching {
        require(articleId > 0L) {
            "articleId non valido: $articleId"
        }

        val url =
            "${baseUrl.trimEnd('/')}/api/product/$articleId/locations"

        Log.d(TAG, "GATEWAY GET PRODUCT LOCATIONS")
        Log.d(TAG, "ARTICLE ID=$articleId")
        Log.d(TAG, "URL = $url")

        val response = executeGet(url)

        Log.d(
            TAG,
            "GATEWAY PRODUCT LOCATIONS RESPONSE HTTP=${response.code}"
        )
        Log.d(TAG, "BODY=${response.body.take(500)}")

        if (response.code !in 200..299) {
            error(
                "Gateway HTTP ${response.code}: ${response.body.take(500)}"
            )
        }

        parseLocations(response.body)
    }

    /**
     * Assegna una ubicazione a un articolo.
     *
     * POST /api/product/{articleId}/locations/{locationId}
     *
     * Restituisce true quando l'associazione viene creata.
     * Restituisce false quando era già presente.
     */
    fun addLocation(
        articleId: Long,
        locationId: Int
    ): Result<Boolean> = runCatching {
        require(articleId > 0L) {
            "articleId non valido: $articleId"
        }
        require(locationId >= 0) {
            "locationId non valido: $locationId"
        }

        val url =
            "${baseUrl.trimEnd('/')}/api/product/" +
                    "$articleId/locations/$locationId"

        Log.d(TAG, "GATEWAY ADD LOCATION")
        Log.d(TAG, "ARTICLE ID=$articleId LOCATION ID=$locationId")
        Log.d(TAG, "URL = $url")

        val response = executeWithoutBody(
            urlString = url,
            method = "POST"
        )

        Log.d(TAG, "GATEWAY ADD LOCATION HTTP=${response.code}")
        Log.d(TAG, "BODY=${response.body.take(500)}")

        if (response.code !in 200..299) {
            error(
                "Gateway HTTP ${response.code}: ${response.body.take(500)}"
            )
        }

        parseBooleanResult(
            jsonText = response.body,
            propertyName = "added"
        )
    }

    /**
     * Rimuove un'ubicazione da un articolo.
     *
     * DELETE /api/product/{articleId}/locations/{locationId}
     *
     * Restituisce true quando l'associazione viene eliminata.
     * Restituisce false quando non era presente.
     */
    fun removeLocation(
        articleId: Long,
        locationId: Int
    ): Result<Boolean> = runCatching {
        require(articleId > 0L) {
            "articleId non valido: $articleId"
        }
        require(locationId >= 0) {
            "locationId non valido: $locationId"
        }

        val url =
            "${baseUrl.trimEnd('/')}/api/product/" +
                    "$articleId/locations/$locationId"

        Log.d(TAG, "GATEWAY REMOVE LOCATION")
        Log.d(TAG, "ARTICLE ID=$articleId LOCATION ID=$locationId")
        Log.d(TAG, "URL = $url")

        val response = executeWithoutBody(
            urlString = url,
            method = "DELETE"
        )

        Log.d(TAG, "GATEWAY REMOVE LOCATION HTTP=${response.code}")
        Log.d(TAG, "BODY=${response.body.take(500)}")

        if (response.code !in 200..299) {
            error(
                "Gateway HTTP ${response.code}: ${response.body.take(500)}"
            )
        }

        parseBooleanResult(
            jsonText = response.body,
            propertyName = "removed"
        )
    }

    fun createLocation(name: String): Result<LocationDto> = runCatching {
        val normalizedName = name.trim().uppercase()
        require(normalizedName.isNotBlank()) { "Il nome dell'ubicazione è obbligatorio" }

        val url = "${baseUrl.trimEnd('/')}/api/locations"
        val response = executeJson(
            urlString = url,
            method = "POST",
            jsonBody = JSONObject().put("name", normalizedName).toString()
        )

        if (response.code !in 200..299) {
            error("Gateway HTTP ${response.code}: ${response.body.take(500)}")
        }

        val root = JSONObject(response.body)
        val item = root.optJSONObject("location")
            ?: error("Risposta Gateway non valida: location mancante")

        LocationDto(
            id = item.optInt("id", -1),
            name = item.optString("name", normalizedName).trim()
        ).also { require(it.id >= 0) { "ID ubicazione non valido" } }
    }

    fun deleteLocation(locationId: Int): Result<DeleteLocationResult> = runCatching {
        require(locationId >= 0) { "locationId non valido: $locationId" }

        val url = "${baseUrl.trimEnd('/')}/api/locations/$locationId"
        val response = executeWithoutBody(url, "DELETE")
        val root = if (response.body.isBlank()) JSONObject() else JSONObject(response.body)

        if (response.code !in 200..299 && response.code != HttpURLConnection.HTTP_CONFLICT) {
            error("Gateway HTTP ${response.code}: ${response.body.take(500)}")
        }

        DeleteLocationResult(
            deleted = root.optBoolean("deleted", false),
            usageCount = root.optInt("usageCount", 0),
            message = root.optString("message", "")
        )
    }


    fun renameLocation(
        locationId: Int,
        name: String
    ): Result<LocationDto> = runCatching {
        require(locationId >= 0) { "locationId non valido: $locationId" }

        val normalizedName = name.trim().uppercase()
        require(normalizedName.isNotBlank()) {
            "Il nome dell'ubicazione è obbligatorio"
        }

        val url = "${baseUrl.trimEnd('/')}/api/locations/$locationId"
        val response = executeJson(
            urlString = url,
            method = "PUT",
            jsonBody = JSONObject().put("name", normalizedName).toString()
        )

        if (response.code !in 200..299) {
            val message = runCatching {
                JSONObject(response.body).optString("message")
            }.getOrNull().orEmpty()

            error(
                message.ifBlank {
                    "Gateway HTTP ${response.code}: ${response.body.take(500)}"
                }
            )
        }

        parseLocationEnvelope(response.body, normalizedName)
    }

    fun duplicateNextLocation(
        locationId: Int
    ): Result<LocationDto> = runCatching {
        require(locationId >= 0) { "locationId non valido: $locationId" }

        val url =
            "${baseUrl.trimEnd('/')}/api/locations/$locationId/duplicate-next"

        val response = executeWithoutBody(
            urlString = url,
            method = "POST"
        )

        if (response.code !in 200..299) {
            val message = runCatching {
                JSONObject(response.body).optString("message")
            }.getOrNull().orEmpty()

            error(
                message.ifBlank {
                    "Gateway HTTP ${response.code}: ${response.body.take(500)}"
                }
            )
        }

        parseLocationEnvelope(response.body, "")
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

    /**
     * Legge lo storico colli recente o completo.
     */
    fun getColloHistory(
        query: String = "",
        days: Int = 30,
        limit: Int = 100
    ): Result<List<ColloHistorySummaryDto>> = runCatching {
        val encodedQuery = URLEncoder.encode(
            query.trim(),
            StandardCharsets.UTF_8.name()
        )

        val url =
            "${baseUrl.trimEnd('/')}/api/session/colli" +
                    "?days=${days.coerceAtLeast(0)}" +
                    "&limit=${limit.coerceIn(1, 500)}" +
                    "&q=$encodedQuery"

        val response = executeGet(url)

        if (response.code !in 200..299) {
            error(
                "Gateway HTTP ${response.code}: ${response.body.take(500)}"
            )
        }

        val root = JSONObject(response.body)
        val array = root.optJSONArray("items")
            ?: error("Risposta storico colli non valida")

        val result = ArrayList<ColloHistorySummaryDto>(array.length())

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue

            result.add(
                ColloHistorySummaryDto(
                    testataId = item.optInt("testataId", 0),
                    numeroCollo = item.optString("numeroCollo", "").trim(),
                    barcodeCollo = item.optString("barcodeCollo", "").trim(),
                    clientId = item.optInt("clientId", 0),
                    clientName = item.optString("clientName", "").trim(),
                    createdAt = item.optString("createdAt", "").trim(),
                    itemCount = item.optInt("itemCount", 0),
                    pieceCount = item.optDouble("pieceCount", 0.0),
                    total = item.optDouble("total", 0.0),
                    isElaborato = item.optBoolean("isElaborato", false)
                )
            )
        }

        result
    }

    fun getColloHistoryDetail(
        testataId: Int
    ): Result<ColloHistoryDetailDto> = runCatching {
        require(testataId > 0) {
            "Id collo non valido"
        }

        val url =
            "${baseUrl.trimEnd('/')}/api/session/colli/$testataId"

        val response = executeGet(url)

        if (response.code !in 200..299) {
            error(
                "Gateway HTTP ${response.code}: ${response.body.take(500)}"
            )
        }

        val root = JSONObject(response.body)
        val itemsArray = root.optJSONArray("items") ?: JSONArray()
        val items = ArrayList<ColloHistoryItemDto>(itemsArray.length())

        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue

            items.add(
                ColloHistoryItemDto(
                    articleId = item.optLong("articleId", 0L),
                    articleCode = item.optString("articleCode", "").trim(),
                    description = item.optString("description", "").trim(),
                    barcode = item.optString("barcode", "").trim(),
                    quantity = item.optDouble("quantity", 0.0),
                    price = item.optDouble("price", 0.0),
                    total = item.optDouble("total", 0.0)
                )
            )
        }

        ColloHistoryDetailDto(
            testataId = root.optInt("testataId", 0),
            numeroCollo = root.optString("numeroCollo", "").trim(),
            barcodeCollo = root.optString("barcodeCollo", "").trim(),
            clientId = root.optInt("clientId", 0),
            clientName = root.optString("clientName", "").trim(),
            createdAt = root.optString("createdAt", "").trim(),
            isElaborato = root.optBoolean("isElaborato", false),
            total = root.optDouble("total", 0.0),
            items = items
        )
    }

    /**
     * Crea un collo dalla Sessione Android.
     *
     * POST /api/session/colli
     */
    fun createSessionCollo(
        clientId: Int,
        items: List<SessionColloItemDto>
    ): Result<CreateColloResultDto> = runCatching {
        require(clientId > 0) {
            "Cliente non valido"
        }

        require(items.isNotEmpty()) {
            "La sessione è vuota"
        }

        val url =
            "${baseUrl.trimEnd('/')}/api/session/colli"

        val body = JSONObject()
            .put("clientId", clientId)
            .put(
                "items",
                JSONArray().apply {
                    items.forEach { item ->
                        put(
                            JSONObject()
                                .put("barcode", item.barcode.trim())
                                .put("quantity", item.quantity)
                                .put("price", item.price)
                        )
                    }
                }
            )
            .toString()

        Log.d(TAG, "GATEWAY CREATE SESSION COLLO")
        Log.d(TAG, "URL = $url")
        Log.d(TAG, "BODY=$body")

        val response = executeJson(
            urlString = url,
            method = "POST",
            jsonBody = body
        )

        Log.d(TAG, "GATEWAY CREATE COLLO HTTP=${response.code}")
        Log.d(TAG, "BODY=${response.body.take(500)}")

        if (response.code !in 200..299) {
            error(
                "Gateway HTTP ${response.code}: ${response.body.take(500)}"
            )
        }

        val root = JSONObject(response.body)

        val created =
            root.optBoolean("created", false)

        val collo =
            root.optJSONObject("collo")
                ?: error(
                    "Risposta Gateway non valida: oggetto collo mancante"
                )

        val numeroCollo =
            collo.optString("numeroCollo", "").trim()

        val barcodeCollo =
            collo.optString("barcodeCollo", "").trim()

        check(
            created &&
                    numeroCollo.isNotBlank() &&
                    barcodeCollo.isNotBlank()
        ) {
            "Risposta Gateway non valida: dati collo mancanti"
        }

        CreateColloResultDto(
            created = created,
            numeroCollo = numeroCollo,
            barcodeCollo = barcodeCollo
        )
    }

    /**
     * Invia un lavoro di stampa etichette al Gateway.
     */
    fun printLabel(
        articleCode: String,
        description: String,
        barcode: String,
        publicPrice: String,
        quantity: Int,
        printer: String,
        template: String,
        note: String = ""
    ): Result<Unit> = runCatching {
        require(articleCode.isNotBlank()) {
            "Codice articolo non disponibile"
        }
        require(barcode.isNotBlank()) {
            "Barcode non disponibile"
        }
        require(quantity in 1..100) {
            "Quantità non valida"
        }

        val url = "${baseUrl.trimEnd('/')}/api/labels/print"

        val body = JSONObject()
            .put("articleCode", articleCode.trim())
            .put("description", description.trim())
            .put("barcode", barcode.trim())
            .put("publicPrice", publicPrice.trim())
            .put("quantity", quantity)
            .put("printer", printer)
            .put("template", template)
            .put("note", note.trim())
            .toString()

        val response = executeJson(
            urlString = url,
            method = "POST",
            jsonBody = body
        )

        if (response.code !in 200..299) {
            error(
                "Gateway HTTP ${response.code}: ${response.body.take(500)}"
            )
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

    private fun executeWithoutBody(
        urlString: String,
        method: String
    ): HttpResponse {
        val connection =
            URL(urlString).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )

            if (method == "POST") {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(0)
                connection.outputStream.use {
                    // Endpoint senza body.
                }
            }

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

    private fun executeJson(
        urlString: String,
        method: String,
        jsonBody: String
    ): HttpResponse {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val bytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            return HttpResponse(code, readResponseBody(connection, code))
        } finally {
            connection.disconnect()
        }
    }


    private fun parseLocationEnvelope(
        jsonText: String,
        fallbackName: String
    ): LocationDto {
        val root = JSONObject(jsonText)
        val item = root.optJSONObject("location")
            ?: error("Risposta Gateway non valida: location mancante")

        return LocationDto(
            id = item.optInt("id", -1),
            name = item.optString("name", fallbackName).trim()
        ).also {
            require(it.id >= 0) { "ID ubicazione non valido" }
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

    private fun parseClientPrice(
        jsonText: String
    ): ClientPriceDto {
        check(jsonText.isNotBlank()) {
            "Il Gateway ha restituito una risposta prezzo cliente vuota"
        }

        val item = JSONObject(jsonText)

        val articleId = item.optLong("articleId", 0L)
        check(articleId > 0L) {
            "Risposta Gateway non valida: articleId mancante"
        }

        return ClientPriceDto(
            clientId = item.optInt("clientId", 0),
            clientName = item.optString("clientName", "").trim(),
            clientPriceListId = item.optInt("clientPriceListId", 0),
            priceListId = item.optInt("priceListId", 0),
            priceListName = item.optString("priceListName", "").trim(),
            articleId = articleId,
            articleCode = item.optString("articleCode", "").trim(),
            description = item.optString("description", "").trim(),
            barcode = item.optString("barcode", "").trim(),
            listPrice = item.optNullableDouble("listPrice"),
            discount1 = item.optDouble("discount1", 0.0),
            discount2 = item.optDouble("discount2", 0.0),
            discount3 = item.optDouble("discount3", 0.0),
            discount4 = item.optDouble("discount4", 0.0),
            finalPrice = item.optNullableDouble("finalPrice")
        )
    }

    private fun parseCustomers(
        jsonText: String
    ): List<CustomerDto> {
        check(jsonText.isNotBlank()) {
            "Il Gateway ha restituito una risposta clienti vuota"
        }

        val root = JSONObject(jsonText)
        val array = root.optJSONArray("items")
            ?: error("Risposta Gateway non valida: items clienti mancante")

        val result = ArrayList<CustomerDto>(array.length())

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue

            val id = item.optInt("id", 0)
            if (id <= 0) continue

            result.add(
                CustomerDto(
                    id = id,
                    name = item.optString("name", "").trim(),
                    code = item.optString("code", "").trim(),
                    priceListId = item.optInt("priceListId", 0),
                    discount1 = item.optDouble("discount1", 0.0),
                    discount2 = item.optDouble("discount2", 0.0),
                    discount3 = item.optDouble("discount3", 0.0),
                    discount4 = item.optDouble("discount4", 0.0)
                )
            )
        }

        return result
    }

    private fun parseLocations(
        jsonText: String
    ): List<LocationDto> {
        check(jsonText.isNotBlank()) {
            "Il Gateway ha restituito una risposta ubicazioni vuota"
        }

        val trimmedJson = jsonText.trim()

        val array = when {
            trimmedJson.startsWith("[") -> {
                JSONArray(trimmedJson)
            }

            trimmedJson.startsWith("{") -> {
                val root = JSONObject(trimmedJson)

                root.optJSONArray("items")
                    ?: root.optJSONArray("locations")
                    ?: error(
                        "Risposta Gateway non valida: " +
                                "lista ubicazioni mancante"
                    )
            }

            else -> {
                error(
                    "Risposta Gateway non valida: JSON non riconosciuto"
                )
            }
        }

        val result = ArrayList<LocationDto>(array.length())

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue

            val id = item.optInt("id", -1)
            val name = item.optString("name", "").trim()

            if (id < 0) {
                Log.w(
                    TAG,
                    "UBICAZIONE IGNORATA: id mancante indice=$index"
                )
                continue
            }

            result.add(
                LocationDto(
                    id = id,
                    name = name
                )
            )
        }

        return result
    }

    private fun parseBooleanResult(
        jsonText: String,
        propertyName: String
    ): Boolean {
        val trimmedJson = jsonText.trim()

        if (trimmedJson.equals("true", ignoreCase = true)) {
            return true
        }

        if (trimmedJson.equals("false", ignoreCase = true)) {
            return false
        }

        check(trimmedJson.isNotBlank()) {
            "Il Gateway ha restituito una risposta vuota"
        }

        val item = JSONObject(trimmedJson)

        check(item.has(propertyName)) {
            "Risposta Gateway non valida: campo $propertyName mancante"
        }

        return item.optBoolean(propertyName, false)
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

data class ColloHistorySummaryDto(
    val testataId: Int,
    val numeroCollo: String,
    val barcodeCollo: String,
    val clientId: Int,
    val clientName: String,
    val createdAt: String,
    val itemCount: Int,
    val pieceCount: Double,
    val total: Double,
    val isElaborato: Boolean
)

data class ColloHistoryItemDto(
    val articleId: Long,
    val articleCode: String,
    val description: String,
    val barcode: String,
    val quantity: Double,
    val price: Double,
    val total: Double
)

data class ColloHistoryDetailDto(
    val testataId: Int,
    val numeroCollo: String,
    val barcodeCollo: String,
    val clientId: Int,
    val clientName: String,
    val createdAt: String,
    val isElaborato: Boolean,
    val total: Double,
    val items: List<ColloHistoryItemDto>
)

data class SessionColloItemDto(
    val barcode: String,
    val quantity: Int,
    val price: Double
)

data class CreateColloResultDto(
    val created: Boolean,
    val numeroCollo: String,
    val barcodeCollo: String
)

data class CustomerDto(
    val id: Int,
    val name: String,
    val code: String,
    val priceListId: Int,
    val discount1: Double,
    val discount2: Double,
    val discount3: Double,
    val discount4: Double
)

data class ClientPriceDto(
    val clientId: Int,
    val clientName: String,
    val clientPriceListId: Int,
    val priceListId: Int,
    val priceListName: String,
    val articleId: Long,
    val articleCode: String,
    val description: String,
    val barcode: String,
    val listPrice: Double?,
    val discount1: Double,
    val discount2: Double,
    val discount3: Double,
    val discount4: Double,
    val finalPrice: Double?
)

data class DeleteLocationResult(
    val deleted: Boolean,
    val usageCount: Int,
    val message: String
)