@file:JvmName("Live2DNative")

package com.winkmoon.xinran

import android.app.Activity
import android.content.Context
import android.util.Log
import java.io.File
import java.lang.ref.WeakReference

private const val TAG = "M3ELive2D"
private var appContext: Context? = null
private var activityRef: WeakReference<Activity>? = null
private var libraryLoaded = false

/** Must be called once before any native call. */
fun initialize(context: Context, activity: Activity) {
    if (!libraryLoaded) {
        System.loadLibrary("m3elive2d")
        libraryLoaded = true
    }
    appContext = context.applicationContext
    activityRef = WeakReference(activity)

    val importedModels = File(context.applicationContext.filesDir, "models")
    importedModels.mkdirs()
    nativeSetImportedRoot(importedModels.absolutePath)
}

fun shutdown() {
    activityRef = null
    appContext = null
}

// Static callbacks used by the C++ bridge.
fun GetAssetList(path: String): Array<String> {
    val context = appContext ?: return emptyArray()
    return try {
        context.assets.list(path) ?: emptyArray()
    } catch (t: Throwable) {
        Log.w(TAG, "asset list failed: $path", t)
        emptyArray()
    }
}

fun LoadFile(path: String): ByteArray? {
    val context = appContext ?: return null
    return try {
        context.assets.open(path).use { it.readBytes() }
    } catch (t: Throwable) {
        Log.w(TAG, "asset open failed: $path", t)
        null
    }
}

fun MoveTaskToBack() {
    activityRef?.get()?.moveTaskToBack(true)
}

fun ReportFrame(durationNanos: Long) {
    Adpf.report(durationNanos)
}

// Rendering lifecycle.
external fun nativeOnStart()
external fun nativeOnPause()
external fun nativeOnStop()
external fun nativeOnDestroy()
external fun nativeOnSurfaceCreated()
external fun nativeOnSurfaceChanged(width: Int, height: Int)
external fun nativeOnDrawFrame()
external fun nativeOnTouchesBegan(x: Float, y: Float)
external fun nativeOnTouchesEnded(x: Float, y: Float)
external fun nativeOnTouchesMoved(x: Float, y: Float)
external fun nativeOnTouchesCancelled()
external fun nativeSetModelScale(scale: Float)
external fun nativeTranslateModel(dx: Float, dy: Float)
external fun nativeResetModelPosition()

// Imported model root / refresh.
external fun nativeSetImportedRoot(path: String)
external fun nativeRefreshModels()

// Model / motion / expression controls.
external fun nativeGetModelNames(): Array<String>
external fun nativeGetModelCount(): Int
external fun nativeGetCurrentModelIndex(): Int
external fun nativeSetModel(index: Int): Boolean
external fun nativeNextModel(): Boolean
external fun nativeGetMotionGroups(): Array<String>
external fun nativeGetMotionCount(group: String): Int
external fun nativeStartMotion(group: String, index: Int): Boolean
external fun nativeStartRandomMotion(group: String): Boolean
external fun nativeGetExpressions(): Array<String>
external fun nativeSetExpression(name: String): Boolean
external fun nativeSetParameter(name: String, value: Float)
external fun nativeResetParameters()
external fun nativeGetModelInfoJson(): String

// Telemetry / performance controls.
external fun nativeGetStats(out: FloatArray)
external fun nativeSetBackgroundColor(r: Float, g: Float, b: Float)
external fun nativeSetMaskScale(scale: Float)
external fun nativeSetMipmaps(enabled: Boolean)
external fun nativeGetRenderThreadTid(): Int
external fun nativeSetAdpfEnabled(enabled: Boolean)
external fun nativeGetGlVersion(): String
