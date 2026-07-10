package com.scan2enter.model

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Responsabile della lettura delle informazioni
 * presenti nella scheda prodotto di Due Retail.
 */
class ProductInfoReader {

    fun readDescription(
        root: AccessibilityNodeInfo?
    ): String? {

        return findTextById(
            root,
            "it.duebit.due:id/descr_textview"
        )
    }

    fun readPrice(
        root: AccessibilityNodeInfo?
    ): String? {

        return null
    }

    fun readDepartment(
        root: AccessibilityNodeInfo?
    ): String? {

        return null
    }

    fun readVat(
        root: AccessibilityNodeInfo?
    ): String? {

        return null
    }

    fun readStock(
        root: AccessibilityNodeInfo?
    ): String? {

        return null
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
}