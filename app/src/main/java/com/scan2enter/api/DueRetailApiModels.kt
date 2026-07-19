package com.scan2enter.api

data class DueRetailProductSummary(
    val id: Long,
    val articleCode: String,
    val description: String,
    val vatRate: Double,
    val year: String,
    val season: String,
    val minimumStock: Double,
    val maximumStock: Double,
    val reorderLot: Double
)

data class DueRetailProductDetail(
    val id: Long,
    val articleCode: String,
    val description: String,
    val barcode: String,
    val vatRate: Double,
    val year: String,
    val season: String,
    val publicPrice: Double?,
    val taxablePrice: Double?,
    val stock: Double?,
    val availableStock: Double?,
    val minimumStock: Double,
    val maximumStock: Double,
    val reorderLot: Double,
    val rawJson: String
)
