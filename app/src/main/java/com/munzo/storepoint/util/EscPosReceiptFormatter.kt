package com.munzo.storepoint.util

import com.munzo.storepoint.data.ReturnItem
import com.munzo.storepoint.data.ReturnTransaction
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats ESC/POS byte payloads for 1D barcodes, official sales receipts, and refund vouchers.
 */
internal object EscPosReceiptFormatter {

    fun buildBarcodeEscPos(
        data: String,
        isCode128: Boolean = true,
        height: Int = 50,
        width: Int = 2
    ): ByteArray {
        val clean = data.trim().filter { it.isLetterOrDigit() || it == '-' }
        if (clean.isBlank()) return byteArrayOf()

        val out = ByteArrayOutputStream()
        out.write(EscPosHelper.CMD_ALIGN_CENTER)
        out.write(EscPosHelper.CMD_BARCODE_HRI_BELOW)
        out.write(EscPosHelper.CMD_BARCODE_FONT_A)
        out.write(byteArrayOf(0x1D, 0x68, height.coerceIn(20, 255).toByte()))
        out.write(byteArrayOf(0x1D, 0x77, width.coerceIn(1, 4).toByte()))

        if (isCode128) {
            val asciiBytes = clean.toByteArray(Charsets.US_ASCII)
            val payload = ByteArrayOutputStream()
            payload.write(0x7B) // '{'
            payload.write(0x42) // 'B'
            payload.write(asciiBytes)
            val bytes = payload.toByteArray()

            out.write(byteArrayOf(0x1D, 0x6B, 73, bytes.size.toByte()))
            out.write(bytes)
        } else {
            val asciiBytes = clean.uppercase(Locale.US).toByteArray(Charsets.US_ASCII)
            out.write(byteArrayOf(0x1D, 0x6B, 69, asciiBytes.size.toByte()))
            out.write(asciiBytes)
        }
        out.write(EscPosHelper.LF)
        return out.toByteArray()
    }

    fun buildReceiptEscPos(
        storeConfig: StoreConfig,
        transaction: Transaction,
        items: List<TransactionItem>,
        is80mm: Boolean = false,
        kickDrawerOnPrint: Boolean = true,
        hasAutoCutter: Boolean = is80mm,
        customHeader: String? = null,
        customFooter: String? = null,
        storeTin: String? = null,
        storeAddress: String? = null,
        printBarcode: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val totalCols = if (is80mm) 48 else 32
        val curr = EscPosHelper.safeCurrencySymbol(storeConfig.currencySymbol)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val storeName = EscPosHelper.sanitizeForEscPos(storeConfig.storeName, curr)

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

        if (!storeAddress.isNullOrBlank()) {
            out.write("${EscPosHelper.sanitizeForEscPos(storeAddress, curr)}\n".toByteArray(Charsets.US_ASCII))
        }
        if (!storeTin.isNullOrBlank()) {
            val tinStr = if (storeTin.contains("TIN", ignoreCase = true)) storeTin else "VAT Reg. TIN: $storeTin"
            out.write("${EscPosHelper.sanitizeForEscPos(tinStr, curr)}\n".toByteArray(Charsets.US_ASCII))
        }
        if (!customHeader.isNullOrBlank()) {
            out.write("${EscPosHelper.sanitizeForEscPos(customHeader, curr)}\n".toByteArray(Charsets.US_ASCII))
        }
        out.write("Official POS Sales Receipt\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_ALIGN_LEFT)
        out.write("Receipt #: ${transaction.id}\n".toByteArray(Charsets.US_ASCII))
        out.write("Date: ${sdf.format(Date(transaction.timestamp))}\n".toByteArray(Charsets.US_ASCII))
        out.write("Cashier: ${EscPosHelper.sanitizeForEscPos(transaction.cashierUsername, curr)}\n".toByteArray(Charsets.US_ASCII))
        if (transaction.customerName.isNotBlank()) {
            out.write("Customer: ${EscPosHelper.sanitizeForEscPos(transaction.customerName, curr)}\n".toByteArray(Charsets.US_ASCII))
        }
        out.write("Payment: ${EscPosHelper.sanitizeForEscPos(transaction.paymentMethod, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        val colHeader = if (is80mm) {
            EscPosHelper.padColumns("ITEM DESCRIPTION", "QTY", "PRICE", "TOTAL", totalCols)
        } else {
            EscPosHelper.padColumns("ITEM", "QTY", "AMT", totalCols)
        }
        out.write(EscPosHelper.CMD_BOLD_ON)
        out.write("$colHeader\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_BOLD_OFF)
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        items.forEach { item ->
            val lineTotalStr = String.format(Locale.US, "%.2f", item.price * item.quantity)
            val priceStr = String.format(Locale.US, "%.2f", item.price)
            val cleanName = EscPosHelper.sanitizeForEscPos(item.productName, curr)
            if (is80mm) {
                val row = EscPosHelper.padColumns(cleanName, item.quantity.toString(), priceStr, lineTotalStr, totalCols)
                out.write("$row\n".toByteArray(Charsets.US_ASCII))
            } else {
                val truncatedName = if (cleanName.length > 30) cleanName.take(28) + ".." else cleanName
                out.write("$truncatedName\n".toByteArray(Charsets.US_ASCII))
                val detail = "  ${item.quantity} x $priceStr"
                val row = EscPosHelper.padColumns(detail, lineTotalStr, totalCols)
                out.write("$row\n".toByteArray(Charsets.US_ASCII))
            }
        }
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_ALIGN_RIGHT)
        val subtotalStr = "VATable Sales: $curr${String.format(Locale.US, "%.2f", transaction.subtotal)}"
        out.write("$subtotalStr\n".toByteArray(Charsets.US_ASCII))

        if (transaction.taxAmount > 0.0) {
            val taxStr = "12% VAT (Inc.): $curr${String.format(Locale.US, "%.2f", transaction.taxAmount)}"
            out.write("$taxStr\n".toByteArray(Charsets.US_ASCII))
        }

        out.write(EscPosHelper.CMD_BOLD_ON)
        val totalStr = "TOTAL AMOUNT: $curr${String.format(Locale.US, "%.2f", transaction.totalAmount)}"
        out.write("$totalStr\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_BOLD_OFF)

        val paidStr = "Amount Tendered: $curr${String.format(Locale.US, "%.2f", transaction.cashPaid)}"
        out.write("$paidStr\n".toByteArray(Charsets.US_ASCII))

        val changeStr = "Change: $curr${String.format(Locale.US, "%.2f", transaction.changeAmount)}"
        out.write("$changeStr\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        if (printBarcode && transaction.id > 0) {
            out.write(buildBarcodeEscPos("TX-${transaction.id}", isCode128 = true, height = 45, width = 2))
            out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))
        }

        out.write(EscPosHelper.CMD_ALIGN_CENTER)
        if (!customFooter.isNullOrBlank()) {
            out.write("${EscPosHelper.sanitizeForEscPos(customFooter, curr)}\n".toByteArray(Charsets.US_ASCII))
        } else {
            out.write("Thank you for your business!\n".toByteArray(Charsets.US_ASCII))
            out.write("Please come again.\n".toByteArray(Charsets.US_ASCII))
        }

        if (hasAutoCutter) {
            out.write("\n\n".toByteArray(Charsets.US_ASCII))
            out.write(EscPosHelper.CMD_PAPER_CUT)
        } else {
            out.write(EscPosHelper.CMD_FEED_LINES)
            out.write("\n\n".toByteArray(Charsets.US_ASCII))
        }

        return out.toByteArray()
    }

    fun buildRefundReceiptEscPos(
        storeConfig: StoreConfig,
        returnTransaction: ReturnTransaction,
        items: List<ReturnItem>,
        is80mm: Boolean = false,
        kickDrawerOnPrint: Boolean = false,
        hasAutoCutter: Boolean = is80mm,
        customHeader: String? = null,
        customFooter: String? = null,
        storeTin: String? = null,
        storeAddress: String? = null,
        printBarcode: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val totalCols = if (is80mm) 48 else 32
        val curr = EscPosHelper.safeCurrencySymbol(storeConfig.currencySymbol)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val storeName = EscPosHelper.sanitizeForEscPos(storeConfig.storeName, curr)

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

        if (!storeAddress.isNullOrBlank()) {
            out.write("${EscPosHelper.sanitizeForEscPos(storeAddress, curr)}\n".toByteArray(Charsets.US_ASCII))
        }
        if (!storeTin.isNullOrBlank()) {
            val tinStr = if (storeTin.contains("TIN", ignoreCase = true)) storeTin else "VAT Reg. TIN: $storeTin"
            out.write("${EscPosHelper.sanitizeForEscPos(tinStr, curr)}\n".toByteArray(Charsets.US_ASCII))
        }
        if (!customHeader.isNullOrBlank()) {
            out.write("${EscPosHelper.sanitizeForEscPos(customHeader, curr)}\n".toByteArray(Charsets.US_ASCII))
        }

        out.write("*** RETURN / REFUND VOUCHER ***\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        out.write(EscPosHelper.CMD_ALIGN_LEFT)
        out.write("Return Slip #: ${returnTransaction.id}\n".toByteArray(Charsets.US_ASCII))
        out.write("Original Sale #: ${returnTransaction.originalTransactionId}\n".toByteArray(Charsets.US_ASCII))
        out.write("Date: ${sdf.format(Date(returnTransaction.timestamp))}\n".toByteArray(Charsets.US_ASCII))
        out.write("Cashier: ${EscPosHelper.sanitizeForEscPos(returnTransaction.cashierUsername, curr)}\n".toByteArray(Charsets.US_ASCII))
        if (returnTransaction.customerName.isNotBlank()) {
            out.write("Customer: ${EscPosHelper.sanitizeForEscPos(returnTransaction.customerName, curr)}\n".toByteArray(Charsets.US_ASCII))
        }
        out.write("Refund Tender: ${EscPosHelper.sanitizeForEscPos(returnTransaction.refundMethod, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write("Return Reason: ${EscPosHelper.sanitizeForEscPos(returnTransaction.returnReason, curr)}\n".toByteArray(Charsets.US_ASCII))
        if (returnTransaction.notes.isNotBlank()) {
            out.write("Notes: ${EscPosHelper.sanitizeForEscPos(returnTransaction.notes, curr)}\n".toByteArray(Charsets.US_ASCII))
        }
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        val colHeader = if (is80mm) {
            EscPosHelper.padColumns("RETURNED ITEM", "QTY", "UNIT", "REFUND", totalCols)
        } else {
            EscPosHelper.padColumns("ITEM", "QTY", "REFUND", totalCols)
        }
        out.write(EscPosHelper.CMD_BOLD_ON)
        out.write("$colHeader\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_BOLD_OFF)
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        items.forEach { item ->
            val sanitizedName = EscPosHelper.sanitizeForEscPos(item.productName, curr)
            val conditionStr = if (item.restockToInventory) "[Restocked]" else "[Damaged/Write-Off]"
            val unitStr = "$curr${String.format(Locale.US, "%.2f", item.unitPrice)}"
            val refundStr = "$curr${String.format(Locale.US, "%.2f", item.refundAmount)}"

            if (is80mm) {
                val line = EscPosHelper.padColumns(sanitizedName, "${item.quantity}", unitStr, refundStr, totalCols)
                out.write("$line\n".toByteArray(Charsets.US_ASCII))
                out.write("  $conditionStr\n".toByteArray(Charsets.US_ASCII))
            } else {
                if (sanitizedName.length > 14) {
                    out.write("$sanitizedName\n".toByteArray(Charsets.US_ASCII))
                    val subLine = EscPosHelper.padColumns("  $conditionStr", "${item.quantity}", refundStr, totalCols)
                    out.write("$subLine\n".toByteArray(Charsets.US_ASCII))
                } else {
                    val line = EscPosHelper.padColumns(sanitizedName, "${item.quantity}", refundStr, totalCols)
                    out.write("$line\n".toByteArray(Charsets.US_ASCII))
                    out.write("  $conditionStr\n".toByteArray(Charsets.US_ASCII))
                }
            }
        }

        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        val totalRefundStr = "$curr${String.format(Locale.US, "%.2f", returnTransaction.totalRefundAmount)}"
        val totalLine = EscPosHelper.padColumns("TOTAL REFUNDED:", totalRefundStr, totalCols)
        out.write(EscPosHelper.CMD_BOLD_ON)
        out.write(EscPosHelper.CMD_FONT_LARGE)
        out.write("$totalLine\n".toByteArray(Charsets.US_ASCII))
        out.write(EscPosHelper.CMD_FONT_NORMAL)
        out.write(EscPosHelper.CMD_BOLD_OFF)
        out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        if (printBarcode && returnTransaction.id > 0) {
            out.write(buildBarcodeEscPos("REF-${returnTransaction.id}", isCode128 = true, height = 45, width = 2))
            out.write(EscPosHelper.dividerLine(totalCols).toByteArray(Charsets.US_ASCII))
        }

        out.write(EscPosHelper.CMD_ALIGN_CENTER)
        if (!customFooter.isNullOrBlank()) {
            out.write("${EscPosHelper.sanitizeForEscPos(customFooter, curr)}\n".toByteArray(Charsets.US_ASCII))
        } else {
            out.write("REFUND PROCESSED AT CASHIER REGISTER\n".toByteArray(Charsets.US_ASCII))
            out.write("Authorized Supervisor Audit Copy\n".toByteArray(Charsets.US_ASCII))
        }
        out.write("\n".toByteArray(Charsets.US_ASCII))

        if (hasAutoCutter) {
            out.write(EscPosHelper.CMD_PAPER_CUT)
        } else {
            out.write(EscPosHelper.CMD_FEED_LINES)
            out.write("\n\n".toByteArray(Charsets.US_ASCII))
        }

        return out.toByteArray()
    }
}
