package com.scan2enter.reorder

/**
 * Rappresenta un articolo inserito nella lista di riordino.
 *
 * I dati vengono copiati da ProductInfo così la lista può continuare
 * a funzionare senza dover interrogare nuovamente le API.
 */
data class ReorderItem(

    val articleId: Long,

    val barcode: String,
    val articleCode: String,
    val description: String,

    val supplierId: Long,
    val supplierName: String,
    val supplierArticleCode: String,

    val purchaseTaxable: Double? = null,
    val purchasePrice: Double? = null,
    val vatRate: Double? = null,

    val stock: Double?,
    val availableStock: Double?,
    val minimumStock: Double?,
    val maximumStock: Double?,
    val reorderLot: Double?,

    val quantityToOrder: Double = 0.0
)