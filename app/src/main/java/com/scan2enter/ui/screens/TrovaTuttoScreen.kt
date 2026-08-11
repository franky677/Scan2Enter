package com.scan2enter.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scan2enter.overlay.OverlayService
import com.scan2enter.search.GatewaySearchClient
import com.scan2enter.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun TrovaTuttoScreen(
    onBack: () -> Unit,
    onArticleOpened: (() -> Unit)? = null,
    onArticleSelected: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val client = remember { GatewaySearchClient() }

    var query by remember { mutableStateOf("") }
    var results by remember {
        mutableStateOf<List<SearchResult>>(emptyList())
    }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(query) {
        val normalized = query.trim()

        if (normalized.length < 2) {
            results = emptyList()
            loading = false
            errorMessage = null
            return@LaunchedEffect
        }

        delay(300L)
        loading = true
        errorMessage = null

        val result = withContext(Dispatchers.IO) {
            client.search(normalized)
        }

        result.onSuccess {
            results = it
        }.onFailure {
            results = emptyList()
            errorMessage = it.message ?: "Errore durante la ricerca"
        }

        loading = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "‹",
                    fontSize = 42.sp,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(
                            horizontal = 12.dp,
                            vertical = 2.dp
                        )
                )

                Text(
                    text = "🔎 TROVATUTTO",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text("Cerca articolo...")
                },
                placeholder = {
                    Text("Codice, barcode o descrizione")
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                )
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            when {
                query.trim().length < 2 -> {
                    Text(
                        text = "Scrivi almeno 2 caratteri.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                results.isEmpty() -> {
                    Text(
                        text = "Nessun articolo trovato.",
                        modifier = Modifier.padding(8.dp)
                    )
                }

                else -> {
                    Text(
                        text = "${results.size} risultati",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = 4.dp,
                            vertical = 6.dp
                        )
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = results,
                            key = { it.id }
                        ) { item ->
                            TrovaTuttoResultRow(
                                item = item,
                                onClick = {
                                    if (item.barcode.isBlank()) {
                                        Toast.makeText(
                                            context,
                                            "Questo articolo non ha un barcode utilizzabile",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        if (onArticleSelected != null) {
                                            onArticleSelected(item.barcode)
                                        } else {
                                            context.startService(
                                                Intent(
                                                    context,
                                                    OverlayService::class.java
                                                ).apply {
                                                    action =
                                                        OverlayService.ACTION_OPEN_SEARCH_ARTICLE

                                                    putExtra(
                                                        OverlayService.EXTRA_CURRENT_ARTICLE_BARCODE,
                                                        item.barcode
                                                    )
                                                }
                                            )

                                            onArticleOpened?.invoke()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrovaTuttoResultRow(
    item: SearchResult,
    onClick: () -> Unit
) {
    val backgroundColor =
        if (item.moved) {
            Color(0xFFFFF59D)
        } else {
            MaterialTheme.colorScheme.surface
        }

    val mainTextColor =
        if (item.moved) Color.Black
        else MaterialTheme.colorScheme.onSurface

    val secondaryTextColor =
        if (item.moved) Color(0xFF424242)
        else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.code.ifBlank { "Codice non disponibile" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = mainTextColor,
                    modifier = Modifier.weight(1f)
                )

                if (item.moved) {
                    Text(
                        text = "MOVIMENTATO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF795548)
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text = item.description.ifBlank {
                    "Articolo senza descrizione"
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = mainTextColor
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            val priceText =
                item.price.toDoubleOrNull()
                    ?.let {
                        String.format(
                            Locale.ITALY,
                            "%.2f €",
                            it
                        )
                    }
                    ?: "—"

            Text(
                text = "💰 $priceText",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = mainTextColor
            )

            Text(
                text = "📦 Giacenza: ${
                    item.stock.ifBlank { "—" }
                }",
                fontSize = 15.sp,
                color = mainTextColor
            )

            if (item.barcode.isNotBlank()) {
                Text(
                    text = "EAN: ${item.barcode}",
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
