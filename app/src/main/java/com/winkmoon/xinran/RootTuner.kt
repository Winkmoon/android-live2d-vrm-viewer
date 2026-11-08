package com.winkmoon.xinran

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional, device-agnostic root tuning.
 *
 * Every path is discovered at runtime. Nothing here is specific to one SoC:
 * CPU governor files, Adreno devfreq nodes and Mali/other devfreq governors are
 * probed and only written when present. Restore is best-effort and automatic.
 */
object RootTuner {
    private const val TAG = "M3ELive2D/Root"
    private const val BACKUP = "/data/local/tmp/m3e_live2d_governors.bak"

    data class Result(val ok: Boolean, val message: String)

    @Volatile
    var isActive: Boolean = false
        private set

    suspend fun isRootAvailable(): Boolean = RootShell.isAvailable()

    suspend fun enable(): Result = withContext(Dispatchers.IO) {
        try {
            val script = """
                set -e
                : > $BACKUP
                for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
                  if [ -f "${'$'}f" ]; then
                    v=${'$'}(cat "${'$'}f" 2>/dev/null)
                    [ -n "${'$'}v" ] && echo "${'$'}f=${'$'}v" >> $BACKUP
                    echo performance > "${'$'}f" 2>/dev/null || true
                  fi
                done
                for f in /sys/class/kgsl/kgsl-3d0/devfreq/governor /sys/class/devfreq/*/governor; do
                  if [ -f "${'$'}f" ]; then
                    v=${'$'}(cat "${'$'}f" 2>/dev/null)
                    [ -n "${'$'}v" ] && echo "${'$'}f=${'$'}v" >> $BACKUP
                    echo performance > "${'$'}f" 2>/dev/null || true
                  fi
                done
                for f in /sys/class/kgsl/kgsl-3d0/force_clk_on \
                         /sys/class/kgsl/kgsl-3d0/force_bus_on \
                         /sys/class/kgsl/kgsl-3d0/force_rail_on \
                         /sys/class/kgsl/kgsl-3d0/force_no_nap; do
                  [ -w "${'$'}f" ] && echo 1 > "${'$'}f" 2>/dev/null || true
                done
                echo "performance mode enabled"
            """.trimIndent()
            val output = RootShell.run(script)
            Log.i(TAG, output)
            isActive = true
            Result(true, output.ifBlank { "performance mode enabled" })
        } catch (t: Throwable) {
            Log.w(TAG, "enable failed", t)
            Result(false, t.message ?: "root command failed")
        }
    }

    suspend fun disable(): Result = withContext(Dispatchers.IO) {
        disableBlocking()
    }

    /** Safe to call from Activity.onDestroy(); never throws. */
    fun disableBlocking(): Result {
        return try {
            val script = """
                if [ -f $BACKUP ]; then
                  while IFS='=' read -r f v; do
                    [ -n "${'$'}f" ] && [ -w "${'$'}f" ] && echo "${'$'}v" > "${'$'}f" 2>/dev/null || true
                  done < $BACKUP
                  rm -f $BACKUP
                fi
                for f in /sys/class/kgsl/kgsl-3d0/force_clk_on \
                         /sys/class/kgsl/kgsl-3d0/force_bus_on \
                         /sys/class/kgsl/kgsl-3d0/force_rail_on \
                         /sys/class/kgsl/kgsl-3d0/force_no_nap; do
                  [ -w "${'$'}f" ] && echo 0 > "${'$'}f" 2>/dev/null || true
                done
                echo "defaults restored"
            """.trimIndent()
            val output = RootShell.run(script)
            Log.i(TAG, output)
            isActive = false
            Result(true, output.ifBlank { "defaults restored" })
        } catch (t: Throwable) {
            Log.w(TAG, "restore failed", t)
            Result(false, t.message ?: "restore failed")
        }
    }

    fun restoreIfActive() {
        if (isActive) {
            disableBlocking()
        }
    }


}
