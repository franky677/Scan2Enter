package com.scan2enter.model

import java.util.ArrayDeque

object ProductInfoStore {

    private const val MAX_HISTORY_SIZE = 20

    private val historyLock = Any()
    private val history = ArrayDeque<ProductInfo>()

    @Volatile
    var current: ProductInfo? = null

    /**
     * Aggiunge un articolo alla cronologia in memoria.
     *
     * - mantiene al massimo gli ultimi 20 articoli;
     * - ignora un duplicato consecutivo;
     * - non esegue alcun accesso al disco;
     * - non modifica l'articolo corrente.
     */
    fun addToHistory(product: ProductInfo) {
        if (!isValidHistoryItem(product)) {
            return
        }

        synchronized(historyLock) {
            val latest = history.peekFirst()

            if (latest != null && representsSameProduct(latest, product)) {
                return
            }

            history.addFirst(product)

            while (history.size > MAX_HISTORY_SIZE) {
                history.removeLast()
            }
        }
    }

    /**
     * Restituisce una copia della cronologia, dal più recente al meno recente.
     */
    fun getHistory(): List<ProductInfo> =
        synchronized(historyLock) {
            history.toList()
        }

    /**
     * Cancella la cronologia conservata in memoria.
     */
    fun clearHistory() {
        synchronized(historyLock) {
            history.clear()
        }
    }

    private fun isValidHistoryItem(product: ProductInfo): Boolean =
        product.barcode.isNotBlank() ||
                product.articleCode.isNotBlank() ||
                product.description.isNotBlank()

    private fun representsSameProduct(
        first: ProductInfo,
        second: ProductInfo
    ): Boolean {
        val firstBarcode = first.barcode.trim()
        val secondBarcode = second.barcode.trim()

        if (firstBarcode.isNotEmpty() && secondBarcode.isNotEmpty()) {
            return firstBarcode == secondBarcode
        }

        val firstArticleCode = first.articleCode.trim()
        val secondArticleCode = second.articleCode.trim()

        return firstArticleCode.isNotEmpty() &&
                secondArticleCode.isNotEmpty() &&
                firstArticleCode == secondArticleCode
    }
}