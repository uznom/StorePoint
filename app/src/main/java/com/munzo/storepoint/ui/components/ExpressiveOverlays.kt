package com.munzo.storepoint.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.theme.LocalReduceMotion
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.springStretchPress
import kotlinx.coroutines.delay

/**
 * Expressive dialog: extra-large squircle corners, springy scale-in on open,
 * adaptive max width/height via [LocalAdaptiveMetrics], keyboard-safe [imePadding],
 * and scrollable body so short landscape viewports never clip dialog actions.
 */
@Composable
fun ExpressiveDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    val metrics = LocalAdaptiveMetrics.current
    var open by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) { open = true }
    val dialogScale by animateFloatAsState(
        targetValue = if (open) 1f else 0.88f,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = 0.76f,
            stiffness = 420f
        ),
        label = "dialogScale"
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        HeroCard(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = metrics.dialogMaxWidth)
                .heightIn(max = metrics.heightDp * metrics.dialogMaxHeightFraction)
                .imePadding()
                .fillMaxWidth()
                .scale(dialogScale),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(if (metrics.isCompactWidth) 18.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                val bodyModifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                Column(
                    modifier = if (scrollable) {
                        bodyModifier.verticalScroll(rememberScrollState())
                    } else {
                        bodyModifier
                    },
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }

                if (confirmButton != null || dismissButton != null) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val stackActions = maxWidth < 340.dp
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
 * Expressive modal bottom sheet with keyboard-safe and navigation-bar-safe insets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** Expressive snackbar shape. */
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

/**
 * Expressive Grid Tile with asymmetric corners, staggered entrance, and tactile spring-stretch.
 */
@Composable
fun ExpressiveGridTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    elevation: Dp = 4.dp,
    staggerIndex: Int = 0,
    labelStyle: TextStyle = MaterialTheme.typography.titleSmall,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = 48.dp,
) {
    val reduceMotion = LocalReduceMotion.current
    val feedback = rememberTactileFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val showDelay = staggerIndex * 40L
    var appeared by remember { mutableStateOf(reduceMotion || staggerIndex == 0) }
    LaunchedEffect(staggerIndex) {
        if (!reduceMotion && staggerIndex > 0) {
            delay(showDelay)
            appeared = true
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.85f,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "gridTileScale"
    )

    val tileShape = AsymmetricCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    HeroCard(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .springStretchPress(interactionSource = interactionSource)
            .clip(tileShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple()
            ) {
                feedback.perform(TactileHapticRole.Tap)
                onClick()
            },
        shape = tileShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(iconSize),
                    tint = iconTint,
                )
            }
            Text(
                text = title,
                style = labelStyle,
                modifier = Modifier.align(Alignment.Start),
                color = labelColor,
            )
        }
    }
}

private data class AsymmetricCornerShape(
    val topStart: Dp,
    val bottomEnd: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            val ts = with(density) { topStart.toPx() }
            val be = with(density) { bottomEnd.toPx() }
            moveTo(ts, 0f)
            lineTo(w, 0f)
            lineTo(w, h - be)
            quadraticBezierTo(w, h, w - be, h)
            lineTo(ts, h)
            quadraticBezierTo(0f, h, 0f, h - ts)
            close()
        }
        return Outline.Generic(path)
    }
}
