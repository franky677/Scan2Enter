package com.scan2enter.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.scan2enter.data.ScanStorage
import com.scan2enter.overlay.OverlayService

class ScanAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "Scan2Enter"

        private var overlayVisible = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.e(TAG, "******** SERVICE CONNESSO ********")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) return

        val pkg = event.packageName?.toString() ?: return

        // Gestione automatica overlay

        if (pkg == "it.duebit.due") {

            if (!overlayVisible) {

                Log.d(TAG, "AVVIO OVERLAY")

                startService(
                    Intent(this, OverlayService::class.java)
                )

                overlayVisible = true
            }

        } else {

            if (overlayVisible) {

                Log.d(TAG, "CHIUDO OVERLAY")

                stopService(
                    Intent(this, OverlayService::class.java)
                )

                overlayVisible = false
            }

            return
        }

        val root = rootInActiveWindow ?: return

        val focused =
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return

        if (!focused.isEditable) {
            return
        }

        val code = ScanStorage.load(applicationContext) ?: ""

        if (code.isBlank()) {
            return
        }

        val args = Bundle()

        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            code
        )

        val ok = focused.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            args
        )

        Log.d(TAG, "SET_TEXT = $ok")

        if (ok) {

            ScanStorage.save(applicationContext, "")

            Log.d(TAG, "CODICE INSERITO")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility interrotto")
    }
}