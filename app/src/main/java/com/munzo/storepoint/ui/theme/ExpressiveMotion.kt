package com.munzo.storepoint.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue

/**
 * Paired horizontal and vertical scale factors for balanced tactile squash-and-stretch.
 */
@Immutable
data class StretchScale(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
)

/**
 * Spring configurations matching Material 3 Expressive and StorePoint's balanced tactile feel.
 *
 * Motion tokens:
 *  - [springBouncy]        : spatial bouncy (FAB, chip, nav indicator morphs)
 *  - [springSnappy]        : default snappy effects (selection, toggles, counter ticks)
 *  - [springFluid]         : smooth spatial (screen containers, sheets, pane switches)
 *  - [pressSpring]         : crisp fast press feedback
 *  - [tactileStretchSpring]: balanced squash-and-stretch spring (snappy damped rebound)
 */
object ExpressiveMotion {

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
     * Balanced tactile squash-and-stretch spring: crisp ~200ms settle with a subtle,
     * physically grounded rebound that never slows down high-speed POS tapping.
     */
    fun tactileStretchSpring() = spring<Float>(
        dampingRatio = 0.62f,
        stiffness = 460f
    )

    /**
     * Returns a press-state driven animated uniform scale for an interactive surface.
     */
    @Composable
    fun rememberPressScale(
        interactionSource: MutableInteractionSource,
        targetDown: Float = 0.94f,
        targetUp: Float = 1f,
        animationSpec: AnimationSpec<Float> = springBouncy(),
    ): Float {
        val reduceMotion = LocalReduceMotion.current
        val isPressed by interactionSource.collectIsPressedAsState()
        return animateFloatAsState(
            targetValue = if (!reduceMotion && isPressed) targetDown else targetUp,
            animationSpec = if (reduceMotion) snap() else animationSpec,
            label = "pressScale",
        ).value
    }

    /**
     * Returns anisotropic squash-and-stretch scale factors on press:
     * slightly widens horizontally (`pressScaleX > 1`) while compressing vertically
     * (`pressScaleY < 1`), then rebounds elastically via [tactileStretchSpring].
     */
    @Composable
    fun rememberStretchScale(
        interactionSource: MutableInteractionSource,
        pressScaleX: Float = 1.024f,
        pressScaleY: Float = 0.948f,
        animationSpec: AnimationSpec<Float> = tactileStretchSpring()
    ): StretchScale {
        val reduceMotion = LocalReduceMotion.current
        val isPressed by interactionSource.collectIsPressedAsState()
        val effectiveSpec = if (reduceMotion) snap() else animationSpec
        val sx by animateFloatAsState(
            targetValue = if (!reduceMotion && isPressed) pressScaleX else 1f,
            animationSpec = effectiveSpec,
            label = "stretchScaleX"
        )
        val sy by animateFloatAsState(
            targetValue = if (!reduceMotion && isPressed) pressScaleY else 1f,
            animationSpec = effectiveSpec,
            label = "stretchScaleY"
        )
        return StretchScale(scaleX = sx, scaleY = sy)
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
