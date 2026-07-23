package com.scan2enter.reorder

import android.util.Log
import com.scan2enter.model.ProductInfo
import kotlin.math.max

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

    /**
     * Sincronizza l'articolo con la lista di riordino.
     *
     * La chiamata mantiene il nome add() per non modificare ScanSession, ma
     * applica la vera regola aziendale:
     *
     * - dati mancanti: non inserire e rimuovere un'eventuale voce vecchia;
     * - minima=0, massima=0, lotto=0: articolo escluso, quindi rimuovere;
     * - stock > minima e disponibile > 0: articolo regolare, quindi rimuovere;
     * - stock <= minima oppure disponibile <= 0: aggiungere o aggiornare.
     */
    @Synchronized
    fun add(product: ProductInfo): Boolean {
        require(product.articleId > 0L) {
            "Impossibile sincronizzare un articolo senza articleId valido"
        }

        val stock = product.stock.toQuantityOrNull()
        val availableStock = product.availableStock.toQuantityOrNull()
        val minimumStock = product.minimumStock.toQuantityOrNull()
        val maximumStock = product.maximumStock.toQuantityOrNull()
        val reorderLot = product.reorderLot.toQuantityOrNull()

        val valuesAvailable =
            stock != null &&
                    availableStock != null &&
                    minimumStock != null

        if (!valuesAvailable) {
            Log.d(
                TAG,
                "REORDER NON CLASSIFICATO - DATI MANCANTI " +
                        "id=${product.articleId} " +
                        "stock=$stock available=$availableStock minimum=$minimumStock"
            )

            return removeIfPresent(
                articleId = product.articleId,
                reason = "dati stock mancanti"
            )
        }

        val excludedFromAutomaticReorder =
            minimumStock == 0.0 &&
                    maximumStock == 0.0 &&
                    reorderLot == 0.0

        if (excludedFromAutomaticReorder) {
            Log.d(
                TAG,
                "REORDER ESCLUSO AUTOMATICAMENTE " +
                        "id=${product.articleId} code=${product.articleCode}"
            )

            return removeIfPresent(
                articleId = product.articleId,
                reason = "articolo escluso dal riordino automatico"
            )
        }

        val needsReorder =
            stock <= minimumStock ||
                    availableStock <= 0.0

        if (!needsReorder) {
            Log.d(
                TAG,
                "REORDER NON NECESSARIO " +
                        "id=${product.articleId} code=${product.articleCode} " +
                        "stock=$stock minimum=$minimumStock available=$availableStock"
            )

            return removeIfPresent(
                articleId = product.articleId,
                reason = "giacenza regolare"
            )
        }

        val quantityToOrder = calculateQuantityToOrder(
            stock = stock,
            minimumStock = minimumStock,
            reorderLot = reorderLot
        )

        val item = ReorderItem(
            articleId = product.articleId,
            barcode = product.barcode,
            articleCode = product.articleCode,
            description = product.description,
            supplierId = product.supplierId,
            supplierName = product.supplierName,
            supplierArticleCode = product.supplierArticleCode,
            stock = stock,
            availableStock = availableStock,
            minimumStock = minimumStock,
            maximumStock = maximumStock,
            reorderLot = reorderLot,
            quantityToOrder = quantityToOrder
        )

        val previous = items.put(item.articleId, item)

        if (previous == null) {
            logAdded(item)
            notifySizeChanged()
        } else {
            Log.d(
                TAG,
                "REORDER ARTICOLO AGGIORNATO " +
                        "id=${item.articleId} code=${item.articleCode} " +
                        "stock=${item.stock} minimum=${item.minimumStock} " +
                        "quantity=${item.quantityToOrder}"
            )
        }

        return true
    }

    @Synchronized
    fun remove(articleId: Long): Boolean {
        val removed = items.remove(articleId) ?: return false

        Log.d(
            TAG,
            "REORDER ARTICOLO RIMOSSO " +
                    "id=${removed.articleId} code=${removed.articleCode}"
        )

        notifySizeChanged()
        return true
    }

    @Synchronized
    fun contains(articleId: Long): Boolean =
        items.containsKey(articleId)

    @Synchronized
    fun getAll(): List<ReorderItem> =
        items.values.toList()

    @Synchronized
    fun getBySupplier(): Map<Long, List<ReorderItem>> =
        items.values.groupBy { it.supplierId }

    @Synchronized
    fun size(): Int =
        items.size

    @Synchronized
    fun supplierCount(): Int =
        items.values
            .map { it.supplierId }
            .distinct()
            .size

    private fun removeIfPresent(
        articleId: Long,
        reason: String
    ): Boolean {
        val removed = items.remove(articleId) ?: return false

        Log.d(
            TAG,
            "REORDER RIMOSSO AUTOMATICAMENTE " +
                    "id=${removed.articleId} code=${removed.articleCode} " +
                    "motivo=$reason"
        )

        notifySizeChanged()
        return true
    }

    private fun calculateQuantityToOrder(
        stock: Double,
        minimumStock: Double,
        reorderLot: Double?
    ): Double {
        if (reorderLot != null && reorderLot > 0.0) {
            return reorderLot
        }

        return max(0.0, minimumStock - stock)
    }

    private fun notifySizeChanged() {
        val count = items.size
        val snapshot = sizeListeners.toList()

        snapshot.forEach { listener ->
            try {
                listener(count)
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "ERRORE LISTENER REORDER STORE",
                    error
                )
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
        Log.d(TAG, "GIACENZA = ${item.stock}")
        Log.d(TAG, "DISPONIBILE = ${item.availableStock}")
        Log.d(TAG, "MINIMA = ${item.minimumStock}")
        Log.d(TAG, "DA ORDINARE = ${item.quantityToOrder}")
        Log.d(TAG, "ARTICOLI IN LISTA = ${size()}")
        Log.d(TAG, "FORNITORI IN LISTA = ${supplierCount()}")
        Log.d(TAG, "========================================")
    }

    private fun String.toQuantityOrNull(): Double? {
        val normalized = trim()
            .replace(" ", "")
            .replace(',', '.')

        if (normalized.isBlank()) {
            return null
        }

        return normalized.toDoubleOrNull()
    }
}