package com.munzo.storepoint.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.springStretchPress

// =========================================================================
// 2. HeroCard Compound Component (HeroUI v3 Architecture)
// =========================================================================

@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    shape: Shape = HeroUITheme.radius.xl,
    backgroundColor: Color = HeroUITheme.colors.surface,
    borderColor: Color = HeroUITheme.colors.border,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 0.dp,
    colors: CardColors? = null,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val feedback = rememberTactileFeedback()
    val cardModifier = if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        modifier
            .springStretchPress(
                interactionSource = interactionSource,
                pressScaleX = 1.015f,
                pressScaleY = 0.97f
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple()
            ) {
                feedback.perform(TactileHapticRole.Tap)
                onClick()
            }
    } else {
        modifier
    }

    val finalBg = colors?.containerColor ?: backgroundColor
    val finalBorder = border ?: BorderStroke(borderWidth, borderColor)

    Surface(
        modifier = cardModifier,
        shape = shape,
        color = finalBg,
        tonalElevation = elevation,
        border = finalBorder
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    shape: Shape = HeroUITheme.radius.xl,
    backgroundColor: Color = HeroUITheme.colors.surface,
    borderColor: Color = HeroUITheme.colors.border,
    borderWidth: Dp = 1.dp,
    elevation: CardElevation,
    colors: CardColors? = null,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    HeroCard(
        modifier = modifier,
        shape = shape,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        borderWidth = borderWidth,
        elevation = 0.dp,
        colors = colors,
        border = border,
        onClick = onClick,
        content = content
    )
}

@Composable
fun HeroCardHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = HeroUITheme.colors.accent,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(HeroUITheme.radius.md)
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = HeroUITheme.colors.foreground
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = HeroUITheme.colors.muted
                    )
                }
            }
        }
        if (action != null) {
            Spacer(modifier = Modifier.width(12.dp))
            action()
        }
    }
}

@Composable
fun HeroCardBody(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(padding)
    ) {
        content()
    }
}

@Composable
fun HeroCardFooter(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        content()
    }
}

// =========================================================================
// 3. HeroChip (HeroUI v3 Pill Tags)
// =========================================================================

@Composable
fun HeroChip(
    text: String,
    modifier: Modifier = Modifier,
    color: HeroColor = HeroColor.Primary,
    variant: HeroChipVariant = HeroChipVariant.Flat,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val c = HeroUITheme.colors
    val feedback = rememberTactileFeedback()
    val (bg, fg, border) = when (color) {
        HeroColor.Primary -> when (variant) {
            HeroChipVariant.Solid -> Triple(c.accent, c.accentForeground, null)
            HeroChipVariant.Flat -> Triple(c.accent.copy(alpha = 0.14f), c.accent, null)
            HeroChipVariant.Bordered -> Triple(Color.Transparent, c.accent, BorderStroke(1.dp, c.accent))
            HeroChipVariant.Dot -> Triple(c.surfaceSecondary, c.foreground, null)
        }
        HeroColor.Success -> when (variant) {
            HeroChipVariant.Solid -> Triple(c.success, c.successForeground, null)
            HeroChipVariant.Flat -> Triple(c.success.copy(alpha = 0.14f), c.success, null)
            HeroChipVariant.Bordered -> Triple(Color.Transparent, c.success, BorderStroke(1.dp, c.success))
            HeroChipVariant.Dot -> Triple(c.surfaceSecondary, c.foreground, null)
        }
        HeroColor.Warning -> when (variant) {
            HeroChipVariant.Solid -> Triple(c.warning, c.warningForeground, null)
            HeroChipVariant.Flat -> Triple(c.warning.copy(alpha = 0.14f), c.warning, null)
            HeroChipVariant.Bordered -> Triple(Color.Transparent, c.warning, BorderStroke(1.dp, c.warning))
            HeroChipVariant.Dot -> Triple(c.surfaceSecondary, c.foreground, null)
        }
        HeroColor.Danger -> when (variant) {
            HeroChipVariant.Solid -> Triple(c.danger, c.dangerForeground, null)
            HeroChipVariant.Flat -> Triple(c.danger.copy(alpha = 0.14f), c.danger, null)
            HeroChipVariant.Bordered -> Triple(Color.Transparent, c.danger, BorderStroke(1.dp, c.danger))
            HeroChipVariant.Dot -> Triple(c.surfaceSecondary, c.foreground, null)
        }
        HeroColor.Default -> when (variant) {
            HeroChipVariant.Solid -> Triple(c.defaultBtn, c.defaultForeground, null)
            HeroChipVariant.Flat -> Triple(c.surfaceSecondary, c.muted, null)
            HeroChipVariant.Bordered -> Triple(Color.Transparent, c.muted, BorderStroke(1.dp, c.border))
            HeroChipVariant.Dot -> Triple(c.surfaceSecondary, c.foreground, null)
        }
    }

    val chipModifier = if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        modifier
            .springStretchPress(interactionSource = interactionSource)
            .clip(HeroUITheme.radius.full)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple()
            ) {
                feedback.perform(TactileHapticRole.Tap)
                onClick()
            }
    } else {
        modifier
    }

    Surface(
        modifier = chipModifier,
        shape = HeroUITheme.radius.full,
        color = bg,
        border = border
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (variant == HeroChipVariant.Dot) {
                val dotColor = when (color) {
                    HeroColor.Primary -> c.accent
                    HeroColor.Success -> c.success
                    HeroColor.Warning -> c.warning
                    HeroColor.Danger -> c.danger
                    HeroColor.Default -> c.muted
                }
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = fg,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun HeroFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    color: HeroColor = HeroColor.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    HeroChip(
        text = label,
        modifier = modifier,
        color = if (selected) color else HeroColor.Default,
        variant = if (selected) HeroChipVariant.Solid else HeroChipVariant.Flat,
        icon = icon,
        onClick = if (enabled) onClick else null
    )
}

@Composable
fun HeroFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: HeroColor = HeroColor.Primary,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    label: @Composable () -> Unit
) {
    val c = HeroUITheme.colors
    val feedback = rememberTactileFeedback()
    val (bg, fg, border) = if (selected) {
        when (color) {
            HeroColor.Primary -> Triple(c.accent, c.accentForeground, null)
            HeroColor.Success -> Triple(c.success, c.successForeground, null)
            HeroColor.Warning -> Triple(c.warning, c.warningForeground, null)
            HeroColor.Danger -> Triple(c.danger, c.dangerForeground, null)
            HeroColor.Default -> Triple(c.defaultBtn, c.defaultForeground, null)
        }
    } else {
        Triple(c.surfaceSecondary, c.muted, null)
    }

    val chipModifier = if (enabled) {
        val interactionSource = remember { MutableInteractionSource() }
        modifier
            .springStretchPress(interactionSource = interactionSource)
            .clip(HeroUITheme.radius.full)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple()
            ) {
                feedback.perform(TactileHapticRole.Tap)
                onClick()
            }
    } else {
        modifier
    }

    Surface(
        modifier = chipModifier,
        shape = HeroUITheme.radius.full,
        color = if (enabled) bg else bg.copy(alpha = 0.5f),
        border = border
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            CompositionLocalProvider(
                LocalContentColor provides fg
            ) {
                label()
            }
        }
    }
}

// =========================================================================
// 4. HeroSegmentedTabs (HeroUI v3 Sliding Pills)
// =========================================================================

@Composable
fun <T> HeroSegmentedTabs(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    iconProvider: ((T) -> ImageVector)? = null
) {
    val c = HeroUITheme.colors
    val feedback = rememberTactileFeedback()
    Surface(
        modifier = modifier,
        shape = HeroUITheme.radius.xl,
        color = c.surfaceSecondary,
        border = BorderStroke(1.dp, c.border)
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                val interactionSource = remember { MutableInteractionSource() }
                val bg by animateColorAsState(
                    targetValue = if (isSelected) c.surface else Color.Transparent,
                    label = "tab_bg"
                )
                val fg by animateColorAsState(
                    targetValue = if (isSelected) c.accent else c.muted,
                    label = "tab_fg"
                )

                Surface(
                    onClick = {
                        feedback.perform(TactileHapticRole.Tap)
                        onItemSelected(item)
                    },
                    interactionSource = interactionSource,
                    shape = HeroUITheme.radius.lg,
                    color = bg,
                    border = if (isSelected) BorderStroke(1.dp, c.border) else null,
                    modifier = Modifier
                        .weight(1f)
                        .springStretchPress(interactionSource = interactionSource)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (iconProvider != null) {
                            Icon(
                                imageVector = iconProvider(item),
                                contentDescription = null,
                                tint = fg,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = labelProvider(item),
                            color = fg,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 5. Responsive Screen Layout Classes
// =========================================================================

enum class HeroWindowSize {
    Compact,
    Medium,
    Expanded
}

@Composable
fun rememberHeroWindowSize(): HeroWindowSize {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    return remember(widthDp) {
        when {
            widthDp < 600 -> HeroWindowSize.Compact
            widthDp < 840 -> HeroWindowSize.Medium
            else -> HeroWindowSize.Expanded
        }
    }
}
