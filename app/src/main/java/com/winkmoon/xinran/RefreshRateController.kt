package com.winkmoon.xinran

import android.app.Activity
import android.os.Looper
import android.util.Log
import kotlin.math.abs

/**
 * Best-effort force of the app window's display refresh mode.
 *
 * `preferredRefreshRate` alone is only a hint on many OEM builds. Setting
 * `preferredDisplayModeId` to a mode with the same resolution and the desired
 * refresh rate is the strongest public API an app has for requesting a mode.
 */
object RefreshRateController {

    fun apply(activity: Activity, targetFps: Int) {
        val fps = targetFps.coerceIn(30, 240)
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread { applyOnMain(activity, fps) }
        } else {
            applyOnMain(activity, fps)
        }
    }

    private fun applyOnMain(activity: Activity, fps: Int) {
        try {
            val display = activity.windowManager.defaultDisplay
            val current = display.mode
            val sameResolution = display.supportedModes.filter {
                it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
            }
            val candidates = sameResolution.ifEmpty { display.supportedModes.toList() }
            val best = candidates.minByOrNull { abs(it.refreshRate - fps) } ?: return

            val attrs = activity.window.attributes
            if (attrs.preferredDisplayModeId == best.modeId &&
                abs(attrs.preferredRefreshRate - best.refreshRate) < 0.01f
            ) {
                return
            }

            attrs.preferredDisplayModeId = best.modeId
            attrs.preferredRefreshRate = best.refreshRate
            activity.window.attributes = attrs
            Log.d(
                "RefreshRate",
                "requested ${fps}Hz -> mode ${best.modeId} (${best.refreshRate}Hz)"
            )
        } catch (error: Throwable) {
            Log.w("RefreshRate", "display mode request failed", error)
        }
    }
}
