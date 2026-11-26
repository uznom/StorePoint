package com.munzo.storepoint.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.munzo.storepoint.data.DrawerTransaction
import com.munzo.storepoint.data.ReturnItem
import com.munzo.storepoint.data.ReturnTransaction
import com.munzo.storepoint.data.ShiftReconciliation
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Universal ESC/POS helper supporting 58mm and 80mm thermal receipt printers over Bluetooth and LAN.
 */
object EscPosHelper {

    enum class PrinterType {
        SYSTEM_SPOOLER,
        NETWORK_ESCPOS,
        BLUETOOTH_ESCPOS,
        USB_ESCPOS
    }

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Command Constants
    val CMD_INIT = byteArrayOf(0x1B, 0x40)
    val CMD_CODEPAGE_CP437 = byteArrayOf(0x1B, 0x74, 0x00)
    val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    val CMD_FONT_LARGE = byteArrayOf(0x1D, 0x21, 0x11)
    val CMD_FONT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    val CMD_DRAWER_KICK_PIN2 = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())
    val CMD_DRAWER_KICK_PIN5 = byteArrayOf(0x1B, 0x70, 0x01, 0x19, 0xFA.toByte())
    val CMD_PAPER_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    val CMD_FEED_LINES = byteArrayOf(0x1B, 0x64, 0x05)
    val LF = byteArrayOf(0x0A)

    val CMD_BARCODE_HRI_BELOW = byteArrayOf(0x1D, 0x48, 0x02)
    val CMD_BARCODE_FONT_A = byteArrayOf(0x1D, 0x66, 0x00)

    data class DiscoveredBluetoothPrinter(
        val name: String,
        val macAddress: String,
        val isLikelyPosPrinter: Boolean
    )

    data class DrawerBalancingSummary(
        val startingFloat: Double = 0.0,
        val cashSales: Double = 0.0,
        val cashIn: Double = 0.0,
        val cashOut: Double = 0.0,
        val cashRefunds: Double = 0.0
    )

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

    fun safeCurrencySymbol(symbol: String): String =
        if (symbol.isBlank() || symbol == "₱") "P" else symbol.trim()

    fun buildDrawerKickBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(CMD_INIT)
        out.write(CMD_DRAWER_KICK_PIN2)
        out.write(CMD_DRAWER_KICK_PIN5)
        return out.toByteArray()
    }

    // --- Delegated ESC/POS Formatters ---

    fun buildBarcodeEscPos(
        data: String,
        isCode128: Boolean = true,
        height: Int = 50,
        width: Int = 2
    ): ByteArray = EscPosReceiptFormatter.buildBarcodeEscPos(data, isCode128, height, width)

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
    ): ByteArray = EscPosReceiptFormatter.buildReceiptEscPos(
        storeConfig, transaction, items, is80mm, kickDrawerOnPrint,
        hasAutoCutter, customHeader, customFooter, storeTin, storeAddress, printBarcode
    )

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
    ): ByteArray = EscPosReceiptFormatter.buildRefundReceiptEscPos(
        storeConfig, returnTransaction, items, is80mm, kickDrawerOnPrint,
        hasAutoCutter, customHeader, customFooter, storeTin, storeAddress, printBarcode
    )

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
    ): ByteArray = EscPosShiftFormatter.buildDrawerSlipEscPos(
        storeConfig, drawerTransaction, balanceBefore, balanceAfter, is80mm,
        kickDrawerOnPrint, hasAutoCutter, customHeader, storeTin, printBarcode
    )

    fun buildTestTicket(
        is80mm: Boolean = false,
        kickDrawer: Boolean = false,
        hasAutoCutter: Boolean = is80mm
    ): ByteArray = EscPosShiftFormatter.buildTestTicket(is80mm, kickDrawer, hasAutoCutter)

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
    ): ByteArray = EscPosShiftFormatter.buildShiftZReadingTicket(
        storeConfig, cashierUsername, startTime, endTime, startingCash, endingCash,
        expectedCash, totalSales, cashSales, nonCashSales, vatableSales, vatAmount,
        payouts, transactionCount, is80mm, hasAutoCutter
    )

    fun buildShiftReconciliationReceipt(
        storeConfig: StoreConfig,
        reconciliation: ShiftReconciliation,
        startTime: Long,
        formulaSummary: DrawerBalancingSummary? = null,
        is80mm: Boolean = false,
        hasAutoCutter: Boolean = is80mm,
        printBarcode: Boolean = true
    ): ByteArray = EscPosShiftFormatter.buildShiftReconciliationReceipt(
        storeConfig, reconciliation, startTime, formulaSummary, is80mm, hasAutoCutter, printBarcode
    )

    // --- Bluetooth & Network Transport ---

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

    suspend fun printOverNetwork(ipAddress: String, port: Int = 9100, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
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

    suspend fun printOverBluetooth(macAddress: String, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (macAddress.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Bluetooth MAC address is empty. Please select your printer in Settings.")
                )
            }
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: return@withContext Result.failure(IllegalStateException("Bluetooth adapter not available on device"))

                if (!adapter.isEnabled) {
                    return@withContext Result.failure(IllegalStateException("Bluetooth is disabled. Please turn on Bluetooth."))
                }

                try {
                    if (adapter.isDiscovering) adapter.cancelDiscovery()
                } catch (_: SecurityException) {}

                val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)
                var socket: BluetoothSocket? = null
                var lastEx: Exception? = null

                withTimeout(8000L) {
                    var s1: BluetoothSocket? = null
                    try {
                        s1 = device.createRfcommSocketToServiceRecord(SPP_UUID)
                        s1.connect()
                        socket = s1
                    } catch (e: Exception) {
                        lastEx = e
                        try { s1?.close() } catch (_: Exception) {}
                        socket = null
                    }

                    if (socket == null) {
                        var s2: BluetoothSocket? = null
                        try {
                            s2 = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                            s2.connect()
                            socket = s2
                        } catch (e: Exception) {
                            lastEx = e
                            try { s2?.close() } catch (_: Exception) {}
                            socket = null
                        }
                    }

                    if (socket == null) {
                        var s3: BluetoothSocket? = null
                        try {
                            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            s3 = m.invoke(device, 1) as BluetoothSocket
                            s3.connect()
                            socket = s3
                        } catch (fallbackEx: Exception) {
                            lastEx = fallbackEx
                            try { s3?.close() } catch (_: Exception) {}
                            socket = null
                        }
                    }
                }

                val activeSocket = socket ?: throw (lastEx ?: IOException("Failed to connect to Bluetooth printer ($macAddress)"))

                activeSocket.use { s ->
                    s.outputStream.use { out ->
                        out.write(data)
                        out.flush()
                        delay(150)
                    }
                }

                Result.success(Unit)
            } catch (e: TimeoutCancellationException) {
                Result.failure(IOException("Bluetooth printer connection timed out after 8s ($macAddress)", e))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // --- Column & Divider Helpers ---

    internal fun dividerLine(totalCols: Int): String = "-".repeat(totalCols) + "\n"

    internal fun padColumns(left: String, right: String, totalCols: Int): String {
        val space = totalCols - left.length - right.length
        return if (space > 0) left + " ".repeat(space) + right else "$left $right"
    }

    internal fun padColumns(c1: String, c2: String, c3: String, totalCols: Int): String {
        val c2c3 = "$c2  $c3"
        val space = totalCols - c1.length - c2c3.length
        return if (space > 0) c1 + " ".repeat(space) + c2c3 else "$c1 $c2 $c3"
    }

    internal fun padColumns(c1: String, c2: String, c3: String, c4: String, totalCols: Int): String {
        val col1Width = totalCols - 24
        val col1 = if (c1.length > col1Width) c1.take(col1Width - 2) + ".." else c1.padEnd(col1Width)
        val col2 = c2.padStart(6)
        val col3 = c3.padStart(8)
        val col4 = c4.padStart(10)
        return "$col1$col2$col3$col4"
    }
}
