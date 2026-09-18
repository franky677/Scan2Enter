package com.scan2enter.update

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateClient(
    private val context: Context,
    private val baseUrl: String = "http://192.168.1.30:5055"
) {

    fun getLatest(): Result<AppUpdateInfo> = runCatching {
        val connection =
            URL("${baseUrl.trimEnd('/')}/api/app-update/latest?channel=${AppUpdateChannel.get(context)}")
                .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode

            if (responseCode !in 200..299) {
                error("Gateway HTTP $responseCode")
            }

            val body =
                connection.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

            val json = JSONObject(body)

            AppUpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.optString("versionName"),
                releaseNotes = json.optString("releaseNotes")
            )
        } finally {
            connection.disconnect()
        }
    }
}
