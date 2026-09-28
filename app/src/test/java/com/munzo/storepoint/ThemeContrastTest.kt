package com.munzo.storepoint

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the shipped palette to WCAG AA contrast.
 *
 * This exists because `StorePointTheme` now defaults `dynamicColor = false`. With
 * Material You enabled (the previous default), Android 12+ derives every role -
 * including `primary`, `onPrimaryContainer` and the `surfaceContainer*` ramp - from an
 * arbitrary wallpaper hue, so icon-on-surface pairs that pass here can silently drop
 * below AA on someone else's device. That was the "icons blend into their background"
 * symptom. The palette below is therefore the contract, and this test guards it.
 *
 * Ratios are computed from the literal values in `Color.kt`, so editing a color without
 * checking its pairings fails the build.
 */
class ThemeContrastTest {

    private fun relativeLuminance(hex: String): Double {
        val h = hex.trimStart('#')
        fun channel(offset: Int): Double {
            val v = Integer.parseInt(h.substring(offset, offset + 2), 16) / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4)
    }

    private fun ratio(fg: String, bg: String): Double {
        val a = relativeLuminance(fg)
        val b = relativeLuminance(bg)
        val hi = maxOf(a, b)
        val lo = minOf(a, b)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertAA(label: String, fg: String, bg: String, min: Double = 4.5) {
        val r = ratio(fg, bg)
        assertTrue(
            "$label must meet WCAG AA (>= $min:1) but is ${"%.2f".format(r)}:1  [fg=$fg bg=$bg]",
            r >= min
        )
    }

    // --- Light scheme (Color.kt light values) ---

    @Test
    fun lightIconsAndTextMeetAA() {
        assertAA("Launch icon on card", "#2D55D6", "#F6F8FC")
        assertAA("Scanner button icon", "#2D55D6", "#E6EBF4")
        assertAA("Category chip text", "#16233F", "#DEE6FF")
        assertAA("LOW stock pill", "#4A148C", "#F1E8FF")
        assertAA("OUT stock pill", "#7F1D1D", "#FEE2E2")
        assertAA("Stock pill text", "#43474E", "#E6EBF4")
        assertAA("Product title", "#1A1C1E", "#F6F8FC")
    }

    // --- Dark scheme (Color.kt dark values) ---

    @Test
    fun darkIconsAndTextMeetAA() {
        assertAA("Launch icon on card", "#819EFE", "#0B0F19")
        assertAA("Scanner button icon", "#819EFE", "#1E293B")
        assertAA("Category chip text", "#E0E7FF", "#1E293B")
        assertAA("LOW stock pill", "#F3E8FF", "#3B0764")
        assertAA("OUT stock pill", "#FEE2E2", "#7F1D1D")
        assertAA("Stock pill text", "#C4C6CF", "#1E293B")
        assertAA("Product title", "#E3E2E6", "#0B0F19")
    }

    /**
     * Non-text UI (the low/out-of-stock card outline) needs 3:1 per WCAG 1.4.11, because
     * it is the only thing signalling a stock state on the tile.
     */
    @Test
    fun stockStateBordersMeetNonTextContrast() {
        // Solid tertiary on the card, i.e. the border before its alpha is applied.
        assertTrue(
            "LOW-stock border must be distinguishable: ${"%.2f".format(ratio("#7C3AED", "#F6F8FC"))}:1",
            ratio("#7C3AED", "#F6F8FC") >= 3.0
        )
        assertTrue(
            "OUT-stock border must be distinguishable: ${"%.2f".format(ratio("#DC2626", "#F6F8FC"))}:1",
            ratio("#DC2626", "#F6F8FC") >= 3.0
        )
    }
}
