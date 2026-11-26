package com.munzo.storepoint.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics

/**
 * Material 3 Expressive Custom Split Button.
 * Side-by-side buttons combined into a singular organic bounding box with spring-stretch feedback.
 */
@Composable
fun ExpressiveSplitButton(
    modifier: Modifier = Modifier,
    mainLabel: String,
    onMainClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    mainIcon: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val reduceMotion = LocalReduceMotion.current
    val feedback = rememberTactileFeedback()
    val mainIntSource = remember { MutableInteractionSource() }
    val secIntSource = remember { MutableInteractionSource() }
    val isSecPressed by secIntSource.collectIsPressedAsState()

    val arrowRotation by animateFloatAsState(
        targetValue = if (isSecPressed) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(
            durationMillis = 220,
            easing = FastOutSlowInEasing
        ),
        label = "secArrowRotation"
    )

    Surface(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer { clip = true; shape = RoundedCornerShape(16.dp) },
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .springStretchPress(mainIntSource)
                    .clickable(interactionSource = mainIntSource, indication = null) {
                        feedback.perform(TactileHapticRole.Tap)
                        onMainClick()
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (mainIcon != null) {
                    mainIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = mainLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.65f)
                    .background(contentColor.copy(alpha = 0.35f))
            )

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .springStretchPress(secIntSource)
                    .clickable(interactionSource = secIntSource, indication = null) {
                        feedback.perform(TactileHapticRole.Tap)
                        onSecondaryClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand Split Options",
                    tint = contentColor.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { this.rotationZ = arrowRotation }
                )
            }
        }
    }
}

/**
 * Shape-Shifting Animated Button Group with spring-stretch tactile response.
 */
@Composable
fun ExpressiveButtonGroup(
    modifier: Modifier = Modifier,
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val feedback = rememberTactileFeedback()

    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { index, title ->
            val isSelected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }

            val shape = remember(isSelected, index, items.size) {
                if (isSelected) {
                    RoundedCornerShape(14.dp)
                } else {
                    when (index) {
                        0 -> RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                        items.size - 1 -> RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp, topStart = 4.dp, bottomStart = 4.dp)
                        else -> RoundedCornerShape(4.dp)
                    }
                }
            }

            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.02f else 1.0f,
                animationSpec = if (reduceMotion) snap() else ExpressiveMotion.tactileStretchSpring(),
                label = "ButtonGroupScale"
            )

            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            }

            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .springStretchPress(interactionSource)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(shape)
                    .clickable(interactionSource = interactionSource, indication = null) {
                        feedback.perform(TactileHapticRole.Tap)
                        onItemSelected(index)
                    },
                color = containerColor,
                contentColor = contentColor,
                shape = shape
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Android 17 / M3 Expressive Segmented Floating Pill Tabs.
 */
@Composable
fun ExpressiveSegmentedTabs(
    modifier: Modifier = Modifier,
    items: List<Pair<String, ImageVector?>>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val feedback = rememberTactileFeedback()

    Surface(
        modifier = modifier.height(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.0f else 0.98f,
                    animationSpec = if (reduceMotion) snap() else ExpressiveMotion.tactileStretchSpring(),
                    label = "TabScale"
                )
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMedium),
                    label = "TabBg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMedium),
                    label = "TabContentColor"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .springStretchPress(interactionSource)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .clickable(interactionSource = interactionSource, indication = null) {
                            feedback.perform(TactileHapticRole.Tap)
                            onTabSelected(index)
                        },
                    shape = CircleShape,
                    color = bgColor,
                    shadowElevation = if (isSelected) 3.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.second != null) {
                            Icon(
                                imageVector = item.second!!,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = item.first,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Android 17 / M3 Expressive Scrollable Pill Tab Bar.
 */
@Composable
fun ExpressiveScrollablePillTabs(
    modifier: Modifier = Modifier,
    tabs: List<Triple<String, String, ImageVector>>,
    activeTabId: String,
    onTabSelected: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val reduceMotion = LocalReduceMotion.current
    val feedback = rememberTactileFeedback()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (id, label, icon) ->
                val isSelected = activeTabId == id
                val interactionSource = remember { MutableInteractionSource() }
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.03f else 1.0f,
                    animationSpec = if (reduceMotion) snap() else ExpressiveMotion.tactileStretchSpring(),
                    label = "ScrollTabScale"
                )
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                    },
                    animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMedium),
                    label = "ScrollTabBg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMedium),
                    label = "ScrollTabContent"
                )

                Surface(
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("tab_$id")
                        .springStretchPress(interactionSource)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .clickable(interactionSource = interactionSource, indication = null) {
                            feedback.perform(TactileHapticRole.Tap)
                            onTabSelected(id)
                        },
                    shape = CircleShape,
                    color = bgColor,
                    border = if (!isSelected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    } else {
                        null
                    },
                    shadowElevation = if (isSelected) 3.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * Android 17 Pill Badge for stock indicators, UOM tags, and quick flags.
 */
@Composable
fun ExpressivePillBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: ImageVector? = null,
    bold: Boolean = true
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Material 3 Expressive section header.
 */
@Composable
fun ExpressiveSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpressiveSectionIndicator(icon = icon, accentColor = accentColor)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                letterSpacing = 1.1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun ExpressiveSectionIndicator(icon: ImageVector?, accentColor: Color) {
    if (icon != null) {
        Surface(
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.14f),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 22.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
    }
}

/**
 * Material 3 Expressive Android 17 Metric Stat Card with elastic value pop and adaptive padding.
 */
@Composable
fun ExpressiveMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val metrics = LocalAdaptiveMetrics.current
    HeroCard(
        modifier = modifier
            .tactileBounce()
            .expressiveGlassCard(
                cornerRadius = 24.dp,
                accentGlow = accentColor.copy(alpha = 0.2f),
                elevation = 3.dp
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.cardPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (icon != null) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.elasticValuePop(value)
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
