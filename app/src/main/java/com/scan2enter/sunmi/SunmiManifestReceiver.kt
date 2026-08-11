package com.scan2enter.sunmi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scan2enter.overlay.OverlayService
import java.nio.charset.StandardCharsets

class SunmiManifestReceiver : BroadcastReceiver() {

    companion object {
        private const val SCAN_ACTION =
            "com.honeywell.tools.action.scan_result"

        private const val WORKFLOW_PREFS =
            "scan_workflow"

        private const val WORKFLOW_MODE_KEY =
            "mode"

        private const val MODE_INFO =
            "INFO"

        private const val MODE_LABELS_GODEX =
            "ETICHETTE_GODEX"

        private const val UI_STATE_PREFS =
            "scan_ui_state"

        private const val UI_SCREEN_KEY =
            "current_screen"

        private const val SCREEN_HOME =
            "HOME"

        private const val SCREEN_SESSION =
            "SESSIONE"
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != SCAN_ACTION) {
            return
        }

        val fromText =
            intent.getStringExtra(
                "barcode_data"
            )?.trim().orEmpty()

        val fromBytes =
            intent.getByteArrayExtra(
                "source_byte"
            )?.let {
                String(
                    it,
                    StandardCharsets.UTF_8
                ).trim()
            }.orEmpty()

        val barcode =
            fromText.ifBlank {
                fromBytes
            }

        if (barcode.isBlank()) {
            return
        }

        val appContext =
            context.applicationContext

        val currentScreen =
            appContext
                .getSharedPreferences(
                    UI_STATE_PREFS,
                    Context.MODE_PRIVATE
                )
                .getString(
                    UI_SCREEN_KEY,
                    SCREEN_HOME
                )
                ?: SCREEN_HOME

        val workflowMode =
            appContext
                .getSharedPreferences(
                    WORKFLOW_PREFS,
                    Context.MODE_PRIVATE
                )
                .getString(
                    WORKFLOW_MODE_KEY,
                    MODE_INFO
                )
                ?: MODE_INFO

        Log.d(
            "Scan2Enter",
            "SUNMI MANIFEST RECEIVER barcode=$barcode " +
                    "screen=$currentScreen mode=$workflowMode"
        )

        /*
         * SESSIONE:
         * il receiver dedicato di SessionScreen gestisce già
         * l'accodamento diretto e la soppressione del popup.
         * Qui non dobbiamo duplicare la lettura.
         */
        if (currentScreen == SCREEN_SESSION) {
            return
        }

        /*
         * HOME + modalità INFO:
         * questa è una vera scansione laser, non un'apertura manuale.
         * Apriamo il popup e forziamo anche il feedback stock BLIP/BLOP.
         */
        if (
            currentScreen == SCREEN_HOME &&
            workflowMode == MODE_INFO
        ) {
            context.startService(
                Intent(
                    context,
                    OverlayService::class.java
                ).apply {
                    action =
                        OverlayService.ACTION_OPEN_CURRENT_ARTICLE

                    putExtra(
                        OverlayService.EXTRA_CURRENT_ARTICLE_BARCODE,
                        barcode
                    )

                    putExtra(
                        OverlayService.EXTRA_FORCE_STOCK_SOUND,
                        true
                    )
                }
            )

            return
        }

        if (workflowMode == MODE_LABELS_GODEX) {
            return
        }

        return
    }
}
