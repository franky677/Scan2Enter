package com.scan2enter.overlay.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class OverlayBarcodeAnalyzer(

    private val onBarcodeDetected: (String) -> Unit

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

    private var lastBarcode = ""

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

                val value =

                    barcodes.firstOrNull()?.rawValue

                if (!value.isNullOrBlank()) {

                    if (value != lastBarcode) {

                        lastBarcode = value

                        onBarcodeDetected(value)

                    }

                }

                imageProxy.close()

            }

            .addOnFailureListener {

                imageProxy.close()

            }

    }

    fun reset() {

        lastBarcode = ""

    }

}