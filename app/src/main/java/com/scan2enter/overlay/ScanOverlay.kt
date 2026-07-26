package com.scan2enter.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.camera.view.PreviewView
import com.scan2enter.overlay.camera.OverlayCameraManager
import com.scan2enter.overlay.camera.OverlayLifecycleOwner
import com.scan2enter.scanner.ScanConfig
import com.scan2enter.scanner.ScanSession

class ScanOverlay(
    private val context: Context
) {

    companion object {
        /*
         * La finestra non è più centrata verticalmente.
         * Il bordo superiore resta vicino alla fotocamera dello S24 Ultra,
         * così il mirino coincide meglio con la direzione reale di ripresa.
         */
        private const val SCANNER_TOP_MARGIN_DP = 72
        private const val RAPID_SCAN_TIMEOUT_MS = 3_000L
    }

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val cameraManager =
        OverlayCameraManager(context)

    private val scanSession =
        ScanSession(context)

    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var container: FrameLayout? = null
    private var previewView: PreviewView? = null
    private var torchButton: TextView? = null

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
                RAPID_SCAN_TIMEOUT_MS
            } else {
                ScanConfig.SCAN_TIMEOUT
            }
        )

        val frame = FrameLayout(context)

        val closeScanner = android.view.View.OnClickListener {
            Log.d("Scan2Enter", "Scanner chiuso con tocco")
            hide()
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

        val overlayView = ScanOverlayView(context)
        overlayView.setOnClickListener(closeScanner)

        frame.addView(
            overlayView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val torchControl = createTorchButton()
        frame.addView(
            torchControl,
            FrameLayout.LayoutParams(
                dp(92),
                dp(42)
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(10)
                marginEnd = dp(10)
            }
        )

        /*
         * Il pulsante deve intercettare il tocco senza chiudere lo scanner.
         */
        torchControl.setOnClickListener {
            cameraManager.cycleTorchMode()
        }

        val params = WindowManager.LayoutParams(
            dp(ScanConfig.OVERLAY_WIDTH_DP),
            dp(ScanConfig.OVERLAY_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(SCANNER_TOP_MARGIN_DP)
        }

        windowManager.addView(frame, params)

        container = frame
        previewView = preview
        torchButton = torchControl

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
            },
            onTorchStateChanged = { mode, enabled, available ->
                updateTorchButton(
                    mode = mode,
                    enabled = enabled,
                    available = available
                )
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
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }

        container = null
        previewView = null
        torchButton = null
        rapidRescan = false
        closing = false
    }

    private fun createTorchButton(): TextView {
        return TextView(context).apply {
            text = "💡 AUTO"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
            isClickable = true
            isFocusable = true

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.argb(210, 20, 20, 20))
                cornerRadius = dp(12).toFloat()
                setStroke(
                    dp(1).coerceAtLeast(1),
                    Color.WHITE
                )
            }
        }
    }

    private fun updateTorchButton(
        mode: OverlayCameraManager.TorchMode,
        enabled: Boolean,
        available: Boolean
    ) {
        val button = torchButton ?: return

        if (!available) {
            button.text = "💡 N/D"
            button.alpha = 0.55f
            button.isEnabled = false
            return
        }

        button.alpha = 1.0f
        button.isEnabled = true

        button.text = when (mode) {
            OverlayCameraManager.TorchMode.AUTO -> {
                if (enabled) {
                    "💡 AUTO ON"
                } else {
                    "💡 AUTO"
                }
            }

            OverlayCameraManager.TorchMode.ON -> {
                "💡 ON"
            }

            OverlayCameraManager.TorchMode.OFF -> {
                "💡 OFF"
            }
        }
    }

    private fun dp(value: Int): Int {
        return (
                value *
                        context.resources.displayMetrics.density
                ).toInt()
    }
}