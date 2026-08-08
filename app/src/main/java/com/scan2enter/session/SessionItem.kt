package com.scan2enter.session

data class SessionItem(
    val articleId: Long,
    val articleCode: String,
    val description: String,
    val barcode: String,
    val publicPrice: String,
    val stock: String,
    val quantity: Int
)
