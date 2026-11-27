package com.munzo.storepoint

import com.munzo.storepoint.data.Category
import com.munzo.storepoint.data.CustomerAccount
import com.munzo.storepoint.data.CustomerCreditEntry
import com.munzo.storepoint.data.ParkedTransactionItem
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.ShiftXReadingSummary
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.TransactionItem
import com.munzo.storepoint.util.CatalogCsvCodec
import com.munzo.storepoint.util.EscPosXReadingFormatter
import com.munzo.storepoint.util.HidBarcodeScannerBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePosFeaturesTest {

    @Test
    fun hidBarcodeBuffer_acceptsRapidHardwareBurst() {
        val buffer = HidBarcodeScannerBuffer()
        val code = "4800016644801"
        var ts = 1_000L
        for (ch in code) {
            buffer.onPrintableChar(ch, ts)
            ts += 12L
        }
        val scanned = buffer.onTerminator(ts + 10L)
        assertEquals(code, scanned)
        assertEquals(0, buffer.pendingLength)
    }

    @Test
    fun hidBarcodeBuffer_rejectsSlowHumanTyping() {
        val buffer = HidBarcodeScannerBuffer()
        var ts = 5_000L
        for (ch in "4800016644801") {
            buffer.onPrintableChar(ch, ts)
            ts += 150L
        }
        val result = buffer.onTerminator(ts + 120L)
        assertNull("Slow human typing must not trigger barcode scan", result)
    }

    @Test
    fun hidBarcodeBuffer_discardsStalePrefixAndCapturesFollowingFastBurst() {
        val buffer = HidBarcodeScannerBuffer()
        // Slow keystrokes followed by rapid hardware burst
        buffer.onPrintableChar('a', 1_000L)
        buffer.onPrintableChar('b', 1_250L)

        var ts = 2_000L
        for (ch in "SKU-9988") {
            buffer.onPrintableChar(ch, ts)
            ts += 8L
        }
        assertEquals("SKU-9988", buffer.onTerminator(ts + 5L))
    }

    @Test
    fun hidBarcodeBuffer_rejectsShortNoiseAndBoundsLength() {
        val buffer = HidBarcodeScannerBuffer(minBarcodeLength = 4, maxBarcodeLength = 10)
        buffer.onPrintableChar('1', 100L)
        buffer.onPrintableChar('2', 110L)
        buffer.onPrintableChar('3', 120L)
        assertNull(buffer.onTerminator(130L))

        var ts = 500L
        for (i in 0 until 20) {
            buffer.onPrintableChar(('0'.code + (i % 10)).toChar(), ts)
            ts += 5L
        }
        val capped = buffer.onTerminator(ts + 5L)
        assertNotNull(capped)
        assertEquals(10, capped!!.length)
    }

    @Test
    fun catalogCsvCodec_roundTripsQuotedFieldsAndQuickSellFlag() {
        val categories = listOf(
            Category(id = 1, name = "Beverages, Cold"),
            Category(id = 2, name = "Snacks")
        )
        val products = listOf(
            Product(
                id = 10,
                name = "Coke \"Zero\" 1.5L, Cold",
                barcode = "4801981110110",
                categoryId = 1,
                price = 75.50,
                cost = 62.00,
                stockCount = 24,
                baseUom = "bottle",
                expirationDate = "2027-06-30",
                isQuickSellPinned = true
            ),
            Product(
                id = 11,
                name = "SkyFlakes Pack",
                barcode = "4800092110528",
                categoryId = 2,
                price = 68.00,
                cost = 55.00,
                stockCount = 15,
                baseUom = "pack",
                isQuickSellPinned = false
            )
        )

        val csv = CatalogCsvCodec.exportProductsToCsv(products, categories)
        // Parse against empty catalog -> both should be inserts
        val preview = CatalogCsvCodec.parseAndPreview("\uFEFF$csv", emptyList())
        assertEquals(2, preview.rowsToInsert.size)
        assertEquals(0, preview.rowsToUpdate.size)
        assertEquals(0, preview.skippedRows.size)

        val first = preview.rowsToInsert[0]
        assertEquals("Coke \"Zero\" 1.5L, Cold", first.name)
        assertEquals("Beverages, Cold", first.categoryName)
        assertEquals(75.50, first.price, 0.001)
        assertEquals(62.00, first.cost, 0.001)
        assertEquals(24, first.stockCount)
        assertEquals("bottle", first.baseUom)
        assertEquals("2027-06-30", first.expirationDate)
        assertTrue(first.isQuickSellPinned)
    }

    @Test
    fun catalogCsvCodec_classifiesInsertsUpdatesAndInvalidRows() {
        val existing = listOf(
            Product(id = 1, name = "Lucky Me Pancit Canton", barcode = "4807770270017", categoryId = 1, price = 15.0, stockCount = 20)
        )
        val csv = """
            product_name,sku,department,selling_price,unit_cost,qty,uom,expiry,pinned
            Lucky Me Pancit Canton,4807770270017,Noodles,16.50,12.00,50,pack,,yes
            Bear Brand 33g,4800361339100,Dairy,14.00,11.25,30,sachet,2027-01-01,1
            ,99999,Invalid,10.00,5.00,5,pc,,false
            Bad Price Item,88888,Misc,-5.00,2.00,5,pc,,false
            Bad Stock Item,77777,Misc,10.00,2.00,-3,pc,,false
        """.trimIndent()

        val preview = CatalogCsvCodec.parseAndPreview(csv, existing)
        assertEquals(1, preview.rowsToUpdate.size)
        assertEquals(1, preview.rowsToInsert.size)
        assertEquals(3, preview.skippedRows.size)
        assertEquals("Lucky Me Pancit Canton", preview.rowsToUpdate.first().name)
        assertEquals(16.50, preview.rowsToUpdate.first().price, 0.001)
        assertTrue(preview.rowsToUpdate.first().isQuickSellPinned)
        assertEquals("Bear Brand 33g", preview.rowsToInsert.first().name)
        assertTrue(preview.rowsToInsert.first().isQuickSellPinned)
    }

    @Test
    fun escPosXReadingAndSukiSlip_formatSanitizedThermalOutput() {
        val config = StoreConfig(
            id = 1,
            storeName = "Aling Nena Sari-Sari",
            currencySymbol = "₱",
            taxPercentage = 12.0
        )
        val summary = ShiftXReadingSummary(
            sessionId = 42,
            cashierUsername = "maria",
            shiftStartTime = 1_700_000_000_000L,
            generatedAt = 1_700_003_600_000L,
            startingCash = 500.0,
            transactionCount = 12,
            grossSales = 2450.0,
            totalDiscounts = 35.0,
            totalTax = 262.50,
            cashSales = 1500.0,
            gcashSales = 450.0,
            mayaSales = 200.0,
            cardSales = 0.0,
            sukiCreditSales = 300.0,
            otherDigitalSales = 0.0,
            drawerCashIn = 250.0,
            drawerPayouts = 100.0,
            cashRefunds = 50.0,
            expectedCashInDrawer = 2100.0
        )

        val xBytes58 = EscPosXReadingFormatter.buildMidShiftXReadingTicket(config, summary, is80mm = false)
        val xText = String(xBytes58, Charsets.US_ASCII)
        assertTrue(xText.contains("MID-SHIFT X-READING"))
        assertTrue(xText.contains("#SES-42"))
        assertTrue(xText.contains("P2100.00"))
        assertTrue(xText.contains("P300.00"))
        assertTrue(xText.contains("Shift remains ACTIVE."))

        val customer = CustomerAccount(id = 7, name = "Mang Jose", creditLimit = 2000.0, currentBalance = 200.0)
        val paymentEntry = CustomerCreditEntry(
            id = 99,
            customerId = 7,
            timestamp = 1_700_004_000_000L,
            actorUsername = "maria",
            type = "PAYMENT",
            amount = 300.0,
            balanceAfter = 200.0,
            paymentMethod = "CASH"
        )
        val slipBytes80 = EscPosXReadingFormatter.buildSukiPaymentSlipTicket(config, customer, paymentEntry, is80mm = true)
        val slipText = String(slipBytes80, Charsets.US_ASCII)
        assertTrue(slipText.contains("SUKI TAB PAYMENT RECEIPT"))
        assertTrue(slipText.contains("Mang Jose"))
        assertTrue(slipText.contains("P500.00"))
        assertTrue(slipText.contains("P300.00"))
        assertTrue(slipText.contains("P200.00"))
    }

    @Test
    fun parkedAndTransactionItems_preserveUomAndDiscountFields() {
        val parked = ParkedTransactionItem(
            parkedTransactionId = 1,
            productId = 5,
            productName = "Marlboro",
            price = 90.0,
            quantity = 3,
            uomName = "Pack (10s)",
            uomMultiplier = 10,
            discountAmount = 4.50
        )
        assertEquals("Pack (10s)", parked.uomName)
        assertEquals(10, parked.uomMultiplier)
        assertEquals(4.50, parked.discountAmount, 0.001)

        val txItem = TransactionItem(
            transactionId = 2,
            productId = 5,
            productName = "Marlboro (Pack (10s))",
            price = 85.50,
            quantity = 2,
            cost = 70.0,
            uomName = "Pack (10s)",
            discountAmount = 9.00
        )
        assertEquals(85.50, txItem.price, 0.001)
        assertEquals(9.00, txItem.discountAmount, 0.001)
    }
}
