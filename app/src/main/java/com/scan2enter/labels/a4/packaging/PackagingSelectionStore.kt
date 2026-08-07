package com.scan2enter.labels.a4.packaging

import android.content.Context

object PackagingSelectionStore {

    private const val PREFS = "a4_packaging_preferences"
    private const val TYPE_KEY = "packaging_type"
    private const val HOOK_KEY = "include_hook_label"
    private const val PRICE_KEY = "show_blister_price"

    fun save(context: Context, options: PackagingOptions) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(TYPE_KEY, options.type.name)
            .putBoolean(HOOK_KEY, options.includeHook)
            .putBoolean(PRICE_KEY, options.showPrice)
            .apply()
    }

    fun load(context: Context): PackagingOptions {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val type = runCatching {
            PackagingType.valueOf(
                prefs.getString(
                    TYPE_KEY,
                    PackagingType.BLISTER_LARGE.name
                ) ?: PackagingType.BLISTER_LARGE.name
            )
        }.getOrDefault(PackagingType.BLISTER_LARGE)

        return PackagingOptions(
            type = type,
            includeHook = prefs.getBoolean(HOOK_KEY, false),
            showPrice = prefs.getBoolean(PRICE_KEY, false)
        )
    }
}