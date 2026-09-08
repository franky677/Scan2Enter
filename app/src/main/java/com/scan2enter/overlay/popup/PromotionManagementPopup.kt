package com.scan2enter.overlay.popup

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.api.ProductPromoDto
import com.scan2enter.model.ProductInfo
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PromotionManagementPopup(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private val gatewayApiClient = GatewayApiClient()

    private var root: View? = null
    private var currentProduct: ProductInfo? = null
    private var currentPromo: ProductPromoDto? = null

    private var discountField: EditText? = null
    private var offerPriceField: EditText? = null
    private var validFromButton: Button? = null
    private var validToButton: Button? = null
    private var statusText: TextView? = null
    private var saveButton: Button? = null
    private var deleteButton: Button? = null
    private var printButton: Button? = null

    private var validFromDate: Calendar? = null
    private var validToDate: Calendar? = null
    private var syncingFields = false

    private var onSavedCallback: ((ProductPromoDto) -> Unit)? = null
    private var onDeletedCallback: (() -> Unit)? = null
    private var onClosedCallback: (() -> Unit)? = null
    private var onPrintRequestedCallback: ((ProductInfo, String) -> Unit)? = null

    fun isShowing(): Boolean = root != null

    fun show(
        product: ProductInfo,
        onSaved: (ProductPromoDto) -> Unit,
        onDeleted: () -> Unit,
        onClosed: () -> Unit,
        onPrintRequested: ((ProductInfo, String) -> Unit)? = null
    ) {
        remove(notifyClosed = false)

        currentProduct = product
        onSavedCallback = onSaved
        onDeletedCallback = onDeleted
        onClosedCallback = onClosed
        onPrintRequestedCallback = onPrintRequested

        val overlay = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(125, 0, 0, 0))
            isClickable = true
            setOnClickListener { remove() }
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            background = roundedBackground(Color.rgb(238, 238, 238), 18f)
            elevation = dp(18).toFloat()
            isClickable = true
            setOnClickListener { }
        }

        val title = TextView(context).apply {
            text = "PROMOZIONE"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        val productText = TextView(context).apply {
            text = buildString {
                append(product.articleCode)
                if (product.articleCode.isNotBlank() && product.description.isNotBlank()) append("\n")
                append(product.description)
            }
            textSize = 13f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            maxLines = 3
            setPadding(0, dp(3), 0, dp(8))
        }

        val publicPrice = parseMoney(product.publicPrice) ?: BigDecimal.ZERO
        val publicText = TextView(context).apply {
            text = "PREZZO PUBBLICO  ${formatMoney(publicPrice)} €"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        }

        val fieldsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val discountBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        discountBox.addView(label("SCONTO %"))
        discountField = numericField("0,00").also { discountBox.addView(it) }

        val offerBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        offerBox.addView(label("PREZZO PROMO €"))
        offerPriceField = numericField(formatMoney(publicPrice)).also { offerBox.addView(it) }

        fieldsRow.addView(
            discountBox,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(5)
            }
        )
        fieldsRow.addView(
            offerBox,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(5)
            }
        )

        installBidirectionalCalculation(publicPrice)

        val datesTitle = label("VALIDITÀ").apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        }

        val datesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        validFromButton = Button(context).apply {
            text = "DA: SUBITO"
            setOnClickListener { chooseDate(isFrom = true) }
        }
        validToButton = Button(context).apply {
            text = "A: SENZA SCADENZA"
            setOnClickListener { chooseDate(isFrom = false) }
        }

        datesRow.addView(
            validFromButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
        )
        datesRow.addView(
            validToButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
            }
        )

        val presets = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(
            "OGGI" to 0,
            "7 GG" to 7,
            "30 GG" to 30,
            "∞" to -1
        ).forEach { (caption, days) ->
            presets.addView(
                Button(context).apply {
                    text = caption
                    textSize = 11f
                    setOnClickListener { applyPreset(days) }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
            )
        }

        statusText = TextView(context).apply {
            text = "Caricamento promozione…"
            textSize = 12.5f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(5))
        }

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        deleteButton = Button(context).apply {
            text = "ELIMINA"
            isEnabled = false
            setOnClickListener { deletePromo() }
        }
        val closeButton = Button(context).apply {
            text = "CHIUDI"
            setOnClickListener { remove() }
        }
        saveButton = Button(context).apply {
            text = "SALVA"
            isEnabled = false
            setOnClickListener { savePromo() }
        }

        actions.addView(deleteButton, actionParams())
        actions.addView(closeButton, actionParams())
        actions.addView(saveButton, actionParams())

        printButton = Button(context).apply {
            text = "STAMPA PROMO"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            isEnabled = false
            setOnClickListener { openPromoPrint() }
        }

        card.addView(title)
        card.addView(productText)
        card.addView(publicText)
        card.addView(fieldsRow)
        card.addView(datesTitle)
        card.addView(datesRow)
        card.addView(presets)
        card.addView(statusText)
        card.addView(actions)
        card.addView(
            printButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )

        val width = minOf(dp(430), context.resources.displayMetrics.widthPixels - dp(12))
        overlay.addView(
            card,
            FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(54)
            }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        root = overlay
        windowManager.addView(overlay, params)
        loadPromo()
    }

    private fun loadPromo() {
        val product = currentProduct ?: return
        Thread {
            val result = gatewayApiClient.getProductPromo(product.articleId)
            postToUi {
                result.onSuccess { promo ->
                    currentPromo = promo
                    if (promo != null) {
                        syncingFields = true
                        discountField?.setText(formatNumber(promo.discountPercent))
                        offerPriceField?.setText(formatNumber(promo.offerPrice))
                        validFromDate = parseServerDate(promo.validFrom)
                        validToDate = parseServerDate(promo.validTo)
                        syncingFields = false
                        statusText?.text = promoStatusText(promo)
                        deleteButton?.isEnabled = true
                        printButton?.isEnabled = onPrintRequestedCallback != null
                    } else {
                        statusText?.text = "Nessuna promozione configurata"
                        deleteButton?.isEnabled = false
                        printButton?.isEnabled = false
                    }
                    refreshDateButtons()
                    saveButton?.isEnabled = true
                }.onFailure { error ->
                    statusText?.apply {
                        text = "Errore lettura promo: ${error.message ?: "errore sconosciuto"}"
                        setTextColor(Color.rgb(183, 28, 28))
                    }
                    saveButton?.isEnabled = false
                }
            }
        }.start()
    }

    private fun installBidirectionalCalculation(publicPrice: BigDecimal) {
        discountField?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (syncingFields || publicPrice <= BigDecimal.ZERO) return

                val discount = parseMoney(s?.toString().orEmpty())
                if (discount == null) {
                    setPriceValidationError(null)
                    return
                }

                if (discount < BigDecimal.ZERO || discount >= BigDecimal(100)) {
                    setPriceValidationError("Lo sconto deve essere compreso tra 0 e 100")
                    return
                }

                val rawOffer = publicPrice.multiply(
                    BigDecimal.ONE.subtract(
                        discount.divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
                    )
                )
                val roundedOffer = roundToCommercialTenCents(rawOffer)

                syncingFields = true
                offerPriceField?.setText(formatMoney(roundedOffer))
                syncingFields = false

                setPriceValidationError(null)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        offerPriceField?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (syncingFields || publicPrice <= BigDecimal.ZERO) return

                val typedOffer = parseMoney(s?.toString().orEmpty())
                if (typedOffer == null) {
                    setPriceValidationError(null)
                    return
                }

                if (typedOffer <= BigDecimal.ZERO) {
                    setPriceValidationError("Il prezzo promo deve essere maggiore di zero")
                    return
                }

                if (typedOffer > publicPrice) {
                    setPriceValidationError("Il prezzo promo non può superare il prezzo pubblico")
                    return
                }

                val roundedOffer = roundToCommercialTenCents(typedOffer)
                val discount = BigDecimal(100)
                    .multiply(
                        BigDecimal.ONE.subtract(
                            roundedOffer.divide(publicPrice, 8, RoundingMode.HALF_UP)
                        )
                    )
                    .setScale(2, RoundingMode.HALF_UP)

                syncingFields = true
                discountField?.setText(formatMoney(discount))
                syncingFields = false

                setPriceValidationError(null)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        offerPriceField?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus || syncingFields || publicPrice <= BigDecimal.ZERO) {
                return@setOnFocusChangeListener
            }

            val typedOffer =
                parseMoney(offerPriceField?.text?.toString().orEmpty())
                    ?: return@setOnFocusChangeListener

            if (typedOffer <= BigDecimal.ZERO || typedOffer > publicPrice) {
                return@setOnFocusChangeListener
            }

            val roundedOffer = roundToCommercialTenCents(typedOffer)

            syncingFields = true
            offerPriceField?.setText(formatMoney(roundedOffer))

            val discount = BigDecimal(100)
                .multiply(
                    BigDecimal.ONE.subtract(
                        roundedOffer.divide(publicPrice, 8, RoundingMode.HALF_UP)
                    )
                )
                .setScale(2, RoundingMode.HALF_UP)

            discountField?.setText(formatMoney(discount))
            syncingFields = false

            setPriceValidationError(null)
        }
    }

    private fun roundToCommercialTenCents(value: BigDecimal): BigDecimal =
        value
            .multiply(BigDecimal.TEN)
            .setScale(0, RoundingMode.HALF_UP)
            .divide(BigDecimal.TEN)
            .setScale(2, RoundingMode.UNNECESSARY)

    private fun setPriceValidationError(message: String?) {
        offerPriceField?.error = message

        if (message == null) {
            saveButton?.isEnabled = currentProduct != null
            statusText?.apply {
                if (currentPromo != null) {
                    text = promoStatusText(currentPromo!!)
                } else {
                    text = "Nessuna promozione configurata"
                }
                setTextColor(Color.DKGRAY)
            }
        } else {
            saveButton?.isEnabled = false
            statusText?.apply {
                text = message
                setTextColor(Color.rgb(183, 28, 28))
            }
        }
    }

    private fun chooseDate(isFrom: Boolean) {
        val initial = (if (isFrom) validFromDate else validToDate) ?: Calendar.getInstance()

        /*
         * Questo popup vive in TYPE_APPLICATION_OVERLAY e quindi non ha
         * un Activity token. Anche il DatePicker deve essere mostrato come
         * overlay; un normale DatePickerDialog tenterebbe invece di usare
         * un token Activity nullo e causerebbe BadTokenException.
         */
        val dialog = DatePickerDialog(
            context,
            { _, year, month, day ->
                val selected = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    if (isFrom) {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    } else {
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }
                    set(Calendar.MILLISECOND, 0)
                }

                if (isFrom) {
                    val currentTo = validToDate
                    if (currentTo != null && selected.after(currentTo)) {
                        Toast.makeText(
                            context,
                            "La data iniziale non può essere successiva alla data finale",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@DatePickerDialog
                    }
                    validFromDate = selected
                } else {
                    val currentFrom = validFromDate
                    if (currentFrom != null && selected.before(currentFrom)) {
                        Toast.makeText(
                            context,
                            "La data finale non può precedere la data iniziale",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@DatePickerDialog
                    }
                    validToDate = selected
                }

                refreshDateButtons()
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        )

        if (isFrom) {
            validToDate?.let { dialog.datePicker.maxDate = it.timeInMillis }
        } else {
            validFromDate?.let { dialog.datePicker.minDate = it.timeInMillis }
        }

        dialog.window?.setType(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        )
        dialog.show()
    }

    private fun applyPreset(days: Int) {
        val now = Calendar.getInstance()
        validFromDate = null
        validToDate =
            if (days < 0) {
                null
            } else {
                Calendar.getInstance().apply {
                    timeInMillis = now.timeInMillis
                    if (days > 0) add(Calendar.DAY_OF_MONTH, days)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 0)
                }
            }
        refreshDateButtons()
    }

    private fun refreshDateButtons() {
        validFromButton?.text =
            validFromDate?.let { "DA: ${displayDate(it)}" } ?: "DA: SUBITO"
        validToButton?.text =
            validToDate?.let { "A: ${displayDate(it)}" } ?: "A: SENZA SCADENZA"
    }

    private fun savePromo() {
        val product = currentProduct ?: return
        val publicPrice = parseMoney(product.publicPrice) ?: BigDecimal.ZERO
        val discount = parseMoney(discountField?.text?.toString().orEmpty())
        val typedOffer = parseMoney(offerPriceField?.text?.toString().orEmpty())

        if (discount == null || discount <= BigDecimal.ZERO || discount >= BigDecimal(100)) {
            discountField?.error = "Inserisci uno sconto maggiore di 0 e minore di 100"
            return
        }

        if (typedOffer == null || typedOffer <= BigDecimal.ZERO) {
            setPriceValidationError("Il prezzo promo deve essere maggiore di zero")
            return
        }

        if (publicPrice <= BigDecimal.ZERO || typedOffer > publicPrice) {
            setPriceValidationError("Il prezzo promo non può superare il prezzo pubblico")
            return
        }

        /*
         * Il Gateway salva la promo partendo dalla percentuale.
         * Prima di inviarla riallineiamo la percentuale al prezzo commerciale
         * arrotondato ai 10 centesimi, così anteprima Android e risultato
         * restituito dal Gateway coincidono.
         */
        val roundedOffer = roundToCommercialTenCents(typedOffer)
        val alignedDiscount = BigDecimal(100)
            .multiply(
                BigDecimal.ONE.subtract(
                    roundedOffer.divide(publicPrice, 8, RoundingMode.HALF_UP)
                )
            )
            .setScale(4, RoundingMode.HALF_UP)

        syncingFields = true
        offerPriceField?.setText(formatMoney(roundedOffer))
        discountField?.setText(formatMoney(alignedDiscount))
        syncingFields = false

        val from = validFromDate
        val to = validToDate
        if (from != null && to != null && to.before(from)) {
            statusText?.apply {
                text = "La data finale non può precedere quella iniziale"
                setTextColor(Color.rgb(183, 28, 28))
            }
            return
        }

        hideKeyboard()
        setBusy(true, "Salvataggio promozione…")

        Thread {
            val result = gatewayApiClient.updateProductPromo(
                articleId = product.articleId,
                discountPercent = alignedDiscount.toDouble(),
                validFrom = from?.let(::serverDate),
                validTo = to?.let(::serverDate)
            )
            postToUi {
                result.onSuccess { promo ->
                    if (promo == null) {
                        setBusy(false, "Risposta promozione non disponibile", isError = true)
                    } else {
                        currentPromo = promo
                        deleteButton?.isEnabled = true
                        Toast.makeText(context, "Promozione salvata", Toast.LENGTH_SHORT).show()
                        onSavedCallback?.invoke(promo)
                        statusText?.apply {
                            text = promoStatusText(promo)
                            setTextColor(Color.DKGRAY)
                        }
                        saveButton?.isEnabled = true
                        deleteButton?.isEnabled = true
                        printButton?.isEnabled = onPrintRequestedCallback != null
                    }
                }.onFailure { error ->
                    setBusy(false, "Errore salvataggio: ${error.message ?: "errore sconosciuto"}", isError = true)
                }
            }
        }.start()
    }

    private fun openPromoPrint() {
        val product = currentProduct ?: return
        val callback = onPrintRequestedCallback ?: return
        val promo = currentPromo ?: run {
            Toast.makeText(context, "Salva prima la promozione", Toast.LENGTH_SHORT).show()
            return
        }

        val offerPrice = String.format(Locale.ITALY, "%.2f", promo.offerPrice)
        hideKeyboard()
        remove(notifyClosed = false)
        callback(product, offerPrice)
    }

    private fun deletePromo() {
        val product = currentProduct ?: return
        hideKeyboard()
        setBusy(true, "Eliminazione promozione…")

        Thread {
            val result = gatewayApiClient.deleteProductPromo(product.articleId)
            postToUi {
                result.onSuccess { deleted ->
                    if (deleted) {
                        Toast.makeText(context, "Promozione eliminata", Toast.LENGTH_SHORT).show()
                        onDeletedCallback?.invoke()
                        remove()
                    } else {
                        setBusy(false, "Promozione non eliminata", isError = true)
                    }
                }.onFailure { error ->
                    setBusy(false, "Errore eliminazione: ${error.message ?: "errore sconosciuto"}", isError = true)
                }
            }
        }.start()
    }

    private fun setBusy(busy: Boolean, message: String, isError: Boolean = false) {
        saveButton?.isEnabled = !busy
        deleteButton?.isEnabled = !busy && currentPromo != null
        printButton?.isEnabled = !busy && currentPromo != null && onPrintRequestedCallback != null
        statusText?.apply {
            text = message
            setTextColor(if (isError) Color.rgb(183, 28, 28) else Color.DKGRAY)
        }
    }

    private fun promoStatusText(promo: ProductPromoDto): String =
        when {
            promo.validFrom == null && promo.validTo == null -> "Promo senza scadenza"
            promo.validTo == null -> "Promo attiva dal ${promo.validFrom?.take(10).orEmpty()}"
            else -> "Promo configurata fino al ${promo.validTo.take(10)}"
        }

    private fun label(textValue: String) = TextView(context).apply {
        text = textValue
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.DKGRAY)
    }

    private fun numericField(initial: String) = EditText(context).apply {
        setText(initial)
        textSize = 20f
        setTextColor(Color.BLACK)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        keyListener = DigitsKeyListener.getInstance("0123456789,.")
        setSelectAllOnFocus(true)
        background = roundedBackground(
            color = Color.WHITE,
            radiusDp = 9f,
            strokeColor = Color.rgb(150, 150, 150),
            strokeWidthDp = 1
        )
        setPadding(dp(8), dp(7), dp(8), dp(7))
    }

    private fun actionParams() =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        }

    private fun parseMoney(raw: String): BigDecimal? =
        raw.trim()
            .replace("€", "")
            .replace(" ", "")
            .replace(',', '.')
            .toBigDecimalOrNull()

    private fun formatMoney(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

    private fun formatNumber(value: Double): String =
        String.format(Locale.ITALY, "%.2f", value)

    private fun displayDate(calendar: Calendar): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(calendar.time)

    private fun serverDate(calendar: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(calendar.time)

    private fun parseServerDate(raw: String?): Calendar? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.take(19)
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                isLenient = false
            }.parse(clean)
        }.getOrNull() ?: return null
        return Calendar.getInstance().apply { time = parsed }
    }

    private fun hideKeyboard() {
        val current = root ?: return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(current.windowToken, 0)
    }

    fun remove(notifyClosed: Boolean = true) {
        val current = root ?: return
        hideKeyboard()
        root = null
        runCatching {
            if (current.isAttachedToWindow) windowManager.removeView(current)
        }

        currentProduct = null
        currentPromo = null
        discountField = null
        offerPriceField = null
        validFromButton = null
        validToButton = null
        statusText = null
        saveButton = null
        deleteButton = null
        printButton = null
        validFromDate = null
        validToDate = null

        if (notifyClosed) onClosedCallback?.invoke()

        onSavedCallback = null
        onDeletedCallback = null
        onClosedCallback = null
        onPrintRequestedCallback = null
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
            cornerRadius = radiusDp * context.resources.displayMetrics.density
            if (strokeColor != null && strokeWidthDp > 0) {
                setStroke(dp(strokeWidthDp), strokeColor)
            }
        }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun postToUi(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }
}
