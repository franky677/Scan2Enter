package com.scan2enter.overlay.popup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import coil3.load
import com.scan2enter.R
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.api.ProductExpiryDto
import com.scan2enter.model.ProductInfo
import kotlin.math.min
import com.scan2enter.favorites.FavoriteRepository
/**
 * Gestisce la creazione, la rimozione e l'aggiornamento grafico
 * del popup informazioni articolo.
 *
 * Timer, suoni e coordinamento del workflow restano in OverlayService.
 */
class ProductInfoPopup(
    private val context: Context,
    private val windowManager: WindowManager
) {

    private val gatewayApiClient = GatewayApiClient()

    enum class StockSoundStatus {
        REGULAR,
        WARNING,
        REORDER
    }

    data class Bindings(
        val root: View,
        val windowParams: WindowManager.LayoutParams,
        val blockedBanner: TextView,
        val priceValueText: TextView,
        val articleCodeValueText: TextView,
        val barcodeValueText: TextView,
        val barcodeImageView: ImageView,
        val productImageView: ImageView,
        val favoriteButton: ImageView,
        val descriptionValueText: TextView,
        val purchaseNetValueText: TextView,
        val supplierNameValueText: TextView,
        val supplierCodeValueText: TextView,
        val yearValueText: TextView,
        val seasonValueText: TextView,
        val locationValueText: TextView,
        val healthLastSaleText: TextView,
        val healthSold12Text: TextView,
        val healthRotationText: TextView,
        val healthCoverageText: TextView,
        val taxablePriceValueText: TextView,
        val vatRateValueText: TextView,
        val stockValueText: TextView,
        val availableStockValueText: TextView,
        val stockStatusContainer: LinearLayout,
        val stockStatusText: TextView,
        val reorderText: TextView,
        val healthSection: LinearLayout,
        val healthStatusText: TextView,
        val healthDetailsText: TextView,
        val healthSecondaryText: TextView,
        val healthCommercialBar: FrameLayout,
        val healthCommercialMarker: View,
        val healthCommercialScoreText: TextView,
        val healthCommercialDescriptionText: TextView,
        val healthEconomicBar: FrameLayout,
        val healthEconomicMarker: View,
        val healthEconomicScoreText: TextView,
        val healthEconomicDescriptionText: TextView,
        val minimumStockValueText: TextView,
        val reorderLotValueText: TextView,
        val popupDurationSeekBar: SeekBar,
        val popupDurationModeButton: TextView,
        val popupDurationValueText: TextView
    )

    private var bindings: Bindings? = null

    fun isShowing(): Boolean = bindings != null

    fun create(
        onStockClick: () -> Unit,
        onLocationClick: () -> Unit,
        onPriceLongClick: () -> Unit,
        onExpiryLongClick: () -> Unit,
        onTouchStarted: () -> Unit,
        onTouchFinished: () -> Unit,
        onPopupTap: () -> Unit
    ): Bindings {
        bindings?.let { existing ->
            /*
             * Se il binding esiste ed è ancora realmente attaccato al
             * WindowManager lo riutilizziamo. Se invece la View è rimasta
             * scollegata per una chiusura/race precedente, scartiamo il binding
             * e ricreiamo il popup da zero.
             */
            if (existing.root.isAttachedToWindow) {
                return existing
            }

            bindings = null
        }

        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        val overlayRoot = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 1.0f
            clipChildren = false
            clipToPadding = false
        }

        val popupView = LayoutInflater.from(context)
            .inflate(R.layout.product_info_popup, overlayRoot, false)

        val blockedBanner = TextView(context).apply {
            text = "⛔  ARTICOLO BLOCCATO"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * density
                setColor(Color.rgb(183, 28, 28))
            }
            visibility = View.GONE
        }

        val mainContent =
            popupView.findViewById<LinearLayout>(
                R.id.productInfoMainContent
            )

        mainContent.addView(
            blockedBanner,
            1,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (12 * density).toInt()
                rightMargin = (12 * density).toInt()
                topMargin = (8 * density).toInt()
                bottomMargin = (4 * density).toInt()
            }
        )

        /*
         * SALUTE ARTICOLO V2
         *
         * Il layout XML esistente resta invariato: aggiungiamo qui le due barre
         * Commerciale/Economica e nascondiamo i tre testi V1. In questo modo
         * la modifica resta confinata al popup e non coinvolge scanner o workflow.
         */
        val healthSection = popupView.findViewById<LinearLayout>(R.id.productHealthSection)
        val healthStatusText = popupView.findViewById<TextView>(R.id.productHealthStatusText)
        val healthDetailsText = popupView.findViewById<TextView>(R.id.productHealthDetailsText)
        val healthSecondaryText = popupView.findViewById<TextView>(R.id.productHealthSecondaryText)

        healthStatusText.visibility = View.GONE
        healthDetailsText.visibility = View.GONE
        healthSecondaryText.visibility = View.GONE

        fun createHealthGradientBar(): Pair<FrameLayout, View> {
            val barHeight = (17 * density).toInt()
            val markerWidth = (4 * density).toInt()
            val markerHeight = (24 * density).toInt()

            val frame = FrameLayout(context).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(
                        Color.rgb(0, 170, 70),     // verde
                        Color.rgb(120, 200, 90),   // verde chiaro
                        Color.rgb(245, 220, 60),   // giallo
                        Color.rgb(196, 145, 45),   // ocra
                        Color.rgb(245, 124, 0),    // arancione
                        Color.rgb(210, 45, 45),    // rosso
                        Color.rgb(120, 25, 45),    // bordeaux
                        Color.rgb(85, 35, 110),    // viola
                        Color.rgb(15, 15, 15)      // nero
                    )
                ).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 9 * density
                }
                clipChildren = false
                clipToPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    barHeight
                ).apply {
                    topMargin = (3 * density).toInt()
                    bottomMargin = (4 * density).toInt()
                }
            }

            val marker = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 2 * density
                    setColor(Color.WHITE)
                    setStroke((1 * density).toInt().coerceAtLeast(1), Color.BLACK)
                }
            }

            frame.addView(
                marker,
                FrameLayout.LayoutParams(markerWidth, markerHeight).apply {
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                }
            )

            return frame to marker
        }

        fun createHealthBlock(title: String): Array<View> {
            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleText = TextView(context).apply {
                text = title
                textSize = 13f
                setTextColor(Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val scoreText = TextView(context).apply {
                text = "0"
                textSize = 18f
                gravity = Gravity.END
                setTextColor(Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            titleRow.addView(
                titleText,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            titleRow.addView(
                scoreText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            val (bar, marker) = createHealthGradientBar()

            val descriptionText = TextView(context).apply {
                textSize = 12f
                setTextColor(Color.rgb(55, 55, 55))
                maxLines = 1
                includeFontPadding = false
                setAutoSizeTextTypeUniformWithConfiguration(
                    9,
                    12,
                    1,
                    TypedValue.COMPLEX_UNIT_SP
                )
                setPadding(0, 0, 0, (4 * density).toInt())
            }

            healthSection.addView(titleRow)
            healthSection.addView(bar)
            healthSection.addView(descriptionText)

            return arrayOf(bar, marker, scoreText, descriptionText)
        }

        val commercialViews = createHealthBlock("COMMERCIALE")
        val economicViews = createHealthBlock("ECONOMICA")


        val createdBindings = Bindings(
            root = overlayRoot,
            windowParams = createWindowParams(),
            blockedBanner = blockedBanner,
            priceValueText = popupView.findViewById(R.id.productPublicPriceText),
            articleCodeValueText = popupView.findViewById(R.id.productArticleCodeText),
            barcodeValueText = popupView.findViewById(R.id.productBarcodeText),
            barcodeImageView = popupView.findViewById(R.id.productBarcodeImage),
            productImageView = popupView.findViewById(R.id.productImagePlaceholder),
            favoriteButton = popupView.findViewById(R.id.productFavoriteButton),
            descriptionValueText = popupView.findViewById(R.id.productDescriptionText),
            purchaseNetValueText = popupView.findViewById(R.id.productPurchaseNetText),
            supplierNameValueText = popupView.findViewById(R.id.productSupplierNameText),
            supplierCodeValueText = popupView.findViewById(R.id.productSupplierCodeText),
            yearValueText = popupView.findViewById(R.id.productYearText),
            seasonValueText = popupView.findViewById(R.id.productSeasonText),
            locationValueText = popupView.findViewById(R.id.productLocationText),
            healthLastSaleText = popupView.findViewById(R.id.healthLastSaleText),
            healthSold12Text = popupView.findViewById(R.id.healthSold12Text),
            healthRotationText = popupView.findViewById(R.id.healthRotationText),
            healthCoverageText = popupView.findViewById(R.id.healthCoverageText),
            taxablePriceValueText = popupView.findViewById(R.id.productTaxablePriceText),
            vatRateValueText = popupView.findViewById(R.id.productVatRateText),
            stockValueText = popupView.findViewById(R.id.productStockText),
            availableStockValueText = popupView.findViewById(R.id.productAvailableStockText),
            stockStatusContainer = popupView.findViewById(R.id.productStockStatusContainer),
            stockStatusText = popupView.findViewById(R.id.productStockStatusText),
            reorderText = popupView.findViewById(R.id.productReorderText),
            healthSection = healthSection,
            healthStatusText = healthStatusText,
            healthDetailsText = healthDetailsText,
            healthSecondaryText = healthSecondaryText,
            healthCommercialBar = commercialViews[0] as FrameLayout,
            healthCommercialMarker = commercialViews[1],
            healthCommercialScoreText = commercialViews[2] as TextView,
            healthCommercialDescriptionText = commercialViews[3] as TextView,
            healthEconomicBar = economicViews[0] as FrameLayout,
            healthEconomicMarker = economicViews[1],
            healthEconomicScoreText = economicViews[2] as TextView,
            healthEconomicDescriptionText = economicViews[3] as TextView,
            minimumStockValueText = popupView.findViewById(R.id.productMinimumStockText),
            reorderLotValueText = popupView.findViewById(R.id.productReorderLotValueText),
            popupDurationSeekBar = popupView.findViewById(R.id.popupDurationSeekBar),
            popupDurationModeButton = popupView.findViewById(R.id.popupDurationModeButton),
            popupDurationValueText = popupView.findViewById(R.id.popupDurationValueText)
        )

        popupView.findViewById<TextView>(R.id.closeProductInfoButton)
            .visibility = View.GONE

        popupView.findViewById<View>(R.id.productStockCard)
            .setOnClickListener { onStockClick() }

        createdBindings.favoriteButton.isClickable = false
        createdBindings.favoriteButton.isFocusable = false

        // L'intera scheda dell'ubicazione è cliccabile, non soltanto il testo.
        (createdBindings.locationValueText.parent as? View)?.apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { onLocationClick() }
        }

        createdBindings.priceValueText.apply {
            isClickable = true
            isFocusable = true
            isLongClickable = true

            setOnLongClickListener {
                onPriceLongClick()
                true
            }
        }

        /*
         * SCADENZA PRODOTTO
         *
         * Riutilizziamo la tessera che prima mostrava la copertura.
         * Il dato di copertura continua a esistere nella Salute Articolo,
         * ma questa icona del popup diventa l'accesso rapido alla scadenza.
         */
        createdBindings.healthCoverageText.apply {
            text = "—"
            isClickable = true
            isFocusable = true
            isLongClickable = true

            setOnLongClickListener {
                onExpiryLongClick()
                true
            }

            (parent as? ViewGroup)?.let { container ->
                for (index in 0 until container.childCount) {
                    val child = container.getChildAt(index)
                    if (
                        child is TextView &&
                        child !== this &&
                        child.text.toString().trim().uppercase()
                            .contains("COPERTURA")
                    ) {
                        child.text = "SCADENZA"
                    }
                }
            }
        }

        val horizontalMargin = (6 * density).toInt()
        val topMargin = (24 * density).toInt()
        val bottomMargin = (8 * density).toInt()
        val bottomBreathingRoom = (22 * density).toInt()
        val shadowOffset = (5 * density).toInt()

        val popupWidth = min(
            (430 * density).toInt(),
            screenWidth - horizontalMargin * 2
        )

        val popupMaxHeight = screenHeight - topMargin - bottomMargin

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

        val cardContainer = object : FrameLayout(context) {
            private var touchDownX = 0f
            private var touchDownY = 0f
            private var touchDownTime = 0L

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownX = event.x
                        touchDownY = event.y
                        touchDownTime = System.currentTimeMillis()
                        onTouchStarted()
                    }

                    MotionEvent.ACTION_UP -> {
                        onTouchFinished()

                        val elapsed = System.currentTimeMillis() - touchDownTime
                        val deltaX = kotlin.math.abs(event.x - touchDownX)
                        val deltaY = kotlin.math.abs(event.y - touchDownY)
                        val tapTolerance = 18 * density

                        if (
                            elapsed <= 350L &&
                            deltaX <= tapTolerance &&
                            deltaY <= tapTolerance
                        ) {
                            onPopupTap()
                        }
                    }

                    MotionEvent.ACTION_CANCEL -> onTouchFinished()
                }

                return super.dispatchTouchEvent(event)
            }
        }.apply {
            clipChildren = false
            clipToPadding = false
        }

        val grayBase = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.BLACK)
                cornerRadius = 22 * density
            }
        }

        cardContainer.addView(
            grayBase,
            FrameLayout.LayoutParams(popupWidth, cardHeight).apply {
                gravity = Gravity.TOP or Gravity.START
                this.topMargin = shadowOffset
            }
        )

        cardContainer.addView(
            popupView,
            FrameLayout.LayoutParams(popupWidth, cardHeight).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        )

        overlayRoot.addView(
            cardContainer,
            FrameLayout.LayoutParams(
                popupWidth,
                cardHeight + shadowOffset
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                this.topMargin = topMargin
            }
        )

        bindings = createdBindings
        windowManager.addView(
            createdBindings.root,
            createdBindings.windowParams
        )

        return createdBindings
    }

    /**
     * Aggiorna tutti i dati visibili e restituisce lo stato stock che
     * OverlayService potrà eventualmente trasformare in feedback sonoro.
     */
    fun update(
        product: ProductInfo?,
        workflowCompleted: Boolean
    ): StockSoundStatus? {
        val current = bindings ?: return null

        fun valueOrLoading(value: String): String =
            value.trim().takeIf { it.isNotEmpty() } ?: "lettura…"

        fun valueOrEmpty(value: String): String =
            value.trim().takeIf { it.isNotEmpty() && it != "-1" } ?: ""

        if (product == null) {
            current.blockedBanner.visibility = View.GONE
            current.priceValueText.text = "—"
            current.articleCodeValueText.text = "—"
            current.barcodeValueText.text = "—"
            current.barcodeImageView.setImageDrawable(null)
            current.barcodeImageView.visibility = View.GONE
            current.productImageView.setImageDrawable(null)
            current.descriptionValueText.text = "Nessun articolo letto"
            current.purchaseNetValueText.text = "—"
            current.supplierNameValueText.text = "—"
            current.supplierCodeValueText.text = "—"
            current.yearValueText.text = "—"
            current.seasonValueText.text = "—"
            current.locationValueText.text = "—"
            current.taxablePriceValueText.text = "—"
            current.vatRateValueText.text = "—"
            current.stockValueText.text = "—"
            current.availableStockValueText.text = "—"
            current.stockStatusContainer.visibility = View.GONE
            current.stockStatusText.text = ""
            current.reorderText.text = ""
            current.healthSection.visibility = View.GONE
            current.healthStatusText.text = ""
            current.healthDetailsText.text = ""
            current.healthSecondaryText.text = ""
            current.healthCommercialScoreText.text = ""
            current.healthCommercialDescriptionText.text = ""
            current.healthEconomicScoreText.text = ""
            current.healthEconomicDescriptionText.text = ""
            current.minimumStockValueText.text = ""
            current.reorderLotValueText.text = ""
            current.healthCoverageText.text = "—"
            return null
        }

        current.blockedBanner.visibility = if (product.active) View.GONE else View.VISIBLE

        current.priceValueText.text = formatPublicPrice(product.publicPrice)

        current.articleCodeValueText.text = valueOrLoading(product.articleCode)
        current.barcodeValueText.text = valueOrLoading(product.barcode)
        current.descriptionValueText.text = valueOrLoading(product.description)

        current.purchaseNetValueText.text =
            product.purchaseTaxable.trim()
                .takeIf { it.isNotEmpty() }
                ?.let { "€ ${formatPlainDecimal(it)}" }
                ?: "—"

        current.supplierNameValueText.text =
            product.supplierName.trim().ifEmpty { "—" }
        current.supplierCodeValueText.text =
            product.supplierArticleCode.trim().ifEmpty { "—" }

        current.taxablePriceValueText.text =
            product.taxablePrice.trim()
                .takeIf { it.isNotEmpty() }
                ?.let { "Imp. € ${formatPlainDecimal(it)}" }
                ?: "Imp. —"

        current.vatRateValueText.text =
            product.vatRate.trim()
                .takeIf { it.isNotEmpty() }
                ?.let { "IVA ${formatPlainDecimal(it)}%" }
                ?: "IVA —"
        current.seasonValueText.text = valueOrLoading(product.season)
        current.yearValueText.text = valueOrLoading(product.year)
        current.locationValueText.text = product.locations
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" · ")
            .ifEmpty {
                product.location.trim().ifEmpty { "Non assegnata" }
            }
        val stockText = valueOrLoading(product.stock)

        current.stockValueText.text = stockText
        current.availableStockValueText.text =
            product.availableStock.trim().ifEmpty { "—" }

        current.stockValueText.textSize = when {
            stockText.length >= 5 -> 40f
            stockText.length == 4 -> 46f
            else -> 56f
        }

        current.minimumStockValueText.text = valueOrEmpty(product.minimumStock)
        current.reorderLotValueText.text = valueOrEmpty(product.reorderLot)
        val isFavorite = FavoriteRepository.isFavorite(product.articleId)

        current.favoriteButton.setImageResource(
            if (isFavorite) {
                R.drawable.ic_star
            } else {
                R.drawable.ic_star_border
            }
        )

        current.favoriteButton.alpha =
            if (isFavorite) 1.0f else 0.35f
        val stockSoundStatus = updateStockStatus(
            product = product,
            current = current
        )


        current.productImageView.setImageDrawable(null)
        current.productImageView.visibility = View.VISIBLE

        val productBarcode = product.barcode.trim()

        if (productBarcode.isNotEmpty()) {
            val imageUrl =
                gatewayApiClient.getProductImageUrl(productBarcode) +
                        "?t=${System.currentTimeMillis()}"

            android.util.Log.d(
                "ProductInfoPopup",
                "CARICAMENTO IMMAGINE = $imageUrl"
            )

            current.productImageView.load(imageUrl) {
                listener(
                    onStart = {
                        android.util.Log.d(
                            "ProductInfoPopup",
                            "IMMAGINE START = $imageUrl"
                        )
                    },
                    onSuccess = { _, result ->
                        android.util.Log.d(
                            "ProductInfoPopup",
                            "IMMAGINE OK = ${result.dataSource}"
                        )
                    },
                    onError = { _, result ->
                        android.util.Log.e(
                            "ProductInfoPopup",
                            "IMMAGINE ERRORE = ${result.throwable.message}",
                            result.throwable
                        )
                    }
                )
            }
        } else {
            android.util.Log.w(
                "ProductInfoPopup",
                "IMMAGINE NON CARICATA: BARCODE VUOTO"
            )
        }


        val barcodeBitmap = createEan13Bitmap(product.barcode)

        if (barcodeBitmap != null) {
            current.barcodeImageView.setImageBitmap(barcodeBitmap)
            current.barcodeImageView.visibility = View.VISIBLE
        } else {
            current.barcodeImageView.setImageDrawable(null)
            current.barcodeImageView.visibility = View.GONE
        }

        android.util.Log.d(
            "ProductInfoPopup",
            "GRAFICA AGGIORNATA completed=$workflowCompleted " +
                    "EAN=${product.barcode} barcodeGraphic=${barcodeBitmap != null}"
        )

        return stockSoundStatus
    }

    fun updateExpiry(expiry: ProductExpiryDto?) {
        val current = bindings ?: return

        current.healthCoverageText.text =
            expiry?.takeIf { it.hasExpiry }
                ?.monthYearText
                ?: "—"

        current.healthCoverageText.setTextColor(
            when {
                expiry == null || !expiry.hasExpiry ->
                    Color.rgb(70, 70, 70)

                expiry.isExpired ->
                    Color.rgb(190, 30, 30)

                expiry.daysToExpiry <= 92 ->
                    Color.rgb(230, 120, 0)

                else ->
                    Color.rgb(40, 40, 40)
            }
        )

        current.healthCoverageText.contentDescription =
            when {
                expiry == null || !expiry.hasExpiry ->
                    "Scadenza non impostata"

                expiry.isExpired ->
                    "Scaduto ${expiry.monthYearText}"

                else ->
                    "Scadenza ${expiry.monthYearText}"
            }
    }


    private fun updateHealthPanel(
        product: ProductInfo,
        current: Bindings
    ) {
        val health = product.health
        if (health == null) {
            current.healthSection.visibility = View.GONE
            current.healthCommercialScoreText.text = ""
            current.healthCommercialDescriptionText.text = ""
            current.healthEconomicScoreText.text = ""
            current.healthEconomicDescriptionText.text = ""
            current.healthLastSaleText.text = "—"
            current.healthSold12Text.text = "—"
            current.healthRotationText.text = "—"
            return
        }

        current.healthSection.visibility = View.VISIBLE

        // V2: pannello neutro. Il significato cromatico è interamente nelle barre.
        current.healthSection.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14 * context.resources.displayMetrics.density
            setColor(Color.rgb(245, 245, 245))
            setStroke(
                (1 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1),
                Color.rgb(215, 215, 215)
            )
        }

        val commercialScore = health.punteggioCommerciale.coerceIn(0, 100)
        val economicScore = health.punteggioEconomico.coerceIn(0, 100)

        current.healthCommercialScoreText.text = commercialScore.toString()
        current.healthCommercialDescriptionText.text =
            health.descrizioneCommerciale.trim().ifEmpty { "—" }

        current.healthEconomicScoreText.text = economicScore.toString()
        current.healthEconomicDescriptionText.text =
            health.descrizioneEconomica.trim().ifEmpty { "—" }

        fun positionMarker(
            bar: FrameLayout,
            marker: View,
            score: Int
        ) {
            bar.post {
                val markerWidth = marker.layoutParams.width
                val usableWidth = (bar.width - markerWidth).coerceAtLeast(0)
                val x = usableWidth * (score.coerceIn(0, 100) / 100f)

                (marker.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    lp.leftMargin = x.toInt()
                    lp.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    marker.layoutParams = lp
                }
            }
        }

        positionMarker(
            current.healthCommercialBar,
            current.healthCommercialMarker,
            commercialScore
        )

        positionMarker(
            current.healthEconomicBar,
            current.healthEconomicMarker,
            economicScore
        )

        fun dateShort(raw: String?): String {
            val s = raw?.trim().orEmpty()
            if (s.isEmpty()) return "Mai"
            val d = s.take(10).split("-")
            return if (d.size == 3) "${d[2]}/${d[1]}/${d[0].takeLast(2)}" else s.take(10)
        }

        fun n(value: Double?): String =
            value?.let {
                if (it == it.toLong().toDouble()) it.toLong().toString()
                else String.format(java.util.Locale.ITALY, "%.2f", it)
            } ?: "—"

        // La copertura resta disponibile nei dati Salute Articolo,
        // ma la quarta tessera del popup è ora riservata alla SCADENZA.
        current.healthLastSaleText.text = dateShort(health.ultimaVendita)
        current.healthSold12Text.text = n(health.venduto12M)
        current.healthRotationText.text = n(health.rotazione12M)

        android.util.Log.d(
            "ProductInfoPopup",
            "SALUTE V2 commerciale=$commercialScore economica=$economicScore fifo=${health.valoreFifo}"
        )
    }

    private fun updateStockStatus(
        product: ProductInfo,
        current: Bindings
    ): StockSoundStatus? {
        val stock = product.stock.toNumericValue()
        val minimumStock = product.minimumStock.toNumericValue()
        val maximumStock = product.maximumStock.toNumericValue()
        val availableStock = product.availableStock.toNumericValue()
        val reorderLot = product.reorderLot.toNumericValue()

        val container = current.stockStatusContainer
        val statusText = current.stockStatusText
        val orderText = current.reorderText

        /*
         * In Due Retail la scorta massima può arrivare vuota/null anche quando
         * minima e lotto sono stati impostati a zero.
         *
         * Minima = 0 e lotto = 0 identificano comunque l'articolo escluso
         * dal riordino automatico.
         */
        val excludedFromAutomaticReorder =
            (
                    minimumStock == null &&
                            maximumStock == null &&
                            reorderLot == null
                    ) ||
                    (
                            minimumStock == 0.0 &&
                                    reorderLot == 0.0 &&
                                    (maximumStock == null || maximumStock == 0.0)
                            )

        if (excludedFromAutomaticReorder) {
            container.visibility = View.VISIBLE
            container.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * context.resources.displayMetrics.density
                setColor(Color.rgb(230, 230, 230))
            }

            statusText.visibility = View.VISIBLE
            statusText.text = "✓ Articolo escluso dal riordino automatico"
            statusText.setTextColor(Color.BLACK)

            orderText.visibility = View.GONE
            orderText.text = ""

            updateHealthPanel(product, current)
            return null
        }

        if (
            stock == null ||
            minimumStock == null ||
            availableStock == null
        ) {
            container.visibility = View.GONE
            statusText.text = ""
            orderText.text = ""
            updateHealthPanel(product, current)
            return null
        }

        container.visibility = View.VISIBLE

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16 * context.resources.displayMetrics.density
        }

        val soundStatus = when {
            stock <= 0.0 || availableStock <= 0.0 -> {
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

            stock <= minimumStock -> {
                background.setColor(Color.rgb(255, 214, 0))
                statusText.text = "SOTTO SCORTA"
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
                background.setColor(Color.rgb(0, 200, 83))
                statusText.text = "SCORTA REGOLARE"
                statusText.setTextColor(Color.BLACK)
                orderText.visibility = View.GONE
                orderText.text = ""
                StockSoundStatus.REGULAR
            }
        }

        container.background = background

        updateHealthPanel(product, current)

        container.background = background
        return soundStatus
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

    private fun formatPlainDecimal(raw: String): String {
        val value = raw.trim()
            .replace("€", "")
            .replace(" ", "")
            .replace(",", ".")
            .toDoubleOrNull()
            ?: return raw.trim()

        return String.format(
            java.util.Locale.ITALY,
            "%.2f",
            value
        )
    }

    private fun formatPublicPrice(rawValue: String): String {
        val cleaned = rawValue
            .trim()
            .replace("€", "")
            .replace(" ", "")

        if (cleaned.isEmpty()) return "lettura…"

        val normalized = when {
            cleaned.contains(',') && cleaned.contains('.') ->
                cleaned.replace(".", "").replace(',', '.')
            else -> cleaned.replace(',', '.')
        }

        val numericValue = normalized.toDoubleOrNull()
            ?: return rawValue.trim()

        return String.format(
            java.util.Locale.ITALY,
            "%.2f",
            numericValue
        )
    }

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
        val bitmapHeight = 110

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

    fun remove() {
        val current = bindings ?: return

        /*
         * Invalidiamo subito lo stato interno PRIMA di parlare con
         * WindowManager. In questo modo un eventuale show/update concorrente
         * non può riutilizzare un binding che sta per essere rimosso.
         */
        bindings = null

        // Ferma/sgancia anche l'eventuale richiesta immagine associata alla View.
        current.productImageView.setImageDrawable(null)
        current.barcodeImageView.setImageDrawable(null)

        try {
            if (current.root.isAttachedToWindow) {
                windowManager.removeView(current.root)
            }
        } catch (_: Exception) {
        }
    }

    private fun createWindowParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
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
    }
}