package com.scan2enter.search

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class GatewaySearchClient(
    private val baseUrl: String = "http://192.168.1.30:5055"
) {

    fun search(query: String): Result<List<SearchResult>> =
        runCatching {
            val normalized = query.trim()

            if (normalized.length < 2) {
                return@runCatching emptyList()
            }

            val encoded = URLEncoder.encode(
                normalized,
                StandardCharsets.UTF_8.name()
            )

            val connection = URL(
                "$baseUrl/api/search?q=$encoded"
            ).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 5_000
                connection.readTimeout = 20_000
                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                val responseCode = connection.responseCode
                val stream =
                    if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val body =
                    if (stream != null) {
                        BufferedReader(
                            InputStreamReader(
                                stream,
                                StandardCharsets.UTF_8
                            )
                        ).use { it.readText() }
                    } else {
                        ""
                    }

                check(responseCode in 200..299) {
                    "Gateway HTTP $responseCode: $body"
                }

                parseSearchResponse(body)
            } finally {
                connection.disconnect()
            }
        }

    private fun parseSearchResponse(
        jsonText: String
    ): List<SearchResult> {
        val root = JSONObject(jsonText)
        val items = root.optJSONArray("items")
            ?: return emptyList()

        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index)
                    ?: continue

                add(
                    SearchResult(
                        id = item.optLong("id"),
                        code = item.optString("code"),
                        description = item.optString("description"),
                        barcode = item.optString("barcode"),
                        price = item.optString("price"),
                        stock = item.optString("stock"),
                        moved = item.optBoolean("moved", false),
                        lastMovement =
                            item.optString("lastMovement")
                                .takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }
}
