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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
    when (index.coerceIn(0, 49)) {
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
        9 -> GenericShape { size, _ ->
            moveTo(size.width * 0.10f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width * 0.90f, size.height)
            lineTo(0f, size.height)
            close()
        }

        // ESAGONO
        10 -> GenericShape { size, _ ->
            moveTo(size.width * 0.22f, 0f)
            lineTo(size.width * 0.78f, 0f)
            lineTo(size.width, size.height * 0.50f)
            lineTo(size.width * 0.78f, size.height)
            lineTo(size.width * 0.22f, size.height)
            lineTo(0f, size.height * 0.50f)
            close()
        }

        // OTTAGONO
        11 -> GenericShape { size, _ ->
            moveTo(size.width * 0.18f, 0f)
            lineTo(size.width * 0.82f, 0f)
            lineTo(size.width, size.height * 0.18f)
            lineTo(size.width, size.height * 0.82f)
            lineTo(size.width * 0.82f, size.height)
            lineTo(size.width * 0.18f, size.height)
            lineTo(0f, size.height * 0.82f)
            lineTo(0f, size.height * 0.18f)
            close()
        }

        // ROMBO
        12 -> GenericShape { size, _ ->
            moveTo(size.width * 0.50f, 0f)
            lineTo(size.width, size.height * 0.50f)
            lineTo(size.width * 0.50f, size.height)
            lineTo(0f, size.height * 0.50f)
            close()
        }

        // BIGLIETTO
        13 -> GenericShape { size, _ ->
            moveTo(0f, 0f)
            lineTo(size.width * 0.90f, 0f)
            lineTo(size.width, size.height * 0.22f)
            lineTo(size.width, size.height)
            lineTo(size.width * 0.10f, size.height)
            lineTo(0f, size.height * 0.78f)
            close()
        }

        // COUPON
        14 -> GenericShape { size, _ ->
            moveTo(size.width * 0.08f, 0f)
            lineTo(size.width * 0.92f, 0f)
            lineTo(size.width, size.height * 0.18f)
            lineTo(size.width * 0.94f, size.height * 0.34f)
            lineTo(size.width, size.height * 0.50f)
            lineTo(size.width * 0.94f, size.height * 0.66f)
            lineTo(size.width, size.height * 0.82f)
            lineTo(size.width * 0.92f, size.height)
            lineTo(size.width * 0.08f, size.height)
            lineTo(0f, size.height * 0.82f)
            lineTo(size.width * 0.06f, size.height * 0.66f)
            lineTo(0f, size.height * 0.50f)
            lineTo(size.width * 0.06f, size.height * 0.34f)
            lineTo(0f, size.height * 0.18f)
            close()
        }

        // BANDIERA
        15 -> GenericShape { size, _ ->
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width * 0.82f, size.height * 0.50f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        // CHEVRON
        16 -> GenericShape { size, _ ->
            moveTo(0f, 0f)
            lineTo(size.width * 0.78f, 0f)
            lineTo(size.width, size.height * 0.50f)
            lineTo(size.width * 0.78f, size.height)
            lineTo(0f, size.height)
            lineTo(size.width * 0.22f, size.height * 0.50f)
            close()
        }

        // FRECCIA DOPPIA
        17 -> GenericShape { size, _ ->
            moveTo(size.width * 0.18f, 0f)
            lineTo(size.width * 0.82f, 0f)
            lineTo(size.width, size.height * 0.50f)
            lineTo(size.width * 0.82f, size.height)
            lineTo(size.width * 0.18f, size.height)
            lineTo(0f, size.height * 0.50f)
            close()
        }

        // PUNTA
        18 -> GenericShape { size, _ ->
            moveTo(0f, 0f)
            lineTo(size.width * 0.72f, 0f)
            lineTo(size.width, size.height * 0.50f)
            lineTo(size.width * 0.72f, size.height)
            lineTo(0f, size.height)
            close()
        }

        // FULMINE
        19 -> GenericShape { size, _ ->
            moveTo(size.width * 0.34f, 0f)
            lineTo(size.width * 0.86f, 0f)
            lineTo(size.width * 0.62f, size.height * 0.38f)
            lineTo(size.width, size.height * 0.38f)
            lineTo(size.width * 0.42f, size.height)
            lineTo(size.width * 0.55f, size.height * 0.56f)
            lineTo(size.width * 0.12f, size.height * 0.56f)
            close()
        }

        // STELLA 5
        20 -> GenericShape { size, _ ->
            val points = 10
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val radius = if (i % 2 == 0) 1f else 0.42f
                val x = cx + (cos(angle) * size.width * 0.50f * radius).toFloat()
                val y = cy + (sin(angle) * size.height * 0.50f * radius).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // STELLA 8
        21 -> GenericShape { size, _ ->
            val points = 16
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val radius = if (i % 2 == 0) 1f else 0.55f
                val x = cx + (cos(angle) * size.width * 0.50f * radius).toFloat()
                val y = cy + (sin(angle) * size.height * 0.50f * radius).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // STELLA 12
        22 -> GenericShape { size, _ ->
            val points = 24
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val radius = if (i % 2 == 0) 1f else 0.62f
                val x = cx + (cos(angle) * size.width * 0.50f * radius).toFloat()
                val y = cy + (sin(angle) * size.height * 0.50f * radius).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // SIGILLO
        23 -> GenericShape { size, _ ->
            val points = 32
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val radius = if (i % 2 == 0) 1f else 0.90f
                val x = cx + (cos(angle) * size.width * 0.50f * radius).toFloat()
                val y = cy + (sin(angle) * size.height * 0.50f * radius).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // ROSETTA
        24 -> GenericShape { size, _ ->
            val points = 20
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val radius = if (i % 2 == 0) 1f else 0.76f
                val x = cx + (cos(angle) * size.width * 0.50f * radius).toFloat()
                val y = cy + (sin(angle) * size.height * 0.50f * radius).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // BOLLO
        25 -> GenericShape { size, _ ->
            val points = 40
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val radius = if (i % 2 == 0) 1f else 0.94f
                val x = cx + (cos(angle) * size.width * 0.50f * radius).toFloat()
                val y = cy + (sin(angle) * size.height * 0.50f * radius).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // BURST SOFT
        26 -> GenericShape { size, _ ->
            val points = 28
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val radius = if (i % 2 == 0) 1f else 0.78f
                val x = cx + (cos(angle) * size.width * 0.50f * radius).toFloat()
                val y = cy + (sin(angle) * size.height * 0.50f * radius).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // BURST HARD
        27 -> GenericShape { size, _ ->
            val points = 36
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val radius = if (i % 2 == 0) 1f else 0.48f
                val x = cx + (cos(angle) * size.width * 0.50f * radius).toFloat()
                val y = cy + (sin(angle) * size.height * 0.50f * radius).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // CORONA
        28 -> GenericShape { size, _ ->
            moveTo(0f, size.height * 0.82f)
            lineTo(size.width * 0.08f, size.height * 0.24f)
            lineTo(size.width * 0.28f, size.height * 0.54f)
            lineTo(size.width * 0.50f, 0f)
            lineTo(size.width * 0.72f, size.height * 0.54f)
            lineTo(size.width * 0.92f, size.height * 0.24f)
            lineTo(size.width, size.height * 0.82f)
            lineTo(size.width * 0.92f, size.height)
            lineTo(size.width * 0.08f, size.height)
            close()
        }

        // FUMETTO
        29 -> GenericShape { size, _ ->
            moveTo(size.width * 0.08f, 0f)
            lineTo(size.width * 0.92f, 0f)
            lineTo(size.width, size.height * 0.12f)
            lineTo(size.width, size.height * 0.72f)
            lineTo(size.width * 0.88f, size.height * 0.84f)
            lineTo(size.width * 0.62f, size.height * 0.84f)
            lineTo(size.width * 0.46f, size.height)
            lineTo(size.width * 0.50f, size.height * 0.84f)
            lineTo(size.width * 0.08f, size.height * 0.84f)
            lineTo(0f, size.height * 0.72f)
            lineTo(0f, size.height * 0.12f)
            close()
        }

        // NUVOLA
        30 -> GenericShape { size, _ ->
            moveTo(size.width * 0.10f, size.height * 0.25f)
            lineTo(size.width * 0.22f, size.height * 0.08f)
            lineTo(size.width * 0.38f, size.height * 0.16f)
            lineTo(size.width * 0.50f, 0f)
            lineTo(size.width * 0.64f, size.height * 0.16f)
            lineTo(size.width * 0.80f, size.height * 0.08f)
            lineTo(size.width * 0.92f, size.height * 0.27f)
            lineTo(size.width, size.height * 0.48f)
            lineTo(size.width * 0.91f, size.height * 0.70f)
            lineTo(size.width * 0.76f, size.height * 0.92f)
            lineTo(size.width * 0.57f, size.height * 0.84f)
            lineTo(size.width * 0.42f, size.height)
            lineTo(size.width * 0.27f, size.height * 0.85f)
            lineTo(size.width * 0.10f, size.height * 0.92f)
            lineTo(0f, size.height * 0.68f)
            lineTo(size.width * 0.06f, size.height * 0.48f)
            close()
        }

        // SPLASH
        31 -> GenericShape { size, _ ->
            moveTo(size.width * 0.47f, 0f)
            lineTo(size.width * 0.56f, size.height * 0.26f)
            lineTo(size.width * 0.78f, size.height * 0.08f)
            lineTo(size.width * 0.72f, size.height * 0.34f)
            lineTo(size.width, size.height * 0.28f)
            lineTo(size.width * 0.79f, size.height * 0.49f)
            lineTo(size.width * 0.98f, size.height * 0.70f)
            lineTo(size.width * 0.70f, size.height * 0.66f)
            lineTo(size.width * 0.76f, size.height)
            lineTo(size.width * 0.54f, size.height * 0.76f)
            lineTo(size.width * 0.37f, size.height * 0.96f)
            lineTo(size.width * 0.34f, size.height * 0.70f)
            lineTo(size.width * 0.05f, size.height * 0.82f)
            lineTo(size.width * 0.23f, size.height * 0.55f)
            lineTo(0f, size.height * 0.39f)
            lineTo(size.width * 0.29f, size.height * 0.34f)
            close()
        }

        // NASTRO
        32 -> GenericShape { size, _ ->
            moveTo(0f, size.height * 0.12f)
            lineTo(size.width * 0.16f, size.height * 0.22f)
            lineTo(size.width * 0.84f, size.height * 0.22f)
            lineTo(size.width, size.height * 0.12f)
            lineTo(size.width * 0.92f, size.height * 0.50f)
            lineTo(size.width, size.height * 0.88f)
            lineTo(size.width * 0.84f, size.height * 0.78f)
            lineTo(size.width * 0.16f, size.height * 0.78f)
            lineTo(0f, size.height * 0.88f)
            lineTo(size.width * 0.08f, size.height * 0.50f)
            close()
        }

        // CARTELLINO
        33 -> GenericShape { size, _ ->
            moveTo(size.width * 0.18f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(size.width * 0.18f, size.height)
            lineTo(0f, size.height * 0.50f)
            close()
        }

        // DIAMANTE
        34 -> GenericShape { size, _ ->
            moveTo(size.width * 0.22f, 0f)
            lineTo(size.width * 0.78f, 0f)
            lineTo(size.width, size.height * 0.32f)
            lineTo(size.width * 0.50f, size.height)
            lineTo(0f, size.height * 0.32f)
            close()
        }

        // SCUDO MAX
        35 -> GenericShape { size, _ ->
            moveTo(size.width * 0.08f, 0f)
            lineTo(size.width * 0.92f, 0f)
            lineTo(size.width, size.height * 0.18f)
            lineTo(size.width * 0.88f, size.height * 0.70f)
            lineTo(size.width * 0.50f, size.height)
            lineTo(size.width * 0.12f, size.height * 0.70f)
            lineTo(0f, size.height * 0.18f)
            close()
        }

        // FRECCIA TURBO
        36 -> GenericShape { size, _ ->
            moveTo(0f, size.height * 0.18f)
            lineTo(size.width * 0.58f, size.height * 0.18f)
            lineTo(size.width * 0.58f, 0f)
            lineTo(size.width, size.height * 0.50f)
            lineTo(size.width * 0.58f, size.height)
            lineTo(size.width * 0.58f, size.height * 0.82f)
            lineTo(0f, size.height * 0.82f)
            lineTo(size.width * 0.12f, size.height * 0.50f)
            close()
        }

        // VENTAGLIO
        37 -> GenericShape { size, _ ->
            moveTo(size.width * 0.50f, size.height)
            lineTo(0f, size.height * 0.62f)
            lineTo(size.width * 0.08f, size.height * 0.26f)
            lineTo(size.width * 0.28f, size.height * 0.08f)
            lineTo(size.width * 0.50f, 0f)
            lineTo(size.width * 0.72f, size.height * 0.08f)
            lineTo(size.width * 0.92f, size.height * 0.26f)
            lineTo(size.width, size.height * 0.62f)
            close()
        }

        // TARGA
        38 -> GenericShape { size, _ ->
            moveTo(size.width * 0.12f, 0f)
            lineTo(size.width * 0.88f, 0f)
            lineTo(size.width, size.height * 0.28f)
            lineTo(size.width * 0.92f, size.height)
            lineTo(size.width * 0.08f, size.height)
            lineTo(0f, size.height * 0.28f)
            close()
        }

        // URLO
        39 -> GenericShape { size, _ ->
            val points = 30
            val cx = size.width / 2f
            val cy = size.height / 2f

            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val radius = when (i % 6) {
                    0 -> 1.00f
                    2 -> 0.88f
                    4 -> 0.72f
                    else -> 0.54f
                }

                val x = cx + (cos(angle) * size.width * 0.50f * radius).toFloat()
                val y = cy + (sin(angle) * size.height * 0.50f * radius).toFloat()

                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // CUORE
        40 -> GenericShape { size, _ ->
            moveTo(size.width * 0.50f, size.height)
            lineTo(size.width * 0.08f, size.height * 0.58f)
            lineTo(0f, size.height * 0.30f)
            lineTo(size.width * 0.08f, size.height * 0.08f)
            lineTo(size.width * 0.28f, 0f)
            lineTo(size.width * 0.50f, size.height * 0.22f)
            lineTo(size.width * 0.72f, 0f)
            lineTo(size.width * 0.92f, size.height * 0.08f)
            lineTo(size.width, size.height * 0.30f)
            lineTo(size.width * 0.92f, size.height * 0.58f)
            close()
        }

        // FIORE
        41 -> GenericShape { size, _ ->
            val points = 24
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val radius = when (i % 4) {
                    0 -> 1.00f
                    2 -> 0.82f
                    else -> 0.68f
                }
                val x = cx + (cos(angle) * size.width * 0.50f * radius).toFloat()
                val y = cy + (sin(angle) * size.height * 0.50f * radius).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // OTTAGONO CUT
        42 -> GenericShape { size, _ ->
            moveTo(size.width * 0.18f, 0f)
            lineTo(size.width * 0.82f, 0f)
            lineTo(size.width, size.height * 0.24f)
            lineTo(size.width * 0.92f, size.height * 0.50f)
            lineTo(size.width, size.height * 0.76f)
            lineTo(size.width * 0.82f, size.height)
            lineTo(size.width * 0.18f, size.height)
            lineTo(0f, size.height * 0.76f)
            lineTo(size.width * 0.08f, size.height * 0.50f)
            lineTo(0f, size.height * 0.24f)
            close()
        }

        // ETICHETTA
        43 -> GenericShape { size, _ ->
            moveTo(0f, size.height * 0.18f)
            lineTo(size.width * 0.12f, 0f)
            lineTo(size.width * 0.86f, 0f)
            lineTo(size.width, size.height * 0.50f)
            lineTo(size.width * 0.86f, size.height)
            lineTo(size.width * 0.12f, size.height)
            lineTo(0f, size.height * 0.82f)
            close()
        }

        // SAETTA
        44 -> GenericShape { size, _ ->
            moveTo(size.width * 0.42f, 0f)
            lineTo(size.width * 0.92f, 0f)
            lineTo(size.width * 0.68f, size.height * 0.32f)
            lineTo(size.width, size.height * 0.32f)
            lineTo(size.width * 0.52f, size.height)
            lineTo(size.width * 0.60f, size.height * 0.56f)
            lineTo(size.width * 0.08f, size.height * 0.56f)
            lineTo(size.width * 0.34f, size.height * 0.28f)
            lineTo(0f, size.height * 0.28f)
            close()
        }

        // BANNER
        45 -> GenericShape { size, _ ->
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width * 0.90f, size.height * 0.50f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            lineTo(size.width * 0.10f, size.height * 0.50f)
            close()
        }

        // GEMMA
        46 -> GenericShape { size, _ ->
            moveTo(size.width * 0.28f, 0f)
            lineTo(size.width * 0.72f, 0f)
            lineTo(size.width, size.height * 0.36f)
            lineTo(size.width * 0.76f, size.height)
            lineTo(size.width * 0.24f, size.height)
            lineTo(0f, size.height * 0.36f)
            close()
        }

        // SOLE
        47 -> GenericShape { size, _ ->
            val points = 32
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val radius = if (i % 2 == 0) 1.00f else 0.72f
                val x = cx + (cos(angle) * size.width * 0.50f * radius).toFloat()
                val y = cy + (sin(angle) * size.height * 0.50f * radius).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // BOOM
        48 -> GenericShape { size, _ ->
            val points = 22
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (i in 0 until points) {
                val angle = -PI / 2.0 + (2.0 * PI * i / points)
                val radius = when (i % 4) {
                    0 -> 1.00f
                    2 -> 0.82f
                    else -> 0.46f
                }
                val x = cx + (cos(angle) * size.width * 0.50f * radius).toFloat()
                val y = cy + (sin(angle) * size.height * 0.50f * radius).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // MEGAFONO
        else -> GenericShape { size, _ ->
            moveTo(0f, size.height * 0.30f)
            lineTo(size.width * 0.24f, size.height * 0.30f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(size.width * 0.24f, size.height * 0.70f)
            lineTo(0f, size.height * 0.70f)
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
private fun AutoFitPromoText(
    text: String,
    modifier: Modifier = Modifier,
    maxFontSize: Float,
    minFontSize: Float = 8f,
    fontWeight: FontWeight = FontWeight.Black,
    fontFamily: FontFamily? = null,
    color: Color = Color.Black,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1
) {
    var fontSize by remember(text, maxFontSize) { mutableStateOf(maxFontSize) }
    var readyToDraw by remember(text, maxFontSize) { mutableStateOf(false) }

    Text(
        text = text,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        color = color,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.05f).sp,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize > minFontSize) {
                fontSize = (fontSize - 1f).coerceAtLeast(minFontSize)
            } else {
                readyToDraw = true
            }
        }
    )
}

@Composable
private fun PromoExplosionBadge(
    titleScale: Float,
    explosionFont: Float,
    shapeIndex: Int,
    rotation: Float,
    shadow: Float,
    proportion: Float,
    shapeTouchScale: Float,
    internalScale: Float,
    internalRotation: Float
) {
    Box(
        modifier = Modifier.size(width = 250.dp, height = 88.dp),
        contentAlignment = Alignment.Center
    ) {
        if (shadow > 0f && shapeIndex == 8) {
            Canvas(
                modifier = Modifier
                    .size(width = 250.dp * proportion, height = 88.dp / proportion)
                    .graphicsLayer {
                        scaleX = shapeTouchScale
                        scaleY = shapeTouchScale
                    }
                    .offset(
                        x = (shadow / 3f).dp,
                        y = (shadow / 3f).dp
                    )
                    .rotate(-3f + rotation)
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
                    val angle = -PI / 2.0 + (2.0 * PI * i / points)
                    val useOuter = i % 2 == 0
                    val radiusX = if (useOuter) outerRadiusX else innerRadiusX
                    val radiusY = if (useOuter) outerRadiusY else innerRadiusY
                    val x = cx + cos(angle).toFloat() * radiusX
                    val y = cy + sin(angle).toFloat() * radiusY

                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                path.close()
                drawPath(
                    path = path,
                    color = Color.Black.copy(alpha = 0.65f)
                )
            }
        }

        if (shadow > 0f && shapeIndex != 8) {
            Box(
                modifier = Modifier
                    .size(width = 250.dp * proportion, height = 88.dp / proportion)
                    .graphicsLayer {
                        scaleX = shapeTouchScale
                        scaleY = shapeTouchScale
                    }
                    .offset(
                        x = (shadow / 3f).dp,
                        y = (shadow / 3f).dp
                    )
                    .rotate(-3f + rotation)
                    .background(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = promoPriceShape(shapeIndex)
                    )
            )
        }

    Box(
        modifier = Modifier
            .size(
                width = 250.dp * proportion,
                height = 88.dp / proportion
            )
                    .graphicsLayer {
                        scaleX = shapeTouchScale
                        scaleY = shapeTouchScale
                    }
            .rotate(-3f + rotation),
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

    }
        Column(
            modifier = Modifier.graphicsLayer {
                scaleX = internalScale
                scaleY = internalScale
                rotationZ = internalRotation
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AutoFitPromoText(
                text = "OFFERTA",
                modifier = Modifier.fillMaxWidth(0.72f),
                maxFontSize = 15f * titleScale,
                minFontSize = 8f,
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontFamily = promoFontFamily(explosionFont)
            )

            AutoFitPromoText(
                text = "BOMBA",
                modifier = Modifier.fillMaxWidth(0.72f),
                maxFontSize = 31f * titleScale,
                minFontSize = 12f,
                color = Color(0xFFE30613),
                fontWeight = FontWeight.Black,
                fontFamily = promoFontFamily(explosionFont)
            )
        }
    }
}
@Composable
private fun PromoDiscountBurst(
    discountPercent: Double,
    shapeIndex: Int,
    borderWidth: Float,
    rotation: Float,
    shadow: Float,
    proportion: Float
) {
    val discountShape = promoPriceShape(shapeIndex)

    Box(
        modifier = Modifier.size(
            width = (92.dp * proportion) + shadow.dp,
            height = (70.dp / proportion) + shadow.dp
        )
    ) {
        if (shadow > 0f) {
            Box(
                modifier = Modifier
                    .size(width = 92.dp * proportion, height = 70.dp / proportion)
                    .offset(x = shadow.dp, y = shadow.dp)
                    .rotate(rotation)
                    .background(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = discountShape
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(width = 92.dp * proportion, height = 70.dp / proportion)
                .rotate(rotation)
                .background(
                    color = Color(0xFFFFE000),
                    shape = discountShape
                )
                .border(
                    width = borderWidth.dp,
                    color = Color.Black,
                    shape = discountShape
                ),
            contentAlignment = Alignment.Center
        ) {
            AutoFitPromoText(
                text = String.format(
                    java.util.Locale.ITALY,
                    "-%.0f%%",
                    discountPercent
                ),
                modifier = Modifier.fillMaxWidth(0.72f),
                maxFontSize = 22f,
                minFontSize = 10f,
                color = Color(0xFFE30613),
                fontWeight = FontWeight.Black
            )
        }
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
    var discountScale by remember { mutableStateOf(1.0f) }
    var footerScale by remember { mutableStateOf(1.0f) }
    var priceShape by remember { mutableStateOf(0f) }
    var discountShape by remember { mutableStateOf(8f) }
    var titleShape by remember { mutableStateOf(0f) }
    var imageShape by remember { mutableStateOf(0f) }
    var footerShape by remember { mutableStateOf(0f) }
    var priceShapeBorder by remember { mutableStateOf(3f) }
    var discountShapeBorder by remember { mutableStateOf(3f) }
    var titleShapeBorder by remember { mutableStateOf(2f) }
    var imageShapeBorder by remember { mutableStateOf(2f) }
    var footerShapeBorder by remember { mutableStateOf(2f) }
    var priceShapeRotation by remember { mutableStateOf(-1.5f) }
    var discountShapeRotation by remember { mutableStateOf(4f) }
    var titleShapeRotation by remember { mutableStateOf(0f) }
    var imageShapeRotation by remember { mutableStateOf(0f) }
    var footerShapeRotation by remember { mutableStateOf(-1f) }
    var priceShapeShadow by remember { mutableStateOf(6f) }
    var priceShapeMeasuredSize by remember { mutableStateOf(IntSize.Zero) }
    var discountShapeShadow by remember { mutableStateOf(0f) }
    var titleShapeShadow by remember { mutableStateOf(0f) }
    var titleShapeMeasuredSize by remember { mutableStateOf(IntSize.Zero) }
    var imageShapeShadow by remember { mutableStateOf(0f) }
    var footerShapeShadow by remember { mutableStateOf(0f) }
    var priceShapeProportion by remember { mutableStateOf(1f) }
    var discountShapeProportion by remember { mutableStateOf(1f) }
    var titleShapeProportion by remember { mutableStateOf(1f) }
    var titleTouchScale by remember { mutableStateOf(1f) }
    var titleTouchRotation by remember { mutableStateOf(0f) }
    var titleTouchMode by remember { mutableStateOf(0) }
    var titleShapeTouchScale by remember { mutableStateOf(1f) }
    var titleInternalScale by remember { mutableStateOf(1f) }
    var titleInternalRotation by remember { mutableStateOf(0f) }
    var imageShapeProportion by remember { mutableStateOf(1f) }
    var footerShapeProportion by remember { mutableStateOf(1f) }
    var footerShapeMeasuredSize by remember { mutableStateOf(IntSize.Zero) }
    var shapeControl by remember { mutableStateOf("PREZZO") }
    var shapeParameter by remember { mutableStateOf("FORMA") }
    var wowVariant by remember { mutableStateOf(0) }
    var wowEditMode by remember { mutableStateOf(false) }
    var previewZoom by remember { mutableStateOf(1f) }
    var previewPanX by remember { mutableStateOf(0f) }
    var previewPanY by remember { mutableStateOf(0f) }
    var wowTitleVariant by remember { mutableStateOf(0) }
    var wowImageVariant by remember { mutableStateOf(0) }
var imageTouchScale by remember { mutableStateOf(1f) }
var imageTouchRotation by remember { mutableStateOf(0f) }
var imageTouchMode by remember { mutableStateOf(0) }
var imageShapeTouchScale by remember { mutableStateOf(1f) }
var imageInternalScale by remember { mutableStateOf(1f) }
var imageInternalRotation by remember { mutableStateOf(0f) }
var priceTouchScale by remember { mutableStateOf(1f) }
var priceTouchRotation by remember { mutableStateOf(0f) }
var priceTouchMode by remember { mutableStateOf(0) }
var priceInternalScale by remember { mutableStateOf(1f) }
var priceInternalRotation by remember { mutableStateOf(0f) }
    var wowPriceVariant by remember { mutableStateOf(0) }
    var wowDiscountVariant by remember { mutableStateOf(0) }
    var discountTouchScale by remember { mutableStateOf(1f) }
    var wowFooterVariant by remember { mutableStateOf(0) }
    var footerTouchScale by remember { mutableStateOf(1f) }

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
                        .graphicsLayer {
                            scaleX = previewZoom
                            scaleY = previewZoom
                            translationX = previewPanX
                            translationY = previewPanY
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .pointerInput(wowEditMode) {
                            if (wowEditMode) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newZoom = (previewZoom * zoom).coerceIn(1f, 3f)
                                    previewZoom = newZoom
                                    if (newZoom <= 1f) {
                                        previewPanX = 0f
                                        previewPanY = 0f
                                    } else {
                                        previewPanX += pan.x
                                        previewPanY += pan.y
                                    }
                                }
                            }
                        }
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
                Box(
                    modifier = Modifier.combinedClickable(
                        onLongClick = {
                            if (wowEditMode) {
                                titleTouchMode = (titleTouchMode + 1) % 3
                            }
                        },
                        onClick = {
                        shapeControl = "TITOLO"
                        dimensionControl = "TITOLI"
                        styleSection = "DIMENSIONI"
                        if (wowEditMode) {
                        when (wowTitleVariant) {
                            0 -> {
                                titleScale = 1.05f
                                explosionFont = 0.10f
                                descriptionFont = 0.70f
                                titleShape = 8f
                                titleShapeProportion = 0.95f
                                titleShapeRotation = -3f
                                titleShapeShadow = 5f
                            }
                            1 -> {
                                titleScale = 0.98f
                                explosionFont = 0.70f
                                descriptionFont = 0.70f
                                titleShape = 23f
                                titleShapeProportion = 1.00f
                                titleShapeRotation = 0f
                                titleShapeShadow = 5f
                            }
                            2 -> {
                                titleScale = 1.00f
                                explosionFont = 0.80f
                                descriptionFont = 0.80f
                                titleShape = 1f
                                titleShapeProportion = 1.00f
                                titleShapeRotation = 0f
                                titleShapeShadow = 2f
                            }
                            3 -> {
                                titleScale = 1.10f
                                explosionFont = 0.10f
                                descriptionFont = 0.60f
                                titleShape = 49f
                                titleShapeProportion = 0.85f
                                titleShapeRotation = -5f
                                titleShapeShadow = 8f
                            }
                            else -> {
                                val v = wowTitleVariant - 3
                                val wowGeneralShapes = (0..49)
                                    .filter { it != 19 && it != 44 }
                                    .map { it.toFloat() }

                                titleScale = 1.10f + ((v % 5) - 2) * 0.025f
                                explosionFont = ((v * 3) % 10) / 10f
                                descriptionFont = ((6 + v * 2) % 10) / 10f
                                titleShape = wowGeneralShapes[(v * 11) % wowGeneralShapes.size]
                                titleShapeProportion = 0.85f + (v % 6) * 0.05f
                                titleShapeRotation = (-5 + (v * 3 % 11)).toFloat()
                                titleShapeShadow = (8 - (v % 5)).toFloat()
                            }
                        }
                        wowTitleVariant = (wowTitleVariant + 1) % 50
                        }
                        }
                    )
                    .pointerInput(wowEditMode, titleTouchMode) {
                        if (wowEditMode) {
                            detectTransformGestures { _, _, zoom, rotation ->
                                if (titleTouchMode == 2) {
                                    titleInternalScale = (titleInternalScale * zoom).coerceIn(0.6f, 1.30f)
                                    titleInternalRotation += rotation
                                } else if (titleTouchMode == 1) {
                                    titleShapeTouchScale = (titleShapeTouchScale * zoom).coerceIn(0.6f, 1.30f)
                                    titleShapeRotation += rotation
                                } else {
                                    titleTouchScale = (titleTouchScale * zoom).coerceIn(0.6f, 1.6f)
                                    titleTouchRotation += rotation
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = titleTouchScale
                        scaleY = titleTouchScale
                        rotationZ = titleTouchRotation
                    }
                ) {
                if (isBlackPreset) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (titleShapeShadow > 0f && titleShapeMeasuredSize != IntSize.Zero) {
                            val density = LocalDensity.current
                            val shadowWidth = with(density) { titleShapeMeasuredSize.width.toDp() }
                            val shadowHeight = with(density) { titleShapeMeasuredSize.height.toDp() }

                            Box(
                                modifier = Modifier
                                    .size(shadowWidth, shadowHeight)
                                    .offset(
                                        x = (titleShapeShadow / 3f).dp,
                                        y = (titleShapeShadow / 3f).dp
                                    )
                                    .rotate(titleShapeRotation)
                                    .graphicsLayer {
                                        scaleX = titleShapeTouchScale
                                        scaleY = titleShapeTouchScale
                                    }
                                    .graphicsLayer { scaleX = titleShapeProportion; scaleY = 1f / titleShapeProportion }
                                    .background(
                                        color = Color.White.copy(alpha = 0.70f),
                                        shape = promoPriceShape(titleShape.toInt())
                                    )
                            )
                        }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { titleShapeMeasuredSize = it.size }
                            .padding(
                                horizontal = 8.dp,
                                vertical = 5.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    scaleX = titleShapeTouchScale
                                    scaleY = titleShapeTouchScale
                                }
                                .graphicsLayer { scaleX = titleShapeProportion; scaleY = 1f / titleShapeProportion }
                                .rotate(titleShapeRotation)
                                .background(
                                    color = shiftPromoHue(Color(0xFFFFD700), colorHue, colorIntensity),
                                    shape = promoPriceShape(titleShape.toInt())
                                )
                                .border(
                                    width = titleShapeBorder.dp,
                                    color = Color.White,
                                    shape = promoPriceShape(titleShape.toInt())
                                )
                        )
                        Column(
                            modifier = Modifier.graphicsLayer {
                                scaleX = titleInternalScale
                                scaleY = titleInternalScale
                                rotationZ = titleInternalRotation
                            },
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
                    }
                } else if (isLiberoPreset) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (titleShapeShadow > 0f && titleShapeMeasuredSize != IntSize.Zero) {
                            val density = LocalDensity.current
                            val shadowWidth = with(density) { titleShapeMeasuredSize.width.toDp() }
                            val shadowHeight = with(density) { titleShapeMeasuredSize.height.toDp() }

                            Box(
                                modifier = Modifier
                                    .size(shadowWidth, shadowHeight)
                                    .offset(
                                        x = (titleShapeShadow / 3f).dp,
                                        y = (titleShapeShadow / 3f).dp
                                    )
                                    .rotate(titleShapeRotation)
                                    .graphicsLayer {
                                        scaleX = titleShapeTouchScale
                                        scaleY = titleShapeTouchScale
                                    }
                                    .graphicsLayer { scaleX = titleShapeProportion; scaleY = 1f / titleShapeProportion }
                                    .background(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = promoPriceShape(titleShape.toInt())
                                    )
                            )
                        }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { titleShapeMeasuredSize = it.size }
                            .padding(
                                horizontal = 8.dp,
                                vertical = 6.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    scaleX = titleShapeTouchScale
                                    scaleY = titleShapeTouchScale
                                }
                                .graphicsLayer { scaleX = titleShapeProportion; scaleY = 1f / titleShapeProportion }
                                .rotate(titleShapeRotation)
                                .background(
                                    color = shiftPromoHue(Color(0xFFFF40C8), colorHue, colorIntensity),
                                    shape = promoPriceShape(titleShape.toInt())
                                )
                                .border(
                                    width = titleShapeBorder.dp,
                                    color = Color.White,
                                    shape = promoPriceShape(titleShape.toInt())
                                )
                        )
                        Column(
                            modifier = Modifier.graphicsLayer {
                                scaleX = titleInternalScale
                                scaleY = titleInternalScale
                                rotationZ = titleInternalRotation
                            },
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (titleShapeShadow > 0f && titleShapeMeasuredSize != IntSize.Zero) {
                            val density = LocalDensity.current
                            val shadowWidth = with(density) { titleShapeMeasuredSize.width.toDp() }
                            val shadowHeight = with(density) { titleShapeMeasuredSize.height.toDp() }

                            Box(
                                modifier = Modifier
                                    .size(shadowWidth, shadowHeight)
                                    .offset(
                                        x = (titleShapeShadow / 3f).dp,
                                        y = (titleShapeShadow / 3f).dp
                                    )
                                    .rotate(titleShapeRotation)
                                    .graphicsLayer {
                                        scaleX = titleShapeTouchScale
                                        scaleY = titleShapeTouchScale
                                    }
                                    .graphicsLayer { scaleX = titleShapeProportion; scaleY = 1f / titleShapeProportion }
                                    .background(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = promoPriceShape(titleShape.toInt())
                                    )
                            )
                        }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { titleShapeMeasuredSize = it.size }
                            .padding(
                                horizontal = 8.dp,
                                vertical = 7.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    scaleX = titleShapeTouchScale
                                    scaleY = titleShapeTouchScale
                                }
                                .graphicsLayer { scaleX = titleShapeProportion; scaleY = 1f / titleShapeProportion }
                                .rotate(titleShapeRotation)
                                .background(
                                    color = shiftPromoHue(Color(0xFFFFE000), colorHue, colorIntensity),
                                    shape = promoPriceShape(titleShape.toInt())
                                )
                                .border(
                                    width = titleShapeBorder.dp,
                                    color = Color.White,
                                    shape = promoPriceShape(titleShape.toInt())
                                )
                        )
                        Text(
                            text = "SUPER RISPARMIO",
                            modifier = Modifier.graphicsLayer {
                                scaleX = titleInternalScale
                                scaleY = titleInternalScale
                                rotationZ = titleInternalRotation
                            },
                            color = shiftPromoHue(Color(0xFF1B5E20), colorHue, colorIntensity),
                            fontSize = 27.sp * titleScale,
                            lineHeight = 29.sp * titleScale,
                            fontWeight = FontWeight.Black,
                            fontFamily = promoFontFamily(explosionFont),
                            textAlign = TextAlign.Center
                        )
                    }

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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (titleShapeShadow > 0f && titleShapeMeasuredSize != IntSize.Zero) {
                            val density = LocalDensity.current
                            val shadowWidth = with(density) { titleShapeMeasuredSize.width.toDp() }
                            val shadowHeight = with(density) { titleShapeMeasuredSize.height.toDp() }

                            Box(
                                modifier = Modifier
                                    .size(shadowWidth, shadowHeight)
                                    .offset(
                                        x = (titleShapeShadow / 3f).dp,
                                        y = (titleShapeShadow / 3f).dp
                                    )
                                    .rotate(titleShapeRotation)
                                    .graphicsLayer {
                                        scaleX = titleShapeTouchScale
                                        scaleY = titleShapeTouchScale
                                    }
                                    .graphicsLayer { scaleX = titleShapeProportion; scaleY = 1f / titleShapeProportion }
                                    .background(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = promoPriceShape(titleShape.toInt())
                                    )
                            )
                        }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { titleShapeMeasuredSize = it.size }
                            .padding(
                                horizontal = 8.dp,
                                vertical = 6.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    scaleX = titleShapeTouchScale
                                    scaleY = titleShapeTouchScale
                                }
                                .graphicsLayer { scaleX = titleShapeProportion; scaleY = 1f / titleShapeProportion }
                                .rotate(titleShapeRotation)
                                .background(
                                    color = shiftPromoHue(Color(0xFF00E5FF), colorHue, colorIntensity),
                                    shape = promoPriceShape(titleShape.toInt())
                                )
                                .border(
                                    width = titleShapeBorder.dp,
                                    color = Color.White,
                                    shape = promoPriceShape(titleShape.toInt())
                                )
                        )
                        Column(
                            modifier = Modifier.graphicsLayer {
                                scaleX = titleInternalScale
                                scaleY = titleInternalScale
                                rotationZ = titleInternalRotation
                            },
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
                    PromoExplosionBadge(
                        titleScale, explosionFont, titleShape.toInt(),
                        titleShapeRotation, titleShapeShadow, titleShapeProportion,
                        titleShapeTouchScale, titleInternalScale, titleInternalRotation
                    )
                }

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
                                .size(
                                    width = 142.dp,
                                    height = 158.dp,
                                )
                                .combinedClickable(
                                    onLongClick = {
                                        if (wowEditMode) imageTouchMode = (imageTouchMode + 1) % 3
                                    },
                                    onClick = {
                                        shapeControl = "FOTO"
                                        dimensionControl = "IMMAGINE"
                                        styleSection = "DIMENSIONI"
                                        if (wowEditMode) {
                                    when (wowImageVariant) {
                                        0 -> {
                                            imageScale = 0.95f
                                            imageShape = 7f
                                            imageShapeProportion = 1.00f
                                            imageShapeRotation = 2f
                                            imageShapeShadow = 3f
                                        }
                                        1 -> {
                                            imageScale = 1.18f
                                            imageShape = 34f
                                            imageShapeProportion = 0.90f
                                            imageShapeRotation = -2f
                                            imageShapeShadow = 7f
                                        }
                                        2 -> {
                                            imageScale = 1.00f
                                            imageShape = 2f
                                            imageShapeProportion = 1.00f
                                            imageShapeRotation = 0f
                                            imageShapeShadow = 2f
                                        }
                                        3 -> {
                                            imageScale = 1.02f
                                            imageShape = 31f
                                            imageShapeProportion = 1.08f
                                            imageShapeRotation = 3f
                                            imageShapeShadow = 6f
                                        }
                                        else -> {
                                            val v = wowImageVariant - 3
                                            val wowImageShapes = listOf(
                                                0f, 1f, 2f, 7f, 8f, 10f, 11f, 12f, 23f,
                                                24f, 25f, 26f, 27f, 30f, 34f, 35f, 47f, 48f
                                            )
                                            imageScale = 1.02f + ((v * 3 % 7) - 3) * 0.025f
                                            imageShape = wowImageShapes[(v * 7) % wowImageShapes.size]
                                            imageShapeProportion = 1.08f + ((v % 7) - 3) * 0.04f
                                            imageShapeRotation = (3 - (v * 2 % 7)).toFloat()
                                            imageShapeShadow = (6 + (v % 4)).toFloat()
                                        }
                                    }
                                    wowImageVariant = (wowImageVariant + 1) % 50
                                        }
                                }
                                )
                                .pointerInput(wowEditMode) {
                                    if (wowEditMode) {
                                        detectTransformGestures { _, _, zoom, rotation ->
                                            if (imageTouchMode == 2) {
                                                imageInternalScale = (imageInternalScale * zoom).coerceIn(0.6f, 1.30f)
                                                imageInternalRotation += rotation
                                            } else if (imageTouchMode == 1) {
                                                imageShapeTouchScale = (imageShapeTouchScale * zoom).coerceIn(0.6f, 1.30f)
                                                imageShapeRotation += rotation
                                            } else {
                                                imageTouchScale = (imageTouchScale * zoom).coerceIn(0.6f, 1.6f)
                                                imageTouchRotation += rotation
                                            }
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = imageTouchScale
                                    scaleY = imageTouchScale
                                    rotationZ = imageTouchRotation
                                }
                        ) {
                            if (imageShapeShadow > 0f) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 142.dp * imageShapeProportion, height = 142.dp / imageShapeProportion)
                                        .align(Alignment.Center)
                                        .graphicsLayer {
                                            scaleX = imageShapeTouchScale
                                            scaleY = imageShapeTouchScale
                                        }
                                        .offset(
                                            x = (imageShapeShadow / 3f).dp,
                                            y = (imageShapeShadow / 3f).dp
                                        )
                                        .rotate(imageShapeRotation)
                                        .background(
                                            color = Color.Black.copy(alpha = 0.65f),
                                            shape = promoPriceShape(imageShape.toInt())
                                        )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(width = 142.dp * imageShapeProportion, height = 142.dp / imageShapeProportion)
                                    .align(Alignment.Center)
                                    .graphicsLayer {
                                        scaleX = imageShapeTouchScale
                                        scaleY = imageShapeTouchScale
                                    }
                                    .rotate(imageShapeRotation)
                                    .background(
                                        color = Color.White,
                                        shape = promoPriceShape(imageShape.toInt())
                                    )
                            )

                            Box(
                                modifier = Modifier
                                    .size(width = 142.dp * imageShapeProportion, height = 142.dp / imageShapeProportion)
                                    .align(Alignment.Center)
                                    .graphicsLayer {
                                        scaleX = imageShapeTouchScale
                                        scaleY = imageShapeTouchScale
                                    }
                                    .zIndex(2f)
                                    .rotate(imageShapeRotation)
                                    .background(
                                        color = Color.Transparent,
                                        shape = promoPriceShape(imageShape.toInt())
                                    )
                                    .border(
                                        width = imageShapeBorder.dp,
                                        color = Color.Black,
                                        shape = promoPriceShape(imageShape.toInt())
                                    )
                                    .padding(2.dp)
                            )

                            AndroidView(
                                modifier = Modifier
                                    .size(142.dp)
                                    .align(Alignment.Center)
                                    .graphicsLayer {
                                        scaleX = 1.15f * imageScale * imageInternalScale
                                        scaleY = 1.15f * imageScale * imageInternalScale
                                        rotationZ = imageInternalRotation
                                    },
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
                                        .graphicsLayer {
                                            scaleX = discountTouchScale * discountScale
                                            scaleY = discountTouchScale * discountScale
                                        }
                                        .clickable {
                                            shapeControl = "SCONTO"
                                            dimensionControl = "SCONTO"
                                            styleSection = "DIMENSIONI"
                                            if (wowEditMode) {
                                            val wowShapes = (0..49)
                                                .filter { it != 19 && it != 44 }
                                            discountShape = wowShapes[(wowDiscountVariant * 13 + 11) % wowShapes.size].toFloat()
                                            discountShapeProportion = 0.85f + (wowDiscountVariant % 7) * 0.05f
                                            discountShapeRotation = ((wowDiscountVariant * 5 % 15) - 7).toFloat()
                                            discountShapeShadow = (3 + (wowDiscountVariant % 6)).toFloat()
                                            wowDiscountVariant = (wowDiscountVariant + 1) % 50
                                            }
                                        }
                                        .pointerInput(wowEditMode) {
                                            if (wowEditMode) {
                                                detectTransformGestures { _, _, zoom, rotation ->
                                                    discountTouchScale = (discountTouchScale * zoom).coerceIn(0.60f, 2.50f)
                                                    discountShapeRotation += rotation
                                                }
                                            }
                                        }
                                ) {
                                    PromoDiscountBurst(
                                        discountPercent = effectiveDiscount,
                                        shapeIndex = discountShape.toInt(),
                                        borderWidth = discountShapeBorder,
                                        rotation = discountShapeRotation,
                                        shadow = discountShapeShadow,
                                        proportion = discountShapeProportion
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
                                .graphicsLayer {
                                    scaleX = priceTouchScale
                                    scaleY = priceTouchScale
                                    rotationZ = priceTouchRotation
                                }
                        ) {
                            if (priceShapeShadow > 0f && priceShapeMeasuredSize != IntSize.Zero) {
                                val density = LocalDensity.current
                                val shadowWidth = with(density) { priceShapeMeasuredSize.width.toDp() }
                                val shadowHeight = with(density) { priceShapeMeasuredSize.height.toDp() }

                                Box(
                                    modifier = Modifier
                                        .size(shadowWidth, shadowHeight)
                                        .offset(
                                            x = priceShapeShadow.dp,
                                            y = priceShapeShadow.dp
                                        )
                                        .rotate(priceShapeRotation)
                                        .graphicsLayer { scaleX = priceShapeProportion; scaleY = 1f / priceShapeProportion }
                                        .background(
                                            color = Color.Black.copy(alpha = 0.65f),
                                            shape = promoPriceShape(priceShape.toInt())
                                        )
                                )
                            }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onLongClick = {
                                        if (wowEditMode) priceTouchMode = (priceTouchMode + 1) % 3
                                    },
                                    onClick = {
                                    shapeControl = "PREZZO"
                                    dimensionControl = "PREZZO"
                                    styleSection = "DIMENSIONI"
                                    if (wowEditMode) {
                                    when (wowPriceVariant) {
                                        0 -> {
                                            priceScale = 1.22f
                                            priceFont = 0.10f
                                            priceShape = 5f
                                            priceShapeProportion = 1.18f
                                            priceShapeRotation = -4f
                                            priceShapeShadow = 8f
                                        }
                                        1 -> {
                                            priceScale = 1.08f
                                            priceFont = 0.10f
                                            priceShape = 38f
                                            priceShapeProportion = 1.05f
                                            priceShapeRotation = 2f
                                            priceShapeShadow = 7f
                                        }
                                        2 -> {
                                            priceScale = 1.05f
                                            priceFont = 0.80f
                                            priceShape = 13f
                                            priceShapeProportion = 1.00f
                                            priceShapeRotation = -1f
                                            priceShapeShadow = 4f
                                        }
                                        3 -> {
                                            priceScale = 1.28f
                                            priceFont = 0.10f
                                            priceShape = 38f
                                            priceShapeProportion = 1.30f
                                            priceShapeRotation = 5f
                                            priceShapeShadow = 12f
                                        }
                                        else -> {
                                            val v = wowPriceVariant - 3
                                            val wowGeneralShapes = (0..49)
                                                .filter { it != 19 && it != 44 }
                                                .map { it.toFloat() }

                                            priceScale = 1.28f + ((v * 5 % 7) - 3) * 0.025f
                                            priceFont = ((1 + v * 7) % 10) / 10f
                                            priceShape = wowGeneralShapes[(v * 17 + 7) % wowGeneralShapes.size]
                                            priceShapeProportion = 1.30f - (v % 8) * 0.05f
                                            priceShapeRotation = (5 - (v * 3 % 11)).toFloat()
                                            priceShapeShadow = (12 - (v % 6)).toFloat()
                                        }
                                    }
                                    wowPriceVariant = (wowPriceVariant + 1) % 50
                                    }
                                }
                                )
                                .pointerInput(wowEditMode) {
                                    if (wowEditMode) {
                                        detectTransformGestures { _, _, zoom, rotation ->
                                            if (priceTouchMode == 2) {
                                                priceInternalScale = (priceInternalScale * zoom).coerceIn(0.60f, 2.50f)
                                                priceInternalRotation += rotation
                                            } else if (priceTouchMode == 1) {
                                                priceShapeProportion = (priceShapeProportion * zoom).coerceIn(0.60f, 1.40f)
                                                priceShapeRotation += rotation
                                            } else {
                                                priceTouchScale = (priceTouchScale * zoom).coerceIn(0.60f, 1.60f)
                                                priceTouchRotation += rotation
                                            }
                                        }
                                    }
                                }
                                .onGloballyPositioned { priceShapeMeasuredSize = it.size },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        scaleX = priceShapeProportion
                                        scaleY = 1f / priceShapeProportion
                                        rotationZ = priceShapeRotation
                                    }
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
                                        width = priceShapeBorder.dp,
                                        color = Color.Black,
                                        shape = promoPriceShape(priceShape.toInt())
                                    )
                            )
                            AutoFitPromoText(
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
                                modifier = Modifier
                                    .fillMaxWidth(if (priceShape.toInt() == 19) 0.52f else 0.90f)
                                    .padding(horizontal = 4.dp, vertical = 10.dp)
                                    .graphicsLayer {
                                        scaleX = priceInternalScale
                                        scaleY = priceInternalScale
                                        rotationZ = priceInternalRotation
                                    },
                                maxFontSize = 22f * priceScale,
                                minFontSize = 10f,
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
                                fontWeight = FontWeight.Black,
                                fontFamily = promoFontFamily(priceFont),
                                textAlign = TextAlign.Center
                            )
                        }
                        }


                    }
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (footerShapeShadow > 0f && footerShapeMeasuredSize != IntSize.Zero) {
                        val density = LocalDensity.current
                        Box(
                            modifier = Modifier
                                .size(
                                    width = with(density) { footerShapeMeasuredSize.width.toDp() },
                                    height = with(density) { footerShapeMeasuredSize.height.toDp() }
                                )
                                .offset(
                                    x = (footerShapeShadow / 3f).dp,
                                    y = (footerShapeShadow / 3f).dp
                                )
                                .rotate(footerShapeRotation)
                                .graphicsLayer { scaleX = footerShapeProportion; scaleY = 1f / footerShapeProportion }
                                .background(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = promoPriceShape(footerShape.toInt())
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { footerShapeMeasuredSize = it.size }
                            .graphicsLayer {
                                scaleX = footerTouchScale * footerScale
                                scaleY = footerTouchScale * footerScale
                            }
                            .clickable {
                                shapeControl = "FASCIA"
                                dimensionControl = "FASCIA"
                                styleSection = "DIMENSIONI"
                                if (wowEditMode) {
                                val wowShapes = (0..49)
                                    .filter { it != 19 && it != 44 }
                                footerShape = wowShapes[(wowFooterVariant * 23 + 17) % wowShapes.size].toFloat()
                                footerShapeProportion = 0.85f + (wowFooterVariant % 6) * 0.05f
                                footerShapeRotation = ((wowFooterVariant * 2 % 13) - 6).toFloat()
                                footerShapeShadow = (2 + (wowFooterVariant % 7)).toFloat()
                                wowFooterVariant = (wowFooterVariant + 1) % 50
                                }
                            }
                            .pointerInput(wowEditMode) {
                                if (wowEditMode) {
                                    detectTransformGestures { _, _, zoom, rotation ->
                                        footerTouchScale = (footerTouchScale * zoom).coerceIn(0.60f, 2.50f)
                                        footerShapeRotation += rotation
                                    }
                                }
                            }
                            .graphicsLayer { scaleX = footerShapeProportion; scaleY = 1f / footerShapeProportion }
                            .rotate(footerShapeRotation)
                            .background(
                                color = Color.Black,
                                shape = promoPriceShape(footerShape.toInt())
                            )
                            .border(
                                width = footerShapeBorder.dp,
                                color = shiftPromoHue(Color(0xFFFFE000), colorHue, colorIntensity),
                                shape = promoPriceShape(footerShape.toInt())
                            )
                            .padding(
                                horizontal = 10.dp,
                                vertical = 7.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AutoFitPromoText(
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
                            modifier = Modifier.fillMaxWidth(0.88f),
                            maxFontSize = 16f,
                            minFontSize = 7f,
                            color = shiftPromoHue(Color(0xFFFFE000), colorHue, colorIntensity),
                            fontWeight = FontWeight.Black,
                            fontFamily = promoFontFamily(footerFont),
                            textAlign = TextAlign.Center
                        )
                    }
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

            if (wowEditMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.82f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { previewZoom = (previewZoom - 0.25f).coerceAtLeast(1f) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("", fontWeight = FontWeight.Black)
                    }

                    Text(
                        text = "${(previewZoom * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = { previewZoom = (previewZoom + 0.25f).coerceAtMost(3f) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("+", fontWeight = FontWeight.Black)
                    }

                    Text(
                        text = if (shapeControl == "TITOLO") {
                            "WOW  TITOLO  [" + when (titleTouchMode) {
                                1 -> "FORMA"
                                2 -> "TESTO"
                                else -> "INSIEME"
                            } + "]"
                        } else if (shapeControl == "FOTO") {
                            "WOW  FOTO  [" + when (imageTouchMode) {
                                1 -> "FORMA"
                                2 -> "FOTO"
                                else -> "INSIEME"
                            } + "]"
                        } else if (shapeControl == "PREZZO") {
                            "WOW  PREZZO  [" + when (priceTouchMode) {
                                1 -> "FORMA"
                                2 -> "PREZZO"
                                else -> "INSIEME"
                            } + "]"
                        } else {
                            "WOW  $shapeControl"
                        },
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(6.dp))
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("DIMENSIONI", "FONT", "COLORI", "FORME").forEach { section ->
                val isSelected = styleSection == section
                Button(
                    onClick = { styleSection = section },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = section,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (styleSection == "DIMENSIONI") {
            listOf(
                listOf("GENERALE", "TITOLI", "IMMAGINE"),
                listOf("PREZZO", "SCONTO", "FASCIA")
            ).forEach { rowControls ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowControls.forEach { control ->
                        val isSelected = dimensionControl == control
                        Button(
                            onClick = { dimensionControl = control },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                text = if (control == "IMMAGINE") "FOTO" else control,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            val dimensionValue = when (dimensionControl) {
                "GENERALE" -> globalScale
                "TITOLI" -> titleScale
                "IMMAGINE" -> imageScale
                "PREZZO" -> priceScale
                "SCONTO" -> discountScale
                else -> footerScale
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
                        "SCONTO" -> discountScale = value
                        "FASCIA" -> footerScale = value
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
                "DIAGONALE",
                "ESAGONO",
                "OTTAGONO",
                "ROMBO",
                "BIGLIETTO",
                "COUPON",
                "BANDIERA",
                "CHEVRON",
                "FRECCIA DOPPIA",
                "PUNTA",
                "FULMINE",
                "STELLA 5",
                "STELLA 8",
                "STELLA 12",
                "SIGILLO",
                "ROSETTA",
                "BOLLO",
                "BURST SOFT",
                "BURST HARD",
                "CORONA",
                "FUMETTO",
                "NUVOLA",
                "SPLASH",
                "NASTRO",
                "CARTELLINO",
                "DIAMANTE",
                "SCUDO MAX",
                "FRECCIA TURBO",
                "VENTAGLIO",
                "TARGA",
                "URLO",
                "CUORE",
                "FIORE",
                "OTTAGONO CUT",
                "ETICHETTA",
                "SAETTA",
                "BANNER",
                "GEMMA",
                "SOLE",
                "BOOM",
                "MEGAFONO"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("FORMA", "BORDO", "INCLINAZIONE", "OMBRA", "PROPORZIONE").forEach { parameter ->
                    val isSelected = shapeParameter == parameter
                    Button(
                        onClick = { shapeParameter = parameter },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(text = parameter, fontSize = 7.sp, maxLines = 1, softWrap = false)
                    }
                }
            }

            val currentShape = when (shapeControl) {
                "SCONTO" -> discountShape
                "TITOLO" -> titleShape
                "FOTO" -> imageShape
                "FASCIA" -> footerShape
                else -> priceShape
            }

            val currentShapeBorder = when (shapeControl) {
                "SCONTO" -> discountShapeBorder
                "TITOLO" -> titleShapeBorder
                "FOTO" -> imageShapeBorder
                "FASCIA" -> footerShapeBorder
                else -> priceShapeBorder
            }

            val currentShapeRotation = when (shapeControl) {
                "SCONTO" -> discountShapeRotation
                "TITOLO" -> titleShapeRotation
                "FOTO" -> imageShapeRotation
                "FASCIA" -> footerShapeRotation
                else -> priceShapeRotation
            }

            val currentShapeShadow = when (shapeControl) {
                "SCONTO" -> discountShapeShadow
                "TITOLO" -> titleShapeShadow
                "FOTO" -> imageShapeShadow
                "FASCIA" -> footerShapeShadow
                else -> priceShapeShadow
            }

            val currentShapeProportion = when (shapeControl) {
                "SCONTO" -> discountShapeProportion
                "TITOLO" -> titleShapeProportion
                "FOTO" -> imageShapeProportion
                "FASCIA" -> footerShapeProportion
                else -> priceShapeProportion
            }

            if (shapeParameter == "FORMA") {
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
                            "FASCIA" -> footerShape = value
                            else -> priceShape = value
                        }
                    },
                    valueRange = 0f..shapeNames.lastIndex.toFloat(),
                    steps = shapeNames.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (shapeParameter == "BORDO") {
                Text("$shapeControl - BORDO: ${String.format("%.1f", currentShapeBorder)}")

                Slider(
                    value = currentShapeBorder,
                    onValueChange = { value ->
                        when (shapeControl) {
                            "SCONTO" -> discountShapeBorder = value
                            "TITOLO" -> titleShapeBorder = value
                            "FOTO" -> imageShapeBorder = value
                            "FASCIA" -> footerShapeBorder = value
                            else -> priceShapeBorder = value
                        }
                    },
                    valueRange = 0f..6f,
                    steps = 11,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (shapeParameter == "INCLINAZIONE") {
                Text("$shapeControl - INCLINAZIONE: ${String.format("%.1f", currentShapeRotation)}°")

                Slider(
                    value = currentShapeRotation,
                    onValueChange = { value ->
                        when (shapeControl) {
                            "SCONTO" -> discountShapeRotation = value
                            "TITOLO" -> titleShapeRotation = value
                            "FOTO" -> imageShapeRotation = value
                            "FASCIA" -> footerShapeRotation = value
                            else -> priceShapeRotation = value
                        }
                    },
                    valueRange = -15f..15f,
                    steps = 59,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (shapeParameter == "OMBRA") {
                Text("$shapeControl - OMBRA: ${String.format("%.1f", currentShapeShadow)}")

                Slider(
                    value = currentShapeShadow,
                    onValueChange = { value ->
                        when (shapeControl) {
                            "SCONTO" -> discountShapeShadow = value
                            "TITOLO" -> titleShapeShadow = value
                            "FOTO" -> imageShapeShadow = value
                            "FASCIA" -> footerShapeShadow = value
                            else -> priceShapeShadow = value
                        }
                    },
                    valueRange = 0f..16f,
                    steps = 31,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (shapeParameter == "PROPORZIONE") {
                Text("$shapeControl - PROPORZIONE: ${String.format("%.0f", currentShapeProportion * 100f)}%")
                Slider(
                    value = currentShapeProportion,
                    onValueChange = { value ->
                        when (shapeControl) {
                            "SCONTO" -> discountShapeProportion = value
                            "TITOLO" -> titleShapeProportion = value
                            "FOTO" -> imageShapeProportion = value
                            "FASCIA" -> footerShapeProportion = value
                            else -> priceShapeProportion = value
                        }
                    },
                    valueRange = when (shapeControl) {
                        "TITOLO" -> 0.75f..1.10f
                        "FASCIA" -> 0.80f..1.10f
                        else -> 0.6f..1.4f
                    },
                    steps = when (shapeControl) {
                        "TITOLO" -> 6
                        "FASCIA" -> 5
                        else -> 15
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (styleSection == "FONT") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("TITOLI", "DESCR.", "PREZZO", "SOTTO").forEach { control ->
                    val isSelected = fontControl == control
                    Button(
                        onClick = { fontControl = control },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = control,
                            fontSize = 9.sp
                        )
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
                    val isSelected = colorControl == control
                    Button(
                        onClick = { colorControl = control },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    val product = selectedProduct
                    if (product != null) {
                        val descriptionLength = product.description.trim().length
                        val publicPrice = product.publicPrice.replace(",", ".").toDoubleOrNull() ?: 0.0

                        globalScale = 1.0f
                        titleScale = when {
                            descriptionLength > 45 -> 0.95f
                            descriptionLength < 25 -> 1.10f
                            else -> 1.02f
                        }
                        imageScale = when {
                            descriptionLength > 45 -> 0.95f
                            descriptionLength < 25 -> 1.05f
                            else -> 1.00f
                        }
                        priceScale = when {
                            publicPrice < 10.0 -> 1.20f
                            publicPrice < 100.0 -> 1.15f
                            publicPrice < 1000.0 -> 1.08f
                            else -> 0.98f
                        }
                        titleShapeProportion = 1.00f
                        imageShapeProportion = 1.00f
                        priceShapeProportion = if (publicPrice < 100.0) 1.08f else 1.00f
                        discountShapeProportion = 1.00f
                        footerShapeProportion = 1.00f
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("AUTO")
            }

            Button(
                onClick = {
                    wowEditMode = !wowEditMode
                    if (false) {
                    when (wowVariant) {
                        0 -> {
                            titleScale = 1.05f
                            imageScale = 0.95f
                            priceScale = 1.22f
                            explosionFont = 0.10f
                            descriptionFont = 0.70f
                            priceFont = 0.10f
                            footerFont = 0.70f
                            titleShape = 8f
                            imageShape = 7f
                            priceShape = 5f
                            discountShape = 20f
                            footerShape = 15f
                            titleShapeProportion = 0.95f
                            imageShapeProportion = 1.00f
                            priceShapeProportion = 1.18f
                            discountShapeProportion = 0.90f
                            footerShapeProportion = 1.05f
                            titleShapeRotation = -3f
                            imageShapeRotation = 2f
                            priceShapeRotation = -4f
                            discountShapeRotation = 6f
                            footerShapeRotation = -2f
                            titleShapeShadow = 5f
                            imageShapeShadow = 3f
                            priceShapeShadow = 8f
                            discountShapeShadow = 5f
                            footerShapeShadow = 4f
                        }
                        1 -> {
                            titleScale = 0.98f
                            imageScale = 1.18f
                            priceScale = 1.08f
                            explosionFont = 0.70f
                            descriptionFont = 0.70f
                            priceFont = 0.10f
                            footerFont = 0.70f
                            titleShape = 23f
                            imageShape = 34f
                            priceShape = 38f
                            discountShape = 27f
                            footerShape = 32f
                            titleShapeProportion = 1.00f
                            imageShapeProportion = 0.90f
                            priceShapeProportion = 1.05f
                            discountShapeProportion = 1.00f
                            footerShapeProportion = 1.00f
                            titleShapeRotation = 0f
                            imageShapeRotation = -2f
                            priceShapeRotation = 2f
                            discountShapeRotation = -5f
                            footerShapeRotation = 1f
                            titleShapeShadow = 5f
                            imageShapeShadow = 7f
                            priceShapeShadow = 7f
                            discountShapeShadow = 5f
                            footerShapeShadow = 4f
                        }
                        2 -> {
                            titleScale = 1.00f
                            imageScale = 1.00f
                            priceScale = 1.05f
                            explosionFont = 0.80f
                            descriptionFont = 0.80f
                            priceFont = 0.80f
                            footerFont = 0.80f
                            titleShape = 1f
                            imageShape = 2f
                            priceShape = 13f
                            discountShape = 25f
                            footerShape = 12f
                            titleShapeProportion = 1.00f
                            imageShapeProportion = 1.00f
                            priceShapeProportion = 1.00f
                            discountShapeProportion = 0.95f
                            footerShapeProportion = 1.00f
                            titleShapeRotation = 0f
                            imageShapeRotation = 0f
                            priceShapeRotation = -1f
                            discountShapeRotation = 2f
                            footerShapeRotation = 0f
                            titleShapeShadow = 2f
                            imageShapeShadow = 2f
                            priceShapeShadow = 4f
                            discountShapeShadow = 3f
                            footerShapeShadow = 2f
                        }
                        3 -> {
                            titleScale = 1.10f
                            imageScale = 1.02f
                            priceScale = 1.28f
                            explosionFont = 0.10f
                            descriptionFont = 0.60f
                            priceFont = 0.10f
                            footerFont = 0.60f
                            titleShape = 49f
                            imageShape = 31f
                            priceShape = 38f
                            discountShape = 48f
                            footerShape = 32f
                            titleShapeProportion = 0.85f
                            imageShapeProportion = 1.08f
                            priceShapeProportion = 1.30f
                            discountShapeProportion = 0.85f
                            footerShapeProportion = 1.08f
                            titleShapeRotation = -5f
                            imageShapeRotation = 3f
                            priceShapeRotation = 5f
                            discountShapeRotation = -7f
                            footerShapeRotation = -3f
                            titleShapeShadow = 8f
                            imageShapeShadow = 6f
                            priceShapeShadow = 12f
                            discountShapeShadow = 8f
                            footerShapeShadow = 6f
                        }
                        else -> {
                            val v = wowVariant - 3

                            titleScale = 1.10f + ((v % 5) - 2) * 0.025f
                            imageScale = 1.02f + ((v * 3 % 7) - 3) * 0.025f
                            priceScale = 1.28f + ((v * 5 % 7) - 3) * 0.025f

                            explosionFont = ((v * 3) % 10) / 10f
                            descriptionFont = ((6 + v * 2) % 10) / 10f
                            priceFont = ((1 + v * 7) % 10) / 10f
                            footerFont = ((6 + v * 5) % 10) / 10f

                            val wowGeneralShapes = (0..49)
                                .filter { it != 19 && it != 44 }
                                .map { it.toFloat() }

                            titleShape = wowGeneralShapes[(v * 11) % wowGeneralShapes.size]
                            val wowImageShapes = listOf(
                                0f, 1f, 2f, 7f, 8f, 10f, 11f, 12f, 23f,
                                24f, 25f, 26f, 27f, 30f, 34f, 35f, 47f, 48f
                            )
                            imageShape = wowImageShapes[(v * 7) % wowImageShapes.size]
                            priceShape = wowGeneralShapes[(v * 17 + 7) % wowGeneralShapes.size]
                            discountShape = wowGeneralShapes[(v * 19 + 13) % wowGeneralShapes.size]
                            footerShape = wowGeneralShapes[(v * 23 + 17) % wowGeneralShapes.size]

                            titleShapeProportion = 0.85f + (v % 6) * 0.05f
                            imageShapeProportion = 1.08f + ((v % 7) - 3) * 0.04f
                            priceShapeProportion = 1.30f - (v % 8) * 0.05f
                            discountShapeProportion = 0.85f + (v % 8) * 0.05f
                            footerShapeProportion = 1.08f - (v % 6) * 0.05f

                            titleShapeRotation = (-5 + (v * 3 % 11)).toFloat()
                            imageShapeRotation = (3 - (v * 2 % 7)).toFloat()
                            priceShapeRotation = (5 - (v * 3 % 11)).toFloat()
                            discountShapeRotation = (-7 + (v * 5 % 15)).toFloat()
                            footerShapeRotation = (-3 + (v * 2 % 7)).toFloat()

                            titleShapeShadow = (8 - (v % 5)).toFloat()
                            imageShapeShadow = (6 + (v % 4)).toFloat()
                            priceShapeShadow = (12 - (v % 6)).toFloat()
                            discountShapeShadow = (8 - (v % 5)).toFloat()
                            footerShapeShadow = (6 + (v % 4)).toFloat()
                        }
                    }
                    wowVariant = (wowVariant + 1) % 50
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (wowEditMode) "WOW ATTIVO" else "WOW")
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
































