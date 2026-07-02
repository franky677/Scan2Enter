package com.scan2enter.overlay.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class OverlayCameraManager(
    private val context: Context
) {

    private val providerFuture =
        ProcessCameraProvider.getInstance(context)

    private var cameraProvider: ProcessCameraProvider? = null

    fun start(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner
    ) {

        providerFuture.addListener({

            cameraProvider = providerFuture.get()

            cameraProvider?.unbindAll()

            val preview = Preview.Builder().build()

            preview.surfaceProvider =
                previewView.surfaceProvider

            val selector =
                CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview
            )

        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {

        cameraProvider?.unbindAll()

    }

    fun release() {

        stop()

    }

}