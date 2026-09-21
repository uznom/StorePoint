package com.munzo.storepoint.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

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
