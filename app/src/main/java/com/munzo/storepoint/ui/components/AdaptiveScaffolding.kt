package com.munzo.storepoint.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width class of the current window, used to adapt button/row layouts across
 * phones (COMPACT), foldables & small tablets (MEDIUM) and tablets/desktop (EXPANDED).
 */
enum class ExpressiveWidthClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberExpressiveWidthClass(): ExpressiveWidthClass {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width < 600 -> ExpressiveWidthClass.COMPACT
        width < 840 -> ExpressiveWidthClass.MEDIUM
        else -> ExpressiveWidthClass.EXPANDED
    }
}

/**
 * Centers content and caps its width so forms, lists and action rows stay readable
 * and never stretch edge-to-edge on foldables, tablets and desktop windows.
 */
@Composable
fun AdaptiveContentContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 560.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
                .padding(horizontal = 16.dp),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/**
 * Single action rendered by [ExpressiveActionRow] / [ExpressiveStackedActions].
 */
data class ExpressiveAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val variant: ExpressiveButtonVariant = ExpressiveButtonVariant.FILLED,
    val size: ExpressiveButtonSize = ExpressiveButtonSize.M,
    val enabled: Boolean = true,
    val testTag: String? = null,
    val containerColor: Color? = null,
    val contentColor: Color? = null,
)

/**
 * Responsive action row: buttons sit side by side when there is room, and stack as
 * full-width buttons on narrow windows so labels never truncate or overflow.
 */
@Composable
fun ExpressiveActionRow(
    actions: List<ExpressiveAction>,
    modifier: Modifier = Modifier,
    stackBelowWidth: Dp = 430.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stack = maxWidth < stackBelowWidth
        if (stack) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                actions.forEach { action -> action.ToButton(Modifier.fillMaxWidth()) }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action -> action.ToButton(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ExpressiveAction.ToButton(modifier: Modifier) {
    ExpressiveButton(
        onClick = onClick,
        modifier = modifier,
        label = label,
        icon = icon,
        variant = variant,
        size = size,
        enabled = enabled,
        testTag = testTag,
        containerColor = containerColor,
        contentColor = contentColor,
    )
}

/**
 * Always-stacked, full-width actions - safe default for dialog CTAs and narrow
 * consoles where side-by-side buttons would clip their labels.
 */
@Composable
fun ExpressiveStackedActions(
    actions: List<ExpressiveAction>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.forEach { action -> action.ToButton(Modifier.fillMaxWidth()) }
    }
}
