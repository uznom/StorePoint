package com.munzo.storepoint.util

import com.munzo.storepoint.data.DrawerTransaction
import com.munzo.storepoint.data.ShiftReconciliation
import com.munzo.storepoint.data.StoreConfig
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Formats ESC/POS byte payloads for drawer slips, hardware test tickets, shift Z-readings,
 * and dual drawer/wallet shift reconciliation reports.
 */
internal object EscPosShiftFormatter {

    fun buildDrawerSlipEscPos(
        storeConfig: StoreConfig,
        drawerTransaction: DrawerTransaction,
        balanceBefore: Double? = null,
        balanceAfter: Double? = null,
        is80mm: Boolean = false,
        kickDrawerOnPrint: Boolean = true,
        hasAutoCutter: Boolean = is80mm,
        customHeader: String? = null,
        storeTin: String? = null,
        printBarcode: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val totalCols = if (is80mm) 48 else 32
        val curr = EscPosHelper.safeCurrencySymbol(storeConfig.currencySymbol)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val storeName = EscPosHelper.sanitizeForEscPos(storeConfig.storeName, curr)
        val isCashIn = drawerTransaction.amount >= 0.0

        out.write(EscPosHelper.CMD_INIT)
        out.write(EscPosHelper.CMD_CODEPAGE_CP437)

        if (kickDrawerOnPrint) {
            out.write(EscPosHelper.CMD_DRAWER_KICK_PIN2)
            out.write(EscPosHelper.CMD_DRAWER_KICK_PIN5)
        }

        out.write(EscPosHelper.CMD_ALIGN_CENTER)
        out.write(EscPosHelper.CMD_FONT_LARGE)
        out.write(EscPosHelper.CMD_BOLD_ON)
        out.write("$storeName\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_FONT_NORMAL)
        out.write(EscPosHelper.CMD_BOLD_OFF)

        if (!storeTin.isNullOrBlank()) {
            val tinFormatted = if (storeTin.contains("TIN", ignoreCase = true)) storeTin else "VAT Reg. TIN: $storeTin"
            out.write("${EscPosHelper.sanitizeForEscPos(tinFormatted, curr)}\n".toByteArray(Charsets.US_ASCII))
        }
        if (!customHeader.isNullOrBlank()) {
            out.write("${EscPosHelper.sanitizeForEscPos(customHeader, curr)}\n".toByteArray(Charsets.US_ASCII))
        }

        out.write(EscPosHelper.CMD_BOLD_ON)
        val slipTitle = if (isCashIn) {
            "*** CASH-IN / FLOAT INSERTION ***\n"
        } else {
            "*** CASH-OUT / DRAWER PAYOUT ***\n"
        }
        out.write(slipTitle.toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_BOLD_OFF)
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_ALIGN_LEFT)
        out.write("Slip #: DRW-${drawerTransaction.id}\n".toByteArray(Charsets.US_ASCII))
        out.write("Session #: #SES-${drawerTransaction.sessionId}\n".toByteArray(Charsets.US_ASCII))
        out.write("Date: ${sdf.format(Date(drawerTransaction.timestamp))}\n".toByteArray(Charsets.US_ASCII))
        out.write("Cashier: ${EscPosHelper.sanitizeForEscPos(drawerTransaction.cashierUsername, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write("Action: ${EscPosHelper.sanitizeForEscPos(drawerTransaction.type, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write("Reason: ${EscPosHelper.sanitizeForEscPos(drawerTransaction.reason, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        val absAmount = abs(drawerTransaction.amount)
        val amountStr = "$curr${String.format(Locale.US, "%.2f", absAmount)}"
        val amountLabel = if (isCashIn) "CASH INFLOW:" else "CASH DISBURSED:"
        out.write(EscPosHelper.CMD_ALIGN_RIGHT)
        out.write(EscPosHelper.CMD_BOLD_ON)
        out.write(EscPosHelper.CMD_FONT_LARGE)
        out.write(EscPosHelper.padColumns(amountLabel, amountStr, totalCols).toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.LF)
        out.write(EscPosHelper.CMD_FONT_NORMAL)
        out.write(EscPosHelper.CMD_BOLD_OFF)

        if (balanceBefore != null && balanceAfter != null) {
            out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))
            out.write(EscPosHelper.padColumns("Drawer Before:", "$curr${String.format(Locale.US, "%.2f", balanceBefore)}", totalCols).toByteArray(Charsets.US_ASCII))
            out.write(EscPosHelper.LF)
            val signStr = if (isCashIn) "+$amountStr" else "-$amountStr"
            out.write(EscPosHelper.padColumns("Adjustment:", signStr, totalCols).toByteArray(Charsets.US_ASCII))
            out.write(EscPosHelper.LF)
            out.write(EscPosHelper.CMD_BOLD_ON)
            out.write(EscPosHelper.padColumns("New Drawer Balance:", "$curr${String.format(Locale.US, "%.2f", balanceAfter)}", totalCols).toByteArray(Charsets.US_ASCII))
            out.write(EscPosHelper.LF)
            out.write(EscPosHelper.CMD_BOLD_OFF)
        }

        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        if (printBarcode && drawerTransaction.id > 0) {
            out.write(EscPosReceiptFormatter.buildBarcodeEscPos("DRW-${drawerTransaction.id}", isCode128 = true, height = 45, width = 2))
            out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))
        }

        out.write(EscPosHelper.CMD_ALIGN_CENTER)
        out.write("Petty Cash / Drawer Audit Copy\n\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.padColumns("Cashier Signature", "Manager Signature", totalCols).toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.LF)
        out.write(EscPosHelper.padColumns("_________________", "_________________", totalCols).toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.LF)

        finishPaper(out, hasAutoCutter)
        return out.toByteArray()
    }

    fun buildTestTicket(
        is80mm: Boolean = false,
        kickDrawer: Boolean = false,
        hasAutoCutter: Boolean = is80mm
    ): ByteArray {
        val totalCols = if (is80mm) 48 else 32
        val out = ByteArrayOutputStream()
        out.write(EscPosHelper.CMD_INIT)
        out.write(EscPosHelper.CMD_CODEPAGE_CP437)
        if (kickDrawer) {
            out.write(EscPosHelper.CMD_DRAWER_KICK_PIN2)
            out.write(EscPosHelper.CMD_DRAWER_KICK_PIN5)
        }
        out.write(EscPosHelper.CMD_ALIGN_CENTER)
        out.write(EscPosHelper.CMD_BOLD_ON)
        out.write("=== STOREPOINT POS ===\n".toByteArray(Charsets.US_ASCII))
        out.write("HARDWARE PRINTER TEST\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_BOLD_OFF)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        out.write("Test Date: ${format.format(Date())}\n".toByteArray(Charsets.US_ASCII))
        out.write("Model: ${if (is80mm) "80mm Wide (48 Cols)" else "58mm Standard (XP-58 Plus)"}\n".toByteArray(Charsets.US_ASCII))
        out.write("Drawer Kick: ${if (kickDrawer) "ENABLED" else "DISABLED"}\n".toByteArray(Charsets.US_ASCII))
        out.write("Auto Cutter: ${if (hasAutoCutter) "ENABLED" else "DISABLED (Manual Tear)"}\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))
        out.write("ESC/POS Communication: OK\n".toByteArray(Charsets.US_ASCII))
        out.write("Thermal Head Status: OK\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))
        out.write("Ready for High-Speed Checkout\n".toByteArray(Charsets.US_ASCII))

        finishPaper(out, hasAutoCutter)
        return out.toByteArray()
    }

    fun buildShiftZReadingTicket(
        storeConfig: StoreConfig,
        cashierUsername: String,
        startTime: Long,
        endTime: Long,
        startingCash: Double,
        endingCash: Double,
        expectedCash: Double,
        totalSales: Double,
        cashSales: Double,
        nonCashSales: Double,
        vatableSales: Double,
        vatAmount: Double,
        payouts: Double,
        transactionCount: Int,
        is80mm: Boolean = false,
        hasAutoCutter: Boolean = is80mm
    ): ByteArray {
        val totalCols = if (is80mm) 48 else 32
        val out = ByteArrayOutputStream()
        val curr = EscPosHelper.safeCurrencySymbol(storeConfig.currencySymbol)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        out.write(EscPosHelper.CMD_INIT)
        out.write(EscPosHelper.CMD_CODEPAGE_CP437)
        out.write(EscPosHelper.CMD_ALIGN_CENTER)
        out.write(EscPosHelper.CMD_BOLD_ON)
        val title = if (storeConfig.storeName.isNotBlank()) EscPosHelper.sanitizeForEscPos(storeConfig.storeName, curr) else "STOREPOINT POS"
        out.write("$title\n".toByteArray(Charsets.US_ASCII))
        out.write("END OF DAY / Z-READING\n".toByteArray(Charsets.US_ASCII))
        out.write("SHIFT CLOSURE REPORT\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_BOLD_OFF)

        val divider = EscPosHelper.dividerLine(totalCols)
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_ALIGN_LEFT)
        out.write("Cashier: ${EscPosHelper.sanitizeForEscPos(cashierUsername, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write("Shift Start: ${dateFormat.format(Date(startTime))}\n".toByteArray(Charsets.US_ASCII))
        out.write("Shift End:   ${dateFormat.format(Date(endTime))}\n".toByteArray(Charsets.US_ASCII))
        out.write("Total Transactions: $transactionCount\n".toByteArray(Charsets.US_ASCII))
        out.write(divider.toByteArray(Charsets.US_ASCII))

        writePair(out, "Starting Float:", "$curr${String.format(Locale.US, "%.2f", startingCash)}", totalCols)
        writePair(out, "Gross Sales:", "$curr${String.format(Locale.US, "%.2f", totalSales)}", totalCols)
        writePair(out, " - Cash Sales:", "$curr${String.format(Locale.US, "%.2f", cashSales)}", totalCols)
        writePair(out, " - Non-Cash / Digital:", "$curr${String.format(Locale.US, "%.2f", nonCashSales)}", totalCols)
        writePair(out, "VATable Sales (Net):", "$curr${String.format(Locale.US, "%.2f", vatableSales)}", totalCols)
        writePair(out, "12% VAT Collected:", "$curr${String.format(Locale.US, "%.2f", vatAmount)}", totalCols)
        if (payouts != 0.0) {
            writePair(out, "Supplier Payouts:", "$curr${String.format(Locale.US, "%.2f", payouts)}", totalCols)
        }
        out.write(divider.toByteArray(Charsets.US_ASCII))

        writePair(out, "Expected Drawer Cash:", "$curr${String.format(Locale.US, "%.2f", expectedCash)}", totalCols)
        writePair(out, "Counted Drawer Cash:", "$curr${String.format(Locale.US, "%.2f", endingCash)}", totalCols)
        val diff = endingCash - expectedCash
        val diffStr = when {
            abs(diff) < 0.01 -> "BALANCED"
            diff < 0 -> "SHORT: -$curr${String.format(Locale.US, "%.2f", -diff)}"
            else -> "OVER: +$curr${String.format(Locale.US, "%.2f", diff)}"
        }
        out.write(EscPosHelper.CMD_BOLD_ON)
        writePair(out, "Drawer Variance:", diffStr, totalCols)
        out.write(EscPosHelper.CMD_BOLD_OFF)
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_ALIGN_CENTER)
        out.write("End of Day Close Completed\n".toByteArray(Charsets.US_ASCII))

        finishPaper(out, hasAutoCutter)
        return out.toByteArray()
    }

    fun buildShiftReconciliationReceipt(
        storeConfig: StoreConfig,
        reconciliation: ShiftReconciliation,
        startTime: Long,
        formulaSummary: EscPosHelper.DrawerBalancingSummary? = null,
        is80mm: Boolean = false,
        hasAutoCutter: Boolean = is80mm,
        printBarcode: Boolean = true
    ): ByteArray {
        val totalCols = if (is80mm) 48 else 32
        val out = ByteArrayOutputStream()
        val curr = EscPosHelper.safeCurrencySymbol(storeConfig.currencySymbol)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        out.write(EscPosHelper.CMD_INIT)
        out.write(EscPosHelper.CMD_CODEPAGE_CP437)
        out.write(EscPosHelper.CMD_ALIGN_CENTER)
        out.write(EscPosHelper.CMD_BOLD_ON)
        val title = if (storeConfig.storeName.isNotBlank()) EscPosHelper.sanitizeForEscPos(storeConfig.storeName, curr) else "STOREPOINT POS"
        out.write("$title\n".toByteArray(Charsets.US_ASCII))
        out.write("SHIFT HANDOVER RECONCILIATION\n".toByteArray(Charsets.US_ASCII))
        out.write("DUAL DRAWER & WALLET BALANCING\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_BOLD_OFF)

        val divider = EscPosHelper.dividerLine(totalCols)
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_ALIGN_LEFT)
        out.write("Cashier:     ${EscPosHelper.sanitizeForEscPos(reconciliation.cashierUsername, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write("Session ID:  #SES-${reconciliation.sessionId}\n".toByteArray(Charsets.US_ASCII))
        out.write("Shift Start: ${dateFormat.format(Date(startTime))}\n".toByteArray(Charsets.US_ASCII))
        out.write("Reconciled:  ${dateFormat.format(Date(reconciliation.timestamp))}\n".toByteArray(Charsets.US_ASCII))
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_BOLD_ON)
        out.write("CASH DRAWER COUNT BREAKDOWN\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_BOLD_OFF)
        writePair(out, " 1,000 x ${reconciliation.count1000}:", "$curr${String.format(Locale.US, "%.2f", reconciliation.count1000 * 1000.0)}", totalCols)
        writePair(out, "   500 x ${reconciliation.count500}:", "$curr${String.format(Locale.US, "%.2f", reconciliation.count500 * 500.0)}", totalCols)
        writePair(out, "   200 x ${reconciliation.count200}:", "$curr${String.format(Locale.US, "%.2f", reconciliation.count200 * 200.0)}", totalCols)
        writePair(out, "   100 x ${reconciliation.count100}:", "$curr${String.format(Locale.US, "%.2f", reconciliation.count100 * 100.0)}", totalCols)
        writePair(out, "    50 x ${reconciliation.count50}:", "$curr${String.format(Locale.US, "%.2f", reconciliation.count50 * 50.0)}", totalCols)
        writePair(out, "    20 x ${reconciliation.count20}:", "$curr${String.format(Locale.US, "%.2f", reconciliation.count20 * 20.0)}", totalCols)
        writePair(out, " Coins & Loose:", "$curr${String.format(Locale.US, "%.2f", reconciliation.countCoins)}", totalCols)
        out.write(divider.toByteArray(Charsets.US_ASCII))

        if (formulaSummary != null) {
            out.write(EscPosHelper.CMD_BOLD_ON)
            out.write("DRAWER FORMULA BREAKDOWN\n".toByteArray(Charsets.US_ASCII))
            out.write(EscPosHelper.CMD_BOLD_OFF)
            writePair(out, " Starting Float:", "$curr${String.format(Locale.US, "%.2f", formulaSummary.startingFloat)}", totalCols)
            writePair(out, " (+) Cash Sales:", "+$curr${String.format(Locale.US, "%.2f", formulaSummary.cashSales)}", totalCols)
            writePair(out, " (+) Cash In:", "+$curr${String.format(Locale.US, "%.2f", formulaSummary.cashIn)}", totalCols)
            writePair(out, " (-) Cash Out:", "-$curr${String.format(Locale.US, "%.2f", formulaSummary.cashOut)}", totalCols)
            writePair(out, " (-) Cash Refunds:", "-$curr${String.format(Locale.US, "%.2f", formulaSummary.cashRefunds)}", totalCols)
            out.write(divider.toByteArray(Charsets.US_ASCII))
        }

        writePair(out, "Total Counted Cash:", "$curr${String.format(Locale.US, "%.2f", reconciliation.totalCountedCash)}", totalCols)
        writePair(out, "Expected Cash Ledger:", "$curr${String.format(Locale.US, "%.2f", reconciliation.expectedCash)}", totalCols)
        val cashVarStr = when {
            abs(reconciliation.cashVariance) < 0.01 -> "BALANCED"
            reconciliation.cashVariance < 0 -> "SHORT -$curr${String.format(Locale.US, "%.2f", -reconciliation.cashVariance)}"
            else -> "OVER +$curr${String.format(Locale.US, "%.2f", reconciliation.cashVariance)}"
        }
        out.write(EscPosHelper.CMD_BOLD_ON)
        writePair(out, "Cash Variance:", cashVarStr, totalCols)
        out.write(EscPosHelper.CMD_BOLD_OFF)
        out.write(divider.toByteArray(Charsets.US_ASCII))

        if (storeConfig.hasGCash) {
            out.write(EscPosHelper.CMD_BOLD_ON)
            out.write("GCASH DIGITAL WALLET\n".toByteArray(Charsets.US_ASCII))
            out.write(EscPosHelper.CMD_BOLD_OFF)
            writePair(out, " Counted App Balance:", "$curr${String.format(Locale.US, "%.2f", reconciliation.countedGCash)}", totalCols)
            writePair(out, " POS Ledger Balance:", "$curr${String.format(Locale.US, "%.2f", reconciliation.expectedGCash)}", totalCols)
            val gcashVarStr = when {
                abs(reconciliation.gcashVariance) < 0.01 -> "BALANCED"
                reconciliation.gcashVariance < 0 -> "SHORT -$curr${String.format(Locale.US, "%.2f", -reconciliation.gcashVariance)}"
                else -> "OVER +$curr${String.format(Locale.US, "%.2f", reconciliation.gcashVariance)}"
            }
            writePair(out, " GCash Variance:", gcashVarStr, totalCols)
            out.write(divider.toByteArray(Charsets.US_ASCII))
        }

        if (storeConfig.hasLoad) {
            out.write(EscPosHelper.CMD_BOLD_ON)
            out.write("PREPAID MOBILE LOAD\n".toByteArray(Charsets.US_ASCII))
            out.write(EscPosHelper.CMD_BOLD_OFF)
            writePair(
                out,
                " Smart Counted/Expected:",
                "${String.format(Locale.US, "%.0f", reconciliation.countedSmartLoad)} / ${String.format(Locale.US, "%.0f", reconciliation.expectedSmartLoad)}",
                totalCols
            )
            writePair(
                out,
                " Globe Counted/Expected:",
                "${String.format(Locale.US, "%.0f", reconciliation.countedGlobeLoad)} / ${String.format(Locale.US, "%.0f", reconciliation.expectedGlobeLoad)}",
                totalCols
            )
            out.write(divider.toByteArray(Charsets.US_ASCII))
        }

        if (reconciliation.hasDiscrepancy || reconciliation.managerOverrideApproved) {
            out.write(EscPosHelper.CMD_BOLD_ON)
            out.write("DISCREPANCY OVERRIDE AUDIT\n".toByteArray(Charsets.US_ASCII))
            out.write(EscPosHelper.CMD_BOLD_OFF)
            out.write("Approved by: ${EscPosHelper.sanitizeForEscPos(reconciliation.managerUsername, curr)}\n".toByteArray(Charsets.US_ASCII))
            val cleanReason = EscPosHelper.sanitizeForEscPos(reconciliation.discrepancyReason.take(60), curr)
            out.write("Reason: $cleanReason\n".toByteArray(Charsets.US_ASCII))
            out.write(divider.toByteArray(Charsets.US_ASCII))
        }

        if (printBarcode && reconciliation.sessionId > 0) {
            out.write(EscPosReceiptFormatter.buildBarcodeEscPos("SES-${reconciliation.sessionId}", isCode128 = true, height = 45, width = 2))
            out.write(divider.toByteArray(Charsets.US_ASCII))
        }

        out.write(EscPosHelper.LF)
        writePair(out, "Cashier Signature", "Manager Signature", totalCols)
        writePair(out, "_________________", "_________________", totalCols)

        finishPaper(out, hasAutoCutter)
        return out.toByteArray()
    }

    private fun writePair(out: ByteArrayOutputStream, left: String, right: String, totalCols: Int) {
        out.write(EscPosHelper.padColumns(left, right, totalCols).toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.LF)
    }

    private fun finishPaper(out: ByteArrayOutputStream, hasAutoCutter: Boolean) {
        if (hasAutoCutter) {
            out.write("\n\n".toByteArray(Charsets.US_ASCII))
            out.write(EscPosHelper.CMD_PAPER_CUT)
        } else {
            out.write(EscPosHelper.CMD_FEED_LINES)
            out.write("\n\n".toByteArray(Charsets.US_ASCII))
        }
    }
}
