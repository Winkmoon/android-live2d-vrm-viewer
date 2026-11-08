package com.winkmoon.xinran

import android.content.Context
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional root-only refresh-rate forcing.
 *
 * Writes the system min/peak refresh settings and the user-preferred display
 * mode, then restores the previous values when the app is destroyed. OEM
 * policies can still cap the effective rate; the UI reports requested vs actual.
 */
object RootRefreshRateForcer {
    private const val TAG = "Moon/RootRefresh"
    private const val PREFS = "m3e_root_refresh"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PEAK = "peak"
    private const val KEY_MIN = "min"
    private const val KEY_SF_MODE = "sf_mode"

    private val sfModeRegex = Regex(
        """\{id=(\d+), hwcId=\d+, resolution=(\d+)x(\d+), vsyncRate=([0-9.]+) Hz"""
    )
    private val activeModeRegex = Regex(
        """(?:activeMode|mDisplayModePtr)=\{id=(\d+)"""
    )

    private data class SfMode(
        val id: Int,
        val width: Int,
        val height: Int,
        val rate: Float
    )

    @Volatile
    var isActive: Boolean = false
        private set

    /** True while the user wants the lock; restored on background and reapplied on resume. */
    @Volatile
    var requested: Boolean = false
        private set

    suspend fun isAvailable(): Boolean = RootShell.isAvailable()

    suspend fun enable(context: Context, targetFps: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay
                val width = display.mode.physicalWidth
                val height = display.mode.physicalHeight
                val fps = targetFps.coerceIn(30, 240)
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

                if (!prefs.getBoolean(KEY_ACTIVE, false)) {
                    val peak = RootShell.run("settings get system peak_refresh_rate").trim()
                    val min = RootShell.run("settings get system min_refresh_rate").trim()
                    prefs.edit()
                        .putString(KEY_PEAK, peak)
                        .putString(KEY_MIN, min)
                        .putBoolean(KEY_ACTIVE, true)
                        .apply()
                }

                val packageName = context.packageName
                val sfDump = RootShell.run("dumpsys SurfaceFlinger")
                val sfMode = sfModeRegex.findAll(sfDump)
                    .mapNotNull { match ->
                        val modeId = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                        val modeWidth = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                        val modeHeight = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                        val rate = match.groupValues[4].toFloatOrNull() ?: return@mapNotNull null
                        SfMode(modeId, modeWidth, modeHeight, rate)
                    }
                    .filter { it.width == width && it.height == height }
                    .minByOrNull { kotlin.math.abs(it.rate - fps) }

                RootShell.run(
                    "settings put system peak_refresh_rate $fps; " +
                        "settings put system min_refresh_rate $fps; " +
                        "cmd display set-user-preferred-display-mode $width $height $fps 0" +
                        (sfMode?.let { "; service call SurfaceFlinger 1035 i32 ${it.id}" } ?: "")
                )
                isActive = true
                requested = true
                Result.success(Unit)
            } catch (t: Throwable) {
                Log.w(TAG, "enable failed", t)
                Result.failure(t)
            }
        }

    suspend fun disable(context: Context) = withContext(Dispatchers.IO) {
        requested = false
        restoreIfActive(context)
    }

    fun restoreIfActive(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) {
            isActive = false
            return
        }
        try {
            val peak = prefs.getString(KEY_PEAK, null)
            val min = prefs.getString(KEY_MIN, null)
            val script = buildString {
                if (peak.isNullOrBlank() || peak == "null") {
                    append("settings delete system peak_refresh_rate; ")
                } else {
                    append("settings put system peak_refresh_rate $peak; ")
                }
                if (min.isNullOrBlank() || min == "null") {
                    append("settings delete system min_refresh_rate; ")
                } else {
                    append("settings put system min_refresh_rate $min; ")
                }
                append("cmd display clear-user-preferred-display-mode 0 || true; ")
                append("cmd game reset ${context.packageName} || true")
            }
            runCatching { RootShell.run(script) }
                .onFailure { Log.w(TAG, "settings restore failed", it) }
        } finally {
            // Restore SurfaceFlinger even if a settings command failed.
            runCatching {
                prefs.getString(KEY_SF_MODE, null)?.toIntOrNull()?.let { original ->
                    RootShell.run("service call SurfaceFlinger 1035 i32 $original")
                }
            }.onFailure { Log.w(TAG, "SurfaceFlinger restore failed", it) }
            prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
            isActive = false
        }
    }


}
