package com.scan2enter.favorites

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.scan2enter.model.ProductInfo
import org.json.JSONArray
import org.json.JSONObject

object FavoriteStore {

    private const val TAG = "Scan2Enter"
    private const val PREFS_NAME = "favorite_store_preferences"
    private const val ITEMS_KEY = "favorite_items_json"

    private val items = linkedMapOf<Long, FavoriteItem>()
    private val listeners = linkedSetOf<(Int) -> Unit>()

    private var preferences: SharedPreferences? = null
    private var initialized = false

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
            "FAVORITE STORE INIZIALIZZATO elementi=${items.size}"
        )
    }

    @Synchronized
    fun addListener(listener: (Int) -> Unit) {
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: (Int) -> Unit) {
        listeners.remove(listener)
    }

    @Synchronized
    fun add(product: ProductInfo): Boolean {
        require(product.articleId > 0L) {
            "Impossibile aggiungere ai preferiti un articolo senza articleId valido"
        }

        val item = FavoriteItem.fromProduct(product)
        val previous = items[item.articleId]

        if (previous == item) {
            return false
        }

        items[item.articleId] = item
        saveToDisk()
        notifyChanged()

        Log.d(
            TAG,
            "PREFERITO AGGIUNTO/AGGIORNATO " +
                    "id=${item.articleId} code=${item.articleCode}"
        )

        return true
    }

    @Synchronized
    fun toggle(product: ProductInfo): Boolean {
        require(product.articleId > 0L) {
            "Impossibile modificare un preferito senza articleId valido"
        }

        return if (contains(product.articleId)) {
            remove(product.articleId)
            false
        } else {
            add(product)
            true
        }
    }

    @Synchronized
    fun remove(articleId: Long): Boolean {
        val removed = items.remove(articleId) ?: return false

        saveToDisk()
        notifyChanged()

        Log.d(
            TAG,
            "PREFERITO RIMOSSO " +
                    "id=${removed.articleId} code=${removed.articleCode}"
        )

        return true
    }

    @Synchronized
    fun contains(articleId: Long): Boolean =
        items.containsKey(articleId)

    @Synchronized
    fun get(articleId: Long): FavoriteItem? =
        items[articleId]

    @Synchronized
    fun getAll(): List<FavoriteItem> =
        items.values.toList()

    @Synchronized
    fun size(): Int =
        items.size

    @Synchronized
    fun replaceAll(newItems: List<FavoriteItem>) {
        val replacement = linkedMapOf<Long, FavoriteItem>()

        newItems.forEach { item ->
            if (item.articleId > 0L) {
                replacement[item.articleId] = item
            }
        }

        if (items == replacement) {
            return
        }

        items.clear()
        items.putAll(replacement)
        saveToDisk()
        notifyChanged()

        Log.d(
            TAG,
            "FAVORITE STORE SINCRONIZZATO elementi=${items.size}"
        )
    }

    @Synchronized
    fun clear() {
        if (items.isEmpty()) return

        items.clear()
        saveToDisk()
        notifyChanged()

        Log.d(TAG, "FAVORITE STORE SVUOTATO")
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

                val item = FavoriteItem(
                    articleId = articleId,
                    barcode = json.optString("barcode", ""),
                    articleCode = json.optString("articleCode", ""),
                    description = json.optString("description", ""),
                    publicPrice = json.optString("publicPrice", ""),
                    stock = json.optString("stock", "")
                )

                items[item.articleId] = item
            }

            Log.d(
                TAG,
                "FAVORITE STORE RIPRISTINATO elementi=${items.size}"
            )
        } catch (error: Exception) {
            items.clear()

            Log.e(
                TAG,
                "ERRORE RIPRISTINO FAVORITE STORE",
                error
            )
        }
    }

    private fun saveToDisk() {
        val prefs = preferences

        if (prefs == null) {
            Log.w(
                TAG,
                "FAVORITE STORE NON INIZIALIZZATO: salvataggio rimandato"
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
                        put("publicPrice", item.publicPrice)
                        put("stock", item.stock)
                    }
                )
            }

            prefs.edit()
                .putString(ITEMS_KEY, array.toString())
                .apply()
        } catch (error: Exception) {
            Log.e(
                TAG,
                "ERRORE SALVATAGGIO FAVORITE STORE",
                error
            )
        }
    }

    private fun notifyChanged() {
        val count = items.size
        val snapshot = listeners.toList()

        snapshot.forEach { listener ->
            try {
                listener(count)
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "ERRORE LISTENER FAVORITE STORE",
                    error
                )
            }
        }
    }
}