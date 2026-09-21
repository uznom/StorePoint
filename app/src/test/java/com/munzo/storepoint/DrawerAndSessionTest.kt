package com.munzo.storepoint

import com.munzo.storepoint.data.CashierSession
import com.munzo.storepoint.data.DrawerTransaction
import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerAndSessionTest {

    @Test
    fun drawerReconciliation_calculatesExpectedCashCorrectly() {
        val startingCash = 2000.00
        val session = CashierSession(
            id = 1,
            cashierUsername = "cashier_maria",
            startTime = 1000L,
            startingCash = startingCash,
            status = "ACTIVE"
        )

        val transactions = listOf(
            Transaction(
                id = 1, timestamp = 1100L, cashierUsername = "cashier_maria",
                subtotal = 500.0, taxAmount = 0.0, totalAmount = 500.0,
                paymentMethod = "CASH", cashPaid = 500.0, changeAmount = 0.0, status = "COMPLETED"
            ),
            Transaction(
                id = 2, timestamp = 1200L, cashierUsername = "cashier_maria",
                subtotal = 350.0, taxAmount = 0.0, totalAmount = 350.0,
                paymentMethod = "CASH", cashPaid = 400.0, changeAmount = 50.0, status = "COMPLETED"
            ),
            // GCash sale should NOT affect physical cash in drawer
            Transaction(
                id = 3, timestamp = 1300L, cashierUsername = "cashier_maria",
                subtotal = 1000.0, taxAmount = 0.0, totalAmount = 1000.0,
                paymentMethod = "GCASH", cashPaid = 1000.0, changeAmount = 0.0, status = "COMPLETED"
            ),
            // Cancelled sale should NOT affect cash in drawer
            Transaction(
                id = 4, timestamp = 1400L, cashierUsername = "cashier_maria",
                subtotal = 200.0, taxAmount = 0.0, totalAmount = 200.0,
                paymentMethod = "CASH", cashPaid = 200.0, changeAmount = 0.0, status = "CANCELLED"
            )
        )

        val drawerPayouts = listOf(
            // Supplier cash payout
            DrawerTransaction(
                id = 1, sessionId = 1, timestamp = 1250L,
                cashierUsername = "cashier_maria", amount = -150.00,
                type = "PAYOUT", reason = "Ice delivery payout"
            ),
            // Customer refund payout
            DrawerTransaction(
                id = 2, sessionId = 1, timestamp = 1350L,
                cashierUsername = "cashier_maria", amount = -50.00,
                type = "PAYOUT", reason = "Refund Return #1"
            )
        )

        // Calculate active session cash
        val validCashSales = transactions.filter {
            it.timestamp >= session.startTime &&
            it.paymentMethod == "CASH" &&
            it.status != "VOIDED" &&
            it.status != "CANCELLED"
        }.sumOf { it.totalAmount }

        assertEquals(850.00, StorePointRepository.roundMoney(validCashSales), 0.0)

        val totalPayouts = drawerPayouts.filter { it.sessionId == session.id }.sumOf { it.amount }
        assertEquals(-200.00, StorePointRepository.roundMoney(totalPayouts), 0.0)

        val expectedCash = StorePointRepository.roundMoney(session.startingCash + validCashSales + totalPayouts)
        // 2000.00 + 850.00 - 200.00 = 2650.00
        assertEquals(2650.00, expectedCash, 0.0)
    }

    @Test
    fun drawerReconciliation_computesOverageAndShortageExactness() {
        val expectedCash = 2650.00

        // Case 1: Exact match
        val countedExact = 2650.00
        val discrepancyExact = StorePointRepository.roundMoney(countedExact - expectedCash)
        assertEquals(0.00, discrepancyExact, 0.0)

        // Case 2: Shortage of 50 pesos
        val countedShort = 2600.00
        val discrepancyShort = StorePointRepository.roundMoney(countedShort - expectedCash)
        assertEquals(-50.00, discrepancyShort, 0.0)
        assertTrue("Discrepancy is a shortage", discrepancyShort < 0.0)

        // Case 3: Overage of 25.50 pesos
        val countedOver = 2675.50
        val discrepancyOver = StorePointRepository.roundMoney(countedOver - expectedCash)
        assertEquals(25.50, discrepancyOver, 0.0)
        assertTrue("Discrepancy is an overage", discrepancyOver > 0.0)
    }
}
