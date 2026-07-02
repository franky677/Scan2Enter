package com.scan2enter.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.scan2enter.data.ScanStorage
import com.scan2enter.viewmodel.MainViewModel
import android.content.Intent
import com.scan2enter.accessibility.BarcodeReceiver

@Composable
fun CameraScreen(
    viewModel: MainViewModel
) {

    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var scannedCode by remember {
        mutableStateOf("Inquadra un QR Code")
    }

    var qrAlreadyRead by remember {
        mutableStateOf(false)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasPermission) {
        Text("Permesso fotocamera negato")
        return
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->

                val previewView = PreviewView(ctx)

                val cameraProviderFuture =
                    ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({

                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build()

                    preview.setSurfaceProvider(
                        previewView.surfaceProvider
                    )

                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                        .build()

                    val scanner = BarcodeScanning.getClient(options)

                    val imageAnalysis =
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                            )
                            .build()

                    imageAnalysis.setAnalyzer(
                        ContextCompat.getMainExecutor(ctx)
                    ) { imageProxy ->

                        val mediaImage = imageProxy.image

                        if (mediaImage == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        Log.d("Scan2Enter", "FRAME ANALIZZATO")

                        scanner.process(image)

                            .addOnSuccessListener { barcodes ->

                                Log.d(
                                    "Scan2Enter",
                                    "SUCCESS - Barcode trovati: ${barcodes.size}"
                                )

                                Log.d(
                                    "Scan2Enter",
                                    "qrAlreadyRead = $qrAlreadyRead"
                                )

                                if (barcodes.isNotEmpty() && !qrAlreadyRead) {

                                    qrAlreadyRead = true

                                    val code =
                                        barcodes.first().rawValue ?: ""

                                    scannedCode = code

                                    Log.d(
                                        "Scan2Enter",
                                        "EAN LETTO: $code"
                                    )

                                    val intent = Intent(BarcodeReceiver.ACTION_BARCODE)

                                    intent.putExtra(
                                        BarcodeReceiver.EXTRA_BARCODE,
                                        code
                                    )

                                    ctx.sendBroadcast(intent)

                                    Log.d(
                                        "Scan2Enter",
                                        "Broadcast inviato: $code"
                                    )

                                    viewModel.onQrScanned(code)

                                    Log.d(
                                        "Scan2Enter",
                                        "CHIUDO L'ACTIVITY"
                                    )

                                    activity?.finish()
                                }
                            }

                            .addOnFailureListener {

                                scannedCode = "Errore lettura"
                            }

                            .addOnCompleteListener {

                                imageProxy.close()
                            }
                    }

                    try {

                        cameraProvider.unbindAll()

                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )

                    } catch (e: Exception) {

                        scannedCode = e.message ?: "Errore CameraX"
                    }

                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        Text(
            text = scannedCode,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}