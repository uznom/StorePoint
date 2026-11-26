package com.munzo.storepoint.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.ui.theme.ExpressiveMotion
import com.munzo.storepoint.ui.theme.ExpressiveSquiggleCircularProgress
import com.munzo.storepoint.ui.theme.LocalReduceMotion
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.springStretchPress
import kotlinx.coroutines.delay

/** Item for [ExpressiveNavigationBar]. */
data class ExpressiveNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector? = null,
)

/**
 * Expressive navigation bar with an animated pill-shaped active indicator,
 * spring-stretch press response, and tactile haptic feedback.
 */
@Composable
fun ExpressiveNavigationBar(
    items: List<ExpressiveNavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val feedback = rememberTactileFeedback()
    val indicatorSpring = if (reduceMotion) snap<Float>() else ExpressiveMotion.tactileStretchSpring()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                val interactionSource = remember { MutableInteractionSource() }
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.15f else 1f,
                    animationSpec = indicatorSpring,
                    label = "navIconScale"
                )
                Column(
                    modifier = Modifier
                        .springStretchPress(interactionSource = interactionSource)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(interactionSource = interactionSource, indication = null) {
                            feedback.perform(TactileHapticRole.Tap)
                            onSelected(index)
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.height(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = selected,
                            enter = expandHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut(),
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
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon ?: item.selectedIcon,
                            contentDescription = item.label,
                            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .scale(iconScale)
                                .size(24.dp),
                        )
                    }
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Expressive filter chip: pill <-> squircle morph + spring-stretch press & haptic cue. */
@Composable
fun ExpressiveChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    val feedback = rememberTactileFeedback()
    val pressed = remember { MutableInteractionSource() }
    val isPressed by pressed.collectIsPressedAsState()
    val cornerPercent by animateFloatAsState(
        targetValue = if (selected || isPressed) 50f else 28f,
        animationSpec = if (reduceMotion) snap() else ExpressiveMotion.tactileStretchSpring(),
        label = "chipCornerShape"
    )
    val shape = RoundedCornerShape(percent = cornerPercent.toInt())
    Surface(
        onClick = {
            feedback.perform(TactileHapticRole.Tap)
            onClick()
        },
        shape = shape,
        modifier = modifier
            .height(40.dp)
            .springStretchPress(interactionSource = pressed),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (!selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
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
        targetValue = if (dragged) 1.28f else 1f,
        animationSpec = if (reduceMotion) snap() else ExpressiveMotion.tactileStretchSpring(),
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
    val bounce by animateFloatAsState(
        targetValue = if (checked) 1.06f else 1f,
        animationSpec = if (reduceMotion) snap() else ExpressiveMotion.tactileStretchSpring(),
        label = "switchBounce"
    )
    HeroSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(bounce),
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
    val bounce by animateFloatAsState(
        targetValue = if (checked) 1.08f else 1f,
        animationSpec = if (reduceMotion) snap() else ExpressiveMotion.tactileStretchSpring(),
        label = "checkboxBounce"
    )
    HeroCheckbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(bounce),
    )
}

/** Expressive (organic) loading indicator wrapped to theme colors. */
@Composable
fun ExpressiveLoadingIndicator(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        ExpressiveSquiggleCircularProgress(
            size = size * 0.9f,
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
    }
}

/**
 * Staggered expressive enter for list/grid items: springy fade + rise with a
 * per-index delay. Snaps when reduce-motion is active.
 */
@Composable
fun Modifier.staggeredEnter(index: Int, visible: Boolean = true): Modifier {
    val reduceMotion = LocalReduceMotion.current
    var shown by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(visible) {
        if (!shown && visible) {
            if (!reduceMotion) delay((index.coerceAtMost(8)) * 40L)
            shown = true
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(200),
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
