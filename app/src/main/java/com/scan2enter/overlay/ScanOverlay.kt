package com.scan2enter.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.scan2enter.MainActivity
import com.scan2enter.scanner.ScanSession

class ScanOverlay(
    private val context: Context
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val scanSession =
        ScanSession(context)

    private val handler =
        Handler(Looper.getMainLooper())

    private var container: FrameLayout? = null

    @Volatile
    private var closing = false

    private var receiverRegistered = false
    private var rapidRescan = false
    private var godexMode = false
    private var a4Mode = false

    private companion object {
        const val TAG = "Scan2Enter"
        const val SUNMI_SCAN_ACTION = "com.honeywell.tools.action.scan_result"
        const val SUNMI_BARCODE_EXTRA = "barcode_data"

        const val WORKFLOW_PREFS = "scan_workflow"
        const val WORKFLOW_MODE_KEY = "mode"
        const val MODE_INFO = "INFO"
        const val MODE_LABELS_GODEX = "ETICHETTE_GODEX"
        const val MODE_LABELS_A4 = "ETICHETTE_A4"
    }

    private val sunmiReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                receiverContext: Context?,
                intent: Intent?
            ) {
                if (intent?.action != SUNMI_SCAN_ACTION) {
                    return
                }

                val barcode =
                    intent.getStringExtra(SUNMI_BARCODE_EXTRA)
                        ?.trim()
                        .orEmpty()

                Log.d(
                    TAG,
                    "SUNMI BROADCAST barcode=$barcode"
                )

                if (barcode.isBlank()) {
                    return
                }

                onHardwareBarcode(barcode)
            }
        }

    fun show(
        rapidRescan: Boolean = false
    ) {
        if (container != null) {
            Log.d(TAG, "SUNMI SCANNER GIÀ APERTO")
            return
        }

        closing = false
        this.rapidRescan = rapidRescan

        val currentMode = loadCurrentMode()
        godexMode = currentMode == MODE_LABELS_GODEX
        a4Mode = currentMode == MODE_LABELS_A4

        scanSession.start()

        try {
            context.registerReceiver(
                sunmiReceiver,
                IntentFilter(SUNMI_SCAN_ACTION)
            )

            receiverRegistered = true
            Log.d(TAG, "SUNMI RECEIVER REGISTRATO")
        } catch (error: Exception) {
            receiverRegistered = false

            Log.e(
                TAG,
                "ERRORE REGISTRAZIONE SUNMI RECEIVER",
                error
            )
        }

        val frame =
            FrameLayout(context).apply {
                setBackgroundColor(
                    Color.rgb(12, 12, 12)
                )
                isClickable = true
            }

        val closeScanner =
            View.OnClickListener {
                Log.d(
                    TAG,
                    "SUNMI SCANNER CHIUSO CON TOCCO"
                )

                if (godexMode || a4Mode) {
                    exitSpecialModeToHome()
                } else {
                    hide()
                }
            }

        frame.setOnClickListener(closeScanner)

        val readyText =
            TextView(context).apply {
                text =
                    if (godexMode) {
                        "🏷️ GODEX\n\nPRONTO ALLA LETTURA\n\nPremi il grilletto"
                    } else if (a4Mode) {
                        "📄 ETICHETTE A4\n\nPRONTO ALLA LETTURA\n\nPremi il grilletto"
                    } else {
                        "🔴 SCANNER SUNMI\n\nPRONTO\n\nPremi il grilletto"
                    }

                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding(
                    dp(18),
                    dp(18),
                    dp(18),
                    dp(18)
                )
                setOnClickListener(closeScanner)
            }

        frame.addView(
            readyText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val params =
            WindowManager.LayoutParams(
                dp(280),
                dp(210),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

        windowManager.addView(frame, params)
        container = frame

        Log.d(
            TAG,
            "SUNMI HARDWARE SCANNER PRONTO - CAMERAX NON AVVIATA"
        )
    }

    fun onHardwareBarcode(
        barcode: String
    ) {
        if (container == null) {
            Log.d(
                TAG,
                "SUNMI BARCODE IGNORATO - SCANNER NON APERTO"
            )
            return
        }

        if (closing) {
            Log.d(
                TAG,
                "SUNMI BARCODE IGNORATO - LETTURA GIÀ IN CORSO"
            )
            return
        }

        val normalized = barcode.trim()

        if (normalized.isBlank()) {
            return
        }

        Log.d(
            TAG,
            "SUNMI BARCODE -> SCANSESSION = $normalized"
        )

        closing = true

        scanSession.onBarcodeRead(normalized) {
            hide()
        }
    }

    fun hide() {
        scanSession.stop()

        if (receiverRegistered) {
            try {
                context.unregisterReceiver(sunmiReceiver)
            } catch (_: Exception) {
            }

            receiverRegistered = false
        }

        container?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }

        container = null
        rapidRescan = false
        godexMode = false
        a4Mode = false
        closing = false

        Log.d(TAG, "SUNMI SCANNER CHIUSO")
    }

    private fun loadCurrentMode(): String {
        return context.applicationContext
            .getSharedPreferences(
                WORKFLOW_PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                WORKFLOW_MODE_KEY,
                MODE_INFO
            )
            ?: MODE_INFO
    }

    private fun exitSpecialModeToHome() {
        if (a4Mode) {
            hide()

            handler.postDelayed(
                {
                    context.startService(
                        Intent(
                            context,
                            OverlayService::class.java
                        ).apply {
                            action =
                                OverlayService.ACTION_SHOW_A4_LABELS
                        }
                    )
                },
                250L
            )

            return
        }

        context.applicationContext
            .getSharedPreferences(
                WORKFLOW_PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                WORKFLOW_MODE_KEY,
                MODE_INFO
            )
            .apply()

        hide()

        context.startActivity(
            Intent(
                context,
                MainActivity::class.java
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
        )
    }

    private fun dp(value: Int): Int {
        return (
                value *
                        context.resources.displayMetrics.density
                ).toInt()
    }
}