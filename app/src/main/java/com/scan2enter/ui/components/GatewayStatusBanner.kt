package com.scan2enter.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val GATEWAY_HEALTH_URL =
    "http://192.168.1.30:5055/api/health/database"

private const val CHECK_INTERVAL_MS = 5_000L
private const val CONNECT_TIMEOUT_MS = 1_800
private const val READ_TIMEOUT_MS = 1_800

private enum class GatewayHealth {
    CHECKING,
    OK,
    UNREACHABLE,
    DATABASE_ERROR
}

@Composable
fun GatewayStatusBanner(
    modifier: Modifier = Modifier
) {
    var health by remember {
        mutableStateOf(GatewayHealth.CHECKING)
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            health = checkGatewayHealth()
            delay(CHECK_INTERVAL_MS)
        }
    }

    when (health) {
        GatewayHealth.CHECKING,
        GatewayHealth.OK -> Unit

        GatewayHealth.UNREACHABLE -> {
            GatewayErrorBanner(
                modifier = modifier,
                title = "🔴 NEGOZIO NON RAGGIUNGIBILE",
                message = "Verifica WireGuard o la connessione di rete."
            )
        }

        GatewayHealth.DATABASE_ERROR -> {
            GatewayErrorBanner(
                modifier = modifier,
                title = "🔴 DATABASE NON DISPONIBILE",
                message = "Il Gateway risponde, ma il db di Due Retail non è raggiungibile."
            )
        }
    }
}

@Composable
private fun GatewayErrorBanner(
    modifier: Modifier,
    title: String,
    message: String
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFB3261E),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(
                horizontal = 14.dp,
                vertical = 11.dp
            ),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = message,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

private suspend fun checkGatewayHealth(): GatewayHealth =
    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            connection =
                (URL(GATEWAY_HEALTH_URL).openConnection()
                        as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    useCaches = false
                }

            when (connection.responseCode) {
                in 200..299 ->
                    GatewayHealth.OK

                503 ->
                    GatewayHealth.DATABASE_ERROR

                else -> {
                    Log.w(
                        "Scan2Enter",
                        "GATEWAY HEALTH HTTP ${connection.responseCode}"
                    )
                    GatewayHealth.DATABASE_ERROR
                }
            }
        } catch (error: Exception) {
            Log.w(
                "Scan2Enter",
                "GATEWAY NON RAGGIUNGIBILE: ${error.message}"
            )
            GatewayHealth.UNREACHABLE
        } finally {
            connection?.disconnect()
        }
    }
