package com.scan2enter.overlay

import android.app.Service
import android.content.Context
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
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.scan2enter.BuildFlags
import com.scan2enter.MainActivity
import com.scan2enter.R
import com.scan2enter.feedback.ScanFeedbackManager
import com.scan2enter.model.ProductInfo
import com.scan2enter.model.ProductInfoStore
import com.scan2enter.repository.ProductRepositoryProvider
import com.scan2enter.reorder.ReorderStore
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

        const val ACTION_OPEN_RAPID_SCANNER =
            "com.scan2enter.action.OPEN_RAPID_SCANNER"

        const val ACTION_SHOW_SCAN_ERROR =
            "com.scan2enter.action.SHOW_SCAN_ERROR"

        const val EXTRA_SCAN_ERROR_MESSAGE =
            "com.scan2enter.extra.SCAN_ERROR_MESSAGE"

        const val EXTRA_WORKFLOW_COMPLETED =
            "com.scan2enter.extra.WORKFLOW_COMPLETED"

        private const val CLICK_THRESHOLD = 12f
        private const val DEFAULT_POPUP_DURATION_SECONDS = 4
        private const val MIN_POPUP_DURATION_SECONDS = 1
        private const val MAX_POPUP_DURATION_SECONDS = 10
        private const val AUTO_REGULAR_DURATION_MS = 1000L
        private const val AUTO_WARNING_DURATION_MS = 3000L
        private const val AUTO_REORDER_DURATION_MS = 8000L
        private const val SCAN_ERROR_DURATION_MS = 2200L

        private const val POPUP_PREFS = "product_popup_preferences"
        private const val POPUP_MODE_AUTO_KEY = "popup_mode_auto"
        private const val POPUP_MANUAL_SECONDS_KEY = "popup_manual_seconds"

        private const val WORKFLOW_PREFS = "scan_workflow"
        private const val WORKFLOW_MODE_KEY = "mode"
        private const val MODE_INFO = "INFO"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var dockView: View

    private lateinit var infoArea: ImageButton
    private lateinit var scannerArea: ImageButton
    private lateinit var reorderBadgeText: TextView

    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var scanOverlay: ScanOverlay

    private val productRepository by lazy {
        ProductRepositoryProvider.get(applicationContext)
    }

    private var startX = 0
    private var startY = 0

    private var touchStartX = 0f
    private var touchStartY = 0f

    private var isDragging = false

    private val reorderStoreListener: (Int) -> Unit = { count ->
        popupHandler.post {
            updateReorderBadge(count)
        }
    }

    private val popupHandler = Handler(Looper.getMainLooper())

    private var productInfoPopup: View? = null
    private var productInfoPopupParams: WindowManager.LayoutParams? = null
    private var stockEditPopup: View? = null

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
    private var stockStatusContainer: LinearLayout? = null
    private var stockStatusText: TextView? = null
    private var reorderText: TextView? = null
    private var availableStockValueText: TextView? = null
    private var minimumStockValueText: TextView? = null
    private var reorderLotValueText: TextView? = null
    private var popupDurationSeekBar: SeekBar? = null
    private var popupDurationModeButton: TextView? = null
    private var popupDurationValueText: TextView? = null

    private var isPopupDurationAuto = false
    private var manualPopupDurationSeconds = DEFAULT_POPUP_DURATION_SECONDS
    private var popupTimerPausedByUser = false

    private var historyPopup: View? = null
    private var scanErrorPopup: View? = null

    private val dismissScanErrorRunnable = Runnable {
        removeScanErrorPopup()
    }

    private enum class StockSoundStatus {
        REGULAR,
        WARNING,
        REORDER
    }

    private var reopenScannerAfterPopup = false

    private val dismissPopupRunnable = Runnable {
        removeProductInfoPopup()
        android.util.Log.d(
            "OverlayService",
            "POPUP INFORMAZIONI CHIUSO AUTOMATICAMENTE"
        )

        /*
         * Il ciclo automatico viene attivato soltanto nella schermata INFO.
         * Collo veloce ed Etichette conservano il loro comportamento attuale.
         */
        val shouldReopenScanner = reopenScannerAfterPopup
        reopenScannerAfterPopup = false

        if (
            shouldReopenScanner &&
            loadCurrentScanMode() == MODE_INFO
        ) {
            openRapidScanner()
        }
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

            ACTION_SHOW_SCAN_ERROR -> {
                val message = intent.getStringExtra(
                    EXTRA_SCAN_ERROR_MESSAGE
                ) ?: "Lettura errata\nRiprovare"

                showScanErrorPopup(message)
            }

            ACTION_OPEN_SCANNER -> {
                android.util.Log.d(
                    "OverlayService",
                    "SCHERMATA INFORMAZIONI PRONTA - APRO SCANNER"
                )

                if (BuildFlags.USE_NEW_SCANNER) {
                    scanOverlay.show(rapidRescan = false)
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

            ACTION_OPEN_RAPID_SCANNER -> {
                openRapidScanner()
            }
        }

        return START_STICKY
    }

    private fun loadCurrentScanMode(): String {
        return applicationContext
            .getSharedPreferences(
                WORKFLOW_PREFS,
                Context.MODE_PRIVATE
            )
            .getString(WORKFLOW_MODE_KEY, MODE_INFO)
            ?: MODE_INFO
    }

    private fun openRapidScanner() {
        if (!BuildFlags.USE_NEW_SCANNER) {
            return
        }

        android.util.Log.d(
            "OverlayService",
            "APERTURA SCANNER AUTOMATICO 2 SECONDI"
        )

        scanOverlay.show(rapidRescan = true)
    }

    override fun onCreate() {
        super.onCreate()

        ProductInfoStore.initialize(applicationContext)
        loadPopupDurationPreferences()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        scanOverlay = ScanOverlay(this)

        ScanFeedbackManager.initialize(applicationContext)

        dockView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_button, null)

        infoArea = dockView.findViewById(R.id.infoArea)
        scannerArea = dockView.findViewById(R.id.scannerArea)
        reorderBadgeText = dockView.findViewById(R.id.reorderBadgeText)

        ReorderStore.addSizeListener(reorderStoreListener)
        updateReorderBadge(ReorderStore.size())

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
     * Aggiorna il badge numerico sovrapposto allo scatolone.
     */
    private fun updateReorderBadge(count: Int) {
        if (!::reorderBadgeText.isInitialized) return

        if (count <= 0) {
            reorderBadgeText.visibility = View.GONE
            reorderBadgeText.text = ""
        } else {
            reorderBadgeText.visibility = View.VISIBLE
            reorderBadgeText.text = if (count > 99) "99+" else count.toString()
        }

        android.util.Log.d(
            "OverlayService",
            "BADGE RIORDINO AGGIORNATO count=$count"
        )
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
            workflowCompleted = workflowCompleted,
            playStockSound = workflowCompleted && !manualOpen
        )

        /*
         * Durante la lettura progressiva il popup lascia passare i tocchi
         * necessari al servizio Accessibility. Appena il prodotto è completo,
         * la finestra diventa interattiva e torna opaca al 100%.
         */
        setProductInfoTouchThrough(
            enabled = !workflowCompleted && !manualOpen
        )

        popupHandler.removeCallbacks(dismissPopupRunnable)
        reopenScannerAfterPopup = workflowCompleted && !manualOpen

        if (workflowCompleted || manualOpen) {
            scheduleProductPopupDismiss(product)
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

        stockStatusContainer =
            popupView.findViewById(R.id.productStockStatusContainer)

        stockStatusText =
            popupView.findViewById(R.id.productStockStatusText)

        reorderText =
            popupView.findViewById(R.id.productReorderText)

        availableStockValueText =
            popupView.findViewById(R.id.productAvailableStockText)

        minimumStockValueText =
            popupView.findViewById(R.id.productMinimumStockText)

        reorderLotValueText =
            popupView.findViewById(R.id.productReorderLotValueText)

        popupDurationSeekBar =
            popupView.findViewById(R.id.popupDurationSeekBar)

        popupDurationModeButton =
            popupView.findViewById(R.id.popupDurationModeButton)

        popupDurationValueText =
            popupView.findViewById(R.id.popupDurationValueText)

        configurePopupDurationControls()

        // Il pulsante resta nascosto: la chiusura automatica è già gestita.
        popupView.findViewById<TextView>(R.id.closeProductInfoButton)
            .visibility = View.GONE

        popupView.findViewById<View>(R.id.productStockCard)
            .setOnClickListener {
                showStockEditPopup()
            }

        // Margini ridotti per lasciare spazio al comando verticale senza
        // comprimere eccessivamente i dati principali del prodotto.
        val horizontalMargin = (6 * density).toInt()
        val topMargin = (42 * density).toInt()
        val bottomMargin = (8 * density).toInt()
        val bottomBreathingRoom = (22 * density).toInt()
        val shadowOffset = (5 * density).toInt()

        val popupWidth = min(
            (430 * density).toInt(),
            screenWidth - horizontalMargin * 2
        )

        // Più spazio verso il basso: circa mezzo centimetro utile in più.
        val popupMaxHeight = screenHeight - topMargin - bottomMargin

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
            this.topMargin = shadowOffset
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
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            this.topMargin = topMargin
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


    private fun showStockEditPopup() {
        if (stockEditPopup != null) return

        popupHandler.removeCallbacks(dismissPopupRunnable)

        val product = ProductInfoStore.current ?: return
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels

        val overlayRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 1.0f
        }

        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.stock_edit_dialog, overlayRoot, false)

        dialogView.findViewById<TextView>(R.id.stockEditArticleText).text =
            listOf(product.articleCode, product.description)
                .filter { it.isNotBlank() }
                .joinToString(" · ")

        val minimumEdit =
            dialogView.findViewById<EditText>(R.id.minimumStockEditText)
        val reorderEdit =
            dialogView.findViewById<EditText>(R.id.reorderLotEditText)

        minimumEdit.setText(product.minimumStock.ifBlank { "0" })
        reorderEdit.setText(product.reorderLot.ifBlank { "0" })

        bindQuantityButtons(
            minusButton = dialogView.findViewById(R.id.minimumStockMinusButton),
            plusButton = dialogView.findViewById(R.id.minimumStockPlusButton),
            editText = minimumEdit
        )

        bindQuantityButtons(
            minusButton = dialogView.findViewById(R.id.reorderLotMinusButton),
            plusButton = dialogView.findViewById(R.id.reorderLotPlusButton),
            editText = reorderEdit
        )

        dialogView.findViewById<View>(R.id.closeStockEditButton)
            .setOnClickListener { removeStockEditPopup() }

        dialogView.findViewById<View>(R.id.cancelStockEditButton)
            .setOnClickListener { removeStockEditPopup() }

        val saveButton = dialogView.findViewById<View>(R.id.saveStockEditButton)

        saveButton.setOnClickListener {
            val minimumStock = minimumEdit.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
            val reorderLot = reorderEdit.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()

            when {
                product.articleId <= 0L -> Toast.makeText(
                    this,
                    "ID articolo non disponibile. Ripetere la scansione.",
                    Toast.LENGTH_LONG
                ).show()

                minimumStock == null || minimumStock < 0.0 -> {
                    minimumEdit.error = "Inserire un valore valido"
                    minimumEdit.requestFocus()
                }

                reorderLot == null || reorderLot < 0.0 -> {
                    reorderEdit.error = "Inserire un valore valido"
                    reorderEdit.requestFocus()
                }

                else -> {
                    saveButton.isEnabled = false
                    minimumEdit.isEnabled = false
                    reorderEdit.isEnabled = false

                    android.util.Log.d(
                        "OverlayService",
                        "SALVATAGGIO SCORTE START articleId=${product.articleId} minimo=$minimumStock lotto=$reorderLot"
                    )

                    Thread {
                        productRepository.updateStockSettings(
                            articleId = product.articleId,
                            minimumStock = minimumStock,
                            reorderLot = reorderLot
                        ).onSuccess { updated ->
                            popupHandler.post {
                                val current = ProductInfoStore.current ?: product
                                val updatedProduct = current.copy(
                                    minimumStock = updated.minimumStock.formatStockQuantity(),
                                    maximumStock = updated.maximumStock.takeIf { it >= 0.0 }?.formatStockQuantity() ?: "",
                                    reorderLot = updated.reorderLot.formatStockQuantity()
                                )

                                ProductInfoStore.current = updatedProduct
                                ProductInfoStore.updateHistoryItem(updatedProduct)
                                updateProductInfoPopup(updatedProduct, true, false)

                                Toast.makeText(this, "Scorte aggiornate", Toast.LENGTH_SHORT).show()
                                android.util.Log.d(
                                    "OverlayService",
                                    "SALVATAGGIO SCORTE OK articleId=${updated.articleId} minimo=${updated.minimumStock} lotto=${updated.reorderLot}"
                                )
                                removeStockEditPopup()
                            }
                        }.onFailure { error ->
                            popupHandler.post {
                                saveButton.isEnabled = true
                                minimumEdit.isEnabled = true
                                reorderEdit.isEnabled = true
                                Toast.makeText(
                                    this,
                                    "Errore salvataggio scorte: ${error.message ?: "errore sconosciuto"}",
                                    Toast.LENGTH_LONG
                                ).show()
                                android.util.Log.e("OverlayService", "SALVATAGGIO SCORTE FALLITO", error)
                            }
                        }
                    }.start()
                }
            }
        }

        val horizontalMargin = (24 * density).toInt()
        val dialogWidth = min(
            (390 * density).toInt(),
            screenWidth - horizontalMargin * 2
        )

        dialogView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = 22 * density
        }
        dialogView.clipToOutline = true
        dialogView.outlineProvider = ViewOutlineProvider.BACKGROUND
        dialogView.elevation = 16 * density

        val dialogParams = FrameLayout.LayoutParams(
            dialogWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        overlayRoot.addView(dialogView, dialogParams)

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1.0f
        }

        stockEditPopup = overlayRoot
        windowManager.addView(overlayRoot, windowParams)
    }

    private fun bindQuantityButtons(
        minusButton: Button,
        plusButton: Button,
        editText: EditText
    ) {
        minusButton.setOnClickListener {
            changeQuantity(editText, -1.0)
        }
        plusButton.setOnClickListener {
            changeQuantity(editText, 1.0)
        }
    }

    private fun changeQuantity(
        editText: EditText,
        delta: Double
    ) {
        val current = editText.text
            ?.toString()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?: 0.0

        val updated = max(0.0, current + delta)
        editText.setText(
            if (updated == updated.toInt().toDouble()) {
                updated.toInt().toString()
            } else {
                updated.toString()
            }
        )
        editText.setSelection(editText.text.length)
    }

    private fun removeStockEditPopup() {
        val popup = stockEditPopup ?: return
        try {
            windowManager.removeView(popup)
        } catch (_: Exception) {
        }
        stockEditPopup = null

        popupHandler.removeCallbacks(dismissPopupRunnable)
        scheduleProductPopupDismiss(ProductInfoStore.current)
    }

    private fun loadPopupDurationPreferences() {
        val preferences = applicationContext.getSharedPreferences(
            POPUP_PREFS,
            Context.MODE_PRIVATE
        )

        isPopupDurationAuto = preferences.getBoolean(
            POPUP_MODE_AUTO_KEY,
            false
        )

        manualPopupDurationSeconds = preferences.getInt(
            POPUP_MANUAL_SECONDS_KEY,
            DEFAULT_POPUP_DURATION_SECONDS
        ).coerceIn(
            MIN_POPUP_DURATION_SECONDS,
            MAX_POPUP_DURATION_SECONDS
        )
    }

    private fun savePopupDurationPreferences() {
        applicationContext.getSharedPreferences(
            POPUP_PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putBoolean(POPUP_MODE_AUTO_KEY, isPopupDurationAuto)
            .putInt(POPUP_MANUAL_SECONDS_KEY, manualPopupDurationSeconds)
            .apply()
    }

    private fun configurePopupDurationControls() {
        val seekBar = popupDurationSeekBar ?: return
        val modeButton = popupDurationModeButton ?: return

        seekBar.max = MAX_POPUP_DURATION_SECONDS - MIN_POPUP_DURATION_SECONDS
        seekBar.progress = manualPopupDurationSeconds - MIN_POPUP_DURATION_SECONDS

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    manualPopupDurationSeconds = (
                            progress + MIN_POPUP_DURATION_SECONDS
                            ).coerceIn(
                            MIN_POPUP_DURATION_SECONDS,
                            MAX_POPUP_DURATION_SECONDS
                        )

                    updatePopupDurationControlState()

                    if (fromUser) {
                        savePopupDurationPreferences()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    popupTimerPausedByUser = true
                    popupHandler.removeCallbacks(dismissPopupRunnable)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    popupTimerPausedByUser = false
                    savePopupDurationPreferences()
                    scheduleProductPopupDismiss(ProductInfoStore.current)
                }
            }
        )

        modeButton.setOnClickListener {
            isPopupDurationAuto = !isPopupDurationAuto
            savePopupDurationPreferences()
            updatePopupDurationControlState()

            popupHandler.removeCallbacks(dismissPopupRunnable)
            scheduleProductPopupDismiss(ProductInfoStore.current)

            android.util.Log.d(
                "OverlayService",
                "MODALITA DURATA POPUP = ${if (isPopupDurationAuto) "AUTO" else "MANUALE"}"
            )
        }

        updatePopupDurationControlState()
    }

    private fun updatePopupDurationControlState() {
        val seekBar = popupDurationSeekBar
        val modeButton = popupDurationModeButton
        val valueText = popupDurationValueText

        seekBar?.isEnabled = !isPopupDurationAuto
        seekBar?.alpha = if (isPopupDurationAuto) 0.35f else 1.0f

        modeButton?.text = if (isPopupDurationAuto) {
            "AUTO"
        } else {
            "MANUALE"
        }

        valueText?.text = if (isPopupDurationAuto) {
            val seconds = resolveAutomaticPopupDurationMs(
                ProductInfoStore.current
            ) / 1000L
            "$seconds s"
        } else {
            "$manualPopupDurationSeconds s"
        }

        modeButton?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12 * resources.displayMetrics.density
            setColor(
                if (isPopupDurationAuto) {
                    Color.rgb(0, 105, 62)
                } else {
                    Color.rgb(230, 235, 232)
                }
            )
        }
        modeButton?.setTextColor(
            if (isPopupDurationAuto) Color.WHITE else Color.rgb(23, 32, 25)
        )
    }

    private fun scheduleProductPopupDismiss(product: ProductInfo?) {
        if (productInfoPopup == null || popupTimerPausedByUser) {
            return
        }

        popupHandler.removeCallbacks(dismissPopupRunnable)

        val durationMs = if (isPopupDurationAuto) {
            resolveAutomaticPopupDurationMs(product)
        } else {
            manualPopupDurationSeconds * 1000L
        }

        popupDurationValueText?.text = "${durationMs / 1000L} s"
        popupHandler.postDelayed(dismissPopupRunnable, durationMs)

        android.util.Log.d(
            "OverlayService",
            "DURATA POPUP mode=${if (isPopupDurationAuto) "AUTO" else "MANUALE"} ms=$durationMs"
        )
    }

    private fun resolveAutomaticPopupDurationMs(
        product: ProductInfo?
    ): Long {
        val stock = product?.stock?.toNumericValue()
        val minimumStock = product?.minimumStock?.toNumericValue()

        return when {
            stock == null || minimumStock == null ->
                manualPopupDurationSeconds * 1000L

            stock > minimumStock -> AUTO_REGULAR_DURATION_MS
            stock == minimumStock -> AUTO_WARNING_DURATION_MS
            else -> AUTO_REORDER_DURATION_MS
        }
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
        workflowCompleted: Boolean,
        playStockSound: Boolean
    ) {
        fun valueOrLoading(value: String): String =
            value.trim().takeIf { it.isNotEmpty() } ?: "lettura…"

        fun valueOrEmpty(value: String): String =
            value.trim().takeIf { it.isNotEmpty() && it != "-1" } ?: ""

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
            stockStatusContainer?.visibility = View.GONE
            stockStatusText?.text = ""
            reorderText?.text = ""
            availableStockValueText?.text = ""
            minimumStockValueText?.text = ""
            reorderLotValueText?.text = ""
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
        availableStockValueText?.text = valueOrEmpty(product.availableStock)
        minimumStockValueText?.text = valueOrEmpty(product.minimumStock)
        reorderLotValueText?.text = valueOrEmpty(product.reorderLot)

        val stockSoundStatus = updateStockStatus(product)

        if (playStockSound && stockSoundStatus != null) {
            playStockStatusSound(
                product = product,
                status = stockSoundStatus
            )
        }

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

    private fun showScanErrorPopup(message: String) {
        removeScanErrorPopup()

        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(150, 0, 0, 0))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                (28 * density).toInt(),
                (22 * density).toInt(),
                (28 * density).toInt(),
                (22 * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(198, 40, 40))
                cornerRadius = 20 * density
            }
            elevation = 16 * density
        }

        val title = TextView(this).apply {
            text = "LETTURA ERRATA"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val detail = TextView(this).apply {
            text = message
                .replace("Lettura errata", "", ignoreCase = true)
                .trim()
                .ifEmpty { "Riprovare" }
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, (8 * density).toInt(), 0, 0)
        }

        card.addView(title)
        card.addView(detail)

        val width = min(
            (360 * density).toInt(),
            screenWidth - (32 * density).toInt()
        )

        root.addView(
            card,
            FrameLayout.LayoutParams(
                width,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        scanErrorPopup = root
        windowManager.addView(root, params)

        popupHandler.removeCallbacks(dismissScanErrorRunnable)
        popupHandler.postDelayed(
            dismissScanErrorRunnable,
            SCAN_ERROR_DURATION_MS
        )

        android.util.Log.d(
            "OverlayService",
            "POPUP ERRORE LETTURA MOSTRATO: $message"
        )
    }

    private fun removeScanErrorPopup() {
        popupHandler.removeCallbacks(dismissScanErrorRunnable)

        val popup = scanErrorPopup ?: return

        try {
            windowManager.removeView(popup)
        } catch (_: Exception) {
        }

        scanErrorPopup = null
    }

    private fun removeProductInfoPopup() {
        stockEditPopup?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
            stockEditPopup = null
        }

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
        stockStatusContainer = null
        stockStatusText = null
        reorderText = null
        availableStockValueText = null
        minimumStockValueText = null
        reorderLotValueText = null
        popupDurationSeekBar = null
        popupDurationModeButton = null
        popupDurationValueText = null
        popupTimerPausedByUser = false
    }

    /**
     * Aggiorna la fascia della giacenza usando colori ad alto contrasto.
     *
     * Verde: giacenza sopra la scorta minima.
     * Giallo vivo: giacenza uguale alla scorta minima.
     * Rosso: giacenza sotto la scorta minima.
     */
    private fun updateStockStatus(
        product: ProductInfo
    ): StockSoundStatus? {
        val stock = product.stock.toNumericValue()
        val minimumStock = product.minimumStock.toNumericValue()
        val reorderLot = product.reorderLot.toNumericValue()

        val container = stockStatusContainer ?: return null
        val statusText = stockStatusText ?: return null
        val orderText = reorderText ?: return null

        if (stock == null || minimumStock == null) {
            container.visibility = View.GONE
            statusText.text = ""
            orderText.text = ""
            return null
        }

        container.visibility = View.VISIBLE

        val density = resources.displayMetrics.density
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16 * density
        }

        val soundStatus = when {
            stock > minimumStock -> {
                background.setColor(Color.rgb(0, 200, 83))
                statusText.text = "SCORTA REGOLARE"
                statusText.setTextColor(Color.BLACK)
                orderText.visibility = View.GONE
                orderText.text = ""
                StockSoundStatus.REGULAR
            }

            stock == minimumStock -> {
                background.setColor(Color.rgb(255, 214, 0))
                statusText.text = "ATTENZIONE - AL LIMITE"
                statusText.setTextColor(Color.BLACK)

                if (reorderLot != null && reorderLot > 0.0) {
                    orderText.visibility = View.VISIBLE
                    orderText.text =
                        "LOTTO RIORDINO: ${reorderLot.formatStockQuantity()} PEZZI"
                    orderText.setTextColor(Color.BLACK)
                } else {
                    orderText.visibility = View.GONE
                    orderText.text = ""
                }

                StockSoundStatus.WARNING
            }

            else -> {
                background.setColor(Color.rgb(213, 0, 0))
                statusText.text = "DA RIORDINARE"
                statusText.setTextColor(Color.WHITE)

                if (reorderLot != null && reorderLot > 0.0) {
                    orderText.visibility = View.VISIBLE
                    orderText.text =
                        "ORDINA ${reorderLot.formatStockQuantity()} PEZZI"
                    orderText.setTextColor(Color.WHITE)
                } else {
                    orderText.visibility = View.GONE
                    orderText.text = ""
                }

                StockSoundStatus.REORDER
            }
        }

        container.background = background
        return soundStatus
    }

    /**
     * Riproduce il feedback stock soltanto quando il workflow è completo.
     *
     * Verde  -> BLIP breve.
     * Giallo -> CRASH: articolo al limite.
     * Rosso  -> CRASH: articolo da riordinare.
     */
    private fun playStockStatusSound(
        product: ProductInfo,
        status: StockSoundStatus
    ) {
        when (status) {
            StockSoundStatus.REGULAR -> {
                ScanFeedbackManager.playSuccess(applicationContext)
            }

            StockSoundStatus.WARNING,
            StockSoundStatus.REORDER -> {
                ScanFeedbackManager.playWarning(applicationContext)
            }
        }

        android.util.Log.d(
            "OverlayService",
            "SUONO STOCK RIPRODOTTO status=$status EAN=${product.barcode}"
        )
    }

    private fun String.toNumericValue(): Double? =
        trim()
            .replace("€", "")
            .replace(" ", "")
            .replace(",", ".")
            .toDoubleOrNull()

    private fun Double.formatStockQuantity(): String =
        if (this == toInt().toDouble()) {
            toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", this)
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
        popupHandler.removeCallbacks(dismissScanErrorRunnable)
        removeProductInfoPopup()
        removeScanErrorPopup()
        removeHistoryPopup()
        ReorderStore.removeSizeListener(reorderStoreListener)

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