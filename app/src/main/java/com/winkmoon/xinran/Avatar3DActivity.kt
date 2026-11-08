@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.winkmoon.xinran

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.LocaleListCompat
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AVATAR_PREFS = "m3e_avatar3d"
private const val LAST_AVATAR_KEY = "last_avatar"

private val AVATAR_MIME_TYPES = arrayOf(
    "model/vrm",
    "model/gltf-binary",
    "application/octet-stream",
    "*/*"
)

class Avatar3DActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrientationPrefs.apply(this, OrientationPrefs.load(this))
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MoonApp(initial3D = true)
        }
    }
}

@Composable
fun Avatar3DContent(
    state: Avatar3DContentState,
    active: Boolean,
    onWebViewCreated: (Avatar3DWebView) -> Unit
) {
    val context = LocalContext.current
    AppSettings.init(context)
    val scope = rememberCoroutineScope()
    val avatarsDir = remember { File(context.filesDir, "avatars").apply { mkdirs() } }
    val prefs = remember { context.getSharedPreferences(AVATAR_PREFS, Context.MODE_PRIVATE) }

    var avatars by remember { mutableStateOf(listAvatars(avatarsDir)) }
    var current by remember { mutableStateOf<File?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<Avatar3DWebView?>(null) }
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showModels by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(Avatar3DStats()) }

    fun selectAvatar(file: File) {
        current = file
        prefs.edit().putString(LAST_AVATAR_KEY, file.name).apply()
        status = null
    }

    LaunchedEffect(Unit) {
        avatars = listAvatars(avatarsDir)
        val lastName = prefs.getString(LAST_AVATAR_KEY, null)
        current = avatars.firstOrNull { it.name == lastName } ?: avatars.firstOrNull()
    }

    LaunchedEffect(active, webView) {
        if (active) webView?.onResume() else webView?.onPause()
    }

    LaunchedEffect(pageReady, current, webView) {
        val file = current
        if (pageReady && file != null) {
            webView?.loadAvatar(file)
        }
    }

    LaunchedEffect(
        pageReady,
        webView,
        AppSettings.avatarQuality,
        AppSettings.avatarShadows,
        AppSettings.targetFps
    ) {
        if (pageReady) {
            webView?.setRenderQuality(AppSettings.avatarQuality)
            webView?.setShadows(AppSettings.avatarShadows)
            webView?.setTargetFps(
                if (AppSettings.targetFps > 0) AppSettings.targetFps else 120
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                status = null
                val result = withContext(Dispatchers.IO) {
                    importAvatar(context, uri, avatarsDir)
                }
                result.onSuccess { file ->
                    avatars = listAvatars(avatarsDir)
                    selectAvatar(file)
                    showModels = false
                }.onFailure { error ->
                    status = context.getString(R.string.avatar3d_import_failed) +
                        (error.message?.let { "\n$it" } ?: "")
                }
                importing = false
            }
        }
    }

    SideEffect {
        state.stats = stats
        state.name = current?.name.orEmpty()
        state.openModels = { showModels = true }
        state.dismissSheets = { showModels = false }
        state.importAvatar = { importLauncher.launch(AVATAR_MIME_TYPES) }
        state.resetView = { webView?.resetView() }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.disposeViewer()
            webView = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                Avatar3DWebView(ctx, avatarsDir).also { view ->
                    view.onPageReady = { pageReady = true }
                    view.onPageError = { message -> status = message }
                    view.onStats = { value -> stats = value }
                    webView = view
                    onWebViewCreated(view)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (current == null && !importing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        stringResource(R.string.avatar3d_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (importing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoadingIndicator(modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.avatar3d_importing))
                    }
                }
            }
        }

        status?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 98.dp)
                    .clickable { status = null },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

    }

    if (showModels) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showModels = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.avatar3d_models),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { importLauncher.launch(AVATAR_MIME_TYPES) },
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.avatar3d_import))
                }
                if (importing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.avatar3d_importing),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                status?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (avatars.isEmpty()) {
                    Text(
                        stringResource(R.string.avatar3d_no_models),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                avatars.forEach { file ->
                    val selected = file == current
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectAvatar(file); showModels = false },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    String.format(
                                        Locale.getDefault(),
                                        "%.1f MB",
                                        file.length() / (1024f * 1024f)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.avatar3d_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }


}

@Composable
fun Avatar3DStatsCard(stats: Avatar3DStats) {
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
                    text = "3D",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            Avatar3DStatRow(stringResource(R.string.stat_draws), stats.drawCalls.toString())
            Avatar3DStatRow(stringResource(R.string.stat_triangles), compact3D(stats.triangles))
            Avatar3DStatRow(
                stringResource(R.string.stat_buffer),
                if (stats.width > 0) "${stats.width}×${stats.height}" else "—"
            )
            Avatar3DStatRow(
                stringResource(R.string.stat_pixel_ratio),
                if (stats.pixelRatio > 0f) {
                    String.format(Locale.getDefault(), "%.2fx", stats.pixelRatio)
                } else {
                    "—"
                }
            )
            Avatar3DStatRow(stringResource(R.string.stat_textures), stats.textures.toString())
            Avatar3DStatRow(
                stringResource(R.string.stat_quality),
                if (AppSettings.avatarQuality % 1f == 0f) {
                    "${AppSettings.avatarQuality.toInt()}x"
                } else {
                    "${AppSettings.avatarQuality}x"
                }
            )
            Avatar3DStatRow(
                stringResource(R.string.avatar3d_shadows),
                if (AppSettings.avatarShadows) {
                    stringResource(R.string.on)
                } else {
                    stringResource(R.string.off)
                }
            )
        }
    }
}

@Composable
private fun Avatar3DStatRow(label: String, value: String) {
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

private fun compact3D(value: Int): String {
    return when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000f)
        else -> value.toString()
    }
}

@Composable
private fun AvatarSettingsTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AvatarQualityChip(
    label: String,
    value: Float,
    selected: Float,
    onClick: (Float) -> Unit
) {
    FilterChip(
        selected = selected == value,
        onClick = { onClick(value) },
        label = { Text(label) }
    )
}

@Composable
private fun AvatarOrientationRow(
    current: OrientationMode,
    onSelect: (OrientationMode) -> Unit
) {
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
                onClick = { onSelect(mode) },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}

@Composable
private fun AvatarLanguageRow(
    current: String,
    onSelect: (String) -> Unit
) {
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
                onClick = { onSelect(tag) },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}

@Composable
private fun RowScope.AvatarToolbarAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun listAvatars(dir: File): List<File> {
    return dir.listFiles()
        ?.filter { it.isFile && (it.name.endsWith(".vrm", true) || it.name.endsWith(".glb", true)) }
        ?.sortedByDescending { it.lastModified() }
        .orEmpty()
}

private fun importAvatar(context: Context, uri: Uri, dir: File): Result<File> = runCatching {
    val displayName = queryDisplayName(context, uri)
        ?: uri.lastPathSegment
        ?: "avatar_${System.currentTimeMillis()}"
    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val mime = context.contentResolver.getType(uri).orEmpty()
    val resolvedExtension = when {
        extension == "vrm" || extension == "glb" -> extension
        mime == "model/gltf-binary" -> "glb"
        mime == "model/vrm" -> "vrm"
        else -> error("Only .vrm or .glb files are supported")
    }
    val baseName = displayName
        .substringBeforeLast('.', displayName)
        .map { ch -> if (ch == '\\' || ch == '/' || ch == ':' || ch == '*' ||
            ch == '?' || ch == '"' || ch == '<' || ch == '>' || ch == '|'
        ) '_' else ch }
        .joinToString("")
        .trim()
        .ifBlank { "avatar" }

    dir.mkdirs()
    var target = File(dir, "$baseName.$resolvedExtension")
    var suffix = 2
    while (target.exists()) {
        target = File(dir, "${baseName}_$suffix.$resolvedExtension")
        suffix++
    }

    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Cannot open the selected file")

    target
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}
