package com.scan2enter.update

import android.content.Context

object AppUpdateChannel {

    private const val PREFS_NAME = "app_update"
    private const val KEY_CHANNEL = "channel"
    private const val DEFAULT_CHANNEL = "stable"

    fun get(context: Context): String {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CHANNEL, DEFAULT_CHANNEL)
            ?.lowercase()
            ?.takeIf { it == "test" || it == "stable" }
            ?: DEFAULT_CHANNEL
    }

    fun set(context: Context, channel: String) {
        val normalized = channel.lowercase()
        require(normalized == "test" || normalized == "stable")

        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CHANNEL, normalized)
            .apply()
    }
}
