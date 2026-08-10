package com.scan2enter.labels.a4

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object A4PdfGenerator {

    private const val GATEWAY_BASE_URL = "http://192.168.1.30:5055"

    /*
     * PdfDocument usa punti tipografici:
     * 1 pollice = 72 punti, 1 pollice = 25,4 mm.
     */
    private const val POINTS_PER_MM = 72f / 25.4f

    private const val PAGE_WIDTH_MM = 210f
    private const val PAGE_HEIGHT_MM = 297f

    // Compensazione della riduzione rilevata in stampa Epson:
    // 74 mm richiesti -> 72 mm stampati; 31,5 mm -> 30,5 mm.
    private const val LABEL_WIDTH_MM = 76.06f
    private const val LABEL_HEIGHT_MM = 32.53f

    private const val COLUMNS = 2
    private const val ROWS = 9
    private const val LABELS_PER_PAGE = COLUMNS * ROWS

    fun generateAndOpen(
        context: Context,
        items: List<A4LabelItem>,
        showArticlePrefix: Boolean = false
    ): Result<Uri> = runCatching {
        require(items.isNotEmpty()) {
            "Nessun articolo da stampare"
        }

        val document = PdfDocument()

        try {
            val pageWidth = mm(PAGE_WIDTH_MM).toInt()
            val pageHeight = mm(PAGE_HEIGHT_MM).toInt()

            val pageInfo = PdfDocument.PageInfo.Builder(
                pageWidth,
                pageHeight,
                1
            ).create()

            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawColor(Color.WHITE)

            /*
             * Le 18 etichette vengono centrate nel foglio:
             * larghezza griglia = 148 mm;
             * altezza griglia = 283,5 mm.
             */
            val gridWidth = mm(LABEL_WIDTH_MM * COLUMNS)
            val gridHeight = mm(LABEL_HEIGHT_MM * ROWS)

            val startX = (pageWidth - gridWidth) / 2f
            val startY = (pageHeight - gridHeight) / 2f

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = mm(0.25f)
            }

            val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
                textSize = mm(3.2f)
                typeface = Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )
            }

            val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
                textSize = mm(3.2f)
                typeface = Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )
            }

            val descriptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
                textSize = mm(2.6f)
                typeface = Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.NORMAL
                )
            }

            val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
                textAlign = Paint.Align.RIGHT
                textSize = mm(5.2f)
                typeface = Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )
            }

            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
                textAlign = Paint.Align.RIGHT
                textSize = mm(2.15f)
                typeface = Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )
            }

            val imagePlaceholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = mm(0.22f)
                pathEffect = android.graphics.DashPathEffect(
                    floatArrayOf(mm(1.6f), mm(1.1f)),
                    0f
                )
            }

            for (index in 0 until LABELS_PER_PAGE) {
                val column = index % COLUMNS
                val row = index / COLUMNS

                val left =
                    startX + column * mm(LABEL_WIDTH_MM)
                val top =
                    startY + row * mm(LABEL_HEIGHT_MM)
                val right = left + mm(LABEL_WIDTH_MM)
                val bottom = top + mm(LABEL_HEIGHT_MM)

                val bounds = RectF(left, top, right, bottom)
                canvas.drawRect(bounds, borderPaint)

                val padding = mm(2f)

                canvas.drawText(
                    (index + 1).toString(),
                    left + padding,
                    top + mm(4.2f),
                    numberPaint
                )

                val item = items.getOrNull(index) ?: continue

                /*
                 * Layout vicino alle etichette già presenti in negozio.
                 */
                val imageBounds = RectF(
                    left + mm(2.4f),
                    top + mm(3.2f),
                    left + mm(17.2f),
                    bottom - mm(2.5f)
                )

                val productImage = loadProductImage(item.barcode)

                if (productImage != null) {
                    drawBitmapFitCenter(
                        canvas = canvas,
                        bitmap = productImage,
                        bounds = imageBounds
                    )
                    productImage.recycle()
                } else {
                    canvas.drawRect(
                        imageBounds,
                        imagePlaceholderPaint
                    )
                }

                val contentLeft = left + mm(19.0f)
                val contentRight = right - padding

                canvas.drawText(
                    formatSeasonYear(item),
                    right - mm(1.8f),
                    top + mm(3.1f),
                    datePaint
                )

                drawEan13(
                    canvas = canvas,
                    rawBarcode = item.barcode,
                    bounds = RectF(
                        contentLeft,
                        top + mm(2.2f),
                        right - mm(21.0f),
                        top + mm(9.2f)
                    )
                )

                drawFittedSingleLine(
                    canvas = canvas,
                    text = formatEanForDisplay(item.barcode),
                    paint = descriptionPaint,
                    left = contentLeft,
                    right = right - mm(21.0f),
                    baseline = top + mm(12.7f),
                    maxTextSize = mm(3.15f),
                    minTextSize = mm(2.2f),
                    centered = true
                )

                drawFittedSingleLine(
                    canvas = canvas,
                    text = displayArticleCode(
                        item.articleCode,
                        showArticlePrefix
                    ),
                    paint = codePaint,
                    left = contentLeft,
                    right = right - mm(20f),
                    baseline = top + mm(18.0f),
                    maxTextSize = mm(3.4f),
                    minTextSize = mm(2.2f)
                )

                drawFittedSingleLine(
                    canvas = canvas,
                    text = item.description,
                    paint = descriptionPaint,
                    left = contentLeft,
                    right = contentRight,
                    baseline = top + mm(22.6f),
                    maxTextSize = mm(2.55f),
                    minTextSize = mm(1.7f)
                )

                canvas.drawText(
                    formatPrice(item.publicPrice),
                    right - padding,
                    top + mm(29.1f),
                    pricePaint
                )
            }

            document.finishPage(page)

            val pdfFile = createDownloadFile(context)
            pdfFile.outputStream().use { output ->
                document.writeTo(output)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            openPdf(context, uri)
            uri
        } finally {
            document.close()
        }
    }

    private fun createDownloadFile(context: Context): File {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.ITALY
        ).format(Date())

        val baseDir =
            context.getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS
            ) ?: context.filesDir

        val reportDir = File(
            baseDir,
            "Scan2Enter"
        ).apply {
            if (!exists() && !mkdirs()) {
                error("Impossibile creare la cartella dei report PDF")
            }
        }

        return File(
            reportDir,
            "Scan2Enter_Etichette_A4_$timestamp.pdf"
        )
    }

    private fun openPdf(
        context: Context,
        uri: Uri
    ) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(
            intent,
            "Apri o stampa etichette"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooser)
    }

    private fun drawFittedSingleLine(
        canvas: android.graphics.Canvas,
        text: String,
        paint: Paint,
        left: Float,
        right: Float,
        baseline: Float,
        maxTextSize: Float,
        minTextSize: Float,
        centered: Boolean = false
    ) {
        val clean = text
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .ifBlank { "ARTICOLO" }

        paint.textSize = maxTextSize

        while (
            paint.textSize > minTextSize &&
            paint.measureText(clean) > right - left
        ) {
            paint.textSize -= 0.4f
        }

        val fitted = if (paint.measureText(clean) <= right - left) {
            clean
        } else {
            ellipsize(clean, paint, right - left)
        }

        val originalAlignment = paint.textAlign
        paint.textAlign =
            if (centered) Paint.Align.CENTER else Paint.Align.LEFT

        canvas.drawText(
            fitted,
            if (centered) (left + right) / 2f else left,
            baseline,
            paint
        )

        paint.textAlign = originalAlignment
        paint.textSize = maxTextSize
    }

    private fun ellipsize(
        text: String,
        paint: Paint,
        maxWidth: Float
    ): String {
        if (paint.measureText(text) <= maxWidth) {
            return text
        }

        var result = text

        while (
            result.length > 1 &&
            paint.measureText("$result…") > maxWidth
        ) {
            result = result.dropLast(1)
        }

        return "$result…"
    }

    private fun displayArticleCode(
        code: String,
        showPrefix: Boolean
    ): String {
        val clean = code.trim()

        return if (showPrefix || clean.length <= 3) {
            clean
        } else {
            clean.drop(3)
        }
    }

    private fun drawEan13(
        canvas: android.graphics.Canvas,
        rawBarcode: String,
        bounds: RectF
    ) {
        val barcode = rawBarcode
            .filter(Char::isDigit)
            .takeLast(13)

        if (!isValidEan13(barcode)) {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = mm(2.2f)
                typeface = Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )
                textAlign = Paint.Align.CENTER
            }

            canvas.drawText(
                barcode.ifBlank { "EAN NON VALIDO" },
                bounds.centerX(),
                bounds.centerY(),
                fallbackPaint
            )
            return
        }

        val modules = buildEan13Modules(barcode)
        val quietModules = 9
        val totalModules = modules.length + quietModules * 2
        val moduleWidth = bounds.width() / totalModules
        val startX = bounds.left + quietModules * moduleWidth

        val barPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        modules.forEachIndexed { index, bit ->
            if (bit != '1') return@forEachIndexed

            val guard =
                index < 3 ||
                        index in 45..49 ||
                        index >= 92

            val barBottom =
                if (guard) {
                    bounds.bottom
                } else {
                    bounds.bottom - mm(0.65f)
                }

            canvas.drawRect(
                startX + index * moduleWidth,
                bounds.top,
                startX + (index + 1) * moduleWidth,
                barBottom,
                barPaint
            )
        }
    }

    private fun isValidEan13(value: String): Boolean {
        if (value.length != 13 || value.any { !it.isDigit() }) {
            return false
        }

        val expected = value.last().digitToInt()
        var sum = 0

        for (index in 0 until 12) {
            val digit = value[index].digitToInt()
            sum += if (index % 2 == 0) digit else digit * 3
        }

        val calculated = (10 - sum % 10) % 10
        return calculated == expected
    }

    private fun buildEan13Modules(ean: String): String {
        val leftPatterns = arrayOf(
            arrayOf("0001101", "0100111"),
            arrayOf("0011001", "0110011"),
            arrayOf("0010011", "0011011"),
            arrayOf("0111101", "0100001"),
            arrayOf("0100011", "0011101"),
            arrayOf("0110001", "0111001"),
            arrayOf("0101111", "0000101"),
            arrayOf("0111011", "0010001"),
            arrayOf("0110111", "0001001"),
            arrayOf("0001011", "0010111")
        )

        val parityPatterns = arrayOf(
            "LLLLLL",
            "LLGLGG",
            "LLGGLG",
            "LLGGGL",
            "LGLLGG",
            "LGGLLG",
            "LGGGLL",
            "LGLGLG",
            "LGLGGL",
            "LGGLGL"
        )

        val rightPatterns = arrayOf(
            "1110010",
            "1100110",
            "1101100",
            "1000010",
            "1011100",
            "1001110",
            "1010000",
            "1000100",
            "1001000",
            "1110100"
        )

        return buildString(95) {
            append("101")

            val parity = parityPatterns[ean[0].digitToInt()]

            for (index in 1..6) {
                val digit = ean[index].digitToInt()
                val patternIndex =
                    if (parity[index - 1] == 'G') 1 else 0

                append(leftPatterns[digit][patternIndex])
            }

            append("01010")

            for (index in 7..12) {
                append(rightPatterns[ean[index].digitToInt()])
            }

            append("101")
        }
    }

    private fun formatEanForDisplay(rawBarcode: String): String {
        val barcode = rawBarcode.filter(Char::isDigit)

        return if (barcode.length == 13) {
            "${barcode.substring(0, 1)} " +
                    "${barcode.substring(1, 7)} " +
                    barcode.substring(7)
        } else {
            barcode
        }
    }

    private fun formatSeasonYear(item: A4LabelItem): String {
        return listOf(
            item.season.trim(),
            item.year.trim()
        )
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .uppercase(Locale.ITALIAN)
    }

    private fun loadProductImage(barcode: String): Bitmap? {
        val cleanBarcode = barcode.trim().filter(Char::isDigit)
        if (cleanBarcode.isBlank()) return null

        val encoded = URLEncoder.encode(
            cleanBarcode,
            StandardCharsets.UTF_8.name()
        )

        val connection = (
                URL("$GATEWAY_BASE_URL/api/product/$encoded/image")
                    .openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 15_000
                setRequestProperty("Accept", "image/*")
            }

        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.use(BitmapFactory::decodeStream)
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun drawBitmapFitCenter(
        canvas: android.graphics.Canvas,
        bitmap: Bitmap,
        bounds: RectF
    ) {
        if (bitmap.width <= 0 || bitmap.height <= 0) return

        val scale = minOf(
            bounds.width() / bitmap.width.toFloat(),
            bounds.height() / bitmap.height.toFloat()
        )

        val width = bitmap.width * scale
        val height = bitmap.height * scale

        val destination = RectF(
            bounds.centerX() - width / 2f,
            bounds.centerY() - height / 2f,
            bounds.centerX() + width / 2f,
            bounds.centerY() + height / 2f
        )

        canvas.drawBitmap(
            bitmap,
            null,
            destination,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun formatPrice(raw: String): String {
        val cleaned = raw
            .trim()
            .replace("€", "")
            .replace(" ", "")

        val normalized = when {
            cleaned.contains(',') && cleaned.contains('.') ->
                cleaned.replace(".", "").replace(',', '.')
            else ->
                cleaned.replace(',', '.')
        }

        val value = normalized.toDoubleOrNull()
            ?: return ""

        return String.format(
            Locale.ITALY,
            "%.2f €",
            value
        )
    }

    private fun mm(value: Float): Float =
        value * POINTS_PER_MM
}