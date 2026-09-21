package com.munzo.storepoint.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import com.munzo.storepoint.ui.theme.ExpressiveMotion
import com.munzo.storepoint.ui.theme.ExpressiveShapeKind
import com.munzo.storepoint.ui.theme.LocalReduceMotion
import com.munzo.storepoint.ui.theme.rememberMorphShape

/**
 * StorePoint M3 Expressive component library.
 *
 * Every component:
 *  - uses only theme tokens (no hardcoded colors),
 *  - moves with physics-based springs (M3E motion spec),
 *  - morphs its shape on state change (press / select / toggle),
 *  - honors LocalReduceMotion (snap instead of animate).
 */

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
 * morphs the shape toward a squircle while the surface scales down with a bouncy
 * spring and settles back with overshoot. All five M3 variants and XS-XL sizes.
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
    containerColor: androidx.compose.ui.graphics.Color? = null,
    contentColor: androidx.compose.ui.graphics.Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current

    val cornerPercent by animateFloatAsState(
        targetValue = if (pressed) 28f else 50f,
        animationSpec = if (reduceMotion) snap() else ExpressiveMotion.springBouncy(),
        label = "buttonCornerShape"
    )
    val shape = RoundedCornerShape(percent = cornerPercent.toInt())

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (reduceMotion) snap() else ExpressiveMotion.springBouncy(),
        label = "buttonPressScale"
    )

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
                contentColor = contentColor ?: androidx.compose.ui.graphics.Color.Unspecified,
            )
            ExpressiveButtonVariant.TONAL -> ButtonDefaults.filledTonalButtonColors(
                containerColor = containerColor,
                contentColor = contentColor ?: androidx.compose.ui.graphics.Color.Unspecified,
            )
            ExpressiveButtonVariant.OUTLINED -> ButtonDefaults.outlinedButtonColors(
                contentColor = contentColor ?: containerColor,
            )
            ExpressiveButtonVariant.ELEVATED -> ButtonDefaults.elevatedButtonColors(
                containerColor = containerColor,
                contentColor = contentColor ?: androidx.compose.ui.graphics.Color.Unspecified,
            )
            ExpressiveButtonVariant.TEXT -> ButtonDefaults.textButtonColors(
                contentColor = contentColor ?: containerColor,
            )
        }
    } else baseColors
    val border = if (variant == ExpressiveButtonVariant.OUTLINED)
        androidx.compose.foundation.BorderStroke(1.dp, contentColor ?: containerColor ?: MaterialTheme.colorScheme.outline) else null
    val contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = size.hPadding)
    val mods = modifier
        .height(size.height)
        .scale(pressScale)
        .let { if (testTag != null) it.testTag(testTag) else it }

    when (variant) {
        ExpressiveButtonVariant.FILLED -> Button(
            onClick = onClick, modifier = mods, enabled = enabled, shape = shape,
            colors = colors, contentPadding = contentPadding, border = border,
            interactionSource = interaction
        ) { ExpressiveButtonContent(icon, label, size) }
        ExpressiveButtonVariant.TONAL -> FilledTonalButton(
            onClick = onClick, modifier = mods, enabled = enabled, shape = shape,
            colors = colors, contentPadding = contentPadding, border = border,
            interactionSource = interaction
        ) { ExpressiveButtonContent(icon, label, size) }
        ExpressiveButtonVariant.OUTLINED -> OutlinedButton(
            onClick = onClick, modifier = mods, enabled = enabled, shape = shape,
            colors = colors, contentPadding = contentPadding, border = border,
            interactionSource = interaction
        ) { ExpressiveButtonContent(icon, label, size) }
        ExpressiveButtonVariant.ELEVATED -> ElevatedButton(
            onClick = onClick, modifier = mods, enabled = enabled, shape = shape,
            colors = colors, contentPadding = contentPadding, border = border,
            interactionSource = interaction
        ) { ExpressiveButtonContent(icon, label, size) }
        ExpressiveButtonVariant.TEXT -> TextButton(
            onClick = onClick, modifier = mods, enabled = enabled, shape = shape,
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
            if (label != null) androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        }
        if (label != null) {
            Text(
                text = label,
                style = expressiveButtonTextStyle(size),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

// ------------------------------------------------------------------ button groups

/**
 * Expressive button group. [connected] joins segments into one pill surface
 * (M3E connected button group); otherwise renders spaced pill buttons where the
 * selected item fills with the primary role. Selection animates with springs.
 */
@Composable
fun ExpressiveButtonGroup(
    options: List<Pair<ImageVector?, String>>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    connected: Boolean = true,
) {
    if (connected) {
        SingleChoiceSegmentedButtonRow(modifier = modifier) {
            options.forEachIndexed { index, (icon, label) ->
                SegmentedButton(
                    selected = selectedIndex == index,
                    onClick = { onSelected(index) },
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

/** Item in an [ExpressiveFabMenu]. */
data class ExpressiveFabMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * M3E morphing FAB: a circular primary action that morphs into a pill (with a
 * label) while pressed, and into a vertical menu of actions when expanded.
 * Menu items enter with staggered, springy scale/alpha (honoring reduce-motion).
 */
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        // Staggered menu items above the FAB.
        items.forEachIndexed { index, item ->
            val enterDelay = if (reduceMotion) 0 else (items.size - 1 - index) * 30
            var appeared by androidx.compose.runtime.remember { mutableStateOf(!expanded) }

            androidx.compose.runtime.LaunchedEffect(expanded) {
                appeared = !expanded
                if (expanded) kotlinx.coroutines.delay(enterDelay.toLong()); appeared = true
            }
            val itemScale by animateFloatAsState(
                targetValue = if (appeared && expanded) 1f else 0.4f,
                animationSpec = if (reduceMotion) snap() else spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "fabItemScale"
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ),
                exit = androidx.compose.animation.shrinkVertically(),
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

        // Main FAB — shape morphs circle <-> pill when expanded, squircles on press.
        val idleShape = rememberMorphShape(
            target = if (expanded) ExpressiveShapeKind.PILL else ExpressiveShapeKind.CIRCLE,
            initial = ExpressiveShapeKind.CIRCLE,
            reduceMotion = reduceMotion
        )
        val pressedShape = rememberMorphShape(
            target = if (pressed) ExpressiveShapeKind.SQUIRCLE else ExpressiveShapeKind.CIRCLE,
            initial = ExpressiveShapeKind.CIRCLE,
            reduceMotion = reduceMotion
        )
        val pressScale by animateFloatAsState(
            targetValue = if (pressed) 0.92f else 1f,
            animationSpec = if (reduceMotion) snap() else ExpressiveMotion.pressSpring(),
            label = "fabPress"
        )
        val fabWidth by animateDpAsState(
            targetValue = if (expanded) 132.dp else 56.dp,
            animationSpec = if (reduceMotion) snap() else spring(
                dampingRatio = 0.7f, stiffness = 380f
            ),
            label = "fabWidth"
        )
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(fabWidth)
                .height(56.dp)
                .scale(pressScale)
                .clip(if (expanded || pressed) pressedShape else idleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                ) { onExpandedChange(!expanded) },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
            ) {
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
            androidx.compose.animation.AnimatedVisibility(
                visible = !expanded,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
            ) {
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

// ------------------------------------------------------------------ navigation

/** Item for [ExpressiveNavigationBar]. */
data class ExpressiveNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector? = null,
)

/**
 * Expressive navigation bar with an animated pill-shaped active indicator that
 * springs between destinations, plus icon morphing (scale overshoot on select).
 */
@Composable
fun ExpressiveNavigationBar(
    items: List<ExpressiveNavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val indicatorSpring = if (reduceMotion) snap<Float>() else ExpressiveMotion.springBouncy()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 24.dp, topEnd = 24.dp
        ),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.15f else 1f,
                    animationSpec = indicatorSpring,
                    label = "navIconScale"
                )
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelected(index) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.height(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Animated pill indicator
                        androidx.compose.animation.AnimatedVisibility(
                            visible = selected,
                            enter = androidx.compose.animation.expandHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            ) + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkHorizontally() +
                                androidx.compose.animation.fadeOut(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(16.dp))

                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                            )
                        }
                        Icon(
                            imageVector = if (selected) item.selectedIcon
                            else item.unselectedIcon ?: item.selectedIcon,
                            contentDescription = item.label,
                            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.scale(iconScale).size(24.dp),
                        )
                    }
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ chips / toggles

/** Expressive filter chip: pill <-> squircle morph + springy scale on selection. */
@Composable
fun ExpressiveChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    val pressed = androidx.compose.runtime.remember { MutableInteractionSource() }
    val isPressed by pressed.collectIsPressedAsState()
    val cornerPercent by animateFloatAsState(
        targetValue = if (selected || isPressed) 50f else 28f,
        animationSpec = if (reduceMotion) snap() else ExpressiveMotion.springBouncy(),
        label = "chipCornerShape"
    )
    val shape = RoundedCornerShape(percent = cornerPercent.toInt())
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = if (reduceMotion) snap() else ExpressiveMotion.springBouncy(),
        label = "chipScale"
    )
    Surface(
        onClick = onClick,
        shape = shape,

        modifier = modifier.height(40.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (!selected) androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        ) else null,
        interactionSource = pressed,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Expressive slider: 48dp touch target, thumb grows while dragging. */
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val reduceMotion = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val thumbScale by animateFloatAsState(
        targetValue = if (dragged) 1.35f else 1f,
        animationSpec = if (reduceMotion) snap() else ExpressiveMotion.springBouncy(),
        label = "sliderThumb"
    )
    Box(modifier = modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            interactionSource = interaction,
            modifier = Modifier.scale(thumbScale),
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                thumbColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )
    }
}

/** Expressive switch with a springy settle overshoot on toggle. */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val bounce by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (checked) 1.08f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "switchBounce"
    )
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(bounce),
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedIconColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** Expressive checkbox with a springy settle overshoot on check. */
@Composable
fun ExpressiveCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val bounce by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (checked) 1.12f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "checkboxBounce"
    )
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(bounce),
        colors = CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.primary,
            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

// ------------------------------------------------------------------ loading / lists

/** Expressive (organic) loading indicator wrapped to theme colors. */
@Composable
fun ExpressiveLoadingIndicator(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        com.munzo.storepoint.ui.theme.ExpressiveSquiggleCircularProgress(size = size * 0.9f, color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
    }
}

/**
 * Staggered expressive enter for list/grid items: springy fade + rise with a
 * per-index delay. Snaps when reduce-motion is active.
 */
@Composable
fun Modifier.staggeredEnter(index: Int, visible: Boolean = true): Modifier {
    val reduceMotion = LocalReduceMotion.current
    var shown by androidx.compose.runtime.remember { mutableStateOf(reduceMotion) }
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (!shown && visible) {
            if (!reduceMotion) kotlinx.coroutines.delay((index.coerceAtMost(8)) * 40L)
            shown = true
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else androidx.compose.animation.core.tween(200),
        label = "staggerAlpha"
    )
    val rise by animateFloatAsState(
        targetValue = if (shown) 0f else 24f,
        animationSpec = if (reduceMotion) snap() else ExpressiveMotion.springFluid(),
        label = "staggerRise"
    )
    return this.then(
        Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = rise
        }
    )
}

// ------------------------------------------------------------------ dialog / sheet

/**
 * Expressive dialog: extra-large squircle corners, springy scale-in on open,
 * tonal surface (no heavy shadows). Drop-in replacement for androidx Dialog.
 */
@Composable
fun ExpressiveDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    var open by androidx.compose.runtime.remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { open = true }
    val dialogScale by animateFloatAsState(
        targetValue = if (open) 1f else 0.85f,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = 0.8f, stiffness = 380f
        ),
        label = "dialogScale"
    )
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        androidx.compose.material3.Card(
            modifier = modifier
                .fillMaxWidth()
                .scale(dialogScale),
            shape = RoundedCornerShape(28.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                content()
                if (confirmButton != null || dismissButton != null) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val stackActions = maxWidth < 380.dp
                        if (stackActions) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                confirmButton?.invoke()
                                dismissButton?.invoke()
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                dismissButton?.invoke()
                                confirmButton?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Expressive modal bottom sheet: large squircle top corners, drag handle,
 * springy slide-in. Wraps androidx.compose.material3.ModalBottomSheet.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 32.dp, topEnd = 32.dp
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        },
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/**
 * Expressive snackbar data + host helpers. Use with Scaffold SnackbarHost:
 * pill shape, tonal inverse surface, springy slide-up.
 */
@Composable
fun ExpressiveSnackbarShape(): Shape = RoundedCornerShape(16.dp)

/** Large flexible top app bar title with expressive typography. */
@Composable
fun ExpressiveLargeAppBarTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
