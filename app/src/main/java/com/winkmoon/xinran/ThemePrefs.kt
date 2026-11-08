package com.winkmoon.xinran

import android.content.Context

enum class ThemeMode {
    System,
    Light,
    Dark
}

object ThemePrefs {
    private const val PREFS = "m3e_theme"
    private const val KEY_MODE = "mode"

    fun load(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, ThemeMode.System.name)
        return runCatching { ThemeMode.valueOf(raw!!) }.getOrDefault(ThemeMode.System)
    }

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}
