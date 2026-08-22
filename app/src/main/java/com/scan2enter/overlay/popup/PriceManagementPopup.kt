package com.scan2enter.overlay.popup

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.api.ProductPriceListDto
import com.scan2enter.model.ProductInfo
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.min

/**
 * Gestione rapida dei prezzi listino di un articolo.
 *
 * - Tap sul prezzo: arrotonda ai 10 centesimi più vicini (5 cent -> su).
 * - Pressione lunga: seleziona tutto e apre subito la tastiera decimale.
 * - SALVA: valida tutto e scrive soltanto i listini realmente modificati.
 */
class PriceManagementPopup(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private val gatewayApiClient = GatewayApiClient()

    private var root: View? = null
    private var currentProduct: ProductInfo? = null
    private var rowsContainer: LinearLayout? = null
    private var statusText: TextView? = null
    private var saveButton: Button? = null

    private val priceFields =
        linkedMapOf<Int, EditText>()

    private val originalPrices =
        linkedMapOf<Int, BigDecimal>()

    private val markupViews =
        linkedMapOf<Int, EditText>()

    private val purchaseTaxables =
        linkedMapOf<Int, BigDecimal?>()

    private var onSavedCallback:
            ((List<ProductPriceListDto>) -> Unit)? = null

    private var onClosedCallback:
            (() -> Unit)? = null

    fun isShowing(): Boolean = root != null

    fun show(
        product: ProductInfo,
        onSaved: (List<ProductPriceListDto>) -> Unit,
        onClosed: () -> Unit
    ) {
        remove(notifyClosed = false)

        currentProduct = product
        onSavedCallback = onSaved
        onClosedCallback = onClosed

        val density =
            context.resources.displayMetrics.density

        val screenWidth =
            context.resources.displayMetrics.widthPixels

        val screenHeight =
            context.resources.displayMetrics.heightPixels

        val overlay =
            FrameLayout(context).apply {
                setBackgroundColor(
                    Color.argb(115, 0, 0, 0)
                )
                isClickable = true
                setOnClickListener {
                    remove()
                }
            }

        val card =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(16),
                    dp(14),
                    dp(16),
                    dp(12)
                )
                background =
                    roundedBackground(
                        color = Color.rgb(238, 238, 238),
                        radiusDp = 18f
                    )
                elevation = 18f * density
                isClickable = true
                setOnClickListener {
                    // Consuma il tap: non deve chiudere l'overlay.
                }
            }

        val title =
            TextView(context).apply {
                text = "GESTIONE PREZZI"
                textSize = 19f
                setTextColor(Color.BLACK)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }

        val productText =
            TextView(context).apply {
                text =
                    product.description
                        .ifBlank { product.articleCode }
                textSize = 13f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                maxLines = 2
            }

        statusText =
            TextView(context).apply {
                text = "Caricamento listini…"
                textSize = 13f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, dp(7))
            }

        rowsContainer =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

        val scroll =
            ScrollView(context).apply {
                isFillViewport = true
                addView(
                    rowsContainer,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }

        val hint =
            TextView(context).apply {
                text =
                    "ⓘ Prezzo: tap breve arrotonda · lungo modifica   |   " +
                            "Ricarico: lungo modifica"
                textSize = 10.5f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, dp(5))
            }

        val buttons =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }

        val closeButton =
            Button(context).apply {
                text = "CHIUDI"
                setOnClickListener {
                    remove()
                }
            }

        saveButton =
            Button(context).apply {
                text = "SALVA"
                isEnabled = false
                setOnClickListener {
                    saveChanges()
                }
            }

        buttons.addView(
            closeButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(6)
            }
        )

        buttons.addView(
            saveButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(6)
            }
        )

        card.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        card.addView(
            productText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(3)
                bottomMargin = dp(3)
            }
        )

        card.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        card.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        card.addView(
            hint,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        card.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val cardWidth =
            min(
                dp(430),
                screenWidth - dp(12)
            )

        val cardHeight =
            min(
                dp(578),
                screenHeight - dp(58)
            )

        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                cardWidth,
                cardHeight
            ).apply {
                gravity =
                    Gravity.TOP or
                            Gravity.CENTER_HORIZONTAL
                topMargin = dp(42)
                bottomMargin = dp(8)
            }
        )

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                softInputMode =
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

        root = overlay
        windowManager.addView(overlay, params)

        loadPriceLists()
    }

    private fun loadPriceLists() {
        val product =
            currentProduct ?: return

        statusText?.apply {
            text = "Caricamento listini…"
            setTextColor(Color.DKGRAY)
            visibility = View.VISIBLE
        }

        saveButton?.isEnabled = false

        Thread {
            val result =
                gatewayApiClient.getProductPriceLists(
                    product.articleId
                )

            postToUi {
                result.onSuccess { lists ->
                    renderLists(lists)
                }.onFailure { error ->
                    rowsContainer?.removeAllViews()
                    statusText?.apply {
                        text =
                            "Errore lettura listini: " +
                                    (error.message
                                        ?: "errore sconosciuto")
                        setTextColor(
                            Color.rgb(183, 28, 28)
                        )
                        visibility = View.VISIBLE
                    }
                }
            }
        }.start()
    }

    private fun renderLists(
        lists: List<ProductPriceListDto>
    ) {
        val container =
            rowsContainer ?: return

        container.removeAllViews()
        priceFields.clear()
        originalPrices.clear()

        val ordered =
            lists.sortedBy { item ->
                when (item.priceListId) {
                    2 -> 1 // INSTALLATORI
                    3 -> 2 // ELETTRICISTI
                    1 -> 3 // AL PUBBLICO
                    4 -> 4 // EXTRA
                    6 -> 5 // MAX
                    else -> 99
                }
            }

        if (ordered.isEmpty()) {
            statusText?.apply {
                text = "Nessun listino disponibile"
                setTextColor(Color.DKGRAY)
                visibility = View.VISIBLE
            }
            saveButton?.isEnabled = false
            return
        }

        statusText?.visibility = View.GONE

        ordered.forEach { priceList ->
            container.addView(
                createPriceRow(priceList),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(4)
                }
            )
        }

        saveButton?.apply {
            text = "SALVA"
            isEnabled = true
        }
    }

    private fun createPriceRow(
        priceList: ProductPriceListDto
    ): View {
        val isPublic =
            priceList.priceListId == 1

        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(8),
                    dp(5),
                    dp(8),
                    dp(6)
                )
                background =
                    roundedBackground(
                        color =
                            if (isPublic) {
                                Color.rgb(207, 216, 230)
                            } else {
                                Color.rgb(224, 224, 224)
                            },
                        radiusDp = 12f
                    )
            }

        val header =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

        val name =
            TextView(context).apply {
                text =
                    priceList.name
                        .substringAfter("-")
                        .ifBlank { priceList.name }
                textSize = 14f
                setTextColor(Color.BLACK)
                setTypeface(
                    typeface,
                    if (isPublic) {
                        Typeface.BOLD
                    } else {
                        Typeface.NORMAL
                    }
                )
            }

        val markup =
            EditText(context).apply {
                setText(
                    priceList.effectiveMarkupPercent
                        ?.let {
                            String.format(
                                Locale.ITALY,
                                "%.2f",
                                it
                            )
                        }
                        ?: ""
                )
                textSize = 15f
                setTextColor(Color.BLACK)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setSingleLine(true)
                inputType =
                    InputType.TYPE_CLASS_NUMBER or
                            InputType.TYPE_NUMBER_FLAG_DECIMAL
                imeOptions = EditorInfo.IME_ACTION_DONE
                setSelectAllOnFocus(true)
                showSoftInputOnFocus = false
                isFocusableInTouchMode = false
                isCursorVisible = false
                background = null
                setPadding(
                    dp(6),
                    0,
                    dp(2),
                    0
                )

                var markupTouchDownAt = 0L
                var markupLongPressTriggered = false

                setOnLongClickListener {
                    markupLongPressTriggered = true
                    isFocusableInTouchMode = true
                    isCursorVisible = true
                    requestFocus()
                    selectAll()
                    showSoftInputOnFocus = true
                    showKeyboard(this)
                    true
                }

                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            markupTouchDownAt =
                                android.os.SystemClock.elapsedRealtime()
                            markupLongPressTriggered = false
                        }

                        android.view.MotionEvent.ACTION_UP -> {
                            val elapsed =
                                android.os.SystemClock.elapsedRealtime() -
                                        markupTouchDownAt

                            if (
                                !markupLongPressTriggered &&
                                elapsed < 500L
                            ) {
                                performClick()
                                return@setOnTouchListener true
                            }
                        }
                    }

                    false
                }

                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        val parsedMarkup =
                            parseMarkup(
                                text?.toString().orEmpty()
                            )

                        if (parsedMarkup == null) {
                            error =
                                "Ricarico non valido – usa ad esempio 35,5"
                            selectAll()
                            false
                        } else {
                            val newPrice =
                                calculatePriceFromMarkup(
                                    priceListId =
                                        priceList.priceListId,
                                    markupPercent =
                                        parsedMarkup
                                )

                            if (newPrice == null) {
                                error =
                                    "Costo acquisto non disponibile"
                                false
                            } else {
                                setText(
                                    formatMarkupForEditing(
                                        parsedMarkup
                                    )
                                )
                                error = null
                                clearFocus()
                                isFocusableInTouchMode = false
                                isCursorVisible = false
                                showSoftInputOnFocus = false
                                hideKeyboard(this)

                                priceFields[
                                    priceList.priceListId
                                ]?.apply {
                                    setText(
                                        formatPriceForEditing(
                                            newPrice
                                        )
                                    )
                                    setSelection(text.length)
                                    isCursorVisible = false
                                    clearFocus()
                                    isFocusableInTouchMode = false
                                    showSoftInputOnFocus = false
                                }

                                updateMarkupPreview(
                                    priceListId =
                                        priceList.priceListId,
                                    salePrice = newPrice
                                )

                                true
                            }
                        }
                    } else {
                        false
                    }
                }
            }

        header.addView(
            name,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(
            markup,
            LinearLayout.LayoutParams(
                dp(86),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val initialPrice =
            priceList.salePrice
                ?.let { BigDecimal.valueOf(it) }
                ?.setScale(2, RoundingMode.HALF_UP)
                ?: BigDecimal.ZERO.setScale(2)

        val priceField =
            EditText(context).apply {
                setText(
                    formatPriceForEditing(initialPrice)
                )
                textSize = 23f
                setTextColor(Color.BLACK)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setSingleLine(true)
                inputType =
                    InputType.TYPE_CLASS_NUMBER or
                            InputType.TYPE_NUMBER_FLAG_DECIMAL
                imeOptions = EditorInfo.IME_ACTION_DONE
                setSelectAllOnFocus(true)
                selectAll()
                setPadding(
                    dp(10),
                    dp(3),
                    dp(10),
                    dp(3)
                )
                background =
                    roundedBackground(
                        color = Color.WHITE,
                        radiusDp = 9f,
                        strokeColor = Color.rgb(150, 150, 150),
                        strokeWidthDp = 1
                    )

                /*
                 * Tap breve = arrotondamento commerciale.
                 * Tap lungo = modifica manuale con selezione completa
                 * e tastiera decimale.
                 *
                 * Usiamo il touch direttamente perché un EditText al primo tap
                 * può limitarsi a prendere il focus senza eseguire in modo
                 * affidabile il normale OnClick.
                 */
                showSoftInputOnFocus = false
                isFocusableInTouchMode = false

                var touchDownAt = 0L
                var longPressTriggered = false

                setOnLongClickListener {
                    longPressTriggered = true
                    isFocusableInTouchMode = true
                    isCursorVisible = true
                    requestFocus()
                    selectAll()
                    showSoftInputOnFocus = true
                    showKeyboard(this)
                    true
                }

                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            touchDownAt =
                                android.os.SystemClock.elapsedRealtime()
                            longPressTriggered = false
                        }

                        android.view.MotionEvent.ACTION_UP -> {
                            val elapsed =
                                android.os.SystemClock.elapsedRealtime() -
                                        touchDownAt

                            if (
                                !longPressTriggered &&
                                elapsed < 500L
                            ) {
                                val parsed =
                                    parsePrice(
                                        text?.toString().orEmpty()
                                    )

                                if (parsed == null) {
                                    error =
                                        "Prezzo non valido – usa ad esempio 5,40"
                                } else {
                                    val rounded =
                                        roundToCommercialTenCents(parsed)

                                    setText(
                                        formatPriceForEditing(rounded)
                                    )
                                    setSelection(text.length)
                                    isCursorVisible = false
                                    error = null
                                    clearFocus()
                                    isFocusableInTouchMode = false
                                    showSoftInputOnFocus = false
                                    hideKeyboard(this)

                                    updateMarkupPreview(
                                        priceListId =
                                            priceList.priceListId,
                                        salePrice = rounded
                                    )

                                    Toast.makeText(
                                        context,
                                        "Arrotondato a ${
                                            formatPriceForEditing(rounded)
                                        } €",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                performClick()
                                return@setOnTouchListener true
                            }
                        }
                    }

                    false
                }

                setOnEditorActionListener { _, actionId, _ ->
                    if (
                        actionId == EditorInfo.IME_ACTION_DONE
                    ) {
                        clearFocus()
                        isFocusableInTouchMode = false
                        showSoftInputOnFocus = false
                        hideKeyboard(this)
                        true
                    } else {
                        false
                    }
                }
            }

        priceFields[priceList.priceListId] =
            priceField

        originalPrices[priceList.priceListId] =
            initialPrice

        markupViews[priceList.priceListId] =
            markup

        purchaseTaxables[priceList.priceListId] =
            priceList.purchaseTaxable
                ?.let { BigDecimal.valueOf(it) }

        row.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        row.addView(
            priceField,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(44)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(5)
            }
        )

        return row
    }

    private fun saveChanges() {
        val product =
            currentProduct ?: return

        val parsedPrices =
            linkedMapOf<Int, BigDecimal>()

        for ((priceListId, field) in priceFields) {
            val parsed =
                parsePrice(field.text?.toString().orEmpty())

            if (parsed == null) {
                field.error =
                    "Prezzo non valido – usa ad esempio 5,40"
                field.requestFocus()
                field.selectAll()
                showKeyboard(field)

                statusText?.apply {
                    text =
                        "Correggi il prezzo evidenziato prima di salvare."
                    setTextColor(
                        Color.rgb(183, 28, 28)
                    )
                    visibility = View.VISIBLE
                }
                return
            }

            parsedPrices[priceListId] =
                parsed.setScale(
                    2,
                    RoundingMode.HALF_UP
                )
        }

        val changed =
            parsedPrices.filter { (id, value) ->
                val original =
                    originalPrices[id]
                        ?.setScale(
                            2,
                            RoundingMode.HALF_UP
                        )

                original == null ||
                        value.compareTo(original) != 0
            }

        if (changed.isEmpty()) {
            Toast.makeText(
                context,
                "Nessun prezzo modificato",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        hideKeyboard(root)

        saveButton?.apply {
            isEnabled = false
            text = "SALVATAGGIO…"
        }

        statusText?.apply {
            text =
                if (changed.size == 1) {
                    "Salvataggio prezzo…"
                } else {
                    "Salvataggio ${changed.size} prezzi…"
                }
            setTextColor(Color.DKGRAY)
            visibility = View.VISIBLE
        }

        priceFields.values.forEach {
            it.isEnabled = false
        }

        Thread {
            val writeResult =
                runCatching {
                    changed.forEach { (priceListId, price) ->
                        gatewayApiClient
                            .updateProductPriceList(
                                articleId = product.articleId,
                                priceListId = priceListId,
                                price = price.toDouble()
                            )
                            .getOrThrow()
                    }

                    gatewayApiClient
                        .getProductPriceLists(
                            product.articleId
                        )
                        .getOrThrow()
                }

            postToUi {
                writeResult.onSuccess { refreshed ->
                    Toast.makeText(
                        context,
                        if (changed.size == 1) {
                            "Prezzo aggiornato"
                        } else {
                            "Prezzi aggiornati"
                        },
                        Toast.LENGTH_SHORT
                    ).show()

                    onSavedCallback?.invoke(refreshed)
                    remove()
                }.onFailure { error ->
                    priceFields.values.forEach {
                        it.isEnabled = true
                    }

                    saveButton?.apply {
                        isEnabled = true
                        text = "SALVA"
                    }

                    statusText?.apply {
                        text =
                            "Errore salvataggio: " +
                                    (error.message
                                        ?: "errore sconosciuto")
                        setTextColor(
                            Color.rgb(183, 28, 28)
                        )
                        visibility = View.VISIBLE
                    }
                }
            }
        }.start()
    }

    private fun updateMarkupPreview(
        priceListId: Int,
        salePrice: BigDecimal
    ) {
        val purchaseTaxable =
            purchaseTaxables[priceListId]

        val target =
            markupViews[priceListId]
                ?: return

        if (
            purchaseTaxable == null ||
            purchaseTaxable.compareTo(BigDecimal.ZERO) <= 0
        ) {
            target.setText("—")
            return
        }

        val vatRate =
            currentProduct
                ?.vatRate
                ?.trim()
                ?.replace("%", "")
                ?.replace(",", ".")
                ?.toBigDecimalOrNull()
                ?: BigDecimal.ZERO

        val divisor =
            BigDecimal.ONE.add(
                vatRate.divide(
                    BigDecimal(100),
                    6,
                    RoundingMode.HALF_UP
                )
            )

        val saleTaxable =
            if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                salePrice
            } else {
                salePrice.divide(
                    divisor,
                    6,
                    RoundingMode.HALF_UP
                )
            }

        val markup =
            saleTaxable
                .subtract(purchaseTaxable)
                .divide(
                    purchaseTaxable,
                    6,
                    RoundingMode.HALF_UP
                )
                .multiply(BigDecimal(100))

        target.setText(
            String.format(
                Locale.ITALY,
                "%.2f",
                markup.toDouble()
            )
        )
    }


    private fun parseMarkup(
        raw: String
    ): BigDecimal? {
        val text =
            raw.trim()
                .replace("%", "")
                .replace(" ", "")

        if (text.isBlank()) return null

        if (
            !text.matches(
                Regex("""^-?\d{1,4}([,.]\d{1,2})?$""")
            )
        ) {
            return null
        }

        return text
            .replace(',', '.')
            .toBigDecimalOrNull()
    }

    private fun formatMarkupForEditing(
        value: BigDecimal
    ): String =
        value
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString()
            .replace('.', ',')

    private fun calculatePriceFromMarkup(
        priceListId: Int,
        markupPercent: BigDecimal
    ): BigDecimal? {
        val purchaseTaxable =
            purchaseTaxables[priceListId]
                ?: return null

        if (
            purchaseTaxable.compareTo(
                BigDecimal.ZERO
            ) <= 0
        ) {
            return null
        }

        val vatRate =
            currentProduct
                ?.vatRate
                ?.trim()
                ?.replace("%", "")
                ?.replace(",", ".")
                ?.toBigDecimalOrNull()
                ?: BigDecimal.ZERO

        val saleTaxable =
            purchaseTaxable.multiply(
                BigDecimal.ONE.add(
                    markupPercent.divide(
                        BigDecimal(100),
                        6,
                        RoundingMode.HALF_UP
                    )
                )
            )

        val vatMultiplier =
            BigDecimal.ONE.add(
                vatRate.divide(
                    BigDecimal(100),
                    6,
                    RoundingMode.HALF_UP
                )
            )

        return saleTaxable
            .multiply(vatMultiplier)
            .setScale(
                2,
                RoundingMode.HALF_UP
            )
    }


    private fun parsePrice(
        raw: String
    ): BigDecimal? {
        val text =
            raw.trim()
                .replace("€", "")
                .replace(" ", "")

        if (text.isBlank()) return null

        /*
         * Accettiamo sia virgola sia punto, ma non combinazioni ambigue.
         * Massimo due cifre decimali.
         */
        if (
            !text.matches(
                Regex("""^\d{1,6}([,.]\d{1,2})?$""")
            )
        ) {
            return null
        }

        val normalized =
            text.replace(',', '.')

        return normalized
            .toBigDecimalOrNull()
            ?.takeIf { it.signum() >= 0 }
    }

    private fun roundToCommercialTenCents(
        value: BigDecimal
    ): BigDecimal {
        /*
         * 5,34 -> 5,30
         * 5,35 -> 5,40
         * 5,36 -> 5,40
         *
         * È la stessa regola commerciale già usata nel Collo veloce:
         * ai 5 centesimi arrotondiamo verso l'alto.
         */
        return value
            .multiply(BigDecimal.TEN)
            .setScale(0, RoundingMode.HALF_UP)
            .divide(
                BigDecimal.TEN,
                2,
                RoundingMode.UNNECESSARY
            )
    }

    private fun formatPriceForEditing(
        value: BigDecimal
    ): String =
        value
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString()
            .replace('.', ',')

    private fun showKeyboard(
        editText: EditText
    ) {
        editText.post {
            val imm =
                context.getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as InputMethodManager

            imm.showSoftInput(
                editText,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    private fun hideKeyboard(
        view: View?
    ) {
        val token =
            view?.windowToken ?: return

        val imm =
            context.getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

        imm.hideSoftInputFromWindow(
            token,
            0
        )
    }

    fun remove(
        notifyClosed: Boolean = true
    ) {
        val current =
            root ?: return

        hideKeyboard(current)
        root = null

        runCatching {
            if (current.isAttachedToWindow) {
                windowManager.removeView(current)
            }
        }

        currentProduct = null
        rowsContainer = null
        statusText = null
        saveButton = null
        priceFields.clear()
        originalPrices.clear()
        markupViews.clear()
        purchaseTaxables.clear()

        if (notifyClosed) {
            onClosedCallback?.invoke()
        }

        onSavedCallback = null
        onClosedCallback = null
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Float,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 0
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius =
                radiusDp *
                        context.resources
                            .displayMetrics
                            .density

            if (
                strokeColor != null &&
                strokeWidthDp > 0
            ) {
                setStroke(
                    dp(strokeWidthDp),
                    strokeColor
                )
            }
        }

    private fun dp(
        value: Int
    ): Int =
        (
                value *
                        context.resources
                            .displayMetrics
                            .density
                ).toInt()

    private fun postToUi(
        block: () -> Unit
    ) {
        android.os.Handler(
            android.os.Looper.getMainLooper()
        ).post(block)
    }
}