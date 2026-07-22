package com.scan2enter.repository

import android.content.Context
import android.util.Log
import com.scan2enter.api.DueRetailApiClient
import java.util.UUID

object ProductRepositoryProvider {
    private const val TAG = "Scan2Enter"
    private const val API_USERNAME = "2bit@2bit.it"
    private const val API_PASSWORD = "2bit"
    private const val API_PREFS_NAME = "due_retail_api"
    private const val API_CLIENT_ID_KEY = "client_id"

    @Volatile
    private var repositoryInstance: ProductRepository? = null

    fun get(context: Context): ProductRepository {
        repositoryInstance?.let { return it }
        return synchronized(this) {
            repositoryInstance ?: createRepository(context.applicationContext).also {
                repositoryInstance = it
            }
        }
    }

    private fun createRepository(context: Context): ProductRepository {
        val clientId = getOrCreatePersistentClientId(context)
        Log.d(TAG, "CREAZIONE REPOSITORY API CONDIVISO clientId=$clientId")
        return ProductRepository(
            DueRetailApiClient(
                username = API_USERNAME,
                password = API_PASSWORD,
                clientId = clientId
            )
        )
    }

    private fun getOrCreatePersistentClientId(context: Context): String {
        val preferences = context.getSharedPreferences(API_PREFS_NAME, Context.MODE_PRIVATE)
        val savedClientId = preferences.getString(API_CLIENT_ID_KEY, null)?.trim().orEmpty()
        if (savedClientId.isNotBlank()) {
            Log.d(TAG, "CLIENT ID PERSISTENTE RIUTILIZZATO = $savedClientId")
            return savedClientId
        }
        val newClientId = UUID.randomUUID().toString()
        check(preferences.edit().putString(API_CLIENT_ID_KEY, newClientId).commit()) {
            "Impossibile salvare il clientId persistente"
        }
        Log.d(TAG, "NUOVO CLIENT ID PERSISTENTE CREATO = $newClientId")
        return newClientId
    }
}
