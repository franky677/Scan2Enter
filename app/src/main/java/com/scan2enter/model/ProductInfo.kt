package com.scan2enter.model

import com.scan2enter.api.LocationDto

/**
 * Contiene tutte le informazioni lette automaticamente
 * da Due Retail Mobile o dalle API.
 */
data class ProductInfo(

    /**
     * Identificativo interno dell'articolo nelle API Due Retail.
     */
    val articleId: Long = 0L,

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
     * Stato articolo in Due Retail.
     *
     * true = articolo attivo
     * false = articolo bloccato/non più utilizzato
     */
    val active: Boolean = true,

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
     * Ubicazione singola restituita dall'endpoint articolo precedente.
     *
     * Campo mantenuto temporaneamente per compatibilità con il popup attuale.
     */
    val location: String = "",

    /**
     * Elenco completo delle ubicazioni assegnate all'articolo.
     */
    val locations: List<LocationDto> = emptyList(),

    /**
     * Giacenza.
     */
    val stock: String = "",

    /**
     * Giacenza disponibile.
     */
    val availableStock: String = "",

    /**
     * Scorta minima.
     */
    val minimumStock: String = "",

    /**
     * Scorta massima.
     */
    val maximumStock: String = "",

    /**
     * Lotto di riordino.
     */
    val reorderLot: String = "",

    /**
     * Identificativo del fornitore predefinito.
     */
    val supplierId: Long = 0L,

    /**
     * Ragione sociale del fornitore predefinito.
     */
    val supplierName: String = "",

    /**
     * Codice articolo usato dal fornitore.
     */
    val supplierArticleCode: String = "",

    /**
     * Percorso dell'immagine di copertina restituito dalle API.
     */
    val coverImagePath: String = ""
)