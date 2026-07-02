package com.scan2enter.scanner

import android.content.Context

class ScanSession(

    private val context: Context

) {

    private val cameraController =
        CameraController(context)

    fun start() {

        // arriverà CameraX

    }

    fun stop() {

        cameraController.unbindAll()

    }

}