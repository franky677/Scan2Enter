package com.scan2enter.scanner

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.common.util.concurrent.ListenableFuture

class CameraController(
    private val context: Context
) {

    private val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
        ProcessCameraProvider.getInstance(context)

    fun getCameraProviderFuture(): ListenableFuture<ProcessCameraProvider> {
        return cameraProviderFuture
    }

    fun unbindAll() {
        if (cameraProviderFuture.isDone) {
            cameraProviderFuture.get().unbindAll()
        }
    }
}