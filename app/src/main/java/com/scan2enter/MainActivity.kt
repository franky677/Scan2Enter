package com.scan2enter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.scan2enter.api.DueRetailApiTest
import com.scan2enter.overlay.OverlayService
import com.scan2enter.scanner.ScannerModeDetector
import com.scan2enter.ui.screens.HomeScreen
import com.scan2enter.ui.screens.TrovaTuttoScreen
import com.scan2enter.ui.screens.SessionScreen
import com.scan2enter.ui.screens.ColloHistoryScreen
import com.scan2enter.session.SessionStore
import com.scan2enter.ui.theme.Scan2EnterTheme

class MainActivity : ComponentActivity() {

    @Volatile
    private var currentScreenName: String = "HOME"

    private var requestedScreen by mutableStateOf<String?>(null)

    private var sunmiHomeReceiverRegistered = false

    private val sunmiHomeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (
                    intent?.action !=
                    "com.honeywell.tools.action.scan_result"
                ) {
                    return
                }

                /*
                 * In SESSIONE il barcode viene già gestito dal receiver
                 * dedicato di SessionScreen: qui interveniamo SOLO in HOME.
                 */
                if (
                    !ScannerModeDetector.isSunmi() ||
                    currentScreenName != "HOME"
                ) {
                    return
                }

                val barcode =
                    intent.getStringExtra("barcode_data")
                        ?.trim()
                        .orEmpty()
                        .ifBlank {
                            intent.getByteArrayExtra("source_byte")
                                ?.toString(Charsets.UTF_8)
                                ?.trim()
                                .orEmpty()
                        }

                if (barcode.isBlank()) {
                    return
                }

                Log.d(
                    "Scan2Enter",
                    "SUNMI HOME BARCODE -> POPUP = $barcode"
                )

                val workflowMode =
                    applicationContext
                        .getSharedPreferences(
                            "scan_workflow",
                            MODE_PRIVATE
                        )
                        .getString(
                            "mode",
                            "INFO"
                        )
                        ?: "INFO"

                if (
                    workflowMode == "ETICHETTE_GODEX" ||
                    workflowMode == "ETICHETTE_A4"
                ) {
                    return
                }

                startService(
                    Intent(
                        this@MainActivity,
                        OverlayService::class.java
                    ).apply {
                        action =
                            OverlayService.ACTION_OPEN_CURRENT_ARTICLE

                        putExtra(
                            OverlayService.EXTRA_CURRENT_ARTICLE_BARCODE,
                            barcode
                        )

                        putExtra(
                            OverlayService.EXTRA_FORCE_STOCK_SOUND,
                            true
                        )
                    }
                )
            }
        }

    private fun registerSunmiHomeReceiver() {
        if (
            !ScannerModeDetector.isSunmi() ||
            sunmiHomeReceiverRegistered
        ) {
            return
        }

        ContextCompat.registerReceiver(
            this,
            sunmiHomeReceiver,
            IntentFilter(
                "com.honeywell.tools.action.scan_result"
            ),
            ContextCompat.RECEIVER_EXPORTED
        )

        sunmiHomeReceiverRegistered = true

        Log.d(
            "Scan2Enter",
            "SUNMI HOME RECEIVER REGISTRATO"
        )
    }

    private fun unregisterSunmiHomeReceiver() {
        if (!sunmiHomeReceiverRegistered) {
            return
        }

        runCatching {
            unregisterReceiver(
                sunmiHomeReceiver
            )
        }

        sunmiHomeReceiverRegistered = false
    }

    private fun openScannerFromHardwareKey() {
        val directToSession =
            currentScreenName == "SESSIONE"

        Log.d(
            "Scan2Enter",
            "S24: CLICK VOLUME GIÙ -> scanner directToSession=$directToSession"
        )

        startService(
            Intent(
                this,
                OverlayService::class.java
            ).apply {
                action = OverlayService.ACTION_OPEN_SCANNER

                putExtra(
                    OverlayService.EXTRA_DIRECT_TO_SESSION,
                    directToSession
                )
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when {
            intent?.getBooleanExtra(EXTRA_OPEN_A4_SEARCH, false) == true ->
                requestedScreen = "TROVATUTTO_A4"

            intent?.getBooleanExtra(EXTRA_OPEN_GODEX_SEARCH, false) == true ->
                requestedScreen = "TROVATUTTO_GODEX"
        }

        Log.d("DueRetailApi", "MAIN ACTIVITY AVVIATA")
        DueRetailApiTest.run()
        Log.d("DueRetailApi", "TEST API LANCIATO")

        /*
         * Blocca qualunque vecchia richiesta automatica di apertura scanner
         * durante i primi istanti di avvio dell'app.
         * I trigger manuali continueranno a funzionare normalmente subito dopo.
         */
        applicationContext
            .getSharedPreferences(
                "scanner_startup_guard",
                MODE_PRIVATE
            )
            .edit()
            .putLong(
                "block_until",
                System.currentTimeMillis() + 2500L
            )
            .apply()

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

        /*
         * All'apertura di Scan2Enter lo scanner deve essere sempre fermo.
         * Da ora parte soltanto tramite i tasti volume, la quick dock
         * o un comando esplicito dell'interfaccia.
         */
        startService(
            Intent(
                this,
                OverlayService::class.java
            ).apply {
                action = OverlayService.ACTION_CLOSE_SCANNER
            }
        )

        SessionStore.initialize(applicationContext)

        enableEdgeToEdge()

        setContent {
            Scan2EnterTheme {
                var currentScreen by rememberSaveable {
                    mutableStateOf("HOME")
                }

                LaunchedEffect(requestedScreen) {
                    requestedScreen?.let { destination ->
                        currentScreen = destination
                        requestedScreen = null
                    }
                }

                LaunchedEffect(currentScreen) {
                    currentScreenName = currentScreen

                    applicationContext
                        .getSharedPreferences(
                            "scan_ui_state",
                            MODE_PRIVATE
                        )
                        .edit()
                        .putString(
                            "current_screen",
                            currentScreen
                        )
                        .apply()

                    /*
                     * La Home è sempre uno stato neutro:
                     * entrando o tornando qui lo scanner viene fermato.
                     * Potrà ripartire soltanto da un trigger esplicito
                     * (Volume Su/Giù, quick dock o pulsante SCANSIONA).
                     */
                    if (currentScreen == "HOME") {
                        /*
                         * HOME deve essere sempre uno stato neutro.
                         * Questo evita che un vecchio workflow A4/BLISTER/GODEX
                         * faccia ignorare il grilletto Sunmi o sporchi SESSIONE.
                         */
                        applicationContext
                            .getSharedPreferences(
                                "scan_workflow",
                                MODE_PRIVATE
                            )
                            .edit()
                            .putString(
                                "mode",
                                "INFO"
                            )
                            .apply()

                        startService(
                            Intent(
                                this@MainActivity,
                                OverlayService::class.java
                            ).apply {
                                action =
                                    OverlayService.ACTION_CLOSE_SCANNER
                            }
                        )
                    }
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


                    "TROVATUTTO_GODEX" -> {
                        TrovaTuttoScreen(
                            onBack = {
                                currentScreen = "HOME"
                                startService(
                                    Intent(
                                        this@MainActivity,
                                        OverlayService::class.java
                                    ).apply {
                                        action = OverlayService.ACTION_SHOW_GODEX_SETUP
                                    }
                                )
                            },
                            onArticleSelected = { barcode ->
                                currentScreen = "HOME"

                                startService(
                                    Intent(
                                        this@MainActivity,
                                        OverlayService::class.java
                                    ).apply {
                                        action =
                                            OverlayService.ACTION_OPEN_GODEX_SEARCH_RESULT

                                        putExtra(
                                            OverlayService.EXTRA_CURRENT_ARTICLE_BARCODE,
                                            barcode
                                        )
                                    }
                                )
                            }
                        )
                    }


                    "TROVATUTTO_A4" -> {
                        TrovaTuttoScreen(
                            onBack = {
                                currentScreen = "HOME"
                                startService(
                                    Intent(
                                        this@MainActivity,
                                        OverlayService::class.java
                                    ).apply {
                                        action = OverlayService.ACTION_SHOW_A4_LABELS
                                    }
                                )
                            },
                            onArticleSelected = { barcode ->
                                currentScreen = "HOME"

                                startService(
                                    Intent(
                                        this@MainActivity,
                                        OverlayService::class.java
                                    ).apply {
                                        action =
                                            OverlayService.ACTION_OPEN_A4_SEARCH_RESULT

                                        putExtra(
                                            OverlayService.EXTRA_CURRENT_ARTICLE_BARCODE,
                                            barcode
                                        )
                                    }
                                )
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
                            },
                            onOpenColloHistory = {
                                currentScreen = "STORICO_COLLI"
                            }
                        )
                    }

                    "STORICO_COLLI" -> {
                        ColloHistoryScreen(
                            onBack = {
                                currentScreen = "SESSIONE"
                            },
                            onDuplicated = {
                                currentScreen = "SESSIONE"
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        when {
            intent.getBooleanExtra(EXTRA_OPEN_A4_SEARCH, false) ->
                requestedScreen = "TROVATUTTO_A4"

            intent.getBooleanExtra(EXTRA_OPEN_GODEX_SEARCH, false) ->
                requestedScreen = "TROVATUTTO_GODEX"
        }
    }

    companion object {
        const val EXTRA_OPEN_GODEX_SEARCH =
            "com.scan2enter.extra.OPEN_GODEX_SEARCH"

        const val EXTRA_OPEN_A4_SEARCH =
            "com.scan2enter.extra.OPEN_A4_SEARCH"
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {
        val isVolumeTrigger =
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                    keyCode == KeyEvent.KEYCODE_VOLUME_UP

        if (
            isVolumeTrigger &&
            !ScannerModeDetector.isSunmi()
        ) {
            /*
             * Sull'S24 entrambi i tasti volume diventano grilletti Scan2Enter.
             * Un singolo click apre immediatamente lo scanner.
             * Gli eventi ripetuti dovuti al tasto tenuto premuto vengono ignorati.
             */
            if ((event?.repeatCount ?: 0) == 0) {
                openScannerFromHardwareKey()
            }

            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {
        val isVolumeTrigger =
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                    keyCode == KeyEvent.KEYCODE_VOLUME_UP

        if (
            isVolumeTrigger &&
            !ScannerModeDetector.isSunmi()
        ) {
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        registerSunmiHomeReceiver()
    }

    override fun onPause() {
        super.onPause()
        Log.d("Scan2Enter", "MainActivity -> onPause")
    }

    override fun onStop() {
        unregisterSunmiHomeReceiver()

        super.onStop()
        Log.d("Scan2Enter", "MainActivity -> onStop")
    }

    override fun onDestroy() {
        unregisterSunmiHomeReceiver()

        super.onDestroy()
        Log.d("Scan2Enter", "MainActivity -> onDestroy")
    }
}