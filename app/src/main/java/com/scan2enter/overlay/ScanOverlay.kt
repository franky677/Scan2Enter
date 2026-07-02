package com.scan2enter.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.camera.view.PreviewView
import com.scan2enter.overlay.camera.OverlayCameraManager
import com.scan2enter.overlay.camera.OverlayLifecycleOwner

class ScanOverlay(
    private val context: Context
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val lifecycleOwner =
        OverlayLifecycleOwner()

    private val cameraManager =
        OverlayCameraManager(context)

    private var previewView: PreviewView? = null

    fun show() {

        if (previewView != null) return

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

        lifecycleOwner.start()

        cameraManager.start(
            preview,
            lifecycleOwner
        )
    }

    fun hide() {

        cameraManager.stop()

        lifecycleOwner.stop()

        previewView?.let {

            windowManager.removeView(it)

        }

        previewView = null
    }

    fun previewView(): PreviewView? {

        return previewView

    }

    private fun dp(value: Int): Int {

        return (
                value *
                        context.resources.displayMetrics.density
                ).toInt()

    }

}