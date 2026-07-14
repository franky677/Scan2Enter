package com.scan2enter.model

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

/**
 * Responsabile della lettura delle informazioni
 * presenti nella scheda prodotto di Due Retail.
 */
class ProductInfoReader {
    companion object {
        private const val TAG = "Scan2Enter"
    }
    fun readDescription(
        root: AccessibilityNodeInfo?
    ): String? {

        return findTextById(
            root,
            "it.duebit.due:id/descr_textview"
        )
    }
    fun openUbicazionePopup(
        root: AccessibilityNodeInfo?
    ): Boolean {

        if (root == null) {
            return false
        }

        val keys = root.findAccessibilityNodeInfosByViewId(
            "it.duebit.due:id/key_textview"
        )

        for (key in keys) {

            if (key.text?.toString() == "Ubicazione") {

                Log.d(
                    TAG,
                    "TROVATA UBICAZIONE"
                )

                var parent = key.parent

                while (parent != null) {

                    val buttons =
                        parent.findAccessibilityNodeInfosByViewId(
                            "it.duebit.due:id/edit_button"
                        )

                    if (buttons.isNotEmpty()) {

                        Log.d(
                            TAG,
                            "TROVATO EDIT BUTTON UBICAZIONE"
                        )

                        return buttons.first()
                            .performAction(
                                AccessibilityNodeInfo.ACTION_CLICK
                            )
                    }

                    parent = parent.parent
                }
            }
        }

        return false
    }
    private fun readPrice(root: AccessibilityNodeInfo?): String? {

        if (root == null) {
            return null
        }

        val vendorNode = findNodeByViewIdAndText(

            root,
            "vendor_textview",
            "3-AL PUBBLICO"
        ) ?: return null
        dumpAllTexts(vendorNode.parent?.parent)
        Log.d(
            "Scan2Enter",
            "VENDOR FOUND = ${vendorNode.text}"
        )

        for (i in 0 until (vendorNode.parent?.childCount ?: 0)) {
            val child = vendorNode.parent?.getChild(i)
            Log.d(
                "Scan2Enter",
                "PARENT CHILD $i = ${child?.text} id=${child?.viewIdResourceName}"
            )
        }
        var p = vendorNode.parent

        while (p != null) {

            Log.d(
                "Scan2Enter",
                "ANCESTOR class=${p.className} childCount=${p.childCount}"
            )

            for (i in 0 until p.childCount) {
                val child = p.getChild(i)

                Log.d(
                    "Scan2Enter",
                    "  CHILD $i text=${child?.text} id=${child?.viewIdResourceName}"
                )
            }

            p = p.parent
        }
        Log.d(
            "Scan2Enter",
            "VENDOR parent = ${vendorNode.parent?.className} ${vendorNode.parent?.text}"
        )

        val row = vendorNode.parent ?: return null

        val priceNode = findNodeByViewId(
            row,
            "price_textview"
        ) ?: return null

        return priceNode.text
            ?.toString()
            ?.trim()
    }
    private fun dumpAllTexts(
        node: AccessibilityNodeInfo?,
        level: Int = 0
    ) {

        if (node == null)
            return

        if (node.text != null) {
            Log.d(
                "Scan2Enter",
                "TEXT=${
                    node.text
                } ID=${
                    node.viewIdResourceName
                }"
            )
        }

        for (i in 0 until node.childCount) {
            dumpAllTexts(
                node.getChild(i),
                level + 1
            )
        }
    }
    fun readYear(
        root: AccessibilityNodeInfo?
    ): String? {

        return findValueByKey(
            root,
            "Anno"
        )
    }

    fun readSeason(
        root: AccessibilityNodeInfo?
    ): String? {

        return findValueByKey(
            root,
            "Stagione"
        )
    }

    fun readLocations(
        root: AccessibilityNodeInfo?
    ): List<String> {

        if (root == null) {
            return emptyList()
        }

        val result = mutableListOf<String>()

        val nodes = root.findAccessibilityNodeInfosByViewId(
            "it.duebit.due:id/textview_group_name"
        )

        for (node in nodes) {

            val text = node.text?.toString()?.trim()

            if (!text.isNullOrEmpty()) {
                result.add(text)
            }
        }

        return result
    }

    private fun clickPublicPriceEditButton(
        root: AccessibilityNodeInfo?
    ): Boolean {

        if (root == null) {
            return false
        }

        val vendors = root.findAccessibilityNodeInfosByViewId(
            "it.duebit.due:id/vendor_textview"
        )

        for (vendor in vendors) {

            if (vendor.text?.toString() != "3-AL PUBBLICO") {
                continue
            }

            var row = vendor.parent
            Log.d(TAG, "VENDOR clickable = ${vendor.isClickable}")
            Log.d(TAG, "ROW clickable = ${row?.isClickable}")
            Log.d(TAG, "ROW enabled = ${row?.isEnabled}")
            Log.d(TAG, "ROW class = ${row?.className}")
            Log.d(TAG, "ROW actions = ${row?.actionList}")

            var node = row

            while (node != null) {

                Log.d(
                    TAG,
                    "NODE class=${node.className} clickable=${node.isClickable} actions=${node.actionList}"
                )

                node = node.parent
            }
            while (row != null) {

                val buttons = row.findAccessibilityNodeInfosByViewId(
                    "it.duebit.due:id/edit_button"
                )

                if (buttons.isNotEmpty()) {

                    Log.d(
                        TAG,
                        "TROVATA MATITA PER: 3-AL PUBBLICO"
                    )

                    val button = buttons.first()
                    val rect = android.graphics.Rect()
                    button.getBoundsInScreen(rect)

                    Log.d(
                        TAG,
                        "EDIT BUTTON bounds = $rect"
                    )
                    Log.d(
                        TAG,
                        "ROW bounds = ${
                            android.graphics.Rect().also {
                                row.getBoundsInScreen(it)
                            }
                        }"
                    )

                    val parent = button.parent

                    if (parent != null && parent.isClickable) {

                        Log.d(
                            TAG,
                            "CLICK RIGA LISTINO PUBBLICO"
                        )

                        return parent.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )
                    }

                    return button.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )
                }

                row = row.parent
            }
        }

        return false
    }
    fun openPublicPricePopup(
        root: AccessibilityNodeInfo?
    ): Boolean {

        return clickPublicPriceEditButton(root)
    }
    private fun findNodeByText(
        root: AccessibilityNodeInfo?,
        text: String
    ): AccessibilityNodeInfo? {

        if (root == null) {
            return null
        }

        if (root.text?.toString() == text) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNodeByText(child, text)

            if (result != null) {
                return result
            }
        }

        return null
    }
    private fun findParentWithViewId(
        node: AccessibilityNodeInfo?,
        viewIdSuffix: String
    ): AccessibilityNodeInfo? {

        var current = node?.parent

        while (current != null) {

            val found = findNodeByViewId(
                current,
                viewIdSuffix
            )

            if (found != null) {
                return current
            }

            current = current.parent
        }

        return null
    }
    private fun findNodeByViewId(
        root: AccessibilityNodeInfo?,
        viewIdSuffix: String
    ): AccessibilityNodeInfo? {

        if (root == null) {
            return null
        }

        root.viewIdResourceName?.let {
            if (it.endsWith(viewIdSuffix)) {
                return root
            }
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue

            val result = findNodeByViewId(
                child,
                viewIdSuffix
            )

            if (result != null) {
                return result
            }
        }

        return null
    }
    private fun findNodeByViewIdAndText(
        root: AccessibilityNodeInfo?,
        viewId: String,
        text: String
    ): AccessibilityNodeInfo? {

        if (root == null) {
            return null
        }

        if (
            root.viewIdResourceName?.endsWith(viewId) == true &&
            root.text?.toString() == text
        ) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue

            val result = findNodeByViewIdAndText(
                child,
                viewId,
                text
            )

            if (result != null) {
                return result
            }
        }

        return null
    }
    fun readStock(
        root: AccessibilityNodeInfo?
    ): String? {

        return findTextById(
            root,
            "it.duebit.due:id/qty_stock_textview"
        )
    }
    fun readBarcode(
        root: AccessibilityNodeInfo?
    ): String? {

        return findTextById(
            root,
            "it.duebit.due:id/barcode_textview"
        )
    }
    fun readDepartment(
        root: AccessibilityNodeInfo?
    ): String? {

        return findValueByKey(
            root,
            "Reparto"
        )
    }

    fun readVat(
        root: AccessibilityNodeInfo?
    ): String? {

        return null
    }
    private fun dumpVatNodes(
        node: AccessibilityNodeInfo?
    ) {

        if (node == null)
            return

        val text = node.text?.toString()

        if (text != null) {

            if (
                text.contains("IVA", true) ||
                text.contains("%") ||
                text.contains("22")
            ) {
                Log.d(
                    "Scan2Enter",
                    "VAT TEXT=$text ID=${node.viewIdResourceName}"
                )
            }
        }

        for (i in 0 until node.childCount) {
            dumpVatNodes(
                node.getChild(i)
            )
        }
    }

    fun buildProductInfo(
        root: AccessibilityNodeInfo?
    ): ProductInfo {

        dumpVatNodes(root)

        return ProductInfo(
            description = readDescription(root).orEmpty(),
            barcode = readBarcode(root).orEmpty(),
            season = readSeason(root).orEmpty(),
            year = readYear(root).orEmpty(),
            publicPrice = readPrice(root).orEmpty(),
            stock = readStock(root).orEmpty()
        )
    }

    private fun findTextById(
        root: AccessibilityNodeInfo?,
        viewId: String
    ): String? {

        if (root == null)
            return null

        val nodes = root.findAccessibilityNodeInfosByViewId(
            viewId
        )

        if (nodes.isEmpty())
            return null

        return nodes.first().text?.toString()
    }
    private fun findValueByKey(
        root: AccessibilityNodeInfo?,
        key: String
    ): String? {

        if (root == null)
            return null

        val keyNodes = root.findAccessibilityNodeInfosByViewId(
            "it.duebit.due:id/key_textview"
        )

        val valueNodes = root.findAccessibilityNodeInfosByViewId(
            "it.duebit.due:id/value_textview"
        )

        val count = minOf(keyNodes.size, valueNodes.size)

        for (i in 0 until count) {

            if (keyNodes[i].text?.toString() == key) {
                return valueNodes[i].text?.toString()
            }
        }

        return null
    }
}