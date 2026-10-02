package com.munzo.storepoint.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Universal ESC/POS helper supporting standard thermal receipt printers,
 * including 58mm models like Xprinter XP-58 Plus, XP-58IIH, POS-58, and 80mm printers,
 * matching Loyverse-grade peripheral compatibility.
 */
object EscPosHelper {

    enum class PrinterType {
        SYSTEM_SPOOLER,
        NETWORK_ESCPOS,
        BLUETOOTH_ESCPOS,
        USB_ESCPOS
    }

    // Standard Bluetooth Serial Port Profile (SPP) UUID
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Command Constants
    val CMD_INIT = byteArrayOf(0x1B, 0x40) // ESC @
    val CMD_CODEPAGE_CP437 = byteArrayOf(0x1B, 0x74, 0x00) // ESC t 0 (Standard CP437 ASCII table)
    val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
    val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
    val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2
    val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
    val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
    val CMD_FONT_LARGE = byteArrayOf(0x1D, 0x21, 0x11) // GS ! 0x11 (Double Width & Height)
    val CMD_FONT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00) // GS ! 0x00
    val CMD_DRAWER_KICK_PIN2 = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()) // ESC p 0 25 250 (12V RJ11 pin 2)
    val CMD_DRAWER_KICK_PIN5 = byteArrayOf(0x1B, 0x70, 0x01, 0x19, 0xFA.toByte()) // ESC p 1 25 250 (12V RJ11 pin 5)
    val CMD_PAPER_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V 66 0 (Feed and full cut)
    val CMD_FEED_LINES = byteArrayOf(0x1B, 0x64, 0x05) // ESC d 5 (Feed 5 lines for manual tear bar)
    val LF = byteArrayOf(0x0A) // Line Feed

    data class DiscoveredBluetoothPrinter(
        val name: String,
        val macAddress: String,
        val isLikelyPosPrinter: Boolean
    )

    /**
     * Sanitizes strings for thermal printers (XP-58 Plus and 58mm/80mm):
     * - Converts multi-byte currency symbols (e.g. Philippine Peso '₱') to 'P' so CP437 thermal
     *   controllers do not print 'â‚±' garbled glyphs and do not push columns out of alignment.
     * - Replaces curly quotes, non-breaking spaces, and typographic dashes with pure ASCII.
     */
    fun sanitizeForEscPos(text: String, currencySymbol: String = "P"): String {
        var clean = text
        if (clean.contains("₱") || currencySymbol == "₱") {
            clean = clean.replace("₱", "P")
        }
        return clean
            .replace('\u00A0', ' ')
            .replace('“', '"').replace('”', '"')
            .replace('‘', '\'').replace('’', '\'')
            .replace('—', '-').replace('–', '-')
    }

    /**
     * Returns a safe single-width ASCII representation of the currency symbol.
     */
    fun safeCurrencySymbol(symbol: String): String {
        return if (symbol.isBlank() || symbol == "₱") "P" else symbol.trim()
    }

    /**
     * Builds standard ESC/POS formatted bytes for cash drawer kick.
     */
    fun buildDrawerKickBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(CMD_INIT)
        out.write(CMD_DRAWER_KICK_PIN2)
        out.write(CMD_DRAWER_KICK_PIN5)
        return out.toByteArray()
    }

    /**
     * Builds a complete, formatted ESC/POS receipt for thermal printers (58mm XP-58 or 80mm width).
     * Automatically handles manual tear-bar feeding without cut-code corruption for XP-58 Plus.
     */
    fun buildReceiptEscPos(
        storeConfig: StoreConfig,
        transaction: Transaction,
        items: List<TransactionItem>,
        is80mm: Boolean = false,
        kickDrawerOnPrint: Boolean = true,
        hasAutoCutter: Boolean = is80mm
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val totalCols = if (is80mm) 48 else 32
        val curr = safeCurrencySymbol(storeConfig.currencySymbol)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val storeName = sanitizeForEscPos(storeConfig.storeName, curr)

        // Initialize Printer & Select Standard Code Page (CP437)
        out.write(CMD_INIT)
        out.write(CMD_CODEPAGE_CP437)

        // Cash Drawer Kick Pulse (if enabled)
        if (kickDrawerOnPrint) {
            out.write(CMD_DRAWER_KICK_PIN2)
            out.write(CMD_DRAWER_KICK_PIN5)
        }

        // Header: Store Name (Centered, Large, Bold)
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_FONT_LARGE)
        out.write(CMD_BOLD_ON)
        out.write("$storeName\n".toByteArray(Charsets.US_ASCII))
        out.write(CMD_FONT_NORMAL)
        out.write(CMD_BOLD_OFF)
        out.write("Official POS Sales Receipt\n".toByteArray(Charsets.US_ASCII))
        out.write(dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        // Transaction Meta (Left Aligned)
        out.write(CMD_ALIGN_LEFT)
        out.write("Receipt #: ${transaction.id}\n".toByteArray(Charsets.US_ASCII))
        out.write("Date: ${sdf.format(Date(transaction.timestamp))}\n".toByteArray(Charsets.US_ASCII))
        out.write("Cashier: ${sanitizeForEscPos(transaction.cashierUsername, curr)}\n".toByteArray(Charsets.US_ASCII))
        if (transaction.customerName.isNotBlank()) {
            out.write("Customer: ${sanitizeForEscPos(transaction.customerName, curr)}\n".toByteArray(Charsets.US_ASCII))
        }
        out.write("Payment: ${sanitizeForEscPos(transaction.paymentMethod, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write(dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        // Column Header
        val colHeader = if (is80mm) {
            padColumns("ITEM DESCRIPTION", "QTY", "PRICE", "TOTAL", totalCols)
        } else {
            padColumns("ITEM", "QTY", "AMT", totalCols)
        }
        out.write(CMD_BOLD_ON)
        out.write("$colHeader\n".toByteArray(Charsets.US_ASCII))
        out.write(CMD_BOLD_OFF)
        out.write(dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        // Items
        items.forEach { item ->
            val lineTotalStr = String.format(Locale.US, "%.2f", item.price * item.quantity)
            val priceStr = String.format(Locale.US, "%.2f", item.price)
            val cleanName = sanitizeForEscPos(item.productName, curr)
            if (is80mm) {
                val row = padColumns(cleanName, item.quantity.toString(), priceStr, lineTotalStr, totalCols)
                out.write("$row\n".toByteArray(Charsets.US_ASCII))
            } else {
                // 32-col layout tailored for XP-58 Plus: Name on line 1, Qty x Price and Line Total on line 2
                val truncatedName = if (cleanName.length > 30) cleanName.take(28) + ".." else cleanName
                out.write("$truncatedName\n".toByteArray(Charsets.US_ASCII))
                val detail = "  ${item.quantity} x $priceStr"
                val row = padColumns(detail, lineTotalStr, totalCols)
                out.write("$row\n".toByteArray(Charsets.US_ASCII))
            }
        }
        out.write(dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        // Totals (Right Aligned - Philippine VAT standard)
        out.write(CMD_ALIGN_RIGHT)
        val subtotalStr = "VATable Sales: $curr${String.format(Locale.US, "%.2f", transaction.subtotal)}"
        out.write("$subtotalStr\n".toByteArray(Charsets.US_ASCII))

        if (transaction.taxAmount > 0.0) {
            val taxStr = "12% VAT (Inc.): $curr${String.format(Locale.US, "%.2f", transaction.taxAmount)}"
            out.write("$taxStr\n".toByteArray(Charsets.US_ASCII))
        }

        out.write(CMD_BOLD_ON)
        val totalStr = "TOTAL AMOUNT: $curr${String.format(Locale.US, "%.2f", transaction.totalAmount)}"
        out.write("$totalStr\n".toByteArray(Charsets.US_ASCII))
        out.write(CMD_BOLD_OFF)

        val paidStr = "Amount Tendered: $curr${String.format(Locale.US, "%.2f", transaction.cashPaid)}"
        out.write("$paidStr\n".toByteArray(Charsets.US_ASCII))

        val changeStr = "Change: $curr${String.format(Locale.US, "%.2f", transaction.changeAmount)}"
        out.write("$changeStr\n".toByteArray(Charsets.US_ASCII))
        out.write(dividerLine(totalCols).toByteArray(Charsets.US_ASCII))

        // Footer (Centered)
        out.write(CMD_ALIGN_CENTER)
        out.write("Thank you for your business!\n".toByteArray(Charsets.US_ASCII))
        out.write("Please come again.\n".toByteArray(Charsets.US_ASCII))

        // Paper Finish: Cut if auto-cutter is present; otherwise feed lines past tear-bar (XP-58 Plus)
        if (hasAutoCutter) {
            out.write("\n\n".toByteArray(Charsets.US_ASCII))
            out.write(CMD_PAPER_CUT)
        } else {
            // XP-58 Plus manual tear bar: feed 5 lines so receipt text clears the tear blade
            out.write(CMD_FEED_LINES)
            out.write("\n\n".toByteArray(Charsets.US_ASCII))
        }

        return out.toByteArray()
    }

    /**
     * Builds a hardware test ticket with drawer kick pulse.
     */
    fun buildTestTicket(
        is80mm: Boolean = false,
        kickDrawer: Boolean = false,
        hasAutoCutter: Boolean = is80mm
    ): ByteArray {
        val totalCols = if (is80mm) 48 else 32
        val out = ByteArrayOutputStream()
        out.write(CMD_INIT)
        out.write(CMD_CODEPAGE_CP437)
        if (kickDrawer) {
            out.write(CMD_DRAWER_KICK_PIN2)
            out.write(CMD_DRAWER_KICK_PIN5)
        }
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_BOLD_ON)
        out.write("=== STOREPOINT POS ===\n".toByteArray(Charsets.US_ASCII))
        out.write("HARDWARE PRINTER TEST\n".toByteArray(Charsets.US_ASCII))
        out.write(CMD_BOLD_OFF)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        out.write("Test Date: ${format.format(Date())}\n".toByteArray(Charsets.US_ASCII))
        out.write("Model: ${if (is80mm) "80mm Wide (48 Cols)" else "58mm Standard (XP-58 Plus)"}\n".toByteArray(Charsets.US_ASCII))
        out.write("Drawer Kick: ${if (kickDrawer) "ENABLED" else "DISABLED"}\n".toByteArray(Charsets.US_ASCII))
        out.write("Auto Cutter: ${if (hasAutoCutter) "ENABLED" else "DISABLED (Manual Tear)"}\n".toByteArray(Charsets.US_ASCII))
        out.write(dividerLine(totalCols).toByteArray(Charsets.US_ASCII))
        out.write("ESC/POS Communication: OK\n".toByteArray(Charsets.US_ASCII))
        out.write("Thermal Head Status: OK\n".toByteArray(Charsets.US_ASCII))
        out.write(dividerLine(totalCols).toByteArray(Charsets.US_ASCII))
        out.write("Ready for High-Speed Checkout\n".toByteArray(Charsets.US_ASCII))

        if (hasAutoCutter) {
            out.write("\n\n".toByteArray(Charsets.US_ASCII))
            out.write(CMD_PAPER_CUT)
        } else {
            out.write(CMD_FEED_LINES)
            out.write("\n\n".toByteArray(Charsets.US_ASCII))
        }
        return out.toByteArray()
    }

    /**
     * Builds an End of Day Close Shift (Z-Reading) thermal receipt ticket.
     */
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
        val curr = safeCurrencySymbol(storeConfig.currencySymbol)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        out.write(CMD_INIT)
        out.write(CMD_CODEPAGE_CP437)
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_BOLD_ON)
        val title = if (storeConfig.storeName.isNotBlank()) sanitizeForEscPos(storeConfig.storeName, curr) else "STOREPOINT POS"
        out.write("$title\n".toByteArray(Charsets.US_ASCII))
        out.write("END OF DAY / Z-READING\n".toByteArray(Charsets.US_ASCII))
        out.write("SHIFT CLOSURE REPORT\n".toByteArray(Charsets.US_ASCII))
        out.write(CMD_BOLD_OFF)

        val divider = dividerLine(totalCols)
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(CMD_ALIGN_LEFT)
        out.write("Cashier: ${sanitizeForEscPos(cashierUsername, curr)}\n".toByteArray(Charsets.US_ASCII))
        out.write("Shift Start: ${dateFormat.format(Date(startTime))}\n".toByteArray(Charsets.US_ASCII))
        out.write("Shift End:   ${dateFormat.format(Date(endTime))}\n".toByteArray(Charsets.US_ASCII))
        out.write("Total Transactions: $transactionCount\n".toByteArray(Charsets.US_ASCII))
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(padColumns("Starting Float:", "$curr${String.format(Locale.US, "%.2f", startingCash)}", totalCols).toByteArray(Charsets.US_ASCII))
        out.write(LF)
        out.write(padColumns("Gross Sales:", "$curr${String.format(Locale.US, "%.2f", totalSales)}", totalCols).toByteArray(Charsets.US_ASCII))
        out.write(LF)
        out.write(padColumns(" - Cash Sales:", "$curr${String.format(Locale.US, "%.2f", cashSales)}", totalCols).toByteArray(Charsets.US_ASCII))
        out.write(LF)
        out.write(padColumns(" - Non-Cash / Digital:", "$curr${String.format(Locale.US, "%.2f", nonCashSales)}", totalCols).toByteArray(Charsets.US_ASCII))
        out.write(LF)
        out.write(padColumns("VATable Sales (Net):", "$curr${String.format(Locale.US, "%.2f", vatableSales)}", totalCols).toByteArray(Charsets.US_ASCII))
        out.write(LF)
        out.write(padColumns("12% VAT Collected:", "$curr${String.format(Locale.US, "%.2f", vatAmount)}", totalCols).toByteArray(Charsets.US_ASCII))
        out.write(LF)
        if (payouts != 0.0) {
            out.write(padColumns("Supplier Payouts:", "$curr${String.format(Locale.US, "%.2f", payouts)}", totalCols).toByteArray(Charsets.US_ASCII))
            out.write(LF)
        }
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(padColumns("Expected Drawer Cash:", "$curr${String.format(Locale.US, "%.2f", expectedCash)}", totalCols).toByteArray(Charsets.US_ASCII))
        out.write(LF)
        out.write(padColumns("Counted Drawer Cash:", "$curr${String.format(Locale.US, "%.2f", endingCash)}", totalCols).toByteArray(Charsets.US_ASCII))
        out.write(LF)
        val diff = endingCash - expectedCash
        val diffStr = if (Math.abs(diff) < 0.01) "BALANCED" else if (diff < 0) "SHORT: -$curr${String.format(Locale.US, "%.2f", -diff)}" else "OVER: +$curr${String.format(Locale.US, "%.2f", diff)}"
        out.write(CMD_BOLD_ON)
        out.write(padColumns("Drawer Variance:", diffStr, totalCols).toByteArray(Charsets.US_ASCII))
        out.write(LF)
        out.write(CMD_BOLD_OFF)
        out.write(divider.toByteArray(Charsets.US_ASCII))

        out.write(CMD_ALIGN_CENTER)
        out.write("End of Day Close Completed\n".toByteArray(Charsets.US_ASCII))

        if (hasAutoCutter) {
            out.write("\n\n".toByteArray(Charsets.US_ASCII))
            out.write(CMD_PAPER_CUT)
        } else {
            out.write(CMD_FEED_LINES)
            out.write("\n\n".toByteArray(Charsets.US_ASCII))
        }
        return out.toByteArray()
    }

    /**
     * Enumerates paired Bluetooth devices and filters/highlights likely thermal POS printers.
     */
    fun getPairedBluetoothPrinters(context: Context): List<DiscoveredBluetoothPrinter> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return emptyList()
            }
        }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return try {
            adapter.bondedDevices.map { dev ->
                val devName = dev.name ?: "Unknown Bluetooth Device"
                val isLikely = devName.contains("58", ignoreCase = true) ||
                               devName.contains("XP", ignoreCase = true) ||
                               devName.contains("POS", ignoreCase = true) ||
                               devName.contains("Print", ignoreCase = true) ||
                               devName.contains("MTP", ignoreCase = true) ||
                               devName.contains("RPP", ignoreCase = true) ||
                               devName.contains("Inner", ignoreCase = true)
                DiscoveredBluetoothPrinter(devName, dev.address, isLikely)
            }.sortedByDescending { it.isLikelyPosPrinter }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * Sends raw ESC/POS byte payload directly to a Network Thermal Printer (TCP/IP Port 9100).
     */
    suspend fun printOverNetwork(ipAddress: String, port: Int = 9100, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ipAddress, port), 3500)
                socket.outputStream.use { out ->
                    out.write(data)
                    out.flush()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends raw ESC/POS byte payload directly to a paired Bluetooth Thermal Printer.
     * Uses multi-tier socket establishment (Secure -> Insecure -> Channel 1 Reflection)
     * and discovery cancellation for seamless XP-58 Plus connection.
     */
    suspend fun printOverBluetooth(macAddress: String, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        if (macAddress.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bluetooth MAC address is empty. Please select your printer in Settings."))
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(IllegalStateException("Bluetooth adapter not available on device"))

            if (!adapter.isEnabled) {
                return@withContext Result.failure(IllegalStateException("Bluetooth is disabled. Please turn on Bluetooth."))
            }

            // XP-58 connection requires cancelling discovery if running
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
            } catch (_: SecurityException) {}

            val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)
            var socket: BluetoothSocket? = null
            var lastEx: Exception? = null

            // 1. Try standard secure RFCOMM SPP socket
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
            } catch (e: Exception) {
                lastEx = e
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }

            // 2. Try insecure RFCOMM socket (common requirement on XP-58 / portable printers)
            if (socket == null) {
                try {
                    socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    socket.connect()
                } catch (e: Exception) {
                    lastEx = e
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null
                }
            }

            // 3. Fallback to RFCOMM Channel 1 via reflection (XP-58 / XP-58IIH / POS-58 standard fix)
            if (socket == null) {
                try {
                    val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    socket = m.invoke(device, 1) as BluetoothSocket
                    socket.connect()
                } catch (fallbackEx: Exception) {
                    lastEx = fallbackEx
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null
                }
            }

            val activeSocket = socket ?: throw (lastEx ?: IOException("Failed to connect to Bluetooth printer ($macAddress)"))

            activeSocket.use { s ->
                s.outputStream.use { out ->
                    out.write(data)
                    out.flush()
                    // Allow printer buffer to complete printing before closing connection
                    Thread.sleep(150)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun dividerLine(totalCols: Int): String {
        return "-".repeat(totalCols) + "\n"
    }

    private fun padColumns(left: String, right: String, totalCols: Int): String {
        val space = totalCols - left.length - right.length
        return if (space > 0) left + " ".repeat(space) + right else "$left $right"
    }

    private fun padColumns(c1: String, c2: String, c3: String, totalCols: Int): String {
        val c2c3 = "$c2  $c3"
        val space = totalCols - c1.length - c2c3.length
        return if (space > 0) c1 + " ".repeat(space) + c2c3 else "$c1 $c2 $c3"
    }

    private fun padColumns(c1: String, c2: String, c3: String, c4: String, totalCols: Int): String {
        val col1Width = totalCols - 24
        val col1 = if (c1.length > col1Width) c1.take(col1Width - 2) + ".." else c1.padEnd(col1Width)
        val col2 = c2.padStart(6)
        val col3 = c3.padStart(8)
        val col4 = c4.padStart(10)
        return "$col1$col2$col3$col4"
    }
}
