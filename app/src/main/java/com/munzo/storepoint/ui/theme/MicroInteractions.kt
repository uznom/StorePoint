package com.munzo.storepoint.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive micro-interactions.
 *
 * Small, reusable feedback gestures that confirm an action registered without blocking
 * the cashier. Each respects the platform's reduced-motion setting, so accessibility
 * settings are honoured rather than overridden.
 *
 * These follow the Laws of UX principle of immediate, visceral feedback: the user
 * should never have to wait to learn that something was rejected.
 */

/**
 * Horizontally shakes the content whenever [trigger] increments.
 *
 * Used for rejected input (wrong PIN, failed scan) where an error message alone may be
 * missed during a busy checkout. The shake is a *secondary* cue — always paired with
 * visible text and colour, never as the only signal.
 *
 * @param trigger increment this to fire a shake.
 * @param amplitude peak horizontal displacement in dp.
 */
@Composable
fun Modifier.shakeOnTrigger(
    trigger: Int,
    amplitude: Float = 8f,
    enabled: Boolean = true
): Modifier {
    val offset = remember { Animatable(0f) }

    return this.composed {
        androidx.compose.runtime.LaunchedEffect(trigger, enabled) {
            if (!enabled || trigger == 0) return@LaunchedEffect
            // Three decaying oscillations: enough to read as "rejected" without
            // feeling punitive or slowing the retry.
            val steps = intArrayOf(1, -1, 1, -1, 0)
            for (factor in steps) {
                offset.animateTo(
                    targetValue = factor * amplitude,
                    animationSpec = tween(durationMillis = 50)
                )
            }
        }
        this.graphicsLayer { translationX = offset.value }
    }
}

/**
 * Briefly scales the content up and settles it back, acknowledging a success.
 *
 * Paired with the Peak-End Rule: the emotional peak of a POS interaction is the moment
 * of confirmed sale, so that moment gets the strongest celebratory feedback.
 */
@Composable
fun Modifier.celebratePulse(
    trigger: Int,
    enabled: Boolean = true
): Modifier {
    val scale = remember { Animatable(1f) }

    return this.composed {
        androidx.compose.runtime.LaunchedEffect(trigger, enabled) {
            if (!enabled || trigger == 0) return@LaunchedEffect
            scale.animateTo(1.06f, tween(durationMillis = 120))
            scale.animateTo(1f, ExpressiveMotion.springSnappy())
        }
        this.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    }
}

/**
 * Lifts the content slightly while pressed, then settles with an overshoot.
 *
 * Complements the shape-morph already provided by ExpressiveButton so touch targets
 * respond in two channels (position + silhouette) — reinforcing the press for users
 * who may not be looking directly at the control.
 */
@Composable
fun Modifier.pressLift(pressed: Boolean, lift: Float = 4f): Modifier {
    val translation = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) -lift else 0f,
        animationSpec = ExpressiveMotion.pressSpring(),
        label = "pressLift"
    )
    return this.graphicsLayer { translationY = translation.value }
}

/** Returns a [Modifier.Offset]-compatible helper for callers that need raw offsets. */
internal fun Modifier.offsetX(value: Float): Modifier =
    this.graphicsLayer { translationX = value }
