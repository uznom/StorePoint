package com.munzo.storepoint

import com.munzo.storepoint.data.StorePointRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryAndUomTest {

    @Test
    fun uomMultiplier_calculatesEffectiveDeduction() {
        // Scenario: A pack contains 12 pieces (multiplier = 12)
        val packMultiplier = 12
        val packsSold = 3
        val baseUnitsDeducted = packsSold * packMultiplier
        assertEquals(36, baseUnitsDeducted)

        // Starting stock: 100 pieces
        val startingStock = 100
        val remainingStock = startingStock - baseUnitsDeducted
        assertEquals(64, remainingStock)
        assertTrue("Remaining stock should not be negative", remainingStock >= 0)
    }

    @Test
    fun restockCalculation_computesAverageUnitCostAccurately() {
        // Bought 5 boxes of instant noodles, each containing 24 sachets (total 120 base units)
        val boxes = 5
        val sachetsPerBox = 24
        val totalBaseUnits = boxes * sachetsPerBox
        val totalInvoiceCost = 1450.00 // PHP 1450.00 total cost

        val costPerBaseUnit = StorePointRepository.roundMoney(totalInvoiceCost / totalBaseUnits)
        // 1450.0 / 120 = 12.083333333333334 -> 12.08
        assertEquals(12.08, costPerBaseUnit, 0.0)

        // Verifying profit margin calculation
        val retailPricePerSachet = 15.00
        val profitPerSachet = StorePointRepository.roundMoney(retailPricePerSachet - costPerBaseUnit)
        assertEquals(2.92, profitPerSachet, 0.0)
    }

    @Test
    fun returnRefund_calculatesAccurateRestockAndRefundAmount() {
        val returnQuantity = 2
        val itemMultiplier = 6 // e.g. 6-pack
        val baseUnitsRestocked = returnQuantity * itemMultiplier
        assertEquals(12, baseUnitsRestocked)

        val pricePerPack = 150.00
        val totalRefund = StorePointRepository.roundMoney(returnQuantity * pricePerPack)
        assertEquals(300.00, totalRefund, 0.0)
    }
}
