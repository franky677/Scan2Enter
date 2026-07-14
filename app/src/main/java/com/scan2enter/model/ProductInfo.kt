package com.scan2enter.model

/**
 * Contiene tutte le informazioni lette automaticamente
 * da Due Retail Mobile.
 */
data class ProductInfo(

    /**
     * Codice articolo.
     */
    val articleCode: String = "",

    /**
     * Descrizione articolo.
     */
    val description: String = "",

    /**
     * Codice a barre.
     */
    val barcode: String = "",

    /**
     * Prezzo imponibile.
     */
    val taxablePrice: String = "",

    /**
     * Aliquota IVA.
     */
    val vatRate: String = "",

    /**
     * Prezzo al pubblico ivato.
     */
    val publicPrice: String = "",

    /**
     * Stagione.
     */
    val season: String = "",

    /**
     * Anno.
     */
    val year: String = "",

    /**
     * Giacenza.
     */
    val stock: String = ""
)