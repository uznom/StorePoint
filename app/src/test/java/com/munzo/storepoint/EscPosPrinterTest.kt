package com.munzo.storepoint

import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import com.munzo.storepoint.util.EscPosHelper
import org.junit.Assert.*
import org.junit.Test

class EscPosPrinterTest {

    private val sampleConfig = StoreConfig(
        id = 1,
        storeName = "StorePoint Sari-Sari Store",
        currencySymbol = "₱",
        taxPercentage = 12.0
    )

    private val sampleTx = Transaction(
        id = 101,
        timestamp = 1790937600000L,
        subtotal = 100.0,
        taxAmount = 12.0,
        totalAmount = 112.0,
        cashPaid = 200.0,
        changeAmount = 88.0,
        paymentMethod = "CASH",
        cashierUsername = "cashier1",
        customerName = "Customer Juan"
    )

    private val sampleItems = listOf(
        TransactionItem(id = 1, transactionId = 101, productId = 10, productName = "Coca-Cola 1.5L", price = 75.0, quantity = 1),
        TransactionItem(id = 2, transactionId = 101, productId = 11, productName = "Piattos Cheese 85g", price = 37.0, quantity = 1)
    )

    @Test
    fun printerTypeEnum_containsUsbEscPos() {
        val types = EscPosHelper.PrinterType.values().map { it.name }
        assertTrue(types.contains("USB_ESCPOS"))
        assertTrue(types.contains("BLUETOOTH_ESCPOS"))
        assertTrue(types.contains("NETWORK_ESCPOS"))
        assertTrue(types.contains("SYSTEM_SPOOLER"))
    }

    @Test
    fun sanitizeForEscPos_convertsMultiBytePesoToP() {
        val input = "Total: ₱150.00"
        val sanitized = EscPosHelper.sanitizeForEscPos(input, "₱")
        assertEquals("Total: P150.00", sanitized)
        assertFalse(sanitized.contains("₱"))
    }

    @Test
    fun sanitizeForEscPos_replacesSmartPunctuationWithAscii() {
        val input = "“Special” item—discounted"
        val sanitized = EscPosHelper.sanitizeForEscPos(input)
        assertEquals("\"Special\" item-discounted", sanitized)
    }

    @Test
    fun safeCurrencySymbol_handlesPesoAndBlanks() {
        assertEquals("P", EscPosHelper.safeCurrencySymbol("₱"))
        assertEquals("P", EscPosHelper.safeCurrencySymbol(""))
        assertEquals("$", EscPosHelper.safeCurrencySymbol("$"))
        assertEquals("PHP", EscPosHelper.safeCurrencySymbol("PHP"))
    }

    @Test
    fun receipt58mm_xp58PlusManualTear_doesNotContainPaperCutBytes() {
        val bytes = EscPosHelper.buildReceiptEscPos(
            storeConfig = sampleConfig,
            transaction = sampleTx,
            items = sampleItems,
            is80mm = false,
            kickDrawerOnPrint = true,
            hasAutoCutter = false // XP-58 Plus default: manual tear bar
        )

        // Ensure GS V 66 0 (Paper cut) is NOT in the stream (which causes 'VB@' print garbage on XP-58)
        assertFalse("58mm receipt for XP-58 Plus must not include cut command bytes", containsSubsequence(bytes, EscPosHelper.CMD_PAPER_CUT))

        // Ensure ESC d 5 (Feed lines) IS included to clear the tear bar
        assertTrue("58mm receipt must include feed lines for manual tear bar", containsSubsequence(bytes, EscPosHelper.CMD_FEED_LINES))
    }

    @Test
    fun receipt80mm_withAutoCutter_containsPaperCutBytes() {
        val bytes = EscPosHelper.buildReceiptEscPos(
            storeConfig = sampleConfig,
            transaction = sampleTx,
            items = sampleItems,
            is80mm = true,
            kickDrawerOnPrint = true,
            hasAutoCutter = true
        )

        assertTrue("80mm cutter receipt must include paper cut bytes", containsSubsequence(bytes, EscPosHelper.CMD_PAPER_CUT))
    }

    @Test
    fun drawerKickBytes_containsBothPin2AndPin5Pulses() {
        val kickBytes = EscPosHelper.buildDrawerKickBytes()
        assertTrue(containsSubsequence(kickBytes, EscPosHelper.CMD_DRAWER_KICK_PIN2))
        assertTrue(containsSubsequence(kickBytes, EscPosHelper.CMD_DRAWER_KICK_PIN5))
    }

    @Test
    fun testTicket_58mmXP58_tailorsModelTextAndFeedsTearBar() {
        val testBytes = EscPosHelper.buildTestTicket(
            is80mm = false,
            kickDrawer = false,
            hasAutoCutter = false
        )
        val text = String(testBytes, Charsets.US_ASCII)
        assertTrue(text.contains("XP-58 Plus"))
        assertTrue(text.contains("Manual Tear"))
        assertFalse(containsSubsequence(testBytes, EscPosHelper.CMD_PAPER_CUT))
        assertTrue(containsSubsequence(testBytes, EscPosHelper.CMD_FEED_LINES))
    }

    @Test
    fun shiftZReadingTicket_58mm_respectsAutoCutterSetting() {
        val zTicketNoCut = EscPosHelper.buildShiftZReadingTicket(
            storeConfig = sampleConfig,
            cashierUsername = "cashier1",
            startTime = 1790930000000L,
            endTime = 1790937600000L,
            startingCash = 1000.0,
            endingCash = 1112.0,
            expectedCash = 1112.0,
            totalSales = 112.0,
            cashSales = 112.0,
            nonCashSales = 0.0,
            vatableSales = 100.0,
            vatAmount = 12.0,
            payouts = 0.0,
            transactionCount = 1,
            is80mm = false,
            hasAutoCutter = false
        )
        assertFalse(containsSubsequence(zTicketNoCut, EscPosHelper.CMD_PAPER_CUT))
        assertTrue(containsSubsequence(zTicketNoCut, EscPosHelper.CMD_FEED_LINES))

        val zTicketCut = EscPosHelper.buildShiftZReadingTicket(
            storeConfig = sampleConfig,
            cashierUsername = "cashier1",
            startTime = 1790930000000L,
            endTime = 1790937600000L,
            startingCash = 1000.0,
            endingCash = 1112.0,
            expectedCash = 1112.0,
            totalSales = 112.0,
            cashSales = 112.0,
            nonCashSales = 0.0,
            vatableSales = 100.0,
            vatAmount = 12.0,
            payouts = 0.0,
            transactionCount = 1,
            is80mm = true,
            hasAutoCutter = true
        )
        assertTrue(containsSubsequence(zTicketCut, EscPosHelper.CMD_PAPER_CUT))
    }

    private fun containsSubsequence(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        for (i in 0..haystack.size - needle.size) {
            var match = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }
}
