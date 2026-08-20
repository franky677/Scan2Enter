package com.scan2enter.api

/**
 * Risposta dell'endpoint:
 * GET /api/product/{barcode}
 *
 * I valori numerici visualizzati dal popup arrivano già come stringhe
 * dal Gateway, così vengono preservati anche i valori null convertiti in "".
 */
data class GatewayProductDto(
    val articleId: Long,
    val articleCode: String,
    val description: String,
    val barcode: String,
    val active: Boolean = true,
    val taxablePrice: String,
    val vatRate: String,
    val publicPrice: String,
    val season: String,
    val year: String,
    val location: String,
    val stock: String,
    val availableStock: String,
    val minimumStock: String,
    val maximumStock: String,
    val reorderLot: String,
    val supplierId: Long,
    val supplierName: String,
    val supplierArticleCode: String,
    val coverImagePath: String
)