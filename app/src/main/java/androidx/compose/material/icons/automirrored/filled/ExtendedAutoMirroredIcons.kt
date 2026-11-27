package androidx.compose.material.icons.automirrored.filled

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight subset of Material Icons Extended actually referenced by StorePoint POS.
 * Replaces the 35 MB material-icons-extended AAR (~10,000 unused vector classes) to keep
 * cold builds, DEXing, and APK size fast and compact.
 */
private val iconCache = ConcurrentHashMap<String, ImageVector>()

private fun cachedIcon(name: String, vararg paths: String): ImageVector {
    return iconCache.computeIfAbsent(name) {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        )
        for (raw in paths) {
            val isEvenOdd = raw.startsWith("E:")
            val d = if (isEvenOdd) raw.substring(2) else raw
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                pathFillType = if (isEvenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = SolidColor(Color.Black),
            )
        }
        builder.build()
    }
}

val Icons.AutoMirrored.Filled.Backspace: ImageVector get() = cachedIcon("Icons.AutoMirrored.Filled.Backspace", "M22,3L7,3c-0.69,0,-1.23,0.35,-1.59,0.88L0,12l5.41,8.11c0.36,0.53,0.9,0.89,1.59,0.89h15c1.1,0,2,-0.9,2,-2L24,5c0,-1.1,-0.9,-2,-2,-2ZM19,15.59L17.59,17L14,13.41L10.41,17L9,15.59L12.59,12L9,8.41L10.41,7L14,10.59L17.59,7L19,8.41L15.41,12L19,15.59Z")
val Icons.AutoMirrored.Filled.OpenInNew: ImageVector get() = cachedIcon("Icons.AutoMirrored.Filled.OpenInNew", "M19,19H5V5h7V3H5c-1.11,0,-2,0.9,-2,2v14c0,1.1,0.89,2,2,2h14c1.1,0,2,-0.9,2,-2v-7h-2v7ZM14,3v2h3.59l-9.83,9.83l1.41,1.41L19,6.41V10h2V3h-7Z")
val Icons.AutoMirrored.Filled.TrendingFlat: ImageVector get() = cachedIcon("Icons.AutoMirrored.Filled.TrendingFlat", "M22,12l-4,-4v3H3v2h15v3Z")
