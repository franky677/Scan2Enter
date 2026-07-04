package com.scan2enter.scanner

import android.content.Context
import android.content.Intent
import android.util.Log

object BarcodeIntentSender {

    private const val TAG = "Scan2Enter"

    fun send(context: Context, barcode: String) {

        try {

            val intent = Intent("com.symbol.datawedge.ACTION")

            intent.addCategory(Intent.CATEGORY_DEFAULT)

            intent.putExtra(
                "com.symbol.datawedge.data_string",
                barcode
            )

            intent.putExtra(
                "com.symbol.datawedge.label_type",
                "EAN13"
            )

            intent.putExtra(
                "com.symbol.datawedge.source",
                "scanner"
            )

            context.sendBroadcast(intent)

            Log.d(TAG, "Broadcast Zebra inviato")

        } catch (e: Exception) {

            Log.e(TAG, "Errore broadcast", e)

        }
    }
}