package com.scan2enter.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.scan2enter.data.ScanStorage
import com.scan2enter.overlay.OverlayService
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

        private const val WORKFLOW_PREFS = "scan_workflow"
        private const val WORKFLOW_MODE_KEY = "mode"

        private const val MODE_HOME = "HOME"
        private const val MODE_INFO = "INFO"
        private const val MODE_ARTICLE_DETAIL = "ARTICLE_DETAIL"
        private const val MODE_FAST_PACKAGE = "COLLO_VELOCE"
        private const val MODE_LABELS = "ETICHETTE"
        private const val MODE_UNKNOWN = "UNKNOWN"

        private var overlayVisible = false

        private var lastInjected = ""

        private var lastWindowId = -1
    }

    private val handler = Handler(Looper.getMainLooper())
    private val productInfoReader = ProductInfoReader()
    private var currentProductInfo: ProductInfo? = null

    private val overlayCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                OverlayService.ACTION_PREPARE_SCANNER -> {
                    Log.d(TAG, "RICHIESTA PREPARAZIONE SCANNER")
                    prepareAndOpenScanner()
                }

                OverlayService.ACTION_REQUEST_CURRENT_ARTICLE -> {
                    Log.d(TAG, "RICHIESTA LETTURA ARTICOLO APERTO")
                    readCurrentArticleBarcode()
                }
            }
        }
    }
    override fun onServiceConnected() {

        Log.d(TAG, "Accessibility connessa")

        val filter = IntentFilter().apply {
            addAction(OverlayService.ACTION_PREPARE_SCANNER)
            addAction(OverlayService.ACTION_REQUEST_CURRENT_ARTICLE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                overlayCommandReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                overlayCommandReceiver,
                filter
            )
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(overlayCommandReceiver)
        } catch (_: IllegalArgumentException) {
        }

        super.onDestroy()
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

    private fun readCurrentArticleBarcode(
        attempt: Int = 0
    ) {
        val root = rootInActiveWindow

        if (root?.packageName?.toString() != DUE_PACKAGE) {
            sendCurrentArticleError(
                "Apri prima una scheda articolo"
            )
            return
        }

        val title = root
            .findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/code_textview"
            )
            .firstOrNull()
            ?.text
            ?.toString()
            ?.trim()
            .orEmpty()

        if (!title.equals("Informazioni articolo", ignoreCase = true)) {
            sendCurrentArticleError(
                "Apri prima una scheda articolo"
            )
            return
        }

        val barcode = root
            .findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/barcode_textview"
            )
            .asSequence()
            .mapNotNull { node ->
                node.text
                    ?.toString()
                    ?.filter(Char::isDigit)
                    ?.takeIf { it.length in 8..14 }
            }
            .firstOrNull()

        if (barcode != null) {
            startService(
                Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_OPEN_CURRENT_ARTICLE
                    putExtra(
                        OverlayService.EXTRA_CURRENT_ARTICLE_BARCODE,
                        barcode
                    )
                    putExtra(
                        OverlayService.EXTRA_CURRENT_ARTICLE_YEAR,
                        productInfoReader.readYear(root).orEmpty()
                    )
                    putExtra(
                        OverlayService.EXTRA_CURRENT_ARTICLE_SEASON,
                        productInfoReader.readSeason(root).orEmpty()
                    )
                    putExtra(
                        OverlayService.EXTRA_CURRENT_ARTICLE_LOCATION,
                        productInfoReader.readLocation(root).orEmpty()
                    )
                }
            )

            Log.d(
                TAG,
                "BARCODE ARTICOLO APERTO = $barcode"
            )
            return
        }

        if (attempt >= 3) {
            sendCurrentArticleError(
                "Barcode articolo non trovato"
            )
            return
        }

        val recycler = root
            .findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/recycler_view"
            )
            .firstOrNull { it.isScrollable }
            ?: root.findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/recycler_view"
            ).firstOrNull()

        val scrolled = recycler?.performAction(
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        ) == true

        Log.d(
            TAG,
            "RICERCA BARCODE attempt=$attempt scroll=$scrolled"
        )

        handler.postDelayed({
            readCurrentArticleBarcode(attempt + 1)
        }, 300)
    }

    private fun sendCurrentArticleError(message: String) {
        startService(
            Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW_SCAN_ERROR
                putExtra(
                    OverlayService.EXTRA_SCAN_ERROR_MESSAGE,
                    message
                )
            }
        )

        Log.d(
            TAG,
            "ARTICOLO APERTO NON DISPONIBILE: $message"
        )
    }

    /**
     * Apre lo scanner da qualunque schermata di Due Retail.
     *
     * Il lookup articolo avviene ora esclusivamente tramite API, quindi non
     * serve più portare automaticamente Due Retail nella schermata
     * "Informazioni". In questo modo la Dock funziona anche dentro procedure
     * come Stampa etichette e Collo veloce.
     */
    private fun prepareAndOpenScanner() {

        val root = rootInActiveWindow
        val currentPackage = root?.packageName?.toString()

        handler.removeCallbacksAndMessages(null)

        if (currentPackage == packageName) {
            Log.d(TAG, "HOME SCAN2ENTER - AVVIO DUE RETAIL")

            val launchIntent = packageManager
                .getLaunchIntentForPackage(DUE_PACKAGE)

            if (launchIntent == null) {
                Log.d(TAG, "IMPOSSIBILE AVVIARE DUE RETAIL")
                return
            }

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            waitForDueRetailAndPrepare()
            return
        }

        if (currentPackage != DUE_PACKAGE || root == null) {
            Log.d(TAG, "DUE RETAIL NON ATTIVO - SCANNER NON APERTO")
            return
        }

        prepareScannerFromDueRetail(root)
    }

    private fun waitForDueRetailAndPrepare(attempt: Int = 0) {
        val root = rootInActiveWindow

        if (root?.packageName?.toString() == DUE_PACKAGE) {
            Log.d(TAG, "DUE RETAIL ATTIVO DOPO AVVIO")
            prepareScannerFromDueRetail(root)
            return
        }

        if (attempt >= 40) {
            Log.d(TAG, "TIMEOUT AVVIO DUE RETAIL")
            return
        }

        handler.postDelayed({
            waitForDueRetailAndPrepare(attempt + 1)
        }, 100)
    }

    private fun prepareScannerFromDueRetail(root: AccessibilityNodeInfo) {
        when (detectCurrentScanMode(root)) {

            MODE_HOME -> {
                saveCurrentScanMode(MODE_INFO)
                Log.d(TAG, "HOME RICONOSCIUTA - APRO INFORMAZIONI")

                val clicked = clickInformationHomeTile(root)
                Log.d(TAG, "CLICK INFORMAZIONI DALLA HOME = $clicked")

                if (clicked) {
                    waitForInformationSearchScreen()
                }
            }

            MODE_ARTICLE_DETAIL -> {
                saveCurrentScanMode(MODE_INFO)
                Log.d(TAG, "SCHEDA ARTICOLO RICONOSCIUTA - TORNO ALLA RICERCA")

                val backResult = performGlobalAction(GLOBAL_ACTION_BACK)
                Log.d(TAG, "BACK DA SCHEDA ARTICOLO = $backResult")

                if (backResult) {
                    waitForInformationSearchScreen()
                }
            }

            MODE_FAST_PACKAGE -> {
                saveCurrentScanMode(MODE_FAST_PACKAGE)
                Log.d(TAG, "COLLO VELOCE RICONOSCIUTO - APERTURA SCANNER")
                requestScannerOpen()
            }

            MODE_LABELS -> {
                saveCurrentScanMode(MODE_LABELS)
                Log.d(TAG, "GESTIONE ETICHETTE RICONOSCIUTA - APERTURA SCANNER")
                requestScannerOpen()
            }

            MODE_INFO -> {
                saveCurrentScanMode(MODE_INFO)
                Log.d(TAG, "INFORMAZIONI RICONOSCIUTE - APERTURA SCANNER")
                requestScannerOpen()
            }

            else -> {
                Log.d(TAG, "SCHERMATA NON RICONOSCIUTA - PROVO A TORNARE INDIETRO")

                val backResult = performGlobalAction(GLOBAL_ACTION_BACK)
                Log.d(TAG, "BACK DI RECUPERO = $backResult")

                if (backResult) {
                    handler.postDelayed({
                        prepareAndOpenScanner()
                    }, 350)
                }
            }
        }
    }

    private fun detectCurrentScanMode(
        root: AccessibilityNodeInfo
    ): String {

        val title = root
            .findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/code_textview"
            )
            .firstOrNull()
            ?.text
            ?.toString()
            ?.trim()
            .orEmpty()

        /*
         * La schermata Informazioni non espone un titolo in code_textview.
         * La riconosco quindi tramite la combinazione di tre elementi
         * presenti contemporaneamente nel relativo pannello di ricerca.
         */
        val hasInfoSearchField =
            root.findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/search_text_edittext"
            ).isNotEmpty()

        val hasInfoBarcodeButton =
            root.findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/search_barcode_imagebutton"
            ).isNotEmpty()

        val hasInfoRecyclerView =
            root.findAccessibilityNodeInfosByViewId(
                "it.duebit.due:id/recycler_view"
            ).isNotEmpty()

        val isInformationScreen =
            hasInfoSearchField &&
                    hasInfoBarcodeButton &&
                    hasInfoRecyclerView

        Log.d(TAG, "TITOLO SCHERMATA DUE RETAIL = $title")
        Log.d(
            TAG,
            "RICONOSCIMENTO INFO " +
                    "searchField=$hasInfoSearchField " +
                    "barcodeButton=$hasInfoBarcodeButton " +
                    "recyclerView=$hasInfoRecyclerView " +
                    "result=$isInformationScreen"
        )

        return when {
            title.equals("Due Retail Mobile", ignoreCase = true) -> MODE_HOME
            title.equals("Informazioni articolo", ignoreCase = true) -> MODE_ARTICLE_DETAIL
            title.startsWith("Collo: COLLO VELOCE", ignoreCase = true) -> MODE_FAST_PACKAGE
            title.equals("Nuova etichetta", ignoreCase = true) -> MODE_LABELS
            isInformationScreen -> MODE_INFO
            else -> MODE_UNKNOWN
        }
    }

    private fun saveCurrentScanMode(mode: String) {
        applicationContext
            .getSharedPreferences(WORKFLOW_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(WORKFLOW_MODE_KEY, mode)
            .apply()

        Log.d(TAG, "MODALITA SCANSIONE SALVATA = $mode")
    }

    private fun loadCurrentScanMode(): String {
        return applicationContext
            .getSharedPreferences(WORKFLOW_PREFS, Context.MODE_PRIVATE)
            .getString(WORKFLOW_MODE_KEY, MODE_INFO)
            ?: MODE_INFO
    }

    /**
     * Cerca il riquadro "Informazioni" nella home di Due Retail.
     *
     * In questa schermata non è presente search_product_layout:
     * il testo "Informazioni" è contenuto nella tessera verde iniziale.
     * Provo prima ACTION_CLICK sul nodo o su un suo genitore cliccabile;
     * se Due Retail non espone la proprietà clickable, uso un tap al centro
     * del contenitore della tessera.
     */
    private fun clickInformationHomeTile(
        root: AccessibilityNodeInfo
    ): Boolean {

        fun findInformationNode(
            node: AccessibilityNodeInfo?
        ): AccessibilityNodeInfo? {

            if (node == null) {
                return null
            }

            val text =
                node.text?.toString()?.trim()

            val description =
                node.contentDescription?.toString()?.trim()

            if (
                text.equals("Informazioni", ignoreCase = true) ||
                description.equals("Informazioni", ignoreCase = true)
            ) {
                return node
            }

            for (index in 0 until node.childCount) {
                val result =
                    findInformationNode(node.getChild(index))

                if (result != null) {
                    return result
                }
            }

            return null
        }

        val informationNode =
            findInformationNode(root)

        if (informationNode == null) {
            Log.d(TAG, "TESTO INFORMAZIONI NON TROVATO")
            return false
        }

        Log.d(
            TAG,
            "INFORMAZIONI TROVATO " +
                    "id=${informationNode.viewIdResourceName} " +
                    "class=${informationNode.className}"
        )

        var node: AccessibilityNodeInfo? =
            informationNode

        var tapCandidate: AccessibilityNodeInfo =
            informationNode

        while (node != null) {

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            /*
             * Memorizzo il contenitore più ampio ma ancora compatibile
             * con una tessera della home, evitando di arrivare all'intera Activity.
             */
            if (
                !bounds.isEmpty &&
                bounds.width() >= 250 &&
                bounds.height() in 150..700
            ) {
                tapCandidate = node
            }

            if (node.isClickable) {

                val clicked =
                    node.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )

                Log.d(
                    TAG,
                    "CLICK CONTENITORE INFORMAZIONI = $clicked"
                )

                if (clicked) {
                    return true
                }
            }

            node = node.parent
        }

        val tapResult =
            tapNode(tapCandidate)

        Log.d(
            TAG,
            "TAP CONTENITORE INFORMAZIONI = $tapResult"
        )

        return tapResult
    }


    private fun waitForInformationSearchScreen(
        attempt: Int = 0
    ) {

        val root = rootInActiveWindow

        val dueRetailActive =
            root?.packageName?.toString() == DUE_PACKAGE

        val searchFieldReady =
            dueRetailActive && findEditable(root) != null

        Log.d(
            TAG,
            "ATTESA INFORMAZIONI attempt=$attempt ready=$searchFieldReady"
        )

        if (searchFieldReady) {
            /*
             * Alla prima apertura Due Retail espone il campo editabile prima
             * che la schermata Informazioni sia completamente stabilizzata.
             * Attendo quindi altri 500 ms e verifico nuovamente la modalità
             * prima di aprire CameraX. Le aperture successive da INFO restano
             * invece immediate perché passano direttamente da prepareAndOpenScanner().
             */
            handler.postDelayed({
                val stableRoot = rootInActiveWindow
                val stableInfo =
                    stableRoot?.packageName?.toString() == DUE_PACKAGE &&
                            detectCurrentScanMode(stableRoot) == MODE_INFO

                Log.d(TAG, "VERIFICA INFO STABILE = $stableInfo")

                if (stableInfo) {
                    requestScannerOpen()
                } else {
                    waitForInformationSearchScreen(attempt + 1)
                }
            }, 500)
            return
        }

        if (attempt >= 25) {
            Log.d(TAG, "TIMEOUT APERTURA SCHERMATA INFORMAZIONI")
            return
        }

        handler.postDelayed({
            waitForInformationSearchScreen(attempt + 1)
        }, 100)
    }

    private fun requestScannerOpen() {

        startService(
            Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_OPEN_SCANNER
            }
        )

        Log.d(TAG, "RICHIESTA APERTURA SCANNER")
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

                val currentMode = loadCurrentScanMode()

                Log.d(
                    TAG,
                    "Barcode click OK - MODALITA = $currentMode"
                )

                if (currentMode == MODE_INFO) {

                    /*
                     * In modalità INFO mi fermo volutamente dopo la ricerca.
                     * I dati del popup arrivano dall'API; Accessibility serve
                     * soltanto a lasciare visibile in Due Retail la lista con
                     * l'articolo appena letto, senza aprirlo automaticamente.
                     */
                    Log.d(
                        TAG,
                        "INFO - RICERCA ESEGUITA, LASCIO VISIBILE LA LISTA RISULTATI"
                    )

                } else {

                    Log.d(
                        TAG,
                        "ACCODAMENTO - attendo e clicco il primo risultato"
                    )

                    waitAndClickFirstQueueResult()
                }

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

    private fun waitAndClickFirstQueueResult(
        attempt: Int = 0
    ) {

        if (attempt < 3) {
            handler.postDelayed({
                waitAndClickFirstQueueResult(attempt + 1)
            }, 100)
            return
        }

        if (clickFirstResult()) {
            Log.d(TAG, "PRIMO RISULTATO CLICCATO - ARTICOLO ACCODATO")

            handler.postDelayed({
                lastInjected = ""
                lastWindowId = -1
                Log.d(TAG, "ACCODAMENTO RESET - PRONTO PER NUOVA SCANSIONE")
            }, 500)

            return
        }

        if (attempt >= 25) {
            Log.d(TAG, "TIMEOUT ATTESA RISULTATO DA ACCODARE")
            return
        }

        handler.postDelayed({
            waitAndClickFirstQueueResult(attempt + 1)
        }, 100)
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
                val location = productInfoReader.readLocation(root2)

                Log.d(TAG, "READ BARCODE = $barcode")
                Log.d(TAG, "READ YEAR = $year")
                Log.d(TAG, "READ SEASON = $season")
                Log.d(TAG, "READ LOCATION = $location")

                currentProductInfo = currentProductInfo?.copy(
                    barcode = barcode ?: currentProductInfo?.barcode.orEmpty(),
                    year = year ?: currentProductInfo?.year.orEmpty(),
                    season = season ?: currentProductInfo?.season.orEmpty(),
                    location = location ?: currentProductInfo?.location.orEmpty()
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
                            location = info.location.ifBlank {
                                previous?.location.orEmpty()
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

                                                        currentProductInfo?.let { completedProduct ->
                                                            ProductInfoStore.addToHistory(
                                                                completedProduct
                                                            )
                                                        }

                                                        Log.d(
                                                            TAG,
                                                            "PRODUCT INFO SALVATO NELLO STORE E NELLA CRONOLOGIA"
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
}