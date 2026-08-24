package com.scan2enter.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

private enum class InventoryView {
    SUMMARY,
    SUPPLIERS,
    MANUFACTURERS,
    FAMILIES,
    SUBFAMILIES,
    CATEGORIES,
    SUBCATEGORIES,
    ITEMS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryAnalysisScreen(onBack: () -> Unit) {
    val client = remember { GatewayApiClient() }

    var currentView by remember { mutableStateOf(InventoryView.SUMMARY) }

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
                limit = 5000
            )
        }.onSuccess {
            items = it
        }.onFailure {
            itemsError =
                it.message ?: "Errore lettura articoli Analisi Magazzino"
        }

        itemsLoading = false
    }

    val title = when (currentView) {
        InventoryView.SUMMARY -> "Analisi Magazzino"
        InventoryView.SUPPLIERS -> "Fornitori"
        InventoryView.MANUFACTURERS -> "Produttori / Marche"
        InventoryView.FAMILIES -> "Famiglie"
        InventoryView.SUBFAMILIES -> selectedFamily?.name ?: "Sottofamiglie"
        InventoryView.CATEGORIES -> selectedSubFamily?.name ?: "Categorie"
        InventoryView.SUBCATEGORIES -> selectedCategory?.name ?: "Sottocategorie"
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
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("←")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
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

                InventoryView.ITEMS -> {
                    ItemsView(
                        loading = itemsLoading,
                        error = itemsError,
                        items = items,
                        selectedRotation = selectedRotation,
                        selectedSupplier = selectedSupplier,
                        selectedManufacturer = selectedManufacturer,
                        selectedClassification = selectedSubCategory,
                        onRetry = { itemsReloadKey++ }
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
    onOpenFamilies: () -> Unit
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
private fun ItemsView(
    loading: Boolean,
    error: String?,
    items: List<InventoryAnalysisItemDto>,
    selectedRotation: InventoryRotationDto?,
    selectedSupplier: InventorySupplierSummaryDto?,
    selectedManufacturer: InventoryManufacturerSummaryDto?,
    selectedClassification: InventoryClassificationDto?,
    onRetry: () -> Unit
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

            items.forEach {
                InventoryItemCard(it)
            }
        }
    }
}

@Composable
private fun InventoryItemCard(
    item: InventoryAnalysisItemDto
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
        }
    }
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