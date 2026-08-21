package com.scan2enter.session

data class SessionItem(
    val articleId: Long,
    val articleCode: String,
    val description: String,
    val barcode: String,
    val publicPrice: String,
    val stock: String,
    val quantity: Int,
    val priceListId: Int = 1,
    val priceListName: String = "",
    val listPrice: String = "",
    val discount1: Double = 0.0,
    val finalPrice: String = "",
    val manualPrice: String = "",
    val effectiveMarkupPercent: Double? = null,
    val roundingPrice: String = "",
    val roundingAdjustment: String = ""
) {
    val basePrice: String
        get() = manualPrice.ifBlank {
            finalPrice.ifBlank { publicPrice }
        }

    val effectivePrice: String
        get() = roundingPrice.ifBlank { basePrice }
}