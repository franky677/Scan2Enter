package com.scan2enter.overlay.popup

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.method.DigitsKeyListener
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
import com.scan2enter.api.ProductPromotionGroupDto
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PromotionGroupManagementPopup(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private val gatewayApiClient = GatewayApiClient()

    private var root: View? = null
    private var currentGroup: ProductPromotionGroupDto? = null

    private var discountField: EditText? = null
    private var validFromButton: Button? = null
    private var validToButton: Button? = null
    private var statusText: TextView? = null
    private var saveButton: Button? = null
    private var deleteButton: Button? = null

    private var validFromDate: Calendar? = null
    private var validToDate: Calendar? = null

    private var onSavedCallback: ((ProductPromotionGroupDto) -> Unit)? = null
    private var onDeletedCallback: (() -> Unit)? = null
    private var onClosedCallback: (() -> Unit)? = null

    fun isShowing(): Boolean = root != null

    fun show(
        promo: ProductPromotionGroupDto,
        onSaved: (ProductPromotionGroupDto) -> Unit,
        onDeleted: () -> Unit,
        onClosed: () -> Unit
    ) {
        remove(notifyClosed = false)

        currentGroup = promo
        onSavedCallback = onSaved
        onDeletedCallback = onDeleted
        onClosedCallback = onClosed

        validFromDate = parseServerDate(promo.validFrom)
        validToDate = parseServerDate(promo.validTo)

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
            text = "PROMO MARCA"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        val groupText = TextView(context).apply {
            text = buildString {
                append(promo.groupDescription.ifBlank { "PROMO GRUPPO" })
                if (promo.groupCode.isNotBlank()) {
                    append("\n${promo.groupCode}")
                }
            }
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(5))
        }

        val articleText = TextView(context).apply {
            text = buildString {
                if (promo.eligibleArticles >= 0) {
                    append("${formatArticleCount(promo.eligibleArticles)} articoli")
                } else {
                    append("Articoli della marca")
                }
                if (promo.materializedArticles >= 0) {
                    append("  •  ${formatArticleCount(promo.materializedArticles)} materializzati")
                }
            }
            textSize = 12.5f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        }

        val discountBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        discountBox.addView(label("SCONTO %").apply { gravity = Gravity.CENTER })
        discountField = numericField(formatNumber(promo.discountPercent)).also {
            discountBox.addView(
                it,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val datesTitle = label("VALIDITÀ").apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        }

        val datesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        validFromButton = Button(context).apply {
            setOnClickListener { chooseDate(isFrom = true) }
        }

        validToButton = Button(context).apply {
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
            text = statusText(promo)
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
            isEnabled = promo.idPromoGroup > 0L
            visibility =
                if (promo.idPromoGroup > 0L) View.VISIBLE
                else View.GONE
            setOnClickListener { deleteGroup() }
        }

        val closeButton = Button(context).apply {
            text = "CHIUDI"
            setOnClickListener { remove() }
        }

        saveButton = Button(context).apply {
            text = "SALVA"
            setOnClickListener { saveGroup() }
        }

        actions.addView(deleteButton, actionParams())
        actions.addView(closeButton, actionParams())
        actions.addView(saveButton, actionParams())

        card.addView(title)
        card.addView(groupText)
        card.addView(articleText)
        card.addView(discountBox)
        card.addView(datesTitle)
        card.addView(datesRow)
        card.addView(presets)
        card.addView(statusText)
        card.addView(actions)

        val width = minOf(
            dp(430),
            context.resources.displayMetrics.widthPixels - dp(12)
        )

        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                width,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
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
        refreshDateButtons()
    }

    private fun saveGroup() {
        val promo = currentGroup ?: return
        val discount = parseNumber(discountField?.text?.toString().orEmpty())

        if (discount == null || discount <= 0.0 || discount > 100.0) {
            discountField?.error = "Inserisci uno sconto maggiore di 0 e massimo 100"
            return
        }

        val from = validFromDate
        val to = validToDate

        if (from != null && to != null && to.before(from)) {
            setStatus(
                "La data finale non può precedere quella iniziale",
                isError = true
            )
            return
        }

        hideKeyboard()
        setBusy(true, "Salvataggio promo marca…")

        Thread {
            val result = gatewayApiClient.updateProducerPromotionGroup(
                producerId = promo.groupId,
                idPromoGroup = promo.idPromoGroup.takeIf { it > 0L },
                discountPercent = discount,
                validFrom = from?.let(::serverDate),
                validTo = to?.let(::serverDate),
                enabled = true,
                producerCode = promo.groupCode,
                producerDescription = promo.groupDescription,
                priority = promo.priority
            )

            postToUi {
                result.onSuccess { saved ->
                    currentGroup = saved
                    deleteButton?.apply {
                        visibility = View.VISIBLE
                        isEnabled = true
                    }
                    validFromDate = parseServerDate(saved.validFrom)
                    validToDate = parseServerDate(saved.validTo)
                    refreshDateButtons()
                    discountField?.setText(formatNumber(saved.discountPercent))
                    Toast.makeText(
                        context,
                        "Promo marca salvata",
                        Toast.LENGTH_SHORT
                    ).show()
                    onSavedCallback?.invoke(saved)
                    setBusy(false, statusText(saved))
                }.onFailure { error ->
                    setBusy(
                        false,
                        "Errore salvataggio: ${error.message ?: "errore sconosciuto"}",
                        isError = true
                    )
                }
            }
        }.start()
    }

    private fun deleteGroup() {
        val promo = currentGroup ?: return
        if (promo.idPromoGroup <= 0L) return

        hideKeyboard()
        setBusy(true, "Eliminazione promo marca…")

        Thread {
            val result =
                gatewayApiClient.deletePromotionGroup(promo.idPromoGroup)

            postToUi {
                result.onSuccess { deleted ->
                    if (deleted) {
                        Toast.makeText(
                            context,
                            "Promo marca eliminata",
                            Toast.LENGTH_SHORT
                        ).show()
                        onDeletedCallback?.invoke()
                        remove()
                    } else {
                        setBusy(
                            false,
                            "Promo marca non eliminata",
                            isError = true
                        )
                    }
                }.onFailure { error ->
                    setBusy(
                        false,
                        "Errore eliminazione: ${error.message ?: "errore sconosciuto"}",
                        isError = true
                    )
                }
            }
        }.start()
    }

    private fun chooseDate(isFrom: Boolean) {
        val initial =
            (if (isFrom) validFromDate else validToDate)
                ?: Calendar.getInstance()

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
            validFromDate?.let { "DA: ${displayDate(it)}" }
                ?: "DA: SUBITO"

        validToButton?.text =
            validToDate?.let { "A: ${displayDate(it)}" }
                ?: "A: SENZA SCADENZA"
    }

    private fun setBusy(
        busy: Boolean,
        message: String,
        isError: Boolean = false
    ) {
        saveButton?.isEnabled = !busy
        deleteButton?.isEnabled = !busy && (currentGroup?.idPromoGroup ?: 0L) > 0L
        setStatus(message, isError)
    }

    private fun setStatus(message: String, isError: Boolean = false) {
        statusText?.apply {
            text = message
            setTextColor(
                if (isError) Color.rgb(183, 28, 28)
                else Color.DKGRAY
            )
        }
    }

    private fun statusText(promo: ProductPromotionGroupDto): String =
        buildString {
            append(
                when (promo.status.uppercase()) {
                    "IN_CORSO" -> "Promo marca in corso"
                    "PROGRAMMATA" -> "Promo marca programmata"
                    "SCADUTA" -> "Promo marca scaduta"
                    "SENZA_SCADENZA" -> "Promo marca senza scadenza"
                    "DISABILITATA" -> "Promo marca disabilitata"
                    else -> "Promo marca ${promo.status.replace('_', ' ')}"
                }
            )
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
        inputType =
            InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
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
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        }

    private fun parseNumber(raw: String): Double? =
        raw.trim()
            .replace("%", "")
            .replace(" ", "")
            .replace(',', '.')
            .toDoubleOrNull()

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.ITALY, "%.4f", value)
                .trimEnd('0')
                .trimEnd(',')
        }

    private fun formatArticleCount(value: Int): String =
        NumberFormat.getIntegerInstance(Locale.ITALY).format(value)

    private fun displayDate(calendar: Calendar): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(calendar.time)

    private fun serverDate(calendar: Calendar): String =
        SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss",
            Locale.US
        ).format(calendar.time)

    private fun parseServerDate(raw: String?): Calendar? {
        if (raw.isNullOrBlank()) return null

        val clean = raw.take(19)
        val parsed = runCatching {
            SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss",
                Locale.US
            ).apply {
                isLenient = false
            }.parse(clean)
        }.getOrNull() ?: return null

        return Calendar.getInstance().apply { time = parsed }
    }

    private fun hideKeyboard() {
        val current = root ?: return
        val imm =
            context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager
        imm.hideSoftInputFromWindow(current.windowToken, 0)
    }

    fun remove(notifyClosed: Boolean = true) {
        val current = root ?: return

        hideKeyboard()
        root = null

        runCatching {
            if (current.isAttachedToWindow) {
                windowManager.removeView(current)
            }
        }

        currentGroup = null
        discountField = null
        validFromButton = null
        validToButton = null
        statusText = null
        saveButton = null
        deleteButton = null
        validFromDate = null
        validToDate = null

        if (notifyClosed) {
            onClosedCallback?.invoke()
        }

        onSavedCallback = null
        onDeletedCallback = null
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
                radiusDp * context.resources.displayMetrics.density

            if (strokeColor != null && strokeWidthDp > 0) {
                setStroke(dp(strokeWidthDp), strokeColor)
            }
        }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun postToUi(block: () -> Unit) {
        android.os.Handler(
            android.os.Looper.getMainLooper()
        ).post(block)
    }
}
