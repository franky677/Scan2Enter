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
        priceListId: Int = 1,
        priceListName: String = "",
        listPrice: String = "",
        discount1: Double = 0.0,
        discount2: Double = 0.0,
        discount3: Double = 0.0,
        discount4: Double = 0.0,
        manualDiscount: Double = 0.0,
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
                    priceListId = priceListId,
                    priceListName = priceListName,
                    listPrice = effectiveListPrice,
                    discount1 = discount1,
                    discount2 = discount2,
                    discount3 = discount3,
                    discount4 = discount4,
                    manualDiscount = manualDiscount,
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
                    priceListId =
                        if (priceListId > 0) priceListId else old.priceListId,
                    priceListName =
                        priceListName.ifBlank { old.priceListName },
                    listPrice =
                        effectiveListPrice.ifBlank { old.listPrice },
                    discount1 = discount1,
                    discount2 = discount2,
                    discount3 = discount3,
                    discount4 = discount4,
                    manualDiscount = manualDiscount,
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

    fun setManualDiscount(
        articleId: Long,
        manualDiscount: Double
    ) {
        require(manualDiscount in 0.0..100.0) {
            "Sconto manuale non valido"
        }

        synchronized(lock) {
            val old = items[articleId] ?: return

            items[articleId] =
                old.copy(
                    manualDiscount = manualDiscount,
                    roundingPrice = "",
                    roundingAdjustment = ""
                )

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
                        /*
                         * Non azzerare discount1..4: sono le condizioni
                         * automatiche della riga/cliente (es. Comune di Mirano).
                         * Lo sconto manuale viene gestito separatamente da
                         * setManualDiscount().
                         */
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
                            obj.optDouble("discount1", 0.0),
                        discount2 =
                            obj.optDouble("discount2", 0.0),
                        discount3 =
                            obj.optDouble("discount3", 0.0),
                        discount4 =
                            obj.optDouble("discount4", 0.0),
                        manualDiscount =
                            obj.optDouble("manualDiscount", 0.0),
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

        /*
         * V6:
         * l'arrotondamento commerciale viene calcolato sul NETTO FINALE
         * della riga, quindi dopo l'eventuale sconto manuale.
         *
         * roundingPrice, quando presente, contiene già il prezzo unitario
         * netto definitivo da mostrare/usare nel totale. La UI NON deve
         * applicare nuovamente manualDiscount sopra roundingPrice.
         */
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

            val number =
                normalized.toBigDecimalOrNull()
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

        fun finalUnitCents(row: SessionItem): Int? {
            val baseCents =
                priceToCents(row.basePrice)
                    ?: return null

            if (baseCents < 0) return null

            if (
                row.manualPrice.isNotBlank() ||
                row.manualDiscount <= 0.0
            ) {
                return baseCents
            }

            val discount =
                row.manualDiscount
                    .coerceIn(0.0, 100.0)

            return java.math.BigDecimal(baseCents)
                .multiply(
                    java.math.BigDecimal.valueOf(
                        1.0 - discount / 100.0
                    )
                )
                .setScale(
                    0,
                    java.math.RoundingMode.HALF_UP
                )
                .intValueExact()
        }

        val rows =
            items.values.mapNotNull { row ->
                val unitCents =
                    finalUnitCents(row)
                        ?: return@mapNotNull null

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
            eligible.firstOrNull {
                it.first.quantity == 1
            } ?: eligible.firstOrNull()

        if (chosen != null) {
            val row = chosen.first
            val originalUnitCents = chosen.second
            val unitAdjustment =
                totalAdjustment / row.quantity

            val roundedUnitCents =
                originalUnitCents + unitAdjustment

            if (roundedUnitCents < 0) return

            val sign =
                if (unitAdjustment >= 0) "+" else ""

            items[row.articleId] =
                row.copy(
                    roundingPrice =
                        centsToPrice(roundedUnitCents),
                    roundingAdjustment =
                        "$sign${centsToPrice(unitAdjustment)}"
                )

            return
        }

        /*
         * Fallback a due righe, mantenuto dalla logica storica.
         * Serve nei casi in cui quantità multiple non consentono di
         * ottenere l'aggiustamento del totale modificando una sola riga.
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

            if (
                first.second % 10 == 0 ||
                first.first.quantity <= 0
            ) {
                continue
            }

            for (
            secondIndex in firstIndex + 1 until rows.size
            ) {
                val second = rows[secondIndex]

                if (
                    second.second % 10 == 0 ||
                    second.first.quantity <= 0
                ) {
                    continue
                }

                for (firstAdjustment in -9..9) {
                    for (secondAdjustment in -9..9) {
                        if (
                            firstAdjustment == 0 &&
                            secondAdjustment == 0
                        ) {
                            continue
                        }

                        val producedAdjustment =
                            firstAdjustment *
                                    first.first.quantity +
                                    secondAdjustment *
                                    second.first.quantity

                        if (
                            producedAdjustment !=
                            totalAdjustment
                        ) {
                            continue
                        }

                        if (
                            first.second +
                            firstAdjustment < 0 ||
                            second.second +
                            secondAdjustment < 0
                        ) {
                            continue
                        }

                        val score =
                            kotlin.math.abs(firstAdjustment) +
                                    kotlin.math.abs(
                                        secondAdjustment
                                    )

                        if (score < bestScore) {
                            bestScore = score

                            bestPair =
                                PairAdjustment(
                                    first = first,
                                    second = second,
                                    firstUnitAdjustment =
                                        firstAdjustment,
                                    secondUnitAdjustment =
                                        secondAdjustment
                                )
                        }
                    }
                }
            }
        }

        val pair =
            bestPair ?: return

        fun applyPairAdjustment(
            candidate: Triple<SessionItem, Int, Int>,
            unitAdjustment: Int
        ) {
            val row = candidate.first

            val roundedUnitCents =
                candidate.second + unitAdjustment

            if (roundedUnitCents < 0) return

            val sign =
                if (unitAdjustment >= 0) "+" else ""

            items[row.articleId] =
                row.copy(
                    roundingPrice =
                        centsToPrice(roundedUnitCents),
                    roundingAdjustment =
                        "$sign${centsToPrice(unitAdjustment)}"
                )
        }

        applyPairAdjustment(
            pair.first,
            pair.firstUnitAdjustment
        )

        applyPairAdjustment(
            pair.second,
            pair.secondUnitAdjustment
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
                    put("discount2", item.discount2)
                    put("discount3", item.discount3)
                    put("discount4", item.discount4)
                    put("manualDiscount", item.manualDiscount)
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