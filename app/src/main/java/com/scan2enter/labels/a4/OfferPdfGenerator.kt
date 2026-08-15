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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OfferFormat(
    val label: String,
    val widthMm: Float,
    val heightMm: Float
) {
    SMALL_7X10("7 × 10 cm", 70f, 100f),
    MEDIUM_15X20("15 × 20 cm", 150f, 200f),
    A4_FULL("A4 pieno", 210f, 297f)
}

object OfferPdfGenerator {

    private const val POINTS_PER_MM = 72f / 25.4f
    private const val GATEWAY_BASE_URL = "http://192.168.1.30:5055"

    fun generateAndOpen(
        context: Context,
        item: A4LabelItem,
        format: OfferFormat,
        offerPrice: String,
        showOldPrice: Boolean = true,
        showBarcode: Boolean = true,
        showImage: Boolean = true,
        showArticlePrefix: Boolean = true
    ): Result<Uri> = runCatching {
        val document = PdfDocument()

        try {
            /*
             * IMPORTANTE:
             * il PDF viene sempre creato come foglio A4 fisico.
             *
             * Se creassimo una pagina PDF 70x100 o 150x200 mm, molti
             * visualizzatori/servizi di stampa Android la adatterebbero
             * automaticamente al foglio A4, facendo risultare tutti i
             * formati grandi uguali.
             *
             * Qui invece il foglio resta A4 e il cartello viene disegnato
             * nelle sue reali dimensioni fisiche, centrato sul foglio.
             */
            val pageWidth = mm(210f).toInt()
            val pageHeight = mm(297f).toInt()

            val pageInfo = PdfDocument.PageInfo.Builder(
                pageWidth,
                pageHeight,
                1
            ).create()

            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val marginMm =
                when (format) {
                    OfferFormat.SMALL_7X10 -> 4f
                    OfferFormat.MEDIUM_15X20 -> 8f
                    OfferFormat.A4_FULL -> 12f
                }

            val margin = mm(marginMm)

            val offerWidth =
                when (format) {
                    OfferFormat.SMALL_7X10 -> mm(70f)
                    OfferFormat.MEDIUM_15X20 -> mm(150f)
                    OfferFormat.A4_FULL -> pageWidth.toFloat()
                }

            val offerHeight =
                when (format) {
                    OfferFormat.SMALL_7X10 -> mm(100f)
                    OfferFormat.MEDIUM_15X20 -> mm(200f)
                    OfferFormat.A4_FULL -> pageHeight.toFloat()
                }

            val offerLeft =
                (pageWidth.toFloat() - offerWidth) / 2f

            val offerTop =
                (pageHeight.toFloat() - offerHeight) / 2f

            val inner = RectF(
                offerLeft + margin,
                offerTop + margin,
                offerLeft + offerWidth - margin,
                offerTop + offerHeight - margin
            )

            val borderPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth =
                        mm(
                            if (
                                format ==
                                OfferFormat.SMALL_7X10
                            ) {
                                0.6f
                            } else {
                                1.0f
                            }
                        )
                }

            canvas.drawRect(
                inner,
                borderPaint
            )

            val headerHeight =
                when (format) {
                    OfferFormat.SMALL_7X10 ->
                        mm(15f)
                    OfferFormat.MEDIUM_15X20 ->
                        mm(28f)
                    OfferFormat.A4_FULL ->
                        mm(38f)
                }

            val blackFill =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                }

            canvas.drawRect(
                inner.left,
                inner.top,
                inner.right,
                inner.top + headerHeight,
                blackFill
            )

            val offerPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textAlign = Paint.Align.CENTER
                    typeface =
                        Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                        )
                    textSize =
                        when (format) {
                            OfferFormat.SMALL_7X10 ->
                                mm(7.5f)
                            OfferFormat.MEDIUM_15X20 ->
                                mm(14f)
                            OfferFormat.A4_FULL ->
                                mm(19f)
                        }
                }

            canvas.drawText(
                "OFFERTA",
                inner.centerX(),
                inner.top + headerHeight * 0.70f,
                offerPaint
            )

            val displayArticleCode =
                if (showArticlePrefix) {
                    item.articleCode.trim()
                } else {
                    item.articleCode
                        .trim()
                        .drop(3)
                }

            val topCodePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textAlign = Paint.Align.LEFT
                    typeface =
                        Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                        )
                    textSize =
                        when (format) {
                            OfferFormat.SMALL_7X10 ->
                                mm(2.8f) + 1f
                            OfferFormat.MEDIUM_15X20 ->
                                mm(5f) + 1f
                            OfferFormat.A4_FULL ->
                                mm(6.5f) + 1f
                        }
                }

            canvas.drawText(
                displayArticleCode,
                inner.left + mm(11f),
                inner.top +
                        headerHeight +
                        when (format) {
                            OfferFormat.SMALL_7X10 -> mm(9f)
                            OfferFormat.MEDIUM_15X20 -> mm(14f)
                            OfferFormat.A4_FULL -> mm(17f)
                        },
                topCodePaint
            )

            val descriptionPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textAlign = Paint.Align.CENTER
                    typeface =
                        Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                        )
                    textSize =
                        when (format) {
                            OfferFormat.SMALL_7X10 ->
                                mm(4.7f)
                            OfferFormat.MEDIUM_15X20 ->
                                mm(9f)
                            OfferFormat.A4_FULL ->
                                mm(12f)
                        }
                }

            drawDescription(
                canvas = canvas,
                text =
                    item.description
                        .ifBlank { "ARTICOLO" },
                paint = descriptionPaint,
                centerX = inner.centerX(),
                top =
                    inner.top +
                            headerHeight +
                            when (format) {
                                OfferFormat.SMALL_7X10 ->
                                    mm(11f)
                                OfferFormat.MEDIUM_15X20 ->
                                    mm(21f)
                                OfferFormat.A4_FULL ->
                                    mm(28f)
                            },
                maxWidth =
                    inner.width() -
                            margin * 1.2f,
                maxLines = 3
            )

            val priceBaseline =
                inner.top +
                        inner.height() *
                        when (format) {
                            OfferFormat.SMALL_7X10 ->
                                0.55f
                            OfferFormat.MEDIUM_15X20 ->
                                0.53f
                            OfferFormat.A4_FULL ->
                                0.51f
                        }

            val newPrice =
                formatPrice(offerPrice)

            val pricePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textAlign = Paint.Align.CENTER
                    typeface =
                        Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                        )
                    textSize =
                        when (format) {
                            OfferFormat.SMALL_7X10 ->
                                mm(12f)
                            OfferFormat.MEDIUM_15X20 ->
                                mm(26f)
                            OfferFormat.A4_FULL ->
                                mm(36f)
                        }
                }

            while (
                pricePaint.textSize >
                mm(
                    when (format) {
                        OfferFormat.SMALL_7X10 ->
                            8f
                        OfferFormat.MEDIUM_15X20 ->
                            18f
                        OfferFormat.A4_FULL ->
                            25f
                    }
                ) &&
                pricePaint.measureText(newPrice) >
                inner.width() - margin
            ) {
                pricePaint.textSize -= 1f
            }

            canvas.drawText(
                newPrice,
                inner.centerX(),
                priceBaseline,
                pricePaint
            )

            if (showOldPrice) {
                val oldPrice =
                    formatPrice(item.publicPrice)

                if (
                    oldPrice.isNotBlank() &&
                    oldPrice != newPrice
                ) {
                    val oldPricePaint =
                        Paint(
                            Paint.ANTI_ALIAS_FLAG
                        ).apply {
                            color = Color.DKGRAY
                            textAlign =
                                Paint.Align.CENTER
                            typeface =
                                Typeface.create(
                                    Typeface.DEFAULT,
                                    Typeface.NORMAL
                                )
                            isStrikeThruText = true
                            textSize =
                                when (format) {
                                    OfferFormat.SMALL_7X10 ->
                                        mm(4.2f)
                                    OfferFormat.MEDIUM_15X20 ->
                                        mm(8f)
                                    OfferFormat.A4_FULL ->
                                        mm(10f)
                                }
                        }

                    canvas.drawText(
                        oldPrice,
                        inner.centerX(),
                        priceBaseline +
                                when (format) {
                                    OfferFormat.SMALL_7X10 ->
                                        mm(9f)
                                    OfferFormat.MEDIUM_15X20 ->
                                        mm(18f)
                                    OfferFormat.A4_FULL ->
                                        mm(24f)
                                },
                        oldPricePaint
                    )
                }
            }

            val footerTop =
                inner.bottom -
                        when (format) {
                            OfferFormat.SMALL_7X10 ->
                                mm(25f)
                            OfferFormat.MEDIUM_15X20 ->
                                mm(48f)
                            OfferFormat.A4_FULL ->
                                mm(66f)
                        }

            val footerGap =
                when (format) {
                    OfferFormat.SMALL_7X10 -> mm(2f)
                    OfferFormat.MEDIUM_15X20 -> mm(4f)
                    OfferFormat.A4_FULL -> mm(6f)
                }

            val footerWidth = inner.width()
            val halfFooterWidth =
                (footerWidth - footerGap) / 2f

            val leftFooter = RectF(
                inner.left,
                footerTop,
                inner.left + halfFooterWidth,
                inner.bottom
            )

            val rightFooter = RectF(
                leftFooter.right + footerGap,
                footerTop,
                inner.right,
                inner.bottom
            )

            /*
             * FOTO: parte bassa sinistra.
             * Se disattivata, non lasciamo cornici o segnaposto.
             */
            if (showImage) {
                val imagePadding =
                    when (format) {
                        OfferFormat.SMALL_7X10 -> mm(1.5f)
                        OfferFormat.MEDIUM_15X20 -> mm(3f)
                        OfferFormat.A4_FULL -> mm(4f)
                    }

                val originalLeft =
                    leftFooter.left + imagePadding

                val originalTop =
                    leftFooter.top + imagePadding

                val originalRight =
                    leftFooter.right - imagePadding

                val originalBottom =
                    leftFooter.bottom - imagePadding

                val originalWidth =
                    originalRight - originalLeft

                val originalHeight =
                    originalBottom - originalTop

                val imageWidth =
                    originalWidth * 1.35f

                val imageHeight =
                    originalHeight * 1.35f

                val centerX =
                    (originalLeft + originalRight) / 2f -
                            when (format) {
                                OfferFormat.SMALL_7X10 -> mm(5f)
                                OfferFormat.MEDIUM_15X20 -> mm(10f)
                                OfferFormat.A4_FULL -> mm(10f)
                            }

                val centerY =
                    (originalTop + originalBottom) / 2f - mm(2f)

                val desiredLeft =
                    centerX - imageWidth / 2f

                val desiredRight =
                    centerX + imageWidth / 2f

                val minImageLeft =
                    inner.left + mm(1f)

                val correctionX =
                    maxOf(
                        0f,
                        minImageLeft - desiredLeft
                    )

                val imageBounds = RectF(
                    desiredLeft + correctionX,
                    centerY - imageHeight / 2f,
                    desiredRight + correctionX,
                    centerY + imageHeight / 2f
                )

                val bitmap =
                    loadProductImage(
                        item.barcode
                    )

                if (bitmap != null) {
                    drawBitmapFitCenter(
                        canvas = canvas,
                        bitmap = bitmap,
                        bounds = imageBounds
                    )
                    bitmap.recycle()
                }
            }

            /*
             * EAN: parte bassa destra, circa metà larghezza rispetto al
             * vecchio barcode a tutta pagina.
             */
            if (showBarcode) {
                val barcodeSidePadding =
                    when (format) {
                        OfferFormat.SMALL_7X10 -> mm(1.5f)
                        OfferFormat.MEDIUM_15X20 -> mm(3f)
                        OfferFormat.A4_FULL -> mm(4f)
                    }

                val numericHeight =
                    when (format) {
                        OfferFormat.SMALL_7X10 -> mm(4.2f)
                        OfferFormat.MEDIUM_15X20 -> mm(7f)
                        OfferFormat.A4_FULL -> mm(9f)
                    }

                val barcodeBounds =
                    RectF(
                        rightFooter.left + barcodeSidePadding,
                        rightFooter.top + barcodeSidePadding,
                        rightFooter.right - barcodeSidePadding,
                        rightFooter.bottom -
                                numericHeight -
                                barcodeSidePadding
                    )

                drawEan13(
                    canvas = canvas,
                    rawBarcode = item.barcode,
                    bounds = barcodeBounds
                )

                val eanText =
                    item.barcode
                        .filter(Char::isDigit)

                val eanPaint =
                    Paint(
                        Paint.ANTI_ALIAS_FLAG
                    ).apply {
                        color = Color.BLACK
                        textAlign =
                            Paint.Align.CENTER
                        typeface =
                            Typeface.create(
                                Typeface.DEFAULT,
                                Typeface.BOLD
                            )
                        textSize =
                            when (format) {
                                OfferFormat.SMALL_7X10 ->
                                    mm(3f)
                                OfferFormat.MEDIUM_15X20 ->
                                    mm(5.5f)
                                OfferFormat.A4_FULL ->
                                    mm(7f)
                            }
                    }

                val maxEanWidth =
                    rightFooter.width() -
                            barcodeSidePadding * 2f

                while (
                    eanPaint.textSize > mm(1.7f) &&
                    eanPaint.measureText(eanText) >
                    maxEanWidth
                ) {
                    eanPaint.textSize -= 0.5f
                }

                val eanMetrics =
                    eanPaint.fontMetrics

                val eanBaseline =
                    barcodeBounds.bottom -
                            eanMetrics.ascent +
                            when (format) {
                                OfferFormat.SMALL_7X10 -> mm(0.4f)
                                OfferFormat.MEDIUM_15X20 -> mm(0.8f)
                                OfferFormat.A4_FULL -> mm(1.0f)
                            }

                canvas.drawText(
                    eanText,
                    rightFooter.centerX(),
                    eanBaseline,
                    eanPaint
                )
            }
// Ridisegna il bordo per ultimo, così resta sopra immagine ed EAN
            canvas.drawRect(
                inner,
                borderPaint
            )
            document.finishPage(page)

            val file =
                createFile(
                    context,
                    format
                )

            file.outputStream().use {
                document.writeTo(it)
            }

            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

            openPdf(
                context,
                uri
            )

            uri
        } finally {
            document.close()
        }
    }

    private fun drawDescription(
        canvas: android.graphics.Canvas,
        text: String,
        paint: Paint,
        centerX: Float,
        top: Float,
        maxWidth: Float,
        maxLines: Int
    ) {
        val words =
            text
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim()
                .split(Regex("\\s+"))

        val lines =
            mutableListOf<String>()

        var current = ""

        for (word in words) {
            val test =
                if (current.isBlank()) {
                    word
                } else {
                    "$current $word"
                }

            if (
                paint.measureText(test) <=
                maxWidth
            ) {
                current = test
            } else {
                if (current.isNotBlank()) {
                    lines.add(current)
                }

                current = word

                if (
                    lines.size >=
                    maxLines - 1
                ) {
                    break
                }
            }
        }

        if (
            current.isNotBlank() &&
            lines.size < maxLines
        ) {
            lines.add(current)
        }

        val metrics = paint.fontMetrics
        val lineHeight =
            metrics.descent -
                    metrics.ascent +
                    mm(1f)

        lines
            .take(maxLines)
            .forEachIndexed {
                    index,
                    line ->

                canvas.drawText(
                    line,
                    centerX,
                    top -
                            metrics.ascent +
                            index *
                            lineHeight,
                    paint
                )
            }
    }

    private fun loadProductImage(
        barcode: String
    ): Bitmap? {
        val cleanBarcode =
            barcode
                .trim()
                .filter(Char::isDigit)

        if (cleanBarcode.isBlank()) {
            return null
        }

        val encoded =
            URLEncoder.encode(
                cleanBarcode,
                StandardCharsets.UTF_8.name()
            )

        val connection =
            (
                    URL(
                        "$GATEWAY_BASE_URL/api/product/$encoded/image"
                    ).openConnection() as HttpURLConnection
                    ).apply {
                    requestMethod = "GET"
                    connectTimeout = 5_000
                    readTimeout = 15_000
                    setRequestProperty(
                        "Accept",
                        "image/*"
                    )
                }

        return try {
            if (
                connection.responseCode !in
                200..299
            ) {
                null
            } else {
                connection.inputStream.use(
                    BitmapFactory::decodeStream
                )
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
        if (
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {
            return
        }

        val scale =
            minOf(
                bounds.width() /
                        bitmap.width.toFloat(),
                bounds.height() /
                        bitmap.height.toFloat()
            )

        val width =
            bitmap.width *
                    scale
        val height =
            bitmap.height *
                    scale

        val destination =
            RectF(
                bounds.centerX() -
                        width / 2f,
                bounds.centerY() -
                        height / 2f,
                bounds.centerX() +
                        width / 2f,
                bounds.centerY() +
                        height / 2f
            )

        canvas.drawBitmap(
            bitmap,
            null,
            destination,
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                        Paint.FILTER_BITMAP_FLAG
            )
        )
    }

    private fun drawEan13(
        canvas: android.graphics.Canvas,
        rawBarcode: String,
        bounds: RectF
    ) {
        val barcode =
            rawBarcode
                .filter(Char::isDigit)
                .takeLast(13)

        if (!isValidEan13(barcode)) {
            val fallback =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    color = Color.BLACK
                    textAlign =
                        Paint.Align.CENTER
                    textSize = mm(3f)
                    typeface =
                        Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                        )
                }

            canvas.drawText(
                barcode.ifBlank {
                    "EAN NON VALIDO"
                },
                bounds.centerX(),
                bounds.centerY(),
                fallback
            )

            return
        }

        val modules =
            buildEan13Modules(barcode)

        val quietModules = 9

        val moduleWidth =
            bounds.width() /
                    (
                            modules.length +
                                    quietModules * 2
                            )

        val startX =
            bounds.left +
                    quietModules *
                    moduleWidth

        val barPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        modules.forEachIndexed {
                index,
                bit ->

            if (bit != '1') {
                return@forEachIndexed
            }

            canvas.drawRect(
                startX +
                        index *
                        moduleWidth,
                bounds.top,
                startX +
                        (index + 1) *
                        moduleWidth,
                bounds.bottom,
                barPaint
            )
        }
    }

    private fun isValidEan13(
        value: String
    ): Boolean {
        if (
            value.length != 13 ||
            value.any { !it.isDigit() }
        ) {
            return false
        }

        val expected =
            value.last().digitToInt()

        var sum = 0

        for (index in 0 until 12) {
            val digit =
                value[index].digitToInt()

            sum +=
                if (index % 2 == 0) {
                    digit
                } else {
                    digit * 3
                }
        }

        return (
                10 -
                        sum %
                        10
                ) % 10 ==
                expected
    }

    private fun buildEan13Modules(
        ean: String
    ): String {
        val leftPatterns =
            arrayOf(
                arrayOf(
                    "0001101",
                    "0100111"
                ),
                arrayOf(
                    "0011001",
                    "0110011"
                ),
                arrayOf(
                    "0010011",
                    "0011011"
                ),
                arrayOf(
                    "0111101",
                    "0100001"
                ),
                arrayOf(
                    "0100011",
                    "0011101"
                ),
                arrayOf(
                    "0110001",
                    "0111001"
                ),
                arrayOf(
                    "0101111",
                    "0000101"
                ),
                arrayOf(
                    "0111011",
                    "0010001"
                ),
                arrayOf(
                    "0110111",
                    "0001001"
                ),
                arrayOf(
                    "0001011",
                    "0010111"
                )
            )

        val parityPatterns =
            arrayOf(
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

        val rightPatterns =
            arrayOf(
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

            val parity =
                parityPatterns[
                    ean[0].digitToInt()
                ]

            for (index in 1..6) {
                val digit =
                    ean[index].digitToInt()

                val patternIndex =
                    if (
                        parity[index - 1] ==
                        'G'
                    ) {
                        1
                    } else {
                        0
                    }

                append(
                    leftPatterns[
                        digit
                    ][patternIndex]
                )
            }

            append("01010")

            for (index in 7..12) {
                append(
                    rightPatterns[
                        ean[index]
                            .digitToInt()
                    ]
                )
            }

            append("101")
        }
    }

    private fun createFile(
        context: Context,
        format: OfferFormat
    ): File {
        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.ITALY
            ).format(Date())

        val baseDir =
            context.getExternalFilesDir(
                Environment
                    .DIRECTORY_DOWNLOADS
            ) ?: context.filesDir

        val reportDir =
            File(
                baseDir,
                "Scan2Enter"
            ).apply {
                if (
                    !exists() &&
                    !mkdirs()
                ) {
                    error(
                        "Impossibile creare la cartella dei report PDF"
                    )
                }
            }

        val suffix =
            when (format) {
                OfferFormat.SMALL_7X10 ->
                    "7x10"
                OfferFormat.MEDIUM_15X20 ->
                    "15x20"
                OfferFormat.A4_FULL ->
                    "A4"
            }

        return File(
            reportDir,
            "Scan2Enter_Offerta_${suffix}_$timestamp.pdf"
        )
    }

    private fun openPdf(
        context: Context,
        uri: Uri
    ) {
        val intent =
            Intent(
                Intent.ACTION_VIEW
            ).apply {
                setDataAndType(
                    uri,
                    "application/pdf"
                )
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
                addFlags(
                    Intent
                        .FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        context.startActivity(
            Intent.createChooser(
                intent,
                "Apri o stampa offerta"
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
        )
    }

    private fun formatPrice(
        raw: String
    ): String {
        val cleaned =
            raw
                .trim()
                .replace("€", "")
                .replace(" ", "")

        val normalized =
            if (
                cleaned.contains(',') &&
                cleaned.contains('.')
            ) {
                cleaned
                    .replace(".", "")
                    .replace(',', '.')
            } else {
                cleaned.replace(',', '.')
            }

        val value =
            normalized.toDoubleOrNull()
                ?: return raw.trim()

        return String.format(
            Locale.ITALY,
            "%.2f €",
            value
        )
    }

    private fun mm(
        value: Float
    ): Float =
        value * POINTS_PER_MM
}