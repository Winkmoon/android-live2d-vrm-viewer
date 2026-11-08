package com.winkmoon.xinran

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun MoonApp(
    initial3D: Boolean = false,
    onViewCreated: (Live2DView) -> Unit = {}
) {
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf(ThemePrefs.load(context)) }
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    M3ELive2DTheme(darkTheme = darkTheme) {
        UnifiedViewerApp(
            initial3D = initial3D,
            themeMode = themeMode,
            onThemeModeChange = { mode ->
                themeMode = mode
                ThemePrefs.save(context, mode)
            },
            onViewCreated = onViewCreated
        )
    }
}
