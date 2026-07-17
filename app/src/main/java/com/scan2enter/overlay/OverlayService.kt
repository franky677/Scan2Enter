package com.scan2enter.overlay

import android.app.AlertDialog
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import com.scan2enter.BuildFlags
import com.scan2enter.MainActivity
import com.scan2enter.R
import com.scan2enter.model.ProductInfoStore
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW_PRODUCT_INFO =
            "com.scan2enter.action.SHOW_PRODUCT_INFO"

        private const val CLICK_THRESHOLD = 12f
        private const val POPUP_DURATION_MS = 4000L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var dockView: View

    private lateinit var infoArea: ImageButton
    private lateinit var scannerArea: ImageButton

    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var scanOverlay: ScanOverlay

    private var startX = 0
    private var startY = 0

    private var touchStartX = 0f
    private var touchStartY = 0f

    private var isDragging = false

    private val popupHandler =
        Handler(Looper.getMainLooper())

    private var productInfoDialog: AlertDialog? = null

    private val dismissPopupRunnable =
        Runnable {
            productInfoDialog?.dismiss()
            productInfoDialog = null

            android.util.Log.d(
                "OverlayService",
                "POPUP INFORMAZIONI CHIUSO AUTOMATICAMENTE"
            )
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_SHOW_PRODUCT_INFO) {

            android.util.Log.d(
                "OverlayService",
                "RICHIESTA POPUP INFORMAZIONI AUTOMATICO RICEVUTA"
            )

            showProductInfoPopup()
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        scanOverlay = ScanOverlay(this)

        dockView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_button, null)

        infoArea = dockView.findViewById(R.id.infoArea)
        scannerArea = dockView.findViewById(R.id.scannerArea)

        val density = resources.displayMetrics.density

        val dockWidth = (80 * density).toInt()
        val dockHeight = (200 * density).toInt()

        layoutParams = WindowManager.LayoutParams(
            dockWidth,
            dockHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = OverlayPosition.getX(this@OverlayService)
            y = OverlayPosition.getY(this@OverlayService)
        }

        dockView.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        )

        android.util.Log.d(
            "OverlayService",
            "Dock size = ${dockView.measuredWidth} x ${dockView.measuredHeight}"
        )

        windowManager.addView(dockView, layoutParams)

        infoArea.setOnClickListener {

            if (isDragging) return@setOnClickListener

            showProductInfoPopup()
        }

        scannerArea.setOnClickListener {

            if (isDragging) return@setOnClickListener

            if (BuildFlags.USE_NEW_SCANNER) {

                scanOverlay.show()

            } else {

                val intent = Intent(
                    this,
                    MainActivity::class.java
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                startActivity(intent)
            }
        }

        dockView.setOnTouchListener { _, event ->

            android.util.Log.d(
                "OverlayDrag",
                "ACTION = ${event.actionMasked}"
            )

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {

                    startX = layoutParams.x
                    startY = layoutParams.y

                    touchStartX = event.rawX
                    touchStartY = event.rawY

                    isDragging = false

                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()

                    if (!isDragging &&
                        (
                                abs(dx.toFloat()) > CLICK_THRESHOLD ||
                                        abs(dy.toFloat()) > CLICK_THRESHOLD
                                )
                    ) {
                        isDragging = true
                    }

                    if (isDragging) {

                        layoutParams.x = startX + dx
                        layoutParams.y = startY + dy

                        clampVertical()

                        windowManager.updateViewLayout(
                            dockView,
                            layoutParams
                        )
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {

                    if (!isDragging) {
                        dockView.performClick()
                    } else {
                        snapToEdge()
                    }

                    isDragging = false

                    true
                }

                MotionEvent.ACTION_CANCEL -> {

                    isDragging = false
                    saveCurrentPosition()

                    false
                }

                else -> false
            }
        }
    }

    private fun showProductInfoPopup() {

        val product = ProductInfoStore.current

        val message = if (product == null) {

            "Nessun articolo letto."

        } else {

            """
            Codice: ${product.articleCode}

            EAN: ${product.barcode}

            Descrizione: ${product.description}

            Imponibile: ${product.taxablePrice}

            IVA: ${product.vatRate}

            Prezzo pubblico: ${product.publicPrice}

            Anno: ${product.year}

            Stagione: ${product.season}

            Giacenza: ${product.stock}
            """.trimIndent()
        }

        popupHandler.removeCallbacks(dismissPopupRunnable)

        productInfoDialog?.dismiss()
        productInfoDialog = null

        val dialog = AlertDialog.Builder(this)
            .setTitle("Informazioni articolo")
            .setMessage(message)
            .setPositiveButton("CHIUDI") { currentDialog, _ ->
                popupHandler.removeCallbacks(dismissPopupRunnable)
                currentDialog.dismiss()
                productInfoDialog = null
            }
            .create()

        dialog.window?.setType(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        )

        dialog.setOnDismissListener {
            popupHandler.removeCallbacks(dismissPopupRunnable)

            if (productInfoDialog === dialog) {
                productInfoDialog = null
            }
        }

        productInfoDialog = dialog

        dialog.show()

        popupHandler.postDelayed(
            dismissPopupRunnable,
            POPUP_DURATION_MS
        )

        android.util.Log.d(
            "OverlayService",
            "POPUP INFORMAZIONI APERTO PER 4 SECONDI"
        )
    }

    /**
     * Impedisce alla Dock di uscire verticalmente dallo schermo.
     */
    private fun clampVertical() {

        val displayHeight = resources.displayMetrics.heightPixels

        val dockHeight =
            dockView.height.takeIf { it > 0 } ?: layoutParams.height

        layoutParams.y = max(
            0,
            min(
                layoutParams.y,
                displayHeight - dockHeight
            )
        )
    }

    /**
     * Aggiorna la posizione della Dock.
     */
    private fun updateDockPosition() {

        windowManager.updateViewLayout(
            dockView,
            layoutParams
        )
    }

    /**
     * Salva la posizione corrente.
     */
    private fun saveCurrentPosition() {

        OverlayPosition.save(
            this,
            layoutParams.x,
            layoutParams.y
        )
    }

    /**
     * Effettua lo snap automatico al bordo sinistro o destro.
     */
    private fun snapToEdge() {

        val screenWidth =
            resources.displayMetrics.widthPixels

        val dockWidth =
            dockView.width.takeIf { it > 0 } ?: layoutParams.width

        layoutParams.x =
            if (layoutParams.x + dockWidth / 2 < screenWidth / 2) {
                0
            } else {
                screenWidth - dockWidth
            }

        clampVertical()

        updateDockPosition()

        saveCurrentPosition()
    }

    override fun onDestroy() {

        popupHandler.removeCallbacks(dismissPopupRunnable)

        productInfoDialog?.dismiss()
        productInfoDialog = null

        try {
            scanOverlay.hide()
        } catch (_: Exception) {
        }

        if (::windowManager.isInitialized && ::dockView.isInitialized) {

            try {
                windowManager.removeView(dockView)
            } catch (_: Exception) {
            }
        }

        super.onDestroy()
    }
}

