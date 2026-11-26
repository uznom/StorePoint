package com.munzo.storepoint.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Semantic haptic feedback roles for StorePoint POS interactions.
 * Keeps tactile cues consistent and unobtrusive across high-speed checkout flows.
 */
enum class TactileHapticRole {
    /** Standard button, chip, or PIN pad tap. */
    Tap,
    /** Barcode scanned or product tile added to cart. */
    ScanSuccess,
    /** Cart quantity incremented/decremented or toggle switched. */
    CartAdjust,
    /** Checkout completed, shift closed, or form saved. */
    ConfirmSuccess,
    /** Wrong PIN, failed scan, out-of-stock, or validation rejection. */
    WarningReject
}

/**
 * Unified haptic dispatcher that maps semantic POS roles to Android haptic constants
 * with clean API-level fallbacks.
 */
class TactileFeedbackController(
    private val view: View,
    private val haptic: HapticFeedback
) {
    fun perform(role: TactileHapticRole) {
        val constant = when (role) {
            TactileHapticRole.Tap -> HapticFeedbackConstants.KEYBOARD_TAP
            TactileHapticRole.ScanSuccess ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
            TactileHapticRole.CartAdjust -> HapticFeedbackConstants.CLOCK_TICK
            TactileHapticRole.ConfirmSuccess ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
            TactileHapticRole.WarningReject ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
        }
        val handled = runCatching { view.performHapticFeedback(constant) }.getOrDefault(false)
        if (!handled) {
            val fallback = when (role) {
                TactileHapticRole.WarningReject,
                TactileHapticRole.ConfirmSuccess -> HapticFeedbackType.LongPress
                else -> HapticFeedbackType.TextHandleMove
            }
            runCatching { haptic.performHapticFeedback(fallback) }
        }
    }
}

@Composable
fun rememberTactileFeedback(): TactileFeedbackController {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    return remember(view, haptic) { TactileFeedbackController(view, haptic) }
}

/**
 * Balanced tactile squash-and-stretch press modifier.
 * Slightly stretches along X while compressing along Y during press, then snaps back
 * elastically via [ExpressiveMotion.tactileStretchSpring]. Honors [LocalReduceMotion].
 */
@Composable
fun Modifier.springStretchPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressScaleX: Float = 1.024f,
    pressScaleY: Float = 0.948f,
    pressTranslationY: Float = 1.5f
): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val active = enabled && !reduceMotion && isPressed
    val spec = if (reduceMotion) snap<Float>() else ExpressiveMotion.tactileStretchSpring()

    val sx by animateFloatAsState(
        targetValue = if (active) pressScaleX else 1f,
        animationSpec = spec,
        label = "springStretchX"
    )
    val sy by animateFloatAsState(
        targetValue = if (active) pressScaleY else 1f,
        animationSpec = spec,
        label = "springStretchY"
    )
    val ty by animateFloatAsState(
        targetValue = if (active) pressTranslationY else 0f,
        animationSpec = spec,
        label = "springStretchTY"
    )

    return this.graphicsLayer {
        scaleX = sx
        scaleY = sy
        translationY = ty
    }
}

/**
 * Elastic pop-and-stretch whenever [valueKey] changes (e.g., cart item count, running total,
 * or badge counter). Skips initial composition so screens enter calmly.
 */
@Composable
fun Modifier.elasticValuePop(
    valueKey: Any?,
    enabled: Boolean = true
): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(valueKey, enabled, reduceMotion) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        if (reduceMotion || !enabled) return@LaunchedEffect
        try {
            sx.snapTo(1.09f)
            sy.snapTo(0.93f)
            sx.animateTo(1f, ExpressiveMotion.tactileStretchSpring())
        } finally {
            sx.snapTo(1f)
            sy.snapTo(1f)
        }
    }
    LaunchedEffect(valueKey, enabled, reduceMotion) {
        if (!initialized || reduceMotion || !enabled) return@LaunchedEffect
        try {
            sy.animateTo(1.04f, tween(durationMillis = 70))
            sy.animateTo(1f, ExpressiveMotion.tactileStretchSpring())
        } finally {
            sy.snapTo(1f)
        }
    }

    return this.graphicsLayer {
        scaleX = sx.value
        scaleY = sy.value
    }
}

/**
 * Horizontally shakes the content whenever [trigger] increments.
 * Used for rejected input (wrong PIN, failed scan) paired with visible text and color.
 */
@Composable
fun Modifier.shakeOnTrigger(
    trigger: Int,
    amplitude: Float = 8f,
    enabled: Boolean = true
): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val offset = remember { Animatable(0f) }

    LaunchedEffect(trigger, enabled, reduceMotion) {
        if (reduceMotion || !enabled || trigger == 0) return@LaunchedEffect
        try {
            val steps = intArrayOf(1, -1, 1, -1, 0)
            for (factor in steps) {
                offset.animateTo(
                    targetValue = factor * amplitude,
                    animationSpec = tween(durationMillis = 48)
                )
            }
        } finally {
            offset.snapTo(0f)
        }
    }
    return this.graphicsLayer { translationX = offset.value }
}

/**
 * Briefly stretches and settles the content, acknowledging a confirmed sale or save.
 */
@Composable
fun Modifier.celebratePulse(
    trigger: Int,
    enabled: Boolean = true
): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }

    LaunchedEffect(trigger, enabled, reduceMotion) {
        if (reduceMotion || !enabled || trigger == 0) return@LaunchedEffect
        try {
            sx.animateTo(1.07f, tween(durationMillis = 95))
            sx.animateTo(1f, ExpressiveMotion.tactileStretchSpring())
        } finally {
            sx.snapTo(1f)
        }
    }
    LaunchedEffect(trigger, enabled, reduceMotion) {
        if (reduceMotion || !enabled || trigger == 0) return@LaunchedEffect
        try {
            sy.animateTo(0.96f, tween(durationMillis = 55))
            sy.animateTo(1.06f, tween(durationMillis = 85))
            sy.animateTo(1f, ExpressiveMotion.tactileStretchSpring())
        } finally {
            sy.snapTo(1f)
        }
    }
    return this.graphicsLayer {
        scaleX = sx.value
        scaleY = sy.value
    }
}

/**
 * Lifts the content slightly while pressed, then settles with a snappy spring.
 */
@Composable
fun Modifier.pressLift(pressed: Boolean, lift: Float = 4f): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val shouldAnimate = !reduceMotion
    val translation = animateFloatAsState(
        targetValue = if (shouldAnimate && pressed) -lift else 0f,
        animationSpec = if (reduceMotion) snap() else ExpressiveMotion.pressSpring(),
        label = "pressLift"
    )
    return this.graphicsLayer { translationY = translation.value }
}

/** Returns a [Modifier.Offset]-compatible helper for callers that need raw offsets. */
internal fun Modifier.offsetX(value: Float): Modifier =
    this.graphicsLayer { translationX = value }
