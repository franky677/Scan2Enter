package com.scan2enter.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scan2enter.overlay.OverlayService

@Composable
fun HomeScreen(
    onOpenTrovaTutto: () -> Unit,
    onOpenSession: () -> Unit
) {
    val context = LocalContext.current

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
                    text = "Scan2Enter",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "  By De Pieri Franco Production",
                    modifier = Modifier.padding(top = 11.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            HomeModuleButton(
                text = "🔎\nTROVATUTTO",
                onClick = onOpenTrovaTutto,
                modifier = Modifier.fillMaxWidth()
            )

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
                rightText = "🏷️\nGODEX",
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
                onRightClick = {
                    context.startService(
                        Intent(
                            context,
                            OverlayService::class.java
                        ).apply {
                            action = OverlayService.ACTION_SHOW_GODEX_SETUP
                        }
                    )
                }
            )

            HomeButtonRow(
                leftText = "📊\nVENDITE",
                rightText = "",
                onLeftClick = {
                    moduleNotAvailable("Vendite")
                },
                onRightClick = null
            )

            Button(
                onClick = onOpenSession,
                modifier = Modifier.fillMaxWidth().height(92.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                Text(
                    text = "📋  SESSIONE",
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