package com.scan2enter.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class BarcodeAnalyzer(

    private val onBarcodeRead: (String) -> Unit

) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(

        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE
            )
            .build()
    )

    private var lastCode = ""

    override fun analyze(imageProxy: ImageProxy) {

        val mediaImage = imageProxy.image

        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->

                val code = barcodes.firstOrNull()?.rawValue

                if (!code.isNullOrBlank() && code != lastCode) {

                    lastCode = code

                    onBarcodeRead(code)
                }

                imageProxy.close()
            }
            .addOnFailureListener {

                imageProxy.close()

            }
    }

    fun reset() {

        lastCode = ""

    }
}