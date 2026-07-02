package com.scan2enter.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.scan2enter.data.ScanStorage
import com.scan2enter.overlay.OverlayService

class ScanAccessibilityService : AccessibilityService() {

    companion object {

        private const val TAG = "Scan2Enter"

        private const val DUE_PACKAGE = "it.duebit.due"

        private var overlayVisible = false

        private var lastInjected = ""

        private var lastWindowId = -1
    }

    override fun onServiceConnected() {

        Log.d(TAG, "Accessibility connessa")
    }

    override fun onInterrupt() {
    }

    // ============================
    // TEST TASTI HARDWARE
    // ============================

    override fun onKeyEvent(event: KeyEvent): Boolean {

        Log.d(
            TAG,
            "KEY = ${event.keyCode} ACTION = ${event.action}"
        )

        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) return

        val root = rootInActiveWindow ?: return

        val pkg = root.packageName?.toString() ?: return

        //-----------------------------------------
        // OVERLAY
        //-----------------------------------------

        if (pkg == DUE_PACKAGE) {

            if (!overlayVisible) {

                overlayVisible = true

                startService(
                    Intent(this, OverlayService::class.java)
                )

                Log.d(TAG, "Overlay ON")
            }

        } else {

            if (overlayVisible) {

                overlayVisible = false

                stopService(
                    Intent(this, OverlayService::class.java)
                )

                Log.d(TAG, "Overlay OFF")
            }

            return
        }

        //-----------------------------------------
        // BARCODE
        //-----------------------------------------

        val code = ScanStorage.load(applicationContext)

        if (code.isNullOrBlank())
            return

        //-----------------------------------------
        // EVITA DI RIPETERE
        //-----------------------------------------

        if (lastInjected == code &&
            lastWindowId == event.windowId) {
            return
        }

        //-----------------------------------------
        // CERCA CAMPO
        //-----------------------------------------

        val field = findEditable(root)

        if (field == null) {

            Log.d(TAG, "Campo editabile NON trovato")

            return
        }

        //-----------------------------------------
        // CAMPO VISIBILE
        //-----------------------------------------

        if (!field.isVisibleToUser) {

            Log.d(TAG, "Campo non visibile")

            return
        }

        //-----------------------------------------
        // FOCUS
        //-----------------------------------------

        field.performAction(
            AccessibilityNodeInfo.ACTION_FOCUS
        )

        //-----------------------------------------
        // INCOLLA TESTO
        //-----------------------------------------

        val args = Bundle()

        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            code
        )

        val ok = field.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            args
        )

        Log.d(TAG, "SET_TEXT = $ok")

        if (!ok)
            return

        //-----------------------------------------
        // MEMORIZZA
        //-----------------------------------------

        lastInjected = code
        lastWindowId = event.windowId

        ScanStorage.clear(applicationContext)

        Log.d(TAG, "BARCODE INCOLLATO = $code")
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {

        if (node == null)
            return null

        if (node.isEditable)
            return node

        for (i in 0 until node.childCount) {

            val child = node.getChild(i)

            val result = findEditable(child)

            if (result != null)
                return result
        }

        return null
    }
}