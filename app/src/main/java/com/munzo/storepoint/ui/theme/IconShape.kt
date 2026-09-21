package com.munzo.storepoint.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Time-aware gradient background icon shape.
 *
 * Renders an icon inside a soft circular gradient chip whose tint responds to the
 * current [ClockState.TimePhase]. The gradient is sampled from the app's expressive
 * palette and gently animates when the phase changes.
 *
 * Usage:
 *   IconShape(
 *       icon = Icons.Default.Schedule,
 *       label = "Now",
 *       modifier = Modifier.size(40.dp)
 *   )
 */
@Composable
fun IconShape(
    icon: ImageVector,
    label: String? = null,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 32.dp,
    showGradient: Boolean = true,
    clockAware: Boolean = true,
) {
    val accent = if (clockAware) {
        when (ClockState.phase) {
            ClockState.TimePhase.MORNING -> Color(0xFFFFB74D)
            ClockState.TimePhase.AFTERNOON -> Color(0xFFFFCA28)
            ClockState.TimePhase.EVENING -> Color(0xFF7986CB)
            ClockState.TimePhase.NIGHT -> Color(0xFF1E88E5)
        }
    } else {
        contentColor
    }

    val animatedAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = spring<Color>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "IconShapeAccent"
    )

    val dim = if (ClockState.isNight) 0.75f else 1f

    Column(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor, shape = CircleShape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        animatedAccent.copy(alpha = 0.6f),
                        animatedAccent.copy(alpha = 0.2f)
                    )
                ),
                shape = CircleShape
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = animatedAccent.copy(alpha = dim),
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

@Composable
fun IconShapePair(
    currentIcon: ImageVector,
    complementIcon: ImageVector,
    modifier: Modifier = Modifier,
    currentLabel: String? = null,
    complementLabel: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconShape(
            icon = currentIcon,
            label = currentLabel,
            clockAware = true,
            size = 36.dp,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        IconShape(
            icon = complementIcon,
            label = complementLabel,
            clockAware = true,
            size = 36.dp,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}

@Composable
fun TimePhaseChip(
    modifier: Modifier = Modifier,
    showTime: Boolean = false,
    time: String? = null,
) {
    val phaseLabel = ClockState.phaseLabel
    val isNight = ClockState.isNight

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isNight) Icons.Default.AccessTime else Icons.Default.Schedule,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = phaseLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showTime && time != null) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
