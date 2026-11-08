package com.winkmoon.xinran

import android.content.Context
import android.os.Build
import android.os.PerformanceHintManager
import android.util.Log

/**
 * Android Dynamic Performance Framework (ADPF) adapter.
 *
 * The native renderer reports its CPU work duration each frame. ADPF uses the
 * hint to bias the scheduler / GPU frequency without permanent overclocking.
 */
object Adpf {
    private const val TAG = "M3ELive2D/ADPF"
    private var session: PerformanceHintManager.Session? = null
    private var targetNanos: Long = 1_000_000_000L / 120L

    fun start(context: Context, renderThreadTid: Int, targetFps: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || renderThreadTid <= 0) {
            return
        }
        stop()
        targetNanos = 1_000_000_000L / targetFps.coerceIn(30, 240)
        try {
            val manager = context.getSystemService(PerformanceHintManager::class.java)
            session = manager?.createHintSession(intArrayOf(renderThreadTid), targetNanos)
            Log.i(TAG, "session started tid=$renderThreadTid target=${targetNanos}ns")
        } catch (t: Throwable) {
            session = null
            Log.w(TAG, "ADPF unavailable", t)
        }
    }

    fun updateTarget(targetFps: Int) {
        val next = 1_000_000_000L / targetFps.coerceIn(30, 240)
        if (next == targetNanos) return
        targetNanos = next
        try {
            session?.updateTargetWorkDuration(targetNanos)
        } catch (t: Throwable) {
            Log.w(TAG, "updateTarget failed", t)
        }
    }

    fun report(durationNanos: Long) {
        val current = session ?: return
        try {
            current.reportActualWorkDuration(durationNanos.coerceAtLeast(1L))
        } catch (_: Throwable) {
            // A closed session is a normal race during surface teardown.
        }
    }

    fun stop() {
        try {
            session?.close()
        } catch (_: Throwable) {
        }
        session = null
    }
}
