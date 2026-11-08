package com.winkmoon.xinran

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Shared chrome for both renderer modes. The container, typography, FPS pill and
 * bottom floating bar stay identical; only the mode-specific actions change.
 */
@Composable
fun ViewerTopBar(
    selected3D: Boolean,
    fps: Float,
    onSelect2D: () -> Unit,
    onSelect3D: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ModeSwitcher(
            selected3D = selected3D,
            onSelect2D = onSelect2D,
            onSelect3D = onSelect3D
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FpsPill(fps = fps, trailing = trailing)
        }
    }
}

@Composable
fun FpsPill(
    fps: Float,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 2.dp, end = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (fps >= 55f) Color(0xFF4ADE80) else Color(0xFFFBBF24))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (fps > 0f) {
                    stringResource(R.string.fps_value, fps.toInt())
                } else {
                    "— fps"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(2.dp))
            trailing()
        }
    }
}

@Composable
fun ViewerBottomBar(
    selected3D: Boolean,
    onModels: () -> Unit,
    onSettings: () -> Unit,
    onMotions: (() -> Unit)? = null,
    onResetView: (() -> Unit)? = null
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
                ViewerToolbarAction(
                    label = stringResource(R.string.toolbar_models),
                    icon = Icons.Default.List,
                    onClick = onModels
                )
                if (!selected3D && onMotions != null) {
                    ViewerToolbarAction(
                        label = stringResource(R.string.toolbar_motion),
                        icon = Icons.Default.Face,
                        onClick = onMotions
                    )
                }
                ViewerToolbarAction(
                    label = stringResource(R.string.toolbar_settings),
                    icon = Icons.Default.Settings,
                    onClick = onSettings
                )
                if (selected3D && onResetView != null) {
                    IconButton(onClick = onResetView) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.avatar3d_reset)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ViewerToolbarAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
