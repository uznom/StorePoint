package com.munzo.storepoint.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerProductCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer_transition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    // The skeleton mirrors the real catalog card's intrinsic height (chip row +
    // 2-line title + price row) instead of an arbitrary fixed height, so the grid
    // does not jump when the real products replace the placeholders.
    Card(
        modifier = Modifier.fillMaxWidth().height(132.dp),
        shape = com.munzo.storepoint.ui.theme.AsymmetricCardShape(16.dp, 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category tag + stock pill row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(18.dp)
                        .background(Color.LightGray.copy(alpha = alpha), RoundedCornerShape(6.dp))
                )
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(18.dp)
                        .background(Color.LightGray.copy(alpha = alpha), RoundedCornerShape(6.dp))
                )
            }

            // Two-line name bar, matching the real card's fixed 2-line title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(Color.LightGray.copy(alpha = alpha), RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .background(Color.LightGray.copy(alpha = alpha), RoundedCornerShape(4.dp))
            )

            // Simulated Price Bar
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(12.dp)
                    .background(Color.LightGray.copy(alpha = alpha), RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
fun ExpressiveLoadingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressive_rotate")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(72.dp)
            .graphicsLayer {
                rotationZ = rotationAngle
                scaleX = scaleFactor
                scaleY = scaleFactor
            },
        contentAlignment = Alignment.Center
    ) {
        // Double overlapping circles/morphed cards using Material 3 Expressive shapes & dynamic color schema
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(18.dp)
                )
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(12.dp)
                )
        )
    }
}
