package com.scan2enter.accessibility

import android.content.Context
import android.os.Environment
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File

object UiDumpExporter {

    private const val TAG = "UiDumpExporter"

    fun export(
        context: Context,
        root: AccessibilityNodeInfo?
    ) {

        if (root == null) {
            Log.d(TAG, "Root null")
            return
        }

        val builder = StringBuilder()

        dump(root, 0, builder)

        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "ui_dump.txt"
        )

        file.writeText(builder.toString())

        Log.d(TAG, "Dump salvato in:")
        Log.d(TAG, file.absolutePath)
    }

    private fun dump(
        node: AccessibilityNodeInfo,
        level: Int,
        builder: StringBuilder
    ) {

        val indent = " ".repeat(level * 2)

        builder.append(indent)
        builder.append("CLASS=")
        builder.append(node.className)
        builder.append('\n')

        builder.append(indent)
        builder.append("ID=")
        builder.append(node.viewIdResourceName)
        builder.append('\n')

        builder.append(indent)
        builder.append("TEXT=")
        builder.append(node.text)
        builder.append('\n')

        builder.append(indent)
        builder.append("DESC=")
        builder.append(node.contentDescription)
        builder.append("\n\n")

        for (i in 0 until node.childCount) {

            node.getChild(i)?.let {

                dump(
                    it,
                    level + 1,
                    builder
                )
            }
        }
    }
}