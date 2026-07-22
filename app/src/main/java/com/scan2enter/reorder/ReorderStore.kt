package com.scan2enter.reorder

import android.util.Log
import com.scan2enter.model.ProductInfo

object ReorderStore {

    private const val TAG = "Scan2Enter"

    private val items = linkedMapOf<Long, ReorderItem>()
    private val sizeListeners = linkedSetOf<(Int) -> Unit>()

    @Synchronized
    fun addSizeListener(listener: (Int) -> Unit) {
        sizeListeners.add(listener)
    }

    @Synchronized
    fun removeSizeListener(listener: (Int) -> Unit) {
        sizeListeners.remove(listener)
    }

    @Synchronized
    fun clear() {
        if (items.isEmpty()) return
        items.clear()
        Log.d(TAG, "REORDER STORE SVUOTATO")
        notifySizeChanged()
    }

    @Synchronized
    fun add(product: ProductInfo): Boolean {
        require(product.articleId > 0L) {
            "Impossibile aggiungere un articolo senza articleId valido"
        }

        if (items.containsKey(product.articleId)) {
            Log.d(TAG, "REORDER ARTICOLO GIA PRESENTE id=${product.articleId} code=${product.articleCode}")
            return false
        }

        val item = ReorderItem(
            articleId = product.articleId,
            barcode = product.barcode,
            articleCode = product.articleCode,
            description = product.description,
            supplierId = product.supplierId,
            supplierName = product.supplierName,
            supplierArticleCode = product.supplierArticleCode,
            stock = product.stock.toQuantityOrNull(),
            availableStock = product.availableStock.toQuantityOrNull(),
            minimumStock = product.minimumStock.toQuantityOrNull(),
            maximumStock = product.maximumStock.toQuantityOrNull(),
            reorderLot = product.reorderLot.toQuantityOrNull(),
            quantityToOrder = 0.0
        )

        items[item.articleId] = item
        logAdded(item)
        notifySizeChanged()
        return true
    }

    @Synchronized
    fun remove(articleId: Long): Boolean {
        val removed = items.remove(articleId) ?: return false
        Log.d(TAG, "REORDER ARTICOLO RIMOSSO id=${removed.articleId} code=${removed.articleCode}")
        notifySizeChanged()
        return true
    }

    @Synchronized
    fun contains(articleId: Long): Boolean = items.containsKey(articleId)

    @Synchronized
    fun getAll(): List<ReorderItem> = items.values.toList()

    @Synchronized
    fun getBySupplier(): Map<Long, List<ReorderItem>> = items.values.groupBy { it.supplierId }

    @Synchronized
    fun size(): Int = items.size

    @Synchronized
    fun supplierCount(): Int = items.values.map { it.supplierId }.distinct().size

    private fun notifySizeChanged() {
        val count = items.size
        val snapshot = sizeListeners.toList()
        snapshot.forEach { listener ->
            try {
                listener(count)
            } catch (error: Exception) {
                Log.e(TAG, "ERRORE LISTENER REORDER STORE", error)
            }
        }
    }

    private fun logAdded(item: ReorderItem) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "REORDER ARTICOLO AGGIUNTO")
        Log.d(TAG, "ID = ${item.articleId}")
        Log.d(TAG, "CODICE = ${item.articleCode}")
        Log.d(TAG, "DESCRIZIONE = ${item.description}")
        Log.d(TAG, "FORNITORE ID = ${item.supplierId}")
        Log.d(TAG, "FORNITORE = ${item.supplierName}")
        Log.d(TAG, "CODICE FORNITORE = ${item.supplierArticleCode}")
        Log.d(TAG, "ARTICOLI IN LISTA = ${size()}")
        Log.d(TAG, "FORNITORI IN LISTA = ${supplierCount()}")
        Log.d(TAG, "========================================")
    }

    private fun String.toQuantityOrNull(): Double? {
        val normalized = trim().replace(" ", "").replace(',', '.')
        if (normalized.isBlank()) return null
        return normalized.toDoubleOrNull()
    }
}
