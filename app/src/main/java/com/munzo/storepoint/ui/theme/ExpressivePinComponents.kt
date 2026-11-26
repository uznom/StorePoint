package com.munzo.storepoint.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics

/** Fixed number of segmented boxes rendered by every PIN field. Always 4. */
const val PIN_BOX_COUNT = 4

/**
 * Android 17 / Material 3 Expressive OTP-Style PIN Input Component.
 */
@Composable
fun ExpressiveOtpPinInput(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isMasked: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    onPinComplete: ((String) -> Unit)? = null,
    enableSoftKeyboard: Boolean = true
) {
    val pinLength = PIN_BOX_COUNT
    val focusRequester = remember { FocusRequester() }
    val reduceMotion = LocalReduceMotion.current

    Box(
        modifier = if (enableSoftKeyboard) {
            modifier.clickable { focusRequester.requestFocus() }
        } else {
            modifier
        }
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
                keyboardType = if (enableSoftKeyboard) KeyboardType.NumberPassword else KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            readOnly = !enableSoftKeyboard,
            cursorBrush = if (enableSoftKeyboard) {
                SolidColor(MaterialTheme.colorScheme.primary)
            } else {
                SolidColor(Color.Transparent)
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (enableSoftKeyboard) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }
                ),
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
                                animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMedium),
                                label = "OtpBorderColor"
                            )

                            val animatedBgColor by animateColorAsState(
                                targetValue = when {
                                    isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                    isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    isFilled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                                },
                                animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMedium),
                                label = "OtpBgColor"
                            )

                            Box(
                                modifier = Modifier
                                    .size(width = 48.dp, height = 54.dp)
                                    .elasticValuePop(isFilled)
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
                                    val cursorAlpha = if (reduceMotion) {
                                        0.85f
                                    } else {
                                        val infiniteTransition = rememberInfiniteTransition(label = "CursorBlink")
                                        val animated by infiniteTransition.animateFloat(
                                            initialValue = 0.2f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(durationMillis = 600),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "CursorAlpha"
                                        )
                                        animated
                                    }
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
}

/**
 * On-screen numeric keypad for PIN entry with balanced spring-stretch & haptic taps.
 */
@Composable
fun ExpressivePinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    val metrics = LocalAdaptiveMetrics.current
    val keyHeight = if (metrics.isCompactHeight) 48.dp else 56.dp
    val keyGap = if (metrics.isCompactHeight) 6.dp else 8.dp
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(keyGap)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(keyGap)
            ) {
                row.forEach { digit ->
                    PinPadKey(
                        modifier = Modifier.weight(1f),
                        height = keyHeight,
                        onClick = { onDigit(digit.first()) },
                        enabled = isEnabled,
                        hapticRole = TactileHapticRole.Tap
                    ) {
                        Text(
                            text = digit,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(keyGap)
        ) {
            PinPadKey(
                modifier = Modifier.weight(1f),
                height = keyHeight,
                onClick = onClear,
                enabled = isEnabled,
                hapticRole = TactileHapticRole.CartAdjust,
                containerColor = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            PinPadKey(
                modifier = Modifier.weight(1f),
                height = keyHeight,
                onClick = { onDigit('0') },
                enabled = isEnabled,
                hapticRole = TactileHapticRole.Tap
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            PinPadKey(
                modifier = Modifier.weight(1f),
                height = keyHeight,
                onClick = onBackspace,
                enabled = isEnabled,
                hapticRole = TactileHapticRole.CartAdjust
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete last digit",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PinPadKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 56.dp,
    enabled: Boolean = true,
    hapticRole: TactileHapticRole = TactileHapticRole.Tap,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    val interactionSource = remember { MutableInteractionSource() }
    val feedback = rememberTactileFeedback()

    Box(
        modifier = modifier
            .height(height)
            .springStretchPress(
                interactionSource = interactionSource,
                enabled = enabled,
                pressScaleX = 1.03f,
                pressScaleY = 0.93f
            )
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor.copy(alpha = containerColor.alpha * alpha))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                onClick = {
                    feedback.perform(hapticRole)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.alpha(alpha)) { content() }
    }
}

/**
 * A complete PIN entry surface: the masked digit display plus an on-screen keypad,
 * with no system keyboard.
 */
@Composable
fun PinPadEntry(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isMasked: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    onPinComplete: ((String) -> Unit)? = null,
    isEnabled: Boolean = true
) {
    val pinLength = PIN_BOX_COUNT
    val metrics = LocalAdaptiveMetrics.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (metrics.isCompactHeight) 10.dp else 16.dp)
    ) {
        ExpressiveOtpPinInput(
            pin = pin,
            onPinChange = onPinChange,
            isMasked = isMasked,
            isError = isError,
            errorMessage = errorMessage,
            enableSoftKeyboard = false,
            modifier = Modifier.fillMaxWidth()
        )

        ExpressivePinPad(
            onDigit = { digit ->
                if (pin.length < pinLength) {
                    val next = pin + digit
                    onPinChange(next)
                    if (next.length == pinLength) {
                        onPinComplete?.invoke(next)
                    }
                }
            },
            onBackspace = {
                if (pin.isNotEmpty()) onPinChange(pin.dropLast(1))
            },
            onClear = { onPinChange("") },
            isEnabled = isEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
