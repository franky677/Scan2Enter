package com.scan2enter.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.scan2enter.BuildFlags
import com.scan2enter.MainActivity
import com.scan2enter.R
import com.scan2enter.model.ProductInfo
import com.scan2enter.model.ProductInfoStore
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW_PRODUCT_INFO =
            "com.scan2enter.action.SHOW_PRODUCT_INFO"

        const val ACTION_UPDATE_PRODUCT_INFO =
            "com.scan2enter.action.UPDATE_PRODUCT_INFO"

        const val ACTION_ENABLE_PRODUCT_INFO_TOUCH_THROUGH =
            "com.scan2enter.action.ENABLE_PRODUCT_INFO_TOUCH_THROUGH"

        const val ACTION_DISABLE_PRODUCT_INFO_TOUCH_THROUGH =
            "com.scan2enter.action.DISABLE_PRODUCT_INFO_TOUCH_THROUGH"

        const val ACTION_PREPARE_SCANNER =
            "com.scan2enter.action.PREPARE_SCANNER"

        const val ACTION_OPEN_SCANNER =
            "com.scan2enter.action.OPEN_SCANNER"

        const val EXTRA_WORKFLOW_COMPLETED =
            "com.scan2enter.extra.WORKFLOW_COMPLETED"

        private const val CLICK_THRESHOLD = 12f
        private const val COMPLETED_POPUP_DURATION_MS = 6000L
        private const val MANUAL_POPUP_DURATION_MS = 6000L
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

    private val popupHandler = Handler(Looper.getMainLooper())

    private var productInfoPopup: View? = null
    private var productInfoPopupParams: WindowManager.LayoutParams? = null

    private var priceValueText: TextView? = null
    private var articleCodeValueText: TextView? = null
    private var barcodeValueText: TextView? = null
    private var barcodeImageView: ImageView? = null
    private var descriptionValueText: TextView? = null
    private var yearValueText: TextView? = null
    private var seasonValueText: TextView? = null
    private var taxablePriceValueText: TextView? = null
    private var vatRateValueText: TextView? = null
    private var stockValueText: TextView? = null

    private var historyPopup: View? = null

    private val dismissPopupRunnable = Runnable {
        removeProductInfoPopup()
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

        when (intent?.action) {
            ACTION_SHOW_PRODUCT_INFO -> {
                val completed = intent.getBooleanExtra(
                    EXTRA_WORKFLOW_COMPLETED,
                    false
                )

                android.util.Log.d(
                    "OverlayService",
                    "RICHIESTA APERTURA POPUP PROGRESSIVO completed=$completed"
                )

                showOrUpdateProductInfoPopup(
                    workflowCompleted = completed,
                    manualOpen = false
                )
            }

            ACTION_UPDATE_PRODUCT_INFO -> {
                val completed = intent.getBooleanExtra(
                    EXTRA_WORKFLOW_COMPLETED,
                    false
                )

                android.util.Log.d(
                    "OverlayService",
                    "RICHIESTA AGGIORNAMENTO POPUP completed=$completed"
                )

                showOrUpdateProductInfoPopup(
                    workflowCompleted = completed,
                    manualOpen = false
                )
            }

            ACTION_ENABLE_PRODUCT_INFO_TOUCH_THROUGH -> {
                setProductInfoTouchThrough(enabled = true)
            }

            ACTION_DISABLE_PRODUCT_INFO_TOUCH_THROUGH -> {
                setProductInfoTouchThrough(enabled = false)
            }

            ACTION_OPEN_SCANNER -> {
                android.util.Log.d(
                    "OverlayService",
                    "SCHERMATA INFORMAZIONI PRONTA - APRO SCANNER"
                )

                if (BuildFlags.USE_NEW_SCANNER) {
                    scanOverlay.show()
                } else {
                    val scannerIntent = Intent(
                        this,
                        MainActivity::class.java
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    startActivity(scannerIntent)
                }
            }
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        ProductInfoStore.initialize(applicationContext)

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

            showHistoryPopup()
        }

        scannerArea.setOnClickListener {
            if (isDragging) return@setOnClickListener

            /*
             * Prima di mostrare CameraX chiedo al servizio Accessibility
             * di aprire la schermata Informazioni di Due Retail.
             */
            sendBroadcast(
                Intent(ACTION_PREPARE_SCANNER).apply {
                    setPackage(packageName)
                }
            )

            android.util.Log.d(
                "OverlayService",
                "RICHIESTA PREPARAZIONE DUE RETAIL"
            )
        }

        /*
         * Lo stesso listener viene applicato alla Dock e ai due pulsanti.
         *
         * Gli ImageButton intercettano normalmente gli eventi touch e,
         * se il listener fosse assegnato solo a dockView, il trascinamento
         * non partirebbe quando il dito si trova sopra infoArea o scannerArea.
         */
        val dockTouchListener = View.OnTouchListener { touchedView, event ->
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
                        (abs(dx.toFloat()) > CLICK_THRESHOLD ||
                                abs(dy.toFloat()) > CLICK_THRESHOLD)
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
                    if (isDragging) {
                        snapToEdge()
                    } else {
                        touchedView.performClick()
                    }

                    isDragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    saveCurrentPosition()
                    true
                }

                else -> false
            }
        }

        dockView.setOnTouchListener(dockTouchListener)
        infoArea.setOnTouchListener(dockTouchListener)
        scannerArea.setOnTouchListener(dockTouchListener)
    }

    /**
     * Mostra la cronologia degli ultimi articoli letti.
     *
     * La cronologia è già mantenuta in RAM da ProductInfoStore e viene
     * soltanto letta quando l'utente preme il pulsante con lo scatolone.
     * Non viene quindi aggiunto alcun lavoro al workflow di scansione.
     */
    private fun showHistoryPopup() {
        if (historyPopup != null) {
            return
        }

        removeProductInfoPopup()

        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val history = ProductInfoStore.getHistory()

        val overlayRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * density).toInt(),
                (18 * density).toInt(),
                (20 * density).toInt(),
                (18 * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = 22 * density
            }
            elevation = 14 * density
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "Cronologia articoli"
            textSize = 24f
            setTextColor(Color.BLACK)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val closeButton = TextView(this).apply {
            text = "✕"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(
                (12 * density).toInt(),
                (4 * density).toInt(),
                (4 * density).toInt(),
                (4 * density).toInt()
            )
            isClickable = true
            setOnClickListener {
                removeHistoryPopup()
            }
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(
            closeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        card.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(this).apply {
            text = if (history.isEmpty()) {
                "Nessun articolo presente"
            } else {
                "${history.size} articoli recenti"
            }
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(
                0,
                (4 * density).toInt(),
                0,
                (12 * density).toInt()
            )
        }

        card.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (history.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "Effettua una scansione completa per aggiungere il primo articolo."
                textSize = 17f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(
                    (8 * density).toInt(),
                    (40 * density).toInt(),
                    (8 * density).toInt(),
                    (40 * density).toInt()
                )
            }

            listContainer.addView(
                emptyText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        } else {
            history.forEachIndexed { index, product ->
                listContainer.addView(
                    createHistoryItemView(
                        product = product,
                        position = index + 1
                    ),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (10 * density).toInt()
                    }
                )
            }
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(
                listContainer,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        card.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val horizontalMargin = (18 * density).toInt()
        val verticalMargin = (36 * density).toInt()

        val cardWidth = min(
            (430 * density).toInt(),
            screenWidth - horizontalMargin * 2
        )

        val cardHeight = min(
            (720 * density).toInt(),
            screenHeight - verticalMargin * 2
        )

        overlayRoot.addView(
            card,
            FrameLayout.LayoutParams(
                cardWidth,
                cardHeight
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        val popupParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1.0f
        }

        historyPopup = overlayRoot
        windowManager.addView(
            overlayRoot,
            popupParams
        )

        android.util.Log.d(
            "OverlayService",
            "CRONOLOGIA APERTA elementi=${history.size}"
        )
    }

    private fun createHistoryItemView(
        product: ProductInfo,
        position: Int
    ): View {
        val density = resources.displayMetrics.density

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true

            setOnClickListener {
                ProductInfoStore.current = product
                removeHistoryPopup()

                showOrUpdateProductInfoPopup(
                    workflowCompleted = true,
                    manualOpen = true
                )

                android.util.Log.d(
                    "OverlayService",
                    "ARTICOLO CRONOLOGIA APERTO EAN=${product.barcode}"
                )
            }

            setPadding(
                (14 * density).toInt(),
                (12 * density).toInt(),
                (14 * density).toInt(),
                (12 * density).toInt()
            )

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(245, 245, 245))
                cornerRadius = 14 * density
                setStroke(
                    (1 * density).toInt().coerceAtLeast(1),
                    Color.LTGRAY
                )
            }

            val description = product.description
                .trim()
                .ifEmpty { "Articolo senza descrizione" }

            val titleText = TextView(this@OverlayService).apply {
                text = "$position. $description"
                textSize = 17f
                setTextColor(Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            addView(
                titleText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            val details = buildString {
                if (product.articleCode.isNotBlank()) {
                    append("Codice: ${product.articleCode.trim()}")
                }

                if (product.barcode.isNotBlank()) {
                    if (isNotEmpty()) append("   •   ")
                    append("EAN: ${product.barcode.trim()}")
                }

                if (product.publicPrice.isNotBlank()) {
                    append("\nPrezzo: ${product.publicPrice.trim()}")
                }

                if (product.stock.isNotBlank()) {
                    if (product.publicPrice.isNotBlank()) {
                        append("   •   ")
                    } else {
                        append("\n")
                    }
                    append("Giacenza: ${product.stock.trim()}")
                }
            }.ifEmpty {
                "Dati aggiuntivi non disponibili"
            }

            val detailText = TextView(this@OverlayService).apply {
                text = details
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(
                    0,
                    (6 * density).toInt(),
                    0,
                    0
                )
            }

            addView(
                detailText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun removeHistoryPopup() {
        val popup = historyPopup ?: return

        try {
            windowManager.removeView(popup)
        } catch (_: Exception) {
        }

        historyPopup = null

        android.util.Log.d(
            "OverlayService",
            "CRONOLOGIA CHIUSA"
        )
    }

    /**
     * Mostra una sola istanza del popup e ne aggiorna il testo.
     *
     * Il popup usa FLAG_NOT_FOCUSABLE e FLAG_NOT_TOUCHABLE: rimane sopra
     * Due Retail senza diventare la finestra attiva e senza ostacolare i
     * tap/dispatchGesture del servizio Accessibility.
     */
    private fun showOrUpdateProductInfoPopup(
        workflowCompleted: Boolean,
        manualOpen: Boolean
    ) {
        val product = ProductInfoStore.current

        if (productInfoPopup == null) {
            createProductInfoPopup()
        }

        updateProductInfoPopup(
            product = product,
            workflowCompleted = workflowCompleted
        )

        popupHandler.removeCallbacks(dismissPopupRunnable)

        if (workflowCompleted || manualOpen) {
            popupHandler.postDelayed(
                dismissPopupRunnable,
                if (manualOpen) {
                    MANUAL_POPUP_DURATION_MS
                } else {
                    COMPLETED_POPUP_DURATION_MS
                }
            )
        }

        android.util.Log.d(
            "OverlayService",
            "POPUP AGGIORNATO completed=$workflowCompleted manual=$manualOpen"
        )
    }

    private fun createProductInfoPopup() {
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        /*
         * La finestra overlay occupa tutto lo schermo.
         * Il wrapper scuro ricrea la separazione visiva del vecchio dialogo,
         * mentre la card interna resta completamente bianca e opaca.
         */
        val overlayRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 1.0f
            clipChildren = false
            clipToPadding = false
        }

        val popupView = LayoutInflater.from(this)
            .inflate(R.layout.product_info_popup, overlayRoot, false)

        descriptionValueText =
            popupView.findViewById(R.id.productDescriptionText)

        articleCodeValueText =
            popupView.findViewById(R.id.productArticleCodeText)

        barcodeValueText =
            popupView.findViewById(R.id.productBarcodeText)

        barcodeImageView =
            popupView.findViewById(R.id.productBarcodeImage)

        taxablePriceValueText =
            popupView.findViewById(R.id.productTaxablePriceText)

        vatRateValueText =
            popupView.findViewById(R.id.productVatRateText)

        priceValueText =
            popupView.findViewById(R.id.productPublicPriceText)

        seasonValueText =
            popupView.findViewById(R.id.productSeasonText)

        yearValueText =
            popupView.findViewById(R.id.productYearText)

        stockValueText =
            popupView.findViewById(R.id.productStockText)

        // Il popup resta non interattivo durante il workflow Accessibility.
        popupView.findViewById<TextView>(R.id.closeProductInfoButton)
            .visibility = View.GONE

        val horizontalMargin = (24 * density).toInt()
        val verticalMargin = (42 * density).toInt()
        val bottomBreathingRoom = (14 * density).toInt()
        val shadowOffset = (5 * density).toInt()

        val popupWidth = min(
            (390 * density).toInt(),
            screenWidth - horizontalMargin * 2
        )

        val popupMaxHeight = screenHeight - verticalMargin * 2

        /*
         * La card bianca resta completamente opaca.
         * Il padding inferiore aggiunge circa 3-4 mm sotto la giacenza.
         */
        popupView.setPadding(
            popupView.paddingLeft,
            popupView.paddingTop,
            popupView.paddingRight,
            popupView.paddingBottom + bottomBreathingRoom
        )

        popupView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = 22 * density
        }
        popupView.alpha = 1.0f
        popupView.clipToOutline = true
        popupView.outlineProvider = ViewOutlineProvider.BACKGROUND
        popupView.elevation = 14 * density

        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(
                popupWidth,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.makeMeasureSpec(
                popupMaxHeight - shadowOffset,
                View.MeasureSpec.AT_MOST
            )
        )

        val cardHeight = min(
            popupView.measuredHeight,
            popupMaxHeight - shadowOffset
        )

        /*
         * Base nera leggermente spostata verso il basso:
         * ricrea profondità visiva senza rendere trasparente la card.
         */
        val cardContainer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }

        val grayBase = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.BLACK)
                cornerRadius = 22 * density
            }
        }

        val grayBaseParams = FrameLayout.LayoutParams(
            popupWidth,
            cardHeight
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = shadowOffset
        }

        val whiteCardParams = FrameLayout.LayoutParams(
            popupWidth,
            cardHeight
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        cardContainer.addView(grayBase, grayBaseParams)
        cardContainer.addView(popupView, whiteCardParams)

        val containerParams = FrameLayout.LayoutParams(
            popupWidth,
            cardHeight + shadowOffset
        ).apply {
            gravity = Gravity.CENTER
        }

        overlayRoot.addView(cardContainer, containerParams)

        val popupParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1.0f
        }

        productInfoPopup = overlayRoot
        productInfoPopupParams = popupParams
        windowManager.addView(overlayRoot, popupParams)
    }

    /**
     * Abilita o disabilita il passaggio dei tocchi senza nascondere
     * e senza ricreare la superficie grafica del popup.
     *
     * Quando FLAG_NOT_TOUCHABLE è attivo, Android può limitare
     * temporaneamente l'alpha della finestra overlay a 0,80.
     * Quando viene rimosso, la finestra torna opaca al 100%.
     */
    private fun setProductInfoTouchThrough(enabled: Boolean) {
        val popup = productInfoPopup ?: return
        val params = productInfoPopupParams ?: return

        val newFlags =
            if (enabled) {
                params.flags or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.flags and
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }

        if (params.flags == newFlags) {
            android.util.Log.d(
                "OverlayService",
                "TOUCH THROUGH GIA' ${if (enabled) "ATTIVO" else "DISATTIVO"}"
            )
            return
        }

        params.flags = newFlags
        params.alpha = 1.0f

        try {
            windowManager.updateViewLayout(
                popup,
                params
            )

            android.util.Log.d(
                "OverlayService",
                if (enabled) {
                    "TOUCH THROUGH ATTIVATO - POPUP SEMPRE VISIBILE"
                } else {
                    "TOUCH THROUGH DISATTIVATO - POPUP OPACO"
                }
            )
        } catch (error: Exception) {
            android.util.Log.e(
                "OverlayService",
                "ERRORE AGGIORNAMENTO TOUCH THROUGH",
                error
            )
        }
    }

    private fun updateProductInfoPopup(
        product: ProductInfo?,
        workflowCompleted: Boolean
    ) {
        fun valueOrLoading(value: String): String =
            value.trim().takeIf { it.isNotEmpty() } ?: "lettura…"

        if (product == null) {
            priceValueText?.text = "—"
            articleCodeValueText?.text = "—"
            barcodeValueText?.text = "—"
            barcodeImageView?.visibility = View.GONE
            descriptionValueText?.text = "Nessun articolo letto"
            yearValueText?.text = "—"
            seasonValueText?.text = "—"
            taxablePriceValueText?.text = "—"
            vatRateValueText?.text = "—"
            stockValueText?.text = "—"
            return
        }

        // Il prezzo è già disponibile quando il popup viene aperto e viene
        // aggiornato per primo. Gli altri campi arrivano progressivamente.
        priceValueText?.text = valueOrLoading(product.publicPrice)
        articleCodeValueText?.text = valueOrLoading(product.articleCode)
        barcodeValueText?.text = valueOrLoading(product.barcode)
        descriptionValueText?.text = valueOrLoading(product.description)
        taxablePriceValueText?.text = valueOrLoading(product.taxablePrice)
        vatRateValueText?.text = valueOrLoading(product.vatRate)
        seasonValueText?.text = valueOrLoading(product.season)
        yearValueText?.text = valueOrLoading(product.year)
        stockValueText?.text = valueOrLoading(product.stock)

        val barcodeBitmap = createEan13Bitmap(product.barcode)

        if (barcodeBitmap != null) {
            barcodeImageView?.setImageBitmap(barcodeBitmap)
            barcodeImageView?.visibility = View.VISIBLE
        } else {
            barcodeImageView?.setImageDrawable(null)
            barcodeImageView?.visibility = View.GONE
        }

        android.util.Log.d(
            "OverlayService",
            "POPUP GRAFICO AGGIORNATO completed=$workflowCompleted " +
                    "EAN=${product.barcode} barcodeGraphic=${barcodeBitmap != null}"
        )
    }

    private fun removeProductInfoPopup() {
        val popup = productInfoPopup ?: return

        try {
            windowManager.removeView(popup)
        } catch (_: Exception) {
        }

        productInfoPopup = null
        productInfoPopupParams = null
        priceValueText = null
        articleCodeValueText = null
        barcodeValueText = null
        barcodeImageView = null
        descriptionValueText = null
        yearValueText = null
        seasonValueText = null
        taxablePriceValueText = null
        vatRateValueText = null
        stockValueText = null
    }

    /**
     * Genera il barcode EAN-13 grafico senza dipendenze esterne.
     */
    private fun createEan13Bitmap(rawValue: String): Bitmap? {
        val ean = rawValue.filter(Char::isDigit)

        if (ean.length != 13 || !isValidEan13(ean)) {
            return null
        }

        val leftPatterns = arrayOf(
            arrayOf("0001101", "0100111", "1110010"),
            arrayOf("0011001", "0110011", "1100110"),
            arrayOf("0010011", "0011011", "1101100"),
            arrayOf("0111101", "0100001", "1000010"),
            arrayOf("0100011", "0011101", "1011100"),
            arrayOf("0110001", "0111001", "1001110"),
            arrayOf("0101111", "0000101", "1010000"),
            arrayOf("0111011", "0010001", "1000100"),
            arrayOf("0110111", "0001001", "1001000"),
            arrayOf("0001011", "0010111", "1110100")
        )

        val parityPatterns = arrayOf(
            "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
            "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"
        )

        val rightPatterns = arrayOf(
            "1110010", "1100110", "1101100", "1000010", "1011100",
            "1001110", "1010000", "1000100", "1001000", "1110100"
        )

        val modules = StringBuilder(95)
        modules.append("101")

        val firstDigit = ean[0].digitToInt()
        val parity = parityPatterns[firstDigit]

        for (index in 1..6) {
            val digit = ean[index].digitToInt()
            val patternIndex = if (parity[index - 1] == 'L') 0 else 1
            modules.append(leftPatterns[digit][patternIndex])
        }

        modules.append("01010")

        for (index in 7..12) {
            modules.append(rightPatterns[ean[index].digitToInt()])
        }

        modules.append("101")

        val quietZoneModules = 11
        val moduleWidth = 4
        val bitmapWidth = (modules.length + quietZoneModules * 2) * moduleWidth
        val bitmapHeight = 220

        val bitmap = Bitmap.createBitmap(
            bitmapWidth,
            bitmapHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.eraseColor(Color.WHITE)

        var x = quietZoneModules * moduleWidth

        modules.forEach { module ->
            if (module == '1') {
                for (barX in x until x + moduleWidth) {
                    for (barY in 0 until bitmapHeight) {
                        bitmap.setPixel(barX, barY, Color.BLACK)
                    }
                }
            }
            x += moduleWidth
        }

        return bitmap
    }

    private fun isValidEan13(value: String): Boolean {
        if (value.length != 13 || value.any { !it.isDigit() }) {
            return false
        }

        var sum = 0

        for (index in 0 until 12) {
            val digit = value[index].digitToInt()
            sum += if (index % 2 == 0) digit else digit * 3
        }

        val expectedCheckDigit = (10 - sum % 10) % 10
        return value[12].digitToInt() == expectedCheckDigit
    }

    private fun clampVertical() {
        val displayHeight = resources.displayMetrics.heightPixels
        val dockHeight = dockView.height.takeIf { it > 0 } ?: layoutParams.height

        layoutParams.y = max(
            0,
            min(
                layoutParams.y,
                displayHeight - dockHeight
            )
        )
    }

    private fun updateDockPosition() {
        windowManager.updateViewLayout(
            dockView,
            layoutParams
        )
    }

    private fun saveCurrentPosition() {
        OverlayPosition.save(
            this,
            layoutParams.x,
            layoutParams.y
        )
    }

    private fun snapToEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val dockWidth = dockView.width.takeIf { it > 0 } ?: layoutParams.width

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
        removeProductInfoPopup()
        removeHistoryPopup()

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