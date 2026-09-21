package com.munzo.storepoint

import com.munzo.storepoint.data.StorePointRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialPrecisionTest {

    @Test
    fun roundMoney_eliminatesFloatingPointDrift() {
        // Classic IEEE 754 floating point drift: 0.1 + 0.2 = 0.30000000000000004
        val unrounded = 0.1 + 0.2
        val rounded = StorePointRepository.roundMoney(unrounded)
        assertEquals(0.30, rounded, 0.0)
    }

    @Test
    fun roundMoney_roundsHalfUpCorrectly() {
        assertEquals(10.55, StorePointRepository.roundMoney(10.545), 0.0)
        assertEquals(10.54, StorePointRepository.roundMoney(10.544), 0.0)
        assertEquals(10.55, StorePointRepository.roundMoney(10.546), 0.0)
    }

    @Test
    fun roundMoney_handlesZeroAndNegativeValues() {
        assertEquals(0.00, StorePointRepository.roundMoney(0.0), 0.0)
        assertEquals(-5.25, StorePointRepository.roundMoney(-5.249), 0.0)
    }

    @Test
    fun calculatePhilippineVat_fromInclusivePrice_isExact() {
        // Tagged retail price in the Philippines already includes 12% VAT
        val retailPrice = 112.00
        // Formula: amount / 1.12 * 0.12
        val vat = StorePointRepository.calculateVatFromInclusive(retailPrice, 12.0)
        assertEquals(12.00, vat, 0.0)

        // Net / VATable sales: amount / 1.12
        val vatableSales = StorePointRepository.calculateVatableSalesFromInclusive(retailPrice, 12.0)
        assertEquals(100.00, vatableSales, 0.0)

        // Customer pays the tagged retail price
        val totalDue = retailPrice
        val cashPaid = 500.00
        val change = StorePointRepository.roundMoney(cashPaid - totalDue)
        assertEquals(388.00, change, 0.0)
    }

    @Test
    fun calculatePhilippineVat_unevenRetailPrice() {
        // Retail item: 100.00 PHP (VAT inclusive)
        val retailPrice = 100.00
        // 100 / 1.12 * 0.12 = 10.71428... -> 10.71
        val vat = StorePointRepository.calculateVatFromInclusive(retailPrice, 12.0)
        assertEquals(10.71, vat, 0.0)

        // Net / VATable sales: 100 - 10.71 = 89.29
        val vatableSales = StorePointRepository.calculateVatableSalesFromInclusive(retailPrice, 12.0)
        assertEquals(89.29, vatableSales, 0.0)

        assertEquals(100.00, StorePointRepository.roundMoney(vatableSales + vat), 0.0)
    }

    @Test
    fun roundMoney_handlesNaNAndInfinitySafely() {
        assertEquals(0.00, StorePointRepository.roundMoney(Double.NaN), 0.0)
        assertEquals(0.00, StorePointRepository.roundMoney(Double.POSITIVE_INFINITY), 0.0)
        assertEquals(0.00, StorePointRepository.roundMoney(Double.NEGATIVE_INFINITY), 0.0)
        // Zero division prevention test
        val zeroDivide = 100.0 / 0.0
        assertEquals(0.00, StorePointRepository.roundMoney(zeroDivide), 0.0)
    }
}
