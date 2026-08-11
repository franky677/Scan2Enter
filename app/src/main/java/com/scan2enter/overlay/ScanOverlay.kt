package com.scan2enter.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.camera.view.PreviewView
import com.scan2enter.MainActivity
import com.scan2enter.overlay.camera.OverlayCameraManager
import com.scan2enter.overlay.camera.OverlayLifecycleOwner
import com.scan2enter.scanner.ScanConfig
import com.scan2enter.scanner.ScanSession
import com.scan2enter.scanner.ScannerMode
import com.scan2enter.scanner.ScannerModeDetector

class ScanOverlay(
    private val context: Context
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val cameraManager = OverlayCameraManager(context)
    private val scanSession = ScanSession(context)
    private val handler = Handler(Looper.getMainLooper())

    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var container: FrameLayout? = null
    private var previewView: PreviewView? = null
    private var scannerActive = false

    @Volatile
    private var closing = false

    private var receiverRegistered = false
    private var rapidRescan = false
    private var godexMode = false
    private var a4Mode = false
    private var directToSession = false

    private companion object {
        const val TAG = "Scan2Enter"
        const val SUNMI_SCAN_ACTION = "com.honeywell.tools.action.scan_result"
        const val SUNMI_BARCODE_EXTRA = "barcode_data"

        const val WORKFLOW_PREFS = "scan_workflow"
        const val WORKFLOW_MODE_KEY = "mode"
        const val MODE_INFO = "INFO"
        const val MODE_LABELS_GODEX = "ETICHETTE_GODEX"
        const val MODE_LABELS_A4 = "ETICHETTE_A4"
    }

    private val timeoutRunnable = Runnable {
        if (ScannerModeDetector.current() != ScannerMode.CAMERA) {
            return@Runnable
        }

        Log.d(TAG, "CAMERA timeout rapidRescan=$rapidRescan")

        if (!rapidRescan) {
            context.startService(
                Intent(
                    context,
                    OverlayService::class.java
                ).apply {
                    action = OverlayService.ACTION_SHOW_SCAN_ERROR
                    putExtra(
                        OverlayService.EXTRA_SCAN_ERROR_MESSAGE,
                        "Nessun codice letto. Riprovare."
                    )
                }
            )
        }

        hide()
    }

    private val sunmiReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                receiverContext: Context?,
                intent: Intent?
            ) {
                if (intent?.action != SUNMI_SCAN_ACTION) return

                val barcode =
                    intent.getStringExtra(SUNMI_BARCODE_EXTRA)
                        ?.trim()
                        .orEmpty()

                Log.d(TAG, "SUNMI BROADCAST barcode=$barcode")

                if (barcode.isBlank()) return

                onHardwareBarcode(barcode)
            }
        }

    fun show(
        rapidRescan: Boolean = false,
        directToSession: Boolean = false
    ) {
        closing = false
        this.rapidRescan = rapidRescan
        this.directToSession = directToSession

        val currentMode = loadCurrentMode()
        godexMode = currentMode == MODE_LABELS_GODEX
        a4Mode = currentMode == MODE_LABELS_A4

        when (ScannerModeDetector.current()) {
            ScannerMode.SUNMI_LASER -> showSunmiScanner()
            ScannerMode.CAMERA -> showCameraScanner()
        }
    }

    private fun showSunmiScanner() {
        if (scannerActive) {
            Log.d(TAG, "SUNMI SCANNER GIÀ ATTIVO")
            return
        }

        scanSession.start()

        try {
            context.registerReceiver(
                sunmiReceiver,
                IntentFilter(SUNMI_SCAN_ACTION)
            )

            receiverRegistered = true
            scannerActive = true

            Log.d(
                TAG,
                "SUNMI HARDWARE SCANNER ATTIVO SENZA FINESTRA"
            )
        } catch (error: Exception) {
            receiverRegistered = false
            scannerActive = false
            scanSession.stop()

            Log.e(
                TAG,
                "ERRORE REGISTRAZIONE SUNMI RECEIVER",
                error
            )
        }
    }

    private fun showCameraScanner() {
        if (container != null) {
            Log.d(TAG, "CAMERA SCANNER GIÀ ATTIVO")
            return
        }

        scanSession.start()
        scannerActive = true

        if (!godexMode && !a4Mode) {
            handler.postDelayed(
                timeoutRunnable,
                if (rapidRescan) 2_000L else ScanConfig.SCAN_TIMEOUT
            )
        }

        val frame = FrameLayout(context)

        val closeScanner = View.OnClickListener {
            Log.d(
                TAG,
                "CAMERA chiusa con tocco godexMode=$godexMode a4Mode=$a4Mode"
            )

            if (godexMode || a4Mode) {
                exitSpecialModeToHome()
            } else {
                hide()
            }
        }

        frame.setOnClickListener(closeScanner)

        val preview = PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setOnClickListener(closeScanner)
        }

        frame.addView(
            preview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val overlayView = ScanOverlayView(context).apply {
            setOnClickListener(closeScanner)
        }

        frame.addView(
            overlayView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        if (godexMode || a4Mode) {
            val message = TextView(context).apply {
                text =
                    if (a4Mode) {
                        "MODALITÀ ETICHETTE A4\nTocca lo scanner per tornare alla pagina A4"
                    } else {
                        "MODALITÀ ETICHETTE GODEX\nTocca lo scanner per tornare alla Home"
                    }

                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(7), dp(10), dp(7))
                setBackgroundColor(0xCC000000.toInt())
                setOnClickListener(closeScanner)
            }

            frame.addView(
                message,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM
                }
            )
        }

        val params = WindowManager.LayoutParams(
            dp(ScanConfig.OVERLAY_WIDTH_DP),
            dp(ScanConfig.OVERLAY_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(frame, params)

        container = frame
        previewView = preview

        lifecycleOwner =
            OverlayLifecycleOwner().also {
                it.start()
            }

        cameraManager.start(
            previewView = preview,
            lifecycleOwner = lifecycleOwner!!,
            onBarcodeDetected = { barcode ->
                if (closing) return@start

                closing = true

                if (directToSession) {
                    Log.d(
                        TAG,
                        "CAMERA BARCODE -> SESSIONE DIRETTA = $barcode"
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
                                barcode
                            )

                            putExtra(
                                OverlayService.EXTRA_SUPPRESS_PRODUCT_POPUP,
                                true
                            )
                        }
                    )

                    hide()
                } else {
                    Log.d(
                        TAG,
                        "CAMERA BARCODE -> SCANSESSION = $barcode"
                    )

                    scanSession.onBarcodeRead(barcode) {
                        hide()
                    }
                }
            }
        )

        Log.d(TAG, "CAMERA SCANNER ATTIVO")
    }

    fun onHardwareBarcode(
        barcode: String
    ) {
        if (
            ScannerModeDetector.current() !=
            ScannerMode.SUNMI_LASER
        ) {
            return
        }

        if (!scannerActive) {
            Log.d(
                TAG,
                "SUNMI BARCODE IGNORATO - SCANNER NON ATTIVO"
            )
            return
        }

        if (closing) {
            Log.d(
                TAG,
                "SUNMI BARCODE IGNORATO - LETTURA GIÀ IN CORSO"
            )
            return
        }

        val normalized = barcode.trim()
        if (normalized.isBlank()) return

        Log.d(
            TAG,
            "SUNMI BARCODE -> SCANSESSION = $normalized"
        )

        closing = true

        scanSession.onBarcodeRead(normalized) {
            hide()
        }
    }

    fun hide() {
        handler.removeCallbacks(timeoutRunnable)

        scanSession.stop()

        if (receiverRegistered) {
            try {
                context.unregisterReceiver(sunmiReceiver)
            } catch (_: Exception) {
            }

            receiverRegistered = false
        }

        cameraManager.stop()

        lifecycleOwner?.stop()
        lifecycleOwner = null

        container?.let { current ->
            runCatching {
                windowManager.removeView(current)
            }
        }

        container = null
        previewView = null

        scannerActive = false
        rapidRescan = false
        godexMode = false
        a4Mode = false
        directToSession = false
        closing = false

        Log.d(
            TAG,
            "SCANNER DISATTIVATO device=${ScannerModeDetector.current()}"
        )
    }

    private fun loadCurrentMode(): String {
        return context.applicationContext
            .getSharedPreferences(
                WORKFLOW_PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                WORKFLOW_MODE_KEY,
                MODE_INFO
            )
            ?: MODE_INFO
    }

    private fun exitSpecialModeToHome() {
        if (a4Mode) {
            hide()

            handler.postDelayed(
                {
                    context.startService(
                        Intent(
                            context,
                            OverlayService::class.java
                        ).apply {
                            action =
                                OverlayService.ACTION_SHOW_A4_LABELS
                        }
                    )
                },
                250L
            )

            return
        }

        context.applicationContext
            .getSharedPreferences(
                WORKFLOW_PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                WORKFLOW_MODE_KEY,
                MODE_INFO
            )
            .apply()

        hide()

        context.startActivity(
            Intent(
                context,
                MainActivity::class.java
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
        )
    }

    private fun dp(value: Int): Int {
        return (
                value *
                        context.resources.displayMetrics.density
                ).toInt()
    }
}