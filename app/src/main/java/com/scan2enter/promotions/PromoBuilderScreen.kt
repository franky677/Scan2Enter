package com.scan2enter.promotions

import android.graphics.Typeface
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.load
import coil3.request.crossfade
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.api.ProductPromoDto
import com.scan2enter.model.ProductInfo
import com.scan2enter.repository.ProductRepositoryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun promoPriceShape(index: Int): Shape =
    when (index.coerceIn(0, 9)) {
        // CLASSICA
        0 -> RoundedCornerShape(8.dp)

        // PILLOLA
        1 -> RoundedCornerShape(50)

        // OVALE
        2 -> RoundedCornerShape(50)

        // TAG
        3 -> GenericShape { size, _ ->
            moveTo(size.width * 0.12f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(size.width * 0.12f, size.height)
            lineTo(0f, size.height * 0.50f)
            close()
        }

        // TRAPEZIO
        4 -> GenericShape { size, _ ->
            moveTo(size.width * 0.10f, 0f)
            lineTo(size.width * 0.90f, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        // FRECCIA
        5 -> GenericShape { size, _ ->
            moveTo(0f, 0f)
            lineTo(size.width * 0.78f, 0f)
            lineTo(size.width, size.height * 0.50f)
            lineTo(size.width * 0.78f, size.height)
            lineTo(0f, size.height)
            lineTo(size.width * 0.10f, size.height * 0.50f)
            close()
        }

        // SCUDO
        6 -> GenericShape { size, _ ->
            moveTo(size.width * 0.08f, 0f)
            lineTo(size.width * 0.92f, 0f)
            lineTo(size.width, size.height * 0.28f)
            lineTo(size.width * 0.82f, size.height * 0.78f)
            lineTo(size.width * 0.50f, size.height)
            lineTo(size.width * 0.18f, size.height * 0.78f)
            lineTo(0f, size.height * 0.28f)
            close()
        }

        // STICKER
        7 -> GenericShape { size, _ ->
            val points = 16
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerX = size.width / 2f
            val outerY = size.height / 2f
            val innerX = outerX * 0.86f
            val innerY = outerY * 0.72f

            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val rx = if (i % 2 == 0) outerX else innerX
                val ry = if (i % 2 == 0) outerY else innerY
                val x = cx + (cos(angle) * rx).toFloat()
                val y = cy + (sin(angle) * ry).toFloat()

                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // ESPLOSIONE
        8 -> GenericShape { size, _ ->
            val points = 24
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerX = size.width / 2f
            val outerY = size.height / 2f
            val innerX = outerX * 0.74f
            val innerY = outerY * 0.60f

            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val rx = if (i % 2 == 0) outerX else innerX
                val ry = if (i % 2 == 0) outerY else innerY
                val x = cx + (cos(angle) * rx).toFloat()
                val y = cy + (sin(angle) * ry).toFloat()

                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // DIAGONALE
        else -> GenericShape { size, _ ->
            moveTo(size.width * 0.10f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width * 0.90f, size.height)
            lineTo(0f, size.height)
            close()
        }
    }
private fun formatPromoPrice(rawPrice: String): String {
    val value = rawPrice
        .trim()
        .replace("€", "")
        .replace(",", ".")
        .trim()
        .toDoubleOrNull()

    return if (value != null) {
        String.format(
            java.util.Locale.ITALY,
            "%.2f €",
            value
        )
    } else {
        rawPrice
    }
}

private fun shiftPromoHue(color: Color, slider: Float, intensity: Float): Color {
    val hueSlider = if (slider <= 0.80f) {
        slider / 0.80f
    } else {
        1.0f
    }

    val delta = (hueSlider - 0.5f) * 360f

    val r = color.red
    val g = color.green
    val b = color.blue

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min

    var h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }

    if (h < 0f) h += 360f

    val baseSaturation = if (max == 0f) 0f else d / max
    val s = (baseSaturation * intensity).coerceIn(0f, 1f)
    val baseValue = max
    val newHue = (h + delta + 360f) % 360f

    val blackFade = if (slider > 0.80f) {
        ((slider - 0.80f) / 0.20f).coerceIn(0f, 1f)
    } else {
        0f
    }

    val newValue = baseValue * (1f - blackFade)

    return Color.hsv(
        hue = newHue,
        saturation = s,
        value = newValue,
        alpha = color.alpha
    )
}

private fun promoFontFamily(value: Float): FontFamily {
    return when {
        value < 0.10f -> FontFamily.SansSerif
        value < 0.20f -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
        value < 0.30f -> FontFamily.Serif
        value < 0.40f -> FontFamily.Monospace
        value < 0.50f -> FontFamily.Cursive
        value < 0.60f -> FontFamily(Typeface.create("casual", Typeface.NORMAL))
        value < 0.70f -> FontFamily(Typeface.create("sans-serif-smallcaps", Typeface.NORMAL))
        value < 0.80f -> FontFamily(Typeface.create("source-sans-pro", Typeface.NORMAL))
        value < 0.90f -> FontFamily(Typeface.create("roboto-flex", Typeface.NORMAL))
        else -> FontFamily(Typeface.create("sec", Typeface.NORMAL))
    }
}

@Composable
private fun PromoExplosionBadge(
    titleScale: Float,
    explosionFont: Float,
    shapeIndex: Int
) {
    Box(
        modifier = Modifier
            .size(
                width = 250.dp,
                height = 88.dp
            )
            .rotate(-3f),
        contentAlignment = Alignment.Center
    ) {

        if (shapeIndex == 8) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                val points = 32
                val outerRadiusX = size.width * 0.49f
                val outerRadiusY = size.height * 0.49f
                val innerRadiusX = size.width * 0.38f
                val innerRadiusY = size.height * 0.34f

                val path = Path()

                for (i in 0 until points) {
                    val angle =
                        -PI / 2.0 +
                            (2.0 * PI * i / points)

                    val useOuter = i % 2 == 0

                    val radiusX =
                        if (useOuter) outerRadiusX
                        else innerRadiusX

                    val radiusY =
                        if (useOuter) outerRadiusY
                        else innerRadiusY

                    val x =
                        cx + cos(angle).toFloat() * radiusX

                    val y =
                        cy + sin(angle).toFloat() * radiusY

                    if (i == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                path.close()

                drawPath(
                    path = path,
                    color = Color.Black,
                    style = Stroke(width = 8f)
                )

                drawPath(
                    path = path,
                    color = Color(0xFFFFE000)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color(0xFFFFE000),
                        shape = promoPriceShape(shapeIndex)
                    )
                    .border(
                        width = 3.dp,
                        color = Color.Black,
                        shape = promoPriceShape(shapeIndex)
                    )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "OFFERTA",
                color = Color.Black,
                fontSize = 15.sp * titleScale,
                lineHeight = 15.sp * titleScale,
                fontWeight = FontWeight.Black,
                fontFamily = promoFontFamily(explosionFont),
            )

            Text(
                text = "BOMBA",
                color = Color(0xFFE30613),
                fontSize = 31.sp * titleScale,
                lineHeight = 31.sp * titleScale,
                fontWeight = FontWeight.Black,
                fontFamily = promoFontFamily(explosionFont),
            )
        }
    }
}
@Composable
private fun PromoDiscountBurst(
    discountPercent: Double,
    shapeIndex: Int
) {
    val discountShape = promoPriceShape(shapeIndex)

    Box(
        modifier = Modifier
            .size(
                width = 92.dp,
                height = 70.dp
            )
            .rotate(4f)
            .background(
                color = Color(0xFFFFE000),
                shape = discountShape
            )
            .border(
                width = 3.dp,
                color = Color.Black,
                shape = discountShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String.format(
                java.util.Locale.ITALY,
                "-%.0f%%",
                discountPercent
            ),
            color = Color(0xFFE30613),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
    }
}
@Composable
fun PromoBuilderScreen(
    selectedBarcode: String?,
    onBack: () -> Unit,
    onChooseArticle: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val promoPrintScope = rememberCoroutineScope()

    val promoPrintLayer = rememberGraphicsLayer()

    suspend fun capturePromoPngBase64(): String {
        val imageBitmap = promoPrintLayer.toImageBitmap()
        val bitmap = imageBitmap.asAndroidBitmap()
        val output = ByteArrayOutputStream()

        bitmap.compress(
            Bitmap.CompressFormat.PNG,
            100,
            output
        )

        return Base64.encodeToString(
            output.toByteArray(),
            Base64.NO_WRAP
        )
    }

    var selectedProduct by remember {
        mutableStateOf<ProductInfo?>(null)
    }

    var selectedPromo by remember {
        mutableStateOf<ProductPromoDto?>(null)
    }

    var loadError by remember {
        mutableStateOf<String?>(null)
    }

    var selectedPreset by remember {
        mutableStateOf("BOMBA")
    }

    // Regolazioni globali Promo Builder - valide per tutti i preset
    var showStylePanel by remember { mutableStateOf(false) }
    var colorHue by remember { mutableStateOf(0.40f) }
    var colorIntensity by remember { mutableStateOf(1.0f) }
    var showDimensionsPanel by remember { mutableStateOf(false) }
    var globalScale by remember { mutableStateOf(1.0f) }
    var promoMeasuredSize by remember { mutableStateOf(IntSize.Zero) }
    var titleScale by remember { mutableStateOf(1.0f) }
    var imageScale by remember { mutableStateOf(1.0f) }
    var priceScale by remember { mutableStateOf(1.0f) }
    var priceShape by remember { mutableStateOf(0f) }
    var discountShape by remember { mutableStateOf(8f) }
    var titleShape by remember { mutableStateOf(0f) }
    var imageShape by remember { mutableStateOf(0f) }
    var shapeControl by remember { mutableStateOf("PREZZO") }

    var explosionFont by remember { mutableStateOf(0f) }
    var descriptionFont by remember { mutableStateOf(0f) }
    var priceFont by remember { mutableStateOf(0f) }
    var footerFont by remember { mutableStateOf(0f) }

    
var styleSection by remember { mutableStateOf("FONT") }
    
var fontControl by remember { mutableStateOf("TITOLI") }
    
var dimensionControl by remember { mutableStateOf("GENERALE") }
    
var colorControl by remember { mutableStateOf("TONALITA") }

    // Personalizzazione del preset LIBERO
    var liberoTitle1 by remember { mutableStateOf("OFFERTA") }
    var liberoTitle2 by remember { mutableStateOf("SPECIALE") }
    var liberoSubtitle by remember { mutableStateOf("UN PREZZO DA COGLIERE AL VOLO") }

    LaunchedEffect(selectedBarcode) {
        selectedProduct = null
        selectedPromo = null
        loadError = null

        if (!selectedBarcode.isNullOrBlank()) {
            val result = withContext(Dispatchers.IO) {
                ProductRepositoryProvider
                    .get(context.applicationContext)
                    .getProduct(selectedBarcode)
            }

            result
                .onSuccess { product ->
                    selectedProduct = product

                    if (product.articleId > 0L) {
                        selectedPromo = withContext(Dispatchers.IO) {
                            GatewayApiClient()
                                .getProductPromo(product.articleId)
                                .getOrNull()
                        }
                    }
                }
                .onFailure { error ->
                    loadError = error.message ?: "Errore caricamento articolo"
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "PROMO BUILDER",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Crea cartelli promozionali grafici",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(16.dp))

        selectedProduct?.let { product ->

            val imageUrl = remember(product.barcode) {
                GatewayApiClient()
                    .getProductImageUrl(product.barcode)
            }

            /*
             * Scheda articolo selezionato.
             */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AndroidView(
                    modifier = Modifier.size(82.dp),
                    factory = { imageContext ->
                        ImageView(imageContext).apply {
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                    },
                    update = { imageView ->
                        imageView.load(imageUrl) {
                            crossfade(true)
                        }
                    }
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text("Codice: ${product.articleCode}")
                    Text("EAN: ${product.barcode}")

                    Text(
                        text = formatPromoPrice(product.publicPrice),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }


            Spacer(Modifier.height(16.dp))

            Text(
                text = "TEMPLATE",
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "LIBERO",
                    "BOMBA",
                    "BLACK",
                    "RISPARMIO",
                    "NOVITÀ"
                ).forEach { preset ->

                    val selected = selectedPreset == preset

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color =
                                    if (selected) {
                                        Color.Black
                                    } else {
                                        Color(0xFFE0E0E0)
                                    },
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.dp,
                                color =
                                    if (selected) {
                                        Color(0xFFFFE000)
                                    } else {
                                        Color.Gray
                                    },
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                selectedPreset = preset
                            }
                            .padding(
                                vertical = 8.dp,
                                horizontal = 2.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset,
                            color =
                                if (selected) {
                                    Color(0xFFFFE000)
                                } else {
                                    Color.Black
                                },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (selectedPreset == "LIBERO") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = liberoTitle1,
                        onValueChange = { liberoTitle1 = it },
                        label = { Text("Titolo 1") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = liberoTitle2,
                        onValueChange = { liberoTitle2 = it },
                        label = { Text("Titolo 2") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = liberoSubtitle,
                        onValueChange = { liberoSubtitle = it },
                        label = { Text("Sottotitolo") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))
            }


            /*
             * PRESET BOMBA
             * Primo vero cartello grafico del Promo Builder.
             */
            val isLiberoPreset = selectedPreset == "LIBERO"
            val isBlackPreset = selectedPreset == "BLACK"
            val isRisparmioPreset = selectedPreset == "RISPARMIO"
            val isNovitaPreset = selectedPreset == "NOVITÀ"

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE7E7E7))
                    .border(2.dp, Color.DarkGray)
                    .padding(4.dp)
            ) {
                val a3Width = maxWidth
                val a3Height = a3Width * (420f / 297f)
                val a4Width = a3Width * (210f / 297f)
                val a4Height = a3Height * (297f / 420f)
                val promoFitScale = if (promoMeasuredSize.width > 0 && promoMeasuredSize.height > 0) {
                    minOf(
                        1f,
                        (promoMeasuredSize.width.toFloat() * (297f / 210f)) / promoMeasuredSize.height.toFloat()
                    )
                } else {
                    1f
                }

                Box(
                    modifier = Modifier
                        .width(a3Width)
                        .height(a3Height)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .width(a4Width)
                            .height(a4Height)
                            .border(2.dp, Color.Gray)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .graphicsLayer {
                                scaleX = 1f
                                scaleY = promoFitScale
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    ) {

                    Column(
                        modifier = Modifier
                            .width(a4Width)
                            .onGloballyPositioned { coordinates ->
                                promoMeasuredSize = coordinates.size
                            }
                            .drawWithContent {
                        promoPrintLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawContent()
                    }
                    .graphicsLayer {
                        scaleX = globalScale
                        scaleY = globalScale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .border(
                        width = 3.dp,
                        color =
                            if (isBlackPreset) {
                                shiftPromoHue(Color(0xFFFFD700), colorHue, colorIntensity)
                            } else if (isLiberoPreset) {
                                shiftPromoHue(Color(0xFFFF40C8), colorHue, colorIntensity)
                            } else if (isRisparmioPreset) {
                                shiftPromoHue(Color(0xFFFFE000), colorHue, colorIntensity)
                            } else if (isNovitaPreset) {
                                shiftPromoHue(Color(0xFF00E5FF), colorHue, colorIntensity)
                            } else {
                                Color.Black
                            },
                        shape = RoundedCornerShape(18.dp)
                    )
                    .background(
                        color =
                            if (isBlackPreset) {
                                Color.Black
                            } else if (isLiberoPreset) {
                                shiftPromoHue(Color(0xFF6A1B9A), colorHue, colorIntensity)
                            } else if (isRisparmioPreset) {
                                shiftPromoHue(Color(0xFF1B5E20), colorHue, colorIntensity)
                            } else if (isNovitaPreset) {
                                shiftPromoHue(Color(0xFF0D47A1), colorHue, colorIntensity)
                            } else {
                                shiftPromoHue(Color(0xFFE30613), colorHue, colorIntensity)
                            },
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                /*
                 * Testata grafica BOMBA:
                 * esplosione gialla con bordo nero.
                 */
                if (isBlackPreset) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = shiftPromoHue(Color(0xFFFFD700), colorHue, colorIntensity),
                                shape = promoPriceShape(titleShape.toInt())
                            )
                            .border(
                                width = 2.dp,
                                color = Color.White,
                                shape = promoPriceShape(titleShape.toInt())
                            )
                            .padding(
                                horizontal = 8.dp,
                                vertical = 5.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "BLACK",
                                color = Color.Black,
                                fontSize = 34.sp * titleScale,
                                lineHeight = 32.sp * titleScale,
                                fontWeight = FontWeight.Black,
                                fontFamily = promoFontFamily(explosionFont),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "FRIDAY",
                                color = Color.Black,
                                fontSize = 25.sp * titleScale,
                                lineHeight = 24.sp * titleScale,
                                fontWeight = FontWeight.Black,
                                fontFamily = promoFontFamily(explosionFont),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "OFFERTA SPECIALE  SOLO PER POCO",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                } else if (isLiberoPreset) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = shiftPromoHue(Color(0xFFFF40C8), colorHue, colorIntensity),
                                shape = promoPriceShape(titleShape.toInt())
                            )
                            .border(
                                width = 2.dp,
                                color = Color.White,
                                shape = promoPriceShape(titleShape.toInt())
                            )
                            .padding(
                                horizontal = 8.dp,
                                vertical = 6.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = liberoTitle1,
                                color = Color.White,
                                fontSize = 31.sp * titleScale,
                                lineHeight = 31.sp * titleScale,
                                fontWeight = FontWeight.Black,
                                fontFamily = promoFontFamily(explosionFont),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = liberoTitle2,
                                color = Color.White,
                                fontSize = 22.sp * titleScale,
                                lineHeight = 23.sp * titleScale,
                                fontWeight = FontWeight.Black,
                                fontFamily = promoFontFamily(explosionFont),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = liberoSubtitle,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                } else if (isRisparmioPreset) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = shiftPromoHue(Color(0xFFFFE000), colorHue, colorIntensity),
                                shape = promoPriceShape(titleShape.toInt())
                            )
                            .border(
                                width = 2.dp,
                                color = Color.White,
                                shape = promoPriceShape(titleShape.toInt())
                            )
                            .padding(
                                horizontal = 8.dp,
                                vertical = 7.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SUPER RISPARMIO",
                            color = shiftPromoHue(Color(0xFF1B5E20), colorHue, colorIntensity),
                            fontSize = 27.sp * titleScale,
                            lineHeight = 29.sp * titleScale,
                            fontWeight = FontWeight.Black,
                            fontFamily = promoFontFamily(explosionFont),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "CONVENIENZA CHE SI VEDE",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                } else if (isNovitaPreset) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = shiftPromoHue(Color(0xFF00E5FF), colorHue, colorIntensity),
                                shape = promoPriceShape(titleShape.toInt())
                            )
                            .border(
                                width = 2.dp,
                                color = Color.White,
                                shape = promoPriceShape(titleShape.toInt())
                            )
                            .padding(
                                horizontal = 8.dp,
                                vertical = 6.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "NOVITÀ",
                                color = shiftPromoHue(Color(0xFF0D47A1), colorHue, colorIntensity),
                                fontSize = 32.sp * titleScale,
                                lineHeight = 32.sp * titleScale,
                                fontWeight = FontWeight.Black,
                                fontFamily = promoFontFamily(explosionFont),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "APPENA ARRIVATO",
                                color = shiftPromoHue(Color(0xFF0D47A1), colorHue, colorIntensity),
                                fontSize = 15.sp * titleScale,
                                fontWeight = FontWeight.Bold,
                                fontFamily = promoFontFamily(explosionFont),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "SCOPRILO ORA",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                } else {
                    PromoExplosionBadge(titleScale, explosionFont, titleShape.toInt())
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = product.description.uppercase(),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 19.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = promoFontFamily(descriptionFont),
                    color =
                        if (isBlackPreset) {
                            shiftPromoHue(Color(0xFFFFD700), colorHue, colorIntensity)
                        } else {
                            Color.White
                        }
                )

                Spacer(Modifier.height(10.dp))

                val promo = selectedPromo

                val originalPrice =
                    promo
                        ?.publicPrice
                        ?.takeIf { it > 0.0 }

                val offerPrice =
                    promo
                        ?.offerPrice
                        ?.takeIf { it > 0.0 }

                val effectiveDiscount =
                    when {
                        promo == null -> null

                        promo.discountPercent > 0.0 ->
                            promo.discountPercent

                        originalPrice != null &&
                            offerPrice != null &&
                            originalPrice > 0.0 ->

                            100.0 * (
                                1.0 -
                                    offerPrice / originalPrice
                                )

                        else -> null
                    }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    /*
                     * Foto prodotto su riquadro bianco.
                     */
                    Column(
                        modifier = Modifier.width(142.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier.size(
                                width = 142.dp,
                                height = 158.dp
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(142.dp)
                                    .background(
                                        color = Color.White,
                                        shape = promoPriceShape(imageShape.toInt())
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = Color.Black,
                                        shape = promoPriceShape(imageShape.toInt())
                                    )
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AndroidView(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = 1.15f * imageScale
                                            scaleY = 1.15f * imageScale
                                        },
                                    factory = { imageContext ->
                                        ImageView(imageContext).apply {
                                            scaleType =
                                                ImageView.ScaleType.CENTER_INSIDE
                                        }
                                    },
                                    update = { imageView ->
                                        imageView.load(imageUrl) {
                                            crossfade(true)
                                        }
                                    }
                                )
                            }

                            if (
                                effectiveDiscount != null &&
                                effectiveDiscount > 0.0
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(
                                            x = 26.dp,
                                            y = 18.dp
                                        )
                                ) {
                                    PromoDiscountBurst(
                                        discountPercent = effectiveDiscount,
                                        shapeIndex = discountShape.toInt()
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = product.articleCode,
                            modifier = Modifier.fillMaxWidth(),
                            color =
                                if (isBlackPreset) {
                                    shiftPromoHue(Color(0xFFFFD700), colorHue, colorIntensity)
                                } else {
                                    Color.White
                                },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start
                        )
                    }

                    /*
                     * Area prezzo: deve essere il punto più forte del cartello.
                     */
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {


                        if (
                            originalPrice != null &&
                            offerPrice != null
                        ) {
                            Text(
                                text = String.format(
                                    java.util.Locale.ITALY,
                                    "%.2f €",
                                    originalPrice
                                ),
                                color = Color.White,
                                fontSize = 16.sp * priceScale,
                                fontWeight = FontWeight.Bold,
                                fontFamily = promoFontFamily(priceFont),
                                textDecoration =
                                    TextDecoration.LineThrough
                            )

                            Spacer(Modifier.height(4.dp))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .rotate(-1.5f)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = promoPriceShape(priceShape.toInt())
                                )
                                .background(
                                    color =
                                        if (isBlackPreset) {
                                            shiftPromoHue(Color(0xFFFFD700), colorHue, colorIntensity)
                                        } else if (isLiberoPreset) {
                                            shiftPromoHue(Color(0xFFFF40C8), colorHue, colorIntensity)
                                        } else if (isRisparmioPreset) {
                                            shiftPromoHue(Color(0xFFFFE000), colorHue, colorIntensity)
                                        } else if (isNovitaPreset) {
                                            shiftPromoHue(Color(0xFF00E5FF), colorHue, colorIntensity)
                                        } else {
                                            shiftPromoHue(Color(0xFFFFE000), colorHue, colorIntensity)
                                        },
                                    shape = promoPriceShape(priceShape.toInt())
                                )
                                .border(
                                    width = 3.dp,
                                    color = Color.Black,
                                    shape = promoPriceShape(priceShape.toInt())
                                )
                                .padding(
                                    horizontal = 4.dp,
                                    vertical = 10.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text =
                                    if (offerPrice != null) {
                                        String.format(
                                            java.util.Locale.ITALY,
                                            "%.2f €",
                                            offerPrice
                                        )
                                    } else {
                                        formatPromoPrice(
                                            product.publicPrice
                                        )
                                    },
                                color =
                                    if (isBlackPreset) {
                                        Color.Black
                                    } else if (isLiberoPreset) {
                                        Color.White
                                    } else if (isRisparmioPreset) {
                                        shiftPromoHue(Color(0xFF1B5E20), colorHue, colorIntensity)
                                    } else if (isNovitaPreset) {
                                        shiftPromoHue(Color(0xFF0D47A1), colorHue, colorIntensity)
                                    } else {
                                        shiftPromoHue(Color(0xFFE30613), colorHue, colorIntensity)
                                    },
                                fontSize = 22.sp * priceScale,
                                lineHeight = 24.sp * priceScale,
                                fontWeight = FontWeight.Black,
                                fontFamily = promoFontFamily(priceFont),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false
                            )
                        }


                    }
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .rotate(-1f)
                        .background(
                            color = Color.Black,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = shiftPromoHue(Color(0xFFFFE000), colorHue, colorIntensity),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(
                            horizontal = 10.dp,
                            vertical = 7.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text =
                            if (isBlackPreset) {
                                "BLACK FRIDAY  OFFERTA SPECIALE"
                            } else if (isLiberoPreset) {
                                "OFFERTA SPECIALE"
                            } else if (isRisparmioPreset) {
                                "PREZZO CONVENIENZA"
                            } else if (isNovitaPreset) {
                                "NOVITÀ  APPENA ARRIVATO"
                            } else {
                                "SUPER PREZZO DA NON PERDERE!"
                            },
                        color = shiftPromoHue(Color(0xFFFFE000), colorHue, colorIntensity),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = promoFontFamily(footerFont),
                        textAlign = TextAlign.Center
                    )
                }

            }

                    }
                    Text(
                        text = "A4 210 x 297 mm",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )

                    Text(
                        text = "A3 297 x 420 mm",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        if (
            selectedBarcode != null &&
            selectedProduct == null &&
            loadError == null
        ) {
            Text("Caricamento articolo...")
            Spacer(Modifier.height(16.dp))
        }

        loadError?.let { error ->
            Text("Errore: $error")
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = onChooseArticle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (selectedProduct == null) {
                    "SCEGLI ARTICOLO"
                } else {
                    "CAMBIA ARTICOLO"
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("DIMENSIONI", "FONT", "COLORI", "FORME").forEach { section ->
                Button(
                    onClick = { styleSection = section },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = section, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (styleSection == "DIMENSIONI") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("GENERALE", "TITOLI", "IMMAGINE", "PREZZO").forEach { control ->
                    Button(
                        onClick = { dimensionControl = control },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (control == "IMMAGINE") "FOTO" else control,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            val dimensionValue = when (dimensionControl) {
                "GENERALE" -> globalScale
                "TITOLI" -> titleScale
                "IMMAGINE" -> imageScale
                else -> priceScale
            }

            Text("$dimensionControl: ${(dimensionValue * 100).toInt()}%")
            if (dimensionControl == "GENERALE" && promoMeasuredSize.width > 0) {
                Text("Promo reale: ${promoMeasuredSize.width} x ${promoMeasuredSize.height} px")
            }

            Slider(
                value = dimensionValue,
                onValueChange = { value ->
                    when (dimensionControl) {
                        "GENERALE" -> globalScale = value
                        "TITOLI" -> titleScale = value
                        "IMMAGINE" -> imageScale = value
                        "PREZZO" -> priceScale = value
                    }
                },
                valueRange = if (dimensionControl == "GENERALE") {
                    0.25f..1.414f
                } else {
                    0.70f..1.30f
                },
                modifier = Modifier.fillMaxWidth()
            )
        }


        if (styleSection == "FORME") {
            val shapeNames = listOf(
                "CLASSICA",
                "PILLOLA",
                "OVALE",
                "TAG",
                "TRAPEZIO",
                "FRECCIA",
                "SCUDO",
                "STICKER",
                "ESPLOSIONE",
                "DIAGONALE"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("PREZZO", "SCONTO", "TITOLO", "FOTO").forEach { control ->
                    Button(
                        onClick = { shapeControl = control },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = control, fontSize = 9.sp)
                    }
                }
            }

            val currentShape = when (shapeControl) {
                "SCONTO" -> discountShape
                "TITOLO" -> titleShape
                "FOTO" -> imageShape
                else -> priceShape
            }

            val currentShapeIndex = currentShape.toInt()
                .coerceIn(0, shapeNames.lastIndex)

            Text("$shapeControl: ${shapeNames[currentShapeIndex]}")

            Slider(
                value = currentShape,
                onValueChange = { value ->
                    when (shapeControl) {
                        "SCONTO" -> discountShape = value
                        "TITOLO" -> titleShape = value
                        "FOTO" -> imageShape = value
                        else -> priceShape = value
                    }
                },
                valueRange = 0f..shapeNames.lastIndex.toFloat(),
                steps = shapeNames.size - 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (styleSection == "FONT") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("TITOLI", "DESCR.", "PREZZO", "SOTTO").forEach { control ->
                    Button(
                        onClick = { fontControl = control },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = control, fontSize = 9.sp)
                    }
                }
            }

            val currentFont = when (fontControl) {
                "TITOLI" -> explosionFont
                "DESCR." -> descriptionFont
                "PREZZO" -> priceFont
                else -> footerFont
            }

            val currentFontName = when {
                currentFont < 0.10f -> "SANS"
                currentFont < 0.20f -> "CONDENSATO"
                currentFont < 0.30f -> "SERIF"
                currentFont < 0.40f -> "MONO"
                currentFont < 0.50f -> "DANCING"
                currentFont < 0.60f -> "CASUAL"
                currentFont < 0.70f -> "SMALL CAPS"
                currentFont < 0.80f -> "SOURCE SANS"
                currentFont < 0.90f -> "ROBOTO FLEX"
                else -> "ONE UI"
            }

            Text("$fontControl: $currentFontName")

            Slider(
                value = currentFont,
                onValueChange = { value ->
                    when (fontControl) {
                        "TITOLI" -> explosionFont = value
                        "DESCR." -> descriptionFont = value
                        "PREZZO" -> priceFont = value
                        "SOTTO" -> footerFont = value
                    }
                },
                valueRange = 0f..1f,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (styleSection == "COLORI") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("TONALITA", "INTENSITA").forEach { control ->
                    Button(
                        onClick = { colorControl = control },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (control == "TONALITA") "TONO" else "INTENS.",
                            fontSize = 10.sp
                        )
                    }
                }
            }

            if (colorControl == "TONALITA") {
                Text("Tonalita colore")

                Slider(
                    value = colorHue,
                    onValueChange = { colorHue = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text("Intensita colore: ${(colorIntensity * 100).toInt()}%")

                Slider(
                    value = colorIntensity,
                    onValueChange = { colorIntensity = it },
                    valueRange = 0.50f..1.50f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                val product = selectedProduct

                if (
                    product != null

                ) {
                    promoPrintScope.launch {
                        val pngBase64 = capturePromoPngBase64()

                        val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="UTF-8">
                                <style>
                                    @page { size: A4 portrait; margin: 0; }
                                    html, body {
                                        margin: 0;
                                        padding: 0;
                                        width: 210mm;
                                        height: 297mm;
                                        background: white;
                                    }
                                    body {
                                        display: flex;
                                        align-items: center;
                                        justify-content: center;
                                    }
                                    img {
                                        display: block;
                                        max-width: 210mm;
                                        max-height: 297mm;
                                        width: auto;
                                        height: auto;
                                    }
                                </style>
                            </head>
                            <body>
                                <img src="data:image/png;base64,$pngBase64">
                            </body>
                            </html>
                        """.trimIndent()

                        printPromoHtml(
                            context = context,
                            html = html,
                            jobName = "Promo ${product.articleCode}"
                        )
                    }
                }
            },
            enabled = selectedProduct != null,


            modifier = Modifier.fillMaxWidth()
        ) {
            Text("STAMPA A4")
        }
    }
}






































private fun printPromoHtml(
    context: Context,
    html: String,
    jobName: String
) {
    val webView = WebView(context)

    webView.settings.javaScriptEnabled = false

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val printManager =
                context.getSystemService(Context.PRINT_SERVICE) as PrintManager

            val adapter =
                view.createPrintDocumentAdapter(jobName)

            printManager.print(
                jobName,
                adapter,
                PrintAttributes.Builder()
                    .setMediaSize(
                        PrintAttributes.MediaSize.ISO_A4.asPortrait()
                    )
                    .build()
            )
        }
    }

    webView.loadDataWithBaseURL(
        null,
        html,
        "text/html",
        "UTF-8",
        null
    )
}

private fun buildLiberoPromoHtml(
    title1: String,
    title2: String,
    subtitle: String,
    description: String,
    articleCode: String,
    imageUrl: String,
    originalPrice: Double?,
    offerPrice: Double?,
    discountPercent: Double?,
    globalScale: Float,
    titleScale: Float,
    imageScale: Float,
    priceScale: Float,
    colorHue: Float,
    colorIntensity: Float
): String {

    fun esc(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    val oldPriceHtml =
        if (originalPrice != null && offerPrice != null) {
            val value = String.format(
                java.util.Locale.ITALY,
                "%.2f \u20AC",
                originalPrice
            )
            """<div class="old-price">${esc(value)}</div>"""
        } else {
            ""
        }

    val finalPrice =
        offerPrice
            ?: originalPrice
            ?: 0.0

    val priceText = String.format(
        java.util.Locale.ITALY,
        "%.2f \u20AC",
        finalPrice
    )

    val discountHtml =
        if (discountPercent != null && discountPercent > 0.0) {
            val discountText = String.format(
                java.util.Locale.ITALY,
                "-%.0f%%",
                discountPercent
            )
            """<div class="discount">${esc(discountText)}</div>"""
        } else {
            ""
        }

    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<style>

@page {
    size: A4 portrait;
    margin: 0;
}

html, body {
    width: 210mm;
    height: 297mm;
    margin: 0;
    padding: 0;
}

body {
    font-family: Arial, Helvetica, sans-serif;
    background: #6617a8;
}

.poster {
    box-sizing: border-box;
    width: 210mm;
    height: 297mm;
    padding: 12mm;
    background:
        linear-gradient(
            145deg,
            #3b086d 0%,
            #8d18c7 48%,
            #ff40c8 100%
        );
    color: white;
    text-align: center;
    transform: scale(${globalScale});
    transform-origin: center center;
    overflow: hidden;
}

.title1 {
    font-size: ${25f * titleScale}mm;
    line-height: 0.86;
    font-weight: 900;
    letter-spacing: -1mm;
    text-shadow: 1.5mm 1.5mm 0 #000000;
}

.title2 {
    font-size: ${22f * titleScale}mm;
    line-height: 0.92;
    font-weight: 900;
    color: #ff40c8;
    -webkit-text-stroke: 0.7mm white;
    text-shadow: 1.3mm 1.3mm 0 #000000;
}

.subtitle {
    margin-top: 5mm;
    font-size: 6mm;
    font-weight: 900;
}

.description {
    margin-top: 10mm;
    min-height: 31mm;
    font-size: 10mm;
    line-height: 1.05;
    font-weight: 900;
    text-transform: uppercase;
}

.product-image {
    box-sizing: border-box;
    width: ${150f * imageScale}mm;
    height: ${65f * imageScale}mm;
    margin: 5mm auto 0 auto;
    padding: 3mm;
    background: white;
    border: 1.5mm solid #000000;
    border-radius: 4mm;
    overflow: hidden;
}

.product-image img {
    width: 100%;
    height: 100%;
    object-fit: contain;
    display: block;
}

.price-area {
    position: relative;
    margin: 7mm auto 0 auto;
    width: 170mm;
}

.old-price {
    font-size: ${8f * priceScale}mm;
    font-weight: 900;
    text-decoration: line-through;
    margin-bottom: 3mm;
}

.price {
    box-sizing: border-box;
    background: #ff40c8;
    border: 1.5mm solid #000000;
    border-radius: 4mm;
    color: white;
    font-size: ${25f * priceScale}mm;
    line-height: 1;
    font-weight: 900;
    padding: 8mm 3mm;
    transform: rotate(-1.5deg);
    box-shadow: 2mm 2mm 0 rgba(0,0,0,0.35);
}

.discount {
    position: absolute;
    right: -5mm;
    top: -10mm;
    box-sizing: border-box;
    width: 35mm;
    height: 35mm;
    border-radius: 50%;
    background: #ffe000;
    border: 1.5mm solid #000000;
    color: #e30613;
    font-size: 11mm;
    line-height: 32mm;
    font-weight: 900;
    transform: rotate(8deg);
}

.article {
    margin-top: 9mm;
    font-size: 5mm;
    font-weight: 700;
}

.footer {
    margin-top: 10mm;
    background: #000000;
    border: 1mm solid #ffe000;
    border-radius: 2mm;
    color: #ffe000;
    font-size: 7mm;
    font-weight: 900;
    padding: 4mm;
}

</style>
</head>

<body>
<div class="poster">

    <div class="title1">${esc(title1)}</div>
    <div class="title2">${esc(title2)}</div>

    <div class="subtitle">
        ${esc(subtitle)}
    </div>

    <div class="description">
        ${esc(description)}
    </div>

    <div class="product-image"><img src="${esc(imageUrl)}" /></div>

    <div class="price-area">
        $oldPriceHtml

        <div class="price">
            ${esc(priceText)}
        </div>

        $discountHtml
    </div>

    <div class="article">
        COD. ${esc(articleCode)}
    </div>

    <div class="footer">
        OFFERTA SPECIALE
    </div>

</div>
</body>
</html>
""".trimIndent()
}
































