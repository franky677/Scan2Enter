package com.scan2enter.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import com.scan2enter.scanner.ScanConfig

class ScanOverlayView(
    context: Context
) : View(context) {

    private val dimPaint = Paint().apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val framePaint = Paint().apply {
        color = Color.rgb(0, 255, 120)
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val laserPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        isAntiAlias = true
    }

    private var laserOffset = 0f

    init {

        ValueAnimator.ofFloat(0f, 1f).apply {

            duration = 1800

            repeatCount = ValueAnimator.INFINITE

            interpolator = LinearInterpolator()

            addUpdateListener {

                laserOffset = it.animatedFraction

                invalidate()

            }

            start()

        }

    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            dimPaint
        )

        val frameWidth = dp(ScanConfig.FRAME_WIDTH_DP)
        val frameHeight = dp(ScanConfig.FRAME_HEIGHT_DP)

        val left = (width - frameWidth) / 2f
        val top = (height - frameHeight) / 2f

        val right = left + frameWidth
        val bottom = top + frameHeight

        canvas.drawRoundRect(
            left,
            top,
            right,
            bottom,
            18f,
            18f,
            framePaint
        )

        val corner = dp(18)

        // Alto SX
        canvas.drawLine(left, top, left + corner, top, framePaint)
        canvas.drawLine(left, top, left, top + corner, framePaint)

        // Alto DX
        canvas.drawLine(right, top, right - corner, top, framePaint)
        canvas.drawLine(right, top, right, top + corner, framePaint)

        // Basso SX
        canvas.drawLine(left, bottom, left + corner, bottom, framePaint)
        canvas.drawLine(left, bottom, left, bottom - corner, framePaint)

        // Basso DX
        canvas.drawLine(right, bottom, right - corner, bottom, framePaint)
        canvas.drawLine(right, bottom, right, bottom - corner, framePaint)

        // Laser

        val y = top + (bottom - top) * laserOffset

        canvas.drawLine(
            left + 10,
            y,
            right - 10,
            y,
            laserPaint
        )
    }

    private fun dp(value: Int): Float {

        return value *
                resources.displayMetrics.density

    }

}