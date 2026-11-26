package com.munzo.storepoint.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a premium, modern glassmorphic panel style with realistic highlights.
 * Uses translucent linear gradients to simulate depth refraction, pairing with solid
 * specular top-and-left borders. Perfect for overlay sheets, modals, and login cards.
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
 * Multi-state tactile modifier implementing balanced squash-and-stretch press physics.
 * Press feedback defaults to GPU-cheap ripple for high-velocity scrolling grids;
 * opt hero surfaces in via `pressFeedback = true`.
 */
@Composable
fun Modifier.tactileBounce(
    pressFeedback: Boolean = false,
    hapticRole: TactileHapticRole? = null,
    // Keep onClick LAST so trailing-lambda call sites (`.tactileBounce { ... }`) keep working.
    onClick: (() -> Unit)? = null
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val feedback = rememberTactileFeedback()

    val pressedModifier = if (pressFeedback) {
        this.springStretchPress(interactionSource = interactionSource)
    } else {
        this
    }

    return if (onClick != null) {
        pressedModifier.clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(),
            onClick = {
                if (hapticRole != null) {
                    feedback.perform(hapticRole)
                }
                onClick()
            }
        )
    } else {
        pressedModifier
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
    val border = borderColor ?: MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (isDark) 0.35f else 0.45f
    )

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

    val specularBrush = remember(glassBg, highlightColor, isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.25f else 0.50f),
                highlightColor,
                Color.Transparent
            )
        )
    }

    val blurred = if (blurRadius > 0.dp) {
        this.blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
    } else {
        this
    }

    val cardShape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    return blurred
        .shadow(
            elevation = elevation,
            shape = cardShape,
            ambientColor = highlightColor,
            spotColor = highlightColor
        )
        .graphicsLayer {
            clip = true
            shape = cardShape
        }
        .background(glassBg, cardShape)
        .border(1.dp, specularBrush, cardShape)
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
