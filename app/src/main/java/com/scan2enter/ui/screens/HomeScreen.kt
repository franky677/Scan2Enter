package com.scan2enter.ui.screens

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scan2enter.overlay.OverlayService

@Composable
fun HomeScreen() {

    val context = LocalContext.current

    fun moduleNotAvailable(moduleName: String) {
        Toast.makeText(
            context,
            "$moduleName: modulo in preparazione",
            Toast.LENGTH_SHORT
        ).show()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),

        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding(),
                shadowElevation = 12.dp
            ) {
                Button(
                    onClick = {
                        context.startService(
                            Intent(
                                context,
                                OverlayService::class.java
                            ).apply {
                                action = OverlayService.ACTION_OPEN_SCANNER
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        ),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text(
                        text = "🔫  SCANSIONA",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text = "Scan2Enter",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 8.dp,
                        bottom = 4.dp
                    ),
                textAlign = TextAlign.Center,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            HomeButtonRow(
                leftText = "📋\nRIORDINO",
                rightText = "🏷️\nGODEX",
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
                            action = OverlayService.ACTION_SHOW_GODEX_SETUP
                        }
                    )
                }
            )

            HomeButtonRow(
                leftText = "📄\nETICHETTE A4",
                rightText = "📦\nCOLLO VELOCE",
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
                    moduleNotAvailable("Collo veloce")
                }
            )

            HomeButtonRow(
                leftText = "⭐\nPREFERITI",
                rightText = "📊\nVENDITE",
                onLeftClick = {
                    context.startService(
                        Intent(
                            context,
                            OverlayService::class.java
                        ).apply {
                            action = OverlayService.ACTION_SHOW_FAVORITES_LIST
                        }
                    )
                },
                onRightClick = {
                    moduleNotAvailable("Vendite")
                }
            )

            HomeButtonRow(
                leftText = "⚙️\nIMPOSTAZIONI",
                rightText = "",
                onLeftClick = {
                    moduleNotAvailable("Impostazioni")
                },
                onRightClick = null
            )
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

        } else {

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                color = MaterialTheme.colorScheme.surface
            ) {}
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