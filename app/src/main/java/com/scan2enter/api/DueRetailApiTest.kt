package com.scan2enter.api

import android.util.Log

object DueRetailApiTest {

    private const val TAG = "DueRetailApi"

    /*
     * Inserisci qui, SOLO nel progetto locale, le stesse credenziali
     * utilizzate per accedere a Due Retail Mobile.
     *
     * Non pubblicare questo file su Git con la password compilata.
     */
    private const val USERNAME = "2bit@2bit.it"
    private const val PASSWORD = "2bit"

    fun run() {
        Thread {
            val client = DueRetailApiClient(
                username = USERNAME,
                password = PASSWORD
            )

            client.getProductByBarcode("8002369011767")
                .onSuccess { product ->
                    Log.d(TAG, "API OK")
                    Log.d(TAG, "ID = ${product.id}")
                    Log.d(TAG, "CODICE = ${product.articleCode}")
                    Log.d(TAG, "DESCRIZIONE = ${product.description}")
                    Log.d(TAG, "BARCODE = ${product.barcode}")
                    Log.d(TAG, "PREZZO PUBBLICO = ${product.publicPrice}")
                    Log.d(TAG, "IMPONIBILE = ${product.taxablePrice}")
                    Log.d(TAG, "IVA = ${product.vatRate}")
                    Log.d(TAG, "ANNO = ${product.year}")
                    Log.d(TAG, "STAGIONE = ${product.season}")
                    Log.d(TAG, "GIACENZA = ${product.stock}")
                    Log.d(TAG, "DISPONIBILE = ${product.availableStock}")
                    Log.d(TAG, "SCORTA MINIMA = ${product.minimumStock}")
                    Log.d(TAG, "SCORTA MASSIMA = ${product.maximumStock}")
                    Log.d(TAG, "LOTTO RIORDINO = ${product.reorderLot}")
                }
                .onFailure { error ->
                    Log.e(TAG, "API ERROR", error)
                }
        }.start()
    }
}