package com.scan2enter.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Utility per lavorare con l'albero Accessibility.
 */
object AccessibilityUtils {

    /**
     * Cerca il primo nodo che contiene il testo indicato.
     */
    fun findByText(
        root: AccessibilityNodeInfo?,
        text: String
    ): AccessibilityNodeInfo? {

        if (root == null) return null

        val result = root.findAccessibilityNodeInfosByText(text)

        return if (result.isNullOrEmpty()) null else result.first()
    }

    /**
     * Cerca il primo nodo tramite ViewId.
     */
    fun findByViewId(
        root: AccessibilityNodeInfo?,
        viewId: String
    ): AccessibilityNodeInfo? {

        if (root == null) return null

        val result = root.findAccessibilityNodeInfosByViewId(viewId)

        return if (result.isNullOrEmpty()) null else result.first()
    }

    /**
     * Legge il testo di un nodo.
     */
    fun getText(node: AccessibilityNodeInfo?): String {

        return node?.text?.toString()?.trim().orEmpty()
    }

    /**
     * Effettua il click sul nodo.
     */
    fun click(node: AccessibilityNodeInfo?): Boolean {

        return node?.performAction(
            AccessibilityNodeInfo.ACTION_CLICK
        ) ?: false
    }

    /**
     * Verifica se il nodo è cliccabile.
     */
    fun isClickable(node: AccessibilityNodeInfo?): Boolean {

        return node?.isClickable == true
    }

    /**
     * Cerca ricorsivamente un nodo tramite ViewId.
     * Utile quando findAccessibilityNodeInfosByViewId()
     * non restituisce risultati.
     */
    fun findByViewIdRecursive(
        node: AccessibilityNodeInfo?,
        viewId: String
    ): AccessibilityNodeInfo? {

        if (node == null) return null

        if (node.viewIdResourceName == viewId) {
            return node
        }

        for (i in 0 until node.childCount) {

            val result = findByViewIdRecursive(
                node.getChild(i),
                viewId
            )

            if (result != null) {
                return result
            }
        }

        return null
    }
}