@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.winkmoon.xinran

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.LocaleListCompat
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private enum class Sheet { Models, Motions, Settings }

data class PerfStats(
    val fps: Float = 0f,
    val frameMs: Float = 0f,
    val cpuMs: Float = 0f,
    val drawCalls: Int = 0,
    val vertices: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val textureMB: Float = 0f,
    val modelIndex: Int = 0,
    val modelCount: Int = 0,
    val maskScale: Float = 1f,
    val renderThreadTid: Int = 0
)

private data class MotionGroupInfo(val group: String, val count: Int)
private data class ModelInfo(
    val name: String = "",
    val drawables: Int = 0,
    val textures: Int = 0,
    val textureMB: Float = 0f,
    val motions: List<MotionGroupInfo> = emptyList(),
    val expressions: List<String> = emptyList()
)

@Composable
fun Live2DContent(
    state: Live2DContentState,
    active: Boolean,
    onViewCreated: (Live2DView) -> Unit
) {
    val context = LocalContext.current
    AppSettings.init(context)
    val scope = rememberCoroutineScope()
    var chromeHideJob by remember { mutableStateOf<Job?>(null) }
    val colorScheme = MaterialTheme.colorScheme

    var renderView by remember { mutableStateOf<Live2DView?>(null) }
    var stats by remember { mutableStateOf(PerfStats()) }
    var modelNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelInfo by remember { mutableStateOf<ModelInfo?>(null) }
    var infoNonce by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    val defaultFps = remember(context) {
        RefreshRateUtils.defaultFps(context)
    }

    val targetFps = if (AppSettings.targetFps > 0) AppSettings.targetFps else defaultFps
    val renderScale = AppSettings.renderScale
    val maskScale = AppSettings.maskScale
    val autoResolution = AppSettings.autoResolution
    val mipmaps = AppSettings.mipmaps
    val adpfEnabled = AppSettings.adpfEnabled

    LaunchedEffect(defaultFps) {
        val tiers = RefreshRateUtils.supportedFpsTiers(context)
        if (AppSettings.targetFps <= 0 || AppSettings.targetFps !in tiers) {
            AppSettings.updateTargetFps(defaultFps)
        }
    }
    var rootAvailable by remember { mutableStateOf(false) }
    var rootEnabled by remember { mutableStateOf(false) }
    var rootBusy by remember { mutableStateOf(false) }
    var rootStatus by remember { mutableStateOf(context.getString(R.string.root_status_checking)) }
    var glVersion by remember { mutableStateOf("") }
    var languageTag by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags()) }
    var orientationMode by remember { mutableStateOf(OrientationPrefs.load(context)) }
    var playingMotion by remember { mutableStateOf<String?>(null) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    var importingModel by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var feedbackNonce by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(active, renderView) {
        if (active) renderView?.onResume() else renderView?.onPause()
    }

    fun showFeedback(message: String) {
        actionFeedback = message
        feedbackNonce++
    }

    fun hideChromeForMotion() {
        if (!AppSettings.hideUiOnMotion) return
        chromeHideJob?.cancel()
        state.chromeHidden = true
        chromeHideJob = scope.launch {
            var elapsed = 0
            while (elapsed < 12_000) {
                val view = renderView
                if (view == null) break
                val result = CompletableDeferred<Boolean>()
                view.queryUserMotionPlaying { result.complete(it) }
                val playing = withTimeoutOrNull(700) { result.await() } ?: false
                if (!playing) break
                delay(100)
                elapsed += 100
            }
            state.chromeHidden = false
        }
    }

    LaunchedEffect(actionFeedback, feedbackNonce) {
        if (actionFeedback != null) {
            delay(2200)
            actionFeedback = null
        }
    }

    val importModelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importingModel = true
                importError = null
                val result = withContext(Dispatchers.IO) {
                    extractModelZip(context, uri)
                }
                result.onSuccess { importedName ->
                    val view = renderView
                    if (view == null) {
                        importingModel = false
                        importError = context.getString(R.string.model_import_failed)
                        return@onSuccess
                    }
                    view.refreshModels { names ->
                        modelNames = names.toList()
                        val index = names.indexOf(importedName)
                        if (index >= 0) {
                            renderView?.setModel(index)
                            infoNonce++
                            sheet = null
                            showFeedback(
                                context.getString(R.string.model_imported, importedName)
                            )
                        } else {
                            importError = context.getString(R.string.model_import_failed)
                        }
                        importingModel = false
                    }
                }.onFailure { error ->
                    importingModel = false
                    importError = context.getString(R.string.model_import_failed) +
                        (error.message?.let { "\n$it" } ?: "")
                }
            }
        }
    }

    SideEffect {
        state.stats = stats
        state.modelName = modelInfo?.name?.ifEmpty { modelNames.getOrNull(stats.modelIndex) }.orEmpty()
        state.openModels = { sheet = Sheet.Models }
        state.openMotions = { sheet = Sheet.Motions }
        state.dismissSheets = { sheet = null }
        state.importModel = {
            importModelLauncher.launch(
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream"
                )
            )
        }
        state.reloadModel = {
            renderView?.setModel(stats.modelIndex)
            infoNonce++
        }
        state.rootAvailable = rootAvailable
        state.rootEnabled = rootEnabled
        state.rootBusy = rootBusy
        state.rootStatus = rootStatus
        state.toggleRoot = { enabled ->
            if (!rootBusy) {
                rootBusy = true
                scope.launch {
                    val result = if (enabled) RootTuner.enable() else RootTuner.disable()
                    rootEnabled = enabled && result.ok
                    rootStatus = if (result.ok) {
                        if (enabled) context.getString(R.string.root_status_enabled)
                        else context.getString(R.string.root_status_restored)
                    } else {
                        context.getString(R.string.root_status_failed, result.message)
                    }
                    rootBusy = false
                }
            }
        }
    }

    val currentStats by rememberUpdatedState(stats)
    val currentScale by rememberUpdatedState(renderScale)
    val statsArray = remember { FloatArray(12) }

    LaunchedEffect(Unit) {
        modelNames = runCatching { nativeGetModelNames().toList() }
            .getOrDefault(emptyList())
        rootAvailable = RootTuner.isRootAvailable()
        rootStatus = if (rootAvailable) {
            context.getString(R.string.root_status_available)
        } else {
            context.getString(R.string.root_status_unavailable)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                nativeGetStats(statsArray)
                stats = PerfStats(
                    fps = statsArray[0],
                    frameMs = statsArray[1],
                    cpuMs = statsArray[2],
                    drawCalls = statsArray[3].toInt(),
                    vertices = statsArray[4].toInt(),
                    width = statsArray[5].toInt(),
                    height = statsArray[6].toInt(),
                    textureMB = statsArray[7],
                    modelIndex = statsArray[8].toInt(),
                    modelCount = statsArray[9].toInt(),
                    maskScale = statsArray[10],
                    renderThreadTid = statsArray[11].toInt()
                )
            }
            delay(200)
        }
    }

    LaunchedEffect(stats.modelIndex, stats.modelCount, infoNonce) {
        // The GL thread may still be loading the model when stats first update.
        // Keep polling until the model is actually resident, otherwise the
        // Motion sheet stays empty forever with the initial empty JSON.
        modelInfo = null
        if (stats.modelCount > 0) {
            repeat(20) {
                delay(250)
                val info = runCatching {
                    parseModelInfo(nativeGetModelInfoJson())
                }.getOrNull()
                if (info != null && info.name.isNotEmpty() && info.drawables > 0) {
                    modelInfo = info
                    return@LaunchedEffect
                }
            }
        }
    }

    LaunchedEffect(stats.fps > 0f) {
        if (stats.fps > 0f) {
            glVersion = renderView?.glVersion.orEmpty()
        }
    }

    LaunchedEffect(renderView, targetFps) {
        renderView?.setTargetFps(targetFps)
    }
    LaunchedEffect(renderView, adpfEnabled) {
        renderView?.setAdpfEnabled(adpfEnabled)
    }
    LaunchedEffect(renderView, renderScale) {
        renderView?.setRenderScale(renderScale)
    }
    LaunchedEffect(renderView, maskScale) {
        renderView?.setMaskScale(maskScale)
    }
    LaunchedEffect(renderView, mipmaps) {
        renderView?.setMipmaps(mipmaps)
    }
    LaunchedEffect(renderView, colorScheme.background) {
        renderView?.setBackgroundColor(
            colorScheme.background.red,
            colorScheme.background.green,
            colorScheme.background.blue
        )
    }

    // Dynamic resolution: scale the render buffer, not the view, so the model
    // still fills the screen through SurfaceFlinger's bilinear upscale.
    LaunchedEffect(autoResolution, targetFps) {
        if (!autoResolution) return@LaunchedEffect
        var lowStreak = 0
        var highStreak = 0
        var lastChangeAt = 0L
        while (isActive) {
            delay(1000)
            val fps = currentStats.fps
            if (fps <= 0f) continue
            val target = targetFps * 0.92f
            lowStreak = if (fps < target * 0.86f) lowStreak + 1 else 0
            highStreak = if (fps > target * 0.99f) highStreak + 1 else 0

            val now = System.currentTimeMillis()
            if (now - lastChangeAt < 3000L) continue

            var next = currentScale
            when {
                lowStreak >= 3 && next > 0.6f -> next -= 0.05f
                highStreak >= 3 && next < 1f -> next += 0.03f
                else -> continue
            }
            next = next.coerceIn(0.6f, 1f)
            if (next != renderScale) {
                AppSettings.updateRenderScale(next)
                lastChangeAt = now
                lowStreak = 0
                highStreak = 0
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            RootTuner.restoreIfActive()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                Live2DView(ctx).also { view ->
                    renderView = view
                    onViewCreated(view)
                    view.setTargetFps(targetFps)
                    view.setRenderScale(renderScale)
                    view.setMaskScale(maskScale)
                    view.setMipmaps(mipmaps)
                    view.setBackgroundColor(
                        colorScheme.background.red,
                        colorScheme.background.green,
                        colorScheme.background.blue
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // A translucent scrim keeps text legible while the GL surface remains visible.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.34f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.42f)
                        )
                    )
                )
        )

        AnimatedVisibility(
            visible = actionFeedback != null && !state.chromeHidden,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 96.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = actionFeedback ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (stats.fps <= 0f && System.currentTimeMillis() - startedAt < 12_000) {
            LoadingOverlay()
        }

        sheet?.let { current ->
            AppSheet(
                sheet = current,
                modelNames = modelNames,
                selectedModel = stats.modelIndex,
                modelInfo = modelInfo,
                playingMotion = playingMotion,
                importingModel = importingModel,
                importError = importError,
                stats = stats,
                targetFps = targetFps,
                renderScale = renderScale,
                maskScale = maskScale,
                autoResolution = autoResolution,
                mipmaps = mipmaps,
                adpfEnabled = adpfEnabled,
                rootAvailable = rootAvailable,
                rootEnabled = rootEnabled,
                rootBusy = rootBusy,
                rootStatus = rootStatus,
                glVersion = glVersion,
                languageTag = languageTag,
                orientationMode = orientationMode,
                onDismiss = { sheet = null },
                onImportModel = {
                    importError = null
                    importModelLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream"
                        )
                    )
                },
                onSelectModel = { index ->
                    sheet = null
                    playingMotion = null
                    renderView?.setModel(index)
                    infoNonce++
                },
                onPlayMotion = { group, index ->
                    renderView?.startMotion(group, index)
                    hideChromeForMotion()
                    playingMotion = "$group:$index"
                    showFeedback(
                        context.getString(
                            R.string.now_playing,
                            "${motionGroupLabel(context, group)} ${index + 1}"
                        )
                    )
                    sheet = null
                },
                onRandomMotion = { group ->
                    renderView?.startRandomMotion(group)
                    hideChromeForMotion()
                    playingMotion = null
                    showFeedback(
                        context.getString(
                            R.string.random_motion_playing,
                            motionGroupLabel(context, group)
                        )
                    )
                    sheet = null
                },
                onExpression = { name ->
                    renderView?.setExpression(name)
                    showFeedback(context.getString(R.string.expression_applied, name))
                    sheet = null
                },
                onResetParameters = {
                    renderView?.resetParameters()
                    playingMotion = null
                    showFeedback(context.getString(R.string.parameters_reset))
                    sheet = null
                },
                onTargetFps = { fps -> AppSettings.updateTargetFps(fps) },
                onRenderScale = {
                    AppSettings.updateAutoResolution(false)
                    AppSettings.updateRenderScale(it)
                },
                onMaskScale = { AppSettings.updateMaskScale(it) },
                onAutoResolution = { AppSettings.updateAutoResolution(it) },
                onMipmaps = {
                    AppSettings.updateMipmaps(it)
                    renderView?.setMipmaps(it)
                    renderView?.setModel(stats.modelIndex)
                },
                onAdpf = { AppSettings.updateAdpfEnabled(it) },
                onRoot = { enabled ->
                    if (!rootBusy) {
                        rootBusy = true
                        scope.launch {
                            val result = if (enabled) RootTuner.enable() else RootTuner.disable()
                            rootEnabled = enabled && result.ok
                            rootStatus = if (result.ok) {
                                if (enabled) context.getString(R.string.root_status_enabled)
                                else context.getString(R.string.root_status_restored)
                            } else {
                                context.getString(R.string.root_status_failed, result.message)
                            }
                            rootBusy = false
                        }
                    }
                },
                onReloadModel = {
                    renderView?.setModel(stats.modelIndex)
                    infoNonce++
                },
                onLanguage = { tag ->
                    languageTag = tag
                    val locales = if (tag.isBlank()) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(tag)
                    }
                    AppCompatDelegate.setApplicationLocales(locales)
                },
                onOrientation = { mode ->
                    orientationMode = mode
                    OrientationPrefs.save(context, mode)
                    context.findActivityOrNull()?.let { activity ->
                        OrientationPrefs.apply(activity, mode)
                    }
                }
            )
        }
    }
}

@Composable
private fun TopBar(
    modelName: String,
    fps: Float,
    showStats: Boolean,
    onToggleStats: () -> Unit,
    onOpen3D: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ModeSwitcher(
            selected3D = false,
            onSelect2D = {},
            onSelect3D = onOpen3D
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M3",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Moon",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (fps >= 55f) Color(0xFF4ADE80) else Color(0xFFFBBF24)
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.fps_value, fps.roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            IconButton(onClick = onToggleStats) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = if (showStats) stringResource(R.string.hide_performance) else stringResource(R.string.show_performance),
                    tint = if (showStats) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }
    }
}

@Composable
fun StatsCard(stats: PerfStats, targetFps: Int, adpfEnabled: Boolean) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier
                .width(238.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stats.fps.roundToInt().toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.fps_unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.target_fps_short, targetFps),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            StatRow(stringResource(R.string.stat_frame), formatMs(stats.frameMs))
            StatRow(stringResource(R.string.stat_cpu), formatMs(stats.cpuMs))
            StatRow(stringResource(R.string.stat_draws), stats.drawCalls.toString())
            StatRow(stringResource(R.string.stat_vertices), compact(stats.vertices))
            StatRow(stringResource(R.string.stat_buffer), "${stats.width}×${stats.height}")
            StatRow(stringResource(R.string.stat_textures), String.format(Locale.getDefault(), "%.1f MB", stats.textureMB))
            StatRow(stringResource(R.string.stat_mask), "${(stats.maskScale * 100f).roundToInt()}%")
            StatRow(stringResource(R.string.stat_adpf), if (adpfEnabled) stringResource(R.string.on) else stringResource(R.string.off))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun BottomToolbar(
    onModels: () -> Unit,
    onMotions: () -> Unit,
    onSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolbarAction(stringResource(R.string.toolbar_models), Icons.Default.List, onModels)
                ToolbarAction(stringResource(R.string.toolbar_motion), Icons.Default.Face, onMotions)
                ToolbarAction(stringResource(R.string.toolbar_settings), Icons.Default.Settings, onSettings)
            }
        }
    }
}

@Composable
private fun RowScope.ToolbarAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LoadingOverlay() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LoadingIndicator(modifier = Modifier.size(52.dp))
                Text(
                    text = stringResource(R.string.loading_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.loading_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSheet(
    sheet: Sheet,
    modelNames: List<String>,
    selectedModel: Int,
    modelInfo: ModelInfo?,
    playingMotion: String?,
    importingModel: Boolean,
    importError: String?,
    stats: PerfStats,
    targetFps: Int,
    renderScale: Float,
    maskScale: Float,
    autoResolution: Boolean,
    mipmaps: Boolean,
    adpfEnabled: Boolean,
    rootAvailable: Boolean,
    rootEnabled: Boolean,
    rootBusy: Boolean,
    rootStatus: String,
    glVersion: String,
    languageTag: String,
    orientationMode: OrientationMode,
    onDismiss: () -> Unit,
    onImportModel: () -> Unit,
    onSelectModel: (Int) -> Unit,
    onPlayMotion: (String, Int) -> Unit,
    onRandomMotion: (String) -> Unit,
    onExpression: (String) -> Unit,
    onResetParameters: () -> Unit,
    onTargetFps: (Int) -> Unit,
    onRenderScale: (Float) -> Unit,
    onMaskScale: (Float) -> Unit,
    onAutoResolution: (Boolean) -> Unit,
    onMipmaps: (Boolean) -> Unit,
    onAdpf: (Boolean) -> Unit,
    onRoot: (Boolean) -> Unit,
    onReloadModel: () -> Unit,
    onLanguage: (String) -> Unit,
    onOrientation: (OrientationMode) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (sheet) {
                Sheet.Models -> ModelsContent(
                    modelNames, selectedModel, modelInfo, onImportModel,
                    importingModel, importError, onSelectModel
                )
                Sheet.Motions -> MotionsContent(
                    modelInfo, playingMotion, onPlayMotion, onRandomMotion,
                    onExpression, onResetParameters
                )
                Sheet.Settings -> UnifiedSettingsContent(
                    selected3D = false,
                    languageTag = languageTag,
                    orientationMode = orientationMode,
                    onLanguage = onLanguage,
                    onOrientation = onOrientation,
                    rootAvailable = rootAvailable,
                    rootEnabled = rootEnabled,
                    rootBusy = rootBusy,
                    rootStatus = rootStatus,
                    onRoot = onRoot,
                    onReloadModel = onReloadModel
                )
            }
        }
    }
}

@Composable
private fun ModelsContent(
    modelNames: List<String>,
    selectedModel: Int,
    modelInfo: ModelInfo?,
    onImportModel: () -> Unit,
    importingModel: Boolean,
    importError: String?,
    onSelectModel: (Int) -> Unit
) {
    SectionTitle(stringResource(R.string.model_library), stringResource(R.string.model_library_subtitle))

    OutlinedButton(
        onClick = onImportModel,
        enabled = !importingModel,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.import_model))
    }
    Text(
        stringResource(R.string.import_model_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (importingModel) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.importing_model), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (importError != null) {
        Text(
            importError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    if (modelNames.isEmpty()) {
        Text(stringResource(R.string.no_models), color = MaterialTheme.colorScheme.error)
    }
    modelNames.forEachIndexed { index, name ->
        val selected = index == selectedModel
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectModel(index) },
            shape = RoundedCornerShape(22.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f)
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.take(1),
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (selected && modelInfo != null) {
                            stringResource(
                                R.string.model_summary,
                                modelInfo.drawables,
                                modelInfo.textures,
                                modelInfo.textureMB
                            )
                        } else {
                            stringResource(R.string.model_tap_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    Text(
        text = stringResource(R.string.model_license_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MotionsContent(
    modelInfo: ModelInfo?,
    playingMotion: String?,
    onPlayMotion: (String, Int) -> Unit,
    onRandomMotion: (String) -> Unit,
    onExpression: (String) -> Unit,
    onResetParameters: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    SectionTitle(stringResource(R.string.motion_title), modelInfo?.name ?: stringResource(R.string.loading_model))
    Text(
        stringResource(R.string.motion_sheet_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (modelInfo == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.reading_motions))
        }
        return
    }

    modelInfo.motions.forEach { group ->
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(localizedMotionGroup(group.group), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.motion_count, group.count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRandomMotion(group.group)
                    }) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.random))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(group.count) { index ->
                        val key = "${group.group}:$index"
                        FilterChip(
                            selected = playingMotion == key,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayMotion(group.group, index)
                            },
                            label = {
                                Text(
                                    "${localizedMotionGroup(group.group).take(1)}${index + 1}"
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (modelInfo.expressions.isNotEmpty()) {
        Text(stringResource(R.string.expressions), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modelInfo.expressions.forEach { name ->
                AssistChip(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onExpression(name)
                    },
                    label = { Text(name) }
                )
            }
        }
    } else {
        Text(
            stringResource(R.string.no_expressions),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    OutlinedButton(onClick = onResetParameters) {
        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.reset_parameters))
    }
}

@Composable
private fun SettingsContent(
    stats: PerfStats,
    targetFps: Int,
    renderScale: Float,
    maskScale: Float,
    autoResolution: Boolean,
    mipmaps: Boolean,
    adpfEnabled: Boolean,
    rootAvailable: Boolean,
    rootEnabled: Boolean,
    rootBusy: Boolean,
    rootStatus: String,
    glVersion: String,
    languageTag: String,
    orientationMode: OrientationMode,
    onTargetFps: (Int) -> Unit,
    onRenderScale: (Float) -> Unit,
    onMaskScale: (Float) -> Unit,
    onAutoResolution: (Boolean) -> Unit,
    onMipmaps: (Boolean) -> Unit,
    onAdpf: (Boolean) -> Unit,
    onRoot: (Boolean) -> Unit,
    onReloadModel: () -> Unit,
    onLanguage: (String) -> Unit,
    onOrientation: (OrientationMode) -> Unit
) {
    SectionTitle(stringResource(R.string.performance_title), stringResource(R.string.performance_subtitle))

    Text(stringResource(R.string.target_frame_rate), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(60, 90, 120, 144).forEach { fps ->
            FilterChip(
                selected = targetFps == fps,
                onClick = { onTargetFps(fps) },
                label = { Text("$fps") }
            )
        }
    }
    Text(
        stringResource(R.string.target_frame_rate_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    SliderRow(
        title = stringResource(R.string.render_scale),
        valueText = "${(renderScale * 100f).roundToInt()}%",
        value = renderScale,
        range = 0.5f..1f,
        onValueChange = onRenderScale
    )
    SwitchRow(
        title = stringResource(R.string.adaptive_resolution),
        subtitle = stringResource(R.string.adaptive_resolution_subtitle),
        checked = autoResolution,
        onCheckedChange = onAutoResolution
    )
    SliderRow(
        title = stringResource(R.string.mask_resolution),
        valueText = "${(maskScale * 100f).roundToInt()}%",
        value = maskScale,
        range = 0.4f..1f,
        onValueChange = onMaskScale,
        subtitle = stringResource(R.string.mask_resolution_subtitle)
    )
    SwitchRow(
        title = stringResource(R.string.mipmaps),
        subtitle = stringResource(R.string.mipmaps_subtitle),
        checked = mipmaps,
        onCheckedChange = onMipmaps
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    Text(stringResource(R.string.scheduling), style = MaterialTheme.typography.titleSmall)
    SwitchRow(
        title = stringResource(R.string.adpf),
        subtitle = if (Build.VERSION.SDK_INT >= 31) {
            stringResource(R.string.adpf_subtitle)
        } else {
            stringResource(R.string.adpf_subtitle_unsupported)
        },
        checked = adpfEnabled,
        enabled = Build.VERSION.SDK_INT >= 31,
        onCheckedChange = onAdpf
    )
    SwitchRow(
        title = stringResource(R.string.root_mode),
        subtitle = rootStatus,
        checked = rootEnabled,
        enabled = rootAvailable && !rootBusy,
        onCheckedChange = onRoot
    )
    if (rootBusy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.waiting_root), style = MaterialTheme.typography.bodySmall)
        }
    }
    Text(
        stringResource(R.string.root_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    Text(stringResource(R.string.runtime), style = MaterialTheme.typography.titleSmall)
    StatRow(stringResource(R.string.gl), glVersion.ifEmpty { stringResource(R.string.initializing) })
    StatRow(stringResource(R.string.render_thread), stringResource(R.string.render_thread_tid, stats.renderThreadTid))
    StatRow(stringResource(R.string.stat_buffer), "${stats.width}×${stats.height}")
    StatRow(stringResource(R.string.draw_calls), stats.drawCalls.toString())
    StatRow(stringResource(R.string.stat_vertices), compact(stats.vertices))
    StatRow(stringResource(R.string.stat_textures), String.format(Locale.getDefault(), "%.1f MB", stats.textureMB))
    StatRow(stringResource(R.string.model), "${stats.modelIndex + 1} / ${stats.modelCount}")

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    SectionTitle(
        stringResource(R.string.orientation),
        stringResource(R.string.orientation_subtitle)
    )
    OrientationRow(current = orientationMode, onOrientation = onOrientation)

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    SectionTitle(
        stringResource(R.string.language),
        stringResource(R.string.language_subtitle)
    )
    LanguageRow(current = languageTag, onLanguage = onLanguage)

    OutlinedButton(onClick = onReloadModel) {
        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.reload_current_model))
    }
}

@Composable
private fun OrientationRow(
    current: OrientationMode,
    onOrientation: (OrientationMode) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val options = listOf(
        OrientationMode.System to R.string.orientation_system,
        OrientationMode.Portrait to R.string.orientation_portrait,
        OrientationMode.Landscape to R.string.orientation_landscape
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (mode, labelRes) ->
            FilterChip(
                selected = current == mode,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOrientation(mode)
                },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}

@Composable
private fun LanguageRow(current: String, onLanguage: (String) -> Unit) {
    val options = listOf(
        "" to R.string.language_system,
        "en" to R.string.language_english,
        "zh-CN" to R.string.language_simplified_chinese,
        "zh-TW" to R.string.language_traditional_chinese,
        "ja" to R.string.language_japanese,
        "ko" to R.string.language_korean
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (tag, labelRes) ->
            FilterChip(
                selected = current.equals(tag, ignoreCase = true),
                onClick = { onLanguage(tag) },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}

@Composable
private fun localizedMotionGroup(group: String): String = when (group) {
    "Idle" -> stringResource(R.string.motion_idle)
    "TapBody" -> stringResource(R.string.motion_tap_body)
    else -> group
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    subtitle: String? = null
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private fun extractModelZip(context: Context, uri: Uri): Result<String> = runCatching {
    val cacheRoot = File(context.cacheDir, "model_import").apply {
        deleteRecursively()
        mkdirs()
    }
    try {
        val zipFile = File(cacheRoot, "model.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            zipFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot open the selected file")

        val extractRoot = File(cacheRoot, "unzipped").apply { mkdirs() }
        val extractRootPath = extractRoot.canonicalPath + File.separator
        var totalBytes = 0L
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (name.isBlank()) {
                    zis.closeEntry()
                    continue
                }
                val outFile = File(extractRoot, name).canonicalFile
                if (outFile.path != extractRoot.canonicalPath &&
                    !outFile.path.startsWith(extractRootPath)
                ) {
                    error("Unsafe zip path: $name")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                    zis.closeEntry()
                    continue
                }
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = zis.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > 512L * 1024L * 1024L) {
                            error("Zip is too large")
                        }
                        output.write(buffer, 0, read)
                    }
                }
                zis.closeEntry()
            }
        }

        val modelJson = extractRoot.walkTopDown()
            .firstOrNull { it.isFile && it.name.endsWith(".model3.json") }
            ?: error("No .model3.json found in the zip")
        val modelRoot = modelJson.parentFile ?: extractRoot
        val rawName = if (modelRoot == extractRoot) {
            modelJson.name.removeSuffix(".model3.json")
        } else {
            modelRoot.name
        }
        val baseName = buildString {
            for (ch in rawName) {
                append(
                    if (ch == '\\' || ch == '/' || ch == ':' || ch == '*' ||
                    ch == '?' || ch == '"' || ch == '<' || ch == '>' || ch == '|')
                        '_'
                    else
                        ch
                )
            }
        }.trim().ifBlank { "ImportedModel" }

        val modelsRoot = File(context.filesDir, "models").apply { mkdirs() }
        var target = File(modelsRoot, baseName)
        var suffix = 2
        while (target.exists()) {
            target = File(modelsRoot, "${baseName}_$suffix")
            suffix++
        }
        modelRoot.copyRecursively(target, overwrite = false)
        if (target.walkTopDown().none { it.isFile && it.name.endsWith(".model3.json") }) {
            error("Imported model is missing .model3.json")
        }
        target.name
    } finally {
        cacheRoot.deleteRecursively()
    }
}

private fun motionGroupLabel(context: Context, group: String): String = when (group) {
    "Idle" -> context.getString(R.string.motion_idle)
    "TapBody" -> context.getString(R.string.motion_tap_body)
    else -> group
}

private fun parseModelInfo(json: String): ModelInfo {
    val root = JSONObject(json)
    val motions = mutableListOf<MotionGroupInfo>()
    root.optJSONArray("motions")?.let { array ->
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            motions += MotionGroupInfo(
                group = item.optString("group"),
                count = item.optInt("count")
            )
        }
    }
    val expressions = mutableListOf<String>()
    root.optJSONArray("expressions")?.let { array ->
        for (i in 0 until array.length()) {
            expressions += array.optString(i)
        }
    }
    return ModelInfo(
        name = root.optString("name"),
        drawables = root.optInt("drawables"),
        textures = root.optInt("textures"),
        textureMB = root.optDouble("textureMB", 0.0).toFloat(),
        motions = motions,
        expressions = expressions
    )
}

private fun formatMs(value: Float): String =
    if (value <= 0f) "—" else String.format(Locale.getDefault(), "%.2f ms", value)

private fun compact(value: Int): String = when {
    value >= 1_000_000 -> String.format(Locale.getDefault(), "%.2fM", value / 1_000_000f)
    value >= 1_000 -> String.format(Locale.getDefault(), "%.1fk", value / 1_000f)
    else -> value.toString()
}
