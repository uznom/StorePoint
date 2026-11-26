package com.munzo.storepoint.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Copy for the scan/search fields.
 *
 * [label] is the persistent field label; [placeholder] is the transient hint shown
 * while the field is empty. [placeholder] is empty at the narrowest size, where any
 * hint competes with the leading search icon and the trailing scanner button for space.
 */
data class ScanSearchCopy(
    val label: String,
    val placeholder: String
)

/**
 * Single source of truth for the scan/search field copy across the POS, inventory
 * portal, price check and stock-audit surfaces.
 */
object ScanSearchField {

    /** Leading search icon plus the field's own start/end padding. */
    val FIELD_CHROME_RESERVE: Dp = 56.dp

    /** A single trailing slot (clear button, or scanner button). */
    val TRAILING_SLOT_WIDTH: Dp = 48.dp

    /** Row spacing plus the fixed-width Qty field, where the search field shares a row with one. */
    val SIBLING_RESERVE: Dp = 92.dp

    fun copyFor(availableWidth: Dp, hasSiblingField: Boolean = false): ScanSearchCopy {
        val reserved = FIELD_CHROME_RESERVE + TRAILING_SLOT_WIDTH +
            (if (hasSiblingField) SIBLING_RESERVE else 0.dp)
        val textWidth = availableWidth - reserved

        return when {
            textWidth >= 300.dp -> ScanSearchCopy(
                label = "Search Item / Barcode",
                placeholder = "Scan or type a name, SKU, or barcode"
            )
            textWidth >= 190.dp -> ScanSearchCopy(
                label = "Search Item",
                placeholder = "Scan or type..."
            )
            textWidth >= 110.dp -> ScanSearchCopy(
                label = "Search",
                placeholder = "Scan..."
            )
            else -> ScanSearchCopy(label = "Scan", placeholder = "")
        }
    }
}

/**
 * Central, single-source-of-truth adaptive layout contract for StorePoint.
 */
enum class WindowLayout {
    /** Phones and narrow windows (< 600dp). Bottom navigation, single-column lists. */
    Compact,

    /** Small tablets / large foldables (600–839dp). Dual panes may appear. */
    Medium,

    /** Tablets, desktop-class windows and landscape POS terminals (>= 840dp). */
    Expanded
}

/** Vertical size bucket to detect short landscape windows and keyboard-tight screens. */
enum class WindowHeightClass {
    /** Short landscape phones or split-screen (< 480dp). */
    Compact,

    /** Standard phone portrait / 7-inch landscape tablets (480–899dp). */
    Medium,

    /** Tall portrait tablets and desktop monitors (>= 900dp). */
    Expanded
}

/** Navigation chrome posture for multi-destination surfaces like Admin Dashboard. */
enum class AdminNavMode {
    BottomBar,
    CompactRail,
    LabeledRail
}

/**
 * Continuous scalable UI metrics derived from window dimensions.
 * Provided globally via [LocalAdaptiveMetrics] inside `StorePointTheme`.
 */
@Immutable
data class AdaptiveMetrics(
    val widthDp: Dp,
    val heightDp: Dp,
    val windowLayout: WindowLayout,
    val heightClass: WindowHeightClass,
    val isLandscape: Boolean,
    val screenPadding: Dp,
    val cardPadding: Dp,
    val sectionSpacing: Dp,
    val itemSpacing: Dp,
    val minTouchTarget: Dp,
    val catalogMinCellWidth: Dp,
    val posCatalogWeight: Float,
    val posCartWeight: Float,
    val dialogMaxWidth: Dp,
    val dialogMaxHeightFraction: Float,
    val adminNavMode: AdminNavMode,
    val useTabularProductList: Boolean,
    val maxFormWidth: Dp
) {
    val isCompactWidth: Boolean get() = windowLayout == WindowLayout.Compact
    val isMediumWidth: Boolean get() = windowLayout == WindowLayout.Medium
    val isExpandedWidth: Boolean get() = windowLayout == WindowLayout.Expanded
    val isCompactHeight: Boolean get() = heightClass == WindowHeightClass.Compact
}

/** Pure, deterministic computation of [AdaptiveMetrics] for unit tests and Compose. */
fun computeAdaptiveMetrics(widthDp: Int, heightDp: Int): AdaptiveMetrics {
    val width = widthDp.coerceAtLeast(320).dp
    val height = heightDp.coerceAtLeast(320).dp
    val layout = when {
        width < 600.dp -> WindowLayout.Compact
        width < 840.dp -> WindowLayout.Medium
        else -> WindowLayout.Expanded
    }
    val heightClass = when {
        height < 480.dp -> WindowHeightClass.Compact
        height < 900.dp -> WindowHeightClass.Medium
        else -> WindowHeightClass.Expanded
    }
    val isLandscape = widthDp > heightDp

    return when (layout) {
        WindowLayout.Compact -> AdaptiveMetrics(
            widthDp = width,
            heightDp = height,
            windowLayout = layout,
            heightClass = heightClass,
            isLandscape = isLandscape,
            screenPadding = if (width < 380.dp) 10.dp else 14.dp,
            cardPadding = if (width < 380.dp) 12.dp else 14.dp,
            sectionSpacing = if (heightClass == WindowHeightClass.Compact) 10.dp else 14.dp,
            itemSpacing = 8.dp,
            minTouchTarget = 48.dp,
            catalogMinCellWidth = if (width < 380.dp) 140.dp else 156.dp,
            posCatalogWeight = 1f,
            posCartWeight = 0f,
            dialogMaxWidth = (widthDp - 24).coerceAtLeast(280).dp,
            dialogMaxHeightFraction = if (heightClass == WindowHeightClass.Compact) 0.94f else 0.88f,
            adminNavMode = AdminNavMode.BottomBar,
            useTabularProductList = false,
            maxFormWidth = 520.dp
        )
        WindowLayout.Medium -> AdaptiveMetrics(
            widthDp = width,
            heightDp = height,
            windowLayout = layout,
            heightClass = heightClass,
            isLandscape = isLandscape,
            screenPadding = 16.dp,
            cardPadding = 16.dp,
            sectionSpacing = if (heightClass == WindowHeightClass.Compact) 12.dp else 16.dp,
            itemSpacing = 10.dp,
            minTouchTarget = 48.dp,
            catalogMinCellWidth = 164.dp,
            posCatalogWeight = 0.58f,
            posCartWeight = 0.42f,
            dialogMaxWidth = 560.dp,
            dialogMaxHeightFraction = if (heightClass == WindowHeightClass.Compact) 0.92f else 0.86f,
            adminNavMode = AdminNavMode.CompactRail,
            useTabularProductList = false,
            maxFormWidth = 640.dp
        )
        WindowLayout.Expanded -> AdaptiveMetrics(
            widthDp = width,
            heightDp = height,
            windowLayout = layout,
            heightClass = heightClass,
            isLandscape = isLandscape,
            screenPadding = if (width >= 1100.dp) 24.dp else 20.dp,
            cardPadding = 18.dp,
            sectionSpacing = 18.dp,
            itemSpacing = 12.dp,
            minTouchTarget = 52.dp,
            catalogMinCellWidth = if (width >= 1100.dp) 176.dp else 168.dp,
            posCatalogWeight = if (width >= 1100.dp) 0.65f else 0.62f,
            posCartWeight = if (width >= 1100.dp) 0.35f else 0.38f,
            dialogMaxWidth = 640.dp,
            dialogMaxHeightFraction = 0.85f,
            adminNavMode = AdminNavMode.LabeledRail,
            useTabularProductList = true,
            maxFormWidth = 760.dp
        )
    }
}

val LocalAdaptiveMetrics = compositionLocalOf { computeAdaptiveMetrics(widthDp = 412, heightDp = 915) }

/** Computes and remembers [AdaptiveMetrics] for the current window configuration. */
@Composable
fun rememberAdaptiveMetrics(): AdaptiveMetrics {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    val heightDp = config.screenHeightDp
    return remember(widthDp, heightDp) {
        computeAdaptiveMetrics(widthDp = widthDp, heightDp = heightDp)
    }
}

/** The current window layout bucket for this device/window configuration. */
@Composable
fun rememberWindowLayout(): WindowLayout = rememberAdaptiveMetrics().windowLayout

/** Convenience check for phone-optimized navigation patterns. */
@Composable
fun isCompactWindow(): Boolean = rememberWindowLayout() == WindowLayout.Compact
