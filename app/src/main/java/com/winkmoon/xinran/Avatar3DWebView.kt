package com.winkmoon.xinran

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.File

/**
 * Local, offline three.js + three-vrm viewer.
 *
 * Assets are served through WebViewAssetLoader so ES modules and glTF/VRM files
 * share a normal http(s) origin instead of relying on file:// XHR quirks.
 */
data class Avatar3DStats(
    val fps: Float = 0f,
    val drawCalls: Int = 0,
    val triangles: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val pixelRatio: Float = 0f,
    val textures: Int = 0
)

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
class Avatar3DWebView(
    context: Context,
    private val avatarsDir: File
) : WebView(context) {

    var onPageReady: (() -> Unit)? = null
    var onPageError: ((String) -> Unit)? = null
    var onStats: ((Avatar3DStats) -> Unit)? = null

    private var pageLoaded = false
    private var pendingUrl: String? = null

    private val assetLoader: WebViewAssetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler("/avatars/", object : WebViewAssetLoader.PathHandler {
            override fun handle(path: String): WebResourceResponse? {
                val name = Uri.decode(path.removePrefix("/"))
                if (name.isBlank() || name.contains('/') || name.contains('\\')) {
                    return null
                }
                val file = File(avatarsDir, name)
                if (!file.isFile) {
                    return null
                }
                val mime = when {
                    name.endsWith(".glb", ignoreCase = true) -> "model/gltf-binary"
                    name.endsWith(".vrm", ignoreCase = true) -> "model/vrm"
                    else -> "application/octet-stream"
                }
                return WebResourceResponse(mime, null, file.inputStream())
            }
        })
        .build()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        addJavascriptInterface(object {
            @JavascriptInterface
            fun onStats(
                fps: Double,
                drawCalls: Int,
                triangles: Int,
                width: Int,
                height: Int,
                pixelRatio: Double,
                textures: Int
            ) {
                post {
                    onStats?.invoke(
                        Avatar3DStats(
                            fps = fps.toFloat(),
                            drawCalls = drawCalls,
                            triangles = triangles,
                            width = width,
                            height = height,
                            pixelRatio = pixelRatio.toFloat(),
                            textures = textures
                        )
                    )
                }
            }
        }, "AndroidAvatar3D")

        webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                pageLoaded = true
                pendingUrl?.let { target ->
                    pendingUrl = null
                    loadNow(target)
                }
                onPageReady?.invoke()
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d("Avatar3D", "${consoleMessage.message()} @${consoleMessage.lineNumber()}")
                return true
            }
        }

        loadUrl("https://appassets.androidplatform.net/assets/vrm/index.html")
    }

    fun loadAvatar(file: File) {
        val url = "https://appassets.androidplatform.net/avatars/${Uri.encode(file.name)}"
        if (pageLoaded) {
            loadNow(url)
        } else {
            pendingUrl = url
        }
    }

    fun resetView() {
        evaluateJavascript("window.Avatar3D && window.Avatar3D.resetView()", null)
    }

    fun setRenderQuality(value: Float) {
        evaluateJavascript(
            "window.Avatar3D && window.Avatar3D.setRenderQuality(${value.coerceIn(0.5f, 3f)})",
            null
        )
    }

    fun setShadows(enabled: Boolean) {
        evaluateJavascript(
            "window.Avatar3D && window.Avatar3D.setShadows($enabled)",
            null
        )
    }

    fun setTargetFps(fps: Int) {
        evaluateJavascript(
            "window.Avatar3D && window.Avatar3D.setTargetFps(${fps.coerceIn(30, 240)})",
            null
        )
    }

    fun disposeViewer() {
        evaluateJavascript("window.Avatar3D && window.Avatar3D.dispose()", null)
        stopLoading()
        destroy()
    }

    private fun loadNow(url: String) {
        val quoted = org.json.JSONObject.quote(url)
        evaluateJavascript("window.Avatar3D && window.Avatar3D.load($quoted)", null)
    }
}
