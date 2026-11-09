@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.winkmoon.xinran

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * One settings body used by both modes:
 *   common  -> language / orientation
 *   Live2D  -> Cubism rendering + scheduling
 *   3D      -> WebView renderer options
 *
 * 2D-only actions (root, reload model) disappear in 3D; 3D-only actions
 * (reset view) disappear in 2D.
 */
@Composable
fun UnifiedSettingsContent(
    selected3D: Boolean,
    languageTag: String,
    orientationMode: OrientationMode,
    onLanguage: (String) -> Unit,
    onOrientation: (OrientationMode) -> Unit,
    rootAvailable: Boolean = false,
    rootEnabled: Boolean = false,
    rootBusy: Boolean = false,
    rootStatus: String? = null,
    onRoot: ((Boolean) -> Unit)? = null,
    onReloadModel: (() -> Unit)? = null,
    onResetView3D: (() -> Unit)? = null,
    rootRefreshAvailable: Boolean = false,
    rootRefreshEnabled: Boolean = false,
    rootRefreshBusy: Boolean = false,
    rootRefreshStatus: String? = null,
    onRootRefresh: ((Boolean) -> Unit)? = null,
    onCheckRoot: (() -> Unit)? = null,
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    val context = LocalContext.current
    RootShell.init(context)
    val fpsTiers = remember(context) {
        RefreshRateUtils.supportedFpsTiers(context)
    }

    LaunchedEffect(fpsTiers) {
        if (fpsTiers.isNotEmpty() && AppSettings.targetFps !in fpsTiers) {
            AppSettings.updateTargetFps(RefreshRateUtils.defaultFps(context))
        }
    }

    UnifiedSectionTitle(
        stringResource(R.string.settings_common),
        stringResource(R.string.settings_common_subtitle)
    )
    Text(
        stringResource(R.string.theme_mode),
        style = MaterialTheme.typography.titleSmall
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            ThemeMode.System to R.string.theme_system,
            ThemeMode.Light to R.string.theme_light,
            ThemeMode.Dark to R.string.theme_dark
        ).forEach { (mode, labelRes) ->
            FilterChip(
                selected = themeMode == mode,
                onClick = { onThemeModeChange(mode) },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
    UnifiedLanguageRow(current = languageTag, onLanguage = onLanguage)
    UnifiedOrientationRow(current = orientationMode, onOrientation = onOrientation)
    Text(
        stringResource(R.string.game_space_orientation_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )

    // Frame pacing is a whole-app policy: it applies to both Cubism and WebGL.
    Text(stringResource(R.string.target_frame_rate), style = MaterialTheme.typography.titleSmall)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        fpsTiers.forEach { fps ->
            FilterChip(
                selected = AppSettings.targetFps == fps,
                onClick = { AppSettings.updateTargetFps(fps) },
                label = { Text("$fps") }
            )
        }
    }
    Text(
        stringResource(R.string.target_frame_rate_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val actualHz = RefreshRateUtils.actualRefreshRate(context)
    Text(
        stringResource(R.string.target_frame_rate_actual, actualHz),
        style = MaterialTheme.typography.bodySmall,
        color = if (AppSettings.targetFps > actualHz) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    UnifiedSectionTitle(
        stringResource(R.string.settings_live2d),
        stringResource(R.string.settings_live2d_subtitle)
    )

    UnifiedSwitchRow(
        title = stringResource(R.string.hide_ui_on_motion),
        subtitle = stringResource(R.string.hide_ui_on_motion_subtitle),
        checked = AppSettings.hideUiOnMotion,
        onCheckedChange = { AppSettings.updateHideUiOnMotion(it) }
    )
    UnifiedSliderRow(
        title = stringResource(R.string.render_scale),
        valueText = "${(AppSettings.renderScale * 100f).roundToInt()}%",
        value = AppSettings.renderScale,
        range = 0.5f..1f,
        onValueChange = {
            AppSettings.updateAutoResolution(false)
            AppSettings.updateRenderScale(it)
        }
    )
    UnifiedSwitchRow(
        title = stringResource(R.string.adaptive_resolution),
        subtitle = stringResource(R.string.adaptive_resolution_subtitle),
        checked = AppSettings.autoResolution,
        onCheckedChange = { AppSettings.updateAutoResolution(it) }
    )
    UnifiedSliderRow(
        title = stringResource(R.string.mask_resolution),
        valueText = "${(AppSettings.maskScale * 100f).roundToInt()}%",
        value = AppSettings.maskScale,
        range = 0.4f..1f,
        onValueChange = { AppSettings.updateMaskScale(it) },
        subtitle = stringResource(R.string.mask_resolution_subtitle)
    )
    UnifiedSwitchRow(
        title = stringResource(R.string.mipmaps),
        subtitle = stringResource(R.string.mipmaps_subtitle),
        checked = AppSettings.mipmaps,
        onCheckedChange = { AppSettings.updateMipmaps(it) }
    )
    UnifiedSwitchRow(
        title = stringResource(R.string.adpf),
        subtitle = if (Build.VERSION.SDK_INT >= 31) {
            stringResource(R.string.adpf_subtitle)
        } else {
            stringResource(R.string.adpf_subtitle_unsupported)
        },
        checked = AppSettings.adpfEnabled,
        enabled = Build.VERSION.SDK_INT >= 31,
        onCheckedChange = { AppSettings.updateAdpfEnabled(it) }
    )

    if (!selected3D && onReloadModel != null) {
        OutlinedButton(onClick = onReloadModel, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.reload_current_model))
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    UnifiedSectionTitle(
        stringResource(R.string.settings_avatar3d),
        stringResource(R.string.settings_avatar3d_subtitle)
    )
    Text(stringResource(R.string.avatar3d_quality), style = MaterialTheme.typography.titleSmall)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(1f to "1x", 1.5f to "1.5x", 2f to "2x").forEach { (value, label) ->
            FilterChip(
                selected = AppSettings.avatarQuality == value,
                onClick = { AppSettings.updateAvatarQuality(value) },
                label = { Text(label) }
            )
        }
    }
    UnifiedSwitchRow(
        title = stringResource(R.string.avatar3d_shadows),
        subtitle = stringResource(R.string.avatar3d_shadows_subtitle),
        checked = AppSettings.avatarShadows,
        onCheckedChange = { AppSettings.updateAvatarShadows(it) }
    )
    if (selected3D && onResetView3D != null) {
        OutlinedButton(onClick = onResetView3D, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.avatar3d_reset))
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    UnifiedSectionTitle(
        stringResource(R.string.settings_root),
        stringResource(R.string.settings_root_subtitle)
    )
    Text(
        stringResource(R.string.root_backend),
        style = MaterialTheme.typography.titleSmall
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RootBackend.values().forEach { backend ->
            FilterChip(
                selected = RootShell.backend == backend,
                onClick = { RootShell.setBackend(context, backend) },
                label = { Text(stringResource(backend.labelRes)) }
            )
        }
    }
    if (RootShell.backend == RootBackend.Custom) {
        OutlinedTextField(
            value = RootShell.customCommand,
            onValueChange = { RootShell.setCustomCommand(context, it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.root_custom_command)) },
            placeholder = { Text("su -c") },
            singleLine = true
        )
        Text(
            stringResource(R.string.root_custom_command_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (onCheckRoot != null) {
        OutlinedButton(onClick = onCheckRoot, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.root_detect))
        }
    }
    if (!rootAvailable && !rootRefreshAvailable) {
        Text(
            stringResource(R.string.root_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        if (onRoot != null && rootStatus != null) {
            UnifiedSwitchRow(
                title = stringResource(R.string.root_mode),
                subtitle = rootStatus,
                checked = rootEnabled,
                enabled = rootAvailable && !rootBusy,
                onCheckedChange = onRoot
            )
            if (rootBusy) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingIndicator(modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.waiting_root),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                stringResource(R.string.root_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (rootRefreshAvailable && onRootRefresh != null) {
            UnifiedSwitchRow(
                title = stringResource(R.string.root_force_refresh),
                subtitle = stringResource(R.string.root_force_refresh_subtitle),
                checked = rootRefreshEnabled,
                enabled = !rootRefreshBusy,
                onCheckedChange = onRootRefresh
            )
            if (rootRefreshBusy) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingIndicator(modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.waiting_root),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            rootRefreshStatus?.let { status ->
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                stringResource(R.string.root_force_refresh_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnifiedSectionTitle(title: String, subtitle: String) {
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
private fun UnifiedSliderRow(
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
private fun UnifiedSwitchRow(
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

@Composable
private fun UnifiedLanguageRow(current: String, onLanguage: (String) -> Unit) {
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
private fun UnifiedOrientationRow(
    current: OrientationMode,
    onOrientation: (OrientationMode) -> Unit
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
                onClick = { onOrientation(mode) },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}
