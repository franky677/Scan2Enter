package com.scan2enter.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

object ProductInfoStore {

    private const val MAX_HISTORY_SIZE = 20

    private const val PREFS_NAME =
        "product_info_history"

    private const val KEY_HISTORY =
        "history_json"

    private val historyLock = Any()
    private val history = ArrayDeque<ProductInfo>()

    @Volatile
    private var initialized = false

    private var applicationContext: Context? = null

    @Volatile
    var current: ProductInfo? = null

    /**
     * Carica una sola volta la cronologia salvata sul dispositivo.
     *
     * Deve essere chiamato all'avvio del servizio overlay.
     */
    fun initialize(context: Context) {
        if (initialized) {
            return
        }

        synchronized(historyLock) {
            if (initialized) {
                return
            }

            applicationContext =
                context.applicationContext

            loadHistoryLocked()

            initialized = true
        }
    }

    /**
     * Aggiunge un articolo alla cronologia.
     *
     * La modifica della lista avviene subito in RAM.
     * Il salvataggio su disco usa SharedPreferences.apply(),
     * quindi non blocca il workflow di scansione.
     */
    fun addToHistory(product: ProductInfo) {
        if (!isValidHistoryItem(product)) {
            return
        }

        synchronized(historyLock) {
            val latest = history.peekFirst()

            if (
                latest != null &&
                representsSameProduct(latest, product)
            ) {
                return
            }

            history.addFirst(product)

            while (history.size > MAX_HISTORY_SIZE) {
                history.removeLast()
            }

            saveHistoryLocked()
        }
    }

    /**
     * Restituisce una copia della cronologia,
     * dal più recente al meno recente.
     */
    fun getHistory(): List<ProductInfo> =
        synchronized(historyLock) {
            history.toList()
        }

    /**
     * Cancella sia la cronologia in RAM
     * sia quella salvata sul dispositivo.
     */
    fun clearHistory() {
        synchronized(historyLock) {
            history.clear()
            saveHistoryLocked()
        }
    }

    private fun loadHistoryLocked() {
        val context =
            applicationContext ?: return

        val json =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            ).getString(
                KEY_HISTORY,
                null
            ) ?: return

        try {
            val array = JSONArray(json)

            history.clear()

            for (index in 0 until array.length()) {
                val item =
                    array.optJSONObject(index)
                        ?: continue

                val product =
                    ProductInfo(
                        articleCode =
                            item.optString("articleCode"),
                        description =
                            item.optString("description"),
                        barcode =
                            item.optString("barcode"),
                        taxablePrice =
                            item.optString("taxablePrice"),
                        vatRate =
                            item.optString("vatRate"),
                        publicPrice =
                            item.optString("publicPrice"),
                        season =
                            item.optString("season"),
                        year =
                            item.optString("year"),
                        stock =
                            item.optString("stock")
                    )

                if (isValidHistoryItem(product)) {
                    history.addLast(product)
                }

                if (history.size >= MAX_HISTORY_SIZE) {
                    break
                }
            }
        } catch (_: Exception) {
            history.clear()
        }
    }

    private fun saveHistoryLocked() {
        val context =
            applicationContext ?: return

        val array = JSONArray()

        history.forEach { product ->
            array.put(
                JSONObject().apply {
                    put(
                        "articleCode",
                        product.articleCode
                    )
                    put(
                        "description",
                        product.description
                    )
                    put(
                        "barcode",
                        product.barcode
                    )
                    put(
                        "taxablePrice",
                        product.taxablePrice
                    )
                    put(
                        "vatRate",
                        product.vatRate
                    )
                    put(
                        "publicPrice",
                        product.publicPrice
                    )
                    put(
                        "season",
                        product.season
                    )
                    put(
                        "year",
                        product.year
                    )
                    put(
                        "stock",
                        product.stock
                    )
                }
            )
        }

        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(
                KEY_HISTORY,
                array.toString()
            )
            .apply()
    }

    private fun isValidHistoryItem(
        product: ProductInfo
    ): Boolean =
        product.barcode.isNotBlank() ||
                product.articleCode.isNotBlank() ||
                product.description.isNotBlank()

    private fun representsSameProduct(
        first: ProductInfo,
        second: ProductInfo
    ): Boolean {
        val firstBarcode =
            first.barcode.trim()

        val secondBarcode =
            second.barcode.trim()

        if (
            firstBarcode.isNotEmpty() &&
            secondBarcode.isNotEmpty()
        ) {
            return firstBarcode == secondBarcode
        }

        val firstArticleCode =
            first.articleCode.trim()

        val secondArticleCode =
            second.articleCode.trim()

        return firstArticleCode.isNotEmpty() &&
                secondArticleCode.isNotEmpty() &&
                firstArticleCode == secondArticleCode
    }
}