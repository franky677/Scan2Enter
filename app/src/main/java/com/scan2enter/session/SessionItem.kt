package com.scan2enter.session

data class SessionItem(
    val articleId: Long,
    val articleCode: String,
    val description: String,
    val barcode: String,
    val publicPrice: String,
    val stock: String,
    val quantity: Int,
    val priceListName: String = "",
    val listPrice: String = "",
    val discount1: Double = 0.0,
    val finalPrice: String = "",
    val manualPrice: String = ""
) {
    val effectivePrice: String
        get() = manualPrice.ifBlank {
            finalPrice.ifBlank { publicPrice }
        }
}