package com.scan2enter.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
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
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.api.CustomerDto
import com.scan2enter.api.SessionColloItemDto
import com.scan2enter.session.SessionItem
import com.scan2enter.session.SessionStore
import com.scan2enter.session.SessionCustomerStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import com.scan2enter.scanner.ScannerModeDetector
private const val SESSION_UI_PREFS = "session_ui_prefs"
private const val KEY_SEARCH_ON_LEFT = "search_on_left"

@Composable
fun SessionScreen(
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenColloHistory: () -> Unit
) {
    val context = LocalContext.current

    var sessionItems by remember {
        mutableStateOf(SessionStore.getItems())
    }

    val sessionListState = rememberLazyListState()

    val firstSessionArticleId =
        sessionItems.firstOrNull()?.articleId

    LaunchedEffect(firstSessionArticleId) {
        if (
            firstSessionArticleId != null &&
            sessionItems.isNotEmpty()
        ) {
            sessionListState.animateScrollToItem(0)
        }
    }

    var editingItem by remember {
        mutableStateOf<SessionItem?>(null)
    }

    var itemPendingDelete by remember {
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

    val currentCustomer = SessionCustomerStore.current.value

    var customerDialogOpen by remember {
        mutableStateOf(false)
    }

    var customerQuery by remember {
        mutableStateOf("")
    }

    var customerResults by remember {
        mutableStateOf<List<CustomerDto>>(emptyList())
    }

    var customerLoading by remember {
        mutableStateOf(false)
    }

    var customerError by remember {
        mutableStateOf<String?>(null)
    }

    val gatewayApiClient = remember {
        GatewayApiClient()
    }

    fun searchCustomers() {
        customerLoading = true
        customerError = null

        Thread {
            val result = gatewayApiClient.getCustomers(customerQuery)

            Handler(Looper.getMainLooper()).post {
                customerLoading = false

                result.onSuccess {
                    customerResults = it
                }.onFailure { error ->
                    customerResults = emptyList()
                    customerError =
                        error.message ?: "Errore ricerca clienti"
                }
            }
        }.start()
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

    DisposableEffect(Unit) {
        context.startService(
            Intent(
                context,
                OverlayService::class.java
            ).apply {
                action =
                    OverlayService.ACTION_CLOSE_SCANNER
            }
        )

        onDispose { }
    }

    DisposableEffect(Unit) {
        var lastBarcode = ""
        var lastScanAt = 0L

        fun routeHardwareBarcode(
            barcode: String,
            source: String
        ) {
            val normalizedBarcode =
                barcode.trim()

            if (normalizedBarcode.isBlank()) {
                return
            }

            val now =
                android.os.SystemClock.elapsedRealtime()

            if (
                normalizedBarcode == lastBarcode &&
                now - lastScanAt < 600L
            ) {
                android.util.Log.d(
                    "SessionScreen",
                    "DOPPIA LETTURA IGNORATA " +
                            "source=$source barcode=$normalizedBarcode"
                )
                return
            }

            lastBarcode = normalizedBarcode
            lastScanAt = now

            android.util.Log.d(
                "SessionScreen",
                "LETTURA HARDWARE SESSIONE " +
                        "source=$source barcode=$normalizedBarcode"
            )

            context.startService(
                Intent(
                    context,
                    OverlayService::class.java
                ).apply {
                    action =
                        OverlayService.ACTION_OPEN_SEARCH_ARTICLE

                    putExtra(
                        OverlayService.EXTRA_CURRENT_ARTICLE_BARCODE,
                        normalizedBarcode
                    )

                    putExtra(
                        OverlayService.EXTRA_SUPPRESS_PRODUCT_POPUP,
                        true
                    )
                }
            )
        }

        val sunmiReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    receiverContext: Context?,
                    intent: Intent?
                ) {
                    if (
                        intent?.action !=
                        "com.honeywell.tools.action.scan_result"
                    ) {
                        return
                    }

                    val barcode =
                        intent.getStringExtra("barcode_data")
                            ?.trim()
                            .orEmpty()

                    routeHardwareBarcode(
                        barcode = barcode,
                        source = "SUNMI"
                    )
                }
            }

        val zebraReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    receiverContext: Context?,
                    intent: Intent?
                ) {
                    if (
                        intent?.action !=
                        "com.scan2enter.SCAN"
                    ) {
                        return
                    }

                    val barcode =
                        intent.getStringExtra(
                            "com.symbol.datawedge.data_string"
                        )
                            ?.trim()
                            .orEmpty()

                    routeHardwareBarcode(
                        barcode = barcode,
                        source = "ZEBRA"
                    )
                }
            }

        if (ScannerModeDetector.isSunmi()) {
            context.registerReceiver(
                sunmiReceiver,
                IntentFilter(
                    "com.honeywell.tools.action.scan_result"
                )
            )
        }

        androidx.core.content.ContextCompat.registerReceiver(
            context,
            zebraReceiver,
            IntentFilter("com.scan2enter.SCAN"),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )

        onDispose {
            if (ScannerModeDetector.isSunmi()) {
                runCatching {
                    context.unregisterReceiver(
                        sunmiReceiver
                    )
                }
            }

            runCatching {
                context.unregisterReceiver(
                    zebraReceiver
                )
            }
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
                        },
                        onOpenColloHistory = onOpenColloHistory
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
                        text = "📦 COLLO VELOCE",
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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "👤 Cliente",
                            fontSize = 13.sp
                        )

                        Text(
                            text = currentCustomer.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Spacer(
                        modifier = Modifier.width(10.dp)
                    )

                    Button(
                        onClick = {
                            customerDialogOpen = true
                            customerQuery = ""
                            customerResults = emptyList()
                            customerError = null
                        }
                    ) {
                        Text(
                            text = "CAMBIA",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            if (sessionItems.isEmpty()) {
                Text(
                    text =
                        "Il collo veloce è vuoto.\n" +
                                "Usa il grilletto laterale o CERCA.",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 18.sp
                )
            } else {
                LazyColumn(
                    state = sessionListState,
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
                            onDoubleClick = {
                                context.startService(
                                    Intent(
                                        context,
                                        OverlayService::class.java
                                    ).apply {
                                        action =
                                            OverlayService.ACTION_OPEN_SESSION_ARTICLE_DETAIL

                                        putExtra(
                                            OverlayService.EXTRA_CURRENT_ARTICLE_BARCODE,
                                            item.barcode
                                        )
                                    }
                                )
                            },
                            onIncrement = {
                                SessionStore.setQuantity(
                                    articleId = item.articleId,
                                    quantity = (item.quantity + 1)
                                        .coerceAtMost(9999)
                                )
                            },
                            onDecrement = {
                                if (item.quantity <= 1) {
                                    itemPendingDelete = item
                                } else {
                                    SessionStore.setQuantity(
                                        articleId = item.articleId,
                                        quantity = item.quantity - 1
                                    )
                                }
                            },
                            onRemove = {
                                itemPendingDelete = item
                            }
                        )
                    }
                }
            }
        }
    }

    if (customerDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                customerDialogOpen = false
            },
            title = {
                Text("Scegli cliente")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                SessionCustomerStore.useBanco()
                                customerDialogOpen = false
                                customerQuery = ""
                                customerResults = emptyList()
                                customerError = null
                            },
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "🏪 BANCO",
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Cliente predefinito",
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    OutlinedTextField(
                        value = customerQuery,
                        onValueChange = {
                            customerQuery = it
                        },
                        label = {
                            Text("Nome cliente")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            searchCustomers()
                        },
                        enabled = !customerLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (customerLoading) {
                                "RICERCA..."
                            } else {
                                "CERCA"
                            }
                        )
                    }

                    customerError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (!customerLoading &&
                        customerResults.isEmpty() &&
                        customerQuery.isNotBlank() &&
                        customerError == null
                    ) {
                        Text("Nessun cliente trovato")
                    }

                    if (customerResults.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.height(260.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                customerResults,
                                key = { it.id }
                            ) { customer ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            SessionCustomerStore.setCustomer(
                                                id = customer.id,
                                                name = customer.name
                                            )
                                            customerDialogOpen = false
                                            customerQuery = ""
                                            customerResults = emptyList()
                                            customerError = null
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    tonalElevation = 2.dp
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = customer.name.ifBlank {
                                                "Cliente ${customer.id}"
                                            },
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            text =
                                                "ID ${customer.id} • Listino ${customer.priceListId}",
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        customerDialogOpen = false
                        customerQuery = ""
                        customerResults = emptyList()
                        customerError = null
                    }
                ) {
                    Text("CHIUDI")
                }
            }
        )
    }

    itemPendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = {
                itemPendingDelete = null
            },
            containerColor = Color(0xFFFFDADA),
            title = {
                Text(
                    text = "⚠ ELIMINAZIONE ARTICOLO",
                    color = Color(0xFFB00020),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sto per eliminare dal collo veloce:",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111)
                    )

                    Text(
                        text = item.articleCode.ifBlank {
                            "Articolo ${item.articleId}"
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB00020)
                    )

                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            fontSize = 16.sp,
                            color = Color(0xFF111111)
                        )
                    }

                    Text(
                        text = "Vuoi proseguire?",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        itemPendingDelete = null
                    }
                ) {
                    Text(
                        text = "ANNULLA",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        SessionStore.remove(item.articleId)
                        itemPendingDelete = null
                    }
                ) {
                    Text(
                        text = "ELIMINA",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    editingItem?.let { item ->
        QuantityDialog(
            item = item,
            onDismiss = {
                editingItem = null
            },
            onOpenArticle = {
                editingItem = null

                context.startService(
                    Intent(
                        context,
                        OverlayService::class.java
                    ).apply {
                        action =
                            OverlayService.ACTION_OPEN_SESSION_ARTICLE_DETAIL

                        putExtra(
                            OverlayService.EXTRA_CURRENT_ARTICLE_BARCODE,
                            item.barcode
                        )
                    }
                )
            },
            onSave = { qty, manualPrice ->
                SessionStore.setQuantityAndManualPrice(
                    articleId = item.articleId,
                    quantity = qty,
                    manualPrice = manualPrice
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
    onClose: () -> Unit,
    onOpenColloHistory: () -> Unit
) {
    val context = LocalContext.current
    val customer = SessionCustomerStore.current.value

    var sendingCollo by remember {
        mutableStateOf(false)
    }

    var sendError by remember {
        mutableStateOf<String?>(null)
    }

    var createdCollo by remember {
        mutableStateOf<com.scan2enter.api.CreateColloResultDto?>(null)
    }

    var colloCreatedAt by remember {
        mutableStateOf("")
    }

    var printingColloLabel by remember {
        mutableStateOf(false)
    }

    var colloLabelMessage by remember {
        mutableStateOf<String?>(null)
    }

    var clearSessionConfirmOpen by remember {
        mutableStateOf(false)
    }

    var colloNote by remember {
        mutableStateOf(SessionStore.getNote())
    }

    var deleteNoteConfirmOpen by remember {
        mutableStateOf(false)
    }

    val gatewayApiClient = remember {
        GatewayApiClient()
    }

    val totalPieces =
        items.sumOf { it.quantity }

    val totalEuro =
        items.sumOf { item ->
            val unitPrice =
                item.effectivePrice
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
                        text = "COLLO VELOCE PRONTO",
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
                        text = "Cliente: ${customer.name}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = colloNote,
                    onValueChange = { value ->
                        val limited = value.take(4000)
                        colloNote = limited
                        SessionStore.setNote(limited)
                    },
                    label = {
                        Text("📝 Nota collo")
                    },
                    placeholder = {
                        Text("Testo libero, es. consegnare venerdì")
                    },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.weight(1f)
                )

                if (colloNote.isNotBlank()) {
                    Text(
                        text = "✕",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB00020),
                        modifier = Modifier
                            .clickable {
                                deleteNoteConfirmOpen = true
                            }
                            .padding(
                                start = 10.dp,
                                top = 12.dp,
                                end = 4.dp,
                                bottom = 12.dp
                            )
                    )
                }
            }

            Text(
                text = "${colloNote.length}/4000",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            sendError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }

            Button(
                onClick = {
                    val payloadItems =
                        items.mapNotNull { item ->
                            val price =
                                item.effectivePrice
                                    .replace(",", ".")
                                    .toDoubleOrNull()

                            if (
                                item.barcode.isBlank() ||
                                item.quantity <= 0 ||
                                price == null ||
                                price < 0.0
                            ) {
                                null
                            } else {
                                SessionColloItemDto(
                                    barcode = item.barcode,
                                    quantity = item.quantity,
                                    price = price
                                )
                            }
                        }

                    if (payloadItems.size != items.size) {
                        sendError =
                            "Una o più righe hanno barcode, quantità o prezzo non validi."
                        return@Button
                    }

                    sendingCollo = true
                    sendError = null

                    Thread {
                        val result =
                            gatewayApiClient.createSessionCollo(
                                clientId = customer.id,
                                items = payloadItems,
                                note = colloNote
                            )

                        Handler(Looper.getMainLooper()).post {
                            sendingCollo = false

                            result.onSuccess { created ->
                                colloCreatedAt =
                                    SimpleDateFormat(
                                        "dd/MM/yy HH:mm",
                                        Locale.ITALY
                                    ).format(Date())

                                colloLabelMessage = null
                                createdCollo = created
                            }.onFailure { error ->
                                sendError =
                                    error.message
                                        ?: "Errore creazione collo"
                            }
                        }
                    }.start()
                },
                enabled =
                    items.isNotEmpty() &&
                            !sendingCollo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (sendingCollo) {
                        "📦  CREAZIONE COLLO..."
                    } else {
                        "📦  INVIA COLLO"
                    }
                )
            }

            Button(
                onClick = onOpenColloHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📚  STORICO COLLI")
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

            TextButton(
                onClick = {
                    clearSessionConfirmOpen = true
                },
                enabled = items.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🗑️  SVUOTA TUTTO IL COLLO VELOCE",
                    color = Color(0xFFB00020),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (deleteNoteConfirmOpen) {
        AlertDialog(
            onDismissRequest = {
                deleteNoteConfirmOpen = false
            },
            title = {
                Text(
                    text = "⚠ ELIMINA NOTA COLLO",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB00020)
                )
            },
            text = {
                Text(
                    "Vuoi eliminare la nota dal collo veloce corrente? " +
                            "Gli articoli non verranno modificati."
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteNoteConfirmOpen = false
                    }
                ) {
                    Text("ANNULLA")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        colloNote = ""
                        SessionStore.clearNote()
                        deleteNoteConfirmOpen = false
                    }
                ) {
                    Text("ELIMINA NOTA")
                }
            }
        )
    }

    if (clearSessionConfirmOpen) {
        AlertDialog(
            onDismissRequest = {
                clearSessionConfirmOpen = false
            },
            containerColor = Color(0xFFFFDADA),
            title = {
                Text(
                    text = "⚠ SVUOTA COLLO VELOCE",
                    color = Color(0xFFB00020),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Stai per eliminare TUTTI gli articoli del collo veloce.",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111)
                    )

                    Text(
                        text = "${items.size} articoli • ${items.sumOf { it.quantity }} pezzi",
                        fontSize = 17.sp,
                        color = Color(0xFF111111)
                    )

                    Text(
                        text = "Questa operazione non può essere annullata.",
                        color = Color(0xFFB00020),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clearSessionConfirmOpen = false
                    }
                ) {
                    Text(
                        text = "ANNULLA",
                        color = Color(0xFF111111),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        SessionCustomerStore.useBanco()
                        SessionStore.clear()
                        clearSessionConfirmOpen = false
                        onClose()
                    }
                ) {
                    Text(
                        text = "SVUOTA TUTTO",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    createdCollo?.let { created ->
        val numeroCollo = created.numeroCollo
        val colloBarcode = created.barcodeCollo

        AlertDialog(
            onDismissRequest = {
                // Restiamo volutamente qui: il barcode deve poter essere letto in cassa.
            },
            title = {
                Text(
                    text = "✅ COLLO CREATO",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Cliente: ${customer.name}",
                        fontSize = 15.sp
                    )

                    Text(
                        text = "Collo n. $numeroCollo",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "LEGGI QUESTO CODICE IN CASSA",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Ean13Barcode(
                        code = colloBarcode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(118.dp)
                    )

                    Text(
                        text = colloBarcode,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    colloLabelMessage?.let { message ->
                        Text(
                            text = message,
                            fontSize = 13.sp,
                            color =
                                if (message.startsWith("✅")) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                        )
                    }

                    Button(
                        onClick = {
                            if (printingColloLabel) {
                                return@Button
                            }

                            printingColloLabel = true
                            colloLabelMessage = null

                            val labelCustomer =
                                customer.name
                                    .trim()
                                    .ifBlank { "BANCO" }

                            val labelDateTime =
                                colloCreatedAt.ifBlank {
                                    SimpleDateFormat(
                                        "dd/MM/yy HH:mm",
                                        Locale.ITALY
                                    ).format(Date())
                                }

                            Thread {
                                val printResult =
                                    gatewayApiClient.printLabel(
                                        articleCode = labelCustomer,
                                        description = labelDateTime,
                                        barcode = colloBarcode,
                                        publicPrice = "",
                                        quantity = 1,
                                        printer = "GODEX",
                                        template = "STANDARD",
                                        note = ""
                                    )

                                Handler(
                                    Looper.getMainLooper()
                                ).post {
                                    printingColloLabel = false

                                    printResult
                                        .onSuccess {
                                            colloLabelMessage =
                                                "✅ Etichetta collo stampata"
                                        }
                                        .onFailure { error ->
                                            colloLabelMessage =
                                                "Errore stampa: " +
                                                        (error.message
                                                            ?: "errore sconosciuto")
                                        }
                                }
                            }.start()
                        },
                        enabled = !printingColloLabel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (printingColloLabel) {
                                "🏷️ STAMPA IN CORSO..."
                            } else {
                                "🏷️ STAMPA ETICHETTA COLLO"
                            }
                        )
                    }

                    Text(
                        text =
                            "La sessione resta disponibile finché non scegli di chiuderla.",
                        fontSize = 13.sp
                    )

                    Button(
                        onClick = {
                            createdCollo = null
                            onOpenColloHistory()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📚 APRI STORICO COLLI")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        createdCollo = null
                        SessionCustomerStore.useBanco()
                        SessionStore.clear()
                        onClose()
                    }
                ) {
                    Text("FATTO • SVUOTA COLLO VELOCE")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        createdCollo = null
                    }
                ) {
                    Text("TORNA AL COLLO VELOCE")
                }
            }
        )
    }
}

@Composable
private fun Ean13Barcode(
    code: String,
    modifier: Modifier = Modifier
) {
    val modules =
        remember(code) {
            encodeEan13Modules(code)
        }

    Canvas(
        modifier = modifier
            .background(Color.White)
            .padding(
                horizontal = 12.dp,
                vertical = 10.dp
            )
    ) {
        if (modules.isEmpty()) {
            return@Canvas
        }

        val quietModules = 10
        val totalModules =
            modules.length + quietModules * 2

        val moduleWidth =
            size.width / totalModules.toFloat()

        val normalHeight =
            size.height * 0.82f

        val guardHeight =
            size.height

        modules.forEachIndexed { index, bit ->
            if (bit != '1') {
                return@forEachIndexed
            }

            val moduleIndex =
                index + quietModules

            val isGuard =
                index in 0..2 ||
                        index in 45..49 ||
                        index in 92..94

            drawRect(
                color = Color.Black,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = moduleIndex * moduleWidth,
                    y = 0f
                ),
                size = androidx.compose.ui.geometry.Size(
                    width = moduleWidth + 0.5f,
                    height =
                        if (isGuard) {
                            guardHeight
                        } else {
                            normalHeight
                        }
                )
            )
        }
    }
}

private fun encodeEan13Modules(
    code: String
): String {
    if (
        code.length != 13 ||
        !code.all(Char::isDigit)
    ) {
        return ""
    }

    val lPatterns = arrayOf(
        "0001101",
        "0011001",
        "0010011",
        "0111101",
        "0100011",
        "0110001",
        "0101111",
        "0111011",
        "0110111",
        "0001011"
    )

    val gPatterns = arrayOf(
        "0100111",
        "0110011",
        "0011011",
        "0100001",
        "0011101",
        "0111001",
        "0000101",
        "0010001",
        "0001001",
        "0010111"
    )

    val rPatterns = arrayOf(
        "1110010",
        "1100110",
        "1101100",
        "1000010",
        "1011100",
        "1001110",
        "1010000",
        "1000100",
        "1001000",
        "1110100"
    )

    val parity = arrayOf(
        "LLLLLL",
        "LLGLGG",
        "LLGGLG",
        "LLGGGL",
        "LGLLGG",
        "LGGLLG",
        "LGGGLL",
        "LGLGLG",
        "LGLGGL",
        "LGGLGL"
    )

    val first =
        code[0].digitToInt()

    val result =
        StringBuilder(95)

    result.append("101")

    for (index in 1..6) {
        val digit =
            code[index].digitToInt()

        result.append(
            if (parity[first][index - 1] == 'L') {
                lPatterns[digit]
            } else {
                gPatterns[digit]
            }
        )
    }

    result.append("01010")

    for (index in 7..12) {
        val digit =
            code[index].digitToInt()

        result.append(
            rPatterns[digit]
        )
    }

    result.append("101")

    return result.toString()
}


@Composable
private fun ScanActionButton(
    modifier: Modifier,
    onClick: () -> Unit,
    onSwap: () -> Unit
) {
    Box(
        modifier = modifier
            .height(96.dp)
            .background(
                color = Color(0xFF1B5E20),
                shape = RoundedCornerShape(18.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🟢 SCANNER ATTIVO",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "Premi il grilletto",
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
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
    onDoubleClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(item.articleId) {
                detectTapGestures(
                    onDoubleTap = {
                        onDoubleClick()
                    },
                    onTap = {
                        onClick()
                    }
                )
            },
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "✕",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB00020),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clickable(onClick = onRemove)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 10.dp
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 14.dp,
                        end = 46.dp,
                        top = 14.dp,
                        bottom = 14.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.manualPrice.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .width(7.dp)
                            .height(92.dp)
                            .background(
                                color = Color(0xFFD50000),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )

                    Spacer(
                        modifier = Modifier.width(10.dp)
                    )
                }

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

                    val finalPriceValue =
                        item.effectivePrice
                            .replace(",", ".")
                            .toDoubleOrNull()

                    val listPriceValue =
                        item.listPrice
                            .ifBlank { item.publicPrice }
                            .replace(",", ".")
                            .toDoubleOrNull()

                    val finalPriceText =
                        finalPriceValue?.let {
                            String.format(
                                Locale.ITALY,
                                "%.2f €",
                                it
                            )
                        } ?: "—"

                    val listPriceText =
                        listPriceValue?.let {
                            String.format(
                                Locale.ITALY,
                                "%.2f €",
                                it
                            )
                        } ?: "—"

                    if (item.priceListName.isNotBlank()) {
                        Text(
                            text = "🏷️ ${item.priceListName}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }

                    val hasDiscount =
                        item.discount1 > 0.0 &&
                                listPriceValue != null &&
                                finalPriceValue != null

                    Text(
                        text =
                            if (hasDiscount) {
                                val discountText =
                                    if (item.discount1 % 1.0 == 0.0) {
                                        item.discount1
                                            .toInt()
                                            .toString()
                                    } else {
                                        String.format(
                                            Locale.ITALY,
                                            "%.1f",
                                            item.discount1
                                        )
                                    }

                                "💰 $listPriceText → " +
                                        "$finalPriceText (-$discountText%)"
                            } else {
                                "💰 $finalPriceText"
                            },
                        fontSize = 14.sp,
                        fontWeight =
                            if (hasDiscount || item.manualPrice.isNotBlank()) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            }
                    )

                    if (item.manualPrice.isNotBlank()) {
                        Text(
                            text = "⚠ PREZZO MODIFICATO",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD50000)
                        )
                    }

                    Text(
                        text =
                            "📦 Giacenza: " +
                                    item.stock.ifBlank { "—" },
                        fontSize = 13.sp
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "x${item.quantity}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .clickable(onClick = onDecrement),
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 4.dp
                        ) {
                            Text(
                                text = "−",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 3.dp
                                )
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .clickable(onClick = onIncrement),
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 4.dp
                        ) {
                            Text(
                                text = "+",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 3.dp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuantityDialog(
    item: SessionItem,
    onDismiss: () -> Unit,
    onOpenArticle: () -> Unit,
    onSave: (Int, String) -> Unit
) {
    var quantityText by remember(item.articleId) {
        mutableStateOf(item.quantity.toString())
    }

    val proposedPrice =
        item.finalPrice.ifBlank {
            item.publicPrice
        }

    var priceText by remember(item.articleId, item.manualPrice) {
        mutableStateOf(
            item.manualPrice.ifBlank {
                proposedPrice
            }
        )
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
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (item.priceListName.isNotBlank()) {
                    Text(
                        text = "🏷️ ${item.priceListName}",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (item.listPrice.isNotBlank()) {
                    Text(
                        text = "Prezzo listino: ${
                            formatPriceText(item.listPrice)
                        }"
                    )
                }

                if (item.discount1 > 0.0) {
                    Text(
                        text = "Sconto: ${
                            formatDiscount(item.discount1)
                        }"
                    )
                }

                Text(
                    text = "Prezzo proposto: ${
                        formatPriceText(proposedPrice)
                    }",
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text("Quantità")

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val value =
                                quantityText.toIntOrNull() ?: 1

                            quantityText =
                                (value - 1)
                                    .coerceAtLeast(0)
                                    .toString()
                        }
                    ) {
                        Text("−")
                    }

                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { value ->
                            if (
                                value.isEmpty() ||
                                value.all(Char::isDigit)
                            ) {
                                quantityText = value
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
                            val value =
                                quantityText.toIntOrNull() ?: 0

                            quantityText =
                                (value + 1)
                                    .coerceAtMost(9999)
                                    .toString()
                        }
                    ) {
                        Text("+")
                    }
                }

                Text(
                    text = "Prezzo collo",
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { value ->
                        if (
                            value.isEmpty() ||
                            value.matches(
                                Regex("""\d{0,6}([,.]\d{0,2})?""")
                            )
                        ) {
                            priceText = value
                        }
                    },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Decimal
                        ),
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = {
                        priceText = proposedPrice
                    }
                ) {
                    Text(
                        "RIPRISTINA ${
                            formatPriceText(proposedPrice)
                        }"
                    )
                }

                if (
                    priceText.trim()
                        .replace(",", ".") !=
                    proposedPrice.trim()
                        .replace(",", ".")
                ) {
                    Text(
                        text = "✏️ Prezzo modificato manualmente",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = "0 quantità rimuove l'articolo.",
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val quantity =
                        (quantityText.toIntOrNull() ?: 0)
                            .coerceIn(0, 9999)

                    val normalizedPrice =
                        priceText.trim()
                            .replace(",", ".")

                    val proposedNormalized =
                        proposedPrice.trim()
                            .replace(",", ".")

                    val manualPrice =
                        if (
                            normalizedPrice.isBlank() ||
                            normalizedPrice ==
                            proposedNormalized
                        ) {
                            ""
                        } else {
                            normalizedPrice
                        }

                    onSave(
                        quantity,
                        manualPrice
                    )
                }
            ) {
                Text("SALVA")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onOpenArticle
                ) {
                    Text("ⓘ APRI ARTICOLO")
                }

                TextButton(
                    onClick = onDismiss
                ) {
                    Text("ANNULLA")
                }
            }
        }
    )
}

private fun formatPriceText(
    value: String
): String {
    val number =
        value.trim()
            .replace(",", ".")
            .toDoubleOrNull()
            ?: return "—"

    return String.format(
        Locale.ITALY,
        "%.2f €",
        number
    )
}

private fun formatDiscount(
    value: Double
): String {
    return if (value % 1.0 == 0.0) {
        "${value.toInt()}%"
    } else {
        String.format(
            Locale.ITALY,
            "%.1f%%",
            value
        )
    }
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