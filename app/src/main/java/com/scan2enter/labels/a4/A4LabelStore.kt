package com.scan2enter.labels.a4

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

object A4LabelStore {

    const val PAGE_CAPACITY = 18

    private const val PREFS_NAME = "a4_label_store"
    private const val KEY_ITEMS = "page_1_items"

    private var initialized = false
    private lateinit var appContext: Context

    private val items = mutableListOf<A4LabelItem>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        load()
        initialized = true
    }

    @Synchronized
    fun getItems(): List<A4LabelItem> = items.toList()

    @Synchronized
    fun add(item: A4LabelItem): AddResult {
        ensureInitialized()

        if (items.any { existing ->
                existing.articleId == item.articleId ||
                        existing.barcode == item.barcode
            }
        ) {
            return AddResult.DUPLICATE
        }

        if (items.size >= PAGE_CAPACITY) {
            return AddResult.PAGE_FULL
        }

        items.add(item)
        save()
        notifyListeners()
        return AddResult.ADDED
    }

    @Synchronized
    fun remove(item: A4LabelItem) {
        ensureInitialized()
        items.removeAll {
            it.articleId == item.articleId ||
                    it.barcode == item.barcode
        }
        save()
        notifyListeners()
    }

    @Synchronized
    fun clearPage() {
        ensureInitialized()
        items.clear()
        save()
        notifyListeners()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { listener ->
            runCatching { listener() }
        }
    }

    private fun ensureInitialized() {
        check(initialized) {
            "A4LabelStore non inizializzato"
        }
    }

    private fun load() {
        items.clear()

        val raw = appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]")
            .orEmpty()

        runCatching {
            val array = JSONArray(raw)

            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)

                items.add(
                    A4LabelItem(
                        articleId = obj.optLong("articleId"),
                        barcode = obj.optString("barcode"),
                        articleCode = obj.optString("articleCode"),
                        description = obj.optString("description"),
                        publicPrice = obj.optString("publicPrice"),
                        season = obj.optString("season"),
                        year = obj.optString("year")
                    )
                )
            }
        }
    }

    private fun save() {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("articleId", item.articleId)
                    .put("barcode", item.barcode)
                    .put("articleCode", item.articleCode)
                    .put("description", item.description)
                    .put("publicPrice", item.publicPrice)
                    .put("season", item.season)
                    .put("year", item.year)
            )
        }

        appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }

    enum class AddResult {
        ADDED,
        DUPLICATE,
        PAGE_FULL
    }
}