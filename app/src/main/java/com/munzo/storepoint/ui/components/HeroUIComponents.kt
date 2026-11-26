package com.munzo.storepoint.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.springStretchPress

enum class HeroColor {
    Primary,
    Success,
    Warning,
    Danger,
    Default
}

enum class HeroButtonVariant {
    Solid,
    Flat,
    Bordered,
    Light
}

enum class HeroSize {
    Sm,
    Md,
    Lg
}

enum class HeroChipVariant {
    Solid,
    Flat,
    Bordered,
    Dot
}

// =========================================================================
// 1. HeroButton (HeroUI v3 Anatomy with spring-stretch + tactile haptics)
// =========================================================================

@Composable
fun HeroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: HeroColor = HeroColor.Primary,
    variant: HeroButtonVariant = HeroButtonVariant.Solid,
    size: HeroSize = HeroSize.Md,
    enabled: Boolean = true,
    shape: Shape = HeroUITheme.radius.lg,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    text: String? = null,
    contentPadding: PaddingValues? = null,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val feedback = rememberTactileFeedback()
    val c = HeroUITheme.colors

    val (bg, fg, border) = when (color) {
        HeroColor.Primary -> when (variant) {
            HeroButtonVariant.Solid -> Triple(c.accent, c.accentForeground, null)
            HeroButtonVariant.Flat -> Triple(c.accent.copy(alpha = 0.15f), c.accent, null)
            HeroButtonVariant.Bordered -> Triple(Color.Transparent, c.accent, BorderStroke(1.5.dp, c.accent))
            HeroButtonVariant.Light -> Triple(Color.Transparent, c.accent, null)
        }
        HeroColor.Success -> when (variant) {
            HeroButtonVariant.Solid -> Triple(c.success, c.successForeground, null)
            HeroButtonVariant.Flat -> Triple(c.success.copy(alpha = 0.15f), c.success, null)
            HeroButtonVariant.Bordered -> Triple(Color.Transparent, c.success, BorderStroke(1.5.dp, c.success))
            HeroButtonVariant.Light -> Triple(Color.Transparent, c.success, null)
        }
        HeroColor.Warning -> when (variant) {
            HeroButtonVariant.Solid -> Triple(c.warning, c.warningForeground, null)
            HeroButtonVariant.Flat -> Triple(c.warning.copy(alpha = 0.15f), c.warning, null)
            HeroButtonVariant.Bordered -> Triple(Color.Transparent, c.warning, BorderStroke(1.5.dp, c.warning))
            HeroButtonVariant.Light -> Triple(Color.Transparent, c.warning, null)
        }
        HeroColor.Danger -> when (variant) {
            HeroButtonVariant.Solid -> Triple(c.danger, c.dangerForeground, null)
            HeroButtonVariant.Flat -> Triple(c.danger.copy(alpha = 0.15f), c.danger, null)
            HeroButtonVariant.Bordered -> Triple(Color.Transparent, c.danger, BorderStroke(1.5.dp, c.danger))
            HeroButtonVariant.Light -> Triple(Color.Transparent, c.danger, null)
        }
        HeroColor.Default -> when (variant) {
            HeroButtonVariant.Solid -> Triple(c.defaultBtn, c.defaultForeground, null)
            HeroButtonVariant.Flat -> Triple(c.surfaceSecondary, c.surfaceForeground, null)
            HeroButtonVariant.Bordered -> Triple(Color.Transparent, c.foreground, BorderStroke(1.dp, c.border))
            HeroButtonVariant.Light -> Triple(Color.Transparent, c.foreground, null)
        }
    }

    val (height, hPadding, fontSize, iconSize) = when (size) {
        HeroSize.Sm -> Tuple4(34.dp, 12.dp, 12.sp, 14.dp)
        HeroSize.Md -> Tuple4(44.dp, 16.dp, 14.sp, 18.dp)
        HeroSize.Lg -> Tuple4(52.dp, 22.dp, 16.sp, 20.dp)
    }

    val finalBg = if (enabled) bg else bg.copy(alpha = 0.4f)
    val finalFg = if (enabled) fg else fg.copy(alpha = 0.4f)

    Surface(
        onClick = {
            feedback.perform(TactileHapticRole.Tap)
            onClick()
        },
        enabled = enabled,
        shape = shape,
        color = finalBg,
        border = if (enabled) border else border?.copy(brush = SolidColor(c.border.copy(alpha = 0.3f))),
        interactionSource = interactionSource,
        modifier = modifier
            .height(height)
            .springStretchPress(interactionSource = interactionSource, enabled = enabled)
    ) {
        Row(
            modifier = if (contentPadding != null) Modifier.padding(contentPadding) else Modifier.padding(horizontal = hPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (content != null) {
                content()
            } else {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = finalFg,
                        modifier = Modifier.size(iconSize)
                    )
                    if (!text.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
                if (!text.isNullOrBlank()) {
                    Text(
                        text = text,
                        color = finalFg,
                        fontSize = fontSize,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (trailingIcon != null) {
                    if (!text.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = finalFg,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

@Composable
fun HeroOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: HeroColor = HeroColor.Primary,
    size: HeroSize = HeroSize.Md,
    enabled: Boolean = true,
    shape: Shape = HeroUITheme.radius.lg,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    text: String? = null,
    contentPadding: PaddingValues? = null,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    HeroButton(
        onClick = onClick,
        modifier = modifier,
        color = color,
        variant = HeroButtonVariant.Bordered,
        size = size,
        enabled = enabled,
        shape = shape,
        icon = icon,
        trailingIcon = trailingIcon,
        text = text,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun HeroTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: HeroColor = HeroColor.Primary,
    size: HeroSize = HeroSize.Md,
    enabled: Boolean = true,
    shape: Shape = HeroUITheme.radius.lg,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    text: String? = null,
    contentPadding: PaddingValues? = null,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    HeroButton(
        onClick = onClick,
        modifier = modifier,
        color = color,
        variant = HeroButtonVariant.Light,
        size = size,
        enabled = enabled,
        shape = shape,
        icon = icon,
        trailingIcon = trailingIcon,
        text = text,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun HeroFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: HeroColor = HeroColor.Primary,
    size: HeroSize = HeroSize.Md,
    enabled: Boolean = true,
    shape: Shape = HeroUITheme.radius.lg,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    text: String? = null,
    contentPadding: PaddingValues? = null,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    HeroButton(
        onClick = onClick,
        modifier = modifier,
        color = color,
        variant = HeroButtonVariant.Flat,
        size = size,
        enabled = enabled,
        shape = shape,
        icon = icon,
        trailingIcon = trailingIcon,
        text = text,
        contentPadding = contentPadding,
        content = content
    )
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
