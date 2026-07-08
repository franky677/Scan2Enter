package com.scan2enter.detector

/**
 * Identifica la schermata corrente di Due Retail Mobile.
 *
 * Questa enum verrà usata dal ProductInfoWorkflow
 * per scegliere il comportamento corretto dopo una scansione.
 */
enum class RetailScreen {

    /**
     * Schermata non riconosciuta.
     */
    UNKNOWN,

    /**
     * Collo Veloce.
     */
    COLLO_VELOCE,

    /**
     * Gestione Etichette.
     */
    GESTIONE_ETICHETTE,

    /**
     * Consultazione articolo.
     */
    CONSULTAZIONE_ARTICOLO
}