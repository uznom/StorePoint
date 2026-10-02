package com.munzo.storepoint.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Helper for managing direct USB OTG thermal receipt printers (such as Xprinter XP-58 Plus,
 * XP-58IIH, POS-58, and standard USB ESC/POS printers) like in Loyverse.
 */
object UsbPrinterHelper {

    const val ACTION_USB_PERMISSION = "com.munzo.storepoint.USB_PERMISSION"

    data class DiscoveredUsbPrinter(
        val deviceId: Int,
        val vendorId: Int,
        val productId: Int,
        val deviceName: String,
        val productName: String,
        val manufacturerName: String,
        val identifier: String // "vendorId:productId" or deviceId string
    ) {
        val displayName: String
            get() {
                val name = productName.takeIf { it.isNotBlank() }
                    ?: manufacturerName.takeIf { it.isNotBlank() }
                    ?: "Thermal USB Printer"
                return "$name (VID:${Integer.toHexString(vendorId).uppercase()}, PID:${Integer.toHexString(productId).uppercase()})"
            }
    }

    /**
     * Determines whether a given USB device is a printer or POS peripheral with bulk OUT endpoints.
     */
    fun isPrinterDevice(device: UsbDevice): Boolean {
        // 1. Direct USB class match (Class 7 = USB_CLASS_PRINTER)
        if (device.deviceClass == UsbConstants.USB_CLASS_PRINTER) return true

        // 2. Check each interface for printer class or bulk OUT endpoint
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_PRINTER) return true
            // Many Chinese 58mm printers (XP-58 / Xprinter) declare class 0 (per-interface) or 255 (vendor-specific)
            // with a bulk OUT transfer endpoint
            for (j in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Scans and returns all attached USB printers.
     */
    fun getConnectedUsbPrinters(context: Context): List<DiscoveredUsbPrinter> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        val result = mutableListOf<DiscoveredUsbPrinter>()

        for (device in usbManager.deviceList.values) {
            if (isPrinterDevice(device)) {
                val prodName = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        device.productName ?: ""
                    } else ""
                } catch (_: Exception) { "" }

                val manName = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        device.manufacturerName ?: ""
                    } else ""
                } catch (_: Exception) { "" }

                result.add(
                    DiscoveredUsbPrinter(
                        deviceId = device.deviceId,
                        vendorId = device.vendorId,
                        productId = device.productId,
                        deviceName = device.deviceName,
                        productName = prodName,
                        manufacturerName = manName,
                        identifier = "${device.vendorId}:${device.productId}"
                    )
                )
            }
        }
        return result
    }

    /**
     * Finds a connected UsbDevice by its stored identifier ("vendorId:productId" or deviceId).
     */
    fun findDeviceByIdentifier(context: Context, identifier: String): UsbDevice? {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        if (identifier.isBlank()) {
            // If identifier is blank, pick the first connected printer if available
            return usbManager.deviceList.values.firstOrNull { isPrinterDevice(it) }
        }

        // Try matching "vendorId:productId"
        if (identifier.contains(":")) {
            val parts = identifier.split(":")
            val vid = parts.getOrNull(0)?.toIntOrNull()
            val pid = parts.getOrNull(1)?.toIntOrNull()
            if (vid != null && pid != null) {
                val match = usbManager.deviceList.values.firstOrNull { it.vendorId == vid && it.productId == pid }
                if (match != null) return match
            }
        }

        // Try matching by deviceId
        val devId = identifier.toIntOrNull()
        if (devId != null) {
            val match = usbManager.deviceList.values.firstOrNull { it.deviceId == devId }
            if (match != null) return match
        }

        // Fallback: match by deviceName
        return usbManager.deviceList.values.firstOrNull { it.deviceName == identifier }
            ?: usbManager.deviceList.values.firstOrNull { isPrinterDevice(it) }
    }

    /**
     * Checks if the app has permission to access the specified USB device.
     */
    fun hasPermission(context: Context, device: UsbDevice): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        return usbManager.hasPermission(device)
    }

    /**
     * Requests USB permission for the device with a system prompt.
     */
    fun requestPermission(context: Context, device: UsbDevice, onResult: (Boolean) -> Unit) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: run {
            onResult(false)
            return
        }

        if (usbManager.hasPermission(device)) {
            onResult(true)
            return
        }

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flag
        )

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == ACTION_USB_PERMISSION) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}
                    onResult(granted)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        usbManager.requestPermission(device, permissionIntent)
    }

    /**
     * Sends raw ESC/POS byte data directly to an attached USB thermal receipt printer.
     */
    suspend fun printOverUsb(
        context: Context,
        deviceIdentifier: String,
        data: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return@withContext Result.failure(IllegalStateException("USB service unavailable"))

        val device = findDeviceByIdentifier(context, deviceIdentifier)
            ?: return@withContext Result.failure(IOException("USB thermal printer not found. Please connect printer via USB OTG cable."))

        if (!usbManager.hasPermission(device)) {
            return@withContext Result.failure(SecurityException("USB permission not granted for ${device.deviceName}. Please grant permission in Admin Settings."))
        }

        val connection: UsbDeviceConnection = usbManager.openDevice(device)
            ?: return@withContext Result.failure(IOException("Failed to open connection to USB printer: ${device.deviceName}"))

        try {
            var targetInterface: UsbInterface? = null
            var targetEndpoint: UsbEndpoint? = null

            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                        targetInterface = intf
                        targetEndpoint = ep
                        break
                    }
                }
                if (targetEndpoint != null) break
            }

            val iface = targetInterface
                ?: return@withContext Result.failure(IOException("No bulk OUT USB endpoint found on printer ${device.deviceName}"))
            val ep = targetEndpoint
                ?: return@withContext Result.failure(IOException("No bulk OUT endpoint found on printer ${device.deviceName}"))

            if (!connection.claimInterface(iface, true)) {
                return@withContext Result.failure(IOException("Could not claim USB interface on ${device.deviceName}"))
            }

            try {
                val chunkSize = 4096
                var offset = 0
                while (offset < data.size) {
                    val length = minOf(chunkSize, data.size - offset)
                    val chunk = data.copyOfRange(offset, offset + length)
                    val transferred = connection.bulkTransfer(ep, chunk, length, 5000)
                    if (transferred < 0) {
                        return@withContext Result.failure(IOException("USB bulk transfer failed with status code $transferred"))
                    }
                    offset += length
                }
                // Small buffer settle delay for XP-58 USB controller
                Thread.sleep(100)
                Result.success(Unit)
            } finally {
                connection.releaseInterface(iface)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { connection.close() } catch (_: Exception) {}
        }
    }
}
