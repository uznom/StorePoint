package com.munzo.storepoint

import androidx.compose.ui.unit.dp
import com.munzo.storepoint.ui.layout.AdminNavMode
import com.munzo.storepoint.ui.layout.WindowHeightClass
import com.munzo.storepoint.ui.layout.WindowLayout
import com.munzo.storepoint.ui.layout.computeAdaptiveMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveMetricsTest {

    @Test
    fun compactPhoneUsesBottomBarAndCardList() {
        val m = computeAdaptiveMetrics(widthDp = 390, heightDp = 844)
        assertEquals(WindowLayout.Compact, m.windowLayout)
        assertEquals(WindowHeightClass.Medium, m.heightClass)
        assertEquals(AdminNavMode.BottomBar, m.adminNavMode)
        assertFalse(m.useTabularProductList)
        assertTrue(m.isCompactWidth)
        assertEquals(1f, m.posCatalogWeight, 0.001f)
    }

    @Test
    fun mediumSevenInchTabletUsesCompactRailAndSplitPos() {
        val m = computeAdaptiveMetrics(widthDp = 720, heightDp = 1024)
        assertEquals(WindowLayout.Medium, m.windowLayout)
        assertEquals(WindowHeightClass.Expanded, m.heightClass)
        assertEquals(AdminNavMode.CompactRail, m.adminNavMode)
        assertFalse(m.useTabularProductList)
        assertEquals(0.58f, m.posCatalogWeight, 0.001f)
        assertEquals(0.42f, m.posCartWeight, 0.001f)
    }

    @Test
    fun expandedTenInchPosTerminalUsesLabeledRailAndTabularProducts() {
        val m = computeAdaptiveMetrics(widthDp = 1280, heightDp = 800)
        assertEquals(WindowLayout.Expanded, m.windowLayout)
        assertEquals(AdminNavMode.LabeledRail, m.adminNavMode)
        assertTrue(m.useTabularProductList)
        assertTrue(m.isLandscape)
        assertEquals(0.65f, m.posCatalogWeight, 0.001f)
        assertEquals(0.35f, m.posCartWeight, 0.001f)
        assertEquals(640.dp, m.dialogMaxWidth)
    }

    @Test
    fun shortLandscapeWindowDetectsCompactHeightAndAllowsTallerDialogs() {
        val m = computeAdaptiveMetrics(widthDp = 640, heightDp = 400)
        assertEquals(WindowHeightClass.Compact, m.heightClass)
        assertTrue(m.isCompactHeight)
        assertTrue(m.dialogMaxHeightFraction >= 0.90f)
    }
}
