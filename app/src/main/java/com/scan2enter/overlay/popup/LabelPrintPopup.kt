package com.scan2enter.overlay.popup

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.scan2enter.R
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.model.ProductInfo
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

class LabelPrintPopup(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private companion object {
        const val PREFS_NAME = "godex_label_preferences"
        const val PREF_TEMPLATE = "template"
        const val PREF_QUANTITY = "quantity"
        const val PREF_NOTE_PREFIX = "note_article_"
    }

    private val gatewayApiClient = GatewayApiClient()
    private var overlayRoot: View? = null

    private fun notePreferenceKey(product: ProductInfo?): String? {
        val articleId = product?.articleId ?: 0L
        return if (articleId > 0L) "$PREF_NOTE_PREFIX$articleId" else null
    }

    fun show(
        product: ProductInfo?,
        onSearchRequested: () -> Unit = {},
        onHardwareScanRequested: () -> Unit = {},
        onClosed: () -> Unit = {},
        onPrintSuccess: () -> Unit = {}
    ) {
        if (overlayRoot != null) return

        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
        }

        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.label_print_dialog, root, false)

        val articleText = dialogView.findViewById<TextView>(
            R.id.labelPrintArticleText
        )
        val standard = dialogView.findViewById<TextView>(
            R.id.standardLabelRadioButton
        )
        val image = dialogView.findViewById<TextView>(
            R.id.imageLabelRadioButton
        )
        val price = dialogView.findViewById<TextView>(
            R.id.priceLabelRadioButton
        )
        val note = dialogView.findViewById<TextView>(
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
        val previewCode = dialogView.findViewById<TextView>(
            R.id.labelPreviewCode
        )
        val previewDescription = dialogView.findViewById<TextView>(
            R.id.labelPreviewDescription
        )
        val previewBarcode = dialogView.findViewById<TextView>(
            R.id.labelPreviewBarcode
        )
        val previewExtra = dialogView.findViewById<TextView>(
            R.id.labelPreviewExtra
        )


        articleText.text =
            if (product == null) {
                "Nessun articolo selezionato"
            } else {
                listOf(product.articleCode, product.description)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            }

        val preferences = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        var currentTemplate =
            preferences.getString(PREF_TEMPLATE, "STANDARD") ?: "STANDARD"

        quantityEdit.setText(
            preferences.getInt(PREF_QUANTITY, 1)
                .coerceIn(1, 100)
                .toString()
        )

        val noteKey = notePreferenceKey(product)
        noteEditText.setText(
            noteKey?.let { preferences.getString(it, "") }.orEmpty()
        )
        noteEditText.setSelection(noteEditText.text.length)

        fun selectedTemplate(): String = currentTemplate

        fun savePreferences() {
            val editor = preferences.edit()
                .putString(PREF_TEMPLATE, selectedTemplate())
                .putInt(
                    PREF_QUANTITY,
                    quantityEdit.text.toString()
                        .toIntOrNull()
                        ?.coerceIn(1, 100)
                        ?: 1
                )

            notePreferenceKey(product)?.let { key ->
                editor.putString(
                    key,
                    noteEditText.text.toString().take(80)
                )
            }

            editor.apply()
        }

        fun updatePreview() {
            if (product == null) {
                previewCode.text = "SELEZIONA UN ARTICOLO"
                previewDescription.text =
                    "Usa il lettore oppure CERCA ARTICOLO"
                previewBarcode.text = "|||| ||||| ||||"
                previewExtra.text = ""
                return
            }

            previewCode.text =
                product.articleCode.ifBlank { "CODICE" }

            previewDescription.text =
                product.description.ifBlank { "Descrizione articolo" }

            previewBarcode.text =
                if (product.barcode.isBlank()) {
                    "|||| ||||| ||||"
                } else {
                    "|||| ||||| ||||   ${product.barcode}"
                }

            previewExtra.text = when (selectedTemplate()) {
                "PRICE" -> {
                    val rawValue = product.publicPrice.trim()

                    val numericValue =
                        rawValue
                            .replace("€", "")
                            .replace(" ", "")
                            .replace(",", ".")
                            .toDoubleOrNull()

                    if (numericValue == null) {
                        if (rawValue.isBlank()) {
                            "PREZZO —"
                        } else {
                            "€ $rawValue"
                        }
                    } else {
                        String.format(
                            Locale.ITALY,
                            "€ %.2f",
                            numericValue
                        )
                    }
                }

                "IMAGE" -> "▣  IMMAGINE"
                "NOTE" -> noteEditText.text.toString()
                    .trim()
                    .ifBlank { "NOTA PERSONALIZZATA" }

                else -> ""
            }
        }

        fun updateNoteEditorVisibility() {
            noteEditorContainer.visibility =
                if (currentTemplate == "NOTE") View.VISIBLE else View.GONE
        }

        fun styleTemplateCard(view: TextView, selected: Boolean) {
            view.background = GradientDrawable().apply {
                setColor(
                    if (selected) Color.parseColor("#F4D000")
                    else Color.parseColor("#F1F3F2")
                )
                cornerRadius = 14 * density
                setStroke(
                    if (selected) (2 * density).toInt() else (1 * density).toInt(),
                    if (selected) Color.BLACK else Color.parseColor("#CCD3CF")
                )
            }
            view.setTextColor(Color.BLACK)
            view.alpha = if (selected) 1.0f else 0.72f
        }

        fun updateTemplateCards() {
            styleTemplateCard(standard, currentTemplate == "STANDARD")
            styleTemplateCard(price, currentTemplate == "PRICE")
            styleTemplateCard(image, currentTemplate == "IMAGE")
            styleTemplateCard(note, currentTemplate == "NOTE")
        }

        fun selectTemplate(template: String) {
            currentTemplate = template
            updateTemplateCards()
            updateNoteEditorVisibility()
            updatePreview()
            savePreferences()
        }

        val hardwareScanKeyListener = View.OnKeyListener { _, keyCode, event ->
            val isVolumeTrigger =
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                        keyCode == KeyEvent.KEYCODE_VOLUME_UP

            when {
                keyCode == KeyEvent.KEYCODE_BACK -> {
                    if (
                        event.action == KeyEvent.ACTION_DOWN &&
                        event.repeatCount == 0
                    ) {
                        savePreferences()
                        remove()
                        onClosed()
                    }

                    true
                }

                isVolumeTrigger -> {
                    if (
                        event.action == KeyEvent.ACTION_DOWN &&
                        event.repeatCount == 0
                    ) {
                        savePreferences()
                        remove()
                        onHardwareScanRequested()
                    }

                    true
                }

                else -> false
            }
        }

        root.isFocusableInTouchMode = true
        root.setOnKeyListener(hardwareScanKeyListener)
        quantityEdit.setOnKeyListener(hardwareScanKeyListener)
        noteEditText.setOnKeyListener(hardwareScanKeyListener)
        root.requestFocus()


        standard.setOnClickListener { selectTemplate("STANDARD") }
        price.setOnClickListener { selectTemplate("PRICE") }
        image.setOnClickListener { selectTemplate("IMAGE") }
        note.setOnClickListener { selectTemplate("NOTE") }

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
                    updatePreview()
                    savePreferences()
                }

                override fun afterTextChanged(editable: Editable?) = Unit
            }
        )

        updateTemplateCards()
        updateNoteEditorVisibility()
        updatePreview()

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


        dialogView.findViewById<Button>(R.id.searchLabelArticleButton)
            .setOnClickListener {
                savePreferences()
                remove()
                onSearchRequested()
            }

        val printButton = dialogView.findViewById<Button>(
            R.id.confirmLabelPrintButton
        )

        printButton.isEnabled = product != null

        printButton.setOnClickListener {
            val printableProduct = product ?: return@setOnClickListener
            val quantity = quantityEdit.text.toString().toIntOrNull()

            if (quantity == null || quantity !in 1..100) {
                quantityEdit.error = "Quantità da 1 a 100"
                return@setOnClickListener
            }

            val template = selectedTemplate()

            if (
                template == "NOTE" &&
                noteEditText.text.toString().trim().isEmpty()
            ) {
                noteEditText.error = "Inserire una nota"
                noteEditText.requestFocus()
                return@setOnClickListener
            }

            savePreferences()
            printButton.isEnabled = false

            Thread {
                val result = gatewayApiClient.printLabel(
                    articleCode = printableProduct.articleCode,
                    description = printableProduct.description,
                    barcode = printableProduct.barcode,
                    publicPrice = printableProduct.publicPrice,
                    quantity = quantity,
                    printer = "GODEX",
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
                        val normalText = "STAMPA ETICHETTA"
                        printButton.text = "✓  INVIATA ALLA GODEX"
                        printButton.postDelayed(
                            {
                                printButton.text = normalText
                            },
                            900L
                        )

                        Toast.makeText(
                            context,
                            "$quantity etichette inviate alla GoDEX",
                            Toast.LENGTH_SHORT
                        ).show()

                        remove()
                        onPrintSuccess()
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
            (430 * density).toInt(),
            screenWidth - (28 * density).toInt()
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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1.0f
        }

        overlayRoot = root
        windowManager.addView(root, params)
    }

    fun remove() {
        val root = overlayRoot ?: return
        runCatching { windowManager.removeView(root) }
        overlayRoot = null
    }

    private fun bindQuantityButtons(
        minusButton: Button,
        plusButton: Button,
        quantityEdit: EditText
    ) {
        minusButton.setOnClickListener {
            updateQuantity(quantityEdit, -1)
        }

        plusButton.setOnClickListener {
            updateQuantity(quantityEdit, 1)
        }
    }

    private fun updateQuantity(
        editText: EditText,
        delta: Int
    ) {
        val current = editText.text.toString().toIntOrNull() ?: 1
        val updated = max(1, min(100, current + delta))
        editText.setText(updated.toString())
        editText.setSelection(editText.text.length)
    }
}
