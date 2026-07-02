package com.scan2enter.overlay.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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

    private val providerFuture =
        ProcessCameraProvider.getInstance(context)

    private var cameraProvider: ProcessCameraProvider? = null

    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    fun start(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onBarcodeDetected: (String) -> Unit
    ) {

        providerFuture.addListener({

            cameraProvider = providerFuture.get()

            cameraProvider?.unbindAll()

            val preview = Preview.Builder()
                .build()

            preview.surfaceProvider =
                previewView.surfaceProvider

            val imageAnalysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

            imageAnalysis.setAnalyzer(

                cameraExecutor,

                OverlayBarcodeAnalyzer(
                    onBarcodeDetected
                )

            )

            cameraProvider?.bindToLifecycle(

                lifecycleOwner,

                CameraSelector.DEFAULT_BACK_CAMERA,

                preview,

                imageAnalysis

            )

        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {

        cameraProvider?.unbindAll()

    }

    fun release() {

        stop()

        cameraExecutor.shutdown()

    }

}