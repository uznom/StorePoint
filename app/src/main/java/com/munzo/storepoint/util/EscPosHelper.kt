package com.munzo.storepoint.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object EscPosHelper {

    enum class PrinterType {
        SYSTEM_SPOOLER,
        NETWORK_ESCPOS,
        BLUETOOTH_ESCPOS
    }

    // Standard Bluetooth Serial Port Profile (SPP) UUID
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Command Constants
    val CMD_INIT = byteArrayOf(0x1B, 0x40) // ESC @
    val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
    val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
    val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2
    val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
    val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
    val CMD_FONT_LARGE = byteArrayOf(0x1D, 0x21, 0x11) // GS ! 0x11 (Double Width & Height)
    val CMD_FONT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00) // GS ! 0x00
    val CMD_DRAWER_KICK_PIN2 = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()) // ESC p 0 25 250
    val CMD_DRAWER_KICK_PIN5 = byteArrayOf(0x1B, 0x70, 0x01, 0x19, 0xFA.toByte()) // ESC p 1 25 250
    val CMD_PAPER_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V 66 0 (Feed and full cut)
    val LF = byteArrayOf(0x0A) // Line Feed

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
     * Builds a complete, formatted ESC/POS receipt for thermal printers (58mm or 80mm width).
     */
    fun buildReceiptEscPos(
        storeConfig: StoreConfig,
        transaction: Transaction,
        items: List<TransactionItem>,
        is80mm: Boolean = false,
        kickDrawerOnPrint: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val totalCols = if (is80mm) 48 else 32
        val curr = storeConfig.currencySymbol
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        // Initialize Printer
        out.write(CMD_INIT)

        // Cash Drawer Kick Pulse (if enabled)
        if (kickDrawerOnPrint) {
            out.write(CMD_DRAWER_KICK_PIN2)
            out.write(CMD_DRAWER_KICK_PIN5)
        }

        // Header: Store Name (Centered, Large, Bold)
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_FONT_LARGE)
        out.write(CMD_BOLD_ON)
        out.write("${storeConfig.storeName}\n".toByteArray(Charsets.UTF_8))
        out.write(CMD_FONT_NORMAL)
        out.write(CMD_BOLD_OFF)
        out.write("Official POS Sales Receipt\n".toByteArray(Charsets.UTF_8))
        out.write("--------------------------------\n".toByteArray(Charsets.UTF_8))

        // Transaction Meta (Left Aligned)
        out.write(CMD_ALIGN_LEFT)
        out.write("Receipt #: ${transaction.id}\n".toByteArray(Charsets.UTF_8))
        out.write("Date: ${sdf.format(Date(transaction.timestamp))}\n".toByteArray(Charsets.UTF_8))
        out.write("Cashier: ${transaction.cashierUsername}\n".toByteArray(Charsets.UTF_8))
        if (transaction.customerName.isNotBlank()) {
            out.write("Customer: ${transaction.customerName}\n".toByteArray(Charsets.UTF_8))
        }
        out.write("Payment: ${transaction.paymentMethod}\n".toByteArray(Charsets.UTF_8))
        out.write("--------------------------------\n".toByteArray(Charsets.UTF_8))

        // Column Header
        val colHeader = if (is80mm) {
            padColumns("ITEM DESCRIPTION", "QTY", "PRICE", "TOTAL", totalCols)
        } else {
            padColumns("ITEM", "QTY", "AMT", totalCols)
        }
        out.write(CMD_BOLD_ON)
        out.write("$colHeader\n".toByteArray(Charsets.UTF_8))
        out.write(CMD_BOLD_OFF)
        out.write("--------------------------------\n".toByteArray(Charsets.UTF_8))

        // Items
        items.forEach { item ->
            val lineTotalStr = String.format(Locale.US, "%.2f", item.price * item.quantity)
            val priceStr = String.format(Locale.US, "%.2f", item.price)
            if (is80mm) {
                val row = padColumns(item.productName, item.quantity.toString(), priceStr, lineTotalStr, totalCols)
                out.write("$row\n".toByteArray(Charsets.UTF_8))
            } else {
                // 32-col layout: Name on first line, Qty x Price and total on second line
                val truncatedName = if (item.productName.length > 30) item.productName.take(28) + ".." else item.productName
                out.write("$truncatedName\n".toByteArray(Charsets.UTF_8))
                val detail = "  ${item.quantity} x $priceStr"
                val row = padColumns(detail, lineTotalStr, totalCols)
                out.write("$row\n".toByteArray(Charsets.UTF_8))
            }
        }
        out.write("--------------------------------\n".toByteArray(Charsets.UTF_8))

        // Totals (Right Aligned - Philippine VAT standard)
        out.write(CMD_ALIGN_RIGHT)
        val subtotalStr = "VATable Sales: $curr${String.format(Locale.US, "%.2f", transaction.subtotal)}"
        out.write("$subtotalStr\n".toByteArray(Charsets.UTF_8))

        if (transaction.taxAmount > 0.0) {
            val taxStr = "12% VAT (Inc.): $curr${String.format(Locale.US, "%.2f", transaction.taxAmount)}"
            out.write("$taxStr\n".toByteArray(Charsets.UTF_8))
        }

        out.write(CMD_BOLD_ON)
        val totalStr = "TOTAL AMOUNT: $curr${String.format(Locale.US, "%.2f", transaction.totalAmount)}"
        out.write("$totalStr\n".toByteArray(Charsets.UTF_8))
        out.write(CMD_BOLD_OFF)

        val paidStr = "Amount Tendered: $curr${String.format(Locale.US, "%.2f", transaction.cashPaid)}"
        out.write("$paidStr\n".toByteArray(Charsets.UTF_8))

        val changeStr = "Change: $curr${String.format(Locale.US, "%.2f", transaction.changeAmount)}"
        out.write("$changeStr\n".toByteArray(Charsets.UTF_8))
        out.write("--------------------------------\n".toByteArray(Charsets.UTF_8))

        // Footer (Centered)
        out.write(CMD_ALIGN_CENTER)
        out.write("Thank you for your business!\n".toByteArray(Charsets.UTF_8))
        out.write("Please come again.\n\n\n".toByteArray(Charsets.UTF_8))

        // Paper Cut
        out.write(CMD_PAPER_CUT)

        return out.toByteArray()
    }

    /**
     * Builds a hardware test ticket with drawer kick pulse.
     */
    fun buildTestTicket(is80mm: Boolean = false, kickDrawer: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(CMD_INIT)
        if (kickDrawer) {
            out.write(CMD_DRAWER_KICK_PIN2)
            out.write(CMD_DRAWER_KICK_PIN5)
        }
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_BOLD_ON)
        out.write("=== STOREPOINT POS ===\n".toByteArray(Charsets.UTF_8))
        out.write("HARDWARE PRINTER TEST\n".toByteArray(Charsets.UTF_8))
        out.write(CMD_BOLD_OFF)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        out.write("Test Date: ${format.format(Date())}\n".toByteArray(Charsets.UTF_8))
        out.write("Width: ${if (is80mm) "80mm Wide (48 Cols)" else "58mm Standard (32 Cols)"}\n".toByteArray(Charsets.UTF_8))
        out.write("Drawer Kick: ${if (kickDrawer) "ENABLED" else "DISABLED"}\n".toByteArray(Charsets.UTF_8))
        out.write("--------------------------------\n".toByteArray(Charsets.UTF_8))
        out.write("ESC/POS Communication: OK\n".toByteArray(Charsets.UTF_8))
        out.write("Thermal Head Status: OK\n".toByteArray(Charsets.UTF_8))
        out.write("--------------------------------\n".toByteArray(Charsets.UTF_8))
        out.write("Ready for High-Speed Checkout\n\n\n".toByteArray(Charsets.UTF_8))
        out.write(CMD_PAPER_CUT)
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
        is80mm: Boolean = false
    ): ByteArray {
        val totalCols = if (is80mm) 48 else 32
        val out = ByteArrayOutputStream()
        val curr = storeConfig.currencySymbol
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        out.write(CMD_INIT)
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_BOLD_ON)
        val title = if (storeConfig.storeName.isNotBlank()) storeConfig.storeName else "STOREPOINT POS"
        out.write("$title\n".toByteArray(Charsets.UTF_8))
        out.write("END OF DAY / Z-READING\n".toByteArray(Charsets.UTF_8))
        out.write("SHIFT CLOSURE REPORT\n".toByteArray(Charsets.UTF_8))
        out.write(CMD_BOLD_OFF)

        val divider = "-".repeat(totalCols) + "\n"
        out.write(divider.toByteArray(Charsets.UTF_8))

        out.write(CMD_ALIGN_LEFT)
        out.write("Cashier: $cashierUsername\n".toByteArray(Charsets.UTF_8))
        out.write("Shift Start: ${dateFormat.format(Date(startTime))}\n".toByteArray(Charsets.UTF_8))
        out.write("Shift End:   ${dateFormat.format(Date(endTime))}\n".toByteArray(Charsets.UTF_8))
        out.write("Total Transactions: $transactionCount\n".toByteArray(Charsets.UTF_8))
        out.write(divider.toByteArray(Charsets.UTF_8))

        out.write(padColumns("Starting Float:", "$curr${String.format(Locale.US, "%.2f", startingCash)}", totalCols).toByteArray(Charsets.UTF_8))
        out.write(LF)
        out.write(padColumns("Gross Sales:", "$curr${String.format(Locale.US, "%.2f", totalSales)}", totalCols).toByteArray(Charsets.UTF_8))
        out.write(LF)
        out.write(padColumns(" - Cash Sales:", "$curr${String.format(Locale.US, "%.2f", cashSales)}", totalCols).toByteArray(Charsets.UTF_8))
        out.write(LF)
        out.write(padColumns(" - Non-Cash / Digital:", "$curr${String.format(Locale.US, "%.2f", nonCashSales)}", totalCols).toByteArray(Charsets.UTF_8))
        out.write(LF)
        out.write(padColumns("VATable Sales (Net):", "$curr${String.format(Locale.US, "%.2f", vatableSales)}", totalCols).toByteArray(Charsets.UTF_8))
        out.write(LF)
        out.write(padColumns("12% VAT Collected:", "$curr${String.format(Locale.US, "%.2f", vatAmount)}", totalCols).toByteArray(Charsets.UTF_8))
        out.write(LF)
        if (payouts != 0.0) {
            out.write(padColumns("Supplier Payouts:", "$curr${String.format(Locale.US, "%.2f", payouts)}", totalCols).toByteArray(Charsets.UTF_8))
            out.write(LF)
        }
        out.write(divider.toByteArray(Charsets.UTF_8))

        out.write(padColumns("Expected Drawer Cash:", "$curr${String.format(Locale.US, "%.2f", expectedCash)}", totalCols).toByteArray(Charsets.UTF_8))
        out.write(LF)
        out.write(padColumns("Counted Drawer Cash:", "$curr${String.format(Locale.US, "%.2f", endingCash)}", totalCols).toByteArray(Charsets.UTF_8))
        out.write(LF)
        val diff = endingCash - expectedCash
        val diffStr = if (Math.abs(diff) < 0.01) "BALANCED" else if (diff < 0) "SHORT: -$curr${String.format(Locale.US, "%.2f", -diff)}" else "OVER: +$curr${String.format(Locale.US, "%.2f", diff)}"
        out.write(CMD_BOLD_ON)
        out.write(padColumns("Drawer Variance:", diffStr, totalCols).toByteArray(Charsets.UTF_8))
        out.write(LF)
        out.write(CMD_BOLD_OFF)
        out.write(divider.toByteArray(Charsets.UTF_8))

        out.write(CMD_ALIGN_CENTER)
        out.write("End of Day Close Completed\n\n\n".toByteArray(Charsets.UTF_8))
        out.write(CMD_PAPER_CUT)
        return out.toByteArray()
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
     */
    suspend fun printOverBluetooth(macAddress: String, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(IllegalStateException("Bluetooth adapter not available on device"))

            if (!adapter.isEnabled) {
                return@withContext Result.failure(IllegalStateException("Bluetooth is disabled"))
            }

            val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)
            val socket: BluetoothSocket = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
                try {
                    device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                } catch (e2: Exception) {
                    val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    m.invoke(device, 1) as BluetoothSocket
                }
            }

            try {
                socket.connect()
            } catch (e: Exception) {
                // Fallback to channel 1 reflection connect if first attempt failed
                try {
                    val fallbackSocket = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket
                    fallbackSocket.connect()
                    fallbackSocket.outputStream.use { out ->
                        out.write(data)
                        out.flush()
                    }
                    try { fallbackSocket.close() } catch (_: Exception) {}
                    return@withContext Result.success(Unit)
                } catch (fallbackEx: Exception) {
                    throw e
                }
            }
            socket.outputStream.use { out ->
                out.write(data)
                out.flush()
            }
            try { socket.close() } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
