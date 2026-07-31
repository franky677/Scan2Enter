package com.scan2enter.favorites

import com.scan2enter.model.ProductInfo

data class FavoriteItem(
    val articleId: Long,
    val barcode: String,
    val articleCode: String,
    val description: String,
    val publicPrice: String,
    val stock: String
) {
    companion object {
        fun fromProduct(product: ProductInfo): FavoriteItem {
            return FavoriteItem(
                articleId = product.articleId,
                barcode = product.barcode.trim(),
                articleCode = product.articleCode.trim(),
                description = product.description.trim(),
                publicPrice = product.publicPrice.trim(),
                stock = product.stock.trim()
            )
        }
    }
}