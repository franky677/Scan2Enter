package com.scan2enter.detector

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object RetailScreenDetector {

    private const val TAG = "ScreenInspector"

    fun detect(root: AccessibilityNodeInfo?): RetailScreen {

        if (root == null) {
            Log.d(TAG, "Root = NULL")
            return RetailScreen.UNKNOWN
        }

        Log.d(TAG, "----------------------------")
        Log.d(TAG, "Package = ${root.packageName}")
        Log.d(TAG, "Class   = ${root.className}")

        dump(root, 0)

        return RetailScreen.UNKNOWN
    }

    private fun dump(
        node: AccessibilityNodeInfo,
        level: Int
    ) {

        val indent = " ".repeat(level * 2)

        val text = node.text?.toString()?.trim()

        val desc = node.contentDescription?.toString()?.trim()

        if (!text.isNullOrEmpty()) {
            Log.d(TAG, "${indent}TEXT = $text")
        }

        if (!desc.isNullOrEmpty()) {
            Log.d(TAG, "${indent}DESC = $desc")
        }

        for (i in 0 until node.childCount) {

            node.getChild(i)?.let {

                dump(it, level + 1)
            }
        }
    }
}