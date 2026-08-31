package com.scan2enter.ui.screens

import android.content.Intent
import android.app.DatePickerDialog
import android.widget.Toast
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.api.InventoryAnalysisItemDto
import com.scan2enter.api.InventoryClassificationDto
import com.scan2enter.api.InventoryAnalysisSummaryDto
import com.scan2enter.api.InventoryManufacturerSummaryDto
import com.scan2enter.api.InventoryRotationDto
import com.scan2enter.api.InventorySupplierSummaryDto
import com.scan2enter.overlay.OverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

private enum class InventoryView {
    SUMMARY,
    SUPPLIERS,
    MANUFACTURERS,
    FAMILIES,
    SUBFAMILIES,
    CATEGORIES,
    SUBCATEGORIES,
    VALUATION,
    QUERY,
    QUERY_ITEMS,
    ITEMS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryAnalysisScreen(onBack: () -> Unit) {
    val client = remember { GatewayApiClient() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentView by remember { mutableStateOf(InventoryView.SUMMARY) }
    var reportLoading by remember { mutableStateOf(false) }
    var reportError by remember { mutableStateOf<String?>(null) }

    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<InventoryAnalysisSummaryDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    var suppliersLoading by remember { mutableStateOf(false) }
    var suppliersError by remember { mutableStateOf<String?>(null) }
    var suppliers by remember {
        mutableStateOf<List<InventorySupplierSummaryDto>>(emptyList())
    }
    var suppliersReloadKey by remember { mutableStateOf(0) }

    var manufacturersLoading by remember { mutableStateOf(false) }
    var manufacturersError by remember { mutableStateOf<String?>(null) }
    var manufacturers by remember {
        mutableStateOf<List<InventoryManufacturerSummaryDto>>(emptyList())
    }
    var manufacturersReloadKey by remember { mutableStateOf(0) }

    var classificationsLoading by remember { mutableStateOf(false) }
    var classificationsError by remember { mutableStateOf<String?>(null) }
    var classifications by remember {
        mutableStateOf<List<InventoryClassificationDto>>(emptyList())
    }
    var classificationsReloadKey by remember { mutableStateOf(0) }

    var selectedFamily by remember { mutableStateOf<InventoryClassificationDto?>(null) }
    var selectedSubFamily by remember { mutableStateOf<InventoryClassificationDto?>(null) }
    var selectedCategory by remember { mutableStateOf<InventoryClassificationDto?>(null) }
    var selectedSubCategory by remember { mutableStateOf<InventoryClassificationDto?>(null) }

    var selectedRotation by remember { mutableStateOf<InventoryRotationDto?>(null) }
    var selectedSupplier by remember { mutableStateOf<InventorySupplierSummaryDto?>(null) }
    var selectedManufacturer by remember { mutableStateOf<InventoryManufacturerSummaryDto?>(null) }

    var itemsLoading by remember { mutableStateOf(false) }
    var itemsError by remember { mutableStateOf<String?>(null) }
    var items by remember {
        mutableStateOf<List<InventoryAnalysisItemDto>>(emptyList())
    }
    var itemsReloadKey by remember { mutableStateOf(0) }

    var queryMode by remember { mutableStateOf<String?>(null) }
    var queryPeriodMonths by remember { mutableStateOf(12) }
    var queryItemsLoading by remember { mutableStateOf(false) }
    var queryItemsError by remember { mutableStateOf<String?>(null) }
    var queryItems by remember {
        mutableStateOf<List<InventoryAnalysisItemDto>>(emptyList())
    }
    var queryReloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null

        withContext(Dispatchers.IO) {
            client.getInventoryAnalysisSummary()
        }.onSuccess {
            summary = it
        }.onFailure {
            error = it.message ?: "Errore lettura Analisi Magazzino"
        }

        loading = false
    }

    LaunchedEffect(currentView, suppliersReloadKey) {
        if (currentView != InventoryView.SUPPLIERS) {
            return@LaunchedEffect
        }

        suppliersLoading = true
        suppliersError = null

        withContext(Dispatchers.IO) {
            client.getInventorySupplierSummary()
        }.onSuccess {
            suppliers = it
        }.onFailure {
            suppliersError =
                it.message ?: "Errore lettura fornitori Analisi Magazzino"
        }

        suppliersLoading = false
    }

    LaunchedEffect(currentView, manufacturersReloadKey) {
        if (currentView != InventoryView.MANUFACTURERS) {
            return@LaunchedEffect
        }

        manufacturersLoading = true
        manufacturersError = null

        withContext(Dispatchers.IO) {
            client.getInventoryManufacturerSummary()
        }.onSuccess {
            manufacturers = it
        }.onFailure {
            manufacturersError =
                it.message ?: "Errore lettura produttori Analisi Magazzino"
        }

        manufacturersLoading = false
    }

    LaunchedEffect(
        currentView,
        selectedFamily?.id,
        selectedSubFamily?.id,
        selectedCategory?.id,
        selectedSubCategory?.id,
        classificationsReloadKey
    ) {
        val dimension = when (currentView) {
            InventoryView.FAMILIES -> "family"
            InventoryView.SUBFAMILIES -> "subfamily"
            InventoryView.CATEGORIES -> "category"
            InventoryView.SUBCATEGORIES -> "subcategory"
            else -> null
        } ?: return@LaunchedEffect

        classificationsLoading = true
        classificationsError = null
        classifications = emptyList()

        withContext(Dispatchers.IO) {
            client.getInventoryClassifications(
                dimension = dimension,
                familyId = if (dimension == "family") null else selectedFamily?.id,
                subFamilyId = if (dimension in setOf("family", "subfamily")) null else selectedSubFamily?.id,
                categoryId = if (dimension in setOf("family", "subfamily", "category")) null else selectedCategory?.id,
                subCategoryId = null
            )
        }.onSuccess {
            classifications = it
        }.onFailure {
            classificationsError =
                it.message ?: "Errore lettura classificazioni Analisi Magazzino"
        }

        classificationsLoading = false
    }

    LaunchedEffect(
        currentView,
        selectedRotation?.rotationId,
        selectedSupplier?.supplierId,
        selectedManufacturer?.manufacturerId,
        selectedFamily?.id,
        selectedSubFamily?.id,
        selectedCategory?.id,
        selectedSubCategory?.id,
        itemsReloadKey
    ) {
        if (currentView != InventoryView.ITEMS) {
            return@LaunchedEffect
        }

        itemsLoading = true
        itemsError = null
        items = emptyList()

        withContext(Dispatchers.IO) {
            client.getInventoryAnalysisItems(
                rotationId = selectedRotation?.rotationId,
                supplierId = selectedSupplier?.supplierId,
                manufacturerId = selectedManufacturer?.manufacturerId,
                familyId = selectedFamily?.id,
                subFamilyId = selectedSubFamily?.id,
                categoryId = selectedCategory?.id,
                subCategoryId = selectedSubCategory?.id,
                limit = 50000
            )
        }.onSuccess {
            items = it
        }.onFailure {
            itemsError =
                it.message ?: "Errore lettura articoli Analisi Magazzino"
        }

        itemsLoading = false
    }


    LaunchedEffect(
        currentView,
        queryMode,
        queryPeriodMonths,
        queryReloadKey
    ) {
        if (currentView != InventoryView.QUERY_ITEMS) {
            return@LaunchedEffect
        }

        val mode = queryMode ?: return@LaunchedEffect

        queryItemsLoading = true
        queryItemsError = null
        queryItems = emptyList()

        withContext(Dispatchers.IO) {
            client.getInventoryAnalysisQuery(
                mode = mode,
                periodMonths = queryPeriodMonths,
                limit = 50000
            )
        }.onSuccess {
            queryItems = it
        }.onFailure {
            queryItemsError =
                it.message ?: "Errore Interroga Magazzino"
        }

        queryItemsLoading = false
    }

    val title = when (currentView) {
        InventoryView.SUMMARY -> "Analisi Magazzino"
        InventoryView.SUPPLIERS -> "Fornitori"
        InventoryView.MANUFACTURERS -> "Produttori / Marche"
        InventoryView.FAMILIES -> "Famiglie"
        InventoryView.SUBFAMILIES -> selectedFamily?.name ?: "Sottofamiglie"
        InventoryView.CATEGORIES -> selectedSubFamily?.name ?: "Categorie"
        InventoryView.SUBCATEGORIES -> selectedCategory?.name ?: "Sottocategorie"
        InventoryView.VALUATION -> "Valorizzazione Magazzino"
        InventoryView.QUERY -> "Interroga Magazzino"
        InventoryView.QUERY_ITEMS -> queryModeTitle(queryMode, queryPeriodMonths)
        InventoryView.ITEMS ->
            selectedRotation?.rotation
                ?: selectedSupplier?.supplier
                ?: selectedManufacturer?.manufacturer
                ?: selectedSubCategory?.name
                ?: selectedCategory?.name
                ?: selectedSubFamily?.name
                ?: selectedFamily?.name
                ?: "Articoli"
    }

    fun navigateBack() {
        when (currentView) {
            InventoryView.SUMMARY -> onBack()

            InventoryView.SUPPLIERS -> {
                currentView = InventoryView.SUMMARY
            }

            InventoryView.MANUFACTURERS -> {
                currentView = InventoryView.SUMMARY
            }

            InventoryView.FAMILIES -> {
                currentView = InventoryView.SUMMARY
            }

            InventoryView.SUBFAMILIES -> {
                selectedFamily = null
                currentView = InventoryView.FAMILIES
            }

            InventoryView.CATEGORIES -> {
                selectedSubFamily = null
                currentView = InventoryView.SUBFAMILIES
            }

            InventoryView.SUBCATEGORIES -> {
                selectedCategory = null
                currentView = InventoryView.CATEGORIES
            }

            InventoryView.VALUATION -> {
                currentView = InventoryView.SUMMARY
            }

            InventoryView.QUERY -> {
                currentView = InventoryView.SUMMARY
            }

            InventoryView.QUERY_ITEMS -> {
                queryItems = emptyList()
                queryItemsError = null
                queryMode = null
                currentView = InventoryView.QUERY
            }

            InventoryView.ITEMS -> {
                items = emptyList()
                itemsError = null

                if (selectedSupplier != null) {
                    selectedSupplier = null
                    currentView = InventoryView.SUPPLIERS
                } else if (selectedManufacturer != null) {
                    selectedManufacturer = null
                    currentView = InventoryView.MANUFACTURERS
                } else if (selectedSubCategory != null) {
                    selectedSubCategory = null
                    currentView = InventoryView.SUBCATEGORIES
                } else {
                    selectedRotation = null
                    currentView = InventoryView.SUMMARY
                }
            }
        }
    }

    BackHandler {
        navigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    Button(
                        onClick = {
                            navigateBack()
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("←")
                    }
                }
            )
        }
    ) { innerPadding ->
        val baseModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .navigationBarsPadding()
            .padding(12.dp)

        Column(
            modifier = if (
                currentView == InventoryView.ITEMS ||
                currentView == InventoryView.QUERY_ITEMS
            ) {
                baseModifier
            } else {
                baseModifier.verticalScroll(rememberScrollState())
            },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (currentView) {
                InventoryView.SUMMARY -> {
                    SummaryView(
                        loading = loading,
                        error = error,
                        summary = summary,
                        onRetry = { reloadKey++ },
                        onRotationClick = {
                            selectedRotation = it
                            selectedSupplier = null
                            selectedManufacturer = null
                            currentView = InventoryView.ITEMS
                        },
                        onOpenSuppliers = {
                            currentView = InventoryView.SUPPLIERS
                        },
                        onOpenManufacturers = {
                            currentView = InventoryView.MANUFACTURERS
                        },
                        onOpenFamilies = {
                            selectedFamily = null
                            selectedSubFamily = null
                            selectedCategory = null
                            selectedSubCategory = null
                            currentView = InventoryView.FAMILIES
                        },
                        onOpenValuation = {
                            currentView = InventoryView.VALUATION
                        },
                        onOpenQuery = {
                            queryMode = null
                            queryItems = emptyList()
                            currentView = InventoryView.QUERY
                        }
                    )
                }

                InventoryView.SUPPLIERS -> {
                    SuppliersView(
                        loading = suppliersLoading,
                        error = suppliersError,
                        suppliers = suppliers,
                        onRetry = { suppliersReloadKey++ },
                        onSupplierClick = {
                            selectedSupplier = it
                            selectedRotation = null
                            selectedManufacturer = null
                            currentView = InventoryView.ITEMS
                        }
                    )
                }

                InventoryView.MANUFACTURERS -> {
                    ManufacturersView(
                        loading = manufacturersLoading,
                        error = manufacturersError,
                        manufacturers = manufacturers,
                        onRetry = { manufacturersReloadKey++ },
                        onManufacturerClick = {
                            selectedManufacturer = it
                            selectedRotation = null
                            selectedSupplier = null
                            currentView = InventoryView.ITEMS
                        }
                    )
                }

                InventoryView.FAMILIES,
                InventoryView.SUBFAMILIES,
                InventoryView.CATEGORIES,
                InventoryView.SUBCATEGORIES -> {
                    val levelTitle = when (currentView) {
                        InventoryView.FAMILIES -> "Famiglie"
                        InventoryView.SUBFAMILIES -> "Sottofamiglie"
                        InventoryView.CATEGORIES -> "Categorie"
                        InventoryView.SUBCATEGORIES -> "Sottocategorie"
                        else -> ""
                    }

                    ClassificationView(
                        title = levelTitle,
                        loading = classificationsLoading,
                        error = classificationsError,
                        items = classifications,
                        onRetry = { classificationsReloadKey++ },
                        onItemClick = { item ->
                            when (currentView) {
                                InventoryView.FAMILIES -> {
                                    selectedFamily = item
                                    selectedSubFamily = null
                                    selectedCategory = null
                                    selectedSubCategory = null
                                    currentView = InventoryView.SUBFAMILIES
                                }

                                InventoryView.SUBFAMILIES -> {
                                    selectedSubFamily = item
                                    selectedCategory = null
                                    selectedSubCategory = null
                                    currentView = InventoryView.CATEGORIES
                                }

                                InventoryView.CATEGORIES -> {
                                    selectedCategory = item
                                    selectedSubCategory = null
                                    currentView = InventoryView.SUBCATEGORIES
                                }

                                InventoryView.SUBCATEGORIES -> {
                                    selectedSubCategory = item
                                    selectedRotation = null
                                    selectedSupplier = null
                                    selectedManufacturer = null
                                    currentView = InventoryView.ITEMS
                                }

                                else -> Unit
                            }
                        }
                    )
                }

                InventoryView.VALUATION -> {
                    InventoryValuationView(
                        reportLoading = reportLoading,
                        reportError = reportError,
                        onGenerateReport = { valuation, stockDate, showHealthBars,
                                             showLastSale, showSupplier,
                                             showManufacturer, showClassification ->
                            if (!reportLoading) {
                                reportLoading = true
                                reportError = null

                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        client.getInventoryAnalysisReportHtml(
                                            valuation = valuation,
                                            title = "VALORIZZAZIONE MAGAZZINO",
                                            stockDate = stockDate,
                                            showHealthBars = showHealthBars,
                                            showLastSale = showLastSale,
                                            showSupplier = showSupplier,
                                            showManufacturer = showManufacturer,
                                            showClassification = showClassification
                                        )
                                    }.onSuccess { html ->
                                        val extendedReport =
                                            showHealthBars ||
                                                    showLastSale ||
                                                    showSupplier ||
                                                    showManufacturer ||
                                                    showClassification

                                        printInventoryHtml(
                                            context = context,
                                            html = html,
                                            jobName = "Valorizzazione Magazzino - ${valuation.uppercase()}",
                                            landscape = extendedReport
                                        )
                                    }.onFailure { throwable ->
                                        reportError =
                                            throwable.message ?: "Errore generazione report"
                                    }

                                    reportLoading = false
                                }
                            }
                        }
                    )
                }

                InventoryView.QUERY -> {
                    InventoryQueryView(
                        onRunQuery = { mode, periodMonths ->
                            queryMode = mode
                            queryPeriodMonths = periodMonths
                            queryItemsError = null
                            currentView = InventoryView.QUERY_ITEMS
                        }
                    )
                }

                InventoryView.QUERY_ITEMS -> {
                    fun printQueryReport(valuation: String) {
                        if (reportLoading) return

                        val mode = queryMode ?: return
                        reportLoading = true
                        reportError = null

                        scope.launch {
                            withContext(Dispatchers.IO) {
                                client.getInventoryAnalysisReportHtml(
                                    valuation = valuation,
                                    title = "INTERROGA MAGAZZINO - ${queryModeTitle(mode, queryPeriodMonths)}",
                                    queryMode = mode,
                                    periodMonths = queryPeriodMonths
                                )
                            }.onSuccess { html ->
                                printInventoryHtml(
                                    context = context,
                                    html = html,
                                    jobName = "Interroga Magazzino - ${queryModeTitle(mode, queryPeriodMonths)} - ${valuation.uppercase()}"
                                )
                            }.onFailure { throwable ->
                                reportError =
                                    throwable.message ?: "Errore generazione report"
                            }

                            reportLoading = false
                        }
                    }

                    QueryItemsView(
                        loading = queryItemsLoading,
                        error = queryItemsError,
                        items = queryItems,
                        mode = queryMode.orEmpty(),
                        periodMonths = queryPeriodMonths,
                        reportLoading = reportLoading,
                        reportError = reportError,
                        onPrintFifo = { printQueryReport("fifo") },
                        onPrintPurchase = { printQueryReport("purchase") },
                        onRetry = { queryReloadKey++ },
                        onArticleClick = { item ->
                            if (item.barcode.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Questo articolo non ha un barcode utilizzabile",
                                    Toast.LENGTH_SHORT
                                ).show()
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
                            }
                        }
                    )
                }

                InventoryView.ITEMS -> {
                    fun printReport(valuation: String) {
                        if (reportLoading) return

                        reportLoading = true
                        reportError = null

                        scope.launch {
                            withContext(Dispatchers.IO) {
                                client.getInventoryAnalysisReportHtml(
                                    valuation = valuation,
                                    title = "ANALISI MAGAZZINO - $title",
                                    rotationId = selectedRotation?.rotationId,
                                    supplierId = selectedSupplier?.supplierId,
                                    manufacturerId = selectedManufacturer?.manufacturerId,
                                    familyId = selectedFamily?.id,
                                    subFamilyId = selectedSubFamily?.id,
                                    categoryId = selectedCategory?.id,
                                    subCategoryId = selectedSubCategory?.id
                                )
                            }.onSuccess { html ->
                                printInventoryHtml(
                                    context = context,
                                    html = html,
                                    jobName = "Analisi Magazzino - $title - ${valuation.uppercase()}"
                                )
                            }.onFailure { throwable ->
                                reportError =
                                    throwable.message ?: "Errore generazione report"
                            }

                            reportLoading = false
                        }
                    }

                    ItemsView(
                        loading = itemsLoading,
                        error = itemsError,
                        items = items,
                        selectedRotation = selectedRotation,
                        selectedSupplier = selectedSupplier,
                        selectedManufacturer = selectedManufacturer,
                        selectedClassification = selectedSubCategory,
                        reportLoading = reportLoading,
                        reportError = reportError,
                        onPrintFifo = { printReport("fifo") },
                        onPrintPurchase = { printReport("purchase") },
                        onRetry = { itemsReloadKey++ },
                        onArticleClick = { item ->
                            if (item.barcode.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Questo articolo non ha un barcode utilizzabile",
                                    Toast.LENGTH_SHORT
                                ).show()
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
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryView(
    loading: Boolean,
    error: String?,
    summary: InventoryAnalysisSummaryDto?,
    onRetry: () -> Unit,
    onRotationClick: (InventoryRotationDto) -> Unit,
    onOpenSuppliers: () -> Unit,
    onOpenManufacturers: () -> Unit,
    onOpenFamilies: () -> Unit,
    onOpenValuation: () -> Unit,
    onOpenQuery: () -> Unit
) {
    when {
        loading -> CenterLoader()

        error != null -> {
            ErrorCard(error, onRetry)
        }

        summary != null -> {
            Text(
                "${summary.articles} articoli con giacenza • " +
                        "${formatQuantity(summary.quantity)} unità",
                fontSize = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ValueCard(
                    "FIFO",
                    formatEuro(summary.fifoValue),
                    Modifier.weight(1f)
                )

                ValueCard(
                    "LISTINO ACQUISTO",
                    formatEuro(summary.purchaseListValue),
                    Modifier.weight(1f)
                )
            }

            if (summary.fifoCalculatedAt.isNotBlank()) {
                Text(
                    "FIFO calcolato: ${formatGatewayDate(summary.fifoCalculatedAt)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            Text(
                "Rotazione magazzino",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Tocca una fascia per vedere gli articoli",
                style = MaterialTheme.typography.bodySmall
            )

            summary.rotation
                .sortedBy { it.rotationId }
                .forEach { row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onRotationClick(row)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween
                            ) {
                                Text(
                                    row.rotation,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )

                                Text(
                                    "›",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            }

                            Text(
                                "${row.articles} articoli • " +
                                        "${formatQuantity(row.quantity)} unità"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween
                            ) {
                                Text("FIFO ${formatEuro(row.fifoValue)}")
                                Text("${formatPercent(row.fifoPercentage)}%")
                            }

                            Text(
                                "Listino ${formatEuro(row.purchaseListValue)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

            HorizontalDivider()

            Button(
                onClick = onOpenSuppliers,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("FORNITORI")
            }

            Button(
                onClick = onOpenManufacturers,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("PRODUTTORI / MARCHE")
            }

            Button(
                onClick = onOpenFamilies,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("FAMIGLIE")
            }

            HorizontalDivider()

            Button(
                onClick = onOpenQuery,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("INTERROGA MAGAZZINO")
            }

            Button(
                onClick = onOpenValuation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("VALORIZZAZIONE MAGAZZINO")
            }

            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("AGGIORNA DATI")
            }
        }
    }
}

@Composable
private fun InventoryValuationView(
    reportLoading: Boolean,
    reportError: String?,
    onGenerateReport: (
        valuation: String,
        stockDate: String?,
        showHealthBars: Boolean,
        showLastSale: Boolean,
        showSupplier: Boolean,
        showManufacturer: Boolean,
        showClassification: Boolean
    ) -> Unit
) {
    var valuation by remember { mutableStateOf("fifo") }
    var stockDate by remember { mutableStateOf("") }
    var showHealthBars by remember { mutableStateOf(true) }
    var showLastSale by remember { mutableStateOf(true) }
    var showSupplier by remember { mutableStateOf(true) }
    var showManufacturer by remember { mutableStateOf(false) }
    var showClassification by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Valorizzazione merce di magazzino",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Prepara una copia completa del magazzino in formato A4. " +
                    "Lascia vuota la data per usare la giacenza di oggi.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("GIACENZE", fontWeight = FontWeight.Bold)

                val context = LocalContext.current

                val displayedStockDate =
                    if (stockDate.isBlank()) {
                        "OGGI"
                    } else {
                        stockDate
                            .split("-")
                            .takeIf { it.size == 3 }
                            ?.let { parts ->
                                "${parts[2]}/${parts[1]}/${parts[0]}"
                            }
                            ?: stockDate
                    }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val today = Calendar.getInstance()

                            val initialDate = Calendar.getInstance().apply {
                                if (stockDate.isNotBlank()) {
                                    runCatching {
                                        val parts = stockDate.split("-")
                                        set(
                                            parts[0].toInt(),
                                            parts[1].toInt() - 1,
                                            parts[2].toInt()
                                        )
                                    }
                                }
                            }

                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    stockDate = String.format(
                                        Locale.ITALY,
                                        "%04d-%02d-%02d",
                                        year,
                                        month + 1,
                                        dayOfMonth
                                    )
                                },
                                initialDate.get(Calendar.YEAR),
                                initialDate.get(Calendar.MONTH),
                                initialDate.get(Calendar.DAY_OF_MONTH)
                            ).apply {
                                datePicker.maxDate = today.timeInMillis
                                show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📅 $displayedStockDate")
                    }

                    Button(
                        onClick = { stockDate = "" },
                        enabled = stockDate.isNotBlank()
                    ) {
                        Text("OGGI")
                    }
                }

                Text(
                    if (stockDate.isBlank()) {
                        "Giacenza corrente"
                    } else {
                        "Giacenza storica al $displayedStockDate"
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                HorizontalDivider()

                Text("VALORIZZAZIONE", fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { valuation = "fifo" },
                        enabled = valuation != "fifo",
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (valuation == "fifo") "✓ FIFO" else "FIFO")
                    }

                    Button(
                        onClick = { valuation = "purchase" },
                        enabled = valuation != "purchase",
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (valuation == "purchase") "✓ LISTINO"
                            else "LISTINO"
                        )
                    }
                }

                HorizontalDivider()

                Text("CONTENUTO REPORT", fontWeight = FontWeight.Bold)

                ReportOptionRow(
                    label = "Barre salute commerciale/economica",
                    checked = showHealthBars,
                    onCheckedChange = { showHealthBars = it }
                )
                ReportOptionRow(
                    label = "Ultima vendita",
                    checked = showLastSale,
                    onCheckedChange = { showLastSale = it }
                )
                ReportOptionRow(
                    label = "Fornitore",
                    checked = showSupplier,
                    onCheckedChange = { showSupplier = it }
                )
                ReportOptionRow(
                    label = "Produttore / marca",
                    checked = showManufacturer,
                    onCheckedChange = { showManufacturer = it }
                )
                ReportOptionRow(
                    label = "Classificazione",
                    checked = showClassification,
                    onCheckedChange = { showClassification = it }
                )
            }
        }

        Button(
            onClick = {
                onGenerateReport(
                    valuation,
                    stockDate.trim().ifBlank { null },
                    showHealthBars,
                    showLastSale,
                    showSupplier,
                    showManufacturer,
                    showClassification
                )
            },
            enabled = !reportLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (reportLoading) "PREPARAZIONE REPORT…"
                else "REPORT COMPLETO"
            )
        }

        reportError?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(
            "La stampa usa A4 reale. La numerazione pagine (es. 3 di 12) " +
                    "resta gestita dal report HTML del Gateway.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ReportOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}


@Composable
private fun SuppliersView(
    loading: Boolean,
    error: String?,
    suppliers: List<InventorySupplierSummaryDto>,
    onRetry: () -> Unit,
    onSupplierClick: (InventorySupplierSummaryDto) -> Unit
) {
    when {
        loading -> CenterLoader()

        error != null -> {
            ErrorCard(error, onRetry)
        }

        else -> {
            Text(
                "${suppliers.size} fornitori",
                fontWeight = FontWeight.Bold
            )

            suppliers
                .sortedByDescending { it.fifoValue }
                .forEach { supplier ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSupplierClick(supplier)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween
                            ) {
                                Text(
                                    supplier.supplier.ifBlank {
                                        "FORNITORE NON IDENTIFICATO"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )

                                Text(
                                    "›",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            }

                            Text(
                                "${supplier.articles} articoli • " +
                                        "${formatQuantity(supplier.quantity)} unità"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "FIFO ${formatEuro(supplier.fifoValue)}"
                                )

                                Text(
                                    "Listino ${formatEuro(supplier.purchaseListValue)}"
                                )
                            }
                        }
                    }
                }
        }
    }
}


@Composable
private fun ManufacturersView(
    loading: Boolean,
    error: String?,
    manufacturers: List<InventoryManufacturerSummaryDto>,
    onRetry: () -> Unit,
    onManufacturerClick: (InventoryManufacturerSummaryDto) -> Unit
) {
    when {
        loading -> CenterLoader()
        error != null -> ErrorCard(error, onRetry)
        else -> {
            Text(
                "${manufacturers.size} produttori / marche",
                fontWeight = FontWeight.Bold
            )

            manufacturers
                .sortedByDescending { it.fifoValue }
                .forEach { manufacturer ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onManufacturerClick(manufacturer) }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    manufacturer.manufacturer.ifBlank {
                                        "PRODUTTORE NON IDENTIFICATO"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text("›", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            }

                            Text(
                                "${manufacturer.articles} articoli • " +
                                        "${formatQuantity(manufacturer.quantity)} unità"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("FIFO ${formatEuro(manufacturer.fifoValue)}")
                                Text("Listino ${formatEuro(manufacturer.purchaseListValue)}")
                            }
                        }
                    }
                }
        }
    }
}


@Composable
private fun ClassificationView(
    title: String,
    loading: Boolean,
    error: String?,
    items: List<InventoryClassificationDto>,
    onRetry: () -> Unit,
    onItemClick: (InventoryClassificationDto) -> Unit
) {
    when {
        loading -> CenterLoader()
        error != null -> ErrorCard(error, onRetry)
        else -> {
            Text(
                "${items.size} $title",
                fontWeight = FontWeight.Bold
            )

            items
                .sortedByDescending { it.fifoValue }
                .forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item) }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    item.name.ifBlank { "NON IDENTIFICATO" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text("›", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            }

                            Text(
                                "${item.articles} articoli • " +
                                        "${formatQuantity(item.quantity)} unità"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("FIFO ${formatEuro(item.fifoValue)}")
                                Text("Listino ${formatEuro(item.purchaseListValue)}")
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun InventoryQueryView(
    onRunQuery: (mode: String, periodMonths: Int) -> Unit
) {
    var periodMonths by remember { mutableStateOf(12) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Interroga il magazzino per articolo",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Ogni risultato apre il popup completo dell'articolo. " +
                    "Le interrogazioni usano solo articoli attivi con giacenza positiva.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("PERIODO", fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(3, 6, 12, 24).forEach { months ->
                        Button(
                            onClick = { periodMonths = months },
                            enabled = periodMonths != months,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (periodMonths == months) "✓ $months M"
                                else "$months M",
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(36, 60).forEach { months ->
                        Button(
                            onClick = { periodMonths = months },
                            enabled = periodMonths != months,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (periodMonths == months) "✓ $months MESI"
                                else "$months MESI"
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { onRunQuery("never-sold", 12) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("MAI VENDUTI")
        }

        Text(
            "Articoli con giacenza che non risultano mai venduti nello storico.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = { onRunQuery("top-sold", periodMonths) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("PIÙ VENDUTI · $periodMonths MESI")
        }

        Text(
            "Classifica per quantità realmente venduta nel periodo scelto.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = { onRunQuery("stopped", periodMonths) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("FERMI DA ALMENO $periodMonths MESI")
        }

        Text(
            "Articoli venduti almeno una volta, ma senza vendite da oltre il periodo scelto.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = { onRunQuery("dead-capital", 12) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CAPITALE FERMO")
        }

        Text(
            "Articoli problematici ordinati per valore FIFO immobilizzato.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}


@Composable
private fun QueryItemsView(
    loading: Boolean,
    error: String?,
    items: List<InventoryAnalysisItemDto>,
    mode: String,
    periodMonths: Int,
    reportLoading: Boolean,
    reportError: String?,
    onPrintFifo: () -> Unit,
    onPrintPurchase: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (InventoryAnalysisItemDto) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            queryModeDescription(mode, periodMonths),
            style = MaterialTheme.typography.bodySmall
        )

        if (!loading && error == null) {
            Text(
                "${items.size} articoli",
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider()

        when {
            loading -> CenterLoader()

            error != null -> ErrorCard(error, onRetry)

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = items,
                        key = { it.articleId }
                    ) { item ->
                        QueryInventoryItemCard(
                            item = item,
                            mode = mode,
                            periodMonths = periodMonths,
                            onClick = { onArticleClick(item) }
                        )
                    }
                }
            }
        }

        if (reportError != null) {
            Text(
                reportError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPrintFifo,
                enabled = !reportLoading && !loading && error == null && items.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (reportLoading) "ATTENDI..."
                    else "STAMPA FIFO",
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onPrintPurchase,
                enabled = !reportLoading && !loading && error == null && items.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (reportLoading) "ATTENDI..."
                    else "STAMPA LISTINO",
                    fontSize = 12.sp
                )
            }
        }
    }
}


@Composable
private fun QueryInventoryItemCard(
    item: InventoryAnalysisItemDto,
    mode: String,
    periodMonths: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                item.description.ifBlank { "Senza descrizione" },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            if (item.articleCode.isNotBlank()) {
                Text(
                    item.articleCode,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Giacenza ${formatQuantity(item.quantity)}")
                Text("FIFO ${formatEuro(item.fifoValue)}")
            }

            when (mode) {
                "never-sold" -> {
                    Text(
                        "Mai venduto nello storico",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Capitale fermo ${formatEuro(item.fifoValue)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                "top-sold" -> {
                    Text(
                        "Venduto $periodMonths mesi: ${formatQuantity(item.soldPeriod)}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Venduto storico: ${formatQuantity(item.soldHistorical)} • " +
                                queryCoverageText(item.monthsCoverage),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                "stopped" -> {
                    Text(
                        if (item.lastSaleDate.isBlank()) {
                            "Ultima vendita: -"
                        } else {
                            "Ultima vendita: ${formatGatewayDate(item.lastSaleDate)}"
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Venduto storico: ${formatQuantity(item.soldHistorical)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                "dead-capital" -> {
                    Text(
                        "Capitale FIFO fermo: ${formatEuro(item.fifoValue)}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Venduto 12M: ${formatQuantity(item.sold12M)} • " +
                                queryCoverageText(item.monthsCoverage),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (item.lastSaleDate.isNotBlank()) {
                        Text(
                            "Ultima vendita: ${formatGatewayDate(item.lastSaleDate)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            "Mai venduto nello storico",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (item.supplier.isNotBlank()) {
                Text(
                    "Fornitore: ${item.supplier}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    modifier = Modifier.width(190.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InventoryHealthBar(
                        label = "COMM.",
                        score = item.commercialScore,
                        description = item.commercialDescription
                    )
                    InventoryHealthBar(
                        label = "ECON.",
                        score = item.economicScore,
                        description = item.economicDescription
                    )
                }
            }
        }
    }
}


private fun queryModeTitle(
    mode: String?,
    periodMonths: Int
): String =
    when (mode) {
        "never-sold" -> "Mai venduti"
        "top-sold" -> "Più venduti · $periodMonths mesi"
        "stopped" -> "Fermi da $periodMonths mesi"
        "dead-capital" -> "Capitale fermo"
        else -> "Interroga Magazzino"
    }


private fun queryModeDescription(
    mode: String,
    periodMonths: Int
): String =
    when (mode) {
        "never-sold" ->
            "Articoli con giacenza e nessuna vendita nello storico."
        "top-sold" ->
            "Articoli ordinati per quantità venduta negli ultimi $periodMonths mesi."
        "stopped" ->
            "Articoli venduti in passato e fermi da almeno $periodMonths mesi."
        "dead-capital" ->
            "Articoli economicamente problematici ordinati per capitale FIFO immobilizzato."
        else -> ""
    }


private fun queryCoverageText(monthsCoverage: Double?): String =
    if (monthsCoverage == null) {
        "Copertura: -"
    } else {
        "Copertura: ${formatQuantity(monthsCoverage)} mesi"
    }


@Composable
private fun ItemsView(
    loading: Boolean,
    error: String?,
    items: List<InventoryAnalysisItemDto>,
    selectedRotation: InventoryRotationDto?,
    selectedSupplier: InventorySupplierSummaryDto?,
    selectedManufacturer: InventoryManufacturerSummaryDto?,
    selectedClassification: InventoryClassificationDto?,
    reportLoading: Boolean,
    reportError: String?,
    onPrintFifo: () -> Unit,
    onPrintPurchase: () -> Unit,
    onRetry: () -> Unit,
    onArticleClick: (InventoryAnalysisItemDto) -> Unit

) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        selectedRotation?.let {
            Text(
                "${it.articles} articoli • " +
                        "${formatQuantity(it.quantity)} unità",
                fontSize = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ValueCard(
                    "FIFO",
                    formatEuro(it.fifoValue),
                    Modifier.weight(1f)
                )

                ValueCard(
                    "LISTINO",
                    formatEuro(it.purchaseListValue),
                    Modifier.weight(1f)
                )
            }
        }

        selectedSupplier?.let {
            Text(
                "${it.articles} articoli • " +
                        "${formatQuantity(it.quantity)} unità",
                fontSize = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ValueCard(
                    "FIFO",
                    formatEuro(it.fifoValue),
                    Modifier.weight(1f)
                )

                ValueCard(
                    "LISTINO",
                    formatEuro(it.purchaseListValue),
                    Modifier.weight(1f)
                )
            }
        }


        selectedManufacturer?.let {
            Text(
                "${it.articles} articoli • ${formatQuantity(it.quantity)} unità",
                fontSize = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ValueCard("FIFO", formatEuro(it.fifoValue), Modifier.weight(1f))
                ValueCard("LISTINO", formatEuro(it.purchaseListValue), Modifier.weight(1f))
            }
        }


        selectedClassification?.let {
            Text(
                "${it.articles} articoli • ${formatQuantity(it.quantity)} unità",
                fontSize = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ValueCard("FIFO", formatEuro(it.fifoValue), Modifier.weight(1f))
                ValueCard("LISTINO", formatEuro(it.purchaseListValue), Modifier.weight(1f))
            }
        }


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPrintFifo,
                enabled = !reportLoading && !loading && error == null && items.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (reportLoading) "ATTENDI…" else "STAMPA FIFO")
            }

            Button(
                onClick = onPrintPurchase,
                enabled = !reportLoading && !loading && error == null && items.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (reportLoading) "ATTENDI…" else "STAMPA LISTINO")
            }
        }

        reportError?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        HorizontalDivider()

        when {
            loading -> CenterLoader()

            error != null -> {
                ErrorCard(error, onRetry)
            }

            else -> {
                Text(
                    "${items.size} articoli caricati",
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = items,
                        key = { it.articleId }
                    ) { item ->
                        InventoryItemCard(
                            item = item,
                            onClick = { onArticleClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryItemCard(
    item: InventoryAnalysisItemDto,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                item.description.ifBlank {
                    "Senza descrizione"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            if (item.articleCode.isNotBlank()) {
                Text(
                    item.articleCode,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Giacenza ${formatQuantity(item.quantity)}"
                )
                Text(
                    "FIFO ${formatEuro(item.fifoValue)}"
                )
            }

            Text(
                "Listino ${formatEuro(item.purchaseListValue)}",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                if (item.lastSaleDate.isBlank()) {
                    "Ultima vendita: nessuna"
                } else {
                    "Ultima vendita: " +
                            formatGatewayDate(item.lastSaleDate)
                },
                style = MaterialTheme.typography.bodySmall
            )

            if (item.supplier.isNotBlank()) {
                Text(
                    "Fornitore: ${item.supplier}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (item.manufacturer.isNotBlank()) {
                Text(
                    "Produttore: ${item.manufacturer}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    modifier = Modifier.width(190.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InventoryHealthBar(
                        label = "COMM.",
                        score = item.commercialScore,
                        description = item.commercialDescription
                    )
                    InventoryHealthBar(
                        label = "ECON.",
                        score = item.economicScore,
                        description = item.economicDescription
                    )
                }
            }
        }
    }
}


@Composable
private fun InventoryHealthBar(
    label: String,
    score: Int,
    description: String
) {
    val safeScore = score.coerceIn(0, 100)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$safeScore/100",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        LinearProgressIndicator(
            progress = { safeScore / 100f },
            modifier = Modifier.fillMaxWidth()
        )

        if (description.isNotBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

private fun printInventoryHtml(
    context: Context,
    html: String,
    jobName: String,
    landscape: Boolean = false
) {
    val webView = WebView(context)

    webView.settings.javaScriptEnabled = false
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val printManager =
                context.getSystemService(Context.PRINT_SERVICE) as PrintManager

            val adapter = view.createPrintDocumentAdapter(jobName)

            val mediaSize =
                if (landscape) {
                    PrintAttributes.MediaSize.ISO_A4.asLandscape()
                } else {
                    PrintAttributes.MediaSize.ISO_A4.asPortrait()
                }

            printManager.print(
                jobName,
                adapter,
                PrintAttributes.Builder()
                    .setMediaSize(mediaSize)
                    .build()
            )
        }
    }

    webView.loadDataWithBaseURL(
        null,
        html,
        "text/html",
        "UTF-8",
        null
    )
}


@Composable
private fun CenterLoader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Button(
                onClick = onRetry
            ) {
                Text("RIPROVA")
            }
        }
    }
}

@Composable
private fun ValueCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatEuro(value: Double): String =
    NumberFormat
        .getCurrencyInstance(Locale.ITALY)
        .format(value)

private fun formatQuantity(value: Double): String =
    NumberFormat
        .getNumberInstance(Locale.ITALY)
        .apply {
            maximumFractionDigits = 3
        }
        .format(value)

private fun formatPercent(value: Double): String =
    NumberFormat
        .getNumberInstance(Locale.ITALY)
        .apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        .format(value)

private fun formatGatewayDate(value: String): String =
    value
        .replace("T", " ")
        .substringBefore(".")
        .take(16)