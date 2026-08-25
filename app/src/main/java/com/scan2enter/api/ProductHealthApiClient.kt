package com.scan2enter.api

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ProductHealthApiClient(
    private val baseUrl: String = "http://192.168.1.30:5055"
) {
    companion object {
        private const val TAG = "Scan2Enter"
    }

    fun getProductHealth(barcode: String): Result<ProductHealthDto> = runCatching {
        require(barcode.isNotBlank()) { "Barcode non valido" }

        val encodedBarcode = URLEncoder.encode(
            barcode.trim(),
            StandardCharsets.UTF_8.name()
        )

        val url = baseUrl.trimEnd('/') + "/api/product/" + encodedBarcode + "/health"

        Log.d(TAG, "PRODUCT HEALTH URL = " + url)

        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept", "application/json")

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
            }.orEmpty()

            if (code !in 200..299) {
                error("Gateway HTTP " + code + ": " + body.take(500))
            }

            val item = JSONObject(body)

            ProductHealthDto(
                idArticolo = item.optLong("idArticolo", 0L),
                barcode = item.optString("barcode", "").trim(),
                giacenzaFifo = item.optDouble("giacenzaFifo", 0.0),
                valoreFifo = item.optDouble("valoreFifo", 0.0),
                costoMedioFifo = item.optDouble("costoMedioFifo", 0.0),
                ultimaVendita = if (item.isNull("ultimaVendita")) null else item.optString("ultimaVendita", "").trim(),
                giorniDaUltimaVendita = if (item.isNull("giorniDaUltimaVendita")) null else item.optInt("giorniDaUltimaVendita"),
                venduto12M = item.optDouble("venduto12M", 0.0),
                venduto24M = item.optDouble("venduto24M", 0.0),
                rotazione12M = if (item.isNull("rotazione12M")) null else item.optDouble("rotazione12M"),
                mesiCopertura = if (item.isNull("mesiCopertura")) null else item.optDouble("mesiCopertura"),
                statoSalute = item.optString("statoSalute", "OK").trim(),
                descrizioneSalute = item.optString("descrizioneSalute", "").trim()
            )
        } finally {
            connection.disconnect()
        }
    }
}
