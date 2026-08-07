package com.scan2enter.labels.a4

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class A4LabelsPopup(
    private val context: Context,
    private val windowManager: WindowManager
) {

    private companion object {
        const val PDF_PREFS = "a4_pdf_preferences"
        const val SHOW_PREFIX_KEY = "show_article_prefix"

        const val PACKAGING_PREFS = "a4_packaging_preferences"
        const val PACKAGING_TYPE_KEY = "packaging_type"
        const val INCLUDE_HOOK_KEY = "include_hook_label"
        const val SHOW_PRICE_KEY = "show_blister_price"

        const val TYPE_BLISTER_LARGE = "BLISTER_LARGE"
        const val TYPE_BLISTER_LONG = "BLISTER_LONG"
        const val TYPE_BLISTER_BIG = "BLISTER_BIG"
    }

    private var root: View? = null
    private var listContainer: LinearLayout? = null
    private var countText: TextView? = null

    private val storeListener: () -> Unit = {
        root?.post { refresh() }
    }

    fun show(
        onScanRequested: () -> Unit,
        onBlisterScanRequested: ((String, Boolean, Boolean) -> Unit)? = null,
        onClosed: () -> Unit
    ) {
        if (root != null) {
            refresh()
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
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 22 * density
            }
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(context).apply {
            text = "ETICHETTE A4"
            textSize = 21f
            setTextColor(Color.BLACK)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val close = TextView(context).apply {
            text = "×"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(dp(14), dp(2), dp(4), dp(2))
            setOnClickListener {
                remove()
                onClosed()
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
        header.addView(close)
        card.addView(header)


        val tabs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val shelfTab = Button(context).apply {
            text = "SCAFFALE"
            textSize = 14f
        }

        val blisterTab = Button(context).apply {
            text = "BLISTER"
            textSize = 14f
        }

        tabs.addView(
            shelfTab,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(5)
            }
        )
        tabs.addView(
            blisterTab,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(5)
            }
        )

        card.addView(
            tabs,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(8)
            }
        )

        val contentFrame = FrameLayout(context)

        card.addView(
            contentFrame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val shelfPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        countText = TextView(context).apply {
            textSize = 18f
            setTextColor(Color.rgb(40, 70, 50))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(8))
        }
        shelfPanel.addView(countText)

        val pdfPreferences = context.getSharedPreferences(
            PDF_PREFS,
            Context.MODE_PRIVATE
        )

        val showPrefixCheckBox = CheckBox(context).apply {
            text = "Mostra prefisso codice articolo"
            textSize = 15f
            setTextColor(Color.BLACK)
            isChecked = pdfPreferences.getBoolean(
                SHOW_PREFIX_KEY,
                false
            )
            setPadding(0, 0, 0, dp(6))
            setOnCheckedChangeListener { _, checked ->
                pdfPreferences.edit()
                    .putBoolean(SHOW_PREFIX_KEY, checked)
                    .apply()
            }
        }
        shelfPanel.addView(showPrefixCheckBox)

        val scroll = ScrollView(context).apply {
            isFillViewport = true
        }

        listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(
            listContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        shelfPanel.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val scanButton = Button(context).apply {
            text = "🔫 SCANSIONA ARTICOLO"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setOnClickListener {
                remove(keepListener = false)
                onScanRequested()
            }
        }

        shelfPanel.addView(
            scanButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                topMargin = dp(10)
            }
        )

        val shelfActions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val clearButton = Button(context).apply {
            text = "NUOVA PAGINA"
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
                        "Pagina svuotata",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val printButton = Button(context).apply {
            text = "ANTEPRIMA / STAMPA"
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

                isEnabled = false

                Thread {
                    val result = A4PdfGenerator.generateAndOpen(
                        context = context,
                        items = items,
                        showArticlePrefix = showPrefixCheckBox.isChecked
                    )

                    post {
                        isEnabled = true

                        result.onSuccess {
                            Toast.makeText(
                                context,
                                "PDF creato nei Download",
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure { error ->
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

        shelfActions.addView(
            clearButton,
            LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                marginEnd = dp(5)
            }
        )

        shelfActions.addView(
            printButton,
            LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                marginStart = dp(5)
            }
        )

        shelfPanel.addView(
            shelfActions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        val blisterPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        blisterPanel.addView(
            TextView(context).apply {
                text = "ETICHETTE BLISTER"
                textSize = 20f
                setTextColor(Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        )

        blisterPanel.addView(
            TextView(context).apply {
                text = "Scegli un solo formato blister."
                textSize = 15f
                setTextColor(Color.DKGRAY)
                setPadding(0, dp(5), 0, dp(10))
            }
        )

        val packagingPreferences = context.getSharedPreferences(
            PACKAGING_PREFS,
            Context.MODE_PRIVATE
        )

        val selectedType = packagingPreferences.getString(
            PACKAGING_TYPE_KEY,
            TYPE_BLISTER_LARGE
        ) ?: TYPE_BLISTER_LARGE

        val radioGroup = android.widget.RadioGroup(context).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }

        fun addBlisterChoice(
            label: String,
            type: String
        ) {
            radioGroup.addView(
                android.widget.RadioButton(context).apply {
                    id = View.generateViewId()
                    text = label
                    textSize = 17f
                    setTextColor(Color.BLACK)
                    tag = type
                    isChecked = selectedType == type
                    setPadding(dp(4), dp(7), dp(4), dp(7))
                }
            )
        }

        addBlisterChoice(
            "Blister grande · 141 × 55 mm",
            TYPE_BLISTER_LARGE
        )
        addBlisterChoice(
            "Blister lungo · 192 × 68 mm",
            TYPE_BLISTER_LONG
        )
        addBlisterChoice(
            "Blister big · 268 × 116 mm",
            TYPE_BLISTER_BIG
        )

        blisterPanel.addView(radioGroup)

        fun createSwitchRow(
            label: String,
            initialValue: Boolean,
            onChanged: (Boolean) -> Unit
        ): Pair<LinearLayout, Switch> {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }

            val switch = Switch(context).apply {
                text = ""
                isChecked = initialValue
                setOnCheckedChangeListener { _, checked ->
                    onChanged(checked)
                }
            }

            val description = TextView(context).apply {
                text = label
                textSize = 17f
                setTextColor(Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(10), 0, 0, 0)
                setOnClickListener {
                    switch.isChecked = !switch.isChecked
                }
            }

            row.addView(
                switch,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
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

        val (hookRow, includeHookSwitch) = createSwitchRow(
            label = "Aggiungi anche etichetta ganci",
            initialValue = packagingPreferences.getBoolean(
                INCLUDE_HOOK_KEY,
                false
            )
        ) { checked ->
            packagingPreferences.edit()
                .putBoolean(INCLUDE_HOOK_KEY, checked)
                .apply()
        }

        blisterPanel.addView(hookRow)

        val (priceRow, showPriceSwitch) = createSwitchRow(
            label = "Mostra prezzo",
            initialValue = packagingPreferences.getBoolean(
                SHOW_PRICE_KEY,
                false
            )
        ) { checked ->
            packagingPreferences.edit()
                .putBoolean(SHOW_PRICE_KEY, checked)
                .apply()
        }

        blisterPanel.addView(priceRow)

        blisterPanel.addView(
            TextView(context).apply {
                text =
                    "Taglio: linea nera continua\n" +
                            "Pieghe: linee grigie tratteggiate\n" +
                            "Immagine prodotto e barcode sempre presenti"
                textSize = 14f
                setTextColor(Color.rgb(70, 70, 70))
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(245, 245, 245))
                    cornerRadius = dp(10).toFloat()
                }
            }
        )

        blisterPanel.addView(
            Button(context).apply {
                text = "🔫 SCANSIONA ARTICOLO"
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setOnClickListener {
                    val checked = radioGroup.findViewById<android.widget.RadioButton>(
                        radioGroup.checkedRadioButtonId
                    )

                    val type = checked?.tag?.toString()
                        ?: TYPE_BLISTER_LARGE

                    val includeHook = includeHookSwitch.isChecked
                    val showPrice = showPriceSwitch.isChecked

                    packagingPreferences.edit()
                        .putString(PACKAGING_TYPE_KEY, type)
                        .putBoolean(INCLUDE_HOOK_KEY, includeHook)
                        .putBoolean(SHOW_PRICE_KEY, showPrice)
                        .apply()

                    if (onBlisterScanRequested == null) {
                        Toast.makeText(
                            context,
                            "Opzioni blister salvate",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        remove(keepListener = false)
                        onBlisterScanRequested(
                            type,
                            includeHook,
                            showPrice
                        )
                    }
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            ).apply {
                topMargin = dp(12)
            }
        )

        contentFrame.addView(
            shelfPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        contentFrame.addView(
            blisterPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        fun styleTab(
            button: Button,
            selected: Boolean
        ) {
            button.setTextColor(
                if (selected) Color.WHITE else Color.rgb(35, 55, 65)
            )

            button.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(
                    if (selected) {
                        Color.rgb(28, 92, 117)
                    } else {
                        Color.rgb(225, 235, 239)
                    }
                )
            }
        }

        fun showShelf() {
            shelfPanel.visibility = View.VISIBLE
            blisterPanel.visibility = View.GONE
            styleTab(shelfTab, true)
            styleTab(blisterTab, false)
            refresh()
        }

        fun showBlister() {
            shelfPanel.visibility = View.GONE
            blisterPanel.visibility = View.VISIBLE
            styleTab(shelfTab, false)
            styleTab(blisterTab, true)
        }

        shelfTab.setOnClickListener { showShelf() }
        blisterTab.setOnClickListener { showBlister() }

        showShelf()

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
            )
        )

        refresh()
    }

    fun remove(keepListener: Boolean = false) {
        val current = root ?: return

        runCatching {
            windowManager.removeView(current)
        }

        root = null
        listContainer = null
        countText = null

        if (!keepListener) {
            A4LabelStore.removeListener(storeListener)
        }
    }

    private fun refresh() {
        val container = listContainer ?: return
        val items = A4LabelStore.getItems()

        countText?.text =
            "Pagina 1 · ${items.size}/${A4LabelStore.PAGE_CAPACITY}"

        container.removeAllViews()

        if (items.isEmpty()) {
            container.addView(
                TextView(context).apply {
                    text = "Nessun articolo inserito.\nPremi SCANSIONA ARTICOLO."
                    textSize = 17f
                    gravity = Gravity.CENTER
                    setTextColor(Color.DKGRAY)
                    setPadding(dp(12), dp(35), dp(12), dp(35))
                }
            )
            return
        }

        items.forEachIndexed { index, item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(9), dp(6), dp(9))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(244, 247, 245))
                    cornerRadius = dp(10).toFloat()
                }
            }

            val text = TextView(context).apply {
                this.text =
                    "${index + 1}. ${item.articleCode}\n" +
                            item.description +
                            item.publicPrice
                                .takeIf { it.isNotBlank() }
                                ?.let { "\n€ $it" }
                                .orEmpty()
                textSize = 15f
                setTextColor(Color.BLACK)
            }

            val remove = Button(context).apply {
                this.text = "×"
                textSize = 20f
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
                    dp(48),
                    dp(48)
                )
            )

            container.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(7)
                }
            )
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}