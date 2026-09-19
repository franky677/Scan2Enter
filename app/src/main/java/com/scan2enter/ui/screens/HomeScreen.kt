package com.scan2enter.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scan2enter.overlay.OverlayService
import com.scan2enter.sales.SalesAccessManager
import com.scan2enter.ui.components.SalesUnlockDialog
import com.scan2enter.ui.components.GatewayStatusBanner
import com.scan2enter.update.AppUpdateInfo
@Composable
fun HomeScreen(
    availableAppUpdate: AppUpdateInfo?,
    onInstallUpdate: () -> Unit,
    onOpenTrovaTutto: () -> Unit,
    onOpenSession: () -> Unit,
    onOpenSales: () -> Unit,
    onOpenInventoryAnalysis: () -> Unit,
    onOpenPromotions: () -> Unit
) {
    val context = LocalContext.current

    val appVersionName = remember {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName ?: "?"
    }

    val salesAccessManager = remember {
        SalesAccessManager(context)
    }

    var showSalesPasswordDialog by remember {
        mutableStateOf(false)
    }

    var showInventoryPasswordDialog by remember {
        mutableStateOf(false)
    }

    var showMoreMenu by remember {
        mutableStateOf(false)
    }

    var showWhatsNewDialog by remember {
        mutableStateOf(false)
    }

    var whatsNewCountdown by remember {
        mutableStateOf(4)
    }

    LaunchedEffect(Unit) {
        val currentVersionCode =
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .longVersionCode

        val prefs = context.getSharedPreferences(
            "whats_new",
            Context.MODE_PRIVATE
        )

        val lastShownVersion = prefs.getLong(
            "last_shown_version",
            6L
        )

        if (currentVersionCode > lastShownVersion) {
            prefs.edit()
                .putLong("last_shown_version", currentVersionCode)
                .apply()

            showWhatsNewDialog = true
        }
    }

    var whatsNewPaused by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(showWhatsNewDialog) {
        if (showWhatsNewDialog) {
            whatsNewCountdown = 4
            while (whatsNewCountdown > 0 && showWhatsNewDialog) {
                delay(1000)
                if (!whatsNewPaused) {
                    whatsNewCountdown--
                }
            }
            if (showWhatsNewDialog) {
                showWhatsNewDialog = false
            }
        }
    }

    DisposableEffect(Unit) {
        context
            .applicationContext
            .getSharedPreferences(
                "scan_workflow",
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                "mode",
                "INFO"
            )
            .apply()

        context.startService(
            Intent(
                context,
                OverlayService::class.java
            ).apply {
                action =
                    OverlayService.ACTION_OPEN_SCANNER
            }
        )

        onDispose {
            context.startService(
                Intent(
                    context,
                    OverlayService::class.java
                ).apply {
                    action =
                        OverlayService.ACTION_CLOSE_SCANNER
                }
            )
        }
    }

    fun moduleNotAvailable(moduleName: String) {
        Toast.makeText(
            context,
            "$moduleName: modulo in preparazione",
            Toast.LENGTH_SHORT
        ).show()
    }

    if (showSalesPasswordDialog) {
        SalesUnlockDialog(
            accessManager = salesAccessManager,
            onDismiss = {
                showSalesPasswordDialog = false
            },
            onUnlocked = {
                showSalesPasswordDialog = false
                onOpenSales()
            }
        )
    }

    if (showInventoryPasswordDialog) {
        SalesUnlockDialog(
            accessManager = salesAccessManager,
            onDismiss = {
                showInventoryPasswordDialog = false
            },
            onUnlocked = {
                showInventoryPasswordDialog = false
                onOpenInventoryAnalysis()
            },
            areaName = "ANALISI MAGAZZINO"
        )
    }

    if (showWhatsNewDialog) {
        AlertDialog(
            modifier = Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    whatsNewPaused = true
                    try {
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                    } finally {
                        whatsNewPaused = false
                    }
                }
            },
            onDismissRequest = { showWhatsNewDialog = false },
            title = {
                Text("Novità - Scan2Enter v$appVersionName")
            },
            text = {
                Text(
                    text = " Aggiunta finestra con le novità dopo ogni aggiornamento\n" +
                    " Aggiunta cronologia degli aggiornamenti\n\n" +
                        "Chiusura tra $whatsNewCountdown secondi"
                )
            },
            confirmButton = {
                Button(
                    onClick = { showWhatsNewDialog = false }
                ) {
                    Text("OK, HO CAPITO")
                }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 8.dp,
                        bottom = 4.dp
                    ),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "Scan2Enter  v$appVersionName",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "  By De Pieri Franco Production",
                    modifier = Modifier
                        .padding(top = 11.dp)
                        .weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )

                IconButton(
                    onClick = { showMoreMenu = true }
                ) {
                    Text(
                        text = "\u22EE",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Novità e aggiornamenti") },
                            onClick = {
                                showMoreMenu = false
                                showWhatsNewDialog = true
                            }
                        )
                    }
                }
            }

            if (availableAppUpdate != null) {
                Text(
                    text = "AGGIORNAMENTO DISPONIBILE  v${availableAppUpdate.versionName}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onInstallUpdate)
                        .padding(vertical = 6.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HomeModuleButton(
                text = "🔎\nTROVATUTTO",
                onClick = onOpenTrovaTutto,
                modifier = Modifier.fillMaxWidth()
            )
            GatewayStatusBanner()
            HomeButtonRow(
                leftText = "📋\nRIORDINO",
                rightText = "⭐\nPREFERITI",
                onLeftClick = {
                    context.startService(
                        Intent(
                            context,
                            OverlayService::class.java
                        ).apply {
                            action = OverlayService.ACTION_SHOW_REORDER_LIST
                        }
                    )
                },
                onRightClick = {
                    context.startService(
                        Intent(
                            context,
                            OverlayService::class.java
                        ).apply {
                            action = OverlayService.ACTION_SHOW_FAVORITES_LIST
                        }
                    )
                }
            )

            HomeButtonRow(
                leftText = "📄\nETICHETTE A4",
                rightText = "🏷️\nPROMOZIONI",
                onLeftClick = {
                    context.startService(
                        Intent(
                            context,
                            OverlayService::class.java
                        ).apply {
                            action = OverlayService.ACTION_SHOW_A4_LABELS
                        }
                    )
                },
                onRightClick = onOpenPromotions
            )

            HomeButtonRow(
                leftText = "📊\nVENDITE",
                rightText = "📦\nANALISI MAGAZZINO",
                onLeftClick = {
                    showSalesPasswordDialog = true
                },
                onRightClick = {
                    showInventoryPasswordDialog = true
                }
            )

            Button(
                onClick = onOpenSession,
                modifier = Modifier.fillMaxWidth().height(92.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                Text(
                    text = "📦  COLLO VELOCE",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HomeButtonRow(
    leftText: String,
    rightText: String,
    onLeftClick: () -> Unit,
    onRightClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeModuleButton(
            text = leftText,
            onClick = onLeftClick,
            modifier = Modifier.weight(1f)
        )

        if (onRightClick != null) {
            HomeModuleButton(
                text = rightText,
                onClick = onRightClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HomeModuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(120.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(
            text = text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 27.sp
        )
    }
}
