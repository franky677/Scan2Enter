package com.scan2enter.workflow

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.scan2enter.detector.RetailScreen
import com.scan2enter.detector.RetailScreenDetector

/**
 * Gestisce il workflow automatico delle informazioni articolo.
 *
 * Per ora riconosce solamente la schermata corrente.
 * Nei prossimi commit eseguirà le azioni automatiche.
 */
class ProductInfoWorkflow {

    fun start(root: AccessibilityNodeInfo?) {

        val screen = RetailScreenDetector.detect(root)

        when (screen) {

            RetailScreen.COLLO_VELOCE -> {
                Log.d("ProductWorkflow", "Collo Veloce")
            }

            RetailScreen.GESTIONE_ETICHETTE -> {
                Log.d("ProductWorkflow", "Gestione Etichette")
            }

            RetailScreen.CONSULTAZIONE_ARTICOLO -> {
                Log.d("ProductWorkflow", "Consultazione Articolo")
            }

            RetailScreen.UNKNOWN -> {
                Log.d("ProductWorkflow", "Schermata sconosciuta")
            }
        }
    }
}