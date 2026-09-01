package com.scan2enter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.AlertDialog
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
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.overlay.OverlayService
import com.scan2enter.scanner.ScannerModeDetector
import com.scan2enter.ui.screens.HomeScreen
import com.scan2enter.ui.screens.TrovaTuttoScreen
import com.scan2enter.ui.screens.SessionScreen
import com.scan2enter.ui.screens.SalesScreen
import com.scan2enter.ui.screens.InventoryAnalysisScreen
import com.scan2enter.ui.screens.ColloHistoryScreen
import com.scan2enter.session.SessionStore
import com.scan2enter.ui.theme.Scan2EnterTheme

class MainActivity : ComponentActivity() {

    @Volatile
    private var currentScreenName: String = "HOME"

    private var requestedScreen by mutableStateOf<String?>(null)

    private var expiryAlertChecked = false
    private var expiryAlertDialog: AlertDialog? = null

    private val expiryAlertHandler =
        android.os.Handler(android.os.Looper.getMainLooper())

    private val dismissExpiryAlertRunnable =
        Runnable {
            expiryAlertDialog?.takeIf { it.isShowing }?.dismiss()
        }

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

    /**
     * Solo Zebra / DataWedge.
     *
     * Trasforma Volume SU e Volume GIÙ in grilletti aggiuntivi del motore
     * scanner hardware senza modificare i trigger fisici originali.
     *
     * ACTION_DOWN -> START_SCANNING
     * ACTION_UP   -> STOP_SCANNING
     */
    private fun sendZebraSoftScanTrigger(command: String) {
        if (!ScannerModeDetector.isZebra()) {
            return
        }

        val intent = Intent("com.symbol.datawedge.api.ACTION").apply {
            putExtra(
                "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER",
                command
            )
        }

        sendBroadcast(intent)

        Log.d(
            "Scan2Enter",
            "ZEBRA VOLUME TRIGGER -> $command"
        )
    }

    private fun checkProductExpiryAlertsOnce() {
        if (expiryAlertChecked) return
        expiryAlertChecked = true

        Thread {
            val result =
                GatewayApiClient()
                    .getProductExpiryAlerts(months = 6)

            runOnUiThread {
                result
                    .onSuccess { alert ->
                        if (alert.hasAlerts && !isFinishing && !isDestroyed) {
                            showProductExpiryAlert(
                                expiredCount = alert.expiredCount,
                                expiringCount = alert.expiringCount
                            )
                        }
                    }
                    .onFailure { error ->
                        Log.w(
                            "Scan2Enter",
                            "Controllo scadenze all'avvio non riuscito",
                            error
                        )
                    }
            }
        }.start()
    }

    private fun showProductExpiryAlert(
        expiredCount: Int,
        expiringCount: Int
    ) {
        if (expiryAlertDialog?.isShowing == true) return

        val parts = mutableListOf<String>()

        if (expiredCount > 0) {
            parts += if (expiredCount == 1) {
                "1 prodotto scaduto"
            } else {
                "$expiredCount prodotti scaduti"
            }
        }

        if (expiringCount > 0) {
            parts += if (expiringCount == 1) {
                "1 prodotto in scadenza nei prossimi 6 mesi"
            } else {
                "$expiringCount prodotti in scadenza nei prossimi 6 mesi"
            }
        }

        val message =
            if (parts.isEmpty()) {
                "Ci sono prodotti da controllare per scadenza."
            } else {
                "Ci sono ${parts.joinToString(" e ")}."
            }

        expiryAlertDialog =
            AlertDialog.Builder(this)
                .setTitle("PRODOTTI IN SCADENZA")
                .setMessage(message)
                .setPositiveButton("VEDI PRODOTTI") { dialog, _ ->
                    dialog.dismiss()
                    requestedScreen = "ANALISI_MAGAZZINO"
                }
                .setNegativeButton("CHIUDI") { dialog, _ ->
                    dialog.dismiss()
                }
                .setOnDismissListener {
                    expiryAlertHandler.removeCallbacks(
                        dismissExpiryAlertRunnable
                    )
                    expiryAlertDialog = null
                }
                .show()

        expiryAlertHandler.removeCallbacks(
            dismissExpiryAlertRunnable
        )
        expiryAlertHandler.postDelayed(
            dismissExpiryAlertRunnable,
            4_000L
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

        checkProductExpiryAlertsOnce()

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

                    "VENDITE" -> {
                        SalesScreen(
                            onBack = {
                                currentScreen = "HOME"
                            }
                        )
                    }

                    "ANALISI_MAGAZZINO" -> {
                        InventoryAnalysisScreen(
                            onBack = {
                                currentScreen = "HOME"
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
                            },
                            onOpenSales = {
                                currentScreen = "VENDITE"
                            },
                            onOpenInventoryAnalysis = {
                                currentScreen = "ANALISI_MAGAZZINO"
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

        if (isVolumeTrigger) {
            /*
             * ZEBRA TC22:
             * entrambi i tasti volume diventano grilletti aggiuntivi DataWedge.
             * I trigger fisici Zebra originali restano completamente invariati.
             */
            if (ScannerModeDetector.isZebra()) {
                if ((event?.repeatCount ?: 0) == 0) {
                    sendZebraSoftScanTrigger("START_SCANNING")
                }

                return true
            }

            /* SUNMI: non intercettiamo qui i tasti volume. */
            if (ScannerModeDetector.isSunmi()) {
                return super.onKeyDown(keyCode, event)
            }

            /* S24 / dispositivi camera: comportamento esistente invariato. */
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

        if (isVolumeTrigger) {
            /* ZEBRA TC22: al rilascio fermiamo il soft trigger. */
            if (ScannerModeDetector.isZebra()) {
                sendZebraSoftScanTrigger("STOP_SCANNING")
                return true
            }

            /* SUNMI resta invariato. */
            if (ScannerModeDetector.isSunmi()) {
                return super.onKeyUp(keyCode, event)
            }

            /* S24: consumiamo il rilascio come già avveniva prima. */
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
        expiryAlertHandler.removeCallbacks(
            dismissExpiryAlertRunnable
        )
        expiryAlertDialog?.dismiss()
        expiryAlertDialog = null
        unregisterSunmiHomeReceiver()

        super.onDestroy()
        Log.d("Scan2Enter", "MainActivity -> onDestroy")
    }
}