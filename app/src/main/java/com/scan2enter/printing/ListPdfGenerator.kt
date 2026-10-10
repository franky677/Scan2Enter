package com.scan2enter.printing

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.scan2enter.favorites.FavoriteItem
import com.scan2enter.reorder.ReorderItem
import com.scan2enter.session.SessionItem
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object ListPdfGenerator {

    private const val PT_PER_MM = 72f / 25.4f
    private const val PAGE_WIDTH_MM = 210f
    private const val PAGE_HEIGHT_MM = 297f
    private const val MARGIN_MM = 10f

    fun generateFavoritesAndOpen(
        context: Context,
        items: List<FavoriteItem>,
        sortDescription: String
    ): Result<Uri> = runCatching {
        require(items.isNotEmpty()) {
            "Nessun preferito da stampare"
        }

        val rows = items.map { item ->
            ListRow(
                code = item.articleCode,
                description = item.description,
                column1 = item.stock.ifBlank { "”" },
                column2 = formatPrice(item.publicPrice)
            )
        }

        generateAndOpen(
            context = context,
            filePrefix = "Preferiti",
            title = "PREFERITI",
            subtitle = "${items.size} articoli ¢ $sortDescription",
            headers = listOf(
                "Codice",
                "Descrizione",
                "Giac.",
                "Prezzo"
            ),
            rows = rows,
            columnFractions = floatArrayOf(
                0.19f,
                0.55f,
                0.11f,
                0.15f
            )
        )
    }

    fun generateDeliveryReportAndOpen(
        context: Context,
        customerName: String,
        items: List<SessionItem>,
        showPrices: Boolean,
        notes: String,
        barcodeCollo: String
    ): Result<Uri> = runCatching {
        require(items.isNotEmpty()) {
            "Nessun articolo nel Collo veloce"
        }

        val document = PdfDocument()

        try {
            val renderer = renderDeliveryReport(
                document = document,
                customerName = customerName,
                items = items,
                showPrices = showPrices,
                notes = notes
            )

            val validBarcodeCollo =
                barcodeCollo.length == 13 &&
                    barcodeCollo.all(Char::isDigit)

            if (validBarcodeCollo) {
                renderer.startColloBarcodePage(
                    customerName = customerName,
                    barcodeCollo = barcodeCollo,
                    items = items,
                    notes = notes
                )
            }

            renderer.finishPage()

            val uri = writeDocument(
                context = context,
                document = document,
                filePrefix = "RapportinoConsegna"
            )

            openPdf(context, uri)
            uri
        } finally {
            document.close()
        }
    }

    fun generateDeliveryReportForEmail(
        customerName: String,
        items: List<SessionItem>,
        showPrices: Boolean,
        notes: String
    ): Result<ByteArray> = runCatching {
        require(items.isNotEmpty()) {
            "Nessun articolo nel Collo veloce"
        }

        val document = PdfDocument()

        try {
            val renderer = renderDeliveryReport(
                document = document,
                customerName = customerName,
                items = items,
                showPrices = showPrices,
                notes = notes
            )

            renderer.finishPage()

            ByteArrayOutputStream().use { output ->
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    private fun renderDeliveryReport(
        document: PdfDocument,
        customerName: String,
        items: List<SessionItem>,
        showPrices: Boolean,
        notes: String
    ): Renderer {
        val deliveryTotalPages =
            calculateDeliveryReportPageCount(
                itemCount = items.size,
                showPrices = showPrices,
                notes = notes
            )

        val renderer =
            Renderer(
                document = document,
                deliveryTotalPages = deliveryTotalPages
            )

        val totalPieces = items.sumOf { it.quantity }

        renderer.startDeliveryReport(
            title = "RAPPORTINO CONSEGNA MERCE",
            subtitle = "Cliente: ${customerName.ifBlank { "BANCO" }}",
            itemCount = items.size,
            showPrices = showPrices,
            notes = notes
        )

        val headers =
            if (showPrices) {
                listOf(
                    "Codice",
                    "Descrizione",
                    "Qta",
                    "Prezzo",
                    "Totale"
                )
            } else {
                listOf(
                    "Codice",
                    "Descrizione",
                    "Qta"
                )
            }

        val fractions =
            if (showPrices) {
                floatArrayOf(
                    0.18f,
                    0.43f,
                    0.09f,
                    0.14f,
                    0.16f
                )
            } else {
                floatArrayOf(
                    0.22f,
                    0.63f,
                    0.15f
                )
            }

        renderer.drawHeader(
            headers = headers,
            fractions = fractions
        )

        var grandTotal = 0.0

        items.forEach { item ->
            val unitPrice =
                item.basePrice
                    .replace(",", ".")
                    .toDoubleOrNull()
                    ?: 0.0

            val rowTotal =
                unitPrice * item.quantity

            grandTotal += rowTotal

            val values =
                if (showPrices) {
                    listOf(
                        item.articleCode,
                        item.description,
                        item.quantity.toString(),
                        String.format(
                            Locale.ITALY,
                            "%.2f EUR",
                            unitPrice
                        ),
                        String.format(
                            Locale.ITALY,
                            "%.2f EUR",
                            rowTotal
                        )
                    )
                } else {
                    listOf(
                        item.articleCode,
                        item.description,
                        item.quantity.toString()
                    )
                }

            renderer.drawRow(
                values = values,
                fractions = fractions
            )
        }

        renderer.addGap(mm(4f))

        renderer.drawSectionTitle(
            "Totale pezzi: $totalPieces"
        )

        if (showPrices) {
            renderer.drawSectionTitle(
                String.format(
                    Locale.ITALY,
                    "Totale: %.2f EUR",
                    grandTotal
                )
            )
        }

        if (notes.isNotBlank()) {
            renderer.addGap(mm(3f))
            renderer.drawSectionTitle(
                "Note: ${notes.trim()}"
            )
        }

        renderer.finishDeliveryReport()

        return renderer
    }

    fun generateReorderAndOpen(
        context: Context,
        items: List<ReorderItem>,
        filterDescription: String
    ): Result<Uri> = runCatching {
        require(items.isNotEmpty()) {
            "Nessun articolo di riordino da stampare"
        }

        val totalQuantity =
            items.sumOf { it.quantityToOrder }

        val grouped = items.groupBy {
            it.supplierName.trim()
                .ifEmpty { "Fornitore non indicato" }
        }

        val document = PdfDocument()

        try {
            val renderer = Renderer(document)

            renderer.startPage(
                title = "RIORDINO",
                subtitle =
                    "Filtro: $filterDescription ¢ " +
                    "${items.size} articoli ¢ " +
                    "Da ordinare: ${formatNumber(totalQuantity)}"
            )

            grouped.forEach { (supplier, supplierItems) ->
                renderer.ensureSpace(mm(14f))
                renderer.drawSectionTitle(
                    "$supplier ¢ ${supplierItems.size} articoli"
                )

                renderer.drawHeader(
                    headers = listOf(
                        "Codice",
                        "Descrizione",
                        "Cod. forn.",
                        "Giac.",
                        "Min.",
                        "Lotto",
                        "Da ord."
                    ),
                    fractions = floatArrayOf(
                        0.15f,
                        0.31f,
                        0.15f,
                        0.08f,
                        0.08f,
                        0.09f,
                        0.14f
                    )
                )

                supplierItems.forEach { item ->
                    renderer.drawRow(
                        values = listOf(
                            item.articleCode,
                            item.description,
                            item.supplierArticleCode,
                            formatNullable(item.stock),
                            formatNullable(item.minimumStock),
                            formatNullable(item.reorderLot),
                            formatNumber(item.quantityToOrder)
                        ),
                        fractions = floatArrayOf(
                            0.15f,
                            0.31f,
                            0.15f,
                            0.08f,
                            0.08f,
                            0.09f,
                            0.14f
                        )
                    )
                }

                renderer.addGap(mm(3f))
            }

            renderer.finishPage()
            val uri = writeDocument(
                context = context,
                document = document,
                filePrefix = "Riordino"
            )
            openPdf(context, uri)
            uri
        } finally {
            document.close()
        }
    }

    private data class ListRow(
        val code: String,
        val description: String,
        val column1: String,
        val column2: String
    )

    private fun generateAndOpen(
        context: Context,
        filePrefix: String,
        title: String,
        subtitle: String,
        headers: List<String>,
        rows: List<ListRow>,
        columnFractions: FloatArray
    ): Uri {
        val document = PdfDocument()

        try {
            val renderer = Renderer(document)

            renderer.startPage(
                title = title,
                subtitle = subtitle
            )

            renderer.drawHeader(
                headers = headers,
                fractions = columnFractions
            )

            rows.forEach { row ->
                renderer.drawRow(
                    values = listOf(
                        row.code,
                        row.description,
                        row.column1,
                        row.column2
                    ),
                    fractions = columnFractions
                )
            }

            renderer.finishPage()

            val uri = writeDocument(
                context = context,
                document = document,
                filePrefix = filePrefix
            )

            openPdf(context, uri)
            return uri
        } finally {
            document.close()
        }
    }

    private fun calculateDeliveryReportPageCount(
        itemCount: Int,
        showPrices: Boolean,
        notes: String
    ): Int {
        val pageHeight = mm(PAGE_HEIGHT_MM)
        val margin = mm(MARGIN_MM)
        val bottomLimit = pageHeight - margin

        fun initialY(): Float =
            margin +
                    mm(9f) +
                    mm(7f) +
                    mm(3f)

        var pages = 1
        var y = initialY()

        fun ensureSpace(height: Float) {
            if (y + height > bottomLimit) {
                pages += 1
                y = initialY()
            }
        }

        // Intestazione tabella.
        ensureSpace(mm(7f))
        y += mm(7f)

        // Righe articoli.
        repeat(itemCount) {
            ensureSpace(mm(8f))
            y += mm(8f)
        }

        // Spazio + totale pezzi.
        ensureSpace(mm(4f))
        y += mm(4f)

        ensureSpace(mm(7f))
        y += mm(7f)

        // Totale economico.
        if (showPrices) {
            ensureSpace(mm(7f))
            y += mm(7f)
        }

        // Note.
        if (notes.isNotBlank()) {
            ensureSpace(mm(3f))
            y += mm(3f)

            ensureSpace(mm(7f))
            y += mm(7f)
        }

        // Spazio riservato alla firma.
        val signatureHeight = mm(28f)

        if (y + signatureHeight + mm(8f) > bottomLimit) {
            pages += 1
        }

        return pages
    }
    private fun encodeEan13Modules(
        code: String
    ): String {
        if (
            code.length != 13 ||
            !code.all(Char::isDigit)
        ) {
            return ""
        }

        val lPatterns = arrayOf(
            "0001101", "0011001", "0010011", "0111101", "0100011",
            "0110001", "0101111", "0111011", "0110111", "0001011"
        )

        val gPatterns = arrayOf(
            "0100111", "0110011", "0011011", "0100001", "0011101",
            "0111001", "0000101", "0010001", "0001001", "0010111"
        )

        val rPatterns = arrayOf(
            "1110010", "1100110", "1101100", "1000010", "1011100",
            "1001110", "1010000", "1000100", "1001000", "1110100"
        )

        val parity = arrayOf(
            "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
            "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"
        )

        val first = code[0].digitToInt()
        val result = StringBuilder(95)

        result.append("101")

        for (index in 1..6) {
            val digit = code[index].digitToInt()

            result.append(
                if (parity[first][index - 1] == 'L') {
                    lPatterns[digit]
                } else {
                    gPatterns[digit]
                }
            )
        }

        result.append("01010")

        for (index in 7..12) {
            val digit = code[index].digitToInt()
            result.append(rPatterns[digit])
        }

        result.append("101")

        return result.toString()
    }
    private class Renderer(
        private val document: PdfDocument,
        private val deliveryTotalPages: Int? = null
    ) {
        private val pageWidth = mm(PAGE_WIDTH_MM).roundToInt()
        private val pageHeight = mm(PAGE_HEIGHT_MM).roundToInt()
        private val margin = mm(MARGIN_MM)
        private val contentWidth = pageWidth - margin * 2f
        private val bottomLimit = pageHeight - margin

        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = margin

        private var currentTitle = ""
        private var currentSubtitle = ""

        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = mm(6.5f)
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
        }

        private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = mm(3.2f)
        }

        private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = mm(4.0f)
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
        }

        private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = mm(2.6f)
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
        }

        private val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = mm(2.55f)
        }

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(205, 205, 205)
            strokeWidth = mm(0.2f)
        }

        private val headerBackgroundPaint = Paint().apply {
            color = Color.rgb(55, 71, 79)
        }

        fun startPage(
            title: String,
            subtitle: String
        ) {
            currentTitle = title
            currentSubtitle = subtitle
            newPage()
        }

        private fun newPage() {
            finishPage()

            pageNumber += 1

            val pageInfo = PdfDocument.PageInfo.Builder(
                pageWidth,
                pageHeight,
                pageNumber
            ).create()

            page = document.startPage(pageInfo)
            canvas = page!!.canvas
            canvas!!.drawColor(Color.WHITE)

            y = margin

            canvas!!.drawText(
                currentTitle,
                margin,
                y + titlePaint.textSize,
                titlePaint
            )

            y += mm(9f)

            canvas!!.drawText(
                currentSubtitle,
                margin,
                y + subtitlePaint.textSize,
                subtitlePaint
            )

            y += mm(7f)

            // Data: solo giorno/mese/anno, senza ora.
            val stamp =
                SimpleDateFormat(
                    "dd/MM/yyyy",
                    Locale.ITALY
                ).format(Date())

            val stampWidth =
                subtitlePaint.measureText(stamp)

            canvas!!.drawText(
                stamp,
                pageWidth - margin - stampWidth,
                margin + subtitlePaint.textSize,
                subtitlePaint
            )

            // Paginazione del solo rapportino.
            if (deliveryTotalPages != null) {
                val pageText =
                    "Pag. $pageNumber di $deliveryTotalPages"

                val pageTextWidth =
                    subtitlePaint.measureText(pageText)

                canvas!!.drawText(
                    pageText,
                    pageWidth - margin - pageTextWidth,
                    margin + mm(7.0f),
                    subtitlePaint
                )
            }

            canvas!!.drawLine(
                margin,
                y,
                pageWidth - margin,
                y,
                gridPaint
            )

            y += mm(3f)
        }
        fun finishPage() {
            page?.let {
                document.finishPage(it)
            }
            page = null
            canvas = null
        }

        fun ensureSpace(height: Float) {
            if (y + height > bottomLimit) {
                newPage()
            }
        }

        fun addGap(height: Float) {
            ensureSpace(height)
            y += height
        }

        fun drawSectionTitle(text: String) {
            val height = mm(7f)
            ensureSpace(height)

            canvas!!.drawText(
                fitText(
                    text,
                    sectionPaint,
                    contentWidth
                ),
                margin,
                y + sectionPaint.textSize,
                sectionPaint
            )
            y += height
        }

        fun drawReceiptSignature() {
            val requiredHeight = mm(32f)
            ensureSpace(requiredHeight)

            y += mm(8f)

            canvas!!.drawText(
                "FIRMA PER RICEVUTA",
                margin,
                y + sectionPaint.textSize,
                sectionPaint
            )

            y += mm(13f)

            canvas!!.drawLine(
                margin,
                y,
                margin + mm(95f),
                y,
                gridPaint
            )

            y += mm(8f)
        }
        fun startDeliveryReport(
            title: String,
            subtitle: String,
            itemCount: Int,
            showPrices: Boolean,
            notes: String
        ) {
            currentTitle = title
            currentSubtitle = subtitle
            newPage()
        }

        fun finishDeliveryReport() {
            val signatureWidth = mm(75f)
            val signatureHeight = mm(28f)

            // Se la firma non entra, viene creata una nuova pagina
            // che diventa automaticamente l'ultima pagina del rapportino.
            ensureSpace(signatureHeight + mm(8f))

            val signatureY =
                bottomLimit - signatureHeight

            val signatureX =
                pageWidth - margin - signatureWidth

            canvas!!.drawText(
                "FIRMA PER RICEVUTA",
                signatureX,
                signatureY,
                sectionPaint
            )

            canvas!!.drawLine(
                signatureX,
                signatureY + mm(17f),
                signatureX + signatureWidth,
                signatureY + mm(17f),
                gridPaint
            )

            canvas!!.drawText(
                "Firma",
                signatureX,
                signatureY + mm(22f),
                subtitlePaint
            )

            finishPage()
        }
        fun startColloBarcodePage(
            customerName: String,
            barcodeCollo: String,
            items: List<SessionItem>,
            notes: String
        ) {
            currentTitle = "IDENTIFICAZIONE COLLO"
            currentSubtitle =
                "Cliente: ${customerName.ifBlank { "BANCO" }}"
            newPage()

            y += mm(8f)

            canvas!!.drawText(
                "COLLO DA LEGGERE IN CASSA",
                margin,
                y + sectionPaint.textSize,
                sectionPaint
            )

            y += mm(12f)

            val modules = encodeEan13Modules(barcodeCollo)

            if (modules.isEmpty()) {
                error("Impossibile generare il barcode del collo")
            }

            /*
             * Barcode volutamente più compatto.
             * Manteniamo invariata l'altezza e riduciamo solo
             * la larghezza, centrando il codice nella pagina.
             */
            val barcodeWidth = contentWidth * 0.68f
            val barcodeLeft =
                (pageWidth - barcodeWidth) / 2f
            val moduleWidth =
                barcodeWidth / modules.length.toFloat()

            val barcodeHeight = mm(45f)

            val barcodePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                }

            modules.forEachIndexed { index, bit ->
                if (bit == '1') {
                    val left =
                        barcodeLeft +
                                index.toFloat() * moduleWidth

                    canvas!!.drawRect(
                        left,
                        y,
                        left + moduleWidth,
                        y + barcodeHeight,
                        barcodePaint
                    )
                }
            }

            y += barcodeHeight + mm(4f)

            val barcodePaintText =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = mm(5f)
                    typeface =
                        Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                        )
                    textAlign = Paint.Align.CENTER
                }

            canvas!!.drawText(
                barcodeCollo,
                pageWidth / 2f,
                y + barcodePaintText.textSize,
                barcodePaintText
            )

            y += mm(12f)

            val instructionPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = mm(3.5f)
                    textAlign = Paint.Align.CENTER
                }

            canvas!!.drawText(
                "LEGGERE QUESTO CODICE IN CASSA",
                pageWidth / 2f,
                y + instructionPaint.textSize,
                instructionPaint
            )

            // =====================================================
            // CONTENUTO DEL COLLO
            // Prezzi sempre visibili sulla pagina 2.
            // =====================================================

            y += mm(10f)

            drawSectionTitle(
                "CONTENUTO DEL COLLO"
            )

            /*
             * CODICE | DESCRIZIONE | QTA |
             * PREZZO PIENO | SCONTO | PREZZO SCONTATO | TOTALE
             */
            val colloFractions = floatArrayOf(
                0.13f,
                0.29f,
                0.07f,
                0.13f,
                0.10f,
                0.14f,
                0.14f
            )

            drawHeader(
                headers = listOf(
                    "CODICE",
                    "DESCRIZIONE",
                    "QTA",
                    "PREZZO PIENO",
                    "SCONTO",
                    "PREZZO SCONT.",
                    "TOTALE"
                ),
                fractions = colloFractions
            )

            fun parsePrice(value: String): Double {
                val cleaned =
                    value
                        .replace("€", "")
                        .replace(
                            "EUR",
                            "",
                            ignoreCase = true
                        )
                        .replace(" ", "")
                        .trim()

                if (cleaned.isBlank()) {
                    return 0.0
                }

                return if (
                    cleaned.contains(",") &&
                    cleaned.contains(".")
                ) {
                    cleaned
                        .replace(".", "")
                        .replace(",", ".")
                        .toDoubleOrNull()
                        ?: 0.0
                } else {
                    cleaned
                        .replace(",", ".")
                        .toDoubleOrNull()
                        ?: 0.0
                }
            }

            fun formatPrice(value: Double): String =
                String.format(
                    Locale.ITALY,
                    "%.2f",
                    value
                )

            fun formatDiscountValue(value: Double): String {
                if (value <= 0.0001) {
                    return "-"
                }

                return if (
                    kotlin.math.abs(
                        value - kotlin.math.round(value)
                    ) < 0.0001
                ) {
                    String.format(
                        Locale.ITALY,
                        "%.0f%%",
                        value
                    )
                } else {
                    String.format(
                        Locale.ITALY,
                        "%.2f%%",
                        value
                    )
                }
            }

            var colloTotal = 0.0

            items.forEach { item ->

                /*
                 * Prezzo realmente applicato:
                 * usa esattamente la gerarchia già prevista
                 * da SessionItem.
                 */
                val effectivePrice =
                    parsePrice(
                        item.effectivePrice
                            .ifBlank { item.basePrice }
                            .ifBlank { item.publicPrice }
                    )

                /*
                 * Prezzo pieno:
                 * listPrice rappresenta il prezzo di partenza
                 * delle condizioni cliente.
                 *
                 * Se manca, ricadiamo sul prezzo pubblico;
                 * se manca anche quello, sul prezzo effettivo.
                 */
                val fullPriceText =
                    item.listPrice
                        .ifBlank { item.publicPrice }
                        .ifBlank {
                            item.effectivePrice
                                .ifBlank { item.basePrice }
                        }

                val fullPrice =
                    parsePrice(fullPriceText)

                /*
                 * Gli sconti commerciali 1..4 sono successivi,
                 * non vanno sommati.
                 *
                 * Per la stampa mostriamo quindi lo sconto
                 * equivalente complessivo.
                 *
                 * Se esiste uno sconto manuale di riga,
                 * viene applicato successivamente alle condizioni
                 * commerciali.
                 */
                val commercialFactor =
                    (1.0 - item.discount1.coerceIn(0.0, 100.0) / 100.0) *
                    (1.0 - item.discount2.coerceIn(0.0, 100.0) / 100.0) *
                    (1.0 - item.discount3.coerceIn(0.0, 100.0) / 100.0) *
                    (1.0 - item.discount4.coerceIn(0.0, 100.0) / 100.0)

                val manualFactor =
                    1.0 -
                            item.manualDiscount
                                .coerceIn(0.0, 100.0) / 100.0

                val calculatedDiscount =
                    if (fullPrice > 0.0) {
                        (
                            1.0 -
                                    (commercialFactor * manualFactor)
                            ) * 100.0
                    } else {
                        0.0
                    }

                /*
                 * Se c'è un prezzo manuale, Scan2Enter considera
                 * quello come prezzo applicato e non gli attribuiamo
                 * automaticamente una percentuale di sconto.
                 */
                val discountToShow =
                    if (item.manualPrice.isNotBlank()) {
                        0.0
                    } else {
                        calculatedDiscount
                    }

                val rowTotal =
                    effectivePrice * item.quantity

                colloTotal += rowTotal

                drawRow(
                    values = listOf(
                        item.articleCode,
                        item.description,
                        item.quantity.toString(),
                        formatPrice(fullPrice),
                        formatDiscountValue(discountToShow),
                        formatPrice(effectivePrice),
                        formatPrice(rowTotal)
                    ),
                    fractions = colloFractions
                )
            }

            addGap(mm(4f))

            drawSectionTitle(
                "Totale pezzi: ${items.sumOf { it.quantity }}"
            )

            drawSectionTitle(
                String.format(
                    Locale.ITALY,
                    "Totale collo: %.2f EUR",
                    colloTotal
                )
            )

            /*
             * Stesse note del rapportino principale.
             */
            if (notes.isNotBlank()) {
                addGap(mm(3f))
                drawSectionTitle(
                    "Note: ${notes.trim()}"
                )
            }
        }
        fun drawHeader(
            headers: List<String>,
            fractions: FloatArray
        ) {
            val height = mm(7f)
            ensureSpace(height)

            canvas!!.drawRect(
                margin,
                y,
                pageWidth - margin,
                y + height,
                headerBackgroundPaint
            )

            drawCells(
                values = headers,
                fractions = fractions,
                top = y,
                height = height,
                paint = headerPaint
            )

            y += height
        }

        fun drawRow(
            values: List<String>,
            fractions: FloatArray
        ) {
            val height = mm(8f)
            ensureSpace(height)

            drawCells(
                values = values,
                fractions = fractions,
                top = y,
                height = height,
                paint = rowPaint
            )

            canvas!!.drawLine(
                margin,
                y + height,
                pageWidth - margin,
                y + height,
                gridPaint
            )

            y += height
        }

        private fun drawCells(
            values: List<String>,
            fractions: FloatArray,
            top: Float,
            height: Float,
            paint: Paint
        ) {
            var x = margin

            values.forEachIndexed { index, rawValue ->
                val cellWidth =
                    contentWidth * fractions[index]

                val padding = mm(1.2f)
                val maxTextWidth =
                    (cellWidth - padding * 2f)
                        .coerceAtLeast(mm(3f))

                val text =
                    fitText(
                        rawValue.ifBlank { "”" },
                        paint,
                        maxTextWidth
                    )

                val baseline =
                    top +
                    (height - (paint.descent() - paint.ascent())) / 2f -
                    paint.ascent()

                canvas!!.drawText(
                    text,
                    x + padding,
                    baseline,
                    paint
                )

                x += cellWidth

                if (index < values.lastIndex) {
                    canvas!!.drawLine(
                        x,
                        top,
                        x,
                        top + height,
                        gridPaint
                    )
                }
            }
        }

        private fun fitText(
            text: String,
            paint: Paint,
            maxWidth: Float
        ): String {
            val clean =
                text.replace(
                    Regex("\\s+"),
                    " "
                ).trim()

            if (
                clean.isEmpty() ||
                paint.measureText(clean) <= maxWidth
            ) {
                return clean
            }

            val suffix = "¦"
            var low = 0
            var high = clean.length

            while (low < high) {
                val mid = (low + high + 1) / 2
                val candidate =
                    clean.take(mid).trimEnd() + suffix

                if (
                    paint.measureText(candidate) <= maxWidth
                ) {
                    low = mid
                } else {
                    high = mid - 1
                }
            }

            return clean.take(low).trimEnd() + suffix
        }
    }

    private fun writeDocument(
        context: Context,
        document: PdfDocument,
        filePrefix: String
    ): Uri {
        val stamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.ITALY
            ).format(Date())

        val baseDir =
            context.getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS
            ) ?: context.filesDir

        val reportDir =
            File(
                baseDir,
                "Scan2Enter"
            ).apply {
                if (!exists() && !mkdirs()) {
                    error("Impossibile creare la cartella dei report PDF")
                }
            }

        val pdfFile =
            File(
                reportDir,
                "${filePrefix}_$stamp.pdf"
            )

        pdfFile.outputStream().use { output ->
            document.writeTo(output)
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
    }

    private fun openPdf(
        context: Context,
        uri: Uri
    ) {
        val intent = Intent(
            Intent.ACTION_VIEW
        ).apply {
            setDataAndType(
                uri,
                "application/pdf"
            )
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        context.startActivity(intent)
    }

    private fun formatPrice(value: String): String {
        val number =
            value.replace(",", ".")
                .toDoubleOrNull()

        return if (number == null) {
            value.ifBlank { "”" }
        } else {
            String.format(
                Locale.ITALY,
                "%.2f €‚¬",
                number
            )
        }
    }

    private fun formatNullable(
        value: Double?
    ): String =
        value?.let(::formatNumber) ?: "”"

    private fun formatNumber(
        value: Double
    ): String {
        val rounded = value.roundToInt()

        return if (
            kotlin.math.abs(value - rounded) < 0.0001
        ) {
            rounded.toString()
        } else {
            String.format(
                Locale.ITALY,
                "%.2f",
                value
            )
        }
    }

    private fun mm(value: Float): Float =
        value * PT_PER_MM
}

