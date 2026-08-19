package com.scan2enter.zebra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scan2enter.overlay.OverlayService

class ZebraDataWedgeReceiver : BroadcastReceiver() {

    companion object {
        private const val SCAN_ACTION =
            "com.scan2enter.SCAN"

        private const val DATAWEDGE_DATA_STRING =
            "com.symbol.datawedge.data_string"

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

        val barcode =
            intent.getStringExtra(DATAWEDGE_DATA_STRING)
                ?.trim()
                .orEmpty()

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
            "ZEBRA DATAWEDGE RECEIVER barcode=$barcode " +
                    "screen=$currentScreen mode=$workflowMode"
        )

        /*
         * SESSIONE:
         * verrà gestita nel passo successivo da un receiver dedicato
         * dentro SessionScreen, come già avviene per Sunmi.
         *
         * Qui non dobbiamo duplicare la lettura.
         */
        if (currentScreen == SCREEN_SESSION) {
            return
        }

        /*
         * HOME + modalità INFO:
         * lettura hardware Zebra tramite DataWedge.
         * Riutilizziamo lo stesso ingresso già usato dal receiver Sunmi:
         * OverlayService carica l'articolo e mostra il popup.
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

                    putExtra(
                        OverlayService.EXTRA_SUPPRESS_AUTO_REOPEN_SCANNER,
                        true
                    )
                }
            )

            return
        }

        /*
         * GoDEX:
         * per ora non intercettiamo qui la scansione.
         * Il supporto Zebra specifico per GoDEX/A4 verrà aggiunto
         * dopo il test HOME, senza alterare i percorsi Sunmi esistenti.
         */
        if (workflowMode == MODE_LABELS_GODEX) {
            return
        }

        return
    }
}