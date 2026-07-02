package com.scan2enter.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.camera.view.PreviewView
import com.scan2enter.data.ScanStorage
import com.scan2enter.feedback.ScanFeedbackManager
import com.scan2enter.overlay.camera.OverlayCameraManager
import com.scan2enter.overlay.camera.OverlayLifecycleOwner

class ScanOverlay(
    private val context: Context
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val cameraManager =
        OverlayCameraManager(context)

    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private var previewView: PreviewView? = null

    private val handler = Handler(Looper.getMainLooper())

    private val timeoutRunnable = Runnable {
        if (!closing) {
            Log.d("Scan2Enter", "Scanner timeout")
            hide()
        }
    }

    @Volatile
    private var closing = false

    fun show() {

        if (previewView != null) return

        val startTime = System.currentTimeMillis()

        closing = false

        handler.postDelayed(
            timeoutRunnable,
            10_000L
        )

        val preview = PreviewView(context)

        preview.setBackgroundColor(Color.BLACK)

        preview.scaleType =
            PreviewView.ScaleType.FILL_CENTER

        val params = WindowManager.LayoutParams(

            dp(340),
            dp(240),

            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,

            PixelFormat.TRANSLUCENT

        )

        params.gravity = Gravity.CENTER

        windowManager.addView(preview, params)

        previewView = preview

        lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner!!.start()

        cameraManager.start(
            preview,
            lifecycleOwner!!
        ) { barcode ->

            if (closing) return@start

            closing = true

            Log.d(
                "Scan2Enter",
                "Barcode letto dopo ${System.currentTimeMillis() - startTime} ms"
            )

            ScanFeedbackManager.beep()

            ScanStorage.save(
                context,
                barcode
            )

            hide()
        }
    }

    fun hide() {

        handler.removeCallbacks(timeoutRunnable)

        if (previewView == null) return

        cameraManager.stop()

        lifecycleOwner?.stop()
        lifecycleOwner = null

        previewView?.let {
            windowManager.removeView(it)
        }

        previewView = null

        Log.d("Scan2Enter", "Scanner chiuso")
    }

    private fun dp(value: Int): Int {

        return (
                value *
                        context.resources.displayMetrics.density
                ).toInt()

    }

}