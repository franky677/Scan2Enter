package com.scan2enter.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageButton
import com.scan2enter.MainActivity
import com.scan2enter.R
import com.scan2enter.overlay.OverlayPosition

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: android.view.View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = OverlayPosition.getX(this)
        params.y = OverlayPosition.getY(this)

        windowManager.addView(floatingView, params)

        val button = floatingView!!.findViewById<ImageButton>(R.id.btnOverlay)

        button.setOnClickListener {

            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        button.setOnTouchListener(object : android.view.View.OnTouchListener {

            private var initialX = 0
            private var initialY = 0

            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(
                v: android.view.View,
                event: MotionEvent
            ): Boolean {

                when (event.action) {

                    MotionEvent.ACTION_DOWN -> {

                        initialX = params.x
                        initialY = params.y

                        initialTouchX = event.rawX
                        initialTouchY = event.rawY

                        return false
                    }

                    MotionEvent.ACTION_MOVE -> {

                        params.x =
                            initialX + (event.rawX - initialTouchX).toInt()

                        params.y =
                            initialY + (event.rawY - initialTouchY).toInt()

                        windowManager.updateViewLayout(
                            floatingView,
                            params
                        )
                        OverlayPosition.save(
                            this@OverlayService,
                            params.x,
                            params.y
                        )
                        return true
                    }

                    MotionEvent.ACTION_UP -> {

                        val screenWidth = resources.displayMetrics.widthPixels

                        if (params.x < screenWidth / 2) {
                            params.x = 0
                        } else {
                            params.x = screenWidth - (floatingView?.width ?: 64)
                        }
                        OverlayPosition.save(
                            this@OverlayService,
                            params.x,
                            params.y
                        )
                        windowManager.updateViewLayout(
                            floatingView,
                            params
                        )

                        return false
                    }
                }

                return false
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()

        floatingView?.let {
            windowManager.removeView(it)
        }
    }
}