package com.munzo.storepoint.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star

// ---------------------------------------------------------------- classic scale

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

val ExpressiveAsymmetricShape = RoundedCornerShape(
    topStart = 32.dp, bottomEnd = 32.dp, topEnd = 14.dp, bottomStart = 14.dp
)
val ExpressiveButtonShape = RoundedCornerShape(20.dp)
val ExpressiveCardShape = RoundedCornerShape(28.dp)

/** Asymmetric "GridTile" corner shape: rounded top-left + bottom-right, sharp top-right + bottom-left. Catalog GridTile signature. */
data class AsymmetricCardShape(
    val topLeft: Dp = 16.dp,
    val bottomRight: Dp = 16.dp,
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.layout.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path().apply {
            val w = size.width
            val h = size.height
            val tl = topLeft.toPx()
            val br = bottomRight.toPx()
            moveTo(tl, 0f)
            lineTo(w, 0f)
            lineTo(w, h - br)
            androidx.compose.ui.graphics.quadraticBezierTo(w, h, w - br, h)
            lineTo(tl, h)
            androidx.compose.ui.graphics.quadraticBezierTo(0f, h, 0f, h - tl)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}
val ExpressiveSheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
val ExpressiveSmallCardShape = RoundedCornerShape(14.dp)
val ExpressiveLargeCardShape = RoundedCornerShape(32.dp)
val ExpressiveBottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
val ExpressiveDialogShape = RoundedCornerShape(32.dp)
val ExpressiveSearchBarShape = RoundedCornerShape(24.dp)
val ExpressiveChipShape = RoundedCornerShape(12.dp)
val ExpressiveFabShape = RoundedCornerShape(24.dp)

// ---------------------------------------------------------------- polygon presets

/** Expressive polygon shape vocabulary (M3E morphing shape scale). */
enum class ExpressiveShapeKind {
    /** Soft squircle — the default expressive container shape. */
    SQUIRCLE,

    /** Perfect circle. */
    CIRCLE,

    /** Pill / stadium. */
    PILL,

    /** Cut-corner pill — top-left & bottom-right rounded, top-right & bottom-left sharp.
     *  Catalog BottomAppBarExpressiveFAB signature. */
    CutCornerPill,

    /** 9-sided "cookie" with heavy rounding — hero emphasis shape. */
    COOKIE,

    /** 4-leaf clover — celebratory / success emphasis shape. */
    CLOVER,

    /** 6-sided soft hexagon — secondary container accent. */
    HEXAGON,

    /** Smooth square (zero smoothing distance corners). */
    SQUARE
}

/** Builds the named expressive polygon in a normalized 2x2 box centered on the origin. */
fun expressivePolygon(kind: ExpressiveShapeKind): RoundedPolygon = when (kind) {
    ExpressiveShapeKind.SQUIRCLE -> RoundedPolygon.rectangle(
        width = 2f, height = 2f, rounding = CornerRounding(0.75f, 0.6f)
    )
    ExpressiveShapeKind.CIRCLE -> RoundedPolygon.circle(numVertices = 12, radius = 1f)
    ExpressiveShapeKind.PILL -> RoundedPolygon.pill(width = 2f, height = 1f, smoothing = 0.85f)
    ExpressiveShapeKind.CutCornerPill -> {
        // Cut-corner pill: top-left & bottom-right rounded 0.5, top-right & bottom-left sharp.
        // Approximated as a path-derived RoundedPolygon for morphing with rememberMorphShape.
        val path = androidx.compose.ui.graphics.Path().apply {
            val r = 0.5f // corner radius for the rounded corners
            // Start top-left, after rounded corner
            moveTo(r, 0f)
            // Top edge to top-right (sharp)
            lineTo(2f - r, 0f)
            // Top-right corner (sharp)
            lineTo(2f, r)
            // Right edge down to bottom-right area
            lineTo(2f, 2f - r)
            // Bottom-right rounded corner: arc back toward bottom-left
            arcTo(androidx.compose.ui.geometry.Rect(2f - 2f * r, 2f - 2f * r, 2f * r, 2f * r), -90f, 90f, false)
            // Bottom edge to bottom-left (sharp)
            lineTo(r, 2f)
            // Bottom-left corner (sharp)
            lineTo(0f, 2f - r)
            // Left edge up to top-left area
            lineTo(0f, r)
            // Top-left rounded corner: arc back to start
            arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, 2f * r, 2f * r), 90f, 90f, false)
            close()
        }
        // Convert path to RoundedPolygon for morphing.
        androidx.graphics.shapes.RoundedPolygon(path)
    }
    ExpressiveShapeKind.COOKIE -> RoundedPolygon(
        numVertices = 9,
        radius = 1f,
        rounding = CornerRounding(0.42f, 0.35f)
    )
    ExpressiveShapeKind.CLOVER -> RoundedPolygon.star(
        numVerticesPerRadius = 4,
        radius = 1f,
        innerRadius = 0.82f,
        rounding = CornerRounding(0.55f, 0.5f),
        innerRounding = CornerRounding(0.28f, 0.4f)
    )
    ExpressiveShapeKind.HEXAGON -> RoundedPolygon(
        numVertices = 6,
        radius = 1f,
        rounding = CornerRounding(0.5f, 0.4f)
    )
    ExpressiveShapeKind.SQUARE -> RoundedPolygon.rectangle(
        width = 2f, height = 2f, rounding = CornerRounding(0.35f, 0.1f)
    )
}

/**
 * A Compose [androidx.compose.ui.graphics.Shape] that renders the supplied [Morph] at a
 * given [progress] (0..1). Wrap with animated progress (spring) for expressive morphing.
 */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
) : androidx.compose.ui.graphics.Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val bounds = morph.calculateMaxBounds()
        val minX = bounds[0]; val minY = bounds[1]
        val maxX = bounds[2]; val maxY = bounds[3]
        val srcW = (maxX - minX).coerceAtLeast(1e-4f)
        val srcH = (maxY - minY).coerceAtLeast(1e-4f)
        val scaleX = size.width / srcW
        val scaleY = size.height / srcH

        val path = Path()
        var started = false
        morph.forEachCubic(progress) { cubic ->
            val p0 = Offset((cubic.anchor0X - minX) * scaleX, (cubic.anchor0Y - minY) * scaleY)
            val p1 = Offset((cubic.control0X - minX) * scaleX, (cubic.control0Y - minY) * scaleY)
            val p2 = Offset((cubic.control1X - minX) * scaleX, (cubic.control1Y - minY) * scaleY)
            val p3 = Offset((cubic.anchor1X - minX) * scaleX, (cubic.anchor1Y - minY) * scaleY)
            if (!started) {
                path.moveTo(p0.x, p0.y); started = true
            }
            path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Convenience: renders a static polygon as a Compose shape (via identity morph). */
fun RoundedPolygon.asComposeShape(): MorphShape =
    MorphShape(Morph(this, RoundedPolygon(this)), 0f)

/** Convenience: renders an animated morph between two polygons. */
fun expressiveMorphShape(from: ExpressiveShapeKind, to: ExpressiveShapeKind, progress: Float): MorphShape =
    MorphShape(Morph(expressivePolygon(from), expressivePolygon(to)), progress)

/**
 * Remembers and spring-animates a shape morph from [initial] to [target]. When
 * reduce-motion is enabled the transition snaps instantly (no animation).
 */
@Composable
fun rememberMorphShape(
    target: ExpressiveShapeKind,
    initial: ExpressiveShapeKind = ExpressiveShapeKind.SQUIRCLE,
    reduceMotion: Boolean = false,
): androidx.compose.ui.graphics.Shape {
    val targetPolygon = remember(target) { expressivePolygon(target) }
    val startPolygon = remember(initial) { expressivePolygon(initial) }
    val morph = remember(startPolygon, targetPolygon) { Morph(startPolygon, targetPolygon) }
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduceMotion) {
            androidx.compose.animation.core.snap()
        } else {
            spring(dampingRatio = 0.75f, stiffness = 400f)
        },
        label = "morphProgress"
    )
    return remember(morph, progress) { MorphShape(morph, progress) }
}
