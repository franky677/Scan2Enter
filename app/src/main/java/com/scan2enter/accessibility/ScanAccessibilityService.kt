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
import com.scan2enter.model.ProductInfoStore

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
        } else if (pkg == packageName) {

            /*
             * Il popup informazioni appartiene a Scan2Enter.
             * Quando diventa la finestra attiva non devo arrestare
             * OverlayService, altrimenti il popup scompare subito.
             */
            Log.d(
                TAG,
                "FINESTRA SCAN2ENTER ATTIVA - OVERLAY MANTENUTO"
            )

            return

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

                Log.d(TAG, "Barcode click OK, attendo il primo risultato")

                waitAndClickFirstResult()

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

    private fun waitAndClickFirstResult(
        attempt: Int = 0,
        recoveryAttempt: Int = 0
    ) {

        val expectedBarcode = lastInjected.filter(Char::isDigit)

        if (expectedBarcode.isBlank()) {
            Log.d(TAG, "BARCODE ATTESO NON DISPONIBILE")
            return
        }

        /*
         * La riga della finestrella risultati non espone sempre il barcode
         * nell'albero Accessibility. Attendo quindi un breve tempo di
         * stabilizzazione e apro il primo risultato appena disponibile.
         */
        if (attempt < 3) {
            handler.postDelayed({
                waitAndClickFirstResult(attempt + 1, recoveryAttempt)
            }, 100)
            return
        }

        if (clickFirstResult()) {

            Log.d(
                TAG,
                "PRIMO RISULTATO APERTO - verifico scheda articolo"
            )

            waitForArticleDetail(expectedBarcode, recoveryAttempt = recoveryAttempt)

            return
        }

        if (attempt >= 25) {

            Log.d(
                TAG,
                "TIMEOUT ATTESA PRIMO RISULTATO barcode=$expectedBarcode"
            )

            return
        }

        handler.postDelayed({
            waitAndClickFirstResult(attempt + 1, recoveryAttempt)
        }, 100)
    }


    private fun waitForArticleDetail(
        expectedBarcode: String,
        attempt: Int = 0,
        recoveryAttempt: Int = 0
    ) {

        val root = rootInActiveWindow

        val descriptionReady =
            root?.findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/descr_textview"
            )?.isNotEmpty() == true

        Log.d(
            TAG,
            "ATTESA SCHEDA attempt=$attempt " +
                    "descriptionReady=$descriptionReady"
        )

        /*
         * Nella scheda Informazioni il barcode non viene sempre esposto
         * nello stesso formato del codice scansito. Usarlo come condizione
         * bloccante fermava quindi il workflow anche sulla scheda corretta.
         *
         * Quando descr_textview compare, lascio tre cicli da 100 ms alla UI
         * per stabilizzarsi e poi avvio la pipeline già collaudata.
         */
        if (descriptionReady && attempt >= 3) {

            Log.d(TAG, "SCHEDA ARTICOLO PRONTA")

            readProductData()

            return
        }

        if (attempt >= 30) {

            Log.d(
                TAG,
                "TIMEOUT ATTESA SCHEDA ARTICOLO barcode=$expectedBarcode"
            )

            return
        }

        handler.postDelayed({
            waitForArticleDetail(
                expectedBarcode = expectedBarcode,
                attempt = attempt + 1,
                recoveryAttempt = recoveryAttempt
            )
        }, 100)
    }


    private fun clickFirstResult(): Boolean {

        val root = rootInActiveWindow ?: return false

        val recycler = root.findAccessibilityNodeInfosByViewId(
            "it.duebit.due:id/recycler_view"
        )

        if (recycler.isEmpty()) {
            return false
        }

        val list = recycler.first()

        if (list.childCount == 0) {
            return false
        }

        val firstItem = list.getChild(0) ?: return false

        Log.d(TAG, "Click primo elemento RecyclerView disponibile")

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

        return ok
    }


    private fun tap(x: Float, y: Float, onComplete: (() -> Unit)? = null) {

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    80
                )
            )
            .build()

        dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {

                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "GESTURE COMPLETED")
                    onComplete?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.d(TAG, "GESTURE CANCELLED")
                }
            },
            null
        )
    }


    private fun waitInfoComplete(
        attempt: Int = 0
    ) {

        val root = rootInActiveWindow

        if (root == null) {

            if (attempt < 10) {
                handler.postDelayed({
                    waitInfoComplete(attempt + 1)
                }, 300)
            }

            return
        }

        val barcode =
            productInfoReader.readBarcode(root)

        val year =
            productInfoReader.readYear(root)

        val season =
            productInfoReader.readSeason(root)

        Log.d(
            TAG,
            "WAIT INFO attempt=$attempt  barcode=$barcode  year=$year  season=$season"
        )

        if (!year.isNullOrBlank() &&
            !season.isNullOrBlank()) {

            currentProductInfo =
                productInfoReader.buildProductInfo(root)

            Log.d(
                TAG,
                "INFO COMPLETA"
            )

            return
        }

        if (attempt < 10) {

            handler.postDelayed({

                waitInfoComplete(attempt + 1)

            }, 300)

        } else {

            Log.d(
                TAG,
                "TIMEOUT INFO"
            )

            currentProductInfo =
                productInfoReader.buildProductInfo(root)
        }
    }


    private fun readProductData(
        attempt: Int = 0
    ) {

        val root = rootInActiveWindow

        if (root == null) {
            retryReadProductData(attempt)
            return
        }


        Log.d(TAG, "===== DUMP SCHERMATA ARTICOLO =====")
        dumpNode(root)
        Log.d(TAG, "==================================")


        val initialProductInfo =
            productInfoReader.buildProductInfo(root)

        currentProductInfo = initialProductInfo.copy(
            barcode = initialProductInfo.barcode.ifBlank {
                lastInjected
            }
        )

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
            val scrolled = scrollInfoDown()

            Log.d(
                TAG,
                "SCROLL INFO = $scrolled"
            )


            handler.postDelayed({

                val root2 = rootInActiveWindow

                val barcode = productInfoReader.readBarcode(root2)
                val year = productInfoReader.readYear(root2)
                val season = productInfoReader.readSeason(root2)

                Log.d(TAG, "READ BARCODE = $barcode")
                Log.d(TAG, "READ YEAR = $year")
                Log.d(TAG, "READ SEASON = $season")

                currentProductInfo = currentProductInfo?.copy(
                    barcode = barcode ?: currentProductInfo?.barcode.orEmpty(),
                    year = year ?: currentProductInfo?.year.orEmpty(),
                    season = season ?: currentProductInfo?.season.orEmpty()
                )

                publishProductInfo(
                    showPopup = false,
                    workflowCompleted = false
                )

            }, 600)



            /*
             * Apro PRZ VEND
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
                 * Lascio la modalità prezzo corrente.
                 * Due Retail normalmente apre già in IVATO.
                 */
                handler.postDelayed({

                    Log.d(
                        TAG,
                        "LEGGO PREZZO MODALITA CORRENTE"
                    )


                    val priceRoot =
                        rootInActiveWindow


                    if (priceRoot != null) {

                        val info =
                            productInfoReader.buildProductInfo(priceRoot)

                        val previous = currentProductInfo

                        currentProductInfo = info.copy(
                            articleCode = info.articleCode.ifBlank {
                                previous?.articleCode.orEmpty()
                            },
                            description = info.description.ifBlank {
                                previous?.description.orEmpty()
                            },
                            barcode = info.barcode.ifBlank {
                                previous?.barcode.orEmpty()
                            },
                            year = info.year.ifBlank {
                                previous?.year.orEmpty()
                            },
                            season = info.season.ifBlank {
                                previous?.season.orEmpty()
                            },
                            taxablePrice = previous?.taxablePrice.orEmpty(),
                            vatRate = previous?.vatRate.orEmpty(),
                            stock = previous?.stock.orEmpty()
                        )

                        val spinnerNodes =
                            root?.findAccessibilityNodeInfosByViewId(
                                "it.duebit.due:id/barcode_spinner"
                            )

                        val spinner = spinnerNodes?.firstOrNull()

                        Log.d(
                            TAG,
                            "BARCODE SPINNER TEXT = ${spinner?.text}"
                        )

                        Log.d(
                            TAG,
                            "BARCODE SPINNER DESC = ${spinner?.contentDescription}"
                        )

                        Log.d(
                            TAG,
                            "BARCODE SPINNER CLASS = ${spinner?.className}"
                        )

                        Log.d(
                            TAG,
                            "PREZZO LETTO = ${currentProductInfo?.publicPrice}"
                        )

                        /*
                         * Mostro subito il popup Scan2Enter. Da questo momento
                         * le altre finestre di Due Retail continuano a cambiare
                         * sotto un overlay non focalizzabile.
                         */
                        publishProductInfo(
                            showPopup = true,
                            workflowCompleted = false
                        )

                    }


                    // ==========================
// APERTURA POPUP LISTINO
// ==========================

                    handler.postDelayed({

                        val vendorNode = findPublicPriceRow(rootInActiveWindow)

                        if (vendorNode == null) {
                            Log.d(TAG, "RIGA 3-AL PUBBLICO NON TROVATA")
                            return@postDelayed
                        }

                        val vendorRect = Rect()
                        vendorNode.getBoundsInScreen(vendorRect)

                        Log.d(TAG, "VENDOR BOUNDS = $vendorRect")

                        var rowNode: AccessibilityNodeInfo = vendorNode
                        var parent = vendorNode.parent

                        while (parent != null) {

                            val parentRect = Rect()
                            parent.getBoundsInScreen(parentRect)

                            Log.d(
                                TAG,
                                "PARENT class=${parent.className} " +
                                        "id=${parent.viewIdResourceName} " +
                                        "bounds=$parentRect"
                            )

                            /*
                             * Cerchiamo il contenitore orizzontale della singola riga.
                             * Deve essere più largo del vendor_textview, ma non alto
                             * quanto l'intera schermata o la RecyclerView.
                             */
                            if (
                                parentRect.width() > vendorRect.width() &&
                                parentRect.height() in 40..250
                            ) {
                                rowNode = parent
                            }

                            /*
                             * Evitiamo di risalire fino al ViewPager o alla Activity.
                             */
                            if (parentRect.height() > 250) {
                                break
                            }

                            parent = parent.parent
                        }

                        val rowRect = Rect()
                        rowNode.getBoundsInScreen(rowRect)

                        if (rowRect.isEmpty) {
                            Log.d(TAG, "BOUNDS RIGA NON VALIDI")
                            return@postDelayed
                        }

                        val x = rowRect.right - 40f
                        val y = rowRect.exactCenterY()

                        Log.d(TAG, "========== GESTURE TEST ==========")
                        Log.d(TAG, "VENDOR = $vendorRect")
                        Log.d(TAG, "ROW COMPLETA = $rowRect")
                        Log.d(TAG, "TAP x=$x y=$y")

                        hideProductInfoPopup {
                            tap(x, y) {

                                Log.d(TAG, "GESTURE COMPLETATA")
                                restoreProductInfoPopup()

                                handler.postDelayed({

                                    val popupRoot = rootInActiveWindow

                                    if (popupRoot == null) {

                                        Log.d(
                                            TAG,
                                            "ROOT POPUP NON DISPONIBILE"
                                        )

                                        return@postDelayed
                                    }

                                    val taxablePrice =
                                        readTextById(
                                            popupRoot,
                                            "it.duebit.due:id/imponibile"
                                        )

                                    val vatRate =
                                        readTextById(
                                            popupRoot,
                                            "it.duebit.due:id/aliquota_iva"
                                        )

                                    Log.d(
                                        TAG,
                                        "IMPONIBILE POPUP = $taxablePrice"
                                    )

                                    Log.d(
                                        TAG,
                                        "IVA POPUP = $vatRate"
                                    )

                                    currentProductInfo =
                                        currentProductInfo?.copy(
                                            taxablePrice = taxablePrice ?: "",
                                            vatRate = vatRate ?: ""
                                        )

                                    Log.d(
                                        TAG,
                                        "IMPONIBILE SALVATO = ${currentProductInfo?.taxablePrice}"
                                    )

                                    Log.d(
                                        TAG,
                                        "IVA SALVATA = ${currentProductInfo?.vatRate}"
                                    )

                                    publishProductInfo(
                                        showPopup = false,
                                        workflowCompleted = false
                                    )

                                    handler.postDelayed({

                                        hideProductInfoPopup {
                                            val popupClosed =
                                                closePublicPricePopup()

                                            Log.d(
                                                TAG,
                                                "POPUP LISTINO CHIUSO = $popupClosed"
                                            )

                                            restoreProductInfoPopup()

                                            if (!popupClosed) {
                                                return@hideProductInfoPopup
                                            }

                                            /*
                                             * Attendo che il popup sia completamente scomparso
                                             * prima di aprire la scheda GIACENZA.
                                             */
                                            handler.postDelayed({

                                                hideProductInfoPopup {
                                                    val stockTabOpened =
                                                        clickStockTab()

                                                    Log.d(
                                                        TAG,
                                                        "APRO GIACENZA = $stockTabOpened"
                                                    )

                                                    restoreProductInfoPopup()

                                                    if (!stockTabOpened) {
                                                        return@hideProductInfoPopup
                                                    }

                                                    /*
                                                     * Attendo il caricamento della scheda GIACENZA.
                                                     */
                                                    handler.postDelayed({

                                                        val stockRoot =
                                                            rootInActiveWindow

                                                        val stock =
                                                            productInfoReader.readStock(stockRoot)

                                                        Log.d(
                                                            TAG,
                                                            "GIACENZA LETTA = $stock"
                                                        )

                                                        currentProductInfo =
                                                            currentProductInfo?.copy(
                                                                stock = stock ?: ""
                                                            )

                                                        Log.d(
                                                            TAG,
                                                            "========== PRODUCT INFO COMPLETO =========="
                                                        )

                                                        Log.d(
                                                            TAG,
                                                            "CODICE = ${currentProductInfo?.articleCode}"
                                                        )

                                                        Log.d(
                                                            TAG,
                                                            "EAN = ${currentProductInfo?.barcode}"
                                                        )

                                                        Log.d(
                                                            TAG,
                                                            "DESCRIZIONE = ${currentProductInfo?.description}"
                                                        )

                                                        Log.d(
                                                            TAG,
                                                            "IMPONIBILE = ${currentProductInfo?.taxablePrice}"
                                                        )

                                                        Log.d(
                                                            TAG,
                                                            "IVA = ${currentProductInfo?.vatRate}"
                                                        )

                                                        Log.d(
                                                            TAG,
                                                            "PREZZO PUBBLICO = ${currentProductInfo?.publicPrice}"
                                                        )

                                                        Log.d(
                                                            TAG,
                                                            "ANNO = ${currentProductInfo?.year}"
                                                        )

                                                        Log.d(
                                                            TAG,
                                                            "STAGIONE = ${currentProductInfo?.season}"
                                                        )

                                                        Log.d(
                                                            TAG,
                                                            "GIACENZA = ${currentProductInfo?.stock}"
                                                        )

                                                        Log.d(
                                                            TAG,
                                                            "=========================================="
                                                        )
                                                        ProductInfoStore.current = currentProductInfo

                                                        Log.d(
                                                            TAG,
                                                            "PRODUCT INFO SALVATO NELLO STORE"
                                                        )

                                                        publishProductInfo(
                                                            showPopup = false,
                                                            workflowCompleted = true
                                                        )

                                                        finishProductWorkflow()
                                                    }, 650)
                                                }
                                            }, 300)
                                        }

                                    }, 200)
                                }, 400)
                            }
                        }
                    }, 300)


                    // ==========================
                    // APERTURA GIACENZA
                    // ==========================

                    /*

                    handler.postDelayed({

                      val openedStock =
                          clickStockTab()

                      Log.d(
                          TAG,
                          "APRO GIACENZA = $openedStock"
                      )


                      if (openedStock) {

                          handler.postDelayed({

                              val rootStock =
                                  rootInActiveWindow


                              val stock =
                                  productInfoReader.readStock(rootStock)


                              Log.d(
                                  TAG,
                                  "GIACENZA LETTA = $stock"
                              )


                              currentProductInfo =
                                  currentProductInfo?.copy(
                                      stock = stock ?: ""
                                  )


                              Log.d(
                                  TAG,
                                  "========== PRODUCT INFO COMPLETO =========="
                              )

                              Log.d(
                                  TAG,
                                  "CODICE = ${currentProductInfo?.articleCode}"
                              )

                              Log.d(
                                  TAG,
                                  "EAN = ${currentProductInfo?.barcode}"
                              )

                              Log.d(
                                  TAG,
                                  "DESCRIZIONE = ${currentProductInfo?.description}"
                              )

                              Log.d(
                                  TAG,
                                  "IMPONIBILE = ${currentProductInfo?.taxablePrice}"
                              )

                              Log.d(
                                  TAG,
                                  "IVA = ${currentProductInfo?.vatRate}"
                              )

                              Log.d(
                                  TAG,
                                  "PREZZO PUBBLICO = ${currentProductInfo?.publicPrice}"
                              )

                              Log.d(
                                  TAG,
                                  "ANNO = ${currentProductInfo?.year}"
                              )

                              Log.d(
                                  TAG,
                                  "STAGIONE = ${currentProductInfo?.season}"
                              )

                              Log.d(
                                  TAG,
                                  "GIACENZA = ${currentProductInfo?.stock}"
                              )

                              Log.d(
                                  TAG,
                                  "=========================================="
                              )
                    ProductInfoStore.current = currentProductInfo

                    Log.d(
                        TAG,
                        "PRODUCT INFO SALVATO NELLO STORE"
                    )


                          }, 1500)



                      }


                    }, 1000)

                     */

                }, 700)

            }, 800)


            return


        }


        retryReadProductData(attempt)
    }


    private fun hideProductInfoPopup(onHidden: () -> Unit) {
        startService(
            Intent(this, OverlayService::class.java).apply {
                action =
                    OverlayService.ACTION_ENABLE_PRODUCT_INFO_TOUCH_THROUGH
            }
        )

        Log.d(TAG, "RICHIESTA TOUCH THROUGH POPUP")

        /*
         * Concedo a WindowManager il tempo di applicare
         * FLAG_NOT_TOUCHABLE prima di inviare il gesto a Due Retail.
         * La finestra resta sempre visibile.
         */
        handler.postDelayed(onHidden, 120)
    }

    private fun restoreProductInfoPopup(delayMs: Long = 120L) {
        handler.postDelayed({
            startService(
                Intent(this, OverlayService::class.java).apply {
                    action =
                        OverlayService.ACTION_DISABLE_PRODUCT_INFO_TOUCH_THROUGH
                }
            )

            Log.d(TAG, "RICHIESTA POPUP OPACO")
        }, delayMs)
    }

    private fun publishProductInfo(
        showPopup: Boolean,
        workflowCompleted: Boolean
    ) {
        val productInfo = currentProductInfo ?: return

        ProductInfoStore.current = productInfo

        val popupIntent = Intent(
            this,
            OverlayService::class.java
        ).apply {
            action = if (showPopup) {
                OverlayService.ACTION_SHOW_PRODUCT_INFO
            } else {
                OverlayService.ACTION_UPDATE_PRODUCT_INFO
            }

            putExtra(
                OverlayService.EXTRA_WORKFLOW_COMPLETED,
                workflowCompleted
            )
        }

        startService(popupIntent)

        Log.d(
            TAG,
            "PUBBLICAZIONE POPUP show=$showPopup completed=$workflowCompleted"
        )
    }


    /**
     * Conclude la lettura dell'articolo e torna alla finestrella
     * dei risultati di Due Retail Mobile, pronta per una nuova scansione.
     */
    private fun finishProductWorkflow() {

        handler.postDelayed({

            Log.d(
                TAG,
                "RITORNO ALLA FINESTRELLA RISULTATO"
            )

            val backResult =
                performGlobalAction(
                    GLOBAL_ACTION_BACK
                )

            Log.d(
                TAG,
                "GLOBAL_ACTION_BACK = $backResult"
            )

            /*
             * Azzera solamente lo stato del ciclo di scansione.
             * ProductInfoStore.current conserva l'ultimo articolo letto,
             * così il pulsante con lo scatolone può mostrarlo nuovamente.
             */
            lastInjected = ""
            lastWindowId = -1
            currentProductInfo = null

            Log.d(
                TAG,
                "WORKFLOW RESET - PRONTO PER NUOVA SCANSIONE"
            )

            Log.d(
                TAG,
                "POPUP PROGRESSIVO GIA AGGIORNATO - NESSUNA RIAPERTURA"
            )

        }, 250)
    }


    private fun closePublicPricePopup(): Boolean {

        val root = rootInActiveWindow

        if (root == null) {
            Log.d(TAG, "ROOT POPUP NON DISPONIBILE PER CHIUSURA")
            return false
        }

        val cancelNodes =
            root.findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/cancel_action"
            )

        val cancelButton = cancelNodes.firstOrNull()

        if (cancelButton == null) {
            Log.d(TAG, "PULSANTE ANNULLA NON TROVATO")
            return false
        }

        var node: AccessibilityNodeInfo? = cancelButton

        while (node != null) {

            if (node.isClickable) {

                val result =
                    node.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )

                Log.d(
                    TAG,
                    "CHIUSURA POPUP CON ANNULLA = $result"
                )

                return result
            }

            node = node.parent
        }

        Log.d(TAG, "NESSUN NODO CLICCABILE PER ANNULLA")

        return false
    }



    private fun readTextById(
        root: AccessibilityNodeInfo?,
        viewId: String
    ): String? {

        if (root == null) {
            return null
        }

        val nodes =
            root.findAccessibilityNodeInfosByViewId(viewId)

        val value =
            nodes.firstOrNull()
                ?.text
                ?.toString()
                ?.trim()

        Log.d(
            TAG,
            "READ ID=$viewId VALUE=$value"
        )

        return value
    }



    private fun scrollInfoDown(): Boolean {

        val root = rootInActiveWindow ?: return false

        val recyclers = root.findAccessibilityNodeInfosByViewId(
            "it.duebit.due:id/recycler_view"
        )

        if (recyclers.isEmpty()) {
            return false
        }

        val recycler = recyclers.first()

        Log.d(TAG, "SCROLL INFO")

        return recycler.performAction(
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        )
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