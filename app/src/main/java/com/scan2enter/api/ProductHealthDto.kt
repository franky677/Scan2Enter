package com.scan2enter.api

data class ProductHealthDto(
    val idArticolo: Long,
    val barcode: String,
    val giacenzaFifo: Double,
    val valoreFifo: Double,
    val costoMedioFifo: Double,
    val ultimaVendita: String?,
    val giorniDaUltimaVendita: Int?,
    val venduto12M: Double,
    val venduto24M: Double,

    // Salute Articolo V2
    val vendutoAnnoPrecedente: Double,
    val mesiConVendite12M: Int,
    val punteggioCommerciale: Int,
    val punteggioEconomico: Int,
    val descrizioneCommerciale: String,
    val descrizioneEconomica: String,

    val rotazione12M: Double?,
    val mesiCopertura: Double?,

    // V1 mantenuta temporaneamente per compatibilità
    val statoSalute: String,
    val descrizioneSalute: String
)