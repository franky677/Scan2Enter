package com.scan2enter.scanner

import android.os.Build

enum class ScannerMode {
    CAMERA,
    SUNMI_LASER
}

object ScannerModeDetector {

    fun current(): ScannerMode {
        return if (
            Build.MANUFACTURER.contains(
                "SUNMI",
                ignoreCase = true
            )
        ) {
            ScannerMode.SUNMI_LASER
        } else {
            ScannerMode.CAMERA
        }
    }

    fun isSunmi(): Boolean =
        current() == ScannerMode.SUNMI_LASER

    /**
     * Riconosce i terminali Zebra senza modificare la classificazione
     * CAMERA/SUNMI già usata dal resto dell'app.
     *
     * Serve per gestire comportamenti Zebra specifici (DataWedge)
     * in modo aggiuntivo e senza regressioni sugli altri dispositivi.
     */
    fun isZebra(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()

        return manufacturer.contains("ZEBRA", ignoreCase = true) ||
                brand.contains("ZEBRA", ignoreCase = true)
    }

    fun isCameraDevice(): Boolean =
        current() == ScannerMode.CAMERA
}