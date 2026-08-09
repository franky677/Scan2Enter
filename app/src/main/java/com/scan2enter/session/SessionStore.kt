package com.scan2enter.session

import android.content.Context
import com.scan2enter.model.ProductInfo
import org.json.JSONArray
import org.json.JSONObject

object SessionStore {
    private const val PREFS_NAME = "scan2enter_work_session"
    private const val KEY_ITEMS = "items"

    private val lock = Any()
    private val listeners = mutableSetOf<(List<SessionItem>) -> Unit>()
    private val items = LinkedHashMap<Long, SessionItem>()

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
                    priceListName = priceListName,
                    listPrice = effectiveListPrice,
                    discount1 = discount1,
                    finalPrice = effectiveFinalPrice,
                    manualPrice = manualPrice
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
                        manualPrice.ifBlank { old.manualPrice }
                )
            }

            items[product.articleId] = updated
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
                    manualPrice = manualPrice.trim()
                )

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
                        manualPrice = manualPrice.trim()
                    )
            }

            saveLocked()
        }

        notifyListeners()
    }

    fun replaceWithHistory(
        historyItems: List<SessionItem>
    ) {
        synchronized(lock) {
            items.clear()

            historyItems.forEach { item ->
                if (item.articleId > 0L && item.quantity > 0) {
                    items[item.articleId] = item
                }
            }

            saveLocked()
        }

        notifyListeners()
    }

    fun remove(articleId: Long) {
        synchronized(lock) {
            if (items.remove(articleId) == null) return
            saveLocked()
        }

        notifyListeners()
    }

    fun clear() {
        synchronized(lock) {
            items.clear()
            saveLocked()
        }

        notifyListeners()
    }

    fun getItems(): List<SessionItem> =
        synchronized(lock) {
            items.values.toList()
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

        val json =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
                .getString(KEY_ITEMS, null)
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
                            obj.optString("manualPrice")
                    )
            }
        }.onFailure {
            items.clear()
        }
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
                    put("priceListName", item.priceListName)
                    put("listPrice", item.listPrice)
                    put("discount1", item.discount1)
                    put("finalPrice", item.finalPrice)
                    put("manualPrice", item.manualPrice)
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
            .apply()
    }
}