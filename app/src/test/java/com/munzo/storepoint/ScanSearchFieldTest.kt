package com.munzo.storepoint

import com.munzo.storepoint.ui.layout.ScanSearchField
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scan/search field copy has to degrade as the field narrows, otherwise the long
 * fixed labels ("Search Item / Barcode", "Audit Item / SKU Barcode") are ellipsized on
 * small terminals and split-screen windows. These tests pin the tier boundaries.
 */
class ScanSearchFieldTest {

    /** textWidth = availableWidth - reserve; reserve is 56 + 48 = 104dp with no sibling. */
    private fun widthForTextWidth(textWidth: Int) = (textWidth + 104).dp

    @Test
    fun wideFieldGetsFullLabelAndDescriptivePlaceholder() {
        val copy = ScanSearchField.copyFor(widthForTextWidth(400))
        assertEquals("Search Item / Barcode", copy.label)
        assertEquals("Scan or type a name, SKU, or barcode", copy.placeholder)
    }

    @Test
    fun mediumFieldShortensToSearchItem() {
        val copy = ScanSearchField.copyFor(widthForTextWidth(220))
        assertEquals("Search Item", copy.label)
        assertEquals("Scan or type...", copy.placeholder)
    }

    @Test
    fun narrowFieldShortensToSearch() {
        val copy = ScanSearchField.copyFor(widthForTextWidth(130))
        assertEquals("Search", copy.label)
        assertEquals("Scan...", copy.placeholder)
    }

    @Test
    fun veryNarrowFieldFallsBackToScanWithNoPlaceholder() {
        val copy = ScanSearchField.copyFor(widthForTextWidth(60))
        assertEquals("Scan", copy.label)
        assertEquals("A placeholder cannot fit at this width", "", copy.placeholder)
    }

    @Test
    fun copyDegradesMonotonicallyAsWidthShrinks() {
        val widths = listOf(600, 400, 300, 220, 190, 130, 110, 80, 40).map { it.dp }
        val labels = widths.map { ScanSearchField.copyFor(it).label }
        // Each step down must never increase the amount of text shown.
        for (i in 1 until labels.size) {
            assertTrue(
                "Label grew when narrowing: ${labels[i - 1]} -> ${labels[i]}",
                labels[i].length <= labels[i - 1].length
            )
        }
    }

    @Test
    fun siblingFieldReserveShiftsCopyDownOneTier() {
        // 220dp of text space with no sibling is "Search Item".
        val alone = ScanSearchField.copyFor(widthForTextWidth(220))
        assertEquals("Search Item", alone.label)

        // The same field width, but sharing a row with the fixed-width Qty field, has
        // 92dp less room and must fall back to the shorter copy.
        val shared = ScanSearchField.copyFor(widthForTextWidth(220), hasSiblingField = true)
        assertEquals("Search", shared.label)
        assertNotEquals(alone.label, shared.label)
    }

    @Test
    fun labelsAndPlaceholdersNeverExceedTheTiersThresholds() {
        // Guards against a future edit making a tier's copy longer than the space it
        // was sized for, which would reintroduce the ellipsis this was built to remove.
        val cases = mapOf(
            300 to ScanSearchField.copyFor(widthForTextWidth(300)),
            190 to ScanSearchField.copyFor(widthForTextWidth(190)),
            110 to ScanSearchField.copyFor(widthForTextWidth(110))
        )
        for ((threshold, copy) in cases) {
            assertTrue(
                "Tier >=${threshold}dp label is too long: '${copy.label}'",
                copy.label.length <= 22
            )
            assertTrue(
                "Tier >=${threshold}dp placeholder is too long: '${copy.placeholder}'",
                copy.placeholder.length <= 38
            )
        }
    }
}
