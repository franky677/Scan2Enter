package com.scan2enter.favorites

import android.content.Context
import com.scan2enter.model.ProductInfo

object FavoriteRepository {

    fun initialize(context: Context) {
        FavoriteStore.initialize(context)
    }

    fun toggle(product: ProductInfo): Boolean {
        return FavoriteStore.toggle(product)
    }

    fun add(product: ProductInfo): Boolean {
        return FavoriteStore.add(product)
    }

    fun remove(articleId: Long): Boolean {
        return FavoriteStore.remove(articleId)
    }

    fun isFavorite(articleId: Long): Boolean {
        return FavoriteStore.contains(articleId)
    }

    fun getAll(): List<FavoriteItem> {
        return FavoriteStore.getAll()
    }

    fun size(): Int {
        return FavoriteStore.size()
    }

    fun clear() {
        FavoriteStore.clear()
    }

    fun addListener(listener: (Int) -> Unit) {
        FavoriteStore.addListener(listener)
    }

    fun removeListener(listener: (Int) -> Unit) {
        FavoriteStore.removeListener(listener)
    }
}