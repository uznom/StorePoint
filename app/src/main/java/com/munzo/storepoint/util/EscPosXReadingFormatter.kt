package com.munzo.storepoint.util

import com.munzo.storepoint.data.CustomerAccount
import com.munzo.storepoint.data.CustomerCreditEntry
import com.munzo.storepoint.data.ShiftXReadingSummary
import com.munzo.storepoint.data.StoreConfig
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats ESC/POS thermal tickets for:
 *  - Mid-Shift X-Reading (non-resetting live shift totals)
 *  - Suki Tab (Customer Credit / Utang) Repayment Slip
 */
object EscPosXReadingFormatter {

    fun buildMidShiftXReadingTicket(
        storeConfig: StoreConfig,
        summary: ShiftXReadingSummary,
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
        val title = storeConfig.storeName.ifBlank { "STOREPOINT POS" }
        out.write("${EscPosHelper.sanitizeForEscPos(title, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write("MID-SHIFT X-READING\n".toByteArray(Charsets.US_ASCII))
        out.write("(NON-RESETTING PREVIEW)\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_BOLD_OFF)

        val divider = EscPosHelper.dividerLine(totalCols)
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_ALIGN_LEFT)
        out.write("Session:   #SES-${summary.sessionId}\n".toByteArray(Charsets.US_ASCII))
        out.write("Cashier:   ${EscPosHelper.sanitizeForEscPos(summary.cashierUsername, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write("Opened:    ${dateFormat.format(Date(summary.shiftStartTime))}\n".toByteArray(Charsets.US_ASCII))
        out.write("Printed:   ${dateFormat.format(Date(summary.generatedAt))}\n".toByteArray(Charsets.US_ASCII))
        out.write("Tx Count:  ${summary.transactionCount}\n".toByteArray(Charsets.US_ASCII))
        out.write(divider.toByteArray(Charsets.US_ASCII))

        writePair(out, "Gross Sales:", money(curr, summary.grossSales), totalCols)
        if (summary.totalDiscounts > 0.0) {
            writePair(out, "Line Discounts:", "-${money(curr, summary.totalDiscounts)}", totalCols)
        }
        writePair(out, "VAT Collected:", money(curr, summary.totalTax), totalCols)
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_BOLD_ON)
        out.write("TENDER BREAKDOWN\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_BOLD_OFF)
        writePair(out, " Cash Sales:", money(curr, summary.cashSales), totalCols)
        writePair(out, " GCash Sales:", money(curr, summary.gcashSales), totalCols)
        writePair(out, " Maya Sales:", money(curr, summary.mayaSales), totalCols)
        if (summary.cardSales > 0.0) {
            writePair(out, " Card Sales:", money(curr, summary.cardSales), totalCols)
        }
        if (summary.sukiCreditSales > 0.0) {
            writePair(out, " Suki Tab (Utang):", money(curr, summary.sukiCreditSales), totalCols)
        }
        if (summary.otherDigitalSales > 0.0) {
            writePair(out, " Other Digital:", money(curr, summary.otherDigitalSales), totalCols)
        }
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_BOLD_ON)
        out.write("DRAWER CASH ESTIMATE\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_BOLD_OFF)
        writePair(out, " Starting Float:", money(curr, summary.startingCash), totalCols)
        writePair(out, " (+) Cash Sales:", "+${money(curr, summary.cashSales)}", totalCols)
        if (summary.drawerCashIn > 0.0) {
            writePair(out, " (+) Cash In/Repay:", "+${money(curr, summary.drawerCashIn)}", totalCols)
        }
        if (summary.drawerPayouts > 0.0) {
            writePair(out, " (-) Payouts:", "-${money(curr, summary.drawerPayouts)}", totalCols)
        }
        if (summary.cashRefunds > 0.0) {
            writePair(out, " (-) Cash Refunds:", "-${money(curr, summary.cashRefunds)}", totalCols)
        }
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_BOLD_ON)
        writePair(out, "EXPECTED IN DRAWER:", money(curr, summary.expectedCashInDrawer), totalCols)
        out.write(EscPosHelper.CMD_BOLD_OFF)
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_ALIGN_CENTER)
        out.write("Shift remains ACTIVE.\n".toByteArray(Charsets.US_ASCII))
        finishPaper(out, hasAutoCutter)
        return out.toByteArray()
    }

    fun buildSukiPaymentSlipTicket(
        storeConfig: StoreConfig,
        customer: CustomerAccount,
        entry: CustomerCreditEntry,
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
        val title = storeConfig.storeName.ifBlank { "STOREPOINT POS" }
        out.write("${EscPosHelper.sanitizeForEscPos(title, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write("SUKI TAB PAYMENT RECEIPT\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_BOLD_OFF)

        val divider = EscPosHelper.dividerLine(totalCols)
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_ALIGN_LEFT)
        out.write("Ref #:    SUKI-${entry.id}\n".toByteArray(Charsets.US_ASCII))
        out.write("Date:     ${dateFormat.format(Date(entry.timestamp))}\n".toByteArray(Charsets.US_ASCII))
        out.write("Customer: ${EscPosHelper.sanitizeForEscPos(customer.name, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write("Cashier:  ${EscPosHelper.sanitizeForEscPos(entry.actorUsername, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write("Method:   ${EscPosHelper.sanitizeForEscPos(entry.paymentMethod, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write(divider.toByteArray(Charsets.US_ASCII))

        val prevBalance = (entry.balanceAfter + entry.amount).coerceAtLeast(0.0)
        writePair(out, "Previous Balance:", money(curr, prevBalance), totalCols)
        out.write(EscPosHelper.CMD_BOLD_ON)
        writePair(out, "Amount Paid:", money(curr, entry.amount), totalCols)
        writePair(out, "Remaining Balance:", money(curr, entry.balanceAfter), totalCols)
        out.write(EscPosHelper.CMD_BOLD_OFF)
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_ALIGN_CENTER)
        out.write("Salamat po, Suki!\n".toByteArray(Charsets.US_ASCII))
        finishPaper(out, hasAutoCutter)
        return out.toByteArray()
    }

    private fun money(curr: String, amount: Double): String =
        "$curr${String.format(Locale.US, "%.2f", amount)}"

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
