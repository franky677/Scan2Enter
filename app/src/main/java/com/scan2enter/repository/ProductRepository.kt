package com.scan2enter.repository

import com.scan2enter.api.DueRetailApiClient
import com.scan2enter.api.DueRetailStockSettings
import com.scan2enter.api.GatewayApiClient
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

    fun getProduct(barcode: String): Result<ProductInfo> {
        return gatewayApi.getProductByBarcode(barcode)
            .map { product ->
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
