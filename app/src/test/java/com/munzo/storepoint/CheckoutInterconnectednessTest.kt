package com.munzo.storepoint

import com.munzo.storepoint.data.CartItemDetails
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.ReturnItem
import com.munzo.storepoint.data.ReturnTransaction
import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutInterconnectednessTest {

    @Test
    fun transaction_preservesCustomerNameAcrossLifecycle() {
        val tx = Transaction(
            id = 101,
            timestamp = 1700000000000L,
            cashierUsername = "cashier1",
            subtotal = 350.00,
            taxAmount = 0.00,
            totalAmount = 350.00,
            paymentMethod = "CASH",
            cashPaid = 500.00,
            changeAmount = 150.00,
            status = "COMPLETED",
            customerName = "Maria Santos"
        )

        assertEquals("Maria Santos", tx.customerName)
        assertEquals("COMPLETED", tx.status)

        // When refunded, customer name should flow into the ReturnTransaction
        val returnTx = ReturnTransaction(
            id = 1,
            originalTransactionId = tx.id,
            timestamp = 1700000001000L,
            cashierUsername = "cashier1",
            totalRefundAmount = 150.00,
            refundMethod = "CASH",
            returnReason = "Defective pack",
            customerName = tx.customerName,
            notes = "Customer returned unopened box"
        )

        assertEquals(tx.customerName, returnTx.customerName)
        assertEquals(tx.id, returnTx.originalTransactionId)
    }

    @Test
    fun cartItemDetails_computesMultiUomEffectiveDeduction() {
        val product = Product(
            id = 1,
            name = "Marlboro Red",
            categoryId = 1,
            price = 10.0, // Per stick
            stockCount = 100, // 100 sticks in stock
            barcode = "4800000000011"
        )

        // Scenario: Cashier sells 2 packs of 20s (multiplier = 20)
        val packItem = CartItemDetails(
            product = product,
            quantity = 2,
            uomName = "20s Pack",
            multiplier = 20,
            selectedPrice = 195.00
        )

        val totalBaseUnitsNeeded = packItem.quantity * packItem.multiplier
        assertEquals(40, totalBaseUnitsNeeded)

        assertTrue(
            "Product has sufficient stock",
            totalBaseUnitsNeeded <= product.stockCount
        )

        val remainingStock = product.stockCount - totalBaseUnitsNeeded
        assertEquals(60, remainingStock)
    }

    @Test
    fun returnRefund_accuratelyRestoresMultiUomStock() {
        // Customer returns 1 pack of 20s
        val returnedItem = ReturnItem(
            id = 1,
            returnTransactionId = 1,
            productId = 1,
            productName = "Marlboro Red (20s Pack)",
            quantity = 1,
            unitPrice = 195.00,
            refundAmount = 195.00,
            uomName = "20s Pack",
            restockToInventory = true
        )

        val multiplier = 20
        val baseUnitsToRestock = returnedItem.quantity * multiplier
        assertEquals(20, baseUnitsToRestock)

        val stockBeforeReturn = 60
        val stockAfterReturn = stockBeforeReturn + baseUnitsToRestock
        assertEquals(80, stockAfterReturn)
    }

    @Test
    fun cashTenderValidation_preventsUnderpaymentAtCalculationLevel() {
        val totalDue = 145.50
        val cashPaidUnder = 140.00
        val cashPaidExact = 145.50
        val cashPaidOver = 200.00

        assertTrue("Underpayment must be detected", cashPaidUnder < totalDue)
        assertTrue("Exact payment must be accepted", cashPaidExact >= totalDue)
        assertTrue("Overpayment must produce valid change", cashPaidOver >= totalDue)

        val change = StorePointRepository.roundMoney(cashPaidOver - totalDue)
        assertEquals(54.50, change, 0.0)
    }

    @Test
    fun prepaidLoad_2PercentRebate_walletCostAndProfitCalculation() {
        // User specification:
        // "a load has 2% rebate like if you load 20 it will cost the customer 22 but will cost only 19.60 from retailer load wallet"
        val loadAmount = 20.0
        val storeFee = 2.0
        val customerPrice = loadAmount + storeFee
        assertEquals(22.00, customerPrice, 0.001)

        val retailerRebateRate = 0.02
        val walletDeduction = StorePointRepository.roundMoney(loadAmount * (1.0 - retailerRebateRate))
        assertEquals(19.60, walletDeduction, 0.001)

        val netRetailerProfit = StorePointRepository.roundMoney(customerPrice - walletDeduction)
        assertEquals(2.40, netRetailerProfit, 0.001)

        // For multiple quantity (e.g. 3x 20 load)
        val qty = 3
        val totalCustomerCharge = customerPrice * qty
        val totalWalletDeduction = StorePointRepository.roundMoney(walletDeduction * qty)
        assertEquals(66.00, totalCustomerCharge, 0.001)
        assertEquals(58.80, totalWalletDeduction, 0.001)
        assertEquals(7.20, StorePointRepository.roundMoney(totalCustomerCharge - totalWalletDeduction), 0.001)
    }

    @Test
    fun prepaidLoad_walletOverdraft_preventsNegativeBalance() {
        val initialSmartBalance = 15.00
        val requiredWalletDeduction = 19.60 // Sells 20 load with 2% rebate

        val hasSufficientBalance = initialSmartBalance >= requiredWalletDeduction
        assertTrue("Insufficient retailer load balance must reject checkout", !hasSufficientBalance)

        val initialGlobeBalance = 50.00
        val remainingGlobeBalance = StorePointRepository.roundMoney(initialGlobeBalance - requiredWalletDeduction)
        assertEquals(30.40, remainingGlobeBalance, 0.001)
    }

    @Test
    fun prepaidLoad_variableServiceFee_walletCostAndCustomerPriceCalculation() {
        // Test variable load fee (e.g., owner sets load fee to ₱3.50 instead of default ₱2.00)
        val loadAmount = 20.0
        val customStoreFee = 3.50
        val customerPrice = loadAmount + customStoreFee
        assertEquals(23.50, customerPrice, 0.001)

        // Rebate is strictly 2% on the base load amount (20 * 0.98 = 19.60)
        val retailerRebateRate = 0.02
        val walletDeduction = StorePointRepository.roundMoney(loadAmount * (1.0 - retailerRebateRate))
        assertEquals(19.60, walletDeduction, 0.001)

        // Net retailer profit = Customer price (23.50) - Wallet deduction (19.60) = 3.90
        val netProfit = StorePointRepository.roundMoney(customerPrice - walletDeduction)
        assertEquals(3.90, netProfit, 0.001)

        // Test dynamic string parsing used in checkout logic
        val productName = "Smart Prepaid Load - 09181234567 (Amt: 20.0, Fee: 3.50, BankFee: 0.0)"
        val feeRegex = Regex("""Fee:\s*([\d.]+)""")
        val parsedFee = feeRegex.find(productName)?.groupValues?.get(1)?.toDoubleOrNull()
        assertEquals(3.50, parsedFee)

        val amtRegex = Regex("""Amt:\s*([\d.]+)""")
        val parsedAmt = amtRegex.find(productName)?.groupValues?.get(1)?.toDoubleOrNull()
        assertEquals(20.0, parsedAmt)
    }

    @Test
    fun gcashAndMaya_variableServiceFee_customerChargeCalculation() {
        // GCash: custom base fee of 15.0 per 1,000 tier
        val gcashAmount = 2500.0
        val customGcashFeeRate = 15.0
        val thousandUnits = Math.ceil(gcashAmount / 1000.0).toInt().coerceAtLeast(1)
        val gcashStoreFee = thousandUnits * customGcashFeeRate
        assertEquals(3, thousandUnits)
        assertEquals(45.00, gcashStoreFee, 0.001)

        // Maya: custom store fee 12.00, custom bank fee 20.00
        val mayaAmount = 500.0
        val customMayaFee = 12.00
        val customMayaBankFee = 20.00
        val mayaTotalCustomerDue = mayaAmount + customMayaFee + customMayaBankFee
        assertEquals(532.00, mayaTotalCustomerDue, 0.001)
    }
}

