package com.scan2enter.promotions

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.api.ProductPromotionListItemDto
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PromotionsScreen(
    onBack: () -> Unit,
    onPromotionSelected: (String) -> Unit,
    onNewPromotion: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val api = remember { GatewayApiClient() }

    var selectedStatus by remember { mutableStateOf<String?>( "ATTIVE" ) }
    var query by remember { mutableStateOf("") }
    var appliedQuery by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<ProductPromotionListItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    /*
     * L'editor PROMO è un overlay del servizio: tornando visibile/attiva
     * questa schermata ricarichiamo l'elenco dal Gateway, così SALVA ed
     * ELIMINA si riflettono subito senza uscire e rientrare nel modulo.
     */
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadKey++
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(selectedStatus, appliedQuery, reloadKey) {
        isLoading = true
        errorMessage = null

        val result = withContext(Dispatchers.IO) {
            api.getPromotions(
                status = selectedStatus,
                query = appliedQuery
            )
        }

        result
            .onSuccess {
                items = it
            }
            .onFailure {
                items = emptyList()
                errorMessage = it.message ?: "Errore Gateway"
            }

        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "PROMOZIONI",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Gestione offerte Scan2Enter",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(onClick = onNewPromotion) {
                Text("+ NUOVA PROMO")
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PromoFilterButton(
                text = "IN CORSO",
                selected = selectedStatus == "ATTIVE",
                modifier = Modifier.weight(1f)
            ) {
                selectedStatus = "ATTIVE"
            }

            PromoFilterButton(
                text = "PROGRAMMATE",
                selected = selectedStatus == "PROGRAMMATA",
                modifier = Modifier.weight(1f)
            ) {
                selectedStatus = "PROGRAMMATA"
            }

            PromoFilterButton(
                text = "SCADUTE",
                selected = selectedStatus == "SCADUTA",
                modifier = Modifier.weight(1f)
            ) {
                selectedStatus = "SCADUTA"
            }

            PromoFilterButton(
                text = "TUTTE",
                selected = selectedStatus == null,
                modifier = Modifier.weight(1f)
            ) {
                selectedStatus = null
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Codice, descrizione, barcode...") }
            )

            Button(
                onClick = {
                    appliedQuery = query.trim()
                    reloadKey++
                }
            ) {
                Text("CERCA")
            }
        }

        Spacer(Modifier.height(10.dp))

        when {
            isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Text(
                    text = "Errore caricamento promozioni\n${errorMessage.orEmpty()}",
                    modifier = Modifier.padding(top = 20.dp)
                )
            }

            items.isEmpty() -> {
                Text(
                    text = "Nessuna promozione trovata",
                    modifier = Modifier.padding(top = 20.dp)
                )
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(
                        items = items,
                        key = { it.articleId }
                    ) { promo ->
                        PromotionCard(
                            promo = promo,
                            onClick = {
                                val barcode = promo.barcode.trim()

                                if (barcode.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "Barcode non disponibile per ${promo.code}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    onPromotionSelected(barcode)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromoFilterButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = !selected
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PromotionCard(
    promo: ProductPromotionListItemDto,
    onClick: () -> Unit
) {
    val money = remember { NumberFormat.getCurrencyInstance(Locale.ITALY) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = promo.description.ifBlank { "(senza descrizione)" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = buildString {
                    append(promo.code)
                    if (promo.barcode.isNotBlank()) {
                        append("  •  ${promo.barcode}")
                    }
                },
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(7.dp))

            Text(
                text = "${money.format(promo.publicPrice)}  →  ${money.format(promo.offerPrice)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Sconto ${formatPercent(promo.discountPercent)}%  •  ${prettyStatus(promo.status)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = formatValidity(promo),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatValidity(
    promo: ProductPromotionListItemDto
): String {
    val from = formatDate(promo.validFrom)
    val to = formatDate(promo.validTo)

    return when {
        from == null && to == null -> "Senza scadenza"
        from != null && to != null -> "Dal $from al $to"
        from != null -> "Dal $from"
        else -> "Fino al $to"
    }
}

private fun formatDate(value: String?): String? {
    if (value.isNullOrBlank()) return null

    return runCatching {
        LocalDateTime.parse(value)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrElse {
        value.take(10)
            .split("-")
            .takeIf { it.size == 3 }
            ?.let { "${it[2]}/${it[1]}/${it[0]}" }
            ?: value
    }
}

private fun formatPercent(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.ITALY, "%.2f", value)
            .trimEnd('0')
            .trimEnd(',')
    }

private fun prettyStatus(status: String): String =
    when (status.uppercase()) {
        "IN_CORSO" -> "IN CORSO"
        "SENZA_SCADENZA" -> "SENZA SCADENZA"
        "PROGRAMMATA" -> "PROGRAMMATA"
        "SCADUTA" -> "SCADUTA"
        "ATTIVE" -> "ATTIVA"
        else -> status.replace('_', ' ')
    }
