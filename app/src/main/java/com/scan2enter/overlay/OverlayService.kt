package com.scan2enter.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import coil3.load
import coil3.request.crossfade
import com.scan2enter.BuildFlags
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.ScannerActivity
import com.scan2enter.R
import com.scan2enter.feedback.ScanFeedbackManager
import com.scan2enter.favorites.FavoriteItem
import com.scan2enter.favorites.FavoriteRepository
import com.scan2enter.model.ProductInfo
import com.scan2enter.model.ProductInfoStore
import com.scan2enter.labels.a4.A4LabelsPopup
import com.scan2enter.labels.a4.A4LabelStore
import com.scan2enter.labels.a4.packaging.PackagingOptions
import com.scan2enter.labels.a4.packaging.PackagingSelectionStore
import com.scan2enter.labels.a4.packaging.PackagingType
import com.scan2enter.overlay.popup.LocationManagementPopup
import com.scan2enter.overlay.popup.LabelPrintPopup
import com.scan2enter.overlay.popup.ProductInfoPopup
import com.scan2enter.overlay.popup.StockSettingsPopup
import com.scan2enter.repository.ProductRepositoryProvider
import com.scan2enter.reorder.ReorderItem
import com.scan2enter.reorder.ReorderStore
import com.scan2enter.session.SessionStore
import com.scan2enter.session.SessionCustomerStore
import com.scan2enter.printing.ListPdfGenerator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW_PRODUCT_INFO =
            "com.scan2enter.action.SHOW_PRODUCT_INFO"

        const val ACTION_SHOW_REORDER_LIST =
            "com.scan2enter.action.SHOW_REORDER_LIST"

        const val ACTION_SHOW_FAVORITES_LIST =
            "com.scan2enter.action.SHOW_FAVORITES_LIST"

        const val ACTION_SHOW_GODEX_PRINT =
            "com.scan2enter.action.SHOW_GODEX_PRINT"

        const val ACTION_SHOW_GODEX_SETUP =
            "com.scan2enter.action.SHOW_GODEX_SETUP"

        const val ACTION_SHOW_A4_LABELS =
            "com.scan2enter.action.SHOW_A4_LABELS"

        const val ACTION_UPDATE_PRODUCT_INFO =
            "com.scan2enter.action.UPDATE_PRODUCT_INFO"

        const val ACTION_ENABLE_PRODUCT_INFO_TOUCH_THROUGH =
            "com.scan2enter.action.ENABLE_PRODUCT_INFO_TOUCH_THROUGH"

        const val ACTION_DISABLE_PRODUCT_INFO_TOUCH_THROUGH =
            "com.scan2enter.action.DISABLE_PRODUCT_INFO_TOUCH_THROUGH"

        const val ACTION_PREPARE_SCANNER =
            "com.scan2enter.action.PREPARE_SCANNER"

        const val ACTION_REQUEST_CURRENT_ARTICLE =
            "com.scan2enter.action.REQUEST_CURRENT_ARTICLE"

        const val ACTION_OPEN_CURRENT_ARTICLE =
            "com.scan2enter.action.OPEN_CURRENT_ARTICLE"

        const val ACTION_OPEN_SEARCH_ARTICLE =
            "com.scan2enter.action.OPEN_SEARCH_ARTICLE"

        const val ACTION_OPEN_SESSION_ARTICLE_DETAIL =
            "com.scan2enter.action.OPEN_SESSION_ARTICLE_DETAIL"

        const val ACTION_OPEN_SCANNER =
            "com.scan2enter.action.OPEN_SCANNER"

        const val ACTION_CLOSE_SCANNER =
            "com.scan2enter.action.CLOSE_SCANNER"

        const val ACTION_OPEN_RAPID_SCANNER =
            "com.scan2enter.action.OPEN_RAPID_SCANNER"

        const val ACTION_SHOW_SCAN_ERROR =
            "com.scan2enter.action.SHOW_SCAN_ERROR"

        const val EXTRA_SCAN_ERROR_MESSAGE =
            "com.scan2enter.extra.SCAN_ERROR_MESSAGE"

        const val EXTRA_CURRENT_ARTICLE_BARCODE =
            "com.scan2enter.extra.CURRENT_ARTICLE_BARCODE"

        const val EXTRA_SUPPRESS_PRODUCT_POPUP =
            "com.scan2enter.extra.SUPPRESS_PRODUCT_POPUP"

        const val EXTRA_CURRENT_ARTICLE_YEAR =
            "com.scan2enter.extra.CURRENT_ARTICLE_YEAR"

        const val EXTRA_CURRENT_ARTICLE_SEASON =
            "com.scan2enter.extra.CURRENT_ARTICLE_SEASON"

        const val EXTRA_CURRENT_ARTICLE_LOCATION =
            "com.scan2enter.extra.CURRENT_ARTICLE_LOCATION"

        const val EXTRA_WORKFLOW_COMPLETED =
            "com.scan2enter.extra.WORKFLOW_COMPLETED"

        private const val DOCK_DRAG_THRESHOLD_DP = 28f
        private const val LONG_PRESS_DURATION_MS = 800L
        private const val DEFAULT_POPUP_DURATION_SECONDS = 4
        private const val MIN_POPUP_DURATION_SECONDS = 1
        private const val MAX_POPUP_DURATION_SECONDS = 10
        private const val AUTO_REGULAR_DURATION_MS = 4000L
        private const val AUTO_WARNING_DURATION_MS = 4000L
        private const val AUTO_REORDER_DURATION_MS = 4000L
        private const val SCAN_ERROR_DURATION_MS = 800L

        private const val POPUP_PREFS = "product_popup_preferences"
        private const val POPUP_MODE_AUTO_KEY = "popup_mode_auto"
        private const val POPUP_MANUAL_SECONDS_KEY = "popup_manual_seconds"

        private const val WORKFLOW_PREFS = "scan_workflow"
        private const val WORKFLOW_MODE_KEY = "mode"
        private const val MODE_INFO = "INFO"
        private const val MODE_LABELS_GODEX = "ETICHETTE_GODEX"
        private const val MODE_LABELS_A4 = "ETICHETTE_A4"
        private const val MODE_LABELS_BLISTER = "ETICHETTE_BLISTER"
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

    private val gatewayApiClient by lazy {
        GatewayApiClient()
    }

    private val productInfoPopupController by lazy {
        ProductInfoPopup(
            context = this,
            windowManager = windowManager
        )
    }

    private val stockSettingsPopupController by lazy {
        StockSettingsPopup(
            context = this,
            windowManager = windowManager
        )
    }

    private val locationManagementPopupController by lazy {
        LocationManagementPopup(
            context = this,
            windowManager = windowManager
        )
    }

    private val labelPrintPopupController by lazy {
        LabelPrintPopup(
            context = this,
            windowManager = windowManager
        )
    }

    private val a4LabelsPopupController by lazy {
        A4LabelsPopup(
            context = this,
            windowManager = windowManager
        )
    }

    private var reorderListLoading = false

    private var startX = 0
    private var startY = 0

    private var touchStartX = 0f
    private var touchStartY = 0f

    private var isDragging = false
    private var scannerLongPressTriggered = false
    private var dockLockedByLongPress = false
    private var currentArticleLoading = false

    private val popupHandler = Handler(Looper.getMainLooper())

    private val scannerLongPressRunnable = Runnable {
        if (!isDragging && ::scannerArea.isInitialized) {
            scannerLongPressTriggered = true
            dockLockedByLongPress = true

            sendBroadcast(
                Intent(ACTION_REQUEST_CURRENT_ARTICLE).apply {
                    setPackage(packageName)
                }
            )

            Toast.makeText(
                this,
                "Lettura articolo aperto…",
                Toast.LENGTH_SHORT
            ).show()

            android.util.Log.d(
                "OverlayService",
                "PRESSIONE LUNGA - RICHIESTA ARTICOLO APERTO"
            )
        }
    }

    private val reorderStoreListener: (Int) -> Unit = { count ->
        popupHandler.post {
            updateReorderBadge(count)
            refreshReorderListPopup()
        }
    }

    private val favoriteStoreListener: (Int) -> Unit = {
        popupHandler.post {
            if (reorderPopupMode == ReorderPopupMode.FAVORITES) {
                refreshReorderListPopup()
            }

            /*
             * Se il popup articolo è visibile, aggiorna anche la stellina
             * indicativa dopo aggiunta/rimozione da Modifica scorte.
             */
            ProductInfoStore.current?.let { current ->
                updateProductInfoPopup(
                    product = current,
                    workflowCompleted = true,
                    playStockSound = false
                )
            }
        }
    }

    private var productInfoPopup: View? = null
    private var productInfoPopupParams: WindowManager.LayoutParams? = null

    private var priceValueText: TextView? = null
    private var articleCodeValueText: TextView? = null
    private var barcodeValueText: TextView? = null
    private var barcodeImageView: ImageView? = null
    private var productImageView: ImageView? = null
    private var descriptionValueText: TextView? = null
    private var yearValueText: TextView? = null
    private var seasonValueText: TextView? = null
    private var locationValueText: TextView? = null
    private var taxablePriceValueText: TextView? = null
    private var vatRateValueText: TextView? = null
    private var stockValueText: TextView? = null
    private var stockStatusContainer: LinearLayout? = null
    private var stockStatusText: TextView? = null
    private var reorderText: TextView? = null
    private var minimumStockValueText: TextView? = null
    private var reorderLotValueText: TextView? = null
    private var popupDurationSeekBar: SeekBar? = null
    private var popupDurationModeButton: TextView? = null
    private var popupDurationValueText: TextView? = null

    private var isPopupDurationAuto = false
    private var manualPopupDurationSeconds = DEFAULT_POPUP_DURATION_SECONDS
    private var popupTimerPausedByUser = false

    private var historyPopup: View? = null
    private var reorderListPopup: View? = null
    private var reorderConfirmationPopup: View? = null
    private var scanErrorPopup: View? = null

    private enum class ReorderPopupMode {
        REORDER,
        HISTORY,
        FAVORITES
    }

    private enum class FavoriteSortMode {
        INSERTION,
        PRICE_ASCENDING,
        PRICE_DESCENDING
    }

    private var reorderPopupMode = ReorderPopupMode.REORDER
    private var favoriteSortMode = FavoriteSortMode.PRICE_ASCENDING
    private var reorderSupplierFilterKey: String? = null

    /*
     * Posizione della lista da ripristinare dopo l'apertura di un articolo
     * o dopo un aggiornamento dell'adapter.
     */
    private var savedListMode = ReorderPopupMode.REORDER
    private var savedListFirstVisiblePosition = 0
    private var savedListTopOffset = 0
    private var urgentStockTone: ToneGenerator? = null

    private val dismissScanErrorRunnable = Runnable {
        removeScanErrorPopup()

        if (loadCurrentScanMode() == MODE_INFO) {
            android.util.Log.d(
                "OverlayService",
                "ERRORE LETTURA - RIAPRO AUTOMATICAMENTE LO SCANNER"
            )

            openRapidScanner()
        }
    }

    private var reopenScannerAfterPopup = false
    private var sessionRecordedForCurrentScan = false

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
            sessionRecordedForCurrentScan = false
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
            ACTION_SHOW_A4_LABELS -> {
                android.util.Log.d(
                    "OverlayService",
                    "RICHIESTA APERTURA ETICHETTE A4"
                )

                applicationContext
                    .getSharedPreferences(
                        WORKFLOW_PREFS,
                        Context.MODE_PRIVATE
                    )
                    .edit()
                    .putString(
                        WORKFLOW_MODE_KEY,
                        MODE_LABELS_A4
                    )
                    .apply()

                scanOverlay.hide()

                popupHandler.postDelayed(
                    {
                        scanOverlay.show(
                            rapidRescan = false
                        )
                    },
                    250L
                )

                a4LabelsPopupController.show(
                    onScanRequested = {
                        applicationContext
                            .getSharedPreferences(
                                WORKFLOW_PREFS,
                                Context.MODE_PRIVATE
                            )
                            .edit()
                            .putString(
                                WORKFLOW_MODE_KEY,
                                MODE_LABELS_A4
                            )
                            .apply()

                        popupHandler.postDelayed(
                            {
                                startService(
                                    Intent(
                                        this,
                                        OverlayService::class.java
                                    ).apply {
                                        action = ACTION_OPEN_SCANNER
                                    }
                                )
                            },
                            180L
                        )
                    },
                    onBlisterScanRequested = { type, includeHook, showPrice ->
                        val packagingType = when (type) {
                            "BLISTER_LONG" -> PackagingType.BLISTER_LONG
                            "BLISTER_BIG" -> PackagingType.BLISTER_BIG
                            else -> PackagingType.BLISTER_LARGE
                        }

                        PackagingSelectionStore.save(
                            applicationContext,
                            PackagingOptions(
                                type = packagingType,
                                includeHook = includeHook,
                                showPrice = showPrice
                            )
                        )

                        applicationContext
                            .getSharedPreferences(
                                WORKFLOW_PREFS,
                                Context.MODE_PRIVATE
                            )
                            .edit()
                            .putString(
                                WORKFLOW_MODE_KEY,
                                MODE_LABELS_BLISTER
                            )
                            .apply()

                        popupHandler.postDelayed(
                            {
                                startService(
                                    Intent(
                                        this,
                                        OverlayService::class.java
                                    ).apply {
                                        action = ACTION_OPEN_SCANNER
                                    }
                                )
                            },
                            180L
                        )
                    },
                    onClosed = {
                        scanOverlay.hide()

                        applicationContext
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
                    }
                )
            }

            ACTION_SHOW_GODEX_SETUP -> {
                android.util.Log.d(
                    "OverlayService",
                    "RICHIESTA CONFIGURAZIONE GODEX DALLA HOME"
                )

                applicationContext
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

                popupHandler.removeCallbacks(dismissPopupRunnable)

                applicationContext
                    .getSharedPreferences(
                        WORKFLOW_PREFS,
                        Context.MODE_PRIVATE
                    )
                    .edit()
                    .putString(
                        WORKFLOW_MODE_KEY,
                        MODE_LABELS_GODEX
                    )
                    .apply()

                scanOverlay.hide()

                popupHandler.postDelayed(
                    {
                        scanOverlay.show(
                            rapidRescan = false
                        )
                    },
                    250L
                )

                labelPrintPopupController.show(
                    product = null,
                    onScanRequested = {
                        applicationContext
                            .getSharedPreferences(
                                WORKFLOW_PREFS,
                                Context.MODE_PRIVATE
                            )
                            .edit()
                            .putString(
                                WORKFLOW_MODE_KEY,
                                MODE_LABELS_GODEX
                            )
                            .apply()

                        popupHandler.postDelayed(
                            {
                                startService(
                                    Intent(
                                        this,
                                        OverlayService::class.java
                                    ).apply {
                                        action = ACTION_OPEN_SCANNER
                                    }
                                )
                            },
                            180L
                        )
                    },
                    onClosed = {
                        scanOverlay.hide()

                        applicationContext
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
                    }
                )
            }

            ACTION_SHOW_GODEX_PRINT -> {
                android.util.Log.d(
                    "OverlayService",
                    "RICHIESTA APERTURA FINESTRA GODEX"
                )

                val product = ProductInfoStore.current

                if (product == null || product.barcode.isBlank()) {
                    Toast.makeText(
                        this,
                        "Articolo non disponibile",
                        Toast.LENGTH_SHORT
                    ).show()

                    reopenGodexScannerIfNeeded()
                } else {
                    popupHandler.removeCallbacks(dismissPopupRunnable)

                    labelPrintPopupController.show(
                        product = product,
                        onScanRequested = {
                            reopenGodexScannerIfNeeded()
                        },
                        onClosed = {
                            reopenGodexScannerIfNeeded()
                        }
                    )
                }
            }

            ACTION_SHOW_FAVORITES_LIST -> {
                android.util.Log.d(
                    "OverlayService",
                    "RICHIESTA APERTURA PREFERITI DALLA HOME"
                )

                showReorderListPopup()
                reorderPopupMode = ReorderPopupMode.FAVORITES
                favoriteSortMode = FavoriteSortMode.PRICE_ASCENDING
                refreshReorderListPopup()
            }

            ACTION_SHOW_REORDER_LIST -> {
                android.util.Log.d(
                    "OverlayService",
                    "RICHIESTA APERTURA LISTA RIORDINO DALLA HOME"
                )

                showReorderListPopup()
                synchronizeReorderListFromGateway()
            }

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
                sessionRecordedForCurrentScan = false

                android.util.Log.d(
                    "OverlayService",
                    "SUNMI - APRO SCANNER HARDWARE"
                )

                scanOverlay.show(
                    rapidRescan = false
                )
            }

            ACTION_CLOSE_SCANNER -> {
                android.util.Log.d(
                    "OverlayService",
                    "CHIUDO SCANNER HARDWARE"
                )

                scanOverlay.hide()
            }

            ACTION_OPEN_RAPID_SCANNER -> {
                sessionRecordedForCurrentScan = false
                openRapidScanner()
            }

            ACTION_OPEN_SEARCH_ARTICLE -> {
                val barcode = intent.getStringExtra(
                    EXTRA_CURRENT_ARTICLE_BARCODE
                ).orEmpty()

                val suppressPopup =
                    intent.getBooleanExtra(
                        EXTRA_SUPPRESS_PRODUCT_POPUP,
                        false
                    )

                openCurrentArticleFromApi(
                    rawBarcode = barcode,
                    uiYear = "",
                    uiSeason = "",
                    uiLocation = "",
                    addToHistory = false,
                    addToReorder = false,
                    addToSession = true,
                    showPopup = !suppressPopup
                )
            }

            ACTION_OPEN_SESSION_ARTICLE_DETAIL -> {
                val barcode = intent.getStringExtra(
                    EXTRA_CURRENT_ARTICLE_BARCODE
                ).orEmpty()

                openCurrentArticleFromApi(
                    rawBarcode = barcode,
                    uiYear = "",
                    uiSeason = "",
                    uiLocation = "",
                    addToHistory = false,
                    addToReorder = false,
                    addToSession = false,
                    showPopup = true
                )
            }

            ACTION_OPEN_CURRENT_ARTICLE -> {
                val barcode = intent.getStringExtra(
                    EXTRA_CURRENT_ARTICLE_BARCODE
                ).orEmpty()

                openCurrentArticleFromApi(
                    rawBarcode = barcode,
                    uiYear = intent.getStringExtra(EXTRA_CURRENT_ARTICLE_YEAR).orEmpty(),
                    uiSeason = intent.getStringExtra(EXTRA_CURRENT_ARTICLE_SEASON).orEmpty(),
                    uiLocation = intent.getStringExtra(EXTRA_CURRENT_ARTICLE_LOCATION).orEmpty()
                )
            }
        }

        return START_STICKY
    }

    private fun addProductToSessionWithCustomerPrice(
        product: ProductInfo
    ) {
        val customer = SessionCustomerStore.current.value
        val barcode = product.barcode.trim()

        if (barcode.isBlank()) {
            SessionStore.addOrIncrement(product)
            return
        }

        Thread {
            val result = gatewayApiClient.getClientPrice(
                clientId = customer.id,
                barcode = barcode
            )

            result
                .onSuccess { clientPrice ->
                    val finalPriceValue =
                        clientPrice.finalPrice
                            ?: product.publicPrice
                                .replace(",", ".")
                                .toDoubleOrNull()
                            ?: 0.0

                    val listPriceValue =
                        clientPrice.listPrice
                            ?: finalPriceValue

                    val finalPriceText =
                        String.format(
                            Locale.ITALY,
                            "%.2f",
                            finalPriceValue
                        )

                    val listPriceText =
                        String.format(
                            Locale.ITALY,
                            "%.2f",
                            listPriceValue
                        )

                    val productForSession =
                        product.copy(
                            publicPrice = finalPriceText
                        )

                    popupHandler.post {
                        SessionStore.addOrIncrement(
                            product = productForSession,
                            priceListName =
                                clientPrice.priceListName,
                            listPrice = listPriceText,
                            discount1 =
                                clientPrice.discount1,
                            finalPrice = finalPriceText
                        )

                        android.util.Log.d(
                            "OverlayService",
                            "SESSIONE +1 cliente=${customer.id} " +
                                    "nome=${customer.name} " +
                                    "articleId=${product.articleId} " +
                                    "ean=$barcode " +
                                    "listino=${clientPrice.priceListName} " +
                                    "prezzoListino=$listPriceText " +
                                    "sconto=${clientPrice.discount1} " +
                                    "prezzoFinale=$finalPriceText " +
                                    "qta=${SessionStore.quantityFor(product.articleId)}"
                        )
                    }
                }
                .onFailure { error ->
                    android.util.Log.e(
                        "OverlayService",
                        "PREZZO CLIENTE NON DISPONIBILE " +
                                "cliente=${customer.id} ean=$barcode - " +
                                "uso prezzo pubblico",
                        error
                    )

                    popupHandler.post {
                        SessionStore.addOrIncrement(product)
                    }
                }
        }.start()
    }

    private fun openCurrentArticleFromApi(
        rawBarcode: String,
        uiYear: String,
        uiSeason: String,
        uiLocation: String,
        addToHistory: Boolean = true,
        addToReorder: Boolean = true,
        addToSession: Boolean = false,
        showPopup: Boolean = true
    ) {
        val barcode = rawBarcode
            .trim()
            .filter(Char::isDigit)

        if (barcode.length !in 8..14) {
            showScanErrorPopup(
                "Barcode articolo non valido"
            )
            return
        }

        if (currentArticleLoading) {
            android.util.Log.d(
                "OverlayService",
                "ARTICOLO APERTO - RICHIESTA GIÀ IN CORSO"
            )
            return
        }

        currentArticleLoading = true

        Thread {
            val result = productRepository.getProduct(barcode)

            popupHandler.post {
                currentArticleLoading = false

                result.onSuccess { product ->
                    val enrichedProduct = product.copy(
                        year = uiYear.ifBlank { product.year },
                        season = uiSeason.ifBlank { product.season },
                        location = uiLocation.ifBlank { product.location }
                    )

                    ProductInfoStore.current = enrichedProduct

                    if (addToHistory) {
                        ProductInfoStore.addToHistory(enrichedProduct)
                    }

                    if (addToReorder) {
                        ReorderStore.add(enrichedProduct)
                    }

                    if (addToSession) {
                        addProductToSessionWithCustomerPrice(
                            enrichedProduct
                        )
                    }

                    if (showPopup) {
                        showOrUpdateProductInfoPopup(
                            workflowCompleted = true,
                            manualOpen = true
                        )
                    }

                    android.util.Log.d(
                        "OverlayService",
                        "ARTICOLO APERTO CARICATO VIA API EAN=$barcode"
                    )
                }.onFailure { error ->
                    showScanErrorPopup(
                        "Impossibile caricare l'articolo"
                    )

                    android.util.Log.e(
                        "OverlayService",
                        "ERRORE ARTICOLO APERTO EAN=$barcode",
                        error
                    )
                }
            }
        }.start()
    }

    private fun reopenGodexScannerIfNeeded() {
        if (loadCurrentScanMode() != MODE_LABELS_GODEX) {
            return
        }

        popupHandler.postDelayed(
            {
                scanOverlay.show(rapidRescan = false)
            },
            250L
        )
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
        android.util.Log.d(
            "OverlayService",
            "SUNMI - RIAPRO SCANNER HARDWARE"
        )

        scanOverlay.show(
            rapidRescan = true
        )
    }

    override fun onCreate() {
        super.onCreate()

        ProductInfoStore.initialize(applicationContext)
        SessionStore.initialize(applicationContext)
        ReorderStore.initialize(applicationContext)
        A4LabelStore.initialize(applicationContext)
        FavoriteRepository.initialize(applicationContext)
        loadPopupDurationPreferences()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        scanOverlay = ScanOverlay(this)

        urgentStockTone = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (error: Exception) {
            android.util.Log.e(
                "OverlayService",
                "IMPOSSIBILE INIZIALIZZARE IL SUONO RIORDINO",
                error
            )
            null
        }

        ScanFeedbackManager.initialize(applicationContext)

        dockView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_button, null)

        infoArea = dockView.findViewById(R.id.infoArea)
        scannerArea = dockView.findViewById(R.id.scannerArea)
        reorderBadgeText = dockView.findViewById(R.id.reorderBadgeText)

        ReorderStore.addSizeListener(reorderStoreListener)
        FavoriteRepository.addListener(favoriteStoreListener)
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

        // Dock storica disabilitata: la nuova Sessione sostituisce
        // i vecchi comandi flottanti. Manteniamo per ora le strutture
        // interne per non alterare scanner e popup.
        android.util.Log.d(
            "OverlayService",
            "DOCK DISABILITATA - NON AGGIUNTA AL WINDOW MANAGER"
        )

        infoArea.setOnClickListener {
            if (isDragging) return@setOnClickListener

            showReorderListPopup()
            synchronizeReorderListFromGateway()
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
                    scannerLongPressTriggered = false
                    dockLockedByLongPress = false

                    popupHandler.removeCallbacks(scannerLongPressRunnable)

                    if (touchedView === scannerArea) {
                        popupHandler.postDelayed(
                            scannerLongPressRunnable,
                            LONG_PRESS_DURATION_MS
                        )
                    }

                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (dockLockedByLongPress || scannerLongPressTriggered) {
                        return@OnTouchListener true
                    }

                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    val dragThresholdPx =
                        DOCK_DRAG_THRESHOLD_DP * resources.displayMetrics.density

                    if (!isDragging &&
                        (abs(dx.toFloat()) > dragThresholdPx ||
                                abs(dy.toFloat()) > dragThresholdPx)
                    ) {
                        isDragging = true
                        popupHandler.removeCallbacks(scannerLongPressRunnable)
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
                    popupHandler.removeCallbacks(scannerLongPressRunnable)

                    if (isDragging) {
                        snapToEdge()
                    } else if (!scannerLongPressTriggered && !dockLockedByLongPress) {
                        touchedView.performClick()
                    }

                    isDragging = false
                    scannerLongPressTriggered = false
                    dockLockedByLongPress = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    popupHandler.removeCallbacks(scannerLongPressRunnable)
                    isDragging = false
                    scannerLongPressTriggered = false
                    dockLockedByLongPress = false
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
     * Aggiorna la cache locale con la lista completa restituita dal Gateway.
     *
     * Il popup viene aperto subito usando l'ultima cache disponibile; quando
     * la richiesta termina, listener e badge vengono aggiornati automaticamente.
     * Se il Gateway non è raggiungibile, la lista salvata resta intatta.
     */
    private fun synchronizeReorderListFromGateway() {
        if (reorderListLoading) {
            android.util.Log.d(
                "OverlayService",
                "SINCRONIZZAZIONE RIORDINO GIÀ IN CORSO"
            )
            return
        }

        reorderListLoading = true

        Toast.makeText(
            this,
            "Aggiornamento lista di riordino…",
            Toast.LENGTH_SHORT
        ).show()

        Thread {
            val result = gatewayApiClient.getReorderList()

            popupHandler.post {
                reorderListLoading = false

                result.onSuccess { serverItems ->
                    ReorderStore.replaceAll(serverItems)
                    refreshReorderListPopup()

                    Toast.makeText(
                        this,
                        "Lista aggiornata: ${serverItems.size} articoli",
                        Toast.LENGTH_SHORT
                    ).show()

                    android.util.Log.d(
                        "OverlayService",
                        "LISTA RIORDINO SINCRONIZZATA elementi=${serverItems.size}"
                    )
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        "Gateway non raggiungibile: uso l'ultima lista salvata",
                        Toast.LENGTH_LONG
                    ).show()

                    android.util.Log.e(
                        "OverlayService",
                        "ERRORE SINCRONIZZAZIONE LISTA RIORDINO",
                        error
                    )
                }
            }
        }.start()
    }

    /**
     * Apre la lista di riordino raggruppata per fornitore.
     */
    private fun showReorderListPopup() {
        if (reorderListPopup != null) return

        removeProductInfoPopup()
        removeHistoryPopup()
        reorderPopupMode = ReorderPopupMode.REORDER

        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        val overlayRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (18 * density).toInt(),
                (16 * density).toInt(),
                (18 * density).toInt(),
                (16 * density).toInt()
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
            text = "Lista di riordino"
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
            setOnClickListener { removeReorderListPopup() }
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        header.addView(closeButton)

        card.addView(header)

        val subtitle = TextView(this).apply {
            id = View.generateViewId()
            tag = "reorderSubtitle"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(
                0,
                (4 * density).toInt(),
                0,
                (10 * density).toInt()
            )
        }
        card.addView(subtitle)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val reorderTab = Button(this).apply {
            tag = "reorderTabButton"
            text = "RIORDINO"
            textSize = 14f
            setOnClickListener {
                reorderPopupMode = ReorderPopupMode.REORDER
                refreshReorderListPopup()
            }
        }

        val historyTab = Button(this).apply {
            tag = "historyTabButton"
            text = "CRONOLOGIA"
            textSize = 13f
            setOnClickListener {
                reorderPopupMode = ReorderPopupMode.HISTORY
                refreshReorderListPopup()
            }
        }

        val favoritesTab = Button(this).apply {
            tag = "favoritesTabButton"
            text = "PREFERITI"
            textSize = 13f
            setOnClickListener {
                reorderPopupMode = ReorderPopupMode.FAVORITES
                refreshReorderListPopup()
            }
        }

        tabs.addView(
            reorderTab,
            LinearLayout.LayoutParams(
                0,
                (44 * density).toInt(),
                1f
            )
        )
        tabs.addView(
            historyTab,
            LinearLayout.LayoutParams(
                0,
                (44 * density).toInt(),
                1f
            ).apply {
                marginStart = (8 * density).toInt()
            }
        )

        tabs.addView(
            favoritesTab,
            LinearLayout.LayoutParams(
                0,
                (44 * density).toInt(),
                1f
            ).apply {
                marginStart = (8 * density).toInt()
            }
        )

        card.addView(
            tabs,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
        )

        val favoriteSortButton = Button(this).apply {
            tag = "favoriteSortButton"
            text = "ORDINA: INSERIMENTO  ▼"
            textSize = 14f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setTextColor(Color.rgb(121, 85, 0))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(255, 248, 225))
                setStroke((1 * density).toInt(), Color.rgb(255, 213, 79))
                cornerRadius = 12 * density
            }
            setOnClickListener { showFavoriteSortMenu(this) }
        }

        card.addView(
            favoriteSortButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (44 * density).toInt()
            ).apply {
                bottomMargin = (10 * density).toInt()
            }
        )

        val supplierFilterButton = Button(this).apply {
            tag = "reorderSupplierFilterButton"
            text = "TUTTI I FORNITORI  ▼"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(27, 94, 32))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(232, 245, 233))
                setStroke((1 * density).toInt(), Color.rgb(165, 214, 167))
                cornerRadius = 12 * density
            }
            setOnClickListener { showSupplierFilterMenu(this) }
        }

        card.addView(
            supplierFilterButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (44 * density).toInt()
            ).apply {
                bottomMargin = (10 * density).toInt()
            }
        )

        val printListButton = Button(this).apply {
            tag = "printListButton"
            text = "🖨  STAMPA PDF"
            textSize = 15f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(55, 71, 79))
                cornerRadius = 12 * density
            }
            setOnClickListener {
                printCurrentVisibleList()
            }
        }

        card.addView(
            printListButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            ).apply {
                bottomMargin = (10 * density).toInt()
            }
        )

        /*
         * Lista virtualizzata: ListView crea soltanto le righe visibili.
         * La precedente combinazione ScrollView + LinearLayout costruiva
         * contemporaneamente tutte le schede, causando blocchi con liste lunghe.
         */
        val listView = ListView(this).apply {
            tag = "reorderListView"
            divider = null
            dividerHeight = 0
            clipToPadding = false
            setPadding(0, 0, 0, (4 * density).toInt())
        }

        card.addView(
            listView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val horizontalMargin = (16 * density).toInt()
        val verticalMargin = (28 * density).toInt()

        val cardWidth = min(
            (440 * density).toInt(),
            screenWidth - horizontalMargin * 2
        )

        val cardHeight = min(
            (760 * density).toInt(),
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

        reorderListPopup = overlayRoot
        windowManager.addView(overlayRoot, popupParams)
        refreshReorderListPopup()

        android.util.Log.d(
            "OverlayService",
            "LISTA RIORDINO APERTA elementi=${ReorderStore.size()}"
        )
    }

    private fun rememberReorderListPosition() {
        val popup = reorderListPopup ?: return
        val listView = findViewByTag<ListView>(
            popup,
            "reorderListView"
        ) ?: return

        savedListMode = reorderPopupMode
        savedListFirstVisiblePosition = listView.firstVisiblePosition
        savedListTopOffset = listView.getChildAt(0)?.top ?: 0

        android.util.Log.d(
            "OverlayService",
            "POSIZIONE LISTA SALVATA mode=$savedListMode " +
                    "position=$savedListFirstVisiblePosition " +
                    "offset=$savedListTopOffset"
        )
    }

    private fun applyListAdapter(
        listView: ListView,
        rows: List<() -> View>
    ) {
        listView.adapter = createVirtualizedListAdapter(rows)

        if (savedListMode != reorderPopupMode) return

        listView.post {
            val maxPosition =
                (listView.adapter?.count ?: 1) - 1

            val safePosition =
                savedListFirstVisiblePosition.coerceIn(
                    0,
                    maxPosition.coerceAtLeast(0)
                )

            listView.setSelectionFromTop(
                safePosition,
                savedListTopOffset
            )

            android.util.Log.d(
                "OverlayService",
                "POSIZIONE LISTA RIPRISTINATA mode=$reorderPopupMode " +
                        "position=$safePosition offset=$savedListTopOffset"
            )
        }
    }

    /**
     * Aggiorna il contenuto della lista usando una ListView virtualizzata.
     * Vengono materializzate soltanto le righe visibili sullo schermo.
     */
    private fun refreshReorderListPopup() {
        val popup = reorderListPopup ?: return

        val subtitle = findViewByTag<TextView>(popup, "reorderSubtitle")
        val listView = findViewByTag<ListView>(popup, "reorderListView")
            ?: return
        val reorderTab = findViewByTag<Button>(popup, "reorderTabButton")
        val historyTab = findViewByTag<Button>(popup, "historyTabButton")
        val favoritesTab = findViewByTag<Button>(popup, "favoritesTabButton")
        val favoriteSortButton =
            findViewByTag<Button>(popup, "favoriteSortButton")
        val supplierFilterButton =
            findViewByTag<Button>(popup, "reorderSupplierFilterButton")
        val printListButton =
            findViewByTag<Button>(popup, "printListButton")

        styleReorderTab(
            button = reorderTab,
            selected = reorderPopupMode == ReorderPopupMode.REORDER
        )
        styleReorderTab(
            button = historyTab,
            selected = reorderPopupMode == ReorderPopupMode.HISTORY
        )
        styleReorderTab(
            button = favoritesTab,
            selected = reorderPopupMode == ReorderPopupMode.FAVORITES
        )

        if (reorderPopupMode == ReorderPopupMode.HISTORY) {
            val history = ProductInfoStore.getHistory().take(20)

            subtitle?.text = when (history.size) {
                0 -> "Nessun articolo letto"
                1 -> "Ultimo articolo letto"
                else -> "Ultimi ${history.size} articoli letti"
            }

            supplierFilterButton?.visibility = View.GONE
            favoriteSortButton?.visibility = View.GONE
            printListButton?.visibility = View.GONE

            val rows: List<() -> View> = if (history.isEmpty()) {
                listOf {
                    createVirtualizedEmptyMessage(
                        "Gli ultimi articoli letti compariranno qui."
                    )
                }
            } else {
                history.mapIndexed { index, product ->
                    { createReorderHistoryItemView(product, index + 1) }
                }
            }

            applyListAdapter(listView, rows)
            return
        }

        if (reorderPopupMode == ReorderPopupMode.FAVORITES) {
            supplierFilterButton?.visibility = View.GONE
            favoriteSortButton?.visibility = View.VISIBLE
            favoriteSortButton?.text = favoriteSortLabel()
            printListButton?.visibility = View.VISIBLE

            val favorites = sortFavoriteItems(
                FavoriteRepository.getAll()
            )

            subtitle?.text = when (favorites.size) {
                0 -> "Nessun articolo preferito"
                1 -> "1 articolo preferito"
                else -> "${favorites.size} articoli preferiti"
            }

            val rows: List<() -> View> = if (favorites.isEmpty()) {
                listOf {
                    createVirtualizedEmptyMessage(
                        "Aggiungi un articolo ai preferiti dalla finestra Modifica scorte."
                    )
                }
            } else {
                favorites.map { item ->
                    { createFavoriteItemView(item) }
                }
            }

            applyListAdapter(listView, rows)
            return
        }

        favoriteSortButton?.visibility = View.GONE
        printListButton?.visibility = View.VISIBLE

        val allItems = ReorderStore.getAll()
            .filter(::shouldShowReorderItem)

        val availableSupplierKeys = allItems
            .map(::supplierFilterKey)
            .toSet()

        if (reorderSupplierFilterKey !in availableSupplierKeys) {
            reorderSupplierFilterKey = null
        }

        val items = reorderSupplierFilterKey?.let { selectedKey ->
            allItems.filter { supplierFilterKey(it) == selectedKey }
        } ?: allItems

        val groupedItems = items.groupBy(::supplierFilterKey)
        val supplierCount = allItems
            .map(::supplierFilterKey)
            .distinct()
            .size

        supplierFilterButton?.visibility = View.VISIBLE
        supplierFilterButton?.text = selectedSupplierFilterLabel(allItems)

        subtitle?.text = when {
            allItems.isEmpty() -> "Nessun articolo presente"
            reorderSupplierFilterKey != null ->
                "${items.size} articoli visualizzati su ${allItems.size}"
            supplierCount == 1 -> "${allItems.size} articoli · 1 fornitore"
            else -> "${allItems.size} articoli · $supplierCount fornitori"
        }

        val rows = mutableListOf<() -> View>()

        if (items.isEmpty()) {
            rows += {
                createVirtualizedEmptyMessage(
                    "Gli articoli aggiunti durante le scansioni compariranno qui."
                )
            }
        } else {
            groupedItems.forEach { (_, supplierItems) ->
                val supplierName = supplierItems
                    .firstOrNull()
                    ?.supplierName
                    ?.trim()
                    .orEmpty()
                    .ifEmpty { "Fornitore non indicato" }

                rows += {
                    createSupplierHeaderView(
                        supplierName = supplierName,
                        itemCount = supplierItems.size
                    )
                }

                supplierItems.forEach { item ->
                    rows += { createReorderItemView(item) }
                }
            }
        }

        applyListAdapter(listView, rows)
    }

    private fun printCurrentVisibleList() {
        when (reorderPopupMode) {
            ReorderPopupMode.FAVORITES -> {
                val favorites = sortFavoriteItems(
                    FavoriteRepository.getAll()
                )

                if (favorites.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Nessun preferito da stampare",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                val sortDescription = when (favoriteSortMode) {
                    FavoriteSortMode.INSERTION ->
                        "Ordine di inserimento"
                    FavoriteSortMode.PRICE_ASCENDING ->
                        "Prezzo crescente"
                    FavoriteSortMode.PRICE_DESCENDING ->
                        "Prezzo decrescente"
                }

                val result = ListPdfGenerator.generateFavoritesAndOpen(
                    context = applicationContext,
                    items = favorites,
                    sortDescription = sortDescription
                )

                result.onFailure { error ->
                    Toast.makeText(
                        this,
                        "Errore PDF preferiti: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            ReorderPopupMode.REORDER -> {
                val allItems = ReorderStore.getAll()
                    .filter(::shouldShowReorderItem)

                val visibleItems =
                    reorderSupplierFilterKey?.let { selectedKey ->
                        allItems.filter {
                            supplierFilterKey(it) == selectedKey
                        }
                    } ?: allItems

                if (visibleItems.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Nessun articolo da stampare",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                val filterDescription =
                    if (reorderSupplierFilterKey == null) {
                        "Tutti i fornitori"
                    } else {
                        visibleItems.firstOrNull()
                            ?.supplierName
                            ?.trim()
                            .orEmpty()
                            .ifEmpty { "Fornitore non indicato" }
                    }

                val result = ListPdfGenerator.generateReorderAndOpen(
                    context = applicationContext,
                    items = visibleItems,
                    filterDescription = filterDescription
                )

                result.onFailure { error ->
                    Toast.makeText(
                        this,
                        "Errore PDF riordino: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            else -> Unit
        }
    }

    private fun favoriteSortLabel(): String =
        when (favoriteSortMode) {
            FavoriteSortMode.INSERTION -> "ORDINA: INSERIMENTO  ▼"
            FavoriteSortMode.PRICE_ASCENDING -> "PREZZO: CRESCENTE  ▼"
            FavoriteSortMode.PRICE_DESCENDING -> "PREZZO: DECRESCENTE  ▼"
        }

    private fun showFavoriteSortMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)

        menu.menu.add(0, 0, 0, "Ordine di inserimento")
        menu.menu.add(0, 1, 1, "Prezzo crescente")
        menu.menu.add(0, 2, 2, "Prezzo decrescente")

        menu.setOnMenuItemClickListener { item ->
            favoriteSortMode = when (item.itemId) {
                1 -> FavoriteSortMode.PRICE_ASCENDING
                2 -> FavoriteSortMode.PRICE_DESCENDING
                else -> FavoriteSortMode.INSERTION
            }

            refreshReorderListPopup()
            true
        }

        menu.show()
    }

    private fun sortFavoriteItems(
        items: List<FavoriteItem>
    ): List<FavoriteItem> =
        when (favoriteSortMode) {
            FavoriteSortMode.INSERTION -> items
            FavoriteSortMode.PRICE_ASCENDING ->
                items.sortedBy { it.publicPrice.toPriceValue() }
            FavoriteSortMode.PRICE_DESCENDING ->
                items.sortedByDescending { it.publicPrice.toPriceValue() }
        }

    private fun String.toPriceValue(): Double {
        val cleaned = trim()
            .replace("€", "")
            .replace(" ", "")

        val normalized = when {
            cleaned.contains(',') && cleaned.contains('.') ->
                cleaned.replace(".", "").replace(',', '.')
            else -> cleaned.replace(',', '.')
        }

        return normalized.toDoubleOrNull() ?: Double.MAX_VALUE
    }

    private fun supplierFilterKey(item: ReorderItem): String {
        val rawId = item.supplierId?.toString()?.trim().orEmpty()
        return if (rawId.isNotEmpty()) rawId else "__NO_SUPPLIER__"
    }

    private fun supplierDisplayName(item: ReorderItem): String {
        return item.supplierName
            ?.trim()
            .orEmpty()
            .ifEmpty { "Fornitore non indicato" }
    }

    private fun selectedSupplierFilterLabel(items: List<ReorderItem>): String {
        val selectedKey = reorderSupplierFilterKey
            ?: return "TUTTI I FORNITORI  ▼"

        val selectedName = items
            .firstOrNull { supplierFilterKey(it) == selectedKey }
            ?.let(::supplierDisplayName)
            ?: "Fornitore"

        return "$selectedName  ▼"
    }

    private fun showSupplierFilterMenu(anchor: View) {
        val items = ReorderStore.getAll()
            .filter(::shouldShowReorderItem)

        val suppliers = items
            .groupBy(::supplierFilterKey)
            .map { (key, supplierItems) ->
                Triple(
                    key,
                    supplierDisplayName(supplierItems.first()),
                    supplierItems.size
                )
            }
            .sortedBy { it.second.lowercase() }

        val popupMenu = PopupMenu(this, anchor)
        popupMenu.menu.add(0, 0, 0, "Tutti i fornitori (${items.size})")

        suppliers.forEachIndexed { index, (_, name, count) ->
            popupMenu.menu.add(0, index + 1, index + 1, "$name ($count)")
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            reorderSupplierFilterKey = if (menuItem.itemId == 0) {
                null
            } else {
                suppliers.getOrNull(menuItem.itemId - 1)?.first
            }

            refreshReorderListPopup()
            true
        }

        popupMenu.show()
    }

    private fun createVirtualizedListAdapter(
        rows: List<() -> View>
    ): BaseAdapter {
        return object : BaseAdapter() {
            override fun getCount(): Int = rows.size

            override fun getItem(position: Int): Any = position

            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup?
            ): View {
                val density = resources.displayMetrics.density
                val row = rows[position]()

                return FrameLayout(this@OverlayService).apply {
                    setPadding(0, 0, 0, (8 * density).toInt())
                    addView(
                        row,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
            }
        }
    }

    private fun createVirtualizedEmptyMessage(message: String): View {
        val density = resources.displayMetrics.density

        return TextView(this).apply {
            text = message
            textSize = 17f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(
                (8 * density).toInt(),
                (48 * density).toInt(),
                (8 * density).toInt(),
                (48 * density).toInt()
            )
        }
    }

    private fun styleReorderTab(
        button: Button?,
        selected: Boolean
    ) {
        button ?: return
        val density = resources.displayMetrics.density

        button.setTextColor(
            if (selected) Color.WHITE else Color.BLACK
        )
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(
                if (selected) {
                    Color.rgb(35, 75, 55)
                } else {
                    Color.rgb(230, 230, 230)
                }
            )
            cornerRadius = 12 * density
        }
    }

    private fun addEmptyListMessage(
        container: LinearLayout,
        message: String
    ) {
        val density = resources.displayMetrics.density

        container.addView(
            TextView(this).apply {
                text = message
                textSize = 17f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(
                    (8 * density).toInt(),
                    (48 * density).toInt(),
                    (8 * density).toInt(),
                    (48 * density).toInt()
                )
            }
        )
    }

    private fun createSupplierHeaderView(
        supplierName: String,
        itemCount: Int
    ): View {
        val density = resources.displayMetrics.density

        return TextView(this).apply {
            text = "$supplierName  ·  $itemCount"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(
                (12 * density).toInt(),
                (9 * density).toInt(),
                (12 * density).toInt(),
                (9 * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(35, 75, 55))
                cornerRadius = 12 * density
            }
        }
    }

    private fun createReorderItemView(item: ReorderItem): View {
        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener {
                openReorderItem(item)
            }
            setPadding(
                (12 * density).toInt(),
                (10 * density).toInt(),
                (8 * density).toInt(),
                (10 * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(245, 245, 245))
                cornerRadius = 12 * density
                setStroke(
                    (1 * density).toInt().coerceAtLeast(1),
                    Color.LTGRAY
                )
            }
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val description = item.description
            .trim()
            .ifEmpty { "Articolo senza descrizione" }

        textContainer.addView(
            TextView(this).apply {
                text = description
                textSize = 16f
                setTextColor(Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        )

        val codes = buildString {
            if (item.articleCode.isNotBlank()) {
                append("Codice: ${item.articleCode.trim()}")
            }
            if (item.supplierArticleCode.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("Cod. fornitore: ${item.supplierArticleCode.trim()}")
            }
            if (item.barcode.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("EAN: ${item.barcode.trim()}")
            }
        }

        if (codes.isNotEmpty()) {
            textContainer.addView(
                TextView(this).apply {
                    text = codes
                    textSize = 13f
                    setTextColor(Color.DKGRAY)
                    setPadding(0, (4 * density).toInt(), 0, 0)
                }
            )
        }

        val quantityToOrder = calculateQuantityToOrder(item)

        val stockLine = buildString {
            append("Giacenza: ${item.stock.formatNullableQuantity()}")
            append("   •   Minima: ${item.minimumStock.formatNullableQuantity()}")
            append("\nDa ordinare: ${quantityToOrder.formatNullableQuantity()}")
        }

        textContainer.addView(
            TextView(this).apply {
                text = stockLine
                textSize = 14f
                setTextColor(Color.rgb(120, 30, 20))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, (6 * density).toInt(), 0, 0)
            }
        )

        root.addView(
            textContainer,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        return root
    }

    /**
     * Applica le regole definitive di visibilità della lista.
     *
     * - minima, massima e lotto tutti a 0: articolo escluso;
     * - minima null e lotto > 0: articolo visibile per decisione manuale;
     * - negli altri casi mostra soltanto articoli realmente da riordinare.
     */
    private fun shouldShowReorderItem(item: ReorderItem): Boolean {
        val minimumStock = item.minimumStock
        val maximumStock = item.maximumStock
        val reorderLot = item.reorderLot

        val explicitlyExcluded =
            minimumStock == 0.0 &&
                    maximumStock == 0.0 &&
                    reorderLot == 0.0

        if (explicitlyExcluded) return false

        if (minimumStock == null && reorderLot != null && reorderLot > 0.0) {
            return true
        }

        if (item.quantityToOrder > 0.0) return true

        val stock = item.stock ?: return false
        val availableStock = item.availableStock ?: return false
        val minimum = minimumStock ?: return false

        return stock <= minimum || availableStock <= 0.0
    }

    private fun calculateQuantityToOrder(
        item: ReorderItem
    ): Double? {
        val reorderLot = item.reorderLot
        val minimumStock = item.minimumStock

        if (
            minimumStock == 0.0 &&
            item.maximumStock == 0.0 &&
            reorderLot == 0.0
        ) {
            return 0.0
        }

        if (item.quantityToOrder > 0.0) {
            return item.quantityToOrder
        }

        /*
         * La minima non impostata, con lotto valorizzato, è il caso
         * concordato per la decisione manuale: mostriamo direttamente il lotto.
         */
        if (minimumStock == null && reorderLot != null && reorderLot > 0.0) {
            return reorderLot
        }

        val stock = item.stock ?: return null
        val availableStock = item.availableStock ?: return null
        val minimum = minimumStock ?: return null

        if (stock > minimum && availableStock > 0.0) {
            return 0.0
        }

        if (reorderLot != null && reorderLot > 0.0) {
            return reorderLot
        }

        return max(0.0, minimum - stock)
    }

    private fun openReorderItem(item: ReorderItem) {
        rememberReorderListPosition()

        if (item.barcode.isBlank()) {
            Toast.makeText(
                this,
                "Barcode non disponibile",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        Toast.makeText(
            this,
            "Apro ${item.articleCode.ifBlank { item.barcode }}",
            Toast.LENGTH_SHORT
        ).show()

        Thread {
            val result = productRepository.getProduct(item.barcode)

            popupHandler.post {
                result.onSuccess { product ->
                    ProductInfoStore.current = product
                    ProductInfoStore.updateHistoryItem(product)

                    // La lista resta aperta dietro al popup articolo.
                    // In questo modo, dopo il salvataggio delle scorte,
                    // l'utente torna subito alla lista senza doverla riaprire.
                    showOrUpdateProductInfoPopup(
                        workflowCompleted = true,
                        manualOpen = true
                    )

                    android.util.Log.d(
                        "OverlayService",
                        "ARTICOLO RIORDINO APERTO EAN=${item.barcode}"
                    )
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        "Impossibile aprire l'articolo",
                        Toast.LENGTH_SHORT
                    ).show()

                    android.util.Log.e(
                        "OverlayService",
                        "ERRORE APERTURA ARTICOLO RIORDINO EAN=${item.barcode}",
                        error
                    )
                }
            }
        }.start()
    }

    private fun createFavoriteItemView(
        item: FavoriteItem
    ): View {
        val density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(
                (12 * density).toInt(),
                (10 * density).toInt(),
                (10 * density).toInt(),
                (10 * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(245, 245, 245))
                cornerRadius = 12 * density
                setStroke(
                    (1 * density).toInt().coerceAtLeast(1),
                    Color.LTGRAY
                )
            }
            setOnClickListener {
                openFavoriteItem(item)
            }
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        textContainer.addView(
            TextView(this).apply {
                text = item.description
                    .trim()
                    .ifEmpty { "Articolo senza descrizione" }
                textSize = 16f
                setTextColor(Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        )

        val codeLine = buildString {
            if (item.articleCode.isNotBlank()) {
                append("Codice: ${item.articleCode.trim()}")
            }
            if (item.barcode.isNotBlank()) {
                if (isNotEmpty()) append("   •   ")
                append("EAN: ${item.barcode.trim()}")
            }
        }

        if (codeLine.isNotEmpty()) {
            textContainer.addView(
                TextView(this).apply {
                    text = codeLine
                    textSize = 13f
                    setTextColor(Color.DKGRAY)
                    setPadding(0, (4 * density).toInt(), 0, 0)
                }
            )
        }

        textContainer.addView(
            TextView(this).apply {
                text = "Prezzo: ${formatFavoritePrice(item.publicPrice)}" +
                        "   •   Giacenza: ${item.stock.ifBlank { "—" }}"
                textSize = 15f
                setTextColor(Color.rgb(35, 75, 55))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, (6 * density).toInt(), 0, 0)
            }
        )

        val starButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_star)
            contentDescription = "Rimuovi dai preferiti"
            isClickable = true
            isFocusable = true
            setPadding(
                (6 * density).toInt(),
                (6 * density).toInt(),
                (6 * density).toInt(),
                (6 * density).toInt()
            )
            setOnClickListener { view ->
                view.parent?.requestDisallowInterceptTouchEvent(true)
                FavoriteRepository.remove(item.articleId)

                Toast.makeText(
                    this@OverlayService,
                    "Articolo rimosso dai preferiti",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        root.addView(
            textContainer,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(
            starButton,
            LinearLayout.LayoutParams(
                (46 * density).toInt(),
                (46 * density).toInt()
            ).apply {
                marginStart = (8 * density).toInt()
            }
        )

        return root
    }

    private fun formatFavoritePrice(raw: String): String {
        val value = raw.toPriceValue()
        return if (value == Double.MAX_VALUE) {
            raw.trim().ifEmpty { "—" }
        } else {
            String.format(
                java.util.Locale.ITALY,
                "%.2f €",
                value
            )
        }
    }

    private fun openFavoriteItem(item: FavoriteItem) {
        rememberReorderListPosition()

        if (item.barcode.isBlank()) {
            Toast.makeText(
                this,
                "Barcode non disponibile",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        Thread {
            val result = productRepository.getProduct(item.barcode)

            popupHandler.post {
                result.onSuccess { product ->
                    ProductInfoStore.current = product
                    ProductInfoStore.updateHistoryItem(product)

                    /*
                     * Mantiene la lista Preferiti aperta dietro al popup,
                     * come già avviene per la lista di riordino.
                     */
                    showOrUpdateProductInfoPopup(
                        workflowCompleted = true,
                        manualOpen = true
                    )

                    android.util.Log.d(
                        "OverlayService",
                        "ARTICOLO PREFERITO APERTO EAN=${item.barcode}"
                    )
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        "Impossibile aprire l'articolo",
                        Toast.LENGTH_SHORT
                    ).show()

                    android.util.Log.e(
                        "OverlayService",
                        "ERRORE APERTURA PREFERITO EAN=${item.barcode}",
                        error
                    )
                }
            }
        }.start()
    }

    private fun createReorderHistoryItemView(
        product: ProductInfo,
        position: Int
    ): View {
        val density = resources.displayMetrics.density

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true

            setOnClickListener {
                rememberReorderListPosition()
                ProductInfoStore.current = product
                removeReorderListPopup()

                showOrUpdateProductInfoPopup(
                    workflowCompleted = true,
                    manualOpen = true
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

            addView(
                TextView(this@OverlayService).apply {
                    text = "$position. ${
                        product.description.trim()
                            .ifEmpty { "Articolo senza descrizione" }
                    }"
                    textSize = 17f
                    setTextColor(Color.BLACK)
                    setTypeface(
                        typeface,
                        android.graphics.Typeface.BOLD
                    )
                }
            )

            addView(
                TextView(this@OverlayService).apply {
                    text = buildString {
                        if (product.articleCode.isNotBlank()) {
                            append("Codice: ${product.articleCode.trim()}")
                        }
                        if (product.barcode.isNotBlank()) {
                            if (isNotEmpty()) append("   •   ")
                            append("EAN: ${product.barcode.trim()}")
                        }
                        if (product.stock.isNotBlank()) {
                            append("\nGiacenza: ${product.stock.trim()}")
                        }
                    }.ifEmpty { "Dati aggiuntivi non disponibili" }

                    textSize = 14f
                    setTextColor(Color.DKGRAY)
                    setPadding(
                        0,
                        (6 * density).toInt(),
                        0,
                        0
                    )
                }
            )
        }
    }

    private fun showReorderConfirmation(
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit
    ) {
        removeReorderConfirmation()
        runCatching { a4LabelsPopupController.remove() }

        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(190, 0, 0, 0))
            isClickable = true
            isFocusable = true
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (22 * density).toInt(),
                (20 * density).toInt(),
                (22 * density).toInt(),
                (18 * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = 20 * density
            }
        }

        card.addView(
            TextView(this).apply {
                text = title
                textSize = 22f
                setTextColor(Color.BLACK)
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }
        )

        card.addView(
            TextView(this).apply {
                text = message
                textSize = 16f
                setTextColor(Color.DKGRAY)
                setPadding(
                    0,
                    (10 * density).toInt(),
                    0,
                    (18 * density).toInt()
                )
            }
        )

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val noButton = Button(this).apply {
            text = "NO"
            setOnClickListener {
                removeReorderConfirmation()
            }
        }

        val yesButton = Button(this).apply {
            text = confirmText
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(183, 28, 28))
                cornerRadius = 12 * density
            }
            setOnClickListener {
                removeReorderConfirmation()
                onConfirm()
            }
        }

        buttons.addView(
            noButton,
            LinearLayout.LayoutParams(
                0,
                (48 * density).toInt(),
                1f
            )
        )
        buttons.addView(
            yesButton,
            LinearLayout.LayoutParams(
                0,
                (48 * density).toInt(),
                1f
            ).apply {
                marginStart = (10 * density).toInt()
            }
        )

        card.addView(buttons)

        root.addView(
            card,
            FrameLayout.LayoutParams(
                min(
                    (390 * density).toInt(),
                    screenWidth - (32 * density).toInt()
                ),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        reorderConfirmationPopup = root
        windowManager.addView(root, params)
    }

    private fun removeReorderConfirmation() {
        val popup = reorderConfirmationPopup ?: return

        try {
            windowManager.removeView(popup)
        } catch (_: Exception) {
        }

        reorderConfirmationPopup = null
    }

    private fun Double?.formatNullableQuantity(): String =
        this?.formatStockQuantity() ?: "—"

    @Suppress("UNCHECKED_CAST")
    private fun <T : View> findViewByTag(
        root: View,
        wantedTag: String
    ): T? {
        if (root.tag == wantedTag) {
            return root as? T
        }

        if (root is android.view.ViewGroup) {
            for (index in 0 until root.childCount) {
                val found = findViewByTag<T>(
                    root.getChildAt(index),
                    wantedTag
                )
                if (found != null) return found
            }
        }

        return null
    }

    private fun removeReorderListPopup() {
        removeReorderConfirmation()
        val popup = reorderListPopup ?: return

        try {
            windowManager.removeView(popup)
        } catch (_: Exception) {
        }

        reorderListPopup = null
        reorderSupplierFilterKey = null

        android.util.Log.d(
            "OverlayService",
            "LISTA RIORDINO CHIUSA"
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

        if (
            workflowCompleted &&
            !manualOpen &&
            !sessionRecordedForCurrentScan &&
            product != null &&
            product.articleId > 0L
        ) {
            sessionRecordedForCurrentScan = true
            addProductToSessionWithCustomerPrice(product)
        }

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
        val bindings = productInfoPopupController.create(
            onStockClick = ::showStockEditPopup,
            onLocationClick = ::showLocationManagementPopup,
            onTouchStarted = {
                popupTimerPausedByUser = true
                popupHandler.removeCallbacks(dismissPopupRunnable)

                android.util.Log.d(
                    "OverlayService",
                    "TIMER POPUP IN PAUSA - DITO APPOGGIATO"
                )
            },
            onTouchFinished = {
                popupTimerPausedByUser = false
                scheduleProductPopupDismiss(ProductInfoStore.current)

                android.util.Log.d(
                    "OverlayService",
                    "TIMER POPUP RIPARTITO - DITO SOLLEVATO"
                )
            },
            onPopupTap = {
                popupHandler.removeCallbacks(dismissPopupRunnable)
                popupTimerPausedByUser = false

                val reopen =
                    reopenScannerAfterPopup &&
                            loadCurrentScanMode() == MODE_INFO

                reopenScannerAfterPopup = false
                removeProductInfoPopup()

                if (reopen) {
                    sessionRecordedForCurrentScan = false
                    openRapidScanner()
                }

                android.util.Log.d(
                    "OverlayService",
                    "POPUP TOCCATO - CHIUSURA IMMEDIATA"
                )
            }
        )

        productInfoPopup = bindings.root
        productInfoPopupParams = bindings.windowParams
        priceValueText = bindings.priceValueText
        articleCodeValueText = bindings.articleCodeValueText
        barcodeValueText = bindings.barcodeValueText
        barcodeImageView = bindings.barcodeImageView
        productImageView = bindings.root.findViewById(
            R.id.productImagePlaceholder
        )
        descriptionValueText = bindings.descriptionValueText
        yearValueText = bindings.yearValueText
        seasonValueText = bindings.seasonValueText
        locationValueText = bindings.locationValueText
        taxablePriceValueText = bindings.taxablePriceValueText
        vatRateValueText = bindings.vatRateValueText
        stockValueText = bindings.stockValueText
        stockStatusContainer = bindings.stockStatusContainer
        stockStatusText = bindings.stockStatusText
        reorderText = bindings.reorderText
        minimumStockValueText = bindings.minimumStockValueText
        reorderLotValueText = bindings.reorderLotValueText
        popupDurationSeekBar = bindings.popupDurationSeekBar
        popupDurationModeButton = bindings.popupDurationModeButton
        popupDurationValueText = bindings.popupDurationValueText

        configureFixedDurationAndSoundControls()
    }

    private fun showLocationManagementPopup() {
        if (locationManagementPopupController.isShowing()) return

        val product = ProductInfoStore.current ?: return

        if (product.articleId <= 0L) {
            Toast.makeText(
                this,
                "ID articolo non disponibile",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        popupHandler.removeCallbacks(dismissPopupRunnable)
        popupTimerPausedByUser = true

        Thread {
            productRepository.getLocations()
                .onSuccess { availableLocations ->
                    popupHandler.post {
                        locationManagementPopupController.show(
                            product = ProductInfoStore.current ?: product,
                            availableLocations = availableLocations,
                            onToggle = { location, currentlyAssigned, complete ->
                                Thread {
                                    val writeResult = if (currentlyAssigned) {
                                        productRepository.removeLocation(
                                            articleId = product.articleId,
                                            locationId = location.id
                                        )
                                    } else {
                                        productRepository.addLocation(
                                            articleId = product.articleId,
                                            locationId = location.id
                                        )
                                    }

                                    val refreshedResult = writeResult.fold(
                                        onSuccess = {
                                            productRepository.getProductLocations(
                                                product.articleId
                                            )
                                        },
                                        onFailure = { Result.failure(it) }
                                    )

                                    popupHandler.post {
                                        refreshedResult.onSuccess { refreshed ->
                                            val current = ProductInfoStore.current ?: product
                                            val updatedProduct = current.copy(
                                                location = refreshed
                                                    .joinToString(" · ") { it.name.trim() },
                                                locations = refreshed
                                            )

                                            ProductInfoStore.current = updatedProduct
                                            ProductInfoStore.updateHistoryItem(updatedProduct)
                                            updateProductInfoPopup(
                                                updatedProduct,
                                                true,
                                                false
                                            )

                                            android.util.Log.d(
                                                "OverlayService",
                                                "UBICAZIONI AGGIORNATE articleId=${product.articleId} " +
                                                        "count=${refreshed.size}"
                                            )
                                        }.onFailure { error ->
                                            android.util.Log.e(
                                                "OverlayService",
                                                "AGGIORNAMENTO UBICAZIONE FALLITO " +
                                                        "articleId=${product.articleId} " +
                                                        "locationId=${location.id}",
                                                error
                                            )
                                        }

                                        complete(refreshedResult)
                                    }
                                }.start()
                            },
                            onCreate = { name, complete ->
                                Thread {
                                    val result = productRepository.createLocation(name).fold(
                                        onSuccess = { created ->
                                            productRepository.addLocation(product.articleId, created.id).getOrThrow()
                                            val available = productRepository.getLocations().getOrThrow()
                                            val assigned = productRepository.getProductLocations(product.articleId).getOrThrow()
                                            Result.success(available to assigned)
                                        },
                                        onFailure = { Result.failure(it) }
                                    )
                                    popupHandler.post {
                                        result.onSuccess { (_, assigned) ->
                                            val current = ProductInfoStore.current ?: product
                                            val updated = current.copy(
                                                location = assigned.joinToString(" · ") { it.name.trim() },
                                                locations = assigned
                                            )
                                            ProductInfoStore.current = updated
                                            ProductInfoStore.updateHistoryItem(updated)
                                            updateProductInfoPopup(updated, true, false)
                                        }
                                        complete(result)
                                    }
                                }.start()
                            },
                            onDelete = { location, complete ->
                                Thread {
                                    val result = productRepository.deleteLocation(location.id)
                                    popupHandler.post { complete(result) }
                                }.start()
                            },
                            onRename = { location, newName, complete ->
                                Thread {
                                    val result = productRepository.renameLocation(
                                        locationId = location.id,
                                        name = newName
                                    ).fold(
                                        onSuccess = {
                                            val available = productRepository
                                                .getLocations()
                                                .getOrThrow()

                                            val assigned = productRepository
                                                .getProductLocations(product.articleId)
                                                .getOrThrow()

                                            Result.success(
                                                Triple(it, available, assigned)
                                            )
                                        },
                                        onFailure = { Result.failure(it) }
                                    )

                                    popupHandler.post {
                                        result.onSuccess { (renamed, _, assigned) ->
                                            val current =
                                                ProductInfoStore.current ?: product

                                            val updated = current.copy(
                                                location = assigned.joinToString(" · ") {
                                                    it.name.trim()
                                                },
                                                locations = assigned
                                            )

                                            ProductInfoStore.current = updated
                                            ProductInfoStore.updateHistoryItem(updated)
                                            updateProductInfoPopup(
                                                updated,
                                                true,
                                                false
                                            )

                                            android.util.Log.d(
                                                "OverlayService",
                                                "UBICAZIONE RINOMINATA " +
                                                        "locationId=${renamed.id} " +
                                                        "name=${renamed.name}"
                                            )
                                        }.onFailure { error ->
                                            android.util.Log.e(
                                                "OverlayService",
                                                "RINOMINA UBICAZIONE FALLITA " +
                                                        "locationId=${location.id}",
                                                error
                                            )
                                        }

                                        complete(
                                            result.map { (renamed, _, _) -> renamed }
                                        )
                                    }
                                }.start()
                            },
                            onDuplicateNext = { location, complete ->
                                Thread {
                                    val result = productRepository
                                        .duplicateNextLocation(location.id)
                                        .fold(
                                            onSuccess = { created ->
                                                productRepository
                                                    .getLocations()
                                                    .getOrThrow()

                                                Result.success(created)
                                            },
                                            onFailure = { Result.failure(it) }
                                        )

                                    popupHandler.post {
                                        result.onSuccess { created ->
                                            android.util.Log.d(
                                                "OverlayService",
                                                "UBICAZIONE SUCCESSIVA CREATA " +
                                                        "locationId=${created.id} " +
                                                        "name=${created.name}"
                                            )
                                        }.onFailure { error ->
                                            android.util.Log.e(
                                                "OverlayService",
                                                "DUPLICAZIONE UBICAZIONE FALLITA " +
                                                        "locationId=${location.id}",
                                                error
                                            )
                                        }

                                        complete(result)
                                    }
                                }.start()
                            },

                            onClose = {
                                popupTimerPausedByUser = false
                                scheduleProductPopupDismiss(ProductInfoStore.current)
                            }
                        )

                        android.util.Log.d(
                            "OverlayService",
                            "GESTIONE UBICAZIONI APERTA articleId=${product.articleId} " +
                                    "disponibili=${availableLocations.size}"
                        )
                    }
                }
                .onFailure { error ->
                    popupHandler.post {
                        popupTimerPausedByUser = false
                        scheduleProductPopupDismiss(ProductInfoStore.current)

                        Toast.makeText(
                            this,
                            "Impossibile caricare le ubicazioni",
                            Toast.LENGTH_LONG
                        ).show()

                        android.util.Log.e(
                            "OverlayService",
                            "ERRORE ELENCO UBICAZIONI",
                            error
                        )
                    }
                }
        }.start()
    }


    private fun showStockEditPopup() {
        if (stockSettingsPopupController.isShowing()) return

        popupHandler.removeCallbacks(dismissPopupRunnable)

        val product = ProductInfoStore.current ?: return

        stockSettingsPopupController.show(
            product = product,
            onSave = { minimumStock, reorderLot, complete ->
                /*
                 * Nel database Due Retail lo stato "escluso dal riordino"
                 * non è rappresentato da tre zeri reali:
                 *
                 * - tabella principale: -1
                 * - tabella store / vista usata dal PC: NULL
                 *
                 * La finestra espone soltanto minimo e lotto; la massima viene
                 * sempre gestita automaticamente. Quando entrambi i valori
                 * visibili sono 0, l'intenzione dell'utente è quindi escludere
                 * completamente l'articolo dal riordino automatico.
                 */
                val excludeFromAutomaticReorder =
                    minimumStock == 0.0 && reorderLot == 0.0

                val minimumStockToSave =
                    if (excludeFromAutomaticReorder) -1.0 else minimumStock
                val maximumStockToSave =
                    if (excludeFromAutomaticReorder) -1.0 else 0.0
                val reorderLotToSave =
                    if (excludeFromAutomaticReorder) -1.0 else reorderLot

                android.util.Log.d(
                    "OverlayService",
                    "SALVATAGGIO SCORTE START articleId=${product.articleId} " +
                            "minimo=$minimumStockToSave massimo=$maximumStockToSave " +
                            "lotto=$reorderLotToSave escluso=$excludeFromAutomaticReorder"
                )

                Thread {
                    productRepository.updateStockSettings(
                        articleId = product.articleId,
                        minimumStock = minimumStockToSave,
                        maximumStock = maximumStockToSave,
                        reorderLot = reorderLotToSave
                    ).onSuccess { updated ->
                        val current = ProductInfoStore.current ?: product
                        val updatedProduct = current.copy(
                            minimumStock = updated.minimumStock
                                .takeIf { it >= 0.0 }
                                ?.formatStockQuantity()
                                ?: "",
                            maximumStock = updated.maximumStock
                                .takeIf { it >= 0.0 }
                                ?.formatStockQuantity()
                                ?: "",
                            reorderLot = updated.reorderLot
                                .takeIf { it >= 0.0 }
                                ?.formatStockQuantity()
                                ?: ""
                        )

                        /*
                         * ReorderStore.add() salva l'intera lista di riordino.
                         * La lista contiene migliaia di articoli, quindi questa
                         * operazione deve restare nel thread di lavoro e non nel
                         * thread grafico.
                         */
                        ReorderStore.add(updatedProduct)

                        popupHandler.post {
                            ProductInfoStore.current = updatedProduct
                            ProductInfoStore.updateHistoryItem(updatedProduct)

                            updateProductInfoPopup(
                                updatedProduct,
                                true,
                                false
                            )

                            complete(Result.success(Unit))

                            Toast.makeText(
                                this,
                                "Scorte aggiornate",
                                Toast.LENGTH_SHORT
                            ).show()

                            android.util.Log.d(
                                "OverlayService",
                                "SALVATAGGIO SCORTE OK articleId=${updated.articleId} minimo=${updated.minimumStock} massimo=${updated.maximumStock} lotto=${updated.reorderLot}"
                            )

                            removeStockEditPopup()

                            if (reorderListPopup != null) {
                                /*
                                 * La lista resta aperta dietro al popup.
                                 * Dopo SALVA viene aggiornata automaticamente,
                                 * mentre il popup articolo rimane visibile con
                                 * i nuovi valori appena salvati.
                                 */
                                refreshReorderListPopup()

                                showOrUpdateProductInfoPopup(
                                    workflowCompleted = true,
                                    manualOpen = true
                                )
                            }
                        }
                    }.onFailure { error ->
                        popupHandler.post {
                            complete(Result.failure(error))
                            Toast.makeText(
                                this,
                                "Errore salvataggio scorte: ${error.message ?: "errore sconosciuto"}",
                                Toast.LENGTH_LONG
                            ).show()
                            android.util.Log.e(
                                "OverlayService",
                                "SALVATAGGIO SCORTE FALLITO",
                                error
                            )
                        }
                    }
                }.start()
            },
            onClose = ::removeStockEditPopup
        )
    }

    /**
     * La durata del popup è ora fissa a 4 secondi.
     *
     * Il vecchio blocco Auto/Manuale viene rimosso completamente e lo stesso
     * spazio viene riutilizzato per il solo comando dei suoni stock.
     */
    private fun configureFixedDurationAndSoundControls() {
        val modeButton = popupDurationModeButton ?: return
        val controlsContainer = modeButton.parent as? ViewGroup ?: return
        val density = resources.displayMetrics.density

        controlsContainer.removeAllViews()

        val toggle = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            isFocusable = true
            minHeight = (44 * density).toInt()

            setPadding(
                (12 * density).toInt(),
                (9 * density).toInt(),
                (12 * density).toInt(),
                (9 * density).toInt()
            )

            fun refresh() {
                val enabled =
                    ScanFeedbackManager.isEnabled(applicationContext)

                text = if (enabled) {
                    "🔊 SUONI STOCK: ON"
                } else {
                    "🔇 SUONI STOCK: OFF"
                }

                setTextColor(
                    if (enabled) {
                        Color.WHITE
                    } else {
                        Color.rgb(30, 35, 32)
                    }
                )

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12 * density
                    setColor(
                        if (enabled) {
                            Color.rgb(0, 105, 62)
                        } else {
                            Color.rgb(225, 228, 226)
                        }
                    )
                }
            }

            setOnClickListener {
                val newValue =
                    !ScanFeedbackManager.isEnabled(applicationContext)

                ScanFeedbackManager.setEnabled(
                    applicationContext,
                    newValue
                )

                refresh()

                Toast.makeText(
                    this@OverlayService,
                    if (newValue) {
                        "Suoni stock attivati"
                    } else {
                        "Suoni stock disattivati"
                    },
                    Toast.LENGTH_SHORT
                ).show()

                /*
                 * Dopo il tocco riparte il conteggio dei 4 secondi,
                 * così il pulsante non scompare mentre viene premuto.
                 */
                popupHandler.removeCallbacks(dismissPopupRunnable)
                scheduleProductPopupDismiss(ProductInfoStore.current)
            }

            refresh()
        }

        controlsContainer.addView(
            toggle,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        popupDurationSeekBar = null
        popupDurationModeButton = null
        popupDurationValueText = null

        android.util.Log.d(
            "OverlayService",
            "CONTROLLO DURATA RIMOSSO - PULSANTE SUONI STOCK AGGIUNTO"
        )
    }

    private fun removeStockEditPopup() {
        if (!stockSettingsPopupController.isShowing()) return

        stockSettingsPopupController.remove()

        popupHandler.removeCallbacks(dismissPopupRunnable)
        scheduleProductPopupDismiss(ProductInfoStore.current)
    }

    private fun loadPopupDurationPreferences() {
        val preferences = applicationContext.getSharedPreferences(
            POPUP_PREFS,
            Context.MODE_PRIVATE
        )

        /*
         * Durata negozio fissa: il popup si chiude sempre dopo 4 secondi,
         * indipendentemente dallo stato stock.
         */
        isPopupDurationAuto = false
        manualPopupDurationSeconds = DEFAULT_POPUP_DURATION_SECONDS
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

        val durationMs = DEFAULT_POPUP_DURATION_SECONDS * 1000L

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
        return DEFAULT_POPUP_DURATION_SECONDS * 1000L
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
        val stockSoundStatus = productInfoPopupController.update(
            product = product,
            workflowCompleted = workflowCompleted
        )

        updateProductImage(product)

        if (
            playStockSound &&
            product != null &&
            stockSoundStatus != null
        ) {
            playStockStatusSound(
                product = product,
                status = stockSoundStatus
            )
        }
    }

    /**
     * Carica la foto prodotto direttamente dal nuovo endpoint del Gateway.
     * Coil esegue rete, decodifica e cache fuori dal thread grafico.
     * In caso di immagine assente (HTTP 404) rimane visibile il placeholder.
     */
    private fun updateProductImage(product: ProductInfo?) {
        val imageView = productImageView ?: return
        val barcode = product?.barcode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (barcode == null) {
            imageView.setImageDrawable(null)
            imageView.contentDescription = "Immagine prodotto non disponibile"
            return
        }

        val imageUrl = gatewayApiClient.getProductImageUrl(barcode)

        /*
         * Il segnaposto con scatolone e testo è disegnato nell'XML sotto
         * l'ImageView. Durante il caricamento e in caso di HTTP 404 questa
         * ImageView resta trasparente; quando la foto arriva la copre.
         */
        imageView.setImageDrawable(null)
        imageView.contentDescription =
            "Immagine di ${product.description.ifBlank { product.articleCode }}"

        imageView.load(imageUrl) {
            crossfade(true)
        }

        android.util.Log.d(
            "OverlayService",
            "CARICAMENTO IMMAGINE PRODOTTO EAN=$barcode URL=$imageUrl"
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
        locationManagementPopupController.remove()
        stockSettingsPopupController.remove()
        productInfoPopupController.remove()

        productInfoPopup = null
        productInfoPopupParams = null
        priceValueText = null
        articleCodeValueText = null
        barcodeValueText = null
        barcodeImageView = null
        productImageView = null
        descriptionValueText = null
        yearValueText = null
        seasonValueText = null
        locationValueText = null
        taxablePriceValueText = null
        vatRateValueText = null
        stockValueText = null
        stockStatusContainer = null
        stockStatusText = null
        reorderText = null
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


    /**
     * Riproduce il feedback stock soltanto quando il workflow è completo.
     *
     * Verde  -> BLIP breve.
     * Giallo -> CRASH: articolo al limite.
     * Rosso  -> CRASH: articolo da riordinare.
     */
    private fun playStockStatusSound(
        product: ProductInfo,
        status: ProductInfoPopup.StockSoundStatus
    ) {
        if (!ScanFeedbackManager.isEnabled(applicationContext)) {
            android.util.Log.d(
                "OverlayService",
                "SUONO STOCK DISATTIVATO status=$status EAN=${product.barcode}"
            )
            return
        }

        when (status) {
            ProductInfoPopup.StockSoundStatus.REGULAR -> {
                ScanFeedbackManager.playSuccess(applicationContext)
            }

            ProductInfoPopup.StockSoundStatus.WARNING -> {
                ScanFeedbackManager.playWarning(applicationContext)
            }

            ProductInfoPopup.StockSoundStatus.REORDER -> {
                urgentStockTone?.startTone(
                    ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,
                    650
                )
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
        popupHandler.removeCallbacks(scannerLongPressRunnable)
        popupHandler.removeCallbacks(dismissPopupRunnable)
        popupHandler.removeCallbacks(dismissScanErrorRunnable)
        removeProductInfoPopup()
        removeScanErrorPopup()
        removeHistoryPopup()
        removeReorderListPopup()
        removeReorderConfirmation()
        ReorderStore.removeSizeListener(reorderStoreListener)
        FavoriteRepository.removeListener(favoriteStoreListener)

        urgentStockTone?.release()
        urgentStockTone = null

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