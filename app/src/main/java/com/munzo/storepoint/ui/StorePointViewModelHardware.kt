package com.munzo.storepoint.ui

import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import com.munzo.storepoint.util.EscPosHelper
import com.munzo.storepoint.util.UsbPrinterHelper
import kotlinx.coroutines.launch

internal fun StorePointViewModel.hardwareUpdatePrinterSettingsImpl(
    type: String,
    ip: String,
    port: Int,
    btMac: String,
    autoKick: Boolean,
    is80mm: Boolean
) {
    prefs.edit()
        .putString("printer_type", type)
        .putString("printer_ip", ip)
        .putInt("printer_port", port)
        .putString("printer_bt_mac", btMac)
        .putBoolean("auto_kick_drawer", autoKick)
        .putBoolean("is_80mm_thermal", is80mm)
        .apply()
    printerType.value = type
    printerIpAddress.value = ip
    printerPort.value = port
    printerBtMac.value = btMac
    isAutoKickDrawerEnabled.value = autoKick
    is80mmThermal.value = is80mm
}

internal fun StorePointViewModel.hardwareSetPrinterTypeImpl(type: String) {
    hardwareUpdatePrinterSettingsImpl(type, printerIpAddress.value, printerPort.value, printerBtMac.value, isAutoKickDrawerEnabled.value, is80mmThermal.value)
}

internal fun StorePointViewModel.hardwareSetPrinterNetworkConfigImpl(ip: String, port: Int) {
    hardwareUpdatePrinterSettingsImpl(printerType.value, ip, port, printerBtMac.value, isAutoKickDrawerEnabled.value, is80mmThermal.value)
}

internal fun StorePointViewModel.hardwareSetPrinterBluetoothMacImpl(mac: String) {
    hardwareUpdatePrinterSettingsImpl(printerType.value, printerIpAddress.value, printerPort.value, mac, isAutoKickDrawerEnabled.value, is80mmThermal.value)
}

internal fun StorePointViewModel.hardwareSetPrinterBluetoothConfigImpl(mac: String, name: String) {
    prefs.edit()
        .putString("printer_type", EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name)
        .putString("printer_bt_mac", mac)
        .putString("printer_device_name", name)
        .apply()
    printerType.value = EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name
    printerBtMac.value = mac
    printerDeviceName.value = name
}

internal fun StorePointViewModel.hardwareSetPrinterUsbConfigImpl(identifier: String, name: String) {
    prefs.edit()
        .putString("printer_type", EscPosHelper.PrinterType.USB_ESCPOS.name)
        .putString("printer_usb_id", identifier)
        .putString("printer_device_name", name)
        .apply()
    printerType.value = EscPosHelper.PrinterType.USB_ESCPOS.name
    printerUsbIdentifier.value = identifier
    printerDeviceName.value = name
}

internal fun StorePointViewModel.hardwareSetPrinterAutoCutterImpl(hasCutter: Boolean) {
    prefs.edit().putBoolean("printer_has_cutter", hasCutter).apply()
    printerHasAutoCutter.value = hasCutter
}

internal fun StorePointViewModel.hardwareSetPaperWidth80mmImpl(is80: Boolean) {
    hardwareUpdatePrinterSettingsImpl(printerType.value, printerIpAddress.value, printerPort.value, printerBtMac.value, isAutoKickDrawerEnabled.value, is80)
    // By default, 80mm printers typically have auto-cutters, while 58mm XP-58 Plus use manual tear bars
    if (!is80) {
        hardwareSetPrinterAutoCutterImpl(false)
    }
}

internal fun StorePointViewModel.hardwareSetAutoKickDrawerEnabledImpl(enabled: Boolean) {
    hardwareUpdatePrinterSettingsImpl(printerType.value, printerIpAddress.value, printerPort.value, printerBtMac.value, enabled, is80mmThermal.value)
}

internal fun StorePointViewModel.hardwareTestPrinterImpl(onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
        val testBytes = EscPosHelper.buildTestTicket(
            is80mm = is80mmThermal.value,
            kickDrawer = isAutoKickDrawerEnabled.value,
            hasAutoCutter = printerHasAutoCutter.value
        )
        when (printerType.value) {
            EscPosHelper.PrinterType.NETWORK_ESCPOS.name -> {
                val res = EscPosHelper.printOverNetwork(printerIpAddress.value, printerPort.value, testBytes)
                res.fold(
                    onSuccess = { onResult(true, "Test ticket printed successfully (Network LAN).") },
                    onFailure = { onResult(false, "Network test failed: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name -> {
                val res = EscPosHelper.printOverBluetooth(printerBtMac.value, testBytes)
                res.fold(
                    onSuccess = { onResult(true, "Test ticket printed successfully (Bluetooth XP-58/Thermal).") },
                    onFailure = { onResult(false, "Bluetooth test failed: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.USB_ESCPOS.name -> {
                val res = UsbPrinterHelper.printOverUsb(context, printerUsbIdentifier.value, testBytes)
                res.fold(
                    onSuccess = { onResult(true, "Test ticket printed successfully (USB OTG).") },
                    onFailure = { onResult(false, "USB print failed: ${it.localizedMessage}") }
                )
            }
            else -> {
                onResult(true, "System Spooler mode active. Connect thermal printer for direct testing.")
            }
        }
    }
}

internal fun StorePointViewModel.hardwareKickCashDrawerImpl(onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
        val kickBytes = EscPosHelper.buildDrawerKickBytes()
        when (printerType.value) {
            EscPosHelper.PrinterType.NETWORK_ESCPOS.name -> {
                val res = EscPosHelper.printOverNetwork(printerIpAddress.value, printerPort.value, kickBytes)
                res.fold(
                    onSuccess = { onResult(true, "Cash drawer kick pulse sent (Network).") },
                    onFailure = { onResult(false, "Network drawer kick failed: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name -> {
                val res = EscPosHelper.printOverBluetooth(printerBtMac.value, kickBytes)
                res.fold(
                    onSuccess = { onResult(true, "Cash drawer kick pulse sent (Bluetooth).") },
                    onFailure = { onResult(false, "Bluetooth drawer kick failed: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.USB_ESCPOS.name -> {
                val res = UsbPrinterHelper.printOverUsb(context, printerUsbIdentifier.value, kickBytes)
                res.fold(
                    onSuccess = { onResult(true, "Cash drawer kick pulse sent (USB).") },
                    onFailure = { onResult(false, "USB drawer kick failed: ${it.localizedMessage}") }
                )
            }
            else -> {
                onResult(true, "Cash drawer manual release signal dispatched.")
            }
        }
    }
}

internal fun StorePointViewModel.hardwarePrintReceiptImpl(
    transaction: Transaction,
    items: List<TransactionItem>,
    onCompleted: (Boolean, String) -> Unit
) {
    viewModelScope.launch {
        val cfg = storeConfig.value ?: run {
            onCompleted(false, "Store configuration not found.")
            return@launch
        }
        val type = printerType.value
        val autoKick = isAutoKickDrawerEnabled.value
        val is80 = is80mmThermal.value
        val hasCutter = printerHasAutoCutter.value
        val receiptBytes = EscPosHelper.buildReceiptEscPos(
            storeConfig = cfg,
            transaction = transaction,
            items = items,
            is80mm = is80,
            kickDrawerOnPrint = autoKick,
            hasAutoCutter = hasCutter
        )

        when (type) {
            EscPosHelper.PrinterType.NETWORK_ESCPOS.name -> {
                val res = EscPosHelper.printOverNetwork(printerIpAddress.value, printerPort.value, receiptBytes)
                res.fold(
                    onSuccess = { onCompleted(true, "Receipt printed over Network ESC/POS.") },
                    onFailure = { onCompleted(false, "Network printer error: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name -> {
                val res = EscPosHelper.printOverBluetooth(printerBtMac.value, receiptBytes)
                res.fold(
                    onSuccess = { onCompleted(true, "Receipt printed over Bluetooth ESC/POS.") },
                    onFailure = { onCompleted(false, "Bluetooth printer error: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.USB_ESCPOS.name -> {
                val res = UsbPrinterHelper.printOverUsb(context, printerUsbIdentifier.value, receiptBytes)
                res.fold(
                    onSuccess = { onCompleted(true, "Receipt printed over USB OTG ESC/POS.") },
                    onFailure = { onCompleted(false, "USB printer error: ${it.localizedMessage}") }
                )
            }
            else -> {
                onCompleted(false, "Printer type set to System Spooler. Use standard Print Dialog.")
            }
        }
    }
}

internal fun StorePointViewModel.hardwarePrintZReadingImpl(
    zTicketBytes: ByteArray,
    onCompleted: (Boolean, String) -> Unit
) {
    viewModelScope.launch {
        when (printerType.value) {
            EscPosHelper.PrinterType.NETWORK_ESCPOS.name -> {
                val res = EscPosHelper.printOverNetwork(printerIpAddress.value, printerPort.value, zTicketBytes)
                res.fold(
                    onSuccess = { onCompleted(true, "Z-Reading printed to network printer.") },
                    onFailure = { onCompleted(false, "Network printer error: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name -> {
                val res = EscPosHelper.printOverBluetooth(printerBtMac.value, zTicketBytes)
                res.fold(
                    onSuccess = { onCompleted(true, "Z-Reading printed to Bluetooth printer.") },
                    onFailure = { onCompleted(false, "Bluetooth printer error: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.USB_ESCPOS.name -> {
                val res = UsbPrinterHelper.printOverUsb(context, printerUsbIdentifier.value, zTicketBytes)
                res.fold(
                    onSuccess = { onCompleted(true, "Z-Reading printed to USB printer.") },
                    onFailure = { onCompleted(false, "USB printer error: ${it.localizedMessage}") }
                )
            }
            else -> {
                onCompleted(false, "Thermal printer not connected. Connect Bluetooth, USB or Network printer.")
            }
        }
    }
}
