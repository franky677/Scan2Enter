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
                column1 = item.stock.ifBlank { "—" },
                column2 = formatPrice(item.publicPrice)
            )
        }

        generateAndOpen(
            context = context,
            filePrefix = "Preferiti",
            title = "PREFERITI",
            subtitle = "${items.size} articoli • $sortDescription",
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
                    "Filtro: $filterDescription • " +
                    "${items.size} articoli • " +
                    "Da ordinare: ${formatNumber(totalQuantity)}"
            )

            grouped.forEach { (supplier, supplierItems) ->
                renderer.ensureSpace(mm(14f))
                renderer.drawSectionTitle(
                    "$supplier • ${supplierItems.size} articoli"
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

    private class Renderer(
        private val document: PdfDocument
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

            val stamp =
                SimpleDateFormat(
                    "dd/MM/yyyy HH:mm",
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
                        rawValue.ifBlank { "—" },
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

            val suffix = "…"
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
            value.ifBlank { "—" }
        } else {
            String.format(
                Locale.ITALY,
                "%.2f €",
                number
            )
        }
    }

    private fun formatNullable(
        value: Double?
    ): String =
        value?.let(::formatNumber) ?: "—"

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
