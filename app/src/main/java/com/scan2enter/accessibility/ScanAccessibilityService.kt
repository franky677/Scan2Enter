package com.scan2enter.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

        private const val INSERT_DELAY = 150L

        private var overlayVisible = false

        private var lastInjected = ""

        private var lastWindowId = -1
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {

        Log.d(TAG, "Accessibility connessa")

    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {

        Log.d(
            TAG,
            "KEY=${event.keyCode} ACTION=${event.action}"
        )

        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null)
            return

        val root = rootInActiveWindow ?: return

        val pkg = root.packageName?.toString() ?: return

        //-----------------------------------------
        // DUMP COMPLETO UI
        //-----------------------------------------

        Log.d(TAG, "============== UI DUMP ==============")
        dumpNode(root)
        Log.d(TAG, "=====================================")

        //-----------------------------------------
        // Overlay
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
        // Barcode presente?
        //-----------------------------------------

        if (!ScanStorage.hasPendingCode(applicationContext))
            return

        handler.removeCallbacksAndMessages(null)

        handler.postDelayed({

            injectBarcode()

        }, INSERT_DELAY)

    }

    private fun injectBarcode() {

        val root = rootInActiveWindow ?: return
        Log.d(TAG, "===== SECONDA SCHERMATA =====")
        dumpClickable(root)
        Log.d(TAG, "=============================")

        val code = ScanStorage.load(applicationContext)
            ?: return

        if (lastInjected == code &&
            lastWindowId == root.windowId) {
            return
        }

        var field = findEditable(root)

        if (field == null) {

            if (clickSearchProduct(root)) {

                Log.d(TAG, "Apro finestra barcode...")

                handler.postDelayed({

                    injectBarcode()

                }, 350)

                return
            }

            Log.d(TAG, "Campo editabile NON trovato")

            return
        }

        field.performAction(
            AccessibilityNodeInfo.ACTION_FOCUS
        )

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

        lastInjected = code
        lastWindowId = root.windowId

        ScanStorage.clear(applicationContext)

        handler.postDelayed({

            clickBarcodeButton(root)

        }, 500)

    }


    private fun clickSearchProduct(root: AccessibilityNodeInfo): Boolean {

        val nodes = root.findAccessibilityNodeInfosByViewId(
            "it.duebit.due:id/search_product_layout"
        )

        if (nodes.isEmpty()) {
            Log.d(TAG, "search_product_layout NON trovato")
            return false
        }

        var node: AccessibilityNodeInfo? = nodes.first()

        while (node != null) {

            Log.d(
                TAG,
                "Provo click -> " +
                        "id=${node.viewIdResourceName} " +
                        "class=${node.className} " +
                        "clickable=${node.isClickable}"
            )

            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "CLICK riuscito")
                return true
            }

            node = node.parent
        }

        Log.d(TAG, "Nessun nodo cliccabile trovato")

        return false
    }

        private fun clickBarcodeButton(root: AccessibilityNodeInfo): Boolean {

            val nodes = root.findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/search_barcode_imagebutton"
            )

            if (nodes.isEmpty()) {

                Log.d(TAG, "Pulsante barcode NON trovato")

                return false
            }

            val node = nodes.first()

            Log.d(TAG, "========== TEST CLICK ==========")
            Log.d(TAG, "enabled = ${node.isEnabled}")
            Log.d(TAG, "clickable = ${node.isClickable}")
            Log.d(TAG, "focusable = ${node.isFocusable}")
            Log.d(TAG, "focused = ${node.isFocused}")
            Log.d(TAG, "visible = ${node.isVisibleToUser}")

            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            val result = node.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )

            Log.d(TAG, "ACTION_CLICK RESULT = $result")

            return result
        }

        private fun findEditable(
            node: AccessibilityNodeInfo?
        ): AccessibilityNodeInfo? {

            if (node == null)
                return null

            if (node.isEditable)
                return node

            for (i in 0 until node.childCount) {

                val result =
                    findEditable(node.getChild(i))

                if (result != null)
                    return result
            }

            return null
        }

        private fun dumpNode(
            node: AccessibilityNodeInfo?,
            level: Int = 0
        ) {

            if (node == null) return

            val indent = " ".repeat(level * 2)

            Log.d(
                TAG,
                indent +
                        "CLASS=${node.className} " +
                        "TEXT=${node.text} " +
                        "DESC=${node.contentDescription} " +
                        "ID=${node.viewIdResourceName} " +
                        "editable=${node.isEditable} " +
                        "clickable=${node.isClickable} " +
                        "focused=${node.isFocused}"
            )

            for (i in 0 until node.childCount) {

                dumpNode(
                    node.getChild(i),
                    level + 1
                )

            }

        }
        private fun dumpClickable(
            node: AccessibilityNodeInfo?,
            level: Int = 0
        ) {

            if (node == null) return

            if (node.isClickable) {

                Log.d(
                    TAG,
                    "CLICKABLE -> " +
                            "CLASS=${node.className} " +
                            "TEXT=${node.text} " +
                            "DESC=${node.contentDescription} " +
                            "ID=${node.viewIdResourceName}"
                )

            }

            for (i in 0 until node.childCount) {

                dumpClickable(
                    node.getChild(i),
                    level + 1
                )

            }

        }
    }