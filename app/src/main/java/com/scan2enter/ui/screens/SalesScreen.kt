package com.scan2enter.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.api.SalesSectionDto
import com.scan2enter.api.SalesSummaryDto
import com.scan2enter.sales.SalesAccessManager
import com.scan2enter.ui.components.SalesChangePasswordDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

private val SALES_YEARS = listOf(2023, 2024, 2025, 2026)

@Composable
fun SalesScreen(
    onBack: () -> Unit
) {
    val apiClient = remember { GatewayApiClient() }
    val context = LocalContext.current

    val salesAccessManager = remember {
        SalesAccessManager(context)
    }

    var showChangePasswordDialog by remember {
        mutableStateOf(false)
    }

    var selectedYear by remember {
        mutableStateOf(2026)
    }

    var summary by remember {
        mutableStateOf<SalesSummaryDto?>(null)
    }

    val comparison = remember {
        mutableStateMapOf<Int, SalesSummaryDto>()
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var comparisonLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var reloadToken by remember {
        mutableStateOf(0)
    }

    LaunchedEffect(selectedYear, reloadToken) {
        isLoading = true
        errorMessage = null

        val result = withContext(Dispatchers.IO) {
            apiClient.getSalesSummary(selectedYear)
        }

        result
            .onSuccess {
                summary = it
                comparison[selectedYear] = it
            }
            .onFailure {
                summary = null
                errorMessage =
                    it.message ?: "Errore durante il caricamento delle vendite"
            }

        isLoading = false
    }

    LaunchedEffect(reloadToken) {
        comparisonLoading = true

        withContext(Dispatchers.IO) {
            SALES_YEARS.forEach { year ->
                apiClient.getSalesSummary(year)
                    .onSuccess {
                        comparison[year] = it
                    }
            }
        }

        comparisonLoading = false
    }

    if (showChangePasswordDialog) {
        SalesChangePasswordDialog(
            accessManager = salesAccessManager,
            onDismiss = {
                showChangePasswordDialog = false
            },
            onChanged = {
                showChangePasswordDialog = false
            }
        )
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBack
                ) {
                    Text("← INDIETRO")
                }

                Text(
                    text = "VENDITE",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
            }

            OutlinedButton(
                onClick = {
                    showChangePasswordDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔒 CAMBIA PASSWORD")
            }

            YearSelector(
                selectedYear = selectedYear,
                onYearSelected = {
                    selectedYear = it
                }
            )

            when {
                isLoading -> {
                    LoadingBlock(
                        text = "Caricamento vendite $selectedYear..."
                    )
                }

                errorMessage != null -> {
                    ErrorBlock(
                        message = errorMessage.orEmpty(),
                        onRetry = {
                            reloadToken++
                        }
                    )
                }

                summary != null -> {
                    val data = summary!!

                    Text(
                        text = buildPeriodText(data),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SalesSummaryCard(
                        title = "CORRISPETTIVI",
                        icon = "🧾",
                        data = data.receipts
                    )

                    SalesSummaryCard(
                        title = "FATTURE",
                        icon = "📄",
                        data = data.invoices
                    )

                    SalesSummaryCard(
                        title = "TOTALE",
                        icon = "📊",
                        data = data.total,
                        emphasized = true
                    )
                }
            }

            ComparisonCard(
                comparison = comparison,
                loading = comparisonLoading
            )

            YearOverYearCard(
                comparison = comparison,
                selectedYear = selectedYear
            )

            Button(
                onClick = {
                    reloadToken++
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        MaterialTheme.colorScheme.secondaryContainer,
                    contentColor =
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text(
                    text = "↻ AGGIORNA DATI",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )
        }
    }
}

@Composable
private fun YearSelector(
    selectedYear: Int,
    onYearSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SALES_YEARS.forEach { year ->
            val selected = year == selectedYear

            if (selected) {
                Button(
                    onClick = {
                        onYearSelected(year)
                    }
                ) {
                    Text(
                        text = year.toString(),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = {
                        onYearSelected(year)
                    }
                ) {
                    Text(year.toString())
                }
            }
        }
    }
}

@Composable
private fun LoadingBlock(
    text: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        Text(
            text = text,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun ErrorBlock(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Impossibile caricare le vendite",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                text = message,
                color =
                    MaterialTheme.colorScheme.onErrorContainer
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
private fun SalesSummaryCard(
    title: String,
    icon: String,
    data: SalesSectionDto,
    emphasized: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                if (emphasized) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (emphasized) 6.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Text(
                text = "$icon  $title",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            SalesValueRow(
                label = "Documenti",
                value = formatInteger(data.documents)
            )

            SalesValueRow(
                label = "Vendite imponibili",
                value = formatEuro(data.salesTaxable),
                strong = true
            )

            SalesValueRow(
                label = "Costo",
                value = formatEuro(data.cost)
            )

            SalesValueRow(
                label = "Differenza",
                value = formatEuro(data.difference),
                strong = true
            )

            SalesValueRow(
                label = "Ricarico sul costo",
                value = formatPercent(data.markupPercent),
                strong = true
            )
        }
    }
}

@Composable
private fun SalesValueRow(
    label: String,
    value: String,
    strong: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp
        )

        Text(
            text = value,
            fontSize = if (strong) 18.sp else 16.sp,
            fontWeight =
                if (strong) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ComparisonCard(
    comparison: Map<Int, SalesSummaryDto>,
    loading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "CONFRONTO ANNI",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Totale vendite nello stesso periodo",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (loading && comparison.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                SALES_YEARS.forEach { year ->
                    val item = comparison[year]

                    if (item != null) {
                        ComparisonYearRow(
                            year = year,
                            data = item.total
                        )
                    } else {
                        Text(
                            text = "$year  — dati non disponibili",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonYearRow(
    year: Int,
    data: SalesSectionDto
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = year.toString(),
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = formatEuro(data.salesTaxable),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text =
                "Costo ${formatEuro(data.cost)}   " +
                        "Diff. ${formatEuro(data.difference)}   " +
                        "Ricarico ${formatPercent(data.markupPercent)}",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Composable
private fun YearOverYearCard(
    comparison: Map<Int, SalesSummaryDto>,
    selectedYear: Int
) {
    val previousYear = selectedYear - 1
    val current = comparison[selectedYear]?.total
    val previous = comparison[previousYear]?.total

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "CONFRONTO $selectedYear vs $previousYear",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            if (current == null || previous == null) {
                Text(
                    text = "Dati non disponibili per il confronto.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            YearOverYearRow(
                label = "Vendite",
                currentValue = formatEuro(current.salesTaxable),
                changeText = formatPercentChange(
                    current.salesTaxable,
                    previous.salesTaxable
                )
            )

            YearOverYearRow(
                label = "Differenza €",
                currentValue = formatEuro(current.difference),
                changeText = formatPercentChange(
                    current.difference,
                    previous.difference
                )
            )

            val markupPoints =
                current.markupPercent - previous.markupPercent

            YearOverYearRow(
                label = "Ricarico",
                currentValue = formatPercent(current.markupPercent),
                changeText = formatPointChange(markupPoints)
            )
        }
    }
}

@Composable
private fun YearOverYearRow(
    label: String,
    currentValue: String,
    changeText: String
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp
            )

            Text(
                text = currentValue,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = changeText,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatPercentChange(
    current: Double,
    previous: Double
): String {
    if (previous == 0.0) {
        return "n.d."
    }

    val change =
        ((current - previous) / previous) * 100.0

    val formatter =
        NumberFormat.getNumberInstance(Locale.ITALY).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

    val sign =
        when {
            change > 0.0 -> "+"
            change < 0.0 -> "−"
            else -> ""
        }

    return "$sign${formatter.format(kotlin.math.abs(change))} %"
}

private fun formatPointChange(
    points: Double
): String {
    val formatter =
        NumberFormat.getNumberInstance(Locale.ITALY).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

    val sign =
        when {
            points > 0.0 -> "+"
            points < 0.0 -> "−"
            else -> ""
        }

    return "$sign${formatter.format(kotlin.math.abs(points))} punti"
}

private fun formatEuro(value: Double): String {
    val formatter =
        NumberFormat.getCurrencyInstance(Locale.ITALY)

    return formatter.format(value)
}

private fun formatPercent(value: Double): String {
    val formatter =
        NumberFormat.getNumberInstance(Locale.ITALY).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

    return "${formatter.format(value)} %"
}

private fun formatInteger(value: Int): String {
    return NumberFormat
        .getIntegerInstance(Locale.ITALY)
        .format(value)
}

private fun buildPeriodText(
    summary: SalesSummaryDto
): String {
    val from =
        summary.from
            .take(10)
            .split("-")
            .let { parts ->
                if (parts.size == 3) {
                    "${parts[2]}/${parts[1]}/${parts[0]}"
                } else {
                    summary.from.take(10)
                }
            }

    val to =
        summary.to
            .take(10)
            .split("-")
            .let { parts ->
                if (parts.size == 3) {
                    "${parts[2]}/${parts[1]}/${parts[0]}"
                } else {
                    summary.to.take(10)
                }
            }

    return "Periodo $from → $to"
}