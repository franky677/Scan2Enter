package com.scan2enter

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.scan2enter.api.DueRetailApiTest
import com.scan2enter.overlay.OverlayService
import com.scan2enter.ui.screens.HomeScreen
import com.scan2enter.ui.screens.TrovaTuttoScreen
import com.scan2enter.ui.screens.SessionScreen
import com.scan2enter.session.SessionStore
import com.scan2enter.ui.theme.Scan2EnterTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("DueRetailApi", "MAIN ACTIVITY AVVIATA")
        DueRetailApiTest.run()
        Log.d("DueRetailApi", "TEST API LANCIATO")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                startService(
                    Intent(this, OverlayService::class.java)
                )
            }
        } else {
            startService(
                Intent(this, OverlayService::class.java)
            )
        }

        SessionStore.initialize(applicationContext)

        enableEdgeToEdge()

        setContent {
            Scan2EnterTheme {
                var currentScreen by rememberSaveable {
                    mutableStateOf("HOME")
                }

                when (currentScreen) {
                    "TROVATUTTO" -> {
                        TrovaTuttoScreen(
                            onBack = {
                                currentScreen = "HOME"
                            },
                            onArticleOpened = null
                        )
                    }

                    "TROVATUTTO_SESSIONE" -> {
                        TrovaTuttoScreen(
                            onBack = {
                                currentScreen = "SESSIONE"
                            },
                            onArticleOpened = {
                                currentScreen = "SESSIONE"
                            }
                        )
                    }

                    "SESSIONE" -> {
                        SessionScreen(
                            onBack = {
                                currentScreen = "HOME"
                            },
                            onOpenSearch = {
                                currentScreen = "TROVATUTTO_SESSIONE"
                            }
                        )
                    }

                    else -> {
                        HomeScreen(
                            onOpenTrovaTutto = {
                                currentScreen = "TROVATUTTO"
                            },
                            onOpenSession = {
                                currentScreen = "SESSIONE"
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("Scan2Enter", "MainActivity -> onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d("Scan2Enter", "MainActivity -> onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Scan2Enter", "MainActivity -> onDestroy")
    }
}
