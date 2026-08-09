package com.scan2enter.sunmi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class SunmiTestActivity : ComponentActivity() {

    private lateinit var textView: TextView

    private val receiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                val barcodeData =
                    intent
                        ?.getStringExtra("barcode_data")

                val data =
                    intent
                        ?.getStringExtra("data")

                val scannerData =
                    intent
                        ?.getStringExtra("scannerdata")

                runOnUiThread {
                    textView.text =
                        """
                        ACTION:
                        ${intent?.action}

                        barcode_data:
                        $barcodeData

                        data:
                        $data

                        scannerdata:
                        $scannerData
                        """.trimIndent()
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        textView =
            TextView(this).apply {
                textSize = 22f
                setPadding(
                    40,
                    80,
                    40,
                    40
                )

                text =
                    """
                    PRONTO

                    Premi il grilletto...
                    """.trimIndent()
            }

        setContentView(textView)
    }

    override fun onResume() {
        super.onResume()

        registerReceiver(
            receiver,
            IntentFilter(
                "com.honeywell.tools.action.scan_result"
            )
        )
    }

    override fun onPause() {
        runCatching {
            unregisterReceiver(receiver)
        }

        super.onPause()
    }
}