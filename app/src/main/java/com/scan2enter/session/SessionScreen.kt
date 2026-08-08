package com.scan2enter.ui.screens

import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scan2enter.overlay.OverlayService
import com.scan2enter.session.SessionItem
import com.scan2enter.session.SessionStore
import java.util.Locale
import kotlin.math.abs

private const val SESSION_UI_PREFS = "session_ui_prefs"
private const val KEY_SEARCH_ON_LEFT = "search_on_left"

@Composable
fun SessionScreen(
    onBack: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val context = LocalContext.current

    var sessionItems by remember {
        mutableStateOf(SessionStore.getItems())
    }

    var editingItem by remember {
        mutableStateOf<SessionItem?>(null)
    }

    var searchOnLeft by remember {
        mutableStateOf(
            context.getSharedPreferences(
                SESSION_UI_PREFS,
                Context.MODE_PRIVATE
            ).getBoolean(KEY_SEARCH_ON_LEFT, false)
        )
    }

    var actionPanelOpen by remember {
        mutableStateOf(false)
    }

    DisposableEffect(Unit) {
        val listener: (List<SessionItem>) -> Unit = {
            sessionItems = it
        }

        SessionStore.addListener(listener)

        onDispose {
            SessionStore.removeListener(listener)
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = actionPanelOpen
                ) {
                    SessionActionPanel(
                        items = sessionItems,
                        onClose = {
                            actionPanelOpen = false
                        }
                    )
                }

                SessionBottomBar(
                    searchOnLeft = searchOnLeft,
                    sessionCount = sessionItems.size,
                    onSessionClick = {
                        actionPanelOpen = !actionPanelOpen
                    },
                    onSwap = {
                        searchOnLeft = !searchOnLeft

                        context.getSharedPreferences(
                            SESSION_UI_PREFS,
                            Context.MODE_PRIVATE
                        ).edit()
                            .putBoolean(
                                KEY_SEARCH_ON_LEFT,
                                searchOnLeft
                            )
                            .apply()

                        vibrateSwap(context)
                    },
                    onScan = {
                        context.startService(
                            Intent(
                                context,
                                OverlayService::class.java
                            ).apply {
                                action = OverlayService.ACTION_OPEN_SCANNER
                            }
                        )
                    },
                    onSearch = onOpenSearch
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 12.dp
                )
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

                Column {
                    Text(
                        text = "📋 SESSIONE",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            "${sessionItems.size} articoli • " +
                                    "${sessionItems.sumOf { it.quantity }} pezzi",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            if (sessionItems.isEmpty()) {
                Text(
                    text =
                        "La sessione è vuota.\n" +
                                "Usa SCANSIONA o CERCA qui sotto.",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 18.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(
                        sessionItems,
                        key = { it.articleId }
                    ) { item ->
                        SessionRow(
                            item = item,
                            onClick = {
                                editingItem = item
                            },
                            onRemove = {
                                SessionStore.remove(item.articleId)
                            }
                        )
                    }
                }
            }
        }
    }

    editingItem?.let { item ->
        QuantityDialog(
            item = item,
            onDismiss = {
                editingItem = null
            },
            onSave = { qty ->
                SessionStore.setQuantity(
                    item.articleId,
                    qty
                )
                editingItem = null
            }
        )
    }
}

@Composable
private fun SessionBottomBar(
    searchOnLeft: Boolean,
    sessionCount: Int,
    onSessionClick: () -> Unit,
    onSwap: () -> Unit,
    onScan: () -> Unit,
    onSearch: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (searchOnLeft) {
                SearchActionButton(
                    modifier = Modifier.weight(4.5f),
                    onClick = onSearch,
                    onSwap = onSwap
                )

                ScanActionButton(
                    modifier = Modifier.weight(4.5f),
                    onClick = onScan,
                    onSwap = onSwap
                )
            } else {
                ScanActionButton(
                    modifier = Modifier.weight(4.5f),
                    onClick = onScan,
                    onSwap = onSwap
                )

                SearchActionButton(
                    modifier = Modifier.weight(4.5f),
                    onClick = onSearch,
                    onSwap = onSwap
                )
            }

            SessionActionButton(
                modifier = Modifier.weight(1f),
                count = sessionCount,
                onClick = onSessionClick
            )
        }
    }
}

@Composable
private fun SessionActionButton(
    modifier: Modifier,
    count: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(96.dp)
            .background(
                color = Color(0xFF424242),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📋",
                fontSize = 24.sp
            )

            Text(
                text = if (count > 99) "99+" else count.toString(),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SessionActionPanel(
    items: List<SessionItem>,
    onClose: () -> Unit
) {
    val totalPieces =
        items.sumOf { it.quantity }

    val totalEuro =
        items.sumOf { item ->
            val unitPrice =
                item.publicPrice
                    .replace(",", ".")
                    .toDoubleOrNull()
                    ?: 0.0

            unitPrice * item.quantity
        }

    Surface(
        tonalElevation = 10.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SESSIONE PRONTA",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            "${items.size} articoli • " +
                                    "$totalPieces pezzi",
                        fontSize = 15.sp
                    )

                    Text(
                        text =
                            "Totale: " +
                                    String.format(
                                        Locale.ITALY,
                                        "%.2f €",
                                        totalEuro
                                    ),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "✕",
                    fontSize = 26.sp,
                    modifier = Modifier
                        .clickable(onClick = onClose)
                        .padding(8.dp)
                )
            }

            Button(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📦  INVIA COLLO")
            }

            Button(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🏷️  GODEX")
            }

            Button(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📄  ETICHETTE A4")
            }

            Button(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💾  SALVA LISTA")
            }
        }
    }
}

@Composable
private fun ScanActionButton(
    modifier: Modifier,
    onClick: () -> Unit,
    onSwap: () -> Unit
) {
    GradientActionButton(
        modifier = modifier,
        text = "🔫  SCANSIONA",
        gradient = Brush.horizontalGradient(
            listOf(
                Color(0xFF0D47A1),
                Color(0xFF42A5F5)
            )
        ),
        onClick = onClick,
        onSwap = onSwap
    )
}

@Composable
private fun SearchActionButton(
    modifier: Modifier,
    onClick: () -> Unit,
    onSwap: () -> Unit
) {
    GradientActionButton(
        modifier = modifier,
        text = "🔎  CERCA",
        gradient = Brush.horizontalGradient(
            listOf(
                Color(0xFF1B5E20),
                Color(0xFF66BB6A)
            )
        ),
        onClick = onClick,
        onSwap = onSwap
    )
}

@Composable
private fun GradientActionButton(
    modifier: Modifier,
    text: String,
    gradient: Brush,
    onClick: () -> Unit,
    onSwap: () -> Unit
) {
    var accumulatedDragX by remember {
        mutableFloatStateOf(0f)
    }

    Box(
        modifier = modifier
            .height(96.dp)
            .background(
                brush = gradient,
                shape = RoundedCornerShape(18.dp)
            )
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        accumulatedDragX = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragX += dragAmount.x
                    },
                    onDragEnd = {
                        if (abs(accumulatedDragX) > 90f) {
                            onSwap()
                        }
                        accumulatedDragX = 0f
                    },
                    onDragCancel = {
                        accumulatedDragX = 0f
                    }
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SessionRow(
    item: SessionItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.description.ifBlank {
                        "Articolo senza descrizione"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                if (item.articleCode.isNotBlank()) {
                    Text(
                        text = item.articleCode,
                        fontSize = 14.sp
                    )
                }

                val price =
                    item.publicPrice
                        .replace(",", ".")
                        .toDoubleOrNull()
                        ?.let {
                            String.format(
                                Locale.ITALY,
                                "%.2f €",
                                it
                            )
                        }
                        ?: "—"

                Text(
                    text =
                        "💰 $price   •   📦 " +
                                item.stock.ifBlank { "—" },
                    fontSize = 14.sp
                )
            }

            Text(
                text = "x${item.quantity}",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    horizontal = 12.dp
                )
            )

            Text(
                text = "✕",
                fontSize = 24.sp,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun QuantityDialog(
    item: SessionItem,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var text by remember(item.articleId) {
        mutableStateOf(item.quantity.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = item.description.ifBlank {
                    item.articleCode
                }
            )
        },
        text = {
            Column {
                Text("Quantità sessione")

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val v = text.toIntOrNull() ?: 1
                            text =
                                (v - 1)
                                    .coerceAtLeast(0)
                                    .toString()
                        }
                    ) {
                        Text("−")
                    }

                    OutlinedTextField(
                        value = text,
                        onValueChange = { value ->
                            if (
                                value.isEmpty() ||
                                value.all(Char::isDigit)
                            ) {
                                text = value
                            }
                        },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Number
                            ),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            val v = text.toIntOrNull() ?: 0
                            text =
                                (v + 1)
                                    .coerceAtMost(9999)
                                    .toString()
                        }
                    ) {
                        Text("+")
                    }
                }

                Text(
                    text = "0 rimuove l'articolo.",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        (text.toIntOrNull() ?: 0)
                            .coerceIn(0, 9999)
                    )
                }
            ) {
                Text("SALVA")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("ANNULLA")
            }
        }
    )
}

private fun vibrateSwap(context: Context) {
    val vibrator =
        context.getSystemService(
            Context.VIBRATOR_SERVICE
        ) as? Vibrator
            ?: return

    runCatching {
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                45L,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }
}
