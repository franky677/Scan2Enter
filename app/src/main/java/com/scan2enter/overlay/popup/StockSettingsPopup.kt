package com.scan2enter.overlay.popup

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
import com.scan2enter.model.ProductInfo
import kotlin.math.max
import kotlin.math.min
import android.widget.ImageView
import com.scan2enter.favorites.FavoriteRepository
import com.scan2enter.api.GatewayApiClient
import java.util.concurrent.Executors
/**
 * Finestra overlay dedicata alla modifica di scorta minima e lotto di riordino.
 *
 * Questa classe gestisce esclusivamente la UI: creazione, validazione,
 * pulsanti quantità e stato di salvataggio. La persistenza rimane al chiamante.
 */
class StockSettingsPopup(
    private val context: Context,
    private val windowManager: WindowManager
) {

    private val labelPrintPopup by lazy {
        LabelPrintPopup(context, windowManager)
    }

    private val gatewayApiClient by lazy {
        GatewayApiClient()
    }

    private val activeExecutor =
        Executors.newSingleThreadExecutor()

    private var overlayRoot: View? = null

    fun isShowing(): Boolean = overlayRoot != null

    fun show(
        product: ProductInfo,
        onSave: (
            minimumStock: Double,
            reorderLot: Double,
            complete: (Result<Unit>) -> Unit
        ) -> Unit,
        onClose: () -> Unit
    ) {
        if (overlayRoot != null) return

        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels

        val root =
            object : FrameLayout(context) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (
                        event.keyCode == KeyEvent.KEYCODE_BACK &&
                        event.action == KeyEvent.ACTION_DOWN &&
                        event.repeatCount == 0
                    ) {
                        onClose()
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }.apply {
                setBackgroundColor(Color.BLACK)
                alpha = 1.0f
                isFocusable = true
                isFocusableInTouchMode = true
            }

        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.stock_edit_dialog, root, false)
        val favoriteButton =
            dialogView.findViewById<ImageView>(
                R.id.stockEditFavoriteButton
            )

        fun updateFavoriteIcon() {
            val isFavorite =
                product.articleId > 0L &&
                        FavoriteRepository.isFavorite(product.articleId)

            favoriteButton.setImageResource(
                if (isFavorite) {
                    R.drawable.ic_star
                } else {
                    R.drawable.ic_star_border
                }
            )

            favoriteButton.contentDescription =
                if (isFavorite) {
                    "Rimuovi dai preferiti"
                } else {
                    "Aggiungi ai preferiti"
                }
        }

        favoriteButton.setOnClickListener {
            if (product.articleId <= 0L) {
                Toast.makeText(
                    context,
                    "Articolo non disponibile",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val isNowFavorite =
                FavoriteRepository.toggle(product)

            favoriteButton.setImageResource(
                if (isNowFavorite) {
                    R.drawable.ic_star
                } else {
                    R.drawable.ic_star_border
                }
            )

            favoriteButton.contentDescription =
                if (isNowFavorite) {
                    "Rimuovi dai preferiti"
                } else {
                    "Aggiungi ai preferiti"
                }

            Toast.makeText(
                context,
                if (isNowFavorite) {
                    "Articolo aggiunto ai preferiti"
                } else {
                    "Articolo rimosso dai preferiti"
                },
                Toast.LENGTH_SHORT
            ).show()
        }

        updateFavoriteIcon()

        val activeButton =
            dialogView.findViewById<Button>(
                R.id.productActiveButton
            )

        var currentActive = product.active

        fun updateActiveButton() {
            activeButton.text =
                if (currentActive) {
                    "⛔  BLOCCA ARTICOLO"
                } else {
                    "✅  SBLOCCA ARTICOLO"
                }

            activeButton.contentDescription =
                if (currentActive) {
                    "Blocca articolo"
                } else {
                    "Sblocca articolo"
                }
        }

        updateActiveButton()

        activeButton.setOnClickListener {
            if (product.articleId <= 0L) {
                Toast.makeText(
                    context,
                    "Articolo non disponibile",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val newActive = !currentActive
            activeButton.isEnabled = false

            activeExecutor.execute {
                gatewayApiClient
                    .updateProductActive(
                        articleId = product.articleId,
                        active = newActive
                    )
                    .onSuccess {
                        currentActive = newActive

                        activeButton.post {
                            if (overlayRoot == null) {
                                return@post
                            }

                            updateActiveButton()
                            activeButton.isEnabled = true

                            Toast.makeText(
                                context,
                                if (currentActive) {
                                    "Articolo sbloccato"
                                } else {
                                    "Articolo bloccato"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .onFailure { error ->
                        activeButton.post {
                            if (overlayRoot == null) {
                                return@post
                            }

                            activeButton.isEnabled = true

                            Toast.makeText(
                                context,
                                "Errore: ${error.message ?: "aggiornamento non riuscito"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
        }

        dialogView.findViewById<TextView>(R.id.stockEditArticleText).text =
            listOf(product.articleCode, product.description)
                .filter { it.isNotBlank() }
                .joinToString(" · ")

        dialogView.findViewById<TextView>(R.id.stockEditCurrentStockText).text =
            product.stock.trim().takeIf { it.isNotEmpty() && it != "-1" } ?: "—"

        val minimumEdit =
            dialogView.findViewById<EditText>(R.id.minimumStockEditText)
        val reorderEdit =
            dialogView.findViewById<EditText>(R.id.reorderLotEditText)

        minimumEdit.setText(product.minimumStock.ifBlank { "0" })
        reorderEdit.setText(product.reorderLot.ifBlank { "0" })

        bindQuantityButtons(
            minusButton = dialogView.findViewById(R.id.minimumStockMinusButton),
            plusButton = dialogView.findViewById(R.id.minimumStockPlusButton),
            editText = minimumEdit
        )

        bindQuantityButtons(
            minusButton = dialogView.findViewById(R.id.reorderLotMinusButton),
            plusButton = dialogView.findViewById(R.id.reorderLotPlusButton),
            editText = reorderEdit
        )

        dialogView.findViewById<View>(R.id.closeStockEditButton)
            .setOnClickListener { onClose() }

        dialogView.findViewById<View>(R.id.cancelStockEditButton)
            .setOnClickListener { onClose() }

        dialogView.findViewById<View>(R.id.printLabelButton)
            .setOnClickListener {
                labelPrintPopup.show(
                    product = product,
                    onPrintSuccess = {
                        remove()
                        onClose()
                    }
                )
            }

        val saveButton = dialogView.findViewById<View>(R.id.saveStockEditButton)

        saveButton.setOnClickListener {
            val minimumStock = minimumEdit.text
                ?.toString()
                ?.trim()
                ?.replace(',', '.')
                ?.toDoubleOrNull()

            val reorderLot = reorderEdit.text
                ?.toString()
                ?.trim()
                ?.replace(',', '.')
                ?.toDoubleOrNull()

            when {
                product.articleId <= 0L -> Toast.makeText(
                    context,
                    "ID articolo non disponibile. Ripetere la scansione.",
                    Toast.LENGTH_LONG
                ).show()

                minimumStock == null || minimumStock < 0.0 -> {
                    minimumEdit.error = "Inserire un valore valido"
                    minimumEdit.requestFocus()
                }

                reorderLot == null || reorderLot < 0.0 -> {
                    reorderEdit.error = "Inserire un valore valido"
                    reorderEdit.requestFocus()
                }

                else -> {
                    setSavingState(
                        saving = true,
                        saveButton = saveButton,
                        minimumEdit = minimumEdit,
                        reorderEdit = reorderEdit
                    )

                    onSave(minimumStock, reorderLot) { result ->
                        if (result.isFailure && overlayRoot != null) {
                            setSavingState(
                                saving = false,
                                saveButton = saveButton,
                                minimumEdit = minimumEdit,
                                reorderEdit = reorderEdit
                            )
                        }
                    }
                }
            }
        }

        val horizontalMargin = (24 * density).toInt()
        val dialogWidth = min(
            (390 * density).toInt(),
            screenWidth - horizontalMargin * 2
        )

        dialogView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = 22 * density
        }
        dialogView.clipToOutline = true
        dialogView.outlineProvider = ViewOutlineProvider.BACKGROUND
        dialogView.elevation = 16 * density

        val dialogParams = FrameLayout.LayoutParams(
            dialogWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        root.addView(dialogView, dialogParams)

        val windowParams = WindowManager.LayoutParams(
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
        windowManager.addView(root, windowParams)
        root.requestFocus()
    }

    fun remove() {
        labelPrintPopup.remove()

        val popup = overlayRoot ?: return

        try {
            windowManager.removeView(popup)
        } catch (_: Exception) {
        }

        overlayRoot = null
    }

    private fun bindQuantityButtons(
        minusButton: Button,
        plusButton: Button,
        editText: EditText
    ) {
        minusButton.setOnClickListener {
            changeQuantity(editText, -1.0)
        }
        plusButton.setOnClickListener {
            changeQuantity(editText, 1.0)
        }
    }

    private fun changeQuantity(
        editText: EditText,
        delta: Double
    ) {
        val current = editText.text
            ?.toString()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?: 0.0

        val updated = max(0.0, current + delta)
        editText.setText(
            if (updated == updated.toInt().toDouble()) {
                updated.toInt().toString()
            } else {
                updated.toString()
            }
        )
        editText.setSelection(editText.text.length)
    }

    private fun setSavingState(
        saving: Boolean,
        saveButton: View,
        minimumEdit: EditText,
        reorderEdit: EditText
    ) {
        saveButton.isEnabled = !saving
        minimumEdit.isEnabled = !saving
        reorderEdit.isEnabled = !saving
    }
}