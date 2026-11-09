package com.winkmoon.xinran

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Settings shared by the Live2D and 3D screens.
 *
 * Both screens live in the same process, so Compose snapshot state is enough to
 * keep a single settings sheet (common + Live2D + 3D sections) coherent no
 * matter which mode is currently visible.
 */
object AppSettings {
    private const val PREFS = "m3e_shared_settings"
    private const val KEY_TARGET_FPS = "target_fps"
    private const val KEY_RENDER_SCALE = "render_scale"
    private const val KEY_MASK_SCALE = "mask_scale"
    private const val KEY_AUTO_RESOLUTION = "auto_resolution"
    private const val KEY_MIPMAPS = "mipmaps"
    private const val KEY_ADPF = "adpf"
    private const val KEY_AVATAR_QUALITY = "avatar_quality"
    private const val KEY_AVATAR_SHADOWS = "avatar_shadows"
    private const val KEY_HIDE_UI_ON_MOTION = "hide_ui_on_motion"

    private var prefs: android.content.SharedPreferences? = null
    private var initialized = false

    var targetFps by mutableIntStateOf(0)
        private set
    var renderScale by mutableFloatStateOf(1f)
        private set
    var maskScale by mutableFloatStateOf(1f)
        private set
    var autoResolution by mutableStateOf(false)
        private set
    var mipmaps by mutableStateOf(true)
        private set
    var adpfEnabled by mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        private set
    var avatarQuality by mutableFloatStateOf(1f)
        private set
    var avatarShadows by mutableStateOf(true)
        private set
    var hideUiOnMotion by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (initialized) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        targetFps = p.getInt(KEY_TARGET_FPS, 0)
        renderScale = p.getFloat(KEY_RENDER_SCALE, 1f)
        maskScale = p.getFloat(KEY_MASK_SCALE, 1f)
        autoResolution = p.getBoolean(KEY_AUTO_RESOLUTION, false)
        mipmaps = p.getBoolean(KEY_MIPMAPS, true)
        adpfEnabled = p.getBoolean(
            KEY_ADPF,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
        avatarQuality = p.getFloat(KEY_AVATAR_QUALITY, 1f)
        avatarShadows = p.getBoolean(KEY_AVATAR_SHADOWS, true)
        hideUiOnMotion = p.getBoolean(KEY_HIDE_UI_ON_MOTION, true)
        initialized = true
    }

    fun ensureTargetFps(defaultFps: Int) {
        if (targetFps <= 0) updateTargetFps(defaultFps)
    }

    fun updateTargetFps(value: Int) {
        targetFps = value
        prefs?.edit()?.putInt(KEY_TARGET_FPS, value)?.apply()
    }

    fun updateRenderScale(value: Float) {
        renderScale = value
        prefs?.edit()?.putFloat(KEY_RENDER_SCALE, value)?.apply()
    }

    fun updateMaskScale(value: Float) {
        maskScale = value
        prefs?.edit()?.putFloat(KEY_MASK_SCALE, value)?.apply()
    }

    fun updateAutoResolution(value: Boolean) {
        autoResolution = value
        prefs?.edit()?.putBoolean(KEY_AUTO_RESOLUTION, value)?.apply()
    }

    fun updateMipmaps(value: Boolean) {
        mipmaps = value
        prefs?.edit()?.putBoolean(KEY_MIPMAPS, value)?.apply()
    }

    fun updateAdpfEnabled(value: Boolean) {
        adpfEnabled = value
        prefs?.edit()?.putBoolean(KEY_ADPF, value)?.apply()
    }

    fun updateAvatarQuality(value: Float) {
        avatarQuality = value
        prefs?.edit()?.putFloat(KEY_AVATAR_QUALITY, value)?.apply()
    }

    fun updateAvatarShadows(value: Boolean) {
        avatarShadows = value
        prefs?.edit()?.putBoolean(KEY_AVATAR_SHADOWS, value)?.apply()
    }

    fun updateHideUiOnMotion(value: Boolean) {
        hideUiOnMotion = value
        prefs?.edit()?.putBoolean(KEY_HIDE_UI_ON_MOTION, value)?.apply()
    }
}
