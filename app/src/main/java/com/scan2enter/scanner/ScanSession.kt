package com.scan2enter.scanner

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.scan2enter.data.ScanStorage
import com.scan2enter.feedback.ScanFeedbackManager
import com.scan2enter.model.ProductInfoStore
import com.scan2enter.overlay.OverlayService
import com.scan2enter.reorder.ReorderStore
import com.scan2enter.repository.ProductRepositoryProvider

class ScanSession(
    private val context: Context
) {

    companion object {
        private const val TAG = "Scan2Enter"

        private const val WORKFLOW_PREFS = "scan_workflow"
        private const val WORKFLOW_MODE_KEY = "mode"

        private const val MODE_INFO = "INFO"
        private const val MODE_FAST_PACKAGE = "COLLO_VELOCE"
        private const val MODE_LABELS = "ETICHETTE"
        private const val MODE_LABELS_GODEX = "ETICHETTE_GODEX"
    }

    private val productRepository by lazy {
        ProductRepositoryProvider.get(context.applicationContext)
    }

    @Volatile
    private var running = false

    fun start() {
        ScanFeedbackManager.initialize(
            context.applicationContext
        )

        running = true
        Log.d(TAG, "ScanSession START")
    }

    fun stop() {
        running = false
        Log.d(TAG, "ScanSession STOP")
    }

    fun onBarcodeRead(
        barcode: String,
        onCompleted: () -> Unit
    ) {
        if (!running) {
            return
        }

        running = false

        val normalizedBarcode = barcode.trim()

        Log.d(TAG, "Barcode grezzo = $barcode")
        Log.d(TAG, "Barcode normalizzato = $normalizedBarcode")

        if (!isValidEan13(normalizedBarcode)) {
            onCompleted()

            Log.d(
                TAG,
                "LETTURA RIFIUTATA - NON EAN13 VALIDO: $normalizedBarcode"
            )

            showScanError(
                "Codice non valido o QR rilevato. Riprovare."
            )
            return
        }

        onCompleted()

        val scanMode = loadCurrentScanMode()

        Log.d(
            TAG,
            "BARCODE ROUTING mode=$scanMode barcode=$normalizedBarcode"
        )

        if (
            scanMode == MODE_FAST_PACKAGE ||
            scanMode == MODE_LABELS
        ) {
            sendBarcodeToAccessibility(
                barcode = normalizedBarcode,
                scanMode = scanMode
            )
            return
        }

        if (scanMode == MODE_INFO) {
            sendBarcodeToAccessibility(
                barcode = normalizedBarcode,
                scanMode = scanMode
            )
        }

        Thread {
            Log.d(
                TAG,
                "API PRODUCT LOOKUP START barcode=$normalizedBarcode"
            )

            productRepository
                .getProduct(normalizedBarcode)
                .onSuccess { productInfo ->
                    ProductInfoStore.initialize(
                        context.applicationContext
                    )

                    Log.d(TAG, "GIACENZA = ${productInfo.stock}")
                    Log.d(
                        TAG,
                        "DISPONIBILE = ${productInfo.availableStock}"
                    )
                    Log.d(
                        TAG,
                        "SCORTA MINIMA = ${productInfo.minimumStock}"
                    )
                    Log.d(
                        TAG,
                        "SCORTA MASSIMA = ${productInfo.maximumStock}"
                    )
                    Log.d(
                        TAG,
                        "LOTTO RIORDINO = ${productInfo.reorderLot}"
                    )

                    ProductInfoStore.current = productInfo
                    ProductInfoStore.addToHistory(productInfo)

                    ReorderStore.add(productInfo)

                    context.startService(
                        Intent(
                            context,
                            OverlayService::class.java
                        ).apply {
                            action = if (scanMode == MODE_LABELS_GODEX) {
                                OverlayService.ACTION_SHOW_GODEX_PRINT
                            } else {
                                OverlayService.ACTION_SHOW_PRODUCT_INFO
                            }

                            putExtra(
                                OverlayService.EXTRA_WORKFLOW_COMPLETED,
                                true
                            )
                        }
                    )

                    Log.d(
                        TAG,
                        "API PRODUCT LOOKUP OK " +
                                "code=${productInfo.articleCode} " +
                                "ean=${productInfo.barcode}"
                    )

                    Log.d(
                        TAG,
                        "PRODUCT INFO API SALVATO NELLO STORE " +
                                "E NELLA CRONOLOGIA"
                    )
                }
                .onFailure { error ->
                    handleApiError(error)
                }
        }.start()
    }

    private fun handleApiError(error: Throwable) {
        Log.e(
            TAG,
            "API PRODUCT LOOKUP ERROR - WORKFLOW TERMINATO",
            error
        )

        Handler(Looper.getMainLooper()).post {
            showScanError(
                "Articolo non trovato. Riprovare la lettura."
            )
        }
    }

    private fun isValidEan13(value: String): Boolean {
        if (value.length != 13 || !value.all(Char::isDigit)) {
            return false
        }

        val expectedCheckDigit = value.last().digitToInt()
        var sum = 0

        for (index in 0 until 12) {
            val digit = value[index].digitToInt()
            sum += if (index % 2 == 0) {
                digit
            } else {
                digit * 3
            }
        }

        val calculatedCheckDigit = (10 - (sum % 10)) % 10
        return calculatedCheckDigit == expectedCheckDigit
    }

    private fun showScanError(message: String) {
        context.startService(
            Intent(
                context,
                OverlayService::class.java
            ).apply {
                action = OverlayService.ACTION_SHOW_SCAN_ERROR

                putExtra(
                    OverlayService.EXTRA_SCAN_ERROR_MESSAGE,
                    message
                )
            }
        )
    }

    private fun loadCurrentScanMode(): String {
        return context.applicationContext
            .getSharedPreferences(
                WORKFLOW_PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                WORKFLOW_MODE_KEY,
                MODE_INFO
            )
            ?: MODE_INFO
    }

    private fun sendBarcodeToAccessibility(
        barcode: String,
        scanMode: String
    ) {
        BarcodeIntentSender.send(
            context,
            barcode
        )

        ScanStorage.save(
            context,
            barcode
        )

        Log.d(
            TAG,
            "ACCESSIBILITY PREPARATA " +
                    "mode=$scanMode barcode=$barcode"
        )
    }
}