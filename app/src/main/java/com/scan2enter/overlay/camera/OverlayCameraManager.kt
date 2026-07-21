package com.scan2enter.overlay.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OverlayCameraManager(
    private val context: Context
) {

    enum class TorchMode {
        AUTO,
        ON,
        OFF
    }

    companion object {
        private const val TAG = "OverlayCameraManager"

        /*
         * Valori medi del piano Y (0-255).
         * Due soglie diverse evitano che la torcia continui ad accendersi
         * e spegnersi quando la luminosità è vicina al limite.
         */
        private const val DARK_THRESHOLD = 52.0
        private const val BRIGHT_THRESHOLD = 78.0

        private const val DARK_FRAMES_REQUIRED = 8
        private const val BRIGHT_FRAMES_REQUIRED = 18

        /*
         * Non analizziamo tutti i pixel: il campionamento mantiene bassissimo
         * il costo della misura e non rallenta ML Kit.
         */
        private const val LUMINANCE_SAMPLE_STEP = 16
    }

    private val providerFuture =
        ProcessCameraProvider.getInstance(context)

    private var cameraProvider: ProcessCameraProvider? = null
    private var boundCamera: Camera? = null

    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var torchMode = TorchMode.AUTO

    @Volatile
    private var torchEnabled = false

    private var darkFrameCount = 0
    private var brightFrameCount = 0

    private var torchStateListener: ((TorchMode, Boolean, Boolean) -> Unit)? =
        null

    fun start(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onBarcodeDetected: (String) -> Unit,
        onTorchStateChanged: (
            mode: TorchMode,
            enabled: Boolean,
            available: Boolean
        ) -> Unit = { _, _, _ -> }
    ) {
        torchStateListener = onTorchStateChanged

        providerFuture.addListener({

            cameraProvider = providerFuture.get()
            cameraProvider?.unbindAll()

            val preview = Preview.Builder()
                .build()

            preview.surfaceProvider =
                previewView.surfaceProvider

            val barcodeAnalyzer = OverlayBarcodeAnalyzer(
                onBarcodeDetected
            )

            val imageAnalysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

            imageAnalysis.setAnalyzer(
                cameraExecutor,
                BrightnessAwareBarcodeAnalyzer(
                    barcodeAnalyzer = barcodeAnalyzer,
                    onBrightnessMeasured = ::handleBrightness
                )
            )

            boundCamera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

            darkFrameCount = 0
            brightFrameCount = 0
            applyTorchMode()

        }, ContextCompat.getMainExecutor(context))
    }

    fun cycleTorchMode(): TorchMode {
        torchMode = when (torchMode) {
            TorchMode.AUTO -> TorchMode.ON
            TorchMode.ON -> TorchMode.OFF
            TorchMode.OFF -> TorchMode.AUTO
        }

        darkFrameCount = 0
        brightFrameCount = 0
        applyTorchMode()

        return torchMode
    }

    fun getTorchMode(): TorchMode = torchMode

    fun stop() {
        setTorchEnabled(false)
        cameraProvider?.unbindAll()
        boundCamera = null
        darkFrameCount = 0
        brightFrameCount = 0
    }

    fun release() {
        stop()
        torchStateListener = null
        cameraExecutor.shutdown()
    }

    private fun handleBrightness(luminance: Double) {
        if (torchMode != TorchMode.AUTO) {
            return
        }

        if (luminance < DARK_THRESHOLD) {
            darkFrameCount++
            brightFrameCount = 0

            if (
                darkFrameCount >= DARK_FRAMES_REQUIRED &&
                !torchEnabled
            ) {
                setTorchEnabled(true)
            }
        } else if (luminance > BRIGHT_THRESHOLD) {
            brightFrameCount++
            darkFrameCount = 0

            if (
                brightFrameCount >= BRIGHT_FRAMES_REQUIRED &&
                torchEnabled
            ) {
                setTorchEnabled(false)
            }
        } else {
            /*
             * Zona neutra: manteniamo lo stato corrente e riduciamo
             * lentamente i contatori per evitare cambi repentini.
             */
            darkFrameCount = (darkFrameCount - 1).coerceAtLeast(0)
            brightFrameCount = (brightFrameCount - 1).coerceAtLeast(0)
        }
    }

    private fun applyTorchMode() {
        when (torchMode) {
            TorchMode.AUTO -> {
                /*
                 * In AUTO partiamo spenti e lasciamo decidere ai frame.
                 */
                setTorchEnabled(false)
            }

            TorchMode.ON -> {
                setTorchEnabled(true)
            }

            TorchMode.OFF -> {
                setTorchEnabled(false)
            }
        }
    }

    private fun setTorchEnabled(enabled: Boolean) {
        val camera = boundCamera
        val available = camera?.cameraInfo?.hasFlashUnit() == true

        if (!available) {
            torchEnabled = false
            notifyTorchState(available = false)
            return
        }

        if (torchEnabled == enabled) {
            notifyTorchState(available = true)
            return
        }

        mainHandler.post {
            camera.cameraControl
                .enableTorch(enabled)
                .addListener(
                    {
                        torchEnabled = enabled
                        notifyTorchState(available = true)

                        Log.d(
                            TAG,
                            "TORCIA mode=$torchMode enabled=$torchEnabled"
                        )
                    },
                    ContextCompat.getMainExecutor(context)
                )
        }
    }

    private fun notifyTorchState(available: Boolean) {
        mainHandler.post {
            torchStateListener?.invoke(
                torchMode,
                torchEnabled,
                available
            )
        }
    }

    private class BrightnessAwareBarcodeAnalyzer(
        private val barcodeAnalyzer: ImageAnalysis.Analyzer,
        private val onBrightnessMeasured: (Double) -> Unit
    ) : ImageAnalysis.Analyzer {

        override fun analyze(image: ImageProxy) {
            try {
                onBrightnessMeasured(
                    calculateAverageLuminance(image)
                )
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "ERRORE LETTURA LUMINOSITA'",
                    error
                )
            }

            /*
             * OverlayBarcodeAnalyzer resta responsabile della chiusura
             * dell'ImageProxy, esattamente come prima.
             */
            barcodeAnalyzer.analyze(image)
        }

        private fun calculateAverageLuminance(
            image: ImageProxy
        ): Double {
            val plane = image.planes.firstOrNull()
                ?: return 255.0

            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val width = image.width
            val height = image.height

            var sum = 0L
            var samples = 0

            var y = 0
            while (y < height) {
                val rowOffset = y * rowStride

                var x = 0
                while (x < width) {
                    val index = rowOffset + x * pixelStride

                    if (index >= 0 && index < buffer.limit()) {
                        sum += buffer.get(index).toInt() and 0xFF
                        samples++
                    }

                    x += LUMINANCE_SAMPLE_STEP
                }

                y += LUMINANCE_SAMPLE_STEP
            }

            return if (samples > 0) {
                sum.toDouble() / samples.toDouble()
            } else {
                255.0
            }
        }
    }
}