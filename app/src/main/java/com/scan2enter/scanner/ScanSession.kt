package com.scan2enter.scanner

import android.content.Context
import android.content.Intent
import android.util.Log
import com.scan2enter.api.DueRetailApiClient
import com.scan2enter.data.ScanStorage
import com.scan2enter.feedback.ScanFeedbackManager
import com.scan2enter.model.ProductInfoStore
import com.scan2enter.overlay.OverlayService
import com.scan2enter.repository.ProductRepository

class ScanSession(
    private val context: Context
) {

    companion object {
        private const val TAG = "Scan2Enter"

        /*
         * Credenziali tecniche già verificate con DueRetailApiTest.
         *
         * In un passaggio successivo potranno essere spostate fuori dal
         * sorgente, per esempio in local.properties / BuildConfig.
         */
        private const val API_USERNAME = "2bit@2bit.it"
        private const val API_PASSWORD = "2bit"
    }

    private val productRepository by lazy {
        ProductRepository(
            DueRetailApiClient(
                username = API_USERNAME,
                password = API_PASSWORD
            )
        )
    }

    @Volatile
    private var running = false

    fun start() {
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

        Log.d(TAG, "Barcode = $barcode")

        ScanFeedbackManager.beep()

        /*
         * La fotocamera può essere chiusa subito.
         * La richiesta API continua su un thread separato.
         */
        onCompleted()

        Thread {
            Log.d(TAG, "API PRODUCT LOOKUP START barcode=$barcode")

            productRepository.getProduct(barcode)
                .onSuccess { productInfo ->
                    ProductInfoStore.initialize(
                        context.applicationContext
                    )

                    ProductInfoStore.current = productInfo
                    ProductInfoStore.addToHistory(productInfo)

                    context.startService(
                        Intent(
                            context,
                            OverlayService::class.java
                        ).apply {
                            action =
                                OverlayService.ACTION_SHOW_PRODUCT_INFO

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
                        "PRODUCT INFO API SALVATO NELLO STORE E NELLA CRONOLOGIA"
                    )
                }
                .onFailure { error ->
                    Log.e(
                        TAG,
                        "API PRODUCT LOOKUP ERROR - AVVIO FALLBACK ACCESSIBILITY",
                        error
                    )

                    startAccessibilityFallback(barcode)
                }
        }.start()
    }

    /**
     * Mantiene invariato il vecchio workflow.
     *
     * Viene eseguito soltanto quando la richiesta API fallisce:
     * broadcast verso Due Retail + ScanStorage per Accessibility.
     */
    private fun startAccessibilityFallback(barcode: String) {
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
            "FALLBACK ACCESSIBILITY PREPARATO barcode=$barcode"
        )
    }

}