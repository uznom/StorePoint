package com.munzo.storepoint.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Android 17 / Material 3 Expressive Sine-Wave Squiggle Linear Progress Bar.
 * Renders an organic sinusoidal wave that glides horizontally across the track.
 * Honors [LocalReduceMotion] by freezing the wave phase when reduced motion is enabled.
 */
@Composable
fun ExpressiveSquiggleLinearProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
    amplitude: Dp = 3.5.dp,
    wavelength: Dp = 24.dp,
    strokeWidth: Dp = 3.5.dp
) {
    val reduceMotion = LocalReduceMotion.current
    val phase = if (reduceMotion || progress != null) {
        0f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "SquigglePhase")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SquigglePhaseAngle"
        )
        animated
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((amplitude * 2) + strokeWidth + 4.dp)
            .testTag("expressive_squiggle_progress")
    ) {
        val width = size.width
        val midY = size.height / 2f
        val ampPx = amplitude.toPx()
        val waveLenPx = wavelength.toPx()
        val strokePx = strokeWidth.toPx()

        drawLine(
            color = trackColor,
            start = Offset(0f, midY),
            end = Offset(width, midY),
            strokeWidth = strokePx * 0.75f,
            cap = StrokeCap.Round
        )

        val endX = if (progress != null) (width * progress.coerceIn(0f, 1f)) else width
        if (endX > 0f) {
            val path = Path()
            var x = 0f
            val step = 3f
            var first = true

            while (x <= endX) {
                val currentPhase = if (progress == null) phase else 0f
                val y = midY + (ampPx * sin((2 * PI * (x / waveLenPx)) + currentPhase).toFloat())
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += step
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/**
 * Android 17 / Material 3 Expressive Rosette / Flower Circular Squiggle Spinner.
 */
@Composable
fun ExpressiveSquiggleCircularProgress(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    lobes: Int = 6,
    strokeWidth: Dp = 3.5.dp
) {
    val reduceMotion = LocalReduceMotion.current
    val rotation: Float
    val pulse: Float
    if (reduceMotion) {
        rotation = 0f
        pulse = 0f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "SquiggleCircleTransition")
        val rot by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SquiggleCircleRotation"
        )
        val pul by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SquiggleCirclePulse"
        )
        rotation = rot
        pulse = pul
    }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
            .testTag("expressive_squiggle_circular")
    ) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxRadius = (this.size.minDimension / 2f) - (strokeWidth.toPx() * 1.2f)
        val baseRadius = maxRadius * 0.82f
        val amp = maxRadius * 0.16f
        val strokePx = strokeWidth.toPx()

        drawCircle(
            color = trackColor,
            radius = baseRadius,
            center = center,
            style = Stroke(width = strokePx * 0.6f)
        )

        val path = Path()
        val totalPoints = 90
        for (i in 0..totalPoints) {
            val theta = (2 * PI * (i.toFloat() / totalPoints)).toFloat()
            val r = baseRadius + (amp * sin((lobes * theta) + pulse).toFloat())
            val x = center.x + (r * cos(theta))
            val y = center.y + (r * sin(theta))
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * Android 17 / Material 3 Expressive Morphing Loader.
 */
@Composable
fun ExpressiveMorphingLoader(
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    label: String? = "StorePoint Initializing..."
) {
    val reduceMotion = LocalReduceMotion.current
    val morphProgress = if (reduceMotion) {
        0.5f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "MorphingLoader")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "MorphProgress"
        )
        animated
    }

    val phase = morphProgress % 4f
    val cornerRadius = when {
        phase < 1f -> 14.dp + (18.dp * phase)
        phase < 2f -> 32.dp - (22.dp * (phase - 1f))
        phase < 3f -> 10.dp + (14.dp * (phase - 2f))
        else -> 24.dp - (10.dp * (phase - 3f))
    }

    val rotationAngle = if (reduceMotion) 0f else morphProgress * 90f

    Column(
        modifier = modifier.testTag("expressive_morphing_loader"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    rotationZ = rotationAngle * 0.35f
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                        alpha = 0.35f
                    },
                shape = RoundedCornerShape(cornerRadius),
                color = tertiaryColor
            ) {}

            Surface(
                modifier = Modifier
                    .size(size * 0.92f)
                    .border(
                        BorderStroke(
                            2.dp,
                            Brush.sweepGradient(
                                listOf(
                                    primaryColor,
                                    tertiaryColor,
                                    Color.White.copy(alpha = 0.85f),
                                    primaryColor
                                )
                            )
                        ),
                        shape = RoundedCornerShape(cornerRadius)
                    ),
                shape = RoundedCornerShape(cornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ExpressiveSquiggleCircularProgress(
                        size = size * 0.55f,
                        color = primaryColor,
                        strokeWidth = 3.dp
                    )
                }
            }
        }

        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            ExpressiveSquiggleLinearProgress(
                modifier = Modifier.width(160.dp),
                color = primaryColor,
                amplitude = 2.5.dp,
                wavelength = 18.dp
            )
        }
    }
}

/**
 * Android 17 / Material 3 Expressive Morphing Status Badge.
 */
@Composable
fun ExpressiveMorphingBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    icon: ImageVector? = null
) {
    val reduceMotion = LocalReduceMotion.current
    val morphCorner = if (reduceMotion) {
        16f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "BadgeMorph")
        val animated by infiniteTransition.animateFloat(
            initialValue = 12f,
            targetValue = 24f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "BadgeCorner"
        )
        animated
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(morphCorner.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
