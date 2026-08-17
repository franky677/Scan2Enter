package com.scan2enter.labels.a4

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.scan2enter.scanner.ScannerModeDetector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class A4LabelsPopup(
    private val context: Context,
    private val windowManager: WindowManager
) {

    private companion object {
        const val PDF_PREFS = "a4_pdf_preferences"
        const val SHOW_PREFIX_KEY = "show_article_prefix"
        const val SHOW_SHELF_PRICE_KEY = "show_shelf_price"

        const val PACKAGING_PREFS = "a4_packaging_preferences"
        const val PACKAGING_TYPE_KEY = "packaging_type"
        const val INCLUDE_HOOK_KEY = "include_hook_label"
        const val SHOW_PRICE_KEY = "show_blister_price"

        const val OFFER_PREFS = "a4_offer_preferences"
        const val OFFER_FORMAT_KEY = "offer_format"
        const val OFFER_SHOW_OLD_PRICE_KEY = "offer_show_old_price"
        const val OFFER_SHOW_BARCODE_KEY = "offer_show_barcode"
        const val OFFER_SHOW_IMAGE_KEY = "offer_show_image"
        const val OFFER_SHOW_PREFIX_KEY = "offer_show_prefix"

        const val TYPE_BLISTER_LARGE = "BLISTER_LARGE"
        const val TYPE_BLISTER_LONG = "BLISTER_LONG"
        const val TYPE_BLISTER_BIG = "BLISTER_BIG"
    }

    private enum class Section {
        HUB,
        SHELF,
        BLISTER,
        OFFER
    }

    private var root: View? = null
    private var listContainer: LinearLayout? = null
    private var countText: TextView? = null
    private var previewCode: TextView? = null
    private var previewDescription: TextView? = null
    private var previewBarcode: TextView? = null
    private var previewPrice: TextView? = null
    private var previewDate: TextView? = null
    private var showPrefixCheckBox: CheckBox? = null
    private var showPriceCheckBox: CheckBox? = null

    /*
     * Mantiene la sezione corrente quando il popup viene temporaneamente
     * rimosso per una scansione e poi riaperto.
     */
    private var currentSection = Section.HUB

    /*
     * OFFERTA deve avere un articolo selezionato proprio.
     * Non può dipendere dall'ultimo elemento della pagina SCAFFALE.
     */
    private var selectedOfferItem: A4LabelItem? = null

    fun selectOfferItem(item: A4LabelItem) {
        selectedOfferItem = item
    }

    fun isOfferSection(): Boolean {
        return currentSection == Section.OFFER
    }

    private val storeListener: () -> Unit = {
        root?.post { refreshShelf() }
    }

    fun show(
        onSearchRequested: () -> Unit = {},
        onHardwareScanRequested: () -> Unit = {},
        onOfferScanRequested: () -> Unit = {},
        onBlisterSearchRequested: ((String, Boolean, Boolean) -> Unit)? = null,
        onBlisterScanRequested: ((String, Boolean, Boolean) -> Unit)? = null,
        onShelfActivated: () -> Unit = {},
        onBlisterActivated: () -> Unit = {},
        onHubActivated: () -> Unit = {},
        onClosed: () -> Unit,
        startAtHub: Boolean = true
    ) {
        if (startAtHub) {
            currentSection = Section.HUB
        }

        if (root != null) {
            refreshShelf()
            return
        }

        A4LabelStore.initialize(context.applicationContext)
        A4LabelStore.addListener(storeListener)

        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        val overlay = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusableInTouchMode = true
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 22 * density
            }
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        titleBlock.addView(
            TextView(context).apply {
                text = "ETICHETTE A4"
                textSize = 21f
                setTextColor(Color.rgb(20, 35, 28))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        )

        titleBlock.addView(
            TextView(context).apply {
                text = "CENTRO STAMPE"
                textSize = 10f
                setTextColor(Color.rgb(95, 112, 103))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        )

        val backOrClose = TextView(context).apply {
            text = if (currentSection == Section.HUB) "×" else "‹"
            textSize = if (currentSection == Section.HUB) 30f else 36f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(dp(12), 0, dp(4), 0)
        }

        header.addView(
            titleBlock,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        header.addView(backOrClose)
        card.addView(header)

        val content = FrameLayout(context)
        card.addView(
            content,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(8)
            }
        )

        lateinit var rebuildCurrentSection: () -> Unit

        fun makeHubCard(
            title: String,
            subtitle: String,
            accent: Int,
            onClick: () -> Unit
        ): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(13), dp(16), dp(13))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(247, 249, 248))
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(2), accent)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }

                addView(
                    TextView(context).apply {
                        text = title
                        textSize = 18f
                        setTextColor(Color.BLACK)
                        setTypeface(
                            typeface,
                            android.graphics.Typeface.BOLD
                        )
                    }
                )

                addView(
                    TextView(context).apply {
                        text = subtitle
                        textSize = 13f
                        setTextColor(Color.rgb(85, 98, 91))
                        setPadding(0, dp(3), 0, 0)
                    }
                )
            }
        }

        fun showHub() {
            currentSection = Section.HUB
            onHubActivated()
            backOrClose.text = "×"
            backOrClose.textSize = 30f
            content.removeAllViews()

            val hub = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            hub.addView(
                TextView(context).apply {
                    text = "SCEGLI IL TIPO DI STAMPA"
                    textSize = 11f
                    setTextColor(Color.rgb(95, 112, 103))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(2), 0, dp(9))
                }
            )

            val shelfCard = makeHubCard(
                title = "SCAFFALE",
                subtitle = "18 etichette prodotto • 72 × 30,5 mm",
                accent = Color.rgb(28, 130, 82)
            ) {
                currentSection = Section.SHELF
                onShelfActivated()
                rebuildCurrentSection()
            }

            val blisterCard = makeHubCard(
                title = "BLISTER",
                subtitle = "Ganci • Grande • Lungo • Big",
                accent = Color.rgb(55, 105, 180)
            ) {
                currentSection = Section.BLISTER
                onBlisterActivated()
                rebuildCurrentSection()
            }

            val offerCard = makeHubCard(
                title = "OFFERTE",
                subtitle = "7 × 10 • 15 × 20 • A4 pieno",
                accent = Color.rgb(230, 180, 0)
            ) {
                currentSection = Section.OFFER

                /*
                 * SOLO SUNMI:
                 * il laser hardware può leggere direttamente mentre la sezione
                 * OFFERTE è aperta, senza passare da volume/dock.
                 *
                 * Impostiamo subito il workflow A4 così il broadcast Honeywell
                 * viene gestito dal receiver A4 dedicato e NON dal receiver HOME
                 * che aprirebbe il popup articolo.
                 *
                 * S24 non entra in questo blocco: comportamento invariato.
                 */
                if (ScannerModeDetector.isSunmi()) {
                    context.applicationContext
                        .getSharedPreferences(
                            "scan_workflow",
                            Context.MODE_PRIVATE
                        )
                        .edit()
                        .putString(
                            "mode",
                            "ETICHETTE_A4"
                        )
                        .apply()
                }

                rebuildCurrentSection()
            }

            hub.addView(
                shelfCard,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(86)
                ).apply {
                    bottomMargin = dp(10)
                }
            )

            hub.addView(
                blisterCard,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(86)
                ).apply {
                    bottomMargin = dp(10)
                }
            )

            hub.addView(
                offerCard,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(86)
                )
            )

            hub.addView(
                TextView(context).apply {
                    text =
                        "Altri report A4 potranno essere aggiunti qui " +
                                "senza modificare i moduli già esistenti."
                    textSize = 12f
                    setTextColor(Color.rgb(105, 115, 110))
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(16), dp(12), 0)
                }
            )

            content.addView(
                hub,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        fun buildShelfPanel(): View {
            val panel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            countText = TextView(context).apply {
                textSize = 16f
                setTextColor(Color.rgb(28, 92, 62))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(2), 0, dp(7))
            }
            panel.addView(countText)

            val searchButton = Button(context).apply {
                text = "⌕  CERCA ARTICOLO"
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setOnClickListener {
                    currentSection = Section.SHELF
                    remove(preserveSection = true)
                    onSearchRequested()
                }
            }
            panel.addView(
                searchButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(50)
                )
            )

            val pdfPreferences = context.getSharedPreferences(
                PDF_PREFS,
                Context.MODE_PRIVATE
            )

            showPrefixCheckBox = CheckBox(context).apply {
                text = "Mostra prefisso codice articolo"
                textSize = 14f
                setTextColor(Color.BLACK)
                isChecked = pdfPreferences.getBoolean(
                    SHOW_PREFIX_KEY,
                    false
                )
                setPadding(dp(2), dp(6), 0, dp(2))
                setOnCheckedChangeListener { _, checked ->
                    pdfPreferences.edit()
                        .putBoolean(SHOW_PREFIX_KEY, checked)
                        .apply()
                    refreshPreview()
                }
            }
            panel.addView(showPrefixCheckBox)

            showPriceCheckBox = CheckBox(context).apply {
                text = "Mostra prezzo"
                textSize = 14f
                setTextColor(Color.BLACK)
                isChecked = pdfPreferences.getBoolean(
                    SHOW_SHELF_PRICE_KEY,
                    true
                )
                setPadding(dp(2), 0, 0, dp(2))
                setOnCheckedChangeListener { _, checked ->
                    pdfPreferences.edit()
                        .putBoolean(
                            SHOW_SHELF_PRICE_KEY,
                            checked
                        )
                        .apply()
                    refreshPreview()
                }
            }
            panel.addView(showPriceCheckBox)

            val previewTitleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(7), 0, dp(5))
            }

            previewTitleRow.addView(
                TextView(context).apply {
                    text = "ANTEPRIMA ULTIMA ETICHETTA"
                    textSize = 11f
                    setTextColor(Color.rgb(95, 112, 103))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            previewTitleRow.addView(
                TextView(context).apply {
                    text = "72 × 30,5 mm"
                    textSize = 10f
                    setTextColor(Color.rgb(130, 145, 137))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            )

            panel.addView(previewTitleRow)

            val previewFrame = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    setStroke(dp(1), Color.BLACK)
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }

            val previewContent = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            val previewTop = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            previewCode = TextView(context).apply {
                textSize = 15f
                setTextColor(Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
            }

            previewDate = TextView(context).apply {
                textSize = 10f
                setTextColor(Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
            }

            previewTop.addView(
                previewCode,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
            previewTop.addView(previewDate)

            previewDescription = TextView(context).apply {
                textSize = 12f
                setTextColor(Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 2
                setPadding(0, dp(2), 0, dp(2))
            }

            previewBarcode = TextView(context).apply {
                textSize = 13f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                maxLines = 1
            }

            previewPrice = TextView(context).apply {
                textSize = 22f
                setTextColor(Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
                maxLines = 1
            }

            previewContent.addView(previewTop)
            previewContent.addView(previewDescription)
            previewContent.addView(previewBarcode)
            previewContent.addView(previewPrice)
            previewFrame.addView(previewContent)

            panel.addView(
                previewFrame,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(142)
                )
            )

            panel.addView(
                TextView(context).apply {
                    text = "PAGINA"
                    textSize = 11f
                    setTextColor(Color.rgb(95, 112, 103))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(9), 0, dp(4))
                }
            )

            val scroll = ScrollView(context).apply {
                isFillViewport = true
            }

            listContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            scroll.addView(listContainer)

            panel.addView(
                scroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )

            val newPageButton = Button(context).apply {
                text = "NUOVA PAGINA"
                textSize = 13f
                setOnClickListener {
                    if (A4LabelStore.getItems().isEmpty()) {
                        Toast.makeText(
                            context,
                            "La pagina è già vuota",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        A4LabelStore.clearPage()
                        Toast.makeText(
                            context,
                            "Nuova pagina pronta",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            panel.addView(
                newPageButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(46)
                ).apply {
                    topMargin = dp(6)
                }
            )

            val printButton = Button(context).apply {
                text = "ANTEPRIMA / STAMPA"
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)

                setOnClickListener {
                    val items = A4LabelStore.getItems()

                    if (items.isEmpty()) {
                        Toast.makeText(
                            context,
                            "Inserire almeno un articolo",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    val showArticlePrefix =
                        showPrefixCheckBox?.isChecked ?: false
                    val showPrice =
                        showPriceCheckBox?.isChecked ?: true

                    isEnabled = false
                    remove()

                    Thread {
                        val result = A4PdfGenerator.generateAndOpen(
                            context = context,
                            items = items,
                            showArticlePrefix = showArticlePrefix,
                            showPrice = showPrice
                        )

                        post {
                            result.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    "Errore PDF: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }.start()
                }
            }

            panel.addView(
                printButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(58)
                ).apply {
                    topMargin = dp(7)
                }
            )

            refreshShelf()
            return panel
        }

        fun buildOfferPanel(): View {
            val offerPreferences =
                context.getSharedPreferences(
                    OFFER_PREFS,
                    Context.MODE_PRIVATE
                )

            val panel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            panel.addView(
                TextView(context).apply {
                    text = "OFFERTE"
                    textSize = 20f
                    setTextColor(Color.BLACK)
                    setTypeface(
                        typeface,
                        android.graphics.Typeface.BOLD
                    )
                }
            )

            panel.addView(
                TextView(context).apply {
                    text =
                        "Scegli il formato, acquisisci l'articolo " +
                                "e imposta il prezzo promozionale."
                    textSize = 13f
                    setTextColor(Color.DKGRAY)
                    setPadding(0, dp(3), 0, dp(8))
                }
            )

            val acquireButton = Button(context).apply {
                text = "⌕  CERCA / ACQUISISCI ARTICOLO"
                textSize = 14f
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
                setOnClickListener {
                    currentSection = Section.OFFER
                    selectedOfferItem = null
                    remove(preserveSection = true)
                    onSearchRequested()
                }
            }

            panel.addView(
                acquireButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(50)
                )
            )

            val selectedItem =
                selectedOfferItem
                    ?: A4LabelStore.getItems().lastOrNull()

            val articleCard =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        dp(12),
                        dp(10),
                        dp(12),
                        dp(10)
                    )
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(247, 249, 248))
                        cornerRadius = dp(12).toFloat()
                        setStroke(
                            dp(1),
                            Color.rgb(215, 220, 217)
                        )
                    }
                }

            articleCard.addView(
                TextView(context).apply {
                    text =
                        selectedItem?.articleCode
                            ?.ifBlank { "NESSUN ARTICOLO" }
                            ?: "NESSUN ARTICOLO"
                    textSize = 15f
                    setTextColor(Color.BLACK)
                    setTypeface(
                        typeface,
                        android.graphics.Typeface.BOLD
                    )
                }
            )

            articleCard.addView(
                TextView(context).apply {
                    text =
                        selectedItem?.description
                            ?.ifBlank {
                                "Usa CERCA / ACQUISISCI ARTICOLO"
                            }
                            ?: "Usa CERCA / ACQUISISCI ARTICOLO"
                    textSize = 13f
                    setTextColor(Color.DKGRAY)
                    setPadding(0, dp(2), 0, 0)
                }
            )

            panel.addView(
                articleCard,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            )

            panel.addView(
                TextView(context).apply {
                    text = "FORMATO"
                    textSize = 11f
                    setTextColor(Color.rgb(95, 112, 103))
                    setTypeface(
                        typeface,
                        android.graphics.Typeface.BOLD
                    )
                    setPadding(0, dp(10), 0, dp(4))
                }
            )

            val formatGroup = RadioGroup(context).apply {
                orientation = RadioGroup.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val savedFormat =
                offerPreferences.getString(
                    OFFER_FORMAT_KEY,
                    OfferFormat.SMALL_7X10.name
                ) ?: OfferFormat.SMALL_7X10.name

            fun addFormatChoice(
                label: String,
                format: OfferFormat
            ) {
                formatGroup.addView(
                    RadioButton(context).apply {
                        id = View.generateViewId()
                        text = label
                        textSize = 14f
                        tag = format.name
                        isChecked = savedFormat == format.name
                    },
                    RadioGroup.LayoutParams(
                        0,
                        RadioGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
            }

            addFormatChoice(
                "7×10",
                OfferFormat.SMALL_7X10
            )
            addFormatChoice(
                "15×20",
                OfferFormat.MEDIUM_15X20
            )
            addFormatChoice(
                "A4",
                OfferFormat.A4_FULL
            )

            /*
             * Salva subito il formato scelto.
             * Il popup OFFERTE viene temporaneamente rimosso durante una scansione;
             * senza questo salvataggio immediato, al ritorno veniva ripristinato
             * l'ultimo formato usato in una stampa precedente.
             */
            formatGroup.setOnCheckedChangeListener { group, checkedId ->
                val checkedButton =
                    group.findViewById<RadioButton>(checkedId)

                val selectedFormat =
                    checkedButton?.tag
                        ?.toString()
                        .orEmpty()

                if (selectedFormat.isNotBlank()) {
                    offerPreferences
                        .edit()
                        .putString(
                            OFFER_FORMAT_KEY,
                            selectedFormat
                        )
                        .apply()
                }
            }

            panel.addView(formatGroup)

            panel.addView(
                TextView(context).apply {
                    text = "PREZZO OFFERTO"
                    textSize = 11f
                    setTextColor(Color.rgb(95, 112, 103))
                    setTypeface(
                        typeface,
                        android.graphics.Typeface.BOLD
                    )
                    setPadding(0, dp(8), 0, dp(3))
                }
            )

            val offerPriceInput =
                EditText(context).apply {
                    textSize = 24f
                    setTextColor(Color.BLACK)
                    gravity = Gravity.CENTER
                    hint = "0,00"
                    inputType =
                        android.text.InputType.TYPE_CLASS_NUMBER or
                                android.text.InputType
                                    .TYPE_NUMBER_FLAG_DECIMAL

                    setText(
                        selectedItem?.publicPrice
                            ?.replace("€", "")
                            ?.trim()
                            .orEmpty()
                    )
                }

            panel.addView(
                offerPriceInput,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(58)
                )
            )

            val oldPriceCheck =
                CheckBox(context).apply {
                    text = "Mostra prezzo precedente barrato"
                    textSize = 14f
                    setTextColor(Color.BLACK)
                    isChecked =
                        offerPreferences.getBoolean(
                            OFFER_SHOW_OLD_PRICE_KEY,
                            true
                        )
                }

            val barcodeCheck =
                CheckBox(context).apply {
                    text = "Mostra barcode grafico + numerico"
                    textSize = 14f
                    setTextColor(Color.BLACK)
                    isChecked =
                        offerPreferences.getBoolean(
                            OFFER_SHOW_BARCODE_KEY,
                            true
                        )
                }

            val imageCheck =
                CheckBox(context).apply {
                    text = "Mostra immagine articolo"
                    textSize = 14f
                    setTextColor(Color.BLACK)
                    isChecked =
                        offerPreferences.getBoolean(
                            OFFER_SHOW_IMAGE_KEY,
                            true
                        )
                }

            val prefixCheck =
                CheckBox(context).apply {
                    text = "Mostra prefisso codice articolo"
                    textSize = 14f
                    setTextColor(Color.BLACK)
                    isChecked =
                        offerPreferences.getBoolean(
                            OFFER_SHOW_PREFIX_KEY,
                            true
                        )
                }

            panel.addView(oldPriceCheck)
            panel.addView(barcodeCheck)
            panel.addView(imageCheck)
            panel.addView(prefixCheck)

            val preview =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(10),
                        dp(8),
                        dp(10),
                        dp(8)
                    )
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        setStroke(dp(2), Color.BLACK)
                        cornerRadius = dp(8).toFloat()
                    }

                    addView(
                        TextView(context).apply {
                            text = "OFFERTA"
                            textSize = 18f
                            gravity = Gravity.CENTER
                            setTextColor(Color.WHITE)
                            setBackgroundColor(Color.BLACK)
                            setTypeface(
                                typeface,
                                android.graphics.Typeface.BOLD
                            )
                            setPadding(
                                dp(8),
                                dp(5),
                                dp(8),
                                dp(5)
                            )
                        },
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    )

                    addView(
                        TextView(context).apply {
                            text =
                                selectedItem?.description
                                    ?: "DESCRIZIONE ARTICOLO"
                            textSize = 14f
                            gravity = Gravity.CENTER
                            setTextColor(Color.BLACK)
                            setTypeface(
                                typeface,
                                android.graphics.Typeface.BOLD
                            )
                            maxLines = 2
                            setPadding(
                                dp(4),
                                dp(7),
                                dp(4),
                                dp(4)
                            )
                        }
                    )

                    addView(
                        TextView(context).apply {
                            text =
                                selectedItem?.publicPrice
                                    ?.let { formatPrice(it) }
                                    ?.ifBlank { "€ 0,00" }
                                    ?: "€ 0,00"
                            textSize = 30f
                            gravity = Gravity.CENTER
                            setTextColor(Color.BLACK)
                            setTypeface(
                                typeface,
                                android.graphics.Typeface.BOLD
                            )
                        }
                    )

                    addView(
                        TextView(context).apply {
                            text =
                                selectedItem?.articleCode
                                    ?: "CODICE"
                            textSize = 11f
                            gravity = Gravity.CENTER
                            setTextColor(Color.DKGRAY)
                            setPadding(0, dp(3), 0, 0)
                        }
                    )
                }

            panel.addView(
                preview,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(150)
                ).apply {
                    topMargin = dp(8)
                }
            )

            val printButton =
                Button(context).apply {
                    text = "ANTEPRIMA / STAMPA"
                    textSize = 16f
                    setTypeface(
                        typeface,
                        android.graphics.Typeface.BOLD
                    )

                    setOnClickListener {
                        val item =
                            selectedOfferItem
                                ?: A4LabelStore
                                    .getItems()
                                    .lastOrNull()

                        if (item == null) {
                            Toast.makeText(
                                context,
                                "Seleziona prima un articolo",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }

                        val price =
                            offerPriceInput.text
                                .toString()
                                .trim()

                        val numericPrice =
                            price
                                .replace("€", "")
                                .replace(" ", "")
                                .replace(",", ".")
                                .toDoubleOrNull()

                        if (numericPrice == null) {
                            Toast.makeText(
                                context,
                                "Inserisci un prezzo offerta valido",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }

                        val checked =
                            formatGroup.findViewById<RadioButton>(
                                formatGroup.checkedRadioButtonId
                            )

                        val format =
                            runCatching {
                                OfferFormat.valueOf(
                                    checked?.tag
                                        ?.toString()
                                        ?: OfferFormat
                                            .SMALL_7X10
                                            .name
                                )
                            }.getOrDefault(
                                OfferFormat.SMALL_7X10
                            )

                        val showOldPrice =
                            oldPriceCheck.isChecked
                        val showBarcode =
                            barcodeCheck.isChecked
                        val showImage =
                            imageCheck.isChecked
                        val showArticlePrefix =
                            prefixCheck.isChecked

                        offerPreferences.edit()
                            .putString(
                                OFFER_FORMAT_KEY,
                                format.name
                            )
                            .putBoolean(
                                OFFER_SHOW_OLD_PRICE_KEY,
                                showOldPrice
                            )
                            .putBoolean(
                                OFFER_SHOW_BARCODE_KEY,
                                showBarcode
                            )
                            .putBoolean(
                                OFFER_SHOW_IMAGE_KEY,
                                showImage
                            )
                            .putBoolean(
                                OFFER_SHOW_PREFIX_KEY,
                                showArticlePrefix
                            )
                            .apply()

                        isEnabled = false
                        remove()

                        Thread {
                            val result =
                                OfferPdfGenerator
                                    .generateAndOpen(
                                        context = context,
                                        item = item,
                                        format = format,
                                        offerPrice = price,
                                        showOldPrice =
                                            showOldPrice,
                                        showBarcode =
                                            showBarcode,
                                        showImage =
                                            showImage,
                                        showArticlePrefix =
                                            showArticlePrefix
                                    )

                            post {
                                result.onFailure { error ->
                                    Toast.makeText(
                                        context,
                                        "Errore PDF offerta: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }.start()
                    }
                }

            panel.addView(
                printButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(58)
                ).apply {
                    topMargin = dp(8)
                }
            )

            return panel
        }

        fun buildBlisterPanel(): View {
            val packagingPreferences = context.getSharedPreferences(
                PACKAGING_PREFS,
                Context.MODE_PRIVATE
            )

            val panel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            panel.addView(
                TextView(context).apply {
                    text = "BLISTER"
                    textSize = 20f
                    setTextColor(Color.BLACK)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            )

            panel.addView(
                TextView(context).apply {
                    text =
                        "Scegli il formato. L'acquisizione usa il lettore " +
                                "hardware come negli altri moduli."
                    textSize = 13f
                    setTextColor(Color.DKGRAY)
                    setPadding(0, dp(4), 0, dp(9))
                }
            )

            val selectedType = packagingPreferences.getString(
                PACKAGING_TYPE_KEY,
                TYPE_BLISTER_LARGE
            ) ?: TYPE_BLISTER_LARGE

            val radioGroup = RadioGroup(context).apply {
                orientation = RadioGroup.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.rgb(245, 247, 249))
                    cornerRadius = dp(12).toFloat()
                }
                setPadding(dp(8), dp(5), dp(8), dp(5))
            }

            fun addChoice(label: String, type: String) {
                radioGroup.addView(
                    RadioButton(context).apply {
                        id = View.generateViewId()
                        text = label
                        textSize = 16f
                        setTextColor(Color.BLACK)
                        tag = type
                        isChecked = selectedType == type
                        setPadding(dp(4), dp(6), dp(4), dp(6))
                        setOnCheckedChangeListener { _, checked ->
                            if (checked) {
                                packagingPreferences.edit()
                                    .putString(
                                        PACKAGING_TYPE_KEY,
                                        type
                                    )
                                    .apply()
                            }
                        }
                    }
                )
            }

            addChoice(
                "Blister grande · 141 × 55 mm",
                TYPE_BLISTER_LARGE
            )
            addChoice(
                "Blister lungo · 192 × 68 mm",
                TYPE_BLISTER_LONG
            )
            addChoice(
                "Blister big · 268 × 116 mm",
                TYPE_BLISTER_BIG
            )

            panel.addView(radioGroup)

            fun createSwitchRow(
                label: String,
                initialValue: Boolean
            ): Pair<LinearLayout, Switch> {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                }

                val switch = Switch(context).apply {
                    isChecked = initialValue
                }

                val description = TextView(context).apply {
                    text = label
                    textSize = 16f
                    setTextColor(Color.BLACK)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(dp(10), 0, 0, 0)
                    setOnClickListener {
                        switch.isChecked = !switch.isChecked
                    }
                }

                row.addView(switch)
                row.addView(
                    description,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )

                return row to switch
            }

            val (hookRow, hookSwitch) = createSwitchRow(
                "Aggiungi anche etichetta ganci",
                packagingPreferences.getBoolean(
                    INCLUDE_HOOK_KEY,
                    false
                )
            )

            hookSwitch.setOnCheckedChangeListener { _, checked ->
                packagingPreferences.edit()
                    .putBoolean(
                        INCLUDE_HOOK_KEY,
                        checked
                    )
                    .apply()
            }

            val (priceRow, priceSwitch) = createSwitchRow(
                "Mostra prezzo",
                packagingPreferences.getBoolean(
                    SHOW_PRICE_KEY,
                    false
                )
            )

            priceSwitch.setOnCheckedChangeListener { _, checked ->
                packagingPreferences.edit()
                    .putBoolean(
                        SHOW_PRICE_KEY,
                        checked
                    )
                    .apply()
            }

            panel.addView(hookRow)
            panel.addView(priceRow)

            panel.addView(
                TextView(context).apply {
                    text =
                        "Taglio: linea nera continua\n" +
                                "Pieghe: linee grigie tratteggiate\n" +
                                "Immagine prodotto e barcode sempre presenti"
                    textSize = 13f
                    setTextColor(Color.rgb(70, 70, 70))
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(245, 245, 245))
                        cornerRadius = dp(10).toFloat()
                    }
                }
            )

            fun currentBlisterSelection(): Triple<String, Boolean, Boolean> {
                val checked =
                    radioGroup.findViewById<RadioButton>(
                        radioGroup.checkedRadioButtonId
                    )

                val type =
                    checked?.tag?.toString()
                        ?: TYPE_BLISTER_LARGE

                val includeHook = hookSwitch.isChecked
                val showPrice = priceSwitch.isChecked

                packagingPreferences.edit()
                    .putString(PACKAGING_TYPE_KEY, type)
                    .putBoolean(INCLUDE_HOOK_KEY, includeHook)
                    .putBoolean(SHOW_PRICE_KEY, showPrice)
                    .apply()

                return Triple(
                    type,
                    includeHook,
                    showPrice
                )
            }

            val searchButton = Button(context).apply {
                text = "⌕  CERCA ARTICOLO"
                textSize = 14f
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
                setOnClickListener {
                    val callback = onBlisterSearchRequested

                    if (callback == null) {
                        Toast.makeText(
                            context,
                            "Ricerca blister non disponibile",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    val (type, includeHook, showPrice) =
                        currentBlisterSelection()

                    currentSection = Section.BLISTER
                    remove(preserveSection = true)

                    callback(
                        type,
                        includeHook,
                        showPrice
                    )
                }
            }

            panel.addView(
                searchButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(50)
                ).apply {
                    topMargin = dp(10)
                    bottomMargin = dp(4)
                }
            )

            fun saveAndScan() {
                val (type, includeHook, showPrice) =
                    currentBlisterSelection()

                if (onBlisterScanRequested == null) {
                    Toast.makeText(
                        context,
                        "Gestione blister non disponibile",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                currentSection = Section.BLISTER
                remove(preserveSection = true)
                onBlisterScanRequested(
                    type,
                    includeHook,
                    showPrice
                )
            }

            panel.addView(
                TextView(context).apply {
                    text =
                        "S24: usa Volume o dock SCAN\n" +
                                "Sunmi: usa il grilletto laser"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(60, 85, 72))
                    setPadding(dp(8), dp(18), dp(8), dp(8))
                    setTypeface(
                        typeface,
                        android.graphics.Typeface.BOLD
                    )
                }
            )

            /*
             * Salviamo la funzione di scansione nell'overlay tramite tag,
             * così il listener hardware comune può richiamarla quando siamo
             * nella sezione Blister.
             */
            panel.tag = { saveAndScan() }

            return panel
        }

        rebuildCurrentSection = {
            content.removeAllViews()

            when (currentSection) {
                Section.HUB -> showHub()

                Section.SHELF -> {
                    backOrClose.text = "‹"
                    backOrClose.textSize = 36f
                    content.addView(
                        buildShelfPanel(),
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }

                Section.BLISTER -> {
                    backOrClose.text = "‹"
                    backOrClose.textSize = 36f
                    content.addView(
                        buildBlisterPanel(),
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }

                Section.OFFER -> {
                    backOrClose.text = "‹"
                    backOrClose.textSize = 36f
                    content.addView(
                        buildOfferPanel(),
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        }

        backOrClose.setOnClickListener {
            if (currentSection == Section.HUB) {
                currentSection = Section.HUB
                remove()
                onClosed()
            } else {
                showHub()
            }
        }

        val hardwareKeyListener = View.OnKeyListener { _, keyCode, event ->
            val isVolumeTrigger =
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                        keyCode == KeyEvent.KEYCODE_VOLUME_UP

            when {
                keyCode == KeyEvent.KEYCODE_BACK -> {
                    if (
                        event.action == KeyEvent.ACTION_DOWN &&
                        event.repeatCount == 0
                    ) {
                        if (currentSection == Section.HUB) {
                            currentSection = Section.HUB
                            remove()
                            onClosed()
                        } else {
                            showHub()
                        }
                    }
                    true
                }

                isVolumeTrigger -> {
                    if (
                        event.action == KeyEvent.ACTION_DOWN &&
                        event.repeatCount == 0
                    ) {
                        when (currentSection) {
                            Section.SHELF -> {
                                remove(preserveSection = true)
                                onHardwareScanRequested()
                            }

                            Section.BLISTER -> {
                                val panel =
                                    content.getChildAt(0) as? LinearLayout

                                @Suppress("UNCHECKED_CAST")
                                val scanAction =
                                    panel?.tag as? (() -> Unit)

                                scanAction?.invoke()
                            }

                            Section.OFFER -> {
                                currentSection = Section.OFFER
                                remove(preserveSection = true)
                                onOfferScanRequested()
                            }

                            Section.HUB -> Unit
                        }
                    }
                    true
                }

                else -> false
            }
        }

        overlay.setOnKeyListener(hardwareKeyListener)
        overlay.requestFocus()

        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                (screenWidth - dp(28)).coerceAtMost(dp(520)),
                (screenHeight - dp(70)).coerceAtMost(dp(850))
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        root = overlay

        windowManager.addView(
            overlay,
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        )

        rebuildCurrentSection()
    }

    fun remove(
        preserveSection: Boolean = false
    ) {
        val current = root ?: return

        runCatching {
            windowManager.removeView(current)
        }

        root = null
        listContainer = null
        countText = null
        previewCode = null
        previewDescription = null
        previewBarcode = null
        previewPrice = null
        previewDate = null
        showPrefixCheckBox = null
        showPriceCheckBox = null

        A4LabelStore.removeListener(storeListener)

        if (!preserveSection) {
            currentSection = Section.HUB
        }
    }

    private fun refreshShelf() {
        val container = listContainer ?: return
        val items = A4LabelStore.getItems()

        val pageCapacity = A4LabelStore.PAGE_CAPACITY
        val pageCount =
            if (items.isEmpty()) {
                1
            } else {
                ((items.size - 1) / pageCapacity) + 1
            }

        val itemsOnLastPage =
            if (items.isEmpty()) {
                0
            } else {
                ((items.size - 1) % pageCapacity) + 1
            }

        countText?.text =
            "Pagina $pageCount  •  $itemsOnLastPage/$pageCapacity etichette" +
                    if (pageCount > 1) {
                        "  •  Totale ${items.size}"
                    } else {
                        ""
                    }

        container.removeAllViews()

        if (items.isEmpty()) {
            container.addView(
                TextView(context).apply {
                    text =
                        "Nessun articolo inserito.\n" +
                                "Usa il lettore oppure CERCA ARTICOLO."
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTextColor(Color.DKGRAY)
                    setPadding(dp(12), dp(18), dp(12), dp(18))
                }
            )
        } else {
            items.forEachIndexed { index, item ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(7), dp(6), dp(7))
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(244, 247, 245))
                        cornerRadius = dp(10).toFloat()
                    }
                }

                val text = TextView(context).apply {
                    this.text =
                        "${index + 1}. ${displayCode(item.articleCode)}\n" +
                                item.description +
                                formatPrice(item.publicPrice)
                                    .takeIf { it.isNotBlank() }
                                    ?.let { "\n$it" }
                                    .orEmpty()
                    textSize = 14f
                    setTextColor(Color.BLACK)
                }

                val remove = Button(context).apply {
                    this.text = "×"
                    textSize = 19f
                    setOnClickListener {
                        A4LabelStore.remove(item)
                    }
                }

                row.addView(
                    text,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
                row.addView(
                    remove,
                    LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                    )
                )

                container.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(6)
                    }
                )
            }
        }

        refreshPreview()
    }

    private fun refreshPreview() {
        val item = A4LabelStore.getItems().lastOrNull()

        if (item == null) {
            previewCode?.text = "SELEZIONA UN ARTICOLO"
            previewDescription?.text =
                "L'ultima etichetta inserita verrà mostrata qui"
            previewBarcode?.text = "|||| ||||| ||||"
            previewPrice?.visibility =
                if (showPriceCheckBox?.isChecked != false) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            previewPrice?.text = ""
            previewDate?.text = currentMonthYear()
            return
        }

        previewCode?.text = displayCode(item.articleCode)
        previewDescription?.text =
            item.description.ifBlank { "Descrizione articolo" }
        previewBarcode?.text =
            if (item.barcode.isBlank()) {
                "|||| ||||| ||||"
            } else {
                "|||| ||||| ||||   ${item.barcode}"
            }
        val showPrice =
            showPriceCheckBox?.isChecked ?: true

        previewPrice?.visibility =
            if (showPrice) View.VISIBLE else View.GONE
        previewPrice?.text =
            if (showPrice) formatPrice(item.publicPrice) else ""

        previewDate?.text = currentMonthYear()
    }

    private fun displayCode(code: String): String {
        val clean = code.trim()
        val showPrefix = showPrefixCheckBox?.isChecked ?: false

        return if (showPrefix || clean.length <= 3) {
            clean
        } else {
            clean.drop(3)
        }
    }

    private fun formatPrice(raw: String): String {
        val numeric =
            raw.trim()
                .replace("€", "")
                .replace(" ", "")
                .replace(",", ".")
                .toDoubleOrNull()
                ?: return raw.trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { "€ $it" }
                    .orEmpty()

        return String.format(
            Locale.ITALY,
            "€ %.2f",
            numeric
        )
    }

    private fun currentMonthYear(): String {
        return SimpleDateFormat(
            "MMM yyyy",
            Locale.ITALY
        ).format(Date())
            .uppercase(Locale.ITALY)
            .replace(".", "")
    }

    private fun dp(value: Int): Int {
        return (
                value *
                        context.resources.displayMetrics.density
                ).toInt()
    }
}