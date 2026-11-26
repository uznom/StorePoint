package com.munzo.storepoint.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.ui.theme.ExpressiveMotion
import com.munzo.storepoint.ui.theme.ExpressiveShapeKind
import com.munzo.storepoint.ui.theme.LocalReduceMotion
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.rememberMorphShape
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.springStretchPress
import kotlinx.coroutines.delay

/** Expressive button size variants XS–XL (M3E sizing). */
enum class ExpressiveButtonSize(
    val height: Dp,
    val hPadding: Dp,
    val iconSize: Dp,
) {
    XS(32.dp, 12.dp, 16.dp),
    S(40.dp, 16.dp, 18.dp),
    M(48.dp, 20.dp, 20.dp),
    L(56.dp, 24.dp, 24.dp),
    XL(72.dp, 32.dp, 28.dp);
}

/** Typography for each expressive button size (theme-driven, not stored in enum). */
@Composable
fun expressiveButtonTextStyle(size: ExpressiveButtonSize): TextStyle = when (size) {
    ExpressiveButtonSize.XS -> MaterialTheme.typography.labelMedium
    ExpressiveButtonSize.S -> MaterialTheme.typography.labelLarge
    ExpressiveButtonSize.M -> MaterialTheme.typography.labelLarge
    ExpressiveButtonSize.L -> MaterialTheme.typography.titleMedium
    ExpressiveButtonSize.XL -> MaterialTheme.typography.titleLarge
}

/** Expressive button variants mapping onto M3 button roles. */
enum class ExpressiveButtonVariant { FILLED, TONAL, OUTLINED, ELEVATED, TEXT }

/**
 * The single expressive button for the whole app. Idle shape is a pill; pressing
 * morphs toward a squircle while applying balanced squash-and-stretch spring physics
 * and tactile haptic confirmation.
 */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    variant: ExpressiveButtonVariant = ExpressiveButtonVariant.FILLED,
    size: ExpressiveButtonSize = ExpressiveButtonSize.M,
    enabled: Boolean = true,
    testTag: String? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val feedback = rememberTactileFeedback()

    val cornerPercent by animateFloatAsState(
        targetValue = if (pressed) 28f else 50f,
        animationSpec = if (reduceMotion) snap() else ExpressiveMotion.tactileStretchSpring(),
        label = "buttonCornerShape"
    )
    val shape = RoundedCornerShape(percent = cornerPercent.toInt())

    val baseColors = when (variant) {
        ExpressiveButtonVariant.FILLED -> ButtonDefaults.buttonColors()
        ExpressiveButtonVariant.TONAL -> ButtonDefaults.filledTonalButtonColors()
        ExpressiveButtonVariant.OUTLINED -> ButtonDefaults.outlinedButtonColors()
        ExpressiveButtonVariant.ELEVATED -> ButtonDefaults.elevatedButtonColors()
        ExpressiveButtonVariant.TEXT -> ButtonDefaults.textButtonColors()
    }
    val colors = if (containerColor != null) {
        when (variant) {
            ExpressiveButtonVariant.FILLED -> ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor ?: Color.Unspecified,
            )
            ExpressiveButtonVariant.TONAL -> ButtonDefaults.filledTonalButtonColors(
                containerColor = containerColor,
                contentColor = contentColor ?: Color.Unspecified,
            )
            ExpressiveButtonVariant.OUTLINED -> ButtonDefaults.outlinedButtonColors(
                contentColor = contentColor ?: containerColor,
            )
            ExpressiveButtonVariant.ELEVATED -> ButtonDefaults.elevatedButtonColors(
                containerColor = containerColor,
                contentColor = contentColor ?: Color.Unspecified,
            )
            ExpressiveButtonVariant.TEXT -> ButtonDefaults.textButtonColors(
                contentColor = contentColor ?: containerColor,
            )
        }
    } else baseColors

    val border = if (variant == ExpressiveButtonVariant.OUTLINED) {
        BorderStroke(1.dp, contentColor ?: containerColor ?: MaterialTheme.colorScheme.outline)
    } else null

    val contentPadding = PaddingValues(horizontal = size.hPadding)
    val mods = modifier
        .height(size.height)
        .springStretchPress(interactionSource = interaction, enabled = enabled)
        .let { if (testTag != null) it.testTag(testTag) else it }

    val handleClick: () -> Unit = {
        feedback.perform(TactileHapticRole.Tap)
        onClick()
    }

    when (variant) {
        ExpressiveButtonVariant.FILLED -> Button(
            onClick = handleClick, modifier = mods, enabled = enabled, shape = shape,
            colors = colors, contentPadding = contentPadding, border = border,
            interactionSource = interaction
        ) { ExpressiveButtonContent(icon, label, size) }
        ExpressiveButtonVariant.TONAL -> FilledTonalButton(
            onClick = handleClick, modifier = mods, enabled = enabled, shape = shape,
            colors = colors, contentPadding = contentPadding, border = border,
            interactionSource = interaction
        ) { ExpressiveButtonContent(icon, label, size) }
        ExpressiveButtonVariant.OUTLINED -> OutlinedButton(
            onClick = handleClick, modifier = mods, enabled = enabled, shape = shape,
            colors = colors, contentPadding = contentPadding, border = border,
            interactionSource = interaction
        ) { ExpressiveButtonContent(icon, label, size) }
        ExpressiveButtonVariant.ELEVATED -> ElevatedButton(
            onClick = handleClick, modifier = mods, enabled = enabled, shape = shape,
            colors = colors, contentPadding = contentPadding, border = border,
            interactionSource = interaction
        ) { ExpressiveButtonContent(icon, label, size) }
        ExpressiveButtonVariant.TEXT -> TextButton(
            onClick = handleClick, modifier = mods, enabled = enabled, shape = shape,
            colors = colors, contentPadding = contentPadding, border = border,
            interactionSource = interaction
        ) { ExpressiveButtonContent(icon, label, size) }
    }
}

@Composable
private fun ExpressiveButtonContent(icon: ImageVector?, label: String?, size: ExpressiveButtonSize) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(size.iconSize))
            if (label != null) Spacer(Modifier.width(8.dp))
        }
        if (label != null) {
            Text(
                text = label,
                style = expressiveButtonTextStyle(size),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ------------------------------------------------------------------ button groups

@Composable
fun ExpressiveButtonGroup(
    options: List<Pair<ImageVector?, String>>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    connected: Boolean = true,
) {
    val feedback = rememberTactileFeedback()
    if (connected) {
        SingleChoiceSegmentedButtonRow(modifier = modifier) {
            options.forEachIndexed { index, (icon, label) ->
                SegmentedButton(
                    selected = selectedIndex == index,
                    onClick = {
                        feedback.perform(TactileHapticRole.Tap)
                        onSelected(index)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {
                        if (icon != null) Icon(icon, contentDescription = null, Modifier.size(18.dp))
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                )
            }
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, (icon, label) ->
                ExpressiveButton(
                    onClick = { onSelected(index) },
                    label = label,
                    icon = icon,
                    variant = if (selectedIndex == index) ExpressiveButtonVariant.FILLED
                    else ExpressiveButtonVariant.TONAL,
                    size = ExpressiveButtonSize.S,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ FAB + FAB menu

data class ExpressiveFabMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun ExpressiveFabMenu(
    items: List<ExpressiveFabMenuItem>,
    modifier: Modifier = Modifier,
    mainIcon: ImageVector = Icons.Filled.Add,
    mainLabel: String = "Actions",
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val feedback = rememberTactileFeedback()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        items.forEachIndexed { index, item ->
            val enterDelay = if (reduceMotion) 0 else (items.size - 1 - index) * 30
            var appeared by remember { mutableStateOf(!expanded) }

            LaunchedEffect(expanded) {
                appeared = !expanded
                if (expanded) delay(enterDelay.toLong())
                appeared = true
            }
            val itemScale by animateFloatAsState(
                targetValue = if (appeared && expanded) 1f else 0.4f,
                animationSpec = if (reduceMotion) snap() else spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "fabItemScale"
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                exit = shrinkVertically(),
            ) {
                Surface(
                    shape = rememberMorphShape(
                        target = ExpressiveShapeKind.PILL,
                        initial = ExpressiveShapeKind.SQUIRCLE,
                        reduceMotion = reduceMotion
                    ),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .scale(itemScale)
                        .clickable {
                            feedback.perform(TactileHapticRole.Tap)
                            item.onClick()
                            onExpandedChange(false)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(item.icon, contentDescription = null, Modifier.size(20.dp))
                        Text(item.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val idleShape = rememberMorphShape(
            target = if (expanded) ExpressiveShapeKind.CutCornerPill else ExpressiveShapeKind.CIRCLE,
            initial = ExpressiveShapeKind.CIRCLE,
            reduceMotion = reduceMotion,
        )
        val pressedShape = rememberMorphShape(
            target = if (pressed) ExpressiveShapeKind.SQUIRCLE else ExpressiveShapeKind.CIRCLE,
            initial = ExpressiveShapeKind.CIRCLE,
            reduceMotion = reduceMotion,
        )
        val fabWidth by animateDpAsState(
            targetValue = if (expanded) 132.dp else 56.dp,
            animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 380f),
            label = "fabWidth"
        )
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(fabWidth)
                .height(56.dp)
                .springStretchPress(interactionSource = interaction)
                .clip(if (pressed) pressedShape else idleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(interactionSource = interaction, indication = null) {
                    feedback.perform(TactileHapticRole.Tap)
                    onExpandedChange(!expanded)
                },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mainLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        mainIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(visible = !expanded, enter = fadeIn(), exit = fadeOut()) {
                Icon(
                    mainIcon,
                    contentDescription = mainLabel,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
