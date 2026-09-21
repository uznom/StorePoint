package com.munzo.storepoint.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies a premium, modern glassmorphic panel style with realistic highlights.
 * Uses translucent linear gradients to simulate depth refraction, pairing with solid
 * speculative top-and-left borders. Perfect for overlay sheets, modals, and login cards.
 */
@Composable
fun Modifier.glassPanel(
    cornerRadius: Dp = 24.dp,
    borderAlpha: Float = 0.25f,
    glareAlpha: Float = 0.08f,
    blurRadius: Dp = 0.dp
): Modifier {
    val glassColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            glassColor,
            glassColor.copy(alpha = 0.88f),
            glassColor.copy(alpha = 0.55f)
        )
    )

    val blurred = if (blurRadius > 0.dp) {
        this.blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
    } else {
        this
    }

    return blurred
        .graphicsLayer {
            clip = true
            shape = RoundedCornerShape(cornerRadius)
        }
        .background(brush = gradientBrush, shape = RoundedCornerShape(cornerRadius))
        .border(
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = borderAlpha + glareAlpha),
                        highlightColor.copy(alpha = borderAlpha * 0.5f),
                        Color.Transparent
                    )
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
}

/**
 * Multi-state tactile modifier implementing scale-down on-press physics.
 * Triggers organic spring elasticity when matching standard user press feedback.
 */
@Composable
fun Modifier.tactileBounce(onClick: (() -> Unit)? = null): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = ExpressiveMotion.springBouncy(),
        label = "PressBounce"
    )

    val scaledModifier = this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

    return if (onClick != null) {
        scaledModifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        scaledModifier
    }
}

/**
 * Material 3 Expressive Card styling conforming to Android 17 design language.
 * Features adaptive surface containers, organic corner radii (24dp), and crisp outline definition.
 */
@Composable
fun Modifier.expressiveCard(
    cornerRadius: Dp = 24.dp,
    containerColor: Color? = null,
    borderColor: Color? = null,
    elevation: Dp = 0.dp
): Modifier {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val bg = containerColor ?: if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val border = borderColor ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.45f)

    val baseModifier = if (elevation > 0.dp) {
        this.shadow(elevation, RoundedCornerShape(cornerRadius), clip = false)
    } else {
        this
    }

    return baseModifier
        .graphicsLayer {
            clip = true
            shape = RoundedCornerShape(cornerRadius)
        }
        .background(bg, RoundedCornerShape(cornerRadius))
        .border(1.dp, border, RoundedCornerShape(cornerRadius))
}

/**
 * Android 17 / Material 3 Expressive Frosted Glass Card with refraction highlight,
 * subtle ambient glow, and translucent glass background.
 */
@Composable
fun Modifier.expressiveGlassCard(
    cornerRadius: Dp = 26.dp,
    accentGlow: Color? = null,
    elevation: Dp = 2.dp,
    blurRadius: Dp = 0.dp
): Modifier {
    val glassBg = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
    val highlightColor = accentGlow ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val specularBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.25f else 0.50f),
            highlightColor,
            Color.Transparent
        )
    )

    val blurred = if (blurRadius > 0.dp) {
        this.blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
    } else {
        this
    }

    return blurred
        .shadow(
            elevation = elevation,
            shape = RoundedCornerShape(cornerRadius),
            ambientColor = highlightColor,
            spotColor = highlightColor
        )
        .graphicsLayer {
            clip = true
            shape = RoundedCornerShape(cornerRadius)
        }
        .background(glassBg, RoundedCornerShape(cornerRadius))
        .border(1.dp, specularBrush, RoundedCornerShape(cornerRadius))
}

/**
 * Frosted translucent glass background with rounded pill shape for floating bars.
 */
@Composable
fun Modifier.frostedGlass(
    cornerRadius: Dp = 24.dp,
    alpha: Float = 0.85f,
    blurRadius: Dp = 0.dp
): Modifier {
    val bg = MaterialTheme.colorScheme.surface.copy(alpha = alpha)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.22f else 0.55f),
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    )
    val blurred = if (blurRadius > 0.dp) {
        this.blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
    } else {
        this
    }
    return blurred
        .graphicsLayer {
            clip = true
            shape = RoundedCornerShape(cornerRadius)
        }
        .background(bg, RoundedCornerShape(cornerRadius))
        .border(1.dp, borderBrush, RoundedCornerShape(cornerRadius))
}

/**
 * Material 3 Expressive Custom Split Button.
 * Side-by-side buttons combined into a singular organic bounding box.
 * Ideal for principal app actions with secondary options attached (like re-printing or changing mode).
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
    val mainIntSource = remember { MutableInteractionSource() }
    val isMainPressed by mainIntSource.collectIsPressedAsState()
    val mainScale by animateFloatAsState(
        isMainPressedState(isMainPressed),
        animationSpec = ExpressiveMotion.springBouncy(),
        label = "SplitMain"
    )

    val secIntSource = remember { MutableInteractionSource() }
    val isSecPressed by secIntSource.collectIsPressedAsState()
    val secScale by animateFloatAsState(
        isMainPressedState(isSecPressed),
        animationSpec = ExpressiveMotion.springBouncy(),
        label = "SplitSec"
    )

    // A unified container wrapping both primary & detail split flows cleanly
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
            // Main Button Interaction Block
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(interactionSource = mainIntSource, indication = null) { onMainClick() }
                    .padding(horizontal = 16.dp)
                    .graphicsLayer { scaleX = mainScale; scaleY = mainScale },
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

            // Divider Line with glowing opacity
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.65f)
                    .background(contentColor.copy(alpha = 0.35f))
            )

            // Secondary Dropdown Interaction Block
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .clickable(interactionSource = secIntSource, indication = null) { onSecondaryClick() }
                    .graphicsLayer { scaleX = secScale; scaleY = secScale },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand Split Options",
                    tint = contentColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private fun isMainPressedState(pressed: Boolean): Float = if (pressed) 0.93f else 1.0f

/**
 * Shape-Shifting Animated Button Group.
 * Beautifully switches border radius on items that are selected to demonstrate high-fidelity M3 Expressive physics.
 */
@Composable
fun ExpressiveButtonGroup(
    modifier: Modifier = Modifier,
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { index, title ->
            val isSelected = index == selectedIndex
            
            // Shape-shifting logic based on index and selected state:
            // Selected item morphs to have larger curves, pushing against other elements visually
            val shape = remember(isSelected, index, items.size) {
                if (isSelected) {
                    RoundedCornerShape(14.dp) // Expressive curved highlighted capsule
                } else {
                    when (index) {
                        0 -> RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                        items.size - 1 -> RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp, topStart = 4.dp, bottomStart = 4.dp)
                        else -> RoundedCornerShape(4.dp)
                    }
                }
            }

            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.03f else 1.0f,
                animationSpec = ExpressiveMotion.springBouncy(),
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
                    .height(38.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(shape)
                    .clickable { onItemSelected(index) },
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
 * Highly polished pill switcher with spring indicator and crisp icons/labels.
 */
@Composable
fun ExpressiveSegmentedTabs(
    modifier: Modifier = Modifier,
    items: List<Pair<String, ImageVector?>>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = modifier
            .height(52.dp),
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
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.0f else 0.98f,
                    animationSpec = ExpressiveMotion.springBouncy(),
                    label = "TabScale"
                )
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "TabBg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "TabContentColor"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .clickable { onTabSelected(index) },
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
 * Frosted horizontal carousel with pill-shaped tabs, active glowing highlights, and resilient non-wrapping text.
 */
@Composable
fun ExpressiveScrollablePillTabs(
    modifier: Modifier = Modifier,
    tabs: List<Triple<String, String, ImageVector>>,
    activeTabId: String,
    onTabSelected: (String) -> Unit
) {
    val scrollState = rememberScrollState()
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
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.04f else 1.0f,
                    animationSpec = ExpressiveMotion.springBouncy(),
                    label = "ScrollTabScale"
                )
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "ScrollTabBg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "ScrollTabContent"
                )

                Surface(
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("tab_$id")
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .clickable { onTabSelected(id) },
                    shape = CircleShape,
                    color = bgColor,
                    border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null,
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
 *
 * Replaces the legacy flat "bold primary coloured title" divider with the current expressive
 * pattern: a compact accent indicator (icon chip, or a rounded accent rail when no icon is
 * supplied) followed by an emphasised label, an optional supporting line, and an optional
 * trailing action slot.
 *
 * @param title Section label rendered as an emphasised (uppercase, letter-spaced) heading.
 * @param subtitle Optional supporting description shown beneath the label.
 * @param icon Optional leading icon for the accent chip.
 * @param accentColor Accent colour for the indicator chip/rail and the heading label.
 * @param trailing Optional composable rendered at the trailing edge (e.g. an action button).
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

/** Accent indicator chip (with icon) or rounded accent rail (without icon) for section headers. */
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
 * Material 3 Expressive Android 17 Metric Stat Card.
 * Clean, modern layout with specular frosted acrylic border, glowing accent pill, and strictly non-wrapping numbers.
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
    Card(
        modifier = modifier
            .tactileBounce()
            .expressiveGlassCard(cornerRadius = 24.dp, accentGlow = accentColor.copy(alpha = 0.2f), elevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                overflow = TextOverflow.Ellipsis
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

/**
 * Android 17 / Material 3 Expressive Sine-Wave Squiggle Linear Progress Bar.
 * Renders an organic sinusoidal wave that glides horizontally across the track.
 *
 * @param progress Null for fluid indeterminate wave motion; 0.0f..1.0f for determinate progress.
 */
@Composable
fun ExpressiveSquiggleLinearProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
    amplitude: Dp = 3.5.dp,
    wavelength: Dp = 24.dp,
    strokeWidth: Dp = 3.5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SquigglePhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SquigglePhaseAngle"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((amplitude * 2) + strokeWidth + 4.dp)
            .testTag("expressive_squiggle_progress")
    ) {
        val width = size.width
        val midY = size.height / 2f
        val ampPx = amplitude.toPx()
        val waveLenPx = wavelength.toPx()
        val strokePx = strokeWidth.toPx()

        // Draw track
        drawLine(
            color = trackColor,
            start = androidx.compose.ui.geometry.Offset(0f, midY),
            end = androidx.compose.ui.geometry.Offset(width, midY),
            strokeWidth = strokePx * 0.75f,
            cap = StrokeCap.Round
        )

        val endX = if (progress != null) (width * progress.coerceIn(0f, 1f)) else width
        if (endX > 0f) {
            val path = Path()
            var x = 0f
            val step = 3f
            var first = true

            while (x <= endX) {
                val currentPhase = if (progress == null) phase else 0f
                val y = midY + (ampPx * sin((2 * PI * (x / waveLenPx)) + currentPhase).toFloat())
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += step
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/**
 * Android 17 / Material 3 Expressive Rosette / Flower Circular Squiggle Spinner.
 * An organic undulating multi-lobe floral wave that spins with continuous spring momentum.
 */
@Composable
fun ExpressiveSquiggleCircularProgress(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    lobes: Int = 6,
    strokeWidth: Dp = 3.5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SquiggleCircleTransition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SquiggleCircleRotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SquiggleCirclePulse"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
            .testTag("expressive_squiggle_circular")
    ) {
        val center = androidx.compose.ui.geometry.Offset(this.size.width / 2f, this.size.height / 2f)
        val maxRadius = (this.size.minDimension / 2f) - (strokeWidth.toPx() * 1.2f)
        val baseRadius = maxRadius * 0.82f
        val amp = maxRadius * 0.16f
        val strokePx = strokeWidth.toPx()

        // Background Track Ring
        drawCircle(
            color = trackColor,
            radius = baseRadius,
            center = center,
            style = Stroke(width = strokePx * 0.6f)
        )

        // Squiggly Rosette Wave
        val path = Path()
        val totalPoints = 90
        for (i in 0..totalPoints) {
            val theta = (2 * PI * (i.toFloat() / totalPoints)).toFloat()
            val r = baseRadius + (amp * sin((lobes * theta) + pulse).toFloat())
            val x = center.x + (r * cos(theta))
            val y = center.y + (r * sin(theta))
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * Android 17 / Material 3 Expressive Morphing Loader.
 * Smoothly shape-shifts geometry between a 4-lobed Flower, a Smooth Squircle, an Asymmetric Cookie,
 * and a Pill, paired with an expressive glowing aura and label.
 */
@Composable
fun ExpressiveMorphingLoader(
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    label: String? = "StorePoint Initializing..."
) {
    val infiniteTransition = rememberInfiniteTransition(label = "MorphingLoader")
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MorphProgress"
    )

    // Current corner radius interpolation: 12dp -> 32dp (circle) -> 4dp (cookie) -> 24dp
    val phase = morphProgress % 4f
    val cornerRadius = when {
        phase < 1f -> 14.dp + (18.dp * phase)
        phase < 2f -> 32.dp - (22.dp * (phase - 1f))
        phase < 3f -> 10.dp + (14.dp * (phase - 2f))
        else -> 24.dp - (10.dp * (phase - 3f))
    }

    val rotationAngle = morphProgress * 90f

    Column(
        modifier = modifier.testTag("expressive_morphing_loader"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    rotationZ = rotationAngle * 0.35f
                },
            contentAlignment = Alignment.Center
        ) {
            // Ambient specular glow behind morphing shape
            Surface(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                        alpha = 0.35f
                    },
                shape = RoundedCornerShape(cornerRadius),
                color = tertiaryColor
            ) {}

            // Fore shape
            Surface(
                modifier = Modifier
                    .size(size * 0.92f)
                    .border(
                        BorderStroke(
                            2.dp,
                            Brush.sweepGradient(
                                listOf(
                                    primaryColor,
                                    tertiaryColor,
                                    Color.White.copy(alpha = 0.85f),
                                    primaryColor
                                )
                            )
                        ),
                        shape = RoundedCornerShape(cornerRadius)
                    ),
                shape = RoundedCornerShape(cornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ExpressiveSquiggleCircularProgress(
                        size = size * 0.55f,
                        color = primaryColor,
                        strokeWidth = 3.dp
                    )
                }
            }
        }

        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            ExpressiveSquiggleLinearProgress(
                modifier = Modifier.width(160.dp),
                color = primaryColor,
                amplitude = 2.5.dp,
                wavelength = 18.dp
            )
        }
    }
}

/**
 * Android 17 / Material 3 Expressive Morphing Status Badge.
 * Shape-shifting animated badge for stock flags, audit status, and admin verified pills.
 */
@Composable
fun ExpressiveMorphingBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    icon: ImageVector? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BadgeMorph")
    val morphCorner by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BadgeCorner"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(morphCorner.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Android 17 / Material 3 Expressive OTP-Style PIN Input Component.
 * Features:
 * - Distinct styled container box for every single digit with fluid corner smoothing.
 * - Active glowing focus container with animated pulsing cursor.
 * - Masked (expressive dots) or unmasked numeral display with instant feedback.
 * - Hardware keyboard, soft keyboard, backspace, and IME complete integration.
 */
@Composable
fun ExpressiveOtpPinInput(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    pinLength: Int = 6,
    isMasked: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    onPinComplete: ((String) -> Unit)? = null
) {
    BasicTextField(
        value = pin,
        onValueChange = { newValue ->
            val filtered = newValue.filter { it.isDigit() }.take(pinLength)
            onPinChange(filtered)
            if (filtered.length == pinLength) {
                onPinComplete?.invoke(filtered)
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        singleLine = true,
        modifier = modifier,
        decorationBox = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (index in 0 until pinLength) {
                        val isFocused = pin.length == index
                        val char = pin.getOrNull(index)
                        val isFilled = char != null

                        val animatedBorderColor by animateColorAsState(
                            targetValue = when {
                                isError -> MaterialTheme.colorScheme.error
                                isFocused -> MaterialTheme.colorScheme.primary
                                isFilled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            },
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "OtpBorderColor"
                        )

                        val animatedBgColor by animateColorAsState(
                            targetValue = when {
                                isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                isFilled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                            },
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "OtpBgColor"
                        )

                        Box(
                            modifier = Modifier
                                .size(width = 46.dp, height = 54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(animatedBgColor)
                                .border(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color = animatedBorderColor,
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFilled) {
                                if (isMasked) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape
                                            )
                                    )
                                } else {
                                    Text(
                                        text = char.toString(),
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            } else if (isFocused) {
                                val infiniteTransition = rememberInfiniteTransition(label = "CursorBlink")
                                val cursorAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.2f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = 600),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "CursorAlpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(20.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha),
                                            RoundedCornerShape(1.dp)
                                        )
                                )
                            }
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank() && isError) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    )
}

