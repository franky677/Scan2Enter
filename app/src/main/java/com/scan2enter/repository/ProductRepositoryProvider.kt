package com.scan2enter.repository

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.scan2enter.api.DueRetailApiClient
import com.scan2enter.api.GatewayApiClient
import java.util.UUID

object ProductRepositoryProvider {

    private const val TAG = "Scan2Enter"

    private const val GATEWAY_BASE_URL =
        "http://192.168.1.30:5055"

    /*
     * Credenziali mantenute temporaneamente soltanto per le scritture
     * delle impostazioni di riordino tramite Due Retail WebAPI.
     * La normale lettura articolo non usa più queste credenziali.
     */
    private const val API_USERNAME = "2bit@2bit.it"
    private const val API_PASSWORD = "2bit"
    private const val API_PREFS_NAME = "due_retail_api"
    private const val API_CLIENT_ID_KEY = "client_id"

    @Volatile
    private var repositoryInstance: ProductRepository? = null

    fun get(context: Context): ProductRepository {
        repositoryInstance?.let { return it }

        return synchronized(this) {
            repositoryInstance
                ?: createRepository(context.applicationContext).also {
                    repositoryInstance = it
                }
        }
    }

    private fun createRepository(
        context: Context
    ): ProductRepository {
        Log.d(
            TAG,
            "CREAZIONE REPOSITORY: letture tramite Gateway " +
                    "baseUrl=$GATEWAY_BASE_URL"
        )

        val gatewayClient = GatewayApiClient(
            baseUrl = GATEWAY_BASE_URL
        )

        /*
         * La costruzione del client non effettua il login.
         * Il token Due Retail viene richiesto soltanto se l'utente
         * salva una modifica alle scorte.
         */
        val clientId = getOrCreatePersistentClientId(context)

        val dueRetailWriteClient = DueRetailApiClient(
            username = API_USERNAME,
            password = API_PASSWORD,
            clientId = clientId
        )

        return ProductRepository(
            gatewayApi = gatewayClient,
            dueRetailWriteApi = dueRetailWriteClient
        )
    }

    private fun getOrCreatePersistentClientId(
        context: Context
    ): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )?.trim().orEmpty()

        check(androidId.isNotBlank()) {
            "ANDROID_ID non disponibile"
        }

        val stableClientId = UUID.nameUUIDFromBytes(
            "${context.packageName}:$androidId"
                .toByteArray(Charsets.UTF_8)
        ).toString()

        val preferences = context.getSharedPreferences(
            API_PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val previouslySavedClientId = preferences
            .getString(API_CLIENT_ID_KEY, null)
            ?.trim()
            .orEmpty()

        if (
            previouslySavedClientId.isNotBlank() &&
            previouslySavedClientId != stableClientId
        ) {
            Log.w(
                TAG,
                "CLIENT ID MIGRATO " +
                        "da=$previouslySavedClientId " +
                        "a=$stableClientId"
            )
        }

        check(
            preferences.edit()
                .putString(API_CLIENT_ID_KEY, stableClientId)
                .commit()
        ) {
            "Impossibile salvare il clientId stabile"
        }

        Log.d(
            TAG,
            "CLIENT ID SCRITTURE DUE RETAIL = $stableClientId"
        )

        return stableClientId
    }
}
