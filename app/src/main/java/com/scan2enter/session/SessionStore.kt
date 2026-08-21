package com.scan2enter.session

import android.content.Context
import com.scan2enter.model.ProductInfo
import org.json.JSONArray
import org.json.JSONObject

object SessionStore {
    private const val PREFS_NAME = "scan2enter_work_session"
    private const val KEY_ITEMS = "items"
    private const val KEY_NOTE = "collo_note"

    private val lock = Any()
    private val listeners = mutableSetOf<(List<SessionItem>) -> Unit>()
    private val items = LinkedHashMap<Long, SessionItem>()
    private var colloNote: String = ""

    /*
     * Protezione contro la stessa lettura hardware instradata due volte
     * (per esempio receiver dinamico + receiver legacy Sunmi).
     *
     * Non coinvolge i pulsanti +/- della Sessione, che usano setQuantity().
     */
    private const val SCAN_DUPLICATE_WINDOW_MS = 700L
    private var lastAddedArticleId = -1L
    private var lastAddedBarcode = ""
    private var lastAddedAtMs = 0L

    private var applicationContext: Context? = null
    private var initialized = false

    fun initialize(context: Context) {
        synchronized(lock) {
            if (initialized) return
            applicationContext = context.applicationContext
            loadLocked()
            initialized = true
        }
    }

    fun addOrIncrement(
        product: ProductInfo,
        amount: Int = 1,
        priceListName: String = "",
        listPrice: String = "",
        discount1: Double = 0.0,
        finalPrice: String = "",
        manualPrice: String = ""
    ): SessionItem? {
        if (product.articleId <= 0L || amount <= 0) return null

        val effectiveFinalPrice =
            finalPrice.ifBlank { product.publicPrice }

        val effectiveListPrice =
            listPrice.ifBlank { product.publicPrice }

        val updated: SessionItem

        synchronized(lock) {
            val now =
                android.os.SystemClock.elapsedRealtime()

            val normalizedBarcode =
                product.barcode.trim()

            val isDuplicateScan =
                product.articleId == lastAddedArticleId &&
                        normalizedBarcode == lastAddedBarcode &&
                        now - lastAddedAtMs <
                        SCAN_DUPLICATE_WINDOW_MS

            if (isDuplicateScan) {
                android.util.Log.d(
                    "SessionStore",
                    "DOPPIA LETTURA IGNORATA " +
                            "articleId=${product.articleId} " +
                            "barcode=$normalizedBarcode"
                )

                return items[product.articleId]
            }

            /*
             * Registriamo subito la lettura valida: se un secondo percorso
             * dello stesso barcode arriva mentre il primo è ancora in corso,
             * verrà scartato.
             */
            lastAddedArticleId = product.articleId
            lastAddedBarcode = normalizedBarcode
            lastAddedAtMs = now

            val old = items[product.articleId]

            updated = if (old == null) {
                SessionItem(
                    articleId = product.articleId,
                    articleCode = product.articleCode,
                    description = product.description,
                    barcode = product.barcode,
                    publicPrice = effectiveFinalPrice,
                    stock = product.stock,
                    quantity = amount,
                    priceListId = 1,
                    priceListName = priceListName,
                    listPrice = effectiveListPrice,
                    discount1 = discount1,
                    finalPrice = effectiveFinalPrice,
                    manualPrice = manualPrice,
                    effectiveMarkupPercent = null,
                    roundingPrice = "",
                    roundingAdjustment = ""
                )
            } else {
                old.copy(
                    articleCode =
                        product.articleCode.ifBlank { old.articleCode },
                    description =
                        product.description.ifBlank { old.description },
                    barcode =
                        product.barcode.ifBlank { old.barcode },
                    publicPrice =
                        effectiveFinalPrice.ifBlank { old.publicPrice },
                    stock =
                        product.stock.ifBlank { old.stock },
                    quantity = old.quantity + amount,
                    priceListName =
                        priceListName.ifBlank { old.priceListName },
                    listPrice =
                        effectiveListPrice.ifBlank { old.listPrice },
                    discount1 = discount1,
                    finalPrice =
                        effectiveFinalPrice.ifBlank { old.finalPrice },
                    manualPrice =
                        manualPrice.ifBlank { old.manualPrice },
                    roundingPrice = "",
                    roundingAdjustment = ""
                )
            }

            /*
             * L'ordine della LinkedHashMap rappresenta l'ordine di ultima lettura.
             * Se l'articolo era già presente, lo togliamo e lo reinseriamo:
             * così una nuova scansione dello stesso articolo lo rende di nuovo
             * l'elemento più recente della Sessione.
             */
            items.remove(product.articleId)
            items[product.articleId] = updated

            recalculateCommercialRoundingLocked()
            saveLocked()
        }

        notifyListeners()
        return updated
    }

    fun setQuantity(articleId: Long, quantity: Int) {
        synchronized(lock) {
            val old = items[articleId] ?: return

            if (quantity <= 0) {
                items.remove(articleId)
            } else {
                items[articleId] =
                    old.copy(
                        quantity = quantity.coerceAtMost(9999)
                    )
            }

            recalculateCommercialRoundingLocked()
            saveLocked()
        }

        notifyListeners()
    }

    fun setManualPrice(
        articleId: Long,
        manualPrice: String
    ) {
        synchronized(lock) {
            val old = items[articleId] ?: return

            items[articleId] =
                old.copy(
                    manualPrice = manualPrice.trim(),
                    roundingPrice = "",
                    roundingAdjustment = ""
                )

            recalculateCommercialRoundingLocked()
            saveLocked()
        }

        notifyListeners()
    }

    fun setQuantityAndManualPrice(
        articleId: Long,
        quantity: Int,
        manualPrice: String
    ) {
        synchronized(lock) {
            val old = items[articleId] ?: return

            if (quantity <= 0) {
                items.remove(articleId)
            } else {
                items[articleId] =
                    old.copy(
                        quantity = quantity.coerceAtMost(9999),
                        manualPrice = manualPrice.trim(),
                        roundingPrice = "",
                        roundingAdjustment = ""
                    )
            }

            recalculateCommercialRoundingLocked()
            saveLocked()
        }

        notifyListeners()
    }

    fun setQuantityAndPricing(
        articleId: Long,
        quantity: Int,
        manualPrice: String,
        priceListId: Int,
        priceListName: String,
        listPrice: String,
        finalPrice: String,
        effectiveMarkupPercent: Double?
    ) {
        synchronized(lock) {
            val old = items[articleId] ?: return

            if (quantity <= 0) {
                items.remove(articleId)
            } else {
                items[articleId] =
                    old.copy(
                        quantity = quantity.coerceAtMost(9999),
                        priceListId = priceListId,
                        priceListName = priceListName.trim(),
                        listPrice = listPrice.trim(),
                        discount1 = 0.0,
                        finalPrice = finalPrice.trim(),
                        manualPrice = manualPrice.trim(),
                        effectiveMarkupPercent = effectiveMarkupPercent,
                        roundingPrice = "",
                        roundingAdjustment = ""
                    )
            }

            recalculateCommercialRoundingLocked()
            saveLocked()
        }

        notifyListeners()
    }

    fun remove(articleId: Long) {
        synchronized(lock) {
            if (items.remove(articleId) == null) return
            recalculateCommercialRoundingLocked()
            saveLocked()
        }

        notifyListeners()
    }

    fun replaceWithHistory(
        historyItems: List<SessionItem>,
        note: String = ""
    ) {
        synchronized(lock) {
            items.clear()
            colloNote = note.take(4000)

            historyItems.forEach { item ->
                if (
                    item.articleId > 0L &&
                    item.quantity > 0
                ) {
                    items[item.articleId] =
                        item.copy(
                            roundingPrice = "",
                            roundingAdjustment = ""
                        )
                }
            }

            recalculateCommercialRoundingLocked()
            saveLocked()
        }

        notifyListeners()
    }

    fun clear() {
        synchronized(lock) {
            items.clear()
            colloNote = ""
            saveLocked()
        }

        notifyListeners()
    }

    fun getNote(): String =
        synchronized(lock) {
            colloNote
        }

    fun setNote(note: String) {
        synchronized(lock) {
            colloNote = note.take(4000)

            val context = applicationContext ?: return
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            ).edit()
                .putString(KEY_NOTE, colloNote)
                .apply()
        }
    }

    fun clearNote() {
        setNote("")
    }

    fun getItems(): List<SessionItem> =
        synchronized(lock) {
            /*
             * La LinkedHashMap conserva in coda l'articolo letto/selezionato
             * più di recente. La UI deve invece mostrarlo in testa.
             */
            items.values.toList().asReversed()
        }

    fun quantityFor(articleId: Long): Int =
        synchronized(lock) {
            items[articleId]?.quantity ?: 0
        }

    fun addListener(
        listener: (List<SessionItem>) -> Unit
    ) {
        synchronized(lock) {
            listeners.add(listener)
        }

        listener(getItems())
    }

    fun removeListener(
        listener: (List<SessionItem>) -> Unit
    ) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners() {
        val snapshot = getItems()

        val callbacks =
            synchronized(lock) {
                listeners.toList()
            }

        callbacks.forEach { callback ->
            runCatching {
                callback(snapshot)
            }
        }
    }

    private fun loadLocked() {
        items.clear()

        val context = applicationContext ?: return

        val prefs =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        colloNote =
            prefs.getString(KEY_NOTE, "")
                ?.take(4000)
                .orEmpty()

        val json =
            prefs.getString(KEY_ITEMS, null)
                ?: return

        runCatching {
            val array = JSONArray(json)

            for (index in 0 until array.length()) {
                val obj =
                    array.optJSONObject(index)
                        ?: continue

                val id =
                    obj.optLong("articleId", 0L)

                val quantity =
                    obj.optInt("quantity", 0)

                if (id <= 0L || quantity <= 0) {
                    continue
                }

                val publicPrice =
                    obj.optString("publicPrice")

                val finalPrice =
                    obj.optString(
                        "finalPrice",
                        publicPrice
                    )

                val listPrice =
                    obj.optString(
                        "listPrice",
                        publicPrice
                    )

                items[id] =
                    SessionItem(
                        articleId = id,
                        articleCode =
                            obj.optString("articleCode"),
                        description =
                            obj.optString("description"),
                        barcode =
                            obj.optString("barcode"),
                        publicPrice =
                            finalPrice.ifBlank {
                                publicPrice
                            },
                        stock =
                            obj.optString("stock"),
                        quantity = quantity,
                        priceListId = obj.optInt("priceListId", 1),
                        priceListName =
                            obj.optString("priceListName"),
                        listPrice = listPrice,
                        discount1 =
                            obj.optDouble(
                                "discount1",
                                0.0
                            ),
                        finalPrice = finalPrice,
                        manualPrice =
                            obj.optString("manualPrice"),
                        effectiveMarkupPercent =
                            if (obj.has("effectiveMarkupPercent") && !obj.isNull("effectiveMarkupPercent")) {
                                obj.optDouble("effectiveMarkupPercent")
                            } else {
                                null
                            },
                        roundingPrice = "",
                        roundingAdjustment = ""
                    )
            }
        }.onFailure {
            items.clear()
        }

        recalculateCommercialRoundingLocked()
    }

    private fun recalculateCommercialRoundingLocked() {
        if (items.isEmpty()) return

        // Ogni ricalcolo parte sempre dai prezzi reali/manuali, mai
        // dall'arrotondamento precedente.
        val cleanItems =
            items.mapValues { (_, value) ->
                value.copy(
                    roundingPrice = "",
                    roundingAdjustment = ""
                )
            }

        items.clear()
        items.putAll(cleanItems)

        fun priceToCents(value: String): Int? {
            val normalized =
                value.trim()
                    .replace("€", "")
                    .replace(" ", "")
                    .replace(",", ".")

            val number = normalized.toBigDecimalOrNull()
                ?: return null

            return runCatching {
                number
                    .movePointRight(2)
                    .setScale(
                        0,
                        java.math.RoundingMode.HALF_UP
                    )
                    .intValueExact()
            }.getOrNull()
        }

        val rows =
            items.values.mapNotNull { row ->
                val unitCents =
                    priceToCents(row.basePrice)
                        ?: return@mapNotNull null

                if (unitCents < 0) {
                    return@mapNotNull null
                }

                Triple(
                    row,
                    unitCents,
                    unitCents * row.quantity
                )
            }

        if (rows.isEmpty()) return

        val totalCents =
            rows.sumOf { it.third }

        val remainder =
            ((totalCents % 10) + 10) % 10

        if (remainder == 0) return

        // Al decimo più vicino. A 5 centesimi arrotondiamo verso l'alto.
        val totalAdjustment =
            if (remainder < 5) {
                -remainder
            } else {
                10 - remainder
            }

        /*
         * Usiamo solo una riga il cui prezzo non sia già "tondo"
         * ai 10 centesimi. Prima preferiamo quantità 1; altrimenti
         * accettiamo una quantità > 1 solo quando la correzione totale
         * è divisibile esattamente per la quantità.
         */
        val eligible =
            rows.filter { (row, unitCents, _) ->
                unitCents % 10 != 0 &&
                        row.quantity > 0 &&
                        totalAdjustment % row.quantity == 0
            }

        fun centsToPrice(cents: Int): String =
            java.math.BigDecimal(cents)
                .movePointLeft(2)
                .setScale(2)
                .toPlainString()

        val chosen =
            eligible.firstOrNull { it.first.quantity == 1 }
                ?: eligible.firstOrNull()

        if (chosen != null) {
            // Comportamento esistente: se basta una riga, non cambia nulla.
            val row = chosen.first
            val originalUnitCents = chosen.second
            val unitAdjustment = totalAdjustment / row.quantity
            val roundedUnitCents = originalUnitCents + unitAdjustment

            if (roundedUnitCents < 0) return

            val sign = if (unitAdjustment >= 0) "+" else ""

            items[row.articleId] =
                row.copy(
                    roundingPrice = centsToPrice(roundedUnitCents),
                    roundingAdjustment = "$sign${centsToPrice(unitAdjustment)}"
                )
            return
        }

        /*
         * Fallback a due righe per casi non risolvibili con una sola riga.
         * Esempio: 7 x 0,36 + 5 x 0,36 = 4,32 -> 4,30
         *           7 x 0,35 + 5 x 0,37 = 4,30
         */
        data class PairAdjustment(
            val first: Triple<SessionItem, Int, Int>,
            val second: Triple<SessionItem, Int, Int>,
            val firstUnitAdjustment: Int,
            val secondUnitAdjustment: Int
        )

        var bestPair: PairAdjustment? = null
        var bestScore = Int.MAX_VALUE

        for (firstIndex in 0 until rows.size - 1) {
            val first = rows[firstIndex]
            if (first.second % 10 == 0 || first.first.quantity <= 0) continue

            for (secondIndex in firstIndex + 1 until rows.size) {
                val second = rows[secondIndex]
                if (second.second % 10 == 0 || second.first.quantity <= 0) continue

                for (firstAdjustment in -9..9) {
                    for (secondAdjustment in -9..9) {
                        if (firstAdjustment == 0 && secondAdjustment == 0) continue

                        val producedAdjustment =
                            firstAdjustment * first.first.quantity +
                                    secondAdjustment * second.first.quantity

                        if (producedAdjustment != totalAdjustment) continue
                        if (first.second + firstAdjustment < 0 ||
                            second.second + secondAdjustment < 0
                        ) continue

                        val score =
                            kotlin.math.abs(firstAdjustment) +
                                    kotlin.math.abs(secondAdjustment)

                        if (score < bestScore) {
                            bestScore = score
                            bestPair = PairAdjustment(
                                first = first,
                                second = second,
                                firstUnitAdjustment = firstAdjustment,
                                secondUnitAdjustment = secondAdjustment
                            )
                        }
                    }
                }
            }
        }

        val pair = bestPair

        fun applyPairAdjustment(
            candidate: Triple<SessionItem, Int, Int>,
            unitAdjustment: Int
        ) {
            val row = candidate.first
            val roundedUnitCents = candidate.second + unitAdjustment
            val sign = if (unitAdjustment >= 0) "+" else ""

            items[row.articleId] =
                row.copy(
                    roundingPrice = centsToPrice(roundedUnitCents),
                    roundingAdjustment = "$sign${centsToPrice(unitAdjustment)}"
                )
        }

        if (pair != null) {
            applyPairAdjustment(pair.first, pair.firstUnitAdjustment)
            applyPairAdjustment(pair.second, pair.secondUnitAdjustment)
            return
        }

        /*
         * Ultimo fallback: prezzo unitario con maggiore precisione.
         *
         * Serve quando né una riga né due righe a 2 decimali possono
         * produrre esattamente la correzione richiesta.
         *
         * Esempi:
         *   8 x 0,36 = 2,88 -> obiettivo 2,90
         *   prezzo riga = 2,90 / 8 = 0,3625
         *
         *   3 x 0,36 + 3 x 0,36 = 2,16 -> obiettivo 2,20
         *   una sola delle due righe assorbe +0,04 sul proprio totale;
         *   il prezzo unitario viene calcolato con 6 decimali.
         *
         * Preferiamo la quantità maggiore per minimizzare la variazione
         * del prezzo unitario.
         */
        val preciseCandidate =
            rows
                .filter { (row, unitCents, _) ->
                    row.quantity > 0 &&
                            unitCents % 10 != 0
                }
                .maxByOrNull { it.first.quantity }
                ?: return

        val preciseRow = preciseCandidate.first
        val originalUnitCents = preciseCandidate.second

        val originalRowTotalCents =
            java.math.BigDecimal(originalUnitCents)
                .multiply(
                    java.math.BigDecimal(preciseRow.quantity)
                )

        val targetRowTotalCents =
            originalRowTotalCents.add(
                java.math.BigDecimal(totalAdjustment)
            )

        if (targetRowTotalCents.signum() < 0) return

        val preciseUnitPrice =
            targetRowTotalCents
                .movePointLeft(2)
                .divide(
                    java.math.BigDecimal(preciseRow.quantity),
                    6,
                    java.math.RoundingMode.HALF_UP
                )

        val baseUnitPrice =
            java.math.BigDecimal(originalUnitCents)
                .movePointLeft(2)

        val preciseAdjustment =
            preciseUnitPrice.subtract(baseUnitPrice)

        val sign =
            if (preciseAdjustment.signum() >= 0) "+" else ""

        items[preciseRow.articleId] =
            preciseRow.copy(
                roundingPrice =
                    preciseUnitPrice
                        .stripTrailingZeros()
                        .toPlainString(),
                roundingAdjustment =
                    sign +
                            preciseAdjustment
                                .stripTrailingZeros()
                                .toPlainString()
            )

    }

    private fun saveLocked() {
        val context = applicationContext ?: return

        val array = JSONArray()

        items.values.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("articleId", item.articleId)
                    put("articleCode", item.articleCode)
                    put("description", item.description)
                    put("barcode", item.barcode)
                    put("publicPrice", item.publicPrice)
                    put("stock", item.stock)
                    put("quantity", item.quantity)
                    put("priceListId", item.priceListId)
                    put("priceListName", item.priceListName)
                    put("listPrice", item.listPrice)
                    put("discount1", item.discount1)
                    put("finalPrice", item.finalPrice)
                    put("manualPrice", item.manualPrice)
                    if (item.effectiveMarkupPercent != null) {
                        put("effectiveMarkupPercent", item.effectiveMarkupPercent)
                    } else {
                        put("effectiveMarkupPercent", JSONObject.NULL)
                    }
                    put("roundingPrice", item.roundingPrice)
                    put("roundingAdjustment", item.roundingAdjustment)
                }
            )
        }

        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_ITEMS,
                array.toString()
            )
            .putString(
                KEY_NOTE,
                colloNote
            )
            .apply()
    }
}