package com.scan2enter.repository

import android.util.Log
import com.scan2enter.api.DueRetailApiClient
import com.scan2enter.api.DueRetailStockSettings
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.api.LocationDto
import com.scan2enter.api.DeleteLocationResult
import com.scan2enter.model.ProductInfo

/**
 * Le letture articolo passano dal Gateway.
 *
 * Le scritture delle scorte restano temporaneamente sulla WebAPI Due Retail,
 * finché il Gateway non avrà il relativo endpoint di aggiornamento.
 */
class ProductRepository(
    private val gatewayApi: GatewayApiClient,
    private val dueRetailWriteApi: DueRetailApiClient
) {

    companion object {
        private const val TAG = "Scan2Enter"
    }

    fun getProduct(barcode: String): Result<ProductInfo> = runCatching {
        val product = gatewayApi
            .getProductByBarcode(barcode)
            .getOrThrow()

        /*
         * Un errore nella lettura delle ubicazioni non deve impedire
         * la visualizzazione dell'intero articolo.
         */
        val locations = gatewayApi
            .getProductLocations(product.articleId)
            .getOrElse { error ->
                Log.e(
                    TAG,
                    "ERRORE LETTURA UBICAZIONI " +
                            "ARTICLE ID=${product.articleId}: " +
                            error.message,
                    error
                )

                emptyList()
            }

        ProductInfo(
            articleId = product.articleId,
            articleCode = product.articleCode,
            description = product.description,
            barcode = product.barcode,

            taxablePrice = product.taxablePrice,
            vatRate = product.vatRate,
            publicPrice = product.publicPrice,

            season = product.season,
            year = product.year,
            location = product.location,
            locations = locations,

            stock = product.stock,
            availableStock = product.availableStock,
            minimumStock = product.minimumStock,
            maximumStock = product.maximumStock,
            reorderLot = product.reorderLot,

            supplierId = product.supplierId,
            supplierName = product.supplierName,
            supplierArticleCode =
                product.supplierArticleCode,
            coverImagePath = product.coverImagePath
        )
    }

    /**
     * Restituisce tutte le ubicazioni disponibili.
     */
    fun getLocations(): Result<List<LocationDto>> {
        return gatewayApi.getLocations()
    }

    /**
     * Rilegge le ubicazioni attualmente assegnate all'articolo.
     */
    fun getProductLocations(
        articleId: Long
    ): Result<List<LocationDto>> {
        return gatewayApi.getProductLocations(articleId)
    }

    /**
     * Assegna un'ubicazione all'articolo.
     *
     * true = aggiunta effettuata
     * false = associazione già presente
     */
    fun addLocation(
        articleId: Long,
        locationId: Int
    ): Result<Boolean> {
        return gatewayApi.addLocation(
            articleId = articleId,
            locationId = locationId
        )
    }

    /**
     * Rimuove un'ubicazione dall'articolo.
     *
     * true = rimozione effettuata
     * false = associazione non presente
     */
    fun removeLocation(
        articleId: Long,
        locationId: Int
    ): Result<Boolean> {
        return gatewayApi.removeLocation(
            articleId = articleId,
            locationId = locationId
        )
    }

    fun createLocation(name: String): Result<LocationDto> {
        return gatewayApi.createLocation(name)
    }

    fun deleteLocation(locationId: Int): Result<DeleteLocationResult> {
        return gatewayApi.deleteLocation(locationId)
    }


    fun renameLocation(
        locationId: Int,
        name: String
    ): Result<LocationDto> {
        return gatewayApi.renameLocation(locationId, name)
    }

    fun duplicateNextLocation(
        locationId: Int
    ): Result<LocationDto> {
        return gatewayApi.duplicateNextLocation(locationId)
    }

    /**
     * Aggiorna uno o più parametri di riordino.
     *
     * Questa operazione usa ancora temporaneamente la WebAPI Due Retail.
     * I campi null restano invariati.
     */
    fun updateStockSettings(
        articleId: Long,
        minimumStock: Double? = null,
        maximumStock: Double? = null,
        reorderLot: Double? = null
    ): Result<DueRetailStockSettings> {
        return dueRetailWriteApi.updateStockSettings(
            articleId = articleId,
            minimumStock = minimumStock,
            maximumStock = maximumStock,
            reorderLot = reorderLot
        )
    }
}