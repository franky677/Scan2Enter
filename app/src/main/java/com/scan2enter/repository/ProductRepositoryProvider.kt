package com.scan2enter.repository

import android.content.Context
import android.provider.Settings
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
            "CLIENT ID STABILE DISPOSITIVO = $stableClientId"
        )

        return stableClientId
    }
}