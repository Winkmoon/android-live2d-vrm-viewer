package com.winkmoon.xinran

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var renderView: Live2DView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        RootShell.init(this)
        RootRefreshRateForcer.restoreIfActive(this)
        OrientationPrefs.apply(this, OrientationPrefs.load(this))
        initialize(this, this)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            MoonApp(onViewCreated = { view -> renderView = view })
        }
    }

    override fun onStart() {
        super.onStart()
        nativeOnStart()
    }

    override fun onResume() {
        super.onResume()
        RefreshRateController.apply(
            this,
            if (AppSettings.targetFps > 0) AppSettings.targetFps
            else RefreshRateUtils.defaultFps(this)
        )
        renderView?.onResume()
        if (RootRefreshRateForcer.requested) {
            lifecycleScope.launch {
                RootRefreshRateForcer.enable(
                    this@MainActivity,
                    if (AppSettings.targetFps > 0) AppSettings.targetFps
                    else RefreshRateUtils.defaultFps(this@MainActivity)
                )
            }
        }
    }

    override fun onPause() {
        renderView?.onPause()
        nativeOnPause()
        super.onPause()
    }

    override fun onStop() {
        RootRefreshRateForcer.restoreIfActive(this)
        nativeOnStop()
        RootTuner.restoreIfActive()
        super.onStop()
    }

    override fun onDestroy() {
        renderView?.releaseAdpf()
        RootRefreshRateForcer.restoreIfActive(this)
        RootTuner.restoreIfActive()
        nativeOnDestroy()
        shutdown()
        super.onDestroy()
    }
}
