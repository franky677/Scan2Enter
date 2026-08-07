package com.scan2enter.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import com.scan2enter.overlay.camera.OverlayCameraManager
import com.scan2enter.overlay.camera.OverlayLifecycleOwner
import com.scan2enter.scanner.ScanConfig
import com.scan2enter.scanner.ScanSession
import com.scan2enter.MainActivity

class ScanOverlay(
    private val context: Context
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val cameraManager =
        OverlayCameraManager(context)

    private val scanSession =
        ScanSession(context)

    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private var container: FrameLayout? = null

    private var previewView: PreviewView? = null

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var closing = false

    private var rapidRescan = false
    private var godexMode = false
    private var a4Mode = false

    private companion object {
        const val WORKFLOW_PREFS = "scan_workflow"
        const val WORKFLOW_MODE_KEY = "mode"
        const val MODE_INFO = "INFO"
        const val MODE_LABELS_GODEX = "ETICHETTE_GODEX"
        const val MODE_LABELS_A4 = "ETICHETTE_A4"
    }

    private val timeoutRunnable = Runnable {

        Log.d(
            "Scan2Enter",
            "Scanner timeout rapidRescan=$rapidRescan"
        )

        /*
         * Il timeout della riapertura automatica non è un errore: significa
         * semplicemente che l'operatore ha terminato il ciclo spara-spara.
         */
        if (!rapidRescan) {
            context.startService(
                android.content.Intent(
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

    fun show(
        rapidRescan: Boolean = false
    ) {

        if (container != null) return

        closing = false
        this.rapidRescan = rapidRescan
        val currentMode = loadCurrentMode()
        godexMode = currentMode == MODE_LABELS_GODEX
        a4Mode = currentMode == MODE_LABELS_A4

        scanSession.start()

        if (!godexMode && !a4Mode) {
            handler.postDelayed(
                timeoutRunnable,
                if (rapidRescan) {
                    2_000L
                } else {
                    ScanConfig.SCAN_TIMEOUT
                }
            )
        }

        val frame = FrameLayout(context)

        val closeScanner = View.OnClickListener {
            Log.d(
                "Scan2Enter",
                "Scanner chiuso con tocco " +
                        "godexMode=$godexMode a4Mode=$a4Mode"
            )

            if (godexMode || a4Mode) {
                exitSpecialModeToHome()
            } else {
                hide()
            }
        }

        /*
         * Assegno il tap sia al contenitore sia ai suoi figli: PreviewView e
         * grafica di mira possono intercettare il tocco prima del FrameLayout.
         */
        frame.setOnClickListener(closeScanner)

        val preview = PreviewView(context)
        preview.setOnClickListener(closeScanner)

        preview.scaleType =
            PreviewView.ScaleType.FILL_CENTER

        frame.addView(
            preview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val overlayView = ScanOverlayView(context)
        overlayView.setOnClickListener(closeScanner)

        frame.addView(
            overlayView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        if (godexMode || a4Mode) {
            val message = TextView(context).apply {
                text = if (a4Mode) {
                    "📄 MODALITÀ ETICHETTE A4\n" +
                            "Tocca lo scanner per tornare alla pagina A4"
                } else {
                    "🏷️ MODALITÀ ETICHETTE GODEX\n" +
                            "Tocca lo scanner per tornare alla Home"
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
        )

        params.gravity = Gravity.CENTER

        windowManager.addView(frame, params)

        container = frame
        previewView = preview

        lifecycleOwner = OverlayLifecycleOwner()

        lifecycleOwner!!.start()

        cameraManager.start(
            previewView = preview,
            lifecycleOwner = lifecycleOwner!!,
            onBarcodeDetected = { barcode ->

                if (closing) return@start

                closing = true

                scanSession.onBarcodeRead(barcode) {
                    hide()
                }
            }
        )
    }

    fun hide() {

        handler.removeCallbacks(timeoutRunnable)

        scanSession.stop()

        cameraManager.stop()

        lifecycleOwner?.stop()
        lifecycleOwner = null

        container?.let {

            windowManager.removeView(it)

        }

        container = null
        previewView = null
        rapidRescan = false
        godexMode = false
        a4Mode = false
        closing = false
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
                            action = OverlayService.ACTION_SHOW_A4_LABELS
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