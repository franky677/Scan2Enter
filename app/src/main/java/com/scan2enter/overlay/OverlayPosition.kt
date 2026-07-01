package com.scan2enter.overlay

import android.content.Context

object OverlayPosition {

    private const val PREFS = "overlay_position"

    private const val KEY_X = "x"
    private const val KEY_Y = "y"

    fun save(
        context: Context,
        x: Int,
        y: Int
    ) {

        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_X, x)
            .putInt(KEY_Y, y)
            .apply()
    }

    fun getX(
        context: Context,
        defaultValue: Int = 50
    ): Int {

        return context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_X, defaultValue)
    }

    fun getY(
        context: Context,
        defaultValue: Int = 300
    ): Int {

        return context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_Y, defaultValue)
    }
}