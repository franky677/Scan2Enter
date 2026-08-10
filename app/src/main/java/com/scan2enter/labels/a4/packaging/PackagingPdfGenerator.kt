package com.scan2enter.labels.a4.packaging

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.scan2enter.R
import com.scan2enter.model.ProductInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PackagingPdfGenerator {

    private const val GATEWAY_BASE_URL = "http://192.168.1.30:5055"
    private const val PT_PER_MM = 72f / 25.4f

    private const val A4_WIDTH_MM = 210f
    private const val A4_HEIGHT_MM = 297f

    // BLISTER GRANDE: misure reali concordate.
    private const val LARGE_WIDTH_MM = 55f
    private const val LARGE_BACK_MM = 70f
    private const val LARGE_SPINE_MM = 38.5f
    private const val LARGE_FRONT_MM = 33.5f

    // BLISTER LUNGO: sviluppo totale 192 mm.
    // Fronte mantenuto a 33 mm come concordato.
    private const val LONG_WIDTH_MM = 68f
    private const val LONG_BACK_MM = 133f
    private const val LONG_SPINE_MM = 26f
    private const val LONG_FRONT_MM = 33f

    // BLISTER BIG: misure reali concordate.
    private const val BIG_WIDTH_MM = 116f
    private const val BIG_BACK_MM = 194f
    private const val BIG_SPINE_MM = 27f
    private const val BIG_FRONT_MM = 41f

    private const val HOOK_WIDTH_MM = 54f
    private const val HOOK_HEIGHT_MM = 125f

    fun generateAndOpen(
        context: Context,
        product: ProductInfo,
        options: PackagingOptions
    ): Result<Uri> = runCatching {
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(
                mm(A4_WIDTH_MM).toInt(),
                mm(A4_HEIGHT_MM).toInt(),
                1
            ).create()

            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            when (options.type) {
                PackagingType.BLISTER_LARGE -> {
                    drawLargeBlister(
                        context = context,
                        canvas = canvas,
                        product = product,
                        left = mm(24f),
                        top = mm(28f),
                        showPrice = options.showPrice
                    )

                    if (options.includeHook) {
                        drawHookLabel(
                            context = context,
                            canvas = canvas,
                            product = product,
                            left = mm(116f),
                            top = mm(28f),
                            showPrice = options.showPrice
                        )
                    }
                }

                PackagingType.BLISTER_LONG -> {
                    drawLongBlister(
                        context = context,
                        canvas = canvas,
                        product = product,
                        left = mm(24f),
                        top = mm(28f),
                        showPrice = options.showPrice
                    )

                    if (options.includeHook) {
                        drawHookLabel(
                            context = context,
                            canvas = canvas,
                            product = product,
                            left = mm(116f),
                            top = mm(28f),
                            showPrice = options.showPrice
                        )
                    }
                }

                PackagingType.BLISTER_BIG -> {
                    val bigLeft = mm(14f)
                    val bigTop = mm(
                        (A4_HEIGHT_MM -
                                (BIG_BACK_MM + BIG_SPINE_MM + BIG_FRONT_MM)) / 2f
                    )

                    drawBigBlister(
                        context = context,
                        canvas = canvas,
                        product = product,
                        left = bigLeft,
                        top = bigTop,
                        showPrice = options.showPrice
                    )

                    if (options.includeHook) {
                        drawHookLabel(
                            context = context,
                            canvas = canvas,
                            product = product,
                            left = mm(140f),
                            top = mm(28f),
                            showPrice = options.showPrice
                        )
                    }
                }
            }

            document.finishPage(page)

            val pdfFile = createDownloadFile(
                context = context,
                product = product
            )

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

    private fun drawLargeBlister(
        context: Context,
        canvas: Canvas,
        product: ProductInfo,
        left: Float,
        top: Float,
        showPrice: Boolean
    ) {
        val width = mm(LARGE_WIDTH_MM)
        val backHeight = mm(LARGE_BACK_MM)
        val spineHeight = mm(LARGE_SPINE_MM)
        val frontHeight = mm(LARGE_FRONT_MM)
        val bottom = top + backHeight + spineHeight + frontHeight

        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = mm(0.35f)
        }
        val foldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            style = Paint.Style.STROKE
            strokeWidth = mm(0.25f)
            pathEffect = DashPathEffect(floatArrayOf(mm(2f), mm(1.5f)), 0f)
        }

        val outer = RectF(left, top, left + width, bottom)
        canvas.drawRect(outer, cutPaint)

        val fold1 = top + backHeight
        val fold2 = fold1 + spineHeight
        canvas.drawLine(left, fold1, left + width, fold1, foldPaint)
        canvas.drawLine(left, fold2, left + width, fold2, foldPaint)

        val backBounds = RectF(left, top, left + width, fold1)
        val frontBounds = RectF(left, fold2, left + width, bottom)

        // Retro capovolto sul foglio: dopo la piega risulta dritto.
        canvas.save()
        canvas.rotate(180f, backBounds.centerX(), backBounds.centerY())
        drawBackPanel(
            context = context,
            canvas = canvas,
            product = product,
            bounds = backBounds
        )
        canvas.restore()

        drawFrontPanel(canvas, product, frontBounds, showPrice)
    }


    private fun drawBigBlister(
        context: Context,
        canvas: Canvas,
        product: ProductInfo,
        left: Float,
        top: Float,
        showPrice: Boolean
    ) {
        val width = mm(BIG_WIDTH_MM)
        val backHeight = mm(BIG_BACK_MM)
        val spineHeight = mm(BIG_SPINE_MM)
        val frontHeight = mm(BIG_FRONT_MM)
        val bottom = top + backHeight + spineHeight + frontHeight

        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = mm(0.35f)
        }

        val foldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            style = Paint.Style.STROKE
            strokeWidth = mm(0.25f)
            pathEffect = DashPathEffect(
                floatArrayOf(mm(2f), mm(1.5f)),
                0f
            )
        }

        val outer = RectF(left, top, left + width, bottom)
        canvas.drawRect(outer, cutPaint)

        val fold1 = top + backHeight
        val fold2 = fold1 + spineHeight

        canvas.drawLine(left, fold1, left + width, fold1, foldPaint)
        canvas.drawLine(left, fold2, left + width, fold2, foldPaint)

        val backBounds = RectF(left, top, left + width, fold1)
        val frontBounds = RectF(left, fold2, left + width, bottom)

        canvas.save()
        canvas.rotate(
            180f,
            backBounds.centerX(),
            backBounds.centerY()
        )

        drawBigBackPanel(
            context = context,
            canvas = canvas,
            product = product,
            bounds = backBounds
        )

        canvas.restore()

        drawBigFrontPanel(
            canvas = canvas,
            product = product,
            bounds = frontBounds,
            showPrice = showPrice
        )
    }

    private fun drawBigBackPanel(
        context: Context,
        canvas: Canvas,
        product: ProductInfo,
        bounds: RectF
    ) {
        val padding = mm(5f)

        val logoBounds = RectF(
            bounds.left + mm(14f),
            bounds.top + mm(8f),
            bounds.right - mm(14f),
            bounds.top + mm(52f)
        )

        val logoBitmap = runCatching {
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.de_pieri_blister
            )
        }.getOrNull()

        if (logoBitmap != null) {
            drawBitmapFitCenter(
                canvas = canvas,
                bitmap = logoBitmap,
                bounds = logoBounds
            )
            logoBitmap.recycle()
        } else {
            drawLogoPlaceholder(canvas, logoBounds)
        }

        drawBrandSeparator(
            canvas = canvas,
            left = bounds.left + padding,
            right = bounds.right - padding,
            top = logoBounds.bottom + mm(2f)
        )

        val codePaint = textPaint(mm(3.8f), bold = true)
        val descriptionPaint = textPaint(mm(3.0f), bold = false)

        val dataTop = logoBounds.bottom + mm(5f)

        canvas.drawText(
            product.articleCode.trim(),
            bounds.left + padding,
            dataTop + mm(4f),
            codePaint
        )

        drawFittedText(
            canvas = canvas,
            text = product.description,
            paint = descriptionPaint,
            left = bounds.left + padding,
            right = bounds.right - padding,
            baseline = dataTop + mm(10f),
            maxSize = mm(3.0f),
            minSize = mm(2.0f)
        )

        val imageBounds = RectF(
            bounds.left + mm(14f),
            dataTop + mm(15f),
            bounds.right - mm(14f),
            dataTop + mm(105f)
        )

        val productImage = loadProductImage(product.barcode)

        if (productImage != null) {
            drawBitmapFitCenter(
                canvas = canvas,
                bitmap = productImage,
                bounds = imageBounds
            )
            productImage.recycle()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                style = Paint.Style.STROKE
                strokeWidth = mm(0.2f)
                pathEffect = DashPathEffect(
                    floatArrayOf(mm(1.5f), mm(1f)),
                    0f
                )
            }
            canvas.drawRect(imageBounds, placeholderPaint)
        }

        // Molto più largo, ma volutamente non più alto.
        val barcodeBounds = RectF(
            bounds.left + mm(18f),
            dataTop + mm(112f),
            bounds.right - mm(18f),
            dataTop + mm(124f)
        )

        drawEan13(
            canvas,
            product.barcode,
            barcodeBounds
        )

        val eanPaint = textPaint(mm(4.8f), bold = true).apply {
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(
            product.barcode.filter(Char::isDigit),
            barcodeBounds.centerX(),
            barcodeBounds.bottom + mm(5f),
            eanPaint
        )

        val seasonYear = listOf(
            product.season,
            product.year
        )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .uppercase(Locale.ITALIAN)

        if (seasonYear.isNotBlank()) {
            val datePaint = textPaint(mm(2.2f), bold = true).apply {
                textAlign = Paint.Align.RIGHT
            }

            canvas.drawText(
                seasonYear,
                bounds.right - padding,
                bounds.bottom - mm(4f),
                datePaint
            )
        }
    }

    private fun drawBigFrontPanel(
        canvas: Canvas,
        product: ProductInfo,
        bounds: RectF,
        showPrice: Boolean
    ) {
        val padding = mm(3f)

        val imageBounds = RectF(
            bounds.left + padding,
            bounds.top + mm(2f),
            bounds.left + mm(38f),
            bounds.bottom - mm(2f)
        )

        val image = loadProductImage(product.barcode)
        if (image != null) {
            drawBitmapFitCenter(canvas, image, imageBounds)
            image.recycle()
        } else {
            val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                style = Paint.Style.STROKE
                strokeWidth = mm(0.2f)
                pathEffect = DashPathEffect(
                    floatArrayOf(mm(1.5f), mm(1f)),
                    0f
                )
            }
            canvas.drawRect(imageBounds, placeholder)
        }

        val contentLeft = bounds.left + mm(42f)
        val contentRight = bounds.right - padding

        val codePaint = textPaint(mm(3.1f), bold = true)
        canvas.drawText(
            product.articleCode.trim(),
            contentLeft,
            bounds.top + mm(6f),
            codePaint
        )

        val descPaint = textPaint(mm(2.5f), bold = false)
        drawFittedText(
            canvas = canvas,
            text = product.description,
            paint = descPaint,
            left = contentLeft,
            right = contentRight,
            baseline = bounds.top + mm(11f),
            maxSize = mm(2.5f),
            minSize = mm(1.7f)
        )

        // Altezza contenuta come nel layout già collaudato.
        val barcodeBounds = RectF(
            contentLeft,
            bounds.top + mm(14f),
            contentRight,
            bounds.top + mm(21f)
        )

        drawEan13(canvas, product.barcode, barcodeBounds)

        val eanPaint = textPaint(mm(3.2f), bold = true).apply {
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(
            product.barcode.filter(Char::isDigit),
            barcodeBounds.centerX(),
            barcodeBounds.bottom + mm(3.3f),
            eanPaint
        )

        if (showPrice) {
            val pricePaint = textPaint(mm(3.95f), bold = true).apply {
                textAlign = Paint.Align.RIGHT
            }

            canvas.drawText(
                formatPrice(product.publicPrice),
                contentRight,
                bounds.bottom - mm(3f),
                pricePaint
            )
        }
    }

    private fun drawLongBlister(
        context: Context,
        canvas: Canvas,
        product: ProductInfo,
        left: Float,
        top: Float,
        showPrice: Boolean
    ) {
        val width = mm(LONG_WIDTH_MM)
        val backHeight = mm(LONG_BACK_MM)
        val spineHeight = mm(LONG_SPINE_MM)
        val frontHeight = mm(LONG_FRONT_MM)
        val bottom = top + backHeight + spineHeight + frontHeight

        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = mm(0.35f)
        }

        val foldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            style = Paint.Style.STROKE
            strokeWidth = mm(0.25f)
            pathEffect = DashPathEffect(
                floatArrayOf(mm(2f), mm(1.5f)),
                0f
            )
        }

        val outer = RectF(
            left,
            top,
            left + width,
            bottom
        )
        canvas.drawRect(outer, cutPaint)

        val fold1 = top + backHeight
        val fold2 = fold1 + spineHeight

        canvas.drawLine(
            left,
            fold1,
            left + width,
            fold1,
            foldPaint
        )
        canvas.drawLine(
            left,
            fold2,
            left + width,
            fold2,
            foldPaint
        )

        val backBounds = RectF(
            left,
            top,
            left + width,
            fold1
        )
        val frontBounds = RectF(
            left,
            fold2,
            left + width,
            bottom
        )

        /*
         * Come nel blister grande, il retro è capovolto sul foglio
         * affinché risulti diritto dopo le pieghe.
         */
        canvas.save()
        canvas.rotate(
            180f,
            backBounds.centerX(),
            backBounds.centerY()
        )

        drawLongBackPanel(
            context = context,
            canvas = canvas,
            product = product,
            bounds = backBounds
        )

        canvas.restore()

        /*
         * Il fronte mantiene la stessa impostazione del blister grande.
         * La funzione è già elastica in larghezza: sui 68 mm guadagna
         * spazio per descrizione, barcode e prezzo senza cambiare
         * l'altezza del layout.
         */
        drawFrontPanel(
            canvas = canvas,
            product = product,
            bounds = frontBounds,
            showPrice = showPrice
        )
    }

    private fun drawLongBackPanel(
        context: Context,
        canvas: Canvas,
        product: ProductInfo,
        bounds: RectF
    ) {
        val padding = mm(3f)

        /*
         * Retro lungo 133 mm:
         * logo fisso più ampio, dati articolo, immagine prodotto grande,
         * barcode largo ma non più alto e EAN numerico.
         */
        val logoBounds = RectF(
            bounds.left + mm(5f),
            bounds.top + mm(4f),
            bounds.right - mm(5f),
            bounds.top + mm(35f)
        )

        val logoBitmap = runCatching {
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.de_pieri_blister
            )
        }.getOrNull()

        if (logoBitmap != null) {
            drawBitmapFitCenter(
                canvas = canvas,
                bitmap = logoBitmap,
                bounds = logoBounds
            )
            logoBitmap.recycle()
        } else {
            drawLogoPlaceholder(
                canvas,
                logoBounds
            )
        }

        drawBrandSeparator(
            canvas = canvas,
            left = bounds.left + padding,
            right = bounds.right - padding,
            top = logoBounds.bottom + mm(1.5f)
        )

        val codePaint = textPaint(
            mm(3.2f),
            bold = true
        )
        val descriptionPaint = textPaint(
            mm(2.5f),
            bold = false
        )

        val dataTop = logoBounds.bottom + mm(4f)

        canvas.drawText(
            product.articleCode.trim(),
            bounds.left + padding,
            dataTop + mm(3.5f),
            codePaint
        )

        drawFittedText(
            canvas = canvas,
            text = product.description,
            paint = descriptionPaint,
            left = bounds.left + padding,
            right = bounds.right - padding,
            baseline = dataTop + mm(8f),
            maxSize = mm(2.5f),
            minSize = mm(1.7f)
        )

        val imageBounds = RectF(
            bounds.left + mm(7f),
            dataTop + mm(12f),
            bounds.right - mm(7f),
            dataTop + mm(66f)
        )

        val productImage = loadProductImage(
            product.barcode
        )

        if (productImage != null) {
            drawBitmapFitCenter(
                canvas = canvas,
                bitmap = productImage,
                bounds = imageBounds
            )
            productImage.recycle()
        } else {
            val placeholderPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.GRAY
                    style = Paint.Style.STROKE
                    strokeWidth = mm(0.2f)
                    pathEffect = DashPathEffect(
                        floatArrayOf(
                            mm(1.5f),
                            mm(1f)
                        ),
                        0f
                    )
                }

            canvas.drawRect(
                imageBounds,
                placeholderPaint
            )
        }

        /*
         * Barcode più largo grazie ai 68 mm disponibili,
         * ma altezza mantenuta a 12 mm come nel grande.
         */
        val barcodeBounds = RectF(
            bounds.left + mm(7f),
            dataTop + mm(70f),
            bounds.right - mm(7f),
            dataTop + mm(82f)
        )

        drawEan13(
            canvas,
            product.barcode,
            barcodeBounds
        )

        val eanPaint = textPaint(
            mm(4.8f),
            bold = true
        ).apply {
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(
            product.barcode.filter(
                Char::isDigit
            ),
            barcodeBounds.centerX(),
            barcodeBounds.bottom + mm(4.2f),
            eanPaint
        )

        val seasonYear = listOf(
            product.season,
            product.year
        )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .uppercase(Locale.ITALIAN)

        if (seasonYear.isNotBlank()) {
            val datePaint = textPaint(
                mm(2.0f),
                bold = true
            ).apply {
                textAlign = Paint.Align.RIGHT
            }

            canvas.drawText(
                seasonYear,
                bounds.right - padding,
                bounds.bottom - mm(3f),
                datePaint
            )
        }
    }

    private fun drawBackPanel(
        context: Context,
        canvas: Canvas,
        product: ProductInfo,
        bounds: RectF
    ) {
        val padding = mm(2.5f)

        // Segnaposto logo fisso: altezza reale 24 mm.
        val logoBounds = RectF(
            bounds.left + padding,
            bounds.top + padding,
            bounds.right - padding,
            bounds.top + mm(24f)
        )
        val logoBitmap = runCatching {
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.de_pieri_blister
            )
        }.getOrNull()

        if (logoBitmap != null) {
            drawBitmapFitCenter(
                canvas = canvas,
                bitmap = logoBitmap,
                bounds = logoBounds
            )
            logoBitmap.recycle()
        } else {
            drawLogoPlaceholder(canvas, logoBounds)
        }

        drawBrandSeparator(
            canvas = canvas,
            left = bounds.left + padding,
            right = bounds.right - padding,
            top = logoBounds.bottom + mm(1.2f)
        )

        val codePaint = textPaint(mm(3.0f), bold = true)
        val smallPaint = textPaint(mm(2.3f), bold = false)

        val dataTop = logoBounds.bottom + mm(3f)
        canvas.drawText(
            product.articleCode.trim(),
            bounds.left + padding,
            dataTop + mm(3f),
            codePaint
        )

        drawFittedText(
            canvas,
            product.description,
            smallPaint,
            bounds.left + padding,
            bounds.right - padding,
            dataTop + mm(7.5f),
            mm(2.3f),
            mm(1.6f)
        )

        val barcodeBounds = RectF(
            bounds.left + mm(5f),
            dataTop + mm(10f),
            bounds.right - mm(5f),
            dataTop + mm(22f)
        )
        drawEan13(canvas, product.barcode, barcodeBounds)

        val eanPaint = textPaint(mm(4.8f), bold = true).apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            product.barcode.filter(Char::isDigit),
            barcodeBounds.centerX(),
            barcodeBounds.bottom + mm(3.3f),
            eanPaint
        )

        val seasonYear = listOf(product.season, product.year)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .uppercase(Locale.ITALIAN)

        if (seasonYear.isNotBlank()) {
            val datePaint = textPaint(mm(2.0f), bold = true).apply {
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(
                seasonYear,
                bounds.right - padding,
                bounds.bottom - mm(2.5f),
                datePaint
            )
        }
    }

    private fun drawFrontPanel(
        canvas: Canvas,
        product: ProductInfo,
        bounds: RectF,
        showPrice: Boolean
    ) {
        val padding = mm(2f)
        val imageBounds = RectF(
            bounds.left + padding,
            bounds.top + mm(1.5f),
            bounds.left + mm(19f),
            bounds.bottom - mm(2f)
        )

        val image = loadProductImage(product.barcode)
        if (image != null) {
            drawBitmapFitCenter(canvas, image, imageBounds)
            image.recycle()
        } else {
            val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                style = Paint.Style.STROKE
                strokeWidth = mm(0.2f)
                pathEffect = DashPathEffect(floatArrayOf(mm(1.5f), mm(1f)), 0f)
            }
            canvas.drawRect(imageBounds, placeholder)
        }

        val contentLeft = bounds.left + mm(21f)
        val contentRight = bounds.right - padding

        val codePaint = textPaint(mm(2.7f), bold = true)
        canvas.drawText(
            product.articleCode.trim(),
            contentLeft,
            bounds.top + mm(5f),
            codePaint
        )

        val descPaint = textPaint(mm(2.1f), bold = false)
        drawFittedText(
            canvas,
            product.description,
            descPaint,
            contentLeft,
            contentRight,
            bounds.top + mm(9f),
            mm(2.1f),
            mm(1.5f)
        )

        val barcodeBounds = RectF(
            contentLeft,
            bounds.top + mm(11f),
            contentRight,
            bounds.top + mm(21f)
        )
        drawEan13(canvas, product.barcode, barcodeBounds)

        val eanPaint = textPaint(mm(3.2f), bold = true).apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            product.barcode.filter(Char::isDigit),
            barcodeBounds.centerX(),
            barcodeBounds.bottom + mm(2.7f),
            eanPaint
        )

        if (showPrice) {
            val pricePaint = textPaint(mm(3.95f), bold = true).apply {
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(
                formatPrice(product.publicPrice),
                contentRight,
                bounds.bottom - mm(2.2f),
                pricePaint
            )
        }
    }

    private fun drawHookLabel(
        context: Context,
        canvas: Canvas,
        product: ProductInfo,
        left: Float,
        top: Float,
        showPrice: Boolean
    ) {
        val width = mm(HOOK_WIDTH_MM)
        val height = mm(HOOK_HEIGHT_MM)
        val bounds = RectF(left, top, left + width, top + height)

        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = mm(0.35f)
        }
        canvas.drawRect(bounds, cutPaint)

        // Dima Euro Hook per la perforazione manuale.
        // La sagoma parte a 7 mm dal bordo superiore.
        drawEuroHookGuide(
            canvas = canvas,
            centerX = bounds.centerX(),
            top = bounds.top + mm(5f)
        )

        val hookLogoBounds = RectF(
            bounds.left + mm(6f),
            bounds.top + mm(20f),
            bounds.right - mm(6f),
            bounds.top + mm(38f)
        )
        BitmapFactory.decodeResource(context.resources, R.drawable.de_pieri_blister)?.let {
            drawBitmapFitCenter(canvas, it, hookLogoBounds)
            it.recycle()
        }

        drawBrandSeparator(
            canvas = canvas,
            left = bounds.left + mm(6f),
            right = bounds.right - mm(6f),
            top = hookLogoBounds.bottom + mm(1f)
        )

        val imageBounds = RectF(
            bounds.left + mm(5f),
            bounds.top + mm(42f),
            bounds.right - mm(5f),
            bounds.top + mm(78f)
        )
        val image = loadProductImage(product.barcode)
        if (image != null) {
            drawBitmapFitCenter(canvas, image, imageBounds)
            image.recycle()
        }

        val codePaint = textPaint(mm(3f), bold = true)
        canvas.drawText(
            product.articleCode.trim(),
            bounds.left + mm(4f),
            bounds.top + mm(84f),
            codePaint
        )

        val descPaint = textPaint(mm(2.2f), bold = false)
        drawFittedText(
            canvas,
            product.description,
            descPaint,
            bounds.left + mm(4f),
            bounds.right - mm(4f),
            bounds.top + mm(90f),
            mm(2.2f),
            mm(1.5f)
        )

        val barcodeBounds = RectF(
            bounds.left + mm(6f),
            bounds.top + mm(98f),
            bounds.right - mm(6f),
            bounds.top + mm(113f)
        )
        drawEan13(canvas, product.barcode, barcodeBounds)

        val eanPaint = textPaint(mm(4.5f), bold = true).apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            product.barcode.filter(Char::isDigit),
            barcodeBounds.centerX(),
            barcodeBounds.bottom + mm(3.2f),
            eanPaint
        )

    }

    private fun drawEuroHookGuide(
        canvas: Canvas,
        centerX: Float,
        top: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(170, 170, 170)
            style = Paint.Style.STROKE
            strokeWidth = mm(0.25f)
        }

        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(140, 140, 140)
            style = Paint.Style.STROKE
            strokeWidth = mm(0.18f)
        }

        val width = mm(32f)
        val height = mm(11.5f)

        val left = centerX - width / 2f
        val right = centerX + width / 2f

        val bodyTop = top + mm(5f)
        val bodyBottom = top + height

        val radius = mm(4f)
        val domeRadius = mm(4f)

        val path = android.graphics.Path()

        path.moveTo(left + radius, bodyBottom)

        path.lineTo(right - radius, bodyBottom)

        path.quadTo(
            right,
            bodyBottom,
            right,
            bodyBottom - radius
        )

        path.lineTo(right, bodyTop + radius)

        path.quadTo(
            right,
            bodyTop,
            right - radius,
            bodyTop
        )

        path.lineTo(centerX + domeRadius, bodyTop)

        path.quadTo(
            centerX,
            top,
            centerX - domeRadius,
            bodyTop
        )

        path.lineTo(left + radius, bodyTop)

        path.quadTo(
            left,
            bodyTop,
            left,
            bodyTop + radius
        )

        path.lineTo(left, bodyBottom - radius)

        path.quadTo(
            left,
            bodyBottom,
            left + radius,
            bodyBottom
        )

        path.close()

        canvas.drawPath(path, paint)

        val yCenter = bodyTop + (bodyBottom - bodyTop) / 2f

        canvas.drawLine(
            centerX - mm(2f),
            yCenter,
            centerX + mm(2f),
            yCenter,
            crossPaint
        )

        canvas.drawLine(
            centerX,
            yCenter - mm(2f),
            centerX,
            yCenter + mm(2f),
            crossPaint
        )
    }

    private fun drawLogoPlaceholder(canvas: Canvas, bounds: RectF) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = mm(0.25f)
        }
        canvas.drawRect(bounds, border)

        val titlePaint = textPaint(mm(3.0f), bold = true).apply {
            textAlign = Paint.Align.CENTER
        }
        val subPaint = textPaint(mm(2.3f), bold = true).apply {
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(
            "MATERIALE ELETTRICO",
            bounds.centerX(),
            bounds.top + mm(7f),
            titlePaint
        )
        canvas.drawText(
            "DE PIERI FRANCO",
            bounds.centerX(),
            bounds.top + mm(13f),
            titlePaint
        )
        canvas.drawText(
            "LINEA BLISTER",
            bounds.centerX(),
            bounds.top + mm(20f),
            subPaint
        )
    }


    private fun drawBrandSeparator(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float
    ) {
        val strongPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = mm(0.45f)
        }

        val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(135, 135, 135)
            style = Paint.Style.STROKE
            strokeWidth = mm(0.22f)
        }

        canvas.drawLine(
            left,
            top,
            right,
            top,
            strongPaint
        )

        canvas.drawLine(
            left,
            top + mm(1.25f),
            right,
            top + mm(1.25f),
            lightPaint
        )
    }

    private fun textPaint(size: Float, bold: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            textSize = size
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (bold) Typeface.BOLD else Typeface.NORMAL
            )
        }

    private fun drawFittedText(
        canvas: Canvas,
        text: String,
        paint: Paint,
        left: Float,
        right: Float,
        baseline: Float,
        maxSize: Float,
        minSize: Float
    ) {
        val clean = text.replace('\n', ' ').replace('\r', ' ').trim()
        paint.textSize = maxSize
        while (paint.textSize > minSize && paint.measureText(clean) > right - left) {
            paint.textSize -= 0.35f
        }
        var fitted = clean
        while (fitted.length > 1 && paint.measureText(fitted) > right - left) {
            fitted = fitted.dropLast(1)
        }
        canvas.drawText(fitted, left, baseline, paint)
        paint.textSize = maxSize
    }

    private fun loadProductImage(barcode: String): Bitmap? {
        val clean = barcode.trim().filter(Char::isDigit)
        if (clean.isBlank()) return null
        val encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8.name())
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
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.use(BitmapFactory::decodeStream)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun drawBitmapFitCenter(canvas: Canvas, bitmap: Bitmap, bounds: RectF) {
        if (bitmap.width <= 0 || bitmap.height <= 0) return
        val scale = minOf(
            bounds.width() / bitmap.width.toFloat(),
            bounds.height() / bitmap.height.toFloat()
        )
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val dst = RectF(
            bounds.centerX() - w / 2f,
            bounds.centerY() - h / 2f,
            bounds.centerX() + w / 2f,
            bounds.centerY() + h / 2f
        )
        canvas.drawBitmap(
            bitmap,
            null,
            dst,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun drawEan13(canvas: Canvas, raw: String, bounds: RectF) {
        val ean = raw.filter(Char::isDigit).takeLast(13)
        if (ean.length != 13) return
        val modules = buildEan13Modules(ean)
        val quiet = 9
        val moduleWidth = bounds.width() / (modules.length + quiet * 2)
        val startX = bounds.left + quiet * moduleWidth
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
        }
        modules.forEachIndexed { i, c ->
            if (c == '1') {
                canvas.drawRect(
                    startX + i * moduleWidth,
                    bounds.top,
                    startX + (i + 1) * moduleWidth,
                    bounds.bottom,
                    paint
                )
            }
        }
    }

    private fun buildEan13Modules(ean: String): String {
        val l = arrayOf(
            arrayOf("0001101", "0100111"), arrayOf("0011001", "0110011"),
            arrayOf("0010011", "0011011"), arrayOf("0111101", "0100001"),
            arrayOf("0100011", "0011101"), arrayOf("0110001", "0111001"),
            arrayOf("0101111", "0000101"), arrayOf("0111011", "0010001"),
            arrayOf("0110111", "0001001"), arrayOf("0001011", "0010111")
        )
        val parity = arrayOf(
            "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
            "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"
        )
        val r = arrayOf(
            "1110010", "1100110", "1101100", "1000010", "1011100",
            "1001110", "1010000", "1000100", "1001000", "1110100"
        )
        return buildString {
            append("101")
            val p = parity[ean[0].digitToInt()]
            for (i in 1..6) {
                val d = ean[i].digitToInt()
                append(l[d][if (p[i - 1] == 'G') 1 else 0])
            }
            append("01010")
            for (i in 7..12) append(r[ean[i].digitToInt()])
            append("101")
        }
    }

    private fun formatPrice(raw: String): String {
        val cleaned = raw.trim().replace("€", "").replace(" ", "")
        val normalized = if (cleaned.contains(',') && cleaned.contains('.')) {
            cleaned.replace(".", "").replace(',', '.')
        } else cleaned.replace(',', '.')
        val value = normalized.toDoubleOrNull() ?: return ""
        return String.format(Locale.ITALY, "%.2f €", value)
    }

    private fun createDownloadFile(
        context: Context,
        product: ProductInfo
    ): File {
        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.ITALY
            ).format(Date())

        val safeCode = product.articleCode
            .trim()
            .replace(
                Regex("[^A-Za-z0-9_-]"),
                "_"
            )
            .ifBlank { "articolo" }

        val baseDir =
            context.getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS
            ) ?: context.filesDir

        val reportDir = File(
            baseDir,
            "Scan2Enter/Blister"
        ).apply {
            if (!exists() && !mkdirs()) {
                error("Impossibile creare la cartella dei report blister")
            }
        }

        return File(
            reportDir,
            "Blister_${safeCode}_$timestamp.pdf"
        )
    }

    private fun openPdf(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Anteprima blister").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun mm(value: Float): Float = value * PT_PER_MM
}