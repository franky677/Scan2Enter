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
        manualPrice: String = "",
        promoActive: Boolean? = null,
        promoValidToEpochMillis: Long? = null
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
                    manualDiscount =
                        if (promoActive == true) 0.0 else manualDiscount,
                    finalPrice = effectiveFinalPrice,
                    manualPrice =
                        if (promoActive == true) "" else manualPrice,
                    effectiveMarkupPercent = null,
                    roundingPrice = "",
                    roundingAdjustment = "",
                    promoActive = promoActive ?: false,
                    promoValidToEpochMillis =
                        if (promoActive != null) {
                            promoValidToEpochMillis
                        } else {
                            null
                        }
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
                    manualDiscount =
                        if (promoActive == true) {
                            0.0
                        } else {
                            manualDiscount
                        },
                    finalPrice =
                        effectiveFinalPrice.ifBlank { old.finalPrice },
                    manualPrice =
                        if (promoActive == true) {
                            ""
                        } else {
                            manualPrice.ifBlank { old.manualPrice }
                        },
                    roundingPrice = "",
                    roundingAdjustment = "",
                    promoActive =
                        promoActive ?: old.promoActive,
                    promoValidToEpochMillis =
                        if (promoActive != null) {
                            promoValidToEpochMillis
                        } else {
                            old.promoValidToEpochMillis
                        }
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

            if (old.isPromoPriceLocked) {
                android.util.Log.d(
                    "SessionStore",
                    "SCONTO MANUALE BLOCCATO: promo attiva articleId=$articleId"
                )
                return
            }

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

            if (old.isPromoPriceLocked) {
                android.util.Log.d(
                    "SessionStore",
                    "PREZZO MANUALE BLOCCATO: promo attiva articleId=$articleId"
                )
                return
            }

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
                        manualPrice =
                            if (old.isPromoPriceLocked) {
                                old.manualPrice
                            } else {
                                manualPrice.trim()
                            },
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
            } else if (old.isPromoPriceLocked) {
                /*
                 * Promo attiva: dal Collo veloce è ammessa soltanto la quantità.
                 * Prezzo, listino, sconti e ricarico restano quelli della promo.
                 */
                items[articleId] =
                    old.copy(
                        quantity = quantity.coerceAtMost(9999),
                        roundingPrice = "",
                        roundingAdjustment = ""
                    )
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
                        roundingAdjustment = "",
                        promoActive =
                            obj.optBoolean("promoActive", false),
                        promoValidToEpochMillis =
                            if (obj.has("promoValidToEpochMillis") && !obj.isNull("promoValidToEpochMillis")) {
                                obj.optLong("promoValidToEpochMillis")
                            } else {
                                null
                            }
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
         * Arrotondamento commerciale del COLLO al decimo di euro.
         *
         * L'aggiustamento appartiene al totale, non al prezzo unitario:
         * per esempio 8 x 0,42 = 3,36 deve diventare 3,40 senza trasformare
         * artificialmente il prezzo al metro in 0,43.
         *
         * roundingAdjustment contiene quindi l'aggiustamento TOTALE della
         * riga scelta (es. +0.04 / -0.03). roundingPrice resta vuoto.
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

            val number = normalized.toBigDecimalOrNull() ?: return null

            return runCatching {
                number
                    .movePointRight(2)
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .intValueExact()
            }.getOrNull()
        }

        fun finalUnitCents(row: SessionItem): Int? {
            val baseCents = priceToCents(row.basePrice) ?: return null
            if (baseCents < 0) return null

            if (row.manualPrice.isNotBlank() || row.manualDiscount <= 0.0) {
                return baseCents
            }

            val discount = row.manualDiscount.coerceIn(0.0, 100.0)

            return java.math.BigDecimal(baseCents)
                .multiply(
                    java.math.BigDecimal.valueOf(
                        1.0 - discount / 100.0
                    )
                )
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .intValueExact()
        }

        val rows =
            items.values.mapNotNull { row ->
                val unitCents = finalUnitCents(row) ?: return@mapNotNull null
                Triple(row, unitCents, unitCents * row.quantity)
            }

        if (rows.isEmpty()) return

        val totalCents = rows.sumOf { it.third }
        val remainder = ((totalCents % 10) + 10) % 10
        if (remainder == 0) return

        // Al decimo più vicino. A 5 centesimi arrotondiamo verso l'alto.
        val totalAdjustment =
            if (remainder < 5) -remainder else 10 - remainder

        fun centsToPrice(cents: Int): String =
            java.math.BigDecimal(cents)
                .movePointLeft(2)
                .setScale(2)
                .toPlainString()

        /*
         * Registriamo l'intero aggiustamento su una sola riga, senza dividerlo
         * per la quantità. In questo modo funziona anche con quantità multiple.
         */
        val chosen = rows.first().first
        val sign = if (totalAdjustment >= 0) "+" else ""

        items[chosen.articleId] =
            chosen.copy(
                roundingPrice = "",
                roundingAdjustment = "$sign${centsToPrice(totalAdjustment)}"
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
                    put("promoActive", item.promoActive)
                    if (item.promoValidToEpochMillis != null) {
                        put(
                            "promoValidToEpochMillis",
                            item.promoValidToEpochMillis
                        )
                    } else {
                        put("promoValidToEpochMillis", JSONObject.NULL)
                    }
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