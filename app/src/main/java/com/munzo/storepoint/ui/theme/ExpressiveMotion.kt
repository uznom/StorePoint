package com.munzo.storepoint.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Spring configurations matching Material 3 Expressive and Android 17 fluid specifications.
 *
 * Expressive motion uses physically-based, bouncy springs organized into a small set of
 * roles (the M3E "motion tokens"):
 *  - [springBouncy]   / spatial bouncy  (fastSpatial ~350ms): FAB, chip, nav indicator morphs
 *  - [springSnappy]   / default effects (defaultEffects ~350ms): selection, toggles
 *  - [springFluid]    / slow spatial    (~500-1000ms): screen-level containers, sheets
 *  - [pressSpring]    / fast effects    (~200ms): press feedback
 */
object ExpressiveMotion {
    // Elegant organic spring with delightful natural bounce
    fun springBouncy() = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = 250f
    )

    fun springSnappy() = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun springFluid() = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun pressSpring() = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )

    /**
     * Returns a press-state driven animated scale for an interactive surface, matching the
     * expressive press-down then settle behavior seen in modern Google surfaces.
     */
    @Composable
    fun rememberPressScale(
        interactionSource: MutableInteractionSource,
        targetDown: Float = 0.94f,
        targetUp: Float = 1f,
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = springBouncy(),
    ): Float {
        val isPressed by interactionSource.collectIsPressedAsState()
        return animateFloatAsState(
            targetValue = if (isPressed) targetDown else targetUp,
            animationSpec = animationSpec,
            label = "pressScale",
        ).value
    }
}

/**
 * CompositionLocal indicating animations should be reduced (accessibility).
 * Provided by [StorePointTheme]; defaults to false so previews stay animated.
 */
val LocalReduceMotion = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Reads the system animator duration scale. A scale of 0 means the user (or the OS,
 * e.g. Remove animations accessibility setting) has disabled animations.
 */
fun readSystemReduceMotion(context: android.content.Context): Boolean =
    try {
        android.provider.Settings.Secure.getFloat(
            context.contentResolver,
            "animator_duration_scale",
            1f
        ) == 0f
    } catch (_: Exception) {
        false
    }
