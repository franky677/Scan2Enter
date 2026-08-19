package com.scan2enter.ui.screens

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scan2enter.api.ColloHistoryDetailDto
import com.scan2enter.api.ColloHistorySummaryDto
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.session.SessionCustomerStore
import com.scan2enter.session.SessionItem
import com.scan2enter.session.SessionStore
import com.scan2enter.ui.components.Ean13Barcode
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ColloHistoryScreen(
    onBack: () -> Unit,
    onDuplicated: () -> Unit
) {
    val gateway = remember { GatewayApiClient() }
    var query by remember { mutableStateOf("") }
    var completeArchive by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<ColloHistorySummaryDto>>(emptyList()) }
    var detail by remember { mutableStateOf<ColloHistoryDetailDto?>(null) }
    var detailLoading by remember { mutableStateOf(false) }

    fun loadHistory() {
        loading = true
        error = null
        Thread {
            val result = gateway.getColloHistory(
                query = query,
                days = if (completeArchive) 0 else 30,
                limit = if (completeArchive) 300 else 100
            )
            Handler(Looper.getMainLooper()).post {
                loading = false
                result.onSuccess { history = it }
                    .onFailure { error = it.message ?: "Errore caricamento storico" }
            }
        }.start()
    }

    fun openDetail(testataId: Int) {
        detailLoading = true
        error = null
        Thread {
            val result = gateway.getColloHistoryDetail(testataId)
            Handler(Looper.getMainLooper()).post {
                detailLoading = false
                result.onSuccess { detail = it }
                    .onFailure { error = it.message ?: "Errore dettaglio collo" }
            }
        }.start()
    }

    LaunchedEffect(completeArchive) { loadHistory() }
    BackHandler(onBack = onBack)

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 42.sp, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 10.dp))
                Column {
                    Text("📚 STORICO COLLI", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(if (completeArchive) "Archivio completo" else "Ultimi 30 giorni", fontSize = 14.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { completeArchive = false }, enabled = completeArchive, modifier = Modifier.weight(1f)) { Text("RECENTI") }
                Button(onClick = { completeArchive = true }, enabled = !completeArchive, modifier = Modifier.weight(1f)) { Text("TUTTO") }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Numero, cliente, articolo, barcode o nota") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { loadHistory() }),
                modifier = Modifier.fillMaxWidth()
            )

            Button(onClick = { loadHistory() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "RICERCA..." else "🔎 CERCA")
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!loading && history.isEmpty()) Text("Nessun collo trovato.", modifier = Modifier.padding(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history, key = { it.testataId }) { collo ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { openDetail(collo.testataId) },
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "📦 ${collo.numeroCollo}",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                if (collo.hasNote) {
                                    Text(
                                        text = "📝✓",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    )
                                }

                                Text(
                                    if (collo.isElaborato) "✅ ELABORATO" else "🟡 APERTO",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(collo.clientName.ifBlank { "Cliente ${collo.clientId}" }, fontWeight = FontWeight.SemiBold)
                            Text("${formatHistoryDate(collo.createdAt)} • ${collo.itemCount} articoli • ${formatPieces(collo.pieceCount)} pezzi")
                            Text(String.format(Locale.ITALY, "%.2f €", collo.total), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (detailLoading) {
        AlertDialog(onDismissRequest = {}, title = { Text("Caricamento collo...") }, text = { Text("Lettura dettaglio in corso.") }, confirmButton = {})
    }

    detail?.let { collo ->
        ColloDetailDialog(
            collo = collo,
            gateway = gateway,
            onDismiss = { detail = null },
            onDuplicate = {
                val sessionItems = collo.items.mapNotNull { item ->
                    val qty = item.quantity.roundToInt().coerceAtLeast(0)
                    if (item.articleId <= 0L || qty <= 0) null else {
                        val price = String.format(Locale.ITALY, "%.2f", item.price)
                        SessionItem(
                            articleId = item.articleId,
                            articleCode = item.articleCode,
                            description = item.description,
                            barcode = item.barcode,
                            publicPrice = price,
                            stock = "",
                            quantity = qty,
                            priceListName = "STORICO",
                            listPrice = price,
                            discount1 = 0.0,
                            finalPrice = price,
                            manualPrice = ""
                        )
                    }
                }
                SessionStore.replaceWithHistory(
                    historyItems = sessionItems,
                    note = collo.note
                )
                SessionCustomerStore.setCustomer(collo.clientId, collo.clientName.ifBlank { "Cliente ${collo.clientId}" })
                detail = null
                onDuplicated()
            }
        )
    }
}

@Composable
private fun ColloDetailDialog(
    collo: ColloHistoryDetailDto,
    gateway: GatewayApiClient,
    onDismiss: () -> Unit,
    onDuplicate: () -> Unit
) {
    val context = LocalContext.current
    var printing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📦 COLLO ${collo.numeroCollo}") },
        text = {
            LazyColumn(modifier = Modifier.height(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(collo.clientName.ifBlank { "Cliente ${collo.clientId}" }, fontWeight = FontWeight.Bold)
                    Text(formatHistoryDate(collo.createdAt))
                    if (collo.note.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "📝 ${collo.note}",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (collo.barcodeCollo.isNotBlank()) {
                        Ean13Barcode(code = collo.barcodeCollo, modifier = Modifier.fillMaxWidth().height(130.dp))
                        Text(collo.barcodeCollo, modifier = Modifier.fillMaxWidth(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }

                items(collo.items) { item ->
                    Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 1.dp) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(item.description.ifBlank { item.articleCode }, fontWeight = FontWeight.Bold)
                            if (item.articleCode.isNotBlank()) Text(item.articleCode)
                            Text("x${formatPieces(item.quantity)} • ${String.format(Locale.ITALY, "%.2f € cad.", item.price)}")
                            Text(String.format(Locale.ITALY, "Totale %.2f €", item.total), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                item {
                    Text(String.format(Locale.ITALY, "TOTALE COLLO %.2f €", collo.total), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onDuplicate, modifier = Modifier.fillMaxWidth()) { Text("📋 DUPLICA IN SESSIONE") }
                    Button(
                        onClick = {
                            printing = true
                            Thread {
                                var failure: Throwable? = null
                                for (item in collo.items) {
                                    val qty = item.quantity.roundToInt().coerceIn(1, 100)
                                    val result = gateway.printLabel(
                                        articleCode = item.articleCode,
                                        description = item.description,
                                        barcode = item.barcode,
                                        publicPrice = String.format(Locale.ITALY, "%.2f", item.price),
                                        quantity = qty,
                                        printer = "GODEX",
                                        template = "PRICE"
                                    )
                                    if (result.isFailure) { failure = result.exceptionOrNull(); break }
                                }
                                Handler(Looper.getMainLooper()).post {
                                    printing = false
                                    Toast.makeText(context, failure?.let { "Errore stampa: ${it.message}" } ?: "Etichette inviate alla GoDEX", Toast.LENGTH_LONG).show()
                                }
                            }.start()
                        },
                        enabled = !printing && collo.items.isNotEmpty() && collo.items.all { it.articleCode.isNotBlank() && it.barcode.isNotBlank() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (printing) "🏷️ STAMPA..." else "🏷️ STAMPA GODEX CON PREZZO") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CHIUDI") } }
    )
}

private fun formatPieces(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.ITALY, "%.2f", value)

private fun formatHistoryDate(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrElse { raw.replace("T", " ").take(16) }
}