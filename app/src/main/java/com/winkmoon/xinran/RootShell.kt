package com.winkmoon.xinran

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

enum class RootBackend(val labelRes: Int) {
    Auto(R.string.root_backend_auto),
    Magisk(R.string.root_backend_magisk),
    KernelSU(R.string.root_backend_kernelsu),
    APatch(R.string.root_backend_apatch),
    SuperSU(R.string.root_backend_supersu),
    SKRoot(R.string.root_backend_skroot),
    Custom(R.string.root_backend_custom)
}

/**
 * Single entry point for every root operation.
 *
 * Most managers expose `su -c <script>`; a custom prefix is available for
 * unusual setups such as a renamed binary or a wrapper that needs extra args.
 */
object RootShell {
    private const val PREFS = "m3e_root_shell"
    private const val KEY_BACKEND = "backend"
    private const val KEY_CUSTOM = "custom_command"

    private var prefs: android.content.SharedPreferences? = null

    var backend by mutableStateOf(RootBackend.Auto)
        private set
    var customCommand by mutableStateOf("su -c")
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        backend = runCatching {
            RootBackend.valueOf(p.getString(KEY_BACKEND, RootBackend.Auto.name)!!)
        }.getOrDefault(RootBackend.Auto)
        customCommand = p.getString(KEY_CUSTOM, "su -c").orEmpty().ifBlank { "su -c" }
    }

    fun setBackend(context: Context, value: RootBackend) {
        init(context)
        backend = value
        prefs?.edit()?.putString(KEY_BACKEND, value.name)?.apply()
    }

    fun setCustomCommand(context: Context, value: String) {
        init(context)
        customCommand = value
        prefs?.edit()?.putString(KEY_CUSTOM, value)?.apply()
    }

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching { run("id").contains("uid=0") }.getOrDefault(false)
    }

    fun run(script: String): String {
        val process = ProcessBuilder(buildCommand(script))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("root command timeout")
        }
        if (process.exitValue() != 0) {
            error(output.ifBlank { "root command exited ${process.exitValue()}" })
        }
        return output
    }

    private fun buildCommand(script: String): List<String> {
        val prefix = when (backend) {
            RootBackend.Auto,
            RootBackend.Magisk,
            RootBackend.KernelSU,
            RootBackend.APatch,
            RootBackend.SuperSU -> "su -c"

            RootBackend.SKRoot -> "skroot -c"
            RootBackend.Custom -> customCommand.ifBlank { "su -c" }
        }
        val parts = prefix.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return listOf("su", "-c", script)

        if (parts.any { it.contains("{cmd}") }) {
            val replaced = parts.joinToString(" ").replace("{cmd}", shellQuote(script))
            return listOf("sh", "-c", replaced)
        }
        return parts + script
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
