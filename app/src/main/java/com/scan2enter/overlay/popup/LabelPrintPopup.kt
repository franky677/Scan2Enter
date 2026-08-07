package com.scan2enter.overlay.popup

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import com.scan2enter.R
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.model.ProductInfo
import kotlin.math.max
import kotlin.math.min

class LabelPrintPopup(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private companion object {
        const val PREFS_NAME = "godex_label_preferences"
        const val PREF_TEMPLATE = "template"
        const val PREF_QUANTITY = "quantity"
        const val PREF_NOTE = "note"
        const val PREF_PRINTER = "printer"
    }

    private val gatewayApiClient = GatewayApiClient()
    private var overlayRoot: View? = null

    fun show(
        product: ProductInfo?,
        onScanRequested: () -> Unit = {},
        onClosed: () -> Unit = {}
    ) {
        if (overlayRoot != null) return

        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
        }

        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.label_print_dialog, root, false)

        dialogView.findViewById<TextView>(R.id.labelPrintArticleText).text =
            if (product == null) {
                "Scegli il tipo di etichetta, poi premi SCANSIONA"
            } else {
                listOf(product.articleCode, product.description)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            }

        val godex = dialogView.findViewById<RadioButton>(
            R.id.godexPrinterRadioButton
        )
        val epson = dialogView.findViewById<RadioButton>(
            R.id.epsonPrinterRadioButton
        )
        val standard = dialogView.findViewById<RadioButton>(
            R.id.standardLabelRadioButton
        )
        val image = dialogView.findViewById<RadioButton>(
            R.id.imageLabelRadioButton
        )
        val price = dialogView.findViewById<RadioButton>(
            R.id.priceLabelRadioButton
        )
        val note = dialogView.findViewById<RadioButton>(
            R.id.noteLabelRadioButton
        )
        val noteEditorContainer = dialogView.findViewById<View>(
            R.id.noteEditorContainer
        )
        val noteEditText = dialogView.findViewById<EditText>(
            R.id.noteEditText
        )
        val quantityEdit = dialogView.findViewById<EditText>(
            R.id.labelQuantityEditText
        )

        val preferences = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        when (preferences.getString(PREF_TEMPLATE, "STANDARD")) {
            "IMAGE" -> image.isChecked = true
            "PRICE" -> price.isChecked = true
            "NOTE" -> note.isChecked = true
            else -> standard.isChecked = true
        }

        if (preferences.getString(PREF_PRINTER, "GODEX") == "EPSON") {
            epson.isChecked = true
        } else {
            godex.isChecked = true
        }

        quantityEdit.setText(
            preferences.getInt(PREF_QUANTITY, 1)
                .coerceIn(1, 100)
                .toString()
        )
        noteEditText.setText(
            preferences.getString(PREF_NOTE, "").orEmpty()
        )
        noteEditText.setSelection(noteEditText.text.length)

        fun selectedTemplate(): String = when {
            image.isChecked -> "IMAGE"
            price.isChecked -> "PRICE"
            note.isChecked -> "NOTE"
            else -> "STANDARD"
        }

        fun savePreferences() {
            preferences.edit()
                .putString(PREF_TEMPLATE, selectedTemplate())
                .putInt(
                    PREF_QUANTITY,
                    quantityEdit.text.toString()
                        .toIntOrNull()
                        ?.coerceIn(1, 100)
                        ?: 1
                )
                .putString(
                    PREF_NOTE,
                    noteEditText.text.toString()
                        .take(80)
                )
                .putString(
                    PREF_PRINTER,
                    if (epson.isChecked) "EPSON" else "GODEX"
                )
                .apply()
        }

        fun updateNoteEditorVisibility() {
            noteEditorContainer.visibility =
                if (note.isChecked) View.VISIBLE else View.GONE
        }

        val optionChanged = android.widget.CompoundButton.OnCheckedChangeListener { _, _ ->
            updateNoteEditorVisibility()
            savePreferences()
        }

        standard.setOnCheckedChangeListener(optionChanged)
        image.setOnCheckedChangeListener(optionChanged)
        price.setOnCheckedChangeListener(optionChanged)
        note.setOnCheckedChangeListener(optionChanged)
        godex.setOnCheckedChangeListener(optionChanged)
        epson.setOnCheckedChangeListener(optionChanged)

        noteEditText.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    text: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    text: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    savePreferences()
                }

                override fun afterTextChanged(editable: Editable?) = Unit
            }
        )

        updateNoteEditorVisibility()

        bindQuantityButtons(
            dialogView.findViewById(R.id.labelQuantityMinusButton),
            dialogView.findViewById(R.id.labelQuantityPlusButton),
            quantityEdit
        )

        dialogView.findViewById<View>(R.id.closeLabelPrintButton)
            .setOnClickListener {
                savePreferences()
                remove()
                onClosed()
            }
        dialogView.findViewById<View>(R.id.cancelLabelPrintButton)
            .setOnClickListener {
                savePreferences()
                remove()
                onClosed()
            }

        val printButton = dialogView.findViewById<Button>(
            R.id.confirmLabelPrintButton
        )

        printButton.text =
            if (product == null) "SCANSIONA" else "STAMPA"

        printButton.setOnClickListener {
            val quantity = quantityEdit.text.toString().toIntOrNull()

            if (quantity == null || quantity !in 1..100) {
                quantityEdit.error = "Quantità da 1 a 100"
                return@setOnClickListener
            }

            val printer = if (epson.isChecked) "EPSON" else "GODEX"
            val template = selectedTemplate()

            if (template == "NOTE" && noteEditText.text.toString().trim().isEmpty()) {
                noteEditText.error = "Inserire una nota"
                noteEditText.requestFocus()
                return@setOnClickListener
            }

            if (printer != "GODEX") {
                Toast.makeText(
                    context,
                    "La stampa Epson è in preparazione",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            savePreferences()

            if (product == null) {
                remove()
                onScanRequested()
                return@setOnClickListener
            }

            printButton.isEnabled = false
            val printableProduct = product

            Thread {
                val result = gatewayApiClient.printLabel(
                    articleCode = printableProduct.articleCode,
                    description = printableProduct.description,
                    barcode = printableProduct.barcode,
                    publicPrice = printableProduct.publicPrice,
                    quantity = quantity,
                    printer = printer,
                    template = template,
                    note = if (template == "NOTE") {
                        noteEditText.text.toString().trim()
                    } else {
                        ""
                    }
                )

                dialogView.post {
                    printButton.isEnabled = true

                    result.onSuccess {
                        Toast.makeText(
                            context,
                            "$quantity etichette inviate alla GoDEX",
                            Toast.LENGTH_SHORT
                        ).show()
                        savePreferences()
                        remove()
                        onClosed()
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            "Errore stampa: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()
        }

        dialogView.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 22 * density
        }
        dialogView.clipToOutline = true
        dialogView.outlineProvider = ViewOutlineProvider.BACKGROUND

        val dialogWidth = min(
            (410 * density).toInt(),
            screenWidth - (40 * density).toInt()
        )

        root.addView(
            dialogView,
            FrameLayout.LayoutParams(
                dialogWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        overlayRoot = root
        windowManager.addView(
            root,
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
            )
        )
    }

    fun remove() {
        val popup = overlayRoot ?: return
        try {
            windowManager.removeView(popup)
        } catch (_: Exception) {
        }
        overlayRoot = null
    }

    private fun bindQuantityButtons(
        minus: Button,
        plus: Button,
        editText: EditText
    ) {
        minus.setOnClickListener {
            changeQuantity(editText, -1)
        }
        plus.setOnClickListener {
            changeQuantity(editText, 1)
        }
    }

    private fun changeQuantity(
        editText: EditText,
        delta: Int
    ) {
        val current = editText.text.toString().toIntOrNull() ?: 1
        val updated = max(1, min(100, current + delta))
        editText.setText(updated.toString())
        editText.setSelection(editText.text.length)
    }
}