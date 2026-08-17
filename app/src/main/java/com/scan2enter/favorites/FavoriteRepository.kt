package com.scan2enter.favorites

import android.content.Context
import android.util.Log
import com.scan2enter.api.FavoriteDto
import com.scan2enter.api.GatewayApiClient
import com.scan2enter.model.ProductInfo
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object FavoriteRepository {

    private const val TAG = "Scan2Enter"
    private const val SYNC_INTERVAL_SECONDS = 15L
    private const val SYNC_PREFS = "favorite_sync_preferences"
    private const val KEY_INITIAL_MIGRATION_DONE = "initial_migration_done"

    @Volatile
    private var applicationContext: Context? = null

    private val gatewayApiClient = GatewayApiClient()

    private val syncExecutor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(
                runnable,
                "Scan2Enter-FavoritesSync"
            ).apply {
                isDaemon = true
            }
        }

    private val syncStarted = AtomicBoolean(false)

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        applicationContext = appContext
        FavoriteStore.initialize(appContext)

        if (syncStarted.compareAndSet(false, true)) {
            syncExecutor.execute {
                synchronizeAccordingToMigrationState()
            }

            syncExecutor.scheduleWithFixedDelay(
                { synchronizeAccordingToMigrationState() },
                SYNC_INTERVAL_SECONDS,
                SYNC_INTERVAL_SECONDS,
                TimeUnit.SECONDS
            )
        }
    }

    fun toggle(product: ProductInfo): Boolean {
        return if (FavoriteStore.contains(product.articleId)) {
            remove(product.articleId)
            false
        } else {
            add(product)
            true
        }
    }

    fun add(product: ProductInfo): Boolean {
        val changed = FavoriteStore.add(product)

        val item =
            FavoriteStore.get(product.articleId)
                ?: FavoriteItem.fromProduct(product)

        syncExecutor.execute {
            saveRemote(item)
        }

        return changed
    }

    fun remove(articleId: Long): Boolean {
        val changed = FavoriteStore.remove(articleId)

        syncExecutor.execute {
            gatewayApiClient
                .removeFavorite(articleId)
                .onFailure { error ->
                    Log.e(
                        TAG,
                        "PREFERITO REMOTO REMOVE FALLITO id=$articleId",
                        error
                    )
                }
        }

        return changed
    }

    fun isFavorite(articleId: Long): Boolean =
        FavoriteStore.contains(articleId)

    fun getAll(): List<FavoriteItem> =
        FavoriteStore.getAll()

    fun size(): Int =
        FavoriteStore.size()

    fun clear() {
        val ids =
            FavoriteStore.getAll()
                .map { it.articleId }

        FavoriteStore.clear()

        ids.forEach { articleId ->
            syncExecutor.execute {
                gatewayApiClient.removeFavorite(articleId)
            }
        }
    }

    fun addListener(listener: (Int) -> Unit) {
        FavoriteStore.addListener(listener)
    }

    fun removeListener(listener: (Int) -> Unit) {
        FavoriteStore.removeListener(listener)
    }

    private fun synchronizeAccordingToMigrationState() {
        if (isInitialMigrationDone()) {
            refreshFromGateway()
        } else {
            migrateLocalFavoritesAndRefresh()
        }
    }

    private fun migrateLocalFavoritesAndRefresh() {
        val local = FavoriteStore.getAll()

        var uploadSucceeded = true

        local.forEach { item ->
            val result =
                gatewayApiClient.saveFavorite(
                    FavoriteDto(
                        articleId = item.articleId,
                        barcode = item.barcode,
                        articleCode = item.articleCode,
                        description = item.description,
                        publicPrice = item.publicPrice,
                        stock = item.stock
                    )
                )

            if (result.isFailure) {
                uploadSucceeded = false
            }
        }

        if (!uploadSucceeded) {
            Log.w(
                TAG,
                "MIGRAZIONE PREFERITI RIMANDATA: upload locale incompleto"
            )
            return
        }

        gatewayApiClient
            .getFavorites()
            .onSuccess { remote ->
                FavoriteStore.replaceAll(
                    remote.map { item ->
                        FavoriteItem(
                            articleId = item.articleId,
                            barcode = item.barcode,
                            articleCode = item.articleCode,
                            description = item.description,
                            publicPrice = item.publicPrice,
                            stock = item.stock
                        )
                    }
                )

                markInitialMigrationDone()

                Log.d(
                    TAG,
                    "MIGRAZIONE PREFERITI COMPLETATA elementi=${remote.size}"
                )
            }
            .onFailure { error ->
                Log.w(
                    TAG,
                    "MIGRAZIONE PREFERITI RIMANDATA: ${error.message}"
                )
            }
    }

    private fun isInitialMigrationDone(): Boolean {
        val context = applicationContext ?: return false

        return context
            .getSharedPreferences(
                SYNC_PREFS,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                KEY_INITIAL_MIGRATION_DONE,
                false
            )
    }

    private fun markInitialMigrationDone() {
        val context = applicationContext ?: return

        context
            .getSharedPreferences(
                SYNC_PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                KEY_INITIAL_MIGRATION_DONE,
                true
            )
            .apply()
    }

    private fun saveRemote(item: FavoriteItem) {
        gatewayApiClient
            .saveFavorite(
                FavoriteDto(
                    articleId = item.articleId,
                    barcode = item.barcode,
                    articleCode = item.articleCode,
                    description = item.description,
                    publicPrice = item.publicPrice,
                    stock = item.stock
                )
            )
            .onFailure { error ->
                Log.e(
                    TAG,
                    "PREFERITO REMOTO SAVE FALLITO id=${item.articleId}",
                    error
                )
            }
    }

    private fun refreshFromGateway() {
        gatewayApiClient
            .getFavorites()
            .onSuccess { remote ->
                val synchronized =
                    remote.map { item ->
                        FavoriteItem(
                            articleId = item.articleId,
                            barcode = item.barcode,
                            articleCode = item.articleCode,
                            description = item.description,
                            publicPrice = item.publicPrice,
                            stock = item.stock
                        )
                    }

                FavoriteStore.replaceAll(synchronized)

                Log.d(
                    TAG,
                    "PREFERITI CENTRALIZZATI SYNC OK elementi=${synchronized.size}"
                )
            }
            .onFailure { error ->
                Log.w(
                    TAG,
                    "PREFERITI CENTRALIZZATI SYNC NON DISPONIBILE: ${error.message}"
                )
            }
    }
}