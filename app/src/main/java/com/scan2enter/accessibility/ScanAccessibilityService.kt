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

        Log.d(TAG, "PACKAGE = $pkg")

        // Overlay

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

        val root = rootInActiveWindow

        if (root == null) {

            Log.d(TAG, "ROOT = NULL")
            return
        }

        Log.d(TAG, "Root class = ${root.className}")

        val focused =
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focused == null) {

            Log.d(TAG, "FOCUS = NULL")
            return
        }

        Log.d(TAG, "Focus class = ${focused.className}")
        Log.d(TAG, "Editable = ${focused.isEditable}")
        Log.d(TAG, "ViewId = ${focused.viewIdResourceName}")
        Log.d(TAG, "Text = ${focused.text}")
        Log.d(TAG, "Actions = ${focused.actionList}")
        Log.d(TAG, "Action count = ${focused.actionList.size}")

        focused.actionList.forEach {
            Log.d(TAG, "ACTION -> ${it.id}   ${it.label}")
        }

        if (!focused.isEditable) {
            return
        }

        val code = ScanStorage.load(applicationContext) ?: ""

        if (code.isBlank()) {

            Log.d(TAG, "CODICE VUOTO")
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

            Log.d(TAG, "CODICE INSERITO")

            // Prova a premere il tasto Invio della tastiera
            focused.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER)

            ScanStorage.save(applicationContext, "")
        }
    }

    override fun onInterrupt() {

        Log.d(TAG, "Accessibility interrotto")
    }
}