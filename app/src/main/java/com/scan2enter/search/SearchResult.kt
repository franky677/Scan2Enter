package com.scan2enter.search

data class SearchResult(
    val id: Long,
    val code: String,
    val description: String,
    val barcode: String,
    val price: String,
    val stock: String,
    val moved: Boolean,
    val lastMovement: String?
)
