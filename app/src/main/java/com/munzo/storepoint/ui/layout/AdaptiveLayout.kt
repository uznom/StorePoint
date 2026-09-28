package com.munzo.storepoint.ui.layout

import androidx.compose.runtime.Composable
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
 *
 * The same control is used on a 7" tablet, a 10" terminal and a narrow phone in a
 * split-screen window, so a fixed long label like "Scan/Search Item" or a fixed long
 * placeholder like "Search product name, ID..." gets clipped to an ellipsis at the
 * small end. Rather than hard-coding three copies of each field, every surface
 * resolves its copy from the width it actually has.
 *
 * Widths account for the chrome the text shares the row with: a leading search icon,
 * a trailing clear affordance and a trailing scanner button. That reserved space is
 * [FIELD_CHROME_RESERVE] plus per-trailing-slot [TRAILING_SLOT_WIDTH].
 *
 * This is a pure function of its input, so the tier boundaries are unit tested
 * directly rather than being verified by eye on a device.
 */
object ScanSearchField {

    /** Leading search icon plus the field's own start/end padding. */
    val FIELD_CHROME_RESERVE: Dp = 56.dp

    /** A single trailing slot (clear button, or scanner button). */
    val TRAILING_SLOT_WIDTH: Dp = 48.dp

    /** Row spacing plus the fixed-width Qty field, where the search field shares a row with one. */
    val SIBLING_RESERVE: Dp = 92.dp

    /**
     * Resolves the copy for a search field.
     *
     * @param availableWidth the width the field itself is given, not the width of the
     *   window or the parent row.
     * @param hasSiblingField true when the field shares a row with a fixed-width
     *   neighbour (for example the "Qty" field in the checkout scanner bar), which
     *   reserves additional space.
     */
    fun copyFor(availableWidth: Dp, hasSiblingField: Boolean = false): ScanSearchCopy {
        val reserved = FIELD_CHROME_RESERVE + TRAILING_SLOT_WIDTH +
            (if (hasSiblingField) SIBLING_RESERVE else 0.dp)
        val textWidth = availableWidth - reserved

        return when {
            // Comfortable: the full label and a descriptive hint both fit.
            textWidth >= 300.dp -> ScanSearchCopy(
                label = "Search Item / Barcode",
                placeholder = "Scan or type a name, SKU, or barcode"
            )
            // Medium: keep the label informative, trim the hint.
            textWidth >= 190.dp -> ScanSearchCopy(
                label = "Search Item",
                placeholder = "Scan or type..."
            )
            // Narrow: label only carries the verb, hint is a single word.
            textWidth >= 110.dp -> ScanSearchCopy(
                label = "Search",
                placeholder = "Scan..."
            )
            // Very narrow: fall back to the scan affordance, which is the fastest
            // path on a terminal anyway. No placeholder - there is no room for one.
            else -> ScanSearchCopy(label = "Scan", placeholder = "")
        }
    }
}

/**
 * Central, single-source-of-truth adaptive layout contract for StorePoint.
 *
 * Every screen must derive its responsive behavior from this enum instead of
 * scattering ad-hoc `screenWidthDp >= 600` / `maxWidth < 650.dp` checks, so the
 * app behaves consistently across phones, foldables, tablets and desktop-class
 * windows.
 */
enum class WindowLayout {
    /** Phones and narrow windows (< 600dp). Bottom navigation, single-column lists. */
    Compact,

    /** Small tablets / large foldables (600–839dp). Dual panes may appear. */
    Medium,

    /** Tablets, desktop-class windows and landscape POS terminals (>= 840dp). */
    Expanded
}

/** The current window layout bucket for this device/window configuration. */
@Composable
fun rememberWindowLayout(): WindowLayout {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return when {
        width < 600.dp -> WindowLayout.Compact
        width < 840.dp -> WindowLayout.Medium
        else -> WindowLayout.Expanded
    }
}

/** Convenience check for phone-optimized navigation patterns. */
@Composable
fun isCompactWindow(): Boolean = rememberWindowLayout() == WindowLayout.Compact
