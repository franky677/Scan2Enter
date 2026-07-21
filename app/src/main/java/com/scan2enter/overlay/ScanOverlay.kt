package com.scan2enter.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import com.scan2enter.overlay.camera.OverlayCameraManager
import com.scan2enter.overlay.camera.OverlayLifecycleOwner
import com.scan2enter.scanner.ScanConfig
import com.scan2enter.scanner.ScanSession

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

        scanSession.start()

        handler.postDelayed(
            timeoutRunnable,
            if (rapidRescan) {
                2_000L
            } else {
                ScanConfig.SCAN_TIMEOUT
            }
        )

        val frame = FrameLayout(context)

        val closeScanner = android.view.View.OnClickListener {
            Log.d("Scan2Enter", "Scanner chiuso con tocco")
            hide()
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
            preview,
            lifecycleOwner!!
        ) { barcode ->

            if (closing) return@start

            closing = true

            scanSession.onBarcodeRead(barcode) {

                hide()

            }

        }
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
        closing = false
    }

    private fun dp(value: Int): Int {

        return (
                value *
                        context.resources.displayMetrics.density
                ).toInt()
    }

}