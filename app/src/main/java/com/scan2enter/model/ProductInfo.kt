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
     * Stagione.
     */
    val season: String = "",

    /**
     * Anno.
     */
    val year: String = "",

    /**
     * Prezzo al pubblico.
     */
    val publicPrice: String = "",

    /**
     * Giacenza.
     */
    val stock: String = ""
)