package com.scan2enter.overlay.popup

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
    private val gatewayApiClient = GatewayApiClient()
    private var overlayRoot: View? = null

    fun show(
        product: ProductInfo,
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
            listOf(product.articleCode, product.description)
                .filter { it.isNotBlank() }
                .joinToString(" · ")

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

        fun updateNoteEditorVisibility() {
            noteEditorContainer.visibility =
                if (note.isChecked) View.VISIBLE else View.GONE
        }

        note.setOnCheckedChangeListener { _, _ ->
            updateNoteEditorVisibility()
        }

        updateNoteEditorVisibility()

        bindQuantityButtons(
            dialogView.findViewById(R.id.labelQuantityMinusButton),
            dialogView.findViewById(R.id.labelQuantityPlusButton),
            quantityEdit
        )

        dialogView.findViewById<View>(R.id.closeLabelPrintButton)
            .setOnClickListener {
                remove()
                onClosed()
            }
        dialogView.findViewById<View>(R.id.cancelLabelPrintButton)
            .setOnClickListener {
                remove()
                onClosed()
            }

        val printButton = dialogView.findViewById<Button>(
            R.id.confirmLabelPrintButton
        )

        printButton.setOnClickListener {
            val quantity = quantityEdit.text.toString().toIntOrNull()

            if (quantity == null || quantity !in 1..100) {
                quantityEdit.error = "Quantità da 1 a 100"
                return@setOnClickListener
            }

            val printer = if (epson.isChecked) "EPSON" else "GODEX"
            val template = when {
                image.isChecked -> "IMAGE"
                price.isChecked -> "PRICE"
                note.isChecked -> "NOTE"
                standard.isChecked -> "STANDARD"
                else -> "STANDARD"
            }

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

            if (template == "NOTE") {
                Toast.makeText(
                    context,
                    "La stampa della nota personalizzata è in preparazione",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            printButton.isEnabled = false

            Thread {
                val result = gatewayApiClient.printLabel(
                    articleCode = product.articleCode,
                    description = product.description,
                    barcode = product.barcode,
                    publicPrice = product.publicPrice,
                    quantity = quantity,
                    printer = printer,
                    template = template
                )

                dialogView.post {
                    printButton.isEnabled = true

                    result.onSuccess {
                        Toast.makeText(
                            context,
                            "$quantity etichette inviate alla GoDEX",
                            Toast.LENGTH_SHORT
                        ).show()
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