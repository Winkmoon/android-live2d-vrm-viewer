package com.winkmoon.xinran

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * SurfaceView-based Cubism renderer.
 *
 * A dedicated GLSurfaceView render thread plus a separate SurfaceView layer keeps
 * Compose UI composition off the model render path. Buffer size can be lowered
 * independently from the view size (dynamic resolution) because SurfaceFlinger
 * scales the surface to the display frame.
 */
class Live2DView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    private var viewWidth = 0
    private var viewHeight = 0
    private var targetFps = 120
    private var renderScale = 1.0f
    private var adpfEnabled = true
    private var mipmapsEnabled = true
    private var renderThreadTid = 0

    @Volatile
    var glVersion: String = ""
        private set

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        preserveEGLContextOnPause = true
        holder.setFormat(PixelFormat.RGBA_8888)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusable = true
        setOnTouchListener { _, event -> handleTouch(event) }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> queueEvent { nativeOnTouchesBegan(x, y) }
            MotionEvent.ACTION_MOVE -> queueEvent { nativeOnTouchesMoved(x, y) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                queueEvent { nativeOnTouchesEnded(x, y) }
        }
        return true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        nativeOnSurfaceCreated()
        nativeSetMipmaps(mipmapsEnabled)
        glVersion = nativeGetGlVersion()
        renderThreadTid = nativeGetRenderThreadTid()
        applyFrameRate()
        if (adpfEnabled) {
            Adpf.start(context, renderThreadTid, targetFps)
            nativeSetAdpfEnabled(true)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        nativeOnSurfaceChanged(width, height)
        applyFrameRate()
    }

    override fun onDrawFrame(gl: GL10?) {
        nativeOnDrawFrame()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        post { applyFixedSize() }
    }

    fun setTargetFps(fps: Int) {
        targetFps = fps.coerceIn(30, 240)
        applyFrameRate()
        Adpf.updateTarget(targetFps)
        if (adpfEnabled && renderThreadTid > 0) {
            Adpf.start(context, renderThreadTid, targetFps)
        }
    }

    fun setRenderScale(scale: Float) {
        val next = scale.coerceIn(0.5f, 1.0f)
        if (abs(next - renderScale) < 0.001f) return
        renderScale = next
        post { applyFixedSize() }
    }

    fun setAdpfEnabled(enabled: Boolean) {
        adpfEnabled = enabled
        nativeSetAdpfEnabled(enabled)
        if (enabled && renderThreadTid > 0) {
            Adpf.start(context, renderThreadTid, targetFps)
        } else {
            Adpf.stop()
        }
    }

    fun setBackgroundColor(r: Float, g: Float, b: Float) {
        queueEvent { nativeSetBackgroundColor(r, g, b) }
    }

    fun setModel(index: Int) = queueEvent { nativeSetModel(index) }
    fun nextModel() = queueEvent { nativeNextModel() }

    /** Re-scan assets + imported models on the GL thread, then report names. */
    fun refreshModels(onDone: (Array<String>) -> Unit) {
        queueEvent {
            nativeRefreshModels()
            val names = nativeGetModelNames()
            post { onDone(names) }
        }
    }
    fun startMotion(group: String, index: Int) =
        queueEvent { nativeStartMotion(group, index) }
    fun startRandomMotion(group: String) =
        queueEvent { nativeStartRandomMotion(group) }
    fun setExpression(name: String) = queueEvent { nativeSetExpression(name) }
    fun setParameter(name: String, value: Float) =
        queueEvent { nativeSetParameter(name, value) }
    fun resetParameters() = queueEvent { nativeResetParameters() }
    fun setMaskScale(scale: Float) = queueEvent { nativeSetMaskScale(scale) }
    fun setMipmaps(enabled: Boolean) {
        mipmapsEnabled = enabled
        queueEvent { nativeSetMipmaps(enabled) }
    }

    fun releaseAdpf() {
        Adpf.stop()
        nativeSetAdpfEnabled(false)
    }

    private fun applyFixedSize() {
        if (viewWidth <= 0 || viewHeight <= 0 || holder.surface == null) return
        if (renderScale >= 0.999f) {
            holder.setSizeFromLayout()
        } else {
            val w = (viewWidth * renderScale).roundToInt().coerceAtLeast(320)
            val h = (viewHeight * renderScale).roundToInt().coerceAtLeast(320)
            holder.setFixedSize(w, h)
        }
    }

    private fun applyFrameRate() {
        val fps = targetFps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val surface: Surface? = holder.surface
                if (surface != null && surface.isValid) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        surface.setFrameRate(
                            fps.toFloat(),
                            Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                            Surface.CHANGE_FRAME_RATE_ALWAYS
                        )
                    } else {
                        surface.setFrameRate(
                            fps.toFloat(),
                            Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }
        try {
            val activity = context.findActivity() ?: return
            RefreshRateController.apply(activity, fps)
        } catch (_: Throwable) {
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
