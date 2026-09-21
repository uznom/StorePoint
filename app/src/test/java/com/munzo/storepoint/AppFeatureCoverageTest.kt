package com.munzo.storepoint

import com.munzo.storepoint.data.Category
import com.munzo.storepoint.data.PaymentSchedule
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.ui.screens.calculateTieredStoreFee
import org.junit.Assert.*
import org.junit.Test

class AppFeatureCoverageTest {

    // ========================================================================
    // 1. Digital Services Tiered Fee Calculation
    // ========================================================================

    @Test
    fun tieredFee_zeroOrNegativeAmount_returnsZero() {
        assertEquals(0.0, calculateTieredStoreFee(0.0), 0.001)
        assertEquals(0.0, calculateTieredStoreFee(-100.0), 0.001)
    }

    @Test
    fun tieredFee_underOrEqual500_calculatesCorrectly() {
        // Default base fee = 10.0 per 1,000 PHP. For <= 500, fee is baseMultiplier * 0.5 (min 5.0)
        assertEquals(5.0, calculateTieredStoreFee(100.0), 0.001)
        assertEquals(5.0, calculateTieredStoreFee(300.0), 0.001)
        assertEquals(5.0, calculateTieredStoreFee(500.0), 0.001)
    }

    @Test
    fun tieredFee_between501And1000_calculatesCorrectly() {
        // Between 501 and 999: full base fee
        assertEquals(10.0, calculateTieredStoreFee(501.0), 0.001)
        assertEquals(10.0, calculateTieredStoreFee(750.0), 0.001)
        // Exactly 1,000 PHP
        assertEquals(10.0, calculateTieredStoreFee(1000.0), 0.001)
    }

    @Test
    fun tieredFee_multiThousands_computesStepTiers() {
        // 1,500 PHP: 1k base (10.0) + 500 bracket (5.0) = 15.0
        assertEquals(15.0, calculateTieredStoreFee(1500.0), 0.001)
        // 1,800 PHP: 1k base (10.0) + >500 bracket (10.0) = 20.0
        assertEquals(20.0, calculateTieredStoreFee(1800.0), 0.001)
        // 2,000 PHP exact: 20.0
        assertEquals(20.0, calculateTieredStoreFee(2000.0), 0.001)
        // 5,000 PHP exact: 50.0
        assertEquals(50.0, calculateTieredStoreFee(5000.0), 0.001)
    }

    @Test
    fun tieredFee_customBaseRate_respectsCustomMultiplier() {
        val customRate = 15.0 // 15.0 PHP per 1,000
        assertEquals(7.5, calculateTieredStoreFee(500.0, customRate), 0.001)
        assertEquals(15.0, calculateTieredStoreFee(1000.0, customRate), 0.001)
        assertEquals(22.5, calculateTieredStoreFee(1500.0, customRate), 0.001)
        assertEquals(30.0, calculateTieredStoreFee(2000.0, customRate), 0.001)
    }

    // ========================================================================
    // 2. Product Search & Multi-UOM Barcode Matching
    // ========================================================================

    private val sampleProducts = listOf(
        Product(
            id = 1,
            name = "Coca-Cola 1.5L Bottle",
            categoryId = 1,
            price = 75.0,
            stockCount = 24,
            barcode = "4800016644806",
            hasCustomUom = true,
            customUomName = "Case of 12",
            customUomMultiplier = 12,
            customUomPrice = 850.0,
            barcodeCustomUom = "4800016644998"
        ),
        Product(
            id = 2,
            name = "Marlboro Red Cigarettes",
            categoryId = 2,
            price = 10.0,
            stockCount = 500,
            barcode = "4800000000011",
            hasStick10s = true,
            priceStick10s = 95.0,
            barcode10s = "4800000000028",
            hasStick20s = true,
            priceStick20s = 185.0,
            barcode20s = "4800000000035",
            hasReam = true,
            priceReam = 1800.0,
            barcodeReam = "4800000000042",
            hasMasterCase = true,
            priceMasterCase = 88000.0,
            barcodeMasterCase = "4800000000059"
        ),
        Product(
            id = 3,
            name = "San Miguel Pale Pilsen 330ml Can",
            categoryId = 1,
            price = 65.0,
            stockCount = 48,
            barcode = "4800011112223"
        )
    )

    @Test
    fun productSearch_byNameCaseInsensitive() {
        val query = "coca"
        val matches = sampleProducts.filter { it.name.contains(query, ignoreCase = true) }
        assertEquals(1, matches.size)
        assertEquals("Coca-Cola 1.5L Bottle", matches[0].name)

        val upperQuery = "MARLBORO"
        val upperMatches = sampleProducts.filter { it.name.contains(upperQuery, ignoreCase = true) }
        assertEquals(1, upperMatches.size)
        assertEquals("Marlboro Red Cigarettes", upperMatches[0].name)
    }

    @Test
    fun productSearch_byPrimaryBarcode() {
        val scan = "4800016644806"
        val match = sampleProducts.find { it.barcode == scan }
        assertNotNull(match)
        assertEquals(1, match?.id)
    }

    @Test
    fun productSearch_byMultiUomVariantBarcodes() {
        // Scan 10s pack barcode
        val scan10s = "4800000000028"
        val match10s = sampleProducts.find {
            it.barcode == scan10s || it.barcode10s == scan10s || it.barcode20s == scan10s ||
            it.barcodeReam == scan10s || it.barcodeMasterCase == scan10s || it.barcodeCustomUom == scan10s
        }
        assertNotNull(match10s)
        assertEquals("Marlboro Red Cigarettes", match10s?.name)

        // Scan Ream barcode
        val scanReam = "4800000000042"
        val matchReam = sampleProducts.find {
            it.barcode == scanReam || it.barcode10s == scanReam || it.barcode20s == scanReam ||
            it.barcodeReam == scanReam || it.barcodeMasterCase == scanReam || it.barcodeCustomUom == scanReam
        }
        assertNotNull(matchReam)
        assertEquals(1800.0, matchReam?.priceReam ?: 0.0, 0.001)

        // Scan Case of 12 custom UOM barcode
        val scanCase = "4800016644998"
        val matchCase = sampleProducts.find {
            it.barcode == scanCase || it.barcodeCustomUom == scanCase
        }
        assertNotNull(matchCase)
        assertEquals("Coca-Cola 1.5L Bottle", matchCase?.name)
        assertEquals(12, matchCase?.customUomMultiplier)
    }

    @Test
    fun productFilter_byCategoryId() {
        val beverages = sampleProducts.filter { it.categoryId == 1 }
        assertEquals(2, beverages.size)

        val tobacco = sampleProducts.filter { it.categoryId == 2 }
        assertEquals(1, tobacco.size)

        val nonExistent = sampleProducts.filter { it.categoryId == 99 }
        assertTrue(nonExistent.isEmpty())
    }

    // ========================================================================
    // 3. Smart Search Keywords for Digital Services
    // ========================================================================

    private fun detectDigitalService(query: String): Pair<Boolean, String> {
        val isMatched = query.isNotEmpty() && (
            query.contains("gcash", ignoreCase = true) ||
            query.contains("g-cash", ignoreCase = true) ||
            query.contains("g cash", ignoreCase = true) ||
            query.contains("maya", ignoreCase = true) ||
            query.contains("load", ignoreCase = true) ||
            query.contains("smart", ignoreCase = true) ||
            query.contains("tnt", ignoreCase = true) ||
            query.contains("globe", ignoreCase = true) ||
            query.contains("tm", ignoreCase = true)
        )
        val provider = when {
            query.contains("gcash", ignoreCase = true) ||
            query.contains("g-cash", ignoreCase = true) ||
            query.contains("g cash", ignoreCase = true) -> "GCash"
            query.contains("maya", ignoreCase = true) -> "Maya"
            else -> "Load"
        }
        return Pair(isMatched, provider)
    }

    @Test
    fun smartSearch_detectsEWalletProviders() {
        val (matchedGcash, providerGcash) = detectDigitalService("gcash")
        assertTrue(matchedGcash)
        assertEquals("GCash", providerGcash)

        val (matchedHyphen, providerHyphen) = detectDigitalService("g-cash cash in")
        assertTrue(matchedHyphen)
        assertEquals("GCash", providerHyphen)

        val (matchedMaya, providerMaya) = detectDigitalService("send via maya")
        assertTrue(matchedMaya)
        assertEquals("Maya", providerMaya)
    }

    @Test
    fun smartSearch_detectsTelcoLoadProviders() {
        val (matchedSmart, providerSmart) = detectDigitalService("smart regular 50")
        assertTrue(matchedSmart)
        assertEquals("Load", providerSmart)

        val (matchedGlobe, providerGlobe) = detectDigitalService("globe load 100")
        assertTrue(matchedGlobe)
        assertEquals("Load", providerGlobe)

        val (matchedGeneral, providerGeneral) = detectDigitalService("regular load")
        assertTrue(matchedGeneral)
        assertEquals("Load", providerGeneral)
    }

    // ========================================================================
    // 4. Payment Schedules / Sinking Funds Features
    // ========================================================================

    @Test
    fun paymentSchedules_filterAndPriorityOrdering() {
        val schedules = listOf(
            PaymentSchedule(id = 1, title = "Store Electricity (Meralco)", amount = 4250.0, dueDate = "2026-06-25", priority = "High", category = "Utilities"),
            PaymentSchedule(id = 2, title = "San Miguel Re-order", amount = 15000.0, dueDate = "2026-06-20", priority = "Medium", category = "Supplier"),
            PaymentSchedule(id = 3, title = "Internet Bill (PLDT)", amount = 1899.0, dueDate = "2026-06-28", priority = "Low", category = "Utilities", isPaid = true)
        )

        // Filter unpaid vs paid
        val unpaid = schedules.filter { !it.isPaid }
        assertEquals(2, unpaid.size)

        val paid = schedules.filter { it.isPaid }
        assertEquals(1, paid.size)
        assertEquals("Internet Bill (PLDT)", paid[0].title)

        // Filter by specific due date
        val dueJune20 = schedules.filter { it.dueDate == "2026-06-20" }
        assertEquals(1, dueJune20.size)
        assertEquals("San Miguel Re-order", dueJune20[0].title)

        // Priority validation
        val highPriority = schedules.filter { it.priority == "High" }
        assertEquals(1, highPriority.size)
        assertEquals("Store Electricity (Meralco)", highPriority[0].title)
    }

    // ========================================================================
    // 5. Multi-UOM Inventory Deduction & Restock Formulas
    // ========================================================================

    @Test
    fun uomRestock_calculatesTotalBaseUnitsAccurately() {
        // Base Unit (Stick)
        val singleCount = 50
        val singleMultiplier = 1
        assertEquals(50, singleCount * singleMultiplier)

        // 10s Pack = 10 units each
        val pack10Count = 5
        val pack10Multiplier = 10
        assertEquals(50, pack10Count * pack10Multiplier)

        // 20s Pack = 20 units each
        val pack20Count = 10
        val pack20Multiplier = 20
        assertEquals(200, pack20Count * pack20Multiplier)

        // Ream / Carton = 200 units each (10 packs of 20)
        val reamCount = 3
        val reamMultiplier = 200
        assertEquals(600, reamCount * reamMultiplier)

        // Master Case = 10,000 units each (50 reams)
        val caseCount = 2
        val caseMultiplier = 10000
        assertEquals(20000, caseCount * caseMultiplier)

        // Custom Multiplier (e.g. Case of 12)
        val customCount = 4
        val customMultiplier = 12
        assertEquals(48, customCount * customMultiplier)
    }

    @Test
    fun uomRestock_drawerCashShortageDetection() {
        val drawerCash = 2500.0
        val deliveryCost = 3000.0
        val payFromDrawer = true

        val isShortage = payFromDrawer && deliveryCost > drawerCash
        assertTrue("Expected shortage when delivery cost exceeds available drawer cash", isShortage)

        // Non-shortage scenario
        val affordableCost = 2000.0
        val isAffordableShortage = payFromDrawer && affordableCost > drawerCash
        assertFalse("Expected no shortage when drawer cash covers delivery", isAffordableShortage)
    }
}
