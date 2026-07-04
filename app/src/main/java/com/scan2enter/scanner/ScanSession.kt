package com.scan2enter.scanner

import android.content.Context
import android.util.Log
import com.scan2enter.data.ScanStorage
import com.scan2enter.feedback.ScanFeedbackManager
import com.scan2enter.scanner.BarcodeIntentSender

class ScanSession(
    private val context: Context
) {

    @Volatile
    private var running = false

    fun start() {

        running = true

        Log.d("Scan2Enter", "ScanSession START")
    }

    fun stop() {

        running = false

        Log.d("Scan2Enter", "ScanSession STOP")
    }

    fun onBarcodeRead(
        barcode: String,
        onCompleted: () -> Unit
    ) {

        if (!running)
            return

        running = false

        Log.d(
            "Scan2Enter",
            "Barcode = $barcode"
        )

        ScanFeedbackManager.beep()

// Prova prima il broadcast verso Due Retail
        BarcodeIntentSender.send(
            context,
            barcode
        )

// Manteniamo comunque il metodo attuale come fallback
        ScanStorage.save(
            context,
            barcode
        )

        onCompleted()
    }

}