package com.scan2enter.labels.a4

import com.scan2enter.model.ProductInfo

data class A4LabelItem(
    val articleId: Long,
    val barcode: String,
    val articleCode: String,
    val description: String,
    val publicPrice: String,
    val season: String,
    val year: String
) {
    companion object {
        fun fromProduct(product: ProductInfo): A4LabelItem {
            return A4LabelItem(
                articleId = product.articleId,
                barcode = product.barcode.trim(),
                articleCode = product.articleCode.trim(),
                description = product.description.trim(),
                publicPrice = product.publicPrice.trim(),
                season = product.season.trim(),
                year = product.year.trim()
            )
        }
    }
}