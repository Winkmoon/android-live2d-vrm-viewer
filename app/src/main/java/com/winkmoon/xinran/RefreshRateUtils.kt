package com.winkmoon.xinran

import android.content.Context
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Reads the display's real refresh-rate modes instead of assuming a fixed
 * 60/90/120/144 ladder. The result is used by the global target-FPS setting
 * for both the Cubism and WebGL renderers.
 */
object RefreshRateUtils {

    fun supportedFpsTiers(context: Context): List<Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val display = wm?.defaultDisplay ?: return listOf(60)
        val tiers = sortedSetOf<Int>()

        runCatching {
            display.supportedModes.forEach { mode ->
                addTier(tiers, mode.refreshRate)
                mode.alternativeRefreshRates.forEach { addTier(tiers, it) }
            }
            addTier(tiers, display.refreshRate)
        }

        return tiers.ifEmpty { sortedSetOf(60) }.toList()
    }

    fun actualRefreshRate(context: Context): Int {
        return runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wm?.defaultDisplay?.refreshRate?.roundToInt() ?: 60
        }.getOrDefault(60)
    }

    fun defaultFps(context: Context): Int {
        val tiers = supportedFpsTiers(context)
        val current = runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wm?.defaultDisplay?.refreshRate ?: 60f
        }.getOrDefault(60f)
        return tiers.lastOrNull { it <= current + 1f } ?: tiers.last()
    }

    private fun addTier(tiers: MutableSet<Int>, rate: Float) {
        val hz = rate.roundToInt()
        if (hz in 30..240) tiers.add(hz)
    }
}
