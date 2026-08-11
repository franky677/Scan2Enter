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

    fun isCameraDevice(): Boolean =
        current() == ScannerMode.CAMERA
}