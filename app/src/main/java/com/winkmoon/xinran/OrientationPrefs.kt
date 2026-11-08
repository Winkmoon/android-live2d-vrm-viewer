package com.winkmoon.xinran

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo

enum class OrientationMode {
    System,
    Portrait,
    Landscape
}

/**
 * Screen-orientation preference.
 *
 * System means "let Android decide" (including the user's auto-rotate lock);
 * the other two lock the activity to a sensor-aware portrait/landscape mode.
 */
object OrientationPrefs {
    private const val PREFS = "m3e_live2d_settings"
    private const val KEY = "screen_orientation"

    fun load(context: Context): OrientationMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        return runCatching { OrientationMode.valueOf(raw.orEmpty()) }
            .getOrDefault(OrientationMode.System)
    }

    fun save(context: Context, mode: OrientationMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, mode.name)
            .apply()
    }

    fun apply(activity: Activity, mode: OrientationMode) {
        activity.requestedOrientation = when (mode) {
            OrientationMode.System -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationMode.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationMode.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}
