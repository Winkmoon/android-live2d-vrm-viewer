@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.winkmoon.xinran

import android.app.Activity

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.delay

enum class ViewerMode { Live2D, Avatar3D }

/**
 * The single UI shell. The same top bar, bottom floating bar, stats card and
 * settings sheet stay mounted while the content area swaps between the Live2D
 * GL surface and the 3D WebView.
 */
@Composable
fun UnifiedViewerApp(
    initial3D: Boolean = false,
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onViewCreated: (Live2DView) -> Unit = {}
) {
    val context = LocalContext.current
    AppSettings.init(context)
    RootShell.init(context)

    var mode by rememberSaveable {
        mutableStateOf(if (initial3D) ViewerMode.Avatar3D else ViewerMode.Live2D)
    }
    var avatarEverOpened by remember { mutableStateOf(initial3D) }
    var showStats by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var rootAvailable by remember { mutableStateOf(false) }
    var rootForceEnabled by remember { mutableStateOf(false) }
    var rootForceBusy by remember { mutableStateOf(false) }
    var rootForceStatus by remember { mutableStateOf<String?>(null) }
    var rootCheckNonce by remember { mutableIntStateOf(0) }

    val liveState = remember { Live2DContentState() }
    val avatarState = remember { Avatar3DContentState() }

    LaunchedEffect(rootCheckNonce, RootShell.backend) {
        rootAvailable = RootRefreshRateForcer.isAvailable()
    }

    LaunchedEffect(rootForceEnabled, AppSettings.targetFps) {
        if (rootForceEnabled) {
            rootForceBusy = true
            val target = if (AppSettings.targetFps > 0) {
                AppSettings.targetFps
            } else {
                RefreshRateUtils.defaultFps(context)
            }
            val result = RootRefreshRateForcer.enable(context, target)
            if (result.isSuccess) {
                delay(600)
                val actual = RefreshRateUtils.actualRefreshRate(context)
                rootForceStatus = context.getString(
                    R.string.root_force_requested,
                    target,
                    actual
                )
            } else {
                rootForceStatus = context.getString(
                    R.string.root_force_failed,
                    result.exceptionOrNull()?.message ?: "unknown"
                )
                rootForceEnabled = false
            }
            rootForceBusy = false
        } else {
            RootRefreshRateForcer.disable(context)
            rootForceStatus = context.getString(R.string.root_force_disabled)
        }
    }

    LaunchedEffect(AppSettings.targetFps) {
        val activity = context.findActivityOrNull()
        val target = if (AppSettings.targetFps > 0) {
            AppSettings.targetFps
        } else {
            RefreshRateUtils.defaultFps(context)
        }
        activity?.let { RefreshRateController.apply(it, target) }
    }

    var languageTag by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }
    var orientationMode by remember { mutableStateOf(OrientationPrefs.load(context)) }

    fun selectMode(next: ViewerMode) {
        showSettings = false
        if (next == ViewerMode.Avatar3D) {
            avatarEverOpened = true
            liveState.dismissSheets?.invoke()
        } else {
            avatarState.dismissSheets?.invoke()
        }
        mode = next
    }

    BackHandler(enabled = mode == ViewerMode.Avatar3D) {
        selectMode(ViewerMode.Live2D)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Both content areas stay composed after their first use, so the GL
        // context and the WebView keep their loaded models when switching.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (mode == ViewerMode.Live2D) 1f else 0f)
                .alpha(if (mode == ViewerMode.Live2D) 1f else 0f)
        ) {
            Live2DContent(
                state = liveState,
                active = mode == ViewerMode.Live2D,
                onViewCreated = onViewCreated
            )
        }

        if (avatarEverOpened) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (mode == ViewerMode.Avatar3D) 1f else 0f)
                    .alpha(if (mode == ViewerMode.Avatar3D) 1f else 0f)
            ) {
                Avatar3DContent(
                    state = avatarState,
                    active = mode == ViewerMode.Avatar3D,
                    onWebViewCreated = {}
                )
            }
        }

        if (!liveState.chromeHidden) {
        Box(modifier = Modifier.zIndex(2f)) {
        ViewerTopBar(
            selected3D = mode == ViewerMode.Avatar3D,
            fps = if (mode == ViewerMode.Live2D) liveState.stats.fps else avatarState.stats.fps,
            onSelect2D = { selectMode(ViewerMode.Live2D) },
            onSelect3D = { selectMode(ViewerMode.Avatar3D) },
            trailing = {
                IconButton(onClick = { showStats = !showStats }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = if (showStats) {
                            stringResource(R.string.hide_performance)
                        } else {
                            stringResource(R.string.show_performance)
                        },
                        tint = if (showStats) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        }
        AnimatedVisibility(
            visible = showStats && !liveState.chromeHidden,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(2f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 124.dp, end = 16.dp)
        ) {
            if (mode == ViewerMode.Live2D) {
                StatsCard(
                    stats = liveState.stats,
                    targetFps = AppSettings.targetFps,
                    adpfEnabled = AppSettings.adpfEnabled
                )
            } else {
                Avatar3DStatsCard(avatarState.stats)
            }
        }

        Box(modifier = Modifier.zIndex(2f)) {
        ViewerBottomBar(
            selected3D = mode == ViewerMode.Avatar3D,
            onModels = {
                if (mode == ViewerMode.Live2D) {
                    liveState.openModels?.invoke()
                } else {
                    avatarState.openModels?.invoke()
                }
            },
            onSettings = { showSettings = true },
            onMotions = { liveState.openMotions?.invoke() },
            onResetView = { avatarState.resetView?.invoke() }
        )
        }
        }
    }

    if (showSettings && !liveState.chromeHidden) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UnifiedSettingsContent(
                    selected3D = mode == ViewerMode.Avatar3D,
                    languageTag = languageTag,
                    orientationMode = orientationMode,
                    onLanguage = { tag ->
                        languageTag = tag
                        val locales = if (tag.isBlank()) {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            LocaleListCompat.forLanguageTags(tag)
                        }
                        AppCompatDelegate.setApplicationLocales(locales)
                    },
                    onOrientation = { next ->
                        orientationMode = next
                        OrientationPrefs.save(context, next)
                        context.findActivityOrNull()?.let { activity ->
                            OrientationPrefs.apply(activity, next)
                        }
                    },
                    rootAvailable = liveState.rootAvailable,
                    rootEnabled = liveState.rootEnabled,
                    rootBusy = liveState.rootBusy,
                    rootStatus = liveState.rootStatus,
                    onRoot = liveState.toggleRoot,
                    onReloadModel = liveState.reloadModel,
                    onResetView3D = avatarState.resetView,
                    rootRefreshAvailable = rootAvailable,
                    rootRefreshEnabled = rootForceEnabled,
                    rootRefreshBusy = rootForceBusy,
                    rootRefreshStatus = rootForceStatus,
                    onRootRefresh = { rootForceEnabled = it },
                    onCheckRoot = { rootCheckNonce++ },
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
        }
    }
}
