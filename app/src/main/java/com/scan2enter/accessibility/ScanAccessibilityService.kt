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
import com.scan2enter.accessibility.UiDumpExporter
import com.scan2enter.model.ProductInfoReader
import com.scan2enter.model.ProductInfo
import android.graphics.Path
import android.accessibilityservice.GestureDescription
import android.graphics.Rect
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
    private val productInfoReader = ProductInfoReader()
    private var currentProductInfo: ProductInfo? = null
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


        if (pkg == DUE_PACKAGE) {

            if (!overlayVisible) {

                overlayVisible = true

                startService(
                    Intent(this, OverlayService::class.java)
                )

                Log.d(TAG, "Overlay ON")
            }
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ) {

                UiDumpExporter.export(
                    this,
                    root
                )
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
        //  dumpClickable(root)
        Log.d(TAG, "=============================")

        val code = ScanStorage.load(applicationContext)
            ?: return

        if (lastInjected == code &&
            lastWindowId == root.windowId
        ) {
            return
        }

        val field = this.findEditable(root)

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

            if (clickBarcodeButton(root)) {

                Log.d(TAG, "Barcode click OK, provo click risultato tra 200ms")

                handler.postDelayed({

                    Log.d(TAG, "Eseguo clickFirstResult()")

                    clickFirstResult()

                    handler.postDelayed({

                        readProductData()

                    }, 300)

                }, 200)


            } else {

                Log.d(TAG, "Barcode click FALLITO")

            }

        }, 400)

    }

    private fun clickSearchProduct(
        root: AccessibilityNodeInfo
    ): Boolean {

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

    private fun tapNode(node: AccessibilityNodeInfo?): Boolean {

        if (node == null) {
            return false
        }

        val rect = android.graphics.Rect()

        node.getBoundsInScreen(rect)

        Log.d(
            TAG,
            "TAP NODE bounds = $rect"
        )

        if (rect.isEmpty) {
            return false
        }

        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()

        Log.d(
            TAG,
            "ESEGUO TAP x=$x y=$y"
        )

        val path = android.graphics.Path()

        path.moveTo(
            x,
            y
        )

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0,
                        100
                    )
                )
                .build()

        return dispatchGesture(
            gesture,
            null,
            null
        )
    }

    private fun findPublicPriceRow(
        root: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {

        if (root == null) {
            return null
        }

        val vendors =
            root.findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/vendor_textview"
            )

        for (vendor in vendors) {

            if (vendor.text?.toString() == "3-AL PUBBLICO") {

                Log.d(
                    TAG,
                    "TROVATO VENDOR PUBBLICO"
                )

                return vendor
            }
        }

        return null
    }

    private fun clickFirstResult() {

        val root = rootInActiveWindow ?: return

        val recycler = root.findAccessibilityNodeInfosByViewId(
            "it.duebit.due:id/recycler_view"
        )

        if (recycler.isEmpty()) {

            Log.d(TAG, "RecyclerView NON trovata")

            return
        }

        val list = recycler.first()

        if (list.childCount == 0) {

            Log.d(TAG, "RecyclerView vuota")

            return
        }

        val firstItem = list.getChild(0)

        if (firstItem == null) {

            Log.d(TAG, "Primo elemento nullo")

            return
        }

        Log.d(TAG, "Click primo elemento RecyclerView")

        var ok = firstItem.performAction(
            AccessibilityNodeInfo.ACTION_CLICK
        )

        if (!ok && firstItem.isClickable) {
            ok = firstItem.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )
        }

        if (!ok) {

            var parent = firstItem.parent

            while (parent is AccessibilityNodeInfo) {

                if (parent.isClickable) {

                    ok = parent.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )

                    if (ok) break
                }

                parent = parent.parent
            }
        }

        Log.d(TAG, "CLICK primo elemento = $ok")
    }

    private fun readProductData(
        attempt: Int = 0
    ) {

        val root = rootInActiveWindow

        if (root == null) {

            retryReadProductData(attempt)

            return
        }


        currentProductInfo =
            productInfoReader.buildProductInfo(root)


        val productInfo = currentProductInfo


        if (productInfo == null) {

            retryReadProductData(attempt)

            return
        }


        if (productInfo.description.isNotEmpty()) {


            Log.d(
                TAG,
                "DESCRIZIONE = ${productInfo.description}"
            )


            Log.d(
                TAG,
                "PREZZO INFO = ${productInfo.publicPrice}"
            )


            /*
             * Apertura scheda prezzo vendita
             */
            handler.postDelayed({

                val opened =
                    clickPriceTab()


                Log.d(
                    TAG,
                    "CLICK TAB PRZ VEND = $opened"
                )


                if (!opened) {
                    return@postDelayed
                }


                /*
                 * Attendo caricamento pagina prezzi
                 */
                handler.postDelayed({

                    Log.d(
                        TAG,
                        "TENTO SWITCH PREZZO"
                    )


                    val currentMode =
                        readPriceMode()


                    Log.d(
                        TAG,
                        "MODE PRIMA SWITCH = $currentMode"
                    )


                    if (currentMode == "Ivato") {


                        val switched =
                            clickPriceModeSwitch()


                        Log.d(
                            TAG,
                            "CLICK CAMBIO MODO = $switched"
                        )


                        handler.postDelayed({


                            val afterMode =
                                readPriceMode()


                            Log.d(
                                TAG,
                                "MODE DOPO SWITCH = $afterMode"
                            )
                            handler.postDelayed({

                                val root = rootInActiveWindow

                                val info =
                                    productInfoReader.buildProductInfo(root)

                                Log.d(
                                    TAG,
                                    "PREZZO DOPO SWITCH = ${info.publicPrice}"
                                )

                            }, 1000)

                            if (afterMode == "Ivato") {


                                Log.d(
                                    TAG,
                                    "SECONDO TENTATIVO SWITCH"
                                )


                                clickPriceModeSwitch()

                            }


                        }, 700)


                    } else {


                        Log.d(
                            TAG,
                            "GIA IMPONIBILE - NESSUNO SWITCH"
                        )

                    }


                    handler.postDelayed({

                        UiDumpExporter.export(
                            this,
                            rootInActiveWindow
                        )

                    }, 1200)


                }, 1500)


            }, 1500)


            return

        }


        retryReadProductData(attempt)
    }
    private fun readStockPage() {

        val root = rootInActiveWindow ?: return

        val stock = productInfoReader.readStock(root)

        Log.d(TAG, "GIACENZA = $stock")
    }

    private fun readAdditionalProductInfo() {

        val newRoot = rootInActiveWindow ?: return

        val year = productInfoReader.readYear(newRoot)
        val season = productInfoReader.readSeason(newRoot)

        Log.d(TAG, "ANNO = $year")
        Log.d(TAG, "STAGIONE = $season")

        val clicked = clickStockTab()

        Log.d(TAG, "CLICK GIACENZA = $clicked")

        if (clicked) {

            handler.postDelayed({

                readStockPage()

            }, 300)
        }
    }

    private fun clickStockTab(): Boolean {

        val root = rootInActiveWindow ?: return false

        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {

            if (node == null) return null

            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()

            if (text == "GIACENZA" || desc == "Giacenza") {
                return node
            }

            for (i in 0 until node.childCount) {
                val result = search(node.getChild(i))
                if (result != null) return result
            }

            return null
        }

        val tab = search(root) ?: return false

        var node: AccessibilityNodeInfo? = tab

        while (node != null) {

            if (node.isClickable) {

                val ok = node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )

                Log.d(TAG, "CLICK TAB GIACENZA = $ok")

                return ok
            }

            node = node.parent
        }

        return false
    }

    private fun clickPriceTab(): Boolean {

        val root = rootInActiveWindow ?: return false

        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {

            if (node == null) return null

            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()

            if (text == "PRZ VEND" || desc == "Prz vend") {
                return node
            }

            for (i in 0 until node.childCount) {
                val result = search(node.getChild(i))
                if (result != null) return result
            }

            return null
        }

        val tab = search(root) ?: return false

        var node: AccessibilityNodeInfo? = tab

        while (node != null) {

            if (node.isClickable) {

                val ok = node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )

                Log.d(TAG, "CLICK TAB PRZ VEND = $ok")

                return ok
            }

            node = node.parent
        }

        return false
    }


    private fun clickPurchasePriceTab(): Boolean {

        val root = rootInActiveWindow ?: return false

        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {

            if (node == null) return null

            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()

            if (text == "PRZ ACQ" || desc == "Prz acq") {
                return node
            }

            for (i in 0 until node.childCount) {

                val result = search(node.getChild(i))

                if (result != null)
                    return result
            }

            return null
        }

        val tab = search(root) ?: return false

        var node: AccessibilityNodeInfo? = tab

        while (node != null) {

            if (node.isClickable) {

                val ok = node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )

                Log.d(
                    TAG,
                    "CLICK TAB PRZ ACQ = $ok"
                )

                return ok
            }

            node = node.parent
        }

        return false
    }


    private fun clickPriceModeSwitch(): Boolean {
        Log.d(
            TAG,
            "ENTRO IN clickPriceModeSwitch"
        )

        val root = rootInActiveWindow ?: return false

        val modeNodes =
            root.findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/price_mode_textview"
            )

        if (modeNodes.isEmpty()) {

            Log.d(
                TAG,
                "PRICE MODE TEXTVIEW NON TROVATO"
            )

            return false
        }


        val textNode = modeNodes.first()

        val modeText =
            textNode.text?.toString()


        Log.d(
            TAG,
            "CURRENT PRICE MODE = $modeText"
        )


        /*
         * Se è già imponibile non fare nulla
         */
        if (modeText.equals("Imponibile", true)) {

            Log.d(
                TAG,
                "GIÀ IMPONIBILE - NESSUNO SWITCH"
            )

            return false
        }


        /*
         * Cerca il contenitore cliccabile
         */
        var node: AccessibilityNodeInfo? =
            textNode


        while (node != null) {


            if (node.isClickable) {


                val ok =
                    node.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )


                Log.d(
                    TAG,
                    "CLICK PRICE MODE = $ok"
                )


                return ok
            }


            node = node.parent
        }


        Log.d(
            TAG,
            "NESSUN PARENT CLICCABILE"
        )


        return false

    }
    private fun dumpChildren(node: AccessibilityNodeInfo?, level: Int = 0) {

        if (node == null) return

        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        Log.d(
            TAG,
            "${" ".repeat(level * 2)}CLASS=${node.className} " +
                    "ID=${node.viewIdResourceName} " +
                    "TEXT=${node.text} " +
                    "DESC=${node.contentDescription} " +
                    "CLICK=${node.isClickable} " +
                    "BOUNDS=$rect"
        )

        for (i in 0 until node.childCount) {
            dumpChildren(node.getChild(i), level + 1)
        }
    }
    private fun clickUbicazioneSearchButton(): Boolean {

        val root = rootInActiveWindow ?: return false

        val buttons =
            root.findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/button_search"
            )

        if (buttons.isEmpty()) {

            Log.d(
                TAG,
                "PULSANTE CERCA UBICAZIONE NON TROVATO"
            )

            return false
        }

        val button = buttons.first()

        val ok = button.performAction(
            AccessibilityNodeInfo.ACTION_CLICK
        )

        Log.d(
            TAG,
            "CLICK CERCA UBICAZIONE = $ok"
        )

        return ok
    }

    private fun scrollProductPage(): Boolean {

        val root = rootInActiveWindow ?: return false

        val recyclerView = findInfoRecyclerView(root)
            ?: return false

        return recyclerView.performAction(
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        )
    }

    private fun findInfoRecyclerView(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {

        if (node == null)
            return null

        if (
            node.viewIdResourceName ==
            "it.duebit.due:id/recycler_view"
        ) {
            return node
        }

        for (i in 0 until node.childCount) {

            val result = findInfoRecyclerView(
                node.getChild(i)
            )

            if (result != null)
                return result
        }

        return null
    }

    private fun scrollForward(
        node: AccessibilityNodeInfo
    ): Boolean {

        if (node.isScrollable) {

            return node.performAction(
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            )
        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            if (scrollForward(child)) {
                return true
            }
        }

        return false
    }

    private fun retryReadProductData(
        attempt: Int
    ) {

        if (attempt >= 10) {

            Log.d(TAG, "descr_textview non trovato")

            return
        }


        handler.postDelayed({

            readProductData(attempt + 1)

        }, 300)
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
    private fun readPriceMode(): String? {

        val root = rootInActiveWindow ?: return null

        val nodes =
            root.findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/price_mode_textview"
            )

        val mode =
            nodes.firstOrNull()
                ?.text
                ?.toString()


        Log.d(
            TAG,
            "READ PRICE MODE = $mode"
        )


        return mode
    }
}