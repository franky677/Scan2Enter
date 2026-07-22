package com.scan2enter.repository

import com.scan2enter.api.DueRetailApiClient
import com.scan2enter.api.DueRetailStockSettings
import com.scan2enter.model.ProductInfo
import java.util.Locale

class ProductRepository(
    private val api: DueRetailApiClient
) {

    fun getProduct(barcode: String): Result<ProductInfo> {
        return api.getProductByBarcode(barcode)
            .map { product ->
                ProductInfo(
                    articleId = product.id,
                    articleCode = product.articleCode,
                    description = product.description,
                    barcode = product.barcode,

                    taxablePrice = product.taxablePrice
                        ?.formatPrice()
                        ?: "",

                    vatRate = product.vatRate
                        .formatVat(),

                    publicPrice = product.publicPrice
                        ?.formatPrice()
                        ?: "",

                    season = product.season,
                    year = product.year,

                    stock = product.stock
                        ?.formatQuantity()
                        ?: "",

                    availableStock = product.availableStock
                        ?.formatQuantity()
                        ?: "",

                    minimumStock = product.minimumStock
                        .takeIf { it >= 0.0 }
                        ?.formatQuantity()
                        ?: "",

                    maximumStock = product.maximumStock
                        .takeIf { it >= 0.0 }
                        ?.formatQuantity()
                        ?: "",

                    reorderLot = product.reorderLot
                        .takeIf { it >= 0.0 }
                        ?.formatQuantity()
                        ?: ""
                )
            }
    }

    /**
     * Aggiorna uno o più parametri di riordino.
     *
     * I campi null restano invariati.
     */
    fun updateStockSettings(
        articleId: Long,
        minimumStock: Double? = null,
        maximumStock: Double? = null,
        reorderLot: Double? = null
    ): Result<DueRetailStockSettings> {
        return api.updateStockSettings(
            articleId = articleId,
            minimumStock = minimumStock,
            maximumStock = maximumStock,
            reorderLot = reorderLot
        )
    }

    private fun Double.formatVat(): String =
        if (this == this.toInt().toDouble()) {
            this.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", this)
        }

    private fun Double.formatPrice(): String =
        String.format(Locale.US, "%.2f", this)

    private fun Double.formatQuantity(): String =
        if (this == this.toInt().toDouble()) {
            this.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", this)
        }
}