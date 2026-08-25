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
    val rotazione12M: Double?,
    val mesiCopertura: Double?,
    val statoSalute: String,
    val descrizioneSalute: String
)
