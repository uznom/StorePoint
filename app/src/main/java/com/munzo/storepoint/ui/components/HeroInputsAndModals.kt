package com.munzo.storepoint.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.rememberTactileFeedback

// =========================================================================
// 6. HeroInput / HeroTextField (HeroUI v3 Inputs)
// =========================================================================

enum class HeroInputVariant {
    Bordered,
    Flat,
    Faded
}

@Composable
fun HeroInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    helperText: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    variant: HeroInputVariant = HeroInputVariant.Bordered,
    shape: Shape = HeroUITheme.radius.lg
) {
    HeroInput(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) HeroUITheme.colors.danger else HeroUITheme.colors.muted
                )
            }
        },
        placeholder = placeholder?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeroUITheme.colors.muted.copy(alpha = 0.6f)
                )
            }
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        isError = isError,
        supportingText = (errorMessage ?: helperText)?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isError) HeroUITheme.colors.danger else HeroUITheme.colors.muted
                )
            }
        },
        singleLine = singleLine,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        variant = variant,
        shape = shape
    )
}

@Composable
fun HeroInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    variant: HeroInputVariant = HeroInputVariant.Bordered,
    shape: Shape = HeroUITheme.radius.lg
) {
    val c = HeroUITheme.colors
    val containerBg = when (variant) {
        HeroInputVariant.Bordered -> c.surface
        HeroInputVariant.Flat -> c.surfaceSecondary
        HeroInputVariant.Faded -> c.surfaceSecondary.copy(alpha = 0.5f)
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        isError = isError,
        supportingText = supportingText,
        singleLine = singleLine,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerBg,
            unfocusedContainerColor = containerBg,
            disabledContainerColor = containerBg.copy(alpha = 0.4f),
            focusedBorderColor = if (isError) c.danger else c.accent,
            unfocusedBorderColor = if (isError) c.danger else c.border,
            errorBorderColor = c.danger,
            focusedTextColor = c.foreground,
            unfocusedTextColor = c.foreground,
            cursorColor = c.accent
        )
    )
}

// =========================================================================
// 7. HeroSwitch (HeroUI v3 Animated Switches with haptic cue)
// =========================================================================

@Composable
fun HeroSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    label: String? = null,
    description: String? = null,
    color: HeroColor = HeroColor.Primary,
    enabled: Boolean = true
) {
    val c = HeroUITheme.colors
    val feedback = rememberTactileFeedback()
    val activeTrackColor = when (color) {
        HeroColor.Primary -> c.accent
        HeroColor.Success -> c.success
        HeroColor.Warning -> c.warning
        HeroColor.Danger -> c.danger
        HeroColor.Default -> c.foreground
    }

    val wrappedToggle: ((Boolean) -> Unit)? = onCheckedChange?.let { callback ->
        { next ->
            feedback.perform(TactileHapticRole.CartAdjust)
            callback(next)
        }
    }

    Row(
        modifier = modifier
            .clip(HeroUITheme.radius.md)
            .clickable(enabled = enabled && wrappedToggle != null) {
                wrappedToggle?.invoke(!checked)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (!label.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) c.foreground else c.muted
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted
                    )
                }
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = wrappedToggle,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = activeTrackColor,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = c.muted,
                uncheckedTrackColor = c.surfaceSecondary,
                uncheckedBorderColor = c.border
            )
        )
    }
}

// =========================================================================
// 8. HeroCheckbox (HeroUI v3 Checkboxes with haptic cue)
// =========================================================================

@Composable
fun HeroCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    label: String? = null,
    color: HeroColor = HeroColor.Primary,
    enabled: Boolean = true
) {
    val c = HeroUITheme.colors
    val feedback = rememberTactileFeedback()
    val activeColor = when (color) {
        HeroColor.Primary -> c.accent
        HeroColor.Success -> c.success
        HeroColor.Warning -> c.warning
        HeroColor.Danger -> c.danger
        HeroColor.Default -> c.foreground
    }

    val wrappedToggle: ((Boolean) -> Unit)? = onCheckedChange?.let { callback ->
        { next ->
            feedback.perform(TactileHapticRole.CartAdjust)
            callback(next)
        }
    }

    Row(
        modifier = modifier
            .clip(HeroUITheme.radius.md)
            .clickable(enabled = enabled && wrappedToggle != null) {
                wrappedToggle?.invoke(!checked)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = wrappedToggle,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = activeColor,
                uncheckedColor = c.border,
                checkmarkColor = Color.White
            )
        )
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) c.foreground else c.muted
            )
        }
    }
}

// =========================================================================
// 9. HeroAlert (HeroUI v3 Alert Callouts)
// =========================================================================

@Composable
fun HeroAlert(
    description: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    color: HeroColor = HeroColor.Primary,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null
) {
    val c = HeroUITheme.colors
    val statusColor: Color = when (color) {
        HeroColor.Primary -> c.accent
        HeroColor.Success -> c.success
        HeroColor.Warning -> c.warning
        HeroColor.Danger -> c.danger
        HeroColor.Default -> c.muted
    }
    val defaultIcon: ImageVector = when (color) {
        HeroColor.Primary -> Icons.Default.Info
        HeroColor.Success -> Icons.Default.CheckCircle
        HeroColor.Warning -> Icons.Default.Warning
        HeroColor.Danger -> Icons.Default.Error
        HeroColor.Default -> Icons.Default.Info
    }

    Surface(
        shape = HeroUITheme.radius.lg,
        color = statusColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon ?: defaultIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.foreground
                )
            }
            if (action != null) {
                action()
            }
        }
    }
}

// =========================================================================
// 10. HeroDivider (HeroUI v3 Micro-Border Separators)
// =========================================================================

@Composable
fun HeroDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = HeroUITheme.colors.border
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

// =========================================================================
// 11. HeroSpinner (HeroUI v3 Circular Spinners)
// =========================================================================

@Composable
fun HeroSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.5.dp,
    color: HeroColor = HeroColor.Primary
) {
    val c = HeroUITheme.colors
    val spinnerColor = when (color) {
        HeroColor.Primary -> c.accent
        HeroColor.Success -> c.success
        HeroColor.Warning -> c.warning
        HeroColor.Danger -> c.danger
        HeroColor.Default -> c.foreground
    }
    CircularProgressIndicator(
        modifier = modifier.size(size),
        strokeWidth = strokeWidth,
        color = spinnerColor
    )
}

// =========================================================================
// 12. HeroModal (HeroUI v3 Adaptive & Keyboard-Safe Modals)
// =========================================================================

@Composable
fun HeroModal(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = HeroUITheme.radius.xl,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val metrics = LocalAdaptiveMetrics.current
    val resolvedProperties = DialogProperties(
        dismissOnBackPress = properties.dismissOnBackPress,
        dismissOnClickOutside = properties.dismissOnClickOutside,
        securePolicy = properties.securePolicy,
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = properties.decorFitsSystemWindows
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = resolvedProperties
    ) {
        Surface(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = metrics.dialogMaxWidth)
                .heightIn(max = metrics.heightDp * metrics.dialogMaxHeightFraction)
                .imePadding(),
            shape = shape,
            color = HeroUITheme.colors.surface,
            border = BorderStroke(1.dp, HeroUITheme.colors.border),
            tonalElevation = 6.dp
        ) {
            val baseColumnModifier = Modifier
                .fillMaxWidth()
                .padding(if (metrics.isCompactWidth) 16.dp else 20.dp)
            Column(
                modifier = if (scrollable) {
                    baseColumnModifier.verticalScroll(rememberScrollState())
                } else {
                    baseColumnModifier
                }
            ) {
                content()
            }
        }
    }
}
