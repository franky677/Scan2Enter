package com.scan2enter.reorder

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.scan2enter.model.ProductInfo
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

object ReorderStore {

    private const val TAG = "Scan2Enter"
    private const val PREFS_NAME = "reorder_store_preferences"
    private const val ITEMS_KEY = "reorder_items_json"

    private val items = linkedMapOf<String, ReorderItem>()
    private val sizeListeners = linkedSetOf<(Int) -> Unit>()

    private var preferences: SharedPreferences? = null
    private var initialized = false

    /**
     * Inizializza lo store e ripristina la lista salvata sul dispositivo.
     *
     * È sicuro richiamare più volte questo metodo: il caricamento viene
     * eseguito una sola volta per ogni processo dell'app.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return

        preferences = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        loadFromDisk()
        initialized = true

        Log.d(
            TAG,
            "REORDER STORE INIZIALIZZATO elementi=${items.size}"
        )
    }

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
        saveToDisk()
        Log.d(TAG, "REORDER STORE SVUOTATO")
        notifySizeChanged()
    }

    /**
     * Sostituisce la cache locale con la lista completa ricevuta dal Gateway.
     *
     * Le righe non valide e quelle con quantità da ordinare pari a zero
     * vengono ignorate. In caso di errore di rete questo metodo non viene
     * chiamato, quindi resta disponibile l'ultima lista salvata sul telefono.
     */
    @Synchronized
    fun replaceAll(newItems: List<ReorderItem>) {
        val replacement = linkedMapOf<String, ReorderItem>()

        newItems.forEach { item ->
            if (item.articleId <= 0L) return@forEach
            if (item.quantityToOrder <= 0.0) return@forEach

            replacement[itemKey(item)] = item
        }

        if (items == replacement) {
            Log.d(
                TAG,
                "REORDER STORE GIÀ AGGIORNATO elementi=${items.size}"
            )
            return
        }

        items.clear()
        items.putAll(replacement)
        saveToDisk()

        Log.d(
            TAG,
            "REORDER STORE SOSTITUITO DAL GATEWAY " +
                    "elementi=${items.size} fornitori=${supplierCount()}"
        )

        notifySizeChanged()
    }

    /**
     * Sincronizza l'articolo con la lista di riordino.
     *
     * La chiamata mantiene il nome add() per non modificare ScanSession, ma
     * applica la vera regola aziendale:
     *
     * - stock o disponibile mancanti: non classificare e rimuovere una voce vecchia;
     * - minima, massima e lotto tutti null: articolo escluso dal riordino;
     * - minima null e lotto > 0: aggiungere con quantità pari al lotto;
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

        val stockValuesAvailable =
            stock != null &&
                    availableStock != null

        if (!stockValuesAvailable) {
            Log.d(
                TAG,
                "REORDER NON CLASSIFICATO - DATI STOCK MANCANTI " +
                        "id=${product.articleId} " +
                        "stock=$stock available=$availableStock"
            )

            return removeIfPresent(
                articleId = product.articleId,
                reason = "dati stock mancanti"
            )
        }

        /*
         * Due Retail usa -1 come equivalente di NULL.
         * L'articolo è escluso dal riordino automatico soltanto quando
         * minima, massima e lotto sono tutti null.
         */
        val excludedFromAutomaticReorder =
            minimumStock == null &&
                    maximumStock == null &&
                    reorderLot == null

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

        /*
         * Se la scorta minima è null ma esiste un lotto positivo,
         * l'articolo deve comparire nella lista con quantità pari al lotto.
         */
        val reorderByLotWithoutMinimum =
            minimumStock == null &&
                    reorderLot != null &&
                    reorderLot > 0.0

        /*
         * Se manca la minima e non esiste un lotto positivo, non abbiamo
         * dati sufficienti per calcolare un riordino automatico.
         */
        if (minimumStock == null && !reorderByLotWithoutMinimum) {
            Log.d(
                TAG,
                "REORDER NON CLASSIFICATO - MINIMA MANCANTE " +
                        "id=${product.articleId} code=${product.articleCode} " +
                        "maximum=$maximumStock lot=$reorderLot"
            )

            return removeIfPresent(
                articleId = product.articleId,
                reason = "scorta minima mancante"
            )
        }

        val needsReorder =
            reorderByLotWithoutMinimum ||
                    (minimumStock != null &&
                            (stock <= minimumStock || availableStock <= 0.0))

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

        val existingKeys = items
            .filterValues { it.articleId == item.articleId }
            .keys
            .toList()

        if (existingKeys.isNotEmpty()) {
            var changed = false

            existingKeys.forEach { key ->
                val current = items[key] ?: return@forEach
                val updated = current.copy(
                    barcode = item.barcode.ifBlank { current.barcode },
                    articleCode = item.articleCode.ifBlank { current.articleCode },
                    description = item.description.ifBlank { current.description },
                    stock = stock,
                    availableStock = availableStock,
                    minimumStock = minimumStock,
                    maximumStock = maximumStock,
                    reorderLot = reorderLot,
                    quantityToOrder = quantityToOrder
                )

                if (updated != current) {
                    items[key] = updated
                    changed = true
                }
            }

            if (changed) {
                saveToDisk()
                notifySizeChanged()
            }

            return true
        }

        items[itemKey(item)] = item
        saveToDisk()
        logAdded(item)

        notifySizeChanged()
        return true
    }

    @Synchronized
    fun remove(articleId: Long): Boolean {
        val keys = items
            .filterValues { it.articleId == articleId }
            .keys
            .toList()

        if (keys.isEmpty()) return false

        keys.forEach { items.remove(it) }
        saveToDisk()

        Log.d(
            TAG,
            "REORDER ARTICOLO RIMOSSO DA TUTTI I FORNITORI " +
                    "id=$articleId righe=${keys.size}"
        )

        notifySizeChanged()
        return true
    }

    @Synchronized
    fun contains(articleId: Long): Boolean =
        items.values.any { it.articleId == articleId }

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
        val keys = items
            .filterValues { it.articleId == articleId }
            .keys
            .toList()

        if (keys.isEmpty()) return false

        val sample = items[keys.first()]
        keys.forEach { items.remove(it) }

        saveToDisk()

        Log.d(
            TAG,
            "REORDER RIMOSSO AUTOMATICAMENTE DA TUTTI I FORNITORI " +
                    "id=$articleId code=${sample?.articleCode.orEmpty()} " +
                    "righe=${keys.size} motivo=$reason"
        )

        notifySizeChanged()
        return true
    }

    private fun itemKey(item: ReorderItem): String =
        "${item.articleId}:${item.supplierId}"

    private fun calculateQuantityToOrder(
        stock: Double,
        minimumStock: Double?,
        reorderLot: Double?
    ): Double {
        if (reorderLot != null && reorderLot > 0.0) {
            return reorderLot
        }

        if (minimumStock != null) {
            return max(0.0, minimumStock - stock)
        }

        return 0.0
    }

    private fun loadFromDisk() {
        items.clear()

        val rawJson = preferences
            ?.getString(ITEMS_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: return

        try {
            val array = JSONArray(rawJson)

            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val articleId = json.optLong("articleId", 0L)

                if (articleId <= 0L) continue

                val item = ReorderItem(
                    articleId = articleId,
                    barcode = json.optString("barcode", ""),
                    articleCode = json.optString("articleCode", ""),
                    description = json.optString("description", ""),
                    supplierId = json.optLong("supplierId", 0L),
                    supplierName = json.optString("supplierName", ""),
                    supplierArticleCode = json.optString("supplierArticleCode", ""),
                    purchaseTaxable = json.optNullableDouble("purchaseTaxable"),
                    purchasePrice = json.optNullableDouble("purchasePrice"),
                    vatRate = json.optNullableDouble("vatRate"),
                    stock = json.optNullableDouble("stock"),
                    availableStock = json.optNullableDouble("availableStock"),
                    minimumStock = json.optNullableDouble("minimumStock"),
                    maximumStock = json.optNullableDouble("maximumStock"),
                    reorderLot = json.optNullableDouble("reorderLot"),
                    quantityToOrder = json.optDouble("quantityToOrder", 0.0)
                )

                items[itemKey(item)] = item
            }

            Log.d(
                TAG,
                "REORDER STORE RIPRISTINATO elementi=${items.size}"
            )
        } catch (error: Exception) {
            items.clear()
            Log.e(
                TAG,
                "ERRORE RIPRISTINO REORDER STORE",
                error
            )
        }
    }

    private fun saveToDisk() {
        val prefs = preferences

        if (prefs == null) {
            Log.w(
                TAG,
                "REORDER STORE NON INIZIALIZZATO: salvataggio rimandato"
            )
            return
        }

        try {
            val array = JSONArray()

            items.values.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("articleId", item.articleId)
                        put("barcode", item.barcode)
                        put("articleCode", item.articleCode)
                        put("description", item.description)
                        put("supplierId", item.supplierId)
                        put("supplierName", item.supplierName)
                        put("supplierArticleCode", item.supplierArticleCode)
                        putNullableDouble("purchaseTaxable", item.purchaseTaxable)
                        putNullableDouble("purchasePrice", item.purchasePrice)
                        putNullableDouble("vatRate", item.vatRate)
                        putNullableDouble("stock", item.stock)
                        putNullableDouble("availableStock", item.availableStock)
                        putNullableDouble("minimumStock", item.minimumStock)
                        putNullableDouble("maximumStock", item.maximumStock)
                        putNullableDouble("reorderLot", item.reorderLot)
                        put("quantityToOrder", item.quantityToOrder)
                    }
                )
            }

            prefs.edit()
                .putString(ITEMS_KEY, array.toString())
                .apply()
        } catch (error: Exception) {
            Log.e(
                TAG,
                "ERRORE SALVATAGGIO REORDER STORE",
                error
            )
        }
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

    private fun JSONObject.putNullableDouble(
        key: String,
        value: Double?
    ) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeUnless { it.isNaN() }
    }

    private fun String.toQuantityOrNull(): Double? {
        val normalized = trim()
            .replace(" ", "")
            .replace(',', '.')

        if (normalized.isBlank()) {
            return null
        }

        val value = normalized.toDoubleOrNull() ?: return null

        // Due Retail usa -1 come valore sentinella equivalente a NULL.
        return if (value == -1.0) null else value
    }
}